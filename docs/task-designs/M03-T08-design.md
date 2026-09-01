# M03-T08 股东与治理 7 数据集 YAML——任务设计

任务编号：`M03-T08`
对应任务：[M03-T08](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t08-股东与治理-7-数据集25hyaml)
实施产物：`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 7 份股东与治理数据集 YAML

## Goal

为 `stk_rewards`、`stk_holdernumber`、`stk_holdertrade`、`top10_holders`、`top10_floatholders`、`pledge_stat` 和 `pledge_detail` 建立唯一运行时元数据。每份 YAML 必须通过 M03-T01 的严格 loader，保持对应 JSON 模板的完整字段名和顺序，采用项目所有者 2026-09-01 批准的 61 列类型、长度和可空性映射，并与 PRD 附录 A.8 的参数及 TRD 9.4 的业务键一致。

本任务完成后，后续模块可通过 `DatasetDefinitionLoader` 读取这 7 个不可变 `DatasetDefinition`。没有样例行的两个 top-10 模板仍必须形成完整元数据；`pledge_detail` 必须以模板全部 14 个字段的原始顺序作为 FINGERPRINT 输入，并保留合法空值。

## Scope

包含：

- 只创建任务卡指定的 7 份运行时 YAML；
- 固定插件/API/表名、分类、显示名、查询方式、参数、字段、业务键、筛选和固定列；
- 按本设计完整且机械可求值的 61 列映射设置 `logicalType`、`nullable`、`length`、`precision` 和 `scale`；
- 使用 M03-T01 现有公开 loader 和 `/private/tmp` 临时 Java harness 执行缺失 7 文件的 RED、精确契约 GREEN、两个空模板验证、模块回归与 JAR 内容验证；
- 提交精确 7 个 YAML 文件。

排除：

- 不修改 Java、POM、M00 schema/示例、M02 records、M03-T01 loader/test、既有 42 份 YAML、其他模块或 `docs/data-template/`；
- 不创建永久测试类；M03-T09 负责 49/49 永久 Java 契约测试；
- 不读取其他 42 份模板，也不把本任务 7 份模板的完整 `data` 数组载入上下文；字段基线只使用 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影；
- 不因 `top10_holders` 或 `top10_floatholders` 当前为空而删除文件、字段或接口支持；
- 不新增源字段、审计字段、`business_key`、批次字段或数据库列；
- 不把 `holder_type`、`in_de`、`is_release` 或 `is_buyback` 收窄为没有批准完整取值集的 `ENUM`；
- 不实现指纹编码、参数值校验、适配、下载、数据库、REST 或前端职责。

## Approach

### 共同结构和命名

全部 YAML 固定：

- `pluginId: tushare_pro`；
- `tableName` 精确由字面前缀 `tushare_pro__` 与文件的 `apiName` 拼接；
- `category: 股东与治理`；
- `displayOrder` 按对应模板 `fields` 从 0 连续递增；列 `label` 精确等于源字段名；
- `STRING` 只写 `length`，`DECIMAL` 只写 `precision: 38` 与 `scale: 18`，`DATE` 和 `LONG` 不写长度或精度；
- 全部列省略 `allowedValues` 和 `longText`，loader 分别映射为空列表和 `false`；
- 省略 YAML 中不存在的 `batchSize`，继续采用 M02 默认值 500。

### 数据集总表

| API | displayName | queryMode | parameters | businessKey | filters | fixedColumn | fields |
|---|---|---|---|---|---|---|---:|
| `stk_rewards` | 管理层薪酬与持股 | `snapshot` | `[{name: ts_code, label: 股票代码, type: TS_CODE, required: true}]` | `COMPOSITE: [ts_code, ann_date, end_date, name]` | `[ts_code, ann_date]` | `ts_code` | 7 |
| `stk_holdernumber` | 股东户数 | `snapshot` | `[{name: ts_code, label: 股票代码, type: TS_CODE, required: true}]` | `COMPOSITE: [ts_code, end_date, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 4 |
| `stk_holdertrade` | 股东增减持 | `ann_date` | `[{name: ann_date, label: 公告日期, type: DATE, required: true}]` | `COMPOSITE: [ts_code, ann_date, holder_name, in_de, change_vol]` | `[ts_code, ann_date]` | `ts_code` | 11 |
| `top10_holders` | 前十大股东 | `ann_date` | `[{name: ann_date, label: 公告日期, type: DATE, required: true}]` | `COMPOSITE: [ts_code, end_date, holder_name, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 9 |
| `top10_floatholders` | 前十大流通股东 | `ann_date` | `[{name: ann_date, label: 公告日期, type: DATE, required: true}]` | `COMPOSITE: [ts_code, end_date, holder_name, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 9 |
| `pledge_stat` | 股权质押统计 | `snapshot` | `[]` | `COMPOSITE: [ts_code, end_date]` | `[ts_code]` | `ts_code` | 7 |
| `pledge_detail` | 股权质押明细 | `snapshot` | `[]` | `FINGERPRINT: [ts_code, ann_date, holder_name, pledge_amount, start_date, end_date, is_release, release_date, pledgor, holding_amount, pledged_amount, p_total_ratio, h_total_ratio, is_buyback]` | `[ts_code, ann_date]` | `ts_code` | 14 |

`stk_rewards` 与 `stk_holdernumber` 只声明必填 `ts_code: TS_CODE`；三个公告日接口只声明必填 `ann_date: DATE`；两个质押接口没有首期参数。所有 filters 只引用实际存在的 `ts_code`/`ann_date`，固定列统一为实际存在的 `ts_code`。

### 精确字段顺序

下列列表是每份 YAML 的完整 `columns` 名称顺序，不得增删或重排：

- `stk_rewards`: `[ts_code, ann_date, end_date, name, title, reward, hold_vol]`；
- `stk_holdernumber`: `[ts_code, ann_date, end_date, holder_num]`；
- `stk_holdertrade`: `[ts_code, ann_date, holder_name, holder_type, in_de, change_vol, change_ratio, after_share, after_ratio, avg_price, total_share]`；
- `top10_holders`: `[ts_code, ann_date, end_date, holder_name, hold_amount, hold_ratio, hold_float_ratio, hold_change, holder_type]`；
- `top10_floatholders`: `[ts_code, ann_date, end_date, holder_name, hold_amount, hold_ratio, hold_float_ratio, hold_change, holder_type]`；
- `pledge_stat`: `[ts_code, end_date, pledge_count, unrest_pledge, rest_pledge, total_share, pledge_ratio]`；
- `pledge_detail`: `[ts_code, ann_date, holder_name, pledge_amount, start_date, end_date, is_release, release_date, pledgor, holding_amount, pledged_amount, p_total_ratio, h_total_ratio, is_buyback]`。

### 完整 61 列类型映射

项目所有者 2026-09-01 批准以下互斥且有优先级的精确字段名规则。对每份模板的每个 `fields` 元素按 1→6 顺序匹配；每列只有一个结果：

1. 精确字段名属于 `{ann_date, end_date, start_date, release_date}`：`logicalType: DATE`，不写长度或精度。
2. 精确字段名属于 `{ts_code, holder_type, in_de, is_release, is_buyback}`：`logicalType: STRING, length: 64`。
3. 精确字段名属于 `{name, title, holder_name, pledgor}`：`logicalType: STRING, length: 128`。
4. 精确字段名属于 `{holder_num, pledge_count}`：`logicalType: LONG`，不写长度或精度。
5. 精确字段名属于 `{reward, hold_vol, change_vol, change_ratio, after_share, after_ratio, avg_price, total_share, hold_amount, hold_ratio, hold_float_ratio, hold_change, unrest_pledge, rest_pledge, pledge_ratio, pledge_amount, holding_amount, pledged_amount, p_total_ratio, h_total_ratio}`：`logicalType: DECIMAL, precision: 38, scale: 18`。
6. 本任务不存在未命中 1～5 的模板字段；harness 遇到未分类字段必须失败，不得建立隐式兜底类型。

按字段出现次数统计，61 列恰为 14 个 `DATE`、13 个 `STRING(64)`、7 个 `STRING(128)`、2 个 `LONG` 和 25 个 `DECIMAL(38,18)`。本任务没有 `TEXT`、`MONTH` 或列级 `ENUM`；不得根据单个样例值改变批准类型，也不得经 `double`。

### 完整 61 列可空性映射

- 对六个 `COMPOSITE` 数据集，字段名属于该 API 的 `businessKey.fields` 时固定 `nullable: false`，其余列固定 `nullable: true`；
- `pledge_detail` 的全部 14 列固定 `nullable: true`。其 FINGERPRINT 输入仍精确包含模板全部字段并保留模板顺序，后续规范化编码负责显式空值标记；
- 参数必填性、筛选和固定列身份不扩大不可空集合。

### 错误边界

- 任一 YAML 的 schema、M02 构造或 M03 语义错误必须使该文件经公开 loader 以 `DATASET_MISCONFIGURED` 失败；不得把无效定义视为成功；
- 文件名/API 不一致、表名不匹配、字段缺失/重复/错序、未分类字段、类型规则偏离、COMPOSITE 键列标为可空、FINGERPRINT 字段缺失/错序、参数错误、业务键/筛选/固定列悬空都必须由临时 harness 拒绝；
- harness 必须通过 7 个精确 classpath 资源路径分别调用公开 loader，并逐项比较 API、61 个字段名/顺序/类型/可空性、参数、业务键、筛选、固定列和默认 batchSize；只检查“能加载”或字段计数不足以验收；
- 两个 top-10 空模板只证明当前没有样例值，不参与运行时加载；harness 仍须分别验证完整 9 列；
- 临时 harness 只存在于 `/private/tmp/M03T08MetadataCheck.java`，不得进入 Git 或仓库源码树。

## Files

只创建：

- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/stk_rewards.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/stk_holdernumber.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/stk_holdertrade.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/top10_holders.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/top10_floatholders.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/pledge_stat.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/pledge_detail.yaml`

实现提交消息固定为 `feat(metadata): define shareholder governance datasets`。提交只暂存上述 7 个文件；任务设计、交接、看板、临时 harness、生成的 `target` 与其他源码不得混入实现提交。

## Tests

### 临时 harness、空模板基线与 RED

先在 `/private/tmp/M03T08MetadataCheck.java` 创建临时 source-file harness。它必须对 7 个精确资源路径分别调用公开 `DatasetDefinitionLoader.loadAll`，硬编码七份模板的 61 个字段名/顺序、数据集总表、类型字段集合、可空性和业务键，并逐项断言本设计的完整契约。成功时只输出：

```text
M03-T08_OK:7:61
```

先确认两个空模板字段完整但没有样例数据：

```bash
jq -e '.data == [] and (.fields | length) == 9' docs/data-template/top10_holders.json
jq -e '.data == [] and (.fields | length) == 9' docs/data-template/top10_floatholders.json
```

两条命令均预期输出 `true` 并退出 0。随后在不创建本任务运行时 YAML 的状态运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -q \
  -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=DatasetDefinitionLoaderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

m03_t08_cp=$(xmllint --xpath \
  'string(/testsuite/properties/property[@name="java.class.path"]/@value)' \
  data-plane/tensor-plugin-tushare/target/surefire-reports/TEST-com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoaderTest.xml)

java -Dslf4j.internal.verbosity=ERROR --class-path "$m03_t08_cp" \
  /private/tmp/M03T08MetadataCheck.java \
  'classpath*:datasets/tushare_pro/'
```

Maven 命令预期 `DatasetDefinitionLoaderTest` 8/8 通过并生成精确测试 classpath；Java 命令预期 harness 编译成功后因首个缺失的精确资源得到 `<pattern>: no resources matched` 并退出非 0，形成只针对本任务缺失 YAML 的 RED。失败不得来自 harness 编译、依赖、schema、既有 42 份 YAML 或环境错误。

### GREEN 与回归

创建 7 份 YAML 后重跑上述 Maven 与 harness 命令。预期：

- `DatasetDefinitionLoaderTest` 8/8 通过；
- harness 只输出 `M03-T08_OK:7:61` 并退出 0；
- 每个精确资源路径恰加载一个定义；字段计数依次为 7、4、11、9、9、7、14，总计 61；
- 每列名称/顺序、类型、可空性和适用属性由完整映射唯一得到；七份参数、键、筛选、固定列和默认 batchSize 与数据集总表一致；
- 两个空 top-10 模板的 9 列运行时定义仍完整加载；`pledge_detail` 的 FINGERPRINT 精确包含全部 14 个字段且顺序不变。

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
  | rg '^datasets/tushare_pro/(stk_rewards|stk_holdernumber|stk_holdertrade|top10_holders|top10_floatholders|pledge_stat|pledge_detail)\.yaml$'
```

预期恰输出 7 行，且运行时源目录合计恰有 49 份 YAML。最后运行 Maven `clean` 清除未被仓库忽略的 `target`，删除 `/private/tmp/M03T08MetadataCheck.java`，并运行：

```bash
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro
git diff --check
```

提交前第一条精确列出 Files 节 7 个 YAML，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确 7 文件范围，工作树干净。

## Acceptance

- 运行时目录恰新增 7 份股东与治理 YAML；API 名、显示名、查询方式与 PRD 附录 A.8、manifest 投影一致；
- 每份 `columns` 与对应 JSON `fields` 名称和顺序完全一致，字段计数依次为 7、4、11、9、9、7、14，合计 61；全部类型、长度、精度和可空性可由本设计规则唯一求得；
- 参数集合与 PRD A.8 完全一致；六个 COMPOSITE 键与 TRD 9.4 一致，`pledge_detail` 以模板全部 14 个字段原序构成 FINGERPRINT；filters/fixedColumn 全部引用现有列；
- 两个空 top-10 模板断言通过，其运行时定义仍各完整加载 9 列；临时 harness 经历可归因 RED 后输出 `M03-T08_OK:7:61` GREEN；
- loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 7 文件、源目录 49 文件、范围与格式门禁得到预期结果；
- 实现提交精确包含 7 个 YAML，未修改 Java/POM/schema/template/既有 YAML/其他模块，未提交临时 harness 或生成物。

## Risks

- `top10_holders` 与 `top10_floatholders` 当前没有样例行；项目所有者已批准本设计的精确字段名映射和“COMPOSITE 键不可空、其余可空”规则。未来真实上游值若不符合该映射，必须通过新的设计裁决及必要迁移处理，不得静默截断、填充或改型；
- `holder_type`、`in_de`、`is_release` 和 `is_buyback` 采用 `STRING(64)`，`name`、`title`、`holder_name` 和 `pledgor` 采用 `STRING(128)`，不建立没有完整取值集的 `ENUM`；未来出现超长值或闭集需求时必须另行设计；
- `holder_num` 与 `pledge_count` 采用 `LONG`。上游若返回小数、溢出或非整数文本，后续适配必须显式失败，不得截断或取整；
- `pledge_detail` 的 14 个 FINGERPRINT 输入全部允许空值；后续指纹编码必须采用 TRD 规定的字段顺序、UTF-8、长度前缀和显式空值标记，不得跳过空字段或改变顺序；
- 本任务所有数值列必须由后续适配严格转换为 `LONG` 或十进制，不得经 `double`。
