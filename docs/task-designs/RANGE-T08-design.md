# RANGE-T08 五类下载的内存批次规划设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T08`：将一次合法首次输入转换为覆盖准确、顺序稳定、对象和时间条件冻结的内存 `FetchBatch` 序列。交易日期先确认完整日历；多日批次必须得到针对精确候选请求的来源规划确认；规划阶段不调用业务取数。

本设计消费已完成的 T05、T06、T07。当前生产来源及日历仍有明确证据缺口，受控五类规划通过不能表述为49项真实下载可用。

## Scope

- 实现五类首次下载规划、日历并集及连续开盘片段、完整年月展开、稳定排序、联合对象／时间覆盖检查及不可变计划。
- 复用 `DownloadParameterConverter`、`SourceParameterMapper`、`CalendarScope`／`CalendarDecision` 和 `FetchBatch`。新增一个精确批次的无业务取数规划 SPI，使用 T07 已有来源注册表提供条件化依据。
- 只提供可独立调用及测试的 Core 规划器，不添加 Spring Bean 或切换现有 `DownloadService`、HTTP 绑定、元数据 DTO、日志、fixture scenario 或前端；T12／T14负责后续接入。
- 不实现 T09 恢复单元划分、适配／入库、失败任务、持久化计划、未开始项预登记、执行槽位、重试编排、分页、自动拆小失败批次、续页、合并失败项或自动重发。不创建股票全集或“股票×日期”笛卡尔积。
- 不改49份 Dataset YAML、生产策略及 schema、来源研究、证据状态或 `CompletenessStatus`；不新增生产来源、来源配置开关、猜测字段、真实凭证读取或外网业务调用。ISSUE-008九项继续“不依赖，未解决”。

## Approach

### 1. 输入依据与已经存在的合同

按看板顺序读取 [PRD §3～§5.1](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD §4.4](../design/Tensor_区间下载_TRD_v1.0.md)、当前 `core/download/`，再完整读取 [T05设计](RANGE-T05-design.md)／[验证](../verification/RANGE-T05-parameter-conversion.md)、[T06设计](RANGE-T06-design.md)／[验证](../verification/RANGE-T06-calendar-confirmation.md)、[T07设计](RANGE-T07-design.md)／[验证](../verification/RANGE-T07-complete-batch-fetch.md)及其实现。

| 当前事实 | T08固定处理 |
|---|---|
| `bindInitial`已按投影校验真实日期、必填、枚举、股票规范化及包含两端31天 | 必须最先调用，不复制参数校验器，不先日历过滤或月份展开 |
| `sourceRequestMode`和`batchPlanning`只是候选，完整性枚举只有UNCONFIRMED | 候选只决定能构造什么形状；不能据此宣称多日可完整获取，不新增CONFIRMED或`complete=true` |
| `SourceParameterMapper`对DATE来源只接受DATE selector；RANGE来源可接受DATE或RANGE | DATE候选不尝试起止请求；RANGE候选单日仍生成相等start_date/end_date，不能猜改单日期字段 |
| `RecoverySelector.RANGE`要求两个端点严格不同 | 所有单点时间使用DATE；原生单日仍是一批相等来源起止 |
| T06返回完整Decision；生产19项均仍日历未确认 | Core只对TRADE_DATE_RANGE调用confirmCalendar，严格检查返回scope相等；不能自行推测必要身份或周末规则 |
| T07生产完整批次来源注册表固定为空；默认fetchBatch明确拒绝 | 新规划入口共享该表并默认拒绝；规划不调用fetchBatch或旧download；后来执行仍独立走fetchBatch全流程 |
| 当前49项均REQUEST；8项请求未确认、forecast冲突，其余40候选；trade_cal BSE另有请求缺口 | 不升级恢复策略；请求和完整性拒绝分别保留；不增补股票、换VIP或误用报告期／解禁日期 |

T08发现的接口缺口是：Core没有方法在业务请求之前知道某个精确候选能否按其来源合同完整取数。它是实现所需的最小跨层合同补充，不是新的产品要求。新接口只做来源合同预检；来源的实际结束、全集和分页核查继续完全属于T07。

### 2. 精确批次预检 SPI

在 `DataSourcePlugin` 增加一个 default 方法，复用既有枚举，不增加新的能力对象或通用策略框架：

