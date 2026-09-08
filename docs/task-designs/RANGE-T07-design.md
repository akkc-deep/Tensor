# RANGE-T07 Tushare 完整批次获取与来源错误设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T07`：提供完整批次取数入口，只有合法来源方式、所有页、整体协议及来源取全判据通过后才返回 `FetchResult`。失败不泄露来源内容，不返回部分数据，不改写原批次参数，也不自行重试。

当前49项均没有已启用的整体取全／分页合同。本项交付可验证的取全执行机制和生产明确拒绝；受控分页成功不代表Tushare真实接口已经支持分页。AC-PRD-RANGE-10／11／12／14／29只记录本任务的来源层证据。

## Scope

- 消费T01取全依据、T04批次／策略／结果合同、T05精确来源参数；添加明确的完整批次SPI入口、Tushare取全执行器、可注入的逐接口来源合同及安全错误分类。
- 受控验证单次、单日、完整月份、原生范围及多页的结束与全批次失败行为；生产来源合同注册表保持空，不发送猜测的分页参数。
- 保留当前HTTP绑定、元数据、日志及三参 `download` 的原运行行为。T08／T12使用新完整批次入口，T14统一切换HTTP；不得从新入口回退到旧单次入口以绕过取全。
- 不实施批次规划、日历获取、股票／时间恢复单元划分、适配及事务、失败记录、HTTP切换、前端或真实来源验证。不增加产品条件、日期限制、通用行数／页数／字节／时长上限、自动重试、失败后拆批或从失败页续传。
- 不修改49份Dataset定义、生产策略／schema／研究证据，不新增CONFIRMED状态，不读取凭证或调用真实业务API。ISSUE-008九项继续“不依赖，未解决”。

## Approach

### 1. 已读取的直接输入与证据判断

依据 [TRD §4.4、§5.2](../design/Tensor_区间下载_TRD_v1.0.md)、[官方调研 §5](../research/2026-09-08-tushare-range-batch-research.md)、[T01完整报告](../research/RANGE-T01-source-capabilities.md)及[逐项JSON](../research/RANGE-T01-source-evidence.json)，并消费[T04设计](RANGE-T04-design.md)／[验证](../verification/RANGE-T04-plugin-contracts.md)、[T05设计](RANGE-T05-design.md)／[验证](../verification/RANGE-T05-parameter-conversion.md)和实际Java合同。

| 已确认输入 | 本任务处理 |
|---|---|
| 49项REQUEST；40 DOCUMENTED_CANDIDATE、8 UNCONFIRMED、forecast CONFLICT；全部整体取全／分页UNCONFIRMED | 请求候选通过不等于执行许可。先拒绝未确认／冲突请求，再查适用于完整参数的来源取全合同；没有合同以SOURCE_COMPLETENESS_UNCONFIRMED拒绝，业务调用为0 |
| 45份可读输入表均无limit/offset，4份页面缺失；HTTP只公开code/msg/data.fields/data.items | 不新增通用offset算法，不给TushareResponse添加猜测total/cursor/truncated字段，不解析msg猜取全；归一化页观察仅由经过核实的接口适配器提供 |
| stock_basic公开“一次可拉取完”，同时6000边界随市场增长，所选L/P/D及当期边界待验证 | 保留该有价值依据，但不将全部状态／当期响应直接认定完整；本项不注册生产单次保证，6000行本身不自动判TRUNCATED |
| trade_cal可检查所选交易所×每个自然日恰一条有效记录，须包含两端和休市；BSE未列入官方输入 | SSE/SZSE的真实日期筛选／边界未验证，仍无生产取全合同；BSE先返回SOURCE_REQUEST_UNCONFIRMED，不能借margin的BSE依据放行。不加is_open过滤，不以相同行数代替身份与日期集合 |
| daily单日6000、broker_recommend单月1000、income区间、margin保留exchange_id、new_share为ipo_date、namechange为ann_date | 仅按T05生成的精确形状消费；短响应和合法空都须有对应取全合同。原生单日保持相等start_date/end_date |
| fina_indicator起止为报告期、fina_mainbz公告输入／输出缺失、express及top10系列缺股票、forecast同页冲突 | 不补股票、不换VIP、不用报告期／解禁日期替公告日、不扩原条件或从输出列猜来源方式 |

