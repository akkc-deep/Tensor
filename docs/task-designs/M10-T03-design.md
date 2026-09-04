# M10-T03 Axios 客户端、DTO 和错误拦截——任务设计

任务编号：`M10-T03`

对应任务：[M10-T03](../superpowers/plans/tensor-modules/M10-frontend-foundation.md#task-m10-t03-api-客户端与-dto-映射20h)

实施产物：与 M00-T03 OpenAPI 一致、供 M11/M12 页面直接复用的前端 API 边界

## Goal

在 `control-plane` 中建立唯一的 Axios 实例、六个明确的业务请求函数和安全的错误归一化边界。所有请求默认访问同源 `/api/v1`，在 130 秒超时内使用前端生成的 UUID 作为 `X-Request-Id`，并保持 OpenAPI 已冻结的路径、字段大小写、动态值和精度表示不变。

完成后，M11 下载页面和 M12 数据集页面只需调用稳定的函数并处理 `ApiError` 或 `ClientError`，无需选择 HTTP 库、拼接路径、设置请求 ID、解释 Axios 原始错误或重新定义 DTO。该边界不访问后端 Java 实现，唯一业务契约来源是 `docs/contracts/openapi-v1.yaml` 与 `docs/contracts/error-codes.md`。

## Scope

包含：

- 创建一个默认 `baseURL=/api/v1`、`timeout=130000` 的 Axios 实例；
- 导出受校验的 `configureHttp({ baseURL, timeout })`，允许启动代码或测试在不创建第二个实例的前提下覆盖这两个默认值；
- 为每次请求生成新的 RFC 4122 UUID，并覆盖写入 `X-Request-Id`；
- 实现任务卡指定的 `listDataSources()`、`listApis(pluginId)`、`downloadDataset(request)`、`listDatasets(pluginId)`、`getDataset(pluginId, apiName)`、`queryDataset(pluginId, apiName, criteria)`；
- 用 JSDoc 冻结这些函数直接消费和返回的 OpenAPI DTO 形状；
- 将有效服务端错误转换为 `ApiError`，将浏览器超时、网络失败、非契约响应和本地意外失败转换为 `ClientError`；
- 使用 Axios adapter 替身覆盖全部请求和错误行为，不访问网络；
- 按严格 RED/GREEN 顺序完成聚焦测试、全量前端单测和生产构建。

排除：

- 不修改 `package.json`、`package-lock.json`、Vite、Vitest、Playwright、router、layout、view、样式或既有测试；
- 不引入 TypeScript、OpenAPI 代码生成器、运行时 schema 库、Pinia、composable、组件或表单校验；
- 不转换、trim、解析或重命名成功 DTO 字段，不把 DECIMAL/BIGINT 字符串转成 JavaScript number；
- 不按 `apiName` 硬编码动态下载参数或数据集筛选规则；
- 不实现请求取消、重试、退避、缓存、并发世代、页面通知或日志；这些属于后续页面/composable 任务；
- 不保存、显示或发送 Token，不加入 Authorization、Cookie、凭证配置、请求体日志或原始 Axios 错误；
- 不读取或修改 Java 实现、OpenAPI、错误码目录及其他已完成任务产物。

## Approach

### 模块边界和公开接口

采用五个小模块，不建立通用 operation registry，也不生成客户端代码：

| 模块 | 导出 | 职责 |
|---|---|---|
| `http.js` | `http`、`configureHttp` | 唯一 Axios 实例、配置校验、逐请求 UUID 和响应错误拦截 |
| `dataSources.js` | `listDataSources`、`listApis` | 数据源和插件 API 元数据请求 |
| `downloads.js` | `downloadDataset` | 同步下载请求 |
| `datasets.js` | `listDatasets`、`getDataset`、`queryDataset` | 数据集摘要、定义和分页记录请求 |
| `errors.js` | `ApiError`、`ClientError`、`normalizeError` | 契约校验和安全错误投影 |

六个业务函数均返回 `response.data`。成功体已经使用 OpenAPI 的 camelCase 固定字段；动态 `params` 键和分页 `items` 键保持 snake_case，因此客户端不做运行时映射。JSDoc 只提供编辑器和调用方可读的 DTO 合同，不增加运行时代码或构建步骤。

各模块声明其直接使用的 DTO：

- `dataSources.js`：`DataSourceSummary` 七个必填字段；`ApiDescriptor` 的 `apiName/displayName/category/queryMode/parameters`，以及参数项的四个必填字段和五个可选字段；
- `downloads.js`：`DownloadRequest` 的 `pluginId/apiName/params` 和 `DownloadResponse` 的八个必填字段；
- `datasets.js`：`DatasetSummary` 七个字段、`DatasetDefinitionResponse` 追加的 `columns`、筛选/列描述项、`PageResponse` 九个字段及 `Record<string, string|null>` 记录；
- `errors.js`：OpenAPI `FieldError` 和 `ApiError` 五字段，以及客户端错误闭集。

### Axios 实例与配置

`http.js` 在模块加载时只调用一次 `axios.create`：

```js
axios.create({ baseURL: '/api/v1', timeout: 130000 })
```

`configureHttp(options)` 要求 `options` 是对象；仅当属性存在时覆盖对应默认值。`baseURL` 必须是非空、非纯空白字符串，保存原值而不补斜杠或解析主机；`timeout` 必须是正安全整数。无效输入抛出不含输入原值的固定 `TypeError`。函数直接修改唯一实例的 `defaults.baseURL`/`defaults.timeout`，不创建或返回另一实例。测试保存并在 `afterEach` 恢复默认值，避免全局状态泄漏。

请求拦截器在 adapter 执行前调用 `globalThis.crypto.randomUUID()`，无条件覆盖 `config.headers` 中的 `X-Request-Id`。业务函数不开放 Axios config/header 参数，因此调用方不能发送 Token 或绕过关联 ID。拦截器不捕获请求内容、不写日志，也不在模块状态中保存 UUID。

### 六个业务请求

所有路径均相对于 `/api/v1`；`pluginId` 和 `apiName` 在插入路径前分别使用 `encodeURIComponent`，不在客户端复制服务端标识符校验。

| 函数 | Axios 调用 | 投影规则 |
|---|---|---|
| `listDataSources()` | `GET /data-sources` | 无输入 |
| `listApis(pluginId)` | `GET /data-sources/{pluginId}/apis` | 编码一个 path segment |
| `downloadDataset(request)` | `POST /downloads` | 新建仅含 `pluginId/apiName/params` 的 body；动态键和值逐字保留 |
| `listDatasets(pluginId)` | `GET /data-sources/{pluginId}/datasets` | 编码一个 path segment |
| `getDataset(pluginId, apiName)` | `GET /data-sources/{pluginId}/datasets/{apiName}` | 分别编码两个 path segment |
| `queryDataset(pluginId, apiName, criteria)` | `GET .../records` | 只复制已提供且不为 `undefined` 的七个允许 query 参数 |

七个查询参数按固定顺序投影：`tsCode`、`tradeDateFrom`、`tradeDateTo`、`annDateFrom`、`annDateTo`、`page`、`pageSize`。`null`、空字符串和非法值不在本任务擅自纠正；若调用方提供，它们原样交给服务端的冻结校验。额外属性被忽略，避免把页面状态或凭证误发到查询串。

`downloadDataset` 同样只投影三个公开根字段，但不枚举或改写 `params` 内部动态键；缺失/非法输入由后端标准错误和后续 M10-T04 页面校验处理。本任务不形成第二套业务验证规则。

### 服务端 `ApiError`

`errors.js` 保存 M00-T03 的 16 项 `code -> HTTP/retryable` 只读映射。`normalizeError` 仅在以下条件全部成立时构造 `ApiError`：

1. Axios error 含 HTTP response；
2. body 是对象且键集合恰为 `requestId/code/message/retryable/fieldErrors`；
3. `requestId` 和 `message` 是非空字符串，`code` 属于 16 项闭集，`retryable` 是 boolean；
4. HTTP status 和 `retryable` 与该 code 的冻结映射一致；
5. `fieldErrors` 是数组，每项恰含非空字符串 `field/message`；
6. 响应头 `X-Request-Id` 是非空字符串并与 body `requestId` 相同。

`ApiError extends Error` 使用服务端安全 `message` 作为标准 Error message，并公开 `requestId`、`code`、`retryable`、`fieldErrors`。`fieldErrors` 中每项和数组本身均复制并冻结，后续调用方不能改写接收到的错误快照。对象不保存 Axios error、response、config、cause、原始响应文本或额外字段。

### 客户端 `ClientError`

非服务端 API 错误使用独立闭集，绝不借用或伪造 16 个服务端 code：

| `kind` | 条件 | `retryable` | 固定消息 |
|---|---|---:|---|
| `TIMEOUT` | Axios code 为 `ECONNABORTED` 或 `ETIMEDOUT` | `true` | `请求超时，请稍后重试。` |
| `NETWORK` | Axios code 为 `ERR_NETWORK` 且无 response | `true` | `无法连接服务，请检查网络后重试。` |
| `INVALID_RESPONSE` | 存在 response，但状态、Header 或 body 不满足 `ApiError` 合同 | `false` | `服务返回了无法识别的响应。` |
| `UNEXPECTED` | 其他本地失败，包括当前任务不处理的取消 | `false` | `请求未能发送。` |

`ClientError extends Error` 公开 `kind`、`retryable` 和 `requestId`。`requestId` 优先从被拒绝请求 config 中的出站 `X-Request-Id` 读取；若失败发生在请求拦截器之前则为 `null`。它只使用上表固定消息，不保存原始 error/cause/config/body/header 或服务端非契约数据。

`http.js` 的响应拒绝拦截器只调用 `normalizeError` 并拒绝其安全结果。它不记录日志、不自动重试，也不改写正常响应。

### TDD 和失败处理

先一次性创建完整 `control-plane/src/api/api.spec.js`，不创建任何生产模块；聚焦测试必须在收集阶段仅因 `./http.js` 等任务目标模块不存在而非零。若先出现测试语法、现有测试、Node engine 或依赖问题，先修正测试/环境，不能把无关失败记录为 RED。

GREEN 阶段只创建五个生产模块。测试用替换 `http.defaults.adapter` 的受控 adapter 观察最终 Axios config 并返回内存响应；不得 mock 六个业务函数、启动 Vite/后端或访问公网。任何路径、大小写、Header、超时、错误字段或安全隔离失败均修复生产代码，不能删除断言、放宽错误合同或暴露 Axios 原始对象。

## Files

创建：

- `control-plane/src/api/http.js`：唯一 Axios 实例、显式配置、请求 ID 与错误拦截器；
- `control-plane/src/api/dataSources.js`：数据源/API 描述 DTO JSDoc 和两个 GET 函数；
- `control-plane/src/api/downloads.js`：下载 DTO JSDoc 和一个 POST 函数；
- `control-plane/src/api/datasets.js`：数据集/分页 DTO JSDoc 和三个 GET 函数；
- `control-plane/src/api/errors.js`：错误映射、`ApiError`、`ClientError` 和归一化；
- `control-plane/src/api/api.spec.js`：唯一聚焦测试文件，共 12 个用例。

不创建、修改或删除其他文件。实现提交固定为 `feat(ui): add typed API client boundaries`，精确包含上述 6 个新增文件；设计、看板、交接、`node_modules`、`dist` 和测试产物不得混入实现提交。

## Tests

所有 npm 命令从 `control-plane` 执行，Node 必须满足 `>=24.15.0 <25`。

1. 前置环境和范围：

```bash
git status --short
cd control-plane
node -e 'const [major,minor]=process.versions.node.split(".").map(Number); if (major !== 24 || minor < 15) process.exit(1)'
npm --version
```

预期：实现开始前工作树只含已批准的设计/计划/看板/交接文档；Node 检查退出 0。

2. 严格 RED：只创建完整测试文件后运行：

```bash
npm run test:unit -- --run src/api/api.spec.js
```

预期：退出非 0，Vitest 只因 `src/api/http.js` 等目标生产模块不存在而不能收集 `api.spec.js`；不是语法、依赖、Node 或既有测试失败。

3. GREEN 聚焦测试固定为 1 file / 12 tests：

```bash
npm run test:unit -- --run src/api/api.spec.js
```

十二个用例精确覆盖：

1. 唯一实例的默认配置、合法局部覆盖、无效 `baseURL/timeout` 拒绝及测试后恢复；
2. `listDataSources` 的 GET 路径和 data 返回；
3. `listApis` 的 path segment 编码；
4. `downloadDataset` 的 POST、精确三字段 body、snake_case 动态键/原值和输入不变；
5. `listDatasets` 的 GET 路径；
6. `getDataset` 的双 path segment 编码；
7. `queryDataset` 的 records 路径、七项 camelCase 白名单、`undefined` 省略、额外属性忽略和输入不变；
8. 连续请求各有不同且可解析的 UUID `X-Request-Id`，adapter 同时观察 130000 或覆盖后的 timeout；
9. 合法 16-code 示例形成 `ApiError`，五字段原样保留且字段错误快照不可变；
10. 未知 code、错误 status/retryable、缺失/额外字段、非法 fieldErrors 或 Header/body requestId 不同均形成不泄漏正文的 `INVALID_RESPONSE`；
11. `ECONNABORTED`/`ETIMEDOUT` 均形成保留出站 requestId 的可重试 `TIMEOUT`；
12. `ERR_NETWORK` 形成可重试 `NETWORK`，其他/取消失败形成不可重试 `UNEXPECTED`，两者均不保留或显示原始错误、请求体与 Header。

预期：12/12 通过，无网络请求、未处理 rejection、console 输出或 Vue/Vitest warning。

4. 完整前端回归与构建：

```bash
npm run test:unit -- --run
npm run build
```

预期：完整单测固定为 4 files / 19 tests 全通过；Vite build 退出 0。构建只允许 M10-T02 已由项目所有者批准的 Element Plus `Some chunks are larger than 500 kB after minification` 提示，不允许新增 warning/error。

5. 配置、导出和合同静态检查：

```bash
node --input-type=module -e 'import assert from "node:assert/strict"; const h=await import("./src/api/http.js"); const ds=await import("./src/api/dataSources.js"); const dl=await import("./src/api/downloads.js"); const q=await import("./src/api/datasets.js"); const e=await import("./src/api/errors.js"); assert.equal(h.http.defaults.baseURL,"/api/v1"); assert.equal(h.http.defaults.timeout,130000); assert.deepEqual(Object.keys(ds).sort(),["listApis","listDataSources"]); assert.deepEqual(Object.keys(dl),["downloadDataset"]); assert.deepEqual(Object.keys(q).sort(),["getDataset","listDatasets","queryDataset"]); assert.deepEqual(Object.keys(e).sort(),["ApiError","ClientError","normalizeError"]);'
```

预期：退出 0；默认配置和公开模块表面精确匹配设计。

6. 安全、范围、格式和生成物门禁：

```bash
cd ..
git diff --check
git status --short --untracked-files=all -- control-plane
git diff --exit-code -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/playwright.config.js control-plane/src/router \
  control-plane/src/layouts control-plane/src/views control-plane/src/style.css
rg -ni 'token|password|authorization|cookie|console\.|localStorage|sessionStorage' control-plane/src/api
git check-ignore control-plane/node_modules control-plane/dist \
  control-plane/node_modules/.cache/tensor-playwright
```

预期：格式与既有文件不变检查退出 0；status 精确显示 Files 节 6 个新增文件；敏感能力扫描无输出并按预期退出 1；三个生成路径均被忽略。暂存后 `git diff --cached --name-status` 精确为 6 个新增文件，提交消息为 `feat(ui): add typed API client boundaries`。

## Acceptance

- 唯一 Axios 实例默认 `/api/v1` 和 130000ms，显式配置只修改该实例并拒绝无效值；
- 每次请求覆盖生成新的 UUID `X-Request-Id`，不接收任意业务 header/config，不发送或保存凭证；
- 六个函数的方法、路径、path encoding、body 和 query 参数与 M00-T03 六条业务操作逐项一致，并直接返回未改写的成功 DTO；
- 下载动态参数和分页业务字段保持 snake_case，固定请求/响应控制字段保持 camelCase，高精度字符串不被转成 number；
- 合法服务端错误严格形成不可变 `ApiError`，保留五个字段并验证 code/status/retryable/Header/body 一致；
- 超时、网络、非契约响应和意外失败形成独立 `ClientError`，具有固定安全消息和正确 retryable，不伪造服务端 code、不泄漏 Axios 原始数据；
- 聚焦 12/12、完整前端 19/19 和生产构建通过，构建没有新增 warning；
- 实现精确包含 6 个新增 API 文件，不修改依赖、配置、路由、页面、样式、Java 或契约，也不提交生成物。

## Risks

- 130 秒客户端超时高于当前后端 Tushare 120 秒读取上限，给后端形成标准超时错误留出 10 秒余量；M13 仍需在生产代理、应用和上游层统一验证完整 timeout ordering。本任务不提前修改生产部署配置。
- Axios adapter 测试能证明客户端配置和投影，不证明真实浏览器、代理和后端联通；真实闭环属于 M14。
- `crypto.randomUUID()` 是 Node 24 和目标桌面 Chrome 的原生能力；本任务不增加旧浏览器 polyfill，也不扩展桌面 Chrome 验收范围。
- JSDoc 不执行运行时成功 DTO 校验。服务端成功契约由 M00/M09 保证；客户端只对错误包络执行严格验证，避免为九个 DTO 复制第二套运行时 schema。
