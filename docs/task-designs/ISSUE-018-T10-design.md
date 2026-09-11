# ISSUE-018-T10：前端模式表单、任务提交与近期列表

## Goal

下载页根据能力选择 SINGLE / RANGE，提交后显示持久化任务身份，并通过服务端近期列表找回任务。响应不明和刷新均沿用原 submissionId，接收回执不再被显示为下载成功。

任务身份来自 [ISSUE-018 看板 T10](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t10)，Order 10，直接依赖已完成的 [T09](ISSUE-018-T09-design.md)。共享依据为[总体设计](ISSUE-018-design.md) §3.3、§3.11–3.12、§5.1–5.2。T09 已记录专项 602、全单元 1019、生产包 1023、验收包 1026 项通过，各生命周期前端 170 项及构建通过，独立审查问题全部关闭；这些是 HTTP 输入就绪证据，不是本项页面的验证结果。

## Scope

新增任务 API、独立 DTO 校验和近期任务列表；改造 useDownloadFlow、DownloadView，复用动态参数表单、数据源/接口选择与现有样式。实现能力加载、模式与参数切换、非敏感不可变提交快照、sessionStorage 恢复、服务端分页、可见性刷新、查询失败提示和详情入口。

T11 实现 `/downloads/tasks/:taskId` 的详情路由、页面、批次查询和 retry/resume；本项提供该路径的链接和可复用 Task DTO，不创建详情占位页或控制按钮。T12 更新普通浏览器 fixtures/断言、完成浏览器/进程/发布门禁。保留 `downloadDataset` 及其 `/downloads` 兼容测试、既有 DownloadResult 组件及测试；不再从 DownloadView 调用旧同步下载。保留现有视觉系统、40 项元数据及 34 项生产 RANGE NEEDS_VERIFICATION 门禁；受控 AVAILABLE 仅用于前端测试。不改后端、迁移、生产能力、依赖版本或全局 HTTP 精度规则。

## Approach

### 现有代码与职责

已检查 `api/http.js`：全站共享 Axios 实例，超时 130000ms，请求拦截器生成 X-Request-Id，错误拦截器用 errors.js 严格核对错误形状/状态/头。现有 downloads.js 直接返回同步结果；useDownloadFlow 把 outcome 转为 SUCCESS/EMPTY，并在 retry 中重新构造请求，不能继续承担任务幂等语义。`useParameterForm` 已根据 parameters 变化清空 values/errors 并按 descriptor 默认值重建；日期、月份、股票规范化及相互关联端点校验可直接复用。

采用三处明确边界：`downloadTasks.js` 负责 HTTP 与 DTO；useDownloadFlow 负责表单元数据及一次未确认提交；新增 useDownloadTaskList 负责列表分页和轮询。列表查询与表单选择独立，历史查询不依赖来源仍启用；不用一个 generation 同时控制两者。DownloadTaskList 为展示组件，由 DownloadView 接线，不再内置第二份轮询。

### API、无损数值与 DTO

新增 `control-plane/src/api/downloadTasks.js`，继续使用现有 http 实例、baseURL、超时、请求 ID 和错误规则。公开接口如下（省略的 options 默认为空 object）：

```js
getDownloadCapabilities(pluginId, apiName, { signal } = {}) // -> Capabilities
submitDownloadTask(request, { signal } = {})              // -> Receipt
listDownloadTasks(criteria = {}, { signal } = {})          // -> TaskPage
```

能力 GET 使用 `/data-sources/{pluginId}/apis/{apiName}/download-capabilities`；POST 使用 `/download-tasks`，body 恰为 `{submissionId,pluginId,apiName,mode,params}`；列表 GET 使用 `/download-tasks`。列表 criteria 仅允许 page/pageSize/pluginId/apiName/status/submissionId，默认 page=1/pageSize=20，省略 undefined；空字符串/null/数组/未知键/非法枚举不发送，作为调用方输入错误抛 TypeError。page 1..2147483647，pageSize 20/50/100，身份遵守 T09 UUID/标识正则。使用 URLSearchParams 序列化已验证的单值，不允许 Axios 自动生成数组或重复参数。前端表单只传 descriptor 白名单 string params。

