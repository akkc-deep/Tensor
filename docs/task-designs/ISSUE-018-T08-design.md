# ISSUE-018-T08：手动重试、恢复与后台生命周期

## Goal

让已持久化任务可以手动 retry / resume，并在单实例内保证只有一个实际 worker。进程启动、数据库故障和正常关闭后，成功叶子不会重发；未退出的旧调用不能被取消标记或新操作绕过。

任务身份来自 [ISSUE-018 看板 T08](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t08)，Order 8，直接依赖已完成 [T07](ISSUE-018-T07-design.md)。共享依据是[总体设计](ISSUE-018-design.md) §3.7–3.10、§3.13–3.14、§5.1–5.2。T07 已于2026-09-12完成，本设计只准备T08，不启动其实现。

## Scope

扩展 DownloadTaskService 的手动控制，新增 DownloadTaskCoordinator 管理启动恢复、每秒轮询、活动租约、异常后的事实恢复及关闭。新增 app 的 DownloadTaskConfiguration 提供可显式导入的生命周期装配，并用真实MySQL和受控executor验证。

保留已存在的接收、幂等、查询、runner公开合同、仓储事务和40项注册。T09负责正式属性绑定、构建任务服务/仓储/runner的生产bean、导入本项配置、HTTP与日志；本项配置在显式测试上下文中完整验证，但不提前启用生产任务HTTP或改现有ApplicationConfiguration。T12负责实际应用进程/浏览器闭环。本项不实现取消任务、自动失败重试、重启自动续跑、多worker、多实例协调、定时下载或生产RANGE开放。

## Approach

### 现有输入与最小结构

T07已有 `DownloadTaskRunner.runNext(BooleanSupplier)`：同步领取一任务，返回IDLE/FINISHED/NEEDS_RECOVERY/PERMIT_LOST；方法实际退出才释放内部占用。其 `RunResult(taskId,permit,disposition,error)` 无原异常，permit含taskId/activeRunId/runGeneration。runNext本身不调度、不恢复、不提供isActive。所有来源与适配在事务外，单个调用可能忽略Future.cancel直到自身有限I/O超时结束。

仓储已实现以下真实表面，不另建DAO或修改V8：

- `unfinishedTasks()`：QUEUED/RUNNING，按queued_at/task_id；`snapshot(UUID)`提供同快照task/Counts。
- `requeue(UUID,long,UUID,RequeueMode,Instant)`：拥有事务，锁任务再批次；RETRY只重排FAILED，RESUME只重排EXECUTION_INTERRUPTED失败批，PENDING本身无需变更；SUCCEEDED/SPLIT保持。
- `recoverStoppedTask(UUID,UUID,int,long,Instant)`：同时校验启动ID/generation/version及QUEUED/RUNNING；全叶子成功重算SUCCEEDED，否则INTERRUPTED并把RUNNING批标为FAILED/EXECUTION_INTERRUPTED；PENDING、普通失败和成功叶子保留。
- `findTask`、`queuedCount`、已有claim/reserve/commit链路。requeue保留requestCount、runRequestCount、sourceRows和attemptCount；**runRequestCount只在下一次claimTask清零**，不是点击重试时清零。

新增core公开类型和用例固定为：

```java
public final class DownloadTaskCoordinator implements AutoCloseable {
    public DownloadTaskCoordinator(DownloadTaskService tasks,
            DownloadTaskRepository repository, DownloadTaskRunner runner,
            Clock clock, UUID activeRunId);
    public void start();
    public boolean isRunning();
    @Override public void close();
}
// 加到现有DownloadTaskService，原构造器与已有public用例保留：
public DownloadTask retry(UUID taskId, long expectedVersion);
public DownloadTask resume(UUID taskId, long expectedVersion);
public ControlAvailability controls(DownloadTask task);
public record ControlAvailability(boolean canRetry, boolean canResume) {}
```

协调器使用一个专用单线程ScheduledExecutorService（轮询线程）和一个单线程ExecutorService（实际worker），禁止CallerRunsPolicy。生产线程名分别为`tensor-download-poller`、`tensor-download-worker`。不为每项任务创建executor，不把任务提交到servlet或公共ForkJoinPool。测试提供同包构造器额外接收这两个executor，使用可控队列执行器验证提交前租约和拒绝策略；不提供生产配置注入多worker。

