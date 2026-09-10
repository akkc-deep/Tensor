# ISSUE-017 方案：为 26 个接口补齐必填股票参数，保留 6 项原下载方式

状态：参数改造已实施并完成自动化验证（2026-09-11），见[实施与验证记录](../../verification/ISSUE-017-stock-scoped-downloads.md)。34 项股票必填、6 项原方式下载已落地；真实下载验收及母 issue 的 `fina_mainbz` 参数纠正尚未完成。下文保留实施时采用的设计。

开发就绪复核（2026-09-11）：本方案范围内的需求、40 项参数映射、错误行为和验证入口已明确，可以进入实施。下文已固定新请求样例与浏览器验证方式；这不表示功能或真实下载验收已经完成。

## 目标

为官网支持、项目尚未开放股票参数的 26 个接口增加必填 `params.ts_code`，与原有 8 项共同形成 34 项股票下载。对这 34 项，用户未指定股票时页面阻止提交，直接调用下载 API 时后端在调用上游前拒绝；合法请求中的股票条件必须传至 Tushare，返回结果只能属于目标股票。另外 6 项仍按原参数下载，当前 40 个下载入口均保留。

## 范围

本方案覆盖 26 项股票参数改造、原有 8 项股票下载回归，以及 6 项原方式下载的兼容验证。股票下载每次一只股票，沿用已有单值 `TS_CODE`；保持原日期、交易所、上市状态参数及其必填规则，不同时引入批量股票、自动补全、日期区间或全历史下载。

原有 8 项必填股票参数继续生效。`trade_cal`、`new_share`、`repurchase`、`margin`、`slb_len`、`index_classify` 不增加股票条件，不排除下载入口；生产接口注册、数据查看、manifest 支持清单均保持当前 40 项。`fina_mainbz` 参数纠正仍由母 issue 单独跟踪，不能仅凭本方案完成关闭整个问题。原始数据和数据库迁移保持其原始事实。

## 方案

### 选型

| 路径 | 改动与效果 | 建议 |
| --- | --- | --- |
| 扩展现有 YAML + 参数类型/Codec | 三个已有 record 直接增加 `tsCode`，仅为 `slb_len` 新增一个日期专用 record；复用动态表单与通用校验 | 按用户确认的方向采用 |
| 给下载请求新增顶层 `tsCode` | 同时存在顶层股票与 `params`，需要改协议并定义合并及冲突规则 | 当前没有必要 |
| 重写参数系统为通用 Map 或新能力框架 | 范围扩大，并影响已有强类型绑定合同 | 当前没有必要 |

### 1. 在 26 份 YAML 中把股票参数声明为必填

统一将以下声明放在 `parameters` 第一项，名称、类型和约束与已有 8 项一致：

```yaml
- { name: ts_code, label: 股票代码, type: TS_CODE, required: true }
```

不设置 `defaultValue`，也不预填样例股票，确保用户明确提供目标。已有其他参数保持原顺序、类型、必填属性和枚举值。

| 改造后的必填参数 | 数量 | 接口 | Java 参数类型 |
| --- | --- | --- | --- |
| `ts_code, trade_date` | 13 | `adj_factor`、`block_trade`、`daily`、`daily_basic`、`margin_detail`、`moneyflow`、`monthly`、`slb_sec`、`slb_sec_detail`、`stk_limit`、`suspend_d`、`top_list`、`weekly` | 修改已有 `TradeDateParameters` |
| `ts_code, ann_date` | 7 | `disclosure_date`、`dividend`、`express`、`forecast`、`stk_holdertrade`、`top10_floatholders`、`top10_holders` | 复用 `TsCodeAnnDateParameters` |
| `ts_code` | 4 | `index_member_all`、`pledge_detail`、`pledge_stat`、`stk_managers` | 复用 `TsCodeParameters` |
| `ts_code, exchange` | 1 | `stock_company` | 修改已有 `ExchangeParameters` |
| `ts_code, list_status` | 1 | `stock_basic` | 修改已有 `ListStatusParameters` |

`top_list` 的交易日仍符合官网必填要求。其余日期本轮也继续必填，以保留现有下载范围；放宽日期是另一项产品行为调整，不是增加股票必填的前提。`stock_company` 的交易所、`stock_basic` 的上市状态仍由用户提供，应与目标股票一致。

