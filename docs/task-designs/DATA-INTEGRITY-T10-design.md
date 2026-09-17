# DATA-INTEGRITY-T10 前端请求、精确 DTO 与检查状态

## Goal

为后续创建/历史/详情页面提供六个完整性API封装和可测试的提交、恢复、轮询状态。保留报告的精确计数、未知值及当时口径；响应丢失、慢请求与路由切换不产生重复检查或旧响应覆盖。

## Scope

新增 `control-plane/src/api/integrityChecks.js`、`integrityDtos.js`、`composables/useIntegrityCheck.js` 及对应 `.spec.js`；在既有 `api/errors.js` 和 `api/api.spec.js` 补齐五个完整性错误。复用HTTP实例、requestId和无损JSON读取，不更改下载状态机。不添加页面、导航、路由、可视化、后端接口或规则；T11/T12消费本项公开方法。历史/问题列表请求由API封装提供，只有详情和当前可见结果页进行定时轮询。

## Approach

### 1. 权威输入与模块边界

HTTP字段以T09已验收的 `docs/contracts/integrity-check.schema.json`、`integrity-check-examples.json`、`openapi-v1.yaml` 为准；专属设计 `DATA-INTEGRITY-T09-design.md` 第2–4节说明绑定、精度与历史行为。共享设计第2、5、8、9节约束交互。本项不把后端Repository记录或当前插件定义当成前端合同。

`integrityDtos.js` 集中纯验证/解析、提交快照函数；`integrityChecks.js` 负责路径/筛选/HTTP响应校验；`useIntegrityCheck.js` 负责能力确认、提交恢复及详情/结果轮询。返回只读快照，Vue状态替换整个快照，不改写旧报告。校验失败沿用 `ClientError('INVALID_RESPONSE', requestId)`，调用前非法参数抛TypeError且不发请求。不引入通用schema框架或新增依赖。

### 2. 六个API封装

每个函数最后一个参数为 `{ signal } = {}`；沿用 `http` 的 `/api/v1` baseURL和每次请求的新X-Request-Id。使用 `responseType:'text'` 与既有 `parseTaskJson`，保证嵌入的下载 `rowLimit` 或业务键JSON整数在解析前不被Number舍入。成功响应必须校验HTTP状态、响应X-Request-Id等于本次请求ID及DTO形状。

| 导出方法 | 请求 | 返回 |
| --- | --- | --- |
| `getIntegrityCapabilities(pluginId, options)` | GET `/data-sources/{pluginId}/integrity-capabilities` | Capability |
| `submitIntegrityCheck(request, options)` | POST `/integrity-checks` | Receipt，200或202 |
| `listIntegrityChecks(criteria = {}, options)` | GET `/integrity-checks` | TaskPage |
| `getIntegrityCheck(checkId, options)` | GET `/integrity-checks/{checkId}` | Detail |
| `listIntegrityResults(checkId, criteria = {}, options)` | GET `/integrity-checks/{checkId}/results` | ResultPage |
| `listIntegrityIssues(checkId, criteria = {}, options)` | GET `/integrity-checks/{checkId}/issues` | IssuePage |

GET只接受200。POST直接发送已验证原快照，不补apiNames、不排序数组、不改符号/哈希；校验回执submissionId/pluginId对应原请求、Location为 `/api/v1/integrity-checks/{checkId}`，正文requestId等于本次请求ID。重放可返回任何合法已存任务状态，不要求QUEUED。详情checkId须与路径一致，结果项checkId须属于路径；能力pluginId须匹配请求。列表page/pageSize必须与请求相符。

`validateIntegrityCriteria(kind, criteria)`（kind为tasks/results/issues）和 `validateIntegrityCheckId(value)` 作为纯参数校验器供composable复用，由API模块重导出。三个列表默认page=1/pageSize=20；page为1..2147483647的整数，pageSize为1..100的任意整数，不复用下载20/50/100白名单。criteria只能包含以下字段和page/pageSize，undefined表示省略，null/空串/未知键拒绝。用URLSearchParams序列化，保留历史symbol的原始非空字符串（最多255 Unicode码点），不trim或按当前股票格式校验。

- 历史：pluginId、status、submissionId。status只用检查任务枚举。
- 结果：symbol、apiName、overallStatus。
- 问题：resultId、symbol、apiName、type、status、dateFrom、dateTo。日期为1000..9999的真实ISO日期，允许单边，双边须有序；不为date=null的问题补日期。

UUID接受完整标准格式，路径规范为小写；identifier沿用 `[a-z][a-z0-9_]{1,63}`。按submissionId查回时固定page=1/pageSize=20且不附加执行状态：仅接受total=0且items=[]，或total=1且唯一记录ID相符；不能取一个不匹配或多条响应的首项当恢复成功。

