# 数据完整性检验设计

日期：2026-09-16。设计版本：v1。依据用户已确认的方案及关于问题日期、规则扩展、前端页面和指定检查范围的补充讨论编写。本次交付为设计文档，功能实现另行执行。

## 做什么

用户选择数据源插件，指定一只或多只股票和日期闭区间，发起完整性计算，得到按“股票 × 接口”组织的报告。数据接口默认全选，允许只检查用户勾选的接口。“全部”限定在所选数据源、股票和时间范围内，不扫描整个数据库。所选接口中不适用或尚无规则的项目仍在报告中解释，不能只统计能够通过的接口。

第一版检查本地已入库数据，计算时不主动访问上游、不自动补数。包含前端入口、检查任务、历史报告、具体问题日期及可扩展的代码规则。在线对账、自动补数、定时检查、跨数据源合并评分和规则编辑器不在第一版范围。

结果回答三个问题：有哪些记录、确认缺少哪些记录、哪些范围还无法判断。记录覆盖率与必填字段缺失分别展示。完整性检查不承担财务勾稽、行情合理性等全部数据质量规则，也不把下载成功当作完整性的证明。

遵循现有 [完整性规则](../runbook/data-integrity-rules.md) 及 [逐项解释](../runbook/data-integrity-explained.md)。只检查本地时，“响应入库一致”“源端全量一致”等没有相应证据的结论保持 UNKNOWN，不能由本地覆盖率推导。

## 怎么做

### 1. 采用现有插件的可选能力扩展

沿用 BatchDownloadSupport 的方式，使用可选能力扩展、来源内部代码规则及统一报告。避免为每个数据源重新实现任务与页面，也不引入通用规则脚本或 DSL 引擎。

不新建一个与数据源平行的业务插件体系。现有 DataSourcePlugin 保持原有合同，可选新增 IntegrityCheckSupport；TushareProPlugin 将检查委托给本插件内的策略类。旧插件不实现该接口时，显示“未提供完整性检查能力”。

能力接口固定如下。`IntegrityRule` 位于 plugin-api，core 仅依赖这份合同，不依赖来源模块：

```java
public interface IntegrityCheckSupport extends DataSourcePlugin {
    String normalizeIntegritySymbol(String symbol);
    Optional<IntegrityDescriptor> integrityDescriptor(ApiName apiName);
    List<IntegrityRule> integrityRules(ApiName apiName);
}

public interface IntegrityRule {
    IntegrityRuleDescriptor descriptor();
    IntegrityRuleResult evaluate(
            IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues);
}
```

- `IntegrityDescriptor`：datasetKey、scopeKind（STOCK_DATE/STOCK_SNAPSHOT/NON_STOCK）、股票列、日期字段和标签、市场时区、能力版本、依赖数据集与列、规则描述列表、限制说明。描述查询为本地纯操作；来源已知 API 没有描述时，报告 UNKNOWN/RULE_NOT_IMPLEMENTED。
- `IntegrityScope`：datasetKey、单只规范股票代码、用户起止日期、受理时刻、该单元快照开始时间。日期闭区间不可被规则静默缩小；允许扩大参考数据查询范围，但要记录用途。
- `IntegrityRuleDescriptor`：ruleId、version、displayName、dimension（COVERAGE/KEY/FIELD）、依赖列/数据集与说明。ruleId 在插件内唯一，首版版本为字符串 `1`。每个 STOCK_DATE/STOCK_SNAPSHOT 接口恰有一项 COVERAGE 规则，NON_STOCK 无执行规则；描述和实际返回规则必须一致，缺列或重复规则使该接口能力无效。
- `IntegrityContext`：框架提供 `scan(IntegrityReadRequest, Consumer<List<Map<String,Object>>>)` 和 `compare(IntegrityExpectedKeys)`。前者在同一快照内按 scan-batch-size（默认 500 行）分批只读扫描；后者复用完整业务键集合比较并返回统计，通过本单元的问题收集器记录差异。PROVEN 的缺键可报 MISSING；UNCONFIRMED 的候选差集只能报 SUSPECTED_MISSING，不能据此判定 EXTRA。规则不能接触 JdbcTemplate、任意 SQL 或证券表写入接口。
- `IntegrityReadRequest`：datasetKey、投影列、等值条件及日期上下界。core 从 catalog 校验表与列、按类型绑定值；请求只能读取当前股票的目标表或 descriptor 已声明的参考范围，不能借引用扩成全市场扫描。
- `IntegrityExpectedKeys`：目标检查范围、basis（PROVEN/UNCONFIRMED）、完整业务键集合和证据。证据必须包含来源、规则版本、覆盖区间、读取时点及内容摘要；PROVEN 表示全范围基线可靠。部分可靠时整体仍为 UNCONFIRMED，已确认缺失作为带局部证据的问题保留，不能生成整体覆盖率。
- `IntegrityRuleResult`：ruleId、version、status、reasonCode、说明、该维度的计数及证据。只有 COVERAGE 规则能写预期数与覆盖率。`IntegrityIssueSink.add(IntegrityIssue)` 收集问题，执行器统一补充 checkId/resultId/ruleId，避免规则自行归入其他任务。

核心通用规则由执行器固定加入；来源通过 `integrityRules` 提供覆盖规则及附加规则。按 ruleId 稳定顺序执行，单规则异常转 UNKNOWN/RULE_EXECUTION_FAILED，其他独立规则继续；规则不能依赖上一条规则修改内存状态。执行器按规则返回值和实际问题共同聚合，已存在 FAIL 问题不能被规则返回的 PASS 覆盖。数据库快照失效或扫描上限触发时整个单元停止，按执行异常处理。

