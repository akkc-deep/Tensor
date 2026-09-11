# 任务看板与交接文档

按项目归类：正式任务统一放在 `tensor-v1/`，每个后续 issue 使用独立的 `ISSUE-编号/` 目录。文件名保留原任务编号。

| 目录 | 内容 | 任务看板 |
| --- | --- | --- |
| [tensor-v1/](tensor-v1/) | Tensor v1 正式任务：1 份看板、78 份任务交接 | [Tensor v1 看板](tensor-v1/tensor-v1-task-board.md) |
| [ISSUE-004/](ISSUE-004/) | UI 改版任务；当前已有看板，后续交接放在同一目录 | [ISSUE-004 看板](ISSUE-004/ISSUE-004-task-board.md) |
| [ISSUE-018/](ISSUE-018/) | 日期区间批量下载：T01–T09 已完成，T10 设计与交接就绪、状态 READY | [ISSUE-018 看板](ISSUE-018/ISSUE-018-task-board.md) |

```text
task-handoffs/
├── README.md
├── tensor-v1/
│   ├── tensor-v1-task-board.md
│   └── Mxx-Txx-handoff.md
├── ISSUE-004/
│   └── ISSUE-004-task-board.md
└── ISSUE-018/
    └── ISSUE-018-task-board.md
```

新增文档沿用以下位置：

- 正式任务交接：`docs/task-handoffs/tensor-v1/<任务编号>-handoff.md`。
- Issue 看板：`docs/task-handoffs/<ISSUE-编号>/<ISSUE-编号>-task-board.md`。
- Issue 任务交接：`docs/task-handoffs/<ISSUE-编号>/<任务编号>-handoff.md`，例如 `ISSUE-004/ISSUE-004-T01-handoff.md`。

各项目看板仍是任务身份、顺序、依赖、状态与交接路径的唯一权威。交接只是快照；目录整理不改变任务状态。设计文档继续放在 `docs/task-designs/`，不随交接文档移动。

迁移文档时同步更新引用，根目录仅保留目录说明。历史记录中的哈希对应当时的 Git 版本；本次引用更新不改变验收结论。
