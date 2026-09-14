# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md`。
- **Completed task:** `ISSUE-025`，COMPLETED。
- **Next task:** `ISSUE-026`，按既定Order8选定。
- **Design document:** `docs/task-designs/ISSUE-026-design.md`，已完成并链接自看板；[设计](../task-designs/ISSUE-026-design.md)。
- **Expected next status:** `READY`；本交接写入并链接后记录NOT_STARTED → READY，不表示开始实施。

## Next Task

2026-09-14用户明确要求继续拆成issue，本交接保留ISSUE-025→ISSUE-026的总任务入口，具体执行转交[ISSUE-027～032子看板](ISSUE-026/ISSUE-026-task-board.md)。027初始READY，其余NOT_STARTED；不是这些子issue已经有完成设计或实施结果。

ISSUE-026：完成剩余区间任务验收与T14收尾。范围为剩余30接口候选实现、真实RANGE/SQL、回归和母任务验收；十一接口按RESPONSE_ONLY采集当次返回记录，其他接口保持既定限量或完整日历合同。

验收采用已完成设计的五项Acceptance：公共规则/runner/持久快照/HTTP/页面/证据消费者一致；268组既有SOURCE加四项待执行fina_mainbz完整拆分SOURCE精确绑定；278项固定TASK（含四项新拆分、两项披露重下、四项既有接口回归）和清洁身份/SQL/门禁成立；全部34 RANGE目标有实际证据，保留6 SINGLE_ONLY与历史事实；最终按合法状态复核T14/T13及母issue。以上数字是执行计划，不是已产生的新任务结果。

## Dependencies

### ISSUE-019

- **Artifact:** `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-019-已交付输入`；`docs/issues/proposals/ISSUE-019-documented-range-limits.md#决策记录`；唯一索引中该节两个run。
- **Decision:** daily/forecast/dividend接受6000/3500/2000工程阈值。
- **Rationale:** 用户已明确同意方案A，原官方措辞及工程采用差异分别记录。
- **Constraint:** 达到/超过阈值拆分，单日仍满额失败；dividend逐自然日，不改VIP/股票。
- **Usage:** 复制28项精确SOURCE参数/身份，验证daily重叠更新和非交易日分红。
- **Readiness evidence:** 两清洁轮，28非空PASS、10空保留；来源绑定及Node78/相关Maven160通过，任务看板记录COMPLETED。

### ISSUE-020

- **Artifact:** `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-020-已交付输入`；`docs/issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录`。
- **Decision:** 默认单次实际分类、SINGLE快照、RANGE工程阈值100；全年/六年窗口真实拆分要求继续保留。
- **Rationale:** 用户已批准方案A，实际110/150与官方100的矛盾未被当作新保证。
- **Constraint:** 不逐类拼接、不改原键，旧4满额EVIDENCE_MISSING不重标或直接选择；不适用ISSUE-025漏数例外。
- **Usage:** 原12项有效输入直接复制；按本设计先实施测试Probe二分SOURCE并固定两股票×2025全年/2020～2025宽窗四新case，再执行四项绑定新SOURCE的真实任务，mainbz合计16 TASK。
- **Readiness evidence:** 原12绑定及4拒绝、Node78/Maven163、独立复审已通过。新增四项完整拆分SOURCE/TASK尚未执行，是本设计明确实施范围，不冒充现有PASS。

### ISSUE-021

- **Artifact:** `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-021-已交付输入`；`docs/task-designs/ISSUE-021-design.md`。
- **Decision:** trade_cal支持SSE/SZSE完整日历；top_list BJ日历参照SSE，保留BJ股票；margin保留exchange_id三交易所。
- **Rationale:** 官方说明与沪深完整日期一致性、BJ独立非空SOURCE成立；直接BSE取得合法空响应且完整性失败。
- **Constraint:** 不把用户直接BSE改为SSE；该负例和生产零调用拒绝保持；日历先完整再取开市日。
- **Usage:** 实现生产BJ映射，38项任务核对市场/日期/闭市零叶子。
- **Readiness evidence:** 38 PASS（35非空、3完整闭市）及1 BSE失败保留，38来源绑定通过；Node78、Maven170与复审通过。

### ISSUE-022

- **Artifact:** `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-022-已交付输入`；`docs/task-designs/ISSUE-022-design.md`。
- **Decision:** 周/月线为实际最后交易日；报告期、公告日、申购日各用既定输出列。
- **Rationale:** 两股票边界、国庆周一、同一行副日期及辅助完整日历已有真实来源。
- **Constraint:** 不硬编码周五/自然月末、不互换日期轴、不把四空对照变成正样本。
- **Usage:** 31项五接口SOURCE与4项辅助日历共35项任务；保留各自准确来源身份。
- **Readiness evidence:** 35绑定/4拒绝、Node78/Maven173及隔离构建、独立复审通过。

### ISSUE-023

