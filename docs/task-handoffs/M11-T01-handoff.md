# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M10-T04`
- **Next task:** `M11-T01`
- **Design document:** `docs/task-designs/M11-T01-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **Task:** `M11-T01` — 数据源与接口分组搜索选择组件。
- **Goal:** 基于调用方传入的 M10 标准化描述符，交付数据源选择、接口分组搜索和接口说明三个受控 Vue 组件，让下载页后续流程可以组合单来源默认选择、不可用原因、49 接口浏览/搜索、接口选择和查询方式说明，而不在组件内请求数据或持有页面业务状态。
- **Scope:** 只创建 `control-plane/src/components/download/` 下 `DataSourceSelect.vue`、`ApiSelect.vue`、`ApiDescription.vue` 及各自 spec；按 `category` 原值和首次出现顺序通用分组，当前 49 项保持七组；不修改 API、通用组件、工具、依赖、配置、路由、布局、页面、样式、Java、YAML、OpenAPI 或 PRD，不加入请求、错误解释、参数、下载动作、结果、API 名分支、分类翻译或接口清单。
- **Acceptance:** 单一来源默认发出 ID，多来源不擅自选择，不可用来源被禁用且公开原因以纯文本可见；接口按当前七个元数据分类完整分组，可按 `apiName`/`displayName` 搜索并用键盘选择，只发出 `update:modelValue`；接口说明安全展示标识、中文名、分类和查询方式；严格 RED、聚焦 10/10、完整前端 44/44、生产构建、安全、范围、格式和固定六文件提交均达到设计预期。

## Dependencies

### `M10-T03`

- **Artifact:** `docs/task-designs/M10-T03-design.md`、`control-plane/src/api/dataSources.js` 和 `control-plane/src/api/api.spec.js`；最终实现范围提交 `8e4ff0d`、`caa9987`、`890ed88`，完成记录 `08d99e3`。
- **Decision:** `listDataSources()` 返回未改写的 `DataSourceSummary[]`，公开 `pluginId/displayName/description/enabled/credentialConfigured/downloadAvailable/unavailableReason`；`listApis(pluginId)` 返回未改写的 `ApiDescriptor[]`，公开 `apiName/displayName/category/queryMode/parameters`，路径中的 pluginId 独立编码。成功 DTO 由 API 边界保持字段和值，组件不建立第二套请求或映射协议。
- **Rationale:** 选择组件应只表达可复用的受控 UI；唯一 Axios 实例、请求 ID、网络访问和安全错误投影已由 M10-T03 集中提供，避免组件重复请求职责或泄漏 Axios 原始状态。
- **Constraint:** 组件只消费调用方提供的数组，不导入或调用 API 函数；不修改描述符、字段大小写、分类或输入顺序；不显示凭证内容；加载、失败、重试、来源/API 切换后的下游清理均留给 M11-T03/M11-T05。
- **Usage:** 后续调用方把 `listDataSources()` 的成功值传给 `DataSourceSelect`，接收 pluginId 后加载接口，再把 `listApis(pluginId)` 的成功值同时传给 `ApiSelect`，并把选中的描述符传给 `ApiDescription`。
- **Readiness evidence:** 权威看板中 M10-T03 为 `COMPLETED`；其完成记录包含严格 TDD、最终 12/12 聚焦、19/19 当时完整前端、生产构建和最终复审无问题；当前 `dataSources.js`/`api.spec.js` 相对最终实现提交 `890ed88` 的范围化 diff 退出 0，公开 JSDoc 与函数仍可直接消费。

唯一直接依赖的决策与 M11-T01 设计无冲突：M10-T03 提供稳定、未改写的来源和接口描述符，M11-T01 仅在这些描述符上建立受控展示、搜索与选择，不回写依赖、不产生请求环路，也不扩大错误或页面状态职责。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M11-T01-design.md`；
2. `docs/superpowers/plans/2026-09-04-m11-t01-download-selectors.md`；
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 M11-T01 行与详情；
4. `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 Global Constraints、Project Inputs、Task M11-T01 和 Module Gate；
5. `docs/task-designs/M10-T03-design.md`；
6. `control-plane/src/api/dataSources.js`、`control-plane/src/api/api.spec.js`、`control-plane/package.json` 和 `control-plane/src/test/setup.js`。

首个实施动作：确认工作树为空并把 Node 24.15.0 放在 PATH 首位，然后只按任务级实施计划完整创建三个 `.spec.js`，运行聚焦命令，取得只因三个目标 `.vue` 文件不存在而失败的严格 RED。

## Risks

- PRD 附录仍表达八个业务分类；项目所有者已明确决定暂不拆分，当前验收以元数据实际七组为准。未来上游拆分 category 时组件无需 API 名分支，但测试分类期望须随授权合同更新。
- 当前基础与组织分类原值为 `basic_organization`；已批准设计不增加局部翻译表，组件按服务端原值显示。
- Element Plus 下拉层可能 Teleport 到 `document.body`；交互测试必须挂载到 body、等待异步更新并可靠 unmount，避免 DOM 与焦点泄漏。
- 成功 DTO 形状由 M10-T03/后端合同保证；本任务不增加运行时 schema，也不把异常形状静默修复成可选项。
