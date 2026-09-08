# RANGE-T03 HTTP 合同、错误码与需求追踪设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md) `RANGE-T03`：将已确认的区间下载、当前失败记录及手动重试语义固定为可校验的 OpenAPI 3.1 合同、错误目录和29项AC增量追踪。T04及后续实现直接消费字段、约束与示例，不再选择对外协议。

## Scope

- 更新 `docs/contracts/openapi-v1.yaml` 和 `docs/contracts/error-codes.md`；创建 `docs/traceability/tensor-range-requirements.md`、`docs/contracts/verify_range_contract.py` 及 `docs/verification/RANGE-T03-contracts.md`。
- HTTP外层、参数命名、原条件、精确数值及只读查询保持既有合同；为38项区间目标、11项原条件、元数据投影、首次／重试本轮结果、失败查询和拒绝边界建立共同定义。
- 不修改Java、Vue、Dataset YAML、数据库或运行配置；不运行服务或真实API。T03的合同文件是后续目标，报告明确“运行接口尚未迁移”。不增加取消、自动重发、永久幂等、历史、父子任务、预登记或查询筛选能力。
- 本设计由T02完成后的后继准备流程交付。T03仍须收到启动请求并按看板进入IN_PROGRESS后，才能实施上述文件修改。

## Approach

### 1. 输入、现状及证据边界

按看板顺序消费：① [PRD v1.4 §5—10](../design/Tensor_区间下载_PRD_v1.0.md)，参数清单补读§3及附录；② [TRD v1.10 §4、§6.4、§8—12](../design/Tensor_区间下载_TRD_v1.0.md)，选择器补读§7.1；③既有OpenAPI及错误目录；④[平台追踪索引](../traceability/tensor-v1-requirements.md)；⑤[T01结论](../research/RANGE-T01-source-capabilities.md)／[JSON](../research/RANGE-T01-source-evidence.json)和[T02结论](../research/RANGE-T02-calendar-capabilities.md)／[JSON](../research/RANGE-T02-calendar-evidence.json)。不重写历史追踪结果。

当前OpenAPI只有六类既有路径，下载示例为daily.trade_date，DownloadResponse仅SUCCESS／EMPTY和R／I／U；ApiDescriptor没有downloadPolicy，错误目录没有日历、完整性或失败任务代码。运行DTO与该旧合同一致。新协议仍用 `/api/v1`，所有响应有X-Request-Id，JSON错误／结果中的requestId与头一致。

T01的40个DOCUMENTED_CANDIDATE只是合法请求候选，8个缺依据、forecast冲突；全部49项恢复暂为有整体取全前提的REQUEST。T02补充1项静态DOCUMENTED、16项未确认、2项冲突；hsgt_top10仍真实排除。T02对monthly样例的冲突说明补充T01文档候选的限制，不更改月线产品目标。两份输入统一收敛为：Q合法方式先确认，K必要日历全部确认，C整体取全后才处理，R独立恢复缺依据用REQUEST。不存在要求T03擅自改变产品范围的决策冲突。

### 2. 请求及49项约束

`DownloadRequest`继续为严格对象 `{pluginId, apiName, params}`，三字段必填，禁止其他外层字段。两个标识沿用 `^[a-z][a-z0-9_]{1,63}$`；params为对象，值只能为字符串。不能把表单区间直接解释成上游支持起止参数。

为Tushare增加条件分支：当pluginId=tushare_pro时，apiName必须属于下表49项，按apiName用JSON Schema的if/then应用严格params形状。复用参数schema，避免复制49个对象。其他插件仍由自己的ApiDescriptor校验，保留既有fixture的scenario合同，不把生产49枚举套到所有插件。

定义以下params组件，全部 `additionalProperties:false`，表中字段全部必填。日期值为字符串 `^[0-9]{8}$`，不能标成ISO `format:date`。股票沿用现有单值规范化（trim、大写）与 `[A-Z0-9]+\.[A-Z0-9]+`；HTTP schema使用 `^\s*[A-Za-z0-9]+\.[A-Za-z0-9]+\s*$` 允许规范化前大小写、首尾空白，但不接受逗号多股，说明语义校验由绑定器执行，不能额外限定六位A股。枚举沿用当前校验器，不扩选项。

