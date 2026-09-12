# ISSUE-018-T11：任务详情、轮询与手动操作

## Goal

用户可直接打开 `/downloads/tasks/:taskId`，刷新后查询同一持久化任务，查看批次结果并手动重试或恢复。后台状态、计数和操作许可均来自服务端；查询失败不冒充任务失败，控制回执不冒充下载成功。

任务身份与范围来自 [ISSUE-018 看板 T11](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t11)，Order 11，直接依赖 [T09](ISSUE-018-T09-design.md) 和 [T10](ISSUE-018-T10-design.md)。T10 已先记录 COMPLETED：完整专项 206 项、全前端 340 项及生产构建通过，独立审查关闭，受控生产页面桌面/窄屏确认通过。本文是其后继设计，不是 T11 实施证据。

## Scope

扩展任务 API/DTO，新增 useDownloadTask、DownloadTaskView 和批次表组件；接入详情路由、列表/接收面板链接和返回下载页导航。实现详情与批次分页查询、运行时轮询、可见性暂停、查询退避、旧响应隔离、版本控制的 retry/resume，以及本项组件/状态/受控浏览器交互验证。

复用现有 WorkbenchPanel、PageHeading、AsyncStatePanel、Element Plus 与 scoped 样式，不更换视觉系统。批次表默认展示全部叶子，不新增筛选器、父子树、取消、自动重试、删除、下载历史导出或任务编辑。旧同步 API、DownloadResult、T10 提交恢复及生产能力门禁保留。T12 负责普通 e2e 全面迁移、真实后端生命周期与发布门禁；T13 负责真实来源完整性。本文不改后端、数据库、依赖版本或全局 HTTP。

## Approach

### 已有输入与职责

外部合同以 `docs/contracts/download-task.schema.json` 为完整字段来源，以 T09 专属设计和实际 DownloadTaskController 为 HTTP 行为来源；总体设计 §3.7、§3.10–3.12 为状态/用户语义来源。T10 已实现 `parseTaskJson`、Task/Receipt/Page/Capabilities 只读 DTO，以及使用原始 JSON text 的任务 Axios 请求。不可另建一套 Task 解析或将 bigint 退回 Number。

`useDownloadTask` 负责一个路由 taskId 的查询和控制生命周期。DownloadTaskView 只呈现与接线；DownloadBatchTable 只呈现批次及发出翻页/刷新事件。不在组件中再建定时器，不用元数据、sessionStorage 或 T10 内存 receipt 作为详情的前提。

### API 与无损控制请求

在 `control-plane/src/api/downloadTasks.js` 增加四个入口，保留现有三个接口：

```js
getDownloadTask(taskId, { signal } = {})                 // -> Task
listDownloadTaskBatches(taskId, criteria = {}, { signal } = {}) // -> BatchPage
retryDownloadTask(taskId, expectedVersion, { signal } = {})   // -> Receipt
resumeDownloadTask(taskId, expectedVersion, { signal } = {})  // -> Receipt
```

taskId 使用 T09 的完整 UUID 正则，大小写均接受，构造路径时规范为小写；非法输入在发送前抛 TypeError。详情 GET `/download-tasks/{taskId}` 不带 query。批次 GET `/download-tasks/{taskId}/batches`：criteria 仅接受 page/pageSize，默认 1/20，page 1..2147483647，pageSize 20/50/100；拒绝 null/数组/未知键/非法值，显式发送 `includeSplit=false`，不发送 status。用 URLSearchParams 避免重复/数组参数；T11 只消费默认叶子视图，后端其余筛选能力保留。

所有 GET 只接受 200；延续 taskOptions 的原始 text/parseTaskJson 和请求 ID 头校验。详情 Task.taskId 须与路径 UUID 等价，批次响应 page/pageSize 须与请求相同；非法返回抛现有 ClientError('INVALID_RESPONSE', requestId)，不能把错任务展示为当前任务。

retry/resume 分别 POST `/download-tasks/{taskId}/retry`、`/resume`，expectedVersion **只接受 bigint**，范围 1n..9223372036854775807n；Number/string/null/小数/越界等均零 HTTP 并抛 TypeError。以已校验 bigint 的十进制文本构造唯一字段 body：

```js
const body = `{"expectedVersion":${expectedVersion.toString()}}`
// http.post(path, body, {...taskOptions(signal),
//   headers: { 'Content-Type': 'application/json' }})
```

