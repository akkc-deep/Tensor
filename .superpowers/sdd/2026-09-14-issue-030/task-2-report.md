# ISSUE-030 Task 2 实施报告

## 结果

完成正式候选索引与离线输入重建：30 个候选保持 `NEEDS_VERIFICATION` / `tushare-range-v2`，按母 issue 七组输入和 ISSUE-029 新增输入精确绑定 272 个完整 `runId/caseId` SOURCE 身份。索引继续保留 4 个 `AVAILABLE`、30 个 `NEEDS_VERIFICATION` 和 6 个 `SINGLE_ONLY`。

- 独立输入组数为 ISSUE-019 28、ISSUE-020 12、ISSUE-021 38、ISSUE-022 35、ISSUE-023 35、ISSUE-024 33、ISSUE-025 87、ISSUE-029 4，共 272 项且完整身份唯一。
- 每个离线计划 case 都通过现有 `validateEvidence`、`validateCasePlan`、`selectTaskCases`，并保存恰一个完整 SOURCE 绑定。ISSUE-020 原 12 项和 ISSUE-029 新 4 项 `fina_mainbz` 按完整身份区分。
- 30 项正式接口均保留 `RANGE_TASK_NOT_RUN` 和 `RANGE_SQL_NOT_VERIFIED`；适用接口还保留重叠更新、拆分、修订更新、历史保留或响应完整性未确认的具体未决码。仅有 SOURCE 和清洁旧 SINGLE TASK 不会将正式 disposition 提升为 `AVAILABLE`。
- 十一个 `RESPONSE_ONLY` 接口统一使用精确决定 `docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录`，并保留逐接口报告、官方 URL 和完整 SOURCE 身份。
- 全部历史 `runs` 对象保持，仍为 26 轮、826 case、928 请求；原四个 `AVAILABLE` 对象和六个 `SINGLE_ONLY` 对象保持。旧空样本、失败、四个满额 `fina_mainbz`、BSE 负例未被重标或删除。

本任务没有执行真实 SOURCE、RANGE TASK 或 SQL，没有使用账户、环境凭据或业务数据库，没有修改 Task 1 的生产/前端矩阵、issue board 或状态，也没有提交或推送。

## 文件与接口

- `control-plane/e2e/issue030-range-candidates.js`：导出 `ISSUE030_SOURCE_GROUPS`、`ISSUE030_GROUP_COUNTS`、`ISSUE030_SOURCE_INPUTS`、`ISSUE030_CANDIDATE_APIS`、`applyIssue030CandidateIndex` 和 `buildIssue030CandidatePlan`。
- `control-plane/e2e/issue030-range-candidates-write.js`：离线重建正式索引、272 项计划/绑定和机器摘要，并固定私有文件权限。
- `control-plane/e2e/tushare-range-evidence.test.js`：覆盖正式候选矩阵、独立输入、历史保全、精确绑定、SINGLE 清洁运行要求和负例拒绝。
- `docs/verification/ISSUE-018-range-acceptance.json`：schema 2 正式候选引用，历史 runs 保持。
- `docs/verification/ISSUE-018-range-acceptance.md`：登记候选规则、离线绑定、哈希和未决边界。
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbeTest.java`：测试跟随 Task 1 已实现行为，确认股票日历规划映射后生产请求仍保留 `920008.BJ` 并发出 `trade_date`；直接 BSE 和不完整日历负例保持。
- `.superpowers/sdd/2026-09-14-issue-030/task-2-report.md`：本报告。

## TDD 与验证证据

正式索引定向 RED：

```sh
node --test --test-name-pattern='ISSUE-030 formal index' \
  control-plane/e2e/tushare-range-evidence.test.js
```

结果为退出码 1：1 项测试按预期失败，因为旧正式索引中的 11 个接口仍为 `UNKNOWN`，尚未应用 Task 2 的 `RESPONSE_ONLY` 候选引用。

完成实现后的完整 Node GREEN：

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
```

结果为 104/104 通过，0 失败、0 取消、0 跳过；包括 6 项 ISSUE-030 专项测试及既有严格证据工具回归。

父任务隔离集成首次暴露 Probe 测试仍预期旧生产拒绝：`TushareRangeSourceProbeTest` 67 项中 1 项失败，整体 79 项中 1 项失败，日志 `/private/tmp/issue030-control/candidate-integration.log`。修正该测试与 Task 1 已实现行为一致后，定向 GREEN：

```sh
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am \
  -Dtest=TushareRangeSourceProbeTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果为 67/67 通过，0 失败、0 错误、0 跳过，reactor `BUILD SUCCESS`。

另外，两个新增 JavaScript 文件均通过 `node --check`，`git diff --check` 和 `git diff --cached --check` 均通过。

## 冻结产物

私有目录 `/private/tmp/issue030-control` 为 `0700`；以下三个文件为 `0600`：

- `candidate-inputs.json`：272 个离线计划 case，SHA-256 `9c2cb2a9daab04c13bf9aeed5ac69a0d403453611c4a3b40b85d8685837c7b3d`。
- `source-bindings.json`：272 个精确 SOURCE 绑定，SHA-256 `a4ce0c747643c91745726b87b4ea5ae682152ac2e542a6783bcd0402acbbba02`。
- `candidate-summary.json`：schema 2 机器摘要，SHA-256 `fb00c64cc5de75ae7951ba640be771dfa97d079d271112a115e23c3dc1a3c4e0`。

写入工具按紧凑 `JSON.stringify` 计算的历史 runs SHA-256 为 `95b2e7e0a4743f982d1818540ea8075f694050b9e0ac51a13617f63529381313`；父任务严格 verifier 独立计算的 canonical runs SHA-256 为 `7da2f75bbf00648df9157261704e9c99913fb1d9f3acd9775a6f948d3f5ef213`。候选计划哈希与 `candidate-inputs.json` 一致；正式索引 SHA-256 为 `a281a8460c4fdf57cf5a5d7ebdf13324f6579b15d61867d937f28d2e566c6490`。

## 审查与剩余边界

父任务严格交付 verifier 退出码为 0：独立确认历史仍为 26 轮、826 case、928 请求，272 个精确绑定分组为 28/12/38/35/35/33/87/4，四个原 `AVAILABLE` 和六个原 `SINGLE_ONLY` 对象保持，30 个正式接口均为 `NEEDS_VERIFICATION`，其中 11 个为 `RESPONSE_ONLY`。机器结果保存在 `/private/tmp/issue030-control/delivery-audit.json`；父任务另在 `/private/tmp/issue030-control/task2-review.diff` 固定 8 个交付文件的审查差异。

本项只准备本地候选和后继真实验收输入。30 项 RANGE TASK/SQL 仍由 ISSUE-031 执行；`daily` 重叠更新、`fina_mainbz` 拆分、`disclosure_date` 修订更新、三个 SLB 接口的历史持续保留，以及十一项 `RESPONSE_ONLY` 的上游完整性边界，均已作为明确未决码保留。