```java
default DownloadPolicy.BatchPlanning planBatch(
        ApiName apiName, FetchBatch batch, DownloadContext context);
```

缺省实现检查三个参数非null、调用一次 `context.checkServerState()`，然后抛 `SourceException(SOURCE_COMPLETENESS_UNCONFIRMED, "Source completeness is unconfirmed")`。它不委托 `download`、`fetchBatch` 或 `confirmCalendar`，不返回候选元数据作为答案。

`batch`是已经经T05转换的**精确来源参数**和原恢复策略。这个方法只读取已建立的来源合同并判断当前对象／市场／日期条件是否满足它，不执行 `source.open`、会话方法、业务client、网络探测、存储或缓存更新。来源确实需要运行后才能判断的实际完整性留到fetchBatch；缺少适用合同则在此明确拒绝。

返回值的含义与 `DownloadPolicy.batchPlanning` 中的静态候选不同：这里是插件对这一次精确输入给出的明确规划建议，不能缓存成整个接口的全局能力。允许组合固定如下：

| 页面模式／候选来源 | 返回值与处理 |
|---|---|
| TRADE_DATE_RANGE或ANN_DATE_RANGE，来源DATE，当前为单日批 | 仅SINGLE_DATE可直接加入计划；任何其他值均不兼容，不改造成RANGE |
| TRADE_DATE_RANGE或ANN_DATE_RANGE，来源RANGE，当前多日连续批 | SOURCE_RANGE表示该完整批可规划；SINGLE_DATE表示合同只建议逐日，丢弃此候选并逐个构造、重新预检全部单日批 |
| 上述来源RANGE，当前单日相等起止 | SOURCE_RANGE或SINGLE_DATE都表示可保留这一个相等起止批，不再分解 |
| MONTH_RANGE／MONTH | 仅SINGLE_MONTH，保留完整月 |
| NATIVE_RANGE／RANGE | 仅SOURCE_RANGE；无论多日或单日都不接受SINGLE_DATE建议，不改变原生一批合同 |
| ORIGINAL_PARAMS／NONE | 仅ORIGINAL_PARAMS，保留完整原条件 |

`null`或UNCONFIRMED不是合法肯定建议；来源应抛明确异常。Core遇到这两种返回以固定SOURCE_COMPLETENESS_UNCONFIRMED拒绝；其他不兼容枚举以固定SOURCE_REQUEST_UNCONFIRMED拒绝。原生返回SINGLE_DATE属于不兼容建议，明确拒绝，不退化成日请求。来源返回SINGLE_DATE不是“本批失败后再试”，没有业务请求发生；只有这一肯定建议允许交易／公告范围在规划阶段前置分日。所有SourceException原样传播，**不捕获任何来源错误来尝试另一方式**。不能据限流、超时、截断或完整性未知自动缩小范围。

新方法不返回完整性许可、不写入FetchBatch标志或改变任何证据状态。成功规划也不意味着获取已完成；后续必须把冻结的FetchBatch交给 `fetchBatch`。后者仍从新会话开始、验证适用条件、每页、结束依据及全集，不接受预检结果作为免检标记，也不回退旧download。

### 3. Tushare接线与生产拒绝

在既有 `TushareBatchSource` 增加 default 方法：

```java
default DownloadPolicy.BatchPlanning plan(
        DatasetDefinition definition, ApiDescriptor api, FetchBatch batch);
```

缺省抛现有 `TushareErrorClassifier.completenessUnconfirmed()`。保持 `open` 为原有抽象方法，因此原T07测试的lambda／Session及取数调用无需改造；没有覆写plan的合同即使能在受控fetch中取数，也不能自动授权规划。测试需要成功规划时显式实现plan，并按收到的精确参数匹配脚本，而不是恒返回策略候选。

在 `TushareCompleteBatchFetcher` 新增：

```java
public DownloadPolicy.BatchPlanning plan(
        DatasetDefinition definition, ApiDescriptor api,
        FetchBatch batch, DownloadContext context);
```

顺序固定为：四参数非null→初始context检查→复用fetch现有的定义／API／插件身份、recoveryPolicy相等、DOCUMENTED_CANDIDATE及来源方式存在、trade_cal BSE拒绝→查同一只读sources→调用 `source.plan(definition, api, batch)`→检查结果非null且不是UNCONFIRMED→最终context检查→返回。缺来源为SOURCE_COMPLETENESS_UNCONFIRMED；null／UNCONFIRMED同样完整性未确认。身份／请求证据不满足用现有requestUnconfirmed工厂。`fetch`和`plan`可抽出一个私有“校验并查source”辅助方法消除重复，但fetch的检查顺序、会话和context次数保持原有合同。

