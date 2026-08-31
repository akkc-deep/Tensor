# Tensor 任务索引

> 本文件是 M00–M14、77 个预定义任务的项目计划索引，只维护任务、交付物、工时、依赖和模块计划链接，不记录当前执行状态或运行时授权。

## 使用说明

- 总计 15 个模块、77 个任务、206 AI 小时；单任务不超过 4 AI 小时。
- 每个任务的项目设计文档使用 `docs/task-designs/<任务编号>-designs.md`。
- 已创建设计文档的任务必须在模块任务卡中提供 `Design` 链接，设计文档顶部同时链接回对应任务。
- 每个模块的详细目标、候选输入、文件边界、接口、步骤和验收保存在模块标题后的计划链接中。
- 本索引中的候选任务和依赖信息不表示任何任务已经被选择或启动。

## 模块汇总

| 模块 | 任务数 | AI 工时 | 详细计划 |
|---|---:|---:|---|
| M00 需求追踪与共享契约 | 4 | 5 | [M00](../superpowers/plans/tensor-modules/M00-contracts.md) |
| M01 后端工程基线 | 3 | 5 | [M01](../superpowers/plans/tensor-modules/M01-backend-foundation.md) |
| M02 Plugin API | 5 | 10 | [M02](../superpowers/plans/tensor-modules/M02-plugin-api.md) |
| M03 Tushare 数据集元数据 | 9 | 22 | [M03](../superpowers/plans/tensor-modules/M03-tushare-metadata.md) |
| M04 MySQL/Flyway | 6 | 21 | [M04](../superpowers/plans/tensor-modules/M04-flyway-schema.md) |
| M05 Core 注册与适配 | 5 | 16 | [M05](../superpowers/plans/tensor-modules/M05-core-registry-adapter.md) |
| M06 Core 持久化与查询 | 6 | 20 | [M06](../superpowers/plans/tensor-modules/M06-core-persistence-query.md) |
| M07 Tushare 插件 | 4 | 12 | [M07](../superpowers/plans/tensor-modules/M07-tushare-plugin.md) |
| M08 Fixture 插件 | 3 | 7 | [M08](../superpowers/plans/tensor-modules/M08-fixture-plugin.md) |
| M09 App/API | 6 | 15 | [M09](../superpowers/plans/tensor-modules/M09-app-api.md) |
| M10 前端工程基线 | 4 | 8 | [M10](../superpowers/plans/tensor-modules/M10-frontend-foundation.md) |
| M11 数据下载页面 | 5 | 12 | [M11](../superpowers/plans/tensor-modules/M11-download-ui.md) |
| M12 数据查看页面 | 5 | 14 | [M12](../superpowers/plans/tensor-modules/M12-dataset-ui.md) |
| M13 构建与运行 | 4 | 9 | [M13](../superpowers/plans/tensor-modules/M13-packaging-runbook.md) |
| M14 集成与发布验证 | 8 | 30 | [M14](../superpowers/plans/tensor-modules/M14-integration-release.md) |
| **总计** | **77** | **206** | 15 个模块计划 |

## M00 需求追踪与共享契约 — 5h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M00-T01 | BRD→PRD→TRD 双向追踪索引 | 1.0 | 无 |
| M00-T02 | 数据集元数据 JSON Schema 与示例 | 1.5 | M00-T01 |
| M00-T03 | `/api/v1` OpenAPI 契约与错误码目录 | 1.5 | M00-T01、M00-T02 |
| M00-T04 | Tensor 任务设计与验收证据模板 | 1.0 | M00-T01 |

## M01 后端工程基线 — 5h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M01-T01 | 五模块 Maven 聚合骨架 | 1.5 | M00-T04 |
| M01-T02 | Java 21、Boot 3.5.x 和测试依赖管理 | 1.5 | M01-T01 |
| M01-T03 | Maven Enforcer、ArchUnit 和禁止 Git 能力门禁 | 2.0 | M01-T02 |

