# DATA-INTEGRITY-T08 Tushare 日周月候选缺口规则

## Goal

在固定股票和日期范围内，用本地交易日历推导 daily、weekly、monthly 的候选业务键，定位疑似缺口，并保留参考资料、生命周期、停牌与发布时点不足的 UNKNOWN。规则不访问上游，不宣称生产预期全集可靠。

## Scope

接入 T02 的三个行情覆盖规则和 T07 的来源参考读取 hook；复用 T03 同快照授权、T04 UNCONFIRMED 精确比较及问题收集。其余 37 接口保持原日期轴、限制和覆盖行为，四项 NON_STOCK/两项 STOCK_SNAPSHOT 的合同不变。不改下载日历/BJ 策略、数据库/YAML、HTTP/页面、core 调度、证券数据或生产基线资料库。

## Approach

### 1. 接口、注册与版本

新增插件包内 `TushareMarketIntegrityRule implements IntegrityRule`，构造参数 `(DatasetKey datasetKey, IntegrityRuleDescriptor descriptor)`；实现既有 `descriptor()`、`evaluate(scope, context, issues)`。仅 daily/weekly/monthly 注册它，其他接口仍使用 `TushareIntegrityCoverageRule`。

`TushareIntegrityPolicies` 新增 `List<IntegrityReadRequest> referenceReads(IntegrityScope scope)`；`TushareProPlugin.integrityReferenceReads(scope)` 直接委托它。纯本地规划，null/异源/非法股票按既有参数合同拒绝；非行情接口返回空列表。规则和 hook 共享一个包内静态参考请求生成方法，避免请求与许可使用不同的窗口、交易所或 purpose。依赖列/用途直接复用现有 `MARKET_DEPENDENCIES`，不引入第二套注册表。

三个 ruleId 保持 `tushare.coverage.daily/weekly/monthly`。T02 的已保存占位规则为 version=1；本任务改变覆盖行为与参考窗口，因此依共享设计第6节把这三条规则版本和对应 descriptor.capabilityVersion 升为字符串 `2`，其他接口保持 `1`。这属于同规则演进，不能把新行为继续写在旧版本下。原任务保存的 @1 口径不重算，完整 capabilityHash 改变后旧待执行计划由 T07 给出 DEFINITION_CHANGED；旧报告原样保留。

### 2. 受限参考请求

SH→SSE、SZ→SZSE。BJ 不以 SSE/SZSE/BSE 表中偶然有行证明日历适用；本地尚无经过验证的 BJ 适用依据，hook 返回空列表，规则直接 UNKNOWN/CALENDAR_BASIS_UNPROVEN，写一条范围级依赖问题及证据，不生成候选。

SH/SZ 的三个请求如下；所有请求 `nullDates=false`，datasetKey 都是 `tushare_pro`，purpose 与 T02 已保存依赖逐字相同：

| 参考 | 投影 | 日期谓词 / 等值条件 | purpose |
| --- | --- | --- | --- |
| trade_cal | exchange, cal_date, is_open | dateField=cal_date；daily 用原范围，weekly 扩到起始 ISO 周一至结束 ISO 周日，monthly 扩到起月1日至末月最后一天；equalities={exchange:SSE或SZSE} | 行情交易日历与周期边界 |
| stock_basic | ts_code, list_date | 无日期谓词，equalities={}；T03 自动固定当前股票 | 上市日期线索 |
| suspend_d | ts_code, trade_date, suspend_timing, suspend_type | dateField=trade_date，原范围，equalities={}；T03 自动固定当前股票 | 停复牌解释线索 |

实际规则只能 `context.scan` 这些请求；target 扫描由 Evaluator 完成，不额外读取目标来猜测全集。Planner 固定许可，T03 再校验投影、来源、窗口及股票；不能在 hook 塞跨股票条件或扩大 suspend_d 范围。参考窗口扩大只用于周期边界，原 scope 和目标键范围从不修改。

### 3. 日历与候选

以 `scope.snapshotStartedAt` 在 Asia/Shanghai 的本地日期作为读取当日，不能用系统当前时间或 acceptedAt 推断发布。业务日期均为 LocalDate，is_open 接受精确 Long 0/1；不将 null、其他数值或字符串当成闭市。

把日历按 cal_date 建索引，检查声明参考窗口内每个自然日恰有一条、exchange 与请求一致、日期有效、is_open 为 0/1。缺行/重复/非法值都记录 REFERENCE_INCOMPLETE/UNKNOWN。缺日有具体参考日期；日历为空可用一条范围级问题说明整个参考窗口缺失，避免一次性构造巨大问题列表。证据必须明确缺失窗口，不能将样本冒充所有缺日。

