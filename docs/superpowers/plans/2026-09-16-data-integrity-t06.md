# Data Integrity T06 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 固定检查范围、幂等受理及有界队列准入。

**Architecture:** 复用 T02 本地插件能力与 T05 独立事务存储；服务锁串行受理，队列预留覆盖事务与发布。原请求先重放，首次受理固定全插件快照与选中范围。

**Tech Stack:** Java 21、Spring Boot 3.5、JUnit 5、MySQL 8.4.6/Testcontainers。

**Spec:** `docs/task-designs/DATA-INTEGRITY-T06-design.md`（完整接口、文件清单、校验顺序与验收案例的权威来源）。

## Global Constraints

- 在 `.worktrees/data-integrity` 保留已有混合暂存基线；仅暂存本次文件，不提交混合基线或合并。
- 100 股票 / 36600 闭区间自然日 / 4000 单元 / 20 排队；计数等于上限允许。全部配置正数，workers=1。
- 不执行检查、不启动工作线程、不新增 HTTP 入口、不查询或修改证券数据。

### Task 1: 原请求受理与队列

Files: 设计 Files 表中的 core `IntegrityCheckService.java`、`IntegrityCheckQueue.java` 及其 Test/IT，plugin-api `IntegrityCheckSupport.java`、`ErrorCode.java`。

Interfaces: `submit(Map<String,Object>) -> SubmissionResult`、`capability(PluginId) -> Capability`；`reserve() -> Reservation`、`Reservation.publish(UUID)`、`poll(Duration) -> Optional<UUID>`。

- [x] RED：先断言旧请求在不可用插件及满队列条件下返回旧任务，且无第二次发布；补充严格输入、范围边界、完整快照与队列生命周期案例。
- [x] 执行 `mvn -o -f data-plane/pom.xml -pl tensor-core -am '-Dtest=IntegrityCheckServiceTest,IntegrityCheckQueueTest' '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dsurefire.failIfNoSpecifiedTests=false test`，确认缺失受理入口导致失败。
- [x] GREEN：实现设计 Approach 1–4 的顺序与失败语义；先 `findBySubmissionId`，再校验/固定，`try (var reservation = queue.reserve()) { repository.create(task, units); reservation.publish(task.checkId()); }`，最后读取结果；唯一冲突退出 reservation 后查询赢家。
- [x] 真实 MySQL 验证设计 Tests 6–8：同/异请求竞争、跨服务唯一冲突、第二单元写失败回滚、满队列无记录、返回读取失败重放以及提交后发布。

### Task 2: 来源日期与应用装配

Files: 设计 Files 表中的 TushareProPlugin、IntegrityCheckProperties/Configuration、ApplicationConfiguration、GlobalExceptionHandler 及对应测试，`docs/runbook/configuration.md`。

Interfaces: `validateIntegrityRange(IntegrityDateRange, Instant)`；`Settings.defaults()`；`IntegrityCheckProperties.toSettings()`。

- [x] RED：固定时刻跨上海午夜验证 endDate，逐项0/负值及workers=2阻止绑定/启动；四个错误码验证 HTTP 状态。
- [x] GREEN：本地 default 日期 hook、Tushare 上海日校验、十项 properties 与纯 Bean 装配；不加 lifecycle。
- [x] 专项执行设计 Tests 中第一条 Maven 命令；完整后端 `mvn -o -f data-plane/pom.xml '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test`。

### Task 3: 审查与交接

- [x] 独立审查 T06 范围，修复实证问题并复验；`git diff --check` / `git diff --cached --check`。
- [x] 写 `docs/verification/DATA-INTEGRITY-T06.md` 实际证据，先记录 T06 COMPLETED。
- [x] 按看板 Order 完成 T07 专属设计与 next-task 交接，再标 READY；暂存所有新增文件，保留隔离区。