- **Artifact:** `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-023-已交付输入`；`docs/task-designs/ISSUE-023-design.md#质押非空样本决定2026-09-13`。
- **Decision:** 质押非空代表股票经用户同意为000014.SZ/600000.SH；最新披露语义与原业务键保持。
- **Rationale:** 有公开事件及固定非空来源，原000001.SZ空保存；同键复查没有观察到本轮跨时点值变化。
- **Constraint:** 不伪造旧披露版本、不按股/日去重多笔大宗交易、不将业务起止日替代公告日。
- **Usage:** 35项输入加两项disclosure_date同参数重下，真实SQL验证更新操作；受控内容修订与上游事实分开。
- **Readiness evidence:** 35绑定及旧空/失败拒绝、Node78/Maven184、独立复审通过；真实更新入库待本任务执行。

### ISSUE-024

- **Artifact:** `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-024-已交付输入`；`docs/issues/proposals/ISSUE-024-historical-support.md#决策记录`。
- **Decision:** 验收历史查询能力，精确起止/持续保留仍未知；三个5000阈值保持。
- **Rationale:** 用户批准方案A，官网历史能力与代表SOURCE已取得，监管停业日不等于API截止。
- **Constraint:** 不限制为虚构支持区间、不排除接口；汇总与明细各自原键，期限/费率区别保留。
- **Usage:** 33项固定历史TASK，SQL核对原键、归属和计数。
- **Readiness evidence:** 33绑定/15负例、全部旧run保留核对、Node78及独立复审通过；六新空保存。

### ISSUE-025

- **Artifact:** `docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录`；`docs/issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-025-条件输入完整性未确认`；`docs/verification/ISSUE-018-T14-runs.md#issue-025-方案a确认与最终验收2026-09-14`。
- **Decision:** 用户2026-09-14明确“可以接受不完整”，十一接口采用独立RESPONSE_ONLY；原完整提取要求的限定差异已同步T13/T14/总体设计与母issue。
- **Rationale:** 官方仍无可核验的截断/完成规则，用户接受无法排除的漏数；来源日期/股票/事件已有有效观察。
- **Constraint:** 原生单请求单叶子、无rowLimit、不按行数拆分；所有返回行仍须校验/适配/事务入库。页面持续显示完整性未确认，空只说明本次无返回；不得将UNKNOWN布尔放行或用严格COMPLETE掩盖。
- **Usage:** 实现独立规则及全部消费者，再执行87组来源绑定任务；schema2和候选v2仅在对应实现/门禁成立后形成，旧SOURCE的UNKNOWN和43空不回填。
- **Readiness evidence:** 三清洁SOURCE轮135case/135请求（92非空、43空），决定后Node78/78、87精确绑定/87当前UNKNOWN拒绝、全部25轮822case886请求与其他29接口保留核对通过；独立复审无剩余阻断。生产仍v1/NEEDS_VERIFICATION，无新TASK/SQL。

以上决定作用于互不重叠的30接口集合，辅助日历规则与ISSUE-021一致；ISSUE-025放宽不推广到fina_mainbz或其他19项。总体与T13/T14的原严格条款已明确引用同一限定决定，没有待用户选择的合同冲突。SOURCE只证明其保存的来源事实；所有生产候选和任务验收仍在后继范围。

## Start Here

按顺序读取：

1. [ISSUE-026完整设计](../task-designs/ISSUE-026-design.md)及[后续看板](ISSUE-018/ISSUE-018-followups-task-board.md)的本任务行。先读设计中的“子issue执行分工”，再定位[子看板](ISSUE-026/ISSUE-026-task-board.md)的ISSUE-027。
2. [七组精确输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md)、[唯一索引](../verification/ISSUE-018-range-acceptance.json)、[运行登记](../verification/ISSUE-018-T14-runs.md)；各依赖决定见上文。
3. [T14设计](../task-designs/ISSUE-018-T14-design.md)、[T13限定合同](../task-designs/ISSUE-018-T13-design.md)、[T12基础设施](../verification/ISSUE-018-task-infrastructure.md)，原看板和T14历史交接供最终状态处理。

具体第一动作：进入ISSUE-027，核对共享设计分配的公共规则/runner/快照与summary范围，完成并回填其专属设计；设计明确后再开始对应回归与实现。其余五部分按子看板推进，不能把本总交接当作直接一次实施全部范围的入口。原来源、任务计划及所有采用决定继续适用。

## Risks

- 十一项完整性仍未知，用户决定不代表新的上游保证；其他限量/历史/分类限制按各自已批准范围保存。
- 新fina_mainbz SOURCE/TASK可能遇最小满额、空或权限变化；按设计保留失败和缺口，不以受控测试冒充真实拆分。
- 同参数新旧SOURCE可同时存在，必须按指定完整runId/caseId绑定，不能沿用first-match；历史快照说明也不能从当前能力反推。
- 私有环境和旧runner路径不是当前可用保证。真实轮次须重新核验源码/两包、授权配置、新空schema、权限/预算/清理，保留旧数据和原工作树。
- READY仅表示设计和交接完成；尚无新生产代码、真实来源或TASK/SQL执行。完整发布脚本前置未获满足，不能宣称发布通过。
