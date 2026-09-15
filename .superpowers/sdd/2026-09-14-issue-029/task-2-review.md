# ISSUE-029 Task 2 独立审查

## 结论

- 规格审查：**PASS**。
- 代码质量审查：**PASS**。
- 阻断或非阻断发现：**无**。

审查范围严格限定为保存的 `task-2.diff` 中三个文件：`TushareRangeSourceProbe.java`、`TushareRangeSourceProbeTest.java` 和 `tensor-plugin-tushare/pom.xml`。未审查或修改并行 Task 1 的 control-plane 代码。

## 规格核对

1. 递归只由 `mode=RANGE && apiName=fina_mainbz` 启用；`SINGLE` 和其他 RANGE API 继续走原有单请求/原规划分支。阈值固定为已批准的 100，`<100` 才成为成功叶，`>=100` 多日窗口调用 core `DateRangePlanner.split`，递归顺序为左后右；单日 `>=100` 以 `BATCH_COMPLETENESS_UNCONFIRMED` 失败。
2. 每个响应在判断行数和做投影前均经过现有 `policies.assess`，因此 Envelope、API、字段宽度、请求参数、股票范围和日期闭区间校验继续逐响应执行。
3. 满额父节点先标记 `SPLIT`，再同时登记两个相邻闭区间的 `NOT_RUN` 子节点。父节点的 `sourceRowCount/insertedRows/updatedRows` 保持 `null`，汇总跳过 `SPLIT`，日期及 mainbz P/D/I、业务键投影只观察 `<100` 的成功叶。完整成功树要求所有叶成功且总成功行数非零；合法空叶可存在。
4. 失败、停止、deadline 和预算耗尽均立即向外传播，未发现重试路径。已经完成的成功叶保留安全计数与投影；失败叶和未进入执行的兄弟分别保留 `FAILED`/`NOT_RUN`，case 内 `requestCount` 使用 Context 真实新增预约数。`candidateLimitReached`、`splitParentCount`、`unresolvedFullLeafCount` 分开记录，不以遇到过满额父节点否定完整拆分树。
5. 现有消费者兼容性成立：`validateTree` 要求每个 `SPLIT` 恰有两个相邻子节点，当前实现总是在递归前同时创建两子；消费者允许 `SPLIT` 计数为 `null/0`，并按非 `SPLIT` 节点重新核对叶数、成功/失败/空叶数和成功叶行数。成功、部分失败、停止及预算树均满足这套结构，后续追加失败或不完整 SOURCE 时不会因树形本身被拒绝。
6. 输出只包含既有日期、计数、枚举错误码及安全聚合摘要；满额父响应和失败异常原文没有进入证据。测试明确覆盖父响应、业务文本、币种和异常文本不外泄。
7. POM 仅增加 `tensor-core` 的 `test` scope 依赖，用于直接复用 `DateRangePlanner`；未增加生产依赖或复制拆分算法。

## 测试与变更证据

- `task-2.diff` 仅含上述三个目标文件，统计为 Probe `+81/-11`、ProbeTest `+177/-0`、POM `+5/-0`。对当前文件执行 `git apply --reverse --check task-2.diff` 成功，证明保存的补丁与当前 Task 2 结果一致。
- 已读取 `probe-after-refactor.log`：`TushareRangeSourceProbeTest` **67 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS**。
- 已读取 `tushare-related-escalated.log`：`TushareTradeCalendarTest`、`TushareBatchDownloadTest`、`TushareBatchPoliciesTest` 合计 **154 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS**。
- RED/GREEN 日志与实施报告相符：初始 66 项有 5 个预期失败；首次实现剩余 2 个投影断言失败；预算测试先因缺 Context overload 编译失败，最终 67 项通过。沙箱内相关回归的 29 个 WireMock socket 错误也由随后本机重跑的 154/154 通过记录闭合。
- 本次审查未重复运行广泛测试、真实 Probe、API、TASK、SQL 或浏览器；新鲜只读门禁 `git diff --check`、`git diff --cached --check`（限定三个 Task 2 文件）和补丁反向检查均以退出码 0 完成。

## 质量评价

实现将拆分逻辑收敛在一个专用递归方法和一个小型摘要对象中，沿用既有校验、预算、错误分类和投影设施。改动范围小，生产路径没有被打开；测试覆盖完整多层树、左右深度优先、空成功叶、单日满额、坏 Envelope、越界数据、部分失败、stop/deadline、请求预算，以及其他 API/SINGLE 隔离。未发现需要 Task 2 修正的问题。