插件负责回答“这个范围按本来源的规则应该有哪些记录”；核心层负责查本地、比较集合、执行任务并保存报告。预期键与实际键可以分批处理，不要求一次把全部记录放入内存。

比较依据是 DatasetDefinition 定义的字段顺序和类型；日期使用 LocalDate，数值精确比较，DECIMAL 按数值而非不同 scale 的字符串表示判断相等。所有预期键先按同一字段合同验证，精度不合法时规则失败，不能截断后匹配。预期集合以可遍历的键序列提供，生成量同样受单元扫描预算约束；前端 businessKey JSON 仅用于展示，不参与重新判定。

### 2. 输入与用户流程

```text
选择数据源 → 指定股票和起止日期 → 选择接口（默认全部）
→ 展示检查口径及依赖 → 开始计算 → 查看汇总 → 展开缺失与未知原因
```

创建任务请求：

```json
{
  "submissionId": "b3c4479a-1784-4db7-9627-dfdf7f323251",
  "pluginId": "tushare_pro",
  "capabilityHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "symbols": ["000001.SZ", "600000.SH"],
  "startDate": "2026-01-01",
  "endDate": "2026-06-30",
  "apiNames": ["daily", "weekly", "income"]
}
```

示例哈希仅说明格式，实际请求必须使用能力接口返回值。`apiNames` 省略时，服务端在受理时解析并保存该插件当前完整接口列表；空数组拒绝，不能在执行途中改变列表。股票去重后固定；未知接口、空股票列表、非法代码、反向日期直接拒绝。代码格式由插件解释，不在 core 中固化 Tushare 股票后缀。Tushare 首版执行 trim 和大写规范化，接受现有合法的六位代码加 SH/SZ/BJ 后缀，不能要求该股票已经存在本地表，否则会漏检整只未下载股票。

日期用严格 ISO `YYYY-MM-DD`；时间戳用 UTC ISO 格式。Tushare 业务日期按 Asia/Shanghai 解释，endDate 不得晚于受理当日；当天记录仍须单独判断是否发布。未知 JSON 字段拒绝，避免拼写错误被当成默认全选。

`capabilityHash` 由 IntegrityCheckJson 按稳定字段顺序编码，覆盖所选插件的有序 API 清单、表/列/业务键定义、检查口径及全部规则版本（包括 core 通用规则），不包含凭据或运行时间。能力变化返回 409/INTEGRITY_DEFINITION_CHANGED，前端刷新口径后重新提交，不能无提示套用新规则。执行前再次核对受理快照；不一致时停止该单元并记录 ERROR/DEFINITION_CHANGED，不用新规则执行旧任务。

`submissionId` 保证幂等：同 ID、相同受理请求返回原 checkId（200），不同请求返回 409/SUBMISSION_CONFLICT；首次受理返回 202 和 Location。重放先比对原请求的规范 JSON 哈希（对象键排序、数组顺序保留），再考虑当前能力，不因后来规则变化或“全部”范围变化重新执行。前端保存不可变提交载荷，明确再次检查时才产生新 ID。

同一日期范围对不同接口的含义由插件声明，并在提交前和结果中展示：例如 daily 为 trade_date，财务接口可能是 ann_date 或 end_date。不得从 SINGLE 的 queryMode 猜测检查日期字段，也不允许计算时静默换日期轴。当前尚未发布的日期或周期保留在请求范围中，单列“未到发布时间”及依据，不计为确认缺失。

stock_basic 等当前快照可以检查股票记录及必填字段，但不能据此证明所选历史窗口完整。margin、slb_len 等非股票级数据不强行按每只股票重复计算，显示 N/A 及原因；trade_cal 作为共享参考依赖检查。缺少实现属于 UNKNOWN，不是 N/A。

股票接口每个股票生成一个单元；NON_STOCK 接口每个 API 生成一条 symbol=null 的范围说明（N/A），不进行股票计算。已知 API 缺少描述时，为每个所选股票产生 UNKNOWN/RULE_NOT_IMPLEMENTED，不尝试猜测日期轴扫描。日历依赖验证在目标单元快照内执行，不计入用户勾选接口数，也不把其他数据集的检查结果充作目标数据完整性。计划单元数在受理时固定，保证未知、无数据和不适用项目不被遗漏。

### 3. Tushare 首版接口范围与日期轴

以下 40 个接口与当前 manifest 对齐。这里定义的是本地检验范围，不改变现有 SINGLE/RANGE 下载能力；撤回 RANGE 的接口仍可检查本地数据，报告保留其下载限制。