不能在plan中调用open来偷做预检，也不增加第二张来源表。每次调用只用本轮参数，来源合同不得将上次建议带到本次。source.plan没有context参数，执行器在回调两侧承担检查；不捕获来源或服务端异常，故障实例保持。规划建议不能由返回行数、Tushare msg、未公开的total／cursor或DOCUMENTED_CANDIDATE自动推导。

`TushareProPlugin.planBatch`沿用fetchBatch入口的顺序：apiName／batch非null→readiness→查definition及api→context非null并检查→委托completeBatchFetcher.plan。不可用为PLUGIN_DISABLED，未知API为现有固定 `Unknown Tushare API`。生产四参构造器仍只创建现有空sources执行器；现有包可见测试构造器足够，不添加配置或公共注入入口。日历provider同样保持空表。

直接调用生产planBatch时，8项未确认＋forecast冲突为SOURCE_REQUEST_UNCONFIRMED，其余40项为SOURCE_COMPLETENESS_UNCONFIRMED；trade_cal BSE另为请求未确认。所有这些检查的source.plan、source.open和业务client均为零。通过Core规划交易日期时会先因生产日历未确认而拒绝，不将直接SPI的9／40分组误记为Core每个接口的错误优先级。

### 4. Core最小入口与冻结结果

新增一个 `core.download.DownloadBatchPlanner`，只依赖现有converter与plugin-api。结果类型嵌套于该类：

```java
public DownloadBatchPlanner(DownloadParameterConverter converter);

public Plan planInitial(DataSourcePlugin plugin, ApiDescriptor api,
        Map<String,Object> rawParams, DownloadContext context);

public record PlannedBatch(RecoverySelector scope, FetchBatch fetchBatch) {}
public record Plan(DatasetKey datasetKey, ValidatedParameters originalParams,
        DownloadParameterConverter.OriginalDateRange originalDateRange,
        Set<LocalDate> skippedDates, List<PlannedBatch> batches) {}
```

构造器及入口对象非null；集合使用防御副本，原参数沿用ValidatedParameters的不可变String map，PlannedBatch各字段非null。Plan.originalDateRange只在ORIGINAL_PARAMS为null；其余从已校验的首次start_date/end_date解析，不从过滤后日期或月份倒推。skippedDates只存确认休市日期，其数量即后续可消费的H；不生成S／F／N或记录数。实际时间由每个scope精确表达，MONTH表示整个年月，NONE没有时间。计划无需另存展开后的首尾日期或重复的自然日总数。

所有首次批次scope均为 `REQUEST, ""`，时间为DATE／RANGE／MONTH／NONE。这里scope记录请求覆盖边界，**不是已确定的独立恢复单元**；FetchBatch仍携带api原recoveryPolicy，T09才处理来源行归属及是否能进一步隔离。即使受控策略为STOCK_TIME，T08也不从输入、市场或返回行推造股票单元，不删除公共ts_code。REQUEST内的规范股票／市场／枚举条件决定实际请求对象。

入口顺序固定：