### 一把协调锁与装配次序

沿用DownloadTaskService私有admissionLock，增加包内 `ReentrantLock coordinationLock()` 供唯一协调器使用。所有接收容量+INSERT、retry/resume、调度登记、租约释放和恢复许可判定使用同一把锁；不再引入第二把协调锁。顺序固定为协调/接收锁→仓储任务行→批次行，永不在数据库行锁中等待数据集锁或调用插件。runner取数不持有协调锁；其事务仍由T07短边界拥有。

为兼容已有独立T03/T07用法，Service原构造器不自动创建线程。增加包内 `bindCoordinator(DownloadTaskCoordinator)`：协调器构造时在这把锁下绑定一次，重复绑定拒绝固定IllegalStateException，不替换仍可能活动的实例。绑定后新接收/控制必须服从该协调器的STARTING/RUNNING/RECOVERING/FAULTED/CLOSING/CLOSED事实；未绑定时旧submit/validateReplay语义保持，新增retry/resume返回固定TASK_STATE_CONFLICT，controls为false/false。生产T09必须先构造所有协作者并绑定，再让Spring生命周期start开放；不得发布一个未绑定的任务Controller。

协调器构造器只校验依赖、取得锁并绑定，不恢复数据库或启动线程。依赖均非null，activeRunId与service和runner必须来自同一bean；增加Service包内 `UUID activeRunId()` 以在绑定时核对，并增加包内 `Settings settings()` 让协调器读取同一enabled设置；runner的同ID通过T09装配合同和测试证明，不扩大T07公开表面。isRunning在RUNNING/RECOVERING/FAULTED都为true（生命周期已启动、资源仍需关闭），不代表当前允许接收；CLOSING/CLOSED/尚未启动为false。FAULTED是私有生命周期状态，独立于任务FAILED：表示executor拒绝或无可靠RunResult的worker意外失败。它不进入数据库探针恢复路径、不再派发、不接收新任务/控制；只允许查询/幂等找回和close，恢复执行须重启应用。

### 手动控制与接收门禁

retry/resume首先拒绝调用方已有事务，沿用Service的固定IllegalStateException。null taskId、expectedVersion<1为PARAM_INVALID。持有admissionLock后固定顺序：

1. findTask；不存在TASK_NOT_FOUND。先核对expectedVersion和所需状态：RETRY为FAILED/PARTIAL_FAILED，RESUME为INTERRUPTED，否则TASK_STATE_CONFLICT。
2. 要求已绑定且完成启动、未关闭、无待处理存储不确定性；不满足为TASK_STATE_CONFLICT。要求无活动worker租约，含尚未进入runNext的提交窗口，否则TASK_STATE_CONFLICT。本版租约是全局单worker占用，因此两个控制操作均在worker空闲时才可接受；普通新submit仍可在worker活动期间排队。
3. 通过T07已有包内executionDefinition复用validateReplay共同校验：current→definitionHash→normalize，定义变化先于新参数拒绝；保留PLUGIN_DISABLED/BATCH_DOWNLOAD_UNAVAILABLE等分类和纯全范围预检，零来源调用。随后读取这次已验证plugin.readiness()：downloadAvailable=false为PLUGIN_DISABLED，null或意外RuntimeException为DATASET_MISCONFIGURED，固定文案、无原cause。不能只依赖PluginRegistry的启动快照。此检查位于容量/requeue之前，不改变validateReplay或旧submit的语义。Service.settings.enabled=false继续为PLUGIN_DISABLED，不修改摘要。
4. queuedCount>=maxQueuedTasks为TASK_QUEUE_FULL，尚未进行requeue，原任务全部事实保持。
5. 调repository.requeue(taskId,expectedVersion,activeRunId,RETRY/RESUME,clock.instant())。提交后仍在同一锁内findTask取得QUEUED结果；不得把客户端版本替换成新读到的版本悄悄重试。
6. 返回已提交任务，轮询最迟下一秒可发现它。若requeue或其后回读失败，传播固定仓储错误，不重试requeue；客户端可重新查询。QUEUED数据库事实不会因回执丢失被删除或重复创建。