| scopeKind / 日期字段 | 接口 | 首版覆盖检查 |
| --- | --- | --- |
| STOCK_DATE / trade_date | daily、weekly、monthly | 输出日/周/月候选缺口，是否能确认完整取决于基线证据。 |
| STOCK_DATE / trade_date | daily_basic、stk_limit、moneyflow、margin_detail、block_trade、slb_sec、slb_sec_detail、top_list、adj_factor、suspend_d | 通用键/字段检查；缺少接口专用全集时 COVERAGE=UNKNOWN，不套用 daily 的日历预期。 |
| STOCK_DATE / ann_date | forecast、stk_holdernumber、stk_holdertrade、pledge_detail、dividend、disclosure_date、income、fina_audit、express、stk_managers、balancesheet、cashflow、repurchase、stk_rewards | 检查本地公告窗口与全部业务键；COVERAGE=UNKNOWN/EXPECTED_SET_UNPROVEN。 |
| STOCK_DATE / end_date | fina_mainbz、top10_holders、top10_floatholders、fina_indicator、pledge_stat | 检查报告期/统计截止日窗口；COVERAGE=UNKNOWN/EXPECTED_SET_UNPROVEN。 |
| STOCK_DATE / ipo_date | new_share | 按上网发行日过滤，不用上市日 issue_date 替换；没有发行全集时 UNKNOWN。 |
| STOCK_DATE / in_date | index_member_all | 按入选日期过滤，展示退出日期；只检查本地入选记录，不代表窗口内全部有效成员；COVERAGE=UNKNOWN。 |
| STOCK_SNAPSHOT / 无历史日期轴 | stock_basic、stock_company | 检查当前股票记录和字段，明确“当前快照”；历史范围完整性 UNKNOWN/HISTORY_NOT_STORED。 |
| NON_STOCK / 不适用 | margin、slb_len、index_classify、trade_cal | 用户按股票选择时给 N/A；trade_cal 另外作为行情规则的参考依赖使用。 |

当日期列允许为空时，范围扫描不能让空日期记录悄悄消失：对所选股票另查 dateField IS NULL，标记 UNKNOWN/DATE_SCOPE_UNRESOLVED，date=null，不归入任何一天或已匹配数量。范围内记录允许为空的非定位字段仍不报必填缺失；不能推断其属于当前区间。股票定位列缺失的全表问题不属于本次指定股票检查范围。

### 4. 完整性计算口径

对相同股票、接口、日期轴和可比版本，令 E 为已证明可靠的应有业务键集合，A 为本地实际业务键集合：

```text
确认缺失 = E - A
额外记录 = A - E
记录覆盖率 = |E ∩ A| / |E|
```

只在 E 可靠且非空时输出覆盖率；不能使用“本地行数 / 交易日数”，也不能用重复或额外记录抵消缺失。可信 E 为空且 A 为空时显示“已验证无应有记录”，覆盖率为 null；E 未知时，预期数量和覆盖率均为 null。

若只有部分日期或股票有可靠依据，确认缺失与未判定范围分别保留；整体覆盖率为 null，局部指标必须带上对应范围。额外记录先核对版本及范围，不能一律认定错误。

| 数据类别 | 应有记录的依据 | 依据不足时 |
| --- | --- | --- |
| 日线等规则时间序列 | 完整交易日历、股票有效上市区间、该接口的停牌/证券覆盖规则、来源服务范围与发布时间 | 输出疑似日期缺口及 UNKNOWN，不计算正式缺失率。daily_basic、adj_factor 等分别定义，不能照搬 daily。 |
| 周线、月线 | 本来源周期归属、实际交易日标记及已发布周期 | 未结束或未发布周期单列；不可固定周五或自然月末，也不能把半个月误作完整月份。 |
| 财报、分红、龙虎榜等 | 同口径可信报告/事件全集或充分验证的来源提取证据 | 展示已有记录、键和必填字段问题；没有事件不等于缺失，不设“每天/每季度必须一条”。 |
| 当前快照 | 明确股票范围、快照日期及来源覆盖约定 | 能判断当前记录存在性，不承诺历史快照完整。 |

通用扫描使用 DatasetDefinition 的完整业务键及 nullable 定义。必填字段缺失可以判 FAIL；允许为空的字段不统一算缺失。数据库键唯一不代表源端业务事件没有被错误合并，不据此宣布业务键设计正确。

现有 CONFIRMED_ROW_LIMIT、RESPONSE_ONLY 属于下载提取证据。任务成功、接口响应非空或低于工程阈值，均不能直接升级成目标范围完整。

首版核心规则固定为 `core.required-fields@1`、`core.business-key@1` 和 `core.source-identity@1`：检查定义中不可空字段、COMPOSITE 全键/FINGERPRINT 重算，以及 source_plugin/source_api。FINGERPRINT 复用 FingerprintKeyCodec，不能自己发明哈希；同日期多事件不得简化成股票加日期。数据库唯一约束只能证明本地物理键唯一，不能证明源端没有业务事件被合并。

三条通用规则分别归属 FIELD、KEY、FIELD；没有实际记录时，逐行键/字段检查返回 N/A/NO_ROWS，覆盖规则仍独立判断整只股票是否缺失或未知，不把“没有可检查行”记成数据完整。

每个适用接口都提供 `tushare.coverage.<apiName>@1`。daily、weekly、monthly 的首版执行行为固定如下：

1. SH 读 SSE，SZ 读 SZSE；BJ 需要其适用且经过验证的日历证据，首版不以 SSE 代替，缺少时 UNKNOWN/CALENDAR_BASIS_UNPROVEN。已有下载的 BJ 参考策略保持不变。
2. 验证参考日历每个自然日恰一条且 is_open 为 0/1；缺日则报告具体参考日期及 UNKNOWN/REFERENCE_INCOMPLETE，不直接认定对应股票行情缺失。
3. 日线候选为开市日期；周线按 ISO 周、月线按自然月分组，候选日期为本来源记录口径下该周期最后开市日。周/月参考日历可扩到完整周期边界，只把最终标记日期落入用户区间的周期纳入目标。当前未结束周期单列，不作为确认缺失；发布时点没有证据时保留 PUBLISH_TIME_UNPROVEN。
4. 本地 stock_basic.list_date 有值时排除明确的上市前候选日期；为空则记录 LISTING_HISTORY_UNPROVEN。停牌记录只作为解释线索，不能因一条 suspend_d 记录就删除当日预期。
5. 缺少完整生命周期、全天停牌全集、来源服务边界或发布时间证据时，候选与实际差集仅产生 SUSPECTED_MISSING，COVERAGE=UNKNOWN，expectedCount/coverageRate=null。没有候选缺口也不能自动 PASS。

