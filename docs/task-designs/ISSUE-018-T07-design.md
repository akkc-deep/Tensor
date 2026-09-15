# ISSUE-018-T07：通用批次执行、拆分与资源预算

## Goal

使已接收的任务通过一个与 HTTP 无关的串行执行器完成领取、规划、取数、完整性判断、二分和原子提交；成功、失败及未执行批次均以数据库事实呈现。任务身份来自 [ISSUE-018 看板 T07](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t07)，Order 7，直接依赖已完成的 [T03](ISSUE-018-T03-design.md)、[T04](ISSUE-018-T04-design.md)、[T06](ISSUE-018-T06-design.md)。共同依据为 [总体设计](ISSUE-018-design.md) §3.2、§3.5、§3.7–3.9、§3.13–3.14、§5.1。

本设计在 T06 完成后准备 T07，尚未实施。全部 34 个生产 Tushare RANGE 仍为 NEEDS_VERIFICATION；执行器用受控插件验收，不将策略算法测试作为真实来源开放证据。

## Scope

新增 `DateRangePlanner` 和 `DownloadTaskRunner`，实现一次领取一项任务的同步工作循环、计划验证与保存、持久参数执行、二分、预算预约、失败处置和任务终态。补充 T03 的包内执行定义入口以复用当前定义校验，并为 T04 增加兼容的提交停止检查重载。测试直接提供协调器、停止信号、Clock 与真实 MySQL 事务。

不实现 T08 的 retry/resume、队列轮询、启动恢复、活动登记实现或关闭装配，不实现 T09 的 bean、Controller、配置绑定和日志/MDC。无自动重试、自动恢复、HTTP 任务路由、前端、分页游标、数据库迁移或生产 Tushare 开放。不修改 T01 公共插件合同、T05 传输实现、旧同步 `DownloadService`、现有 40 项注册及 YAML，不增加 core 到具体插件的依赖。T08 可直接消费本文固定的同步调用、执行许可和返回处置合同。

## Approach

### 现有接口与本项新增边界

生产代码位于 `com.akkc.tensor.core.download.task`。以下仓储方法已经存在，直接复用其短事务；不要按设计伪造另一个 DAO：

| 已有方法 | 执行器用途 |
| --- | --- |
| `queuedTasks(UUID activeRunId, int limit)` | 当前启动 ID 的 `queued_at,task_id` 顺序候选，limit=1 |
| `claimTask(UUID taskId, UUID activeRunId, Instant now, Instant deadline)` | 返回 `Optional<DownloadTask>`；条件领取并递增 generation/version，清本轮预约数 |
| `savePlan(ExecutionPermit, List<NewBatch>, int maxNodes, Instant now)` | 原子插入全部根批并置 planReady，包括合法空交易日计划 |
| `pendingBatches(UUID taskId)`、`claimBatch(ExecutionPermit, UUID batchId, Instant now)` | batch_key 顺序取待执行批，领取才递增 attemptCount |
| `reserveRequest(ExecutionPermit, long maxRequests, Instant now)` | 每个实际来源请求前提交累计/本轮预约，检查许可和截止 |
| `split(ExecutionPermit, UUID parentId, NewBatch left, NewBatch right, int maxNodes, Instant now)` | SPLIT 父批和两子批同事务，父计数归零 |
| `failBatch(ExecutionPermit, UUID batchId, ErrorCode, Instant now)` | 事务外记录当前 RUNNING 批失败，拒绝旧许可 |
| `snapshot(UUID taskId)`、`counts(UUID taskId)`、`batches(...)` | 一致状态/计数、累计成功行数、首个失败原因 |
| `finishTask(ExecutionPermit, DownloadTask.Status target, ErrorCode error, Instant now)` | 在无 RUNNING 批时校验叶子事实并提交终态 |

`ExecutionPermit(UUID taskId, UUID activeRunId, int runGeneration)` 和 `NewBatch(UUID batchId, String batchKey, DateRange range, Map<String,Object> sourceParams)` 是实际已有嵌套 record。`Counts` 的 `totalBatches` 是非 SPLIT 叶子数，节点总数是 `totalBatches + splitBatches`；`sourceRows` 只累计 SUCCEEDED。没有公开 `findBatch`，不以不存在的方法编写失败恢复流程。仓储 `recoverStoppedTask` 已存在，但本项 runner 不调用它；确认实际 worker 已退出后的恢复属于 T08。

新增公开表面固定为：

```java
public final class DateRangePlanner {
    private DateRangePlanner() {}
    public static List<DateRange> split(DateRange range);
    public static List<DateRange> calendarDays(DateRange range);
}

public final class DownloadTaskRunner {
    public DownloadTaskRunner(DownloadTaskService tasks,
            DownloadTaskRepository repository, BatchCommitService commits,
            DownloadTaskJson json, Clock clock, UUID activeRunId, Settings settings);

    public RunResult runNext(BooleanSupplier stopRequested);

    public record Settings(boolean enabled, int maxRangeDays, int maxBatchNodes,
            long maxRequestsPerRun, Duration maxRunDuration, long maxSourceRowsPerTask) {
        public static Settings defaults();
    }
    public enum Disposition { IDLE, FINISHED, NEEDS_RECOVERY, PERMIT_LOST }
    public record RunResult(UUID taskId, DownloadTaskRepository.ExecutionPermit permit,
            Disposition disposition, ErrorCode error) {}
}
```

