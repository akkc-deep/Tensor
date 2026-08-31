# M03-T03 行情与估值 7 数据集 YAML——任务设计

任务编号：`M03-T03`
对应任务：[M03-T03](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t03-行情与估值-7-数据集25hyaml)
实施产物：`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 7 份行情与估值数据集 YAML

## Goal

为 `daily`、`weekly`、`monthly`、`adj_factor`、`suspend_d`、`daily_basic` 和 `stk_limit` 建立唯一运行时元数据。每份 YAML 必须通过 M03-T01 的严格 loader，保持对应 JSON 模板的完整字段名和顺序，采用项目所有者批准的 62 列类型/长度/可空性映射，并与 PRD 附录 A.2 的必填 `trade_date` 参数和 TRD 9.4 的复合业务键一致。

本任务完成后，后续模块可通过 `DatasetDefinitionLoader` 读取这 7 个不可变 `DatasetDefinition`，无需重新解释行情字段、估值精度、参数、业务键、筛选或固定列。

## Scope

包含：

- 只创建任务卡指定的 7 份运行时 YAML；
- 固定每份定义的插件/API/表名、分类、显示名、查询方式、参数、字段、业务键、筛选和固定列；
- 按本设计的完整 62 列类型图设置 `logicalType`、`nullable`、`length`、`precision` 和 `scale`；
- 使用 M03-T01 现有公开 loader 和 `/private/tmp` 临时 Java harness 执行缺失 7 文件的 RED、精确契约 GREEN、模块回归与 JAR 内容验证；
- 提交精确 7 个 YAML 文件。

排除：

- 不修改 Java、POM、M00 schema/示例、M02 records、M03-T01 loader/test、既有 11 份 YAML、其他模块或 `docs/data-template/`；
- 不创建永久测试类；M03-T09 负责 49/49 永久 Java 契约测试；
- 不读取其他 42 份模板，也不把本任务 7 份模板的完整 `data` 数组载入上下文；字段基线只使用已批准的 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影；
- 不新增源字段、审计字段、`business_key`、批次字段或数据库列；持久化附加字段属于 M04/M06；
- 不实现参数值校验、适配、下载、数据库、REST 或前端职责。

## Approach

### 共同结构和命名

全部 YAML 固定：

- `pluginId: tushare_pro`；
- `tableName` 精确由字面前缀 `tushare_pro__` 与文件的 `apiName` 拼接；
- `category: 行情与估值`；
- `queryMode: trade_date`；
- 唯一参数为必填 `trade_date`，label 为 `交易日期`、type 为 `DATE`，省略 `description`、`defaultValue`、`allowedValues`、`pattern` 和 `relatedParameter`；
- `displayOrder` 按模板 `fields` 从 0 连续递增；列 `label` 精确等于源字段名；
- `ts_code` 使用 `STRING(64)`，日期使用 `DATE`，所有行情与估值数值使用 `DECIMAL(38,18)`；`suspend_timing` 使用 `STRING(255)`，`suspend_type` 使用 `STRING(64)`；
- 非 `STRING` 列不写 `length`，`DECIMAL` 只写 `precision: 38` 与 `scale: 18`；所有列省略 `allowedValues` 和 `longText`，由 loader 分别映射为空列表和 `false`；
- 所有定义省略 YAML 中不存在的 `batchSize`，继续采用 M02 默认值 500。

### 数据集总表

| API | displayName | parameters | businessKey | filters | fixedColumn |
|---|---|---|---|---|---|
| `daily` | 日线行情 | `[trade_date]` | `COMPOSITE: [ts_code, trade_date]` | `[ts_code, trade_date]` | `ts_code` |
| `weekly` | 周线行情 | `[trade_date]` | `COMPOSITE: [ts_code, trade_date]` | `[ts_code, trade_date]` | `ts_code` |
| `monthly` | 月线行情 | `[trade_date]` | `COMPOSITE: [ts_code, trade_date]` | `[ts_code, trade_date]` | `ts_code` |
| `adj_factor` | 复权因子 | `[trade_date]` | `COMPOSITE: [ts_code, trade_date]` | `[ts_code, trade_date]` | `ts_code` |
| `suspend_d` | 每日停复牌信息 | `[trade_date]` | `COMPOSITE: [ts_code, trade_date]` | `[ts_code, trade_date]` | `ts_code` |
| `daily_basic` | 每日估值与市场指标 | `[trade_date]` | `COMPOSITE: [ts_code, trade_date]` | `[ts_code, trade_date]` | `ts_code` |
| `stk_limit` | 每日涨跌停价格 | `[trade_date]` | `COMPOSITE: [trade_date, ts_code]` | `[ts_code, trade_date]` | `ts_code` |

`filters` 与 `fixedColumn` 采用项目所有者 2026-09-01 批准的固定策略。`stk_limit` 的列顺序和业务键顺序分别来自模板与 TRD，不因 filters 的展示顺序而改变。

### 可空性规则

- 每个 COMPOSITE 业务键中的 `ts_code` 和 `trade_date` 固定 `nullable: false`；
- 所有非业务键行情、成交量、成交额、复权因子和估值数值固定 `nullable: true`，不把上游 null 转为 0；
- `suspend_timing` 与 `suspend_type` 固定 `nullable: true`；
- 不根据单个样例中的非空值推断其他列必填；空样例 `monthly` 仍按与日线/周线相同的字段语义完整定型。

### 精确 62 列类型图

下列顺序就是每份 YAML 的 `columns` 顺序；`nullable`、长度和精度必须逐项照抄。

#### `daily`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `open` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `high` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `low` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `close` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `pre_close` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `change` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `pct_chg` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `vol` | `DECIMAL` | true | precision 38, scale 18 |
| 10 | `amount` | `DECIMAL` | true | precision 38, scale 18 |

#### `weekly`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `close` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `open` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `high` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `low` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `pre_close` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `change` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `pct_chg` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `vol` | `DECIMAL` | true | precision 38, scale 18 |
| 10 | `amount` | `DECIMAL` | true | precision 38, scale 18 |

#### `monthly`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `close` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `open` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `high` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `low` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `pre_close` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `change` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `pct_chg` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `vol` | `DECIMAL` | true | precision 38, scale 18 |
| 10 | `amount` | `DECIMAL` | true | precision 38, scale 18 |

#### `adj_factor`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `adj_factor` | `DECIMAL` | true | precision 38, scale 18 |

#### `suspend_d`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `suspend_timing` | `STRING` | true | length 255 |
| 3 | `suspend_type` | `STRING` | true | length 64 |

#### `daily_basic`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `close` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `turnover_rate` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `turnover_rate_f` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `volume_ratio` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `pe` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `pe_ttm` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `pb` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `ps` | `DECIMAL` | true | precision 38, scale 18 |
| 10 | `ps_ttm` | `DECIMAL` | true | precision 38, scale 18 |
| 11 | `dv_ratio` | `DECIMAL` | true | precision 38, scale 18 |
| 12 | `dv_ttm` | `DECIMAL` | true | precision 38, scale 18 |
| 13 | `total_share` | `DECIMAL` | true | precision 38, scale 18 |
| 14 | `float_share` | `DECIMAL` | true | precision 38, scale 18 |
| 15 | `free_share` | `DECIMAL` | true | precision 38, scale 18 |
| 16 | `total_mv` | `DECIMAL` | true | precision 38, scale 18 |
| 17 | `circ_mv` | `DECIMAL` | true | precision 38, scale 18 |

#### `stk_limit`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ts_code` | `STRING` | false | length 64 |
| 2 | `up_limit` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `down_limit` | `DECIMAL` | true | precision 38, scale 18 |

