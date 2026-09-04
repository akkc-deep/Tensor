# M12-T05 `DatasetView` 页面集成和组件回归——任务设计

任务编号：`M12-T05`

对应任务：[M12-T05](../superpowers/plans/tensor-modules/M12-dataset-ui.md#task-m12-t05-datasetview-集成25h)

实施产物：把 M12-T01～T04 的受控组件与状态 composable 组合成可完成元数据加载、筛选查询、宽表查看和服务端分页的 `/datasets` 页面

## Goal

在已完成的数据集选择/筛选、全字段表格、服务端分页和查询生命周期能力之上，替换 M10-T02 的数据查看占位主体。页面挂载后加载数据源，按“数据源 → 数据集 → 动态筛选 → 查询动作 → 查询状态/表格 → 分页”顺序组合既有公开合同，使用户无需了解 API、请求世代或分页快照即可完成一次只读数据查询。

页面必须用独立元数据 generation 隔离数据源、数据集摘要和数据集定义的陈旧成功/失败；记录查询仍只由 `useDatasetQuery()` 管理。选择切换、重置、查询、重试、翻页和每页条数变化都通过既有公开接口连接，不复制子组件或 composable 已冻结的校验、格式化、分页或竞态逻辑。

## Scope

包含：

- 修改 `DatasetView.vue`，保留稳定标题并组合现有 `DataSourceSelect`、`DatasetSelect`、`DynamicFilterForm`、`DatasetTable`、`DatasetPagination` 和 `AsyncStatePanel`；
- 页面挂载时调用一次 `listDataSources()`，来源切换时调用 `listDatasets(pluginId)`，数据集切换时调用 `getDataset(pluginId, apiName)`；
- 以页面局部、单调递增的元数据 generation 忽略被新选择或重试取代的旧成功和旧失败；
- 保存元数据加载状态、安全错误和最近失败操作，使 retryable 的来源、数据集清单或定义加载可按原上下文手动重试；
- 数据源或数据集切换时清除下游元数据、筛选表单实例和查询状态，并使在途记录查询失效；
- 查询前严格执行动态筛选表单的 `validate()`，成功后立即读取 `criteria()` 新快照并调用 `useDatasetQuery().query()`；
- 呈现未查询、加载、成功、空结果和失败五态；成功时展示完整表格和分页，空结果时保留可改变 20/50/100 的分页控件；
- 接通记录查询的 reset、retry、page 和 page-size 动作，保留当前数据源和数据集；
- 创建八项真实页面组合测试，并更新应用壳中仍断言旧数据查看占位文案的一项回归；
- 执行严格 RED、聚焦测试、完整前端回归、生产构建及范围、安全和格式检查。

排除：

- 不修改 `router/index.js` 或 `router/index.spec.js`；用户已批准保留 M10-T02 正确注册并由现有测试覆盖的 named `/datasets` 路由，不制造无行为价值的改动；
- 不修改 M10/M11/M12 已完成的 API、composable、共享组件、数据集子组件、工具、依赖、配置、布局生产代码或全局样式；
- 不创建新的 composable、store、组件、API 封装或通用状态机；元数据协调只存在于本路由页面；
- 不自动发起 records 查询，不自动选择数据集，不绕过动态筛选表单公开方法；
- 不复制筛选字段映射、日期/代码校验、单元格格式化、查询 generation、失败快照、页码归一或 page-size 枚举；
- 不直接使用 Axios/fetch，不解释原始 error/response/config/cause，不显示 `fieldErrors`、请求正文、Header、Token、Cookie、SQL、stack 或内部路径；
- 不取消请求，不增加超时、退避、自动重试、缓存、Pinia、持久化、定时器、全量加载或客户端分页；
- 不按具体 `pluginId`、`apiName`、数据集名称或列名写分支，不裁剪或排序返回记录；
- 不提供排序、列配置、行选择、新增、编辑、删除或导出，也不使用 `v-html`/`innerHTML`。

## Approach

### 稳定页面和依赖边界

`DatasetView` 保持无 props、无 emits 的路由页面，根节点固定为：

```text
section.page[aria-labelledby="datasets-title"]
└── h1#datasets-title  数据查看
```

页面创建一次 `useDatasetQuery()`，使用一个 `ref(null)` 保存当前 `DynamicFilterForm` 实例。它直接导入 M10-T03 的 `listDataSources`、`listDatasets` 和 `getDataset` 作为元数据网络边界；records 请求只能由 `useDatasetQuery()` 内部调用 `queryDataset`。

M12 四项直接依赖职责保持不变：

- M12-T01 拥有数据集选择、筛选渲染、校验、成功条件快照和筛选 reset；
- M12-T02 拥有业务列原序、三个来源列、固定列、横向滚动和安全格式化；
- M12-T03 拥有服务端 totals 摘要、20/50/100、受控分页事件和零页可用性；
- M12-T04 拥有 records 五态、不可变请求快照、查询 generation、服务端页码事实、失败重试和查询 reset。

页面只读取和连接这些公开表面，不访问其内部 ref、快照、常量或 generation。

### 元数据状态和请求世代

页面局部状态固定为：

```text
sources: ShallowRef<DataSourceSummary[]> = []
datasets: ShallowRef<DatasetSummary[]> = []
selectedPluginId: Ref<string> = ''
selectedApiName: Ref<string> = ''
definition: ShallowRef<DatasetDefinitionResponse|null> = null
metadataLoading: Ref<boolean> = false
metadataOperation: Ref<'SOURCES'|'DATASETS'|'DEFINITION'|null> = null
metadataError: ShallowRef<ApiError|ClientError|null> = null
metadataGeneration: number = 0
failedMetadata: null | {type:'SOURCES'} |
  {type:'DATASETS', pluginId:string} |
  {type:'DEFINITION', pluginId:string, apiName:string}
```

`metadataCanRetry` 仅当存在 `failedMetadata`、`metadataError.retryable === true`，且带选择上下文的失败快照仍与当前选择一致时为 true。元数据错误对象原样保存；页面只显示公开 `message` 和非空 `requestId`，不读取 code、kind、fieldErrors 或传输细节。

三个异步动作都先递增 `metadataGeneration`、捕获本次值、清空旧元数据错误/失败快照，把 `metadataOperation` 设置为本次类型并进入 loading；fulfilled/rejected 后先比较 generation。只有当前 generation 可以把 `metadataLoading` 改回 false 并清空 `metadataOperation`。stale 分支只消费 Promise 并返回 `false`，不得改变任何当前页面状态，也不得产生未处理 rejection。

动作语义固定为：

1. `loadSources()`：清空两级选择、数据集清单、定义和查询状态，调用 `listDataSources()`；当前成功原样保存来源。`DataSourceSelect` 的既有单来源默认事件可以继续触发 `selectSource`，页面不自行选择。
2. `selectSource(pluginId)`：同步保存来源 ID，清空数据集 ID、清单和定义并调用 `useDatasetQuery().reset()`；空 ID 不请求，非空调用 `listDatasets(pluginId)`。快速切换来源允许新动作使旧列表成功或失败过期。
3. `selectDataset(apiName)`：同步保存数据集 ID，清空定义并调用查询 reset；空 ID 不请求，非空调用 `getDataset(currentPluginId, apiName)`。快速切换数据集同样由 generation 隔离。

元数据手动重试只重放 `failedMetadata` 的新副本：SOURCES 调用 `loadSources()`，DATASETS 仅在 pluginId 仍匹配时重新加载清单，DEFINITION 仅在 pluginId/apiName 均匹配时重新加载定义。按钮显式调用无参包装函数，不能把 DOM click event 传入业务动作。任一新选择会先清除旧失败快照，使旧错误不能被误重试。

### 页面组件顺序和交互

标题后按以下固定 DOM 顺序渲染：

1. `DataSourceSelect`：传入 `selectedPluginId/sources`，更新事件连接 `selectSource`；沿用已批准的单来源默认和来源可用性表面。
2. `DatasetSelect`：传入 `selectedApiName/datasets`，没有来源或清单为空时禁用，更新事件连接 `selectDataset`。
3. `DynamicFilterForm`：只在 `definition !== null` 时挂载，传入 `definition.filters`；records `LOADING` 时禁用。
4. 查询动作区：只在定义存在时显示原生 button 类型的 Element Plus “查询”和“重置”。查询中禁用重复查询，“重置”保持可用，以便立即失效在途结果并恢复未查询状态。
5. 状态/结果区：按下一节优先级挂载一个状态面板，或表格与分页。

来源、数据集和筛选元数据都直接使用 API 返回对象，不排序、不改写、不按名称分支。切换来源/数据集会卸载旧筛选表单，从而清除旧字段值和错误；查询 reset 同时清除旧结果、错误、分页快照并恢复 page/pageSize 为 1/50。页面级“重置”则先调用当前表单 `reset()`，再调用查询 reset，保留当前来源、数据集和定义。

`handleQuery()` 固定顺序为：若无定义或表单 ref 则返回；`await filterForm.validate()`；失败立即返回且不请求；成功时立即读取一次 `filterForm.criteria()`，再调用 `query(selectedPluginId, selectedApiName, snapshot)`。页面不缓存或改写条件对象。

### 状态、结果和分页

渲染优先级固定为：元数据 loading → 元数据 failure → 尚未完成来源/数据集/定义选择 → records 查询状态。

- 元数据加载：`AsyncStatePanel state="LOADING"`，标题按操作显示“正在加载数据源 / 正在加载数据集 / 正在加载数据集定义”，说明为“请稍候。”；不显示旧定义、表格或查询错误。
- 元数据失败：`AsyncStatePanel state="FAILURE"`，标题“数据查看配置加载失败”，message 使用安全错误消息；actions 显示可用请求 ID，并仅在 `metadataCanRetry` 时显示“重新加载”。
- 未选择来源：INITIAL 面板标题“请选择数据源”，说明“选择数据源后加载可查询的数据集。”。
- 已选择来源但未选择数据集：INITIAL 面板标题“请选择数据集”，说明“选择数据集后设置筛选条件。”。
- 定义已加载且 records 为 `UNQUERIED`：INITIAL 面板标题“设置筛选条件后查询”，说明“筛选条件可留空，结果将由服务端分页返回。”。
- records 为 `LOADING`：LOADING 面板标题“正在查询数据”，说明“请稍候。”；旧表格、分页、result 和 error 都不可见。
- records 为 `FAILURE`：FAILURE 面板标题“查询失败”，message 使用同一个安全错误对象的公开消息；actions 显示可用请求 ID，并仅在 `canRetry` 时显示“重新查询”，点击显式调用无参 `retry()`。
- records 为 `EMPTY`：EMPTY 面板标题精确为“未找到符合条件的数据”，说明“请修改筛选条件后重新查询。”；其后仍挂载 `DatasetPagination`，以允许零结果下改变 page size。
- records 为 `SUCCESS`：只把 `definition.columns`、`result.items` 传给 `DatasetTable`，随后挂载 `DatasetPagination`。

SUCCESS 和 EMPTY 的分页都直接接收查询 composable 的 `page/pageSize` 以及当前 `result.totalElements/totalPages`；`update:page` 连接 `changePage`，`update:pageSize` 连接 `changePageSize`，`disabled` 只等于 records `loading`。页面不根据 items/totals 计算状态、页数或页码。服务端规范后的 page/pageSize 由 M12-T04 回写并在下一次渲染成为唯一事实。

### 测试和实现纪律

`DatasetView.spec.js` 只 mock M10 网络边界 `listDataSources/listDatasets/getDataset/queryDataset`。真实挂载 `DatasetView`、全部 M12 子组件、`DataSourceSelect`、`AsyncStatePanel`、两个 composable 和 Element Plus；mock 响应使用完整 DataSourceSummary、DatasetSummary、DatasetDefinitionResponse、PageResponse 或真实 M10 安全错误对象。

测试断言页面可见行为、公开 props/emits、API 参数、Promise 竞态、焦点和 read-only 表面，不断言私有变量、mock DOM 或 Element Plus 内部 class。deferred Promise 必须显式 resolve/reject 并 await。先创建完整页面 spec 并更新壳层的旧占位断言，在生产页面仍为占位时取得行为 RED；GREEN 只修改 `DatasetView.vue`，不得通过修改子组件、composable、API、router 或测试配置绕过失败。

## Files

创建：

- `control-plane/src/views/DatasetView.spec.js`：八项真实元数据、选择、筛选、查询、竞态、结果、分页、重试、重置和只读页面组合测试。

修改：

- `control-plane/src/views/DatasetView.vue`：元数据 generation、来源/数据集/定义加载、既有组件与查询 composable 组合、状态和动作接线；
- `control-plane/src/layouts/AppLayout.spec.js`：把 `/datasets` 导航回归从旧占位文案迁移到新页面稳定初始表面，并继续隔离页面挂载的元数据请求。

不修改或删除其他文件。特别是 `control-plane/src/router/index.js`、`router/index.spec.js`、`App.spec.js`、API、composable、子组件、布局生产代码和样式保持当前已验证内容。实现提交固定为 `feat(ui): complete dataset query page`，精确包含上述一新增、两修改文件；本设计、交接和看板不得混入实现提交。

## Tests

所有命令从仓库根目录开始，使用 Node.js 24.15.0：

```bash
TENSOR_NODE=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin/node
TENSOR_NPM=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/lib/node_modules/npm/bin/npm-cli.js
"$TENSOR_NODE" --version
```

预期输出 `v24.15.0`。M12-T04 最终基线为 19 个测试文件、112 项测试；开始实施时须重新运行基线，若既有测试失败，先停止并定位，不能记作本任务 RED。

### 严格 RED

先完整创建 `DatasetView.spec.js`，并只修改 `AppLayout.spec.js` 的数据查看新页面断言，不修改 `DatasetView.vue`，然后从 `control-plane` 运行：

```bash
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run \
  src/views/DatasetView.spec.js \
  src/layouts/AppLayout.spec.js
```

预期命令非零；失败只来自现有占位 `DatasetView` 缺少元数据请求、选择、筛选、动作、状态、表格和分页行为，以及壳层仍渲染旧占位表面。不得出现 mock 提升、测试语法、Vue/Element Plus、router、setup、未处理 rejection 或真实网络错误；不得提交 RED 检查点。

### `DatasetView` 八项

1. 页面保留唯一“数据查看”标题，挂载只调用一次来源 API；pending 时礼貌显示“正在加载数据源”，retryable 来源失败时以 alert 纯文本显示安全摘要、请求 ID 和“重新加载”，点击后只重试来源加载并恢复“请选择数据源”，旧占位文案不存在；
2. 单来源由真实 `DataSourceSelect` 默认发出来源 ID并精确调用 `listDatasets(pluginId)`；快速切换两个来源时，较早数据集清单的成功或失败均不能覆盖当前来源、清单、状态、错误或失败快照；
3. 选择数据集精确调用 `getDataset(pluginId, apiName)`；快速切换数据集时旧定义成功/失败均不可覆盖新选择。当前定义原样驱动对应筛选字段，页面不自动调用 `queryDataset`；
4. 来源、数据集、筛选表单、查询/重置动作和初始状态按固定 DOM 顺序渲染；非法筛选点击“查询”产生既有字段错误和首错焦点且不请求，合法筛选精确发送当前来源、数据集和 `criteria()` 快照；
5. records pending 时旧表格/分页/错误立即隐藏，筛选与重复查询禁用；“重置”使在途成功或失败失效、清空筛选和分页并恢复 UNQUERIED，同时保留当前来源、数据集和定义；
6. SUCCESS 原样把定义业务列和当前页 items 传给 `DatasetTable`，分页显示服务端 totals；真实 page/page-size 事件分别保留当前来源/数据集/筛选并发出正确请求，page-size 回第 1 页，超界响应页成为后续公开事实；
7. EMPTY 显示精确空态且不挂载表格，但保留零页可用分页并允许 20/50/100 重新查询；FAILURE 不显示旧表格，原样显示安全摘要/请求 ID，只有 retryable 错误显示“重新查询”，并用失败时冻结快照成功重试；
8. 来源或数据集切换同步清除旧筛选、查询结果、错误、页码和重试上下文；真实数据源 combobox、数据集 combobox、首个筛选控件及查询/重置按钮按页面顺序可聚焦，页面不存在排序、选择、新增、编辑、删除或导出入口。

每项期望值使用手写字面 fixture，不从生产常量或实现逻辑生成。对网络 mock 的参数断言只验证页面与既有 API 的边界合同；状态、refs、computed、generation、子组件渲染和用户事件使用真实实现。

### GREEN、回归与构建

最小修改 `DatasetView.vue` 后从 `control-plane` 运行：

```bash
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run \
  src/views/DatasetView.spec.js \
  src/layouts/AppLayout.spec.js
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run
"$TENSOR_NODE" "$TENSOR_NPM" run build
```

预期：聚焦为 2 files / 11 tests，完整前端在 19/112 基线上变为 20 files / 120 tests，全部通过且无未处理 rejection、console、Vue、Element Plus、测试或编译 warning/error。Vite 生产构建退出 0，只允许既有 Element Plus `Some chunks are larger than 500 kB after minification` 提示。

### 范围、格式和安全检查

从仓库根目录运行：

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
git diff --exit-code -- \
  control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/playwright.config.js control-plane/src/App.spec.js \
  control-plane/src/api control-plane/src/components \
  control-plane/src/composables control-plane/src/router \
  control-plane/src/layouts/AppLayout.vue control-plane/src/style.css \
  control-plane/src/utils
rg -n 'v-html|innerHTML|axios|fetch\(|AbortController|setTimeout|setInterval|localStorage|sessionStorage|Authorization|Cookie|token|password|console\.|Math\.(ceil|floor|round)|slice\(' \
  control-plane/src/views/DatasetView.vue
```

预期：格式与受保护路径检查退出 0；status 精确显示 Files 节的一新增、两修改文件；禁止 HTML 注入、直接网络、取消、定时器、持久化、凭证、日志、客户端页数计算和切片的扫描无输出并按预期退出 1。暂存前后检查完整 `git diff --cached`；只暂存三个实施文件，不能混入工作树中已有的 `.idea`、`docs/issues` 或 `data-plane/**/target/` 变化。

## Acceptance

- `/datasets` 保持 M10-T02 已验证的 route name、路径和应用壳接入，router 文件无差异；页面标题仍为“数据查看”，旧未完成占位文案完全移除；
- 页面只创建一个 `useDatasetQuery()`；来源、数据集摘要和定义分别通过 M10 API 加载，records 只通过查询 composable 请求；
- 元数据 generation 使旧来源/数据集/定义的成功和失败不能覆盖新选择；retryable 元数据失败只按失败时上下文手动重试；
- 数据源、数据集、动态筛选、查询动作、状态/表格和分页按固定顺序组合，全部只连接依赖批准的公开 props/emits/actions；
- 选择数据集后不自动查询；提交前必须 await `validate()`，成功后立即提交唯一新鲜 `criteria()` 快照，无条件查询合法；
- 查询开始立即隐藏旧结果；LOADING 禁用筛选、重复查询和分页，reset 可立即失效请求并保留来源/数据集；
- SUCCESS 原样展示全部业务列、三个来源列和当前页，EMPTY 与 FAILURE 不把旧表格伪装成新结果；空结果仍允许改变 page size；
- page/pageSize/totals 完全服从 M12-T04 和服务端响应；翻页保留选择与筛选，改变 size 回第 1 页，客户端不计算或修正分页；
- query retry 只由 `canRetry` 控制并重放失败快照；页面只显示安全 message/requestId，不解释错误内部或传输对象；
- 来源/数据集切换清除旧筛选、结果、错误、页码和重试上下文；页面只读、无具体数据集分支、无网络旁路、缓存、持久化、定时器或 HTML 注入；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 11/11、完整前端 120/120、稳定 router 3/3、生产构建、范围、安全和格式门禁达到预期；
- 实现提交精确包含 Files 节三个文件，提交消息为 `feat(ui): complete dataset query page`。

## Risks

- `DataSourceSelect` 是 M11 已冻结且由 M12-T01 指定在页面组合层复用的来源选择器；其单来源默认事件可能在来源数组更新后同步触发，页面测试必须用 `flushPromises()` 消费来源和数据集清单的连续请求，不能用任意 timeout。
- 页面允许来源或数据集的新选择取代正在进行的元数据或 records 请求；两个独立 generation 分别保护元数据与查询结果，切换处理器必须先 reset 查询，避免旧 records 在新定义加载期间落地。
- `definition.filters` 必须以 API 返回的新数组引用直接传给 `DynamicFilterForm`；不得在页面原地改写或复用旧定义数组，否则 M12-T01 的 watch reset 边界会失效。
- EMPTY 下必须同时显示空态与分页；若只显示空态，用户无法在零结果时切换 page size，会破坏 M12-T03/M12-T04 的批准合同。
- jsdom 不完整模拟原生 Tab 顺序；单元测试验证可聚焦控件、DOM 顺序、真实键盘选择和原生 button 类型，完整浏览器键盘闭环留给 M14-T03。
- 当前工作树存在与本任务无关的 `.idea`、`docs/issues` 和后端 `target/` 变化；设计、交接和后续实施提交必须使用精确路径，检查完整暂存区并保留这些用户变化。
