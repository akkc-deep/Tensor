# M07-T02 Tushare 请求、响应 DTO 和严格返回校验——任务设计

任务编号：`M07-T02`
对应任务：[M07-T02](../superpowers/plans/tensor-modules/M07-tushare-plugin.md#task-m07-t02-上游-dto解析和返回校验30h)
实施产物：Tushare 专用请求/响应 DTO、解析前响应体限长、严格返回校验、同步 `TushareProClient` 与真实 WireMock 测试

## Goal

在 Java 21 `tensor-plugin-tushare` 模块中交付唯一接触 Tushare Token 和上游 JSON 协议的同步 `TushareProClient`。客户端从已验证的 M03 `DatasetDefinition` 生成精确的 `api_name`、方法局部 Token、参数和逗号分隔字段请求，经 M07-T01 已配置的 `RestClient` POST 到上游；对 2xx 响应先实施 `maxResponseBytes` 有界读取，再严格解析业务码、`data.fields`、`data.items`、字段顺序和行宽，最终只为合法成功或合法空结果构造 M02 `DownloadEnvelope`。

非 2xx、无效 JSON、非零业务码和结构异常均以不含 Token、原始响应、上游消息、URI、字段值或堆栈的固定安全失败拒绝，不返回半包络。用户于 2026-09-03 批准：M07-T02 对非零业务码先使用固定脱敏通用失败；M07-T03 获准修改本任务的 client/validator，在原始 HTTP 状态或 code/msg 仍为方法局部值时立即映射成 M02 `SourceException`，不得把原始上游消息保存到异常或公共包络。

## Scope

包含：

- 创建包内可见的 Tushare 专用请求、响应和 data records；请求 JSON 字段精确为 `api_name`、`token`、`params`、`fields`，响应只消费 `code`、`msg`、`data.fields` 和 `data.items`；
- 请求 record 只在 `execute` 的出站序列化点短暂保存 Token，所有协议 DTO 的 `toString()` 使用固定脱敏文本，不输出请求或响应内容；
- 创建包内可见的 `TushareResponseValidator`，按 HTTP/JSON、业务码、data 节点、字段唯一与精确顺序、行宽、实际 row count 的顺序验证；
- 创建唯一公开的 final `TushareProClient`，由公开 `(RestClient, TushareProperties)` 构造器注入 M07-T01 产物，并公开任务卡固定的 `DownloadEnvelope execute(DatasetDefinition definition, Map<String, Object> params)`；
- 使用 `RestClient.exchange` 检查 HTTP 状态，并以 `InputStream.readNBytes(maxResponseBytes + 1)` 在 JSON 解析前实施结果级响应体上限；不得使用 `readAllBytes` 或先完整缓冲再比较长度；
- 使用 Jackson 严格重复键检测和尾随 token 检测解析响应；允许上游已知的 `request_id` 等非消费字段，不把它们加入 DTO 或公共结果；
- 使用真实 M03 `daily` 定义、M07-T01 properties/factory、WireMock 和 JUnit 5 覆盖成功、合法空、HTTP/JSON/业务/结构失败、响应限长、请求秘密边界和零半包络；
- 执行严格 TDD、两项核心 mutation、提交态模块回归、Enforcer、秘密/禁用 API/范围/格式/清理和精确六文件提交门禁。

排除：

- 不修改 POM、M07-T01 的 `TushareProperties`/`TushareRestClientFactory`/测试、M03 loader/YAML/测试、plugin-api、core、app、fixture、数据库、合同或前端；
- 不创建或使用 `TushareErrorClassifier`，不把 HTTP 状态、业务码或上游消息映射为 `ErrorCode`/`SourceException`，不决定 retryable；这些属于 M07-T03；
- 不实现 `TushareProPlugin`、Spring Bean 装配、描述符、readiness 拒绝或 49 接口注册/委托；这些属于 M07-T04；
- 不执行参数语义校验、适配、类型转换、业务键生成、去重、持久化、事务、查询、REST 或页面行为；
- 不把 Token 放入 URI、header、cookie、MDC、日志、异常、响应 DTO 的可输出字符串、公共 M02 类型或测试失败诊断；
- 不记录或传播原始 HTTP body、Tushare `msg`、Jackson/网络异常 cause、请求 JSON、字段/行实际值或其他不可信上游内容；
- 不引入异步、重试、压缩缓存、流式业务行处理、外部 SDK、替代 HTTP 客户端、新依赖、额外生产文件或测试资源。

## Approach

### 协议 DTO 与唯一公开表面

在 `com.akkc.tensor.plugin.tushare.client` 中创建以下精确类型形状：

```java
record TushareRequest(
        @JsonProperty("api_name") String apiName,
        String token,
        Map<String, Object> params,
        String fields) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TushareResponse(Integer code, String msg, TushareData data) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TushareData(List<String> fields, List<List<Object>> items) {}

final class TushareResponseValidator {
    static DownloadEnvelope validate(
            DatasetDefinition definition,
            Map<String, Object> params,
            TushareResponse response);
}

public final class TushareProClient {
    public TushareProClient(RestClient restClient, TushareProperties properties);
    public DownloadEnvelope execute(DatasetDefinition definition, Map<String, Object> params);
}
```

`TushareRequest`、`TushareResponse`、`TushareData` 和 `TushareResponseValidator` 顶层类型均不得声明 public；除 record 自动访问器和 validator 的 package-private static `validate` 外不增加可见方法。三个 DTO 均覆盖 `toString()`，分别固定返回 `TushareRequest[REDACTED]`、`TushareResponse[REDACTED]`、`TushareData[REDACTED]`。请求 compact constructor 对四个组件做非 null 校验并以 `Map.copyOf` 保存 params；不得 trim、改写或把 Token 复制到第二个字段。响应 DTO 保持 Jackson 输入形状，验证前不对 null、重复、行宽或业务码做隐式修正。

`TushareProClient` 只有两个 private final 实例字段：注入的 `RestClient` 与 `TushareProperties`；另有一个 private static final `ObjectMapper`。构造器拒绝 null，不执行网络、readiness、Token 有效性或元数据加载。mapper 使用 `JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)` 创建，启用 `DeserializationFeature.FAIL_ON_TRAILING_TOKENS`，并禁用 `MapperFeature.ALLOW_COERCION_OF_SCALARS`，使数字/布尔值与字符串之间的默认强制转换也按错误 JSON 类型拒绝；请求序列化和响应反序列化共用该 mapper，不接受外部可变 mapper 注入。

`TushareRequest` 使用 `@JsonPropertyOrder({"api_name", "token", "params", "fields"})` 冻结序列化顺序。`execute` 先以组件名 `definition` 和 `params` 拒绝这两个 null 调用方输入，再从 `definition.datasetKey().apiName().value()` 取得 `api_name`；validator 保留相同的包内防御性非 null 检查。客户端只在构造该方法局部 request 时调用 `properties.token().value()`，以原样 `params` 和 `definition.columns().map(ColumnDefinition::name)` 的逗号连接值构造请求。序列化失败只抛 `IllegalStateException("Tushare request cannot be encoded")`，不附 cause；序列化后的 byte array 直接作为 `application/json` POST body，唯一请求级 header 为 `Accept: application/json`，M07-T01 的 `User-Agent` 继续由 factory 提供。不得设置 Authorization、Cookie、Token header/query、日志 interceptor 或重试。

### HTTP、限长与 JSON 解析

`execute` 使用 `restClient.post().uri("").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(requestBytes).exchange(...)` 同步执行一次请求。exchange callback 首先读取 `response.getStatusCode()`：非 2xx 立即抛 `IllegalStateException("Tushare HTTP request failed")`，不得读取或保存错误响应 body。M07-T03 将按本次批准修改这一方法，在相同局部位置把 HTTP 状态交给 classifier；M07-T02 不提前分类。

2xx 响应通过 private static helper 从 `response.getBody()` 最多读取 `maxResponseBytes + 1` 字节。读取结果大于 `maxResponseBytes` 时抛 `IllegalStateException("Tushare response exceeds maxResponseBytes")`；等于上限合法。读取 I/O 失败抛 `IllegalStateException("Tushare response cannot be read")`，不附 cause。只有通过长度门禁的 byte array 才交给 ObjectMapper；空、截断、重复 JSON key、错误类型、语法错误或第二个根值统一抛 `IllegalStateException("Tushare response is invalid JSON")`，不附 Jackson cause、原 body 或解析位置。

测试用小型自定义 `maxResponseBytes` 验证边界，不分配 64 MiB fixture。精确上限用一个合法紧凑成功 JSON 的实际 UTF-8 长度配置并通过；超限用同一合法 JSON 追加一个空格得到 `max + 1`，必须在解析前失败。不得信任 `Content-Length` 代替实际有界读取。

### 有序返回校验与包络

`TushareResponseValidator.validate` 依次执行，前一步失败后不得继续观察后续内容：

1. `definition`、`params`、`response` 非 null；这三项是调用方编程错误，沿用 `Objects.requireNonNull` 的固定组件名；
2. `response.code()` 非 null，否则抛 `IllegalStateException("Tushare response code is missing")`；
3. code 非 0 时立即抛 `IllegalStateException("Tushare business request failed")`，不得把 code/msg 放入消息或异常字段；M07-T03 将获准在此局部位置读取 code/msg、立即分类并替换该通用失败；
4. `data` 非 null，否则抛 `IllegalStateException("Tushare response data is missing")`；
5. `fields` 非 null，否则抛 `IllegalStateException("Tushare response fields are missing")`；
6. `items` 非 null，否则抛 `IllegalStateException("Tushare response items are missing")`；
7. fields 含 null 或重复时抛 `IllegalStateException("Tushare response fields contain duplicates or null")`；
8. returned fields 必须与 `definition.columns()` 名称列表完整同序相等，否则抛 `IllegalStateException("Tushare response fields do not match dataset definition")`；M03-T09 已冻结 49/49 字段顺序，M05 adapter 也消费同序包络，因此不得只比较集合、排序、删除或重排字段/行；
9. 每个 items row 非 null，否则抛 `IllegalStateException("Tushare response row is missing")`；每行元素数必须等于 fields 数，否则抛 `IllegalStateException("Tushare response row width does not match fields")`；行内 null 单元格合法，保持上游语义；
10. 使用 `items.size()` 作为唯一 rowCount，构造 `DownloadEnvelope(definition.datasetKey().pluginId(), definition.datasetKey().apiName(), params, fields, items.size(), items, SUCCESS, null)`。

合法空响应必须仍携带与定义同序的非空 fields、`items=[]`、`rowCount=0` 和 `error=null`。validator 不构造 `FAILURE` 包络：任一失败均抛固定安全异常，避免把未分类失败伪装为 M02 空结果或传播半包络。`DownloadEnvelope` 负责最终不可变复制和公共形状不变量；validator 不维护第二套深复制实现。

### 直接依赖与裁决比较

- M03-T09 的实现提交 `36230d8` 与看板完成证据冻结 49 API、851 列、字段名称/顺序、参数、业务键和 filters 总契约；当前 YAML 和永久 `TushareMetadataContractTest` 相对该提交无差异。M07-T02 只消费传入 `DatasetDefinition` 的 API 名和 columns 顺序，不读取模板、manifest 或在生产代码加载 YAML。
- M07-T01 的实现提交 `06682a8` 及测试安全修复 `6e09e3a`、`e936287`、`9c49eb6` 提供注解绑定的 `TushareProperties`、唯一明文 `Credential.value()`、`maxResponseBytes`、同步 JDK `RestClient`、base URL、connect/read timeout、`Tensor/1.0` 和零重试；M07-T02 只在方法局部出站请求构造点读取 Token，并真正执行响应字节上限，不修改 factory/config/readiness。
- M02 `DownloadEnvelope` 已冻结成功/空结果、非空 fields、rowCount 与 data 大小、行宽及不可变性。M07-T02 只在上游成功完成全部验证后构造该类型，不创建平行公共包络。
- 用户批准的跨任务裁决允许 M07-T03 修改 `TushareProClient.java` 和/或 `TushareResponseValidator.java`：HTTP 状态、业务 code/msg 与 transport/parse failure 只能在方法局部交给 classifier，并立即转为固定安全 `SourceException`；原始 msg/body/cause 不得进入异常、日志或公共 DTO。M07-T02 当前只交付安全通用失败，不提前完成 M07-T03。

这些输入无冲突：M03 提供精确请求/返回字段顺序，M07-T01 提供安全配置与传输，M02 提供最终包络；本任务只连接这三项并保留后继错误分类的单一局部接缝。

## Files

- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRequest.java`：包内请求 record、精确 JSON 名称/顺序、params 复制和固定脱敏字符串。
- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponse.java`：包内 code/msg/data 响应 record，忽略非消费字段并固定脱敏字符串。
- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareData.java`：包内 fields/items record，忽略非消费字段并固定脱敏字符串。
- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponseValidator.java`：有序业务/结构校验和成功/空 `DownloadEnvelope` 构造。
- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`：唯一公开同步上游协议、方法局部 Token 注入、HTTP 门禁、有界读取与严格 JSON 解析。
- Create `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareProClientTest.java`：真实 M03/M07-T01/WireMock 协议、限制、安全和失败顺序测试。

实现提交只暂存上述六个新文件，提交消息固定为 `feat(tushare): validate upstream response envelopes`。设计、交接、看板、POM、M07-T01 文件、metadata/YAML/现有测试、生成的 `target` 和其他模块不得混入实现提交。

## Tests

### 基线、RED 与 GREEN

实现前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
```

预期退出 0；plugin-api 79 项、tensor-plugin-tushare 67 项，共 146/146，0 failure、0 error、0 skipped，三层 Enforcer 通过且只有既有 platform-encoding 警告类别。

先完整创建 `TushareProClientTest.java`，不创建五个生产类型，运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am \
  -Dtest=TushareProClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `tensor-plugin-tushare:testCompile` 只因 `TushareRequest`、`TushareResponse`、`TushareData`、`TushareResponseValidator` 和 `TushareProClient` 缺失而退出非 0；不得因测试语法、依赖解析、WireMock 启动、真实网络或环境错误失败，作为可归因 RED。

创建最小生产实现后重跑同一命令，预期 `TushareProClientTest` 恰有十个普通 `@Test`，10/10 通过：

1. 反射确认只有 `TushareProClient` 是 public final，只有公开 `(RestClient,TushareProperties)` 构造器与 `execute(DatasetDefinition,Map)`；四个协议/validator 顶层类型包内可见且形状、Jackson 注解、固定脱敏 `toString` 无额外 public API；
2. 真实加载 M03 `daily` 定义，经 M07-T01 factory 对 WireMock 同步 POST；捕获 JSON 精确只有同序 `api_name/token/params/fields`，Token 只在 body，URI/header/cookie 无 Token，返回含未知 `request_id` 的合法 2xx 后得到 `tushare_pro/daily`、原 params、11 个同序 fields、实际两行、`SUCCESS`、null error；
3. `code=0`、同序 fields、`items=[]` 得到合法成功空包络，rowCount 0 且不伪装失败；
4. 非 2xx 即使 body 是含秘密或畸形 JSON，也首先得到固定 `Tushare HTTP request failed`，异常/cause/测试诊断不含 status body、Token 或 URI，且不构造包络；
5. 2xx 的空 body、语法错误、重复 key、错误 JSON 类型和尾随第二根值均得到固定 `Tushare response is invalid JSON`，不附 cause、原 body或解析位置；
6. 缺 code 得到固定 code-missing；非零 code 同时缺 data 且 msg 含测试秘密时仍先得到固定 business failure，异常/cause/输出不含 code/msg/秘密；
7. success code 下按顺序验证缺 data、缺 fields、缺 items，各自得到对应固定消息，前序失败不被后序缺陷覆盖；
8. fields 中 null/重复、错误集合和同集合不同顺序分别得到固定 fields 失败；不得排序、去重或重排行数据；
9. items 含 null row 或任一短/长行分别得到固定 row 失败；合法行内 null 单元格被保留并可进入成功包络；
10. 合法紧凑 JSON 恰等于配置上限时通过；同一合法 JSON 追加一个空格成为上限加一时在解析前得到固定 oversize 失败，未调用 `readAllBytes` 或先完整解析。

所有 WireMock 使用 JUnit 5 extension 管理动态本地 HTTP 端口并在每项测试后关闭；stub 使用 catch-all 请求匹配，避免 Token/畸形 body 在 unmatched-request 诊断中回显。测试使用固定非真实秘密 `m07-t02-secret-sentinel`，所有可能接触 URI、header、body、DTO、异常和上游 msg 的断言先归约为 boolean/计数，再用常量说明断言；不得让 AssertJ/WireMock/Jackson 输出实际敏感值。

### Mutation、回归与门禁

受控 mutation A：临时把有界读取判定从 `> maxResponseBytes` 改成允许 `max + 1`，重跑第 10 项，预期超限断言失败且不输出 body；恢复后通过。受控 mutation B：临时跳过字段顺序比较或行宽比较，分别重跑第 8 或第 9 项，预期结构断言失败；恢复后 10/10。mutation 不提交。

再做秘密路径 mutation：临时把 Token 加入 URI/query 或让固定 business failure 携带上游 msg，重跑相应第 2/6 项；预期安全 boolean 断言失败，完整 Maven 输出扫描仍不得出现 `m07-t02-secret-sentinel`。恢复源码后重跑 10/10。

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am verify
```

两条命令均预期退出 0；plugin-api 保持 79 项，tushare 从 67 增至 77 项，共 156/156，0 failure、0 error、0 skipped，父项目/plugin-api/tushare 三层 Enforcer 通过且无新增警告类别。

运行静态、秘密、范围、格式和清理门禁：

```bash
rg -n 'readAllBytes|retrieve\(\)|Authorization|Cookie|requestInterceptor|requestInterceptors|Retry|retryWhen|System\.out|System\.err|Logger|MDC' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRequest.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponse.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareData.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponseValidator.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java
rg -n 'api_name|JsonPropertyOrder|STRICT_DUPLICATE_DETECTION|FAIL_ON_TRAILING_TOKENS|ALLOW_COERCION_OF_SCALARS|exchange\(|readNBytes|maxResponseBytes|items\.size\(\)|DownloadStatus\.SUCCESS|\[REDACTED\]' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/Tushare*.java
git diff --quiet -- data-plane/pom.xml data-plane/tensor-plugin-tushare/pom.xml \
  data-plane/tensor-plugin-api data-plane/tensor-core data-plane/tensor-app data-plane/tensor-plugin-fixture \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactory.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata \
  data-plane/tensor-plugin-tushare/src/main/resources \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactoryTest.java \
  data-plane/tensor-plugin-tushare/src/test/resources
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am clean
```

第一项预期无输出并退出 1；第二项只显示授权的协议、严格解析、有界读取、实际计数和脱敏机制；受保护路径与格式退出 0；clean 成功。将最终聚焦测试和响应上限、字段/行结构、秘密路径三类 mutation 的完整 Maven 输出分别保存为 `/private/tmp/m07-t02-focused.log`、`/private/tmp/m07-t02-mutation-limit.log`、`/private/tmp/m07-t02-mutation-structure.log` 和 `/private/tmp/m07-t02-mutation-secret.log`，然后运行：

```bash
rg -n 'm07-t02-secret-sentinel' \
  /private/tmp/m07-t02-focused.log \
  /private/tmp/m07-t02-mutation-limit.log \
  /private/tmp/m07-t02-mutation-structure.log \
  /private/tmp/m07-t02-mutation-secret.log
rm -f /private/tmp/m07-t02-focused.log \
  /private/tmp/m07-t02-mutation-limit.log \
  /private/tmp/m07-t02-mutation-structure.log \
  /private/tmp/m07-t02-mutation-secret.log
```

`rg` 应无输出且退出 1；四个固定临时文件随后删除。clean 后提交前 Git 状态只能列 Files 节六个新文件。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确六文件范围，工作树干净。

## Acceptance

- 唯一公开 `TushareProClient` 与四个包内协议/validator 类型具有设计冻结的最小表面；请求 JSON 精确包含同序 `api_name/token/params/fields`，Token 只在方法局部出站 body 构造点出现；
- 真实 M03 `DatasetDefinition` 决定 API 名和完整字段顺序，返回成功包络保留上游字段/行语义，rowCount 只取 `items.size()`，合法空结果保持非空 fields、零行与 null error；
- 非 2xx 在 JSON 前拒绝；2xx body 在解析前执行实际字节上限，恰等上限允许、上限加一拒绝；严格 JSON 拒绝空/畸形/重复 key/错误类型/尾随根值；
- 校验顺序固定为业务码、data、fields、items、字段 null/重复与同序定义、row null/宽度；任一失败不构造半包络、不排序或修复上游内容；
- 所有通用失败消息固定且不含 HTTP body、Tushare msg/code、Token、URI、字段/行实际值、解析位置或 cause；DTO 字符串固定脱敏，测试失败输出也不暴露秘密；
- 用户批准的 M07-T03 接缝被明确保留：后继可修改 client/validator，只在局部读取 status/code/msg 并立即映射 `SourceException`，本任务未提前实现分类；
- 严格 TDD 得到缺五个生产类型的可归因 RED 后，聚焦 10/10、三类 mutation、reactor `test`/`verify` 156/156、三层 Enforcer、秘密/静态/范围/格式/清理和精确六文件提交门禁全部得到预期结果；
- 未修改依赖、既有生产/测试、YAML/合同或其他模块，未提前实现 M07-T03/M07-T04、适配、持久化、REST 或前端职责。

## Risks

- `TushareRequest` 在方法局部短暂持有明文 Token 是向上游发送 JSON 的必要条件；固定 `toString()` 只能防止被动字符串化，不能阻止主动读取包内 accessor。实现和后继审查必须继续用静态扫描把生产 accessor 使用限制在唯一序列化路径。
- M07-T02 的固定通用失败有意不保留 HTTP 状态、业务 code/msg 或底层 cause；这是用户批准的阶段边界，不是最终领域错误体验。M07-T03 必须按已批准范围修改 client/validator，在这些值仍为局部变量时完成分类，否则不能实现鉴权、权限、限流、网络与超时区分。
- `readNBytes(max + 1)` 将单次最多保留 64 MiB 加一字节，符合首期上限但仍是显著堆内存；本任务不引入流式 JSON 行处理。后续性能验证必须在预期 JVM heap 下验证最大响应场景，不能提高上限或改成无界读取。
- 对 returned fields 使用完整同序比较比 TRD 7.3 的集合表述更严格，但它保持 M03-T09 的 851 列顺序并满足现有 `GenericDatasetAdapter` 的同序消费合同；若真实 Tushare 无视请求顺序，必须通过独立设计裁决，不能在客户端静默排序或重排行。
- WireMock 必须绑定本地回环端口；受限沙箱内若出现 `SocketException: Operation not permitted`，应在允许本地监听的测试环境重跑，不得把环境权限失败误判为实现失败或删除集成断言。