### 2. 修改三个已有 record，只新增一个日期专用 record

在 `DownloadParameters` 内直接给以下三个已有 record 增加 `tsCode`，并为 `slb_len` 新增 `TradeDateOnlyParameters`：

```java
record TradeDateParameters(String tsCode, String tradeDate) implements DownloadParameters {}
record ExchangeParameters(String tsCode, String exchange) implements DownloadParameters {}
record ListStatusParameters(String tsCode, String listStatus) implements DownloadParameters {}
record TradeDateOnlyParameters(String tradeDate) implements DownloadParameters {}
```

在 `ParameterCodec.supported()` 中修改前三个类型对应的 Codec，使其结构和读写字段均包含必填 `ts_code`；新增 `TradeDateOnlyParameters` 的 Codec，结构为必填 `trade_date`，读写均只包含日期。股票类型读取时保留缺失值为 null，写回时准确生成 `ts_code` 和原有参数名；沿用 `DownloadParameterResolver.toRawValues(..., suppliedFields)`，不合成用户没有提交的股票字段。原有 record 的构造器调用和类型预期同步更新。

当前 `ParameterShape` 比较字段名称、类型、必填、默认值、枚举等约束。因此仅修改 YAML 会导致新组合找不到 Codec，返回 `DATASET_MISCONFIGURED`；类型和 Codec 必须与 YAML 一起发布。`exchange` 复用 `SSE/SZSE/BSE`，`list_status` 复用 `L/P/D`，股票字段使用必填 `TS_CODE`、无默认值。

`DownloadParameterResolver` 按 Java 类型建立唯一的 `byType` 索引，不能让同一个 record 同时注册股票＋日期与仅日期两个 Codec。日期专用 record 保持这一约束，无需给 `tsCode` 引入可选语义，也无需改变 Resolver；现有 11 个类型/Codec 变为 12 个。全部 40 项及 fixture 均应逐一验证唯一匹配和读写往返。

6 项原方式下载的参数与类型明确如下，只有 `slb_len` 更换内部类型，外部 JSON 继续只传原参数：

| 接口 | 保留的下载参数 | Java 参数类型 |
| --- | --- | --- |
| `trade_cal` | `exchange, start_date, end_date` | `ExchangeDateRangeParameters` |
| `new_share` | `start_date, end_date` | `DateRangeParameters` |
| `repurchase` | `ann_date` | `AnnDateParameters` |
| `margin` | `exchange_id, trade_date` | `ExchangeTradeDateParameters` |
| `slb_len` | `trade_date` | 新增 `TradeDateOnlyParameters` |
| `index_classify` | 无，`params: {}` | `SnapshotParameters` |

`ExchangeDateRangeParameters` 和 `ExchangeTradeDateParameters` 是独立类型，不随 `ExchangeParameters` 或 `TradeDateParameters` 增加股票字段。`AnnDateParameters` 继续服务 `repurchase`；改造中的 7 项公告日接口使用已有 `TsCodeAnnDateParameters`。

### 3. 复用页面与服务端的必填校验

页面为 34 项股票下载从接口元数据自动显示股票输入框，使用现有 `useParameterForm` 检查必填和格式，规范化前后空格与大小写。没有股票时显示字段错误，不发送下载请求；原本无需参数的 4 项现在进入正常表单分支。6 项原方式下载不显示股票输入框，其中 `index_classify` 继续允许空参数对象。重试沿用 `useDownloadFlow` 保存的完整请求快照，股票下载保留最初提交的目标股票，原方式下载保留其原参数。

服务端最终校验继续放在 `DownloadService.execute()` 调用的 `ParameterValidator.validate()`，顺序为：绑定参数 → 按该接口元数据校验 → `plugin.download()` → Tushare 请求 → 入库。即使旧页面或脚本绕过前端，34 项股票下载缺失股票也会在上游调用前失败；6 项原方式下载只验证其原参数。控制器无需为每个接口编写分支，也不能无条件对所有接口要求 `ts_code`。

