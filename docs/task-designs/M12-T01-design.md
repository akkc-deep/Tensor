# M12-T01 数据集选择与动态筛选表单——任务设计

任务编号：`M12-T01`

对应任务：[M12-T01](../superpowers/plans/tensor-modules/M12-dataset-ui.md#task-m12-t01-数据集选择与动态筛选30h)

实施产物：由数据集摘要元数据驱动的受控数据集选择器，以及生成只读查询条件的动态筛选表单和状态 composable

## Goal

在现有 Vue 3、Element Plus、Vitest 和 M10 前端公共边界上，交付可复用的 `DatasetSelect`、`DynamicFilterForm` 与 `useDatasetFilters`。数据查看页后续只需传入当前来源的数据集摘要和所选数据集的 `filters` 元数据，即可完成 49 个 Tushare Pro 数据集及测试插件数据集的选择、核心字段筛选、客户端校验和稳定查询条件快照，而不需要按具体数据集名称编写分支。

本任务冻结数据集选择和筛选状态的公开接口。数据源加载、数据集元数据请求、分页记录查询、结果状态、表格和页码由后续 M12 任务在页面组合层负责。

## Scope

包含：

- 创建按 `category` 分组、可按 `apiName` 或 `displayName` 搜索的受控数据集选择器；
- 创建只根据 OpenAPI `DatasetFilter[]` 渲染 `ts_code`、`trade_date`、`ann_date` 的动态筛选表单；
- 创建管理筛选值、字段错误、首错和最后一次成功校验快照的 `useDatasetFilters`；
- 将 `ts_code` 规范化为去除首尾空格后的大写 `代码.市场`，严格校验真实 ISO 日期，同时保持查询日期为 `YYYY-MM-DD`；
- 支持空条件、单边日期范围和多个条件的 AND 查询条件快照；
- 在手动重置或数据集筛选元数据切换时清空筛选、错误和成功快照；
- 用三份测试覆盖任务卡列出的数据集切换、筛选组合、校验、重置、可访问性和禁用行为；
- 执行严格 RED、聚焦测试、完整前端回归、生产构建和范围/安全检查。

排除：

- 不创建或修改数据源选择组件；`DataSourceSelect` 的页面复用与来源状态属于 M12-T05；
- 不修改 `DatasetView.vue`、router、layout、全局样式、API 客户端、依赖或配置；
- 不调用 `listDataSources`、`listDatasets`、`getDataset` 或 `queryDataset`，不管理网络、加载、失败、重试或请求世代；
- 不管理或清空查询结果、页码、表格和分页状态；页面后续组合 `useDatasetFilters.reset()` 与 `useDatasetQuery.reset()` 完成整体重置；
- 不创建 M12-T02～T04 的表格、分页或查询 composable，不实现 M12-T05 页面集成；
- 不从 `columns`、`queryMode` 或具体 `apiName` 推导筛选，不为 `daily`、`balancesheet` 或其他数据集硬编码分支；
- 不增加运行时 schema、Pinia、第三方表单/日期库、缓存、持久化、排序、编辑、删除、导出或 HTML 渲染。

## Approach

### 组件与状态边界

采用“受控选择器 + 表单外壳 + 独立筛选 composable”，沿用 M11 的 `ApiSelect`、`DynamicParameterForm` 和 `useParameterForm` 结构，但不复用下载参数的紧凑日期转换或必填规则。

`DatasetSelect.vue` 的公开接口为：

```text
props:
  modelValue: string，默认 ''
  datasets: DatasetSummary[]，必填
  disabled: boolean，默认 false
emits:
  update:modelValue(apiName: string)
```

组件按输入数组首次出现的 `category` 顺序分组，并保持各组内原始顺序。本地搜索文本去除首尾空格并转小写，同时匹配 `apiName` 与 `displayName`；搜索不得修改输入数组、当前选择或分组元数据。选项可见文本为 `displayName` 和 `apiName`，值只使用 `apiName`。组件使用可见“数据集”标签、固定 `id="dataset-select"`、`filterable`、`default-first-option`、`暂无数据集` 和 `无匹配数据集`，键盘行为交给 Element Plus；禁用时不产生新的选择事件。组件不知道当前数据源，也不发起请求。

`DynamicFilterForm.vue` 的公开接口为：

```text
props:
  filters: DatasetFilter[]，必填
  disabled: boolean，默认 false
expose:
  validate(): Promise<boolean>
  criteria(): Record<string, string>
  reset(): void
```

组件通过 `toRef(props, 'filters')` 创建一次 `useDatasetFilters`。`validate()` 调用状态校验；失败后等待 DOM 更新并聚焦 `firstError` 对应的第一个输入。`criteria()` 和 `reset()` 原样委托 composable。组件不发出网络请求或结果事件。

`useDatasetFilters(filters)` 接收一个 `Ref<DatasetFilter[]>`，只导出并返回：

```text
values: reactive object
errors: reactive object
firstError: Ref<string|null>
setValue(name, value): void
validateValues(): boolean
criteria(): Record<string, string>
reset(): void
```

`values` 和 `errors` 的键使用 M10-T03 已冻结的查询参数名。`criteria()` 只在最近一次 `validateValues()` 成功后返回新的浅拷贝；尚未校验、校验失败、值已修改或已重置时返回新的空对象。空筛选和全部留空是合法查询，因此成功校验后同样返回 `{}`；M12-T05 必须保持“先 `validate()`、再读取 `criteria()`、再查询”的调用顺序。

### 元数据映射与渲染

筛选描述符只消费 OpenAPI 的三个封闭组合：

| 元数据 | 状态/查询键 | 控件与标签 |
|---|---|---|
| `ts_code + EQ + TEXT` | `tsCode` | `el-input`；`证券代码 (ts_code)` |
| `trade_date + BETWEEN + DATE_RANGE` | `tradeDateFrom`、`tradeDateTo` | 两个 `el-date-picker`；`交易日期开始 (trade_date)`、`交易日期结束 (trade_date)` |
| `ann_date + BETWEEN + DATE_RANGE` | `annDateFrom`、`annDateTo` | 两个 `el-date-picker`；`公告日期开始 (ann_date)`、`公告日期结束 (ann_date)` |

控件按 `filters` 数组顺序渲染；同一日期描述符的开始控件在结束控件之前。所有日期控件固定 `type="date"`、`value-format="YYYY-MM-DD"`，所有筛选均为可选。`trade_date` 与 `ann_date` 同时存在时使用字段名区分，不折叠为一个通用日期范围。不支持的描述符不产生控件；其契约错误由 M00/M09 的服务端元数据边界负责，本任务不复制运行时 schema。

控件 ID 与错误 ID 固定为：

```text
dataset-filter-<query-key>
dataset-filter-<query-key>-error
```

每个控件都有可见 `<label>`，错误存在时设置 `aria-invalid="true"` 和指向 `FieldError` 的 `aria-describedby`。`FieldError` 只显示固定本地错误文本，不使用 `v-html`，也不包含用户输入或元数据正则。

### 校验、快照与重置

`setValue(name, value)` 更新当前键，删除该键错误，按元数据顺序重新计算仍存在的首错，并立即废弃成功快照。禁用状态由表单阻止控件更新；composable 保持纯状态接口。

`validateValues()` 先清空旧错误、首错和快照，再按元数据顺序处理：

1. 空字符串、仅空白、`null` 和 `undefined` 均视为未提供并省略，不形成错误；
2. `tsCode` 去除首尾空格并转大写，必须匹配 `^[A-Z0-9]+\\.[A-Z0-9]+$`，否则在 `tsCode` 记录“请输入代码.市场格式，例如 000001.SZ”；
3. 非空日期必须由 M10-T04 `toApiDate` 判定为严格真实的 `YYYY-MM-DD`，但成功快照保留原 ISO 字符串；非法值在对应字段记录“请选择有效日期”；
4. 同一日期范围只有一端时合法；两端均非空且 `isRangeOrdered(from, to)` 为假时，在开始字段记录“开始日期不得晚于结束日期”；
5. 无错误时按元数据顺序保存所有非空规范化字段。多个字段同时存在于同一对象，由 M10-T03 作为独立 query 参数发送，服务端按 AND 处理。

`firstError` 是按可见控件顺序遇到的第一个错误键。任一错误存在时返回 `false` 且不保存部分快照；否则保存快照并返回 `true`。

`reset()` 清空 `values`、`errors`、`firstError` 和快照，再只为当前 `filters` 声明的查询键设置空字符串。对 `filters` 引用使用 `{ immediate: true }` 的 `watch` 调用同一 `reset()`；因此父页面切换到不同数据集并传入其筛选数组时，旧筛选和校验状态立即失效。该动作不接触数据源、数据集选择、结果或页码。

### 失败边界与实现纪律

本任务只产生本地字段错误，不解释 `ApiError`/`ClientError`。服务端成功 DTO 由 M10-T03 保持原样，`DatasetSummary.filters` 已由 OpenAPI 和后端校验形成封闭元数据；组件不得自行请求、重试、记录日志或显示服务端正文。

先一次性创建三份完整测试，生产模块不存在时必须只因目标 import 无法解析而形成 RED。GREEN 仅创建三个生产文件。不得删除断言、降低日期/代码校验、改为从列推导筛选，或通过修改配置、测试环境和既有代码绕过失败。

## Files

创建且只创建以下 6 个实施文件：

- `control-plane/src/components/dataset/DatasetSelect.vue`：受控、分组、可搜索的数据集选择器；
- `control-plane/src/components/dataset/DynamicFilterForm.vue`：元数据驱动的可访问筛选控件和公开表单方法；
- `control-plane/src/composables/useDatasetFilters.js`：筛选值、校验、成功快照和重置状态；
- `control-plane/src/components/dataset/DatasetSelect.spec.js`：选择器 4 项组件测试；
- `control-plane/src/components/dataset/DynamicFilterForm.spec.js`：表单 5 项组件测试；
- `control-plane/src/composables/useDatasetFilters.spec.js`：composable 6 项行为测试。

不修改或删除其他生产、测试、依赖、配置或文档文件。实现提交固定为 `feat(ui): add dataset selection and dynamic filters`，精确包含上述 6 个新增文件；本设计、实施计划、交接和看板不得混入实现提交。

## Tests

所有 npm 命令从仓库根目录开始，并把 Node.js 24.15.0 放在 PATH 首位：

```bash
export PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH"
node --version
```

预期输出 `v24.15.0`。M11-T05 完成证据记录的当前基线为 13 个测试文件、75 项测试；开始实施时须重新运行基线，若范围内既有测试失败，先停止并定位，不能把它记作本任务 RED。

### 严格 RED

只创建三份完整 spec，不创建三个生产模块，然后运行：

```bash
cd control-plane
npm run test:unit -- --run \
  src/components/dataset/DatasetSelect.spec.js \
  src/components/dataset/DynamicFilterForm.spec.js \
  src/composables/useDatasetFilters.spec.js
```

预期命令非零，Vitest 只因 `DatasetSelect.vue`、`DynamicFilterForm.vue` 和 `useDatasetFilters.js` 不存在而无法收集三个测试文件；不得出现测试语法、依赖、Node、Element Plus、Vue setup 或既有测试失败。

### `DatasetSelect` 4 项

1. 使用当前来源的 49 个数据集摘要，按元数据类别与输入顺序完整渲染，选项值均为唯一 `apiName`；
2. 搜索忽略大小写和首尾空格，同时匹配 `apiName`/`displayName`，清空搜索恢复原顺序且不改变当前选择或输入数组；
3. `modelValue` 完全受控，用户选择只发出一次 `update:modelValue`，组件不自行写回 prop；
4. 真实 combobox 可聚焦并由键盘选择首项；`disabled` 同时锁定 Element Plus 和原生输入，之后不再发出选择事件。

### `DynamicFilterForm` 5 项

1. 空 `filters` 不渲染控件，`validate()` 成功且 `criteria()` 返回新的空对象；
2. 仅代码、仅一个日期字段，以及 `trade_date`/`ann_date` 同时存在时，按描述符顺序渲染精确控件、可见字段名标签、ID 和 `YYYY-MM-DD` 配置，不读取 `apiName` 或 `columns`；
3. 通过真实组件更新输入后，`validate()` 生成去空格大写代码和保持 ISO 日期的条件；所有非空字段同时进入同一快照；
4. 非法代码、无效公历日期与两个逆序范围显示固定纯文本错误，设置 `aria-invalid`/`aria-describedby`，并只聚焦按控件顺序的首错；
5. `reset()`、替换 `filters` 和 `disabled` 分别清除表单状态、只保留新数据集控件并阻止更新，不修改 prop 元数据。

### `useDatasetFilters` 6 项

1. 空筛选和全部可选值为空均校验成功，产生新的 `{}` 快照；
2. `ts_code` 只映射到 `tsCode`，合法值去空格转大写，非法格式失败且不泄漏输入到错误；
3. `trade_date` 或 `ann_date` 只生成对应 `From/To`，接受单边范围，严格拒绝不存在日期并保持合法 ISO 字符串；
4. 三个筛选描述符生成五个固定 camelCase 字段，保持元数据顺序且不修改输入，用同一对象表达 AND；
5. 两个日期范围分别校验顺序，逆序只标记对应开始键，`firstError` 遵守元数据/控件顺序且任何错误都不保存部分快照；
6. 字段编辑清除自身错误并废弃旧成功快照；手动 `reset()` 和 `filters` 引用切换清空值、错误、首错和快照，只初始化当前声明键。

### GREEN、回归与构建

最小创建三个生产模块后运行：

```bash
cd control-plane
npm run test:unit -- --run \
  src/components/dataset/DatasetSelect.spec.js \
  src/components/dataset/DynamicFilterForm.spec.js \
  src/composables/useDatasetFilters.spec.js
npm run test:unit -- --run
npm run build
```

预期：聚焦测试为 3 files / 15 tests 全部通过；在记录的 13/75 基线上，完整前端回归为 16 files / 90 tests 全部通过；Vite 生产构建退出 0，只允许既有 Element Plus `Some chunks are larger than 500 kB after minification` 提示，不允许新增 Vue、Element Plus、可访问性、测试或编译 warning/error。

### 公开表面、范围与安全检查

```bash
cd ..
node --input-type=module -e 'import assert from "node:assert/strict"; const m=await import("./control-plane/src/composables/useDatasetFilters.js"); assert.deepEqual(Object.keys(m),["useDatasetFilters"]);'
git diff --check
git status --short --untracked-files=all -- control-plane
git diff --exit-code -- \
  control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/playwright.config.js control-plane/src/api \
  control-plane/src/router control-plane/src/layouts \
  control-plane/src/views control-plane/src/style.css
rg -n 'v-html|innerHTML|axios|fetch\(|listDataSources|listDatasets|getDataset|queryDataset|daily|balancesheet|parseFloat|parseInt|Number\(|BigInt\(' \
  control-plane/src/components/dataset/DatasetSelect.vue \
  control-plane/src/components/dataset/DynamicFilterForm.vue \
  control-plane/src/composables/useDatasetFilters.js
```

预期：公开导出、格式和受保护路径检查退出 0；status 精确显示 Files 节 6 个新增实施文件；禁止 HTML 注入、网络、具体数据集分支和数值转换扫描无输出并按预期退出 1。暂存前后都必须检查整个 `git diff --cached`，只暂存任务的 6 个文件，不能混入工作树中已有的 `.idea`、`docs/issues` 或 `data-plane/**/target/` 变化。

## Acceptance

- `DatasetSelect` 只依赖传入的数据集摘要，完整展示 49 项，按元数据分组并搜索 `apiName`/展示名，保持受控值、输入顺序和输入对象不变，支持键盘且正确禁用；
- `DynamicFilterForm` 只根据 `filters` 的三个合法组合渲染实际筛选字段，不检查列、查询模式或具体数据集名称；同时存在交易日期和公告日期时分别显示带字段名的开始/结束控件；
- `useDatasetFilters` 只映射 `tsCode`、`tradeDateFrom/To`、`annDateFrom/To`，支持无条件、单边日期和多条件 AND，代码规范化但 ISO 查询日期不转换为下载紧凑格式；
- 不存在日期、非法代码和逆序范围在客户端阻止形成条件快照；固定纯文本错误与控件 ARIA 正确关联，校验失败聚焦首错且不泄漏输入；
- 编辑、手动重置和数据集元数据切换均清空旧错误与成功快照；重置不改变当前来源/数据集，也不承担结果和页码状态；
- 三个生产文件不请求网络、不解释 API 错误、不持久化状态、不使用 `v-html`，不引入依赖或修改 M10/M11 和后续 M12 边界；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 15/15、完整前端 90/90、生产构建、公开导出、范围、安全、格式和 Git 跟踪门禁达到预期；
- 实现提交精确包含 6 个新增文件，提交消息为 `feat(ui): add dataset selection and dynamic filters`。

## Risks

- Element Plus 日期控件清空时可能发出 `null`；composable 必须把 `null` 与空字符串同样视为未提供，不能让可选筛选形成错误或发送空 query 参数。
- `criteria()` 对“成功的空条件”和“没有可用成功快照”都返回 `{}`；调用方必须遵守设计固定的 `validate()` 成功后再读取快照，不能仅凭对象是否为空判断有效性。
- 数据集切换依赖父层传入不同的 `filters` 数组引用；M12-T05 组合时应直接使用当前数据集摘要/定义中的数组，不得在父层复用并原地改写同一数组。
- OpenAPI 已封闭筛选描述符组合，本任务不增加第二套运行时 schema。后续若服务端合同扩展字段或控件类型，必须先修改 OpenAPI、API JSDoc 和本设计，而不是让组件静默推导。
- 工作树存在与本任务无关的 `.idea`、`docs/issues` 和后端 `target/` 变化；实施及文档提交必须使用精确路径、检查完整暂存区并保留这些用户变化。
