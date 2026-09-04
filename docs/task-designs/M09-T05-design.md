# M09-T05 全局异常和 HTTP 状态映射——任务设计

任务编号：`M09-T05`
对应任务：[M09-T05](../superpowers/plans/tensor-modules/M09-app-api.md#task-m09-t05-全局异常与-http-状态20h)
实施产物：`GlobalExceptionHandler` 和 `GlobalExceptionHandlerTest`

## Goal

在 `tensor-app` 建立唯一的 Servlet 全局异常出口，把当前 Controller、Core、plugin 和 Spring MVC 抛出的失败稳定投影为既有 `ApiErrorResponse`。每个错误响应必须保持 `X-Request-Id` 与 body `requestId` 一致，严格遵循 M00 已冻结的 16 项错误码、HTTP 状态和 `retryable` 真值，同时阻止 SQL、Token、异常消息、堆栈和内部路径进入客户端响应。

## Scope

包含：

- 创建 `GlobalExceptionHandler`，以 `@RestControllerAdvice` 统一处理 `TensorException`、Core 参数字段错误、Bean Validation、MVC 输入绑定、值对象输入、持久化/查询失败和未知异常；
- 把全部 16 个 `ErrorCode` 映射到冻结的 400、409、422、500、502、504 状态，并使用 `ErrorCode.retryable()` 作为唯一重试真值；
- 把 M05 `ParameterValidationException` 的字段错误，以及当前 `DownloadRequest` Bean Validation 的结构错误，投影为安全、稳定的 `FieldErrorResponse`；
- 按当前固定 API 路径区分同步下载持久化失败、records 查询失败和其他内部失败；
- 使用固定客户端摘要，不把 Throwable 的消息、cause、请求参数、请求体、Header 或原始上游内容写入响应；
- 记录低信息量的安全日志：4xx 不带堆栈，5xx 只带固定字段和去除原消息/cause/suppressed 的堆栈副本；
- 使用 standalone MockMvc、真实 `RequestIdFilter`、真实 Bean Validation 和捕获日志完成固定 25 个测试 invocation；
- 执行严格 RED/GREEN、三项受控 mutation、聚焦/reactor 回归、静态、JAR、范围、格式、Git 跟踪和清理门禁。

排除：

- 不新增、删除或修改 `ErrorCode`，不修改 `docs/contracts/error-codes.md`、OpenAPI、TRD 或任何既有任务设计；
- 不实现 TRD 12.6 的宽泛 404/503 分类。M00-T03 已冻结的 16 项闭集没有 404/503 错误码，并明确要求后续如需该语义必须先独立修订契约；本任务不得把现有错误码改绑到 404/503；
- 不修改 `DataSourceController`、`DownloadController`、`DatasetController`、`RequestIdFilter`、既有 DTO、Core、plugin、POM、YAML、资源、迁移或现有测试；
- 不增加局部 `@ExceptionHandler`、第二套错误 DTO、Controller/service 包装异常或按异常消息分支；
- 不实现 M09-T06 的生产 Bean 总装配、指标、健康、操作日志、配置、静态资源或安全响应头；
- 不做国际化、客户端文案选择、重试、错误持久化、告警或远程日志传输。

## Approach

### Advice 公共表面与请求标识

`GlobalExceptionHandler` 位于 `com.akkc.tensor.web`，是声明 `@RestControllerAdvice` 和 `@ConditionalOnWebApplication(type = SERVLET)` 的 `public final` 类。它没有实例字段、公开构造器或外部协作者；唯一类字段是 `private static final org.slf4j.Logger`。异常处理方法保持 package-private，只返回 `ResponseEntity<ApiErrorResponse>`，不暴露新的公共 Java API。

所有响应通过同一个私有工厂形成。工厂从 `MDC.get(RequestIdFilter.MDC_KEY)` 读取请求 ID，要求该值非 null、非 blank，并用它构造 `ApiErrorResponse`。它不生成第二个 ID、不恢复旧 MDC，也不重复写响应 Header；M09-T01 的 `RequestIdFilter` 已在进入 DispatcherServlet 前设置同值 `X-Request-Id` 和 MDC，并在请求完成后清理。测试必须安装真实 Filter 并断言 Header/body 相同。

Advice 不读取请求参数、请求体或认证 Header。它只接收异常、必要的 `HttpServletRequest` 路由身份，以及 Spring 已解析的安全字段名。

### 领域错误映射与固定消息

`@ExceptionHandler(TensorException.class)` 只读取 `exception.code()`；HTTP 状态使用穷尽 `switch`，不得读取 `@ResponseStatus`、异常类名或异常消息决定状态：

| HTTP | `ErrorCode` |
|---:|---|
| 400 | `PARAM_REQUIRED`, `PARAM_INVALID` |
| 409 | `PLUGIN_DISABLED`, `DATASET_MISCONFIGURED` |
| 422 | `ADAPTER_FIELD_MISSING`, `ADAPTER_TYPE_INVALID` |
| 500 | `PERSISTENCE_FAILED`, `QUERY_FAILED`, `INTERNAL_ERROR` |
| 502 | `SOURCE_AUTH_FAILED`, `SOURCE_PERMISSION_DENIED`, `SOURCE_RATE_LIMITED`, `SOURCE_UNAVAILABLE`, `SOURCE_NETWORK_ERROR`, `SOURCE_PAYLOAD_INVALID` |
| 504 | `SOURCE_TIMEOUT` |

`ApiErrorResponse.retryable` 始终传入 `code.retryable()`。客户端 `message` 使用第二个穷尽 `switch` 的固定英文摘要，不返回 `TensorException.getMessage()`：

| `ErrorCode` | 固定 `message` |
|---|---|
| `PARAM_REQUIRED` | `Required parameters are missing` |
| `PARAM_INVALID` | `Parameters are invalid` |
| `PLUGIN_DISABLED` | `Plugin is unavailable` |
| `DATASET_MISCONFIGURED` | `Dataset metadata is unavailable` |
| `SOURCE_AUTH_FAILED` | `Source authentication failed` |
| `SOURCE_PERMISSION_DENIED` | `Source permission denied` |
| `SOURCE_RATE_LIMITED` | `Source rate limit exceeded` |
| `SOURCE_UNAVAILABLE` | `Source is unavailable` |
| `SOURCE_NETWORK_ERROR` | `Source network request failed` |
| `SOURCE_TIMEOUT` | `Source request timed out` |
| `SOURCE_PAYLOAD_INVALID` | `Source returned an invalid payload` |
| `ADAPTER_FIELD_MISSING` | `Source data is missing a required field` |
| `ADAPTER_TYPE_INVALID` | `Source data contains an invalid value` |
| `PERSISTENCE_FAILED` | `Persistence failed` |
| `QUERY_FAILED` | `Query failed` |
| `INTERNAL_ERROR` | `Internal server error` |

普通 `TensorException` 返回空 `fieldErrors`。若实例是 `ParameterValidator.ParameterValidationException`，则保持 Core `fieldErrors()` 顺序，把每个非空白 `field/message` 逐项构造为 `FieldErrorResponse`；不返回 raw 参数值，也不重新解释 M05 的参数规则。

### Bean Validation 与 MVC 输入失败

`@ExceptionHandler(MethodArgumentNotValidException.class)` 处理当前 `DownloadRequest` 的 `@Valid @RequestBody` 失败。Advice 只使用 Spring 字段名和约束 code，不使用 rejected value 或包含它的默认消息：

- `NotNull`、`NotBlank`、`NotEmpty` 统一为安全字段消息 `is required`；
- 其他约束统一为 `has invalid value`；
- 同一字段同时失败多个约束时只保留一项；任一非缺失约束失败优先使用 `has invalid value`；
- 字段错误按字段名排序，确保响应稳定；
- 所有失败都属于缺失约束时使用 `PARAM_REQUIRED`，否则使用 `PARAM_INVALID`。

MVC 输入异常使用独立、具体的 handler，避免进入未知异常分支：

- `MissingServletRequestParameterException` → `400 + PARAM_REQUIRED`，字段名为 parameter name，消息 `is required`；
- `MethodArgumentTypeMismatchException` → `400 + PARAM_INVALID`，字段名为 argument name，消息 `has invalid value`；
- `HttpMessageNotReadableException` → `400 + PARAM_INVALID`，固定字段 `request`，消息 `has invalid value`。
- Controller 值对象工厂传播的 `IllegalArgumentException` → `400 + PARAM_INVALID`，空字段数组和固定摘要；该 handler 不读取异常消息，也不按具体值对象类型分支。

这些 handler 不回显非法日期、数值、JSON、目标 Java 类型、解析器消息或 cause。当前代码没有方法级约束或类级对象约束，因此不增加 `ConstraintViolationException`、`HandlerMethodValidationException` 或任意全局对象错误协议；未来出现真实消费方时再独立设计。

### 持久化、查询与未知异常

最后一个 `@ExceptionHandler(Exception.class)` 负责领域/MVC handler 之外的失败，并按固定路由与异常类型选择一个内部错误码：

1. `POST /api/v1/downloads` 上的 `DataAccessException` 或 `TransactionException` → `PERSISTENCE_FAILED`；
2. `GET /api/v1/data-sources/{pluginId}/datasets/{apiName}/records` 上的其余未处理异常 → `QUERY_FAILED`，覆盖 M09-T04 明确留给本任务的 JDBC 异常、无效 COUNT 结果和读取失败；
3. 其他路径、方法或异常 → `INTERNAL_ERROR`。

records 匹配必须同时验证 HTTP method、固定 `/api/v1/data-sources/` 前缀、`/datasets/` 中段和 `/records` 后缀；downloads 必须同时精确匹配 method 与 path。不得只按 GET/POST 粗分，也不得读取异常消息、SQL、stack frame、cause 文本或 Controller 私有异常类。三类状态均为 500，客户端使用前节固定消息和空字段错误。

这个路由判断是对 M09-T03/M09-T04 已批准边界的最小适配：两个任务刻意保留 Spring 数据库/事务异常和查询层 `IllegalStateException`，同时 M09-T05 的固定文件范围禁止修改既有 Controller/Core 增加包装类型。

### 安全日志

响应工厂在返回前只记录以下字段：固定事件文本、requestId、`ErrorCode` 和异常运行时类型名。它不得记录 URI、查询串、参数、request body、Header、Cookie、Token、异常消息、cause 消息或字段错误内容。

- 400、409、422 使用 `WARN`，不附 Throwable；
- 500、502、504 使用 `ERROR`，附一个新建的脱敏 `RuntimeException("Request failure details redacted")`：只复制原异常的 `StackTraceElement[]`，不复制 message、cause、suppressed 或自定义字段；日志另以固定字段记录原异常类型名。

这样服务器仍保留失败位置栈用于定位，同时 SQL/Token/上游原文等异常消息不会进入日志或响应。测试通过 Logback `ListAppender` 捕获 advice logger，验证固定字段与堆栈存在，并扫描 formatted message、Throwable message/cause 和响应 JSON 均不包含测试 SQL、绝对路径、`stacktrace` 或 Token 哨兵。

### 直接依赖与约束比较

- M09-T02 提供 `DataSourceController` 的 `PLUGIN_DISABLED|DATASET_MISCONFIGURED` 私有 `TensorException`，其错误消息虽已安全，本任务仍只按 code 生成固定响应；未知插件/数据集已经批准为 409，不能被全局 advice 改成 404。
- M09-T03 提供 `DownloadController`、`DownloadService`、`ParameterValidationException`、`SourceException`、`AdapterException` 及原样传播的数据库/事务异常。本任务保持领域 code 和字段错误，按 downloads 路由把数据库/事务失败归为 `PERSISTENCE_FAILED`，不改变事务或阶段行为。
- M09-T04 提供 records Controller 的 `PARAM_INVALID|DATASET_MISCONFIGURED` 以及原样传播的查询失败。本任务保持既有 400/409，并把 records 未处理失败统一为 `QUERY_FAILED`。
- M09-T01 提供 `RequestIdFilter`、`ApiErrorResponse` 和 `FieldErrorResponse`。本任务只消费这三个合同，不生成新 ID、不修改 DTO 不变量，也不创建第二套错误包络。
- M00-T03 冻结 16 项错误码闭集与精确 HTTP/retryable 矩阵，优先消除 TRD 12.6 宽泛 404/503 分类的歧义。项目所有者已在本任务设计阶段再次批准契约优先方案。

这些依赖的职责互补且无冲突。M09-T02/T03/T04 是权威看板列出的直接依赖；M09-T01 和 M00-T03 是通过三项直接依赖传递消费的稳定 HTTP 合同，本任务不修改其看板依赖。

## Files

创建：

- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`：统一领域、验证、MVC、数据库/事务、查询和未知异常映射，以及安全响应和日志；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/GlobalExceptionHandlerTest.java`：standalone MockMvc、测试 Controller、真实 Filter/Validation、16 码矩阵、字段错误、路由分类和泄漏扫描。

不修改或删除其他文件。实现提交只暂存上述两个新增 Java 文件，固定消息为 `feat(api): map domain errors safely`；设计、计划、交接、看板、合同、POM、既有 Java、资源、其他模块、临时日志和 `target/` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前在允许 Mockito/Byte Buddy attach 的环境运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，共 295/295，0 failure、0 error、0 skipped；六层 Enforcer、app ArchUnit 和禁止 Git 能力门禁通过。

随后只完整创建 `GlobalExceptionHandlerTest.java`，不创建生产 handler，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=GlobalExceptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `GlobalExceptionHandler` 不存在而在 `tensor-app:testCompile` 非零；不得因测试语法、依赖、Logger、Validation、MockMvc、上游无匹配测试或环境形成伪 RED。

### 固定 25 个 GREEN invocation

测试只使用当前 POM 已有的 JUnit Jupiter/params、AssertJ、Spring Test/MVC/Validation、Jackson、Mockito、SLF4J/Logback；不修改 POM，不启动完整 Boot context，不访问 Docker、数据库、网络或 Token。standalone MockMvc 注册测试专用抛错 Controller、生产 `GlobalExceptionHandler`、真实 `RequestIdFilter` 和 Bean Validation。

固定测试构成为 25 个 Surefire invocation：

1. 参数化 16 次：测试专用最小 `TensorException` 依次携带全部 16 个 `ErrorCode`，断言精确 HTTP、固定 message、enum code、`code.retryable()`、空字段错误、Header/body requestId 一致；异常原消息包含 SQL/绝对路径/stacktrace/Token 哨兵，响应均不得出现；
2. 参数化 2 次：测试 Controller 通过真实 `ParameterValidator` 分别触发 `PARAM_REQUIRED` 与 `PARAM_INVALID`，断言 Core 字段顺序和安全 field/message 原样投影；
3. 单次：以 `DownloadRequest` 的真实 Bean Validation 覆盖纯缺失、纯格式错误和混合失败，断言 code 选择、每字段唯一、排序和固定安全字段消息；
4. 单次：覆盖缺失 request parameter、非法整数/日期类型绑定、malformed JSON 和值对象 `IllegalArgumentException`，断言 `PARAM_REQUIRED|PARAM_INVALID` 与不回显原值/解析诊断；
5. 参数化 3 次：分别在精确 downloads POST、records GET 和其他路由抛带敏感消息的数据库/运行时异常，断言 `PERSISTENCE_FAILED`、`QUERY_FAILED`、`INTERNAL_ERROR`；
6. 单次：普通未知异常固定为 `500 + INTERNAL_ERROR`，不受异常消息、cause 或 `@ResponseStatus` 影响；
7. 单次：反射确认 advice final/Servlet-only/唯一职责表面，并捕获 WARN/ERROR 日志，验证只含固定事件、requestId/code/type，5xx 有脱敏堆栈且日志/响应均无敏感哨兵。

创建最小生产 handler 后重跑聚焦命令，预期 25/25，0 failure、0 error、0 skipped。

### Mutation、回归与静态门禁

执行三项受控 mutation，每次观察预期失败后立即恢复且不提交：

1. 临时交换一个领域码状态或 retryable 来源，16 码矩阵必须失败；
2. 临时把响应 message 改为 `exception.getMessage()`，敏感响应扫描必须失败；
3. 临时移除 route classifier 或把原 Throwable 直接写入 logger，持久化/查询分类或日志敏感扫描必须失败。

恢复后再次运行聚焦 25/25，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 61，共 320/320，0 failure、0 error、0 skipped；六层 Enforcer、app ArchUnit 和禁止 Git 能力门禁通过。

运行结构、敏感信息、范围、JAR、格式、跟踪和清理检查：

```bash
rg -n '@RestControllerAdvice|@ExceptionHandler|TensorException|MethodArgumentNotValidException|MethodArgumentTypeMismatchException|HttpMessageNotReadableException|IllegalArgumentException|DataAccessException|TransactionException|PERSISTENCE_FAILED|QUERY_FAILED|INTERNAL_ERROR|RequestIdFilter\.MDC_KEY' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java
rg -n 'getMessage\(|getQueryString|getParameter\(|getHeader\(|getCookies\(|request\.getReader|request\.getInputStream|(?i:authorization|credential|password|token)|SELECT |INSERT |UPDATE |DELETE ' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/web/GlobalExceptionHandler.*\.class'
git diff --quiet -- \
  docs data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/main/resources \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RequestIdFilterTest.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DataSourceControllerTest.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-fixture data-plane/tensor-plugin-tushare
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/GlobalExceptionHandlerTest.java
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am clean
```

第一项扫描命中批准的 advice、异常和分类符号；第二项禁用扫描无输出并退出 1；JAR 命中 handler 及其编译器生成/私有嵌套类；受保护路径和格式退出 0。提交前 scoped status 精确列出两个新增 Java 文件且都已加入 Git，无 `target/`。提交后 `git show --stat --oneline --summary HEAD` 必须显示固定消息和精确两文件实现范围，最终工作树干净。

## Acceptance

- `GlobalExceptionHandler` 是唯一 Servlet `@RestControllerAdvice`，所有当前 API 错误使用既有 `ApiErrorResponse`，Header/body requestId 与 Filter MDC 同值且不生成第二个 ID；
- 全部 16 个 `ErrorCode` 的 HTTP 和 retryable 与 M00 错误目录精确一致，只产生 400/409/422/500/502/504，不自行增加或复用错误码实现 404/503；
- 普通领域错误使用固定安全 message 和空字段数组；Core 参数错误保持安全字段顺序，Bean Validation/MVC/值对象输入错误使用稳定 code、字段名和固定消息；
- downloads 数据库/事务失败为 `PERSISTENCE_FAILED`，records 未处理查询失败为 `QUERY_FAILED`，其他未知失败为 `INTERNAL_ERROR`；所有客户端 500 message 固定且不按异常文本分类；
- 响应和日志不包含原始 SQL、Token、请求数据、异常消息、cause 消息、绝对路径或 `stacktrace` 哨兵；4xx 无堆栈，5xx 仅保留已脱敏堆栈与固定低信息量字段；
- 严格 RED 只来自缺失生产 handler；GREEN 聚焦 25/25、reactor `test`/`verify` 320/320、三项 mutation、Enforcer、ArchUnit、禁止 Git、JAR、静态、范围、格式、跟踪和 clean 门禁得到预期结果；
- 实现提交精确包含两个新增 Java 文件并使用 `feat(api): map domain errors safely`；未修改合同、POM、既有实现/测试或提前交付 M09-T06。

## Risks

- M00-T03 冻结闭集中没有 404/503；本任务按项目所有者批准保持契约一致，因此不满足脱离错误码目录阅读 TRD 12.6 时可能产生的“必须出现这两个状态”预期。未来若确需该语义，必须先独立修订错误码目录、OpenAPI、`ErrorCode` 和消费者，再扩展 advice。
- M09-T03/M09-T04 为保持最小领域表面而原样传播数据库/查询运行时异常，因此 advice 必须依赖当前固定 method/path 区分 `PERSISTENCE_FAILED` 与 `QUERY_FAILED`。新增数据库 API 时必须显式扩展设计，不能默认归入现有两类。
- 安全 5xx 日志刻意丢弃原异常 message、cause 和 suppressed，只保留异常类型与栈位置；这降低单条日志的诊断细节，但避免数据库驱动或上游异常把 SQL、Token 和响应原文写入日志。更丰富的受控诊断若需要，应在 M09-T06 的日志/脱敏设计中增加明确白名单。
- Advice 依赖 M09-T01 Filter 在 DispatcherServlet 前建立 MDC。若绕过 Filter 直接使用 MVC，无法形成满足非空 requestId 合同的错误体；本任务测试安装真实 Filter，M09-T06 必须在完整生产上下文验证 Filter 顺序和 advice 注册。
