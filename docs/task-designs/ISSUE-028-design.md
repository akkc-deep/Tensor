# ISSUE-028：响应采集接口合同与页面说明

## Goal

消费已完成的[ISSUE-027后端合同](../verification/ISSUE-027-response-only-backend.md)，让HTTP、正式schema及页面准确表达任务保存的采集规则。身份和状态由[ISSUE-026子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)管理；本设计已实施，实际结果见[验收记录](../verification/ISSUE-028-response-only-contract-ui.md)。

## Scope

实现共享设计[Approach第3节](ISSUE-026-design.md)：TaskResponse.extraction、正式JSON Schema/examples/OpenAPI、前端严格DTO及下载/任务列表/详情文案。保留SINGLE、历史严格/UNKNOWN语义、原参数/计数/业务键与已有布局；不增加弹窗或勾选步骤，不改变生产4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY，不执行SOURCE/TASK、不升级证据索引。证据harness由ISSUE-029处理，生产候选由ISSUE-030处理。

## Approach

### 1. HTTP持久摘要

`DownloadTaskResponse`在mode附近增加必需字段`DownloadTaskService.TaskPolicySummary extraction`。`from(TaskSnapshot, ControlAvailability, TaskPolicySummary)`显式接受summary；在`DownloadTaskController.detail`取得snapshot后，以同一个task调用`service.controls(task)`及`service.policySummary(task)`，传入from。列表现有复用detail的转换继续保证一致，不从capabilities拼装历史含义。

extraction为SINGLE null、RANGE精确`{policyVersion,ruleKind}`，ruleKind支持CONFIRMED_ROW_LIMIT、VERIFIED_RULE、RESPONSE_ONLY、UNKNOWN。policyVersion必须保存值；旧v1/v2、当前插件移除/策略升级后仍显示原规则。不暴露policySnapshot/definitionHash/执行permit。提交receipt和批次DTO不加字段；损坏快照延续现有存储错误映射，不能回退当前规则。后端无需迁移或补写旧任务。

### 2. 正式合同

在`docs/contracts/download-task.schema.json`的CompletenessRule.kind增加RESPONSE_ONLY；该分支要求rowLimit=null、evidence非空。RangeCapability追加跨字段约束：RESPONSE_ONLY只允许NATIVE_RANGE和splittable=false，包括NEEDS_VERIFICATION的形状检查；AVAILABLE仍拒绝UNKNOWN。

新增`$defs.TaskPolicySummary`（object，required为policyVersion/ruleKind，additionalProperties=false），TaskResponse.properties.extraction引用该对象或null，required包含extraction；按mode条件强制SINGLE null、RANGE对象，不接受缺字段/未知规则/额外键/空版本。保留现有时间、计数、状态约束和additionalProperties=false。

`download-task-examples.json`保留全部原例并给每个TaskResponse补准确extraction；新增RESPONSE_ONLY AVAILABLE能力、SUCCEEDED非空和空任务，以及原严格规则/UNKNOWN历史RANGE、SINGLE null例。`openapi-v1.yaml`仍引用正式schema，描述extraction来源及RESPONSE_ONLY下SUCCEEDED只表示返回记录处理成功。ContractTest直接验证正式schema和examples；不能仅验证Java序列化。

### 3. 严格前端解析

`downloadTaskDtos.js`的TASK_KEYS加入extraction；解析函数复用exactObject/nonBlank，RANGE要求精确二字段对象并冻结，SINGLE必须null，错误走现有invalid(requestId)。`completenessRule`允许新枚举、无阈值/有依据；`rangeCapability`补原生且不可拆交叉检查。不得因历史/当前能力不同放宽未知字段或缺字段检查。

所有受影响任务fixture（API、composable、视图、E2E）补字段：SINGLE null、RANGE由该fixture原任务策略给出版本及规则。列表、详情均只消费task.extraction；不通过apiName白名单或当前能力推断历史规则。跨请求状态更新时说明跟随新task快照，保留当前轮询、路由、人工重试行为。

### 4. 文案及展示位置