所有依赖非 null。Settings.defaults 为 `true,36600,10000,5000,Duration.ofMinutes(30),1000000`。数值上限必须为正；maxBatchNodes 还须不超过 999999，来自已有六位根 batch_key 的表示上限，不扩大仓储键格式。maxRunDuration 必须为可表示毫秒数的正 Duration，至少 1ms；构造失败固定 `IllegalArgumentException("Invalid download runner settings")`，无原值/cause。T08/T09 从同一配置把 enabled/maxRangeDays 同时传给 T03/T07，不引入另一套同名生产配置。默认 maxQueuedTasks=100 仍仅由 T03 接收锁控制，runner 不重新检查队列容量。

runNext 必须拒绝调用方已有事务。内部一个 `AtomicBoolean` 或等价非重入占用标记覆盖完整方法，第二次并发/重入调用立即 `IllegalStateException("Download runner is already active")`，不访问数据库。占用在所有实际同步调用都退出后的 finally 释放。方法不创建调度线程、线程池或 Future，不在 servlet 上自动执行；受控测试与未来 T08 的专用 worker 调用它。

RunResult 固定语义：IDLE 的 taskId/permit/error 全 null；FINISHED 必须有 taskId/permit，表示终态已持久，error 为 null 或该终态保存的固定错误码；PERMIT_LOST 有已知 taskId/permit，error=TASK_STATE_CONFLICT；NEEDS_RECOVERY 的 error 非 null，已知 taskId/permit 就保留，候选查询失败可二者皆 null，领取回执不明可只有 taskId。每个非空 permit.taskId 必须等于 taskId。返回值不携带 Throwable、响应或原始消息，也不替代仓储任务状态。

### 复用 T03 当前定义，避免另一套参数/摘要规则

`DownloadTaskService` 当前仅有 public `validateReplay(task)`，校验使用 private `Current`，不能给 runner 直接返回执行对象。本项增加以下**包内**入口与 record，不新增 public 方法或改变构造器：

```java
ExecutionDefinition executionDefinition(DownloadTask task);
record ExecutionDefinition(DataSourcePlugin plugin, DatasetAdapter adapter,
        DatasetDefinition dataset, BatchDownloadDescriptor range) {}
```

提取 validateReplay 的共同私有校验：使用已有 current→definitionHash 比较→normalize 顺序；hash 不同先抛 TASK_DEFINITION_CHANGED，避免新参数校验掩盖定义变化。public validateReplay 保留原语义与异常顺序。包内 executionDefinition 在同一校验基础上返回该次已验证对象，并从已有 adapters 索引获得唯一 adapter；RANGE 的 range 是该次当前 AVAILABLE 描述，SINGLE 为 null。无需给 runner 暴露 ApiDescriptor、目录构造器或重新实现 ParameterValidator。

执行入口另检查返回插件当前 `readiness().downloadAvailable()`：false 为 PLUGIN_DISABLED，null/意外异常为 DATASET_MISCONFIGURED。现有 PluginRegistry 是启动时快照，单靠 registry.find 不能证明测试中/实现中后来变化的 readiness。这个执行前检查不改变 T03 的幂等找回顺序，不把动态接收开关或全局热重载引入本项。

runner 在领取后、每次批次取数前及完整响应处理后到提交前调用此包内入口，避免队列等待、长请求后用已经变化的定义继续执行。当前定义中的凭证、预算不进入摘要；实际批次请求始终使用已存 sourceParams。normalize仍可在每次验证时调用sourceParameters作**整个用户范围的纯预检探针**，其返回值只检查JSON合同并丢弃，不用于重新生成或覆盖已存片段参数；测试必须把这种探针与创建根/子批的映射分别统计。来源承诺的动态语义变更必须更新 policyVersion，runner 无法识别不改变摘要的插件内部语义偷换。

### 领取、许可和 T08 控制合同

runNext 固定流程：

1. 检查无外层事务并取得本实例占用。settings.enabled=false、stopRequested=true 或线程已中断时返回 IDLE，零领取；不清线程中断位。stopRequested 是线程安全、非阻塞、无 I/O、只读的本轮信号，不能在调用中复位。
2. 调 `queuedTasks(activeRunId,1)`；空列表返回 IDLE。只尝试该一项，使用同一个 `now=clock.instant().truncatedTo(MILLIS)` 及 `deadline=now.plus(maxRunDuration).truncatedTo(MILLIS)` 领取。截止计算溢出返回 NEEDS_RECOVERY/TASK_LIMIT_EXCEEDED，保留候选taskId、permit=null，零领取/请求，供协调器停止派发并修正配置。候选查询/领取之间再次检查停止；未领取就返回 IDLE。
3. `claimTask` empty 表示候选已经不满足条件，返回 IDLE，不循环抢另一项。成功返回的实际 task.runGeneration 构造 permit；deadline 从该持久任务读取，不在每批重新开始 30 分钟。随后任何资源检查错误都属于这次已经开始的执行轮次。task 对象仅作不可变身份/参数快照，状态/计数使用仓储新事实。
4. 建立这一轮唯一的 `BatchCallContext`，存固定 deadline 与 permit。`stopRequested()` 合并外部信号和执行线程中断状态；由于 T05 可从辅助 I/O 线程读取 context，还须读取捕获的 ownerThread.isInterrupted()，不能只看调用 accessor 的线程。`beforeRequest()` 先检查停止/截止，再调用 `repository.reserveRequest(permit,maxRequestsPerRun,clock.instant())`，返回后再检查停止/截止。已预约但尚未发送时被停止不撤销预约；它是已消费的预算许可。
5. 任一同步插件调用未返回，runNext 就未返回，本实例占用不会释放。过期/停止后的响应在返回边界被拒绝，不能适配、拆分或入库。对于普通旧 SINGLE 插件，不能仅通过 Future.cancel 推断底层调用已退出；它自身须有有限 I/O 超时，仍未退出时允许暂时保持 RUNNING 与活动登记，不并发启动替代执行。

