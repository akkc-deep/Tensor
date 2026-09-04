# ISSUE-001：DownloadController 下载参数聚合方案

关联问题：[ISSUE-001：Controller 方法入口参数不够聚合](../problems/ISSUE-001-method-input-aggregation.md)

方案状态：总体方案已确认，待整理为正式任务设计和实施计划。

## 方案结论

下载接口继续保留一个 Controller 方法：

```java
@PostMapping
public DownloadResponse download(
        @RequestBody DownloadRequest request
) {
}
```

但 `DownloadRequest.params` 不再是裸 `Map<String, Object>`，而是多态父类型
`DownloadParameters`。每次请求只会产生一个具体参数子类型，不会把所有 API 的字段放进一个万能对象。

```text
请求体中的 pluginId + apiName
              ↓
确定本次请求需要的参数类型
              ↓
将 params 转换为该具体类型
              ↓
构造完整 DownloadRequest
              ↓
调用 DownloadController.download(request)
```

例如：

```text
tushare_pro + daily
    → TradeDateParameters

tushare_pro + income
    → TsCodeAnnDateParameters

tushare_pro + trade_cal
    → ExchangeDateRangeParameters

fixture + fixture_daily
    → ScenarioParameters
```

本轮只重构 Controller 请求边界：

- HTTP 契约保持不变。
- `DownloadService` 方法签名保持不变。
- `OperationLogger` 方法签名保持不变。
- `ParameterValidator` 保持不变。
- `DataSourcePlugin` 插件接口保持不变。
- 明确参数类型进入现有调用链前，暂时转换为原来的 Map。

## HTTP 契约

接口继续使用：

```http
POST /api/v1/downloads
Content-Type: application/json
```

请求字段和结构不变：

```json
{
  "pluginId": "tushare_pro",
  "apiName": "daily",
  "params": {
    "trade_date": "20260905"
  }
}
```

以下内容均不改变：

- URL：`/api/v1/downloads`
- HTTP 方法：`POST`
- 顶层字段：`pluginId`、`apiName`、`params`
- `params` 内部字段的 snake_case 命名
- 成功响应 JSON
- 失败响应 JSON 和错误码

Java 对象内部会将 `pluginId + apiName` 聚合为项目已有的 `DatasetKey`，但不会要求调用方把请求改成
嵌套的 `dataset` JSON。

## 入参对象模型

### DownloadRequest

修改前：

```java
public record DownloadRequest(
        String pluginId,
        String apiName,
        Map<String, Object> params
) {
}
```

修改后：

```java
public record DownloadRequest(
        DatasetKey dataset,
        DownloadParameters params
) {
}
```

`DatasetKey` 聚合数据集标识，`DownloadParameters` 表示本次请求对应的一种明确参数类型。

### DownloadParameters

```java
public sealed interface DownloadParameters
        permits SnapshotParameters,
                AnnDateParameters,
                ExchangeParameters,
                ExchangeDateRangeParameters,
                ExchangeTradeDateParameters,
                HsTypeParameters,
                ListStatusParameters,
                MonthParameters,
                DateRangeParameters,
                TradeDateParameters,
                TsCodeParameters,
                TsCodeAnnDateParameters,
                ScenarioParameters {
}
```

一次请求只会使用其中一个子类型。例如：

```java
public record TradeDateParameters(
        String tradeDate
) implements DownloadParameters {
}

public record TsCodeAnnDateParameters(
        String tsCode,
        String annDate
) implements DownloadParameters {
}

public record ExchangeDateRangeParameters(
        String exchange,
        String startDate,
        String endDate
) implements DownloadParameters {
}

public record ScenarioParameters(
        String scenario
) implements DownloadParameters {
}
```

第一阶段继续使用 `String` 保存日期、月份和枚举值，由现有 `ParameterValidator` 负责格式、范围、默认值
和关联参数校验。此次重构不同时改变参数数据类型和校验规则。

### 参数类型清单

49 个 Tushare API 对应 12 种参数结构，测试插件 `fixture_daily` 还有一种 `scenario` 结构。当前项目一共
需要 13 种参数类型。

