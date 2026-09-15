# ISSUE-018-T06：Tushare 区间策略、日期规划与参数纠正

## Goal

使 Tushare 插件实现 T01 的可选批量合同，为 34 项 RANGE 目标提供明确的参数、日期轴、规划、范围检查与完整性算法，保留全部 40 项 SINGLE。当前只有官网文档调研，没有真实 Tushare API 验证；本任务完成的是策略实现和受控测试，生产 34 项均保持 `NEEDS_VERIFICATION`，由 T13 逐项验证后开放。

身份和范围来自 [任务看板 T06](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t06)，Order 6，直接依赖已完成 T01、T05。共享依据为 [总体设计](ISSUE-018-design.md) §2.2、§3.2–3.5、§3.13、§5.1、§7，以及[母 issue 的 40 项官网表](../issues/problems/ISSUE-018-date-range-batch-downloads.md)。T05 已完成，本设计仅准备后继，不启动实现。

## Scope

新增显式策略注册表、三类规划、纯参数转换、响应日期及完整性检查；`TushareProPlugin` 实现 `BatchDownloadSupport`，所有带上下文真实请求使用 T05 客户端。仅纠正 `fina_mainbz.yaml` 的 SINGLE 参数和相应示例、精确测试预期。

不实现 worker、二分、任务状态或预算存储、HTTP 新路由、前端任务交互、迁移、自动重试、游标分页或真实 API 验收。不改变其余 39 份 SINGLE YAML，不增加 type、period、limit、offset 或 VIP 请求，不混合主营业务 P/D/I，不恢复九项已移除接口。不修改 T01 公共合同、T05 传输协议、插件配置 bean 签名或增加依赖。

## Approach

### 最小结构和固定接口

新增插件 `batch/` 包中的两个生产文件：

1. `TushareBatchPolicies.java`：`public final` 门面，拥有不可变注册表、DatasetDefinition 索引与原客户端；生成描述、转换参数、规划及判断。唯一 public 构造器为 `(TushareProClient client, List<DatasetDefinition> definitions)`。public 方法为 `Optional<BatchDownloadDescriptor> batchDescriptor(ApiName apiName)`、`List<DateRange> plan(ApiName apiName, Map<String,Object> params, BatchCallContext context)`、`Map<String,Object> sourceParameters(ApiName apiName, Map<String,Object> params, DateRange range)`、`BatchAssessment assess(ApiName apiName, DateRange range, DownloadEnvelope envelope)`。内部辅助方法、记录和测试注入均 package-private 或 private，不增加 Spring bean。
2. `TushareTradeCalendar.java`：package-private final 纯校验器；`static List<LocalDate> openDays(DateRange range, String exchange, DownloadEnvelope envelope)` 校验完整日历后返回排序开市日。日历结构校验与普通范围检查共享门面内必要的包内静态辅助方法，不复制一套客户端。

`TushareProPlugin` 直接 implements `BatchDownloadSupport`（它已 extends `DataSourcePlugin`），保留唯一公开三参数构造器，在其中创建门面。已有 `descriptor/readiness/download` 保留；新增接口五方法中的四项委托门面。`downloadBatch(ApiName, Map<String,Object>, BatchCallContext)` 与旧 download 复用 readiness 和 definition 查找，唯一取数调用为 `client.execute(definition, sourceParams, context)`。不以 RANGE availability 阻止此方法：T07 的后台 SINGLE 对全部 40 项也必须走它，且 SINGLE 不调用 assess。旧同步 download 仍用二参数 execute。

`batchDescriptor` 是纯本地查询，不检查凭证、不发上游请求。34 项返回描述；六项 SINGLE-only 及未知 API 返回 `Optional.empty()`，T03 已将 empty 合成为 UNSUPPORTED。plan/sourceParameters/assess 对无区间策略的 API 抛固定 `BATCH_DOWNLOAD_UNAVAILABLE`。旧 download/downloadBatch 的未知 API 继续为固定 `IllegalArgumentException("Unknown Tushare API")`。现有 plugin 私有 PluginUnavailableException 保持，无须扩展其职责。

### 注册表 schema 与证据门禁

门面内定义包内嵌套 `Policy` record，字段固定为：

```java
record Policy(ApiName apiName, ParameterShape parameterShape,
              DateAxis dateAxis, String dateLabel, PlanningMode planningMode,
              String outputDateColumn, String dailyParameter, boolean splittable,
              RuleKind ruleKind, Long documentedRowLimit, String documentationNote,
              String officialUrl, LocalDate documentationCheckedOn,
              String policyVersion, boolean sourceVerified, String verificationEvidence) {}
enum ParameterShape { STOCK, DATES, EXCHANGE, EXCHANGE_ID }
enum RuleKind { ROW_LIMIT, CALENDAR_COVERAGE, UNKNOWN }
```

