# Tensor v1 Project Task Board

## Project

- **Project ID:** `tensor-v1`.
- **Goal:** 按契约优先的模块化单体路线图交付 Tensor v1：完成 49 个 Tushare Pro 数据集的下载、适配、单事务 Upsert、只读查询、Vue 控制面、单 JAR 打包及发布验证。
- **Scope:** 包含 M00–M14 的 77 个预定义任务；排除路线图明确范围外的热加载、外部插件 JAR、任务队列、登录权限、导出、插件市场，以及后续发现后另行规划的缺陷修复任务。
- **Completion condition:** 77 个预定义任务全部为 `COMPLETED`，其任务验收和模块门禁全部通过，并满足路线图中 49/49 数据集契约、AC-001～018、性能、安全、单 JAR 和全新环境页面闭环条件。

## Workflow

- **Authority:** This board is the sole authoritative source for task identity, order, definition, dependencies, status, design documents, and handoffs.
- **Execution:** Serial execution is owned by the user; the board does not enforce cross-task exclusion.
- **Next-task selection:** Choose the non-completed task with the smallest greater `Order` after the current task completes.
- **Successor preparation:** Complete and link the selected successor's design document before writing its `next-task` handoff or making it `READY`; a successor-design failure never changes the completed predecessor.
- **Allowed transitions:** `NOT_STARTED -> READY`, `READY -> IN_PROGRESS`, `IN_PROGRESS -> PAUSED`, `PAUSED -> IN_PROGRESS`, `READY -> BLOCKED`, `IN_PROGRESS -> BLOCKED`, `BLOCKED -> READY`, `IN_PROGRESS -> COMPLETED`.

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
|---:|---|---|---|---|---|---|
| 1 | M00-T01 | BRD→PRD→TRD 双向追踪索引 | `COMPLETED` | None | docs/task-designs/M00-T01-designs.md | docs/task-handoffs/M00-T01-handoff.md |
| 2 | M00-T02 | 数据集元数据 JSON Schema 与示例 | `COMPLETED` | M00-T01 | docs/task-designs/M00-T02-designs.md | docs/task-handoffs/M00-T02-handoff.md |
| 3 | M00-T03 | `/api/v1` OpenAPI 契约与错误码目录 | `COMPLETED` | M00-T01, M00-T02 | docs/task-designs/M00-T03-designs.md | docs/task-handoffs/M00-T03-handoff.md |
| 4 | M00-T04 | Tensor 任务设计与验收证据模板 | `COMPLETED` | M00-T01 | docs/task-designs/M00-T04-designs.md | docs/task-handoffs/M00-T04-handoff.md |
| 5 | M01-T01 | 五模块 Maven 聚合骨架 | `COMPLETED` | M00-T04 | docs/task-designs/M01-T01-designs.md | docs/task-handoffs/M01-T01-handoff.md |
| 6 | M01-T02 | Java 21、Boot 3.5.x 和测试依赖管理 | `COMPLETED` | M01-T01 | docs/task-designs/M01-T02-designs.md | docs/task-handoffs/M01-T02-handoff.md |
| 7 | M01-T03 | Maven Enforcer、ArchUnit 和禁止 Git 能力门禁 | `COMPLETED` | M01-T02 | docs/task-designs/M01-T03-designs.md | docs/task-handoffs/M01-T03-handoff.md |
| 8 | M02-T01 | `PluginId`、`ApiName`、`DatasetKey`、`TableName`、`RequestId` | `COMPLETED` | M01-T02 | docs/task-designs/M02-T01-designs.md | docs/task-handoffs/M02-T01-handoff.md |
| 9 | M02-T02 | 参数、API、插件描述符和 readiness | `COMPLETED` | M02-T01 | docs/task-designs/M02-T02-designs.md | docs/task-handoffs/M02-T02-handoff.md |
| 10 | M02-T03 | 数据集字段、业务键、筛选和展示定义 | `COMPLETED` | M02-T01, M02-T02 | docs/task-designs/M02-T03-design.md | docs/task-handoffs/M02-T03-handoff.md |
| 11 | M02-T04 | `DownloadEnvelope`、`AdaptedBatch` 和执行结果 | `COMPLETED` | M02-T01, M02-T03 | docs/task-designs/M02-T04-design.md | docs/task-handoffs/M02-T04-handoff.md |
| 12 | M02-T05 | `DataSourcePlugin`、`DatasetAdapter` 和领域错误 | `COMPLETED` | M00-T03, M02-T02, M02-T03, M02-T04 | docs/task-designs/M02-T05-design.md | docs/task-handoffs/M02-T05-handoff.md |
| 13 | M03-T01 | YAML 加载、schema 校验和模板对照测试框架 | `COMPLETED` | M00-T02, M02-T03 | docs/task-designs/M03-T01-design.md | docs/task-handoffs/M03-T01-handoff.md |
| 14 | M03-T02 | 基础与组织 11 数据集 YAML | `COMPLETED` | M03-T01 | docs/task-designs/M03-T02-design.md | docs/task-handoffs/M03-T02-handoff.md |
| 15 | M03-T03 | 行情与估值 7 数据集 YAML | `COMPLETED` | M03-T01 | docs/task-designs/M03-T03-design.md | docs/task-handoffs/M03-T03-handoff.md |
| 16 | M03-T04 | 交易与资金 6 数据集 YAML | `COMPLETED` | M03-T01 | docs/task-designs/M03-T04-design.md | docs/task-handoffs/M03-T04-handoff.md |
| 17 | M03-T05 | 互联互通与转融通 6 数据集 YAML | `COMPLETED` | M03-T01 | docs/task-designs/M03-T05-design.md | docs/task-handoffs/M03-T05-handoff.md |
| 18 | M03-T06 | 财务与披露 9 数据集 YAML | `COMPLETED` | M03-T01 | docs/task-designs/M03-T06-design.md | docs/task-handoffs/M03-T06-handoff.md |
| 19 | M03-T07 | 公司行动 3 数据集 YAML | `COMPLETED` | M03-T01 | docs/task-designs/M03-T07-design.md | docs/task-handoffs/M03-T07-handoff.md |
| 20 | M03-T08 | 股东与治理 7 数据集 YAML | `COMPLETED` | M03-T01 | docs/task-designs/M03-T08-design.md | docs/task-handoffs/M03-T08-handoff.md |
| 21 | M03-T09 | 49/49 名称、字段、参数、键和筛选总契约 | `COMPLETED` | M03-T02, M03-T03, M03-T04, M03-T05, M03-T06, M03-T07, M03-T08 | docs/task-designs/M03-T09-design.md | docs/task-handoffs/M03-T09-handoff.md |
| 22 | M04-T01 | V1 基础与组织表 | `COMPLETED` | M03-T02 | docs/task-designs/M04-T01-design.md | docs/task-handoffs/M04-T01-handoff.md |
| 23 | M04-T02 | V2 行情、交易与资金表 | `COMPLETED` | M03-T03, M03-T04 | docs/task-designs/M04-T02-design.md | docs/task-handoffs/M04-T02-handoff.md |
| 24 | M04-T03 | V3 互联互通与转融通表 | `COMPLETED` | M03-T05 | docs/task-designs/M04-T03-design.md | docs/task-handoffs/M04-T03-handoff.md |
| 25 | M04-T04 | V4 财务与披露宽表 | `COMPLETED` | M03-T06 | docs/task-designs/M04-T04-design.md | docs/task-handoffs/M04-T04-handoff.md |
| 26 | M04-T05 | V5 公司行动、股东与治理表 | `COMPLETED` | M03-T07, M03-T08 | docs/task-designs/M04-T05-design.md | docs/task-handoffs/M04-T05-handoff.md |
| 27 | M04-T06 | V6 fixture 表与 49 表结构总校验 | `COMPLETED` | M04-T01, M04-T02, M04-T03, M04-T04, M04-T05 | docs/task-designs/M04-T06-design.md | docs/task-handoffs/M04-T06-handoff.md |
| 28 | M05-T01 | `PluginRegistry` 与 `AdapterRegistry` | `COMPLETED` | M02-T05 | docs/task-designs/M05-T01-design.md | docs/task-handoffs/M05-T01-handoff.md |
| 29 | M05-T02 | `DatasetCatalog` 和启动元数据/表结构校验 | `COMPLETED` | M03-T09, M04-T06 | docs/task-designs/M05-T02-design.md | docs/task-handoffs/M05-T02-handoff.md |
| 30 | M05-T03 | 元数据驱动参数校验 | `COMPLETED` | M02-T02, M02-T05, M03-T09 | docs/task-designs/M05-T03-design.md | docs/task-handoffs/M05-T03-handoff.md |
| 31 | M05-T04 | 严格日期、文本、整数和精确数值转换 | `COMPLETED` | M02-T03, M02-T05 | docs/task-designs/M05-T04-design.md | docs/task-handoffs/M05-T04-handoff.md |
| 32 | M05-T05 | `GenericDatasetAdapter`、重复键和指纹键 | `COMPLETED` | M02-T04, M02-T05, M05-T02, M05-T03, M05-T04 | docs/task-designs/M05-T05-design.md | docs/task-handoffs/M05-T05-handoff.md |
| 33 | M06-T01 | 白名单 SQL 标识符和 Upsert 模板 | `COMPLETED` | M02-T03, M04-T06 | docs/task-designs/M06-T01-design.md | docs/task-handoffs/M06-T01-handoff.md |
| 34 | M06-T02 | 复合键与指纹键编码/绑定 | `COMPLETED` | M05-T05 | docs/task-designs/M06-T02-design.md | docs/task-handoffs/M06-T02-handoff.md |
| 35 | M06-T03 | 已有键预查、数据集锁和插入/更新计数 | `COMPLETED` | M06-T01, M06-T02 | docs/task-designs/M06-T03-design.md | docs/task-handoffs/M06-T03-handoff.md |
| 36 | M06-T04 | 单事务批量 Upsert 与回滚 | `COMPLETED` | M06-T03 | docs/task-designs/M06-T04-design.md | docs/task-handoffs/M06-T04-handoff.md |
| 37 | M06-T05 | 查询条件白名单和 COUNT/分页 SQL | `COMPLETED` | M02-T03, M04-T06 | docs/task-designs/M06-T05-design.md | docs/task-handoffs/M06-T05-handoff.md |
| 38 | M06-T06 | `DatasetQueryService`、页码归一化和精度序列化 | `COMPLETED` | M06-T05 | docs/task-designs/M06-T06-design.md | docs/task-handoffs/M06-T06-handoff.md |
| 39 | M07-T01 | 配置属性和同步 `RestClient` | `COMPLETED` | M02-T05 | docs/task-designs/M07-T01-design.md | docs/task-handoffs/M07-T01-handoff.md |
| 40 | M07-T02 | Tushare 请求、响应 DTO 和严格返回校验 | `COMPLETED` | M03-T09, M07-T01 | docs/task-designs/M07-T02-design.md | docs/task-handoffs/M07-T02-handoff.md |
| 41 | M07-T03 | 鉴权、权限、限流、网络、超时和格式错误分类 | `COMPLETED` | M07-T02 | docs/task-designs/M07-T03-design.md | docs/task-handoffs/M07-T03-handoff.md |
| 42 | M07-T04 | `TushareProPlugin` 描述符、readiness 和 49 接口下载 | `COMPLETED` | M07-T02, M07-T03 | docs/task-designs/M07-T04-design.md | docs/task-handoffs/M07-T04-handoff.md |
| 43 | M08-T01 | fixture 元数据、插件和适配器 | `COMPLETED` | M02-T05, M04-T06, M05-T05 | docs/task-designs/M08-T01-design.md | docs/task-handoffs/M08-T01-handoff.md |
| 44 | M08-T02 | 成功、空、上游失败、适配失败和写入失败模式 | `COMPLETED` | M08-T01 | docs/task-designs/M08-T02-design.md | docs/task-handoffs/M08-T02-handoff.md |
| 45 | M08-T03 | fixture 注册→适配→入库→查询集成测试 | `COMPLETED` | M05-T01, M05-T05, M06-T04, M06-T06, M08-T02 | docs/task-designs/M08-T03-design.md | docs/task-handoffs/M08-T03-handoff.md |
| 46 | M09-T01 | Boot 入口、请求标识和通用 API DTO | `COMPLETED` | M01-T03, M02-T01, M02-T05 | docs/task-designs/M09-T01-design.md | docs/task-handoffs/M09-T01-handoff.md |
| 47 | M09-T02 | 数据源、接口和数据集元数据 API | `COMPLETED` | M05-T01, M05-T02, M09-T01 | docs/task-designs/M09-T02-design.md | docs/task-handoffs/M09-T02-handoff.md |
| 48 | M09-T03 | 同步下载 API 与事务提交后结果 | `COMPLETED` | M05-T01, M05-T03, M05-T05, M06-T04, M07-T04, M09-T01 | docs/task-designs/M09-T03-design.md | docs/task-handoffs/M09-T03-handoff.md |
| 49 | M09-T04 | 数据集定义与只读分页查询 API | `COMPLETED` | M05-T02, M06-T06, M09-T01 | docs/task-designs/M09-T04-design.md | docs/task-handoffs/M09-T04-handoff.md |
| 50 | M09-T05 | 全局异常和 HTTP 状态映射 | `COMPLETED` | M09-T02, M09-T03, M09-T04 | docs/task-designs/M09-T05-design.md | docs/task-handoffs/M09-T05-handoff.md |
| 51 | M09-T06 | 配置、脱敏、指标、健康和静态资源安全 | `COMPLETED` | M09-T01, M09-T02, M09-T03, M09-T04, M09-T05 | docs/task-designs/M09-T06-design.md | docs/task-handoffs/M09-T06-handoff.md |
| 52 | M10-T01 | Vue 依赖、Vitest、VTU 和 Playwright 配置 | `COMPLETED` | M00-T03 | docs/task-designs/M10-T01-design.md | docs/task-handoffs/M10-T01-handoff.md |
| 53 | M10-T02 | `/downloads`、`/datasets` 路由和桌面布局 | `COMPLETED` | M10-T01 | docs/task-designs/M10-T02-design.md | docs/task-handoffs/M10-T02-handoff.md |
| 54 | M10-T03 | Axios 客户端、DTO 和错误拦截 | `COMPLETED` | M00-T03, M10-T01 | docs/task-designs/M10-T03-design.md | docs/task-handoffs/M10-T03-handoff.md |
| 55 | M10-T04 | 日期、空值、精度格式化和无障碍状态组件 | `COMPLETED` | M10-T01 | docs/task-designs/M10-T04-design.md | docs/task-handoffs/M10-T04-handoff.md |
| 56 | M11-T01 | 数据源与接口分组搜索选择组件 | `COMPLETED` | M10-T03 | docs/task-designs/M11-T01-design.md | docs/task-handoffs/M11-T01-handoff.md |
| 57 | M11-T02 | 元数据驱动动态参数表单 | `COMPLETED` | M10-T03, M10-T04 | docs/task-designs/M11-T02-design.md | docs/task-handoffs/M11-T02-handoff.md |
| 58 | M11-T03 | 下载 composable、控件锁定和请求世代 | `NOT_STARTED` | M10-T03 | docs/task-designs/M11-T03-design.md | None |
| 59 | M11-T04 | 成功、空和失败结果组件 | `NOT_STARTED` | M10-T04, M11-T03 | None | None |
| 60 | M11-T05 | `DownloadView` 页面集成和组件回归 | `NOT_STARTED` | M11-T01, M11-T02, M11-T03, M11-T04 | None | None |
| 61 | M12-T01 | 数据集选择与动态筛选表单 | `NOT_STARTED` | M10-T03, M10-T04 | None | None |
| 62 | M12-T02 | 全字段、固定列和横向滚动表格 | `NOT_STARTED` | M10-T04 | None | None |
| 63 | M12-T03 | 20/50/100 分页组件 | `NOT_STARTED` | M10-T04 | None | None |
| 64 | M12-T04 | 查询 composable、竞态和超界页处理 | `NOT_STARTED` | M10-T03 | None | None |
| 65 | M12-T05 | `DatasetView` 页面集成和组件回归 | `NOT_STARTED` | M12-T01, M12-T02, M12-T03, M12-T04 | None | None |
| 66 | M13-T01 | 前端确定性构建及静态资源复制 | `NOT_STARTED` | M10-T02, M11-T05, M12-T05 | None | None |
| 67 | M13-T02 | 单个可执行 JAR 打包和内容检查 | `NOT_STARTED` | M09-T06, M13-T01 | None | None |
| 68 | M13-T03 | 生产配置、CORS、SPA fallback 和优雅停机 | `NOT_STARTED` | M09-T06, M13-T02 | None | None |
| 69 | M13-T04 | 全新环境运行说明和启动 smoke test | `NOT_STARTED` | M13-T03 | None | None |
| 70 | M14-T01 | fixture 页面端到端主闭环 | `NOT_STARTED` | M13-T04 | None | None |
| 71 | M14-T02 | 下载失败、空结果、幂等和回滚矩阵 | `NOT_STARTED` | M14-T01 | None | None |
| 72 | M14-T03 | 查询、分页、宽表、竞态和无障碍 E2E | `NOT_STARTED` | M14-T01 | None | None |
| 73 | M14-T04 | 49 数据集自动契约与页面回归驱动 | `NOT_STARTED` | M03-T09, M04-T06, M14-T01 | None | None |
| 74 | M14-T05 | 真实 Tushare 49 接口受控页面验收 | `NOT_STARTED` | M14-T04 | None | None |
| 75 | M14-T06 | `daily` 与 `balancesheet` 性能验证 | `NOT_STARTED` | M14-T03, M14-T05 | None | None |
| 76 | M14-T07 | Token、SQL、依赖、网络和运行安全验证 | `NOT_STARTED` | M14-T02, M14-T03, M14-T04, M14-T05 | None | None |
| 77 | M14-T08 | 全新环境 AC-001～018 与发布证据包 | `NOT_STARTED` | M14-T01, M14-T02, M14-T03, M14-T04, M14-T05, M14-T06, M14-T07 | None | None |

## Task Details

### `M00-T01`

- **Goal:** 交付“BRD→PRD→TRD 双向追踪索引”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “BRD→PRD→TRD 双向追踪索引”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** None.
- **Sources:** `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 `Task M00-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 `Task M00-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-30：用户明确要求按照权威任务看板执行当前任务，作为首次 `READY -> IN_PROGRESS` 的启动证据。实施与独立审查确认：授权源未定义五类非功能需求的 BRD 值，也未提供逐项 PRD/NFR→AC 交叉表；设计要求七列非空、缺少映射时停止且当前任务禁止修改设计，因此按 `docs/task-handoffs/M00-T01-handoff.md` 执行 `IN_PROGRESS -> BLOCKED`。解阻条件是项目所有者批准的设计或授权矩阵明确给出这些映射/表示规则。随后用户回复“同意”，批准以 `N/A（BRD 未定义）` 表示无 BRD 映射的 NFR，并区分直接 AC、部分覆盖 AC 与 PRD 内联验收；`docs/task-designs/M00-T01-designs.md` 已记录 6 项 NFR→BRD 裁决和 37 项 Acceptance 交叉表，满足交接中的解阻条件，因此执行 `BLOCKED -> READY`。用户再次回复“同意”，明确授权按修订后的设计继续执行 M00-T01，作为本次 `READY -> IN_PROGRESS` 的启动证据。`docs/traceability/tensor-v1-requirements.md` 最终包含按序排列的 31 项功能需求和 6 项非功能要求、固定七列、完整 FR/AC 覆盖，并与设计中的 6 项 NFR→BRD 裁决及 37 项 Acceptance 交叉表逐项一致；结构契约、映射裁决、标识完整性、空值/占位符、受保护输入哈希和相对链接六项最终验证均退出码 0，独立审查结论为 `Ready to merge: Yes` 且无 Critical/Important 实施问题，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M00-T02`

- **Goal:** 交付“数据集元数据 JSON Schema 与示例”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据集元数据 JSON Schema 与示例”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 `Task M00-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 `Task M00-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-30：M00-T01 已在权威看板中完成；M00-T02 直接消费其产物 `docs/traceability/tensor-v1-requirements.md` 中 `PRD-F-002`、`PRD-F-004`、`PRD-F-007`、`PRD-F-008`、`PRD-F-015`、`PRD-F-016`、`PRD-F-019`、`PRD-F-020`、`PRD-F-024`、`PRD-F-025`、`PRD-F-027` 和 `PRD 10.4` 共 12 行，任务卡、TRD 5.3/8.1 与 `docs/data-template/manifest.json` 均可定位且约束无冲突；`docs/task-handoffs/M00-T02-handoff.md` 已按 `next-task` 模板创建并记录这些直接输入，因此执行 `NOT_STARTED -> READY`。随后用户明确要求按权威任务看板执行当前任务；完整读取既有交接并核对任务卡、TRD 5.3/8.1/8.2 与 `daily` 字段基线后，确认授权输入没有唯一规定 `businessKey` 如何同时表达字段列表和 `COMPOSITE|FINGERPRINT` 模式，也没有规定固定列的表示位置，且看板尚无 M00-T02 设计文档；这些选择会改变后续模块消费的公开契约，不能由实施者猜测。`docs/task-handoffs/M00-T02-handoff.md` 已改写为 `pause` 交接，解阻条件是项目所有者批准可写入任务设计的精确 schema 字段结构，因此执行 `READY -> BLOCKED`。用户回复“同意”，批准 `businessKey: {mode, fields}`、`filters: string[]`、根级可选 `fixedColumn`、TRD 5.3 参数字段及列展示元数据结构；`docs/task-designs/M00-T02-designs.md` 已固化该裁决和精确测试/验收命令，任务卡与看板均已回填同一设计路径，设计五标题、链接和占位符门禁通过，满足暂停交接的解阻条件，因此执行 `BLOCKED -> READY`。用户再次回复“同意”，明确授权按 `docs/task-designs/M00-T02-designs.md` 继续实施 M00-T02，作为本次 `READY -> IN_PROGRESS` 的启动证据；现有 `pause` 交接路径保留为历史与恢复上下文。最终 `docs/contracts/dataset-definition.schema.json` 为合法 JSON Schema 2020-12，恰含十个必填根字段、可选 `fixedColumn`、批准的正则/枚举/条件字段及封闭对象；`docs/contracts/dataset-definition.example.yaml` 通过该 schema，包含与模板同序的 11 个 `daily` 字段、连续展示顺序及批准的业务键/筛选/固定列。修复后 schema/example 正向校验、任务卡 `jq` 门禁、7/7 结构反例、`daily` 顺序/引用、精确契约/链接/状态/条件守卫及缺失判别字段诊断七项最终验证均退出码 0；任务级审查为规范符合且质量 `Approved`，最终整体审查为 `Ready to merge: Yes`，唯一修复波的两项 Minor 均经范围化复审确认已解决且无新 Critical/Important 问题。范围检查确认 `docs/contracts/` 仅有两个目标产物；项目无 Git 元数据且未初始化。因此满足任务设计和任务卡验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M00-T03`

- **Goal:** 交付“`/api/v1` OpenAPI 契约与错误码目录”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`/api/v1` OpenAPI 契约与错误码目录”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T01, M00-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 `Task M00-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 `Task M00-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-30：M00-T02 已按设计和任务卡完成；准备预定义后继任务时发现 M00-T03 任务卡明确要求复用 M00-T02 标识符正则，而原看板和规划索引只列 M00-T01。项目所有者批准把 M00-T03 依赖修订为 `M00-T01, M00-T02`，权威看板详情与 `docs/planning/task-index.md` 已同步。M00-T01 的追踪索引与 M00-T02 的 schema/设计均可定位、状态为 `COMPLETED`，其决策和约束无冲突；原 `next-task` 交接在设计形成前记录了首个设计动作，并按当时流程执行 `NOT_STARTED -> READY`。此后 `docs/task-designs/M00-T03-designs.md` 已完成并回填；`docs/task-handoffs/M00-T03-handoff.md` 已按新模板刷新，记录同一设计路径、两个直接输入、读取顺序和首个实施动作。M00-T03 保持 `READY`，未记录新的状态转换。2026-08-31：用户明确要求按照权威任务看板执行当前任务；已完整读取 M00-T03 设计、既有 `next-task` 交接、任务卡及其直接依赖输入，确认任务身份、范围、输入和首个动作均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。最终 `docs/contracts/openapi-v1.yaml` 以合法无重复键的 OpenAPI 3.1 YAML 冻结六条业务路径、九个公开 schema、请求关联头、同步下载与分页/精度/固定列语义；`docs/contracts/error-codes.md` 冻结与 `ApiError.code` 完全一致的 16 项 HTTP/retryable 矩阵。实施前两项缺失产物门禁均按预期退出 1；最终设计 7 项 GREEN 门禁无失败（敏感词扫描按预期退出 1 且无输出），严格 YAML 加载、事务/筛选/列顺序语义断言和四类示例 JSON Schema 校验均退出 0。任务级审查的 3 组 Important 已在修复轮次 1 全部解决；最终整体审查的 3 项 Important 与 2 项 Minor 已在唯一修复波全部解决，范围化复审结论为全部 addressed 且无新 Critical/Important。最终验证期间 Git 状态已变为可用，`main`/`origin/main` 的 `068f001` 已跟踪两个产物且工作树干净，因此未重写既有提交。满足任务设计、任务卡、范围与审查门禁，执行 `IN_PROGRESS -> COMPLETED`。

### `M00-T04`

- **Goal:** 交付“Tensor 任务设计与验收证据模板”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Tensor 任务设计与验收证据模板”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 `Task M00-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 `Task M00-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：M00-T03 已按设计、任务卡、完整 GREEN 验证及两级独立审查完成。按权威看板预定义顺序选择 M00-T04；其直接依赖 M00-T01 为 `COMPLETED`，`docs/traceability/tensor-v1-requirements.md` 提供的稳定 requirement/acceptance 标识及“Evidence 仅表示计划责任”语义与 M00-T04 模板职责无冲突。`docs/task-designs/M00-T04-designs.md` 已完成、五个固定标题/占位词/反向链接门禁通过并回填同一精确路径；`docs/task-handoffs/M00-T04-handoff.md` 已按 `next-task` 模板创建，记录唯一直接依赖、读取顺序和从完整设计开始的首个实施动作。先链接交接路径，再执行 `NOT_STARTED -> READY`。随后用户澄清交接门禁必须完成下一任务的实际设计，而非仅创建结构化模板说明；复核确认原设计仍要求实施者自行组织两份目标正文。设计已在同一路径修订为两个带 `BEGIN`/`END` 标记的完整、逐字实现契约，并新增两份实现文件与嵌入正文的自动同步门禁；交接的验收条件、首个动作和风险已同步刷新。嵌入区块数量、模板标题/字段、设计五标题和占位词检查均通过，M00-T04 保持 `READY`，未记录新的状态转换。用户本轮明确同意保留现有变更并直接在当前 `main` 工作区按该设计实施 M00-T04，作为 `READY -> IN_PROGRESS` 的启动证据；既有 `next-task` 交接路径保留为进入上下文。两份目标模板已按设计冻结正文逐字创建：缺失产物 RED 门禁先因 `task-design.md` 不存在退出 1；实现后正文同步、五标题/元数据、任务卡字段、精确证据列四项正向门禁均退出 0，越权运行时职责扫描无输出并按预期退出 1。设计标题、未决词、目标目录仅含两文件、双向链接和 `git diff --check` 均退出 0；M00 需求追踪、schema/example、OpenAPI 六路径/九 schema 和模板总门禁退出 0，任务卡列出的 PRD/AC、反例、敏感词及错误码回归也均得到各自预期结果。逐条范围自审未发现 Critical、Important 或 Minor 偏离，且未修改生产代码、既有契约或需求基线；因此满足结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M01-T01`

- **Goal:** 交付“五模块 Maven 聚合骨架”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “五模块 Maven 聚合骨架”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 `Task M01-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 `Task M01-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：M00-T04 已依据冻结正文、任务级 RED/GREEN、范围/链接检查和 M00 模块总门禁完成，并在权威看板中为 `COMPLETED`；按预定义顺序选择后继 M01-T01。直接输入 `docs/superpowers/task-templates/task-design.md` 与 `docs/superpowers/task-templates/acceptance-evidence.md` 均可定位，五标题设计结构、结果证据字段及不承载运行时看板职责的约束与 M01-T01 任务卡、TRD 3.3 和现有 `data-plane/pom.xml` 无冲突。`docs/task-designs/M01-T01-designs.md` 已冻结最小六 POM 结构、父子坐标、固定模块顺序、旧入口保留、T02/T03 排除边界、确定性 RED、Maven GREEN、范围和提交门禁；五标题、占位词、双向链接、模块契约与差异检查均退出码 0，并已回填同一设计路径。`docs/task-handoffs/M01-T01-handoff.md` 已按 `next-task` 模板创建并先链接，记录唯一直接依赖、阅读顺序、首个实施动作及环境/工作区风险，因此执行 `NOT_STARTED -> READY`。用户随后明确同意直接在当前 `main` 工作区执行 M01-T01，并要求严格只修改、暂存和提交设计指定的六个 POM；已完整复核设计、交接、任务卡、TRD 3.3、路线图规范和实施前基线，任务身份、范围、输入、首个动作与固定实现均无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。实施前确定性结构契约因缺少 packaging/modules 以预期 `AssertionError` 退出 1；提交 `09a5c65302b203c967b6eeb7540cd47cfbd1a78c`（`build: create backend Maven modules`）仅包含父 POM 与五个子 POM，形成 `com.akkc.tensor:data-plane:1.0-SNAPSHOT` 聚合工程和固定顺序的五个 `jar` 子模块，未修改旧 `Main.java` 或混入 M00 文档。2026-08-31T02:14:11+08:00 的最终新鲜验证中，结构契约、逐项等价的父子坐标/T02-T03 排除断言、Maven Help 有序五模块输出、`mvn validate` 六项目 reactor、旧入口保护、data-plane 干净状态、提交六文件范围及差异格式检查均退出码 0，reactor 为 6/6 `SUCCESS`。设计所载坐标一行存在嵌套单引号缺陷，Maven Help Plugin 将列表渲染为等价的有序 XML `<strings>`；控制器已分别以无歧义等价命令验证全部断言并裁决接受相同有序值的 XML 表示。任务级审查结论为规范符合、质量 `Approved` 且无 Critical/Important/Minor；整体审查的两项 Important 均为验收证据表示问题，XML 裁决经范围化复审确认为 addressed，报告中复制的等价命令因转义膨胀仍不可直接复现，但生产提交无缺陷且控制器同轮独立等价检查退出 0，因此将该非承载证据文档问题按审查流程裁决保留。结果级目标、范围、构建与提交验收均满足，执行 `IN_PROGRESS -> COMPLETED`。