| 参数签名 | Java 类型 | API |
| --- | --- | --- |
| 无参数 | `SnapshotParameters` | `index_classify`、`index_member`、`index_member_all`、`pledge_detail`、`pledge_stat`、`stk_managers` |
| `ann_date` | `AnnDateParameters` | `disclosure_date`、`dividend`、`express`、`forecast`、`repurchase`、`share_float`、`stk_holdertrade`、`top10_floatholders`、`top10_holders` |
| `exchange` | `ExchangeParameters` | `stock_company` |
| `exchange,start_date,end_date` | `ExchangeDateRangeParameters` | `trade_cal` |
| `exchange_id,trade_date` | `ExchangeTradeDateParameters` | `margin` |
| `hs_type` | `HsTypeParameters` | `hs_const` |
| `list_status` | `ListStatusParameters` | `stock_basic` |
| `month` | `MonthParameters` | `broker_recommend` |
| `start_date,end_date` | `DateRangeParameters` | `namechange`、`new_share` |
| `trade_date` | `TradeDateParameters` | `adj_factor`、`block_trade`、`daily`、`daily_basic`、`hk_hold`、`hsgt_top10`、`margin_detail`、`moneyflow`、`moneyflow_hsgt`、`monthly`、`slb_len`、`slb_sec`、`slb_sec_detail`、`stk_limit`、`suspend_d`、`top_inst`、`top_list`、`weekly` |
| `ts_code` | `TsCodeParameters` | `stk_holdernumber`、`stk_rewards` |
| `ts_code,ann_date` | `TsCodeAnnDateParameters` | `balancesheet`、`cashflow`、`fina_audit`、`fina_indicator`、`fina_mainbz`、`income` |
| `scenario` | `ScenarioParameters` | `fixture_daily` |

新增 API 时遵循以下规则：

- 使用已有参数结构：复用已有参数类型，不增加 Controller。
- 出现新的参数结构：增加一个参数类型和一个 Codec。
- 不允许无法识别的参数结构回退为裸 Map。
- 只有出现新的 HTTP 用例或完全不同的响应语义时，才考虑新增 Controller。

## JSON 到具体类型的阶段

类型处理发生在 Spring MVC 处理 `@RequestBody` 时，早于 Controller 方法执行。

```text
HTTP 请求
    ↓
DispatcherServlet
    ↓
RequestMappingHandlerAdapter
    ↓
RequestResponseBodyMethodProcessor 处理 @RequestBody
    ↓
MappingJackson2HttpMessageConverter
    ↓
ObjectMapper.readValue(body, DownloadRequest.class)
    ↓
DownloadRequestDeserializer
    ↓
DownloadParameterResolver
    ↓
DownloadRequest(DatasetKey, 具体参数子类型)
    ↓
DownloadController.download(request)
```

`MappingJackson2HttpMessageConverter` 负责从 HTTP 请求中取得 JSON 字节流，并调用 Spring Boot 配置的
`ObjectMapper`。它不负责决定具体参数子类型，类型选择由自定义反序列化器和 Resolver 完成。

### 通过 Spring 管理的 Jackson Module 注册

`DownloadRequestDeserializer` 依赖 `DownloadParameterResolver`，不能让 Jackson 自己通过无参构造器
创建。因此先由 Spring 构造反序列化器：

```java
@Bean
DownloadRequestDeserializer downloadRequestDeserializer(
        DownloadParameterResolver resolver
) {
    return new DownloadRequestDeserializer(resolver);
}
```

再把这个实例注册到 Jackson Module：

```java
@Bean
Module downloadRequestJacksonModule(
        DownloadRequestDeserializer deserializer
) {
    SimpleModule module = new SimpleModule(
            "tensor-download-request"
    );

    module.addDeserializer(
            DownloadRequest.class,
            deserializer
    );

    return module;
}
```

Spring Boot 会收集容器中的 `Module` Bean，并注册到自动配置的 `ObjectMapper`。Spring MVC 的
`MappingJackson2HttpMessageConverter` 使用这个 ObjectMapper：

```text
DownloadParameterResolver Bean
        ↓ 注入
DownloadRequestDeserializer Bean
        ↓ 注册
Jackson Module Bean
        ↓ Spring Boot 自动注册
ObjectMapper
        ↓ 提供给
MappingJackson2HttpMessageConverter
```

本方案只使用 Spring Module 注册，不同时在 `DownloadRequest` 上添加：

```java
@JsonDeserialize(using = DownloadRequestDeserializer.class)
```

注解方式只提供反序列化器 Class，Jackson 通常会自行创建实例，不方便取得 Spring 容器里的 Resolver。

### DownloadRequestDeserializer

反序列化器先读取完整请求，再根据顶层 `pluginId + apiName` 处理 `params`：

