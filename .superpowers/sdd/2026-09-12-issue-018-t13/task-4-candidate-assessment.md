# Task 4 — 最终 SOURCE 轮候选评估

**本轮可直接进入本地候选开放：0 项。** 完整运行门禁未满足；下表的成功事实不能被剪裁、重包装成另一个退出 0 的 run。所有 34 项 RANGE 仍须保持 NEEDS_VERIFICATION，6 项既定 SINGLE_ONLY 不变。这里不修改源码、策略、索引或执行计划。

## 最终运行身份

已在 root 通知结束后重新读取最终安全 `source-run.json` / `source-evidence.json`，两文件的 cases 完全一致，替代评估过程中的中间 checkpoint：

- runId：`issue018-t13-source-20260912T123840Z`。
- 运行：`2026-09-12T17:18:49.462Z` 至 `2026-09-12T17:27:07.915Z`；exitCode **1**；cleanup **PASS**，完成于 `2026-09-12T17:27:08.002Z`。
- 181 cases、244 请求：88 PASS、89 EVIDENCE_MISSING、1 FAILED、3 NOT_RUN。
- 失败：`trade_cal-bse-direct`，固定 `BATCH_COMPLETENESS_UNCONFIRMED`。安全投影仅说明日历覆盖未确认；不能从成功行计数 0 推断原始响应必为空，更不能断言 BSE HTTP 不支持。
- 未执行：`top_list-closed-calendar`、`top_list-sh-calendar`、`margin-bse-direct`；保持 null 请求/行数。
- 最终 source-run SHA-256：`140c881f692f3a5d6b99563b0790d7dd1f005260a5a0362a6dd009804d62e10b`。
- 最终 source-evidence SHA-256：`e1565de8e064aaa8d809c962d9af79e5c4ba4e0e6c6de4adca1811f5a93c81ac`。

文件在 `/private/tmp/issue018-t13-source-20260912T123840Z/`。依据为绑定 T13 设计与 `official-review.md` 的逐项官方合同复核；未读取原始响应或凭证。SOURCE PASS 是已检查的非空参数/字段/范围/股票语义观察，始终独立于完整性与 TASK 验收。

## 有效事实最多的后续候选素材

以下均受上述运行失败门禁阻止。若未来由 root 依设计登记并授权另一轮有效运行，不能仅复制本轮 PASS 或换一个 runId 解决门禁。表中 TASK caseId 是**后续对应方案的引用素材，不是当前可提交 TASK 的授权清单**。两股票 SINGLE 的通用精确命名为 `<api>-single-000001` / `<api>-single-600000`；它们证明各自股票 SINGLE 观察，不能冒充第二股票 RANGE 证据。

