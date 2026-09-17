# DATA-INTEGRITY-T02 本地能力发现与 40 接口口径

## Goal

启用的来源插件即使缺 Token 也能发现本地完整性检查能力；Tushare 全部 40 个接口有明确范围、股票列、日期轴、依赖及覆盖结论。承接 T01 已验收合同，不改变下载可用性。

## Scope

包含 PluginRegistry 独立本地入口、Tushare 可选能力、股票规范化、40 项静态策略与明确 UNKNOWN 覆盖规则、兼容及纯本地测试。daily/weekly/monthly 的候选缺口计算归 T08；数据库读取、集合比较、任务受理、HTTP 和页面不在本项。

## Approach

### 注册与本地发现

在 `PluginRegistry` 既有 Candidate 分组流程中添加不可变的本地检查查找表；不改变原 `plugins` 下载表、`find(PluginId)`、descriptor 列表或排序。复用同一次 descriptor/readiness 快照，避免重复插件调用及独立入口绕过重复 ID 检查。

新增接口：

```java
public IntegrityAvailability findIntegrity(PluginId pluginId);
public record IntegrityAvailability(IntegrityCheckSupport support, String unavailableReason) {
    public boolean available() { return support != null; }
}
```

`IntegrityAvailability` 作为 PluginRegistry 的嵌套 public record；available 时 reason=null，否则 support=null 且 reason 为非空安全说明，不透传插件异常。`findIntegrity(null)` 继续抛 NullPointerException。

判定顺序：重复 ID → `duplicate plugin id`；readiness 读取异常 → `plugin readiness unavailable`；enabled=false → `plugin disabled`；不是 IntegrityCheckSupport → `integrity check unsupported`；其余返回支持实例。未知 ID 返回 `plugin not found`。enabled=true 时忽略 credentialConfigured/downloadAvailable，不读取 Token、不调用下载；旧 find 仍按 downloadAvailable 返回空或实例。注册层不执行规则、扫描数据或计算完整率。

### Tushare 来源策略

`TushareProPlugin implements BatchDownloadSupport, IntegrityCheckSupport`。新增私有 `TushareIntegrityPolicies`，构造参数为已有 `List<DatasetDefinition>` 和 `Map<ApiName,BatchDownloadDescriptor>` 本地快照，不持有 client、properties、JdbcTemplate 或网络对象；插件三个方法直接委托。保持现有 public 构造器及全部下载方法。

`TushareIntegrityPolicies` 构造时复制定义，核对与下表 40 个 API 精确一致；建立有序描述/规则映射，顺序使用传入的定义列表。调用 `IntegrityContracts.validate` 校验每项描述与实际规则及所有参考定义，最后 `validatePlugin` 校验跨接口 ID。元数据不合法抛 IllegalArgumentException，不静默删 API。

方法：`normalizeSymbol(String)`、`descriptor(ApiName)` 和 `rules(ApiName)`。未知 API 分别返回 Optional.empty()/List.of()；null API 拒绝。`normalizeSymbol` 拒绝 null/空，trim 后 `toUpperCase(Locale.ROOT)`，严格 `[0-9]{6}[.](SH|SZ|BJ)`，非法则 IllegalArgumentException。此方法不读取本地证券表，`999999.SH` 这类格式合法且本地不存在的代码仍保留。

所有描述 `marketZone=Asia/Shanghai`、`capabilityVersion="1"`；股票级 `symbolField=ts_code`。不从 queryMode、RANGE 描述或样例行数推断日期轴。

