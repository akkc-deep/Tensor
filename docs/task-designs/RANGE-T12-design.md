# RANGE-T12 首次下载编排、执行槽位与本轮结果设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 RANGE-T12：在 `DownloadService` 提供可独立调用的首次下载用例，串行遍历 T08 冻结的全部请求批次，消费 T09 单元及 T11 真实提交结果，明确失败经 T10 确认保存后继续，返回与本轮已确认事实一致的结果。共用进程内槽位持续到实际执行结束。

本设计准备于 T11 已记录 COMPLETED 之后；观察本项为 NOT_STARTED、Design/Handoff 均为 None，直接依赖 T08／T09／T10／T11 均 COMPLETED。设计就绪不是实施开始或功能通过。

## Scope

- 新增首次 `executeInitial`、一个进程级执行槽位、不可变本轮结果及携带停止快照的 Core 异常；装配到现有 App 单例 `DownloadService`。覆盖参数／日历／规划前置、全部页取数、全局检查、逐单元提交、首次失败创建与同任务追加、S／F／N／H 和 R／I／U。
- 现有四参 `execute` 保留旧 sourceParameters／download／DownloadResult 行为，接入同一个槽位；当前 Controller、请求绑定、DTO、成功日志和 fixture HTTP 继续使用它。新首次用例只接受投影后的新下载参数。明确区分两个具名入口，不按参数形状自动猜测或降级；T14 同版切换 Web，T13 才实现精确重试。
- 不实现重试循环、HTTP 端点及响应映射、前端、HTTP 自动重发、取消／暂停／终止、队列／异步后台 worker、进度、持久执行状态、预登记、历史、补偿或第三张表。不开启新生产来源、日历、STOCK 能力；不修改迁移／49份 Dataset／生产策略及查询行为。
- 31 个自然日保持唯一整轮输入上限；来源超时沿来源合同，T10／T11已有短事务超时保持。不添加整轮时长、累计行数、累计响应体、索引数量限制，不自动拆小失败批次、不重试失败请求。ISSUE-008 九项继续“不依赖，未解决，用户后续单独处理”。

## Approach

### 1. 按序输入及真实接口

按看板读取 [PRD v1.4 §2、§5、§6](../design/Tensor_区间下载_PRD_v1.0.md)，再读取 [TRD v1.10 §2、§3.2、§6.4、§8.2、§9～§10](../design/Tensor_区间下载_TRD_v1.0.md)，并以 BRD 的成功保留／同步不可终止边界核对；随后读取当前 `DownloadService.java`／`DownloadServiceTest.java`，再完整核对下列直接输入的设计、验证和实现。读取 [OpenAPI](../contracts/openapi-v1.yaml) 的 DownloadExecutionResult／DownloadResponse／ApiError 和[错误码](../contracts/error-codes.md)，内部新合同采用已经冻结的字段与枚举，不提前改变 HTTP。

| 输入 | 已确认事实及消费约束 |
|---|---|
| [T08设计](RANGE-T08-design.md)／[验证](../verification/RANGE-T08-memory-batch-planning.md) | `DownloadBatchPlanner.planInitial(plugin,api,raw,context)` 返回 Plan(datasetKey,originalParams,originalDateRange,skippedDates,batches)，每批 PlannedBatch(scope,fetchBatch)。先 31 天校验，再完整日历屏障与精确 planBatch，五类计划顺序、冻结条件及覆盖已验证；planBatch 不是业务取数或取全豁免 |
| [T09设计](RANGE-T09-design.md)／[验证](../verification/RANGE-T09-recovery-units.md) | 只接受注册 `GenericDatasetAdapter`。`openInitial` 冻结 Session；取数后仅调用一次 accept 或 sourceFailed。PreparedBatch 分别含 units／failures；全部归属／键归属屏障先于单元发布，validate 返回 ReadyUnit 或 RejectedUnit。同轮索引只允许原线程确认提交，KnownMembers 仍是包内测试能力 |
| [T10设计](RANGE-T10-design.md)／[验证](../verification/RANGE-T10-failure-storage.md) | `RetryTaskStorageService.create(dataset,frozenParams,Failure)`／`append(taskId,Failure)` 返回 SavedFailure(key)，仅正常提交返回才确认保存。Failure 只接 selector＋errorCode，安全消息由仓储白名单生成。首次 UUID 在 create 内生成，编排不得预生成；后续不更新 task_params |
| [T11设计](RANGE-T11-design.md)／[验证](../verification/RANGE-T11-unit-commit.md) | `commitInitial(ReadyUnit)` 返回 Committed(selector,sourceRowCount,writeCounts,stopExecution)、RolledBack(selector,storageUnavailable)、Unavailable(selector) 或 Unconfirmed(selector)。只有 Committed 可确认索引／累计；false 的明确回滚必须独立保存确认才继续；其余故障停止。实际 105 项 MySQL、70 项定向 Core、755／758 项 Java 构建及各170项前端通过，独立最终评审 PASS；这些是依赖证据，不计为 T12 通过 |
| 当前旧路径 | execute 先插件／描述符／适配器，再 sourceParameters 校验，一次旧 download、旧 adapt／persist，返回旧 DownloadResult。DownloadControllerIT 固定一个五参构造器和仅 execute 的反射断言；DownloadRequestBindingTest 和多个 IT helper 手工构造服务，必须精确迁移其装配断言 |

