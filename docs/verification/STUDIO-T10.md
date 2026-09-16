# STUDIO-T10 验证记录

- 日期：2026-09-16。
- 任务板：[Studio 前端迁移任务板](../task-handoffs/studio-frontend-task-board.md)。
- 设计：[STUDIO-T10](../task-designs/STUDIO-T10-design.md)。
- 分支：既有 `feat/studio-frontend`；保留全部 T01–T09 暂存改动，未提交、推送或部署。
- 环境：Node 24.15.0、Chromium；正式页面通过 HTTP API 夹具验收。

## 交付结果

新增 `control-plane/e2e/studio-acceptance.spec.js`：两条连续旅程分别运行于 1024／1280／1440px，另有三个宽度的四页面最终视觉对照，共 9 项。复用 `installApi`、真实定义列、`syntheticRecords`、任务及批次夹具；请求记录 method、path、query、body、requestId 和原始 body，未定义 API 路径立即失败。正式页面请求 `/src/demos/` 或产生 pageerror 均使验收失败。

单次旅程提交 daily／SINGLE 的 `000001.SZ`、`20260807`，只发一次规范化 POST；从服务端列表打开同一返回 taskId，关闭和浏览器前进重开后保留参数。数据页查询同一代码／日期得到一条夹具记录，再主动扩大已提交日期范围至 61 条以验证分页；未提交代码／日期草稿不污染第二页请求。设置页切换强调色并返回后，草稿、结果、页码和长精度数值均保留；刷新只验证主题持久化，再从服务端列表重开原任务。

批量旅程提交 daily／RANGE 并从 RUNNING 推进至 PARTIAL_FAILED；详情展示成功与失败批次。详情发起重试后关闭重开，列表与新详情共享锁且只产生一次操作 POST，`expectedVersion=9007199254740993` 精确传输。受理响应返回 QUEUED 时保留旧状态，放行 GET 后才显示成功。成功批次的尝试次数与来源行数逐一比较重试前后的实际界面。另一个独立 INTERRUPTED 任务以 `expectedVersion=9007199254740997` 恢复，返回 SUCCEEDED 后查询对应记录并往返主题设置；详情仍保留来源、服务端进度及历史 RESPONSE_ONLY 完整性说明。冲突／不确定操作沿用 T07 专项回归，不复制生产状态机。

删除 4 个无生产引用组件及其 3 个独立测试；只移除相关死 CSS 和两处旧的“组件不存在”断言。README 已替换为实际运行、路由、任务恢复、筛选缓存、键盘、主题及 PC 使用说明；模拟 Demo 和后端接入 Demo 均保持独立。

## 验证命令与结果

以下命令在 `control-plane` 执行，使用 Node 24.15.0。浏览器开发服务先执行：

```bash
npm run dev -- --host 127.0.0.1 --port 4174 --strictPort
```

