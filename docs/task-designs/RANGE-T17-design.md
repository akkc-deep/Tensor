# RANGE-T17 失败任务列表、详情与手动重试页面设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的`RANGE-T17`：用户在下载页查看当前保存的失败任务、区分原始下载区间与剩余失败范围，并直接对原任务执行一次手动重试。

本设计在T16已记录COMPLETED后创建；按预定义Order选择17，观察到NOT_STARTED、Design／Handoff均None。设计及交接准备不启动实现。

## Scope

- 在下载页增加“发起下载／失败任务”页签；接入T14的三个任务端点、独立插件／接口筛选、20／50／100分页、任务列表、完整详情和“重试一次”。消费T16精确query入口及结果组件。
- 覆盖AC-PRD-RANGE-20、21、22、23、25、27的前端适用部分。保留T15表单、T16首次下载与通信边界、现有KeepAlive、数据查看、主题及公共组件；仅做任务页面所需局部接入。
- 不新增历史、任务编辑、核对结果、确认弹窗、取消／暂停、进度、轮询、自动重试、浏览器分批、持久化本轮计数或全局任务状态。不以原始区间／当前表单重建重试请求，不修改后端、OpenAPI、数据库、生产来源或日历。
- 保留任务启动时分支／HEAD、原索引和任务外工作；新增正式文件精确加入Git，不提交／发布。完整后端浏览器闭环由T18／T19完成，真实来源由T20验证。

## Approach

### 1. 直接输入与最小结构

依次读[PRD v1.4 §1.1、§5.5～§5.8](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD v1.10 §8](../design/Tensor_区间下载_TRD_v1.0.md)，再读当前DownloadView、DownloadResult、useDownloadFlow、http／errors／downloadResult、公共WorkbenchPanel／AsyncStatePanel以及DatasetPagination。最后消费[T14设计](RANGE-T14-design.md)／[HTTP验证](../verification/RANGE-T14-http.md)、[T16设计](RANGE-T16-design.md)／[结果验证](../verification/RANGE-T16-results.md)，精确wire以[OpenAPI](../contracts/openapi-v1.yaml)的三个retry-tasks端点及RetryTaskPage／Summary／Detail／Item／OriginalDateRange为准。

T14已提供只读任务快照、空body execute、404／409及18字段执行结果。T16已完成结果守卫、26码错误解析、六outcome／通信未知和`{name:'downloads',query:{tab:'retry-tasks',taskId}}`入口；最终137项指定／278项全量、1701模块build、四码真实Axios补证、受控页面及独立最终评审通过。它们是直接输入，不能冒充T17页面验收。

新增`api/retryTasks.js`（三个请求及任务守卫）、`composables/useRetryTaskFlow.js`（列表／详情／本轮重试状态）、`components/download/RetryTaskList.vue`和`RetryTaskDetail.vue`（展示和事件）。DownloadView持有两个flow、页签和路由协调；不另造路由、store或任务中心。结果直接复用DownloadResult。范围文字沿T16的小函数规则，任务组件内实现，不为少量格式化引入全局框架。

### 2. Axios接口与响应守卫

复用http实例、X-Request-Id拦截器、130000ms timeout和errors.js，不改变查询全局解析。模块导出以下三个异步函数；响应不重新计算字段：

| 接口 | 请求及返回 |
|---|---|
| `listRetryTasks({pluginId,apiName,page=1,pageSize=20}={})` | 一次GET `/retry-tasks`；省略空筛选，两个标识独立，不依赖当前元数据；传page／pageSize。返回已验证RetryTaskPage |
| `getRetryTask(taskId)` | 一次GET `/retry-tasks/${encodeURIComponent(taskId)}`；返回已验证RetryTaskDetail，并校验返回taskId等于请求UUID的小写规范值 |
| `executeRetryTask(taskId)` | 一次`http.post(path)`，不传第二个data参数，也不带任何范围／条件query；真实adapter中data必须undefined，浏览器中body必须零字节。返回经T16 isDownloadResult验证的正常五outcome；错误沿用ApiError快照／ClientError |

