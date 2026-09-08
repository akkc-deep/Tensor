# RANGE-T11 恢复单元提交与失败明细原子删除设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T11`：每个已校验恢复单元独立提交；重试时业务 Upsert、精确失败明细删除及主表收尾在同一 MySQL 事务内完成。服务区分确认提交、确认回滚和结果未知，为 T12／T13 提供与真实提交一致的单元结果。

本设计在 T10 已记录 COMPLETED 后准备；读取时 T11 为 Order 11、NOT_STARTED，依赖 T09／T10，Design／Handoff 为 None。本次只准备设计，不启动实现或功能验收。

## Scope

- 新增 Core `BatchCommitService`，复用现有 `PersistenceService`、`DatasetLockManager`、JDBC 批量 Upsert 与 T10 仓储原语；App 增加一个服务 bean。
- 消费 T09 私有构造的 `ReadyUnit`，提供首次成功、已有失败项重试成功和完整确认全休市删除入口；返回提交确认后的 R／I／U 候选，不接管执行累计。
- 增加事务完成观察、输入及票据预检查、真实 MySQL SQL 分组／明细删除／主表收尾回滚、同连接与锁生命周期验证。
- 不接入 DownloadService、执行槽位、首次／重试循环、失败原因保存、HTTP／DTO／页面、来源或日历提供器；不写历史成功账本、状态、计划、自动重试、补偿或启动恢复。T12／T13 决定调用顺序及本轮结果，失败原因仍通过 T10 的独立短事务保存。
- 不修改迁移、业务唯一键、49 份 Dataset YAML、生产策略、SQL 分组大小及既有 Upsert 语义；不访问真实业务来源或凭证。保留现有分支 `feat/date-range-download` 和已有暂存成果。

## Approach

### 1. 已核对的直接输入

依看板顺序读取 [TRD §6.2～§6.4、§7.3、§9](../design/Tensor_区间下载_TRD_v1.0.md)，实际 PersistenceService、GenericUpsertRepository、PersistenceServiceIT，再核对 [T09设计](RANGE-T09-design.md)／[证据](../verification/RANGE-T09-recovery-units.md)、[T10设计](RANGE-T10-design.md)／[证据](../verification/RANGE-T10-failure-storage.md)及实际代码。错误语义复用 [error-codes](../contracts/error-codes.md)。日历额外输入是已存在的 plugin-api `CalendarScope`／`CalendarDecision`、T05 `DownloadParameterConverter.mapRetry` 及 [T06设计](RANGE-T06-design.md) 的整轮日历前置屏障，不新增日历合同。

| 实际输入 | T11 的处理 |
|---|---|
| `PersistenceService.persist(AdaptedBatch)` | REQUIRED、60 秒；非空时先取数据集锁，在事务同步的 afterCompletion 解锁。加入外层事务时返回的 WriteCounts 只是候选，不能直接作为提交证明 |
| `DatasetLockManager` | 真实类名是此名称；仓库没有 DatasetWriteLockManager。按 DatasetKey 使用同一个 App singleton；不新增锁管理器、不主动再次 acquire／unlock |
| `GenericUpsertRepository` | JdbcTemplate.batchUpdate 按 definition.batchSize 分组；组数不是事务数。保留现有业务键和 ExistingKeyRepository 的写前成员查询计数 |
| `ReadyUnit` | 公开 selector／batch／sourceRowCount，index／pending／confirmed 为 `core.download` 包可见；batch 已去除单元内重复及本轮已提交相同内容。零待写行既可能合法空，也可能重复，不能将 R 一律改零 |
| `CommittedKeyIndex` | 每轮一个、绑定数据集和当前线程；只有 confirmCommitted 改索引。T09 已约定 T11 确认提交后，由 T12 调 confirmCommitted 再更新本轮累计，T11 保留此所有权 |
| T10 六个公开仓储原语 | lockTask、containsItem、deleteItem、hasItems、touchTask、deleteEmptyTask 都加入调用者绑定连接的事务；不自行提交，不获取数据集锁 |
| T10 StorageService | create／append／updateReason 禁止外层事务，SavedFailure 仅正常提交返回后产生；T11 不在单元事务内调用这些方法 |
| App 装配 | 当前 PersistenceService、RetryTaskRepository 共用同一 JdbcTemplate／DataSource，使用相同 PlatformTransactionManager；Boot JDBC 本地事务管理器属于 DataSourceTransactionManager 家族。没有需要新建第二个 manager／DataSource 的理由 |

