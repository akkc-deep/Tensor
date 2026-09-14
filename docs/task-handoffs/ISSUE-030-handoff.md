# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** [docs/task-handoffs/ISSUE-026/ISSUE-026-task-board.md](ISSUE-026/ISSUE-026-task-board.md)。
- **Completed task:** ISSUE-029，已记录COMPLETED。
- **Next task:** ISSUE-030，按预定义Order4选取。
- **Design document:** [docs/task-designs/ISSUE-030-design.md](../task-designs/ISSUE-030-design.md)，详细设计已完成并先链接到本项看板行。
- **Expected next status:** READY；本交接写入并链接后记录NOT_STARTED → READY，尚未开始实施。

## Next Task

ISSUE-030「配置剩余接口候选策略与日历映射」：按已完成设计配置11个RESPONSE_ONLY、18个ROW_LIMIT、1个CALENDAR_COVERAGE候选，加入top_list BJ→SSE日历参照，交付精确SOURCE映射、独立能力预期及新候选源码/生产包/验收包身份。

验收要求是30项逐一匹配已采用规则、日期轴、参数/原键和有效SOURCE；原四AVAILABLE/v2、40/34/6与31/3/6结构保持。十一项原生单请求、无阈值且不可拆，直接trade_cal BSE在来源调用前拒绝，top_list保留原BJ证券，margin保留exchange_id。相关测试、隔离构建和独立审查通过；正式索引30项仍NEEDS_VERIFICATION，26轮826case历史逐对象保持。本项不执行新真实SOURCE、TASK或SQL，278 TASK验收属于ISSUE-031。

## Dependencies

### ISSUE-027

