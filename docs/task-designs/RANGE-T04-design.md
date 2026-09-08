# RANGE-T04 插件 SPI、下载策略与描述符投影设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md) `RANGE-T04`：提供可编译、可验证的插件内存合同和49项显式下载策略，使后续参数转换、日历、取全、规划和恢复单元处理有共同输入。下载投影、来源参数和只读查询定义各自保持明确职责。

## Scope

- 扩展 `ApiDescriptor`、`DataSourcePlugin`，新增下载策略、选择器、批次、取数结果、日历结果和执行上下文；同版迁移 Tushare、fixture、唯一生产调用链和测试替身。
- 新增独立策略资源和严格加载校验，保留49份 Dataset YAML 的参数、列、业务键、查询模式与筛选定义。生产策略恰为19／15／1／3／11，不用名称或返回字段猜能力。
- 本项交付内存／SPI合同及描述符投影，既有HTTP元数据和单次下载继续按原来源参数工作。区间绑定能力由T05实现，日历由T06、完整取数由T07、规划由T08、单元处理由T09、执行与HTTP切换由T12～T14接入；不提前开放尚无执行支持的区间表单。
- 不新增旧二进制SPI兼容层、功能开关、数据库、失败任务端点、执行槽位、自动重试、取消或前端改动。不调用真实业务API，不改变ISSUE-008九项排除。
- 本设计是T03完成后的后继准备产物。看板READY只表示可按设计启动；本轮不实施T04代码。

## Approach

### 1. 直接输入和边界

依次读取 [TRD §4.1～§4.2、§4.4、§5及附录A](../design/Tensor_区间下载_TRD_v1.0.md)，本节列出的现有Java入口，再读取以下直接输入：

| 输入 | 保留的决定 | T04用法 |
|---|---|---|
| [T01报告](../research/RANGE-T01-source-capabilities.md)及[逐项JSON](../research/RANGE-T01-source-evidence.json) | 49目标；40个DOCUMENTED_CANDIDATE、8个UNCONFIRMED、forecast的CONFLICT；49项REQUEST；Q／C／P／R门槛 | 逐项登记候选方式、来源日期参数、取全缺口、证据引用和恢复门槛；不把候选写成已启用 |
| [T02报告](../research/RANGE-T02-calendar-capabilities.md)及[逐项JSON](../research/RANGE-T02-calendar-evidence.json) | 19项，C-A／C-M／C-S／C-N／C-X为12／1／3／2／1；1项静态DOCUMENTED、16项UNCONFIRMED、2项CONFLICT | 登记profile与研究状态；未实现的运行日历统一明确拒绝，不将静态资料视为当前日期已确认 |
| [T03 OpenAPI](../contracts/openapi-v1.yaml)、[错误目录](../contracts/error-codes.md)、[追踪](../traceability/tensor-range-requirements.md)、[验证报告](../verification/RANGE-T03-contracts.md) | 49目标／9形状、公共策略5字段、选择器两对象四时间、26码及证据边界 | 固定投影和类型值；公共HTTP字段不因内部SPI扩展而增加 |

T02的monthly日期冲突补充限制T01的DATE候选，不删除目标或改成月份输入。margin_detail的市场说明冲突同样保留。T01／T02输入不存在需要改产品范围的冲突；来源缺口是明确拒绝条件，后续验证不能以登记或受控测试替代。

当前 `ApiDescriptor.parameters` 同时被元数据DTO、HTTP绑定、参数校验、下载执行和参数日志消费。采用新增只读 `sourceParameters` 的方式保留原合同；它只从 `DatasetDefinition.parameters()` 派生。`parameters` 则固定为下载投影。这样无需构造“带新策略、实际装旧参数”的临时描述符，也无需让Core依赖Tushare实现类。

### 2. 描述符与策略类型

`ApiDescriptor` 的规范构造参数在现有五项后追加 `DownloadPolicy downloadPolicy, List<ParameterDescriptor> sourceParameters`。两组参数分别复制为不可变列表，禁止重复字段；策略必填。所有生产及测试构造点同版改用新构造器，不保留五参兼容构造器。`queryMode`继续原值。

