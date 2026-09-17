# DATA-INTEGRITY-T12 报告详情、问题定位与再次检查

## Goal

用户能从已保存的检查报告区分执行进度与数据结论，按股票、接口定位具体日期和完整业务键的问题，并使用当前口径发起新检查，保留原报告。

## Scope

扩展 T11 已注册的 `/integrity/checks/:checkId` 页面，实现报告总览、结果单元筛选分页、按单元查看问题及日期筛选、GET 轮询与重连、返回创建页复制条件。消费 T10 的精确 DTO 与状态模块，不修改 HTTP 合同、后端、规则或下载流程。浏览器验收使用 API stub；真实 MySQL/fixture 闭环仍属 T13。不新增报告编辑、导出、自动补数或当前规则重算旧报告。

## Approach

### 1. 已确认输入和页面结构

权威输入为共享设计第 5、6、8、9 节，T10 精确 DTO/API/生命周期和 T11 页面实现。T10 已完成；T11 已通过 Node24.15.0 完整前端 46 文件/696 项、14 项 Chromium stub、构建及独立复审。直接依赖没有未解决冲突。

`IntegrityCheckView.vue` 保留现有 `checkId` 路由 prop、`integrity-check` 路由名、顶部导航和返回入口。普通页面，不使用下载详情的 modal/drawer 或 KeepAlive。按顺序显示 PageHeading“检查报告”、工具栏、连接/错误信息、范围与汇总、结果列表、用户展开的问题区域；继承 `--tensor-*` token、字体、焦点和控件样式。

三个区域分别交给 `IntegritySummary.vue`、`IntegrityResultsTable.vue`、`IntegrityIssuesTable.vue`。页面拥有 T10 flow 和当前选中结果，问题区域拥有独立只读查询状态。宽屏摘要多列，680px 以下单列；表格仅在自身容器横向滚动，390px 文档宽度不得超过窗口。长编号、键、原因、依赖和证据可换行；状态有 Element Plus 图标及文字，不能仅靠颜色。

### 2. 详情与结果生命周期

页面创建一个 `useIntegrityCheck()`。watch `checkId` 并立即执行本地 `validateIntegrityCheckId`，合法后 `flow.load(id)`；非法值先停止旧活动轮询，清空页面选中结果/问题、显示“检查编号无效”，不发 GET。由非法切到合法时 `load` 恢复活动状态；由任务 A 切到 B 时全部问题状态和输入重置。卸载调用 `flow.dispose()`，问题 composable 同时清理。

初始详情为空且 loading 时显示加载态；不存在、无初始数据且请求失败分别显示明确错误和 requestId、返回链接及 GET 重试按钮。已有同任务数据时 GET 失败保留快照，在上方显示“连接已中断，以下为上次读取结果”和对应详情/结果错误的 requestId。工具栏“重新连接”只调用 `flow.reconnect()`，不调用创建方法。详情和结果任一失败后由 T10 停止轮询；重连成功且 QUEUED/RUNNING 时恢复完整轮次结束后 2 秒轮询，终态停止。

结果列表始终是当前可见页，使用 `flow.results`、`resultsCriteria` 和 `changeResults(criteria)`。筛选输入为股票、接口、数据结论：股票/接口选项来自**保存的** `detail.scope.symbols/apiNames`，不得用当前来源能力或本地股票清单；全部使用空 UI 值，发送时省略，不发空字符串。结论枚举为 PASS/FAIL/WARN/UNKNOWN/NOT_APPLICABLE。点击“查询结果”应用草稿并回到 page=1；“重置筛选”省略全部条件回到第1页。换页保留已应用条件，换 pageSize 回第1页；未应用的草稿不能偷偷随翻页提交。请求期间禁重复查询/翻页。

`flow.changeResults` 已负责清除不同 criteria 的旧结果和拒绝迟到响应；同条件刷新错误保留旧页。结果查询失败和详情查询失败分别显示，不因任一成功掩盖另一个失败。当前页为空给“没有符合条件的检查单元”，不改筛选/页码或默认为 PASS。

### 3. 汇总、单元和精确数值

`IntegritySummary` 输入只读 Detail，显示来源 pluginId、保存的完整股票/接口列表（摘要可展开全部）、原范围、checkId/submissionId/capabilityHash、创建/开始/结束时间和错误。时间沿用 `formatIngestedAt`，标明 Asia/Shanghai，并保留原 ISO 值作 datetime/title；null 时间显示“尚无”。原请求 `originalRequest` 与固定执行范围 `scope` 不混写；原请求省略 apiNames 时说明“受理时固定为以下接口”，实际接口仍读 scope。

