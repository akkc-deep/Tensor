# DATA-INTEGRITY-T13 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 完成真实 fixture、SQL、HTTP、浏览器、版本历史及既有回归的证据闭环。

**Architecture:** 复用通用检查链路，仅扩展 acceptance fixture；用自有 MySQL 与 JVM 验证真实报告。stub 与真实结果独立登记。

**Tech Stack:** Java 21, Node 24.15.0, MySQL 8.4.6, Maven, Playwright, Python 3.

**Spec:** `docs/task-designs/DATA-INTEGRITY-T13-design.md`

## Global Constraints

- `.worktrees/data-integrity` / `feat/data-integrity` 内实施，保留 Studio/T01–T12 混合暂存基线；精确 git add，不提交混合基线、不合并。
- PROVEN_EXTRA 可靠窗口2026-01-01..21、预期1..20；PROVEN原窗口1..20；PROVEN_EMPTY为空；超出窗口 UNCONFIRMED。
- fixture 版本2/3，版本3增加 fixture.acceptance.extension@1；core/UI 不新增来源或规则 ID 分支。
- 真实 IT 必须 MySQL且 skip=0；上游接收器计数0；证券全字段前后相同。
- 不修改 scripts/verify-contracts.sh 的 clean committed main 前提；最终缺失该门禁不能标 COMPLETED。
- 新文件加入 Git；仅清理自有进程/容器，凭据不入版本控制。

### Task 1: Fixture 规则及真实 HTTP/SQL

**Files:** fixture 模块 FixtureIntegrityRules.java、FixturePlugin.java、FixtureConfiguration.java；新增 FixtureIntegrityExtensionRule.java；对应 fixture tests；新增 app fixture/IntegrityFixtureFlowIT.java。

**Interfaces:** fixture 配置 `tensor.plugins.fixture.integrity-version` 仅2/3，默认2；六完整性端点合同保持；SQL fixture__fixture_daily使用现有7列。

- [x] 新建真实 HTTP IT，首先用 PROVEN_EXTRA Jan1..19+21 请求 Jan1..21；断言 `coverageRate="0.950000"`，`expectedCount="20"`，`matchedCount="19"`，`missingCount="1"`，`extraCount="1"`，Jan20 MISSING及Jan21 EXTRA。
- [x] 运行 `mvn -o -f data-plane/pom.xml -Dtest=IntegrityFixtureFlowIT -Dsurefire.failIfNoSpecifiedTests=false test`，观察 UNKNOWN/null 的行为失败。
- [x] 实现设计第1节的可靠窗口和版本规则；保留原场景，非法配置阻止启动；规则3为通用 FIELD PASS且合成证据。
- [x] 扩展IT验证六端点/重放/冲突、可靠空/未知/无记录股票、递归CTE独立SQL复算、证券逐行及SHA256、上游计数0。
- [x] 同库应用重启2→3，A旧JSON/响应不变；旧submission重放A，旧hash新ID409，新hash B显示@3/extension；覆盖fixture版本/注册/窗口单测。
- [x] 运行专项测试，登记真实RED/GREEN命令及计数，精确暂存新增与修改文件；不提交。

### Task 2: 真实浏览器与可复现环境

**Files:** 新增 control-plane/e2e/integrity-fixture.spec.js、integrity-fixture.helpers.js、scripts/verify-integrity-fixture.py；最小更新download-outcomes.spec.js、dataset-query.spec.js的V9断言；四套旧回归仅按实测同步过时Studio展示定位（另含tushare-metadata.spec.js、fixture-flow.spec.js）。

**Interfaces:** 使用Task1的fixture@2/@3；ACCEPTANCE_JAR、TENSOR_DB_URL/USERNAME/PASSWORD、M14_DB_SCHEMA、M14_MYSQL_DEFAULTS_FILE；脚本接受实际绝对 `--acceptance-jar`。

- [x] 按设计第4节实现独立真实套件；创建→95%→Jan20完整键→刷新/历史→SQL UPDATE Jan21为20→再次确认→新ID100%/PASS且旧报告95%；证券各次检查前后相同。
- [x] 验证PROVEN/PROVEN_EMPTY/UNCONFIRMED及同库版本2→3，扩展证据通用展示；1440/1024/390截图和无页面溢出，窄屏键盘问题定位。
- [x] 实现仅loopback/schema/defaults验证、8080空闲、health、自有JVM关闭及上游计数器；缺环境显式失败。
- [x] Python启动自有mysql:8.4.6，五schema，随机临时凭据0600及限定授权；串行T13及四旧套件，任何失败汇总非零，异常清理自有资源。
- [x] V9同步为1..9迁移/55表，保留旧业务断言；构建acceptance JAR后运行Python真实套件并保存安全证据。
- [x] 精确暂存，不提交。

### Task 3: 完整回归、文档与证据（BLOCKED：最终main门禁）

**Files:** docs/runbook/configuration.md、data-integrity-rules.md、docs/task-designs/DATA-INTEGRITY-design.md；新增 docs/verification/DATA-INTEGRITY-T13.md及data-integrity-t13证据；任务看板。

**Interfaces:** 消费本次Surefire/XML、Playwright、SQL/JSON/截图、JAR SHA；公开合同只在发现实际不一致时修改。

- [x] 运行完整性专项真实IT、全前端单测/构建/39 stub，并记录逐方法结果。
- [x] acceptance和production各执行 `mvn -o -f data-plane/pom.xml clean verify`；核对IT执行、失败/错误/跳过。
- [x] 更新运维及实施状态，解释六端点、长期报告、预算/重启、FAIL/UNKNOWN/N/A、合成95%与生产未知边界；verify-contracts脚本最小同步V9方法名/证据统计，保留所有集成门禁。
- [x] 将共享设计10项验证与15项测试逐行映射到本次具体方法和证据。
- [x] 独立审查本次差异、修复实质问题并做针对回归；精确Git跟踪。
- [x] 所有隔离区工作已完成，最终审查APPROVED；记录明确pause交接并给用户具体可审查结果。
- [ ] 在用户授权、代码一致的clean committed main实际通过最终合同门禁，记录集成SHA；当前BLOCKED，未写COMPLETED。

## 2026-09-17 远程 main 同步后续

用户要求同步远程 main 到既有隔离区并继续完成 T13；仓库指令允许直接在 main 工作。先保存恢复 stash，再同步 `origin/main@5aaf6ad`、逐项合并并独立审查；原“不提交混合基线”的保护仍适用。当前相对 main 已去除旧 Studio 差异，只集成已审查的数据检验功能与依赖。本轮验证、源代码一致性及最终真实 main 门禁见 `docs/verification/data-integrity-t13/main-sync/README.md`。旧暂存树/旧验证数据作为历史保留，不冒充本轮结果。
