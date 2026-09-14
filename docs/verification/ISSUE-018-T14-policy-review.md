# ISSUE-018-T14 四接口候选策略独立审查

2026-09-13。未发现阻止四项候选同步、重建及进入已登记 RANGE 验收的代码问题。本结论只覆盖本地候选策略与相关测试；不宣布最终 AVAILABLE、25 个 RANGE TASK / SQL、完整 SINGLE、T14 或母 issue 完成。

## 范围与输入

已阅读全文 [T14 设计](../task-designs/ISSUE-018-T14-design.md)，核对 [T13 共享策略合同](../task-designs/ISSUE-018-T13-design.md)、[优先 SOURCE 审查](ISSUE-018-T14-priority-review.md)、[轮次登记](ISSUE-018-T14-runs.md)及[实现报告](ISSUE-018-T14-priority-policy.md)。源码审查使用 `git diff -- <文件>`，以原暂存的 T13 为基线，未把 T13 既有实现当成本次新增。

本次检查实现报告列出的八个源码/测试文件：Policy、两个策略/下载测试、服务准入测试、应用配置 IT，以及三个普通浏览器 fixture/spec。另只读追查未变化的 `DownloadTaskService`、`DownloadTaskJson`、`DownloadTaskRunner` 与对应既有回归，以核对拒绝路径和任务定义身份。

本审查未读取凭证、SQL defaults、原始账户响应或账户运行日志；未调用来源、账户 SQL、真实浏览器或创建子代理。仅阅读实现代理的离线测试日志，运行 Node 24 纯校验及 Git 差异检查。只新增本报告，未改源码、索引、看板或暂存区；主流程统一将本文件加入 Git。

## 策略与证据

| API | 规则 / 阈值 | 新版本 / 核验日期 | 实际 SOURCE 引用 |
| --- | --- | --- | --- |
| daily_basic | ROW_LIMIT / 6000 | tushare-range-v2 / 2026-09-13 | 9 项，含跨年 |
| stk_limit | ROW_LIMIT / 5800 | tushare-range-v2 / 2026-09-13 | 8 项 |
| moneyflow | ROW_LIMIT / 6000 | tushare-range-v2 / 2026-09-13 | 8 项 |
| margin_detail | ROW_LIMIT / 6000 | tushare-range-v2 / 2026-09-13 | 8 项 |

`add` 只有这四处显式传入 SOURCE case 后缀；仅这些条目得到 `sourceVerified=true`、非空证据、v2 和新核验日期。证据包含各自规范 Markdown 锚点、完整 runId 与全部 caseId，能力再附对应官方 URL。其余 30 项的规则、阈值、日期、v1、false/null 原值保持；没有全表默认开放或生产验证开关。核验日期表示本次候选复核，公开正文的原获取时间仍保留为 2026-09-12，未改写历史取证时间。

本次用 Node 24.15.0 直接解析生产注册声明，并独立调用 `rangeCapability`：生产仍为 34 个 RANGE Policy；40 项能力精确为 **4 AVAILABLE / 30 NEEDS_VERIFICATION / 6 UNSUPPORTED**。四项均为 STOCK / TRADE_DATE / NATIVE_RANGE，仍使用单股票与闭区间；31 原生、3 逐日、6 SINGLE 的合同未变。11 个 UNKNOWN 条目没有开放；全部 40 个原 SINGLE 入口保持。

对规范索引执行 `validateEvidence` 成功，并逐项核对四项生产和 UI 证据字符串：全部 33 个 caseId 均真实存在于 `issue018-t14-priority-source-20260913T092938Z`，API 与引用接口相同、phase=SOURCE、status=PASS、taskId/submissionId=null；该轮 exitCode=0、cleanup=PASS。每项含两股票 SINGLE、两股票分别整段与两端，daily_basic 另含跨年。未引用旧失败轮中的 PASS 充当新清洁轮证据。

检查时规范索引为 6 轮 / 442 case。原暂存索引的三个 run 与当前前三个 run 逐字段 deepEqual，329 个旧 case 保持不变；其规范 JSON SHA-256 仍为 `bc740683e9b7b15d66ed70d1bbbb23c404c89139374bea11205fb43bec0b79ce`。这一检查不对其他新增轮次做最终验收结论。

所审生产 Policy 文件 SHA-256 为 `bb6513c77595e4cb89de9eb050415f300b9501155108fdc87cfd0f186a46b771`。SOURCE 取证时的 UNKNOWN 观察没有因本地候选开放而被回填成已确认完整。

## 拒绝与完整性边界

