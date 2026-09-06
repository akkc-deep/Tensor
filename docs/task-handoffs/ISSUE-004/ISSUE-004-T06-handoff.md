# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** docs/task-handoffs/ISSUE-004/ISSUE-004-task-board.md
- **Completed task:** ISSUE-004-T05
- **Next task:** ISSUE-004-T06
- **Design document:** docs/task-designs/ISSUE-004-T06-design.md
- **Expected next status:** READY

## Next Task

ISSUE-004-T06：正式前端回归与视觉验收

- **Goal:** 用正式 Vue 构建证明视觉方案、跨页行为、全部 UI 元数据与客户端契约均已满足。
- **Scope:** 独立 Playwright UI 配置、49 项元数据 UI 覆盖、主题 / 状态 / 日期 / 查询浏览器场景、多视口视觉检查、组件与逻辑复用审查、证据和 ISSUE 收尾；不执行真实 Tushare 下载或扩大为后端验收。
- **Acceptance:** 全部前端单测、构建和指定 UI spec 通过；设置零请求、切页状态保留、原日期参数与一次请求、精确宽表、五种视口及键盘 / 对比度检查有证据；结果写入 `docs/verification/ISSUE-004-ui-redesign.md`，包含共享实现、实际使用位置及调用处回归结果，截图可追踪；全部关闭条件满足后才关闭 ISSUE。

## Dependencies

### ISSUE-004-T04

- **Artifact:** control-plane/src/views/DownloadView.vue、SettingsView.vue；control-plane/src/components/common/WorkbenchPanel.vue、AsyncStatePanel.vue；control-plane/src/style.css
- **Decision:** 下载配置/结果双面板，所有真实状态、原计数和原重试；共享面板/状态
- **Rationale:** 正式下载可见反馈并保留业务契约
- **Constraint:** 单次有效下载只发一次原参数请求，canRetry由原流程控制，设置与换色不触发API
- **Usage:** 浏览器验收六类状态、单日期/原生起止、共享反馈、设置/缓存/主题
- **Readiness evidence:** 看板COMPLETED；c2d4d52、f7363d1；164项全套和构建通过，独立复审通过；T05全套170项继续通过

### ISSUE-004-T05

- **Artifact:** control-plane/src/views/DatasetView.vue；control-plane/src/components/common/CatalogSelect.vue；control-plane/src/components/dataset/DatasetTable.vue、DatasetPagination.vue；control-plane/src/utils/format.js
- **Decision:** 上筛选/下结果面板、共享选择器和单一分页；保留全部字段/原列顺序与精确字符串，daily百分数/weekly比率不换算
- **Rationale:** 完整可用的宽表和一致交互，避免丢精度或改查询快照
- **Constraint:** API/查询状态机/日期/生产YAML/依赖只读；20/50/100、默认50；范围可空及单边，原参数重试不变
- **Usage:** 浏览器验证49项元数据、155列表格、真实tooltip/固定列、分页/缓存、五视口与主题
- **Readiness evidence:** 看板COMPLETED；65fad74、a2d2de3；针对性71项、公共调用方36项、全套170项和构建通过，移动端padding修正后独立复审通过

## Start Here

1. 完整读取 docs/task-designs/ISSUE-004-T06-design.md。
2. 读取上述直接依赖实现与看板验收证据。
3. 读取 docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md 的 ISSUE-004-T06 步骤。
4. 按专属设计 Tests 编写新增行为测试或运行既有覆盖，随后实施该任务。

## Risks

保留现有 API / 日期 / 精确数字契约；浏览器最终组合验收由 T06 完成。依赖决定与约束一致，无未解决冲突。
