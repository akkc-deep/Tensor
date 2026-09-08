# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T10`，已记录 `COMPLETED`。
- **Next task:** `RANGE-T11`，按预定义 Order 11 选择。
- **Design document:** `docs/task-designs/RANGE-T11-design.md`（[完整设计](../../task-designs/RANGE-T11-design.md)），已完成、完整读取并链接到本项看板行；独立规格及实施就绪评审 PASS。
- **Expected next status:** `READY`；写入并链接本交接后才由 `NOT_STARTED` 转入，尚未启动实现。

## Next Task

`RANGE-T11 — 恢复单元提交与失败明细原子删除`。目标是每个恢复单元原子入库，已有失败项重试成功时业务数据与失败记录同步提交。

实施范围为一个 Core `BatchCommitService`、既有索引检查的包内提取、一个 App bean 及相应测试；复用 PersistenceService、DatasetLockManager、JDBC 批量 Upsert 和 T10 仓储。提供首次成功、精确重试、完整全休市删除三个入口，固定结果分类与消费者顺序。不开启 T12／T13 执行循环，不接 HTTP／页面，不增加迁移或来源能力。

验收须实际观察：

- 每单元独立短事务；来源／日历在事务外，重试先锁任务主表再取数据集锁，全部 SQL 同 manager／同物理连接，锁持续到事务完成。
- 任一 SQL 分组、明细删除或主表收尾失败回滚当前单元，前序已提交单元保留；完整五列键隔离同日两股及不连续日期，最后一项才删主表。
- 合法空、同轮重复及完整全休市可清理失败项而不删除旧业务行；整轮日历未全部确认之前零业务／存储副作用。
- 只有确认提交返回计数；T12／T13随后串行确认索引再累计。已确认回滚、启动不可用、提交未知和已确认提交后的框架故障按设计区分，未知不补造／更新失败项。
- 显式真实 MySQL IT、定向回归、两次完整构建及合同检查全部通过，记录 AC-PRD-RANGE-07／13／15／21／24／26 的本项机制证据；不冒充端到端或真实来源通过。

## Dependencies

### RANGE-T09