1. `converter.bindInitial(api, rawParams)`完成全部初始校验；失败直接保留PARAM_REQUIRED／PARAM_INVALID，此时没有日历或来源规划回调。用返回值冻结原输入，随后不再读取rawParams。
2. 调用context初始检查；核对plugin.descriptor中同名ApiDescriptor与传入api完全相等，且对应DatasetKey列于descriptor.datasets；不匹配抛私有固定TensorException(DATASET_MISCONFIGURED, `Download dataset is unavailable`)。检查plugin.readiness().downloadAvailable，失败为私有固定TensorException(PLUGIN_DISABLED, `Download plugin is unavailable`)。不访问注册器／适配器／数据库，也不读取凭证值。
3. 区间模式解析原始两端，按LocalDate升序展开至多31个自然日；ORIGINAL_PARAMS不解析或构造日期。循环在处理end后结束，避免9999-12-31再加一天；月份循环同样避免9999-12之后再加月。不引入系统时区、当前日期或最早历史日条件。
4. 仅交易模式调用一次confirmCalendar，入参为 `new CalendarScope(originalParams.values(), exactInitialDates)`；包括原股票／市场及原始日期。前后分别context检查，返回非null且decision.scope必须与传入scope完全相等；错误scope或null抛新的CalendarUnconfirmedException。必要身份、覆盖及修订的权威判断仍由T06提供器负责，Core不复制市场映射。
5. 交易模式只消费decision.openDates并集，skippedDates为原自然日集合减openDates。不按星期过滤，不因某市场关闭取消其他开盘市场，不将股票停牌看成休市。全闭市时跳过第6／7步，以空batches进入第8步，仍完成覆盖和最终context检查后才返回合法空计划；source.plan／planBatch及两种download取数入口均为零。其他模式skippedDates为空且confirmCalendar调用零次。
6. 除上述全闭分支外（包括参数为{}的原条件请求），先要求requestEvidenceStatus为DOCUMENTED_CANDIDATE且sourceRequestMode非null，否则固定SOURCE_REQUEST_UNCONFIRMED；不能对null候选做switch或猜形状。按下节构造候选，通过 `converter.mapInitial(api, originalParams, scope)`得到来源参数，再创建 `FetchBatch(mapped.sourceParams().values(), api.downloadPolicy().recoveryPolicy())`。不能手写来源日期键；mapInitial拒绝即结束，不查另一候选。每个mapped.originalDateRange须等于冻结原始范围（原条件均null）。
7. 按候选时间升序，在每次plugin.planBatch调用前后分别context检查并消费第2节建议。允许的显式逐日建议只展开当前连续片段，逐日再执行mapInitial和planBatch；每个最终批都必须得到直接可用的建议。已经预检通过且未改动的批不再次预检。任何异常立即中止，不返回前缀计划，不继续后面的预检，不发业务请求。
8. 全部最终批通过后执行联合覆盖检查，再执行最终context检查，返回不可变Plan。没有日历Decision或预检结果的跨调用缓存；相同输入第二次planInitial仍重新确认和规划。任何服务端检查故障实例原样传播，不宽泛捕获RuntimeException／Error。

本任务不提供planRetry入口。T13须以现存失败选择器决定实际范围并冻结原恢复边界；不得把本入口的原始区间作为重试范围使用。首次日历过滤产生不连续开盘日期时，已经在本项证明不跨空档组批。

### 5. 五类候选与具体序列

以下“可用”均指受控来源plan返回对应肯定建议，实际fetch仍未执行。

| 模式 | 确定算法 |
|---|---|
| TRADE_DATE_RANGE | DATE候选：按openDates升序每个DATE一批。RANGE候选：按自然日期相邻关系分成最大连续开盘片段；单点DATE，多点RANGE；优先预检整个片段，可用则保留，明确SINGLE_DATE才分日并全部重新预检。不开跨休市空档的范围 |
| ANN_DATE_RANGE | DATE候选：按全部自然日升序逐日。RANGE候选：先预检原完整范围（单点为DATE）；可用则一批，明确SINGLE_DATE则逐自然日重建并预检。不调用日历，不删周末或假日 |
| MONTH_RANGE | YearMonth.from(start)到YearMonth.from(end)，按年月升序，每个不同年月一个MONTH batch；不裁剪为输入日，不把扩展后的月份首尾用于31天校验 |
| NATIVE_RANGE | 原始两端及全部原条件恰一批，单日用DATE scope转换为相等起止；只接受SOURCE_RANGE；任何失败或逐日建议都拒绝，不拆分、不过滤trade_cal的休市记录 |
| ORIGINAL_PARAMS | `REQUEST,"",NONE,""`恰一批；原map（含规范化条件）完整保留，无参数接口为{}，没有日期或日历 |

对于SOURCE_RANGE候选，只支持SINGLE_DATE或完整当前范围这两种已经需要的前置方案。没有新的已核实固定分段上限，故不增加每批N天、均匀分块、二分探测或让来源返回任意分段树。未来确有不同合法分段合同另行设计，当前无法采用合法方式则明确拒绝。

