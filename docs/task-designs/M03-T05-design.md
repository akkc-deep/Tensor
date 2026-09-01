# M03-T05 互联互通与转融通 6 数据集 YAML——任务设计

任务编号：`M03-T05`
对应任务：[M03-T05](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t05-互联互通与转融通-6-数据集25hyaml)
实施产物：`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 6 份互联互通与转融通数据集 YAML

## Goal

为 `moneyflow_hsgt`、`hsgt_top10`、`hk_hold`、`slb_len`、`slb_sec` 和 `slb_sec_detail` 建立唯一运行时元数据。每份 YAML 必须通过 M03-T01 的严格 loader，保持对应 JSON 模板的完整字段名和顺序，采用项目所有者 2026-09-01 批准的 44 列类型、长度和可空性映射，并与 PRD 附录 A.4/A.5 的参数及 TRD 9.4 的复合业务键一致。

本任务完成后，后续模块可通过 `DatasetDefinitionLoader` 读取这 6 个不可变 `DatasetDefinition`。三个 SLB 模板即使没有样例行，也必须形成完整、可加载、可持久化设计的数据集定义，后续模块无需根据空数据猜测字段类型、业务键、筛选或固定列。

## Scope

包含：

- 只创建任务卡指定的 6 份运行时 YAML；
- 固定每份定义的插件/API/表名、分类、显示名、查询方式、参数、字段、业务键、筛选和固定列；
- 按本设计的完整 44 列类型图设置 `logicalType`、`nullable`、`length`、`precision` 和 `scale`；
- 使用 M03-T01 现有公开 loader 和 `/private/tmp` 临时 Java harness 执行缺失 6 文件的 RED、精确契约 GREEN、空模板字段验证、模块回归与 JAR 内容验证；
- 提交精确 6 个 YAML 文件。

排除：

- 不修改 Java、POM、M00 schema/示例、M02 records、M03-T01 loader/test、既有 24 份 YAML、其他模块或 `docs/data-template/`；
- 不创建永久测试类；M03-T09 负责 49/49 永久 Java 契约测试；
- 不读取其他 43 份模板，也不把本任务 6 份模板的完整 `data` 数组载入上下文；字段基线只使用已批准的 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影；
- 不因 `slb_len`、`slb_sec`、`slb_sec_detail` 当前为空而删除文件、字段或接口支持；
- 不新增源字段、审计字段、`business_key`、批次字段或数据库列；持久化附加字段属于 M04/M06；
- 不实现参数值校验、适配、下载、数据库、REST 或前端职责。

## Approach

### 共同结构和命名

全部 YAML 固定：

- `pluginId: tushare_pro`；
- `tableName` 精确由字面前缀 `tushare_pro__` 与文件的 `apiName` 拼接；
- `category: 互联互通与转融通`；
- `queryMode: trade_date`；
- 只声明一个必填 `trade_date` 参数：label 为 `交易日期`、type 为 `DATE`；省略 `description`、`defaultValue`、`allowedValues`、`pattern` 和 `relatedParameter`；
- `displayOrder` 按模板 `fields` 从 0 连续递增；列 `label` 精确等于源字段名；
- `ts_code`、`code` 和 `exchange` 使用 `STRING(64)`，`name` 使用 `STRING(128)`；日期使用 `DATE`；`rank`、`market_type` 和 `tenor` 使用 `LONG`；其他普通数值、金额、数量、余额、比例和费率统一使用 `DECIMAL(38,18)`，包括模板样例中表现为字符串或整数的值；
- 非 `STRING` 列不写 `length`；`DECIMAL` 只写 `precision: 38` 与 `scale: 18`；`DATE` 与 `LONG` 不写长度或精度；所有列省略 `allowedValues` 和 `longText`，由 loader 分别映射为空列表和 `false`；
- 所有定义省略 YAML 中不存在的 `batchSize`，继续采用 M02 默认值 500。

### 数据集总表

| API | displayName | parameters | businessKey | filters | fixedColumn |
|---|---|---|---|---|---|
| `moneyflow_hsgt` | 沪深港通资金流向 | `[trade_date]` | `COMPOSITE: [trade_date]` | `[trade_date]` | `trade_date` |
| `hsgt_top10` | 沪深港通十大成交股 | `[trade_date]` | `COMPOSITE: [trade_date, ts_code, market_type]` | `[ts_code, trade_date]` | `ts_code` |
| `hk_hold` | 沪深港股通持股明细 | `[trade_date]` | `COMPOSITE: [trade_date, code, exchange]` | `[ts_code, trade_date]` | `ts_code` |
| `slb_len` | 转融通期限与规模 | `[trade_date]` | `COMPOSITE: [trade_date, ob]` | `[trade_date]` | `trade_date` |
| `slb_sec` | 转融通证券汇总 | `[trade_date]` | `COMPOSITE: [trade_date, ts_code]` | `[ts_code, trade_date]` | `ts_code` |
| `slb_sec_detail` | 转融通证券明细 | `[trade_date]` | `COMPOSITE: [trade_date, ts_code, tenor, fee_rate]` | `[ts_code, trade_date]` | `ts_code` |

`filters` 只使用实际存在的 `ts_code` 和 `trade_date`，并保持 `[ts_code, trade_date]` 顺序；没有 `ts_code` 的定义只使用 `[trade_date]`。`hk_hold` 必须同时保留源字段 `code` 和 `ts_code`：TRD 业务键精确使用 `code`，筛选与固定列精确使用实际存在的 `ts_code`，不得把二者改名、合并或互换。`fixedColumn` 优先使用 `ts_code`；没有 `ts_code` 时使用首个业务键字段 `trade_date`。

### 可空性规则

- 每个 COMPOSITE 业务键中的全部字段固定 `nullable: false`，包括 `hsgt_top10.market_type`、`hk_hold.code/exchange`、`slb_len.ob` 和 `slb_sec_detail.tenor/fee_rate`；
- 所有非业务键数值和字符串列固定 `nullable: true`，不把上游 null 转为 0 或空字符串；因此 `hk_hold.ts_code` 可空，不能因它是筛选或固定列就改变可空性；
- 参数必填性不改变同名列规则；六份定义的 `trade_date` 同时属于业务键，因此均不可空；
- 不根据三个非空样例中的值扩大必填范围，也不根据三个 SLB 空样例缩减字段或放宽业务键。

### 精确 44 列类型图

下列顺序就是每份 YAML 的 `columns` 顺序；`nullable`、长度和精度必须逐项照抄。

#### `moneyflow_hsgt`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ggt_ss` | `DECIMAL` | true | precision 38, scale 18 |
| 2 | `ggt_sz` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `hgt` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `sgt` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `north_money` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `south_money` | `DECIMAL` | true | precision 38, scale 18 |