## M02 Plugin API — 10h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M02-T01 | `PluginId`、`ApiName`、`DatasetKey`、`TableName`、`RequestId` | 1.5 | M01-T02 |
| M02-T02 | 参数、API、插件描述符和 readiness | 2.0 | M02-T01 |
| M02-T03 | 数据集字段、业务键、筛选和展示定义 | 2.5 | M02-T01、M02-T02 |
| M02-T04 | `DownloadEnvelope`、`AdaptedBatch` 和执行结果 | 2.0 | M02-T01、M02-T03 |
| M02-T05 | `DataSourcePlugin`、`DatasetAdapter` 和领域错误 | 2.0 | M00-T03、M02-T02～T04 |

## M03 Tushare 数据集元数据 — 22h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M03-T01 | YAML 加载、schema 校验和模板对照测试框架 | 2.0 | M00-T02、M02-T03 |
| M03-T02 | 基础与组织 11 数据集 YAML | 3.0 | M03-T01 |
| M03-T03 | 行情与估值 7 数据集 YAML | 2.5 | M03-T01 |
| M03-T04 | 交易与资金 6 数据集 YAML | 2.5 | M03-T01 |
| M03-T05 | 互联互通与转融通 6 数据集 YAML | 2.5 | M03-T01 |
| M03-T06 | 财务与披露 9 数据集 YAML | 4.0 | M03-T01 |
| M03-T07 | 公司行动 3 数据集 YAML | 2.0 | M03-T01 |
| M03-T08 | 股东与治理 7 数据集 YAML | 2.5 | M03-T01 |
| M03-T09 | 49/49 名称、字段、参数、键和筛选总契约 | 1.0 | M03-T02～T08 |

## M04 MySQL/Flyway — 21h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M04-T01 | V1 基础与组织表 | 3.5 | M03-T02 |
| M04-T02 | V2 行情、交易与资金表 | 3.5 | M03-T03～T04 |
| M04-T03 | V3 互联互通与转融通表 | 3.5 | M03-T05 |
| M04-T04 | V4 财务与披露宽表 | 3.5 | M03-T06 |
| M04-T05 | V5 公司行动、股东与治理表 | 3.5 | M03-T07～T08 |
| M04-T06 | V6 fixture 表与 49 表结构总校验 | 3.5 | M04-T01～T05 |

## M05 Core 注册与适配 — 16h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M05-T01 | `PluginRegistry` 与 `AdapterRegistry` | 3.0 | M02-T05 |
| M05-T02 | `DatasetCatalog` 和启动元数据/表结构校验 | 3.0 | M03-T09、M04-T06 |
| M05-T03 | 元数据驱动参数校验 | 3.0 | M02-T02、M03-T09 |
| M05-T04 | 严格日期、文本、整数和精确数值转换 | 3.5 | M02-T03 |
| M05-T05 | `GenericDatasetAdapter`、重复键和指纹键 | 3.5 | M05-T02～T04 |

## M06 Core 持久化与查询 — 20h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M06-T01 | 白名单 SQL 标识符和 Upsert 模板 | 3.0 | M02-T03、M04-T06 |
| M06-T02 | 复合键与指纹键编码/绑定 | 3.0 | M05-T05 |
| M06-T03 | 已有键预查、数据集锁和插入/更新计数 | 3.5 | M06-T01～T02 |
| M06-T04 | 单事务批量 Upsert 与回滚 | 4.0 | M06-T03 |
| M06-T05 | 查询条件白名单和 COUNT/分页 SQL | 3.5 | M02-T03、M04-T06 |
| M06-T06 | `DatasetQueryService`、页码归一化和精度序列化 | 3.0 | M06-T05 |

## M07 Tushare 插件 — 12h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M07-T01 | 配置属性和同步 `RestClient` | 2.5 | M02-T05 |
| M07-T02 | Tushare 请求、响应 DTO 和严格返回校验 | 3.0 | M03-T09、M07-T01 |
| M07-T03 | 鉴权、权限、限流、网络、超时和格式错误分类 | 2.5 | M07-T02 |
| M07-T04 | `TushareProPlugin` 描述符、readiness 和 49 接口下载 | 4.0 | M07-T02～T03 |

