# DATA-INTEGRITY-T07 后台执行、预算与中断处理

## Goal

消费 T06 固定计划，在独立单工作线程中执行本地检查并保存可追溯报告；正常计算完成、数据有问题、单元错误和任务中断各自明确，重启不重新计算旧快照。

## Scope

新增 `IntegrityCheckRunner`、检查协调器与生命周期装配，复用 T03 只读一致快照、T04 `IntegrityUnitEvaluator`、T05 原子报告和 T06 队列/配置。补齐任务状态写入、已提交进度读取、启动中断与来源参考范围装配的兼容入口。

不实现 T08 的 Tushare 日周月推导、HTTP/前端、补数、自动重试、分布式调度或通用任务平台；不修改证券表、V9 或下载队列。可靠 fixture 的结论不代表 Tushare 生产基线。

## Approach

### 1. 同步任务执行入口

core 新增 `IntegrityCheckRunner`，构造依赖 `IntegrityCheckRepository`、`IntegrityCheckService`、`PluginRegistry`、`IntegrityCheckJson`、`IntegrityUnitEvaluator`、`Clock` 和 T06 `Settings`。公开 `void run(UUID checkId, BooleanSupplier stopping)`；它只处理指定已受理任务，不直接 poll，不在证券读取或整个任务外层加事务。时间预算从成功 QUEUED→RUNNING 时开始，排队时间不计入。

流程固定为：

1. 仓库 `start(checkId, startedAt)` 原子比较状态，仅 QUEUED 可转 RUNNING；返回 false 时忽略旧/重复队列项，不能重算历史。保存 started_at/updated_at，原 created_at、scope.acceptedAt 不变。
2. 以 `results(checkId, null, page, 100)` 稳定分页读取固定计划；不新增/移除单元，不按当前股票表重建范围。分页以不可变 api_name/symbol/result_id 排序，写单元状态不改变页归属。
3. 每单元开始前检查任务 deadline/停止标记，再核验当前完整能力hash与任务保存hash；失败不能使用新版规则运行旧单元。核验成功后比较当前对应 ApiSnapshot 的规范JSON与 ResultRecord.definitionSnapshot；同组当前快照用于本次装配，不再读取第二份descriptor。来源规则实现取得一次，并与保存的规则描述再匹配。
4. 缺描述与NON_STOCK走第3节直接报告；股票单元构造读计划与共享预算，调用 `IntegrityUnitEvaluator.evaluate`，将 BoundIssue 映射为仓库 NewIssue 后一次 `saveResult`。只有报告和问题提交成功才算进度。
5. 单元ERROR继续其他单元；任务deadline/停止标记使任务INTERRUPTED；任务计划/进度/报告仓库的查询或写入失败使任务FAILED；证券扫描失败按下文保留为单元ERROR并继续。所有单元均为COMPLETED/ERROR时才可任务COMPLETED；overallStatus由保存结果聚合，不能因为任务COMPLETED强制PASS。

首次核验即发现完整hash变化时，各待执行单元均以保存的口径写 ERROR/DEFINITION_CHANGED，无证券扫描，任务在全部单元落库后可COMPLETED并显示errorUnits。若处理中能力改变，保留早先结果，后续单元同样ERROR。插件不可用为 ERROR/INTEGRITY_UNAVAILABLE；非法当前元数据为 ERROR/DEFINITION_CHANGED。任务/报告仓库查询失败不能伪装成定义变更，而是任务FAILED/QUERY_FAILED；这不同于Evaluator返回的证券扫描READ_FAILED。

### 2. 保存口径与参考读取装配

任务/单元的历史JsonNode不能用当前规则重建。为 `IntegrityCheckJson` 新增 `readScope(JsonNode) -> IntegrityScope` 和 `readDescriptor(JsonNode) -> IntegrityDescriptor`（null描述返回null）。只解码生成未扫描报告所需的 scope、descriptor、规则及依赖；DatasetDefinition仍从已通过完整快照比对的当前 ApiSnapshot 取得，不新增通用反射反序列化框架。拒绝缺字段、额外字段、错误类型/枚举/时区/日期/时间，容器防御复制，并校验 `readValue(write(decoded)).equals(input)`；坏存储内容统一安全QUERY_FAILED，原始内容不进入错误。

来源参考窗口不能由core猜测。给 `IntegrityCheckSupport` 增加兼容的纯本地默认方法：