不得 JSON.stringify(bigint)、Number(version)、把版本加引号或先重新 GET 再偷偷替换点击时版本。真实 Axios adapter 测试必须断言最终 wire body 为 `{"expectedVersion":9007199254740993}`，最大 int64 同样保真且 Content-Type 为 application/json。

控制成功只接受 202，复用 parseDownloadTaskReceipt；核对 requestId、同一 taskId 和 `Location: /api/v1/download-tasks/{taskId}`。控制回执允许任一合法任务状态，不能复用首次提交“202必须QUEUED”的限制：worker 可在用例回读前推进状态。Receipt 只证明请求已接收，最终页面状态由后续 GET 提供。HTTP 错误继续由 http.js/errors.js 校验，不改变错误分类或 requestId 规则。现有 ApiError 不保存 HTTP status，状态分支必须按其已验证的 code 与 T09 固定映射判断，不能读取不存在的 error.status；ClientError 按 kind 判断。

### Batch DTO

在 `downloadTaskDtos.js` 增加 `parseDownloadBatch(value, requestId)` 与 `parseDownloadBatchPage(value, requestId)`，复用文件内已有 object/键集/UUID/参数/时间/整数/StoredError 校验函数，返回冻结白名单对象。不得从服务端回传对象直接透传额外字段。

| 字段 | 固定合同 |
| --- | --- |
| batchId / parentBatchId | UUID；parentBatchId 可为 null |
| batchKey | `^[0-9]{6}(?:/[01])*$`，最长128字符；保留服务端顺序 |
| rangeStart / rangeEnd | 均 null，或均真实 `YYYY-MM-DD` 日期且 start≤end；不当作 UTC 时间转时区 |
| sourceParams | 规范标识键、string 值的业务参数快照；按 T10 相同规则冻结 |
| status | PENDING / RUNNING / SUCCEEDED / FAILED / SPLIT |
| attemptCount | Number，整数 0..2147483647；不是 int64，原数值词法仍经过 parseTaskJson |
| sourceRows / insertedRows / updatedRows | bigint，0..9223372036854775807n |
| error | null 或严格 `{code,message}` StoredError；不增 retryable |
| createdAt / updatedAt | 必填 UTC ISO Instant；沿用 T10 时间校验 |
| startedAt / finishedAt | 显式 null 或 UTC ISO Instant |

全部键 required、禁止未知字段；日期/时间不能只做正则检查。BatchPage 为 `{page,pageSize,total,items}`，total bigint，items≤pageSize，batchId 无重复，沿用 TaskPage 的空页约束与 int32 分页范围。SPLIT 在纯 Batch parser 中合法，但本项叶子查询若返回 SPLIT 行则 INVALID_RESPONSE。不能要求一个批次页的行计数等于详情 counts；它们是独立请求/快照，后台可能在两次读取间推进或拆分。

所有 number token 必须沿用 JSON.parse context.source 的整数词法检查；wire string、小数（含舍入后看似整数）、溢出和缺 source 明确拒绝。计数展示使用 toString，不能序列化整个 Task/Batch 或重新解析其数字字符串。旧浏览器提示沿用 T10 的“当前浏览器无法保真处理任务数值，请更新浏览器后重试”。

### 查询状态与单一调度器

`useDownloadTask()` 返回下列 refs/computed 与方法，创建时不隐式发请求：

```js
// refs
taskId, task, batches, page, pageSize,
loading, taskError, batchesError, taskUpdatedAt, batchesUpdatedAt,
operation, operationError, operationMessage
// computed
notFound, invalidTaskId, canRetry, canResume
// methods
load(taskId), refresh(), changePage(page), changePageSize(pageSize),
retry(), resume(), dispose()
```

初始 task/batches/error/time 为 null，page=1/pageSize=20，operation=null；操作中取 'retry' 或 'resume'。load 规范化 UUID，相同 ID 只合并一次刷新意图；不同 ID 递增 generation，清除旧 task/批次/时间/错误/控制提示和旧 timer，失败退避计数归零，重置1/20，并立即安排新查询（保留旧请求在途标记至其结算）。非法 ID 显示“任务地址无效”且零 HTTP。正常 UUID 的详情 TASK_NOT_FOUND/404 显示“任务不存在”，清除旧任务/批次，停止自动轮询，保留手动刷新和返回下载页。

