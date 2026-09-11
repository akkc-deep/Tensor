# ISSUE-018-T04：证券数据与批次成功状态原子提交

任务：[ISSUE-018 看板 T04](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t04)。直接输入：[T02 仓储设计](ISSUE-018-T02-design.md)及实现。共享来源：[总体设计](ISSUE-018-design.md) §3.9、§3.13–3.14、§5.1。

## Goal

同一批证券数据、成功计数和批次 SUCCEEDED 状态在一个数据库事务中提交或回滚，空批次也有可靠成功状态。通过固定的数据集锁→任务行→批次行顺序与执行许可校验，拒绝旧 worker 的迟到提交。

## Scope

新增通用 PersistenceParticipant、PersistenceService 重载及 BatchCommitService，增加真实 MySQL 事务与故障测试。沿用现有 DatasetCatalog、DatasetLockManager、业务键计数、GenericUpsertRepository 和 60 秒事务超时。

persistence 包不依赖 download/task。BatchCommitService 仅依赖既有 persistence、T02 仓储与插件 API 事实对象；不依赖 T03 接收服务。下载、完整性检查、适配、批次拆分、任务终态汇总、失败循环、重试 / 恢复及后台生命周期属于 T07/T08；本项不实现 worker、HTTP、Spring bean 或新配置，不修改 V1–V8、40 个来源 YAML 或注册集合。

## Approach

### 通用事务参与接口

新增 `com.akkc.tensor.core.persistence.PersistenceParticipant`：

```java
public interface PersistenceParticipant {
    PersistenceParticipant NONE = new PersistenceParticipant() {
        public void beforeWrite() {}
        public void afterWrite(WriteCounts counts) {}
    };
    void beforeWrite();
    void afterWrite(WriteCounts counts);
}
```

两回调均在证券事务内同步执行，不能开启自己的事务、访问上游或取得其他数据集锁；异常传播并触发同一事务回滚。afterWrite 接收实际业务键计算的 WriteCounts，在事务提交之前调用，不是提交成功通知。参与者不拥有 commit / rollback，不向其暴露事务管理器或任意 JDBC 回调。

PersistenceService 保留构造器与 `WriteCounts persist(AdaptedBatch batch)`，旧方法只委托 `persist(batch, PersistenceParticipant.NONE)`；新增 `WriteCounts persist(AdaptedBatch batch, PersistenceParticipant participant)`。batch / participant 空值用具名 NPE 拒绝，保持既有 batch 参数约定。

### PersistenceService 的顺序与兼容边界

1. 入口检查 `TransactionSynchronizationManager.isActualTransactionActive()`，有调用方事务时立即 IllegalStateException，不能先获取数据集锁或访问仓储。两种重载执行同一规则。
2. 沿用现有目录查找和 `GenericUpsertRepository.validateBatch`，在副作用之前检查来源、表、列顺序、业务键等合同。非法输入仍按旧 IllegalArgumentException 分类，不让参与者先改状态。
3. `rows` 为空且 `participant == PersistenceParticipant.NONE` 时保留旧零写入快速路径，返回 `WriteCounts(0,0)`，不取得锁、不创建事务。真实参与者（即使其行为恰好为空）必须进入以下事务路径，不能根据 rows 为空省略回调。
4. 在事务外用现有 BusinessKeyExtractor 提取 keys；取得该批 datasetKey 的 DatasetLockManager 锁，然后开启 REQUIRED / 60 秒 TransactionTemplate。因为入口拒绝外层事务，这里始终拥有实际提交边界。
5. 在事务内，先执行 `participant.beforeWrite()`；然后沿用 findExisting、WriteCounts.from、upsert；最后执行 `participant.afterWrite(counts)` 并返回 counts。参与者的空批次 keys=[]、counts=(0,0)，可以跳过无数据的证券 SQL，但两个回调仍各执行一次。
6. 事务成功提交之后才返回 WriteCounts；任何阶段抛错时先完成 rollback，再释放数据集锁。现有 afterCompletion 释放与 finally 兜底可以保留；不得双重 unlock。因为新规则不加入外层事务，也可使用覆盖完整 execute 的 try/finally 简化，但必须保留同步未启用、事务开始 / 回滚失败等路径的锁释放。

