# ISSUE-029 Task 1 独立审查

## 结论

- 规格符合性：**FAIL / 需修改**。
- 代码质量：**NEEDS CHANGES**。
- 报告所述 `93/93` Node 测试结果与改动覆盖面一致，但现有测试没有覆盖下述 schema 1 未引用对象和新清单缺失完整 SOURCE 身份的路径。本审查按约束没有重跑完整套件。

## Findings

### 1. High：schema 1 仍接受未被 interface 引用的 RESPONSE_ONLY TASK

`validateEvidence` 先验证所有 `runs[].cases`，但 schema 版本限制只在 `validateInterface` 对 `value.cases` 映射得到的 `found` 集合执行（`control-plane/e2e/tushare-range-evidence.js:484-500`）。因此，合法 run 中一个未列入任何 `interfaces[].cases` 的 TASK 可以在 `schemaVersion=1` 下设置 `expectedCoverage="RESPONSE_ONLY"` 并通过验证。现有测试只修改了已被 interface 引用的 TASK（`control-plane/e2e/tushare-range-evidence.test.js:475-479`）。

聚焦内存复现结果为 `BUG_REPRODUCED_ACCEPTED`：将当前索引克隆为 schema 1，在既有合法 run 追加一个唯一、未引用的 TASK 副本，并仅把 `expectedCoverage` 改为 `RESPONSE_ONLY`，`validateEvidence` 未拒绝。

这违反设计中“schema1 不允许 RESPONSE_ONLY”的全索引规则，也允许新语义藏在历史 run/case 集合中。应在 `validateEvidence` 遍历全部 run/case 时实施 schema 级限制，而不是只扫描 interface 的引用闭包；同时增加“未引用 TASK”拒绝测试。schema 2 下也建议把 RESPONSE_ONLY TASK 约束到采用该合同的十一接口及其合法绑定，避免未引用对象绕过 API 白名单。

### 2. Medium：新 RESPONSE_ONLY 清单没有被强制携带完整 SOURCE 身份

`selectTaskCases` 在没有完整或旧式 SOURCE ref 时直接回退到全部合法同参数来源（`control-plane/e2e/tushare-range-evidence.js:596-609`）。只要候选唯一，RESPONSE_ONLY 计划即使未提供 `#<runId>/<caseId>` 仍会被接受。随后 `initialTaskEvidence` 从该隐式绑定生成看似完整的 `expectedCoverageSource`（`control-plane/e2e/tushare-live.spec.js:517-527`），从结果中无法看出输入清单违反了“新清单一律完整身份”的要求。

两个同参数来源的歧义测试（`control-plane/e2e/tushare-range-evidence.test.js:524-580`）证明了显式身份和歧义拒绝，但没有覆盖“唯一来源、RESPONSE_ONLY、新清单未指定身份”。应为可识别的新计划强制恰一个完整 ref，并仅对明确的旧计划保留唯一候选兼容路径；至少应先对 RESPONSE_ONLY 计划强制完整身份并补测试。

### 3. Low：运行期 SOURCE 参数等价判断依赖对象键顺序

选择阶段用逐键语义比较，正确接受键顺序不同但内容相同的参数（`control-plane/e2e/tushare-range-evidence.js:175-178,590-595`）；运行阶段却用 `JSON.stringify(boundSource.params) === JSON.stringify(sample.params)`（`control-plane/e2e/tushare-live.spec.js:1562-1569`）。合法计划只要以不同插入顺序书写 `ts_code/start_date/end_date`，就会在实际执行前被错误拒绝。

应复用语义相等函数或比较排序后的 entries，并增加重排参数键的运行期测试。

## 已核对且符合设计的部分

