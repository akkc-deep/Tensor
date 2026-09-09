# RANGE-T16 下载状态、部分结果与断连提示设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的`RANGE-T16`：下载页准确显示T14返回的本轮结果、等待及通信未确认状态，保留T15输入合同，并给已确认保存的失败任务提供确定的入口目标。

本设计在T15已记录COMPLETED后创建；按预定义Order选择16，观察到NOT_STARTED、Design／Handoff均None。准备设计与交接不启动T16实现。

## Scope

- 修改下载状态流、结果组件及下载页，接入六种outcome、S／F／N／H与R／I／U、失败／未开始／未知范围、已保存任务入口和不可终止提示。
- 迁移前端错误解析所依赖的26码及可选downloadResult；严格保留原查询错误安全校验。首次下载仍一次POST，状态与计数仅存在当前页面内存。
- 覆盖AC-PRD-RANGE-07、13、15、17、20、26、27、28的前端适用部分。复用AsyncStatePanel、现有按钮、布局／主题／KeepAlive；不重做UI，不修改T15日期表单、后端、生产策略／日历、数据库、OpenAPI或错误目录。
- T17负责“发起下载／失败任务”页签、列表／详情及execute。本项定义并验证任务入口的路由目标；任务内容页的可用闭环仍由T17／T19交付。本项不增加任务列表或以JSON端点代替页面，也不执行任务重试。
- 不增加进度、取消、暂停、轮询、自动重发、浏览器存储历史、启动恢复、查业务表推断结果、股票集合或新的执行限制。保留分支／HEAD、原索引及任务外工作；新文件加入Git，不提交／发布。

## Approach

### 1. 直接输入和差距

按顺序读取[PRD v1.4 §2、§5.4、§5.8](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD v1.10 §6.4、§8.3、§9](../design/Tensor_区间下载_TRD_v1.0.md)，再读当前useDownloadFlow、DownloadResult、DownloadView、http／errors及[T14设计](RANGE-T14-design.md)／[HTTP验证](../verification/RANGE-T14-http.md)、[T15设计](RANGE-T15-design.md)／[表单验证](../verification/RANGE-T15-form.md)。精确字段／样例以[OpenAPI](../contracts/openapi-v1.yaml)的DownloadResponse、DownloadExecutionResult、RecoveryScope、RecoveryFailure、ApiError为准。

T14提供18字段结果；200只允许SUCCESS／EMPTY／NO_OPEN_DATES／PARTIAL／FAILED，错误快照使用UNCONFIRMED。N及remainingFailedUnits允许null；taskId仅表示确认保存且仍有失败明细的任务，不能从F或failureRecordStatus推导。HTTP错误可保留已确认小计，不表示回滚。

T15已经提供五类表单、策略守卫、一次规范化POST、parametersChanged清理及锁定。最终96项指定测试、两个时区各22项、全量215项／build及独立评审通过；这些是输入基线，不是T16结果验收。

当前差距：DownloadView只显示旧SUCCESS／EMPTY／FAILURE；DownloadResult只展示旧三种状态，错误仍提供“使用原参数重试”。errors.js严格只接受旧16码及五字段ApiError，因此新码和带downloadResult的错误会降级为INVALID_RESPONSE。useDownloadFlow会把下载失败保存为可重放DOWNLOAD操作；这些必须同版迁移。AppLayout已经KeepAlive下载页，切到设置仍保持请求；无需改布局生命周期。

### 2. 最小响应边界与错误迁移

新增`control-plane/src/api/downloadResult.js`，仅导出`isDownloadResult(value, { allowUnconfirmed = false } = {})`：一个下载结果合同守卫，供downloads.js和errors.js共用，不引入schema库／通用验证框架或循环import。

- 根对象恰好包含OpenAPI的18字段；requestId／message非空字符串，pluginId／apiName满足现有标识形状；outcome来自上述允许集合，failureRecordStatus来自NOT_REQUIRED／CONFIRMED／UNCONFIRMED。
- S／F／H／R／I／U为非负安全整数；N和remaining为非负安全整数或显式null。缺失／字符串数字／NaN／负数不得转换为0。超出JS安全整数范围作为无法确认的响应处理，不能显示舍入后的“准确计数”；这不是服务端执行限制，也不改变records的大整数／DECIMAL字符串合同。
- taskId为标准完整UUID字符串或null。三个范围数组必需存在，元素恰为RecoveryScope四字段或RecoveryFailure六字段。REQUEST的targetValue为空，STOCK为单一规范化代码；DATE／MONTH用现有严格日期工具校验，RANGE为`YYYY-MM-DD/YYYY-MM-DD`且开始小于结束，NONE的timeValue为空。失败码／原因按合同长度及非空检查，历史未知失败码仍是可展示文本，不要求属于当前26码。
- 按OpenAPI现有结果语义校验：S=0时R／I／U必须0；SUCCESS有S和R且F0；EMPTY有S且R／I／U／F0；NO_OPEN_DATES为S／F／R／I／U0且H>0；PARTIAL有S及F；FAILED为S0且F>0。200不得有UNCONFIRMED，PARTIAL／FAILED须满足DownloadResponse的记录确认约束；错误UNCONFIRMED的failureRecordStatus为UNCONFIRMED。不得新增“F=remaining”、数组长度决定N、R=I+U、按日期数决定S或统一totalUnits等不在合同中的关系。

