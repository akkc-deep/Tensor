# STUDIO-T02：接口目录与真实元数据

## Goal

用户在正式下载工作台的 Studio 左栏搜索、分类并选择真实接口，中栏展示当前接口及其能力加载状态。任务身份与状态以 `docs/task-handoffs/studio-frontend-task-board.md` 为准。

## Scope

- 迁移接口列表、名称／编码搜索、分类、总数／结果数、选中态及中栏接口标题。
- 使用 `listDataSources`、`listApis`、`getDownloadCapabilities`；处理数据源、目录与能力的加载、失败、重试、空态和不可用状态。
- 复用 T01 三栏、主题、路由缓存以及现有下载状态和参数控件。仅验收 PC。参数表单重构和任务链路分别由后续任务承担，本轮只完成 T02，不准备或实施 T03。

## Approach

- 将 `ApiSelect.vue` 的下拉选择改为 Demo 的内嵌目录，保留 `apis`、`modelValue`、`disabled` 和 `update:modelValue` 接口，新增 `sourceName` 用于结果数页脚。生产模块不导入 demos。
- 元数据 `apiName/displayName/category` 对应 Demo 的 `id/name/category`；保留服务端顺序、原始分类，不引入分类映射或接口快照。搜索 trim 后忽略大小写，分别匹配名称或编码；分类与搜索取交集，分类项始终从完整目录去重。标题显示完整目录数，页脚显示筛选后数量和真实来源。
- 搜索框、原生分类 select 和真实 button 列表使用可访问名称；按钮 `aria-pressed` 表示选中，重复点击当前项不重置表单。过滤隐藏当前项时仍保留当前接口和参数；切换来源以 `selectedPluginId` 为 key 重建目录，清除旧搜索／分类。
- `DownloadView.vue` 保留 `DataSourceSelect`，单来源沿用其自动选择，多来源等待选择，不擅自选中接口。额外的数据源控件是正式版相对 Demo 的必要差异。不可用来源显示服务端原因，目录及提交不可用；在 `useDownloadFlow.selectApi` 同样校验 `downloadAvailable`。
- 复用 `metadataState` 与当前选择定位阶段：未选来源时为来源加载；已选来源且未选接口为目录加载；已选接口为能力加载。来源／目录状态展示在左栏，能力状态展示在中栏。失败保留请求 ID 和 `retryMetadata`；加载不误报为空目录，失败不误报为无结果。能力加载期间保留目录可切换，清空旧参数和能力，提交禁用。
- 延续 `metadataGeneration`：切换来源或接口即递增，过期成功与失败均丢弃，销毁后不更新。来源切换立即清空旧选中接口、目录和能力。用户看见的标题来自当前目录项，与尚未返回的能力请求解耦。
- 中栏采用 Demo 图标、21px 标题与 12px 编码，分类／查询方式保留为紧凑元数据说明；原重复接口说明移出左栏。未选时提示从左侧选择接口。
- 视觉沿用 Demo 227px 左栏、搜索框 35px 输入、分类控件、列表内边距 8px、行内边距 10px 12px、列表最大高 417px 与局部滚动、强调色选中态及底部结果数。长名称／编码换行，键盘焦点可见，1024/1280/1440 PC 无页面横向溢出。

## Files

- 修改 `control-plane/src/components/download/ApiSelect.vue` 及其测试：目录过滤、选择、数量与样式。
- 修改 `control-plane/src/views/DownloadView.vue` 及其测试：目录阶段状态、来源限制、中栏标题与能力状态。
- 修改 `control-plane/src/composables/useDownloadFlow.js` 及其测试：不可用来源防护、来源／能力竞态验收。
- 保留 `control-plane/src/App.spec.js` 的路由断言，更新受影响的 `control-plane/e2e` 目录选择交互断言；增加 `studio-catalog.spec.js`，覆盖目录对照和长列表。
- 新增 `docs/verification/STUDIO-T02.md` 和 PC 对照截图；更新任务板设计引用、状态和证据。新增文件加入 Git。

## Tests

在 `control-plane` 下使用 Node 24.15.0：

1. `npm test -- src/components/download/ApiSelect.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js`：先观察目录新行为失败，实施后验证过滤交集、数量、重复选择、禁用、空态、分阶段失败重试及过期响应。
2. `npm test`：全部单元／集成检查通过，保留参数校验、提交快照、恢复和轮询业务检查。
3. `npm run build`：正式入口与两个 Demo 构建成功。
4. 在本地 Vite 4174 服务执行 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-catalog.spec.js studio-shell.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js`：HTTP 契约受控响应，验证请求、目录、切换、键盘、提交和缓存。依赖后端／账户的其他 E2E 仅同步受影响选择器并记录未执行边界。
5. 同视口截图对比 Demo 与正式目录、标题；几何断言比较列表行、颜色和宽度，明确数据源控件与待迁移表单的差异。

## Acceptance

- 所有目录项、分类、数量与标题均来自真实请求结果，无 Demo 依赖。
- 名称／编码搜索和分类可组合；清空筛选恢复全部，过滤不丢失当前参数。
- 快速切换及迟到成功／失败不会污染当前来源、接口、能力或错误。
- 来源不可用时无法选择接口／提交，保留原因。加载、空目录、无匹配、失败各自准确并可恢复。
- PC 长目录内部滚动、键盘选择、可见焦点和中栏联动通过；受影响业务检查及构建通过。

## Risks

- 正式版支持多来源，来源控件会使目录搜索与列表比 Demo 更低；对照需明确这一必要差异。
- 参数表单、接收结果与任务列表仍为 T01 后的现有实现，不将此次验收表述为整个下载工作台迁移完成。
- 浏览器受控响应可验证 HTTP 接线与交互，不证明上游账号或真实下载服务可用。
