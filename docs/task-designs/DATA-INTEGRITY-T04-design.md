# DATA-INTEGRITY-T04 精确集合比较、通用规则与 fixture

## Goal

在 T03 的单元只读快照内，用完整业务键比较可靠全集或候选集合，独立计算 COVERAGE、KEY、FIELD，并保留每个具体问题的日期、键、证据及规则归属。提供 T07 可直接调用的同步单元计算入口，不实现后台任务。

设计依据为共享设计第 1、4、5、6 节、规则 runbook 第 2.3、3 节，以及已实现的 T01/T03 合同。T03 验收为 18 个 unit、12 个真实 MySQL IT 和完整后端 1283 个测试通过，均无失败/错误/跳过，见 `docs/verification/DATA-INTEGRITY-T03.md`；这些是前置证据，不是 T04 的通过记录。T04 实施与实际验证结果见 `docs/verification/DATA-INTEGRITY-T04.md`，本文件保留实施合同。

## Scope

- 实现 `IntegrityContext.compare`、精确键规范化、单元问题收集、规则执行和维度聚合。
- 固定加入 `core.required-fields@1`、`core.business-key@1`、`core.source-identity@1`，其描述直接取自 `IntegrityContracts.coreRules(definition)`。
- 消费 T03 的 `ReadSession`、`TargetBatch` 与共享 `IntegrityReadBudget`；接入已有 fixture 覆盖规则的实际比较测试，并增加测试专用局部证据场景。
- 不修改 plugin-api 的 record/接口，不改变指纹编码、已有下载行为或 Tushare 规则。不实现任务受理、队列、线程、任务超时/重启恢复、报告持久化/API、Tushare 候选推导、生产基线或行情合理性规则。
- 不改 fixture 的既有生产注册口径或扩大其可靠窗口。新测试专用规则不注册到应用能力，不参与生产 capabilityHash。

## Approach

### 1. 最小结构与 T07 装配入口

新增 `core.integrity.IntegrityUnitEvaluator`，构造参数为 `IntegrityReadRepository` 和 `Clock`。公开入口固定为：

```java
Evaluation evaluate(
    IntegrityScope scope,
    IntegrityReadPlan plan,
    DatasetDefinition definition,
    List<IntegrityRule> sourceRules,
    IntegrityReadBudget budget,
    int batchSize,
    int maxIssues);

record Evaluation(IntegrityUnitResult result, List<BoundIssue> issues) {}
record BoundIssue(String ruleId, String ruleVersion, IntegrityIssue issue) {}
```

`Evaluation`、`BoundIssue` 为 evaluator 的公开嵌套 record；列表防御复制。T07 在报告写事务之外调用此入口，传入受理时核对过的定义、来源规则、计划及预算，接收完成/错误单元和有归属的问题，然后原子持久化。checkId/resultId/issueId 由 T05/T07 赋予；来源规则不接触这些 ID。T04 不另建任务状态机、线程或规则注册平台。

入口要求 scope 未带快照时间、股票单元、definition.datasetKey 与 plan.target 一致，maxIssues/batchSize 正数，来源描述与 plan.target.rules 完全相同且无 ID 冲突；非法调用在开快照前抛 `IllegalArgumentException`，属于调用合同错误。元数据发现/跨数据集合同已由 T01/T02 校验，读取授权仍完全由 T03 校验。NON_STOCK 和缺描述单元由 T07 按已有合同直接生成 N/A 或 UNKNOWN，不能调用本入口猜测扫描。

执行步骤：

1. 建立本次调用私有的累计器/问题收集器。调用既有 `withSnapshot(scope, plan, budget, batchSize, action)`，保存 `session.scope()` 的真实快照时点。
2. `session.scanTarget` 恰好一次，缓存不可变的范围内行和 `dateScopeUnresolved=true` 行，保持物理游标顺序。读取行由 T03 计费，不重复给缓存遍历收取扫描行数；缓存大小受同一个 maxItems 上限约束，首版不实现磁盘归并。缓存只活到本次 evaluate 返回。
3. 扫描结束后独立建立不可变键索引及通用规则输入。业务键验证异常逐行转 KEY 问题，不在 JDBC callback 内抛出可恢复的业务异常；真实读取异常仍中止单元。缓存构建与其后 CPU 循环逐项 `budget.check()`。
4. 合并三条 core 规则和 sourceRules，按 `ruleId` 字典序执行。每项建立专属 context 和 sink，执行结束立即关闭，禁止保存后再调用。core 规则由构造参数获取同一份不可变目标输入；来源仅拿到 plugin-api 的 context/sink。不存在“前一条规则先填充某个可变列表，后一条才能运行”的依赖。
5. 聚合每条返回值和实际收集的问题，生成单元数据。action 返回后仍须等待 `withSnapshot` 完成其预算检查、会话检查及回滚/释放；成功返回前不发布结果。
6. 在 `withSnapshot` 外统一捕获 `IntegrityReadException`，包括 action 已返回但预算/会话/清理失败，转单元 ERROR。已有问题按下文保留；不重开快照、不以空集合继续比较。