```java
default List<IntegrityReadRequest> integrityReferenceReads(IntegrityScope scope) {
    Objects.requireNonNull(scope, "scope");
    return List.of();
}
```

这是来源声明本单元需要的参考范围和等值条件，不执行读取、不暴露core/JDBC类型。返回空列表仍允许目标规则运行；需要参考的来源在规则/能力版本变更时覆盖该方法。现有Tushare规则不读取参考，T07保持默认空列表，T08再提供来源窗口与交易所；fixture测试可以声明受控参考。

core新增包内 `IntegrityReadPlanner`，入口 `IntegrityReadPlan plan(IntegrityScope scope, ApiSnapshot target, List<ApiSnapshot> pluginSnapshot, List<IntegrityReadRequest> references)`。每项必须同来源、是target.dependencies中声明的datasetKey/purpose、投影列不超声明，且当前完整快照有对应descriptor；同datasetKey/purpose只允许一项，nullDates=false。将来源dateField/dateRange/equalities固定成ReferencePermit。目标原scope不可修改。股票参考使用同股票/原范围或当前快照；NON_STOCK按T03要求单个交易所/完整物理键等值和原/ISO周/自然月窗口。T03 `validatePlan` 与scan继续执行最终独立授权，来源提议不能绕过它；不在core硬编码tushare_pro、exchange或股票后缀。来源提议无效为单元ERROR/INVALID_READ_REQUEST，消息固定且无输入值。

`integrityReferenceReads`必须在执行时使用已核验来源版本；改变其范围语义须升级来源capabilityVersion/对应rule version，仍由既有完整hash捕获。新增default方法不改变旧插件下载/本地能力兼容。

### 3. 不扫描的报告

从已保存PENDING报告读取scope、descriptor、definitionHash，不借用当前元数据。真正未扫描的PENDING单元保持snapshotStartedAt=null；恢复/终止兼容已有RUNNING单元时，保留已保存scope.snapshotStartedAt及对应SQL时间，不清除、不重新指定，否则会违反T05的scope不变约束。finishedAt为当前时刻，publishedRange=null、各数量与coverageRate=null、ruleResults/issues/evidence为空。

| 情况 | unitStatus | coverage/key/field | incomplete / issuesComplete | reasonCode |
| --- | --- | --- | --- | --- |
| 缺descriptor，且能力核验未变化 | COMPLETED | UNKNOWN / UNKNOWN / UNKNOWN | false / true | RULE_NOT_IMPLEMENTED |
| NON_STOCK，且能力核验未变化 | COMPLETED | NOT_APPLICABLE / NOT_APPLICABLE / NOT_APPLICABLE | false / true | NON_STOCK_SCOPE |
| 定义/规则变化 | ERROR | UNKNOWN / UNKNOWN / UNKNOWN | true / false | DEFINITION_CHANGED |
| 当前本地插件不可用 | ERROR | UNKNOWN / UNKNOWN / UNKNOWN | true / false | INTEGRITY_UNAVAILABLE |
| 停止/超时/框架失败后的剩余单元 | NOT_RUN | UNKNOWN / UNKNOWN / UNKNOWN | true / false | 对应任务终止原因 |

无描述仍不能PASS，N/A只用于明确NON_STOCK。任务终止时已提交COMPLETED/ERROR不改写；剩余PENDING/RUNNING统一NOT_RUN。未扫描单元不假造读取时间或空集合统计。

### 4. 单元及任务预算

复用T06 Settings：scanBatchSize=500、累计读取/生成预期键maxScannedRowsPerUnit=500000、maxIssuesPerUnit=20000、unitTimeoutSeconds=120、taskTimeoutSeconds=1800。每单元新建一个预算，全部目标、空日期、参考读取及compare预期键生成共享它；达到计数上限允许、下一项终止。SQL超时及CPU检查继续沿用T03/T04，不给每条规则重置预算。

单元deadline为 `min(unitStart+unitTimeout, taskStarted+taskTimeout)`；正数配置很大导致Instant加法溢出时按Instant.MAX饱和，不能反向变为已到期。到deadline即停止。给IntegrityReadBudget保留原构造器并新增带`BooleanSupplier stopping`的重载，check在已锁存失败之后检查停止标记及线程中断，以安全reasonCode `EXECUTION_INTERRUPTED`锁存；原构造器使用永不停止的supplier，兼容T03/T04。

