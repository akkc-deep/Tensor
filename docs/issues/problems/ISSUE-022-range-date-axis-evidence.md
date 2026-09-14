# ISSUE-022：补齐日期口径与区间边界证据

## 当前状态

COMPLETED，已解决（2026-09-13）。用户明确“完成issue22”，依[专属设计](../../task-designs/ISSUE-022-design.md)及[实施计划](../../superpowers/plans/2026-09-13-issue-022.md)完成两轮来源、日期对照、边界和交付验收；独立复审无剩余发现，记录NOT_STARTED → READY → IN_PROGRESS → COMPLETED。准确状态见[后续issue看板](../../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md)。本项完成不代表T14或母issue已完成。

## 目标与范围

验证周期行情、财务报告期、公告日与新股申购日的真实筛选含义。

主责接口：`weekly`、`monthly`、`fina_indicator`、`stk_holdernumber`、`new_share`。

## 启动前问题与证据（历史保留）

- weekly 固定边界为周一且为空，缺节假日周最后交易日样本。
- monthly 新样本已证明 000001.SZ / 201809 返回 20180928，20180930 为空；缺第二股票、其他月份和完整边界。
- fina_indicator 两股票新样本 5 / 4 行，各有报告期在内而公告日在外的记录；两端待补。
- stk_holdernumber 只有 20260815 公告样本，缺同一行统计截止日对照和两端。
- new_share 按 ipo_date（申购日）筛选；缺下边界及同一行 issue_date 对照。

来源：[当前验收报告](../../verification/ISSUE-018-range-acceptance.md)、[唯一 JSON 索引](../../verification/ISSUE-018-range-acceptance.json)、[公开补证](../../verification/ISSUE-018-T14-official-evidence.md)。这些是已有事实，创建本 issue 没有产生新的 API / TASK / SQL 结果。

## 依赖与处理顺序

无其他新 issue 的完成依赖；消费 T14 既有设计和证据。串行顺序由看板规定。具体顺序为 ISSUE-019 → ISSUE-020 → ISSUE-021 → ISSUE-022 → ISSUE-023 → ISSUE-024 → ISSUE-025 → ISSUE-026；不得将顺序误作已经完成的上游输入。

第一动作：从已有 monthly 和 fina_indicator 有效样本出发，结合官方公开样例登记缺失的第二股票、特殊日期与边界计划。

## 关闭条件

- 周期行情用真实最后交易日，不硬编码周五或自然月末。
- 报告期、公告日、统计截止日、申购日和上市日通过同一行日期对照区分。
- 各接口补足两股票或原非股票方式的有效整段 / 边界 SOURCE，向 ISSUE-026 提供固定任务输入。

## 约束

沿用 [T14 设计](../../task-designs/ISSUE-018-T14-design.md)和 [T13 验收合同](../../task-designs/ISSUE-018-T13-design.md)。规则解释、默认参数、业务键、市场映射或历史承诺的实质变更须先记录依据及必要的用户决策；真实执行前完成本 issue 的专属设计。保留旧轮次和未验证状态，不在 issue 文档另造验收索引，不因拆分关闭 ISSUE-017 / ISSUE-018。

## 本轮已取得的证据（2026-09-13）

- 两轮39case/39次真实来源请求，35非空PASS（含4项辅助日历），4空对照EVIDENCE_MISSING；两个exit0/cleanup PASS、输入稳定。运行计划、官方缓存核对、完整隔离源码及实际两包身份、逐case安全结果全部追加[原运行登记](../../verification/ISSUE-018-T14-runs.md#issue-022-第一轮固定来源计划)，唯一JSON现17轮/609case/581请求，旧15轮/570case不改。
- weekly两股票整段20240927/20240930/20241011和两端、国庆周一最后交易日有效；monthly两股票20180928/20181031及两端有效。完整SSE/SZSE日历按周/月取最后开市日，均与实际主轴相同；20241004周五与20180930自然月末空对照原样保留。
- fina_indicator两股票报告期整段5/4行、两端非空，公告在外同一行证据成立；stk_holdernumber两股票公告20260815/20260828的整段、单日及上下边界窗口共8项有效，截止日在外；new_share原方式整段10行/两端1、2行，同一行申购与上市日期有效不同，整段2行上市在外。
- [ISSUE-026已接收35项固定候选](ISSUE-026-range-task-final-acceptance.md#issue-022-已交付输入)，当前消费者精确绑定两轮来源，4空对照拒绝；没有提交TASK或连接数据库。
- 仅最小扩展测试Probe的日期整数投影及离线测试。专项Maven173/173、隔离acceptance1072后端/包和468前端检查通过，最终Node78/78；旧源码/两包/历史对象及40行报告一致性核对成立。生产五接口仍NEEDS_VERIFICATION/v1，真实RANGE TASK/SQL归ISSUE-026，母issue及T13/T14保留剩余验收。

## 关闭验收

三项关闭条件均成立：周期日期由真实完整日历核对；五类业务日期具备同一行对照；两股票/原非股票有效整段与边界及固定TASK输入已交付。独立最终复审确认无Critical/Important/Minor剩余，Node78/78及diff检查通过；曾发现的当前缺口旧文字已同步并复核。详见[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-022-最终验收)。本项记录IN_PROGRESS → COMPLETED，未启动ISSUE-023。