| scopeKind | dateField / dateLabel | API（精确成员） |
| --- | --- | --- |
| STOCK_DATE | trade_date / 交易日期 | daily、weekly、monthly、daily_basic、stk_limit、moneyflow、margin_detail、block_trade、slb_sec、slb_sec_detail、top_list、adj_factor、suspend_d |
| STOCK_DATE | ann_date / 公告日期 | forecast、stk_holdernumber、stk_holdertrade、pledge_detail、dividend、disclosure_date、income、fina_audit、express、stk_managers、balancesheet、cashflow、repurchase、stk_rewards |
| STOCK_DATE | end_date / 报告期或统计截止日 | fina_mainbz、top10_holders、top10_floatholders、fina_indicator、pledge_stat |
| STOCK_DATE | ipo_date / 上网发行日期 | new_share |
| STOCK_DATE | in_date / 入选日期 | index_member_all |
| STOCK_SNAPSHOT | null / 当前快照 | stock_basic、stock_company |
| NON_STOCK | null / 非股票级范围 | margin、slb_len、index_classify、trade_cal |

2026-09-16 已逐项核对 manifest、YAML：恰好 40 项，无重漏；股票接口 ts_code 存在，各日期轴为 DATE。index_member_all 是 in_date，out_date 仅解释；new_share 是 ipo_date，不替换为 issue_date。

### 覆盖规则与参考声明

每个 36 个股票级 API 恰有一条 `tushare.coverage.<apiName>@1`（dimension=COVERAGE、displayName 使用“<接口显示名>覆盖”）。requiredColumns 包含完整业务键，再按顺序去重加入 ts_code/dateField；无任意 JSON/SQL/下载调用。NON_STOCK 的 rules 为空，symbolField/dateField=null；由后续执行器产生一条 symbol=null 的 N/A 范围说明。

`TushareIntegrityCoverageRule` 为同一简单实现，接收不可变 descriptor 和 reason，不调用 context.scan/compare 或 issueSink。`evaluate` 返回 descriptor、UNKNOWN、固定原因/解释、七项均 null 的 IntegrityStatistics、非空本地策略证据。证据 source=`tushare-local-policy:<apiName>`，ruleVersion=`1`，range=scope.range()，readAt=scope.snapshotStartedAt()，summary 说明依据缺口；要求执行 scope 与目标 datasetKey 一致、symbol 和 snapshotStartedAt 非空。

- stock_basic、stock_company：`HISTORY_NOT_STORED`，“当前快照不保存所选历史窗口的完整版本”。不得用 setup_date 或 list_date 当检查日期轴。
- 其余股票级接口（含暂未计算候选的 daily/weekly/monthly）：`EXPECTED_SET_UNPROVEN`，“缺少与所选范围同口径的可靠预期全集”。T08 接入候选算法时升级这些规则版本，使哈希变化。
- 缺少描述和 UNKNOWN 是不同情况；本项 Tushare 40 项均有描述，不用 N/A 代替未证明。

daily/weekly/monthly 的 descriptor 和覆盖 rule 同时声明以下参考依赖，为 T08 预留明确读取口径；其他 API 的 dependencies 为空：

| dataset | columns | purpose（稳定值） |
| --- | --- | --- |
| tushare_pro/trade_cal | exchange, cal_date, is_open | 行情交易日历与周期边界 |
| tushare_pro/stock_basic | ts_code, list_date | 上市日期线索 |
| tushare_pro/suspend_d | ts_code, trade_date, suspend_timing, suspend_type | 停复牌解释线索 |

所有依赖使用真实 YAML 已存在字段。未储存 delist_date/list_status、不全生命周期和停牌全集不足以证明覆盖；不声明不存在列。BJ 缺适用日历的限制保留，不以 SSE 替代。T02 只声明本地依赖，不扫描、扩张区间或生成疑似缺口。

### 下载限制与版本

IntegrityDescriptor.limitations 固定说明“只检查本地数据；下载完成不证明覆盖”，快照添加 HISTORY_NOT_STORED 的用户解释，NON_STOCK 添加“不按单只股票执行”。index_member_all 补充“入选窗口不等于窗口内全部有效成员”；new_share 补充“日期为空时范围无法确定”。行情三项说明参考日历/生命周期/停牌/发布时间证据不足及 BJ 日历限制。