使用一个 setTimeout 和一个在途 cycle 标记；cycle 对同一 taskId 的详情 GET 与当前页批次 GET 使用 Promise.allSettled。两种端点可并行，但每种最多一个未结算请求，整个 cycle 结算前不启动下一轮。两个成功结果独立更新各自快照和更新时间，某端点失败只写对应查询错误，保留该端点同页/同任务的上次成功数据。详情404优先于本轮批次成功，不留下孤立批次。没有成功详情时显示加载或失败面板；仍成功返回的批次可暂存，详情恢复后才呈现。

每次请求捕获 generation、taskId、page/pageSize。翻页/换页长递增 generation，清除旧页批次及其时间/错误，保留同 task 的详情；换页长回第1页。在途 cycle 未结算时仅合并最新意图，旧成功、失败、finally 都不能污染当前可见状态或启动旧意图定时器。旧 cycle 的结算只释放其在途标记，然后由当前状态调度最新意图；切换不能靠将标记置空来制造并发。定时/手动刷新/恢复可见/控制后刷新都走同一调度入口。

| 当前最新 cycle 结果 | 下一步 |
| --- | --- |
| 两个 GET 成功且 Task 为 QUEUED/RUNNING | cycle 完成后2000ms再次查询详情和当前批次页 |
| 两个 GET 成功且为 SUCCEEDED/PARTIAL_FAILED/FAILED/INTERRUPTED | 停止自动轮询，仍可手动刷新/翻页/恢复可见时查询 |
| 任一查询临时失败或 INVALID_RESPONSE | 显示“状态暂时无法更新”及各自上次更新时间；连续失败按5000/10000/30000ms，之后保持30000ms；下一次两者成功归零 |
| 详情 TASK_NOT_FOUND 或非法 URL | 停止自动轮询；用户刷新/换ID才重新尝试（非法ID仍不请求） |
| document.visibilityState 为 hidden | 清 timer、递增 generation，使在途结果失效，保留在途标记至真正 settle；不自动 GET/POST |
| 恢复 visible | 即使上次为终态也立即查询一次；尚有在途 cycle/控制则合并一个刷新意图 |
| dispose | 清 timer/listener、递增 generation；方法此后不发请求，迟到响应/控制回执不写状态，不取消后台任务 |

注册 visibilitychange 一次，dispose 注销。路由切换中仍在途的控制 POST 同样占用调度槽，结算后才能发新 task 查询；不会把旧 task 的控制结果写到新 task。失败 GET 的 HTTP/客户端错误和 task.lastError、Batch.error 分开存储和展示，绝不将查询错误改写为 Task.status='FAILED'。

### 手动 retry / resume

按钮分别由详情中的 canRetry/canResume 决定是否出现，不通过 Task.status 或 ErrorCode.retryable 自行推断许可。computed canRetry/canResume 还要求当前详情存在、taskError为空、operation为空、未 dispose 且页面可见；详情查询失败时已有按钮保留但禁用，直到新的详情查询成功。批次查询失败不阻止已有有效详情发控制。普通轮询中的按钮不因 loading 闪烁。

点击首先同步设置 operation 防重入，捕获此刻 taskId 和原 bigint version，清 timer，递增 generation 使旧查询失效。若已有 cycle，等待其真正结算后执行该次控制，期间禁用两个控制按钮；不得再读一次较新 version 替换捕获值。等待时若已离开/换 taskId/隐藏且 POST 尚未发送则放弃本次未发出的操作、释放锁；已经发出的 POST 不能当作被取消的后台操作。详情控件刷新意图等待 operation 结束后统一执行。

| 控制结果 | UI 与后续动作 |
| --- | --- |
| 合法202 | 显示“重试请求已接收”或“恢复请求已接收”；释放 operation，立即GET同task和当前批次页；不把 Receipt 拼成 Task、不清零计数、不用其状态宣称成功 |
| 409 | 保留安全错误信息；TASK_STATE_CONFLICT 显示“任务状态已变化，已重新查询”，其他409同时显示具体固定原因（如定义变化）；立即GET刷新，禁止自动重发POST |
| 网络/超时/5xx/无效回执 | 显示“操作结果尚未确认，正在重新查询任务”；保留原task快照并立即GET找回，不生成submissionId、不创建新任务、不自动重放控制 |
| 400/404/429 等其他合法拒绝 | 显示安全错误/requestId并刷新一次事实；429不改原任务状态，404由随后的GET进入不存在状态 |