日期、枚举均使用 T01 实际类型（DateAxis/PlanningMode 是 BatchDownloadDescriptor 的嵌套类型）。默认 `policyVersion="tushare-range-v1"` 是本次新建策略版本，不是上游版本；全部 `documentationCheckedOn=LocalDate.of(2026,9,11)` 引用母 issue 已记录的网页核对日期，不伪造本任务重新请求官网。officialUrl 为下表完整 URL；documentationNote 保存已记录的限量或限制。全部生产 `sourceVerified=false, verificationEvidence=null`。

`ROW_LIMIT` 必须有正 documentedRowLimit，其他种类此值为 null。UNKNOWN 永不允许 sourceVerified=true；已验证规则必须有非空 verificationEvidence；false 不许填已验证证据。dailyParameter 仅逐日模式有值，所有原生项为 null；逐日 splittable=false，原生行数/未知策略 splittable=true；trade_cal 的完整覆盖规则不靠行数拆分，splittable=false。集合不可变，重复 API、策略不在 40 个 definition 中、日期输出列不存在、股票形状与现有股票必填规则不一致在构造时拒绝，不在请求中才发现。构造输入违反本段不变量统一抛 `IllegalArgumentException("Invalid Tushare batch policies")`，不附原值、cause 或 suppressed。生产 keys 精确为下表 34 项。包内 `static Map<ApiName,Policy> productionPolicies()` 返回不可变原表，供同包测试复制；该方法不公开，不修改全局注册表。

**描述和执行分开表示候选依据与已验证依据。** sourceVerified=false 的描述始终为 `NEEDS_VERIFICATION`，`CompletenessRule(UNKNOWN,null,null)`，不能将已读官网数值写成已通过真实验收的 CONFIRMED_ROW_LIMIT。候选数值仍保存在上述内部记录供测试和 T13 使用。其 unavailableReason 固定表达“区间参数语义与完整性尚待真实接口验证”；11 项 UNKNOWN 额外表达“尚无可确认的完整提取依据”。sourceVerified=true 才能生成 AVAILABLE：ROW_LIMIT 映射 CONFIRMED_ROW_LIMIT，CALENDAR_COVERAGE 映射 VERIFIED_RULE；evidence 使用 verificationEvidence 并关联官方 URL，不能只写测试通过。版本在 T13 修改规则/开放状态时更新，供已有定义摘要拒绝旧语义重放。

所有生产策略的 assess 在检查结构与范围之后返回 UNKNOWN；空数据同样不能 COMPLETE。plan 对未验证策略在任何 HTTP 前拒绝 BATCH_DOWNLOAD_UNAVAILABLE。sourceParameters 不作 availability 检查，保持可测试的纯转换与 T03 本地预检功能；T03 已在调用它之前要求 AVAILABLE。

测试采用门面的 package-private 四参数构造器 `(TushareProClient, List<DatasetDefinition>, Map<ApiName,Policy>, Clock)`；public 构造器只能使用固定生产表和系统 UTC 时钟。测试在同包复制完整生产表，用明确标注 `controlled-test-only` 的证据替换选定已知规则项、sourceVerified=true，再直接测试门面。UNKNOWN 项不能通过此替换变为已验证。没有属性、环境变量、HTTP 参数、反射篡改或 public 构造器可为生产注入验证状态；测试副本不影响生产 plugin。此缝隙验证已确认规则时的控制流，不构成真实开放证据。

### 逐项策略表

形状 S=`ts_code,start_date,end_date`；D=`start_date,end_date`；E=`exchange,start_date,end_date`；I=`exchange_id,start_date,end_date`。N=NATIVE_RANGE，C=CALENDAR_DAYS，T=TRADING_DAYS。下表数值均为候选官网行数上限，与数据库 batchSize 无关。所有行当前验证标记都是 false。

