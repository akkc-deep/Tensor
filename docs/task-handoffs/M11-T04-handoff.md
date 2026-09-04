# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M11-T03`
- **Next task:** `M11-T04`
- **Design document:** `docs/task-designs/M11-T04-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。

## Next Task

- **ID:** `M11-T04`
- **Title:** 成功、空和失败结果组件
- **Goal:** 交付受控下载按钮和只呈现最终结果的可访问组件，使 M11-T05 可直接组合提交入口、成功计数、合法空结果、安全失败与原参数重试。
- **Scope:** 只创建 `control-plane/src/components/download/DownloadAction.vue`、`DownloadResult.vue` 和 `DownloadResult.spec.js`；不修改既有 API、composable、共享/下载组件、依赖、配置、路由、页面、样式或后端合同，不实现请求、参数校验、错误解释、自动重试、取消、阶段、进度、历史或持久化。
- **Acceptance criteria:** `DownloadAction(disabled, submitting)` 只在未锁定时发出 `submit`；`DownloadResult(state, result, error, canRetry)` 只呈现最终三态并发出 `retry`。SUCCESS 礼貌播报三个本次计数，EMPTY 精确显示“下载成功，0 条数据”，FAILURE 使用 alert 展示 M10 安全摘要、可用请求 ID 和受 `canRetry` 控制的“使用原参数重试”；Node.js 24.15.0 下严格 RED 原因正确，聚焦 6/6、完整前端 67/67、生产构建和精确三文件范围均达到设计结果。

## Dependencies

### `M10-T04`

- **Artifact:** `control-plane/src/components/common/AsyncStatePanel.vue` 及其行为测试 `AsyncStatePanel.spec.js`；完整合同见 `docs/task-designs/M10-T04-design.md`。
- **Decision:** 通用面板的 `EMPTY` 使用 `role="status"`/`aria-live="polite"`，`FAILURE` 使用 `role="alert"`，可选 `actions` 插槽由调用方放置业务动作；组件刻意不提供 SUCCESS 业务结果。
- **Rationale:** 空结果和失败可复用统一无障碍语义，成功下载的业务计数则由 M11-T04 自己以礼貌 live region 呈现，避免把业务结果反向塞入通用组件。
- **Constraint:** M11-T04 必须复用既有面板呈现 EMPTY/FAILURE，不修改共享组件、不预创建隐藏 alert、不使用 `v-html` 或 Element Plus 内部 DOM；SUCCESS 必须自行提供 `role="status"` 和 `aria-live="polite"`。
- **Usage:** `DownloadResult` 把固定空结果文案或 `error.message` 传给面板，并通过 actions 插槽放置非空请求 ID 与受控重试按钮；成功分支直接呈现三个响应计数。
- **Readiness evidence:** 权威看板记录 M10-T04 为 `COMPLETED`；其初始实现提交为 `0a61e3f`，严格时间戳修复提交为 `0818fbc`，完成证据记录聚焦 15/15、当时全量 34/34、双宿主时区断言、生产构建和最终独立复审通过。当前 `AsyncStatePanel.vue`/spec 相对 `0818fbc` 无差异。

### `M11-T03`

- **Artifact:** `control-plane/src/composables/useDownloadFlow.js` 暴露的 `state`、`result`、`error`、`locked`、`canSubmit`、`canRetry`、`submit(params)` 和 `retry()`，以及 `useDownloadFlow.spec.js`；完整合同见 `docs/task-designs/M11-T03-design.md`。
- **Decision:** 流程以 `SUCCESS | EMPTY | FAILURE` 表达最终结果，原样保留 OpenAPI `DownloadResponse` 或 M10 安全错误；`locked` 只表示下载提交锁，`canRetry` 只在最近失败操作可重试时成立，无参 `retry()` 使用内部冻结上下文重做原操作。
- **Rationale:** 结果组件只负责展示和发出意图，无需复制异步状态、错误码、retryable 判断或失败参数，从而维持唯一流程状态源。
- **Constraint:** M11-T04 不得修改或包装响应/错误，不得读取 `error.retryable`、code/kind/fieldErrors 或当前表单，不得自行发请求或重试；按钮锁定和重试可见性必须完全服从调用方传入的 `disabled`、`submitting` 与 `canRetry`。
- **Usage:** M11-T05 将 `!canSubmit`/`locked` 传给 `DownloadAction`，把最终 `state`/`result`/`error`/`canRetry` 传给 `DownloadResult`，并把两个组件事件连接到校验后的 `submit(params)` 与无参 `retry()`。
- **Readiness evidence:** 权威看板记录 M11-T03 为 `COMPLETED`；实现提交 `e893d0f` 精确新增 composable 与 8 项测试，完成记录提交为 `90bffd7`。当前两个实现文件相对 `e893d0f` 无差异；完成证据及 2026-09-04 交接复核均确认 Node 24.15.0 下聚焦 8/8、当前全量 61/61 和生产构建通过。

两项直接依赖无冲突：M10-T04 提供无业务含义的 EMPTY/FAILURE 可访问容器，M11-T03 提供唯一最终状态、响应、安全错误和重试判定；M11-T04 只在二者之间增加受控按钮和最终结果投影，不改变任一合同。项目所有者已于 2026-09-04 批准设计中的最小 props/emits、固定按钮文案和三态展示边界。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M11-T04-design.md`
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 `M11-T04` 行与详情
3. `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T04`
4. `docs/task-designs/M10-T04-design.md` 与 `control-plane/src/components/common/AsyncStatePanel.vue`
5. `docs/task-designs/M11-T03-design.md` 与 `control-plane/src/composables/useDownloadFlow.js`
6. `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的 5.6、5.7、7.2 和 9 节

首个实施动作：确认工作树为空并使用 Node.js 24.15.0 复验 11 files / 61 tests 基线；随后只完整创建 `control-plane/src/components/download/DownloadResult.spec.js`，在两个生产组件均不存在时运行聚焦命令，取得仅因 `DownloadAction.vue` 或 `DownloadResult.vue` 缺失而发生的严格 RED。

## Risks

- M11-T05 必须只在最终三态挂载 `DownloadResult`，并保证 SUCCESS 有非空 result、FAILURE 有非空 error；M11-T04 不复制第二套运行时合同校验。
- FAILURE 面板挂载即形成 alert；不得把隐藏的失败组件预创建为状态缓存。
- Element Plus loading 的内部 DOM 不稳定；测试只依赖公开 props、原生按钮语义、可见文本与组件事件。
- 浏览器侧 `ClientError.requestId` 可能为 null；缺失时省略请求 ID 行，不生成占位值。