| API / 官方合同 | 本轮聚合事实及剩余限制 | 后续 TASK 对应的具体 SOURCE caseId |
| --- | --- | --- |
| daily_basic / L6000 | 最强候选素材。两股票 SINGLE 各 1 行；000001.SZ 整段 6 行、两端各 1 行，trade_date 集合与区间一致，均未触限。仍缺有效 SOURCE run 身份、候选包 TASK/SQL 与设计要求的跨年代表闭环。 | `daily_basic-range`, `daily_basic-lower-bound`, `daily_basic-upper-bound` |
| stk_limit / L5800 | 同样具备两股票 SINGLE、6 行整段与两端非空观察。不能扩大为所有交易所股票已实测。 | `stk_limit-range`, `stk_limit-lower-bound`, `stk_limit-upper-bound` |
| moneyflow / L6000 | 同样具备两股票 SINGLE、6 行整段与两端非空观察。官方历史起点 2010；本轮只核验所选 2026 样本，不能宣称全历史已逐日实测。 | `moneyflow-range`, `moneyflow-lower-bound`, `moneyflow-upper-bound` |
| margin_detail / L6000 | 同样具备两股票 SINGLE、6 行整段与两端非空观察；不把沪深样本推成北交所实际可用证明。 | `margin_detail-range`, `margin_detail-lower-bound`, `margin_detail-upper-bound` |
| weekly / L6000 | 两股票 SINGLE 非空；短窗 1 行，7–8 月宽窗 9 个实际日期。固定两端是周一，均 EVIDENCE_MISSING；这可如实记录稀疏边界观察，不能称两端 SOURCE PASS。观察日期恰为周五，不能推导“所有周五”算法或证明节假日周的实际最后交易日。作为后续候选素材有价值，最终记录须保留此范围限制。 | `weekly-range`, `weekly-period-last-trading-day`；两端仅失败于证据充足性，不列入 PASS 选中集 |
| margin / L4000 | SSE SINGLE 1 行、整段 6 行、两端各 1 行且 exchange_id 保留，具备 SSE 候选素材。`margin-bse-direct` 未运行；本轮不证明 BSE，也无独立 SZSE 来源样本。不能把后续 SSE TASK 说成全交易所验收完成。 | `margin-range`, `margin-lower-bound`, `margin-upper-bound` |
| new_share / L2000 | 无股票输入；SINGLE 1 行、月窗 19 行、上端 1 行，验证轴为 ipo_date；下端空仍 EVIDENCE_MISSING。可作为后续 ipo_date 区间候选素材；安全投影没有 issue_date 对照，不能声称本轮已经观察到两个日期列不同的原始事实。 | `new_share-range`, `new_share-upper-bound` |
| stk_holdernumber / L3000 | 两股票 snapshot 各 149/129 行；公告窗仅 1 行、实际 ann_date=20260815，两端均空。支持范围过滤的有限观察，但没有非空公告端点，也没有该行 end_date 对照；后续应明确保留公告轴证据范围，不能把 snapshot 两股票当成两股票 RANGE 边界证明。 | `stk_holdernumber-range`（目前只是独立来源事实） |
| trade_cal / CALENDAR_COVERAGE | SSE/SZSE 整段各 8 个唯一自然日、6 个开市日；两所休市窗各 2 行且 0 开市，SSE 两端非空。沪深完整覆盖事实成立。BSE 直接调用覆盖校验失败，必须保留生产 BSE 直接输入拒绝；BJ 参照沪深虽有官方依据，本轮未证明 BJ 规划，不可顺带添加映射。 | `trade_cal-range`, `trade_cal-lower-bound`, `trade_cal-upper-bound`, `trade_cal-szse-calendar`, `trade_cal-szse-closed`, `trade_cal-sse-closed`；绝不把 `trade_cal-bse-direct` 放入 PASS 集 |

上述前四项的 SOURCE 日期/两端/两股票素材最充分。其余五项保留表中明确限制；退出 0 本身也不能消除这些语义或样本缺口。任何未来本地候选开放还必须按设计逐项更新版本/证据，绑定新构建，并以真实 TASK 页面/完整叶子/SQL 与查询证据决定最终 AVAILABLE；当前尚无此 TASK 结论。

## 其余 11 个明确 ROW_LIMIT 接口

19 个明确 ROW_LIMIT 接口中，上表含 8 个；下表补足 11 个。它们除了运行门禁，还存在下述实质缺口。

