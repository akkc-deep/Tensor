# RANGE-T06 适用日历获取与执行前确认设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T06`：插件根据已确认适用关系取得本轮必要日历，完整验证身份、覆盖、开闭标识及修订确认后才返回CalendarDecision；无法确认时明确拒绝，让后续编排不能把未知日期当休市。

## Scope

- 在Tushare插件内增加日历来源端口、必要身份解析和统一确认提供器，接入现有confirmCalendar SPI；使用受控来源验证完整获取、同轮复用、并集和失败边界。
- 严格消费T02的19项结论及T04策略／CalendarScope／CalendarDecision，不更改来源研究或49项策略。非交易日期不产生辅助来源请求。
- 本项没有已具备完整运行证据的生产日历适配器可启用：T02的唯一DOCUMENTED项hsgt_top10仅为静态来源／适用关系，仍缺本轮获取及修订确认；其他18项为UNCONFIRMED／CONFLICT。生产来源注册表固定为空，全部19项保持CALENDAR_UNCONFIRMED。这是既定证据门槛的拒绝实现，不是19项休市过滤正式通过；不得把空表改成周末过滤、静态年度表或读取时间兜底。
- 受控来源位于测试目录，不将测试数据、确认布尔值或来源注入入口暴露为配置、HTTP参数或产品开关。未来真实适配器须先补足逐项来源／历史生效／特殊修订证据，并另经验证后才能注册；本任务不凭猜测实施外网抓取器。
- 不实施T07完整业务取数、T08规划、T10失败表、T12／T13执行编排、T14 HTTP切换、前端或真实业务API调用。首次不建任务／重试保留明细在本项通过无存储副作用保证，实际表及业务请求由T18验证。

## Approach

### 1. 输入依据及证据边界

依次读取 [PRD §4](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD §5.1](../design/Tensor_区间下载_TRD_v1.0.md)、现有TushareProPlugin／client，再完整读取 [T02报告](../research/RANGE-T02-calendar-capabilities.md)及[逐项证据](../research/RANGE-T02-calendar-evidence.json)，最后读取 [T04设计](RANGE-T04-design.md)、[T04验证](../verification/RANGE-T04-plugin-contracts.md)与共享日历类型。

T02的19项分类12／1／3／2／1，状态1 DOCUMENTED／16 UNCONFIRMED／2 CONFLICT。研究JSON不在运行期加载；没有新来源证据时生产策略状态不升级。T04的CalendarDecision构造器只校验集合／深复制，不能证明必要身份齐全、来源权威或新鲜。本项在构造它之前完成这些检查。

T02规定每轮重新获取或重新确认来源及修订，不跨执行缓存；读取时间、固定TTL、业务数据更新时间均不替代日历更新依据。HKEX2026表只能连同北向交易规则、适用年份及修订使用，不能把任意空白当开盘，也不能把普通A股／香港日历代替互联互通。CSF第21条及暂停公告不能直接固化为普通交易所并集或无限期休市。ISSUE-008九项真实排除不变；本任务涉及其中hk_hold、hsgt_top10、moneyflow_hsgt、top_inst四项。

### 2. 最小插件内合同

新增 `plugin.tushare.calendar.CalendarSource`，仅依赖JDK和现有plugin-api，不引入Core、任务存储或Servlet类型：

```java
@FunctionalInterface
public interface CalendarSource {
    CalendarData fetch(Set<String> identities, Set<LocalDate> dates);
    record CalendarData(String sourceId, LocalDate effectiveFrom, LocalDate effectiveThrough,
            boolean revisionsConfirmed, List<CalendarRow> rows) {}
    record CalendarRow(String identity, LocalDate date, String isOpen) {}
}
```

CalendarData保存来源适配器对本轮的观察，不是执行许可。sourceId必须与调用的注册键相同；有效期是该来源及适用规则已经确认的历史生效范围，不是取数时间。revisionsConfirmed只有来源适配器在本次fetch内完成权威修订核查才可为true；构造类型或设置字段本身不证明新鲜。当前生产不创建true的实例，也不提供配置设置该字段。不能为真实适配器补造“已核查”的默认值。

rows以防御副本保存（非null列表不可变，元素为不可变record）；允许CalendarData／CalendarRow携带异常原始字段供提供器统一拒绝。null列表或null行可保留为无效来源观察，不在该record中抛出带输入的解析异常。参数及返回值全部不带凭证、HTTP正文、任意诊断、SQL或页面文案。来源适配器若不能获取完整、适用且修订已确认的结果，抛CalendarUnconfirmedException；真实实现可保留底层安全SourceException供提供器转换。来源端口不接收DownloadContext，服务端检查只由提供器在fetch前后调用，避免把context故障混入捕获来源异常的边界。

