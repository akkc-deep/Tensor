# M03-T09 49/49 名称、字段、参数、键和筛选总契约——任务设计

任务编号：`M03-T09`
对应任务：[M03-T09](../superpowers/plans/tensor-modules/M03-tushare-metadata.md#task-m03-t09-49-数据集总契约10hjava)
实施产物：`TushareMetadataContractTest` 永久构建门禁

## Goal

新增一个永久 Java 契约测试，通过 M03-T01 的公开 `DatasetDefinitionLoader` 加载全部 49 份 Tushare Pro YAML，并用不来自被测 YAML 的基线校验接口全集、模板字段顺序、PRD 参数、TRD 业务键和已批准筛选集合。

该门禁必须在 M03 后续修改中同时发现缺文件、多文件、字段漂移、参数漂移、业务键漂移、筛选漂移、表名公式破坏或悬空引用，使 49 份元数据继续保持单一且可执行的总契约。

## Scope

包含：

- 只创建任务卡指定的 `TushareMetadataContractTest.java`；
- 通过公开 loader 加载 `classpath*:datasets/tushare_pro/*.yaml`；
- 从 `docs/data-template/manifest.json` 读取 49 个 API 名和模板文件名；
- 以流式 JSON 方式从 49 个模板只读取根级 `api_name` 与 `fields`，遇到 `data` 必须 `skipChildren()`，不得物化完整样例数组；
- 在测试源码中显式编码 49 个 API 的参数、业务键和 filters 独立期望；
- 校验插件名、API 名、表名公式、49 文件全集、851 列名称/顺序、参数描述符、业务键模式/顺序、filters 引用和默认 batchSize；
- 执行可归因 RED、定向 GREEN、模块回归、`verify`、范围和格式门禁。

排除：

- 不修改生产 Java、POM、schema、49 份 YAML、JSON 模板、manifest、M02 records、M03-T01 loader/test 或其他模块；
- 不复制模板到 `src/test/resources`，不新增生成代码、资源同步脚本或第二个测试文件；
- 不从被测 YAML 或实际 `DatasetDefinition` 反向生成参数、键或 filters 的期望值；
- 不重新裁决 M03-T02～T08 已批准的列类型、长度和可空性，也不在本任务重复 851 列类型图；
- 不实现运行时模板读取、适配、下载、数据库、REST 或前端职责；生产 JAR 仍不得包含 `docs/data-template/`。

## Approach

### 测试结构

`TushareMetadataContractTest` 使用 JUnit 5、AssertJ、Jackson 和现有 Spring 资源解析器，不新增依赖。固定包含：

1. 一个全局测试 `hasExactManifestAndExpectationCoverage`：
   - 向上查找同时包含 `docs/data-template/manifest.json` 和 `data-plane/pom.xml` 的最近祖先作为仓库根；找不到时以包含当前绝对起点的明确断言失败；
   - 读取 manifest 的 `interfaces`，拒绝缺失、重复或空 `api_name`/`filename`，并断言恰有 49 项；
   - 通过 loader 加载全部 classpath YAML，拒绝重复 API，并断言 API 集合与 manifest 精确相等；
   - 断言参数、业务键和 filters 三个显式期望 map 的 key set 均与 manifest API 集合精确相等；
   - 断言 49 个定义合计恰有 851 列。
2. 一个 `@ParameterizedTest`，由已加载定义提供 49 次独立调用；每个 API 逐项断言：
   - `pluginId == tushare_pro`、`apiName` 与当前 manifest 项一致、`tableName == tushare_pro__<api>`、`batchSize == 500`；
   - manifest 的 `filename` 精确指向 `docs/data-template/<api>.json`，模板根级 `api_name` 等于当前 API；
   - 实际 `columns[].name` 与模板 `fields` 字符串数组完整同序相等；模板缺失字段、非字符串字段、重复字段或多余根文档均明确失败；
   - 实际参数列表与显式 `ExpectedParameter` 列表按声明顺序完全相等；
   - 实际 `BusinessKeyMode` 和字段顺序与显式 `ExpectedBusinessKey` 完全相等；
   - 实际 filters 与显式列表按顺序完全相等，并再次断言每个 filter 引用当前列；
   - 业务键的每个字段引用当前列，COMPOSITE 键列均不可空；FINGERPRINT 只校验显式字段顺序，不扩大其可空性约束。

测试类可使用 private records `ManifestEntry`、`TemplateProjection`、`ExpectedParameter` 和 `ExpectedBusinessKey`，以及只服务上述行为的 private helper。所有集合返回不可变副本；断言说明必须带 API 名和组件名，禁止用一个无上下文的布尔总断言隐藏首个漂移位置。

### 模板与仓库路径

仓库根查找从 `Path.of("").toAbsolutePath().normalize()` 开始逐级向上，只接受同时存在两个哨兵文件的目录。模板路径必须使用 manifest 的 `filename`，但同时断言 `filename` 精确为 `<api_name>.json`，并在解析前断言归一化后的模板路径仍位于 `docs/data-template/` 内。

模板读取使用 Jackson `JsonParser`：只接受一个根对象，只提取一个字符串 `api_name` 和一个字符串数组 `fields`；其他根属性全部跳过，`data` 数组不得进入 `JsonNode` 或 Java collection。解析结束必须确认无第二个 JSON 根值。

### 参数独立期望

`ExpectedParameter` 固定包含 `name`、`label`、`ParameterType`、`required`、`allowedValues`、`relatedParameter`。全部参数 `required: true`，`description/defaultValue/pattern` 必须为 `null`；非 ENUM 参数的 `allowedValues` 必须为空。下列分组必须展开成 key set 恰含 49 API 的显式 map，分组只减少重复，不允许查询实际 YAML 来补 key：

| API 分组 | 按序参数期望 |
|---|---|
| `stock_basic` | `list_status/上市状态/ENUM/[L,P,D]` |
| `stock_company` | `exchange/交易所/ENUM/[SSE,SZSE,BSE]` |
| `hs_const` | `hs_type/沪深港通类型/ENUM/[SH,SZ]` |
| `trade_cal` | `exchange`；`start_date/开始日期/DATE_RANGE_MEMBER/related=end_date`；`end_date/结束日期/DATE_RANGE_MEMBER/related=start_date` |
| `new_share,namechange` | 上述 `start_date,end_date` 两项 |
| `broker_recommend` | `month/月/月份` 的精确描述符为 `month/月份/MONTH` |
| `daily,weekly,monthly,adj_factor,suspend_d,daily_basic,stk_limit,moneyflow,margin_detail,top_list,top_inst,block_trade,moneyflow_hsgt,hsgt_top10,hk_hold,slb_len,slb_sec,slb_sec_detail` | `trade_date/交易日期/DATE` |
| `margin` | `exchange_id/交易所/ENUM/[SSE,SZSE,BSE]`；`trade_date/交易日期/DATE` |
| `income,balancesheet,cashflow,fina_indicator,fina_audit,fina_mainbz` | `ts_code/股票代码/TS_CODE`；`ann_date/公告日期/DATE` |
| `express,forecast,disclosure_date,dividend,repurchase,share_float,stk_holdertrade,top10_holders,top10_floatholders` | `ann_date/公告日期/DATE` |
| `stk_rewards,stk_holdernumber` | `ts_code/股票代码/TS_CODE` |
| `stk_managers,index_classify,index_member,index_member_all,pledge_stat,pledge_detail` | `[]` |

表中 `broker_recommend` 只采用最终字面期望 `month/月份/MONTH`；“月”不构成第二标签。实现测试源码时不得保留解释性斜线字符串，而应构造结构化 record。

### 业务键独立期望

测试源码必须用 `Map<String, ExpectedBusinessKey>` 显式逐项编码 TRD 9.4 的全部 49 行，模式和字段顺序不得通过 YAML、模板列或其他实际值推导。两个 FINGERPRINT 特例固定为：

- `stk_managers`: `[ts_code, ann_date, name, gender, lev, title, birthday, begin_date]`；
- `pledge_detail`: `[ts_code, ann_date, holder_name, pledge_amount, start_date, end_date, is_release, release_date, pledgor, holding_amount, pledged_amount, p_total_ratio, h_total_ratio, is_buyback]`。

其余 47 项逐字转录 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 9.4 表。Map 构造后必须先校验自身恰有 49 个无重复 key，再参与实际比较。

### filters 独立期望

filters 采用 M03-T02～T08 已批准设计的精确集合与顺序，固定分组如下；每个 API 只能属于一个分组：

| 期望 filters | API |
|---|---|
| `[]` | `trade_cal,index_classify,index_member` |
| `[ts_code]` | `stock_basic,stock_company,hs_const,new_share,broker_recommend,index_member_all,fina_mainbz,pledge_stat` |
| `[trade_date]` | `margin,moneyflow_hsgt,slb_len` |
| `[ts_code,trade_date]` | `daily,weekly,monthly,adj_factor,suspend_d,daily_basic,stk_limit,moneyflow,margin_detail,top_list,top_inst,block_trade,hsgt_top10,hk_hold,slb_sec,slb_sec_detail` |
| `[ts_code,ann_date]` | `namechange,stk_managers,income,balancesheet,cashflow,fina_indicator,fina_audit,express,forecast,disclosure_date,dividend,repurchase,share_float,stk_rewards,stk_holdernumber,stk_holdertrade,top10_holders,top10_floatholders,pledge_detail` |

显式 map 构造必须拒绝 API 落入两个分组，并最终断言 key set 与 manifest 相同。filters 的身份是精确产品契约，不得只检查“字段存在”。

### 失败边界

- manifest、模板或仓库根不可访问，manifest 非 49 项，API/filename 重复或不一致，必须使测试明确失败；
- YAML 缺失、多余、schema/M02/M03 语义错误继续由公开 loader 以 `DATASET_MISCONFIGURED` 失败；测试不得绕过 loader 直接解析 YAML；
- 模板 `api_name`、字段名或顺序漂移，显式参数/键/filters 漂移，表名或默认 batchSize 漂移，必须在对应 API 的参数化调用中失败；
- 测试不得联网，不得写仓库或模板，不得依赖模板 `data` 中是否有样例行。

## Files

只创建：

- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java`

实现提交消息固定为 `test(metadata): enforce all Tushare dataset contracts`。提交只暂存该测试文件；任务设计、交接、看板、生成的 `target` 和其他源码不得混入实现提交。

## Tests

### 可归因 RED

先确保父 POM 与 `tensor-plugin-api` 已进入隔离本地仓库，使模块单独执行时不会因兄弟依赖缺失失败：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-api -am -DskipTests install
```

在尚未创建目标测试类时运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-plugin-tushare/pom.xml \
  -Dtest=TushareMetadataContractTest test
```

第二条命令必须只因 `No tests matching pattern "TushareMetadataContractTest"` 退出非 0；不得因依赖、编译、schema、49 YAML 或环境错误失败。

### GREEN 与回归

创建完整测试类后运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am \
  -Dtest=TushareMetadataContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期 49 次参数化契约调用和一个全局覆盖测试全部通过：manifest、loader 和三个期望 map 均恰含同一 49 API；49 模板投影合计与 851 列逐项同序一致；参数、键、filters、表名、引用和默认值无漂移。

随后运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am verify
```

两条命令均必须退出 0；既有 87 项测试继续为 0 failure、0 error、0 skipped，新增 50 项测试全部通过，因此 reactor 总计 137 项；父项目、plugin-api 与 tushare 的 Enforcer 全部通过，且无新增警告类别。

最后确认生产 JAR 不含模板、测试类只出现在 test-classes，并清理生成物：

```bash
jar tf data-plane/tensor-plugin-tushare/target/tensor-plugin-tushare-1.0-SNAPSHOT.jar \
  | rg '(^docs/data-template/|TushareMetadataContractTest)'
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare -am clean
git status --short --untracked-files=all -- \
  data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata
git diff --check
```

第一条 `rg` 预期无输出并退出 1；`clean` 退出 0；提交前 status 精确列出 Files 节一个新文件，格式检查退出 0。提交后 `git show --stat --oneline HEAD` 显示固定消息和精确单文件范围，工作树干净。

## Acceptance

- 永久测试通过公开 loader 加载且只加载 49 份 Tushare Pro YAML，API 集与 manifest 完全一致；
- 49 个模板只流式读取 `api_name` 和 `fields`，不物化 `data`，851 个字段名与 YAML 列名完整同序一致；
- 参数期望完全来自本设计和 M03-T02～T08 批准描述符，参数 map 独立覆盖 49/49；
- 业务键期望逐项编码 TRD 9.4，模式和字段顺序覆盖 49/49，两个 FINGERPRINT 使用批准的完整身份字段；
- filters 期望精确采用本设计五组，覆盖 49/49 且全部引用现有列；表名公式、业务键引用和默认 batchSize 校验通过；
- 可归因 RED、定向 50 项 GREEN、reactor 137/137、`verify`、Enforcer、生产 JAR 排除、范围和格式门禁均得到预期结果；
- 实现提交精确包含一个测试文件，未修改生产 Java、POM、YAML、schema、模板、manifest 或其他模块。

## Risks

- 测试有意依赖仓库内 `docs/data-template/`，只作为构建期契约；仓库根查找使 Maven 从仓库根或模块目录运行均可工作，从仓库外复制单独模块执行时会以明确消息失败；
- 模板可能很大，因此必须用流式 parser 并跳过 `data`。把模板改为 `readTree` 会造成无必要的内存和时间开销，属于设计偏离；
- 参数、业务键或 filters 的授权变更必须同步更新相应设计/PRD/TRD 与本测试的显式期望；测试失败是要求审查契约变更的信号，不得改成从 YAML 自举期望以消除失败；
- 新增测试使 reactor 预期从 87 项增至 137 项；若测试框架对参数化调用计数方式发生版本变化，应以 49 次 API 调用全部执行和 0 failure/error/skipped 为结果级证据，不得删减 API 覆盖来追求显示计数。
