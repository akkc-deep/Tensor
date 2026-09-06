# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** docs/task-handoffs/ISSUE-004/ISSUE-004-task-board.md
- **Completed task:** ISSUE-004-T04
- **Next task:** ISSUE-004-T05
- **Design document:** docs/task-designs/ISSUE-004-T05-design.md
- **Expected next status:** READY

## Next Task

ISSUE-004-T05：查看工作台与精确表格展示

- **Goal:** 查看页形成清晰筛选 / 结果布局，宽表格保留全部字段和精确值。
- **Scope:** 查看组件重排、独立日期布局、数字对齐 / 行情符号、限定的 daily / weekly 展示映射、固定列 / tooltip / 分页主题；复用公共选择、面板与反馈，本任务发现的同职责重复部分同步抽取并替换相关调用处；不改变查询状态机或筛选契约。
- **Acceptance:** 交易日 / 公告日 / 单边日期及无筛选数据集可用；查询、重置、分页、每页条数及重试不变；152 业务列 + 3 来源列不丢失；数字字符串不丢精度；日 / 周单位正确；宽表格内部滚动；跨页同职责重复 UI 已共用组件，两页行为回归通过。

## Dependencies

### ISSUE-004-T02

- **Artifact:** control-plane/src/layouts/AppLayout.vue、src/components/common/PageHeading.vue、src/composables/useTheme.js、src/style.css（src均相对control-plane）
- **Decision:** 三入口、具名业务页缓存、根主题变量；设置只有前端状态
- **Rationale:** 切页保留业务实例，配色与查询状态独立
- **Constraint:** 不改变KeepAlive名称/key/生命周期，不因布局或换色重发请求
- **Usage:** 查看页继续使用PageHeading、原缓存实例和主题变量
- **Readiness evidence:** 看板COMPLETED；7f577e4；本轮T04调用方20项、全套164项继续通过

### ISSUE-004-T04

- **Artifact:** control-plane/src/components/common/WorkbenchPanel.vue、AsyncStatePanel.vue；control-plane/src/views/DownloadView.vue、DatasetView.vue；control-plane/src/style.css
- **Decision:** 面板为headingId/title/meta/default slot；状态组件统一requestId/retryLabel/retry且调用方决定资格
- **Rationale:** 统一下载/查看/设置的同职责呈现，不合并业务状态机
- **Constraint:** 保留公共alert/status/live语义及原重试资格；下载表单/单次请求仍不变
- **Usage:** 查看页接入双面板，保留已接入的共享错误；共享选择器同时替换下载调用处
- **Readiness evidence:** 看板COMPLETED；c2d4d52、f7363d1；46项针对性、20项调用方、164项全套、构建/diff检查通过，独立复审通过

## Start Here

1. 完整读取 docs/task-designs/ISSUE-004-T05-design.md。
2. 读取上述直接依赖实现与看板验收证据。
3. 读取 docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md 的 ISSUE-004-T05 步骤。
4. 按专属设计 Tests 编写新增行为测试或运行既有覆盖，随后实施该任务。

## Risks

保留现有 API / 日期 / 精确数字契约；浏览器最终组合验收由 T06 完成。依赖决定与约束一致，无未解决冲突。