| API | 形状/规划 | DateAxis / 输出列 | 候选规则与边界 | officialUrl |
| --- | --- | --- | --- | --- |
| daily | S/N | TRADE_DATE / trade_date | ROW_LIMIT 6000 | https://tushare.pro/document/2?doc_id=27 |
| weekly | S/N | TRADE_DATE / trade_date | ROW_LIMIT 6000；实际每周最后交易日 | https://tushare.pro/document/2?doc_id=144 |
| monthly | S/N | TRADE_DATE / trade_date | ROW_LIMIT 4500；实际每月最后交易日 | https://tushare.pro/document/2?doc_id=145 |
| adj_factor | S/N | TRADE_DATE / trade_date | UNKNOWN | https://tushare.pro/document/2?doc_id=28 |
| daily_basic | S/N | TRADE_DATE / trade_date | ROW_LIMIT 6000 | https://tushare.pro/document/2?doc_id=32 |
| stk_limit | S/N | TRADE_DATE / trade_date | ROW_LIMIT 5800 | https://tushare.pro/document/2?doc_id=183 |
| suspend_d | S/N | TRADE_DATE / trade_date | UNKNOWN | https://tushare.pro/document/2?doc_id=214 |
| moneyflow | S/N | TRADE_DATE / trade_date | ROW_LIMIT 6000 | https://tushare.pro/document/2?doc_id=170 |
| margin | I/N | TRADE_DATE / trade_date | ROW_LIMIT 4000；保留 exchange_id | https://tushare.pro/document/2?doc_id=58 |
| margin_detail | S/N | TRADE_DATE / trade_date | ROW_LIMIT 6000 | https://tushare.pro/document/2?doc_id=59 |
| block_trade | S/N | TRADE_DATE / trade_date | ROW_LIMIT 1000 | https://tushare.pro/document/2?doc_id=161 |
| slb_len | D/N | TRADE_DATE / trade_date | ROW_LIMIT 5000 | https://tushare.pro/document/2?doc_id=331 |
| slb_sec | S/N | TRADE_DATE / trade_date | ROW_LIMIT 5000；标停，历史可用性待验 | https://tushare.pro/document/2?doc_id=332 |
| slb_sec_detail | S/N | TRADE_DATE / trade_date | ROW_LIMIT 5000；标停，历史可用性待验 | https://tushare.pro/document/2?doc_id=333 |
| trade_cal | E/N | CALENDAR_DATE / cal_date | CALENDAR_COVERAGE；无猜测行数上限 | https://tushare.pro/document/2?doc_id=26 |
| new_share | D/N | ISSUE_DATE / ipo_date | ROW_LIMIT 2000；不是 issue_date 上市日期 | https://tushare.pro/document/2?doc_id=123 |
| income | S/N | ANNOUNCEMENT_DATE / ann_date | UNKNOWN | https://tushare.pro/document/2?doc_id=33 |
| balancesheet | S/N | ANNOUNCEMENT_DATE / ann_date | UNKNOWN | https://tushare.pro/document/2?doc_id=36 |
| cashflow | S/N | ANNOUNCEMENT_DATE / ann_date | UNKNOWN；不是 f_ann_date | https://tushare.pro/document/2?doc_id=44 |
| fina_audit | S/N | ANNOUNCEMENT_DATE / ann_date | UNKNOWN | https://tushare.pro/document/2?doc_id=80 |
| forecast | S/N | ANNOUNCEMENT_DATE / ann_date | ROW_LIMIT 3500 | https://tushare.pro/document/2?doc_id=45 |
| express | S/N | ANNOUNCEMENT_DATE / ann_date | UNKNOWN | https://tushare.pro/document/2?doc_id=46 |
| repurchase | D/N | ANNOUNCEMENT_DATE / ann_date | UNKNOWN；默认 2000 不是区间硬上限 | https://tushare.pro/document/2?doc_id=124 |
| stk_managers | S/N | ANNOUNCEMENT_DATE / ann_date | UNKNOWN | https://tushare.pro/document/2?doc_id=193 |
| stk_holdernumber | S/N | ANNOUNCEMENT_DATE / ann_date | ROW_LIMIT 3000；不是 enddate/end_date | https://tushare.pro/document/2?doc_id=166 |
| stk_holdertrade | S/N | ANNOUNCEMENT_DATE / ann_date | ROW_LIMIT 3000；不是持股变动起止日 | https://tushare.pro/document/2?doc_id=175 |
| pledge_detail | S/N | ANNOUNCEMENT_DATE / ann_date | ROW_LIMIT 1000；不是质押 start_date/end_date | https://tushare.pro/document/2?doc_id=111 |
| fina_indicator | S/N | REPORT_PERIOD / end_date | ROW_LIMIT 100 | https://tushare.pro/document/2?doc_id=79 |
| fina_mainbz | S/N | REPORT_PERIOD / end_date | ROW_LIMIT 100；不传 type/period/ann_date | https://tushare.pro/document/2?doc_id=81 |
| top10_holders | S/N | REPORT_PERIOD / end_date | UNKNOWN | https://tushare.pro/document/2?doc_id=61 |
| top10_floatholders | S/N | REPORT_PERIOD / end_date | UNKNOWN | https://tushare.pro/document/2?doc_id=62 |
| top_list | S/T | TRADE_DATE / trade_date | ROW_LIMIT 10000；dailyParameter=trade_date | https://tushare.pro/document/2?doc_id=106 |
| dividend | S/C | ANNOUNCEMENT_DATE / ann_date | ROW_LIMIT 2000；dailyParameter=ann_date | https://tushare.pro/document/2?doc_id=103 |
| disclosure_date | S/C | ANNOUNCEMENT_DATE / ann_date | ROW_LIMIT 6000；dailyParameter=ann_date，最新披露公告日 | https://tushare.pro/document/2?doc_id=162 |

