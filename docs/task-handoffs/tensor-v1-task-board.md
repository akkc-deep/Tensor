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
| 22 | M04-T01 | V1 基础与组织表 | `READY` | M03-T02 | docs/task-designs/M04-T01-design.md | docs/task-handoffs/M04-T01-handoff.md |
| 23 | M04-T02 | V2 行情、交易与资金表 | `NOT_STARTED` | M03-T03, M03-T04 | None | None |
| 24 | M04-T03 | V3 互联互通与转融通表 | `NOT_STARTED` | M03-T05 | None | None |
| 25 | M04-T04 | V4 财务与披露宽表 | `NOT_STARTED` | M03-T06 | None | None |
| 26 | M04-T05 | V5 公司行动、股东与治理表 | `NOT_STARTED` | M03-T07, M03-T08 | None | None |
| 27 | M04-T06 | V6 fixture 表与 49 表结构总校验 | `NOT_STARTED` | M04-T01, M04-T02, M04-T03, M04-T04, M04-T05 | None | None |
| 28 | M05-T01 | `PluginRegistry` 与 `AdapterRegistry` | `NOT_STARTED` | M02-T05 | None | None |
| 29 | M05-T02 | `DatasetCatalog` 和启动元数据/表结构校验 | `NOT_STARTED` | M03-T09, M04-T06 | None | None |
| 30 | M05-T03 | 元数据驱动参数校验 | `NOT_STARTED` | M02-T02, M03-T09 | None | None |
| 31 | M05-T04 | 严格日期、文本、整数和精确数值转换 | `NOT_STARTED` | M02-T03 | None | None |
| 32 | M05-T05 | `GenericDatasetAdapter`、重复键和指纹键 | `NOT_STARTED` | M05-T02, M05-T03, M05-T04 | None | None |
| 33 | M06-T01 | 白名单 SQL 标识符和 Upsert 模板 | `NOT_STARTED` | M02-T03, M04-T06 | None | None |
| 34 | M06-T02 | 复合键与指纹键编码/绑定 | `NOT_STARTED` | M05-T05 | None | None |
| 35 | M06-T03 | 已有键预查、数据集锁和插入/更新计数 | `NOT_STARTED` | M06-T01, M06-T02 | None | None |
| 36 | M06-T04 | 单事务批量 Upsert 与回滚 | `NOT_STARTED` | M06-T03 | None | None |
| 37 | M06-T05 | 查询条件白名单和 COUNT/分页 SQL | `NOT_STARTED` | M02-T03, M04-T06 | None | None |
| 38 | M06-T06 | `DatasetQueryService`、页码归一化和精度序列化 | `NOT_STARTED` | M06-T05 | None | None |
| 39 | M07-T01 | 配置属性和同步 `RestClient` | `NOT_STARTED` | M02-T05 | None | None |
| 40 | M07-T02 | Tushare 请求、响应 DTO 和严格返回校验 | `NOT_STARTED` | M03-T09, M07-T01 | None | None |
| 41 | M07-T03 | 鉴权、权限、限流、网络、超时和格式错误分类 | `NOT_STARTED` | M07-T02 | None | None |
| 42 | M07-T04 | `TushareProPlugin` 描述符、readiness 和 49 接口下载 | `NOT_STARTED` | M07-T02, M07-T03 | None | None |
| 43 | M08-T01 | fixture 元数据、插件和适配器 | `NOT_STARTED` | M02-T05, M04-T06 | None | None |
| 44 | M08-T02 | 成功、空、上游失败、适配失败和写入失败模式 | `NOT_STARTED` | M08-T01 | None | None |
| 45 | M08-T03 | fixture 注册→适配→入库→查询集成测试 | `NOT_STARTED` | M05-T05, M06-T06, M08-T02 | None | None |
| 46 | M09-T01 | Boot 入口、请求标识和通用 API DTO | `NOT_STARTED` | M01-T03, M02-T05 | None | None |
| 47 | M09-T02 | 数据源、接口和数据集元数据 API | `NOT_STARTED` | M05-T02, M09-T01 | None | None |
| 48 | M09-T03 | 同步下载 API 与事务提交后结果 | `NOT_STARTED` | M05-T03, M05-T05, M06-T04, M07-T04, M09-T01 | None | None |
| 49 | M09-T04 | 数据集定义与只读分页查询 API | `NOT_STARTED` | M06-T06, M09-T01 | None | None |
| 50 | M09-T05 | 全局异常和 HTTP 状态映射 | `NOT_STARTED` | M09-T02, M09-T03, M09-T04 | None | None |
| 51 | M09-T06 | 配置、脱敏、指标、健康和静态资源安全 | `NOT_STARTED` | M09-T01, M09-T02, M09-T03, M09-T04, M09-T05 | None | None |
| 52 | M10-T01 | Vue 依赖、Vitest、VTU 和 Playwright 配置 | `NOT_STARTED` | M00-T03 | None | None |
| 53 | M10-T02 | `/downloads`、`/datasets` 路由和桌面布局 | `NOT_STARTED` | M10-T01 | None | None |
| 54 | M10-T03 | Axios 客户端、DTO 和错误拦截 | `NOT_STARTED` | M00-T03, M10-T01 | None | None |
| 55 | M10-T04 | 日期、空值、精度格式化和无障碍状态组件 | `NOT_STARTED` | M10-T01 | None | None |
| 56 | M11-T01 | 数据源与接口分组搜索选择组件 | `NOT_STARTED` | M10-T03 | None | None |
| 57 | M11-T02 | 元数据驱动动态参数表单 | `NOT_STARTED` | M10-T03, M10-T04 | None | None |
| 58 | M11-T03 | 下载 composable、控件锁定和请求世代 | `NOT_STARTED` | M10-T03 | None | None |
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