- daily：对有效日历单元，is_open=1 的日期是候选标记；缺日不生成该日疑似行情缺失，其他有依据日期仍可比较。
- weekly：按 ISO 周（周一至周日）分组。仅完整且有效的整周日历可选最后开市日；全周闭市无候选；不固定周五。
- monthly：按自然月分组，完整有效月历中最后开市日为标记；全月闭市无候选；不固定自然月末。
- 周/月只把最终标记位于原用户闭区间内的周期纳入候选。任何日历不完整的周期不猜测最后开市日；原区间未覆盖的标记不纳入目标，也不以裁剪后的部分周期冒充全周期。
- 当日 daily 或末日尚未过去的 ISO 周/自然月单列 `PERIOD_NOT_FINISHED` 范围证据，不进入候选缺口比较。用周期自然末日与市场读取日期比较：末日早于读取日期才作为已结束候选周期。即使已结束，也没有足够来源发布证据，持续记录 `PUBLISH_TIME_UNPROVEN`，不据此确认缺失。

按日期升序生成完整键 `{ts_code: scope.symbol, trade_date: candidateDate}`。候选可以惰性 Iterable 提交给 `context.compare`，由核心逐键计费/检查预算；参考读取自然计入同一预算。不要捕获/吞掉 IntegrityReadException，也不为来源规则重置预算。没有新 SQL、网络客户端或线程。

### 4. 上市与停牌线索

stock_basic 有且只有一个当前股票记录且 list_date 非空时，只排除严格早于 list_date 的候选；当天保留。无行/空日期/矛盾记录时增加 `LISTING_HISTORY_UNPROVEN`，不凭空选择日期或删除股票。

即使 list_date 有值，当前表没有 list_status/delist_date 的历史版本，仍记录 `LIFECYCLE_UNPROVEN`；不得把当前快照当作历史证券全集。suspend_d 在原范围内的 suspend_type、suspend_timing 只作为可追溯线索摘要，不由单条 S、R、空 timing、全天或盘中标记移除任何候选。空表不能证明没有停牌，持续记录 `SUSPENSION_BASIS_UNPROVEN`。另保留 `SERVICE_BOUNDARY_UNPROVEN` 和 `PUBLISH_TIME_UNPROVEN`。

比较前生成基线证据，包含真实依赖 datasetKey、用途、参考范围、交易所、同一 scope.snapshotStartedAt、规则版本和上述不足说明。停牌摘要只包含候选相关的已存字段与日期，不保存任意原始行/响应；大量参考行用确定计数和按日期的线索描述，必要时让既有问题预算截断，不新增绕开预算的明细集合。

### 5. 比较、状态和问题定位

始终使用 `IntegrityExpectedKeys(scope, UNCONFIRMED, candidates, evidence)` 调用一次 `context.compare`。候选差集由核心产生 SUSPECTED_MISSING/WARN，全业务键与问题日期由核心绑定；不产生 MISSING 或 EXTRA。无候选、候选全命中或整个周期闭市仍为 UNKNOWN，expectedCount/matchedCount/coverageRate=null。实际键数由核心扫描结果提供，不以日历长度冒充实际数量；来源返回 compare 的统计，不自行覆写正式覆盖统计。

返回 `IntegrityRuleResult` 的状态固定 UNKNOWN；BJ reasonCode=CALENDAR_BASIS_UNPROVEN；存在日历缺损时为 REFERENCE_INCOMPLETE；其余为 EXPECTED_SET_UNPROVEN。完整性未知原因并存于证据/依赖问题，不仅保留一个总原因。三条通用规则仍独立运行，已知必填/键失败可以让整个单元 overall=FAIL，这不升级行情覆盖证据。

非缺口的说明使用现有 `IntegrityIssue.Type.REFERENCE_INCOMPLETE`、status=UNKNOWN；不扩展公共枚举。问题始终归属于目标股票/api，dateField=trade_date，businessKey={}。参考缺损日期在原范围内时 date=该日期，超出原范围的周/月参考日期用 date=null、relatedDates={cal_date:真实参考日}，evidence.range 为真实参考窗口；说明明确它是参考问题，不能伪装为目标行情缺失。生命周期/服务边界/发布等范围不足 date=null，不能伪造某个缺失业务键。未结束周期同样用范围级说明，并在 evidence.range 写该周期与原范围的交集。大量问题通过 sink 即时收集，达到上限后按核心 ERROR/incomplete 语义结束。

## Files

