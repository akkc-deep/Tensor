# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M00-T01`
- **Transition:** `IN_PROGRESS -> BLOCKED`

## Current State

- **Complete:** 已完整读取 M00-T01 设计；看板未关联交接文件，因此启动时无交接可读；已按设计声明的标题范围读取 BRD、PRD、TRD、路线图和 M00-T01 任务卡；已执行契约测试的预期失败阶段。
- **Partial:** 已创建 `docs/traceability/tensor-v1-requirements.md` 草稿。37 行结构、顺序、优先级、TRD、Module、Evidence 和功能需求 BRD 映射通过自动检查及独立审查。
- **Blocked:** 授权源没有定义 PRD 10.1～10.3、10.5、10.6 的 BRD 单元格值，也没有提供逐项 PRD/NFR→AC 交叉表；设计同时要求七列非空、缺少映射时停止且不得猜测。
- **Unverified:** 草稿中的上述 BRD 与 Acceptance 推断未获权威来源支持，因此整体源保真验收和 M00-T01 完成条件未通过。

## Changed Files

- `docs/traceability/tensor-v1-requirements.md`：创建 37 行需求追踪草稿；其中 TRD、Module、Evidence、优先级和功能 BRD 映射已核验，部分 BRD/Acceptance 单元格仍受阻塞影响。
- `docs/task-handoffs/tensor-v1-task-board.md`：记录用户显式启动证据并将 M00-T01 从 `READY` 转为 `IN_PROGRESS`；本交接写入后将记录 `IN_PROGRESS -> BLOCKED` 及交接路径。

## Verification

- `traceability-contract`（`.task-handoff/current.yaml` 中登记的完整 `python3 -c` 命令）：首次执行因目标文件不存在返回退出码 1，符合 RED 预期；创建草稿后重新执行返回退出码 0。
- `for n in $(seq -w 1 31); do rg -q "PRD-F-0$n" docs/traceability/tensor-v1-requirements.md || exit 1; done; for n in $(seq -w 1 18); do rg -q "AC-0$n" docs/traceability/tensor-v1-requirements.md || exit 1; done`：退出码 0。
- `rg -n '\| *\| *$|T[B]D|T[O]DO' docs/traceability/tensor-v1-requirements.md`：无输出，符合预期。
- 相对链接目标检查：2/2 目标存在。
- 六个受保护输入文件的 SHA-256 在实施前后保持与 `.task-handoff/current.yaml` 登记值一致。
- 独立审查结论：`Ready to merge: With fixes`；没有 Critical 问题，存在两项设计权威缺口及其导致的源保真实施问题。

## Remaining Work

- 由权威设计补充非功能需求的 BRD 映射规则，或明确允许使用的非空无映射表示。
- 由权威设计补充逐项 PRD/NFR→AC 交叉表，或明确直接、部分和内联验收的表达规则。
- 按修订后的权威规则仅重生成受影响的 BRD 和 Acceptance 单元格。
- 重新执行契约、标识完整性、空列/占位符、源文件不变性和独立源保真审查。

## Resume Task

- **Task ID:** `M00-T01`
- **Goal:** 交付“BRD→PRD→TRD 双向追踪索引”。

## Start Here

1. `docs/task-handoffs/tensor-v1-task-board.md` 的 `M00-T01` 行与任务详情。
2. `docs/task-designs/M00-T01-designs.md` 全文。
3. `docs/task-handoffs/M00-T01-handoff.md` 全文。
4. `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的 `12.1 核心验收场景` 与 `13. BRD 追踪矩阵`。
5. `docs/traceability/tensor-v1-requirements.md` 草稿。
6. **First action:** 由项目所有者修订并批准 M00-T01 设计，明确非功能需求 BRD 值与逐项 Acceptance 映射规则，使缺失映射不再需要实施者推断。

## Blocker

- **Reason:** 当前授权源不足以在不推断的前提下填满全部 BRD 和 Acceptance 单元格，与设计的非空要求和“缺少映射时停止生成”规则冲突；当前任务又明确禁止修改设计及需求源。
- **Resolution condition:** 项目所有者批准的 M00-T01 设计或其授权需求矩阵明确给出上述映射/表示规则，并将其纳入可读取输入后，可凭该证据执行 `BLOCKED -> READY`。

## Risks

- 当前草稿虽通过结构检查，但尚非权威追踪索引；若在解阻前被下游使用，可能把推断的 BRD/AC 关系误当成正式需求映射。