**已明确的兼容收紧：** 当前 PersistenceServiceIT 有 `retainsDatasetLockUntilJoinedOuterTransactionCompletes`，证明旧入口曾支持加入外层事务；这与 ISSUE-018 看板的“禁止持有外层数据库事务等待数据集锁”冲突。按本任务新锁序要求，将此用例改为两个重载在外层事务中立即拒绝、无数据写入 / 无锁获取 / 无回调，新增“独立事务提交前锁仍持有”的并发证明。不通过仅限制新重载、REQUIRES_NEW 或挂起外层事务来绕过约束。旧同步 DownloadService 已在入口拒绝事务，不受此收紧影响；其余旧新增 / 更新 / 指纹 / JDBC 分批 / 回滚 / ingestedAt 行为保持回归。旧无参与空批快速路径继续保留。

### BatchCommitService 接口与成功路径

新建 final `com.akkc.tensor.core.download.task.BatchCommitService`，唯一构造器和公开用例：

```java
BatchCommitService(PersistenceService persistence, DownloadTaskRepository repository, Clock clock);
WriteCounts commit(DownloadTaskRepository.ExecutionPermit permit, UUID batchId,
                   AdaptedBatch batch, long sourceRows);
```

依赖不可空；用例先拒绝调用方事务，再校验 permit / batchId / batch 非空和 sourceRows >= 0。sourceRows 是 T07 从已校验 envelope 取得的原始来源行数；WriteCounts 来自实际业务键计数，不能以 JDBC affectedRows 代替。不同业务键重复行仍按现有去重计数，不增加跨批全局去重或擅自要求 sourceRows 等于写入记录数。

1. `repository.findTask(permit.taskId())` 在自己的短只读事务取得预检查快照，结束事务后再进入 persistence。任务不存在、状态非 RUNNING、启动 ID / generation 不同为 TASK_STATE_CONFLICT；任务 datasetKey 与 batch.datasetKey 不同为 DATASET_MISCONFIGURED；deadline 缺失或 `!clock.instant().isBefore(deadlineAt)` 为 TASK_LIMIT_EXCEEDED。此快照只是快速拒绝依据，绝不作为最终写入许可。
2. 创建该次调用私有的 PersistenceParticipant，调用 `persistence.persist(batch, participant)`。不能用外层 TransactionTemplate 包住 persist。
3. `beforeWrite` 在已取得数据集锁和实际事务后依次调用 `repository.lockTask(permit)`、`repository.lockBatch(permit,batchId)`。T02 的低层方法要求现有事务，复验任务 RUNNING / activeRunId / generation，批次归属 / RUNNING / generation。用锁定任务再次核对 datasetKey 和截止，覆盖等待数据集锁时发生的重排或超时；全部通过之后才允许证券查询 / 写入。
4. `afterWrite` 使用一次 `clock.instant()`，调用 T02 的 `repository.succeedBatch(permit,batchId,sourceRows,counts.insertedRows(),counts.updatedRows(),finishedAt)`。该方法参加同一事务、再次验证许可，并检查成功累计计数的 long 溢出，写 SUCCEEDED / 三计数 / finishedAt / 清错误。不要调用任何拥有独立事务的仓储高层方法。
5. 成功返回时数据和状态已经提交，T07 才可记录成功事件或继续处理下一批。任务状态此时仍 RUNNING；本服务不调用 finishTask，也不推导整个任务成功。

截止时间在进入 persist 前和 beforeWrite 的实际写入许可边界检查。若等待锁期间达到截止则拒绝并零写入；beforeWrite 通过后已经开始的事务允许在现有 60 秒期限内完成，afterWrite 不再次以任务截止打断该事务。beforeWrite 对锁定任务的检查不能省略为只信任最初快照。

### 错误与失败记录归属

BatchCommitService 将证券路径的 DataAccessException / TransactionException 转为固定 PERSISTENCE_FAILED，无原始 message/cause；对已分类 TensorException 原样传播。对证券批与注册定义不匹配的 IllegalArgumentException，转换 DATASET_MISCONFIGURED；入口空值 / 负 sourceRows 与外层事务错误仍是调用合同异常，不混入上述包装。可用服务私有嵌套 CommitException，仅接收 ErrorCode，复用 `new DownloadTaskRepository.StoredError(code).message()`；不要依赖 T03 的 TaskException。