`downloadDataset`仍只发一次POST；用真实Axios返回的data／headers校验正常结果、头与body requestId一致、pluginId／apiName与本次request一致。无效时抛带本次请求ID的`ClientError('INVALID_RESPONSE')`，不丢弃成成功或自动重试。成功响应保持原字段／null，不重算计数。现有请求头生成、timeout=130000及HTTP拦截器保持不变。

errors.js保留原16码映射和5字段严格校验，增加现行错误目录的10码：SOURCE_REQUEST_UNCONFIRMED=409/false、CALENDAR_UNCONFIRMED=502/true、SOURCE_TRUNCATED=502/false、SOURCE_COMPLETENESS_UNCONFIRMED=502/false、DATA_CONFLICT=422/false、RETRY_TASK_NOT_FOUND=404/false、DOWNLOAD_BUSY=409/true、RETRY_TASK_INVALID=409/false、TASK_RECORD_SAVE_UNCONFIRMED=500/false、COMMIT_UNCONFIRMED=500/false。

ApiError仅额外允许可选downloadResult；出现时必须为合法allowUnconfirmed快照、outcome=UNCONFIRMED、嵌套requestId等于外层及响应头，且外层code是TASK_RECORD_SAVE_UNCONFIRMED／COMMIT_UNCONFIRMED／PERSISTENCE_FAILED／INTERNAL_ERROR之一。显式null、字段缺失或新增其他字段仍拒绝。ApiError实例增加`downloadResult`，无快照时为null；对嵌套对象／数组复制并冻结，沿用fieldErrors保护，不回显响应正文。更新ApiErrorCode／ApiErrorBody和下载响应JSDoc。ClientError现有通用文案不改；下载页面使用本项专门的未知结果说明，查询页面保留原错误处理。

### 3. 执行上下文、状态与锁定

useDownloadFlow增加只读消费的`executionContext` shallowRef，首次提交时保存`{pluginId, apiName, params, rangeMode}`；params为本次已验证参数副本，rangeMode由所选公开policy决定。它只用于本轮标识、REQUEST附加条件及等待文案，不形成历史或重放任务。load／selectSource／selectApi／parametersChanged清理它，与result／error同步；SUBMITTING时这些操作仍被既有locked拦住。

保持INITIAL、METADATA_LOADING、READY、SUBMITTING，结束态如下：

| 输入 | state／result／error |
|---|---|
| 200合法五种正常结果 | state=response.outcome，result=response，error=null；不按R或F重新分支 |
| ApiError有合法快照 | state=UNCONFIRMED，result=error.downloadResult，error保留；再检查快照pluginId／apiName与executionContext一致，错配按ClientError INVALID_RESPONSE处理 |
| ApiError无快照 | state=FAILURE，result=null，error保留；不伪造S/F/N/H或taskId |
| ClientError（TIMEOUT／NETWORK／INVALID_RESPONSE／UNEXPECTED） | state=UNCONFIRMED，result=null，error保留；只有本次执行上下文和请求ID，不编造计数／任务 |

保留generation检查；过期请求不能覆盖当前上下文。异步等待中切到其他路由由现有KeepAlive保留，同一Promise的唯一响应在返回时可见；不加onActivated重发、不使用AbortController／onDeactivated取消。彻底卸载再挂载只重新加载metadata，不恢复或重发下载；刷新没有本轮计数是既定边界。

`failedOperation`只保留SOURCES／APIS，canRetry／retry仅重载metadata。移除DOWNLOAD分支及原参数重放入口；error.retryable表示服务端类别，不授权前端重新执行整个原区间。结束后用户主动点击“开始下载”仍使用当前表单做一次新的首次下载，生成新的请求ID，不复用数据库任务标识。