| API / L | 结论与真实缺口 | 保留的 SOURCE caseId / 后续用途 |
| --- | --- | --- |
| monthly / 4500 | 阻止。两股票 SINGLE、短窗与两端均空；宽窗虽有 20260731、20260831 两行，两天都是工作日自然月末，无法裁决“最后交易日 vs 周末自然月末”的官方冲突。需要具有区分力且有依据的样本，不自动换日期。 | `monthly-period-last-trading-day` 仅支持这两个实际日期；无可直接 TASK 开放的完整依据 |
| block_trade / 1000 | 阻止。两股票 SINGLE、整段与两端全空，没有同股同日多条及 buyer/seller 区分的真实证据。 | `block_trade-range`, `block_trade-lower-bound`, `block_trade-upper-bound` 均 EVIDENCE_MISSING |
| slb_len / 5000 | 阻止。SINGLE、整段与两端全空；融资汇总实际基数未观察，不能按“期限多行”旧描述推断。 | `slb_len-range`, `slb_len-lower-bound`, `slb_len-upper-bound` 均 EVIDENCE_MISSING |
| slb_sec / 5000 | 阻止。全部固定样本空；官方标停，没有已核定停用日/可访问历史窗口。 | `slb_sec-range` 及两端、两股票 SINGLE 均为空证据 |
| slb_sec_detail / 5000 | 阻止。与 slb_sec 相同，且无明细 tenor/fee_rate 的非空真实观察。 | `slb_sec_detail-range` 及两端、两股票 SINGLE 均为空证据 |
| stk_holdertrade / 3000 | 阻止。全部固定样本空，无法观察公告日与增减持业务起止日的区别。 | `stk_holdertrade-range` 及两端均 EVIDENCE_MISSING |
| pledge_detail / 1000 | 阻止。仅 600000.SH snapshot 有 2 行；000001.SZ snapshot 与全部 RANGE 空。SH snapshot 不能替代公告区间或质押业务日期区分。 | `pledge_detail-single-600000` 是独立 SINGLE 事实；RANGE 暂无 PASS |
| fina_indicator / 100 | 阻止。000001.SZ 报告年窗 5 行、上端 1 行；两股票 SINGLE 都空，无非空 SH 样本。安全投影只含 end_date，未记录设计要求的 ann_date 在报告区间外的真实判别事实。 `<100/=100/>100` 仍引用 T12 受控证据，不要求刷满账户。 | `fina_indicator-range`, `fina_indicator-upper-bound` 可保留报告轴观察，不能单独完成特殊轴/两股票条件 |
| fina_mainbz / 100 | 阻止。未传 type 的范围 74 行、上端 37 行；默认分类集合仍无依据。两股票 SINGLE 各 **150 行**，已高于官方“单次最大100”；SINGLE/RANGE 形状可能不同，但本轮不能自行解释或静默忽略，需明确阈值合同适用范围。没有业务分类/键摘要可证明默认涵盖 P/D/I 哪些类别。 | `fina_mainbz-single-000001`, `fina_mainbz-single-600000`, `fina_mainbz-range`, `fina_mainbz-upper-bound` 仅事实保留；禁止混 type 拼接补齐 |
| top_list / 10000 | 阻止。两股票 SINGLE 空；SZ RANGE 日历确认 6 开市日但证券全空。完整休市零叶子与 SH 日历编排两个 case 均 NOT_RUN，不能借 trade_cal 的休市成功代填 top_list 成功；trade_cal 开放依赖也未闭环。 | `top_list-range` EVIDENCE_MISSING；`top_list-closed-calendar`, `top_list-sh-calendar` NOT_RUN |
| disclosure_date / 6000 | 阻止。31 个自然日全部执行且全空，两股票 SINGLE 也空；没有“最新公告”非空样本与同业务键修订复查，不能宣称全部历史版本可回放。 | `disclosure_date-range` EVIDENCE_MISSING（31 请求/31 成功空叶子）；无可直接用于候选开放的 PASS |

## 3 个数值合同仍含糊的接口

| API | 最终观察与阻止原因 | SOURCE 引用 |
| --- | --- | --- |
| daily | 两股票、整段、两端、重叠和跨年均非空，来源语义素材丰富；官方“每次6000”仍不是确定的截断合同。不能用样本成功填完整性缺口；本轮也无 TASK/SQL 重叠更新证据。 | `daily-range`, `daily-lower-bound`, `daily-upper-bound`, `daily-overlap`, `daily-cross-year` |
| forecast | 全固定样本空，且“单次3500行”合同含糊。 | `forecast-range` 及两端、两股票 SINGLE |
| dividend | RANGE 31 日执行得到 2 行，实际公告日 20260815 为周六，确有非交易日日期观察；但缺可复核事件依据/业务语义，两股票 SINGLE 空，且2000并非明确最大值。不得因自然日样本成功而开放。 | `dividend-range`；节点中邻日已请求的事实不等于公告内容已复查 |

## 11 个 UNKNOWN：逐项保留 NEEDS_VERIFICATION

