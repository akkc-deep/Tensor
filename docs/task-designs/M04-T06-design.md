# M04-T06 V6 fixture 表与 49 表结构总校验——任务设计

任务编号：`M04-T06`
对应任务：[M04-T06](../superpowers/plans/tensor-modules/M04-flyway-schema.md#task-m04-t06-fixture-迁移与-49-表结构总校验35hjava-sql-test-harness)
实施产物：测试专用 `V6__create_fixture_tables.sql` 与永久 `FlywaySchemaContractIT`

## Goal

在 `tensor-app` 中建立永久 MySQL 8.4.6 Flyway/schema 契约门禁：测试 classpath 以生产 V1～V5 加测试专用 V6 迁移出 49 张 Tushare 表和一张 fixture 表，再通过公开 `DatasetDefinitionLoader` 加载全部 49 份 YAML，逐表对照 `information_schema` 验证列、类型、可空性、键、最小查询索引、引擎和排序规则。V6 必须只存在于测试资源，确保生产 JAR 继续只发布 49 张 Tushare 表对应的 V1～V5。

## Scope

包含：

- 只在 `data-plane/tensor-app/pom.xml` 增加 `org.testcontainers:junit-jupiter` 和 `org.testcontainers:mysql` 两项 `test` scope 依赖；版本继续由父 POM 已有 `testcontainers-bom` 的 `1.21.4` 管理，不写子模块版本；
- 创建测试迁移 `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`，只创建 `fixture__fixture_daily`；
- 创建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`，固定使用官方镜像 `mysql:8.4.6`；
- 通过 Flyway 从 `classpath:db/migration` 首次执行 V1～V6 六项迁移，执行 validate，再确认第二次 migrate 为零项；
- 通过公开 `com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader` 加载且只加载 49 份 `classpath*:datasets/tushare_pro/*.yaml`，生成 49 次动态生产表 schema 契约；
- 永久验证 fixture schema、全局表/列/索引总量和 main/test migration 输出隔离；
- 执行缺测试类 RED、缺 V6 RED、固定 MySQL 8.4.6 GREEN、reactor 回归、生产 JAR、依赖范围和 Git 范围门禁。

排除：

- 不修改父 POM、生产 V1～V5、生产 Java、49 份 YAML、JSON 模板、schema、应用配置或其他模块；
- 不把 V6 放入 `src/main/resources`，不让 fixture 表进入生产 JAR，也不创建生产 V6；
- 不实现 fixture 插件、fixture YAML、场景工厂、适配器、Upsert、查询、下载、REST、前端或 M05～M08 任务职责；
- 不修改 Surefire/Failsafe includes、生命周期或其他构建插件；`FlywaySchemaContractIT` 由任务卡的显式 `-Dtest` 模块门禁运行；
- 不修改已发布迁移来消除契约失败，不从实际 SQL 反向生成期望，不用 H2、SQLite 或浮动 MySQL 标签替代固定 MySQL 8.4.6。

用户已明确批准任务卡原文件列表之外只扩展 `data-plane/tensor-app/pom.xml`，且该扩展严格限于上述两项测试依赖；也已批准下文 fixture 表的精确结构。不存在其他隐含范围授权。

## Approach

### Fixture 测试迁移

`V6__create_fixture_tables.sql` 按以下固定内容和顺序实现：

1. `SET time_zone = '+00:00';`，固定迁移会话为 UTC；
2. 无 `IF NOT EXISTS` 地创建 `fixture__fixture_daily`；
3. 业务列依次为：
   - `ts_code VARCHAR(64) NOT NULL`；
   - `trade_date DATE NOT NULL`；
   - `amount DECIMAL(38,18) NOT NULL`；
   - `note VARCHAR(255) NULL`；
4. 随后依次追加 `source_plugin VARCHAR(64) NOT NULL`、`source_api VARCHAR(64) NOT NULL`、`ingested_at DATETIME(3) NOT NULL`；
5. 唯一键为 `PRIMARY KEY (ts_code, trade_date)`；不创建 UNIQUE 或二级索引，因为主键已覆盖唯一查询 filter `ts_code` 的最左前缀；
6. 表固定为 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs`。

所有表名和列名使用反引号。fixture 合计恰有 7 列、1 个 PRIMARY 和 0 个二级索引。名称来自既定 fixture plugin ID `fixture`、API `fixture_daily` 及表名公式 `<plugin_id>__<api_name>`。

### 测试生命周期与资源边界

`FlywaySchemaContractIT` 使用 JUnit 5、AssertJ、JDBC、Flyway 和 Testcontainers；不启动 Spring context。类级静态 `MySQLContainer` 必须由 `DockerImageName.parse("mysql:8.4.6")` 创建，database/user/password 只服务本测试，并以容器参数固定 `utf8mb4` 和 `utf8mb4_0900_as_cs`。测试不得在 Docker 不可用时静默 skip。

`@BeforeAll` 完成以下共享准备：

1. 使用 `PathMatchingResourcePatternResolver` 和公开 loader 从 `classpath*:datasets/tushare_pro/*.yaml` 加载定义，按 API 排序，断言恰有 49 个互不重复定义、851 个业务列、47 个 COMPOSITE 和 2 个 FINGERPRINT；
2. 以容器 JDBC URL 配置 `Flyway.configure().locations("classpath:db/migration")`，对空 `tensor` schema 首次 migrate；在任何表级断言前先断言 `migrationsExecuted() == 6`，使缺 V6 阶段只以“五项而非六项”形成可归因 RED；
3. 执行 `validateWithResult()` 并断言成功，再执行第二次 migrate 并断言 `migrationsExecuted() == 0`；
4. 查询当前 schema 的 `information_schema.tables`、`columns` 和 `statistics`，形成只读快照供测试使用。

永久测试固定由以下 52 次调用组成：

- 一个 `@TestFactory` 返回 49 个 `DynamicTest`，每个 YAML 定义对应一张生产表；动态测试显示名包含 API；
- `migratesAndValidatesRepeatablyOnMySql846`：断言数据库 `VERSION()` 以 `8.4.6` 开头、首次六项/validate/二次零项结果，以及排除 `flyway_schema_history` 后 50 表、1007 列、50 个 PRIMARY、40 个二级索引；
- `fixtureSchemaMatchesContract`：逐列、逐键验证批准的 7 列 fixture 表、InnoDB、排序规则和无二级索引；
- `keepsV6InTestOutputOnly`：以测试类 code source 定位 `target/test-classes`，断言其中 migration 目录只含 V6；其同级 `target/classes` migration 目录只含 V1～V5 且不含 V6。生产 JAR 排除另由外部门禁再次验证。

任何容器启动、镜像、Flyway、loader、资源、SQL 或 `information_schema` 读取失败都必须使测试非零退出，不使用假定结果或降级数据库。

### 49 张生产表的期望构造

每个动态测试以当前 `DatasetDefinition` 为元数据来源，但不读取 SQL 文本生成期望。表名必须精确等于 `definition.tableName().value()`，且全部生产表集合必须与 49 个定义一一对应。

期望列严格按以下顺序构造：

1. `definition.columns()` 按现有顺序逐项映射，且每项 `displayOrder` 必须等于列表下标；
2. 仅当 `businessKey.mode() == FINGERPRINT` 时追加 `business_key CHAR(64) NOT NULL`；因此只有 `tushare_pro__stk_managers` 和 `tushare_pro__pledge_detail` 含该列；
3. 最后固定追加三个来源字段：`source_plugin VARCHAR(64) NOT NULL`、`source_api VARCHAR(64) NOT NULL`、`ingested_at DATETIME(3) NOT NULL`。

业务逻辑类型只按下表机械映射；测试同时比较 `information_schema.columns` 的名称、`ORDINAL_POSITION`、`DATA_TYPE`、长度/精度/小数位或 datetime precision、`IS_NULLABLE`，并把实际 `DATA_TYPE` 归一到对应 `java.sql.Types` 后比较 JDBC 类型。当前 49 份定义若出现未列出的 `ENUM` 或新类型，测试必须明确失败为 unsupported logical type，不能猜测映射。

| logicalType | MySQL 物理类型 | JDBC 类型 |
|---|---|---|
| `STRING` + `length: n` | `VARCHAR(n)` | `Types.VARCHAR` |
| `TEXT` | `TEXT` | `Types.LONGVARCHAR` |
| `DATE` | `DATE` | `Types.DATE` |
| `MONTH` | `CHAR(6)` | `Types.CHAR` |
| `LONG` | `BIGINT` | `Types.BIGINT` |
| `DECIMAL` + `precision: p, scale: s` | `DECIMAL(p,s)` | `Types.DECIMAL` |

业务列的 `nullable: false` 必须映射 `NOT NULL`，否则必须为 nullable；测试不按字段名放宽或收紧。技术列另以字面期望比较：`business_key` 为 `CHAR(64)`，两个 source 字符列为 `VARCHAR(64)`，`ingested_at` 为 `DATETIME(3)`，全部 `NOT NULL`。

### 主键、查询索引和表属性

每张生产表的主键按业务键模式构造：

- COMPOSITE：主键列名和顺序精确等于 `businessKey.fields()`；
- FINGERPRINT：主键精确为单列 `business_key`，原始指纹输入列保持 YAML 顺序和可空性，不进入数据库主键。

二级索引按既定最小规则构造：对 `definition.filters()` 的每个字段建立单列 `idx_<api_name>_<field>`，但若该字段已经等于主键第一列则省略。`information_schema.statistics` 必须按 `INDEX_NAME` 和 `SEQ_IN_INDEX` 完整比较索引集合、列顺序与唯一性；只有 `PRIMARY` 的 `NON_UNIQUE=0`，全部二级索引的 `NON_UNIQUE=1`，不得存在额外 UNIQUE、组合或重复主键前缀索引。

该算法必须得到生产 49 个 PRIMARY 和 40 个二级索引；加 fixture 后为 50 个 PRIMARY，二级索引仍为 40。每张生产表和 fixture 表还必须逐项断言 `ENGINE=InnoDB`、`TABLE_COLLATION=utf8mb4_0900_as_cs`。全局生产计数必须保持 49 表、851 个业务列、1000 个总列；加 fixture 后为 50 表、1007 列。所有总量均排除 `flyway_schema_history`。

### 失败边界

- YAML 不是 49 份、API/表名重复、业务列不是 851 个、FINGERPRINT 不是已冻结的两项时，在访问 schema 前明确失败；
- 首次迁移不是 V1～V6 恰六项、validate 失败或第二次 migrate 非零时明确失败；
- 任一生产表缺失/多余，任一列名称、顺序、物理/JDBC 类型、参数或可空性漂移，任一键/索引名称、顺序、唯一性漂移，或引擎/排序规则漂移时，由对应 API 动态测试明确失败；
- fixture 任一字段、来源列、主键、索引或表属性漂移时由独立 fixture 测试失败；
- V6 出现在 main output/JAR、V1～V5 缺少或测试 output 混入其他迁移时资源隔离门禁失败；
- Docker daemon 或固定镜像不可用是环境阻塞，不授权 skip、改标签、改数据库或删除实际 schema 断言；
- 已发布 V1～V5 若与 YAML 契约发生漂移，修复必须通过单独设计的前向生产迁移；M04-T06 不修改历史 SQL。

## Files

修改：

- `data-plane/tensor-app/pom.xml`：只增加 `org.testcontainers:junit-jupiter`、`org.testcontainers:mysql` 两项无显式版本的测试依赖。

创建：

- `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`：只在测试 classpath 创建 `fixture__fixture_daily`；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`：永久 49 表、fixture、迁移重复性和资源隔离契约。

不删除文件。实现提交消息固定为 `test(db): verify Flyway schema contracts`，提交必须精确包含上述三个文件；设计、交接、看板、`target/`、容器数据或其他文件不得混入实现提交。

## Tests

### 基线和缺测试类 RED

修改前先运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

预期现有 reactor 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过。

只增加两项批准的 POM 依赖后，先安装 reactor 依赖但不运行测试，再从 app 模块直接指定尚不存在的测试类：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -DskipTests install
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-app/pom.xml \
  -Dtest=FlywaySchemaContractIT test
```

第二条必须只因 `No tests matching pattern "FlywaySchemaContractIT"` 非零退出。此处不能加 `surefire.failIfNoSpecifiedTests=false`，否则会吞掉所需 RED；也不得因依赖解析、编译或既有测试失败形成伪 RED。

### 缺 V6 RED

创建完整 `FlywaySchemaContractIT.java`，但仍不创建 V6，然后运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am \
  -Dtest=FlywaySchemaContractIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`-am` 引入的上游模块没有该测试类，因此此处必须显式关闭上游“未匹配指定测试”失败。预期测试编译、loader 和固定 `mysql:8.4.6` 容器均正常，Flyway 只发现并执行生产 V1～V5，随后在共享准备中以“期望六项、实际五项”的明确断言非零退出。不得以缺表后续异常替代该首个可归因失败。

### GREEN、重复性与 schema 契约

添加精确 V6 后重跑同一 reactor 定向命令。预期退出 0，并报告 49 个动态生产表测试加 3 个固定测试，共 52 次调用，0 failure、0 error、0 skipped；实际结果必须同时证明：

- 容器数据库为 MySQL 8.4.6；
- Flyway 首次执行 V1～V6 六项，validate 成功，二次 migrate 为零项；
- 49 YAML、851 业务列、49 张生产表、1000 个生产列、49 个生产 PRIMARY、40 个生产二级索引全部匹配；
- fixture 为第 50 张表，恰有 7 列、1 个 PRIMARY、0 个二级索引；全部非 history 表总计 1007 列、50 个 PRIMARY、40 个二级索引；
- 每表列/键/索引/唯一性/来源字段/InnoDB/排序规则以及 main/test migration 输出隔离全部匹配。

### Reactor、生产 JAR、范围与清理

运行非定向回归与打包：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

由于本任务按任务卡保留 `*IT` 命名且不修改构建生命周期，两条非定向命令继续预期既有 150/150、0 failure、0 error、0 skipped；M04 的 52 次 MySQL 契约由上一节显式模块门禁承担。两条命令均须退出 0，六层 Enforcer 通过且无新增警告类别。

验证依赖和生产 JAR：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-app/pom.xml dependency:tree \
  -Dincludes=org.testcontainers
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg '^db/migration/V[0-9]+__.*\.sql$'
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'V6__create_fixture_tables|FlywaySchemaContractIT'
```

依赖树必须显示批准的两个 direct dependency 均为 test scope 且版本由 BOM 解析为 1.21.4；第二条恰输出生产 V1～V5 五行；第三条无输出并以 1 退出，证明测试 SQL 和测试类未进入生产 JAR。

最后运行 reactor `clean`，确认容器已由 Testcontainers 停止，并执行：

```bash
git status --short --untracked-files=all -- \
  data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/resources/db/migration \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/db
git diff --check
```

提交前 status 必须精确列出 Files 节一个修改和两个新文件，格式检查退出 0；按仓库规则将两个新文件加入 Git。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确三文件范围，工作树干净。

## Acceptance

- `tensor-app` 仅新增两项 BOM 管理的 Testcontainers 测试依赖，没有显式版本、compile/runtime scope 或其他 POM 改动；
- 测试专用 V6 精确创建 `fixture__fixture_daily` 的 4 个批准业务列、3 个来源列、复合主键、InnoDB 和 `utf8mb4_0900_as_cs`，不含二级/唯一索引或其他表；
- 永久 `FlywaySchemaContractIT` 固定官方 `mysql:8.4.6`，通过公开 loader 加载 49/49 YAML，并以 49 个动态测试逐表验证列名、顺序、物理/JDBC 类型、参数、可空性、FINGERPRINT 技术列、主键、精确最小查询索引、唯一性、来源字段、引擎和排序规则；
- Flyway 首次 migrate 恰执行 V1～V6 六项，validate 成功，二次 migrate 为零项；生产 totals 保持 49 表/851 业务列/1000 总列/49 PRIMARY/40 二级索引，加 fixture 后为 50 表/1007 列/50 PRIMARY/40 二级索引；
- TDD 依次得到缺测试类的可归因 RED、完整测试缺 V6 时五项对六项的可归因 RED，以及固定 MySQL 8.4.6 上 52 次调用全部 GREEN；
- main/test 输出隔离和生产 JAR 门禁证明 V6 与测试类只在测试输出，生产 JAR 恰含 V1～V5；
- 非定向 reactor `test`/`verify` 150/150、Enforcer、依赖范围、Git 范围、格式、清理和干净工作树门禁全部得到预期结果；
- 实现提交精确包含 POM 修改、测试 V6 和 `FlywaySchemaContractIT` 三个文件，未修改生产迁移、生产 Java、YAML、模板、父 POM 或其他模块。

## Risks

- Testcontainers 依赖可用的 Docker daemon 和固定官方 `mysql:8.4.6` 镜像；环境不可用时必须报告阻塞，不能 skip 或替换数据库。
- 当前 Flyway 可能提示其最高已测试 MySQL 低于 8.4，但 V1～V5 已在同一 8.4.6 镜像实际通过；验收仍以 migrate、validate、零项二次 migrate 和完整结果级 schema 断言为准。
- `FlywaySchemaContractIT` 的 `*IT` 名称不会被现有 Surefire 默认发现；本任务有意遵守任务卡并用显式 `-Dtest` 模块门禁运行，不在获批范围外改动构建生命周期。后续若要纳入默认 `verify`，必须另行设计 Failsafe/Surefire 配置。
- 期望值主要从公开 YAML 契约构造，因此可发现 SQL 与元数据漂移；全局字面 totals、两项 FINGERPRINT、fixture 字面 schema、资源隔离和精确最小索引规则提供独立约束，测试不得改成从实际数据库或 SQL 自举期望。
- V1～V5 已发布，任何后续合法 schema 变化都必须使用新的前向生产迁移并同步更新 YAML/契约；不得原地改历史迁移来让本测试通过。