- **Artifact:** [后端验收](../verification/ISSUE-027-response-only-backend.md)；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/CompletenessRule.java`、`BatchAssessment.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRunner.java`、`DownloadTaskService.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`。
- **Decision:** RESPONSE_ONLY为独立规则及评估，只允许NATIVE_RANGE、rowLimit=null、splittable=false；UNKNOWN继续拒绝。`TaskPolicySummary`从保存的policySnapshot读取规则和版本，SINGLE为null。
- **Rationale:** 用户已接受十一接口上游可能截断；该决定只允许响应采集，不取消结构、日期、股票归属、适配、事务或预算检查。历史任务语义必须来自持久快照。
- **Constraint:** runner严格匹配规则与评估，不能将RESPONSE_ONLY作为COMPLETE或SPLIT_REQUIRED；repurchase原全市场日期查询形状保持。生产30项候选尚未配置，不能靠统一sourceVerified=true开放。
- **Usage:** 在现有通用实现上按专属设计显式配置候选依据/规则/v2，复用summary和runner；不新增公共枚举、快照格式或生产验证开关。
- **Readiness evidence:** 已有280项后端专项、7项包合同及468项前端验证通过，独立审查无剩余发现；ISSUE-029冻结完整源码的后端/包1132项和前端524项再次通过。专项与全量不重复相加。

### ISSUE-028

- **Artifact:** [HTTP/页面验收](../verification/ISSUE-028-response-only-contract-ui.md)；`docs/contracts/download-task.schema.json`、`download-task-examples.json`、`openapi-v1.yaml`；`control-plane/src/api/downloadTaskDtos.js`、`src/utils/downloadTaskText.js`；`control-plane/e2e/ui-redesign.fixtures.js`、`tushare-metadata.spec.js`及相关页面。
- **Decision:** TaskResponse必需`extraction`；SINGLE为null，RANGE为保存的`{policyVersion,ruleKind}`。响应采集持续显示“数据完整性未确认，可能存在上游截断”；成功非空为“返回记录已采集”，成功空为“本次请求未返回记录”。
- **Rationale:** 用户应能识别响应采集和严格完整性规则，当前能力变化不能改写旧任务含义；表单、列表和详情遵守同一合同。
- **Constraint:** 缺失/错误extraction或非法能力组合继续严格拒绝；失败不能显示成功。不增加确认弹窗、勾选步骤或新UI功能；旧严格、UNKNOWN和SINGLE语义保持。
- **Usage:** 同步30项候选的独立RANGE_EXPECTATIONS、rule/limit/splittable/version及受控任务fixture；metadata按ROW_LIMIT/VERIFIED_RULE/RESPONSE_ONLY分别核对HTTP。用现有表单/列表/详情验证候选文案，不从生产注册反向生成测试预期。
- **Readiness evidence:** ISSUE-028最终acceptance后端/包1125项、独立HTTP/MySQL19项、前端524项及受控浏览器66项通过；独立前端及整体审查通过。ISSUE-029未新增真实TASK或浏览器验收。

### ISSUE-029

- **Artifact:** [工具及四SOURCE验收](../verification/ISSUE-029-range-evidence-and-split-source.md)、[唯一JSON索引](../verification/ISSUE-018-range-acceptance.json)、[报告](../verification/ISSUE-018-range-acceptance.md)、[运行登记](../verification/ISSUE-018-T14-runs.md#issue-029-主营业务拆分source)；`control-plane/e2e/tushare-range-evidence.js`、`tushare-live.spec.js`及测试侧`TushareRangeSourceProbe.java`。
- **Decision:** schema2支持严格RESPONSE_ONLY；schema1整个历史拒绝新规则。指定SOURCE先按完整runId/caseId定位再核对清洁身份、当前引用、API/日期轴/参数与依据，错误不回退；新响应采集计划强制完整身份。初始证据和执行共用sourceBindings，成功TASK还需实际SQL事实。
- **Rationale:** 新mainbz年窗与旧whole可有相同参数/74行，必须区分身份；来源通过不证明任务写入或上游保证完整。100是已采用工程阈值，SPLIT父不计成功行/投影。
- **Constraint:** 新run为`issue026-mainbz-split-source-20260914T013736Z`，四个完整caseId为该run加`-000001-annual`、`-600000-annual`、`-000001-wide`、`-600000-wide`。股票000001.SZ/600000.SH，annual为20250101～20251231，wide为20200101～20251231，REPORT_PERIOD/end_date、省略type。保留原12项精确旧SOURCE、旧四满额EVIDENCE_MISSING及所有旧run/case；不能重跑来源、重标失败或添加伪TASK。
- **Usage:** 按设计机械映射母输入表268项和新四项，共272项SOURCE；四新来源尚未加入正式interface.cases，本项配置候选时加入。保留26轮826case928请求及四旧AVAILABLE引用；30项采用规则/依据/候选v2后仍NEEDS_VERIFICATION。使用validateEvidence/validateCasePlan/selectTaskCases核对完整绑定；未来TASK ID为`issue026-task-<完整SOURCE caseId>`。
- **Readiness evidence:** 四SOURCE均PASS，实际请求1/3/15/23、来源行74/110/458/657，合计42请求、19 SPLIT父、23成功叶、1299行；exit0、cleanup PASS，源码/两包/输入稳定。Node98、Probe67、相关154、隔离acceptance后端/包1132及前端524通过，独立组件/整体/实际证据审查PASS。索引SHA-256为`2abab2e9ccef3fe0179989cccc9c2bc351326b4c39aea32ad7498a0e4023a510`；旧25轮822case和40个interface逐对象保持，原12/新4的16项精确绑定已离线核对。

三项输入的规则、历史摘要及证据边界一致，没有未解决的依赖冲突。当前生产仍4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY；候选配置、来源引用与独立预期是本后继的实际工作。

## Start Here

按顺序读取：

1. [ISSUE-030完整设计](../task-designs/ISSUE-030-design.md)及[本项问题](../issues/problems/ISSUE-030-range-candidate-policies.md)。
2. [共享设计](../task-designs/ISSUE-026-design.md)第1/2/4节及[母issue七组精确输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md)。
3. 上述ISSUE-027、028、029实际交付记录及当前源码。
4. 唯一JSON/报告和T14运行登记，按完整身份消费SOURCE。

第一动作：在`TushareBatchPoliciesTest`和`TushareBatchAvailabilityTest`补上已完成设计中的30项独立能力预期（十一项不可拆RESPONSE_ONLY及新v2），运行设计Tests中的定向Maven命令观察RED，再最小实现候选配置。新增文件加入Git，保留所有前序未提交成果；本交接不启动实施。

## Risks

- 本地候选AVAILABLE与正式验收AVAILABLE不同；仍缺278真实TASK/SQL，不能提前关闭母任务。
- 同参数来源、历史失败/空和直接BSE负例必须保留准确身份及原事实，不能从索引随意选第一个PASS。
- 候选修改生产源码，必须重新冻结并构建两包；ISSUE-029的已验证包只证明前序来源执行身份，不能作为新候选包。
- 普通测试继续使用无真实账户/业务DB的隔离环境；后继真实任务可能为空、失败或遇到上游修订，按ISSUE-031实际结果处理。