首版不创建新的生产基线资料库，也不从当前下载次数推导基线可信。因此当前 Tushare 多数覆盖项会是 UNKNOWN；这是可观察的规则结果。通用 PROVEN 集合比较由可选能力支持，并用 fixture 验证；后续补齐可靠资料或接入能提供全集的来源时才能给对应生产窗口正式百分比。新增生产基线时必须更新覆盖规则版本。

### 5. 报告与问题日期

每个检查单元记录：pluginId、apiName、股票、原始范围、日期字段、有效已发布范围（未知则 null）、规则版本、定义哈希、参考依据、读取时间、实际键数、预期键数、确认缺失数、疑似缺口数、额外键数、必填字段问题数、覆盖率及判断理由。单独保留 coverageStatus、keyStatus、fieldStatus 与 overallStatus。

状态沿用现有规则文档：

Java/API 的枚举为 PASS、FAIL、WARN、UNKNOWN、NOT_APPLICABLE；表格和页面把 NOT_APPLICABLE 显示为 N/A（不适用）。

| 结论 | 含义 |
| --- | --- |
| PASS | 该项适用检查已充分执行且证据足够。字段检查 PASS 不代表范围完整 PASS。 |
| FAIL | 已确认漏键或必填字段缺失等问题，保留具体键和证据。 |
| WARN | 存在待解释异常，尚不能确认错误。 |
| UNKNOWN | 预期依据缺失、未实现、扫描失败或检查未完成。 |
| N/A | 检查确实不适用于该对象，并注明原因。 |

汇总按 FAIL > UNKNOWN > WARN > PASS 聚合，N/A 不参与；全是 N/A 时不得显示 PASS。汇总同时显示各状态数量及已检查/计划检查数量，第一版不提供跨接口的总百分比。

对实际键数、预期键数、命中数、问题计数等 `Long` 统一在 API 中序列化为十进制字符串，沿用精度保护；null 表示不可计算，不能转换成 0。coverageRate 是 0～1 的十进制字符串（BigDecimal，6 位小数，HALF_UP，仅用于展示），前端显示百分数。判定是否完整必须用精确计数，不能依据舍入后显示的 100%。

每条问题保存以下定位信息：

| 字段 | 定义 |
| --- | --- |
| ruleId / ruleVersion / type / status | 哪条规则、哪类问题及严重程度。 |
| symbol / apiName / dateField / date | 目标股票、接口和问题日期；无法定位具体日时 date=null。 |
| businessKey / field | 完整业务键与出问题的字段；缺字段或缺整行分别表达，禁止补造未知键。 |
| relatedDates | 财报同时展示 ann_date/end_date 等已存在的关联日期，主日期仍按检查轴过滤。 |
| reasonCode / message / evidence | 可机器筛选的原因、用户可读解释、参考数据集/范围/快照/摘要。 |

问题类型至少包含 MISSING、SUSPECTED_MISSING、EXTRA、REQUIRED_FIELD_MISSING、BUSINESS_KEY_INVALID、SOURCE_IDENTITY_MISMATCH、REFERENCE_INCOMPLETE、DATE_SCOPE_UNRESOLVED、RULE_EXECUTION_FAILED。确认缺失用 FAIL，疑似缺口用 WARN，同时覆盖结论维持 UNKNOWN。没有具体日期的规则/依赖异常显示“整个范围”或“日期未知”，不得把任务创建日期当成问题日期。

示例：同一股票一个已发布窗口，应有日线 20 个键、本地命中 19 个，显示“95%，确认缺失某日”；income 有 8 条但没有可靠报告全集，显示“实际 8 条，完整性无法判定”。这是示例数据，不是当前数据库实测。

### 6. 规则扩展和版本

规则第一版用 Java 代码维护。新增通用规则放 core/integrity/rules，来源专用规则放对应插件的 integrity 包，并注册到适用 API；新增规则必须给出 ruleId、version、维度、依赖、判定条件、问题定位方式和测试。规则禁止网络访问；只读合同及禁调用来源客户端的测试作为本地模式边界。

增加规则、修改阈值/适用范围/日期轴/预期集合或改变判定结果时，必须升级对应规则或能力版本，使 capabilityHash 改变。任务保存当时完整规则描述和数据集定义哈希；历史页面读取已存报告，不能拿当前描述覆盖旧规则名称、口径和结论。再次检查创建新任务，保留两个报告。第一版所有适用规则执行，不提供前端任意开关来隐藏失败项。

例如以后可添加“日线最高价不能低于最低价”的来源专用规则，但这不属于第一版验收范围，也不代表本次已经实现行情合理性检查。

### 7. 执行与持久化

使用后台任务，避免多股票、长历史扫描阻塞 HTTP 请求。第一版使用应用内有界队列和单个检查工作线程，不引入消息中间件、分布式调度或自动修复。执行器与下载任务独立，复用编码和 SQL 校验工具，不抽取通用任务平台。

