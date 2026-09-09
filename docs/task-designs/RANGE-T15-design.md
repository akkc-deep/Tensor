# RANGE-T15 区间表单、日期说明与参数迁移设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T15`：下载页消费T14已经迁移的元数据和请求合同，准确展示五类输入、日期含义及输入范围摘要，每次提交一个合法请求。

本设计在T14已记录COMPLETED后，按预定义Order选择15，观察到本项NOT_STARTED、Design／Handoff均None后创建。设计和交接就绪只准备READY，不表示已实施表单或通过浏览器验收。

## Scope

- 接入公开downloadPolicy；修改下载表单日期标签／初值、常驻说明、包含两端的自然日校验、完整月份摘要，以及切换／修改条件后的本地结果清理。
- 保留49项的19／15／1／3／11分类、38个区间与11个原条件、九种参数形状和原有股票／市场条件。复用MetadataField、Element Plus日期控件、useParameterForm及首错聚焦逻辑，沿用当前布局／样式。
- 覆盖AC-PRD-RANGE-01、03、04、05、06、18、19、20的表单部分；更新本项验证报告和增量追踪。
- 不修改后端、OpenAPI／错误码、Dataset／策略／迁移、生产来源或日历；不修改只读数据查看的筛选／日期合同。结果状态、部分／未知结果、通信失败处理及失败任务页面分别由T16／T17完成；受控fixture／故障、完整浏览器闭环及真实来源由T18～T20验收。不给表单增加股票集合、取消、进度、自动重试、日期分批循环、未经确认的未来／历史日期限制。
- 保留当前分支、HEAD、原索引和ISSUE-017等任务外工作；新增正式文件加入Git，不提交／发布。本文准备阶段仅写设计／交接／看板，不实施T15代码或运行其功能测试。

## Approach

### 1. 直接输入及当前差距

依次读[PRD v1.4 §2.2、§3、§7](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD v1.10 §4.3、§8.3、§10.1](../design/Tensor_区间下载_TRD_v1.0.md)、现有DynamicParameterForm／useParameterForm／DownloadView／useDownloadFlow／downloads API，再消费[T14设计](RANGE-T14-design.md)和[最终验证](../verification/RANGE-T14-http.md)。共享来源文件名v1.0不代表正文版本。

T14已确认：`GET /api/v1/data-sources/{pluginId}/apis`的parameters是下载投影；downloadPolicy恰含mode、dateSemantic、description、calendarProfile、limits。四种区间模式limits为`{maxRangeDays:31}`，ORIGINAL_PARAMS的limits=null。参数两端为必填DATE_RANGE_MEMBER且relatedParameter互指，非日期条件在前。`POST /downloads`仅接受string标识及声明的字符串条件，38项不再接受旧trade_date／ann_date／month。T14实际49项／九形状、生产Spring mapper／servlet及39／80／236／114项聚焦验证、829／832项完整Java构建和各170项前端回归通过，原来源能力未升级。

当前前端表单只接parameters，日期控件已经使用YYYY-MM-DD，归一化已输出YYYYMMDD并处理股票trim／大写；通用校验已有必填／类型／pattern／enum／配对逆序，但没有自然日上限或月份摘要。DownloadView尚未传策略，零参数时跳过表单直接提交；ApiDescription只展示旧queryMode。useDownloadFlow切换来源／接口会清理结果，修改参数不会。当前结果展示只覆盖旧状态；本项只修改输入、清理和类型说明，不把T16的状态展示顺带实现。

### 2. 模式、标签与常驻说明

新增最小`control-plane/src/utils/downloadPolicy.js`，集中导出`isRangeMode(mode)`、`dateLabels(policy)`和`downloadPolicyError(api)`，供下载组件使用。只认五个公开mode，日期标签从mode及公开dateSemantic决定；不按queryMode或接口名猜测模式，不读取私有来源策略。现有ApiDescription的api prop保留，将“查询方式”一项改为“下载方式”，按下表展示；底层queryMode元数据和数据查看页面不改。

