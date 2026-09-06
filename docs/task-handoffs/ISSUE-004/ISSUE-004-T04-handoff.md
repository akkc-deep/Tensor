# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** docs/task-handoffs/ISSUE-004/ISSUE-004-task-board.md
- **Completed task:** ISSUE-004-T03
- **Next task:** ISSUE-004-T04
- **Design document:** docs/task-designs/ISSUE-004-T04-design.md
- **Expected next status:** READY

## Next Task

ISSUE-004-T04：下载工作台布局与反馈。

- **Goal:** 下载页按左配置/右结果布局呈现真实业务反馈。
- **Scope:** 既有下载组件重排、双面板、统一 AsyncStatePanel、加载与失败呈现、响应式样式；抽取同职责重复面板与反馈组件，消费 T03 表单并复用既有下载流程。
- **Acceptance:** 元数据加载、待操作、提交、成功、空、失败均可见；三项真实计数及适用重试正常；长参数/错误可读；桌面双栏、窄屏纵向；重复面板与反馈实际复用；下载与公共调用方回归通过。

## Dependencies

### ISSUE-004-T02

- **Artifact:** control-plane/src/layouts/AppLayout.vue、src/components/common/PageHeading.vue、src/views/SettingsView.vue、src/style.css（均在 control-plane 下）。
- **Decision:** 三入口应用壳、具名业务页缓存、共享根主题；设置只有前端状态。
- **Rationale:** 往返保留表单与在途操作，主题与业务独立。
- **Constraint:** 不改业务缓存生命周期，不因重排或主题重新请求。
- **Usage:** 保留 PageHeading，设置外层和下载双面板接入 WorkbenchPanel，延用根主题。
- **Readiness evidence:** 看板 T02 COMPLETED；7f577e4；18项针对性/157项全套及构建通过，独立审查通过；本次全套158项继续通过。

### ISSUE-004-T03

- **Artifact:** control-plane/src/components/common/MetadataField.vue、control-plane/src/composables/useFormValidation.js、control-plane/src/components/download/DynamicParameterForm.vue、control-plane/src/views/DownloadView.spec.js。
- **Decision:** 元数据决定独立日期控件；字段/ARIA/焦点共享，原业务校验和归一化保留。
- **Rationale:** 下载与查看呈现一致且不改变日期能力。
- **Constraint:** 下载表单公开 validate/normalizedValues/reset 保持；每次提交一次原请求，不拆分日期。
- **Usage:** 下载配置面板继续使用原 DynamicParameterForm 和提交函数；保留日线/新股请求回归。
- **Readiness evidence:** 看板 T03 COMPLETED；b4e486b、decb5c7；58项针对性、158项全套与构建通过，审查修正后14项通过，独立复审通过；62项保护源散列未变。

## Start Here

1. 完整读取 docs/task-designs/ISSUE-004-T04-design.md。
2. 读取上述直接依赖实现及看板证据。
3. 对照 docs/issues/proposals/ISSUE-004-ui-visual-concepts.html 的下载布局。
4. 先将 DownloadView 提交态测试改为结果区内可见“正在下载”并确认失败，再按专属设计实施。

## Risks

共享状态/错误组件必须保留各调用方原重试资格及 live region；元数据无 description 字段，不补造说明。两个依赖的决定与约束一致，无未解决冲突。