- 执行状态与数据结论分开：QUEUED、RUNNING、COMPLETED、FAILED、INTERRUPTED 描述任务运行；任务正常完成后，数据结论仍可能是 FAIL 或 UNKNOWN。
- 单个检查单元报错，记录 UNKNOWN 和原因，继续其他单元；框架或报告持久化失败将任务标记 FAILED。任务预算到期标记 INTERRUPTED。应用重启时将遗留未终结任务标记 INTERRUPTED，未完成单元标记 NOT_RUN，用户重新发起新检查；不自动恢复旧快照。
- 每个“股票 × 接口”在同一个只读一致性快照内扫描目标表和参考表，确保该单元分页及集合比较一致；发布/周期判断使用该单元快照开始时间。报告记录每个单元的读取时点；不同单元不宣称属于同一个全库快照。不能把 ingested_at 截止过滤伪装成历史数据版本查询。
- 从 catalog 校验过的数据集定义生成 SQL；使用参数绑定，按完整物理主键稳定游标分批扫描，不调用前端 records 分页来充当全量检查。COMPOSITE 用完整键排序，FINGERPRINT 用存储的 business_key 排序并重算验证。扫描上限按每个单元全部目标/参考读取的累计行数计算，不因多次调用 scan 重置。
- 数据表不变，检查只写报告表。参考数据为空或不可靠时不自动下载，返回依赖缺失原因。

单元状态为 PENDING/RUNNING/COMPLETED/ERROR/NOT_RUN，与检查结论分开。扫描异常或超过预算时 ERROR、统计标明未完成且覆盖率 null，不能发布部分键集的“完整率”；已确认问题可留在明细中，但需带 incomplete=true。规则的业务 FAIL 不等于执行 ERROR。任务 COMPLETED 要求所有计划单元已产生 COMPLETED 或 ERROR 结果，汇总同时显示 errorUnits；其 overallStatus 仍按所有单元结论聚合。

配置放 `tensor.integrity`，以下为本设计采用的工程默认值，能力接口同时返回供前端预检：

| 配置 | 默认值 / 行为 |
| --- | --- |
| max-symbols / max-range-days / max-units | 100 / 36600（闭区间自然日数）/ 4000；超过则受理前 400/INTEGRITY_LIMIT_EXCEEDED。 |
| queue-capacity / workers | 20 个排队任务 / 1；满则 429/INTEGRITY_QUEUE_FULL，不留下已受理任务。 |
| scan-batch-size / max-scanned-rows-per-unit | 500 / 500000；所有规则累计，超限该单元 ERROR。 |
| max-issues-per-unit / unit-timeout-seconds | 20000 / 120；问题数量超限或执行超时则该单元 ERROR，issuesComplete=false，保留限额内明细及截断原因。 |
| task-timeout-seconds | 1800，从执行开始计时；到期停止，剩余 NOT_RUN，任务 INTERRUPTED。 |

以上值须为正，workers 首版固定为 1，非法配置阻止启动。计数允许等于上限，超过一项才算超限；时间达到 deadline 即停止。SQL 查询超时不得超过单元剩余预算，批次间检查 deadline。将原计划范围改小属于新任务，不允许为了避免触限静默删除日期或股票。第一版报告持续保留，无自动清理和删除入口。

沿用现有表名前缀，新增三张表：

| 表 | 主要字段与约束 |
| --- | --- |
| tensor_integrity_check_task | check_id UUID 主键；submission_id 唯一；request_hash、原请求 JSON、规范范围 JSON、capability_hash、规则/定义快照 JSON、执行状态、计划单元数、时间与错误。created_at/check_id 支持历史排序。 |
| tensor_integrity_check_result | result_id UUID 主键、check_id 外键、unit_key、plugin_id、api_name、symbol（可空）、日期轴、单元执行状态、各维度结论、统计、rule_results JSON、证据摘要 JSON、snapshot_started_at/finished_at、issues_complete。唯一(check_id, unit_key)，unit_key 为有序[apiName,symbol]的规范 JSON SHA-256。 |
| tensor_integrity_check_issue | issue_id BIGINT 主键、result_id 外键、规则 ID/版本、type/status、date_field、issue_date 可空、business_key JSON、field、related_dates JSON、reason_code/message、evidence JSON、incomplete。索引(result_id, issue_date, issue_id)与(result_id, type, issue_id)，按完整定位条件分页。 |

枚举状态、非负计数、开始日期不晚于结束日期及外键通过数据库约束保护。业务字段和证据不得包含 Token、任意原始响应或异常堆栈；报告只存定位和解释需要的值。

受理先处理幂等重放，再预留队列名额并在一个短事务中写任务及全部计划单元，提交后入队，失败释放名额；启动时把已提交未入队的遗留任务同样标记 INTERRUPTED。每个单元先读取只读 REPEATABLE_READ 快照，问题在上述上限内收集；结束后用单独写事务批量插入问题并提交结果，二者原子可见。前端不读取未提交的半份明细；进度按已提交单元计算。需要更多问题时用户按较小范围重新检查，不把首屏样本冒充全部。

### 8. HTTP 合同

API：

| 方法与路径 | 作用 |
| --- | --- |
| GET /api/v1/data-sources/{pluginId}/integrity-capabilities | 获取接口范围、日期轴、依赖和能力限制。 |
| POST /api/v1/integrity-checks | 校验输入、固定范围并创建任务，返回 202 与 checkId。 |
| GET /api/v1/integrity-checks | 分页查看检查历史。 |
| GET /api/v1/integrity-checks/{checkId} | 查询执行状态、进度与汇总。 |
| GET /api/v1/integrity-checks/{checkId}/results | 分页查询各股票与接口结果。 |
| GET /api/v1/integrity-checks/{checkId}/issues | 按股票、接口、问题类型分页定位明细。 |