| params schema | 字段 | apiName全集 |
|---|---|---|
| RangeParams | start_date,end_date | adj_factor, block_trade, daily, daily_basic, hk_hold, hsgt_top10, margin_detail, moneyflow, moneyflow_hsgt, monthly, slb_len, slb_sec, slb_sec_detail, stk_limit, suspend_d, top_inst, top_list, weekly；disclosure_date, dividend, express, forecast, repurchase, share_float, stk_holdertrade, top10_floatholders, top10_holders；broker_recommend；namechange,new_share |
| StockRangeParams | ts_code,start_date,end_date | balancesheet,cashflow,fina_audit,fina_indicator,fina_mainbz,income |
| ExchangeIdRangeParams | exchange_id,start_date,end_date；exchange_id枚举SSE/SZSE/BSE | margin |
| ExchangeRangeParams | exchange,start_date,end_date；exchange枚举SSE/SZSE/BSE | trade_cal |
| EmptyParams | 无，精确{} | index_classify,index_member,index_member_all,pledge_detail,pledge_stat,stk_managers |
| StockParams | ts_code | stk_holdernumber,stk_rewards |
| HsTypeParams | hs_type，SH/SZ | hs_const |
| ListStatusParams | list_status，L/P/D | stock_basic |
| ExchangeParams | exchange，SSE/SZSE/BSE | stock_company |

模式仍为19 TRADE_DATE_RANGE、15 ANN_DATE_RANGE、1 MONTH_RANGE、3 NATIVE_RANGE、11 ORIGINAL_PARAMS，与T01 JSON精确对应。请求形状声明为目标合同，不因来源缺口偷偷删除接口或新增必填股票。

所有38项的日期按严格公历、两端包含、start<=end且最多31自然日校验，先于日历／月份展开；缺失无默认，31允许、32拒绝。JSON Schema只保证形状，不能用正则冒充公历／日期差值校验；这些规则写入description和校验脚本的独立语义检查。新增区间项带旧trade_date／ann_date／month，无论单独还是混用，均PARAM_INVALID；缺必填为PARAM_REQUIRED；非法公历、单端、逆序、未知字段／参数或非字符串值按现有PARAM_INVALID／PARAM_REQUIRED语义。11项增加日期也拒绝。

OpenAPI新增文档扩展 `x-tensor-range-targets`：49行，每行严格为 `{apiName, mode, paramsSchema, calendarProfile, liveExcluded}`；来源为上述清单及T01/T02，非交易日期calendarProfile=null。它仅供合同核对，不是生产策略资源，不放可执行=true或分页配置。

### 3. 元数据投影

`ApiDescriptor`增加必填downloadPolicy，原parameters数组改为下载投影（38项日期替换为start/end两个DATE_RANGE_MEMBER并互相relatedParameter；原附加条件保留且顺序在日期前；11项原数组保留）。queryMode、数据集描述和records端点均不变。

公开 `DownloadPolicy` 严格对象，全部字段必填：

| 字段 | 类型和语义 |
|---|---|
| mode | 上述五个模式枚举，表示目标表单，不代表来源已可执行 |
| dateSemantic | `TRADE_DATE / ANN_DATE / COVERED_MONTH / CALENDAR_DATE / IPO_DATE / NONE`；前三分别前三类，trade_cal=CALENDAR_DATE，new_share=IPO_DATE，namechange=ANN_DATE，原条件=NONE |
| description | 非空安全文案；交易日期说明依适用日历，公告不等于报告期，月份覆盖完整月；原生文案只说已公开语义，附“实际筛选与完整性仍需来源核实”，不宣称已实测 |
| calendarProfile | C-A/C-M/C-S/C-N/C-X或null；仅19项非null |
| limits | 38项为严格 `{maxRangeDays:31}`，11项为null |

sourceRequestMode、sourceDateParameter、batchPlanning、completenessPolicy、recoveryPolicy及证据URL留在插件内部，不要求浏览器根据它们组批。DataSourceSummary.downloadAvailable仍只反映既有插件／凭证配置条件，不能代表49项来源或日历通过。界面可展示未确认接口，运行时以明确拒绝错误为准。

### 4. 公共选择器、错误明细及本轮结果

复用数据库已确认的对象／时间表达为严格 `RecoveryScope`：必填targetType、targetValue、timeType、timeValue。无需额外unitId、状态或版本。

