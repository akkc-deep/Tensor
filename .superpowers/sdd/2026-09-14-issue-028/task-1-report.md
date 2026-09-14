# Task 1 后端与正式合同实施报告

## 结果

ISSUE-028 的后端 HTTP 与正式合同已消费 ISSUE-027 的持久策略摘要。`TaskResponse.extraction` 现在为必需字段：SINGLE 精确为 `null`，RANGE 精确为保存的 `{policyVersion, ruleKind}`。详情和列表都从同一个 task snapshot 取得 controls 与 policy summary；不会从当前 capability 推断历史规则。

正式 schema 支持 `RESPONSE_ONLY`，要求无 rowLimit、有非空白 evidence，并在 AVAILABLE 与 NEEDS_VERIFICATION 两种形状下都要求 `NATIVE_RANGE`、`splittable=false`。TaskPolicySummary 为关闭对象，版本不得为空或纯空白，规则只允许 CONFIRMED_ROW_LIMIT、VERIFIED_RULE、RESPONSE_ONLY、UNKNOWN。OpenAPI 明确摘要来源，并明确 RESPONSE_ONLY 的 SUCCEEDED 只表示返回记录处理成功，不保证上游区间完整。

## 修改文件

- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskResponse.java`
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadTaskController.java`
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskContractTest.java`
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskControllerTest.java`
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskControllerIT.java`
- `docs/contracts/download-task.schema.json`
- `docs/contracts/download-task-examples.json`
- `docs/contracts/openapi-v1.yaml`
- `.superpowers/sdd/2026-09-14-issue-028/task-1-report.md`

正式 examples 保留全部原例并补齐 extraction，新增：

- `responseOnlyAvailableCapabilities`
- `responseOnlySucceededTask`
- `responseOnlyEmptyTask`
- `historicalConfirmedRangeTask`
- `historicalUnknownRangeTask`
- `singleTask`

合同中的生产现状统一为 4 AVAILABLE / 30 NEEDS_VERIFICATION / 6 SINGLE_ONLY；controlled fixture 仍不代表生产准入。

## RED 证据

运行环境使用 Java 21.0.11 Oracle、离线 Maven、白名单环境，并通过 `-Dskip.npm -Dskip.installnodenpm` 避免与父任务前端修改并发。

1. `/private/tmp/issue028-control/contract-red.log`
   - 命令目标：`DownloadTaskContractTest`
   - 9 项执行，2 failures，0 errors，0 skipped。
   - 新能力用例因 `RESPONSE_ONLY` 不在 schema enum 而失败。
   - 新任务摘要用例因 schema 禁止未知 `extraction` 字段而失败。
2. `/private/tmp/issue028-control/http-contract-red.log`
   - 命令目标：`DownloadTaskContractTest,DownloadTaskControllerTest`
   - testCompile 精确失败于缺少 `DownloadTaskResponse.from(snapshot, controls, summary)` 三参入口及 `extraction()` accessor。

最初复制的 ISSUE-027 wrapper 仍指向旧日志目录，且 Maven 默认生命周期带入父任务当时未完成的前端 fixture；该次运行未作为 RED 证据。随后将 wrapper 的日志目录修正为 `/private/tmp/issue028-control` 并显式跳过前端插件，以上两次才是可归因的 RED。

## GREEN 证据

1. `/private/tmp/issue028-control/contract-unit-green-escalated.log`
   - Contract 9/9，Controller 2/2；合计 11/11。
   - 0 failures，0 errors，0 skipped。
2. `/private/tmp/issue028-control/controller-it-green.log`
   - ControllerIT 15/15。
   - 覆盖 SINGLE null、RESPONSE_ONLY 保存摘要、当前 capability 版本/规则变化后历史不变、旧严格/UNKNOWN、列表详情一致、损坏快照在详情与列表均映射 500 QUERY_FAILED 且不泄露保存内容。
   - 0 failures，0 errors，0 skipped。
3. `/private/tmp/issue028-control/backend-targeted-green.log`
   - DownloadTaskServiceTest 27/27。
   - DownloadTaskJsonTest 21/21。
   - TushareBatchAvailabilityTest 3/3。
   - DownloadTaskControllerIT 15/15、DownloadTaskContractTest 9/9、DownloadTaskControllerTest 2/2。
   - 合计 77 项，0 failures，0 errors，0 skipped，BUILD SUCCESS。
4. `/private/tmp/issue028-control/response-only-outcomes-green.log`
   - 独立审查后新增的参数化 HTTP 执行测试 4/4。
   - 受控来源真实覆盖非空成功、空成功、来源失败、中断；每种结果都核对详情和列表的 extraction、status、counts，失败/中断保留 lastError 且不成为成功。
   - 0 failures，0 errors，0 skipped，BUILD SUCCESS。

此外，`jq empty docs/contracts/download-task.schema.json docs/contracts/download-task-examples.json` 通过；任务范围文件的 `git diff --check` 通过。Mockito 在沙箱内因 Java agent 自附加权限初始化失败，按任务简报在沙箱外用相同白名单环境复跑后通过；日志中只有 JDK 动态 agent 的未来兼容警告。

## 未执行与关注点

- 未运行完整 `-Pacceptance verify` 或任何真实来源/真实任务；父任务负责最终聚合门禁。
- 未运行前端测试；父任务独立负责 control-plane 与页面验证。
- 未修改生产候选数量、来源、任务、证据索引、提交/批次 DTO 或数据库格式。
- 未提交或推送。
