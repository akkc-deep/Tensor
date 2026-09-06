# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** docs/task-handoffs/ISSUE-004/ISSUE-004-task-board.md
- **Completed task:** ISSUE-004-T02
- **Next task:** ISSUE-004-T03
- **Design document:** docs/task-designs/ISSUE-004-T03-design.md
- **Expected next status:** READY

## Next Task

ISSUE-004-T03：日期控件与原参数契约

- **Goal:** 日期控件按现有下载参数和查看筛选元数据呈现，改版后原契约保持。
- **Scope:** 原日期控件的标签 / 宽度 / 错误 / 窄屏呈现及必要的参数回归；抽取两个表单中同职责重复的字段组件与输入属性处理逻辑；保留原校验、格式、请求流程和重试，不增加日期字段或批次能力。
- **Acceptance:** DATE 保持一个日期控件，原生 DATE_RANGE_MEMBER 保持独立起止控件；原必填 / 格式 / 顺序校验与查看可空 / 单边筛选有效；每次有效下载提交只发一次原契约请求；进入设置再返回保留控件值；同职责重复呈现和逻辑已实际共用，标签、错误关联与聚焦无回归。

## Dependencies

### ISSUE-004-T02

- **Artifact:** control-plane/src/layouts/AppLayout.vue；control-plane/src/views/DownloadView.vue；control-plane/src/views/DatasetView.vue；control-plane/src/components/common/PageHeading.vue；control-plane/src/style.css
- **Decision:** DownloadView/DatasetView 具名缓存、max=2、route.name key；共享 PageHeading 与根主题
- **Rationale:** 表单本地状态及在途请求在设置往返时保持
- **Constraint:** 不得在激活或主题变更时刷新元数据/重发；不改变表单业务 composable 与日期格式
- **Usage:** 在两个缓存业务页接入共享 MetadataField 与 UI 聚焦；沿用 T02 AppLayout 集成测试
- **Readiness evidence:** 7f577e4；针对性18/全套157项与构建通过；独立规格/质量审查通过

## Start Here

1. 完整读取 docs/task-designs/ISSUE-004-T03-design.md。
2. 读取上述直接依赖实现与看板验收证据。
3. 读取 docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md 的 ISSUE-004-T03 步骤。
4. 按专属设计 Tests 编写新增行为测试或运行既有覆盖，随后实施该任务。

## Risks

保留现有 API / 日期 / 精确数字契约；浏览器最终组合验收由 T06 完成。依赖决定与约束一致，无未解决冲突。
