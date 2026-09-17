# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`。
- **Completed task:** `DATA-INTEGRITY-T06`，已记录COMPLETED。
- **Next task:** `DATA-INTEGRITY-T07`，按既定Order=7选择。
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T07-design.md`，已完成并回填看板。
- **Expected next status:** READY；本交接链接后按NOT_STARTED→READY准备，不启动实现。

## Next Task

**DATA-INTEGRITY-T07 — 后台执行、预算与中断处理。** 用独立单工作线程消费固定计划，串联只读快照、通用/来源规则和原子报告；保存已提交进度，隔离单元异常，落实单元/任务预算和启动中断。

验收必须观察到：任务运行状态与数据结论分离；规则异常UNKNOWN和确认缺失FAIL可同时保留；证券扫描READ_FAILED为单元ERROR并继续，任务/报告存储故障才FAILED；全部单元COMPLETED/ERROR才能任务COMPLETED。任务超时/停止/重启为INTERRUPTED，剩余NOT_RUN，旧报告/问题/规则不改写。启动恢复及关闭排空期间拒绝首次受理但可重放；真实MySQL验证事务、进度、预算和恢复，证券表不变。

## Dependencies

### DATA-INTEGRITY-T03

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T03-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityReadRepository.java`、`IntegrityReadPlan.java`、`IntegrityReadBudget.java`、`IntegrityReadException.java`。
- **Decision:** 每单元独占REPEATABLE_READ/READ ONLY快照，目标/参考/空日期扫描共享预算；完整物理键游标，参考仅同来源、声明用途和受限范围/等值条件。
- **Rationale:** 单元内一致而非全库历史快照；拒绝跨股票/任意SQL/未经授权参考。
- **Constraint:** 股票参考固定同股票；非股票时间参考须固定其余业务键，范围仅原范围/ISO周/自然月边界。来源具体交易所与窗口由来源确定，core不得猜测。READ_FAILED是证券扫描/快照异常，不能误作报告仓库QUERY_FAILED。
- **Usage:** Runner装配ReadPlan和共享Budget，Evaluator打开快照。T07增加兼容的取消supplier重载，保留原构造器；来源参考hook只提议读取，T03仍最终校验授权。
- **Readiness evidence:** 看板T03已完成，`docs/verification/DATA-INTEGRITY-T03.md`记录30项专项（真实MySQL12+unit18）及1283后端回归通过，无失败/跳过。

### DATA-INTEGRITY-T04

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T04-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityUnitEvaluator.java`、`DefaultIntegrityContext.java`、`IntegrityIssueCollector.java`及`rules/`。
- **Decision:** `evaluate`是同步单元入口，返回不可变Evaluation(result, issues)；按ruleId稳定执行，异常规则UNKNOWN不覆盖已知FAIL，扫描/预算故障返回ERROR且保留不完整问题。
- **Rationale:** 比较、规则归属、精确统计和异常隔离已有一个实现，Runner只负责任务编排和保存。
- **Constraint:** 入口必须是有descriptor的股票单元；NON_STOCK/缺描述由Runner直接报告。READ_FAILED通常在返回Evaluation内，不会抛给Runner。异常后整体及子规则正式覆盖统计均为空，不能恢复部分百分比。
- **Usage:** 将BoundIssue映射为T05 NewIssue后一次saveResult；error继续其他单元，task deadline/停止则保存当前已知结果后终止剩余。不得重复计算统计或每规则重置预算。
- **Readiness evidence:** 看板T04已完成，`docs/verification/DATA-INTEGRITY-T04.md`记录37项读取/比较专项（真实MySQL19）、59项规则/fixture专项和1318项后端回归通过；独立审查问题已修复。

### DATA-INTEGRITY-T05

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T05-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckRepository.java`、`IntegrityCheckJson.java`；`data-plane/tensor-app/src/main/resources/db/migration/V9__create_integrity_check_tables.sql`。
- **Decision:** 任务及计划原子创建，单元结果与问题一次原子保存；保存原范围、规则/定义与精确JSON，查询不按当前元数据重算。
- **Rationale:** 历史报告必须可复算且不暴露半份结果，状态/进度以已提交数据库结果为依据。
- **Constraint:** 所有公开仓库入口拒绝外部事务；saveResult只接受PENDING/RUNNING且拒绝改写旧scope/descriptor/definitionHash。任务definitionSnapshot读回为数组；已有scope.snapshotStartedAt非null时不可清除。V9不增加报告清理入口，不修改证券表。
- **Usage:** T07增加start/complete/terminate/recovery/progress及private事务内报告辅助逻辑；终止原子写剩余NOT_RUN，保留既有终态/问题和RUNNING已有读取时点。只解码历史scope/descriptor，完整定义比对后使用当前typed快照。
- **Readiness evidence:** 看板T05已完成，`docs/verification/DATA-INTEGRITY-T05.md`有真实MySQL与原子/分页/故障证据；T06最终再次实际运行RepositoryIT21项全部通过，见`docs/verification/DATA-INTEGRITY-T06.md`。

