# RANGE-T09 恢复单元划分、适配与同轮冲突设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T09`：在完整取数之后、任何业务提交之前完成整体结构和可靠归属检查，再按冻结恢复边界逐单元适配、去重和校验同轮冲突。交付可由T11／T12直接消费的内存单元与提交确认后更新的键摘要索引，覆盖AC-PRD-RANGE-07／08／09／11／14／29的本项机制。

本设计在T08已记录COMPLETED后按Order选择T09时准备。观察T09为NOT_STARTED、依赖T04／T07、Design与Handoff均为None；设计就绪不表示T09实现、测试或生产独立恢复已经启动。

## Scope

- 整体包络、字段顺序、行宽、对象／时间和业务键唯一归属预检查；REQUEST／STOCK_TIME划分；明确来源单元失败消费；安全、精确且可重建的整批失败范围。
- GenericDatasetAdapter的新增逐行适配入口、全业务列规范内容编码、单元内精确去重／冲突、同轮已提交键索引；保留原业务键及原 `adapt` 入口行为。
- 受控验证A／B／C隔离、缺行、整体失败、合法空、冻结重试边界、规范内容及确认提交前后的索引变化。
- 不实施业务事务、失败表／仓储、任务保存／删除、S／F／N与I／U编排、HTTP、DownloadService接入、前端、来源切换、T10或T11代码。T12只作为后续消费者列出约束，本项不改其运行入口。
- 不添加证券全集、股票选择入口、股票×日期笛卡尔积、推测分页、独立恢复生产配置、跨次成功指纹或执行上限。不改49份Dataset、生产策略／schema、Tushare完整来源注册表或真实证据；49项生产仍为REQUEST且完整来源未启用，ISSUE-008九项仍不依赖、未解决、不执行真实调用。

## Approach

### 1. 已核对输入和信任边界

依照看板规定来源，读取[TRD §4.2、§5.2、§6](../design/Tensor_区间下载_TRD_v1.0.md)、当前GenericDatasetAdapter及BusinessKeyExtractor，再完整读取[T04设计](RANGE-T04-design.md)／[验证](../verification/RANGE-T04-plugin-contracts.md)、[T07设计](RANGE-T07-design.md)／[验证](../verification/RANGE-T07-complete-batch-fetch.md)。另以[BRD §3～§6](../design/Tensor_区间下载_BRD_v1.0.md)、[PRD §5～§6及§9.1](../design/Tensor_区间下载_PRD_v1.0.md)、[T05设计](RANGE-T05-design.md)及实际T05／T08代码核对冻结条件和下一层输入。

已存在的事实如下：

| 输入 | T09必须保留的含义 |
|---|---|
| `DataSourcePlugin.fetchBatch`／`FetchResult` | 完整批次合同；T07取完全部页、检查来源结束与整体范围后才返回。不是旧 `download` 的别名。失败没有可供T09提交的部分包络 |
| `DownloadEnvelope` | 构造器已拒绝重复字段、错误rowCount、成功错宽及失败带部分行；T09仍核对与当前definition／batch相等的身份、参数和有序字段。测试这些构造不变量时直接验证构造拒绝，不用反射破坏record制造正常路径不可能的输入 |
| `RecoveryPolicy.REQUEST` | targetField／timeField／unitTimeType全为null。Core不得猜ts_code、ann_date、trade_date、报告期或股票全集；来源范围和合法空依赖严格fetchBatch的来源合同 |
| `RecoveryPolicy.STOCK_TIME` | 只有明确映射、独立恢复verified及证据才可构造；其语义须同时覆盖单元完整性、归属和独立重取。列存在或调用者提供成员列表本身都不是来源许可 |
| `FetchResult.failures` | 只表达来源确实提供的明确单元失败，Tushare当前恒为空。没有“每股成功0行”声明，不从缺行补出此状态 |
| T05转换器 | REQUEST保留原ts_code；STOCK从selector补入且公共map不能带ts_code；原始日期只展示，执行以selector为准。不同单元不能混用无法共用的冻结公共map |
| T08 `PlannedBatch(scope, fetchBatch)` | scope是精确可重建的REQUEST请求范围，不是已知股票单元全集；不根据scope推导股票×日期 |
| GenericDatasetAdapter | 目前整体转换后按Map.equals去重，同键异值抛ADAPTER_TYPE_INVALID。DECIMAL经ValueConverter按声明scale用UNNECESSARY规范化；FingerprintKeyCodec已有固定编码，不可顺便改写 |
| 现有持久化 | BusinessKeyExtractor及BusinessKey定义保持；UpsertSqlFactory在业务列之外添加source_plugin／source_api／ingested_at。PersistenceService不能用于证明T09事务已实现 |

T09接受的完整结果只能来自未来编排对严格 `fetchBatch` 的调用或等义受控测试；Java record自身不证明来源真实性。REQUEST范围检查由T07经核实来源合同负责，T09检查包络与冻结请求一致；STOCK_TIME还由T09扫描所有行确认恢复归属。两者均不以HTTP 200、短响应、空响应或恢复标志替代整体取全。

