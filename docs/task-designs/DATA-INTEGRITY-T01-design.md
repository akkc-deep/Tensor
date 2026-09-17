# DATA-INTEGRITY-T01 插件能力、规则与报告合同

## Goal

按共享设计 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、2、5、6 节，建立插件与 core 共用的本地检查合同。用户 2026-09-16 明确要求在隔离工作区完成 T1；工作位置为 `.worktrees/data-integrity`，分支 `feat/data-integrity`。

## Scope

新增可选能力、不可变值类型、规则元数据校验、能力快照/哈希和精确报告 JSON；接入现有 acceptance fixture 的可靠与未知样例。保留 DataSourcePlugin、SINGLE/RANGE 及 fixture 下载场景。SQL 扫描、实际集合比较、通用规则执行、注册门禁、Tushare 策略、持久化、调度、HTTP 与页面由后续任务承担。

## Approach

### 插件与元数据

`IntegrityCheckSupport extends DataSourcePlugin`，提供共享设计指定的 `normalizeIntegritySymbol(String)`、`integrityDescriptor(ApiName)` 和 `integrityRules(ApiName)`。元数据查询不得访问网络；能力接口不携带凭据。

`integrity/` 使用 Java 21 record 与 enum，不引入依赖。`IntegrityDescriptor` 保存 datasetKey、ScopeKind、symbolField/dateField/dateLabel、marketZone、capabilityVersion、dependencies、rules、limitations。STOCK_DATE 必须有股票列与日期列；STOCK_SNAPSHOT 有股票列且没有历史日期轴；NON_STOCK 没有股票/日期列和执行规则。股票接口恰好一个 COVERAGE。

`IntegrityDependency` 保存 datasetKey、columns、purpose；目标依赖列在规则描述的 requiredColumns 中，参考依赖在 dependencies 中。`IntegrityRuleDescriptor` 保存 ruleId、version、displayName、Dimension、requiredColumns、dependencies、description。`IntegrityContracts.validate` 校验定义存在、目标键一致、股票列为 STRING/ENUM、日期列为 DATE、所需列存在、规则依赖是接口声明的子集、描述与实现完全一致、规则 ID 在插件内唯一。接口无描述的情况不伪造描述，由后续发现层产生 UNKNOWN。

### 范围、读取与证据

`IntegrityScope(datasetKey,symbol,startDate,endDate,acceptedAt,snapshotStartedAt)` 保留原闭区间；仅 NON_STOCK 使用 null symbol，运行前 snapshotStartedAt 可以 null。`IntegrityDateRange` 校验日期正序，供已发布范围及证据区间复用。

`IntegrityReadRequest(datasetKey,columns,equalities,dateField,dateRange,nullDates,purpose)` 只描述读取，不提供 SQL；nullDates 与日期区间互斥。投影/等值字段为标识符，条件值仅接受不可变精确标量。授权、范围扩展与预算由 T03 检查，purpose 解释参考读取用途。

`IntegrityContext.scan(request,Consumer<List<Map<String,Object>>>)` 和 `compare(IntegrityExpectedKeys)` 返回 `IntegrityStatistics`。`IntegrityExpectedKeys` 保存 scope、Basis(PROVEN/UNCONFIRMED)、可遍历完整键序列及非空证据；集合输入防御复制，惰性序列由插件保证重复遍历稳定，每个输出键复制为不可变精确标量 map。不能在构造时耗尽惰性序列，T04 比较时逐键校验 DatasetDefinition 类型、完整性及预算。

`IntegrityEvidence(source,ruleVersion,range,readAt,summary)` 强制来源、版本、区间、读取时点和摘要存在。PROVEN 表示整个原范围有可靠证据；至少一条证据须覆盖目标范围。UNCONFIRMED 可以携带局部证据，但无整体 expectedCount/coverageRate；局部确认缺失保存为独立 issue。

### 报告与数值

`IntegrityStatistics` 保存 actualCount、expectedCount、matchedCount、missingCount、suspectedMissingCount、extraCount、requiredFieldIssueCount，均为可空非负 Long。coverageRate 由 matchedCount/expectedCount 精确计算成 scale=6、HALF_UP 的 BigDecimal；expectedCount 为 null 或零时返回 null。计数关系必须成立；判定由精确计数进行。

`IntegrityRuleResult` 保存完整 descriptor、status、reasonCode、message、statistics、evidence，仅 COVERAGE 可写 expected/matched/missing/suspected/extra；FIELD/KEY 的预期及覆盖率必为空。`IntegrityIssue` 保存 type/status、symbol/apiName、dateField/date、完整 businessKey、field、relatedDates、reasonCode/message、evidence、incomplete；规则归属及 checkId/resultId 由执行器绑定，不给来源自行指定。