无需额外内存唤醒接口；每秒数据库轮询是可靠通知路径。版本增加与本轮时间清理由现有requeue事务提供，领取才增generation/attemptCount，不重复更新字段或成功计数。

原submit继续首先查submissionId，已存在的同义请求在关闭/恢复/队列满时仍可找回；**只在确认新键之后**检查已绑定协调器是否允许接收。未完成启动/关闭使用PLUGIN_DISABLED，RECOVERING/FAULTED使用TASK_STATE_CONFLICT；之后沿用当前本地检查与容量事务顺序。不把这个动态门禁加进validateReplay或definitionHash。

`controls(task)`为查询提示，在同一锁下只组合绑定/启动状态、settings.enabled、无活动租约、无恢复暂停，以及task.status。允许状态分别对应canRetry/canResume；其他情况false/false。它不读数据库、不调用插件/normalize、不预留队列、不保证操作一定被接收；HTTP操作仍严格复验版本、定义、当前可用性和容量。null task为PARAM_INVALID，外层事务拒绝。T09将结果映射DTO，不在T08修改现有QueryService的事实查询合同。

### 启动恢复与运行轮询

start仅允许一次；RUNNING/RECOVERING期间重复start幂等无新线程，FAULTED/CLOSING/CLOSED后start拒绝固定IllegalStateException。启动过程中锁住接收/协调门禁：

1. 前提为Flyway及DatasetCatalog验证已完成，activeRunId是此后生成的新随机UUID。
2. unfinishedTasks取得已有任务，只处理activeRunId不同的QUEUED/RUNNING。逐项以查询到的启动ID、generation、version调用recoverStoppedTask。按仓储真实叶子事实恢复；不调用runner或任何来源，不清planReady、不重排批次。
3. 每次恢复事务独立提交；若查询或恢复失败，启动不开放、不调度worker，保留已完成恢复事实，抛固定分类使配置启动失败。重启应用可重复这一步，已成终态的记录不再扫描。不得捕获失败后继续开放任务API。
4. 全部成功后设置RUNNING，调用scheduleWithFixedDelay，初始delay=0、delay=1秒；无并发poll。若调度提交被拒绝，进入FAULTED、关闭接收并释放尚无实际worker的资源，抛固定INTERNAL_ERROR；不得退回可自动重试的STARTING。

每个tick先取得协调锁。若关闭、FAULTED、settings关闭、已有活动租约，则不派发。若RECOVERING，执行下一节的只读核对/恢复，成功后留给下一tick正常派发。正常派发不先在poller查询队列再把候选传给runner；runner已经拥有queuedTasks(activeRunId,1)的唯一选择入口。

在调用worker.execute之前创建活动租约并保存于协调器。租约至少包含唯一对象身份、单调AtomicBoolean stop、可空RunResult；它从**提交前**覆盖尚未进入runner的队列窗口。execute拒绝时，只有该runnable确实未被接受才清该租约；固定INTERNAL_ERROR并进入FAULTED，不用CallerRuns降级、不在下个tick用数据库探针解除。每秒空队列最多一次runNext→IDLE，不把轮询计为来源请求。

runnable在try中同步调用runner.runNext(lease.stop::get)，在finally取得同一协调锁，保存返回处置并移除**同一个**租约。只有这里释放活动占用；超时、Future状态、HTTP断开和stopRequested都不能提前释放。不持锁调用runNext，不以Future.done/cancel触发恢复。JVM Error不包装成可重试任务失败；finally仍释放真实退出后的租约并进入FAULTED，重抛Error。意外RuntimeException同样进入FAULTED并固定INTERNAL_ERROR，不自动继续；这类没有可靠RunResult的异常要求应用重启后按数据库事实恢复，不能猜taskId改状态。FAULTED不创建虚假的NEEDS_RECOVERY RunResult，不走taskId=null探针捷径；worker只设置状态，不从自身线程调用会等待termination的close。

### T07返回处置与数据库恢复

