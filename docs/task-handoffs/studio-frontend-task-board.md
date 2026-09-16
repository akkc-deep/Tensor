# Studio 前端迁移任务板

## Project

- **Project ID:** `studio-frontend`。
- **Goal:** 在当前 `feat/studio-frontend` 分支，将正式前端的布局、视觉和交互迁移为 `control-plane/src/demos` 的 Studio 工作台，并接入真实业务。
- **Scope:** 按用户要求拆为 10 个中等偏小任务，允许彻底重构前端，采用最小实现。规划沿用上一轮建议：复用现有 API、业务状态与校验能力，保留正式应用现有业务能力；尚无删除业务能力的要求。覆盖页面框架、外观设置、接口目录、下载、任务、数据查询和表格。后端批量执行策略、数据采集能力扩展不属于本次迁移；任务筛选契约差异在 STUDIO-T05 的设计中单独处理。
- **平台范围:** 按用户 2026-09-16 的最新要求，仅适配和验收 PC 桌面浏览器；手机、平板及其他平台不在范围内。
- **视觉基准:** 以拆分时提交 `2ae5963` 的 `control-plane/src/demos` 为基准。正式产品区域按相同视口和等价数据状态对照；Demo 的示例数据、模拟进度、演示入口和“示例／本地预览”等说明应替换为真实状态或移除。正式业务额外的加载、错误、分页与恢复状态沿用同一视觉语言，在对应任务设计中记录具体落点。
- **Completion condition:** 正式入口提供 Studio 下载工作台、数据查看和外观设置；单次／批量提交、任务查询、重试恢复、真实数据查询分页可用；PC 桌面对照完成；受影响的单元、集成和端到端检查通过；生产入口不依赖 Demo 模拟状态和目录快照；新增文件加入 Git。
- **工作量口径:** 每项以约 0.5–1 人日为拆分目标，包含该模块的适配与验证。该范围是基于现有业务逻辑可复用的估算，详细设计发现契约缺口时应更新估算。拆分降低单次变更范围，总工作量仍接近此前估算的 5–8 人日。

## Workflow

- **Execution:** 按下表顺序在当前分支推进；串行执行由用户安排，本板不额外强制跨任务互斥。此次仅初始化任务板，首项 `READY`，其余 `NOT_STARTED`，尚未开始实现。
- **Next-task selection:** 当前任务完成后，选择 `Order` 更大且最小的未完成任务。
- **Successor preparation:** 完成并关联后继任务的详细设计，再编写 `next-task` 交接并准备后继；后继设计未完成不改变前项已完成状态。
- **Allowed transitions:** `NOT_STARTED -> READY`、`READY -> IN_PROGRESS`、`IN_PROGRESS -> PAUSED`、`PAUSED -> IN_PROGRESS`、`READY -> BLOCKED`、`IN_PROGRESS -> BLOCKED`、`BLOCKED -> READY`、`IN_PROGRESS -> COMPLETED`。
- **验证分配:** 每项负责自己的业务检查、受影响测试及 PC 桌面表现。STUDIO-T10 负责跨模块验收和残留清理，各模块缺陷仍回到对应责任范围处理。
- **文档关联:** 本板是本项目任务身份和状态的唯一依据。任务设计创建后，将精确路径写入对应 `Design document`；未创建设计时使用 `None`，不将任务板描述视为已完成的实施设计。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
|---:|---|---|---|---|---|---|
| 1 | STUDIO-T01 | 页面框架、主题与外观设置 | READY | None | None | None |
| 2 | STUDIO-T02 | 接口目录与真实元数据 | NOT_STARTED | STUDIO-T01 | None | None |
| 3 | STUDIO-T03 | 单次／批量参数表单 | NOT_STARTED | STUDIO-T02 | None | None |
| 4 | STUDIO-T04 | 下载提交与结果确认 | NOT_STARTED | STUDIO-T03 | None | None |
| 5 | STUDIO-T05 | 最近任务、状态筛选与轮询 | NOT_STARTED | STUDIO-T04 | None | None |
| 6 | STUDIO-T06 | 任务详情弹窗与批次明细 | NOT_STARTED | STUDIO-T05 | None | None |
| 7 | STUDIO-T07 | 任务重试与中断恢复 | NOT_STARTED | STUDIO-T06 | None | None |
| 8 | STUDIO-T08 | 数据集选择、筛选与查询 | NOT_STARTED | STUDIO-T01 | None | None |
| 9 | STUDIO-T09 | 数据表格、格式与分页 | NOT_STARTED | STUDIO-T08 | None | None |
| 10 | STUDIO-T10 | 整体对照、回归与旧代码清理 | NOT_STARTED | STUDIO-T07, STUDIO-T09 | None | None |

## Task Details

### STUDIO-T01