能力响应固定包含 pluginId、localCheckAvailable、unavailableReason、capabilityHash、limits 和 apis；每个 API 返回第 1 节的描述、downloadAvailability 及检查限制。本地检查可用性不能复用 downloadAvailable，缺 Token 不影响扫描。

任务详情返回原范围、状态、overallStatus、plannedUnits/completedUnits/errorUnits/notRunUnits、各结论数量、开始/结束时间及错误；未结束时显示“进行中，已有结论”，未计算单元在总览中维持 UNKNOWN。结果接口返回第 5 节字段及各规则结果。历史仅按 pluginId/status/submissionId 筛选；前端可用唯一 submissionId 找回不确定的提交。

三种列表采用 page（默认 1）和 pageSize（默认 20，最大 100），返回 `{page,pageSize,total,items}`，超出末页返回空 items，非法页数返回 400。历史按 created_at DESC/check_id DESC；单元按 api_name/symbol/result_id；问题按 issue_date ASC（null 最后）/issue_id ASC 稳定排序。结果支持 symbol/apiName/overallStatus，问题支持 resultId/symbol/apiName/type/status/dateFrom/dateTo；日期过滤针对 issue_date，界面明确排除日期未知项，重置过滤可查看它们。列表总数和页数据在同一读取事务中获取。

复用统一 requestId 和异常响应，新增错误码 INTEGRITY_UNAVAILABLE（409）、INTEGRITY_DEFINITION_CHANGED（409）、INTEGRITY_CHECK_NOT_FOUND（404）、INTEGRITY_QUEUE_FULL（429）、INTEGRITY_LIMIT_EXCEEDED（400）。数据库失败沿用 PERSISTENCE_FAILED/QUERY_FAILED，参数沿用 PARAM_REQUIRED/PARAM_INVALID。单元 UNKNOWN 属于正常 200 报告，不使用 HTTP 500 表示业务资料不足。scope/row-limit/timeout 等内部原因另以 reasonCode 保存在结果。

### 9. 前端页面与交互

在现有工作区导航添加“数据完整性”，沿用当前布局、色彩、字体和通用表单组件；当前工程导航在顶部，无需为本功能改成侧栏。路由为 `/integrity`（创建检查与历史）和 `/integrity/checks/:checkId`（结果详情），详情刷新/深链接可以独立加载。

| 区域 | 固定行为 |
| --- | --- |
| 创建表单 | 数据源单选；股票代码支持逐个添加或按逗号、空白、换行粘贴，去重后显示数量；开始/结束日期；按分类勾选接口，默认全选，支持全选/清空。股票入口不依赖本地已有股票清单。 |
| 口径预览 | 显示股票数、接口数、日期范围及各接口日期含义；当前快照明确标记，不适用/暂无覆盖规则保留说明。缺 Token 但本地可检查时仍可提交。 |
| 提交 | 校验通过后启用“开始检查”；受理成功跳转详情。请求进行中禁止重复点击。响应不确定时保留 submissionId 和载荷，先查历史的 submissionId；需要重发时复用同一 ID，不能自动创建新任务。 |
| 历史 | 显示数据源、股票摘要、范围、创建时间、执行状态和检查结论，点击进入已存报告；不显示一个跨接口总完整率。 |
| 结果总览 | 分开显示“计算已完成/进行中/中断”和“通过/有问题/无法判定”；显示已处理单元/计划单元、执行错误数和各结论数量。 |
| 结果列表 | 一行对应股票 × 接口，非股票项一行范围说明；展示日期口径、实际/预期数量、覆盖率、确认缺失、疑似缺口及原因，支持筛选。null 显示“无法计算”，0 明确显示 0。 |
| 问题明细 | 点击“查看问题”展开分页明细，显示具体日期、完整业务键、规则名/版本、问题类型及依据；财报另显示已知公告日和报告期；可按问题日期筛选。无具体日显示“日期未知/整个范围”。 |
| 再次检查 | 从旧报告复制条件回表单，重新获取能力和规则；用户提交后生成新检查，不覆盖旧报告。 |

任务运行中每 2 秒轮询详情及当前可见结果页，同一请求未返回不发下一次；终态停止。切换路由或卸载时取消轮询，响应使用当前 checkId/request generation 校验，避免旧任务覆盖新页面。临时查询失败保留已展示结果及请求 ID，停止轮询并提供“重新连接”；恢复只执行 GET，不自动 POST。

状态同时使用文字和图标，不能只靠颜色；表单与筛选有可访问名称和键盘操作。桌面 1440/1024 验证布局，窄屏将表单和摘要纵向排列，长表格在自身容器内滚动，不让页面横向溢出。第一版无需日历热力图、规则管理页或导出功能。

### 10. 对现有工程的修改位置