新增 `plugin.tushare.calendar.TushareCalendarProvider`：

```java
public TushareCalendarProvider(Map<String, CalendarSource> sources);
public CalendarDecision confirm(ApiDescriptor api, CalendarScope scope, DownloadContext context);
```

构造器防御复制sources，拒绝null键／值及不符合`[A-Z][A-Z0-9_]{1,63}`的来源键；sources在整个提供器生命周期只读。测试直接构造提供器并注入受控来源，生产TushareProPlugin只构造`new TushareCalendarProvider(Map.of())`，保持现有四参构造器，不新增SPI或全局可变注册方法。

每次confirm调用拥有独立局部结果map。fetch的identities和dates均为不可变副本，dates精确等于本轮实际待确认日期集合（包括不连续日期）。不得把scope.publicParams里的原始start_date/end_date用于推算日期，不把股票或市场用户条件透传为任意上游参数；只有已解析的必要身份和实际日期传给来源。

### 3. 必要身份与来源登记

先检查api、scope、context非null，调用context.checkServerState；再检查mode=TRADE_DATE_RANGE及calendarEvidenceStatus=DOCUMENTED。UNCONFIRMED／CONFLICT立即拒绝，来源调用为零。模式／类别／接口不符合下表，或必要集合尚未知，也立即拒绝。非交易日期被误调用时同样返回CALENDAR_UNCONFIRMED且零辅助来源；调用方T08必须仅为交易日期调用此SPI，不返回伪造“全开盘”或null Decision来表示不适用。

下表固定代码分支与内部来源键。它保存已建立的必要身份或未来有完整证据时的接线位置；表中存在分支不能跳过上述DOCUMENTED门槛或缺来源检查。来源键是内部定位标识，不表示适配器已经存在。

| 接口／类别 | 必要身份 → 来源键 | 本项生产行为 |
|---|---|---|
| moneyflow／C-A | SSE→SSE_CALENDAR，SZSE→SZSE_CALENDAR | UNCONFIRMED，零来源拒绝 |
| stk_limit／C-A | SSE→SSE_CALENDAR，SZSE→SZSE_CALENDAR，BSE→BSE_CALENDAR | UNCONFIRMED；不删BSE、不以SSE替代 |
| margin／C-M | 只取publicParams.exchange_id对应的SSE／SZSE／BSE及上述一个来源键 | 三分支均UNCONFIRMED；无默认市场；非法／缺失／非字符串条件拒绝 |
| slb_len／C-S | CSF_REFINANCING→CSF_REFINANCING_CALENDAR | UNCONFIRMED；不代用交易所日历 |
| slb_sec、slb_sec_detail／C-S | CSF_SECURITIES_REFINANCING→CSF_SECURITIES_CALENDAR | UNCONFIRMED；业务暂停不能当全闭市 |
| hsgt_top10／C-N | NORTHBOUND_SH、NORTHBOUND_SZ→同一个HKEX_NORTHBOUND_CALENDAR | 静态DOCUMENTED，但生产注册表缺来源，零来源拒绝 |
| adj_factor、block_trade、daily、daily_basic、margin_detail、monthly、suspend_d、top_inst、top_list、weekly／C-A | 完整必要市场集合未知，无可执行映射 | 明确拒绝，不把JSON的已证实部分当完整集合；两个CONFLICT保留 |
| hk_hold／C-N、moneyflow_hsgt／C-X | 方向未知，无可执行映射 | 明确拒绝，空集合不是全休市 |

19项名单和对应calendarProfile必须与T04策略逐项一致；不单按profile猜接口范围。对margin先按DOCUMENTED门槛守门，再要求exchange_id是原枚举精确字符串；测试使用受控DOCUMENTED副本分别验证三分支。其他参数仍由前置绑定校验，本提供器不重复完整参数校验、31天限制或证券归属，也不因scope里有股票条件缩小已确定的全市场必要集合。

受控正例可以在测试内复制策略，将明确scope的moneyflow、stk_limit、margin、CSF或hsgt_top10标为DOCUMENTED并注入对应假来源，用来证明算法；该副本不写回生产资源，不计为新增真实适用证据。对完整市场仍未知的十个C-A及两个未知方向接口，即使测试伪改状态也必须因无映射而拒绝。

### 4. 本轮获取与全量确认

按以下固定顺序实现，不创建执行状态机或跨调用缓存：

