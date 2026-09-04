# ISSUE-002：Controller 业务逻辑分层方案

关联问题：[ISSUE-002：Controller 承担过多业务逻辑](../problems/ISSUE-002-controller-business-logic-layering.md)

方案状态：已确认，待制定实施计划。

## 方案结论

增强现有 Core Service，并新增最小的元数据查询 Service。Controller 只保留 HTTP 参数绑定、值对象构造、Service 调用、响应投影和成功操作记录，不新增空转发的 Application Facade 或 Service 接口。

`OperationLogger` 改为显式的成功结果记录器：业务调用完成后，Controller 把请求标识、输入、核心结果和耗时传给它。它不再接受 `Supplier`、执行 Service、捕获业务异常或依赖 Web DTO。

```text
HTTP 请求
    ↓
Controller：绑定参数并构造值对象
    ↓
Service：校验并执行业务
    ↓
Controller：构造响应 DTO
    ↓
OperationLogger：记录成功日志和指标
    ↓
返回响应
```

Service 抛出异常时不调用 `OperationLogger`，失败统一由 `GlobalExceptionHandler` 记录和映射。

## Controller 边界

Controller 只负责：

- 声明 Spring MVC 路由和参数绑定；
- 构造 `PluginId`、`ApiName`、`DatasetKey`、`QueryCriteria` 和 `RequestId`；
- 调用一个明确的业务 Service；
- 将核心结果转换为 Web Response DTO；
- 在成功后显式调用 `OperationLogger`。

Controller 不再：

- 直接访问 `PluginRegistry`、`DatasetCatalog`、Repository 或 Plugin；
- 判断插件、API、数据集或过滤条件是否可用；
- 定义 `TensorException` 子类或转换业务异常；
- 通过 `Supplier`、回调或 `finally` 让日志组件控制业务执行。

ISSUE-001 的 Controller 入参聚合不属于本方案，避免同时调整请求模型和业务分层。

## DataSourceController

新增 `MetadataQueryService`，`DataSourceController` 只依赖该 Service。目标调用形态为：

```java
public List<ApiDescriptorResponse> listPluginApis(String pluginId) {
    return metadataQueryService.listApis(PluginId.of(pluginId)).stream()
            .map(ApiDescriptorResponse::from)
            .toList();
}

public DatasetDefinitionResponse getDatasetDefinition(
        String pluginId, String apiName) {
    DatasetKey key = DatasetKey.of(
            PluginId.of(pluginId), ApiName.of(apiName));
    return DatasetDefinitionResponse.from(
            metadataQueryService.getDataset(key));
}
```

删除 Controller 中的描述符筛选、注册判断、目录读取、`requireRegistered` 和 `MetadataAccessException`。DTO 投影仍属于 Web 层；Service 保证返回的数据集定义满足当前查询能力，否则抛出 `DATASET_MISCONFIGURED`。

## MetadataQueryService

`MetadataQueryService` 位于 `tensor-core`，集中元数据访问规则，使用具体类而非接口：

```java
List<PluginDescriptor> listDataSources();

List<ApiDescriptor> listApis(PluginId pluginId);

List<DatasetDefinition> listDatasets(PluginId pluginId);

DatasetDefinition getDataset(DatasetKey key);
```

它负责：

- 从 `PluginRegistry` 读取稳定描述符快照；
- `listDataSources` 保持返回全部描述符；
- `listApis` 要求指定插件恰好存在一个可下载描述符，否则抛出 `PLUGIN_DISABLED`；
- `listDatasets` 和 `getDataset` 只要求存在同 ID 描述符，不因凭证缺失或下载禁用而拒绝已准入数据集；
- 从 `DatasetCatalog` 读取已准入数据集；
- 判断数据集定义是否受当前查询能力支持；
- 未知插件、数据集缺失或不支持的查询元数据使用 `DATASET_MISCONFIGURED` 表达失败。

## DatasetController

`DatasetController` 只构造查询输入并调用现有 `DatasetQueryService`。成功路径保持线性：

```java
RequestId requestId = currentRequestId();
long started = System.nanoTime();

DatasetPage result = datasetQueryService.query(key, criteria);
Duration duration = Duration.ofNanos(System.nanoTime() - started);
PageResponse response = PageResponse.from(
        requestId.value().toString(), key, result);

operationLogger.recordQuerySuccess(
        requestId, key, criteria, result, duration);

return response;
```

先完成核心查询和响应投影，再记录成功，避免查询失败或 DTO 投影失败时误记成功。`OperationLogger` 不放入 `finally`，因为失败时不存在合法 `DatasetPage`，且日志异常不应覆盖原业务异常。

删除 Controller 中的：

- `DatasetCatalog` 和 `SUPPORTED_FILTERS`；
- 数据集定义和过滤字段集合读取；
- 请求过滤条件与元数据的匹配判断；
- `filterNames` 手工收集；
- `InvalidQueryException`、`DatasetQueryAccessException` 和相关 `try/catch`。

## DatasetQueryService

扩展现有 `DatasetQueryService`，使其覆盖完整查询用例：

- 查找数据集定义；
- 判断数据集过滤元数据是否受当前查询实现支持；
- 判断请求实际使用的过滤条件是否由数据集声明；
- 构造 SQL、查询总数、归一化页码并查询记录；
- 将查询访问失败转换为稳定的 `QUERY_FAILED`。

错误区分保持为：

- 数据集不存在或过滤元数据不受支持：`DATASET_MISCONFIGURED`；
- 请求使用数据集未声明的过滤条件：`PARAM_INVALID`；
- Repository 或数据库查询失败：`QUERY_FAILED`。