本设计阶段核查 `TushareProClient.execute`、`TushareResponseValidator`、`TushareErrorClassifier`、`TushareProClientTest`：现有客户端每次只发一个POST，严格JSON解码、字段顺序及行宽校验，保留BigDecimal和合法null；`stk_holdernumber`既有公告时间规范化保持。包络身份来自请求定义，并非来源独立回传身份；本项不能将本地赋值当作真实来源身份认证证据。

### 2. 完整批次入口与过渡边界

在共享 `DataSourcePlugin` 添加语义明确的入口：

```java
default FetchResult fetchBatch(ApiName apiName, FetchBatch batch,
        DownloadContext context);
```

缺省实现只检查三个参数非null，调用 `context.checkServerState()`，然后抛 `SourceException(SOURCE_COMPLETENESS_UNCONFIRMED, "Source completeness is unconfirmed")`。不能委托 `download`，不能以默认空结果成功。保留原三参 `download`，不恢复旧两参方法或增加二进制兼容垫片。这里两入口分别表达当前原单次运行和严格完整批次，不是双版本下载兼容；这是T07对T04合同的明确增量。

`TushareProPlugin.fetchBatch` 按当前download的顺序校验apiName／batch、readiness、查找definition及ApiDescriptor，再检查context并委托下节执行器。未知API仍为固定安全IllegalArgumentException，插件不可用仍为PLUGIN_DISABLED。返回值只有完整成功且failures为空，或抛异常；本项没有可信逐股来源状态。

生产构造器现有签名不变，在构造中创建 `new TushareCompleteBatchFetcher(client, Map.of())`。增加包可见构造器，最后一参为 `TushareCompleteBatchFetcher`，供同包插件测试注入受控合同；生产配置继续调用原构造器，无配置开关／自动发现／docs运行时加载。执行器对自身来源注册表作防御复制。

T08规划负责生成经过T05校验的 `FetchBatch(sourceParams,recoveryPolicy)`；T12执行只调用 `fetchBatch`。`FetchBatch`中既有参数是完整原批次，不带分页控制键。当前HTTP继续走原 `download`，其成功只表示历史单次协议通过，不计为新区间取全。不得在T07改 `DownloadService`、Deserializer、DTO或日志的入口选择。fixture本项沿用缺省拒绝；其旧scenario下载不受影响，后续完整执行受控fixture由对应任务显式接入。

### 3. 最小内部来源合同

新增两个类，全部来源专属辅助类型嵌套于第二个类，避免建立通用分页框架。

```java
public final class TushareCompleteBatchFetcher {
    public TushareCompleteBatchFetcher(TushareProClient client,
            Map<ApiName, TushareBatchSource> sources);
    public FetchResult fetch(DatasetDefinition definition, ApiDescriptor api,
            FetchBatch batch, DownloadContext context);
}

public interface TushareBatchSource {
    Session open(DatasetDefinition definition, ApiDescriptor api, FetchBatch batch);
    interface Session {
        Set<String> paginationParameters();
        Map<String, Object> pageParameters(String cursor);
        Observation observe(String cursor, DownloadEnvelope page);
        void validateComplete(DownloadEnvelope complete);
    }
    record Observation(End end, String nextCursor, Long totalRows) {}
    enum End { CONTINUE, COMPLETE, TRUNCATED, UNCONFIRMED }
}
```