locked仅等于本地SUBMITTING；网络Promise结束后本地等待结束，不声称服务端槽位已释放。未知结果页面明确说明服务端可能仍在执行；用户再次主动提交可能收到DOWNLOAD_BUSY，展示真实409且不排队／轮询。不能让页面在断连后无限锁定，也不能把解除本地控件锁定解释为服务端终止。

区间等待固定显示“区间下载已开始，不可终止，请等待结果。”，原条件等待显示“下载请求已提交，请等待结果。”。保持来源／接口／参数／开始按钮禁用，等待标识只有当前请求，无百分比、阶段、取消／暂停／停止／终止入口。T15提交前提示保留。

### 4. 结果组件与可读范围

DownloadResult保留state／result／error props，移除canRetry及retry事件；新增`context` prop（上述executionContext）和`view-task`事件（payload为当前result.taskId）。使用现有AsyncStatePanel的state和slot，映射如下，不修改公共组件的状态枚举：

| 页面结果 | 标题／基础样式 |
|---|---|
| SUCCESS | 下载完成／SUCCESS |
| EMPTY | 下载完成，0 条数据／EMPTY |
| NO_OPEN_DATES | 所选范围均为休市日期／EMPTY |
| PARTIAL | 部分完成／FAILURE，正文“本轮已完成部分恢复单元，仍有明确失败项。” |
| FAILED | 本轮下载失败／FAILURE，正文“本轮尝试的恢复单元均未完成，请查看明确失败范围。” |
| UNCONFIRMED | 结果未确认／FAILURE |
| 无快照FAILURE | 请求未完成／FAILURE；CALENDAR_UNCONFIRMED单独标题“未开始下载：日历未确认”，DOWNLOAD_BUSY单独标题“已有下载正在执行” |

保留安全服务端message或error.message为普通文本（不能v-html），但不能用它覆盖固定未知说明。对无快照的参数／来源拒绝只展示实际错误，不把所有无快照响应都宣称“业务调用为零”。日历专用说明为“未确认适用日历，本次未开始业务下载，也未新增失败任务。”；全闭说明为“已按适用日历确认跳过休市日期，不属于来源返回空数据。”

有result的所有状态均展示七项dl：`已完成项`S、`明确失败项`F、`未开始项`N、`跳过休市日期`H、`已确认返回行数`R、`已确认新增行数`I、`已确认更新行数`U。N=null显示“未确认”，0显示0；未知结果的这些数字标题加共同说明“以下为本轮已确认小计，不代表完整结果。”。不对EMPTY强制从外部传入的错误99计数推零，API边界会拒绝这种坏数据；组件测试使用符合合同的完整样例。

另列`当前剩余失败项`remaining（null为“未确认”）及记录状态，三值分别为“无需保留失败记录”“失败记录已确认”“失败记录保存或删除结果未确认”。F和remaining分别展示，不互相替代。taskId非null才显示“查看重试任务”按钮，无论recordStatus如何都保留服务端已确认的该标识；不从F>0造ID，也不因最新保存未确认而隐藏之前已经确认的任务。taskId=null／remaining=0的确认删除结果不补回旧ID。

按原数组次序分三个有标题的语义列表：`本轮明确失败范围`、`本轮未开始范围`、`本轮结果未确认范围`。失败行展示对象、时间和安全原因／错误码；其他行只展示对象和时间。空数组不生成“无失败”或“全部完成”的推断，也不显示虚构占位明细。

对象STOCK显示代码，REQUEST显示“原请求条件”，并在列表前从context.params显示非start_date／end_date的附加条件（原顺序、普通文本；无附加条件显示“无附加条件”）。时间DATE直接YYYY-MM-DD、MONTH直接YYYY-MM并注明“完整月份”、RANGE将斜杠分隔为“开始 至 结束”、NONE显示“原条件请求”。不同股票同日、3日和7日分别保留，不合并为一个日期或连续范围。组件内部小函数即可，不新增全局范围展示框架；T17可直接复用相同规则。

无快照UNCONFIRMED不显示计数dl、三个范围列表或特定任务按钮；固定说明：“未收到可确认的下载结果，服务端可能仍在执行。请查看实际保存的失败记录；本提示不代表已终止、已回滚或已成功。” 再显示“重新提交当前条件属于新的首次下载，不是原任务重试。”。有快照UNCONFIRMED说明“本轮结果或失败记录未完全确认，已确认提交的单元不会因此撤销。”并保留上面的本轮小计、实际范围及安全错误。两者都不得承诺从任务列表还原完整历史；查询不到任务不代表无响应请求成功。

### 5. 页面入口与T17衔接

