# RANGE-T05 区间参数绑定与恢复请求转换设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T05`：让首次下载的投影参数与保存的失败选择器，通过同一转换逻辑得到精确来源参数；冻结原始展示区间，拒绝不合法或冲突的条件，为后续规划和重试提供可直接消费的内存结果。

## Scope

- 增加投影参数绑定入口、两种缺少的 HTTP 参数形状、包含两端的 31 天校验，以及首次参数／任务参数／恢复选择器之间的转换。
- 消费 T04 的 ApiDescriptor、DownloadPolicy、RecoverySelector；保留来源候选、完整性和日历证据的区别，不修改49项来源研究或生产策略。
- T05 提供可独立测试的新绑定与转换入口；当前 DownloadRequestDeserializer、Controller、DownloadService、元数据 DTO 和日志仍使用 T04 的 sourceParameters 运行路径。T14 在区间执行可用时统一切换，不添加配置开关或临时双版本 SPI。
- 不实现日期／月份批次循环、日历调用、分页、取全判定、单元分组、失败表／JSON存储解析、执行槽位、HTTP失败任务接口或前端。新能力不发来源请求、不写数据库；不实施 T06 或 T07。

## Approach

### 1. 输入依据与固定边界

按顺序读取 [PRD §3、§7.1](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD §4.3、§7.1.1、§7.1.3](../design/Tensor_区间下载_TRD_v1.0.md)、现有 `web/download/` 绑定代码、`ParameterValidator`，再读 [T04设计](RANGE-T04-design.md)、[T04验证](../verification/RANGE-T04-plugin-contracts.md)及下述实际类型。

T04 已完成三参SPI、投影／sourceParameters分离、49显式策略及选择器。49项均为REQUEST，8项来源方式为空，forecast的请求证据为CONFLICT；全部取全／分页未确认。DOCUMENTED_CANDIDATE仅允许按登记形状构造候选参数，不能证明来源实际可执行；取全和日历仍由T06／T07及后续执行守门。本任务不得把候选状态改成已确认。

T03固定的HTTP目标有9种形状：范围、股票＋范围、exchange_id＋范围、exchange＋范围，以及空、股票、exchange、hs_type、list_status五种原条件。38项要求start_date/end_date，11项不增加日期；fixture的scenario仍按原条件处理。日期值为YYYYMMDD，选择器日期为YYYY-MM-DD、月份为YYYY-MM，全部不带时区。

### 2. 参数校验与绑定

保留 `ParameterValidator.validate(List<ParameterDescriptor>, Map<String,Object>)` 的通用合同及required-before-invalid顺序。`validate(ApiDescriptor, Map<String,Object>)` 先委托投影列表完成必填、字符串、未知字段、枚举、股票及日期合法性校验，再根据downloadPolicy对区间执行包含两端的31天校验；ORIGINAL_PARAMS不加范围限制。超长范围抛既有ParameterValidationException/PARAM_INVALID，字段为end_date，安全消息 `must be within 31 inclusive days`。不自动截短、不补日期、不先过滤休市、不按展开后整月长度拒绝输入。

严格公历补齐年范围1～9999（拒绝0000），沿用8位DATE和6位MONTH输入；TS_CODE继续strip＋Locale.ROOT大写、单值格式，并限制规范结果最多64字符以匹配RecoverySelector。日期不strip、不引入未来或最早历史日期限制。列表重载不承担31天规则，因此T04原运行入口不会提前切换到新区间执行。

在 `DownloadParameters` 新增：

```java
record TsCodeDateRangeParameters(String tsCode, String startDate, String endDate)
        implements DownloadParameters {}
record ExchangeIdDateRangeParameters(String exchangeId, String startDate, String endDate)
        implements DownloadParameters {}
```

`ParameterCodec.supported()` 为它们增加精确shape和蛇形字段映射，复用既有DATE_RANGE_MEMBER、ts_code、exchange_id字段定义。已有DateRangeParameters、ExchangeDateRangeParameters和五种原条件复用；保留当前旧日期codec直到T14切换完成。不得为了匹配放宽枚举、required、pattern或relatedParameter。

