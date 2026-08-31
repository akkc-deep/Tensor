# Tensor 重复任务索引正式退役设计

## 状态

- 项目所有者于 2026-08-31 正式决定停用并删除 `docs/planning/task-index.md`。
- 项目所有者随后批准方案 A：权威 task board 成为唯一活跃任务索引，历史迁移文档仅标记为已被取代。

## 目标

消除 task board 与静态任务索引之间重复维护任务身份、顺序和依赖的风险。完成后：

- `docs/task-handoffs/tensor-v1-task-board.md` 是任务身份、顺序、定义、依赖、状态、设计和交接的唯一权威来源；
- `docs/superpowers/plans/tensor-modules/` 继续保存每个任务的详细实施卡；
- 总路线图改为指向权威 task board；
- 已删除索引不再被任何活跃入口或任务 `Sources` 引用。

## 范围

删除：

- `docs/planning/task-index.md`。

修改：

- `docs/task-handoffs/tensor-v1-task-board.md`：在 Workflow 明确唯一权威职责，并从 77 个任务详情的 `Sources` 中移除静态索引，仅保留对应模块任务卡；不改变任务表、状态、依赖、设计、交接、验收或状态证据。
- `docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md`：第 5 节改为链接权威 task board；工时汇总和模块计划链接保持不变。
- `docs/superpowers/specs/2026-08-26-task-handoff-retirement-design.md`：顶部添加历史取代说明，不改写当时的方案、事实或验证记录。
- `docs/superpowers/plans/2026-08-26-task-handoff-retirement.md`：顶部添加历史取代说明，不重写当时实施步骤。

排除：

- 不删除或重写历史迁移设计/计划正文；其中对旧索引的提及作为历史事实保留，但不得再被解释为当前指令。
- 不修改任何任务状态、顺序、依赖、设计路径、交接路径、验收、状态证据或产品/技术实现。
- 不修改 BRD、PRD、TRD、M00 契约、任务设计或生产代码。
- 不创建替代静态索引；task board 直接承担唯一索引职责。

## 权威与历史边界

活跃读取顺序固定为：

1. 从 `docs/task-handoffs/tensor-v1-task-board.md` 获取当前任务身份、顺序、定义、依赖、状态、设计和交接路径；
2. 完整读取看板链接的任务设计与交接；
3. 读取任务详情 `Sources` 中唯一的模块任务卡，并按卡内来源继续核验。

2026-08-26 的退役设计和实施计划是历史记录。它们顶部必须明确：其中“保留 `docs/planning/task-index.md`”的决定已被 2026-08-31 项目所有者决定取代；正文仅用于解释过去发生的迁移，不再约束当前任务工作流。

## 数据保留

删除静态索引不会删除任务定义：

- 77 个任务的 ID、标题、顺序、依赖、目标、范围、验收、状态、设计与交接仍在 task board；
- 每个任务的文件、接口、步骤和门禁仍在 15 个模块任务计划；
- 15 个模块、77 个任务和 206 AI 小时的汇总仍在总路线图；
- Git 历史保留已删除索引，可在需要时审计或恢复。

## 实施顺序

1. 记录删除前 task board 中 77 行任务表及状态/依赖/设计/交接字段的校验摘要。
2. 删除 `docs/planning/task-index.md`。
3. 更新 task board 的 Workflow，并机械移除 77 个 `Sources` 中的索引来源。
4. 更新总路线图第 5 节。
5. 给两份 2026-08-26 历史文档添加取代说明。
6. 运行残留引用、结构、格式和 Git 范围检查。
7. 只提交本设计列出的一个删除和四个修改，提交消息为 `docs: retire duplicate task index`。

## 验证

必须满足：

- `test ! -e docs/planning/task-index.md` 退出 0；
- task board 的 Workflow 明确其唯一权威职责；
- task board 仍恰有 77 个任务表行和 77 个任务详情，任务表的 ID、Order、Title、Status、Dependencies、Design document、Handoff 与删除前一致；
- 77 个任务详情的 `Sources` 均只指向一个存在的模块任务卡，不再包含 `docs/planning/task-index.md`；
- 总路线图不再链接或声明使用静态索引，而是链接权威 task board；
- 两份历史文档顶部均含 2026-08-31 取代说明；历史正文中的旧路径可保留；
- 除本退役设计及其实施计划、带取代标记的历史正文和 task board 的既有状态证据外，活跃文档不存在已删除索引的引用；
- `git diff --check` 退出 0，Git 变更范围精确为一个删除和四个修改；
- 不存在任务状态转换，M02-T05 继续保持 `NOT_STARTED` 且 Handoff 为 `None`。

## 风险

最大的风险是机械清理 77 个 `Sources` 时误改同一行附近的任务事实。实施必须使用可审阅的精确替换，并在修改前后比较任务表的权威字段摘要。历史文档保留旧路径会使全仓库文本搜索仍有命中，因此验证必须区分“带取代标记的历史正文”与“活跃入口引用”，不能以零命中作为错误目标。
