# M07-T01 配置属性和同步 RestClient——任务设计

任务编号：`M07-T01`
对应任务：[M07-T01](../superpowers/plans/tensor-modules/M07-tushare-plugin.md#task-m07-t01-配置与-restclient25h)
实施产物：注解绑定的 `TushareProperties`、同步 `TushareRestClientFactory`、真实 Binder/WireMock 测试，以及模块所需的最小 Spring Boot 配置属性依赖

## Goal

在 Java 21 `tensor-plugin-tushare` 模块中交付可由 Spring Boot 以 `tensor.plugins.tushare-pro` 前缀进行构造器绑定的安全配置对象，并创建使用 JDK HTTP 传输的同步 Spring `RestClient`。配置具有权威默认值、严格边界、缺失 Token 时不阻止应用创建的 readiness 语义和不会输出明文的凭证值对象；客户端固定连接/读取超时与 `User-Agent: Tensor/1.0`，不携带 Token、不访问网络完成构造，也不自动重试，为 M07-T02 的唯一出站请求构造和响应体限制提供稳定输入。

## Scope

包含：

- 用户于 2026-09-03 明确批准采用注解方式，因此在 `tensor-plugin-tushare` POM 中新增由现有 Boot BOM 管理版本的 `org.springframework.boot:spring-boot` 最小编译依赖；
- 创建带 `@ConfigurationProperties("tensor.plugins.tushare-pro")` 的不可变 `TushareProperties` record，构造器绑定 `enabled`、`baseUrl`、`token`、`connectTimeout`、`readTimeout`、`maxResponseBytes`；
- 使用 `@DefaultValue` 冻结 true、官方 HTTPS 地址、空凭证、5 秒、120 秒和 64 MiB 默认值，并验证 URI、时长和字节上限；
- 在 `TushareProperties` 内创建可由单个配置标量转换的公开嵌套 `Credential` record；其 `toString()` 固定脱敏，并由配置派生 M02 `PluginReadiness`；
- 创建只返回同步 `RestClient` 的 final factory，使用 JDK `HttpClient`/`JdkClientHttpRequestFactory` 配置连接与读取超时、基地址和固定 User-Agent；
- 使用 Spring Boot `Binder` 与 WireMock 覆盖真实默认/覆盖绑定、验证、readiness、脱敏、基地址、超时、User-Agent 和零自动重试；
- 执行严格 TDD、模块回归、Enforcer、依赖树、两项受控 mutation、秘密/禁用 API/范围/格式/清理和精确四文件提交门禁。

排除：

- 不注册 `TushareProperties` Bean，不创建 `TusharePluginConfiguration` 或应用 `@EnableConfigurationProperties`；Spring 装配属于 M07-T04；
- 不创建 Tushare 请求/响应 DTO、`TushareProClient`、响应读取、64 MiB 流量截断、JSON 校验或 row count；这些属于 M07-T02，当前任务只校验并携带 `maxResponseBytes`；
- 不实现鉴权、权限、限流、5xx、网络、超时或 payload 的领域错误分类，不创建 `SourceException`；这些属于 M07-T03；
- 不实现 `DataSourcePlugin`、描述符、49 接口注册/委托或下载拒绝；这些属于 M07-T04；
- 不把 Token 放入默认 header、URI、日志、异常、MDC、公共 plugin-api 类型、响应 DTO 或测试失败文本；
- 不引入 Spring Boot starter、autoconfigure、configuration processor、WebClient、RestTemplate、Apache/OkHttp 客户端、重试库、调度器、异步客户端、额外生产类型或测试资源；
- 不修改父 POM、plugin-api、core、app、fixture、YAML、数据库迁移、合同或其他任务文件。

## Approach

### 注解绑定与唯一公开配置合同

在 `com.akkc.tensor.plugin.tushare.config` 中冻结以下唯一公开类型形状：

```java
@ConfigurationProperties("tensor.plugins.tushare-pro")
public record TushareProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://api.tushare.pro") URI baseUrl,
        @DefaultValue("") Credential token,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("120s") Duration readTimeout,
        @DefaultValue("67108864") int maxResponseBytes) {

    public PluginReadiness readiness();

    public record Credential(String value) {
        public boolean configured();
        @Override public String toString();
    }
}
```

不得增加 setter、builder、额外构造器、可变集合、公开常量或其他 public/protected 方法。Boot 3.5.16 `Binder` 已经由无仓库改动探针证实：`@DefaultValue("")` 可以构造空 `Credential`，单个 `tensor.plugins.tushare-pro.token=<value>` 标量可以调用其 `String` canonical constructor，因而无需自定义 converter、configuration processor 或 JavaBean 可变绑定。

canonical constructor 对 `baseUrl`、`connectTimeout`、`readTimeout` 做非 null 校验；null `token` 归一为 `new Credential("")`，使直接构造或缺失凭证不会导致应用失败。`baseUrl` 必须是具有 host 的绝对 `http` 或 `https` URI，且不允许 user-info、query 或 fragment；HTTP 只用于受控本地替身，部署配置继续遵守 TRD 的 HTTPS 地址要求。错误消息固定为 `baseUrl must be an absolute HTTP(S) URI without credentials, query, or fragment`，且不得拼入原始 URI。

`connectTimeout` 与 `readTimeout` 必须严格大于零；`readTimeout` 不得超过权威上限 120 秒。`maxResponseBytes` 必须在 `1..67108864`。固定消息分别为 `connectTimeout must be positive`、`readTimeout must be positive and at most 120 seconds`、`maxResponseBytes must be between 1 and 67108864`；消息不包含配置值。连接超时允许正值覆盖，响应超时和最大响应体只允许从权威上限下调。

`Credential` 把 null value 归一为空字符串，不 trim、不复制到其他字段；空或 `isBlank()` 为未配置，其他任意原始值为已配置。`configured()` 只返回该判断；`value()` 是 record 生成的唯一明文访问器，专供后续 M07-T02 在构造出站 JSON body 的最小代码区使用。`toString()` 对空和非空值均逐字返回 `[REDACTED]`；外层 record 的生成 `toString()` 因而也不出现明文。类型不提供 `char[]`、日志、异常、header 或 URI 转换方法。

`readiness()` 每次根据当前不可变值创建 M02 `PluginReadiness`：

| enabled | credential configured | downloadAvailable | unavailableReason |
|---|---|---|---|
| false | false/true | false | `Disabled` |
| true | false | false | `Credentials missing` |
| true | true | true | null |

disabled 优先于缺凭证原因，但 `credentialConfigured` 仍反映凭证真实配置状态。该方法不访问网络，不验证 Token 有效性，也不返回 Token 或配置路径。

### 同步 RestClient

在 `com.akkc.tensor.plugin.tushare.client` 中冻结以下唯一公开表面：

```java
public final class TushareRestClientFactory {
    public TushareRestClientFactory();
    public RestClient create(TushareProperties properties);
}
```

`create` 先拒绝 null properties，然后用 `HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build()` 创建 JDK 同步底层客户端，以其构造 `JdkClientHttpRequestFactory` 并调用 `setReadTimeout(properties.readTimeout())`。随后使用新的 `RestClient.builder()` 配置 `baseUrl(properties.baseUrl())`、唯一默认 header `User-Agent: Tensor/1.0` 和该 request factory，再返回 `build()` 结果。

factory 可以保留一个 package-private static `HttpClient createHttpClient(Duration)` helper，使测试通过标准 `HttpClient.connectTimeout()` 直接验证连接超时；helper 不得成为 public/protected API。不得调用 `requestInterceptor`/`requestInterceptors`，不得实现状态码或 I/O 重试，且不得配置 Authorization、Cookie、Token header、query parameter、日志 interceptor 或默认请求 body。创建 properties、factory 或 client 均不得发起网络请求；只有调用返回的 `RestClient` 才同步执行网络 I/O。

factory 不读取 `properties.token()`、`enabled()`、`readiness()` 或 `maxResponseBytes()`。Token 将由 M07-T02 的 `TushareProClient` 只在出站 JSON body 构造点读取；M07-T02 同时负责在 JSON 解析前按 `maxResponseBytes` 拒绝超限响应。本任务不能用 converter 缓冲设置伪装该尚未实现的结果级限制。

### 直接依赖与约束比较

- M02-T05 的提交 `445b941` 与修复提交 `dd495ee` 提供稳定 `DataSourcePlugin` SPI、`PluginReadiness` 引用和安全领域错误边界。M07-T01 只通过 `TushareProperties.readiness()` 消费 `PluginReadiness`，不修改 framework-free plugin-api，也不提前使用来源异常。
- M02-T05 延续 M02-T02 的决策：readiness 只能暴露 enabled、credentialConfigured、downloadAvailable 和安全原因，不能携带凭证值或路径；本任务的嵌套 `Credential` 留在具体插件模块，并只把布尔配置状态投影到该公共类型。
- TRD 7.1～7.2、14.1～14.2、16 与附录 B 冻结前缀、默认地址、超时、最大响应、User-Agent、零自动重试、Token 注入与脱敏边界；用户批准的注解绑定补足了原任务卡与现有 POM 的材料性缺口。

这些输入无冲突：plugin-api 保持无 Spring 依赖；具体 Tushare 模块新增 Boot 核心依赖以完成真实注解绑定；配置/readiness 与同步传输属于本任务，协议 body、响应限制执行和错误分类仍留在后继任务。

## Files

- Modify `data-plane/tensor-plugin-tushare/pom.xml`：新增 BOM 管理且不写版本的 `org.springframework.boot:spring-boot` 编译依赖；不增加 starter、autoconfigure、processor 或测试依赖。
- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config/TushareProperties.java`：提供注解构造器绑定、默认值、验证、脱敏凭证和 M02 readiness 投影。
- Create `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactory.java`：创建带 JDK 连接/读取超时、基地址和固定 User-Agent 的同步 `RestClient`。
- Create `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactoryTest.java`：验证公开表面、真实 Binder、配置安全边界和 WireMock 客户端行为。

实现提交只暂存上述四个文件，提交消息固定为 `feat(tushare): configure secure upstream client`。任务设计/交接、生成的 `target`、其他 POM、既有 Java/YAML/合同和其他模块不得混入实现提交。

## Tests

### 基线、RED 与 GREEN

实现前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
```

预期退出 0；plugin-api 79 项、tensor-plugin-tushare 58 项，共 137/137，0 failure、0 error、0 skipped，三层 Enforcer 通过且只有既有平台编码警告类别。

先只修改获批的模块 POM 并完整创建 `TushareRestClientFactoryTest.java`，不创建两个生产类；POM 变更只使完整测试能够编译 Boot Binder API。运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am \
  -Dtest=TushareRestClientFactoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `tensor-plugin-tushare:testCompile` 仅因 `TushareProperties` 与 `TushareRestClientFactory` 缺失而退出非 0；不得因测试语法、依赖解析、WireMock 启动或环境错误失败，作为可归因 RED。

创建最小生产实现后重跑同一命令，预期 `TushareRestClientFactoryTest` 恰有九个普通 `@Test`，9/9 通过：

1. 反射确认 `TushareProperties` 是带精确六组件和正确 `@ConfigurationProperties` 前缀的 public record，`Credential` 是 public nested record，factory 是 final 且只有公开无参构造器和 `create(TushareProperties)`；不存在 setter、额外 public/protected 方法或构造器；
2. 空 `MapConfigurationPropertySource` 经 Boot `Binder.bindOrCreate` 得到 true、`https://api.tushare.pro`、空凭证、5 秒、120 秒、67108864，并证明缺 Token 不抛异常；
3. kebab-case 属性将 false、自定义本地 base URL、测试 Token、2 秒、30 秒和 1048576 全部覆盖到精确 Java 值，证明单标量 Token 到 `Credential` 的真实构造器绑定；
4. 直接构造与 Binder 分别拒绝 null/相对或带 user-info/query/fragment 的 URI、非正时长、超过 120 秒的 read timeout、非正或超过 64 MiB 的响应上限，固定消息不含无效 URI或测试秘密；
5. disabled（有/无 Token）、enabled+缺 Token、enabled+Token 的 `PluginReadiness` 精确符合真值表，且创建 properties/factory/client 均不访问网络；
6. `Credential.toString()` 与外层 properties `toString()` 对测试秘密只出现 `[REDACTED]`；所有构造/绑定失败消息、客户端异常消息和测试捕获输出均不含秘密；
7. factory 对自定义 WireMock base URL 发出一次同步请求，server 观察到唯一 `User-Agent: Tensor/1.0`，URI/header 不含 Token，且 factory 未读取 Token 形成默认请求内容；
8. package-private helper 创建的 JDK `HttpClient.connectTimeout()` 等于覆盖值；WireMock 延迟响应超过 read timeout 时一次请求以内以 `ResourceAccessException` 失败，证明两项 timeout 都进入传输层；
9. WireMock 对 POST 返回 503 时 `RestClient` 传播标准非 2xx 异常且 server 恰收到一次请求，证明没有 interceptor 或应用自动重试。

测试使用固定非真实秘密 `m07-t01-secret-sentinel`，禁止在 assertion failure message 中拼接其实际值。WireMock 使用动态本地 HTTP 端口，仅验证允许的受控替身覆盖；不连接 Tushare、不依赖公网、环境变量、时钟或 sleep。服务端 fixed delay 只用于 read-timeout 行为，超时差至少一个数量级，避免边界抖动。

### Mutation、回归与门禁

受控 mutation A：临时让 `Credential.toString()` 返回明文或让外层字符串绕过该值对象，重跑第 6 项，预期秘密断言失败；恢复源码后通过。受控 mutation B：临时移除 JDK connect timeout 或 `JdkClientHttpRequestFactory.setReadTimeout`，分别重跑第 8 项，预期连接配置或延迟响应断言失败；恢复源码后 9/9 通过。mutation 不提交。

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am verify
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare dependency:tree \
  -Dincludes=org.springframework.boot:spring-boot,org.springframework:spring-web,org.springframework:spring-context
```

前两条均预期退出 0；plugin-api 保持 79 项，tushare 从 58 增至 67 项，共 146/146，0 failure、0 error、0 skipped，父项目/plugin-api/tushare 三层 Enforcer 通过且无新增警告类别。依赖树预期显示 Boot BOM 管理的 `spring-boot:3.5.16`、`spring-web:6.2.19` 和 spring-boot 传递的 `spring-context:6.2.19`，不出现 starter、WebFlux、Apache/OkHttp 或重试库。

运行静态、秘密、范围、格式和清理门禁：

```bash
rg -n 'RestTemplate|WebClient|@Value|Authorization|Cookie|requestInterceptor|requestInterceptors|Retry|retryWhen|System\.out|System\.err|Logger|MDC' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config/TushareProperties.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactory.java
rg -n '@ConfigurationProperties|@DefaultValue|JdkClientHttpRequestFactory|connectTimeout|setReadTimeout|USER_AGENT|Tensor/1\.0|\[REDACTED\]|PluginReadiness' \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config/TushareProperties.java \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactory.java
git diff --quiet -- data-plane/pom.xml data-plane/tensor-plugin-api \
  data-plane/tensor-core data-plane/tensor-app data-plane/tensor-plugin-fixture \
  data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata \
  data-plane/tensor-plugin-tushare/src/main/resources \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata \
  data-plane/tensor-plugin-tushare/src/test/resources
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am clean
```

第一项预期无输出并退出 1；第二项显示唯一允许的绑定、超时、User-Agent、脱敏和 readiness 机制；受保护路径与格式退出 0；clean 成功。另将最终聚焦测试的完整 Maven 输出保存到工作区外的临时日志，使用 `rg -n 'm07-t01-secret-sentinel' <log>` 验证无输出且退出 1，再删除临时日志。clean 后 Git 状态只能列 Files 节四个实现文件。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确四文件范围，工作树干净。

## Acceptance

- `TushareProperties` 由真实 Spring Boot 注解/Binder 以精确前缀和 kebab-case 属性完成不可变构造器绑定，六个组件、默认值、覆盖值、URI/时长/大小验证和公开表面与设计一致；
- 缺 Token 不阻止绑定、配置或 client 创建；disabled、缺凭证和 ready 三类 M02 `PluginReadiness` 结果准确，公共状态不携带凭证值或路径；
- Token 只保存在具体插件的 `Credential` 中，`toString`/外层字符串/固定错误/测试输出均不泄漏明文，factory 不读取或传输 Token；
- factory 返回配置官方或覆盖 base URL、JDK connect timeout、request read timeout 和唯一 `Tensor/1.0` User-Agent 的同步 `RestClient`，构造无网络副作用，503 和 timeout 均只产生一次上游请求；
- `maxResponseBytes` 正确绑定并限制为 1..64 MiB，同时明确由 M07-T02 在解析前执行结果级上限；本任务未伪装已完成响应限制；
- POM 只新增 BOM 管理的 `spring-boot` 核心依赖，无 starter/autoconfigure/processor/重试/替代 HTTP 客户端；plugin-api 仍无 Spring 依赖；
- 严格 TDD 得到缺两个生产类的可归因 RED 后，聚焦 9/9、两项 mutation、reactor `test`/`verify` 146/146、三层 Enforcer、依赖树、秘密/静态/范围/格式/清理和精确四文件提交门禁全部得到预期结果；
- 未提前实现 M07-T02～T04、修改既有 Java/YAML/合同或影响其他模块。

## Risks

- `Credential.value()` 必须公开，Boot 才能把单标量构造成值对象且 M07-T02 才能构造授权的出站 body；`toString()` 脱敏不能阻止调用者主动读取明文。后继实现必须把该 accessor 的生产使用限制在唯一出站 JSON 构造点，并继续通过静态与日志扫描守护边界。
- `@ConfigurationProperties` 只声明绑定合同，不自动注册 Bean；M07-T04 的 `TusharePluginConfiguration` 必须显式启用该类型，并保持本设计的默认值、验证和缺凭证 readiness，不得再创建平行配置模型。
- 本地 WireMock 需要 HTTP 覆盖，因此 URI 验证允许绝对 HTTP(S)；生产配置仍必须使用 TRD 指定的 HTTPS 地址。若后续要求代码强制生产 HTTPS，需要独立的环境策略设计，不能破坏本任务的确定性本地测试。
- JDK `HttpClient` 与 `RestClient` 没有应用级重试 interceptor；底层传输的协议级连接行为不等同于业务自动重试。本任务以 503 恰一次请求证明首期用户可观察的零自动重试合同。
- 64 MiB 限制仅在配置对象中完成绑定和边界验证；真正按读取字节拒绝超限响应是 M07-T02 的验收项，不能把本任务的配置存在误报为限制已经执行。
