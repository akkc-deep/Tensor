# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-026/ISSUE-026-task-board.md`。
- **Completed task:** `ISSUE-027`，COMPLETED。
- **Next task:** `ISSUE-028`，按既定Order2选取。
- **Design document:** `docs/task-designs/ISSUE-028-design.md`，已完成并链接自看板；[设计](../task-designs/ISSUE-028-design.md)。
- **Expected next status:** `READY`；写入并链接本交接后记录NOT_STARTED → READY，不开始实现。

## Next Task

ISSUE-028：贯通响应采集接口合同与页面说明。消费后端policySummary，为任务详情/列表增加extraction并同步正式schema/examples/OpenAPI、严格前端解析及下载/任务页面提示。验收要求RANGE精确持久规则/版本、SINGLE null，RESPONSE_ONLY无阈值/不可拆/原生；历史规则不受当前能力变化影响；提交前与终态持续说明完整性未确认，非空/空/失败准确区分；受控合同/页面检查和独立审查通过。生产开放、证据工具和真实任务不在本项实施范围。

## Dependencies

### ISSUE-027

- **Artifact:** `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java`的public `policySummary(DownloadTask)`与嵌套`TaskPolicySummary(String policyVersion, CompletenessRule.Kind ruleKind)`；公共`BatchDownloadDescriptor`/`BatchAssessment`枚举；[实际验收](../verification/ISSUE-027-response-only-backend.md)。
- **Decision:** RESPONSE_ONLY是独立的返回记录采集合同，不表示完整性；合法原生单请求单叶子、无阈值、不可拆。summary对SINGLE返回null，对RANGE只解析持久policySnapshot；旧严格/UNKNOWN保留。
- **Rationale:** 延续ISSUE-025用户接受不完整的决定及共享设计，避免当前插件升级改写历史含义。
- **Constraint:** 不把新规则映射成COMPLETE或用UNKNOWN放行。非法存储JSON仍报存储错误，不能回退当前能力；不暴露内部快照/摘要哈希/执行权限。HTTP只增加TaskResponse.extraction，receipt/批次不扩字段。
- **Usage:** controller对同一snapshot.task调用summary并传入DTO；前端严格解析其精确二字段对象、驱动历史提示和结果文案，按专属设计同步正式schema及fixture。
- **Readiness evidence:** 专项280项（含11项MySQL）、两包7项和前端468项通过，失败/错误/跳过0；独立及增量审查通过。新文件已Git纳管；生产createPolicies注册/版本与开始时一致，唯一真实索引字节不变，仍25轮822case886请求、4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY。没有新真实来源/任务。

共享设计第3节、ISSUE-027实现及专属设计使用同一规则、版本和空/失败合同，无冲突。后端JSON schemaVersion=1是任务内部快照版本，不是ISSUE-029未来验收索引schemaVersion=2。

## Start Here

按顺序读取[ISSUE-028完整设计](../task-designs/ISSUE-028-design.md)、[子看板](ISSUE-026/ISSUE-026-task-board.md)的ISSUE-028行、[ISSUE-027实际验收](../verification/ISSUE-027-response-only-backend.md)、[共享设计第3节](../task-designs/ISSUE-026-design.md)，再查看当前DownloadTaskResponse/controller、正式schema和downloadTaskDtos.js。

第一动作：在`DownloadTaskContractTest`与`downloadTaskDtos.spec.js`增加RESPONSE_ONLY合法能力/任务、RANGE缺extraction和SINGLE非null拒绝用例，运行得到真实RED；随后按设计补后端extraction及正式schema，保持全部生产候选不变。

## Risks

严格前端和schema必须同步全部实际TaskResponse fixture；不能把缺字段默认为旧任务。测试中的新能力须使用受控来源/fixture，不能为页面提前开放生产。合法空只说明本次请求未返回记录；SINGLE及严格规则原行为保持。ISSUE-028当前只有设计/交接准备，不是功能或页面已通过。