| 字段 | 约束 |
|---|---|
| targetType | STOCK或REQUEST |
| targetValue | STOCK为规范单值代码（非空、最多64字符、无逗号）；REQUEST严格空字符串 |
| timeType/timeValue | DATE=`YYYY-MM-DD`；MONTH=`YYYY-MM`；RANGE=`YYYY-MM-DD/YYYY-MM-DD`且两端有序、真正多日；NONE严格空字符串。日期严格公历；单日原生范围也保存DATE，来源参数仍由策略还原两端相同 |

用if/then约束类型和值、oneOf区分时间；不同股票同日始终不同scope。`RecoveryFailure`在scope四字段上增加errorCode（非空字符串、最多64）、errorMessage（非空安全摘要、最多512），不包含来源原文。记录旧错误码可显示而不要求仍属于当前枚举；本版生成的错误码必须在目录中。避免在additionalProperties:false的scope外使用无法扩展的allOf：实现复用properties的YAML锚点，在两个对象各自应用类型条件并封闭。

`DownloadResponse`保留原字段名，扩展outcome并增加下列全部必填字段；不保存本对象到任务表：

| 字段 | 类型及精确口径 |
|---|---|
| outcome | SUCCESS / EMPTY / NO_OPEN_DATES / PARTIAL / FAILED / UNCONFIRMED；规则见下文 |
| completedUnits | 非负integer/int64，S：已确认完整提交或合法空的恢复单元；已过滤休市日期不算完成单元 |
| failedUnits | 非负integer/int64，F：本轮已明确失败单元，不把未知／未执行算失败；保存结果单列 |
| notStartedUnits | 非负integer/int64或null，N：仅已知恢复单元集合时计数，未知为null；0仅表示确认没有未开始单元 |
| skippedClosedDates | 非负integer/int64，H：本轮已完整确认并跳过的自然日期数；同日多股只计一次 |
| taskId | UUID字符串或null；仅有已确认保存且尚有失败明细的任务时返回；全成功／全部解决为null；保存或删除提交未知且无法确认仍存在时为null，不能用预生成ID当保存证明 |
| remainingFailedUnits | 非负integer/int64或null；本轮结束时数据库已确认的当前剩余失败项数，不等于F；无任务为0；保存或提交未知无法确认时为null |
| failureRecordStatus | NOT_REQUIRED / CONFIRMED / UNCONFIRMED；无须保留失败记录、记录及删除结果已确认、最新记录或删除结果未确认；不是持久化状态 |
| failures | RecoveryFailure数组，仅本轮明确失败的对象时间及安全原因；可能未保存，须结合failureRecordStatus；不作为任务查询的替代 |
| notStartedScopes | RecoveryScope数组，本轮确定未开始的实际范围，可为尚未解析独立单元的REQUEST范围；这些范围不入失败表，数组长度不推算N |
| unconfirmedScopes | RecoveryScope数组，已执行但提交等结果未知的实际范围；与已完成／失败范围不重叠，不入失败表，不用数组长度猜恢复单元数 |

保留 `sourceRowCount/insertedRows/updatedRows` 为非负integer/int64，分别R/I/U，只表示已确认完成单元的本轮合计。发生某单元提交未知时，保留此前可证明的合计且标UNCONFIRMED和具体范围；这些数是“已确认”小计，不把未知单元写成成功0、失败0或整轮总计。不新增推测的totalUnits；只有集合明确且无未知时才可按S+F+N核对，休市过滤在此集合之前。

正常完成HTTP200：notStartedUnits=0、notStartedScopes=[]、unconfirmedScopes=[]、failureRecordStatus!=UNCONFIRMED。SUCCESS为F=0且R>0；EMPTY为F=0、存在完成单元且R/I/U=0；NO_OPEN_DATES为所有实际待处理日期都确认休市、S/F/R/I/U=0且H>0；PARTIAL为S>0且F>0；FAILED为S=0且F>0。PARTIAL／FAILED要求失败保存已确认并有taskId、remainingFailedUnits>0，失败数组与F按明确单元一一对应。合法空与失败混合为PARTIAL。

UNCONFIRMED仅出现在错误响应携带的本轮结果里，不作为HTTP200结果：记录保存、业务提交或删除提交未知时，即使此前有确认成功也使用此outcome。已确认业务失败后保存失败，F与failures可保留该事实，但failureRecordStatus=UNCONFIRMED；业务提交未知不增加S或F，而列入unconfirmedScopes。数据库不可用且停止后续也走错误；若当前已确认回滚且失败已保存，可携带PARTIAL／FAILED的已确认小计与非空notStartedScopes，但不得混入正常200 schema。

