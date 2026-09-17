# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`。
- **Completed task:** `DATA-INTEGRITY-T03`（COMPLETED）。
- **Next task:** `DATA-INTEGRITY-T04`（按 Order=4 选择）。
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T04-design.md`。
- **Expected next status:** `READY`；完整设计已链接，本交接写入后从 `NOT_STARTED` 转为 `READY`，未启动实施。

## Next Task

`DATA-INTEGRITY-T04`：精确集合比较、通用规则与 fixture。

在已验收的读取组件上实现真实 `IntegrityContext.compare`、三条固定 core 规则、规则归属的问题收集与单元聚合。设计已固定同步 `IntegrityUnitEvaluator.evaluate` 入口，供 T07 在报告事务外调用；不实现任务调度、报告持久化或 Tushare 候选推导。

验收必须证明合法范围内 E=20、命中19、额外1时覆盖率为95%，额外不能抵消缺失；可靠空集、未知全集、局部确认与未知并存分别正确。完整财报/事件键、DECIMAL数值相等与既有指纹、nullable、空日期和来源身份均有具体定位；已知FAIL不被PASS/UNKNOWN覆盖；读取、预算及问题上限中断不产生整体比例。现有 fixture 与真实比较器相连，但不能被当成生产 Tushare 基线。

## Dependencies

### DATA-INTEGRITY-T01

- **Artifact:** `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/integrity/` 下的 `IntegrityContext.java`、`IntegrityExpectedKeys.java`、`IntegrityStatistics.java`、`IntegrityRuleResult.java`、`IntegrityIssue.java`、`IntegrityUnitResult.java`、`IntegrityContracts.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckJson.java`；`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/integrity/FixtureIntegrityRules.java`；`docs/task-designs/DATA-INTEGRITY-T01-design.md`。
- **Decision:** 复用不可变完整键、PROVEN/UNCONFIRMED、精确统计和规则描述；三条 core 规则 ID/version 不变。局部确认通过既有问题及局部证据表达，全范围仍使用原 scope；不扩 plugin-api。指纹必须调用既有 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java`，DECIMAL保持声明scale。
- **Rationale:** 结论、哈希和历史报告共享稳定合同；不能以展示JSON、浮点值或另写哈希替代完整键比较，不能把局部依据升级成全范围可靠。
- **Constraint:** 现有 fixture 的 PROVEN 窗口为2026-01-01..20，已经包含每个日期；该范围不能容纳第21个额外日期。20/19/1须用专属设计确定的测试规则及Jan1..21范围；原fixture另验，不改其生产合同。UNKNOWN保留null，可靠空集不计算0/0。
- **Usage:** 实现 context、键规范化、三条规则、问题收集器和 evaluator，执行实际比较并按既有JSON保持Long/BigDecimal精度；来源及core规则独立消费不可变输入。
- **Readiness evidence:** T01 的 `docs/verification/DATA-INTEGRITY-T01.md` 已记录合同通过。T03最终后端回归1283项全部通过，其中 fixture `IntegrityPluginContractTest` 17项及 core同名注册合同7项仍通过；此证据不宣称T04已实现。

### DATA-INTEGRITY-T03

- **Artifact:** `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityReadRepository.java`、`IntegrityReadPlan.java`、`IntegrityReadBudget.java`、`IntegrityReadException.java`；`docs/task-designs/DATA-INTEGRITY-T03-design.md`；`docs/verification/DATA-INTEGRITY-T03.md`。
- **Decision:** `withSnapshot` 独占一个RR只读连接，scope由 `session.scope()` 提供真实读取时点；`scanTarget` 单列空日期批次。目标与同来源已声明参考在同快照内读取，全部真实扫描共享预算；预期键生成必须使用同一budget.consume。预算与扫描失败锁存，上层捕获后仍不能成功。
- **Rationale:** 保证集合比较依据来自一致时点，防止跨股票/来源/日期读取及部分扫描被当成全量结果。
- **Constraint:** 不修改读取公开入口或授权边界，不复用records分页，不把JDBC给插件。空日期不得进入范围命中；读取记录中的实际范围/条件可用于证据，较大的参考窗口不能修改目标scope。声明类型不符的数据库值会先READ_FAILED，不能由T04吞掉。
- **Usage:** evaluator 在报告写事务之外调用withSnapshot；一次scanTarget构建预算内的不可变目标输入，来源scan继续直接委托session；compare逐个预期键consume并检查deadline。处理仓库出口检查/清理失败，保留已知问题但标ERROR/incomplete及null整体比例。
- **Readiness evidence:** 30项专项测试通过（18 unit + 12个实际MySQL 8.4.6 IT），0失败/错误/跳过；完整后端1283项通过；最终独立复审Critical 0 / Important 0。证据和准确命令均见T03验收文档。

上述直接输入的精确类型、不可变范围、证据强度及累计预算约束一致，无未解决冲突。

## Start Here

1. `docs/task-designs/DATA-INTEGRITY-T04-design.md`（完整实施设计，含入口、失败处理、精确输入和命令）。
2. `docs/task-handoffs/data-integrity-task-board.md` 的T04，及 `docs/task-designs/DATA-INTEGRITY-design.md` 第1、4、5、6节。
3. T01上述合同、`FixtureIntegrityRules`、`FingerprintKeyCodec`，及 `docs/runbook/data-integrity-rules.md` 第2.3、3节。
4. T03上述读取组件、设计、真实MySQL测试及验收结果。

第一步：在 `IntegrityComparisonTest` 写入设计固定的合法Jan1..21范围、E=Jan1..20、A=Jan1..19加Jan21的失败测试，断言19命中、1缺失、1额外及`0.950000`，再实现精确键比较和同步单元入口。不能用捕获expected后直接返回假统计来让用例通过。

## Risks

工作区仍为 `.worktrees/data-integrity` / `feat/data-integrity`，含既有Studio/T01/T02及T03暂存改动，保留它们；尚未合回原工作区。目标缓存有行数预算但宽表仍占内存；不静默缩小范围。MySQL IT需要本机Docker/端口权限，必须实际执行，不能skip后宣称验收。此交接只有已验收输入和实施设计，T04尚无实现或通过结果。