新增包内 `DefaultIntegrityContext implements IntegrityContext`，构造参数绑定 session、当前规则描述、不可变目标输入、definition、同一个 budget 与当前规则 sink。`scan` 委托 `session.scan`，不跨规则缓存参考读取、不改变授权、不重置预算。`compare` 仅允许当前 COVERAGE 规则调用；每次规则调用最多一次 compare，重复调用为规则合同错误，避免重算和重复输出。来源规则可以先自行 scan 参考数据，也可以不调用 compare 而直接返回 UNKNOWN。

一个 evaluator 实例可以复用，但不保存任何跨单元集合/问题状态。context/sink 只允许由创建它们的线程在当前规则调用期间使用；关闭后调用立即拒绝，不允许异步逃逸。其状态属于框架生命周期，不是来源规则间通信通道。

### 2. 业务键合同与规范化

新增 `public final IntegrityBusinessKeys`，统一服务实际行、预期键和 `integrity.rules` 子包的业务键规则；不写第二份指纹 codec。构造器接收 DatasetDefinition，公开 `normalize(Map<String,Object> row, boolean expected)` 返回嵌套不可变 `Key(List<Object> values, Map<String,Object> fields)`；`values` 用于相等/哈希，`fields` 用于问题定位及调用 codec。expected=true 执行恰好完整键字段校验，false 从实际行提取。目标行输入直接用已有 `List<IntegrityReadRepository.TargetBatch>` 防御复制传给三条core规则，不创建另一套公共输入层。

- 键字段顺序严格为 `definition.businessKey().fields()`，按对应 `ColumnDefinition` 校验。预期 map 必须恰好含完整业务键字段，含物理 `business_key`、缺字段或多字段均拒绝；实际行允许带全部数据列，只提取业务键字段。map 顺序不影响相等。
- COMPOSITE 所有键字段非空，即使列 nullable 也不能缺键。FINGERPRINT 输入按各列 nullable 允许显式 null，缺 map 字段不等于合法 null；物理 `business_key` 必须存在且重算一致。
- STRING/ENUM 按现有 `ValueConverter` trim，空串归 null；保留大小写和内部空格，STRING 长度按 Unicode 码点，ENUM 校验 allowedValues。TEXT 保留原字符串。MONTH 使用现有合法年月格式。DATE 边界为 `LocalDate`，直接按日期比较，不将其转为任意时区时间戳；MONTH 的 `YearMonth` 可按现有转换格式适配后校验。
- LONG 接受合同可表示的整数并归一到 Long，必须在有符号 64 位内；禁止 Float/Double 或不精确强转。DECIMAL 归一为 `BigDecimal.setScale(column.scale(), UNNECESSARY)` 后检查 precision，`1.0`/`1.00` 在 scale=2 下均为 `1.00`，`1.001` 不截断。转换委托 `ValueConverter` 可复用分支；它的 DATE 入口期待源字符串，不能把 LocalDate 直接传给该分支。仅适配已知逻辑类型，不采用普遍 `toString()`。
- 内部比较 token 使用按上述顺序规范化后的完整值列表；列表必须容纳 FINGERPRINT 合法 null（不能使用 `List.copyOf`）。DECIMAL 统一到声明 scale 后再 equals/hashCode，等价于按数值比较。外部 businessKey 保存完整规范化 map，不通过展示 JSON 或物理哈希重新判断相等。
- FINGERPRINT 用相同的规范化值 map 调用现有 `FingerprintKeyCodec.sha256(fields, row)`。DECIMAL 保持声明 scale，绝不把 stripTrailingZeros 后的表示传给 codec；现有 codec 使用 `toPlainString()`，改写它会破坏已落库键。比较按逻辑完整键，物理指纹损坏另报 KEY/FAIL，不让错误物理哈希制造第二个业务事件。
- 合法相同键重复出现只计一个集合成员；每个输入项仍计入预算。实际物理多行若规范化后碰撞，`core.business-key` 对后续碰撞行给 `BUSINESS_KEY_INVALID/NORMALIZED_KEY_COLLISION`，不任选一行作为通过证据。不得把数据库唯一性表述为“源端事件没有被合并”。