### `M01-T02`

- **Goal:** 交付“Java 21、Boot 3.5.x 和测试依赖管理”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Java 21、Boot 3.5.x 和测试依赖管理”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M01-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 `Task M01-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 `Task M01-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：M01-T01 已在权威看板中完成，提交 `09a5c65302b203c967b6eeb7540cd47cfbd1a78c` 仅包含六个 POM，最终六项目 reactor 为 6/6 `SUCCESS`，父子坐标、固定模块顺序、旧入口、范围与格式门禁均退出码 0。M01-T02 直接消费该六 POM 基线；任务卡、TRD 第 4 节、本机 Maven 3.9.15/Java 21.0.11 与 2026-08-31 Maven Central 稳定元数据均可定位且约束无冲突。`docs/task-designs/M01-T02-designs.md` 已冻结 Java release 21、Boot 3.5.16、Compiler 3.14.1、Surefire/Failsafe 3.5.6、JUnit/AssertJ/Mockito/Testcontainers/WireMock/ArchUnit 精确版本，两项 BOM、内部/测试依赖管理、五模块依赖清单、无落盘 RED/GREEN、effective POM、六模块 test、范围与提交门禁；五个固定二级标题、占位词、任务卡双向链接、data-plane 无变更及 `git diff --check` 均退出码 0，并已回填同一设计路径。`docs/task-handoffs/M01-T02-handoff.md` 已按 `next-task` 模板创建并先链接，记录唯一直接依赖的产物/决策/理由/约束/用法/可用证据、阅读顺序、首个实施动作及环境风险，因此执行 `NOT_STARTED -> READY`。用户随后要求按照权威任务看板执行当前任务；已完整读取设计、交接、任务卡、TRD 第 4 节、路线图规范和六 POM 基线，确认任务身份、范围、依赖、精确实现及首个动作均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。实施前结构契约因父 POM缺少目标属性以预期 `AssertionError` 退出 1；提交 `6e692b9229cba8bbe5e83307402bcc5d1bfad14c`（`build: lock backend runtime and test dependencies`）仅修改六个 POM，锁定设计指定的 11 个版本属性、两项 BOM、内部/测试 dependency management、三个插件管理项及五模块精确依赖与 scope。2026-08-31T03:02:14+08:00 的最终新鲜验证中，两项 XML 契约、`help:effective-pom`、Java 21 求值、旧入口保护、data-plane 清洁、提交范围和格式检查均退出码 0；Help aggregator 输出全部 6 份 effective project 文档，`mvn test` 六项目为 6/6 `SUCCESS` 且 Surefire 为 3.5.6。Help aggregator 摘要对子模块显示 `SKIPPED` 及 Maven Resources 平台编码警告经最终审查裁定为不影响本任务结果的非阻塞文档/后续配置事项。任务级审查为规范符合、质量 `Approved` 且无 Critical/Important/Minor；最终整体审查为 `Ready to merge: Yes`，无 Critical/Important，唯一 Minor 是设计对 aggregator 摘要的表述不精确。结果级目标、范围、构建与提交验收均满足，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M01-T03`

- **Goal:** 交付“Maven Enforcer、ArchUnit 和禁止 Git 能力门禁”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Maven Enforcer、ArchUnit 和禁止 Git 能力门禁”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M01-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 `Task M01-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 `Task M01-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：M01-T02 已在权威看板中完成，提交 `6e692b9229cba8bbe5e83307402bcc5d1bfad14c` 仅包含六个 POM，两项 XML 契约、effective POM、Java 21、旧入口/范围/格式检查均退出码 0，最终 `mvn test` 六项目为 6/6 `SUCCESS`，任务级审查为 `Approved`，最终整体审查为 `Ready to merge: Yes`。M01-T03 直接消费该 Java 21 / Boot 3.5.16 / ArchUnit 1.5.0 / Surefire 3.5.6 基线；任务卡、TRD 1.4/3.3/16.2/20.1、Maven Enforcer 3.6.3 官方元数据与规则语义、本地 ArchUnit API均可定位且约束无冲突。`docs/task-designs/M01-T03-designs.md` 已冻结 Enforcer 3.6.3、七项直接/传递依赖禁令、四条模块包边、生产文本扫描范围、六类 API 标记、三类进程/脚本正则、十个反例、13 项测试、RED/GREEN、M01 verify、范围与提交门禁；五段结构、占位符、任务卡双向链接和 `git diff --check` 均退出码 0，并已回填同一设计路径。`docs/task-handoffs/M01-T03-handoff.md` 已按 `next-task` 模板创建并链接，记录唯一直接依赖的产物、决策、理由、约束、用法、可用证据、阅读顺序、首个实施动作与环境风险，因此执行 `NOT_STARTED -> READY`。用户随后明确要求按照权威任务看板执行当前任务；已完整读取设计、交接、任务卡、路线图规范、TRD 1.4/3.3/16.2/20.1 与当前 POM 基线，确认任务身份、范围、依赖、精确实现和首个动作均可定位且无冲突，基线 `mvn -f data-plane/pom.xml test` 为六项目 `SUCCESS`，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。实施按 TDD 形成 `efe755a`、`3a6d910` 与 `d56f683` 三个提交，净范围仅为父 POM与两个架构测试：Enforcer 3.6.3 在 `validate` 阶段对六项目执行七项直接/传递依赖禁令；ArchUnit 逐条执行设计冻结的四条生产包边；生产文本扫描以稳定 reactor 根仅覆盖 `src/main/java|resources`，拒绝六类 API 标记、两类 Java 子进程和脚本 Git 命令，并由十个反例、允许边界及真实扫描验证。任务级审查首轮的一项 reactor-root Important 经修复与范围化复审确认解决；最终整体审查的稳定根与源目录范围问题经唯一修复波解决，关于第五条 app→fixture 规则按精确四规则设计裁决保留 Maven test scope 屏障，范围化复审仅留下未直接模拟另类 runner 工作目录的证据请求，解析实现本身不读取 `user.dir` 或相对路径并验证聚合/模块 POM，因此记录为已裁决的非承载风险。控制器于 2026-08-31 新鲜复跑：无落盘 XML/文件结构契约、旧 Main 保护与 `git diff --check` 均退出 0；`mvn validate`、聚焦 `test`、全 reactor `test` 与 `verify` 均 `BUILD SUCCESS`，Enforcer 对 6/6 项目通过，Surefire 3.5.6 运行 13 项、0 failure、0 error、0 skipped。结果级目标、范围、测试、M01 模块门禁和提交验收均满足，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M02-T01`

- **Goal:** 交付“`PluginId`、`ApiName`、`DatasetKey`、`TableName`、`RequestId`”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`PluginId`、`ApiName`、`DatasetKey`、`TableName`、`RequestId`”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M01-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：M01-T03 已按设计、任务卡、三层门禁和新鲜验证完成，权威看板已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M02-T01。其直接依赖 M01-T02 已 `COMPLETED`，提交 `6e692b9229cba8bbe5e83307402bcc5d1bfad14c` 提供 Java 21、JUnit 5.12.2、AssertJ 3.27.7、Surefire 3.5.6 与无 Spring 编译依赖的 `tensor-plugin-api` 基线；M02 任务卡、TRD 5.1、M00-T02 schema 和当前 Enforcer 门禁均可定位，标识正则、表名派生、UUID、包根和依赖约束无冲突。`docs/task-designs/M02-T01-designs.md` 已冻结五个 record 组件与工厂、canonical constructor 不变量、正则/边界、TableName 派生、UUID 语义、IdentifierTest RED/GREEN、模块 test/verify、范围和六文件提交门禁；五段结构、占位符、任务卡双向链接、源路径和 `git diff --check` 均通过，并已回填同一设计路径。`docs/task-handoffs/M02-T01-handoff.md` 已按 `next-task` 模板创建并先链接，记录唯一直接依赖的产物、决策、理由、约束、用法、可用证据、阅读顺序、具体 RED 首个实施动作与非冲突风险，因此执行 `NOT_STARTED -> READY`。随后用户明确要求按照权威任务看板执行当前任务；已完整读取同一路径的设计与 `next-task` 交接，并核对 M02 任务卡、路线图 spec、TRD 5.1、M00-T02 schema、M01-T02 POM 基线和 Enforcer 门禁，任务身份、范围、输入和首个 RED 动作均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。实施按严格 TDD 先创建完整 `IdentifierTest.java`，聚焦命令因五个生产类型缺失在 `testCompile` 预期 RED；提交 `4078dad6f2becb2cbcd4239c5aa5bace21fed5a5`（`feat(plugin-api): add validated identifiers`）精确包含五个公开 Java records 与一个真实测试文件。五个 canonical constructors 均执行冻结不变量，两个字符串标识不 trim/不改写，`DatasetKey` 只保存非 null 组件，`TableName.from` 唯一派生双下划线表名，`RequestId.newId()` 仅生成 UUID v4/variant 2 且不接收用户输入。任务级审查唯一 Important 是缺少显式空白不规范化断言，修复轮次 1 增加两个字面量断言并以临时 trim mutation 逐项证明会失败；范围化复审确认两项发现全部解决且无新破坏，最终整体审查结论为 `Ready to merge: Yes`，无 Critical/Important/Minor。控制器于 2026-08-31 新鲜复跑聚焦、模块 `test` 与模块 `verify`，三次均执行 26 项、0 failure、0 error、0 skipped，reactor 2/2 `SUCCESS`，Enforcer 对父项目和模块通过；随后 `clean` 精确删除生成的模块 `target`。POM/app 无差异、模块范围状态为空、JDK-only 源扫描、精确六文件/固定提交消息和 `git diff --check` 均通过；输出仅保留设计已记录的 M01 平台编码配置警告。因此满足结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M02-T02`

- **Goal:** 交付“参数、API、插件描述符和 readiness”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “参数、API、插件描述符和 readiness”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：M02-T01 已按设计、严格 TDD、三项新鲜 Maven 验证、范围门禁和两级独立审查完成，权威看板与提交 `72a4208` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M02-T02；其唯一直接依赖提交 `4078dad6f2becb2cbcd4239c5aa5bace21fed5a5` 提供已校验 `PluginId`、`ApiName` 与 `DatasetKey`，与 M02 任务卡、M00-T02 参数契约、OpenAPI 数据源/API 视图及 TRD 5.2/5.3/6.1/6.2 无冲突。项目所有者批准 `PluginDescriptor.datasets` 固定为 `List<DatasetKey>`，避免提前依赖 M02-T03 的 `DatasetDefinition` 并保持本任务独立编译。`docs/task-designs/M02-T02-designs.md` 已冻结六个公开类型的精确 components、枚举闭集、canonical constructor 不变量、集合复制/重复名/引用校验、readiness 真值与敏感信息边界、完整 RED/GREEN、模块回归、Enforcer、范围和七文件提交门禁；五标题、占位符、任务卡双向链接和 `git diff --check` 均通过，任务卡与看板已回填同一设计路径。`docs/task-handoffs/M02-T02-handoff.md` 已按 `next-task` 模板创建并先链接，记录直接依赖的产物、决策、理由、约束、用法、可用证据、读取顺序、从完整设计开始的测试先行首个动作及非冲突风险，因此执行 `NOT_STARTED -> READY`。用户随后明确要求按照权威任务看板执行当前任务；已完整读取 M02-T02 设计和既有 `next-task` 交接，核对看板中的任务身份、范围、直接依赖、验收与首个动作均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。最终提交 `7984f0c` 精确创建六个公开 descriptor 类型和 `PluginDescriptorTest.java`：枚举闭集、record components、构造期 null/文本/条件字段/集合复制/重复名/引用/readiness 真值约束与 `List<DatasetKey>` 契约均符合设计，公开面不含 Token、凭证值、配置路径或认证头。完整测试先在六类型缺失时取得纯 `testCompile` RED；最终主控新鲜复跑聚焦测试 19/19、模块 `test` 和 `verify` 45/45，均为 0 failure、0 error、0 skipped，父项目与模块 Enforcer 通过，仅保留已记录的平台编码提示。任务级审查的一项 TDD 证据误读经报告补强和范围化复审确认 addressed；最终整体审查无 Critical/Important，仅一项未使用 import Minor 已在唯一修复波删除，范围化复审确认无新破坏。`clean` 后模块工作区为空，POM/app 工作区与提交范围均无差异，敏感字段扫描、固定消息、精确七文件与 `git diff --check` 均通过，因此满足结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M02-T03`

- **Goal:** 交付“数据集字段、业务键、筛选和展示定义”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据集字段、业务键、筛选和展示定义”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T01, M02-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：执行前 successor 交接核验确认，已批准设计中的 `DatasetDefinition` 直接消费 M02-T01 的 `DatasetKey`/`TableName` 与 M02-T02 的 `QueryMode`/`ParameterDescriptor`；原看板和规划索引仅列 M02-T01，与实际公开 Java 接口不一致。项目所有者批准把 M02-T03 直接依赖修订为 `M02-T01, M02-T02`，并同步权威看板与任务索引；任务保持 `NOT_STARTED`，未生成交接或记录状态转换。依赖修订提交 `4b6ed5e` 后重新完整核验设计、任务卡、实施计划和两项直接依赖；M02-T01 的 `DatasetKey`/`TableName` 与 M02-T02 的 `QueryMode`/`ParameterDescriptor` 均来自已完成提交，当前模块基线 45/45 测试通过，决策和约束无冲突。`docs/task-handoffs/M02-T03-handoff.md` 已按 `next-task` 模板完成并记录同一设计路径、直接输入、读取顺序和具体 RED 首个动作；先链接精确交接路径，再执行 `NOT_STARTED -> READY`。用户随后选择执行方式 `2`，明确要求在当前会话使用 `executing-plans` 内联执行；已重新完整读取同一路径设计与交接，确认任务、剩余工作、顺序来源和首个 RED 动作一致，作为本次 `READY -> IN_PROGRESS` 的启动证据，交接路径保留为进入上下文。实施先以完整测试取得六个生产类型缺失的 `testCompile` RED；提交 `551c18f` 精确创建任务卡指定的两个枚举、四个不可变 records 和一个真实测试，冻结 field-only `FilterDefinition`、Java-only 默认 `batchSize=500`、有序不可变集合、局部值约束及表名/业务键/筛选/固定列引用约束。首轮独立审查发现 JSON Schema `maxLength` 与 UTF-16 `String.length()` 的 Unicode 语义偏差及两个数值边界证据缺口；提交 `0a74740` 改为按 Unicode 码点计数并补充 128/64 成功、129/65 失败、`precision=0` 和 `scale=-1` 断言，提交 `bcd5a91` 同步设计和计划。范围化复审确认无 Critical、Important 或 Minor，结论为 `Ready to proceed: Yes`。主控随后新鲜复跑：聚焦测试 9/9、模块 `test` 与 `verify` 54/54 均为 0 failure、0 error、0 skipped，父项目和模块 Enforcer 均通过；`clean` 后禁用依赖/API 扫描、POM/app 无差异、三项提交消息/精确文件数、`git diff --check` 和干净工作区门禁全部退出 0。完整 `daily` 定义、公开形状、不可变性、引用和边界结果均满足设计与任务卡，且未修改 schema、POM、既有 Java 类型或其他模块，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M02-T04`

- **Goal:** 交付“`DownloadEnvelope`、`AdaptedBatch` 和执行结果”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`DownloadEnvelope`、`AdaptedBatch` 和执行结果”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T01, M02-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：M02-T03 已按设计、严格 TDD、最终 54/54 模块验证、两层 Enforcer、范围门禁及无 Critical/Important/Minor 的范围化复审完成，权威看板已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M02-T04；其直接依赖 M02-T01 提供已校验的 `PluginId`、`ApiName`、`DatasetKey`、`TableName`、`RequestId`，M02-T03 提供已校验的 `BusinessKeyDefinition` 与列名语义，两项依赖均为 `COMPLETED` 且约束无冲突。授权资料未规定失败枚举名、包络 error 类型和计数 Java 类型；项目所有者批准最小无循环契约：`DownloadStatus=SUCCESS|FAILURE`、nullable 安全 `String error`、包络 `int rowCount`、结果 `long` 计数。`docs/task-designs/M02-T04-design.md` 已据此冻结五个公开类型的精确 components、状态/计数/嵌套集合/业务 null/表名/行 key/业务键/时间不变量、完整 RED/GREEN、模块回归、Enforcer、范围和七文件提交门禁，且已从任务卡和看板链接。`docs/task-handoffs/M02-T04-handoff.md` 已按 `next-task` 模板完成并先链接，记录直接依赖的产物、决策、理由、约束、用法、可用证据、读取顺序和只创建两个完整测试后取得缺失类型 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。用户随后明确要求按照权威任务看板执行当前任务；已完整读取同一路径设计与交接，核对任务卡、跨模块稳定接口/数据形状、TRD 5.4/5.5、OpenAPI `DownloadResponse` 及 M02-T01/M02-T03 直接依赖，确认任务身份、范围、来源、剩余工作和首个 RED 动作一致且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。实施先完整创建两个测试文件，聚焦命令在 `testCompile` 仅因五个生产类型缺失退出 1，形成可归因 RED；提交 `075d1d4` 精确创建两个枚举、三个不可变 records 和两个真实测试，落实成功/空/失败状态、计数、字段/列/行、表名、业务键、批次时间、业务 null 及外层/嵌套容器复制约束。已提交内容上的最终聚焦测试 17/17、模块 `test` 与 `verify` 71/71 均为 0 failure、0 error、0 skipped，父项目和模块 Enforcer 均通过；`clean` 后 plugin-api 工作区为空，POM/app 无差异，`git diff --check` 通过，禁用依赖/API 扫描无匹配，提交消息与范围精确为设计指定七文件。独立审查确认规范符合、代码质量 `Approved`、`Ready to merge: Yes`，且无 Critical、Important 或 Minor；五个公开类型的形状和全部验收行为与设计逐项一致，未混入排除职责，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M02-T05`

- **Goal:** 交付“`DataSourcePlugin`、`DatasetAdapter` 和领域错误”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`DataSourcePlugin`、`DatasetAdapter` 和领域错误”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T03, M02-T02, M02-T03, M02-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 `Task M02-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：准备 M02-T05 设计时确认任务卡要求将 `ErrorCode` 与 `docs/contracts/error-codes.md` 对照，因而直接消费已完成 M00-T03 的 16 项错误码/retryable 契约；原看板与任务索引只列 M02-T02～T04。项目所有者批准把 M02-T05 依赖修订为 `M00-T03, M02-T02, M02-T03, M02-T04`，权威看板详情与任务索引同步；任务保持 `NOT_STARTED`，未生成交接或记录状态转换。项目所有者随后批准最小异常方案：`ErrorCode` 保存固定 retryable 真值且不携带 HTTP，抽象 `TensorException` 保存非空安全消息与错误码并派生 `retryable()`，`SourceException`/`AdapterException` 限制各自错误码类别，SPI 精确沿用任务卡签名且不暴露原始响应、Token 或额外诊断字段。`docs/task-designs/M02-T05-design.md` 已据此创建并从任务卡与看板链接；在书面设计审阅门禁完成前，M02-T05 保持 `NOT_STARTED` 且 Handoff 为 `None`。同日已完整复核该设计、任务卡、错误码目录、四项直接依赖与现有 Java 类型，确认七节设计无占位符、冲突或需实施者裁决的材料性缺口；四项依赖均为 `COMPLETED` 且约束互补。`docs/task-handoffs/M02-T05-handoff.md` 已按 `next-task` 模板写入并链接，记录同一设计路径、直接输入、读取顺序与先创建完整测试取得缺失类型 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。用户随后明确要求按照权威任务看板执行当前任务；已再次完整读取同一路径设计与交接，确认任务身份、范围、输入、验收和首个 RED 动作一致且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；交接路径保留为进入上下文。实施先完整创建 `PluginApiSurfaceTest.java`；修正反射 checked exception 后删除生产类型重跑，聚焦命令仅因六个交付类型缺失在 `testCompile` 退出 1，形成可归因 RED。提交 `445b941` 精确创建两个 SPI、16 项 `ErrorCode`、抽象 `TensorException`、两个类别受限最终异常和真实反射/行为测试；独立审查发现的公开性、泛型/声明异常安全扫描、精确 public 方法集与不 trim 门禁均经突变验证修复于 `dd495ee`，范围化复审确认原 Important/Minor 已解决且无新 Critical/Important/Minor，结论 `Ready to merge: Yes`。修复后的聚焦测试 8/8、模块 `verify` 79/79 均为 0 failure、0 error、0 skipped，父项目和模块 Enforcer 通过；`jdeps` 仅输出 `java.base`，禁用依赖扫描无输出且退出 1，`clean`、POM/app 无差异和提交范围格式检查均通过。两个 SPI、错误矩阵、异常构造/类别/消息/retryable 与安全表面逐项满足设计和任务卡，未混入排除职责，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T01`

- **Goal:** 交付“YAML 加载、schema 校验和模板对照测试框架”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “YAML 加载、schema 校验和模板对照测试框架”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T02, M02-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-08-31：M02-T05 已按设计、任务卡、79/79 模块验证、两层 Enforcer、`jdeps`、范围门禁及无 Critical/Important/Minor 的独立复审完成，权威看板已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M03-T01。其直接依赖 M00-T02 提供封闭的 JSON Schema 2020-12 与完整 `daily` 示例，M02-T03 提供不可变 `DatasetDefinition` 公共模型和构造期不变量，两项依赖均为 `COMPLETED` 且序列化/Java 决策互补无冲突。项目所有者批准把任务文件范围扩至 `data-plane/pom.xml` 与 `data-plane/tensor-plugin-tushare/pom.xml`，并随后明确同意推荐设计：networknt 固定 `1.5.9`、Jackson YAML 使用 Boot BOM 的 `2.21.4`、权威 schema 原样打包到 classpath、严格单文档 YAML/schema/M02/跨字段校验、确定性聚合 `DATASET_MISCONFIGURED` 和按 `apiName` 排序的不可变返回。`docs/task-designs/M03-T01-design.md` 已以七节完整设计冻结精确六文件范围、接口、映射、失败规则、测试命令和验收结果并从任务卡与看板链接；结构、占位符、范围、模板字段投影和 `git diff --check` 自审通过。`docs/task-handoffs/M03-T01-handoff.md` 已按 `next-task` 模板创建并先链接，记录同一设计路径、仅两项直接输入、依赖比较、阅读顺序和测试先行首个实施动作，因此执行 `NOT_STARTED -> READY`。同日用户明确要求按照权威任务看板执行当前任务；已完整读取同一路径设计与交接，确认任务身份、范围、输入、验收和首个 RED 动作一致且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。实施按严格 TDD 先取得仅因 loader 缺失的 `testCompile` RED，随后提交 `80a5a8e` 完成精确六文件最小实现；任务审查的三项 Important 由 `f5ab9d4` 修复并经范围化复审全部解决，最终整体审查的两项 Important 与两项 Minor 由唯一修复波 `7fed596` 解决，最终范围化复审确认全部 addressed 且无新 Critical/Important breakage。主控新鲜复跑聚焦测试 8/8、reactor `test` 与 `verify` 87/87，均为 0 failure、0 error、0 skipped，父项目、plugin-api 与 tushare 的 Enforcer 全部通过；依赖树精确解析 YAML `2.21.4` 与 validator `1.5.9`，JAR 中 schema 路径恰一条且与权威源字节一致，`javap` 只暴露批准的无参构造器与 `loadAll`，提交净范围精确六文件，`git diff --check` 通过且 Maven `clean` 后实现范围无工作区残留。完整 daily 映射、严格 YAML/schema/M02/M03 校验、外部 schema ref 禁止、networknt 异常安全聚合、确定性诊断、不可变排序结果与干净 SLF4J 输出均满足设计和任务卡，未混入排除职责，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T02`

- **Goal:** 交付“基础与组织 11 数据集 YAML”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “基础与组织 11 数据集 YAML”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T01 已按严格 TDD、最终 87/87 reactor 验证、三层 Enforcer、依赖/JAR/schema/API/范围门禁和无残留 Critical/Important 的最终复审完成，并在权威看板中为 `COMPLETED`。准备预定义后继 M03-T02 时，PRD 附录 A.1、TRD 9.4、manifest 与 11 个授权模板投影可定位，但未唯一规定 93 列的类型/长度/可空性和现成的 11-API 永久测试入口；项目所有者明确同意推荐方案：`basic_organization` 分类、固定参数/枚举/related parameter、批准的 DATE/MONTH/LONG/DECIMAL/TEXT/分级 STRING 类型图、COMPOSITE 键不可空、FINGERPRINT/非键可空、规定 filters/fixedColumn，并以 `/private/tmp` 公开-loader harness 执行 0→11 RED/GREEN，永久 49/49 Java 契约留给 M03-T09。`docs/task-designs/M03-T02-design.md` 已冻结精确 11 文件、93 列顺序/类型、参数、键、失败规则、命令和验收；七节顺序、占位符、93 行计数、11 份模板字段逐项 diff、链接、临时 harness classpath 实测和 `git diff --check` 均通过。`docs/task-handoffs/M03-T02-handoff.md` 已按 `next-task` 模板创建并链接，记录唯一直接依赖 M03-T01、读取顺序和先创建临时完整 harness 取得零匹配 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。同日用户明确要求按照权威任务看板执行当前任务；已完整读取 M03-T02 设计与既有 `next-task` 交接，确认任务身份、范围、直接依赖、验收和首个 RED 动作一致且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **Completion evidence:** 实施以 `/private/tmp/M03T02MetadataCheck.java` 对公开 `DatasetDefinitionLoader.loadAll` 执行严格 TDD：既有 loader 8/8 基线先通过，harness 编译成功后仅因 `<pattern>: no resources matched` 退出 1；创建 YAML 后同一 harness 只输出 `M03-T02_OK:11`，逐项验证 11 个排序 API、93 列类型/长度/可空性、参数、10 个 COMPOSITE 键、1 个 FINGERPRINT 键、filters、fixedColumn、默认 batchSize 与不可变结果。提交 `5fe20a2`（`feat(metadata): define basic and organization datasets`）精确创建设计指定的 11 个 YAML、225 行，未混入 Java、POM、schema、模板、看板或生成物。主控新鲜运行 reactor `test` 与 `verify` 均为 87/87（plugin-api 79、tushare 8），0 failure、0 error、0 skipped，父项目、plugin-api、tushare 三层 Enforcer 均通过；JAR 与源目录恰含这 11 份运行时 YAML且无额外数据集，暂存/提交范围及 `git diff --check` 通过。范围化本地审查无 Critical、Important 或 Minor；Maven `clean` 成功，临时 harness 与三个模块的 `target` 均已清除。因此满足任务设计、任务卡、范围、提交和结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T03`