`DownloadPolicy` 放在 `plugin.api.download`，为SPI内部完整策略；公共HTTP投影仅取下表“公开”字段。相关枚举和小record嵌套在该类型中，避免一枚举一文件。除明确可空项外全部必填；列表及嵌套集合防御性复制，不用 `Map<String,Object>` 容纳策略结构。

| 字段 | 固定类型／规则 |
|---|---|
| `mode`（公开） | `Mode`：TRADE_DATE_RANGE／ANN_DATE_RANGE／MONTH_RANGE／NATIVE_RANGE／ORIGINAL_PARAMS |
| `dateSemantic`（公开） | `DateSemantic`：TRADE_DATE／ANN_DATE／COVERED_MONTH／CALENDAR_DATE／IPO_DATE／NONE；对应T03 |
| `description`（公开） | 非空安全文案，见下文；不含来源正文或凭证 |
| `calendarProfile`（公开） | 可空 `CalendarProfile`：C_A／C_M／C_S／C_N／C_X，提供value()返回C-A／C-M／C-S／C-N／C-X；wire/resource用value；仅TRADE_DATE_RANGE非空 |
| `limits`（公开） | 可空 `Limits(Integer maxRangeDays)`；38项精确为31，ORIGINAL_PARAMS为null；无其他上限 |
| `sourceRequestMode` | 可空 `SourceRequestMode`：DATE／MONTH／RANGE／NONE；表示有条件的请求候选，不能单独判断可执行 |
| `sourceDateParameter` | DATE仅trade_date或ann_date，MONTH仅month；RANGE／NONE／未知为null。RANGE映射start_date/end_date固定于转换合同，不用另一份可配置字段对 |
| `requestEvidenceStatus` | DOCUMENTED_CANDIDATE／UNCONFIRMED／CONFLICT，精确保留T01；未知candidateMode映射null，forecast保留DATE＋CONFLICT |
| `batchPlanning` | SINGLE_DATE／SINGLE_MONTH／SOURCE_RANGE／ORIGINAL_PARAMS／UNCONFIRMED；分别对应候选DATE／MONTH／RANGE／NONE／null。只描述请求形状，分段与执行由T08负责 |
| `recoveryPolicy` | 下节的 `RecoveryPolicy`；49项均REQUEST，独立恢复标志false、映射为空 |
| `completenessPolicy` | 嵌套 `CompletenessPolicy(status, paginationStatus, documentedLimit, requirement)`；T04生产记录的前两项只接受UNCONFIRMED，后两项为T01的documentedLimit及gapOrConditionalCriterion非空文本。不存在已启用的分页算法或“短于上限即完整”规则 |
| `calendarEvidenceStatus` | 可空枚举DOCUMENTED／UNCONFIRMED／CONFLICT；19项严格取T02.status，其余null；与运行CalendarDecision分离 |
| `evidenceRefs` | 非空、去重的相对仓库引用列表；每项含 `docs/research/RANGE-T01-source-evidence.json#apiName=<name>`，19项另含T02同格式引用。运行时不读取docs或访问URL |

这些生产策略未提供任何CONFIRMED取全实现，T04不添加执行能力判定捷径。后续T07接入证据和具体算法时再扩展完整性策略；不得仅把状态文字改成“已确认”就取数。旧单次执行在T04只做SPI签名迁移，不能将其历史成功计为新区间取全通过。

公共文案固定如下：交易日期“按区间内已确认的适用交易日期下载；来源请求与完整性仍需核实。”；weekly／monthly再加“保留周线／月线记录，不扩大输入区间。”。公告日期“按公告日期范围下载，公告日期不等于报告期；来源请求与完整性仍需核实。”；月份“下载区间覆盖的完整自然月；来源请求与完整性仍需核实。”；原条件“按原有条件下载，不增加日期筛选，也不承诺仅返回最新数据。”。trade_cal／new_share／namechange分别为“按日历日期范围下载”“按IPO申购日期范围下载”“按公告日期范围下载”，均追加“；实际筛选与完整性仍需来源核实。”。

### 3. 策略资源、加载及投影

新增 `tensor-plugin-tushare/src/main/resources/download/tushare-pro-policies.yaml`，根为仅有 `policies` 的对象，值为49元素数组。每项仅有 `apiName` 和上节全部策略字段；字段名用camelCase。空值显式null，不使用YAML merge、默认策略、名称推断或运行时生成；按apiName排序便于核对。

