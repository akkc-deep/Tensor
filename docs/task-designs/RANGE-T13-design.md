# RANGE-T13 原任务精确重试与中断边界设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 RANGE-T13：用户手动执行原失败任务时，在共用槽位内重新读取当前明细，只重取这些完整恢复单元；成功与精确删除原子提交，再次失败在原项更新原因并继续，返回本轮确认结果，不恢复已删除项或推算历史。

本设计准备时 T12 已记录 COMPLETED；按预定义 Order 选择13，观察 T13 为 NOT_STARTED、Design／Handoff 均为 None，五个直接前驱均 COMPLETED。本文只固定实施合同，不表示 T13 已开始或验证通过。

## Scope

- 新增独立 Core 重试用例并在 App 装配，复用 T12 的唯一槽位、结果及停止异常，复用 T05精确参数转换、T06日历、T09单元票据、T10读取／更新、T11业务与删除事务。
- 读取当前原任务一次，完整前置校验和整轮日历后，按固定顺序逐原项串行处理；覆盖 REQUEST、STOCK、DATE／MONTH／RANGE／NONE、合法空、全闭清理、所有存储／提交停止分支和剩余项确认。
- 不新增 HTTP／DTO／日志映射或前端，不改旧 `DownloadService.execute`／`executeInitial`；T14才接新重试 HTTP。404／409在本项验证对应 Core 错误码，真实 HTTP 状态留 T14。
- 不实现自动重试、队列、取消、异步worker、永久幂等、历史、启动扫描、补偿／readback、任务编辑、第三张表或新迁移。不开启任何生产来源／日历／STOCK能力，不改49份Dataset、生产策略、T10／T11事务实现及既有持久化语义。
- 保留唯一31个自然日**原始输入**限制及既有来源／短事务超时，不按重试项数量、日期并集、完整月或合法旧 RANGE 新增整轮／行数／响应体限制。ISSUE-008九项仍“不依赖，未解决，用户后续单独处理”。

## Approach

### 1. 输入核对和三组先行期望

依看板顺序读取 [PRD §5.6～§5.8](../design/Tensor_区间下载_PRD_v1.0.md)，再读 [TRD §3.3、§7、§8.2、§9](../design/Tensor_区间下载_TRD_v1.0.md)，核对当前下载目录实现及以下完整直接输入。计数另以 PRD§5.4／TRD§6.4、[OpenAPI DownloadExecutionResult／重试 allClosed例子](../contracts/openapi-v1.yaml)和[错误码](../contracts/error-codes.md)为准。

| 输入及验证 | 实际可调用合同及 T13 约束 |
|---|---|
| [T05设计](RANGE-T05-design.md)／[验证](../verification/RANGE-T05-parameter-conversion.md) | `converter.mapRetry(api,header.taskParams(),selector)` 返回精确sourceParams及仅供展示的originalDateRange。两端均缺可为旧记录；单边／非法日期为RETRY_TASK_INVALID。REQUEST保留原公共股票／市场；STOCK只从selector补ts_code。RANGE不拆，原生DATE生成相等起止，MONTH完整月；原展示区间不限制／覆盖合法selector |
| [T06设计](RANGE-T06-design.md)／[验证](../verification/RANGE-T06-calendar-confirmation.md) | `confirmCalendar(apiName,new CalendarScope(frozenHeaderMap,actualSelectorDates),context)` 一次完整确认。实际日期仅来自现存selector；同轮复用、下轮重确认，不能从原展示日期展开或把未知当闭市。生产提供器仍无来源 |
| [T10设计](RANGE-T10-design.md)／[验证](../verification/RANGE-T10-failure-storage.md) | `find(UUID)` 返回独立只读快照Optional<Task>；Task带原Header和完整唯一ItemKey。`updateReason(ItemKey,ErrorCode)` 仅在原项存在时更新，返回SavedFailure才确认；不接受新参数、不重插缺项。create／append不是本项可调用操作 |
| [T11设计](RANGE-T11-design.md)／[验证](../verification/RANGE-T11-unit-commit.md) | `commitRetry(ItemKey,ReadyUnit)` 同事务业务＋精确删项＋主表收尾；`commitClosedRetry(dataset,api,item,decision)` 仅完整全闭可删、锁内核对原header map。Committed／RolledBack／Unavailable／Unconfirmed及两个布尔标志沿用，不从SQL异常或事后表值猜删除结果 |
| [T12设计](RANGE-T12-design.md)／[验证](../verification/RANGE-T12-initial-execution.md) | `DownloadExecutionSlot.acquire(taskId)` 与新旧首次同槽；`DownloadExecutionResult`／`DownloadExecutionException`可复用。T12现有唯一八参构造器及两个四参方法保持。T12实际Service20／Core114／App62／helpers31、verify775／acceptance778及各170前端、独立评审PASS是前驱证据，不计为T13通过 |