**给 T08 的所有权约束：** T08 的唯一协调器在把本次 runnable 提交给专用 worker **之前**登记活动租约，覆盖尚未进入 runNext 的排队窗口；同一协调锁保护调度与 retry/resume/恢复的“无活动 worker”判定。该租约由 runnable 的 finally 在 runNext 实际返回/抛出后释放，不能由 Future 的 done/cancel、请求截止、数据库暂不可用或 HTTP 断开释放。内部占用是额外防重复执行保护，不能代替外部租约。

T08 对 FINISHED 只消费数据库终态；NEEDS_RECOVERY 时停止进一步派发，等 actual return 后以新 snapshot 核对同一启动 ID/generation，再用已有 recoverStoppedTask 处理遗留 RUNNING/QUEUED；数据库尚不可用则继续等待，零自动重发。PERMIT_LOST 不改写可能已属于其他轮次的任务。IDLE 允许未来常规轮询继续。NEEDS_RECOVERY 没有 taskId 时表示全局数据库查询失败，应暂停派发并恢复连接，不能猜测一项任务进行修改。T07 的测试协调器实现这些最小顺序证明，但不在生产代码预建 T08 生命周期。

### 日期算法与计划快照

DateRangePlanner.split 对单点抛固定 `IllegalArgumentException("Range cannot be split")`；否则令 `days=DAYS.between(start,end)`、`middle=start.plusDays(days/2)`，返回不可变 `[start,middle]` 与 `[middle.plusDays(1),end]`，按自然日二分，不按周/月/季度或证券日期字段二分。calendarDays 返回不可变升序单点范围，包含两端，在当前日期等于 end 后退出再避免 plusDays；LocalDate.MAX 单点合法，算法不转 int 天数。null range 为具名 NPE；runner 在确认可拆分后才调用 split。

领取后先做 executionDefinition 与控制检查，再从 task.policySnapshot 经已有包内 `json.readRangePolicy` 读取保存的 RANGE 合同，并按保存的 startParameter/endParameter 从 task.params 严格解析八位日期。已有 T03 校验之外，执行器也检查范围天数上限，保护旧任务在预算下调后重执行；不硬编码 from/to 或 start_date/end_date。数据库事实非法/策略解析异常归 DATASET_MISCONFIGURED。

planReady=false 时：

- SINGLE 不调用 batchDescriptor/plan/sourceParameters/assess 的执行分支，构造一条根批 `range=null,sourceParams=task.params,batchKey="000001"`；T03 SINGLE 校验本身也不调用这些 RANGE 方法。
- RANGE 在无事务下恰好调用一次 `BatchDownloadSupport.plan(apiName,task.params,context)`；规划日历请求通过 context 预约。本轮所有请求共享同一 context。插件返回后再次检查控制，并完整验证计划后才生成/保存根批，不保存部分计划。
- 每个合法 DateRange 调纯 `sourceParameters(apiName,task.params,range)`；返回 map 通过 `json.writeBatchParams` 的非敏感字符串合同及 16 KiB 上限，防御复制后进入 NewBatch。不将 task.params 改写为片段；UUID.randomUUID() 生成批次 ID，根键严格为 `String.format(Locale.ROOT,"%06d",i+1)`。
- 构造根批和校验日期的循环逐项检查停止/截止；检查根数 `<=maxBatchNodes`，再次executionDefinition确认规划期间策略未变化，再检查控制后调用已有 savePlan，其事务再次核对许可、截止、节点数和全部合法快照。planReady 只在全计划事务提交后成立。

计划核对规则固定如下，不默默排序、去重、补日或截掉越界项：

| 规划方式 | 必须满足 |
| --- | --- |
| NATIVE_RANGE | 列表恰好一项，等于完整用户闭区间 |
| CALENDAR_DAYS | 每项单点，严格升序，首尾等于用户端点；数量等于自然日数，每个后继是前一天+1，公告周末不省略 |
| TRADING_DAYS | 每项单点，严格升序、无重复、均在用户范围内；允许空列表，完整日历证据由插件负责 |

null 列表/元素、非法参数映射为 DATASET_MISCONFIGURED；合法日期越出用户范围为 SOURCE_RANGE_MISMATCH；其他遗漏/重复/乱序/非单点/原生片段不等于整段为 BATCH_COMPLETENESS_UNCONFIRMED；过多节点为 TASK_LIMIT_EXCEEDED。所有规划失败 task.planReady 仍 false、无批次、无证券行，按 FAILED 保存固定原因。若失败发生在计划事务回执或失败状态写入阶段，走下文 NEEDS_RECOVERY，不猜测计划是否已提交。

