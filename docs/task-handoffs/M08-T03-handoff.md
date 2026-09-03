# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M08-T02`
- **Next task:** `M08-T03`
- **Design document:** `docs/task-designs/M08-T03-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **ID:** `M08-T03`
- **Title:** fixture 注册→适配→入库→查询集成测试
- **Goal:** 交付“fixture 注册→适配→入库→查询集成测试”。
- **Scope:** 只创建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`，使用生产注册、适配、目录校验、持久化和查询公共表面覆盖 success、empty、type failure、persistence rollback 与 production absence，并执行该任务的直接测试和验证；不修改 POM、生产源码、配置、YAML、迁移、已有测试或其他预定义任务交付物。
- **Acceptance criteria:** 固定 MySQL 8.4.6 上，fixture 从 acceptance context 注册后经真实生产路径得到精确 typed row；empty 零事务/零连接，type failure 零写入，test-scope marker 故障在真实 batch 执行后使事务回滚，production context 不注册 fixture；设计规定的 RED、5/5、57/57、272/272、Enforcer、ArchUnit、隔离、范围、格式、Git 跟踪和 clean 门禁全部得到预期结果，唯一实现提交精确包含一个新 IT。

## Dependencies

### `M05-T01`

- **Artifact:** `docs/task-designs/M05-T01-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java` 与 `AdapterRegistry.java`，源自实现提交 `7ea252c`。
- **Decision:** 注册表在构造期形成不可变视图；只有 ID 唯一且当前可下载的插件、key 唯一的 adapter 可由 `find` 获取，重复或局部损坏输入全部隔离。
- **Rationale:** fixture 必须走与其他插件相同的扩展发现边界，才能证明核心流程不依赖具体插件实现，也不能由测试直接持有 Bean 绕过 readiness/唯一性语义。
- **Constraint:** M08-T03 只能把 acceptance context 实际 Bean 交给两个生产注册表并从 `find` 取回插件/adapter；不得调用下载、定义或适配来替代注册表构造，也不得新增测试专用注册实现。
- **Usage:** `FixtureFlowIT` 从 context 收集唯一 `DataSourcePlugin` 和 `DatasetAdapter`，构造两个 registry，随后分别按 `PluginId(fixture)` 与 `DatasetKey(fixture, fixture_daily)` 查找流程入口。
- **Readiness evidence:** 权威看板将 M05-T01 标记为 `COMPLETED`；实现和测试增强提交 `7ea252c`、`ca39a34` 已通过聚焦 10/10、模块 test/verify 89/89、Enforcer、范围及独立审查，当前消费文件相对实现提交无后续行为修改。

### `M05-T05`

- **Artifact:** `docs/task-designs/M05-T05-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`，源自实现提交 `d7ec551`。
- **Decision:** 唯一通用 adapter 只消费身份和字段精确匹配的成功包络，按定义顺序产生不可变 typed batch；空包络产生合法零行 batch，任一类型错误拒绝整个调用且不产生部分 batch。
- **Rationale:** fixture 的成功、空和类型失败必须验证元数据驱动的真实核心适配边界，不能由 fixture 专用转换代码复制或弱化类型合同。
- **Constraint:** IT 必须从 `AdapterRegistry` 获得 context 发布的真实 `GenericDatasetAdapter`，为每次适配传入显式固定 `Instant`；TYPE_FAILURE 必须止于精确 `ADAPTER_TYPE_INVALID`，不得调用 persistence。
- **Usage:** SUCCESS 和 PERSISTENCE_FAILURE 转为可持久化 batch，EMPTY 转为零行 batch，TYPE_FAILURE 证明 amount 的真实 DECIMAL 转换失败。
- **Readiness evidence:** 权威看板将 M05-T05 标记为 `COMPLETED`；实现提交 `d7ec551` 与合同修复 `8ca49d0` 已通过严格 TDD、reactor test/verify 132/132、Enforcer、范围与审查门禁。

### `M06-T04`

- **Artifact:** `docs/task-designs/M06-T04-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java` 与 `GenericUpsertRepository.java`，源自实现提交 `da56663`。
- **Decision:** 合法空 batch 在锁和事务/JDBC 前返回 `(0,0)`；非空 batch 在单一 Spring `REQUIRED` 事务中完成已有键预查和参数化 batch Upsert，任何 SQL 失败回滚全部写入并在事务完成后释放锁。
- **Rationale:** M08-T03 要证明的是生产事务编排的真实原子性和空结果短路，而不是 trigger、自动提交或测试自行管理事务得到的表面结果。
- **Constraint:** `JdbcTemplate`、`ExistingKeyRepository`、`GenericUpsertRepository` 与 `DataSourceTransactionManager` 必须共享同一个包装 DataSource；故障注入只存在于 IT，并且只能在真实 `executeBatch()` 成功后抛 `SQLException`，不得修改 production service/repository。
- **Usage:** SUCCESS 产生 insert count，EMPTY 证明零连接，PERSISTENCE_FAILURE 先真实更新种子行再由代理抛错，随后以生产查询证明事务恢复原值。
- **Readiness evidence:** 权威看板将 M06-T04 标记为 `COMPLETED`；实现提交 `da56663` 已通过固定 MySQL 8.4.6 定向 8/8、reactor test/verify 146/146、两项事务/批大小 mutation、Enforcer、范围及无发现审查。