### 2. 最小代码接口与所有权

新增Core的三个final类，辅助类型嵌套，保持共享SPI、HTTP及运行配置不变。公开入口仅消费T04已有RecoverySelector／FetchBatch，不依赖T08的PlannedBatch类型；T12集成时可从PlannedBatch取scope和fetchBatch传入。T05的DownloadParameterConverter及其设计是复用的现有公共转换辅助，应随本设计作为实施阅读／交接输入；看板直接依赖仍为T04／T07。

```java
// com.akkc.tensor.core.download
RecoveryUnitProcessor(GenericDatasetAdapter adapter,
        DownloadParameterConverter converter, BusinessKeyExtractor keys,
        BusinessContentCodec contents);

BatchSession openInitial(ApiDescriptor api, ValidatedParameters original,
        RecoverySelector scope, FetchBatch batch, DownloadContext context);
BatchSession openRetry(ApiDescriptor api, Map<String,Object> frozenTaskParams,
        RecoverySelector savedSelector, FetchBatch batch, DownloadContext context);

// BatchSession: one source request, one terminal preflight operation
PreparedBatch accept(FetchResult complete);
PreparedBatch sourceFailed(SourceException failure);

// PreparedBatch exposes immutable UnitInput / Failure lists, never a new fallback operation
Validation validate(UnitInput unit, CommittedKeyIndex committed, Instant ingestedAt);

// com.akkc.tensor.core.adapter
byte[] BusinessContentCodec.encode(DatasetDefinition definition, Map<String,Object> row);
String BusinessContentCodec.sha256(byte[] canonical);

// com.akkc.tensor.core.download
CommittedKeyIndex(DatasetKey datasetKey);
void CommittedKeyIndex.confirmCommitted(ReadyUnit ready);
```

`BatchSession`、`PreparedBatch`、`UnitInput`、`Validation`、`ReadyUnit`、`Failure`置于RecoveryUnitProcessor内。Validation为sealed接口，仅ReadyUnit和RejectedUnit两种；RejectedUnit持有一个Failure。Failure字段为 `RecoverySelector selector, ErrorCode errorCode, String errorMessage`，不能复用只允许来源码的 `FetchResult.UnitFailure` 表达ADAPTER或DATA_CONFLICT。

- Session冻结api、definition、scope、FetchBatch、任务公共map及context；只能从OPEN调用一次accept或sourceFailed，随即终结；再次调用固定IllegalStateException，不返回失败范围。整体预检查完成前不能获得UnitInput。PreparedBatch无REQUEST回退API，因此单元暴露后不能在局部错误处理时重新整批回退。
- PreparedBatch包含不可变 `List<UnitInput> units` 和 `List<Failure> failures`；同一selector只出现一次，units与failures不相交。全局失败只有failures、无units。UnitInput构造器不公开，保存本单元selector、原批次参数的行切片包络、原始行数及所属Session的context；不能由外部绕过整体检查构造。
- `validate`为Processor实例方法，只接受本实例产生的UnitInput；结果为ReadyUnit或RejectedUnit。ReadyUnit含selector、`AdaptedBatch batch`（仅需实际写入的行）、`long sourceRowCount`、待确认键摘要。ReadyUnit由validate创建、对外不可自行构造；绑定所用索引实例，索引拒绝其他实例生成的票据和同一票据重复确认。确认标记保存在ReadyUnit自身，不在索引中保留ReadyUnit引用或已消费票据集合，避免间接保留完整行。
- `CommittedKeyIndex`每次首次下载／手动执行创建一个，绑定一个DatasetKey；串行使用，不能共享到另一执行、线程或数据集。唯一内容变更入口是confirmCommitted；validate只读索引。测试可用包可见size／lookup观察，不提供对外可写Map。
- `BusinessContentCodec`与索引不访问业务表、事务管理器、任务表、日志或来源；ReadyUnit不携带I／U，也不把sourceRowCount立即计成成功。

当前49项均由注册的GenericDatasetAdapter适配。本项Processor明确依赖该实际适配器，不根据definition另建一个Generic覆盖注册扩展，也不扩展DatasetAdapter SPI。T12未来从AdapterRegistry取得非Generic适配器时，应在业务调用前以DATASET_MISCONFIGURED拒绝当前恢复路径；不能静默调用旧adapt。此限制不修改当前DownloadService或自定义适配器原路径。

### 3. 在取数前冻结恢复范围与公共条件

`openInitial`先检查context；核对apiName与adapter.definition身份、batch恢复策略与api策略一致，scope为REQUEST；使用 `converter.taskParameters(api, original, REQUEST)`取得公共map，再调用 `mapInitial(api, original, scope)`，要求生成的sourceParams精确等于FetchBatch.sourceParams。保留原始日期和非日期条件，不以最后一页或单元参数替代。失败为安全SOURCE_REQUEST_UNCONFIRMED；适配器／描述符配置不一致为DATASET_MISCONFIGURED，均在取数前抛出，不产失败单元。