为避免一个组件承担两种约束，抽出 `DownloadExecutionResult` 定义上述全字段，`DownloadResponse`通过约束限制HTTP200的outcome和完成状态；`ApiError`可选增加 `downloadResult` 引用DownloadExecutionResult（未开始业务的错误省略）。error.requestId、downloadResult.requestId及响应头相等。错误携带结果不撤销此前已提交单元。

### 5. 失败任务HTTP与查询形状

新增端点及operationId：

| 路径 | 方法／operationId | 输入与响应 |
|---|---|---|
| /api/v1/retry-tasks | GET listRetryTasks | 可选pluginId、apiName（沿用标识格式，可单独筛选；不因插件下线拒绝合法标识）；page>=1默认1；pageSize枚举20/50/100默认20；200 RetryTaskPage；400参数错误，500查询错误 |
| /api/v1/retry-tasks/{taskId} | GET getRetryTask | taskId为UUID；200 RetryTaskDetail，400非法UUID，404不存在，500查询错误 |
| /api/v1/retry-tasks/{taskId}/execute | POST executeRetryTask | taskId为UUID；不声明requestBody，description要求零字节空请求体；带{}、null或任何内容均PARAM_INVALID；200 DownloadResponse，400/404/409/500/502/504按错误表 |

首次POST /downloads补409忙等错误及新200结构；生产下载路径错误响应固定400/409/500/502/504，原422适配错误改为进入业务后的结果明细（错误目录仍保留422分类供其他适用上下文），不在此端点留下整轮适配失败即中止的旧说明，继续同步单次请求。只读任务GET不占下载槽位、不探测日历或调用业务API。execute取得槽位后重新读取实际明细，正在执行时409且不排队；槽位空闲且任务不存在404。非法输入先400，忙时优先409于随后读任务的404；只读GET可在执行中反映当时数据库已提交状态。相同请求标识不去重；无If-Match、Idempotency-Key或版本领取。

定义 `RetryTaskSummary`（全部必填、严格对象）：taskId（uuid）、pluginId、apiName、pluginDisplayName（string|null）、apiDisplayName（string|null）、originalDateRange、originalDateRangeStatus、failedItemCount（int64>=1）、failedScopes（RecoveryScope数组，保存的全部当前选择器，不附错误）、createdAt、updatedAt（format:date-time，UTC毫秒Z）。没有展示名时页面使用保存标识。列表根据选择器生成摘要；不把不连续日期合并为连段，也不折叠同日不同股票。

`RetryTaskPage`（严格、全必填）：requestId、page、pageSize、totalElements、totalPages、items（RetryTaskSummary数组）。排序updatedAt DESC、taskId DESC。沿用现有只读分页约定：超末页归一到最后一页；空结果page=1、totalElements/totalPages=0、items=[]。只对任务分页，详情明细不新增分页参数。

`RetryTaskDetail`包含summary的全部字段，另有requestId、taskParams、items、retrying、canExecute、executionBlocker（全部必填）。taskParams为安全JSON对象（不是JSON字符串），按已保存白名单公开条件及原始日期返回；不将STOCK对象／失败时间回填主表，不加入凭证。items为RecoveryFailure加updatedAt；按targetType、targetValue、timeType、timeValue升序（字符串序）稳定返回，和failedScopes及failedItemCount一致。所有字段映射为驼峰，taskParams内部保留原蛇形参数。

retrying为当前进程是否正在重试该taskId；canExecute是查询时静态可执行性与槽位快照，不是来源验证承诺，不能替代execute再校验。executionBlocker为null或严格 `{code,message}`，均非空安全字符串；canExecute=true iff executionBlocker=null。当前进程槽位忙、插件／凭证不可用、参数或选择器不兼容、已知来源／日历证据拒绝时canExecute=false；原因优先忙、插件、任务参数、来源合法性、日历。GET不通过联网调用来计算可执行性；点击后仍可能502。不得持久化这三个字段。

`OriginalDateRange`为严格 `{startDate,endDate}`，YYYYMMDD字符串；summary/detail的originalDateRange为该对象或null，来源只能是taskParams原始两端。`originalDateRangeStatus`是必要的null含义区分：

