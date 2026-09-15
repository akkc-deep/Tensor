# ISSUE-033：修复财务指标 RANGE 适配失败

## 当前状态

未开始，本次跳过（2026-09-15）。用户已明确fina_indicator及另三个接口的RANGE批量下载不纳入本次交付，见[范围决定](../proposals/ISSUE-026-range-scope.md)。本项保留为后续问题；原6项失败/未运行及v3撤回保持，不修复、不补验，也不作为本次母任务关闭的前置。

## 问题与证据

数据源`tushare_pro`，接口`fina_indicator`（财务指标）。原任务`506ba45f-ed85-483e-91ac-a132e183a6a1`、批次`8f26fa6f-9bac-4c82-a5e0-8a220049ed3e`，参数`{"ts_code":"000001.SZ","start_date":"20250331","end_date":"20251231"}`；run `issue026-range-dates-20260914T181447Z`。

任务/批次真实FAILED / ADAPTER_TYPE_INVALID，1请求/1尝试，SQL原键0→0、insert/update0。sourceRows0是成功计数，不能推断上游空响应。旧v2策略、包身份、来源绑定和失败schema已保留；当前RANGE已撤回为v3 / NEEDS_VERIFICATION，SINGLE仍可用。

现有安全日志不足以区分ValueConverter的类型/精度/日期转换失败与GenericDatasetAdapter的同原业务键内容冲突；原键为`ts_code,end_date,ann_date`。旧SOURCE只保存日期/行数投影，未保存足以重现的财务值或逐行键；旧成功SINGLE为空。没有依据放宽字段、精度或改业务键。

## 承接的固定样本

以下6项均保持原SOURCE、参数和REPORT_PERIOD/end_date语义；本表状态是原dates轮事实，不表示未来补验。完整SOURCE run均为`issue022-source-20260913T141544Z`，SOURCE case与原任务映射见原固定计划和唯一索引。

| 原TASK caseId | 股票 | start_date | end_date | 原状态 |
| --- | --- | --- | --- | --- |
| `issue026-issue022-indicator-000001-whole` | 000001.SZ | 20250331 | 20251231 | FAILED |
| `issue026-issue022-indicator-000001-lower` | 000001.SZ | 20250331 | 20250331 | NOT_RUN |
| `issue026-issue022-indicator-000001-upper` | 000001.SZ | 20251231 | 20251231 | NOT_RUN |
| `issue026-issue022-indicator-600000-whole` | 600000.SH | 20250331 | 20251231 | NOT_RUN |
| `issue026-issue022-indicator-600000-lower` | 600000.SH | 20250331 | 20250331 | NOT_RUN |
| `issue026-issue022-indicator-600000-upper` | 600000.SH | 20251231 | 20251231 | NOT_RUN |

## 处理范围与验收条件

1. 先取得可验证的安全适配分支证据或脱敏复现样本；若需要新的来源诊断，先完成本项明确取证方案。当前用户授权是跳过本接口，不包括先前待批准的额外诊断。
2. 基于根因最小修复，补原失败可复现的受控回归；不猜精度、不改原业务键掩盖冲突。保留SINGLE、字段/日期轴、内部100阈值和原工程采用合同。
3. 定向及受影响回归通过后递增候选版本，重新冻结源码/两包；按新的run/case映射、原SOURCE/参数、新空schema和固定预算补验6项，不覆盖已有FAILED/NOT_RUN。
4. 六项实际TASK、SQL原键/归属/写入、页面、批次及清洁身份全部合格并独立审查后才恢复正式AVAILABLE。未复现仅证明当次观察，不等于历史根因已解决。

## 关联

- [ISSUE-031设计与本次范围调整](../../task-designs/ISSUE-031-design.md)、[验收与失败证据](../../verification/ISSUE-031-range-live-task-verification.md)。
- [唯一索引](../../verification/ISSUE-018-range-acceptance.json)、[运行登记](../../verification/ISSUE-018-T14-runs.md)、[权威子看板](../../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)。
- 私有SQL`/private/tmp/issue026-range-dates-20260914T181447Z/sql-supplement.json`，schema `tensor_m14_t05_e866d8c7fdca2d11`。

本项尚未修复，不能声称全部原34个RANGE或278项通过；本次母任务已按明确决定调整为30个RANGE/251项纳入，四接口27项排除。ISSUE-033不再阻塞本次收尾，032仍按用户要求不开始。
