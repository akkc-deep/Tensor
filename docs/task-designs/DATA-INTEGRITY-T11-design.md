# DATA-INTEGRITY-T11 创建检查、口径预览与历史页面

## Goal

在现有工作区中创建明确范围的本地检查，并从分页历史进入已保存报告。用户在提交前能理解每个接口的日期口径、快照限制、不适用和依据不足；缺下载Token不妨碍本地检查。

## Scope

新增 `/integrity` 创建/历史页面、表单与口径预览组件、历史组件、导航入口及测试。消费T10已验收的六API与状态，不更改HTTP、后端规则、精确DTO或下载流程。成功受理与历史链接指向 `/integrity/checks/:checkId`；T11提供最小可路由的报告入口，完整报告内容、结果/问题筛选和再次检查属于T12。本项浏览器测试使用API stub，真实后端验收属于T13。

## Approach

### 1. 已确认输入与页面结构

权威输入按顺序为共享设计第2、3、9节，T10设计/实现/验收，现有AppLayout/router/DownloadView/PageHeading/style。T10已通过专项66项、完整前端663项和构建，独立审查通过；直接依赖无未解决冲突。

保持顶部导航与现有 `--tensor-*` 色彩、字体、控件、边框和焦点样式。导航顺序为数据下载、数据查看、数据完整性、外观设置；数据完整性图标采用既有Element Plus图标库的 `CircleCheck`，详情路由也标记该入口 `aria-current=page`，面包屑显示数据完整性。

`IntegrityView.vue` 为正常页面，不加入下载/数据查看KeepAlive。PageHeading标题“数据完整性”，说明“检查本地数据，查看缺失与无法判定的原因”。下方按任务顺序分三块：创建检查（来源/股票/日期/接口）、检查口径预览与提交、检查历史。宽屏表单两列，股票和接口列表跨列；1024保持可读列宽，680及以下全部一列。组件最小宽度为0，长说明换行；仅历史表自身容器可横向滚动，390px整页不得横溢。

### 2. 来源和接口元数据

页面使用 `listDataSources()` 读取现有来源元数据。来源为空显示空态，不发能力查询；只有一个来源时自动选中，否则保持未选。选项只按 `enabled` 禁用；禁止直接复用按downloadAvailable禁用选项的 `DataSourceSelect.vue`。选择来源后调用 `flow.loadCapabilities(pluginId)`，并读取 `listApis(pluginId)` 获取分类。后者仅提供 `category`，接口集合、显示名、描述及下载限制以能力响应的 `apis` 为准；分类缺失时归入“未分类”，分类请求失败显示可重试说明但不能删除能力中的接口。

本地是否可检查只依赖 `localCheckAvailable`。不可用时显示 `unavailableReason`，保留表单并禁止提交；缺Token但localCheckAvailable=true时显示“下载不可用，本地检查可用”，可正常检查。能力请求失败保留安全错误和requestId，提供“重新获取”；不发送POST。来源/分类请求使用generation隔离，卸载后不写状态；不改既有元数据API方法签名。

首次选中来源且成功加载能力时，按能力列表顺序默认勾选全部API。提供分类标题、逐项复选框、全选/清空及选择数量；清空不能提交。每项显示displayName和apiName，可展开日期轴/限制。描述为null的API保留并标注“覆盖规则未实现”；NON_STOCK保留“不适用”说明，不能从选项列表剔除。

### 3. 股票、日期和计划范围

表单数据仅为pluginId、symbols、startDate、endDate和选中apiNames；submissionId由T10生成。股票输入支持逐个“添加”及逗号（含中文逗号）、空白、换行粘贴，按首次出现顺序去重，显示可删除条目与总数。不请求本地股票清单，不因本地无记录剔除代码。Tushare按既定合同trim/大写后以六位数字加SH/SZ/BJ校验；其他插件只做非空与去重，插件格式错误显示后端fieldErrors，不发明通用股票格式。

开始/结束日期初始为空，使用有label的date输入，保持ISO字符串，年份1000..9999且是真实日期，双边必填并有序。天数按UTC日期差加1计算。Tushare结束日不得超过 `Intl.DateTimeFormat` 的Asia/Shanghai当日；不把浏览器本地日期当上海日期。不得自动收缩日期范围。

按能力limits校验股票数、包含首尾的日期天数和计划单元数：选中API的descriptor.scopeKind=NON_STOCK计1，其余（含descriptor=null）每只股票计1。等于上限允许、超过禁止提交并指出对应限额。空股票、空接口、未选来源、能力加载/失败/不可用均禁提交。原API顺序与用户股票顺序保留；发送当前选择的显式apiNames数组，全部选择也固定为该数组。