T09 已验证内存单元及替身提交顺序；T10 已在真实 MySQL 验证两表和原语，均未证明本项业务＋删除组合。T11 必须产生自己的 MySQL 证据，不能复述前项为通过。

### 2. 固定接口及票据边界

新类放在 `com.akkc.tensor.core.download`，与 ReadyUnit／CommittedKeyIndex 同包。仅一个生产新类，小结果类型嵌套其中；不扩展 plugin-api 或 HTTP 结果类型。

```java
public final class BatchCommitService {
    public BatchCommitService(PersistenceService persistence,
            RetryTaskRepository tasks, DownloadParameterConverter converter,
            PlatformTransactionManager transactions, Clock clock);

    public CommitResult commitInitial(ReadyUnit ready);
    public CommitResult commitRetry(ItemKey item, ReadyUnit ready);
    public CommitResult commitClosedRetry(DatasetKey dataset, ApiDescriptor api,
            ItemKey item, CalendarDecision decision);

    public sealed interface CommitResult
            permits Committed, RolledBack, Unconfirmed, Unavailable {
        RecoverySelector selector();
    }
    public record Committed(RecoverySelector selector, long sourceRowCount,
            WriteCounts writeCounts, boolean stopExecution) implements CommitResult {}
    public record RolledBack(RecoverySelector selector,
            boolean storageUnavailable) implements CommitResult {}
    public record Unconfirmed(RecoverySelector selector) implements CommitResult {}
    public record Unavailable(RecoverySelector selector) implements CommitResult {}
}
```

必要引用非 null；计数非负。只有 Committed 携带计数；其他结果没有 R／I／U 字段，不以 `(0,0,0)` 表示未知。结果不保存 Throwable、SQL、任务参数、来源正文或凭证；不新增持久状态、返回完整行或新公共错误码。

- `Committed.stopExecution=false` 是通常正常返回；已观察到真实 COMMITTED，但事务框架在完成通知之后仍抛异常时为 true，保留已提交事实并停止后续执行（§4）。
- `RolledBack` 只用于 SQL／事务异常且有§4规定的回滚证明。storageUnavailable 区分已确认回滚但连接／资源异常；false 仅允许下一层尝试保存明确失败，不等于可立即继续来源请求。
- `Unconfirmed` 对应 COMMIT_UNCONFIRMED；`Unavailable` 仅表示事务回调尚未开始时发生数据库／事务启动异常，没有执行任何本单元 SQL，对应 PERSISTENCE_FAILED 的存储不可用停止路径；两者均不发布计数。Unavailable 不谎称收到了 rollback 确认，也不谎称可能执行了本服务尚未调用的 Upsert。
- 不存在任务／项抛固定无 cause 的 TensorException `RETRY_TASK_NOT_FOUND / Retry task was not found`；数据集／选择器／保存参数不相容为 `RETRY_TASK_INVALID / Saved retry task is invalid`。调用者误用 null、外层事务、已确认／跨线程票据是固定安全 IllegalArgumentException／IllegalStateException，不伪造可保存业务失败。

入口先拒绝 `TransactionSynchronizationManager.isActualTransactionActive()` **或** `isSynchronizationActive()`，固定消息 `Recovery unit commit must not join an existing transaction`。后一项防止加入无实际事务的外层同步范围后，错误地提前认定完成。随后做非数据库输入检查，才进入短事务。

将 CommittedKeyIndex 现有 confirmCommitted 内“线程、票据所属索引、未确认、版本及冲突检查”原样提取为包可见 `void checkConfirmable(ReadyUnit ready)`；公开 confirmCommitted 调用此方法，再 putAll／markConfirmed，保留原公开签名与全部拒绝行为。T11 对普通两个入口在 SQL 前调用 `ready.index().checkConfirmable(ready)`；commitRetry 另要求 `item.selector().equals(ready.selector())`。不开放 ReadyUnit 构造器、可变摘要或公共 index getter。

调用者在同一个执行线程严格执行 `validate → commit → 若 Committed 则 confirmCommitted → 累加 → 下一单元 validate`。不提前 validate 多个单元、不跨线程、不在 commit 和 confirm 之间操作索引，不重复提交未确认票据。预检查使可预见的票据误用在 SQL 前失败；索引确认依旧重复检查，不能因为提前检查就取消原保护。T11 从不在事务回调／afterCommit／afterCompletion 写索引或本轮累计，也不捕获下一层的确认异常。若提交后发生内存确认／累计错误，已提交业务不能被解释为回滚、失败重建或重新执行；调用者停止并保留已知提交事实。VM 致命错误不纳入正常返回保证。