执行器和接口置于 `plugin.tushare.client`。sources键／值非null且防御复制；构造不合法用固定安全IllegalArgumentException。Session、分页声明、分页map、Observation若返回null，或分页声明／页参数违反下列结构规则，统一SOURCE_PAYLOAD_INVALID；只校验并映射这些已知结构错误，不捕获任意来源／服务端异常。`open`每批创建独立Session，不共享游标／累计行数；必须验证合同适用于该api及原参数，并冻结源方需要的范围／结束依据。不匹配请求条件抛SOURCE_REQUEST_UNCONFIRMED，缺取全证据抛SOURCE_COMPLETENESS_UNCONFIRMED。生产注册表为空，T07只在测试源码实现Session；不新增任何生产Tushare分页适配器或全量保证适配器。

- `paginationParameters`是这份合同核实可使用的分页键集合；执行器冻结它，要求每个键匹配`[a-z][a-z0-9_]{1,63}`且无null。禁止键集合精确为baseParams键、definition.parameters名称、api.sourceParameters名称、api.parameters名称，以及固定保留键`ts_code/trade_date/ann_date/month/start_date/end_date/exchange/exchange_id/hs_type/list_status`的并集；这10项是当前49份Dataset使用的业务参数，不能因当前接口／批次未携带而变为分页键。声明集合与禁止集合必须不相交，否则SOURCE_PAYLOAD_INVALID且不调用pageParameters或client。空集合表示只能单次请求。未来来源额外业务条件不能通过声明为分页键绕过其独立合法性核实。
- 首次调用 `pageParameters(null)`；后续cursor必须来自上一观察的nextCursor。返回仅含声明分页键的String值，不得含原批次键或未知键，允许首请求空map；执行器冻结参数并与原批次合并。没有自动生成limit/offset，也不对游标作数字解释。
- `observe`接收单次客户端已通过整体协议检查的包络。它将**该接口已核实**的结束证据转换为Observation；这些枚举不是Tushare wire字段。COMPLETE必须有适用于该请求的结束保证，不能凭HTTP 200、空页、短页或未确认上限产生。当前没有这样的生产适配器。
- `validateComplete`在全部页合并后检查合同所要求的全集／对象／时间覆盖；合法空也必须通过。适用合同已经在open确认后，观察到缺日、重复日期掩盖缺日、错市场、越界或不可解析日期等覆盖矛盾，明确抛`TushareErrorClassifier.invalidPayload()`，即SOURCE_PAYLOAD_INVALID，无cause／suppressed。请求适用性／证据缺失在open分别使用SOURCE_REQUEST_UNCONFIRMED／SOURCE_COMPLETENESS_UNCONFIRMED；结束依据未知使用Observation.UNCONFIRMED。该回调不执行适配／事务或按股票生成失败状态，不能用空实现掩盖尚未核实的范围依据；其他运行异常保持原样，不宽泛捕获。
- Observation非null；end必填；CONTINUE的nextCursor必须非null且`!isBlank()`，不strip或改写游标；其余状态nextCursor必须null；totalRows可null，否则非负。record紧凑构造器违反任一条件直接抛`TushareErrorClassifier.invalidPayload()`，不先抛IllegalArgumentException再依赖调用方捕获；执行器遇null Observation也使用同一工厂。受控测试逐项断言SourceException／SOURCE_PAYLOAD_INVALID及整批无结果。
- Session及Observation的toString不得输出游标、参数或页内容；观察值、页请求参数及安全异常不进入日志。无需增加来源响应缓存或持久化会话。

`TushareProClient`仍只暴露 `execute(DatasetDefinition, Map)`。执行器逐页调用这一实际客户端，复用精确数值解码、请求投影、传输错误和协议校验，不复制HTTP实现。当前TushareResponse不公开通用页元数据；未来确有证据需要读取额外来源字段时，应在该接口适配实现的设计中明确扩展，不能靠本项抽象接口宣称已经能读取total/cursor。

### 4. 执行算法与全批次失败规则