额外核对 T09 当前 `RecoveryUnitProcessor.openRetry`：REQUEST的split始终false，即使当前策略增强也整项处理；STOCK只能产生原股票、原DATE／MONTH／RANGE范围的单元，合法空仍带原selector。T13不使用初始规划器或首轮独立成员推断，不开放ReadyUnit构造或KnownMembers。

**已解决的输入措辞差异：**T11设计§5第5步曾把全闭清理的消费者写成“累计本轮已完成单元及0行计数”。T11实现只返回Committed零行候选、不累计S；OpenAPI明确 `filtered closed dates are not units`，重试allClosed例子为S=0、H>0。T13采用当前冻结的OpenAPI／T12结果不变量：全闭确认删除只增加私有已移除项数，不增加S，T11代码不变。这是消费合同的明确澄清，无待用户决定事实。

实施第一动作先把以下三组字面期望写入新Core测试，再运行首条命令。记录缺接口编译与实际行为RED的区别。

| 已保存原任务 | 本次精确请求／删除及返回 |
|---|---|
| REQUEST DATE3、DATE7；原map为1～10日及exchange_id=SSE | 受控margin的RANGE来源分别只发 `{exchange_id:SSE,start_date:20260903,end_date:20260903}` 和对应7日相等起止；3成功删除，7再次超时则只updateReason原7，结果S1/F1/N0、原ID及remaining1、原1～10日不变；下一次只请求7。另存REQUEST RANGE4～6时只发start_date=20260904/end_date=20260906并整项删／留 |
| STOCK A=000001.SZ、B=600000.SH同日3，公共map原1～10日 | 各自补ts_code、请求3日；A成功只删A＋3，B失败仅改B原因；B后续成功时原子删最后明细及主表，无新taskId |
| trade_cal原exchange=SSE、原1～10日，REQUEST DATE3 | 只发 `{exchange:SSE,start_date:20260903,end_date:20260903}`；不发trade_date／ann_date。正常空也删原项和最后主表，S1/R0/I0/U0、EMPTY、taskId=null；实际来源由受控插件提供 |

### 2. 最小公开表面和装配

新增 `com.akkc.tensor.core.download.RetryDownloadService`，final，唯一公开构造器和唯一公开用例方法：

```java
public RetryDownloadService(PluginRegistry plugins, AdapterRegistry adapters,
    ParameterValidator validator, BatchCommitService commits,
    RetryTaskStorageService failures, DownloadExecutionSlot slot, Clock clock);
public DownloadExecutionResult execute(UUID taskId, RequestId requestId);
```

七个依赖均非null。构造器内部只建 `new DownloadParameterConverter(validator)`；每轮用已注册GenericDatasetAdapter创建processor和同线程 `CommittedKeyIndex(dataset)`。不需要PersistenceService依赖，不另造结果／异常／事务manager或数据源。私有嵌套 `RetryExecution` 与 `RetryEntry(ItemKey,FetchBatch,BatchSession,boolean closed)` 保存本轮轻量状态即可；不增加可修改计划或通用任务框架。执行排序直接按T12同一规则实现私有比较器，不为本项重构T12。

`ApplicationConfiguration`新增一个 `retryDownloadService(...)` bean，参数顺序与上述构造器相同，注入现有plugins／adapters／validator／commits／failures／slot／clock；不得调用工厂再创建slot或存储服务。`ProductionApplicationContextIT`验证新服务唯一、七依赖共享及slot身份。`DownloadService`现有构造器／两个public方法保持，现有Controller仍消费旧入口。T14可直接调用此独立服务，输入只有原taskId与RequestId，没有可编辑参数、目标集合、cancel/context或重发令牌。

### 3. 槽位、读取和整轮前置屏障