在同目录新增 `tushare-pro-policies.schema.json`，使用现有Jackson YAML及JSON Schema依赖。`DownloadPolicyLoader` 按明确classpath资源加载，严格重复键检测、未知字段拒绝、尾随第二文档拒绝；固定schema及record构造约束共同检查嵌套字段和模式组合，禁止外部schema加载。借鉴 `DatasetDefinitionLoader` 的安全诊断，不为这一个资源抽出通用加载框架。

loader入口固定为 `Map<ApiName, DownloadPolicy> load(Resource resource)`，schema由loader从同模块classpath加载；返回不可变Map。资源顶层及apiName包装只在loader的私有Raw record中解析，不能让共享SPI依赖Jackson或Spring。`requestEvidenceStatus=UNCONFIRMED`必须配sourceRequestMode=null／sourceDateParameter=null／batchPlanning=UNCONFIRMED；DOCUMENTED_CANDIDATE和CONFLICT必须带非null候选及对应形状。具体候选是否与逐项研究一致由独立49项回归核对，结构校验不冒充来源核实。

构造Tushare插件前将策略与已加载的49份definitions联合校验：集合完全相等，apiName不重复，数量和模式精确19／15／1／3／11；日历集合与类别精确对应T03目标表；非交易日期不带calendarProfile/status。少项、多项、拼错模式、日期参数映射矛盾、错误31上限均以DATASET_MISCONFIGURED失败并阻止插件注册，不能静默丢掉一个接口。loader采用与DatasetDefinitionLoader相同的私有TensorException子类模式，固定错误码和安全消息；现有loader的私有异常不跨类复用。错误只输出受控资源名、合法apiName及规则名。

49行的apiName／mode／calendarProfile由T03 `x-tensor-range-targets` 对照；请求候选、状态、完整性文字由T01逐项映射；calendarEvidenceStatus由T02映射。生产资源不复制来源参数、数据列、业务键、必要市场列表或网页正文。8个请求未确认项保留sourceRequestMode=null；9个真实排除项仍登记策略，排除清单继续由研究和验收文档管理，不新增运行功能开关。

投影逻辑放入 `DownloadParameterProjection.project(sourceParameters, policy)`：

1. 19个TRADE_DATE_RANGE移除原trade_date、15个ANN_DATE_RANGE移除ann_date、MONTH_RANGE移除month；其余原字段按原顺序保留在前，追加start_date/end_date。移除字段的名称和类型必须与模式相符。
2. 两个新字段均为必填DATE_RANGE_MEMBER，互相relatedParameter，无默认值、allowedValues为空；标签“开始日期／结束日期”，description用策略文案，pattern为null（沿用既有范围字段由ParameterType校验八位日期的方式）。严格公历校验继续复用现有校验器，31天行为由T05增加，本项只登记类型与上限。
3. NATIVE_RANGE保留其现有起止字段和附加条件语义，附加字段排在日期前；缺合法日期对或存在多余日期字段即元数据错误。ORIGINAL_PARAMS完整保留原数组，不删除或增补字段，fixture的scenario也保留。
4. 每个非日期ParameterDescriptor在投影前后完全相等，sourceParameters完全等于DatasetDefinition.parameters；38项形成T03四种范围形状、11项形成五种原条件形状。不得为express／top10_holders／top10_floatholders偷偷补ts_code，不为fina_mainbz透传报告期冒充公告范围。

`TusharePluginConfiguration` 添加名为 `tushareDownloadPolicies` 的策略bean，创建 `TushareProPlugin(properties, client, definitions, Map<ApiName, DownloadPolicy> policies)`；所有测试构造点同步。定义与策略冻结后生成描述符。fixture仅一个 `fixture_daily`，用显式ORIGINAL_PARAMS策略、NONE来源方式、DOCUMENTED_CANDIDATE请求登记、ORIGINAL_PARAMS组批形状、REQUEST恢复、UNCONFIRMED完整性及无日历；文案为“受控fixture场景，仅用于测试。”，两项完整性文本均为“既有fixture单次响应场景，不代表真实来源取全。”，策略与恢复证据均引用 `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java`。其原scenario参数保持不变，不套用Tushare49项数量校验。

