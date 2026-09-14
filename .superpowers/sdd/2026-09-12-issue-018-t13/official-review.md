# ISSUE-018-T13 官方证据复核

复核对象为 `docs/task-designs/ISSUE-018-T13-design.md`、`docs/verification/ISSUE-018-range-acceptance.md` 与 `/private/tmp/issue018-t13-official` 中的 40 份公开页面 HTML/正文及 `index.json`。本次未联网、未调用数据 API，也未使用任何样本行数证明完整性。

## 总体结论

- `index.json` 恰有 40 项，验收 Markdown 恰有 40 个同名接口章节。40/40 的 URL、获取 UTC、正文 SHA-256 与 HTML SHA-256 均和索引一致。
- 验收 Markdown 的 40 条“正文准确引文”和“输入表准确引文”拆成 189 个分号分隔片段后，189/189 都是对应缓存正文的逐字子串；未发现伪引文或串页。Markdown 写作 `/tmp/issue018-t13-official`，本次审计使用其规范路径 `/private/tmp/issue018-t13-official`。
- 22 个候选数值规则中，19 页已经给出足以采用设计中 ROW_LIMIT 判定的公开合同：正文明确使用“最大/最多”，并且接口输入提供相应日期范围或单日参数；其中多数还明确写了“总量不限制”或“可循环”。该证据只建立行数截断边界 `L`：原始响应 `<L` 没有触及该边界，`=L` 仍须拆分；它不证明账户权限、日期筛选实际生效、历史窗口可用或 SOURCE/TASK 成功。
- 其余 3 个候选值仍含糊：`daily` 只写“每次6000条”，`forecast` 只写“单次3500行”，`dividend` 只写“单次查询返回2000行”。它们没有写“最大/最多”，不能在严格合同下把该数值当截断边界。数据样例不能补齐这个缺口。
- 设计列出的 11 个 UNKNOWN 确实仍缺公开行数截断或其他完整提取规则：`adj_factor`、`suspend_d`、`income`、`balancesheet`、`cashflow`、`fina_audit`、`express`、`repurchase`、`stk_managers`、`top10_holders`、`top10_floatholders`。其中 `repurchase` 的 2000 仅适用于所有参数都省略时的默认返回，不能成为指定公告区间的 `L`。
- 两处语义需修正或拆开记录：`monthly` 的正文与同页样例冲突；`trade_cal` 已给出北交所参照沪深日历的官方语义，但没有把 `BSE` 列为合法输入。详见专项结论。

## 22 个 ROW_LIMIT 候选