原生 31=19 数值+1 日历+11 UNKNOWN；逐日 3 均有候选数值。RANGE 股票 29、非股票 5。六项只有 SINGLE：`pledge_stat`（doc_id=110）、`stk_rewards`（194）、`stock_basic`（25）、`stock_company`（112）、`index_classify`（181）、`index_member_all`（335）；URL 均使用同一官方 `https://tushare.pro/document/2?doc_id=` 前缀。前两项的单截止日/报告期不能推成区间；后四项无日期输入。六项中除 index_classify 外均沿用必填股票，因此全部 40 项仍为股票 34、非股票 6。

### 描述和纯参数映射

`ParameterDescriptor` 使用现有九字段构造器。所有参数 required=true、defaultValue=null、description=null、pattern=null；非枚举 allowedValues=[]。S 的股票参数为 TS_CODE/“股票代码”，E/I 为 ENUM/“交易所”，allowedValues 沿用 `SSE,SZSE,BSE` 以匹配已完成 T01 的唯一 Codec（不能缩小枚举导致 ParameterShape 不匹配）。两端均 DATE_RANGE_MEMBER、相互 related，顺序 start 后 end。dateLabel 固定为 TRADE_DATE“交易日期”、ANNOUNCEMENT_DATE“公告日期”、REPORT_PERIOD“报告期”、CALENDAR_DATE“日历日期”、ISSUE_DATE“上网发行日期”；disclosure_date 单独“最新披露公告日”。端点 label 为对应 dateLabel + “开始日期/结束日期”（已有“日期”的标签移除尾部“日期”再拼接，避免重复）。

sourceParameters 校验输入 keys 恰好为策略形状，值均为非空规范化字符串，股票满足现有大写单值 TS_CODE 语法，不自行扩展为列表；日期先限定 `[0-9]{8}` 再严格解析 uuuuMMdd，拒绝后缀/非法闰日/缺端点/逆序。range 必须位于用户闭区间内。错误固定 PARAM_INVALID，不含原值或 cause。原生返回原范围中的股票/交易所条件，加本片段 start_date/end_date；没有旧 trade_date/ann_date。逐日返回 `ts_code` 和 dailyParameter=`range.start`，没有起止参数。返回 Map.copyOf，不修改输入，无时钟、预算、HTTP、凭证或上次调用状态。

**已有 T03 预检兼容：** DownloadTaskService.normalize 会以整个用户区间调用 sourceParameters，包含逐日模式。故转换对逐日非单点 range 也允许并只生成首日参数，这是纯预检探针；不能要求 start=end 而使合法多日任务被拒绝。实际执行仅消费 plan 的单点区间；assess 对逐日非单点 range 拒绝，T07 也核对计划。测试必须覆盖此多日预检，不能修改 T03 为调用上游或擅自将逐日降为仅首日。

top_list 股票后缀 SH→SSE、SZ→SZSE；BJ 和其他不能映射后缀在 sourceParameters/plan 的本地校验中拒绝 BATCH_DOWNLOAD_UNAVAILABLE，未验证前不借沪深日历。trade_cal RANGE exchange=BSE 同样本地拒绝，保持用户条件不替换；SINGLE 的 BSE 行为不变。margin 的 BSE 不因日历限制被扩大禁止，它保留原枚举且本身仍待验证。

### 规划与交易日历

plan 先校验参数、context/deadline 非 null、能力已验证和本地条件；停止或线程中断抛 EXECUTION_INTERRUPTED，now>=deadline 抛 TASK_LIMIT_EXCEEDED。本地循环检查控制条件但不调用 beforeRequest；预约完全由 client 执行。Clock 仅用于这些纯 CPU 检查，单调节流和 I/O 控制继续由 T05 拥有。

NATIVE_RANGE 返回恰好一个用户完整闭区间，不先按周/月/季度切片。CALENDAR_DAYS 按 LocalDate 自然日逐日生成单点范围，排序、含两端、跨月跨年闰日不遗漏，公告周末也请求。不依赖 core 的 DateRangePlanner，不引入插件到 core 依赖。枚举采用比较到 end 后停止的循环，避免 end=LocalDate.MAX 后 plusDays 溢出；业务 max-range-days 和 max-batch-nodes 仍由 T03/T07 控制。

