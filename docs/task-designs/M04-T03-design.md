# M04-T03 V3 互联互通与转融通表——任务设计

任务编号：`M04-T03`
对应任务：[M04-T03](../superpowers/plans/tensor-modules/M04-flyway-schema.md#task-m04-t03-v3-互联互通与转融通表35hsql)
实施产物：`V3__create_connect_and_slb_tables.sql`

## Goal

创建第三份生产 Flyway 迁移，以固定、可审阅的 MySQL 8.4.6 SQL 建立 M03-T05 已冻结的 6 张互联互通与转融通来源表。每张表必须逐列保持 YAML 的名称、顺序、类型和可空性，使用批准的复合主键、最小查询索引及统一来源字段，并与已发布 V1、V2 组成可重复迁移和校验的 30 表 schema。

## Scope

包含：

- 只创建任务卡指定的 V3 迁移文件；
- 创建 `moneyflow_hsgt`、`hsgt_top10`、`hk_hold`、`slb_len`、`slb_sec` 和 `slb_sec_detail` 对应的 6 张 `tushare_pro__*` 表；
- 按 M03-T05 的 44 个业务列原序机械转换 MySQL 类型和可空性，三个空样例 SLB 数据集仍完整建表；
- 每表追加三个来源字段，不追加 `business_key` 或其他技术列；
- 声明 6 个 COMPOSITE 主键和本设计冻结的 4 个最小二级索引；
- 使用 `/private/tmp/M04T03SchemaCheck.java`、现有 Flyway/MySQL 依赖和本机 Colima 中固定的官方 `mysql:8.4.6` 执行可归因 RED、V1–V3 Flyway GREEN、`information_schema` 校验、reactor 回归、JAR 内容和范围门禁。

排除：

- 不修改 POM、Java/YAML/schema/template、V1、V2、M03 产物、应用配置或其他模块；
- 不创建永久 Java 测试、测试资源、生成器或第四个 SQL 文件；M04-T06 负责永久 49 表总契约；
- 不创建 M04-T04～T06 的表、fixture 表、Flyway history 之外的辅助表、视图、触发器、外键或存储过程；
- 不因 `slb_len`、`slb_sec`、`slb_sec_detail` 的模板样例为空而删列、改型或放宽业务键；
- 不在 SQL 中使用 `IF NOT EXISTS`、运行时推导 DDL、默认时间值、自动更新时间或静默兼容已有错误 schema；
- 不新增与 metadata filters 无关的索引，不实现 Upsert、查询、下载、REST、前端或启动校验服务。

## Approach

### 迁移结构与统一规则

迁移文件按以下固定顺序组成：

1. `SET time_zone = '+00:00';`，固定 Flyway 当前迁移会话为 UTC；
2. 按 M03-T05 设计顺序写 6 个无 `IF NOT EXISTS` 的 `CREATE TABLE`；
3. 每张表先按 YAML `displayOrder` 写全部业务列；
4. 按序追加 `source_plugin VARCHAR(64) NOT NULL`、`source_api VARCHAR(64) NOT NULL`、`ingested_at DATETIME(3) NOT NULL`；
5. 在列定义之后声明主键与二级索引；
6. 每张表以 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs` 结束。

6 表顺序固定为：`moneyflow_hsgt`、`hsgt_top10`、`hk_hold`、`slb_len`、`slb_sec`、`slb_sec_detail`。

所有表名、列名和索引名统一使用反引号包围；字面标识符只来自本设计和 M03-T05 设计，禁止插入变量或用户输入。全部业务列名称、顺序、`logicalType`、长度、精度和 `nullable` 以 `docs/task-designs/M03-T05-design.md` 的精确类型图及其 6 份运行时 YAML 为同一冻结基线；设计与 YAML 不一致时停止，不由实施者选择一方覆盖另一方。

逻辑类型只按下表机械转换，不做字段名特例：

| M03 logicalType | MySQL DDL | 可空性 |
|---|---|---|
| `STRING` + `length: n` | `VARCHAR(n)` | `nullable: false` → `NOT NULL`，否则显式 `NULL` |
| `DATE` | `DATE` | 同上 |
| `LONG` | `BIGINT` | 同上 |
| `DECIMAL` + `precision: p, scale: s` | `DECIMAL(p,s)` | 同上 |

不得把 `DECIMAL(38,18)` 改为整数或浮点类型；只有 M03-T05 已冻结为 `LONG` 的 `rank`、`market_type` 和 `tenor` 转为 `BIGINT`。不得为 nullable 列写默认值。所有 6 表均为 COMPOSITE 键表，不得增加 `business_key`。

### 表、主键和索引总表

| API | 业务列数 | 主键（精确顺序） | 二级索引（精确名称与顺序） |
|---|---:|---|---|
| `moneyflow_hsgt` | 7 | `(trade_date)` | None；PK 已覆盖唯一 filter `trade_date` |
| `hsgt_top10` | 11 | `(trade_date, ts_code, market_type)` | `idx_hsgt_top10_ts_code (ts_code)` |
| `hk_hold` | 7 | `(trade_date, code, exchange)` | `idx_hk_hold_ts_code (ts_code)` |
| `slb_len` | 6 | `(trade_date, ob)` | None；PK 已覆盖唯一 filter `trade_date` |
| `slb_sec` | 7 | `(trade_date, ts_code)` | `idx_slb_sec_ts_code (ts_code)` |
| `slb_sec_detail` | 6 | `(trade_date, ts_code, tenor, fee_rate)` | `idx_slb_sec_detail_ts_code (ts_code)` |

主键列名和顺序逐字来自 M03-T05 与 TRD 9.4。`hsgt_top10.market_type`、`hk_hold.code/exchange`、`slb_len.ob` 和 `slb_sec_detail.tenor/fee_rate` 保持不可空并参与主键。`hk_hold` 必须保留业务列原序中的 `code` 与 `ts_code` 两列：主键使用 `code`，二级索引使用可空的 `ts_code`，不得改名、合并或互换。

二级索引沿用已发布迁移的最小确定性规则：对 metadata `filters` 中的每个字段建立单列索引，除非相同字段已经是主键最左前缀。V3 最终必须恰有上表 4 个非 PRIMARY 索引；与 V1–V2 合计为 22 个非 PRIMARY 索引。

### 临时 Flyway/schema harness

`/private/tmp/M04T03SchemaCheck.java` 只用于本任务验证，不进入 Git。它必须：

- 先从当前目录向上定位同时包含 `data-plane/pom.xml` 和 M03 YAML 目录的仓库根；
- 在任何数据库连接前按序断言已发布 V1、V2 文件存在，再断言 V3 文件存在；V3 缺失时只以 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V3__create_connect_and_slb_tables.sql` 失败，形成可归因 RED；
- 通过公开 `DatasetDefinitionLoader` 加载 `classpath*:datasets/tushare_pro/*.yaml`，筛选并断言本设计的 6 API 恰有 44 个业务列；
- 使用 `Flyway.configure().dataSource(...).locations("filesystem:<migration-directory>")` 对空 `tensor` schema 执行迁移，断言首次恰执行 V1、V2、V3 三项，再执行 `validate()` 并确认第二次 `migrate()` 执行 0 项；
- 查询 `information_schema.tables`、`columns` 和 `statistics`，逐表比较 V3 的业务/来源列名称、序号、MySQL 类型参数、可空性、主键顺序和本设计二级索引；
- 断言迁移后恰有 30 张 `tushare_pro__*` 表和 361 个总列；其中 V3 恰有 6 表、44 个业务列、62 个总列（44 + 6×3 来源列）、6 个 PRIMARY 和 4 个二级索引；全体 30 表合计 30 个 PRIMARY 和 22 个二级索引；
- 逐个 V3 表断言 `ENGINE=InnoDB`、`TABLE_COLLATION=utf8mb4_0900_as_cs`，并断言三个来源字段位置和类型完全一致；
- 单独断言 `tushare_pro__hk_hold` 恰有 10 列，同时保留 `code` 与 `ts_code`，主键顺序为 `(trade_date, code, exchange)`，二级索引为 `idx_hk_hold_ts_code (ts_code)`；
- 单独断言三个 SLB 表合计恰有 19 个业务列；
- 成功时只输出 `M04-T03_OK:30:6:44:62:4`。

临时数据库固定使用本机 Colima 中已有的官方 `mysql:8.4.6`，随机映射本机端口，schema 为 `tensor`；容器启动参数固定服务端字符集和排序规则。容器名、密码和本地变量只用于本任务，完成或失败都必须停止容器，不能复用已有开发 schema，不得创建或依赖浮动 `mysql:8.4` 标签。官方镜像初始化阶段会先启动 `port: 0` 的临时 socket 服务，因此 readiness 必须轮询容器内 `mysqladmin ping -h127.0.0.1`，以真实 TCP MySQL 协议就绪为准，不能只轮询默认 Unix socket 或只探测宿主端口可连接。

### 失败边界

- V1、V2 或 V3 文件缺失、Flyway 执行/校验失败、首次执行项不是三项、第二次迁移仍有 pending、表/列/键/索引/引擎/排序规则任一漂移都使 harness 非零退出；
- V1–V3 必须在全新 schema 一次成功；已有部分表不能被 `IF NOT EXISTS` 掩盖；
- 本机 Colima/Docker daemon、官方 `mysql:8.4.6` 镜像或临时端口不可用是环境阻塞，不授权改用 H2、SQLite、MariaDB、其他 MySQL 版本或跳过实际 schema 校验；
- Maven 依赖下载失败必须重试既定命令或报告环境阻塞，不能提交依赖 JAR、wrapper 或本地仓库内容；
- M03-T05 设计与运行时 YAML 若发生真实冲突，停止实施并刷新设计，不在 SQL 中猜测；
- 任一复合主键在 MySQL 8.4.6 中触发索引长度、保留字或其他 DDL 错误都属于实现 RED，只能修正 V3 的可实现错误；不得缩窄已冻结业务类型或改变业务键来绕过错误。

## Files

只创建：

- `data-plane/tensor-app/src/main/resources/db/migration/V3__create_connect_and_slb_tables.sql`

临时且必须清理：

- `/private/tmp/M04T03SchemaCheck.java`
- `/private/tmp/M04-T03-classpath.txt`
- Docker 容器 `tensor-m04-t03-mysql`
- Maven 生成的各模块 `target/`

实现提交消息固定为 `feat(db): create connect and SLB tables`。提交只暂存唯一 V3 SQL；任务设计、交接、看板、临时 harness、classpath 文件、生成物和其他源码不得混入实现提交。

## Tests

### 基线、空模板与可归因 RED

先运行现有 reactor 到 `tensor-app`：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

预期退出 0，现有测试分布为 plugin-api 79、core 0、tushare 58、fixture 0、app 13，合计 150 项，0 failure、0 error、0 skipped；六层 Enforcer 均通过且没有新增警告类别。

确认三个 SLB 模板字段完整但没有样例数据：

```bash
jq -e '.data == [] and (.fields | length) == 6' docs/data-template/slb_len.json
jq -e '.data == [] and (.fields | length) == 7' docs/data-template/slb_sec.json
jq -e '.data == [] and (.fields | length) == 6' docs/data-template/slb_sec_detail.json
```

三条命令均预期输出 `true` 并退出 0。随后创建完整临时 harness，并准备现有依赖 classpath：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -DskipTests install
mvn -q -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-app/pom.xml -DincludeScope=test \
  dependency:build-classpath \
  -Dmdep.outputFile=/private/tmp/M04-T03-classpath.txt

m04_t03_cp=$(tr -d '\n' < /private/tmp/M04-T03-classpath.txt)
java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t03_cp" \
  /private/tmp/M04T03SchemaCheck.java \
  'jdbc:mysql://127.0.0.1:1/tensor' root unavailable
```

在 V3 文件不存在时，最后一条必须在尝试连接前只因 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V3__create_connect_and_slb_tables.sql` 非零退出；不得因 harness 编译、classpath、loader、V1、V2、schema 或依赖错误失败。

### Flyway GREEN 与实际 schema

先确认固定镜像已经存在，再启动隔离 MySQL 8.4.6：

```bash
m04_t03_docker=/opt/homebrew/opt/docker/bin/docker
"$m04_t03_docker" image inspect mysql:8.4.6 \
  --format '{{.Id}} {{.Os}}/{{.Architecture}} {{json .Config.Env}}'

trap '"$m04_t03_docker" stop tensor-m04-t03-mysql >/dev/null 2>&1 || true' EXIT
"$m04_t03_docker" run --detach --rm --name tensor-m04-t03-mysql \
  --publish 127.0.0.1::3306 \
  --env MYSQL_ROOT_PASSWORD=tensor-m04-t03 \
  --env MYSQL_DATABASE=tensor \
  mysql:8.4.6 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_as_cs

for m04_t03_attempt in {1..60}; do
  "$m04_t03_docker" exec tensor-m04-t03-mysql \
    mysqladmin ping --silent -h127.0.0.1 -uroot -ptensor-m04-t03 && break
  sleep 1
done
"$m04_t03_docker" exec tensor-m04-t03-mysql \
  mysqladmin ping --silent -h127.0.0.1 -uroot -ptensor-m04-t03
m04_t03_port=$("$m04_t03_docker" port tensor-m04-t03-mysql 3306/tcp | sed 's/.*://')

java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t03_cp" \
  /private/tmp/M04T03SchemaCheck.java \
  "jdbc:mysql://127.0.0.1:${m04_t03_port}/tensor?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" \
  root tensor-m04-t03
```

镜像检查必须显示 `MYSQL_MAJOR=8.4` 与 `MYSQL_VERSION=8.4.6-1.el9`。Flyway 必须首次执行 V1、V2、V3 三项，随后 validate 和零项二次 migrate 通过；harness 只输出 `M04-T03_OK:30:6:44:62:4` 并退出 0。任何来自 SQL 语法、MySQL 8.4.6 保留字、索引长度、重复索引或 `information_schema` 对照的失败都属于实现 RED，必须只修正唯一 V3 SQL 后重跑同一 harness。

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
  | rg '^db/migration/V(1__create_basic_and_organization_tables|2__create_market_and_trading_tables|3__create_connect_and_slb_tables)\.sql$'
```

预期恰输出 V1、V2、V3 各一行。最后停止临时容器并清理 Maven 产物和 `/private/tmp` 两个文件：

```bash
"$m04_t03_docker" stop tensor-m04-t03-mysql
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am clean
rm -f /private/tmp/M04T03SchemaCheck.java /private/tmp/M04-T03-classpath.txt

git status --short --untracked-files=all -- \
  data-plane/tensor-app/src/main/resources/db/migration
git diff --check
```

提交前 status 必须精确列出 Files 节一个新 V3 SQL，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确单文件范围，工作树干净。

## Acceptance

- V3 生产迁移精确创建 M03-T05 的 6 张表，恰含 44 个原序业务列和 18 个来源列，总计 62 列；V1–V3 合计恰有 30 张来源表和 361 列；
- 所有业务列的 MySQL 类型参数与可空性机械匹配 YAML；`STRING` 保持批准长度、日期为 `DATE`、`LONG` 只转为 `BIGINT`、其他数值保持 `DECIMAL(38,18)`，没有默认值或静默缩窄；
- 6 个 COMPOSITE 主键的名称/顺序正确，最终恰有本设计 4 个二级索引且没有主键前缀重复索引；V1–V3 合计 30 个 PRIMARY 和 22 个二级索引；
- 6 表均为 InnoDB、`utf8mb4_0900_as_cs`，每表来源字段按固定顺序和类型追加，迁移会话固定 UTC；`hk_hold` 恰有 10 列、保留 `code` 与 `ts_code` 的不同职责；三个 SLB 表在空样例基线下仍合计建立 19 个业务列；
- 完整临时 harness 经历缺 V3 文件的可归因 RED 后，在本机官方 `mysql:8.4.6` 的全新 schema 中只输出 `M04-T03_OK:30:6:44:62:4`；Flyway 首次 migrate、validate 和零项二次 migrate 全部通过；
- reactor `test`/`verify` 150/150、六层 Enforcer、JAR 三份迁移资源、范围、格式、清理和干净工作树门禁得到预期结果；
- 实现提交精确包含一个 V3 SQL，未修改 POM、Java、YAML、schema、template、V1、V2、其他迁移或模块，未提交临时 harness、classpath、容器数据或生成物。

## Risks

- 三个 SLB 模板当前没有样例行，但 19 个业务列类型和可空性已经由 M03-T05 冻结；真实上游值若不符合该映射，必须通过新的设计裁决和前向迁移处理，不得在 V3 或适配阶段静默改型；
- `hk_hold.code` 与 `hk_hold.ts_code` 同时存在但职责不同；建表、主键、索引和后续查询必须保留两列，不得因名称相似而合并；
- `moneyflow_hsgt` 样例以字符串承载数值，但数据库类型仍为 `DECIMAL(38,18)`，后续适配必须严格按十进制文本转换；
- M04-T02 验证发现官方镜像初始化阶段的 Unix socket 会先于 TCP 就绪；本设计已把 readiness 固定为容器内 TCP 协议探测，若仍超时应报告环境阻塞，不得改用任意延时或其他数据库版本；
- 现有 Flyway 版本会提示其最高已测试 MySQL 为 8.1，但 M04-T01、M04-T02 已在同一 MySQL 8.4.6 镜像实际通过；本任务仍以实际 8.4.6 结果级门禁为准，不因警告放宽或跳过验证；
- 后续 YAML 合法变更必须通过新的元数据设计和前向 Flyway 迁移发布，不能修改已经发布的 V1、V2、V3 来兼容运行中数据库。
