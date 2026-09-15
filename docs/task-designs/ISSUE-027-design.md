# ISSUE-027：允许不完整的后端响应采集

## Goal

落实[共享设计第2节及第3节后端部分](ISSUE-026-design.md)：RANGE可明确采集本次返回记录，且不会冒充完整提取；向ISSUE-028交付持久策略摘要。用户2026-09-14要求“完成issue27”，沿用已批准的ISSUE-025决定。

## Scope

公共规则/评估枚举、Tushare通用策略校验和评估、runner精确匹配、只读policySummary及对应测试。保留生产40接口与4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY，注册版本和SOURCE/TASK索引不改；HTTP、schema、页面、候选开放及真实账户取证分别留给ISSUE-028～032。复用policy_snapshot，不改迁移，不提交或发布。

## Approach

1. `CompletenessRule.Kind.RESPONSE_ONLY`要求rowLimit=null、非空evidence；descriptor仅允许NATIVE_RANGE且splittable=false。AVAILABLE仍拒绝UNKNOWN。`BatchAssessment.RESPONSE_ONLY`独立于COMPLETE。
2. `TushareBatchPolicies.RuleKind.RESPONSE_ONLY`要求原生、无阈值、不可拆；仅通用分支支持，不改createPolicies注册。已验证规则映射为公共RESPONSE_ONLY；未sourceVerified仍UNKNOWN、plan拒绝。复用结构、请求、日期闭区间及股票归属检查，检查通过后空/非空均返回RESPONSE_ONLY。plan和sourceParameters复用原生一叶子/原请求路径。
3. runner在UNKNOWN拒绝后、拆分/适配前按持久policy判定：RESPONSE_ONLY仅接受同名评估；严格规则仅接受COMPLETE/SPLIT_REQUIRED；跨规则冒用返回DATASET_MISCONFIGURED，零适配/入库。拆分可用性及最小日期边界沿用BATCH_COMPLETENESS_UNCONFIRMED。响应大小/请求/时间/行数预算、中断、错误与原子事务路径保持。
4. `DownloadTaskService.policySummary(DownloadTask)`返回嵌套public record `TaskPolicySummary(String policyVersion, BatchDownloadDescriptor.CompletenessRule.Kind ruleKind)`。null任务按PARAM_INVALID；SINGLE返回null；RANGE只用json.readRangePolicy读取policySnapshot，非法内容按既有存储JSON的IllegalArgumentException处理；不查插件、适配器、能力或数据库。HTTP映射由ISSUE-028消费。
5. JSON继续schemaVersion=1及现有严格枚举读写；不增加迁移/新格式。测试新规则往返、旧v1/v2严格/UNKNOWN快照、非法组合、definitionHash规则/版本敏感和人工重放拒绝。已有代码满足时只补测试。

## Files

- `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/BatchDownloadDescriptor.java`、`BatchAssessment.java`：公共合同。
- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`：通用规则分支。
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRunner.java`、`DownloadTaskService.java`：匹配和摘要；`DownloadTaskJson.java`按实际测试需要最小修改。
- 对应`BatchDownloadDescriptorTest`、`TushareBatchPoliciesTest`、`TushareBatchDownloadTest`、`DownloadTaskRunnerTest`、`DownloadTaskRunnerIT`、`DownloadTaskServiceTest`、`DownloadTaskJsonTest`：合同、受控来源、MySQL事务及兼容性验证。
- 本设计、issues索引与问题文档、ISSUE-026子看板及母看板、`docs/verification/ISSUE-027-response-only-backend.md`：实际启动/验收和后端交付记录。

## Tests

使用`/Users/qiangzhiwei/.sdkman/candidates/java/21.0.11-oracle`的Java21，普通测试白名单环境仅保留工具路径、HOME、JAVA_HOME、TMPDIR、LANG/LC_ALL及必要本地Docker设置，排除真实账户/DB。日志保存在`/private/tmp/issue027-control/`。MySQL由现有Testcontainers启动mysql:8.4.6并清理。

先补公共合法/非法组合测试并观察缺新枚举的失败，再加入枚举与组合校验；随后补runner双向冒用的失败测试，再实现匹配。接着补Tushare及summary/JSON测试，运行失败后最小实现。最后补受控MySQL的合法非空/空、适配失败、写入回滚及规则冒用用例，验证单请求单叶子和原键实际数据。

```sh
mvn -o -f data-plane/pom.xml -Dtest=BatchDownloadDescriptorTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskServiceTest,DownloadTaskJsonTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

期望全部通过，失败/错误/跳过为0；无真实Tushare调用。按变化补充必要core/plugin/app合同回归与构建，不重复既有账户SOURCE/TASK。独立审查后修复实际发现并复验受影响范围。

## Acceptance

1. 新规则仅合法组合成立，UNKNOWN保持拒绝；双向评估冒用和错误响应零入库。
2. 合法非空/空均一请求一成功叶子、复用适配与原子提交；结构/日期/股票、适配/写入、预算、中断失败不被豁免；MySQL有实际验证。
3. 持久摘要只含保存的版本和规则，旧快照可读、SINGLE null；当前能力变化不改历史摘要，定义变化仍拒绝重放。
4. 独立审查及局部回归通过；生产注册、所有原真实证据保持。新增文件加入Git，不自动commit/push。

## Risks

接受漏数不等于允许坏响应或提升生产准入。测试候选必须通过现有包内构造注入，不能添加生产验证开关。历史策略可能UNKNOWN，摘要必须原样返回；非法快照不能回退当前能力。工作树已有大量暂存成果，所有改动基于当前内容保留它们。
