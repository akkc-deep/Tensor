# ISSUE-019：确认三接口限量规则并补齐来源证据

## 当前状态

COMPLETED，已解决（2026-09-13）。用户明确同意[方案A](../proposals/ISSUE-019-documented-range-limits.md#决策记录)，正式索引采用daily6000、forecast3500、dividend2000的ROW_LIMIT工程阈值；T13/T14已记录仅这三项的采用例外。该决定不是新增上游截断保证。

独立SOURCE两轮38case/50请求：28项非空PASS、10项空对照EVIDENCE_MISSING，0请求失败/未执行，退出与清理均通过。daily两股票整段/端点/重叠、forecast两股票历史公告及官方额外样例、dividend两股票公告与周六自然日场景均有真实证据；全部10轮/513case及空状态保留。

决定后78项Node证据测试、160项相关Maven测试通过，独立预期先RED后GREEN。[ISSUE-026输入](ISSUE-026-range-task-final-acceptance.md#issue-019-已交付输入)已交付28项精确参数与SOURCE绑定、空对照保留方式及候选版本/任务/SQL要求，并通过现有消费者的离线匹配检查。专属[设计](../../task-designs/ISSUE-019-design.md)与[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-019-方案a确认与最终验收)已链接看板。

本issue的规则与来源范围已完成；三接口生产sourceVerified=false、v1、NEEDS_VERIFICATION保持，未新建TASK/SQL或开放RANGE。ISSUE-026负责后续真实任务验收；母issue未关闭。

## 目标与范围

明确官方 6000 / 3500 / 2000 行说明的采用口径，补齐三接口可用于 RANGE 的来源证据。

主责接口：`daily`、`forecast`、`dividend`。

## 创建时问题与证据（历史）

- daily：原文“每次6000条数据”；旧 SOURCE 有数据，但原轮失败；真实重叠下载与更新尚未验证。
- forecast：原文“单次3500行”；固定样本为空，需要有公告依据的非空样本。
- dividend：原文“单次查询返回2000行”；按自然日逐日查询，历史观察含 20260815 周六，缺可核验公告事件依据。

来源：[当前验收报告](../../verification/ISSUE-018-range-acceptance.md)、[唯一 JSON 索引](../../verification/ISSUE-018-range-acceptance.json)、[公开补证](../../verification/ISSUE-018-T14-official-evidence.md)。这些是已有事实，创建本 issue 没有产生新的 API / TASK / SQL 结果。

## 依赖与处理顺序

无其他新 issue 的完成依赖；消费 T14 既有设计和证据。串行顺序由看板规定。具体顺序为 ISSUE-019 → ISSUE-020 → ISSUE-021 → ISSUE-022 → ISSUE-023 → ISSUE-024 → ISSUE-025 → ISSUE-026；不得将顺序误作已经完成的上游输入。

本issue已完成；其规则/来源/任务参数输入由ISSUE-026在既定顺序到达后消费。规则采用决定不解除其他issue的依赖，也不要求重复本issue已完成的账户请求。

## 关闭条件

- 三项限量的适用请求、依据、采用口径和异常处理明确；用户决策与上游事实分开记录。
- 固定新 SOURCE 计划后取得有效整段、边界、两股票及非交易日公告证据；失败与空样本如实保留。
- 已有真实结果只追加，新规则不得单独把接口标为 AVAILABLE；将匹配的 RANGE TASK / SQL 验收输入交给 ISSUE-026。

## 约束

沿用 [T14 设计](../../task-designs/ISSUE-018-T14-design.md)和 [T13 验收合同](../../task-designs/ISSUE-018-T13-design.md)。规则解释、默认参数、业务键、市场映射或历史承诺的实质变更须先记录依据及必要的用户决策；真实执行前完成本 issue 的专属设计。保留旧轮次和未验证状态，不在 issue 文档另造验收索引，不因拆分关闭 ISSUE-017 / ISSUE-018。
