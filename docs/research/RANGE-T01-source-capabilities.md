# RANGE-T01：49 项请求、完整性与恢复能力核实

核实日期：2026-09-08。任务及范围以 [区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md) 的 `RANGE-T01` 为准，执行合同见 [本任务设计](../task-designs/RANGE-T01-design.md)。依据 [PRD v1.4](../design/Tensor_区间下载_PRD_v1.0.md) §3／§6／§9.2／§10／附录和 [TRD v1.10](../design/Tensor_区间下载_TRD_v1.0.md) §4.4／§5.2／§13.2。

## 结论与证据边界

49 项已逐项登记：19 个交易日期、15 个公告日期、1 个月份、3 个原生范围、11 个原条件。45 项有可读官方参数表，4 项原页面正文为“404, 文档不存在！”；这不是 API 停用的实测。8 项缺少当前条件下的合法请求依据（4 项缺页、`express/fina_mainbz/top10_holders/top10_floatholders`），另有 `forecast` 参数表与单股限制正文冲突。其余 40 项仅有文档层面的请求候选，**40 不代表本轮可执行或真实通过数量**。

所有接口的分页结束合同及独立恢复均未由本轮真实调用验证。49 项恢复登记暂为 `REQUEST`；只有整体取全已确认时才可处理该完整单元。独立恢复候选用于后续核实，不能作为已经启用 STOCK_TIME 的依据。`stock_basic` 有“一次可拉取完”的公开保证，`trade_cal` 可以按已选交易所的完整自然日期集合检查覆盖；其他接口各自的取全缺口见逐行表，不能统一采用“少于上限即完整”。

本轮重新核读 [既有官方调研](2026-09-08-tushare-range-batch-research.md) 的同日缓存，对 49 份原 HTML 重新计算 SHA-256，全部与原 `evidence.json` 匹配；没有重新下载网页、读取凭证或调用业务 API。版本控制内的 [逐项证据 JSON](RANGE-T01-source-evidence.json) 保存原 URL、HTML 摘要、公开介绍、输入参数表、相关输出字段、请求示例、本地参数／业务键及 YAML 摘要。临时缓存不再是这些结论的唯一载体。JSON 中 `source` 是公开摘录，`request/completeness/recovery` 是本任务判断，二者不能混当来源承诺。

JSON 的 `mode` 是产品目标；`request.candidateMode` 是候选来源方式；`request.legality` 是当前条件下的证据状态。`DOCUMENTED_CANDIDATE` 不等于启用，`CONFLICT/UNCONFIRMED` 必须先关闭合法性缺口。`date.liveVerified=false`、`independentRecoveryVerified=false` 和 `live.status` 分别记录日期实测、独立恢复与本轮真实调用状态。没有用既有 40 项单日验收替代新区间结论。

## 统一消费规则

| 门槛 | 启用条件与拒绝边界 | 后续消费者 |
|---|---|---|
| Q 请求合法性 | 同一 apiName、正确日期语义、全部原条件及官方必要条件可同时满足。缺页、缺必要股票或正文矛盾未关闭时不得发起猜测业务请求。错误码由 T03 固定，本研究不新增 HTTP 合同。 | T03／T04／T05 |
| C 整体取全 | 有适用于当前条件的来源结束／全集依据；整体字段、行宽、对象、时间归属通过。单日、短响应、200、重复调用一致和“总量不限制”都不单独证明完整。已知截断为 SOURCE_TRUNCATED，不能确认则 SOURCE_COMPLETENESS_UNCONFIRMED；均不提交部分响应。达到公开上限但没有取全证明也不能成功，不能一概当作已知丢失。 | T04／T07／T09 |
| P 分页／分段 | 45 项可读输入表均未列 `limit/offset`；4 项无表。未公开不代表绝不支持，但未核实不得发送。按日期／股票“循环”不证明 offset 合法；先证明参数、页边界、稳定遍历和结束条件。后页失败、游标重复、总数矛盾阻止整批输出，不从失败页续传。 | T04／T07／T08 |
| R 独立恢复 | 同时证明对象、时间、整体／单元完整性及同 API 精确单独重试；先整体校验再拆分。只看到 ts_code 不足以启用；没有可靠成员全集时整体失败保存 REQUEST，不能从部分响应猜失败股票。REQUEST 同样须过 Q／C，并保持公共条件和失败时间，不拆小、不换接口、不包含已提交单元。 | T04／T09／T13 |
| K 日历 | 只对 19 项交易日期使用 T02 核实的适用市场／业务日历；任一必要来源未确认则 CALENDAR_UNCONFIRMED，业务调用为零。非交易日期 30 项不套此屏障，原生 trade_cal 保留休市行。 | T02／T06 |
| V 真实证据 | 本轮业务 API 调用为 0。T20 按完成的设计选择有权限代表样例；已启用的策略必须有相应来源证据。ISSUE-008 的 9 项不列入调用，不关闭问题。 | T20／T21 |

Q／C／R 是独立判断：缺少合法方式先不可执行；合法但取全未知不能提交；整体取全成立而独立恢复未知才可按 REQUEST 完整事务处理。该规则不要求猜满“支持”清单，也不允许用拒绝边界冒充最终功能 AC 通过。

