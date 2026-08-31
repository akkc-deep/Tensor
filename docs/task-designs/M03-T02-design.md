# M03-T02 基础与组织 11 数据集 YAML——任务设计

任务编号：`M03-T02`
对应任务：[M03-T02](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t02-基础与组织-11-数据集30hyaml)
实施产物：`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 11 份基础与组织数据集 YAML

## Goal

为 `stock_basic`、`stock_company`、`hs_const`、`trade_cal`、`new_share`、`namechange`、`stk_managers`、`broker_recommend`、`index_classify`、`index_member` 和 `index_member_all` 建立唯一运行时元数据。每份 YAML 必须通过 M03-T01 的严格 loader，保持对应 JSON 模板的完整字段名和顺序，采用已批准的字段类型/长度/可空性映射，并与 PRD 附录 A.1 的查询参数及 TRD 9.4 的业务键一致。

本任务完成后，后续模块可通过 `DatasetDefinitionLoader` 读取这 11 个不可变 `DatasetDefinition`，无需重新解释模板、参数、业务键、筛选或展示列。

## Scope

包含：

- 只创建任务卡指定的 11 份运行时 YAML；
- 固定每份定义的插件/API/表名、分类、显示名、查询方式、参数、字段、业务键、筛选和固定列；
- 按本设计的完整 93 列类型图设置 `logicalType`、`nullable`、`length`、`precision`、`scale` 和 `longText`；
- 使用 M03-T01 现有公开 loader 和 `/private/tmp` 临时 Java harness 执行 0→11 RED/GREEN、精确契约、模块回归与 JAR 内容验证；
- 提交精确 11 个 YAML 文件。

排除：

- 不修改 Java、POM、M00 schema/示例、M02 records、M03-T01 loader/test、其他模块或 `docs/data-template/`；
- 不创建永久测试类；M03-T09 负责 49/49 永久 Java 契约测试；
- 不读取其他 38 份模板，也不把本任务 11 份模板的完整 `data` 数组载入上下文；字段基线只使用已批准的 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影；
- 不新增源字段、审计字段、`business_key`、批次字段或数据库列；持久化附加字段属于 M04/M06；
- 不实现参数值校验、适配、下载、数据库、REST 或前端职责。

## Approach

### 共同结构和命名

全部 YAML 固定：

- `pluginId: tushare_pro`；
- `tableName` 精确由字面前缀 `tushare_pro__` 与该文件的 `apiName` 拼接；
- `category: basic_organization`；
- `displayOrder` 按模板 `fields` 从 0 连续递增；
- 列 `label` 精确等于源字段名。授权输入没有列中文标签，使用源字段名可避免猜测翻译；
- 除参数 ENUM 外，列 `allowedValues` 一律省略并映射为空列表；
- 非 `TEXT` 列省略 `longText`，由 loader 映射为 `false`；`TEXT` 列显式 `longText: true`；
- `STRING` 只写 `length`，`DECIMAL` 只写 `precision: 38` 与 `scale: 18`，其余类型不写不适用的长度/精度字段；
- 所有定义省略 YAML 中不存在的 `batchSize`，继续采用 M02 默认值 500。

### 数据集总表

| API | displayName | queryMode | parameters（声明顺序） | businessKey | filters | fixedColumn |
|---|---|---|---|---|---|---|
| `stock_basic` | 股票基础信息 | `snapshot` | `list_status` | `COMPOSITE: [ts_code]` | `[ts_code]` | `ts_code` |
| `stock_company` | 上市公司基本信息 | `snapshot` | `exchange` | `COMPOSITE: [ts_code]` | `[ts_code]` | `ts_code` |
| `hs_const` | 沪深港通标的范围 | `snapshot` | `hs_type` | `COMPOSITE: [hs_type, ts_code, in_date]` | `[ts_code]` | `ts_code` |
| `trade_cal` | 交易日历 | `date_range` | `exchange, start_date, end_date` | `COMPOSITE: [exchange, cal_date]` | `[]` | `exchange` |
| `new_share` | IPO 新股发行信息 | `date_range` | `start_date, end_date` | `COMPOSITE: [ts_code]` | `[ts_code]` | `ts_code` |
| `namechange` | 证券名称变更记录 | `date_range` | `start_date, end_date` | `COMPOSITE: [ts_code, start_date, name]` | `[ts_code, ann_date]` | `ts_code` |
| `stk_managers` | 上市公司管理层信息 | `snapshot` | `[]` | `FINGERPRINT: [ts_code, ann_date, name, gender, lev, title, birthday, begin_date]` | `[ts_code, ann_date]` | `ts_code` |
| `broker_recommend` | 券商月度推荐 | `snapshot` | `month` | `COMPOSITE: [month, broker, ts_code]` | `[ts_code]` | `ts_code` |
| `index_classify` | 行业指数分类 | `snapshot` | `[]` | `COMPOSITE: [index_code]` | `[]` | `index_code` |
| `index_member` | 行业指数成分 | `snapshot` | `[]` | `COMPOSITE: [index_code, con_code, in_date]` | `[]` | `index_code` |
| `index_member_all` | 行业分级与完整成分 | `snapshot` | `[]` | `COMPOSITE: [l1_code, l2_code, l3_code, ts_code, in_date]` | `[ts_code]` | `ts_code` |

`filters` 只包含实际存在的 `ts_code`、`trade_date`、`ann_date`，并保持上述顺序。`fixedColumn` 优先使用 `ts_code`；没有 `ts_code` 时使用首个业务键字段。

### 参数定义

全部参数固定 `required: true`，省略 `description`、`defaultValue` 和 `pattern`。相同参数在所有文件中使用同一描述符：

| name | label | type | allowedValues | relatedParameter |
|---|---|---|---|---|
| `list_status` | 上市状态 | `ENUM` | `[L, P, D]` | None |
| `exchange` | 交易所 | `ENUM` | `[SSE, SZSE, BSE]` | None |
| `hs_type` | 沪深港通类型 | `ENUM` | `[SH, SZ]` | None |
| `start_date` | 开始日期 | `DATE_RANGE_MEMBER` | None | `end_date` |
| `end_date` | 结束日期 | `DATE_RANGE_MEMBER` | None | `start_date` |
| `month` | 月份 | `MONTH` | None | None |

`trade_cal` 的 `exchange`、`start_date`、`end_date` 按表中顺序声明；`new_share` 和 `namechange` 只声明 `start_date`、`end_date`。没有参数的文件写 `parameters: []`。

### 可空性和类型规则

- `COMPOSITE` 业务键中的每一列固定 `nullable: false`；该定义保证直接复合键可用。
- `FINGERPRINT` 的身份字段允许业务空值并由后续规范化编码显式空值标记，因此 `stk_managers` 的全部列固定 `nullable: true`。
- 其余非复合键列固定 `nullable: true`；不根据单个样例中的非空值推断必填。
- 日期字段使用 `DATE`；`month` 使用 `MONTH`；`employees`、`is_open` 使用 `LONG`；金额/比率使用 `DECIMAL(38,18)`；三项长叙述使用 `TEXT`；其余字段使用下表冻结的 `STRING(64|128|255)`。

### 精确 93 列类型图

下列顺序就是每份 YAML 的 `columns` 顺序；`nullable`、长度和精度必须逐项照抄。

#### `stock_basic`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `symbol` | `STRING` | true | length 64 |
| 2 | `name` | `STRING` | true | length 128 |
| 3 | `area` | `STRING` | true | length 128 |
| 4 | `industry` | `STRING` | true | length 128 |
| 5 | `cnspell` | `STRING` | true | length 64 |
| 6 | `market` | `STRING` | true | length 64 |
| 7 | `list_date` | `DATE` | true | — |
| 8 | `act_name` | `STRING` | true | length 128 |
| 9 | `act_ent_type` | `STRING` | true | length 128 |

#### `stock_company`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `com_name` | `STRING` | true | length 128 |
| 2 | `com_id` | `STRING` | true | length 64 |
| 3 | `chairman` | `STRING` | true | length 128 |
| 4 | `manager` | `STRING` | true | length 128 |
| 5 | `secretary` | `STRING` | true | length 128 |
| 6 | `reg_capital` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `setup_date` | `DATE` | true | — |
| 8 | `province` | `STRING` | true | length 128 |
| 9 | `city` | `STRING` | true | length 128 |
| 10 | `introduction` | `TEXT` | true | longText true |
| 11 | `website` | `STRING` | true | length 255 |
| 12 | `email` | `STRING` | true | length 255 |
| 13 | `office` | `STRING` | true | length 255 |
| 14 | `business_scope` | `TEXT` | true | longText true |
| 15 | `employees` | `LONG` | true | — |
| 16 | `main_business` | `TEXT` | true | longText true |
| 17 | `exchange` | `STRING` | true | length 64 |

#### `hs_const`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `hs_type` | `STRING` | false | length 64 |
| 2 | `in_date` | `DATE` | false | — |
| 3 | `out_date` | `DATE` | true | — |
| 4 | `is_new` | `STRING` | true | length 64 |

#### `trade_cal`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `exchange` | `STRING` | false | length 64 |
| 1 | `cal_date` | `DATE` | false | — |
| 2 | `is_open` | `LONG` | true | — |
| 3 | `pretrade_date` | `DATE` | true | — |

#### `new_share`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `sub_code` | `STRING` | true | length 64 |
| 2 | `name` | `STRING` | true | length 128 |
| 3 | `ipo_date` | `DATE` | true | — |
| 4 | `issue_date` | `DATE` | true | — |
| 5 | `amount` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `market_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `price` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `pe` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `limit_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 10 | `funds` | `DECIMAL` | true | precision 38, scale 18 |
| 11 | `ballot` | `DECIMAL` | true | precision 38, scale 18 |

#### `namechange`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `name` | `STRING` | false | length 128 |
| 2 | `start_date` | `DATE` | false | — |
| 3 | `end_date` | `DATE` | true | — |
| 4 | `ann_date` | `DATE` | true | — |
| 5 | `change_reason` | `STRING` | true | length 255 |

#### `stk_managers`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | true | length 64 |
| 1 | `ann_date` | `DATE` | true | — |
| 2 | `name` | `STRING` | true | length 128 |
| 3 | `gender` | `STRING` | true | length 64 |
| 4 | `lev` | `STRING` | true | length 64 |
| 5 | `title` | `STRING` | true | length 128 |
| 6 | `edu` | `STRING` | true | length 128 |
| 7 | `national` | `STRING` | true | length 128 |
| 8 | `birthday` | `DATE` | true | — |
| 9 | `begin_date` | `DATE` | true | — |
| 10 | `end_date` | `DATE` | true | — |

#### `broker_recommend`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `month` | `MONTH` | false | — |
| 1 | `broker` | `STRING` | false | length 128 |
| 2 | `ts_code` | `STRING` | false | length 64 |
| 3 | `name` | `STRING` | true | length 128 |

#### `index_classify`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `index_code` | `STRING` | false | length 64 |
| 1 | `industry_name` | `STRING` | true | length 128 |
| 2 | `level` | `STRING` | true | length 64 |
| 3 | `industry_code` | `STRING` | true | length 64 |
| 4 | `is_pub` | `STRING` | true | length 64 |
| 5 | `parent_code` | `STRING` | true | length 64 |
| 6 | `src` | `STRING` | true | length 64 |

#### `index_member`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `index_code` | `STRING` | false | length 64 |
| 1 | `con_code` | `STRING` | false | length 64 |
| 2 | `in_date` | `DATE` | false | — |
| 3 | `out_date` | `DATE` | true | — |
| 4 | `is_new` | `STRING` | true | length 64 |

#### `index_member_all`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `l1_code` | `STRING` | false | length 64 |
| 1 | `l1_name` | `STRING` | true | length 128 |
| 2 | `l2_code` | `STRING` | false | length 64 |
| 3 | `l2_name` | `STRING` | true | length 128 |
| 4 | `l3_code` | `STRING` | false | length 64 |
| 5 | `l3_name` | `STRING` | true | length 128 |
| 6 | `ts_code` | `STRING` | false | length 64 |
| 7 | `name` | `STRING` | true | length 128 |
| 8 | `in_date` | `DATE` | false | — |
| 9 | `out_date` | `DATE` | true | — |
| 10 | `is_new` | `STRING` | true | length 64 |

### 错误边界

- 任一 YAML 的 schema、M02 构造或 M03 语义错误必须使整组 11 文件以 `DATASET_MISCONFIGURED` 失败；不得把部分定义视为成功。
- 文件名/API 重复、表名不匹配、字段顺序错位、业务键/筛选/固定列悬空、参数 related reference 悬空均由现有 loader/M02 契约拒绝。
- 验证 harness 必须逐项比较本设计的 API 集、字段顺序、类型图、参数、业务键、筛选和固定列；只检查“能加载”不足以验收。
- 临时 harness 只存在于 `/private/tmp/M03T02MetadataCheck.java`，不得进入 Git 或仓库源码树。

## Files

只创建：

- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/stock_basic.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/stock_company.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/hs_const.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/trade_cal.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/new_share.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/namechange.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/stk_managers.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/broker_recommend.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/index_classify.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/index_member.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/index_member_all.yaml`

