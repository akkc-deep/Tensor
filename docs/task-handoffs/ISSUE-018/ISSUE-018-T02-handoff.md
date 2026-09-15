# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T01`，看板已记录 `COMPLETED`。
- **Next task:** `ISSUE-018-T02`，按预定义 Order 选中的直接后继。
- **Design document:** `docs/task-designs/ISSUE-018-T02-design.md`，已完整编写、复核并回填看板。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，尚未启动实现。

## Next Task

`ISSUE-018-T02`：任务与批次模型、迁移和仓储。

目标是让任务、计划、批次树和规范化快照持久化，并通过受约束 JDBC 操作提供可靠状态和计数。范围为 V8 两张表、领域记录 / 状态、DownloadTaskRepository、受限 JSON / 摘要、core Jackson databind 依赖，以及迁移和包结构断言；接收用例、证券事务参与和执行器分别由 T03 / T04 / T07 实现。

验收要求：MySQL 8.4.6 新建、从 V7 升级和重复迁移通过；唯一键保证提交幂等输入；计划与 plan_ready、SPLIT 父与两子分别同事务，失败无半份数据；条件更新隔离旧许可；叶子统计排除 SPLIT，成功计数保留；快照限量、摘要稳定且无凭证。V1–V7 不修改，生产 51 表 / 7 迁移、测试 52 表 / 8 迁移、40 项证券数据集保持不变。详细字段、方法、失败规则和测试见专属设计。

## Dependencies

### ISSUE-018-T01

- **Artifact:** `docs/task-designs/ISSUE-018-T01-design.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/`；同模块 `BatchDownloadSupport.java`、`error/ErrorCode.java`；`docs/contracts/error-codes.md`。
- **Decision:** DateRange 是包含两端的 LocalDate 闭区间；DownloadMode 为 SINGLE / RANGE。BatchDownloadDescriptor 保存独立区间语义、起止参数名、有序参数列表和策略版本，枚举与 CompletenessRule 是其公共嵌套类型。UNKNOWN 不能构造 AVAILABLE；普通插件继续支持原 DataSourcePlugin.download。
- **Rationale:** 核心存储需要明确表达业务范围、执行模式和完整性依据，同时支持不使用 Tushare 字段名的来源；已有单次插件不承担新接口实现义务。
- **Constraint:** core 不导入 app 或 Tushare；日期参数的数组顺序有方向含义，快照和定义摘要必须保留。不能将 UNKNOWN 序列化成无限制 / COMPLETE，不能写入来源凭证。BatchCallContext 的 beforeRequest 约定先检查和预约再节流 / 请求，预算实现由后续任务消费仓储原语。ErrorCode.retryable 不能替代任务状态判断。
- **Usage:** 领域记录复用 DateRange / DownloadMode；DownloadTaskJson 显式编码描述、参数和策略，不持久化任意 Java 类型；仓储提供带启动 ID 和轮次检查的请求预约、状态更新及任务查询。范围端点从保存的策略参数名读取。
- **Readiness evidence:** T01 看板 Completion / Verification evidence 已记录合同与四形状绑定验收。2026-09-11 完整 Maven 单元回归 637 项无失败 / 错误 / 跳过，其中 plugin-api 87 项；同一生命周期前端 170 项及生产构建成功。独立只读代码审查通过。此证据证明可消费合同，不代表 V8 或仓储已经实现。

直接依赖的类型、参数方向、错误分类与 T02 设计一致，无未解决输入冲突。

## Start Here

按顺序阅读：

1. `docs/task-designs/ISSUE-018-T02-design.md`（全文）。
2. `docs/task-designs/ISSUE-018-design.md` §3.6–3.11、§3.13–3.14、§4、§5.1。
3. T01 专属设计及上述 plugin-api 合同源码。
4. `data-plane/tensor-app/src/main/resources/db/migration/`、`data-plane/tensor-core/pom.xml`、现有 JdbcTemplate / TransactionTemplate 持久化实现。
5. `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`、两个 PackagedJarContract 测试，以及 core `persistence/PersistenceServiceIT.java` 的真实 MySQL 测试装配。

第一个实施动作：按 T02 设计新增 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskJsonTest.java`，先写规范化请求摘要与 8 KiB 边界的失败用例，再加入 BOM 管理的 Jackson databind 依赖和最小 JSON 实现；随后按专属设计实现 V8 / 仓储及真实 MySQL 故障测试。以用户下一次明确启动请求记录 `READY -> IN_PROGRESS`。

## Risks

- 高层仓储操作必须拥有事务并在提交后返回；设计明确拒绝外层事务，只有 T04 三个低层方法参与已有事务，避免破坏证券数据共同提交。
- MySQL JSON 返回文本可能增加空格；读入先使用 128 KiB 解析保护，再按规范化字节检查 8 / 16 KiB 业务上限，必须有真实数据库边界回读证据。
- worker 是否已退出、当前能力和队列容量由后续应用服务判断；仓储行状态不构成进程存活证据。截止后仍须能记录失败和终态。
- T02 必须实际执行 MySQL IT，不能用当前 T01 单元回归代替；生产包和验收包各自保留准确迁移清单。