| API | 缓存页准确引文 | 日期/输入语义与决定 | 官方截断合同 |
| --- | --- | --- | --- |
| `daily` | “每次6000条数据，一次请求相当于提取一个股票23年历史”；“停牌期间不提供数据” | 输入有 `ts_code/trade_date/start_date/end_date`，输出轴为 `trade_date`；停牌缺行决定受正文直接支持。 | **含糊**。没有“最大/最多”，6000 不能严格解释为截断边界。 |
| `weekly` | “单次最大6000行，可使用交易日期循环提取，总量不限制” | `trade_date` 是“每周最后一个交易日期”，输入也有 `start_date/end_date`；不固定周五的决定正确。 | **足够，L=6000**。 |
| `monthly` | “单次最大4500行，总量不限制”；输入称 `trade_date` 为“每月最后一个交易日日期” | **缓存内冲突**：同页样例含 `20180930`（周日）、`20180630`（周六）、`20171231`（周日）等自然月末。当前“实际每月最后交易日，不能固定自然月末”不能算已核实，须由真实 SOURCE 或新增官方说明裁决。 | **足够，L=4500**；只解决截断，不解决日期轴冲突。 |
| `daily_basic` | “单次请求最大返回6000条数据，可按日线循环提取全部历史” | 输入表同时出现 `ts_code str Y 股票代码（二选一）` 与 `trade_date ...（二选一）`，并列 `start_date/end_date`；本地坚持单股票是产品子集。 | **足够，L=6000**。 |
| `stk_limit` | “单次最多提取5800条记录，可循环调取，总量不限制” | 输入范围是 `start_date/end_date`，输出 `trade_date`；页面还列输出交易所 `SSE/SZSE/BSE`。 | **足够，L=5800**。 |
| `moneyflow` | “数据开始于2010年。 限量：单次最大提取6000行记录，总量不限制” | 输入 `ts_code/trade_date/start_date/end_date`，输出 `trade_date`；2010 是公开历史起点，仍须 SOURCE 验证所选股票样本。 | **足够，L=6000**。 |
| `margin` | “单次请求最大返回4000行数据，可根据日期循环” | `exchange_id` 明列 `SSE/SZSE/BSE`，日期轴 `trade_date`；BSE 是该接口的公开合法输入，仍要单列真实返回归属。 | **足够，L=4000**。 |
| `margin_detail` | “单次请求最大返回6000行数据，可根据日期循环” | 输入 `ts_code/trade_date/start_date/end_date`；正文标题描述“沪深两市”，同时更新注记提到北交所，不能从页面独立推断所有 BSE 股票支持。 | **足够，L=6000**。 |
| `block_trade` | “单次最大1000条，总量不限制” | 输入按 `trade_date`；输出含 `buyer/seller`，同页样例在 `20181227` 对 `601318.SH` 重复多条，支持“不能按股票+日期先去重”的决定。 | **足够，L=1000**。 |
| `slb_len` | “单次最大可以提取5000行数据，可循环获取所有历史” | 公开输入无股票，只有 `trade_date/start_date/end_date`，故“不套股票校验”正确。页面输出仅 `trade_date/ob/auc_amount/repo_amount/repay_amount/cb`，没有期限字段；设计中的“期限/规模多行”不受该页支持，应改为融资汇总行并由 SOURCE 核对实际基数。 | **足够，L=5000**。 |
| `slb_sec` | “单次最大可以提取5000行数据，可循环获取所有历史” | 输入和输出轴均为 `trade_date`；HTML 侧栏逐字为“转融券交易汇总(停）”。正文没给停用日或可调用历史窗口，同页样例日期 `20240620` 也不能证明现在可用。 | **足够，L=5000**；接口可用性/历史窗口仍阻塞开放。 |
| `slb_sec_detail` | “单次最大可以提取5000行数据，可循环获取所有历史” | HTML 侧栏逐字为“转融券交易明细(停）”；输出另含 `tenor/fee_rate`。正文没给停用日或可调用窗口，样例 `20240620` 不足以证明持续或历史可用。 | **足够，L=5000**；接口可用性/历史窗口仍阻塞开放。 |
| `new_share` | “单次最大2000条，总量不限制” | 输入是“上网发行开始日期/结束日期”，输出 `ipo_date` 是“上网发行日期”，而 `issue_date` 是“上市日期”；设计选择 `ipo_date` 正确。 | **足够，L=2000**。 |
| `forecast` | “单次3500行” | `start_date/end_date` 是公告区间，输出轴 `ann_date`。官方允许 `ts_code` 与公告日期二选一，并提示普通接口按单只股票取历史；本地必填单股票是更窄的产品规则。 | **含糊**。没有“最大/最多”或总量/循环措辞。 |
| `stk_holdernumber` | “单次最大3000,总量不限制” | `enddate` 是筛选“截止日期”；`start_date/end_date` 是公告区间；输出 `end_date` 又是“截止日期”。设计按 `ann_date` 做范围轴的区分正确。 | **足够，L=3000**。 |
| `stk_holdertrade` | “单次最大提取3000行记录，总量不限制” | 输入范围是公告 `start_date/end_date`，输出另有 `begin_date/close_date` 表示增减持业务日期；按 `ann_date` 验证范围正确。 | **足够，L=3000**。 |
| `pledge_detail` | “限量：单次最大1000” | 输入 `start_date/end_date` 是公告区间，输出另有质押 `start_date/end_date`；范围轴应为 `ann_date`，不能拿输出质押日期做边界。 | **足够，L=1000**。正文没有“总量不限制”，但“单次最大”已明确本次响应的截断边界；真实日期拆分仍必须验证。 |
| `fina_indicator` | “每次请求最多返回100条记录，可通过设置日期多次请求获取更多数据” | 输入 `start_date/end_date` 明称报告期，输出轴 `end_date`；公告日可在请求报告期之外。 | **足够，L=100**。 |
| `fina_mainbz` | “单次最大提取100行，总量不限制，可循环获取” | `start_date/end_date` 是报告期；`type` 只列 `P/D/I`，没有省略时默认值。未传 `type` 的分类集合必须真实核验，且不能用三次 P/D/I 请求拼接。 | **足够，L=100**；默认类型仍独立阻塞开放。 |
| `top_list` | “单次请求返回最大10000行数据，可通过参数循环获取全部历史” | 唯一必填上游日期是单日 `trade_date`；本地按完整交易日历逐日规划与页面相容。BJ 日历处理见下文。 | **足够，L=10000**。 |
| `dividend` | “起始时间2000-01-01，单次查询返回2000行” | 上游没有范围参数，只有精确 `ann_date` 等至少一个条件；本地按自然日逐日传 `ann_date` 合理，不能换成交易日历。 | **含糊**。没有“最大/最多”，2000 不能严格作为截断边界。 |
| `disclosure_date` | “单次最大6000，总量不限制” | 上游 `ann_date` 是“最新披露公告日”，`end_date` 是财报周期；本地自然日枚举应传 `ann_date`，且页面的 `modify_date` 只表明存在修正记录，不能承诺回放全部历史版本。 | **足够，L=6000**。 |

## 11 个 UNKNOWN

| API | 缓存页准确引文 | 日期/输入语义与决定 |
| --- | --- | --- |
| `adj_factor` | “可提取单只股票全部历史复权因子，也可以提取单日全部股票的复权因子” | 输入有 `start_date/end_date`、输出 `trade_date`，但全文无单次最大值、分页或其他完整性规则。“全部历史”描述查询能力，不定义响应是否截断；保持 UNKNOWN 正确。 |
| `suspend_d` | “按日期方式获取股票每日停复牌信息” | `start_date/end_date` 是“停复牌查询”区间，输出 `trade_date` 是“覆盖从停牌到覆盖期间的连续日期”；全文无限量规则，稀疏/空结果不能证明完整，保持 UNKNOWN。 |
| `income` | “当前接口只能按单只股票获取其历史数据” | `start_date/end_date` 明称公告日区间，输出同时含 `ann_date/f_ann_date/end_date`；范围轴选 `ann_date` 正确，但无完整性规则。 |
| `balancesheet` | “当前接口只能按单只股票获取其历史数据” | `start_date/end_date` 是公告日区间，`period`/输出 `end_date` 是报告期；范围轴选 `ann_date` 正确，保持 UNKNOWN。 |
| `cashflow` | “当前接口只能按单只股票获取其历史数据” | 页面同时支持 `ann_date` 与 `f_ann_date`，但 `start_date/end_date` 明称公告日区间；设计不以 `f_ann_date` 代替 `ann_date` 正确，保持 UNKNOWN。 |
| `fina_audit` | “获取上市公司定期财务审计意见数据” | `start_date/end_date` 是公告区间，`period` 是报告期；范围请求不传 `period`、按 `ann_date` 核验与页面相容，但无完整性规则。 |
| `express` | “当前接口只能按单只股票获取其历史数据” | `start_date/end_date` 是公告区间，输出轴 `ann_date`；无最大行数或循环规则，保持 UNKNOWN。 |
| `repurchase` | “ann_date ...（任意填参数，如果都不填，单次默认返回2000条）” | `start_date/end_date` 是公告区间，且没有股票输入；2000 明确只约束所有参数都不填的默认请求，不能升级成区间 `L`。多股票输出是合法形状。 |
| `stk_managers` | “股票代码，支持单个或多个股票输入”；输入另列公告 `start_date/end_date` | 页面无最大行数。SINGLE 快照与公告 RANGE 是两种请求形状；范围完整性保持 UNKNOWN，且本地仍应限制单股票。 |
| `top10_holders` | `start_date/end_date` 为“报告期开始日期/结束日期” | 输出同时有公告 `ann_date` 与报告期 `end_date`；范围轴选报告期 `end_date` 正确，公告日可越界，无完整性规则。 |
| `top10_floatholders` | `start_date/end_date` 为“报告期开始日期/结束日期” | 与 `top10_holders` 相同；范围轴为报告期 `end_date`，无完整性规则。 |

## `trade_cal` 与 6 个 SINGLE

| API | 缓存页准确引文 | 结论 |
| --- | --- | --- |
| `trade_cal` | “三大交易所的交易日历都是一样的，北交所交易日历参考上交所和深交所”；输入 `exchange` 只列 `SSE/SZSE/CFFEX/SHFE/CZCE/DCE/INE` | 需要拆开两件事：页面已给出 BJ 使用沪深日历的官方语义依据，因此不应笼统记成“BJ/BSE 都无依据”；但输入枚举没有 `BSE`，所以不能声称 `exchange=BSE` 可直接调用，也不能声称响应会返回 `BSE`。页面还没有明确保证请求闭区间“每自然日恰一条”；SSE/SZSE 完整覆盖、休市行和直接 BSE 均须 SOURCE 取证。若实现 BJ 映射，需记录选用 SSE 或 SZSE 的确定规则，并保留返回日历的完整自然日校验。 |
| `pledge_stat` | 输入 `ts_code` 与 `end_date 截止日期` | `end_date` 是单个截止日，没有开始日期；页面不足以构造 RANGE。保持 `SINGLE_ONLY` 合理。 |
| `stk_rewards` | 输入 `ts_code` 与 `end_date 报告期` | 单报告期不是区间；保持 `SINGLE_ONLY` 合理。 |
| `stock_basic` | `list_status` 默认 `L`；正文称“调取一次就可以拉取完” | 无日期输入；本地 `ts_code+list_status` 目标一致是产品约束，仍需真实归属验证。`SINGLE_ONLY` 正确。 |
| `stock_company` | 输入 `ts_code` 与 `exchange ... SSE/SZSE/BSE` | 无日期输入；本地同时校验股票与交易所是更严的产品约束，需 SOURCE 验证。`SINGLE_ONLY` 正确。 |
| `index_classify` | 缓存正文没有“输入参数”表 | 无股票/日期入口，作为字典 SINGLE 合理。 |
| `index_member_all` | `ts_code str N 股票代码；is_new str N 是否最新（默认为“Y是”）` | `ts_code` 是股票而行业代码是另一个筛选维度；页面没有日期范围。保留默认最新归属的 `SINGLE_ONLY` 决定正确。 |

## 对现有验收文档的具体修正建议

1. 不要对所有候选数值页统一写“仍须结合可核验截断合同”。上表 19 页的缓存正文已经提供 ROW_LIMIT 所需的明确“最大/最多”边界；应在结果索引中记录对应官方引文，并继续把 `sourceStatus/taskStatus` 留为 `NOT_RUN`，`disposition` 留为 `NEEDS_VERIFICATION`，直到真实证据完成。
2. `daily`、`forecast`、`dividend` 的候选数值继续保留为候选或 UNKNOWN，不把其样例、默认/单次数量解释成 `<L` 完整性合同。
3. 将 `monthly` 标为“官方页面内部日期语义冲突”，不能继续把“实际每月最后交易日”写成已确认事实。
4. 将 `trade_cal` 拆成“BJ 参照沪深日历有官方依据”和“BSE 直接输入未列、仍未验证”两条；官方参照语义不能替代闭区间逐自然日 SOURCE 完整性验证。
5. 两项 `slb_sec` 页面分别有侧栏 `(停）`，正文同时仍写“可循环获取所有历史”。这只建立历史查询意图和 ROW_LIMIT，不给停用日期，也不证明当前账户可读取任何历史窗口；继续要求有依据的历史窗口与实际可用范围。
6. `fina_mainbz` 的 ROW_LIMIT 可由官方页确认为 100，但省略 `type` 的默认集合仍完全未定义；两件事须分字段记录，不能因前者成立而开放。

## 完成摘要

官方缓存与验收引文的身份和逐字内容全部一致。40 项均已给出逐项结论；19 个 ROW_LIMIT 候选具备公开截断合同，3 个候选仍含糊，11 个 UNKNOWN 仍缺完整性规则，`trade_cal` 是独立 CALENDAR_COVERAGE。没有任何结论把样例当完整性证明，也没有把官方页证据当成真实 SOURCE/TASK 通过。