新 `control-plane/src/api/downloadTaskDtos.js` 导出 `parseTaskJson`、`parseDownloadCapabilities`、`parseDownloadTaskReceipt`、`parseDownloadTask`、`parseDownloadTaskPage`，后四个接收已解析值和 requestId，返回新建的只读白名单 DTO，非法响应抛 ClientError('INVALID_RESPONSE', requestId)。Task 的完整字段与 required/nullability 以 `docs/contracts/download-task.schema.json` 的 TaskResponse 为准；不能套用旧 SUCCESS/EMPTY 或证券记录 DTO。T11 直接复用 parseDownloadTask，再增加 Batch/Control 边界。

任务请求单独设置 `responseType: 'text'` 和 `transformResponse: [parseTaskJson]`，在 Axios 默认 JSON.parse 丢失数值前处理原始 JSON。parseTaskJson 使用原生 `JSON.parse(text, reviver)`：对每一个 number token，必须先取得第三个 reviver 参数的 `context.source` 并核对十进制整数词法，再决定安全值保留 Number、不安全值用 `BigInt(source)` 保真。不能先依据舍入后的 Number.isSafeInteger 跳过原 token 校验，也不能 `BigInt(已舍入的 Number)`；例如 `9007199254740990.5` 和 `1.0000000000000001` 会舍入成安全整数，仍必须拒绝。无法解析、数值缺原 token、非整数 token 均保留为无效响应值（返回原 text），由 DTO/现有错误边界统一拒绝；不要在 transform 中抛出会被错误拦截器改写为 UNEXPECTED 的自定义异常。任务传输测试 adapter 必须提供原始 JSON text，不能预先 JSON.parse 后跳过该边界；DTO 纯函数单测可直接传安全 Number/bigint。

DTO 将所有 int64 字段统一为 bigint：version、page.total、counts 九项、requestCount/runRequestCount、可空 completenessRule.rowLimit。输入只接受安全 Number 或上述无损解析得到的 bigint，检查 0..9223372036854775807（version/rowLimit 最小 1），拒绝 wire string、小数、负数及溢出。page/pageSize 保持 Number；T11 的 attemptCount 亦为 int32 Number。字符串 params 保持字符串，不把股票代码/业务值转 BigInt。各层禁止 Number(version)、parseInt(count) 或 JSON 序列化整个 Task；显示用 bigint.toString()/toLocaleString，计算用 bigint。后续 T11 expectedVersion 须经正 int64 校验再将十进制文本作为 JSON **number token** 写入 body，不能直接 JSON.stringify(BigInt) 或加引号；T10 不提前实现该路由。

此选择无需新增库。Node 24 和支持 JSON.parse source 的浏览器可读取完整 int64；不支持 source 的旧浏览器遇到含数值的任务响应明确 INVALID_RESPONSE，保留原任务/未确认提交状态，提示浏览器无法保真处理任务数值。不能声称无 source 时可安全识别已舍入的小数，也不能将已舍入计数展示为事实。保持 http.js、旧证券 LONG/DECIMAL string 和 downloads.js 解析不变。合法 HTTP 错误 JSON（无 number 字段）经同一个 transform 成为普通 object，仍交 normalizeError；非法错误正文仍是 INVALID_RESPONSE，不能绕过其 requestId/HTTP状态检查。

DTO 逐层检查精确字段集合、布尔/枚举、UUID、规范标识、真实日历日期及 UTC ISO 时间、string map、可空字段；不回显错误正文。Capabilities 参数校验复用现有 ParameterDescriptor 形状：ENUM 有非空 allowedValues，DATE_RANGE_MEMBER 有指向现存且互指端点的 relatedParameter，参数名不重复，字段白名单遵守 schema。AVAILABLE 必须有有效 dateAxis/dateLabel/startParameter/endParameter、对应端点、planningMode/policyVersion 和明确 completenessRule；NEEDS_VERIFICATION/UNSUPPORTED 必须有不可用原因，UNSUPPORTED 的 null 字段和空数组按 schema 接受。未知类型不渲染可提交表单。

