# ISSUE-004：UI 改版技术实现设计

依据：[已确认的视觉方案](../issues/proposals/ISSUE-004-ui-visual-concepts.md) · [HTML 参考](../issues/proposals/ISSUE-004-ui-visual-concepts.html) · [问题](../issues/problems/ISSUE-004-ui-visual-redesign.md)。

执行入口：[任务看板](../task-handoffs/ISSUE-004/ISSUE-004-task-board.md) · [实施计划](../superpowers/plans/2026-09-07-issue-004-ui-redesign.md)。本文件是 ISSUE-004 的总体技术设计，不替代后续单项任务的专属设计。本轮只制定技术方案和拆分任务。

## Goal

将已确认的数据工作台方案落到现有 Vue 前端：统一冰川白及自定义主题、侧栏导航、设置页、下载与查看布局，并保留元数据驱动、接口契约和业务状态。

## Scope

- 实施位于 `control-plane`，沿用 Vue 3、Vue Router、Element Plus、Axios、Vitest 和 Playwright；不引入 Pinia、主题库、日期库或新的外部组件库。重复且职责相同的 UI 在现有项目内抽取为可复用 Vue 组件。
- 覆盖全部已接入数据集，而非 HTML 中的 daily、weekly 两个示例。
- 新增前端主题偏好；日期表单沿用接口元数据定义，设置不调用后端。
- 后端代码、YAML、OpenAPI、数据库、依赖版本及其他 ISSUE 不在本次范围。保留请求路径、方法、字段、响应、状态码和错误语义。
- HTML 的样例行数、样例下载结果、状态预览工具条、方案说明区不进入正式产品。不增加监控、任务历史、后台队列、跨设备同步。

## Approach

### 1. 现有实现与改动边界

| 现有位置 | 已有能力 / 差异 | 实现决定 |
| --- | --- | --- |
| `src/layouts/AppLayout.vue`、`src/router/index.js` | 顶部双入口；普通 RouterView 会卸载业务页 | 改为侧栏三入口；缓存两个业务页实例 |
| `src/style.css` | 少量全局样式；`body` 最小宽度 1280px | 使用语义色变量、Element Plus 映射和响应式布局 |
| `src/composables/useDownloadFlow.js` | 单次 POST；已有请求快照、状态、重试与过期响应保护 | 保留现有流程，每次提交一次请求 |
| `src/components/download/DynamicParameterForm.vue` | 元数据驱动七类参数控件与校验 | DATE 保留单日期，DATE_RANGE_MEMBER 保留原起止控件，仅调整呈现 |
| `src/components/dataset/DynamicFilterForm.vue` | 已有独立交易日 / 公告日起止控件 | 复用现有字段、校验和日期格式，仅调整布局 |
| `src/composables/useDatasetQuery.js` | 请求快照、分页、每页条数、重试、过期响应保护 | 保留流程；界面调整不重新实现查询状态机 |
| `src/components/dataset/DatasetTable.vue` | 全业务列、来源列、固定列、长文本提示 | 保留数据与列顺序，补齐数字对齐和主题样式 |

所有 `src/...` 路径均相对 `control-plane`。

### 2. 主题：纯计算 + 一个应用级状态

新增 `src/utils/theme.js`，从 HTML 的 `mix`、相对亮度、对比度和 `applyTheme` 中提取纯计算，不复制 DOM 操作。导出：

```js
DEFAULT_ACCENT // '#2857b4'
createTheme(value) // 合法 HEX -> { requested, applied, colors }; 非法 -> null
contrastRatio(foreground, background) // number
```

`colors` 固定包含 `bg`、`surface`、`raised`、`nav`、`line`、`text`、`muted`、`accentBg`、`accent`、`success`、`error`。默认值严格使用视觉方案；自定义配色沿用 HTML 的混色比例。将输入向黑色以 1% 步长混合，取在五种表面及白色上均达到 5.5:1 的第一个操作色。背景从用户输入色生成，操作色使用校正值；成功 `#14785e`、错误 `#b72d47` 固定。

新增 `src/composables/useTheme.js`，由 `App.vue` 初始化一次并通过 Vue `provide/inject` 共享；主题状态随应用实例创建，避免单例污染测试。对外提供：

```js
createThemeState() // { requested, applied, storageStatus, apply(value), reset() }
useTheme() // 获取 App 提供的同一状态
// apply(value) -> boolean；非法值返回 false，当前主题和存储均不改变。
// storageStatus: 'saved' | 'preview-only'
```

在挂载业务内容前读取 `localStorage['tensor-issue004-accent']`；只存标准化的输入 HEX。缺失或损坏值回退默认；读取或写入抛错均不阻断页面，显示“仅本次预览”。恢复默认重新应用完整默认 palette 并尝试保存。HEX 草稿属于设置页本地表单，不把未提交的输入混入已应用状态。