| mode／公开语义 | 下载方式／两端标签 | 常驻说明 |
|---|---|---|
| TRADE_DATE_RANGE | 交易日期区间；开始交易日期／结束交易日期 | 按交易日期下载，包含起止日期；自动跳过适用交易日历确认的休市日。个股停牌不作为市场休市处理。 |
| ANN_DATE_RANGE | 公告日期区间；开始公告日期／结束公告日期 | 按公告日期下载，包含起止日期及周末、节假日；公告日期不等于财务报告期或事件生效日期。 |
| MONTH_RANGE | 覆盖月份；开始日期／结束日期 | 按所选日期覆盖的完整月份下载，月度数据不按日切分。 |
| NATIVE_RANGE／CALENDAR_DATE | 原生日期范围；开始日历日期／结束日历日期 | 下载所选交易所的日历日期范围，包含开盘及休市记录，不套用交易日期下载的休市过滤。 |
| NATIVE_RANGE／IPO_DATE | 原生日期范围；开始申购日期／结束申购日期 | 按IPO申购日期范围下载，申购日期不等同于上市日期；实际筛选与完整性仍需来源核实。 |
| NATIVE_RANGE／ANN_DATE | 原生日期范围；开始公告日期／结束公告日期 | 按名称变更公告日期范围下载，不将起止日期解释为名称有效期；实际筛选与完整性仍需来源核实。 |
| ORIGINAL_PARAMS | 保留原条件；不新增日期控件 | 当前接入方式不支持按历史区间下载；请按现有条件获取数据。 |

同时以普通文本显示服务端`downloadPolicy.description`，保留其中来源／日历／完整性未确认的限定，不用v-html。三个原生接口目前已发布的dateSemantic分别为CALENDAR_DATE／IPO_DATE／ANN_DATE；这是公开文案依据，不是筛选字段、包含性或真实取全的验收证据。不得去掉T14返回的保守说明。

`weekly`／`monthly`在交易日期说明后追加PRD固定说明：“结果保持周线／月线粒度；所选日期用于匹配来源记录的交易日期，不生成每日记录，也不自动扩展为整个周／月。”分别使用周线／月线，不混同MONTH_RANGE的完整月份行为。该特殊补充仅控制文字，不改变参数或客户端请求数量。

四种区间表单常驻显示“单次最多支持 {maxRangeDays} 个自然日（含起止日期）”及“区间下载开始后不可终止。”，数字从服务端limits取得，Vue／校验器无31默认值。ORIGINAL_PARAMS不显示日期范围上限或区间摘要，不说“仅有最新快照”“上游不支持历史”。无参数仍显示现有“此接口无需填写请求参数。”并允许提交空对象。

`downloadPolicyError(api)`在已选接口缺策略、未知mode、区间limits缺失／非正整数、区间两端缺失／类型错误／非必填／互指错误或仍含旧日期键，以及原条件带新两端时返回固定“下载配置不完整，请重新加载页面。”；正常返回null。NATIVE_RANGE遇未知dateSemantic同样明确配置错误，不猜标签。DownloadView在表单区域用role=alert展示该文字，禁用下载且handleSubmit首先拒绝；零参数快速路径也必须经过此检查。不静默退回旧日期、不补31、不自动修正范围或自动请求。普通传输元数据错误继续走既有失败／重载流程。该检查仅保护下载策略依赖，不另建通用schema引擎。

### 3. 表单接口与纯日期运算

DynamicParameterForm保留`parameters`、`disabled`和暴露的`validate()`、`normalizedValues()`、`reset()`；新增可空`downloadPolicy` prop和无payload的`change`事件。下载页始终传当前策略；无策略的直接通用组件使用保持旧行为，供现有所有字段类型／generic relatedParameter回归使用，生产页面则由上述配置检查阻止缺策略提交。

对四种区间模式，组件用computed生成参数副本：只将start_date／end_date的label替换为dateLabels，去掉这两项的defaultValue使选接口／reset后日期为空；原非日期descriptor、顺序、必填、allowedValues、pattern和默认值保留。不修改props里的数组／对象，不从上一接口填回日期。11项按原数组渲染，不新增／删除其原参数；fixture的ORIGINAL_PARAMS／scenario仍是合法普通表单。

`useParameterForm(parameters, { maxRangeDays } = {})`只增加可选ref／computed上限参数；默认undefined表示不新增长度校验，所有其他调用者行为不变。DynamicParameterForm只在四种区间模式传入该ref。已有必填、类型、pattern、enum及配对逆序检查先运行；两端有效且有序时再用显示日期计算包含两端天数，超过上限将错误记在start_date，文案“单次最多支持 {maxRangeDays} 个自然日（含起止日期），请缩小区间”。沿用firstError及useFormValidation聚焦；失败返回false且normalizedValues为空，不截短日期。修改任一日期时清掉本下载日期对的过期配对／长度错误和成功快照，再计算firstError；未传上限的通用／查询调用维持既有行为。

在`utils/date.js`新增两个纯函数，复用现有严格parseDate，不改变toApiDate／toApiMonth／formatDate：

