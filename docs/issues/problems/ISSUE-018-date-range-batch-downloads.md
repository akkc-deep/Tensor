# ISSUE-018：补齐按日期区间批量下载能力

## 当前状态

处理中，未解决（2026-09-12）。[详细设计](../../task-designs/ISSUE-018-design.md)所列T01–T12已完成：插件合同、任务存储/执行/恢复、HTTP与前端，以及真实Servlet/MySQL生命周期、故障矩阵和六条源码门禁均有证据。最终普通浏览器7文件126项全部通过，见[基础设施验证](../../verification/ISSUE-018-task-infrastructure.md)。34项生产RANGE仍为NEEDS_VERIFICATION，真实接口语义和完整性验收由T13跟踪，母issue保持未解决。完整发布脚本因main/干净输入/HEAD前置未满足而未运行。

T13的[专属设计](../../task-designs/ISSUE-018-T13-design.md)与[交接](../../task-handoffs/ISSUE-018/ISSUE-018-T13-handoff.md)已完成并准备READY，尚未启动实施。准确任务状态见[ISSUE-018看板](../../task-handoffs/ISSUE-018/ISSUE-018-task-board.md)。T12的[专属设计](../../task-designs/ISSUE-018-T12-design.md)与[历史暂停交接](../../task-handoffs/ISSUE-018/ISSUE-018-T12-handoff.md)保留；以下官网调研与旧代码现状是历史来源，不改写为真实接口已验收。

## 调研范围与结论

本项目当前生产数据源为 **Tushare Pro**，本次“哪些数据源”按其已接入的 40 个数据接口逐项核对。范围来自 [manifest.json](../../data-template/manifest.json) 与[生产 YAML](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro)，源码基线为 `452832ed17d484f4f4b4af310546242c604d5f8d`，工作分支为 `feat/download-by-date-range`。

2026-09-11 逐页读取下表链接对应的 **40 个 Tushare 官方公开文档页面**，均取得 HTTP 200，并核对正文接口名称、输入参数表、限量说明及相关示例。本轮未调用真实数据 API；以下结论证明官网声明的能力，不代表当前账户权限、实际数据可用性或区间数据完整性已经验收。

| 分类 | 数量 | 结论与项目差距 |
| --- | --- | --- |
| 原生支持 `start_date + end_date` | 31 | 上游可按区间请求；项目仅 `trade_cal`、`new_share` 已配置区间参数，另外 29 项尚未开放 |
| 仅支持单日，可由应用遍历日期 | 3 | `top_list`、`dividend`、`disclosure_date`；可作为批量编排候选，不是上游原生区间能力 |
| 仅有单个截止日或报告期参数 | 2 | `pledge_stat`、`stk_rewards`；不能把 `end_date` 直接当作区间上界，批量方案待验证 |
| 官网未声明日期输入 | 4 | `stock_basic`、`stock_company`、`index_classify`、`index_member_all`；不提供日期区间入口 |
| 合计 | 40 | 31 项原生区间、3 项逐日候选、2 项待验证、4 项无日期输入 |

“批量下载”在本 issue 中指一次用户操作获取同一接口在指定日期范围内的数据，必要时分多次上游请求；不自动扩展为多股票、全市场或多个接口同时下载。沿用 [ISSUE-017](ISSUE-017-stock-scoped-downloads.md) 的范围约束：34 个支持股票输入的接口必须指定股票，另外 6 个保持各自的非股票参数方式。

