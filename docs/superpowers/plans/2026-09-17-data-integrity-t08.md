# Data Integrity T08 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 用本地参考为 Tushare 日周月生成可定位的候选缺口，生产覆盖保持 UNKNOWN。

**Architecture:** 来源 hook 与规则共用参考请求；核心承担同快照读取、预算和 UNCONFIRMED 比较。仅三个规则及能力升级到 2。

**Tech Stack:** Java 21、JUnit 5、Maven、Testcontainers MySQL 8.4.6。

**Spec:** `docs/task-designs/DATA-INTEGRITY-T08-design.md`（完整接口、算法、证据、失败规则及验收表为实施依据）。

## Global Constraints

- 所有修改在 `.worktrees/data-integrity`；保留 Studio/T01–T07 混合暂存基线，不创建混合提交。
- 只修改专属设计 Files 所列实现/测试及任务文档，不更改下载策略、核心或公共 HTTP 合同。
- 始终 UNCONFIRMED，UNKNOWN，正式计数/比例 null；参考问题不伪装目标缺键；预算异常原样传递。
- 不访问上游，无 Token 可执行；新文件加入 Git。

### Task 1: 行情规则、hook 和专项测试

**Files:** `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/integrity/TushareMarketIntegrityRule.java`（新增）、同目录 `TushareIntegrityPolicies.java`、上一级 `TushareProPlugin.java`；`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/integrity/TushareIntegrityRulesTest.java`（新增）、同目录 `TushareIntegrityPoliciesTest.java`；`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java` 的既有公开方法白名单。

**Interfaces:** 实现既有 IntegrityRule.evaluate；新增 policies.referenceReads(IntegrityScope)，由插件 integrityReferenceReads 委托；不新增公共合同。

- [x] 在既有策略测试基线通过后，写 weekly 首个 RED：2024-03-25 至 2024-03-31 完整周历，最后开市 2024-03-28、周五闭市，snapshot 2024-04-02，传入 compare 的键必须为 `{ts_code:600000.SH, trade_date:2024-03-28}`、basis=UNCONFIRMED。确认占位规则未 compare 导致断言失败。
- [x] 按专属设计 Approach 1–5 实现最小来源规则和共享请求，逐项补充 Tests 表全部手算边界。来源不重写核心比较；使用 recording context 验证请求和完整候选。
- [x] 改策略测试，仅行情版本变 2，非行情继续不扫描；验证三规则行为改变可被能力 hash 检测且无客户端交互。
- [x] 执行 `mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am '-Dtest=TushareIntegrityRulesTest,TushareIntegrityPoliciesTest,IntegrityPluginContractTest' '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dsurefire.failIfNoSpecifiedTests=false test`，记录 RED/GREEN 和实际数量。

### Task 2: 真实应用验收与任务交接

**Files:** `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ProductionApplicationContextIT.java`；`docs/verification/DATA-INTEGRITY-T08.md`；`docs/task-handoffs/data-integrity-task-board.md`；完成后按已有看板准备 T09 设计和交接。

**Interfaces:** 真实 Spring IntegrityCheckService.create / Repository 读取已提交结果；消费 Task 1 插件 hook，不 mock Runner/Reader/比较器。

- [x] 无 Token 生产上下文先受理缺参考的三接口，等待 COMPLETED，逐单元断言 UNKNOWN、REFERENCE_INCOMPLETE、原范围/全部计划保留。
- [x] 在受理前种入完整 2024-02 月历和目标 daily/weekly/月末缺行，核对 daily/weekly/monthly 的 SUSPECTED_MISSING 完整业务键，三个证券/三个参考表内容前后一致；结果统计 UNKNOWN/null，不产生 MISSING/EXTRA。
- [x] 执行设计中的 ProductionApplicationContextIT、IntegrityCheckRunnerIT、IntegrityReadRepositoryIT 真实 MySQL 组，0 skip；执行完整后端 `mvn ... test`。
- [x] 独立规格/代码审查，针对有效缺陷补反例再修正，最终记录命令/结果。
- [x] 先将 T08 记为 COMPLETED，再完成并链接 T09 专属设计、next-task handoff 和 READY；仅暂存本轮文件，保留隔离区。
