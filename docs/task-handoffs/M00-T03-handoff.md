# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M00-T02`
- **Next task:** `M00-T03`
- **Design document:** `docs/task-designs/M00-T03-designs.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M00-T03`
- **Title:** `/api/v1` OpenAPI 契约与错误码目录
- **Goal:** 交付“`/api/v1` OpenAPI 契约与错误码目录”。
- **Scope:** 包含该交付物及其直接测试与验证；不包含其他预定义任务的交付物，也不扩展 `docs/superpowers/plans/tensor-modules/M00-contracts.md` 中 M00-T03 任务卡明确的文件、主要语言、接口和排除边界。
- **Acceptance:** “`/api/v1` OpenAPI 契约与错误码目录”按任务卡指定的位置和行为形成；任务卡列出的全部测试、验证命令和检查得到其注明的预期结果；没有混入排除范围。

## Dependencies

### `M00-T01`

- **Artifact:** `docs/traceability/tensor-v1-requirements.md`
- **Decision:** M00-T03 必须保留追踪索引中稳定的 PRD/Acceptance 语义；`Acceptance` 区分直接、部分和内联验收，`Evidence` 只表示后续证据责任。
- **Rationale:** REST 元数据、下载、查询、分页和失败响应需要能够回溯到已冻结的功能需求与验收范围，但追踪表本身不替代详细 DTO 设计。
- **Constraint:** 不得把 `Evidence` 当作已通过验收，也不得从追踪表推断 TRD 12 未定义的字段或状态码；精确 DTO 名称、字段大小写和错误映射以 M00-T03 任务卡、PRD 5～9 和 TRD 12 为准。
- **Usage:** 把映射到 REST/页面交互的追踪行作为六个业务操作、响应结果和错误契约的需求检查清单，并在设计与验收中保留对应需求标识。
- **Readiness evidence:** M00-T01 在权威看板中为 `COMPLETED`；其结构、映射、标识、占位符、输入不变性和链接验证均退出码 0，独立审查结论为 `Ready to merge: Yes`。

### `M00-T02`

- **Artifact:** `docs/contracts/dataset-definition.schema.json`；解释该契约边界的设计为 `docs/task-designs/M00-T02-designs.md`。
- **Decision:** 插件/API 标识符使用 `^[a-z][a-z0-9_]{1,63}$`；数据集定义固定采用十个必填根字段和可选 `fixedColumn`，业务键为 `{mode, fields}`，筛选为字段名数组。
- **Rationale:** M00-T03 的路径参数和 `DatasetDefinitionResponse` 必须与已冻结的元数据契约保持同一标识符与字段语义，避免后端、前端和 OpenAPI 出现平行定义。
- **Constraint:** OpenAPI 中的插件/API 路径参数必须逐字复用批准的正则；对外数据集定义不得暴露凭证，也不得擅自改变 M00-T02 已冻结的字段名称或枚举含义。
- **Usage:** 将标识符正则用于相关 path parameter schema，并以 M00-T02 契约核对 `DatasetDefinitionResponse` 的元数据字段与嵌套结构。
- **Readiness evidence:** M00-T02 在权威看板中为 `COMPLETED`；修复后的正向、根契约、七个反例、`daily` 顺序/引用、精确契约和缺失判别字段诊断均退出码 0，最终独立审查结论为 `Ready to merge: Yes` 且修复复审无未解决问题。

## Start Here

1. `docs/task-designs/M00-T03-designs.md` 全文。
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 `M00-T03` 行与任务详情。
3. `docs/superpowers/plans/tensor-modules/M00-contracts.md` 的 M00-T03 任务卡。
4. `docs/traceability/tensor-v1-requirements.md`。
5. `docs/contracts/dataset-definition.schema.json` 与 `docs/task-designs/M00-T02-designs.md`。
6. `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的 5～9 节。
7. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 12 节。
8. **First action:** 按设计“如何测试”先运行 OpenAPI 结构检查和错误目录检查，在两个目标文件尚不存在时记录预期的非零退出结果，再开始创建契约文件。

## Risks

- 设计已冻结 16 个错误码及其 HTTP 状态和 `retryable`；若后续需要 TRD 12.6 中更宽泛的 404/503 语义，必须先独立修订契约，不能在实现中自行扩展。
- 项目没有 Git 元数据；后续任务卡提交步骤仅在 Git 可用时执行，不得初始化仓库。