预期键的 scope 必须与 session.scope 完全一致（包含受理及快照时点）；证据中的读取时间必须与本次快照相符。若股票/检查日期轴本身在业务键中，还须验证股票一致、日期在原范围且非空。部分数据集如 new_share 的日期轴不在业务键中，此时完整预期键不能凭空补日期；scope 和证据表达范围，来源负责产生适用键，缺失问题 date=null。不扩展 T01 以强塞非键字段。

预期键非法是规则错误：`UNKNOWN/RULE_EXECUTION_FAILED`，解释为 `INVALID_EXPECTED_KEY`，不得产生截断/修复后的匹配。先全部有界遍历并校验 E，再产生集合差异；迭代到后半段才失败时，不留下该次 compare 的半份确认缺失或有效整体统计。此前规则已明确发出的局部问题可以保留。

### 3. compare 的集合与统计

先取范围内、业务键可规范化的唯一实际键集合 A。空日期批次永不进入 A。完整键无法取得的实际行保留具体 KEY 问题；actualCount 是已扫描到的有效唯一业务键数，不是物理行数，不能用非法行补足命中。此时 COVERAGE 至少 UNKNOWN，不能因剔除后的集合看似完整给 PASS/正式比例；KEY/FAIL 和已确认问题仍保留。

遍历 expected.keys 时，在每次 iterator.hasNext/next 前后检查时间，每个产出键在去重前 `budget.consume(1)`；循环结束再 check。即使重复键/非法键也不免费。无穷重复序列会触限；不在构造阶段耗尽惰性 iterable。只遍历一次，不预先调用 size/count。

| 情况 | actual / expected / matched | missing / suspected / extra | 结论与问题 |
| --- | --- | --- | --- |
| PROVEN 且目标键/日期可完整确定 | `|A| / |E| / |A∩E|` | `|E-A| / 0 / |A-E|` | 每个 E-A 为 MISSING/FAIL；每个 A-E 为 EXTRA/WARN；无差异 PASS |
| PROVEN，E=A=空 | `0 / 0 / 0` | `0 / 0 / 0` | PASS/VERIFIED_EMPTY，coverageRate=null |
| PROVEN，E=空、A非空 | `|A| / 0 / 0` | `0 / 0 / |A|` | EXTRA/WARN，比例仍 null |
| UNCONFIRMED | `|A| / null / null` | `null / |E-A| / null` | 每个候选差异 SUSPECTED_MISSING/WARN；COVERAGE 至少 UNKNOWN/EXPECTED_SET_UNPROVEN，不判 EXTRA |

UNCONFIRMED 即使所有候选都命中、E 或 A 为空仍是 UNKNOWN；候选数/候选命中可写证据摘要，不冒充正式 expected/matched。`requiredFieldIssueCount` 在比较阶段为 null，由通用规则聚合赋值。缺失/额外依据完整键，不按股票日期投影去重。

比较使用 long 精确计数，完整性由差集是否为空决定。最终 coverageRate 只调用已有 `IntegrityStatistics.coverageRate()`（6 位 HALF_UP），不添加另一套除法；例如 2,000,001 个预期、2,000,000 个命中即使显示 `1.000000` 仍 FAIL。

发现空日期或不可规范化键时，正式 expected/matched/coverageRate 置 null；`actualCount` 保留有效范围内键数。完整可定位 E-A 仍可输出具体缺失，A-E 仍可按可靠全集说明额外，但若某缺失键与空日期行的完整逻辑键相同（日期轴不在键中），该键只能作为疑似缺口，不能把范围未知行算命中或断言不存在。对于不可规范化实际键，不能证明与之无关的差异为确认缺失，降为疑似缺口；证据明确说明键损坏导致比较无法完成。上述已读完全但语义未知不是执行中断，单元仍 COMPLETED、incomplete=false。

### 4. 局部确认与未知共存