- IDLE：无任务状态修改；正常继续后续tick。
- FINISHED：终态已存，释放租约后继续轮询；不再次finishTask、不重算并写计数。
- PERMIT_LOST：不以返回旧许可写任何任务/批次，不恢复该任务；释放租约后下个tick可继续当前启动ID其他QUEUED。记录固定分类的内部结果供T09日志使用，日志细节不在本项实现。
- NEEDS_RECOVERY：保留原RunResult，设置RECOVERING；停止新派发及新提交/控制，实际runnable finally退出后才允许下个tick访问以下恢复路径。等待连接期间只重试数据库读/恢复，零上游调用。

有taskId时：snapshot读取当前事实；任务不存在、已是终态、启动ID不等于当前activeRunId或非空permit.generation不等于当前generation时，不覆盖其状态，清理该不确定结果。仍是本启动且同已知轮次的QUEUED/RUNNING时，使用**这次snapshot的version**调用recoverStoppedTask；这不是用户retry/resume，不使用过时expectedVersion。permit=null表示claim回执不明，可保留候选taskId，仅在相同启动ID且无任何活动租约时采用数据库当前generation；已有成功叶子由仓储重算，不重发。恢复提交冲突时下个tick重新snapshot，不以旧参数循环写入。

NEEDS_RECOVERY的taskId=null只可能是T07候选查询失败；FAULTED始终在此分支之前被排除：不得选一个任务猜测恢复。用queuedCount做本地数据库可用性探针，成功后清恢复暂停；既有QUEUED仍可由下一tick正常首次领取。这是恢复轮询能力，不是自动重试失败批次。若taskId已知则不使用这个无身份捷径。

任何恢复读写异常保持RECOVERING，下一秒再次核对，ErrorCode固定且不保留原始message/cause。恢复期间关闭时不再继续轮询；如果数据库仍不可用，保留原记录给下一次应用启动处理，不声称已保存INTERRUPTED。

### 正常关闭与实际退出

close幂等。先在协调锁内进入CLOSING，关闭新接收/控制/派发，活动lease.stop置true且不复位；再在锁外shutdown poller和worker。不调用shutdownNow、Future.cancel或Thread.interrupt打断已获准证券事务，也不丢弃已登记的runnable（它进入runner后看到stop返回IDLE）。

close在锁外等待worker实际termination；允许同步来源返回和已获准数据库事务完成，finally依旧能取得协调锁释放租约。等待线程自身被中断时记住中断并继续等待，退出前恢复其中断位；不能在中断异常路径假装worker已退出。不增加一个“等待超时就释放活动标记”的捷径。普通插件必须自身有有限I/O超时；永不返回时正常关闭也等待，需要运维强制停止进程，这是T07已明确的兼容限制。

worker完全退出后，在同一协调锁下调用unfinishedTasks，逐项恢复**当前activeRunId**的QUEUED/RUNNING，使用本次读取的generation/version。这是关闭当前实例的所有未完成工作，包含接受后尚未进入runner的队列；不能只处理最后一个RunResult而把其他已接收任务遗留为正常QUEUED。已是终态的不在扫描中，其他启动ID不改写；若上一结果为PERMIT_LOST，则跳过该taskId，不越过其禁止旧许可改写的边界。无法访问数据库时允许关闭完成但保留未恢复事实，交由下次启动；不无限循环阻塞关闭等待数据库。若runner在关闭前已完整成功，保存或保留SUCCEEDED；未完成才由recoverStoppedTask标中断。随后CLOSED；释放资源不能清空持久计划或批次。没有活跃任务时close零来源调用。

### 应用装配边界

新增 `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadTaskConfiguration.java`，使用**可显式导入的lite配置类**：public final，含@Bean方法，但不标@Configuration/@Component、不改ApplicationConfiguration；T09用@Import明确接线。本项测试通过AnnotationConfigApplicationContext显式register，证明配置完整工作。避免在本项尚未提供生产Service/Runner bean时靠条件扫描生成半套生命周期。

固定bean：

