# M03-T04 交易与资金 6 数据集 YAML——任务设计

任务编号：`M03-T04`
对应任务：[M03-T04](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t04-交易与资金-6-数据集25hyaml)
实施产物：`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 6 份交易与资金数据集 YAML

## Goal

为 `moneyflow`、`margin`、`margin_detail`、`top_list`、`top_inst` 和 `block_trade` 建立唯一运行时元数据。每份 YAML 必须通过 M03-T01 的严格 loader，保持对应 JSON 模板的完整字段名和顺序，采用项目所有者 2026-09-01 批准的 71 列类型、长度和可空性映射，并与 PRD 附录 A.3 的参数及 TRD 9.4 的复合业务键一致。

本任务完成后，后续模块可通过 `DatasetDefinitionLoader` 读取这 6 个不可变 `DatasetDefinition`，无需重新解释交易金额、融资融券指标、龙虎榜身份字段、大宗交易身份字段、参数、业务键、筛选或固定列。

## Scope

包含：

- 只创建任务卡指定的 6 份运行时 YAML；
- 固定每份定义的插件/API/表名、分类、显示名、查询方式、参数、字段、业务键、筛选和固定列；
- 按本设计的完整 71 列类型图设置 `logicalType`、`nullable`、`length`、`precision` 和 `scale`；
- 使用 M03-T01 现有公开 loader 和 `/private/tmp` 临时 Java harness 执行缺失 6 文件的 RED、精确契约 GREEN、模块回归与 JAR 内容验证；
- 提交精确 6 个 YAML 文件。

排除：

- 不修改 Java、POM、M00 schema/示例、M02 records、M03-T01 loader/test、既有 18 份 YAML、其他模块或 `docs/data-template/`；
- 不创建永久测试类；M03-T09 负责 49/49 永久 Java 契约测试；
- 不读取其他 43 份模板，也不把本任务 6 份模板的完整 `data` 数组载入上下文；字段基线只使用已批准的 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影；
- 不新增源字段、审计字段、`business_key`、批次字段或数据库列；持久化附加字段属于 M04/M06；
- 不实现参数值校验、适配、下载、数据库、REST 或前端职责。

## Approach

### 共同结构和命名

全部 YAML 固定：

- `pluginId: tushare_pro`；
- `tableName` 精确由字面前缀 `tushare_pro__` 与文件的 `apiName` 拼接；
- `category: 交易与资金`；
- `queryMode: trade_date`；
- `trade_date` 参数 label 为 `交易日期`、type 为 `DATE`、必填；`margin` 在其前声明必填 `exchange_id`，label 为 `交易所`、type 为 `ENUM`、`allowedValues: [SSE, SZSE, BSE]`；所有参数省略 `description`、`defaultValue`、`pattern` 和 `relatedParameter`，非 ENUM 参数还省略 `allowedValues`；
- `displayOrder` 按模板 `fields` 从 0 连续递增；列 `label` 精确等于源字段名；
- `ts_code` 与 `exchange_id` 使用 `STRING(64)`，`side` 使用 `STRING(64)`，`name` 使用 `STRING(128)`，`exalter`、`reason`、`buyer` 和 `seller` 使用 `STRING(255)`；日期使用 `DATE`，所有数值列统一使用 `DECIMAL(38,18)`，包括样例为整数的成交量和融资融券数量；
- 非 `STRING` 列不写 `length`，`DECIMAL` 只写 `precision: 38` 与 `scale: 18`；所有列省略 `allowedValues` 和 `longText`，由 loader 分别映射为空列表和 `false`；
- 所有定义省略 YAML 中不存在的 `batchSize`，继续采用 M02 默认值 500。

### 数据集总表

| API | displayName | parameters（声明顺序） | businessKey | filters | fixedColumn |
|---|---|---|---|---|---|
| `moneyflow` | 个股资金流向 | `[trade_date]` | `COMPOSITE: [ts_code, trade_date]` | `[ts_code, trade_date]` | `ts_code` |
| `margin` | 融资融券汇总 | `[exchange_id, trade_date]` | `COMPOSITE: [trade_date, exchange_id]` | `[trade_date]` | `trade_date` |
| `margin_detail` | 融资融券交易明细 | `[trade_date]` | `COMPOSITE: [trade_date, ts_code]` | `[ts_code, trade_date]` | `ts_code` |
| `top_list` | 龙虎榜每日明细 | `[trade_date]` | `COMPOSITE: [trade_date, ts_code, reason]` | `[ts_code, trade_date]` | `ts_code` |
| `top_inst` | 龙虎榜机构明细 | `[trade_date]` | `COMPOSITE: [trade_date, ts_code, exalter, side, reason, net_buy]` | `[ts_code, trade_date]` | `ts_code` |
| `block_trade` | 大宗交易 | `[trade_date]` | `COMPOSITE: [trade_date, ts_code, buyer, seller, price, vol]` | `[ts_code, trade_date]` | `ts_code` |

`filters` 只使用实际存在的 `ts_code` 和 `trade_date`，并保持 `[ts_code, trade_date]` 顺序；没有 `ts_code` 的 `margin` 只使用 `[trade_date]`。`fixedColumn` 优先使用 `ts_code`，`margin` 使用首个业务键字段 `trade_date`。字段顺序、业务键顺序和筛选展示顺序彼此独立，均不得互相重排。

### 可空性规则

- 每个 COMPOSITE 业务键中的全部字段固定 `nullable: false`，包括 `top_list.reason`，`top_inst.exalter/side/reason/net_buy` 以及 `block_trade.buyer/seller/price/vol`；
- 所有非业务键数值和字符串列固定 `nullable: true`，不把上游 null 转为 0 或空字符串；
- 参数必填性不改变同名列规则；`margin.exchange_id` 因同时属于业务键而不可空；
- 不根据单个样例中的非空值扩大必填范围。

### 精确 71 列类型图

下列顺序就是每份 YAML 的 `columns` 顺序；`nullable`、长度和精度必须逐项照抄。

#### `moneyflow`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `buy_sm_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `buy_sm_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `sell_sm_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `sell_sm_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `buy_md_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `buy_md_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `sell_md_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `sell_md_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 10 | `buy_lg_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 11 | `buy_lg_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 12 | `sell_lg_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 13 | `sell_lg_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 14 | `buy_elg_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 15 | `buy_elg_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 16 | `sell_elg_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 17 | `sell_elg_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 18 | `net_mf_vol` | `DECIMAL` | true | precision 38, scale 18 |
| 19 | `net_mf_amount` | `DECIMAL` | true | precision 38, scale 18 |

#### `margin`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `exchange_id` | `STRING` | false | length 64 |
| 2 | `rzye` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `rzmre` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `rzche` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `rqye` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `rqmcl` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `rzrqye` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `rqyl` | `DECIMAL` | true | precision 38, scale 18 |

#### `margin_detail`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ts_code` | `STRING` | false | length 64 |
| 2 | `rzye` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `rqye` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `rzmre` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `rqyl` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `rzche` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `rqchl` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `rqmcl` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `rzrqye` | `DECIMAL` | true | precision 38, scale 18 |