T01 没有“部分 PROVEN 子集”的 compare 方法，不能用较小 scope 调 compare 后把其比例套到全范围。局部证据由来源规则在同一快照独立扫描、验证并 `issues.add(MISSING/FAIL)`，带局部 evidence.range；整体 compare 仍传原 scope、UNCONFIRMED。来源规则不能读取此前其他规则的内存结果来确定局部缺失。

收集器对同一规则、同一完整规范化键及问题主日期，将已确认 MISSING 优先于 SUSPECTED_MISSING；无论两者到达顺序如何只保留 MISSING，避免同一缺口双计。不同规则或不同字段的问题不合并。业务问题去重标识为 ruleId/version、type、dateField/date、规范化完整 businessKey、field；无键的范围异常再加 reasonCode 和证据，避免抹掉不同参考范围。

聚合时 UNCONFIRMED 的 missingCount 在没有局部确认时为 null；存在局部确认则填唯一确认缺失问题数（已知下界），suspectedMissingCount 排除被确认升级的键。expectedCount/matchedCount/extraCount/coverageRate 继续 null，证据明确“仅确认局部，其他范围未知”。规则原始 UNKNOWN 及 reason/evidence 保留；该 COVERAGE 维度因实际 MISSING/FAIL 升为 FAIL，overall=FAIL，不能因此隐藏 EXPECTED_SET_UNPROVEN。

### 5. 三条通用规则

新增 `core.integrity.rules.RequiredFieldsRule`、`BusinessKeyRule`、`SourceIdentityRule`，均实现 `IntegrityRule`。构造器接收 definition 与不可变目标输入；同步入口通过四参数重载同时传入共享预算检查和 descriptor.dateField（快照为 null），保证逐行 CPU 检查和明确日期轴。按行处理范围内及空日期批次。它们各自可独立执行、互相无结果依赖，不能调用 compare。描述完全使用 T01 已哈希的 coreRules 三项，版本仍为 `1`。

| 规则 | 检查及问题 | 统计/无行 |
| --- | --- | --- |
| required-fields / FIELD | 仅 definition 中 nullable=false 的列；实际 null，以及 STRING/ENUM trim 后的空值，逐行逐字段报 REQUIRED_FIELD_MISSING/FAIL，field 为该列。不把 nullable 数值、文本等空值报必填，不加业务域/财务规则。 | requiredFieldIssueCount 为问题数，其他覆盖计数 null；一行缺两个字段为 2。完全无实际行 N/A/NO_ROWS、字段问题数 0。 |
| business-key / KEY | COMPOSITE 完整非空键、类型/长度/枚举/精度；FINGERPRINT 输入合法 nullable 与物理指纹存在/匹配；规范化碰撞。失败 BUSINESS_KEY_INVALID/FAIL，reason 为 KEY_FIELD_MISSING、KEY_VALUE_INVALID、FINGERPRINT_MISMATCH 或 NORMALIZED_KEY_COLLISION，field 指向具体列或 business_key。 | 覆盖计数及 requiredFieldIssueCount 均 null；无行 N/A/NO_ROWS。 |
| source-identity / FIELD | 每行 source_plugin/source_api 分别精确等于目标 datasetKey 的 pluginId/apiName；缺失也为 SOURCE_IDENTITY_MISMATCH/FAIL，逐错误字段定位。不把 source 值作为目标身份覆盖。 | requiredFieldIssueCount 不计来源问题，保持 null；无行 N/A/NO_ROWS。 |

有行且该规则没有问题为 PASS；只有空日期行也属于“有实际行”，三条规则仍执行，不能返回 NO_ROWS。同一非空键字段为空可以同时有 FIELD 必填问题与 KEY 问题，维度各司其职。T03 在数据库读取时已经拒绝超精度、类型损坏等不可交付值；此类真实读取失败必须 ERROR，不能在 T04 假装收到行再吞掉。业务键规则的非法值分支用纯规则测试覆盖，真实 MySQL 用可存储的空串/损坏指纹验证具体问题。

### 6. 问题归属、日期和聚合

`IntegrityIssueCollector` 为包内、单元私有；`forRule(descriptor)` 返回仅在当前调用有效的 sink。所有 compare、core、来源问题通过它绑定 ruleId/version，来源无法指定另一规则。校验 symbol、apiName、dateField 与目标一致；股票/接口错归属为当前规则 RULE_EXECUTION_FAILED，不接受该问题。规则返回 descriptor 也必须完全等于正在执行的描述。