所有结果均先使旧控制许可失效，直到操作后的新详情GET成功；可用独立布尔标记而非伪造taskError实现。刷新失败按上述5/10/30秒退避；即使之前是终态也不能因旧快照提前停止本次确认。确认成功后按新Task状态恢复2秒或停止；失败批次错误的 retryable 不决定按钮。页面在POST过程中隐藏时不在隐藏状态发确认GET，恢复可见后立即找回；路由刷新/关闭重开直接从URL查询，无控制记录存储需求。

### 页面、批次与路由

新增 `/downloads/tasks/:taskId`，name='download-task'，懒加载 DownloadTaskView；保持 existing catch-all。View 以 immediate watch 监听 route.params.taskId 并调用 load，unmount dispose；不要只 onMounted 取一次ID。AppLayout 现有组件 key 为路由 name，同名详情ID切换会复用组件，必须靠上述 watch 隔离；详情不加入 KeepAlive，保留 DownloadView/DatasetView 的既有两项缓存。

AppLayout routeLabels 增加“任务详情”，详情页的“数据下载”导航保持 active 视觉。把 DownloadView 接收面板与 DownloadTaskList 的查看任务链接改为 RouterLink（URL不变，保留原生打开新标签页能力），详情页提供“返回下载页”RouterLink。正常来回导航保留下载配置；T10 的 onActivated/onDeactivated + taskList.setActive 会暂停离屏列表并在返回时补查。本项不改 T10 提交恢复逻辑，不把详情页进入当作表单 dispose。

页面沿用 PageHeading“任务详情”，上方返回入口与“刷新状态”按钮，主体为任务概览与全宽批次表。原生语义 table、列标题、可访问名称与现有token保持一致；窄屏表格局部横向滚动，长UUID/业务参数可断行，页面自身无横向溢出。状态文字不能只靠颜色，自动查询不重建整页或移动焦点，只有操作/错误变化使用 polite/alert，不能每2秒播报整表。

概览展示 taskId、pluginId/apiName、模式、按键排序的全部规范化params、状态/版本、createdAt/updatedAt/queuedAt和可空startedAt/finishedAt/deadlineAt。时间以浏览器时区显示，ISO保留在time datetime/title，null显示“尚无”。不请求当前能力来解释历史日期轴；业务参数名称和值保留事实。

计数展示“已结束 succeeded+failed / 当前计划 total 批”，分列成功/失败/待执行/运行中及独立拆分数；planReady=false显示“尚未生成计划”，0批不自行判成功。sourceRows、新增记录次数、更新记录次数、累计/本轮请求次数完整显示bigint，说明“新增/更新为已提交写入操作次数，不是整段去重总量”。无固定百分比。

SINGLE说明“单次请求，结果不代表完整历史”；SUCCEEDED时可称“本次请求已完成”。RANGE成功说明“本次请求范围内的计划已完成”，不能宣称全部历史或跨批同一时刻快照。FAILED/PARTIAL_FAILED突出失败数、待执行数与查看批次；INTERRUPTED说明需手动恢复；lastError仅作为任务停止/规划原因显示，不能替代批次明细。按钮说明：重试处理失败及未完成工作，保留成功结果；恢复继续中断工作，普通失败可能仍需之后重试。

`DownloadBatchTable` props 为 `{page,pageSize,result,loading,error,lastUpdatedAt}`，事件 `refresh`、`update:page`、`update:pageSize`。列为批次key/ID、区间、sourceParams、状态、attemptCount、三类计数、错误原因、更新时间/开始/结束时间。parentBatchId有值时显示父ID用于定位，但默认不请求SPLIT父行。null区间标记“单次请求”；范围按日期原样展示。PENDING标“待执行”，FAILED标“失败”并突出区间与固定错误文案；attemptCount=0明确尚未尝试，不能因旧attemptCount>0把PENDING伪装本轮已执行。

分页仅使用服务端page/pageSize/total，沿用T10 bigint计算、2147483647页上限和真实ElPagination自动折页防护；不为此抽取全站分页框架。total=0显示0页并禁用前后页；超尾空页显示“本页暂无批次”并可手动返回第一页，total变小（含零）不触发自动GET另一页。批次查询失败同页保留旧结果，换页不保留上一页行。表下明确“批次按服务端计划排序，拆分可能改变总批数”。