`style.css` 声明默认 `--tensor-*` 变量，初始化后在 `document.documentElement` 更新同名变量。映射至少覆盖：

| Tensor 角色 | Element Plus 变量 |
| --- | --- |
| accent / accentBg / raised | `--el-color-primary`、主色 light/dark 系列及选中/悬停背景 |
| bg / surface / nav | `--el-bg-color-page`、`--el-bg-color`、`--el-bg-color-overlay` |
| text / muted | `--el-text-color-primary`、`--el-text-color-regular`、`--el-text-color-secondary` |
| line / raised | border、fill、disabled、table 背景与边框变量 |
| success / error | `--el-color-success`、`--el-color-danger` 及其浅色背景 |

主按钮的普通、悬停和按下态使用满足对比度的深色，不能机械地把按钮悬停背景映射成浅色。日期弹层、下拉、tooltip、固定表格列从根节点继承变量，避免 scoped 样式只覆盖当前页。对比度检查用实际计算样式补充纯函数测试。

### 3. 导航、设置与状态生命周期

新增 `/settings`（路由名 `settings`）及 `SettingsView.vue`；保留 `/downloads`、`/datasets`、根路径重定向和 404。导航继续使用 `RouterLink`，保留 `aria-current`、键盘焦点与可辨识的选中态。

在 `AppLayout.vue` 使用 RouterView 插槽与 `KeepAlive`，只缓存具名 `DownloadView`、`DatasetView`，容量为 2。使用稳定路由名作为业务实例 key，不以主题或筛选条件生成 key。通过 `defineOptions({ name: ... })` 明确缓存名称。

- 首次进入业务页仍执行已有 `onMounted` 元数据加载；再次激活不刷新、不重置、不重新提交。
- 表单、日期、结果、错误、查询快照、页码、每页条数及下载状态均留在页面实例中，不复制进全局 store，不写入 localStorage。
- 进入设置后，用户此前主动发起的请求可以完成；返回时显示最新状态。设置本身及回到已缓存页不触发新 API 请求。
- 刷新只恢复主题。业务内存状态不做跨刷新恢复，也不把尚未收到响应的请求描述成已取消或已回滚。
- SettingsView 提供原生取色器（`input` 即时应用）、HEX 输入（提交生效）、实际应用色说明、就地错误和“恢复冰川白”。禁止导入 API 客户端。

### 4. 日期控件与原参数契约

2026-09-07 用户明确要求沿用现有约束：支持单日期就使用单日期，支持起始 / 结束日期就使用起始 / 结束日期。前端按运行时参数元数据呈现；每次有效提交调用一次现有下载接口，不增加日期枚举、请求拆分、批次计数或续传能力。此前提出的区间执行策略不再适用，无相关待确认项。

| 参数形态 | 表单与请求行为 |
| --- | --- |
| `DATE`，如 `trade_date` 或 `ann_date` | 每个原参数显示一个日期控件，保留原字段名、标签、必填及格式规则；如 daily / weekly 只有交易日期 |
| 原有互相关联的 `DATE_RANGE_MEMBER` | 保留两个独立控件及原参数，一次请求；如 trade_cal、new_share、namechange |
| 其他参数，包括 MONTH、证券代码、枚举、无参数及 margin 的 `exchange_id` | 按元数据保留原控件与单次请求，不补充日期字段 |
| 查看日期 | 保留 `tradeDateFrom/To`、`annDateFrom/To` 和可空、单边筛选；发送 `YYYY-MM-DD` |

复用 `useParameterForm.js`、`useDatasetFilters.js` 和 `utils/date.js` 的现有校验与归一化。下载表单继续暴露 `validate()`、`normalizedValues()`、`reset()`，查看表单继续暴露 `validate()`、`criteria()`、`reset()`；不新增表单接口或前端日期参数。下载 DATE / DATE_RANGE_MEMBER 仍归一化为 `YYYYMMDD`，MONTH 为 `YYYYMM`；原生范围保留日期先后校验、元数据规定的必填规则与错误聚焦。

流程接口保持 `submit(params)`，View 仍在校验通过后调用 `submit(parameterForm.value.normalizedValues())`。提交中的锁、过期响应保护、原参数快照和 `error.retryable` 判定均沿用现有逻辑；结果直接展示该次 DownloadResponse 的计数，重试仍为现有单次请求的原参数重试。

49 份生产 YAML 是日期能力依据：18 个仅有 `trade_date: DATE` 的接口仍显示一个交易日期；trade_cal、new_share、namechange 保留原生起止参数，其余照原元数据处理。查询筛选元数据与下载参数分开使用；daily 下载为单日期，不影响 daily 查看保留交易日起止筛选。