| 输入情形（股票下载接口，其他参数合法） | 预期行为 |
| --- | --- |
| 缺少 `ts_code`、null、空串、纯空白 | HTTP 400 / `PARAM_REQUIRED`，定位 `ts_code`，上游零调用、零写库 |
| 股票代码格式错误，或提交数字、数组、对象、逗号分隔多代码 | HTTP 400 / `PARAM_INVALID`，上游零调用、零写库 |
| `" 000001.sz "` | 规范化为 `000001.SZ` 后请求上游 |
| 缺少或错误的日期/交易所/上市状态 | 沿用原有参数规则；同时有多个错误时保持现有必填优先顺序 |
| 请求使用旧参数组合，例如 daily 只传日期 | HTTP 400 / `PARAM_REQUIRED`，不兼容回退为全市场下载 |

6 项原方式下载的合法旧请求继续执行，包括 `slb_len` 只传 `trade_date`、`repurchase` 只传 `ann_date`、`index_classify` 传 `{}`。它们若额外提交未声明的 `ts_code`，仍按现有未知参数规则拒绝；不因结果字段包含股票代码而自动注入股票筛选。

HTTP 外层结构继续使用 `pluginId / apiName / params`，但这 26 项增加必填字段属于调用合同变化，调用方必须随版本更新。示例：

```json
{
  "pluginId": "tushare_pro",
  "apiName": "daily",
  "params": {
    "ts_code": "000001.SZ",
    "trade_date": "20260910"
  }
}
```

### 4. 确认上游实际带股票条件，并校验响应范围

现有 `TushareProClient.execute()` 已将服务层验证后的参数写入 Tushare 请求的 `params`。修改后的股票 Codec 正确写回 `ts_code` 后可直接复用；日期专用 Codec 只写回 `trade_date`。回归测试必须检查实际 HTTP 请求体，确认 34 项携带目标股票、6 项仅携带原参数。

在现有 `TushareResponseValidator.validate()` 中增加一段股票范围检查：仅当对应接口元数据声明必填 `ts_code / TS_CODE` 时，要求请求中存在已规范化的股票代码，并在现有字段/行结构校验之后定位 `ts_code` 列，将每行值与请求代码精确比较。非空结果中代码列缺失、任一行代码为 null 或不匹配，都返回现有 `SOURCE_PAYLOAD_INVALID`（HTTP 502），整批不进入适配与持久化。该逻辑统一覆盖新增 26 项及原有 8 项；6 项不执行股票归属校验，尤其不能误拒绝 `new_share`、`repurchase` 的正常多股票结果。

合法空结果继续返回 `EMPTY`。34 项股票下载重试使用同一股票，不得省略股票重试、下载全市场后过滤或将混股结果静默筛选后入库。6 项原方式下载继续正常处理汇总和多股票结果。错误响应和日志沿用现有安全消息，不回显整份上游数据。

### 5. 同步合同、测试输入和调用方

更新 OpenAPI 的说明和示例，明确这 26 项需要 `params.ts_code`，必填约束仍以对应接口元数据为准。更新前后端独立参数预期、浏览器用例、下载脚本和新版本运行样例。

`docs/data-template` 中现有文件带有真实 `fetched_at`、旧请求参数和全市场返回数据，保留其历史来源含义，继续用于字段/结构合同；不能只补一个 `ts_code` 就将其伪装成新下载样例。新建 `docs/contracts/download-request-examples.json`，只保存当前请求，不包含返回数据、抓取时间或成功状态。结构固定为：

```json
{
  "requests": [
    {
      "pluginId": "tushare_pro",
      "apiName": "daily",
      "params": { "ts_code": "000001.SZ", "trade_date": "20260807" }
    },
    {
      "pluginId": "tushare_pro",
      "apiName": "slb_len",
      "params": { "trade_date": "20260807" }
    }
  ]
}
```

上面仅展示两项；正式文件按 manifest 顺序为全部 40 个接口各提供一项。34 项股票请求统一使用 `000001.SZ`，`stock_company.exchange` 使用匹配的 `SZSE`，`stock_basic.list_status` 使用 `L`；已有日期沿用 manifest 中该接口第一组样例的日期。6 项原方式下载直接沿用各自第一组参数，包括 `index_classify` 的 `{}`。接口集合和必填字段以本方案的独立映射表核验，示例股票不能成为生产表单或后端默认值。

