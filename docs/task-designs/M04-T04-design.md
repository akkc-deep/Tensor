# M04-T04 V4 财务与披露宽表——任务设计

任务编号：`M04-T04`
对应任务：[M04-T04](../superpowers/plans/tensor-modules/M04-flyway-schema.md#task-m04-t04-v4-财务与披露宽表35hsql)
实施产物：`V4__create_financial_tables.sql`

## Goal

创建第四份生产 Flyway 迁移，以固定、可审阅的 MySQL 8.4.6 SQL 建立 M03-T06 已冻结的 9 张财务与披露来源表。每张表必须逐列保持 YAML 的名称、顺序、类型和可空性，使用批准的复合主键、最小查询索引及统一来源字段，并与已发布 V1～V3 组成可重复迁移和校验的 39 表 schema。

## Scope

包含：

- 只创建任务卡指定的 V4 迁移文件；
- 创建 `income`、`balancesheet`、`cashflow`、`fina_indicator`、`fina_audit`、`fina_mainbz`、`express`、`forecast` 和 `disclosure_date` 对应的 9 张 `tushare_pro__*` 表；
- 按 M03-T06 的 490 个业务列原序机械转换 MySQL 类型和可空性，五个空样例数据集仍完整建表；
- 每表追加三个来源字段，不追加 `business_key` 或其他技术列；
- 声明 9 个 COMPOSITE 主键和本设计冻结的 8 个最小二级索引；
- 使用 `/private/tmp/M04T04SchemaCheck.java`、现有 Flyway/MySQL 依赖和本机 Colima 中固定的官方 `mysql:8.4.6` 执行可归因 RED、V1～V4 Flyway GREEN、`information_schema` 校验、reactor 回归、JAR 内容和范围门禁。

排除：

- 不修改 POM、Java/YAML/schema/template、V1～V3、M03 产物、应用配置或其他模块；
- 不创建永久 Java 测试、测试资源、生成器或第五个 SQL 文件；M04-T06 负责永久 49 表总契约；
- 不创建 M04-T05～T06 的表、fixture 表、Flyway history 之外的辅助表、视图、触发器、外键或存储过程；
- 不因 `income`、`balancesheet`、`cashflow`、`fina_indicator` 和 `fina_audit` 的模板样例为空而删列、改型或放宽业务键；
- 不为 `fina_mainbz` 发明模板/YAML 中不存在的 `ann_date` 列；
- 不在 SQL 中使用 `IF NOT EXISTS`、运行时推导 DDL、默认时间值、自动更新时间或静默兼容已有错误 schema；
- 不新增与 metadata filters 无关的索引，不实现 Upsert、查询、下载、REST、前端或启动校验服务。

## Approach

### 迁移结构与统一规则

迁移文件按以下固定顺序组成：

1. `SET time_zone = '+00:00';`，固定 Flyway 当前迁移会话为 UTC；
2. 按 M03-T06 设计顺序写 9 个无 `IF NOT EXISTS` 的 `CREATE TABLE`；
3. 每张表先按 YAML `displayOrder` 写全部业务列；
4. 按序追加 `source_plugin VARCHAR(64) NOT NULL`、`source_api VARCHAR(64) NOT NULL`、`ingested_at DATETIME(3) NOT NULL`；
5. 在列定义之后声明主键与二级索引；
6. 每张表以 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs` 结束。

9 表顺序固定为：`income`、`balancesheet`、`cashflow`、`fina_indicator`、`fina_audit`、`fina_mainbz`、`express`、`forecast`、`disclosure_date`。

所有表名、列名和索引名统一使用反引号包围；字面标识符只来自本设计和 M03-T06 设计，禁止插入变量或用户输入。全部业务列名称、顺序、`logicalType`、长度、精度、`longText` 和 `nullable` 以 `docs/task-designs/M03-T06-design.md` 的机械规则及其 9 份运行时 YAML 为同一冻结基线；设计与 YAML 不一致时停止，不由实施者选择一方覆盖另一方。

逻辑类型只按下表机械转换，不做字段名特例：

| M03 logicalType | MySQL DDL | 可空性 |
|---|---|---|
| `STRING` + `length: n` | `VARCHAR(n)` | `nullable: false` → `NOT NULL`，否则显式 `NULL` |
| `DATE` | `DATE` | 同上 |
| `DECIMAL` + `precision: p, scale: s` | `DECIMAL(p,s)` | 同上 |
| `TEXT` + `longText: true` | `TEXT` | 同上 |

V4 精确类型分布为 DATE 23、STRING 30、TEXT 3、DECIMAL 434；不得出现 `BIGINT`、浮点类型或字段名推断。不得为 nullable 列写默认值。所有 9 表均为 COMPOSITE 键表，不得增加 `business_key`。

### 表、主键和索引总表

| API | 业务列数 | 总列数 | 主键（精确顺序） | 二级索引（精确名称与顺序） |
|---|---:|---:|---|---|
| `income` | 85 | 88 | `(ts_code, end_date, report_type, ann_date)` | `idx_income_ann_date (ann_date)` |
| `balancesheet` | 152 | 155 | `(ts_code, end_date, report_type, ann_date)` | `idx_balancesheet_ann_date (ann_date)` |
| `cashflow` | 97 | 100 | `(ts_code, end_date, report_type, ann_date)` | `idx_cashflow_ann_date (ann_date)` |
| `fina_indicator` | 108 | 111 | `(ts_code, end_date, ann_date)` | `idx_fina_indicator_ann_date (ann_date)` |
| `fina_audit` | 7 | 10 | `(ts_code, end_date, ann_date)` | `idx_fina_audit_ann_date (ann_date)` |
| `fina_mainbz` | 8 | 11 | `(ts_code, end_date, bz_item, curr_type)` | None；PK 已覆盖唯一 filter `ts_code` |
| `express` | 15 | 18 | `(ts_code, end_date, ann_date)` | `idx_express_ann_date (ann_date)` |
| `forecast` | 13 | 16 | `(ts_code, end_date, ann_date, type)` | `idx_forecast_ann_date (ann_date)` |
| `disclosure_date` | 5 | 8 | `(ts_code, end_date)` | `idx_disclosure_date_ann_date (ann_date)` |

主键列名和顺序逐字来自 M03-T06 与 TRD 9.4，全部保持不可空。`disclosure_date.ann_date` 是可空筛选列而不是业务键；`fina_mainbz` 必须保持 8 个业务列且不创建 `ann_date` 列或二级索引。`express.perf_summary`、`forecast.summary` 和 `forecast.change_reason` 必须保持可空 `TEXT`。

二级索引沿用已发布迁移的最小确定性规则：对 metadata `filters` 中的每个字段建立单列索引，除非相同字段已经是主键最左前缀。所有 9 个主键均以 `ts_code` 开头，因此不重复创建 `ts_code` 索引；除 `fina_mainbz` 外的 8 表均另建上表精确 `ann_date` 索引。V4 最终必须恰有 8 个非 PRIMARY 索引；与 V1～V3 合计为 30 个非 PRIMARY 索引。

### 临时 Flyway/schema harness

`/private/tmp/M04T04SchemaCheck.java` 只用于本任务验证，不进入 Git。它必须：

- 先从当前目录向上定位同时包含 `data-plane/pom.xml` 和 M03 YAML 目录的仓库根；
- 在任何数据库连接前按序断言已发布 V1、V2、V3 文件存在，再断言 V4 文件存在；V4 缺失时只以 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V4__create_financial_tables.sql` 失败，形成可归因 RED；
- 通过公开 `DatasetDefinitionLoader` 加载 `classpath*:datasets/tushare_pro/*.yaml`，筛选并断言本设计的 9 API 恰有 490 个业务列，类型分布恰为 DATE 23、STRING 30、TEXT 3、DECIMAL 434；
- 使用 `Flyway.configure().dataSource(...).locations("filesystem:<migration-directory>")` 对空 `tensor` schema 执行迁移，断言首次恰执行 V1、V2、V3、V4 四项，再执行 `validate()` 并确认第二次 `migrate()` 执行 0 项；
- 查询 `information_schema.tables`、`columns` 和 `statistics`，逐表比较 V4 的业务/来源列名称、序号、MySQL 类型参数、可空性、主键顺序和本设计二级索引；
- 断言迁移后恰有 39 张 `tushare_pro__*` 表和 878 个总列；其中 V4 恰有 9 表、490 个业务列、517 个总列、9 个 PRIMARY 和 8 个二级索引；全体 39 表合计 39 个 PRIMARY 和 30 个二级索引；
- 逐个 V4 表断言 `ENGINE=InnoDB`、`TABLE_COLLATION=utf8mb4_0900_as_cs`，并断言三个来源字段位置和类型完全一致；
- 单独断言 `tushare_pro__balancesheet` 恰有 155 列，其中 152 个业务列与 YAML 原序一致；
- 单独断言 `tushare_pro__fina_mainbz` 恰有 11 列、不含 `ann_date`、主键为 `(ts_code, end_date, bz_item, curr_type)` 且没有二级索引；
- 单独断言三个长文本列恰为可空 `TEXT`，五个空模板对应的运行时定义仍合计建立 449 个业务列；
- 成功时只输出 `M04-T04_OK:39:9:490:517:8`。

临时数据库固定使用本机 Colima 中已有的官方 `mysql:8.4.6`，随机映射本机端口，schema 为 `tensor`；容器启动参数固定服务端字符集和排序规则。容器名、密码和本地变量只用于本任务，完成或失败都必须停止容器，不能复用已有开发 schema，不得创建或依赖浮动 `mysql:8.4` 标签。readiness 必须轮询容器内 `mysqladmin ping -h127.0.0.1`，以真实 TCP MySQL 协议就绪为准。

### 失败边界

- V1～V4 文件缺失、Flyway 执行/校验失败、首次执行项不是四项、第二次迁移仍有 pending、表/列/键/索引/引擎/排序规则任一漂移都使 harness 非零退出；
- V1～V4 必须在全新 schema 一次成功；已有部分表不能被 `IF NOT EXISTS` 掩盖；
- 本机 Colima/Docker daemon、官方 `mysql:8.4.6` 镜像或临时端口不可用是环境阻塞，不授权改用 H2、SQLite、MariaDB、其他 MySQL 版本或跳过实际 schema 校验；
- Maven 依赖下载失败必须重试既定命令或报告环境阻塞，不能提交依赖 JAR、wrapper 或本地仓库内容；
- M03-T06 设计与运行时 YAML 若发生真实冲突，停止实施并刷新设计，不在 SQL 中猜测；
- 宽表行大小、`fina_mainbz` 复合主键长度、MySQL 8.4.6 保留字或其他 DDL 错误属于实现 RED，只能修正 V4 的可实现错误；不得缩窄已冻结业务类型、把 `TEXT` 改为短字符串或改变业务键来绕过错误。

## Files

只创建：

- `data-plane/tensor-app/src/main/resources/db/migration/V4__create_financial_tables.sql`

临时且必须清理：

- `/private/tmp/M04T04SchemaCheck.java`
- `/private/tmp/M04-T04-classpath.txt`
- Docker 容器 `tensor-m04-t04-mysql`
- Maven 生成的各模块 `target/`

实现提交消息固定为 `feat(db): create financial disclosure tables`。提交只暂存唯一 V4 SQL；任务设计、交接、看板、临时 harness、classpath 文件、生成物和其他源码不得混入实现提交。

## Tests

### 基线、空模板与可归因 RED

先运行现有 reactor 到 `tensor-app`：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

预期退出 0，现有测试分布为 plugin-api 79、core 0、tushare 58、fixture 0、app 13，合计 150 项，0 failure、0 error、0 skipped；六层 Enforcer 均通过且没有新增警告类别。

确认五个空模板字段完整但没有样例数据：

```bash
jq -e '.data == [] and (.fields | length) == 85' docs/data-template/income.json
jq -e '.data == [] and (.fields | length) == 152' docs/data-template/balancesheet.json
jq -e '.data == [] and (.fields | length) == 97' docs/data-template/cashflow.json
jq -e '.data == [] and (.fields | length) == 108' docs/data-template/fina_indicator.json
jq -e '.data == [] and (.fields | length) == 7' docs/data-template/fina_audit.json
```

五条命令均预期输出 `true` 并退出 0。随后创建完整临时 harness，并准备现有依赖 classpath：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -DskipTests install
mvn -q -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-app/pom.xml -DincludeScope=test \
  dependency:build-classpath \
  -Dmdep.outputFile=/private/tmp/M04-T04-classpath.txt

m04_t04_cp=$(tr -d '\n' < /private/tmp/M04-T04-classpath.txt)
java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t04_cp" \
  /private/tmp/M04T04SchemaCheck.java \
  'jdbc:mysql://127.0.0.1:1/tensor' root unavailable
```

在 V4 文件不存在时，最后一条必须在尝试连接前只因 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V4__create_financial_tables.sql` 非零退出；不得因 harness 编译、classpath、loader、V1～V3、schema 或依赖错误失败。

### Flyway GREEN 与实际 schema

使用 `/opt/homebrew/opt/docker/bin/docker` 先 `image inspect mysql:8.4.6`，结果必须显示 linux/arm64、`MYSQL_MAJOR=8.4` 与 `MYSQL_VERSION=8.4.6-1.el9`。随后以 `--detach --rm --name tensor-m04-t04-mysql`、随机 `127.0.0.1::3306`、`MYSQL_ROOT_PASSWORD=tensor-m04-t04`、`MYSQL_DATABASE=tensor`、`--character-set-server=utf8mb4` 和 `--collation-server=utf8mb4_0900_as_cs` 启动隔离容器；以 trap 保证退出时停止容器，并最多轮询 60 次容器内 `mysqladmin ping --silent -h127.0.0.1`。

取得随机宿主端口后运行：

```bash
java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t04_cp" \
  /private/tmp/M04T04SchemaCheck.java \
  "jdbc:mysql://127.0.0.1:${m04_t04_port}/tensor?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" \
  root tensor-m04-t04
```

Flyway 必须首次执行 V1～V4 四项，随后 validate 和零项二次 migrate 通过；harness 只输出 `M04-T04_OK:39:9:490:517:8` 并退出 0。任何 SQL 语法、行大小、保留字、索引长度、重复索引或 `information_schema` 对照失败都属于实现 RED，必须只修正唯一 V4 SQL 后重跑同一 harness。

### Reactor、打包、范围与清理

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

两条命令均预期 150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过且没有新增警告类别。验证生产 JAR：

```bash
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg '^db/migration/V(1__create_basic_and_organization_tables|2__create_market_and_trading_tables|3__create_connect_and_slb_tables|4__create_financial_tables)\.sql$'
```

预期恰输出 V1～V4 各一行。最后停止临时容器，运行 reactor `clean`，删除 `/private/tmp/M04T04SchemaCheck.java` 与 `/private/tmp/M04-T04-classpath.txt`，并运行迁移目录 `git status --short --untracked-files=all` 与 `git diff --check`。提交前 status 必须精确列出 Files 节一个新 V4 SQL；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确单文件范围，工作树干净。

## Acceptance

- V4 生产迁移精确创建 M03-T06 的 9 张表，恰含 490 个原序业务列和 27 个来源列，总计 517 列；V1～V4 合计恰有 39 张来源表和 878 列；
- 所有业务列的 MySQL 类型参数与可空性机械匹配 YAML；V4 类型分布为 DATE 23、STRING 30、TEXT 3、DECIMAL 434，没有默认值、浮点类型或静默缩窄；
- 9 个 COMPOSITE 主键的名称/顺序正确，最终恰有本设计 8 个二级索引且没有主键前缀重复索引；V1～V4 合计 39 个 PRIMARY 和 30 个二级索引；
- 9 表均为 InnoDB、`utf8mb4_0900_as_cs`，每表来源字段按固定顺序和类型追加，迁移会话固定 UTC；`balancesheet` 恰有 155 列，`fina_mainbz` 不含 `ann_date`，三个长文本字段保持可空 `TEXT`，五个空样例数据集仍完整建立 449 个业务列；
- 完整临时 harness 经历缺 V4 文件的可归因 RED 后，在本机官方 `mysql:8.4.6` 的全新 schema 中只输出 `M04-T04_OK:39:9:490:517:8`；Flyway 首次 migrate、validate 和零项二次 migrate 全部通过；
- reactor `test`/`verify` 150/150、六层 Enforcer、JAR 四份迁移资源、范围、格式、清理和干净工作树门禁得到预期结果；
- 实现提交精确包含一个 V4 SQL，未修改 POM、Java、YAML、schema、template、V1～V3、其他迁移或模块，未提交临时 harness、classpath、容器数据或生成物。

## Risks

- 五个模板共 449 列当前没有样例行；项目所有者已批准 M03-T06 的精确类型/可空性规则。未来真实上游值若不符合该映射，必须通过新的设计裁决和前向迁移处理，不得在 V4 或适配阶段静默改型；
- `balancesheet`、`cashflow` 和 `fina_indicator` 是宽表，`fina_mainbz` 复合主键含 `VARCHAR(255)`；实际 MySQL 8.4.6 的行大小和索引长度只能由完整 Flyway/schema 门禁确认，失败时不得缩窄业务契约绕过；
- `fina_mainbz` 接受 `ann_date` 查询参数但没有同名业务列；建表与后续查询必须保留该差异，不得发明列；
- `perf_summary`、`summary` 和 `change_reason` 是可空长文本，必须映射为 `TEXT` 而不是 `VARCHAR`；
- 现有 Flyway 版本会提示其最高已测试 MySQL 为 8.1，但 M04-T01～T03 已在同一 MySQL 8.4.6 镜像实际通过；本任务仍以实际 8.4.6 结果级门禁为准；
- 已发布 V1～V4 后不得原地修改历史迁移；任何后续 schema 调整都必须通过新的前向 Flyway 迁移发布。