- **Goal:** 交付“行情与估值 7 数据集 YAML”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “行情与估值 7 数据集 YAML”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T02 已按严格 TDD、公开 loader harness、最终 reactor 87/87、三层 Enforcer、JAR/范围/格式门禁完成，提交 `5fe20a2` 精确包含 11 个 YAML，看板提交 `fdc5810` 已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M03-T03。其唯一直接依赖 M03-T01 为 `COMPLETED`，公开 `DatasetDefinitionLoader`、严格 schema/M02/M03 校验、不可变结果和 classpath schema 打包均可定位，与任务卡、PRD A.2、TRD 9.4 和 7 个授权模板投影无冲突。授权输入未唯一规定 62 列类型/长度/可空性；项目所有者明确同意推荐契约：`ts_code=STRING(64)`、`trade_date=DATE` 且复合键列不可空，其他行情/估值数值为可空 `DECIMAL(38,18)`，`suspend_timing=STRING(255)`、`suspend_type=STRING(64)` 且可空，列 label 使用字段名，filters 固定 `[ts_code, trade_date]`，fixedColumn 固定 `ts_code`。`docs/task-designs/M03-T03-design.md` 已冻结精确 7 文件、62 列顺序/类型、参数、键、失败规则、RED/GREEN 命令和验收；七节顺序、占位符、62 行计数、7 份模板字段逐项 diff、来源与 `git diff --check` 均通过并已回填同一设计路径。`docs/task-handoffs/M03-T03-handoff.md` 已按 `next-task` 模板创建并链接，记录唯一直接依赖 M03-T01、读取顺序和先创建临时完整 harness 取得精确资源缺失 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。同日用户明确要求按照权威任务看板执行当前任务；已完整读取同一路径设计与交接，核对任务卡、M03-T01 直接输入、PRD A.2、TRD 9.4、schema、manifest 和 7 份授权模板投影，确认任务身份、范围、输入、验收和首个 RED 动作一致且无冲突，未修改代码的 loader 基线 8/8 通过，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **Completion evidence:** 实施按严格 TDD 先以 `/private/tmp/M03T03MetadataCheck.java` 对七个精确 classpath 资源取得仅因 `<pattern>: no resources matched` 的非零 RED；创建 YAML 后同一完整 harness 只输出 `M03-T03_OK:7`，逐项验证 7 个 API、62 列类型/长度/可空性、参数、业务键、filters、fixedColumn 和默认 batchSize。提交 `3c2e977`（`feat(metadata): define market datasets`）精确创建七个设计文件、146 行，未混入 Java、POM、schema、模板、既有 YAML、文档、临时 harness 或生成物。主控新鲜运行 reactor `test` 与 `verify` 均为 87/87（plugin-api 79、tushare 8），0 failure、0 error、0 skipped，父项目、plugin-api、tushare 三层 Enforcer 全部通过；公开 loader smoke 输出 `M03-T03_MAIN_OK:18/7/62`，JAR 恰含七个目标资源，运行时目录共 18 份 YAML，提交范围与 `git diff --check` 通过。任务级审查与最终整体审查均确认规范符合、质量批准、`Ready to merge: Yes`，无 Critical、Important、Minor 或设计缺陷；Maven `clean` 成功，两个 reactor `target` 和临时 harness 均已清除，工作树干净。因此满足任务设计和任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T04`

- **Goal:** 交付“交易与资金 6 数据集 YAML”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “交易与资金 6 数据集 YAML”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T03 已按严格 TDD、最终 reactor 87/87、三层 Enforcer、公开 loader smoke、JAR/范围/格式门禁和两层无发现审查完成，提交 `3c2e977` 精确包含 7 个 YAML，看板提交 `19e8fe3` 已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M03-T04。其唯一直接依赖 M03-T01 为 `COMPLETED`，公开 `DatasetDefinitionLoader`、严格 schema/M02/M03 校验、不可变结果和 classpath schema 打包均可定位，与任务卡、PRD A.3、TRD 9.4 和 6 个授权模板投影无冲突。授权输入未唯一规定 71 列类型/长度/可空性、filters 和 fixedColumn；项目所有者明确同意推荐契约：全部数值列使用 `DECIMAL(38,18)`，`ts_code/exchange_id/side=STRING(64)`、`name=STRING(128)`、`exalter/reason/buyer/seller=STRING(255)`，全部业务键列不可空、其余列可空，`margin.exchange_id` 参数为必填 ENUM `[SSE,SZSE,BSE]`，含 `ts_code` 的定义使用 filters `[ts_code, trade_date]`/fixedColumn `ts_code`，`margin` 使用 filters `[trade_date]`/fixedColumn `trade_date`。`docs/task-designs/M03-T04-design.md` 已冻结精确 6 文件、71 列顺序/类型、参数、键、失败规则、RED/GREEN 命令和验收；七节结构、占位符、71 行计数、6 份模板字段逐项 diff、链接和 `git diff --check` 均通过并已先回填同一设计路径。`docs/task-handoffs/M03-T04-handoff.md` 已按 `next-task` 模板创建并先链接，记录唯一直接依赖 M03-T01、读取顺序和先创建临时完整 harness 取得精确资源缺失 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。同日用户明确要求按照权威任务看板执行当前任务；已完整读取 M03-T04 设计与既有 `next-task` 交接，核对任务卡、M03-T01 直接输入、PRD A.3、TRD 9.4、schema、manifest 和 6 份授权模板投影，确认任务身份、范围、输入、验收和首个 RED 动作一致且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **Completion evidence:** 实施按严格 TDD 先以 `/private/tmp/M03T04MetadataCheck.java` 对六个精确 classpath 资源取得仅因 `<pattern>: no resources matched` 的非零 RED；创建 YAML 后同一完整 harness 只输出 `M03-T04_OK:6`，逐项验证 6 个 API、71 列类型/长度/可空性、参数、业务键、filters、fixedColumn 和默认 batchSize。提交 `c00ea0d`（`feat(metadata): define trading and funding datasets`）精确创建六个设计文件、144 行，未混入 Java、POM、schema、模板、既有 YAML、文档、临时 harness 或生成物。主控新鲜运行独立公开-loader 检查得到 `M03-T04_ROOT_OK:6/71`，六份授权模板字段投影与 YAML 逐一相同，manifest 六行及 query mode 相同；reactor `test` 与 `verify` 均为 87/87（plugin-api 79、tushare 8），0 failure、0 error、0 skipped，父项目、plugin-api、tushare 三层 Enforcer 全部通过，仅出现既有平台编码警告类别。JAR 恰含六个目标资源，运行时源目录共 24 份 YAML，提交范围与 `git diff --check` 通过。任务级审查与最终整体审查均确认规范符合、质量批准、`Ready to merge: Yes`，无 Critical、Important、Minor 或未解决设计缺陷；Maven `clean` 成功，两个 reactor `target` 与临时 harness 均已清除，工作树干净。因此满足任务设计和任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T05`

- **Goal:** 交付“互联互通与转融通 6 数据集 YAML”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “互联互通与转融通 6 数据集 YAML”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T04 已按严格 TDD、公开 loader、最终 reactor 87/87、三层 Enforcer、模板/manifest/JAR/范围门禁及两层无发现审查完成，提交 `c00ea0d` 精确包含 6 个 YAML，看板提交 `b6855f0` 已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M03-T05。其唯一直接依赖 M03-T01 为 `COMPLETED`，公开 `DatasetDefinitionLoader`、严格 schema/M02/M03 校验、不可变结果和 classpath schema 打包均可定位，与任务卡、PRD A.4/A.5、TRD 9.4 和 6 个授权模板投影无冲突。三个 SLB 模板没有样例行且原授权输入未唯一规定 44 列类型/长度/可空性、filters 和 fixedColumn；项目所有者明确同意推荐契约：普通数值及数量统一 `DECIMAL(38,18)`，`rank/market_type/tenor` 使用 `LONG`，代码字段使用 `STRING(64)`、名称使用 `STRING(128)`，全部业务键字段不可空、其余字段可空，filters 使用 `[ts_code, trade_date]` 或仅 `[trade_date]`，fixedColumn 优先 `ts_code`、否则使用首个业务键字段。`docs/task-designs/M03-T05-design.md` 已冻结精确 6 文件、44 列顺序/类型、参数、键、失败规则、空模板断言、RED/GREEN 命令和验收；七节顺序、占位符、44 行计数、6 份模板字段逐项 diff、三个 SLB 空数组基线、链接和 `git diff --check` 均通过，并由提交 `f598ef1` 回填同一设计路径。`docs/task-handoffs/M03-T05-handoff.md` 已按 `next-task` 模板创建并链接，记录唯一直接依赖 M03-T01、读取顺序和先创建临时完整 harness、验证 SLB 空数组后取得精确资源缺失 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。2026-09-01：用户明确要求按照权威任务看板执行当前任务；已完整读取设计文档、`next-task` 交接、任务卡、Global Constraints 和 Module Gate，确认任务身份、范围、直接依赖、首个动作及批准的 44 列契约均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。最终提交 `09967d4` 精确新增设计 Files 节的 6 个 YAML；三个 SLB 空模板断言均输出 `true`，主控重建的公开-loader 精确契约 harness 输出 `M03-T05_OK:6`，loader 8/8、reactor 87/87、`test`、`verify`、三层 Enforcer、JAR 6 资源、源目录 30 文件、clean、范围和 `git diff --check` 全部通过，临时 harness 与 `target` 均已清理且工作树干净。任务级审查为规范符合且质量 `Approved`，最终整体审查为 `Ready to merge: Yes`，两者均无 Critical、Important 或 Minor 问题；因此满足设计与任务卡验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T06`

- **Goal:** 交付“财务与披露 9 数据集 YAML”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “财务与披露 9 数据集 YAML”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T06` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T06` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T05 已按严格 TDD、公开-loader 精确 harness、reactor 87/87、`verify`、三层 Enforcer、JAR/源目录/范围门禁及两层无发现审查完成，提交 `09967d4` 精确包含 6 个 YAML，看板提交 `a92160b` 已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M03-T06。其唯一直接依赖 M03-T01 为 `COMPLETED`，公开 `DatasetDefinitionLoader`、严格 schema/M02/M03 校验、不可变结果、默认 batchSize 和 classpath schema 打包均可定位，与任务卡、PRD A.6、TRD 9.4 和 9 个授权模板投影无冲突。五个模板共 449 列没有样例行，项目所有者明确同意推荐契约：业务键列不可空、其余列可空；六个精确日期字段名使用 `DATE`，八个标识/状态字段名使用 `STRING(64)`，四个审计文本/业务项目字段名使用 `STRING(255)`，三个叙述字段名使用 `TEXT`，其余财务字段统一使用 `DECIMAL(38,18)`；filters 只使用实际存在的 `ts_code/ann_date`，fixedColumn 统一为 `ts_code`。`docs/task-designs/M03-T06-design.md` 已冻结精确 9 文件、490 列机械映射、参数、键、失败规则、RED/GREEN 命令和验收并由提交 `fa9fe15` 回填同一设计路径；七节顺序、占位符、字段计数、21 个显式类型字段集合、五个空模板、业务键引用、`fina_mainbz` 特例、链接和 `git diff --check` 均通过。`docs/task-handoffs/M03-T06-handoff.md` 已按 `next-task` 模板创建并链接，记录唯一直接依赖 M03-T01、读取顺序和先创建临时完整 harness、验证空模板后取得精确资源缺失 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。随后用户明确要求按照权威任务看板执行当前任务；已完整读取 M03-T06 设计、既有 `next-task` 交接、任务卡、Global Constraints 和 Module Gate，确认任务身份、范围、直接依赖、首个动作及批准的 490 列契约均可定位且无冲突，修改前 reactor 基线 87/87 与三层 Enforcer 通过，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。最终提交 `73f9278` 精确新增设计 Files 节的 9 份 YAML：先以五个空模板字段断言全为 `true` 和公开-loader 完整 harness 取得仅因 `<pattern>: no resources matched` 的可归因 RED，实施后同一 harness 只输出 `M03-T06_OK:9:490`；提交后新鲜 `test` 与 `verify` 均为 reactor 87/87、0 failure、0 error、0 skipped，三层 Enforcer 通过且只出现既有平台编码警告类别。JAR 恰含 9 个目标资源，运行时源目录恰有 39 份 YAML，类型分布 DATE 23、STRING 30、TEXT 3、DECIMAL 434；九个 API 的 490 列名称/顺序/类型/属性/可空性、参数、业务键、filters、fixedColumn 和默认 batchSize 均逐项匹配设计，`fina_mainbz` 保留 `ann_date` 参数而未新增同名列。提交范围、`git diff --check`、临时 harness/target 清理和干净工作树门禁均通过；提交前任务级规范/质量复核无 Critical、Important 或 Minor 发现。因此满足设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T07`

- **Goal:** 交付“公司行动 3 数据集 YAML”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “公司行动 3 数据集 YAML”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T07` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T07` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T06 已按严格 TDD、公开-loader 完整 harness、提交后 reactor 87/87、`verify`、三层 Enforcer、JAR/源目录/范围门禁及无发现任务级复核完成，提交 `73f9278` 精确包含 9 个 YAML，看板提交 `1931ed4` 已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M03-T07。其唯一直接依赖 M03-T01 为 `COMPLETED`，公开 `DatasetDefinitionLoader`、严格 schema/M02/M03 校验、不可变结果、默认 batchSize 和 classpath schema 打包均可定位，与任务卡、PRD A.7、TRD 9.4 和 3 个授权模板投影无冲突。`dividend` 模板没有样例行，且授权输入未唯一规定公司行动字段的字符串长度和可空性；项目所有者明确同意推荐契约：`end_date/ann_date/record_date/ex_date/pay_date/div_listdate/imp_ann_date/exp_date/float_date` 使用 `DATE`，`ts_code/div_proc/proc/share_type` 使用 `STRING(64)`，`holder_name` 使用 `STRING(128)`，其余 11 个数值字段使用 `DECIMAL(38,18)`，业务键列不可空、其余列可空，filters 统一为 `[ts_code, ann_date]`，fixedColumn 统一为 `ts_code`。`docs/task-designs/M03-T07-design.md` 已冻结精确 3 文件、30 列机械映射、参数、键、失败规则、RED/GREEN 命令和验收并由提交 `63bed97` 回填同一设计路径；七节顺序、占位符、14/9/7 字段计数、12 DATE/6 STRING(64)/1 STRING(128)/11 DECIMAL 完整分类、空 `dividend` 模板、链接和 `git diff --check` 均通过。`docs/task-handoffs/M03-T07-handoff.md` 已按 `next-task` 模板创建并链接，记录唯一直接依赖 M03-T01、读取顺序和先创建临时完整 harness、验证空模板后取得精确资源缺失 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。随后用户明确要求按照权威任务看板执行当前任务；已完整读取设计文档、`next-task` 交接、任务卡、Global Constraints 和 Module Gate，并核对 schema、公开 loader/test、模块 POM、PRD A.7、TRD 9.4 及 3 个授权模板投影，确认任务身份、范围、直接依赖、首个动作和批准的 30 列契约均可定位且无冲突；修改前 reactor 87/87 与三层 Enforcer 通过，作为本次 `READY -> IN_PROGRESS` 的启动证据，既有交接路径保留为进入上下文。最终提交 `7cc724e` 精确新增设计 Files 节的 3 份 YAML：空 `dividend` 断言输出 `true`，生产资源创建前公开-loader 完整 harness 仅因 `<pattern>: no resources matched` 形成可归因 RED，创建后同一 harness 只输出 `M03-T07_OK:3:30`；主控新鲜复跑 `test` 与 `verify` 均为 reactor 87/87、0 failure、0 error、0 skipped，三层 Enforcer 通过且仅有既有 platform-encoding 警告类别。JAR 恰含 3 个目标资源，运行时源目录恰有 42 份 YAML，30 列名称/顺序/类型/属性/可空性、参数、业务键、filters、fixedColumn 和默认 batchSize 均逐项匹配设计；提交范围、`git diff --check`、临时 harness/target 清理和干净工作树门禁均通过。任务级审查为规范符合且质量 `Approved`，最终整体审查为 `Ready to merge: Yes`，两者均无 Critical、Important 或 Minor 问题；因此满足设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T08`

- **Goal:** 交付“股东与治理 7 数据集 YAML”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “股东与治理 7 数据集 YAML”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T08` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T08` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T07 已按设计、严格 TDD、公开-loader 完整 harness、主控 reactor 87/87、`verify`、Enforcer、JAR/源目录/范围门禁及两层无发现审查完成；实现提交 `7cc724e` 精确包含 3 个 YAML，看板提交 `ab6c316` 已先记录 `IN_PROGRESS -> COMPLETED`，随后按预定义顺序选择后继 M03-T08。其唯一直接依赖 M03-T01 为 `COMPLETED`，严格 `DatasetDefinitionLoader`、schema/M02/M03 校验、不可变结果、默认 batchSize 和 classpath schema 打包均可定位，与任务卡、PRD A.8、TRD 9.4 和 7 个授权模板投影无冲突。两个 top-10 模板没有样例行，且授权输入未唯一规定 61 列的类型、长度和可空性；项目所有者明确同意推荐契约：`ann_date/end_date/start_date/release_date` 使用 `DATE`，`ts_code/holder_type/in_de/is_release/is_buyback` 使用 `STRING(64)`，`name/title/holder_name/pledgor` 使用 `STRING(128)`，`holder_num/pledge_count` 使用 `LONG`，其余 25 个数值字段使用 `DECIMAL(38,18)`；六个 COMPOSITE 数据集的键列不可空、其余列可空，`pledge_detail` 全部 14 列可空并按模板原序参与 FINGERPRINT。`docs/task-designs/M03-T08-design.md` 已冻结精确 7 文件、61 列机械映射、参数、键、失败规则、RED/GREEN 命令和验收并由提交 `e0ec0e3` 回填同一设计路径；七节顺序、占位符、61/14/13/7/2/25 分类、两个空模板、链接和 `git diff --check` 均通过。`docs/task-handoffs/M03-T08-handoff.md` 已按 `next-task` 模板创建并链接，记录唯一直接依赖 M03-T01、读取顺序和先创建临时完整 harness、验证两个空模板后取得精确资源缺失 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。随后用户明确要求按照权威任务看板执行当前任务；已完整读取设计文档、`next-task` 交接、任务卡、Global Constraints 和 Module Gate，并核对 schema、公开 loader/test、模块 POM、PRD A.8、TRD 9.4、manifest 与 7 个授权模板投影，确认任务身份、范围、直接依赖、首个动作和批准的 61 列契约均可定位且无冲突；修改前 reactor 87/87 与三层 Enforcer 通过，作为本次 `READY -> IN_PROGRESS` 的启动证据，既有交接路径保留为进入上下文。最终提交 `cedb21b` 精确新增设计 Files 节的 7 份 YAML：两个空 top-10 模板断言均为 `true`，生产资源创建前完整公开-loader harness 仅因 `<pattern>: no resources matched` 形成可归因 RED，创建后及提交后同一 harness 均只输出 `M03-T08_OK:7:61`；提交后新鲜 `test` 与 `verify` 均为 reactor 87/87、0 failure、0 error、0 skipped，三层 Enforcer 通过且仅有既有 platform-encoding 警告类别。JAR 恰含 7 个目标资源，运行时源目录恰有 49 份 YAML，61 列名称/顺序/类型/属性/可空性、参数、六个 COMPOSITE 键、`pledge_detail` 全字段原序 FINGERPRINT、filters、fixedColumn 和默认 batchSize 均逐项匹配设计；提交范围、`git diff --check`、临时 harness/target 清理和干净工作树门禁均通过。独立审查结论为 `Ready to merge: Yes`，无 Critical、Important 或 Minor 发现；因此满足设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M03-T09`

- **Goal:** 交付“49/49 名称、字段、参数、键和筛选总契约”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “49/49 名称、字段、参数、键和筛选总契约”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T02, M03-T03, M03-T04, M03-T05, M03-T06, M03-T07, M03-T08.
- **Sources:** `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T09` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md` 的 `Task M03-T09` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T08 已按设计、公开-loader RED/GREEN、提交后 reactor 87/87、`verify`、三层 Enforcer、JAR/源目录/范围门禁及无发现独立审查完成；实现提交 `cedb21b` 精确包含 7 个 YAML，看板提交 `7e31f51` 已先记录 `IN_PROGRESS -> COMPLETED`，随后按预定义顺序选择后继 M03-T09。其直接依赖 M03-T02～M03-T08 均为 `COMPLETED`，实现提交 `5fe20a2`、`3c2e977`、`c00ea0d`、`09967d4`、`73f9278`、`7cc724e`、`cedb21b` 分别交付互不重叠的 11+7+6+6+9+3+7 个 API 和 93+62+71+44+490+30+61 列，合计 49 API/851 列；七批均使用同一公开 loader、`tushare_pro__<api>` 表名公式和默认 batchSize 500，参数/filters 分区、模板字段顺序与 TRD 9.4 业务键来源无冲突。用户明确同意采用 bounded 设计：只新增永久 `TushareMetadataContractTest`，以 manifest/流式模板字段、显式 PRD 参数、显式 TRD 键和批准 filters 为独立基线，不修改生产实现、POM、YAML 或模板。`docs/task-designs/M03-T09-design.md` 已冻结单文件范围、49 参数分组、49 业务键转录、filters 五组、流式跳过 `data`、失败边界、可归因 RED、定向 50 项、reactor 137 项、生产 JAR 排除和验收，并由提交 `1d538c9` 回填同一设计路径；七节顺序、占位符、链接、49/851 计数和 `git diff --check` 均通过。`docs/task-handoffs/M03-T09-handoff.md` 已按 `next-task` 模板创建并链接，逐项记录七个直接依赖、决策/约束比较、读取顺序和先安装隔离依赖后取得缺测试类 RED 的首个实施动作，因此执行 `NOT_STARTED -> READY`。随后用户明确要求按照权威任务看板执行当前任务；已完整读取设计文档、`next-task` 交接、任务卡、Global Constraints 和 Module Gate，并核对公开 loader/test、模块 POM、PRD 附录 A、TRD 9.4、manifest 与七个直接依赖的批准约束，确认任务身份、范围、首个动作和 49 API/851 列独立基线均可定位且无冲突；修改前 reactor 87/87 与三层 Enforcer 通过，作为本次 `READY -> IN_PROGRESS` 的启动证据，既有交接路径保留为进入上下文。最终提交 `36230d8` 精确新增设计 Files 节的唯一永久测试文件：隔离父 POM/plugin-api 安装完成后，生产测试类不存在时模块命令只因 `No tests matching pattern` 退出非 0；创建后定向测试为 49 次参数化调用加 1 个全局覆盖测试，共 50/50。提交后新鲜 `test` 与 `verify` 均为 reactor 137/137、0 failure、0 error、0 skipped，三层 Enforcer 通过且只有既有 platform-encoding 警告类别；生产 JAR 排除检查无输出并按预期退出 1，测试类只存在于 `test-classes`，`clean`、提交范围、`git diff --check` 和干净工作树门禁通过。独立审查结论为 `Ready to merge: Yes`，Critical、Important、Minor 均为 0；因此满足设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M04-T01`

- **Goal:** 交付“V1 基础与组织表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V1 基础与组织表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M03-T09 已按设计交付永久 49/49 元数据总契约，提交 `36230d8` 精确包含唯一测试文件；reactor 137/137、`verify`、三层 Enforcer、生产 JAR 排除、范围/清理门禁及无发现独立审查均通过，看板提交 `33dbbb5` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M04-T01；其唯一直接依赖 M03-T02 为 `COMPLETED`，提交 `5fe20a2` 中 11 份 YAML 与 `docs/task-designs/M03-T02-design.md` 冻结的 93 列、十个 COMPOSITE 键、一个 FINGERPRINT 键及 filters 可定位，并已由 M03-T09 再次验证，与 M04 任务卡及 TRD 8.3/9.1～9.6 无冲突。`docs/task-designs/M04-T01-design.md` 已完成并由提交 `06663d9` 回填同一设计路径，冻结唯一 V1 SQL、机械 MySQL 类型映射、11 表/127 总列/六个二级索引、来源字段、MySQL 8.4 Flyway RED/GREEN harness、150 项 reactor 门禁、失败边界和精确验收；七节顺序、链接、占位符和 `git diff --check` 均通过。`docs/task-handoffs/M04-T01-handoff.md` 已按 `next-task` 模板创建并链接，只记录直接依赖 M03-T02、约束比较、读取顺序和先创建完整临时 harness 取得缺 V1 文件 RED 的首个动作，因此执行 `NOT_STARTED -> READY`。随后用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M04-T01-design.md` 与 `docs/task-handoffs/M04-T01-handoff.md`，确认任务身份、范围、唯一直接依赖、首个动作和验收契约均可定位，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **State evidence (blocker):** 2026-09-01：修改前 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am test` 在 plugin-api 79/79 和 tushare 58/58 通过后，因 `ModuleDependencyTest` 的唯一测试失败而退出 1；定向命令稳定复现同一结果。根因是架构规则的 `..core..` 模式把 `org.springframework.core` 与 `com.fasterxml.jackson.core` 误判为项目 `tensor-core`，修复需修改 M04-T01 设计明确排除的 Java 测试。`docs/task-handoffs/M04-T01-handoff.md` 已在状态转换前刷新为 `pause` 交接，记录根因、命令结果、未开始的 SQL 实现与解阻条件；因此执行 `IN_PROGRESS -> BLOCKED`。解阻条件是项目所有者授权并完成独立架构测试缺陷修复，且同一基线 reactor 命令以 150/150、0 failure、0 error、0 skipped 退出 0。

- **State evidence (resolution):** 2026-09-01：项目所有者回复“同意”，授权先以独立缺陷范围修复架构测试误判，并在 reactor 150/150 后恢复、继续 M04-T01。提交 `13d599f` 只修改 `ModuleDependencyTest.java`，将禁止依赖目标收窄到精确 `com.akkc.tensor` 模块包；原失败定向命令为 1/1 通过，暂停交接指定的基线 reactor 命令为 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过；独立审查确认 ArchUnit 包匹配语义、四条模块方向约束和单文件范围均正确，无 Critical、Important 或 Minor 发现。因此暂停交接的解阻条件已满足，既有 `pause` 交接路径保留为历史上下文，执行 `BLOCKED -> READY`。

- **State evidence (restart):** 2026-09-01：在 `BLOCKED -> READY` 解阻证据记录后，重新完整读取 M04-T01 设计与同任务 `pause` 交接，确认恢复任务仍为 M04-T01，剩余范围为临时 harness、可归因缺 V1 RED、唯一 SQL 实现与全部设计门禁，顺序源和首个动作与交接一致。用户的“同意”明确授权在 reactor 150/150 后恢复并继续 M04-T01，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有 `pause` 交接路径保留为历史与进入上下文。

- **State evidence (environment blocker):** 2026-09-01：恢复后已按设计产生完整临时 harness 并取得只因缺 V1 文件的可归因 RED；唯一 V1 SQL 已创建但未提交，SQL 后 reactor `test`/`verify` 均为 150/150、0 failure、0 error、0 skipped，六层 Enforcer、JAR 单资源、`git diff --check`、范围与清理门禁通过。实现代理两次执行设计指定的 `docker run ... mysql:8.4 ...` 均在 Docker Hub manifest HTTPS 请求阶段超时且未创建容器；控制器随后执行 `docker image inspect mysql:8.4 --format '{{.Id}}' || docker pull mysql:8.4` 的安全重试，确认本地镜像不存在并再次因同类 HTTPS 超时而退出 1。任务设计明确禁止替换 MySQL 8.4 或跳过实际 schema 校验，因此无法产生 Flyway migrate/validate/二次 migrate、`information_schema` 与 `M04-T01_OK:11:93:127:6` GREEN 证据。`docs/task-handoffs/M04-T01-handoff.md` 已在转换前刷新，记录未提交 SQL、已完成验证、未验证范围和可观测解阻条件；因此执行 `IN_PROGRESS -> BLOCKED`。

- **State evidence (environment resolution):** 2026-09-01：项目所有者指出其他项目已在本机 Colima 使用 MySQL 8.4。宿主环境只读复核确认默认 Colima profile 正常 `Running`，Docker daemon 为 linux/arm64 且可响应；本地已有官方 `mysql:8.4.6`，镜像声明 `MYSQL_MAJOR=8.4`、`MYSQL_VERSION=8.4.6-1.el9`，符合设计允许 MySQL 8.4 LTS 维护补丁的约束。为同一镜像补充本地 `mysql:8.4` 标签后，`docker image inspect mysql:8.4` 返回与 `8.4.6` 相同的 `sha256:869218921e61...`，`mysqld --version` 为 MySQL Community Server 8.4.6；设计指定的无挂载、随机端口任务容器随后成功启动，`mysqladmin ping` 输出 `mysqld is alive`。暂停交接的镜像可用及容器可启动条件均已满足，既有 `pause` 交接保留为历史上下文，因此执行 `BLOCKED -> READY`。

- **State evidence (environment restart):** 2026-09-01：提交 `5af2852` 已单独记录环境解阻；随后重新完整核对 M04-T01 设计与同任务 `pause` 交接，确认任务身份、唯一 V1 SQL 范围、实际 MySQL 8.4 Flyway/`information_schema` 门禁及最终提交顺序均未改变。项目所有者要求复用其他项目已使用的本机 MySQL 8.4，并已取得同一官方镜像可启动的客观证据，授权条件与剩余工作均明确，因此执行 `READY -> IN_PROGRESS`。