### 4. 内存选择器、恢复策略和取数结果

新增以下 `plugin.api.download` 类型，不带Spring、Jackson注解、SQL或任务持久化职责：

| 类型与规范构造字段 | 不变量 |
|---|---|
| `RecoverySelector(TargetType targetType, String targetValue, TimeType timeType, String timeValue)` | TargetType=STOCK／REQUEST，TimeType=DATE／MONTH／RANGE／NONE；STOCK为已规范化单值 `[A-Z0-9]+\.[A-Z0-9]+`，最多64字符；REQUEST严格空串。DATE=严格YYYY-MM-DD，MONTH=YYYY-MM，RANGE=严格start/end且start<end，NONE严格空串。拒绝null、逆序、相等RANGE和无效公历；相等来源起止由T05生成DATE。record的相等性包含全部四项，两股同日不可折叠 |
| `RecoveryPolicy(Mode mode, String targetField, String timeField, RecoverySelector.TimeType unitTimeType, boolean independentRecoveryVerified, List<String> evidenceRefs)` | Mode=REQUEST／STOCK_TIME；evidenceRefs两种模式均为非空去重不可变列表。REQUEST映射三项为null、independentRecoveryVerified=false；STOCK_TIME要求合法非空字段标识、unitTimeType为DATE／MONTH／RANGE、verified=true。targetField/timeField表示行归属字段，RANGE边界取冻结批次，不从单行猜范围。字段存在本身不是启用依据；Tushare全部REQUEST并引用对应T01行。该类型表达恢复能力，不负责从响应枚举股票 |
| `FetchBatch(Map<String,Object> sourceParams, RecoveryPolicy recoveryPolicy)` | 精确保存原请求批次参数的不可变副本，不混入未转换的公开条件、最后一页游标或候选明细；支持原条件空map。key为既有参数标识，value仅为非null String，与现有ValidatedParameters一致；无可变集合值。合法的RANGE来源仍含start_date/end_date。不能用尚未确认方式创建可执行批次，合法性由T05／T08守门 |
| `FetchResult(DownloadEnvelope envelope, List<UnitFailure> failures)`；`UnitFailure(RecoverySelector selector, ErrorCode errorCode, String errorMessage)`为嵌套record | envelope必填，保留既有完整包络约束；failures不可变、无null、selector不重复。errorCode仅允许当前SourceException接受的业务来源代码（不含SOURCE_REQUEST_UNCONFIRMED、CALENDAR_UNCONFIRMED）；errorMessage为非空安全摘要、最多512字符。FAILURE包络必须failures为空；SUCCESS可带明确单元失败，表示包络结构成功，不能由此认定每个单元成功 |

T04不推导归属或完整性，不按返回列猜STOCK_TIME，也不从“未返回股票”生成failure。STOCK_TIME正向构造只在受控测试中证明类型可表达，不升级49项真实策略。源失败的完整批次继续抛 `SourceException`；`FetchResult.failures` 只供以后真实有依据的逐单元状态，Tushare和现有fixture迁移均返回空列表。

`FetchResult` 不替代现有面向HTTP的 `DownloadResult`；不得新增S／F等运行计数或在此层构造失败任务。包络params仍是本次完整来源请求参数，T07分页合并时不能变成最后一页参数。

### 5. SPI与日历缺省行为

`DataSourcePlugin` 保留descriptor和readiness，删除旧两参download，增加：

```java
FetchResult download(ApiName apiName, Map<String, Object> sourceParams,
                     DownloadContext context);
default CalendarDecision confirmCalendar(ApiName apiName, CalendarScope scope,
                                         DownloadContext context);
```

`DownloadContext` 是只有 `void checkServerState()` 的函数式接口；用于服务端已确认故障检查，异常原样传播，不接收Servlet请求、客户端中断、超时取消、停止标志或Thread.interrupted信号。T04原调用者传无操作lambda；Tushare／fixture在来源调用前及取得结果后各检查一次；后续T07在分页边界复用。它不新增整轮超时或重试。

`CalendarScope(Map<String,Object> publicParams, Set<LocalDate> dates)` 为已冻结公共条件及实际待确认日期集合；两者防御复制，日期非空、不可含null，可表达不连续3／7日，不自动扩成最小最大范围。复用FetchBatch的字符串条件约束，不持有可变对象。