planReady=true 时完全跳过 plan 与根批生成，使用保存的 batch_key/range/sourceParams；不因为交易日历变化重新规划、不按当前 sourceParameters 重新生成原有批次。当前 definitionHash/参数组合仍须通过验证。SINGLE 已存根批由仓储合同保证一项无日期；RANGE 的已存批次范围在执行前按保存模式再次核对包含关系与逐日单点，sourceParams 经 JSON 合同检查。完整树与原子写入事实由仓储保证，不重新遍历/合成历史计划。

### 串行批次循环与完整性

每轮从 pendingBatches(taskId) 的当前结果取第一项，领取该 batchId；不一次缓存整份待执行队列，确保二分后的 `/0,/1` 子批按 batch_key 顺序先于后续兄弟处理。claimBatch 的 empty 在单 worker 合同下说明事实已经变化，终止为 PERMIT_LOST；不能忙循环取相同项。成功领取后才允许请求，失败/拆分/成功均不自动重排。

每个批次流程为：

1. 控制检查、executionDefinition、已存片段与 sourceParams 检查。若前一批之后出现停止/到期/定义变化等，在尚未领取本批时直接停止，未执行批保持 PENDING；已经领取的当前批按错误矩阵记录失败或交由中断恢复。
2. `plugin instanceof BatchDownloadSupport` 时，无论 task.mode 是 SINGLE 还是 RANGE，都调用 `downloadBatch(apiName,batch.sourceParams,context)`；runner 不提前为它额外预约。普通插件只允许 SINGLE，由 runner 在 `download` 前调用 context.beforeRequest 一次，再检查控制。旧插件一次方法调用计一个预算单位，不声称知道其内部 HTTP 数。不能调用旧 DownloadService.execute，它会同步持久化并绕过 T04。
3. 调用返回后先检查停止/截止，再调用executionDefinition确认请求期间定义未变化，才验证 envelope：非 null、SUCCESS、pluginId/apiName 和 batch.sourceParams 精确一致，fields 顺序与执行 DatasetDefinition.columns 一致、rowCount/data 行宽一致。结构/FAILURE envelope 为 SOURCE_PAYLOAD_INVALID，身份/参数不符为 SOURCE_RANGE_MISMATCH；不把 envelope.error 直接存储或重新抛出。`DownloadEnvelope` 自身已拒绝部分非法结构，runner 仍检查来源边界。
4. SINGLE 不调用 assess，也不因普通单次结果不完整而拆分。RANGE 调 `assess(apiName,batch.range,envelope)`，null 返回为 DATASET_MISCONFIGURED；UNKNOWN 映射 BATCH_COMPLETENESS_UNCONFIRMED。COMPLETE 进入成功路径；SPLIT_REQUIRED 仅在保存策略 splittable=true、planningMode=NATIVE_RANGE 且 range.start<range.end 时二分，否则 BATCH_COMPLETENESS_UNCONFIRMED。
5. 二分使用 DateRangePlanner.split；纯参数转换分别生成两子批 sourceParams 并经 JSON 校验，batch_key 为父键+`/0`、父键+`/1`。节点预算检查包含已存 SPLIT 父节点；再次executionDefinition及控制检查后，交给 repository.split 再次原子核对；父 envelope 不适配、不入库、不贡献 source_rows。分割/参数转换过程同样检查控制；不二分自然日/交易日计划为任意子区间。
6. COMPLETE 或 SINGLE 成功时，从 counts 读取累计成功 sourceRows，检查本批原始 envelope.rowCount 不超过剩余成功行预算，再调用 adapter.adapt(envelope,clock.instant())。空 envelope 也适配并调用 T04 提交，不能走旧同步零行快捷返回。保留适配器自有字段/类型分类；返回 null/错误 datasetKey 为 DATASET_MISCONFIGURED。上游归属/日期检查不转移到 core 的字段名分支。
7. 适配后控制与 executionDefinition 再检查，再调用本文五参数 commits.commit(permit,batchId,adapted,envelope.rowCount,stopSupplier)。其返回代表证券数据与 SUCCEEDED 已共同提交；不要再调用 succeedBatch 或累计一份独立数据库计数。适配按业务键折叠行不改变 sourceRows 的原始行数，insert/update 由 T04 实际计数。
8. 每次循环结束仅保留必要身份/结果，释放该批 envelope/adapted 引用；不缓存整个任务来源数据。重新取当前 pending 列表，已失败批不会自动执行第二次。

### 提交边界的兼容补充

T04 已有四参数 `BatchCommitService.commit` 会在等待数据集锁后复验数据库许可与 deadline，尚无读取本地停止信号的参数。T07 需要覆盖“适配后检查已通过、等待数据集锁时才收到停止”的窗口。以下是**本项计划新增**的兼容重载，不是已经存在的方法，也不是重写 T04 的完成事实：

```java
public WriteCounts commit(ExecutionPermit permit, UUID batchId,
        AdaptedBatch batch, long sourceRows, BooleanSupplier stopRequested);
```

旧四参数方法委托新方法并传 `() -> false`，保留既有参数/事务约定。新参数非 null，供 runner 传入合并本轮 stop 信号和 owner 中断位的无 I/O supplier。新重载在原预检查阶段及 participant.beforeWrite 内取得任务/批次锁、完成原身份/截止检查之后，调用 supplier；true 为固定 EXECUTION_INTERRUPTED，不附 cause。不得在持有这些锁时调用 executionDefinition、仓储高层方法或任何插件方法。

