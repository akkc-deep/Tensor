# M07-T04 TushareProPlugin 描述符、readiness 和 49 接口下载——任务设计

任务编号：`M07-T04`
对应任务：[M07-T04](../superpowers/plans/tensor-modules/M07-tushare-plugin.md#task-m07-t04-tushareproplugin40h)
实施产物：元数据驱动的 `TushareProPlugin`、单 Bean Spring 装配和 49 接口插件测试

## Goal

在 Java 21 `tensor-plugin-tushare` 模块中交付实现 M02 `DataSourcePlugin` 的 `TushareProPlugin`。插件以 `tushare_pro` 作为唯一 ID，只把 M03 已验证的 49 份 `DatasetDefinition` 投影为 API/数据集描述符，以 M07-T01 本地配置产生 readiness，并按 `ApiName` 查找定义后委托 M07-T02/M07-T03 已完成的同步 `TushareProClient`。

插件在启用且 Token 已配置时允许下载；禁用或缺 Token 时仍注册并公开完整的非敏感描述信息，但在任何上游调用前以固定 `PLUGIN_DISABLED` 领域失败拒绝直接下载。Spring 配置只注册一个 `TushareProPlugin` Bean，同时启用 `TushareProperties` 绑定、创建既有客户端并从 classpath 加载 49 份元数据；配置与插件构造均不得联网。

## Scope

包含：

- 创建 public final `TushareProPlugin`，实现 M02 `DataSourcePlugin` 的 `descriptor()`、`readiness()` 和 `download(ApiName, Map<String, Object>)`；
- 固定 `pluginId = tushare_pro`、`displayName = "Tushare Pro"`、`description = "Tushare Pro 证券数据源"`，并把全部 49 个 `DatasetDefinition` 一对一投影为 `ApiDescriptor` 与 `DatasetKey`；
- 以不可变 `Map<ApiName, DatasetDefinition>` 完成下载查找，不使用 `switch`、API 名字符串分支或每接口客户端方法；
- 强制构造输入恰有 49 份定义；描述符继续由 M02 构造器拒绝重复 API、错误 plugin ID 或无对应 API 的 dataset；
- 复用 `TushareProperties.readiness()` 的 disabled、缺 Token、ready 真值表，并让 `PluginDescriptor` 的四个状态字段与该 readiness 完全一致；
- 用户于 2026-09-03 批准：disabled 或缺 Token 时直接调用 `download()`，统一抛出插件内 private static final `TensorException`，code 为 `PLUGIN_DISABLED`、message 固定为 `Tushare Pro download is unavailable`，cause 为 null、suppressed 为空，且不调用 client；
- 用户于 2026-09-03 批准：ready 状态下未知 `ApiName` 固定抛 `IllegalArgumentException("Unknown Tushare API")`，消息不得包含调用方输入，且不调用 client；
- 用户于 2026-09-03 批准：插件公开构造器精确为 `(TushareProperties, TushareProClient, List<DatasetDefinition>)`；Spring 配置使用 `@Configuration(proxyBeanMethods = false)`、`@EnableConfigurationProperties(TushareProperties.class)` 和一个 `TushareProPlugin` Bean 方法，在该方法内创建客户端并加载 classpath 元数据；
- 创建八项普通 JUnit 5/AssertJ/Mockito 测试，覆盖公开表面、Spring 上下文、49 描述符、三种 readiness、构造期零网络、不可用/未知拒绝和 `daily` 精确委托；
- 执行严格 TDD、三项受控 mutation、聚焦与 reactor 回归、M03 总契约、Enforcer、秘密/静态/范围/格式/清理及精确三文件提交门禁。

排除：

- 不修改 POM、plugin-api、core、app、fixture、M03 loader/YAML/总契约测试、M07-T01 properties/factory/test、M07-T02/M07-T03 client/classifier/validator/DTO/test、合同、数据库或前端；
- 不新增配置属性、公共异常类、第二套描述符/readiness/下载包络、Spring starter/autoconfigure、资源、依赖或额外生产/测试文件；
- 不执行参数语义校验、字段适配、类型转换、业务键生成、持久化、事务、查询、REST、健康检查或页面行为；
- 不在 disabled 时省略 Bean 或隐藏描述符；禁用和缺 Token 只禁止下载，不能阻止应用上下文创建或已入库数据查询；
- 不验证 Token 真实性，不在构造或 readiness 阶段调用 Tushare，不自动重试，不增加日志、MDC、指标、定时器或异步行为；
- 不读取或输出 Token，不把凭证值、配置路径、base URL、调用方未知 API 值或底层异常加入描述符、异常、日志或测试失败诊断；
- 不捕获、改写或包装 `TushareProClient` 的成功包络和 M07-T03 `SourceException`；上游分类结果必须原样传播。

## Approach

### 插件公开表面与不可变元数据

在 `com.akkc.tensor.plugin.tushare` 中冻结以下唯一公开插件表面：

```java
public final class TushareProPlugin implements DataSourcePlugin {
    public TushareProPlugin(
            TushareProperties properties,
            TushareProClient client,
            List<DatasetDefinition> definitions);

    @Override public PluginDescriptor descriptor();
    @Override public PluginReadiness readiness();
    @Override public DownloadEnvelope download(ApiName apiName, Map<String, Object> params);
}
```

类只有上述一个 public 构造器和三个 SPI public 方法，不增加 builder、factory、setter、重载或 public/protected 常量/helper。构造器用组件名 `properties`、`client`、`definitions` 拒绝 null；先以 `List.copyOf` 保存定义，定义数量不是 49 时固定抛 `IllegalArgumentException("definitions must contain exactly 49 datasets")`。定义元素、API 重复、plugin ID 和 API/dataset 一致性继续由映射过程及 M02 `ApiDescriptor`/`PluginDescriptor` 不变量拒绝，不吞掉或降级为部分描述符。

每个 `DatasetDefinition` 只按以下同位置字段投影，不读取模板、manifest、数据库或网络：

```text
ApiDescriptor(
    definition.datasetKey().apiName(),
    definition.displayName(),
    definition.category(),
    definition.queryMode(),
    definition.parameters())
dataset key = definition.datasetKey()
```

API 与 dataset 列表完整保留传入定义顺序；正式装配传入 M03 loader 已冻结的 `apiName` 升序不可变列表。插件同时创建不可变 `Map<ApiName, DatasetDefinition>` 供下载查找。不得按 API 名复制描述符数据、维护第二份 49 清单或特殊处理 `daily`。

构造器从 `properties.readiness()` 取得一次状态快照并创建不可变 `PluginDescriptor`：ID、名称和说明使用批准固定值，四个状态字段逐项取 readiness，APIs/datasets 使用上述 49 项投影。`descriptor()` 返回该不可变描述符；`readiness()` 每次直接返回 `properties.readiness()`。`TushareProperties` 本身不可变，因此二者状态相同；M05 `PluginRegistry` 仍可按其既有合同用当前 readiness 覆盖描述符快照。

### readiness、拒绝顺序与委托

`download` 的顺序固定为：

1. `Objects.requireNonNull(apiName, "apiName")` 与 `Objects.requireNonNull(params, "params")` 拒绝调用方 null；
2. 调用 `readiness()`；当 `downloadAvailable=false` 时立即抛新的 `PluginUnavailableException`，不查定义、不读取 Token、不调用 client；disabled 优先于未知 API；
3. 从不可变 map 查找 `apiName`；不存在时抛固定 `IllegalArgumentException("Unknown Tushare API")`，不得拼接或保存传入值；
4. 只调用一次 `client.execute(definition, params)` 并直接返回相同 `DownloadEnvelope`；不得复制 params、包络或定义，不捕获任何 `SourceException`，不重试。

`PluginUnavailableException` 是 `TushareProPlugin` 内唯一 private static final 嵌套异常，继承 `TensorException`，只有 private 无参构造器并固定调用：

```java
super(ErrorCode.PLUGIN_DISABLED, "Tushare Pro download is unavailable");
```

它不增加字段、cause、suppressed、公开访问器或动态消息。disabled 与缺 Token 的外部 readiness 原因仍分别为 M07-T01 固定的 `Disabled` 和 `Credentials missing`，但直接下载失败统一使用上述安全领域摘要。

### 单 Bean Spring 装配

在相同包中创建以下配置表面：

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TushareProperties.class)
public final class TusharePluginConfiguration {
    public TusharePluginConfiguration();

