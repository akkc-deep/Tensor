# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** [ISSUE-026/ISSUE-026-task-board.md](ISSUE-026/ISSUE-026-task-board.md)。
- **Task ID:** ISSUE-031，Order5。
- **Transition:** IN_PROGRESS -> BLOCKED。
- **Design document:** [ISSUE-031-design.md](../task-designs/ISSUE-031-design.md)。

## Current State

2026-09-15用户要求“完成issue31”后按固定计划实测：limits28、mainbz16、calendar38均PASS，dates首项FAILED / ADAPTER_TYPE_INVALID，另34项及后六轮161项NOT_RUN。合计82 PASS / 1 FAILED / 195 NOT_RUN，没有重试或换样本。mainbz真实19个SPLIT父、35成功叶和1115原键，calendar交易所/日期/股票归属证据成立。

四轮真实运行使用ISSUE-030冻结副本`/private/tmp/issue030-work-20260914T025010Z`及v2包，原身份前后保持；前三轮exit0、第四轮exit1，四轮cleanup PASS。正式索引追加至30轮943case1081请求，前26轮逐对象保持；正式10 AVAILABLE / 24 NEEDS_VERIFICATION / 6 SINGLE_ONLY。fina_indicator现已撤回为v3 / NEEDS_VERIFICATION；当前候选能力33 AVAILABLE / 1 NEEDS_VERIFICATION / 6 UNSUPPORTED。撤回不表示适配根因已修复。尚不满足278项验收，ISSUE-032不准备、不启动，母任务不关闭。

原入口交接对应ISSUE-030 → ISSUE-031的READY准备事实仍见看板准备证据及[ISSUE-030验收](../verification/ISSUE-030-range-candidate-policies.md)；本文件更新为当前阻塞交接。

## Changed Files

- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`：仅撤回fina_indicator候选并递增v3；原字段/业务键/内部阈值及SINGLE不变。
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPoliciesTest.java`：当前准入矩阵；受控保留阈值/日期轴/规划测试。
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java`：原失败参数拒绝提交、零入队/零上游，以及33候选/40 SINGLE。
- `control-plane/e2e/ui-redesign.fixtures.js`：v3撤回能力的独立浏览器预期。
- `control-plane/e2e/tushare-range-evidence.test.js`：实际部分结果、历史保持及重复TASK ID拒绝；保留SOURCE-only不能开放负例。
- `docs/verification/ISSUE-031-range-live-task-verification.md`：新建并已加入Git，逐轮证据、失败与恢复条件。
- `docs/verification/ISSUE-018-range-acceptance.json`、同名`.md`及`ISSUE-018-T14-runs.md`：追加四轮真实事实，保留旧run，更新当前接口处置。
- `docs/verification/ISSUE-030-range-candidate-policies.md`：仅补旧runs SHA的排序序列化表达式，原hash及历史事实保持。
- `docs/issues/problems/ISSUE-031-range-live-task-verification.md`、`docs/issues/README.md`、`docs/task-handoffs/README.md`及权威子看板：阻塞状态。
- `docs/runbook/configuration.md`、`docs/runbook/first-run.md`：区分当前候选能力与已完成真实验收，说明fina_indicator撤回。

## Verification

- 三个定向Java类155/155通过：`mvn -o -f data-plane/pom.xml -pl tensor-app -am -Dtest=TushareBatchAvailabilityTest,TushareBatchPoliciesTest,TushareBatchDownloadTest -Dsurefire.failIfNoSpecifiedTests=false test`，exit0，失败/错误/跳过0。新提交拒绝测试先观察AVAILABLE错误RED，再GREEN。安全日志`/private/tmp/issue031-control/withdrawal-red.log`及`withdrawal-green.log`。
- `node --test control-plane/e2e/tushare-range-evidence.test.js`：104/104 PASS，exit0。
- 四轮真实浏览器/SQL/日志/清理结果及构建身份见[完整验收](../verification/ISSUE-031-range-live-task-verification.md)；失败只读SQL记录`/private/tmp/issue026-range-dates-20260914T181447Z/sql-supplement.json`。
- 新隔离`mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0：后端/两包1134、前端524全部通过，0失败/错误/跳过；准确两包身份见同一验收文档。metadata浏览器40/40 PASS、exit0、零任务提交/零上游、JVM/容器/临时秘密清理通过；原v2实测不能归于新v3包。

## Remaining Work

查明并修复fina_indicator适配失败；现有证据不足以区分转换失败与冲突原键。失败项需经明确恢复合同补验，另195固定TASK待执行，包含dates辅助日历、两次disclosure更新、全部RESPONSE_ONLY及四旧接口回归。保持原SOURCE/params/dateAxis；新执行不能复用已有TASK caseId，需明确新run/case映射、预算、新空schema和冻结构建。之后仍须逐轮SQL/日志/清理与独立审查，全部满足后才能完成31并准备32。

## Resume Task

恢复ISSUE-031：取得278项真实TASK及全部代表场景证据，按正式合同完成验收，不以当前82PASS替代总目标。

## Start Here

1. [专属设计](../task-designs/ISSUE-031-design.md)及本交接、权威看板当前状态。
2. [完整验收](../verification/ISSUE-031-range-live-task-verification.md)、[唯一索引](../verification/ISSUE-018-range-acceptance.json)、[运行登记](../verification/ISSUE-018-T14-runs.md)。
3. `/private/tmp/issue031-control/ten-round-plan.json`（278固定项与276 SOURCE身份）、四轮私有安全产物、失败SQL与旧v2冻结副本。
4. 当前撤回源码及`/private/tmp/issue031-control/withdrawal/`的新包身份/离线结果。

第一动作：从授权保留的失败载荷或脱敏复现样本获得安全适配分支证据；若没有样本，先形成原参数、仅诊断、不写业务表、不自动重试的明确取证方案并取得恢复授权。不得直接重跑TASK/SOURCE试运气。

## Blocker

- **Reason:** TASK `506ba45f-ed85-483e-91ac-a132e183a6a1` / case `issue026-issue022-indicator-000001-whole` / params `{"ts_code":"000001.SZ","start_date":"20250331","end_date":"20251231"}`实际FAILED，ADAPTER_TYPE_INVALID、1请求/1尝试、SQL0→0；现有安全日志丢失适配分支，sourceRows0不证明空响应。
- **Resolution condition:** 可验证的安全诊断或脱敏样本区分字段转换与冲突键，并据此定向修复、受控验证、明确新版本/冻结身份和补验run/case/SOURCE映射及预算/新空schema；有明确恢复证据后才BLOCKED → READY。没有证据不放宽精度、字段或业务键。

## Risks

保留四个schema、全部历史数据和原v2包；本轮临时client.cnf/environment.json均已删除。十一项RESPONSE_ONLY不保证完整，候选AVAILABLE不等同正式验收。禁止自动重试、换日期、复用ID或运行旧候选writer重置现有索引。现有大量前序暂存/未暂存成果保留，没有提交、推送或发布。