TaskCounts 用 bigint 核对 totalBatches=pending+running+succeeded+failed，split 独立；planReady=false/0 批不推导成功。Page items≤pageSize，ID 不重复，响应 page/pageSize 必须等于请求；保留 total 和服务器顺序，不按返回的新 status 重筛或补位。按 submissionId 找回时必须是 total=1 且恰好一个匹配 submissionId 的 Task，或 total=0/items=[]；其他结果 INVALID_RESPONSE。submitDownloadTask 仅接受 POST 200/202，验证 requestId 与响应 X-Request-Id 相等、Location 恰为 `/api/v1/download-tasks/{taskId}`；其首次接收 202 应为 QUEUED，200 重放可为任一任务状态。共享 Receipt DTO 接受全部任务状态，不把首次接收限制固化到 T11 将复用的控制回执解析。GET 仅接受 200。

### 模式、元数据与表单

useDownloadFlow 保留 load/selectSource/selectApi/submit 的入口，selectApi 改为 async 加载能力。公开 `metadataState`（INITIAL/LOADING/READY/FAILURE）、sources/apis、两个选择值、selectedSource/selectedApi、capabilities、mode、parameters、formKey、metadataError 及 retryMetadata；`submissionState` 和提交输出另列在下节。所有方法对无效选择安全返回 false。

选择来源立即清除接口、能力和表单；选择接口立即清除上一能力和参数后 GET 能力。元数据链单独 generation，来源/接口变化、重新加载和 dispose 均递增；迟到成功和失败都丢弃。加载失败明确“下载配置加载失败”，提供重新加载当前失败步骤；不可沿用旧接口能力。保留 DataSourceSelect 的单来源自动选择行为。

能力成功后 AVAILABLE 默认 RANGE，其余默认 SINGLE，禁用 RANGE 选项并显示 range.unavailableReason。single.available=false 时也禁止 SINGLE 提交，来源不可用原因继续显示。canSubmit 必须同时满足当前来源可下载、匹配的能力已加载、模式可用、无进行中提交/恢复、没有待找回快照；不从旧 `/apis` 单独推导 RANGE 可用。

模式选择用带标签的 el-radio-group（“单次请求”“日期区间”），RANGE 说明来自 dateLabel/参数描述，禁止统一改成公告时间。SINGLE 始终显示“单次请求，结果不代表完整历史”。有效模式/来源/接口切换递增 formKey，DynamicParameterForm 以该 key 重挂载并接收当前模式独立 parameters；值、错误、规范化快照和旧默认值全部重建，不能仅删除 trade_date/ann_date 后复用其余输入。即使 SINGLE 和 RANGE 的字段名完全相同，也必须清空用户输入再应用当前 descriptor 默认值。API 描述保留，实际参数只来自能力中的所选模式。

handleSubmit 调用当前表单 validate/normalizedValues；无参数提交 `{}`。沿用 YYYYMMDD/股票大写规则、必填聚焦和日期端点校验，不硬编码过去日期限制或凭前端放开 BSE。服务端字段错误在接收面板以安全文案和字段名显示，表单值保留供修正。

### 提交快照与恢复状态机

新增 `control-plane/src/utils/downloadTaskSubmission.js`，唯一存储键 `tensor.downloadTasks.pending.v1`，值恰为 `{schemaVersion:1,request:{submissionId,pluginId,apiName,mode,params}}`。仅存业务参数和身份，不存 headers、凭证、完整响应/错误/能力对象。新提交在校验完成后用 crypto.randomUUID 生成一次 ID，按当前 parameter 名白名单复制并冻结 request 和 params；以 TextEncoder 校验 params JSON≤8192字节。先成功写 sessionStorage，再调用 POST。存储访问或写入失败时明确“无法保存提交记录，暂不能提交”，零 POST，允许手动再试；不静默降低刷新恢复保证。