生产 `TushareProPlugin` 的完整来源及日历表仍为空：交易模式经新用例先拒绝日历，其他模式按当前请求／完整性证据拒绝，零业务来源请求、零失败任务。不能因为旧入口仍存在，就宣称旧调用证明新完整区间可用。

### 2. 最小新增表面与兼容装配

`DownloadService` 保持 final，采用唯一构造器，参数顺序固定为：

```java
DownloadService(PluginRegistry plugins, AdapterRegistry adapters,
    ParameterValidator validator, PersistenceService persistence,
    BatchCommitService commits, RetryTaskStorageService failures,
    DownloadExecutionSlot slot, Clock clock);
DownloadResult execute(PluginId plugin, ApiName api, Map<String,Object> params, RequestId requestId);
DownloadExecutionResult executeInitial(PluginId plugin, ApiName api, Map<String,Object> params, RequestId requestId);
```

全部依赖非null；不保留缺少新依赖的兼容构造器，不增加可空服务或回退路径。构造器内部创建一个 `DownloadParameterConverter(validator)` 及 `DownloadBatchPlanner(converter)`；每轮从注册 Generic adapter 创建 `RecoveryUnitProcessor(adapter,converter,new BusinessKeyExtractor(),new BusinessContentCodec())`。处理器及索引不注册为跨数据集单例。App 新增一个 slot bean，并把已有 commits／failures 传入现有服务 bean；不另造 DataSource、transaction manager 或数据集锁。

新建 Core `DownloadExecutionResult` record，字段顺序及类型：

```java
record DownloadExecutionResult(RequestId requestId, Outcome outcome,
    PluginId pluginId, ApiName apiName,
    long sourceRowCount, long insertedRows, long updatedRows, String message,
    long completedUnits, long failedUnits, Long notStartedUnits, long skippedClosedDates,
    UUID taskId, Long remainingFailedUnits, FailureRecordStatus failureRecordStatus,
    List<RecoveryUnitProcessor.Failure> failures,
    List<RecoverySelector> notStartedScopes, List<RecoverySelector> unconfirmedScopes) {
  enum Outcome { SUCCESS, EMPTY, NO_OPEN_DATES, PARTIAL, FAILED, UNCONFIRMED }
  enum FailureRecordStatus { NOT_REQUIRED, CONFIRMED, UNCONFIRMED }
}
```

必需对象非null、集合防御复制且拒绝null元素，long非负、nullable Long非负；S=0时R/I/U必须0，F等于 failures.size，成功／空／全闭／部分／失败使用 OpenAPI 各自不变量。内部用现有强类型，不要求 plugin-api 的旧 DownloadOutcome／DownloadResult改变，也不加 JSON 注解。没有 totalUnits、运行状态、重试次数或历史计数。公开异常 `DownloadExecutionException extends TensorException` 持有非null `downloadResult()`，构造器只允许 PERSISTENCE_FAILED、TASK_RECORD_SAVE_UNCONFIRMED、COMMIT_UNCONFIRMED、INTERNAL_ERROR 四个停止码和 UNCONFIRMED 快照；固定安全异常消息，不保留原 SQL／来源异常 cause。T14 将来可按该访问器填 ApiError.downloadResult。

### 3. 槽位、检查顺序及上下文