#### `hsgt_top10`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ts_code` | `STRING` | false | length 64 |
| 2 | `name` | `STRING` | true | length 128 |
| 3 | `close` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `change` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `rank` | `LONG` | true | — |
| 6 | `market_type` | `LONG` | false | — |
| 7 | `amount` | `DECIMAL` | true | precision 38, scale 18 |
| 8 | `net_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 9 | `buy` | `DECIMAL` | true | precision 38, scale 18 |
| 10 | `sell` | `DECIMAL` | true | precision 38, scale 18 |

#### `hk_hold`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `code` | `STRING` | false | length 64 |
| 1 | `trade_date` | `DATE` | false | — |
| 2 | `ts_code` | `STRING` | true | length 64 |
| 3 | `name` | `STRING` | true | length 128 |
| 4 | `vol` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `ratio` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `exchange` | `STRING` | false | length 64 |

#### `slb_len`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ob` | `DECIMAL` | false | precision 38, scale 18 |
| 2 | `auc_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 3 | `repo_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `repay_amount` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `cb` | `DECIMAL` | true | precision 38, scale 18 |

#### `slb_sec`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ts_code` | `STRING` | false | length 64 |
| 2 | `name` | `STRING` | true | length 128 |
| 3 | `ope_inv` | `DECIMAL` | true | precision 38, scale 18 |
| 4 | `lent_qnt` | `DECIMAL` | true | precision 38, scale 18 |
| 5 | `cls_inv` | `DECIMAL` | true | precision 38, scale 18 |
| 6 | `end_bal` | `DECIMAL` | true | precision 38, scale 18 |

