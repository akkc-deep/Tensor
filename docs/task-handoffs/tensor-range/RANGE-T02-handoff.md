# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`
- **Completed task:** `RANGE-T01`，看板已记录 COMPLETED。
- **Next task:** `RANGE-T02`，按大于前项的最小未完成 Order=2 选择。
- **Design document:** `docs/task-designs/RANGE-T02-design.md`，已完成并链接在本项看板行。
- **Expected next status:** `READY`；写入并链接本交接后执行 `NOT_STARTED -> READY`，准备不表示研究已启动。

## Next Task

`RANGE-T02`：19 项适用日历与来源核实。

目标：为 19 个交易日期接口确定实际必要市场／业务日历和可靠性判据，供后续日历实现、合同及来源验证消费。

范围：C-A／C-M／C-S／C-N／C-X 的实际覆盖、权威来源、互联互通方向、转融通等价依据、覆盖和更新规则。保留已有市场条件，不以单一 SSE、普通港股日历或周末过滤替代，不做提供器实现或真实业务调用。

验收：19 项分别有来源、必要集合、并集规则、覆盖／新鲜度判据、状态和缺口；未确认明确 `CALENDAR_UNCONFIRMED`、零业务请求，不算作休市过滤已可用。区分停牌与休市，非交易日期不套屏障。AC-PRD-RANGE-02／15／16／17／18 来源前提可追溯，ISSUE-008 排除保持。实际报告和 JSON 尚未创建或验证，下一位直接按完成的研究设计执行。

## Dependencies

None。看板未声明 T02 的直接任务依赖；共享需求和本地定义按下节读取，不把 T01 已完成当作适用日历已确认。以下共享规则一致：逐项来源核实、全部必要日历确认后才能计算开盘并集、未知时拒绝业务执行；未发现需要改变研究范围或流程的输入冲突。

## Start Here

按顺序读取：

1. [完成的 T02 设计](../../task-designs/RANGE-T02-design.md)，路径 `docs/task-designs/RANGE-T02-design.md`。
2. [权威看板](tensor-range-task-board.md) 的 RANGE-T02 行和详情，核对实际状态与本交接。
3. [PRD v1.4 §4及附录 A.1](../../design/Tensor_区间下载_PRD_v1.0.md)：19 项映射、并集与未确认规则。
4. [TRD v1.10 §5.1、§13.2](../../design/Tensor_区间下载_TRD_v1.0.md)：本轮重新确认、仅同轮复用、精确重试范围及排除边界。
5. [trade_cal.yaml](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/trade_cal.yaml)：原 exchange 枚举、日历输出和业务键；枚举本身不证明上游支持。
6. [ISSUE-008](../../issues/problems/ISSUE-008-tushare-live-coverage-gap.md)：9 项“不依赖，未解决，用户后续单独处理”，真实调用不恢复。
7. [既有官方调研](../../research/2026-09-08-tushare-range-batch-research.md) 的原接口链接，仅用于定位公开资料，不消费其已过时事务讨论。

取得本任务的明确启动请求并记录 `READY -> IN_PROGRESS` 后，第一步按设计固定 19 个 apiName／calendarProfile 到 `docs/research/RANGE-T02-calendar-evidence.json` 骨架，从 `trade_cal` 原官方输入表对 SSE／SZSE／BSE 的逐项依据核读开始，写入可追溯原文和缺口，再按各原接口范围查证必要市场及权威日历。研究结构、失败规则、输出路径和校验命令已经确定，不需要重新创建设计。

## Risks

- 原 `trade_cal` 本地枚举与来源实际可支持市场必须独立核实；不能由业务接口支持 BSE 推断日历也支持。
- 19 项实际市场、C-S 等价及 C-N／C-X 方向仍是本任务待研究事实；未知集合或缺日不能算作休市。
- 年度休市表、业务停止更新、股票停牌和普通港股交易日历均不能单独证明业务适用日期及新鲜度；资料冲突按未确认处理。
- ISSUE-008 中本任务涉及的 hk_hold、hsgt_top10、moneyflow_hsgt、top_inst 只做公开资料核实，不安排真实调用。研究登记不能关闭后续来源及功能验收缺口。