TRADING_DAYS：本地映射股票后，使用 trade_cal 的已有 definition，参数恰好为 `exchange,start_date,end_date`，**不传 is_open=1**；先要求注册表中的 trade_cal 策略也有 sourceVerified=true，再经 `client.execute(calendarDefinition, calendarParams, context)` 恰好一次取整段日历。top_list 后续每个实际逐日请求经 plugin.downloadBatch 的同一客户端和同一个本轮 context。规划调用、原生调用、逐日调用、后台 SINGLE 均不得改走二参数 execute、自己多预约一次、另建 client 或自动重发。日历超过响应大小/时间/请求预算沿 T05 原分类失败。

`TushareTradeCalendar.openDays` 验证顺序：

1. SUCCESS、pluginId=tushare_pro、apiName=trade_cal，params 精确等于请求 exchange 和闭区间；fields 精确等于 definition 的 `exchange,cal_date,is_open,pretrade_date`（普通 assess 使用所选 definition 的列顺序）。rowCount=data.size，不能丢字段或接受失败 envelope。
2. 每行 exchange 是请求值；cal_date 是严格八位合法日期，且属于闭区间；日期唯一，重复即失败，不使用 Set 静默去重。is_open 必须表示 0 或 1：接受精确字符串 "0"/"1" 及 JSON 整数/BigDecimal 可无损转整数的 0/1；拒绝 null、布尔、其他数字、小数非整数及空白字符串。pretrade_date 不作为范围列，不要求在本次范围内。
3. 日期集合必须等于范围内全部自然日，每天恰好一条；空响应、少一天、只返开市日均失败。乱序合法，验证后排序。不以总行数相等代替逐日覆盖，不能让重复+缺日互相抵消。
4. 完整验证后筛 is_open=1，返回不可变升序列表。全休市返回空列表是合法规划；周五为休市就不请求 top_list，不使用 weekday 推断，也不强加“周末必须休市”。

trade_cal 自身 RANGE assess 先做通用 envelope、行结构、日期范围、exchange 和 is_open 合法性检查。策略未验证时随即返回 UNKNOWN，包括空响应；此时不执行日期唯一性及完整自然日覆盖判定。策略已验证时才复用 openDays 的完整校验，通过才 COMPLETE；不猜测最大行数，不用缺日触发行数拆分。top_list 规划始终要求已验证日历并执行全部四步，空日历失败与完整日历全休市的合法空计划必须区分。用于 top_list 的日历只作规划证据，不适配入库、不贡献证券 source_rows；计划持久化与重试复用日期属于 T07，T06 不缓存日历改变后续重试计划。

### 响应检查、完整性和错误

assess 无 I/O。先确认 envelope 的 source/API/status/字段顺序对应所选 definition，并从 envelope.params 验证本片段参数形状和范围端点；原生 start/end 必须等于 range，逐日必须为单点且日参数等于 range.start。由于接口没有额外 params 实参，原始用户股票快照与 envelope.params 的相等性由 T07 核对，门面再用 envelope.params 的股票/交易所验证每行，不声称能从不存在的输入证明股票身份。

股票行应与 params.ts_code 严格相等；margin 同时验证 exchange_id，trade_cal 验证 exchange。非股票 new_share/repurchase 不限制响应股票、不按 ts_code 去重。所有行按策略 outputDateColumn 验证：null、非字符串、非法日期失败，合法但越界失败；不能过滤越界行后认定完整，不能使用另一个日期列替代。报告期内 end_date + 区间外 ann_date 合法；反例仍拒绝。stk_holdernumber 的有效带时间公告值由现有 TushareResponseValidator 已归一化，门面不再添加宽松解析；无效时间仍在此失败。

范围检查完成后：

- 未验证/UNKNOWN → UNKNOWN，含空响应；不返回假成功。
- 已验证 ROW_LIMIT → 原始 envelope.rowCount `<L` 为 COMPLETE，`=L` 和 `>L` 均 SPLIT_REQUIRED，不先去重或过滤。即使单日/不可拆分仍返回 SPLIT_REQUIRED，由 T07 按合同转 BATCH_COMPLETENESS_UNCONFIRMED；门面不执行拆分、不重试、不适配父响应。
- 已验证 CALENDAR_COVERAGE → 完整自然日校验通过才 COMPLETE；全休市日历行可完整，空日历不完整。

