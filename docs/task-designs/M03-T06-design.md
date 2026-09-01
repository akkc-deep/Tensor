# M03-T06 财务与披露 9 数据集 YAML——任务设计

任务编号：`M03-T06`  
对应任务：[M03-T06](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t06-财务与披露-9-数据集40hyaml)  
实施产物：`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 9 份财务与披露数据集 YAML

## Goal

为 `income`、`balancesheet`、`cashflow`、`fina_indicator`、`fina_audit`、`fina_mainbz`、`express`、`forecast` 和 `disclosure_date` 建立唯一运行时元数据。每份 YAML 必须通过 M03-T01 的严格 loader，保持对应 JSON 模板的完整字段名和顺序，采用项目所有者 2026-09-01 批准的 490 列类型、长度和可空性映射，并与 PRD 附录 A.6 的参数及 TRD 9.4 的复合业务键一致。

本任务完成后，后续模块可通过 `DatasetDefinitionLoader` 读取这 9 个不可变 `DatasetDefinition`。五个空模板也必须形成完整、可加载、可持久化设计的数据集定义，后续模块无需根据缺失样例猜测字段类型、业务键、筛选或固定列。

## Scope

包含：

- 只创建任务卡指定的 9 份运行时 YAML；
- 固定每份定义的插件/API/表名、分类、显示名、查询方式、参数、字段、业务键、筛选和固定列；
- 按本设计的完整且机械可求值的 490 列映射设置 `logicalType`、`nullable`、`length`、`precision`、`scale` 和 `longText`；
- 使用 M03-T01 现有公开 loader 和 `/private/tmp` 临时 Java harness 执行缺失 9 文件的 RED、精确契约 GREEN、空模板验证、模块回归与 JAR 内容验证；
- 提交精确 9 个 YAML 文件。

排除：

- 不修改 Java、POM、M00 schema/示例、M02 records、M03-T01 loader/test、既有 30 份 YAML、其他模块或 `docs/data-template/`；
- 不创建永久测试类；M03-T09 负责 49/49 永久 Java 契约测试；
- 不读取其他 40 份模板，也不把本任务 9 份模板的完整 `data` 数组载入上下文；字段基线只使用已批准的 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影；
- 不因 `income`、`balancesheet`、`cashflow`、`fina_indicator` 和 `fina_audit` 当前为空而删除文件、字段或接口支持；
- 不新增源字段、审计字段、`business_key`、批次字段或数据库列；特别是不得为 `fina_mainbz` 新增模板中不存在的 `ann_date` 列；
- 不实现参数值校验、适配、下载、数据库、REST 或前端职责。

## Approach

### 共同结构和命名

全部 YAML 固定：

- `pluginId: tushare_pro`；
- `tableName` 精确由字面前缀 `tushare_pro__` 与文件的 `apiName` 拼接；
- `category: 财务与披露`；
- `queryMode: ann_date`；
- `displayOrder` 按对应模板 `fields` 从 0 连续递增；列 `label` 精确等于源字段名；
- `STRING` 只写 `length`，`DECIMAL` 只写 `precision: 38` 与 `scale: 18`，`DATE` 与 `TEXT` 不写长度或精度；
- `TEXT` 列显式写 `longText: true`；其他列省略 `longText`。全部列省略 `allowedValues`；loader 分别把缺省值映射为 `false` 和空列表；
- 所有定义省略 YAML 中不存在的 `batchSize`，继续采用 M02 默认值 500。

### 数据集总表

| API | displayName | parameters（声明顺序） | businessKey | filters | fixedColumn | fields |
|---|---|---|---|---|---|---:|
| `income` | 利润表 | `[ts_code, ann_date]` | `COMPOSITE: [ts_code, end_date, report_type, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 85 |
| `balancesheet` | 资产负债表 | `[ts_code, ann_date]` | `COMPOSITE: [ts_code, end_date, report_type, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 152 |
| `cashflow` | 现金流量表 | `[ts_code, ann_date]` | `COMPOSITE: [ts_code, end_date, report_type, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 97 |
| `fina_indicator` | 财务指标 | `[ts_code, ann_date]` | `COMPOSITE: [ts_code, end_date, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 108 |
| `fina_audit` | 财务审计意见 | `[ts_code, ann_date]` | `COMPOSITE: [ts_code, end_date, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 7 |
| `fina_mainbz` | 主营业务构成 | `[ts_code, ann_date]` | `COMPOSITE: [ts_code, end_date, bz_item, curr_type]` | `[ts_code]` | `ts_code` | 8 |
| `express` | 业绩快报 | `[ann_date]` | `COMPOSITE: [ts_code, end_date, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 15 |
| `forecast` | 业绩预告 | `[ann_date]` | `COMPOSITE: [ts_code, end_date, ann_date, type]` | `[ts_code, ann_date]` | `ts_code` | 13 |
| `disclosure_date` | 财报披露计划 | `[ann_date]` | `COMPOSITE: [ts_code, end_date]` | `[ts_code, ann_date]` | `ts_code` | 5 |

`filters` 只使用实际存在的 `ts_code` 和 `ann_date`，并保持 `[ts_code, ann_date]` 顺序。`fina_mainbz` 的 PRD 参数包含 `ann_date`，但模板字段中没有 `ann_date`，所以它只声明过滤字段 `[ts_code]`；不得为满足参数名而发明源列。九份定义都以实际存在的 `ts_code` 为 `fixedColumn`。

### 参数定义

全部参数固定 `required: true`，省略 `description`、`defaultValue`、`allowedValues`、`pattern` 和 `relatedParameter`。相同参数在所有文件中使用同一描述符：

| name | label | type |
|---|---|---|
| `ts_code` | 股票代码 | `TS_CODE` |
| `ann_date` | 公告日期 | `DATE` |

`income`、`balancesheet`、`cashflow`、`fina_indicator`、`fina_audit` 和 `fina_mainbz` 按 `[ts_code, ann_date]` 顺序声明参数；`express`、`forecast` 和 `disclosure_date` 只声明 `[ann_date]`。

### 完整 490 列类型映射

项目所有者 2026-09-01 批准以下互斥且有优先级的精确字段名规则。对每份模板的每个 `fields` 元素按 1→5 顺序匹配；精确字段名集合彼此不重叠，因此每列只有一个结果：

1. 精确字段名属于 `{ann_date, f_ann_date, end_date, first_ann_date, pre_date, actual_date}`：`logicalType: DATE`，不写长度或精度。
2. 精确字段名属于 `{ts_code, report_type, comp_type, end_type, update_flag, type, bz_code, curr_type}`：`logicalType: STRING, length: 64`。
3. 精确字段名属于 `{audit_result, audit_agency, audit_sign, bz_item}`：`logicalType: STRING, length: 255`。
4. 精确字段名属于 `{perf_summary, summary, change_reason}`：`logicalType: TEXT, longText: true`，不写长度或精度。
5. 本任务九份模板中所有未命中 1～4 的字段：`logicalType: DECIMAL, precision: 38, scale: 18`。

本任务没有 `LONG`、`MONTH` 或列级 `ENUM`。规则 5 包含所有普通财务金额、余额、比率、每股指标、周转天数、审计费用和数量字段；不得根据样例中的整数/小数表现改用 `LONG` 或浮点类型，也不得经 `double`。

完整性基线固定为对应模板的 `fields` 数组，顺序和计数分别为 85、152、97、108、7、8、15、13、5，合计 490。实现者必须逐项复制字段名；类型通过上述规则机械计算，不得另行解释字段语义或建立未批准的例外。

### 完整 490 列可空性映射

每份定义先使用数据集总表中的精确 `businessKey.fields` 集合：

- 字段名属于该 API 的 `businessKey.fields`：固定 `nullable: false`；
- 该 API 的所有其他字段：固定 `nullable: true`。

因此 `income`、`balancesheet` 和 `cashflow` 的 `ts_code/end_date/report_type/ann_date` 不可空；`fina_indicator`、`fina_audit` 和 `express` 的 `ts_code/end_date/ann_date` 不可空；`fina_mainbz` 的 `ts_code/end_date/bz_item/curr_type` 不可空；`forecast` 的 `ts_code/end_date/ann_date/type` 不可空；`disclosure_date` 只有 `ts_code/end_date` 不可空。参数必填性、筛选和固定列身份不扩大不可空集合；例如 `disclosure_date.ann_date` 可空。

### 错误边界

- 任一 YAML 的 schema、M02 构造或 M03 语义错误必须使该文件经公开 loader 以 `DATASET_MISCONFIGURED` 失败；不得把无效定义视为成功；
- 文件名/API 不一致、表名不匹配、字段缺失/重复/错序、类型规则偏离、业务键列标为可空、参数错误、业务键/筛选/固定列悬空都必须由临时 harness 拒绝；
- harness 必须分别通过 9 个精确 classpath 资源路径调用公开 loader，并逐项比较 API、490 个字段名/顺序/类型/可空性、参数、业务键、筛选、固定列和默认 batchSize；只检查“能加载”或字段计数不足以验收；
- 五个空模板只证明当前没有样例值，不参与运行时加载；harness 仍须验证它们的全部 449 列；
- 临时 harness 只存在于 `/private/tmp/M03T06MetadataCheck.java`，不得进入 Git 或仓库源码树。

## Files

只创建：

- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/income.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/balancesheet.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/cashflow.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/fina_indicator.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/fina_audit.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/fina_mainbz.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/express.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/forecast.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/disclosure_date.yaml`

实现提交消息固定为 `feat(metadata): define financial disclosure datasets`。提交只暂存上述 9 个文件；任务设计、交接、看板、临时 harness、生成的 `target` 与其他源码不得混入实现提交。

## Tests

### 临时 harness、空模板基线与 RED

先在 `/private/tmp/M03T06MetadataCheck.java` 创建临时 source-file harness。它必须对 9 个精确资源路径分别调用公开 `DatasetDefinitionLoader.loadAll`，硬编码九份模板的 490 个字段名/顺序、数据集总表、类型字段集合和业务键集合，并逐项断言本设计的完整契约。成功时只输出：

```text
M03-T06_OK:9:490
```

先确认五个空模板字段完整但没有样例数据：

```bash
jq -e '.data == [] and (.fields | length) == 85' docs/data-template/income.json
jq -e '.data == [] and (.fields | length) == 152' docs/data-template/balancesheet.json
jq -e '.data == [] and (.fields | length) == 97' docs/data-template/cashflow.json
jq -e '.data == [] and (.fields | length) == 108' docs/data-template/fina_indicator.json
jq -e '.data == [] and (.fields | length) == 7' docs/data-template/fina_audit.json
```

五条命令均预期输出 `true` 并退出 0。随后在不创建本任务运行时 YAML 的状态运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -q \
  -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=DatasetDefinitionLoaderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

m03_t06_cp=$(xmllint --xpath \
  'string(/testsuite/properties/property[@name="java.class.path"]/@value)' \
  data-plane/tensor-plugin-tushare/target/surefire-reports/TEST-com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoaderTest.xml)

java -Dslf4j.internal.verbosity=ERROR --class-path "$m03_t06_cp" \
  /private/tmp/M03T06MetadataCheck.java \
  'classpath*:datasets/tushare_pro/'
```

第一条预期 `DatasetDefinitionLoaderTest` 8/8 通过并生成精确测试 classpath；第三条预期 harness 编译成功后因首个缺失的精确资源得到 `<pattern>: no resources matched` 并退出非 0，形成只针对本任务缺失 YAML 的 RED。失败不得来自 harness 编译、依赖、schema、既有 30 份 YAML 或环境错误。

### GREEN 与回归

创建 9 份 YAML 后重跑上述 Maven 与 harness 命令。预期：

- `DatasetDefinitionLoaderTest` 8/8 通过；
- harness 只输出 `M03-T06_OK:9:490` 并退出 0；
- 每个精确资源路径恰加载一个定义；字段计数依次为 85、152、97、108、7、8、15、13、5，总计 490；
- 每列名称/顺序、类型、可空性和适用属性由完整映射唯一得到；九份参数、键、筛选、固定列和默认 batchSize 与数据集总表一致；
- 五份空模板的 449 列在没有样例行时仍完整加载；`fina_mainbz` 没有新增 `ann_date` 列但仍声明 PRD 要求的 `ann_date` 参数。

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
  | rg '^datasets/tushare_pro/(income|balancesheet|cashflow|fina_indicator|fina_audit|fina_mainbz|express|forecast|disclosure_date)\.yaml$'
```

预期恰输出 9 行，且运行时源目录合计恰有 39 份 YAML。最后运行 Maven `clean` 清除未被仓库忽略的 `target`，删除 `/private/tmp/M03T06MetadataCheck.java`，并运行：

```bash
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro
git diff --check
```

提交前第一条精确列出 Files 节 9 个 YAML，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确 9 文件范围，工作树干净。

## Acceptance

- 运行时目录恰新增 9 份财务与披露 YAML；API 名、显示名、查询方式与 PRD 附录 A.6、manifest 投影一致；
- 每份 `columns` 与对应 JSON `fields` 名称和顺序完全一致，字段计数分别为 85、152、97、108、7、8、15、13、5，合计 490；全部类型、长度、精度、`longText` 和可空性可由本设计规则唯一求得；
- 前六份定义按顺序声明必填 `ts_code: TS_CODE` 与 `ann_date: DATE`，后三份只声明必填 `ann_date: DATE`；业务键与 TRD 9.4 完全一致，filters/fixedColumn 全部引用现有列；
- 五个空模板断言通过，其 449 列运行时定义仍完整加载；临时 harness 经历可归因 RED 后输出 `M03-T06_OK:9:490` GREEN；
- loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 9 文件、源目录 39 文件、范围与格式门禁得到预期结果；
- 实现提交精确包含 9 个 YAML，未修改 Java/POM/schema/template/既有 YAML/其他模块，未提交临时 harness 或生成物。

## Risks

- 五个模板共 449 列当前没有样例行；项目所有者已批准本设计的精确字段名映射和“业务键不可空、其余可空”规则。未来真实上游值若不符合该映射，必须通过新的设计裁决及必要迁移处理，不得在适配阶段静默截断、填充或改型；
- 九份宽定义合计 490 列，机械复制时容易遗漏或错序；临时 harness 必须逐项比较硬编码字段顺序和完整契约，不能只比较计数；
- `fina_mainbz` 接受 `ann_date` 查询参数但模板没有同名列；实现必须保留该参数/列差异，不得新增源列或把参数从 PRD 契约中删除；
- `perf_summary`、`summary` 和 `change_reason` 采用 `TEXT` 且 `longText: true`；后续持久化和 API 展示必须保留长文本，不得降为定长短字符串；
- 本任务所有数值列使用 `DECIMAL(38,18)`，后续适配必须从上游文本/数值节点严格转换为十进制，不得经 `double`。
