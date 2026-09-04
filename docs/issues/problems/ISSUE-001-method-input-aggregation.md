# ISSUE-001：Controller 方法入口参数不够聚合

## 当前阶段

范围已收缩为 Controller 专项。DownloadController 的 [参数聚合方案](../proposals/ISSUE-001-download-request-aggregation.md) 已确认，待整理为正式任务设计；尚未制定实施计划或修改生产代码。

## 问题描述

部分 Controller 方法直接接收多个彼此相关的独立参数，导致请求入口较为分散，参数之间的业务关系不能从方法签名中直接体现。

本问题只处理 Controller 的 HTTP 请求入参。非 Controller 方法的参数聚合问题暂不处理，后续如有需要再单独记录。

## Controller 入口清单

| 类与方法 | 当前入参 | 本轮关注点 |
| --- | ---: | --- |
| `DataSourceController.listDataSources` | 0 | 无需处理。 |
| `DataSourceController.listPluginApis` | 1 | 当前接收原始 `String pluginId`；待确认是否直接绑定为 `PluginId`。 |
| `DataSourceController.listPluginDatasets` | 1 | 当前接收原始 `String pluginId`；待确认是否直接绑定为 `PluginId`。 |
| `DataSourceController.getDatasetDefinition` | 2 | `pluginId` 与 `apiName` 共同标识数据集，可作为一个完整概念处理。 |
| `DatasetController.listDatasetRecords` | 9 | 数据集标识、过滤条件、两个日期范围及分页混合在同一入口。 |
| `DownloadController.download` | 1 | 虽已使用 `DownloadRequest`，但其中 `params` 仍是裸 `Map<String, Object>`，缺少明确的入口类型。 |

## `DownloadRequest.params` 现状

下载接口根据 `apiName` 选择数据集，而不同数据集的参数由运行时元数据描述，字段集合并不固定。当前实现因此使用 `Map<String, Object>` 接收动态 JSON 对象，并将它继续传递到校验器、插件和源端客户端。

这种实现支持动态参数，但 Controller 边界缺少类型安全、字段语义和可发现性。本轮必须为该入口设计明确类型，不再在 `DownloadRequest` 中直接暴露裸 `Map<String, Object>`。

## 本轮范围

- 审查并调整三个 Controller 的公开端点方法入参。
- 为 Controller 请求入口建立按接口语义命名的参数对象。
- 在必要时增加最小的 Spring MVC 绑定或解析支持。
- 调整直接受方法签名变化影响的 Controller 测试。

## 明确不在本轮范围

- 不修改现有 URL、HTTP 方法、查询参数名称、请求体字段或响应 JSON。
- 不修改服务层、核心层、插件层、持久化层或可观测性方法的参数设计。
- 不处理 Spring Bean 管理问题。
- 不创建适用于所有接口的通用查询对象或通用反射解析框架。
- 不升级依赖，不增加新功能。

## 已确认约束

- 按方法及业务语义封装参数，不强制所有查询使用同一种类型。
- 只有语义和约束一致的参数对象才允许复用。
- 单个参数已经具有明确类型时，不为了形式统一增加无意义的包装层。
- `DownloadRequest` 不得直接使用裸 `Map<String, Object>` 作为请求字段类型。
- 保持既有成功响应、错误响应和日志行为。
- 遵循仓库的最小代码原则。

## 方案设计阶段需要回答的问题

- 单个 `pluginId` 路径变量应直接绑定为 `PluginId`，还是继续使用 `String`？
- `pluginId + apiName` 应直接解析为现有 `DatasetKey`，还是在方法内部组装？
- `listDatasetRecords` 的查询条件如何分组，默认值和校验放在哪里？
- 下载参数应采用每个 API 的静态类型、统一的动态参数值对象，还是其他明确模型？
- 禁止裸 `Map<String, Object>` 的范围仅限 Controller 请求边界，还是覆盖整个下载调用链？
- 是否值得为 `DatasetKey` 增加专用 Spring MVC 参数解析器？
- 如何保持当前 `PARAM_INVALID`、字段错误和请求日志契约不变？

## 后续产物

1. 将已确认的 [DownloadController 参数聚合方案](../proposals/ISSUE-001-download-request-aggregation.md) 整理为正式设计文档。
2. 为其他 Controller 分别确认入参聚合方案。
3. 经确认的实施计划。
4. 可独立实施和验收的任务清单或任务板。
5. 实现、测试及验收证据。

## 关闭条件

- Controller 端点按已确认设计完成入参聚合或明确保留原签名。
- 现有 HTTP 契约、成功行为和错误行为保持兼容。
- Controller 相关测试和完整后端验证通过。
- 实施结果和验证证据已记录。