`DownloadParameterResolver` 新增 `resolveDownload(DatasetKey dataset, Map<String,Object> values)`：解析真实ApiDescriptor，先调用validator.validate(api, values)以执行全部投影规则，再按api.parameters匹配codec，用规范化值创建具体DownloadParameters。现有resolve方法继续按sourceParameters绑定，调用点不变；两入口共用私有列表／codec处理逻辑，不构造替身ApiDescriptor，不增加布尔开关。toRawValues支持新records，仍只保留suppliedFields；缺字段不能由codec补成已提供。

新入口对38项旧trade_date／ann_date／month以及混用、未知字段和非字符串拒绝；先报必填缺失，所需字段齐全时再报非法字段。11项新增日期同样拒绝。T05通过直接调用新resolver验证新合同，旧HTTP入口回归仍按sourceParameters通过；T14才修改Deserializer的入口调用及页面元数据。

### 3. 插件内来源参数转换

新增无Spring／Jackson／Core依赖的 `plugin.api.download.SourceParameterMapper`，集中来源日期形状转换，首次和重试共同调用：

```java
public static MappedParameters map(ApiDescriptor api,
        Map<String,Object> commonParams, RecoverySelector selector);
public record MappedParameters(Map<String,Object> values,
        List<ParameterDescriptor> descriptors) {}
```

结果两集合防御复制，values只容纳合法标识及非null String；descriptors用于Core复用ParameterValidator，不复制一套值校验器。共同条件必须已移除展示start_date/end_date；未知字段、旧时间字段、非字符串和任何待写键冲突均拒绝，不能用put覆盖后掩盖问题。

先检查requestEvidenceStatus：UNCONFIRMED／CONFLICT或缺候选时抛SourceException(SOURCE_REQUEST_UNCONFIRMED)，固定安全摘要 `Source request conditions are unconfirmed`。DOCUMENTED_CANDIDATE继续做结构转换；该通过不批准任何业务请求。其余不兼容对象／时间或条件结构抛IllegalArgumentException，固定安全摘要，不包含输入值，供下节根据调用阶段映射。

| 选择器时间 | 允许的sourceRequestMode | 精确转换 |
|---|---|---|
| DATE | DATE | 使用策略sourceDateParameter，值为YYYYMMDD |
| DATE | RANGE | start_date=end_date=该日，绝不发送trade_date／ann_date |
| MONTH | MONTH | month=YYYYMM，保留完整月份 |
| RANGE | RANGE | 原有两个端点转YYYYMMDD，保持整个区间，不拆小 |
| NONE | NONE | 不添加任何时间参数 |

其他组合全部拒绝，包括DATE来源接受RANGE、月份接受DATE或RANGE、原条件接受有时间选择器。MONTH仅用于MONTH_RANGE，NONE仅用于ORIGINAL_PARAMS。REQUEST不增补股票，不忽略已有股票／交易所条件；即使恢复策略后来增强，也不能自动将保存的REQUEST改成STOCK。

STOCK仅接受明确STOCK_TIME且independentRecoveryVerified=true的策略；时间类型须匹配unitTimeType（RANGE单位退化为单日时允许DATE），并且本项可用的请求股票字段必须是sourceParameters中已声明的TS_CODE类型ts_code。targetField是行归属字段，不能把任意targetField当成请求键；缺少明确ts_code来源参数时拒绝，不猜code／con_code等映射，不为49项新增股票输入。受控测试使用已有股票参数的income形状，证明两股独立转换；当前49项生产REQUEST均拒绝STOCK。后续需要扩展股票请求能力时必须有新的明确来源参数合同和证据，不能只改恢复标志。

STOCK的commonParams不得再含ts_code，即使值相同也拒绝重复承载；代码只从selector.targetValue补入一次。REQUEST在原来源声明ts_code时保留commonParams中的原值。模式DATE／MONTH／NONE的校验descriptors采用sourceParameters；模式RANGE采用T04范围投影parameters，因为其日期形状已表达合法来源起止候选。非日期描述符保持原值。

### 4. Core中的首次冻结与重建

新增 `core.download.DownloadParameterConverter`，构造器接收已有ParameterValidator；不成为下载执行器。固定接口如下，结果record嵌套于该类：