现有 `TushareProClient.execute` 只构造原参数请求并发送一次 POST，再调用响应校验；本轮读取了该代码，没有来源分页遍历的实现证据。官方 [HTTP 说明](https://tushare.pro/document/1?doc_id=130) 只给出 `code/msg/data.fields/data.items`，未提供通用于这 49 项的 total／cursor 结束保证；[SDK 说明](https://tushare.pro/document/1?doc_id=131) 与 [FAQ](https://tushare.pro/document/1?doc_id=122) 也不补足逐接口合同。[获取指南](https://tushare.pro/document/1?doc_id=230) 的全市场按日期循环建议可用于选择候选，不能替代取全证明；其中自动重试建议不进入本项目。

## 逐接口证据与策略条件

下表所有当前输入均以 YAML 为准且必填；`margin.exchange_id`、`trade_cal/stock_company.exchange` 保留 SSE／SZSE／BSE，`hs_const.hs_type` 保留 SH／SZ，`stock_basic.list_status` 保留 L／P／D。来源参数表中的可选性不降低本地必填要求。`{}` 继续表示无用户参数。表中每项同时受 Q／C／P／R／V 约束，交易日期项另受 K 约束；上限是来源说明，未明示不是无限。

`DATE` 使用该接口原 `trade_date` 或 `ann_date`；`RANGE` 使用经确认语义的 `start_date/end_date` 并保留原附加条件；`MONTH` 按完整年月；`NONE` 保留原请求；`UNCONFIRMED` 无可启用转换。RANGE 候选仍需当前条件和取全证据，不能绕过单日候选或自行扩大范围。标记 † 的 9 项真实调用继续“不依赖，未解决，用户后续单独处理”。每项完整参数公式、对象时间字段原文及精确 REQUEST 重建约束见 JSON 对应 `apiName`。

### 交易日期（19）

| 接口／官方来源 | 当前输入 | 候选来源方式／合法性 | 上限或默认值／取全缺口 | 对象时间与独立重试依据 |
|---|---|---|---|---|
| [adj_factor](https://tushare.pro/document/2?doc_id=28) | `trade_date` | `DATE`：单日 trade_date 全市场有正文与示例；单股历史不证明无股票区间合法 | **未明示**；正文明确单日全部股票，仍须核实当日覆盖和来源是否存在未述截断 | ts_code+trade_date；需证明该组合与全市场当日中该股数据相等；当前 `REQUEST` |
| [block_trade](https://tushare.pro/document/2?doc_id=161) | `trade_date` | `RANGE`：股票代码与日期至少一个；区间与单日均列入参数表 | **1000**；需确认无股票区间接受及多笔成交取全；单日也可能超过1000 | ts_code+相同起止范围或 trade_date；同股多笔买卖必须整体取全；当前 `REQUEST` |
| [daily](https://tushare.pro/document/2?doc_id=27) | `trade_date` | `DATE`：官方明确 trade_date 全市场并建议按日期循环；区间示例携带股票 | **6000**；单日6000边界及缺股原因待核实；停牌无记录合法，不能用当前上市数当历史应有行数 | ts_code+trade_date 或相同两端；与已证明完整的全市场对应集合比较；当前 `REQUEST` |
| [daily_basic](https://tushare.pro/document/2?doc_id=32) | `trade_date` | `DATE`：ts_code 标Y但描述与 trade_date 二选一；正文支持按日线循环 | **6000**；按日不豁免6000上限；无股票仅区间不满足已确认二选一条件 | ts_code+trade_date；估值字段与完整响应对应记录比较；当前 `REQUEST` |
| [hk_hold](https://tushare.pro/document/2?doc_id=188) † | `trade_date` | `UNCONFIRMED`：旧官方页正文404；当前 trade_date 合同未得到本轮公开依据 | **无法确认**；对象 code／exchange 及全市场范围、取全方式均未确认 | 不得猜 ts_code；code 不能自动映射 STOCK；ISSUE-008 排除；当前 `REQUEST` |
| [hsgt_top10](https://tushare.pro/document/2?doc_id=48) † | `trade_date` | `DATE`：ts_code／trade_date 二选一；实际覆盖沪股通和深股通 | **未明示**；不能机械要求每天每市场10条；需核实两方向实际结果、空数据及取全 | ts_code+trade_date；同股 market_type 全部保留；ISSUE-008 排除；当前 `REQUEST` |
| [margin](https://tushare.pro/document/2?doc_id=58) | `exchange_id`、`trade_date` | `RANGE`：保留 exchange_id；官方输入输出均列 SSE／SZSE／BSE；可按日期循环 | **4000**；31天与交易所维度给出小规模候选，但缺行、更新延迟及完整结束依据须确认 | 非股票汇总；REQUEST 保留 exchange_id 与整个时间范围；当前 `REQUEST` |
| [margin_detail](https://tushare.pro/document/2?doc_id=59) | `trade_date` | `RANGE`：日期和可选股票、起止参数公开；正文沪深两市而更新提示提到北交所 | **6000**；全市场单日及多日6000截断，实际市场覆盖不能按提示猜测 | ts_code+同范围或 trade_date；单股范围取全与市场覆盖需核实；当前 `REQUEST` |
| [moneyflow](https://tushare.pro/document/2?doc_id=170) | `trade_date` | `RANGE`：股票或时间至少一个；正文为沪深A股，当前无股票输入 | **6000**；无股票区间接受及6000截断需验证；总量不限制不提供结束合同 | ts_code+同范围或 trade_date；逐股单元不能由缺少记录推导；当前 `REQUEST` |
| [moneyflow_hsgt](https://tushare.pro/document/2?doc_id=47) † | `trade_date` | `UNCONFIRMED`：旧官方页正文404；不能借同名资金流新接口替换 | **无法确认**；方向、日期、原参数及全部方向取全均未确认 | 本地仅 trade_date 键；REQUEST；ISSUE-008 排除；当前 `REQUEST` |
| [monthly](https://tushare.pro/document/2?doc_id=145) | `trade_date` | `DATE`：trade_date 为每月最后交易日期；与 ts_code 任选一 | **4500**；单月全市场也可能触及4500；不可缩到单日便宣称完整 | ts_code+trade_date；保持月线粒度，不扩整月或构造逐日记录；当前 `REQUEST` |
| [slb_len](https://tushare.pro/document/2?doc_id=331) | `trade_date` | `RANGE`：官方示例仅 start_date/end_date；交易日期范围有公开依据 | **5000**；循环获取历史未给单段结束合同；ob 是余额而非股票标识 | 无股票参数及对象；REQUEST 保留完整范围，不用 ob 作 STOCK；当前 `REQUEST` |
| [slb_sec](https://tushare.pro/document/2?doc_id=332) | `trade_date` | `RANGE`：可选股票与日期区间公开；既有调研记录导航标停 | **5000**；5000截断、历史可用性及停止更新后的合法空须核实 | ts_code+同范围或 trade_date；需确认停止更新影响与单元完整性；当前 `REQUEST` |
| [slb_sec_detail](https://tushare.pro/document/2?doc_id=333) | `trade_date` | `RANGE`：可选股票与日期区间公开；既有调研记录导航标停 | **5000**；多期限／费率记录须取全；5000及停止更新边界未确认 | ts_code+同范围或 trade_date；保留同股同日所有 tenor／fee_rate；当前 `REQUEST` |
| [stk_limit](https://tushare.pro/document/2?doc_id=183) | `trade_date` | `RANGE`：正文覆盖全市场A/B股和基金；可选股票与区间公开 | **5800**；单日也可能超过5800；不能只按A股宇宙证明取全 | ts_code+同范围或 trade_date；基金／B股能否合法用 STOCK 编码待验证；当前 `REQUEST` |
| [suspend_d](https://tushare.pro/document/2?doc_id=214) | `trade_date` | `RANGE`：起止为停复牌查询日期；trade_date 输出说明为停复牌连续日期 | **未明示**；需核实输出连续覆盖及 S/R 同日记录；不把停牌当市场休市 | ts_code+同范围或 trade_date；证明全停复牌记录完整且键归属无冲突；当前 `REQUEST` |
| [top_inst](https://tushare.pro/document/2?doc_id=107) † | `trade_date` | `DATE`：trade_date 必填；未列起止参数；ts_code 可选 | **10000**；单日多机构／方向／原因行完整结束未确认 | ts_code+trade_date；同股全部机构行；ISSUE-008 排除；当前 `REQUEST` |
| [top_list](https://tushare.pro/document/2?doc_id=106) | `trade_date` | `DATE`：trade_date 必填；未列起止参数；ts_code 可选 | **10000**；单日多上榜原因行不可按每股一行判断取全 | ts_code+trade_date；同股全部 reason 行；当前 `REQUEST` |
| [weekly](https://tushare.pro/document/2?doc_id=144) | `trade_date` | `DATE`：trade_date 为每周最后交易日期；与 ts_code 任选一 | **6000**；全市场6000上限及非周末交易日合法空需确认 | ts_code+trade_date；周线不扩到整周，不生成日线；当前 `REQUEST` |

### 公告日期（15）

| 接口／官方来源 | 当前输入 | 候选来源方式／合法性 | 上限或默认值／取全缺口 | 对象时间与独立重试依据 |
|---|---|---|---|---|
| [balancesheet](https://tushare.pro/document/2?doc_id=36) | `ts_code`、`ann_date` | `RANGE`：ts_code 必填且本地已有；start/end 为公告日期 | **未明示**；同公告日多报告类型／修订版本及未明示上限，不能用行少证明完整 | 原 ts_code+ann_date 或相同公告范围；区分 ann_date 与 f_ann_date；当前 `REQUEST` |
| [cashflow](https://tushare.pro/document/2?doc_id=44) | `ts_code`、`ann_date` | `RANGE`：ts_code 必填且本地已有；start/end 为公告日期 | **未明示**；同公告日多报告类型／修订与来源结束依据未确认 | 原 ts_code+ann_date 或相同公告范围；不得改用实际公告日 f_ann_date；当前 `REQUEST` |
| [disclosure_date](https://tushare.pro/document/2?doc_id=162) | `ann_date` | `DATE`：ann_date 为最新披露公告日；end_date 是财报周期，无 start_date | **6000**；按最新公告日不保证保有历史每次计划；同日6000及日期筛选需核实 | ts_code+ann_date；同一 ts_code/end_date 可跨公告更新，须核对归属和既有键；当前 `REQUEST` |
| [dividend](https://tushare.pro/document/2?doc_id=103) | `ann_date` | `DATE`：ann_date 为预案／决案公告日；无起止参数 | **2000**；不能拿 imp_ann_date／ex_date 替代；同日多分红方案取全未确认 | ts_code+ann_date；全方案及进度记录，沿用现有 FINGERPRINT 键；当前 `REQUEST` |
| [express](https://tushare.pro/document/2?doc_id=46) | `ann_date` | `UNCONFIRMED`：官方 ts_code 必填、正文限单股；本地只有 ann_date，当前条件冲突 | **未明示**；合法请求先未成立，不能用 REQUEST 绕过；不得换 express_vip | 即使可按股票重试，也不能据此使当前无股票首次请求合法；当前 `REQUEST` |
| [fina_audit](https://tushare.pro/document/2?doc_id=80) | `ts_code`、`ann_date` | `RANGE`：本地已有必填 ts_code；起止为公告日期 | **未明示**；同公告日跨报告期及审计记录取全、未明示上限需核实 | 原 ts_code+ann_date 或相同公告范围；报告期 end_date 不是选择器日期；当前 `REQUEST` |
| [fina_indicator](https://tushare.pro/document/2?doc_id=79) | `ts_code`、`ann_date` | `DATE`：保留 ts_code+ann_date；start/end 是报告期，禁止公告区间直传 | **100**；单股单公告日也需取全；按报告期过滤后本地筛公告会遗漏 | 原 ts_code+ann_date；包含所有报告期指标及修订，不按 end_date 分公告单元；当前 `REQUEST` |
| [fina_mainbz](https://tushare.pro/document/2?doc_id=81) | `ts_code`、`ann_date` | `UNCONFIRMED`：官方既无输入 ann_date 也无输出 ann_date；只有报告期起止，不能承接公告目标 | **100**；当前公告合同无合法依据；type/period 循环与100上限不能补足公告归属 | 无公告时间映射；禁止猜测 ann_date 或替换 fina_mainbz_vip；当前 `REQUEST` |
| [forecast](https://tushare.pro/document/2?doc_id=45) | `ann_date` | `DATE`：参数表 ts_code／ann_date 二选一，正文却说普通接口仅单股；存在冲突 | **3500**；需先确认不带股票的 ann_date 请求合法及整体3500边界，区间不直接启用 | ts_code+ann_date 虽有表项，须先解决首次全市场条件矛盾；当前 `REQUEST` |
| [income](https://tushare.pro/document/2?doc_id=33) | `ts_code`、`ann_date` | `RANGE`：本地已有必填 ts_code；起止为公告日期，非报告期 | **未明示**；所有 report_type／修订版本完整性与边界未确认 | 原 ts_code+ann_date 或相同公告范围；核对 ann_date／f_ann_date／end_date；当前 `REQUEST` |
| [repurchase](https://tushare.pro/document/2?doc_id=124) | `ann_date` | `RANGE`：只列 ann_date/start_date/end_date；没有 ts_code 输入 | **仅无条件默认2000**；默认2000不是公告区间最大值；真实区间取全合同未公开 | 不支持已公开单股过滤；REQUEST，输出有 ts_code 仍不能按股重试；当前 `REQUEST` |
| [share_float](https://tushare.pro/document/2?doc_id=160) † | `ann_date` | `DATE`：ann_date 合法；start/end 是解禁日期，不能承接公告区间 | **6000**；同公告日所有 float_date／股东记录取全与6000边界未确认 | ts_code+ann_date；时间是 ann_date 而非 float_date；ISSUE-008 排除；当前 `REQUEST` |
| [stk_holdertrade](https://tushare.pro/document/2?doc_id=175) | `ann_date` | `RANGE`：ann_date 与公告起止均公开，ts_code 可选 | **3000**；同股多股东／方向／数量记录取全，3000截断待核实 | ts_code+相同公告范围或 ann_date；需覆盖全部股东记录；当前 `REQUEST` |
| [top10_floatholders](https://tushare.pro/document/2?doc_id=62) | `ann_date` | `UNCONFIRMED`：官方 ts_code 必填而本地未输入；起止是报告期 | **未明示**；股票条件与公告语义两项缺口，不能以最多十人推断完整 | 原接口单股+ann_date 是核实候选，但不合法化无股票首次请求；当前 `REQUEST` |
| [top10_holders](https://tushare.pro/document/2?doc_id=61) | `ann_date` | `UNCONFIRMED`：官方 ts_code 必填而本地未输入；起止是报告期 | **未明示**；股票条件与公告语义两项缺口，多个报告期可能各十人 | 原接口单股+ann_date 是核实候选，但不合法化无股票首次请求；当前 `REQUEST` |

### 覆盖月份（1）

| 接口／官方来源 | 当前输入 | 候选来源方式／合法性 | 上限或默认值／取全缺口 | 对象时间与独立重试依据 |
|---|---|---|---|---|
| [broker_recommend](https://tushare.pro/document/2?doc_id=267) † | `month` | `MONTH`：month 必填；无月份列表和起止参数；每个覆盖月份一批 | **1000**；每月多券商股票推荐取全，循环一词未说明单月超过1000怎样继续 | 没有 ts_code 输入；REQUEST+MONTH，不能按股／券商拆重试；ISSUE-008 排除；当前 `REQUEST` |

### 原生范围（3）

| 接口／官方来源 | 当前输入 | 候选来源方式／合法性 | 上限或默认值／取全缺口 | 对象时间与独立重试依据 |
|---|---|---|---|---|
| [namechange](https://tushare.pro/document/2?doc_id=100) † | `start_date`、`end_date` | `RANGE`：官方 start/end 为公告日期；输出同名字段是名称使用日期 | **未明示**；公开文案可区分 ann_date；筛选字段与包含性未实测，完整结束未确认 | ts_code+原起止虽列为可选，独立恢复未证；REQUEST 保持原范围；ISSUE-008 排除；当前 `REQUEST` |
| [new_share](https://tushare.pro/document/2?doc_id=123) | `start_date`、`end_date` | `RANGE`：官方起止为上网发行日期 ipo_date，不是上市日期 issue_date | **2000**；2000边界及 ipo_date／issue_date 不同样例的筛选、包含性需实测 | 没有 ts_code 输入；REQUEST+RANGE，单日仍 start_date=end_date；当前 `REQUEST` |
| [trade_cal](https://tushare.pro/document/2?doc_id=26) | `exchange`、`start_date`、`end_date` | `RANGE`：日历日期 cal_date；输入 exchange 未列BSE，本地却允许BSE | **未明示**；逐日全集可核对：请求交易所×每个自然日恰一有效记录，必须含两端及休市；BSE来源支持未确认 | 非股票；REQUEST+RANGE；单日仍 start_date=end_date，保留 exchange；当前 `REQUEST` |

### 原条件（11）

| 接口／官方来源 | 当前输入 | 候选来源方式／合法性 | 上限或默认值／取全缺口 | 对象时间与独立重试依据 |
|---|---|---|---|---|
| [hs_const](https://tushare.pro/document/2?doc_id=104) † | `hs_type` | `UNCONFIRMED`：旧官方页正文404；本地 hs_type=SH/SZ 原条件保留 | **无法确认**；hs_type 选择及全标的取全依据未确认 | REQUEST+NONE 保留 hs_type；不得替换新接口；ISSUE-008 排除；当前 `REQUEST` |
| [index_classify](https://tushare.pro/document/2?doc_id=181) | `{}` | `NONE`：参数均可选；保留空请求；例子明确指定 level/src，空请求默认集合未说明 | **未明示**；511个2021分类及359个2014分类是版本介绍，不是空请求返回数量合同 | 非股票分类；REQUEST+NONE，不能擅加 level/src 或日期；当前 `REQUEST` |
| [index_member](https://tushare.pro/document/2?doc_id=182) † | `{}` | `UNCONFIRMED`：旧官方页正文404；原请求为空 | **无法确认**；旧接口默认范围和取全未确认，不能换 index_member_all | REQUEST+NONE；con_code 未证明为可独立重试股票；ISSUE-008 排除；当前 `REQUEST` |
| [index_member_all](https://tushare.pro/document/2?doc_id=335) | `{}` | `NONE`：各参数可选，is_new 默认Y；当前空请求保留该默认 | **2000**；2000上限可能截断全分类成员；按行业循环还需可靠分类全集及集合等价依据 | 原条件组固定 REQUEST+NONE；不把 ts_code 可选扩展为新股票输入；当前 `REQUEST` |
| [pledge_detail](https://tushare.pro/document/2?doc_id=111) | `{}` | `NONE`：各参数可选，保留空请求；有公告区间能力但不扩本期范围 | **1000**；空请求的默认时间覆盖及1000截断未确认；不能悄加日期缩小范围 | 原条件组 REQUEST+NONE；输出质押起止日期不作请求选择器；当前 `REQUEST` |
| [pledge_stat](https://tushare.pro/document/2?doc_id=110) | `{}` | `NONE`：可选 ts_code/end_date；end_date 是单个截止日，当前空请求 | **1000**；默认截止期／历史范围及1000截断未确认 | 原条件组 REQUEST+NONE；不把 end_date 当区间或按股票拆请求；当前 `REQUEST` |
| [stk_holdernumber](https://tushare.pro/document/2?doc_id=166) | `ts_code` | `NONE`：保留本地必填 ts_code；虽有公告起止，也不增加日期 | **3000**；单股原条件默认覆盖及3000上限；enddate 是截止条件，end_date 是公告区间结束 | 原股票公共条件保持；REQUEST+NONE，不从返回 ann_date 自动新增日期选择器；当前 `REQUEST` |
| [stk_managers](https://tushare.pro/document/2?doc_id=193) | `{}` | `NONE`：参数可选；当前空请求；虽有公告区间，仍保留原条件 | **未明示**；默认覆盖和全管理层记录完整性未确认；输出 end_date 是离任日期 | 原条件组 REQUEST+NONE，不因支持多代码新增股票入口；当前 `REQUEST` |
| [stk_rewards](https://tushare.pro/document/2?doc_id=194) | `ts_code` | `NONE`：ts_code 必填且本地已有，可多代码但当前只保留原单股；end_date 是报告期 | **未明示**；单股所有管理层／报告期和默认历史范围取全未确认 | 原股票公共条件保持；REQUEST+NONE，不擅加 end_date 或日期范围；当前 `REQUEST` |
| [stock_basic](https://tushare.pro/document/2?doc_id=25) | `list_status` | `NONE`：保留 list_status=L/P/D；官方更多状态不自动扩枚举；明确一次可拉取完 | **6000**；有单次全量公开保证且说明上限随市场增长；需核实所选状态与当期边界，不能忽略异常截断 | 原条件组 REQUEST+NONE；完整股票列表也不自动成为其他接口历史成员全集；当前 `REQUEST` |
| [stock_company](https://tushare.pro/document/2?doc_id=112) | `exchange` | `NONE`：保留 exchange=SSE/SZSE/BSE；官方明确按交易所分批 | **4500**；当前已按交易所，不再扩市场；单交易所若触顶仍需取全证据 | 原条件组 REQUEST+NONE；即使有可选股票也不改变原条件恢复合同；当前 `REQUEST` |

## 必须单独处理的差异

| 接口 | 已核实的公开差异 | 本期处理与解除条件 |
|---|---|---|
| `fina_indicator` | 起止是报告期；`ann_date` 输入输出均有依据，本地已有股票 | 公告目标只能考虑原股票＋逐 `ann_date`；禁传公告起止为报告期；100 行边界和单公告日全部报告期仍需证据。 |
| `fina_mainbz` | 起止是报告期；官方输入和输出均没有 `ann_date`，本地却要求公告日 | 当前公告目标不可执行；不能通过本地过滤、猜默认字段、增加报告期或换 VIP 补救。须取得同 API 公告筛选及归属依据，或由用户确认需求变更后更新来源文档；本任务不改目标。 |
| `top10_holders` | 起止是报告期；官方股票必填，本地无股票条件 | 两个缺口分别关闭。`ann_date` 虽存在，不能使无股票请求合法；禁止新增必填股票或换 API。 |
| `top10_floatholders` | 同上，且单次可能有多个报告期的前十流通股东 | 同上；“前十”不证明一次只有十条或数据完整。 |
| `share_float` | 起止是解禁日期，输出 `float_date` 与 `ann_date` 分离 | 公告目标只考虑逐 `ann_date`，重试不得改为 `float_date`。ISSUE-008 排除，实际筛选与取全缺口继续保留。 |

`express` 的起止语义确为公告日，但官方股票必填／单股正文与本地仅公告日期冲突，同样不能调用无代码请求或换 `express_vip`。`forecast` 额外存在“股票／公告日二选一”与“普通接口只按单股”的同页矛盾：逐日仅是候选，未关闭矛盾不能宣称支持全市场公告日，也不能由区间候选跳过该限制。

11 个原条件接口中 `pledge_detail/stk_holdernumber/stk_managers` 公开有公告区间参数，本轮仍不增加日期。`index_member_all` 的 `is_new` 默认 Y 是来源默认范围，原请求 `{}` 不意味着全历史；`index_classify` 的行业版本介绍不是空请求默认全集。原条件重试包含原请求语义，不在重试时增添过滤条件来规避上限。

## 日期两端、对象归属与恢复

| 场景 | 已知事实 | 保持的未验证事实／处理 |
|---|---|---|
| DATE 交易／公告 | 参数表给出单日字段；客户端可枚举用户范围的两端 | 这只能证明规划包含两端；仍需核实来源确按该字段筛选、合法空和完整性。不得用报告期／实际公告日替换 `ann_date`。 |
| RANGE 候选 | 公开列出开始／结束；daily、slb_len 样例出现结束日，trade_cal 样例出现开始日 | 个别示例不是所有当前条件下的双端合同。本轮未实测任何接口的两端包含性，启用前以两端已有数据的样例核对。 |
| `trade_cal` | 输入日历起止，输出 `cal_date/is_open/exchange`，官方样例包括休市行 | 可用所选交易所×请求每一自然日的精确集合检查覆盖：无缺日、越界、错市场、无效值或冲突；不能只比行数。SSE／SZSE 输入公开，BSE 未列，不能借 `margin` 支持 BSE 来证明日历支持。实际新鲜度／适用性交 T02。 |
| `new_share` | 输入为上网发行日期；对应输出 `ipo_date`，`issue_date` 为上市日期 | 公开文案支持“申购／上网发行日期”方向；仍需两个字段不同样例，以及各只包含一个日期、恰含边界的范围。未完成前不能声称实际筛选确认。 |
| `namechange` | 输入起止为公告日，输出 `ann_date`，输出 `start_date/end_date` 是名称使用日期 | 公开文案与有效期可区分；实际筛选和包含性未验证，ISSUE-008 不恢复调用。不能为凑原生接口通过数量解除排除。 |
| MONTH | `broker_recommend.month` 是年月，股票和券商共同构成业务键 | 31 天限制先校验，再覆盖完整年月；`20260131～20260302` 对应 1／2／3 月。只有 month 过滤，按股恢复没有公开请求依据。 |
| 周线／月线 | `trade_date` 是周／月最后交易日期，数据保持原周期 | 不构造每日记录，不扩展整周／月来改变输入边界；非产出日可能合法空，不能以缺日判失败。 |

公开输出字段及本地业务键已逐项保存在 JSON，不修改业务键。特别注意：`slb_len.ob` 是期初余额而不是证券对象；`hk_hold.code`、`index_member.con_code` 没有本轮股票选择器映射依据；`margin/moneyflow_hsgt/trade_cal/index_classify` 不应强行股票化。`repurchase/new_share/broker_recommend` 即使返回 ts_code，公开请求没有股票过滤，继续 REQUEST。

对可以公开表达 `ts_code+日期` 的接口，R 门槛还要求：完整批次中该对象时间的所有行，恰等于相同字段投影的独立请求结果（业务键集合和规范内容均相等）；同股多个报告期／原因／机构／期限必须一起覆盖。局部适配失败可在这些条件满足后隔离；缺少股票记录本身不是股票失败，也不能拿静态 stock_basic 全集替代某历史业务应有成员集合。来源没有逐股状态时不编造状态。

REQUEST 的时间按真实批次保存：DATE 为原单日，MONTH 为完整年月，RANGE 为完整起止，原条件为 NONE；公共股票／交易所／状态条件保持。三个原生接口即使时间选择器是 DATE，也重建 `start_date=end_date=d`，不发送 `trade_date/ann_date`。合法方式未确认的项先拒绝，不为其猜建不可重建的范围。失败后不拆小 RANGE、不把 REQUEST 转 STOCK，不把其他已成功单元重新包入失败项。

## 交给后续任务的待验证事实

下面是核实清单，不是已安排或执行的业务调用。T20 需先按看板完成专属设计，确定实际账号权限、样例、基准来源、取全判据和可接受结果；未被选中的接口继续保持逐行未验证状态。T01／T02 的未知项不能经由受控 fixture 变成来源已支持。

| 核实主题 | 候选范围（均须 T20 选择） | 具体方法与通过结果 |
|---|---|---|
| 当前条件与边界 | daily、income、margin、weekly／monthly、trade_cal、new_share | 使用原股票／交易所条件及两端已知有记录的范围，增加单日两端相等和只含一侧日期的对照；捕获实际请求，核对官方含义、两端和对象条件均不扩大。不能只比较总行数。 |
| 公告／报告期／实际公告日 | income；如选择 fina_indicator／disclosure_date，先完成合法单日与结束依据 | 用 ann_date、f_ann_date、end_date 不相同的记录比较；公告范围结果的业务键及完整内容等于已证完整的逐公告日并集。fina_indicator 不传公告区间到报告期参数。 |
| 原生日期 | new_share、trade_cal | new_share 使用 ipo_date≠issue_date 的真实样例；trade_cal 逐日集合包含开闭市、身份和两端，检查切换交易所与缺页／错误市场响应。namechange 仅保留公开结论，不进入本轮调用。 |
| 来源完整性 | 被选接口逐一；尤其 daily 6000、monthly 4500、stk_limit 5800 的单日高量风险 | 先取得适用的合法分页或来源全量保证／可靠预期集合。区间与完整合法单日／单期基准比较业务键和规范内容；短响应、两次同截断结果一致不够。不能用少量低负荷样例证明任意高量都可取全。 |
| 独立恢复 | 仅选择 R 门槛有希望闭合的同 API／ts_code／时间组合 | 将完整批次按可靠字段分组，与精确独立请求比较全部键及内容；覆盖同股多记录与合法空。随后由 T18 的受控故障证明 A/C 保留、仅 B 失败；真实来源取全和系统事务隔离是两类证据。 |
| 原条件／请求冲突 | 被选原条件接口，以及 forecast／express／top10 系列／fina_mainbz 的公开合同缺口 | 先关闭来源依据冲突才安排调用；不能添加本地未有股票／日期、改 VIP 或按猜测对象全集分段。原请求默认范围与结束依据必须可说明。未解决继续拒绝，并在 T21 分别报告。 |
| 日历来源 | T02 的 19 项对应来源；特别是 BSE、转融通、互联互通 | T02 先核实必要市场、权威来源、等价与更新规则；T20 再按实际范围验证。单一 SSE、香港普通日历、周末过滤不能替代；股票停牌不等于市场休市。 |

明确排除真实调用：`top_inst`、`broker_recommend`、`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`namechange`、`hsgt_top10`。这些项的目标合同和受控覆盖仍保留，[ISSUE-008](../issues/problems/ISSUE-008-tushare-live-coverage-gap.md) 保持“不依赖，未解决，用户后续单独处理”。没有用 49－9 计算本轮支持数量。

## AC 来源前提追踪

| PRD 验收项 | 本任务提供的依据 | 后续结果证据 |
|---|---|---|
| AC-PRD-RANGE-01 | 49 项集合、19／15／1／3／11 分类、本地必填与枚举，目标与候选状态分开 | T03／T04 合同与策略；T15／T19 表单；T20 真实范围 |
| AC-PRD-RANGE-03 | income 保留 ts_code、margin 保留 exchange_id；同 API 精确范围重建 | T05／T13 参数及恢复，T18／T20 |
| AC-PRD-RANGE-14 | 49 项 REQUEST 回退条件；非股票和无股票过滤接口；11 项 NONE 原条件 | T09／T13／T18；不能用 REQUEST 掩盖取全未知 |
| AC-PRD-RANGE-18 | 公告、完整月份、三原生及原条件不进入日历屏障；日期语义差异专题 | T06／T08／T15／T18 |
| AC-PRD-RANGE-19 | 当前参数精确快照；38 项投影不能机械透传；原附加条件保持 | T03／T05／T14／T15 的迁移拒绝及合法请求测试 |
| AC-PRD-RANGE-25 | 4 缺页、4 合同缺口与 forecast 矛盾的拒绝条件；不得猜参换 API | T05／T13 参数不兼容时保留原记录；T18 |
| AC-PRD-RANGE-29 | 整体取全、对象时间及合法精确单独恢复三个证据层；不猜缺股失败 | T07／T09／T18 受控行为与 T20 来源核实 |

以上是来源前提可追溯，不表示这些功能 AC 已通过。未核实来源仍影响相关业务可用性；T20 选定范围的必要证据缺口不能仅凭本报告登记关闭。

## 验证

从仓库根运行以下结构校验。它核对交付物及来源快照一致性，不调用业务 API，也不把断言通过算作来源实测。

```sh
python3 - <<'PY'
import hashlib, json, re
from collections import Counter
from pathlib import Path
root = Path.cwd()
e = json.loads(Path('docs/research/RANGE-T01-source-evidence.json').read_text())
rows = e['interfaces']
by_api = {r['apiName']: r for r in rows}
yaml_dir = Path('data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro')
prd = Path('docs/design/Tensor_区间下载_PRD_v1.0.md').read_text().split('## 附录 A：')[1]
assert len(rows) == len(by_api) == 49
assert set(by_api) == {p.stem for p in yaml_dir.glob('*.yaml')}
assert set(by_api) == set(re.findall(r'^\| `([a-z0-9_]+)` \|', prd, re.M))
assert Counter(r['mode'] for r in rows) == dict(TRADE_DATE_RANGE=19, ANN_DATE_RANGE=15, MONTH_RANGE=1, NATIVE_RANGE=3, ORIGINAL_PARAMS=11)
excluded = set('top_inst broker_recommend share_float hs_const moneyflow_hsgt hk_hold index_member namechange hsgt_top10'.split())
assert {r['apiName'] for r in rows if r['live']['status'] == 'EXCLUDED_ISSUE_008'} == excluded
assert {r['apiName'] for r in rows if r['date']['rangeSemanticDiffers']} == set('fina_indicator fina_mainbz top10_holders top10_floatholders share_float'.split())
assert all(by_api[a]['request']['candidateMode'] != 'RANGE' for a in by_api if by_api[a]['date']['rangeSemanticDiffers'])
assert {r['apiName'] for r in rows if r['source']['status'] == 'MISSING_PAGE'} == set('hk_hold moneyflow_hsgt hs_const index_member'.split())
assert len([r for r in rows if r['request']['legality'] == 'DOCUMENTED_CANDIDATE']) == 40
assert by_api['forecast']['request']['legality'] == 'CONFLICT'
for r in rows:
    c, s = r['current'], r['source']
    data = Path(c['path']).read_bytes()
    assert hashlib.sha256(data).hexdigest() == c['sha256'], r['apiName']
    text = data.decode()
    assert c['parametersYaml'] in text and c['businessKeyYaml'] in text
    assert re.fullmatch(r'https://tushare.pro/document/2\?doc_id=\d+', s['url'])
    assert re.fullmatch(r'[a-f0-9]{64}', s['rawPageSha256']) and s['introduction']
    assert not {'limit', 'offset'} & set(s['inputParameters'])
    assert bool(s['inputParameters']) == (s['status'] == 'READABLE')
    assert r['request']['params'] and r['request']['conclusion'] and r['date']['endpoints']
    assert r['completeness']['gapOrConditionalCriterion'] and set(r['completeness']['requiredGates']) <= e['gates'].keys()
    assert r['recovery']['policy'] == 'REQUEST' and r['recovery']['independentRetryCandidateOrRestriction']
    assert not r['date']['liveVerified'] and not r['recovery']['independentRecoveryVerified']
    assert ('K' in r['completeness']['requiredGates']) == (r['mode'] == 'TRADE_DATE_RANGE')
report = Path('docs/research/RANGE-T01-source-capabilities.md')
assert set(re.findall(r'^\| \[([a-z0-9_]+)\]\(https://tushare', report.read_text(), re.M)) == set(by_api)
for p in [report, Path('docs/task-designs/RANGE-T01-design.md'), Path('docs/task-designs/RANGE-T02-design.md'), Path('docs/task-handoffs/tensor-range/RANGE-T02-handoff.md')]:
    if not p.exists():
        assert 'RANGE-T02' in p.name  # 后继准备前可尚未创建；准备后另查板链。
        continue
    for link in re.findall(r'\]\(([^)]+)\)', p.read_text()):
        if not link.startswith(('https://', 'http://', '#')):
            assert (p.parent / link.split('#')[0]).exists(), (p, link)
print('PASS: 49接口集合/分组/快照；9排除；5日期差异；来源/恢复门槛；报告行与链接')
PY
python3 -m json.tool docs/research/RANGE-T01-source-evidence.json > /dev/null
git diff --check
git diff --cached --check
```

人工复核重点：输入表按“名称／类型／必选或必须”定位，未将 index_classify 的 511 行行业表当参数；5 项非公告区间、fina_mainbz 输入及输出均无公告日、3 项缺股票、forecast 冲突、trade_cal 与 margin 的 BSE 说明差异、REQUEST 不豁免取全、原生单日请求形状均已写入逐项结论。

本轮结果（2026-09-08）：上述结构校验输出 PASS，JSON 语法与暂存／未暂存空白检查均为退出码 0；49 个本地 YAML 摘要一致、9 项排除及 5 项差异集合精确相等。49 份原 HTML 摘要另行与既有调研缓存核对一致。未运行 Maven、前端或业务 API 验证；这些结果只证明研究交付的覆盖与一致性。