#### `slb_sec_detail`

| # | name | logicalType | nullable | type detail |
|---:|---|---|---|---|
| 0 | `trade_date` | `DATE` | false | — |
| 1 | `ts_code` | `STRING` | false | length 64 |
| 2 | `name` | `STRING` | true | length 128 |
| 3 | `tenor` | `LONG` | false | — |
| 4 | `fee_rate` | `DECIMAL` | false | precision 38, scale 18 |
| 5 | `lent_qnt` | `DECIMAL` | true | precision 38, scale 18 |

### 错误边界

- 任一 YAML 的 schema、M02 构造或 M03 语义错误必须使该文件经公开 loader 以 `DATASET_MISCONFIGURED` 失败；不得把无效定义视为成功；
- 文件名/API 不一致、表名不匹配、字段顺序错位、业务键/筛选/固定列悬空、参数错误、`hk_hold.code/ts_code` 混用或业务键列错误标为可空都必须由 harness 拒绝；
- 验证 harness 必须分别通过 6 个精确 classpath 资源路径调用公开 loader，并逐项比较本设计的 API、44 列类型图、参数、业务键、筛选和固定列；只检查“能加载”不足以验收；
- 三个 SLB JSON 模板的 `data` 为空只用于证明当前没有样例值，不参与运行时加载；harness 必须仍验证三份 YAML 的全部 19 列定义；
- 临时 harness 只存在于 `/private/tmp/M03T05MetadataCheck.java`，不得进入 Git 或仓库源码树。

## Files

只创建：

- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/moneyflow_hsgt.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/hsgt_top10.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/hk_hold.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/slb_len.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/slb_sec.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/slb_sec_detail.yaml`

实现提交消息固定为 `feat(metadata): define connect and SLB datasets`。提交只暂存上述 6 个文件；任务设计、交接、看板、临时 harness、生成的 `target` 与其他源码不得混入实现提交。

## Tests

### 临时 harness、空模板基线与 RED

先在 `/private/tmp/M03T05MetadataCheck.java` 创建临时 source-file harness。它必须对 6 个精确资源路径分别调用公开 `DatasetDefinitionLoader.loadAll`，硬编码并逐项断言本设计的 API、44 列类型图、参数、业务键、筛选、固定列和默认 batchSize，成功时只输出：

```text
M03-T05_OK:6
```

先确认三个 SLB 模板字段完整但没有样例数据：

```bash
jq -e '.data == [] and (.fields | length) == 6' docs/data-template/slb_len.json
jq -e '.data == [] and (.fields | length) == 7' docs/data-template/slb_sec.json
jq -e '.data == [] and (.fields | length) == 6' docs/data-template/slb_sec_detail.json
```

三条命令均预期输出 `true` 并退出 0。随后不创建本任务的运行时 YAML，运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -q \
  -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=DatasetDefinitionLoaderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

m03_t05_cp=$(xmllint --xpath \
  'string(/testsuite/properties/property[@name="java.class.path"]/@value)' \
  data-plane/tensor-plugin-tushare/target/surefire-reports/TEST-com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoaderTest.xml)

java -Dslf4j.internal.verbosity=ERROR --class-path "$m03_t05_cp" \
  /private/tmp/M03T05MetadataCheck.java \
  'classpath*:datasets/tushare_pro/'
```