三个正常响应均核对body与响应头RequestId；不合法抛只含安全固定文案和本次outgoing RequestId的ClientError INVALID_RESPONSE，不回显body。execute只能返回taskId=null或原路径UUID，不接受另一个任务。pluginId／apiName与冻结详情的匹配由flow核对，正常和错误快照同样检查。错误快照的taskId也只能为null或原任务，null不能由输入ID补回。UUID不限定版本，但必须原始string且完整8-4-4-4-12十六进制，不接受数组或短段；URL入口规范化为小写。

任务守卫是该模块私有函数，不引入schema库。为复用已有四字段范围校验，仅在downloadResult.js导出`isRecoveryScope`（现有validScope的命名导出），不改变isDownloadResult及18字段语义。RetryTaskItem先检精确七字段，再对四字段投影调用该守卫；不构造假的DownloadResult做校验。

- Summary恰11字段：taskId、pluginId、apiName、pluginDisplayName、apiDisplayName、originalDateRange、originalDateRangeStatus、failedItemCount、failedScopes、createdAt、updatedAt。Detail恰17字段：Summary的11字段加requestId、taskParams、items、retrying、canExecute、executionBlocker六字段；与下载执行结果的18字段分开。Page恰6字段：requestId、page、pageSize、totalElements、totalPages、items。标识采用T16的原始string和现有正则；显示名为string或显式null，显示为空时也回退到保存标识。计数／页码只接受非负安全整数，failedItemCount>=1，page>=1，pageSize属于20／50／100；不将字符串数字或缺失值变0。
- Summary.failedScopes为非空、按完整四选择器唯一的范围数组，failedItemCount等于数组长度。Detail.items非空，精确七字段（四选择器＋errorCode／errorMessage／updatedAt），错误码／原因沿T16非空及64／512长度边界，items数量／顺序／选择器与failedScopes一致；按targetType／targetValue／timeType／timeValue字符串升序。不能只按日期去重。
- createdAt、updatedAt及item.updatedAt严格UTC毫秒`YYYY-MM-DDTHH:mm:ss.SSSZ`，用现有日期工具验证真实公历并校验时分秒；拒绝Date的自动溢出归一化。列表保持服务端updatedAt DESC／taskId DESC，不在浏览器重排或补页；验证重复taskId、计数与分页形状，totalPages等于向上取整，items长度与返回页的总数相符。越界请求允许服务端返回末页，不要求返回page等于请求page；pageSize必须匹配请求值。空页固定page1／totalElements0／totalPages0／items[]。
- RECORDED必须带恰startDate／endDate的compact YYYYMMDD对象：原始string、真实公历、开始<=结束、包含两端<=31天。其他三种status必须originalDateRange=null。NOT_APPLICABLE显示“不适用”，NOT_RECORDED显示“未记录”，UNCONFIRMED显示“原始区间未确认”；不能用失败项、表单或taskParams重新派生该显示值。
- Detail.taskParams是对象，键为snake_case公开条件、值为string；延续T14安全投影，拒绝非字符串、嵌套值及已禁止的offset／limit／cursor／page／page_size／trade_date／ann_date／month，以及键中含token／authorization／cookie／password／credential的内容（不区分大小写）。不额外请求metadata决定是否显示离线任务。start_date／end_date只用于独立保存事实，不将缺失／畸形历史参数改写为当前表单值；原始日期status由服务端负责，合法UNCONFIRMED详情仍需可读。
- retrying／canExecute为boolean；executionBlocker为null或恰code／message的非空安全字符串对象，canExecute当且仅当blocker=null。UNCONFIRMED原始区间必须canExecute=false；retrying=true必须canExecute=false且blocker.code=DOWNLOAD_BUSY。blocker优先级及实时可执行性由服务端决定，前端不靠error.retryable重算执行权限，不过滤不可执行记录。