```js
inclusiveDateDays(start, end) // YYYY-MM-DD；非法／缺端／逆序返回null，否则含两端的整数天数
coveredMonths(start, end)    // 同样输入；非法／缺端／逆序返回[]，否则升序唯一YYYY-MM数组
```

自然日差采用公历年月日对应的UTC午夜差，不依赖浏览器本地时区。使用`new Date(0)`后`setUTCFullYear(year, month - 1, day)`并固定UTC零时，避免Date.UTC把0001～0099映射到1901～1999；严格解析先拒绝0000和非法日期。月份使用整数`year * 12 + month - 1`枚举，并把年份补足四位；不通过时区格式化，也不按月份字符串的月号单独去重。不引入日期依赖库。

DynamicParameterForm在字段网格之后、提交按钮之前显示摘要；两端合法、有序且未超过当前上限才生成摘要，不预估请求次数、记录数或交易日数：

- “所选范围：2026-08-15 至 2026-09-03，共 20 个自然日”。
- MONTH_RANGE追加“实际覆盖月份：2026-08、2026-09，共 2 个月，按完整月份下载”。

月份枚举只在输入长度合法后执行；单日仍显示所在完整月份。2026-01-31～2026-03-02为31天，显示2026-01／02／03三个月，提交仍是原两端；不得用展开后的1月1日～3月31日天数拒绝。缺失／非法／逆序／超长时不显示旧摘要。复用现有正文／辅助色与间距，摘要跨两列；680px以下沿用现有单列，不改全局主题或页面结构。

### 4. 一次请求、清理与当前状态边界

DownloadView为表单传downloadPolicy，并以`pluginId + ':' + apiName`为key，确保同形状接口切换也重新初始化。校验配置后仍按既有handleSubmit流程：有参数先await validate，再取只包含当前声明字段的规范化副本；零参数传`{}`。useDownloadFlow.submit仍只调用一次downloadDataset，后者仍只POST `/downloads`，不在浏览器枚举日期／月份发请求。

DynamicParameterForm只在用户通过未禁用控件修改值时发change，初始化／reset／props变更不伪造用户编辑。useDownloadFlow增加公开`parametersChanged()`：locked时不操作；否则清理result、error及failedOperation并回到READY。DownloadView监听change调用它。切换来源／API保留既有generation与清理机制；修改条件不会执行下载、编辑数据库任务或保留可重放旧参数的失败操作。结果对象即使是新PARTIAL等本项尚未展示的状态也必须被清空。

请求执行中沿用locked，来源／API／参数和重复提交不可操作；本项不加AbortController、取消按钮、离开页面清理服务端槽位、自动重试或超时推断。T16负责新结果状态、执行中文字及通信未确认处理；本项只交付提交前提示和既有锁定回归，不把SUCCESS／EMPTY mock测试称为新结果完整页面验收。

`api/dataSources.js`补全五字段策略及nullable limits／calendarProfile的JSDoc；`api/downloads.js`请求仍是`{pluginId,apiName,params}`，日期是字符串YYYYMMDD。响应JSDoc可按T14的18字段／六outcome更新，精确以OpenAPI为准，不借文档修改提前实现T16展示逻辑。HTTP错误处理、timeout和数据查询API保持原样。

以下各行只提交一次，原非日期默认值按实际metadata提供：

| 示例 | 精确params |
|---|---|
| daily两天 | `{"start_date":"20260901","end_date":"20260902"}` |
| income股票及公告两天 | `{"ts_code":"000001.SZ","start_date":"20260901","end_date":"20260902"}`，输入股票首尾空格／小写在提交前规范化 |
| margin市场及单日 | `{"exchange_id":"SSE","start_date":"20260901","end_date":"20260901"}` |
| broker_recommend跨三月 | `{"start_date":"20260131","end_date":"20260302"}`，不发month或三次请求 |
| trade_cal日历及交易所 | `{"exchange":"SSE","start_date":"20260901","end_date":"20260902"}`，不新增is_open |
| new_share／namechange | `{"start_date":"20260901","end_date":"20260902"}`，标签由各自dateSemantic决定，不发送ipo_date／ann_date |
| stock_basic／index_classify | 分别`{"list_status":"L"}`／`{}`，不夹带上次区间 |

## Files

以下为正式启动T15后的文件边界；不删文件，不改其他页面或ISSUE-017演示。

