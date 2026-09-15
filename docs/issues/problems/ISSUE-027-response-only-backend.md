# ISSUE-027：实现允许不完整的后端响应采集

## 当前状态

COMPLETED（2026-09-14）。后端规则、runner和持久策略摘要已交付，专项280项、两包7项及前端468项检查与独立审查通过；详见[验收记录](../../verification/ISSUE-027-response-only-backend.md)、[专属设计](../../task-designs/ISSUE-027-design.md)及[子看板](../../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)。生产注册和历史真实证据保持。

## 目标与范围

让后端明确执行RESPONSE_ONLY响应采集，保持严格完整性规则、失败处理和旧任务语义。

- 公共CompletenessRule.Kind、BatchAssessment和Tushare RuleKind支持独立RESPONSE_ONLY，要求原生区间、rowLimit为null且不可拆分；保留UNKNOWN拒绝。
- runner按持久化策略精确匹配评估，响应仍经过结构、日期、股票归属、适配和原子入库；覆盖合法空、非空、错误及规则不匹配。
- 复用policy_snapshot保存规则/版本，提供只读policySummary，保留旧快照读取、definitionHash和人工重放校验。
- Tushare策略实现支持新规则，但本issue不修改30项生产注册的候选开放标记/版本；HTTP响应字段、正式schema和页面交ISSUE-028，SOURCE/TASK取证交后续。

## 已知依据

直接消费[共享设计](../../task-designs/ISSUE-026-design.md)：共享设计Approach第2节及第3节的持久化/summary部分；ISSUE-025已批准决定；现有公共批次合同与runner。 原采用决定和精确输入继续以[母issue输入表](ISSUE-026-range-task-final-acceptance.md)、[唯一验收索引](../../verification/ISSUE-018-range-acceptance.json)及[运行登记](../../verification/ISSUE-018-T14-runs.md)为准。拆分没有产生新的实现、SOURCE、TASK、SQL或测试结果。

## 依赖与处理顺序

- 直接子issue前置：None；共享ISSUE-019～025采用决定与来源作为母任务已交付输入，不能推定未来新来源已成功。
- 串行顺序：ISSUE-027 → ISSUE-028 → ISSUE-029 → ISSUE-030 → ISSUE-031 → ISSUE-032；编号顺序不代替依赖的实际验收。
- 第一动作：核对共享设计第2节与持久化summary部分及现有BatchDownloadDescriptor/runner，完成并回填docs/task-designs/ISSUE-027-design.md，固定先验证RESPONSE_ONLY非法组合与规则匹配的实施步骤。

## 关闭条件

- RESPONSE_ONLY仅在合法原生、无阈值、不可拆组合成立；UNKNOWN拒绝，严格规则与新评估互相冒用均失败且零入库。
- 合法非空/空经既有适配与事务完成；坏响应、错股票/日期、适配/写入失败、预算或中断不被接受漏数的决定豁免；受控来源及MySQL验证通过。
- 旧快照读取和摘要变更拒绝重放成立；policySummary只读取持久事实、SINGLE为null，不依赖当前插件能力。
- 局部回归及独立审查通过，向ISSUE-028交付确定的后端summary/枚举合同；现有4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY生产注册和真实证据保持。

## 实施落点与验证

`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/BatchDownloadDescriptor.java`、`BatchAssessment.java`；TushareBatchPolicies的通用规则分支；core的DownloadTaskRunner/DownloadTaskService/DownloadTaskJson及对应Test/IT。

从仓库根使用项目Java21/Node24，按专属设计准备环境后执行以下相关检查；以下为专项复现入口，实际通过结果及追加的日历/准入/包检查见验收记录：

```sh
mvn -o -f data-plane/pom.xml -Dtest=BatchDownloadDescriptorTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskServiceTest,DownloadTaskJsonTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## 约束

遵循共享设计、T13/T14及用户已批准差异，保持40接口、原日期/股票/业务键、无自动重试和历史证据。真实调用前固定输入、完整源码/两包身份和受限环境，SOURCE不入库，TASK每轮新空schema；至少2000ms间隔、每轮30分钟/5000请求和清理要求不变。无新原因不重复已有效验证；新增文件加入Git，不自动提交或发布。局部任务完成不替代尚未完成的生产准入/真实任务/母合同验收。
