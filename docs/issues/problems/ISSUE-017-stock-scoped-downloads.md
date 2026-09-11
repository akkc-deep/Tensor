# ISSUE-017：支持股票参数的接口必填股票，其余保留原下载方式

## 当前状态

处理中（2026-09-11）。[26 项必填股票参数方案](../proposals/ISSUE-017-required-stock-parameters.md)已实施：34 项必须指定股票，6 项保留原下载方式；参数绑定、响应归属、前后端回归及生产构建已通过，见[实施与验证记录](../../verification/ISSUE-017-stock-scoped-downloads.md)。`fina_mainbz` 的 SINGLE 参数已由 [ISSUE-018-T06](../../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t06) 纠正为仅 `ts_code`，自动化验证通过。母 issue 暂不关闭：新请求的真实下载验收和默认 type 语义仍待完成。

用户最新要求：**支持股票输入的 34 个接口下载时必须指定股票；不支持股票输入的 6 个接口保留现有下载入口及参数方式。** 对支持股票输入的接口，股票条件必须传入上游请求，不能下载全市场后在本地筛选，也不能只在数据查看页面筛选。当前 40 个接口均继续提供下载，不新增全局股票必填规则。

## 调研范围与结论

以 [manifest.json](../../data-template/manifest.json) 和 [生产 YAML](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro) 的当前 40 项为范围，源码基线为 `5e5f956`。2026-09-11 逐页读取下表链接对应的 Tushare 官方公开文档，核对接口名称、**输入参数**、限制及示例。40 页均取得对应接口正文；本轮没有调用真实数据 API，因此结论是文档能力，不能作为账户权限或实际返回范围的验收证据。

| 分类 | 数量 | 结论 |
| --- | --- | --- |
| 官网明确支持股票输入，项目已声明必填 `ts_code` | 8 | 保留，并核对其余参数语义 |
| 官网明确支持股票输入，项目未声明 `ts_code` | 26 | 补齐股票下载参数，并在产品侧设为必填 |
| 官网输入参数未声明股票条件 | 6 | 保留现有下载入口和原参数，不添加股票条件 |
| 合计 | 40 | 34 项必须指定股票，6 项按原方式下载 |

