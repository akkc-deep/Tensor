# STUDIO-T09 验证记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T09-design.md`。
- 实施分支：既有 `feat/studio-frontend`，保留此前 T01–T08 的暂存改动。
- 视觉基准：`2ae5963` 的 `DatasetBrowser.vue` 和 `demo.css`；两文件与固定基准无差异。

## 实现结果

表格使用 Studio 的细边框、7px 圆角、浅色表头、14px 单元格留白和主题悬停状态，限高 490px，纵向滚动保留表头，横向滚动仅发生在表格内。分页总数在左，条数选择和页码在右，32px 控件、17px 纵向留白；保留直接选页和每页 20／50／100 条。

元数据完整列序、三个来源字段、固定业务列、数值精度、上海入库时间、日线百分数／周线比率和空值语义保持不变；不引入 Demo 的 24 行／8 列限制。浏览器已实际展示 155 列、50／100 行与千万级页码，翻页和改页大小沿用已提交查询条件。

长文本单行省略，所有非空长文本均可聚焦查看纯文本全文；提示最多 560px 宽、50vh 高，连续字符可换行。上下键、PageUp／PageDown、Home／End 可滚动全文并保持单元格焦点；焦点在表内或表外都可按 Esc 关闭提示。内部滚动区显式可聚焦，支持 Tab 和 PageUp／PageDown。

## 验证

以下命令在 `control-plane` 执行，浏览器使用本地 Vite `http://127.0.0.1:4174` 和 HTTP API 夹具。

| 检查 | 命令 | 结果 |
|---|---|---|
| 最终针对性单元／集成 | `npm test -- src/components/dataset/DatasetTable.spec.js src/components/dataset/DatasetPagination.spec.js src/composables/useDatasetQuery.spec.js src/utils/format.spec.js src/views/DatasetView.spec.js` | 43 项通过 |
| 最终全量单元／集成 | `npm test` | 37 文件、609 项通过 |
| 专项浏览器及截图 | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-table.spec.js --output=node_modules/.cache/studio-t09-review` | 8 项通过 |
| 键盘遍历复验 | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- ui-redesign.spec.js -g '键盘、焦点' --output=node_modules/.cache/studio-t09-focus` | 1 项通过 |
| 最终统一浏览器回归 | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-table.spec.js studio-datasets.spec.js ui-redesign.spec.js studio-shell.spec.js` | 71 项通过（最终统一运行） |
| 生产构建 | `npm run build` | 通过；保留既有大于 500 kB 的 bundle 提示 |
| 静态检查 | `git diff --check`；Impeccable detector（两个变更 Vue 文件） | 无空白错误；detector 无发现 |

首次聚焦提示测试先因缺少焦点触发而失败，再通过；浏览器进一步验证了 Esc 关闭。独立只读审查复现的三项 P2 已修复并由原审查者通过真实 Chromium 复核：25 字中文被裁切却没有全文入口、6000 字提示无法键盘读到底、表外焦点的悬停提示不能 Esc 关闭。审查复核确认键盘滚动到全文末尾且保持焦点，未发现页面异常。

新增长文本焦点使既有键盘回归的 40 次 Tab 上限不足；上限调整为 400，覆盖当前 100 行、三列长文本的完整键盘路径。早期桌面对照使用严格像素相等，浏览器实际高度为 489.578px，改为小于 0.5px 的容差；未放宽实际布局上限。

## 桌面对照

三种宽度使用与 Demo 相同的日线记录作 HTTP 夹具，正式表格包含完整字段及来源列。对比边界、圆角、边框、表头字体／颜色／底色、正文行高／字号／留白和分页文字／间距。业务名称、字段编码、日期规范化、数值右对齐和涨跌色属于保留能力，正式页不复制 Demo 的行列截断及演示说明。

表体保留正式应用的主题白色底色；Demo 表体继承页面底色。表头高度因正式应用同时显示中文业务名称和字段编码而较高；正式分页保留页大小和直接选页，区别于 Demo 的两个箭头。这些差异支持真实业务使用。

- [1024px 表格](studio-t09/table-1024.png)／[Demo](studio-t09/demo-table-1024.png)
- [1280px 表格](studio-t09/table-1280.png)／[Demo](studio-t09/demo-table-1280.png)
- [1440px 表格](studio-t09/table-1440.png)／[Demo](studio-t09/demo-table-1440.png)
- [155 列宽表末端和来源字段](studio-t09/wide-table-1024.png)
- [长文本键盘焦点和纯文本提示](studio-t09/long-text-1024.png)
- [空结果](studio-t09/empty-1024.png)
- [千万级页码](studio-t09/large-page-1024.png)

截图已关闭过渡动画，并等待下拉框退出；共 10 张。所有表格状态的页面均无横向溢出。

## 边界

PC 以 Chromium 验收，未运行依赖打包后端、MySQL 或 Tushare 凭据的真实服务集成套件。HTTP 夹具验证前端真实请求路径、参数、状态及界面，不代表本轮重新验证后端或数据库。生产数据查看未导入 Demo 状态、快照或模拟查询。新增文件纳入 Git，未提交或推送。

停止本轮临时 Vite 服务时，开发日志仍有 `ResizeObserver loop completed with undelivered notifications`，与 T05／T08 已记录日志相同。上述功能、布局、键盘与浏览器断言通过，但不据此宣称开发控制台没有警告。