## Files

以下是 T11 实施落点；本次后继准备只创建本文与交接。

| 文件 | 责任 |
| --- | --- |
| `control-plane/src/api/downloadTasks.js`、`.spec.js` | 四个新API、UUID/分页验证、严格控制number token、HTTP身份与回执验证 |
| `control-plane/src/api/downloadTaskDtos.js`、`.spec.js` | Batch/BatchPage解析与完整字段/数值/日期回归，复用Task/Receipt |
| `control-plane/src/composables/useDownloadTask.js`、`.spec.js` | 新增查询/控制调度、可见性/退避/旧响应隔离与版本捕获 |
| `control-plane/src/components/download/DownloadBatchTable.vue`、`.spec.js` | 新增批次表、精度/错误/区间/真实分页与空页 |
| `control-plane/src/views/DownloadTaskView.vue`、`.spec.js` | 新增详情展示、route watch、独立控制按钮和生命周期接线 |
| `control-plane/src/router/index.js`、`index.spec.js` | 注册详情route并验证直接地址/参数/兜底 |
| `control-plane/src/layouts/AppLayout.vue`、`.spec.js` | 详情导航标签/active，真实来回导航缓存与同名详情ID切换回归 |
| `control-plane/src/views/DownloadView.vue`、`.spec.js`；`control-plane/src/components/download/DownloadTaskList.vue`、`.spec.js` | 查看任务链接改RouterLink，保留href/新标签能力与T10全部状态行为 |
| `control-plane/e2e/download-tasks.fixtures.js`、`download-tasks.spec.js` | 新增本项受控页面闭环；原始JSON数字、跨页面持久夹具、控制并发记录与截图 |

不改 `http.js`、`errors.js`、旧同步接口、生产YAML、Maven、迁移、普通e2e夹具和Playwright排除配置。已有 `ui-redesign.fixtures.js` 等仍有同步页面断言，统一迁移由T12承担；不将其未运行记录为通过。

## Tests

第一项实施动作：扩展 `downloadTasks.spec.js`，真实http adapter返回原始text回执，调用尚不存在的 `retryDownloadTask(taskId, 9007199254740993n)`，断言最终JSON body精确、Content-Type、202/Location/同taskId与bigint回执；观察缺入口RED后实现最小控制传输，再补详情/批次及调度。禁止先改共享http或先写完页面再补测试。

| 场景 | 必须观察的结果 |
| --- | --- |
| API/DTO | 四路径、GET200/控制202、请求ID/Location/错任务拒绝；原始JSON严格字段/枚举/null/真实日期/范围配对，批次页响应一致/ID去重/叶子约束；T10三API和旧同步API通过 |
| 数值 | 9007199254740991/2/3、Long.MAX_VALUE无损收发；控制只接受bigint且wire为number token；wire string/小数/指数/越界/缺source拒绝，attemptCount仅int32 |
| 查询调度 | fake timers验证2秒、终态停止、5/10/30退避/归零、隐藏暂停/可见立即查；详情成功+批次失败独立保留，查询失败不改变task状态 |
| 竞态 | deferred控制在途请求，手动/翻页/换页长/ID切换只合并最新意图；旧成功/错误/finally不污染，不出现第二个同端点并发；dispose后零timer/listener/请求 |
| 控制 | canRetry/canResume独立、不看retryable；双击/两个按钮只一次POST；等待在途GET也发送点击时原bigint版本；202/409/响应丢失只刷新、不重放POST；确认失败退避，隐藏/ID切换/卸载隔离 |
| 详情/表格 | SINGLE与RANGE文案、未规划/零批/动态拆分、失败与PENDING区间、尝试次数、完整整数/时间；首次失败/404/非法URL/同页旧快照；total收缩至3/0保留超尾页，真实翻页和页长操作有效 |
| 路由与缓存 | 列表/接收链接进入真实详情；URL刷新不依赖receipt、sessionStorage、元数据；详情A→B同名路由旧响应丢弃；返回下载页保留表单，离屏列表不轮询、返回补查 |

浏览器用新 `download-tasks.fixtures.js` 在 BrowserContext 上拦截API，状态存测试端并跨同context的新Page保留，不存在于页面内存。参考T09 schema/examples，所有返回传原始JSON text，显式替换/拼接已验证的int64字面量，禁止先Number舍入；动态匹配请求ID，未知路由记录并失败。覆盖以下三个有界场景，不使用长sleep：

