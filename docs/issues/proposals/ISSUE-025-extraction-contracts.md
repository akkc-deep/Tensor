# ISSUE-025：十一接口提取承诺采用方案

## 当前结论

2026-09-14 用户明确回复“可以接受不完整”，采用方案 A：十一接口按上游响应采集验收，允许无法排除的漏数。该决定解除产品承诺待决项；上游完整性仍未知，生产实现和真实任务验收由 ISSUE-026 完成。

十一项为`adj_factor,suspend_d,income,balancesheet,cashflow,fina_audit,express,repurchase,stk_managers,top10_holders,top10_floatholders`。[本轮官方复核](../../verification/ISSUE-018-T14-official-evidence.md#14-issue-025-十一接口提取合同复核2026-09-14)取得17份公开文档；十一份接口正文与此前调查一致，未取得当前请求的响应上限、截断标志或提取完成规则。`repurchase`的2000只指不传任何参数时的默认返回数量，top10名称不代表服务器最多返回10条。

真实SOURCE解决了可查日期、两股票/非股票归属和部分代表场景；[运行登记](../../verification/ISSUE-018-T14-runs.md#issue-025-十一接口提取规则与来源)保留每轮精确输入、全部空/非空及身份。它们不能证明没有被截断，也不能据此选一个数值作为工程上限。此前ISSUE-019/020的采用决定有具体文档数字，不能自动推广到本十一项。

## 方案 A：限定为上游响应采集（已采用）

将这十一项的产品承诺明确改为“按所选日期区间采集本次上游实际返回的记录”，完整性保持未确认。成功只说明返回记录已经按约定校验、适配和持久化，不承诺所选区间或全部历史无遗漏。用户能直接看到“数据完整性未确认，可能存在上游截断”。这是对原“完整提取”要求的实质放宽。

具体执行合同：

- 保持原生日期区间请求：十项仍单股票，repurchase仍无股票；三个日期轴按现有表，原字段/业务键保持。不增加分页、VIP、type、多股票合并、逐日扫描或猜测阈值。
- 每个原生区间请求只采集当次响应；严格检查API/字段/行宽/日期闭区间/股票归属并按原适配与业务键入库。空响应仅表示这次请求无返回，不能称“该区间没有数据”；错误、范围不符、适配或写入失败照常失败，无自动重试。
- 在生产策略、能力描述、批次评估和验收索引中显式区分`RESPONSE_ONLY`（仅返回行）与已有完整规则。不能把UNKNOWN伪装成ROW_LIMIT/CALENDAR_COVERAGE，也不能简单将`sourceVerified`改true或让UNKNOWN一律通过。任务和页面需区分“返回行已采集”与“完整下载”，保留精确策略版本/规则及结果追踪。
- 保留原十一项未取得上游完整合同的事实、全部旧run和原验收差异；ISSUE-025按修订后的“明确响应采集合同+来源验证”验收，而不是声称已经证明完整提取。其他29接口及其原有完整性要求不变。
- 批准后先在ISSUE-025完成上述明确规则的接口/验收合同修订及必要回归；生产实现、候选版本升级、能力与页面说明、真实TASK/SQL由ISSUE-026专属设计和实施统一完成。规则放宽本身不开放生产、不完成T14或母issue。

主要代价是接受无法排除的漏数可能；宽窄窗口相同、正样本或重复调用不能消除此风险。新增`RESPONSE_ONLY`是明确的结果语义，需要贯通合同及消费者，不能用现有`COMPLETE`的严格含义掩盖。若后续取得上游完整规则，再另行升级策略并验证，保留历史口径。

## 方案 B：保留完整提取要求（未采用）

十一项保持UNKNOWN和NEEDS_VERIFICATION；只保留已完成来源调查，等待可核验的上游规则，ISSUE-025暂不关闭。

需要的资料应明确对应API、普通接口、十项单股票或repurchase日期请求、适用账户权限/版本，并提供至少一种可执行完成判据：实际截断上限与低于上限的含义、可靠的`has_more/total/cursor`终止合同，或可验证的完整事件/日期集合。仅给默认数量、代码成功、样例或“支持历史”不能满足。新资料如要求ROW_LIMIT/CALENDAR_COVERAGE之外算法，先修订设计再实施；联系上游需用户另行明确授权，本次未发送任何消息。

## 决策记录

2026-09-15后续范围决定：用户明确本次不支持balancesheet、cashflow、repurchase及fina_indicator的区间批量下载，并要求不开始ISSUE-032，原话及精确范围见[本次范围调整](ISSUE-026-range-scope.md#决策记录)。本节对前三个RESPONSE_ONLY接口同时记录本次EXCLUDED的明确依据；原响应采集合同及其余八个接口的采用决定保持。该排除不表示冲突已修复，也不改变历史结果或SINGLE。

2026-09-14，用户在了解十一接口的数据含义及方案差异后，明确回复“可以接受不完整”。据此采用本提案方案 A；不是借用其他 issue 的批准，也不是新的上游保证。

原验收要求“取得可核验完整提取规则”，现对本十一项改为“明确响应采集合同，验证参数、日期、归属及返回记录处理”。只有这一完整性要求放宽；范围、来源取证、失败处理、原业务键、版本隔离及真实 TASK/SQL 要求保持。其他 29 接口不适用本例外。

### 已批准的逐接口合同

所有接口均为 `NATIVE_RANGE`，输入含 `start_date/end_date`，按下表输出日期闭区间校验；十项每请求一个 `ts_code`，回购不传股票。日期轴不得从副日期或业务事件日期替换。

| API | 股票输入 | dateAxis / 输出列 | 返回内容与日期约束 |
| --- | --- | --- | --- |
| adj_factor | 必填 | TRADE_DATE / trade_date | 复权因子，交易日期 |
| suspend_d | 必填 | TRADE_DATE / trade_date | 停复牌记录，保留 S/R 和原日内字段，不推断缺日无事件 |
| income | 必填 | ANNOUNCEMENT_DATE / ann_date | 利润表，公告日而非报告期 |
| balancesheet | 必填 | ANNOUNCEMENT_DATE / ann_date | 资产负债表，公告日而非报告期 |
| cashflow | 必填 | ANNOUNCEMENT_DATE / ann_date | 现金流量表，不替换为 f_ann_date 或 end_date |
| fina_audit | 必填 | ANNOUNCEMENT_DATE / ann_date | 审计意见，不增加 period 参数 |
| express | 必填 | ANNOUNCEMENT_DATE / ann_date | 普通接口业绩快报，不改用 VIP |
| repurchase | 无 | ANNOUNCEMENT_DATE / ann_date | 回购公告，保留多证券返回 |
| stk_managers | 必填 | ANNOUNCEMENT_DATE / ann_date | 管理层公告，不以输出 begin_date/end_date 任期替代 ann_date 公告轴 |
| top10_holders | 必填 | REPORT_PERIOD / end_date | 前十大股东，允许公告日在范围外及非季末报告期 |
| top10_floatholders | 必填 | REPORT_PERIOD / end_date | 前十大流通股东，允许公告日在范围外 |

### 成功、失败及历史证据

- 每个原生区间计划一个叶子，只处理当次响应；`RESPONSE_ONLY` 不设 rowLimit、不按行数拆分、不追加请求补齐。用户另建或手动重跑任务沿用既有生命周期及版本校验，不代表相同响应或全量数据。
- `RESPONSE_ONLY` 是独立规则和批次评估值，不能返回严格的 `COMPLETE` 来掩盖不完整。只有结构、字段、行宽、日期、股票归属通过，且所有返回行按原适配和业务键完成持久化后，任务才可为 `SUCCEEDED`；终态表示本次采集操作成功。
- 保留原业务键更新语义；sourceRowCount 是原始返回行数，insertedRows/updatedRows 来自实际写入，不能把去重后的键数当来源数。非法响应、适配错误、写入失败、超时、取消和预算中断沿用现有失败/停止与事务规则，不能因接受漏数忽略它们。
- 合法空响应可完成本次采集，证券写入为 0；页面写“本次请求未返回记录”，不能写“该区间没有数据”。非空完成写“返回记录已采集”。提交前的区间说明及任务列表/详情均保留“数据完整性未确认，可能存在上游截断”；任务页面根据持久化策略快照显示，不从当前接口能力反推旧任务语义。
- 本轮索引仍为 schemaVersion 1，十一项 `completeness={kind:UNKNOWN,rowLimit:null,evidenceRefs:[]}`、v1 和生产准入保持；仅以 `decisionRef` 记录本决定，待决码改为 `RESPONSE_ONLY_IMPLEMENTATION_PENDING`。UNKNOWN 消费者继续拒绝全部 87 组条件输入。ISSUE-026 必须先贯通独立规则、能力、批次评估、持久化快照、页面及证据消费者，才可形成 v2 本地候选；不能只改布尔准入。
- 原 25 轮 / 822 case / 886 请求及全部旧 SOURCE/TASK 结果不回填。43 个新空仍是来源代表性 `EVIDENCE_MISSING`；后续合法空 TASK 可在新 run 按响应采集合同验收，不能据此重标旧 SOURCE。真实 TASK/SQL 和清洁构建身份未成立前，当前验收索引不得标 AVAILABLE。