首次拆分的必要条件同时为：STOCK_TIME策略可用、T05支持明确ts_code来源参数、能用同一公共map独立重建每个单元、后续整体检查通过。提前检查 `converter.taskParameters(api, original, STOCK)`是否可建立，且与REQUEST公共map完全相等。不相等或缺合法单股映射时冻结本次为REQUEST；不删除REQUEST必需的股票条件。例如income原输入含ts_code时两map不等，采用完整REQUEST。此判断在任何批次单元提交前确定，不能先成功STOCK再发现后批REQUEST无法共用任务参数。未知真实能力不升级，完整REQUEST仍须经过fetchBatch。

`openRetry`执行同样的context、适配器／描述符身份和batch策略检查，然后使用保存的selector原样及保存公共map，调用mapRetry核对精确来源参数；不再运行首次输入包含性限制，不用原始展示区间扩大重试。保存REQUEST无条件保持一个REQUEST，即使当前策略增强；保存STOCK仍保持原STOCK＋DATE／MONTH／RANGE，RANGE不按行拆日期。STOCK已不合法则沿T05的RETRY_TASK_INVALID或来源未确认抛出；没有静默扩大到REQUEST的后备路径。相等原生范围仍是DATE选择器、来源两端相等。

Session保留一个精确失败边界：首次为openInitial传入并核对的scope，重试为savedSelector。REQUEST回退永远等于该边界及同一公共map，不是首次展示大区间，也不是跨多个来源批次的范围。转换检查必须在来源调用前完成；不可重建的对象子集不能先取数再尝试补任务参数。

### 4. 成员依据：已知全集和响应可观察成员分开

生产openInitial当前没有股票全集输入，默认“成员未知”。T08不提供该事实，49项当前也没有可信成员提供器。为验证TRD§5.2.4的已知分支，增加仅包可见的openInitial测试重载（同样接收RecoverySelector scope、FetchBatch batch，前述参数次序不变），最后一参为嵌套 `KnownMembers(DatasetKey datasetKey, FetchBatch batch, List<RecoverySelector> selectors, List<String> evidenceRefs)`；主源码只定义值合同，不增加生产成员提供器、自动发现、配置开关或新的插件SPI。正式公共入口不接收用户／HTTP的成员列表。

KnownMembers列表非空、不可变、去重；绑定精确数据集、原批次及恢复策略，所有selector必须是合法STOCK，严格位于当前对象／时间范围且逐项能用同一公共map通过T05重建；evidenceRefs非空、无空白及重复。T05重建通过不是单元边界校验：每个selector还必须精确等于§5按该策略可生成的规范单元。unitTimeType=RANGE时，selector的timeType／timeValue必须精确等于冻结scope的时间（scope退化单日才允许同一DATE），禁止任何子RANGE或从多日RANGE中挑DATE；DATE单位仅允许scope内单个DATE，MONTH单位仅允许与scope相同的MONTH。保存STOCK仅原selector自身合法。按同一股票检查时间范围不相交，拒绝重复、DATE落入同股RANGE、同股相交RANGE及其他混合粒度重叠，不以集合去重替代；不同股票同一时间仍是不同合法单元。仅已允许独立恢复时才能消费。构造和绑定检查失败在取数前拒绝SOURCE_REQUEST_UNCONFIRMED，不能把错误证据当成未知后继续。测试引用测试源文件和场景名，明确是受控证据。

这里的证据来源责任是提供该合同的服务端来源实现：必须已核实该请求实际覆盖的**精确完整独立单元集合**，不是股票清单乘日期，也不是某页返回对象。引用和结构检查只防止错配，不能认证来源事实；未来生产接入须另有该接口／条件的真实证据及明确实现审查，本项不交付此接入。KnownMembers从不绕过fetchBatch、STOCK_TIME或T05，也不把未知取全改为成功。

- 没有KnownMembers：完整成功响应的所有可归属行及明确failure给出“本响应可观察的单元”，可在已核实单元完整性下隔离；不宣称这是股票全集，不为没出现的股票／日期造0行成功或失败。整体来源失败则只能用精确REQUEST边界，不能使用失败前的部分行枚举股票。
- 有KnownMembers：来源调用整体失败、且尚未accept时可对列表中每个实际受影响单元产生同一来源错误，摘要说明“请求失败，未获取完整数据”，不声称各股票分别发生来源业务错误。请求尚未完成、未开始成员不预登记。
- 完整成功时KnownMembers只是额外覆盖核对：行或failure归属不在列表内是SOURCE_PAYLOAD_INVALID；非空响应中某已知成员既无行也无明确failure，是SOURCE_COMPLETENESS_UNCONFIRMED，整scope失败，不能把那个成员直接判为失败或合法空。全空且failures为空的严格完整响应按下节整scope合法空处理，不把列表展开成若干股空成功。
- 保存STOCK重试自身是一个明确已知的冻结单元，不需要外部成员集合；来源失败仅返回这个selector，成功空也只表示这个selector合法空。