纯表单解析和校验放在 `utils/integrityForm.js`，导出 `parseIntegritySymbols(text, pluginId)`、`validateIntegritySelection(selection, capability, today)`；后者返回 `{ valid, errors, plannedUnits, rangeDays }`，errors为字段到中文消息的对象，today由调用方传入，便于时区边界测试。它只校验/计算，不生成ID、不写缓存、不改变传入selection。表单组件通过v-model对象更新，不改写T10冻结快照。

### 4. 口径预览与提交

预览显示股票数、接口数、计划单元数和完整日期范围；逐接口显示dateLabel/dateField、scopeKind、limitations、依赖datasetKey及purpose。STOCK_SNAPSHOT明确写“当前快照不能证明所选历史窗口完整”；NON_STOCK写“不按股票逐只检查”；descriptor=null写“缺少覆盖规则，结论可能无法判定”。仅展示保存/当前描述提供的信息，不根据下载queryMode猜测日期轴，不显示跨接口总完整率。

预览之后提供勾选项“我已确认以上检查口径”，对应 `flow.confirmCapability(currentHash)`。来源或hash变化时清除界面确认，用户须重新勾选。已有hash变更后保留原股票/日期及API选择；已移除API显示失效选项并阻止新提交，要求用户明确移除或重新选择，不静默丢弃范围。新出现API不自动加入旧选择。

首次点击“开始检查”时提交未添加的股票文本，再执行表单校验、确认当前hash、`prepareSubmission(selection)`、`submit()`。submitting/recovering/uncertain期间锁定输入及普通提交；双击由T10合并。prepared状态因存储失败重试时只调submit，不能重新prepare生成ID。以 `receipt.checkId` 或 `recoveredTask.checkId` 导航到详情，并只导航一次。

错误和恢复交互完全沿用T10：

- 显示submissionError及requestId，recoveryError与storageError分开显示，不让存储错误覆盖原POST请求ID。
- uncertain时提供“查询提交结果”，只调用recoverSubmission；canResend=true才提供“使用原请求重发”，调用resendSubmission，始终保留原快照/ID。恢复失败继续禁止重发。
- 页面挂载发现pendingSubmission时先展示原范围，自动调用一次recoverSubmission（GET），不自动POST；恢复期间锁定新检查。损坏缓存显式报错，新检查需用户明确操作，不能悄悄生成替代ID。
- rejected显示后端fieldErrors/明确拒绝；用户修改范围并点击新的“开始检查”才prepare新请求。SUBMISSION_CONFLICT不自动换ID。
- definition-changed展示“检查口径已更新，请重新确认”，能力由T10刷新，旧请求仍保留；用户确认新hash后明确提交才产生新ID，不改写旧快照重发。

页卸载调用dispose（T10作用域清理也是幂等保护）；路由离开不发送恢复POST。不创建全局跨标签广播或自动重试循环。

### 5. 历史与报告入口

`IntegrityCheckHistory.vue` 自行维护只读服务端页快照，默认page=1/pageSize=20；筛选为pluginId、任务执行status，清空筛选重回第1页。使用五个任务枚举，pageSize提供20/50/100供UI选择（API仍支持1..100）。切页、筛选和手动“刷新历史”仅GET；generation与AbortController拒绝旧请求，失败保留同criteria数据和requestId，显式重试。首屏空、筛选空、初始加载与失败各有可读文字，状态用文字加图标。

列固定为来源、股票摘要（前3个及总数，完整列表可展开）、起止范围、接口数、创建时间、执行状态、结论入口。时间复用现有显示工具并标明时区，原值不改写。total是BigInt，历史组件用上一页/下一页按钮、20/50/100下拉和当前页/总页文字；沿用现有分页样式，不直接传入仅接收Number的DatasetPagination。总页数用 `(total + BigInt(pageSize) - 1n) / BigInt(pageSize)` 计算，空页总页显示0；禁止先Number(total)，下一页条件为 `BigInt(page) * BigInt(pageSize) < total && page < 2147483647`，上一页条件为page>1，加载时禁用翻页。

T09历史TaskSummary没有overallStatus：结论列显示链接“查看报告结论”，不伪造PASS/UNKNOWN、不从COMPLETED推导通过，也不为每行隐式查询详情。上层接受新任务后直接导航，不在旧历史页手工插入记录；回到页面重新GET历史。