已永久移除的 `top_inst`、`broker_recommend`、`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`hsgt_top10`、`namechange` 不属于本轮范围，也不恢复支持；不扩展到 Tushare 全站其他接口或其他供应商。

## 项目现状与影响

- 40 份生产 YAML 中只有 [trade_cal.yaml](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/trade_cal.yaml) 和 [new_share.yaml](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/new_share.yaml) 声明了成对的 `DATE_RANGE_MEMBER`。例如 [daily.yaml](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/daily.yaml) 当前要求 `ts_code + trade_date`，用户无法直接输入历史起止日期。
- [DynamicParameterForm.vue](../../../control-plane/src/components/download/DynamicParameterForm.vue) 按元数据渲染参数；[ParameterValidator](../../../data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java) 会拒绝未声明参数。现有日期范围校验可以复用，但仅在页面增加输入框不能打通下载。
- [ParameterCodec](../../../data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/ParameterCodec.java) 按具体参数形状绑定请求，已有无股票区间和 `exchange + 区间`，缺少 `ts_code + 区间`、`exchange_id + 区间` 等本次所需形状。新增 YAML 时必须同步请求参数类型与绑定。
- [DownloadService](../../../data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java) 一次调用插件后适配、入库；[TushareProClient](../../../data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java) 每次执行只发起一次上游 POST。当前链路没有按日期切片、逐日遍历或分页补齐逻辑。
- 因此，已有区间表单也不等于“任意长区间完整下载”；新增区间参数后如果仍只请求一次，可能把上游达到行数上限的结果误当作完整结果。

## 官网逐项核对

下表的“当前参数”来自本次源码基线；“单次限量”来自对应官网页面。`未注明` 表示该页未明确给出单次行数，**不代表无限制**。所有原生区间接口的官网输入表都声明了 `start_date` 和 `end_date`；下表按它们实际筛选的日期归类。

### 原生区间：交易日期、日历日期与发行日期（16 项）

| 接口（官网） | 数据 / 区间语义 | 当前参数 | 官网单次限量与注意事项 |
| --- | --- | --- | --- |
| [daily](https://tushare.pro/document/2?doc_id=27) | 日线行情 / 交易日期 | `ts_code, trade_date` | 6000 行；官网有单股票起止日期示例，停牌期间不提供行情 |
| [weekly](https://tushare.pro/document/2?doc_id=144) | 周线行情 / 每周最后交易日 | `ts_code, trade_date` | 6000 行；按周末交易日落入区间理解，不能按自然周五硬编码 |
| [monthly](https://tushare.pro/document/2?doc_id=145) | 月线行情 / 每月最后交易日 | `ts_code, trade_date` | 4500 行；不是每日更新的月线接口 |
| [adj_factor](https://tushare.pro/document/2?doc_id=28) | 复权因子 / 交易日期 | `ts_code, trade_date` | 未注明；明确支持单只股票全部历史 |
| [daily_basic](https://tushare.pro/document/2?doc_id=32) | 每日指标 / 交易日期 | `ts_code, trade_date` | 6000 行；官网允许按日期循环获取历史 |
| [stk_limit](https://tushare.pro/document/2?doc_id=183) | 涨跌停价格 / 交易日期 | `ts_code, trade_date` | 5800 行；总量不限制，可循环调用 |
| [suspend_d](https://tushare.pro/document/2?doc_id=214) | 停复牌 / 停复牌查询日期 | `ts_code, trade_date` | 未注明；可另选 `suspend_type`，并非每天都有事件 |
| [moneyflow](https://tushare.pro/document/2?doc_id=170) | 个股资金流向 / 交易日期 | `ts_code, trade_date` | 6000 行；数据始于 2010 年，股票和时间至少一个 |
| [margin](https://tushare.pro/document/2?doc_id=58) | 融资融券汇总 / 交易日期 | `exchange_id, trade_date` | 4000 行；无股票输入，保留交易所条件 |
| [margin_detail](https://tushare.pro/document/2?doc_id=59) | 融资融券明细 / 交易日期 | `ts_code, trade_date` | 6000 行；官网允许按日期循环 |
| [block_trade](https://tushare.pro/document/2?doc_id=161) | 大宗交易 / 交易日期 | `ts_code, trade_date` | 1000 行；同一股票同一天可能多条 |
| [slb_len](https://tushare.pro/document/2?doc_id=331) | 转融资汇总 / 交易日期 | `trade_date` | 5000 行；无股票输入，可循环获取历史 |
| [slb_sec](https://tushare.pro/document/2?doc_id=332) | 转融券汇总 / 交易日期 | `ts_code, trade_date` | 5000 行；官网目录标“停”，历史可用性待实测 |
| [slb_sec_detail](https://tushare.pro/document/2?doc_id=333) | 转融券明细 / 交易日期 | `ts_code, trade_date` | 5000 行；官网目录标“停”，历史可用性待实测 |
| [trade_cal](https://tushare.pro/document/2?doc_id=26) | 交易日历 / `cal_date` | `exchange, start_date, end_date` | 未注明；已提供区间；官网说明北交所日历参考沪深，输入交易所枚举未列 BSE |
| [new_share](https://tushare.pro/document/2?doc_id=123) | IPO 列表 / **上网发行日期** | `start_date, end_date` | 2000 行；已提供区间；不是按上市日期筛选 |

### 原生区间：公告日期（11 项）

| 接口（官网） | 数据 / 官网起止日期说明 | 当前参数 | 官网单次限量与注意事项 |
| --- | --- | --- | --- |
| [income](https://tushare.pro/document/2?doc_id=33) | 利润表 / 公告日开始、结束日期 | `ts_code, ann_date` | 未注明；普通接口只能按单只股票获取历史 |
| [balancesheet](https://tushare.pro/document/2?doc_id=36) | 资产负债表 / 公告日开始、结束日期 | `ts_code, ann_date` | 未注明；普通接口只能按单只股票获取历史 |
| [cashflow](https://tushare.pro/document/2?doc_id=44) | 现金流量表 / 公告日开始、结束日期 | `ts_code, ann_date` | 未注明；`f_ann_date` 是另一个输入参数，不能混为同一日期 |
| [fina_audit](https://tushare.pro/document/2?doc_id=80) | 审计意见 / 公告开始、结束日期 | `ts_code, ann_date` | 未注明；报告期另用 `period` |
| [forecast](https://tushare.pro/document/2?doc_id=45) | 业绩预告 / 公告开始、结束日期 | `ts_code, ann_date` | 3500 行；普通接口正文要求单只股票 |
| [express](https://tushare.pro/document/2?doc_id=46) | 业绩快报 / 公告开始、结束日期 | `ts_code, ann_date` | 未注明；普通接口只能按单只股票获取历史 |
| [repurchase](https://tushare.pro/document/2?doc_id=124) | 股票回购 / 公告开始、结束日期 | `ann_date` | 官网只明确“都不填，单次默认返回 2000 条”；不能据此推定指定区间后的硬上限；无股票输入 |
| [stk_managers](https://tushare.pro/document/2?doc_id=193) | 管理层 / 公告开始、结束日期 | `ts_code` | 未注明；当前快照入口可补日期区间 |
| [stk_holdernumber](https://tushare.pro/document/2?doc_id=166) | 股东户数 / 公告开始、结束日期 | `ts_code` | 3000 行；输入 `enddate` 才是单个截止日期，区别于区间参数 `end_date` |
| [stk_holdertrade](https://tushare.pro/document/2?doc_id=175) | 股东增减持 / 公告开始、结束日期 | `ts_code, ann_date` | 3000 行；不是按实际增减持起止日期筛选 |
| [pledge_detail](https://tushare.pro/document/2?doc_id=111) | 股权质押明细 / 公告开始、结束日期 | `ts_code` | 1000 行；输出中的 `start_date/end_date` 属于质押业务日期，不能用同名输出列校验公告范围 |

### 原生区间：报告期（4 项）

| 接口（官网） | 数据 / 官网起止日期说明 | 当前参数 | 官网单次限量与注意事项 |
| --- | --- | --- | --- |
| [fina_indicator](https://tushare.pro/document/2?doc_id=79) | 财务指标 / **报告期开始、结束日期** | `ts_code, ann_date` | 100 行；官网建议按日期多次请求；不能沿用公告日区间语义 |
| [fina_mainbz](https://tushare.pro/document/2?doc_id=81) | 主营业务构成 / **报告期开始、结束日期** | `ts_code, ann_date` | 100 行；官网无 `ann_date` 输入；另支持 `period` 和 `type=P/D/I` |
| [top10_holders](https://tushare.pro/document/2?doc_id=61) | 前十大股东 / **报告期开始、结束日期** | `ts_code, ann_date` | 未注明；官网示例的公告日可在请求区间之外 |
| [top10_floatholders](https://tushare.pro/document/2?doc_id=62) | 前十大流通股东 / **报告期开始、结束日期** | `ts_code, ann_date` | 未注明；同上，应按返回的报告期判断范围 |

以上 31 项中，26 项支持股票输入并沿用产品必填股票规则；`trade_cal`、`new_share`、`margin`、`slb_len`、`repurchase` 这 5 项不添加股票条件。第 6 个无股票输入接口 `index_classify` 不支持日期输入，见下文。

### 仅支持单日：应用逐日调用候选（3 项）

这里的批量方案是根据官网单日参数作出的工程建议，尚未实现或实测。应用可以接受用户区间，但发给上游的每个子请求仍使用官方单日参数，不能直接透传 `start_date/end_date`。

| 接口（官网） | 官网完整输入参数 | 当前参数 | 批量候选方式与限制 |
| --- | --- | --- | --- |
| [top_list](https://tushare.pro/document/2?doc_id=106) | `trade_date, ts_code` | `ts_code, trade_date` | 固定股票，按交易日遍历；`trade_date` 官网必填；单次最大 10000 行，历史始于 2005 年 |
| [dividend](https://tushare.pro/document/2?doc_id=103) | `ts_code, ann_date, record_date, ex_date, imp_ann_date` | `ts_code, ann_date` | 固定股票，按公告日遍历自然日；单次 2000 行，数据始于 2000-01-01；其他日期轴需另行明确 |
| [disclosure_date](https://tushare.pro/document/2?doc_id=162) | `ts_code, end_date, pre_date, ann_date, actual_date` | `ts_code, ann_date` | 沿用现有语义按“最新披露公告日”遍历自然日；单次 6000 行；`end_date` 是单个财报周期，不能当区间上界，也不能承诺取得所有历史计划版本 |

### 单截止日 / 单报告期：不计入原生区间（2 项）

| 接口（官网） | 官网完整输入参数 | 当前参数 | 结论与待验证项 |
| --- | --- | --- | --- |
| [pledge_stat](https://tushare.pro/document/2?doc_id=110) | `ts_code, end_date` | `ts_code` | `end_date` 仅写“截止日期”，单次 1000 行；示例结果呈周频，但不足以确定等值 / 截止筛选语义及完整日期枚举规则，需实测后再决定批量方式 |
| [stk_rewards](https://tushare.pro/document/2?doc_id=194) | `ts_code, end_date` | `ts_code` | `end_date` 是单个“报告期”，未注明单次行数；可研究逐报告期请求，但不能假设任意自然日起止范围都能完整覆盖 |

### 官网未声明日期输入（4 项）

| 接口（官网） | 官网完整输入参数 | 当前参数 | 结论 |
| --- | --- | --- | --- |
| [stock_basic](https://tushare.pro/document/2?doc_id=25) | `ts_code, name, market, list_status, exchange, is_hs` | `ts_code, list_status` | 基础信息快照；输出上市 / 退市日期不代表可按该日期下载 |
| [stock_company](https://tushare.pro/document/2?doc_id=112) | `ts_code, exchange` | `ts_code, exchange` | 公司信息快照；不加日期区间 |
| [index_classify](https://tushare.pro/document/2?doc_id=181) | `index_code, level, parent_code, src` | 无 | 行业分类字典；版本选择不等于日期区间 |
| [index_member_all](https://tushare.pro/document/2?doc_id=335) | `l1_code, l2_code, l3_code, ts_code, is_new` | `ts_code` | `is_new` 选择是否最新，输出纳入 / 剔除日期不代表支持日期输入 |

## 实施前必须保留的语义与限制

1. **日期轴必须显式区分。** 交易日、公告日、报告期、上网发行日期不是同一含义。尤其 `fina_indicator`、`fina_mainbz`、`top10_holders`、`top10_floatholders` 必须展示“报告期开始 / 结束日期”。官网 `top10_holders` 示例请求 `20170101～20171231`，结果包含 `ann_date=20180428, end_date=20171231`，这与报告期区间一致；按公告日二次过滤会误删数据。
2. **同名输入与输出不保证同义。** `pledge_detail` 的输入区间按公告日，输出起止日按质押业务；`stk_holdernumber` 的输入 `enddate` 是截止日，`end_date` 是公告区间终点，输出 `end_date` 又是统计截止日。响应范围验证必须按接口日期轴映射，不能通用地比较同名字段。
3. **区间模式不能残留旧单日必填条件。** 例如 `daily` 发起区间请求时，不应被迫同时提交 `trade_date`；公告区间同理。是替换原入口还是增加模式，留待方案确定。`fina_mainbz` 的无依据 `ann_date` 必须纠正，并与 ISSUE-017 的未完成项统一处理。
4. **达到行数上限不等于完整。** 原生区间优先，但需有切片或其他有依据的补齐方式。`fina_indicator/fina_mainbz` 只有 100 行，`block_trade/pledge_detail` 只有 1000 行；缩到同一天 / 同一报告期仍可能达到上限。此时应继续使用已验证的细分条件或明确报告“完整性未确认”，不能无限重试同一片段，也不能直接返回完整成功。
5. **分页、拆片与限流需要独立核验。** 本轮这 40 页输入表未列出 `limit/offset`，不能据此假设各接口都具有相同分页语义；若拟用通用分页须另补官方依据与实际验证。表中上限、更新时点与账户限频都要纳入策略；例如 `daily` 页写基础积分每分钟 500 次，三项 `slb_*` 页写 2000 积分每分钟 200 次、5000 积分 500 次，不能套统一并发数。
6. **切片要保持完整范围与股票约束。** 各请求保留同一股票 / 交易所条件；不得省略股票扩大为全市场，也不得自动切换 VIP 接口。公告日遍历不能只用交易日历；周/月线以实际最后交易日为准；区间端点是否包含、跨片重叠与去重都需验证。
7. **文档可读不等于数据仍可下载。** `slb_sec/slb_sec_detail` 在本次官网导航中标“停”，需用合适历史日期验证；不得承诺持续更新。`trade_cal` 当前产品含 BSE，但官网输入枚举未列 BSE，应先明确北交所日历取法再将它用于批量交易日枚举。
8. **批量结果必须如实反映完整性。** 当前链路是一次上游调用后统一入库。改为多片段后，要先确定事务范围、失败片段处理、统计口径与超时边界；任一片段失败或疑似截断不能把整段显示为成功。空片段应允许后续片段继续，重复或重叠区间应保持现有业务键幂等性。

## 调研时的处理建议

优先为上述 **31 项原生区间接口**补齐能力：保留已有 2 项，为缺失的 29 项补元数据、参数绑定和区间处理，并按实际日期轴设置文案和完整性规则。可先用 `daily` 验证股票交易日区间，用 `income` 验证公告区间，用 `fina_indicator` 验证报告期与低行数上限，用 `repurchase` 验证无股票区间，再覆盖完整清单。

调研时将另外 3 项列为逐日候选；2 项单截止日 / 报告期接口先验证，4 项无日期输入接口保持现有方式。当前方案已将 3 项逐日调用纳入草稿，具体方向见[方案草稿](../proposals/ISSUE-018-date-range-batch-downloads.md)，上线前仍需验证。

## 关闭条件

- 确定并记录最终支持清单；31 项原生区间均有实现与验收证据，或将明确不纳入的项目记录范围决定；其余 9 项的产品处理与上表分类一致。
- 对纳入范围的接口，日期区间从前端、请求类型、参数校验到上游调用完整贯通，日期轴标签准确；34 项股票必填与 6 项非股票接口规则保持成立。
- 验证同日、跨月 / 跨年、无效日期、起始晚于结束、缺少端点、空数据、股票条件及报告期 / 公告日不同的样例；确认上下边界和周/月线日期语义。
- 验证行数上限触发、切片覆盖、最小片段仍满额、限流或中途失败，以及重复下载的幂等与计数；不能把部分下载当作完整成功。
- `fina_mainbz` 参数偏差、停止更新接口历史可用性、未注明限量接口的完整性策略和日历取法均有明确处理结论。
- 通过与改动相关的后端参数 / 下载链路、前端表单、契约检查，并对纳入范围逐项记录真实 API 下载结果；文档调研和现有单日样例不能替代区间下载验收。
