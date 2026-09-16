# STUDIO-T08 数据集选择、筛选与查询

## Goal

正式数据查看页使用 Studio 横向查询栏选择真实数据源、数据集和筛选条件；查询快照、切换竞态、重置与重试保持可靠。

## Scope

迁移数据选择、动态筛选、查询按钮及配置／查询状态，验收 1024／1280／1440px PC。保留数据集搜索、分类和现有完整表格、格式化及分页；表格与分页外观归 T09。无后端契约变更，不导入 Demo 数据或模拟查询。

## Approach

- 以任务板固定的 `2ae5963` Demo `DatasetBrowser.vue`、`demo.css` 为视觉基准，将两块 WorkbenchPanel 替换为 `data-browser`、横向 `query-form` 和 `data-caption`。使用现有主题变量、43px 控件与主按钮；数据源是正式业务增加的一项，窄桌面宽度允许整项换行。
- 保留 `DataSourceSelect` 的单来源默认选择与可用性规则，保留 `DatasetSelect`／`CatalogSelect` 的名称／编码搜索、分类和键盘操作。局部适配选择器外观，不改变下载页。
- `DynamicFilterForm` 使用原生文字／日期输入，按定义顺序仅呈现支持的 `ts_code EQ TEXT`、`trade_date BETWEEN DATE_RANGE`、`ann_date BETWEEN DATE_RANGE`。接口不变：`validate()`、`criteria()`、`reset()`；条件为 `tsCode`、`tradeDateFrom/To`、`annDateFrom/To`，日期保持 ISO。复用 `useDatasetFilters` 的规范化和白名单，补齐原生日期分段不完整校验、关联日期错误清理及错误聚焦／ARIA。
- 查询栏为有名称的 form，Enter 和按钮均调用 `handleQuery`；原生表单用 `novalidate`，统一由字段校验呈现错误。校验等待前后检查 queryLoading 和当前表单引用，防止重复提交或切换后的旧表单提交。
- 不自动查询。选择数据源／数据集清空定义和查询上下文；继续使用 metadataGeneration 和 useDatasetQuery generation 丢弃过期成功与失败。元数据三阶段各自加载和安全重试；补充“暂无数据源”“暂无可查询的数据集”空态。
- 查询时禁用字段和查询按钮，仍允许选择新数据集或重置取消旧上下文。重置保留选择，清空输入、校验、结果并恢复默认页大小，不触发查询。重试、翻页和页大小变化继续使用已提交请求快照，不读取编辑中的草稿。KeepAlive 保持页面往返选择、草稿和结果。
- 结果标题显示真实定义名称／编码及“只读”，有结果时显示服务端总数。加载、失败（含 requestId）、未查询、空结果沿用 AsyncStatePanel；状态区域使用 Studio 的居中图标、紧凑标题和恢复按钮；结果表格和分页组件原样接入。

## Files

- `control-plane/src/views/DatasetView.vue`：页面结构、局部样式、元数据空态和提交防重。
- `control-plane/src/components/dataset/DynamicFilterForm.vue`、`control-plane/src/composables/useDatasetFilters.js`：原生输入与筛选校验。
- 对应单元测试与 `control-plane/e2e/studio-datasets.spec.js`：业务、键盘和桌面对照；同步受影响既有浏览器测试的布局／标签断言与原生日期键盘操作，适配 `AppLayout.spec.js` 页面缓存测试。
- `docs/verification/STUDIO-T08.md`、`docs/verification/studio-t08/`：验证记录与截图；任务板回填设计、启动与完成证据。

## Tests

- `cd control-plane && npm test -- src/views/DatasetView.spec.js src/components/dataset/DynamicFilterForm.spec.js src/composables/useDatasetFilters.spec.js src/composables/useDatasetQuery.spec.js`：支持的字段、无筛选定义、日期错误、规范化、重置、失败重试、竞态及提交快照通过。
- `cd control-plane && npm test`、`npm run build`：全量单元／集成和生产构建通过。
- 启动 `npm run dev -- --host 127.0.0.1 --port 4174 --strictPort` 后运行 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-datasets.spec.js ui-redesign.spec.js studio-shell.spec.js`：HTTP 夹具下验证真实请求路径和参数、草稿隔离、重试、切换、缓存、原生日期分段及 PC 布局。对照 Demo 控件高度、字体、颜色、顶部边界与横向结构，导出截图；页面不产生横向溢出。

## Acceptance

数据集和筛选均来自 API 元数据；查询仅发送当前支持字段；加载／错误／空态可辨认并可恢复；查询、重置和重试保持既有语义；草稿不影响分页／失败重试；过期响应不覆盖当前数据集；路由往返与键盘操作可用；桌面对照、受影响检查及构建通过；新文件加入 Git。

## Risks

正式查询栏比 Demo 多数据源控件，搜索下拉继续使用 Element Plus，故宽度分配与 Demo 不完全一致。已有表格／分页保留旧样式，由 T09 负责迁移。浏览器夹具验证前端 HTTP 契约，不替代带 MySQL 的打包后端集成验收。