### 5. accept的整体预检查与划分

严格按以下优先级处理；所有步骤结束前不对外暴露任何单元，不调用整行业务适配／持久化。

1. context检查异常原样传播。核对result非null、SUCCESS包络、pluginId／apiName与definition一致、params精确等于原FetchBatch.sourceParams、fields精确等于definition.columns顺序。FAILURE包络不能通过error文本猜来源码，按SOURCE_PAYLOAD_INVALID整scope失败；严格来源抛出的SourceException由sourceFailed入口处理。构造器已有rowCount／行宽不变量仍作为前置合同。
2. 校验所有显式failure的selector与冻结范围、对象条件相容；只有拟作为独立STOCK输出的failure才要求通过单股重建合同，并严格套用§4相同的规范单元及无重叠规则，不能把T05可重建的子RANGE或DATE当成完整RANGE单位。整REQUEST的原因不要求能单独重试那一股。越界日期／错误股票／未知时间语义、包络范围外REQUEST、不同范围重叠导致无法唯一归属，均SOURCE_PAYLOAD_INVALID整scope失败。对于已冻结REQUEST，允许与范围相容的明确局部STOCK失败用于说明“整个REQUEST未完成”；不能据此拆分。此检查只消费明确selector和冻结来源条件，不猜REQUEST策略的行字段。无法可靠证明局部selector属于范围时同样整体拒绝。
3. 冻结REQUEST路径：不读取或推断映射列，全部数据仅属于原REQUEST；failures非空则整个REQUEST失败，选按selector四字段字典序首项的errorCode并使用固定“请求包含明确来源单元失败”摘要，不回显来源消息。无failure则一个UnitInput，包括合法全空。业务键和值错误随后均影响这整个单元。
4. 可拆STOCK_TIME或保存STOCK路径：核对targetField列为STRING／ENUM、DATE或RANGE的timeField为DATE、MONTH的timeField为MONTH；缺列／错误类型属于DATASET_MISCONFIGURED，在open时即可依据definition拒绝，不能运行到部分提交后才发现。使用Processor内部无状态ValueConverter实例转换映射字段（复用现有实现，不复制规则）；目标必须是RecoverySelector可表示的规范代码，禁止trim之外另作大小写／市场推测；日期必须严格公历，月份严格YYYYMM再转YYYY-MM。null、不可解析、越界、原条件股票不符均SOURCE_PAYLOAD_INVALID整scope失败。
5. DATE单位取每行实际日期，必须落在scope.DATE或RANGE内；MONTH取完整年月，必须等于scope.MONTH；RANGE单位从冻结scope取完整RANGE（单日保留DATE），每行日期只用于包含性核对，不缩到返回行最早／最晚日期。SourceParameterMapper不支持的形状在open阶段拒绝／选REQUEST；不产生MONTH来源下的DATE或原条件下的STOCK_TIME。
6. 扫描**所有行，包括显式失败单元的行**，逐行建立selector。仅转换业务键字段并用旧FingerprintKeyCodec派生原business_key，再由BusinessKeyExtractor取得BusinessKey，建立 `Map<BusinessKey,RecoverySelector>`。同键落入不同单元，即使业务内容相同，也为SOURCE_PAYLOAD_INVALID整scope失败；键不可解析／缺失也使全局归属证明失败。相同键落在同一单元留给局部精确比较。普通非键、非归属业务列不在此转换，B.amount坏值不能提前让A／C失去隔离。
7. 核对显式failure及可观察行归属，再核对KnownMembers。已证明成员范围外的行／failure优先SOURCE_PAYLOAD_INVALID；其后仅对非空或带failure的结果判断缺少成员覆盖为SOURCE_COMPLETENESS_UNCONFIRMED；完全空且无failure进入下一步整scope合法空。不以来源failure优先跳过坏行归属；即使A先出现且正常，最后一行归属错误也不得暴露A。
8. 完整全空且无failure：首次得到一个原REQUEST合法空UnitInput；保存STOCK得到一个原STOCK空UnitInput。未声明的其他股票／日期不产生条目。非空成功时按selector四字段字典序稳定排列实际可观察单元；同单元行保持原包络顺序。明确失败的单元只放Failure，其行不送局部适配；其余各形成一个UnitInput。若有覆盖当前整个scope的明确REQUEST failure，则整scope失败，不保留成功候选。
9. 最后再检查context，随后一次性返回PreparedBatch。源包络params始终是原批次，即使切片只有某股某日也不改为单股来源参数；独立重试参数只通过T05验证，不要求切片包络params等于单元请求。

全局检查产生的SOURCE_PAYLOAD_INVALID／SOURCE_COMPLETENESS_UNCONFIRMED只用精确冻结scope失败，不能信任坏响应继续拆分KnownMembers；已保存STOCK仍为原STOCK scope。生产REQUEST没有行映射，不能宣称通过了独立股票归属；其范围安全仍依赖T07完整来源合同。

### 6. 逐单元适配、内容编码与错误