    @Bean
    public TushareProPlugin tushareProPlugin(TushareProperties properties);
}
```

配置类只有隐式 public 无参构造器和上述一个 public `@Bean` 方法，不持有字段，不注册第二个 `DataSourcePlugin`、client、factory、loader 或定义列表 Bean。Bean 方法按固定顺序执行本地装配：

1. `new TushareRestClientFactory().create(properties)` 创建已配置的同步 `RestClient`；
2. 用该 client 与同一 properties 创建 `TushareProClient`；
3. 用 `new DatasetDefinitionLoader().loadAll(new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml")` 加载 M03 定义；
4. 用批准的公开构造器创建并返回 `TushareProPlugin`。

无论 enabled 或 Token 状态如何，配置都注册插件 Bean，使 M05 注册表和后续元数据 API 可以展示来源及已入库数据。factory/client 构造只创建本地对象，loader 只读取打包 classpath；任何网络访问只能由后续显式 `download` 委托触发。

### 直接依赖与裁决比较

- M07-T02 的设计 `docs/task-designs/M07-T02-design.md` 与实现提交 `3244d92` 提供唯一公开 `TushareProClient.execute(DatasetDefinition, Map)`、真实 M03 definition 到精确上游请求的映射、严格响应验证和成功/合法空 `DownloadEnvelope`。本任务只按 `ApiName` 选择一份 definition 并委托，不修改请求、params、字段、row count 或包络。
- M07-T03 的设计 `docs/task-designs/M07-T03-design.md`、实现提交 `09c48c5` 和审查补强提交 `546f246` 把当前 client 的 HTTP、业务、transport 与 payload 失败冻结为七项固定安全 `SourceException`。本任务不捕获或转译这些上游失败；本地 unavailable 发生在 client 前并使用批准的 `PLUGIN_DISABLED`，未知 API 使用批准的固定 `IllegalArgumentException`，职责不重叠。
- M07-T02 已直接消费并验证 M03-T09 的 49 API/851 列 `DatasetDefinition` 输入；当前 `DatasetDefinitionLoader`、49 YAML 与永久总契约提供按 API 名排序、不可变且无重复的运行时列表。本任务复用同一公开 loader/pattern，不读 `docs/data-template`，不维护生产侧 49 名字符串分支。
- M07-T01 的不可变 `TushareProperties.readiness()`、同步 `TushareRestClientFactory` 和构造期零网络行为已由 M07-T02 直接消费并保持；本任务只将其组合进一个插件 Bean。用户的三个补充裁决冻结了现有文档未指定的 unavailable/unknown 失败、构造器/Bean 形状和描述符文案。

这些输入无冲突：M07-T02 提供成功委托边界，M07-T03 提供上游失败边界，M03 元数据提供完整查找表，M07-T01 提供本地 readiness/传输对象。M07-T04 只做不可变注册、前置可用性门禁和一次同步委托，不新增协议、分类或核心编排职责。

## Files

- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`：实现 M02 SPI、49 元数据投影/查找、readiness、批准的本地失败和 client 委托。
- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TusharePluginConfiguration.java`：启用 properties 绑定并通过一个 Bean 方法完成既有 factory/client/loader/plugin 本地装配。
- Create `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java`：覆盖精确表面、Spring 上下文、49 描述符、readiness、失败和委托。

实现提交只暂存上述三个新文件，提交消息固定为 `feat(tushare): register 49 API plugin`。设计、交接、看板、POM、既有源码/测试/YAML/合同、生成的 `target` 和其他模块不得混入实现提交。

## Tests

### 基线、RED 与 GREEN

实现前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
```

预期退出 0；plugin-api 79 项、tensor-plugin-tushare 85 项，共 164/164，0 failure、0 error、0 skipped，父项目/plugin-api/tushare 三层 Enforcer 通过且只有既有 platform-encoding 警告类别。

先完整创建 `TushareProPluginTest.java`，不创建两个生产类型，运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am \
  -Dtest=TushareProPluginTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `tensor-plugin-tushare:testCompile` 只因 `TushareProPlugin` 与 `TusharePluginConfiguration` 缺失而退出非 0；不得因测试语法、依赖解析、Spring、Mockito、元数据、真实网络或环境错误失败，作为可归因 RED。

创建两个最小生产类型后重跑同一命令，预期 `TushareProPluginTest` 恰有八个普通 `@Test`，8/8 通过：

1. 反射确认 plugin 是 public final、直接实现 `DataSourcePlugin`，只有批准的 public 构造器和三个 SPI 方法；配置是 public final，具有精确两项 Spring 注解、public 无参构造器和唯一 public `@Bean tushareProPlugin(TushareProperties)`；嵌套 unavailable 异常是 private static final、继承 `TensorException` 且无额外公开表面；
2. 用真实 loader 定义构造 ready plugin，descriptor 固定为 `tushare_pro`、`Tushare Pro`、`Tushare Pro 证券数据源`，四个状态与 readiness 相等；apis/datasets 恰有 49 项，每项 displayName/category/queryMode/parameters/DatasetKey 都与对应 definition 相等；
3. 使用不从被测 descriptor/YAML 生成的以下 API 名升序常量，断言 APIs 和 datasets 名称均完整同序相等，无缺失、多余或重复：`adj_factor, balancesheet, block_trade, broker_recommend, cashflow, daily, daily_basic, disclosure_date, dividend, express, fina_audit, fina_indicator, fina_mainbz, forecast, hk_hold, hs_const, hsgt_top10, income, index_classify, index_member, index_member_all, margin, margin_detail, moneyflow, moneyflow_hsgt, monthly, namechange, new_share, pledge_detail, pledge_stat, repurchase, share_float, slb_len, slb_sec, slb_sec_detail, stk_holdernumber, stk_holdertrade, stk_limit, stk_managers, stk_rewards, stock_basic, stock_company, suspend_d, top10_floatholders, top10_holders, top_inst, top_list, trade_cal, weekly`；传入 48 项时得到固定 definitions-count 失败；
4. 对 disabled+空 Token、disabled+测试 Token、enabled+空/blank Token、enabled+测试 Token 分别断言 M07-T01 真值表，且 descriptor 四个状态逐项匹配；任何字符串化表面不含 `m07-t04-secret-sentinel`；
5. 用 `AnnotationConfigApplicationContext` 在 refresh 前加入本地测试属性并注册配置类；缺 Token 与 disabled 两个上下文均成功创建，分别恰有一个 `TushareProperties` 和一个 `DataSourcePlugin`/`TushareProPlugin` Bean，描述符均含 49 项，refresh/close 期间没有上游访问；
6. disabled（有/无 Token）和 enabled+缺 Token 直接调用 `download` 均先得到 code `PLUGIN_DISABLED`、message `Tushare Pro download is unavailable`、retryable false、null cause、空 suppressed，且 Mockito client 零交互；
7. ready plugin 对 null apiName/params 分别得到组件名 `apiName`/`params` 的 `NullPointerException`；对 `ApiName.of("unknown_api")` 得到固定 `IllegalArgumentException("Unknown Tushare API")`，异常字符串不含 `unknown_api`，client 零交互；disabled+unknown 仍先得到 `PLUGIN_DISABLED`；
8. ready plugin 的 `daily` 调用只从 map 取得真实 daily definition，把同一个 params 实例传给 `client.execute` 恰一次，并直接返回 mock client 的同一个成功 `DownloadEnvelope`；client 抛出的一个 M07-T03 `SourceException` 也以同一实例传播，无包装、重试或额外交互。

Spring 上下文测试只使用 classpath、本地 properties 和 `.invalid`/回环不可达 base URL，不启动 server、不访问公网、不用 sleep。任何可能包含 sentinel、未知 API 或 URL 的断言先归约成 boolean/计数再使用固定说明，避免测试失败诊断回显输入。

### M03 总契约、mutation、回归与门禁

运行插件与 49 元数据总契约聚焦测试：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am \
  -Dtest=TushareProPluginTest,TushareMetadataContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期退出 0；TushareMetadataContractTest 的 50 次契约调用与插件八项测试共 58/58 通过，确认同一 49 API/851 列元数据总契约仍成立。

受控 mutation A：临时删除 definitions 恰 49 门禁或允许 48 项，重跑第 3 项，预期 48 项构造失败断言失败；恢复后 8/8。受控 mutation B：临时删除 readiness 前置拒绝或把缺 Token 视为可下载，重跑第 4/6 项，预期真值或 client 零交互断言失败；恢复后 8/8。受控 mutation C：临时把 map lookup 替换为 definitions 的第一项或捕获/包装 client 结果/异常，重跑第 8 项，预期 daily definition、同一结果/异常或恰一次交互断言失败；恢复后 8/8。mutation 不提交。

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am verify
```

两条命令均预期退出 0；plugin-api 保持 79 项，tushare 从 85 增至 93 项，共 172/172，0 failure、0 error、0 skipped，父项目/plugin-api/tushare 三层 Enforcer 通过且无新增警告类别。

运行静态、秘密、范围、格式和清理门禁：

```bash
rg -n 'switch|case|if \(.*apiName|daily|retrieve\(|exchange\(|Logger|MDC|System\.out|System\.err|Retry|retryWhen|Authorization|Cookie|token\(\)' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TusharePluginConfiguration.java
rg -n 'implements DataSourcePlugin|PluginId\.of\("tushare_pro"\)|Tushare Pro 证券数据源|definitions\.size\(\)|PluginReadiness|PLUGIN_DISABLED|Unknown Tushare API|client\.execute|@Configuration|@EnableConfigurationProperties|@Bean|DatasetDefinitionLoader|classpath\*:datasets/tushare_pro/\*\.yaml' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TusharePluginConfiguration.java
git diff --quiet -- data-plane/pom.xml data-plane/tensor-plugin-tushare/pom.xml \
  data-plane/tensor-plugin-api data-plane/tensor-core data-plane/tensor-app data-plane/tensor-plugin-fixture \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata \
  data-plane/tensor-plugin-tushare/src/main/resources \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata \
  data-plane/tensor-plugin-tushare/src/test/resources
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am clean
```

第一项预期无输出并退出 1，证明生产装配无 API 名特例、网络执行、Token 读取、日志或重试；第二项只显示授权的 SPI、固定描述符、49 门禁、readiness/失败、单次委托和 Spring 装配机制；受保护路径与格式退出 0；clean 成功。将最终聚焦测试和三项 mutation 的完整 Maven 输出分别保存为 `/private/tmp/m07-t04-focused.log`、`/private/tmp/m07-t04-mutation-count.log`、`/private/tmp/m07-t04-mutation-readiness.log`、`/private/tmp/m07-t04-mutation-delegation.log`，然后运行：

```bash
rg -n 'm07-t04-secret-sentinel|unknown_api' \
  /private/tmp/m07-t04-focused.log \
  /private/tmp/m07-t04-mutation-count.log \
  /private/tmp/m07-t04-mutation-readiness.log \
  /private/tmp/m07-t04-mutation-delegation.log
rm -f /private/tmp/m07-t04-focused.log \
  /private/tmp/m07-t04-mutation-count.log \
  /private/tmp/m07-t04-mutation-readiness.log \
  /private/tmp/m07-t04-mutation-delegation.log
```

`rg` 应无输出且退出 1；四个固定临时文件随后删除。clean 后提交前 Git 状态只能列 Files 节三个新 Java 文件。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确三文件范围，工作树干净。

## Acceptance

- `TushareProPlugin` 是唯一新增插件类型，以批准的 public 构造器实现精确 M02 SPI；`TusharePluginConfiguration` 以批准的注解和一个 Bean 方法注册它，不新增依赖、公共异常或平行模型；
- descriptor 固定为 `tushare_pro`、`Tushare Pro`、`Tushare Pro 证券数据源`，APIs/datasets 恰有批准的 49 个同序名称，所有展示、分类、查询模式和参数逐项来自对应 M03 definition；
- disabled、缺 Token、ready 的 readiness 和 descriptor 状态完全符合 M07-T01 真值表；任何配置状态都允许 Bean/描述信息创建，构造/readiness 不联网且不读取或输出 Token；
- definitions 不是恰 49 项时构造失败，不生成部分插件；生产实现只用不可变 map 查找，不按 API 名分支或维护第二份运行时 49 清单；
- unavailable 下载在 client 前统一抛批准的 `PLUGIN_DISABLED`/固定安全 message，未知 API 抛批准的固定 `IllegalArgumentException`，二者均无输入泄漏且无 client 交互；
- ready `daily` 下载只调用一次当前 `TushareProClient.execute` 并保持 definition、params、成功/空包络及 M07-T03 上游异常身份，不包装、修正、重试或生成半包络；
- 严格 TDD 得到只缺两个生产类型的可归因 RED 后，聚焦 8/8、插件+M03 契约 58/58、三类 mutation、reactor `test`/`verify` 172/172、三层 Enforcer、秘密/静态/范围/格式/清理和精确三文件提交门禁全部得到预期结果；
- 未修改 POM、既有生产/测试/YAML/合同或其他模块，未提前实现参数校验、适配、持久化、REST、健康、前端或自动重试职责。

## Risks

- 插件 Bean 有意在 disabled 或缺 Token 时仍创建；若误加 `@ConditionalOnProperty` 或在 Bean 方法内以 readiness 抛错，会使来源和已入库数据从注册表消失，违反 TRD 6.2 与 PRD 5.3。
- 构造期强制恰 49 项会使 classpath 元数据缺失、多余或重复时应用上下文失败；这是 PRD-F-004 和任务卡要求的 fail-fast 完整性门禁，不得降级为部分插件或在生产代码补默认定义。
- `PluginDescriptor` 缓存的是不可变 properties 的构造期状态；当前 `TushareProperties` 不支持运行时刷新。凭证或 enabled 配置变化需要重启应用，不能通过给插件增加可变状态绕过既有配置合同。
- Spring 配置依赖组件扫描发现具体插件模块；M09 应用装配若收窄扫描包，必须显式 import 该配置而不是复制 Bean 或在核心模块依赖具体插件。