### 3. 页签、GET与状态生命周期

DownloadView使用Element Plus现有`el-tabs`／`el-tab-pane`，标签精确为“发起下载”“失败任务”。两页内容挂载后保持实例，用v-show／非销毁页签；不因切页销毁表单、本轮结果或在途Promise。现有DownloadView KeepAlive与路由定义不改。

- 默认tab为发起下载；仅`route.name==='downloads'`且query.tab为单个`retry-tasks`字符串时选择失败任务，其余安全回默认。首次进入失败任务触发一次列表GET；返回已加载页签保持内存，不使用onActivated自动刷新／execute。显式“刷新列表”“刷新详情”仅GET。
- T16入口及列表“查看详情”均写精确`{name:'downloads',query:{tab:'retry-tasks',taskId}}`，只让路由watch调用详情加载，点击处理器不再重复GET。单个合法UUID才GET详情；数组／空值／短UUID显示“任务标识无效”，不向端点发送它。没有taskId显示“选择失败任务查看详情”。首次以tab=retry-tasks且合法taskId深链进入时列表和详情各GET一次，可独立成功；详情不依赖该任务恰好在当前筛选页。
- 切到发起下载用`{name:'downloads',query:{}}`，保留任务flow内存；再次点击失败页签时若已有选中任务则带原taskId，否则用`query:{tab:'retry-tasks'}`。路由返回同一已加载taskId时不重新GET；首次进入、新taskId或显式刷新才读详情。失败页query无taskId时清理当前选择并显示初始说明；其他路由上的query变化不清理在途状态。浏览器刷新／完全卸载后重新挂载只GET当前记录，无历史计数和自动POST。

`useRetryTaskFlow({isDownloadLocked})`接收只读函数，返回listState／listResult／listError、detailState／detail／detailError、selectedTaskId、page／pageSize、appliedFilters、executionState／executionResult／executionError／executionContext、locked、canExecute、needsRefresh、executionMessage及以下方法：`loadList(filters)`、`changePage(page)`、`changePageSize(size)`、`refreshList()`、`selectTask(taskId)`、`refreshDetail()`、`execute()`。selectTask(null)清理选择；filters只含pluginId／apiName，应用筛选和改变pageSize均回第1页；列表使用返回page更新本地状态。分页首次20，不继承数据查看默认50。needsRefresh在执行开始及结束后旧权限失效时为true，仅最新成功详情GET将其清为false；canExecute还必须满足needsRefresh=false。executionMessage按本地等待／首次下载占用、待读取刷新、服务端blocker顺序给出只读说明，供详情按钮旁展示。

列表状态INITIAL／LOADING／SUCCESS／EMPTY／FAILURE，详情INITIAL／LOADING／SUCCESS／NOT_FOUND／FAILURE，执行INITIAL／SUBMITTING／T16六结果／FAILURE。列表和详情用独立generation拒绝迟到响应覆盖新筛选／新选择；GET故障仅给各自“重新加载”，不能复用execute。GET失败不抹掉独立的本轮执行结果，也不能把旧列表伪称最新。

未执行时选择不同任务清理旧本轮结果／上下文；同一任务刷新详情保留刚收到的本轮结果，但标签明确是“本轮重试结果”。清理详情期间禁用execute，不能凭旧canExecute发送。列表筛选不更改首次下载来源／API／表单，也不改变当前已选详情；选中任务可以不在该筛选页。

### 4. 手动重试、刷新与404／409

`execute()`只接受当前已验证详情，二次检查本地未锁、外部首次下载未锁、detailState=SUCCESS、canExecute=true和retrying=false。同步置SUBMITTING，冻结`{operation:'RETRY',taskId,pluginId,apiName,params:{...detail.taskParams},rangeMode}`；只有UUID传API，params仅用于本轮REQUEST范围说明。rangeMode来自originalDateRangeStatus：RECORDED／NOT_RECORDED为true，NOT_APPLICABLE为false；UNCONFIRMED不可执行。不得假设详情存在mode字段或以失败时间类型猜模式。

