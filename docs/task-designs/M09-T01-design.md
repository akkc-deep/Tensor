# M09-T01 Boot 入口、请求标识和通用 API DTO——任务设计

任务编号：`M09-T01`
对应任务：[M09-T01](../superpowers/plans/tensor-modules/M09-app-api.md#task-m09-t01-boot-入口请求标识与通用-dto25h)
实施产物：`tensor-app` 的 Spring Boot 入口、请求关联 Filter、两个通用错误 DTO 和唯一测试

## Goal

在 `tensor-app` 建立可启动的 Spring Boot 根入口和所有后续 `/api/v1` 请求共用的关联标识边界：每个请求在进入后续处理前获得规范小写 UUID，响应头和 MDC 在请求期间使用同一值，结束或异常后不在线程中残留。同步交付与 OpenAPI 对齐、不会泄漏异常内部状态的不可变错误 DTO，使后续 Controller、统一异常映射、日志和下载结果可以复用同一公共表面。

## Scope

包含：

- 创建 `TensorApplication` 作为 `com.akkc.tensor` 根包的标准 Spring Boot 入口；
- 创建最高优先级 `RequestIdFilter`，处理 `X-Request-Id`、MDC `requestId`、服务端 UUID 和无条件清理；
- 创建 `ApiErrorResponse` 与 `FieldErrorResponse` 两个 public records，冻结与 OpenAPI 相同的组件顺序、错误码类型、字段列表防御复制和输入不变量；
- 删除旧的 IntelliJ 示例入口 `data-plane/src/main/java/com/akkc/Main.java`；
- 创建唯一 `RequestIdFilterTest`，覆盖过滤器、DTO JSON/不可变性和排除尚未交付数据库配置后的 Boot context smoke test；
- 执行严格 RED/GREEN、模块 `test`/`verify`、Enforcer、ArchUnit、JAR、范围、格式、Git 跟踪和 clean 门禁。

排除：

- 不修改任何 POM、资源、配置、迁移、plugin-api/core/plugin 实现或已有测试；
- 不创建 Controller、下载编排、查询映射、全局异常处理器、Jackson 精度配置、日志/指标、健康检查、安全响应头或生产数据库配置，这些分别属于 M09-T02～T06；
- 不修改 `RequestId` 或增加字符串解析工厂，不接受任意 opaque 客户端标识、大小写规范化、trim 或请求 ID 指标标签；
- 不把请求 ID 写入异常对象，不在 DTO 中序列化 Throwable、cause、stack、SQL、内部路径、Token、原始上游响应或请求头；
- 不启动 MySQL、调用 Tushare、访问网络或把 fixture 加入生产上下文。

## Approach

### Boot 入口和模块边界

`data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java` 位于既有生产包共同根 `com.akkc.tensor`，类上只声明 `@SpringBootApplication`，`main(String[] args)` 只调用 `SpringApplication.run(TensorApplication.class, args)`。保持默认组件扫描，使后续 app、core 和 Tushare 配置可由应用根发现；不在入口中手工构造 registry、service、数据源、Filter 或插件，也不添加 profile、属性默认值和自动配置排除。

删除聚合目录外的旧 `com.akkc.Main` 示例。它不承担兼容入口，删除后生产 JAR 的唯一 main application 类型是 `TensorApplication`。

### 请求标识解析、传播和清理

`RequestIdFilter` 位于 `com.akkc.tensor.web`，是 `public final` 类，继承 `OncePerRequestFilter`，声明 `@Component` 与 `@Order(Ordered.HIGHEST_PRECEDENCE)`。公开两个唯一共享常量：

```java
public static final String HEADER_NAME = "X-Request-Id";
public static final String MDC_KEY = "requestId";
```

客户端值只通过 `request.getHeader(HEADER_NAME)` 读取一次。项目所有者批准的唯一沿用格式为精确 36 字符的规范小写 UUID：

```text
^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$
```

不得 trim、转小写或接受 uppercase、无连字符 UUID、普通 opaque ID、空白、超长值、控制字符或 CR/LF。匹配值用 `UUID.fromString` 构造现有 `RequestId` 并以 `requestId.value().toString()` 取得同一规范字符串；缺失或不匹配值调用 M02-T01 的 `RequestId.newId()`，由其生成 version 4、RFC 4122 variant 2 UUID。无效客户端原值不得进入响应、MDC、日志、异常或错误 DTO。

`doFilterInternal` 的固定顺序是：解析或生成 `RequestId`；把规范字符串写入 MDC；进入 `try` 后先用 `response.setHeader` 设置响应头，再调用 `filterChain.doFilter`；在唯一 `finally` 中调用 `MDC.remove(MDC_KEY)`。下游正常返回或抛 `IOException`、`ServletException`、runtime exception 时都执行同一清理。Filter 不恢复进入前的同名 MDC 值，避免线程池中的陈旧请求标识重新泄漏；请求期间若已有同名值则被当前请求覆盖，结束后为空。

不把 ID 另存为 Filter 实例字段、ThreadLocal、request attribute 或静态可变状态。`OncePerRequestFilter` 保持其默认 async/error dispatch 语义；MDC 的生命周期是当前处理线程上的一次请求过滤链，响应头在进入链前已经固定。

### 通用错误 DTO

两个类型位于 `com.akkc.tensor.web.dto`，使用 record 默认的值语义和 Jackson record 支持，不增加 Jackson annotation、builder、setter、工厂或 Throwable 字段：

```java
public record FieldErrorResponse(String field, String message) {}

public record ApiErrorResponse(
        String requestId,
        ErrorCode code,
        String message,
        boolean retryable,
        List<FieldErrorResponse> fieldErrors) {}
```

compact constructor 对引用组件先使用 `Objects.requireNonNull`；`requestId`、`message`、`field` 和字段级 `message` 若 `isBlank()` 则抛 `IllegalArgumentException`，但不 trim 或改写。`ApiErrorResponse.fieldErrors` 使用 `List.copyOf` 防御复制并拒绝 null 元素，空列表合法且始终序列化。`retryable` 必须精确等于 M02-T05 `ErrorCode.retryable()`，不允许 DTO 形成第二套漂移值。

`requestId` 保持 JSON string 而不是直接暴露 `RequestId` record，避免 Jackson 产生嵌套 `{value: ...}`；运行时调用者必须传入 Filter 的规范字符串。`code` 直接使用 `ErrorCode`，Jackson 序列化为冻结的 enum 名称。record component 顺序同时固定 JSON 的预期字段顺序：`requestId, code, message, retryable, fieldErrors` 和 `field, message`。

### 直接输入与约束比较

- M01-T03 提供六层 Maven Enforcer、四条生产包依赖规则和禁止 Git 能力扫描；app 可以依赖 core/plugin 模块，新增生产代码不得引入反向依赖或 Git/代码托管能力。
- M02-T01 提供只依赖 JDK 的 `RequestId(UUID)` 与无参 `newId()`；服务端生成必须复用该工厂，客户端沿用逻辑只存在于 app Filter，不反向扩展 plugin-api。
- M02-T05 提供 16 项闭集 `ErrorCode` 及唯一 retryable 真值；DTO 直接保存该 enum 并核对布尔值，不复制错误码、HTTP 映射或异常内部状态。

三项直接输入职责互补且无冲突：M01 控制模块和能力边界，M02-T01 控制请求 UUID 身份，M02-T05 控制错误身份与 retryable。客户端 UUID 白名单只决定 Filter 是否沿用请求头，不改变 `RequestId` 或 `ErrorCode` 的公共合同。

## Files

创建：

- `data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java`：Spring Boot 根入口。
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java`：请求 ID 校验、生成、响应头、MDC 生命周期。
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java`：统一错误响应 record。
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java`：字段错误 record。
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RequestIdFilterTest.java`：本任务唯一测试。

删除：

- `data-plane/src/main/java/com/akkc/Main.java`：无业务语义的旧示例入口。

不修改其他文件。实现提交消息固定为 `feat(app): bootstrap Tensor and request correlation`，提交精确包含五个新增 Java 文件和一个删除文件；设计、交接、看板、POM、资源、已有测试、其他任务或 `target/` 不得混入实现提交。

## Tests

### 严格 RED 与聚焦 GREEN

先完整创建 `RequestIdFilterTest.java`，包含本节全部测试和对四个待创建生产类型的直接引用，但不创建生产类型，也不删除旧 Main。运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=RequestIdFilterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `tensor-app:testCompile` 仅因 `TensorApplication`、`RequestIdFilter`、`ApiErrorResponse` 和 `FieldErrorResponse` 不存在而非零；不得先出现测试语法、依赖解析、MDC/Servlet API 或既有代码失败。该缺失交付物失败是本任务 RED。

`RequestIdFilterTest` 只使用现有 JUnit Jupiter、AssertJ、Mockito、Jackson 与 Spring Boot runtime API，不新增 Spring Test 依赖。通过 mocked `HttpServletRequest`、`HttpServletResponse`、`FilterChain` 调用 Filter 的 public `doFilter`，固定为 11 次测试执行：

1. 一个合法小写 UUID 原样成为链内 MDC 和响应头，链返回后 MDC 为空；
2. 缺失请求头生成不同的规范小写 UUID，并断言 version 4、variant 2；
3. 六项参数化非法值分别为 blank、uppercase UUID、普通 opaque ID、32 位无连字符值、CR/LF 日志注入值和合法 UUID 后追加字符；每项都被新 UUID 替换，非法原值不出现在响应或 MDC；
4. 预置陈旧 MDC 后，下游抛 `ServletException`，当前请求 ID 在链内覆盖旧值、响应头已设置，异常原样传播且 finally 后 MDC 为空；
5. DTO 测试验证组件顺序、null/blank 拒绝、retryable 漂移拒绝、字段列表防御复制/不可修改，以及 Jackson tree 精确只有 OpenAPI 字段和值；
6. 使用 `SpringApplicationBuilder(TensorApplication.class).web(WebApplicationType.NONE)` 启动并关闭 context，断言入口和 Filter Bean 存在。测试属性只排除 `DataSourceAutoConfiguration` 与 `FlywayAutoConfiguration`，因为数据库配置属于 M09-T06；生产入口本身不声明排除。测试同时禁用 Tushare 下载但不得访问网络。

完成四个生产类型并删除旧 Main 后重跑聚焦命令，预期 11/11，0 failure、0 error、0 skipped，命令退出 0。

### Reactor、JAR 与边界门禁

当前提交态默认 reactor 基线为 plugin-api 79、core 75、Tushare 93、fixture 12、app 13，共 272/272。新增 11 次测试执行后运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

两条命令均预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 24，共 283/283，0 failure、0 error、0 skipped；六层 Enforcer 与 `tensor-app` 的 ArchUnit/禁止 Git 能力测试通过。`FixtureFlowIT` 和 `FlywaySchemaContractIT` 仍因 `*IT` 命名不进入默认 Surefire 计数，本任务不重复 M08 的 MySQL 验收。

运行静态和提交范围门禁：

```bash
test ! -e data-plane/src/main/java/com/akkc/Main.java
rg -n '@SpringBootApplication|SpringApplication\.run|OncePerRequestFilter|Ordered\.HIGHEST_PRECEDENCE|X-Request-Id|requestId|RequestId\.newId|MDC\.put|MDC\.remove|List\.copyOf|ErrorCode' \
  data-plane/tensor-app/src/main/java
rg -n '@RestController|@RestControllerAdvice|DataSourceAutoConfiguration|FlywayAutoConfiguration|Throwable|StackTrace|JdbcTemplate|PluginRegistry|DatasetQueryService' \
  data-plane/tensor-app/src/main/java
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/(TensorApplication|web/RequestIdFilter|web/dto/ApiErrorResponse|web/dto/FieldErrorResponse)\.class'
git diff --quiet -- \
  data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/main/resources \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-fixture data-plane/tensor-plugin-tushare
git diff --check
git status --short --untracked-files=all -- data-plane
```

旧入口检查退出 0；授权符号扫描命中设计规定的入口、Filter、MDC、DTO 约束；禁用职责扫描无输出并退出 1；生产 JAR 精确命中四个新增生产类型且不含旧 `com/akkc/Main.class`；受保护路径和格式退出 0。提交前 scoped status 精确列出五个新增文件和一个删除文件且无 `target/`，所有新增文件必须已加入 Git。

实现提交后运行：

```bash
git show --stat --oneline --summary HEAD
git show --format= --name-status HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
```

提交消息与六文件范围精确匹配，`clean` 退出 0，最终工作树为空且不存在 `target/`。

## Acceptance

- `TensorApplication` 是 `com.akkc.tensor` 根包的标准 Boot 入口；旧示例 Main 已删除，测试排除项没有进入生产入口；
- `RequestIdFilter` 只沿用规范小写 UUID，其他客户端值一律由 `RequestId.newId()` 替换；响应头与链内 MDC 始终相等，无效原值不传播；
- Filter 在进入链前设置 `X-Request-Id`，正常或异常结束均删除 MDC，且不恢复陈旧同名值、不保存静态可变请求状态；
- 两个 DTO 的 record component、Jackson 字段、`ErrorCode`、retryable、不空文本和不可变字段列表与 OpenAPI/M02-T05 一致，无异常内部状态或敏感字段；
- 严格 RED 只来自四个缺失交付类型；GREEN 聚焦 11/11、默认 reactor `test`/`verify` 283/283、Enforcer、ArchUnit、JAR、生产职责、范围、格式、Git 跟踪与 clean 门禁得到预期结果；
- 实现提交只包含五个新增 Java 文件和旧 Main 删除，消息为 `feat(app): bootstrap Tensor and request correlation`，不混入 M09-T02～T06、POM、配置、数据库或其他任务。

## Risks

- 客户端请求 ID 被刻意收窄为规范小写 UUID；使用 uppercase UUID 或其他 tracing 格式的调用方会收到新服务端 UUID。该行为是项目所有者为保持 `RequestId`、MDC、响应头和后续 `DownloadResult` 一致而批准的合同，不得在实施时放宽。
- `OncePerRequestFilter` 的默认 async dispatch 行为意味着 MDC 只绑定当前处理线程和当前过滤链；本任务不引入跨线程上下文传播。若后续增加异步 Controller，必须另行设计 MDC 传播，不能用静态状态延长生命周期。
- smoke test 为隔离尚未交付的 M09-T06 数据库配置，只在测试 builder 中排除 JDBC/Flyway 自动配置；这证明本任务的 Boot 根和组件扫描可启动，不宣称无数据库配置的完整生产应用已可运行。
- Mockito/Byte Buddy 在受限沙箱可能不能 self-attach；应在允许 attach 的测试环境执行，不得把环境失败误判为代码 RED 或通过修改 POM扩大本任务范围。
