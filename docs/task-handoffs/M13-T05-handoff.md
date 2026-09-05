# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M13-T04`
- **Next task:** `M13-T05`
- **Design document:** `docs/task-designs/M13-T05-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M13-T05`
- **Title:** 独立 acceptance JAR 打包及启停验收
- **Goal:** 为 M14-T01 提供可独立 `java -jar` 启动、含 fixture 与 V6 的验收包，维持现有生产 JAR 内容合同。
- **Scope:** 只修改 `data-plane/tensor-app/pom.xml`，新增 `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java` 和 `docs/runbook/acceptance.md`。显式 Maven `-Pacceptance` 在 Boot repackage 后使用 AntRun 3.1.0 装配同次生产包、原 fixture JAR 和原 V6；输出固定为 `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`。保留五模块、fixture test scope、原生产 repackage 和原四项打包合同；不修改业务 Java/Vue/YAML/SQL，不增加故障注入、下载或 E2E，不 attach/install/deploy 验收包。
- **Acceptance criteria:** 默认构建保持前端 120、Surefire 368、原 Failsafe 4 及唯一顶层生产 JAR，无验收包；显式构建保持相同基线并额外执行三个验收归档合同，共七项 Failsafe，全部零失败/错误/跳过。三个合同验证精确文件集合及 SHA-256 保留、原 fixture/V6、Boot manifest、STORED 嵌套库、输出目录与无遗留 `.tmp`。真实 Java 21/MySQL 8.4.6 下验证验收包的 acceptance+true、同库 acceptance+false 重启、production+true，以及原生产包在另一空 schema 的 acceptance+true；检查 health、原四项 smoke、页面刷新、只读元数据、fixture 显隐、Tushare 摘要、V1～V6/50 业务表与生产 V1～V5/49 业务表隔离和正常停止。完成严格 RED、默认/显式/无 clean 重建/默认 clean 回归及范围/格式/链接/Git 门禁后，以 `build: add isolated acceptance jar` 提交精确三个实施文件。

本交接记录设计与输入就绪；上述新增构建、归档测试和真实验收包启停均为 M13-T05 后续实施工作，尚未执行。

## Dependencies

### `M13-T04`

- **Artifact:** `docs/runbook/first-run.md`、`docs/runbook/configuration.md`、`scripts/smoke-test.sh`，以及生产构建产物 `data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`。运行约束的设计来源为 `docs/task-designs/M13-T04-design.md`；target 为可重建产物，不加入 Git。
- **Decision:** 生产分发继续使用单 JAR、同源页面/API、V1～V5 和 49 张业务表；缺 Tushare Token 可首跑，下载不可用。原 smoke 只读 GET health、downloads、datasets、data-sources，不执行下载。使用 schema 级 CREATE/SELECT/INSERT/UPDATE 应用账号和隐藏秘密输入。
- **Rationale:** 验收包复用已经验证的生产入口、页面、依赖、配置和操作步骤，新增部分仅为 fixture 与 V6 的隔离分发。
- **Constraint:** 不修改两份生产 runbook、smoke 或原生产打包合同；保留 `120s < 130s <= proxy` 和每阶段 70s 停机语义，等待 JVM 自行退出。验收库必须独立为 `tensor_acceptance`，不能复用生产库或把含 V6 的库转作生产库；history 表不计入业务表数。秘密、日志、响应、备份与构建产物不得提交。
- **Usage:** 以同次 reactor 生产 JAR 作为归档基础，仅替换 manifest、去除两个索引并附加原 fixture/V6；新说明引用既有账号、配置、终端秘密输入与停机步骤，在新分发目录复用原 smoke，另做只读元数据和 fixture 显隐验证。
- **Readiness evidence:** 实现 `59acec3`，完成状态由 `bb26660` 记录。看板已提供 2026-09-05 14:00:04（Asia/Shanghai）正常 JVM 权限下 `mvn -f data-plane/pom.xml clean verify` 成功结果：前端 120、Surefire 368、Failsafe 4，零失败/错误/跳过；最终 smoke 临时黑盒矩阵 107/107。真实 Java 21.0.11/MySQL 8.4.6、全新分发目录与 schema 级账号完成首跑、同库重启、两页直接访问/刷新、Token 哨兵无泄漏和正常停机；生产包排除 fixture/V6，V1～V5 五条成功 history 与 49 张业务表成立。一次性数据库已停止删除。这些是已记录的前置证据，本交接未重跑这些验证。

### `M08-T02`

- **Artifact:** `data-plane/tensor-plugin-fixture/target/tensor-plugin-fixture-1.0-SNAPSHOT.jar`；公开输入为 `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java`、`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`、`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureScenario.java`、`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureEnvelopeFactory.java`，以及 `data-plane/tensor-plugin-fixture/src/main/resources/datasets/fixture/fixture_daily.yaml`。行为合同见 `docs/task-designs/M08-T02-design.md`。
- **Decision:** 仅 Spring `acceptance` profile 与 `tensor.plugins.fixture.enabled=true` 同时满足时注册 plugin/adapter；保持五个确定性场景与真实适配器。fixture 模块继续为 app test-scope 依赖，验收包附加完整原模块 JAR，不把其 class/YAML 摊平到外层。
- **Rationale:** 复用现有组件扫描和适配器扩展接缝即可让打包实例发现 fixture；保留原模块边界和双条件，无需生产业务代码改动。
- **Constraint:** 不改 fixture 源码、依赖或场景语义，不把所有 test-scope 依赖打入验收包。`PERSISTENCE_FAILURE` 仅有 note 标记，不代表打包实例已有数据库故障注入；不能把测试数据源带入本包或宣称后续失败矩阵完成。
- **Usage:** 从本次 reactor 构建的精确路径复制原 JAR 到 `BOOT-INF/lib/`；合同校验原始 SHA-256、四个公开类型、YAML、无测试/秘密及 STORED 存储；以 HTTP 元数据验证双条件、禁用重启与 Tushare 摘要保持。
- **Readiness evidence:** 实现 `885313d`、最终测试强化 `54c2b30`。看板记录当时完整 reactor verify 为 272/272，含 fixture 12 项，零失败/错误/跳过；依赖、Enforcer、ArchUnit、fixture JAR 四生产类型/YAML、app 生产隔离与独立审查通过。五种场景、真实 adapter 边界、安全分派及双条件两 Bean 已验证；该历史总数不替代当前 368 项 Surefire 基线，也不证明新验收包已经启动。