`CalendarDecision(CalendarScope scope, Map<String,Map<LocalDate,Boolean>> calendars)` 表示全部必要日历已确认；外层键为提供器确定的非空市场／业务来源身份，集合非空；每份内层map的键必须精确等于scope.dates且开闭值非null。构造器深复制；`openDates()`返回任一必要日历为true的日期不可变集合。全部false是已确认休市；缺map、空map、缺日、多日、null都不能返回一个空openDates冒充休市。必要身份集合、权威来源和新鲜度由T06提供器确认，不能由构造器凭map证明；T04受控样例只验证表达及覆盖不变量。

缺省confirmCalendar校验参数非null、调用context检查后，抛新增固定码 `CalendarUnconfirmedException`（继承TensorException，CALENDAR_UNCONFIRMED，安全文案“Applicable calendars are unconfirmed”）。Tushare和fixture本项均使用该缺省；不访问辅助或业务来源，不返回伪确认。非交易日期的后续编排不调用此方法；原生trade_cal自身下载仍是业务接口。

### 6. 同版调用链迁移与错误码

仅保留一个新三参SPI download；Tushare仍委托现有client.execute，fixture仍执行原scenario逻辑，最后将原包络包装为 `FetchResult(envelope, List.of())`。readiness、凭证检查、来源异常及原业务参数保持既有顺序；不得在本项添加推测分页、日历调用或完整性通过标志。

现有生产消费者按以下边界迁移：

| 消费者 | T04修改 |
|---|---|
| `ParameterValidator` | 新增 `validate(List<ParameterDescriptor>, Map<String,Object>)` 复用原逻辑；现有ApiDescriptor重载明确委托其parameters（下载投影）。本项不加31天运行校验 |
| `DownloadService` | 原执行校验改用api.sourceParameters；调用三参download，先检查FetchResult／envelope非null以及failures为空，再走原包络身份／params核对、适配和持久化。遇非空failures以SOURCE_PAYLOAD_INVALID安全拒绝、adapter／persist调用为零，不能静默丢掉失败并报成功。此保护由T09／T12接管后才能移除 |
| `DownloadParameterResolver`／`ParameterShape`／`ParameterJsonReader` | 当前HTTP入口明确使用sourceParameters。给ParameterShape增加list入口并从resolver传入来源字段；JSON声明字段及validator使用同一来源列表，不构造替身ApiDescriptor。保留原codec、未知字段拒绝和错误优先级 |
| `ApiDescriptorResponse` | T04原DTO只序列化sourceParameters，保持现有wire字段；不序列化内部策略或sourceParameters字段本身。T14改为parameters投影＋公共策略五字段时同时接入区间执行 |
| `OperationLogger` | 参数白名单／摘要改从sourceParameters取得；保留安全白名单和日志顺序，不记录内部证据、来源响应或凭证 |

T05可以实现投影参数绑定器和重建能力；实际HTTP入口何时切换须与T12／T14执行集成一致，不能出现表单接受范围而旧DownloadService要求单日的中间发布状态。本项不增加兼容开关或运行双SPI。

`ErrorCode` 增加T03已经固定的10码和retryable值，保留原16码。SourceException在现有7个SOURCE码外接受SOURCE_REQUEST_UNCONFIRMED、SOURCE_TRUNCATED、SOURCE_COMPLETENESS_UNCONFIRMED；日历用独立固定异常，其他新码暂不新增执行异常类。`GlobalExceptionHandler`及其测试的穷举switch同时按T03错误目录补齐HTTP／安全消息；不实现分阶段downloadResult响应，该职责仍在T14。单元failure只允许SourceException集合扣除SOURCE_REQUEST_UNCONFIRMED；禁止日历、参数、存储或提交未知作为插件明确单元失败。

所有测试插件、包装插件、Mockito桩／verify及描述符构造点同版迁移。保留测试原意，不能通过删除原测试、把所有策略改成ORIGINAL_PARAMS或只放宽反射断言来获得通过。

### 7. 实施顺序