“重试一次”点击直接POST，无确认弹窗。区间可执行详情在按钮旁显示“区间下载开始后不可终止。”；等待使用T16固定区间／原条件文案，不添加进度或取消。本地locked只覆盖POST Promise等待；SUBMITTING期间禁用任务筛选／分页／选择／刷新／重试控件，页签及其他路由仍可切换。

DownloadView的combinedLocked=首次locked或重试locked，用于首次配置控件；handleSubmit在异步表单校验前后都检查combinedLocked，来源／API／参数变更处理器也检查。任务canExecute始终检查isDownloadLocked；首次执行期间任务仍可GET查看，但禁用重试并说明“已有下载正在执行”。两方向的保护都在事件／flow内验证，不能只依赖按钮disabled。无需修改useDownloadFlow状态机或增加全局槽位。

重试期间若浏览器后退／前进改变taskId，保留当前冻结执行与唯一Promise，仅记最新路由目标，结束后再消费该目标；不取消、不把A响应显示为B结果。先按A上下文完成响应核对和状态，再切换详情时按正常选择规则清理A的可见结果。等待中其他路由不触发额外GET或POST。

| 执行返回 | 展示及后续读取 |
|---|---|
| 合法200五outcome | 原样保留本轮结果，复用DownloadResult。执行等待结束后刷新一次当前列表；若result.taskId为原任务，另GET一次该详情，展示真实剩余项／新原因；若taskId=null且remaining=0，清理旧详情为NOT_FOUND并说明“本轮响应确认当前任务已无剩余失败项”，不补旧任务，仍以真实列表GET确认列表内容 |
| ApiError带合法UNCONFIRMED快照 | 保留快照、错误、null和实际taskId，展示此前已确认小计。令旧详情待刷新且不可执行，允许用户显式GET当前记录；不自动execute或补回原ID／明细 |
| ClientError TIMEOUT／NETWORK／INVALID_RESPONSE／UNEXPECTED | 本轮结果未确认，无虚构计数／任务。旧详情标记“记录可能已变化，请刷新详情”并禁用重试；用户显式GET后按新的canExecute决定是否能再次主动重试，不无限锁住页面，也不声称服务器已结束 |
| RETRY_TASK_NOT_FOUND 404（GET或POST） | 清理选中详情和旧执行权限，显示“任务不存在或已无剩余失败记录；查不到任务不代表某次无响应下载成功。”；触发一次当前条件列表GET，保留404说明和已有本轮未知结果。不新建／重插任务；列表GET失败只显示自己的读取错误 |
| DOWNLOAD_BUSY 409 | 显示实际错误，旧权限失效，禁用再次重试直到用户“刷新详情”获得新的可执行快照；不轮询、排队或计时自动恢复。GET的retrying表示该任务进程内重试中，其他任务占槽仅从blocker说明，均不是持久状态 |
| 其他无快照ApiError | 展示实际拒绝码／说明，保留只读详情但使执行权限待刷新；不删除记录。插件／参数／来源／日历不可执行均有说明，不根据retryable重发POST |

自动刷新仅是一次收到明确正常响应或404后的GET更新，不是轮询。刷新失败或详情读取不成功时不能继续使用旧canExecute，也不能用本轮failures代替数据库当前items。原始1～10日与当前3／7日例子：初次GET显示两组；重试后GET只返回7日则仅剩7日，原始区间仍1～10；不在前端根据S1猜是哪天成功。

DownloadResult仅为重试上下文增加两处文案分支，首次默认不变：UNCONFIRMED时将“重新提交当前条件属于新的首次下载……”换为“可刷新当前失败记录；再次手动重试只处理服务端当时仍保存的明细，不会恢复已删除项。”；无快照CALENDAR_UNCONFIRMED在重试上下文显示“未确认适用日历，本次未开始业务重试，原失败记录保留。”。计数、三组范围、确认taskId按钮与未知边界完全复用T16。结果的view-task只接受当前executionResult.taskId，进入相同query；不把执行上下文ID当作结果确认ID。