1. 固定definition／api／batch／context非null，立即执行context.checkServerState，然后才检查身份、证据或访问source。该首次检查失败时source.open、paginationParameters、pageParameters、client全部调用为0，故障实例原样传播。随后核对definition.datasetKey.apiName与api.apiName相等、插件身份为tushare_pro，batch.recoveryPolicy与api.downloadPolicy.recoveryPolicy相等；不匹配安全SOURCE_REQUEST_UNCONFIRMED。原参数来自T05，执行器不二次生成日期或归一化值；用不可变副本作为整个调用的baseParams。通过插件入口调用时先有插件自身context检查，再有执行器此初始检查，不省略任一个。
2. 检查requestEvidenceStatus为DOCUMENTED_CANDIDATE且来源方式存在；UNCONFIRMED／CONFLICT拒绝。对trade_cal的exchange=BSE按T01条件缺口拒绝。随后查source；缺失则SOURCE_COMPLETENESS_UNCONFIRMED，source.open／client均不调用。空表由生产构造固定，不能由调用者传一个“已确认”布尔值绕过。
3. `open`后冻结分页声明；初始化cursor=null、已用游标集合、已发实际参数集合、累计rows和可选固定total。每页生成页附加参数并严格检查第3节的键／值约束，合并到base副本。相同实际参数第二次出现即SOURCE_PAYLOAD_INVALID，不再次发请求；这与游标重复检测共同阻止A→B→A及伪进展。
4. 在每次client调用前及其成功返回后各执行context检查；异常原样传播，不封装成来源错误，不根据客户端断线／Thread.interrupted取消。旧客户端每次POST恰一次，无重发、sleep或退避。后页HTTP／网络／业务／协议异常保留既有安全分类，累计rows仅为本地变量，立即丢弃。
5. 对每个返回包络检查非null、SUCCESS、pluginId/apiName等于definition、params恰等于本次实际请求（含分页键）、fields恰等于定义列的有序列表、rowCount等于data大小、每行宽度一致；任何失败SOURCE_PAYLOAD_INVALID。不能接受错API、错页参数、重排字段或FAILURE包络后继续。客户端来源body结构校验仍先于观察。
6. 取得Observation并检查结构；按页顺序追加原始rows，不去重、不排序、不转换BigDecimal，不因重复业务键掩盖来源页重叠。累计计数使用long；最终超过DownloadEnvelope现有int可表达范围以SOURCE_PAYLOAD_INVALID拒绝，这是既有结果类型边界，不增加产品行数上限。
7. 若任一页给出totalRows，保存首次值；其后给值必须一致（后页未给值仍保留已有total），累计不得超过它；COMPLETE时如有total，累计必须精确相等。变化、超出、结束不足均SOURCE_PAYLOAD_INVALID。total只是一项一致性检查，不会单独授予COMPLETE，尤其累计达到total且CONTINUE仍按协议处理下一页。
8. CONTINUE只在分页声明非空时允许；没有声明却要求后页以SOURCE_COMPLETENESS_UNCONFIRMED拒绝。nextCursor若已使用或等于本页cursor则SOURCE_PAYLOAD_INVALID；否则记录并继续。空页也不能自行结束；有合法新游标及已核实继续依据则可继续，不加猜测页数上限。
9. TRUNCATED仅表示合同确认来源已截断，抛SOURCE_TRUNCATED。UNCONFIRMED抛SOURCE_COMPLETENESS_UNCONFIRMED。达到公开行数边界、缺总数或没有下一页字段本身不能产生TRUNCATED。错误优先为已经观察到的结构／身份／总数矛盾，其次来源结束分类；不为了返回截断而掩盖坏协议。
10. COMPLETE后构造候选完整包络，params恢复baseParams，fields为定义列、rows为全部页顺序并集，status=SUCCESS且error=null。调用session.validateComplete，再执行最终context检查；全部通过才返回 `new FetchResult(envelope,List.of())`。任何检查失败均不得返回候选包络或部分成功列表。