- **State evidence (completion):** 2026-09-01：实现提交 `09dbbfd` 按固定消息精确新增设计 Files 节的唯一 178 行 V1 SQL，未修改 POM、Java、YAML、schema、模板、其他迁移或模块。提交前与提交后均使用本机原有官方 MySQL Community Server 8.4.6 在无挂载、随机端口的全新 `tensor` schema 中运行完整临时 harness；Flyway 首次 migrate 恰执行 V1、validate 通过、二次 migrate 为零项，`information_schema` 对照确认 11 表、93 个原序业务列、127 总列、11 个主键、六个二级索引、统一来源字段、InnoDB 与 `utf8mb4_0900_as_cs`，最终只输出 `M04-T01_OK:11:93:127:6`。提交后新鲜 reactor `test` 与 `verify` 均为 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过；生产 JAR 恰含一份 V1 资源，提交范围和 `git diff --check` 通过。独立审查逐列核对设计与 11 份 YAML，并得到 `REVIEW_SCHEMA_OK:11:93:127:11PK:6`，结论 `Ready to merge: Yes`，Critical、Important、Minor 均为 0。最终 Maven `clean`、任务容器、临时 harness/classpath 与本任务添加的浮动镜像标签均已清理，原有 `mysql:8.4.6` 保留且工作树干净；因此满足任务设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`，既有 `pause` 交接路径保留为历史上下文。

### `M04-T02`

- **Goal:** 交付“V2 行情、交易与资金表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V2 行情、交易与资金表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T03, M03-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M04-T01 已在本机 Colima 的官方 MySQL 8.4.6 上通过 V1 Flyway migrate/validate/二次 migrate、11 表/127 列/键/索引/引擎/排序规则实际 schema 对照、提交后 reactor 150/150、六层 Enforcer、JAR/范围/清理和无发现独立审查；实现提交 `09dbbfd` 精确包含唯一 V1 SQL，看板提交 `88da372` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M04-T02；其直接依赖 M03-T03、M03-T04 均为 `COMPLETED`，提交 `3c2e977`、`c00ea0d` 分别交付互不重叠的 7+6 个 API 和 62+71 个业务列，合计 13 表/133 列，全部使用冻结的 COMPOSITE 键、同一公开 loader、表名公式和机械 MySQL 类型映射，主键/filters 差异已逐表比较且无冲突。项目所有者批准 M04-T02 采用临时 Java harness、固定官方 `mysql:8.4.6`、13 表/172 V2 总列/12 二级索引及 V1–V2 24 表实际验证，并进一步裁决 M04-T02～T06 均固定使用该版本；提交 `4c32e7d` 已将版本裁决写入模块计划，创建完整 `docs/task-designs/M04-T02-design.md` 并回填同一设计路径。设计七节顺序、占位符、13/133/12 矩阵、依赖路径、版本表述和 `git diff --check` 均通过，书面设计随后获项目所有者明确同意。`docs/task-handoffs/M04-T02-handoff.md` 已按 `next-task` 模板创建并链接，记录两个直接依赖、决策/约束比较、读取顺序和先取得缺 V2 文件 RED 的首个实施动作；因此执行 `NOT_STARTED -> READY`，尚未开始 SQL 实现。2026-09-01：用户明确要求按照权威任务看板执行当前任务；已完整读取 M04-T02 设计、既有 `next-task` 交接、模块任务卡及上位路线图设计，逐项核对 M03-T03/M03-T04 设计、提交与 13 份运行时 YAML，并确认 V1、POM、公开 loader、任务范围、验收和首个 RED 动作均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-01：实施按严格 TDD 先通过 150/150 reactor 基线，再以完整 `/private/tmp/M04T02SchemaCheck.java` 在数据库连接前只因精确 V2 路径缺失退出 1；提交 `0967474`（`feat(db): create market and trading tables`）精确新增唯一 237 行 V2 SQL。固定官方 `mysql:8.4.6` 镜像为 linux/arm64、`MYSQL_VERSION=8.4.6-1.el9`；在隔离全新 `tensor` schema 中，Flyway 首次恰执行 V1/V2 两项、validate 通过、二次 migrate 为零项，`information_schema` 逐表对照最终只输出 `M04-T02_OK:24:13:133:172:12`，确认全局 24 表/299 列/24 PRIMARY/18 二级索引及 V2 13 表/133 业务列/172 总列/13 PRIMARY/12 二级索引、引擎、排序规则、来源字段和 `daily` 特例。提交前新鲜 reactor `test` 与 `verify` 均为 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过，生产 JAR 恰含 V1/V2 两份迁移；独立审查另行输出 `INDEPENDENT_DDL_YAML_CHECK_OK tables=13 business=133 total=172 pks=13 secondary=12`，结论 `Ready to merge: Yes` 且 Critical、Important、Minor 均为 0。最终 Maven `clean`、任务容器、临时 harness/classpath 均已清理，提交范围、`git diff --check` 与干净工作树门禁通过。因此满足任务设计和任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`，既有 `next-task` 交接路径保留为历史进入上下文。

### `M04-T03`

- **Goal:** 交付“V3 互联互通与转融通表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V3 互联互通与转融通表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M04-T02 已按严格 TDD、官方 MySQL 8.4.6 实际 V1–V2 Flyway/schema 验收、提交前 reactor 150/150、六层 Enforcer、JAR/范围/格式/清理门禁和无发现独立审查完成；实现提交 `0967474` 精确包含唯一 V2 SQL，看板提交 `29c5bdf` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M04-T03；其唯一直接依赖 M03-T05 为 `COMPLETED`，提交 `09967d4` 交付的 6 个 API、44 个业务列、COMPOSITE 键、filters 及当前运行时 YAML 完全一致。`docs/task-designs/M04-T03-design.md` 已冻结唯一 V3 SQL、6 表/44 业务列/62 V3 总列/6 PRIMARY/4 二级索引、全局 V1–V3 30 表/361 列/30 PRIMARY/22 二级索引、`hk_hold.code/ts_code` 职责、三个 SLB 空样例边界、官方 `mysql:8.4.6` 与真实 TCP readiness，并由提交 `601399b` 回填同一设计路径；机械设计输入校验输出 `M04-T03_DESIGN_INPUT_OK:6:44:62:4:30:361:22`。`docs/task-handoffs/M04-T03-handoff.md` 已按 `next-task` 模板创建并先链接，记录唯一直接依赖、读取顺序和先取得缺 V3 文件 RED 的首个实施动作；因此执行 `NOT_STARTED -> READY`，尚未开始 SQL 实现。

- **State evidence (start):** 2026-09-01：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M04-T03-design.md` 与 `docs/task-handoffs/M04-T03-handoff.md`，并按交接顺序核对模块任务卡、Global Constraints、Module Gate、上位路线图设计、M03-T05 设计与提交 `09967d4` 的六份运行时 YAML、V1/V2、tensor-app POM 和公开 loader。六份 YAML 与冻结的 44 列、六个 COMPOSITE 键、filters 及 `hk_hold.code/ts_code` 职责一致，任务身份、唯一 SQL 范围、MySQL 8.4.6 验收和首个可归因 RED 动作均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有 `next-task` 交接路径保留为进入上下文。

- **State evidence (completion):** 2026-09-01：实现提交 `5fa8ec6` 以固定消息精确新增唯一 91 行 V3 SQL；严格 TDD 先由完整临时 harness 在数据库连接前只因精确 V3 路径缺失退出 1，再于官方 `mysql:8.4.6`（linux/arm64，`MYSQL_VERSION=8.4.6-1.el9`）全新 schema 中完成 V1/V2/V3 首次 migrate、validate 与零项二次 migrate，实际 `information_schema` 对照只输出 `M04-T03_OK:30:6:44:62:4`。结果确认 V3 恰有 6 表、44 个原序业务列、62 总列、6 PRIMARY、4 二级索引，V1–V3 合计 30 表、361 列、30 PRIMARY、22 二级索引；`hk_hold.code/ts_code` 职责、三个 SLB 的 19 列、来源字段、InnoDB、排序规则与 UTC 均符合设计。控制器在最终提交上重新运行实际 MySQL harness、reactor `test` 与 `verify`；后两者各为 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过，JAR 恰含 V1/V2/V3。Maven 产物、临时 harness/classpath 和任务容器已清理，提交范围、`git diff --check` 与干净工作树门禁通过；任务审查为规范 `✅`、质量 `Approved` 且 Critical/Important/Minor 为 0/0/0，最终整体审查为 `Ready to merge: Yes` 且三档问题均为 0。因此满足任务设计和任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`，既有 `next-task` 交接保留为历史进入上下文。

### `M04-T04`

- **Goal:** 交付“V4 财务与披露宽表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V4 财务与披露宽表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-01：M04-T03 已按严格 TDD、官方 MySQL 8.4.6 实际 V1～V3 Flyway/schema 验收、控制器新鲜 reactor 150/150、六层 Enforcer、JAR/范围/格式/清理门禁和两层无发现独立审查完成；实现提交 `5fa8ec6` 精确包含唯一 V3 SQL，看板提交 `69df6f2` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M04-T04；其唯一直接依赖 M03-T06 为 `COMPLETED`，提交 `73f9278` 交付的 9 个 API、490 个业务列、COMPOSITE 键、filters 及当前运行时 YAML 完全一致。`docs/task-designs/M04-T04-design.md` 已冻结唯一 V4 SQL、9 表/490 业务列/517 V4 总列/9 PRIMARY/8 二级索引、全局 V1～V4 39 表/878 列/39 PRIMARY/30 二级索引、`fina_mainbz` 参数/列差异、三个长文本和五个空样例边界、官方 `mysql:8.4.6` 与真实 TCP readiness，并由提交 `d9df0d5` 回填同一设计路径；机械设计输入校验输出 `M04-T04_DESIGN_INPUT_OK:9:490:517:8:39:878:30`。`docs/task-handoffs/M04-T04-handoff.md` 已按 `next-task` 模板创建并链接，只记录直接依赖 M03-T06、约束比较、读取顺序和先取得缺 V4 文件 RED 的首个实施动作；因此执行 `NOT_STARTED -> READY`，尚未开始 SQL 实现。2026-09-02：用户明确要求按照权威任务看板执行当前任务；已完整读取 M04-T04 设计、既有 `next-task` 交接及任务卡，确认任务身份、范围、输入、首个动作和验收均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。严格 TDD 首先由完整临时 harness 在数据库连接前仅以精确缺失 V4 路径退出非 0；随后唯一 `V4__create_financial_tables.sql` 建立 9 张财务与披露表。固定官方 `mysql:8.4.6` 镜像确认为 `linux/arm64`、`MYSQL_VERSION=8.4.6-1.el9`，两次独立全新 schema 验收均完成首次 V1～V4 四项 migrate、validate 和零项二次 migrate，最终只输出 `M04-T04_OK:39:9:490:517:8`；实际 `information_schema` 同时确认 V4 的 490 个业务列、517 个总列、9 个 PRIMARY、8 个二级索引及全局 39 表、878 列、39 个 PRIMARY、30 个二级索引，`balancesheet` 155 列、`fina_mainbz` 11 列且无 `ann_date`/二级索引、三个 nullable `TEXT` 与五个空样例 449 列均符合设计。格式修正后 reactor `test` 与 `verify` 分别为 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过；生产 JAR 恰含 V1～V4，机械 DDL diff、SQL 排除守卫、`git diff --check`、范围和清理门禁均退出 0，临时 harness/classpath、容器和全部 `target/` 已清理。实现提交 `9105ad5` 精确新增唯一 V4 SQL；外部同步后以 `fcb64e4` 单独记录末尾空行修正而未改写远端历史。独立最终审查对设计、任务卡、M03-T06 与 9 份 YAML 逐项复核，结论 `Ready to merge: Yes`，Critical、Important、Minor 均为 None；因此验收结果成立，执行 `IN_PROGRESS -> COMPLETED`。

### `M04-T05`

- **Goal:** 交付“V5 公司行动、股东与治理表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V5 公司行动、股东与治理表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T07, M03-T08.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：M04-T04 已按任务设计、官方 MySQL 8.4.6 实际 V1～V4 Flyway/schema 验收、reactor 150/150、JAR/范围/格式/清理门禁及无发现独立审查完成，实现提交 `9105ad5` 与格式提交 `fcb64e4` 只涉及唯一 V4 SQL，看板提交 `0a86d98` 已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M04-T05。其直接依赖 M03-T07、M03-T08 均为 `COMPLETED`，实现提交 `7cc724e`、`cedb21b` 分别交付互不重叠的 3+7 份运行时 YAML 和 30+61 个业务列，当前十份文件相对各自提交无差异；两项输入使用同一表名公式、字段顺序与机械类型/可空性规则，九个 COMPOSITE 键和 `pledge_detail` 全字段原序 FINGERPRINT 与 V5 的键和最小索引映射无冲突。`docs/task-designs/M04-T05-design.md` 已由提交 `f51042d` 创建并回填同一设计路径，完整冻结唯一 V5 SQL、10 表/91 业务列/122 总列/10 PRIMARY/10 二级索引、全局 V1～V5 49 表/1000 列/49 PRIMARY/40 二级索引、`pledge_detail` 内部键特例、官方 `mysql:8.4.6` RED/GREEN 流程和结果级验收；按 `designing-task-contracts` 复核后无待实施者裁决的内容。`docs/task-handoffs/M04-T05-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M04-T05 及直接输入 M03-T07/M03-T08，包含依赖决策/约束比较、设计优先的读取顺序和先取得精确缺 V5 路径 RED 的首个实施动作；因此执行 `NOT_STARTED -> READY`，尚未开始 V5 SQL 实现。随后用户明确要求按照权威任务看板执行当前任务；已完整读取 M04-T05 设计和既有 `next-task` 交接，确认任务身份、范围、输入、首个动作和验收均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。严格 TDD 先由完整临时 harness 在数据库连接前仅以精确缺失 V5 路径退出 1；实现提交 `2790ee5` 精确新增唯一 `V5__create_corporate_and_governance_tables.sql`，建立 10 表、91 个原序业务列、1 个内部指纹键、30 个来源列、10 个 PRIMARY 和 10 个最小二级索引。固定官方 `mysql:8.4.6` 镜像确认为 `linux/arm64`、`MYSQL_VERSION=8.4.6-1.el9`；两次独立全新 schema 均完成 V1～V5 五项 migrate、validate 与零项二次 migrate，harness 最终输出 `M04-T05_OK:49:10:91:122:10`，实际确认全局 49 表、1000 列、49 个 PRIMARY、40 个二级索引及 V5 的逐列类型/可空性、来源字段、InnoDB、排序规则和 `pledge_detail` 特例。提交后新鲜 reactor `verify` 为 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过，生产 JAR 恰含 V1～V5；范围、格式、SQL 排除和清理门禁通过，临时 harness/classpath、容器及全部 `target/` 已清理，工作树干净。独立审查确认 SQL 与 10 份 YAML 逐项一致，Critical/Important 均为 None；唯一清理 Minor 已关闭。因此满足任务设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M04-T06`

- **Goal:** 交付“V6 fixture 表与 49 表结构总校验”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V6 fixture 表与 49 表结构总校验”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M04-T01, M04-T02, M04-T03, M04-T04, M04-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T06` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T06` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：M04-T05 已按任务设计、官方 MySQL 8.4.6 实际 V1～V5 Flyway/schema 验收、reactor 150/150、JAR/范围/格式/清理门禁及无 Critical/Important 独立审查完成，实现提交 `2790ee5` 精确包含唯一 V5 SQL，看板提交 `d785545` 已先记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M04-T06。其五项直接依赖 M04-T01～T05 均为 `COMPLETED`，最终生产迁移分别为提交 `09dbbfd`、`0967474`、`5fa8ec6`、`9105ad5` 加格式提交 `fcb64e4`、`2790ee5` 的 V1～V5，当前文件相对各自最终提交无差异；五项输入按版本顺序互不重叠，合计 49 表、851 个业务列、1000 个总列、49 个 PRIMARY 和 40 个二级索引，并使用同一表名、机械类型、三个来源字段、主键/最小索引、InnoDB、`utf8mb4_0900_as_cs` 与 UTC 规则，两个 FINGERPRINT 特例无冲突。项目所有者明确批准任务卡文件范围扩展至只修改 `data-plane/tensor-app/pom.xml`，且只增加 BOM 管理的 `org.testcontainers:junit-jupiter`、`org.testcontainers:mysql` 两项 test-scope 依赖；同时批准 `fixture__fixture_daily` 的 `ts_code VARCHAR(64) NOT NULL`、`trade_date DATE NOT NULL`、`amount DECIMAL(38,18) NOT NULL`、`note VARCHAR(255) NULL`、三个来源字段、主键 `(ts_code, trade_date)`、无二级索引、InnoDB、`utf8mb4_0900_as_cs` 与 UTC 精确结构。提交 `d7d55cb` 已创建并回填 `docs/task-designs/M04-T06-design.md`，完整冻结 POM 例外、测试专用 V6、固定 `mysql:8.4.6`、公开 loader、49 个动态加 3 个固定测试、缺测试类/缺 V6 RED、六项 migrate/validate/零项二次 migrate、生产与 fixture totals、资源/JAR 隔离、失败边界和结果级验收；链接后已重新完整读取，七节顺序、无占位符、自洽性、范围和 `git diff --check` 均通过，无待实施者裁决的内容。`docs/task-handoffs/M04-T06-handoff.md` 已按 `next-task` 模板完整创建，只记录 M04-T06 及直接输入 M04-T01～T05，包含逐项 artifact/decision/rationale/constraint/usage/readiness evidence、五项约束比较、设计优先读取顺序和先运行 150/150 基线再取得缺测试类 RED 的首个实施动作；因此执行 `NOT_STARTED -> READY`，实现尚未开始。随后用户明确要求按权威任务看板执行当前任务；已完整读取 M04-T06 设计、`next-task` 交接、任务卡、Global Constraints 与 Module Gate，确认任务身份、范围、直接依赖、首个动作和批准契约均可定位且无冲突，工作树干净并获仓库指令授权直接在 `main` 实施，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。最终提交 `e78bd98` 精确包含两项 BOM 管理的 Testcontainers test-scope 依赖、测试专用 V6 与永久 `FlywaySchemaContractIT` 三个批准文件；TDD 先得到缺测试类 RED，再在显式 Colima socket 下用固定 `mysql:8.4.6` 得到首次迁移 5 对 6 的可归因 RED，添加 V6 后 49 个动态加 3 个固定调用共 52/52 通过。主控新鲜复跑确认首次 V1～V6 六项迁移、validate 成功、二次迁移零项，49 YAML/851 业务列/50 表/1007 列/50 PRIMARY/40 二级索引及资源隔离全部通过；非定向 reactor `test` 与 `verify` 均为 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过，依赖树只显示批准的两个 1.21.4 test 直接依赖，生产 JAR 恰含 V1～V5 且排除 V6/测试类，`clean` 后无残留 MySQL 8.4.6 容器，`git diff --check` 和干净工作树门禁通过。任务级审查为规范符合且质量 `Approved`，最终整体审查为 `Ready to merge: Yes`，均无 Critical/Important；唯一重复总量断言的 Minor 不会绕过逐表主键、全局 50 PRIMARY 与 fixture 主键三重门禁。因此满足结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M05-T01`

- **Goal:** 交付“`PluginRegistry` 与 `AdapterRegistry`”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`PluginRegistry` 与 `AdapterRegistry`”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：M04-T06 已按设计、固定 MySQL 8.4.6 的 52/52 实际 schema 门禁、reactor 150/150、依赖/JAR/资源隔离/范围/清理门禁及两层无 Critical/Important 独立审查完成；实现提交 `e78bd98` 精确包含三个批准文件，看板提交 `337f2a3` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M05-T01；其唯一直接依赖 M02-T05 为 `COMPLETED`，提交 `445b941` 与契约修复 `dd495ee` 提供精确 `DataSourcePlugin`/`DatasetAdapter` SPI 及其直接引用的描述符、readiness、身份、数据集和下载/适配公共类型，当前消费文件相对最终修复提交无差异。用户明确批准注册语义：只有 ID 唯一且当前可下载的插件进入 `find`；重复 PluginId 的所有实例均排除但各自以固定安全原因保留在按 pluginId/displayName 排序的描述符列表；描述符异常使用固定安全消息记录并跳过；重复 DatasetKey 的所有适配器均排除。`docs/task-designs/M05-T01-design.md` 已据此冻结两个 final 注册表的精确公共表面、readiness 构造期快照、重复/损坏/null/日志/不可变规则、10 项 RED/GREEN、89 项模块回归、三层 Enforcer、范围清理和精确三文件提交门禁，并由提交 `a0af7b4` 创建及回填同一设计路径；重新完整读取后确认七节顺序、无占位符、无依赖冲突或待实施者裁决。`docs/task-handoffs/M05-T01-handoff.md` 已按 `next-task` 模板创建并链接，只记录 M05-T01 和直接依赖 M02-T05，包含精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计优先读取顺序及先跑 79/79 基线后创建完整测试取得缺两类 RED 的首个动作，因此执行 `NOT_STARTED -> READY`；实现尚未开始。随后用户回复“同意”，明确授权按已批准设计实施 M05-T01；已完整读取设计、既有 `next-task` 交接、任务卡、Global Constraints、唯一直接依赖设计及当前 SPI/POM 基线，确认任务身份、范围、首个动作和验收均可定位且无冲突，工作树干净且仓库指令允许直接在 `main` 工作，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。严格 TDD 先以完整 `RegistryTest.java` 在两个生产类缺失时取得仅源自未解析 `PluginRegistry`/`AdapterRegistry` 的 `tensor-core:testCompile` RED；实现提交 `7ea252c` 精确创建两个 final 注册表和一份 10 项真实行为测试，修复提交 `ca39a34` 仅增强同一测试对 SPI 调用次数、`Error` 传播、重复组有效兄弟、同排序键稳定性和元数据保留的保护。最终主控在 `ca39a34` 上新鲜复跑聚焦测试 10/10、模块 `test` 与 `verify` 各 89/89，均为 0 failure、0 error、0 skipped且三层 Enforcer 通过；固定安全运行时 WARNING 符合设计，Maven/编译只保留既有平台编码警告类别。禁用依赖扫描无匹配，`clean`、POM/app/plugin-api 范围、`git diff --check` 与干净工作树门禁均通过，`3fc2b5c..ca39a34` 精确只涉及批准的三个 Java 文件。任务级审查为规范符合且质量 `Approved`，无 Critical/Important/Minor；最终审查的调用次数/错误边界与重复隔离两项 Important 已由唯一修复波解决且范围化复审确认无新破坏。关于正常 readiness 原因再清洗的剩余意见与批准合同冲突：M02 descriptor/readiness 已定义为非敏感公共契约，设计要求 M05 原样快照当前四字段且禁止修改 M02，故按合同裁决保留现状，并记录非规范未来插件可能违反 M02 安全原因约束的残余风险。结果级目标、冻结公共表面、失败隔离、不可变性、副作用禁令、严格测试与精确范围均已满足，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M05-T02`

- **Goal:** 交付“`DatasetCatalog` 和启动元数据/表结构校验”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`DatasetCatalog` 和启动元数据/表结构校验”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T09, M04-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：M05-T01 已按批准设计交付两个不可变注册表和 10 项真实行为测试，实现提交 `7ea252c` 与测试增强提交 `ca39a34` 精确限定在三个批准 Java 文件；聚焦 10/10、模块 `test`/`verify` 89/89、三层 Enforcer、静态/范围/格式/清理门禁和最终无发现审查均已通过，看板提交 `1f0fa6d` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M05-T02；其直接依赖 M03-T09、M04-T06 均为 `COMPLETED`，提交 `36230d8` 已冻结 49 API、851 个原序业务列、参数/filters、47 个 COMPOSITE 和 2 个 FINGERPRINT 键的独立元数据总契约，提交 `e78bd98` 已在固定 MySQL 8.4.6 上验证 49 张生产表、1000 列、49 PRIMARY、40 个非唯一二级索引、技术列及同一 JDBC 类型/nullability 映射。两项输入分别提供期望定义和实际物理 schema，约束互补且无冲突。项目所有者明确同意 M05-T02 采用 `DatasetStartupValidator(List<DatasetDefinition>, SchemaInspector).validate()`、`SchemaInspector(DataSource).inspect(TableName)` 和只读 `DatasetCatalog.find/list` 合同，局部 schema 失败只排除单数据集、JDBC metadata 整体失败阻止启动且不增加公开 diagnostics；提交 `bdc4eb8` 已创建并回填完整 `docs/task-designs/M05-T02-design.md`，冻结三个生产类、一个 10 项测试类、严格 RED/GREEN、99/99 模块门禁和精确四文件范围，经完整复读确认无待实施者裁决内容。`docs/task-handoffs/M05-T02-handoff.md` 已按 `next-task` 模板创建并链接，只记录 M05-T02、M03-T09 和 M04-T06，包含依赖决策/约束比较、设计优先读取顺序和先运行 89/89 基线再完整创建测试取得缺三类 RED 的首个实施动作；因此执行 `NOT_STARTED -> READY`，实现尚未开始。

- **State evidence (start):** 2026-09-02：用户明确要求按照权威任务看板执行当前任务；已完整读取 M05-T02 设计、既有 `next-task` 交接、模块任务卡、Global Constraints、Module Gate 和两个直接依赖设计，并核对 JDBC/M02 公开类型、core POM、任务身份、冻结公共表面、失败边界、TDD 顺序和精确四文件范围，确认输入可定位且无冲突。该请求作为本次 `READY -> IN_PROGRESS` 的启动证据，既有交接路径保留为进入上下文。

- **State evidence (completion):** 2026-09-02：严格 TDD 先以完整 10 项 `DatasetStartupValidatorTest` 在三个生产类缺失时取得仅源自未解析 `DatasetCatalog`、`SchemaInspector`、`DatasetStartupValidator` 及 nested snapshot 类型的 `tensor-core:testCompile` RED；最终实现提交 `57771b0` 以固定消息 `feat(core): validate dataset catalog at startup` 精确新增三个 `public final` 生产类和一个测试类，提供不可变且确定排序的已验证目录、JDBC metadata 有序快照，以及对 null/重复 key、定义关系、表/列/type/nullability、主键、唯一键和无效键引用的逐数据集隔离。审查发现的 JDBC `TABLE_NAME` pattern 碰撞、无 `COLUMN_NAME` 表达式 UNIQUE 身份和资源关闭覆盖均已在同一最终提交中修复；同一审查代理复核最终 HEAD 后确认无 Critical/Important。主控在最终提交上新鲜运行模块 `test` 与 `verify`，两者均为 plugin-api 79/79、core 20/20、总计 99/99，0 failure、0 error、0 skipped且父项目/plugin-api/core 三层 Enforcer 通过；设计允许的安全运行时 WARNING 保留，未修改 POM。禁用能力扫描无输出并按预期退出 1，非目标 POM/app/plugin-api/plugin-tushare 无差异，`git diff --check`、精确四文件提交形态和 `clean` 后干净工作树门禁通过；公开 API 与设计一致且无 M05-T03～T05 越界行为。因此满足结果级目标、冻结公共表面、失败隔离、严格测试和精确范围，执行 `IN_PROGRESS -> COMPLETED`。

### `M05-T03`

- **Goal:** 交付“元数据驱动参数校验”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “元数据驱动参数校验”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T02, M02-T05, M03-T09.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：完成 M05-T02 后按预定义顺序选择后继 M05-T03；设计检查确认任务卡要求以 `PARAM_REQUIRED|PARAM_INVALID` 和字段错误表达失败，而原看板依赖只列 M02-T02、M03-T09。项目所有者批准在 `ParameterValidator` 内嵌公开 `ParameterValidationException`/`FieldError`，异常继承 M02-T05 的 `TensorException` 并使用其 `ErrorCode`，因此同步补充已完成 M02-T05 为直接依赖。项目所有者还批准 `ValidatedParameters(Map<String,Object>)` 保存有序不可变规范化字符串、默认值规则、两阶段同类错误聚合，以及互相关联 `DATE_RANGE_MEMBER` 按声明顺序执行前者不晚于后者；提交 `6314188` 的 `docs/task-designs/M05-T03-design.md` 已据此冻结精确公开表面、六类参数规则、安全边界、10 项 TDD、109/109 回归和精确三文件范围并回填，书面设计经项目所有者确认无需修改。三项直接依赖均为 `COMPLETED`，参数规则形状、错误/SPI 边界和 49 API 参数实例职责互补且无冲突；`docs/task-handoffs/M05-T03-handoff.md` 已按 `next-task` 模板创建并链接，记录相同设计、直接输入、读取顺序、风险和先运行 99/99 基线后取得缺两个生产类型 RED 的首个实施动作。因此执行 `NOT_STARTED -> READY`，Java 实现尚未开始。

- **State evidence (start):** 2026-09-02：用户明确要求按照权威任务看板执行当前任务；已完整读取 M05-T03 设计、既有 `next-task` 交接、模块任务卡、Global Constraints、Module Gate 和三项直接依赖设计，并核对参数描述符、领域错误、下载 SPI 与 49/49 参数契约测试，确认任务身份、冻结公共表面、失败边界、严格 TDD 顺序和精确三文件范围均可定位且无冲突。该请求作为本次 `READY -> IN_PROGRESS` 的启动证据，既有交接路径保留为进入上下文。

- **State evidence (completion):** 2026-09-02：提交 `6e86d46` 精确创建设计批准的 `ParameterValidator.java`、`ValidatedParameters.java` 和 10 项真实行为 `ParameterValidatorTest.java`，实现只依据 `ApiDescriptor.parameters` 的 required/default/optional、六类 type、allowedValues、整串 pattern、互反范围和声明顺序返回有序不可变字符串 map，并以安全、确定排序的 `PARAM_REQUIRED|PARAM_INVALID` 字段错误拒绝未知、缺失和无效输入；提交 `be26e31` 在独立审查发现 `uuuu` 可接受扩展/负年份后，以可归因 4 项 RED 和最小 ASCII 固定宽度门禁补齐 DATE、DATE_RANGE_MEMBER、MONTH 与默认值元数据回归。最终提交后聚焦 10/10，完整 `test` 与 `verify` 均为 plugin-api 79 项、core 30 项、总计 109/109，0 failure、0 error、0 skipped，父项目、plugin-api、core 三层 Enforcer 通过；禁用 API/插件/数据集/Token/框架/SQL 依赖扫描无命中，非目标 POM/app/plugin-api/plugin-tushare 无差异，格式、精确 10 个 `@Test`、`clean` 和干净工作树门禁通过。最终只读复审对 `37a3fbd..be26e31` 无 Critical、Important 或 Minor 发现并结论 `Ready to merge: Yes`；公开表面、安全消息、不可变快照、元数据错误阻止、严格日期/月和范围行为均满足设计与任务卡验收，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M05-T04`

- **Goal:** 交付“严格日期、文本、整数和精确数值转换”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “严格日期、文本、整数和精确数值转换”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T03, M02-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：准备 M05-T04 设计时确认任务卡要求所有转换失败抛出 M02-T05 定义的 `AdapterException(ErrorCode.ADAPTER_TYPE_INVALID, ...)`，而原看板仅列出提供 `LogicalType` 与 `ColumnDefinition` 的 M02-T03。项目所有者明确批准将已完成的 M02-T05 补充为 M05-T04 的直接依赖。项目所有者随后逐项批准 `ConversionContext(ApiName,int)`、TEXT/空值、来源运行时类型、nullable 职责、精确安全消息、Unicode 长度/ENUM 以及单类 switch 实现方案，并确认接口行为与测试设计准确；`docs/task-designs/M05-T04-design.md` 已据此冻结精确公开表面、七类转换、安全边界、12 项 TDD、121/121 回归和精确三文件范围并回填。新鲜基线在 attach 受限沙箱中只因 Mockito/Byte Buddy `MockMaker` 无法自附加而产生 core 10 errors；同一命令在允许 JVM attach 的环境立即恢复 plugin-api 79/79、core 30/30、三层 Enforcer 全绿，确认是执行环境差异并已写入设计风险。设计完整复读确认七节顺序、无占位符、无冲突或待实施者裁决；M02-T03 与 M02-T05 均为 `COMPLETED`，其元数据类型/列参数与领域错误职责互补。`docs/task-handoffs/M05-T04-handoff.md` 已按 `next-task` 模板创建并链接，只记录这两项直接输入、约束比较、设计优先读取顺序和取得缺两个生产类型 RED 的首个动作，因此执行 `NOT_STARTED -> READY`，Java 实现尚未开始。同日用户明确要求按照权威任务看板执行当前任务；已完整读取并核对同一路径设计、交接、任务卡、两项直接依赖设计及当前公开类型，确认任务身份、范围、输入、验收和首个 RED 动作一致且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **State evidence (completion):** 2026-09-02：严格 TDD 先完整创建 12 项真实行为 `ValueConverterTest.java`，聚焦命令仅因 `ValueConverter` 与 `ConversionContext` 缺失在 `tensor-core:testCompile` 非零，形成可归因 RED；提交 `e609f50`（`feat(core): add strict dataset value conversion`）随后精确创建两个生产类型和同一测试文件，实现 null/TEXT、短字符串 Unicode 码点门禁、开放/闭合 ENUM、ASCII 固定宽度严格 DATE/MONTH、精确 LONG、无 double 且按 `RoundingMode.UNNECESSARY` 固化 scale/precision 的 DECIMAL，以及只含 API、从 0 开始行号和字段名的统一 `ADAPTER_TYPE_INVALID` 安全摘要。主控在允许 JVM attach 的环境新鲜复跑聚焦 12/12、reactor `test` 与 `verify` 各 121/121，均为 0 failure、0 error、0 skipped，父项目、plugin-api、core 三层 Enforcer 全部通过；两项禁用表面/浮点扫描均无匹配，非目标 POM/app/plugin-api/plugin-tushare 无差异，格式、`clean`、无 `target`、精确三文件提交和干净工作树门禁通过。任务级规格/质量审查与最终整体审查均无 Critical、Important 或 Minor，最终结论 `Ready to merge: Yes`；公开表面、七类转换、精度与安全失败边界逐项满足设计和任务卡，且未混入 M05-T05、持久化、下载、REST 或其他排除职责，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M05-T05`