```java
ValidatedParameters bindInitial(ApiDescriptor api, Map<String,Object> raw);
Map<String,Object> taskParameters(ApiDescriptor api, ValidatedParameters original,
        RecoverySelector.TargetType targetType);
MappedInput mapInitial(ApiDescriptor api, ValidatedParameters original,
        RecoverySelector selector);
MappedInput mapRetry(ApiDescriptor api, Map<String,Object> taskParams,
        RecoverySelector selector);
record OriginalDateRange(LocalDate startDate, LocalDate endDate) {}
record MappedInput(ValidatedParameters sourceParams, OriginalDateRange originalDateRange) {}
```

bindInitial直接复用validator.validate(api, raw)，返回包含原始两端及规范股票的不可变值。taskParameters只从这份已校验的原始输入建立新不可变map：REQUEST保留确定原对象的所有非日期条件及原始start_date/end_date；STOCK经上节能力检查后移除ts_code，保留其他公共条件和原始日期。原条件不添加日期，空条件返回空map。参数由实际拟保存的对象类型决定，不能仅凭策略优选模式把REQUEST必需的股票条件删掉。T10／T12保存时消费此输出；不同单元如无法共享同一冻结公共map，不能通过本转换器静默丢条件或混合不可重建范围。

mapInitial先核对拟生成的单元仍位于首次输入：DATE在原始两端之内，RANGE整体被原始区间包含，MONTH属于原始区间覆盖的年月，NONE只用于原条件；若首次输入已有ts_code，STOCK的targetValue必须等于该规范值，不能替换成另一股票。不匹配安全拒绝，不自动扩展输入。随后根据selector的实际对象类型调用taskParameters，再复用私有重建步骤；mapRetry从保存map的内存副本开始。两者都先提取仅供展示的原始日期，再移除展示键、组合selector并调用SourceParameterMapper；最后按MappedParameters.descriptors和值调用列表validator，保证完整请求仍满足必填和枚举。返回的originalDateRange仅供上层展示，不决定sourceParams，不改写输入map。

| 保存参数情况 | 固定处理 |
|---|---|
| 区间模式，两端均存在 | 值必须为严格8位合法日期，顺序及31天限制按首次规则单独校验；不因STOCK已移走必填股票而校验整个主表为缺字段 |
| 区间模式，两端均不存在 | 兼容旧记录，originalDateRange=null；仍用完整selector恢复，不能用失败明细推算原始展示范围 |
| 只有一端、null、非字符串、非法／逆序／超过31天 | RETRY_TASK_INVALID，保留原map，无来源调用 |
| ORIGINAL_PARAMS且无两端 | originalDateRange=null，按原条件恢复；上层结合mode识别“不适用” |
| ORIGINAL_PARAMS出现任一展示日期 | RETRY_TASK_INVALID，不能删除无效日期后执行 |

OriginalDateRange的两值非null、顺序合法且包含两端不超过31天。合法月份恢复不拿完整月与展示区间求交；非连续失败日期须由调用者分别传入选择器，本类不合并它们。原始范围与失败范围不同是正常情况，不要求相等，也不以展示范围重写、截断或推算失败选择器。

不接受当前请求参数与选择器相互覆盖：恢复commonParams中的trade_date／ann_date／month、STOCK重复ts_code、未知／非法条件均拒绝。恢复策略不再支持已保存对象／时间时拒绝，不转换记录类型、不换apiName。JSON是否为对象、重复JSON键及原始字符串解析由T10存储边界处理；T05接收Map后仍检查键和值的合同。

错误按入口区分：bindInitial保留PARAM_REQUIRED／PARAM_INVALID；mapInitial的来源证据缺口保留SOURCE_REQUEST_UNCONFIRMED，其余内部范围／对象／条件不兼容也安全映射为SOURCE_REQUEST_UNCONFIRMED，防止发起不合法候选请求。mapRetry的保存内容、组合或校验错误统一抛私有固定TensorException子类，代码RETRY_TASK_INVALID、摘要 `Saved download parameters are incompatible`；来源证据未确认的SourceException原样保留。不得捕获并改写其他意外异常，不包含保存JSON、SQL、Token或任意输入值。

mapInitial／mapRetry不应用SourceParameterMapper之外的替代日期映射，不检查完整性是否已确认、不创建FetchBatch或调用插件。它们返回的是完成结构校验的来源参数，不是执行许可；T06／T07／T08还必须检查各自前提。