实现提交消息固定为 `feat(metadata): define basic and organization datasets`。提交只暂存上述 11 个文件；任务设计、交接、看板、临时 harness、生成的 `target` 与其他源码不得混入实现提交。

## Tests

### 临时 harness 与 RED

先在 `/private/tmp/M03T02MetadataCheck.java` 创建临时 source-file harness。它必须通过公开 `DatasetDefinitionLoader.loadAll` 加载 `classpath*:datasets/tushare_pro/*.yaml`，硬编码并逐项断言本设计的 11 个 API、93 列类型图、参数、业务键、筛选、固定列和默认 batchSize，成功时只输出：

```text
M03-T02_OK:11
```

先不创建任何运行时 YAML，运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -q \
  -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=DatasetDefinitionLoaderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

m03_t02_cp=$(xmllint --xpath \
  'string(/testsuite/properties/property[@name="java.class.path"]/@value)' \
  data-plane/tensor-plugin-tushare/target/surefire-reports/TEST-com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoaderTest.xml)

java -Dslf4j.internal.verbosity=ERROR --class-path "$m03_t02_cp" \
  /private/tmp/M03T02MetadataCheck.java \
  'classpath*:datasets/tushare_pro/*.yaml'
```

第一条预期 `DatasetDefinitionLoaderTest` 8/8 通过并生成精确测试 classpath；第三条预期因 `<pattern>: no resources matched` 或 11 个 API 缺失而退出非 0，形成只针对本任务缺失 YAML 的 RED。失败不得来自 harness 编译、依赖、schema 或环境错误。

### GREEN 与模块回归

创建 11 份 YAML 后重跑上述 Maven 与 harness 命令。预期：

- `DatasetDefinitionLoaderTest` 8/8 通过；
- harness 只输出 `M03-T02_OK:11` 并退出 0；
- 11 个定义按 `apiName` 排序且不可变；
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
  | rg '^datasets/tushare_pro/(stock_basic|stock_company|hs_const|trade_cal|new_share|namechange|stk_managers|broker_recommend|index_classify|index_member|index_member_all)\.yaml$'
```