此前永久移除的 `top_inst`、`broker_recommend`、`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`hsgt_top10`、`namechange` 不属于本轮范围，也不因本 issue 恢复支持。见 [ISSUE-008](ISSUE-008-tushare-live-coverage-gap.md)。本次不扩展到 Tushare 全站其他接口。

## 改造前现状与原因（调研基线）

- 40 份 YAML 中只有 `income`、`balancesheet`、`cashflow`、`fina_indicator`、`fina_audit`、`fina_mainbz`、`stk_rewards`、`stk_holdernumber` 声明下载参数 `ts_code`，且均为 `TS_CODE / required: true`；其余 32 项没有股票下载参数。
- 例如 [daily.yaml](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/daily.yaml) 只声明 `trade_date`，[stock_basic.yaml](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/stock_basic.yaml) 只声明 `list_status`，[index_member_all.yaml](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/index_member_all.yaml) 的 `parameters` 为空，尽管这三项的官网均支持 `ts_code` 输入。
- [TushareProPlugin](../../../data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java) 从 YAML 生成下载接口元数据；[DynamicParameterForm.vue](../../../control-plane/src/components/download/DynamicParameterForm.vue) 只渲染元数据声明的参数。因此未声明的股票条件不会出现在下载表单中。
- [ParameterValidator](../../../data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java) 拒绝未声明参数；[TushareProClient](../../../data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java) 将已验证参数传给上游。不能仅在前端加输入框或手工向现有请求追加 `ts_code`。
- YAML 的 `filters: [ts_code, ...]` 属于本地数据查询能力，不代表下载支持该参数。当前上游响应校验也没有检查每行股票代码是否属于请求目标，见 [TushareResponseValidator](../../../data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponseValidator.java)。

## 官网逐项核对

下表“Y / N”保留官网输入参数表的必选标记；N 表示官网允许省略，**不影响 Tensor 对这 34 个支持股票输入的接口要求必填股票代码**。“当前下载参数”来自生产 YAML，不是官网完整参数列表。

### 已有股票下载参数：8 项

| 接口（官网） | 数据 | 官网 `ts_code` 必选 | 当前下载参数 |
| --- | --- | --- | --- |
| [income](https://tushare.pro/document/2?doc_id=33) | 利润表 | Y | `ts_code, ann_date` |
| [balancesheet](https://tushare.pro/document/2?doc_id=36) | 资产负债表 | Y | `ts_code, ann_date` |
| [cashflow](https://tushare.pro/document/2?doc_id=44) | 现金流量表 | Y | `ts_code, ann_date` |
| [fina_indicator](https://tushare.pro/document/2?doc_id=79) | 财务指标 | Y | `ts_code, ann_date` |
| [fina_audit](https://tushare.pro/document/2?doc_id=80) | 财务审计意见 | Y | `ts_code, ann_date` |
| [fina_mainbz](https://tushare.pro/document/2?doc_id=81) | 主营业务构成 | Y | `ts_code, ann_date`，其中 `ann_date` 官网未声明，见下文 |
| [stk_rewards](https://tushare.pro/document/2?doc_id=194) | 管理层薪酬与持股 | Y | `ts_code` |
| [stk_holdernumber](https://tushare.pro/document/2?doc_id=166) | 股东户数 | N | `ts_code` |

### 官网支持、项目缺少股票下载参数：26 项

以下每项的官网输入参数均明确包含 `ts_code`，应补为产品必填项。

| 接口（官网） | 数据 | 官网 `ts_code` 必选 | 当前下载参数 |
| --- | --- | --- | --- |
| [stock_basic](https://tushare.pro/document/2?doc_id=25) | 股票基础信息 | N | `list_status` |
| [stock_company](https://tushare.pro/document/2?doc_id=112) | 上市公司基本信息 | N | `exchange` |
| [stk_managers](https://tushare.pro/document/2?doc_id=193) | 上市公司管理层 | N | 无 |
| [daily](https://tushare.pro/document/2?doc_id=27) | 日线行情 | N | `trade_date` |
| [weekly](https://tushare.pro/document/2?doc_id=144) | 周线行情 | N，代码与交易日任选一 | `trade_date` |
| [monthly](https://tushare.pro/document/2?doc_id=145) | 月线行情 | N，代码与交易日任选一 | `trade_date` |
| [adj_factor](https://tushare.pro/document/2?doc_id=28) | 复权因子 | N | `trade_date` |
| [daily_basic](https://tushare.pro/document/2?doc_id=32) | 每日估值与市场指标 | Y，但描述为与交易日二选一 | `trade_date` |
| [stk_limit](https://tushare.pro/document/2?doc_id=183) | 每日涨跌停价格 | N | `trade_date` |
| [suspend_d](https://tushare.pro/document/2?doc_id=214) | 每日停复牌 | N | `trade_date` |
| [moneyflow](https://tushare.pro/document/2?doc_id=170) | 个股资金流向 | N，股票和时间至少一个 | `trade_date` |
| [top_list](https://tushare.pro/document/2?doc_id=106) | 龙虎榜每日明细 | N | `trade_date`，官网仍要求交易日必填 |
| [margin_detail](https://tushare.pro/document/2?doc_id=59) | 融资融券交易明细 | N | `trade_date` |
| [block_trade](https://tushare.pro/document/2?doc_id=161) | 大宗交易 | N，股票和日期至少一个 | `trade_date` |
| [slb_sec](https://tushare.pro/document/2?doc_id=332) | 转融券交易汇总 | N | `trade_date`；官网目录标“停” |
| [slb_sec_detail](https://tushare.pro/document/2?doc_id=333) | 转融券交易明细 | N | `trade_date`；官网目录标“停” |
| [forecast](https://tushare.pro/document/2?doc_id=45) | 业绩预告 | N，表格写代码与公告日二选一 | `ann_date`；正文提示普通接口只能按单只股票获取 |
| [express](https://tushare.pro/document/2?doc_id=46) | 业绩快报 | Y | `ann_date` |
| [dividend](https://tushare.pro/document/2?doc_id=103) | 分红送股 | N | `ann_date` |
| [disclosure_date](https://tushare.pro/document/2?doc_id=162) | 财报披露计划 | N | `ann_date` |
| [stk_holdertrade](https://tushare.pro/document/2?doc_id=175) | 股东增减持 | N | `ann_date` |
| [top10_holders](https://tushare.pro/document/2?doc_id=61) | 前十大股东 | Y | `ann_date` |
| [top10_floatholders](https://tushare.pro/document/2?doc_id=62) | 前十大流通股东 | Y | `ann_date` |
| [pledge_stat](https://tushare.pro/document/2?doc_id=110) | 股权质押统计 | N | 无 |
| [pledge_detail](https://tushare.pro/document/2?doc_id=111) | 股权质押明细 | N | 无 |
| [index_member_all](https://tushare.pro/document/2?doc_id=335) | 申万行业成分与所属分类 | N | 无 |

### 官网未声明股票输入：6 项

这六项保留现有下载入口与下表中的当前参数，不要求传股票代码。其中 `new_share`、`repurchase` 的输出包含 `ts_code`，但官网输入表和示例没有股票筛选依据；不能仅凭输出列给请求增加股票约束，也不能对其正常多股票结果执行单股票归属校验。将来若获得新的官方股票输入依据，再重新核验是否调整参数合同。

| 接口（官网） | 数据 | 官网完整输入参数 | 当前下载参数及处理依据 |
| --- | --- | --- | --- |
| [trade_cal](https://tushare.pro/document/2?doc_id=26) | 交易日历 | `exchange, start_date, end_date, is_open` | 当前 `exchange, start_date, end_date`；按交易所提供日历，无股票条件 |
| [new_share](https://tushare.pro/document/2?doc_id=123) | IPO 新股发行信息 | `start_date, end_date` | 当前同左；按上网发行日期提供列表，股票代码仅在输出中 |
| [repurchase](https://tushare.pro/document/2?doc_id=124) | 股票回购 | `ann_date, start_date, end_date` | 当前 `ann_date`；股票代码仅在输出中，示例也是按日期调用 |
| [margin](https://tushare.pro/document/2?doc_id=58) | 融资融券交易汇总 | `trade_date, start_date, end_date, exchange_id` | 当前 `exchange_id, trade_date`；继续提供交易所汇总，个股明细另用 `margin_detail` |
| [slb_len](https://tushare.pro/document/2?doc_id=331) | 转融资交易汇总 | `trade_date, start_date, end_date` | 当前 `trade_date`；期限与规模汇总，无股票条件 |
| [index_classify](https://tushare.pro/document/2?doc_id=181) | 申万行业分类字典 | `index_code, level, parent_code, src` | 当前无参数；`index_code` 是行业指数代码，个股所属行业可用 `index_member_all(ts_code=...)` |

## 改造时必须保留的接口差异

1. **指定股票不等于只传股票。** `top_list` 官网要求 `trade_date` 必填，因此应传 `ts_code + trade_date`。周线/月线的 `trade_date` 分别表示每周/每月最后交易日。不能统一删除所有日期条件，也不能给所有接口套同一套日期参数。
2. **`fina_mainbz` 存在额外的参数合同偏差。** 项目目前要求 `ann_date` 并标记 `queryMode: ann_date`；官网输入只有 `ts_code, period, type, start_date, end_date`，日期范围含义为报告期。后续需移除这个无官网依据的下载参数并校正查询模式/说明，按需要提供官网支持的报告期筛选；不能沿用“公告日下载成功”的历史样例来证明此参数有效。
3. **财报接口应保持单只股票范围。** `income`、`balancesheet`、`cashflow`、`fina_indicator`、`fina_mainbz`、`forecast`、`express` 的官网正文提示普通接口按单只股票获取，全市场季度数据另有 VIP 接口。后续不得因空结果或权限错误自动切换 VIP 或省略股票代码。`forecast` 输入表与正文对可省略股票条件的描述并不一致，但两者均支持指定股票，产品侧必填可避免该歧义。
4. **`index_member_all` 可以直接按股票取所属行业。** 官网原文为“也可按股票代码提取所属分类”，输入名是 `ts_code`；`l1_code/l2_code/l3_code` 是行业代码，不能当股票条件。`is_new` 默认为 Y，最新归属与历史归属应在参数说明中区分。
5. **`slb_sec` 与 `slb_sec_detail` 有股票参数，但官网目录标“停”。** 本轮将其计入文档支持的 34 项，只说明可按股票请求的参数依据；是否仍可取得历史数据需后续用合适历史日期验收，不能承诺持续更新，也不能把当前日期无数据误判为不支持股票参数。`slb_len` 与这两项不同，没有股票输入。
6. **辅助条件应与目标股票一致。** `stock_basic` 官网的 `list_status` 默认为 L，目标为退市/暂停上市股票时需匹配状态；`stock_company` 的 `exchange` 也不能与股票冲突。这些条件不能替代 `ts_code`。官网明确允许多代码的个别接口也不意味着全部 34 项都支持相同的批量语法，建议先沿用单值 `TS_CODE` 实现每次一只股票。
7. **指定股票仍有返回行数上限。** 例如 `fina_indicator`、`fina_mainbz` 文档各写单次最多 100 行。后续如提供历史范围下载，须按该接口真实日期语义拆分并保持股票条件，明确实际覆盖范围，不能将一次成功等同于完整历史下载。

## 后续处理方向

本节记录满足用户要求的边界，具体设计和实施计划按[本地问题流程](../README.md)后续形成。

- 为缺失的 26 项补充必填 `ts_code`，保留已有 8 项的必填约束。前端元数据、表单校验和后端参数校验保持一致；这 34 项收到空值、空白值或缺失股票代码时不能触发上游下载。
- `trade_cal`、`new_share`、`repurchase`、`margin`、`slb_len`、`index_classify` 保留原下载入口及参数。下载和数据查看均保持当前 40 项注册范围；数据库迁移与已有数据保留。
- 直接给已有 `TradeDateParameters`、`ExchangeParameters`、`ListStatusParameters` 增加 `tsCode`，另为 `slb_len` 新增只含 `tradeDate` 的 `TradeDateOnlyParameters`；同步调整 Codec。其余 5 项非股票接口继续复用原类型，详见方案中的映射表。
- 对支持股票输入的 34 项，从入口、上游请求到入库保持同一目标股票。重试、按日期分段和未来批量任务的每次上游请求都必须保留明确股票条件；不得在失败、空结果时退回无股票请求。返回其他股票时应明确报错并阻止该批入库，不能静默筛掉其他股票后声称完成指定股票下载。6 项原方式下载不应用股票必填或股票归属校验。
- 修正 `fina_mainbz` 的参数偏差，保留各接口必需的日期与报告期语义。优先复用现有 YAML、`TS_CODE`、动态表单和参数校验链路，避免为这次范围调整引入额外通用框架。
- 同步更新下载 API 合同、当前运行样例、接口描述、测试和执行下载的脚本，验证支持清单仍为 40 项。manifest 与历史样例中的原抓取参数、时间和数据保持其来源含义；新请求样例另行提供，不能把旧全市场样例改写为单股票验收证据。

## 关闭条件

- [x] 下载及数据查看继续覆盖当前 40 项：34 项必填股票，6 项保留原方式，不因缺少股票输入能力停用接口。
- [x] 对支持股票输入的 34 项验证：表单要求股票代码，后端拒绝缺失/空白/非法代码；失败请求对上游和数据库均零访问。
- [x] 对 6 项原方式下载验证：页面可选且不出现股票必填项；合法旧请求能够到达上游，仍不携带 `ts_code`；原参数校验继续生效，合法汇总及多股票结果正常处理。
- [x] 三个已有 record 增加 `tsCode`，`slb_len` 使用新增日期专用 record；全部 40 项的参数结构与类型唯一匹配，读写往返不丢字段、不注入股票字段。
- [x] 检查 34 项股票下载实际发出的上游请求，确认 `params.ts_code` 为用户指定股票；合法重试及分段请求仍保留该值，没有无股票回退路径。只检查本地查询过滤条件不算通过。
- [ ] 对股票下载以两只不同股票分别验证，返回及本次入库的行均属于对应股票；混入其他股票的模拟响应被拒绝且整批不入库。合法空结果不触发扩大范围请求。
- [ ] `top_list` 必填交易日、`fina_mainbz` 报告期参数、`index_member_all` 股票/行业代码区别等差异均有对应验证；长区间结果不得无依据宣称完整。
- [ ] 为 34 项股票下载记录带股票条件的真实验收及结果范围，并为 6 项原方式下载记录对应回归证据；权限不足、停止更新、没有合适历史样本等情况如实记录，不能用官网支持或旧验收替代真实通过。
- [ ] 元数据、接口合同、当前下载样例、前后端回归及相关契约校验与新范围一致，更新本 issue 状态并链接验收记录。
