# ISSUE-019：三接口限量采用口径

## 当前结论

2026-09-13 用户在“是否确认采用方案A，按官方6000／3500／2000行作为阈值”的明确问题后回复“同意”，确认采用方案A。该决定接受三段官方说明作为工程阈值依据，没有取得新的上游截断保证。

此前独立SOURCE两轮38case/50请求（28非空PASS、10空对照、无请求失败）及[专属设计](../../task-designs/ISSUE-019-design.md)继续有效。正式索引采用三项ROW_LIMIT并链接本决定；原始SOURCE仍保留取证时的UNKNOWN说明，生产RANGE仍待ISSUE-026任务验收，不因本决定开放。

## 已有证据

| 接口 | 官方原文 | 候选值 | 日期 / 请求方式 |
| --- | --- | ---: | --- |
| [daily](https://tushare.pro/document/2?doc_id=27) | 每次6000条数据 | 6000 | 固定股票，trade_date 原生区间 |
| [forecast](https://tushare.pro/document/2?doc_id=45) | 单次3500行 | 3500 | 固定股票，ann_date 原生区间 |
| [dividend](https://tushare.pro/document/2?doc_id=103) | 单次查询返回2000行 | 2000 | 固定股票，按每个自然日的 ann_date 查询 |

官方原文的获取时间、正文 / HTML 摘要保存在 [T14 沿用的验收报告](../../verification/ISSUE-018-range-acceptance.md)。[T14 公开补证](../../verification/ISSUE-018-T14-official-evidence.md)已核对 HTML、静态文档和一般规则，未找到更明确的新截断合同。

[T13 设计](../../task-designs/ISSUE-018-T13-design.md)要求：“ROW_LIMIT要求证据说明按该L截断且小于L可视为本片完整；默认返回数、数据库batchSize和一次样例不足。”此前因此将三项索引记为UNKNOWN；本次经用户确认，仅对这三项接受上述官方原文为阈值依据，并在T13/T14设计记录此例外。

生产 `TushareBatchPolicies` 已保存这三个候选值，采用 `rowCount < limit` 完整、达到或超过阈值要求拆分的通用判断；三接口 sourceVerified=false，仍拒绝 RANGE。问题不需要另造分页框架。

## 方案 A：按官方限量说明采用阈值（已确认）

将这三段官方限量说明接受为相应请求的工程依据，不再额外要求“最大 / 最多”字样。只适用于这三接口，不扩展到无规则的十一项，也不把 repurchase 的无参数默认值视为区间上限。

- daily / forecast：达到或超过阈值时拆分日期范围；最小单日仍满额则明确失败，不显示完整成功。
- dividend：遍历全部自然日；单日达到或超过 2000 即完整性未确认，不自动添加分页、股票或分类。
- 保留真实 SOURCE 的股票、日期、整段和边界验证，forecast 需非空公告、dividend 需可追查非交易日公告。少量 / 空样本不会新增完整性保证。
- 决策后先修订 T13 / T14 对三项的采用口径，再补规则引用与独立预期；缺有效 SOURCE 时保持 NEEDS_VERIFICATION。只有匹配真实 RANGE TASK / SQL 和相关门禁成立后才记录最终开放，统一在 ISSUE-026 收尾。
- 若实测或新官方材料证明上限不适用，撤回相应候选判断并保留失败；不靠修改业务键或忽略数据通过验证。

该方案依赖文档限量适用于所选请求。如果上游存在未披露的更低限制，已有小样本无法排除漏数风险。用户同意表示接受这一验收口径，不表示 Tushare 追加了保证，也不能记成真实验证通过。

## 方案 B：保留现有严格要求

继续要求能直接说明截断和小于阈值完整的可核验依据，三项保持 UNKNOWN。已有公开材料调查结果不变；取得新官方说明或其他可核验规则前，不把重复抽样当成规则证据。本方案保持原验收要求，但该缺口可能需要上游进一步澄清。联系上游另需用户明确授权，不自动发送消息。

## 决策记录

- 确认日期：2026-09-13；用户原话：“同意”。上下文为明确询问是否采用方案A及6000／3500／2000阈值。
- 决策内容：仅daily固定单股票trade_date原生区间采用6000，forecast固定单股票ann_date原生区间采用3500，dividend固定单股票逐自然日ann_date采用2000。按原始响应行数判断，小于L按此口径视为该片完整；达到或超过L要求拆分，不能再拆的单日以完整性未确认失败。
- 适用边界：不扩展到十一项无规则接口，不把repurchase无参数默认2000作为区间上限，不改变股票/日期语义、业务键或分页方式。
- 决策与事实分开：这是用户接受文档限量适用于所选请求的工程口径；不是Tushare追加保证。已有少量/空样本不能排除未披露的更低限制；新反证须撤回对应判断并保留历史。
- 开放条件：本issue只确认规则和交付有效来源输入；生产sourceVerified=false、v1、NEEDS_VERIFICATION继续保持。ISSUE-026须用新候选包完成匹配RANGE TASK/SQL、相关门禁后才记录开放。

## 决策后的验证

仅在规则 / 测试预期改变后运行现有证据校验和相关策略测试：

```sh
data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

真实来源已按原合同在先前两轮完成，本次规则决定不重跑账户请求或改写旧run。决定后先观察独立预期RED（exit1），更新索引后Node78项与Maven160项回归全部通过（exit0、无失败/跳过）；原口径下的acceptance构建1063/468属于历史验证。28项TASK输入已离线核对并交ISSUE-026，真实TASK/SQL尚未执行。