新增 final `DownloadExecutionSlot`，公开 `Lease acquire(UUID retryTaskId)`、`boolean busy()`、`boolean retrying(UUID taskId)`。null retryTaskId 表示首次或旧下载；非null供 T13 复用。内部一个 AtomicReference<Lease>，一次 CAS 失败立即抛固定安全 TensorException(DOWNLOAD_BUSY, `Download execution is busy`)，不阻塞或排队。Lease 只带 ownerThread 和本次 retryTaskId，implements AutoCloseable；close 仅原线程释放同一实例、幂等，不能释放后来持有者。没有 forceRelease、cancel、凭 requestId 释放或客户端可写状态。retrying 仅在当前 lease 的非null任务标识精确相等时为true；它不表示主表持久状态，不会把首次新建的任务显示为“正在重试”。

新首次入口顺序固定：

1. 检查四个参数非null；拒绝 `isActualTransactionActive()` **或** `isSynchronizationActive()`，零插件回调／SQL，不让整个循环加入外层事务。
2. 复用当前访问错误优先级：pluginRegistry.find 失败为 PLUGIN_DISABLED；唯一 downloadAvailable 描述符及同名 API、注册 adapter 缺失为 DATASET_MISCONFIGURED。新入口额外要求 adapter 是 GenericDatasetAdapter、其 datasetKey 与目标相同；在日历和业务调用前拒绝不兼容自定义 adapter。
3. `converter.bindInitial(api, rawParams)` 完成投影校验并冻结输入，32天、缺端、旧字段／混用等在任何日历、来源预检或取数前失败；检查失败不占槽、不建任务。无效参数先于 DOWNLOAD_BUSY，插件访问错误先于参数错误，与当前访问边界一致。
4. `try (Lease ignored=slot.acquire(null))` 包含余下所有规划、Session预检、取数、单元处理、失败保存及结果／异常快照构造。取得槽位后调用 planner，传已经冻结的 values；planner 原有二次无副作用校验保留，不绕过其身份／readiness复核。忙时日历、planBatch、fetchBatch、业务及失败 SQL 全为0。
5. planner 必须全部成功返回后才允许业务请求。冻结 REQUEST task map；为每个 PlannedBatch 调 `processor.openInitial` 完成全部轻量 Session 配置／映射检查，保存对应 Session 的有序内存列表，尚无响应或SQL。任意初始 Session 不兼容均在首个业务请求前拒绝。全闭计划直接返回 NO_OPEN_DATES，不开 Session／业务事务、不建失败任务。
6. 然后在同一线程串行处理本轮；finally只在实际循环结束或抛错退出后执行。新旧两个入口都使用同一个slot，但不得嵌套获取（execute不调用executeInitial）。旧 execute 在完成其原参数校验后取得lease，保留旧单次业务逻辑及异常语义。

新用例内部固定一个无客户端状态的 `DownloadContext context = () -> {}`，传给 planner／Session／fetchBatch。当前系统没有独立的受控服务器生命周期状态源，不新增停止配置或公共 context／cancel 入参，不把 Thread.interrupted、Servlet request、连接存活、前端 AbortSignal、等待者 Future.cancel 当作业务取消条件；不清除线程中断标记。Core 不持有 HttpServletRequest／Response，不创建 executor 或异步 worker。T14须保持 servlet 同步调用，响应写入在用例返回之后；正常客户端断连没有可调用的释放／终止通道。服务端强制线程中断、进程退出或不可预期 Error 仍是基础设施故障，不承诺继续或补造记录。来源网络超时是业务 SourceException，按下一节继续；未知内部异常原样传播，实际finally释放，不能伪装成用户取消。

### 4. 串行双层循环与冻结恢复边界

按 T08 batches 的时间升序执行，保持每个 fetchBatch 原参数／范围，不合并相邻批、不在出错后拆批。每个 Session 仅终结一次：只在 `plugin.fetchBatch(apiName,fetchBatch,context)` 这一调用周围捕获 SourceException；成功返回（包括null）交 session.accept；捕获的业务来源异常交 session.sourceFailed。不捕获 accept／validate／commit 抛出的任意 RuntimeException 再调用 sourceFailed，也不在已有单元提交后重新全 REQUEST 回退。SOURCE_REQUEST_UNCONFIRMED 由 sourceFailed 原样抛出，属于执行条件不确认，停止且不保存该项；插件不可用、参数／上下文／意外异常同样不映射为实际来源失败。preflight 阶段的完整性／来源错误也直接拒绝，不创建业务失败项。

PreparedBatch 返回前全局屏障已完成，T12不重复拆行、信任前几页或自行构造 ReadyUnit。合并其 units 和 failures 为当前批的待处理事件序列（私有小 record 即可），使用以下稳定比较器：时间起点升序（DATE自身、MONTH当月1日、RANGE左端；NONE固定最前且原条件只有一批），时间终点升序（MONTH月末／RANGE右端），再 targetType.name、targetValue、timeType.name、timeValue。这样多日多股先日期再股票，不能沿 T09 当前“股票优先”的列表顺序处理，也不能先处理所有成功再保存所有失败。同日示例代码排序使用 A=000001.SZ、B=000002.SZ、C=600000.SH。

