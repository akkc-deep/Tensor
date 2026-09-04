# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M11-T04`
- **Next task:** `M11-T05`
- **Design document:** `docs/task-designs/M11-T05-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。

## Next Task

- **ID:** `M11-T05`
- **Title:** `DownloadView` 页面集成和组件回归
- **Goal:** 把 M11-T01～T04 已完成的受控组件和唯一下载流程组合成可从 `/downloads` 完成来源/API 加载、参数校验、同步下载与最终反馈的页面，同时保持稳定路由和真实应用壳回归。
- **Scope:** 创建 `control-plane/src/views/DownloadView.spec.js`，修改 `control-plane/src/views/DownloadView.vue`、`control-plane/src/App.spec.js` 和 `control-plane/src/layouts/AppLayout.spec.js`；不修改 router、API、composable、子组件、工具、依赖、配置、布局生产代码或全局样式，不增加阶段、进度、取消、自动重试、历史或持久化。
- **Acceptance criteria:** 页面挂载即自动加载元数据，按来源→接口→说明→参数→动作→结果顺序只连接既有公开合同；元数据加载/失败和初始引导与下载最终三态严格分流，提交前校验，提交期间全部相关控件锁定，来源/API 切换清除旧下游状态；Node.js 24.15.0 下严格 RED 原因正确，页面/壳层聚焦 12/12、完整前端 75/75、既有稳定 router 3/3、生产构建及精确四文件范围均达到设计结果。

## Dependencies

### `M11-T01`

- **Artifact:** `control-plane/src/components/download/DataSourceSelect.vue`、`ApiSelect.vue`、`ApiDescription.vue` 及其三份同目录测试；完整合同见 `docs/task-designs/M11-T01-design.md`。
- **Decision:** 三个组件均为受控组件，只消费标准来源/API 描述符并发出选择更新；单一来源在空值时默认发出 ID，接口按元数据分类/顺序分组并支持双字段搜索，接口说明只展示当前描述符。
- **Rationale:** 页面只负责把选择事件交给唯一流程，避免子组件发请求、保存页面状态或按具体来源/API 分支。
- **Constraint:** 页面必须通过 `modelValue`/`update:modelValue` 连接选择，提交期间传入 disabled；不可用来源必须禁用接口/下载，不得修改组件、硬编码 49 清单、复制搜索/分组或依赖 Element Plus 私有结构。
- **Usage:** `DownloadView` 把 M11-T03 的 `sources`/`selectedPluginId` 和 `apis`/`selectedApiName` 分别传给两个选择器，将更新事件连接到 `selectSource`/`selectApi`，并把 `selectedApi` 传给说明组件。
- **Readiness evidence:** 权威看板记录 M11-T01 为 `COMPLETED`；实现提交 `e08f467`、键盘回归强化 `38ddb8a`，完成证据为聚焦 10/10、当时全量 44/44、构建和最终复审通过。交接准备时六个产物相对 `38ddb8a` 无差异。

### `M11-T02`

- **Artifact:** `control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/composables/useParameterForm.js` 和同目录组件测试；完整合同见 `docs/task-designs/M11-T02-design.md`。
- **Decision:** 表单只按 `ParameterDescriptor[]` 管理六类输入、本地错误和新鲜成功快照，对外暴露异步 `validate()`、同步 `normalizedValues()`/`reset()`，并用 `disabled` 锁定控件；parameters 变化会重置表单。
- **Rationale:** 参数形状、默认值、校验、规范化和首错聚焦集中在表单边界，页面无需复制规则或接触具体参数名。
- **Constraint:** 页面必须先 `await validate()`，仅成功后立即读取一次 `normalizedValues()` 并提交；不得接收/判断 `apiName`、读取表单内部状态、缓存旧快照或在校验失败时调用下载。
- **Usage:** `DownloadView` 只在 `selectedApi` 存在时挂载表单，传入 `selectedApi.parameters`/`locked`，通过唯一 ref 在动作事件中执行批准的校验—快照顺序。
- **Readiness evidence:** 权威看板记录 M11-T02 为 `COMPLETED`；实现提交 `3e2b3ce`、原型字段与 mutation 回归修复 `e6024c7`，完成证据为聚焦 9/9、当时全量 53/53、构建和范围化复审通过。交接准备时三个产物相对 `e6024c7` 无差异。

### `M11-T03`

- **Artifact:** `control-plane/src/composables/useDownloadFlow.js` 暴露的七态、来源/API/选择/结果/错误 refs、派生值与 `load`/`selectSource`/`selectApi`/`submit`/`retry` 动作，以及 `useDownloadFlow.spec.js`；完整合同见 `docs/task-designs/M11-T03-design.md`。
- **Decision:** `useDownloadFlow()` 是页面唯一业务状态源；元数据和下载共享 `METADATA_LOADING | READY | FAILURE` 等七态，单调 generation 忽略 stale 响应，选择切换同步清理下游状态，下载锁定和 `canRetry`/冻结参数重试均由 composable 决定。
- **Rationale:** 页面只投影状态和连接事件，不能形成第二套异步状态、竞态、错误或重试逻辑。
- **Constraint:** 页面只创建一个实例并在 mounted 调用一次 `load()`；必须使用 `locked`/`canSubmit`/`canRetry`，不得读取 `error.retryable`、复制 generation、自动重试或从当前表单重建原参数重试。元数据失败时 API 选择为空，下载失败时当前 API 保留，这是两类 FAILURE 的既有区分输入。
- **Usage:** 页面把 refs/computed 传给各子组件，选择事件连接相应动作，合法表单快照交给 `submit`，两个重试入口均调用无参 `retry()`；页面根据 state 与 `selectedApiName` 分流元数据失败和下载失败。
- **Readiness evidence:** 权威看板记录 M11-T03 为 `COMPLETED`；实现提交 `e893d0f` 精确新增 composable 与 8 项测试，完成证据为聚焦 8/8、当时全量 61/61、构建和安全/范围门禁通过。交接准备时两个产物相对 `e893d0f` 无差异。

### `M11-T04`

- **Artifact:** `control-plane/src/components/download/DownloadAction.vue`、`DownloadResult.vue` 和 `DownloadResult.spec.js`；完整合同见 `docs/task-designs/M11-T04-design.md`。
- **Decision:** `DownloadAction(disabled, submitting)` 固定显示“开始下载”且仅在未锁定时发出 `submit`；`DownloadResult(state, result, error, canRetry)` 只呈现下载最终 `SUCCESS | EMPTY | FAILURE` 并发出 `retry`，重试可见性只服从 `canRetry`。
- **Rationale:** 页面无需复制按钮防重、成功计数、合法空结果、安全失败或原参数重试呈现，并可避免把元数据失败误画成下载失败。
- **Constraint:** 页面必须传入 `!canSubmit`/`locked` 和原样最终状态/响应/错误/`canRetry`；只有真实下载最终态可挂载 `DownloadResult`，元数据失败必须使用独立面板；不得显示 response message、内部错误字段、阶段或进度。
- **Usage:** 页面始终渲染动作组件并在 `submit` 事件执行表单校验；下载完成后挂载结果组件，将其 `retry` 事件直接连接 M11-T03 无参重试。
- **Readiness evidence:** 权威看板记录 M11-T04 为 `COMPLETED`；实现提交 `8bb9959`、受控重试边界强化 `c78d4f3`，完成记录提交 `f9f9a5d`。交接准备时三个产物相对 `c78d4f3` 无差异；最终证据为聚焦 6/6、全量 67/67、构建、安全/范围门禁和本地逐项审查通过。

四项直接依赖无冲突：M11-T01 提供无网络的受控选择/说明，M11-T02 提供无业务状态的参数校验与快照，M11-T03 提供唯一异步状态和动作，M11-T04 提供受控提交入口与下载最终结果；M11-T05 只按这些公开边界组合。项目所有者于 2026-09-05 批准页面 mounted 自动加载、非最终状态独立面板、仅下载最终态使用 `DownloadResult`、稳定 router 不改，以及为自动加载副作用纳入 `App.spec.js`/`AppLayout.spec.js` 的精确四文件范围。Node.js 24.15.0 下交接前新鲜基线为 12 files / 67 tests 全通过，Vite 8.2.2 构建转换 1599 modules 并退出 0，只含既有 Element Plus chunk-size 提示。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M11-T05-design.md`
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M11-T05 行与详情
3. `docs/superpowers/plans/tensor-modules/M11-download-ui.md` 的 `Task M11-T05`
4. `docs/task-designs/M11-T01-design.md`、`M11-T02-design.md`、`M11-T03-design.md`、`M11-T04-design.md`
5. `control-plane/src/components/download/` 六个生产组件、`control-plane/src/composables/useParameterForm.js` 和 `useDownloadFlow.js`
6. `control-plane/src/views/DownloadView.vue`、`control-plane/src/App.spec.js`、`control-plane/src/layouts/AppLayout.spec.js`、`control-plane/src/router/index.js` 与 `index.spec.js`
7. `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的 4.1～5.7、7.2、9、10.5 和 12.1 节

首个实施动作：确认 `control-plane` 的四文件目标范围和受保护路径没有重叠改动，并保留当前与本任务无关的 `.idea/misc.xml` 及 `data-plane/**/target/` 构建输出；在 Node.js 24.15.0 下复验 12 files / 67 tests 基线后，只完整创建 `control-plane/src/views/DownloadView.spec.js` 并修改 `control-plane/src/App.spec.js`、`control-plane/src/layouts/AppLayout.spec.js` 的 API 隔离与新页面断言，在保持 `DownloadView.vue` 为既有占位实现时运行设计中的三文件聚焦命令，取得只因完整下载页面行为尚未实现而发生的严格 RED。

## Risks

- 页面用当前 API 选择是否为空区分元数据 FAILURE 与下载 FAILURE；该规则依赖 M11-T03 已冻结并验证的清理/保留合同，未来若状态接口改变必须同步修订页面与测试。
- 单来源默认选择会在来源加载后立即继续触发 API 元数据请求；测试必须用 `flushPromises()` 消费确定性 Promise 链，不能使用 timeout。
- Element Plus 的下拉层可能 Teleport；焦点和键盘测试只依赖公开 combobox/input/button 与组件事件，并可靠 unmount。
- App/AppLayout 测试只能 mock 元数据 API 以隔离自动加载，不能 mock 下载页、router、layout 或子组件，否则会失去真实壳层回归价值。
- 当前工作区存在 M11-T05 范围外的 `.idea/misc.xml` 修改和 `data-plane/**/target/` 构建输出；后继实施必须保留并绕开，不能把它们混入实现提交或擅自清理。
