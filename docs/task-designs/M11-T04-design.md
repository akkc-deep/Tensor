# M11-T04 成功、空和失败结果组件——任务设计

任务编号：`M11-T04`

对应任务：[M11-T04](../superpowers/plans/tensor-modules/M11-download-ui.md#task-m11-t04-下载结果反馈15h)

实施产物：受控下载按钮、只呈现最终下载结果的可访问组件，以及覆盖三种结果和锁定边界的组件测试

## Goal

在 M10-T04 无业务状态的可访问面板与 M11-T03 下载流程状态之上，交付 `DownloadAction` 和 `DownloadResult`。前者提供不会绕过页面锁定的唯一下载触发入口；后者把最终 `SUCCESS | EMPTY | FAILURE` 明确呈现为成功计数、合法空结果或安全失败信息，使 M11-T05 只需传入流程状态并绑定 `submit`/`retry` 事件即可完成页面组合。

本任务只呈现按钮和最终结果，不拥有选择、参数、请求、generation、错误归一化或重试策略。提交期间只显示按钮 loading/禁用语义，不展示下载、适配、入库阶段、进度或百分比。

## Scope

包含：

- 创建 `DownloadAction.vue`，接收调用方的禁用和提交状态，渲染固定“开始下载”按钮并发出受控 `submit` 事件；
- 创建 `DownloadResult.vue`，接收最终状态、下载响应、安全错误和调用方计算的重试能力；
- `SUCCESS` 以礼貌 live region 展示“下载成功”以及本次上游返回数、插入数和更新数；
- `EMPTY` 复用 M10-T04 `AsyncStatePanel` 展示精确标题“下载成功，0 条数据”；
- `FAILURE` 复用 `AsyncStatePanel` 展示“下载失败”、M10 安全错误摘要、可用请求 ID，并仅在 `canRetry` 时提供“使用原参数重试”；
- 所有动态文本使用 Vue 文本插值，结果组件不解释错误 code、fieldErrors 或原始 Axios 数据；
- 创建一个同目录测试文件，以真实 `AsyncStatePanel` 和 Element Plus 按钮覆盖按钮、三种结果、重试和安全边界；
- 在 Node.js 24.15.0 下执行严格 RED、聚焦测试、完整前端单测和生产构建。

排除：

- 不修改 M10/M11 已完成的 API、共享组件、选择组件、参数表单或 composable，不修改依赖、配置、路由、布局、页面、样式、Java、YAML、OpenAPI 或 PRD；
- 不调用 `listDataSources`、`listApis`、`downloadDataset`、Axios、fetch 或任何网络边界；
- 不接收表单引用，不执行参数校验，不保存参数、选择、响应、错误或重试快照；
- 不根据 `error.code`、`error.kind`、`error.retryable`、来源或接口名称决定文案或重试；`canRetry` 是唯一重试显示输入；
- 不显示成功响应的 `message`、pluginId、apiName、成功 requestId、结果数据预览或历史记录；
- 不显示失败 fieldErrors、stack、cause、config、response、请求正文、Header、Token、Cookie、SQL 或内部路径；
- 不自动重试，不实现取消、定时器、退避、队列、进度条、百分比、阶段状态或跨页面持久化；
- 不使用 `v-html`、`innerHTML`、Element Plus 内部类名或新增第三方依赖。

## Approach

### 项目所有者批准的公开接口

项目所有者于 2026-09-04 批准以下最小接口：

```text
DownloadAction
props:
  disabled: boolean = false
  submitting: boolean = false
emits:
  submit

DownloadResult
props:
  state: 'SUCCESS' | 'EMPTY' | 'FAILURE'（必填）
  result: DownloadResponse | null = null
  error: ApiError | ClientError | null = null
  canRetry: boolean = false
emits:
  retry
```

`DownloadAction` 和 `DownloadResult` 都是受控组件，不复制 M11-T03 状态。M11-T05 负责把 `!canSubmit` 与 `locked` 传给前者，把最终状态、`result`、`error` 和 `canRetry` 传给后者，并将两个事件分别连接到表单校验后的 `submit(params)` 与 M11-T03 的无参 `retry()`。

`DownloadResult` 的调用合同要求：`SUCCESS` 时 `result` 是 M11-T03 原样保留的 `DownloadResponse`，`FAILURE` 时 `error` 是 M10-T03 已归一化并由 M11-T03 原样保留的安全错误；`EMPTY` 不读取响应字段。组件不复制 OpenAPI 或错误运行时校验，也不为调用方合同违例伪造成功计数或错误摘要。

### 下载动作

`DownloadAction` 只渲染一个 `el-button`：`type="primary"`、`native-type="button"`，可见文本始终为“开始下载”。`submitting` 绑定 Element Plus `loading` 并设置原生 `aria-busy`；有效禁用值为 `disabled || submitting`。保持固定文案可避免把一次同步请求误画成“下载中/适配中/入库中”等阶段状态，同时 loading 与锁定仍向用户说明主操作暂不可用。

点击处理器仅在 `disabled === false` 且 `submitting === false` 时发出一次 `submit`。即使测试或调用方直接触发组件事件处理器，禁用和提交期间也不能发出事件；组件不接收回调 prop、不自行调用 Promise，也不在提交结束后改变自身状态。

### 三种最终结果

`DownloadResult` 的 `state` 使用 Vue prop validator 限定为 `SUCCESS | EMPTY | FAILURE`。M11-T05 只在这三种最终状态挂载该组件；`INITIAL`、`METADATA_LOADING`、`READY` 和 `SUBMITTING` 的引导/加载布局不属于本组件。

- `SUCCESS`：渲染原生 `<section role="status" aria-live="polite">`，标题固定“下载成功”，以语义化 `<dl>` 依次显示“上游返回数”“插入数”“更新数”，值分别直接来自 `sourceRowCount`、`insertedRows`、`updatedRows`。不按计数重判状态，不计算总数，不显示 `result.message`。
- `EMPTY`：渲染 `AsyncStatePanel state="EMPTY"`，标题精确为“下载成功，0 条数据”，说明固定为“本次请求没有可写入的数据。”；不显示三个计数、占位记录、预览或重试入口。
- `FAILURE`：渲染 `AsyncStatePanel state="FAILURE"`，标题固定“下载失败”，`message` 直接使用 `error.message`。非空 `error.requestId` 以“请求 ID：<值>”显示在面板 actions 区；`canRetry === true` 时同一区域显示 `el-button`“使用原参数重试”，点击只发出一次 `retry`。`canRetry === false` 时不显示该按钮，组件不读取或复算 `error.retryable`。

`AsyncStatePanel` 已保证 EMPTY 的 `role="status"`/`aria-live="polite"` 与 FAILURE 的 `role="alert"`；SUCCESS 由本组件提供对应礼貌播报。错误摘要、请求 ID 和所有计数都用文本插值，不使用 HTML 注入。结果组件只读取上述批准字段；错误对象的其他字段和成功响应 message 即使存在也不进入 DOM。

### 数据流与失败边界

数据流固定为：M11-T05 用户点击 → `DownloadAction` 发出 `submit` → 页面校验并调用 M11-T03；请求完成后，页面按 M11-T03 最终状态挂载 `DownloadResult`。原参数重试为 `DownloadResult` 发出 `retry` → 页面调用 M11-T03 `retry()`；组件不读取当前表单，也不持有失败参数。

M10-T03 已把服务端和客户端失败归一化为安全 `ApiError`/`ClientError`，M11-T03 已保证只保留该对象并由 `canRetry` 表达最近操作是否可重试。本任务因此不建立错误码到文案的第二张表，不修改错误摘要，不展开 fieldErrors 或原始传输对象。缺失 requestId 时只省略请求 ID 行，仍显示安全摘要；非 retryable 失败仍允许用户回到页面修正选择或参数后重新提交，但结果组件不显示原参数重试。

## Files

创建且只创建以下三个文件：

- `control-plane/src/components/download/DownloadAction.vue`：受控开始下载按钮、提交 loading/禁用与 `submit` 事件；
- `control-plane/src/components/download/DownloadResult.vue`：SUCCESS/EMPTY/FAILURE 最终结果、安全请求 ID 和可选原参数重试；
- `control-plane/src/components/download/DownloadResult.spec.js`：两个组件的 6 项公开行为测试。

不修改或删除其他生产、测试、依赖、配置和文档文件。实现提交固定为 `feat(ui): show final download outcomes`，精确包含上述三个新增文件；本设计、看板和交接不得混入实现提交。

## Tests

所有命令从仓库根目录开始，并把 Node.js 24.15.0 放在 PATH 首位：

```bash
export PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH"
cd control-plane
node --version
```

预期输出 `v24.15.0`。M11-T03 完成后的前端基线为 11 个测试文件、61 项测试。

严格 RED：先完整创建 `control-plane/src/components/download/DownloadResult.spec.js`，不创建两个生产组件，然后运行：

```bash
npm run test:unit -- --run src/components/download/DownloadResult.spec.js
```

预期命令非零，套件在收集阶段只因 `./DownloadAction.vue` 或 `./DownloadResult.vue` 不存在而失败；不得出现测试语法、依赖、Element Plus、Vue SFC、setup 或既有测试失败。不得提交 RED 检查点。

GREEN 固定为 1 file / 6 tests：

1. 默认动作按钮可见文本为“开始下载”、原生类型为 button，未禁用且未提交时每次点击只发出一次 `submit`；
2. 外部 `disabled` 或 `submitting` 均使动作按钮不可用且不发事件；提交时 loading 与 `aria-busy` 成立，文本仍为“开始下载”，不出现阶段或进度文案；
3. `SUCCESS` 使用礼貌 live region，显示“下载成功”及本次响应的上游返回数、插入数、更新数，保持数字原值且不显示 response message、预览或请求阶段；
4. `EMPTY` 使用 M10 `AsyncStatePanel` 的礼貌状态语义，精确显示“下载成功，0 条数据”和固定说明，不显示成功计数、失败、重试或占位记录；
5. retryable `FAILURE` 使用 alert 语义，把形似 HTML 的安全摘要显示为纯文本，显示非空请求 ID；“使用原参数重试”点击发出一次 `retry`，但组件不读取 error code/kind/fieldErrors；
6. 非 retryable `FAILURE` 在 requestId 为 null 时仍显示安全摘要，但不渲染请求 ID 或重试按钮；全部状态均不泄漏未消费字段、Token/原始传输详情，不出现阶段、进度或百分比。

运行：

```bash
npm run test:unit -- --run src/components/download/DownloadResult.spec.js
npm run test:unit -- --run
npm run build
```

预期聚焦为 1 file / 6 tests、完整前端为 12 files / 67 tests，全部通过且无未处理 rejection、console 或 Vue warning；Vite 构建退出 0，只允许既有 Element Plus chunk-size 提示。

从仓库根目录运行：

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'v-html|innerHTML|axios|fetch\(|ApiError|ClientError|fieldErrors|stack|cause|config|response|Authorization|Cookie|token|password|setInterval|setTimeout|progress|percent|下载中|适配中|入库中' \
  control-plane/src/components/download/DownloadAction.vue \
  control-plane/src/components/download/DownloadResult.vue
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/src/api control-plane/src/composables \
  control-plane/src/components/common \
  control-plane/src/components/download/DataSourceSelect.vue \
  control-plane/src/components/download/ApiSelect.vue \
  control-plane/src/components/download/ApiDescription.vue \
  control-plane/src/components/download/DynamicParameterForm.vue \
  control-plane/src/router control-plane/src/layouts control-plane/src/views \
  control-plane/src/style.css
```

预期格式检查退出 0；status 精确显示 Files 节三个新增文件；HTML 注入、网络、错误解释、原始传输字段、凭证、定时器、阶段和进度扫描无输出并退出 1；受保护路径无差异。暂存后 `git diff --cached --name-status` 精确显示三个新增文件，提交消息与 Files 节固定值一致。

## Acceptance

- `DownloadAction` 只接收 `disabled`/`submitting` 并发出 `submit`，固定显示“开始下载”；任一锁定条件都同时阻止交互与事件，提交时只有 loading/`aria-busy`，无阶段或进度；
- `DownloadResult` 只接收最终三态、原样响应、安全错误和 `canRetry`，发出 `retry`；不请求数据、不管理流程、不保存参数或重试策略；
- SUCCESS 礼貌播报并直接显示本次 `sourceRowCount`、`insertedRows`、`updatedRows`，不按计数重判、不计算或显示响应 message；
- EMPTY 礼貌播报并精确显示“下载成功，0 条数据”，不伪装为失败、不显示占位记录或结果预览；
- FAILURE 使用 alert，直接显示 M10 安全摘要和可用请求 ID；只有 `canRetry` 为 true 时显示“使用原参数重试”，不自行检查错误类型或 retryable；
- 动态摘要与请求 ID 均以纯文本呈现，组件不读取或显示 fieldErrors、stack、cause、config、response、请求正文、Header、Token、Cookie、SQL 或内部路径；
- 两个组件无具体来源、接口、参数或错误码分支，不自动请求/重试，不包含取消、历史、持久化、定时器、阶段、进度或百分比；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 6/6、完整前端 67/67 和生产构建达到预期，且只有既有 chunk-size 提示；
- 实现提交精确包含 Files 节三个新增文件，不修改依赖、配置、API、composable、共享/既有下载组件、路由、页面、样式、Java、YAML 或契约。

## Risks

- `DownloadResult` 依赖 M11-T05 只在最终三态挂载，并保证 SUCCESS 有非空 result、FAILURE 有非空 error；本任务按已完成 M11-T03 合同消费，不复制第二套运行时校验。页面集成必须保持这一前置条件。
- `AsyncStatePanel` 的 FAILURE 在挂载时立即形成 alert；M11-T05 不得预创建并隐藏失败组件，否则屏幕阅读器可能播报并不存在的失败。
- Element Plus loading 可能改变按钮内部 DOM。测试只断言公开 `loading`/`disabled` props、原生按钮语义、可见文本和组件事件，不依赖内部 class 或 spinner 结构。
- requestId 对服务端 `ApiError` 必有值，但浏览器侧 `ClientError` 可能为 null；缺失时省略该行是批准的安全降级，不生成占位 ID。