PreparedBatch.failures 在 accept/sourceFailed 返回时已全部明确：一次加入本轮 failures/F（按同一比较器），即使更早的事件导致停止也不能把已知来源失败说成未开始。随后按事件顺序逐项保存；若前一保存不确认，后续已知失败留在本轮 failures但不再写库。未访问的 UnitInput 才属于尚未开始的恢复单元。validate 后的 RejectedUnit 当场追加F并立即独立保存；ReadyUnit立即commitInitial，确认后才进入下一事件。预先validate多个ReadyUnit再批量提交不允许。

所有失败保存均转换为 `new RetryTaskRepository.Failure(failure.selector(),failure.errorCode())`；保留T09安全Failure供结果展示，不复制任意来源 message、UnitFailure.errorMessage 或 cause。来源允许码为 T10现有白名单的9个 SOURCE 码，另有 ADAPTER_FIELD_MISSING／ADAPTER_TYPE_INVALID／DATA_CONFLICT／PERSISTENCE_FAILED；未知／检查类码不保存。SOURCE_TRUNCATED／SOURCE_COMPLETENESS_UNCONFIRMED业务获取失败不允许提交部分页；成员未知只保存精确完整scope REQUEST，不推导股票全集。trade_cal业务下载失败按正常业务失败处理，其辅助日历角色不能让其失败被跳过。

首次任务公共 map 固定为 `converter.taskParameters(api,plan.originalParams(),REQUEST)`，保留原始过滤／展开前 start_date/end_date及原有公共条件。每个实际Failure在保存前用**自身** targetType 求 taskParameters并核对与该固定map完全相等；STOCK能共享是 T09 canSplitInitial已证明REQUEST／STOCK公共map相等，不能按首个结果锁死恢复对象类型。混合“前批STOCK失败／后批整体REQUEST失败”用一个主表，选择器分别保存；含原ts_code且两种map不相等时T09本来就冻结REQUEST，不能删除股票条件强行细分。保存后不改 task_params、不改变原始日期、不把已存 REQUEST 转 STOCK。

保存门槛：本轮开始 taskId=null；第一次实际失败调用create，只在 SavedFailure返回且key.selector等于当前selector后接纳其taskId。后续append同一个taskId，每次检查返回key精确匹配。记录已确认保存的 selector集合用于当前剩余数；重复完整selector是upsert，不增加剩余数。create或append抛 TASK_RECORD_SAVE_UNCONFIRMED 即停止，不再取数／校验／提交／保存后续项，不尝试查询来推定本次写入结果或重新生成taskId。首次保存未知则taskId仍null；追加未知保留先前已经确认的taskId，但不能声称新增失败已保存。外部SQL删除导致的NOT_FOUND或编程异常原样停止，不重建主表。

只持有当前批完整响应／UnitInput及当前ReadyUnit；事件处理后移除已消费引用，批结束释放PreparedBatch与来源包络。全轮只保留轻量计划／Session、失败与停止范围、已保存selector集合及CommittedKeyIndex摘要，不积累完整业务数据。

### 5. T11结果与停止快照

| 结果 | 当前单元与后续动作 |
|---|---|
| Committed(stopExecution=false) | 检查selector匹配；先index.confirmCommitted(ready)，再S加1、R加sourceRowCount、I/U加writeCounts。继续下一事件 |
| Committed(stopExecution=true) | 同样确认索引并完整累计；当前已成功，不放failures或unconfirmedScopes。随后以INTERNAL_ERROR停止，绝不取下一项或补建当前失败 |
| RolledBack(storageUnavailable=false) | 不确认索引／不累计R/I/U；当前明确PERSISTENCE_FAILED，F加1；独立create/append确认成功后才继续下一事件 |
| RolledBack(storageUnavailable=true) | 当前明确PERSISTENCE_FAILED，F加1，但存储已不可用，不尝试保存；以PERSISTENCE_FAILED停止。前面已提交／保存事实保留 |
| Unavailable | 事务回调尚未进入，业务来源与校验已实际执行，当前明确未完成持久化，作为PERSISTENCE_FAILED计F；零保存，不归N或提交未知；以PERSISTENCE_FAILED停止 |
| Unconfirmed | 当前selector仅放unconfirmedScopes，不计S/F/N、不确认摘要、不保存任何新失败；以COMMIT_UNCONFIRMED停止 |
| 保存TASK_RECORD_SAVE_UNCONFIRMED | F保留已经明确的全部失败，未知的是记录保存，故不把明确失败复制到unconfirmedScopes；以该码停止 |

