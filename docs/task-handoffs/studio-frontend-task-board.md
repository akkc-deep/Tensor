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

- **Execution:** 按下表顺序在当前分支推进；串行执行由用户安排，本板不额外强制跨任务互斥。2026-09-16 用户明确要求开始 T01；T01 已完成并记录验收证据；2026-09-16 用户明确要求“完成STUDIO-T02”，本轮实施并验收 T02；T02 已完成；2026-09-16 用户明确要求“完成STUDIO-T03”，T03 已完成并记录验收证据；2026-09-16 用户明确要求“完成STUDIO-T04”，本轮实施并验收 T04；2026-09-16 用户明确要求“完成STUDIO-T05”，T05 已完成并记录验收证据；2026-09-16 用户明确要求“完成STUDIO-T06”，T06 已完成并记录验收证据；2026-09-16 用户明确要求“完成STUDIO-T07”，T07 已完成并记录验收证据；2026-09-16 用户明确要求“完成STUDIO-T08”，T08 已完成并记录验收证据；2026-09-16 用户明确要求“完成STUDIO-T09”，T09 已完成并记录验收证据；2026-09-16 用户明确要求“完成STUDIO-T10”，T10 已完成跨页面验收、引用清理和文档收尾；本板 T01–T10 全部完成。
- **Next-task selection:** 当前任务完成后，选择 `Order` 更大且最小的未完成任务。
- **Successor preparation:** 完成并关联后继任务的详细设计，再编写 `next-task` 交接并准备后继；后继设计未完成不改变前项已完成状态。
- **Allowed transitions:** `NOT_STARTED -> READY`、`READY -> IN_PROGRESS`、`IN_PROGRESS -> PAUSED`、`PAUSED -> IN_PROGRESS`、`READY -> BLOCKED`、`IN_PROGRESS -> BLOCKED`、`BLOCKED -> READY`、`IN_PROGRESS -> COMPLETED`。
- **验证分配:** 每项负责自己的业务检查、受影响测试及 PC 桌面表现。STUDIO-T10 负责跨模块验收和残留清理，各模块缺陷仍回到对应责任范围处理。
- **文档关联:** 本板是本项目任务身份和状态的唯一依据。任务设计创建后，将精确路径写入对应 `Design document`；未创建设计时使用 `None`，不将任务板描述视为已完成的实施设计。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
|---:|---|---|---|---|---|---|
| 1 | STUDIO-T01 | 页面框架、主题与外观设置 | COMPLETED | None | docs/task-designs/STUDIO-T01-design.md | None |
| 2 | STUDIO-T02 | 接口目录与真实元数据 | COMPLETED | STUDIO-T01 | docs/task-designs/STUDIO-T02-design.md | None |
| 3 | STUDIO-T03 | 单次／批量参数表单 | COMPLETED | STUDIO-T02 | docs/task-designs/STUDIO-T03-design.md | None |
| 4 | STUDIO-T04 | 下载提交与结果确认 | COMPLETED | STUDIO-T03 | docs/task-designs/STUDIO-T04-design.md | None |
| 5 | STUDIO-T05 | 最近任务、状态筛选与轮询 | COMPLETED | STUDIO-T04 | docs/task-designs/STUDIO-T05-design.md | None |
| 6 | STUDIO-T06 | 任务详情弹窗与批次明细 | COMPLETED | STUDIO-T05 | docs/task-designs/STUDIO-T06-design.md | None |
| 7 | STUDIO-T07 | 任务重试与中断恢复 | COMPLETED | STUDIO-T06 | docs/task-designs/STUDIO-T07-design.md | None |
| 8 | STUDIO-T08 | 数据集选择、筛选与查询 | COMPLETED | STUDIO-T01 | docs/task-designs/STUDIO-T08-design.md | None |
| 9 | STUDIO-T09 | 数据表格、格式与分页 | COMPLETED | STUDIO-T08 | docs/task-designs/STUDIO-T09-design.md | None |
| 10 | STUDIO-T10 | 整体对照、回归与旧代码清理 | COMPLETED | STUDIO-T07, STUDIO-T09 | docs/task-designs/STUDIO-T10-design.md | docs/task-handoffs/STUDIO-T10-handoff.md |

## Task Details

### STUDIO-T01

