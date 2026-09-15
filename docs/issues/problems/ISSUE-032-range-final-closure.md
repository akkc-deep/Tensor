# ISSUE-032：完成最终回归与母任务收尾

## 当前状态

COMPLETED（2026-09-15）。本次30个RANGE接口/251项通过、四接口27项明确排除；六门禁1401/524/1134/1137/15/138及111证据测试、历史/身份审计、独立终审全部通过。已先完成032，再收尾T14、T13、017/018及026，033保持NOT_STARTED。已最小修正过期集成测试断言及浏览器用例，详见[最终报告](../../verification/ISSUE-032-range-final-closure.md)。未提交、推送或发布。

## 目标与范围

汇总所有子issue的真实结果，完成六条源码门禁、独立终审以及ISSUE-026/T14/T13和母issue关闭核对。

- 消费前序最终代码及ISSUE-031全部真实证据，复核六条源码门禁、受影响SINGLE/旧任务兼容和40项能力/版本/页面/手册的一致性。
- 在相同最终源码上补齐缺失或受改动影响的检查，保留测试结果与构建身份，不为每个子issue机械重复全套真实任务或回归。
- 完成独立终审及已批准完整性/历史/分类差异说明，确认30个RANGE纳入、4个明确排除和6 SINGLE_ONLY处理明确。
- 按各权威看板的实际状态和合法转换完成本issue、ISSUE-026、T14/T13及ISSUE-017/018关闭；保留旧状态证据，不自动提交、合并或发布。

## 已知依据

直接消费[共享设计](../../task-designs/ISSUE-026-design.md)：共享设计Approach第6节、Tests/Acceptance；ISSUE-031汇总证据与最终代码；T13/T14、总体设计§6及母issue关闭条件。 原采用决定和精确输入继续以[母issue输入表](ISSUE-026-range-task-final-acceptance.md)、[唯一验收索引](../../verification/ISSUE-018-range-acceptance.json)及[运行登记](../../verification/ISSUE-018-T14-runs.md)为准。拆分没有产生新的实现、SOURCE、TASK、SQL或测试结果。

## 依赖与处理顺序

- 直接子issue前置：ISSUE-031；共享ISSUE-019～025采用决定与来源作为母任务已交付输入，不能推定未来新来源已成功。
- 串行顺序：ISSUE-027 → ISSUE-028 → ISSUE-029 → ISSUE-030 → ISSUE-031 → ISSUE-032；编号顺序不代替依赖的实际验收。
- 第一动作：对照ISSUE-031实际结果和最终源码身份，完成并回填docs/task-designs/ISSUE-032-design.md，列出六门禁可复用证据、缺失检查及母合同逐条验收表。

## 关闭条件

- 前序实现及251项纳入任务证据（原27项排除记录保留）满足共享设计第1～4项，并具备第5项母任务收尾所需的事实条件；原run/失败/空/成功数据保留，30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY有实际依据。母状态记录按下述完成顺序处理，不要求先关闭母任务才能完成本子issue。
- 六条门禁按既定顺序在最终适用源码上通过，数量/退出/跳过/清理有记录；已有同一代码且顺序合规的有效结果可复用，有新影响时补验。
- 接口规则、正式HTTP合同、历史快照、独立测试、页面和手册一致，独立终审无剩余阻断；接受漏数等用户决定不写成上游保证。
- 本issue验收成立后先记录其COMPLETED，再按实际状态核对并完成母任务关闭；任何母合同缺口如实保留，发布脚本未运行不声称发布通过。

## 实施落点与验证

唯一验收报告/索引、T14运行登记、`docs/runbook/configuration.md`、`first-run.md`、相关母issue及各权威看板；新问题按影响修复与复验。

从仓库根使用项目Java21/Node24，按专属设计准备环境后执行以下相关检查；这些是待执行命令，不是本次结果：

```sh
mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js
npm --prefix control-plane run test:e2e
git diff --check
git diff --cached --check
```

## 约束

遵循共享设计、T13/T14及用户已批准差异，保持40接口、原日期/股票/业务键、无自动重试和历史证据。真实调用前固定输入、完整源码/两包身份和受限环境，SOURCE不入库，TASK每轮新空schema；至少2000ms间隔、每轮30分钟/5000请求和清理要求不变。无新原因不重复已有效验证；新增文件加入Git，不自动提交或发布。局部任务完成不替代尚未完成的生产准入/真实任务/母合同验收。