```java
public final class DownloadRequestDeserializer
        extends JsonDeserializer<DownloadRequest> {

    private final DownloadParameterResolver resolver;

    public DownloadRequestDeserializer(
            DownloadParameterResolver resolver
    ) {
        this.resolver = resolver;
    }

    @Override
    public DownloadRequest deserialize(
            JsonParser parser,
            DeserializationContext context
    ) {
        JsonNode root = parser.getCodec().readTree(parser);

        String pluginId = requiredText(root, "pluginId");
        String apiName = requiredText(root, "apiName");
        ObjectNode params = requiredObject(root, "params");

        DatasetKey dataset = DatasetKey.of(
                PluginId.of(pluginId),
                ApiName.of(apiName)
        );

        DownloadParameters typedParameters = resolver.resolve(
                dataset,
                params
        );

        return new DownloadRequest(
                dataset,
                typedParameters
        );
    }
}
```

例如 `daily` 请求最终构造的对象相当于：

```java
new DownloadRequest(
        DatasetKey.of(
                PluginId.of("tushare_pro"),
                ApiName.of("daily")
        ),
        new TradeDateParameters("20260905")
);
```

Controller 被调用时，JSON 已经读取完毕，`request.params()` 的运行时类型已经是
`TradeDateParameters`。

## 具体参数类型的选择

不能只根据 `params` 中出现的字段猜测类型，因为不同 API 可能拥有相似字段。类型必须由
`pluginId + apiName` 决定。

```text
DatasetKey
    ↓
ApiDescriptor
    ↓
ParameterShape
    ↓
ParameterCodec
    ↓
具体 DownloadParameters 子类型
```

### ParameterShape

Resolver 从 `ApiDescriptor.parameters()` 生成规范化参数签名。签名比较参数名称、类型和约束，不依赖
YAML 中的声明顺序。

```java
public record ParameterShape(
        List<ParameterFieldShape> fields
) {
    public static ParameterShape from(ApiDescriptor api) {
        return new ParameterShape(
                api.parameters().stream()
                        .map(ParameterFieldShape::from)
                        .sorted(comparing(ParameterFieldShape::name))
                        .toList()
        );
    }
}
```

### ParameterCodec

每种参数结构对应一个 Codec。Codec 同时负责读取明确类型和转换回现有下游协议。

```java
interface ParameterCodec<T extends DownloadParameters> {

    ParameterShape shape();

    Class<T> parameterType();

    T read(ParameterJsonReader json);

    Map<String, Object> write(T parameters);
}
```

交易日 Codec 示例：

```java
ParameterCodec<TradeDateParameters> tradeDateCodec() {
    return codec(
            shape(field("trade_date", DATE, true)),
            TradeDateParameters.class,
            json -> new TradeDateParameters(
                    json.nullableText("trade_date")
            ),
            value -> values(
                    "trade_date",
                    value.tradeDate()
            )
    );
}
```

Resolver 保存两份索引：

```java
Map<ParameterShape, ParameterCodec<?>> codecsByShape;
Map<Class<?>, ParameterCodec<?>> codecsByType;
```

读取请求时按元数据签名选择 Codec：

```java
public DownloadParameters resolve(
        DatasetKey dataset,
        ObjectNode paramsJson
) {
    ApiDescriptor api = descriptors.requireApi(dataset);
    ParameterShape shape = ParameterShape.from(api);
    ParameterCodec<?> codec = codecsByShape.get(shape);

    if (codec == null) {
        throw datasetMisconfigured(
                "Unsupported parameter shape"
        );
    }

    return codec.read(new ParameterJsonReader(
            paramsJson,
            api.parameters()
    ));
}
```

参数结构与 API 不匹配时不会改选其他子类型。例如 `apiName=daily` 却提交 `ann_date`，Resolver 仍然
选择 `TradeDateParameters`，随后报告缺少 `trade_date` 且 `ann_date` 未声明。

`ParameterJsonReader` 只负责：

- 确认 `params` 是 JSON 对象。
- 确认字符串参数没有被提交为数字、数组、布尔值或嵌套对象。
- 拒绝元数据中没有声明的字段。
- 缺失字段保留为 `null`，由现有 `ParameterValidator` 返回 `PARAM_REQUIRED`。

日期格式、枚举允许值、证券代码格式和日期范围关系仍由现有校验器处理，不在绑定层复制规则。

## Controller 和现有调用链

Controller 收到的 `DownloadRequest` 已经包含具体参数子类型。为了保持本轮范围，在调用现有 Service 前
通过 Codec 转回原参数结构：

