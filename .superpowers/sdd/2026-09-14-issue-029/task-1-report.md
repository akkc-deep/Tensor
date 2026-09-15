# ISSUE-029 Task 1 实施报告

## 结果

仅修改以下任务归属文件：

- `control-plane/e2e/tushare-range-evidence.js`
- `control-plane/e2e/tushare-range-evidence.test.js`
- `control-plane/e2e/tushare-live.spec.js`

实现内容：

- `validateEvidence` 同时读取 schema 1/2；schema 1 拒绝 interface 或 TASK 中的 `RESPONSE_ONLY`，schema 2 只允许决定采用的十一接口。
- `RESPONSE_ONLY` 严格要求 `NATIVE_RANGE`、`rowLimit=null`、ISSUE-025 决定、逐接口验收报告和原官方 URL；UNKNOWN 规则仍不可选择或开放。
- RESPONSE_ONLY TASK 的 `expectedCoverage` 固定为 `RESPONSE_ONLY`，`expectedCoverageSource` 同时绑定决定和完整 `runId/caseId` SOURCE 身份；AVAILABLE 校验依据结构化任务、叶子和 SQL 字段，不读取 `reviewMethod` 关键词。
- RANGE SOURCE 选择优先解析 `docs/verification/ISSUE-018-range-acceptance.json#<runId>/<caseId>`；兼容全局唯一旧 `#caseId`，无身份时只允许唯一合法同参数来源。不存在、错误、多个、未登记或不清洁身份不回退。
- `initialTaskEvidence` 与实际执行共用 `sourceBindings`；响应采集预期来自采用合同，旧 SOURCE 语义独立保留。
- harness 使用 ISSUE-028 严格 capability/task/batch 解析结果，核对保存的 policy/rule、单请求、整段单成功叶子、SQL 和页面状态；严格 DTO 的 BigInt 计数先转为有界安全整数再进入证据 JSON。
- 页面核对非空“返回记录已采集”、空“本次请求未返回记录”，并持续核对“数据完整性未确认，可能存在上游截断”。已知非空 SOURCE 的对应 TASK 若意外为空，保留真实 SUCCEEDED 事实但证据状态为 EVIDENCE_MISSING。

未修改正式证据索引或生产代码，未执行 Playwright、JVM、SQL 或真实 Tushare API，未提交或推送。

## TDD 证据

日志目录：`/private/tmp/issue029-control`（目录 0700，日志 0600）。

- 首轮必需 RED：82 项，79 通过、3 失败。失败分别为 schema2 尚未支持、完整 SOURCE 身份未生效、双合法来源未拒绝歧义。
- RESPONSE_ONLY TASK/初始证据 RED：85 项，83 通过、2 失败。
- 严格 HTTP/runtime/page RED：88 项，83 通过、5 失败。
- 响应样本审查与意外空 RED：90 项，88 通过、2 失败。
- BigInt 证据序列化 RED：92 项，91 通过、1 失败。
- schema1 隐藏 RESPONSE_ONLY TASK 定向 RED：1 项，0 通过、1 失败。
- 最终 GREEN：93 项，93 通过、0 失败、0 跳过、0 todo。

最终命令：

```sh
env -i PATH=/usr/bin:/bin:/usr/sbin:/sbin LANG=C LC_ALL=C \
  data-plane/tensor-app/target/frontend/node/node \
  --test control-plane/e2e/tushare-range-evidence.test.js
```

附加门禁：两个修改后的 JS 文件 `node --check` 通过，`git diff --check` 和 `git diff --cached --check` 通过。

## 集成关注

- 当前正式索引仍为 schema 1。消费者已验证 schema 2 可保持旧对象不变；仅追加一个未引用的 SOURCE run 也不会改变现有 interface 聚合。
- SOURCE 选择按设计要求 `entry.cases` 引用精确 SOURCE case。若本 issue 只追加四项 SOURCE run 而不改 interface 对象，索引可验证，但后继 TASK 暂时不能选择这些新 SOURCE；后续候选登记需把相应 caseId 加到 `fina_mainbz.cases` 后才能绑定。
- 独立代码审查由父任务安排，本报告提交时尚未收到审查结论。

## Fix round 1

已接受并处理 `task-1-review.md` 的 H1、M2、L3：

- H1：schema1 的 RESPONSE_ONLY 禁令提升到全部 `runs[].cases[]`，不再局限于 interface 引用闭包。schema2 对所有 RESPONSE_ONLY TASK（含未引用对象）全局校验十一接口白名单、完整决定与 `runId/caseId` SOURCE 身份、清洁独立 SOURCE、API/模式/日期轴/参数一致性；PASS 继续要求单请求整窗单成功叶。FAILED/NOT_RUN 保留真实字段，未增加 SQL 要求。
- M2：当候选接口采用 RESPONSE_ONLY 时，计划必须携带且只携带一条完整 `#<runId>/<caseId>` SOURCE 引用；ROW_LIMIT/CALENDAR_COVERAGE 的旧唯一候选和旧 `#caseId` 兼容路径保持。
- L3：运行期绑定 SOURCE 与计划参数改为排序键值后的语义比较，参数插入顺序不影响合法执行。

TDD 证据：

- 定向 RED：4 项，0 通过、4 失败，分别复现未引用 schema1 TASK、未引用 schema2 TASK 绕过、RESPONSE_ONLY 唯一来源无完整身份仍放行、重排参数误拒绝。
- 定向 GREEN：5 项，5 通过、0 失败；额外验证 FAILED/NOT_RUN RESPONSE_ONLY 无 SQL 事实仍可表示。
- 最终完整 GREEN：98 项，98 通过、0 失败、0 跳过、0 todo。
- 与 `/private/tmp/issue029-control/before-fix1-*.js` 对比，生产差异仅为上述三项修复；正式 schema2 索引及旧对象回归通过。

Fix round 1 未修改正式索引、Probe 或其他生产文件，未执行真实 API、SQL、JVM 或 Playwright，未提交或推送。限定复审由父任务安排。
