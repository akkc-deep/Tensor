# M12-T02 全字段、固定列和横向滚动表格——任务设计

任务编号：`M12-T02`

对应任务：[M12-T02](../superpowers/plans/tensor-modules/M12-dataset-ui.md#task-m12-t02-全字段宽表35h)

实施产物：按数据集定义原序展示全部业务字段与三个来源字段、具有确定固定列和横向滚动行为的只读数据表格

## Goal

在现有 Vue 3、Element Plus、Vitest、M10-T04 格式化边界和 M12 数据集元数据合同上，交付可复用的 `DatasetTable`。数据查看页后续只需传入当前数据集的业务列定义、当前服务端分页记录和加载状态，即可完整展示普通数据集及 `balancesheet` 的 152 个业务字段，不裁列、不损失高精度值，并在桌面内容区内通过左侧固定列与横向滚动查看宽表。

本任务只冻结只读表格的公开接口和显示规则。数据集定义/记录请求、查询状态、筛选、分页、总数和页面组合由 M12-T04/M12-T05 负责。

## Scope

包含：

- 创建只消费业务列元数据、当前页记录和加载布尔值的 `DatasetTable`；
- 按业务列输入顺序完整渲染，并在末尾固定追加 `source_plugin`、`source_api`、`ingested_at`；
- 有 `ts_code` 时只固定该业务列，否则只固定第一个业务列；
- 为全部列提供单行省略和纯文本 tooltip，并使用项目所有者批准的普通、长文本、入库时间列宽；
- 复用 M10-T04 `formatCell` 展示空值、日期、入库时间和高精度字符串；
- 以 Element Plus loading 遮罩和 `aria-busy` 表达加载状态，不改变或缓存传入数据；
- 用一份真实组件测试覆盖任务卡列出的列顺序、152 列、横向滚动、固定列、格式化、tooltip 和加载行为；
- 执行严格 RED、聚焦测试、完整前端回归、生产构建和范围/安全检查。

排除：

- 不修改 `DatasetView.vue`、router、layout、全局样式、API 客户端、依赖或配置；
- 不请求数据，不管理查询、失败、重试、请求世代、筛选、结果总数、页码或每页条数；
- 不创建 M12-T03～T05 的分页、查询 composable 或页面集成；
- 不从 `items` 的键集合、具体 `apiName`、数据库类型或字段名猜测业务列元数据；
- 不裁剪业务列，不隐藏来源列，不渲染内部 `business_key`，也不在前端排序或重排记录；
- 不提供排序、列配置、行选择、新增、编辑、删除、导出或 HTML 渲染；
- 不解析、计算、舍入或本地化 `DECIMAL`/`LONG` 字符串，不引入第三方表格、tooltip、日期或格式化依赖。

## Approach

### 公开接口与数据边界

`DatasetTable.vue` 的公开接口固定为：

```text
props:
  columns: DatasetColumn[]，必填；仅含服务端数据集定义中的业务列，已按 displayOrder 排序
  items: Array<Record<string, string|null>>，必填；当前页记录，键顺序由 API 保证
  loading: boolean，默认 false
emits: none
expose: none
```

组件不接收 `apiName`、`fixedColumn`、分页或时区 prop。M09 已保证 `columns` 非空、唯一且按 `displayOrder` 输出，分页记录的每行键集合和顺序等于业务列原序加三个来源列；组件信任这条服务端边界，不复制运行时 schema，也不从第一条记录反向生成表头。

业务列数组只读使用且不排序、不改写。组件以新的显示列数组追加三个固定来源描述符：

| name | label | display type | min-width |
|---|---|---|---:|
| `source_plugin` | `source_plugin` | 原字符串 | 140px |
| `source_api` | `source_api` | 原字符串 | 140px |
| `ingested_at` | `ingested_at` | M10-T04 入库时间 | 180px |

业务列保持服务端 `name` 作为记录键、`label` 作为表头、`logicalType`/`longText` 作为展示元数据。普通业务列 `min-width=140px`，`longText === true` 的业务列 `min-width=240px`。这些宽度以及来源列标签来自项目所有者 2026-09-05 的批准，不由实现者重新选择。

### 列顺序、固定列与横向滚动

每个业务列按 `columns` 输入次序创建一个 `el-table-column`，随后严格创建三个来源列。即使 `items` 为空仍展示完整表头；记录中额外键不创建列，缺少键只会让对应单元格按 `undefined` 显示 `--`，组件不静默改写合同。

固定列名按以下唯一规则计算：

1. 业务列中存在 `name === 'ts_code'` 时固定 `ts_code`；
2. 否则固定 `columns[0].name`；
3. OpenAPI 已要求业务列非空；若测试或调用方违反合同传入空数组，不固定任何来源列，也不在组件内发明业务列。

只有该业务列设置 `fixed="left"`，其他业务列和三个来源列均不固定。组件外壳固定 `max-width: 100%` 和 `overflow-x: auto`；所有列设置上述确定 `min-width`，因此 152 个业务列加三个来源列完整渲染并形成水平溢出，由 Element Plus 表格滚动区域和外壳共同保证页面不裁列。组件不设置前端 `sortable`，不添加 selection/index/expand 列。

### 单元格显示与 tooltip

所有列都设置 Element Plus `show-overflow-tooltip`，单元格使用单行省略；tooltip 内容只来自同一个格式化后的显示值。默认插槽用 Vue 文本插值输出，不使用 `v-html`、`innerHTML` 或自定义 HTML renderer，因此形似标签的字符串在单元格和 tooltip 中均保持纯文本。

每个单元格调用：

```text
formatCell(item[column.name], column)
```

业务列传入原始 `DatasetColumn`；来源列传入上述固定描述符。由 M10-T04 保证：

- `null`/`undefined` 显示 `--`，数值 `0` 和空字符串保持原义；
- `logicalType === 'DATE'` 的严格日期保持 `YYYY-MM-DD`；
- `ingested_at` 按默认 `Asia/Shanghai` 显示到秒；
- `DECIMAL`/`LONG` 十进制字符串原样显示，不进入 `Number`、`parseFloat`、`parseInt` 或 `BigInt`。

组件不捕获或替换格式化结果，不自行解释非法服务端值；M10-T04 会让非法日期/时间保持原值，从而暴露上游合同问题。

### 加载、空页与失败边界

根表格区域在 `loading=true` 时设置 `aria-busy="true"` 并启用 Element Plus `v-loading` 遮罩；`loading=false` 时为 `aria-busy="false"` 且无遮罩。加载只影响视觉/辅助技术状态，不复制、清空或隐藏 `columns/items`，旧结果何时隐藏由 M12-T04 查询状态负责。

`items=[]` 是合法空页：仍渲染全部列头，由后续页面状态决定是否挂载表格或显示空状态。本组件不显示总数、空结果文案或失败文案，不解释 `ApiError`/`ClientError`，不记录日志。

### 实现纪律

先完整创建唯一 spec，不创建生产组件；聚焦命令必须只因 `DatasetTable.vue` 不存在而形成收集阶段 RED。GREEN 只创建一个生产文件并复用 `formatCell`。不得删除断言、降低 152 列/来源列/精度/tooltip/固定列门禁，或修改测试配置、共享格式化工具和既有代码绕过失败。

## Files

创建且只创建以下 2 个实施文件：

- `control-plane/src/components/dataset/DatasetTable.vue`：全字段只读表格、固定列、横向滚动、格式化、tooltip 和加载状态；
- `control-plane/src/components/dataset/DatasetTable.spec.js`：六项真实组件行为测试。

不修改或删除其他生产、测试、依赖、配置和文档文件。初始实现提交固定为 `feat(ui): render complete dataset tables`，精确包含上述 2 个新增文件；本设计、交接和看板不得混入实现提交。

## Tests

所有 npm 命令从仓库根目录开始，使用 Node.js 24.15.0：

```bash
TENSOR_NODE=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin/node
TENSOR_NPM=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/lib/node_modules/npm/bin/npm-cli.js
"$TENSOR_NODE" --version
```

预期输出 `v24.15.0`。M12-T01 最终基线为 16 个测试文件、92 项测试；开始实施时须重新运行基线，若既有测试失败，先停止并定位，不能记作本任务 RED。

### 严格 RED

只创建完整 `DatasetTable.spec.js`，不创建 `DatasetTable.vue`，然后运行：

```bash
cd control-plane
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run src/components/dataset/DatasetTable.spec.js
```

预期命令非零，Vitest 只因 `DatasetTable.vue` 不存在而无法收集该测试文件；不得出现测试语法、依赖、Node、Element Plus、Vue setup 或既有测试失败。

### `DatasetTable` 六项

1. 以字面业务列/记录 fixture 证明业务表头和记录键严格保持输入顺序，随后只追加 `source_plugin`、`source_api`、`ingested_at`，来源标签等于字段名；记录额外键不生成列，输入数组和对象保持不变；
2. 传入 152 个业务列时精确创建 155 个 `el-table-column`，首尾名称和三个来源列位置正确，所有业务列均存在；表格外壳声明水平滚动且没有 selection/index/expand 列；
3. 含 `ts_code` 时只有该列 `fixed="left"`，不含时只有首个业务列固定，三个来源列始终不固定；
4. 通过真实单元格验证 `null`、`undefined`、数值 `0`、空字符串、高精度 `DECIMAL`、最大 `LONG`、严格 DATE 和 `ingested_at` 的精确可见文本，精度字符串未变化；
5. 验证全部列启用 `show-overflow-tooltip`，普通/长文本/入库时间列的 `min-width` 分别为 140/240/180；构造真实溢出并悬停后，tooltip 显示完整格式化纯文本，形似 HTML 的值不生成标签；
6. 切换 `loading` 证明 `aria-busy` 和 Element Plus loading 遮罩同步变化，列/记录不被清空；同时确认不存在排序、选择、编辑、删除或导出事件/控件。

每项测试在写测试体前都必须能命名一个会令其失败的生产缺陷；期望值使用字面结果或手工核对计数，不从组件实现共享生成逻辑。

### GREEN、回归与构建

最小创建 `DatasetTable.vue` 后运行：

```bash
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run src/components/dataset/DatasetTable.spec.js
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run
"$TENSOR_NODE" "$TENSOR_NPM" run build
```

预期：聚焦测试为 1 file / 6 tests 全部通过；在 16/92 基线上，完整前端回归为 17 files / 98 tests 全部通过；Vite 生产构建退出 0，只允许既有 Element Plus `Some chunks are larger than 500 kB after minification` 提示，不允许新增 Vue、Element Plus、可访问性、测试或编译 warning/error。

### 范围、格式与安全检查

```bash
cd ..
git diff --check
git status --short --untracked-files=all -- control-plane/src/components/dataset
git diff --exit-code -- \
  control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/playwright.config.js control-plane/src/api \
  control-plane/src/router control-plane/src/layouts \
  control-plane/src/views control-plane/src/style.css \
  control-plane/src/utils
rg -n 'v-html|innerHTML|axios|fetch\(|queryDataset|apiName|sortable|selection|parseFloat|parseInt|Number\(|BigInt\(' \
  control-plane/src/components/dataset/DatasetTable.vue
```

预期：格式和受保护路径检查退出 0；范围化 status 只新增 Files 节两个实施文件；禁止 HTML 注入、网络、具体数据集分支、排序/选择和数值转换扫描无输出并按预期退出 1。暂存前后检查完整 `git diff --cached`；只以精确路径提交两个任务文件，不能混入工作树中已有的 `.idea`、`docs/issues` 或 `data-plane/**/target/` 变化。

## Acceptance

- `DatasetTable` 只接收 `columns/items/loading`，不请求网络、不管理查询/分页/失败，也不修改输入；
- 业务列按元数据原序完整展示，三个来源列按固定顺序位于末尾；152 个业务列形成 155 个真实表格列且可横向滚动，无字段静默丢失；
- 有 `ts_code` 时只固定该列，否则只固定首个业务列，来源列不固定；
- 全部列单行省略并提供纯文本 tooltip；普通、长文本、入库时间列最小宽度精确为 140/240/180px，来源标签使用字段名；
- `formatCell` 保证空值、0、空字符串、DATE、`ingested_at` 和高精度字符串达到冻结显示结果，不发生数值转换或 HTML 渲染；
- `loading` 仅同步遮罩与 `aria-busy`，空页仍保留完整表头；组件不暴露排序、选择、列配置或写操作；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 6/6、完整前端 98/98、生产构建、范围、安全和格式门禁达到预期；
- 初始实现提交精确包含 2 个新增文件，提交消息为 `feat(ui): render complete dataset tables`。

## Risks

- jsdom 没有真实布局，tooltip 测试必须为目标单元格提供可控的 `scrollWidth/clientWidth`（或等价溢出条件）后触发真实 hover；不得退化为只断言 prop。
- Element Plus 同时在内部表格滚动层和外壳处理横向溢出；测试以 155 个列实例、确定最小宽度和外壳滚动合同证明不裁列，浏览器 152 列体验留给 M14-T03 E2E。
- `items=[]` 时表头仍存在；M12-T05 必须根据查询状态决定是否展示表格，不能把本组件的空记录误当成页面空状态实现。
- M10-T04 默认时区为 `Asia/Shanghai`，本任务接口没有时区 prop；后续若 M13 引入可配置展示时区，必须先扩展共享格式化边界和任务设计。
- 工作树存在与本任务无关的 `.idea`、`docs/issues` 和后端 `target/` 变化；设计、交接和后续实施提交必须使用精确路径，检查完整暂存区并保留这些用户变化。