- **Goal:** 交付“`GenericDatasetAdapter`、重复键和指纹键”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`GenericDatasetAdapter`、重复键和指纹键”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T04, M02-T05, M05-T02, M05-T03, M05-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：准备 M05-T05 设计时确认任务卡要求实现 M02-T05 发布的 `DatasetAdapter`，直接消费 M02-T04 的 `DownloadEnvelope`/`AdaptedBatch`，并以 M02-T05 的 `AdapterException` 表达必填字段和重复键失败，而原看板只列 M05-T02～T04。项目所有者明确批准保留既有三项依赖并补充已完成的 M02-T04、M02-T05；同时批准指纹编码采用字段原序、1 字节 `0=null|1=value` 标记、非空规范文本 UTF-8 前置 4 字节大端长度，拼接后 SHA-256 输出 64 位小写十六进制。M05-T05 保持 `NOT_STARTED`，设计与交接尚未创建。

- **State evidence (readiness):** 2026-09-02：`docs/task-designs/M05-T05-design.md` 已完整冻结 `GenericDatasetAdapter`/`FingerprintKeyCodec` 公开表面、成功包络准入、逐值转换与缺失校验、COMPOSITE/FINGERPRINT 去重、空批次、11 项严格 TDD、132/132 reactor 门禁和精确三文件范围并链接；复读确认七节顺序完整、无占位符、无冲突或留给实施者的材料选择。指纹合同保持项目所有者批准的字段原序、`0x00|0x01` tag、4 字节大端 UTF-8 长度、规范文本及 64 位小写 SHA-256，固定向量字节 `0100000003e4b8ad00010000000234320100000004312e3230` 对应摘要 `c593b786a7708a9b7a106e244094f1cabd200caa3e95fad3b041225c17ac19ad`。五项直接依赖 M02-T04、M02-T05、M05-T02、M05-T03、M05-T04 均为 `COMPLETED`，其数据形状、SPI/错误、已验证定义、参数准入和单值转换职责互补且无冲突。`docs/task-handoffs/M05-T05-handoff.md` 已按 `next-task` 模板完整创建并链接，记录同一设计路径、五项直接输入及约束比较、设计优先读取顺序、风险和先确认 121/121 基线再取得缺两个生产类型 RED 的首个实施动作；因此执行真实的 `NOT_STARTED -> READY`，Java 实现尚未开始。

- **State evidence (start):** 2026-09-02：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M05-T05-design.md` 与 `docs/task-handoffs/M05-T05-handoff.md`，确认任务身份、精确三文件范围、验收、读取顺序和首个 TDD 动作均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有 `next-task` 交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-02：严格 TDD 先在只存在完整 `GenericDatasetAdapterTest` 时取得仅因 `GenericDatasetAdapter`/`FingerprintKeyCodec` 缺失的 `testCompile` RED，再由实现提交 `d7ec551` 精确新增设计规定的三个 Java 文件并取得聚焦 11/11 GREEN；最终整体审查发现的 null 指纹字段元素合同偏差又以回归 RED（原 NPE）→GREEN 和提交 `8ca49d0` 修正，范围化复审判定 ADDRESSED、无新问题。最终实现按冻结的成功包络、单批字段索引、逐值转换、必填/业务键校验、COMPOSITE/FINGERPRINT 稳定去重、空批次、固定安全错误与警告以及字段原序、显式 null tag、4 字节大端 UTF-8 长度、五类规范文本和 64 位小写 SHA-256 合同工作，固定向量通过。主控在最终提交上新鲜运行 reactor `test` 与 `verify`，两者均为 plugin-api 79/79 加 core 53/53、合计 132/132，0 failure、0 error、0 skipped，三层 Enforcer 通过；两项禁用模式扫描均无输出，非目标模块无差异，`git diff --check`、精确提交范围与 Maven `clean` 门禁通过，工作树干净。任务级审查为规范 `✅`、质量 `Approved` 且无问题；最终整体审查经唯一修复波后无剩余发现。因此满足任务设计和任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`，既有 `next-task` 交接路径保留为历史进入上下文。

### `M06-T01`

- **Goal:** 交付“白名单 SQL 标识符和 Upsert 模板”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “白名单 SQL 标识符和 Upsert 模板”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T03, M04-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

- **State evidence (readiness):** 2026-09-02：M05-T05 已按严格 TDD、最终 reactor `test`/`verify` 132/132、三层 Enforcer、静态/范围/格式/清理门禁及两层审查完成，实现提交 `d7ec551` 与合同修复 `8ca49d0` 已由看板提交 `387ceb8` 记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择后继 M06-T01。其直接依赖 M02-T03、M04-T06 均为 `COMPLETED`：前者以提交 `551c18f` 和修复 `0a74740` 冻结保序不可变 `DatasetDefinition`/业务键合同，后者以提交 `e78bd98` 在官方 MySQL 8.4.6 验证业务列原序、两项 FINGERPRINT `business_key`、三个来源字段和 49 张生产表物理主键。两项输入分别提供逻辑元数据与物理映射，结合 TRD 10.3 的参数化 Upsert 结构无冲突。提交 `21eabe6` 已创建并回填 `docs/task-designs/M06-T01-design.md`，完整冻结两个无状态公开类型、白名单正则、COMPOSITE/FINGERPRINT insert/update 差异、`daily` 精确 SQL、6 项严格 TDD、138/138 reactor 和精确三文件范围；完整复读确认七节齐全、无占位符或留给实施者的材料选择。`docs/task-handoffs/M06-T01-handoff.md` 已按 `next-task` 模板创建并先链接，只记录 M06-T01 及直接输入 M02-T03/M04-T06，包含决策/约束比较、设计优先读取顺序和先确认 132/132 基线再取得缺两个生产类型 RED 的首个动作；因此执行 `NOT_STARTED -> READY`，实现尚未开始。

- **State evidence (start):** 2026-09-02：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M06-T01-design.md` 与 `docs/task-handoffs/M06-T01-handoff.md`，并核对模块计划的 Global Constraints、Task M06-T01、Module Gate、两项直接依赖设计、当前公开元数据类型、TRD 9.1/9.2/10.2/10.3 以及 `daily`、`stk_managers`、`pledge_detail` 物理映射，确认任务身份、精确三文件范围、冻结公开表面、失败边界、验收和首个 TDD 动作均可定位且无冲突。该请求作为本次 `READY -> IN_PROGRESS` 的启动证据，既有 `next-task` 交接路径保留为进入上下文。

- **State evidence (blocker):** 2026-09-02：精确三个 Java 文件已按严格 TDD 创建并暂存；缺两个生产类型的聚焦 RED 可归因，聚焦 GREEN 为 6/6，授权 JVM 环境 reactor `test`/`verify` 均为 plugin-api 79/79 加 core 59/59、合计 138/138，三层 Enforcer、第一项静态扫描、范围、格式和清理检查通过。独立任务审查确认实现与测试满足功能合同，但发现设计第二项静态门禁以包含裸 `;` 的正则扫描 `UpsertSqlFactory.java` 并要求无输出；合法 Java 必须包含分号，故原命令不可满足，审查把它列为唯一 Important 计划/门禁问题并判定修正前不能满足全部验收。`docs/task-handoffs/M06-T01-handoff.md` 已改写为 `pause` 交接，记录当前暂存产物、验证证据、剩余工作与解阻条件；因此执行 `IN_PROGRESS -> BLOCKED`。解阻条件是项目所有者批准一个精确、可执行且能验证生成 SQL 无末尾分号/危险关键字或注释的替代扫描规则，并把批准裁决记录进任务设计。

- **State evidence (resolution):** 2026-09-02：项目所有者回复“同意”，批准将第二项门禁修正为源码 `rg` 只扫描危险 SQL 关键字与注释，生成 SQL 无末尾分号由现有第 4 项 `doesNotEndWith(";")` 和第 6 项禁止字符行为断言验证，并授权随后继续完成任务。提交 `c0de827` 已把精确替代命令、预期结果和验收表述写入既有 `docs/task-designs/M06-T01-design.md`；修订未改变公开合同、生产实现、六项测试数量或三文件范围。暂停交接所列解阻条件因此满足，保留同一交接路径作为历史恢复上下文，执行 `BLOCKED -> READY`。

- **State evidence (restart):** 2026-09-02：项目所有者对“按修正门禁继续完成任务”的提问回复“同意”，构成本次 `READY -> IN_PROGRESS` 的明确启动证据。已重新完整读取修订后的任务设计和同一 `pause` 交接，确认恢复任务仍为 M06-T01，剩余工作仅为执行批准后的门禁、精确三文件提交、最终审查与结果级验收；暂存区仍精确包含三个目标 Java 文件且无冲突改动。既有交接路径保留为历史恢复上下文。

- **Completion evidence:** 2026-09-02：严格 TDD 先以完整六项 `UpsertSqlFactoryTest` 在两个生产类型缺失时取得仅源自 `SqlIdentifierPolicy`/`UpsertSqlFactory` 未解析的 `testCompile` RED；实现提交 `029b344` 以固定消息 `feat(core): generate validated upsert SQL` 精确新增两个无状态 final 生产类和同一测试类。实现只从 `DatasetDefinition` 派生并重新白名单校验全部标识符，按业务列原序、可选 `business_key` 和三个来源字段生成全占位符 SQL；COMPOSITE 排除定义主键更新，FINGERPRINT 排除内部主键并更新全部定义业务列。修订门禁后聚焦测试新鲜复跑 6/6；授权 JVM 环境模块 `test`/`verify` 均为 plugin-api 79/79 加 core 59/59、合计 138/138，最终提交后的全仓 reactor `verify` 为 plugin-api 79、core 59、Tushare 58、app 13、合计 209 项，0 failure、0 error、0 skipped，六模块 Enforcer 通过。两项源码扫描均无输出并退出 1，无末尾分号由第 4、6 项行为断言覆盖；非目标模块差异、精确提交范围、`git diff --check` 与全仓 `clean` 后干净工作树门禁通过。任务级复审为规格符合且质量 `Approved`，无问题；最终整体审查为 `Ready to merge: Yes`，无 Critical/Important，唯一临时报告状态 Minor 已刷新。因此结果级目标、公开表面、两种键模式、安全边界、严格测试与精确范围均满足，执行 `IN_PROGRESS -> COMPLETED`；同一 `pause` 交接路径保留为历史上下文。

### `M06-T02`

- **Goal:** 交付“复合键与指纹键编码/绑定”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “复合键与指纹键编码/绑定”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：M06-T01 已按结果级验收完成并由提交 `b15bdc1` 记录 `IN_PROGRESS -> COMPLETED`，按预定义顺序选择最小后继 M06-T02。项目所有者批准 `JdbcValueBinder.bind(PreparedStatement,int,Object,int)` 的 typed-null 合同，并在比较最小有序值键、携带模式/字段的键及模式专用类型层次后批准最小方案：COMPOSITE 按定义原序形成结构键，FINGERPRINT 直接消费 M05 已生成的 `business_key`，生产代码不重复哈希；随后批准完整书面设计。提交 `440b9b5` 已创建并回填 `docs/task-designs/M06-T02-design.md`，冻结三个生产类型的公开表面、固定安全错误、五类 setter、UTC Instant、8 项严格 TDD、146/146 reactor 门禁和精确四文件范围；完整复读和自审确认七节齐全、无占位符、矛盾、范围漂移或留给实施者的材料选择。直接依赖 M05-T05 已为 `COMPLETED`，其 `FingerprintKeyCodec`、`GenericDatasetAdapter`、允许值类型与适配行合同通过 132/132 reactor 及最终审查，与本设计职责互补且无冲突。`docs/superpowers/plans/2026-09-02-m06-t02-business-key-binding.md` 已把批准设计转换为严格 RED→GREEN 的逐步实施计划；`docs/task-handoffs/M06-T02-handoff.md` 已按 `next-task` 模板先行完整创建，只记录 M06-T02 及直接输入 M05-T05，并以设计、实施计划和依赖产物为读取顺序，首个实施动作是确认 138/138 基线后只创建测试取得缺三个生产类型的 RED。因此链接交接并执行真实的 `NOT_STARTED -> READY`；Java 实现尚未开始。2026-09-02：用户明确要求按照权威任务看板执行当前任务；已完整读取 M06-T02 设计、`next-task` 交接、逐步实施计划、M06 任务卡、M05-T05 直接依赖设计与产物，并核对现有指纹 codec、适配器和 M06-T01 测试风格；确认任务身份、范围、四文件产物、首个动作、直接依赖和固定契约均可定位且无冲突，工作树干净，作为本次 `READY -> IN_PROGRESS` 的明确启动证据；既有交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-02：实现提交 `2bd8996` 以固定消息精确新增设计 Files 节的三个生产类和一个测试类。修改前在允许 JVM attach 的环境确认 reactor 基线 plugin-api 79/79、core 59/59，共 138/138；只创建完整测试后，聚焦命令仅因 `BusinessKey`、`BusinessKeyExtractor`、`JdbcValueBinder` 不存在而在 `tensor-core:testCompile` 非零，形成可归因 RED；最小实现后同一聚焦命令 8/8 GREEN。提交后新鲜 `test` 与 `verify` 均为 plugin-api 79/79、core 67/67，合计 146/146、0 failure、0 error、0 skipped，三层 Enforcer 全部通过，仅保留设计允许的 platform-encoding、Mockito/JDK agent 与既有安全 WARNING 类别。两项生产源码扫描均无输出并退出 1，受保护路径无差异，测试恰有 8 个 `@Test`，`clean`、`git diff --check`、精确四文件提交和干净工作树门禁全部通过。独立审查结论为 `Ready to merge: Yes`，Critical、Important、Minor 均为 0；因此满足冻结设计和任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M06-T03`

- **Goal:** 交付“已有键预查、数据集锁和插入/更新计数”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “已有键预查、数据集锁和插入/更新计数”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M06-T01, M06-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-02：M06-T02 已按严格 TDD、reactor `test`/`verify` 146/146、三层 Enforcer、静态/范围/格式/清理门禁及无发现独立审查完成；实现提交 `2bd8996` 精确交付三个生产类和一个测试类，看板提交 `e86e1af` 已先记录 `IN_PROGRESS -> COMPLETED`，按预定义顺序选择最小后继 M06-T03。其直接依赖 M06-T01、M06-T02 均为 `COMPLETED`：前者由提交 `029b344` 提供白名单标识符、参数化 Upsert 模板和 COMPOSITE/FINGERPRINT 物理键规则，后者由提交 `2bd8996` 提供不可变有序 `BusinessKey`、两种键模式提取和无 `setObject` 的明确 JDBC binder；两项输入分别冻结安全物理键列和有序键值/绑定，约束互补且无冲突。项目所有者明确批准已有键 SQL 策略：单物理键列使用参数化 scalar `IN`，多物理键列使用 MySQL row-constructor `IN`，每查询最多 1000 个绑定参数且分块键数为 `floor(1000 / physicalKeyWidth)`；并批准任务卡文件范围只扩展 `data-plane/tensor-core/pom.xml`，只增加由 Spring Boot BOM 管理的 `com.mysql:mysql-connector-j` test-scope 依赖，因为依赖树已证明现有 Testcontainers MySQL 不提供 JDBC driver。提交 `82c7c99` 已创建并回填 `docs/task-designs/M06-T03-design.md`，完整冻结三个生产类型的唯一公开表面、锁 acquisition/释放/引用清理、精确 SQL 与结果映射、JDBC 类型、空/重复/宽度/SQL 失败边界、`WriteCounts.from` 不变量、固定 8 项 MySQL/并发测试、显式 `*IT` 生命周期、146/146 标准回归和精确五文件实现范围；链接后重新完整读取并确认七节齐全、无占位符、矛盾、范围漂移或留给实施者的材料选择。`docs/task-handoffs/M06-T03-handoff.md` 已按 `next-task` 模板先行完整创建，只记录 M06-T03 及直接输入 M06-T01/M06-T02，包含逐项 artifact/decision/rationale/constraint/usage/readiness evidence、约束比较、设计优先读取顺序，以及先确认 146/146 基线和 driver 缺失、再只添加批准 POM 依赖与完整 IT 取得缺三个生产类型 RED 的首个实施动作；因此链接交接并执行真实的 `NOT_STARTED -> READY`，Java 实现尚未开始。2026-09-02：用户明确要求按照权威任务看板执行当前任务；已完整读取 M06-T03 设计、`next-task` 交接、M06 任务卡与 Global Constraints/Module Gate、TRD 10.2～10.4、M06-T01/M06-T02 设计及直接消费产物，确认任务身份、五文件范围、公开合同、首个动作和依赖约束均可定位且无冲突；当前为已获授权的 `main` 普通检出且工作树干净，作为本次 `READY -> IN_PROGRESS` 的明确启动证据，既有交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-03：实现提交 `65ad1d7` 精确交付批准的 POM 测试依赖、三个生产类型和一个集成测试类，审查修复提交 `d49d6c2` 加固真实分块与跨线程数据集隔离合同。修改前 reactor 基线为 plugin-api 79/79、core 67/67，共 146/146；只加入批准依赖和完整测试时，仅因 `DatasetLockManager`、`ExistingKeyRepository`、`WriteCounts` 尚不存在而在 `testCompile` 非零，形成可归因 RED。固定 MySQL 8.4.6 集成/并发测试最终 8/8，reactor `test` 与 `verify` 最终均为 146/146、0 failure、0 error、0 skipped，三层 Enforcer 全部通过；完成迁移前又串行复跑定向测试 8/8 和 `verify` 146/146。依赖树只显示直接 BOM 管理的 `com.mysql:mysql-connector-j:jar:9.7.0:test`；危险源码扫描无输出并退出 1，必需实现扫描确认公平锁、1000 参数上限、`SqlIdentifierPolicy` 和 `JdbcValueBinder`，测试类恰有 8 个 `@Test`。受保护路径无差异，累计提交范围精确为设计批准的五个文件，`clean`、`git diff --check` 和干净工作树门禁通过。两项受控 mutation 分别证明：取消分块会观测到单次 1001/1002 bind 并使边界测试失败，折叠为全局锁会使跨线程隔离测试超时失败。独立复审确认首轮三个发现均已解决，无新增 Critical、Important 或 Minor，结论 `Ready to merge: Yes`；因此满足任务目标与全部结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M06-T04`

- **Goal:** 交付“单事务批量 Upsert 与回滚”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “单事务批量 Upsert 与回滚”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M06-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：M06-T03 已按严格 TDD、固定 MySQL 8.4.6 定向 8/8、reactor `test`/`verify` 146/146、三层 Enforcer、两项受控 mutation、范围/格式/清理门禁及无发现独立复审完成，权威看板已记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择后继 M06-T04；其唯一直接依赖 M06-T03 为 `COMPLETED`，提交 `65ad1d7` 与审查修复 `d49d6c2` 提供公平可重入数据集锁、1000 bind 分块已有键预查和集合语义 `WriteCounts`，与已冻结的 M06-T01/T02 SQL、业务键和 JDBC binder 合同相容。项目所有者已明确批准事务同步持锁到最外层 `afterCompletion` 的方案；`docs/task-designs/M06-T04-design.md` 完整冻结两个生产类、一个固定 MySQL 8.4.6 的 8 项集成测试、严格 RED/GREEN、两项 mutation、146/146 回归和精确三文件范围，设计七节、公开表面、错误边界、命令与验收无占位符、矛盾或待实施者裁决。`docs/superpowers/plans/2026-09-03-m06-t04-atomic-persistence.md` 已把批准设计转换为任务级实施计划；`docs/task-handoffs/M06-T04-handoff.md` 已按 `next-task` 模板创建并先链接，只记录 M06-T04 与直接输入 M06-T03、设计优先读取顺序和先确认 146/146 基线再取得缺两个生产类型 RED 的首个动作。因此执行真实的 `NOT_STARTED -> READY`，Java 实现尚未开始。

- **State evidence (start):** 2026-09-03：用户明确要求“按照权威任务看板执行当前任务”，已重新完整读取 M06-T04 设计、既有 `next-task` 交接、任务级实施计划、M06 任务卡与唯一直接依赖设计/产物；确认任务身份、精确三文件范围、公开合同、事务/锁失败边界、严格 TDD 顺序、首个动作和结果级验收均可定位且无冲突。该请求作为本次 `READY -> IN_PROGRESS` 的明确启动证据；既有交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-03：实现提交 `da56663` 精确新增 `GenericUpsertRepository`、`PersistenceService` 和 `PersistenceServiceIT` 三个设计文件，以 `TransactionTemplate` 的 `REQUIRED`/60 秒合同组合目录校验、事务外键提取、公平数据集锁、事务内已有键预查/集合计数、元数据批大小 JDBC Upsert 和 `afterCompletion` 解锁。严格 TDD 先在仅有完整测试时因两个生产类型缺失形成可归因 `testCompile` RED；固定官方 MySQL 8.4.6 定向套件最终 8/8，真实覆盖 2+1 batch、全插入/更新/混合计数、第二 batch 失败整批回滚、加入外层事务时提交前阻塞、FINGERPRINT 幂等、统一 UTC `ingested_at` 及空/错误边界。两项受控 mutation 分别证明提前解锁会产生错误 `(1,0)` 并发计数、忽略 `DatasetDefinition.batchSize()` 会把 `[2,1]` 错成 `[3]`，恢复后对应测试与完整定向套件均通过。reactor `test` 与 `verify` 均为 plugin-api 79/core 67，共 146/146，三层 Enforcer 通过；提交态完成前又新鲜复跑 MySQL 8/8 与 `verify` 146/146。禁止模式零命中，所需事务同步/批大小/SQL factory/binder 机制全部命中，受保护路径无差异，提交级 `diff --check`、精确三文件范围和 Maven `clean` 通过。独立只读复审确认无 Critical、Important 或 Minor，结论 `Ready to merge: Yes`。因此目标级事务原子性、准确计数、锁生命周期、参数化绑定、失败回滚和全部范围约束均满足，执行 `IN_PROGRESS -> COMPLETED`。

### `M06-T05`

- **Goal:** 交付“查询条件白名单和 COUNT/分页 SQL”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “查询条件白名单和 COUNT/分页 SQL”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T03, M04-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：M06-T04 已按严格 TDD、固定 MySQL 8.4.6 定向 8/8、提交态 reactor `verify` 146/146、三层 Enforcer、两项受控 mutation、静态/范围/格式/清理门禁和无 Critical/Important/Minor 的独立复审完成，权威看板已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择最小后继 M06-T05；其直接依赖 M02-T03、M04-T06 均为 `COMPLETED`：前者以提交 `551c18f` 与修复 `0a74740` 提供已校验、保序不可变的 DatasetDefinition/列/filter/业务键公共合同，后者以提交 `e78bd98` 在固定 MySQL 8.4.6 上证明 49 张生产表、COMPOSITE/FINGERPRINT 物理键和三个来源列与元数据一致，两项约束互补且无冲突。`docs/task-designs/M06-T05-design.md` 已完整冻结三个生产类型的唯一公开表面、证券代码/日期/分页值不变量、三字段元数据白名单、参数化 COUNT/分页 SQL、明确列、两种稳定排序、固定八项测试、两项 mutation、154/154 回归和精确四文件范围，七节顺序、无占位符、自洽性与 `git diff --check` 均通过，并已从任务卡和看板链接。`docs/task-handoffs/M06-T05-handoff.md` 已按 `next-task` 模板写入并先链接，只记录 M06-T05 与两项直接输入、设计优先读取顺序和先确认 146/146 基线再只创建完整测试取得缺三个生产类型 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；M06-T05 Java 实现尚未开始。

- **State evidence (start):** 2026-09-03：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M06-T05-design.md` 与 `docs/task-handoffs/M06-T05-handoff.md`，确认任务身份、四文件范围、严格 TDD 首个动作、结果级验收和进入上下文均可定位且无冲突，仓库指令明确授权直接在 `main` 工作，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有 `next-task` 交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-03：实现提交 `263513d` 以固定消息精确新增 `QueryCriteria`、`QuerySql`、`QuerySqlFactory` 和 `QuerySqlFactoryTest` 四个 Java 文件；严格 TDD 先由完整八项测试只因三个生产类型缺失在 `tensor-core:testCompile` 得到可归因 RED，再以最小实现取得定向 8/8 GREEN。受控 mutation 分别移除 criteria/filter 成员校验和 FINGERPRINT 末尾 `business_key` 排序，均由对应聚焦测试按预期捕获，恢复后 1/1 通过。主控在最终提交上新鲜复跑 reactor `test` 与 `verify`，两者均为 plugin-api 79/79、core 75/75，合计 154/154、0 failure、0 error、0 skipped，父项目/plugin-api/core 三层 Enforcer 通过；禁止/必需静态扫描、受保护路径、`git diff --check`、`clean`、精确四文件提交和干净工作树门禁均通过。任务级独立审查结论为规范符合、质量 `Approved`，最终整体审查为 `Ready to merge: Yes`，Critical、Important、Minor 均为 0；审查无法从差异内确认的启动/schema 前置条件已由 M02 构造期元数据不变量、M04 MySQL 49 表合同及新鲜基线验证解析为既有依赖而非任务缺口。因此满足结果级验收，执行 `IN_PROGRESS -> COMPLETED`；既有 `next-task` 交接保留为历史进入上下文。

### `M06-T06`