底层 `QuerySqlFactory` 继续负责安全 SQL 构造，可以保留防御性校验，但业务错误语义只由 `DatasetQueryService` 决定。

支持的过滤字段由 `tensor-core` 内一个最小的查询能力策略统一维护，供 `MetadataQueryService`、`DatasetQueryService` 和 `QuerySqlFactory` 复用；不得在 Controller 或多个 Service 中分别复制 `ts_code`、`trade_date`、`ann_date` 集合。

## DownloadController 与 DownloadService

`DownloadService` 继续负责现有插件选择、参数校验、源端调用、数据适配和持久化编排。Controller 的成功路径调整为：

```java
RequestId requestId = currentRequestId();
long started = System.nanoTime();

DownloadResult result = downloadService.execute(
        key.pluginId(), key.apiName(), request.params(), requestId);
Duration duration = Duration.ofNanos(System.nanoTime() - started);
DownloadResponse response = DownloadResponse.from(result);

operationLogger.recordDownloadSuccess(
        requestId, key, request.params(), result, duration);

return response;
```

`DownloadService` 在持久化边界把数据库或事务失败转换为 `PERSISTENCE_FAILED`，Web 层不再根据请求路径猜测失败类型。

## OperationLogger

`OperationLogger` 保留在 `tensor-app` 的可观测性包，公开两个显式、返回 `void` 的成功记录方法：

```java
void recordQuerySuccess(
        RequestId requestId,
        DatasetKey key,
        QueryCriteria criteria,
        DatasetPage result,
        Duration duration);

void recordDownloadSuccess(
        RequestId requestId,
        DatasetKey key,
        Map<String, Object> parameters,
        DownloadResult result,
        Duration duration);
```

它负责：

- 输出查询和下载成功日志；
- 记录成功或空结果指标；
- 记录结果数量和耗时；
- 从显式输入生成过滤字段或参数摘要；
- 排除 Token、Authorization、Cookie、Password 和 Credential 等敏感参数名；
- 隔离可观测性内部失败，不改变已经完成的业务结果。

它不再：

- 接受或执行 `Supplier`；
- 捕获、分类或重新抛出业务异常；
- 记录失败指标；
- 依赖 `DownloadResponse`、`PageResponse`、MDC 或 `RequestIdFilter`。

`TensorMetrics` 本轮不需要改变公共方法；`OperationLogger` 不再调用 `FAILURE` 分支即可。是否进一步清理未使用的失败枚举和分支，应以实际引用扫描决定，不扩大本问题范围。

## 失败与异常映射

```text
Service 抛出 TensorException
    ↓
不调用 OperationLogger
    ↓
GlobalExceptionHandler 记录失败日志
    ↓
按 ErrorCode 映射 HTTP 状态和错误响应
```

`GlobalExceptionHandler` 保留 HTTP 映射职责，但删除按 URL 和 HTTP 方法判断 `QUERY_FAILED`、`PERSISTENCE_FAILED` 的逻辑。Service 负责稳定错误码，未识别异常统一映射为 `INTERNAL_ERROR`。

按照已确认取舍，失败仍有全局错误日志，但不再产生查询或下载失败指标，也不再记录操作级失败耗时。

## 架构约束

增加架构测试保证：

- `*Controller` 不依赖 Registry、Catalog、Repository 或 Plugin 实现；
- `*Controller` 不定义业务异常；
- `OperationLogger` 不依赖 `com.akkc.tensor.web..`；
- `tensor-core` 不依赖 Controller、HTTP 类型或 Web DTO；
- Web DTO 投影只发生在 Web 层；
- 没有多实现需求时不新增 Service 接口；
- 不新增通用反射框架、AOP 切面或空转发 Facade。

## 测试与验证

- 为 `MetadataQueryService` 增加插件可用、重复描述符、未知插件、数据集缺失和不支持元数据测试；
- 扩展 `DatasetQueryService` 测试，覆盖过滤能力、参数错误、分页和查询失败映射；
- 调整三个 Controller 测试，断言只进行输入转换、Service 调用和响应投影；
- 调整 `OperationLogger` 测试，覆盖成功、空结果、敏感参数过滤、耗时和指标内部失败；
- 调整 `GlobalExceptionHandler` 测试，删除路由推断并验证稳定错误码映射；
- 增加 Controller 与可观测性包依赖规则；
- 运行完整 Maven `test`、`verify`、格式检查和 Git 范围检查。

## 实施顺序

1. 新增 `MetadataQueryService` 并精简 `DataSourceController`。
2. 扩展 `DatasetQueryService` 并精简 `DatasetController`。
3. 将查询和持久化错误码下沉到对应 Service。
4. 将 `OperationLogger` 改为显式成功记录器。
5. 调整 `DatasetController`、`DownloadController` 的线性调用顺序。
6. 更新全局异常映射和架构测试。
7. 执行完整回归并记录验收证据。

## 验收条件

- Controller 不再包含插件、数据集或过滤条件业务规则；
- Controller 不直接访问 Registry、Catalog、Repository 或 Plugin；
- 查询和下载成功路径是显式的“Service → DTO → Logger → return”；
- `OperationLogger` 不执行 Service、不接受回调且不依赖 Web DTO；
- 失败只由 `GlobalExceptionHandler` 记录，不产生失败指标；
- HTTP 请求、响应、错误码和成功日志字段保持兼容；
- 完整测试、架构边界和构建验证通过。
