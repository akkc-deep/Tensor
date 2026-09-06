# ISSUE-004-T02：侧栏、设置与业务状态保留

## Goal

交付三入口侧栏和外观设置，业务页往返设置时保持同一实例及在途请求。

## Scope

应用壳、settings 路由、主题交互与页面缓存；保留原下载/查看内容及所有业务状态机。共享页面标题呈现，表单与面板布局留给后续任务。

## Approach

AppLayout 使用稳定 RouterView 插槽、KeepAlive include DownloadView/DatasetView、max=2，component key 为 route.name；两个业务页 defineOptions 明确同名。SettingsView 和 404 不缓存，外层 KeepAlive 不随页面切换卸载。仅原 onMounted 加载元数据，激活不加载/提交；主题不作为实例 key。业务 refs 与请求快照保持页面本地，设置无 API 导入。所有现有路由及根重定向保留，增加 /settings name=settings。

按已确认 HTML 的应用壳实现 210px 侧栏、1250px 以下 180px、1000px 以下 154px、680px 以下顶部导航。body 最小宽度降为 360px，页面、main、grid 子项 min-width:0；工作区 padding 桌面 35px 36px、1250 以下 28px 25px、680 以下 25px 16px。侧栏使用 RouterLink 与 aria-current，品牌和三导航采用一致的线条 SVG；可用 v-for 在 AppLayout 内共享导航结构，不建立图标框架。增加指向可聚焦 main#workspace 的跳转链接。工作区顶部提供工作空间/当前页面位置，标题延用已确认方案的字号和说明；不加入示例来源、预览工具条或假业务值。

新建 common/PageHeading.vue，props id/title/description，统一 h1 与说明，用于下载、查看、设置三个页面；以必要 props 和模板实现，不加入业务状态。页面容器与标题使用统一 CSS。原先 header 总数量断言改为检测一个应用导航区域/工作区，标题和业务断言保留。

SettingsView 使用 T01 useTheme() 注入；本地 draft 初始为 requested 大写，原生 color input 在 input 事件 apply 并同步 draft；HEX form submit 调用 apply，非法显示 FieldError，关联 aria-invalid/aria-describedby 并保持已应用主题；合法清除错误。显示实际应用色大写，以及输入和应用不同时的亮度校正说明。storageStatus=preview-only 显示“仅本次预览”，否则“已保存”。重置调用 reset 并同步草稿/清除错误，标签“恢复冰川白”。主题控件只出现在设置，44px 可操作高度，窄屏自然换行。状态说明固定占位/换行避免主题色改变控件尺寸；样式使用根变量。所有数据页内容与原流程保持。

## Files

新建 control-plane/src/views/SettingsView.vue、SettingsView.spec.js、components/common/PageHeading.vue。修改 layouts/AppLayout.vue、AppLayout.spec.js、router/index.js、index.spec.js、views/DownloadView.vue、DatasetView.vue、App.spec.js、style.css。可在 layouts/AppLayout.spec.js 内集中集成缓存测试，不新增业务 composable 或全局 store。

## Tests

Node 24.15.0，在 control-plane：先扩展 route/layout/settings 测试验证缺少 settings/三导航/缓存失败。SettingsView 用真实 createThemeState 注入，操作取色器/HEX/表单，断言非法不修改、亮度校正、存储提示及重置。布局集成用真实业务页面/Element Plus，mock 现有 API 边界：填 daily 日期 -> 设置 -> 返回值保留，listDataSources/listApis 次数不增加；提交下载延迟 Promise，设置期间完成，返回结果存在且一次 POST；查询设置 tsCode 与交易/公告日期，查询后改每页100并到第2页，往返后结果/分页/草稿保持；延迟查询在设置完成不重发；失败往返后保留错误和原快照，修改草稿后重试仍用失败参数。独立打开 settings 并应用/重置主题时所有 API mock 为零调用。404、跳转链接和导航键盘焦点可用。

运行 `npm test -- src/router/index.spec.js src/layouts/AppLayout.spec.js src/views/SettingsView.spec.js src/App.spec.js`、`npm test` 和 `npm run build`，均退出 0。不要重写既有业务用例，只增加缓存/设置行为与更新布局断言。

## Acceptance

三入口、404、取色/HEX/实际应用色/降级/重置可用；设置零请求；两个业务实例往返保留日期、结果、错误、分页和在途响应且不重发。PageHeading 在三页实际使用。响应式壳与键盘路径就绪，最终实际视口验收由 T06 完成。

## Risks

Element Plus 弹层属于根节点，缓存页离开时不应新增请求；错误/结果状态必须通过路由实际往返测试验证。只承诺内存业务状态，不承诺刷新恢复。