1. 在PluginApiSurfaceTest及新增DownloadContractsTest中先固定三参SPI、默认日历拒绝、选择器和集合不变量、26码，再实现共享类型；迁移测试替身以恢复编译。
2. 用真实49份definitions和离线策略样例先写加载／投影正反用例，再实现资源、schema、loader和投影；逐项对照T01／T02／T03。
3. 同版迁移Tushare／fixture及所有旧调用者，验证完整原包络包装、context检查、非空明确失败拒绝和旧参数日志／绑定／元数据行为。
4. 跑下节后端验证及独立数据集／策略核对，记录准确结果。更新增量追踪中的T04合同证据，不把29个功能AC改成通过；完成看板后按Order准备T05设计及交接。

## Files

路径均相对仓库根；下列尚未存在的Java／资源／报告是T04待实施产物，本设计不表示已创建。

- `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/`：修改 `DataSourcePlugin.java`、`descriptor/ApiDescriptor.java`、`error/ErrorCode.java`、`error/SourceException.java`；新增 `error/CalendarUnconfirmedException.java` 及 `download/DownloadPolicy.java`、`DownloadParameterProjection.java`、`RecoverySelector.java`、`RecoveryPolicy.java`、`FetchBatch.java`、`FetchResult.java`、`DownloadContext.java`、`CalendarScope.java`、`CalendarDecision.java`。
- `data-plane/tensor-plugin-tushare/src/main/`：新增 `resources/download/tushare-pro-policies.yaml`、`resources/download/tushare-pro-policies.schema.json`、`java/com/akkc/tensor/plugin/tushare/metadata/DownloadPolicyLoader.java`；修改 `TushareProPlugin.java`、`TusharePluginConfiguration.java`（位于同Java包根）。不改datasets目录及client完整取数行为。
- `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`：显式fixture策略与新SPI。
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java`、`download/DownloadService.java`：来源列表校验与结果包装消费。
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/`：修改 `web/download/DownloadParameterResolver.java`、`ParameterShape.java`、`ParameterJsonReader.java`、`web/dto/ApiDescriptorResponse.java`、`web/GlobalExceptionHandler.java`、`observability/OperationLogger.java`，用途见§6。
- 新增测试：`tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/download/DownloadContractsTest.java`、`DownloadParameterProjectionTest.java`；`tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/DownloadPolicyLoaderTest.java`（均在data-plane下）。扩展同模块 `PluginApiSurfaceTest`、`PluginDescriptorTest`、`TushareProPluginTest`，fixture的 `FixturePluginTest`，core的 `ParameterValidatorTest`／`DownloadServiceTest`，app的 `DataSourceControllerTest`／`DownloadRequestBindingTest`／`DownloadParameterResolverTest`／`OperationLoggerTest`／`GlobalExceptionHandlerTest`。
- 机械迁移其他实际构造／调用点，包括core的 `MetadataQueryServiceTest`／`RegistryTest`、app的 `DownloadControllerIT`／`FixtureFlowIT`／`DatasetControllerIT`。以全仓编译及搜索覆盖，不局限于本清单。
- 创建 `docs/verification/RANGE-T04-plugin-contracts.md`，更新 `docs/traceability/tensor-range-requirements.md` 及本项目看板／后继设计交接。沿用当前工作区，不覆盖已有暂存文档；新建文件加入Git暂存，不提交或发布。

## Tests

从仓库根执行；本节是T04将执行的命令，不是本设计阶段的通过记录。