### `M06-T06`

- **Artifact:** `docs/task-designs/M06-T06-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/DatasetQueryService.java`、`GenericQueryRepository.java`、`DatasetPage.java` 与 `QueryCriteria.java`，源自实现提交 `9c3fa44`。
- **Decision:** query service 只查询已验证目录中的 definition，严格 COUNT-first，并按元数据列序返回深不可变页面；DECIMAL、DATE 和来源时间分别保持 `BigDecimal`、`LocalDate`、`Instant`。
- **Rationale:** 入库后的成功和回滚结果必须通过生产只读边界观察，才能同时验证物理数据、来源元数据、排序与 JDBC 类型读取，而不是用原始 SQL 绕过查询合同。
- **Constraint:** 目录必须由 `DatasetStartupValidator(...).validate()` 公开入口基于真实 V6 schema 创建；fixture 只声明 `ts_code` filter，因此 IT 的 `QueryCriteria` 不得设置 trade/ann date 条件，page/pageSize 固定为 1/20。
- **Usage:** SUCCESS 查询精确七列 typed row；PERSISTENCE_FAILURE 后查询同一业务键，确认 note、amount 和首次 `ingested_at` 均未被失败批次更新。
- **Readiness evidence:** 权威看板将 M06-T06 标记为 `COMPLETED`；实现提交 `9c3fa44` 已通过固定 MySQL 8.4.6 定向 8/8、两项页码/精度 mutation、reactor test/verify 154/154、Enforcer、范围和独立审查。

### `M08-T02`

- **Artifact:** `docs/task-designs/M08-T02-design.md`；当前 `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`、`FixtureConfiguration.java`、`FixtureScenario.java` 与 `FixtureEnvelopeFactory.java`，源自实现提交 `885313d`，测试合同由 `54c2b30` 加固。
- **Decision:** `acceptance + tensor.plugins.fixture.enabled=true` 是唯一 fixture 注册入口；SUCCESS、EMPTY、TYPE_FAILURE 和 PERSISTENCE_FAILURE 都由插件确定性产生，其中 persistence 场景是可正常适配的行且 note 精确为 `PERSISTENCE_FAILURE`，只供 M08-T03 test-scope 故障注入消费。
- **Rationale:** 固定包络和 marker 让全流程在无网络、无凭证、无随机性的前提下复现成功与分层失败，同时保持生产核心没有 fixture 判断。
- **Constraint:** IT 必须通过 registry 查找出的插件调用 `download`，不得直接调用 envelope factory；marker 只能由 `FixtureFlowIT` 嵌套 DataSource 识别，production profile 即使属性为 true 也不得注册 fixture；SOURCE_FAILURE 已完成且不在本任务重复。
- **Usage:** 四个数据库流程测试分别传入场景名称；独立 production context 测试双条件隔离，JAR 门禁继续证明 app 生产产物不含 fixture 类、YAML 或 V6。
- **Readiness evidence:** 权威看板将 M08-T02 标记为 `COMPLETED`；实现/测试提交 `885313d`、`54c2b30` 及完成提交 `1eee27c` 已通过最终 reactor verify 272/272、Enforcer、ArchUnit、依赖/JAR/生产能力/范围/格式/clean 门禁和独立复审。

五项直接依赖的决定和约束没有冲突：M08-T02 提供只在 acceptance 生效的确定输入，M05 注册表和 adapter 将其变成公共 typed batch，M06 persistence/query 在同一已验证 schema 上提供真实事务写入与类型保真观察；故障 marker 仅由 IT 代理在 JDBC batch 后消费，不侵入任何生产层。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M08-T03-design.md`
2. `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T03`、Global Constraints 与 Module Gate
3. `docs/task-designs/M08-T02-design.md`
4. `docs/task-designs/M05-T01-design.md`
5. `docs/task-designs/M05-T05-design.md`
6. `docs/task-designs/M06-T04-design.md`
7. `docs/task-designs/M06-T06-design.md`
8. `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`
9. `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`
10. 设计 `Approach` 中列出的当前 fixture、registry、adapter、catalog、persistence 和 query 生产类型

首个实施动作：在 Docker 与 JVM attach 可用的环境执行设计 `Tests` 中的默认 module gate 和现有 `FlywaySchemaContractIT` 基线，分别确认 272/272 与 52/52；随后才创建完整 `FixtureFlowIT.java` 测试骨架并取得只因 `Fixture flow not wired` 的严格 RED。

## Risks

- test-scope dynamic proxy 依赖当前 production repository 调用 JDBC `executeBatch()`；路径变化会使故障注入不再触发并让 IT 失败，需要在后续任务重新设计证据，不能把 marker 判断移入生产代码。
- MySQL 容器测试和既有 Mockito/Byte Buddy 测试分别需要 Docker 与允许 attach 的执行环境；环境失败不能通过跳过 IT、删测或放宽门禁规避。
- `DATETIME(3)` 只保留毫秒且查询按 UTC 读取；测试时间必须保持毫秒精度，不能改为任意纳秒或本地时区值。
- `FixtureFlowIT` 的 `IT` 命名不进入默认 Surefire 扫描；显式 5/5 和联跑 57/57 是不可由默认 reactor 272/272 替代的结果证据。