beforeWrite 的最后许可检查通过后，事务获准开始证券 SQL。之后即使停止或达到任务截止，允许它在既有 60 秒事务期限内结束；不在 afterWrite 加停止检查、不另开中断状态事务打断提交。此顺序保留数据集锁→任务行→批次行。停止先于 beforeWrite 则回滚零证券写入且不写 SUCCEEDED；runner 返回 NEEDS_RECOVERY，测试协调器/T08 等实际退出后记录中断。已成功事务之后观察停止时保留成功叶子，不把它改 FAILED。

### 预算边界与检查位置

所有预算均独立于插件完整性限量，没有“调大上限即可确认完整”的路径。默认值与总体设计一致：

| 预算 | 边界和执行位置 |
| --- | --- |
| 范围 36600 自然日 | T03 接收与 runner 领取后都检查；含两端，恰好允许，+1 拒绝；SINGLE 无日期预算 |
| 节点 10000 | 根计划和每次 split 检查全部节点；父 SPLIT 仍占节点，拆分净增加2；恰好允许，超过原子失败，不先插一个子批 |
| 每轮请求预约 5000 | context.beforeRequest 调仓储原子预约；1至5000允许，第5001拒绝且无该次来源请求；包括日历、父响应和失败请求，不仅成功子批 |
| 每轮 30分钟 | 从实际 claimTask 的 startedAt 起，排队不计；deadlineAt 固定保存，now>=deadline即过期；检查在规划/批次边界、纯循环、每次预约、响应返回、拆分与进入提交前，仓储/T04还有最终复验 |
| 累计成功 sourceRows 1000000 | 领取后读取既有成功计数，>预算时零新请求；=预算仍允许请求零行结果，但任何需提交正行数的批超限拒绝。每个可提交批判断 `rowCount <= max - succeededSourceRows`，先验证 succeededSourceRows<=max，避免加法溢出；父SPLIT/规划日历/失败响应不累加 |
| 排队100、响应大小、事务60秒 | 分别复用 T03、来源客户端和 T04；runner 不扩大/重复实现这些边界 |

已有批次树的节点数或累计成功行数在配置下调后已经超限，领取后应停止该轮、保留历史成功和PENDING，不删节点/计数。恰好节点上限还可执行已存叶子，只有新增节点超限时拒绝。原生父响应即使行数大于剩余累计预算，先 assess；若必须拆分则父不计入预算，允许生成子批直至真正可提交的数据达到限额。

每轮计数由 claimTask 清零；累计 requestCount 与成功sourceRows不清零。本项测试用已有仓储 requeue 作为建立第二轮事实的夹具，证明新 runner 读取持久计数，但不新增服务 retry/resume。执行单例及 T08 排他租约保证同一 permit 无两个并行提交者；累计预算在事务外读取与 T04 提交之间不会被另一合法 worker 增长，DB仍用许可排除旧轮次。不存在多实例预算并发保证。

### 错误分类、继续与停止

错误处置不使用 ErrorCode.retryable 决定本轮自动重试。所有保存的消息来自 `new DownloadTaskRepository.StoredError(code).message()`，只保留分类；不保存 TensorException.getMessage/cause/suppressed、原始参数、SQL、响应正文或凭证。来源已分类异常在控制流中保留其 code，不转 UNKNOWN；边界意外 RuntimeException 按下面阶段转换。JVM Error 不捕获为正常失败；仍执行 finally 释放实际执行占用，由未来协调器标记需要恢复。

| 错误/阶段 | 当前批及本轮处置 |
| --- | --- |
| SOURCE_UNAVAILABLE、SOURCE_NETWORK_ERROR、SOURCE_TIMEOUT、SOURCE_PAYLOAD_INVALID、SOURCE_RANGE_MISMATCH、ADAPTER_FIELD_MISSING、ADAPTER_TYPE_INVALID、BATCH_COMPLETENESS_UNCONFIRMED | 已领取批调用 failBatch，提交成功后继续下一个PENDING；同请求无自动重发 |
| BatchCommitService 的 PERSISTENCE_FAILED | 尝试一次 failBatch；它若成功，表明同许可下该批尚可从 RUNNING 转失败，继续其他批。失败标记也失败/许可冲突则不再发请求，NEEDS_RECOVERY，不断言原提交未成功 |
| SOURCE_AUTH_FAILED、SOURCE_PERMISSION_DENIED、SOURCE_RATE_LIMITED、PLUGIN_DISABLED、TASK_DEFINITION_CHANGED、BATCH_DOWNLOAD_UNAVAILABLE、TASK_LIMIT_EXCEEDED | 有当前RUNNING批则 failBatch；然后停止，其他PENDING保留；按成功叶子数保存 PARTIAL_FAILED/FAILED。未领取下一批时不制造失败批 |
| PARAM_REQUIRED、PARAM_INVALID、DATASET_MISCONFIGURED、INTERNAL_ERROR，以及执行路径不应产生的 TASK_NOT_FOUND、SUBMISSION_CONFLICT、TASK_QUEUE_FULL | 当前批记录固定分类并停止；不把内部/参数合同错误当作可不断继续的请求错误 |
| EXECUTION_INTERRUPTED、本轮stop或owner线程中断 | 不继续取数或新提交；不在仍有同步调用未退出时恢复任务。返回 NEEDS_RECOVERY/EXECUTION_INTERRUPTED，当前RUNNING和PENDING由退出后的T08恢复处理；已有SUCCEEDED不变 |
| TASK_STATE_CONFLICT | 停止且不以旧许可再写fail/finish，返回 PERMIT_LOST；提交失败后的failBatch冲突按“提交结果不明”处理为NEEDS_RECOVERY |
| 仓储读 QUERY_FAILED，领取/预约/savePlan/split/failBatch/finishTask 的 PERSISTENCE_FAILED 或未分类存储异常 | 立即停止来源请求与后续状态写入，返回 NEEDS_RECOVERY；保留已知taskId/permit。不能因规划/拆分事务异常就另写终态掩盖可能已提交的结果 |