已发参数和游标是每次fetch的内存状态；同一原批次手动再调用始终从null开始，不复用旧session或失败页。执行器不保存恢复任务，不把缺少的股票补成UnitFailure。

T07负责分页协议及来源合同整体范围／全集检查。T09负责所有行可解析并唯一归属恢复单元、业务键归属冲突及局部业务字段错误的隔离；T12必须先完成T09整体归属检查再允许任何单元提交。来源有ts_code不意味着T07可猜股票全集，REQUEST亦须先通过T07。受控validateComplete中的日期／对象检查仅证明来源合同能够拒绝越界，不替代T09所有行归属算法或最终AC-29。

### 5. 错误及安全

沿用 `SourceException`、T03错误HTTP／retryable映射，不新增错误码。在现有包可见 `TushareErrorClassifier`增加三个固定工厂：`requestUnconfirmed()`、`truncated()`、`completenessUnconfirmed()`，分别使用SOURCE_REQUEST_UNCONFIRMED／SOURCE_TRUNCATED／SOURCE_COMPLETENESS_UNCONFIRMED，固定文案为 `Tushare request conditions are unconfirmed`、`Tushare response was truncated`、`Tushare completeness is unconfirmed`。现有 `invalidPayload()`用于整体协议矛盾。

HTTP401／403／429、不可用、网络、超时及业务错误仍用现有分类，不从任意来源msg新增“截断”关键词猜测。来源错误均无cause／suppressed，消息不含Token、URL、来源正文、股票／日期参数或游标。Session内部受控结构错误统一使用上述安全来源错误；执行器不得捕获所有RuntimeException并吞掉服务端检查异常。现有maxResponseBytes仍按原单次响应保护执行，不将其改成新批次总字节限制，也不把超过该保护当成已知来源截断。

## Files

下列为T07启动后待实施文件；本设计阶段仅创建本设计。