Evaluator已有按ruleId执行、普通规则异常隔离、累计预算与incomplete结果，不在Runner重复实现。普通来源异常仍为规则UNKNOWN/RULE_EXECUTION_FAILED，其他独立规则继续；已发现FAIL证据不丢失。读取/预算/问题上限错误得到ERROR，已知问题标incomplete、issuesComplete=false，整体与全部子规则的expected/matched/coverageRate清空。

Evaluator返回后再次检查task deadline/停止标记；若本单元已返回完整Evaluation则先原子保存它，再把任务置INTERRUPTED（即使刚好是最后一个单元，也不把截止事件改称正常完成）。若本单元因预算或停止返回ERROR，也先保存其已知问题，再中断剩余单元。任务超时reasonCode为TASK_TIME_BUDGET_EXHAUSTED；普通单元超时只ERROR并继续。单元ERROR可以与总体FAIL共存。

明确区分两类读取失败：T03连接、证券SELECT或快照清理失败产生READ_FAILED，T04返回`Evaluation(result.unitStatus=ERROR, reasonCode=READ_FAILED)`；Runner先保存其不完整报告/问题，再继续下一单元，不转任务FAILED。只有任务计划/报告仓库抛QUERY_FAILED，或报告提交抛PERSISTENCE_FAILED，才执行任务级FAILED终止；来源回调包装进READ_FAILED也遵循单元语义。

### 5. 仓库状态与进度

扩展现有 `IntegrityCheckRepository`，继续要求调用时没有外部Spring事务：

```java
boolean start(UUID checkId, Instant startedAt);
void complete(UUID checkId, Instant finishedAt);
void terminate(UUID checkId, IntegrityTaskStatus status, String reasonCode, Instant finishedAt);
int interruptUnfinished(Instant interruptedAt);
Optional<Progress> progress(UUID checkId);
record Progress(TaskRecord task, long completedUnits, long errorUnits, long notRunUnits,
        Map<IntegrityStatus, Long> statusCounts, IntegrityStatus overallStatus) {}
```

- start通过行锁/CAS仅转换QUEUED；complete锁任务并验证status=RUNNING、全部计划单元均COMPLETED/ERROR，其他情况安全拒绝，不能伪造成功。完成时间不早于开始/创建时间。
- terminate只接受FAILED或INTERRUPTED，原因白名单固定为：INTERRUPTED仅TASK_TIME_BUDGET_EXHAUSTED/EXECUTION_INTERRUPTED；FAILED仅QUERY_FAILED/PERSISTENCE_FAILED/INTERNAL_ERROR（未分类框架RuntimeException归INTERNAL_ERROR）；不提供任意异常message参数，仓库按原因保存固定安全说明。锁任务后在同一个短写事务中把全部剩余单元的报告JSON与冗余SQL字段原子更新为第3节NOT_RUN，再写任务终态。提取private事务内报告写辅助方法以复用saveResult验证，不能从事务内部调用公开saveResult绕过ownTransaction防御。
- 已终态任务不得回退或改写报告；同一已终止任务重复terminate为幂等无修改。已完成的单元结果与问题长期保留，原始scope/定义/规则版本不动。
- interruptUnfinished按稳定created_at/check_id分批选择QUEUED/RUNNING，每个任务用自己的短事务执行INTERRUPTED/EXECUTION_INTERRUPTED及NOT_RUN；包含已提交未publish的任务。启动前执行完毕，失败使启动失败，不能继续接收后假装恢复完成。
- progress在一个RR只读事务中读TaskRecord和按unit_status/overall_status聚合结果。completedUnits是已处理的COMPLETED+ERROR，errorUnits是其中ERROR子集；notRunUnits单列。statusCounts包含每个IntegrityStatus键（无结果的值为0）并防御复制，覆盖全部计划单元，PENDING/RUNNING/NOT_RUN维持UNKNOWN；overall遵守FAIL>UNKNOWN>WARN>PASS、全N/A为N/A。不计算跨接口总覆盖率，不从部分明细推算进度。
- 查询/写入失败仍为QUERY_FAILED/PERSISTENCE_FAILED；任务FAILED原因可保存这两个代码。若标记FAILED本身也写失败，保留数据库可证状态、仅记录固定安全日志并继续协调器循环；下次进程启动由interruptUnfinished处理遗留，不宣称失败终态已经落库。

单元无需单独持久RUNNING中间态：T05 PENDING可直接原子提交COMPLETED/ERROR，读取中进度只见未完成。RUNNING单元仍在恢复兼容范围内。

### 6. 单工作线程与应用生命周期