- schema 2 的 RESPONSE_ONLY completeness 使用精确十一接口白名单，并要求 `NATIVE_RANGE`、`rowLimit=null`、决定引用、逐接口报告和官方 URL（`control-plane/e2e/tushare-range-evidence.js:4-10,420-434`）。
- RESPONSE_ONLY AVAILABLE 会绑定一个清洁且同参数的 SOURCE，并对 PASS TASK 强制 `requestCount=1`、单根单成功叶和完整父窗口；通用 case 校验同时要求 TASK 的 SQL、归属和摘要字段非空（`control-plane/e2e/tushare-range-evidence.js:369-405,446-480`）。
- 显式完整 SOURCE 身份不存在、错配、多重或不清洁时不会回退；`sourceBindings` 同时供初始证据和实际执行使用（`control-plane/e2e/tushare-range-evidence.js:559-615`，`control-plane/e2e/tushare-live.spec.js:517-531,1556-1569`）。
- capability 和持久 task extraction 均按候选 policy/rule 严格核对；RESPONSE_ONLY 终态要求单请求、单次尝试、单个整窗成功叶（`control-plane/e2e/tushare-live.spec.js:1047-1069`，`control-plane/e2e/tushare-range-evidence.js:693-706`）。
- 严格 DTO 的 Int64/BigInt 在写入证据前转换为有界安全整数，任务、分页、汇总和最终 JSON 均未进行有损 Number 强转（`control-plane/e2e/tushare-range-evidence.js:160-165,664-690`，`control-plane/e2e/tushare-live.spec.js:1373-1405,1687-1717`）。
- harness 实际检查响应采集页面的非空/空文案与持续截断提示，并核对任务摘要、SQL 增量、股票归属、最终页面计数；已知非空 SOURCE 对应的空 TASK 保留 SUCCEEDED 事实并降为 EVIDENCE_MISSING（`control-plane/e2e/tushare-live.spec.js:1330-1335,1414-1424,1570-1605`）。

## 审查边界与开放风险

按任务约束未运行真实 Tushare API、SQL、浏览器或完整测试套件，因此运行期结论来自代码和受控测试证据。Task 2 Java Probe、正式索引在本快照后的升级及后续四个 SOURCE 登记不在本审查范围内。

## Fix round 1 限定复审

### 结论

- 规格符合性：**PASS**；本结论取代上面的初审 FAIL。
- 代码质量：**PASS**；H1、M2、L3 均已关闭，在限定范围及直接回归中没有新增发现。

### 关闭情况

- **H1 已关闭。** `validateEvidence` 现在遍历全量 `runs[].cases[]`，schema 1 对任何 `expectedCoverage=RESPONSE_ONLY` 直接拒绝；schema 2 的全局校验同时限制 TASK/RANGE、十一接口、决定加完整 SOURCE 身份、独立清洁 SOURCE、API/日期轴/参数一致性，并继续对 PASS 强制单请求整窗单成功叶（`control-plane/e2e/tushare-range-evidence.js:484-504,540-555`）。FAILED/NOT_RUN 仍由通用状态校验保留原始空 SQL/任务字段，没有被 PASS 要求污染。
- **M2 已关闭。** RESPONSE_ONLY 候选现在要求恰一条完整 `#<runId>/<caseId>` 引用且不接受旧式 `#caseId`；其他规则继续保留原兼容分支（`control-plane/e2e/tushare-range-evidence.js:622-637`）。
- **L3 已关闭。** 运行期绑定比较改为排序后的键值对，参数对象插入顺序不再影响语义相等判断（`control-plane/e2e/tushare-live.spec.js:1562-1570`）。

### 验证

定向执行上述三个修复对应的五个新增测试：**5 通过、0 失败、0 跳过、0 todo**。覆盖 schema 1 未引用 TASK、schema 2 未引用 TASK 的 API/来源/PASS 结构、FAILED/NOT_RUN 保真、新 RESPONSE_ONLY 清单完整身份和参数键重排。实施报告记录的完整结果为 **98/98**；本限定复审未重复运行完整套件，也未执行真实 API、SQL、浏览器或 Task 2 Probe。