入口顺序固定：

1. 两个实参非null；拒绝 `isActualTransactionActive()` **或** `isSynchronizationActive()`，固定IllegalStateException `Retry orchestration must not run in a transaction`，不读取数据库／插件。
2. `try (var lease=slot.acquire(taskId))` 包含后续全部读取、前置、来源、事务、结果构造。忙即DOWNLOAD_BUSY，无SQL／插件回调、无排队；即使输入任务其实不存在，忙优先。原ID用于 `retrying(id)`，只表示该lease当前持有，不变成持久RUNNING。
3. 槽位内调用且仅调用一次 `failures.find(taskId)`。Optional.empty抛固定无cause `TensorException(RETRY_TASK_NOT_FOUND,"Retry task was not found")`；不创建任务。返回header.taskId须等于输入，items不能为空，完整key唯一且均属于header；不一致／空主表按RETRY_TASK_INVALID、零更改，不自动删除“孤儿”主表。真实仓储的非法解码错误原样保持。
4. 从保存header.datasetKey取plugin/API。复用T12访问错误：pluginRegistry.find缺失为PLUGIN_DISABLED，唯一downloadAvailable描述符／API／注册适配器缺失为DATASET_MISCONFIGURED。adapter必须是相同datasetKey的GenericDatasetAdapter。核对实际plugin.descriptor包含该API及dataset且readiness.downloadAvailable；配置变化不能以保存标识绕过当前访问边界。错误消息用T12固定安全消息，不含保存内容。
5. 按所有items做完整 `mapRetry`、创建FetchBatch及 `processor.openRetry(api,frozenMap,item.selector,batch,context)`，保存有序轻量entries。任一末项不兼容，首项也不能先fetch／update／delete。不对header整体调用bindInitial；否则STOCK已移走的ts_code及旧缺日期会被误拒绝。mapRetry的SOURCE_REQUEST_UNCONFIRMED保留，其他保存不兼容保持RETRY_TASK_INVALID；未确认来源／参数／日历等前置错误不附执行快照。
6. 仅TRADE_DATE_RANGE从全部selector取DATE单日／RANGE完整含端日期的并集，调用一次confirmCalendar；若出现MONTH／NONE等不兼容形状，应在上一步或本步之前RETRY_TASK_INVALID拒绝。publicParams使用**原header.taskParams完整冻结map**（包括展示日期），日期不从此map推算。decision非null且scope精确相等，所有必要身份／日期完整；否则CALENDAR_UNCONFIRMED。非交易模式不调用辅助日历。任何业务来源、原因更新、关闭项删除之前，整轮日历必须完整成功。
7. 用该decision将每个交易项分为全闭或保留业务请求，规则见§4。对每个**保留业务请求**，串行调用 `plugin.planBatch` 预检精确原FetchBatch；所有预检成功后才处理第一个entry。全闭项不需要业务来源取全能力、不调用planBatch，但仍完成步骤5的合法条件／Session检查和整轮日历；不能因为没有业务来源实现而拒绝已经足以合法清理的全闭项。

planBatch许可精确固定：DATE来源只接受SINGLE_DATE，MONTH只SINGLE_MONTH，NONE只ORIGINAL_PARAMS；RANGE来源的RANGE项只接受SOURCE_RANGE。RANGE来源的DATE项在TRADE_DATE_RANGE／ANN_DATE_RANGE可接受SOURCE_RANGE或SINGLE_DATE；NATIVE_RANGE仍只接受SOURCE_RANGE。null／UNCONFIRMED为SOURCE_COMPLETENESS_UNCONFIRMED，其余不兼容建议为SOURCE_REQUEST_UNCONFIRMED。对完整RANGE返回SINGLE_DATE不能触发拆分、降级、修改selector或部分删除，全部业务／任务写入为0。来源规划异常原样传播，不更新已有原因。预检通过并非取全成功；每次fetch仍须拿完整原范围。

整个用例使用固定 `DownloadContext context=()->{}`，无客户端状态、Thread.interrupted或Future.cancel路径。未知RuntimeException／Error不伪装为来源失败；实际finally释放lease。来源超时依合同保存原因后继续，等待者超时／不消费结果不能释放持有者槽位。T13仅证明Core生命周期，HTTP断连仍待T14／T18。

### 4. 全闭、混合 RANGE 和 H

