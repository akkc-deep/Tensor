# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M04-T05`
- **Next task:** `M04-T06`
- **Design document:** `docs/task-designs/M04-T06-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **ID and title:** `M04-T06`，V6 fixture 表与 49 表结构总校验。
- **Goal:** 在 `tensor-app` 中建立永久 MySQL 8.4.6 Flyway/schema 契约门禁：以生产 V1～V5 和测试专用 V6 迁移 49 张 Tushare 表及一张 fixture 表，通过公开 `DatasetDefinitionLoader` 和 `information_schema` 逐表验证 schema，同时保证生产 JAR 只含 V1～V5。
- **Scope:** 只修改 `data-plane/tensor-app/pom.xml`，增加 `org.testcontainers:junit-jupiter` 与 `org.testcontainers:mysql` 两项 BOM 管理的 test-scope 依赖；只创建测试资源 `V6__create_fixture_tables.sql` 和测试类 `FlywaySchemaContractIT.java`。不得修改生产 V1～V5、父 POM、生产 Java、YAML、模板或其他模块，不得实现 fixture plugin、持久化、查询、下载、REST 或前端职责，也不修改 Surefire/Failsafe 生命周期。
- **Acceptance criteria:** 严格 TDD 依次取得缺测试类 RED、完整测试缺 V6 时“五项对六项”RED，并在官方 `mysql:8.4.6` 上通过 49 个动态生产表契约与 3 个固定测试；Flyway 首次执行 V1～V6 六项、validate 成功、二次 migrate 为零项；生产维持 49 表/851 业务列/1000 总列/49 PRIMARY/40 二级索引，加 fixture 后为 50 表/1007 列/50 PRIMARY/40 二级索引；fixture 精确为批准的 7 列复合键表；生产 JAR 恰含 V1～V5，V6 与测试类只进入测试输出；reactor、Enforcer、依赖范围、Git 范围、格式和清理门禁通过，最终实现提交只含设计 Files 节三个文件。

## Dependencies

### `M04-T01`

- **Artifact:** `data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql`，最终实现提交 `09dbbfd`。
- **Decision:** V1 固定创建 11 张基础与组织表、93 个原序业务列、127 个总列、11 个 PRIMARY 和 6 个最小二级索引；`stk_managers` 是唯一 V1 FINGERPRINT 表，以内部 `business_key CHAR(64)` 为主键。
- **Rationale:** M03-T02 的 11 份 YAML 已冻结列、键和 filters；V1 机械映射这些契约并建立统一来源字段，作为后续迁移和总校验的首个生产 schema 基线。
- **Constraint:** M04-T06 只能读取和迁移该已发布 SQL，不得修改；必须保留业务列顺序/类型/可空性、`stk_managers` 技术键、三个来源字段、InnoDB、`utf8mb4_0900_as_cs` 和精确索引集合。
- **Usage:** `FlywaySchemaContractIT` 从测试 classpath 的生产资源加载 V1，并用 11 个对应 YAML 定义逐表对照其 schema。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；官方 MySQL 8.4.6 harness 已记录 `M04-T01_OK:11:93:127:6`，Flyway migrate/validate/二次零项、reactor 150/150、JAR/范围/格式/清理和无发现独立审查通过；当前 V1 与提交 `09dbbfd` 无差异。

### `M04-T02`

- **Artifact:** `data-plane/tensor-app/src/main/resources/db/migration/V2__create_market_and_trading_tables.sql`，最终实现提交 `0967474`。
- **Decision:** V2 固定创建 13 张行情、交易与资金表、133 个原序业务列、172 个总列、13 个 PRIMARY 和 12 个最小二级索引；全部使用 COMPOSITE 键。
- **Rationale:** M03-T03/M03-T04 的 13 份 YAML 已冻结字段、业务键和 filters；V2 延续 V1 的机械类型、来源字段和最小索引规则，组成 V1～V2 的 24 表基线。
- **Constraint:** M04-T06 不得修改 V2；复合主键顺序、主键前缀索引省略规则、来源字段、InnoDB 和排序规则必须原样接受并由契约测试验证。
- **Usage:** `FlywaySchemaContractIT` 在 V1 后迁移 V2，以 13 个对应 YAML 定义验证列、键和 12 个二级索引。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；官方 MySQL 8.4.6 harness 已记录 `M04-T02_OK:24:13:133:172:12`，全局 24 表/299 列/24 PRIMARY/18 二级索引及 reactor 150/150、JAR/范围/格式/清理和无发现独立审查通过；当前 V2 与提交 `0967474` 无差异。

### `M04-T03`

- **Artifact:** `data-plane/tensor-app/src/main/resources/db/migration/V3__create_connect_and_slb_tables.sql`，最终实现提交 `5fa8ec6`。
- **Decision:** V3 固定创建 6 张互联互通与转融通表、44 个原序业务列、62 个总列、6 个 PRIMARY 和 4 个最小二级索引；全部使用 COMPOSITE 键，并保留 `hk_hold.code` 与 `hk_hold.ts_code` 的不同职责。
- **Rationale:** M03-T05 已冻结六份 YAML；即使三个 SLB 模板没有样例行，44 列、业务键和 filters 仍是可执行契约，V3 只做机械 DDL 转换。
- **Constraint:** M04-T06 不得修改 V3、删减空样例字段或合并 `hk_hold` 两列；必须按 YAML 验证列、键、4 个二级索引、来源字段和统一表属性。
- **Usage:** `FlywaySchemaContractIT` 在 V1/V2 后迁移 V3，以 6 个对应 YAML 定义补齐生产 schema 的第 25～30 张表。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；官方 MySQL 8.4.6 harness 已记录 `M04-T03_OK:30:6:44:62:4`，全局 30 表/361 列/30 PRIMARY/22 二级索引、reactor 150/150、JAR/范围/格式/清理及两层无发现审查通过；当前 V3 与提交 `5fa8ec6` 无差异。

### `M04-T04`

- **Artifact:** `data-plane/tensor-app/src/main/resources/db/migration/V4__create_financial_tables.sql`，实现提交 `9105ad5`，最终格式提交 `fcb64e4`。
- **Decision:** V4 固定创建 9 张财务与披露宽表、490 个原序业务列、517 个总列、9 个 PRIMARY 和 8 个最小二级索引；全部使用 COMPOSITE 键，三个长文本字段保持 TEXT，`fina_mainbz` 不增加 `ann_date` 业务列。
- **Rationale:** M03-T06 已冻结九份宽表 YAML；字段宽度、长文本和参数/列差异必须由实际 MySQL schema 验证，不能按样例是否为空或字段语义重新推断。
- **Constraint:** M04-T06 不得修改 V4 或缩窄宽表；必须保持 490 列顺序/类型/可空性、九个主键、八个二级索引、三个来源字段和统一表属性。
- **Usage:** `FlywaySchemaContractIT` 在 V1～V3 后迁移 V4，以 9 个对应 YAML 定义验证宽表，并把生产累计扩展到 39 表/878 列。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；官方 MySQL 8.4.6 harness 已记录 `M04-T04_OK:39:9:490:517:8`，全局 39 表/878 列/39 PRIMARY/30 二级索引、reactor 150/150、JAR/范围/格式/清理和无发现独立审查通过；当前 V4 与最终提交 `fcb64e4` 无差异。

### `M04-T05`

- **Artifact:** `data-plane/tensor-app/src/main/resources/db/migration/V5__create_corporate_and_governance_tables.sql`，最终实现提交 `2790ee5`。
- **Decision:** V5 固定创建 10 张公司行动、股东与治理表、91 个原序业务列、122 个总列、10 个 PRIMARY 和 10 个最小二级索引；`pledge_detail` 保留 14 个 nullable 指纹输入列并以内部 `business_key CHAR(64)` 为主键。
- **Rationale:** M03-T07/M03-T08 的十份 YAML 已冻结列、键和 filters；V5 完成 49 张 Tushare 表，并用与 V1 相同的 FINGERPRINT 技术键规则处理 `pledge_detail`。
- **Constraint:** M04-T06 不得修改 V5 或收紧 `pledge_detail` 业务列；必须保持 9 个 COMPOSITE 和 1 个 FINGERPRINT 主键、10 个二级索引、来源字段及统一表属性。
- **Usage:** `FlywaySchemaContractIT` 在 V1～V4 后迁移 V5，以最后 10 个 YAML 定义完成 49 张生产表逐表对照，并将两项 FINGERPRINT 作为字面总契约验证。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；官方 MySQL 8.4.6 harness 已记录 `M04-T05_OK:49:10:91:122:10`，全局 49 表/1000 列/49 PRIMARY/40 二级索引、reactor 150/150、JAR/范围/格式/清理和无 Critical/Important 独立审查通过；当前 V5 与提交 `2790ee5` 无差异。

五项输入按 Flyway 版本顺序互不重叠，分别贡献 11+13+6+9+10=49 张表、127+172+62+517+122=1000 列和 6+12+4+8+10=40 个二级索引；全部使用同一 `tushare_pro__<api>` 表名公式、机械逻辑类型映射、三个来源字段、主键/最小索引规则、InnoDB、`utf8mb4_0900_as_cs` 与 UTC 迁移会话。仅 V1/V5 各含一项 FINGERPRINT 技术键，已由 M04-T06 设计统一处理；不存在未解决的依赖决策或约束冲突。

## Start Here

1. 完整读取 `docs/task-designs/M04-T06-design.md`，以其 POM 例外、fixture 精确结构、52 次测试结构、失败边界和结果级验收作为唯一实施合同。
2. 核对 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 Global Constraints、Task M04-T06 和 Module Gate。
3. 按版本顺序读取 `data-plane/tensor-app/src/main/resources/db/migration/` 下 V1～V5，确认不修改历史迁移。
4. 读取 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoader.java`、plugin API 的 dataset records 和 49 份 `datasets/tushare_pro/*.yaml`。
5. 读取父 POM 的 Testcontainers BOM 与 `data-plane/tensor-app/pom.xml`，保持只增加两项批准的 test-scope 依赖。

首个实施动作：运行设计 Tests 节的基线命令 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am test`，确认现有 reactor 150/150 后，才在 app POM 增加两项批准依赖并按设计取得缺测试类 RED。

## Risks

- Testcontainers 依赖可用 Docker daemon 和固定官方 `mysql:8.4.6` 镜像；不可用时必须报告环境阻塞，不得 skip、改标签或换数据库。
- `FlywaySchemaContractIT` 采用任务卡指定的 `*IT` 名称，现有 Surefire 默认不会运行它；本任务必须使用设计中的显式 `-Dtest` 模块门禁，并保留上游模块所需的 `-Dsurefire.failIfNoSpecifiedTests=false`，不得擅自扩展构建生命周期。
- 49 个动态期望从公开 YAML 构造，必须保留全局字面 totals、两项 FINGERPRINT、fixture 字面 schema、资源隔离和精确索引规则这些独立约束，不能从实际 SQL/数据库自举期望。
- V1～V5 是已发布历史；若契约发现真实漂移，只能停止并另行设计前向生产迁移，不得修改历史 SQL 让测试变绿。
