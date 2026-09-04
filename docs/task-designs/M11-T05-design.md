# M11-T05 `DownloadView` 页面集成和组件回归——任务设计

任务编号：`M11-T05`

对应任务：[M11-T05](../superpowers/plans/tensor-modules/M11-download-ui.md#task-m11-t05-downloadview-集成20h)

实施产物：把 M11-T01～T04 的受控组件与 `useDownloadFlow()` 组合成可完成一次下载操作的 `/downloads` 页面，并维护真实应用壳回归测试

## Goal

在已完成的数据源/API 选择、动态参数表单、下载流程和最终结果组件之上，替换 M10-T02 的下载页占位主体。页面挂载后自动加载数据源，按“来源 → 接口 → 说明 → 参数 → 动作 → 结果”顺序组合现有合同，在提交前执行字段校验，在请求期间锁定全部相关控件，并正确区分元数据加载、初始引导、成功、合法空结果和失败。

本任务只负责页面组合。状态、竞态、响应、错误和原参数重试仍由 M11-T03 唯一管理；控件、校验和结果呈现仍由各子组件负责。`/downloads` 路由已由 M10-T02 正确注册并由现有 router 测试覆盖，不为满足旧任务卡的 `Modify` 标记制造无行为价值的 router 改动。

## Scope

包含：

- 修改 `DownloadView.vue`，保留稳定页面标题并挂载 M11-T01～T04 的六个下载组件；
- 页面 `onMounted` 时调用一次 `useDownloadFlow().load()`，由单来源选择器的既有事件继续触发接口元数据加载；
- 用 M10-T04 `AsyncStatePanel` 呈现下载配置加载、下载配置失败和无最终结果时的初始引导；
- 只在真实下载的 `SUCCESS | EMPTY | FAILURE` 最终态挂载 M11-T04 `DownloadResult`；
- 把选择事件、参数表单 `validate()`/`normalizedValues()`、下载 `submit()` 和无参 `retry()` 接到唯一 `useDownloadFlow()`；
- 创建 `DownloadView.spec.js`，以真实子组件与 composable、仅在 HTTP API 边界使用受控 mock，覆盖八项页面集成行为；
- 修改 `App.spec.js` 与 `AppLayout.spec.js`，在真实路由/布局测试中隔离自动元数据请求，并把旧占位文案断言更新为已完成下载页面的稳定表面；
- 在 Node.js 24.15.0 下执行严格 RED、页面/壳层聚焦测试、完整前端单测和生产构建。

排除：

- 不修改 `router/index.js` 或 `router/index.spec.js`；项目所有者于 2026-09-05 批准保留既有 `/`→`/downloads`、named `downloads` 和 `DownloadView` 绑定；
- 不修改 M10/M11 已完成的 API、composable、共享组件、下载子组件、工具、依赖、配置、布局生产代码或全局样式；
- 不在页面复制响应式状态、generation、retryable、错误码、参数规则、计数或元数据 schema；
- 不直接调用 Axios、fetch 或后端 URL，不硬编码 Tushare、fixture、49 个 API、具体 `pluginId`、`apiName` 或参数名；
- 不自动选择接口，不绕过参数表单公开方法，不从当前表单重建“原参数重试”；
- 不增加阶段、进度、百分比、取消、自动重试、队列、历史、全局 store 或跨路由/刷新持久化；
- 不显示 `fieldErrors`、原始响应、请求正文、Header、Token、Cookie、SQL、stack、cause、config 或内部路径；
- 不使用 `v-html`、`innerHTML`、Element Plus 内部类名或新增第三方依赖。

## Approach

### 稳定路由和页面公开表面

`DownloadView` 仍是无 props、无 emits 的路由页面，根节点保持：

```text
section.page[aria-labelledby="downloads-title"]
└── h1#downloads-title  数据下载
```

`control-plane/src/router/index.js` 当前已把 `/downloads` 的 named route `downloads` 静态绑定到该组件，`/` 也已重定向到该 route；`router/index.spec.js` 已覆盖这两条合同。因此本任务只验证该文件相对当前基线无差异，不改变路由名、路径、导入方式、history factory、导航或 404。

页面在 `<script setup>` 中创建一次 `useDownloadFlow()`，使用 `ref(null)` 保存唯一动态参数表单组件引用，并在 `onMounted(load)` 时自动启动数据源元数据加载。页面不导出额外状态或动作，也不创建第二个 composable 实例。

### 组件顺序、锁定和选择

标题后按以下固定 DOM 顺序组合：

1. `DataSourceSelect`：`modelValue=selectedPluginId`、`sources=sources`、`disabled=locked`，更新事件只调用 `selectSource(pluginId)`；元数据加载期间仍允许切换来源，以保留 M11-T03 generation 合同。
2. `ApiSelect`：`modelValue=selectedApiName`、`apis=apis`，只有当前来源 `downloadAvailable === true` 且未 `locked` 时可用；更新事件只调用 `selectApi(apiName)`。
3. `ApiDescription`：只接收 `selectedApi`；无选择时由组件自行不渲染。
4. `DynamicParameterForm`：只在 `selectedApi !== null` 时挂载，`parameters=selectedApi.parameters`、`disabled=locked`，并登记为页面唯一表单 ref。
5. `DownloadAction`：始终渲染，`disabled=!canSubmit`、`submitting=locked`，`submit` 事件调用页面提交处理器。
6. 状态/结果区域：按下节规则只挂载一个面板或结果组件；`SUBMITTING` 时不挂载阶段或进度面板。

来源或接口切换不直接调用表单 `reset()`：M11-T03 在接受切换时同步清除下游选择、结果、错误和下载重试上下文；`selectedApi` 变为 null 会卸载旧表单，选择新接口后以新 `parameters` 挂载全新表单。提交期间子组件禁用与 composable 动作拒绝共同防止竞态和重复请求。

### 非最终状态与元数据失败

状态区域使用固定优先级：

- `state === 'METADATA_LOADING'`：`AsyncStatePanel state="LOADING"`，标题“正在加载下载配置”，说明“请稍候。”；它只表达来源/API 元数据加载，不暗示下载、适配或入库阶段。
- `state === 'FAILURE' && selectedApiName === ''`：这是数据源或接口元数据失败，使用 `AsyncStatePanel state="FAILURE"`，标题“下载配置加载失败”，`message=error.message`。非空 `error.requestId` 以“请求 ID：<值>”放入 actions；`canRetry` 为 true 时同一区域显示原生 button 类型的 Element Plus 按钮“重新加载”，点击只调用无参 `retry()`。
- `state !== 'SUBMITTING'` 且尚无下载最终结果：使用 `AsyncStatePanel state="INITIAL"`。无 `selectedApi` 时标题“请选择数据接口”、说明“选择接口后填写参数并开始下载。”；已有 `selectedApi` 时标题“填写参数并开始下载”、说明“提交后将在此显示本次下载结果。”。
- `state === 'SUBMITTING'`：结果区域为空；固定“开始下载”按钮的 loading、disabled 和 `aria-busy` 是唯一进行中反馈，不显示“下载中”“适配中”“入库中”、阶段、进度或百分比。

元数据失败面板只读取 M10 安全错误的 `message` 和可选 `requestId`，重试可见性只服从 M11-T03 的 `canRetry`。页面不读取 `error.retryable`、code、kind 或 fieldErrors，也不将元数据失败传给标题固定为“下载失败”的 `DownloadResult`。

### 提交、最终结果和重试

页面提交处理器固定为：若表单 ref 不存在则返回；否则先 `await parameterForm.validate()`，失败时立即返回且不调用下载；成功时立刻读取一次 `parameterForm.normalizedValues()` 并 `await submit(snapshot)`。页面不缓存、修改或记录该对象。

最终结果条件固定为：

```text
state === SUCCESS
state === EMPTY
state === FAILURE && selectedApiName !== ''
```

满足时只挂载一个 `DownloadResult`，原样传入 `state`、`result`、`error` 和 `canRetry`，并把 `retry` 事件直接连接到 M11-T03 的无参 `retry()`。SUCCESS/EMPTY 文案和计数、FAILURE 安全摘要/请求 ID/重试按钮均由 M11-T04 负责；页面不按计数或错误类型重判结果。下载失败后表单继续挂载且保留显示值；“使用原参数重试”消费 M11-T03 的冻结快照，用户修改当前表单后重新提交则再次走页面校验。

### 回归测试隔离

`DownloadView.spec.js` 只 mock `dataSources.js` 的 `listDataSources`/`listApis` 和 `downloads.js` 的 `downloadDataset`，真实挂载 `DownloadView`、M11-T01～T04 子组件、`useDownloadFlow()`、`useParameterForm()`、Element Plus 与 `AsyncStatePanel`。mock 返回完整 `DataSourceSummary`、`ApiDescriptor`、`DownloadResponse` 或真实 M10 安全错误形状；测试断言页面可见行为、子组件公开 props/emits、请求参数与焦点，不断言 mock DOM 或 Element Plus 私有 class。

`App.spec.js` 和 `AppLayout.spec.js` 各自在文件级 mock 数据源 API，使真实 router 挂载下载页时得到确定性的空来源成功结果且不触网；不 mock `DownloadView`、router、layout 或下载子组件。`App.spec.js` 继续验证唯一 header/nav/main/h1，并新增已完成页面的受控下载按钮断言；`AppLayout.spec.js` 保留三项路由、焦点、404 和对比度检查，只把旧“模块尚未完成”文案断言改为下载页初始引导/禁用动作且确认旧文案消失。两个文件的测试数量不变。

## Files

创建：

- `control-plane/src/views/DownloadView.spec.js`：8 项真实页面组合、状态、提交、重试、切换和键盘顺序测试。

修改：

- `control-plane/src/views/DownloadView.vue`：自动加载、六段组件组合、非最终状态、校验提交和最终结果接线；
- `control-plane/src/App.spec.js`：隔离下载页自动元数据请求，并验证根应用接入已完成页面；
- `control-plane/src/layouts/AppLayout.spec.js`：隔离自动请求并将下载导航回归从旧占位文案迁移到新页面稳定表面。

不修改或删除其他文件。特别是 `control-plane/src/router/index.js` 与 `control-plane/src/router/index.spec.js` 保持当前已验证内容；项目所有者于 2026-09-05 批准上述四文件范围。实现提交固定为 `feat(ui): complete data download page`，精确包含一新增、三修改文件；本设计、看板和交接不得混入实现提交。

## Tests

所有命令从仓库根目录开始，并在 Node.js 24.15.0 下运行：

```bash
source /Users/qiangzhiwei/.nvm/nvm.sh
nvm use 24.15.0
cd control-plane
node --version
```

预期输出 `v24.15.0`。M11-T04 完成后的基线为 12 个测试文件、67 项测试；Vite 8.2.2 构建转换 1599 modules 并退出 0，只含既有 Element Plus chunk-size 提示。

严格 RED：先完整创建 `DownloadView.spec.js`，并只修改 `App.spec.js`、`AppLayout.spec.js` 的 API 隔离和新页面断言，不修改 `DownloadView.vue`，然后运行：

```bash
npm run test:unit -- --run \
  src/views/DownloadView.spec.js \
  src/App.spec.js \
  src/layouts/AppLayout.spec.js
```

预期命令非零；失败只来自现有占位 `DownloadView` 缺少自动加载、组件、按钮、状态和结果行为。不得出现 mock 提升、测试语法、Vue/Element Plus、router、setup、未处理 rejection 或真实网络错误；不得提交 RED 检查点。

GREEN 固定为新页面 8 项：

1. 页面保留唯一“数据下载”标题，挂载即调用一次来源 API；pending 时礼貌显示“正在加载下载配置”，retryable 元数据失败时以 alert 纯文本显示安全摘要、请求 ID 和“重新加载”，点击后只重试同一元数据操作并恢复初始引导，旧占位文案不存在；
2. 单一可用来源由真实 `DataSourceSelect` 默认发出 ID，页面只调用一次对应 `listApis`，把 49 个完整、唯一描述符原样交给 `ApiSelect`；未选接口时动作禁用并显示“请选择数据接口”；
3. 选择接口后按固定 DOM 顺序显示接口说明、对应动态参数和动作；空必填值点击动作只产生字段错误/首错焦点，不调用 `downloadDataset`；
4. 合法参数提交当前来源、接口和规范化快照；pending 期间来源、接口、参数和动作全部锁定，按钮固定文案、loading/`aria-busy` 成立且无阶段/进度；成功后解锁并显示本次上游返回数、插入数和更新数；
5. `outcome=EMPTY` 原样进入空结果，精确显示“下载成功，0 条数据”及固定说明，不显示计数、失败、重试或占位记录；
6. 下载拒绝显示 M10 安全摘要和请求 ID；只有页面传入的 `canRetry` 为 true 时显示“使用原参数重试”，点击后调用 composable 无参重试并使用失败时冻结的参数，页面不读取当前已修改表单或错误内部字段；
7. 接口切换同步清除旧成功/空/失败结果并卸载旧参数，随后以新描述符挂载空的新表单；来源切换同时清除接口、表单和结果，并只加载新来源接口；
8. 真实数据源 combobox、接口 combobox、首个动态参数 input 和原生下载 button 按 DOM 顺序均可获得焦点；真实 `ApiSelect` 可用 ArrowDown/Enter 选择接口，下载按钮保持 `type="button"` 且可由键盘聚焦，不依赖 Element Plus 私有结构。

运行：

```bash
npm run test:unit -- --run \
  src/views/DownloadView.spec.js \
  src/App.spec.js \
  src/layouts/AppLayout.spec.js
npm run test:unit -- --run
npm run build
```

预期聚焦为 3 files / 12 tests、完整前端为 13 files / 75 tests，全部通过且无未处理 rejection、console 或 Vue warning；Vite 构建退出 0，只允许既有 Element Plus chunk-size 提示。现有 `router/index.spec.js` 的 3 项稳定路由测试必须继续包含在全量通过结果中。

从仓库根目录运行：

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'v-html|innerHTML|axios|fetch\(|ApiError|ClientError|fieldErrors|stack|cause|config|response|Authorization|Cookie|token|password|localStorage|sessionStorage|setInterval|setTimeout|progress|percent|下载中|适配中|入库中|apiName\s*===|pluginId\s*===' \
  control-plane/src/views/DownloadView.vue
git diff -- \
  control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/src/api control-plane/src/composables \
  control-plane/src/components control-plane/src/utils \
  control-plane/src/router control-plane/src/layouts/AppLayout.vue \
  control-plane/src/style.css
```

预期格式检查退出 0；status 精确显示 Files 节的一新增、三修改文件；HTML 注入、直连网络、错误解释、原始传输字段、凭证、持久化、定时器、阶段、进度和具体来源/API 分支扫描无输出并退出 1；受保护路径无差异。暂存后 `git diff --cached --name-status` 精确显示同一四文件集合，提交消息与 Files 节固定值一致。

## Acceptance

- `/downloads` 保持 M10-T02 已验证的 route name、路径、重定向和应用壳接入，router 文件无差异；页面标题仍为“数据下载”，旧未完成占位文案完全移除；
- 页面只创建一个 `useDownloadFlow()` 并在挂载时加载来源；单来源默认选择后自动加载其 API，49 个描述符由元数据原样驱动，无来源、接口或参数硬编码；
- 六段组件按来源、接口、说明、参数、动作、结果顺序组合，选择、禁用、表单 ref、提交和重试只通过各依赖批准的公开 props/emits/actions 连接；
- 元数据加载、元数据失败和初始引导使用 M10 `AsyncStatePanel`；元数据失败不伪装为下载失败，安全摘要、可用请求 ID 和 `canRetry` 控制的重新加载可用；
- 未选择接口时不显示参数表单且下载按钮禁用；提交前必须 await `validate()`，失败时不请求，成功时立即提交唯一的新鲜 `normalizedValues()` 快照；
- `SUBMITTING` 期间来源、接口、参数和动作全部锁定，只有固定按钮 loading/`aria-busy`，无阶段、进度、百分比或取消；
- 只把真实下载 `SUCCESS | EMPTY | FAILURE` 原样传给 `DownloadResult`，不按计数/错误重判；失败后的原参数重试只调用 M11-T03 `retry()`，当前表单仍可修正后重新校验提交；
- 来源或接口切换清除旧下游选择、参数、校验、最终结果、错误和重试上下文，不跨路由或刷新保留页面状态；
- App 与 AppLayout 回归不触网、不 mock 页面/router/layout，继续覆盖真实壳层、导航、焦点、404 与新下载页入口；
- Node.js 24.15.0 下严格 RED 原因正确，页面/壳层聚焦 12/12、完整前端 75/75、稳定 router 3/3 和生产构建达到预期，且只有既有 chunk-size 提示；
- 实现提交精确包含 Files 节四个文件，不修改依赖、配置、API、composable、子组件、工具、router、布局生产代码、样式或后端合同。

## Risks

- 页面用 `state === 'FAILURE' && selectedApiName === ''` 区分元数据失败与下载失败，依赖 M11-T03 在 `load()`/`selectSource()` 开始时同步清空 API 选择、在下载失败时保留当前 API。该合同已由 M11-T03 设计和测试冻结；若未来状态接口改变，页面分支与测试必须同步修订。
- 单来源 `DataSourceSelect` 在挂载/来源数组更新后立即发出默认选择，页面测试必须用 `flushPromises()` 消费相继发生的来源和 API 请求，不能用任意 timeout 等待。
- Element Plus select/date picker 可能 Teleport 内容；焦点与键盘测试使用公开 combobox/input/button 和组件事件，并在 `finally` 中 unmount，不依赖内部 class 或残留 `document.body`。
- `App.spec.js`、`AppLayout.spec.js` 的数据源 mock 只隔离页面自动加载副作用；若 mock 整个页面或 router，测试将失去真实应用壳回归价值，属于不满足本设计。