规划阶段没有当前批，任何普通来源/计划验证/能力错误都使本轮停止，以 fail/partial 终态保存错误；planReady=false 不得推导成功。规划请求中的预约/存储失败依旧优先进入 NEEDS_RECOVERY。beforeRequest 因 QUERY_FAILED/PERSISTENCE_FAILED 拒绝时，异常从插件原样传播，runner须根据这个持久分类停止，不尝试下一批。

未分类插件取数 RuntimeException 为 INTERNAL_ERROR并停止，未分类适配错误同样 INTERNAL_ERROR；明确由 JSON 参数、当前元数据或 AdaptedBatch合同检查造成的非法返回值为 DATASET_MISCONFIGURED。不捕获所有 RuntimeException后默认 SOURCE_NETWORK_ERROR；也不在一个大catch中把仓储失败变成普通可继续的批失败。实现中用明确阶段边界及一个私有错误处置方法，避免重复failBatch。

### 终态与不确定提交

待执行列表为空或遇到可持久的停止原因后，调用一次 snapshot 取得task和Counts，核对同一permit/RUNNING。c.running必须为0；出现不属于当前已处理批的RUNNING事实，按 NEEDS_RECOVERY/INTERNAL_ERROR终止，不猜测成功或覆盖它。已提交一个批次后，下一轮先读取待执行列表：为空则直接进入此汇总；非空才在领取前检查控制。汇总没有来源调用或证券写入，不因最后一次获准事务结束后观察到stop/deadline而阻止保存真实SUCCEEDED。

- `planReady && c.succeeded == c.totalBatches` 为 SUCCEEDED，含合法0交易日计划和全部空成功叶子；error=null。最后一次已经获准的事务结束时越过deadline/观察到stop，全部工作已经成功则仍保存SUCCEEDED；若此时仅数据库汇总/保存失败才返回NEEDS_RECOVERY，T08现有恢复也须按成功事实重算。
- 不满足成功且c.succeeded>0为PARTIAL_FAILED，否则FAILED。正常处理完全部PENDING时，用 `batches(taskId,new BatchFilter(FAILED,false),1,20)` 第一项的固定code作为lastError；因全局错误提前停止则用该停止code。PENDING保留并计入totalBatches，不能只根据“失败批数0”认为成功。
- 调finishTask一次；它在事务内重新验证许可与叶子事实。只有成功返回才报告FINISHED；如果终态提交失败，返回NEEDS_RECOVERY，不重发任一已SUCCEEDED来源请求。

数据库提交回执不明时，证券数据与SUCCEEDED仍由同一事务裁决。runner不把已成功批降为FAILED；failBatch仅允许RUNNING且验同permit。如果原commit其实成功，failBatch会冲突，执行器必须停止交给恢复。计划或split回执不明也不在内存重试同一写操作；下一轮经人工恢复使用planReady和已有树。禁止用“捕获异常后继续”跨越无法确认当前状态是否已保存的边界。

## Files

- 新增 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DateRangePlanner.java`：纯日期二分/自然日枚举。
- 新增同目录 `DownloadTaskRunner.java`：固定公开表面；内部上下文、计划验证、串行循环、预算和错误处置均用私有/包内帮助方法，不增加通用执行框架。
- 修改同目录 `DownloadTaskService.java`：包内 executionDefinition/record及复用验证，保留原public表面和幂等/接收顺序。
- 修改同目录 `BatchCommitService.java`：新增五参数stop重载，旧四参数兼容委托。
- 新增 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DateRangePlannerTest.java`、`DownloadTaskRunnerTest.java`、`DownloadTaskRunnerIT.java`；扩展 `DownloadTaskServiceTest.java`、`BatchCommitServiceIT.java`。确有跨这些测试复用的夹具只用同包测试帮助类型，不新建测试框架或生产注入开关。
- 新增 `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixtureDownloadTaskRunnerTest.java`：真实旧FixturePlugin的SINGLE兼容。fixture模块已经依赖core；不能反向给core增加fixture依赖形成循环。
- 不修改Repository公开表面、DownloadTaskJson schema、迁移、依赖文件、ApplicationConfiguration、生产生命周期配置或前端。

## Tests

首个实现动作：写 DateRangePlannerTest 的跨闰日/跨年闭区间二分和首尾枚举失败测试，观察缺失类，再实现最小算法；随后以真实受控插件写 runner 的 SINGLE 空批也调用原子commit、三批中第二批失败仍执行第三批的失败用例。测试不是逐私有方法的镜像；优先证明状态、请求快照、预算、停止及数据效果。

### 无来源分支与确定性单元测试

DownloadTaskRunnerTest 使用显式测试来源 `runner_test/prices`，RANGE参数 `symbol/from/to`、来源日期列 `observed_on`，输出证券键 `symbol+observed_on`；SINGLE另用简单snapshot描述。用真实T03、ParameterValidator、测试插件/适配器；仓储或commit可mock固定交互顺序，数据库原子事实由IT证明。不得使用 Tushare源码、ts_code/ann_date/end_date条件分支或放宽生产34门禁跑通测试。