读取时检查 schemaVersion、精确键、UUID/标识/模式、string params 和字节限额，恢复不访问当前能力或重算规范化参数。损坏/未知版本不能执行 POST：显示“本地提交记录无法恢复，请先核对近期任务”，保留列表可读并提供“清除损坏的本地记录”动作；该动作只清除此键，文案明确不会取消服务端任务，动作本身不提交。其他 sessionStorage 键不动。读取存储异常则不能证明无 pending，阻止新提交并可重试读取。不存已完成任务历史，标签页关闭后的找回依靠数据库列表。

useDownloadFlow 另公开 `submissionState`（IDLE/SUBMITTING/UNCERTAIN/RECOVERING/ACCEPTED/REJECTED）、pendingSubmission、receipt、recoveredTask、submissionError、storageError、locked、canSubmit、recoverSubmission、replaySubmission、retryStorage、clearCorruptSubmission、dispose。locked 仅在 SUBMITTING/RECOVERING 时为 true；正常接收后立即 false，不等待 worker。元数据与 submission 各自拥有 generation，选择其他接口不清除未确认快照或接收身份。

| 事件 | 状态与动作 |
| --- | --- |
| 新提交且无 pending | 保存不可变快照→SUBMITTING；拒绝第二次点击/重入；本次提交期间冻结选择和表单 |
| 200/202 Receipt 通过全部验证 | ACCEPTED；显示“任务已接收”及 taskId，标注进度以近期任务/详情为准；200 可附“已找回原任务”；释放锁、清除相同 submissionId 的本地 pending，通知列表刷新第一页；不从 Receipt.status 宣称下载成功 |
| 首次 POST 明确拒绝 | 经过 ApiError 校验的 PARAM_REQUIRED/PARAM_INVALID、PLUGIN_DISABLED/DATASET_MISCONFIGURED/BATCH_DOWNLOAD_UNAVAILABLE/TASK_STATE_CONFLICT/TASK_DEFINITION_CHANGED/TASK_QUEUE_FULL 为 REJECTED，清除本次 pending、释放锁，显示错误供修改/稍后手动提交；不自动重试 |
| SUBMISSION_CONFLICT | 进入 UNCERTAIN，保留原键并 GET submissionId；找到时显示已有任务及“该提交标识已对应任务，请核对参数”，不自动换键重发 |
| 网络/超时/无效或不完整成功响应、PERSISTENCE_FAILED/QUERY_FAILED/INTERNAL_ERROR 及其他非明确接收拒绝 | UNCERTAIN，保留原键/参数；提示“提交结果尚未确认，请找回原任务”，不把后台任务标为 FAILED，不因 retryable=false 丢弃快照 |
| 刷新首先调用 recoverSubmission，存在合法 pending | 先进入 RECOVERING，执行精确 submissionId GET，完成本次尝试后再独立启动来源/接口加载和普通列表；先前能力停用/移除也不能阻止恢复 |
| GET 找到原任务 | ACCEPTED，保存 recoveredTask 与原任务身份，清除匹配 pending；没有本次 Receipt 时不伪造 requestId，Task 的 status/counts 为服务端查询事实 |
| GET 空页 | UNCERTAIN，不证明旧请求永远未接收；提供“使用原参数重新确认”按钮，显式点击才 POST 相同完整 request 和 submissionId；不会自动创建新键 |
| 恢复 GET 失败 | UNCERTAIN，保留快照，展示“状态暂时无法更新”和安全错误，提供“重新查找”；一次恢复不启动无限自动 POST/GET |
| 同键重新确认失败 | 保留 UNCERTAIN 和原快照，无论该次错误是否 retryable；旧请求曾有不确定窗口，不能把后续失败当作原任务不存在的证明 |
| dispose/离开页面 | 递增 generation、卸载回调；不清除 pending、不尝试取消后端任务；已发出的 POST 迟到结果不写组件状态/存储 |