问题主日期按 descriptor.dateField：实际行取该列 LocalDate；缺失取预期完整键内已知的该列，没有则 null。其他已知 DATE 列放 relatedDates（排除主轴，值只取真实行/完整预期键已提供值）；income 的 end_date 不替换 ann_date。参考问题可以使用目标 dateField 并以参考日作 issue.date，evidence 标明参考数据集/区间；问题仍归目标单元。范围异常 date=null，不取受理时间或今日。非法键保留真实可取得且报告合同能精确表示的原始值；无法表示的值记 null，实际缺少的字段不补造。有效值按统一合同规范化。MISSING/SUSPECTED_MISSING/EXTRA 即使键 map 为空也必须验证完整键，键内股票/日期须与问题归属、原范围一致；参考异常的日期仍按前述参考规则处理。

每条空日期实际行产生 DATE_SCOPE_UNRESOLVED/UNKNOWN、date=null、field=目标日期列，完整键与其他已知日期保留。该问题由框架绑定到本接口唯一 COVERAGE 规则，不新造 core 规则 ID；仅输出一次，不因三条通用规则重复。空日期问题强制 COVERAGE 至少 UNKNOWN，expected/matched/coverageRate=null；不阻止 KEY/FIELD 各自 PASS 或 FAIL。

按 `IntegrityStatus.aggregate` 聚合当前规则返回 status 和该规则全部问题 status；聚合为对应维度，再聚合 overall。无规则的维度为 NOT_APPLICABLE，全 N/A 不为 PASS。UNCONFIRMED、空日期、键无法确定等 UNKNOWN 依据作为覆盖维度的约束，不能被来源返回 PASS 覆盖；FAIL 优先。

单元统计只消费唯一 COVERAGE 的比较统计及其局部问题，绝不累加各规则 actualCount；actualCount 来自共享不可变实际键索引。requiredFieldIssueCount 只数 REQUIRED_FIELD_MISSING 问题，不把 source identity/KEY 问题算进去。来源直接返回统计时须验证 actualCount 与已读索引一致、正式计数满足 T01 不变量；compare 已调用时不能以另一套返回统计覆盖框架已算差集。来源不调用 compare、也无可靠证明时不允许构造正式 expected/coverageRate。

正常结束 `unitStatus=COMPLETED, incomplete=false, issuesComplete=true`，定义哈希取 `IntegrityCheckJson.definitionHash(definition)`，scope 使用真实快照 scope，finishedAt 取注入 Clock。`publishedRange=null`：现有规则返回合同没有结构化发布范围，本任务不从 evidence.range 猜测“已发布”。证据汇总保留来源证据及读取范围/时点的安全摘要，不含 SQL、原始响应、凭据或堆栈。

### 7. 失败、累计预算与问题上限

- 普通来源运行异常、非法 expected 或错误规则返回：当前规则 UNKNOWN/RULE_EXECUTION_FAILED，增加同类型的范围问题（date=null、键为空），安全消息不含异常原文/堆栈。继续其他独立规则；已确认业务问题不删除、不降级。Java `Error` 不作为正常业务异常吞掉。
- `IntegrityReadException`（任何 reasonCode）、预算耗尽、问题上限均为单元级终止，不能被普通 catch 改成单条规则 UNKNOWN 后继续。规则前后、scan/compare 前后、逐键/行/问题及聚合阶段检查共享 budget 与收集器终止状态；来源自行 catch 了异常也必须在 evaluate/withSnapshot 出口再抛出。若问题上限异常发生于来源scan callback而被T03包装为READ_FAILED，外层仍优先采用收集器已锁存的ISSUE_LIMIT_EXCEEDED，不能丢失真正终止原因。
- `maxIssues` 对去重/缺失升级后的保留问题计费；恰好上限允许，第一个新问题超过时锁存 `ISSUE_LIMIT_EXCEEDED`，保留上限内问题，停止整个单元，`issuesComplete=false`。不得为了写一个截断问题挤掉已有明细，截断原因放单元 reasonCode/message。累计消费的扫描/预期键预算从不回退。
- 单元 ERROR 时 `incomplete=true`，单元及保留的规则子结果中 expectedCount/matchedCount/coverageRate 均为 null；不得公布部分遍历的 actual/missing/extra 为完整总计。actualCount 只在目标缓存确实完整构建后保留，否则 null；已确认缺失/字段问题可保留为明细及已发现下界并明确未完成，未知总数为 null。全部已保留 issue 复制成 incomplete=true，已有 FAIL 仍使对应维度/overall=FAIL，未完成维度至少 UNKNOWN。
- ISSUE_LIMIT_EXCEEDED 和 UNIT_TIME_BUDGET_EXHAUSTED 均 issuesComplete=false。其他读取失败若可能还有未产出问题也为 false，不声称明细完整；现有范围已确定的 FAIL 继续显示。未启动/未返回的规则不伪造 PASS 结果，维度补 UNKNOWN。
- withSnapshot 尚未建立就失败时保留原 scope（snapshotStartedAt=null），不得虚构读取时点。快照清理失败按同一 ERROR 规则降级，已算出的成功结果不可提交。