- **Goal:** 正式入口呈现 Studio 页面框架和外观设置。
- **Scope:** 迁移顶部导航、品牌、工作区标题、三栏容器、基础样式和 PC 窗口宽度适配；连接现有路由、页面缓存及主题状态；将外观设置适配为 Demo 样式。保留生产页面内容供后续逐项替换，不在本任务接入新业务流程。
- **Acceptance:** 下载、数据、设置路由以及前进／后退可用；外层布局、默认配色、字体和间距有与 Demo 的对照记录；PC 导航可操作；主题选择、恢复默认、已有主题持久化和存储失败降级可用；生产环境演示标识已处理；相关布局、路由、主题检查通过。
- **Dependencies:** `None`。
- **Sources:** 依次读取 `control-plane/src/demos/DemoApp.vue`、`control-plane/src/demos/demo.css`、`control-plane/src/layouts/AppLayout.vue`、`control-plane/src/router/index.js`、`control-plane/src/App.vue`、`control-plane/src/views/SettingsView.vue`、`control-plane/src/composables/useTheme.js`、`control-plane/src/style.css`、`control-plane/src/main.js`。
- **First action:** 对照 Demo 和正式页面框架，列出需迁移的容器、样式变量及主题控件，完成本任务设计并关联本板。
- **State evidence:** 用户要求将前端迁移为 Demo，并在本轮要求拆为 10 个中等偏小任务；本项初始化为 `READY`；2026-09-16 用户明确要求“开始前端重构T1任务吧”，在完整读取已关联设计后执行 `READY -> IN_PROGRESS`。2026-09-16 完成 Studio 顶栏、页面标题、三栏容器及主题/外观设置；全量单元/集成 571 项、既有 UI 浏览器回归 49 项、新增桌面对照/分页 3 项通过，最终受影响测试 52 项复验通过，构建通过。独立审查发现的大页码裁切已复现并修复；对照截图、限制及完整命令见 `docs/verification/STUDIO-T01.md`。新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T02

- **Goal:** 用户通过 Studio 左侧接口目录选择真实接口。
- **Scope:** 接入数据源和接口元数据，迁移搜索、分类、数量、选中状态以及中栏接口标题；适配加载、失败、空目录和数据源不可用状态。下载参数控件归 STUDIO-T03。
- **Acceptance:** 目录来自真实元数据请求；名称／接口编码搜索、分类和数量一致；选择接口更新中栏且快速切换不会显示过期响应；保留数据源可用性限制；PC 长目录滚动和选择可用；相关元数据和切换检查通过。
- **Dependencies:** `STUDIO-T01`，消费页面容器和公共样式。
- **Sources:** 依次读取 `control-plane/src/demos/DemoApp.vue`、`control-plane/src/api/dataSources.js`、`control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/views/DownloadView.vue`、`control-plane/src/components/download/DataSourceSelect.vue`、`control-plane/src/components/download/ApiSelect.vue`。
- **First action:** 将 Demo 目录字段与 `listDataSources`、`listApis` 和现有下载状态逐项对应，完成本任务设计。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T02”；已完成并完整读取 `docs/task-designs/STUDIO-T02-design.md`，按本项既定范围执行 `NOT_STARTED -> READY`，根据同一明确启动请求执行 `READY -> IN_PROGRESS`。2026-09-16 完成真实元数据驱动的 Studio 目录、搜索／分类／数量、选中态和中栏标题，完善来源／目录／能力加载失败与重试、空态、不可用来源防护及请求竞态验证。最终全量单元／集成 576 项、构建通过；49 项 UI、3 项股票参数浏览器回归通过，修复后目录／任务／外壳 24 项复验通过，合计覆盖 76 个不同浏览器场景；1024／1280／1440 对照和最终截图导出通过。独立审查发现的测试辅助函数正则兼容问题已复现并修复。命令、截图及未执行边界见 `docs/verification/STUDIO-T02.md`。新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T03

