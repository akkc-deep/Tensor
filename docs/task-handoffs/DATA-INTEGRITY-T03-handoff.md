# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`。
- **Completed task:** `DATA-INTEGRITY-T02`（COMPLETED）。
- **Next task:** `DATA-INTEGRITY-T03`（按 Order=3 选择）。
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T03-design.md`。
- **Expected next status:** `READY`；本交接写入后从 `NOT_STARTED` 转为 `READY`，尚未启动实施。

## Next Task

`DATA-INTEGRITY-T03`：一致快照与受限只读扫描。

实现同一检查单元的 MySQL REPEATABLE_READ 只读快照、目标与已声明参考授权、完整物理键游标、精确绑定/读取、空日期分支和累计预算。接口、资源所有权、参考 permit、失败规则及具体测试已经在专属设计中确定。

验收必须实际连接 MySQL：目标跨页/参考表在并发写入下保持同快照，无重漏；非法表列、跨股票和越范围被拒绝；DATE/LONG/DECIMAL 无损；空日期明确标记范围未确定，所有读取累计计费，超限或中断不能产生全量结果；检查不写证券表。T03 不实现集合比较、来源候选规则、任务/报告或页面。

## Dependencies

### DATA-INTEGRITY-T01

- **Artifact:** `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/integrity/IntegrityReadRequest.java`、`IntegrityScope.java`、`IntegrityDescriptor.java`、`IntegrityDependency.java`、`IntegrityContext.java`、`IntegrityContracts.java`；`docs/task-designs/DATA-INTEGRITY-T01-design.md`。
- **Decision:** 来源仅提交不可变读取请求；字段/依赖由 descriptor 声明，表及类型来自 DatasetDefinition。scope 保留用户原股票和闭区间，nullDates 与日期范围互斥；读取受核心授权、快照和累计预算约束。
- **Rationale:** 来源规则不获得 SQL/写权限，目标与参考按同一时点读取，保证后续集合比较的依据一致。
- **Constraint:** 不变更 T01 的插件公开合同；plugin-api 不依赖 JDBC/core；LocalDate、Long、BigDecimal 和 null 原样保留，不引入浮点/截断。空日期不能猜测所属窗口，参考扩展不改变原目标范围。
- **Usage:** 实现专属设计的 withSnapshot/ReadSession.scan/scanTarget、IntegrityReadPlan 和预算；T04 再将 scan 委托到该会话并实现 compare。消费 catalog 和已声明依赖建立只读 SQL，不从 queryMode 猜日期轴。
- **Readiness evidence:** `docs/verification/DATA-INTEGRITY-T01.md`：17 项合同测试及当时 1158 项后端测试通过；本次 `docs/verification/DATA-INTEGRITY-T02.md` 的完整后端回归 1265 项通过、0 失败/错误/跳过，其中 T01 的 17 项合同测试仍通过。此证据仅证明现有合同可用，不证明 T03 已实现。

现有工程输入：`DatasetCatalog` 提供已校验定义；`SqlIdentifierPolicy` 引用标识符；`JdbcValueBinder` 精确绑定；`ExistingKeyRepository` 的 COMPOSITE 全字段/FINGERPRINT business_key 物理键选择是复用依据。`GenericQueryRepository` 仅供读取类型实现参考，不能复用其 OFFSET 分页充当全量扫描。上述约束无已知冲突。

## Start Here

1. `docs/task-designs/DATA-INTEGRITY-T03-design.md`（完整实施设计）。
2. `docs/task-handoffs/data-integrity-task-board.md` T03；`docs/task-designs/DATA-INTEGRITY-design.md` 第 1、3、4、7 节。
3. T01 上述合同及 `docs/task-designs/DATA-INTEGRITY-T01-design.md`。
4. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java`、`persistence/SqlIdentifierPolicy.java`、`persistence/JdbcValueBinder.java`、`persistence/ExistingKeyRepository.java`、`query/GenericQueryRepository.java`。
5. `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRepositoryIT.java`（既有 MySQL 8.4.6 Testcontainers 模式）。

第一步：在 `IntegrityReadRepositoryIT` 编写 batchSize=2、五行复合物理键的失败测试，在首批消费时由另一连接提交目标与参考更改，断言同 session 后续页和首次参考扫描仍看到旧快照；再实现设计固定的独占只读连接和游标入口。

## Risks

本隔离工作区 `.worktrees/data-integrity` / `feat/data-integrity` 有既有 Studio/T01/T02 暂存基线，必须保留；尚未合回原工作区。MySQL IT 需 Docker 和本机连接权限，不得 skip 后宣称验收；显式 `-Dtest=IntegrityReadRepositoryTest,IntegrityReadRepositoryIT` 才会运行新增 IT。完整后端回归沿用已有 Mockito javaagent 和 WireMock 本机端口权限。T03 当前只有设计与交接，没有实现或 MySQL 通过证据；可信核心参考 permit 的后续装配必须保持设计规定的股票、用途与窗口边界。
