# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-026/ISSUE-026-task-board.md`。
- **Task ID:** ISSUE-031。
- **Transition:** IN_PROGRESS -> BLOCKED

## Current State

2026-09-15后续最终验收：032已完成六门禁及母任务收尾，见[最终报告](../verification/ISSUE-032-range-final-closure.md)。本交接保留031当时交付状态；以下未启动032及母任务未关闭的记录不再代表当前执行状态，033继续未开始。

2026-09-15后续范围决定已解除本次范围阻塞：用户明确排除balancesheet/cashflow/repurchase/fina_indicator的RANGE批量下载，并要求不开始032，见[决定](../issues/proposals/ISSUE-026-range-scope.md)。当前目标为251项，原27项保留问题且不计PASS。范围收尾及离线核对已完成，权威看板已记录ISSUE-031 COMPLETED；见[本次验收](../verification/ISSUE-031-range-live-task-verification.md#2026-09-15四接口排除与issue-031收尾)。本文件继续作为历史pause入口，以下272项阻塞、Remaining Work、Resume Task、Start Here及Blocker均为决定前快照；不再授权执行四接口修复/诊断或自动推进032，当前状态以权威看板为准。

2026-09-15，用户已要求“财务指标先跳过，记录一个issue，再处理其他数据源”。ISSUE-033已登记并加入Git，状态NOT_STARTED；fina_indicator的6项原1 FAILED/5 NOT_RUN和v3撤回保留，本次未调用。当前ISSUE-031目标272项，已清洁接受251项，另3 FAILED/18 NOT_RUN分属balancesheet8、cashflow8、repurchase5；其他固定任务和最后4接口回归均已完成、审查及归档。三个接口未获准另行递延。

唯一索引45轮/1217case/1362来源请求，另三个有界诊断各1请求单列。正式30 AVAILABLE/4 NEEDS_VERIFICATION/6 SINGLE_ONLY，运行能力30 AVAILABLE/4 NEEDS_VERIFICATION/6 UNSUPPORTED；四失败接口保持v3撤回，40接口SINGLE保持。所有历史失败/空/未执行、成功数据和各自冻结包身份保留。ISSUE-031不具备完成条件，ISSUE-032/033未启动，母任务未关闭。

三个诊断均确认此次响应的KEY_CONFLICT，字段转换失败0。balancesheet只观察到total_share/update_flag差异，版本优先级未定。cashflow观察6行/4原键组/2冲突组，两个冲突组各有一个标记1的不同完整行，官方doc44注明1最新；尚未决定采用该版本。repurchase观察852行/15冲突组/17次不同内容比较，原ts_code,ann_date,proc键下差异仅end_date/vol/amount/high_limit/low_limit，记录身份规则未定。诊断不是TASK验收；cashflow/repurchase规范SQL前后null保持。

## Changed Files

- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`：四失败接口v3撤回，原参数/键/字段/精度/SINGLE保持。
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPoliciesTest.java`：撤回预期与原日期/非股票保留回归。
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java`：四接口原失败参数的提交前拒绝，回购无ts_code。
- `control-plane/e2e/ui-redesign.fixtures.js`：独立能力矩阵同步。
- `control-plane/e2e/tushare-live.spec.js`：RESPONSE_ONLY文案及先保全真实FAILED，严格成功校验保持。
- `control-plane/e2e/tushare-range-evidence.test.js`：111项阶段/精确补验映射/历史与nullSQL保全检查。
- `docs/verification/ISSUE-018-range-acceptance.json`：45轮真实索引及正式30/4/6；三个冲突与财务指标缺口保留。
- `docs/verification/ISSUE-018-range-acceptance.md`：逐接口当前结论。
- `docs/verification/ISSUE-018-T14-runs.md`、`docs/verification/ISSUE-031-range-live-task-verification.md`：预登记、实际运行/SQL、诊断及最终核对。
- `docs/issues/problems/ISSUE-033-fina-indicator-adaptation.md`：递延财务指标的范围、失败事实与恢复要求。
- `docs/issues/problems/ISSUE-031-range-live-task-verification.md`、`docs/issues/problems/ISSUE-026-range-task-final-acceptance.md`、`docs/issues/README.md`：当前目标与状态索引。
- `docs/task-designs/ISSUE-031-design.md`、`docs/task-designs/ISSUE-026-design.md`：已批准财务指标递延及失败后的独立续验/有界定位合同。
- `docs/task-handoffs/ISSUE-026/ISSUE-026-task-board.md`、`docs/task-handoffs/ISSUE-031-handoff.md`、`docs/task-handoffs/README.md`：本次阻塞交接与状态。
- `docs/runbook/configuration.md`、`docs/runbook/first-run.md`：30/4/6能力、251/272验收与四接口撤回。

## Verification

- `/private/tmp/issue031-repurchase-20260914T211953Z/data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`：111/111 PASS，exit0、0跳过；最终日志`/private/tmp/issue031-repurchase-control/final-stage-final.log`。
- 同一Node执行`/private/tmp/issue031-repurchase-control/final-audit.mjs`：PASS，272精确参数/日期/包绑定、251/3/18、45轮、30/4/6、原26轮摘要和42轮基线保全、新三轮安全输出/输入/身份/清理均核对；报告`final-audit.json`，索引SHA `1a83add3b0288d2823c1f2bf1d862bc279ac0b8c59cd0ab912c1a2a1c2321558`。
- `git diff --check`、`git diff --cached --check`：exit0；`git ls-files --others --exclude-standard`无输出，ISSUE-033已暂存。
- 已供给的完整离线`mvn -o -f data-plane/pom.xml -Pacceptance verify`结果：冻结副本`/private/tmp/issue031-repurchase-20260914T211953Z`，1137后端/打包（66 suites）、524前端通过，失败/错误/跳过0；定向158通过。该包metadata4/4通过、零任务/上游/records，清理完成；不是旧包metadata40/受控页面15。
- 冻结898文件snapshot `258b7df2a635a9cd3f53f7aaf6abb9257a55ab2551549269551bf09602da3b9b`，生产JAR `4d4f1e3985c10aca803a0ca4d8d1a535e2637909b0f68192c977d193c40735be`，验收JAR `5ecb993e13d6c1340013eb48ca87ea4a91782906dd06bd51f2108b7b51c1bfd5`。`post-live-source-audit.json`确认快照/两包稳定，422相关文件仅最终证据测试变化，运行/合同源码与真实包匹配。
- 所有实际轮次与诊断已独立审查；最后holders8+8/regression4各exit0/cleanup PASS，凭据已移除，成功schema保留。两个holder样本均未观察同股东同报告期多公告日，不能以每期超过10行替代该场景。

- 最终文档/索引/阶段测试/状态增量独立审查无阻断问题；已修正看板陈旧278范围与首次执行动作，以及四接口回归“开放”措辞。

## Remaining Work

1. 确定balancesheet/cashflow/repurchase的冲突版本保留或记录身份规则；不任意取末行、扩键、四舍五入或丢弃冲突内容。
2. 若继续修复，按确认后的接口专属规则最小实现、定向回归并冻结新版本/两包，以新run/case和显式原SOURCE/参数映射在新schema补验21项；原失败/未执行历史保留。
3. 只有当前目标全部成立后，才能完成ISSUE-031并按既定流程准备ISSUE-032。财务指标6项仍由ISSUE-033解决，母任务原完整关闭条件保持。

## Resume Task

恢复ISSUE-031：完成当前272项固定区间任务的真实TASK/SQL验收，保留独立递延至ISSUE-033的6项及全部历史证据。

## Start Here

1. `docs/task-handoffs/ISSUE-026/ISSUE-026-task-board.md`中的ISSUE-031行与状态证据。
2. 完整阅读`docs/task-designs/ISSUE-031-design.md`。
3. `docs/verification/ISSUE-031-range-live-task-verification.md`的当前结果、三接口诊断与最终离线核对，再读唯一JSON及T14登记。
4. `/private/tmp/issue031-repurchase-control/`的final-audit.json、case-build-bindings.json和最终272项计划；原准备status字段是历史快照，以audit/唯一索引为准。三个诊断目录为`/private/tmp/issue031-balancesheet-diagnostic`、`/private/tmp/issue031-cashflow-diagnostic`和`/private/tmp/issue031-repurchase-diagnostic`。

第一动作：依据已归档的诊断，确认三个接口可执行的冲突保留/身份规则，或取得用户对另行递延及范围变更的明确决定；记录解决证据后才进行BLOCKED -> READY，再单独开始实施。现金流量表可以讨论仅采用唯一最新版本、零/多最新仍失败的方案，但目前未决定，不能据此修改生产适配。三个诊断均已执行，不重复请求。

## Blocker

- **Reason:** 21项依赖的原业务键对同一响应中不同内容不能唯一确定；版本保留/记录身份合同未解决。缺少的是数据语义决定，不是Token、构建或沙箱权限。只批准财务指标递延，未批准其他三个接口递延。
- **Resolution condition:** 三接口的冲突处理规则有明确可执行的决定及相应设计，或用户明确批准其他接口的递延和验收范围调整；恢复时逐接口保留未解决范围，不推定全部解除。

## Risks

RESPONSE_ONLY只保证采集返回记录，不保证上游完整。cashflow标记规则不能推广到其他接口；诊断未保存原失败payload，不声称逐值复现。旧包/数据库/原始安全证据不可覆盖；临时目录可能丢失，缺失身份须重新准备，不能臆造结果。此次续办未commit/push；先前要求的一次远程提交已由f563bd9完成。