## Files

所有路径相对于 `.worktrees/data-integrity`；实施时新增文件加入 Git，只暂存本任务文件。

| 路径 | 责任 |
| --- | --- |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityUnitEvaluator.java` | 同步入口、快照生命周期、规则调用/聚合、嵌套 Evaluation/BoundIssue |
| 同目录 `DefaultIntegrityContext.java` | 框架 context、scan 委托、一次 compare 和生命周期 |
| 同目录 `IntegrityBusinessKeys.java` | 类型规范化、公开不可变 Key token、指纹复用；目标行输入直接复用已有 TargetBatch，内部索引保持包内 |
| 同目录 `IntegrityIssueCollector.java` | 规则归属、问题去重/升级、锁存上限、未完成复制 |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/rules/RequiredFieldsRule.java` | 必填字段 |
| 同目录 `BusinessKeyRule.java`、`SourceIdentityRule.java` | 键及来源身份；只暴露构造所需输入，不引入任意 SQL |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityComparisonTest.java` | 集合/精度/局部证据/问题聚合/规则失败/预算，使用真实 compare，不 mock 统计返回 |
| 同目录 `IntegrityCoreRulesTest.java` | 三条 core 规则的逐行定位与独立执行 |
| 同目录 `IntegrityComparisonIT.java` | 实际 MySQL + ReadRepository + evaluator，验证合法范围及失败传播 |
| `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/integrity/FixtureIntegrityComparisonTest.java` | 直接执行已有 FixtureIntegrityRules 与真实 core 比较；不捕获 expected 后伪造结果 |
| `docs/verification/DATA-INTEGRITY-T04.md` | 实施后记录真实命令/结果/边界；此设计阶段不创建通过证据 |

不修改 T01 plugin-api、FingerprintKeyCodec、T03 ReadRepository/ReadPlan/ReadBudget 及 FixtureIntegrityRules 生产代码。若实施揭示真实既有合同冲突，先报告具体输入和冲突位置，不以暗改这些文件扩大 T04。

## Tests

### 精确测试输入与断言

统一固定 acceptedAt=`2026-09-16T00:00:00Z`，快照时点由测试 Clock 固定为同日稍后时间；默认股票 `PROVEN`，用真实 definition/descriptor，读入 Map 保留 null。单测可以提供真实行的 ReadSession fake 来控制输入，但必须执行生产比较器、规则和聚合器，禁止 context.compare 直接返回预写 Statistics。

| 场景 | 输入 | 必须断言 |
| --- | --- | --- |
| 20/19/1 | 测试专用 COVERAGE 规则，原范围 `2026-01-01..2026-01-21`，PROVEN 证据覆盖此范围；E=Jan1..Jan20，A=Jan1..Jan19 加 Jan21（均同股票）。 | actual=20、expected=20、matched=19、missing=1、extra=1、suspected=0、比例 `0.950000`；Jan20 MISSING/FAIL，Jan21 EXTRA/WARN，coverage/overall FAIL，两键都在 scope。MySQL IT 以 batchSize=2 再验一次。 |
| 可靠空集合 | 同 scope，测试规则 E 空；分别 A 空、A={Jan1}。 | 前者 PASS/VERIFIED_EMPTY、0计数、比例null、core三项NO_ROWS；后者 extra=1/WARN、比例null。 |
| 未知集合 | 与20/19/1相同E/A但 basis=UNCONFIRMED。另测全部20候选命中及空候选/空实际。 | expected/matched/missing/extra/比例null；差集Jan20仅SUSPECTED_MISSING/WARN；Jan21无EXTRA；coverage/overall UNKNOWN；无差集也不得PASS。 |
| 现有可靠fixture | 直接已有FixtureIntegrityRules，PROVEN，scope Jan1..20，A Jan1..19。 | E20、matched19、missing1、extra0、0.950000、Jan20确认缺失；证据含“非Tushare生产基线”。不能将Jan21行塞进此范围制造额外。 |
| 现有未知fixture | UNCONFIRMED股票同窗口、A Jan1..19；另PROVEN股票scope Jan1..21。 | 前者Jan20疑似，后者整体仍UNCONFIRMED；expected/比例均null，固定窗口不被升级。 |
| 局部确认 | 测试专用规则原scope Jan1..21，候选Jan1..20，A Jan1..18；同一规则通过独立scan确认Jan19缺失，局部证据Jan19..19，再compare UNCONFIRMED。互换add与compare顺序。 | Jan19一条MISSING/FAIL、Jan20一条SUSPECTED/WARN，missing=1、suspected=1、expected/matched/extra/比例null；rule保留EXPECTED_SET_UNPROVEN证据、coverage/overall FAIL；结果不随到达顺序改变。 |
| DECIMAL与精确Long | 键含DECIMAL(6,2)、LONG；E amount=1.0，A amount=1.00，LONG=9007199254740993。分别COMPOSITE/FINGERPRINT。 | 一个匹配，无差异；JSON Long为十进制字符串；FINGERPRINT期望值严格等于现有codec对1.00算出的hash，不改变已存指纹。 |
| 非法预期 | amount=1.001、10000.00(超6位precision)、缺键、额外键、错股票、键中日期越scope；惰性序列最后一项非法。 | 当前规则UNKNOWN/RULE_EXECUTION_FAILED且解释INVALID_EXPECTED_KEY；expected/比例null，无半份compare差异；其他独立规则仍执行。 |
| 完整多维键 | 财报 `(ts_code,end_date,report_type,ann_date)`：同end_date保留两个report_type、两个ann_date；事件同股同日不同buyer/seller/price/vol。 | 删除一个完整版本只报该完整键；不按股票日期合并；财报主日期ann_date，relatedDates保留end_date。 |
| 指纹nullable与碰撞 | FINGERPRINT nullable键值null且物理codec正确；同值损坏物理hash；字符串组合`["ab","c"]`与`["a","bc"]`；规范化后相同完整键的两行。 | 合法null通过；坏hash KEY/FAIL定位business_key；两个字符串组合不冲突；实际规范化碰撞有KEY问题，actual按唯一键计。 |
| 空日期 | nullable日期轴不在COMPOSITE键内（键ts_code+event_id）：scope Jan1..2，范围内event a/Jan1，空日期event b；E仅a，或E含b。 | 空日期不入actual/matched，不报Jan1/Jan2；一条DATE_SCOPE_UNRESOLVED、date=null、键b保留；expected/matched/比例null、coverage UNKNOWN；E含b时只疑似，不确认b缺失。通用规则执行b，不误NO_ROWS。 |
| 必填/来源 | 行date=Jan1，required_name=null、nullable note=null，source_plugin错误、source_api正确；另测试required_name="  "。 | required-field仅name一条、FIELD FAIL；note无问题；source错字段单独issue；requiredFieldIssueCount=1，不因来源问题变2。 |
| 异常返回与独立性 | 一来源规则先emit MISSING/FAIL后返回PASS；另一独立FIELD规则抛普通运行异常；打乱输入规则列表，并分别独立运行core规则。 | FAIL保留、异常规则UNKNOWN且后续执行；稳定按ruleId输出；同输入每条规则独立结果相同，无前规则缓存依赖。 |
| 问题归属/显示 | 来源试图emit OTHER股票或api；DATE轴缺失于预期键；快照scope。 | 拒绝错归属，绑定当前规则的失败；缺失问题date=null且无补造日期；scope原窗口、快照时间、所有ruleId/version准确。 |
| 精确舍入 | 直接统计matched=2000000、expected=2000001、missing=1，并通过已知MISSING问题驱动聚合。 | 展示`1.000000`，overall仍FAIL；不必为此显示测试生成两百万键突破预算。 |
| 累计预算 | 目标2行、来源参考1行、预期2项（其中重复也计费），maxItems=5成功；改4失败。另两次scan各读2行，不能重置额度。 | 上限等于允许；超限ERROR/SCAN_LIMIT_EXCEEDED、incomplete、比例null；规则自行catch仍不能成功；IT实际扫描验证至少一次。 |
| 时间与迭代失败 | Clock推进到deadline，发生于预期生成、规则返回之后、withSnapshot清理之前；迭代器普通异常另测。 | 时间到点ERROR、issuesComplete=false、保留已知FAIL且问题incomplete；普通迭代异常只当前规则UNKNOWN，不能等同空E。 |
| 问题数量 | maxIssues=2，两个不同缺口允许；第三个不同问题触限；重复问题及suspected升级confirmed不新增一格。 | ISSUE_LIMIT_EXCEEDED、ERROR、只保留2条、issuesComplete=false、不添加第3条“截断问题”、无整体比例。 |
| 读失败/回调失败 | 真ReadRepository扫描异常，及source scan callback抛异常；或规则catch扫描异常后返回PASS。 | T03锁存失败传出，整个单元ERROR，其他规则不继续；不得包装成可恢复规则失败；快照关闭后上下文不可用。 |

`IntegrityComparisonIT` 复用现有 `IntegrityReadRepositoryIT` 的 MySQL 8.4.6/Testcontainers 建表与精确类型方式，在本测试类内建最小数据集，不抽取跨项目测试框架；除20/19/1，至少覆盖空日期、损坏指纹、目标+参考+预期累计预算与超限后的证券表逐行不变。IT 必须实际启动 MySQL、无skip；fixture的实际业务规则通过 fixture 模块测试执行，其既有双开关/下载兼容由回归覆盖。

### 命令及预期结果

先新增有意义的集合/规则失败测试，观察尚未实现入口或行为失败；实现后运行：

```sh
mvn -o -f data-plane/pom.xml -pl tensor-core,tensor-plugin-fixture -am \
  '-Dtest=IntegrityComparisonTest,IntegrityCoreRulesTest,FixtureIntegrityComparisonTest,IntegrityPluginContractTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityComparisonIT,IntegrityReadRepositoryTest,IntegrityReadRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test