#### `top_list`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ts_code` | `STRING` | false | length 64 |
| 2 | `name` | `STRING` | true | length 128 |
| 3 | `close` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `pct_change` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `turnover_rate` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `amount` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `l_sell` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `l_buy` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `l_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 10 | `net_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 11 | `net_rate` | `DECIMAL` | true | precision 38, scale 18 |
| 12 | `amount_rate` | `DECIMAL` | true | precision 38, scale 18 |
| 13 | `float_values` | `DECIMAL` | true | precision 38, scale 18 |
| 14 | `reason` | `STRING` | false | length 255 |

#### `top_inst`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ts_code` | `STRING` | false | length 64 |
| 2 | `exalter` | `STRING` | false | length 255 |
| 3 | `buy` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `buy_rate` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `sell` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `sell_rate` | `DECIMAL` | true | precision 38, scale 18 |
| 7 | `net_buy` | `DECIMAL` | false | precision 38, scale 18 |
| 8 | `side` | `STRING` | false | length 64 |
| 9 | `reason` | `STRING` | false | length 255 |

#### `block_trade`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `ts_code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `price` | `DECIMAL` | false | precision 38, scale 18 |
| 3 | `vol` | `DECIMAL` | false | precision 38, scale 18 |
| 4 | `amount` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `buyer` | `STRING` | false | length 255 |
| 6 | `seller` | `STRING` | false | length 255 |

### 错误边界

- 任一 YAML 的 schema、M02 构造或 M03 语义错误必须使该文件经公开 loader 以 `DATASET_MISCONFIGURED` 失败；不得把无效定义视为成功；
- 文件名/API 不一致、表名不匹配、字段顺序错位、业务键/筛选/固定列悬空、参数顺序/枚举错误或业务键列错误标为可空都必须由 harness 拒绝；
- 验证 harness 必须分别通过 6 个精确 classpath 资源路径调用公开 loader，并逐项比较本设计的 API、71 列类型图、参数、业务键、筛选和固定列；只检查“能加载”不足以验收；
- 临时 harness 只存在于 `/private/tmp/M03T04MetadataCheck.java`，不得进入 Git 或仓库源码树。