GenericDatasetAdapter新增两个公开方法，旧adapt签名／语义不变：

```java
AdaptedBatch adaptRows(DownloadEnvelope envelope, Instant ingestedAt);
Map<String,Object> adaptKeyRow(DownloadEnvelope envelope, int rowIndex);
```

抽取现有字段转换及业务键派生为私有辅助方法共用。adaptRows仍核对身份／有序列、逐行转换、nullable和复合键必填，返回全部适配行，保留输入重复和顺序；不去重、不报同键异值、不加重复日志。adaptKeyRow只转换businessKey.fields并执行同样的空值规则，FINGERPRINT使用旧codec产生business_key，返回不可变键投影；不遍历其他业务列。旧adapt继续按原Map.equals去重、保留固定重复日志及ADAPTER_TYPE_INVALID冲突，不改ValueConverter、FingerprintKeyCodec或BusinessKeyExtractor。

`validate`先检查context及索引DatasetKey，调用注册Generic的adaptRows；其ADAPTER_FIELD_MISSING／ADAPTER_TYPE_INVALID转为当前selector的RejectedUnit，固定安全摘要，不带源值／cause／suppressed。其他意外异常和context异常原样传播，不宽捕获成可继续的业务失败。DATA_CONFLICT由Core Failure表示，不扩大AdapterException允许码。

规范内容编码固定为版本1（常量 `VERSION=1`），不复用业务键指纹编码：

- 只使用definition.columns顺序的全部持久化业务列，精确排除 `ingested_at`、`source_plugin`、`source_api`、派生 `business_key`；不按Map遍历顺序或displayOrder重排，不按名字模糊排除普通业务列。
- 字节头为大端int版本与业务列数量。每列依次写：UTF-8列名的四字节长度＋内容、一字节固定逻辑类型码（STRING=1、TEXT=2、ENUM=3、DATE=4、MONTH=5、LONG=6、DECIMAL=7）、DECIMAL的precision／scale大端int（其他类型不写），再写一字节null标记（0为空；1为非空）。非空值写四字节UTF-8长度＋规范文本。长度以字节数计，空串和null不同，不使用分隔符拼接或JSON序列化。
- STRING／TEXT／ENUM保存适配后值；TEXT空白不得trim。DATE用ISO公历YYYY-MM-DD；MONTH把已适配YYYYMM编码为YYYY-MM；LONG用十进制Long.toString。DECIMAL继续验证声明precision及scale，setScale(UNNECESSARY)后toPlainString，禁止double／float、中途舍入或stripTrailingZeros改变声明尺度。
- encode仅接受适配后的合法类型／存在列；不对缺字段悄悄补null。适配错误先于内容比较；算法配置／不受支持类型的意外错误不伪装来源失败。返回字节数组不对外共享可变内部状态。

每个单元先完整适配，再用BusinessKeyExtractor逐行取原键，保留首个行及其规范字节。后续同键使用 `Arrays.equals` **精确比较字节**：相同去重，异值整个当前单元DATA_CONFLICT，不输出部分ReadyUnit。R候选仍是原始来源行数，不能用去重后行数代替。完成单元内检查后才与同轮索引比较，避免发现一个同轮相同行就跳过同单元其他坏值。

### 7. 同轮已提交索引与提交边界

索引存 `BusinessKey -> ContentDigest(version, sha256Hex)`，由适配规范值和既有BusinessKeyExtractor形成规范键；不重新哈希业务键，不改变大小写、复合键次序、数据库唯一键或指纹business_key。绑定DatasetKey实现来源隔离。digest按版本1规范字节用SHA-256转小写64位hex；记录不可变，不保留完整行或规范内容字节。

validate将本单元内已去重各行与索引比较：

| 索引状态 | 结果 |
|---|---|
| 无该键 | 保留实际待写行及其待确认digest |
| 同版本同digest | 本轮已确认存在相同内容，当前行不重复写；仍保留本单元完整sourceRowCount |
| 同键异digest | 整个当前单元DATA_CONFLICT，包括尚未冲突的其他待写行；原索引不变 |
| 版本不符 | 内存合同误用，固定IllegalStateException并停止调用，不视为业务冲突或自动重建索引 |

ReadyUnit.batch可为0行：来源合法空或本轮全部同键同内容。两者sourceRowCount分别为0或原来源行数，不能混同“来源返回0”。没有删除旧业务数据的操作。

只有T11未来确认单元事务成功（重试还必须业务写入＋原失败明细删除同事务成功），T12才调用 `index.confirmCommitted(ready)`，然后使用sourceRowCount及数据库实际WriteCounts更新计数。调用不能发生在适配完成、SQL成功返回、未确认外层事务提交或失败明细删除之前。确认回滚／本地拒绝丢弃ReadyUnit而不调用；提交未知同样不调用并停止整轮。索引确认插入为原子内存更新：先完整检查票据归属／重复确认及现有键一致性，再一次加入全部pending digest，不能插一半后抛错。