路由：`/integrity` name=`integrity`，`/integrity/checks/:checkId` name=`integrity-check`。T11新增 `IntegrityCheckView.vue` 最小入口，仅使用PageHeading“检查报告”、经过UUID验证的检查编号和“返回数据完整性”链接；非法ID显示明确错误且不发请求。该入口不展示占位结论/百分比，不出现开发进度文案。T12直接扩展此文件实现独立加载报告，T11验收只断言正确URL/编号/返回导航。

### 6. 组件职责和可访问性

`IntegrityView.vue`拥有来源加载、T10实例、表单选择和导航；`IntegrityCheckForm.vue`拥有输入/勾选控件并发出selection更新；`IntegrityScopePreview.vue`为只读口径和确认区；`IntegrityCheckHistory.vue`拥有历史GET生命周期。视图不将冻结API对象作为v-model；每个输入/错误有稳定id和label/aria-describedby，分类用fieldset/legend，多选用原生checkbox，提交按钮有busy语义，确认/错误区域有适当status/alert。请求ID和长接口/股票名可换行；键盘可到达添加、移除、全选、清空、确认、提交、恢复、历史链接和分页。

## Files

- 新增 `control-plane/src/views/IntegrityView.vue`、`IntegrityCheckView.vue`及对应spec：页面装配、明确报告入口。
- 新增 `control-plane/src/components/integrity/IntegrityCheckForm.vue`、`IntegrityScopePreview.vue`、`IntegrityCheckHistory.vue`及对应spec：表单、只读口径、历史请求/分页。
- 新增 `control-plane/src/utils/integrityForm.js`及spec：股票解析、日期/限额/计划数纯校验。
- 修改 `control-plane/src/router/index.js`、`layouts/AppLayout.vue`及现有相关路由/布局测试：新增两路由与一个导航项。组件使用scoped样式；仅确有共用需求时补充style.css，避免改写既有下载布局。
- 新增 `control-plane/e2e/integrity-checks.spec.js` 的创建/历史stub场景，预留同一文件由T12/T13扩展；新增 `docs/verification/DATA-INTEGRITY-T11.md`记录实际证据。

## Tests

首个RED：用utils测试证明本地不存在但格式合法的Tushare股票仍被保留、大小写重复只计一次；限额恰好相等允许，超过禁止；代码尚未实现时断言失败，而非导入失败。

专项覆盖：来源空/失败、缺Token仍可检查、能力不可用、分类缺失不漏API、默认全选/清空、粘贴/去重、反向/无效日期与上海跨日边界、NON_STOCK/空描述的计划数、hash变化不缩小原范围、确认门禁、冻结后表单修改、双击与响应丢失查回、空恢复同ID显式重发、缓存/存储错误、历史筛选/分页/迟到结果、TaskSummary不生成结论或N+1、接受/历史跳转正确URL、非法详情ID不请求。

Node24.15.0下从隔离区根目录执行：

```sh
npm --prefix control-plane test -- src/utils/integrityForm.spec.js src/components/integrity src/views/IntegrityView.spec.js src/views/IntegrityCheckView.spec.js src/layouts/AppLayout.spec.js
npm --prefix control-plane test
npm --prefix control-plane run build
```

stub浏览器验证启动独立Vite端口，使用现有Playwright配置：

```sh
npm --prefix control-plane run dev -- --host 127.0.0.1 --port 4178
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4178 npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js
```

使用T09合同示例生成完整响应，按实际请求回传X-Request-Id及回执requestId/Location；不能绕过T10严格DTO。验证1440/1024/390布局、键盘完整提交/恢复操作、长名称/多股票/长错误、空历史和接口NONE/UNKNOWN说明；截图与滚动宽度检查结果记录在验收文档。预期上述命令全部通过、无失败/跳过、构建成功；stub结果不得充当真实后端闭环。

## Acceptance

用户可选择启用来源和完整股票/日期/接口范围，理解并确认口径后只创建一次检查；无Token仍可本地检查，响应不确定时保持原请求可找回。历史分页和错误恢复遵循API，执行状态与报告结论不混淆，入口URL和导航可用。桌面/窄屏及键盘可操作无页面横溢，新增文件加入Git，实际专项/回归/构建/stub结果回填。

## Risks

历史摘要缺结论是已验收API边界；“查看报告结论”明确指向T12详情，不能用N+1或假UNKNOWN补齐。现有下载来源选择器有下载可用性门禁，必须使用本地检查门禁。分类元数据不是能力全集，合并时不能漏掉API。T11报告入口只承担路由契约，详情行为须由T12验收。无未解决的直接依赖冲突或材料缺口；本设计不授权自动开始T11。