### 3. 单元事务、连接与锁顺序

每次调用创建独立的本地观察对象，复用服务中配置为 REQUIRED、timeout=60 的 TransactionTemplate；不使用 REQUIRES_NEW、NESTED、手工 Connection.commit 或跨单元外层事务。首次入口也在 T11 开启外层短事务，使零行与非零行获得相同提交观察边界；PersistenceService 的 REQUIRED 仅加入这一事务。

进入回调的第一步标记 entered，并要求实际事务及同步都 active；立即注册§4观察器，之后才执行任何仓储或持久化操作。整个服务同步执行，不启动线程／事件。锁及 SQL 固定如下：

| 入口 | 回调内严格顺序 |
|---|---|
| commitInitial | `persistence.persist(ready.batch())`，保存候选 `WriteCounts`；不触碰任务表 |
| commitRetry | `lockTask(taskId)` → 核对 header.datasetKey 等于 ready.batch.datasetKey → `containsItem(item)` → `persistence.persist(ready.batch())` → `deleteItem(item)` → `hasItems(taskId)` → 尚有项时 `touchTask(taskId, now)`，否则 `deleteEmptyTask(taskId)` → 保存候选 |
| commitClosedRetry | 同样 lockTask／数据集／containsItem → 验证锁内保存参数与日历结果精确相等（§5）→ deleteItem → hasItems → touch 或 deleteEmptyTask；不调用 persist、不获取数据集锁、不写业务表 |

deleteItem 必须返回1；最后一项的 deleteEmptyTask 必须返回1。已锁主表且确认项存在后仍返回0是数据库／事务一致性故障，在回调中抛固定安全 `DataRetrievalFailureException("Retry task cleanup did not affect the expected row")` 触发整单元回滚。touchTask 的0不能认作不存在：MySQL 相同毫秒／相同值的 affectedRows 受驱动 foundRows 设置影响；主表存在性由持有的锁保证。只删完整五字段 ItemKey，不按日期、股票或taskId批量删除；同日另一股、其他时间及其他任务保留。

`now=clock.instant().truncatedTo(MILLIS)` 在持有主表锁后、收尾写入前取一次；不改 created_at／task_params／plugin_id／api_name。主表删除无级联依赖，继续用真实 hasItems 及 NOT EXISTS 判断最后项。

普通非空重试顺序明确是 **任务主表行锁 → PersistenceService 中的同一个数据集锁 → 业务查询／全部 Upsert 组 → 明细及主表 SQL → 事务完成**。数据集锁由现有 afterCompletion 释放，任务锁由数据库在完成时释放；persist 的加入事务返回绝不是解锁点。不要在 T11 手动提前 unlock，也不要再次 acquire 相同锁增加释放责任。空单元没有业务读写，PersistenceService 原有空分支不获取数据集锁；主表锁仍持有到真实事务完成。闭市分支同理。

本服务只接收已经完成的 ReadyUnit 或 CalendarDecision；没有 DataSourcePlugin／HTTP client／DownloadContext 回调参数，因此不可能把网络调用放进事务。T12／T13 先完成取数、整体检查及单元适配，或完整日历确认，之后调用本服务。

App 的新 bean 直接注入既有 PersistenceService、RetryTaskRepository、PlatformTransactionManager、Clock；用已有 ParameterValidator 构造一个 DownloadParameterConverter 传入。只增加该 bean，不新建另一套 JDBC／manager／锁，不改 DownloadService 构造器。两个持久化仓储和 T10 仓储继续引用同一个 JdbcTemplate 及同一个 DataSource。真实集成测试用可记录的 DataSource 代理同时包住这一个实例，断言 lockTask、ExistingKey SELECT、每组 Upsert、DELETE、主表收尾在同一物理连接且恰一次物理 commit／rollback；两个不同 manager 即使连接同一数据库也不算满足条件。装配合同以这一个 bean 的实际依赖及测试为保证，不扩充 PersistenceService 的公共 API。

### 4. 提交结果的证据分类

