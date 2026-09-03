# M09-T02 数据源、接口和数据集元数据 API——任务设计

- **任务编号：** `M09-T02`
- **对应任务：** [M09-T02](../superpowers/plans/tensor-modules/M09-app-api.md#task-m09-t02-数据源与元数据-api20h)
- **实施产物：** `tensor-app` 中四条只读元数据路由、三个 REST 投影 DTO、一个独立 MockMvc 测试及最小测试依赖

## Goal

在 `tensor-app` 中交付 OpenAPI 已冻结的数据源、接口和数据集元数据查询边界。调用方可以查看已注册插件的安全 readiness 摘要、可下载插件的 API/参数定义，以及启动校验通过的数据集摘要和完整展示定义；插件缺少 Token 时仍能查询已入库数据所需的数据集元数据。

实现必须直接消费 M05 的 `PluginRegistry` 与 `DatasetCatalog`，只投影 M00-T03 允许公开的字段，不读取凭证、不调用上游、不访问数据库、不按具体插件分支。受控的不可用状态在本任务形成 HTTP 409 和准确 `ErrorCode`；标准 `ApiErrorResponse` 错误体继续由预定义的 M09-T05 统一生成。

## Scope

包含：

- 创建 `DataSourceController`，实现以下四条 GET 路由：
  - `/api/v1/data-sources`
  - `/api/v1/data-sources/{pluginId}/apis`
  - `/api/v1/data-sources/{pluginId}/datasets`
  - `/api/v1/data-sources/{pluginId}/datasets/{apiName}`
- 创建 `DataSourceResponse`、`ApiDescriptorResponse` 和 `DatasetDefinitionResponse`，用 nested records 覆盖参数、数据集摘要、筛选和列对象；
- 将领域值对象和枚举投影为 OpenAPI 字段，省略不允许为 null/空数组的可选 JSON 属性；
- 以私有 `TensorException` 子类携带 `PLUGIN_DISABLED` 或 `DATASET_MISCONFIGURED`，并在 M09-T05 前先固定 HTTP 409；
- 创建唯一 `DataSourceControllerTest`，使用独立 MockMvc、Mockito、真实 Jackson 和 `RequestIdFilter` 验证路由、状态、错误码、响应头和 JSON；
- 按项目所有者批准的唯一文件范围例外，在 `tensor-app/pom.xml` 增加 test scope 的 `org.springframework:spring-test`；
- 执行严格 TDD、聚焦测试、完整 reactor test/verify、OpenAPI 投影、JAR、范围、格式、Git 跟踪与 clean 门禁。

排除：

- 不创建或装配 `PluginRegistry`、`DatasetCatalog`、`DatasetStartupValidator`、`SchemaInspector`、插件 adapters、下载/查询 service 的 Spring Bean；
- 不修改 `TensorApplication`、`RequestIdFilter`、通用错误 DTO、plugin-api、core、Tushare、fixture、OpenAPI、错误目录、配置或资源；
- 不实现 M09-T03 下载 API、M09-T04 records 分页 API、M09-T05 全局异常体映射或 M09-T06 配置、健康、指标与安全；
- 不新增 404、`NOT_FOUND` 或其他错误码，不返回凭证值、认证头、表名、业务键、批量大小、SQL、堆栈或内部路径；
- 不使用 `spring-boot-starter-test`，不增加生产依赖，不启动完整 Spring Web 上下文，也不把独立 MockMvc 结果表述为生产 Bean 装配验证。

## Approach

### Controller 边界与路由

创建 `public final` 的 `DataSourceController`，位于 `com.akkc.tensor.web`，声明 `@RestController`、`@RequestMapping("/api/v1/data-sources")` 与 `@ConditionalOnWebApplication(type = SERVLET)`。Servlet 条件保证 M09-T01 现有 `WebApplicationType.NONE` smoke context 不会尝试创建尚无真实 Bean 装配的 Controller；生产 Servlet context 仍必须取得真实 `PluginRegistry` 和 `DatasetCatalog`，不得以该条件掩盖缺失装配。唯一构造器接收并拒绝 null 的两个依赖；不使用字段注入、`ObjectProvider`、可选依赖或插件专用类型。

公开 HTTP 方法固定为：

```java
@GetMapping
public List<DataSourceResponse> listDataSources();

@GetMapping("/{pluginId}/apis")
public List<ApiDescriptorResponse> listPluginApis(@PathVariable String pluginId);

@GetMapping("/{pluginId}/datasets")
public List<DatasetDefinitionResponse.DatasetSummary> listPluginDatasets(
        @PathVariable String pluginId);

@GetMapping("/{pluginId}/datasets/{apiName}")
public DatasetDefinitionResponse getDatasetDefinition(
        @PathVariable String pluginId,
        @PathVariable String apiName);
```

路径字符串分别通过现有 `PluginId.of` 和 `ApiName.of` 构造值对象，不 trim、不改写大小写。格式非法时保留值对象的 `IllegalArgumentException`；预定义 M09-T05 将该输入失败统一映射为 `400 + PARAM_INVALID`，本任务不复制输入错误体逻辑。

数据流固定如下：

1. `listDataSources` 按 `PluginRegistry.descriptors()` 的不可变快照顺序映射，保留 ready、disabled、缺凭证和重复冲突描述符；
2. `listPluginApis` 在描述符快照中按 `pluginId` 查找唯一 `downloadAvailable == true` 的描述符，并保持其 `apis()` 原序；插件未知或任一不可下载状态均抛 `PLUGIN_DISABLED`；
3. `listPluginDatasets` 只要求描述符快照中存在该 `pluginId`，随后返回 `DatasetCatalog.list(pluginId)` 的既定 apiName 升序投影；已注册插件禁用或缺 Token 不阻止查询，合法空目录返回 `200 []`；
4. `getDatasetDefinition` 同样先确认插件已注册，再用 `DatasetKey.of(pluginId, apiName)` 调用 `DatasetCatalog.find`；插件未知或定义不存在均抛 `DATASET_MISCONFIGURED`。

Controller 不调用 `PluginRegistry.find`，因为该方法刻意只暴露可下载插件，不能承担缺 Token 时的数据集查询。Controller 也不调用 `DataSourcePlugin.descriptor()` 或 `readiness()`，避免越过 registry 的启动快照和重复隔离语义。

### REST 投影 DTO

三个顶层 DTO 都位于 `com.akkc.tensor.web.dto`，是 `public record`，只保留 OpenAPI 字段，并提供 `public static from(...)` 工厂。所有必填引用非 null，所有列表在 compact constructor 中用 `List.copyOf` 保序防御复制并拒绝 null 元素。

`DataSourceResponse` 精确组件为：

```java
public record DataSourceResponse(
        String pluginId,
        String displayName,
        String description,
        boolean enabled,
        boolean credentialConfigured,
        boolean downloadAvailable,
        String unavailableReason) {}
```

它从 `PluginDescriptor` 映射。`unavailableReason` 是 OpenAPI 的必填 nullable 字段：下载可用时仍序列化为 JSON null，不得通过全局 NON_NULL 省略；下载不可用时保持 registry 已形成的非空安全说明。DTO 不含 `apis`、`datasets` 或任何凭证内容。

`ApiDescriptorResponse` 精确组件为：

```java
public record ApiDescriptorResponse(
        String apiName,
        String displayName,
        String category,
        QueryMode queryMode,
        List<ParameterResponse> parameters) {}
```

其 nested `ParameterResponse` 组件顺序为 `name, label, type, required, description, defaultValue, allowedValues, pattern, relatedParameter`。`type` 直接使用 `ParameterType`。nested record 使用 Jackson NON_NULL；工厂把空 `allowedValues` 转为 null，使属性缺失而不是输出违反 `minItems: 1` 的空数组。参数列表保持 `ApiDescriptor.parameters()` 原序。

`DatasetDefinitionResponse` 精确组件为：

```java
public record DatasetDefinitionResponse(
        String pluginId,
        String apiName,
        String displayName,
        String category,
        QueryMode queryMode,
        List<FilterResponse> filters,
        String fixedColumn,
        List<ColumnResponse> columns) {}
```

其 nested records 为：

- `DatasetSummary`：`pluginId, apiName, displayName, category, queryMode, filters, fixedColumn`；
- `FilterResponse`：`field, operator, controlType`；
- `ColumnResponse`：`name, label, logicalType, nullable, displayOrder, length, precision, scale, allowedValues, longText`。

`ColumnResponse.logicalType` 直接使用 `LogicalType`。nested `ColumnResponse` 使用 Jackson NON_NULL；空 `allowedValues` 转为 null，`length`、`precision`、`scale` 仅在领域值存在时输出，primitive `longText` 始终输出且仍符合 OpenAPI 的可选属性约束。顶层和摘要字段全部必填，不使用 NON_NULL。

数据集工厂执行以下唯一映射：

- `PluginId`、`ApiName` 输出其 `value()`；`QueryMode`、`ParameterType`、`LogicalType` 直接由 Jackson 输出现有枚举名称；
- filters 保持定义顺序，`ts_code -> EQ/TEXT`，`trade_date` 与 `ann_date -> BETWEEN/DATE_RANGE`；
- 任何其他 filter field 由 DTO 工厂以固定、无字段名的 `IllegalArgumentException` 拒绝；Controller 在数据集投影边界将其转换为 `DATASET_MISCONFIGURED`，不输出不符合 OpenAPI 的组合；
- `fixedColumn` 非 null 时原样使用；为 null 时按 `displayOrder` 取第一项业务列名称；
- columns 按 `displayOrder` 升序输出，且不追加 `source_plugin`、`source_api`、`ingested_at`，因为本路径只返回 OpenAPI 所称 business columns；
- 不映射 `parameters`、`tableName`、`businessKey` 或 `batchSize` 到数据集响应。

### 受控失败与阶段边界

`DataSourceController` 内定义一个带 `@ResponseStatus(HttpStatus.CONFLICT)` 的私有静态 `MetadataAccessException extends TensorException`。构造器只接受 `PLUGIN_DISABLED` 或 `DATASET_MISCONFIGURED`，其他 code 立即失败。消息使用不包含输入标识符、插件原因、凭证或内部细节的固定安全文本。

映射矩阵为：

| 操作 | 条件 | HTTP | ErrorCode |
|---|---|---:|---|
| API 列表 | 插件未知或 `downloadAvailable=false` | 409 | `PLUGIN_DISABLED` |
| 数据集列表 | 插件未注册 | 409 | `DATASET_MISCONFIGURED` |
| 数据集定义 | 插件未注册或 key 不在 catalog | 409 | `DATASET_MISCONFIGURED` |
| 数据集投影 | filter 无法映射到冻结组合 | 409 | `DATASET_MISCONFIGURED` |

`@ResponseStatus` 使独立 MockMvc 在 M09-T05 前即可观察 409；测试从 `MvcResult.getResolvedException()` 断言 `TensorException.code()`。本任务不自行构造标准错误 JSON。M09-T05 后续捕获同一 `TensorException`，使用已有 `ApiErrorResponse` 生成含同值 requestId、retryable 和空 fieldErrors 的 OpenAPI 错误体。其他 runtime failure 原样传播给该全局边界，不在 Controller 中吞掉、记录或错误归类。

### 依赖与约束比较

- M05-T01 的 `PluginRegistry` 在构造期形成不可变 readiness 快照：`descriptors()` 按 pluginId/displayName 稳定排序并保留禁用、缺凭证和重复冲突项，`find()` 只暴露 ID 唯一且可下载的插件；本任务直接消费描述符快照，不重新调用插件或把列表收集为唯一 key map。
- M05-T02 的 `DatasetCatalog` 只暴露启动校验通过的定义，`list` 按 apiName 排序、`find` 精确查找；本任务不重新读取 YAML、JDBC metadata 或诊断被隔离定义。
- M09-T01 的 `RequestIdFilter` 在进入链前设置 `X-Request-Id` 并在当前线程 MDC 中保存同值；独立 MockMvc 显式安装该真实 Filter，Controller 和 DTO 不复制请求 ID 状态。
- M00-T03 冻结四条本任务路径、三个根响应 schema、参数/筛选/列对象、409 状态与 16 项错误码闭集；本任务不扩展 OpenAPI，也不新增 404/NOT_FOUND。
- M02-T02/M02-T03 的领域 records 保留描述符、参数、列和筛选原序；REST 仅补充已明确留给 M09 的 filter operator/controlType 和 fixedColumn fallback。

这些输入无冲突。M05-T01 提供插件是否已注册及能否下载的安全快照，M05-T02 提供能否查询的已验证数据集目录，M09-T01 提供请求关联；三者分别承担本任务四条路由的唯一运行时输入。唯一已批准的阶段边界是：M09-T02 用测试替身独立验证 Controller，不交付真实 registry/catalog Bean 装配；M09-T05 完成标准错误体。两者都不得被当前测试结果描述为已经完成。

## Files

创建：

- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java`：四条 GET 路由、registry/catalog 查询、数据集投影包装和私有 409 异常；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DataSourceResponse.java`：数据源摘要 record 与映射；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java`：API/参数 records 与映射；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DatasetDefinitionResponse.java`：数据集摘要、完整定义、筛选、列 records 与映射；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DataSourceControllerTest.java`：12 次独立 MockMvc 测试执行。

修改：

- `data-plane/tensor-app/pom.xml`：只增加 test scope 的 `org.springframework:spring-test`。

不修改或删除其他文件。实现提交消息固定为 `feat(api): expose data-source metadata`，提交精确包含上述六个文件；设计、交接、看板、生成目录或其他任务不得混入实现提交。

## Tests

### 严格 RED 与聚焦 GREEN

第一步只修改 `tensor-app/pom.xml` 并完整创建 `DataSourceControllerTest.java`，暂不创建四个生产类型。运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DataSourceControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期依赖解析和已有模块编译成功，随后只在 `tensor-app:testCompile` 因 `DataSourceController`、`DataSourceResponse`、`ApiDescriptorResponse`、`DatasetDefinitionResponse` 四个交付类型不存在而退出非 0。测试语法、MockMvc、Servlet/Jackson、Mockito 或既有代码失败不是有效 RED。

`DataSourceControllerTest` 使用 Mockito mocks 提供 `PluginRegistry`/`DatasetCatalog`，把 Controller 传给 `MockMvcBuilders.standaloneSetup`，并通过 `.addFilters(new RequestIdFilter())` 安装真实请求标识 Filter。响应内容由现有 Jackson `ObjectMapper` 解析成 tree，不依赖 JsonPath。测试固定为 12 次执行：

1. 一次测试同时列出 ready 与 unavailable 数据源，保持 registry 顺序和完整七字段，并扫描 JSON 不含 Token 值、authorization、apis、datasets 或内部字段；
2. 一次测试为 ready 插件返回恰好 49 个 API，验证原序、五个根字段、参数九字段、现有枚举名称和 null/空 allowedValues 省略；
3. 三次参数化执行分别覆盖未知、disabled、缺 Token 插件的 API 列表，均断言 409、`X-Request-Id` 和 resolved `PLUGIN_DISABLED`；
4. 一次测试证明缺 Token 插件仍返回 catalog 数据集摘要，并验证 apiName 升序、七字段与 filter 组合；
5. 一次测试证明缺 Token 插件仍返回完整定义，验证 fixedColumn fallback、filter 原序、column displayOrder、可选字段省略、longText 和内部字段排除；
6. 一次测试证明未知插件的数据集列表返回 409、请求头和 `DATASET_MISCONFIGURED`；
7. 三次参数化执行分别覆盖未知插件、未知数据集、不可映射 filter 的定义请求，均断言 409、请求头和 `DATASET_MISCONFIGURED`；
8. 一次测试证明已注册插件的空 catalog 返回 `200 []`，并验证响应头；
9. 上述成功投影测试同时修改源列表并尝试修改响应列表，证明 DTO 防御复制且不可修改；并反射断言 Controller 只在 Servlet Web 应用中注册，不增加单独测试执行。

创建四个生产类型后重跑聚焦命令，预期 `DataSourceControllerTest` 12/12，0 failure、0 error、0 skipped，命令退出 0。

### Reactor 与结构门禁

当前提交基线为 plugin-api 79、core 75、Tushare 93、fixture 12、app 24，共 283/283。增加 12 次执行后运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

两条命令均预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，共 295/295，0 failure、0 error、0 skipped；六层 Enforcer、`tensor-app` ArchUnit 和禁止 Git 能力门禁通过。Mockito/Byte Buddy 需要在允许 agent attach 的 Maven 环境执行，不得为绕过沙箱失败修改依赖或测试。

执行静态、JAR 和范围门禁：

```bash
rg -n '@RestController|/api/v1/data-sources|PluginRegistry|DatasetCatalog|PLUGIN_DISABLED|DATASET_MISCONFIGURED' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web
rg -n 'Tushare|Fixture|\.download\(|JdbcTemplate|DataSourcePlugin|[Tt]oken|Credential|tableName\(|businessKey\(|batchSize\(' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/web/(DataSourceController|dto/DataSourceResponse|dto/ApiDescriptorResponse|dto/DatasetDefinitionResponse).*\.class'
git diff --quiet -- \
  docs data-plane/pom.xml \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-fixture data-plane/tensor-plugin-tushare \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java
git diff --check
git status --short --untracked-files=all -- data-plane
```

第一项命中授权路由、依赖与两种错误码；第二项无输出并退出 1，证明没有具体插件、下载、JDBC、Token/凭证或被禁止公开的领域访问；JAR 命中四个顶层生产类型及其 nested classes；受保护路径和格式退出 0。提交前 scoped status 精确列出五个新增 Java 文件和一个修改 POM，不含 `target/`，所有新增文件已加入 Git。

实现提交后运行：

```bash
git show --stat --oneline --summary HEAD
git show --format= --name-status HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
```

提交消息和六文件范围精确匹配；clean 退出 0；最终工作树为空且不存在 `target/`。

## Acceptance

- 四条 GET 路由存在且只使用 `PluginRegistry`/`DatasetCatalog`；Controller 只在 Servlet Web 应用中注册，既有 non-Web smoke context 不受影响；没有插件专用分支、下载、写操作、数据库访问或真实 Bean 装配；
- 数据源列表同时公开安全的 ready/unavailable 摘要；API 列表只允许可下载插件；缺 Token/禁用不阻止已验证数据集列表和定义查询；
- 未知/不可下载插件的 API 列表为 `409 + PLUGIN_DISABLED`；未知插件/数据集和不可安全投影定义为 `409 + DATASET_MISCONFIGURED`；没有 404 或新错误码；
- 三个顶层 DTO 及 nested records 的字段、大小写、枚举、数组顺序、fixedColumn fallback、filter 组合、column order、可选字段省略和内部字段排除与 OpenAPI/M00/M02 一致；
- 每个 MockMvc 响应具有 `X-Request-Id`；本任务不伪造标准错误体，M09-T05 可直接从 `TensorException` 读取 code/retryable；
- 严格 RED 只来自四个缺失交付类型；聚焦 12/12、默认 reactor test/verify 295/295、Enforcer、ArchUnit、禁止 Git、JAR、范围、格式、跟踪和 clean 门禁取得预期结果；
- 实现提交精确包含五个 Java 文件和 `tensor-app/pom.xml`，消息为 `feat(api): expose data-source metadata`，不混入其他任务或生成物。

## Risks

- 当前生产代码尚无 `PluginRegistry` 和 `DatasetCatalog` Spring Bean 装配。项目所有者已批准本任务只用独立 MockMvc 验证 Controller；该结果不代表完整生产 Web context 可启动，后续装配任务必须在启用真实路由前补齐并验证。
- M09-T05 尚未交付时，受控失败只有 HTTP 409 和 resolved exception 中的准确错误码，不具备 OpenAPI `ApiErrorResponse` 正文。项目所有者已批准该分阶段边界；M09-T05 必须捕获现有 `TensorException`，不得让 Controller 形成第二套错误体。
- `PluginRegistry.descriptors()` 可以保留相同 pluginId 的重复冲突描述符；API 路由只接受唯一可下载描述符，而数据集路由把任一同 ID 描述符视为“已注册”。这是 M05 局部隔离与已批准“不可下载 API 失败、数据集查询按 catalog 决定”的组合结果。
- Mockito/Byte Buddy 在受限沙箱可能无法 self-attach。测试必须在允许 attach 的环境取得真实 GREEN，不得把环境失败当作有效 RED、修改 POM 开启宽泛能力或降低测试。
- 无未决设计选择。