UNCERTAIN 可浏览/切换来源和接口，接收面板独立保留原快照概要；新的提交按钮不可覆盖该快照，提供的是查找/同键确认入口。原任务被确认后可立即开始另一项下载。重复查询/确认按钮在 RECOVERING/SUBMITTING 时互斥。恢复使用独立保存对象，不能从当前表单读参数，也不能先 GET capabilities；响应提交失败时不能自动调用下载任务的 retry 路由。

清除存储须重新读取并核对键中的 submissionId，避免旧请求清掉较新快照；删除失败保留安全提示，但已确认的内存状态仍可解锁，刷新可能再找回同一任务。下一次新提交必须重新成功持久化新快照后才发请求。不把接受响应对象（含 bigint）JSON.stringify 进 sessionStorage。

### 近期列表、分页与生命周期

新增 useDownloadTaskList，公开 `page`（1）、`pageSize`（20）、`result`、`loading`、`error`、`lastUpdatedAt`、`start`、`refresh`、`changePage`、`changePageSize`、`onAccepted`、`dispose`。普通列表仅传 page/pageSize，展示所有接口近期任务；不受表单选择筛选，不在本项增加筛选 UI。submissionId 恢复使用单独请求，不改变普通列表筛选。

start 在恢复尝试结束后执行可见页面首查，注册 visibilitychange；页面隐藏不启动定时/首查。每次请求结束后用一个 setTimeout 排下一次：成功后 5000ms；连续查询失败按 5000/10000/30000ms（后续保持 30000），成功归零。同一列表最多一个未完成请求，不用 setInterval；手动刷新/可见性恢复/接收通知遇到在途请求只合并一个待刷新意图，待其 finally 后再发，不能并发补查。可见性恢复立即查询；隐藏时清 timer、递增 generation 使在途结果失效，并保留在途标记直到请求真正 settle；恢复时若未 settle 则排队立即补查。dispose 清 timer/listener、递增 generation，所有迟到成功、失败、finally 都不得再写可见状态或排定时器。

翻页/换页长更新请求意图和 generation（换页长回到第1页），旧页迟到响应丢弃；在途请求结算后仅执行最新意图。翻页期间清除旧页内容，避免把上一页行标成新页；同页后台刷新保留上次成功 rows 与更新时间。onAccepted 回到第1页请求服务器，不把 Receipt 拼成虚构 Task 行、增加 total 或在客户端排序；即便当时请求在途也合并执行一次最新页刷新。

分页只使用响应 page/pageSize/total；总页数以 bigint `(total + pageSize - 1)/pageSize` 计算，再限制为 HTTP 可访问的 2147483647 后转 Number 传 el-pagination，原 total 始终按 bigint 完整显示。空列表总页数显示 0、禁用前后页；超尾空页保留服务端 page，显示“本页暂无任务”并允许回到第一页，不伪造折页响应。不得把某行 status=FAILED 或 PARTIAL_FAILED 当成整个 GET 失败。查询失败保留同页既有任务状态，提示“状态暂时无法更新”及上次更新时间；没有成功快照时显示错误面板，提供手动刷新。

DownloadTaskList props 为 `{page,pageSize,result,loading,error,lastUpdatedAt}`，事件为 `refresh`、`update:page`、`update:pageSize`。内置近期列表标题、手动刷新、表格和 el-pagination，沿用 DatasetPagination 的视觉/尺寸约定；其已有文案/Number total 是数据集专用，故不改该组件或强行共用其数据协议。通过显式 aria-label="近期任务分页"、键盘可操作按钮/链接、横向滚动容器保持窄屏可读，不添加全新布局框架。