全闭判定必须对**本项完整日期集合**、decision全部必要身份逐日为false。只有全闭项调用 `commits.commitClosedRetry(dataset,api,itemKey,decision)`；直接传整轮原Decision，不删除市场身份、不重写publicParams、不构造空Ready。在统一排序轮到该项时才执行清理事务，不在规划阶段先批量删除。

有任一天／必要市场开盘，该项按保存的完整选择器调用原FetchBatch一次。特别是保存REQUEST或STOCK RANGE 3～7日，当前确认3、7开盘／4～6闭市：仍请求精确3～7日，在一个原恢复单元中完整取全、校验、提交／删除；不能变成3日和7日两项，不删除中间日范围、不合并其他明细。全范围请求包含闭市日期边界是保留原失败单元合同的必要结果，并不授权新的范围或按日补调用；不能因日历部分闭市静默拒绝这个合法RANGE。来源返回仍由真实Processor对完整selector检查，不由T13按日历裁掉行。

`H`采用本轮源请求跳过的自然日集合：完整日历后，`closedDates`为所有全闭entries日期并集，`retainedDates`为所有保留业务entries完整日期并集，`skippedDates=closedDates-retainedDates`，H为其大小。非交易模式H=0。这不是全calendar中false日期个数：仅有上述混合RANGE时H=0；单独全闭DATE4与混合RANGE3～7重叠时也H=0；两股相同全闭DATE4且无保留范围覆盖4时H=1。

H在全部前置成功后冻结，正常结果与执行中停止快照使用同一值；即使某个全闭清理尚未轮到，其日期已经确定不产生业务请求，不因后续停止扩大H或按已成功删除项重新计算。前置拒绝不返回部分结果。是否已确认删除则由私有removed集合／剩余项处理，不能用H推算。此口径与T12按完整计划冻结休市日期一致，并明确重叠RANGE没有实际跳过的日期。

### 5. 原项串行处理和原因更新

排序与T12一致：时间起点、时间终点升序（NONE固定最前，DATE自身、MONTH月初／月末、RANGE原端点），再targetType.name、targetValue、timeType.name、timeValue。存储读取的股票优先顺序不作执行顺序；不合并同日两股、相邻或不连续项，不按原始展示范围生成额外entries。

每个非全闭entry严格 `fetch → accept/sourceFailed → validate → commitRetry → 确认索引和累计 → 下一项`。仅围绕 `plugin.fetchBatch(apiName,exactBatch,context)` 捕获SourceException；成功包括null交Session.accept，来源异常交Session.sourceFailed，二者仅一次。SOURCE_REQUEST_UNCONFIRMED按T09原样抛出、停止、不更新该项；accept／validate／commit或意外代码异常不能再次转sourceFailed。

openRetry的输出应恰为一个与原ItemKey.selector相同的UnitInput或Failure；T13检查这个私有消费不变量，违反时抛固定IllegalStateException `Retry unit selector mismatch`，不提交／删项／重新分割。REQUEST即使当前独立策略增强也仍一个单元；STOCK RANGE不分日期。来源局部UnitFailure或最后一行归属错误经Processor归为**原项**失败，不新增明细或只删除成功片段。合法空产生原selector Ready，仍用commitRetry删除；同轮同内容重复保留完整R、I/U可0，新手动执行用新index并允许正常Upsert旧键新值。

再次明确来源／适配／冲突失败：把T09安全Failure加入本轮F，调用 `failures.updateReason(originalItemKey,errorCode)`。只保存T10白名单13码，不复制来源message、旧Item.errorMessage或异常cause。SavedFailure.key必须精确等于原ItemKey；正常返回才继续下一项，不调用create／append、不补偿读回、不按retryable自动重试。当前失败原项在更新前一直存在；更新未确认保留F并按§6停止，原始task_params、created_at和邻项原因不变。

每次只保留当前完整来源包络及当前Ready，消费后释放；全轮只保留entries／Session、原轻量任务快照、当前Failure列表、removed摘要及CommittedKeyIndex，不积累所有业务响应。

### 6. 提交结果、删除确认和停止

`commitRetry`及`commitClosedRetry`返回均先核对selector精确匹配原项；返回值不能对应另一股／日期。沿用T11四结果，不修改T11、PersistenceService或StorageService：

