# ISSUE-021：验证交易所日历与交易日下载

## 当前状态

COMPLETED，已完成（2026-09-13）。用户明确要求“完成issue21”；已按[专属设计](../../task-designs/ISSUE-021-design.md)完成三项关闭条件，并记录NOT_STARTED → READY → IN_PROGRESS → COMPLETED。用户要求将 T14 剩余问题拆为多个 issue 并挨个解决。准确任务状态见[后续 issue 看板](../../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md)。本 issue 承接缺口，不代表 T14 已完成。

## 目标与范围

明确沪深完整日历、北交所映射以及交易日枚举，补齐交易所和非空证券样本。

主责接口：`trade_cal`、`margin`、`top_list`。

## 初始问题与证据（历史）

- trade_cal 的 BSE 旧请求报 BATCH_COMPLETENESS_UNCONFIRMED；不能据此断言 HTTP 不支持或原始响应为空。
- 官网说北交所参考沪深日历，但直接输入枚举未列 BSE；新增 SSE 三日样本不足以覆盖全部市场和边界。
- margin 的新 BSE SOURCE 已返回 6 行；仍缺独立 SZSE 与完整边界。
- top_list 全休市区间 0 开市日 / 0 证券子请求已通过；SH 6 个交易日证券数据为空，SZ 非空与 BJ 规划待验。

来源：[当前验收报告](../../verification/ISSUE-018-range-acceptance.md)、[唯一 JSON 索引](../../verification/ISSUE-018-range-acceptance.json)、[公开补证](../../verification/ISSUE-018-T14-official-evidence.md)。这些是已有事实，创建本 issue 没有产生新的 API / TASK / SQL 结果。

## 依赖与处理顺序

无其他新 issue 的完成依赖；消费 T14 既有设计和证据。串行顺序由看板规定。具体顺序为 ISSUE-019 → ISSUE-020 → ISSUE-021 → ISSUE-022 → ISSUE-023 → ISSUE-024 → ISSUE-025 → ISSUE-026；不得将顺序误作已经完成的上游输入。

第一动作：核对 TushareTradeCalendar 与 exchangeForStock 的拒绝路径，依据官方日历说明提出可区分 BSE 直接输入与 BJ 映射的固定验证计划。

## 关闭条件

- SSE / SZSE 完整区间、两端和全休市场景有有效证据；BJ 映射及 BSE 直接输入分别形成结论。
- 若映射需要代码变化，先依据事实修订对应设计；margin 保留 exchange_id，交易所归属正确。
- top_list 按完整日历枚举，取得非空两股票及边界证据；准备 ISSUE-026 的匹配任务计划。

## 约束

沿用 [T14 设计](../../task-designs/ISSUE-018-T14-design.md)和 [T13 验收合同](../../task-designs/ISSUE-018-T13-design.md)。规则解释、默认参数、业务键、市场映射或历史承诺的实质变更须先记录依据及必要的用户决策；真实执行前完成本 issue 的专属设计。保留旧轮次和未验证状态，不在 issue 文档另造验收索引，不因拆分关闭 ISSUE-017 / ISSUE-018。

## 本轮结论与交付（2026-09-13）

[三轮固定计划与实际结果](../../verification/ISSUE-018-T14-runs.md#issue-021-日历交易所与交易日独立取证)已追加唯一索引，共39case/63请求，38 PASS、1 BSE直接完整性FAILED，三个cleanup PASS；无TASK/SQL。

- SSE/SZSE共14项完整日历覆盖整段、两端、国庆休市、跨年和BJ事件窗口，逐自然日唯一无缺失，成对开市日期一致。
- BSE直接请求本次合法结构响应0行，导致BATCH_COMPLETENESS_UNCONFIRMED；不代表HTTP不支持，也不改写旧失败的未知响应事实。直接BSE生产RANGE继续拒绝。
- 根据官网同历说明，BJ→SSE测试候选以920008.BJ完成非空整段、事件单日及上下边界；股票请求保留BJ，休市窗口0证券子请求。该映射不扩大存储范围、不放宽完整性；生产实现留给ISSUE-026随候选包及任务验证。
- margin三交易所整段各6行，两端各1行，exchange_id和归属保持。top_list的000007.SZ/600318.SH两股票也各取得非空整段/事件/上下边界及全休市证据，全部按完整日历枚举。

[ISSUE-026已交付输入](ISSUE-026-range-task-final-acceptance.md#issue-021-已交付输入)固定38项来源绑定、规则和任务观察，单独保留BSE拒绝负例。三接口仍NEEDS_VERIFICATION/v1、生产BJ仍拒绝；本issue负责的规则/语义/来源验证与后续真实任务验收分开，母issue未关闭。

最终验证：相关Maven170/170、最终Node78/78通过，两次隔离acceptance构建通过；38项TASK候选与真实SOURCE逐一绑定，BSE负例拒绝，旧12轮/531case及其他37接口保留。独立最终复审确认三项关闭条件满足；结果表两格旧状态已修正并核对全部40行与JSON一致。详见[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-021-最终验收)。下一项ISSUE-022未启动，不创建占位设计或交接。