| 受控样例 | 精确期望 |
|---|---|
| margin，exchange_id=SSE，20260901～20260907；开盘1／2／4／7日，RANGE建议可用 | scopes为RANGE `2026-09-01/2026-09-02`、DATE `2026-09-04`、DATE `2026-09-07`；来源map分别为保留exchange_id的1～2、4～4、7～7起止，H=3 |
| 同一margin，1～2片段明确SINGLE_DATE | 原1～2候选仅预检；最终1、2、4、7四个DATE批，各自独立预检，相等起止均保留SSE；没有3／5／6日来源范围 |
| daily，DATE候选，开盘1／2／4／7日 | 仅四个trade_date请求；即使元数据存在起止投影也不构造多日候选；若插件建议SOURCE_RANGE则拒绝 |
| income，ts_code=` 000001.sz `，20260904～20260907，RANGE建议可用 | 一批 `{ts_code:'000001.SZ', start_date:'20260904', end_date:'20260907'}`，包含5／6日周末；日历零调用。明确SINGLE_DATE则四个保留代码的相等起止批 |
| top_list／top_inst、dividend／disclosure_date的DATE候选 | 前两者仅按确认开盘日的trade_date，后两者按全部自然日的ann_date；没有猜测start_date/end_date。top_inst仅受控测试，真实排除不变 |
| fina_indicator的公告DATE候选 | 使用ann_date单日；不把公告范围转到报告期起止。fina_mainbz等缺方式及forecast冲突明确拒绝 |
| broker_recommend，20260131～20260302 | 31天合法；MONTH scopes为2026-01／02／03，map为month=202601／202602／202603，实际完整覆盖1月1日～3月31日；原始范围仍1月31日～3月2日 |
| broker_recommend，20260215～20260215；20261231～20270101 | 前者一个完整2026-02；后者2026-12、2027-01各一次，按年份区别，日历零调用 |
| trade_cal，exchange=SSE，20260904～20260906 | 一批原起止及SSE；不查询辅助日历、不添加is_open。new_share／namechange同样只保留原范围含义，不推断新字段 |
| 三原生接口20260903～20260903 | 每个恰一批DATE scope，来源start_date=end_date=20260903；trade_cal保留exchange |
| stock_basic list_status=L；stock_company exchange=SZSE；stk_holdernumber／stk_rewards ts_code=000001.SZ；index_classify无参数 | 在受控来源确认下每个恰一批NONE，精确保留各原条件或{}；其他原条件接口按下表验证，不新增时间或证券条件 |
| 任一区间20260131～20260303 | 32天，PARAM_INVALID；confirmCalendar、planBatch、fetchBatch、download均零调用，不能通过分月／分日接受 |

11个原条件接口的实际输入及来源门槛逐项固定如下，不能把“保持原请求”解释为绕过未知方式：

| 接口 | 合法公共条件示例 | T08受控验证边界 |
|---|---|---|
| stock_basic | `{list_status:'L'}`，另测P／D | NONE候选，肯定预检时一批原map |
| stock_company | `{exchange:'SZSE'}`，另测SSE／BSE | NONE候选，肯定预检时一批原map；不证明各市场真实取全 |
| stk_holdernumber、stk_rewards | `{ts_code:'000001.SZ'}` | NONE候选，肯定预检时一批保留股票 |
| index_classify、index_member_all、pledge_detail、pledge_stat、stk_managers | `{}` | NONE候选，肯定预检时每个一批{}；尤其pledge_stat无股票输入，不补ts_code |
| hs_const | `{hs_type:'SH'}`，另测SZ | 公共绑定保持原条件，但当前sourceRequestMode=null／UNCONFIRMED，SOURCE_REQUEST_UNCONFIRMED且planBatch为零 |
| index_member | `{}` | 公共绑定合法，当前来源方式未确认，SOURCE_REQUEST_UNCONFIRMED且planBatch为零 |

以上9个NONE候选在真实生产预检中仍因空来源注册表返回SOURCE_COMPLETENESS_UNCONFIRMED；受控正例与2项无方式拒绝分开报告，不为凑齐11个正例修改真实策略。

### 6. 联合对象与时间覆盖校验

在DownloadBatchPlanner内使用一个包可见实例方法，便于同包测试直接注入坏序列，无需新增通用验证器类：

```java
void validateCoverage(ApiDescriptor api, ValidatedParameters original,
        Set<LocalDate> skippedDates, List<PlannedBatch> batches);
```

它只在计划返回前验证已冻结结构，不重新调用日历或来源plan。非法规划统一抛固定SourceException(SOURCE_REQUEST_UNCONFIRMED, `Source request conditions are unconfirmed`)，不带输入值、cause或suppressed。只显式检查本节列出的矛盾，不捕获任意运行故障。

