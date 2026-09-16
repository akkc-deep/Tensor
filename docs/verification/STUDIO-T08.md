# STUDIO-T08 验证记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T08-design.md`。
- 实施分支：既有 `feat/studio-frontend`，保留此前 T01–T07 的暂存改动。
- 视觉基准：任务板固定的 Studio Demo（`2ae5963`），Demo 未修改。

## 实现结果

数据查看页已迁移为横向数据源／数据集／动态筛选栏、查询与重置和结果标题。保留真实数据集分类、名称／编码搜索、单来源默认选择与可用性限制；控件只根据真实定义支持的三类筛选生成。原生日期与证券代码继续复用既有规范化、白名单及快照逻辑。

查询支持按钮和 Enter，等待期间不重复提交。重置保留选择、清空输入与结果，不自动查询；过期元数据和查询响应仍被丢弃。翻页、页大小变更及失败重试使用已提交条件，页面往返保留未提交草稿与查询结果。数据源／目录／定义加载和失败可分别恢复，空来源、空目录、未查询及空结果有明确状态。

修复关联日期错误未清除，并阻止把原生日期中尚未输入完整的分段误当空条件。日期错误关联 ARIA 并聚焦首个错误；重置也能清除原生分段状态。现有完整表格、精度、来源列和分页逻辑保留，外观迁移归 T09。

## 验证

以下命令在 `control-plane` 执行；浏览器使用本地 Vite `http://127.0.0.1:4174` 与 HTTP API 夹具。

| 检查 | 命令 | 结果 |
|---|---|---|
| 针对性单元／集成 | `npm test -- src/views/DatasetView.spec.js src/components/dataset/DynamicFilterForm.spec.js src/composables/useDatasetFilters.spec.js src/composables/useDatasetQuery.spec.js` | 31 项通过 |
| 页面缓存回归 | `npm test -- src/layouts/AppLayout.spec.js` | 11 项通过 |
| 最终全量单元／集成 | `npm test` | 37 文件、609 项通过 |
| 浏览器回归 | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-datasets.spec.js ui-redesign.spec.js studio-shell.spec.js` | 63 项通过 |
| 最终专项与截图复验 | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-datasets.spec.js` | 11 项通过，包含在上行 63 个场景中；最终 8 张截图已导出 |
| 生产构建 | `npm run build` | 通过；仍有既有大于 500 kB 的 bundle 提示 |
| 受影响后端浏览器用例收集 | `npm run test:e2e -- dataset-query.spec.js download-outcomes.spec.js fixture-flow.spec.js tushare-metadata.spec.js --list` | 69 项收集成功，仅收集，未执行 |
| 静态检查 | `git diff --check`；Impeccable detector（两个变更 Vue 文件） | 无空白错误；detector 无发现 |

浏览器覆盖：40 个数据集定义与一次请求的矩阵；查询／分页／重试快照、未提交草稿、设置往返和切换取消；三阶段元数据错误恢复；空目录；公告日期及无筛选数据集；原生日期不完整、日期倒序、首错聚焦和重置；纯键盘选择、日期分段输入及 Enter 查询；四主题和 1024／1280／1440px 桌面布局。

独立只读审查发现旧打包后端键盘测试仍向日期框直接键入 ISO 文本。已根据 Chromium 实测改为日期分段按键与 Tab 导航，新增夹具下的纯键盘用例验证实际查询参数。审查未发现其余需修复的业务缺陷。

## 桌面对照

正式查询栏与 Demo 对照验证了控件 43px 高度、字号、颜色、背景及页面边界，扣除 Demo 独有的 60px 演示横幅。正式页面多一个数据源控件，且保留可搜索的分类下拉：1280／1440px 为单行，1024px 查询／重置换至下一行；筛选输入保持横向，页面无横向溢出。结果表格继续使用原有组件，因此表格外观、展示列及行数不与 Demo 样例作等像素比较。

- [1024px 查询](studio-t08/query-1024.png)／[Demo](studio-t08/demo-query-1024.png)
- [1280px 查询](studio-t08/query-1280.png)／[Demo](studio-t08/demo-query-1280.png)
- [1440px 查询](studio-t08/query-1440.png)／[Demo](studio-t08/demo-query-1440.png)
- [原生日期校验](studio-t08/invalid-date.png)
- [1024px 定义加载失败与重试](studio-t08/metadata-failure-1024.png)

## 边界

未运行需要打包后端、MySQL 或 Tushare 凭据的真实服务集成套件；上述浏览器结果验证前端真实 HTTP 路径和参数、状态及界面，不代表本轮重新验证后端采集或数据库行为。PC 以 Chromium 验收。生产数据查看不导入 Demo 状态或目录快照。本轮未实施 T09 的表格及分页外观迁移。

停止本轮临时 Vite 服务时，开发日志仍包含 `ResizeObserver loop completed with undelivered notifications`；此前 `STUDIO-T05.md` 已记录同类日志。本轮上述功能、布局及键盘断言均通过，但不据此宣称整轮开发控制台无警告。