### 5. 下载与查看页面

下载页复用现有组件，改为配置 / 结果两个面板；数据源、接口并排，说明、动态参数和提交分组。右侧始终呈现 INITIAL、元数据加载、SUBMITTING、SUCCESS、EMPTY 或 FAILURE 的真实状态。复用 `AsyncStatePanel` 的 live region 和错误行为，`DownloadResult` 保持原 props，展示本次响应的三项真实计数与适用的重试入口。

查看页将数据源、数据集、动态筛选与查询 / 重置组成上方面板，表格与分页组成下面板。不添加元数据没有提供的筛选，不把参考页的 8 条分页覆盖现有 20 / 50 / 100、默认 50 的契约。查询中禁用筛选、重复查询和分页；保留既有重置 / 切换数据源使旧请求失效的行为。

表格保留元数据列顺序及全部来源列，证券代码存在时固定该列，否则保持当前首业务列策略。`LONG`、`DECIMAL` 右对齐并用等宽数字；数字字符串不经 `Number`、不舍入、不截断。已知行情字段 `change`、`pct_chg` 使用字符串符号与非零判断添加正号和涨跌色，零值中性色，负号保留；其他数值不按正负上色。

daily / weekly 可使用前端小型展示映射补足参考方案的中文列名（含“前收盘价”）及字段代码；其他列继续使用元数据 label。daily 的 `pct_chg` 标为百分数，weekly 标为比率，均保留接口原字符串，不套用 HTML 的浮点格式化或对全部数据集乘 100。上下文通过 DatasetTable 新增可选 `pluginId`、`apiName` props 传入；映射只作用于 `tushare_pro` 已明确的两个数据集，不影响其他插件同名接口。空值、长文本转义、tooltip 和入库时间格式沿用原逻辑。

### 6. 响应式与可访问性

从 HTML 提取布局尺寸与断点：默认侧栏 210px，1250px 以下 180px，1000px 以下 154px 且下载单列，680px 以下导航在内容上方。移除 `body` 的 1280px 限制，支持至少 360px；内容、面板和表格各层设置 `min-width: 0`，宽表格只在内部滚动。

复用原有控件标签、必填提示、`aria-invalid`、错误关联与聚焦逻辑；补跳转工作区链接。实际校验日期弹层、下拉与分页的窄屏可用性。遵循 `prefers-reduced-motion`，不添加依赖动画才能理解的反馈。

### 7. 组件抽象与复用

2026-09-07 用户补充实施规则：多次出现且职责相同的 UI 必须抽取为可复用组件，避免重复编写。该规则贯穿 T01–T05 的实现，T06 汇总验收。

- 先检查现有组件和工具，优先复用或小幅扩展；重复的面板结构、字段呈现、状态反馈按实际职责抽取。样式相同但结构、行为不同的部分共用 CSS 类和主题变量。
- 同一职责的有状态逻辑放入 composable，纯计算或属性处理放入工具函数；下载、查询各自的业务状态机及日期契约继续保持，不因外形相似而合并。
- 组件只用必要的 props、事件和 slots 表达实际差异，不为尚不存在的场景增加配置或通用框架。
- 抽取后同步替换所有相关重复实现；仅创建公共组件但调用处仍复制代码不算完成。每项任务完成前检查本次涉及的重复实现，发现重复即在该任务内处理。

## Files

| 任务 | 主要新建 / 修改文件 |
| --- | --- |
| T01 主题基础 | 新建 `src/utils/theme.js`、`src/composables/useTheme.js` 及各自 `.spec.js`；修改 `src/App.vue`、`src/App.spec.js`、`src/style.css` |
| T02 导航与设置 | 新建 `src/views/SettingsView.vue`、`.spec.js`；修改 `src/layouts/AppLayout.vue`、`.spec.js`、`src/router/index.js`、`.spec.js`、两个业务 View 的缓存名称、`src/style.css` |
| T03 日期控件与原参数契约 | 调整 `src/components/download/DynamicParameterForm.vue`、`src/components/dataset/DynamicFilterForm.vue` 的控件呈现及各自 `.spec.js`；仅在覆盖缺失时补充 `src/views/DownloadView.spec.js` 的请求回归；日期工具、表单 composable、下载流程和 API 客户端作为只读依赖 |
| T04 下载页 | 修改 `src/views/DownloadView.vue`、`.spec.js`、`src/components/download/` 既有组件及相关测试、`src/components/common/AsyncStatePanel.vue`、`.spec.js`、`src/style.css` |
| T05 查看页 | 修改 `src/views/DatasetView.vue`、`.spec.js`、`src/components/dataset/` 既有组件及相关测试、`src/utils/format.js`、`.spec.js`、`src/style.css` |
| T06 验收 | 新建 `control-plane/playwright.ui.config.js`、`control-plane/e2e/ui-redesign.spec.js`、`docs/verification/ISSUE-004-ui-redesign.md`；更新 ISSUE 索引及问题阶段 |

