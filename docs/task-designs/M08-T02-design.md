# M08-T02 成功、空、上游失败、适配失败和写入失败模式——任务设计

任务编号：`M08-T02`
对应任务：[M08-T02](../superpowers/plans/tensor-modules/M08-fixture-plugin.md#task-m08-t02-确定性结果与故障场景25h)
实施产物：`FixtureScenario`、`FixtureEnvelopeFactory`、确定性场景测试，以及 M08-T01 插件接缝从临时拒绝切换为真实场景委托

## Goal

在 `tensor-plugin-fixture` 中交付五种只供验收使用的确定性下载结果：合法单行、合法空结果、固定上游失败、可由真实 `GenericDatasetAdapter` 拒绝的类型失败，以及由 M08-T03 测试故障注入识别的写入失败标记。M08-T01 已建立的 `acceptance` profile 与 `tensor.plugins.fixture.enabled=true` 双条件继续作为唯一注册入口；插件配置场景工厂后改为可下载，但仍不执行数据库、网络、事务或持久化。

## Scope

包含：

- 创建 public enum `FixtureScenario`，只声明 `SUCCESS`、`EMPTY`、`SOURCE_FAILURE`、`TYPE_FAILURE`、`PERSISTENCE_FAILURE` 五个既定值；
- 创建 public final `FixtureEnvelopeFactory`，由场景和已经验证的参数构造不可变 `DownloadEnvelope`，或为来源失败抛出固定安全 `SourceException`；
- 修改 `FixturePlugin`，通过新增的工厂构造依赖选择场景、把 readiness 切换为可下载并只委托一次；
- 修改 `FixtureConfiguration`，在既有双条件内为插件直接构造工厂，继续只发布插件和适配器两个 Bean；
- 修改 `FixturePluginTest`，覆盖新的公开构造器、可下载 readiness、配置 Bean 边界、场景路由和安全失败；
- 创建六项普通 JUnit 5 `FixtureEnvelopeFactoryTest`，逐项覆盖公开表面、五种结果以及真实通用适配器对故障包络的消费结果；
- 执行严格 TDD、聚焦/模块/完整 reactor、Enforcer、ArchUnit、依赖、JAR 隔离、静态、范围、格式和 clean 门禁。

项目所有者已在权威看板批准 M08-T01/M08-T02 的两阶段下载接缝，并批准本任务除任务卡的三个新文件外修改 M08-T01 的 `FixturePlugin.java`、`FixtureConfiguration.java` 和 `FixturePluginTest.java`。本任务不扩大该六文件范围。

排除：

- 不创建或修改 M08-T03 的 `FixtureFlowIT`、故障注入数据源、事务、持久化或查询装配；
- 不让包络工厂访问数据库、网络、时钟、随机数、文件、环境变量、凭证、Spring context 或 Tushare 类型；
- 不修改 fixture YAML、POM、ArchUnit、plugin-api、core、Tushare、app、迁移、合同、生产配置或前端；
- 不增加新的 Bean、配置属性、场景别名、大小写归一、重试、日志、指标、线程或异步行为；
- 不在插件内复制参数元数据校验、字段适配、类型转换、业务键、持久化失败或回滚逻辑；
- 不把 `PERSISTENCE_FAILURE` 实现为工厂异常或生产写入分支；它只产生一行合法数据和固定 note 标记，实际写入失败由 M08-T03 的测试专用故障注入触发。

## Approach

### 场景与工厂公开表面

公开表面冻结为：

```java
public enum FixtureScenario {
    SUCCESS,
    EMPTY,
    SOURCE_FAILURE,
    TYPE_FAILURE,
    PERSISTENCE_FAILURE
}

public final class FixtureEnvelopeFactory {
    public FixtureEnvelopeFactory();
    public DownloadEnvelope create(FixtureScenario scenario, Map<String, Object> params);
}
```

枚举除 Java 自动生成的 `values()`/`valueOf(String)` 外不声明字段、构造器或方法。工厂无实例字段；唯一 public `create` 方法用组件名 `scenario`、`params` 拒绝 null，并让 `DownloadEnvelope` 自身复制参数。工厂不解析字符串场景，也不修改调用方 map。

所有成功包络固定使用 plugin ID `fixture`、API `fixture_daily`、字段顺序 `[ts_code, trade_date, amount, note]`、`DownloadStatus.SUCCESS` 和 `error=null`。五种场景的精确结果为：

| 场景 | 结果 |
|---|---|
| `SUCCESS` | `rowCount=1`，唯一原始行依次为 `"000001.SZ"`, `"20260807"`, `"11.23"`, `null`。真实通用适配后得到 `LocalDate.of(2026, 8, 7)`、`BigDecimal("11.230000000000000000")` 和 nullable note。 |
| `EMPTY` | `rowCount=0`、`data=[]`，仍保留完整四字段和调用方参数，不返回 null、不构造失败包络。 |
| `SOURCE_FAILURE` | 不构造包络；抛 `SourceException(ErrorCode.SOURCE_UNAVAILABLE, "Fixture source unavailable")`，`retryable=true`，不附 cause、输入值或内部信息。 |
| `TYPE_FAILURE` | `rowCount=1`，除 amount 固定为 `"not-a-decimal"` 外与 `SUCCESS` 相同；工厂仍返回合法成功包络，真实 `GenericDatasetAdapter` 随后以既有 `ADAPTER_TYPE_INVALID` 和 `Invalid adapter value: api=fixture_daily, row=0, field=amount` 拒绝。 |
| `PERSISTENCE_FAILURE` | `rowCount=1`，前三项与 `SUCCESS` 相同，note 固定为 `"PERSISTENCE_FAILURE"`；真实适配必须成功并保留该 note，留给 M08-T03 的 test-scope 数据源触发写入失败。 |

工厂使用穷尽 `switch`，不提供默认场景、额外场景或失败 `DownloadEnvelope`。`PERSISTENCE_FAILURE` 标记只是 acceptance-only fixture 数据；本任务不读取它或据此抛异常。

### 插件选择与委托

`FixturePlugin` 的唯一 public 构造器从 `(DatasetDefinition)` 改为：

```java
public FixturePlugin(DatasetDefinition definition, FixtureEnvelopeFactory envelopeFactory);
```

构造器继续用既有规则拒绝 null/错误 definition，并以组件名 `envelopeFactory` 拒绝 null 工厂。它保存工厂，构造 `PluginReadiness(true, true, true, null)`；descriptor 的四个状态字段取自该 readiness，文案、唯一 API、参数和 dataset 投影保持 M08-T01 不变。`descriptor()` 与 `readiness()` 继续返回构造期保存的相同不可变实例。

`download` 顺序固定为：

1. 以组件名 `apiName`、`params` 拒绝 null；
2. 未知 API 固定抛 `IllegalArgumentException("Unknown Fixture API")`，不解析场景且不回显输入；
3. 读取 `params.get("scenario")`；值只有在运行时类型为 `String` 且与五个枚举名称大小写精确一致时才映射，缺失、非字符串或未知字符串统一抛 `IllegalArgumentException("Unknown Fixture scenario")`，不得回显值；
4. 调用一次 `envelopeFactory.create(scenario, params)` 并直接返回包络或原样传播固定 `SourceException`。

参数默认值与通用 ENUM 准入仍由 M05 的 `ParameterValidator` 在调用插件前完成；插件不把缺失场景改成 `SUCCESS`，不 trim、改大小写或接受别名，只做 fixture 内部场景分派。测试直接调用插件时必须传入 `scenario`，并单独验证绕过参数准入的无效输入被安全拒绝。

### Spring 与生产隔离

`FixtureConfiguration` 保留既有 `@Profile("acceptance")` 和精确 `@ConditionalOnProperty`，固定 definition、适配器构造及 YAML 均不变。`fixturePlugin()` 改为 `new FixturePlugin(DEFINITION, new FixtureEnvelopeFactory())`；`FixtureEnvelopeFactory` 不另行发布为 Bean。因此条件成立时仍恰有一个 `FixturePlugin` 和一个 `DatasetAdapter`，不存在 factory Bean；默认、production、缺属性或 false 仍无任何 fixture Bean。

fixture 模块依赖和 ArchUnit 规则不变：继续只消费 plugin-api、core 和 Spring Boot autoconfigure，不依赖 Tushare/app。app 生产 JAR 继续不包含 fixture 类或资源。

### 直接输入与一致性

- M08-T01 的设计 `docs/task-designs/M08-T01-design.md`、实现提交 `79cc80d` 和完成证据提交 `6ee1fbd` 提供唯一 definition、双条件配置、精确 descriptor/API/scenario 元数据、真实 `GenericDatasetAdapter` Bean、临时下载接缝与收窄后的模块边界；本任务只替换临时 unavailable 行为，不改元数据或适配路径。
- M08-T01 已直接消费 M02 的 `DownloadEnvelope`/`SourceException`、M04 fixture 表和 M05 `GenericDatasetAdapter`。本任务通过该已完成边界构造合法成功/空包络、固定来源异常和两种下游故障输入，不修改任何公共类型或复制其职责。

这些输入无冲突：五值 enum 与既有参数 allowed values 同序一致；类型失败故意由公共 adapter 识别，写入失败标记故意留给后继 test-scope 故障注入，来源失败则在产生包络前使用公共来源异常。

## Files

修改：

- `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`：注入工厂、切换可下载 readiness、安全选择场景并一次委托。
- `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java`：只在现有插件 Bean 内构造工厂，保持双条件和两 Bean 边界。
- `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java`：更新构造器/readiness 断言并覆盖配置与插件场景路由。

创建：

- `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureScenario.java`：五值场景 enum。
- `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureEnvelopeFactory.java`：无状态确定性包络/来源失败工厂。
- `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixtureEnvelopeFactoryTest.java`：六项场景、公开表面和真实 adapter 结果测试。

不删除文件。实现提交消息固定为 `feat(fixture): provide deterministic acceptance scenarios`，提交精确包含上述六个文件；三个新文件全部加入 Git，设计、交接、看板、`target/` 或其他文件不得混入实现提交。

## Tests

### 新鲜基线与严格 RED

实施前在允许 Mockito/Byte Buddy attach 的环境运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
```

预期 plugin-api 79、core 75、Tushare 93、fixture 6、app 13，共 266/266，0 failure、0 error、0 skipped，六层 Enforcer 与当前 ArchUnit 通过。受限 JVM 若只因既有 inline mock maker 无法附加而失败，原命令移至允许环境重跑，不修改或跳过测试。

先完整创建 `FixtureEnvelopeFactoryTest.java`，并把 `FixturePluginTest.java` 更新为新构造器、可下载 readiness 与场景委托期望；不创建两个新生产类型，也不修改两个既有生产类型。运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture -am \
  -Dtest=FixturePluginTest,FixtureEnvelopeFactoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 fixture `testCompile` 因 `FixtureScenario`、`FixtureEnvelopeFactory` 和扩展后的插件构造器/行为尚不存在而非零退出；POM、上游编译、测试语法和既有资源不得先失败。该失败作为可归因 RED。

### 聚焦 GREEN 与行为覆盖

创建两个新生产类型并最小修改插件/配置后重跑相同聚焦命令。预期 `FixturePluginTest` 保持六项普通 `@Test`，`FixtureEnvelopeFactoryTest` 恰有六项普通 `@Test`，合计 12/12，0 failure、0 error、0 skipped：

1. 工厂测试确认 enum 五值/顺序、工厂 final、public 无参构造器、唯一 `create(FixtureScenario,Map)` 方法及 null 边界；
2. `SUCCESS` 的身份、同一参数内容、字段顺序、单行原始值与真实 adapter 转换结果精确；
3. `EMPTY` 为带完整字段的合法零行成功包络，真实 adapter 返回空批次；
4. `SOURCE_FAILURE` 产生精确安全 `SOURCE_UNAVAILABLE`、message、retryable，且无失败/半包络；
5. `TYPE_FAILURE` 包络本身合法，真实 adapter 精确产生 amount 的 `ADAPTER_TYPE_INVALID`，无部分批次；
6. `PERSISTENCE_FAILURE` 为合法可适配行并原样保留固定 note 标记，不在工厂/adapter 层失败。

既有 `FixturePluginTest` 同时更新并证明：新构造器/null 工厂边界；descriptor 与 readiness 已可下载；Java/YAML 元数据未漂移；配置仍只有插件/adapter 且使用真实通用适配；非 acceptance/禁用矩阵不变；插件对五个合法值正确路由，对 null/未知 API 及缺失、非字符串、未知场景使用固定安全边界。

测试只使用 JUnit 5、AssertJ、真实 Spring context、真实 factory、真实 `GenericDatasetAdapter` 和公共值对象；不使用 Mockito、数据库、网络、时钟、随机数、日志断言或 fixture 专用 adapter。

### 模块、完整 reactor 与静态门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture,tensor-app -am verify
```

第一条预期 plugin-api 79、core 75、fixture 12，共 166/166；后两条预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 13，共 272/272。全部退出 0，0 failure、0 error、0 skipped，六层 Enforcer 与 ArchUnit 通过。

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-fixture -am dependency:tree \
  -Dincludes=com.akkc.tensor:tensor-core,org.springframework.boot:spring-boot-autoconfigure
jar tf data-plane/tensor-plugin-fixture/target/tensor-plugin-fixture-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/plugin/fixture/(FixturePlugin|FixtureConfiguration|FixtureScenario|FixtureEnvelopeFactory)|datasets/fixture/fixture_daily.yaml'
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/plugin/fixture|datasets/fixture'
rg -n 'plugin\.tushare|tensor-plugin-tushare|RestClient|ServiceLoader|java\.sql|javax\.sql|JdbcTemplate|Flyway|DataSource|Connection|Instant\.now|Clock|Random|Logger' \
  data-plane/tensor-plugin-fixture/src/main/java
rg -n 'FixtureScenario|FixtureEnvelopeFactory|SOURCE_UNAVAILABLE|not-a-decimal|PERSISTENCE_FAILURE|GenericDatasetAdapter|@Profile\("acceptance"\)|ConditionalOnProperty' \
  data-plane/tensor-plugin-fixture/src/main/java \
  data-plane/tensor-plugin-fixture/src/test/java
git diff --quiet -- \
  data-plane/pom.xml data-plane/tensor-plugin-api data-plane/tensor-core \
  data-plane/tensor-plugin-tushare data-plane/tensor-plugin-fixture/pom.xml \
  data-plane/tensor-plugin-fixture/src/main/resources data-plane/tensor-app
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short --untracked-files=all -- data-plane/tensor-plugin-fixture
```

依赖树保持 M08-T01 的两个 direct compile dependency 且无 Tushare/app；fixture JAR 命中四个生产类型和 YAML，app JAR 扫描无输出并退出 1；禁止能力扫描无输出并退出 1，授权扫描命中场景、工厂、固定失败/标记、通用适配与双条件；受保护路径及格式退出 0；clean 后 scoped status 精确显示三个修改和三个新文件且无 `target/`。

提交后运行相同完整 reactor `verify`，预期 272/272；`git show --stat --oneline --summary HEAD` 必须显示固定消息和精确六文件范围，`git diff --check HEAD^ HEAD`、clean 和最终 `git status --short` 均通过且无输出。

## Acceptance

- `FixtureScenario` 只有批准的五值，`FixtureEnvelopeFactory` 以单一无状态公开方法产生精确的 success/empty/type/persistence 包络或固定安全 source failure，不访问外部资源；
- success 的稳定值、empty 的合法零行语义、source 的安全 `SOURCE_UNAVAILABLE`、type 经真实 adapter 的 `ADAPTER_TYPE_INVALID`、persistence 的合法 note 标记均可观察且职责不串层；
- `FixturePlugin` 通过唯一新构造器注入工厂，readiness/descriptor 已可下载，合法场景只委托一次；null、未知 API 和无效场景均安全拒绝且不回显输入；
- `FixtureConfiguration` 继续只在 `acceptance + enabled=true` 下注册一个插件和一个真实通用适配器，不发布 factory Bean；默认、production、缺属性或 false 均无 fixture Bean；
- M08-T01 的 definition、YAML、适配器、依赖与 ArchUnit 边界不漂移，app 生产 JAR 继续不含 fixture；不存在数据库、网络、Tushare、日志、重试或 M08-T03 故障注入实现；
- 严格 TDD 取得可归因 RED 后聚焦 12/12、模块 166/166、完整 reactor `test`/`verify` 272/272、Enforcer、ArchUnit、依赖/JAR/静态/范围/格式/clean 门禁得到预期结果；
- 实现提交精确包含 Files 节三个修改和三个已跟踪的新文件，固定消息正确，工作树干净。

## Risks

- `PERSISTENCE_FAILURE` 的 note 值已成为 M08-T03 test-scope 故障注入接缝；后继只能在验收测试数据源中消费它，不得让生产持久化代码按业务值分支。
- 插件只接收 M05 参数校验后的精确 enum 字符串；直接绕过编排的缺失/非字符串/未知值使用固定本地异常，不得在插件中复制默认、别名或字段错误 DTO。
- `TYPE_FAILURE` 必须保持包络结构合法并只破坏 amount 值，否则不能证明真实 adapter 而非包络构造器承担类型失败。
- 完整 reactor 的既有 Mockito 测试需要允许 Byte Buddy JVM attach；受限环境的 attach 错误不能通过删测、skip core 或改动本任务范围规避。