## M08 Fixture 插件 — 7h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M08-T01 | fixture 元数据、插件和适配器 | 2.5 | M02-T05、M04-T06 |
| M08-T02 | 成功、空、上游失败、适配失败和写入失败模式 | 2.5 | M08-T01 |
| M08-T03 | fixture 注册→适配→入库→查询集成测试 | 2.0 | M05-T05、M06-T06、M08-T02 |

## M09 App/API — 15h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M09-T01 | Boot 入口、请求标识和通用 API DTO | 2.5 | M01-T03、M02-T05 |
| M09-T02 | 数据源、接口和数据集元数据 API | 2.0 | M05-T02、M09-T01 |
| M09-T03 | 同步下载 API 与事务提交后结果 | 3.0 | M05-T03、M05-T05、M06-T04、M07-T04、M09-T01 |
| M09-T04 | 数据集定义与只读分页查询 API | 3.0 | M06-T06、M09-T01 |
| M09-T05 | 全局异常和 HTTP 状态映射 | 2.0 | M09-T02～T04 |
| M09-T06 | 配置、脱敏、指标、健康和静态资源安全 | 2.5 | M09-T01～T05 |

## M10 前端工程基线 — 8h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M10-T01 | Vue 依赖、Vitest、VTU 和 Playwright 配置 | 2.0 | M00-T03 |
| M10-T02 | `/downloads`、`/datasets` 路由和桌面布局 | 2.0 | M10-T01 |
| M10-T03 | Axios 客户端、DTO 和错误拦截 | 2.0 | M00-T03、M10-T01 |
| M10-T04 | 日期、空值、精度格式化和无障碍状态组件 | 2.0 | M10-T01 |

## M11 数据下载页面 — 12h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M11-T01 | 数据源与接口分组搜索选择组件 | 2.5 | M10-T03 |
| M11-T02 | 元数据驱动动态参数表单 | 3.0 | M10-T03～T04 |
| M11-T03 | 下载 composable、控件锁定和请求世代 | 3.0 | M10-T03 |
| M11-T04 | 成功、空和失败结果组件 | 1.5 | M10-T04、M11-T03 |
| M11-T05 | `DownloadView` 页面集成和组件回归 | 2.0 | M11-T01～T04 |

## M12 数据查看页面 — 14h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M12-T01 | 数据集选择与动态筛选表单 | 3.0 | M10-T03～T04 |
| M12-T02 | 全字段、固定列和横向滚动表格 | 3.5 | M10-T04 |
| M12-T03 | 20/50/100 分页组件 | 2.0 | M10-T04 |
| M12-T04 | 查询 composable、竞态和超界页处理 | 3.0 | M10-T03 |
| M12-T05 | `DatasetView` 页面集成和组件回归 | 2.5 | M12-T01～T04 |

## M13 构建与运行 — 9h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M13-T01 | 前端确定性构建及静态资源复制 | 2.5 | M10～M12 |
| M13-T02 | 单个可执行 JAR 打包和内容检查 | 2.5 | M09、M13-T01 |
| M13-T03 | 生产配置、CORS、SPA fallback 和优雅停机 | 2.0 | M09-T06、M13-T02 |
| M13-T04 | 全新环境运行说明和启动 smoke test | 2.0 | M13-T03 |

## M14 集成与发布验证 — 30h

| 任务 | 交付物 | 工时 | 前置 |
|---|---|---:|---|
| M14-T01 | fixture 页面端到端主闭环 | 4.0 | M13-T04 |
| M14-T02 | 下载失败、空结果、幂等和回滚矩阵 | 4.0 | M14-T01 |
| M14-T03 | 查询、分页、宽表、竞态和无障碍 E2E | 4.0 | M14-T01 |
| M14-T04 | 49 数据集自动契约与页面回归驱动 | 4.0 | M03-T09、M04-T06、M14-T01 |
| M14-T05 | 真实 Tushare 49 接口受控页面验收 | 4.0 | M14-T04 |
| M14-T06 | `daily` 与 `balancesheet` 性能验证 | 4.0 | M14-T03、M14-T05 |
| M14-T07 | Token、SQL、依赖、网络和运行安全验证 | 3.0 | M14-T02～T05 |
| M14-T08 | 全新环境 AC-001～018 与发布证据包 | 3.0 | M14-T01～T07 |