必须覆盖：

| 场景 | 可观察结果 |
| --- | --- |
| 日期二分 | 单点拒绝；两天、奇偶天数、跨年/闰日、非季度报告期无漏无重；LocalDate.MIN/MAX可二分，MAX单点枚举不溢出 |
| 领取 | 只取当前run最早一项；empty无执行；settings关闭/预先停止零DB领取；领取才增加generation/attempt；并发/重入runNext立即拒绝且没有第二调用 |
| 计划三模式 | 原生整段；自然日包含周末；交易日受控非weekday集合/合法空；重复/乱序/遗漏/越界/null/非单点被拒绝且不save半计划 |
| 持久参数 | 根/新子批创建时才保存转换参数；executionDefinition的全用户范围纯预检允许重复，但不得用其结果覆盖片段快照。改变测试插件转换结果但不改变已存批后，planReady=true执行的请求仍精确使用原sourceParams，且不重新plan/保存根批；规则版本变化则先拒绝而不请求 |
| SINGLE路径 | 可选插件走downloadBatch但不plan/assess；普通插件走download且runner预约一次；空响应仍适配/commit；原生父请求满额不适配，不先去重 |
| 身份/完整性 | 来源/API/参数快照不符，字段顺序/FAILURE/null错误；COMPLETE提交、UNKNOWN失败、单点/不可拆分SPLIT失败，报告期二分不看业务字段 |
| 错误矩阵 | 上表全部ErrorCode的continue/stop/recovery分支参数化；第二批失败第三批仍执行；限流/权限等停止后第三批保留PENDING；消息/cause中的测试秘密不进StoredError/RunResult |
| 定义/可用性 | 排队后版本变化零上游；请求中语义版本变化零提交；运行中readiness变false停止；现有validateReplay与幂等行为不回归 |
| 上下文 | 日历规划一次预约+父/子每次预约都计数；同context透传；可选SINGLE不双预约，普通SINGLE恰一次；预约拒绝零实际来源调用 |
| 默认预算边界 | 36600/+1自然日、10000/+1根节点、节点9998+2允许/9999+2拒绝、5000/5001预约、1000000/+1累计成功行；5000累计预约可直接循环context无sleep，不需要5000个HTTP |
| 时间/控制 | 固定/可推进Clock的deadline-1ms允许、deadline拒绝；规划自然循环停止、适配后停止、每个边界停止；不中断已经获准的事务 |
| 新轮次 | 已保存成功计数不清，预约runRequestCount重置而requestCount保留；planReady已有根不再次plan，成功及SPLIT不再领取 |
| 晚到旧调用 | latch阻塞不实现BatchDownloadSupport的插件，推进Clock/设置stop/中断owner；即使外部Future显示cancelled，插件未退出时租约保持且第二任务不启动；释放latch后响应不适配/commit，无假FINISHED |
| 外层事务 | runNext与新增service包内入口立即拒绝，无插件调用；所有来源/适配测试回调断言无实际事务 |

预算单测可使用小配置验证复杂树/失败交错，同时必须有上表默认实际数值的等号/+1测试；不得只测小值后声称默认边界通过。DateRangePlanner.calendarDays的超大自由输入不用于内存压力测试；runner先限制maxRangeDays再生成/校验计划。

FixtureDownloadTaskRunnerTest 用真实 `new FixtureConfiguration().fixturePlugin()` 和 `.fixtureDatasetAdapter()`，消费其definition构造目录与真实T03；以mock仓储/commit覆盖SUCCESS、EMPTY、SOURCE_FAILURE、TYPE_FAILURE，并证明普通DataSourcePlugin未增加可选接口、每次调用一次预约、EMPTY也进入commit。测试目录沿用T03反射构造方式，不改变DatasetCatalog生产构造器；无需启动acceptance Spring上下文或修改fixture生产实现。

### MySQL 事务与恢复边界

DownloadTaskRunnerIT 使用 Testcontainers **MySQL 8.4.6**，沿用T04的同DataSource/JdbcTemplate/DataSourceTransactionManager、真实Repository/PersistenceService/BatchCommitService，ScriptUtils执行唯一app V8；测试定义与目录仅含`runner_test/prices`。创建测试证券表`runner_test__prices`，列symbol VARCHAR(32)、observed_on DATE、amount DECIMAL(18,2)、三元列source_plugin/source_api/ingested_at，复合主键symbol+observed_on，GenericDatasetAdapter进行真实适配。测试插件在内存返回确定响应，没有真实Tushare请求。

IT必须证明：