在 `DownloadRequestBindingTest` 中读取这份样例，验证 40 个接口各一次、参数绑定与规范化正确；对 34 项逐一删除股票字段，验证受控拒绝且没有上游调用；对 6 项保持原参数验证可继续执行。当前真实下载执行器 `control-plane/e2e/tushare-live.spec.js` 应将请求输入与历史 manifest 分开：manifest 继续提供接口/字段来源，新版本请求从新样例文件读取，真实验收绑定新请求文件与新 JAR 的身份。涉及旧参数预期的 `tushare-metadata.spec.js`、`download-outcomes.spec.js`、`scripts/security/verify-release.sh` 按新请求合同更新；历史验收记录中的参数、结果、哈希和截图不改写。

需要新真实响应样例时，应重新按股票抓取并记录实际参数、时间和匹配结果；上面的请求示例不充当真实通过证据。生产数据集总数仍为 40。

## 涉及文件

以下为本方案的实施位置；实际改动及新增组合回归见验证记录。

| 文件 | 责任 |
| --- | --- |
| [Tushare YAML 目录](../../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro) | 修改表格列出的 26 份 YAML，新增第一项必填股票参数 |
| [DownloadParameters.java](../../../data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/DownloadParameters.java) | 修改 3 个已有 record，新增 1 个 `TradeDateOnlyParameters` |
| [ParameterCodec.java](../../../data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/ParameterCodec.java) | 修改 3 个已有 Codec，新增 1 个日期专用 Codec |
| [TushareResponseValidator.java](../../../data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareResponseValidator.java) | 校验返回行的股票归属 |
| [openapi-v1.yaml](../../contracts/openapi-v1.yaml) | 更新必填要求说明和请求示例 |
| `docs/contracts/download-request-examples.json`（已创建） | 40 项当前请求样例，作为新版本执行输入，不包含历史响应证据 |
| [TushareMetadataContractTest.java](../../../data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java) | 维护独立的 26 项预期，检查股票第一项、必填、无默认值 |
| [DownloadParameterResolverTest.java](../../../data-plane/tensor-app/src/test/java/com/akkc/tensor/web/download/DownloadParameterResolverTest.java)、[DownloadRequestBindingTest.java](../../../data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadRequestBindingTest.java)、[DownloadControllerIT.java](../../../data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java) | 检查 5 种股票结构、修改的 3 个类型和新增日期类型；覆盖 26 项请求及 6 项旧请求兼容 |
| [DownloadServiceTest.java](../../../data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadServiceTest.java)、[TushareProClientTest.java](../../../data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareProClientTest.java) | 检查校验先于上游调用、请求体传参、股票下载混股拒绝及非股票下载多股票结果兼容 |
| [DynamicParameterForm.spec.js](../../../control-plane/src/components/download/DynamicParameterForm.spec.js)、[DownloadView.spec.js](../../../control-plane/src/views/DownloadView.spec.js)、[useDownloadFlow.spec.js](../../../control-plane/src/composables/useDownloadFlow.spec.js) | 检查股票下载必填及重试；6 项原入口、表单与提交保持可用 |
| `control-plane/e2e/stock-download-parameters.spec.js`（已创建） | 以模拟 API 验证 34 项股票必填、6 项原方式提交和请求快照重试 |
| [ui-redesign.fixtures.js](../../../control-plane/e2e/ui-redesign.fixtures.js)、[ui-redesign.spec.js](../../../control-plane/e2e/ui-redesign.spec.js) | 更新独立参数矩阵及旧请求断言，新用例复用已有 API 模拟与数据集描述 |
| [tushare-live.spec.js](../../../control-plane/e2e/tushare-live.spec.js)、[tushare-metadata.spec.js](../../../control-plane/e2e/tushare-metadata.spec.js)、[download-outcomes.spec.js](../../../control-plane/e2e/download-outcomes.spec.js)、[verify-release.sh](../../../scripts/security/verify-release.sh) | 更新当前执行器的参数预期、请求输入及其新版本身份；保留历史验收记录 |

## 验证

从仓库根执行下列验证入口；本次实际结果、环境配置及未执行项见验证记录。