不能用 `catch DataAccessException => 已回滚`，不能只用 SQL 回调正常返回认定提交，也不能把 Spring 的某个事务异常类型等同物理结果。服务用一个仅本调用可见的 TransactionSynchronization，`getOrder()` 为 `Ordered.HIGHEST_PRECEDENCE`，记录：

- `entered`：事务回调已进入；在注册观察器之前立即设置。
- `commitPhaseSeen`：beforeCommit 被调用即置 true，永不清除。这个信号只说明已经进入可能提交阶段，**不**是已提交。
- `completion`：afterCompletion 收到的唯一状态；初始为“未收到”。回调只赋字段，不做 SQL、日志、索引或业务动作，不抛异常。
- `candidate`：完整业务与清理 SQL 成功后在回调内设置的不可变 R／WriteCounts 候选；不向调用者发布。

模板 execute 的边界捕获 RuntimeException（不捕获 Error），先按下表判断是否已有确定的完成事实；仅 DataAccessException／TransactionException 可形成普通存储失败结果，其他编程／输入异常在确认回滚后原样传播固定安全类型。事务前输入异常根本不进入此分类。

| 观察证据 | 返回／传播 |
|---|---|
| completion=COMMITTED 且 candidate存在，execute正常返回 | Committed，stopExecution=false；此时才构造公开结果 |
| completion=COMMITTED 且 candidate存在，execute仍抛异常 | Committed，stopExecution=true。框架已报告成功提交，后续异常不能变成回滚；不自动重试，不把异常正文带入结果 |
| completion=ROLLED_BACK 且 commitPhaseSeen=false，执行失败为SQL／事务异常 | RolledBack；明确回滚后才允许下一层将 PERSISTENCE_FAILED 交给 T10 保存 |
| 相同回滚证据，但失败为锁内明确not-found／invalid或意外编程异常 | 传播该固定业务／编程异常；不产生失败项，不把缺项解释为成功 |
| entered=false 且 execute抛DataAccessException／TransactionException | Unavailable；本单元从未进入SQL回调，停止 |
| 其余已进入回调的执行失败／未知完成状态，或正常返回却没有可信COMMITTED候选 | Unconfirmed；停止、不计数、不确认索引、不更新或补建失败项 |

**保守规则：**只要 beforeCommit 已运行，后续 ROLLED_BACK 也不作为明确回滚。JDBC commit 可能已经在服务端成功再抛错；若 manager 启用 rollbackOnCommitFailure，随后 rollback 正常返回只能回滚一个已经结束／新开始的事务，不能证明之前未提交。上述规则不依赖该 manager 开关，也不更改全局配置。反过来，afterCompletion=COMMITTED 表示本地 JDBC manager 的物理 commit 已正常返回；即使 afterCommit 或 manager 包装器随后抛异常，仍有提交证据。仅 delegate Connection.commit 已在服务器执行但代理随后抛错时，Spring 不会收到这一成功确认，必须得到 Unconfirmed。

Committed.stopExecution=true 的下一层处理固定为：先按已提交单元确认索引及计数，再停止并使用既有 INTERNAL_ERROR 报告内部完成阶段故障；不使用 PERSISTENCE_FAILED／COMMIT_UNCONFIRMED、不增加本单元失败或未知项。T11只提供这个内部结果，不实现HTTP映射。

beforeCommit 中其他同步回调抛错也可能保守归未知；本服务不注册此类业务回调。这个局限优于猜测物理 commit 有没有发生，且不影响当前 SQL／清理失败在提交阶段前确认回滚的主路径。观察器不承担修复任意第三方事务管理器的职责；本项支持并实测 App 使用的 Spring 本地 JDBC manager，不能用伪造 completion 的测试 manager 宣称真实数据库保证。

RolledBack.storageUnavailable 用异常**类型／结构化SQL状态**判定，禁止匹配 message：检查 cause 与 SQLException.nextException 链（identity set防循环），遇 `DataAccessResourceFailureException`、`RecoverableDataAccessException`、`CannotCreateTransactionException`、`SQLTransientConnectionException`、`SQLNonTransientConnectionException`、`SQLRecoverableException` 或 SQLState 以`08`开头即 true；若失败本身为 TransactionException 也设true（事务系统故障）。其余 DataAccessException 且回滚确认可为false，包括受控SQLState45000清理／分组错误。SQL字符串与原始异常链只作本地检查，不输出到结果／失败表。

