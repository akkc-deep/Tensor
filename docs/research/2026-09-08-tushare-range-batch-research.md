# Tushare 多日批量下载能力调研

调研日期：2026-09-08。范围：Tensor 当前登记的 49 个接口，重点核对尚未原生接入区间的 35 个日期／月份接口。

**结论：多数接口公开支持起止日期，不能依据本地只声明单日参数，就判断上游只能逐日下载。但支持区间查询，不等于全市场多日数据一定能在一次响应中取全。**

**设计状态更新（2026-09-08）：** 本文保留官方能力摘录及当时的讨论记录；§6 第 5 项中“下载、事务与恢复使用同一批次”的产品决策已由 [BRD v1.4](../design/Tensor_区间下载_BRD_v1.0.md)、[PRD v1.4](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD v1.10](../design/Tensor_区间下载_TRD_v1.0.md) 替代。当前请求批次可包含多个独立恢复单元；是否可按股票时间独立恢复仍需单独核实，本文不构成该能力的验证。

## 1. 核实方式与统计

本轮只读访问 Tushare 官方公开接口文档，核对输入参数表、参数含义、必填条件、调用示例及返回数量说明。49 个接口文档地址中，45 个返回对应接口正文，4 个显示“404, 文档不存在！”。另查看官方获取指南、常见问题及 HTTP／SDK 说明。未读取账号凭证，未调用业务数据 API，未验证账号权限、实际筛选效果或分页行为。

| 当前设计分组 | 官方列出起止日期参数 | 未列出对应区间参数 | 官方旧页面不存在 | 合计 |
|---|---:|---:|---:|---:|
| 交易日期接口 | 15 | 2 | 2 | 19 |
| 公告日期接口 | 13 | 2 | 0 | 15 |
| 月度推荐 | 0 | 1 | 0 | 1 |
| 已接入原生区间 | 3 | 0 | 0 | 3 |
| 保留原条件接口 | 3 | 6 | 2 | 11 |
| 合计 | **34** | **11** | **4** | **49** |

用户本次重点询问的其余 35 个日期／月份接口中，**28 个列出了 `start_date/end_date`，5 个没有对应区间参数，2 个无法从当前官方页面确认**。这 28 个中有 5 个按报告期或解禁日期筛选，不能直接替换现有设计的公告日期语义；部分接口还有股票代码条件。

下表“上游条数说明”来自官方文档，未明示表示本轮页面没有明确数字，不表示无限返回。这些是上游条件；Tensor 的执行限制仍按用户已确认的日期输入最多 31 个自然日处理。本调研不恢复已删除的整轮时长、累计行数或累计响应体限制。

## 2. 19 个交易日期接口

表中“支持”表示官方声明有区间参数，实际使用还须满足同一行列出的选择条件及完整性要求。