### `M04-T02`

- **Goal:** 交付“V2 行情、交易与资金表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V2 行情、交易与资金表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T03, M03-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M04-T03`

- **Goal:** 交付“V3 互联互通与转融通表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V3 互联互通与转融通表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M04-T04`

- **Goal:** 交付“V4 财务与披露宽表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V4 财务与披露宽表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M04-T05`

- **Goal:** 交付“V5 公司行动、股东与治理表”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V5 公司行动、股东与治理表”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T07, M03-T08.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M04-T06`

- **Goal:** 交付“V6 fixture 表与 49 表结构总校验”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “V6 fixture 表与 49 表结构总校验”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M04-T01, M04-T02, M04-T03, M04-T04, M04-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T06` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Task M04-T06` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M05-T01`

- **Goal:** 交付“`PluginRegistry` 与 `AdapterRegistry`”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`PluginRegistry` 与 `AdapterRegistry`”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M05-T02`

- **Goal:** 交付“`DatasetCatalog` 和启动元数据/表结构校验”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`DatasetCatalog` 和启动元数据/表结构校验”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T09, M04-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M05-T03`

- **Goal:** 交付“元数据驱动参数校验”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “元数据驱动参数校验”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T02, M03-T09.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M05-T04`

- **Goal:** 交付“严格日期、文本、整数和精确数值转换”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “严格日期、文本、整数和精确数值转换”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M05-T05`

- **Goal:** 交付“`GenericDatasetAdapter`、重复键和指纹键”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`GenericDatasetAdapter`、重复键和指纹键”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T02, M05-T03, M05-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 `Task M05-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M06-T01`

- **Goal:** 交付“白名单 SQL 标识符和 Upsert 模板”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “白名单 SQL 标识符和 Upsert 模板”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T03, M04-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M06-T02`

- **Goal:** 交付“复合键与指纹键编码/绑定”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “复合键与指纹键编码/绑定”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M06-T03`

- **Goal:** 交付“已有键预查、数据集锁和插入/更新计数”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “已有键预查、数据集锁和插入/更新计数”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M06-T01, M06-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M06-T04`

- **Goal:** 交付“单事务批量 Upsert 与回滚”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “单事务批量 Upsert 与回滚”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M06-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M06-T05`

- **Goal:** 交付“查询条件白名单和 COUNT/分页 SQL”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “查询条件白名单和 COUNT/分页 SQL”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T03, M04-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M06-T06`