false 的后续固定规则是：T12／T13在本服务返回、锁释放之后调用 T10 create／append／updateReason 保存 PERSISTENCE_FAILED；**只有该独立保存事务确认返回 SavedFailure 才允许继续后续单元**。true／Unavailable／Unconfirmed停止；未知不得调用失败保存。T11 不做健康探测、自动重试、独立保存或后续来源调用；真正存储不可用会使保存未确认并由 T10 阻止继续。任何已提交的前序单元保留。

### 5. 合法空、同轮重复及完整全休市

普通零行 ReadyUnit 走同一入口／外层事务，不从 rowCount 或 HTTP 200自行制造票据。persist 对合法空返回 WriteCounts(0,0)，重试仍原子删除精确项与收尾主表，绝不删除旧业务行。

- 来源完整合法空：Committed 的 sourceRowCount=0、I=U=0；首次不写任务，重试移除该失败项。
- 同轮相同内容全部被 T09 过滤：batch.rows为空，但 sourceRowCount保留原来源行数；确认提交后可完成单元，R不是0、I=U=0。T12仍调用confirmCommitted标记票据已消费。
- 混合新／旧键：仅实际待写的 distinct keys 按 PersistenceService 的写前成员查询返回 I／U，R仍为完整来源行数。新执行使用新索引，可按既有 Upsert 更新历史同键内容，不跨次DATA_CONFLICT。

全休市不是来源合法空，不能伪造 ReadyUnit／FetchResult。`commitClosedRetry` 是只处理**已有**失败项的独立入口：

1. 事务前要求 api.apiName 等于 dataset.apiName，api.downloadPolicy.mode 为 TRADE_DATE_RANGE，selector.timeType 只能DATE或RANGE；否则安全RETRY_TASK_INVALID。NONE／MONTH及公告／原生日历范围不允许凭休市删除。
2. 从保存 selector 解析出**完整**日期集合：DATE一个日期；RANGE使用规范两端及全部包含日期，不裁剪到原始展示区间或日历已有键。不增加新的天数上限；现有共享selector和T05继续执行既定校验。
3. decision 是本轮所有待重试日期的一次完整确认（下段），不能逐项重新调用SPI。事务前要求decision非null、decision.scope.dates包含本项完整日期集合，并逐个保留decision.calendars中的**全部必要身份**，检查每个身份在本项每一天均为false；任一缺失或open均固定 CalendarUnconfirmedException，零SQL。本项之外的本轮日期可以开盘，不影响本项全闭结论。CalendarDecision构造器保证各身份对整轮日期完整覆盖；投影只能筛日期，不能删除市场身份或将并集以外的未知值当false。
4. 锁内确认原项及数据集后，要求 `decision.scope().publicParams().equals(header.taskParams())`，将整轮日历严格绑定该任务冻结公共条件（包括原始展示日期，不能据此推算实际日期）。另调用 `converter.mapRetry(api, header.taskParams(), item.selector())` 检查当前精确选择器仍可按保存条件重建；映射结果只验证可重建性，不替换日历publicParams。这样其他exchange／任务条件的日历不能删除当前项，STOCK目标仍由原ItemKey及T05检查负责。mapRetry不进行网络调用、不改参数。绑定不符为CalendarUnconfirmedException；无法重建保留T05的RETRY_TASK_INVALID。
5. 通过后仅执行§3完整键删除和主表收尾，提交候选 R=I=U=0。没有索引待确认票据；下一层只累计本轮已完成单元及0行计数。失败、确认回滚、提交未知与普通重试使用相同事务分类。

T13的前置消费顺序固定为：读取本轮原任务失败项快照，先检查全部项兼容性；按全部实际selector求完整日期集合的并集（不连续3／7日只有3／7，RANGE包含全范围，不从原始展示区间补日期）；以 `new CalendarScope(snapshot.header().taskParams(), allRetryDates)` 调用一次当前插件confirmCalendar，要求返回scope整体相等。**这一个整轮Decision成功以前，不允许任何业务来源调用、业务写入、失败原因更新或明细删除。** 任一必要来源／日期失败则保留全部原项。成功后所有单元共用这一个不可变Decision；T11仅在内存投影本项日期检查，绝不逐项confirm→delete后才确认下一项。测试驱动必须验证“前项全闭、后项日历缺失”时T11调用次数为零且两项都保留。