1. 每个scope必须REQUEST且targetValue为空，fetchBatch.recoveryPolicy须与api策略相等。再次调用mapInitial重建预期map，要求与batch.sourceParams逐键相等。这是本地重建，不是再次预检；它同时保证股票、交易所、枚举、无参数原条件及日期形状均未删除、增加或改值。公共对象键取 `original.values` 去掉start_date/end_date之后的整个不可变map，不能只看股票或假定空map代表一个已知股票全集。
2. 用 `(公共对象条件map, 时间原子)` 检查重复、遗漏和扩大。日期三类的时间原子为LocalDate；从每个DATE或RANGE scope展开其真实包含日期。月份为YearMonth原子，不逐日展开MONTH；NONE使用单个无时间标记。无需建立股票列表或跨调用全局集合；两份原输入分别为000001.SZ和600000.SH时，同日应分别合法，不能按日期对两个对象去重。
3. 交易模式skippedDates必须是原自然日集合的子集，期望覆盖为原日期减skipped；其他模式skipped必须为空。公告／原生的期望覆盖为原全部自然日，月份为原输入覆盖的全部YearMonth，原条件为一个NONE。MONTH扩大至整月属于规定语义；其他日期范围不得扩大。
4. 扫描每个批加入时间原子时若已存在即拒绝，不能先放Set再仅比较最终并集掩盖重叠；最终集合须与期望精确相等。序列须按时间严格升序（多点结束早于下一批开始），不接受逆序后静默排序。MONTH年月升序且去重；ORIGINAL_PARAMS严格一批；NATIVE_RANGE严格一批并与原范围完全相等。
5. 对DATE候选要求所有scope为DATE；对MONTH／NONE要求对应时间类型；对RANGE候选单点DATE、多点RANGE。交易的RANGE内部不能包含任何skippedDate。仅交易全闭市允许零批，其他合法输入空计划均为遗漏。正确并集不掩盖错误对象map、策略、日期形状或原生拆分。

覆盖方法不证明source是否取全，也不证明日历来源权威；它只证明规划器实际发出的对象／时间与已校验输入、T06结论和确定的五类规则一致。冻结计划不包含来源Session、可变游标、原响应或确认布尔值。

## Files

下列为T08启动后待实施文件；本次设计阶段只创建本设计。

- 新增 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadBatchPlanner.java`：首次规划、嵌套Plan／PlannedBatch、日期片段与联合覆盖验证；新增 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadBatchPlannerTest.java`。Core测试使用本模块受控ApiDescriptor和内嵌DataSourcePlugin，不增加对Tushare模块的依赖。
- 修改 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`：精确planBatch默认拒绝；修改同模块 `PluginApiSurfaceTest.java`、`download/DownloadContractsTest.java`：精确签名、default属性和零其他回调。
- 修改 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareBatchSource.java`：default plan；同目录 `TushareCompleteBatchFetcher.java`：同一注册表的无业务预检；修改 `TushareProPlugin.java`：覆盖planBatch。复用已有错误工厂，不新增错误码或新源码类型。
- 修改同Tushare模块 `client/TushareCompleteBatchFetcherTest.java`、`TushareProPluginTest.java`：受控精确建议、生产49项及具体条件拒绝、旧fetch完整验证及download回归。受控脚本仅位于test源码，原T07会话测试保持其独立入口语义。
- 新增 `docs/verification/RANGE-T08-memory-batch-planning.md`，更新 `docs/traceability/tensor-range-requirements.md`：保存本轮命令、实际数量、先行失败、资源审计、规划层AC证据及仍未验证事项。
- 不修改FetchBatch／RecoverySelector／CompletenessStatus、SourceParameterMapper、DownloadParameterConverter的既有合同、49份Dataset、两份生产策略资源、Core DownloadService、Web／fixture运行路径、数据库或生产配置。新文件加入Git版本控制，不覆盖前项已暂存修改，不提交或发布。

## Tests

首个实施动作：在新DownloadBatchPlannerTest写入broker_recommend的20260131～20260302三完整月及32天零回调反例，先运行确认规划器缺失，再实施最小骨架；随后先写精确planBatch默认拒绝和RANGE显式逐日建议用例，运行确认缺失／失败后补齐接口与算法。编译缺类型和运行行为失败分别记录，不把测试设置错误当业务证据。