| 检查 | 命令 | 结果 |
|---|---|---|
| 全量单元／集成 | `npm test` | 34 文件、601 项通过；原 609 项减少的 8 项仅覆盖三个已删除死组件 |
| 离线证据与打包环境 | `node --test e2e/tushare-range-evidence.test.js e2e/packaged-test-environment.test.js` | 130 项通过 |
| 跨页面及最终对照 | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-acceptance.spec.js` | 9 项通过；审查强化后以 `--output=node_modules/.cache/studio-t10-review` 再运行，9 项通过（43.9 秒） |
| 统一浏览器回归 | 下方完整命令 | 150 项通过（4.9 分钟）；之后仅加强本文件断言并复验 9 项 |
| 生产构建 | `npm run build` | 通过；1724 个模块，三个 HTML 入口均生成 |
| 正式产物预览 | `env -u TENSOR_UI_BASE_URL npm run test:e2e -- --config=playwright.ui.config.js` | 49 项通过（1.5 分钟），配置自动启停 4173 预览服务 |
| 静态／Git 检查 | 根目录 `git diff --check`、`git diff --cached --check`、引用搜索、截图跟踪核对 | 通过；24 张 PNG 宽度／文件签名正确并全部由 Git 跟踪 |

```bash
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-acceptance.spec.js studio-shell.spec.js studio-catalog.spec.js studio-form.spec.js studio-submission.spec.js studio-tasks.spec.js studio-detail.spec.js studio-recovery.spec.js studio-datasets.spec.js studio-table.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js
```

本轮离线检查首次有 2 项失败：导航测试编译真实 `AppLayout.vue`，但测试导入适配器未覆盖 T07 新增的下载任务通道。只在离线测试中为该确切导入补充无副作用的依赖映射，保留真实布局编译、SSR 渲染和导航断言；修复后 130 项全通过，没有修改生产通道或后端契约。

新增旅程调试期间纠正了测试装配：弹窗重建不继承旧实例的操作提示，因此按 POST 受理与 GET 请求时序断言；同时阻塞列表／详情 GET，避免服务器夹具推进前列表先读到旧状态。页大小使用现有键盘路径操作。这些调整均未改动生产行为。

独立审查检查了清理、文档、离线装配和连续旅程，发现两处测试问题并已修正：成功批次比较从夹具自比较改为两行实际渲染值的前后比较；终态任务汇总统一为来源／新增／更新 `15/15/0`，与三个叶子批次一致。修复后 9 项通过，原审查者复核两项均 resolved，无新增发现。

## 最终桌面对照

基准是 `2ae5963` 的模拟 Demo。以下检查无差异，排除本轮更新的 README 和基准后已有的独立 live Demo：

```bash
git diff 2ae5963 -- control-plane/src/demos ':!control-plane/src/demos/README.md' ':!control-plane/src/demos/live'
```

三种视口均为宽度 × 1000px，全页截图；关闭动画并等待任务／表格数据和下拉过渡稳定。下载页采用等价 daily／SINGLE 参数及五个相同接口／状态的历史任务；详情采用同一单次成功任务、一批一行；数据页使用相同日线样例、第一页 20 行；设置页采用相同默认强调色。自动核对外层宽度、字号、颜色、表格边界及详情宽度／留白；旅程检查页面无横向溢出、表格内部横向滚动和详情内部纵向滚动。

| 页面 | 1024px | 1280px | 1440px |
|---|---|---|---|
| 下载工作台 | [正式](studio-t10/downloads-1024.png)／[Demo](studio-t10/demo-downloads-1024.png) | [正式](studio-t10/downloads-1280.png)／[Demo](studio-t10/demo-downloads-1280.png) | [正式](studio-t10/downloads-1440.png)／[Demo](studio-t10/demo-downloads-1440.png) |
| 数据查看 | [正式](studio-t10/datasets-1024.png)／[Demo](studio-t10/demo-datasets-1024.png) | [正式](studio-t10/datasets-1280.png)／[Demo](studio-t10/demo-datasets-1280.png) | [正式](studio-t10/datasets-1440.png)／[Demo](studio-t10/demo-datasets-1440.png) |
| 外观设置 | [正式](studio-t10/settings-1024.png)／[Demo](studio-t10/demo-settings-1024.png) | [正式](studio-t10/settings-1280.png)／[Demo](studio-t10/demo-settings-1280.png) | [正式](studio-t10/settings-1440.png)／[Demo](studio-t10/demo-settings-1440.png) |
| 任务详情 | [正式](studio-t10/detail-1024.png)／[Demo](studio-t10/demo-detail-1024.png) | [正式](studio-t10/detail-1280.png)／[Demo](studio-t10/demo-detail-1280.png) | [正式](studio-t10/detail-1440.png)／[Demo](studio-t10/demo-detail-1440.png) |

保留的必要差异：

- 正式页没有演示横幅、页脚和假状态说明；提供真实数据源控件。接口名称、分组和顺序取自真实元数据，不以 Demo 目录快照覆盖。
- 正式任务列表显示接口编码、来源、完整性、参数与进度、更新时间和服务端分页；成功用“已成功”，失败／中断采用正式状态色。额外信息增高右栏，继续保持三栏和整页无横向溢出。
- 正式数据表保留全部 11 个业务列及 3 个来源列、业务标签、规范化日期、精确值和涨跌色，不复制 Demo 的 8 列限制。页大小和直接选页继续可用；1024px 查询按钮因数据源控件换行。表体背景、双层表头和字段宽度延续 T09 的真实表格行为。
- 正式详情保留规范化参数、来源计数、进度语义、任务／批次历史元信息和分页；与 Demo 同宽 430px，内容在弹窗内纵向滚动。设置说明反映浏览器持久化，区别于 Demo 的内存状态。

本轮 12 张正式截图和 12 张基准截图均新增保存，不覆盖 T01–T09 历史证据。

## 清理引用证据

删除前在根目录运行设计要求的搜索，并扩展到整个 `control-plane`（排除 node_modules／dist）：

```bash
rg -n 'WorkbenchPanel|ApiDescription|DownloadResult|MetadataField' control-plane/src control-plane/e2e
rg -n --hidden --glob '!node_modules/**' --glob '!dist/**' 'WorkbenchPanel|ApiDescription|DownloadResult|MetadataField|FieldError' control-plane
```

完整名称匹配（行号为删除前）：

| 名称 | 删除前全部匹配 | 处理 |
|---|---|---|
| WorkbenchPanel | `src/components/common/WorkbenchPanel.spec.js:3,5,7,26` | 无生产导入；删除组件和测试 |
| ApiDescription | `src/components/download/ApiDescription.spec.js:3,15,17,23` | 无生产导入；删除组件和测试 |
| DownloadResult | 自身测试 `:6,22,24,47,81,105`；`src/views/DownloadView.spec.js:12,512`；`src/layouts/AppLayout.spec.js:52,474`；`e2e/tushare-live.spec.js:1720` 的无关属性 `liveDownloadResultsRecorded` | 删除组件、独立测试及两份页面测试旧引用；保留无关属性 |
| MetadataField | `e2e/ui-redesign.spec.js:144,181` 的无关辅助函数 `fillMetadataField` | 无组件引用；删除组件，保留辅助函数 |
| FieldError | `SettingsView.vue:4,85`、`DynamicParameterForm.vue:4,71`、`DynamicFilterForm.vue:6,77`；`AsyncStatePanel.spec.js:5,99,101,109`、`SettingsView.spec.js:3,45`；API 字段错误类型／校验引用 `errors.js:14,25,78,102,115,159`；被删除 MetadataField 的 `:4,135` | 仍有生产及测试使用，保留 |

删除后同一搜索只剩 `fillMetadataField` 两行与 `liveDownloadResultsRecorded` 一行；它们均不引用已删除组件。上述四个 `.vue` 文件本身无同名自引用，因此引用清单结合文件路径和全部导入检查判断。

CSS 在全部源文件及测试中重新搜索，删除前完整匹配如下：

```text
workbench-panel:
  src/style.css:228,249,252,273,280
  src/components/common/WorkbenchPanel.vue:10,13