### 错误边界

- 任一 YAML 的 schema、M02 构造或 M03 语义错误必须使该文件经公开 loader 以 `DATASET_MISCONFIGURED` 失败；不得把无效定义视为成功。
- 文件名/API 不一致、表名不匹配、字段顺序错位、业务键/筛选/固定列悬空或参数错误均由现有 loader/M02 契约拒绝。
- 验证 harness 必须分别通过 7 个精确 classpath 资源路径调用公开 loader，并逐项比较本设计的 API、62 列类型图、参数、业务键、筛选和固定列；只检查“能加载”不足以验收。
- 临时 harness 只存在于 `/private/tmp/M03T03MetadataCheck.java`，不得进入 Git 或仓库源码树。

## Files

只创建：

- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/daily.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/weekly.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/monthly.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/adj_factor.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/suspend_d.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/daily_basic.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/stk_limit.yaml`

实现提交消息固定为 `feat(metadata): define market datasets`。提交只暂存上述 7 个文件；任务设计、交接、看板、临时 harness、生成的 `target` 与其他源码不得混入实现提交。

## Tests

### 临时 harness 与 RED

先在 `/private/tmp/M03T03MetadataCheck.java` 创建临时 source-file harness。它必须对 7 个精确资源路径分别调用公开 `DatasetDefinitionLoader.loadAll`，硬编码并逐项断言本设计的 API、62 列类型图、参数、业务键、筛选、固定列和默认 batchSize，成功时只输出：

```text
M03-T03_OK:7
```

先不创建本任务的运行时 YAML，运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -q \
  -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=DatasetDefinitionLoaderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

m03_t03_cp=$(xmllint --xpath \
  'string(/testsuite/properties/property[@name="java.class.path"]/@value)' \
  data-plane/tensor-plugin-tushare/target/surefire-reports/TEST-com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoaderTest.xml)

java -Dslf4j.internal.verbosity=ERROR --class-path "$m03_t03_cp" \
  /private/tmp/M03T03MetadataCheck.java \
  'classpath*:datasets/tushare_pro/'
```