| 状态 | originalDateRange | 判定／显示 |
|---|---|---|
| RECORDED | 合法完整两端对象 | 原始输入，过滤／月份展开前冻结；重试不改变 |
| NOT_APPLICABLE | null | 已知ORIGINAL_PARAMS且没有原始日期，显示“不适用” |
| NOT_RECORDED | null | 已知区间模式且两端都缺，显示“未记录”；仍可按失败明细精确恢复 |
| UNCONFIRMED | null | 单端／非法原始日期，或当前元数据及已登记模式均无法判定；显示“原始区间未确认”，详情原因说明且canExecute=false |

已登记49项可根据同版策略识别模式，即使插件disabled也保留显示；不能从失败日期或用户当前表单反推。RECORDED原始区间与当前scope不同是正常情况，不判冲突。单端／非法日期拒绝执行但不隐藏保存记录；原条件记录若异常带日期也不能悄删后执行。

### 6. 错误目录与阶段规则

保留既有16个代码及非下载端点HTTP映射。ApiError增加下列枚举；fieldErrors仍必填（无字段问题为[]）。retryable仅提示条件修复后可手动再试，永远不触发自动重发。

| 新代码 | HTTP | retryable | 触发和用户建议 |
|---|---:|---|---|
| SOURCE_REQUEST_UNCONFIRMED | 409 | false | Q门槛缺合法方式／条件／日期语义，零业务请求，首次不建任务；联系维护者补齐原接口依据，不改VIP或凭空加股票 |
| CALENDAR_UNCONFIRMED | 502 | true | 任一必要日历适用关系／覆盖／新鲜度未确认，零业务请求，首次不建任务、重试原项保留；检查来源后手动再试 |
| SOURCE_TRUNCATED | 502 | false | 有证据的来源截断，当前批次部分响应不得入库；排查合法完整获取方式 |
| SOURCE_COMPLETENESS_UNCONFIRMED | 502 | false | 无法证明当前响应完整，不能把短响应当完整；联系维护者核实取全 |
| DATA_CONFLICT | 422 | false | 单元内或与本轮已提交数据同键不同内容；仅当前完整恢复单元失败 |
| RETRY_TASK_NOT_FOUND | 404 | false | 当前失败任务不存在；刷新真实列表，不声称无响应下载已成功 |
| DOWNLOAD_BUSY | 409 | true | 单实例槽位占用；等待真实执行结束，不排队、不解除原执行 |
| RETRY_TASK_INVALID | 409 | false | 保存条件／对象／时间不可解析或当前合同不兼容，保留原记录；联系维护者，不能编辑原任务扩大范围 |
| TASK_RECORD_SAVE_UNCONFIRMED | 500 | false | 明确失败的创建／追加／原因更新未确认保存（已知回滚也用本码并说明未保存），停止；查看实际记录，不承诺找回 |
| COMMIT_UNCONFIRMED | 500 | false | 业务写入或成功删除事务提交状态不明，停止；结果未确认，不将其补为失败或自动重发 |

PERSISTENCE_FAILED现有语义修正为“当前恢复单元事务确认失败／回滚，之前已提交单元保留”，不能继续说整次下载零提交。连接健康且回滚明确时记录当前失败并继续；否则停止并带可确认小计。QUERY_FAILED仍用于GET失败。

阶段决定承载方式：执行前参数／插件／凭证／策略／日历失败用非200 ApiError，首次不建失败；进入业务后的来源认证、权限、限流、网络、超时、payload／截断／完整性错误及单元适配／冲突，可靠保存后继续后续计划，最终以200 PARTIAL/FAILED及failures承载，错误码的HTTP栏不是提前退出整轮的理由。存储故障／保存未确认／提交未知停止，用非200 ApiError及适用downloadResult。非下载端点原映射不变。

PLUGIN_DISABLED建议修正为检查原插件配置，不建议原任务换源。所有错误、任务原因及本轮范围均不含Token、Authorization、原始上游响应、堆栈或数据库诊断。来源策略未确认与用户参数非法不同；不要把用户合法目标输入报成拼错参数。

### 7. 必须落入OpenAPI的示例

请求示例包含daily同日区间、income原股票公告区间、margin原交易所区间、broker_recommend跨三月31天、trade_cal原生同日、stock_basic原条件及无参数index_classify；均仅是目标合同示例，不代表账号或来源通过。同日起止均取20260901；income和margin取20260901—20260910，附加条件分别为000001.SZ和SSE；跨三月取20260131—20260302；trade_cal的exchange=SSE，stock_basic的list_status=L。

正向响应示例（计数均按上述schema，填齐其余字段）：

