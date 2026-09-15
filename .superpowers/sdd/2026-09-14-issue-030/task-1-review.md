# ISSUE-030 Task 1 独立审查

## Verdicts

- Spec compliance: **PASS**.
- Code quality: **APPROVED**.
- Critical: **0**. Important: **0**. Minor: **0**.

## Findings

无。

## Review basis

完整读取了 `task-1-brief.md`、`docs/task-designs/ISSUE-030-design.md`、`task-1-report.md` 和隔离基线产生的 `task1-review.diff`，并仅为核对具体合同读取了当前策略实现、相关前端 fixture/受控任务用例及正式索引中的候选 SOURCE case。本审查没有修改实现、运行测试、提交代码或使用子代理；采信报告中的 focused GREEN 158 项和最终策略 121 项全通过证据。

生产和两份独立测试矩阵均为 40 个注册接口、34 个 RANGE、6 个 SINGLE_ONLY；34 个 RANGE 中 31 个 `NATIVE_RANGE`、3 个逐日计划，规则分布为 22 个 `ROW_LIMIT`（18 新、4 原有）、11 个 `RESPONSE_ONLY` 和 1 个 `CALENDAR_COVERAGE`。逐接口参数形状、日期轴、输出列、规划模式、阈值和可拆性与设计第 1 节一致，30 个新候选均为 `AVAILABLE` / `tushare-range-v2` / `2026-09-14`。四个原候选通过原 `sourceCases` 分支继续保留原规则、版本、`2026-09-13` 日期、备注及完整 evidence 字符串；没有被新候选映射覆盖。

30 个新增候选各有采用决定、逐接口验收报告、官方 URL 和完整 `runId/caseId` SOURCE 引用。静态独立核对确认这 30 个 case 身份无重复，全部存在于当前正式索引，并分别匹配声明的 API、父 runId、`RANGE`、`SOURCE` 和 `PASS`。这不替代 Task 2 的精确 272 项来源清单、绑定与历史保全验收。

响应边界保持正确：11 个 `RESPONSE_ONLY` 都不可拆、以完整父窗形成单叶计划，合法空、非空和超过任意候选数目的响应仍返回 `RESPONSE_ONLY`，同时保留 envelope、字段、日期、股票范围和参数一致性拒绝。22 个限量规则以原始行数执行严格 `< limit` 判定，等于阈值即请求拆分；单日等号路径仍产生 `SPLIT_REQUIRED`，由既有执行边界处理最小窗失败。`trade_cal` 继续要求逐自然日完整、无重复覆盖，不受响应采集合约放宽。

`.BJ` 只在 `top_list` 的交易日历规划中映射到 `SSE`；实际逐日请求和响应范围检查继续使用原 `.BJ` 股票。受控客户端测试覆盖先请求 SSE 日历、再以 `920008.BJ` 请求 `top_list`。直接 `trade_cal exchange=BSE` 在规划和来源参数边界本地拒绝，零预留、零 HTTP；`margin` 的 `SSE`、`SZSE`、`BSE` 三个 `exchange_id` 均保留。

前端 `RANGE_EXPECTATIONS` 是显式的 34 项独立矩阵，能力 fixture 按矩阵产生三种完整性规则及保存的 `policyVersion/ruleKind`。metadata 受控规格分别断言 `CONFIRMED_ROW_LIMIT`、`VERIFIED_RULE` 和 `RESPONSE_ONLY`，响应采集提示已覆盖表单；既有下载任务规格仍显式覆盖保存的 `RESPONSE_ONLY`、历史 `UNKNOWN`、严格规则和 SINGLE 摘要，不会用当前能力改写历史任务语义。

完整 acceptance、实际浏览器执行、精确 272 项绑定和两包身份属于后续 Task 2/Task 3；它们未被本次 Task 1 实现或报告提前宣称完成，因此不构成本审查缺陷。
