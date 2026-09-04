# M11-T03 下载 composable、控件锁定和请求世代——任务设计

任务编号：`M11-T03`

对应任务：[M11-T03](../superpowers/plans/tensor-modules/M11-download-ui.md#task-m11-t03-下载状态与竞态控制30h)

实施产物：下载页唯一的内存态异步流程 composable，以及覆盖元数据加载、选择、提交、重试和 stale response 的单元测试

## Goal

在 M10-T03 安全 API 客户端之上交付 `useDownloadFlow`，统一管理下载页的数据源/API 元数据、当前选择、最终下载结果、安全错误、提交锁定和请求世代。页面组合层只需调用明确动作并消费响应式状态，不再自行拼接异步流程、判断竞态或保存重试参数。

本任务只建立浏览器内存中的页面流程状态，不建立后端任务状态机，不显示下载、适配、入库阶段或进度，也不跨路由或刷新持久化。M11-T02 的参数表单仍拥有显示值和本地校验；M11-T05 必须先验证表单并取得规范化快照，再传给本任务的 `submit(params)`。

## Scope

包含：

- 创建 `useDownloadFlow.js`，直接调用 M10-T03 的 `listDataSources`、`listApis` 和 `downloadDataset`；
- 暴露任务卡冻结的 `INITIAL | METADATA_LOADING | READY | SUBMITTING | SUCCESS | EMPTY | FAILURE` 七种页面状态；
- 管理数据源/API 列表、当前来源/API、派生的选中描述符、最终响应和 M10 安全错误；
- 数据源切换后加载其 API，来源或 API 切换时清除旧的下游选择、结果、错误和下载重试快照；
- 下载期间锁定来源、API、参数和提交入口，并拒绝重复提交；
- 使用单调递增 generation，使被后续加载或选择取代的较早响应和失败均无法覆盖当前状态；
- 按项目所有者 2026-09-04 批准的“操作感知重试”方案，`retry()` 重试最近一次可重试的失败操作：来源元数据、当前来源的 API 元数据或最后一次下载请求；
- 下载失败时保存调用时参数的浅复制快照供原参数重试，且不修改调用方对象；
- 创建一个同目录测试文件，以受控 M10 API mock 覆盖状态、调用、锁定、重试和竞态；
- 在 Node.js 24.15.0 下执行严格 RED、聚焦测试、完整前端单测和生产构建。

排除：

- 不修改 M10 API、M11-T01/M11-T02 组件或 composable、依赖、配置、路由、布局、页面、样式、Java、YAML、OpenAPI 或 PRD；
- 不接收表单组件引用，不执行必填、格式、范围或服务端字段错误回填；
- 不渲染按钮、状态面板、结果、错误文案、请求 ID 或重试提示；这些属于 M11-T04/M11-T05；
- 不解释、包装、记录或展开 `ApiError`/`ClientError`，只保留 M10 已归一化的安全对象；
- 不自动重试，不实现退避、定时器、取消、AbortController、队列、并行下载、进度、历史或跨页面持久化；
- 不硬编码 Tushare Pro、fixture、49 个 API、具体 `apiName`、参数名或错误码；
- 不在 composable 中读取 Token、Authorization、Cookie、localStorage、sessionStorage 或请求正文日志。

## Approach

### 公开接口

`control-plane/src/composables/useDownloadFlow.js` 只导出具名 `useDownloadFlow()`，无参数并直接消费 M10-T03 模块。返回以下 refs、computed 和动作：

```text
refs:
  state
  sources
  apis
  selectedPluginId
  selectedApiName
  result
  error
computed:
  selectedSource
  selectedApi
  locked
  canSubmit
  canRetry
actions:
  load(): Promise<boolean>
  selectSource(pluginId: string): Promise<boolean>
  selectApi(apiName: string): boolean
  submit(params: Record<string, string>): Promise<boolean>
  retry(): Promise<boolean>
```

`selectedSource` 和 `selectedApi` 分别按当前 ID 从已加载数组查找，找不到时为 `null`。`locked` 只在 `SUBMITTING` 为 `true`。`canSubmit` 只在选中来源的 `downloadAvailable === true`、选中 API 确实存在、状态不是 `METADATA_LOADING`/`SUBMITTING` 时为 `true`；因此下载失败后仍可用修正后的表单参数重新 `submit`。`canRetry` 只在 `FAILURE`、存在最近失败上下文且 `error.retryable === true` 时为 `true`。

异步动作只在当前 generation 成功落地时返回 `true`；API 拒绝、被更新请求取代、锁定、无有效选择或无可重试操作均返回 `false`。`selectApi` 在接受选择并完成同步重置时返回 `true`，提交锁定时返回 `false`。调用方不依赖动作返回 DTO，成功结果和错误统一从 `result`/`error` 读取。

### 元数据加载和选择

初始值固定为：`state = INITIAL`，两个列表为空，两个选择为空字符串，`result = null`，`error = null`。不在 composable 构造时自动请求。

`load()` 在未锁定时开启新 generation，清空列表、选择、结果、错误和重试上下文，进入 `METADATA_LOADING` 并调用一次 `listDataSources()`。当前 generation 成功后原样保存返回数组并进入 `READY`；失败则保留 M10 安全错误、记录 `SOURCES` 失败上下文并进入 `FAILURE`。本任务不自动选择单一来源；M11-T01 的 `DataSourceSelect` 继续发出默认选择，再由 M11-T05 调用 `selectSource`。

`selectSource(pluginId)` 在未锁定时开启新 generation，保存字符串 ID，清空 API 选择、API 列表、结果、错误和下载重试快照。空 ID 直接进入 `READY` 且不请求；非空 ID 进入 `METADATA_LOADING` 并调用一次 `listApis(pluginId)`。当前 generation 成功后原样保存 API 数组并进入 `READY`；失败保存安全错误、记录带当前 pluginId 的 `APIS` 失败上下文并进入 `FAILURE`。来源是否可下载只影响 `canSubmit`；本任务不复制插件 readiness 校验，也不修改返回描述符。

`selectApi(apiName)` 在未锁定时开启新 generation，保存字符串 ID，清空结果、错误和下载重试快照并进入 `READY`。未知或空 ID 不抛异常；`selectedApi` 为 `null` 且 `canSubmit` 为 `false`，由既有选择器约束正常交互。

### 下载、结果和锁定

`submit(params)` 先检查 `locked` 与 `canSubmit`；不满足时返回 `false` 且不调用 API。接受提交时按当前 `selectedPluginId`、`selectedApiName` 和 `{...params}` 构造一次请求快照，开启新 generation，清空旧结果/错误，进入 `SUBMITTING`。`sources`、`apis` 和表单值不被清空；M11-T05 把 `locked` 透传给所有控件和按钮。

当前 generation 的 `downloadDataset(request)` 成功后原样保存响应：`outcome === 'SUCCESS'` 进入 `SUCCESS`，`outcome === 'EMPTY'` 进入 `EMPTY`。闭集由 M10/OpenAPI 保证，本任务不根据计数重新判定、不改写 message，也不显示结果。失败时保存原 `ApiError`/`ClientError` 对象、记录包含冻结选择和浅复制 params 的 `DOWNLOAD` 失败上下文并进入 `FAILURE`。请求结束即离开 `SUBMITTING`，页面控件恢复可用；任何失败都不会自动重试。

提交锁定期间 `load`、`selectSource`、`selectApi`、第二次 `submit` 和 `retry` 全部返回 `false`，不改变选择、generation 或在途请求。这样 UI 禁用与动作边界共同防止重复提交和结果错配。

### 操作感知重试

内部失败上下文闭集为 `SOURCES | APIS | DOWNLOAD`，不作为公开状态返回：

- `SOURCES`：`retry()` 重新执行 `load()`；
- `APIS`：仅当保存的 pluginId 仍等于当前选择时，重新执行该来源的 API 加载；
- `DOWNLOAD`：仅当保存的 pluginId/apiName 仍等于当前选择时，以保存的 params 新副本重新执行 `submit()`。

只有 `canRetry === true` 才执行；非 retryable 错误、选择已变化或没有失败上下文时返回 `false` 且不请求。用户修正参数后由 M11-T05 重新校验并调用 `submit(newParams)`；结果区的“原参数重试”才调用 `retry()`。重试不读取当前表单，也不把保存快照暴露给调用方。

### 请求世代和失败边界

composable 内部维护从 0 开始的整数 generation。每次被接受的 `load`、来源选择、API 选择和提交都先递增并捕获当前值；异步完成时只有捕获值仍等于当前值才能写入 refs。来源/API 快速切换会使早先元数据成功或失败变为 stale；stale 分支只消费 Promise 结果并返回 `false`，不得改变列表、选择、状态、结果、错误或重试上下文，也不得产生未处理 rejection。

M10-T03 已保证成功 DTO 未改写且失败对象安全归一化。本任务不捕获原始 Axios config/response/cause，不记录日志，不把 params、错误字段、凭证或请求正文复制到公开错误。所有状态随组件卸载自然释放，无全局 store 或模块级可变状态。

## Files

创建且只创建以下两个文件：

- `control-plane/src/composables/useDownloadFlow.js`：七态、元数据/选择、提交锁定、generation、结果/错误和操作感知重试；
- `control-plane/src/composables/useDownloadFlow.spec.js`：使用受控 M10 API mock 的 8 项公开行为测试。

不修改或删除其他生产、测试、依赖、配置和文档文件。实现提交固定为 `feat(ui): manage download request lifecycle`，精确包含上述两个新增文件；设计、看板和交接不得混入实现提交。

## Tests

所有命令从仓库根目录开始，并把 Node.js 24.15.0 放在 PATH 首位：

```bash
export PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH"
cd control-plane
node --version
```

预期输出 `v24.15.0`。M11-T02 完成后的前端基线为 10 个测试文件、53 项测试。

严格 RED：先完整创建 `control-plane/src/composables/useDownloadFlow.spec.js`，不创建生产 composable，然后运行：

```bash
npm run test:unit -- --run src/composables/useDownloadFlow.spec.js
```

预期命令非零，套件在收集阶段只因 `./useDownloadFlow.js` 不存在而失败；不得出现测试语法、mock、依赖、Node、setup 或既有测试失败。不得提交 RED 检查点。

GREEN 固定为 1 file / 8 tests：

1. 初始状态无自动请求；`load()` 精确执行一次来源加载，覆盖 `INITIAL -> METADATA_LOADING -> READY`，原样保存来源并清空旧页面状态；
2. `selectSource` 清空下游状态并加载所选来源 API，`selectApi` 更新当前 ID；只有存在对应描述符时派生选择和 `canSubmit` 才成立，不可用来源边界正确；
3. 来源或 API 元数据失败保留同一个安全错误且不自动重试；仅 retryable 错误允许 `retry()` 重做同一操作并恢复 `READY`；
4. 快速切换两个来源时，较早的 API 成功或失败均不能覆盖较新来源的列表、选择、状态、错误或重试上下文；所有拒绝均被消费；
5. 来源/API 切换清空旧下载结果、安全错误和内部下载重试快照，不修改已加载来源描述符；空来源不请求 API；
6. 合法 `submit(params)` 发送当前来源、API 和 params 副本，立即进入 `SUBMITTING`/`locked`，锁定期间全部动作和重复提交均为 no-op，调用方 params 后续修改不改变在途请求；
7. 两个合法响应分别按 `outcome` 进入 `SUCCESS`/`EMPTY` 并原样保存，不根据 counts/message 重判，也不保留旧错误；
8. 下载拒绝进入 `FAILURE` 并恢复解锁，保留同一个安全错误且不自动重试；retryable 失败的 `retry()` 使用失败时冻结的原参数和选择，可成功落地，非 retryable 或选择变化后不请求。

运行：

```bash
npm run test:unit -- --run src/composables/useDownloadFlow.spec.js
npm run test:unit -- --run
npm run build
```

预期聚焦为 1 file / 8 tests、完整前端为 11 files / 61 tests，全部通过且无未处理 rejection、console 或 Vue warning；Vite 构建退出 0，只允许既有 Element Plus chunk-size 提示。

从仓库根目录运行：

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
node --input-type=module -e 'const m=await import("./control-plane/src/composables/useDownloadFlow.js"); if (Object.keys(m).join(",") !== "useDownloadFlow") process.exit(1)'
rg -n 'setInterval|setTimeout|AbortController|localStorage|sessionStorage|Authorization|Cookie|token|password|console\.|progress|percent|apiName\s*===|pluginId\s*===' \
  control-plane/src/composables/useDownloadFlow.js
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/src/api control-plane/src/components control-plane/src/utils \
  control-plane/src/router control-plane/src/layouts control-plane/src/views \
  control-plane/src/style.css
```

预期格式和唯一导出检查退出 0；status 精确显示 Files 节两个新增文件；定时器、取消、持久化、凭证、日志、进度和具体来源/API 分支扫描无输出并退出 1；受保护路径无差异。暂存后 `git diff --cached --name-status` 精确显示两个新增文件，提交消息与 Files 节固定值一致。

## Acceptance

- `useDownloadFlow` 只导出一个无参数具名 composable，直接使用 M10-T03 三条 API，公开 refs/computed/actions 与设计一致；
- 七种状态只表示当前页面内存态；无自动请求、后端任务、阶段进度、取消、历史、定时器、持久化或全局 store；
- 来源加载、来源/API 选择、结果/错误清理和派生描述符行为确定，未知/空选择不会发起下载；
- `canSubmit` 同时要求来源可下载、API 存在且当前未加载/提交，下载失败后允许调用方以修正参数重新提交；
- 下载期间 `locked` 为 true，全部选择、加载、重试和重复提交入口均拒绝改变状态或产生第二请求；
- 成功响应严格按 OpenAPI `outcome` 映射 `SUCCESS`/`EMPTY` 并原样保留，失败原样保留 M10 安全错误且不自动重试；
- 单调 generation 使 stale 元数据成功与失败均不可覆盖较新选择，并完整消费拒绝；
- `retry()` 只重做最近一次 retryable 的来源、API 或下载失败；下载重试使用失败时参数和选择的副本，选择变化后不误用；
- composable 不修改来源、API、params 或响应对象，不解释服务端错误，不读取/显示凭证或写日志；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 8/8、完整前端 61/61 和生产构建达到预期，且只有既有 chunk-size 提示；
- 实现提交精确包含 Files 节两个新增文件，不修改依赖、配置、API、组件、共享工具、路由、页面、样式、Java、YAML 或契约。

## Risks

- generation 只忽略 stale 结果，不取消网络请求；这是首期无取消功能的明确边界，M10 的超时仍负责最终释放请求。
- `retry()` 的下载参数是提交时浅复制的字符串字典；OpenAPI 已限制值为字符串，不存在需要深复制的嵌套对象。调用方若修改表单，必须走重新校验后的 `submit(newParams)`，不能期望无参 `retry()`读取当前表单。
- `error.retryable` 只决定是否显示/执行原操作重试，不阻止用户修正选择或参数后发起新的加载/提交。
- `locked` 只代表下载提交锁；元数据加载期间由 `state === METADATA_LOADING` 和 `canSubmit === false` 控制提交，同时允许来源快速切换以验证和使用 generation。