core新增 `IntegrityCheckCoordinator implements AutoCloseable`，构造queue、runner、repository、service、clock；`start()/isRunning()/close()`。应用Bean工厂在返回IntegrityCheckService之前调用stopAccepting，服务以关闭首次受理的STARTING状态进入容器，避免最高phase生命周期晚于Web服务器启动的窗口。start先interruptUnfinished，再且仅一次启动命名为`tensor-integrity-worker`的线程，成功后调用startAccepting开放首次受理；恢复/线程启动失败保持关闭并让应用启动失败。循环 `queue.poll(Duration.ofSeconds(1))`，取得任务调用runner.run。同一协调器仅启动一次，重复start不能多开线程，close后的start抛IllegalStateException，不能重启；新应用用新实例。每次run异常做安全日志后继续，不复用下载锁或协调器。

为 `IntegrityCheckService` 增加对称的 `stopAccepting()/startAccepting()`（纯受理门禁，不新增配置）：均使用既有admissionLock；stopAccepting关闭首次受理并等待已进入锁的create/publish完成，startAccepting仅由成功完成恢复并启动worker的协调器调用；submit仍先重放旧请求，新ID在关闭后以INTEGRITY_UNAVAILABLE拒绝，不新增记录。协调器close先调用stopAccepting，再设置可见停止标记并唤醒空闲poll；正在执行时预算在规则/扫描边界观察取消，SQL受剩余超时约束。等待线程实际退出后才完成stop回调；在工作线程自身调用close时不能join自身。已排队但未执行的任务在关闭时统一INTERRUPTED/NOT_RUN，消费/释放余下队列项；没有新提交入口时完成关闭，不为旧队列自动重建任务。新应用实例的队列为空；生命周期恢复在Web应用对外就绪前执行。

`IntegrityCheckConfiguration`增加ReadRepository、UnitEvaluator、Runner、Coordinator及SmartLifecycle，继续显式import；使用现有DataSource、Catalog、UTC Clock和同一Settings。SmartLifecycle自动启动、最高phase优先关闭，stop(Runnable)在协调器确已停止后回调；销毁close幂等。T06的“无worker”装配测试在T07改为生命周期和受控运行断言，不保留过期假设。

协作取消不能强制终止不调用任何上下文方法的恶意/无限来源代码；当前内置规则遵守预算检查。T07不增加多线程强杀机制或宣称对任意第三方代码有硬实时隔离。

## Files

| 路径 | 责任 |
| --- | --- |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckRunner.java` | 单任务状态、计划遍历、版本核验、预算、单元原子报告 |
| 同目录 `IntegrityCheckCoordinator.java` | 单消费者、恢复、停止生命周期 |
| 同目录 `IntegrityReadPlanner.java` | 来源参考声明转受限读计划，不包含Tushare分支 |
| 同目录 `IntegrityCheckService.java` | 启动恢复/关闭期间的锁内首次受理门禁，保留旧请求重放 |
| 同目录 `IntegrityCheckRepository.java` | 状态转换、剩余NOT_RUN原子终止、启动恢复和进度 |
| 同目录 `IntegrityCheckJson.java` | 保存scope/descriptor严格解码，不变更编码和历史hash |
| 同目录 `IntegrityReadBudget.java` | 兼容构造器与协作取消检查 |
| `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/IntegrityCheckSupport.java` | 默认纯本地参考读取声明 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/IntegrityCheckConfiguration.java` | evaluator/runner/coordinator和SmartLifecycle装配 |
| core测试 `IntegrityCheckRunnerTest.java`、`IntegrityCheckRunnerIT.java`、`IntegrityCheckCoordinatorTest.java`、`IntegrityReadPlannerTest.java` | 状态、定义变化、真实快照/持久化、生命周期和授权 |
| 既有 `IntegrityCheckRepositoryIT.java`、`IntegrityCheckJsonTest.java`、`IntegrityReadRepositoryTest.java`、`IntegrityCheckConfigurationTest.java` | 状态事务、严格历史解码、取消与新生命周期回归 |
| `docs/runbook/configuration.md`、`docs/verification/DATA-INTEGRITY-T07.md`、任务看板 | 执行预算已生效的说明、真实证据与状态 |

Tushare具体参考范围选择和候选规则仍由T08修改插件，T07只测试来源中立的fixture reference hook；不提前改生产覆盖判断。

## Tests

