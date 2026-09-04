# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M11-T01`
- **Next task:** `M11-T02`
- **Design document:** `docs/task-designs/M11-T02-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。

## Next Task

- **ID:** `M11-T02`
- **Title:** 元数据驱动动态参数表单
- **Goal:** 交付按 `ParameterDescriptor[]` 生成六类可访问控件、执行本地字段校验并提供顺序稳定规范化快照的动态参数表单。
- **Scope:** 只创建 `control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/composables/useParameterForm.js` 和 `control-plane/src/components/download/DynamicParameterForm.spec.js`；不修改 API、共享原语、依赖、配置、页面或其他任务产物，不请求网络、不接收 `apiName`、不拥有下载生命周期或服务端错误解释。
- **Acceptance criteria:** 六种元数据类型使用设计冻结的 Element Plus 控件与显示/提交格式；默认值、reset、参数切换、必填/类型/pattern/范围校验、字段 ARIA、首错聚焦、空表单、disabled 和成功快照失效边界全部可观察；Node.js 24.15.0 下严格 RED 原因正确，聚焦 9/9、完整前端 53/53 和 Vite 构建通过，且实现提交精确只含设计规定的三个文件。

## Dependencies

### `M10-T03`

- **Artifact:** `control-plane/src/api/dataSources.js` 中 `ApiParameter`/`ApiDescriptor` JSDoc 与 `listApis(pluginId)` 成功 DTO 边界；完整合同见 `docs/task-designs/M10-T03-design.md`。
- **Decision:** 参数描述符按原顺序提供 `name`、`label`、`type`、`required`，并可提供 `description`、`defaultValue`、`allowedValues`、`pattern`、`relatedParameter`；`type` 闭集为 `DATE | DATE_RANGE_MEMBER | MONTH | TS_CODE | ENUM | TEXT`。
- **Rationale:** M10 API 边界直接保持 OpenAPI 成功 DTO 的字段和值，后续 UI 只消费服务端元数据，不复制运行时 schema 或按具体 API 名分支。
- **Constraint:** M11-T02 不得修改、trim、重命名或补造描述符字段，不得消费 `apiName` 或调用 `listApis`；组件只接收调用方已经取得的 `parameters` 数组。
- **Usage:** `DynamicParameterForm` 按描述符顺序和 `type` 选择控件，并从其余元数据字段建立默认值、选项、说明、pattern 和日期关联。
- **Readiness evidence:** 权威看板记录 M10-T03 为 `COMPLETED`；其最终修复提交为 `890ed88`，当前 `dataSources.js` 与相关 `api.spec.js` 相对该提交无差异。M10-T03 完成证据记录 Node 24.15.0 下聚焦 12/12、当时全量 19/19、生产构建及最终独立复审通过。

### `M10-T04`

- **Artifact:** `control-plane/src/utils/date.js`、`control-plane/src/utils/validation.js` 和 `control-plane/src/components/common/FieldError.vue`；完整合同见 `docs/task-designs/M10-T04-design.md`。
- **Decision:** 下载日期/月分别由 `toApiDate`/`toApiMonth` 严格转换为紧凑格式；空值、元数据 pattern 和有效日期范围分别使用 `hasValue`、`matchesPattern`、`isRangeOrdered`；字段错误使用纯文本 `FieldError`，调用方负责字段 ARIA 与首错聚焦。
- **Rationale:** M11 与 M12 共用确定性、无业务文案的校验和可访问性原语，避免动态表单复制第二套日期、正则、范围或错误渲染规则。
- **Constraint:** `toApiDate`/`toApiMonth` 只接受严格 ISO 显示字符串；`matchesPattern` 不自动加锚、空或非法正则返回 `false`；`isRangeOrdered` 只比较已通过严格校验的字符串且允许缺失端；`FieldError` 不替调用方设置 `aria-describedby`、`aria-invalid` 或焦点。
- **Usage:** composable 在类型规范化后调用这些纯函数，组件以 `FieldError` 输出固定本地错误并自行维护控件 ID、ARIA 关系和焦点引用。
- **Readiness evidence:** 权威看板记录 M10-T04 为 `COMPLETED`；其最终修复提交为 `0818fbc`，当前上述三个生产文件及相关测试相对该提交无差异。M10-T04 完成证据记录 Node 24.15.0 下聚焦 15/15、当时全量 34/34、双宿主时区断言、生产构建及最终独立复审通过。

两项直接依赖无冲突：M10-T03 提供未经改写的参数描述符，M10-T04 提供不含业务文案的通用转换、校验和错误渲染原语；M11-T02 只在两者之上增加元数据分派、本地文案、状态和可访问焦点行为。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M11-T02-design.md`
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 `M11-T02` 行与详情
3. `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T02`
4. `control-plane/src/api/dataSources.js` 与 `docs/task-designs/M10-T03-design.md`
5. `control-plane/src/utils/date.js`、`control-plane/src/utils/validation.js`、`control-plane/src/components/common/FieldError.vue` 与 `docs/task-designs/M10-T04-design.md`

首个实施动作：确认工作树为空并使用 Node.js 24.15.0 复验 9 files / 44 tests 基线；随后只完整创建 `control-plane/src/components/download/DynamicParameterForm.spec.js`，在组件和 composable 均不存在时运行聚焦命令，取得仅因 `./DynamicParameterForm.vue` 缺失而发生的严格 RED。

## Risks

- 当前 49 个描述符没有 TEXT、pattern、默认值或可选参数；测试必须用合成描述符覆盖 OpenAPI 已允许的这些形状，生产实现不得把当前数据现状硬编码成闭集。
- `matchesPattern` 使用无 flags 的 JavaScript `RegExp` 且不自动加锚；表单只能按元数据原样调用，并把非法 pattern 作为固定本地格式错误处理。
- Element Plus 日期和选择控件可能使用 Teleport/Popper；测试不得依赖内部类名，挂载到 `document.body` 的包装器必须可靠卸载。
- 字段变化、reset 或校验失败会使最近一次成功快照失效；后续调用方必须始终先等待 `validate()` 成功，再立即读取 `normalizedValues()`。
