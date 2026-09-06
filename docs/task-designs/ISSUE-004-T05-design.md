# ISSUE-004-T05：查看工作台与精确表格展示

## Goal

查看页形成上方筛选、下方结果的工作台，完整保留元数据列、数值精度、日期筛选和查询快照。

## Scope

调整 DatasetView、选择器、筛选布局、表格和分页呈现；下载与查看实际共用选择、面板和反馈组件。业务 composable、API、日期工具、生产 YAML 与依赖只读，不新增查询参数、数据转换或业务状态。

## Approach

### 页面与共享选择

沿用 T04 的 WorkbenchPanel（headingId/title/meta/default slot）、AsyncStatePanel（state/title/message/requestId/retryLabel/retry）及 `.workbench-selects`、`.setup-body` 样式。保留 DatasetView script 的元数据加载、过期保护、query、reset、changePage、changePageSize、retry。仅导入公共面板并给 DatasetTable 传递所选 pluginId/apiName。

- PageHeading 后放筛选面板，headingId=`dataset-config-title`、title=`查询配置`、meta 为所选 pluginId。body 使用公共 `.setup-body`，查看页补底部padding24px，内部各组间隔24px；数据源和数据集并排，随后是原 DynamicFilterForm，最后原“查询 / 重置”操作。无筛选定义也保留表单实例和查询操作，提示“此数据集无需填写筛选条件。”。
- 下方面板 headingId=`dataset-result-title`，title 为 definition.displayName 或“查询结果”，meta 为所选 apiName。两个面板间隔24px，各层 min-width:0。
- 元数据加载/失败、未选择、未查询、查询中、查询失败、空状态原有条件和文案原样移入结果面板，继续使用公共反馈；成功显示 DatasetTable。EMPTY 和 SUCCESS 之后共用一个 DatasetPagination，条件为这两个状态且 result 存在；加载时原表格/分页仍隐藏。
- 查询时动态筛选和“查询”禁用；来源/数据集切换和“重置”保留原有使在途响应失效的行为，不新增禁用规则。
- DynamicFilterForm 的字段和接口完全不变，仅把桌面布局改为三等列，1000px 以下两列，680px 以下一列；交易日和公告日起止仍是独立 MetadataField。长标签自然换行。

新增 `src/components/common/CatalogSelect.vue`，统一 ApiSelect/DatasetSelect 的分组搜索与控件结构。props 为 `id`、`label`、`items`（必需），`modelValue`（默认空字符串）、`disabled`（默认false）、`emptyText`、`noMatchText`（调用方传原文案）。只发 `update:modelValue`；placeholder 由“请选择”加 label 得到。items 使用原 apiName/displayName/category 元数据，不复制或改变顺序。内部一个 query ref、一个 computed 按原算法 trim/lowercase 搜索两种名称并用 Map 保留分类顺序。Escape capture 清空 query；disabled 时不发值更新；empty slot 区分无数据与无匹配。

ApiSelect、DatasetSelect 保持现有外部 props、事件和根类，作为薄包装传入各自原 ID、标签、空提示和 items，并转发事件。公共组件负责 label、el-select、el-option-group、选项名称及代码。代码与选项均文本插值，选择器宽度100%、各层 min-width:0。沿用现有 DataSourceSelect，不改其默认选择和可用性规则。

### 精确表格

DatasetTable 新增可选字符串 props `pluginId`、`apiName`，默认空字符串。displayColumns 始终按原 columns 后接 source_plugin/source_api/ingested_at，不重排、不裁剪、不增加工具列。固定策略仍为有 ts_code 固定它，否则固定首业务列；继续用现有 stickyStyle 保持物理元数据顺序。body 固定列背景为 `--tensor-surface`，header 固定列为 `--tensor-raised`；hover 使用主题 raised 表面。保留原最小宽度140、长文本240、入库时间180、tooltip与loading行为。表格外层保留原 max-width/overflow-x，补 min-width:0、tabindex=0、role=region、aria-label=“数据表格，可横向滚动”和可见焦点；内部滚动不能撑开页面。

LONG/DECIMAL 用 el-table-column align=right，并给数值文本等宽数字样式。空值、DATE、入库时间仍通过 formatCell；不使用 Number、parseFloat、舍入或百分比换算。`format.js` 新增纯函数 `decimalSign(value)`：只对 string/number 的普通十进制文本（可选一个正负号、整数部分及可选小数部分）判断；无1–9数字、空值、非法文本都返回0，否则负号开头返回-1，其余返回1。全程字符串判断，不修改输入；现有 formatCell 接口不变。

