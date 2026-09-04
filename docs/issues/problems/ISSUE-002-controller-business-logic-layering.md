# ISSUE-002：Controller 承担过多业务逻辑

## 当前阶段

[Controller 业务逻辑分层方案](../proposals/ISSUE-002-controller-service-layering.md) 已确认，待制定实施计划；尚未修改生产代码。

## 问题描述

当前后端已经存在 `DownloadService`、`DatasetQueryService` 和 `PersistenceService`，但部分用例的业务规则和编排仍位于 Controller。Controller 因此同时承担 HTTP 适配、资源可用性判断、查询能力校验、异常语义转换和可观测性编排，职责边界不够清晰。

本问题关注业务职责从 Controller 下沉，不以减少代码行数或统一增加 `Service` 接口为目标。

## 已知事实

### `DataSourceController`

- `listPluginApis` 直接遍历插件描述符，并判断插件是否唯一且允许下载。
- `listPluginDatasets` 和 `getDatasetDefinition` 在 Controller 内判断插件是否注册、数据集是否存在。
- Controller 内部定义 `MetadataAccessException`，将资源访问规则转换为业务错误码。
- DTO 投影属于 HTTP 输出适配，可以继续保留在 Web 层。

### `DatasetController`

- Controller 读取 `DatasetCatalog` 并判断数据集是否可用。
- Controller 根据数据集元数据判断允许使用哪些过滤条件。
- Controller 维护 `SUPPORTED_FILTERS`，决定当前查询实现支持的过滤能力。
- Controller 构造 `QueryCriteria`、收集日志字段、调用查询服务并转换部分异常。
- `DatasetQueryService` 当前主要负责 SQL 构造、总数查询、页码归一化和记录查询，没有覆盖完整查询用例。

### `DownloadController`

- 下载业务的主要编排已经位于 `DownloadService`。
- Controller 主要完成请求 DTO、`DatasetKey`、`RequestId` 和响应 DTO 的转换，职责相对合理。
- `OperationLogger` 的调用仍由 Controller 编排，是否调整应与可观测性边界一并设计。

### `OperationLogger`

- `OperationLogger` 直接依赖 `DownloadResponse` 和 `PageResponse` 等 Web DTO。
- 可观测性组件因此与 HTTP 输出模型耦合，难以复用于非 HTTP 调用入口。

## 期望职责边界

### Controller

- 声明路由和 Spring MVC 参数绑定。
- 从 HTTP 请求及 MDC 取得输入。
- 将 HTTP 输入转换为应用层或核心层输入。
- 调用一个明确的用例 Service。
- 将用例结果投影为响应 DTO。
- 保留 HTTP 状态、序列化和响应结构等 Web 语义。

### 用例 Service

- 判断插件、API 和数据集是否可用。
- 根据数据集定义校验查询能力及过滤条件。
- 编排 registry、catalog、repository、plugin 和 persistence 等协作者。
- 返回与 HTTP 无关的结果类型。
- 抛出稳定且可由全局异常处理器映射的业务异常。

### Repository 和 Plugin

- Repository 负责持久化及查询操作。
- Plugin 负责外部数据源能力和调用。
- 两者不依赖 Controller 或 Web DTO。

## 建议方向

1. 优先收敛 `DatasetController`，把数据集访问、过滤能力校验和完整查询编排下沉到现有 `DatasetQueryService`。
2. 为元数据读取建立最小的 `MetadataQueryService`，集中 `DataSourceController` 当前的插件和数据集访问规则。
3. 保持 `DownloadService` 的现有业务编排边界，仅审查请求转换和可观测性调用是否仍属于 Controller。
4. 让 `OperationLogger` 面向 `DownloadResult`、`DatasetPage` 等非 Web 结果，并在 Service 成功返回后显式记录，不再通过回调包裹用例调用。
5. 增加架构测试，防止 Controller 再次直接访问 Repository、Plugin 或承载业务规则。

具体边界和调用顺序已由关联方案确认，实施细节不得改变该方案冻结的职责和错误语义。

## 约束

- 保持现有 URL、HTTP 方法、请求字段、响应 JSON、错误码、成功日志和成功/空结果指标兼容；按已确认方案取消操作级失败指标和失败耗时。
- 保持 `tensor-core` 不依赖 Spring MVC 或 Web DTO。
- 使用具体的用例 Service 即可；没有多实现需求时不增加 Service 接口。
- 不创建覆盖所有用例的大一统 Service、通用反射框架或形式化空转发层。
- 优先复用现有 `DatasetQueryService` 和 `DownloadService`，遵循最小代码原则。
- 与 ISSUE-001 的 Controller 入参聚合保持独立，避免在一次变更中同时重做输入模型和业务分层。

## 非目标

- 不在本问题记录阶段修改生产代码。
- 不改变数据库结构、插件协议或数据集元数据格式。
- 不增加新的 API 功能。
- 不因分层重构改变客户端可观察行为。

## 已确认决策

- 查询用例逻辑扩展现有 `DatasetQueryService`，不增加应用层空转发门面。
- `MetadataQueryService` 位于 `tensor-core`，使用具体类而非接口。
- Core Service 抛出稳定业务错误码，Web 层只负责 HTTP 状态和错误响应映射。
- Controller 先调用 Service、构造响应，再显式调用 `OperationLogger` 记录成功结果；不使用 `Supplier` 或 `finally`。
- 失败由 `GlobalExceptionHandler` 记录，不再产生查询或下载失败指标。
- 架构测试约束 Controller 依赖和 `OperationLogger` 的 Web 隔离，不限制 Web 层合理的 DTO 投影。
- ISSUE-001 与本问题独立设计和实施，不在一次变更中同时调整输入模型与业务分层。

## 后续产物

1. 已确认的 [Controller 业务逻辑分层方案](../proposals/ISSUE-002-controller-service-layering.md)。
2. 保持 HTTP 契约兼容的实施计划。
3. 可独立验收的 Controller、Service、可观测性和架构测试任务。
4. 实现、回归测试及验收证据。

## 关闭条件

- Controller 只保留 HTTP 适配、输入转换、用例调用和响应投影。
- 插件/数据集可用性及查询能力规则由用例 Service 统一执行。
- Controller 不直接访问 Repository 或调用 Plugin。
- 核心用例结果和可观测性逻辑不依赖 Web DTO。
- HTTP 契约、错误码、成功日志和成功/空结果指标保持兼容，失败指标按已确认方案取消。
- 用例 Service、Controller 和架构边界测试通过完整后端验证。
- 实施结果及验证证据已记录。