- **Goal:** 交付“`DatasetQueryService`、页码归一化和精度序列化”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`DatasetQueryService`、页码归一化和精度序列化”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M06-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T06` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T06` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：M06-T05 已按严格 TDD、定向 8/8、两项受控 mutation、提交态 reactor `test`/`verify` 154/154、三层 Enforcer、静态/范围/格式/清理门禁及两层无发现独立审查完成，实现提交 `263513d` 精确包含三个查询生产类型和唯一测试，权威看板提交 `19251a5` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择最小后继 M06-T06；其唯一直接依赖 M06-T05 已为 `COMPLETED`，安全 SQL/绑定/排序生成职责与本任务的 JDBC 执行、COUNT-first、页码归一及精确类型读取职责互补且无冲突。`docs/task-designs/M06-T06-design.md` 已完整冻结三个生产类型的唯一公开表面、页面不变量、显式 JDBC 绑定/类型矩阵、空结果和超界页顺序、固定 MySQL 8.4.6 八项测试、两项 mutation、154/154 回归及精确四文件范围，并由提交 `e9447c3` 创建且回填同一设计路径；七节顺序、任务卡链接、无未决占位符、自洽性与 `git diff --check` 均通过。`docs/task-handoffs/M06-T06-handoff.md` 已按 `next-task` 模板创建并链接，只记录 M06-T06 与直接输入 M06-T05，包含设计优先读取顺序和先确认 154/154 基线、再只创建完整 IT 取得缺三个生产类型 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；M06-T06 Java 实现尚未开始。随后用户明确要求按照权威任务看板执行当前任务；已完整读取设计文档、`next-task` 交接、任务卡、Global Constraints 和 Module Gate，确认任务身份、范围、直接依赖、首个动作及冻结合同均可定位且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。实现提交 `9c3fa44` 精确包含设计 Files 节的三个生产类型和唯一八项 IT：基线 reactor 154/154 后，仅创建 IT 得到只因三个生产类型缺失的 `testCompile` RED；最小实现后固定 `mysql:8.4.6` 定向 8/8，超界归一和 DECIMAL 读取两项受控 mutation 均使对应单测失败且恢复后通过。提交态定向 8/8、reactor `test`/`verify` 均为 plugin-api 79、core 75，共 154/154，0 failure、0 error、0 skipped，三层 Enforcer 通过；禁止扫描无输出并按预期退出 1，显式 JDBC/保序/factory/归一化扫描命中，受保护路径、`git diff --check`、精确四文件提交和 `clean`/干净工作树门禁通过。独立审查结论为 `Ready to merge: Yes`，无 Critical 或 Important 实现问题；唯一 Minor 是既有 `QuerySql(List.copyOf)` 使设计中的 null 值 repository 边界不可达，属范围外设计文字矛盾，不影响 factory 合法值路径。因此满足结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M07-T01`

- **Goal:** 交付“配置属性和同步 `RestClient`”。
- **Scope:** 包含该交付物、直接测试与验证，以及用户批准为真实 Spring Boot 注解绑定所需的 `data-plane/tensor-plugin-tushare/pom.xml` 最小依赖变更；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 中该任务卡的其他主要语言、接口和排除边界。
- **Acceptance:** “配置属性和同步 `RestClient`”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：M06-T06 已按严格 TDD、固定 MySQL 8.4.6 定向 8/8、两项受控 mutation、提交态 reactor `test`/`verify` 154/154、三层 Enforcer、静态/范围/格式/清理门禁和无 Critical/Important 的独立复审完成，实现提交 `9c3fa44`，权威看板提交 `a4e0d3d` 已先记录 `IN_PROGRESS -> COMPLETED`。按预定义顺序选择最小后继 M07-T01；其唯一直接依赖 M02-T05 为 `COMPLETED`，提交 `445b941` 与契约修复 `dd495ee` 提供 framework-free `DataSourcePlugin`/`PluginReadiness` 和安全领域错误边界，当前消费类型相对最终提交无差异。原任务卡要求属性 “bind”，但模块缺少配置属性依赖且原三文件范围不含 POM；用户明确选择按注解方式进行，批准将模块 POM 的最小 `spring-boot` 核心依赖和真实 Binder 测试纳入范围。无仓库改动的 Boot 3.5.16 探针已验证 `@DefaultValue` 默认值、单标量 Token 到脱敏嵌套 record 的转换与覆盖绑定可行。设计提交 `9b555ee` 创建并回填 `docs/task-designs/M07-T01-design.md`，完整冻结注解前缀、六组件/凭证/readiness、URI/时长/大小验证、JDK 同步 RestClient、User-Agent、零重试、九项 RED/GREEN、两项 mutation、137 基线/146 最终回归和精确四文件范围；七节顺序、无占位符、依赖比较及 `git diff --check` 自审通过。`docs/task-handoffs/M07-T01-handoff.md` 已按 `next-task` 模板写入并链接，只记录 M07-T01 与直接输入 M02-T05，包含同一设计路径、读取顺序和先确认 137/137 基线、再只加 POM/完整测试取得缺两个生产类 RED 的具体首个动作，因此执行真实的 `NOT_STARTED -> READY`；M07-T01 Java/POM 实现尚未开始。2026-09-03：用户再次明确要求按照权威任务看板执行当前任务，并先读取设计文档与交接文件；已完整读取并确认二者与看板、任务卡和当前模块边界一致，因此作为 `READY -> IN_PROGRESS` 的启动证据，保留既有 next-task 交接为入口上下文。

- **Completion evidence:** 2026-09-03：实现提交 `06682a8` 以精确四文件范围交付注解绑定的六组件 `TushareProperties`、脱敏 `Credential`、M02 readiness 投影和使用 JDK 连接/读取超时、配置 base URL、唯一 `Tensor/1.0` User-Agent、零应用重试且不读取 Token 的同步 `RestClient` factory；测试安全修复提交 `6e09e3a`、`e936287`、`9c49eb6` 依次消除断言/未匹配请求的秘密输出路径、确保 WireMock 生命周期关闭，并补齐正负时长 mutation 覆盖，未修改生产行为或越出原四文件集合。严格 TDD 先确认 137/137 基线，再取得仅缺两个生产类的可归因 RED；最终聚焦测试 9/9，明文、read-timeout、User-Agent、URL 泄漏和负时长受控 mutation 均按预期失败且恢复后通过，完整输出的 `m07-t01-secret-sentinel` 扫描无匹配。提交态 reactor `verify` 为 plugin-api 79/79、tushare 67/67，共 146/146，0 failure、0 error、0 skipped，父项目/plugin-api/tushare 三层 Enforcer 通过；依赖树仅显示 Boot BOM 管理的 `spring-boot:3.5.16`、`spring-web:6.2.19`、`spring-context:6.2.19`，禁用 API、受保护路径、格式和清理门禁均通过。任务级审查经两轮修复后清洁，最终整体审查发现的唯一 Important 和延期 Minor 已在单次最终修复波解决，范围化复审确认无新 Critical/Important；因此任务设计的结果级验收全部成立，执行 `IN_PROGRESS -> COMPLETED`。

### `M07-T02`

- **Goal:** 交付“Tushare 请求、响应 DTO 和严格返回校验”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Tushare 请求、响应 DTO 和严格返回校验”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T09, M07-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：M07-T01 已以实现提交 `06682a8`、测试安全修复 `6e09e3a`、`e936287`、`9c49eb6` 及看板提交 `7b0bec4` 按严格 TDD、聚焦 9/9、提交态 reactor 146/146、三层 Enforcer、依赖树、秘密/禁用 API/范围/格式/清理门禁和最终范围化复审完成。按预定义顺序选择最小未完成后继 M07-T02；其直接依赖 M03-T09 和 M07-T01 均为 `COMPLETED`，M03 冻结的 49 API/851 列元数据原序与 M07-T01 冻结的脱敏配置、同步 `RestClient`、超时、User-Agent、零应用重试和 `maxResponseBytes` 职责互补且无冲突，当前直接消费产物相对各自最终提交无差异。用户批准 M07-T02 对非零业务码使用固定脱敏通用失败，并允许 M07-T03 在 status/code/msg 仍为方法局部值时修改 client/validator 立即映射安全 `SourceException`，原始上游消息不得保存或进入异常、日志或公共包络。设计提交 `29a4349` 创建并回填 `docs/task-designs/M07-T02-design.md`，完整冻结六个新文件、唯一公开客户端 API、精确请求字段、方法局部 Token、`exchange`/`readNBytes(max + 1)`、重复键/尾随根值/禁止标量强制转换的严格 JSON、有序响应校验、固定安全失败、十项测试、三类 mutation、146/156 计数与精确实现范围；七节顺序、任务卡链接、无占位符、六文件计数、依赖比较和格式自审通过。`docs/task-handoffs/M07-T02-handoff.md` 已按 `next-task` 模板写入并链接，只记录 M07-T02 及直接输入 M03-T09/M07-T01，包含设计优先读取顺序和先确认 146/146 基线、再只创建完整测试以取得缺五个生产类 RED 的具体首个动作，因此执行真实的 `NOT_STARTED -> READY`；M07-T02 Java 实现尚未开始。2026-09-03：用户明确要求按照权威任务看板执行当前任务，并先读取设计文档和交接文件；已完整读取并确认 M07-T02 设计、交接、任务卡及直接依赖无冲突，作为 `READY -> IN_PROGRESS` 的显式启动证据。严格 TDD 先确认基线 plugin-api 79/79、Tushare 67/67，再得到仅因五个目标生产类型缺失的可归因 RED；实现提交 `3244d92` 精确创建设计规定的六个文件，聚焦测试 10/10，通过响应上限、字段顺序和业务消息秘密路径三类受控 mutation，四份完整日志的 `m07-t02-secret-sentinel` 扫描无命中。提交态 reactor `test` 与 `verify` 均为 plugin-api 79/79、Tushare 77/77，共 156/156，0 failure、0 error、0 skipped，父项目/plugin-api/tushare 三层 Enforcer 通过且仅有既有编码警告；禁用 API、授权机制、受保护路径、格式、精确六文件提交范围和 `clean` 后干净工作树门禁均通过，范围化复审未发现验收缺口，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M07-T03`

- **Goal:** 交付“鉴权、权限、限流、网络、超时和格式错误分类”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “鉴权、权限、限流、网络、超时和格式错误分类”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M07-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：M07-T02 已以实现提交 `3244d92` 和看板提交 `16e5bb2` 按严格 TDD、聚焦 10/10、提交态 reactor `test`/`verify` 156/156、三类 mutation、三层 Enforcer、秘密/静态/范围/格式/清理门禁及范围化复审完成。按预定义顺序选择最小未完成后继 M07-T03；其唯一直接依赖 M07-T02 为 `COMPLETED`，当前 client、validator 和 client test 相对实现提交无差异，M07-T02 已明确保留 status、业务 code/msg、transport/parse failure 的局部分类接缝并授权后继修改 client/validator。用户批准业务词表为 `token`/`认证`/`用户不存在`→auth、`每分钟`/`每小时`/`频率`/`限流`→rate、`权限`/`积分`→permission、未知非零业务码→payload invalid，且 rate 在与 permission 冲突时优先；TRD 与 M02 既有合同补充冻结 HTTP 401/403/429/5xx、DNS/connect、read timeout、invalid payload 到七项 `SourceException` 及其 retryable 的映射，原始 status/code/msg/body/Token/URI/cause 不得进入异常。设计提交 `c6b2ffe` 创建并回填 `docs/task-designs/M07-T03-design.md`，完整冻结五文件范围、四个包内静态分类入口、七项固定安全 message、auth→rate→permission 顺序、两遍有界 cause 顺序、M07-T02 client/validator 接缝、八项 classifier 与十项 client 测试、三类 mutation、156/164 计数和门禁；七节顺序、任务卡链接、无占位符、依赖比较和格式自审通过。`docs/task-handoffs/M07-T03-handoff.md` 已按 `next-task` 模板写入并链接，只记录 M07-T03 与直接输入 M07-T02，包含同一设计路径、设计优先读取顺序和先确认 156/156 基线、再只写测试取得缺 classifier RED 的具体首个动作，因此执行真实的 `NOT_STARTED -> READY`；M07-T03 Java 实现尚未开始。2026-09-03：用户明确要求按照权威任务看板执行当前任务并先读取设计文档和交接文件；已完整读取并核对 M07-T03 设计、交接、任务卡、唯一直接依赖与当前工作树，任务身份、范围、验收和首个动作一致且无冲突，作为 `READY -> IN_PROGRESS` 的显式启动证据。实现先取得 156/156 基线，再只写测试得到仅缺 `TushareErrorClassifier` 的可归因 RED；提交 `09c48c5` 精确交付设计规定的五文件，提交 `546f246` 仅补强独立审查指出的 transport 接缝与 16 层边界回归。最终聚焦测试保持 classifier 8/8、client 10/10，全量 reactor `verify` 为 plugin-api 79/79、tushare 85/85，共 164/164 且 0 failure、0 error、0 skipped，三层 Enforcer 通过；业务顺序、cause 顺序、秘密泄漏三类设计 mutation 及外层 transport、读取 IOException、16 层边界三类审查 mutation 均触发预期失败并已恢复。静态扫描无禁用项，七项来源异常只有 classifier 一个构造点，受保护路径、精确五文件范围、格式、秘密日志和清理门禁通过，工作树无生成物；独立复审确认原 Important/Minor 均已解决、无新 Critical/Important/Minor，结论 `Ready to merge: Yes`。HTTP、业务、transport、payload 分类及固定 message/retryable/零泄漏边界逐项满足设计与任务卡，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M07-T04`

- **Goal:** 交付“`TushareProPlugin` 描述符、readiness 和 49 接口下载”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`TushareProPlugin` 描述符、readiness 和 49 接口下载”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M07-T02, M07-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：M07-T03 已以实现提交 `09c48c5`、审查补强提交 `546f246` 及看板提交 `03d6c54` 按严格 TDD、聚焦 18/18、提交态 reactor `verify` 164/164、三层 Enforcer、六类受控 mutation、秘密/静态/范围/格式/清理门禁和无 Critical/Important/Minor 的独立复审完成。按预定义顺序选择最小未完成后继 M07-T04；其直接依赖 M07-T02/M07-T03 均为 `COMPLETED`，前者提供真实 M03 definition 到同步 `TushareProClient.execute` 与成功/空包络的唯一接缝，后者只补齐同一 client 的七项安全上游异常，当前接口、成功数据流与失败职责互补且无冲突。用户批准 disabled/缺 Token 的直接下载统一使用 private `TensorException`、`PLUGIN_DISABLED` 和固定 `Tushare Pro download is unavailable`，批准未知 API 使用不回显输入的固定 `IllegalArgumentException("Unknown Tushare API")`，并批准 `(TushareProperties,TushareProClient,List<DatasetDefinition>)` 公开构造器、单 Bean 配置形状及 `Tushare Pro`/`Tushare Pro 证券数据源` 固定文案。设计提交 `223a7e0` 创建并回填 `docs/task-designs/M07-T04-design.md`，完整冻结三文件范围、49 名称全集、元数据投影/不可变查找、readiness/拒绝顺序、Spring 本地装配、八项测试、三类 mutation、164/172 计数和门禁；七节顺序、无占位符、显式 49 名称与 49 YAML 同序、依赖比较及格式自审通过。`docs/task-handoffs/M07-T04-handoff.md` 已按 `next-task` 模板写入并链接，只记录 M07-T04 与直接输入 M07-T02/M07-T03，包含同一设计路径、设计优先读取顺序和先确认 164/164 基线、再只写测试取得缺两个生产类型 RED 的具体首个动作，因此执行真实的 `NOT_STARTED -> READY`；M07-T04 Java 实现尚未开始。

- **State evidence (start):** 2026-09-03：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M07-T04-design.md` 和 `docs/task-handoffs/M07-T04-handoff.md`，核对 M07 模块 Global Constraints、M07-T04 任务卡/Module Gate、M07-T02/M07-T03 设计及当前 SPI、properties、client、loader 接口，确认任务身份、三文件范围、直接依赖、首个动作与验收合同一致且无冲突。用户的本次请求作为启动证据，因此执行 `READY -> IN_PROGRESS`，既有 `next-task` 交接路径保留为进入上下文。

- **State evidence (completion):** 2026-09-03：实现提交 `608a7a0` 以固定消息精确新增设计 Files 节的三个 Java 文件，交付元数据驱动的 public final `TushareProPlugin`、单 Bean `TusharePluginConfiguration` 和八项普通测试；审查补强提交 `ae1a7c2` 仅修改同一测试文件，补齐反序输入保持、48/50 数量边界及缺凭证时 unavailable 优先级。修改前 reactor 基线为 plugin-api 79/79、tushare 85/85；严格 TDD 的纯净 RED 只因 `TushareProPlugin` 与 `TusharePluginConfiguration` 缺失而在 `testCompile` 非零，GREEN 聚焦 8/8，插件与 M03 总契约为 58/58。放宽数量门禁、移除 readiness 前置拒绝和错误 definition 委托三项受控 mutation 均形成可归因失败，恢复后 8/8；最终提交态 reactor `test` 与新鲜 `verify` 均为 plugin-api 79/79、tushare 93/93，合计 172/172、0 failure、0 error、0 skipped，三层 Enforcer 与 JAR 构建通过。禁止 API 名特例/网络/Token/日志/重试扫描无输出并退出 1，授权机制扫描、受保护路径、秘密日志、提交范围、`git diff --check` 与 Maven `clean` 门禁均通过；仅出现既有 platform-encoding 以及 Mockito/JDK 动态 agent 运行环境提示。独立审查先给出 `Ready to merge: Yes` 且无 Critical/Important，两项 Minor 测试盲点补强后复审确认 Critical、Important、Minor 均为 0、`Ready to merge: Yes`。因此满足任务设计和任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`，既有 `next-task` 交接保留为历史进入上下文。

### `M08-T01`

- **Goal:** 交付“fixture 元数据、插件和适配器”。
- **Scope:** 包含该交付物及其直接测试与验证；经项目所有者批准，额外允许修改 `data-plane/tensor-plugin-fixture/pom.xml` 以增加 Spring Boot 与 `tensor-core` 编译依赖，并修改 `data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java` 以仅放开 `fixture -> core`；不包含其他预定义任务的交付物，也不作其他任务卡外扩展。
- **Acceptance:** “fixture 元数据、插件和适配器”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T05, M04-T06, M05-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：在准备 M07-T04 的预定义最小后继时，发现 M08-T01 要求 Spring 条件配置并复用 `GenericDatasetAdapter`，但 fixture POM 仅依赖 plugin-api、既有 ArchUnit 门禁禁止 `fixture -> core`，且看板未列提供适配器的 M05-T05。项目所有者明确批准增加 fixture POM 的 Spring Boot/`tensor-core` 编译依赖、将 M05-T05 加为直接依赖，并把 ArchUnit 规则收窄为只允许 `fixture -> core`、继续禁止 fixture 依赖 Tushare/app；同时批准 profile 精确为 `acceptance`，仅与 `tensor.plugins.fixture.enabled=true` 联合激活。项目所有者还批准 M08-T01 直接构造唯一 `DatasetDefinition`、以测试验证 Java/YAML 一致而不复制或依赖 Tushare loader，冻结 `Fixture`/`Fixture 验收数据源`、`Fixture 日线`、`验收`、`trade_date`、五值必填 `scenario`、`[ts_code, trade_date]` 业务键、`[ts_code]` 过滤器和 `ts_code` 固定列；M08-T01 下载在场景工厂接入前以 `SOURCE_UNAVAILABLE` 和 `Fixture scenarios are not configured` 安全拒绝。任务仍为 `NOT_STARTED`，设计、交接和实现均尚未创建。

- **State evidence (readiness):** 2026-09-03：项目所有者明确批准整合后的架构型设计并复核书面设计。提交 `7915ed9` 已创建并回填 `docs/task-designs/M08-T01-design.md`，冻结双条件注册、唯一 definition、精确插件/API/scenario/四列/键/filter 元数据、临时安全下载拒绝、`GenericDatasetAdapter` 复用、POM/ArchUnit 窄例外、六项严格 TDD、260/266 计数和精确六文件实现范围；七节顺序、无占位符、接口/消息/属性一致性和 `git diff --check` 自审通过。提交 `aa4c895` 已创建 `docs/superpowers/plans/2026-09-03-m08-t01-fixture-plugin.md`，把完整测试与生产代码、RED/GREEN、模块/完整 reactor、依赖/JAR/静态/范围/提交/clean 门禁拆为 12 个可执行步骤，并修正为 reactor dependency tree 与 clean 后 scoped status。直接依赖 M02-T05、M04-T06、M05-T05 均为 `COMPLETED`；其 SPI/错误、固定 MySQL 8.4.6 fixture 表和通用适配职责互补，旧 `fixture -> core` 冲突已由项目所有者裁决且无剩余依赖冲突。主控在允许 Mockito/Byte Buddy attach 的环境新鲜运行完整基线 `verify`，plugin-api 79、core 75、Tushare 93、fixture 0、app 13，共 260/260，0 failure、0 error、0 skipped，六层 Enforcer 与既有 ArchUnit 通过；沙箱内同命令只因已知 attach 限制失败。`docs/task-handoffs/M08-T01-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M08-T01 与三项直接输入，包含同一设计/计划读取顺序和先确认干净 260/260 基线、再取得只缺两个生产类型 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；M08-T01 实现尚未开始。

- **State evidence (start):** 2026-09-03：用户明确要求按照权威任务看板执行当前任务；已完整读取看板链接的设计文档与交接文件并确认二者一致，故执行 `READY -> IN_PROGRESS`，保留现有交接路径作为入口上下文。

- **State evidence (completion):** 2026-09-03：提交 `79cc80d` 以固定消息 `feat(fixture): add acceptance data-source plugin` 精确交付两个修改文件和四个已跟踪的新文件；fixture 仅在 `acceptance` 与 `tensor.plugins.fixture.enabled=true` 同时成立时注册唯一插件和真实 `GenericDatasetAdapter`，Java/YAML 元数据、临时 `SOURCE_UNAVAILABLE` 边界及 `fixture -> core` 窄依赖均与设计一致，未实现 M08-T02/T03。严格 TDD 取得只缺 `FixturePlugin`/`FixtureConfiguration` 的可归因 RED 后聚焦 6/6 GREEN，模块 160/160；主控在允许 Mockito/Byte Buddy attach 的环境新鲜运行完整 reactor `verify`，plugin-api 79、core 75、Tushare 93、fixture 6、app 13，共 266/266，0 failure、0 error、0 skipped，六层 Enforcer 与 ArchUnit 通过。依赖树、fixture/app JAR 隔离、禁止能力、授权机制、受保护路径、格式与 clean 门禁均得到预期结果且工作树干净；任务级和最终独立审查均无 Critical、Important 或 Minor 发现，验收结果成立，故执行 `IN_PROGRESS -> COMPLETED`。

### `M08-T02`

- **Goal:** 交付“成功、空、上游失败、适配失败和写入失败模式”。
- **Scope:** 包含该交付物及其直接测试与验证；经项目所有者批准，除任务卡列出的两个新生产类型和测试外，允许修改 M08-T01 已创建的 `FixturePlugin.java`、`FixtureConfiguration.java` 和 `FixturePluginTest.java`，使插件从临时安全拒绝切换为委托 `FixtureEnvelopeFactory`；不包含其他预定义任务的交付物，也不作其他任务卡外扩展。
- **Acceptance:** “成功、空、上游失败、适配失败和写入失败模式”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M08-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：项目所有者批准 M08-T01/M08-T02 使用两阶段下载接缝；M08-T02 可在自身开始后修改 `FixturePlugin.java`、`FixtureConfiguration.java` 和 `FixturePluginTest.java`，接入五种确定性场景。M08-T02 仍为 `NOT_STARTED`，该批准不构成其设计、准备或启动证据。

- **State evidence (readiness):** 2026-09-03：M08-T01 已以提交 `79cc80d` 交付并由提交 `6ee1fbd` 记录为 `COMPLETED`，其 266/266 reactor、Enforcer、ArchUnit、依赖/JAR/静态/范围/clean 结果及两轮独立无发现审查证明唯一直接输入可用。提交 `9eb385c` 已创建并回填完整 `docs/task-designs/M08-T02-design.md`，冻结六文件范围、五值 enum、无状态工厂、精确 success/empty/source/type/persistence 结果、插件安全分派、双条件与真实 adapter 边界、12/166/272 测试计数和提交门禁；七节顺序、占位符、内部一致性与格式自审通过。`docs/task-handoffs/M08-T02-handoff.md` 已按 `next-task` 模板完整创建，只记录 M08-T02 及其直接消费的 M08-T01 输入，且具体首个动作从已完成设计开始实施。输入职责无冲突，故链接该交接并执行 `NOT_STARTED -> READY`；M08-T02 实现尚未开始。

- **State evidence (start):** 2026-09-03：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M08-T02-design.md` 与 `docs/task-handoffs/M08-T02-handoff.md`，并核对任务卡、M08-T01 设计和完成证据，确认任务身份、六文件范围、直接依赖、精确验收与严格 RED 首个动作一致且无冲突，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **Completion evidence:** 严格 TDD 先完整更新两份测试并在 fixture `testCompile` 取得只因 `FixtureScenario`、`FixtureEnvelopeFactory` 与新插件构造/行为缺失的可归因 RED；提交 `885313d`（`feat(fixture): provide deterministic acceptance scenarios`）精确实现设计六文件，提交 `54c2b30`（`test(fixture): strengthen scenario contracts`）按最终审查只增强两份既有测试的公开表面、无状态、全成功场景参数快照与 API/场景校验顺序门禁。主控在允许 Mockito/Byte Buddy attach 的环境对最终 `54c2b30` 新鲜运行完整 reactor `verify`，plugin-api 79、core 75、Tushare 93、fixture 12、app 13，共 272/272，0 failure、0 error、0 skipped，六层 Enforcer 与 ArchUnit 通过；依赖树仅显示批准的 core 与 Spring Boot autoconfigure direct compile 依赖，fixture JAR 含四个生产类型和 YAML，app JAR 无 fixture 类/资源，授权符号、受保护路径、任务范围、三份新文件跟踪、`git diff --check` 与 Maven clean 门禁通过，最终工作树干净。设计的原始禁用正则仅误命中必须存在的 `DataSourcePlugin` 两处；原始结果已保留，附加的单词边界 JDBC/Spring capability 扫描无匹配，且由依赖/JAR/ArchUnit/范围门禁交叉控制。任务级审查的两项证据问题经一轮复审全部解决；最终整体审查的一项 Important 与一项 Minor 测试门禁问题经唯一修复波全部解决，范围化复审确认无新 Critical/Important，最终没有未处理发现。因此五种确定性结果、真实 adapter 故障边界、插件安全分派、双条件两 Bean 与生产隔离均满足设计和任务卡，执行 `IN_PROGRESS -> COMPLETED`。

### `M08-T03`

- **Goal:** 交付“fixture 注册→适配→入库→查询集成测试”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “fixture 注册→适配→入库→查询集成测试”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T01, M05-T05, M06-T04, M06-T06, M08-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：准备 M08-T03 后继设计时确认任务卡明确要求使用生产 `PluginRegistry`、`DatasetAdapter`、`PersistenceService` 与 `DatasetQueryService`，而原看板只列 M05-T05、M06-T06、M08-T02，缺少分别交付 registry 与 persistence service 的 M05-T01、M06-T04。项目所有者回复“同意”，批准把 M08-T03 的直接依赖修订为 M05-T01、M05-T05、M06-T04、M06-T06、M08-T02；任务保持 `NOT_STARTED`，设计与交接仍为 `None`。

- **State evidence (readiness):** 2026-09-03：项目所有者明确同意 M08-T03 的单文件集成测试设计。提交 `c83cae7` 已创建并回填完整 `docs/task-designs/M08-T03-design.md`，冻结固定 MySQL 8.4.6、六迁移、acceptance/production context、生产 registry/adapter/catalog/persistence/query 装配、真实 batch 后 test-scope DataSource 故障注入、success/empty/type/rollback/production absence 五项测试、严格 RED、5/5、57/57、272/272 和精确一文件实现范围；七节顺序、占位符、内部一致性、范围、歧义与格式自审通过，无留给实施者的材料选择。五项直接依赖 M05-T01、M05-T05、M06-T04、M06-T06、M08-T02 均为 `COMPLETED`，分别提供不可变 registry、通用 typed adapter、单事务持久化、类型保真查询与确定 fixture 场景/marker，决策和约束互补且无冲突。`docs/task-handoffs/M08-T03-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M08-T03 和五项直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计优先读取顺序，以及先确认 272/272 与 52/52 基线再创建完整 IT 取得 test wiring RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；`FixtureFlowIT.java` 尚未创建，实现未开始。

- **State evidence (start):** 2026-09-03：用户明确要求按照权威任务看板执行当前任务，并先读取设计文档与交接文件；已完整读取 `docs/task-designs/M08-T03-design.md` 与 `docs/task-handoffs/M08-T03-handoff.md`，确认任务身份、精确一文件范围、五项直接依赖、固定 MySQL/生产流程装配、严格 RED 首个动作和结果级验收一致且无冲突。该请求作为本次 `READY -> IN_PROGRESS` 的明确启动证据；既有 `next-task` 交接路径保留为进入上下文。

- **State evidence (completion):** 2026-09-03：提交 `607e7de`（`test(fixture): verify plugin through core data flow`）创建唯一实现文件 `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`，提交 `43773af` 按独立审查唯一 Minor 建议补齐 Java 21 `DataSource` builder 方法显式委托；累计 `26a2583..43773af` 精确只包含该已跟踪 IT。严格 RED 的 production isolation 测试通过，其余四项仅因 `UnsupportedOperationException("Fixture flow not wired")` 失败；完成最小 wiring 后，在固定 MySQL 8.4.6 和六迁移上新鲜通过 `FixtureFlowIT` 5/5、与 `FlywaySchemaContractIT` 联跑 57/57，以及默认 reactor `test` 和 `verify` 各 272/272，均为 0 failure、0 error、0 skipped，六层 Enforcer 与 ArchUnit 通过。依赖树确认 fixture 仅为 app 的 test-scope 依赖；app 生产 JAR 和生产源码能力扫描无命中；授权测试符号、受保护路径、格式、Git 跟踪与累计范围门禁通过；Maven `clean` 后无 `target/` 且工作树干净。测试覆盖 acceptance 注册→生产 adapter→单事务入库→类型保真查询、EMPTY 零连接、TYPE_FAILURE 零数据库访问、真实 `executeBatch()` 后异常的完整回滚，以及 production 即使 enabled 也不注册 fixture；独立复审最终无未处理 Critical/Important。因此执行 `IN_PROGRESS -> COMPLETED`。

### `M09-T01`

- **Goal:** 交付“Boot 入口、请求标识和通用 API DTO”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Boot 入口、请求标识和通用 API DTO”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M01-T03, M02-T01, M02-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-03：准备 M09-T01 后继设计时确认任务卡要求 `RequestIdFilter` 生成服务端 UUID、校验并沿用客户端请求标识，而 M02-T01 已交付专用 `RequestId.newId()`，且其设计明确把客户端头沿用逻辑留给后续 `RequestIdFilter`；原看板只列 M01-T03、M02-T05，遗漏了被直接消费的 M02-T01。项目所有者回复“同意”，批准把 M09-T01 的直接依赖修订为 M01-T03、M02-T01、M02-T05；同时批准仅沿用规范小写 UUID，缺失或非法值生成新 UUID，并在请求结束时清理 MDC。任务保持 `NOT_STARTED`，设计与交接仍为 `None`。

