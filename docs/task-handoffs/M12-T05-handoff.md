# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M12-T04`
- **Next task:** `M12-T05`
- **Design document:** `docs/task-designs/M12-T05-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **Task:** `M12-T05` — `DatasetView` 页面集成和组件回归。
- **Goal:** 把 M12-T01～T04 的受控组件与状态 composable 组合成 `/datasets` 页面，以独立元数据 generation 加载数据源、数据集和定义，并完成筛选校验、五态 records 查询、全字段表格、服务端分页、重试和重置的只读闭环。
- **Scope:** 只创建 `control-plane/src/views/DatasetView.spec.js`，修改 `control-plane/src/views/DatasetView.vue` 与 `control-plane/src/layouts/AppLayout.spec.js`；不修改 router、API、composable、子组件、依赖、配置、布局生产代码或样式，不新增 store/组件/composable，不自动查询、计算分页、解释错误正文或增加写操作。
- **Acceptance:** `/datasets` 保持稳定路由与标题，元数据和 records 的旧成功/失败均不能覆盖新选择；选择后不自动查询，合法条件或无条件查询经既有表单提交；SUCCESS 完整显示当前页和分页，EMPTY 保留 page-size，FAILURE 安全重试，reset/选择切换边界正确；Node.js 24.15.0 下严格 RED、聚焦 11/11、完整前端 120/120、生产构建及精确三文件范围达到设计预期。

## Dependencies

### `M12-T01`

- **Artifact:** `docs/task-designs/M12-T01-design.md`、`control-plane/src/components/dataset/DatasetSelect.vue`、`DynamicFilterForm.vue`、`control-plane/src/composables/useDatasetFilters.js` 及其三份 spec；实现提交 `75ee19b`，空态修复 `efc86e7`，搜索复位修复 `e7e42e3`。
- **Decision:** 数据集选择器只消费摘要并保持受控；筛选表单只由 `definition.filters` 驱动，公开 `validate()/criteria()/reset()`；成功条件快照固定使用五个 camelCase 查询键，数据源选择留在页面组合层。
- **Rationale:** 数据集差异和合法筛选由服务端元数据表达，页面只按统一公开合同组合，避免具体数据集分支和第二套校验规则。
- **Constraint:** 页面必须先 await `validate()`，成功后立即读取一次新的 `criteria()`；定义切换必须传入新的 filters 引用并卸载旧表单，不能读取 composable 内部状态、从 columns/apiName 推导筛选或改写输入对象。
- **Usage:** `DatasetView` 把当前数据集清单与 ID 传给 `DatasetSelect`，把当前定义的 filters 传给唯一 `DynamicFilterForm`；查询、页面 reset 和选择切换分别消费其公开方法或挂载生命周期。
- **Readiness evidence:** 权威看板中 M12-T01 为 `COMPLETED`；完成证据记录严格 RED、最终 16 files / 92 tests、生产构建、精确六文件范围及两轮审查修复均通过，无未决审查问题。

### `M12-T02`

- **Artifact:** `docs/task-designs/M12-T02-design.md`、`control-plane/src/components/dataset/DatasetTable.vue` 与 `DatasetTable.spec.js`；实现提交 `1f073ee`，列顺序修复 `c75b4eb`，sticky 层级修复 `cd8f7ab`。
- **Decision:** `DatasetTable` 只接收 `columns/items/loading`，按定义原序完整展示全部业务列并追加三个来源列，固定 `ts_code` 或首个业务列，复用安全格式化且不裁列、不排序。
- **Rationale:** 列、类型和顺序是数据集定义的服务端事实，表格组件集中保证 152 列宽表、精度和只读显示，页面不应重建展示逻辑。
- **Constraint:** 页面只能在 SUCCESS 时传入 `definition.columns` 和 `result.items`；不得从记录键生成列、移除来源字段、转换精度字符串或把 EMPTY 的空 items 当成表格状态实现。
- **Usage:** records SUCCESS 分支挂载一个 `DatasetTable`；LOADING/EMPTY/FAILURE/UNQUERIED 由页面状态面板处理，旧结果不传入表格。
- **Readiness evidence:** 权威看板中 M12-T02 为 `COMPLETED`；完成证据记录最终聚焦 6/6、完整前端 98/98、生产构建、安全/范围门禁及无 Critical/Important/Minor 的最终复审通过。

### `M12-T03`

- **Artifact:** `docs/task-designs/M12-T03-design.md`、`control-plane/src/components/dataset/DatasetPagination.vue` 与 `DatasetPagination.spec.js`；实现提交 `f19d4ff`，零页事件修复 `7918e8e`。
- **Decision:** 分页组件只接收服务端 `page/pageSize/totalElements/totalPages/disabled`，固定 20/50/100，发送 `update:page/update:pageSize`；零页仍显示 `第 1 / 0 页` 并保留 page-size 选择器。
- **Rationale:** 服务端负责 totals 和超界页规范化；受控分页组件只表达事实和用户意图，避免客户端产生第二套页码计算。
- **Constraint:** 页面不得从 total/pageSize 计算页数或在 size 事件中自行拼接查询；SUCCESS 与 EMPTY 都必须传服务端 totals，EMPTY 不能隐藏分页，只有 records loading 可以禁用分页动作。
- **Usage:** 页面把查询 composable 的 page/pageSize、当前 result totals 和 loading 传给组件，并将两个事件分别连接 `changePage`/`changePageSize`。
- **Readiness evidence:** 权威看板中 M12-T03 为 `COMPLETED`；完成证据记录最终聚焦 6/6、完整前端 104/104、生产构建、真实零页选择修复和无任何级别问题的复审通过。

### `M12-T04`

- **Artifact:** `docs/task-designs/M12-T04-design.md`、`control-plane/src/composables/useDatasetQuery.js` 与 `useDatasetQuery.spec.js`；实现提交 `8b89828`。
- **Decision:** `useDatasetQuery()` 唯一管理 UNQUERIED/LOADING/SUCCESS/EMPTY/FAILURE、不可变请求快照、records generation、服务端页码事实、retryable 失败重试和查询 reset。
- **Rationale:** 页面组合不应复制 records 网络、竞态或分页状态；统一 execute 边界保证 stale success/failure 被消费但不能写入当前页面。
- **Constraint:** 页面只创建一个实例；新查询必须经 `query(pluginId,apiName,criteria)`，分页/重试/reset 只调用公开动作；不得读取内部快照/generation、直接调用 queryDataset、重算状态或让 DOM event 成为 retry 参数。
- **Usage:** 页面使用公开 refs/computed 驱动状态面板、筛选禁用、表格与分页；来源/数据集切换和页面 reset 调用查询 reset，失败按钮显式无参调用 retry。
- **Readiness evidence:** 权威看板中 M12-T04 为 `COMPLETED`；提交 `8b89828` 经控制器新鲜复验聚焦 8/8、完整前端 112/112、生产构建、导出/安全/范围门禁，并由任务级和最终审查确认无 Critical、Important 或 Minor。

四项直接依赖无冲突：M12-T01 产出选择和合法条件快照，M12-T02 只渲染定义列与当前页，M12-T03 只发出服务端分页意图，M12-T04 统一执行 records 查询并保存其状态；`DatasetView` 只负责加载元数据和按顺序连接这些互补边界。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M12-T05-design.md`；
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M12-T05 行与详情；
3. `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 Global Constraints、Task M12-T05 和 Module Gate；
4. `docs/task-designs/M12-T01-design.md` 至 `M12-T04-design.md`，以及各自 Files 节列出的当前生产文件和 spec；
5. `control-plane/src/api/dataSources.js`、`datasets.js`、`errors.js` 与 `api.spec.js`；
6. `control-plane/src/components/common/AsyncStatePanel.vue`、`control-plane/src/components/download/DataSourceSelect.vue`、`control-plane/src/views/DownloadView.vue` 与 `DownloadView.spec.js`，只参考安全状态、单来源事件、显式无参重试和真实页面测试模式；
7. `control-plane/src/views/DatasetView.vue`、`control-plane/src/router/index.js`、`router/index.spec.js` 和 `control-plane/src/layouts/AppLayout.spec.js`；
8. PRD 6.4～6.7、7.4、AC-012～016，TRD 11.2、12.4、13.5～13.7、20.4，以及 OpenAPI 的数据源、数据集摘要/定义、records、PageResponse 和 ApiError 合同。

首个实施动作：在 Node.js 24.15.0 下确认当前 19 files / 112 tests 基线和完整暂存区状态，再只创建完整 `DatasetView.spec.js` 并更新 `AppLayout.spec.js` 的数据查看稳定表面断言；保持 `DatasetView.vue` 为占位实现，运行两文件聚焦命令并取得只由缺少批准页面行为产生的严格 RED。

## Risks

- `DataSourceSelect` 的单来源默认事件会在来源数组更新后触发连续的数据集清单请求；测试必须显式消费 Promise，不使用任意 timeout。
- 元数据和 records 使用两个独立 generation；来源或数据集切换必须先 reset 查询，避免旧 records 在新定义加载时落地。
- EMPTY 必须同时显示空态与可用 page-size；只显示空文案会破坏 M12-T03/M12-T04 的零页合同。
- retry 使用失败时的元数据或 records 快照，不读取当前表单；按钮必须用显式无参包装，避免转发 click event。
- 当前工作树中的 `.idea`、`docs/issues` 和 `data-plane/**/target/` 变化不属于 M12-T05，实施必须用精确路径提交并保留它们。