- `partialUnits`：requestId=c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33，pluginId=tushare_pro，apiName=daily；S=2、F=1、N=0、H=0，R=20、I=18、U=2，outcome=PARTIAL；失败为STOCK 000002.SZ + DATE 2026-09-03 + ADAPTER_TYPE_INVALID；taskId=11111111-1111-4111-8111-111111111111、remainingFailedUnits=1、failureRecordStatus=CONFIRMED，notStartedScopes/unconfirmedScopes=[]。本例必须标“受控合同示例，独立恢复未真实启用”，不创建新fixture接口。
- `empty`：S=1、F=N=H=R=I=U=0，EMPTY、taskId=null、remainingFailedUnits=0、NOT_REQUIRED，全部范围数组空。
- `allClosed`：S=F=N=R=I=U=0、H=2，NO_OPEN_DATES、taskId=null、remainingFailedUnits=0、NOT_REQUIRED；首次文案“所选区间无开盘日期”，重试用“所选范围无开盘日期”。
- `allFailed`：S=0、F=1、N=H=R=I=U=0，FAILED；REQUEST+RANGE 2026-09-01/2026-09-02、SOURCE_TIMEOUT，记录已确认且剩余1项，HTTP200。
- `recordSaveUnknown`：HTTP500 TASK_RECORD_SAVE_UNCONFIRMED；downloadResult=UNCONFIRMED，S=1/F=1，R/I/U为此前确认的10/10/0；尚未形成后续单元集合N=null、notStartedScopes=[REQUEST+RANGE 2026-09-04/2026-09-10]，failures=[REQUEST+DATE 2026-09-03]，failureRecordStatus=UNCONFIRMED，taskId=null、remainingFailedUnits=null；不把这些未保存范围放入GET。
- `commitUnknown`：HTTP500 COMMIT_UNCONFIRMED；S=1/F=0、此前确认R/I/U=10/10/0，未知当前REQUEST+DATE 2026-09-03列入unconfirmedScopes，N=null，未开始范围同上，failures=[]，failureRecordStatus=UNCONFIRMED、taskId/remainingFailedUnits=null。
- `calendarUnconfirmed`：HTTP502 CALENDAR_UNCONFIRMED，fieldErrors=[]，省略downloadResult；说明业务未执行，已有失败项保留。
- 列表／详情示例：taskParams={exchange_id:SSE,start_date:20260901,end_date:20260910}，originalDateRange=20260901—20260910、RECORDED；failedScopes/明细为REQUEST+DATE 2026-09-03和2026-09-07。部分重试之后仅剩7日，原始区间保持。另列STOCK两股同日、NOT_APPLICABLE原条件、NOT_RECORDED旧记录、UNCONFIRMED单端记录、插件下线名称null及404/409示例。

HTTP200的所有例子可由schema验证。反向样例放进校验脚本，不放到OpenAPI examples作为有效值：旧／混用日期，丢股票／交易所，原条件带日期，REQUEST非空对象值、STOCK空值／多股、NONE非空日期、未知N写成0的错误场景、UNCONFIRMED冒充200、EMPTY含正行数、FAILED无保存任务、已保存两股同日却折叠计数、单端原始区间冒充RECORDED。未知数量的判断以场景事实为依据，不声称JSON Schema能自动知道执行事实。

### 8. 29项AC增量追踪与执行顺序

新追踪表列固定为 `AC | PRD | TRD | Contract | Implementation tasks | Verification tasks | Expected evidence | Result`，逐行AC-PRD-RANGE-01—29，Result初始化“未执行；T03仅合同校验”。PRD映射原样抄§9.1的PRD-01—17，TRD引用具体章节，Contract指到实际路径或schema锚点；无直接HTTP的事务用例明确其结果／失败选择器观察面，不能写N/A后丢掉责任。

任务映射和预期证据固定如下（表中数字为RANGE-T后缀）：

