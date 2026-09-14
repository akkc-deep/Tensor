# ISSUE-030：配置剩余接口候选策略与日历映射

## 当前状态

COMPLETED（2026-09-15）。30候选、BJ日历参照、272精确来源绑定和新源码/生产包/验收包交付，相关测试与独立审查通过；完整结果见[验收记录](../../verification/ISSUE-030-range-candidate-policies.md)。正式索引保持4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY，26轮826case928请求历史不变，没有新真实SOURCE/TASK/SQL。准确状态见[子看板](../../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)；原[入口交接](../../task-handoffs/ISSUE-030-handoff.md)保留为启动前快照，母任务仍IN_PROGRESS。

## 目标与范围

将已交付合同落实为可用于本地真实任务验收的30项候选策略和稳定构建。

- 按共享设计规则表逐项配置11个RESPONSE_ONLY、其余19个限量/完整日历候选，记录准确依据、日期与首次v2；四个已开放策略保持v2。
- 实现top_list BJ→SSE参照，保留原BJ证券；trade_cal直接BSE RANGE继续零来源调用拒绝，margin保留exchange_id。
- 消费ISSUE-029的新四SOURCE和全部既有输入，建立精确候选引用/规则及独立能力预期；历史空/失败留在runs，正式验收索引在TASK通过前保持NEEDS_VERIFICATION。
- 固定包含前序完整成果的候选源码、生产/验收包及输入摘要；执行必要局部/集成检查，不提交真实RANGE TASK或宣称最终开放。

## 已知依据

直接消费[共享设计](../../task-designs/ISSUE-026-design.md)：共享设计Approach第1/2/4节；ISSUE-027～029实际产物；ISSUE-026问题文档七组输入及唯一索引。原采用决定和精确输入继续以[母issue输入表](ISSUE-026-range-task-final-acceptance.md)、[唯一验收索引](../../verification/ISSUE-018-range-acceptance.json)及[运行登记](../../verification/ISSUE-018-T14-runs.md)为准。[ISSUE-029验收](../../verification/ISSUE-029-range-evidence-and-split-source.md)已交付schema2、完整来源绑定及四项新mainbz SOURCE（42请求）；交付当时为26轮826case928请求、生产4/30/6；本项随后实现候选，实际结果以验收记录为准，真实TASK/SQL仍未执行。

## 依赖与处理顺序

- 直接子issue前置：ISSUE-027, ISSUE-028, ISSUE-029，已交付的后端/HTTP合同、严格工具及四项实际来源见交接；共享ISSUE-019～025采用决定与来源继续作为母任务已交付输入，未来TASK仍需实际验收。
- 串行顺序：ISSUE-027 → ISSUE-028 → ISSUE-029 → ISSUE-030 → ISSUE-031 → ISSUE-032；编号顺序不代替依赖的实际验收。
- 第一动作：消费已完成的专属设计，在TushareBatchPoliciesTest和TushareBatchAvailabilityTest补上30项独立能力预期（含十一项不可拆RESPONSE_ONLY及新v2），运行定向Maven测试观察RED，再最小实现候选配置。

## 关闭条件

- 30候选分别匹配已批准规则、日期轴、参数/原键和有效来源；不能全表默认true、为UNKNOWN猜阈值或把RESPONSE_ONLY映射成完整规则。
- BJ参照与直接BSE拒绝、严格限量等号/最小满额、11项单请求及独立能力/页面预期全部通过；保留40/34/6和31/3/6结构。
- 候选包具有实际可复核源码/两包/manifest/examples身份，供ISSUE-031运行；本地运行时候选AVAILABLE与最终证据AVAILABLE明确区分。
- 独立审查和相关回归通过；正式验收索引未仅因候选就绪将30项标AVAILABLE，向ISSUE-031交付稳定候选和来源映射。

## 实施落点与验证

生产TushareBatchPolicies及相应策略/日历/HTTP可用性测试；`control-plane/e2e/ui-redesign.fixtures.js`、`tushare-metadata.spec.js`等独立能力预期；唯一验收索引候选引用和T14构建登记。

从仓库根使用项目Java21/Node24，按专属设计准备环境后执行以下相关检查；命令的实际结果已记录在验收文档：

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -o -f data-plane/pom.xml -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## 约束

遵循共享设计、T13/T14及用户已批准差异，保持40接口、原日期/股票/业务键、无自动重试和历史证据。真实调用前固定输入、完整源码/两包身份和受限环境，SOURCE不入库，TASK每轮新空schema；至少2000ms间隔、每轮30分钟/5000请求和清理要求不变。无新原因不重复已有效验证；新增文件加入Git，不自动提交或发布。局部任务完成不替代尚未完成的生产准入/真实任务/母合同验收。
