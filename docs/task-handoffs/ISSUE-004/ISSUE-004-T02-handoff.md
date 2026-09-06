# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** docs/task-handoffs/ISSUE-004/ISSUE-004-task-board.md
- **Completed task:** ISSUE-004-T01
- **Next task:** ISSUE-004-T02
- **Design document:** docs/task-designs/ISSUE-004-T02-design.md
- **Expected next status:** READY

## Next Task

ISSUE-004-T02：侧栏、设置与业务状态保留

- **Goal:** 三入口导航与外观设置可用，往返业务页面保留状态且不重发请求。
- **Scope:** 侧栏、响应式应用壳、settings 路由与表单、KeepAlive、键盘导航和路由回归；复用或抽取同职责重复结构，不改下载与查询流程。
- **Acceptance:** 下载 / 查看 / 设置路由及 404 正常；取色器、HEX、实际应用色、存储提示和重置有效；表单、日期、结果、错误、分页及在途请求保留；设置无新 API 请求；本次涉及的同职责重复结构已复用；路由和设置测试通过。

## Dependencies

### ISSUE-004-T01

- **Artifact:** control-plane/src/composables/useTheme.js；control-plane/src/utils/theme.js；control-plane/src/style.css
- **Decision:** THEME_KEY / useTheme 共享每 App 的只读 refs 与 apply/reset；根 --tensor-* 及 EP 映射
- **Rationale:** 整页同一主题且设置无需 API
- **Constraint:** 只存输入 HEX；非法不变化；存储失败 preview-only；业务状态不入主题
- **Usage:** SettingsView 调用 useTheme，布局与标题消费语义 CSS
- **Readiness evidence:** ba043c9；29 针对性/148 全套测试及构建通过，独立审查通过

## Start Here

1. 完整读取 docs/task-designs/ISSUE-004-T02-design.md。
2. 读取上述直接依赖实现与看板验收证据。
3. 读取 docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md 的 ISSUE-004-T02 步骤。
4. 按专属设计 Tests 编写新增行为测试或运行既有覆盖，随后实施该任务。

## Risks

保留现有 API / 日期 / 精确数字契约；浏览器最终组合验收由 T06 完成。依赖决定与约束一致，无未解决冲突。