- **Artifact:** [T09设计](../../task-designs/RANGE-T09-design.md)、[T09验证](../../verification/RANGE-T09-recovery-units.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/RecoveryUnitProcessor.java`、同目录 `CommittedKeyIndex.java`，以及 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/BusinessContentCodec.java`。
- **Decision:** ReadyUnit由真实Processor在完整来源／归属检查及单元适配后产生，构造器不开放；索引只存本轮已确认键摘要。T11确认事务成功后，由T12／T13调用confirmCommitted并累计。
- **Rationale:** 部分页、待提交数据、确认回滚及未知提交不能作为后续单元成功依据；R必须保留完整来源行数，不能等同去重后的写入数。
- **Constraint:** 保留同线程串行 `validate → commit → confirm → 累计`；REQUEST和已保存STOCK RANGE不拆小。空Ready与全重复Ready均可零写入，sourceRowCount不同。不得改业务键、内容编码、公开Ready构造边界或让T11提前确认索引。
- **Usage:** 新服务与ReadyUnit同属core.download，仅提取现有checkConfirmable做SQL前预检；传ready.batch给既有持久化，提交后返回sourceRowCount及WriteCounts。测试必须通过真实Processor生成票据，并证明回滚／未知没有确认索引。
- **Readiness evidence:** T09定向六类89项、verify729项Java、acceptance732项Java及各170项前端检查通过，独立复审通过；这些证明内存机制。T10随后完整构建仍通过；T09的提交驱动是替身，不能替代T11 MySQL事务验证。

### RANGE-T10

- **Artifact:** [T10设计](../../task-designs/RANGE-T10-design.md)、[T10验证](../../verification/RANGE-T10-failure-storage.md)；`data-plane/tensor-app/src/main/resources/db/migration/V8__create_download_failure_tables.sql`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/retry/RetryTaskRepository.java`、同目录 `RetryTaskStorageService.java`／`TaskParametersJson.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/db/RetryTaskStorageIT.java`。
- **Decision:** 两表仅保存当前明确失败，主表参数及原始日期冻结；ItemKey由taskId及完整RecoverySelector组成。六个公开仓储原语加入调用者同一连接事务，不自行提交或取数据集锁。
- **Rationale:** 同日不同股票必须独立删除；业务成功和原失败项删除必须同一事务，不能在失败记录已删后才发现业务未提交。
- **Constraint:** T11只组合lockTask、containsItem、deleteItem、hasItems、touchTask、deleteEmptyTask；不得在组合事务内调用StorageService的create／append／updateReason，后者拒绝外层事务。不能用REQUIRES_NEW分拆，不更新task_params／created_at，不把touch的0受影响行当作不存在。保存未确认与业务提交未知保留不同语义。
- **Usage:** 按主表锁→精确项存在→PersistenceService→精确删除→真实剩余项检查→touch／删除空主表组合。合法空及全闭不删旧业务行。只有组合事务确认回滚、且存储可用时，未来编排才在锁释放后用独立T10短事务保存PERSISTENCE_FAILED；SavedFailure确认前不继续。
- **Readiness evidence:** T10 Core reactor289项、六类规定MySQL IT共89项、额外App16项均零失败／错误／跳过；verify745项Java及acceptance748项Java、各170项前端测试与构建通过，合同8组＋4个变异反例通过，独立规格／质量／集成复审PASS。真实MySQL已证明两表、原语同连接参与及回滚；业务＋删除组合尚未实施。

上述直接输入的决定和约束已逐项比较，无未解决冲突：T09提供未提交票据，T10提供事务参与原语，T11只负责单元事务结果。T09的后提交索引所有权与T10独立保存边界均保留。

## Start Here

按顺序读取：

1. `docs/task-designs/RANGE-T11-design.md`，完整读取接口、完成观察器、整轮日历消费约束、文件清单及全部测试；不重新设计。
2. `docs/design/Tensor_区间下载_TRD_v1.0.md` §6.2～§6.4、§7.3、§9；全闭入口另核对§5.1和设计引用的T06整轮日历屏障。
3. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java`、`GenericUpsertRepository.java`、`DatasetLockManager.java`，及 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`。
4. 上述T09／T10直接产物及验证；核对实际ReadyUnit／CommittedKeyIndex、六个仓储原语、App共享manager／JdbcTemplate装配，以及现有CalendarDecision／CalendarScope和T05 mapRetry。

**第一个实施动作：**保留当前分支和暂存，记录HEAD、暂存清单以及7份生产迁移＋49份Dataset＋2份策略的58份摘要后，在新 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/BatchCommitServiceIT.java` 写“B业务跨两组已执行，精确明细DELETE触发45000时全单元回滚，已提交A及旧业务哨兵保留”的用例，用独立连接核对，并运行设计第一条定向IT命令记录先行结果。缺类型编译失败单独记录，不冒充运行断言反例；随后按完成设计实施最小服务。

设计Tests已列出全部精确命令，六类最终MySQL XML必须tests>0且failures／errors／skipped均0；acceptance clean前保存XML及日志。T10已确认本机Colima的测试进程配置为 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock`、`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`；含ProductionApplicationContextIT的命令用 `env -u TENSOR_TUSHARE_TOKEN` 保持该测试的无凭证场景。不得读取凭证或跳过Docker检查代替通过。

## Risks

- beforeCommit之后即便rollback返回成功也不能证明此前未提交；设计对此保守返回未知。实际MySQL故障代理须证明边界，不能靠异常名称或事后业务表查询恢复运行计数。
- 整轮必要日历在任何业务／存储副作用前全部确认；共享结果投影本项日期时保留全部市场身份。CalendarDecision是内存合同，不自行证明来源权威性。
- 生产日历与完整来源注册表仍为空，49项仍为REQUEST；受控STOCK／日历用例不升级生产支持，ISSUE-008仍“不依赖，未解决”。
- 继续保留 `feat/date-range-download`、既有暂存及并发ISSUE-017工作；只显式加入本项文件，不提交／发布、不修改旧迁移或用户数据库。READY仅表示设计与交接就绪。
