# M08-T03 fixture 注册→适配→入库→查询集成测试——任务设计

任务编号：`M08-T03`
对应任务：[M08-T03](../superpowers/plans/tensor-modules/M08-fixture-plugin.md#task-m08-t03-fixture-全流程集成20h)
实施产物：`tensor-app` 中一个固定 MySQL 8.4.6 的五场景全流程集成测试

## Goal

在 `tensor-app` 测试范围内证明 fixture 使用生产 `PluginRegistry`、`AdapterRegistry`、`GenericDatasetAdapter`、`PersistenceService` 与 `DatasetQueryService` 完成注册、下载、适配、单事务入库和类型保真查询；同时证明空结果不打开事务、适配失败不触碰数据库、测试专用写入故障会回滚已执行的 Upsert，且 `production` profile 即使显式启用属性也不注册 fixture。整个验证不向生产代码增加 fixture 分支、故障钩子或依赖。

## Scope

包含：

- 只创建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`；
- 使用固定 `mysql:8.4.6` Testcontainers 实例和 `classpath:db/migration` 执行现有六个 Flyway 迁移，包括测试专用 V6 fixture 表；
- 通过 `AnnotationConfigApplicationContext` 激活 `acceptance` profile 和 `tensor.plugins.fixture.enabled=true`，从真实 `FixtureConfiguration` 取得唯一 `DataSourcePlugin` 与 `DatasetAdapter` Bean；
- 用上述 Bean 构造生产 `PluginRegistry`、`AdapterRegistry`，并通过 `DatasetStartupValidator(List<DatasetDefinition>, new SchemaInspector(dataSource)).validate()` 获得公开可用的已验证 `DatasetCatalog`；
- 用同一个测试专用 delegating `DataSource` 装配真实 `JdbcTemplate`、`DataSourceTransactionManager`、`ExistingKeyRepository`、`GenericUpsertRepository`、`PersistenceService`、`GenericQueryRepository` 和 `DatasetQueryService`；
- 覆盖成功、空、类型失败、写入失败回滚及 production 不注册五项场景；
- 执行严格 RED/GREEN、既有 schema IT 联跑、完整 reactor、Enforcer、依赖/JAR 隔离、生产能力、范围、格式、Git 跟踪和 clean 门禁。

排除：

- 不修改 POM、生产 Java、配置、YAML、迁移、已有测试或其他文档；
- 不创建第二套下载、适配、持久化或查询流程，不直接调用 fixture 工厂绕过插件注册表，不以 fixture 专用 adapter/repository/service 替代生产实现；
- 不重复测试 M08-T02 已覆盖的 `SOURCE_FAILURE`；本任务按任务卡只覆盖 success、empty、type failure、persistence rollback 和 production absence；
- 不在生产源码、Spring production context、数据库 trigger、存储过程或迁移中加入故障注入；
- 不依赖 Tushare、网络、真实凭证、系统时钟、随机值、测试顺序、共享外部数据库或新增测试库；
- 不提交 `target/`、容器产物、日志、设计/交接/看板或任何非指定实现文件到实现提交。

## Approach

### 固定数据库、上下文与生产流程装配

`FixtureFlowIT` 使用一个类级固定 `MySQLContainer<?>`，镜像精确为 `mysql:8.4.6`。`@BeforeAll` 启动容器，以容器 JDBC URL、用户名和密码配置 Flyway，location 精确为 `classpath:db/migration`；首次 `migrate()` 必须报告六个迁移，随后 `validateWithResult()` 成功。测试结束由 `@AfterAll` 关闭 acceptance context 和容器。每项数据库场景前使用未包装的容器连接执行 `DELETE FROM fixture__fixture_daily`，保证测试无顺序依赖。

原始 JDBC 数据源使用 Spring JDBC 已有的 `DriverManagerDataSource`。其外层包一层 `FixtureFaultDataSource`，后者是 `FixtureFlowIT` 内的 `private static final` 测试实现；生产代码和配置不可引用它。acceptance context 的构造顺序固定为：

1. 新建 `AnnotationConfigApplicationContext`，激活唯一 profile `acceptance`；
2. 在最高优先级 `MapPropertySource` 中设置 `tensor.plugins.fixture.enabled=true`；
3. 注册 `FixtureConfiguration.class` 并 `refresh()`；
4. 从 context 分别读取 `DataSourcePlugin` 和 `DatasetAdapter` 类型 Bean，断言各恰有一个；
5. 以 Bean 列表构造 `PluginRegistry` 与 `AdapterRegistry`，从 adapter definition 和包装数据源构造 `DatasetStartupValidator`，调用 `validate()` 获得目录；
6. 对包装数据源创建一个 `JdbcTemplate` 和一个 `DataSourceTransactionManager`，据此直接构造生产持久化、查询 repository 与 service。

所有场景使用固定 `DatasetKey(fixture, fixture_daily)`、参数 `Map.of("scenario", scenario.name())` 和毫秒精度 `Instant.parse("2026-08-07T08:09:10.123Z")`。下载必须从 `PluginRegistry.find(PluginId.of("fixture"))` 得到的 `DataSourcePlugin` 发起；适配必须从 `AdapterRegistry.find(datasetKey)` 得到的 `DatasetAdapter` 发起。不得直接 `new FixturePlugin`、`new FixtureEnvelopeFactory` 或 `new GenericDatasetAdapter`。目录只通过公开 validator 创建，因为 `DatasetCatalog` 构造器不是公共 API。

### 成功、空与类型失败

成功测试按同一调用链执行：注册表查找插件 → `download(fixture_daily, SUCCESS)` → 注册表查找 adapter → `adapt(envelope, fixedInstant)` → `PersistenceService.persist(batch)` → `DatasetQueryService.query(key, criteria)`。查询条件只传 `tsCode="000001.SZ"`、`page=1`、`pageSize=20`，其余日期条件为 null，以遵守 fixture 只声明 `ts_code` filter 的元数据。

成功结果必须精确断言：

- 插件 descriptor 可下载，查找出的 adapter 是生产 `GenericDatasetAdapter`；
- `WriteCounts` 为 `inserted=1, updated=0`；
- 页面 columns 按序为 `ts_code, trade_date, amount, note, source_plugin, source_api, ingested_at`，page/pageSize/totalElements/totalPages 为 `1/20/1/1`；
- 唯一行按同序为 `"000001.SZ"`、`LocalDate.of(2026, 8, 7)`、`new BigDecimal("11.230000000000000000")`、null、`"fixture"`、`"fixture_daily"`、上述固定 `Instant`，从而同时验证 DATE、DECIMAL、nullable 字段和来源元数据的真实 JDBC 往返。

空场景从同一插件和 adapter 获得 columns 完整、rows 为空的 `AdaptedBatch`。在调用 `persist` 前重置包装数据源的观测计数，断言结果为 `WriteCounts(0, 0)`、包装数据源 `getConnection` 次数为 0、故障批次执行次数为 0，且用未包装连接核对表仍为零行。这证明 M06 空批次分支在锁和事务/JDBC 之前返回。

类型失败场景从同一插件下载结构合法但 amount 为 `not-a-decimal` 的包络；在适配前重置观测计数，断言真实 adapter 抛出 `AdapterException`，错误码精确为 `ADAPTER_TYPE_INVALID`，消息精确为 `Invalid adapter value: api=fixture_daily, row=0, field=amount`。不得构造部分 batch 或调用 `persist`；包装数据源连接次数保持 0，未包装连接核对表为零行。

### 写入故障代理与真实回滚

`FixtureFaultDataSource` 实现 `javax.sql.DataSource` 并显式委托其全部 DataSource 方法。两个 `getConnection` 重载均增加连接观测计数，再以 JDK dynamic proxy 包装真实 `Connection`；connection proxy 只对 `prepareStatement` 返回的 `PreparedStatement` 增加第二层 proxy，其他调用原样委托。反射调用必须解包 `InvocationTargetException` 并抛出原 cause，保持 JDBC/Spring 异常语义。

prepared-statement proxy 观察所有 `setString(index, value)`：一旦任一绑定值精确等于 `PERSISTENCE_FAILURE`，把当前 statement 标记为故障目标。对未标记 statement 以及所有 setter、`addBatch`、查询和生命周期调用完全委托。标记 statement 收到 `executeBatch()` 时必须严格执行：

1. 先调用真实 statement 的 `executeBatch()`；
2. 真实调用成功返回后，将 `delegatedMarkedBatchCount` 增加 1；
3. 随即抛出新的 `SQLException("Fixture persistence failure")`，不返回 affected rows。

若真实 `executeBatch()` 自身失败，则直接传播真实异常，不增加计数，也不替换为注入异常。代理不识别 SQL、表名、参数位置或生产类型，只消费 M08-T02 唯一固定 note 标记；观测状态只通过 package-private/private 测试 helper 重置和读取，不形成生产 API。

回滚测试先用 `SUCCESS` 和固定时间持久化同一业务键，确认插入成功；再使用晚一毫秒的固定 `Instant` 适配 `PERSISTENCE_FAILURE`。调用第二次 `persist` 时断言 Spring 抛出 `DataAccessException`，root cause 是消息为 `Fixture persistence failure` 的 `SQLException`，且 `delegatedMarkedBatchCount == 1`。随后通过真实 `DatasetQueryService` 查询，必须仍只有种子行，note 仍为 null、amount 与 `ingested_at` 仍为首次值。该组合证明 Upsert 已在事务连接上真实执行，但抛错后完整回滚，而不是在 JDBC 前短路或留下部分更新。

### production 隔离

production 测试另建独立 `AnnotationConfigApplicationContext`，只激活 `production` profile，同时仍设置 `tensor.plugins.fixture.enabled=true` 并注册 `FixtureConfiguration.class`。刷新后 `getBeansOfType(DataSourcePlugin.class)`、`getBeansOfType(DatasetAdapter.class)` 与 `getBeansOfType(FixturePlugin.class)` 都必须为空，然后关闭 context。它不启动或装配 fixture 生产流程；app 生产 JAR 不含 fixture 类/YAML/V6 的事实由 JAR 门禁独立证明。

### 直接输入与约束比较

- M05-T01 的 `PluginRegistry`/`AdapterRegistry` 只暴露构造期不可变、安全隔离后的查找表；本测试只传入 context 实际 Bean 并从 `find` 消费唯一可下载插件和唯一 adapter，不绕过重复/readiness 规则。
- M05-T05 的 `GenericDatasetAdapter` 只接收成功包络和调用方时间，合法空结果形成零行 batch，任一类型失败拒绝整个适配；本测试分别消费这三条边界，不把失败重新分类或产生部分写入。
- M06-T04 的 `PersistenceService` 在合法空 batch 上零事务/零 JDBC，在非空 batch 上使用单一 REQUIRED 事务并在异常后回滚；测试数据源与 `DataSourceTransactionManager`、两个 JDBC repository 共享同一包装实例，确保线程绑定的是同一事务连接。
- M06-T06 的 `DatasetQueryService` 只接受已验证目录中的 definition，执行 COUNT-first 并返回保序、类型保真的页面；本测试通过 `DatasetStartupValidator` 公开入口创建真实目录并只使用已声明 filter。
- M08-T02 的 fixture 插件提供确定 success/empty/type/persistence 包络，且 `PERSISTENCE_FAILURE` 只允许被后继 test-scope 故障注入消费；本设计把 marker 检测严格封闭在唯一 IT 文件，不改变生产插件、adapter 或 persistence service。

五项输入职责互补且无冲突：context Bean 提供扩展身份，注册表提供查找，adapter 提供类型化 batch，目录/schema 提供持久化和查询准入，M06 服务提供真实事务/JDBC，fixture marker 只在测试代理的 executeBatch 后触发异常。`SOURCE_FAILURE` 已在 M08-T02 完成，本任务不重复或扩展其合同。

## Files

创建：

- `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`：包含固定 MySQL 生命周期、Flyway/acceptance/production context、生产流程装配、五项场景测试，以及唯一嵌套 test-scope DataSource/Connection/PreparedStatement 代理。

不修改或删除任何文件。实现提交消息固定为 `test(fixture): verify plugin through core data flow`，提交精确包含上述一个已跟踪的新文件；设计、交接、看板、POM、生产源码、迁移、已有测试、`target/` 或其他文件不得混入实现提交。

## Tests

### 新鲜基线与 test-scope RED

实施前在允许 Mockito/Byte Buddy attach 且 Docker 可用的环境运行默认 M08 module gate：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
```

预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 13，共 272/272，0 failure、0 error、0 skipped，六层 Enforcer 与 ArchUnit 通过。`FixtureFlowIT` 的 `IT` 命名不进入默认 Surefire 扫描，因此该计数必须保持不变。

同时先运行现有 schema 合同：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=FlywaySchemaContractIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期固定 MySQL 8.4.6 上 52/52，0 failure、0 error、0 skipped，首次六迁移、Flyway validate、重复 migrate、50 张表及 V6 test-only 断言全部通过。

严格 RED 时先完整创建五个测试方法、数据库/context 生命周期、断言和方法签名，但让唯一私有流程装配 helper 固定抛 `UnsupportedOperationException("Fixture flow not wired")`，尚不实现嵌套故障数据源及 service wiring。运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=FixtureFlowIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因四个数据库流程场景命中 `Fixture flow not wired` 而非零；production profile 隔离测试通过。Flyway、容器、Spring 条件、测试编译、依赖解析或既有代码不得先失败。该失败作为本测试任务的可归因 RED。

### 聚焦与联跑 GREEN

完成最小测试装配和嵌套代理后重跑相同 `FixtureFlowIT` 命令，预期恰有五项普通 `@Test` 且 5/5 通过，0 failure、0 error、0 skipped：

1. acceptance 注册后 success 经真实 adapter、persistence、query 返回精确 typed row 和来源元数据；
2. empty 适配为合法空 batch，persist 返回 `(0,0)` 且包装数据源零连接；
3. type failure 产生精确 adapter error，未调用 persistence 且表为零行；
4. persistence marker 的真实 batch 先执行后抛错，事务回滚并保留原行；
5. `production + enabled=true` 仍无 fixture plugin/adapter Bean。

随后联跑两个显式 IT：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=FlywaySchemaContractIT,FixtureFlowIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期固定 MySQL 8.4.6 上合计 57/57，0 failure、0 error、0 skipped；两类 IT 各自管理容器且不共享数据库状态。

### reactor、隔离、范围与清理

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
```

两条默认命令均预期 272/272，0 failure、0 error、0 skipped，六层 Enforcer 与 ArchUnit 通过；显式 IT 的 5/5 和联跑 57/57 是不可替代的 MySQL/事务证据。

运行静态与边界门禁：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am dependency:tree \
  -Dincludes=com.akkc.tensor:tensor-plugin-fixture
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/plugin/fixture|datasets/fixture|V6__create_fixture_tables'
rg -n 'FixtureFaultDataSource|PERSISTENCE_FAILURE|executeBatch|DataSourceTransactionManager|PluginRegistry|AdapterRegistry|GenericDatasetAdapter|PersistenceService|DatasetQueryService' \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java
rg -n 'FixtureFaultDataSource|PERSISTENCE_FAILURE|FixturePlugin|tensor\.plugins\.fixture' \
  data-plane/tensor-app/src/main data-plane/tensor-core/src/main
git diff --quiet -- \
  data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/main data-plane/tensor-app/src/test/resources \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-fixture data-plane/tensor-plugin-tushare
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture
```

依赖树必须显示 fixture 仅为 app 的 test-scope dependency；app 生产 JAR 扫描无输出并退出 1；授权测试扫描命中代理、marker、真实事务和五个生产组件；生产能力扫描无输出并退出 1；受保护路径和格式退出 0；提交前 scoped status 精确显示唯一新 IT 且无 `target/`。按仓库规则把新文件加入 Git。

实现提交后运行：

```bash
git show --stat --oneline --summary HEAD
git show --format= --name-only HEAD
git ls-files --error-unmatch \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java
git diff --check HEAD^ HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
```

提交必须使用固定消息且精确只有一个已跟踪 IT；格式、clean 和最终工作树门禁通过且没有 `target/`。clean 后仍需在 Docker 可用环境重新执行显式 5/5，再执行完整 reactor `verify` 272/272，作为提交态最终结果。

## Acceptance

- 唯一新文件通过 Spring acceptance context 取得 fixture Bean，并只经生产 registry、adapter、catalog、persistence 和 query 公共表面完成流程；没有直接工厂捷径、fixture 专用核心分支或生产改动；
- 固定 MySQL 8.4.6 与六个 Flyway 迁移上，SUCCESS 精确返回一个 `LocalDate`/`BigDecimal`/nullable note/来源 metadata/UTC `Instant` 类型保真的页面行，写计数为 `(1,0)`；
- EMPTY 返回合法空 batch，persist 为 `(0,0)` 且没有获取包装数据源连接；TYPE_FAILURE 在 adapter 层以固定 `ADAPTER_TYPE_INVALID` 拒绝并保持零行；
- PERSISTENCE_FAILURE 由唯一 test-scope 数据源在真实 `executeBatch` 成功后抛 `SQLException`，Spring 传播 `DataAccessException`，delegated 计数为 1，事务回滚后原行及首次 `ingested_at` 完整保留；
- `production + tensor.plugins.fixture.enabled=true` 不注册任何 fixture plugin/adapter，app 生产 JAR 不含 fixture 类、YAML 或 V6；
- 严格 RED 只来自未完成 test wiring；GREEN 5/5、与既有 schema IT 联跑 57/57、默认 reactor `test`/`verify` 272/272、Enforcer、ArchUnit、依赖/JAR/生产能力/范围/格式/Git 跟踪/clean 门禁均得到预期结果；
- 实现提交消息为 `test(fixture): verify plugin through core data flow` 且精确包含一个已跟踪新文件，没有混入 `SOURCE_FAILURE` 重测、POM、生产源码、迁移、已有测试、其他任务或生成物。

## Risks

- `FixtureFaultDataSource` 依赖 Spring JDBC collection batch 最终调用 JDBC `PreparedStatement.executeBatch()`；当前 `GenericUpsertRepository` 已固定该路径。若未来生产 repository 改为非 batch API，本 IT 会停止触发注入并失败，迫使新路径重新建立真实回滚证据。
- marker 识别以绑定值而非 SQL/列位置完成；`PERSISTENCE_FAILURE` 是 M08-T02 为该 test-scope 接缝冻结的唯一值。后续 fixture 业务数据若允许同值作为普通 note，必须先修改场景合同和测试注入策略，不能把该判断移入生产代码。
- `DATETIME(3)` 只保留毫秒；测试固定 Instant 已精确到毫秒并通过 UTC Calendar 查询，避免舍入和时区歧义。部署仍必须遵守既有 UTC 连接约定。
- 两个显式 IT 各自启动 MySQL 8.4.6，运行时间高于默认 reactor；因此 `*IT` 继续显式执行，不通过改名把容器测试混入每次普通 Surefire 回归。