| 位置（相对仓库根目录） | 职责 |
| --- | --- |
| data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/IntegrityCheckSupport.java 与 integrity/ | 可选能力；IntegrityDescriptor、IntegrityScope、IntegrityRule、IntegrityRuleDescriptor、IntegrityRuleResult、IntegrityContext、IntegrityReadRequest、IntegrityExpectedKeys、IntegrityIssue/IntegrityIssueSink 等不可变合同。 |
| data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java 与 integrity/ | 接入能力，维护 Tushare 各接口口径和依赖。 |
| data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java | 增加独立的本地检查能力查找；保持现有 find 的下载可用性语义。重复 ID 继续拒绝，显式停用插件仍不可执行。 |
| data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/ | IntegrityCheckService、IntegrityCheckRunner、IntegrityCheckRepository、IntegrityReadRepository、IntegrityCheckJson 与 rules/；通用扫描、集合比较、任务执行、报告持久化，不写 Tushare 特定业务分支。 |
| data-plane/tensor-app/src/main/java/com/akkc/tensor/web/IntegrityCheckController.java 与 config/ | 薄控制器、DTO、依赖装配及有界后台执行。 |
| data-plane/tensor-app/src/main/resources/db/migration/V9__create_integrity_check_tables.sql | 当前最新迁移为 V8，新增三张报告表，不改历史迁移；实施时若 V9 已被占用，采用下一个版本并同步本设计文件名。 |
| data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java 与 tensor-app 的 web/GlobalExceptionHandler.java | 增加完整性接口错误及 HTTP 映射，不复用消息明确限定下载任务的错误码。 |
| control-plane/src/api/integrityChecks.js、integrityDtos.js 与 composables/useIntegrityCheck.js | 请求、精确数值/状态解析、提交幂等恢复及轮询生命周期。 |
| control-plane/src/views/IntegrityCheckView.vue、IntegrityCheckDetailView.vue 与 components/integrity/ | 创建和历史页、结果页及按职责拆分的表单/结果/问题组件。 |
| control-plane/src/router/index.js、layouts/AppLayout.vue | 路由、导航、详情归属；沿用已有页面风格。 |
| data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java 与完整性 fixture 规则 | 在现有验收专用开关内提供可靠 E、缺失与未知样本；生产不启用，不创建额外真实来源调用。 |
| docs/contracts/openapi-v1.yaml、error-codes.md，docs/runbook/configuration.md、data-integrity-rules.md | 实施时同步请求/响应、错误、配置及已实现规则范围；不把规则规范全文标成自动实现。 |
| 对应模块的 src/test、control-plane/src 与 e2e/integrity-checks.spec.js | 插件兼容、规则、持久化和完整用户流程测试。 |

当前 PluginRegistry 只注册 downloadAvailable 的实例。因此必须独立判断本地检查能力：启用但未配置上游 Token 的插件，仍应可以检查已有本地数据，不能直接复用下载入口的可用性门禁。

### 11. 实施顺序

1. 落地插件能力、固定范围、规则版本和统一报告合同，先完成 fixture 的可靠/未知集合案例。
2. 打通任务、只读扫描与通用键/必填字段检查；为全部接口列出能力和日期口径，未实现项明确 UNKNOWN。
3. 为当前 40 个接口接入第 3 节明确的口径，以及 daily、weekly、monthly 的候选规则和依赖验证。缺乏充分依据时交付疑似缺口和原因，不虚构可计算的覆盖率。
4. 接入前端和历史报告。在线对账及用户选择缺口后的补数作为后续独立能力讨论。

## 如何测试

以下命令用于后续实现验证，本次设计编写不宣称这些实现测试已存在或已通过。Java 21、Node 24 与 Docker/MySQL 环境沿用项目约定；用受控 fixture 执行，不需要 Tushare Token。

```sh
mvn -f data-plane/pom.xml -Dtest='*Integrity*Test,*Integrity*IT' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
npm --prefix control-plane run build
npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js
```

后端必须新增的测试及职责：

| 测试 | 必须证明的行为 |
| --- | --- |
| IntegrityPluginContractTest | 旧插件兼容、描述/规则一致、必需列存在、无 Token 可检查、规则版本影响哈希。 |
| IntegrityComparisonTest | 可靠与未知集合、精确业务键、额外行不能抵消缺失、0/0 不产生百分比。 |
| TushareIntegrityPoliciesTest | 日期表恰好覆盖 manifest 的 40 个接口，字段存在，日期轴固定；不以 RANGE 能力代替本地检查能力。 |
| TushareIntegrityRulesTest | SSE/SZSE/BJ 依赖、日/周/月边界与停牌线索；空参考和不全生命周期保持 UNKNOWN。 |
| IntegrityReadRepositoryIT | MySQL 一致快照、游标跨页、类型/精度、日期为空的范围归属，以及非法表/列/跨范围请求拒绝。 |
| IntegrityCheckRepositoryIT / IntegrityCheckRunnerIT | 幂等受理、并发队列限制、预算、报告原子提交、故障回滚、重启中断与旧版本不重算。 |
| IntegrityCheckControllerTest | 请求校验、状态码、Location、精确计数、全部分页/筛选及错误不泄露原始异常。 |

上述目标测试预期全部通过，IT 必须实际连接受控 MySQL 执行，不能以环境缺失后的 skip 计为验收。完整回归包括既有下载、数据查看和打包：

```sh
mvn -f data-plane/pom.xml clean verify
sh scripts/verify-contracts.sh
```

必须覆盖：