插件已有 `batchDescriptor(api)` 是下载限制的权威来源：若 availability 非 AVAILABLE，将其安全 unavailableReason 追加至检查描述 limitations，仅作展示，不改变 scope 或本地可用性。TushareIntegrityPolicies 不持有 batch client；由 TushareProPlugin 在构造时传入一次读取的 `Map<ApiName,BatchDownloadDescriptor>` 元数据，构造顺序 batches → 本地 batchDescriptor 快照 → integrityPolicies。描述查询仍只返回预先保存的本地值。

T02 不组装 HTTP DTO；后续 T06/T09 由 core 使用 T01 `ApiSnapshot(definition,descriptor,referenceDefinitions,coreRules)` 生成能力哈希。股票接口 coreRules 使用 `IntegrityContracts.coreRules(definition)`，NON_STOCK/缺描述为空；必须包含声明参考定义。不得把 Token/readiness 或运行时间传入 hash。以后改变日期轴/依赖/行为/规则必须升级版本。

## Files

- 修改 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`：本地查找表、入口和嵌套可用性结果。
- 修改 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`：实现可选能力、一次性本地策略装配及委托。
- 新增 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/integrity/TushareIntegrityPolicies.java`：40 项固定口径、依赖和下载限制元数据。
- 新增同目录 `TushareIntegrityCoverageRule.java`：UNKNOWN/HISTORY_NOT_STORED/EXPECTED_SET_UNPROVEN 本地规则。
- 新增 `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/integrity/TushareIntegrityPoliciesTest.java`，修改既有 Tushare 插件兼容测试。
- 新增 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityPluginContractTest.java`：注册兼容；沿用 `RegistryTest` 回归。T01 fixture 同名测试保留。
- 新增 `docs/verification/DATA-INTEGRITY-T02.md` 并更新本任务板状态/证据；不改 manifest/YAML 的数据定义以迁就错误日期轴。

## Tests

```sh
mvn -o -f data-plane/pom.xml \
  -Dtest=IntegrityPluginContractTest,TushareIntegrityPoliciesTest,RegistryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml test
```

环境需要时沿用 T01 已验证的 Mockito javaagent 参数；完整回归需要本机 WireMock 端口权限。预期零失败，不用 skip 代替验收。

1. 参数化独立预期表对照 manifest 和所有 YAML：恰好 40、36 个单覆盖规则、4 个无规则 NON_STOCK、2 个 snapshot；日期字段/类型/完整键依赖均正确。使用独立期望，不能从被测策略生成期望。
2. 缺 Token enabled=true 的支持插件 findIntegrity 可用，但 find 仍为空；enabled=false、重复 ID、readiness 异常、未知 ID、旧插件均按上文返回拒绝说明。下载/readiness/metadata 方法调用次数不因新入口重复。
3. 股票 trim/大写/SH/SZ/BJ、格式合法但本地不存在、非法长度/后缀/内部空白；验证未访问 DB。
4. 每条 UNKNOWN 覆盖规则在 scan/compare/download 一经调用就失败的测试环境中执行，仍返回正确原因、null 数值及原范围证据。Snapshot 不转 PASS，非股票不伪造覆盖规则。
5. 断言所有元数据调用不触发 TushareProClient；四个撤回 RANGE 接口描述仍存在并保留下载限制。旧 SINGLE/RANGE/registry 全部回归通过。
6. 损坏定义、缺依赖列、重漏 API 的构造失败；无需网络或 MySQL。

## Acceptance

满足看板 T02 的全部结果：无 Token 本地发现与下载门禁分离、40 项完整、正确日期轴、快照/NON_STOCK/UNKNOWN 边界清晰、描述纯本地、既有下载兼容。验收记录需给出实际测试结果；此设计就绪不表示 T02 已实施。

## Risks

T01 已通过 17 项专项及 1158 项后端测试；实际扫描、比较与任务仍未实现。不要把 T02 的 UNKNOWN 元数据规则当作 T08 候选推导或生产覆盖证明。原工作区可能有后续 Studio 更改，本隔离工作区不自动同步；继续在本工作区操作，保留现有暂存基线。