### `M04-T06`

- **Artifact:** `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`；结构合同来源为 `docs/task-designs/M04-T06-design.md` 和 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`。
- **Decision:** V6 保留为测试资源，只建立七列 `fixture__fixture_daily` 表，主键 `(ts_code, trade_date)`，无二级索引；保持既定类型、来源字段、InnoDB、`utf8mb4_0900_as_cs` 与 UTC 规则。
- **Rationale:** 独立验收库需要与 fixture 元数据一致的真实表；附加原 V6 可复用已经验证的结构，同时维持生产资源仅 V1～V5。
- **Constraint:** 不修改 V1～V6，不移动 V6 至主资源；只能把精确 V6 复制到验收包 `BOOT-INF/classes/db/migration/`，不能复制整个 test resources/test classes。验收包中 V6 对 Flyway 始终可见，fixture 关闭只改变 Bean/目录，不回滚迁移或删除表/history。
- **Usage:** 对归档 V6 与原文件比较 SHA-256；在独立 MySQL 首跑验证六项成功迁移、50 张业务表，随后同库重启验证迁移不重复；原生产 JAR 用另一空 schema 验证五项迁移/49 张业务表。
- **Readiness evidence:** 实现 `e78bd98`；看板提供实际 MySQL 8.4.6 下 schema 合同 52/52、首次 V1～V6 migrate、validate 成功及第二次 migrate 零项的结果。最终总量为 50 张业务表、1007 列、50 个主键、40 个二级索引，生产 JAR 排除 V6/测试类。该证据证明原 SQL 可用，新增验收 JAR 的资源可见性与真实启停仍须在本任务验证。

三个直接输入的约束一致：生产包保留原隔离合同，验收包在独立子目录只附加原 fixture JAR 和原 V6；双条件控制目录暴露，独立 schema 承担始终可见的 V6。无须改变生产依赖或迁移来满足验收运行。设计已由 `ab5ce88` 提交并链接到看板；独立只读设计审查结论为 `Ready for implementation: Yes`，无 Critical/Important/Minor。审查核对 Maven profile 合并顺序、两个 Failsafe execution/汇总行为和运行接缝，未执行新构建或运行验收。

## Start Here

按顺序读取：

1. `docs/task-designs/M13-T05-design.md`，完整读取。
2. 本交接，以及 `docs/task-handoffs/tensor-v1-task-board.md` 的 M13-T05 行与详情。
3. `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 Global Constraints、Task M13-T05、Module Gate。
4. `data-plane/tensor-app/pom.xml`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java`，以及 M13-T04 两份 runbook 和原 smoke。
5. `docs/task-designs/M08-T02-design.md` 与上述 fixture 公开配置/描述符，`docs/task-designs/M04-T06-design.md` 与原 V6；只读核对 `data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java` 和 `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java` 的既有扫描/适配器接缝。

首个实施动作：确认三个目标文件没有重叠用户修改，按完整设计创建恰好三项归档测试，只加入 Surefire 排除和 acceptance profile 内独立 Failsafe 绑定，暂不加入 AntRun 装配；运行 `mvn -f data-plane/pom.xml -Pacceptance clean verify`，取得前端/后端/原生产四项合同通过、仅因验收 JAR 缺失而失败的严格 RED。依赖解析、编译、JVM attach 或错误测试选择失败不能充当 RED。随后按设计实现装配与运行说明，再依次完成构建矩阵和真实四种启动状态，无需重新选择打包方案或补写设计。

## Risks

- 验收包即使禁用 fixture 仍包含 V6；始终使用独立 `tensor_acceptance` schema，原生产包使用另一空 schema，不能混用数据库迁移历史。
- 嵌套 JAR 必须 STORED；验收包会更大。移除 classpath/layers 索引时必须同时移除 manifest 对应属性，不沿用过期索引。
- 现有生产依赖覆盖 fixture 运行依赖；后续 fixture 依赖变化需要重新评估白名单与真实启动，不能自动收集 test-scope 依赖。
- AntRun 首次下载、正常 JVM attach、Java 21、MySQL 8.4.6 及浏览器验证环境仍是实施验证条件；不得 skip 或放宽原合同。遇到已知 Byte Buddy 沙箱限制，只能原命令在正常 JVM 权限下重跑。
- 本任务只读运行验证不覆盖页面下载闭环或持久化故障矩阵，后续任务仍需各自实施验收。
- 保留工作区既有修改与未跟踪 Maven target；仅提交批准的源码/文档，不提交临时环境、数据库、凭证或生成产物。