### 5. 列表与详情展示

RetryTaskList使用WorkbenchPanel和语义列表，避免宽表在窄屏溢出。props为state／result／error／selectedTaskId／page／pageSize／disabled；事件为`filter`（两个标识）、`page`、`page-size`、`refresh`、`select`（UUID）。内部仅保存尚未提交的两个筛选输入；使用带可见label的el-input，空白trim后省略，非空按现有标识正则校验、错误显示在字段下且零GET。两个筛选独立可用，允许离线／未知保存标识；不借用下载metadata下拉框。Enter或“筛选”应用一次，刷新使用已应用条件。

每行显示任务ID、插件／接口名及保存标识、**原始下载区间**、**当前失败范围**（完整failedScopes，不合并或按首尾缩写）、当前失败项数、创建／更新时间和“查看详情”。列表不提供未知的历史成功数、原因或公共参数字段；原因／条件只在详情显示。空列表标题精确“暂无失败任务”。分页直接复用现有el-pagination的20／50／100及prev／pager／next模式，在任务组件内标注“失败任务分页”，不修改DatasetPagination的默认50或查询ARIA。

RetryTaskDetail props为state／detail／error／disabled／canExecute／needsRefresh／executionMessage；disabled仅来自本地重试locked，首次下载占用仅禁用execute并展示executionMessage，不禁用GET刷新。事件`refresh`及`execute`。显示插件／接口保存身份、任务ID、原始区间、公共条件、当前失败项数、创建／更新时间和全部items（对象、时间、原因／错误码、该项更新时间），不分页明细、不叠加历史状态。公共条件从taskParams过滤start_date／end_date后按原顺序普通文本展示；为空显示“无已记录的公共条件”，不声称原任务必然没有条件。

范围沿T16：STOCK显示独立代码；REQUEST显示“原请求条件”并结合详情公共条件；DATE直接日期、MONTH附“完整月份”、RANGE将斜杠显示为“至”、NONE显示“原条件请求”。使用完整选择器作为DOM key，同日两股、稀疏3／7日分别成行。RECORDED原始日期仅按compact切片格式化，不经过本地时区转换；创建／更新用现有formatIngestedAt并标注“北京时间”。所有值Vue转义，禁止v-html、展开raw错误对象或把params写到URL。

不可执行仍展示全部记录：blocker的安全message／code、retrying提示、needsRefresh提示优先于通用按钮说明；“刷新详情”可在非本地执行时使用，canExecute=true也只是GET快照，不承诺实际来源支持。本轮结果放在单独WorkbenchPanel并注明执行任务ID，不能把N／S／R等塞入任务详情变成历史。

页面保留当前视觉变量、间距和按钮。失败任务列表／详情桌面两列，680px以下单列，本轮结果在详情之后；长ID、股票和原因任意换行、min-width:0。页签、筛选、查看、刷新及重试可键盘操作，状态沿AsyncStatePanel通告；不自动抢输入焦点，选中详情标题可用aria-labelledby关联。没有新全局主题／动画／组件库。

## Files

