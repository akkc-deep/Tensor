# M03-T07 公司行动 3 数据集 YAML——任务设计

任务编号：`M03-T07`  
对应任务：[M03-T07](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t07-公司行动-3-数据集20hyaml)  
实施产物：`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下 3 份公司行动数据集 YAML

## Goal

为 `dividend`、`repurchase` 和 `share_float` 建立唯一运行时元数据。每份 YAML 必须通过 M03-T01 的严格 loader，保持对应 JSON 模板的完整字段名和顺序，采用项目所有者 2026-09-01 批准的 30 列类型、长度和可空性映射，并与 PRD 附录 A.7 的公告日参数及 TRD 9.4 的复合业务键一致。

本任务完成后，后续模块可通过 `DatasetDefinitionLoader` 读取这 3 个不可变 `DatasetDefinition`。当前没有样例行的 `dividend` 也必须形成完整、可加载、可持久化设计的数据集定义，后续模块无需根据缺失样例猜测字段类型、业务键、筛选或固定列。

## Scope

包含：

- 只创建任务卡指定的 3 份运行时 YAML；
- 固定每份定义的插件/API/表名、分类、显示名、查询方式、参数、字段、业务键、筛选和固定列；
- 按本设计的完整且机械可求值的 30 列映射设置 `logicalType`、`nullable`、`length`、`precision` 和 `scale`；
- 使用 M03-T01 现有公开 loader 和 `/private/tmp` 临时 Java harness 执行缺失 3 文件的 RED、精确契约 GREEN、空模板验证、模块回归与 JAR 内容验证；
- 提交精确 3 个 YAML 文件。

排除：

- 不修改 Java、POM、M00 schema/示例、M02 records、M03-T01 loader/test、既有 39 份 YAML、其他模块或 `docs/data-template/`；
- 不创建永久测试类；M03-T09 负责 49/49 永久 Java 契约测试；
- 不读取其他 46 份模板，也不把本任务 3 份模板的完整 `data` 数组载入上下文；字段基线只使用 `jq '{api_name,query_mode,params,fields,sample:(.data[0] // null)}'` 投影；
- 不因 `dividend` 当前为空而删除文件、字段或接口支持；
- 不新增源字段、审计字段、`business_key`、批次字段或数据库列；
- 不把 `div_proc`、`proc` 或 `share_type` 收窄为没有批准完整取值集的 `ENUM`；
- 不实现参数值校验、适配、下载、数据库、REST 或前端职责。

## Approach

### 共同结构和命名

全部 YAML 固定：

- `pluginId: tushare_pro`；
- `tableName` 精确由字面前缀 `tushare_pro__` 与文件的 `apiName` 拼接；
- `category: 公司行动`；
- `queryMode: ann_date`；
- 只声明一个必填参数 `{name: ann_date, label: 公告日期, type: DATE, required: true}`，省略 `description`、`defaultValue`、`allowedValues`、`pattern` 和 `relatedParameter`；
- `displayOrder` 按对应模板 `fields` 从 0 连续递增；列 `label` 精确等于源字段名；
- `STRING` 只写 `length`，`DECIMAL` 只写 `precision: 38` 与 `scale: 18`，`DATE` 不写长度或精度；
- 全部列省略 `allowedValues` 和 `longText`，loader 分别映射为空列表和 `false`；
- 所有定义省略 YAML 中不存在的 `batchSize`，继续采用 M02 默认值 500。

### 数据集总表

| API | displayName | businessKey | filters | fixedColumn | fields |
|---|---|---|---|---|---:|
| `dividend` | 分红送股 | `COMPOSITE: [ts_code, end_date, ann_date]` | `[ts_code, ann_date]` | `ts_code` | 14 |
| `repurchase` | 股票回购 | `COMPOSITE: [ts_code, ann_date, proc]` | `[ts_code, ann_date]` | `ts_code` | 9 |
| `share_float` | 限售股解禁 | `COMPOSITE: [ts_code, float_date, holder_name, share_type]` | `[ts_code, ann_date]` | `ts_code` | 7 |

三份定义的 filters 只使用实际存在的 `ts_code` 和 `ann_date`，并保持 `[ts_code, ann_date]` 顺序；固定列统一使用实际存在的 `ts_code`。

### 完整 30 列类型映射

项目所有者 2026-09-01 批准以下互斥且有优先级的精确字段名规则。对每份模板的每个 `fields` 元素按 1→5 顺序匹配；每列只有一个结果：

1. 精确字段名属于 `{end_date, ann_date, record_date, ex_date, pay_date, div_listdate, imp_ann_date, exp_date, float_date}`：`logicalType: DATE`，不写长度或精度。
2. 精确字段名属于 `{ts_code, div_proc, proc, share_type}`：`logicalType: STRING, length: 64`。
3. 精确字段名为 `holder_name`：`logicalType: STRING, length: 128`。
4. 精确字段名属于 `{stk_div, stk_bo_rate, stk_co_rate, cash_div, cash_div_tax, vol, amount, high_limit, low_limit, float_share, float_ratio}`：`logicalType: DECIMAL, precision: 38, scale: 18`。
5. 本任务不存在未命中 1～4 的模板字段；harness 遇到未分类字段必须失败，不得建立隐式兜底类型。

按模板字段出现次数统计，30 列恰为 12 个 `DATE`、6 个 `STRING(64)`、1 个 `STRING(128)` 和 11 个 `DECIMAL(38,18)`。本任务没有 `TEXT`、`MONTH`、`LONG` 或列级 `ENUM`；不得根据单个样例值改变批准类型，也不得经 `double`。

### 完整 30 列可空性映射

每份定义先使用数据集总表中的精确 `businessKey.fields` 集合：

- 字段名属于该 API 的 `businessKey.fields`：固定 `nullable: false`；
- 该 API 的所有其他字段：固定 `nullable: true`。

因此 `dividend.ts_code/end_date/ann_date`、`repurchase.ts_code/ann_date/proc` 与 `share_float.ts_code/float_date/holder_name/share_type` 不可空，其余 20 列可空。参数必填性、筛选和固定列身份不扩大不可空集合；例如 `share_float.ann_date` 可空。

### 错误边界

- 任一 YAML 的 schema、M02 构造或 M03 语义错误必须使该文件经公开 loader 以 `DATASET_MISCONFIGURED` 失败；不得把无效定义视为成功；
- 文件名/API 不一致、表名不匹配、字段缺失/重复/错序、未分类字段、类型规则偏离、业务键列标为可空、参数错误、业务键/筛选/固定列悬空都必须由临时 harness 拒绝；
- harness 必须分别通过 3 个精确 classpath 资源路径调用公开 loader，并逐项比较 API、30 个字段名/顺序/类型/可空性、参数、业务键、筛选、固定列和默认 batchSize；只检查“能加载”或字段计数不足以验收；
- `dividend` 空模板只证明当前没有样例值，不参与运行时加载；harness 仍须验证它的全部 14 列；
- 临时 harness 只存在于 `/private/tmp/M03T07MetadataCheck.java`，不得进入 Git 或仓库源码树。

## Files

只创建：

- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/dividend.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/repurchase.yaml`
- `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/share_float.yaml`