执行状态文案固定为 QUEUED 排队中、RUNNING 执行中、COMPLETED 计算已完成、FAILED 执行失败、INTERRUPTED 已中断。数据结论独立显示 PASS 通过、FAIL 有问题、WARN 待核实、UNKNOWN 无法判定、NOT_APPLICABLE N/A（不适用）。直接使用保存的 overallStatus，不按 HTTP 200、COMPLETED 或显示比例重新推断。QUEUED/RUNNING 显示“进行中，已有结论”；FAILED/INTERRUPTED 显示中断/错误原因和未运行单元数。

进度使用精确文字“已处理 completedUnits / 计划 plannedUnits”，另列 errorUnits、notRunUnits 和五种 statusCounts。completedUnits **已经包含** errorUnits，不重复相加。不提供跨接口百分比或数据完整率进度条；全 N/A 与全 UNKNOWN 各自显示，不替换成通过。

`IntegrityResultsTable` 的输入为 ResultPage 与当前查询条件/加载/错误状态；emit `query`、`update:page`、`update:pageSize`、`select-result`。一行一个 resultId，不合并同股票同日的多个单元。主要列为股票或范围说明、apiName、检查日期轴、单元执行状态、overall/coverage/key/field 结论、实际/预期/命中数量、覆盖率、确认缺失/疑似缺口/额外/必填字段问题数，以及“查看问题”。所有字段直接来自 `item.report`。

NON_STOCK 通过已存 descriptor.scopeKind 或 symbol=null 显示“范围说明，不按股票逐只检查”；descriptor=null 显示“覆盖规则未实现”，仍保留该单元和结论，不猜日期轴。展开“保存的检查依据”显示 descriptor 日期标签/字段、规则名/ID/版本/维度/原因、limitations/dependencies、definitionHash、publishedRange、`report.scope.snapshotStartedAt`、finishedAt 和 evidence。不得请求当前 capability 给旧报告补名称或覆盖口径；descriptor 为 null 时仍展示已存 ruleResults 与原因。

单元执行状态分别显示 PENDING 待执行、RUNNING 执行中、COMPLETED 已计算、ERROR 执行错误、NOT_RUN 未运行。ERROR/NOT_RUN 不隐藏已有 FAIL。`incomplete=true` 显示“统计未完成”，`issuesComplete=false` 显示“问题明细不完整”，保留已知问题与 reasonCode/message，不声称列表为全部问题。

新增纯工具 `utils/integrityReport.js`：`formatIntegrityCount(value)` 将 BigInt 直接 `.toString()`，null 返回“无法计算”，0 返回“0”；`formatIntegrityRate(rate)` 将已解析的六位小数字符串做十进制位移，返回最多4位小数的百分数并去尾零，null 返回“无法计算”。例如 `0.950000→95%`、`0.999999→99.9999%`，不用二进制浮点决定结论。即使保存比例舍入为 `1.000000`，FAIL 和缺失计数仍原样展示。完整键标量格式化 `formatIntegrityValue(value)` 保留字符串精度、BigInt、安全整数、布尔和 null 文本，不将对象直接 JSON.stringify 导致 BigInt 抛错。

结果/问题复用本项 `IntegrityPagination.vue`：props `{ page:Number, pageSize:Number, total:BigInt, disabled:Boolean }`，events `update:page`、`update:pageSize`；沿用 T11 的 BigInt 总页和 next 上限逻辑，UI页长20/50/100。不要把 Long total 传入仅接收 Number 的 DatasetPagination。T11 历史无须为此任务重构。

### 4. 问题定位和按需查询

点击结果行“查看问题”将该**保存的 Result** 交给问题区域，明确显示股票/接口/范围与 resultId，滚动并聚焦区域标题（标题 tabindex=-1）；不会为其他结果发问题查询。该单元的 symbol/apiName 是本问题区域的股票/接口范围；要换股票/接口，使用结果筛选并选择另一个单元。关闭区域停止问题请求并把焦点还给原按钮（按钮消失时回结果标题）。不新增全库问题搜索页面。

`useIntegrityIssues()` 独立提供只读 `{checkId, criteria, page, loading, error}`，以及 `load(checkId, criteria)`、`refresh()`、`dispose()`。load 默认 `{page:1,pageSize:20}` 并消费 T10 `validateIntegrityCriteria('issues',...)` 与 `listIntegrityIssues(id,criteria,{signal})`。UI 每次固定传所选 resultId；查询类型、问题数据结论、问题日期起止，空条件发送时省略。type 选项覆盖 T10 ISSUE_TYPES：MISSING、SUSPECTED_MISSING、EXTRA、REQUIRED_FIELD_MISSING、BUSINESS_KEY_INVALID、SOURCE_IDENTITY_MISMATCH、REFERENCE_INCOMPLETE、DATE_SCOPE_UNRESOLVED、RULE_EXECUTION_FAILED。