第一条预期 `DatasetDefinitionLoaderTest` 8/8 通过并生成精确测试 classpath；第三条预期 harness 编译成功后因首个缺失的精确资源得到 `<pattern>: no resources matched` 并退出非 0，形成只针对本任务缺失 YAML 的 RED。失败不得来自 harness 编译、依赖、schema、既有 11 份 YAML或环境错误。

### GREEN 与模块回归

创建 7 份 YAML 后重跑上述 Maven 与 harness 命令。预期：

- `DatasetDefinitionLoaderTest` 8/8 通过；
- harness 只输出 `M03-T03_OK:7` 并退出 0；
- 每个精确资源路径恰加载一个定义；
- 每个定义的字段、类型、参数、键、筛选和固定列与本设计逐项一致。

随后运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am verify
```

两条命令均预期退出 0；既有 87 项测试继续为 0 failure、0 error、0 skipped，父项目、plugin-api 与 tushare 的 Enforcer 全部通过，且无新增警告类别。

验证 JAR 内容：

```bash
jar tf data-plane/tensor-plugin-tushare/target/tensor-plugin-tushare-1.0-SNAPSHOT.jar \
  | rg '^datasets/tushare_pro/(daily|weekly|monthly|adj_factor|suspend_d|daily_basic|stk_limit)\.yaml$'
```

预期恰输出 7 行。最后运行 Maven `clean` 清除未被仓库忽略的 `target`，删除 `/private/tmp/M03T03MetadataCheck.java`，并运行：

```bash
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro
git diff --check
```

提交前第一条精确列出 Files 节 7 个 YAML，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确 7 文件范围，工作树干净。

## Acceptance

- 运行时目录恰新增 7 份行情与估值 YAML；API 名、显示名、查询方式与 PRD 附录 A.2/manifest 投影一致。
- 每份 `columns` 与对应 JSON `fields` 名称和顺序完全一致，合计 62 列；类型、长度、精度与可空性逐项符合本设计，包括空样例 `monthly`。
- 七份定义都只声明必填 `trade_date: DATE` 参数；业务键与 TRD 9.4 完全一致，filters/fixedColumn 全部引用现有列。
- 临时 harness 经历可归因 RED 后输出 `M03-T03_OK:7` GREEN；loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 7 文件、范围与格式门禁得到预期结果。
- 实现提交精确包含 7 个 YAML，未修改 Java/POM/schema/template/既有 YAML/其他模块，未提交临时 harness 或生成物。

## Risks

- `monthly` 模板在基线交易日没有样例数据，但字段与日线/周线同构；项目所有者已批准所有非键行情数值统一使用可空 `DECIMAL(38,18)`，因此实施不得根据空样例删列或改变类型。
- `suspend_timing` 的样例为 null；项目所有者已批准 `STRING(255)`，`suspend_type` 固定 `STRING(64)`，二者均可空。未来若上游文档要求枚举或更长文本，应通过新的设计裁决修改。
- `/private/tmp` harness 是本任务的聚焦可执行证据，不替代 M03-T09 的永久 49/49 Java 契约测试；若后续字段基线变化，必须同时更新授权模板/任务设计和 M03-T09 独立基线。
