# STUDIO-T01 页面框架、主题与外观设置

## Goal

正式入口采用 `control-plane/src/demos` 的 Studio 页面外壳和外观设置，为后续业务视图迁移提供三栏容器。任务身份与状态以 `docs/task-handoffs/studio-frontend-task-board.md` 为准。

## Scope

- 顶部品牌与三项导航、工作区路径、页面标题、默认配色、桌面三栏布局、外观设置。
- 保留 Vue Router URL、浏览器历史、DownloadView / DatasetView 的 KeepAlive、下载提交和列表轮询生命周期。
- 保留现有业务内容和主题 HEX 自定义、存储恢复、不可写降级能力。
- 仅验收 PC；接口目录交互、参数控件、任务卡片和详情弹窗、数据筛选与表格的完整视觉迁移分别属于 T02–T09。

## Approach

### 已确认的视觉与实现选择

- 视觉基准为 `2ae5963` 的 DemoApp.vue / demo.css；当前原版设计未变。`demos/live` 是接线参考，不作为正式入口依赖。
- AppLayout 将侧栏改为顶栏：66px 高、左右 30px、品牌与导航间距 60px、导航项间距 32px。采用 Demo 同款图标；下载详情沿用下载导航的选中状态，路径显示“任务详情”。
- 工作区路径 48px 高；主区域最大宽 1490px，内边距 8px 30px 32px；标题 25px、描述 14px，标题组下间距 24px。页面标题依次为“下载工作台”“数据查看”“外观设置”。移除原装饰图形及 Demo 横幅、预览说明和设计页脚。
- 下载页 `.studio-layout` 使用 `227px minmax(330px, 1fr) 295px`，最小高度 576px、边框 1px、圆角 9px。小于等于 1200px 时沿用 Demo 的 `195px minmax(280px, 1fr) 250px`；小于等于 960px 时右栏移至下一行。左栏放现有数据源与接口选择器、元数据状态；中栏放现有参数表单、提交按钮与结果；右栏放现有任务列表。三栏都是实际可用内容，后续任务在这些位置迁移对应控件。
- 复用 `--tensor-*` 和 Element Plus 映射，不引入生产代码对 demos 的导入。默认色取 Studio：背景 #f7f9fb、白色表面、raised #f1f4f8、nav #ffffff、线 #e0e6ee、正文 #1f2d43、次级文字 #52627a、accentBg #eaf0fa、accent #3565b6；增加 subtle #f8fafc 用于侧栏。
- 更换主题只改变强调色，背景和文字维持 Studio 配色。保留现有 HEX 验证与 requested/applied 区分；过亮颜色向黑色修正到所有操作背景及白字按钮至少 4.5:1，四个 Demo 预设色保持原色。存储键继续使用 `tensor-issue004-accent`，旧值直接恢复，恢复默认写入 #3565b6。
- SettingsView 对齐 Demo 的无卡片布局、18px 二级标题、四个 29px 圆形预设按钮与“恢复默认”。以 aria-pressed 表达选中状态。保留的自定义色输入放入下方默认折叠的“自定义主题色”；保存/降级与亮度修正由 role=status 提示。存储失败文案为“仅本次生效，无法保存到当前浏览器”。
- 保持 App.vue 的主题提供方式、main.js 的注册和路由定义；不增加后端请求或依赖。正常导航不抢走键盘焦点，跳转工作区链接保留。

### 实施顺序

1. 更新主题、设置和外壳测试到上述预期，观察当前实现失败。
2. 最小修改主题工具、布局、页面容器、公共标题与样式。
3. 验证路由缓存、存储故障、PC 对照和现有业务回归；同步受影响的旧 UI 断言。
4. 写验收记录、登记 T01 完成证据，新增文件加入 Git。T02 不在本次实施范围内。

## Files

- 修改 `control-plane/src/layouts/AppLayout.vue`、`src/components/common/PageHeading.vue`、`src/style.css`：Studio 外壳与公共样式。
- 修改 `control-plane/src/views/DownloadView.vue`、`src/views/DatasetView.vue`：三栏承载与标题。
- 修改 `control-plane/src/views/SettingsView.vue`、`src/utils/theme.js`：主题设置与配色；`useTheme.js` 保持存储契约。
- 更新 `control-plane/src/App.spec.js`、`src/layouts/AppLayout.spec.js`、`src/views/SettingsView.spec.js`、`src/views/DatasetView.spec.js`、`src/utils/theme.spec.js`、`src/composables/useTheme.spec.js` 的受影响断言。
- 新建 `control-plane/e2e/studio-shell.spec.js`；更新 `control-plane/e2e/ui-redesign.spec.js` 的布局和主题断言。
- 新建 `docs/verification/STUDIO-T01.md`，更新任务板本项设计引用、状态与证据。

## Tests

在 `control-plane` 下使用 Node 24.15.0：

- `npm test -- src/App.spec.js src/layouts/AppLayout.spec.js src/views/SettingsView.spec.js src/utils/theme.spec.js src/composables/useTheme.spec.js src/router/index.spec.js`：导航、缓存、预设、HEX 校验、旧偏好、默认恢复和存储降级通过。
- `npm test` 和 `npm run build`：受影响前端全量回归与三个入口构建通过。
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-shell.spec.js`：本地 Vite，API 使用受控响应。1280/1440 桌面无页面横向溢出；三个路由与历史往返、缓存值、刷新持久化、跳转链接、主题与 DOM 几何对照通过；1024px 下大页码不被右栏裁切，点击末页发出对应页码请求。
- `TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- ui-redesign.spec.js`：保留元数据、提交和查询断言；布局验收改为本轮 PC 范围。
- 截取 Demo 与正式入口同视口的下载页、设置页；剔除 Demo 横幅高度后比较顶栏、路径、标题、三栏尺寸、设置控件与配色。记录仍由后续任务负责的内部差异。

## Acceptance

- 正式入口出现 Studio 顶部导航、页面标题和实际三栏工作区；设置页对应 Demo 结构。
- 桌面下外壳的颜色、尺寸与 Demo 有可复现对照证据，无生产演示文案或模拟状态依赖。
- 路由和前进/后退正常，下载/数据页缓存与轮询行为通过现有测试。
- 四色预设、自定义 HEX、旧偏好恢复、默认恢复、读写失败降级均可用。
- 构建及上述检查通过，新增文件已被 Git 跟踪。

## Risks

- T01 保留的 Element Plus 业务控件、列表表格与 Demo 的内部设计尚有差异，分别由后续任务迁移；不能将 T01 验收表述为整项前端已经 1:1 完成。
- 原主题算法对整页染色且要求 5.5:1，与 Demo 配色冲突；本次明确采用固定 Studio 表面及 WCAG 正文 4.5:1，既有自定义颜色的实际显示可能略变，存储的请求颜色保持不变。
- 旧任务表格较宽，右栏保留表格自身的局部横向滚动；不得为容纳它扩大整个页面。页码组允许换行，避免大页码被右栏裁切。