日历publicParams继续表示冻结公共条件，与T08首次使用original.values的合同一致；重试直接使用保存的taskParams，保留原展示日期但实际覆盖只由scope.dates决定。STOCK的ts_code存于selector而不重复写公共map，T06必要市场集合不能按股票任意缩小；来源请求专用的trade_date等映射参数不取代这份公共map。T05 mapRetry仍负责执行条件和对象重建。当前插件／日历提供器负责核实“必要日历”集合，Java record或布尔值不能自行证明生产市场事实。T11不添加真实提供器，不授权49项生产来源能力。首次计划全休市由T08空计划／T12完成，不调用本删除入口、不创建任务，也不伪造一个来源成功单元。

### 6. 最小变更与实施顺序

先在新App MySQL IT写入“业务两组已执行、对应明细DELETE失败，业务与失败记录全部回滚”场景，保留先前成功单元和旧业务哨兵，使用独立连接读回。执行本设计第一条定向IT命令记录先行结果；缺服务类型导致的编译失败是编译证据，不能作为运行回滚反例。

随后实现一个BatchCommitService、索引检查提取和一个App bean；补完成观察器故障矩阵、合法空／全闭及精确键测试。不要为测试重写PersistenceService／Upsert、复制V8 DDL或增加运行故障注入开关。取得实际MySQL证据后完成回归、追踪及本项评审，不启动T12实现。

## Files

以下为 T11 正式启动后的确定范围；当前准备阶段只创建本设计。

