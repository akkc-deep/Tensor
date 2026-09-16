# 任务看板与交接文档

按项目归类：正式任务统一放在 `tensor-v1/`，每个后续 issue 使用独立的 `ISSUE-编号/` 目录。文件名保留原任务编号。

| 目录 | 内容 | 任务看板 |
| --- | --- | --- |
| [tensor-v1/](tensor-v1/) | Tensor v1 正式任务：1 份看板、78 份任务交接 | [Tensor v1 看板](tensor-v1/tensor-v1-task-board.md) |
| 当前目录 | Studio 前端迁移：10 个中等偏小任务 | [Studio 前端看板](studio-frontend-task-board.md) |
| 当前目录 | 数据完整性检验：13 项任务，按本地检查设计实施 | [数据完整性看板](data-integrity-task-board.md) |
| [ISSUE-004/](ISSUE-004/) | UI 改版任务；当前已有看板，后续交接放在同一目录 | [ISSUE-004 看板](ISSUE-004/ISSUE-004-task-board.md) |
| [ISSUE-018/](ISSUE-018/) | 日期区间批量下载：T01–T14均已完成；本次30个RANGE/251项通过，四接口27项明确排除，SINGLE74项保留；032六门禁及终审通过，母任务已收尾 | [ISSUE-018 看板](ISSUE-018/ISSUE-018-task-board.md) · [T14交接](ISSUE-018/ISSUE-018-T14-handoff.md) |
| [ISSUE-026/](ISSUE-026/) | 原六项ISSUE-027～032均COMPLETED；251项通过、四接口27项明确排除且问题保留；033本次跳过，保持NOT_STARTED | [子issue看板](ISSUE-026/ISSUE-026-task-board.md) · [历史交接](ISSUE-031-handoff.md) · [最终验收](../verification/ISSUE-032-range-final-closure.md) · [总任务原交接](ISSUE-026-handoff.md) |

ISSUE-018 的后续缺口按用户要求另由 [ISSUE-019～026 看板](ISSUE-018/ISSUE-018-followups-task-board.md)串行管理；与 T01～T14 身份分开，不替代原看板或已有验收状态。ISSUE-026的六子issue另由其子看板管理，母任务仍在019～026看板；不重复登记同一任务的权威状态。

```text
task-handoffs/
├── README.md
├── studio-frontend-task-board.md
├── data-integrity-task-board.md
├── tensor-v1/
│   ├── tensor-v1-task-board.md
│   └── Mxx-Txx-handoff.md
├── ISSUE-004/
│   └── ISSUE-004-task-board.md
├── ISSUE-018/
│   └── ISSUE-018-task-board.md
└── ISSUE-026/
    └── ISSUE-026-task-board.md
```

新增文档沿用以下位置：

- 正式任务交接：`docs/task-handoffs/tensor-v1/<任务编号>-handoff.md`。
- Issue 看板：`docs/task-handoffs/<ISSUE-编号>/<ISSUE-编号>-task-board.md`。
- Issue 任务交接：`docs/task-handoffs/<ISSUE-编号>/<任务编号>-handoff.md`，例如 `ISSUE-004/ISSUE-004-T01-handoff.md`。

各项目看板仍是任务身份、顺序、依赖、状态与交接路径的唯一权威。交接只是快照；目录整理不改变任务状态。设计文档继续放在 `docs/task-designs/`，不随交接文档移动。

未编号为 Issue 的独立项目看板使用 `docs/task-handoffs/<project-id>-task-board.md`，如 Studio 前端迁移。

迁移文档时同步更新引用。历史记录中的哈希对应当时的 Git 版本；本次引用更新不改变验收结论。