| API | 来源事实与额外缺口 | SOURCE 引用 |
| --- | --- | --- |
| adj_factor | 两股票 SINGLE、整段和两端非空；仍无完整提取规则。 | `adj_factor-range`, `adj_factor-lower-bound`, `adj_factor-upper-bound` |
| suspend_d | 全固定样本空；无完整性规则，也无实际停牌连续覆盖样本。 | `suspend_d-range` 及两端 |
| income | 公告窗 2 行、实际日20260815；两股票 SINGLE 和两端空。除规则未知，投影没有报告期对照，不能声称代表性 ann_date/end_date 区分已完成。 | `income-range` |
| balancesheet | 公告窗2行；两股票 SINGLE及两端空，完整性未知。 | `balancesheet-range` |
| cashflow | 公告窗1行；两股票 SINGLE及两端空，完整性未知，未观察 f_ann_date 对照。 | `cashflow-range` |
| fina_audit | 全固定样本空；完整性未知。 | `fina_audit-range` 及两端 |
| express | 全固定样本空；完整性未知。 | `express-range` 及两端 |
| repurchase | 非股票公告窗852行、两端44/10行、SINGLE27行；多股票为合法输入形状，但安全投影没有实际股票集合。默认2000不能用作指定区间阈值。 | `repurchase-range`, `repurchase-lower-bound`, `repurchase-upper-bound` |
| stk_managers | 两股票 snapshot非空；公告窗2行、下端1行、上端空；仍无完整性规则。 | `stk_managers-range`, `stk_managers-lower-bound` |
| top10_holders | 报告窗40行、上端10行；两股票 SINGLE与下端空。规则未知，不能以典型10条构造完整性。 | `top10_holders-range`, `top10_holders-upper-bound` |
| top10_floatholders | 报告窗40行、上端10行；两股票 SINGLE与下端空。规则未知，不能以典型10条构造完整性。 | `top10_floatholders-range`, `top10_floatholders-upper-bound` |

## 6 个 SINGLE_ONLY：不扩大 RANGE

| API | 最终 SOURCE 事实 | 可保留的精确 caseId |
| --- | --- | --- |
| pledge_stat | 两股票各643/628行；只是截止日/快照形状。 | `pledge_stat-single-000001`, `pledge_stat-single-600000` |
| stk_rewards | 两股票各1428/1066行；不据此推导区间。 | `stk_rewards-single-000001`, `stk_rewards-single-600000` |
| stock_basic | 两股票各1行，输入list_status=L；投影无返回状态值对照，不能新增“目标状态已复查”的声明。 | `stock_basic-single-000001`, `stock_basic-single-600000` |
| stock_company | 两股票各1行，股票/交易所匹配检查通过。 | `stock_company-single-000001`, `stock_company-single-600000` |
| index_classify | 字典359行，无股票/日期参数。 | `index_classify-single` |
| index_member_all | 两股票各1行；默认最新归属是官方输入语义，投影未提供历史归属对照。 | `index_member_all-single-000001`, `index_member_all-single-600000` |

## 交给 root 的决定边界

本评估不提出启动 RANGE TASK、不改任何 sourceVerified、不修改 UNKNOWN、不重试、不换样本，也不从失败轮挑 PASS 另建成功 run。未来新轮如需成立，先按设计记录其独立目的、固定样本与依据，处理 BSE 失败的真实含义及剩余证据需求；root 保留所有运行与实施决定。跨年成功目前只有 daily，不能借给其他接口冒充其真实跨年 TASK 证明；至少一项最终开放接口的跨年 SOURCE/TASK代表要求仍需闭合。清理 PASS只证明清理完成，不修复退出1或任何语义缺口。独立的默认 SINGLE TASK 轮不依赖 RANGE SOURCE 准入；root 已通知它按原设计启动，本评估不把 SOURCE RANGE 的失败扩大为禁止该独立 SINGLE 轮。

本评估只读绑定设计、官方复核及安全 JSON，执行的本地脚本仅聚合已有事实和计算文件摘要。无测试、网络、SQL、凭证读取、源码编辑或子代理。只写此报告，由 root 统一加入 Git。