`IntegrityUnitResult` 保存 scope、descriptor、definitionHash、有效发布范围、unitStatus、coverage/key/fieldStatus、statistics、ruleResults、evidence、finishedAt、incomplete/issuesComplete、reasonCode/message。单元本身保存执行/未实现原因，不能伪造规则结果。overallStatus 按 FAIL > UNKNOWN > WARN > PASS 聚合，全 N/A 返回 NOT_APPLICABLE；未完成状态保持 UNKNOWN（已知 FAIL 保留），不完整结果不能携带整体预期/比例。任务运行枚举与单元运行枚举独立于数据状态。

### 稳定编码

core `IntegrityCheckJson` 的 `write(Object)` 对明确的值类型/record 递归编码，对象字段按名称排序、数组保留顺序；Long/BigDecimal 输出十进制字符串，LocalDate/Instant/ZoneId 输出 ISO/区域名，null 原样保留，拒绝浮点数、任意对象与敏感字段名。plugin-api 无 Jackson 依赖。`requestHash(Map)` 用规范 JSON SHA-256；`definitionHash(DatasetDefinition)` 覆盖整个定义。

`capabilitySnapshot(PluginId,List<ApiSnapshot>)` 仅接收有序 ApiSnapshot(definition,descriptor,referenceDefinitions,coreRules)；包括参考数据集定义，能力变化可被检测。校验同插件 API、不重复 API、描述匹配定义与全部元数据。core 三条规则描述由 `IntegrityContracts.coreRules(definition)` 提供，hash 调用必须包含各目标适用的三条通用规则，版本作为参数可测试升级。绝不把 readiness、凭据、受理或运行时点纳入能力哈希。`capabilityHash` 对同一快照 SHA-256。

### Fixture

维持 `@Profile("acceptance")` 加 `tensor.plugins.fixture.enabled=true` 双门禁。`FixtureIntegrityRules` 给 fixture_daily 一条 COVERAGE，选择 `PROVEN`/`UNCONFIRMED` 两个明确测试股票，窗口 2026-01-01..2026-01-20 共 20 个确定键。窗口外或其他股票为 UNCONFIRMED；按请求区间过滤固定集合，不将局部证据升级为全范围可靠。规则只构造 expected keys 并调用 context.compare；T01 用捕获型 context 验证其输入，真实比较与 19 命中+1额外案例在 T04。证据明确写“fixture 验收合成全集，非 Tushare 生产基线”。

## Files

- `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/IntegrityCheckSupport.java` 与 `integrity/*.java`：上述能力、合同、枚举及校验。
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckJson.java`：能力快照、稳定哈希与精确报告编码。
- `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java` 与 `integrity/FixtureIntegrityRules.java`：复用现有 fixture 注册的可选检查能力。
- `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/integrity/IntegrityPluginContractTest.java`：通过 fixture 的既有 api/core 依赖集中验证三层合同，避免添加模块间测试依赖。
- 本设计、任务看板及 `docs/verification/DATA-INTEGRITY-T01.md`：设计链接、状态、验收证据。

## Tests

1. 先新增合同测试，运行 `mvn -o -f data-plane/pom.xml -pl tensor-plugin-fixture -am -Dtest=IntegrityPluginContractTest -Dsurefire.failIfNoSpecifiedTests=false test`，确认缺少能力类型的失败。
2. 实现后同命令全部通过：覆盖 scopeKind 基数、元数据不匹配/重复/缺列、原范围与防御复制、未知/空预期、计数与舍入、优先级、超 JS 安全整数、完整财报键/关联日期、哈希版本/定义/顺序变化及 map 顺序稳定、fixture 可靠/未知/局部证据。
3. 回归 `mvn -o -f data-plane/pom.xml -pl tensor-plugin-fixture -am test`。本机 Mockito 自附加受限制时显式加入 `-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar`，不修改工程配置绕过失败。
4. `git diff --check`；`scripts/verify-contracts.sh` 要求 clean main 和专用 Maven 缓存，不符合本次保留暂存基线的隔离分支条件，记录实际门禁退出，不改脚本绕过。后端 Maven 单元回归另外运行；T01 没有数据库操作，不以其测试替代后续 MySQL IT。

## Acceptance

共享设计 T01 责任内各项验收均有断言。旧插件及 fixture 下载回归通过；规则没有 SQL/网络依赖；报告精确保留原范围、键、问题日期及证据；哈希不依赖运行时点；所有新增文件加入 Git。仅在隔离工作区更新 T01 完成状态，不自动合并原工作区。

## Risks

工作区包含创建时复制的其他任务暂存基线，保留现有暂存内容；本次只暂存本任务改动，不打包提交其他人的更改。可遍历生成器的确定性由插件实现负责，core 后续执行必须校验逐键值及累计预算。可靠 fixture 不代表生产完整性。