validate与该单元的提交／明确回滚串行，不能先验证多个ReadyUnit再并行提交。即使某单元在验证后回滚，后续单元同键也仍须实际写入；没有将“待提交”当“已提交”的预占键。独立新执行使用空索引，同键异值交给既有Upsert，不能读取数据库历史值或失败任务当作同轮成功证明。

T09测试只用一个显式确认的内存提交替身验证上述顺序；不添加事务类、不宣称该替身证明MySQL事务。原始包络／单位行切片在处理后释放引用，索引仅保存键＋摘要＋版本，执行结束整体释放，不持久化。

### 8. 全部失败分支及优先级

| 触发点 | T09输出／传播 | 可提交候选 |
|---|---|---|
| open参数／范围不兼容、错误来源策略、不能重建scope | 沿T05入口语义抛SOURCE_REQUEST_UNCONFIRMED或RETRY_TASK_INVALID；配置错误DATASET_MISCONFIGURED | 0，尚未取数，不产失败记录 |
| 首次STOCK公共map与REQUEST不同／无合法独立请求映射 | 取数前冻结完整REQUEST，不丢条件；仍执行完整来源门槛 | 仅后续完整REQUEST |
| context或未知内部运行异常 | 原样传播，调用者停止；不变成来源／单元明确失败 | 不发布新的候选 |
| sourceFailed收到业务SourceException，成员未知 | 一个精确scope Failure，保留errorCode、使用固定安全摘要 | 0 |
| sourceFailed收到同类错误，已有可信完整独立成员 | 各已知selector共同请求失败；不声称来源逐股错误 | 0 |
| SOURCE_TRUNCATED／SOURCE_COMPLETENESS_UNCONFIRMED | 同上处理完整失败，绝不接收部分页；成员列表不能使取全通过 | 0 |
| sourceFailed收到SOURCE_REQUEST_UNCONFIRMED | 属于执行条件未确认，原样抛出；不假称业务请求实际失败 | 0 |
| 包络／归属／业务键跨单元冲突／范围外failure | SOURCE_PAYLOAD_INVALID，整scope一个Failure；结构矛盾优先于局部failure及覆盖缺口 | 0 |
| 有可信成员但非空完整结果未覆盖某成员 | SOURCE_COMPLETENESS_UNCONFIRMED整scope；不补那一股失败 | 0 |
| REQUEST含一个或多个相容局部failure | 整个REQUEST失败，稳定首项来源码＋固定摘要 | 0 |
| STOCK_TIME明确且相容的局部failure | 原selector来源Failure，其余单元继续局部校验 | 其他独立单元 |
| 普通非键业务列转换／必填错误 | 当前selector ADAPTER_FIELD_MISSING／ADAPTER_TYPE_INVALID | 其他独立单元；REQUEST则整项 |
| 单元内同键异内容或同轮已提交键异内容 | 当前selector DATA_CONFLICT | 其他独立单元；旧索引保留 |
| 合法空或本轮全部重复 | 0写入行ReadyUnit；完整来源行数独立保留 | 原冻结合法单元 |
| 已accept后再次sourceFailed／accept | 固定IllegalStateException；无整批回退输出 | 不重建已成功范围 |
| SQL／失败保存／删除失败、数据库不可用、提交未知 | 不属于T09产生的Failure；由T11／T12决定明确回滚与停止，T09索引不提前变更 | T09不做事务或继续编排 |

所有新错误摘要固定、非空、最多512字符，不含控制字符、Token、源行、SQL、参数或异常链；不照抄FetchResult.UnitFailure.errorMessage或任意SourceException.message。保留错误码的现有retryable语义，不增错误码。细粒度失败必须先经过整体归属屏障，整批回退只存在于尚未发布任何单元的Session终结路径。

## Files

以下是T09启动后待实施清单；本设计阶段只创建本设计及内部设计报告。

- 修改 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`：共用转换／键派生，新增adaptRows及adaptKeyRow，保留旧adapt行为。
- 新增同目录 `BusinessContentCodec.java`：版本化全业务列精确内容编码和SHA-256。
- 新增 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/RecoveryUnitProcessor.java`：冻结入口、一次性Session、全局归属、单元切片／局部校验及全部嵌套合同；仅包可见KnownMembers受控入口。
- 新增同download目录 `CommittedKeyIndex.java`：单轮、单数据集只保存已确认键摘要。
- 修改 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/adapter/GenericDatasetAdapterTest.java`：精确更新公开方法穷举断言，新增不去重适配及只转换键列测试，保留旧入口行为断言。
- 新增同测试adapter目录 `BusinessContentCodecTest.java`；新增测试download目录 `RecoveryUnitProcessorTest.java`、`CommittedKeyIndexTest.java`。受控完整结果、成员证据和串行确认提交驱动均在测试源码，不新增生产fixture能力。
- 沿用现有 `BusinessKeyExtractorTest`、`GenericDatasetAdapterTest` 中的旧 `FingerprintKeyCodec` 黄金断言、`ValueConverterTest`、`DownloadServiceTest`及全量回归，不修改业务键或既有下载行为来迁就新测试。
- 实施完成时创建 `docs/verification/RANGE-T09-recovery-units.md`，按实际证据更新 `docs/traceability/tensor-range-requirements.md`；准确区分Core机制、事务未实施、来源未启用。新建实施文件按仓库规定加入Git版本控制，不提交／发布。看板／后继设计交接由任务主流程按顺序处理。

## Tests

先写整体最后一行归属错误不得暴露A、B普通amount错误只拒绝B、同键异内容应DATA_CONFLICT以及未确认提交不得污染索引的行为断言，再实现最小代码。先行编译缺方法不记成行为反例；报告保存实际运行失败与修正结果。本设计准备阶段不执行下列命令。

从仓库根执行：

```sh
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RecoveryUnitProcessorTest,BusinessContentCodecTest,CommittedKeyIndexTest,GenericDatasetAdapterTest,BusinessKeyExtractorTest,ValueConverterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