查询/重置回第1页；重置清除 type/status/dateFrom/dateTo，保留该 resultId，所以无日期的问题重新可见。日期允许单边，1000..9999 严格 ISO 日期，双边有序；非法输入就地报错，不发请求。任何日期条件存在时明确显示“日期筛选仅包含可定位日期的问题，已排除日期未知项”。日期筛选以 issue.date 为唯一依据，不将 relatedDates 或任务创建时间当主日期。

composable 使用 generation + 本地 AbortController，切 checkId/resultId/已应用条件/页时取消旧请求并清空旧页；同条件 refresh 时保留旧页。所有 then/catch/finally 核对当前 generation、任务和控制器，晚到成功/错误不能覆盖新状态，卸载不写状态。没有问题轮询或自动重试；加载失败显示原 requestId 和“重新查询问题”，该操作只 GET。详情重新连接只重新连接 T10 详情/结果，不隐式恢复问题请求。

问题列表输入 IssuePage、所选 Result 和错误/加载状态。每条显示 issueId、type/status、symbol/apiName、dateField/date、field、reasonCode/message/incomplete、businessKey 全部键值、relatedDates 全部日期及 evidence 的 source/ruleVersion/range/readAt/summary。键用 definition list 或逐键表格，不只保留股票/日期、不把 scale 不同的 DECIMAL 转 Number；空业务键显示“未提供业务键”。date=null 显示“日期未知 / 整个检查范围”，不补造日期。

规则名称从所选保存报告的 ruleResults.descriptor 按 `ruleId+version` 查找；同时始终显示 Issue 自己的 ruleId/ruleVersion。若该存档缺对应描述，显示 ruleId 作为标识并说明“未保存规则名称”，不查当前注册表、不猜版本。页面空态写“当前条件下没有问题记录”，不据空列表推断 PASS，issuesComplete=false 的警示始终保留。

### 5. 再次检查与创建页衔接

详情已成功加载时提供“再次检查”，仅跳转 `{name:'integrity',query:{fromCheckId:detail.checkId}}`，不 POST、不复用旧 submissionId。用引用 ID 传递条件，避免把长股票/API数组塞入 URL 或另建持久缓存。

T12 在 `IntegrityView.vue` 增加该明确入口：没有未确认 pending 请求时验证 fromCheckId 并 GET 旧 Detail，复制 **scope** 的 pluginId、symbols、startDate/endDate 和显式 apiNames 为可编辑选择；不复制旧 hash/ID/统计/规则对象。查询旧报告失败显示 requestId 和“重新读取原条件”，不悄悄回退成全接口新检查。原来源目前停用/消失时仍展示原范围及不可提交原因，用户明确选新来源后按 T11 新来源流程处理。

旧条件成功读取后重新 `flow.loadCapabilities(pluginId)` 并查询分类，确认框初始为 false。复制的 API 数组不得被 T11 默认全选逻辑替换；保持缺失 API 可见且阻止提交，新增 API 不自动加入。当前能力的限额或上海日期规则不满足时显示具体错误，不缩减旧范围。用户明确确认并点击开始后才 prepare 新 ID；旧报告内容始终从保存结果读取，返回旧 URL 仍显示原数据。

如创建页已有 pendingSubmission，则先执行 T11 的原请求查回流程，原 scope/ID 与恢复控件优先；显示“请先确认上一次检查的提交结果，再从报告发起新检查”，不覆盖 pending、不并发读取/默认化复制范围、不自动 POST。确定拒绝且不处于 uncertain/prepared/忙碌状态时，用户可点击“读取这份报告的条件”再执行上述复制流程。复制查询及 fromCheckId 改变同样使用 generation/AbortController 隔离，不让迟到旧报告覆盖用户已开始编辑的新选择。

### 6. 可访问性与边界

每个筛选有 label 与稳定 error id/aria-describedby，分页、展开、返回、重连和再次检查可键盘操作。标题层级从页面 h1 到区域 h2，动态数据/连接变化采用简短 status，不每2秒朗读整个大表格。表格容器可聚焦并带区域名称，窄屏能通过键盘滚动到问题按钮。保留 T11 的 source/category generation 与原请求恢复；无需引入新状态库、来源专属分支或通用平台。

## Files