1. `@Bean("downloadTaskRunId") UUID downloadTaskRunId(DatasetCatalog catalog)`：在catalog依赖完成后UUID.randomUUID；catalog已有DependsOnDatabaseInitialization，不能另造未验证目录。
2. `@Bean(destroyMethod="close") DownloadTaskCoordinator downloadTaskCoordinator(DownloadTaskService tasks, DownloadTaskRepository repository, DownloadTaskRunner runner, Clock clock, @Qualifier("downloadTaskRunId") UUID runId)`：绑定唯一协调器。
3. `@Bean SmartLifecycle downloadTaskLifecycle(DownloadTaskCoordinator coordinator)`：start委托start，isRunning委托isRunning，isAutoStartup=true，phase=Integer.MAX_VALUE；stop及stop(Runnable callback)调用close，实际退出/关闭完成后才执行callback。不在SmartLifecycle之外再创建调度线程。

T09负责让Service/Runner的构造器使用同一runId、同一配置的enabled/maxRangeDays以及同DataSource仓储/证券事务；本项不增加第二套默认预算属性或先导入该配置。配置测试需明确真实数据库初始化/catalog成功先于runId和start，失败时零来源；不以当前生产未启用证明生命周期通过。

## Files

- 修改core `download/task/DownloadTaskService.java`：控制用例、纯controls提示、同一锁包内复用、一次绑定和新键接收门禁。
- 新增同包 `DownloadTaskCoordinator.java`：唯一调度、租约、启动/异常恢复及关闭；包内可控executor构造器仅供测试。
- 新增app `config/DownloadTaskConfiguration.java`：上述显式导入lite bean与SmartLifecycle适配；现有生产ApplicationConfiguration保持不变，T09再导入。
- 新增core `DownloadTaskCoordinatorTest.java`、`DownloadTaskRecoveryIT.java`；扩展DownloadTaskServiceTest/IT验证原幂等与新增控制。共用T07受控来源确有复用时提取同包测试帮助类，不创建生产测试开关。
- 新增app `config/DownloadTaskConfigurationTest.java`，显式注册配置和受控协作者；需要MySQL的装配顺序用新增 `DownloadTaskConfigurationIT.java`，复用已有MySQL8.4.6/V8环境。
- 不改公共插件API、T07 runner表面、Repository表面/V8、40项YAML、前端和HTTP合同。

## Tests

首个实施动作：在DownloadTaskRecoveryIT建立T07真实三批任务，第二批SOURCE_NETWORK_ERROR、第一/三批已提交；调用尚不存在的service.retry(taskId,expectedVersion)，先观察缺少入口失败。实现最小控制路径后证明下一轮只再次下载第二批，计数从2增至3、成功叶子attemptCount不变、第二批attemptCount为2。继续以下场景，不以纯mock替代数据库转换事实。

| 层级 | 场景与必须结果 |
| --- | --- |
| Service/真实MySQL | FAILED/PARTIAL_FAILED retry；INTERRUPTED resume保留普通FAILED，只重排中断批并继续PENDING；SUCCEEDED/SPLIT不逆转；planReady=false手动重试重新规划，已保存树不重新plan |
| 控制竞争 | 两个相同expectedVersion操作有且仅有一次成功，另一次409所需TASK_STATE_CONFLICT；旧版本、非法状态、未知ID、非法version各固定分类。队列满和定义变化不改变原状态/时间/attempt/计数；插件注册后readiness变false时retry/resume均拒绝、零requeue，null/意外readiness固定分类；凭证/预算变化不改变hash |
| 生命周期门禁 | 绑定后start前新submit拒绝，同键找回仍成功；运行中有租约新submit可排队但控制拒绝；关闭/恢复暂停时幂等找回仍可用、历史QueryService仍可读。controls不访问来源或数据库，不取代操作校验 |
| 启动恢复 | 同一MySQL重建对象并换runId：旧QUEUED、RUNNING含普通失败/成功/PENDING正确恢复；已计划全部成功仅重算SUCCEEDED、零来源；中途恢复失败不开放/不派发，重复启动不重写终态 |
| 调度窗口 | 测试worker executor接受但不运行runnable时lease已生效，resume拒绝；同时tick只提交一次；executor拒绝没有CallerRuns、零来源、租约不泄漏；worker意外RuntimeException/Error同样进入FAULTED，后续tick和新submit/控制均拒绝，start不可解除，幂等找回保留且close可释放资源；空队列每tick只一次runNext |
| 不确定结果 | T07已有真实doCommit回执故障作为输入，NEEDS_RECOVERY后不再发送，数据库继续失败保持暂停；连接恢复只在实际退出后recover。taskId=null只探针不猜任务；permit=null候选能按同启动事实恢复；新generation/启动ID不被旧结果覆盖 |
| 关闭/迟到 | 阻塞普通SINGLE插件，stop/Futurecancel观察不能释放租约；close仍等待实际返回，返回后零适配/迟到提交；已获准证券事务在stop后共同提交，全部叶子成功保持SUCCEEDED；数据库不可用关闭保留待下次启动恢复事实；关闭时当前启动ID未领取QUEUED也转INTERRUPTED，别的启动ID不动 |
| 运行许可 | 旧permit提交/拆分/失败状态更新拒绝；恢复产生新generation后旧调用不得污染；T04锁序/旧RR快照回归保留 |
| 配置 | 显式register lite配置：Flyway/catalog→runId→service/runner/coordinator→start，单个UUID和worker；停止callback仅实际退出后发生；当前生产配置未提前引入任务HTTP或缺bean启动错误 |