固定错误分类：结构/非法字段/非法日期/非法 is_open 为 SOURCE_PAYLOAD_INVALID；合法日期越界、股票或交易所不匹配为 SOURCE_RANGE_MISMATCH；日历重复或缺自然日为 BATCH_COMPLETENESS_UNCONFIRMED。真实客户端原有股票混入检查先抛 SOURCE_PAYLOAD_INVALID，保持现有同步分类；直接调用 assess 的混股检查用 SOURCE_RANGE_MISMATCH，不为统一名字修改 T05 validator。身份/参数快照不符归 SOURCE_RANGE_MISMATCH；无区间策略或未验证门禁为 BATCH_DOWNLOAD_UNAVAILABLE。SourceException 只接受 SOURCE_*，其余使用门面 private static final TensorException 子类，消息由固定映射产生：PARAM_INVALID="Invalid Tushare range parameters"，BATCH_DOWNLOAD_UNAVAILABLE="Tushare range download is unavailable"，BATCH_COMPLETENESS_UNCONFIRMED="Tushare calendar coverage is unconfirmed"，SOURCE_PAYLOAD_INVALID="Invalid Tushare range response"，SOURCE_RANGE_MISMATCH="Tushare response does not match requested range"；控制错误沿用T05的固定消息。无原 cause/suppressed/原始参数/响应。已分类 T05 预算、停止、权限、限流、网络错误原样传播，不捕获后变成 UNKNOWN 或空计划。

### fina_mainbz 的唯一 SINGLE 合同纠正

`queryMode: ann_date` 改 `snapshot`，parameters 只保留必填 ts_code。列、业务键 `[ts_code,end_date,bz_item,curr_type]`、过滤器和表不变。旧 `ts_code+ann_date` 同步 HTTP 请求因未知参数返回 PARAM_INVALID/400，零上游/零写入；`ts_code` 请求合法，实际 source params 中没有 ann_date/type。RANGE 参数独立为 S/N 报告期，未传 type 的真实默认范围留待 T13，不自动多次请求 P/D/I。

## Files

新增生产文件：

- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`。
- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareTradeCalendar.java`。

修改 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`、`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/fina_mainbz.yaml`、`docs/contracts/download-request-examples.json`。

新增测试 `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/{TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest}.java` 和 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java`（真实生产插件组合 T03 的本地能力/接收测试，仓储 mock，零数据库/HTTP）；package-private test helper 仅在确有复用需要时留同一测试文件，不新建测试框架。

更新以下现有精确断言，保留原数量/完整枚举/业务键约束：

- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java`：直接接口改 BatchDownloadSupport；公开方法精确为原三项+五项批量方法，公开构造器仍唯一原签名，配置 bean 与原私有 unavailable 类型断言保持。增加真实 plugin 批量门禁和后台 SINGLE 上下文委托测试。
- 同模块 `metadata/TushareMetadataContractTest.java`：fina_mainbz 从 ts_code+ann_date 组移入 ts_code 组，显式断言 snapshot，40 项、789 声明业务列/912 实际列/34 非主索引及业务键不变。
- 同模块 `client/TushareProClientTest.java`：expectedRequestParameters 中 fina_mainbz 移到股票单值请求组，旧客户端全 40 项回归保留。
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/download/DownloadParameterResolverTest.java`：fina_mainbz 绑定 TsCodeParameters；新增生产 34 RANGE 描述对四类 Codec 的实际匹配，不使用手造近似描述替代。
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadRequestBindingTest.java`：新增旧 ann_date 400、合法股票单值 200、未知 type/period/起止参数的 SINGLE 拒绝；保留全 40 示例回归。
- 同 app `StockScopedDownloadTest.java`：fina_mainbz 仅股票合法请求及混股拒绝，34/6 约束不变，零间隔受控 client 配置继续保留。
- `control-plane/e2e/tushare-metadata.spec.js`、`control-plane/e2e/ui-redesign.fixtures.js`：fina_mainbz tuple 改 snapshot + `[ts_code]`；`control-plane/e2e/tushare-live.spec.js` 同条请求参数改 `[ts_code]`。这些是旧元数据事实同步，不引入任务界面或假 live 证据。tushare-metadata.spec.js 的 REQUESTS_SHA 必须按修改后的 download-request-examples.json 实际字节重新计算并固定，保留 MANIFEST_SHA、40项和完整校验；不删除 hash 检查或动态计算 expected 来规避它。

不编辑数据库迁移、数据模板字段、contract schema 或发布脚本中不受影响的断言；实现时用 rg 核对剩余 fina_mainbz 引用，历史 issue 的原始调研表不改写成当前完成事实。创建的文件加入 Git，不自动提交。

## Tests

首个实现动作：新增 TushareBatchPoliciesTest，独立列出上述 34 行与 6 SINGLE-only 预期，断言所有生产描述 NEEDS_VERIFICATION/UNKNOWN、原生31+逐日3、29/5形状和官方来源；先观察缺失策略类失败，再实现最小注册表。不得在测试中仅用生产表派生预期而漏验错项。

必须覆盖以下行为矩阵：

| 测试层 | 场景 | 预期 |
| --- | --- | --- |
| 注册/表面 | 每项的 URL/日期/版本/flag、形状、日期列、限量；重复/遗漏/已移除九项；六项 SINGLE-only | 34 正确、6 empty、UNKNOWN11 不可验证，所有生产 AVAILABLE 数量0 |
| 描述绑定 | 实际34描述、四类 Codec、标签、SINGLE不混入端点、枚举BSE仍匹配 | 唯一绑定；fina_mainbz SINGLE仅股票，RANGE报告期 |
| 纯转换 | 全34、首末/内部子片段、输入不可变、重复调用一致、额外/缺参数/空值/非字符串/无效闰日/尾缀/逆序/子范围越界 | 正确最小 source params 或固定分类，零上游/零预约 |
| T03 兼容 | top_list/dividend/disclosure_date 以多日用户区间做 sourceParameters 预检 | 返回首日探针；plan 实际覆盖每个所需日，不只首日 |
| 门禁 | 生产34 plan、现有T03对生产插件RANGE接收；凭证配置也不开放 | BATCH_DOWNLOAD_UNAVAILABLE、零上游；正常SINGLE不受阻 |
| 原生/自然日 | 同日、闰年2月、跨月跨年、周末公告；weekly周五休市、monthly非自然月末返回日 | 原生一个完整范围，公告自然日全覆盖；不强制星期/季度边界 |
| 交易日历 | SH/SZ、乱序、全休市、周五休市；缺日/重复/重复替缺日/越界/错exchange/错身份/缺字段/非法日期/is_open | 完整验证后才排序取开市日；错误全规划失败，不返回半计划 |
| 日历门禁 | BJ/BSE/其他未知股票后缀、trade_cal未验证而top_list测试策略已验证 | 本地拒绝且零HTTP，不替换交易所；SINGLE BSE保留 |
| 日期轴 | new_share ipo_date vs issue_date；cashflow ann_date vs f_ann_date；pledge_detail业务起止；stk_holdernumber截止日/正常时间归一化；4项报告期ann_date在外 | 只规定日期轴决定范围，不过滤、不误拒合法报告期 |
| 完整性 | 全22候选数值的 `<L/=L/>L/0`，已验证副本；单点满额；11 UNKNOWN和其余未验证生产项空/非空；未验证trade_cal空/缺日/重复与非法日期/错exchange | COMPLETE/SPLIT_REQUIRED精确，生产全部UNKNOWN；未验证日历先拒绝结构/范围错误，再返回UNKNOWN，不提前执行完整覆盖判定 |
| 结构/归属 | 所有29股票策略混股，margin错exchange_id，非股票多股票，字段顺序/日期null/非法日期/失败envelope | 分类正确；合法非股票结果不被额外限制；范围检查先于UNKNOWN |
| 上下文链路 | 使用真实T05 client+MockRestServiceServer/WireMock：top_list计划一次日历+N逐日，原生一次，后台SINGLE含六项一次 | 每个HTTP预约一次、实际body最小params、同context、无retry；规划响应不传给证券适配 |
| 控制/错误 | 日历预约拒绝/停止/到期/权限/限流/网络失败、每日计划循环停止；已分类异常含固定消息 | 零后续请求，无假空成功，T05分类原样，cause/suppressed无秘密 |
| 旧HTTP | fina_mainbz单股票合法，旧ann_date/额外type拒绝，34混股/6非股票及40示例 | 合法200；非法400、零上游/写入，其他SINGLE不变 |

门面单元测试注入 Clock；传输测试显式 `minRequestInterval=Duration.ZERO`，共享节流本身继续由 T05 的 Gate/Control 测试证明，不用长 sleep 或真实 token。已验证副本测试同时断言新建生产 plugin 仍不可用；不能为了跑通测试将注册表任何一项改为已验证。T03 的生产门禁由 app 新增 `TushareBatchAvailabilityTest.java` 承载：真实生产插件与适配器注册，mock 仓储，直接组合 DownloadTaskService，断言所有34 RANGE拒绝且仓储无插入/客户端无请求；SINGLE仍可本地接收。禁止加 core→tushare 生产依赖。

从仓库根运行，Java21，构建内 Node24.15.0/npm11.12.1：

```sh
mvn -f data-plane/pom.xml -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest,TushareProPluginTest,TushareMetadataContractTest,TushareProClientTest,TushareProClientControlTest,TushareRequestGateTest,DownloadParameterResolverTest,DownloadRequestBindingTest,StockScopedDownloadTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