- 新建 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/BatchCommitService.java`：三个入口、四个嵌套结果及私有完成观察／分类／日历日期辅助。
- 修改 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/CommittedKeyIndex.java`：仅提取包可见checkConfirmable，保留原确认签名及算法。
- 修改 `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`：一个BatchCommitService bean，复用既有参数校验器、持久化／仓储／manager／clock。
- 新建 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/BatchCommitServiceTest.java`：输入、票据、事务完成分类、安全与调用顺序；受控Ready必须经真实Processor产生。
- 修改 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/CommittedKeyIndexTest.java`：checkConfirmable不改变状态、原公开confirm行为不变。
- 新建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/BatchCommitServiceIT.java`：真实Flyway／MySQL事务组合、连接与锁、故障注入、空与全闭。使用App已有Flyway及Testcontainers依赖，不为Core增加Flyway／迁移复制。
- 新建 `docs/verification/RANGE-T11-unit-commit.md`；修改 `docs/traceability/tensor-range-requirements.md`：只记录本项实际证据及AC范围。完成时按看板流程记录T11并准备T12设计／交接，不扩大本项运行功能。
- 不删除文件；不改PersistenceService、GenericUpsertRepository、DatasetLockManager、RetryTaskRepository／StorageService、ReadyUnit、迁移、POM、DownloadService、Web／前端、生产Dataset／策略。现有PersistenceServiceIT等只执行回归，保持公共表面反射断言不变。
- 正式实施的新文件显式加入Git，不重置原暂存、不提交／发布；本次设计子任务不操作暂存或看板，交由主流程收尾。

## Tests

设计准备未执行下列功能测试。正式实施必须在隔离 `mysql:8.4.6` Testcontainers schema，用App实际Flyway迁移（包括V8）及已注册业务definition／adapter构造服务链；为批分组场景可在测试内使用现有合法definition的较小batchSize副本，业务列／键与实际schema保持一致。触发器或DataSource／Connection代理仅在测试中使用，不用H2／纯Mockito替代MySQL事务证据。

| 套件／场景 | 必须观察的结果 |
|---|---|
| 首个MySQL反例 | 创建A已提交单元及待重试B，B至少3行、batchSize2；让tensor_download_task_item的精确B删除触发SIGNAL45000。在独立连接核对B所有组插入消失、B更新旧行恢复、B项及主表原时间保留、A业务保持。服务为RolledBack且索引未确认 |
| SQL分组失败 | B最后组故障，前组和已有行更新全部回滚，未执行成功删除；A保留。去除测试故障后C可通过独立短事务提交；测试驱动在B独立updateReason确认保存后才尝试C |
| 明细后收尾失败 | 分别让touchTask失败、最后项deleteEmptyTask失败；业务、已删项、主表都恢复，不留下业务已写但失败项消失的半状态。相同Clock下touch返回0允许成功，不能误判不存在 |
| 正常首次／重试 | 初次不写两表；重试仅删精确项，非最后项仅更新主表updated_at，原参数／created_at不变；最后一项恰好删主表。同日两股、不连续3／7日、完整REQUEST RANGE和STOCK RANGE均以完整键精确删除，另一个taskId保留 |
| 已有业务／计数 | 同时插入新键与更新旧键，WriteCounts准确；来源重复行保留R、只写去重后行。新执行允许更新历史同键异内容；SQL affectedRows不能替代I／U |
| 缺项与错绑定 | 主表不存在、项不存在、dataset不符、ready与item.selector不相等均拒绝，业务SQL为零，不重建已删项；锁内拒绝正常回滚后为固定NOT_FOUND／INVALID，而非RolledBack业务失败 |
| 票据与索引 | 预检查拒绝已确认／错线程票据且getTransaction和SQL为零；checkConfirmable与普通T11成功／失败均不修改索引。真实提交返回后测试驱动confirm并累计；回滚／未知绝不confirm。正常成功后重复确认仍按T09拒绝；下一轮新索引为空 |
| 零待写行 | 严格完整空Ready与同轮重复Ready分别验证R=0／R>0、I=U=0；重试原子删项、旧业务全部列不变；初次两表不变。重复Ready用实际Processor在第一次确认后重新validate产生，不能手造票据 |
| 全闭市 | 受控TRADE_DATE_RANGE保存DATE及完整RANGE，必要两个市场逐日false；整轮一次确认、事务内公共参数绑定并仅投影本项日期后删除，旧业务不变，R/I/U=0。至少一市场任一天open、错scope日期／exchange／任务公共条件、缺日期、无必要日历、NONE／MONTH／非交易模式均保留项；构造不变量直接断言CalendarDecision拒绝，不反射制造不可能record。整轮前项全闭而后项必要日期缺失时来源业务及T11调用均为零、原明细全部保留；整轮其他项开盘时当前全闭项可按完整身份投影删除 |
| 连接／传播 | 所有业务＋管理SQL记录同一物理连接，REQUIRED／60秒；初次非空及重试各只有一个物理commit。persist内部加入不会物理提交。无外层事务时正常独立开启；外层SUPPORTS同步／真正外层事务拒绝；生产bean依赖使用同一个manager、DataSource及锁singleton |
| 锁生命周期 | 双线程有界latch／future：重试已取任务锁、等待预先占用的数据集锁时，第二连接对同任务FOR UPDATE阻塞，证明任务先锁；持久化返回后、明细清理／物理commit或rollback尚未结束时，另一业务写线程仍不能取得同数据集锁。分别覆盖提交／回滚后可继续；另一dataset／task独立。无长sleep、无死锁后无限等待 |
| 网络边界 | 测试驱动的受控来源／日历调用发生时同步与实际事务均inactive，之后才进入T11；服务签名和依赖不接受来源回调。不能以真实业务HTTP调用作本项测试 |
| SQL失败＋rollback成功 | 在真实连接内执行触发器错误，rollback正常返回；afterCompletion=ROLLED_BACK且未进入beforeCommit，返回RolledBack。storageUnavailable=false才允许独立保存；保存失败不继续C |
| rollback失败 | SQL失败后Connection.rollback代理抛SQLException，Spring完成为UNKNOWN或缺确认；返回Unconfirmed，业务索引不改、原原因不主动更新、C未调用。独立清理测试连接后读库只能作为测试事实，不用于生产反推 |
| commit答复未知 | Connection.commit先真实delegate.commit成功，再抛SQLState08006；独立连接实证业务＋删除已落库，但调用返回Unconfirmed，无R/I/U／索引／失败重建。另测commit在delegate前抛错；两个未知结果一致，不从事后表值回填结果 |
| commit异常后rollback成功 | 对同一测试manager启用rollbackOnCommitFailure，复现真实commit后代理抛错且后续rollback正常；即使Spring给ROLLED_BACK也因beforeCommit已见而返回Unconfirmed。不得关闭该反例迁就默认开关 |
| commit后的框架异常 | manager代理先正常delegate.commit（同步已报告COMMITTED）再抛TransactionSystemException；得到Committed(stopExecution=true)而非RolledBack／Unconfirmed，候选计数正确且下一单元不执行。纯单测补afterCommit抛错及正常execute缺completion时的保守结果 |
| 启动／资源异常 | 事务开始连接失败且entered=false为Unavailable；无业务SQL／索引。SQLState08及连接异常链若rollback明确完成则RolledBack(storageUnavailable=true)，停止；SQL45000普通局部错误为false。nextException循环安全，原始SQL／凭证哨兵不出现在公开结果／安全异常／失败原因 |

从仓库根顺序执行，第一条先用于上述先行用例，完成实现后再次执行全部规定检查：

```sh
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=BatchCommitServiceIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=BatchCommitServiceTest,CommittedKeyIndexTest,RecoveryUnitProcessorTest,RetryTaskStorageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=PersistenceServiceIT,ExistingKeyRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=BatchCommitServiceIT,RetryTaskStorageIT,FlywaySchemaContractIT,ProductionApplicationContextIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