### 3. 精确DTO与状态

导出 `parseIntegrityCapabilities`、`parseIntegrityReceipt`、`parseIntegrityTaskSummary`、`parseIntegrityDetail`、`parseIntegrityResult`、`parseIntegrityIssue` 及 `parseIntegrityTaskPage/ResultPage/IssuePage`，签名均为 `(value, requestId)`。嵌套字段逐项按schema验证并复制冻结；未知/缺失固定字段、非法类型/枚举/日期/比例返回INVALID_RESPONSE，不凭空补默认字段。

| 字段类别 | 前端保留方式 |
| --- | --- |
| total、issueId、completedUnits/errorUnits/notRunUnits、statusCounts各项、statistics七项计数、limits的三个Long项 | wire必须是非负规范十进制字符串，转BigInt；可空项的null保留。拒绝数字token、负数、小数和指数；不经Number中转。 |
| page/pageSize、plannedUnits、limits其余int项 | 按schema检查范围，保留安全Number。 |
| coverageRate | null或 `0.xxxxxx` / `1.000000` 字符串；不用于判定完整性，不在DTO层转百分比。 |
| 时间、范围、定义/规则版本、原因、evidence | 原字段/字符串保留；日期与UTC时间有效性需校验，不使用浏览器本地时区改写。 |
| businessKey | 保留所有键及标量类型；字符串DECIMAL/Long不自动转型。JSON整数用无损解析后的安全Number/BigInt，禁止取子集或拼显示串。 |
| originalRequest/scope | 分别校验并保留，apiNames是否省略与数组顺序不变；不能用规范scope覆盖原请求。 |
| downloadAvailability | 复用 `parseDownloadCapabilities`；其rowLimit仍是下载合同的JSON整数及现有BigInt映射，不能要求它变为检查计数字符串。 |

任务状态严格为QUEUED/RUNNING/COMPLETED/FAILED/INTERRUPTED；单元状态为PENDING/RUNNING/COMPLETED/ERROR/NOT_RUN；数据结论为PASS/FAIL/WARN/UNKNOWN/NOT_APPLICABLE。三者分别解析，不接受下载任务的SUCCEEDED/PARTIAL_FAILED，不把COMPLETED或200映射为PASS。

Detail保留TaskSummary字段并添加进度/overallStatus/statusCounts，五个结论计数键必须齐全；completedUnits已经包含errorUnits，不能再相加。历史TaskSummary**没有overallStatus**，不得伪造PASS/UNKNOWN或为每条历史隐式请求详情。

Result保留report的scope、descriptor（可null）、definitionHash、publishedRange、五项执行/数据状态、statistics、ruleResults、evidence、finishedAt、incomplete/issuesComplete、reasonCode/message；Issue保留ruleId/version及全部定位字段。按保存版本解释，无当前能力请求；不按Tushare、特定ruleId或版本做分支。NON_STOCK、未实现descriptor=null、null日期和不完整报告均可正常解析。

错误表新增：INTEGRITY_UNAVAILABLE=[409,false]、INTEGRITY_DEFINITION_CHANGED=[409,false]、INTEGRITY_CHECK_NOT_FOUND=[404,false]、INTEGRITY_QUEUE_FULL=[429,true]、INTEGRITY_LIMIT_EXCEEDED=[400,false]。保留既有错误码行为及安全消息，requestId存于ApiError/ClientError，不把底层Axios错误展示给调用方。

### 4. 不可变提交和能力确认

`integrityDtos.js` 导出 `createIntegritySubmission(selection, submissionId = crypto.randomUUID())`：验证请求schema，复制冻结symbols及可选apiNames和对象；仅包含submissionId/pluginId/capabilityHash/symbols/startDate/endDate及实际存在的apiNames。不在此层做Tushare大小写/去重/默认全选规范化，不生成新日期范围。

`useIntegrityCheck({ storage = globalThis.sessionStorage } = {})` 提供以下创建状态与方法：