- 可靠 E 有 20 个键、A 命中 19 个且额外 1 个：覆盖率 95%，明确缺失及额外键，不能算 100%。
- E 可靠为空、E 不可靠、部分范围依据不足：分别给出已验证空、UNKNOWN、局部证据；没有 0/0、伪造百分比或缩小后冒充原范围通过。
- 节假日、上市前、退市后、全天和盘中停牌、未发布日/周/月：按各接口证据判断，未知历史保留未知。
- 财报多公告版本、同日多事件、快照和交易所级接口：使用完整业务键与正确日期轴，不统一按股票交易日推导。
- 参考日历缺日、停牌表为空、当前 stock_basic 缺生命周期证据：明确依赖问题，不将参考缺失当作正常空集合。
- nullable 字段为空不报必填缺失；规则声明必填的字段为空报 FAIL，二者不混淆。
- 扫描超过一页、并发写入、单项异常、进程重启和问题明细分批落库：统计与明细一致，未完成不能 PASS。
- 旧插件不实现能力仍正常下载；本地检查不需要 Token；重复 ID 或显式停用仍拒绝。
- 前端提交前展示接口日期轴，任务完成但数据 FAIL 时正确展示，UNKNOWN 和 N/A 都不能显示成绿色完整。
- 所选股票在本地完全不存在仍可以受理，可靠 fixture 基线下列出全部缺失日期；生产基线不足时返回 UNKNOWN，不能从本地股票表先移除该股票。
- 股票、接口、任务预算、问题量达到限额与超一项分别测试；截断后覆盖率 null、issuesComplete=false，且不能把不完整明细总数写成全范围问题总数。
- 日期定位列为空时只报告范围无法确定，不归入任意一天；财报主日期过滤与 relatedDates 展示相互独立。
- 一条规则异常但另一条证明缺失：保留 FAIL 和 UNKNOWN 两项证据，overallStatus=FAIL；失败事务不能留下无结果归属的明细。
- 相同 submissionId 重发只创建一个任务；不同载荷冲突；规则升级后旧请求仍找回旧报告，新请求按新 capabilityHash 检查。
- 增加测试专用规则时只改规则及注册，核心执行器和前端报告无需新增规则 ID 分支；测试不改写生产规则定义。

前端单元测试覆盖表单范围、默认全选/部分选择、能力变化、null 与精确整数、筛选、轮询清理及过期响应；`integrity-checks.spec.js` 使用项目现有 API stub 验证页面行为，明确它不代替后端 SQL 验收。另在验收 profile 的真实后端 fixture 上完成一次“建数据 → 创建检查 → 查看缺失日 → 重查生成新报告”的浏览器闭环，核对返回明细与数据库。

桌面 1440/1024 和窄屏 390 检查可操作性，覆盖空结果、全部 UNKNOWN、全 N/A、超长股票/规则名称、提交响应丢失和任务中断。预期无页面级横向溢出、无无限轮询、无重复创建。

## 如何验证

1. 输入指定股票和日期后，报告完整保存原范围、接口清单、定义/规则版本、每个检查单元读取时点和参考依据。
2. 用固定 fixture 构造缺失键，可以从汇总定位到具体股票、接口、日期和业务键，实际数、缺失数与覆盖率可复算。
3. 对缺少充分依据的接口显示明确原因及 null 覆盖率；界面不会将“已下载”“检查运行完成”显示成“数据完整”。
4. 在没有上游 Token 和网络请求的条件下可以检查本地数据；运行前后证券数据表内容保持不变。
5. 不支持或不适用的接口仍在所选范围中解释；失败和未知不会通过自动移除股票、日期或接口被隐藏。
6. 新增来源只需实现其可选能力与来源规则，核心任务、数据库扫描和前端报告无需新增来源专用分支。
7. 由前端完成“指定股票和日期 → 默认全部或选择接口 → 检查 → 按具体日期定位问题 → 查看历史/再次检查”完整流程，结果刷新后仍可查询。
8. 修改规则版本后，新旧报告各自显示保存的版本及结论；原报告不被重算覆盖，新增规则不需要新增前端专用页面。
9. 实际日期轴表与 40 个 YAML/manifest 清单逐项一致；快照、非股票接口和暂无可靠基线的接口均有明确结果，不能通过隐藏它们得到“全部通过”。
10. 仅存在部分已完成单元、部分已扫描行或不完整问题明细时，报告明确未完成范围；确认缺失与无法判定可以同时存在。

## 依赖什么信息

- **已确认要求：** 按数据源插件设计；指定股票和时间范围；支持多股票、接口默认全部且可单选/多选；提供前端页面、具体问题日期、历史结果和后续规则扩展；第一版采用讨论方案中的本地检查。当前只交付设计。
- **关键数据缺口：** 当前 stock_basic 只有 list_date，没有 list_status、delist_date；suspend_d 的 RESPONSE_ONLY 也不能证明事件全集完整。现有本地资料不足以保证任意历史股票窗口都能算出可靠百分比。第一版应诚实显示 UNKNOWN；若产品要求这些窗口必须可算，需要单独确定可信生命周期、停牌、历史服务边界与发布时间证据的取得和保存方式。
- **参考合同：** DatasetDefinition 决定表、列、类型、nullable 和业务键；[manifest](../data-template/manifest.json)固定当前 40 个接口；完整性规则文档决定状态、集合计算及结论强度；BatchDownloadSupport 只提供采集策略证据，不能代替上述判断。
- **现有实现边界：** PluginRegistry.find 按下载可用性过滤，须增加独立本地能力查找；DatasetQueryService 的页面查询日期能力有限，须使用专门的 IntegrityReadRepository；当前表采用 upsert，没有历史时点查询能力。以上均已纳入修改范围。
- **工程风险及处理：** 本地只读快照可能较长，采用单工作线程、单元 120 秒和任务 30 分钟上限；问题量可能很大，采用单元上限、完整性标记和分页；持久报告增长通过运维观察，首版不自动删除用户报告。
- **基线证据的边界：** 可靠生命周期、停牌全集、历史服务范围和发布时间目前缺失，首版按已定义的 UNKNOWN 结果交付，不以此阻塞任务/页面/通用规则实现，也不将 fixture 的可靠基线说成真实 Tushare 数据。后续补足资料需要另行设计来源和保存方式。
- **协作边界：** 工作区已有 Studio 前端和下载任务改动；实施时以当前实际代码为基础接入，保留其他改动。设计只维护本文这一份，不创建并行冲突方案或擅自变更现有任务板。