1. T03提交→真实claim→savePlan→三批执行，第二批网络/适配失败后第三批写入，最终PARTIAL_FAILED、两SUCCEEDED一FAILED；请求/来源/insert/update计数来自持久状态，批次attemptCount正确，独立连接可见数据与状态一致。
2. 原生父要求SPLIT，保存`000001/0`与`/1`，父零计数且未适配；子批各成功，最终证券行与source计数不包含父；节点预算失败无半子树。全部空批及合法0交易日计划均SUCCEEDED，planReady=false规划失败绝不SUCCEEDED。
3. 用SQL触发器拒绝当前证券写入，T04回滚后failBatch可提交，随后兄弟批继续成功；触发器再拒绝失败标记时runner立即NEEDS_RECOVERY、下一批零来源请求。控制器只在runNext退出并移除故障之后调用已有recoverStoppedTask，遗留当前批为EXECUTION_INTERRUPTED，PENDING/既有SUCCEEDED不变。
4. 用测试JdbcTemplate/DataSource代理在savePlan/split/commit/finishTask实际提交后模拟回执错误，runner不自动重试；恢复读取已提交计划/树/成功叶子。特别是commit已成功但返回PERSISTENCE_FAILED，failBatch必须拒绝，原成功数据/计数不降级；终态写失败后成功批不再次下载。
5. 真实预约计数和split节点预算边界；预置旧成功叶子接近source上限，本轮超一行回滚/不写，历史值不清；在夹具中用仓储requeue/claim建立新轮次，证明未清累计成功行数/总预约。
6. 控制仓储读取失败、预约失败、claim回执不明，每种均零后续请求、返回已知身份；不会由runner直接recover当前还未退出的调用。
7. 受控协调器用latch登记租约→执行→实际退出→释放→恢复，证明晚到旧插件返回后零commit、恢复才可生成新许可。另预置新generation后尝试旧permit写入/拆分，原许可被拒绝；T04已有旧RR快照隔离测试继续通过。

扩展BatchCommitServiceIT：保留四参数回归，新增五参数预先停止与持有DatasetLockManager锁后等待期间停止，零证券/成功写入；释放锁后固定EXECUTION_INTERRUPTED、无cause。另在beforeWrite最后控制检查已通过、证券SQL的测试代理中发出stop，事务仍共同提交SUCCEEDED；不添加afterWrite停止校验。此项是T07新增窗口测试，已有T04截止/回滚/锁序证据继续保留。

所有并发使用有界latch/future和可控Clock，不使用长sleep、真实token、H2或只mock事务证明原子性。回执故障代理仅位于测试，没有新增生产反射开关。T08全面retry/resume、进程重启装配和T12浏览器闭环不计入本项测试完成。

从仓库根运行，Java21；需要MySQL的第一条先具备与T02–T04相同的Colima/Docker连接。当前已记录本机连接是`DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock`与`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`，实施时先核对服务可用，不修改或清理用户容器。命令为：

```sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -Dtest=DateRangePlannerTest,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskServiceTest,DownloadTaskServiceIT,DownloadTaskRepositoryIT,BatchCommitServiceIT,PersistenceServiceIT,DownloadServiceTest,FixtureDownloadTaskRunnerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
git diff --check
```

预期全部退出0，首条选定的新增类和MySQL IT实际运行，无失败/错误/跳过；不能用clean verify代替显式IT。完整生命周期前端仍使用构建内Node24.15.0/npm11.12.1执行现有单测与生产构建。本项无浏览器/fixture元数据变化，不新增浏览器回归命令。依赖main/干净HEAD的发布脚本留既有流程，不绕过前置条件。新创建生产/测试文件在实施阶段加入Git，不自动提交；本文设计写作不运行这些尚未实现的测试。

## Acceptance

1. 两个新生产类型与本文固定公开表面存在；T03包内定义复用、T04兼容重载有回归，core不依赖具体插件，旧同步与40项数据集合同不变。
2. 一次实际worker串行领取一任务，permit来自真实claim结果；计划/批次参数已持久、复用时不重新规划；日期二分/三类计划核对无漏无重，合法空交易日与未规划失败明确区分。
3. 每个来源请求预约一次；规划和SPLIT父请求计预算，满额父不入库；五项默认预算等号/+1均有实际证据，重执行不清累计成功sourceRows或总预约。
4. 失败继续/停止矩阵落地：可定位单批失败不阻塞独立兄弟，鉴权/限流/定义/预算停止保留PENDING；状态存储不可确认则停止全部新来源调用、返回NEEDS_RECOVERY，已成功数据和叶子不反转。
5. 空批也经T04原子提交；stop在等待锁时发生则零写入，已获准事务可完成。晚到响应不适配/提交，活动租约直到同步调用实际退出才释放；T08消费协议明确且已有受控协调测试。
6. 首条真实MySQL专项、全单元、生产包、验收包及diff检查均有可复核通过证据；实施/审查事实先写T07完成，再按Order准备T08设计与交接，不在本项提前实现T08/T09/T12/T13。

## Risks

- 普通旧插件不接受BatchCallContext，runner只能对其一次方法调用预约预算并在返回后拒绝迟到结果。插件必须自身具有有限超时；永不退出的调用会占住唯一worker，需要运维停止进程，不能以cancel状态释放租约并悄悄并发重发。这是既有兼容边界，不通过本项线程包装伪造可中止保证。
- 提交回执不明可能已有计划/数据/成功状态。本设计将这种情况交给实际退出后的数据库事实恢复；不以一次异常推断回滚，不自动重发。生产恢复/关闭仍需T08完成后才能装配。
- 累计source预算依赖单实例单实际worker与T08统一协调锁。执行许可防迟到不等于多实例协调；未来放开并行需另改原子预算合同，本项不提供。
- BatchCommitService本地停止重载是已定位的T07必要兼容补充；必须维持beforeWrite之后允许事务完成，避免破坏T04原子提交语义。现有deadline验证不因新增stop检查而移除。
- 全部34生产Tushare RANGE尚未真实验证；T07基础设施通过不改变availability，T13仍须逐项取得真实参数、日期轴及完整提取依据。当前没有阻止本专属设计实施的未决需求；T08的生产协调装配是明确后继范围。