- 新增 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/integrity/TushareMarketIntegrityRule.java`：三个行情接口共用的参考规划、候选与解释。
- 修改同目录 `TushareIntegrityPolicies.java`：三规则注册/版本、referenceReads、复用依赖常量。
- 修改 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`：兼容 hook 委托。
- 新增 `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/integrity/TushareIntegrityRulesTest.java`：规则、窗口、候选、参考不足、证据和本地无网络测试。
- 修改同目录 `TushareIntegrityPoliciesTest.java`：三个版本=2、其余版本=1；原“全部不scan”测试仅用于非行情，行情移入具体规则测试；保留恰好40接口和日期轴断言。
- 修改 `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java`：公开方法白名单加入既有可选接口的 `integrityReferenceReads` 实现，继续约束其余插件公开面。
- 修改 `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ProductionApplicationContextIT.java`：沿用真实应用/MySQL装配，在无Token上下文受理选定 daily/weekly/monthly，等待已提交报告，核对参考缺失为UNKNOWN、全计划保留且证券表无写入。测试数据库种子在受理前写入，禁止规则内写证券表。
- 文档 `docs/verification/DATA-INTEGRITY-T08.md` 与任务看板：记录实测和状态，不改公共HTTP schema。

## Tests

第一项 RED：受控完整日历含周五闭市、周四开市，weekly 原范围含周四，断言 rule 传给 compare 的 UNCONFIRMED 完整键标记是周四且不是周五；当前占位规则既不scan也不compare，必须观察失败再实现。

专项用例必须独立给出手算日期/键，测试 context 仅模拟来源读取边界，不重新实现核心比较：

| 场景 | 预期 |
| --- | --- |
| SH/SZ/BJ | 请求单一适用交易所；BJ无日历替代，无候选，UNKNOWN/CALENDAR_BASIS_UNPROVEN。 |
| 日历缺日/重复/空is_open/非法is_open/空表 | REFERENCE_INCOMPLETE；不把参考缺日当行情缺失；完整独立日期/周期仍可比较。 |
| 节假日/周四末次开市/跨年ISO周/闰月/月末闭市 | 标记为真实最后开市日，范围扩大严格等于完整周期；完整键顺序稳定。 |
| 部分周/月窗口 | 最终标记在原区间才进入候选，不能按窗口末日替代周期标记；扩展窗口缺日仅参考问题。 |
| 当前未结束周期/当日/历史周期 | 未结束单列且不比较为疑似缺失；已结束仍PUBLISH_TIME_UNPROVEN。 |
| list_date早于/等于/晚于候选/空/无本地股票 | 仅排除明确上市前日期；其余范围保留，缺生命周期始终未知。 |
| 全天/盘中/S/R/空停牌表 | 线索可追溯；候选不因单条停牌或空表被删除。 |
| 候选缺失/全部命中/空候选 | UNCONFIRMED；来源覆盖始终UNKNOWN、正式比例null，不产生MISSING/EXTRA/PASS。 |
| 参考读取异常/预算取消 | 原异常传出给核心，不能改成规则业务PASS或吞掉扫描失败。 |
| 版本与兼容 | 仅三项变为2；完整hash随规则变化，其他日期轴/能力不变；旧报告读取仍用保存JSON。 |
| 无网络 | 规则/hook/能力执行后真实客户端spy无交互；无Token可受理运行，不改变下载readiness。 |
| 真实装配 | Runner使用声明参考窗口，MySQL实际完成，缺参考为UNKNOWN、有疑似键可定位；证券内容前后一致。 |

```sh
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  '-Dtest=TushareIntegrityRulesTest,TushareIntegrityPoliciesTest,IntegrityPluginContractTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml '-Dtest=ProductionApplicationContextIT,IntegrityCheckRunnerIT,IntegrityReadRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

实施后预期全部BUILD SUCCESS、0失败/错误/跳过；IT实际使用MySQL8.4.6。本文不宣称T08测试已存在/通过。

## Acceptance

T08看板列明场景全部有证据：明确参考不足与疑似行情缺口、精确候选标记和完整原范围，生产覆盖仍UNKNOWN且无正式百分比。三规则版本升级可被完整hash识别，其他接口、下载能力和核心执行器不变。受控测试与真实装配通过，新文件加入Git；T08实现须另行显式启动。

## Risks

- stock_basic不是历史生命周期库，suspend_d不是停牌全集；不能靠这两张表把生产候选升级为PROVEN。
- BJ适用日历、来源发布时点与服务边界未被证明，设计固定保留UNKNOWN，不留下待实施者猜测的替代策略。
- 周/月扩展参考日期可能不在原范围，必须以参考证据/关联日期定位，不制造目标缺键。
- 默认历史窗口可较长，来源参考和生成键共享T07预算，问题达到上限由框架标ERROR/incomplete；不静默抽样。
