# ISSUE-030 Task 1 实施报告

## 结果

完成 34 个候选接口的明确生产矩阵：22 个 `ROW_LIMIT`、11 个 `RESPONSE_ONLY`、1 个 `CALENDAR_COVERAGE`，均为 `AVAILABLE` / `tushare-range-v2`。

- 为 30 个新候选逐项登记适用决定/设计、逐接口验收报告、官方文档 URL、核对日期 `2026-09-14` 和完整的 `runId/caseId` SOURCE 身份。
- 保留 `daily_basic`、`stk_limit`、`moneyflow`、`margin_detail` 四条旧策略的版本、日期、备注和证据身份。
- `.BJ` 股票只在股票日历规划时映射至 `SSE`，实际接口仍请求原始 `.BJ` 证券代码；直接请求 `trade_cal exchange=BSE` 在发起调用前拒绝。
- margin 继续支持 `SSE`、`SZSE`、`BSE` 三种 `exchange_id`。
- 十一个 `RESPONSE_ONLY` 接口覆盖单叶计划、空/非空响应及结构、日期、股票错误；限量规则覆盖阈值等号和最小日满额，交易日历完整性仍保持严格。
- 前端独立矩阵、能力 fixture、metadata 三规则分支和截断提示已同步；历史 `UNKNOWN` 与严格任务 fixture 显式保留。

本任务没有修改正式证据 JSON/报告、issue board 或公共 schema/枚举；没有执行真实 SOURCE/TASK/SQL、使用在线凭据、提交或推送。

## 文件

- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPoliciesTest.java`
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchDownloadTest.java`
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareTradeCalendarTest.java`
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java`
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbeTest.java`
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java`
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/DownloadTaskApplicationConfigurationIT.java`
- `control-plane/e2e/ui-redesign.fixtures.js`
- `control-plane/e2e/ui-redesign.spec.js`
- `control-plane/e2e/tushare-metadata.spec.js`
- `.superpowers/sdd/2026-09-14-issue-030/task-1-report.md`

## TDD 证据

工作区 RED 命令：

```sh
python3 /private/tmp/issue030-control/run-tests.py task1-red.log \
  -Dtest=TushareBatchPoliciesTest,TushareBatchAvailabilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果为退出码 1。reactor 按预期在 plugin 测试停止：121 项，67 失败、31 错误、0 跳过。日志：`/private/tmp/issue030-control/task1-red.log`。

父任务在隔离基线上独立复核 Availability RED：退出码 1，3 项中 1 通过、1 失败（`adj_factor` 预期 `AVAILABLE`，实际 `NEEDS_VERIFICATION`）、1 个预期业务拒绝错误（队列 admission 不可用）、0 跳过。日志：`/private/tmp/issue030-control/task1-http-red-local.log`。

必需 focused GREEN 命令：

```sh
python3 /private/tmp/issue030-control/run-tests.py task1-green.log \
  -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果为退出码 0：plugin 155 项、app 3 项，共 158 项，0 失败、0 错误、0 跳过。日志：`/private/tmp/issue030-control/task1-green.log`。

加强四条旧策略的精确断言后，定向复跑 `TushareBatchPoliciesTest`：121 项，0 失败、0 错误、0 跳过。日志：`/private/tmp/issue030-control/task1-policy-final.log`。

前端检查：

- 三个修改后的 E2E 文件通过 `node --check`。
- 直接导入 fixture 得到 34 个候选，分布为 22 `CONFIRMED_ROW_LIMIT`、11 `RESPONSE_ONLY`、1 `VERIFIED_RULE`。
- Playwright `--list` 成功解析 `ui-redesign.spec.js` 和 `tushare-metadata.spec.js`，列出 91 项测试。
- `git diff --check` 通过。

## 集成关注

- 完整 acceptance、打包和浏览器验证由父任务在隔离工作区执行。
- sandbox 内 WireMock 端口绑定被拒；使用限定范围的提权复跑后 focused GREEN 通过。另两次无效尝试（sandbox 端口拒绝、Surefire 参数拼写错误）不计入验证结果。
- 本任务只提供候选代码和测试；正式证据 JSON/报告与任务板由后续父任务流程处理。

## Browser fix round

父任务的受控浏览器 RED 为 45 通过、14 失败、7 未运行。前 13 个失败由两类测试假设引起：`RESPONSE_ONLY` 警告 locator 同时命中两个帮助段落，以及候选转为 `AVAILABLE` 后页面正确默认 RANGE，但既有单次流程仍等待 `trade_date`。最后一个 worker 失败和 7 个未运行由浏览器与 Maven 同时操作同一临时 `node_modules` 导致，不作为产品失败。

本轮仅修改测试：

- 两处警告断言改为按完整警告文本筛选目标 `.form-footer__help`，并分别断言可见和文本内容。
- 新增测试辅助方法，在 7 个明确预期 SINGLE 的设置点选择并确认“单次请求”：状态/重试流程、`new_share` 最终提交、五视口循环、业务主题流程、键盘流程、正常截图流程和错误截图流程。
- 保留 40 项矩阵对可用候选默认 RANGE 的断言，以及原有请求、空/非空、历史摘要和页面断言；未修改产品代码、HTTP schema 或证据文件。

静态验证：两个修改后的 spec 均通过 `node --check`；Playwright `--list` 成功解析两个文件的 91 项测试；`git diff --check` 通过。完整浏览器套件由父任务在清洁依赖目录中与 Maven 串行复跑。
