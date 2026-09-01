# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M05-T01`
- **Next task:** `M05-T02`
- **Design document:** `docs/task-designs/M05-T02-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **ID and title:** `M05-T02`，`DatasetCatalog` 和启动元数据/表结构校验。
- **Goal:** 在 `tensor-core` 中建立启动期数据集准入边界：接收已构造的不可变 `DatasetDefinition` 列表，以真实 JDBC metadata 检查表、原序列、JDBC 类型、可空性、主键和唯一键，只让身份唯一且定义与实际 schema 一致的数据集进入只读 `DatasetCatalog`；单个数据集损坏只隔离自身，metadata 整体失败则阻止启动。
- **Scope:** 恰好创建 `DatasetCatalog.java`、`SchemaInspector.java`、`DatasetStartupValidator.java` 和 `DatasetStartupValidatorTest.java`；不修改 POM、M02/M03/M04/M05-T01 或其他模块，不依赖 Tushare loader/YAML/Flyway，不装配 Spring Bean，不增加公开 diagnostics、刷新、持久化、查询、下载、REST 或 M05-T03～T05 行为。
- **Acceptance criteria:** 三个 `public final` 类及 nested records 的公开表面精确符合设计；inspector 生成不可变、有序 schema 快照并以固定安全异常传播 JDBC metadata 整体失败；validator 对 null、重复 key、定义关系、缺表、列顺序/type/nullability、主键、唯一键和无效键引用逐数据集隔离；catalog 只暴露验证通过且 key 唯一的定义并提供不可变、确定排序查询；严格 TDD 取得缺三类 RED 后 10/10 GREEN，模块 `test`/`verify` 99/99、三层 Enforcer、静态/范围/格式/清理和精确四文件提交门禁全部通过。

## Dependencies

### `M03-T09`

- **Artifact:** `docs/task-designs/M03-T09-design.md` 与 `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java`；实现提交 `36230d8`。
- **Decision:** 公开 loader 产生的 49 份 `DatasetDefinition` 已按独立 manifest/模板/PRD/TRD 基线冻结为 49 API、851 个原序业务列、参数描述符、47 个 COMPOSITE 键、2 个 FINGERPRINT 键、filters、`tushare_pro__<api>` 表名和默认 batchSize 500 的总契约。
- **Rationale:** 启动校验必须以稳定定义契约生成期望 schema，不能从实际数据库或被测 YAML 反向生成期望，否则元数据和物理 schema 的共同漂移会被隐藏。
- **Constraint:** Core 只消费装配边界提供的 `List<DatasetDefinition>`，不得依赖 Tushare loader、manifest、模板或 YAML；必须保留业务列原序、逻辑类型/nullability、业务键模式和字段顺序，并检查 display order、参数关联及键引用关系。
- **Usage:** `DatasetStartupValidator` 以每个 `DatasetDefinition` 构造期望列、技术列和主键，再与 `SchemaInspector` 返回的实际快照逐项比较；定义列表不由本任务加载。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；提交 `36230d8` 的定向契约测试 50/50、reactor `test`/`verify` 137/137、三层 Enforcer、JAR 排除、范围/格式/清理和独立审查均已记录通过。

### `M04-T06`

- **Artifact:** `docs/task-designs/M04-T06-design.md`、`data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_tables.sql` 至 `V5__create_corporate_and_governance_tables.sql`，以及 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`；总校验提交 `e78bd98`。
- **Decision:** 固定 MySQL 8.4.6 实证冻结 49 张生产表、851 个业务列、1000 个总列、49 个 PRIMARY、40 个非唯一二级索引、两个 FINGERPRINT `business_key`、三个统一来源字段，以及逻辑类型到 JDBC 类型族和 nullability 的映射；生产表没有主键之外的 UNIQUE 索引。
- **Rationale:** M05-T02 需要以真实 JDBC metadata 判断 M03 定义能否安全进入运行时目录，同时继续由 M04 的实际 MySQL 门禁负责长度、precision/scale、datetime precision、引擎、collation 和非唯一查询索引等更宽物理属性。
- **Constraint:** 启动校验只比较设计批准的列名/顺序、JDBC 类型族、nullability、主键和 UNIQUE 键；不得执行 Flyway、修改或修复 schema，也不得把测试专用 V6 fixture 表纳入生产目录输入或要求运行时重复 M04 的全部物理门禁。
- **Usage:** `SchemaInspector` 读取当前 connection catalog 中目标生产表的 columns、primary keys 和 unique indexes；validator 以 M04 已证明的 JDBC 映射比较实际快照并排除发生漂移的数据集。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；提交 `e78bd98` 已在固定 MySQL 8.4.6 上记录 52/52 显式 schema 契约通过，首次 V1～V6 六项迁移、validate、二次零项迁移、49 生产表/1000 生产列/49 PRIMARY/40 二级索引及生产/test 资源隔离均通过，非定向 reactor `test`/`verify` 150/150、六层 Enforcer、依赖/JAR/范围/清理和审查门禁也已通过。

两项直接依赖的决策与约束一致：M03-T09 提供独立且有序的期望定义，M04-T06 证明同一类型/nullability、技术列和业务键规则生成的实际 MySQL schema；M05-T02 只做启动时交叉验证，不读取具体插件实现、不从数据库生成期望，也不重复或削弱 M04 的物理 schema 门禁。未发现冲突。

## Start Here

1. 完整读取 `docs/task-designs/M05-T02-design.md`，以其中冻结的公共表面、JDBC 快照规则、局部/整体失败边界、10 项测试和精确四文件范围作为唯一实施合同。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M05-T02 行与详情。
3. 核对 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 Global Constraints、Task M05-T02 与 Module Gate。
4. 读取 `docs/task-designs/M03-T09-design.md`、`docs/task-designs/M04-T06-design.md` 及上述直接依赖 artifact，确认定义与物理 schema 映射边界。
5. 读取 `data-plane/tensor-core/pom.xml` 和 M02 的 `DatasetDefinition`、`DatasetKey`、`PluginId`、`TableName` 及其直接引用类型，确认现有依赖满足任务且不修改 POM。

首个实施动作：先运行设计 Tests 节的基线命令 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test`，确认现有 89/89；随后完整创建 `DatasetStartupValidatorTest.java`、保持三个生产类不存在，并运行设计中的聚焦命令，取得只因 `DatasetCatalog`、`SchemaInspector`、`DatasetStartupValidator` 及其 nested snapshot 类型缺失而产生的可归因 `testCompile` RED。

## Risks

- JDBC metadata 的 catalog/schema 语义依赖 MySQL Connector/J；inspector 固定使用当前 connection catalog 和 null schema pattern，切换数据库或驱动不在本任务范围。
- 数据库权限、连接或 metadata 整体失败必须阻止启动，不能静默变成 49 个局部排除。
- 运行时快照不重复校验字符长度、数值/时间精度、引擎、collation 和非唯一查询索引；这些属性继续由 M04-T06 的固定 MySQL 8.4.6 门禁保证。
- 按批准合同不提供公开 diagnostics API；固定安全日志不能让调用者结构化区分缺表和各类漂移，未来健康/API 若需要原因必须另行设计。