- 状态：capability、capabilityLoading、capabilityError、confirmedCapabilityHash；pendingSubmission、submissionState、submissionError、recoveryError、storageError、receipt、recoveredTask、canResend。recoveryError保存查回GET失败；storageError单独保存会话读写失败，不能覆盖最初POST错误/requestId。默认sessionStorage getter也在受控错误处理内访问。
- 方法：`loadCapabilities(pluginId)`、`confirmCapability(hash)`、`prepareSubmission(selection)`、`submit()`、`recoverSubmission()`、`resendSubmission()`。方法返回Promise或同步校验结果，不执行路由跳转。
- `loadCapabilities`只GET，来源或hash改变清除确认；`confirmCapability`只接受当前已加载且localCheckAvailable=true的hash，表示上层明确确认了口径。`prepareSubmission`是用户明确开始新检查的调用，只有其可生成新ID；要求来源/hash与已确认能力一致。锁定submitting/recovering或尚未查清的uncertain状态，防止编辑/重复点击替换原快照。
- `submit`仅提交已准备快照。请求前将 `{schemaVersion:1,request}` 写入独立key `tensor.integrityChecks.pending.v1`；复用下载utility的存储模式，不复用其key或请求字段。存储失败保留错误且不发POST，避免失去恢复载荷。加载缓存时严格校验，损坏数据显式报错，不生成替代ID或自动POST。
- submitted成功校验回执后置accepted，保留receipt或恢复记录；只清除相同submissionId的待确认缓存。accepted不触发第二次POST。prepare新检查后调用submit才产生下一项。

submissionState固定为idle/prepared/submitting/recovering/uncertain/accepted/rejected/definition-changed。响应不确定包括网络/超时/无法验证成功响应及服务端PERSISTENCE_FAILED/QUERY_FAILED/INTERNAL_ERROR；此时保留原快照及最初submissionError（含requestId），进入recovering并**先GET历史submissionId**。找到唯一匹配任务即accepted；空页仍为uncertain、允许明确resend；恢复GET失败保持uncertain、保留恢复错误/requestId且canResend=false。`recoverSubmission`可再次GET，绝不POST。

`resendSubmission`仅在最近成功查回为空且没有进行中的操作时允许，发送同一submissionId和逐字段相同的冻结请求；恢复失败或忙碌时不发送。所有POST必须来自submit/resend显式调用；不使用通用retryable自动重发，不因重连或挂载自动POST。缓存中的未确认提交恢复时先GET。确定的4xx拒绝置rejected（包含SUBMISSION_CONFLICT），仍保留请求/错误供核对；不能通过自动换ID解决冲突。

收到INTEGRITY_DEFINITION_CHANGED置definition-changed，清除确认并重新GET当前能力，保留旧载荷，禁止把新hash写入旧请求并重发。上层重新展示口径并调用confirmCapability后，由用户明确新检查调用prepareSubmission生成新ID/快照。查询旧报告/查回旧ID不受当前hash/Token变化阻挡。能力请求与提交各有generation/AbortController，来源切换或卸载后的响应不得覆盖当前状态。

### 5. 详情与当前可见结果页轮询

同一composable提供checkId、detail、results、resultsCriteria、detailError、resultsError、loading、connected及以下方法：

- `load(checkId)`：校验ID，切换时递增generation、取消旧GET/定时器、清空旧任务快照及结果筛选（默认1/20），立即GET详情与可见结果页。同ID调用合并为一次refresh，不创建POST。
- `changeResults(criteria)`：按结果criteria完整替换当前筛选/分页，默认1/20；generation更新并取消旧结果请求，立即查询当前页。迟到页结果不覆盖新criteria。筛选的UI默认/重置由T12调用方负责。
- `setResultsVisible(boolean)`：只在true时请求结果页；详情继续查询。默认true。不为不可见页、全部历史或问题页建立轮询。
- `refresh()`：当前有效任务的一次GET轮次；`reconnect()`：显式清除连接中断并执行相同GET轮次，成功后恢复正常轮询。两者不调用提交逻辑。
- `setActive(boolean)`：路由离开时false，取消GET并停止定时器；重新进入由页面load或setActive(true)发GET。`dispose()`幂等清理所有控制器和定时器，增加generation，阻止后续状态写入；在Vue作用域存在时注册onScopeDispose，测试可直接调用dispose。

每轮并行GET详情和当前可见结果页并分别保存成功快照；轮次全部settle后，只有当前generation、仍active、无查询错误且详情为QUEUED/RUNNING才以setTimeout安排下一轮2000ms。使用请求槽/Promise复用保证慢于2秒的同一请求不重叠；意图改变时abort，但在finally结算前不把相同请求当作空闲。所有then/catch/finally写入前同时核对checkId、generation和所属请求，旧请求失败也不能覆盖新状态。

COMPLETED/FAILED/INTERRUPTED停止后续定时器；本轮已发的可见结果GET结算后不再自动GET。任一当前查询失败保留已展示的同任务/同criteria数据及对应error/requestId，置connected=false并停轮询，不指数退避自动重试。重新连接只执行GET，失败仍停，成功且任务活跃才恢复2秒轮询。取消/过期请求不产生可见连接错误，不清除新请求loading。INTEGRITY_CHECK_NOT_FOUND也停止轮询并暴露明确错误。问题页通过 `listIntegrityIssues` 按需查询，由T12拥有其页面生命周期，不在此任务增加隐藏轮询。

