# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M00-T02`
- **Transition:** `READY -> BLOCKED`

## Current State

- **Complete:** 已读取权威看板中的 M00-T02 行与任务详情、原 `next-task` 交接、M00-T02 任务卡、M00-T01 追踪产物中的 12 个直接输入、TRD 5.3/8.1 及 `daily` 模板字段基线；看板的 `Design document` 为 `None`。
- **Partial:** 已确认目标文件、JSON Schema draft、十个必填根字段、标识符正则、四组枚举和 `daily` 的 11 列顺序，但尚未创建任务设计或实现产物。
- **Blocked:** 权威输入没有唯一规定 `businessKey` 如何同时表达字段列表与 `COMPOSITE|FINGERPRINT` 模式，也没有规定固定列应是根字段还是列级展示元数据；这些选择会改变后续 M02、M03 消费的公开契约。
- **Unverified:** `docs/contracts/dataset-definition.schema.json` 与 `docs/contracts/dataset-definition.example.yaml` 尚未创建，任务卡中的解析和契约检查尚未执行。

## Changed Files

- `docs/task-handoffs/M00-T02-handoff.md`：以本次 `pause` 快照替换原 `next-task` 快照，记录设计阻塞与恢复入口。
- `docs/task-handoffs/tensor-v1-task-board.md`：本交接写入后记录 `READY -> BLOCKED`、阻塞证据和最新交接路径。

## Verification

Not run；尚未创建任务设计或实现产物。

## Remaining Work

- 由项目所有者批准 `businessKey`、`filters`、固定列及参数/列展示元数据的精确 schema 形状。
- 创建 `docs/task-designs/M00-T02-designs.md`，并把该路径双向回填到任务卡与权威看板。
- 设计批准后恢复 M00-T02，按任务卡创建 schema 与完整 `daily` 示例。
- 运行 JSON/YAML 解析、schema 自校验、正反例校验、11 列顺序与任务卡 `jq` 门禁。

## Resume Task

- **Task ID:** `M00-T02`
- **Goal:** 交付“数据集元数据 JSON Schema 与示例”。

## Start Here

1. `docs/task-handoffs/tensor-v1-task-board.md` 的 `M00-T02` 行与任务详情。
2. `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 M00-T02 任务卡。
3. `docs/task-handoffs/M00-T02-handoff.md` 全文。
4. `docs/traceability/tensor-v1-requirements.md` 中 `PRD-F-002`、`PRD-F-004`、`PRD-F-007`、`PRD-F-008`、`PRD-F-015`、`PRD-F-016`、`PRD-F-019`、`PRD-F-020`、`PRD-F-024`、`PRD-F-025`、`PRD-F-027` 和 `PRD 10.4`。
5. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 5.3、8.1 与 8.2。
6. `docs/data-template/manifest.json` 的 `daily` 条目和 `docs/data-template/daily.json` 的 `fields`。
7. **First action:** 由项目所有者批准 M00-T02 的精确 schema 形状，使实现者无需推断键模式、筛选与固定列的表示位置。

## Blocker

- **Reason:** 任务卡要求键模式枚举和固定列，但 TRD 8.2 只给出裸 `businessKey`/`filters` 数组且没有固定列字段；现有授权输入不足以唯一确定可被后续模块长期消费的 JSON Schema 结构。
- **Resolution condition:** 项目所有者批准并可写入 `docs/task-designs/M00-T02-designs.md` 的精确字段结构，至少明确 `businessKey` 的模式与字段表示、`filters` 的元素形状、固定列的位置，以及参数/列展示元数据的必填字段。

## Risks

- 若直接选择一种结构，M02 的 Java 值对象、M03 的 49 份 YAML 和后续查询 API 可能围绕未经授权的契约展开，返工面会跨多个模块。