受控Core插件必须记录 `confirmCalendar`、`planBatch`、`fetchBatch`、`download`事件和精确参数；两种取数方法都立即抛AssertionError，任何规划用例误调用即失败。日历用明确日期和必要身份的完整表，不用周末函数。来源建议按预置精确map匹配，未登记请求明确拒绝；原始证据资源保持不变。Tushare测试直接使用真实49描述符／策略和T07来源端口，不引入真实Token或网络。

| 验证组 | 必须观察的结果 |
|---|---|
| 五类与精确样例 | 上节全部序列逐个断言scope、sourceParams、recoveryPolicy、originalParams、originalDateRange和H；完整月份／自然日／原生含休市的差异明确，不仅断言批次数 |
| 范围优先及显式分日 | RANGE完整片段确认只预检一次；返回SINGLE_DATE后逐个单日预检，最终只含单日。另测第2个单日预检拒绝：无Plan、后续预检停止、业务零次。来源抛SOURCE_REQUEST_UNCONFIRMED／SOURCE_COMPLETENESS_UNCONFIRMED／SOURCE_TIMEOUT等任一异常都不触发降级或自动再调用 |
| 候选与返回不兼容 | DATE候选收到SOURCE_RANGE拒绝，实际没有构造起止map；MONTH／NONE收到日期建议拒绝；原生多日及单日收到SINGLE_DATE都拒绝。null／UNCONFIRMED为完整性未确认，不当单日兜底 |
| 日历顺序与scope | 31／32天、缺端、非法日期、枚举、旧日期混用先拒绝；合法交易仅一次confirmCalendar，完整原条件和精确原日期传入；Decision的日期相同但publicParams被改、scope日期错或null均CALENDAR_UNCONFIRMED且planBatch为零。多市场并集、全闭空计划、开盘日单点及多个空档均覆盖 |
| 非交易绕过 | 公告包含周末、月份展开、trade_cal／new_share／namechange、11项原条件均confirmCalendar零次。生产原条件不因绕过日历自动通过完整性门槛 |
| 边界 | 合法闰日、跨年、单日、00010101、99991231均不溢出；0000、非闰日、逆序、非字符串仍沿用参数错误。三月示例31天可用、32天拒绝；完整月扩展不重新套31天 |
| 覆盖反例 | 对validateCoverage注入重复日期、重叠RANGE、缺日、越界、跨休市空档、逆序、月份重复／缺失、原生拆为两批、原条件两批、非交易skipped非空、全闭以外空计划，均明确拒绝；不能只比较集合最终并集 |
| 对象与参数反例 | 同日期把000001.SZ换600000.SH、删ts_code、删exchange_id／exchange、改枚举、增股票／日期键、改recoveryPolicy、把REQUEST改STOCK均拒绝；分别以两股原始输入规划同日都成功且对象独立。11个原条件按第5节逐项验证，9项NONE候选可受控预检成功、2项当前无方式明确拒绝，不为pledge_stat补股票 |
| 不可变与重复调用 | 修改raw map不影响Plan；修改skippedDates／batches／sourceParams等失败，外部集合变更不污染副本；相同输入第二次规划重新confirm／plan。首次原1～7日展示不因实际1／2／4／7日或月份展开改变 |
| 服务端错误 | 初始context故障时日历／plan均零；日历后、预检前后及最终检查故障均无Plan且保持同一异常实例。比较事件顺序，确保日历完成后才预检、覆盖完成后才返回，无Thread.interrupted／客户端取消逻辑 |
| Tushare无业务预检 | 真实49项9／40分组及trade_cal BSE拒绝；stock_basic L/P/D和trade_cal SSE/SZSE不误启用。受控精确map建议能返回；缺default plan明确拒绝；open／paginationParameters／pageParameters／observe／validateComplete／client均零次 |
| 与fetch的独立性 | 对受控来源先成功plan，再让fetch.open拒绝、观察UNCONFIRMED／TRUNCATED或后页失败，仍整批失败无结果。fetch不读取任何预检许可，旧T07分页／全集／context事件数保持；规划拒绝不调用旧download补救 |
| 既有路径与资源 | 当前DownloadService／HTTP／fixture测试保持原语义；51受保护资源逐文件SHA-256不变，19／15／1／3／11、9真实排除及请求证据40／8／1不变 |