- **Goal:** Studio 表单按真实能力元数据呈现单次／批量参数。
- **Scope:** 迁移模式切换、参数输入、日期范围、校验与能力说明；复用已有日期规范化和参数规则。任务提交、受理提示及提交恢复归 STUDIO-T04。
- **Acceptance:** 控件由真实 `single`／`range` 参数驱动；必填、枚举、日期关联及代码规则正确；不支持／待验证能力无法提交批量请求；完整性说明与后端能力一致；表单不展示批次预览；输入值规范化及 PC 表单布局检查通过。
- **Dependencies:** `STUDIO-T02`，消费选中接口和能力加载状态。
- **Sources:** 依次读取 `control-plane/src/demos/DownloadForm.vue`、`control-plane/src/composables/useParameterForm.js`、`control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/api/downloadTasks.js`、`control-plane/src/api/downloadTaskDtos.js`、`control-plane/src/utils/date.js`、`control-plane/src/utils/validation.js`。
- **First action:** 对照现有参数校验与 Demo 控件，定义视图字段和规范化参数之间的关系，完成本任务设计。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T03”；已完成设计并回填引用，按本项既定范围执行 `NOT_STARTED -> READY`；完整读取已关联设计后，根据同一明确启动请求执行 `READY -> IN_PROGRESS`。完成 Studio 模式按钮、原生参数控件、明确起止字段的日期分组与真实完整性说明；保留校验／规范化／禁用和提交快照，修复重复模式重置、关联日期旧错误及不完整选填日期遗漏。最终 37 文件 582 项单元／集成、111 项离线采集测试、84 项浏览器回归和构建通过；1024／1280／1440 对照及最终截图导出通过。独立审查提出的原生日期 badInput 问题已复现、修复并复核。详细命令、六张截图和边界见 `docs/verification/STUDIO-T03.md`，新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T04

- **Goal:** 从 Studio 表单可靠创建真实下载任务，并确认提交结果。
- **Scope:** 将新表单接入现有 `useDownloadFlow`，迁移提交锁定、受理反馈、错误、结果不确定、刷新恢复和本地存储异常的界面。已创建任务的重试／恢复归 STUDIO-T07。
- **Acceptance:** 单次和批量请求使用真实任务 API；重复点击不重复创建；等待期间提交快照保持一致；网络结果不确定和页面刷新后沿用既有提交标识确认原任务；受理后触发已有列表刷新回调；失败信息可操作；受影响的提交流程检查通过。
- **Dependencies:** `STUDIO-T03`，消费模式、有效参数快照与表单状态。
- **Sources:** 依次读取 `control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/utils/downloadTaskSubmission.js`、`control-plane/src/views/DownloadView.vue`、`control-plane/src/api/downloadTasks.js`、`control-plane/src/composables/downloadTaskFlow.integration.spec.js`、`control-plane/src/composables/useDownloadFlow.spec.js`。
- **First action:** 将现有提交状态对应到 Studio 表单中的禁用、反馈和恢复入口，完成本任务设计。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T04”；完成并完整读取 `docs/task-designs/STUDIO-T04-design.md`，已回填设计引用，按本项既定范围执行 `NOT_STARTED -> READY`；再次完整读取设计后，根据同一启动请求执行 `READY -> IN_PROGRESS`。2026-09-16 完成 Studio 提交按钮、紧凑受理／错误反馈、等待与恢复原参数快照及存储异常恢复指引；复用既有幂等任务 API、刷新恢复与列表受理回调。37 文件 584 项单元／集成、111 项离线采集、分批覆盖 97 个浏览器场景及构建通过；最终提交专项 13 项和受影响单元 33 项复验通过。代码审查发现的旧 ElButton 测试与长标识局部溢出已复现、修复并复核；视觉审查的链接主题／焦点问题已关闭；修复回归测试中的主题页挂载竞态及无效调用。三种 PC 宽度对照、七张截图、命令与在线验收边界见 `docs/verification/STUDIO-T04.md`。新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T05

