# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M06-T04`
- **Next task:** `M06-T05`
- **Design document:** `docs/task-designs/M06-T05-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **ID:** `M06-T05`
- **Title:** 查询条件白名单和 COUNT/分页 SQL
- **Goal:** 在 `tensor-core` 中以固定查询条件和已验证 `DatasetDefinition` 生成参数化 COUNT/分页 SQL，使无条件、白名单组合筛选、明确列和 COMPOSITE/FINGERPRINT 稳定分页可以由 M06-T06 安全执行。
- **Scope:** 只创建 `QueryCriteria.java`、`QuerySql.java`、`QuerySqlFactory.java` 和 `QuerySqlFactoryTest.java`；实现证券代码/日期/分页值校验、元数据 filter 白名单、COUNT/明确列分页模板、固定绑定顺序和两种业务键稳定排序。不得修改 POM、plugin-api、迁移/YAML、现有生产类型或测试生命周期，不访问数据库，不实现查询 service/page DTO、页码归一、REST/序列化或前端职责。
- **Acceptance criteria:** 三个生产类型的唯一公开表面和值不变量与设计一致；只允许元数据声明的 `ts_code`/`trade_date`/`ann_date`，无条件与单边/闭区间/AND 组合均参数化；分页只选择业务列与三个来源列，COMPOSITE 按业务键、FINGERPRINT 按身份字段加内部 `business_key` 稳定升序；严格 RED/GREEN 后定向 8/8、两项 mutation、reactor 154/154、三层 Enforcer、静态/范围/格式/清理和精确四文件提交门禁全部通过。

## Dependencies

### `M02-T03`

- **Artifact:** `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/` 下 `DatasetDefinition.java`、`ColumnDefinition.java`、`FilterDefinition.java`、`BusinessKeyDefinition.java` 与 `BusinessKeyMode.java`；实现提交 `551c18f`，Unicode 边界修复 `0a74740`。
- **Decision:** 数据集表名、业务列、筛选字段和业务键字段均在构造期校验并保序不可变；filter 只保存字段名，operator/control type 不进入 plugin-api。
- **Rationale:** 同一元数据必须驱动适配、持久化、查询列和筛选，查询层不应另建可漂移的表/列/键清单。
- **Constraint:** M06-T05 只读这些有序值，不修改、排序或复制标识符正则；不得把客户端 operator/列名写入公共元数据，也不得放宽表名、业务键或字段引用不变量。
- **Usage:** `QuerySqlFactory` 从 definition 取得安全表名、明确业务列、允许的 filters 与排序身份字段；`QueryCriteria` 只提供固定形状的值，不提供标识符。
- **Readiness evidence:** 权威看板中 M02-T03 为 `COMPLETED`；最终聚焦 9/9、模块 `test`/`verify` 54/54、Enforcer、范围/格式/清理和无发现范围化复审均已记录通过。

### `M04-T06`

- **Artifact:** `data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql` 至 `V5__create_corporate_and_governance_tables.sql`，以及提交 `e78bd98` 的 `FlywaySchemaContractIT.java` 结果级 schema 契约。
- **Decision:** 49 张生产表与 49 份元数据逐表一致；COMPOSITE 主键顺序等于业务键字段，FINGERPRINT 仅 `stk_managers`/`pledge_detail` 以内部 `business_key` 为唯一主键；每表业务列之后固定存在 `source_plugin`、`source_api`、`ingested_at`。
- **Rationale:** 查询 SQL 的明确列和稳定排序必须对应真实 MySQL 物理列/唯一键，不能仅靠字符串模板假定 schema。
- **Constraint:** M06-T05 不读取或修改历史迁移、数据库 metadata 或实际 schema；分页 SELECT 不暴露内部 `business_key`，但 FINGERPRINT ORDER BY 必须把它作为最后唯一决胜列，并保留三个来源列。
- **Usage:** `QuerySqlFactory` 按 M02 元数据生成业务列/COMPOSITE 排序，并按 M04 已验证物理合同追加来源列与 FINGERPRINT 内部排序列。
- **Readiness evidence:** 权威看板中 M04-T06 为 `COMPLETED`；固定 MySQL 8.4.6 的 52/52 验证了 49 YAML、50 表、1007 列、50 PRIMARY、40 二级索引和资源隔离，reactor 150/150、六层 Enforcer、JAR/范围/清理与无 Critical/Important 审查均已记录通过。

两项依赖无冲突：M02-T03 冻结运行时有序白名单与键语义，M04-T06 证明同一合同在 MySQL 中的业务列、物理唯一键和三个来源列真实存在；查询设计只机械组合两者，并由 TRD 11.2～11.3 固定值规范化、闭区间/AND、服务端分页、明确列、稳定排序和全参数绑定。

## Start Here

1. 完整读取 `docs/task-designs/M06-T05-design.md`，以其中公开表面、逐字 SQL、固定八项测试、两项 mutation 和四文件范围为唯一实施合同。
2. 核对 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 Global Constraints、Task M06-T05 和 Module Gate。
3. 核对 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 11.1～11.3、上述 M02-T03 公共元数据类型、M04-T06 schema 合同，以及现有 `SqlIdentifierPolicy.java`。
4. 首个实施动作：先运行设计 Tests 节的 reactor 基线并确认 plugin-api 79/core 67（146/146）；随后只完整创建 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/query/QuerySqlFactoryTest.java`，不创建三个生产类型，运行指定聚焦命令并确认只因这三个类型缺失在 `testCompile` 非零。

## Risks

- M06-T05 只生成请求页 SQL；M06-T06 必须先执行 COUNT，再用规范页重新生成或选择分页 SQL，总数为零时跳过行查询。
- FINGERPRINT 使用未选择的内部 `business_key` 做最后排序列；该行为适用于当前非 DISTINCT MySQL 查询，不授权未来直接叠加 DISTINCT/GROUP BY。
- 当前任务无数据库测试；SQL 物理前提来自已完成 M04-T06，M06-T06 将用固定 MySQL 集成测试覆盖实际执行与宽表读取。
