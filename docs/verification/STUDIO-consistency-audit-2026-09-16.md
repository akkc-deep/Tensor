# Studio 与 Demo 一致性检查

日期：2026-09-16。结论：**尚未完全一致**。基础布局和主要控件常态尺寸已对齐，但输入框交互、按钮反馈和部分控件外观仍有偏差；正式业务扩展也使若干页面与 Demo 不同。本次只检查并记录，没有修改产品代码或任务板状态。

后续修正与 2K / 4K 验证见 [显示适配记录](STUDIO-display-adaptation-2026-09-16.md)。本报告保留修正前的检查结果。

## 方法与范围

- 基准：`2ae5963` 的 `control-plane/src/demos`。再次运行 `git diff 2ae5963 -- control-plane/src/demos ':!control-plane/src/demos/README.md' ':!control-plane/src/demos/live'`，输出为空。
- 对象：当前工作区正式 `/downloads`、`/datasets`、`/settings` 和任务详情，与 `/ui-demos.html` 对照。
- 环境：Node 24.15.0、Playwright Chromium；桌面 1024、1280、1440 × 1000px；默认蓝色主题。另抽查四种预设主题下设置页恢复默认按钮的文字颜色。
- 使用现有 `installApi`、T10 的 `comparisonTasks` 和同一份 daily 示例记录，拦截全部业务 HTTP 请求，没有操作真实数据库或上游数据源。
- 120 个元素／状态比较，采集字体、边框、颜色、尺寸、间距、透明度、过渡等 27 个 CSS 属性及几何信息。1440px 扩展检查文本／日期／枚举、下载／查询／重置、模式切换、主题按钮的普通、悬停、程序聚焦及按下状态；关闭按钮只检查普通／悬停／聚焦，避免误触关闭。
- 随后用 Tab 建立键盘输入模式并确认 `:focus-visible`，单独复核输入框、下载、查询、重置按钮。最终采集无 pageerror、无未定义 API 请求；三个宽度的数据页均无整页横向溢出。Vite 日志仍出现既有 `ResizeObserver loop completed with undelivered notifications`，不将 pageerror 为空解读为开发控制台无告警。
- [测量原始记录](studio-consistency-2026-09-16/measurements.json)。`keyboard` 是真实键盘焦点复核证据；主采集的 `focus` 是程序聚焦，不能单独用来判断按钮是否缺失键盘焦点。矩形坐标受滚动和 Demo 顶部横幅影响，不直接比较绝对 y 值。
- 使用 impeccable 做一致性审查，并按 systematic-debugging 的方式核对运行结果与 CSS 来源。机械检测器扫描主要相关文件返回 `[]`，它不具备与 Demo 比较样式的能力，不能证明一致。

## 已对齐的部分

以下均为本次浏览器实测，不代表其他状态也一致。

| 项目 | 正式与 Demo 的共同结果 |
|---|---|
| 顶部导航 | 高度 66px，相关字号、文字颜色及横向留白一致 |
| 页面标题 | 主体宽度、字号、颜色一致；Demo 自有横幅不纳入产品区域比较 |
| 下载三栏 | 三种桌面宽度的外框和列宽一致；内容增加会改变整体高度 |
| 下载文本／日期／枚举输入框 | 高度 43px，圆角 6px，14px 字号；单次与批量日期框常态样式一致 |
| 下载按钮 | 高度 40px，圆角 6px，14px／500 字体；默认、悬停及按下背景色一致；真实键盘焦点均为 2px、偏移 2px |
| 查询按钮 | 常态 84 × 43px，圆角 6px，14px／500 字体 |
| 任务详情 | 面板宽度 430px、内边距 31px、背景与遮罩一致 |
| 外观设置 | 900px 内容上限、预设主题按钮 29 × 29px、主区域尺寸和文字颜色一致 |

## 尚未对齐的控件细节

### 1. 输入框悬停和焦点样式不同（P2）

正式下载与查询输入框悬停时直接采用主题蓝 `#3565b6` 边框；Demo 使用主题色与边框色混合后的较浅颜色。聚焦时，正式是偏移 2px 的实线外框，边框仍为 `#e0e6ee`，没有阴影；Demo 是主题色边框加 2px、12% 不透明度的柔和光环。正式输入框没有 Demo 的 160ms 边框／阴影过渡。

影响：相同输入动作呈现不同的视觉反馈，无法达到控件级一致。两边都存在可见焦点，此项不等于“没有键盘焦点”。

- 位置：`control-plane/src/components/download/DynamicParameterForm.vue:88`、`control-plane/src/components/dataset/DynamicFilterForm.vue:86`、`control-plane/src/components/download/ApiSelect.vue:67`。
- Demo 规则：`control-plane/src/demos/demo.css:158`、`:164`。
- 证据：[正式焦点](studio-consistency-2026-09-16/app-input-focus.png)／[Demo 焦点](studio-consistency-2026-09-16/demo-input-focus.png)。截图输入值及说明文案不同，结论基于边框、光环与键盘测量，而非整图像素差。
- 建议：统一输入框、原生下拉、目录搜索的 hover／focus 规则，保留正式校验和禁用逻辑。

### 2. 查询按钮的交互样式不同（P2）

正式查询按钮悬停采用 `filter: brightness(.94)`；Demo 通过混合背景色变暗。按下时，正式继续使用悬停处理；Demo 切换到更深背景并下移 1px。真实键盘焦点实测：正式外框 3px、偏移 3px，Demo 为 2px、偏移 2px。查询旁“重置”均为 2px 焦点线，但偏移分别为 3px 与 2px。