| 列 | 内容 |
| --- | --- |
| 接口/参数 | pluginId / apiName；规范化 params 所有业务键值，稳定按键名展示。ts_code/exchange 等自然包含；不依赖当前元数据存在，不用当前日期轴错误解释历史参数 |
| 模式/范围 | SINGLE“单次请求”，RANGE“日期区间”；展示 params 中实际端点名称与 YYYYMMDD 值（仍保留其他参数），不猜历史公告日/报告期含义 |
| 状态 | QUEUED 排队中、RUNNING 运行中、SUCCEEDED 已成功、PARTIAL_FAILED 部分失败、FAILED 失败、INTERRUPTED 已中断；颜色沿用现有语义，同时保留文字 |
| 当前批次 | planReady=false“尚未生成计划”；否则“已结束 succeeded+failed / 当前计划 total 批”，同时列成功/失败/待执行/运行中及独立拆分数；0 计划如实展示，不推导终态，不用固定百分比 |
| 写入次数 | “新增记录次数”“更新记录次数”，完整整数；说明为已提交写入操作次数，不是整段去重总量 |
| 时间/入口 | createdAt、updatedAt 按浏览器时区展示，保留 ISO 值为 title；每行“查看任务”链接 `/downloads/tasks/{taskId}` |

### 页面与 T11 接口

保留 download-grid 的两栏结构；左侧仍为下载配置，加入模式选择；右侧标题改为“任务接收”，使用 AsyncStatePanel 展示加载、明确拒绝、未确认恢复和已接收身份。修改 DownloadAction 的按钮文案为“提交任务”，保留其 disabled/submitting/submit 接口和按钮可访问性；下方帮助文案为“接收后可继续提交其他任务”。底部增加一个全宽 DownloadTaskList，使用现有 WorkbenchPanel/token 和 scoped 样式，不改整站导航或主题。

接收面板和列表均提供真实 path 字符串链接 `/downloads/tasks/{taskId}`，不用尚未注册的 named route。T10 验收链接路径及 taskId，T11 再注册详情路由并证明导航/刷新。中间阶段详情页面尚未实现是明确边界，不能将已有链接声称为完整详情体验，也不为绕过这一边界在 T10 添加占位路由。T11 直接消费 bigint version、Task DTO、已提交任务身份和路径；T10 不自动导航，收到 ID 之后用户可继续配置其他接口。

DownloadView mount 先 await recoverSubmission()；其首次调用读取存储，无 pending 即结束，有 pending 按上表找回，读失败也返回明确状态。之后以 Promise.allSettled 独立启动 load() 元数据与列表 start()，不让元数据超时阻塞历史列表。load 只负责元数据且不清 pending，手动 recoverSubmission 复用冻结快照。unmount 调用两个 dispose，清除观察器和事件处理；await recoverSubmission 返回后的续体也必须检查组件仍挂载，已卸载不得再调用 load/start，dispose 后各入口不重新启动请求。metadata、submission、list 的错误各自呈现，不互相覆盖。加载期间 aria-busy，仅新状态用 polite/alert 提示，不每五秒重建整页/移动键盘焦点。

## Files

路径均相对仓库根；本次设计写作只创建本文，不提前实施下列改动。

| 文件 | 实施职责 |
| --- | --- |
| `control-plane/src/api/downloadTasks.js`、`downloadTasks.spec.js` | 能力/提交/列表传输、query/响应状态/头校验、原始 JSON 数值与安全错误测试 |
| `control-plane/src/api/downloadTaskDtos.js`、`downloadTaskDtos.spec.js` | Task/Receipt/Page/Capabilities、无损 JSON helper、bigint 合同；引用 T09 schema/示例作测试来源 |
| `control-plane/src/utils/downloadTaskSubmission.js`、`downloadTaskSubmission.spec.js` | 唯一 sessionStorage 键、白名单冻结/校验/同键清除及失败边界 |
| `control-plane/src/composables/useDownloadFlow.js`、`useDownloadFlow.spec.js` | 两种模式、独立元数据/提交状态、恢复优先和同键确认 |
| `control-plane/src/composables/useDownloadTaskList.js`、`useDownloadTaskList.spec.js` | 服务端分页、可见性/退避/互斥/generation 和销毁 |
| `control-plane/src/components/download/DownloadTaskList.vue`、`DownloadTaskList.spec.js` | 表格、写入次数/动态批数、时间、分页/刷新、任务 path 链接 |
| `control-plane/src/components/download/DownloadAction.vue`、`DownloadAction.spec.js` | 任务提交文案，保留操作接口及双击禁用 |
| `control-plane/src/views/DownloadView.vue`、`DownloadView.spec.js` | 模式/表单 key、接收状态与恢复入口、列表生命周期和两栏布局接线 |
| `control-plane/src/components/download/DynamicParameterForm.spec.js` | 必要时补同字段名模式重建回归；实现组件/useParameterForm 可直接复用，默认不改 |