| T11结果 | T13处理 |
|---|---|
| Committed(false)，业务／合法空 | index.confirmCommitted(ready)；原key加入removed；S+1，R/I/U按该确认结果累计；再处理下一项 |
| Committed(false)，全闭清理 | 原key加入removed；不确认不存在的Ready、不加S/F/R/I/U；H已冻结；继续 |
| Committed(true) | 先按上述路径完整接纳删除及业务成功事实，再以INTERNAL_ERROR停止；当前不是F／unknown，不撤销已删项。即使最后主表已确认删除也返回UNCONFIRMED错误快照而非正常成功 |
| RolledBack(false) | 当前原项明确PERSISTENCE_FAILED，F+1；原key不removed、不加R/I/U／index。独立updateReason正常返回后才继续。全闭清理回滚也同样记录本轮明确持久化失败，不伪造成功跳过 |
| RolledBack(true)或Unavailable | 当前明确PERSISTENCE_FAILED计F，不removed、不加S／R/I/U；零原因更新，以PERSISTENCE_FAILED停止。Unavailable未进入事务，不能说当前已删除或提交未知 |
| Unconfirmed | 原项仅进入unconfirmedScopes，不计S/F/N、R/I/U，不removed、不更新原因／重插；以COMMIT_UNCONFIRMED停止。全闭清理未知也描述原范围的删除结果未知 |
| updateReason抛TASK_RECORD_SAVE_UNCONFIRMED | 保留当前F及此前事实，零后续fetch／validate／commit／更新，以该码停止；业务失败已明确，不放unconfirmedScopes |

以上四种停止码使用现有 `DownloadExecutionException`，复用固定安全消息及 `outcome=UNCONFIRMED`、`failureRecordStatus=UNCONFIRMED`、message“结果未确认；计数仅包含此前确认项”。不添加cause。未知编程异常／Error及T10/T11原样NOT_FOUND／INVALID不重建原项、不生成假Failure或假快照，沿既有异常通道传播并释放slot。

`find`只在入口读取一次，T11锁内仍核对原任务／项存在；updateReason也锁内检查。若绕过应用的外部SQL在执行中删除项，NOT_FOUND原样停止，不静默跳过重建。应用没有编辑task_params的API；本项保证自己从不更新原map，不增加长任务锁／分布式锁来协调外部SQL篡改。不可预期外部修改不属于受支持的并发输入。

### 7. S/F/N、剩余项和任务入口

本轮原失败项全集由槽位内find确定。私有 `removed` 只记录T11确认删除的完整key；不把来源成功、SQL回调返回或一次后验查表当删除证据。`retained=initialItemKeys-removed` 用于确认当前剩余，不从S推导（全闭删除不计S）。本轮F只含**本轮**实际明确失败，不把旧原因、尚未执行原项或提交未知重复算F。

- 正常结束所有项均尝试，N=0、notStartedScopes／unconfirmedScopes均空。剩余0则taskId=null、remaining=0、status=NOT_REQUIRED；仍有项则原taskId、remaining=retained.size、status=CONFIRMED，且应等于本轮不同Failure选择器数，满足现有result构造不变量。
- 停止快照N是尚未开始的**业务恢复单元**数量，原项全集已知，始终可给long，不能因STOCK而设null；全闭项已从业务单元集合过滤，不计N。notStartedScopes仍列出所有尚未轮到的原selectors（包括尚未清理的全闭项），用来说明哪些原记录未被处理；所以数组长度不能替代N。当前未知／明确失败／已确认删除项均不在N或notStartedScopes。全闭清理失败计F、未知计unknown时不强做S+F+N与原明细数量相等。
- 普通明确回滚／启动不可用及确认完成后框架故障没有新删除不确定性时，remaining为已读取初始项数减确认removed，原ID仅在remaining>0时保留。当前即使保存原因失败已停止，也绝不能抹掉前序R/I/U或确认删除事实。
- updateReason保存未确认：原ID仍来自已确认存在且不会被本次UPDATE删除的原项；remaining置null，表示最新存储确认失败，不以旧读数宣称本轮记录完整确认。
- commit／delete Unconfirmed：remaining=null；若retained中除当前未知项外还有至少一个确定未删除的原项，仍有已确认存续任务可保留原ID；若当前是最后剩余项，任务可能已与其同事务删除，输出taskId=null，不声称原任务仍在。输入taskId仍可由调用者保留用于刷新当前列表，不能作为确认入口输出或重建依据。不得为了决定此字段再find／查询业务表。
- 确认删除最后项后才发生框架异常：removed已包含最后项，taskId=null、remaining0、unknown空，业务S/R/I/U保留；全闭最后删除则S/R/I/U仍0。status仍UNCONFIRMED，描述本次执行异常，不推翻已确认删除。