上述停止均抛 DownloadExecutionException，snapshot.outcome=UNCONFIRMED、failureRecordStatus=UNCONFIRMED，message固定“结果未确认；计数仅包含此前确认项”。这是整体执行未正常完成的错误快照；即使 stopExecution 的当前提交已确认，也不能返回正常SUCCESS或需要F≥1的PARTIAL。因此 S>0／F=0／N>0 的框架停止可在不改变T03 schema下表达。真正提交未知才有当前unconfirmedScopes；记录未知与业务未知独立。

异常安全文案固定：PERSISTENCE_FAILED“存储执行异常，已停止”；TASK_RECORD_SAVE_UNCONFIRMED“失败记录保存未确认，已停止”；COMMIT_UNCONFIRMED“当前范围提交结果未确认，已停止”；INTERNAL_ERROR“执行发生内部异常，已停止”。结果不包含 SQL、源行、响应、Token、堆栈或异常链。不用错误码retryable决定是否自动重试。未知内部异常／Error没有新业务Failure或虚构快照；沿现有上层异常通道传播且保留已提交事实，T14可继续按现有安全日志处理。

### 6. S／F／N／H、R／I／U与任务确认

- S只计确认提交的恢复单元；合法空Ready也经T11提交确认后S+1，R/I/U为0。全同轮重复仍S+1且R保留完整来源行数、I/U为0。全休市没有Ready、S=0；不删除旧数据。
- F是本轮已明确失败的单元数，等于failures.size，与是否保存无关；完整REQUEST/RANGE各一项，不按股票行数、SQL分组或天数放大。失败来源行及未知单元行均不计R。S、F、H、R/I/U使用long，仅确定事实从0累加。
- R/I/U始终为数值，含此前确认单元小计，未知当前项不加进去。T03要求的“未知不填零”是不能以当前未知项0行宣称整轮0行或完成；不把这三个既有数值字段改成null。S=0且当前提交未知时小计可为0，必须同时有UNCONFIRMED及当前unknown范围说明。
- H为plan.skippedDates.size，按不同自然日数，不乘股票数。非交易模式H=0。
- N只计算尚未开始的恢复单元：当前已Prepared的未访问UnitInput每个1；已明确来源failures即使未保存也不是N；当前Unconfirmed不是N。未来尚未fetch的批只在冻结为单个REQUEST时各计1，任何仍可能分STOCK的未知成员批存在则整个N=null。为避免T12复制T09 split判定，在BatchSession只新增只读 `public boolean usesIndependentUnits()` 返回构造时冻结的split；不开放成员注入／修改scope。所有future session均不独立时可精确求和；独立策略已因公共map回退REQUEST时getter为false，不能把可确定的N误写null。
- notStartedScopes列出当前批未处理UnitInput的精确selector及未来未fetch批的原REQUEST scope；保持上述时间顺序，不为展示合并不连续日期，不从数组长度得N。unknown列表只描述实际未知当前selector；其与S/F及notStarted不重叠。全部正常处理后N=0且两范围列表为空；只有全集确定且没有unknown时才核对S+F+N，不生成总数字段。
- 正常无失败时 taskId=null、remainingFailedUnits=0、failureRecordStatus=NOT_REQUIRED；正常有失败且全部保存确认时taskId为确认ID、remainingFailedUnits为本轮已保存不同selector数、status=CONFIRMED。首次任务不执行任何删除，因而无需额外查询失败表来重算正常剩余数。
- 停止时taskId仅保留此前create确认过的ID。create/append保存未知后remainingFailedUnits=null；其他停止未尝试任何新的失败写入时，可保留此前已确认selector集合数（无任务为0），它不意味着本轮所有F已保存。failureRecordStatus仍按错误快照合同为UNCONFIRMED，与已确认历史部分可并存；不机械把taskId和全部计数同时清空，不按F推算当前数据库项数。

正常结果只在全部计划处理完后选择：全闭空计划为NO_OPEN_DATES（“所选区间无开盘日期”）；F=0且R=0为EMPTY（“下载成功，0 条数据”）；F=0且R>0为SUCCESS（“下载成功”）；S>0且F>0为PARTIAL（“部分完成，失败范围已保存”）；S=0且F>0为FAILED（“下载失败，失败范围已保存”）。合法空加失败仍PARTIAL，不将HTTP请求数作为S/F。不在任务表保存任何计数或正常结果。

