# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M06-T05`
- **Next task:** `M06-T06`
- **Design document:** `docs/task-designs/M06-T06-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **ID:** `M06-T06`
- **Title:** `DatasetQueryService`、页码归一化和精度序列化
- **Goal:** 在 `tensor-core` 中执行 M06-T05 的参数化查询 SQL，严格按 COUNT-first 计算和归一页码，返回完整有序列/行并保留 DECIMAL、BIGINT、DATE 和来源时间的精确 Java 类型，为 M09 的字符串序列化提供无损输入。
- **Scope:** 只创建 `DatasetPage.java`、`GenericQueryRepository.java`、`DatasetQueryService.java` 和 `DatasetQueryServiceIT.java`；实现深不可变页面、显式 JDBC 绑定/类型读取、空结果短路、超界页重建 SQL、COMPOSITE/FINGERPRINT 稳定分页和 152 业务列宽表读取。不得修改 M06-T05、POM、catalog/plugin-api、YAML/迁移、持久化代码或测试生命周期，不实现 REST DTO、HTTP 默认值、错误映射、JSON 字符串序列化、前端或快照事务。
- **Acceptance criteria:** 三个生产类型的唯一公开表面和页面不变量与设计一致；只查询已验证 catalog definition，COUNT-first 后空结果不执行 page SQL、超界请求只执行规范页 SQL；查询值和分页值全绑定，结果保持业务列原序加三个来源列且不暴露内部键；精确类型和 152 业务列不丢失；严格 RED/GREEN 后固定 MySQL 8.4.6 定向 8/8、两项 mutation、reactor 154/154、三层 Enforcer、静态/范围/格式/清理和精确四文件提交门禁全部通过。

## Dependencies

### `M06-T05`

- **Artifact:** `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QueryCriteria.java`、`QuerySql.java`、`QuerySqlFactory.java` 与测试 `QuerySqlFactoryTest.java`；实现提交 `263513d`。
- **Decision:** 查询输入固定为五个可选值加 page/pageSize；factory 只允许 definition 声明的三种 filter，生成参数化 COUNT/明确列分页 SQL、固定绑定顺序，并按 COMPOSITE 业务键或 FINGERPRINT 身份字段加内部 `business_key` 稳定升序。
- **Rationale:** 查询执行层必须消费同一已验证元数据和唯一 SQL 安全合同，不能再次拼装表/列/条件/排序或接受客户端标识符。
- **Constraint:** M06-T06 不修改、复制或放宽 M06-T05 生产合同；repository 只执行其 SQL/值，FINGERPRINT 不返回内部键；service 必须先 COUNT，再为空结果短路或以规范页重建 factory 输入，不能执行原超界页后修正响应。
- **Usage:** `DatasetQueryService` 使用 criteria/factory 生成请求和规范页 SQL；`GenericQueryRepository` 按 QuerySql 原序绑定并依据同一 DatasetDefinition 显式读取列和值；`DatasetPage` 承载最终列、行和规范 totals。
- **Readiness evidence:** 权威看板中 M06-T05 为 `COMPLETED`；提交态定向 8/8、两项 mutation、reactor `test`/`verify` 154/154、三层 Enforcer、静态/范围/清理和精确四文件门禁通过，任务审查 `Approved`、最终审查 `Ready to merge: Yes` 且三档问题均为 0。

唯一直接依赖无内部冲突；其 SQL 生成职责与本任务的 JDBC 执行、页面语义和类型保真职责边界清晰。M05 catalog/M04 schema 是设计中已建立的间接物理前提，不新增看板直接依赖。

## Start Here

1. 完整读取 `docs/task-designs/M06-T06-design.md`，以其中公开表面、类型映射、COUNT-first 顺序、固定八项 MySQL 测试、两项 mutation 和四文件范围为唯一实施合同。
2. 核对 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 Global Constraints、Task M06-T06 和 Module Gate。
3. 核对稳定路线图的 `DatasetQueryService`/`DatasetPage` 接口，以及 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 11.1～11.3、12.4。
4. 核对 M06-T05 三个生产类型、现有 `DatasetCatalog`/`DatasetStartupValidator`/`SchemaInspector`、`JdbcTemplate` repository 模式及现有 Testcontainers IT。
5. 首个实施动作：先运行设计 Tests 节的 reactor 基线并确认 plugin-api 79/core 75（154/154）；随后只完整创建 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/query/DatasetQueryServiceIT.java`，不创建三个生产类型，运行指定聚焦命令并确认只因三个类型缺失在 `testCompile` 非零。

## Risks

- COUNT 与 page 不在同一数据库快照；当前合同保证稳定唯一排序和 COUNT 时刻的超界归一，不承诺并发写入期间 totals/items 的快照一致。
- `ingested_at` 依赖连接继续遵守 UTC；repository 必须使用 UTC Calendar 读取 MySQL `DATETIME(3)`。
- `DatasetQueryServiceIT` 不进入默认 Surefire 扫描；显式固定 MySQL 8.4.6 定向 8/8 不能由 reactor 154/154 替代。
- 152 业务列测试验证完整有序读取；真实 `balancesheet` schema 与页面性能仍分别由已完成 M04 和后续 M14 验收。
