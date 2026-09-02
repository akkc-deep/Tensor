# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M05-T05`
- **Next task:** `M06-T01`
- **Design document:** `docs/task-designs/M06-T01-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。

## Next Task

- **ID / title:** `M06-T01`——白名单 SQL 标识符和 Upsert 模板。
- **Goal:** 在 `tensor-core` 中只从已通过启动校验的 `DatasetDefinition` 派生表名、插入列、物理键和更新列，把所有标识符再次白名单校验并统一反引号引用，生成顺序确定且所有值位置都是 `?` 的 MySQL 8.4 `INSERT ... ON DUPLICATE KEY UPDATE` 模板，供后继 JDBC 仓储只绑定值而不再拼接标识符。
- **Scope:** 精确创建 `SqlIdentifierPolicy.java`、`UpsertSqlFactory.java` 和 `UpsertSqlFactoryTest.java`；实现固定正则与安全错误、业务列原序、FINGERPRINT 内部 `business_key`、三个来源字段、COMPOSITE/FINGERPRINT 物理键差异、TRD 10.3 `VALUES(column)` 更新表达式和严格 TDD。不修改 POM、plugin-api、既有 Java、YAML、Flyway SQL或其他模块，不实现绑定、预查、锁、计数、事务、仓储、查询、REST 或前端。
- **Acceptance criteria:** 两个无状态 final 类的公开表面与设计精确一致；所有标识符均满足 `^[a-z][a-z0-9_]{1,63}$` 并统一引用，非法输入不回显；insert 列精确为业务列、可选 `business_key`、三个来源字段且每列一个 `?`；COMPOSITE 只更新非键业务列，FINGERPRINT 更新全部定义业务列但不更新内部 `business_key`，两者都更新三个来源字段；`daily` 精确 SQL和两种键代表合同通过；取得缺两个生产类型的可归因 RED 后 6/6 GREEN，reactor `test`/`verify` 138/138、三层 Enforcer、静态/范围/格式/清理和精确三文件提交门禁全部通过。

## Dependencies

### `M02-T03`

- **Artifact:** `docs/task-designs/M02-T03-design.md`；实现提交 `551c18f` 与 code-point 修复 `0a74740` 中 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/` 的 `DatasetDefinition`、`ColumnDefinition`、`BusinessKeyDefinition`、`BusinessKeyMode`，以及 `TableName` 值对象。
- **Decision:** 数据集定义以不可变 record 保存表名、业务列原序和有序业务键；`BusinessKeyMode` 固定为 COMPOSITE/FINGERPRINT，标识符使用统一小写蛇形正则，默认 batch size 为 500。
- **Rationale:** SQL 模板必须由已发布、已校验、保序的公共元数据机械派生，不能接受裸字符串或复制第三套数据集结构。
- **Constraint:** 不修改、排序或规范化定义；不根据逻辑类型或字段名猜测列；表名、每个业务列与业务键引用都必须再次经过 SQL 标识符白名单；本任务不改变 plugin-api 或 batch size。
- **Usage:** `UpsertSqlFactory.create` 读取 `definition.tableName()`、`columns()`、`businessKey().mode()/fields()`，按原序构造 insert 和 update 列；`SqlIdentifierPolicy` 对每个名称统一引用。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；当前公共类型包含已记录的 code-point 修复，M02-T03 的聚焦、模块、Enforcer、范围与格式完成证据已记录，且 M05/M06 现有代码可编译消费这些类型。

### `M04-T06`

- **Artifact:** `docs/task-designs/M04-T06-design.md`；实现提交 `e78bd98` 中的永久 `FlywaySchemaContractIT`，以及 `data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql` 至 `V5__create_corporate_and_governance_tables.sql`。
- **Decision:** 49 张生产表物理列固定为 YAML 业务列原序、仅 FINGERPRINT 追加 `business_key`、最后追加 `source_plugin`/`source_api`/`ingested_at`；COMPOSITE 的定义字段是物理主键，FINGERPRINT 只以 `business_key` 为物理主键，原 identity fields 保持普通业务列。
- **Rationale:** SQL 模板的列顺序和物理键必须与已经在官方 MySQL 8.4.6 上通过 `information_schema` 验证的生产 schema 完全一致，避免运行时列错位或更新主键。
- **Constraint:** 不修改或读取历史迁移来动态生成 SQL；insert 必须保持“业务列→可选 `business_key`→三个来源字段”，update 必须排除物理键；所有值继续由后继 `PreparedStatement` 绑定。
- **Usage:** 工厂用该物理映射确定 FINGERPRINT 的内部 insert/主键列，以及两种模式的 update 列；测试以手工 `daily`、仅键 COMPOSITE 和 FINGERPRINT 定义验证映射，不启动数据库。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；提交 `e78bd98` 的固定 MySQL 8.4.6 契约已证明 49 表、851 业务列、1000 总列、49 PRIMARY、40 二级索引、两项 FINGERPRINT 技术键及三个来源字段，生产 JAR/资源隔离、reactor、Enforcer、范围与清理门禁均已记录通过。

两项直接输入没有冲突：M02-T03 冻结逻辑名称、顺序与键模式，M04-T06 冻结同一元数据的物理列、技术列和主键映射；TRD 10.3 在两者之上固定参数化 Upsert 形状。COMPOSITE 的 identity fields 是物理键并排除更新；FINGERPRINT 的内部 `business_key` 才是物理键，原 identity fields 因而属于可更新的非键业务列。

## Start Here

1. 完整读取 `docs/task-designs/M06-T01-design.md`。
2. 核对 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 Global Constraints、Task M06-T01 与 Module Gate。
3. 读取 `docs/task-designs/M02-T03-design.md` 及当前 `DatasetDefinition`、`ColumnDefinition`、`BusinessKeyDefinition`、`BusinessKeyMode`、`TableName`。
4. 读取 `docs/task-designs/M04-T06-design.md`，并只为核对固定物理映射读取 V1～V5 中 `daily`、`stk_managers`、`pledge_detail` 的列/主键片段；不得从 SQL 自举期望。
5. 读取 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 9.1/9.2 与 10.2/10.3，保持技术列、事务外模板生成和参数绑定边界。

首个实施动作：在允许 Mockito/Byte Buddy 本地 JVM attach 的环境运行 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test`，确认当前 plugin-api 79 项、core 53 项、合计 132/132 与三层 Enforcer 通过；随后只完整创建 `UpsertSqlFactoryTest.java`，在两个生产类型不存在时运行设计规定的聚焦命令，取得只因 `SqlIdentifierPolicy`/`UpsertSqlFactory` 缺失产生的可归因 `testCompile` RED。

## Risks

- TRD 10.3 固定的 `VALUES(column)` 在 MySQL 8.4.6 可用但已被标记弃用；本任务不得擅自引入别名方言，未来变更需同步精确 SQL 测试和后继仓储。
- 工厂只能重验标识符，不能连接数据库证明 schema；后继装配必须继续从 M05-T02 已验证 `DatasetCatalog` 取得定义。
- FINGERPRINT identity fields 不是物理主键，按非键业务列参与 update；正常路径中它们与摘要对应值一致，SHA-256 理论碰撞与键版本治理不在本任务范围。