### 7. 当前Web及测试迁移

所有手工构造器 helper 增加 commits／failures／slot：旧路径测试的两个新存储依赖可用mock（断言零交互），每个独立测试使用新slot，同一并发场景明确共享slot；实际App配置使用真实既有bean。不要保留五参构造器来使反射测试蒙混通过。

`DownloadServiceTest` 原三组旧路径断言继续保留：旧sourceParameters输入、明确UnitFailure／null在旧adapt前拒绝、旧persist异常映射。增加新入口行为用例，与旧输入兼容性并列；不把旧test改为新参数后就宣称旧兼容通过。`DownloadControllerIT#exposesExactSurfacesAndImmutableDtos` 精确期待上述唯一八参构造器与两个四参public方法、各自返回类型；Controller的唯一原方法／DTO表面断言不变。DownloadRequestBindingTest helper与DownloadControllerIT全部 helper 同版迁移；ControllerUseCaseTest仍mock execute，既有日志和HTTP行为不改。

`ProductionApplicationContextIT` 增加slot唯一bean断言并核对service依赖复用；原批提交及存储单例不变。新Core结果和异常不在T12被旧DownloadResponse或OperationLogger消费，故不能用本项Core通过宣称HTTP区间／断连已验收。

## Files

以下为正式启动后的确定文件；本次准备只创建本设计及内部design-report。

- 修改 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`：新增首次编排和私有循环／汇总辅助，旧入口共用槽位。
- 新建同目录 `DownloadExecutionSlot.java`、`DownloadExecutionResult.java`、`DownloadExecutionException.java`：单实例lease、不可变结果、停止快照异常；不增加通用任务框架。
- 修改同目录 `RecoveryUnitProcessor.java`：仅增加BatchSession只读usesIndependentUnits getter，原分割及安全处理算法不变。
- 修改 `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`：slot bean及唯一服务构造器装配。
- 修改 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadServiceTest.java`，新建同目录 `DownloadExecutionSlotTest.java`、`DownloadExecutionResultTest.java`；修改 `RecoveryUnitProcessorTest.java` 验证getter反映真实拆分与公共map回退。
- 修改 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadRequestBindingTest.java`、`DownloadControllerIT.java` 及 `observability/ProductionApplicationContextIT.java`：精确装配／表面断言，保留旧路径全部行为。
- 新建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/InitialDownloadServiceIT.java`：真实Flyway/MySQL下完整首次用例、失败保存门槛和提交确认的组合证据。
- 新建 `docs/verification/RANGE-T12-initial-execution.md`，修改 `docs/traceability/tensor-range-requirements.md`，仅记录本项机制AC及真实执行证据。新实施文件按仓库规定加入Git，不撤销已有暂存／并行ISSUE-017工作，不提交／发布。看板及T13设计交接由主工作流依次收尾，不自动启动T13。
- 不改plugin-api旧结果、DataSourcePlugin、Tushare生产来源／策略、T10/T11实现、PersistenceService、迁移、POM、Controller生产代码／DTO／日志／前端；不删除文件。

## Tests

实施第一动作：在 `DownloadServiceTest` 保留原测试基础上新增“公告DATE请求20260901～20260910，3日SOURCE_RATE_LIMITED、7日SOURCE_TIMEOUT，全部10日各调用一次，首次create与后续append必须返回SavedFailure后才触发下一日，8个成功、2个失败、原始1～10日冻结”的受控行为测试；执行下列第一条命令保存先行结果。缺类型／构造器的编译失败与可执行行为反例分别记录，不能混称。随后实现最小编排，并补齐下表。

