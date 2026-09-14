# ISSUE-028：贯通响应采集接口合同与页面说明

## 当前状态

COMPLETED（2026-09-14）。HTTP合同、严格解析与页面说明已实现，通过后端1144项（含HTTP19及两包7项）、前端524项、受控浏览器66项和独立审查；实际证据见[验收记录](../../verification/ISSUE-028-response-only-contract-ui.md)，权威状态见[子看板](../../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)。

## 目标与范围

让接口和页面准确展示采集规则与任务结果，历史任务的说明来自其保存的策略。

- 消费ISSUE-027的policySummary，增加TaskResponse.extraction并保持列表/详情一致；SINGLE为null，RANGE为准确规则/版本对象。
- 同步download-task.schema.json、download-task-examples.json和openapi-v1.yaml的新枚举、模式约束及示例；保持严格字段校验。
- 更新前端DTO解析、下载页、任务列表/详情及对应fixture；持续展示完整性未确认说明，区分非空采集成功、空响应和失败。
- 新旧任务与当前能力变化分别回归；不改生产候选矩阵，不升级验收索引或执行真实SOURCE/TASK。

## 已知依据

直接消费[共享设计](../../task-designs/ISSUE-026-design.md)：共享设计Approach第3节；ISSUE-027实际交付的summary与枚举；正式HTTP合同和当前前端严格解析/页面。 原采用决定和精确输入继续以[母issue输入表](ISSUE-026-range-task-final-acceptance.md)、[唯一验收索引](../../verification/ISSUE-018-range-acceptance.json)及[运行登记](../../verification/ISSUE-018-T14-runs.md)为准。拆分没有产生新的实现、SOURCE、TASK、SQL或测试结果。

## 依赖与处理顺序

- 直接子issue前置：ISSUE-027；共享ISSUE-019～025采用决定与来源作为母任务已交付输入，不能推定未来新来源已成功。
- 串行顺序：ISSUE-027 → ISSUE-028 → ISSUE-029 → ISSUE-030 → ISSUE-031 → ISSUE-032；编号顺序不代替依赖的实际验收。
- 第一动作：读取ISSUE-027实际交付的summary合同，对照共享设计第3节的正式schema及页面规则，消费已完成的docs/task-designs/ISSUE-028-design.md，先补正式schema与DTO的新规则/缺字段失败测试。

## 关闭条件

- HTTP实际响应、正式schema/examples/OpenAPI和严格前端解析一致；RESPONSE_ONLY要求无阈值/不可拆/原生，缺失或错误extraction拒绝。
- 提交前及任务列表/详情持续显示“数据完整性未确认，可能存在上游截断”；非空成功为“返回记录已采集”，空为“本次请求未返回记录”，失败不显示成功。
- 旧RANGE显示保存的规则/版本，当前能力变化不改写历史含义；SINGLE保留单次语义，不新增确认弹窗或勾选步骤。
- 后端契约、前端和受控页面回归及独立审查通过；向ISSUE-029交付可被harness严格检查的能力/任务响应合同，生产开放状态保持。

## 实施落点与验证

`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskResponse.java`、DownloadTaskController及契约测试；`docs/contracts/download-task.schema.json`、`download-task-examples.json`、`openapi-v1.yaml`；`control-plane/src/api/downloadTaskDtos.js`、DownloadView/DownloadTaskView/DownloadTaskList和相关spec/fixture。

从仓库根使用项目Java21/Node24，按专属设计准备环境后执行以下相关检查；以下为复核命令，已执行的精确命令和结果见验收记录：

```sh
mvn -o -f data-plane/pom.xml -Dtest=DownloadTaskContractTest,DownloadTaskControllerTest,DownloadTaskControllerIT,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
```

## 约束

遵循共享设计、T13/T14及用户已批准差异，保持40接口、原日期/股票/业务键、无自动重试和历史证据。真实调用前固定输入、完整源码/两包身份和受限环境，SOURCE不入库，TASK每轮新空schema；至少2000ms间隔、每轮30分钟/5000请求和清理要求不变。无新原因不重复已有效验证；新增文件加入Git，不自动提交或发布。局部任务完成不替代尚未完成的生产准入/真实任务/母合同验收。