| AC尾号 | 实现任务 | 验证任务 | 预期证据 |
|---|---|---|---|
| 01 | 04,05,14,15 | 18,19,20 | 49目标形状／策略分类、38范围及具体账号口径分列 |
| 02 | 06,08,12,13 | 18,20 | 首次及重试日历确认后实际业务日期 |
| 03 | 05,13,14 | 18,20 | income股票、margin市场参数精确还原 |
| 04 | 05,08,13,15 | 18,19 | 31天跨三月、跨年、完整月份及失败月份 |
| 05 | 05,06,08,12 | 18,19 | 单日开盘／休市／公告／合法空 |
| 06 | 05,14,15 | 18,19 | 非法／31／32天请求捕获及零业务调用 |
| 07 | 11,12,16 | 18,19 | 合法空不占位、不删旧数据，空失败混合计数 |
| 08 | 09,11,12 | 18,20 | 同响应A/C业务表保留、仅B失败；来源独立恢复另证 |
| 09 | 09,11,12 | 18 | 同键相同去重／不同当前单元失败 |
| 10 | 07,12,13 | 18 | 来源失败保存后继续全部后续范围 |
| 11 | 07,09 | 18,20 | 后页错误／截断整批不提交及完整恢复边界 |
| 12 | 07,12,13 | 18 | 局部／来源连续失败及后续成功，无自动重试 |
| 13 | 11,12,14,16 | 18,19 | S/F与R/I/U确认小计及2/1示例 |
| 14 | 04,05,08,09,13 | 18,20 | REQUEST不拆小、不转STOCK、原条件无日期 |
| 15 | 06,12,13 | 18,19 | 全休市零业务请求，新任务无记录、原项按完整确认移除 |
| 16 | 06,08 | 18,20 | 多市场并集、停牌与休市区分 |
| 17 | 06,12,13,14 | 18,19 | 日历未知零业务，首次无任务、原项保留 |
| 18 | 04,06,08,15 | 18,19,20 | 非交易日期不进日历，trade_cal闭市行保留 |
| 19 | 05,14,15 | 18,19 | 旧／混用拒绝与附加条件保留 |
| 20 | 15,16,17 | 19 | 切换／响应丢失不重发、不推测成功 |
| 21 | 10,11,13,17 | 18,19 | 两股同日分别保存／删除，最后删除主表 |
| 22 | 05,10,13,14,17 | 18,19 | 原1—10与剩余3/7、部分后仅7，重启仍正确 |
| 23 | 12,13,14,17 | 18,19 | 忙409、无任务404，无重建或排队 |
| 24 | 10,11,13 | 18 | SQL／删除同事务回滚与A/C保留 |
| 25 | 04,05,13,14,17 | 18,19 | 不兼容拒绝且原记录不改源／范围 |
| 26 | 10,11,12,13,14,16 | 18,19 | 保存与提交未知区别，确认小计、N=null |
| 27 | 12,13,16,17 | 18,19 | 无取消／暂停入口，执行结束才解锁 |
| 28 | 12,13,14,16 | 18,19 | 断连继续、不释放槽位、不自动重提 |
| 29 | 07,09 | 18,20 | 缺股票不猜失败，整体归属未过不拆分 |

每行T21为最终证据汇总责任，在文件说明中统一写明。保持ISSUE-008九项明确排除，受控合同通过不登记真实通过。

实施第一步：在OpenAPI中增加RangeParams及其三种带条件形状，给DownloadRequest加Tushare的49项分支并迁移daily示例；随后依次完成策略投影、结果和错误、三个retry-tasks端点、示例、校验脚本及增量追踪。最后运行Tests、写验证报告及看板完成证据；完成T03之后才按流程设计并交接T04。本设计不会预先创建T03合同实现文件。

## Files

| 路径 | 操作／责任 |
|---|---|
| docs/contracts/openapi-v1.yaml | 修改；请求、投影、选择器、结果、错误、任务端点、示例及49项扩展表 |
| docs/contracts/error-codes.md | 修改；新代码、阶段承载、原单元事务语义与安全建议 |
| docs/traceability/tensor-range-requirements.md | 创建；29项增量追踪；保留平台历史追踪只读 |
| docs/contracts/verify_range_contract.py | 创建；仅文档合同的结构、示例及语义规则校验，不调用服务 |
| docs/verification/RANGE-T03-contracts.md | 创建；实际命令结果、49／38／29覆盖、运行接口未迁移及未验证范围 |
| docs/task-handoffs/tensor-range/tensor-range-task-board.md | T03启动／完成证据及按流程准备后继；不改其他项目 |

新建文件加入Git，保留已有暂存和未暂存改动，不顺带提交或发布整个工作区。

## Tests

仓库根执行 `python3 docs/contracts/verify_range_contract.py`、`git diff --check`、`git diff --cached --check`。脚本依赖当前环境已有PyYAML和jsonschema，不引入前端／Maven依赖；使用yaml.safe_load、Draft202012Validator、FormatChecker（日期／UUID／时间戳）和本地$ref解析，禁止联网加载schema。