各任务可在 `src/components/common/` 或对应业务组件目录抽取必要的共享组件，在 `src/composables/`、`src/utils/` 抽取共用 UI 逻辑，并修改其实际调用处。具体文件和职责写入该任务专属设计；验证优先复用现有行为测试，仅补充未覆盖的边界。此范围不改变上表只读业务依赖的约束，不建立额外框架或全局业务 store。

## Tests

在 `control-plane` 使用 Node `>=24.15.0 <25`。本机默认 shell 是 Node 22；命令前可加 `PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH`，该机器路径不写入项目配置。

```sh
npm test
npm run build
npx playwright test --config=playwright.ui.config.js --project=chromium --workers=1 --retries=0
```

- 单测：主题默认值、5.5:1、HEX 错误不变更、存储降级；路由往返保留表单 / 分页 / 结果 / 在途请求且不重发；下载单日期与原生起止参数分别呈现，每次有效提交只发送一次原契约请求；查询日期范围、数字精度、全部宽列、来源列和分页快照。
- 复用检查：逐项检查本次改动中的重复结构与逻辑，记录共享组件 / 函数及实际使用位置，确认相关调用处均已接入。运行受影响调用处的行为回归，覆盖参数、事件、禁用、错误关联与聚焦；不以组件名称或快照断言代替行为验证。
- 浏览器：新增独立配置启动 Vite preview 的最新构建，限定只发现 `ui-redesign.spec.js`，通过 Playwright route 返回符合现有 API DTO 的固定数据。覆盖三页、所有反馈状态、键盘、日期弹层、默认及极亮 / 极暗 / 自定义主题、存储失败、切页保留状态和全程请求断言。设置页新开及变更主题均为零 API 请求；已有在途响应与设置触发请求分开统计。
- 元数据覆盖：读取 `docs/data-template/manifest.json` 的 `interfaces` 确认 49 个 `api_name`；参数与筛选依据现有 `e2e/tushare-metadata.spec.js` 的 `PARAMETER`、`EXPECTED_ROWS` 和 `expectedFilters` 契约表，字段列表取各样例 JSON 的 `fields`，构造现有 API / dataset DTO。不要导入整份旧 spec 而触发其 JVM 生命周期，也不修改旧脚本的冻结证据。数值精度、DATE / MONTH / TEXT 等类型另设与生产 YAML 一致的代表响应；该浏览器矩阵证明 UI 覆盖，不能冒充后端元数据一致性验证。
- 布局：1440×1080、1024×768、768×1024、390×844、360×800；页面整体无横向溢出，155 列表格内部可滚动，固定列背景正确。主题切换前后同一数据状态的控件矩形保持一致。
- 验收不能把 HTML 的 `scripts/verify-ui-concept-theme.cjs` 通过当作正式前端通过；旧脚本保留为方案参考检查。
- 旧 `control-plane/e2e/*.spec.js` 会启动自有 JVM / 数据库环境，其中还有真实 Tushare 测试；本次不直接运行无筛选的 `npm run test:e2e`。既有后端证据仅作契约输入，不声称本轮重新验证真实上游、发布或安全门禁。

## Acceptance

1. 三个页面符合已确认的布局与主题规则，全部已接入元数据继续可用。
2. 应用 / 重置 / 恢复主题与存储降级有效，根变量和 Element Plus 弹层一致，设置无 API 请求。
3. 往返设置保留业务日期、筛选、分页、结果、错误和操作状态，不重复下载或查询。
4. 日期控件数量、字段和必填规则与各自元数据一致；支持范围时起止独立输入；每次有效下载提交只发送一次原契约请求，查询筛选沿用现有范围语义。
5. 单次下载和查询的接口契约、精确数字、全业务列、来源列、分页与重试通过回归。
6. 重复且职责相同的 UI / 逻辑已抽取并在相关调用处复用，接口保持简单；验收记录包含共享实现、实际使用位置及受影响行为的回归结果。
7. 上述单测、构建、浏览器场景、复用和视觉检查通过，T06 保存命令、版本、结果和截图位置后，才能关闭 ISSUE-004。

## Risks

- 后端数值以字符串保持精度，且日 / 周涨跌幅单位不同；UI 不得复制样例的浮点运算。
- 老布局测试检查双导航和原 CSS 选择器，应更新视觉断言并保留行为断言；不为让新页面过测试而削弱 API、精度、状态及元数据覆盖。
- 现有构建有大于 500 kB 的 chunk 提示；本任务不追加无关的打包优化。浏览器验收证明当前 UI 和客户端契约，不代替既有真实后端验收。