实施核对修正：仓库没有独立 `FingerprintKeyCodecTest`；旧编码黄金值、顺序、空值及异常合同位于 `GenericDatasetAdapterTest`，上方命令使用六个实际存在的测试类，保留原指纹验证范围。

Maven沿用前序已验证可支持Mockito附加的授权执行环境，不改依赖／禁用断言。每条命令实际退出0；Surefire目标测试必须实际执行且零失败／错误，不以无匹配跳过代替。两次verify记录真实数量、JAR合同及前端附带构建；当前POM未调度的数据库IT明确“仅编译、未执行”，不以acceptance配置名字宣称MySQL恢复事务通过。真实API、浏览器和T10／T11行为不在本项执行。

| 测试组 | 具体输入及可观察预期 |
|---|---|
| 严格完整入口信任 | T07后页超时／截断只将SourceException送sourceFailed，之前部分A行从不进入accept或validate；失败全集未知输出原REQUEST，严格确认完整才允许accept。旧DownloadService测试仍按原路径通过 |
| A／B／C隔离 | 测试definition有可选合法ts_code来源参数、DATE或RANGE来源形状、独立STOCK_TIME证据、相同REQUEST／STOCK公共map；A=000001.SZ、B=600000.SH、C=000002.SZ，同日2026-09-03。B非键amount非法，A／C Ready、B ADAPTER_TYPE_INVALID；去掉独立策略则整个REQUEST失败，无A／C候选成功 |
| 全局先行 | 最后一行目标null／非法代码、日期20260230、2026-09-04超出单日、业务键不可转换；均整scope SOURCE_PAYLOAD_INVALID，adaptRows调用0。普通字段坏值不在全局阶段失败；键列与归属列重合错误仍全局 |
| 业务键唯一归属 | 合成definition的键不含归属字段，同键分别归A与B（内容相同或不同）均整体拒绝；同键同selector才交局部比较。FINGERPRINT按原字段序生成键，nullable键语义与旧适配器一致 |
| 包络与失败列表 | 错plugin／api／原params／列顺序／FAILURE包络全部整scope拒绝；rowCount／错宽／重复列由DownloadEnvelope构造拒绝。越界／不相容failure、覆盖整个scope的REQUEST failure、相容B failure带部分行及多failure确定顺序逐项覆盖；同一RANGE策略下failure为子RANGE／DATE或与另一failure重叠时SOURCE_PAYLOAD_INVALID整scope失败，不能独立输出；完全匹配scope的STOCK RANGE failure才合法 |
| 无成员与有成员 | 无成员时完整A／C行只形成A／C，不补B；整体超时只一个REQUEST。受控KnownMembers A／B／C整体超时输出三项共同失败；成员值／证据／批次错配业务前拒绝；RANGE策略scope=2026-09-01/2026-09-10时，同股DATE 09-03＋全RANGE、子RANGE 01/05＋04/10、单独子RANGE或DATE均在open时SOURCE_REQUEST_UNCONFIRMED，来源调用0；仅全scope的RANGE可接受。完整非空仅A／C且B无failure整体完整性未确认；范围外D行优先payload错误；成员列表绝不使未知取全通过 |
| 空 | 完整0行无failure首次仅一个REQUEST空Ready，保存STOCK空仅原selector；两批受控“合法空／明确失败”可分别返回Ready／Failure。缺一股／缺一天不自动成功0行或失败；KnownMembers不展开全空为三股完成。空Ready不产生占位、写入行或删除调用 |
| 日期和冻结 | DATE／MONTH／RANGE分别按策略，月份覆盖完整月；RANGE行仅落9月3／7日仍输出原1～10日RANGE。保存REQUEST策略增强仍一项；保存STOCK RANGE不拆日。原生单日DATE来源两端相等，ORIGINAL_PARAMS仅REQUEST NONE |
| 公共map | 含原ts_code的income初始REQUEST保留代码，STOCK map与其不等时事前冻结REQUEST；市场exchange／exchange_id及原始日期保持。缺ts_code请求声明、对象子集不能重建、错selector／batch均业务前拒绝；不能因有归属列就补请求字段 |
| 精确内容 | Map插入顺序不同编码相同；1.2／1.20／1.200在DECIMAL(…,2)适配后相同，超scale需舍入仍Adapter错误；0.1及大精确数无浮点损失。中文／emoji的UTF-8字节长度、null／空串、TEXT空白、日期／月份、Long边界、类型标签、列边界分隔反例及普通非键列差异均覆盖 |
| 元数据排除／旧键 | 仅ingestedAt、source_plugin／source_api／派生business_key变化不改变内容编码；普通业务列变化必改变。固定版本1字节／摘要黄金样例由手工格式期望验证，旧FingerprintKeyCodec黄金摘要及BusinessKeyExtractor键值完全不变 |
| 单元内 | 同键同规范内容仅首行留待写，sourceRowCount仍含所有来源行；同键异值返回整个单元DATA_CONFLICT且无半个ReadyUnit。adaptRows保留重复；旧adapt同键异值仍ADAPTER_TYPE_INVALID |
| 同轮索引 | A Ready产生后索引仍空；将A模拟明确回滚并丢弃，再验证同键仍需写；确认A后相同内容0写入行，R候选保留原行数；异内容当前B DATA_CONFLICT，A索引不变；混合重复／新增／冲突整B拒绝且不插新增摘要 |
| 回滚与未知 | 内存提交替身明确回滚／未知均不调用confirmCommitted，索引不变；未知驱动停止不继续C。只有确认成功调用后增加摘要；先插一部分再遇非法票据不允许，重复确认／跨索引票据拒绝；新执行空索引不产生跨次冲突 |
| 生命周期与安全 | accept后／A确认后再次sourceFailed不能输出REQUEST；不能外部构造UnitInput／ReadyUnit绕过检查。context初始／全局末尾／局部异常实例原样传播。来源消息及行中哨兵不进入新摘要／cause／suppressed／日志 |

