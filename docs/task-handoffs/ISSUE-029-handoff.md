# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-026/ISSUE-026-task-board.md`。
- **Completed task:** `ISSUE-028`，COMPLETED。
- **Next task:** `ISSUE-029`，按既定Order3选取。
- **Design document:** `docs/task-designs/ISSUE-029-design.md`，已完成并先链接至看板；[完整设计](../task-designs/ISSUE-029-design.md)。
- **Expected next status:** READY；写入并链接本交接后记录NOT_STARTED → READY，不开始实现。

## Next Task

ISSUE-029：升级验收工具并补齐主营业务拆分来源。实现schema1/2兼容、RESPONSE_ONLY证据规则、指定run/case来源绑定及harness的能力/持久摘要/单请求单叶子核对；仅扩展测试侧mainbz按100阈值二分，取得两股票全年和六年宽窗四项SOURCE。验收要求旧25轮822case逐对象保持、来源身份无歧义、四新完整成功树和清洁源码/两包身份、局部回归及独立审查通过。生产候选和真实TASK/SQL不在本项范围。

## Dependencies

### ISSUE-028

- **Artifact:** `docs/contracts/download-task.schema.json`、`download-task-examples.json`、`openapi-v1.yaml`；`control-plane/src/api/downloadTaskDtos.js`；[实际验收](../verification/ISSUE-028-response-only-contract-ui.md)。
- **Decision:** 必需TaskResponse.extraction，SINGLE=null、RANGE精确保存`{policyVersion,ruleKind}`。RESPONSE_ONLY能力要求无阈值、有依据、NATIVE_RANGE且不可拆；SUCCEEDED只表示本次返回记录处理成功，不保证区间完整性。
- **Rationale:** 贯彻ISSUE-025已接受不完整的决定，并保证历史任务含义不随当前能力改变。
- **Constraint:** 保持严格字段/模式/枚举校验、UNKNOWN拒绝准入及原严格规则；不能从当前能力重建历史摘要。空成功与非空代表性验收分别处理；不把response-only映射为COMPLETE。
- **Usage:** harness直接复用严格parser，核对候选capability、任务保存的规则/版本、单请求完整单叶子及页面文案；绑定SOURCE身份后单独生成采用合同下的TASK预期。
- **Readiness evidence:** 后端1125项（含两包7项）及最终MySQL HTTP19项、前端524项、受控浏览器66项均通过，失败/错误/跳过0；独立前端/整体/增量审查APPROVE。生产策略与唯一索引字节保持，仍4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY和25轮822case886请求；无新真实SOURCE/TASK。

### 共享来源与采用决定

- **Artifact:** [共享设计第4节及mainbz拆分约定](../task-designs/ISSUE-026-design.md)、[ISSUE-020决定](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)、[ISSUE-025决定](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)、[唯一索引](../verification/ISSUE-018-range-acceptance.json)和[运行登记](../verification/ISSUE-018-T14-runs.md)。
- **Decision:** 十一项按响应采集采用；mainbz保持ROW_LIMIT=100且省略type，四新SOURCE固定000001.SZ/600000.SH各20250101～20251231和20200101～20251231。旧12有效SOURCE保持，旧四满额EVIDENCE_MISSING不重标。
- **Rationale:** 上游未承诺十一接口完整性与mainbz已批准阈值是不同合同；新annual与旧74行whole同参数，必须按精确身份区分。
- **Constraint:** 新SOURCE runId=`issue026-mainbz-split-source-<UTC>`；四case后缀000001-annual/600000-annual/000001-wide/600000-wide。执行前固定输入及源码两包，>=2000ms间隔、每轮30分钟/5000请求，无自动重试、SOURCE不入库；失败/未执行不得当PASS。
- **Usage:** 先在受控测试证明两同参数来源的指定选择、schema拒绝和mainbz完整树，再冻结身份执行四来源；结果只追加，供后续候选/任务引用。
- **Readiness evidence:** 上述采用决定及旧来源已交付；四新来源尚未执行，没有行数、请求数或成功结论。SOURCE成功不等于TASK/SQL完成。

上述HTTP、共享规则与来源决定一致，无未解决合同冲突。HTTP内部保存策略版本与证据索引schemaVersion分别管理；ISSUE-028未升级后者。

## Start Here

依次读取[ISSUE-029完整设计](../task-designs/ISSUE-029-design.md)、[子看板](ISSUE-026/ISSUE-026-task-board.md)的ISSUE-029行、[ISSUE-028验收](../verification/ISSUE-028-response-only-contract-ui.md)、共享设计第4节及mainbz约定、唯一索引和原来源登记。

第一动作：在`control-plane/e2e/tushare-range-evidence.test.js`增加两同参数清洁SOURCE的指定run/case选择/歧义拒绝及schema1/2新规则测试，运行`node --test control-plane/e2e/tushare-range-evidence.test.js`观察RED，再按设计实现消费者。Probe和四项真实SOURCE须在对应前置检查及当轮登记之后执行。

## Risks

当前十一项UNKNOWN不能因工具支持直接开放；原74行同参数来源不能替代新annual树。预算可能阻止完整树完成，失败或清理缺口必须保留并停止。真实执行时重新固定UTC、凭据可用性、源码/两包与清单，不能把本交接的就绪状态当成新来源已通过。