1. 解析完整必要身份→来源键，确认每个所需来源键在sources中存在。任一个缺失，所有fetch调用为零；不能先获取可用来源并算部分并集。
2. 按来源键字典序分组调用fetch，同一来源在一次confirm内调用一次，传入该来源负责的全部必要身份。HKEX两个北向方向共用一次读取；每个fetch前后分别调用context.checkServerState。context调用在捕获来源错误的try块之外，异常实例原样传播。
3. 仅围绕fetch捕获SourceException并转换为新的CalendarUnconfirmedException，无cause及原消息。已有CalendarUnconfirmedException原样保留；不捕获Error、任意RuntimeException或服务端故障。失败后不自动重试、不调用剩余来源，不返回部分Decision。
4. 验证每次返回非null，sourceId精确匹配、有效期两端非null且有序，所有scope.dates都在有效期内，revisionsConfirmed=true，rows非null且非空；任一失败抛CalendarUnconfirmedException。没有固定“当前年”限制，跨年实际日期必须都有有效依据。
5. 每行identity非null且属于这次来源负责的必要集合，date非null，isOpen只能精确为`"0"`或`"1"`（无strip／数字／布尔／空值强转）。未知身份、非法标识均拒绝。允许来源返回较宽日期；只将scope.dates中的行加入候选map，任务外日期不进入Decision或业务计划。行的类型与身份仍须合法，不能通过任务外日期藏匿坏协议。
6. 同身份同任务日期重复且一致则去重；0／1冲突立即失败。每个必要身份最终日期键集合必须精确等于scope.dates，不能用总行数判断覆盖；任一身份缺日、仅返回其他日期或空结果都失败。
7. 全部来源成功并验证后，统一构造`new CalendarDecision(scope, calendars)`；外层身份集合必须精确等于必要集合，内层只含实际任务日期。调用context最后一次检查后返回。复用CalendarDecision.openDates计算并集，不重新写周末规则或按业务有无数据猜开市。

CalendarData的有效期／修订声明仍属于来源适配器可信边界，提供器只能检查声明与完整性，不能从任意接口响应自动证明权威。当前没有真实适配器，这个缺口以生产缺来源拒绝保留；不能把受控布尔正例当成真实更新依据。

### 5. SPI接入及后续消费

TushareProPlugin新增私有只读calendarProvider和按ApiName定位ApiDescriptor的只读map（从已构造descriptor.apis派生）。覆盖confirmCalendar：非null检查、context检查、readiness沿用现有PLUGIN_DISABLED规则；未知apiName返回固定安全IllegalArgumentException（`Unknown Tushare API`）；已知接口委托provider.confirm。不给该方法添加参数绑定、业务client.execute、任务存储、取消、线程中断检查或自动重试。

现有download方法不调用日历、不改业务参数／包络；当前旧HTTP入口继续按原路径运行。fixture本项继续默认confirmCalendar拒绝；受控日历属于本项插件测试，不为了测试增加新的fixture业务接口。T08将给19类范围构造一次本轮实际日期CalendarScope，T12／T13在任何业务调用及任何明细删除前消费完整Decision；非交易类别绕过屏障由这些编排任务验证。

CalendarDecision仅在本轮内存使用；调用者可在本轮复用同一不可变结果，不对同一轮反复调用SPI。第二次confirm调用即新的确认，必须再次fetch，不能由相同scope、taskId或同一context对象触发跨次缓存。3／7日请求只能返回3／7日判定，即便上游返回了1～10日数据，也不能生成4～6日业务计划。

### 6. 精确受控样例

统一使用实际日期集合`{2026-09-03, 2026-09-07}`；不依赖测试运行当天或系统时区。有效范围默认2026-01-01～2026-12-31且revisionsConfirmed=true，来源id严格匹配注册键。

| 样例 | 预期 |
|---|---|
| moneyflow：SSE两日0／0，SZSE两日1／0 | Decision含两个身份各两天；openDates仅3日，7日全休市 |
| moneyflow：两身份两日均0 | openDates空；已有完整非空日历，不是缺来源 |
| SSE完整，SZSE缺7日、空、身份错误、过时或修订未确认 | CALENDAR_UNCONFIRMED；无Decision、无业务／存储副作用 |
| SSE同日0／0重复或0／1重复 | 前者去重，后者整次拒绝 |
| margin三种exchange_id分别输入 | 只调用对应一个来源；缺值、小写、未知、非字符串拒绝，不回退SSE |
| hsgt_top10两北向方向绑定同源 | fetch一次，identities为两方向；第二次confirm仍再fetch一次 |
| 来源带1～10日合法额外行 | Decision仍只含3／7日；输入scope及原条件不变 |
| 注册表缺SZSE或缺BSE | 任一fetch均不发生，不先处理已有SSE |
| 来源抛SourceException(SOURCE_TIMEOUT) | 日历固定错误，原来源消息不回显，未进行自动重试 |
| context在调用前／来源后抛出受控故障实例 | 原异常原样传播；来源后故障不返回Decision |
| 当前49项生产描述符 | 19交易项均日历未确认；30非交易项显式误调用亦零辅助请求，现有业务download不因T06改变 |