- **UNKNOWN：** 构造器仍拒绝 UNKNOWN 且 `sourceVerified=true`，未验证能力公开 UNKNOWN；`plan` 拒绝未验证策略，`assess` 保留 UNKNOWN。任务 runner 在适配与提交前拒绝 UNKNOWN，不会显示完整成功。
- **阈值等号：** `assess` 继续按原始 `rowCount < L` 才 COMPLETE，`=L`、`>L` 都 SPLIT_REQUIRED。新增独立预期让四项直接使用生产策略覆盖 0、L−1、L、L+1，使用重复原始行也不先去重规避阈值。
- **单点满额：** runner 仅允许可拆原生区间且 start<end 时拆分；单点 SPLIT_REQUIRED 返回 `BATCH_COMPLETENESS_UNCONFIRMED`，在适配/写入之前失败。既有 `unknownAndUnsplittableResponsesNeverCommit` 覆盖 UNKNOWN 与单点满额的零提交。
- **BJ/BSE：** `top_list` 的 BJ/未知后缀拒绝、SH→SSE / SZ→SZSE 映射及 `trade_cal` 的 BSE 本地拒绝均未变化，既有参数与受控请求测试仍覆盖。`top_list` 也继续依赖已验证 `trade_cal`。`margin` 保留 `exchange_id=BSE` 的纯参数形状，但其整个 RANGE 仍未验证且不可准入。这里确认的是原有日历门禁；四项原生股票接口没有新增市场覆盖证明，不能把沪深样本写成北交所已实测。
- **历史任务：** v2、完整性规则及 evidence 都进入 `DownloadTaskJson.definitionHash`。`validatedCurrent` 先比较定义摘要再规范化历史参数；runner 在调用前、响应后及写入前复核定义。既有服务与 runner 测试覆盖版本变化后的 `TASK_DEFINITION_CHANGED`、调用前零请求以及调用中变化后零提交。本次没有删改这些路径。
- **六项 SINGLE：** 仍无 RANGE descriptor。生产策略测试逐项验证 plan/sourceParameters/assess 拒绝；服务的 AVAILABLE 过滤在队列计数与 insert 之前拒绝无 descriptor 或不可用能力，因此它们没有获得 RANGE 写入路径。

## 准入与普通浏览器预期

`TushareBatchAvailabilityTest` 使用真实插件、适配器和服务，数据库边界及上游为受控 mock。新增测试逐项验证四 API × 两股票被接受为 QUEUED、planReady=false、原参数保留，未发任何来源请求；30 个未验证 RANGE 逐项拒绝，并明确断言零 insert、零 queuedCount、零上游。全部 40 SINGLE 原参数准入继续覆盖，fina_mainbz 仍为仅 ts_code 的 snapshot。

这个测试没有经过真实 Servlet，因此其独立证据是**服务准入与零来源/写入边界**，不是四项 HTTP 202/Location 或真实任务成功。六项 SINGLE_ONLY 在该测试内检查 UNSUPPORTED 能力，具体拒绝由上述生产策略测试和服务代码路径核对；不能描述成该测试已对 36 个拒绝项各发一次 HTTP。

普通浏览器预期没有从生产响应反算：`ui-redesign.fixtures.js` 保留独立 34 项矩阵和精确四项阈值表；本次纯检查确认其完整 evidence 与生产声明/真实 case 一致。`ui-redesign.spec.js` 最终明确验证四项AVAILABLE默认RANGE，其余默认SINGLE、RANGE禁用并显示原因；随后显式选择SINGLE并保留原提交断言。此修正来自后续G6实证，见下节。`tushare-metadata.spec.js` 对实际能力响应做完整独立对象比较，并额外断言四项 v2/阈值及按钮状态。应用配置 IT 明确要求启动后的 4/30/6 集合。

当前审查只核对这些测试代码和发现结果；应用配置 IT、真实 metadata/UI 浏览器的本次运行结果须由主流程六条门禁取得。

## 验证证据与后续边界

本审查实际执行了 Node 24 纯校验：解析生产注册、精确 4/30/6、四项阈值/版本、逐字 evidence 与 33 个实际 SOURCE 引用、完整 `validateEvidence`、原三 run deepEqual；退出 0。另检查本次差异与本报告格式；未重复 Maven/Vitest/浏览器耗时测试。

已直接核读 `/private/tmp/issue018-t14-priority-policy-tests/` 下离线日志，而非仅采用实现代理结论：

| 现有日志 | 读到的实际结果 | 本次审查的使用范围 |
| --- | --- | --- |
| green.log | Java 8 类合计 254，0 failures/errors/skips，BUILD SUCCESS | 策略109、下载21、日历4、准入3、Probe30、插件12、runner51、服务24 |
| node-unit.log | 34 文件 / 468 项通过 | 前端单测；日志含既有 Vue 受控事件警告，无失败 |
| node-list.log | 7 文件 / 126 tests | 仅发现，不是浏览器执行 |
| node-fixture.log | 4/30/6、33 SOURCE 引用核对 PASS | 本审查另独立执行同类纯校验 |

主流程仍须完整同步八个源码/测试文件，重建并绑定候选生产/验收包，按设计完成当前源码六条门禁及已登记的 **25 个独立 RANGE TASK**（daily_basic 7，其余各6）和 50 次 records 查询、fixture 独立计数、全批次树、SQL、日志与清理。任何失败按设计保留事实、撤回该接口本轮标记并递增版本。此前 SOURCE 包不代替新 TASK 包身份；本报告不减少其余 30 项或代表场景的工作范围。

## 后续G6实证修正（2026-09-13）

上述静态审查中“仍默认SINGLE”的描述不准确：既有产品合同对AVAILABLE默认RANGE。首次G6实际发现三个普通套件遗留SINGLE假设，已在测试中先验证RANGE默认，再显式选择SINGLE验证原参数；不修改产品默认。最终实际结果以 [T14运行登记](ISSUE-018-T14-runs.md) 为准，此静态审查不替代G6。