预期恰输出 11 行，且没有其他 `datasets/tushare_pro/*.yaml`。最后运行 Maven `clean` 清除未被仓库忽略的 `target`，删除 `/private/tmp/M03T02MetadataCheck.java`，并运行：

```bash
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro
git diff --check
```

提交前第一条精确列出 Files 节 11 个 YAML，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确 11 文件范围，工作树干净。

## Acceptance

- 运行时目录恰新增 11 份基础与组织 YAML；API 名、显示名、查询方式与 PRD 附录 A.1/manifest 投影一致。
- 每份 `columns` 与对应 JSON `fields` 名称和顺序完全一致，合计 93 列；类型、长度、精度、可空性和长文本标记逐项符合本设计。
- 参数集合、顺序、类型、必填性、枚举和 related parameter 精确符合本设计；无参数文件使用空列表。
- 10 个 COMPOSITE 键和 1 个 FINGERPRINT 键与 TRD 9.4 完全一致；filters/fixedColumn 全部引用现有列。
- 临时 harness 经历可归因 RED 后输出 `M03-T02_OK:11` GREEN；loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 11 文件、范围与格式门禁得到预期结果。
- 实现提交精确包含 11 个 YAML，未修改 Java/POM/schema/template/其他模块，未提交临时 harness 或生成物。

## Risks

- 授权模板只提供字段名、参数样例和一行数据，不提供列中文标签或数据库类型。本设计使用用户批准的类型/可空性策略，并把列 label 固定为源字段名；未来若产品要求中文列标签，应由新的设计裁决修改，而不得在实施时猜测。
- `/private/tmp` harness 是本任务的聚焦可执行证据，不替代 M03-T09 的永久 49/49 Java 契约测试；若后续字段基线变化，必须同时更新授权模板/任务设计和 M03-T09 独立基线。
- `xmllint` 用于从 Surefire XML 提取经过实际测试验证的 classpath；缺少该系统工具属于环境阻塞，不授权修改 POM 或提交替代测试代码。