- **Goal:** Studio 右栏展示可刷新、可筛选的真实最近任务。
- **Scope:** 迁移任务卡片、状态分组、计数、分页、空态和刷新反馈，复用任务轮询生命周期。先在设计中解决 Demo 多状态分组与现有单状态查询契约的差异；记录准确计数和分页的数据来源及必要的最小适配。详情内容归 STUDIO-T06，任务操作归 STUDIO-T07。
- **Acceptance:** 全部／进行中／已完成／需处理分组符合 Demo 含义；筛选结果、数量与分页对应同一查询范围，不把当前页过滤误报为全部结果；新任务受理后列表更新；隐藏页面、离开页面及重新进入时轮询正确；真实状态、进度与错误可读；多页混合状态场景及轮询检查通过。
- **Dependencies:** `STUDIO-T04`，消费任务受理及列表刷新联动。
- **Sources:** 依次读取 `control-plane/src/demos/TaskList.vue`、`control-plane/src/composables/useDownloadTaskList.js`、`control-plane/src/components/download/DownloadTaskList.vue`、`control-plane/src/api/downloadTasks.js`、`control-plane/src/api/downloadTaskDtos.js`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskQuery.java`、`control-plane/src/composables/useDownloadTaskList.spec.js`。
- **First action:** 对照四个筛选分组与任务分页契约，完成分组查询和计数方案的设计及工作量复核。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T05”；完成并完整读取 `docs/task-designs/STUDIO-T05-design.md`，已回填设计引用，按既定范围执行 `NOT_STARTED -> READY`；再次完整读取设计，根据同一启动请求执行 `READY -> IN_PROGRESS`。2026-09-16 完成 Studio 最近任务卡片、四组服务端筛选、准确计数／分页、刷新反馈和轮询联动；保留完整历史字段与数值精度。后端新增互斥 statusGroup，并将列表总数／任务／批次计数收敛至同一 REPEATABLE_READ 快照。37 文件 599 项前端单元／集成、88 项浏览器回归、47 项后端定向、304 项 Core／Plugin API 单元、18 项真实 MySQL 集成及构建通过（后端测试存在重叠，不简单相加）。代码审查终态提示及视觉审查零结果页脚问题已通过 RED → GREEN 修复，最终组件 30 项和构建复验通过；视觉收尾复核为 ship。三种 PC 宽度、七张截图、命令及在线边界见 `docs/verification/STUDIO-T05.md`。新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T06

- **Goal:** 点击任务名称后，在 Studio 弹窗中查看真实任务和批次。
- **Scope:** 将任务详情与批次展示迁移到 Demo 弹窗，接入详情轮询和批次分页；保留任务链接直达、刷新与浏览器返回能力。重试／恢复按钮行为归 STUDIO-T07。
- **Acceptance:** 任务标识、参数、状态、计划进度、批次区间、尝试次数、行数和错误来自真实响应；正确展示计划未就绪、动态拆分和完整性语义；批次数值保持精度；详情和批次可分别报错及重载；关闭弹窗停止对应活动，焦点可返回触发处；深链接、分页及 PC 详情检查通过。
- **Dependencies:** `STUDIO-T05`，消费任务列表和详情打开入口。
- **Sources:** 依次读取 `control-plane/src/demos/DemoApp.vue`、`control-plane/src/views/DownloadTaskView.vue`、`control-plane/src/components/download/DownloadBatchTable.vue`、`control-plane/src/composables/useDownloadTask.js`、`control-plane/src/router/index.js`、`control-plane/src/api/downloadTaskDtos.js`、`control-plane/src/utils/downloadTaskText.js`。
- **First action:** 对照 Demo 弹窗与现有详情字段，定义弹窗、任务 URL 和轮询的生命周期，完成本任务设计。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T06”；已完成并完整读取 `docs/task-designs/STUDIO-T06-design.md`，回填设计引用，按既定范围执行 `NOT_STARTED -> READY`；再次完整读取设计，根据同一明确请求执行 `READY -> IN_PROGRESS`。2026-09-16 完成真实任务详情的 Studio 路由弹窗、纵向批次及分页、计划／完整性／大整数语义、独立错误重载和焦点／轮询生命周期；保留下载草稿及既有重试／恢复行为。最终 37 文件 601 项前端单元／集成、111 项离线证据测试、分批覆盖 96 个不同浏览器场景和构建通过；视觉修正后详情 8 项及 1024px 分页专项复验通过。独立代码审查的返回链接额外历史记录通过 RED → GREEN 修复并复核；视觉审查三项收尾（琥珀色／分页证据／独立错误证据）均 resolved，限定复核为 ship。三种 PC 宽度、12 张截图、命令及在线验收边界见 `docs/verification/STUDIO-T06.md`。新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T07

- **Goal:** 在列表与详情中安全重试失败任务、恢复中断任务。
- **Scope:** 将列表快捷操作和详情按钮接入既有重试／恢复业务逻辑；统一权限状态、禁用、操作反馈和操作后的详情／列表刷新。创建任务时的提交恢复已由 STUDIO-T04 负责。
- **Acceptance:** 按新鲜任务快照中的 `canRetry`／`canResume` 决定操作能力；请求携带正确版本；快速点击不重复发送；状态冲突及操作结果不确定时重新查询；成功批次和行数由服务端保留；列表和详情最终一致；部分失败、中断、冲突及网络异常检查通过。
- **Dependencies:** `STUDIO-T06`，消费列表入口、详情弹窗及任务状态。
- **Sources:** 依次读取 `control-plane/src/demos/TaskList.vue`、`control-plane/src/demos/DemoApp.vue`、`control-plane/src/composables/useDownloadTask.js`、`control-plane/src/api/downloadTasks.js`、`control-plane/src/composables/useDownloadTask.spec.js`、`control-plane/e2e/download-tasks.spec.js`、`control-plane/e2e/download-task-lifecycle.spec.js`。
- **First action:** 对照现有任务操作约束，明确两个入口如何共享操作与刷新行为，完成本任务设计。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T07”；完成并完整读取 `docs/task-designs/STUDIO-T07-design.md`，回填设计引用，按既定范围执行 `NOT_STARTED -> READY`；根据同一明确请求执行 `READY -> IN_PROGRESS`。2026-09-16 完成列表快捷重试／恢复、最新详情预查询、精确版本操作及跨入口锁；操作后列表／详情刷新包含详情关闭后的迟到结果；修复隐藏后旧许可提前启用及同任务旧反馈残留。最终 37 文件 607 项单元／集成、两组去重 105 个浏览器场景及构建通过；三个 PC 宽度对照、八张截图和验证边界见 `docs/verification/STUDIO-T07.md`。独立代码审查的两项反馈问题均通过 RED → GREEN 修正，查询时态文案已修正并限定复核关闭，无未解决审查问题。新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T08

- **Goal:** Studio 数据查看页使用真实数据集定义和筛选条件发起查询。
- **Scope:** 迁移数据源／数据集选择、横向筛选栏、查询与重置，复用查询状态和筛选校验；处理元数据加载、切换、失败和空状态。先沿用现有结果表格，表格外观与分页控件归 STUDIO-T09。
- **Acceptance:** 数据集和筛选字段来自真实元数据；只发送当前定义支持的筛选参数；查询、重置和重试符合现有语义；切换数据集丢弃过期响应；未提交草稿不影响已执行查询；页面往返状态及 PC 筛选布局可用；相关筛选与查询检查通过。
- **Dependencies:** `STUDIO-T01`，消费数据页面框架和样式。
- **Sources:** 依次读取 `control-plane/src/demos/DatasetBrowser.vue`、`control-plane/src/views/DatasetView.vue`、`control-plane/src/api/datasets.js`、`control-plane/src/composables/useDatasetFilters.js`、`control-plane/src/composables/useDatasetQuery.js`、`control-plane/src/components/dataset/DynamicFilterForm.vue`、`control-plane/src/composables/useDatasetQuery.spec.js`。
- **First action:** 对照 Demo 筛选栏与真实数据集定义，列出控件、查询快照和错误状态的对应关系，完成本任务设计。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T08”；完成设计并回填引用，按本项既定范围执行 `NOT_STARTED -> READY`；完整读取关联设计后，根据同一明确请求执行 `READY -> IN_PROGRESS`。 完成 Studio 横向数据选择／筛选栏、Enter 查询和提交防重、真实元数据加载／重试／空态及日期校验；保留查询快照、切换竞态、重置和页面缓存。最终 37 文件 609 项单元／集成、63 项浏览器回归、构建通过；最后 11 项查询专项及 8 张截图复验通过。1024／1280／1440 桌面对照、四主题、纯键盘查询和审查发现的原生日期键盘用例适配均完成。完整命令、截图及未运行的后端集成边界见 `docs/verification/STUDIO-T08.md`；新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T09

- **Goal:** Studio 表格完整呈现真实查询结果，支持准确分页。
- **Scope:** 迁移表头、单元格、横向滚动、空态和分页外观，复用格式化、完整字段及分页逻辑；适配长文本、空值和宽表。筛选栏和请求状态由 STUDIO-T08 提供。
- **Acceptance:** 展示真实完整字段和服务端页数据，不引入 Demo 的 24 行／8 列限制；保留来源列及现有数值精度、日期、单位和空值语义；切页／改页大小使用已提交查询条件；宽表仅在表格区域横向滚动；键盘、PC 宽表、超大整数及长文本检查通过。
- **Dependencies:** `STUDIO-T08`，消费数据集定义、查询结果及已提交筛选快照。
- **Sources:** 依次读取 `control-plane/src/demos/DatasetBrowser.vue`、`control-plane/src/components/dataset/DatasetTable.vue`、`control-plane/src/components/dataset/DatasetPagination.vue`、`control-plane/src/utils/format.js`、`control-plane/src/composables/useDatasetQuery.js`、`control-plane/src/components/dataset/DatasetTable.spec.js`、`control-plane/e2e/dataset-query.spec.js`。
- **First action:** 将 Demo 表格样式对应到真实列定义和现有格式化规则，完成本任务设计。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T09”；完成设计并回填引用，按本项既定范围执行 `NOT_STARTED -> READY`；完整读取关联设计后，根据同一明确请求执行 `READY -> IN_PROGRESS`。 完成 Studio 完整表格、局部双向滚动、精确格式与分页外观；补齐长文本聚焦、全文键盘滚动和全局 Esc 关闭。最终 37 文件 609 项单元／集成、71 项浏览器回归和构建通过；1024／1280／1440 等价记录对照及 10 张截图完成。独立审查三项长文本问题已复现、修复并通过限定复核。命令、截图、差异及集成边界见 `docs/verification/STUDIO-T09.md`；新增文件已被 Git 跟踪，据此执行 `IN_PROGRESS -> COMPLETED`。

### STUDIO-T10

- **Goal:** 完成跨页面视觉与业务验收，清理本次迁移留下的冗余代码。
- **Scope:** 汇总各项已完成的模块验证，完成正式入口的跨页面联动和 PC 视口对照；更新受影响的端到端断言及使用说明；清理确认无引用的旧组件／样式。Demo 基准资源的保留或归档在本任务设计中记录，保留可追溯的对照来源。
- **Acceptance:** 三个主页面及任务详情在 PC 上有相同视口的对照记录；新增任务、查看进度、重试／恢复、查询数据和主题切换的跨页面场景通过；正式入口无演示数据依赖；受影响单元／集成／端到端检查及构建通过；删除项经引用检查；新增文件被 Git 跟踪，记录验证结果和残留限制。
- **Dependencies:** `STUDIO-T07`，消费完整下载与任务链路；`STUDIO-T09`，消费完整数据查看链路；两条链路包含此前页面与主题成果。
- **Sources:** 依次读取本任务板、各任务已关联的设计和验证记录、`control-plane/e2e/ui-redesign.spec.js`、`control-plane/e2e/ui-redesign.fixtures.js`、`control-plane/e2e/download-tasks.spec.js`、`control-plane/e2e/dataset-query.spec.js`、`control-plane/package.json`、`control-plane/playwright.ui.config.js`、`control-plane/vite.config.js`、`control-plane/src/demos/README.md`。
- **First action:** 汇总前九项验收证据，列出剩余跨模块对照与引用清理范围，完成本任务设计。
- **State evidence:** 2026-09-16 用户明确要求“完成STUDIO-T10”，完整读取已关联设计和交接后执行 `READY -> IN_PROGRESS`。两条连续旅程、三宽度四页面对照、引用清理与使用说明完成；全量 34 文件／601 项单元集成、130 项离线检查、150 项开发浏览器回归、49 项构建产物 UI 回归及生产构建通过，审查补强后 9 项验收复验通过。独立审查问题全部 resolved。`docs/verification/STUDIO-T10.md` 与 `docs/verification/studio-t10/` 记录 24 张新截图、删除引用证据和真实集成边界；新增文件已加入 Git，执行 `IN_PROGRESS -> COMPLETED`。无更大 Order 的后继任务，Studio 迁移任务板全部完成；保留既有交接作为历史上下文，不创建后继。

## Risks

- Demo 的目录、参数能力和数据记录是快照，批量进度由定时器模拟；这些内容不能成为正式业务的数据来源或执行规则。
- Demo 状态分组跨多个任务状态，现有任务查询只接受单个 `status`；STUDIO-T05 必须先核实查询、计数和分页方案，契约适配可能影响该项估算。
- 正式应用具有 Demo 未展示的提交结果不确定、版本冲突、动态拆分及完整性状态；相关任务需在 Studio 界面中明确呈现这些状态。
- 正式数据包含完整列、来源字段及精确数值，宽度与 Demo 样例不同；视觉对照需要等价数据条件，并覆盖真实宽表。
- 现有端到端检查绑定旧布局和控件。各任务需同步调整受影响的界面断言并保留业务语义，最后一项只负责跨模块收尾。
- 整体工作量仍为源码评估；T01、T02 已有浏览器视觉对照和实现验证，详见各自验收记录，不能据此推断其余任务完成。
