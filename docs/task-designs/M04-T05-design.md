# M04-T05 V5 公司行动、股东与治理表——任务设计

任务编号：`M04-T05`
对应任务：[M04-T05](../superpowers/plans/tensor-modules/M04-flyway-schema.md#task-m04-t05-v5-公司行动股东与治理表35hsql)
实施产物：`V5__create_corporate_and_governance_tables.sql`

## Goal

创建第五份生产 Flyway 迁移，以固定、可审阅的 MySQL 8.4.6 SQL 建立 M03-T07 与 M03-T08 已冻结的 10 张公司行动、股东与治理来源表。迁移必须逐列保持 10 份 YAML 的名称、顺序、类型和可空性，为 9 张 COMPOSITE 表使用批准的复合主键，为 `pledge_detail` 增加唯一内部指纹键，并与 V1～V4 组成可重复迁移和校验的完整 49 表生产 schema。

## Scope

包含：

- 只创建任务卡指定的 V5 迁移文件；
- 按 M03-T07 顺序创建 `dividend`、`repurchase`、`share_float`，再按 M03-T08 顺序创建 `stk_rewards`、`stk_holdernumber`、`stk_holdertrade`、`top10_holders`、`top10_floatholders`、`pledge_stat`、`pledge_detail`；
- 按 10 份冻结 YAML 原序机械转换 91 个业务列的 MySQL 类型和可空性，三个空样例数据集仍完整建表；
- 每表追加三个来源字段；仅 FINGERPRINT 表 `pledge_detail` 在业务列之后、来源字段之前增加 `business_key CHAR(64) NOT NULL`；
- 声明 10 个主键和本设计冻结的 10 个最小二级索引；
- 使用 `/private/tmp/M04T05SchemaCheck.java`、现有 Flyway/MySQL 依赖和本机 Colima 中固定的官方 `mysql:8.4.6` 执行可归因 RED、V1～V5 Flyway GREEN、`information_schema` 校验、reactor 回归、JAR 内容和范围门禁。

排除：

- 不修改 POM、Java/YAML/schema/template、V1～V4、M03 产物、应用配置或其他模块；
- 不创建永久 Java 测试、测试资源、生成器或第六个 SQL 文件；M04-T06 负责永久 49 表总契约和 fixture 迁移；
- 不创建 fixture 表、辅助表、视图、触发器、外键或存储过程，不实现指纹编码、Upsert、查询、下载、REST、前端或启动校验服务；
- 不因 `dividend`、`top10_holders` 和 `top10_floatholders` 的模板样例为空而删列、改型或放宽业务键；
- 不为 9 张 COMPOSITE 表增加 `business_key`，也不把 `pledge_detail` 的 nullable 业务列改为不可空；
- 不在 SQL 中使用 `IF NOT EXISTS`、运行时推导 DDL、默认时间值、自动更新时间或静默兼容已有错误 schema；
- 不把批准的字符串改为 `ENUM`，不把 `LONG`/`DECIMAL` 改为浮点类型或根据样例值缩窄。

## Approach

### 迁移结构与统一规则

迁移文件固定：

1. 以 `SET time_zone = '+00:00';` 固定 Flyway 当前迁移会话为 UTC；
2. 按 Scope 节顺序写 10 个无 `IF NOT EXISTS` 的 `CREATE TABLE`；
3. 每表先按 YAML `displayOrder` 写全部业务列；
4. `pledge_detail` 紧接业务列追加 `business_key CHAR(64) NOT NULL`，其他表不写该列；
5. 每表随后按序追加 `source_plugin VARCHAR(64) NOT NULL`、`source_api VARCHAR(64) NOT NULL`、`ingested_at DATETIME(3) NOT NULL`；
6. 在列定义之后声明主键与二级索引；
7. 每表以 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs` 结束。

所有表名、列名和索引名使用反引号；字面标识符只来自本设计和两项依赖的运行时 YAML。业务列名称、顺序、`logicalType`、长度、精度和 `nullable` 以 `docs/task-designs/M03-T07-design.md`、`docs/task-designs/M03-T08-design.md` 及其 10 份当前 YAML 为同一冻结基线；设计与 YAML 不一致时停止，不由实施者选择一方覆盖另一方。

逻辑类型只按下表转换，不做字段名特例：

| M03 logicalType | MySQL DDL | 可空性 |
|---|---|---|
| `STRING` + `length: n` | `VARCHAR(n)` | `nullable: false` → `NOT NULL`，否则显式 `NULL` |
| `DATE` | `DATE` | 同上 |
| `LONG` | `BIGINT` | 同上 |
| `DECIMAL` + `precision: p, scale: s` | `DECIMAL(p,s)` | 同上 |

V5 的 91 个业务列精确分布为 DATE 26、STRING 27、LONG 2、DECIMAL 36；不得出现 `TEXT`、浮点类型、默认值或字段名推断。9 张 COMPOSITE 表不增加技术列；`pledge_detail` 的 14 个业务列全部保持 nullable，内部 `business_key` 是其唯一不可空主键列。

### 表、主键和索引总表

| API | 业务列 | 总列 | 主键（精确顺序） | 二级索引（精确名称与顺序） |
|---|---:|---:|---|---|
| `dividend` | 14 | 17 | `(ts_code, end_date, ann_date)` | `idx_dividend_ann_date (ann_date)` |
| `repurchase` | 9 | 12 | `(ts_code, ann_date, proc)` | `idx_repurchase_ann_date (ann_date)` |
| `share_float` | 7 | 10 | `(ts_code, float_date, holder_name, share_type)` | `idx_share_float_ann_date (ann_date)` |
| `stk_rewards` | 7 | 10 | `(ts_code, ann_date, end_date, name)` | `idx_stk_rewards_ann_date (ann_date)` |
| `stk_holdernumber` | 4 | 7 | `(ts_code, end_date, ann_date)` | `idx_stk_holdernumber_ann_date (ann_date)` |
| `stk_holdertrade` | 11 | 14 | `(ts_code, ann_date, holder_name, in_de, change_vol)` | `idx_stk_holdertrade_ann_date (ann_date)` |
| `top10_holders` | 9 | 12 | `(ts_code, end_date, holder_name, ann_date)` | `idx_top10_holders_ann_date (ann_date)` |
| `top10_floatholders` | 9 | 12 | `(ts_code, end_date, holder_name, ann_date)` | `idx_top10_floatholders_ann_date (ann_date)` |
| `pledge_stat` | 7 | 10 | `(ts_code, end_date)` | None；PK 已覆盖唯一 filter `ts_code` |
| `pledge_detail` | 14 | 18 | `(business_key)` | `idx_pledge_detail_ts_code (ts_code)`；`idx_pledge_detail_ann_date (ann_date)` |

最小索引规则与已发布迁移一致：对 metadata `filters` 的每个字段建立单列索引，除非该字段已是主键最左前缀。9 个 COMPOSITE 主键均以 `ts_code` 开头，因此不重复创建 `ts_code` 索引；其中 8 表另建 `ann_date` 索引，`pledge_stat` 无 `ann_date` filter。`pledge_detail` 的主键是内部指纹，不覆盖任一 filter，因此创建两个单列索引。V5 最终恰有 10 个非 PRIMARY 索引；V1～V5 合计为 40 个。

### 临时 Flyway/schema harness

`/private/tmp/M04T05SchemaCheck.java` 只用于本任务验证，不进入 Git。它必须：

- 从当前目录向上定位同时包含 `data-plane/pom.xml` 和 M03 YAML 目录的仓库根；
- 在任何数据库连接前按序断言已发布 V1～V4 文件存在，再断言 V5 文件存在；V5 缺失时只以 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V5__create_corporate_and_governance_tables.sql` 失败，形成可归因 RED；
- 通过公开 `DatasetDefinitionLoader` 分别加载 10 个精确 YAML 资源，断言恰有 91 个业务列、DATE 26、STRING 27、LONG 2、DECIMAL 36、9 个 COMPOSITE 和 1 个 FINGERPRINT；
- 使用 `Flyway.configure().dataSource(...).locations("filesystem:<migration-directory>")` 对空 `tensor` schema 执行迁移，断言首次恰执行 V1～V5 五项，再执行 `validate()` 并确认第二次 `migrate()` 执行 0 项；
- 查询 `information_schema.tables`、`columns` 和 `statistics`，逐表比较 V5 的业务/技术/来源列名称、序号、MySQL 类型参数、可空性、主键顺序和本设计二级索引；
- 断言迁移后恰有 49 张 `tushare_pro__*` 表和 1000 个总列；其中 V5 恰有 10 表、91 个业务列、122 个总列、10 个 PRIMARY 和 10 个二级索引；全体 49 表合计 49 个 PRIMARY 和 40 个二级索引；
- 逐个 V5 表断言 `ENGINE=InnoDB`、`TABLE_COLLATION=utf8mb4_0900_as_cs`，并断言三个来源字段位置和类型一致；
- 单独断言 `pledge_detail` 恰有 18 列：前 14 个业务列与 YAML 原序一致且全部可空，第 15 列为 `CHAR(64) NOT NULL business_key`，随后才是三个来源字段；其 PRIMARY 仅含 `business_key`，并恰有 `ts_code`/`ann_date` 两个二级索引；
- 断言其他 9 表均无 `business_key`，两个 `LONG` 列恰为 nullable `BIGINT`，三个空模板仍合计建立 32 个业务列；
- 成功时只输出 `M04-T05_OK:49:10:91:122:10`。

临时数据库固定使用本机 Colima 中已有的官方 `mysql:8.4.6`，随机映射本机端口，schema 为 `tensor`；容器名固定 `tensor-m04-t05-mysql`，密码和本地变量只用于本任务，完成或失败都必须停止容器。容器启动参数固定服务端字符集和排序规则；readiness 最多轮询 60 次容器内 `mysqladmin ping --silent -h127.0.0.1`，以真实 TCP MySQL 协议就绪为准。

### 失败边界

- V1～V5 文件缺失、Flyway 执行/校验失败、首次执行项不是五项、第二次迁移仍有 pending、表/列/键/索引/引擎/排序规则任一漂移都使 harness 非零退出；
- V1～V5 必须在全新 schema 一次成功；已有部分表不能被 `IF NOT EXISTS` 掩盖；
- 本机 Colima/Docker daemon、固定镜像或临时端口不可用是环境阻塞，不授权改用 H2、SQLite、MariaDB、其他 MySQL 版本或跳过实际 schema 校验；
- Maven 依赖下载失败必须重试既定命令或报告环境阻塞，不能提交依赖 JAR、wrapper 或本地仓库内容；
- M03-T07/T08 设计与运行时 YAML 若发生真实冲突，停止实施并刷新设计，不在 SQL 中猜测；
- MySQL 保留字、复合主键长度或其他 DDL 错误属于实现 RED，只能修正 V5 的可实现错误；不得缩窄业务类型、改变业务键、收紧 `pledge_detail` nullability 或跳过指纹技术列来绕过错误。

## Files

只创建：

- `data-plane/tensor-app/src/main/resources/db/migration/V5__create_corporate_and_governance_tables.sql`

临时且必须清理：

- `/private/tmp/M04T05SchemaCheck.java`
- `/private/tmp/M04-T05-classpath.txt`
- Docker 容器 `tensor-m04-t05-mysql`
- Maven 生成的各模块 `target/`

实现提交消息固定为 `feat(db): create corporate governance tables`。提交只暂存唯一 V5 SQL；任务设计、交接、看板、临时 harness、classpath、生成物和其他源码不得混入实现提交。

## Tests

### 基线、空模板与可归因 RED

先运行现有 reactor 到 `tensor-app`：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

预期退出 0，现有测试分布为 plugin-api 79、core 0、tushare 58、fixture 0、app 13，合计 150 项，0 failure、0 error、0 skipped；六层 Enforcer 通过且没有新增警告类别。

确认三个空模板字段完整但没有样例数据：

```bash
jq -e '.data == [] and (.fields | length) == 14' docs/data-template/dividend.json
jq -e '.data == [] and (.fields | length) == 9' docs/data-template/top10_holders.json
jq -e '.data == [] and (.fields | length) == 9' docs/data-template/top10_floatholders.json
```

三条命令均预期输出 `true` 并退出 0。随后创建完整临时 harness，并准备现有依赖 classpath：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -DskipTests install
mvn -q -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-app/pom.xml -DincludeScope=test \
  dependency:build-classpath \
  -Dmdep.outputFile=/private/tmp/M04-T05-classpath.txt

m04_t05_cp=$(tr -d '\n' < /private/tmp/M04-T05-classpath.txt)
java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t05_cp" \
  /private/tmp/M04T05SchemaCheck.java \
  'jdbc:mysql://127.0.0.1:1/tensor' root unavailable
```

在 V5 文件不存在时，最后一条必须在尝试连接前只因精确 V5 路径缺失而非 0 退出；不得因 harness 编译、classpath、loader、V1～V4、schema 或依赖错误失败。

### Flyway GREEN 与实际 schema

使用 `/opt/homebrew/opt/docker/bin/docker` 先 `image inspect mysql:8.4.6`，结果必须显示 `linux/arm64`、`MYSQL_MAJOR=8.4` 与 `MYSQL_VERSION=8.4.6-1.el9`。随后以 `--detach --rm --name tensor-m04-t05-mysql`、随机 `127.0.0.1::3306`、`MYSQL_ROOT_PASSWORD=tensor-m04-t05`、`MYSQL_DATABASE=tensor`、`--character-set-server=utf8mb4` 和 `--collation-server=utf8mb4_0900_as_cs` 启动隔离容器；以 trap 保证退出时停止容器，并按 Approach 节执行 TCP readiness。

取得随机宿主端口后运行：

```bash
java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t05_cp" \
  /private/tmp/M04T05SchemaCheck.java \
  "jdbc:mysql://127.0.0.1:${m04_t05_port}/tensor?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" \
  root tensor-m04-t05
```

Flyway 必须首次执行 V1～V5 五项，随后 validate 和零项二次 migrate 通过；harness 只输出 `M04-T05_OK:49:10:91:122:10` 并退出 0。任何 SQL 语法、索引长度、重复索引或 `information_schema` 对照失败都属于实现 RED，必须只修正唯一 V5 SQL 后重跑同一 harness。

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
  | rg '^db/migration/V(1__create_basic_and_organization_tables|2__create_market_and_trading_tables|3__create_connect_and_slb_tables|4__create_financial_tables|5__create_corporate_and_governance_tables)\.sql$'
```

预期恰输出 V1～V5 各一行。最后停止临时容器，运行 reactor `clean`，删除 `/private/tmp/M04T05SchemaCheck.java` 与 `/private/tmp/M04-T05-classpath.txt`，并运行迁移目录 `git status --short --untracked-files=all` 与 `git diff --check`。提交前 status 必须精确列出 Files 节一个新 V5 SQL；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确单文件范围，工作树干净。

## Acceptance

- V5 生产迁移精确创建 M03-T07/T08 的 10 张表，恰含 91 个原序业务列、1 个内部指纹键和 30 个来源列，总计 122 列；V1～V5 合计恰有 49 张来源表和 1000 列；
- 所有业务列的 MySQL 类型参数与可空性机械匹配 YAML；V5 类型分布为 DATE 26、STRING 27、LONG 2、DECIMAL 36，没有默认值、浮点类型或静默缩窄；
- 9 个 COMPOSITE 主键与 1 个 FINGERPRINT 主键正确，最终恰有本设计 10 个二级索引且没有主键前缀重复索引；V1～V5 合计 49 个 PRIMARY 和 40 个二级索引；
- 10 表均为 InnoDB、`utf8mb4_0900_as_cs`，来源字段顺序和类型一致；`pledge_detail` 保留全部 14 个 nullable 业务列、内部 `CHAR(64) NOT NULL business_key` 和两个查询索引，其他 9 表不含该技术列；两个 `LONG` 保持 nullable `BIGINT`，三个空样例数据集仍完整建立 32 个业务列；
- 完整临时 harness 经历缺 V5 文件的可归因 RED 后，在固定官方 `mysql:8.4.6` 的全新 schema 中只输出 `M04-T05_OK:49:10:91:122:10`；Flyway 首次 migrate、validate 和零项二次 migrate 全部通过；
- reactor `test`/`verify` 150/150、六层 Enforcer、JAR 五份迁移资源、范围、格式、清理和干净工作树门禁得到预期结果；
- 实现提交精确包含一个 V5 SQL，未修改 POM、Java、YAML、schema、template、V1～V4、其他迁移或模块，未提交临时 harness、classpath、容器数据或生成物。

## Risks

- 三个模板共 32 列当前没有样例行；已批准的精确类型与可空性只能由实际 MySQL 8.4.6 schema 门禁确认，未来真实值不符时必须通过新设计和前向迁移处理；
- `share_float`、`stk_rewards`、`stk_holdertrade` 与两个 top-10 表包含较长复合主键；必须由固定 MySQL 8.4.6 实际门禁确认索引长度，不得缩窄业务类型或改变键绕过失败；
- `pledge_detail` 的 14 个 FINGERPRINT 输入均允许空值；V5 只存储内部 `business_key`，不实现编码。后续指纹实现必须保持字段原序、UTF-8、长度前缀和显式空值标记；
- 现有 Flyway 会提示其最高已测试 MySQL 为 8.1，但 M04-T02～T04 已在同一 8.4.6 镜像实际通过；本任务仍以 migrate/validate/二次 migrate 和结果级 schema 断言为准；
- 已发布 V1～V5 后不得原地修改历史迁移；任何后续 schema 调整都必须通过新的前向 Flyway 迁移发布。