预期四条均退出0，所选新增类实际运行，无失败/错误/跳过；Maven 生命周期前端测试及生产构建通过。T06 不改变数据库，不新要求容器事务测试；clean verify 不是所有 *IT 证明，不冒充 T12。修改浏览器元数据 fixture 后另执行项目常规受控页面回归，不调用真实 API。上述 Maven 构建已生成 control-plane/dist 并安装 Node24/npm；从仓库根在一个终端启动页面服务，保留其运行：

```sh
PATH="$PWD/data-plane/tensor-app/target/frontend/node:$PATH" npm --prefix control-plane run preview -- --host 127.0.0.1 --port 8080 --strictPort
```

确认服务就绪于 http://127.0.0.1:8080 后，在另一个终端从仓库根运行：

```sh
PATH="$PWD/data-plane/tensor-app/target/frontend/node:$PATH" npm --prefix control-plane run test:e2e -- e2e/stock-download-parameters.spec.js
```

预期所选 Chromium 用例全部通过；若缺少浏览器，先用同一 Node24 PATH 执行 `npm --prefix control-plane exec -- playwright install chromium`。测试结束后停止本次 preview 服务。若端口已由本工作区同一构建的页面服务占用，可复用该服务；不终止未知进程。

tushare-metadata.spec.js 是会自行启动验收JAR的专用MySQL集成 harness，要求 ACCEPTANCE_JAR、ISSUE_017_ACCEPTANCE_JAR_SHA256 和 TENSOR_DB_*，不是上述页面mock测试；本项同步其 tuple/固定请求hash，并由后续T12按已有完整harness运行，不将未运行它写成通过。只维护 tushare-live.spec.js 的正确输入，本项不执行真实账户验收。`git diff --check` 通过；核对除 fina_mainbz 外其余39 YAML原字节不变，40注册和schema表数不变。依赖main/干净HEAD的 `scripts/verify-contracts.sh` 仍留既有发布流程，不绕过条件。

## Acceptance

1. 生产 Tushare 实现完整可选合同，原有 SINGLE 和40数据集保留；明确31原生+3逐日、6仅SINGLE、RANGE29股票+5非股票、全部34股票+6非股票。注册来源为已记录官网文档，无虚构实时核验。
2. 所有34生产描述 NEEDS_VERIFICATION、完整性UNKNOWN，真实可用数为0；11未知依据单列且没有自动化测试即开放的开关。受控已验证规则的算法可测试，但不伪装成T13完成。
3. 规划和参数遵循日期轴；完整自然日日历在取开市日之前验证，BJ/BSE拒绝无替换；实际取数每次经过同一个T05客户端/context，不双预约或自动重试。
4. 响应范围/结构检查先于完整性；越界不被过滤，数值阈值包含等号，未知/最小片段满额无法冒充完整；不提前实现worker拆分或入库。
5. fina_mainbz SINGLE修正为ts_code+snapshot，非法ann_date400且无上游；报告期RANGE不混type。精确表面断言与旧示例同步，四条Maven及受影响受控浏览器回归有实际证据，新文件加入Git。
6. 完成事实写T06看板后，按既定Order准备T07专属设计与交接；该工作流不属于本次设计写作的实现授权，不把后继准备写成T07已实施。

## Risks

- 没有真实Tushare验证证据，这是明确的T13输入缺口而不是可用测试补齐的事实。19原生数值+3逐日数值也必须核验真实参数/边界/截断语义；11 UNKNOWN尚缺完整提取依据，不能称全部完整支持。
- 日历北交所映射/直接BSE、slb_sec及slb_sec_detail历史可用范围、fina_mainbz不传type的默认范围、disclosure_date最新版本语义保持待验。代码严格拒绝未确认条件，不猜历史范围或默认类型。
- 所有生产RANGE在本项完成后仍不可接收，这符合门禁；T07/T12可消费测试插件与本项受控策略算法，T13才逐项修改生产开放证据和版本。
- sourceParameters的逐日全区间首日探针是既有T03纯预检所需兼容，必须和plan的完整单日序列/assess单点约束一起测试，防止后继误把预检结果当实际完整计划。
- 跨批上游可能修订数据；规则验证针对每批完整取完，不承诺跨批同一时刻快照。固定响应大小、资源预算和版本摘要继续由T05/T03/T07边界执行。