保留 `control-plane/src/api/downloads.js`、`api/api.spec.js` 中旧同步方法合同、DownloadResult 及其测试；更新流/视图中旧同步结果断言为本项接收断言，不能删除股票/日期/无参数/聚焦/旧响应隔离覆盖。T11 新增 router/index.js 的详情路由及批次/控制方法，T12 接续普通 e2e fixtures；本项不将这些未来产物加入已完成清单。

## Tests

首个实施动作：新增 downloadTasks.spec.js，使用现有 http.defaults.adapter 的真实 Axios 管道返回原始 JSON、202、X-Request-Id 和 Location；断言 submitDownloadTask 只 POST 一次完整 submissionId/mode/string params，返回 Receipt（version 为 bigint）且不是 SUCCESS/EMPTY。观察缺失模块/入口 RED 后实现最小传输与 Receipt 验证，再补关键不确定提交状态用例。按最小代码实现，禁止先重构全站 HTTP。

| 场景 | 必须观察的结果 |
| --- | --- |
| HTTP/DTO | 三路由、严格 query、200/202/Location/requestId、全部 required/null/type/enum、schema 实例（仅本项四种响应）；旧 downloadDataset request/response 原样兼容 |
| 数值保真 | 原始 JSON 的 9007199254740991、9007199254740992、9007199254740993、9223372036854775807 精确得到 bigint；越界、wire string、小数（含会舍入成安全整数的 9007199254740990.5、1.0000000000000001）、非法 JSON、无 source 的所有数值 token 拒绝；API 错误拦截仍正确，不改变旧证券字符串 |
| 能力/模式 | AVAILABLE 默认 RANGE；NEEDS_VERIFICATION/UNSUPPORTED 默认 SINGLE+原因；single不可用阻止提交；能力请求竞态/失败无旧数据；交易日/公告日/报告期标签原样，旧 trade_date/ann_date 不进入 RANGE；相同字段名切换也清值/错误，默认值重新应用 |
| 参数 | 40 描述仍可选择；股票/交易所/日期/无参数、未来日期及端点错误沿用现有动态行为；非法表单零 POST，聚焦首错 |
| 接收 | 200/202 仅显示任务已接收，锁释放且可选另一接口/提交新任务；不会显示旧下载成功/EMPTY计数；请求快照冻结不随传入对象或后续表单变化 |
| 恢复/幂等 | 存储先于 POST；双击仅一次；丢响应/500/无效Receipt保留同键；刷新先 GET submissionId 后加载能力，停用/移除元数据仍找回；空页后手动同键确认，参数/ID不变；UNCERTAIN 切换接口不覆盖原键，第二个新键不发送 |
| 故障/生命周期 | 明确首次400/409/429清本次记录，SUBMISSION_CONFLICT找回；不确定后的重放失败仍保留；存储读取/写入/删除异常、损坏记录及8KiB边界；卸载后迟到响应不清新键/改状态，存储不误删其他键 |
| 列表 | 服务端分页20/50/100、顺序和total、超尾空页；接收后第一页GET，无客户端插行；状态/计划0/动态拆分计数和bigint写入次数正确，历史不依赖当前元数据；链接精确到taskId |
| 轮询 | fake timers/deferred 证明5秒、隐藏暂停/恢复补查、同页保留、5/10/30失败退避及成功重置；翻页/换页长/接受事件只执行最新意图；在途不重叠，旧成功/失败/finally无污染；unmount无timer/listener泄漏，查询失败不把任务改FAILED |
| 组件 | 真控件模式与必填键盘顺序、接收文案、未确认按钮互斥、分页/刷新事件、窄屏滚动结构；保留旧 DownloadResult 与旧同步 API 测试通过 |