- **State evidence (readiness):** 2026-09-03：项目所有者明确批准方案 1 和书面设计；提交 `1578374` 已创建并回填完整 `docs/task-designs/M09-T01-design.md`，冻结 Boot 根入口、规范小写 UUID 白名单、响应头/MDC 同值与无条件清理、不可变通用错误 DTO、严格 RED/GREEN、283/283 reactor 和精确六文件实现范围，七节顺序、占位符、内部一致性、范围与歧义自审通过，无留给实施者的材料选择。三项直接依赖 M01-T03、M02-T01、M02-T05 均为 `COMPLETED`，分别提供模块与禁止能力门禁、UUID 请求身份、错误码与 retryable 真值，决策和约束互补且无冲突。`docs/task-handoffs/M09-T01-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M09-T01 和三项直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计优先读取顺序，以及先完整创建 `RequestIdFilterTest.java` 并取得只因四个生产类型缺失而失败的严格 RED 首个动作。因此执行真实的 `NOT_STARTED -> READY`；功能实现尚未开始。

- **State evidence (start):** 2026-09-03：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M09-T01-design.md` 与 `docs/task-handoffs/M09-T01-handoff.md`，核对 M09 任务卡、OpenAPI `ApiError`/`FieldError`、TRD 17.1、三项直接依赖和 app 现有门禁，确认身份、范围、输入、验收和首个 RED 动作一致且无冲突。未改代码的 reactor 基线在允许 Mockito/Byte Buddy attach 的环境通过 plugin-api 79、core 75、Tushare 93、fixture 12、app 13，共 272/272，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **Completion evidence:** 严格 TDD 先完整创建唯一 `RequestIdFilterTest.java`，聚焦命令仅因 `TensorApplication`、`RequestIdFilter`、`ApiErrorResponse`、`FieldErrorResponse` 四个缺失交付类型在 `tensor-app:testCompile` 非零；最小实现随后创建 Boot 根入口、最高优先级请求关联 Filter、两个不可变错误 DTO 并删除旧示例 Main。提交 `367b0d1`（`feat(app): bootstrap Tensor and request correlation`）精确包含设计规定的五个新增 Java 文件和一个删除文件。最终提交态聚焦测试 11/11，完整 reactor `test` 与 `verify` 均为 plugin-api 79、core 75、Tushare 93、fixture 12、app 24，共 283/283，0 failure、0 error、0 skipped，六层 Enforcer、ArchUnit 和禁止 Git 能力门禁通过；JAR 精确包含四个新生产类型且不含旧 Main，授权/禁用职责扫描、受保护路径、格式、提交范围、Git 跟踪和 Maven `clean` 门禁通过，工作树为空且无 `target/`。独立审查未发现 Critical 或 Important；唯一 Minor“最高优先级未受测试保护”已通过同一 smoke 测试断言和移除 `@Order` 必然失败的 mutation 检查修复，范围化复审确认已解决且无新问题，结论 `Ready to merge: Yes`。Boot 根、规范小写 UUID 白名单、响应头/MDC 同值与异常后无条件清理、错误 DTO/OpenAPI/ErrorCode/不可变性合同和排除边界均满足设计及任务卡，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M09-T02`

- **Goal:** 交付“数据源、接口和数据集元数据 API”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据源、接口和数据集元数据 API”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T01, M05-T02, M09-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence (readiness):** 2026-09-03：项目所有者依次批准 M09-T02 的方案 1、四节完整设计和书面版本，冻结薄 Controller、三个精确投影 DTO、缺 Token 仍允许数据集查询、未知/不可下载插件 API 使用 `409 + PLUGIN_DISABLED`、未知插件/数据集使用 `409 + DATASET_MISCONFIGURED`、由私有 `TensorException` 分阶段携带错误码且标准错误体留给 M09-T05、独立 MockMvc 代替真实 Bean 装配，以及 `tensor-app/pom.xml` 仅增加 test scope `spring-test` 的范围例外。提交 `25ae585` 创建 `docs/task-designs/M09-T02-design.md`，提交 `7d98d49` 根据项目所有者批准把直接消费 `PluginRegistry` 的 M05-T01 补入设计/看板依赖并回填同一 Design 路径；最终设计已完整复读，七节顺序、占位符、内部一致性、范围、歧义和 `git diff --check` 自审通过，无留给实施者的材料选择。三项直接依赖 M05-T01、M05-T02、M09-T01 均为 `COMPLETED`：当前 `PluginRegistry.java`、`DatasetCatalog.java` 和 M09-T01 三项消费产物分别相对实现提交 `7ea252c`、`57771b0`、`367b0d1` 无差异，已记录的最终门禁分别为 89/89、99/99、283/283，决策和约束分别提供插件注册/readiness 快照、已验证数据集目录和请求关联/通用错误 DTO，互补且无冲突。`docs/task-handoffs/M09-T02-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M09-T02 和三项直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、依赖比较、设计优先读取顺序，以及先增加测试依赖并完整创建唯一测试取得只因四个生产类型缺失而失败的严格 RED 首个动作。因此执行真实的 `NOT_STARTED -> READY`；功能实现尚未开始。

- **State evidence (start):** 2026-09-03：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M09-T02-design.md`、`docs/task-handoffs/M09-T02-handoff.md`、M09 任务卡与 Module Gate，以及 M05-T01、M05-T02、M09-T01 三项直接依赖设计和消费产物，确认任务身份、六文件范围、四条路由、投影/失败合同、严格 TDD 顺序、首个动作与结果级验收均可定位且无冲突。当前为项目明确允许直接工作的 `main` 普通检出；未改代码的 reactor 基线在允许 Mockito/Byte Buddy attach 的环境通过 plugin-api 79、core 75、Tushare 93、fixture 12、app 24，共 283/283，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有 `next-task` 交接路径保留为进入上下文。

- **Completion evidence:** 严格 TDD 先只增加 test scope `spring-test` 并完整创建唯一 `DataSourceControllerTest.java`，聚焦命令仅因 `DataSourceController`、`DataSourceResponse`、`ApiDescriptorResponse`、`DatasetDefinitionResponse` 四个生产交付类型缺失而在 `tensor-app:testCompile` 非零；最小实现随后交付四条只读路由、三个精确 REST 投影 DTO、固定安全的 409 领域错误及 Servlet 注册边界。实现提交 `2c40b53`（`feat(api): expose data-source metadata`）精确包含设计规定的五个 Java 文件和一个 POM 修改；审查加固提交 `2c57da8`、`05c1a69` 只修改同一既有测试文件。最终聚焦测试 12/12，完整 reactor `test` 与 `verify` 均为 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，共 295/295，0 failure、0 error、0 skipped；六层 Enforcer、ArchUnit、禁止 Git 能力、授权/禁用静态扫描、生产 JAR、受保护路径、格式、提交范围、Git 跟踪和 Maven `clean` 门禁通过，工作树为空且不存在 `target/`。列 `displayOrder`/fixedColumn fallback、`queryMode`、`relatedParameter`、重复插件保留、API 唯一可下载语义、固定安全异常消息和 filter 原序的受控 mutation 均按预期失败并已恢复。最终范围化复审确认全部先前测试缺口已关闭，Critical/Important/Minor 均无发现。数据源 ready/unavailable 安全摘要、49 API/参数、已验证数据集摘要/定义、缺 Token 仍可查询、未知或不可下载场景错误码、响应头、不变性和敏感/内部字段排除均满足设计及 OpenAPI；真实 Bean 装配与标准错误体仍明确保留给后续预定义任务，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M09-T03`

- **Goal:** 交付“同步下载 API 与事务提交后结果”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “同步下载 API 与事务提交后结果”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T01, M05-T03, M05-T05, M06-T04, M07-T04, M09-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-04：准备 M09-T03 时确认通用 `DownloadService` 必须直接消费 M05-T01 的 `PluginRegistry` 与 `AdapterRegistry`，而原看板只列 M05-T03/M05-T05/M06-T04/M07-T04/M09-T01；项目所有者批准把 M05-T01 补入直接依赖。项目所有者随后依次批准五依赖构造器与既定 execute 签名、保持任务卡五文件范围并以真实 fixture/MySQL 手工装配 IT 验证、失败包络/异常分类边界、`Map<String,Object>` 请求 DTO，以及完整设计。`docs/task-designs/M09-T03-design.md` 已创建并回填；在书面设计复核和后续交接门禁完成前，任务保持 `NOT_STARTED` 且 Handoff 为 `None`。

- **State evidence (readiness):** 2026-09-04：项目所有者批准完整书面设计；提交 `0ce5c79` 已补齐 null 包络的 `SOURCE_PAYLOAD_INVALID` 边界、fixture 默认 `SUCCESS` 与测试专用 `PARAM_REQUIRED` 场景，并将批准设计转化为 `docs/superpowers/plans/2026-09-04-m09-t03-synchronous-download.md`。设计七节、计划强制头、五文件范围、12 个 checkbox 步骤、接口/方法名、10/15/295 测试计数、占位符、错误矩阵、静态/范围/提交门禁和格式自审通过，无留给实施者的材料选择。六项直接依赖 M05-T01、M05-T03、M05-T05、M06-T04、M07-T04、M09-T01 均为 `COMPLETED`，其注册、参数、适配、事务、来源和请求关联职责互补且无冲突。`docs/task-handoffs/M09-T03-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M09-T03 与六项直接输入，包含精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计优先读取顺序，以及先确认 295/295 基线、再只创建完整 IT 取得缺四个生产类型 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；Java 实现尚未开始。

- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M09-T03-design.md`、`docs/task-handoffs/M09-T03-handoff.md`、12 步实施计划、M09 任务卡与 Module Gate、OpenAPI 下载路径/两个 DTO schema，以及 M05-T01、M05-T03、M05-T05、M06-T04、M07-T04、M09-T01 六项直接依赖设计，确认任务身份、五文件范围、线性阶段、失败边界、严格 TDD、首个动作和结果级验收均可定位且无冲突。当前为项目明确允许直接工作的 `main` 普通检出；未改代码的 reactor 基线在允许 Mockito/Byte Buddy attach 的环境通过 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，共 295/295，零失败、错误或跳过，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有 `next-task` 交接路径保留为进入上下文。

- **Completion evidence:** 严格 TDD 先以完整 `DownloadControllerIT` 在四个生产类型缺失时取得只因 `DownloadService`、`DownloadController`、`DownloadRequest`、`DownloadResponse` 未解析而失败的 `tensor-app:testCompile` RED；实现提交 `ade4995`（`feat(api): execute synchronous dataset downloads`）精确新增设计批准的四个生产 Java 文件和一份恰有 10 个普通测试的真实 fixture/MySQL IT，审查修复提交 `c17346e`、`dad2ee4` 只加固同一 IT 的 literal factories、trigger 环境说明及双元素保序快照断言。三项受控 mutation 分别证明参数必须先于上游、EMPTY 必须零时钟/适配/持久化、SUCCESS 必须等待真实持久化提交，均按预期失败并恢复。主控在最终 `dad2ee4` 上新鲜通过固定 MySQL 8.4.6 `DownloadControllerIT` 10/10、与 `FixtureFlowIT` 联跑 15/15，以及 reactor `verify` 的 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，共 295/295；全部零 failure、error、skip，六层 Enforcer、app ArchUnit 与禁止 Git 能力测试通过。授权机制、语义修正后的禁用能力、JAR、十测试计数、受保护路径、累计五文件范围、格式、Git 跟踪和 Maven `clean` 门禁通过，工作树为空且无 `target/`；敏感词扫描无命中后七份固定临时日志已删除。计划原禁用正则中的裸 `DataSource` 只误命中设计强制使用的 `DataSourcePlugin`，因此保留原误报并以 `\bDataSource\b` 复验无命中；非 root `tensor` 用户创建真实 `SIGNAL` trigger 必须在临时 MySQL 容器启用 `--log-bin-trust-function-creators=1`。最终整体审查独立认可两项裁决，唯一保序测试 Minor 已修复且范围化复审确认无新破坏，最终无 Critical/Important；Flyway MySQL 支持提示和 Mockito 动态 agent 提示因本任务禁止修改 POM/生命周期而留给构建依赖维护。事务外线性编排、错误短路、EMPTY、真实插入/更新、固定时间、数据库整批回滚、header/body requestId 和提交后 SUCCESS 均达到设计结果，生产 Bean 总装配仍明确留给 M09-T06，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M09-T04`

- **Goal:** 交付“数据集定义与只读分页查询 API”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据集定义与只读分页查询 API”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T02, M06-T06, M09-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-04：准备 M09-T04 时确认可靠区分不存在数据集的 `409 + DATASET_MISCONFIGURED` 需要 Controller 在调用 `DatasetQueryService` 前直接消费 M05-T02 的 `DatasetCatalog.find`，而原看板只列 M06-T06、M09-T01；项目所有者明确批准方案 1，把 M05-T02 补为直接依赖，并依次批准双依赖薄 Controller、catalog-first 数据流、筛选/criteria 错误边界、PageResponse、只字符串化 boxed Long/BigDecimal 且保留 primitive 分页 number 的 Jackson 方案、固定四文件范围和八项真实 MySQL 集成测试。`docs/task-designs/M09-T04-design.md` 已创建并回填；任务保持 `NOT_STARTED`，Handoff 仍为 `None`，功能实现尚未开始。

- **State evidence (readiness):** 2026-09-04：项目所有者明确批准书面设计；提交 `66673bf` 已创建并回填完整 `docs/task-designs/M09-T04-design.md`，冻结唯一 records GET、catalog-first 409、固定筛选/criteria/MDC 边界、九组件深不可变 PageResponse、boxed Long/BigDecimal plain string 与 primitive 分页 number、固定 MySQL 八项 IT 和精确四文件范围。提交 `0d901a1` 已把批准设计转化为 `docs/superpowers/plans/2026-09-04-m09-t04-read-only-dataset-paging.md` 的 12 步严格 TDD 实施计划；设计七节、计划强制头、接口/类型/参数名、测试定义、8/23/295 计数、mutation、占位符、范围、格式和歧义自审通过，无留给实施者的材料选择。三项直接依赖 M05-T02、M06-T06、M09-T01 均为 `COMPLETED`，其当前 catalog、query/page 和 request-ID/error 消费产物分别相对实现提交 `57771b0`、`9c3fa44`、`367b0d1` 无差异，决策和约束互补且无冲突。`docs/task-handoffs/M09-T04-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M09-T04 与三项直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计/计划优先读取顺序，以及先确认 295/295 基线再只创建完整 IT 取得缺三个生产类型 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；Java 实现尚未开始。

- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M09-T04-design.md`、既有 `next-task` 交接、12 步实施计划、看板 M09-T04 行与详情、M09 任务卡/Global Constraints/Module Gate、OpenAPI records/PageResponse、三项直接依赖设计和当前 catalog/query/page/request-ID 生产接口，确认任务身份、四文件范围、首个动作、依赖输入及约束均可定位且无冲突。初次基线在受限沙箱中仅因 Mockito/Byte Buddy 无法 self-attach 失败；获准在可 attach 环境重跑后 reactor 为 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，合计 295/295，0 failure、0 error、0 skipped，六层 Enforcer、app ArchUnit 和禁止 Git 门禁通过，作为本次 `READY -> IN_PROGRESS` 的启动证据；既有交接路径保留为进入上下文。

- **State evidence (completion):** 2026-09-04：实现提交 `4617f22` 以固定消息 `feat(api): expose read-only dataset paging` 精确新增设计 Files 节的四个 Java 文件：唯一 records GET Controller、九组件深不可变 PageResponse、仅字符串化 boxed `Long`/`BigDecimal` 且使用 `toPlainString()` 的 Jackson module，以及恰八项真实 MySQL 8.4.6 集成测试。完整 IT 先于生产代码创建，严格 RED 仅因三个生产类型缺失而在 `tensor-app:testCompile` 非零；GREEN 聚焦 8/8，三类主闭环联跑 23/23，reactor `test`/`verify` 295/295。四项受控 mutation 分别证明 catalog-first、boxed Long、plain BigDecimal 和 primitive 分页 number 均会被测试捕获并在提交前恢复；静态、JAR、只读路由、受保护路径、范围、格式、Git 跟踪、敏感日志和 clean 门禁通过。主控在最终审查后独立重跑提交态聚焦 8/8、联跑 23/23 和 reactor `verify` 295/295，全部 0 failure、0 error、0 skipped；仅保留既有 Mockito 动态 agent 与 Flyway/MySQL 8.4 支持提示。任务级审查为规范符合且质量 `Approved`，最终整体审查为 `Ready to merge: Yes`，两者 Critical、Important、Minor 均为 0；唯一执行裁决已在既定八项测试内补入 MDC 缺失时固定失败且零数据库访问断言。因此达到设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M09-T05`

- **Goal:** 交付“全局异常和 HTTP 状态映射”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “全局异常和 HTTP 状态映射”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M09-T02, M09-T03, M09-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** 2026-09-04：项目所有者明确批准契约优先方案、完整聊天设计和书面设计，冻结 16 项错误码只产生 400/409/422/500/502/504、七类全局 handler、固定安全摘要、Core/Bean/MVC/值对象字段边界、downloads 持久化与 records 查询路由分类、脱敏 5xx 栈、精确两文件范围以及 25/320 测试计数；404/503 因 M00 冻结闭集没有对应 code 而不在本任务实现。提交 `2902f98` 创建并回填 `docs/task-designs/M09-T05-design.md`，提交 `a445f74` 将批准设计转化为 `docs/superpowers/plans/2026-09-04-m09-t05-global-exception-mapping.md` 的 12 步严格 TDD 计划；依赖一致性检查随后发现 M09-T02 明确延期的值对象 `IllegalArgumentException -> 400 + PARAM_INVALID`，提交 `afbba7f` 已把该既定输入补入设计/计划的第七类 handler 和现有 MVC 测试方法，未改变闭集、范围或测试计数。设计七节、计划强制头、单一审查任务、接口/方法/固定消息、严格 RED、25/25、320/320、三项 mutation、静态/JAR/安全/范围/提交/clean 门禁、占位符、Spring 6.2.19 API、类型、内部一致性和歧义自审通过，无留给实施者的材料选择。三项直接依赖 M09-T02、M09-T03、M09-T04 均为 `COMPLETED`，当前生产消费文件分别相对实现提交 `2c40b53`、`ade4995`、`4617f22` 无差异；其元数据 409/值对象 400、下载领域/字段/持久化、records 领域/MVC/查询职责互补且无冲突。`docs/task-handoffs/M09-T05-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M09-T05 与三项直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计/计划优先读取顺序，以及先确认 295/295 基线再只创建完整测试取得缺生产 handler RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；Java 实现尚未开始。2026-09-04：用户明确要求按照权威任务看板执行当前任务；已完整读取看板引用的设计文档与交接文件并确认二者一致，作为 `READY -> IN_PROGRESS` 的启动证据。实施严格遵循两文件范围：先取得仅因缺失 `GlobalExceptionHandler` 的 `tensor-app:testCompile` RED，再实现七组 handler；聚焦测试最终为 25/25，状态、客户端消息、路由分类和原始 Throwable 日志四次受控 mutation 分别产生 5、22、2、1 项预期失败并全部恢复。完整 reactor `test`、首次 `verify` 及提交后的最终 `verify` 均为 plugin-api 79、core 75、Tushare 93、fixture 12、app 61，共 320/320，0 failure、0 error、0 skipped；六层 Enforcer、ArchUnit 和禁止 Git 能力门禁通过。授权符号与 JAR 扫描命中，敏感 API/文本扫描无匹配并按预期退出 1，受保护路径、格式和精确范围门禁通过；独立审查结论为 `Ready to merge: Yes` 且 Critical/Important/Minor 均为零。实现提交 `b2dbb09` 使用固定消息 `feat(api): map domain errors safely` 并精确包含两个新增 Java 文件；最终 `mvn clean` 成功且工作树为空。因此满足设计与任务卡结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M09-T06`

- **Goal:** 交付“配置、脱敏、指标、健康和静态资源安全”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “配置、脱敏、指标、健康和静态资源安全”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M09-T01, M09-T02, M09-T03, M09-T04, M09-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T06` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T06` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence (readiness):** 2026-09-04：项目所有者依次批准把 M09-T02～T05 明确延期的生产 Bean 总装配纳入 M09-T06、以显式 Controller wrapper 接入观测而不使用 AOP/请求响应缓存、日志/指标/健康/静态资源安全方案、13 文件实现与验证范围，以及最终书面设计；提交 `7d46800` 创建并回填 `docs/task-designs/M09-T06-design.md`，提交 `afe6200` 澄清 query 完成日志边界，提交 `b4ea49e` 将批准设计转化为 `docs/superpowers/plans/2026-09-04-m09-t06-safe-configuration-observability.md` 的 16 步严格 TDD 计划。设计七节、计划强制头、7 新增/6 修改文件范围、四个新生产类型 RED、18/51/1/53/338 测试计数、五项 mutation、固定指标/标签/安全头/缓存/Actuator/秘密边界、单一审查、JAR/范围/格式/跟踪/clean 门禁和固定实现提交均已完整复读和自审，无留给实施者的材料选择。五项直接依赖 M09-T01～T05 均为 `COMPLETED`，当前生产消费文件分别相对实现提交 `367b0d1`、`2c40b53`、`ade4995`、`4617f22`、`b2dbb09` 无差异；它们提供的 Boot/请求身份、元数据装配缺口、下载事务边界、只读查询/精度语义和唯一脱敏错误出口互补且无冲突。`docs/task-handoffs/M09-T06-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M09-T06 与五项直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计/计划优先读取顺序，以及先确认工作树为空和 320/320 基线、再完整创建两个测试文件并取得只因四个新生产类型缺失而失败的严格 RED 首个动作。因此执行真实的 `NOT_STARTED -> READY`；功能实现尚未开始。
- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M09-T06-design.md`、实施计划、`docs/task-handoffs/M09-T06-handoff.md`、任务卡及其强制依赖输入，确认任务身份、13 文件范围、严格 TDD 顺序、验收计数和首个动作均可定位且无冲突；工作树为空，仓库指引允许直接在 `main` 工作。该请求作为本次 `READY -> IN_PROGRESS` 的显式启动证据，既有交接路径保留为进入上下文。
- **Completion evidence:** 严格 TDD 先完整创建 `ObservabilityTest` 与 `ProductionApplicationContextIT`，聚焦命令仅因 `ApplicationConfiguration`、`TensorMetrics`、`OperationLogger`、`WebSecurityHeadersConfiguration` 四个生产类型缺失而在 `tensor-app:testCompile` 非零；最小实现提交 `d7a47f3`（`feat(app): add safe configuration and observability`）精确包含设计规定的 13 个文件（7 新增、6 修改），未混入 POM、Core、plugin-api、fixture、迁移、DTO、既有全局 handler/filter、文档或生成物。普通观测测试 18/18、受影响 Tushare/Controller/全局异常回归 51/51、固定 MySQL 8.4.6 生产 Servlet 上下文 1/1、与 Flyway schema 合同联跑 53/53 均为零 failure/error/skip；提交态最终重新运行生产上下文 1/1 和默认 reactor `verify`，后者为 plugin-api 79、core 75、Tushare 93、fixture 12、app 79，共 338/338，六层 Enforcer、app ArchUnit 和禁止 Git 能力门禁通过。生产上下文证明 49 个 Tushare definitions 只加载一次并全部进入经 Flyway/schema 验证的 catalog/adapter registry；空 Token 时 health 200/UP、db UP 且元数据 `credentialConfigured=false`、`downloadAvailable=false`，四个未暴露 Actuator 路径均为 404，数据库停止后 health 为 503/DOWN；全部响应带 requestId、六个安全头与批准缓存策略，启动/响应/健康日志扫描不含共享 Token/密码哨兵、JDBC URL 或用户名。五项受控 mutation 分别产生 1（未知 key 额外完成事件）、1（异常 message 泄漏）、3（rows 指标改名）、8（替换 supplier 异常 identity）和 1（默认暴露 metrics）项预期失败/错误，每次精确恢复后均重跑 18/18。默认 reactor `test` 与 `verify` 在允许 Mockito/Byte Buddy attach 的环境均为 338/338；授权指标/标签、敏感 API、七环境变量、health-only exposure、env/configprops 防御、JAR、受保护路径、格式、精确范围、Git 跟踪与 Maven `clean` 门禁通过。唯一独立审查发现全局启用 Problem Details 会抢占既有安全错误 DTO；生产 IT 新增非法分页响应断言后按预期 RED，随后在同一 13 文件范围内用仅处理 `NoHandlerFoundException`/`NoResourceFoundException` 的窄 404 advice 替代该开关，一轮合并修正后普通 18/18、回归 51/51、生产上下文 1/1 和 reactor `verify` 338/338 全部恢复，既有 `PARAM_INVALID` 安全摘要与不回显原值合同得到真实生产上下文保护。五项指标低基数闭集、已知 download/query 唯一完成事件、参数/筛选白名单、结果与异常 identity、Servlet Bean 图、数据库健康、Actuator 隐藏、安全头/缓存及全部排除边界均满足设计和任务卡，因此执行 `IN_PROGRESS -> COMPLETED`。

### `M10-T01`

- **Goal:** 交付“Vue 依赖、Vitest、VTU 和 Playwright 配置”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Vue 依赖、Vitest、VTU 和 Playwright 配置”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence (readiness):** 2026-09-04：M09-T06 已以实现提交 `d7a47f3`、提交态生产上下文 1/1 和默认 reactor `verify` 338/338、五项受控 mutation、静态/JAR/秘密/范围/格式/跟踪/clean 门禁及独立审查修复完成，权威看板已记录其 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择最小更大 Order 的后继 M10-T01。`docs/task-designs/M10-T01-design.md` 已完整冻结 Node `>=24.15.0 <25`、十个精确依赖版本、六个 npm scripts、不 rewrite 的 `/api` 代理、Vitest/VTU 清理、桌面 Chrome Playwright 基线、严格 RED/GREEN、5 新增/2 修改文件和固定实现提交；七个模板章节、版本/文件/命令、占位符、生成路径忽略及格式门禁通过，无留给实施者的材料选择。唯一直接依赖 M00-T03 为 `COMPLETED`，当前 `docs/contracts/openapi-v1.yaml` 与 `docs/contracts/error-codes.md` 相对实现提交 `068f001` 无差异；其六条 `/api/v1` 路径、九个公开 schema、16 项错误码、大小写/精度/安全约束与 M10-T01 的透传代理和依赖基线互补且无冲突。`docs/task-handoffs/M10-T01-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M10-T01 与直接输入 M00-T03，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、读取顺序，以及先切换 Node 24.15+、再只创建完整 App smoke test 并取得缺少 `test:unit` script RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；当前默认 Node 22.22.3 仅作为已记录的实施前置风险，前端实现尚未开始。
- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M10-T01-design.md`、`docs/task-handoffs/M10-T01-handoff.md`、M10 任务卡与 Global Constraints、唯一直接依赖 M00-T03 的 OpenAPI/错误码契约，以及当前 `control-plane` 前端基线，确认任务身份、7 文件范围、精确版本、严格 TDD 顺序、验收与首个动作均可定位且无冲突；工作树为空且仓库指引允许直接在 `main` 工作。该请求作为本次 `READY -> IN_PROGRESS` 的显式启动证据，既有交接路径保留为进入上下文；本机默认 Node 22.22.3 仍须先切换到设计要求的 Node 24.15+，再开始 npm 与 RED/GREEN 操作。
- **Completion evidence:** 已通过 nvm 取得 Node 24.15.0/npm 11.12.1，并在只创建完整 `App.spec.js` 后观察到 `npm run test:unit -- --run` 以退出码 1 且直接原因为 `Missing script: "test:unit"` 的严格 RED。最终实现提交 `90c2029`（`build(ui): establish frontend test foundation`）精确包含设计规定的 5 个新增和 2 个修改文件：Node engine、十个精确顶层版本、六个脚本、官方 npm registry 的 lockfile v3、无 rewrite/无浏览器暴露地址的 `/api` 代理、Vitest/jsdom/VTU 清理、唯一 `h1` 的真实 App mount smoke test，以及无 `webServer` 的单一 Desktop Chrome Playwright 基线；未修改 App、业务页面、API client、Java/契约或其他排除项。独立审查首轮发现 lockfile 受本机镜像配置影响及标题唯一性断言缺口；在干净临时目录以官方 registry 重新生成后，196/196 个 registry package 均具备 `resolved` 和 `integrity` 且唯一主机为 `registry.npmjs.org`，同一审查者复核最终范围后给出 `Ready to merge: Yes`，无 Critical/Important/Minor。最终提交态以官方 registry 新鲜运行 `npm ci`，安装 172 packages、审计 173 packages 且 0 vulnerabilities；`test:unit` 与 `npm test` 各为 1 file/1 test 全通过，Vite 8.2.2 build 16 modules 成功。顶层 package/lock 精确版本与 lockfile v3、三份配置 import、敏感前缀扫描、三类生成路径 ignore、`git diff --check`、固定提交消息、干净工作树和 5 新增/2 修改范围门禁全部退出 0，因此满足任务设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M10-T02`

- **Goal:** 交付“`/downloads`、`/datasets` 路由和桌面布局”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`/downloads`、`/datasets` 路由和桌面布局”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence (readiness):** 2026-09-04：M10-T01 已以实现提交 `90c2029`、官方 registry `npm ci` 安装 172 packages/审计 173 packages 且 0 vulnerabilities、`test:unit` 与 `npm test` 各 1 file/1 test、Vite 8.2.2 build 16 modules、精确 5 新增/2 修改范围及独立审查无问题完成，权威看板已记录其 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择最小更大 Order 的后继 M10-T02。项目所有者批准方案 1；提交 `88fa148` 创建并回填 `docs/task-designs/M10-T02-design.md`，提交 `3262ae1` 将批准设计转化为 `docs/superpowers/plans/2026-09-04-m10-t02-routes-desktop-layout.md` 的严格 TDD 实施计划。设计七节完整冻结四条路由、三个路由名、语义化顶部导航、三个无状态 view、1280px 桌面 CSS、3 files/7 tests、7 新增/4 修改/5 删除范围和固定实现提交；无未决内容或留给实施者的材料选择。唯一直接依赖 M10-T01 为 `COMPLETED`，当前 `control-plane` 相对其实现提交无差异；其精确版本、Node 24、官方 lockfile、Vitest/jsdom/VTU、Element Plus setup 和脚本与 M10-T02 的 router、layout、memory history 测试及生产入口互补且无冲突。`docs/task-handoffs/M10-T02-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M10-T02 与 M10-T01 直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计/计划优先读取顺序，以及确认干净工作树和官方 registry 基线后只写三份测试取得缺目标模块 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；路由与桌面布局实现尚未开始。

- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务，批准方案 1 并再次回复“同意”；已完整读取 `docs/task-designs/M10-T02-design.md`、`docs/task-handoffs/M10-T02-handoff.md`、17 步实施计划、看板 M10-T02 行与详情、M10 任务卡/Global Constraints/Module Gate、唯一直接依赖 M10-T01 的设计和当前前端基线，确认任务身份、严格 TDD 顺序、精确 16 文件范围、验收、读取顺序与首个动作均可定位且无冲突。工作树为空，仓库指引允许直接在 `main` 工作；当前为普通主 checkout 且非 submodule，依用户授权在原地实施。上述请求和批准作为本次 `READY -> IN_PROGRESS` 的明确启动证据，既有 `next-task` 交接路径保留为进入上下文。

- **State evidence (blocker):** 2026-09-04：严格 TDD 已先取得三个 suite 仅因缺少 `src/router/index.js` 而失败的预期 RED，再形成设计规定的 7 新增、4 修改、5 删除实现；聚焦与完整单测均为 3 files/7 tests，清理、敏感前缀、生成路径 ignore、M10-T01 配置不变、格式和精确范围门禁通过。生产构建退出 0 且 1599 modules transformed，但全量 `.use(ElementPlus)` 与 `element-plus/dist/index.css` 产生 1004.62 kB JS，稳定触发 Vite 默认 500 kB chunk size warning；基线未安装生产 Element Plus 时仅 16 modules/63.72 kB JS 且无该提示，根因已定位。已批准设计同时强制生产全量安装 Element Plus、禁止修改 Vite 配置、固定 16 文件范围，并规定任何构建警告视为缺陷，现有约束无法同时满足；提高阈值只会隐藏症状，按需注册或拆包会改变既定安装/文件合同，均不能擅自实施。`docs/task-handoffs/M10-T02-handoff.md` 已改写为 `pause` 并记录当前 16 文件工作树、全部验证、剩余工作和解阻条件；因此执行 `IN_PROGRESS -> BLOCKED`。解阻条件是项目所有者明确批准把这一唯一已知 Element Plus 体积提示作为非阻断风险并修订设计，或批准修改安装策略、Vite 配置和文件范围的精确替代合同；实现尚未提交或独立审查。

- **State evidence (resolution):** 2026-09-04：项目所有者回复“同意”，明确批准把全量 Element Plus 导致的唯一、可重复 `Some chunks are larger than 500 kB after minification` 提示记录为非阻断风险，保持 `.use(ElementPlus)`、Element Plus CSS、既有 Vite 配置和 16 文件范围继续完成任务。提交 `a06d5fc` 已把该裁决写入既有设计与实施计划：构建仍须退出 0，只允许这一项提示，不接受其他 warning/error，不提高 `chunkSizeWarningLimit`，也不改为按需注册。暂停交接的 Resolution condition 因此完整满足；保留 `docs/task-handoffs/M10-T02-handoff.md` 作为历史恢复上下文，执行 `BLOCKED -> READY`。

- **State evidence (restart):** 2026-09-04：项目所有者的同一条“同意”明确授权保持全量 Element Plus 与既定 16 文件范围继续完成 M10-T02，作为本次 `READY -> IN_PROGRESS` 的启动证据。已再次完整读取修订后的 `docs/task-designs/M10-T02-design.md` 与历史 `pause` 交接，确认恢复任务仍为 M10-T02；剩余工作精确为按批准后的构建合同重跑验证、暂存并提交 16 文件、独立审查和提交态最终门禁，读取顺序仍为设计、实施计划、看板、历史交接及当前入口/配置，首个动作是重新运行聚焦/完整单测和生产构建并确认只出现获批的 Element Plus chunk-size 提示。当前工作树仍精确保留 7 新增、4 修改、5 删除，不含设计外生产变更；历史交接路径继续保留为恢复上下文。

- **Completion evidence:** 2026-09-04：实现提交 `d3d4be7`（`feat(ui): add Tensor routes and desktop layout`）精确包含设计规定的 16 个 `control-plane` 路径（7 新增、4 修改、5 删除），交付四条路由、三个稳定路由名、语义化两项顶部导航、三个无状态 view、可恢复 404、生产 router/Element Plus 入口和 1280px 桌面壳；修复提交 `993dd3c`（`fix(ui): improve shell color contrast`）仅修改同一范围内的 layout 测试与 CSS，以 `#1f5f99` 使交互文字对白色和 `#ecf5ff` 分别达到 6.656:1、6.045:1，并以真实 CSSOM 回归保护 active、focus 和 404 action。严格 TDD 初始 RED 仅因目标 router/layout 模块缺失；最终 Node 24.15.0 下聚焦与完整单测均为 3 files/7 tests，官方 registry `npm ci` 安装 172 packages 后两套提交态测试仍为 7/7，离线审计缓存报告 0 vulnerabilities；在线审计端点因网络超时未形成成功证据。Vite 8.2.2 构建转换 1599 modules 并生成 361.67 kB CSS、1004.62 kB JS，退出 0 且只有项目所有者已批准的 Element Plus chunk-size 提示。示例引用、敏感前缀扫描均无匹配，五个示例路径不存在，三个生成路径继续被忽略，M10-T01 package/lock/Vite/Vitest/Playwright/setup 配置无差异，格式、精确范围、干净工作树和普通 `main` checkout 门禁通过。最终独立复审为 Critical 0、Important 0、Minor 0，确认先前对比度问题已解决且无范围回归；因此达到设计与任务卡的结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M10-T03`

- **Goal:** 交付“Axios 客户端、DTO 和错误拦截”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Axios 客户端、DTO 和错误拦截”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T03, M10-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence (readiness):** 2026-09-04：M10-T02 已以提交 `d3d4be7`、范围内对比度修复 `993dd3c`、提交态 3 files/7 tests、Vite 构建仅含项目所有者批准的 Element Plus chunk-size 提示、精确 16 文件范围和独立复审无问题完成，权威看板已记录其 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择最小更大 Order 的后继 M10-T03。项目所有者依次批准前端逐请求生成 UUID `X-Request-Id`、唯一 Axios 实例默认 `/api/v1`/130000ms 并由显式函数覆盖、服务端 `ApiError` 与四类 `ClientError` 分离、五模块显式边界，以及完整架构/错误/测试设计；提交 `aa601f1` 创建并回填 `docs/task-designs/M10-T03-design.md`，提交 `9b14b12` 将其转换为 `docs/superpowers/plans/2026-09-04-m10-t03-api-client.md` 的 14 步严格 TDD 计划。设计七节完整冻结六个公开请求函数、JSDoc DTO、错误校验、12/19 测试计数、6 个新增文件和固定实现提交；计划的测试与五个生产代码块均通过 JavaScript 语法检查，无占位符、范围或类型表面缺口。两项直接依赖 M00-T03、M10-T01 均为 `COMPLETED`；当前 OpenAPI/错误码相对 `068f001` 无差异，当前 package/lock/Vite/Vitest/Playwright/setup 相对 `90c2029` 无差异，两者提供的六路径/九 DTO/16 错误/精度安全合同与 Axios 1.20.0、Node 24、测试/代理基线互补且无冲突。`docs/task-handoffs/M10-T03-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M10-T03 与两项直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计/计划优先读取顺序，以及确认空工作树和 Node 24.15 后只创建完整 `api.spec.js` 并取得缺目标模块 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；API 客户端实现尚未开始。

- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务，并连续回复“同意”授权继续；已完整读取 `docs/task-designs/M10-T03-design.md` 与 `docs/task-handoffs/M10-T03-handoff.md`，确认任务身份、精确六文件范围、冻结公开接口与错误边界、严格 TDD 顺序、结果级验收、直接依赖、读取顺序和首个 RED 动作均可定位且无冲突。准备提交 `bbb955c` 已将完整 `next-task` 交接链接到看板并执行 `NOT_STARTED -> READY`，当前工作树为空；上述请求和确认作为本次 `READY -> IN_PROGRESS` 的明确启动证据，既有交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-04：严格 TDD 先只创建完整 `api.spec.js`，聚焦命令在收集阶段仅因 `./dataSources.js` 等五个目标生产模块不存在而退出 1；实现提交 `8e4ff0d`（`feat(ui): add typed API client boundaries`）精确新增设计规定的六个 API 文件，交付唯一可原子配置的 Axios 实例、逐请求 UUID `X-Request-Id`、六个路径与 DTO 对齐的业务函数、16-code `ApiError` 校验和四类固定安全 `ClientError`。独立审查发现 Axios 1.20 默认序列化会省略已提供的 `null` query、公开 JSDoc 表面不完整及普通对象闭集查找的继承属性问题；修复提交 `caa9987` 以最终 URL 回归和显式 serializer 保留 `annDateFrom=`、补齐配置/路径/错误输入输出类型，并使用 own-property 查找。复审进一步以运行时证据确认 property-key 强制转换可让数组判别值穿透；第二轮 RED 精确得到 2 failed/10 passed，提交 `890ed88` 以 primitive string 守卫和数组 kind/code 回归关闭该边界。最终 Node 24.15.0 下聚焦测试为 1 file/12 tests，完整前端为 4 files/19 tests，均 0 failure；Vite 8.2.2 构建转换 1599 modules 并退出 0，只含项目所有者已批准的 Element Plus chunk-size 提示。默认 `/api/v1`/130000ms 和精确导出检查退出 0，敏感能力扫描无输出并退出 1，禁止修改路径无差异，三个生成路径保持忽略，`fb34bb2..890ed88` 合并范围精确为六个新增 API 文件，格式与干净工作树门禁通过。最终只读复审确认全部先前发现关闭且无新问题，Critical/Important/Minor 均为 None，结论 `Ready to merge: Yes`；因此结果级目标、错误安全、精度/字段保持、测试、构建和范围验收全部成立，执行 `IN_PROGRESS -> COMPLETED`。

### `M10-T04`

- **Goal:** 交付“日期、空值、精度格式化和无障碍状态组件”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “日期、空值、精度格式化和无障碍状态组件”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence (readiness):** 2026-09-04：M10-T03 已以实现提交 `8e4ff0d`、审查修复 `caa9987`/`890ed88`、Node 24.15.0 下聚焦 12/12、全量 19/19、Vite 构建仅含已批准 Element Plus chunk-size 提示、精确六文件范围和最终独立复审无问题完成，权威看板已记录其 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择最小更大 Order 的后继 M10-T04。项目所有者逐节批准并最终确认书面设计；提交 `f6cea50` 创建并回填 `docs/task-designs/M10-T04-design.md`，提交 `3086430` 将其转化为 `docs/superpowers/plans/2026-09-04-m10-t04-shared-ui-utilities.md` 的 17 步严格 TDD 计划。设计七节完整冻结 8 个纯函数、四态/字段错误 ARIA 语义、M11 紧凑下载日期与 M12 ISO 查询分界、默认/回退 `Asia/Shanghai`、DECIMAL/LONG 字符串精度、15/34 测试计数、精确 7 个新增文件和固定实现提交；计划覆盖全部设计要求，5 个 JavaScript 与 2 个 Vue 代码块通过 Node 24/`@vue/compiler-sfc` 语法编译检查，9/6 测试计数、占位符、类型、范围和格式自审通过，无留给实施者的材料选择。唯一直接依赖 M10-T01 为 `COMPLETED`，当前 package、lock、Vite、Vitest 和 setup 相对其完成提交 `90c2029` 无差异；Node 24.15.0 下重新验证当前基线为 4 files / 19 tests 全通过，Vite 转换 1599 modules 并退出 0，只产生已批准 chunk-size 提示，其运行时、测试和构建约束与 M10-T04 设计互补且无冲突。`docs/task-handoffs/M10-T04-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M10-T04 和直接输入 M10-T01，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计与计划优先读取顺序，以及只创建两个完整 spec 并取得目标生产模块缺失 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；生产和测试代码均尚未开始。

- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M10-T04-design.md`、`docs/task-handoffs/M10-T04-handoff.md` 和 17 步任务级实施计划，并核对 M10 模块任务卡、唯一直接依赖 M10-T01 的设计与当前前端测试配置。确认任务身份、精确七文件范围、冻结公开函数与 ARIA 语义、严格 TDD 顺序、首个 RED 动作和结果级验收均可定位且无冲突；普通 `main` 检出工作树为空，Node 24.15.0 下新鲜基线为 4 files / 19 tests 全通过，Vite 构建退出 0 且只有已批准的 Element Plus chunk-size 提示。该请求作为本次 `READY -> IN_PROGRESS` 的明确启动证据，既有 `next-task` 交接路径保留为进入上下文。

- **State evidence (blocker):** 2026-09-04：严格 TDD 与批准计划逐步执行完成；实现提交 `0a61e3f` 以固定消息精确新增七个设计文件，提交态聚焦 15/15、全量 34/34、Vite 构建、精确导出、范围、格式和禁止能力门禁均通过。独立审查发现一项 Important：`formatIngestedAt` 使用宽松 `new Date(value)`，Node 24 会把 `2026-02-30T02:30:15Z` 静默归一化为 3 月 2 日，并按宿主时区解释无偏移字符串；`TZ=UTC`/`TZ=America/New_York` 复现分别得到不同结果，违反同一设计的“非法时间保持原值、宿主时区不泄漏”保证。后端集成测试的实际 `Instant` JSON 为带 `Z` 的 ISO 字符串，OpenAPI 示例为带 `+08:00` 的字符串，现有权威输入足以限定兼容边界，但收紧已批准设计仍需项目所有者明确批准。`docs/task-handoffs/M10-T04-handoff.md` 已改写为 `pause`，完整记录现状、验证、两文件最小修复和解阻条件；因此执行 `IN_PROGRESS -> BLOCKED`。解阻条件是项目所有者明确批准严格日历日期、完整时分秒和必需 `Z`/数值偏移的最小修订合同，并授权写入设计/计划后继续严格 TDD。

- **State evidence (resolution):** 2026-09-04：项目所有者回复“同意”，明确批准 pause 交接中的最小修订合同并授权继续：`formatIngestedAt` 只转换严格 `YYYY-MM-DDTHH:mm:ss[.fraction](Z|±HH:mm)`、真实日历日期和有效时间/偏移，无偏移、不存在日期、非法时间/偏移及非字符串保持原值，同时继续接受后端 `Z` 与 OpenAPI 数值偏移。提交 `d5e9990` 已把该裁决写入既有 `docs/task-designs/M10-T04-design.md`，并在任务级实施计划增加精确两文件 RED/GREEN、双宿主时区验证、完整门禁和修复提交步骤；公开 API、依赖、组件和七文件总体范围不变。pause 交接的解阻条件因此满足，同一交接路径保留为历史恢复上下文，执行 `BLOCKED -> READY`。

- **State evidence (restart):** 2026-09-04：项目所有者的“同意”同时明确授权按批准的最小修订设计继续完成 M10-T04；在提交 `be7d0f2` 记录 `BLOCKED -> READY` 后，已重新完整读取修订设计与同一 `pause` 交接，确认恢复任务、剩余两文件修复、来源顺序、首个回归 RED 动作和最终验证均可定位且无冲突，工作树为空。该授权构成本次 `READY -> IN_PROGRESS` 的明确启动证据，既有交接路径保留为历史恢复上下文。

- **Completion evidence:** 2026-09-04：严格 TDD 的初始实现提交 `0a61e3f` 以固定消息精确新增设计规定的七个文件；项目所有者批准审查修订后，回归先取得 1 failed / 8 passed 的可归因 RED，修复提交 `0818fbc` 仅修改 `format.js` 与 `format.spec.js`，在构造 `Date` 前验证显式偏移时间戳的严格形状、真实公历日期、时分秒和偏移范围。Node 24.15.0 提交态新鲜门禁中，`TZ=UTC` 与 `TZ=America/New_York` 断言均退出 0，聚焦测试为 2 files / 15 tests、全量测试为 6 files / 34 tests 且全部通过，Vite 转换 1599 modules 并退出 0，仅含已批准的 Element Plus chunk-size 提示；精确公开导出、禁止能力扫描、受保护路径、格式、两提交范围和七文件总体范围均符合设计，工作树为空。最终只读复审确认原 Important 已关闭且无 Critical/Important，结论 `Ready to merge: Yes`；仅记录现实入库数据不可达的公元纪元边界 Minor，不阻塞当前 M11/M12 共享原语交付。因此日期转换、显式偏移入库时间、精度安全单元格、校验原语及四态/字段错误无障碍组件均达到结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M11-T01`

- **Goal:** 交付“数据源与接口分组搜索选择组件”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据源与接口分组搜索选择组件”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence (readiness):** 2026-09-04：M10-T04 已以初始实现 `0a61e3f`、严格时间戳修复 `0818fbc`、Node 24.15.0 下聚焦 15/15、全量 34/34、双宿主时区断言、生产构建、精确七文件范围和最终独立复审无 Critical/Important 完成，并由提交 `e999fb5` 在权威看板记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择最小更大 Order 的后继 M11-T01。准备设计时发现 PRD 八分类与当前元数据七分类冲突，项目所有者明确决定暂不拆分；据此修订 M11 任务卡为按描述符 category 通用分组、当前 49 项保持七组，并批准三个受控组件的接口、单来源默认、不可用原因、搜索、选择、说明、职责边界和测试方案。提交 `d17f81a` 已创建并回填 `docs/task-designs/M11-T01-design.md`，冻结精确六文件、10/44 测试计数、七组事实、严格 RED/GREEN、固定实现提交和失败边界；提交 `2252a7b` 已把设计转化为 `docs/superpowers/plans/2026-09-04-m11-t01-download-selectors.md` 的 11 步实施计划，三个 JavaScript 测试块共 10 项、三个 Vue SFC 块均通过 Node 24 语法/编译检查，覆盖、占位符、类型、范围和格式自审通过。唯一直接依赖 M10-T03 为 `COMPLETED`，当前 `dataSources.js`/`api.spec.js` 相对其最终实现提交 `890ed88` 无差异，公开来源/API 描述符和现有 Vue/Element Plus/Vitest 基线与本设计互补且无冲突。`docs/task-handoffs/M11-T01-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M11-T01 和直接输入 M10-T03，包含同一设计路径、依赖 artifact/decision/rationale/constraint/usage/readiness evidence、设计/计划读取顺序，以及先只创建三个完整 spec 并取得目标 SFC 缺失 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；生产和测试代码均尚未开始。

- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M11-T01-design.md`、`docs/task-handoffs/M11-T01-handoff.md` 和 11 步任务级实施计划，并核对 M11 模块任务卡、唯一直接依赖 M10-T03 的设计与当前 API/前端测试配置。确认任务身份、精确六文件范围、三个受控组件接口、当前七组元数据决策、严格 TDD 顺序、首个 RED 动作和结果级验收均可定位且无冲突；普通 `main` 检出工作树为空，Node 24.15.0 下新鲜基线为 6 files / 34 tests 全部通过，Vite 构建退出 0 且只有已批准的 Element Plus chunk-size 提示。该请求作为本次 `READY -> IN_PROGRESS` 的明确启动证据，既有 `next-task` 交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-04：严格 TDD 先完整创建三份 spec，聚焦命令在收集阶段仅因三个目标 SFC 缺失而按预期非零；最小实现首跑 9/10 后，依据 Element Plus 2.14.5 键盘状态机定位到首次 `ArrowDown` 只展开而不推进高亮索引，以公开 `default-first-option` 单行修复恢复“ArrowDown 不发事件、Enter 选择首项”语义。实现提交 `e08f467` 以固定消息精确新增设计规定的六个文件；独立审查提出两个键盘事件验证 Important，提交 `38ddb8a` 仅强化既有 `ApiSelect` 第五项测试，明确断言 ArrowDown 前置无事件及 disabled 后键盘输入不增加事件，复审确认两项均关闭且 Critical、Important、Minor 全为 0，结论 `Ready to merge: Yes`。Node 24.15.0 最终提交态新鲜门禁中，聚焦为 3 files / 10 tests、全量为 9 files / 44 tests，全部通过；Vite 转换 1599 modules 并退出 0，仅含已批准的 Element Plus chunk-size 提示。`71a3f27..38ddb8a` 总范围精确为六个新增组件/测试文件，格式检查通过，禁止网络、错误解释、凭证和 HTML 注入扫描无输出，受保护依赖、配置、API、通用组件、工具、路由、布局、页面和样式路径无差异，工作树为空。因此三个受控组件的来源默认/不可用说明、七组元数据投影、双字段搜索、键盘/禁用事件、查询方式和纯文本渲染均达到结果级验收，执行 `IN_PROGRESS -> COMPLETED`。

### `M11-T02`

- **Goal:** 交付“元数据驱动动态参数表单”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “元数据驱动动态参数表单”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T03, M10-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence (readiness):** 2026-09-04：M11-T01 已以实现提交 `e08f467`、审查测试修复 `38ddb8a`、Node 24.15.0 下聚焦 10/10、全量 44/44、生产构建、精确六文件范围和最终独立复审无问题完成，并由提交 `5c6064a` 在权威看板记录 `IN_PROGRESS -> COMPLETED`；按预定义顺序选择最小更大 Order 的后继 M11-T02。项目所有者已明确批准参数表单的公开接口、六类控件映射、显示/提交格式、默认值/reset/快照失效、必填/类型/pattern/范围错误、首错聚焦和 9 项测试设计；提交 `ca947e7` 已创建并回填 `docs/task-designs/M11-T02-design.md`，其七个必需章节冻结精确三文件范围、严格 RED、聚焦 9/9、全量 53/53、固定实现提交和失败边界，没有占位或未决材料。两项直接依赖 M10-T03、M10-T04 均为 `COMPLETED`；当前 `dataSources.js`/相关 API 测试相对 M10-T03 最终修复提交 `890ed88` 无差异，当前 `date.js`、`validation.js`、`FieldError.vue` 及相关测试相对 M10-T04 最终修复提交 `0818fbc` 无差异，参数描述符合同与通用转换、校验和错误渲染原语互补且无冲突。Node 24.15.0 下重新验证当前前端基线为 9 files / 44 tests 全通过，Vite 8.2.2 构建转换 1599 modules 并退出 0，只产生既有 Element Plus chunk-size 提示。`docs/task-handoffs/M11-T02-handoff.md` 已按 `next-task` 模板完整创建并链接，只记录 M11-T02 与两项直接输入，包含同一设计路径、精确 artifact/decision/rationale/constraint/usage/readiness evidence、设计优先读取顺序，以及确认干净 Node 24 基线后只创建完整 spec 并取得缺组件 RED 的具体首个动作。因此执行真实的 `NOT_STARTED -> READY`；实现尚未开始。

- **State evidence (start):** 2026-09-04：用户明确要求按照权威任务看板执行当前任务；已完整读取 `docs/task-designs/M11-T02-design.md`、`docs/task-handoffs/M11-T02-handoff.md`、M11-T02 任务卡、M10-T03/M10-T04 直接依赖设计与其当前产物，并确认公开接口、六类控件、校验/快照边界、精确三文件范围、严格 TDD 顺序和首个动作均可定位且无冲突。普通 `main` 检出由仓库级 `AGENTS.md` 明确授权原地工作；开始前工作树干净，Node.js 24.15.0 下前端基线为 9 files / 44 tests 全通过。该请求构成本次 `READY -> IN_PROGRESS` 的明确启动证据；既有 `next-task` 交接路径保留为进入上下文。

- **Completion evidence:** 2026-09-04：严格 TDD 先只创建完整 9 项 `DynamicParameterForm.spec.js`，聚焦命令在收集阶段仅因 `./DynamicParameterForm.vue` 不存在而非零；实现提交 `3e2b3ce` 以固定消息 `feat(ui): render download parameters from metadata` 精确新增设计规定的组件、composable 和测试三个文件，交付六类 Element Plus 控件、默认值/reset、必填/类型/pattern/双向范围校验、纯文本错误与 ARIA、首错聚焦、禁用锁定及顺序稳定的成功快照。独立审查发现合法字段名 `constructor` 会与普通错误对象原型冲突，以及 `value-format`、reset 清错的测试 mutation 缺口；修复提交 `e6024c7` 将字段错误字典改为 null-prototype，并在既有 9 项中加入对应回归。`constructor` 用例先取得仅该缺陷导致的 1 failed / 8 passed RED 后恢复 9/9；破坏 MONTH `value-format` 和移除 `clear(errors)` 的两项受控 mutation 均使对应测试按预期失败，恢复后聚焦 9/9。当前 HEAD 在 Node.js 24.15.0 下新鲜验证聚焦 1 file / 9 tests、完整前端 10 files / 53 tests 全通过且无未处理 rejection、console 或 Vue warning；Vite 8.2.2 构建转换 1599 modules 并退出 0，仅有既有 Element Plus chunk-size 提示。`ca65718..e6024c7` 精确只新增设计规定的三个文件，禁止能力扫描无输出并退出 1，受保护路径无差异，`git diff --check` 通过且工作树干净。原评审者范围化复审确认三项发现全部解决，无剩余 Critical、Important 或 Minor，结论 `Ready to merge: Yes`。因此结果级目标、公开接口、校验/快照/可访问性边界、严格测试和精确范围均满足，执行 `IN_PROGRESS -> COMPLETED`；既有 `next-task` 交接路径保留为历史进入上下文。

### `M11-T03`

- **Goal:** 交付“下载 composable、控件锁定和请求世代”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “下载 composable、控件锁定和请求世代”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M11-T04`

- **Goal:** 交付“成功、空和失败结果组件”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “成功、空和失败结果组件”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T04, M11-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M11-T05`

- **Goal:** 交付“`DownloadView` 页面集成和组件回归”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`DownloadView` 页面集成和组件回归”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M11-T01, M11-T02, M11-T03, M11-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M12-T01`

- **Goal:** 交付“数据集选择与动态筛选表单”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据集选择与动态筛选表单”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T03, M10-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M12-T02`

- **Goal:** 交付“全字段、固定列和横向滚动表格”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “全字段、固定列和横向滚动表格”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M12-T03`

- **Goal:** 交付“20/50/100 分页组件”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “20/50/100 分页组件”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M12-T04`

- **Goal:** 交付“查询 composable、竞态和超界页处理”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “查询 composable、竞态和超界页处理”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M12-T05`

- **Goal:** 交付“`DatasetView` 页面集成和组件回归”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`DatasetView` 页面集成和组件回归”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M12-T01, M12-T02, M12-T03, M12-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 `Task M12-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M13-T01`

- **Goal:** 交付“前端确定性构建及静态资源复制”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “前端确定性构建及静态资源复制”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T02, M11-T05, M12-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 `Task M13-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 `Task M13-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M13-T02`

- **Goal:** 交付“单个可执行 JAR 打包和内容检查”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “单个可执行 JAR 打包和内容检查”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M09-T06, M13-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 `Task M13-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 `Task M13-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M13-T03`

- **Goal:** 交付“生产配置、CORS、SPA fallback 和优雅停机”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “生产配置、CORS、SPA fallback 和优雅停机”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M09-T06, M13-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 `Task M13-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 `Task M13-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M13-T04`

- **Goal:** 交付“全新环境运行说明和启动 smoke test”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “全新环境运行说明和启动 smoke test”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M13-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 `Task M13-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 `Task M13-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M14-T01`

- **Goal:** 交付“fixture 页面端到端主闭环”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “fixture 页面端到端主闭环”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M13-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M14-T02`

- **Goal:** 交付“下载失败、空结果、幂等和回滚矩阵”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “下载失败、空结果、幂等和回滚矩阵”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M14-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M14-T03`

- **Goal:** 交付“查询、分页、宽表、竞态和无障碍 E2E”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “查询、分页、宽表、竞态和无障碍 E2E”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M14-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M14-T04`

- **Goal:** 交付“49 数据集自动契约与页面回归驱动”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “49 数据集自动契约与页面回归驱动”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T09, M04-T06, M14-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M14-T05`

- **Goal:** 交付“真实 Tushare 49 接口受控页面验收”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “真实 Tushare 49 接口受控页面验收”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M14-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M14-T06`

- **Goal:** 交付“`daily` 与 `balancesheet` 性能验证”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`daily` 与 `balancesheet` 性能验证”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M14-T03, M14-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T06` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T06` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M14-T07`

- **Goal:** 交付“Token、SQL、依赖、网络和运行安全验证”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Token、SQL、依赖、网络和运行安全验证”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M14-T02, M14-T03, M14-T04, M14-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T07` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T07` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M14-T08`

- **Goal:** 交付“全新环境 AC-001～018 与发布证据包”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “全新环境 AC-001～018 与发布证据包”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M14-T01, M14-T02, M14-T03, M14-T04, M14-T05, M14-T06, M14-T07.
- **Sources:** `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T08` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 `Task M14-T08` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

## Risks

- 用户回复、权限审批、网络故障、真实 Tushare 服务不可用和 CI 排队可能形成外部等待；这些等待不计入任务 AI 工时，并应单独记录为进度风险。
- 真实 Tushare 验收依赖可用 Token 和上游服务；Token 不得进入前端、业务 API、数据库、普通日志、异常正文或诊断端点。
- Git 不可用时不得初始化新仓库；各任务改为在验收证据中记录修改文件和校验输出。