实施前后对49份Dataset和2份生产策略资源逐文件SHA-256核对，预期51份完全不变；核对Tushare生产来源表仍为空、无新增生产STOCK策略。验证报告记录输入读取、测试实际执行项、失败修正及独立评审结果，不复制旧通过数字作为本项证据。

## Acceptance

1. 整体包络／归属／键跨单元错误在任何单元暴露及提交前拒绝；REQUEST不猜行映射，T07完整来源仍是必要前提。AC-PRD-RANGE-11／29具有受控Core证据。
2. 可靠STOCK_TIME下B普通字段错误或明确来源失败只影响B；不可靠或公共条件不兼容时完整REQUEST处理，且能按冻结公共map及selector精确重建。AC-PRD-RANGE-08／14的本项证据通过。
3. 缺行不造股票／日期全集，不报未证实的0行成功或股票失败；完整合法空保留原scope且无写入／删除副作用。AC-PRD-RANGE-07／29的本项证据通过。
4. 内容包含全部持久化业务列并排除导入／审计元数据，规范编码带版本、类型、空值及长度；单元内精确比较，同轮只比较确认提交摘要，当前冲突不撤销旧成功，跨次仍沿原Upsert。AC-PRD-RANGE-09的本项证据通过。
5. 已保存REQUEST及STOCK RANGE不转换边界；整体失败回退仅在该请求尚未产生任何可提交单元时形成，不能包含已成功项。REQUEST公共条件与STOCK任务条件不兼容的受控反例通过。
6. 规定验证及独立规格／质量评审通过，报告与增量追踪清楚区分Core机制、后续事务／HTTP验收及生产来源缺口；新实施文件纳入Git。T09不提前实施T10或改运行入口，完整功能AC由后续任务继续验证。

## Risks

- 生产独立恢复与完整来源合同仍未启用；受控STOCK_TIME、KnownMembers和完整结果只证明内存机制，不能提升49项真实可用性或关闭T20来源缺口。
- 现有FetchResult没有逐单元成功空状态。故本项只确认完整scope空，不从非空批次遗漏成员推导空；将来若来源提供这种证据，须明确扩展合同及测试后再消费，不在本项猜测。
- T05公共map合同决定：含原ts_code且无法同时重建REQUEST和STOCK时首次保留REQUEST。该选择保留完整恢复与两表冻结结构；不强行实现无法可靠回退的细粒度子集。
- 当前恢复适配实现限定注册GenericDatasetAdapter；未来自定义适配器须提供等价的全局键投影／逐行适配合同后才能接入。T09不绕过注册插件，T12需在业务前检查兼容性。
- SHA-256摘要存在理论碰撞边界；单元内仍用精确字节比较。索引随本轮成功键数增长，仅存规范键、摘要和版本，不新增内存数量上限或持久成功账本。
- 索引只能由已确认事务结果更新，当前PersistenceService使用REQUIRED且T09不处理外层事务；T11／T12必须以真实提交确认实现该消费条件，SQL执行返回不够。没有未解决的产品选择；上述来源未知均有明确拒绝／REQUEST边界，不以TBD掩盖实施缺口。