脚本须实际验证以下可失败断言，不能仅打印PASS：

1. OpenAPI版本3.1.0；遍历所有本地 `$ref`，目标存在；所有component schema通过 `Draft202012Validator.check_schema`；下载和任务operationId唯一，所有操作响应头有X-Request-Id；新增路径集合恰三个，无取消／核对端点或新幂等头。
2. 49目标扩展表与T01 JSON集合／模式精确一致，19calendarProfile与T02一致，9liveExcluded一致；每个apiName构造合法目标请求并验证对应schema，38／11精确计数；按上述表核对必填附加条件与原枚举。Tushare未知api拒绝；已存在fixture的原scenario形状仍由通用请求接受。
3. 为49请求逐项增加未知参数；38项逐一删除两端、加原旧日期；需要股票／交易所项逐一缺失；11项逐一加日期；非字符串、混用和多股被拒绝。额外语义函数解析公历、31/32天、逆序、闰日、跨年和20260131—20260302覆盖1/2/3月；函数只检验本设计的文档规则，运行校验器仍由T05/T14测试，不冒充运行验证。
4. 遍历OpenAPI request/response的全部examples并验证其实际schema；检查RecoveryScope四种时间和两种对象，原生单日DATE说明、范围精确、不连续日期不合并；对§7全部反向样例断言验证失败或语义检查明确失败。
5. 200响应与错误downloadResult边界；空／全休市／2成功1失败／全失败／保存未知／提交未知均有示例；所有已知数值和null语义符合§4；requestId跨体一致，计数不包含未知当前单元。不能由schema通过宣称数据库行为正确。
6. 列表默认20、可选20/50/100、page从1、任务UUID、排序／空／越页说明；详情originalDateRange四状态、taskParams对象、原1—10与失败3/7、同日两股、部分后仅7、非法原始日期拒绝、插件下线标识仍显示；execute不声明body且明确拒绝任意内容。
7. 错误目录代码集合与ApiError枚举完全相同，新增10码的HTTP／retryable及阶段说明一致；所有失败示例标准errorCode可追目录；查询旧schema、精确数值字符串合同保持。
8. 新增追踪文件恰29唯一AC，各列非空，任务ID存在于本看板且与§8匹配，来源和已存在的相对文档链接可访问；尚未创建的后续验证产物只记“预期证据”，不伪造链接或结果。

成功预期：脚本退出0，逐类输出49目标（19/15/1/3/11）、38迁移／11保留、29AC、所有正反例PASS、10新增错误码及3新增路径通过；将实际结果写验证报告。可用临时内存变异验证脚本有效：将pageSize默认改50、删某目标、将旧trade_date放回daily、把UNCONFIRMED结果置200，四者应非零；不写回合同文件。只运行这些合同检查，不运行业务API或生产功能测试。

设计准备阶段仅复核文件模板、字段／案例／任务映射和引用，未运行尚不存在的T03校验脚本，未宣称合同已经迁移。

## Acceptance

- 49项请求精确覆盖38新区间＋11原条件；metadata投影与原查询模式分离；缺口保留拒绝条件，无默改股票、市场、日期或API。
- 正常本轮结果、已确认小计、未知范围／N、保存失败和提交未知有互不混淆的可校验表达；已保存入口不由预生成ID伪造。
- 当前失败任务列表／详情／execute、原始区间与明细、两个对象／四种时间、三分页大小、404／409均固定；同日两股和非连续日期不折叠，原始日期不扩大重试。
- 29项AC精确映射实现／验证责任和预期证据；全部合同正反例及差异检查有本轮PASS报告。生产运行、受控行为及来源真实结果仍明确未验证。
- 当前任务完成记录先于T04设计／交接；新建文件入Git，不更改历史问题和验收结论。

## Risks

T01/T02已明确来源合法性、完整性和日历缺口，尤其monthly及margin_detail冲突、BSE与转融通等价、原旧文档缺失。T03只能统一拒绝与目标合同，不能借合同示例宣称来源可用或关闭功能AC。后续获得新证据时由其任务更新策略依据，涉及产品条件变更须先更新确认来源。

OpenAPI/schema表达不了所有日期、计数及执行事实，校验脚本的语义例子也不替代运行接口、MySQL原子性或真实来源测试。执行前静态canExecute是瞬时提示；执行仍须重新验证。未保存、未开始及提交未知范围不保证可恢复，此限制须贯穿错误和任务文案。