```sh
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=TushareMetadataContractTest,DownloadParameterResolverTest,DownloadRequestBindingTest,DownloadControllerIT,DownloadServiceTest,TushareProClientTest -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test -- src/components/download/DynamicParameterForm.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js
mvn -f data-plane/pom.xml clean verify
```

预期所有定向测试和完整构建通过。构建使用 Java 21、项目指定 Node/npm；数据库集成验证需可用 Docker/Testcontainers。

浏览器验证新增 `control-plane/e2e/stock-download-parameters.spec.js`，复用 `ui-redesign.fixtures.js` 的接口模拟和独立参数矩阵。启动本地前端开发服务，在另一个终端运行新用例：

```sh
npm --prefix control-plane run dev -- --host 127.0.0.1 --port 4173 --strictPort
```

```sh
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173 npm --prefix control-plane run test:e2e -- stock-download-parameters.spec.js --project=chromium
```

前置条件为前端依赖和 Playwright Chromium 已安装；4173 端口未占用，测试退出后停止本次启动的服务。用例必须在进入页面前拦截全部 `/api/v1/**` 请求，沿用现有模拟器的未知请求检查，不使用真实 Token、后端服务或数据库，也不写入历史截图目录。

覆盖 34 项股票表单空值阻断、规范化后合法提交，6 项原表单合法提交（包含 `slb_len` 只填交易日、`index_classify` 无参数），以及失败重试保留原请求。通过条件是请求数量、完整请求体、页面状态均符合对应接口合同且没有未声明请求。浏览器模拟只证明页面行为；后端拒绝、上游 HTTP 请求体和混股结果零写库由前述 Java 测试证明，不能用页面模拟替代。

已提交到 `main`、生产源码及数据模板无未提交修改且满足既有 Maven 缓存/Docker 前置条件后，再执行 `sh scripts/verify-contracts.sh`，预期元数据、schema、打包合同全部通过。该脚本当前从 HEAD 创建快照，不能在未提交改动上运行后声称验证了新实现。

## 验收条件

- 26 项均具有必填且无默认值的单只股票参数；与原有 8 项合计 34 项具备该约束。
- 三个已有 record 增加 `tsCode`，仅新增 `TradeDateOnlyParameters`；5 种股票参数组合和 6 项原方式参数均唯一绑定正确类型，40 项及 fixture 的 Codec 读写往返正确。
- 6 项原方式下载的入口、原参数及合法调用保持可用；`slb_len` 的日期专用类型仅向上游写入 `trade_date`，不存在空 `ts_code` 字段；下载及数据查看注册总数均保持 40。
- 全部 26 项覆盖缺失、null、空白、非法类型/格式的请求；拒绝请求对上游和持久化均零调用。
- 对 34 项股票下载，合法请求的上游 HTTP 请求体包含规范化 `ts_code`；按两只不同股票分别验证，不存在自动省略或替换股票的逻辑。
- 股票下载非空响应全部属于请求股票，混股响应被整批拒绝且零写库；空结果与重试不扩大请求范围。6 项非股票下载保留正常汇总和多股票结果处理，不被股票归属校验误伤。
- 页面必填、合法提交和重试通过验证；当前调用方完成必填字段迁移；原有 8 项及其他现有合同回归通过。
- 母 issue 中 `fina_mainbz` 参数纠正及其他尚未完成的验证继续跟踪，不以本方案局部完成关闭整个问题。

## 风险与后续边界

- 官方支持参数不等于账户有权限或指定日期一定有数据。`slb_sec`、`slb_sec_detail` 官网标停，真实验收需合适历史区间；当前日期 EMPTY 不能证明已验证完整历史能力。
- 现有 `TS_CODE` 校验的是代码格式，不查询证券主数据证明股票存在；此方案不新增会触发全市场下载的股票选择器。
- 对 34 项股票下载，响应范围校验会将以往被接受的混股/null 股票响应转为明确失败，应通过真实样例核验上游行为；6 项原方式下载保持其原有结果语义。
- 给已有 record 增加字段会改变内部构造器签名，须同步更新所有调用点；`slb_len` 必须迁移到日期专用类型，避免被错误要求股票。全部 40 项的元数据/绑定回归应覆盖该边界。