DownloadView的结果分支覆盖六outcome及FAILURE，并传executionContext；元数据错误仍走原重新加载分支。接收`view-task`时只在payload等于当前非null result.taskId时调用router.push，目标固定为`{name:'downloads', query:{tab:'retry-tasks', taskId}}`。不保留上次query中的其他条件，不把api参数写入URL；不发execute或额外download。

这是T16交付给T17的入口合同：T16验证按钮只出现于已确认任务、发出精确UUID、路由目标正确且下载POST数不变；T17创建页签后消费tab／taskId并GET详情。T16不宣称此时任务内容页已完成，T17／T19验证点击后真实展示。该增量与T14先交付HTTP、T15先交付表单相同，项目完成门槛仍包含后续页面任务。不得用空白新路由、外部页面、JSON端点或占位“开发中”面板替代后继。

新增区域复用现有颜色／间距；计数及范围列表可换行，680px以下单列或两列计数，不能横向溢出。恢复范围和错误码用可读文本，RequestId沿用AsyncStatePanel。按钮可键盘聚焦，状态变化有既有status／alert通告；不主动把焦点移出用户正在填写的表单，不新增动画／全局样式。

## Files

| 文件 | 责任 |
|---|---|
| `control-plane/src/api/downloadResult.js`（新） | 最小18字段及范围快照守卫，正常／错误允许集合；无HTTP和UI状态 |
| `control-plane/src/api/downloads.js` | 一次POST后验证正常快照、标识／RequestId；不改输入或执行数量 |
| `control-plane/src/api/errors.js` | 26码与可选安全downloadResult，冻结副本；查询原安全边界保留 |
| `control-plane/src/composables/useDownloadFlow.js` | 本轮上下文、六outcome／通信未知、仅metadata重载、清理与锁定 |
| `control-plane/src/components/download/DownloadResult.vue` | 精确计数、三组范围、记录状态、特定任务事件及未知说明 |
| `control-plane/src/views/DownloadView.vue` | 等待文案、所有结果分支及context／task路由入口 |
| `control-plane/src/api/downloadResult.spec.js`（新）、`api/api.spec.js`、`api/errors.spec.js` | 完整正常／错误wire、26码、错配与安全拒绝，不以模拟ApiError构造替代Axios边界 |
| `control-plane/src/composables/useDownloadFlow.spec.js`、`components/download/DownloadResult.spec.js`、`views/DownloadView.spec.js`、`layouts/AppLayout.spec.js` | 状态、真实表单锁定／单次提交、路由切换及入口、DOM可读内容／焦点 |
| `control-plane/src/test/fixtures/range-results.json`（新） | 按OpenAPI正常／停止样例整理完整18字段，记录来源；不从运行验证器生成预期 |
| `docs/verification/RANGE-T16-results.md`（新）、`docs/traceability/tensor-range-requirements.md` | 本轮实际前端证据及八项AC边界 |

必要时只迁移其他现有下载相关测试的旧8字段响应fixture，以适配真实18字段；不能改查询fixture或放宽生产验证使旧mock通过。不删文件，不改路由定义或AppLayout实现。

## Tests

正式实施先保存当前分支／HEAD／原索引／任务外文件摘要，读取上述源码；按独立OpenAPI样例写三项先行行为RED：①真实页面PARTIAL显示完成2／失败1及确认R/I/U；②Axios500带UNCONFIRMED快照保留N=null和此前小计；③ClientError超时显示未知且无原参数重试。缺文件／接口的导入失败与行为断言失败分别记录，然后实施最小变更。

