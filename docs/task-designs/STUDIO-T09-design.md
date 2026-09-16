# STUDIO-T09 数据表格、格式与分页

## Goal

Studio 数据查看页完整展示真实字段和服务端页数据，宽表、长文本与精确数值可读，分页沿用已提交条件。

## Scope

迁移表格、来源列、滚动和分页外观；验证 1024／1280／1440px PC、键盘、空结果、长文本和大整数。沿用 T08 筛选栏、查询状态和请求快照，不改后端契约、格式化规则或 Demo。

## Approach

- 视觉继承固定基准 `2ae5963` 的 `DatasetBrowser.vue`／`demo.css`。保留 Element Plus 表格和分页以复用完整字段、固定业务列、精确格式与直接选页能力，局部覆盖样式，不引入新的表格实现。
- 表格使用 7px 圆角、1px 边框、主题浅色表头和悬停底色；单元格横向 15px、纵向 14px，正文 14px、表头 12px。表头保留现有中文名称与行情字段编码、日线百分数／周线比率语义。
- 表格总高不超过 490px，表头在纵向滚动时可见；横向滚动仅发生在表格内部。原有证券代码列（没有时为首个业务列）固定逻辑不变。区域可聚焦，用左右键滚动；内部滚动区显式设为可聚焦，Tab 进入后可用 PageUp／PageDown 纵向翻阅。
- 不裁剪列和行；全部定义列后附三列 `source_plugin`、`source_api`、`ingested_at`。继续使用 `formatCell`、`decimalSign`，空值显示 `--`，空字符串／零值、大整数与 DECIMAL 字符串原样保留，入库时间使用上海时区。不增加数值转换、四舍五入或单位换算。
- 所有非空长文本保持单行省略，全文通过纯文本 tooltip 展示，支持悬停和键盘聚焦、全局 Escape 关闭；提示宽度适应桌面视口并对连续字符换行。超长提示限高 50vh，聚焦原单元格即可用上下键、PageUp／PageDown、Home／End 滚动全文，焦点不离开单元格。非长文本继续保留既有溢出提示。
- 分页左侧播报服务端总数和页数，右侧为每页条数与页码控件，使用 Studio 17px 纵向留白、12px 文字、32px 控件。保留 20／50／100、默认 50、上一页／下一页及直接选页；大页码允许控件自然扩宽和必要换行。
- 复用 `useDatasetQuery`：翻页使用已提交条件，改页大小回第一页；新查询从第一页开始；失败重试复用失败请求；等待中不重复发起分页。空结果沿用 T08 的空态和服务端 `1 / 0` 页语义，页大小仍可调整。

## Files

- `control-plane/src/components/dataset/DatasetTable.vue`：局部表格样式、限高、长文本键盘提示。
- `control-plane/src/components/dataset/DatasetPagination.vue`：分页局部样式，事件接口不变。
- `control-plane/src/components/dataset/DatasetTable.spec.js`：长文本键盘行为及既有完整字段／精度回归。
- `control-plane/e2e/studio-table.spec.js`：桌面对照、宽表滚动、行列完整性、精度／长文本、分页快照、空态和主题检查。
- `docs/verification/STUDIO-T09.md`、`docs/verification/studio-t09/`、任务板：实施和验收证据。

## Tests

- `cd control-plane && npm test -- src/components/dataset/DatasetTable.spec.js src/components/dataset/DatasetPagination.spec.js src/composables/useDatasetQuery.spec.js src/utils/format.spec.js src/views/DatasetView.spec.js`：格式、完整字段、键盘提示和分页语义通过。
- `cd control-plane && npm test` 与 `npm run build`：全量单元／集成和生产构建通过。
- 启动 `npm run dev -- --host 127.0.0.1 --port 4174 --strictPort` 后运行 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-table.spec.js studio-datasets.spec.js ui-redesign.spec.js studio-shell.spec.js`：HTTP 夹具下检查实际请求参数、152 业务列加来源列、超过 24 行、宽表和长文本、首尾页／大页码、空态、四主题和三种桌面宽度。
- `git diff --check`；检查最终截图并导出到验证目录，新文件加入 Git。

## Acceptance

表格符合 Studio 视觉规则；完整字段、来源、精度、日期、单位和空值语义不变；宽表不使页面溢出；键盘能滚动和操作分页／长文本；翻页和页大小变化不读取未提交草稿；受影响检查通过，截图与限制被记录。

## Risks

真实表格保留中文业务名称、来源列、直接选页和页大小，因此不会与 Demo 的截断样例逐像素等同。Element Plus 内部滚动和固定列需要浏览器验证。HTTP 夹具不替代依赖 MySQL／凭据的打包后端集成套件。