实现提交消息固定为 `feat(metadata): define corporate action datasets`。提交只暂存上述 3 个文件；任务设计、交接、看板、临时 harness、生成的 `target` 与其他源码不得混入实现提交。

## Tests

### 临时 harness、空模板基线与 RED

先在 `/private/tmp/M03T07MetadataCheck.java` 创建临时 source-file harness。它必须对 3 个精确资源路径分别调用公开 `DatasetDefinitionLoader.loadAll`，硬编码三份模板的 30 个字段名/顺序、数据集总表、类型字段集合和业务键集合，并逐项断言本设计的完整契约。成功时只输出：

```text
M03-T07_OK:3:30
```

先确认空模板字段完整但没有样例数据：

```bash
jq -e '.data == [] and (.fields | length) == 14' docs/data-template/dividend.json
```

命令预期输出 `true` 并退出 0。随后在不创建本任务运行时 YAML 的状态运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -q \
  -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=DatasetDefinitionLoaderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

m03_t07_cp=$(xmllint --xpath \
  'string(/testsuite/properties/property[@name="java.class.path"]/@value)' \
  data-plane/tensor-plugin-tushare/target/surefire-reports/TEST-com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoaderTest.xml)

java -Dslf4j.internal.verbosity=ERROR --class-path "$m03_t07_cp" \
  /private/tmp/M03T07MetadataCheck.java \
  'classpath*:datasets/tushare_pro/'
