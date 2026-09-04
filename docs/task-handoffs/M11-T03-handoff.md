# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M11-T02`
- **Next task:** `M11-T03`
- **Design document:** `docs/task-designs/M11-T03-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。

## Next Task

- **ID:** `M11-T03`
- **Title:** 下载 composable、控件锁定和请求世代
- **Goal:** 交付下载页唯一的内存态异步流程 composable，统一管理元数据、选择、最终结果、安全错误、提交锁定、操作感知重试和请求世代。
- **Scope:** 只创建 `control-plane/src/composables/useDownloadFlow.js` 和 `control-plane/src/composables/useDownloadFlow.spec.js`；不修改 M10 API、既有组件/composable、依赖、配置、路由、布局、页面、样式或后端合同，不实现表单校验、结果渲染、进度、取消、自动重试或持久化。
- **Acceptance criteria:** `useDownloadFlow()` 的唯一导出、七态、公开 refs/computed/actions、元数据与选择清理、提交锁定、重复提交防护、单调 generation、原样成功/安全失败和 `SOURCES | APIS | DOWNLOAD` 操作感知重试均与设计一致；Node.js 24.15.0 下严格 RED 原因正确，聚焦 8/8、完整前端 61/61 和 Vite 构建通过，且实现提交精确只含设计规定的两个文件。

## Dependencies

### `M10-T03`

- **Artifact:** `control-plane/src/api/dataSources.js` 的 `listDataSources()`/`listApis(pluginId)`、`control-plane/src/api/downloads.js` 的 `downloadDataset(request)`，以及 `control-plane/src/api/errors.js` 的安全 `ApiError`/`ClientError` 边界；完整合同见 `docs/task-designs/M10-T03-design.md`。
- **Decision:** 三个业务函数直接返回未改写的 OpenAPI 成功 DTO；所有失败在唯一 Axios 边界归一化为具有 `retryable` 的安全错误对象，不自动重试、不暴露原始 Axios 数据。
- **Rationale:** 页面流程可在稳定 API 边界之上只负责响应式编排、锁定、竞态和重试，无需重复路径、DTO 或错误解释逻辑。
- **Constraint:** M11-T03 必须直接调用这三个函数，原样保存成功响应和安全错误；不得修改 M10 模块、包装错误、复制原始请求/响应详情、发送凭证或把 API 层职责移入 composable。
- **Usage:** `load()` 消费来源列表，`selectSource(pluginId)` 消费对应 API 列表，`submit(params)` 消费最终下载响应；generation 只决定异步结果是否仍可落地，`retry()` 只重做最近一次可重试的失败操作。
- **Readiness evidence:** 权威看板记录 M10-T03 为 `COMPLETED`；其最终修复提交为 `890ed88`，当前 `dataSources.js`、`downloads.js`、`errors.js`、`http.js` 和 `api.spec.js` 相对该提交无差异。M10-T03 完成证据记录 Node 24.15.0 下聚焦 12/12、当时全量 19/19、生产构建及最终独立复审通过；2026-09-04 交接复核时，当前前端基线为 10 files / 53 tests 全通过。

该唯一直接依赖无冲突：M10-T03 提供未改写的成功 DTO 和已归一化的安全失败；M11-T03 只在其上增加页面内存态编排、操作感知重试、提交锁定和 generation，不改变 API 边界。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M11-T03-design.md`
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 `M11-T03` 行与详情
3. `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T03`
4. `control-plane/src/api/dataSources.js`、`control-plane/src/api/downloads.js`、`control-plane/src/api/errors.js` 与 `docs/task-designs/M10-T03-design.md`

首个实施动作：确认工作树为空并使用 Node.js 24.15.0 复验 10 files / 53 tests 基线；随后只完整创建 `control-plane/src/composables/useDownloadFlow.spec.js`，在生产 composable 不存在时运行聚焦命令，取得仅因 `./useDownloadFlow.js` 缺失而发生的严格 RED。

## Risks

- generation 只忽略 stale 结果，不取消网络请求；M10 的超时继续负责最终释放请求。
- 下载重试只保存字符串参数字典的浅复制快照；修改后的表单值必须经 M11-T05 重新校验后调用 `submit(newParams)`，不能由无参 `retry()` 读取。
- `error.retryable` 只控制原操作重试，不阻止用户修正选择或参数后发起新操作。
- `locked` 只表示下载提交锁；元数据加载期间以 `METADATA_LOADING` 和 `canSubmit === false` 阻止提交，同时允许来源快速切换并由 generation 抑制 stale 结果。