| 测试组 | 必须观察的行为 |
|---|---|
| 前置次序 | 插件缺失优先于错误参数；adapter非Generic在日历前拒绝；31允许、32／非法日／缺端／旧字段／混用拒绝且零日历、plan、fetch、commit、storage；忙时零外部回调，释放后可再接受；外层实际事务和仅同步范围拒绝 |
| 完整计划及五类 | 交易1／2／4／7日保持连续批段和H，两个市场任一开盘仍执行；全闭S/F/R/I/U=0、H>0、无事务无任务；日历缺失或最后日期不确认零业务；公告包括周末、三完整月20260131～20260302、原生多日一批、11原条件不加日期。第二批规划或Session初检失败不得执行第一批 |
| 全成功／空 | 非空成功／全空／空加失败／成功加空分别断言outcome与计数，旧业务哨兵不变；重复来源行R保留、I/U只按真实提交；同轮全部重复R>0不能判EMPTY；新一轮同键異值沿Upsert更新 |
| 来源继续 | 3／7失败后其他日期真实调用且只有一个主表两个明细；另分别注入认证、权限、限流、不可用、网络、超时、payload、截断、完整性未知，连续失败仍每批实际执行一次，retryable=false同样保存后继续；没有fetch重试／旧download回退／异常拆批 |
| A/B/C与排序 | 一个完整批多日多股、来源行乱序，逐事件按时间再股票；B字段错误或明确UnitFailure只影响B，A/C成功。明确source failures与units混合时不能先完成所有成功再保存B；去掉独立能力整REQUEST单元失败。最后一行归属／键错误使整scope失败且0提交；后页错误的部分A行永不accept |
| 已知失败与停止 | accept同时返回两个明确Failure和一个UnitInput，首个保存未知，F=2且failures有两项；后续不保存、不validate，该UnitInput为N，两个明确Failure均不在N／unknown。未处理future独立STOCK批使N=null；全部冻结REQUEST时N精确为未处理单元之和，不能用scope数组长度替代 |
| 单任务map | 原ts_code不丢；同轮先STOCK失败、后REQUEST整体失败共享唯一相等公共map及原日期。两股同日两条；公共map不等的含股输入由T09回退REQUEST；月份的原始日期仍为1月31～3月2，明细分别按完整月份 |
| 确认门槛 | mock/latch阻塞create或append返回时，下一事件／批的fetch、validate、commit均未调用；SavedFailure返回后继续。首次保存未知taskId=null、remaining=null；后续保存未知保留旧taskId、remaining=null，无自动readback或重建；来源消息／SQL／Token哨兵不进结果或两表 |
| 全部T11变体 | Committed正常确认索引才累计；stopExecution=true保留当前S/R/I/U且unknown为空、以UNCONFIRMED错误停止；普通RolledBack保存成功才继续，保存失败停止；storageUnavailable=true和Unavailable计当前F、零保存、零后续；Unconfirmed只进入unknown，索引不确认，当前不计F/N或R/I/U。先前S与已保存ID保持 |
| N及错误形状 | S=1/F=0/N>0的stopExecution快照不造PARTIAL；S=0未知提交的R/I/U=0明确是小计且有unknown范围；REQUEST剩余N精确、独立未知N=null、全完N=0。无新失败保存的错误可保留已确认remaining数；保存未知必须null。所有record集合不可变及非法组合拒绝 |
| 槽位生命周期 | 两线程有界latch：第一轮停在fetch、commit、失败save三处时第二个新/旧execute或直接retry lease均忙；不同plugin/API也忙。等待者超时／放弃读取返回值不调用Future.cancel，持有线程继续全部日期且完成前busy；释放以后允许新轮。用例不读取客户端标记，无取消端点／队列；来源故障继续期间仍busy，真实停止／意外异常finally释放，旧Lease重复close不释放新Lease |
| 真实MySQL组合 | 使用真实converter/planner/processor/BatchCommitService/RetryTaskStorageService，受控本地plugin；A已成功，B业务后组SIGNAL45000导致rollback，再独立失败保存，C成功；独立连接确认A/C业务、B旧数据回滚及仅B失败。故障失败表首明细INSERT证明create无半条结构且C未开始；append touch失败保留已有主表／原原因并停止。物理commit成功后代理抛错返回unknown，独立连接可见实际业务，但编排不确认索引／计数或补写失败。commit后框架异常则计当前成功并停止 |
| 网络与事务 | 受控confirmCalendar、planBatch、fetchBatch回调断言isActualTransactionActive及isSynchronizationActive均false；获取不持有数据集锁；每个业务单元和失败保存分别短事务。不得用单测mock代替这组MySQL组合证据 |
| 兼容与生产拒绝 | 原DownloadServiceTest、DownloadRequestBindingTest、DownloadControllerIT、FixtureFlowIT及原日志断言继续通过；构造器／两个公开方法反射精确更新。生产空日历/完整来源表不变，49项策略仍REQUEST；新入口生产能力前拒绝不建失败任务。未运行真实业务API或浏览器 |

从仓库根依次执行（设计阶段未执行这些功能测试）：