所有并发用有界latch/future等待和可控executor/Clock；轮询测试手动触发tick，不等待大量真实1秒。新增包内tick只允许同包测试调用，生产由唯一poller调用；tick仍执行相同协调锁/状态条件。不把计数expected从实现计算出来，使用明确3批/2成功等固定结果。MySQL固定8.4.6，ScriptUtils执行唯一app V8，共用JdbcTemplate/DataSourceTransactionManager，不使用H2；通过测试事务管理器或连接代理故障真实验证提交/回执。

从仓库根运行，Java21；先核对已记录Colima连接，不清理用户容器：

```sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -Dtest=DownloadTaskCoordinatorTest,DownloadTaskRecoveryIT,DownloadTaskConfigurationTest,DownloadTaskConfigurationIT,DownloadTaskServiceTest,DownloadTaskServiceIT,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskRepositoryIT,BatchCommitServiceIT,PersistenceServiceIT,FixtureDownloadTaskRunnerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
git diff --check
```

预期全部退出0，所列新增类与MySQL IT实际执行、失败/错误/跳过0；前端现有170项与构建随生命周期通过。不得用clean verify替代显式IT，不提前声称T12进程/浏览器闭环或T13真实接口验收。新增文件加入Git，不自动提交；当前分支和已有T06/T07成果保留。

## Acceptance

1. 手动retry/resume状态、版本、容量、定义与计数满足上述数据库事实；三批只重试第二批，不重写成功叶子或已存参数。
2. 同一协调锁覆盖新接收/控制/调度/恢复；lease提交前成立、实际调用finally才释放。数据库状态不明与旧调用未退出时零替代来源执行。
3. 启动旧任务只重算/中断，无自动请求；连接恢复不重发失败批；正常关闭保留原子成功与实际退出约束。
4. 生命周期lite配置可在显式上下文完整验证，T09所需import、同runId及属性装配责任准确。四条Maven及diff门禁具备实际证据，既有同步/40注册/生产RANGE门禁不变。
5. 先记录T08完成与验证，再按Order准备T09专属设计与交接；本设计自身不改变T08为实施中。

## Risks

- 单实例前提未变，启动ID/轮次防迟到并非多实例协调。新进程启动前必须停止旧实例。
- 普通插件永不退出会使正常close等待；不通过取消Future或超时释放租约。需插件自身有限I/O超时或运维强制停止进程。
- MySQL提交回执不明不能等价为回滚；用户控制响应丢失应查询版本/状态，恢复只依据数据库事实。恢复中再次失联保持暂停。
- 显式导入lite配置是T08/T09已划定装配职责的实现选择；T09遗漏@Import或混用runId会破坏启动门禁，必须保留本项配置测试并在T09验证生产接线。
- 全局单worker租约活动时暂不接受retry/resume，用户可继续新建排队任务；controls如实显示当前可操作性。未来若需对其他任务并发接受手动控制，应另扩展按任务身份登记合同。
- 本项准备具备所需来源和行为决定，无待补的实现前需求；真实Tushare34项RANGE仍由T13验证开放。