### 5. 精确样例

下列map均以键值相等判断，不依赖顺序；字符串日期按此表原样转换。所有样例无实际来源调用。

| 接口／条件／选择器 | 期望来源参数 |
|---|---|
| daily，原20260901～20260910，REQUEST＋DATE 2026-09-03 | `{trade_date: '20260903'}` |
| income，ts_code输入` 000001.sz `，REQUEST＋RANGE 2026-09-03/2026-09-07 | `{ts_code: '000001.SZ', start_date: '20260903', end_date: '20260907'}` |
| margin，exchange_id=SSE，REQUEST＋DATE 2026-09-03 | `{exchange_id: 'SSE', start_date: '20260903', end_date: '20260903'}`；保留T04的RANGE候选，不猜改单日方式 |
| fina_indicator，ts_code=000001.SZ，REQUEST＋DATE 2026-09-03 | `{ts_code: '000001.SZ', ann_date: '20260903'}`，不得传公告区间到报告期参数 |
| trade_cal，exchange=SSE，REQUEST＋DATE 2026-09-03 | `{exchange: 'SSE', start_date: '20260903', end_date: '20260903'}` |
| new_share／namechange，REQUEST＋DATE 2026-09-03 | `{start_date: '20260903', end_date: '20260903'}`；namechange仅离线合同，不恢复真实调用 |
| broker_recommend，原20260131～20260302，REQUEST＋MONTH 2026-02 | `{month: '202602'}`，不裁剪月份；本类不负责枚举1／2／3月 |
| stock_basic，list_status=L，REQUEST＋NONE | `{list_status: 'L'}` |
| index_classify，空公共参数，REQUEST＋NONE | `{}` |
| 受控income的STOCK_TIME策略，公共原日期1～10日，STOCK 000001.SZ＋DATE 2026-09-03 | `{ts_code: '000001.SZ', start_date: '20260903', end_date: '20260903'}`；同日600000.SH仅股票值不同 |

8项缺方式和forecast冲突在尝试映射时返回SOURCE_REQUEST_UNCONFIRMED；合法公开参数仍可独立通过bindInitial，不将来源问题误报参数拼错。weekly／monthly仍使用DATE候选，输入只用于后续匹配记录日期，不在本任务扩大为整周／整月。

## Files

所有Java路径以 `data-plane/` 为根；新增文件仅在本任务启动后实施：

- `tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/SourceParameterMapper.java`：唯一插件内对象／时间转换及嵌套MappedParameters；新增同包测试 `SourceParameterMapperTest.java`。
- `tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadParameterConverter.java`：首次冻结、任务参数生成、展示日期提取及安全重建；新增同包测试 `DownloadParameterConverterTest.java`。
- `tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java` 及现有 `ParameterValidatorTest.java`：投影上限、年范围与股票64字符约束，复用既有字段错误机制。
- `tensor-app/src/main/java/com/akkc/tensor/web/download/DownloadParameters.java`、`ParameterCodec.java`、`DownloadParameterResolver.java`，必要时 `ParameterJsonReader.java`：新records及显式投影绑定入口，共用现有列表逻辑。
- `tensor-app/src/test/java/com/akkc/tensor/web/download/DownloadParameterResolverTest.java`、`web/DownloadRequestBindingTest.java`：49项投影绑定及原HTTP入口回归；核心转换测试使用受控ApiDescriptor，不让Core测试依赖Tushare实现。
- `docs/verification/RANGE-T05-parameter-conversion.md`、`docs/traceability/tensor-range-requirements.md`：实际验证报告及本任务合同／行为证据。记录完成后按看板准备T06设计和交接。
- 不修改Dataset YAML、49项生产策略、数据库迁移、DownloadService执行逻辑或当前HTTP入口选择；新文件加入Git暂存，沿用工作区，不提交或发布。

## Tests

按“先写失败用例、运行确认、最小实现、针对性复测”执行。首个实施动作是在ParameterValidatorTest用共享T04类型构造受控tradeRange描述符、写入31／32天断言（不引入Tushare模块依赖），运行Core测试，确认现有ApiDescriptor重载尚未拒绝32天；随后实施上限并继续下列转换测试。