- `DownloadView.vue`：RESPONSE_ONLY RANGE表单与提交按钮附近显示“按所选日期区间采集本次接口返回的记录。数据完整性未确认，可能存在上游截断。”沿用现有提示排版；SINGLE原说明保持。
- `DownloadTaskList.vue`及`DownloadTaskView.vue`：根据保存的RESPONSE_ONLY规则持续展示“数据完整性未确认，可能存在上游截断”，包括排队/运行/失败/中断/成功。将说明放在任务模式/范围和状态相邻处，普通文本可访问，不靠颜色或仅悬停提示。
- 仅SUCCEEDED且sourceRows>0显示“返回记录已采集”，SUCCEEDED且sourceRows=0显示“本次请求未返回记录”。比较现有BigInt计数使用`0n`；失败、部分失败、中断不显示这两个成功文案，即使已有sourceRows。保留错误、重试/恢复、计数和日期信息。
- 历史UNKNOWN RANGE只说明“数据完整性未确认”，不称完整下载；严格规则与SINGLE原状态文案保持。不要写“该区间没有数据”或“已全部下载”。尽量在现有三个组件内增加条件；若列表/详情需共享相同判断，可提取一个小的纯文案函数，不引入新的状态管理或页面组件层。

## Files

- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskResponse.java`、`DownloadTaskController.java`：summary→HTTP。
- 同目录测试`DownloadTaskControllerTest.java`、`DownloadTaskControllerIT.java`、`DownloadTaskContractTest.java`及构造DTO的相关fixture：列表/详情、schema和存储事实验证。
- `docs/contracts/download-task.schema.json`、`download-task-examples.json`、`openapi-v1.yaml`：正式发布合同。
- `control-plane/src/api/downloadTaskDtos.js`及`.spec.js`：严格能力/任务解析。
- `control-plane/src/views/DownloadView.vue`、`DownloadTaskView.vue`、`control-plane/src/components/download/DownloadTaskList.vue`及对应`.spec.js`：持续提示和成功/空/失败文案。
- 受影响`control-plane/src/api/downloadTasks.spec.js`、composable/flow fixture和`control-plane/e2e/download-tasks.spec.js`、`ui-redesign.fixtures.js`、`tushare-metadata.spec.js`等：准确补原规则/版本，保持原测试覆盖。文件范围由DTO实际消费者搜索确定。
- 本issue问题文档、子看板和验收记录：如实记录最终结果并交ISSUE-029。

## Tests

沿用ISSUE-027已验证的Java21、Node24和白名单受控环境；MySQL8.4.6由Testcontainers管理，本地Docker地址按当时实际context读取。先补正式schema与DTO缺字段/错误模式/非法新规则拒绝测试，观察RED，再补HTTP字段和schema；随后补前端解析与页面行为RED，再最小实现。

```sh
mvn -o -f data-plane/pom.xml -Dtest=DownloadTaskControllerTest,DownloadTaskControllerIT,DownloadTaskContractTest,DownloadTaskServiceTest,DownloadTaskJsonTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
mvn -o -f data-plane/pom.xml -Pacceptance verify
npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js
git diff --check
git diff --cached --check
```

期望全部通过、无失败/错误/未解释跳过；实际数量由报告统计。复用现有受控E2E启动配置和隔离测试库，必要fixture先补全；不得用真实来源或修改生产开关为页面制造场景。要求新规则非空/空/失败/中断、旧严格及UNKNOWN、SINGLE null、当前策略变更后历史不变均有HTTP和页面观察；列表/详情内容一致、提示在终态持续。非法extraction缺失/null/多键/错类型/规则/空版本及非法能力组合必须拒绝。独立审查后才记录完成；最终母任务整套门禁由ISSUE-032汇总，未执行不冒充通过。

## Acceptance

1. 实际HTTP、schema/examples/OpenAPI与前端解析一致；RANGE精确extraction、SINGLE null；RESPONSE_ONLY无阈值/原生/不可拆。
2. 提交前、任务列表及详情持续说明完整性未确认，非空成功、空成功与失败准确区分，不增加用户确认步骤。
3. 历史说明来自保存的策略，当前能力变化不能改写；原参数、计数和人工重放规则保持。
4. 后端/前端/受控页面回归及独立审查通过；生产与真实证据保持，向ISSUE-029交付确定的HTTP合同。新增文件加入Git，不自动提交或发布。

## Risks

严格解析意味着所有TaskResponse fixture必须同步，不可通过允许缺extraction掩盖遗漏。UNKNOWN历史任务与新RESPONSE_ONLY有不同采集依据，文案不能统一为完整成功。ISSUE-027产物没有开放新生产接口，页面场景必须受控注入；具体实现与验证结果以验收记录为准。