- **Goal:** 正式入口呈现 Studio 页面框架和外观设置。
- **Scope:** 迁移顶部导航、品牌、工作区标题、三栏容器、基础样式和 PC 窗口宽度适配；连接现有路由、页面缓存及主题状态；将外观设置适配为 Demo 样式。保留生产页面内容供后续逐项替换，不在本任务接入新业务流程。
- **Acceptance:** 下载、数据、设置路由以及前进／后退可用；外层布局、默认配色、字体和间距有与 Demo 的对照记录；PC 导航可操作；主题选择、恢复默认、已有主题持久化和存储失败降级可用；生产环境演示标识已处理；相关布局、路由、主题检查通过。
- **Dependencies:** `None`。
- **Sources:** 依次读取 `control-plane/src/demos/DemoApp.vue`、`control-plane/src/demos/demo.css`、`control-plane/src/layouts/AppLayout.vue`、`control-plane/src/router/index.js`、`control-plane/src/App.vue`、`control-plane/src/views/SettingsView.vue`、`control-plane/src/composables/useTheme.js`、`control-plane/src/style.css`、`control-plane/src/main.js`。
- **First action:** 对照 Demo 和正式页面框架，列出需迁移的容器、样式变量及主题控件，完成本任务设计并关联本板。
- **State evidence:** 用户要求将前端迁移为 Demo，并在本轮要求拆为 10 个中等偏小任务；本项按初始化规则设为 `READY`，尚无实现或验证结果。

### STUDIO-T02

- **Goal:** 用户通过 Studio 左侧接口目录选择真实接口。
- **Scope:** 接入数据源和接口元数据，迁移搜索、分类、数量、选中状态以及中栏接口标题；适配加载、失败、空目录和数据源不可用状态。下载参数控件归 STUDIO-T03。
- **Acceptance:** 目录来自真实元数据请求；名称／接口编码搜索、分类和数量一致；选择接口更新中栏且快速切换不会显示过期响应；保留数据源可用性限制；PC 长目录滚动和选择可用；相关元数据和切换检查通过。
- **Dependencies:** `STUDIO-T01`，消费页面容器和公共样式。
- **Sources:** 依次读取 `control-plane/src/demos/DemoApp.vue`、`control-plane/src/api/dataSources.js`、`control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/views/DownloadView.vue`、`control-plane/src/components/download/DataSourceSelect.vue`、`control-plane/src/components/download/ApiSelect.vue`。
- **First action:** 将 Demo 目录字段与 `listDataSources`、`listApis` 和现有下载状态逐项对应，完成本任务设计。
- **State evidence:** `None`。

### STUDIO-T03

- **Goal:** Studio 表单按真实能力元数据呈现单次／批量参数。
- **Scope:** 迁移模式切换、参数输入、日期范围、校验与能力说明；复用已有日期规范化和参数规则。任务提交、受理提示及提交恢复归 STUDIO-T04。
- **Acceptance:** 控件由真实 `single`／`range` 参数驱动；必填、枚举、日期关联及代码规则正确；不支持／待验证能力无法提交批量请求；完整性说明与后端能力一致；表单不展示批次预览；输入值规范化及 PC 表单布局检查通过。
- **Dependencies:** `STUDIO-T02`，消费选中接口和能力加载状态。
- **Sources:** 依次读取 `control-plane/src/demos/DownloadForm.vue`、`control-plane/src/composables/useParameterForm.js`、`control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/api/downloadTasks.js`、`control-plane/src/api/downloadTaskDtos.js`、`control-plane/src/utils/date.js`、`control-plane/src/utils/validation.js`。
- **First action:** 对照现有参数校验与 Demo 控件，定义视图字段和规范化参数之间的关系，完成本任务设计。
- **State evidence:** `None`。

### STUDIO-T04

- **Goal:** 从 Studio 表单可靠创建真实下载任务，并确认提交结果。
- **Scope:** 将新表单接入现有 `useDownloadFlow`，迁移提交锁定、受理反馈、错误、结果不确定、刷新恢复和本地存储异常的界面。已创建任务的重试／恢复归 STUDIO-T07。
- **Acceptance:** 单次和批量请求使用真实任务 API；重复点击不重复创建；等待期间提交快照保持一致；网络结果不确定和页面刷新后沿用既有提交标识确认原任务；受理后触发已有列表刷新回调；失败信息可操作；受影响的提交流程检查通过。
- **Dependencies:** `STUDIO-T03`，消费模式、有效参数快照与表单状态。
- **Sources:** 依次读取 `control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/utils/downloadTaskSubmission.js`、`control-plane/src/views/DownloadView.vue`、`control-plane/src/api/downloadTasks.js`、`control-plane/src/composables/downloadTaskFlow.integration.spec.js`、`control-plane/src/composables/useDownloadFlow.spec.js`。
- **First action:** 将现有提交状态对应到 Studio 表单中的禁用、反馈和恢复入口，完成本任务设计。
- **State evidence:** `None`。