仅 LONG/DECIMAL 且列名为 change/pct_chg 时使用 decimalSign 表示行情方向：非零正数前加“+”（已有“+”不重复）、负数字符串保留原负号，零值包括“-0.0000”保留原字符串且中性色。正数使用 `--tensor-error`（参考页上涨红色）、负数使用 `--tensor-success`（下跌绿色），不把涨跌当业务成功/失败。其他数值列不按正负着色；非法文本和空值继续原呈现。

表头小型常量映射仅在 pluginId=tushare_pro 且 apiName=daily/weekly 时启用：ts_code→证券代码、trade_date→交易日、open→开盘价、high→最高价、low→最低价、close→收盘价、pre_close→前收盘价、change→涨跌额、vol→成交量、amount→成交额、source_plugin→来源插件、source_api→来源接口、ingested_at→入库时间。pct_chg 在 daily 显示“涨跌幅（%）”，weekly 显示“涨跌幅（比率）”。已映射表头下显示原字段代码；其余表头保持元数据 label，不影响其他插件同名接口。weekly原列顺序（close在open前）必须保留。不因显示映射变更原 columns 对象；el-table-column 的 label 与实际显示标题一致。

### 分页与响应式

保留 DatasetPagination props/events、总数、总页数、默认50及20/50/100选择、disabled事件保护。同一分页组件只渲染一次；容器 gap12px、可换行、padding24px、上边框。el-pagination 使用 pager-count=5，内部 flex-wrap；680px 以下 page-size 选择独占一行，其余按钮在可用宽度内排列，不隐藏总数或页码、不改分页请求。1000px 以下面板间隔18px，680px 以下面板body和分页padding18px。

## Files

- 新建 `control-plane/src/components/common/CatalogSelect.vue`。
- 修改 `control-plane/src/components/download/ApiSelect.vue`、`src/components/dataset/DatasetSelect.vue`，接入共享选择器；原测试保留，仅补下载选择器缺失的Escape回归。
- 修改 `control-plane/src/views/DatasetView.vue`、`.spec.js`，组合面板、共用分页、传表格上下文。
- 修改 `control-plane/src/components/dataset/DynamicFilterForm.vue`、`DatasetTable.vue`、`DatasetTable.spec.js`、`DatasetPagination.vue`；必要的共享布局写入 `src/style.css`。
- 修改 `control-plane/src/utils/format.js`、`format.spec.js`，集中字符串符号判断。

## Tests

在 control-plane 使用 Node24.15.0，先补 decimalSign 与真实表格用例，运行确认新增行为失败，再实现：

```sh
npm test -- src/components/dataset/DatasetTable.spec.js src/utils/format.spec.js
```

覆盖超长精确数字、正数0.0100→+0.0100、已有正号、负数、-0.0000中性色、普通持仓数不着色、空值/非法文本不改变；daily/weekly不同单位与前收盘价、其他插件同名API仍用原label、原metadata对象不变。155列、无ts_code固定首列、长文本转义tooltip、入库时间、loading既有测试保持；数字列明确断言右对齐。

选择器现有49项分类顺序、中英文搜索、空提示、键盘/禁用/事件保持；补ApiSelect搜索后Escape还原全部选项。DatasetView原回归继续覆盖查询前校验、单边日期、重置/来源切换使旧响应失效、精确分页快照及错误重试；补表格上下文传递和无筛选数据集可提交的行为。面板纯样式不新增镜像测试。

```sh
npm test -- src/views/DatasetView.spec.js src/components/dataset src/composables/useDatasetQuery.spec.js src/composables/useDatasetFilters.spec.js src/utils/format.spec.js src/api/api.spec.js
npm test -- src/views/DownloadView.spec.js src/components/download/ApiSelect.spec.js src/components/common src/layouts/AppLayout.spec.js
npm test
npm run build
git diff --check
```

全部退出0，完成独立规格/质量审查后用明确路径加入Git并独立提交。真实宽表滚动、分页窄屏、主题弹层与颜色在T06组合验收，不用jsdom样式断言代替浏览器证据。

## Acceptance

上筛选/下结果布局、全部状态和无筛选数据集可用；原查询/日期/重置/分页/重试/缓存行为通过回归；155列与精确字符串不丢失，daily/weekly映射范围及单位正确；下载/查看实际共用CatalogSelect、WorkbenchPanel和AsyncStatePanel，分页重复结构移除。

## Risks

Element Plus固定列和tooltip需要真实浏览器验证；窄屏分页必须保留全部控制而不是仅缩小字体。高精度数字不能转浮点，weekly不能乘100；共享选择器必须保留下载与查看各自外部接口。现有设计和直接依赖一致，无未解决的需求决定。
