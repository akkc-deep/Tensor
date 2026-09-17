# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`。
- **Completed task:** `DATA-INTEGRITY-T05`，已记录 `COMPLETED`。
- **Next task:** `DATA-INTEGRITY-T06`，按既定 Order=6 选择。
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T06-design.md`，已完成并回填本板对应单元格。
- **Expected next status:** `READY`；本交接写入并链接后，按 `NOT_STARTED -> READY` 准备，未开始实施。

## Next Task

**DATA-INTEGRITY-T06 — 范围固定、幂等受理与队列准入。** 固定完整用户范围，以原请求幂等受理任务，在范围超限或队列满时明确拒绝。实现 IntegrityCheckService、完整能力快照、计划生成、队列名额预留与提交后发布，装配并校验 tensor.integrity 配置。来源日期校验采用向后兼容的本地能力 default 方法；工作线程、状态推进和重启中断由 T07 实施，HTTP 由 T09 实施。

可观察验收：省略 apiNames 固定当前全量，非法输入/未知字段拒绝；合法但本地完全无数据的股票仍受理，股票级/缺描述按股票计划，NON_STOCK 每接口一条 null symbol。相同 ID 和原请求重放旧任务且不入队，不同请求冲突，重放先于当前能力校验；首次请求固定全插件 hash/快照，并发仅一份完整任务。100股票/36600闭区间天/4000单元/20排队的默认边界相等允许、超出拒绝；队列满无新任务，写失败释放名额；配置全部正数且 workers=1，非法值阻止启动。实际 MySQL 的幂等、故障及并发准入与后端回归须有真实通过证据。

## Dependencies

### DATA-INTEGRITY-T02

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T02-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/IntegrityCheckSupport.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/integrity/TushareIntegrityPolicies.java`。
- **Decision:** 本地检查通过 findIntegrity 独立发现，缺 Token 不影响；股票由插件规范化，不按本地表过滤。40 项 Tushare 口径及依赖固定，36 项股票接口、4 项 NON_STOCK，快照与未知覆盖不得伪装 PASS。
- **Rationale:** 检查本地数据不需要下载凭据，整只未下载股票也是合法检查对象；日期轴和依赖只能由来源明确声明。
- **Constraint:** 保留下载 find/SINGLE/RANGE 合同，不调用来源客户端；完整能力 hash 包括按序全量定义、参考、来源规则与 core 规则，不包括 Token/运行时间。当前接口没有日期校验 hook，按 T06 设计添加兼容 default 方法，禁止在 core 中硬编码来源。
- **Usage:** 规范化原股票列表、构造并校验完整 ApiSnapshot、核对首次提交的 capabilityHash；已保存原请求重放必须先于这些当前元数据查询。
- **Readiness evidence:** 看板 T02 已完成；`docs/verification/DATA-INTEGRITY-T02.md` 记录7项注册合同、100项策略测试及1265项完整后端回归通过，0失败/错误/跳过。40项定义及无Token本地发现可直接消费；这些证据不代表 T06 已实现。

### DATA-INTEGRITY-T05

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T05-design.md`；`data-plane/tensor-app/src/main/resources/db/migration/V9__create_integrity_check_tables.sql`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckRepository.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckJson.java`；`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityCheckRepositoryIT.java`。
- **Decision:** create 原子保存 NewTask 与全部 NewUnit，submission_id 唯一；findBySubmissionId 返回原 requestHash 和历史内容。TaskScope 是固定选中范围，definitionSnapshot 可以包含整个插件，单元必须匹配其保存快照。仓库所有公开入口拒绝外部事务，create 返回表示自己的事务已经提交。
- **Rationale:** 不允许半份计划、并发重复任务或后来规则覆盖历史；全插件能力 hash 与用户选择的子范围承担不同职责。
- **Constraint:** 原请求 hash 对象键排序、数组顺序保留，不先规范化股票或补全 apiNames；每个选中股票级/缺描述 API 必须覆盖全部股票，NON_STOCK 恰好一条 null。DuplicateKeyException 原样传播，其他数据库失败为安全 PERSISTENCE_FAILED/QUERY_FAILED；不能一律把唯一冲突视作重放。既有 JSON 编码/能力 hash 样例保持兼容，不修改 V9 规避完整计划校验。
- **Usage:** 先 findBySubmissionId 重放；合法首次请求预留队列名额，再 create，返回后发布 checkId。唯一冲突释放预留并查赢家的原请求 hash；失败不留半份计划或名额。使用同一组 ApiSnapshot 同时计算 hash 和构造 NewTask/NewUnit。
- **Readiness evidence:** 看板 T05 已完成；`docs/verification/DATA-INTEGRITY-T05.md` 记录最终专项66项（真实MySQL40+JSON26）、完整后端单元1344项、分组应用MySQL73项和生产JAR4项通过。实际验证唯一约束、完整计划回滚、并发冲突、原子结果/问题及历史精确读回；独立最终复审无阻断问题。

两项输入一致：T02 提供完整能力，T05 可保存全量快照及选中范围；缺描述保留股票单元、NON_STOCK 只一条的规则相同，无未解决的依赖冲突。

## Start Here

1. 完整阅读 `docs/task-designs/DATA-INTEGRITY-T06-design.md`，以其接口、校验次序、失败规则和测试边界开始实施。
2. 阅读 `docs/task-designs/DATA-INTEGRITY-design.md` 第2、7、8节及看板 T06 详情。
3. 阅读上述 T02 本地能力和 T05 仓库/JSON 的实际接口及测试；参考 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java` 的固定排序。
4. 阅读 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadTaskConfiguration.java`、同目录 DownloadTaskProperties/ApplicationConfiguration 与 `docs/runbook/configuration.md`，复用已有装配习惯。

**First action:** 在 `.worktrees/data-integrity` 按已完成的 T06 设计编写 IntegrityCheckServiceTest：同 submissionId/原请求在当前插件变化或队列满时仍返回旧任务，并断言不查询能力、不创建、不再入队；观察失败后实现服务入口。显式启动请求到来后再将 T06 从 READY 转为 IN_PROGRESS，本交接不启动实现。

## Risks

- 内存队列只服务首版单实例；数据库提交到入队之间的崩溃窗口由 T07 启动中断处理覆盖，不能承诺自动恢复执行。
- T06 的 scan/时间预算仅绑定和校验，运行时执行由 T07 完成；缺描述 UNKNOWN 与 NON_STOCK N/A 的终态也不在受理阶段伪造。
- 隔离工作区分支为 feat/data-integrity，含既有混合暂存基线；保留这些改动，新文件加入 Git，不创建混合基线提交或回写原工作区。
- T05 验收记录保留了旧下载浏览器用例的一次 TASK_STATE_CONFLICT 时序竞争及独立复跑通过证据；没有修复下载协调器。既有 clean-main 合同脚本未在隔离分支运行，不把未运行门禁算作通过。