| 验证组 | 固定观察 |
|---|---|
| 六种结果 | SUCCESS(S2/F0/R10/I7/U3)、EMPTY(S2/R0)、NO_OPEN_DATES(S0/H3/R0)、PARTIAL(S2/F1/R10/I7/U3)、FAILED(S0/F2/R0)、错误UNCONFIRMED保留此前确认小计；所有未列计数／记录字段仍按合同补完整 |
| 合法空与失败 | S1/F1/R0仍是PARTIAL；不能按R0变为EMPTY。全闭H3不计S3；首次日历未确认无计数／任务并说明未开始 |
| null／0独立 | N=null与0不同、remaining=null与0不同、F1而remaining2不改写；taskId非null＋recordStatusUNCONFIRMED仍保留已确认入口；确认删除后taskId=null/remaining0不恢复旧ID |
| 范围 | STOCK两股同日分别可读；REQUEST的3／7日不扩大，RANGE含多日仍一行、MONTH完整月份、NONE原条件；未开始范围数组1项且N=null不显示N1 |
| 停止快照 | 四停止码分别按目录status/retryable验证；保存失败含明确F和未知保存，提交未知不计当前S/F，仅展示此前已确认值；不显示撤销／整体失败回滚 |
| 错误边界 | 26码正确status/retryable及RequestId；原五字段仍接受；额外字段、null快照、未知outcome、200UNCONFIRMED、字符串／缺失／非安全整数计数、坏范围、ID错配、快照在非法错误码下出现均安全INVALID_RESPONSE；不回显body哨兵 |
| 通信 | TIMEOUT、NETWORK、INVALID_RESPONSE、UNEXPECTED分别无伪造计数／任务和自动重发；没有DOWNLOAD失败重放操作，metadata重载仍单次可用；再次主动提交是新请求ID的一次首次POST |
| 锁定与切页 | deferred请求期间来源／API／日期及重复开始禁用；区间固定不可终止文案，无取消／进度；切数据查看／设置再返回仍一个请求，晚到的PARTIAL／错误响应匹配原上下文。彻底卸载重挂只读metadata，不重发下载 |
| 修改／入口 | 结果后修改日期／股票／枚举及切接口清理全部本轮上下文；仅真实taskId出现按钮，键盘触发精确query目标且下载次数不变；无任务／未知ID无特定入口，不触发execute |
| 回归 | T15真实49项／31天／月份及规范化继续通过；旧查询错误、精确数字、通用表单、metadata重载及主题布局保持通过；长代码／长原因文本安全换行 |

在`control-plane/`依次执行，Node使用项目已安装24.15.0；最终各命令exit0、零失败／跳过，记录实际文件和用例数，不预设通过数量：

```sh
npm test -- src/api/downloadResult.spec.js src/api/api.spec.js src/api/errors.spec.js src/composables/useDownloadFlow.spec.js src/components/download/DownloadResult.spec.js src/views/DownloadView.spec.js src/layouts/AppLayout.spec.js
npm test
npm run build
```

仓库根执行`PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py`、`git diff --check`及`git diff --cached --check`。检查58份生产资源及开始时任务外／原索引／branch／HEAD不变，新增正式文件精确加入Git。独立规格／质量及最终集成／证据评审通过后记录完成。

做一轮桌面与窄屏结果页面观察，使用完整受控HTTP快照：PARTIAL两成一败、UNCONFIRMED的null／已知小计及长范围，确认无溢出、键盘按钮和可读提示。记录实际环境及截图；受控页面不算T19完整后端闭环。T16不改后端，不用重跑MySQL替代表单／Axios证据，不调用真实Tushare；真实socket／进程中断仍由T18验收。

## Acceptance

1. 页面消费正常五种与错误UNCONFIRMED，显示准确的本轮S/F/N/H及R/I/U，合法空、休市、失败和未知不混淆；null不补0，范围和对象不合并或推算。
2. 正常响应、26码和四停止快照通过真实Axios边界；结构、状态、标识或RequestId不合法时安全未知，不丢弃已验证快照或显示伪造成功。
3. SUBMITTING期间保持控件锁定和不可终止提示；切页保留同一执行，刷新不重发，不显示进度、取消或服务端结束的臆测。
4. 通信未确认明确保留未知边界；不造任务、不重放原DOWNLOAD操作。用户主动提交仍为独立首次请求；metadata重载与新任务入口不混用。
5. 已确认taskId才有特定任务入口，向T17提供唯一精确路由query合同；任务列表／详情及重试闭环未在本项冒称交付。
6. 指定测试、构建、静态合同、布局观察与差异保护实际通过，正式报告和八项AC增量完整。先记录本项完成再准备T17，不自动实施后继。

## Risks

- 无待决产品事实；上述状态、文案、字段、入口及测试已固定。任务入口当前仅交付T17消费的导航合同，T17／T19必须补齐点击后的内容和重试闭环，不能凭T16链接存在宣称失败任务页面已可用。
- 不同于服务端提交未知，客户端通信错误没有可信执行快照。不得从旧result、请求原范围、F或当前业务表补齐计数；已知原任务也不能在丢回复后补造明细。
- ApiError当前严格校验是安全边界，新增可选快照必须保持精确字段／RequestId检查；所有文本由Vue转义。模块依赖保持http→errors→downloadResult、downloads→http及downloadResult，避免循环。
- 当前来源／日历仍保守关闭，生产fixture批次能力由T18提供。T14受控HTTP、T15受控表单不代表真实账号取全；ISSUE-008九项继续不依赖、未解决。
- T15完成证据只证明表单，T16必须迁移所有下载相关旧响应mock，不能给新结果或错误加兼容回退以维持旧测试。既有Vite大chunk提示不在本项优化范围。