```

第一条预期 `DatasetDefinitionLoaderTest` 8/8 通过并生成精确测试 classpath；第三条预期 harness 编译成功后因首个缺失的精确资源得到 `<pattern>: no resources matched` 并退出非 0，形成只针对本任务缺失 YAML 的 RED。失败不得来自 harness 编译、依赖、schema、既有 39 份 YAML 或环境错误。

### GREEN 与回归

创建 3 份 YAML 后重跑上述 Maven 与 harness 命令。预期：

- `DatasetDefinitionLoaderTest` 8/8 通过；
- harness 只输出 `M03-T07_OK:3:30` 并退出 0；
- 每个精确资源路径恰加载一个定义；字段计数依次为 14、9、7，总计 30；
- 每列名称/顺序、类型、可空性和适用属性由完整映射唯一得到；三份参数、键、筛选、固定列和默认 batchSize 与数据集总表一致；
- `dividend` 的 14 列在没有样例行时仍完整加载。

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
  | rg '^datasets/tushare_pro/(dividend|repurchase|share_float)\.yaml$'
```

预期恰输出 3 行，且运行时源目录合计恰有 42 份 YAML。最后运行 Maven `clean` 清除未被仓库忽略的 `target`，删除 `/private/tmp/M03T07MetadataCheck.java`，并运行：

```bash
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro
git diff --check
```

提交前第一条精确列出 Files 节 3 个 YAML，格式检查退出 0；提交后 `git show --stat --oneline HEAD` 显示固定消息和精确 3 文件范围，工作树干净。

## Acceptance

- 运行时目录恰新增 3 份公司行动 YAML；API 名、显示名、查询方式与 PRD 附录 A.7、manifest 投影一致；
- 每份 `columns` 与对应 JSON `fields` 名称和顺序完全一致，字段计数分别为 14、9、7，合计 30；全部类型、长度、精度和可空性可由本设计规则唯一求得；
- 三份定义都只声明必填 `ann_date: DATE`；业务键与 TRD 9.4 完全一致，filters/fixedColumn 全部引用现有列；
- 空 `dividend` 模板断言通过，其 14 列运行时定义仍完整加载；临时 harness 经历可归因 RED 后输出 `M03-T07_OK:3:30` GREEN；
- loader 8/8、reactor 87/87、`verify`、Enforcer、JAR 3 文件、源目录 42 文件、范围与格式门禁得到预期结果；
- 实现提交精确包含 3 个 YAML，未修改 Java/POM/schema/template/既有 YAML/其他模块，未提交临时 harness 或生成物。

## Risks

- `dividend` 当前没有样例行；项目所有者已批准本设计的精确字段名映射和“业务键不可空、其余可空”规则。未来真实上游值若不符合该映射，必须通过新的设计裁决及必要迁移处理，不得静默截断、填充或改型；
- `div_proc`、`proc` 和 `share_type` 采用 `STRING(64)`，`holder_name` 采用 `STRING(128)`，不建立没有完整取值集的 `ENUM`。未来出现超长值或需要闭集语义时必须另行设计，不得在适配阶段静默截断；
- `repurchase.proc` 与 `share_float.holder_name/share_type` 属于业务键并固定不可空；真实上游若返回空值，后续适配必须显式失败并通过新设计处理，不得填充伪值；
- 本任务所有数值列使用 `DECIMAL(38,18)`，后续适配必须从上游文本/数值节点严格转换为十进制，不得经 `double`。