```java
@PostMapping
public DownloadResponse download(
        @RequestBody DownloadRequest request
) {
    DatasetKey dataset = request.dataset();
    RequestId requestId = currentRequestId();

    Map<String, Object> rawParameters =
            parameterResolver.toRawValues(
                    request.params()
            );

    return operationLogger.download(
            dataset,
            rawParameters,
            () -> DownloadResponse.from(
                    downloadService.execute(
                            dataset.pluginId(),
                            dataset.apiName(),
                            rawParameters,
                            requestId
                    )
            )
    );
}
```

这里的 Map 是现有调用链的临时兼容值，不是 HTTP 请求模型。以下公开接口本轮保持不变：

```java
DownloadService.execute(
        PluginId pluginId,
        ApiName apiName,
        Map<String, Object> params,
        RequestId requestId
);

OperationLogger.download(
        DatasetKey key,
        Map<String, Object> parameters,
        Supplier<DownloadResponse> operation
);

DataSourcePlugin.download(
        ApiName apiName,
        Map<String, Object> params
);
```

因此本轮不会重写下载编排、日志、插件调用、Envelope 校验或持久化逻辑。彻底移除下载调用链中的 Map
应作为后续独立问题处理。

## 错误行为

必须保持以下结果：

| 场景 | 错误码 |
| --- | --- |
| 缺少 `pluginId`、`apiName` 或 `params` | `PARAM_REQUIRED` |
| 标识符格式错误 | `PARAM_INVALID` |
| `params` 不是 JSON 对象 | `PARAM_INVALID` |
| 缺少 API 必填参数 | `PARAM_REQUIRED` |
| 参数 JSON 类型错误 | `PARAM_INVALID` |
| 日期、月份、枚举或证券代码不合法 | `PARAM_INVALID` |
| 参数中存在未声明字段 | `PARAM_INVALID` |
| 开始日期晚于结束日期 | `PARAM_INVALID` |
| 插件不可用 | `PLUGIN_DISABLED` |
| API、适配器或参数签名配置异常 | `DATASET_MISCONFIGURED` |

Jackson 可能把自定义绑定异常包装为 `HttpMessageNotReadableException`。`GlobalExceptionHandler` 应只解包
项目自己的绑定异常，保留其错误码和字段错误；其他不可读 JSON 继续返回现有通用
`PARAM_INVALID`。

正式设计必须增加特征测试，锁定同一请求同时包含多个错误时的现有错误优先级，避免解析阶段前移导致
错误码顺序变化。

## Spring Bean 边界

具有依赖和协作行为的对象由 Spring 管理：

- `DownloadController`
- `DownloadService`
- `DownloadDescriptorResolver`
- `DownloadParameterResolver`
- `DownloadRequestDeserializer`
- 注册反序列化器的 Jackson Module

请求数据和值对象不注册为 Spring Bean：

- `DownloadRequest`
- `DatasetKey`
- 13 种 `DownloadParameters` 实现
- `DownloadResponse`
- `DownloadResult`

生产 ApplicationContext 测试不仅要确认 Module Bean 存在，还要使用容器中的 `ObjectMapper` 读取一份
`daily` 请求，并断言 `request.params()` 是 `TradeDateParameters`，以证明 MVC 实际使用了该 Module。

## 验收要点

后续正式任务设计和实施必须证明：

- `POST /api/v1/downloads` 的请求和响应契约没有变化。
- `DownloadRequest` 不再声明 `Map<String, Object> params`。
- Java 内部使用现有 `DatasetKey` 表示 `pluginId + apiName`。
- 49 个 Tushare API 和 `fixture_daily` 都能唯一匹配一个参数 Codec。
- 13 种参数结构都能解析为对应的明确参数类型。
- 参数结构与 API 不匹配时返回受控字段错误，不会猜测或切换类型。
- Spring Boot 管理的 ObjectMapper 已注册下载请求 Module。
- Controller 被调用前已经完成 JSON 到具体参数子类型的转换。
- `DownloadService`、`OperationLogger`、`ParameterValidator` 和 `DataSourcePlugin` 的签名与行为保持不变。
- 现有成功、空结果、源端失败、校验失败和持久化失败测试继续通过。
- 不增加 49 个重复 Controller，也不增加万能参数对象或裸 Map 回退类型。

本文件只记录已确认的架构方案。下一步应按照 `docs/task-designs` 的规范形成正式设计，明确实际文件、
实施步骤、测试命令和验收证据，再进行任务拆分；当前阶段不修改生产代码。