- 修改 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`：明确完整批次默认拒绝入口；不改DownloadContext／FetchBatch／FetchResult结构。
- 新增 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareCompleteBatchFetcher.java`、`TushareBatchSource.java`：批次编排和嵌套来源会话合同。
- 修改同client目录 `TushareErrorClassifier.java`：三个固定安全错误工厂。`TushareProClient.java`／`TushareResponseValidator.java`／响应DTO预期无需修改，复用原单次实现；不得为受控分页添加猜测wire字段。
- 修改 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`：fetchBatch入口、生产空注册表和包可见测试注入；生产配置保持原构造调用。
- 新增 `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareCompleteBatchFetcherTest.java`；扩展现有同模块 `TushareProPluginTest.java`、client下 `TushareProClientTest.java`；测试源码内嵌脚本式来源Session，禁止放入main资源／注册表。
- 扩展 `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/PluginApiSurfaceTest.java`、`download/DownloadContractsTest.java`：新入口及默认不委托旧download。PluginApiSurfaceTest明确断言fetchBatch为default且参数／返回类型精确匹配，同时保留confirmCalendar为default和三参download为抽象的断言；原先默认方法数量等穷举断言按增量更新，旧两参不存在的断言保留。
- 创建 `docs/verification/RANGE-T07-complete-batch-fetch.md`，更新 `docs/traceability/tensor-range-requirements.md`：准确记录规定验证、先行失败、来源层AC证据及未验证事项。实现完成后由主流程记录完成及准备后继。
- 不改49份Dataset、2份策略资源、数据库、Core／Web运行入口或真实来源资料。新文件加入Git暂存，不提交／发布，不覆盖既有暂存工作。

## Tests

先写生产候选请求在完整批次入口拒绝且旧client调用为0的断言，再实现缺省门槛；然后写受控两页及后页失败断言，确认失败后实施最小执行器。保留先行失败与修正后的准确日志，不将首次编译缺类型当作运行行为失败。

测试Session固定在src/test中，用声明的合成键 `page_token`，首请求无附加参数、第二页为 `{page_token:'p2'}`、第三页为 `{page_token:'p3'}`；这些值只证明执行器合同，明确不代表任何Tushare接口的参数。WireMock只监听测试本机；通过真实client解码，Session从预置脚本输出结束观察，不从HTTP成功或行数产生取全保证。

| 场景 | 具体输入与预期 |
|---|---|
| 生产拒绝 | 真实49描述符／策略。8未确认及forecast冲突为SOURCE_REQUEST_UNCONFIRMED；其余40为SOURCE_COMPLETENESS_UNCONFIRMED；另测trade_cal exchange=BSE为请求未确认。全部client及source.open零调用；stock_basic L/P/D、trade_cal SSE/SZSE不能被文档候选误启用 |
| 默认与过渡 | 未覆盖fetchBatch的插件默认明确拒绝，context一次，旧download调用0。Tushare不可用／未知API保持原错误；旧daily、month、原生范围及fixture HTTP绑定／下载仍通过，未新增分页键 |
| T05端到来源形状 | 用SourceParameterMapper真实api及REQUEST选择器生成daily 2026-09-03→trade_date=20260903；income保留000001.SZ及20260903～20260907两端；margin DATE生成exchange_id=SSE及相等起止；broker_recommend MONTH=2026-02→month=202602；stock_basic list_status=L、index_classify空map；三原生单日保留相等两端。受控合同下WireMock实际JSON逐键相等；不再次改参 |
| 两页成功 | daily原map `{trade_date:'20260903'}`，第1页2行、CONTINUE p2 total=3，第2页1行、COMPLETE null total=3；恰2次POST，只有第2次带合成page_token，合并3行有序、params仍原map、failures空。含0.1／12345.123456789012345678／1e-18／1.2300及null的单元值精确保留 |
| 单次与合法空 | 无分页声明，一页COMPLETE total=1且validateComplete通过→一次完整成功；空页COMPLETE total=0且合同明确允许空→空成功；同样空页UNCONFIRMED→拒绝。无分页声明却CONTINUE→完整性未确认，不发第二次 |
| 后页失败 | 首页已有2行；第2页分别HTTP429／503、超时、非法JSON、缺字段／错宽／业务权限错误；返回相应安全错误，整次无FetchResult，恰2次调用，第三页0次，不重试。再次手动fetch从第一页开始 |
| 游标与实际参数 | p2→p2、p2→p3→p2均SOURCE_PAYLOAD_INVALID且不重复调用；不同游标生成相同实际参数也在重发前拒绝；Observation的null end、null／空串／纯空白CONTINUE游标、终止仍有游标均精确断言SourceException／SOURCE_PAYLOAD_INVALID，无cause／suppressed |
| 总数 | 第1页total=3、第2页4；累计4而total=3；COMPLETE累计2而total=3，均协议失败无部分结果。前页null后页3可接受；前页3后页null保留总数；所有total为null只有明确COMPLETE及validateComplete通过才成功；负数拒绝 |
| 截断与未知 | 受控明确TRUNCATED→SOURCE_TRUNCATED；UNCONFIRMED→SOURCE_COMPLETENESS_UNCONFIRMED。生产daily行数0／5999／6000均不能凭行数放行；生产入口先拒绝且零请求，不为测试假装已观察生产6000行 |
| 参数与身份 | 声明trade_date（在daily原map中）或ts_code／exchange（不在该map中）为分页键，均SOURCE_PAYLOAD_INVALID且pageParameters／client为0；非法标识同样拒绝。分页map试图放业务键、未声明offset、非String、null均SOURCE_PAYLOAD_INVALID且client为0。mock client返回错plugin／api／实际params／列顺序／FAILURE包络同样拒绝。原map、注册表、分页声明外部修改不影响本批次 |
| 整体来源范围 | 受控daily合同要求所有trade_date=20260903，越界20260904或无法解析的日期导致validateComplete抛SOURCE_PAYLOAD_INVALID、整批无结果；不生成股票失败。受控trade_cal合同对SSE 20260904～20260906要求3日各一条，含休市；缺日、相同行数但重复日／错市场／越界均精确断言SOURCE_PAYLOAD_INVALID且无cause／suppressed。该脚本不注册生产，也不证明真实来源筛选 |
| 服务端与安全 | 直接fetch首次context失败时open／paginationParameters／pageParameters／client计数全为0；第1页后检查失败仅1次POST且无结果；最后validateComplete后检查失败也无结果。保留同一服务端异常实例；两页成功顺序为初始check→open／声明→pageParameters→check→POST→check→observe（重复第二页的pageParameters至observe）→validateComplete→最终check，共6次check；经插件入口为先额外一次check，共7次。来源哨兵Token／URL／body／cursor不出现在message／cause／suppressed或日志中，错误retryable仍等于既有ErrorCode |

从仓库根执行：

```sh
mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-plugin-tushare -am test
mvn -f data-plane/pom.xml test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