| 组别 | 必须观察的结果 |
|---|---|
| 日期与条件 | 20260131～20260302为31天可接受，至20260303为32天拒绝；单日、20240229、跨年合法；0000年、非闰日、逆序、单边缺失、非字符串及枚举错误拒绝；股票规范化且单值最长64 |
| 49投影绑定 | 实际Tushare描述符恰为38区间／11原条件，9种形状逐项通过；新增两records精确写回；旧日期单独／混用、未知键拒绝；源证据未确认不妨碍测试公共输入合法性 |
| 请求转换 | 上节全部map逐一断言；DATE的两种来源形状、MONTH完整月、RANGE保留端点、NONE无日期；首次单元越过输入边界拒绝，MONTH按覆盖年月判断；全部不兼容组合及冲突拒绝；三原生接口单日无错误日期键 |
| 原始区间 | 原1～10日、失败3／7日分别映射，只请求各自日期；原map及两端完全不变。缺两端旧记录允许，缺一端／非法日期拒绝；月份不裁剪；原条件日期拒绝 |
| 对象与错误 | 当前49项不接受STOCK；受控已验证income分别绑定两股首次输入，生成同日不同请求；首次selector换股拒绝，恢复各自保存selector不改股；主表map不重复ts_code，REQUEST保留股票；未知股票请求映射／重复股票／时间不兼容拒绝，无状态升级 |
| 不可变与副作用 | 修改输入集合不影响输出，尝试修改输出失败；mapInitial与mapRetry同一单元结果完全相同；测试组件无插件调用／数据库依赖，不进行自动拆分或重发 |
| 当前运行回归 | T04旧daily、month、原生范围和fixture scenario HTTP绑定保持；元数据与日志仍为sourceParameters，未暴露不能执行的范围表单 |

从仓库根实际执行：

```sh
mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-core -am test
mvn -f data-plane/pom.xml test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

命令均须退出0；Mockito沙箱附加限制沿用T04已验证环境，Maven使用获授权的沙箱外执行。明确报告真实执行数、数据库IT未调度／未执行、浏览器及来源未调用，不能以打包通过代替区间端到端验收。报告保留首次失败与修正后的验证事实；只因新变更／失败重跑相关检查。

## Acceptance

1. 38／11投影和9种形状可绑定，31天允许、32天在任何日历／业务来源之前拒绝，原必填／枚举／股票语义保留；当前HTTP原运行路径回归通过。
2. 首次和重试共用SourceParameterMapper，按DATE／MONTH／RANGE／NONE及真实策略得到精确map；3个原生单日均为相等起止，不因返回列或接口名称猜参数。
3. taskParameters保留原始区间及实际对象范围所需条件；STOCK不重复保存代码，REQUEST不丢失原股票／市场；原始展示与执行范围分离且输入不变，旧记录缺两端可恢复，单边／非法／冲突记录拒绝。
4. 未确认／冲突来源不能因转换而启用，当前49策略／Dataset定义不变；无来源、日历、数据库或重发副作用。受控STOCK正例不计为真实独立恢复通过。
5. 规定验证及评审通过，报告记录AC-PRD-RANGE-03／04／05／06／14／19／22／25的本任务参数层证据；最终功能与真实来源验收仍保留。新文件已暂存，完成后按Order准备T06，不自动实施后继。

## Risks

- 来源方式仍是候选；来源合法性、完整取数和日历缺口不能由结构转换消除。REQUEST不能绕过取全，8项缺方式、forecast冲突及ISSUE-008排除均保留。
- T04的targetField描述行归属，不是任意股票请求键。本任务只消费已声明ts_code的独立恢复合同，其他股票请求映射明确拒绝，后续不能靠切换标志误启用。
- T05新绑定入口与当前运行入口须保持命名和职责清楚。T14切换时需同时迁移Deserializer、执行、DTO与日志，不能提前把公共区间交给旧单次下载服务。
- 两端均缺失的旧记录不代表原始区间等于剩余范围；null原始范围由上层结合mode解释，插件已下线的UNCONFIRMED展示状态由T14处理。
- 本设计为T04完成后的后继准备，仅固定T05实施决策与验证标准，不表示T05已开始或测试已执行。
