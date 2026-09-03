# M08-T01 fixture 元数据、插件和适配器——任务设计

任务编号：`M08-T01`
对应任务：[M08-T01](../superpowers/plans/tensor-modules/M08-fixture-plugin.md#task-m08-t01-fixture-元数据插件与适配器25h)
实施产物：fixture 条件配置、单数据集插件、`fixture_daily.yaml`、复用的 `GenericDatasetAdapter` Bean、插件测试及收窄后的模块依赖门禁

## Goal

在 `tensor-plugin-fixture` 中交付只供验收使用的最小数据源入口：仅当 Spring profile `acceptance` 已激活且 `tensor.plugins.fixture.enabled=true` 时，注册一个描述 `fixture/fixture_daily` 的 `DataSourcePlugin` 和一个直接复用 `GenericDatasetAdapter` 的 `DatasetAdapter`。元数据必须与 M04-T06 已建的测试专用表完全一致，默认及 production profile 不得出现 fixture Bean，且本任务不提前实现 M08-T02 的五种确定性下载场景。

## Scope

包含：

- 在 `data-plane/tensor-plugin-fixture/pom.xml` 增加无显式版本的 `tensor-core` 与 `spring-boot-autoconfigure` compile 依赖；版本继续由 reactor/父 BOM 管理；
- 创建 public final `FixturePlugin`，实现 M02-T05 的 `DataSourcePlugin` 三方法合同并发布唯一插件/接口描述符；
- 创建 public final `FixtureConfiguration`，以 `@Profile("acceptance")` 和 `@ConditionalOnProperty(prefix = "tensor.plugins.fixture", name = "enabled", havingValue = "true")` 双条件注册插件与适配器；
- 在配置类中直接构造唯一的不可变 `DatasetDefinition`，供插件描述符和 `GenericDatasetAdapter` 共享；
- 创建 `datasets/fixture/fixture_daily.yaml`，保存与 Java 定义一致的验收元数据；
- 创建六项普通 JUnit 5 测试，覆盖公开表面、精确描述符、Java/YAML 元数据、条件注册、真实通用适配器和临时安全下载拒绝；
- 修改 `ModuleDependencyTest`，仅从 fixture 禁止列表移除 core，继续禁止 fixture 依赖 Tushare 与 app；
- 执行严格 TDD、聚焦测试、模块/完整 reactor 回归、JAR 隔离、依赖、静态和 Git 范围门禁。

项目所有者已明确批准任务卡外的两项实现文件扩展：修改 fixture POM，并修改 `ModuleDependencyTest`；也批准将 M05-T05 加为直接依赖。该决定只把 `fixture -> core` 作为受控例外放开，不授权 `fixture -> tushare`、`fixture -> app` 或任何反向依赖。

排除：

- 不创建 M08-T02 的 `FixtureScenario`、`FixtureEnvelopeFactory` 或其测试，不实现 success、empty、source failure、type failure、persistence failure 的包络内容；
- 不创建 M08-T03 的 `FixtureFlowIT`，不配置故障注入数据源，不执行数据库、持久化、事务、查询或端到端流程；
- 不修改 plugin-api、core、Tushare 生产/测试代码、dataset JSON schema、M04 测试迁移、生产迁移、应用配置、Controller、合同、前端或历史任务设计；
- 不依赖、移动、复制或泛化 Tushare 的 `DatasetDefinitionLoader`；fixture YAML 本任务作为验收契约资源，不作为运行时加载源；
- 不增加新的业务实现模块、YAML 解析库、网络、数据库、重试、凭证、外部插件加载或生产 profile fixture 入口；
- 不把场景工厂缺失伪装为可下载：M08-T01 的注册态 readiness 明确不可下载，待 M08-T02 按已批准范围修改插件、配置和测试后再切换。

## Approach

### 固定元数据

配置类持有一份 private static final `DatasetDefinition`。定义使用公共值对象直接构造，插件和适配器 Bean 必须共享这一个实例，不创建 fixture 专用适配器或第二份元数据模型。

精确数据集合同如下：

| 项 | 固定值 |
|---|---|
| plugin ID | `fixture` |
| API | `fixture_daily` |
| table | `fixture__fixture_daily` |
| display name | `Fixture 日线` |
| category | `验收` |
| query mode | `trade_date` |
| fixed column | `ts_code` |
| filters | 仅 `[ts_code]` |
| business key | `COMPOSITE [ts_code, trade_date]` |
| batch size | `DatasetDefinition` 默认值 `500` |

唯一参数按以下公开描述符构造：

- name `scenario`、label `场景`、description `确定性验收场景`；
- type `ENUM`、`required=true`、default `SUCCESS`；
- allowed values 按序为 `SUCCESS`, `EMPTY`, `SOURCE_FAILURE`, `TYPE_FAILURE`, `PERSISTENCE_FAILURE`；
- pattern 与 related parameter 均为 null。

四个业务列严格按下列顺序构造，`displayOrder` 等于下标，未列出的可选属性使用空列表、false 或 null：

| order | name / label | logical type | nullable | length | precision / scale |
|---:|---|---|---|---:|---|
| 0 | `ts_code` | `STRING` | false | 64 | — |
| 1 | `trade_date` | `DATE` | false | — | — |
| 2 | `amount` | `DECIMAL` | false | — | 38 / 18 |
| 3 | `note` | `STRING` | true | 255 | — |

`fixture_daily.yaml` 逐项保存相同值与顺序。由于现有 JSON schema 的 `tableName` 仍硬编码 `tushare_pro__*`，本任务不错误复用该 schema 或 Tushare loader；测试分别对 Java 值对象和 YAML 的完整固定文本建立独立断言，使任一侧漂移都失败。

### `FixturePlugin`

`FixturePlugin` 是 public final 类并直接实现 `DataSourcePlugin`。它只有一个 public 构造器 `(DatasetDefinition)`，拒绝 null，且要求 key 精确为 `fixture/fixture_daily`；错误 definition 使用不含输入值的固定 `IllegalArgumentException("definition must be fixture_daily")`。

构造阶段不执行 I/O，只从 definition 建立一次不可变 `PluginDescriptor`：

- plugin ID `fixture`、display name `Fixture`、description `Fixture 验收数据源`；
- readiness 固定为 `PluginReadiness(true, true, false, "Fixture scenarios are not configured")`；fixture 不需要凭证，因此注册态 `credentialConfigured=true`；
- `apis` 恰含一个由 definition 的 API、显示名、分类、查询模式和参数投影而成的 `ApiDescriptor`；
- `datasets` 恰含 definition 的唯一 key；
- `descriptor()` 与 `readiness()` 每次返回构造时保存的相同不可变实例。

`download(ApiName, Map<String,Object>)` 先拒绝 null API/params；未知 API 固定抛 `IllegalArgumentException("Unknown Fixture API")`，不回显输入。唯一合法 API 在 M08-T02 场景工厂尚未接入时固定抛 `SourceException(ErrorCode.SOURCE_UNAVAILABLE, "Fixture scenarios are not configured")`。本任务不解释或复制 `scenario` 值，也不返回假成功/空包络。

### `FixtureConfiguration`

配置类使用：

```java
@Configuration(proxyBeanMethods = false)
@Profile("acceptance")
@ConditionalOnProperty(
        prefix = "tensor.plugins.fixture",
        name = "enabled",
        havingValue = "true")
```

它只发布两个 Bean：

1. `FixturePlugin fixturePlugin()`，以固定 definition 构造；
2. `DatasetAdapter fixtureDatasetAdapter()`，返回 `new GenericDatasetAdapter(definition, new ValueConverter(), new FingerprintKeyCodec())`。

不把 definition、converter 或 codec 另行暴露为 Bean，不扫描包，不依赖 Tushare，也不读取 YAML。`proxyBeanMethods=false` 下两个方法都直接使用同一 static final definition，避免依赖代理调用。

条件真值固定为：

| active profiles | property | FixturePlugin / DatasetAdapter |
|---|---|---|
| 无、`default` 或 `production` | 缺失/false/true | 均不存在 |
| `acceptance` | 缺失或 false | 均不存在 |
| `acceptance` | true | 各恰有一个 |

测试使用 `AnnotationConfigApplicationContext`，在注册 `FixtureConfiguration` 前设置 profile/property，再 refresh；不启动完整 Boot 应用，也不新增 `spring-boot-test`。

### 模块依赖门禁

fixture POM 新增：

```xml
<dependency>
    <groupId>com.akkc.tensor</groupId>
    <artifactId>tensor-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
```

既有 plugin-api 与三项 test 依赖保持不变，不写 version，不增加 build/profile 配置。`ModuleDependencyTest` 中 fixture 规则的禁止包精确从 `core, tushare, app` 改为 `tushare, app`；plugin-api、core 和 Tushare 三条规则保持原样。因此 core 继续不依赖 fixture，Tushare 继续不依赖 core/fixture/app，fixture 也不能借本任务接入 Tushare loader。

### 直接输入与一致性

- M02-T05 的 `DataSourcePlugin`、`DatasetAdapter`、`SourceException` 和 `SOURCE_UNAVAILABLE` 固定公开方法、错误类别与安全消息边界；本任务只实现/构造这些类型，不扩展 SPI。
- M04-T06 的 `V6__create_fixture_tables.sql` 冻结四个业务列、类型、可空性、`fixture__fixture_daily` 和 `[ts_code, trade_date]` 主键，并确认唯一查询 filter 为 `ts_code`；本任务元数据与其逐项一致，且不把测试 V6 放入 production。
- M05-T05 的 public final `GenericDatasetAdapter`、`ValueConverter` 和 `FingerprintKeyCodec` 提供唯一通用适配路径；本任务直接实例化三者，不复制转换、键或去重逻辑。

三项输入职责互补。M05-T05 位于 core 与旧 fixture 模块边界发生的冲突，已由项目所有者明确裁决为只允许 `fixture -> core`，并由 POM 与 ArchUnit 同步表达；Tushare loader 对 plugin ID 和表名 schema 的硬编码不作为可消费输入。

## Files

修改：

- `data-plane/tensor-plugin-fixture/pom.xml`：只增加 `tensor-core` 和 `spring-boot-autoconfigure` 两项 compile 依赖。
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java`：只从 fixture 禁止依赖集合移除 core。

创建：

- `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`：唯一 fixture 数据源描述符和 M08-T01 临时下载拒绝。
- `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java`：双条件配置、固定 definition、插件 Bean 与通用适配器 Bean。
- `data-plane/tensor-plugin-fixture/src/main/resources/datasets/fixture/fixture_daily.yaml`：单数据集验收元数据契约。
- `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java`：六项永久插件、元数据、条件和适配器测试。

不删除文件。实现提交消息固定为 `feat(fixture): add acceptance data-source plugin`，提交精确包含上述六个文件；设计、交接、看板、`target/` 或其他文件不得混入实现提交。四个新文件按仓库规则全部加入 Git。

## Tests

### 新鲜基线

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
```

预期 plugin-api 79、core 75、Tushare 93、fixture 0、app 13，共 260/260，0 failure、0 error、0 skipped；六层 Enforcer 和既有 ArchUnit 通过。2026-09-03 已在允许 Mockito/Byte Buddy attach 的环境取得该基线；受限 JVM 若只因 inline mock maker 无法附加而失败，属于环境阻塞，不能改测试或跳过 core。

### 严格 RED

先修改 POM 与 ArchUnit，完整创建 YAML 和六项 `FixturePluginTest`，但不创建两个生产 Java 类型，然后运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture -am \
  -Dtest=FixturePluginTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 fixture `testCompile` 只因 `FixturePlugin` 和 `FixtureConfiguration` 缺失而非零退出；POM 解析、上游编译、测试语法、YAML 路径和既有代码不得形成伪 RED。

### 聚焦 GREEN

创建两个最小生产类后重跑同一命令，预期 `FixturePluginTest` 恰有六项普通 `@Test`，6/6 通过：

1. 反射确认 `FixturePlugin`/`FixtureConfiguration` final、插件唯一 public 构造器及 SPI 三方法，构造 null/错误 key 被固定拒绝；
2. 精确断言插件文案、唯一 API/数据集、注册态 readiness 及 scenario 参数全部字段和顺序；
3. 精确断言 Java definition 的表、四列、类型/参数/可空性/顺序、业务键、filter、fixed column、batch size，并断言 YAML 资源完整固定文本；
4. `acceptance + enabled=true` 上下文各注册一个 `FixturePlugin` 和 `DatasetAdapter`，后者运行时类型恰为 `GenericDatasetAdapter`、共享同一 definition，并可把一行合法成功包络适配为正确类型和键；
5. 缺 acceptance profile、`production + enabled=true` 均不注册两个 Bean；
6. `acceptance` 下属性缺失或 false 均不注册；合法 API 临时抛精确 `SOURCE_UNAVAILABLE`，未知 API 与 null 边界按设计拒绝，不产生包络或 I/O。

测试只使用 JUnit、AssertJ、真实 Spring context 和真实领域类型，不使用 Mockito、网络、数据库、时钟、Tushare loader、动态扫描或测试专用替身适配器。

### 模块与完整 reactor

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
```

第一条预期 plugin-api 79、core 75、fixture 6，共 160/160；后两条均预期 plugin-api 79、core 75、Tushare 93、fixture 6、app 13，共 266/266。全部命令退出 0，0 failure、0 error、0 skipped，六层 Enforcer 与修改后的 ArchUnit 通过。

### 条件、依赖、JAR、静态与范围门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-plugin-fixture/pom.xml dependency:tree \
  -Dincludes=com.akkc.tensor:tensor-core,org.springframework.boot:spring-boot-autoconfigure

jar tf data-plane/tensor-plugin-fixture/target/tensor-plugin-fixture-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/plugin/fixture/(FixturePlugin|FixtureConfiguration)|datasets/fixture/fixture_daily.yaml'
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/plugin/fixture|datasets/fixture'

rg -n 'plugin\.tushare|tensor-plugin-tushare|RestClient|ServiceLoader|java\.sql|javax\.sql|JdbcTemplate|Flyway' \
  data-plane/tensor-plugin-fixture/pom.xml \
  data-plane/tensor-plugin-fixture/src
rg -n '@Profile\("acceptance"\)|tensor\.plugins\.fixture|ConditionalOnProperty|GenericDatasetAdapter' \
  data-plane/tensor-plugin-fixture/src/main/java \
  data-plane/tensor-plugin-fixture/src/test/java

git diff --quiet -- \
  data-plane/pom.xml data-plane/tensor-plugin-api data-plane/tensor-core \
  data-plane/tensor-plugin-tushare data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/main
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-fixture \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
```

依赖树必须只显示两项批准的新 direct compile dependency 及其正常传递闭包，不显示 fixture 对 Tushare/app 的依赖。fixture JAR 检查必须找到两个类和一个 YAML；app 生产 JAR 检查无输出并退出 1，证明 test-scope fixture 未进入生产包。禁止扫描无输出并退出 1；授权机制扫描至少命中 profile、property、condition 和通用适配器。受保护路径无差异，scoped status 在提交前精确显示六个 Files 节实现文件且无 `target/`；格式和 clean 通过。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确六文件范围，工作树干净。

## Acceptance

- fixture 仅在 `acceptance` 与 `tensor.plugins.fixture.enabled=true` 同时成立时各注册一个插件和通用适配器；默认、production、缺属性或 false 均无 fixture Bean；
- `FixturePlugin` 的公开表面、精确插件/API/参数/数据集描述符、注册态不可下载 readiness、未知 API 和 M08-T01 临时 `SOURCE_UNAVAILABLE` 边界与设计一致，无 I/O、输入回显或假包络；
- Java definition 与 YAML 均精确表达 `fixture/fixture_daily`、四列、COMPOSITE 键、唯一 `ts_code` filter、fixed column 和五值 scenario 参数，并与 M04-T06 的表结构一致；
- 注册的适配器运行时类型恰为 M05-T05 的 `GenericDatasetAdapter`，真实合法包络适配成功；不存在 fixture 专用适配、转换、指纹、去重或 YAML loader 路径；
- POM 只增加批准的 core/Spring Boot 依赖；ArchUnit 只允许 `fixture -> core`，仍拒绝 fixture 对 Tushare/app 及其他既有反向边；
- 严格 TDD 取得缺两个生产类的可归因 RED 后 6/6 GREEN；模块 160/160、完整 reactor `test`/`verify` 266/266、六层 Enforcer、ArchUnit、依赖、JAR 隔离、静态、范围、格式和 clean 门禁全部得到预期结果；
- 实现提交精确包含 Files 节六个文件，四个新文件均由 Git 跟踪；未提前实现 M08-T02/T03，未修改受保护模块、数据库、应用生产代码、配置、合同或前端。

## Risks

- 允许 `fixture -> core` 是项目所有者为复用 `GenericDatasetAdapter` 批准的窄例外，取代旧模块依赖图对 fixture 的限制；若后续扩大为 fixture 依赖 Tushare/app、core 反向依赖 fixture 或把 fixture 打入生产 JAR，必须由 ArchUnit/JAR 门禁拒绝。
- fixture YAML 不经现有 schema/loader 运行时加载，因为二者硬编码 Tushare；本任务用完整固定资源文本与独立 Java 值断言控制漂移。若未来需要多 fixture 数据集或运行时 YAML 加载，必须单独设计不含 plugin ID 硬编码的公共元数据能力，不能复制现有 loader。
- M08-T01 的已注册插件有意报告不可下载并安全拒绝唯一 API；M08-T02 必须按已批准扩展同时修改插件、配置和测试，接入 `FixtureEnvelopeFactory` 后再把 readiness 改为可下载，不能长期保留中间态或在本任务偷跑场景逻辑。
- 完整 reactor 测试需要 Mockito/Byte Buddy 本地 JVM attach；沙箱内已观察到的 inline mock maker 初始化失败不是代码 RED，必须在允许 attach 的环境验证，不能删测、skip core 或用该环境失败宣称实现回归。
