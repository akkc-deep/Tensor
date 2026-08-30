# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M00-T03`
- **Next task:** `M00-T04`
- **Design document:** `docs/task-designs/M00-T04-designs.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M00-T04`
- **Title:** Tensor 任务设计与验收证据模板
- **Goal:** 交付“Tensor 任务设计与验收证据模板”。
- **Scope:** 创建 `docs/superpowers/task-templates/task-design.md` 和 `docs/superpowers/task-templates/acceptance-evidence.md` 及其直接测试与验证；不记录或实现当前状态、权限、事件、交接、归档或恢复能力，不修改生产代码、需求基线、既有契约或其他预定义任务交付物。
- **Acceptance:** 两份模板与 `docs/task-designs/M00-T04-designs.md` 中分别标记的完整正文逐字一致；任务设计模板恰含五个稳定二级标题和任务卡链接元数据，验收证据模板包含需求、变更文件、命令、时间、退出码、计数、有限摘要和敏感信息扫描字段；设计列出的全部命令得到注明的预期结果且没有混入排除范围。

## Dependencies

### `M00-T01`

- **Artifact:** `docs/traceability/tensor-v1-requirements.md`
- **Decision:** 后续验收证据使用稳定的 BRD、PRD、Acceptance 和非功能要求标识；`Acceptance` 区分直接、部分覆盖和 PRD 内联验收，`Evidence` 只表示计划证据责任。
- **Rationale:** 证据模板必须把每条命令和变更文件关联到稳定需求/验收标识，同时避免把计划责任误写成已经执行或通过的结果。
- **Constraint:** 模板只能引用已建立的标识和映射语义，不得把 `Evidence` 列当作验收结论，不得增加运行时看板状态、执行权限、事件、交接、归档或恢复字段。
- **Usage:** 以追踪索引中的稳定标识语义定义 `requirement ID`、`acceptance criterion` 和 `evidence references` 字段及其填写提示。
- **Readiness evidence:** M00-T01 在权威看板中为 `COMPLETED`；结构、映射、标识、占位符、输入不变性和链接验证均退出码 0，独立审查结论为 `Ready to merge: Yes`。

## Start Here

1. `docs/task-designs/M00-T04-designs.md` 全文。
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M00-T04 行与任务详情。
3. `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 M00-T04 任务卡。
4. `docs/task-designs/README.md`。
5. `docs/traceability/tensor-v1-requirements.md`。
6. `docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md` 的 1、7 节。
7. `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md` 的 3、9 节。
8. **First action:** 按设计“如何测试”先运行两份完整正文的逐字同步门禁，在目标文件尚不存在时记录预期的非零退出结果；随后从设计的两个 `BEGIN`/`END` 区块提取已冻结正文，一次性落盘两份目标模板。

## Risks

- 项目基线文档记录的“无 Git 元数据”在 M00-T03 最终验证期间已发生变化；实施 M00-T04 时必须重新检测 Git 和工作树状态，只提交本任务仍未提交的目标变更，不重写或压平现有历史。
- 两份目标文件本身是可复用模板，尖括号槽位是设计规定的输入位置；实施必须保持完整正文逐字一致，使用模板时才替换槽位，不能把槽位误判为设计缺口或在 M00-T04 实施中提前填值。