| 文件 | 责任 |
|---|---|
| `control-plane/src/utils/downloadPolicy.js`（新） | 最小模式判断、日期标签及下载配置错误检查；无HTTP／状态逻辑 |
| `control-plane/src/utils/date.js` | 两个纯日期／月份函数，保留旧转换表面 |
| `control-plane/src/composables/useParameterForm.js` | 可选服务端范围长度校验、下载日期对过期错误清理；默认调用不改 |
| `control-plane/src/components/download/DynamicParameterForm.vue` | 策略prop、参数副本、清空两端、范围／月份摘要、change事件；复用MetadataField |
| `control-plane/src/components/download/ApiDescription.vue` | 五类说明及原生语义、保守description、周线／月线补充 |
| `control-plane/src/views/DownloadView.vue` | 传策略／key、配置错误与提交守卫、监听参数修改、原零参数路径 |
| `control-plane/src/composables/useDownloadFlow.js` | parametersChanged清理旧结果／错误／旧失败操作，不改执行状态机 |
| `control-plane/src/api/dataSources.js`、`downloads.js` | 公开策略及新请求／响应类型说明；请求仍单次POST |
| `control-plane/src/test/fixtures/range-apis.json`（新） | 从下列真实资源投影的49项测试输入；只用于测试，不成为运行注册表 |
| `control-plane/src/components/download/RangeDownloadForm.spec.js`（新） | 49项／九形状、五模式及完整表单行为矩阵；独立于运行工具的预期 |
| 既有`DynamicParameterForm.spec.js`、`ApiDescription.spec.js`（同目录） | 保留通用类型／聚焦／禁用／props不变回归，增加策略与安全文案断言 |
| `control-plane/src/views/DownloadView.spec.js`、`composables/useDownloadFlow.spec.js`、`api/api.spec.js`、`utils/format.spec.js` | 当前UI输入mock迁移、一次请求及清理、真实Axios adapter body、日期工具／查询回归 |
| `docs/verification/RANGE-T15-form.md`（新）、`docs/traceability/tensor-range-requirements.md` | 实际T15验证和本项AC增量，不升级整个功能或来源状态 |

## Tests

正式实施第一动作：记录分支／HEAD／原索引及任务外文件摘要，按以下固定来源准备49项测试输入，再先写三条行为RED：①daily提交两端且旧字段不出现；②31天跨三月摘要、32天拒绝且下载调用0；③修改参数后旧结果／旧失败重放信息清空。缺接口导致的编译／导入错误与实际行为RED分开记录，再实施最小变更。