```sh
mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-plugin-tushare,tensor-plugin-fixture -am test
mvn -f data-plane/pom.xml test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

前两条用于局部开发后全量单测；后两条Maven verify包括现有打包合同和acceptance包回归，不等于本项目真实来源验收。数据库／Docker适用IT若跳过须在报告列出，不能写成数据库通过；本任务不要求新增数据库功能证明。命令均需实际退出0；若编译、单测或打包合同未通过，不标T04完成。仅针对明确失败补跑相关检查。

必须覆盖以下可观察断言：

| 检查组 | 正向／负向与预期 |
|---|---|
| 共享类型 | 两股同日selector不等；REQUEST四种时间可表达；非法公历、非规范股票、MONTH缺月、NONE带值、相等或逆序RANGE拒绝；嵌套集合外部变更不影响值；STOCK_TIME缺映射／证据／verified拒绝 |
| 49策略及证据 | 与实际49份YAML及T03集合精确相等；19／15／1／3／11、19日历及12／1／3／2／1、40／8／1请求状态、1／16／2日历状态、49 REQUEST及49完整性未确认。8项sourceRequestMode为null；forecast DATE＋CONFLICT；monthly DATE＋日历CONFLICT |
| 加载拒绝 | 缺策略、多策略、重复apiName、YAML重复键、未知字段、第二文档、错模式、错profile、32上限、空证据、未确认方式伪填DATE、日期映射错误、生产STOCK_TIME或伪已确认取全都不能注册 |
| 投影与原条件 | 参数按T03九形状精确比较；daily原trade_date与投影start/end分离；income保留ts_code，margin保留exchange_id，trade_cal保留exchange；11原条件参数数组不变；queryMode／filters／列／业务键不变；生产资源不加载进datasets |
| 运行边界 | 旧daily单日、月份、原生范围、fixture scenario的原绑定与来源params保持；三参调用一次且无自动重发，包络原params不变；旧metadata仍展示可执行的原字段，内部策略／sourceParameters字段不出现在JSON；原参数日志脱敏回归 |
| FetchResult与context | 空failures保留原成功／空／失败行为；带明确failure时旧服务SOURCE_PAYLOAD_INVALID且adapter和persist零调用；null及重复failure拒绝；context调用前失败时零来源调用，返回后检查失败不持久化；不从客户端断开构造取消信号 |
| 日历 | 默认confirmCalendar抛CALENDAR_UNCONFIRMED且零来源调用；受控两市场取并集、全false合法空、不连续日期精确覆盖；缺日、多日、null、空必要集合拒绝。该测试不证明来源适用或新鲜度 |
| 错误与回归 | 26码的HTTP／retryable精确对照T03，原16码及安全消息行为保留；反射确认无旧两参download或五参ApiDescriptor构造器，calendar默认实现仅明确拒绝；全仓测试替身编译 |

实现前保存49份Dataset YAML的SHA-256、parameters／queryMode／filters／columns／businessKey基线到临时目录；实现后逐文件比较一致，并将集合及摘要比较结果写入报告。用T01/T02 JSON与T03目标表独立核对生产策略，不把一份被测策略拷贝成测试期望。报告保留执行命令、退出码、跳过项和四项AC合同证据；受控测试不计为真实来源支持。

## Acceptance

1. 共享SPI只有新三参下载和明确拒绝的缺省日历；所有内置及测试插件同版迁移，全仓编译／回归命令通过，无旧二进制兼容层。
2. 49策略、候选／证据状态和日历分类精确匹配直接输入；不支持的来源仍未确认，Tushare独立恢复与分页／取全均未误启用。AC-PRD-RANGE-01／14／18／29仅本项合同层证据通过。
3. 下载parameters与sourceParameters分离，38／11投影正确，原必填条件和49份Dataset定义保持一致；HTTP旧调用可用且内部策略未泄露。
4. STOCK／REQUEST及DATE／MONTH／RANGE／NONE、取数明确单元失败和精确日历集合有正反验证；旧服务不能丢掉failure后持久化。
5. 验证报告和增量追踪准确区分类型合同、现有调用回归、未实施区间行为、未验证来源；新文件已暂存。记录T04完成后，按工作流完成并链接T05设计，再写后继交接和准备READY，不自动实施T05。

## Risks

- T01／T02研究缺口仍影响后续真实功能可用性；REQUEST不豁免整体取全。T04仅登记未确认状态，不能以49项策略或旧单次调用通过关闭缺口。
- sourceParameters是T04过渡运行入口使用的明确来源合同，parameters是目标下载合同；T05／T12／T14切换时必须同时核对绑定、日志、元数据与执行消费者，避免中间版本暴露无法执行的表单。本项不引入双版本SPI。
- CalendarDecision能检查数据结构及日期覆盖，不能证明必要市场集合完整、来源权威或更新及时；这些门槛由T06和T20依据证据验证。hsgt_top10静态DOCUMENTED也不能在T04返回运行确认。
- ISSUE-008九项继续“不依赖，未解决”；fina_mainbz公告条件、三项缺股票条件、forecast冲突及monthly／margin_detail日历冲突不得静默修补。无新增产品条件或本项待用户选择的实施决定。