1. 受控AVAILABLE的RANGE提交→仅接收→查看详情→刷新同URL→关闭Page新开Page，从下载页近期列表进入同task；断言仅一个提交POST、参数/日期轴正确、taskId保持。关闭再开证明服务端列表入口交互，不证明真实后台独立执行。
2. 三叶子2成功/1失败，显示失败区间和attemptCount；双击重试只一个原version POST；夹具推进RUNNING→SUCCEEDED，成功批次计数不重复、失败批次attemptCount从1变2；附大整数version/计数精确断言。
3. INTERRUPTED只出现恢复，409后显示冲突并GET，用户重新操作使用刷新后的版本；模拟查询500恢复后任务状态仍正确；在390px检查按钮/局部滚动/无页面溢出，1440px核对详情和批次布局。pageerror应为空，失败需定位，不能过滤掉实际应用异常。

Node要求 `>=24.15.0 <25`，当前构建内Node为 `data-plane/tensor-app/target/frontend/node/node`、npm11.12.1。仓库根按顺序运行（每条npm前可加 `env PATH="$PWD/data-plane/tensor-app/target/frontend/node:$PATH"`）：

```sh
npm --prefix control-plane test -- src/api/downloadTasks.spec.js src/api/downloadTaskDtos.spec.js src/api/api.spec.js src/composables/useDownloadTask.spec.js src/components/download/DownloadBatchTable.spec.js src/views/DownloadTaskView.spec.js src/router/index.spec.js src/layouts/AppLayout.spec.js src/views/DownloadView.spec.js src/components/download/DownloadTaskList.spec.js
npm --prefix control-plane test
npm --prefix control-plane run build
# 独立终端启动本次构建；仅绑定回环地址，端口占用时选择空闲端口并记录实际URL
npm --prefix control-plane run preview -- --host 127.0.0.1 --port 4173 --strictPort
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173 npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js
git diff --check
git diff --cached --check
```

预期专项、全量、build和本项Chromium spec均退出0，新用例实际执行、无失败/未解释跳过；前端当前31文件/340项是输入基线，最终数量以实际报告为准。用fake timers/deferred/jsdom visibility与真实组件/router，不用纯stub证明分页及KeepAlive。生产浏览器截图桌面/窄屏一次成组检查，问题集中修复后确认；完成后仅停止自己启动的预览服务。记录实际命令/数量/日志和独立代码审查结论，再完成T11并准备T12；不因本项前端改动重复T09 Maven或宣称真实生命周期已通过。

## Acceptance

1. 同taskId直接地址、刷新、关闭重开后的列表入口均可查询详情；不依赖当前插件或提交内存，返回导航保留下载配置。
2. 详情与叶子批次分页如实显示，日期、动态批数、完整写入次数、失败/未执行区间与尝试次数正确；无固定百分比或完整历史误报。
3. 运行2秒、终态停止、隐藏暂停/恢复补查、失败5/10/30秒退避和单一在途调度成立，旧ID/页/卸载响应不会污染，查询失败不改任务状态。
4. retry/resume依服务端许可独立可操作，发送原bigint版本的JSON数值；重复操作互斥，202恢复查询、409刷新提示、未知回执只查不重发，成功数据不由页面清零。
5. DTO/真实Axios/组件/状态/本项受控浏览器测试及生产构建有通过证据，兼容T10和旧同步API；后端门禁、生产能力与T12/T13证据边界保持。

## Risks

- controls是瞬时提示，操作仍可能被当前定义、插件、队列容量或活动worker拒绝；必须保留固定错误原因与刷新，不自动纠正参数或重试。
- Task与BatchPage分别查询，不能保证跨请求同一时刻；页面不能自行用当前页批次数重算全任务计数。
- 缺JSON.parse source的浏览器不能保真处理任务数值；T10的明确失败边界保持，无有损回退。
- AppLayout保留DownloadView的KeepAlive，详情同name切换复用实例；必须同时测试下载页停轮询和详情route watch，不能以单独mount代替实际路由证据。
- 34项生产RANGE仍NEEDS_VERIFICATION。受控AVAILABLE/页面生命周期只证明交互；真实后台/进程重建、普通e2e全面迁移和真实完整性分别由T12/T13承担。
- 无待补充的实质需求；本设计固定T11实施行为，不改变看板顺序或后端合同。