git diff --check
git diff --cached --check
```

预期所有所列测试实际执行、0失败/错误/跳过、BUILD SUCCESS，空白检查退出0。完整test默认不包含IT，以显式MySQL命令为准；核对各模块surefire报告并将实际数量记入T04验收，不预填数量。保留现有暂存基线，`scripts/verify-contracts.sh` 的 clean main 门禁不适用于此隔离分支；不修改门禁、不声称其已通过。

## Acceptance

1. T07 可直接调用上述 evaluate，获得不可变单元结果及带规则归属的问题；任务调度/持久化仍未进入本任务。
2. 合法范围内20/19/1、可靠空集、未知全集、局部确认与未知并存均有生产比较器断言；extra不抵消missing，舍入不决定PASS。
3. 精确类型、声明scale指纹、多维财报/事件键、nullable、空日期、来源身份和无行语义按上表验收；不改已有codec或T01合同。
4. 问题日期/关联日期/规则归属准确，已知FAIL不被返回PASS或其他UNKNOWN覆盖；问题与预算上限锁存，读取失败不发布半份整体统计。
5. 来源与core规则独立消费只读输入，来源网络客户端从未调用；MySQL实际只读读取、证券数据不变，所有目标/参考/生成键累计预算有证据。
6. 新测试、真实IT和完整后端回归按实记录；新增文件加入Git；验收明确可靠fixture只是合成全集，不代表Tushare生产完整率。

## Risks

- 有界目标缓存最多保留预算内的实际行及完整键，宽表会占用内存；不为本项引入外部排序或缓存平台。一次单元结束释放，既有500000上限仍生效；若压测发现不可承受，作为明确后续容量决策，不静默缩小范围。
- T01没有结构化“局部指标/发布范围”的返回扩展；本设计用既有局部证据+issues表达局部确认，publishedRange保持null，不扩合同、不将证据范围冒充发布时间。
- 现有fixture的可靠Jan1..20窗口已经包含所有日键，不能在同范围构造第21个日期额外键；20/19/1用单独测试规则和Jan1..21可靠scope，既有fixture另验。此为测试输入约束，不是待决定需求。
- 当前读取器对物理类型/精度损坏会先ERROR，T04不能保证所有损坏行均有逐字段问题；精确拒绝优先，具体合法可读取问题有KEY/FIELD定位。
- 未发现必须修改T01的合同冲突；无待用户确认的实现决定。实施若出现新冲突，应报告并更新设计，不自行扩大范围。
