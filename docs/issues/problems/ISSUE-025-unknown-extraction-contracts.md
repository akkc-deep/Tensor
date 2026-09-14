# ISSUE-025：明确十一接口响应采集合同与来源

## 当前状态

COMPLETED（2026-09-14）。用户明确“可以接受不完整”，已采用[方案 A](../proposals/ISSUE-025-extraction-contracts.md#决策记录)，完成十一接口响应采集合同、来源取证和交付。准确状态见[后续看板](../../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md)；生产实现和真实TASK/SQL由ISSUE-026承担，T14及母issue尚未完成。

## 目标与范围

按用户2026-09-14“可以接受不完整”的决定，为十一项 UNKNOWN 明确上游响应采集合同，并补齐对应日期 / 事件 / 边界来源证据。上游完整性仍未知，原要求与修订差异见[决策记录](../proposals/ISSUE-025-extraction-contracts.md#决策记录)。

主责接口：`adj_factor`、`suspend_d`、`income`、`balancesheet`、`cashflow`、`fina_audit`、`express`、`repurchase`、`stk_managers`、`top10_holders`、`top10_floatholders`。

## 已知问题与证据

- 既有公开 HTML、静态文档及 FAQ 调查未取得完整提取规则；宽窄区间相同或少量非空不能证明完整。
- income 新两股票各 2 行已证明公告日与报告期区别；repurchase 新 852 行 / 585 股票已证明证券数量与归属。
- repurchase 无参数默认 2000 不是日期过滤请求的硬上限；top10 系列业务名称不能推导提取上限。
- 其余逐接口空样本、两端和日期对照缺口保留在唯一验收索引。

来源：[当前验收报告](../../verification/ISSUE-018-range-acceptance.md)、[唯一 JSON 索引](../../verification/ISSUE-018-range-acceptance.json)、[公开补证](../../verification/ISSUE-018-T14-official-evidence.md)。这些是已有事实，创建本 issue 没有产生新的 API / TASK / SQL 结果。

## 依赖与处理顺序

无其他新 issue 的完成依赖；消费 T14 既有设计和证据。串行顺序由看板规定。具体顺序为 ISSUE-019 → ISSUE-020 → ISSUE-021 → ISSUE-022 → ISSUE-023 → ISSUE-024 → ISSUE-025 → ISSUE-026；不得将顺序误作已经完成的上游输入。

第一动作：逐项对照已有公开调查，列出确实仍缺的上游合同和可由技术验证解决的缺口，向用户提交无法由现有材料回答的决策。

## 关闭条件

- 逐接口明确已批准的 RESPONSE_ONLY 合同、原生日期/股票参数、单次响应处理及成功/空/失败含义，记录用户决定及原完整提取要求的差异；上游 UNKNOWN 事实保留。
- 同步 T13/T14、专属设计、唯一索引决定引用与独立预期，验证当前 UNKNOWN 仍拒绝提前准入；生产实现和版本由 ISSUE-026 承担。
- 补齐各接口来源与代表场景、清洁运行和必要回归，完成独立复审；向 ISSUE-026 交付合同及匹配来源参数，不以来源成功冒充 TASK/SQL。

## 约束

沿用 [T14 设计](../../task-designs/ISSUE-018-T14-design.md)和 [T13 验收合同](../../task-designs/ISSUE-018-T13-design.md)。规则解释、默认参数、业务键、市场映射或历史承诺的实质变更须先记录依据及必要的用户决策；真实执行前完成本 issue 的专属设计。保留旧轮次和未验证状态，不在 issue 文档另造验收索引，不因拆分关闭 ISSUE-017 / ISSUE-018。

## 取证完成时的待决记录（2026-09-14，决定前）

按专属设计完成17份官方公开复核、最小测试侧日期/事件投影及三轮固定SOURCE：135case/135请求，92非空PASS、43空EVIDENCE_MISSING，三个exit0/cleanup PASS。两基准股票/原非股票方式的代表整段与非空边界已取得，停复牌S/R与连续五日、公告/报告期、管理层任期、回购多证券均有新观察；cashflow的ann/f_ann本轮均相同，不宣称可互换。详细原对象与准确差异见[运行登记](../../verification/ISSUE-018-T14-runs.md#issue-025-十一接口提取规则与来源)和唯一验收索引。

相关Maven194/194、隔离验收1093项后端/包与468前端、Node78/78通过。独立投影复审提出的管理层离任日期测试缺口已补齐并通过复核。87组唯一非空参数作为[条件输入](ISSUE-026-range-task-final-acceptance.md#issue-025-条件输入完整性未确认)交付，SOURCE参数与身份精确绑定，现有消费者对十一UNKNOWN全部87项拒绝；没有提前准入或新TASK/SQL。

第一项关闭条件仍不成立：十一接口未取得可核验完整提取规则，样本和边界不能证明没有截断。已形成[具体采用方案](../proposals/ISSUE-025-extraction-contracts.md)：A仅采集上游当次返回行并明确完整性未确认；B保留严格完整提取要求、等待上游规则。两者均未获用户选择，UNKNOWN/decisionRef/生产策略与版本保持，原22轮及其他29接口未改写，母issue不关闭。

当时保持IN_PROGRESS，等待用户对实际产品承诺作出决定。方案A若获批准，先修订本issue及T13/T14的明确规则和验收合同；生产RESPONSE_ONLY实现、能力/页面、版本和真实TASK/SQL在ISSUE-026专属设计内落实，不能仅靠将UNKNOWN改true跳过完整性门禁。方案B下保留现有成果等待准确上游依据。

最终独立复审已核对22组有效来源边界与87条文档重建输入，无剩余阻断发现；报告中无法由保存证据支持的具体报告期推断已删除，修正后Node78/78通过，全部原运行对象保持。当时仍待完整性承诺决定，未因取证和复审完成而关闭；后续明确批准见本提案决策记录。

## 完成证据（2026-09-14，方案 A 已批准）

已按用户“可以接受不完整”修订本issue三项关闭条件、专属设计及T13/T14/总体设计/母issue对应条款；上游完整性未知事实保留。三轮135项SOURCE的92非空与43空、全部原运行不回填；87组精确参数和已批准合同正式交ISSUE-026，当前UNKNOWN消费者仍全部拒绝。

决定后Node78/78、87绑定/87拒绝、全部25轮822case886请求及其他29接口不变核对通过；独立复审的问题已修正，复核无剩余阻断。三项Acceptance实际满足，记录IN_PROGRESS → COMPLETED。详情见[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-025-方案a确认与最终验收2026-09-14)。本次不实现生产RESPONSE_ONLY、不新建TASK/SQL，不将用户批准记作上游完整保证。