## Files

- 新增 `control-plane/src/api/integrityDtos.js`：固定公开DTO、数值/状态校验、不可变提交快照。
- 新增 `control-plane/src/api/integrityChecks.js`：六API、筛选参数及响应/requestId/Location校验。
- 新增 `control-plane/src/composables/useIntegrityCheck.js`：能力确认、会话待确认载荷、提交查回及GET生命周期。
- 新增上述三个模块的同目录 `.spec.js`：独立合同示例、真实HTTP adapter与fake timer/deferred Promise测试。
- 修改 `control-plane/src/api/errors.js` 和 `control-plane/src/api/api.spec.js`：五种完整性错误与状态/retryable组合。
- 新增 `docs/verification/DATA-INTEGRITY-T10.md`，更新既有任务看板与完成后的后继交接。不得提前实现T11/T12页面。

## Tests

首个RED：在 `integrityDtos.spec.js` 用T09结果示例把actualCount改成字符串 `9223372036854775807`，expectedCount/coverageRate设null；断言得到精确BigInt和null，数字token计数拒绝；先建立最小可调用导出，确认断言因精度/空值解析尚未实现而失败，不把导入错误当作RED，然后实现解析。

必须覆盖：

1. T09全部15示例按其schema类型解析，补齐任务/单元/结论全枚举，descriptor=null/NON_STOCK、FAILED/INTERRUPTED/NOT_RUN、null日期、旧规则、完整多字段键及incomplete/issuesComplete；未知/缺字段/非法日期/比例/类型拒绝且requestId保留。计数0、超过安全整数、Long.MAX_VALUE、null各自正确；下载rowLimit的原始JSON大整数无损。
2. 六端点路径/方法/全部筛选、默认/1/37/100分页和越末页空；非法ID/未知键/日期反向/枚举/页数在发送前拒绝。历史symbol原样编码。200/202、Location、请求/正文/响应requestId、身份/页数不一致及5种错误映射；原apiNames省略与有序数组未改写。
3. POST双击只一次、冻结后修改表单不影响载荷、首次成功/完成态重放、超时后先GET找回、空结果明确同ID重发、恢复GET失败不能POST、缓存恢复不自动POST、缓存损坏/写入失败、SUBMISSION_CONFLICT不自动换ID。能力变化刷新但不自动提交，新口径必须确认；明确再次检查才新ID，旧hash恢复不被当前能力阻挡。
4. fake timers验证立即GET、完整轮次settle后2000ms、慢请求不重叠、隐藏结果页只查详情、筛选和页切换、所有终态停止、任一GET失败保留数据/requestId并停止、重连只GET、再次失败仍停。deferred Promise覆盖任务A迟于B、同任务旧页晚返回、旧错误和旧finally、路由离开/卸载abort且无计时器泄漏。
5. 既有API、下载请求/DTO/恢复/轮询测试及完整前端回归保持通过。

使用项目Node24.15.x；在隔离区根目录执行：

```sh
npm --prefix control-plane test -- src/api/integrityDtos.spec.js src/api/integrityChecks.spec.js src/composables/useIntegrityCheck.spec.js src/api/api.spec.js
npm --prefix control-plane test
npm --prefix control-plane run build
```

预期全部通过、无失败/跳过且构建成功；实际数量/命令/结果写入T10验收文档。无需为本项启动真实后端或改后端；T13的真实浏览器闭环不能用这些adapter单测冒充。

## Acceptance

六API与T09字段/精度/错误完全兼容；历史和问题保留原报告，不重新解释UNKNOWN/N/A。不可变提交、能力确认、submissionId查回及同ID显式重发可由测试证明；GET重连不产生POST。2秒轮询无重叠，终态/路由/卸载结束生命周期，所有旧响应被拒绝；查询失败有保留数据和requestId的可重连状态。专项、完整前端测试及构建通过，新增文件加入Git，验收证据回填；T10须经用户显式启动才实施。

## Risks

- 现有下载轮询在查询失败后自动退避重试，与本项明确停轮询不同；仅复用HTTP/解析工具，不复制下载状态机。
- T09历史TaskSummary不含结论汇总；T10保持该事实，T11不能据COMPLETED推导PASS。
- Axios取消会经过现有错误规范化；必须先以signal/generation识别取消，不能误报UNEXPECTED或触发提交重发。
- sessionStorage不跨标签页同步；同一冻结submissionId的幂等仍由后端保证，本项不增加跨标签广播协议。
- 原范围/股票字符串/数组次序和apiNames省略都是重放身份的一部分；任何自动“整理”都会影响幂等。
- 无未解决的直接依赖冲突；T09已验收的API可用，clean-main全量发布合同门禁仍由T13承担。