第一项RED：真实MySQL保存T06任务及多单元，直接调用尚不存在的Runner；期望QUEUED→RUNNING→COMPLETED且一个确定缺失报告使整体FAIL、数据库问题可定位。由该失败开始实现，不能仅验证线程启动。

| 案例 | 必须观察到的结果 |
| --- | --- |
| 原子状态/进度 | start只成功一次；混合COMPLETED/ERROR才允许complete；报告提交前仍PENDING/UNKNOWN，保存问题后进度原子增长。 |
| 完整范围 | 多股票、NON_STOCK、缺描述均保留；没有证券记录的fixture股票可报告全部缺失；不扫描的单元时间与null统计符合第3节。 |
| 版本与兼容 | 接受后改定义/规则版本或新增接口，待执行单元ERROR/DEFINITION_CHANGED且证券读取为0；旧报告内容不变；旧插件/fixture default hook兼容。 |
| 参考授权 | 同快照目标和参考、跨页并发写入只在下一单元可见；错误股票、交易所覆盖、异源/未声明/扩张窗口拒绝；core无来源分支。 |
| 规则隔离 | ruleId稳定顺序；一条throw而另一条确认缺失，保存UNKNOWN与FAIL两项证据，任务正常完成但overallFAIL。 |
| 预算 | 扫描+参考+预期键累计等于上限允许、下一项停止；问题上限保留已知问题且不完整；可注入Clock恰到unit/task deadline，任务级中断剩余NOT_RUN。 |
| 扫描与报告查询失败边界 | 第一单元证券SELECT故障使Evaluator返回ERROR/READ_FAILED，保存后第二单元继续，任务可COMPLETED；计划/报告仓库QUERY_FAILED则任务FAILED且剩余NOT_RUN。 |
| 持久化异常 | 注入问题插入/报告写失败，无半报告；任务FAILED且剩余NOT_RUN；终止写事务故障回滚，下一次重启可中断遗留。 |
| 启动恢复 | QUEUED（含提交未publish）、RUNNING及已有部分终态报告混合恢复；只改未完成单元，旧终态/问题/规则不变，不调用来源或重算；构造已有snapshotStartedAt的RUNNING报告，恢复为NOT_RUN时scope与读取时点原样保留。 |
| 生命周期 | 启动恢复barrier期间新ID拒绝、旧请求可重放，恢复后才开放新提交；默认一线程、重复start不加线程，停止空闲poll及时退出；正在扫描时取消并等待实际结束，关闭释放连接；关闭与提交竞争时，在途create/publish先完成，新ID拒绝、旧请求可重放，排队任务无遗漏；下载协调器独立。 |
| 数据安全 | 检查前后证券表逐行一致；Task/报告/日志无SQL、Token、异常堆栈原文；大Long和null统计保持精确。 |

```sh
mvn -o -f data-plane/pom.xml \
  '-Dtest=IntegrityCheckRunnerTest,IntegrityCheckCoordinatorTest,IntegrityReadPlannerTest,IntegrityCheckJsonTest,IntegrityReadRepositoryTest,IntegrityCheckConfigurationTest,IntegrityPluginContractTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityCheckRunnerIT,IntegrityCheckServiceIT,IntegrityCheckRepositoryIT,IntegrityComparisonIT,IntegrityReadRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

预期BUILD SUCCESS、0失败/错误/跳过，IT实际使用MySQL8.4.6；授权运行本机Docker/测试端口。当前本文只设计T07，未执行或宣称这些新增测试已存在/通过。

## Acceptance

T07看板验收全部成立：固定计划逐单元持久完成；运行状态与数据结论分离；单元异常不丢失已知问题、不发布部分覆盖率；任务预算/停止/重启明确INTERRUPTED和剩余NOT_RUN；报告失败不留半份明细。所有进度来源于已提交单元，旧版本不重算；真实MySQL、生命周期和既有回归有实际通过记录。新文件加入Git，保留隔离工作区。

## Risks

- T03/T04现有完整预算和安全错误必须复用，避免Runner再次计算统计或重置预算。
- 来源参考提议是受限请求而非任意SQL授权；T08负责Tushare交易所/周期语义，不能在T07默认猜测。
- 数据库故障可能阻止FAILED状态写入，必须保留“未成功保存终态”的事实，由恢复处理；不能假报持久成功。
- 单实例内存队列不保证进程崩溃后继续执行；这是明确的中断后新建检查语义。
- 工作区已有混合暂存基线；不提交全部历史改动、不合回原分支。无待用户决定的产品范围缺口。
