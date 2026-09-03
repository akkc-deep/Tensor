# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M07-T04`
- **Next task:** `M08-T01`
- **Design document:** `docs/task-designs/M08-T01-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **ID/title:** `M08-T01` — fixture 元数据、插件和适配器。
- **Goal:** 仅在 Spring profile `acceptance` 与 `tensor.plugins.fixture.enabled=true` 同时成立时，注册描述 `fixture/fixture_daily` 的 `FixturePlugin` 和一个直接复用 `GenericDatasetAdapter` 的 `DatasetAdapter`；默认及 production profile 保持无 fixture Bean。
- **Scope:** 创建 `FixturePlugin.java`、`FixtureConfiguration.java`、`fixture_daily.yaml` 和 `FixturePluginTest.java`；按项目所有者批准修改 fixture POM 增加 `tensor-core`/`spring-boot-autoconfigure` compile 依赖，并修改 `ModuleDependencyTest.java` 仅允许 `fixture -> core`。不实现 M08-T02 五场景、M08-T03 集成流、YAML loader、数据库、网络、生产配置、合同或前端。
- **Acceptance criteria:** 双条件注册矩阵、精确插件/API/scenario/数据集元数据、Java/YAML 一致、M04 fixture 表一致、真实 `GenericDatasetAdapter` 适配、M08-T01 临时安全下载拒绝及收窄后的模块/JAR 隔离均可观察；严格 RED→6/6 GREEN、模块 160/160、完整 reactor `test`/`verify` 266/266、六层 Enforcer、ArchUnit、依赖/JAR/静态/范围/格式/clean 门禁全部通过；固定实现提交只含两个修改文件和四个 Git 跟踪的新文件。

## Dependencies

### `M02-T05`

- **Artifact:** `docs/task-designs/M02-T05-design.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`、`DatasetAdapter.java` 及 `error/` 下的领域错误类型。
- **Decision:** 插件只实现 `descriptor()`、`readiness()`、`download(ApiName,Map<String,Object>)`；适配器使用既有 `DatasetAdapter`；已知 API 的临时失败使用 `SourceException(ErrorCode.SOURCE_UNAVAILABLE, "Fixture scenarios are not configured")`。
- **Rationale:** 所有数据源共享同一最小 SPI、错误码/retryable 矩阵和安全消息边界，避免 fixture 专用接口或裸异常。
- **Constraint:** 不修改 plugin-api，不增加 cause/原始响应/Token/诊断字段，不从下载 SPI 执行适配或持久化。
- **Usage:** `FixturePlugin` 实现 `DataSourcePlugin`；配置发布一个 `DatasetAdapter`；临时下载拒绝复用公开来源异常。
- **Readiness evidence:** 权威看板为 `COMPLETED`；最终实现提交 `445b941` 与修复提交 `dd495ee` 已通过聚焦 8/8、模块 79/79、`jdeps java.base`、Enforcer、静态/范围/clean 和无发现复审。当前 2026-09-03 完整基线再次验证 plugin-api 79/79。

### `M04-T06`

- **Artifact:** `docs/task-designs/M04-T06-design.md`；`data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`。
- **Decision:** `fixture__fixture_daily` 的业务列依次为 `ts_code VARCHAR(64) NOT NULL`、`trade_date DATE NOT NULL`、`amount DECIMAL(38,18) NOT NULL`、`note VARCHAR(255) NULL`，主键为 `(ts_code, trade_date)`，唯一查询 filter 为 `ts_code`。
- **Rationale:** fixture 元数据必须与已经在固定 MySQL 8.4.6 上验证的测试专用物理表一致，才能由后续公共持久化/查询流程消费。
- **Constraint:** V6 只在 app test resources；本任务不得移动/修改 V6、增加生产迁移或让 fixture 进入 app 生产 JAR。
- **Usage:** Java definition 与 YAML 逐项复用四列、类型、可空性、表名、COMPOSITE 键、filter 和 fixed column。
- **Readiness evidence:** 权威看板为 `COMPLETED`；提交 `e78bd98` 已在官方 MySQL 8.4.6 上通过 52/52 定向 schema 调用、150/150 reactor、六层 Enforcer、生产 JAR 隔离和无 Critical/Important 的审查。

### `M05-T05`

- **Artifact:** `docs/task-designs/M05-T05-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`、`ValueConverter.java`、`FingerprintKeyCodec.java`。
- **Decision:** fixture 适配必须直接实例化 `GenericDatasetAdapter(DatasetDefinition,ValueConverter,FingerprintKeyCodec)`；成功包络由该公共路径完成身份/字段校验、值转换、业务键和稳定批次构造。
- **Rationale:** M08 的目的在于验证核心流程不依赖 Tushare；复制 fixture 适配器会建立第二套转换、键和失败语义。
- **Constraint:** 不修改 core，不复制适配、转换、指纹或去重逻辑；项目所有者批准的 `fixture -> core` 仅通过 POM 与 ArchUnit 窄放开，fixture 仍不得依赖 Tushare/app。
- **Usage:** `FixtureConfiguration.fixtureDatasetAdapter()` 以唯一 fixture definition 和三个 public final core 类型创建一个 `DatasetAdapter` Bean；单元测试用一行真实成功包络证明适配路径。
- **Readiness evidence:** 权威看板为 `COMPLETED`；实现提交 `d7ec551` 与修复提交 `8ca49d0` 已通过聚焦 11/11、当时 reactor 132/132、Enforcer、静态/范围/clean 和最终无剩余发现审查。当前 2026-09-03 完整基线再次验证包含该实现的 core 75/75。

三项输入无未解决冲突：M02 固定 SPI/错误边界，M04 固定 fixture 数据形状与物理表，M05 提供元数据驱动适配实现。旧模块图禁止 fixture 依赖 core 的冲突已由项目所有者明确裁决为仅放开该单向边；现有 Tushare loader/schema 的 plugin ID 硬编码不作为输入，因而不会引入 fixture→Tushare。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M08-T01-design.md`；
2. `docs/superpowers/plans/2026-09-03-m08-t01-fixture-plugin.md`；
3. `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 M08 Global Constraints、M08-T01 与 Module Gate；
4. `data-plane/tensor-plugin-fixture/pom.xml` 与 `data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java`；
5. 上述 M02-T05、M04-T06、M05-T05 设计与精确消费文件。

具体首个实施动作：确认工作树干净，然后不改文件运行 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-plugin-fixture,tensor-app -am verify`，必须取得 plugin-api 79、core 75、Tushare 93、fixture 0、app 13，共 260/260 基线；随后严格按实施计划先创建完整测试/YAML和两项批准的修改，取得只因两个生产类缺失的 `testCompile` RED。

## Risks

- `fixture -> core` 是窄例外；任何 fixture→Tushare/app、core→fixture 或生产 JAR 包含 fixture 都必须失败。
- fixture YAML 只作为固定验收契约，不经硬编码 Tushare 的现有 schema/loader 加载；未来多数据集/运行时加载需要单独公共元数据设计。
- M08-T01 的注册态有意不可下载；M08-T02 必须按已批准范围同时修改插件、配置和测试接入场景工厂，不能在本任务偷跑场景逻辑。
- 完整 reactor 的既有 Mockito 测试需要 Byte Buddy JVM attach；受限环境的单一 attach 失败必须原命令移至允许环境重跑，不能删测或 skip core。