前置 Node `>=24.15.0 <25`，npm 使用项目构建已验证的 11.12.1；仓库根运行：

```sh
npm --prefix control-plane test -- src/api/downloadTasks.spec.js src/api/downloadTaskDtos.spec.js src/api/api.spec.js src/utils/downloadTaskSubmission.spec.js src/composables/useDownloadFlow.spec.js src/composables/useDownloadTaskList.spec.js src/components/download/DownloadTaskList.spec.js src/components/download/DownloadAction.spec.js src/components/download/DynamicParameterForm.spec.js src/views/DownloadView.spec.js
npm --prefix control-plane test
npm --prefix control-plane run build
git diff --check
```

预期全部退出 0，新增测试实际执行，失败/错误/无解释跳过为 0；全量保留既有 24 文件/170 项基线的兼容覆盖（改造后的计数以实际报告为准）。用 fake timers、可控 Promise、jsdom sessionStorage 和 visibilitychange 验证；每个挂载/定时测试清理自己的组件和 timer，不通过长 sleep 猜状态。独立审查提交恢复、数值边界和轮询后记录实际命令/数量，再按既定工作流完成 T10、准备 T11。此任务仅前端改动，不为文案/设计重复执行 T09 四条 Maven；T12 的全量浏览器和真实后台闭环另行运行，不能把本项模拟 API 测试当成后台独立执行证据。

## Acceptance

1. 能力正确决定模式、参数与原因，模式切换从当前 descriptor 重建干净模型；SINGLE 明示完整性限制，日期轴不混淆。
2. 提交只调用 task API，200/202 显示接收并解锁；响应不明和刷新使用不可变原 submissionId/请求找回，无自动新键或后台任务失败误报。
3. sessionStorage 只存非敏感 pending，异常不会导致未保存即发送；恢复不依赖当前能力，已接收任务可由服务端列表发现。
4. 列表遵守数据库分页、动态批数/提交计数和时间，五秒可见刷新、失败退避、请求互斥及旧响应隔离有测试；详情路径与 T11 边界明确。
5. Task int64 在支持 source 的环境完整保真，无法取得原 token 的数值响应明确拒绝；不修改后端数字合同或旧 LONG/DECIMAL string。旧同步 API/组件兼容、全前端测试与构建通过，并记录实际证据。

## Risks

- 生产 RANGE 仍全部 NEEDS_VERIFICATION；AVAILABLE 测试夹具不开放来源，真实策略验证属 T13。
- sessionStorage 是单标签页未确认快照，不是持久历史；关闭标签页仍从数据库近期列表找回。存储损坏无法猜回原键，必须先提示核对历史。
- 旧浏览器缺少 JSON.parse source 时无法读取含数值的任务响应；设计明确失败边界、保留 pending，不承诺会损失原 token 的回退。T11 必须延续 bigint 数值合同及 JSON number token 控制请求。
- 当前 T10 中间阶段只提供详情 path，T11 才交付可直接刷新打开的详情页；T12 尚需更新普通浏览器同步下载断言。不得因此提前宣称母 issue 已完成。
- 元数据、接收和列表有不同请求生命周期；测试须覆盖迟到失败和 finally，不仅覆盖迟到成功。关闭页面不等于取消后台任务。
- 没有待用户补充的实质需求；本文固定实现选择，不改变看板范围、后端合同或任务顺序。
