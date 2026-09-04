# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M12-T01`
- **Next task:** `M12-T02`
- **Design document:** `docs/task-designs/M12-T02-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **Task:** `M12-T02` — 全字段、固定列和横向滚动表格。
- **Goal:** 交付只消费业务列元数据、当前页记录和加载状态的只读 `DatasetTable`，按定义原序完整显示全部业务字段及三个来源字段，通过确定固定列、纯文本 tooltip 和横向滚动安全承载 `balancesheet` 152 个业务列，并复用 M10-T04 保持空值、日期、入库时间和高精度值的展示语义。
- **Scope:** 只创建 `control-plane/src/components/dataset/DatasetTable.vue` 和 `DatasetTable.spec.js`；不修改页面、router、API、共享工具、依赖或配置，不请求网络，不管理查询/失败/分页，也不提供排序、选择、列配置或写操作。
- **Acceptance:** 业务列保持元数据原序并追加 `source_plugin/source_api/ingested_at`，152 个业务列形成 155 个真实表格列且可横向滚动；只固定 `ts_code` 或首个业务列；全列纯文本 tooltip 及 140/240/180px 最小宽度、M10-T04 格式化、loading/ARIA、严格 RED、聚焦 6/6、全量 98/98、构建和精确两文件范围达到 `docs/task-designs/M12-T02-design.md` 的预期。

## Dependencies

### `M10-T04`

- **Artifact:** `docs/task-designs/M10-T04-design.md`、`control-plane/src/utils/date.js`、`control-plane/src/utils/format.js` 与 `control-plane/src/utils/format.spec.js`；初始实现提交 `0a61e3f`、严格时间戳修复提交 `0818fbc`。
- **Decision:** `formatCell(value, column, timeZone = 'Asia/Shanghai')` 只把 `null/undefined` 映射为 `--`，保持 `0`、空字符串和 `DECIMAL/LONG` 字符串原值，按 `logicalType === 'DATE'` 保持严格 ISO 日期，并按 `name === 'ingested_at'` 将严格显式偏移时间戳转换为默认上海时区、精确到秒的文本。
- **Rationale:** M12 表格必须复用同一确定性显示边界，避免宽表组件重新实现日期/时区规则或把高精度数据库值转换为 JavaScript number。
- **Constraint:** M12-T02 必须对每个单元格原样调用 `formatCell(item[column.name], column)`，不得修改共享工具、解析数值、吞掉非法日期/时间或传入自选时区；所有结果通过 Vue 文本插值和 Element Plus tooltip 展示，禁止 HTML 渲染。
- **Usage:** `DatasetTable` 把业务 `DatasetColumn` 和三个固定来源描述符传给 `formatCell`；`ingested_at` 依靠名称分派，其他来源值保持字符串，业务 DATE/DECIMAL/LONG 按元数据分派或保留。
- **Readiness evidence:** 权威看板中 M10-T04 为 `COMPLETED`；当前 `date.js`、`format.js`、`validation.js` 和 `FieldError.vue` 相对最终修复提交 `0818fbc` 的范围化差异检查退出 0。

唯一直接依赖的决策与 M12-T02 无冲突：M10-T04 冻结单元格值的安全格式化，M12-T02 只负责元数据原序、来源列、固定列、宽度、tooltip、横向滚动和加载展示，不扩大共享工具或查询职责。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M12-T02-design.md`；
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M12-T02 行与详情；
3. `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 Global Constraints、Task M12-T02 和 Module Gate；
4. `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的 6.5、7.4 与 AC-015，以及 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 12.4、13.5；
5. `docs/contracts/openapi-v1.yaml` 的 `DatasetDefinitionResponse`、`DatasetColumn` 与 `PageResponse` 合同；
6. `docs/task-designs/M10-T04-design.md` 与 `control-plane/src/utils/date.js`、`format.js`、`format.spec.js`；
7. 现有 `control-plane/src/components/dataset` 测试和组件，只复用 Vue/Element Plus 的局部风格、真实组件测试与安全文本模式。

首个实施动作：在 Node.js 24.15.0 下确认当前 16 files / 92 tests 基线仍通过并完整检查暂存区，然后只创建完整 `DatasetTable.spec.js`，运行聚焦命令，取得仅因 `DatasetTable.vue` 不存在而无法收集测试的严格 RED。

## Risks

- jsdom 无真实布局；tooltip 测试必须注入可控溢出尺寸并触发真实 hover，不能只断言 `show-overflow-tooltip` prop。
- 152 个业务列加三个来源列的浏览器滚动体验属于 M14-T03 E2E；本任务以精确列实例、宽度和滚动外壳证明不裁列。
- 空 `items` 仍保留完整表头；页面空结果状态由 M12-T05 组合，不得塞入本组件。
- M10-T04 默认时区固定为 `Asia/Shanghai`；本任务不增加时区 prop 或提前实现 M13 配置。
- 当前工作树包含与任务无关的 `.idea`、`docs/issues` 和后端 `target/` 变化；实施必须使用精确路径提交并保留这些用户变化。