| 文件 | 责任 |
|---|---|
| `control-plane/src/api/retryTasks.js`（新）及相邻`retryTasks.spec.js`（新） | 三端点、任务wire守卫、实际Axios和零body／安全拒绝验证 |
| `control-plane/src/api/downloadResult.js`及相邻spec | 仅导出现有四字段范围守卫并验证，18字段行为不变 |
| `control-plane/src/composables/useRetryTaskFlow.js`及相邻spec（均新） | GET世代、选择／刷新、本轮重试上下文、互锁、404／409／未知结果 |
| `control-plane/src/components/download/RetryTaskList.vue`、`RetryTaskDetail.vue`及各自spec（均新） | 筛选分页、原始／当前范围、只读详情、按钮和安全文字 |
| `control-plane/src/components/download/DownloadResult.vue`及相邻spec | 重试上下文两处文案，复用T16计数／范围和确认ID入口 |
| `control-plane/src/views/DownloadView.vue`及相邻spec | 两页签、精确query消费、两个flow的本地互锁与非销毁生命周期 |
| `control-plane/src/layouts/AppLayout.spec.js` | 保持现有KeepAlive实现，补重试切页／返回与彻底卸载回归 |
| `control-plane/src/test/fixtures/retry-tasks.json`（新） | OpenAPI独立完整列表／详情、3／7→7、两股同日、原条件／旧记录／blocked样例，记录来源 |
| `docs/verification/RANGE-T17-retry-page.md`（新）、`docs/traceability/tensor-range-requirements.md` | 实际前端验证、六项AC增量及T18～T20边界 |

不删除文件，不修改后端、HTTP全局拦截器、错误目录、路由定义、AppLayout实现、T15表单／useDownloadFlow／查询或DatasetPagination实现。若验证揭示直接输入缺陷，先按证据修正本设计范围，不能静默放宽任务守卫或重建请求来绕过。

## Tests

启动时保存分支／HEAD、全部原索引、任务外文件及58生产资源摘要。整理独立OpenAPI fixture后先写行为RED：①T16 task入口后真实页面GET列表／详情，显示原1～10与当前3／7；②点击重试一次，实际Axios adapter只看到指定UUID的零data POST，响应及后续GET让页面仅剩7日；③重试超时无自动重发、无伪造计数，刷新404只显示实际空列表和未知边界。导入缺文件失败单列，最小导出后的行为断言失败另记；再实现最小改动。

| 组 | 固定场景与预期 |
|---|---|
| 任务wire／安全 | 精确11／17／6／7字段、null显示名、严格UUID／日期／UTC毫秒、整数及计数／分页一致、完整选择器唯一、详情范围相符；坏类型／数组标识／字符串数／新增字段／坏日期／非法taskParams／ID错配／不一致blocker均安全INVALID_RESPONSE，不回显正文哨兵 |
| API与分页 | 独立pluginId-only／apiName-only／combined／无筛选、空白省略、离线及未知标识；默认20、切50／100、越界末页、空page1、双排序不重排；GET不得POST，真正execute data undefined／浏览器零字节，不发送{}／null／params或原始日期 |
| 原始与当前 | 原20260901～20260910、REQUEST 3／7，重试响应后GET仅7且原区间不变；同日A／B股票一项移除后另一项保留；MONTH／RANGE／NONE、RECORDED／NOT_APPLICABLE／NOT_RECORDED／UNCONFIRMED、名称缺失及不可执行记录可读 |
| 本轮结果 | 复用六outcome含合法空和全闭；正常／错误快照核对plugin／API／task identity；UNCONFIRMED null／确认小计／taskId=null不补原ID，重试文案不指引首次下载；刷新失败不覆盖本轮结果，不把历史GET当本轮计数 |
| 刷新与消失 | PARTIAL后一次列表＋详情GET；最后项确认解决一次列表GET并清理详情；GET／POST404各一次列表更新，列表更新失败不假空；读取到旧详情后POST409／404仍安全。busy／插件／参数／来源／日历blocker禁用且记录保留，刷新后只按新快照启用 |
| 通信／并发 | 四类ClientError零自动重发，四停止ApiError快照保留；详情必须显式GET刷新后再手动execute。首次与重试双向本地互锁、快速双击及异步表单校验竞争只一POST；请求结束不等于服务端结束，409不轮询 |
| 路由／生命周期 | T16键盘入口只GET一次列表及详情、列表select不重复GET；坏query零详情GET；返回默认页签、切设置／数据查看／返回保留同一重试和唯一晚到响应；在途taskId变更后A响应不挂B；卸载重挂只GET，无Web存储／历史恢复 |
| UI回归 | 两筛选不改首次表单，筛选与pageSize回1，generation阻止旧GET覆盖；窄屏长原因／ID换行、键盘操作、GET独立错误重载；T15真实49项及T16／查询／精确数值／主题回归仍通过 |