本机T10已验证Docker context为Colima；如仍使用同一环境，给**测试进程**设置 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock` 和 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。ProductionApplicationContextIT不接受真实token覆盖其无凭证场景，沿T10使用 `env -u TENSOR_TUSHARE_TOKEN` 执行含该类的命令。不要读取token、改变全局环境、真实来源调用或为了通过而关闭断言／容器测试。

所有命令须退出0。Surefire默认不运行数据库IT，Failsafe只跑打包合同；必须显式运行上述IT并逐一读取六类最终XML（BatchCommitServiceIT、PersistenceServiceIT、ExistingKeyRepositoryIT、RetryTaskStorageIT、FlywaySchemaContractIT、ProductionApplicationContextIT），tests>0且failures/errors/skipped均0。在acceptance clean前复制XML／日志至临时证据目录，记录真实类名、数量、命令及结果；Docker未执行不能写MySQL通过或标COMPLETED。受控提交代理的服务器真实状态由独立连接验证，mock测试不替代此证据。

开始前记录分支、HEAD、暂存清单及7份生产迁移／49份Dataset／2份策略资源SHA-256；结束确认上述58份资源不变、原暂存未撤销、ISSUE-017等并行任务内容保留。完整构建应保持生产包无V6／fixture、acceptance仅既有差异和前端回归通过，不因为本项增加bean就修改schema／打包合同清单。

## Acceptance

1. 每个单元独立TransactionTemplate；取数／日历均在事务外。真实重试证明任务主表锁先于数据集锁，两者持续至完成，全部业务及管理SQL同manager／同物理连接且仅一次提交。
2. 任一业务SQL组、精确明细删除或主表收尾失败使当前单元全部回滚；已提交前序单元保留。完整五字段删除隔离同日两股、不同日期及完整RANGE，最后一项才删除主表。
3. 合法空／同轮重复／完整全休市路径均不删除旧业务；空与重复的R不同，完整全休市只在原失败范围及公共条件完整确认后移除，未知日历保留项。
4. 只有可信COMMITTED返回计数候选；确认回滚、启动不可用、提交未知被准确区分。真实commit后丢答复、rollback失败、commit失败后rollback成功均不冒充明确失败；提交后框架异常保留已知提交并停止。
5. T11不修改索引／本轮累计；票据误用在SQL前拒绝，T12／T13仅在Committed后串行confirm再累计。确认回滚后的失败原因保存是独立短事务，保存确认前不继续；未知不补建／更新失败项。
6. 本项MySQL、定向测试、两次完整构建及合同检查实际通过；验证及追踪覆盖 AC-PRD-RANGE-07／13／15／21／24／26 的单元提交机制，未将HTTP、浏览器、真实来源或后续编排宣称完成。新增文件加入Git，原分支／暂存／生产资源保持。

## Risks

- 没有尚待用户决定的产品规则。这里固定的内部结果、同步观察、票据检查和日历绑定是为兑现TRD事务规则的实现选择；设计就绪不表示代码已实现或测试已执行。
- beforeCommit标记刻意保守：进入可能提交阶段后即使回滚回调成功，也不反推未提交；来源计数只保留先前确认单元。系统不提供未知提交核对／补偿端点、自动重放或从业务表重建历史计数。
- CommittedKeyIndex只支持同线程串行执行，SHA-256理论碰撞边界及内存随本轮已提交键增长的边界继承T09。T11不新增并发单元或跨次成功索引；不可预期的提交后内存故障不能回滚数据库。
- 数据集锁是现有进程内锁，I／U沿用现有写前成员查询计数；本项不承诺协调绕过本应用的外部SQL写入或新增分布式锁。主表行锁和数据库事务负责失败项原子性。
- CalendarDecision只能携带已核实日历结果，不能认证来源；真实必要市场集合和生产可用性仍归T06／来源及后续验收。49项生产REQUEST、空完整来源／日历注册表及ISSUE-008未解决状态不被受控测试升级。