## Files

只创建：

- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/moneyflow.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/margin.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/margin_detail.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/top_list.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/top_inst.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/block_trade.yaml`

实现提交消息固定为 `feat(metadata): define trading and funding datasets`。提交只暂存上述 6 个文件；任务设计、交接、看板、临时 harness、生成的 `target` 与其他源码不得混入实现提交。

## Tests

### 临时 harness 与 RED

先在 `/private/tmp/M03T04MetadataCheck.java` 创建临时 source-file harness。它必须对 6 个精确资源路径分别调用公开 `DatasetDefinitionLoader.loadAll`，硬编码并逐项断言本设计的 API、71 列类型图、参数、业务键、筛选、固定列和默认 batchSize，成功时只输出：

```text
M03-T04_OK:6
```

先不创建本任务的运行时 YAML，运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -q \
  -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=DatasetDefinitionLoaderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

m03_t04_cp=$(xmllint --xpath \
  'string(/testsuite/properties/property[@name="java.class.path"]/@value)' \
  data-plane/tensor-plugin-tushare/target/surefire-reports/TEST-com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoaderTest.xml)

java -Dslf4j.internal.verbosity=ERROR --class-path "$m03_t04_cp" \
  /private/tmp/M03T04MetadataCheck.java \
  'classpath*:datasets/tushare_pro/'
```

第一条预期 `DatasetDefinitionLoaderTest` 8/8 通过并生成精确测试 classpath；第三条预期 harness 编译成功后因首个缺失的精确资源得到 `<pattern>: no resources matched` 并退出非 0，形成只针对本任务缺失 YAML 的 RED。失败不得来自 harness 编译、依赖、schema、既有 18 份 YAML或环境错误。

### GREEN 与模块回归

创建 6 份 YAML 后重跑上述 Maven 与 harness 命令。预期：

- `DatasetDefinitionLoaderTest` 8/8 通过；
- harness 只输出 `M03-T04_OK:6` 并退出 0；
- 每个精确资源路径恰加载一个定义；
- 每个定义的字段、类型、参数、键、筛选和固定列与本设计逐项一致；
- `margin` 的 `exchange_id` 参数枚举精确为 `[SSE, SZSE, BSE]`。

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
  | rg '^datasets/tushare_pro/(moneyflow|margin|margin_detail|top_list|top_inst|block_trade)\.yaml$'
```

预期恰输出 6 行，且运行时源目录合计恰有 24 份 YAML。最后运行 Maven `clean` 清除未被仓库忽略的 `target`，删除 `/private/tmp/M03T04MetadataCheck.java`，并运行：

```bash
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro
git diff --check
```

提交前第一条精确列出 Files 节 6 个 YAML，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确 6 文件范围，工作树干净。

## Acceptance

- 运行时目录恰新增 6 份交易与资金 YAML；API 名、显示名、查询方式与 PRD 附录 A.3/manifest 投影一致；
- 每份 `columns` 与对应 JSON `fields` 名称和顺序完全一致，合计 71 列；类型、长度、精度与可空性逐项符合本设计；
- `margin` 按序声明必填 `exchange_id: ENUM[SSE,SZSE,BSE]` 和 `trade_date: DATE`，其余定义只声明必填 `trade_date: DATE`；业务键与 TRD 9.4 完全一致，filters/fixedColumn 全部引用现有列；
- 临时 harness 经历可归因 RED 后输出 `M03-T04_OK:6` GREEN；loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 6 文件、源目录 24 文件、范围与格式门禁得到预期结果；
- 实现提交精确包含 6 个 YAML，未修改 Java/POM/schema/template/既有 YAML/其他模块，未提交临时 harness 或生成物。

## Risks

- `top_list.reason`、`top_inst.exalter/side/reason/net_buy` 和 `block_trade.buyer/seller/price/vol` 是 TRD 复合业务键字段；项目所有者已批准它们不可空。若真实上游未来返回 null，适配阶段必须明确失败，而不能填充占位值或改变业务键；
- 样例中的部分成交量和融资融券数量表现为整数；项目所有者已批准所有数值列统一使用 `DECIMAL(38,18)`，实施不得据此改为 `LONG` 或经 `double` 转换；
- `buyer`、`seller`、`reason` 和 `exalter` 已批准为 `STRING(255)` 而非 `TEXT`；未来若上游值超长，应通过新的设计裁决和迁移处理，不得在本任务中静默截断；
- `/private/tmp` harness 是本任务的聚焦可执行证据，不替代 M03-T09 的永久 49/49 Java 契约测试。