正常outcome及message固定：有F时S>0为PARTIAL“部分完成，失败范围已保存”，S=0为FAILED“下载失败，失败范围已保存”；无F且存在业务完成单元时R>0为SUCCESS“下载成功”，R=0为EMPTY“下载成功，0 条数据”；所有原项均全闭且确认清理后为NO_OPEN_DATES“所选范围无开盘日期”（S/F/R/I/U=0，H>0）。全闭清理成功加另一个业务失败为FAILED，不能把闭市删除凑成S；合法空加失败为PARTIAL。所有字段只属于本轮，不保存历史计数。

## Files

以下为T13正式启动后的确定变更；本次只新建本文及内部design-report。

- 新建 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/RetryDownloadService.java`：上述七参服务、两参execute及私有前置／循环／快照逻辑。
- 修改 `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`：仅增加共享依赖的一个重试服务bean。
- 新建 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/RetryDownloadServiceTest.java`：三组先行期望、完整前置、精确请求、提交／更新门槛、计数／未知及槽位生命周期；使用真实converter／processor产生票据。
- 新建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/RetryDownloadServiceIT.java`：真实Flyway/MySQL完整原任务执行、业务＋删除故障、原因保存故障、停止与重新手动执行证据。不复制DDL、不改T11测试作为代替。
- 修改 `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ProductionApplicationContextIT.java`：唯一新bean及共享slot／commits／failures身份、生产拒绝后原记录保留；原T12图及旧Web验证不变。
- 新建 `docs/verification/RANGE-T13-exact-retry.md`，修改 `docs/traceability/tensor-range-requirements.md`：只记录本项机制及实际证据；本项收尾由主流程按看板准备T14设计／交接，不自动实施。
- 不删除文件、不修改T05／T06／T09～T12实现、DownloadExecutionResult合同、DownloadService反射表面、plugin-api／Tushare生产代码、迁移／策略／Dataset／POM／HTTP／前端。现有测试只运行回归，除了上述ProductionApplicationContextIT不要求改构造helpers。
- 实施新增文件显式加入Git，保留当前 `feat/date-range-download`、HEAD、原暂存及并行ISSUE-017，不提交／发布；当前设计子任务不改看板／交接或代码。

## Tests

实施前记录分支／HEAD／原暂存及58份受保护资源摘要（49 Dataset、2生产策略、7生产迁移）。按三组先行期望先写新Core测试，运行首命令确认缺入口与实际行为RED，再实施最小服务。后续不能把缺类型编译错误、测试设置错误、Mockito环境错误当可执行行为反例。

| 测试组 | 必须观察的结果 |
|---|---|
| 原任务与表面 | 唯一七参构造器、唯一 `execute(UUID,RequestId)` 返回既有result；输入空／外层事务／仅同步范围零SQL。忙时find／插件均0；空Optional为NOT_FOUND，空主表为INVALID，无新任务。先占用再读取：等待旧执行结束后新一次读取只见剩余项；没有缓存旧页面数据 |
| 全部前置 | 末项参数／selector／Session不兼容、缺adapter、插件下线、当前来源证据不足、最后plan失败时首项fetch／commit／update均0，所有原项及JSON不变；plan建议把RANGE拆成日时拒绝且保留原项。原map旧缺两端可用，单边／非法／冲突拒绝；原始范围与selector不同不误拒绝 |
| 完整日历 | 只确认失败DATE3／7并集；含RANGE时覆盖完整内部日期；原1～10日不扩张。前项全闭／末项缺日历则零删除；两市场任一开盘仍业务。相同task第二次调用重新确认，不复用上一轮；非交易模式零辅助回调 |
| 全闭／混合范围 | 全闭DATE及STOCK完整RANGE只调用commitClosedRetry且传同一个原Decision。两股同闭日H1、S0；全部删后NO_OPEN_DATES/nullID/remaining0。mixed RANGE3～7保留完整一次fetch、一个提交，H0；另全闭DATE4重叠仍H0。全闭清理尚未轮到的停止快照H冻结、N排除该闭项但scope仍披露，不能按scopes长度造N |
| 精确重建 | 开头三组完整字面map；REQUEST RANGE4～6仍一批，STOCK RANGE不拆日；REQUEST即使当前STOCK能力增强也不拆股；MONTH原1月31～3月2的失败2月恢复整月；NONE只原条件；三个原生单日均相等起止且不出现旧时间键 |
| 逐项隔离与继续 | 同日两股分别删除；3成功／7再失败仅留7，第二次只请求7，原1～10日JSON／created_at不变；时间再股票排序。9类来源错误逐一更新原原因后继续，不依赖retryable；局部Failure／末行归属错／后页失败不得提交或删除原项的一部分 |
| 更新确认门槛 | 有界latch阻塞updateReason返回时，下一entry尚无fetch／validate／commit／update；SavedFailure全key匹配后才继续。updateReason不存在项不重插；保存未知保留F、原ID、remaining=null，未访问原项原原因保持，无find/readback／create／append |
| T11全部分支 | 普通确认成功先index再S/R/I/U及removed；全闭只removed。RolledBack(false)独立update确认才继续；true／Unavailable当前F、零更新停止；Unconfirmed仅unknown，零计数／索引／原因更新。业务和全闭均覆盖stopExecution=true，确认最后删除后nullID/remaining0而整体错误 |
| 前序事实与未知 | 先有一个业务确认成功／一个再次失败确认更新，后注入全部停止分支，断言此前S/R/I/U／removed／原ID保留；业务提交未知时有确定兄弟项保留ID、仅最后项未知则nullID，二者remaining=null，N只未来业务项；无事后查库改分类 |
| 空／重复／跨次 | 合法空原子删项且旧业务整行保留，S1/R0；同轮重复原R保留、I/U0并删当前项；新手动轮次同键不同值正常Upsert，不沿历史索引报DATA_CONFLICT；不复制上次S/F/R到本轮 |
| 共用槽位 | 真实执行线程分别阻塞find、fetch、commitRetry／commitClosedRetry、updateReason，另一新／旧首次或retry立即忙；retrying仅原ID。等待者Future.get超时后不cancel，持有线程继续剩余项、实际结束才释放；真实停止／意外异常finally释放 |
| MySQL精确闭环 | 实际仓储创建原1～10日的3／7记录；重试3成功、7失败，独立连接确认仅7和原JSON；重建服务后仅请求7并删最后主表；再次同ID返回NOT_FOUND、无来源调用。另一task同条件不受影响，不用模拟list或记录计数替代读取 |
| MySQL同股分组／同日两股 | 真实fina_audit表和composite业务键、batchSize2副本；同日A／B原失败记录，A成功仅删A；B三行后组或精确明细DELETE的45000使其全部写入回滚、旧B行及B明细保留。独立updateReason提交后才C请求／提交，原task map不变；两股同日不得合并删除 |
| MySQL清理与更新失败 | 业务成功后删除最后主表失败、非最后touch失败都全回滚；原因UPDATE后touch触发失败，原原因／header时间／JSON保持、停止C。确认全闭DELETE故障也不能被直接从内存抹除；成功闭市清理不改业务表 |
| MySQL未知与丢响应 | delegate.commit真实提交业务＋删除后代理SQLState08，独立连接可见实际删项／最后主表消失，但结果按unknown及正确ID/remaining处理，无重插／update。框架在COMMITTED后抛错则计当前成功及删除，再停止；下一手动调用只读实际剩余，已删不重建。另复现原因保存commit未知停止；独立读库仅测试证据 |
| 事务与生产边界 | confirmCalendar／planBatch／fetchBatch断言actual及sync均false；每个业务／清理／原因更新独立短事务，共享manager／DataSource，来源不持有数据集锁。生产日历／完整来源仍拒绝，原任务保留；旧初始、Web、fixture及生产图回归 |

从仓库根依次执行，设计准备阶段不运行这些功能测试：

```sh
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RetryDownloadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RetryDownloadServiceTest,DownloadServiceTest,DownloadExecutionSlotTest,DownloadExecutionResultTest,DownloadParameterConverterTest,RecoveryUnitProcessorTest,CommittedKeyIndexTest,BatchCommitServiceTest,RetryTaskStorageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RetryDownloadServiceIT,InitialDownloadServiceIT,BatchCommitServiceIT,RetryTaskStorageIT,DownloadControllerIT,FixtureFlowIT,ProductionApplicationContextIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,ControllerUseCaseTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

