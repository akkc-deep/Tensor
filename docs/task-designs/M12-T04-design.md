# M12-T04 查询 composable、竞态和超界页处理——任务设计

任务编号：`M12-T04`

对应任务：[M12-T04](../superpowers/plans/tensor-modules/M12-dataset-ui.md#task-m12-t04-查询生命周期与竞态30h)

实施产物：保存不可变查询快照、隔离请求竞态并接受服务端规范分页结果的 `useDatasetQuery`

## Goal

在现有 Vue 3、M10 API 客户端和 M12 筛选/分页组件基线上交付 `useDatasetQuery`。后续 `DatasetView` 只需提交当前数据源、数据集和已校验筛选快照，即可驱动未查询、加载、成功、空结果和失败五种查询状态，并通过同一不可变请求上下文完成翻页、修改每页条数和安全重试。

composable 必须在新请求开始时隐藏旧结果，用单调 generation 阻止旧成功或旧失败覆盖当前页面，保留筛选条件完成服务端翻页，并以服务端返回的 `page/pageSize/totalElements/totalPages` 为最终事实。它不加载元数据、不校验筛选、不渲染页面，也不在客户端计算或修正分页。

## Scope

包含：

- 创建只导出 `useDatasetQuery` 的查询生命周期 composable；
- 管理 `UNQUERIED | LOADING | SUCCESS | EMPTY | FAILURE` 五种内存状态；
- 管理当前结果、安全错误、页码、每页条数、加载状态和可重试状态；
- 保存内部不可变查询快照，让翻页和每页条数变化保留数据源、数据集与筛选；
- 新筛选查询固定回第 1 页，修改每页条数固定回第 1 页，普通翻页保持当前 page size；
- 原样接受服务端超界页归一结果，并用响应的 `page/pageSize` 更新公开状态；
- 允许新的 `query()` 和 `reset()` 使在途请求失效，加载期间拒绝分页和重试动作；
- retryable 失败使用失败请求的完整快照原样重试；
- 用一份单元测试覆盖状态、快照、竞态、重试、分页和重置；
- 执行严格 RED、聚焦测试、完整前端回归、生产构建及范围/安全检查。

排除：

- 不修改 `DatasetView.vue`、router、layout、全局样式、组件、API 客户端、依赖或配置；
- 不加载数据源、数据集摘要或数据集定义，不管理当前选择；
- 不渲染筛选、表格、分页、空态、失败提示或重试按钮；
- 不校验、trim、转换或解释筛选值，不读取 `useDatasetFilters` 内部状态；
- 不解释 `ApiError`/`ClientError` 正文，不读取 Axios 原始 error、response、config 或 cause；
- 不取消请求，不引入 `AbortController`、超时、退避、自动重试、缓存、Pinia、持久化或定时器；
- 不从 `items.length` 推导空结果，不根据 totals 计算、截断、修正或猜测页码；
- 不提供客户端数据切片、排序、全量加载、写操作、导出或具体数据集分支；
- 不修改 OpenAPI、Java、SQL、YAML 或既有任务产物。

## Approach

### 公开接口和状态边界

`control-plane/src/composables/useDatasetQuery.js` 只导出：

```text
useDatasetQuery(): {
  state: Ref<'UNQUERIED' | 'LOADING' | 'SUCCESS' | 'EMPTY' | 'FAILURE'>
  result: ShallowRef<PageResponse | null>
  error: ShallowRef<ApiError | ClientError | null>
  page: Ref<number>
  pageSize: Ref<20 | 50 | 100>
  loading: ComputedRef<boolean>
  canRetry: ComputedRef<boolean>
  query(pluginId: string, apiName: string, criteria: QueryCriteria): Promise<boolean>
  changePage(nextPage: number): Promise<boolean>
  changePageSize(nextPageSize: 20 | 50 | 100): Promise<boolean>
  retry(): Promise<boolean>
  reset(): void
}
```

初始值固定为 `state='UNQUERIED'`、`result=null`、`error=null`、`page=1`、`pageSize=50`、`loading=false`、`canRetry=false`。`loading` 只等于 `state === 'LOADING'`；`canRetry` 只在当前状态为 `FAILURE`、存在失败快照且 `error.retryable === true` 时为 true。

composable 不公开当前请求、失败请求、generation 或内部执行函数。调用方必须先使用 M12-T01 表单的 `validate()` 和 `criteria()` 取得筛选快照，再调用 `query()`；本任务不重复筛选验证。`result` 原样保留 M10-T03 的 `PageResponse`，包括列、当前页记录和四个分页字段。

### 请求快照和统一执行

内部请求快照固定为：

```text
{
  pluginId: string,
  apiName: string,
  criteria: Record<string, string>,
  page: number,
  pageSize: 20 | 50 | 100
}
```

创建快照时新建根对象和 `criteria` 浅拷贝。OpenAPI 查询筛选值都是字符串，快照没有嵌套可变对象，因此不需要深复制；快照不返回给调用方。向 `queryDataset` 发送时重新组合 `{...criteria, page, pageSize}`，不修改调用方对象。

所有网络动作调用同一个内部 `execute(snapshot)`：

1. 递增整数 generation 并捕获本次值；
2. 保存本次请求的独立快照作为当前上下文，清除失败快照；
3. 立即把公开 `page/pageSize` 更新为请求意图，清空旧 `result/error`，进入 `LOADING`；
4. 调用 `queryDataset(pluginId, apiName, {...criteria, page, pageSize})`；
5. Promise fulfilled 或 rejected 后先比较 generation；若已过期，只消费结果并返回 `false`，不修改任何公开或内部当前状态；
6. 当前响应成功时原样保存响应，以响应 `page/pageSize` 覆盖请求意图，并把当前快照的分页值同步为响应值；清空失败快照，按 `response.totalElements === 0` 进入 `EMPTY`，否则进入 `SUCCESS`，返回 `true`；
7. 当前请求失败时保持请求时的公开 `page/pageSize`，令 `result=null`，原样保存安全错误和失败快照，清除可分页的当前快照，进入 `FAILURE` 并返回 `false`。

状态只由 `totalElements` 判断 `EMPTY`，不读取 `items.length` 或重新验证 DTO。OpenAPI 已规定非空数据在规范页上返回记录、空结果为 `page=1,totalPages=0,totalElements=0,items=[]`；合同不一致由 API/服务端边界暴露，本 composable 不掩盖。

### 动作语义

`query(pluginId, apiName, criteria)` 在包括 `LOADING` 在内的任何状态均可接受，用当前 `pageSize` 创建 `page=1` 的新快照并执行。新查询覆盖旧上下文，因此切换筛选或数据集后不会保留旧页码；较旧请求随后完成时只能走 stale 分支。

`changePage(nextPage)` 仅当存在最近成功或空结果的当前快照、状态不是 `LOADING` 且 `nextPage !== page` 时执行；否则返回 `false` 且不改变状态。合法调用复制当前快照，只替换 `page`，因此保留 pluginId、apiName、筛选和 page size。动作信任 M12-T03 的 Element Plus 控件与 OpenAPI 一基页码合同，不自行验证、夹取或计算 nextPage。若请求页超界，成功响应中的服务端规范页覆盖公开页码，并成为后续翻页的当前事实。

`changePageSize(nextPageSize)` 使用相同前置条件，并在 `nextPageSize !== pageSize` 时复制当前快照，固定设置 `page=1` 和新的 page size 后执行。`EMPTY` 仍保留当前快照，因此零结果下的 20/50/100 选择可以重新查询；组件不因 totals 为零自行禁用。动作信任 M12-T03 只产生 20/50/100，不复制运行时枚举验证。

`retry()` 仅当 `canRetry` 为 true 且当前不为 `LOADING` 时，复制失败快照并原样执行；这包括失败时的 pluginId、apiName、筛选、page 和 pageSize。非 retryable 错误、没有失败快照、成功/空/未查询/加载状态均返回 `false`，不请求也不改状态。它不读取当前表单；用户修改筛选后必须重新校验并调用新的 `query()`。

`reset()` 在任何状态下递增 generation，令所有在途请求过期；随后清除当前和失败快照，恢复初始五项公开值。它不请求网络、不改变页面持有的数据源或数据集选择，也不调用 M12-T01 的筛选重置；M12-T05 负责组合两个独立 reset。

### 竞态、失败和实现纪律

新 `query()` 在 `LOADING` 中仍可被接受，使页面组合能够用新的数据集或筛选上下文取代旧请求；`reset()` 同样立即失效旧请求。`changePage()`、`changePageSize()` 和 `retry()` 在 `LOADING` 中统一返回 `false`，与 M12-T03 接收 `disabled=true` 的 UI 锁定保持一致。

generation 只决定响应是否可写状态，不取消底层 Axios Promise。stale success 和 stale failure 都必须完整消费并返回 `false`，不得产生未处理 rejection，也不得改变 result、error、page、pageSize、state、当前快照或失败快照。

M10-T03 已将失败归一化为不泄漏原始内容的 `ApiError` 或 `ClientError`。composable 保存同一个错误对象，仅读取公开 boolean `retryable` 计算 `canRetry`；不记录日志、不生成新错误文案、不保存 cause/config/body/header。严格 TDD 先创建完整 spec 并取得缺生产模块 RED，再创建最小 composable；不得通过修改 mock、测试环境、API 客户端或既有组件绕过失败。

## Files

创建且只创建以下 2 个实施文件：

- `control-plane/src/composables/useDatasetQuery.js`：五态、不可变查询快照、统一执行、generation、分页、重试和重置；
- `control-plane/src/composables/useDatasetQuery.spec.js`：使用受控 `queryDataset` mock 的 8 项公开行为测试。

不修改或删除其他生产、测试、依赖、配置和文档文件。初始实现提交固定为 `feat(ui): manage dataset query lifecycle`，精确包含上述 2 个新增文件；本设计、交接和看板不得混入实现提交。

## Tests

所有 npm 命令从仓库根目录开始，使用 Node.js 24.15.0：

```bash
TENSOR_NODE=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin/node
TENSOR_NPM=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/lib/node_modules/npm/bin/npm-cli.js
"$TENSOR_NODE" --version
```

预期输出 `v24.15.0`。M12-T03 最终基线为 18 个测试文件、104 项测试；开始实施时须重新运行基线，若既有测试失败，先停止并定位，不能记作本任务 RED。

### 严格 RED

只创建完整 `control-plane/src/composables/useDatasetQuery.spec.js`，不创建生产 composable，然后运行：

```bash
cd control-plane
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run src/composables/useDatasetQuery.spec.js
```

预期命令非零，Vitest 只因 `./useDatasetQuery.js` 不存在而无法收集测试文件；不得出现测试语法、mock、依赖、Node、setup 或既有测试失败。不得提交 RED 检查点。

### `useDatasetQuery` 八项

1. 初始五项状态为 `UNQUERIED/null/null/1/50`，`loading/canRetry` 为 false，且创建 composable 不自动调用 `queryDataset`；首次 `query('fixture','daily',{tsCode:'000001.SZ'})` 精确发送浅拷贝筛选加 `page=1,pageSize=50`，立即进入 `LOADING` 并更新请求意图；
2. 已有成功结果后发起新查询会立即清除旧 result/error 并回第 1 页；当前成功响应按 `totalElements>0` 进入 `SUCCESS`、原样保存 DTO，并采用服务端 `page/pageSize`；当前空响应按 `totalElements=0` 进入 `EMPTY`，保留原始零 totals 响应而不是丢弃；
3. 当前请求拒绝后进入 `FAILURE`、`result=null`、保存同一个安全错误且不自动重试；retryable 与 non-retryable 错误分别产生正确 `canRetry`；
4. retryable 失败的 `retry()` 精确重放失败时 pluginId、apiName、筛选、page 和 pageSize 的新副本并可成功落地；non-retryable、没有失败快照或失败后已发起新查询时不重放旧请求；
5. 两个并发 `query()` 的较早成功或失败均不能覆盖较新查询；`reset()` 后完成的旧成功/失败同样不改变 `UNQUERIED` 默认状态，所有 rejection 均被消费；
6. `changePage(3)` 保留来源、数据集、筛选和 pageSize，只替换请求 page；若服务端把超界 3 规范为 2，公开 page、result 和当前快照均采用 2，后续翻页从服务端事实继续；同页调用不请求；
7. `changePageSize(20)` 在 `SUCCESS` 与 `EMPTY` 均保留来源、数据集和筛选、固定请求 `page=1`；`LOADING` 期间 page/size/retry 动作都返回 false 且不产生额外请求；同 size 调用不请求；
8. 当前 pageSize 为 20 时，新筛选 `query()` 回第 1 页但保留 20；`reset()` 清空 result/error/快照、恢复 `UNQUERIED/page=1/pageSize=50` 并使在途响应失效，且不调用 API。

每项期望值使用手写字面量和完整 PageResponse fixture，不从生产常量或实现逻辑生成。只 mock M10-T03 的 `queryDataset` 网络边界；状态、Promise、refs、computed 和 generation 使用真实 Vue/composable 行为。测试中的 deferred Promise 必须显式 resolve/reject 并 await，避免未处理 rejection。

### GREEN、回归与构建

最小创建 `useDatasetQuery.js` 后运行：

```bash
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run src/composables/useDatasetQuery.spec.js
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run
"$TENSOR_NODE" "$TENSOR_NPM" run build
```

预期：聚焦测试为 1 file / 8 tests 全部通过；在 18/104 基线上，完整前端回归为 19 files / 112 tests 全部通过；Vite 生产构建退出 0，只允许既有 Element Plus `Some chunks are larger than 500 kB after minification` 提示，不允许新增 Vue、未处理 rejection、console、测试或编译 warning/error。

### 公开表面、范围和安全检查

```bash
cd ..
"$TENSOR_NODE" --input-type=module -e 'const m=await import("./control-plane/src/composables/useDatasetQuery.js"); if (Object.keys(m).join(",") !== "useDatasetQuery") process.exit(1)'
git diff --check
git status --short --untracked-files=all -- control-plane/src/composables
git diff --exit-code -- \
  control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/playwright.config.js control-plane/src/api \
  control-plane/src/components control-plane/src/router \
  control-plane/src/layouts control-plane/src/views \
  control-plane/src/style.css control-plane/src/utils
rg -n 'v-html|innerHTML|axios|fetch\(|setTimeout|setInterval|AbortController|localStorage|sessionStorage|Authorization|Cookie|token|password|console\.|Math\.(ceil|floor|round)|slice\(' \
  control-plane/src/composables/useDatasetQuery.js
```

预期：公开导出、格式和受保护路径检查退出 0；范围化 status 只新增 Files 节两个实施文件；禁止 HTML 注入、直接网络、定时器、取消、持久化、凭证、日志、客户端页数计算和切片的扫描无输出并按预期退出 1。暂存前后检查完整 `git diff --cached`；只以精确路径提交两个任务文件，不能混入工作树中已有的 `.idea` 或 `data-plane/**/target/` 变化。

## Acceptance

- `useDatasetQuery` 是唯一导出，无参数创建，公开状态、分页 refs、computed 和五个动作与设计精确一致；
- 初始不请求；新查询固定第 1 页并保留当前 page size，立即隐藏旧结果和错误；
- 请求快照不修改调用方 criteria，翻页和修改 page size 保留 pluginId、apiName 与全部筛选，修改 size 固定回第 1 页；
- 当前成功 DTO 原样保存，`totalElements=0` 映射 `EMPTY`、其他映射 `SUCCESS`，响应 page/pageSize 是超界归一后的最终事实；
- 当前失败保存同一个安全错误且不展示旧结果，不自动重试；只有 retryable 失败可按完整失败快照重试；
- 新 query 和 reset 在加载中仍可使旧请求失效；stale success/failure 不覆盖当前任何状态且不产生未处理 rejection；
- 加载期间分页与重试不请求；零结果仍允许修改 page size 并以第 1 页重新查询；reset 恢复 `UNQUERIED/page=1/pageSize=50` 且不改变页面选择或筛选；
- composable 不加载元数据、不校验或解释筛选、不渲染 UI、不解释错误正文、不取消/缓存/持久化、不计算 totals/page、不切片数据；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 8/8、完整前端 112/112、生产构建、公开导出、范围、安全和格式门禁达到预期；
- 初始实现提交精确包含 2 个新增文件，提交消息为 `feat(ui): manage dataset query lifecycle`。

## Risks

- generation 只忽略 stale 结果，不取消网络请求；M10-T03 的 130 秒超时仍负责最终释放底层请求，新查询不会等待旧请求结束。
- 公开 page/pageSize 在请求开始时表示用户意图，成功后改为服务端事实；加载和失败状态不会显示旧表格，避免把请求中页码与旧数据组合。
- 服务端可能把超界请求页规范为较小的最后一页；成功后必须同时更新公开 page 和内部当前快照，否则下一次翻页会从过期请求页继续。
- `retry()` 使用失败时快照而不是当前表单；用户修改筛选后必须重新校验并调用 `query()`，避免把新筛选与旧页码混合。
- criteria 浅复制依赖当前 OpenAPI 只允许字符串筛选值；若合同以后加入数组或嵌套对象，必须先更新 OpenAPI、M10 JSDoc 和本设计，再决定复制策略。
- JavaScript `number` 承载 OpenAPI int64 totals 是 M10-T03 已接受的客户端合同；本任务原样保存，不扩大为 BigInt/string 协议调整。
- 工作树存在与本任务无关的 `.idea/misc.xml` 和后端 `data-plane/**/target/` 变化；文档与后续实施必须用精确路径提交并保留这些用户变化。