在`control-plane/`使用Node24.15.0，依次执行：

```sh
npm test -- src/api/retryTasks.spec.js src/api/downloadResult.spec.js src/api/api.spec.js src/api/errors.spec.js src/composables/useRetryTaskFlow.spec.js src/composables/useDownloadFlow.spec.js src/components/download/RetryTaskList.spec.js src/components/download/RetryTaskDetail.spec.js src/components/download/DownloadResult.spec.js src/views/DownloadView.spec.js src/layouts/AppLayout.spec.js
npm test
npm run build
```

各命令exit0、零失败／跳过，记录实际数量而非照抄T16的278。根目录执行`PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py`、`git diff --check`、`git diff --cached --check`及开始时摘要保护；仅stage新增正式文件，保留原索引。

对本轮最终构建做Chromium1440及390宽度受控HTTP页面观察，真实任务入口→原1～10／剩3、7→键盘直接重试（捕获零字节POST）→GET仅7→最后解决空列表；另观察两股同日、未知／404／409、切页同一请求和长范围。截图与构建摘要／请求日志对应，无横向溢出及非预期console/pageerror。服务受控响应不连接真实Tushare，不替代T19完整后端闭环或T18真实进程／socket中断。独立规格／质量与最终集成／证据评审通过后记录完成。

## Acceptance

1. T16精确taskId入口真实打开失败任务页签及详情；独立筛选、默认20与50／100分页、离线标识、空列表、GET故障可重载，未发未经点击的POST。
2. 列表／详情明确区分原始区间与当前范围：1～10和3／7，部分解决后原始不变且只剩7；两股同日不合并，三种缺原始日期说明准确；不恢复历史计数。
3. “重试一次”直接发一个UUID零字节body POST，不发送原表单／区间／公共条件；GET快照只决定按钮，409／404及其他拒绝仍按真实响应处理，不重建任务或排队。
4. 首次与重试互锁、切页保持Promise和身份、通信未知无自动重发；本轮结果原样显示，刷新GET仅说明现存记录，查不到不推断无响应请求成功。
5. 完成项和最终任务移除来自明确执行响应及实际后续GET，不从S／F猜具体明细；保存／提交未知、读取失败和不可执行原因均可见，不把旧权限当作新许可。
6. 指定测试、全量、build、静态合同、桌面／窄屏观察、差异保护及独立评审实际通过，报告六项AC前端证据。先记录T17完成再准备Order后继，不能自动实施T18或提前宣称后端／真实来源验收。

## Risks

- 无待决产品事实；任务页结构、接口、状态、错误分支与验收已固定。GET和POST之间槽位／记录仍可能变化，这是既有合同，不能靠自动刷新或浏览器锁保证永久幂等。
- OriginalDateRange与当前失败范围来自不同事实。旧记录可能没有原始两端或元数据，仍须显示记录；安全投影为空不表示原条件本来为空，不从当前metadata／表单补造。
- GET快照不含mode，不含成功计数；retrying／canExecute／blocker不持久。空body需实际Axios和浏览器双层证据，方法名叫execute并不能证明没有发送JSON。
- T16共享组件仅允许上述重试文案和范围守卫导出；首次下载、查询及安全边界必须保留回归。请求未确认后是否仍执行只能由服务端判断，解除本地等待不代表槽位释放。
- T18生产fixture批次及后端故障、T19完整浏览器、T20真实来源尚未在本项验收；生产日历／完整来源仍保守关闭，ISSUE-008九项继续不依赖、未解决。既有Vite大chunk提示不在任务页优化范围。