各命令须实际退出0。Maven沿用已确认能支持Mockito附加JVM的授权执行环境，不改依赖或禁用断言。运行前将49份Dataset及2份生产策略资源SHA-256存临时目录，运行后逐文件核对完全相同；报告记录方法、结果及实际测试数量。数据库IT若当前POM未调度则明确“仅编译、未执行”，浏览器／真实业务API未运行；不能把verify或受控分页称为真实来源验收。只有新变更或失败才补跑相关检查。

## Acceptance

1. `fetchBatch`具有默认明确拒绝和Tushare完整批次入口；现有三参download与HTTP过渡行为保持，新入口没有旧路径回退。T08／T12可按固定SPI及FetchBatch直接消费。
2. 所有生产来源取全合同仍为空；49项Q／C拒绝和trade_cal BSE条件缺口有可观察零调用证据。没有发送未经核实的分页参数、更新证据状态或误启用STOCK。
3. 受控单次／两页／空结果只有整体协议、结束依据及validateComplete通过后才输出；后页失败、重复游标／请求、总数矛盾、明确截断和完整性未知均整批拒绝，且安全错误可区分。
4. 包络保留原完整批次参数和全部页精确数值，来源条件未被最后一页覆盖；不自动重试／拆小／续页，不从缺少行编造逐股失败。
5. 规定验证及独立评审通过，新文件已暂存。验证报告只回填AC-PRD-RANGE-10／11／12／14／29的本项来源机制证据；T09归属、数据库事务、HTTP区间、真实来源支持及最终功能AC保持后续责任。

## Risks

- 生产完整批次路径当前全部拒绝是T01明确证据状态的执行结果，不是最终49项功能可用。stock_basic一次全量说明、trade_cal精确全集判据须保留并继续核实，不能以本项受控成功替代T20所需来源证据。
- Tushare现有HTTP响应没有通用游标／total；本设计的Session是内部来源证据接口，当前只有测试实现。将来注册生产实现必须取得同API、同原条件下的合法分页参数、稳定遍历、结束、日期／对象和异常截断依据；不能仅实现COMPLETE回调或改状态枚举。
- 旧单次运行与完整批次语义并存直到T14统一切换。任何后续区间执行误用download会绕过本任务门槛，必须在T12／T14集成测试断言只调用fetchBatch；本项不提前改变HTTP可用性。
- T07无可靠股票全集，不提供逐股来源状态；T09整体归属检查仍是提交前必需门槛。本项的协议安全不等于所有恢复单元可独立成功。
- 本设计仅为T06完成后的T07准备，不代表实现、测试或真实来源调用已经执行。无需要用户新增产品决定的缺失要求；来源未知已按任务允许的明确拒绝处理。
