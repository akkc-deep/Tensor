# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M08-T01`
- **Next task:** `M08-T02`
- **Design document:** `docs/task-designs/M08-T02-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **Task ID:** `M08-T02`
- **Title:** 成功、空、上游失败、适配失败和写入失败模式
- **Goal:** 在 acceptance-only fixture 插件中交付 `SUCCESS`、`EMPTY`、`SOURCE_FAILURE`、`TYPE_FAILURE`、`PERSISTENCE_FAILURE` 五种确定性结果；插件从 M08-T01 的临时不可下载状态切换为通过无状态 `FixtureEnvelopeFactory` 可下载，同时保持真实通用适配路径和生产隔离。
- **Scope:** 创建 `FixtureScenario.java`、`FixtureEnvelopeFactory.java`、`FixtureEnvelopeFactoryTest.java`，并按已批准接缝只修改 `FixturePlugin.java`、`FixtureConfiguration.java`、`FixturePluginTest.java`。不修改 YAML/POM/ArchUnit、公共模块、Tushare/app、数据库、迁移、生产配置或前端，不实现 M08-T03 的故障注入与集成流。
- **Acceptance criteria:** 五值 enum、无状态工厂、精确 success/empty/source/type/persistence 结果、插件安全场景选择与可下载 readiness、双条件下仍只有插件/真实 adapter 两 Bean，以及依赖/JAR/生产隔离均符合完成设计；严格 RED 后聚焦 12/12、模块 166/166、完整 reactor `test`/`verify` 272/272、Enforcer、ArchUnit、依赖/JAR/静态/范围/格式/clean 和精确六文件提交门禁全部取得预期结果。

## Dependencies

### `M08-T01`

- **Artifact:** `docs/task-designs/M08-T01-design.md`；`data-plane/tensor-plugin-fixture/pom.xml`；当前 `FixturePlugin.java`、`FixtureConfiguration.java`、`fixture_daily.yaml`、`FixturePluginTest.java`；当前 `ModuleDependencyTest.java`；实现提交 `79cc80d` 与完成证据提交 `6ee1fbd`。
- **Decision:** fixture 只在 `acceptance` 与 `tensor.plugins.fixture.enabled=true` 同时成立时注册；配置持有唯一不可变 `fixture/fixture_daily` definition，发布一个插件和一个真实 `GenericDatasetAdapter`；插件/API/scenario/四列/复合键/filter/fixed-column 元数据已经冻结。M08-T01 以固定 `SOURCE_UNAVAILABLE` 暂时拒绝下载，专门为本任务接入场景工厂后切换为可下载。
- **Rationale:** fixture 必须复用生产相同的 SPI 与通用适配器来证明核心流程不依赖 Tushare；两阶段接缝让元数据/注册边界先稳定，再由本任务增加确定性包络而不混入数据库或集成故障注入。
- **Constraint:** 保持 definition、YAML、双条件、真实 adapter、`fixture -> core` 窄例外及 fixture 对 Tushare/app 的禁止边不变；只用设计批准的六文件，不复制参数校验/适配/持久化逻辑，不发布 factory Bean，不让 fixture 进入 app 生产 JAR。
- **Usage:** 新工厂使用既有固定身份、字段和参数构造五种结果；插件以新构造依赖选择场景并一次委托；配置在原插件 Bean 内直接构造工厂；更新后的测试继续保护 M08-T01 元数据、注册矩阵和通用适配路径。
- **Readiness evidence:** 权威看板为 `COMPLETED`。实现经过严格缺类型 RED、聚焦 6/6、模块 160/160；主控在允许 Mockito/Byte Buddy attach 的环境新鲜取得完整 reactor 266/266、0 failure/error/skipped，六层 Enforcer 与 ArchUnit 通过，依赖/JAR/静态/范围/格式/clean 门禁符合且工作树干净。任务级和最终独立审查均无任何级别发现。

- **Dependency comparison:** M08-T01 是唯一直接依赖；其固定 definition、双条件与适配器边界和 M08-T02 五场景职责互补。类型失败由既有 adapter 消费，写入失败标记留给后继 test-scope 注入，来源失败在包络前使用公共异常，三层职责无未解决冲突。

## Start Here

1. 完整读取 `docs/task-designs/M08-T02-design.md`，以其中六文件范围、公开接口、五种精确结果、场景选择顺序、12 项测试和 272/272 门禁为唯一实施合同。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M08-T02 行与任务详情，并确认本交接仍是其当前入口上下文。
3. 核对 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 Global Constraints、Task M08-T02 和 Module Gate。
4. 完整读取 `docs/task-designs/M08-T01-design.md`，核对当前 `FixturePlugin.java`、`FixtureConfiguration.java`、`fixture_daily.yaml`、`FixturePluginTest.java`、fixture POM 和 `ModuleDependencyTest.java`，保持已完成边界不漂移。
5. 核对公共 `DownloadEnvelope`、`SourceException`/`ErrorCode`、`ParameterValidator`/`ValidatedParameters` 与 `GenericDatasetAdapter` 当前接口；只消费，不修改或复制。
6. **First action:** 在允许 Mockito/Byte Buddy attach 的环境运行设计中的完整 reactor 基线并确认 266/266；随后先创建完整六项 `FixtureEnvelopeFactoryTest.java` 并更新 `FixturePluginTest.java`，不创建或修改生产类型，运行聚焦命令取得只因 `FixtureScenario`、`FixtureEnvelopeFactory` 和新插件接缝缺失的可归因 RED。

## Risks

- `PERSISTENCE_FAILURE` 的固定 note 是 M08-T03 test-scope 故障注入接缝，生产持久化代码不得读取或按该业务值分支。
- `TYPE_FAILURE` 必须保持包络结构合法且只破坏 amount，才能证明失败发生在真实通用 adapter；不得让工厂提前拒绝。
- 插件只分派经过 M05 参数准入的精确 enum 字符串；直接绕过编排的无效值必须用固定无回显异常拒绝，不能复制通用校验或默认逻辑。
- 完整 reactor 的既有 Mockito 测试需要允许 Byte Buddy JVM attach；受限环境错误不得通过删测、skip core 或扩大本任务范围规避。