测试fixture使用 `docs/contracts/openapi-v1.yaml` 的`x-tensor-range-targets`名单，逐项读取 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/<apiName>.yaml` 与 `download/tushare-pro-policies.yaml`：描述符顶层复制apiName／displayName／category／queryMode；策略只取五个公开字段；ORIGINAL_PARAMS的parameters逐字段保留原数组，区间模式去掉原trade_date／ann_date／month／start_date／end_date，保留其他条件并追加通用必填互指DATE_RANGE_MEMBER的start_date／end_date。生成完整JSON测试输入，保留来源说明；不得从Vue运行标签函数生成预期。独立逐项比对target的mode／calendarProfile和paramsSchema字段顺序，确认49唯一、19／15／1／3／11、38＋11及九形状。生产Java投影的运行真实性由T14已通过的49项HTTP测试作为直接证据；本fixture只证明消费该合同的前端行为，不冒称新一轮真实服务端／浏览器验收。

| 验证组 | 观察结果 |
|---|---|
| 49项／九形状 | 每项实际mount表单并填合法值、校验并取得精确键集合；38项仅新两端，11项原数组无新增日期；六个财务股票、margin／trade_cal市场、原条件股票／枚举／无参数分别保留 |
| 元数据上限 | 真实策略测试输入31允许、32拒绝；测试内改为2后2允许3拒绝且文案同步，证明无硬编码默认。缺失／非正整数limits和未知模式／原生语义、错误日期descriptor走明确配置错误、零请求，包括零参数快速路径 |
| 日期算术 | 相同日=1、合法2024-02-29、非法2026-02-29／4月31日／0000／非string／宽松格式；0001／0099／0100／9999不被Date构造重映射；逆序和缺端失败，无未来／最早历史日期禁用 |
| 月份 | 2026-01-31～03-02=31天／3月，2026-01-31～03-03=32天拒绝；跨年2025-12-20～2026-01-10=22天／2月；单日整月及同月唯一；原提交两端不被扩张 |
| 说明及安全 | 全部标签与常驻说明正确；三个原生语义来自公开字段并保留未核实限定；weekly／monthly不误当完整月份；服务端description中的HTML只显示文本；ORIGINAL_PARAMS不声称“最新快照” |
| 初值／复用 | range两端默认空，即使输入metadata带日期默认；非日期默认／枚举保留；接口同形状切换、range→original→range与reset后无旧字段／旧摘要，原props未改变 |
| 规范化／聚焦 | 股票trim大写、必填、enum／pattern和多股拒绝；非法无snapshot、字段错误可读、焦点在首错；修正另一端后旧范围错误移除，重新验证正常。MetadataField原ARIA／禁用／通用字段测试继续通过 |
| 一次请求 | View真实表单提交对应上表精确params；api.spec使用既有Axios adapter捕获POST一次及序列化body，含31天跨三月；无浏览器按日／月循环，无参数提交{} |
| 修改／切换／锁定 | 成功或失败结果后修改任一日期／股票／枚举立即清理旧result/error/failedOperation，调用0；来源／接口切换同样清理。deferred请求期间修改和二次提交均无效，结束后可发起新的首次请求；不自动重发 |
| 查询回归 | 既有数据查看筛选、通用relatedParameter、日期／月份转换及精确数字显示保持通过；没有下载上限时通用表单不被新增31天限制 |

在`control-plane/`依次运行，RED阶段只跑当前先行测试；最终每项exit0、零失败／跳过，记录实际文件／用例数量：

```sh
npm test -- src/components/download/RangeDownloadForm.spec.js src/components/download/DynamicParameterForm.spec.js src/components/download/ApiDescription.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js src/api/api.spec.js src/utils/format.spec.js
TZ=UTC npm test -- src/utils/format.spec.js src/components/download/RangeDownloadForm.spec.js
TZ=America/Los_Angeles npm test -- src/utils/format.spec.js src/components/download/RangeDownloadForm.spec.js
npm test
npm run build
```

时区两组验证同一自然日期／compact值／月份集合，不依赖截图猜测日期。仓库根再运行：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

本任务不改后端，因此不把重跑MySQL当作表单证据；T14的既有通过结果只能作为直接输入。表单／DOM及Axios受控测试必须实际通过，不能仅更新旧mock字段或只测helper。完整浏览器／真实下载状态闭环留T19；若本项做了视觉检查，报告具体环境和观察，不能把无后端或受控页面称为真实来源通过。完成前核对任务外源码／58生产资源／原索引及分支HEAD保持，新增正式文件加入Git，做独立规格／质量复核并记录发现与处理。

## Acceptance

1. 49项按已发布策略恰为19／15／1／3／11；38项新两端、11项原条件，必要股票和市场不丢失，旧日期不再由下载页提交。
2. 日期按YYYY-MM-DD展示、YYYYMMDD提交，无时区移动；服务端公布上限驱动文案和包含两端校验，31允许32拒绝，不静默截短／扩张。
3. 有效范围及完整月份摘要位于提交前；31天跨三个月、跨年和单日行为正确。三项原生及周线／月线文案保留公开语义与证据限定。
4. 字段非法时显示清晰错误、聚焦首个无效控件且零下载；配置不完整时显示表单级错误并禁止提交。合法提交恰好一次POST，参数只来自当前接口，ORIGINAL_PARAMS无参数可发{}。
5. 选择接口日期为空；修改条件／切换接口清理旧结果、错误和旧失败操作，不修改持久任务。执行期间保持现有锁定，不增加取消或自动重试。
6. 指定测试／build／静态合同与Git保护通过，正式验证及追踪记录本项表单证据；T16状态展示、T19浏览器和T20真实来源仍明确未在本项验收。新增文件纳入Git后按看板先记录T15完成，再准备其后继，不自动实施T16。

## Risks

- 无待补的产品事实；实施选择已固定。fixture测试输入来自真实资源和冻结合同，但不替代T14生产mapper测试或T19浏览器集成；不能用旧synthetic api_1～api_49的数量测试冒充真实49项。
- T14运行参数已同版切换，旧客户端会收到拒绝；本项不加旧参数回退。生产Tushare日历／完整来源仍为空，合法表单请求仍可能被来源门禁拒绝，不能将downloadAvailable或public policy解释为账号实测通过。
- T14的正向HTTP fixture是测试内包装，生产FixturePlugin批次能力由T18提供；本项无需改它以制造页面成功。ISSUE-008九项真实调用继续不依赖、未解决。
- T15之后结果面板仍需T16迁移，参数与请求通过不表示PARTIAL／UNCONFIRMED／断连提示已可用。只为清理旧本地状态增加parametersChanged，不在这里改造重试或错误状态机。
- useParameterForm也服务通用表单；长度校验必须显式opt-in。年份0001～0099及DST是日期实现风险，必须由纯日期及两个时区回归关闭。无需新增npm依赖、未来日期范围、查询限制或UI重新设计。