| 接口与官方来源 | 区间参数／日期含义 | 上游条数说明 | 对当前下载方式的判断 |
|---|---|---|---|
| [adj_factor](https://tushare.pro/document/2?doc_id=28) | 支持 `start_date/end_date`，交易日期 | 未明示 | 官方描述单只股票历史或单日全部股票；可研究区间调用，全市场多日不能据此保证一次取全 |
| [block_trade](https://tushare.pro/document/2?doc_id=161) | 支持，交易日期 | 单次最多 1,000 | 股票代码和日期至少一个；可按区间获取，密集日期须核实取全方式 |
| [daily](https://tushare.pro/document/2?doc_id=27) | 支持，交易日期 | 每次 6,000 | 示例明确单股区间；`ts_code` 支持逗号分隔多股。官方建议全市场历史按日期循环 |
| [daily_basic](https://tushare.pro/document/2?doc_id=32) | 支持，交易日期 | 单次最多 6,000 | `ts_code/trade_date` 标注二选一；当前只有日期输入，不能直接假定仅起止日期可获取全市场 |
| [hk_hold](https://tushare.pro/document/2?doc_id=188) | 无法确认，页面显示文档不存在 | 无法确认 | 保留证据缺口，不能判为支持或不支持 |
| [hsgt_top10](https://tushare.pro/document/2?doc_id=48) | 支持，交易日期 | 未明示 | `ts_code/trade_date` 标注二选一；仅区间、不指定股票是否接受须核实 |
| [margin](https://tushare.pro/document/2?doc_id=58) | 支持，交易日期 | 单次最多 4,000 | 可保留当前 `exchange_id` 并传区间，适合优先考虑原生区间 |
| [margin_detail](https://tushare.pro/document/2?doc_id=59) | 支持，交易日期 | 单次最多 6,000 | 可以表达区间；全市场多日须处理返回截断 |
| [moneyflow](https://tushare.pro/document/2?doc_id=170) | 支持，交易日期 | 单次最多 6,000 | 股票或时间参数至少一个；可以表达区间，全市场取全需核实 |
| [moneyflow_hsgt](https://tushare.pro/document/2?doc_id=47) | 无法确认，页面显示文档不存在 | 无法确认 | 保留证据缺口，不能套用其他资金流接口的参数 |
| [monthly](https://tushare.pro/document/2?doc_id=145) | 支持，月线对应交易日期 | 单次最多 4,500 | `ts_code/trade_date` 任选一；区间示例带股票代码。不能把只传区间视为已确认支持全市场 |
| [slb_len](https://tushare.pro/document/2?doc_id=331) | 支持，交易日期 | 单次最多 5,000 | 官方示例只传起止日期，可优先考虑原生区间 |
| [slb_sec](https://tushare.pro/document/2?doc_id=332) | 支持，交易日期 | 单次最多 5,000 | 可表达区间；官方导航标“停”，历史可用性和更新状态须另验 |
| [slb_sec_detail](https://tushare.pro/document/2?doc_id=333) | 支持，交易日期 | 单次最多 5,000 | 可表达区间；官方导航标“停”，历史可用性和更新状态须另验 |
| [stk_limit](https://tushare.pro/document/2?doc_id=183) | 支持，交易日期 | 单次最多 5,800 | 覆盖 A/B 股和基金；全市场单日也须核实是否超过条数上限 |
| [suspend_d](https://tushare.pro/document/2?doc_id=214) | 支持，停复牌查询日期 | 未明示 | 可表达区间，股票代码可输入多值；需按返回日期与停复牌语义检查完整性 |
| [top_inst](https://tushare.pro/document/2?doc_id=107) | 未列起止日期；`trade_date` 必填 | 单次最多 10,000 | 按当前公开合同逐日获取；没有多日期列表参数说明 |
| [top_list](https://tushare.pro/document/2?doc_id=106) | 未列起止日期；`trade_date` 必填 | 单次最多 10,000 | 按当前公开合同逐日获取；没有多日期列表参数说明 |
| [weekly](https://tushare.pro/document/2?doc_id=144) | 支持，周线对应交易日期 | 单次最多 6,000 | `ts_code/trade_date` 任选一；区间示例带股票代码，全市场仍建议按对应交易日组织 |

`weekly/monthly` 的周期不会因为使用起止日期而变成日线。官方分别说明每周／每月最后交易日期；按天查询时也应遵守接口实际产出日期，不能把每个开盘日都理解成有一份新的周／月行情。

## 3. 15 个现设计公告日期接口

| 接口与官方来源 | 官方起止日期含义 | 上游条数说明 | 与当前条件／语义的关系 |
|---|---|---|---|
| [balancesheet](https://tushare.pro/document/2?doc_id=36) | 支持，公告日期 | 未明示 | `ts_code` 必填，当前已保留；可考虑单股公告区间 |
| [cashflow](https://tushare.pro/document/2?doc_id=44) | 支持，公告日期 | 未明示 | `ts_code` 必填，当前已保留；可考虑单股公告区间 |
| [disclosure_date](https://tushare.pro/document/2?doc_id=162) | 未列 `start_date`；`end_date` 是单个财报周期 | 单次最多 6,000 | 不能把 `end_date` 当区间结束日；当前公告日期需求继续按 `ann_date` 组织 |
| [dividend](https://tushare.pro/document/2?doc_id=103) | 未列起止日期；有多个不同含义的单日期参数 | 单次 2,000 | 当前公告日期区间按 `ann_date` 逐日；不能用除权日或实施公告日代替 |
| [express](https://tushare.pro/document/2?doc_id=46) | 支持，公告日期 | 未明示 | 官方 `ts_code` 必填，本地没有该输入；不能直接将当前全市场公告日请求改成无代码区间请求 |
| [fina_audit](https://tushare.pro/document/2?doc_id=80) | 支持，公告日期 | 未明示 | `ts_code` 必填，当前已保留；可考虑单股公告区间 |
| [fina_indicator](https://tushare.pro/document/2?doc_id=79) | **支持，但为报告期开始／结束日期** | 每次最多 100 | 当前按公告日期；直接透传起止日期会改变查询含义 |
| [fina_mainbz](https://tushare.pro/document/2?doc_id=81) | **支持，但为报告期开始／结束日期** | 单次最多 100 | 官方输入表没有 `ann_date`；本地按公告日期接入缺乏当前官方合同依据，须先修正设计判断 |
| [forecast](https://tushare.pro/document/2?doc_id=45) | 支持，公告日期 | 单次 3,500 | 参数表 `ts_code/ann_date` 二选一，正文又提示普通接口只按单股取历史；无股票区间能力不能据此确认 |
| [income](https://tushare.pro/document/2?doc_id=33) | 支持，公告日期 | 未明示 | `ts_code` 必填，当前已保留；可考虑单股公告区间 |
| [repurchase](https://tushare.pro/document/2?doc_id=124) | 支持，公告日期 | 全部参数不填时默认 2,000；不是已明示的区间最大值 | 可表达公告区间，仍需核实完整获取条件 |
| [share_float](https://tushare.pro/document/2?doc_id=160) | **支持，但为解禁开始／结束日期** | 单次最多 6,000 | 当前按公告日期；不能把解禁日期区间当作公告日期区间 |
| [stk_holdertrade](https://tushare.pro/document/2?doc_id=175) | 支持，公告日期 | 单次最多 3,000 | 可表达公告区间，保留原条件并检查取全 |
| [top10_floatholders](https://tushare.pro/document/2?doc_id=62) | **支持，但为报告期开始／结束日期** | 未明示 | 官方 `ts_code` 必填而本地没有；同时存在股票条件和日期语义差异 |
| [top10_holders](https://tushare.pro/document/2?doc_id=61) | **支持，但为报告期开始／结束日期** | 未明示 | 官方 `ts_code` 必填而本地没有；同时存在股票条件和日期语义差异 |

其中 8 个公开列出公告日期起止参数，另 5 个列出其他日期含义的起止参数。报告期与公告日期没有可直接替换的一一对应关系，不能仅在下载后过滤来弥补上游按错误日期条件遗漏的数据。

普通 `income/balancesheet/cashflow/fina_indicator/fina_mainbz/forecast/express` 页面的提示还提到对应 `*_vip` 接口可按季度获取全部上市公司数据，并要求相应权限。它们是不同接口和获取维度，不能因同页提及就将当前 49 个接口视为已具备该能力，也不能替换 API 后宣称仍沿用原接口。

## 4. 月度、已接入区间及原条件接口

| 接口与官方来源 | 区间／批量能力 | 上游条数说明与当前设计影响 |
|---|---|---|
| [broker_recommend](https://tushare.pro/document/2?doc_id=267) | 只列必填 `month`，未列起止月份或月份列表 | 单次最多 1,000；跨月仍逐月获取，同月无需按天重复 |
| [namechange](https://tushare.pro/document/2?doc_id=100) | `start_date/end_date` 是公告日期区间 | 已接入原生区间；不能与输出的名称使用起止日期混为一谈 |
| [new_share](https://tushare.pro/document/2?doc_id=123) | `start_date/end_date` 是上网发行日期区间 | 单次最多 2,000；不同于上市日期 |
| [trade_cal](https://tushare.pro/document/2?doc_id=26) | 支持日历区间 | 可整段获取；具体交易所可用性另验，休市记录按原条件保留 |
| [hs_const](https://tushare.pro/document/2?doc_id=104) | 无法确认，旧页面不存在 | 当前导航有不同页面，不能据其替代旧接口能力 |
| [index_classify](https://tushare.pro/document/2?doc_id=181) | 未列日期区间 | 按行业分级、来源等条件获取；分类版本不是任意日期历史 |
| [index_member](https://tushare.pro/document/2?doc_id=182) | 无法确认，旧页面不存在 | `index_member_all` 是另一接口，不能替代核实 |
| [index_member_all](https://tushare.pro/document/2?doc_id=335) | 未列日期区间 | 单次最多 2,000；有分级、股票和 `is_new`，并不代表按任意历史日期查成员 |
| [pledge_detail](https://tushare.pro/document/2?doc_id=111) | **支持公告日期区间** | 单次最多 1,000；当前未接入该条件，可作为后续扩展候选 |
| [pledge_stat](https://tushare.pro/document/2?doc_id=110) | 未列 `start_date`，仅 `end_date` 截止日期 | 单次最多 1,000；单个截止日期不是起止区间 |
| [stk_holdernumber](https://tushare.pro/document/2?doc_id=166) | **支持公告日期区间** | 单次最多 3,000；当前已有股票条件，可作为后续扩展候选；`enddate` 与 `end_date` 是不同参数 |
| [stk_managers](https://tushare.pro/document/2?doc_id=193) | **支持公告日期区间，股票代码支持多个** | 当前未接入区间条件，可作为后续扩展候选 |
| [stk_rewards](https://tushare.pro/document/2?doc_id=194) | 未列日期区间；股票代码支持多个 | `end_date` 为单个报告期；“批量股票”不等于“多日区间” |
| [stock_basic](https://tushare.pro/document/2?doc_id=25) | 未列日期区间；可按状态等条件批量获取股票列表 | 单次最多 6,000，官方说明随股票总数增长调整；不需要逐日重复获取 |
| [stock_company](https://tushare.pro/document/2?doc_id=112) | 未列日期区间；可按交易所等条件批量获取 | 单次 4,500，官方建议按交易所分批 |

原条件组新发现的 3 个公告区间接口属于扩展候选；本次调研不自动改变已经确认的 38＋11 产品范围。

## 5. 一次请求、全市场与分页

官方 [daily 文档](https://tushare.pro/document/2?doc_id=27) 明确提供单股跨日期例子，并写道：“建议提供循环日期来提取全市场数据，不要通过循环 ts_code 来拉取历史”。官方 [如何优雅高效地获取数据](https://tushare.pro/document/1?doc_id=230) 也区分：部分个股历史用股票代码加起止日期，全市场历史用交易日期循环。

这对 Tensor 尤其相关：当前 `daily` 等接口的表单只有日期，没有股票筛选，实际请求范围是全市场。以现有 [manifest](../data-template/manifest.json) 的 2026-08-07 样例为例，`daily` 一天记录 5,535 行；两天若规模相近，约 11,070 行，超过官方每次 6,000 行说明。这是规模示例，不是本轮真实两日 API 验证。

本轮查看的接口输入参数表均未列出通用 `limit/offset`，部分正文只说“循环获取”或“总量不限制”；这些文字不能证明每个接口都支持同一套分页参数。[HTTP 说明](https://tushare.pro/document/1?doc_id=130) 展示的是 `fields/items` 返回结构，也没有提供覆盖这 49 个接口的统一分页结束合同。[SDK 说明](https://tushare.pro/document/1?doc_id=131) 与 [常见问题](https://tushare.pro/document/1?doc_id=122) 未补足这个证据。

因此，批量实现应先逐接口确认分页或合法分段方式。达到官方条数边界时不能默认完整；没有明确后续标志的短响应也仍需有经核实的取全判据。即使逐日请求，`monthly`、`stk_limit` 等全市场单日数据也可能触及上游上限，逐日策略本身不能作为完整性证明。

## 6. 对现有方案的建议

1. **按接口和实际条件选择下载方式。** 日期含义相同且选择条件满足的接口，可优先评估原生区间，例如保留交易所的 `margin`、`slb_len`，以及已要求股票代码的 `income/balancesheet/cashflow/fina_audit`。仍需验证取全和实际请求结果。
2. **全市场行情继续按日期或经核实的分段方式组织。** `daily/daily_basic/weekly/monthly` 等不能因为出现 `start_date/end_date` 就直接改为无股票条件的整段请求；选择条件和单次条数必须一起判断。
3. **没有对应区间参数的接口按已公开粒度组织。** `top_list/top_inst/dividend/disclosure_date` 按对应单日；`broker_recommend` 按单月。多股票查询、默认返回历史或一次返回多行，均不等同于按用户选定日期区间查询。
4. **先处理日期语义和参数差异。** 5 个报告期／解禁日期接口不能机械映射现有公告区间；`fina_mainbz` 的 `ann_date`、`express/top10_holders/top10_floatholders` 缺少股票条件的本地定义需要单独核实和修订。
5. **下载、校验、入库和恢复使用同一批次边界（调研后的用户确认）。** 每批完整下载并校验后批量入库，提交确认后处理下一批；成功批次保留，失败批次按原范围手动重试。多日批次不再按日分别提交，分页或 SQL 分组失败则整批失败，不凭部分响应把其中日期标为成功，不自动拆批重试。

公开来源结论与产品决策分开：以上批次处理方式随后由用户确认，并已同步至 BRD／PRD 正文 v1.2 及 TRD 的批次规则；生产代码尚未修改。每个接口能否采用多日批次仍须符合本报告的日期语义、必填条件和取全证据，不能在缺少核实的情况下全部改为一次全量请求。

## 7. 实施前还需验证的事实

- 在保持现有股票／交易所条件的前提下，区间请求是否被实际接受，筛选字段和两端包含性是否符合文档。
- 每个候选接口的返回上限、是否支持分页、分页结束及取全依据；区间结果与合法单日／单期结果的业务键集合和内容是否一致。
- 区间内合法空日期、停牌、休市、分页中断及数据冲突如何在已确认的批次边界内验证，证明失败整批回滚、成功批次保留。
- 4 个旧接口文档缺失、转融券停止更新标记及现有真实账号证据缺口。公开页面消失不等于已实测 API 停用；公开列出参数也不等于账号获准访问。

上述接口页逐行链接为本报告的主要证据。原始页面与提取结果保存在本机临时目录 `/tmp/tensor-tushare-batch-research-20260908/`，其中 `evidence.json` 保存接口 URL、原始页面 SHA-256、正文和参数表；`summary.json` 保存机械参数统计。临时缓存可能被清理，报告中的日期、参数摘录和官方来源链接保留在 Git 中。
