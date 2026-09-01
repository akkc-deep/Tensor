# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M03-T09`
- **Next task:** `M04-T01`
- **Design document:** `docs/task-designs/M04-T01-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M04-T01`
- **Title:** V1 基础与组织表
- **Goal:** 创建生产 Flyway V1 迁移，以固定 MySQL 8.4 SQL 建立 M03-T02 的 11 张基础与组织来源表，逐列保持 93 个业务列、十个 COMPOSITE 键、一个 FINGERPRINT 键、来源字段、最小查询索引、InnoDB 和 `utf8mb4_0900_as_cs` 契约。
- **Scope:** 只创建设计 Files 节指定的一个 V1 SQL；使用 `/private/tmp` harness 和临时 `mysql:8.4` 容器做 RED/GREEN 与 `information_schema` 校验。不得修改 POM、Java、YAML、schema、template、应用配置或其他迁移，不得提交临时 harness、classpath、容器数据或生成物。
- **Acceptance criteria:** V1 在全新 MySQL 8.4 schema 中只执行一次并通过 validate/零项二次 migrate；11 表恰含 93 个原序业务列、33 个来源列和一个内部 `business_key`，总计 127 列；11 个主键、六个二级索引、类型/可空性/引擎/排序规则逐项匹配设计；临时 harness 输出 `M04-T01_OK:11:93:127:6`；reactor `test`/`verify`、六层 Enforcer、JAR 单迁移资源、范围、格式和清理门禁通过；实现提交精确包含一个 SQL。

## Dependencies

### `M03-T02`

- **Artifact:** `docs/task-designs/M03-T02-design.md`，以及提交 `5fe20a2` 中 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 `stock_basic`、`stock_company`、`hs_const`、`trade_cal`、`new_share`、`namechange`、`stk_managers`、`broker_recommend`、`index_classify`、`index_member`、`index_member_all` 共 11 份 YAML。
- **Decision:** 冻结 11 个精确表名、93 个业务列的名称/顺序/逻辑类型/长度/精度/可空性、十个 COMPOSITE 键、`stk_managers` FINGERPRINT 身份字段和 filters；`STRING/TEXT/DATE/MONTH/LONG/DECIMAL` 的数据库映射由 TRD 8.3 与 M04-T01 设计机械固定。
- **Rationale:** M03-T02 已消除模板未定义的列类型和可空性歧义，使 Flyway 迁移无需从样例值猜测或缩窄字段，并保持元数据为结构对照基线。
- **Constraint:** V1 必须原序保留全部 93 列和批准的 nullability；COMPOSITE 键按 TRD 顺序直接作为主键，`stk_managers` 的业务列继续可空且只增加内部 `business_key CHAR(64)` 主键；不得修改或从 SQL 反向生成 YAML 期望。
- **Usage:** 通过公开 `DatasetDefinitionLoader` 为临时 harness 提供逐列结构期望，并由本任务设计的独立表/索引矩阵生成可审阅的 11 个固定 `CREATE TABLE`。
- **Readiness evidence:** 权威看板为 `COMPLETED`；实现提交 `5fe20a2` 精确包含 11 份 YAML，后续 M03-T09 永久门禁提交 `36230d8` 已再次验证 49/49 总集合、851 列、参数、键和 filters，reactor 137/137、`verify`、Enforcer 和审查均已记录通过。

- **Dependency comparison:** M04-T01 只有 M03-T02 一个直接依赖；其 11 API、93 列、键和 filters 与 TRD 8.3/9.1～9.6、M04 任务卡及完成的 M04-T01 设计一致，不存在跨依赖冲突。

## Start Here

1. 完整读取 `docs/task-designs/M04-T01-design.md`，以其中机械类型映射、11 表/主键/索引总表、来源字段、Flyway harness、失败边界和测试命令作为唯一实施契约。
2. 核对 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 Global Constraints、Task M04-T01 和 Module Gate。
3. 核对 `docs/task-designs/M03-T02-design.md` 的 93 列类型图、11 份运行时 YAML、TRD 8.3 与 9.1～9.6，以及 `data-plane/tensor-app/pom.xml` 中既有 Flyway/MySQL 依赖；不得修改这些输入。
4. 首个实施动作：在 `/private/tmp/M04T01SchemaCheck.java` 创建设计规定的完整 Flyway/loader/`information_schema` harness，按设计生成隔离 classpath，并在 V1 SQL 尚不存在时运行它，确认只因 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql` 非零退出；随后才创建唯一生产 SQL。

## Risks

- 实际 schema GREEN 依赖可用 Docker daemon 和 `mysql:8.4`；环境不可用时不得改用 H2/SQLite 或修改 POM，应保留任务状态并报告环境阻塞。
- Maven 隔离本地仓库首次可能下载既定依赖；网络解析失败不是代码 RED，不得通过提交依赖二进制或放宽验证解决。
- `mysql:8.4` 官方维护补丁可能更新保留字或 `information_schema` 表达；所有标识符统一反引号，结果仍必须满足 MySQL 8.4 实际迁移和设计断言。
- 临时 harness 可从 YAML机械构造业务列期望，但主键、内部列和六个二级索引必须来自设计的独立字面矩阵，不能从 SQL 自举。
