# M04-T01 V1 基础与组织表——任务设计

任务编号：`M04-T01`
对应任务：[M04-T01](../superpowers/plans/tensor-modules/M04-flyway-schema.md#task-m04-t01-v1-基础与组织表35hsql)
实施产物：`V1__create_basic_and_organization_tables.sql`

## Goal

创建第一份生产 Flyway 迁移，以固定、可审阅的 MySQL 8.4 SQL 建立 M03-T02 已冻结的 11 张基础与组织来源表。每张表必须逐列保持 YAML 的名称、顺序、类型和可空性，使用批准的主键/指纹键、最小查询索引及统一来源字段，使后续 Flyway 迁移、Upsert 和结构总校验可以直接消费稳定的 V1 schema。

## Scope

包含：

- 只创建任务卡指定的 V1 迁移文件；
- 创建 `stock_basic`、`stock_company`、`hs_const`、`trade_cal`、`new_share`、`namechange`、`stk_managers`、`broker_recommend`、`index_classify`、`index_member` 和 `index_member_all` 对应的 11 张 `tushare_pro__*` 表；
- 按 M03-T02 的 93 个业务列原序机械转换 MySQL 类型和可空性；
- 每表追加三个来源字段，只有 `stk_managers` 追加内部 `business_key`；
- 声明十个 COMPOSITE 主键、一个 FINGERPRINT 主键和本设计冻结的六个最小二级索引；
- 使用 `/private/tmp/M04T01SchemaCheck.java`、现有 Flyway/MySQL 依赖和临时 `mysql:8.4` 容器执行可归因 RED、Flyway GREEN、`information_schema` 校验、reactor 回归、JAR 内容和范围门禁。

排除：

- 不修改 POM、Java/YAML/schema/template、M03 产物、应用配置或其他模块；
- 不创建永久 Java 测试、测试资源、生成器或第二个 SQL 文件；
- 不创建 M04-T02～T06 的表、fixture 表、Flyway history 之外的辅助表、视图、触发器、外键或存储过程；
- 不在 SQL 中使用 `IF NOT EXISTS`、运行时推导 DDL、默认时间值、自动更新时间或静默兼容已有错误 schema；
- 不新增与 metadata filters 无关的索引，不把 `business_key` 暴露为业务字段，也不实现 Upsert、查询或启动校验服务。

## Approach

### 迁移结构与统一规则

迁移文件按以下固定顺序组成：

1. `SET time_zone = '+00:00';`，固定 Flyway 当前迁移会话为 UTC；
2. 按 M03-T02 设计顺序写 11 个无 `IF NOT EXISTS` 的 `CREATE TABLE`；
3. 每张表先按 YAML `displayOrder` 写全部业务列；
4. `stk_managers` 在业务列后写 `business_key CHAR(64) NOT NULL`，其他表不写该列；
5. 按序追加 `source_plugin VARCHAR(64) NOT NULL`、`source_api VARCHAR(64) NOT NULL`、`ingested_at DATETIME(3) NOT NULL`；
6. 在列定义之后声明主键与二级索引；
7. 每张表以 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs` 结束。

所有表名、列名和索引名统一使用反引号包围；字面标识符只来自本设计和 M03-T02，禁止插入变量或用户输入。全部业务列名称、顺序、`logicalType`、长度、精度和 `nullable` 以 `docs/task-designs/M03-T02-design.md` 的“精确 93 列类型图”和 11 份运行时 YAML 为同一冻结基线；两者不一致时停止，不由实施者选择一方覆盖另一方。

逻辑类型只按下表机械转换，不做字段名特例：

| M03 logicalType | MySQL DDL | 可空性 |
|---|---|---|
| `STRING` + `length: n` | `VARCHAR(n)` | `nullable: false` → `NOT NULL`，否则显式 `NULL` |
| `TEXT` | `TEXT` | 同上 |
| `DATE` | `DATE` | 同上 |
| `MONTH` | `CHAR(6)` | 同上 |
| `LONG` | `BIGINT` | 同上 |
| `DECIMAL` + `precision: p, scale: s` | `DECIMAL(p,s)` | 同上 |

不得把 `STRING(64)` 缩为 TRD 示例中的 `VARCHAR(16)`，不得把 `DECIMAL(38,18)` 改为浮点类型，不得为 nullable 列写默认值。`TEXT` 业务列不参与本任务的任何索引。

### 表、主键和索引总表

| API | 业务列数 | 主键（精确顺序） | 额外技术列 | 二级索引（精确名称与顺序） |
|---|---:|---|---|---|
| `stock_basic` | 10 | `(ts_code)` | None | None；PK 已覆盖 `ts_code` |
| `stock_company` | 18 | `(ts_code)` | None | None；PK 已覆盖 `ts_code` |
| `hs_const` | 5 | `(hs_type, ts_code, in_date)` | None | `idx_hs_const_ts_code (ts_code)` |
| `trade_cal` | 4 | `(exchange, cal_date)` | None | None；metadata filters 为空 |
| `new_share` | 12 | `(ts_code)` | None | None；PK 已覆盖 `ts_code` |
| `namechange` | 6 | `(ts_code, start_date, name)` | None | `idx_namechange_ann_date (ann_date)`；`ts_code` 已由 PK 覆盖 |
| `stk_managers` | 11 | `(business_key)` | `business_key CHAR(64) NOT NULL` | `idx_stk_managers_ts_code (ts_code)`；`idx_stk_managers_ann_date (ann_date)` |
| `broker_recommend` | 4 | `(month, broker, ts_code)` | None | `idx_broker_recommend_ts_code (ts_code)` |
| `index_classify` | 7 | `(index_code)` | None | None；metadata filters 为空 |
| `index_member` | 5 | `(index_code, con_code, in_date)` | None | None；metadata filters 为空 |
| `index_member_all` | 11 | `(l1_code, l2_code, l3_code, ts_code, in_date)` | None | `idx_index_member_all_ts_code (ts_code)` |

主键列名和顺序逐字来自 M03-T02/TRD 9.4。FINGERPRINT 表保留全部 11 个 nullable 业务列，不能把身份字段改为 `NOT NULL`；仅内部 `business_key` 不可空并作为主键。

二级索引采用 TRD 9.5 的最小确定性规则：对 metadata `filters` 中的每个字段建立单列索引，除非相同字段已经是主键最左前缀。授权输入没有建立“高频组合查询”的事实，因此本任务不创建组合二级索引。最终必须恰有六个非 PRIMARY 索引，名称和字段顺序与上表一致。

### 临时 Flyway/schema harness

`/private/tmp/M04T01SchemaCheck.java` 只用于本任务验证，不进入 Git。它必须：

- 先从当前目录向上定位同时包含 `data-plane/pom.xml` 和 M03-T02 YAML 目录的仓库根；
- 在任何数据库连接前断言 V1 文件存在，缺失时只以 `migration file missing: <relative-path>` 失败，形成可归因 RED；
- 通过公开 `DatasetDefinitionLoader` 加载 `classpath*:datasets/tushare_pro/*.yaml`，筛选并断言本设计的 11 API 恰有 93 个业务列；
- 使用 `Flyway.configure().dataSource(...).locations("filesystem:<migration-directory>")` 对空 `tensor` schema 执行迁移，断言只执行 V1 一项，再执行 `validate()` 并确认第二次 `migrate()` 执行 0 项；
- 查询 `information_schema.tables`、`columns` 和 `statistics`，逐表比较 business/technical/source 列的名称、序号、MySQL 类型参数、可空性、主键顺序和本设计二级索引；
- 断言恰有 11 张 `tushare_pro__*` 表、93 个业务列、127 个总列（93 + 11×3 来源列 + 1 个 `business_key`）、11 个 PRIMARY 索引和六个二级索引；
- 逐表断言 `ENGINE=InnoDB`、`TABLE_COLLATION=utf8mb4_0900_as_cs`，并断言三个来源字段位置和类型完全一致；
- 成功时只输出 `M04-T01_OK:11:93:127:6`。

临时 MySQL 使用 Docker 官方 `mysql:8.4`，随机映射本机端口，schema 为 `tensor`；容器启动参数固定服务端字符集和排序规则。容器名、密码和本地变量只用于本任务，完成或失败都必须停止容器，不能复用已有开发 schema。

### 失败边界

- 迁移文件缺失、Flyway 执行/校验失败、执行项不是一项、第二次迁移仍有 pending、表/列/键/索引/引擎/排序规则任一漂移都使 harness 非零退出；
- V1 必须在全新 schema 一次成功；已有部分表不能被 `IF NOT EXISTS` 掩盖；
- Docker daemon、`mysql:8.4` 拉取或临时端口不可用是环境阻塞，不授权修改 POM、放宽 MySQL 版本、改用 H2/SQLite 或跳过实际 schema 校验；
- Maven 依赖下载失败必须重试既定命令或报告环境阻塞，不能提交依赖 JAR、wrapper 或本地仓库内容；
- M03-T02 设计与运行时 YAML 若发生真实冲突，停止实施并刷新设计，不在 SQL 中猜测。

## Files

只创建：

- `data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql`

临时且必须清理：

- `/private/tmp/M04T01SchemaCheck.java`
- `/private/tmp/M04-T01-classpath.txt`
- Docker 容器 `tensor-m04-t01-mysql`
- Maven 生成的各模块 `target/`

实现提交消息固定为 `feat(db): create basic and organization tables`。提交只暂存唯一 V1 SQL；任务设计、交接、看板、临时 harness、classpath 文件、生成物和其他源码不得混入实现提交。

## Tests

### 基线与可归因 RED

先运行现有 reactor 到 `tensor-app`：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

预期退出 0，现有测试分布为 plugin-api 79、core 0、tushare 58、fixture 0、app 13，合计 150 项，0 failure、0 error、0 skipped；六层 Enforcer 均通过且没有新增警告类别。首次隔离仓库可能需要下载既定依赖，网络失败不属于代码 RED。

随后创建完整临时 harness，并准备其现有依赖 classpath：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -DskipTests install
mvn -q -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-app/pom.xml -DincludeScope=test \
  dependency:build-classpath \
  -Dmdep.outputFile=/private/tmp/M04-T01-classpath.txt

m04_t01_cp=$(tr -d '\n' < /private/tmp/M04-T01-classpath.txt)
java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t01_cp" \
  /private/tmp/M04T01SchemaCheck.java \
  'jdbc:mysql://127.0.0.1:1/tensor' root unavailable
```

在 V1 文件不存在时，最后一条必须在尝试连接前只因 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql` 非零退出；不得因 harness 编译、classpath、loader、schema 或依赖错误失败。

### Flyway GREEN 与实际 schema

创建 V1 后启动隔离 MySQL 8.4：

```bash
trap 'docker stop tensor-m04-t01-mysql >/dev/null 2>&1 || true' EXIT
docker run --detach --rm --name tensor-m04-t01-mysql \
  --publish 127.0.0.1::3306 \
  --env MYSQL_ROOT_PASSWORD=tensor-m04-t01 \
  --env MYSQL_DATABASE=tensor \
  mysql:8.4 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_as_cs

for m04_t01_attempt in {1..60}; do
  docker exec tensor-m04-t01-mysql \
    mysqladmin ping --silent -uroot -ptensor-m04-t01 && break
  sleep 1
done
docker exec tensor-m04-t01-mysql \
  mysqladmin ping --silent -uroot -ptensor-m04-t01
m04_t01_port=$(docker port tensor-m04-t01-mysql 3306/tcp | sed 's/.*://')

java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t01_cp" \
  /private/tmp/M04T01SchemaCheck.java \
  "jdbc:mysql://127.0.0.1:${m04_t01_port}/tensor?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" \
  root tensor-m04-t01
```

预期 Flyway 只执行 V1，随后 validate 和零项二次 migrate 通过；harness 只输出 `M04-T01_OK:11:93:127:6` 并退出 0。任何来自 SQL 语法、MySQL 8.4 保留字、索引长度、重复索引或 `information_schema` 对照的失败都属于实现 RED，必须修正唯一 SQL 后重跑同一 harness。

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
  | rg '^db/migration/V1__create_basic_and_organization_tables\.sql$'
```

预期恰输出一行。最后停止临时容器，清理 Maven 产物和 `/private/tmp` 两个文件：

```bash
docker stop tensor-m04-t01-mysql
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am clean

git status --short --untracked-files=all -- \
  data-plane/tensor-app/src/main/resources/db/migration
git diff --check
```

提交前 status 必须精确列出 Files 节一个新 SQL，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确单文件范围，工作树干净。

## Acceptance

- V1 生产迁移精确创建 M03-T02 的 11 张表，恰含 93 个原序业务列、33 个来源列和 `stk_managers` 一个内部 `business_key`，总计 127 列；
- 所有业务列的 MySQL 类型参数与可空性机械匹配 YAML；MONTH 为 `CHAR(6)`、LONG 为 `BIGINT`、DECIMAL 保持 `(38,18)`，没有默认值或静默缩窄；
- 十个 COMPOSITE 主键和一个 FINGERPRINT 主键的名称/顺序正确，最终恰有本设计六个二级索引且没有主键前缀重复索引；
- 11 表均为 InnoDB、`utf8mb4_0900_as_cs`，每表来源字段按固定顺序和类型追加，迁移会话固定 UTC；
- 完整临时 harness 经历缺 V1 文件的可归因 RED 后，在全新 MySQL 8.4 中只输出 `M04-T01_OK:11:93:127:6`；Flyway 首次 migrate、validate 和零项二次 migrate 全部通过；
- reactor `test`/`verify` 150/150、六层 Enforcer、JAR 单迁移资源、范围、格式、清理和干净工作树门禁得到预期结果；
- 实现提交精确包含一个 V1 SQL，未修改 POM、Java、YAML、schema、模板、其他迁移或模块，未提交临时 harness、classpath、容器数据或生成物。

## Risks

- 实际 schema 验证依赖可用的 Docker daemon 和 `mysql:8.4` 镜像；环境不可用时必须报告阻塞，不能用非 MySQL 数据库替代。
- `mysql:8.4` 固定 LTS 主次版本但允许官方维护补丁更新；若补丁更新改变保留字或 `information_schema` 表达，SQL 仍以 MySQL 8.4 实际执行和本设计结果级断言为准，不得放宽业务 schema。
- 临时 harness 动态从 YAML 构造业务列期望，但主键、技术列和二级索引矩阵必须使用本设计的独立字面基线；不得从 SQL 反向生成期望。
- 后续 YAML 合法变更必须通过新的元数据设计和前向 Flyway 迁移发布，不能修改已经发布的 V1 来兼容运行中数据库。