本服务只处理原子成功提交。**失败记录由事务外调用者负责：** T07 捕获已分类提交失败后，用现有 `repository.failBatch(permit,batchId,errorCode,clock.instant())` 另开短事务；本项 IT 直接扮演调用者，证明 rollback 后的失败标记独立提交。这里不增加失败重试循环或 failBatch 包装 API，避免 T07 再次标记同一失败。许可已失效时 failBatch 同样拒绝，不能覆盖新 worker。数据库不可用、失败状态无法保存时，由 T07/T08 停止和中断处理，不继续下载。

事务体、计数或成功标记的已知失败必须全部回滚；若连接在 commit 回执阶段中断，调用者不能仅凭异常断言结果不存在，更不能自动重发。数据与成功状态仍由同一数据库事务决定，后续查询 / 恢复消费成功叶子事实。T04 不自行将 SUCCEEDED 改为 FAILED。

## Files

- 新增 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceParticipant.java`：仅两个回调与 NONE。
- 修改同目录 `PersistenceService.java`：重载、外层事务拒绝、参与回调与空批事务，保留原计数 / Upsert 链。
- 新增 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/BatchCommitService.java`：许可 / 截止 / 身份预检查、参与者和提交异常分类。
- 修改 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`：新增重载精确 public-surface 断言；参与事务 / 空批 / 回滚 / 锁序测试；按上述新合同替换外层事务加入断言。
- 新增 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/BatchCommitServiceIT.java`：真实数据和批次状态共同提交、故障与旧许可隔离。
- 不修改 T02 三个参与方法、Migration 或依赖文件来配合测试；若发现 T02 实际缺陷，先用真实失败测试证明，并单独记录最小修复。

## Tests

先扩展 PersistenceServiceIT：带参与者的空 AdaptedBatch 应在同一实际事务中依次 beforeWrite / afterWrite，返回 (0,0)，两个回调的 JDBC 写入在返回后可见；先观察缺少接口 / 重载的失败，再实施。参与测试回调可以写测试专用审计表，不给生产类增加测试开关。

PersistenceServiceIT 保留既有有效回归，新增：

- 两个回调确实在同一连接、同一事务内，beforeWrite 发生在首次证券 SQL 前，afterWrite 在 upsert 后、commit 前；记录 REQUIRED 和 60 秒超时。
- beforeWrite 失败无证券副作用；upsert 第二 JDBC 批失败回滚第一批和 beforeWrite 测试写入；afterWrite 失败回滚证券数据、计数相关测试写入和状态。空批 afterWrite 失败同样回滚。
- 两个 persist 重载在真实外层事务中拒绝，包括空批；回调 / 数据集锁均未访问。旧无参与空批仍可在无数据库可用性的条件下返回 (0,0)。
- latch 阻塞 afterWrite，另一线程对同数据集请求只能在第一次提交后进入自己的 beforeWrite；第一事务失败时同样释放锁。非法批次 / 事务管理器拒绝 / 同步未启用仍释放或根本不获取锁。采用有界 latch/future，不用长 sleep。

BatchCommitServiceIT 使用 Testcontainers MySQL 8.4.6、ScriptUtils 执行唯一 app V8，与证券测试表共用一个 DataSource / DataSourceTransactionManager。测试目录以 T03 已用的 `DatasetCatalog.class.getDeclaredConstructor(List.class)` 反射创建显式受控定义，生产构造器不改。可在测试内创建 `commit_test__prices` 来源表（datasetKey=`commit_test/prices`），列含 symbol VARCHAR(32)、trade_date DATE、amount DECIMAL(18,2)，业务键 symbol+trade_date，及 source_plugin VARCHAR(64) NOT NULL、source_api VARCHAR(64) NOT NULL、ingested_at TIMESTAMP(3) NOT NULL 元列；batchSize=1 用来注入第二证券行失败。字段与元列绑定保持 GenericUpsertRepository 既有规则。