```sh
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadServiceTest,DownloadExecutionSlotTest,DownloadExecutionResultTest,DownloadBatchPlannerTest,RecoveryUnitProcessorTest,CommittedKeyIndexTest,BatchCommitServiceTest,RetryTaskStorageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=InitialDownloadServiceIT,BatchCommitServiceIT,RetryTaskStorageIT,DownloadControllerIT,FixtureFlowIT,ProductionApplicationContextIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,ControllerUseCaseTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

MySQL使用隔离mysql:8.4.6 Testcontainers与App真实迁移／schema；不复制DDL、不使用H2、不开disabledWithoutDocker。沿T11本机Colima仅为测试子进程设置socket，若环境改变先只读核对Docker context；不读取凭证。含ProductionApplicationContextIT命令取消测试进程TENSOR_TUSHARE_TOKEN覆盖，保留无凭证场景。通过授权环境解决Mockito／Docker限制，不关闭断言或改依赖。

每条命令必须实际退出0。逐一确认六类显式App IT的Surefire XML tests>0且failure/error/skipped均0，clean前保存原始XML／日志，记录实际数量；默认verify/acceptance的JAR检查不代替MySQL。两个全量构建必须包含实际前端测试和构建，记录本轮数字，不复制T11的755/758。独立读回业务表及两失败表，记录请求顺序、S/F/N/H、R/I/U、taskId与冻结JSON；故障代理实测状态只作验收事实，生产不得用readback推断unknown。

开始前保存当前分支／HEAD／原暂存清单和58份受保护资源摘要（49 Dataset、2生产策略、7生产迁移）；结束核对不变。只对新增变更或失败补跑相关检查，保留并行任务内容。Core等待者放弃测试仅证明use-case槽位不依赖响应消费，真实socket断连及HTTP 200/500映射留T14/T18，不能提前记为通过。

## Acceptance

1. 新首次用例串行处理全部冻结计划范围；3／7日及来源级错误保存确认后仍实际尝试后续日期，无自动重试／缩小／合并；A/C成功、B失败与整体REQUEST失败分别有Core及MySQL证据。覆盖AC-PRD-RANGE-08／10／12的本项编排部分。
2. 参数／插件／辅助日历／来源规划失败零业务失败表写入；31天边界保留；合法空和全休市无新增任务，不删除旧业务。AC-PRD-RANGE-05／07／15／17本项证据明确。
3. 每次首次下载最多一个新失败任务；仅create返回SavedFailure后输出taskId，后续失败按真实selector追加且原始日期／公共map不变。首次／追加保存未知停止且不补造，未知提交不保存失败。
4. 只有T11确认提交才确认摘要并累计；全部四种结果及两个布尔标志有精确分支，已提交不因后来故障撤销。S/F/N/H、R/I/U、remaining和record status独立准确，不虚报单位全集或历史计数。AC-PRD-RANGE-13／26本项机制通过。
5. 一个进程槽位同时保护新／旧首次及T13预留调用，忙即拒绝；来源／提交／保存都未结束时始终持有，不受等待者是否接收结果影响，无客户端取消输入／主动终止通道。AC-PRD-RANGE-23／27／28仅Core证据；HTTP断连必须由后续真实Web验收。
6. 规定测试及独立规格／质量／集成评审通过，新增文件纳入Git，旧Web合同行为、58份资源及生产空能力边界不变；验证报告和追踪不把T13精确重试、T14 HTTP或T20真实来源宣称完成。

## Risks

- 无待用户决定的产品事实；接口、字段、排序、N计算、停止结果和兼容迁移均已固定。T03错误快照的UNCONFIRMED不等于每个已处理单元都未知：确认提交的S/R/I/U及先前保存ID必须保留。
- 单线程进程槽位不保证跨进程／重启永久幂等，也不协调绕过应用的外部SQL；进程退出、保存未知及尚未开始范围不能从日志或业务表重建。无新补偿／历史账本。
- 当前采用同步servlet调用与无客户端context；T12可验证槽位的用例生命周期，不能单凭Core测试证明真实HTTP断连行为。T14禁止把连接异常关联到取消／提前释放，T18再实测socket边界。
- 新用例仍被真实来源／日历证据门槛保护；受控STOCK、完整分页及MySQL通过不能开启49项来源能力或关闭ISSUE-008。旧execute暂存是T14前的分阶段兼容，不作为新完整性验收或永久回退。
- 当前批完整数据和同轮摘要索引会随合法数据量增长，不新增未经授权的上限；保持只保留当前批数据。不可预期内存／进程故障不能逆转已确认数据库提交，结果丢失不被补造为失败任务。