- 位置：`control-plane/src/views/DatasetView.vue:329`、`control-plane/src/style.css:165`。
- Demo 规则：`control-plane/src/demos/demo.css:90`、`:100`、`:161`。
- 建议：查询与下载使用同一套按钮交互规则，避免同一产品内两种主按钮反馈。

### 3. 详情关闭按钮尺寸和颜色不同（P2）

正式按钮为 **30 × 30px**、默认文字色 `#52627a`；Demo 为 **34 × 34px**、默认文字色 `#1f2d43`。因此图标位置、点击区域及视觉重量不同，虽面板宽度完全一致，控件仍不一致。

- 位置：`control-plane/src/views/DownloadTaskView.vue:155`。
- Demo 规则：`control-plane/src/demos/demo.css:98`。
- 证据：[正式详情](studio-consistency-2026-09-16/app-detail-1440.png)／[Demo 详情](studio-consistency-2026-09-16/demo-detail-1440.png)。全页截图中超过 1000px 的部分不是可见视口，不据此判断遮罩缺陷。
- 建议：关闭按钮统一至 34px 和相同默认颜色。

### 4. 按钮过渡、按压反馈及禁用透明度不同（P3）

正式下载、模式、主题等已抽查按钮通常为 `transition-duration: 0s`；Demo 的背景／颜色过渡为 160ms，轮廓／位移过渡为 120ms。Demo 下载、查询、主题按钮按下会下移 1px，正式没有这段反馈。

`stock_basic` 不支持批量下载时，两边批量模式按钮都正确禁用，但正式透明度为 **0.45**，Demo 为 **0.55**。

- 位置：`control-plane/src/components/download/DownloadAction.vue:32`、`control-plane/src/views/DownloadView.vue:471`、`control-plane/src/style.css:361`。
- Demo 规则：`control-plane/src/demos/demo.css:6`、`:13`、`:100`。
- 建议：对齐交互过渡和禁用视觉，同时保留减少动态效果偏好。

## 页面层面的差异

这些差异大多在 T10 中已明确保留，不能将“功能上有理由保留”表述为“视觉完全一致”。

| 区域 | 本次观察 | 判断 |
|---|---|---|
| 下载目录 | 正式增加数据源选择，接口顺序／名称来自真实元数据；Demo 使用固定目录 | 业务差异，保留真实数据源及元数据 |
| 最近任务 | 正式显示接口编码、来源、完整性、参数与进度、刷新与服务端分页；Demo 更紧凑且使用中文示例名称。失败／中断颜色也不同 | 已记录的正式业务扩展，导致页面高度与信息密度不同 |
| 数据筛选 | 正式数据集使用可搜索的 Element Plus 下拉，Demo 使用原生 select；边框实现、箭头、展开面板与行为不同 | 搜索能力可保留，但不能称控件完全一致 |
| 数据页 1024px | 正式因增加数据源字段，“查询／重置”换到下一行左侧；Demo 保持第一行右侧 | 已记录的布局差异；如要求严格视觉对齐，应重新安排数据源与操作区 |
| 数据表格 | 正式完整业务列加来源列、双层表头、格式化日期及涨跌色；Demo 8 列、单层字段名 | 业务展示差异，不能通过删减真实字段来机械追平 |
| 分页按钮 | 正式“上一页”为 **44 × 32px**，并有页码和每页条数；Demo 是 **34 × 34px** 图标按钮 | 分页能力需保留，按钮尺寸及外观可以进一步统一 |
| 设置页 | 正式有持久化说明、保存状态和自定义主题输入框；Demo 仅内存主题 | 正式能力扩展，没有一一对应的 Demo 控件 |

页面证据：[正式下载](studio-consistency-2026-09-16/app-downloads-1440.png)／[Demo 下载](studio-consistency-2026-09-16/demo-downloads-1440.png)，[正式数据 1024px](studio-consistency-2026-09-16/app-datasets-1024.png)／[Demo 数据 1024px](studio-consistency-2026-09-16/demo-datasets-1024.png)，[正式设置](studio-consistency-2026-09-16/app-settings-1440.png)／[Demo 设置](studio-consistency-2026-09-16/demo-settings-1440.png)。Demo 数据截图左上角可见其跳转链接的全页截图边缘，不将此截图表现计为产品差异。

## 为什么 T10 通过仍存在差异

`control-plane/e2e/studio-acceptance.spec.js:357` 对下载页主要断言容器的 width／fontSize／color；`:367` 对详情断言面板宽度／内边距／背景；`:383` 对数据表断言宽度／圆角／边框色；`:390` 对设置主区域断言宽度／字号／颜色。

这些断言没有要求所有按钮／输入框的 hover、active、focus、disabled、transition 一致，也没有逐像素比对。因此“任务板完成、当时验收通过”成立，但不能据此推导“页面和控件完全一致”。

## 后续建议与边界

优先用 `$impeccable polish` 收敛上述四类已测得的样式偏差；保留真实业务功能，并明确哪些页面布局差异属于可接受范围。如果目标是严格对齐，再对数据源区域、搜索下拉和分页做专项 `$impeccable layout`／`$impeccable polish`。

本次不是全功能重新验收：未穷举 40 个接口、所有加载／错误／恢复状态，也未检查手机、平板、其他浏览器或真实后端执行。120 个样本足以否定“完全一致”，不构成整体相似度百分比。报告与新证据加入 Git；未提交、推送或部署。
