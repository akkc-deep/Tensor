# ISSUE-001：Controller 方法入口参数不够聚合

## 当前阶段

已解决（2026-09-07）。按[正式设计](../../task-designs/ISSUE-001-designs.md)完成 Controller 请求边界聚合；624 项后端单元/集成测试、170 项前端测试和 7 项生产/验收包合同检查通过。独立代码审查及修复复审通过，见[验收记录](../../verification/ISSUE-001-controller-inputs.md)。

## 问题描述

原有部分 Controller 将彼此相关的 HTTP 入参分散在方法签名中，下载请求的 `params` 直接使用裸 `Map<String, Object>`，缺少明确的入口类型。本问题仅处理 Controller 请求边界。

## Controller 入口处理结果

| 类与方法 | 最终入参 | 处理结果 |
| --- | --- | --- |
| `DataSourceController.listDataSources` | 无参 | 保留。 |
| `DataSourceController.listPluginApis` | 单个 `String pluginId` | 明确保留：无需聚合；保持原非法标识符错误字段行为。 |
| `DataSourceController.listPluginDatasets` | 单个 `String pluginId` | 同上。 |
| `DataSourceController.getDatasetDefinition` | `DatasetPath` | 聚合路径标识，保留插件检查先于 API 转换的错误顺序。 |
| `DatasetController.listDatasetRecords` | `DatasetRecordsRequest` | 聚合路径、证券代码、交易日期范围、公告日期范围与分页。 |
| `DownloadController.download` | `DownloadRequest` | 使用 `DatasetKey`、13 种明确参数子类型与已提交字段名集合。 |

## 已实现约束

- 按接口语义封装，不建立通用反射解析框架。
- URL、HTTP 方法、查询参数名称、请求体字段与响应 JSON 保持兼容。
- `DownloadRequest` 不再直接声明裸 Map；私有临时 wire record 仅保持 Jackson 原有结构及重复字段解析，随后必须转换为明确参数类型。
- 49 个 Tushare API 与 fixture 唯一匹配 13 种 Codec；未知参数结构返回 `DATASET_MISCONFIGURED`，不回退 Map。
- 缺失与显式 null、默认值、多错误优先级、字段错误和操作完成日志均保留。
- 查询参数的转换/校验仍在日志边界内，保留 ISSUE-012 白名单和 ISSUE-013 完成事件行为。
- Service、核心、插件、持久化和可观测性生产代码及接口保持不变，不升级依赖。

## 实施产物

1. [已确认下载架构方案](../proposals/ISSUE-001-download-request-aggregation.md)。
2. [正式设计](../../task-designs/ISSUE-001-designs.md)：三个 Controller 的最终决策、文件、绑定与失败规则。
3. [实施计划及任务清单](../../superpowers/plans/2026-09-07-issue-001-controller-inputs.md)：GET、下载、完整验证三个可独立验收任务。
4. [实现与验收证据](../../verification/ISSUE-001-controller-inputs.md)。

## 关闭条件

- [x] Controller 端点完成入参聚合或明确保留原签名。
- [x] 现有 HTTP 契约、成功行为和错误行为保持兼容。
- [x] Controller 相关测试和完整后端验证通过。
- [x] 实施结果和验证证据已记录。