- 修改 `control-plane/src/views/IntegrityCheckView.vue` 与 spec：独立详情、T10生命周期、结果查询和所选问题协调；沿用当前路由 prop，无新增详情路由。
- 新增 `components/integrity/IntegritySummary.vue`、`IntegrityResultsTable.vue`、`IntegrityIssuesTable.vue`、`IntegrityPagination.vue` 及各自 spec：只读报告展示、按需问题与精确分页。
- 新增 `composables/useIntegrityIssues.js` 及 spec：问题按需 GET、筛选/页/任务隔离与错误重试。
- 新增 `utils/integrityReport.js` 及 spec：精确数量/百分数/标量显示；只放实际复用的显示逻辑。
- 修改 `views/IntegrityView.vue` 与 spec：fromCheckId 复制入口，当前能力确认与 pending 优先，不改 T10 公共方法。
- 扩展 `e2e/integrity-checks.spec.js` 及其专用 fixtures：原 T11 编号/导航断言保留，详情场景增加真实 DTO 的 Detail/ResultPage/IssuePage stub；不能继续断言合法详情零GET。
- 新增 `docs/verification/DATA-INTEGRITY-T12.md`，完成后回填看板并按顺序准备 T13。

## Tests

首个 RED：为未实现的报告格式化工具提供最小可调用骨架，断言 Long.MAX_VALUE `9223372036854775807n` 原样显示、null 显示“无法计算”、0n 显示“0”、`0.999999` 显示 `99.9999%`；观察行为断言失败，随后实现，不把导入错误当完成RED。

专项场景：

1. 保存 Detail 的 COMPLETED+FAIL、全 UNKNOWN、全 N/A、RUNNING已有结论、FAILED/INTERRUPTED/notRun/errorUnits；不重复计算已处理数，不出现跨接口百分比。规则升级后历史仍显示旧 descriptor/版本/证据，详情不 GET capability。
2. 结果严格一个 resultId 一行，NON_STOCK/null描述、ERROR/NOT_RUN、incomplete/issuesComplete、nullable统计、精确大数及长值。筛选/分页参数与旧页迟到结果、空结果、同条件失败保留均正确。
3. 问题完整多字段键（包括精确DECIMAL/Long和布尔/null）、关联日期、主日期null、规则名称与ID/版本、evidence、已截断结果。日期过滤发送dateFrom/dateTo并提示排除未知；重置后可见未知日期。按不同单元切换时忽略旧成功/错误/finally，关闭/卸载abort，无后台问题轮询。
4. 页面深链接/刷新加载，非法UUID不GET；任务A→B、页1→2和卸载的迟到响应不覆盖；真实T10的2秒轮询、终态停止、任一错误停轮询与GET重连。问题区域重试不会POST。
5. 再次检查引用旧报告scope，当前能力刷新且未确认时不提交；新hash/新增API/移除API不改旧范围；缺失来源/过限明确处理。用户明确开始后新submissionId、新checkId，返回旧URL内容不变。pending优先与复制请求晚到不覆盖编辑需独立测试。
6. 1440/1024/390：空/全UNKNOWN/N/A/长值/中断、完整键展开、键盘筛选/重连/再次检查；无文档横溢。T11与既有下载/数据查看/设置回归保持通过。

在隔离区根目录使用 Node24.15.0：

```sh
npm --prefix control-plane test -- src/utils/integrityReport.spec.js src/composables/useIntegrityIssues.spec.js src/components/integrity src/views/IntegrityCheckView.spec.js src/views/IntegrityView.spec.js
npm --prefix control-plane test
npm --prefix control-plane run build
npm --prefix control-plane run dev -- --host 127.0.0.1 --port 4178 --strictPort
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4178 npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js
```

预期专项/完整前端/stub全部通过，0失败/跳过，构建退出0；记录实际数量及截图检查。stub用 T09 完整合同示例、真实请求ID，不能跳过T10 DTO；这不是T13真实数据库闭环。

## Acceptance

可独立打开报告并区分执行状态和数据结论；保存的范围/版本/依据、精确统计、不完整范围和具体问题可查。用户通过股票/接口选择结果单元，再以类型/结论/主日期定位完整业务键，日期未知项可通过重置找回。切页/切任务/失败/卸载均不会显示过期数据或持续无效轮询；重连只GET。再次检查取得当前能力并创建新报告，旧报告不改写。三宽度和键盘验收、专项/全量/构建/stub通过，新增文件加入Git并回填实际证据。

## Risks

Issue DTO 不含独立规则显示名，必须用同一保存Result的ruleResults匹配，缺存档时只显示准确ID/版本；不可用当前规则补齐。T11历史摘要没有overallStatus，不改成N+1。BigInt不能直接传现有Number分页或直接JSON.stringify完整键。复制条件是新用户意图，pending未确认时优先查回旧请求；复制失败不能回退成另一范围。当前构建已有大chunk提示，本项不做无关打包改造。没有未解决的直接依赖冲突或需要外部输入的材料缺口；本设计只准备T12，不授权自动开始实施。