- **Goal:** 交付“`DatasetQueryService`、页码归一化和精度序列化”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`DatasetQueryService`、页码归一化和精度序列化”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M06-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T06` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 `Task M06-T06` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M07-T01`

- **Goal:** 交付“配置属性和同步 `RestClient`”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “配置属性和同步 `RestClient`”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M07-T02`

- **Goal:** 交付“Tushare 请求、响应 DTO 和严格返回校验”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Tushare 请求、响应 DTO 和严格返回校验”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M03-T09, M07-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M07-T03`

- **Goal:** 交付“鉴权、权限、限流、网络、超时和格式错误分类”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “鉴权、权限、限流、网络、超时和格式错误分类”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M07-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M07-T04`

- **Goal:** 交付“`TushareProPlugin` 描述符、readiness 和 49 接口下载”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`TushareProPlugin` 描述符、readiness 和 49 接口下载”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M07-T02, M07-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 `Task M07-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M08-T01`

- **Goal:** 交付“fixture 元数据、插件和适配器”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “fixture 元数据、插件和适配器”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M02-T05, M04-T06.
- **Sources:** `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M08-T02`

- **Goal:** 交付“成功、空、上游失败、适配失败和写入失败模式”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “成功、空、上游失败、适配失败和写入失败模式”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M08-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M08-T03`

- **Goal:** 交付“fixture 注册→适配→入库→查询集成测试”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “fixture 注册→适配→入库→查询集成测试”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T05, M06-T06, M08-T02.
- **Sources:** `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md` 的 `Task M08-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M09-T01`

- **Goal:** 交付“Boot 入口、请求标识和通用 API DTO”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Boot 入口、请求标识和通用 API DTO”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M01-T03, M02-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M09-T02`

- **Goal:** 交付“数据源、接口和数据集元数据 API”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据源、接口和数据集元数据 API”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T02, M09-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M09-T03`

- **Goal:** 交付“同步下载 API 与事务提交后结果”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “同步下载 API 与事务提交后结果”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M05-T03, M05-T05, M06-T04, M07-T04, M09-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M09-T04`

- **Goal:** 交付“数据集定义与只读分页查询 API”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据集定义与只读分页查询 API”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M06-T06, M09-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M09-T05`

- **Goal:** 交付“全局异常和 HTTP 状态映射”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “全局异常和 HTTP 状态映射”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M09-T02, M09-T03, M09-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T05` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T05` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M09-T06`

- **Goal:** 交付“配置、脱敏、指标、健康和静态资源安全”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “配置、脱敏、指标、健康和静态资源安全”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M09-T01, M09-T02, M09-T03, M09-T04, M09-T05.
- **Sources:** `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T06` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 `Task M09-T06` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M10-T01`

- **Goal:** 交付“Vue 依赖、Vitest、VTU 和 Playwright 配置”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Vue 依赖、Vitest、VTU 和 Playwright 配置”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M10-T02`

- **Goal:** 交付“`/downloads`、`/datasets` 路由和桌面布局”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`/downloads`、`/datasets` 路由和桌面布局”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M10-T03`

- **Goal:** 交付“Axios 客户端、DTO 和错误拦截”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “Axios 客户端、DTO 和错误拦截”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M00-T03, M10-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T03` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T03` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M10-T04`

- **Goal:** 交付“日期、空值、精度格式化和无障碍状态组件”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “日期、空值、精度格式化和无障碍状态组件”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T01.
- **Sources:** `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T04` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 `Task M10-T04` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M11-T01`

- **Goal:** 交付“数据源与接口分组搜索选择组件”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “数据源与接口分组搜索选择组件”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T03.
- **Sources:** `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T01` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T01` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

### `M11-T02`

- **Goal:** 交付“元数据驱动动态参数表单”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 中该任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “元数据驱动动态参数表单”已按该任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。
- **Dependencies:** M10-T03, M10-T04.
- **Sources:** `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T02` 任务卡。
- **First action:** 读取 `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T02` 任务卡，并确认其 `Context boundary`、输入和目标文件均可定位。
- **State evidence:** None.

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