## Files

Java路径以`data-plane/`为根，新文件在T06明确启动后实施：

- `tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/calendar/CalendarSource.java`：插件内来源端口及嵌套观察类型。
- 同目录`TushareCalendarProvider.java`：必要身份／来源分组、获取和全量确认、无跨次缓存。
- `tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`：confirmCalendar覆盖及只读provider／描述符索引；现有构造器和download保留。
- 对应测试`calendar/TushareCalendarProviderTest.java`：受控fetch及全量验证、构造／不可变边界；已有`TushareProPluginTest.java`：真实49项策略拒绝、readiness／context及旧业务路径回归。受控来源作为测试嵌套类型，按调用次数／参数／事件序列观察，不新增生产假数据。
- `docs/verification/RANGE-T06-calendar-confirmation.md`和`docs/traceability/tensor-range-requirements.md`：本轮验证与五项AC的提供器层证据，明确生产证据缺口。
- 不改共享SPI类型、49份Dataset、49项生产策略、HTTP／Core执行、数据库或前端。新文件加入Git暂存，不提交或发布；完成后按看板准备T07。

## Tests

按测试先行实施。首个动作写TushareCalendarProviderTest中的moneyflow两来源3／7日并集用例，使用测试内DOCUMENTED策略副本及两个受控CalendarSource，先运行确认提供器缺失，再实施最小确认逻辑。随后逐一增加拒绝／同轮复用／跨调用重新获取及SPI回归用例。

必须验证上节全部样例，另外覆盖：非null调用合同；来源注册map及CalendarData.rows防御复制；传给来源的集合和最终Decision不可改；来源返回null、null行／字段、非法开闭字符串、逆序有效期、缺市场、多重复行掩盖缺日；受控跨年范围；未知C-A完整集合及C-N／C-X方向不能通过更换状态伪启用；C-S不调用SSE源。context回调不得被转换成日历来源错误。

从仓库根执行：

```sh
mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am test
mvn -f data-plane/pom.xml test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

所有规定命令退出0。Maven沿用已授权的沙箱外环境；记录本轮实际计数、先行失败及修正，不复制T05通过数。独立核对19接口／5类别与T02 JSON、生产状态1／16／2与T04策略完全一致，49份Dataset及策略资源SHA-256不变。测试不读取凭证、不访问外网；数据库IT只编译未调度的事实、浏览器／真实来源未运行须明确披露。

## Acceptance

1. 必要身份在任何获取前确定，未知／冲突／非交易误调用／缺来源均安全拒绝；margin保留三分支，CSF及互联互通不被普通市场替代。
2. 受控来源完整获取并验证身份、任务日期集合、有效期、修订声明和重复冲突后，返回精确不可变CalendarDecision；任一开盘取并集，全部确认闭市返回空openDates，缺日／空来源不产生伪全休市。
3. 同一次确认内相同来源读取一次；第二次确认重新读取；3／7日只消费3／7日，不用原始展示区间替代，不产生任务外业务范围。
4. 来源故障安全映射、context故障原样传播、输入不变、无来源自动重试及数据库／任务写入。当前旧业务下载回归通过；实际首次／重试表与编排守门效果留在T12／T13／T18。
5. 当前生产19项仍明确未确认，唯一静态DOCUMENTED项不冒充运行可用；没有新增生产假来源或证据开关。规定验证及独立评审通过，AC-PRD-RANGE-02／15／16／17／18只记录提供器层／受控证据，真实适用及日历证据缺口继续追踪。
6. 新文件已暂存，T06完成后按Order完成并链接T07设计和交接，不自动实施T07。

## Risks

- 当前没有完整运行依据的生产日历源，T06完成也不会使19项生产休市过滤可用。后续要启用必须交付权威获取及修订核查适配器并关闭对应来源证据；不能只修改calendarEvidenceStatus或注册一个返回true的来源。
- HKEX2026静态年表、CSF规则、交易所公告分别有年份／业务／修订边界；生产注册表为空准确保留这些缺口。真实来源缺口若影响最终功能验收，不能只登记风险关闭项目。
- CalendarData修订字段是可信来源适配器的运行观察，提供器不能证明一个任意实现的声明。当前只有受控测试实现；没有对外来源注入或参数开关。
- CalendarScope保证日期非空及不可变，但不负责业务范围合法性；T08传入首次／失败项的实际日期，T12／T13须先取得整轮Decision再产生业务或存储副作用。
- 本文为T05完成后的T06实施设计，未开始T06代码；READY只表示实施合同及交接已准备。