第一条预期 `DatasetDefinitionLoaderTest` 8/8 通过并生成精确测试 classpath；第三条预期 harness 编译成功后因首个缺失的精确资源得到 `<pattern>: no resources matched` 并退出非 0，形成只针对本任务缺失 YAML 的 RED。失败不得来自 harness 编译、依赖、schema、既有 24 份 YAML或环境错误。

### GREEN 与模块回归

创建 6 份 YAML 后重跑上述 Maven 与 harness 命令。预期：

- `DatasetDefinitionLoaderTest` 8/8 通过；
- harness 只输出 `M03-T05_OK:6` 并退出 0；
- 每个精确资源路径恰加载一个定义；
- 每个定义的字段、类型、参数、键、筛选和固定列与本设计逐项一致；
- 三份 SLB 定义在模板 `data` 为空的情况下仍分别包含 6、7、6 列，合计 19 列；
- `hk_hold` 同时保留 `code` 与 `ts_code`，业务键使用 `code`，筛选与固定列使用 `ts_code`。

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
  | rg '^datasets/tushare_pro/(moneyflow_hsgt|hsgt_top10|hk_hold|slb_len|slb_sec|slb_sec_detail)\.yaml$'
```

预期恰输出 6 行，且运行时源目录合计恰有 30 份 YAML。最后运行 Maven `clean` 清除未被仓库忽略的 `target`，删除 `/private/tmp/M03T05MetadataCheck.java`，并运行：

```bash
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro
git diff --check
```

提交前第一条精确列出 Files 节 6 个 YAML，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确 6 文件范围，工作树干净。

## Acceptance

- 运行时目录恰新增 6 份互联互通与转融通 YAML；API 名、显示名、查询方式与 PRD 附录 A.4/A.5、manifest 投影一致；
- 每份 `columns` 与对应 JSON `fields` 名称和顺序完全一致，合计 44 列；类型、长度、精度与可空性逐项符合本设计；
- 六份定义都只声明必填 `trade_date: DATE` 参数；业务键与 TRD 9.4 完全一致，filters/fixedColumn 全部引用现有列，`hk_hold.code` 与 `hk_hold.ts_code` 的键/筛选职责没有混用；
- 三个 SLB 模板的空 `data` 基线验证通过，三份运行时定义仍完整加载 19 列；临时 harness 经历可归因 RED 后输出 `M03-T05_OK:6` GREEN；
- loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 6 文件、源目录 30 文件、范围与格式门禁得到预期结果；
- 实现提交精确包含 6 个 YAML，未修改 Java/POM/schema/template/既有 YAML/其他模块，未提交临时 harness 或生成物。

## Risks

- 三个 SLB 模板当前没有样例行；项目所有者已批准 `ob/auc_amount/repo_amount/repay_amount/cb/ope_inv/lent_qnt/cls_inv/end_bal/fee_rate` 使用 `DECIMAL(38,18)`、`tenor` 使用 `LONG`。未来真实上游值若不符合该映射，必须通过新的设计裁决及必要迁移处理，不得在适配阶段静默截断或改型；
- `hsgt_top10.market_type`、`hk_hold.code/exchange`、`slb_len.ob` 和 `slb_sec_detail.tenor/fee_rate` 是复合业务键字段，已批准不可空；真实上游若返回 null，后续适配必须明确失败，不得填充占位值或改变业务键；
- `moneyflow_hsgt` 样例以字符串承载数值，后续适配必须按十进制文本严格转换，不得经 `double`；
- `hk_hold` 的 `code` 与 `ts_code` 同时存在但职责不同，后续表结构、适配与查询必须保留两列，不得以名称相似为由合并；
- `/private/tmp` harness 是 M03-T05 的聚焦可执行证据，不替代 M03-T09 的永久 49/49 Java 契约测试。