### DATA-INTEGRITY-T06

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T06-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckService.java`、`IntegrityCheckQueue.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/IntegrityCheckProperties.java`、`IntegrityCheckConfiguration.java`；`docs/runbook/configuration.md`。
- **Decision:** 原请求先重放，首次才校验完整能力hash并固定范围/计划；预留→独立事务create→publish，poll后释放排队名额。配置全部正数、workers=1，默认500扫描批次/500000累计项/20000问题/120秒单元/1800秒任务。
- **Rationale:** 旧请求在能力或队列变化后仍可找回，排队容量不含运行中任务，崩溃恢复明确交给T07。
- **Constraint:** 不重复入队旧submissionId、不改变原requestHash；Long/BigDecimal在原请求复制阶段拒绝，避免报告codec字符串化碰撞。T06尚无worker或启动恢复，计划均PENDING/UNKNOWN；扫描/时间预算目前仅绑定。完整hash包括全插件口径，不能仅核验用户子集。
- **Usage:** Coordinator独占poll；Runner消费Settings和固定Task/Result。按T07设计给Service加锁内startAccepting/stopAccepting；Bean工厂先关闭首次受理，恢复及worker启动成功再开放，关闭先等在途受理完成再排空。重放始终先于门禁。
- **Readiness evidence:** T06已COMPLETED；`docs/verification/DATA-INTEGRITY-T06.md`记录后端1385、前端609、真实MySQL29、真实应用启动1项全部通过，0失败/错误/跳过。核心与装配独立最终审查无遗留问题，新文件已暂存。

四项输入一致：T03只读/预算→T04单元Evaluation→T05原子报告→T06固定队列输入。扫描错误与报告仓库错误已明确分层；恢复保留已保存读取时点。来源参考提议通过T07兼容hook传递，T08负责Tushare语义，无未解决依赖冲突。

## Start Here

1. 完整阅读`docs/task-designs/DATA-INTEGRITY-T07-design.md`。
2. 阅读看板T07详情和共享`docs/task-designs/DATA-INTEGRITY-design.md`第1、5–7节及测试要求。
3. 按上方依赖顺序阅读T03读取/授权/预算、T04 evaluator、T05存储/JSON、T06服务/队列/Settings实际实现。
4. 参考`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRunner.java`和`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadTaskConfiguration.java`的现有生命周期习惯，保持下载与检查独立。

**First action:** 在隔离区编写`IntegrityCheckRunnerIT`首个受控MySQL失败用例：用T06受理含多个计划单元的fixture任务，调用Runner，断言任务完成但确定缺失使overallFAIL、已提交进度和具体问题可从T05读回；观察缺少Runner导致RED，再按设计实施。收到显式启动请求后才将T07 READY→IN_PROGRESS。

## Risks

- 队列不持久化：重启中断旧QUEUED/RUNNING，包括提交未发布窗口，不自动执行或重算旧任务。
- 生命周期启动/关闭门禁与受理锁必须联动；仅检查应用ready状态不能消除并发窗口。
- 正常扫描故障保留为单元ERROR，报告持久化故障才任务FAILED；标记FAILED也写失败时不能宣称终态已保存。
- 取消是合作式预算/JDBC边界检查，不是任意第三方插件的强制线程隔离。
- `.worktrees/data-integrity`保留Studio/T01–T06混合暂存内容；不全量提交、不合并原工作区。T06未运行clean-main合同脚本，没有改动门禁。