### STUDIO-T05

- **Goal:** Studio 右栏展示可刷新、可筛选的真实最近任务。
- **Scope:** 迁移任务卡片、状态分组、计数、分页、空态和刷新反馈，复用任务轮询生命周期。先在设计中解决 Demo 多状态分组与现有单状态查询契约的差异；记录准确计数和分页的数据来源及必要的最小适配。详情内容归 STUDIO-T06，任务操作归 STUDIO-T07。
- **Acceptance:** 全部／进行中／已完成／需处理分组符合 Demo 含义；筛选结果、数量与分页对应同一查询范围，不把当前页过滤误报为全部结果；新任务受理后列表更新；隐藏页面、离开页面及重新进入时轮询正确；真实状态、进度与错误可读；多页混合状态场景及轮询检查通过。
- **Dependencies:** `STUDIO-T04`，消费任务受理及列表刷新联动。
- **Sources:** 依次读取 `control-plane/src/demos/TaskList.vue`、`control-plane/src/composables/useDownloadTaskList.js`、`control-plane/src/components/download/DownloadTaskList.vue`、`control-plane/src/api/downloadTasks.js`、`control-plane/src/api/downloadTaskDtos.js`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskQuery.java`、`control-plane/src/composables/useDownloadTaskList.spec.js`。
- **First action:** 对照四个筛选分组与任务分页契约，完成分组查询和计数方案的设计及工作量复核。
- **State evidence:** `None`。

### STUDIO-T06

- **Goal:** 点击任务名称后，在 Studio 弹窗中查看真实任务和批次。
- **Scope:** 将任务详情与批次展示迁移到 Demo 弹窗，接入详情轮询和批次分页；保留任务链接直达、刷新与浏览器返回能力。重试／恢复按钮行为归 STUDIO-T07。
- **Acceptance:** 任务标识、参数、状态、计划进度、批次区间、尝试次数、行数和错误来自真实响应；正确展示计划未就绪、动态拆分和完整性语义；批次数值保持精度；详情和批次可分别报错及重载；关闭弹窗停止对应活动，焦点可返回触发处；深链接、分页及 PC 详情检查通过。
- **Dependencies:** `STUDIO-T05`，消费任务列表和详情打开入口。
- **Sources:** 依次读取 `control-plane/src/demos/DemoApp.vue`、`control-plane/src/views/DownloadTaskView.vue`、`control-plane/src/components/download/DownloadBatchTable.vue`、`control-plane/src/composables/useDownloadTask.js`、`control-plane/src/router/index.js`、`control-plane/src/api/downloadTaskDtos.js`、`control-plane/src/utils/downloadTaskText.js`。
- **First action:** 对照 Demo 弹窗与现有详情字段，定义弹窗、任务 URL 和轮询的生命周期，完成本任务设计。
- **State evidence:** `None`。

### STUDIO-T07

- **Goal:** 在列表与详情中安全重试失败任务、恢复中断任务。
- **Scope:** 将列表快捷操作和详情按钮接入既有重试／恢复业务逻辑；统一权限状态、禁用、操作反馈和操作后的详情／列表刷新。创建任务时的提交恢复已由 STUDIO-T04 负责。
- **Acceptance:** 按新鲜任务快照中的 `canRetry`／`canResume` 决定操作能力；请求携带正确版本；快速点击不重复发送；状态冲突及操作结果不确定时重新查询；成功批次和行数由服务端保留；列表和详情最终一致；部分失败、中断、冲突及网络异常检查通过。
- **Dependencies:** `STUDIO-T06`，消费列表入口、详情弹窗及任务状态。
- **Sources:** 依次读取 `control-plane/src/demos/TaskList.vue`、`control-plane/src/demos/DemoApp.vue`、`control-plane/src/composables/useDownloadTask.js`、`control-plane/src/api/downloadTasks.js`、`control-plane/src/composables/useDownloadTask.spec.js`、`control-plane/e2e/download-tasks.spec.js`、`control-plane/e2e/download-task-lifecycle.spec.js`。
- **First action:** 对照现有任务操作约束，明确两个入口如何共享操作与刷新行为，完成本任务设计。
- **State evidence:** `None`。

### STUDIO-T08

- **Goal:** Studio 数据查看页使用真实数据集定义和筛选条件发起查询。
- **Scope:** 迁移数据源／数据集选择、横向筛选栏、查询与重置，复用查询状态和筛选校验；处理元数据加载、切换、失败和空状态。先沿用现有结果表格，表格外观与分页控件归 STUDIO-T09。
- **Acceptance:** 数据集和筛选字段来自真实元数据；只发送当前定义支持的筛选参数；查询、重置和重试符合现有语义；切换数据集丢弃过期响应；未提交草稿不影响已执行查询；页面往返状态及 PC 筛选布局可用；相关筛选与查询检查通过。
- **Dependencies:** `STUDIO-T01`，消费数据页面框架和样式。
- **Sources:** 依次读取 `control-plane/src/demos/DatasetBrowser.vue`、`control-plane/src/views/DatasetView.vue`、`control-plane/src/api/datasets.js`、`control-plane/src/composables/useDatasetFilters.js`、`control-plane/src/composables/useDatasetQuery.js`、`control-plane/src/components/dataset/DynamicFilterForm.vue`、`control-plane/src/composables/useDatasetQuery.spec.js`。
- **First action:** 对照 Demo 筛选栏与真实数据集定义，列出控件、查询快照和错误状态的对应关系，完成本任务设计。
- **State evidence:** `None`。

### STUDIO-T09

- **Goal:** Studio 表格完整呈现真实查询结果，支持准确分页。
- **Scope:** 迁移表头、单元格、横向滚动、空态和分页外观，复用格式化、完整字段及分页逻辑；适配长文本、空值和宽表。筛选栏和请求状态由 STUDIO-T08 提供。
- **Acceptance:** 展示真实完整字段和服务端页数据，不引入 Demo 的 24 行／8 列限制；保留来源列及现有数值精度、日期、单位和空值语义；切页／改页大小使用已提交查询条件；宽表仅在表格区域横向滚动；键盘、PC 宽表、超大整数及长文本检查通过。
- **Dependencies:** `STUDIO-T08`，消费数据集定义、查询结果及已提交筛选快照。
- **Sources:** 依次读取 `control-plane/src/demos/DatasetBrowser.vue`、`control-plane/src/components/dataset/DatasetTable.vue`、`control-plane/src/components/dataset/DatasetPagination.vue`、`control-plane/src/utils/format.js`、`control-plane/src/composables/useDatasetQuery.js`、`control-plane/src/components/dataset/DatasetTable.spec.js`、`control-plane/e2e/dataset-query.spec.js`。
- **First action:** 将 Demo 表格样式对应到真实列定义和现有格式化规则，完成本任务设计。
- **State evidence:** `None`。

### STUDIO-T10

- **Goal:** 完成跨页面视觉与业务验收，清理本次迁移留下的冗余代码。
- **Scope:** 汇总各项已完成的模块验证，完成正式入口的跨页面联动和 PC 视口对照；更新受影响的端到端断言及使用说明；清理确认无引用的旧组件／样式。Demo 基准资源的保留或归档在本任务设计中记录，保留可追溯的对照来源。
- **Acceptance:** 三个主页面及任务详情在 PC 上有相同视口的对照记录；新增任务、查看进度、重试／恢复、查询数据和主题切换的跨页面场景通过；正式入口无演示数据依赖；受影响单元／集成／端到端检查及构建通过；删除项经引用检查；新增文件被 Git 跟踪，记录验证结果和残留限制。
- **Dependencies:** `STUDIO-T07`，消费完整下载与任务链路；`STUDIO-T09`，消费完整数据查看链路；两条链路包含此前页面与主题成果。
- **Sources:** 依次读取本任务板、各任务已关联的设计和验证记录、`control-plane/e2e/ui-redesign.spec.js`、`control-plane/e2e/ui-redesign.fixtures.js`、`control-plane/e2e/download-tasks.spec.js`、`control-plane/e2e/dataset-query.spec.js`、`control-plane/package.json`、`control-plane/playwright.ui.config.js`、`control-plane/vite.config.js`、`control-plane/src/demos/README.md`。
- **First action:** 汇总前九项验收证据，列出剩余跨模块对照与引用清理范围，完成本任务设计。
- **State evidence:** `None`。

## Risks

- Demo 的目录、参数能力和数据记录是快照，批量进度由定时器模拟；这些内容不能成为正式业务的数据来源或执行规则。
- Demo 状态分组跨多个任务状态，现有任务查询只接受单个 `status`；STUDIO-T05 必须先核实查询、计数和分页方案，契约适配可能影响该项估算。
- 正式应用具有 Demo 未展示的提交结果不确定、版本冲突、动态拆分及完整性状态；相关任务需在 Studio 界面中明确呈现这些状态。
- 正式数据包含完整列、来源字段及精确数值，宽度与 Demo 样例不同；视觉对照需要等价数据条件，并覆盖真实宽表。
- 现有端到端检查绑定旧布局和控件。各任务需同步调整受影响的界面断言并保留业务语义，最后一项只负责跨模块收尾。
- 当前工作量来自源码评估，尚未完成浏览器视觉基线与本轮实现验证；任务状态不得据此推断为已完成。
