# ISSUE-020：厘清主营业务构成默认分类与数量限制

## 当前状态

COMPLETED，已按用户确认方案A完成（2026-09-13）。专属[取证设计](../../task-designs/ISSUE-020-design.md)已链接。用户要求将 T14 剩余问题拆为多个 issue 并挨个解决。准确任务状态见[后续 issue 看板](../../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md)。本 issue 承接缺口，不代表 T14 已完成。

## 目标与范围

解释省略 type 的默认分类集合，以及官方最大 100 行与 SINGLE 实测 150 行的适用关系。

主责接口：`fina_mainbz`。

## 已知问题与证据

- SINGLE 两股票各 150 行，任务和 SQL 已通过；旧 SQL 观察编码失败已修复，不重新作为当前故障。
- 官方列 P / D / I 但未定义省略 type 的默认集合；当前业务键不包含分类类型。

来源：[当前验收报告](../../verification/ISSUE-018-range-acceptance.md)、[唯一 JSON 索引](../../verification/ISSUE-018-range-acceptance.json)、[公开补证](../../verification/ISSUE-018-T14-official-evidence.md)。这些是已有事实，创建本 issue 没有产生新的 API / TASK / SQL 结果。

## 依赖与处理顺序

无其他新 issue 的完成依赖；消费 T14 既有设计和证据。串行顺序由看板规定。具体顺序为 ISSUE-019 → ISSUE-020 → ISSUE-021 → ISSUE-022 → ISSUE-023 → ISSUE-024 → ISSUE-025 → ISSUE-026；不得将顺序误作已经完成的上游输入。

第一动作：对照官方默认 type 说明、现有业务键和新 SINGLE 安全摘要，明确最小取证方案与需要用户决定的参数范围。

## 关闭条件

- 形成可核验的默认分类与 SINGLE / RANGE 上限适用结论；如需改参数或业务键，先取得用户明确范围决策并修订设计。
- 不混 P / D / I、不改键掩盖冲突、不以 VIP 绕过；取得报告期 SOURCE 的两股票整段和边界证据。
- 向 ISSUE-026 提供规则、来源证据及任务样本；同步 ISSUE-017 剩余问题事实，不能提前关闭母 issue。

## 约束

沿用 [T14 设计](../../task-designs/ISSUE-018-T14-design.md)和 [T13 验收合同](../../task-designs/ISSUE-018-T13-design.md)。规则解释、默认参数、业务键、市场映射或历史承诺的实质变更须先记录依据及必要的用户决策；真实执行前完成本 issue 的专属设计。保留旧轮次和未验证状态，不在 issue 文档另造验收索引，不因拆分关闭 ISSUE-017 / ISSUE-018。

## 确认前进展（2026-09-13，历史）

已完成官方原文复核、安全分类/业务键摘要及两轮独立SOURCE。18case/18请求，14 PASS、4满额EVIDENCE_MISSING，两个run exit0/cleanup PASS；两股票报告期整段及非空上下边界成立。默认省略type均观察到P/D/I，样本键唯一且无跨类冲突。RANGE实测110/150，证明100并非该请求的实际硬上限；150仍只是观察值。

[方案A/B](../proposals/ISSUE-020-fina-mainbz-default-type.md)已具体列出原严格合同与工程口径的差异，待用户选择。首项关闭条件尚未满足，故保持IN_PROGRESS；不修改生产准入，不把SOURCE通过当作任务/SQL通过。下一动作是记录范围决定，再修订规则引用并交付[ISSUE-026](ISSUE-026-range-task-final-acceptance.md#issue-020-已交付输入)；新文件Git纳管，未提交/合并/发布。

## 完成结论（2026-09-13）

用户明确“同意方案A（推荐）”，[决定](../proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)已同步T13/T14与专属设计：默认保留一次上游实际返回的分类（样本含P/D/I），SINGLE单次快照，RANGE100工程拆分阈值、单日满额失败。100并非实测硬上限、150未获上限保证的事实原样保留，不逐类拼接、不改键。

两股票报告期整段及非空上下边界SOURCE有效，18case/18请求包含14 PASS和4满额未确认；12项RANGE来源、采用规则和精确任务输入已[正式交付ISSUE-026](ISSUE-026-range-task-final-acceptance.md#issue-020-已交付输入)，ISSUE-017当前事实同步。决定后Node78/78、相关Maven163/163、离线12绑定/4拒绝及限定复审通过，原12轮/531case及其他39接口未改。三项关闭条件成立，看板记录IN_PROGRESS -> COMPLETED，证据见[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-020-方案a确认与最终验收)。

生产fina_mainbz仍NEEDS_VERIFICATION/v1；真实RANGE TASK/SQL、全年与宽窗口拆分归ISSUE-026，不提前关闭母issue。新文件Git纳管，未提交/合并/发布。