逐项断言：

1. 用 T02 insert/claimTask/savePlan/claimBatch 建立真实 RUNNING 任务 / 批次，再调用 commit。独立连接同时读到证券行与 SUCCEEDED，sourceRows、insertedRows、updatedRows、finishedAt 正确，任务仍 RUNNING；既有证券键更新与新增混合计数正确。无插件 / 上游调用。
2. 空适配批次、sourceRows=0：证券表不变，批次 SUCCEEDED 和三个 0 计数共同提交，不漏 afterWrite。
3. SQL 触发器分别拒绝第二证券行和批次状态更新。前者不留下第一行 / 成功状态；后者不留下任何本批证券新增 / 更新。去掉触发器后在事务外调用 failBatch，只写固定 FAILED / PERSISTENCE_FAILED 和零计数，原证券值保留。
4. 预置兄弟成功批次的累计计数接近 Long.MAX_VALUE，当前计数超过上限时 succeedBatch 抛 TASK_LIMIT_EXCEEDED，当前证券写入与成功状态一起回滚；先前成功批的计数 / 数据不变。
5. 旧 activeRunId、旧 generation、任务不 RUNNING、批次不 RUNNING、批次属于另一任务都拒绝，旧许可不能写证券或覆盖新状态。不同证券 datasetKey 亦拒绝，不因合法表名就混写。
6. 用 JDBC latch 在 BatchCommitService 的 findTask 查询返回前暂停；另一连接先执行 T02 recoverStoppedTask / requeue / claimTask，使初始查询的 RR 快照仍返回旧许可。释放后 beforeWrite 锁定当前行拒绝，验证不能信任预检查快照。无需在生产类添加暂停开关。
7. 可控 Clock：调用时已到截止拒绝；外部线程持有数据集锁，使提交预检查通过后等待，推进 Clock 到截止再释放锁，beforeWrite 拒绝；许可检查已通过、upsert 执行时再推进 Clock 越过任务截止，afterWrite 仍在本次事务超时预算内成功。时钟 / JDBC gate 仅在测试提供。
8. 两个 public commit / persist 事务边界不互相嵌套，返回后外部连接可见共同结果；外层 TransactionTemplate 调用 commit 拒绝，不能通过调用者 rollback 撤销一个已经报告成功的提交。

从根目录运行（Java 21；Docker 环境同 T02/T03）：

```sh
mvn -f data-plane/pom.xml -Dtest=PersistenceServiceIT,BatchCommitServiceIT,DownloadTaskRepositoryIT,DownloadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

四条退出 0，必选 MySQL IT 实际执行且无跳过；`clean verify` 不替代第一条。现有 T03 任务接收 / 查询回归随完整单元保持；T04 不运行需 main / 干净 HEAD 的发布脚本。

## Acceptance

数据集锁在任何任务行锁之前取得，调用方无活动外层事务。证券 Upsert、成功计数、SUCCEEDED 同事务，空批亦然；SQL 与计数失败不留下部分当前批结果，失败状态另用仓储短事务保存。过期或旧许可在写入前拒绝；在途已许可事务可完成，成功叶子不重复累计。persistence 对任务领域无依赖，旧同步和既有有效持久化行为通过回归。实现、实际测试数量 / 命令与审查结果先写 T04 完成证据，再按 Order 准备 T05 设计与交接。

## Risks

- 旧外层事务加入测试与新锁序要求存在明确行为差异，已在本设计固定为拒绝并替换相应断言；不能暗中保留另一条不受保护的路径。
- 同一 DataSource 的 Spring 事务资源绑定是跨证券 / 批次原子性的前提，测试与后续 T09 装配必须一致。共享 JVM 数据集锁及运行许可不构成多实例协调支持。
- T02 succeedBatch 为累计计数会继续锁定该任务的兄弟批次；它先锁任务行，所有相同任务写入因此串行，T04 不改变其锁协议。
- 回执不明与失败状态不可写由后续执行 / 恢复流程消费数据库事实；本项测试不等于整个 worker 生命周期交付。上游范围 / 完整性检查与适配必须在调用 commit 前由 T07 完成。