从仓库根执行，第一条用于首次定向失败及后续定向复测，其余用于完成前验证：

```sh
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadBatchPlannerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-core,tensor-plugin-tushare -am test
mvn -f data-plane/pom.xml test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

最终命令均须退出0，实际调度测试零失败／错误／跳过；保留先行失败与修正事实，记录真实执行数，不复制T07计数。Maven使用已有能支持Mockito附加JVM的执行环境，不改依赖、禁用断言或跳过检查。默认POM未调度的数据库／容器IT须明确“仅编译、未执行”；浏览器和真实业务API未运行，不以verify作为端到端或来源证据。

资源审计在实施前执行第一段Python保存基线，实施后执行第二段Python核对；这份临时清单不进入生产或Git：

```sh
mkdir -p /private/tmp/tensor-range-t08
python3 - <<'PY'
from pathlib import Path
import hashlib, json
root = Path('data-plane/tensor-plugin-tushare/src/main/resources')
paths = sorted((root / 'datasets/tushare_pro').glob('*.yaml')) + [
    root / 'download/tushare-pro-policies.yaml',
    root / 'download/tushare-pro-policies.schema.json',
]
assert len(paths) == 51
Path('/private/tmp/tensor-range-t08/resource-hashes.json').write_text(json.dumps(
    {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}, indent=2))
PY
python3 - <<'PY'
from pathlib import Path
import hashlib, json
expected = json.loads(Path('/private/tmp/tensor-range-t08/resource-hashes.json').read_text())
assert len(expected) == 51
assert all(hashlib.sha256(Path(p).read_bytes()).hexdigest() == h for p, h in expected.items())
print('PASS: 51 protected resources unchanged')
PY
```

上面最后一段是实施后核对命令，不是重建基线。按实际阶段分别执行；报告同时核对T01／T02登记分类与生产策略状态，未变化不能误记为真实来源验证通过。

## Acceptance

1. 合法输入生成五类准确序列；31天限制先于日历和月份展开，20260131～20260302覆盖三个完整月，原生一批、11原条件保持；原始输入与实际范围均冻结。
2. 只有交易模式确认日历，先确认完整scope再预检任何业务批；连续开盘片段精确，不跨休市空档。全闭合法零批、其他日历问题拒绝且没有业务调用。
3. 新planBatch只对精确候选提供已建立来源合同的规划建议；候选RANGE不自动通过。仅显式SINGLE_DATE允许交易／公告前置分日，所有最终批独立预检；异常不自动降级、拆分或重发。
4. 联合对象／时间覆盖检查能拒绝重叠、遗漏、范围扩大、条件丢失、换股和不稳定顺序；不创建股票全集、恢复单元或持久化计划。
5. 生产来源和日历表仍为空，未知能力明确拒绝；预检成功不能绕过fetchBatch独立完整性检查，现有HTTP／Core下载过渡路径保持。没有真实凭证、业务调用、数据库或失败记录副作用。
6. 规定验证及独立评审通过，交付文件加入Git；验证报告仅回填AC-PRD-RANGE-02／03／04／05／06／14／15／18的规划层及受控证据，不提前完成最终功能AC。由主流程记录T08完成后按Order准备T09，不自动实施后继。

## Risks

- 当前生产19项日历及49项完整批次仍未启用。T08成功只证明规划机制和拒绝边界；来源缺口若影响最终功能验收，仍须按后续任务取得证据，不能靠风险登记关闭项目。
- `planBatch`与来源plan是可信插件合同的预检，不能从任意枚举值数学证明真实来源支持。当前仅test实现正例；生产没有实现或开关，未来注册真实来源必须提供精确条件、日期语义及取全依据，并保留fetchBatch独立验证。
- 日历确认与完整取数是不同屏障；全闭不需要业务完整性预检，生产交易输入则通常先返回日历未确认。验收必须分别报告直接SPI和Core规划的错误优先级，不能用受控日历替代真实日历。
- 本任务只规划首次请求。T13若重用此入口按原始区间处理失败项会扩大范围，必须使用现存失败选择器单独设计；本设计没有承诺通用重试规划器或动态恢复单元拆分。
- 无需要新增用户产品决定的未决事项；已知来源未知均按既定明确拒绝规则处理。本文为T07完成后的T08设计准备，未实施T08代码或运行其功能验证。