workbench-panel__meta:
  src/style.css:273,280
  src/components/common/WorkbenchPanel.vue:13
panel-heading:
  src/views/DownloadView.vue:217
  src/style.css:245,256,265,272
  src/components/common/WorkbenchPanel.vue:11
theme-status:
  src/views/SettingsView.vue:64,75,80
  src/style.css:274,454,459
```

只删除 `.workbench-panel`、`.workbench-panel__meta` 及 `.studio-form > .workbench-panel`／`.studio-tasks > .workbench-panel` 规则。混合规则仅去掉死分支，保留 `.theme-status`。`.panel-heading` 仍服务正式目录标题，所以保留本身、子选择器和 `.catalog-panel .panel-heading`。删除后前两类无匹配；后两类仍分别引用 `DownloadView.vue`／`SettingsView.vue` 及对应样式。未清理其他选择器、主题 token 或 Element Plus 覆盖。删除后布局由本轮三个宽度截图和相关浏览器回归验证。

## 入口隔离与边界

扫描全部非 Demo、非测试 JS／Vue（包含 `main.js`、`App.vue`）未发现 `demos`、`catalog.json`、`demoKey`、`createDemo` 依赖。构建后的 `dist/index.html` 只引用正式 app、共用依赖及任务列表模块，不引用 Demo 入口；`src/demos`、`src/demos/live`、`ui-demos.html`、`studio-live.html` 及 Vite 三入口均保留。

本轮不执行依赖真实 MySQL、Tushare 凭据或 packaged 后端的集成套件。HTTP 夹具仅证明前端请求接线、响应呈现、恢复和缓存行为，记录出现不证明真实数据库写入。T05 的后端 statusGroup 和参数一致性证据继续引用 [STUDIO-T05](STUDIO-T05.md)，前后端应一起发布，本轮不发布。

生产构建仍有既有 >500 kB chunk 提示：app 957.31 kB（gzip 304.56 kB），未开展打包优化。停止本轮临时 Vite 服务时，日志仍出现 `ResizeObserver loop completed with undelivered notifications`，与 T05／T08／T09 已记录现象一致；新增旅程的 pageerror 收集为空，但不能据此声称开发控制台没有日志。临时 4174 服务已停止。
