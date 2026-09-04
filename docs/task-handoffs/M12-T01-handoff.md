# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M11-T05`
- **Next task:** `M12-T01`
- **Design document:** `docs/task-designs/M12-T01-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **Task:** `M12-T01` — 数据集选择与动态筛选表单。
- **Goal:** 交付只依赖数据集摘要和 `filters` 元数据的受控 `DatasetSelect`、可访问 `DynamicFilterForm` 与 `useDatasetFilters`，使后续数据查看页可选择当前来源的数据集、按实际核心字段校验筛选并取得稳定查询条件快照，而不按具体数据集写分支。
- **Scope:** 只创建 `control-plane/src/components/dataset/DatasetSelect.vue`、`DynamicFilterForm.vue`、两份对应 spec、`control-plane/src/composables/useDatasetFilters.js` 及其 spec；不创建数据源选择、不修改页面/router/API/配置，不请求网络，也不管理结果、页码、表格、查询状态或后续 M12 组件。
- **Acceptance:** 选择器完整展示并搜索 49 个受控数据集选项；表单仅按 `ts_code`、`trade_date`、`ann_date` 元数据渲染可访问控件；composable 支持空条件、代码规范化、严格 ISO 日期、单边范围、多条件 AND、错误/首错/成功快照及切换重置；严格 RED、聚焦 15/15、全量 90/90、生产构建、公开表面、安全和精确六文件范围达到 `docs/task-designs/M12-T01-design.md` 的预期。

## Dependencies

### `M10-T03`

- **Artifact:** `docs/task-designs/M10-T03-design.md` 与 `control-plane/src/api/datasets.js`，最终实现提交 `8e4ff0d`、`caa9987`、`890ed88`。
- **Decision:** `DatasetSummary` 使用 `pluginId/apiName/displayName/category/queryMode/filters/fixedColumn`，`DatasetFilter` 只含三个封闭字段，查询条件键固定为 `tsCode`、`tradeDateFrom/To`、`annDateFrom/To`、`page`、`pageSize`；成功 DTO 保持 OpenAPI camelCase，不由 API 层 trim、校验或重命名。
- **Rationale:** M10 API 边界和 M12 页面必须共享 OpenAPI 的数据集元数据与查询参数合同，避免组件按数据集名称或另一套字段规则推导协议。
- **Constraint:** M12-T01 只根据传入摘要和筛选描述符形成前五个筛选键，不修改 API 模块、不调用网络、不发送空字段、不引入 `page/pageSize`，也不把 `apiName` 用作行为分支。
- **Usage:** `DatasetSelect` 直接消费 `DatasetSummary[]` 的 `apiName/displayName/category`；`DynamicFilterForm` 和 `useDatasetFilters` 消费 `filters`，输出可直接合并进后续 `queryDataset` criteria 的前五个 camelCase 字段。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；当前 `control-plane/src/api/datasets.js` 相对最终修复提交 `890ed88` 的范围化差异检查退出 0。

### `M10-T04`

- **Artifact:** `docs/task-designs/M10-T04-design.md`、`control-plane/src/utils/date.js`、`control-plane/src/utils/validation.js` 与 `control-plane/src/components/common/FieldError.vue`，实现提交 `0a61e3f`、严格时间戳修复提交 `0818fbc`。
- **Decision:** `toApiDate` 只接受真实严格 `YYYY-MM-DD`；`hasValue` 与 `isRangeOrdered` 提供无业务文案的空值/范围布尔校验；M12 查询日期保持 ISO 而不转换为下载紧凑格式；`FieldError` 以纯文本和 `role="alert"` 显示错误，ARIA 关联和首错聚焦由调用方负责。
- **Rationale:** M11/M12 复用确定性校验和安全错误展示，同时让下载日期转换、查询日期传输和业务错误文案保持清晰分层。
- **Constraint:** M12-T01 可用 `toApiDate` 判断日期真实性但必须保留合法 ISO 原值；不得修改共享工具、向 `FieldError` 传入用户值/HTML，或把可选空筛选误判为错误。
- **Usage:** `useDatasetFilters` 用 `hasValue`、`toApiDate` 和 `isRangeOrdered` 校验可选筛选；`DynamicFilterForm` 用 `FieldError`、`aria-invalid`、`aria-describedby` 和聚焦逻辑表达固定本地错误。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；当前三个直接消费文件相对最终修复提交 `0818fbc` 的范围化差异检查退出 0。

两项直接依赖无冲突：M10-T03 冻结传入的元数据形状和传出的查询键，M10-T04 提供不改写 M12 ISO 查询日期的校验/错误原语；M12-T01 只在二者之间建立本地受控选择与校验状态。Node.js 24.15.0 下当前前端基线为 13 files / 75 tests 全通过，Vite 8.2.2 构建转换 1676 modules 并退出 0，仅包含项目所有者已批准的 Element Plus chunk-size 提示。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M12-T01-design.md`；
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M12-T01 行与详情；
3. `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 Global Constraints、Task M12-T01 和 Module Gate；
4. `docs/contracts/openapi-v1.yaml` 的数据集摘要、定义、筛选和 records 参数合同；
5. `docs/task-designs/M10-T03-design.md` 与 `control-plane/src/api/datasets.js`；
6. `docs/task-designs/M10-T04-design.md` 与 `control-plane/src/utils/date.js`、`validation.js`、`components/common/FieldError.vue`；
7. 现有 `control-plane/src/components/download/ApiSelect.vue`、`DynamicParameterForm.vue` 与 `control-plane/src/composables/useParameterForm.js`，只复用其受控组件和表单分层模式。

首个实施动作：在 Node.js 24.15.0 下确认 13 files / 75 tests 基线仍通过并完整检查暂存区，然后只创建设计规定的三份 spec，运行聚焦命令，取得仅因三个目标生产模块不存在而失败的严格 RED。

## Risks

- `criteria()` 对成功空条件和不可用快照都返回 `{}`；调用方必须保持 `validate()` 成功后再读取快照的固定顺序。
- 数据集切换重置依赖父层传入新的 `filters` 数组引用；不得复用并原地改写同一数组。
- Element Plus 日期清空可能发出 `null`，必须与空字符串同样省略；单边日期范围仍是合法筛选。
- OpenAPI 已封闭筛选描述符组合；本任务不复制运行时 schema，也不得从列或查询模式推导未知筛选。
- 当前工作树包含与本任务无关的 `.idea/misc.xml`、`docs/issues` 和 `data-plane/**/target/` 变化；必须保留并绕开，提交前检查整个 `git diff --cached`，只提交任务明确路径。
