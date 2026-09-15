# ISSUE-026：本次区间下载范围调整

## 决策记录

2026-09-15，用户明确：

> 我明确告诉你本次区间下载中不需要支持这四个接口的批量下载了，先把这些问题跳过，然后不开始32

“四个接口”指Tushare Pro（`tushare_pro`）的以下接口，仅排除本次RANGE区间批量下载，不取消SINGLE单次下载：

| 接口 | 名称 | 原固定TASK项数 | 保留的原结果与问题 |
| --- | --- | ---: | --- |
| balancesheet | 资产负债表 | 8 | 1 FAILED/7 NOT_RUN；原业务键的版本冲突未修复 |
| cashflow | 现金流量表 | 8 | 1 FAILED/7 NOT_RUN；原业务键的版本冲突未修复 |
| repurchase | 股票回购 | 5 | 1 FAILED/4 NOT_RUN；记录身份冲突未修复 |
| fina_indicator | 财务指标 | 6 | 1 FAILED/5 NOT_RUN；ADAPTER_TYPE_INVALID根因未定位，保留ISSUE-033 |

## 本次适用范围

- 当前交付目标为30个RANGE接口、251项固定TASK。原278项保留总追踪，分为251项通过与27项经用户明确排除；不把排除项记为PASS，不删除或重标任何历史run/case。
- 唯一验收索引使用既有`EXCLUDED`处置标记四接口，形成30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY。`rangeTarget=true`保留原34项目录矩阵身份，当前是否纳入由`disposition`表达；不将四接口伪装为原六个SINGLE_ONLY。
- 四接口运行能力继续为`NEEDS_VERIFICATION`、`tushare-range-v3`，RANGE拒绝，40接口SINGLE入口保留。本次范围决定不修改业务键、字段、适配器、生产策略、历史任务或包身份，也不恢复候选。
- 原11个RESPONSE_ONLY规则及历史合同保留，本次纳入8个，另外3个被排除；严格规则纳入21个ROW_LIMIT和1个CALENDAR_COVERAGE。原278输入、旧SOURCE、SQL、失败/空/未执行记录和全部成功数据继续保留。

## 任务边界

- ISSUE-031按调整后的251项验收收尾；三个冲突接口的21项不再构成本次阻塞，财务指标6项也不再是本次母任务关闭的前置。
- ISSUE-033保留为未开始的后续问题，本次跳过。重新纳入任何接口时，先明确对应冲突/身份或适配规则，再按原证据安排修复和补验；本次不调用、诊断或重试这四接口。
- ISSUE-032保持NOT_STARTED；不准备READY、不生成后继交接、不执行最终六门禁。已有设计草稿仅作历史准备资料，用户再次要求启动后再按本决定修订。
- ISSUE-026、T13/T14和ISSUE-017/018本次不关闭。未来关闭合同中的全量RANGE目标按30纳入/4明确排除/6 SINGLE_ONLY、251通过/27排除核对；所有纳入项的真实证据、SINGLE、兼容性、六门禁和独立终审仍须满足。

本决定限定替代共享设计、T13/T14、总体设计§6、母issue及031/032/033相关文档中“全部34 RANGE/278项必须通过”“仅财务指标可递延”“四接口问题仍阻止本次关闭”及自动推进032的要求；其余合同继续适用。旧阶段数字与阻塞记录保留为历史，不再作为当前执行要求。

## 证据入口

- [唯一验收索引及逐接口报告](../../verification/ISSUE-018-range-acceptance.md)。
- [ISSUE-031实际TASK/SQL、诊断和范围收尾记录](../../verification/ISSUE-031-range-live-task-verification.md)。
- [权威子看板](../../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)。

## 2026-09-15后续启动与收尾

用户随后要求“完成issue32”，只取代上述不启动032的时序决定，四接口RANGE排除范围继续生效。032已完成六门禁与独立终审，T14/T13、017/018及026按原合同和本范围限定收尾，详见[最终验收](../../verification/ISSUE-032-range-final-closure.md)。033保持NOT_STARTED；原27项问题未修复、不计PASS。