必须全部实际退出0；七类显式App IT每份最终Surefire XML均tests>0且failures/errors/skipped为0，clean前复制XML／日志并记录本轮数量。默认verify／acceptance只调度打包合同，不能代替MySQL。两次完整构建均须包含真实前端测试／构建，报告本轮数字，不照抄T12的775／778或170。

MySQL固定隔离 `mysql:8.4.6` Testcontainers及App实际迁移，不复制DDL、不使用H2／disabledWithoutDocker。受控STOCK用已有明确ts_code来源参数和真实fina_audit业务schema，测试内策略／batchSize副本不写生产资源；不调用真实来源、读取真实凭证或浏览器。沿T12授权环境处理Mockito／Docker限制；环境变更只读核对Colima context，不关闭断言或修改依赖。全部日历／来源异常使用受控哨兵，确认结果／两表无SQL、Token、正文或异常链。

最终核对58份资源、分支／HEAD及任务外暂存不变，保存独立规格／质量／集成评审、真实计数、请求顺序、两表及业务独立读回和JSON证据；只对新变更／失败／未解决检查重跑。服务重建／丢弃响应消费仅证明用例和持久删除边界，不冒称真实进程kill或socket断连已通过。

## Acceptance

1. 槽位内重新读取原任务，只处理当前明细；忙为DOWNLOAD_BUSY（未来409），不存在为RETRY_TASK_NOT_FOUND（未来404）。无缓存旧项、create／append、自动重试或重建已删范围。
2. 全部映射、Session、完整必要日历和业务来源规划屏障通过前零业务／原因更新／删除；插件／参数／日历／当前请求不可兼容时原记录保持。REQUEST及STOCK RANGE不拆、不合并、不按展示区间执行。
3. 每项成功业务与精确删除原子确认后才累计；两股同日独立、3／7日原JSON不变、最后一项删除主表。合法空／同轮重复及全闭正确清理且旧业务保留。
4. 再次来源失败按原key更新原因并在保存确认后继续；明确回滚、存储不可用、保存未知、删除未知及确认后框架故障各分支正确停止／保留前序事实，无新任务、原因覆盖邻项或事后推断。
5. 本轮S/F/N/H、R/I/U、remaining、taskId与确认事实一致；全闭S0，混合RANGE完整请求、H不夸大，未知最后删除不提供虚假存续任务入口。不把旧明细数／原区间当历史成功或本轮失败数。
6. 规定Core／七类MySQL／helpers／两次全量构建／合同检查及独立评审通过，新增文件纳入Git、受保护资源和旧Web保持。只确认AC-PRD-RANGE-03／12／14／15／17／21／22／23／24／25／26／28的本项机制；T14HTTP、T18真实中断、T20生产来源仍由后继验收。

## Risks

- 无未解决产品事实；已明确解决T11旧全闭消费者措辞与现行OpenAPI的区别。全闭删除不是业务单元成功，混合RANGE的完整恢复优先于按日重规划，不能把合法原范围偷偷判无效。
- 仅单进程槽位；不协调外部SQL对原任务的直接编辑／删除，不保证跨重启永久幂等。真实原项锁内不存在原样报错，不猜应该补哪个任务。查不到任务也不证明曾丢失响应的下载一定成功。
- commit未知可能已删除原项甚至主表，最后项的taskId／remaining必须不确认；不能用readback替代本次提交证明。源失败更新未知也只能保留前序确认结果，原错误可能尚未刷新。
- 不把31天原始输入上限误用于合法旧selector、月份展开或重试项总量；本轮只保留当前业务响应，但完整来源与摘要内存仍随合法数据量增长，不新增未授权上限。
- 生产完整来源／日历注册表和49项REQUEST仍封闭；测试策略／MySQL证明的是精确重试机制，不能升级实际来源或ISSUE-008结论。本文设计就绪不表示T13已实施，更不表示新HTTP／真实断连已验收。
