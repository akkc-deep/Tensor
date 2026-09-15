# ISSUE-029 工具与主营业务拆分SOURCE验收

2026-09-14，按[专属设计](../task-designs/ISSUE-029-design.md)及[计划](../superpowers/plans/2026-09-14-issue-029.md)实施。保留当前分支与前序未提交成果，没有commit、push或发布。

## 实现

- 证据消费者兼容schema1/2。schema1在整个run/case历史中拒绝RESPONSE_ONLY；schema2限定已采用的十一接口、原生区间、无阈值，以及决定/逐接口报告/官方来源依据。新TASK采用独立RESPONSE_ONLY预期，不能以COMPLETE或自由文本冒充；成功证据需完整来源身份、单请求整段单成功叶子与SQL事实。UNKNOWN继续拒绝。
- 指定run/case先精确定位，再核对当前引用、清洁身份、SOURCE/RANGE/PASS、API、日期轴与参数。旧唯一caseId或唯一候选兼容保留，新RESPONSE_ONLY计划强制完整身份；错配或歧义不回退。初始证据与实际执行共用sourceBindings，运行期按参数语义比较。
- harness复用严格HTTP解析，核对能力和持久extraction的规则/版本、原始/写入数、SQL和持续页面说明；BigInt经有界整数转换进入证据JSON。非空来源对应的意外空TASK保留真实SUCCEEDED，代表性证据记EVIDENCE_MISSING。未执行新的真实TASK或浏览器验收。
- 仅测试侧fina_mainbz RANGE按100工程阈值递归二分；直接复用DateRangePlanner.split，先左后右，每个SPLIT父预先保存两个子节点。父不计成功行数或投影；成功叶<100，单日满额失败。失败、预算和停止保留已完成、失败及未执行事实，无自动重试。插件仅增加tensor-core test依赖，生产依赖/注册/版本保持。

## 真实四项SOURCE

runId：`issue026-mainbz-split-source-20260914T013736Z`。每个caseId为此完整runId、`-`、下表后缀；未来TASK为`issue026-task-<完整SOURCE caseId>`。

| case后缀 | ts_code | start_date | end_date | 请求 | SPLIT父 | 成功叶 | 成功叶原始行数 |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: |
| 000001-annual | 000001.SZ | 20250101 | 20251231 | 1 | 0 | 1 | 74 |
| 600000-annual | 600000.SH | 20250101 | 20251231 | 3 | 1 | 2 | 110 |
| 000001-wide | 000001.SZ | 20200101 | 20251231 | 15 | 7 | 8 | 458 |
| 600000-wide | 600000.SH | 20200101 | 20251231 | 23 | 11 | 12 | 657 |

均为REPORT_PERIOD/end_date、省略type。UTC `2026-09-14T02:09:53.597Z`～`2026-09-14T02:11:21.644Z`，exit0、cleanup PASS；4 PASS、42请求、19 SPLIT父、23成功叶、合计1299来源行。每叶<100、每项非空且整树完整无重叠覆盖父窗；父无写入/成功行及P/D/I、键、日期投影，未解决满额叶为0。原始行数与原业务键去重数分别观察，本轮每项恰好相同，跨分类同键为0；这不是全历史无冲突保证。

expectedCoverage为`ACCEPTED_ROW_LIMIT_COVERAGE`，依据[ISSUE-020决定](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)。上游完整性UNKNOWN事实保留；通过表示已采用工程阈值下的拆分覆盖，不能改写成官方完整性承诺。

全部原安全run对象直接追加[唯一JSON](ISSUE-018-range-acceptance.json)，与私有source-run.json完全相同；完整节点及投影在那里保留。[运行登记](ISSUE-018-T14-runs.md#issue-029-主营业务拆分source)记录执行前固定输入、预算及实际结果。

## 冻结身份与清理

- 完整隔离副本：`/private/tmp/issue029-work-20260914T020500Z`，879个Git管理文件包含所有未提交内容，复制及构建后逐文件相同；snapshot SHA-256 `647b11c1cdfda9949e9a20b072246acfdad3c899cdd787b40cb4cb8d0511f79b`。
- sourceDiff SHA-256：`f81c6de542e22d3e28d8505cf4997765b370cb2878a833126a49275c3754eadb`。
- 生产包SHA-256：`f58bc3073e7ea5b6ed19cb9c4561f26e2a4ac08e9607de29c723f475882eaca8`；验收包：`82444713eb4336ac12de10b8da8a4cddbbee9b2ef26979aaff7635c61efb10d1`。
- manifest：`386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`；request examples：`6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。
- 固定清单SHA-256：`305121ee329ae4ba8c5836bab04cbf01cd9602cedca80a9e573f8e168d6541ea`；私有目录`/private/tmp/issue026-mainbz-split-source-20260914T013736Z`为0700，输入/输出/日志0600。
- wrapper执行前后重算完整snapshot、源码差异、两包、manifest/examples和清单，全部稳定；日志凭据扫描通过，受控进程已退出。至少2000ms间隔，实际42次请求在30分钟/5000请求预算内。最坏10224请求只是树上界，未突破预算执行；无自动重试、业务DB或TASK/SQL。

## 验证与审查

Java21.0.11、Node24.15.0、Maven3.9.15。普通回归环境移除账户和业务DB变量，日志与机器汇总在`/private/tmp/issue029-control/`；临时审查brief/diff/progress归档到其`review-inputs/`，五份最终报告保留在仓库并加入Git。

| 检查 | 实际结果 |
| --- | --- |
| 首批来源/schema RED | 82项，79通过/3个预期失败 |
| Node实现与审查修复 | 最初93通过；修复轮定向4失败→5通过；最终98/98通过 |
| Probe | 初始66项/5个预期失败；最终67/67通过 |
| 相关Tushare回归 | 154/154通过；初次29个WireMock沙箱端口错误保留，获本地执行许可后同环境通过 |
| 隔离acceptance verify | 66测试类、1132项后端/包检查通过（含两包7项），BUILD SUCCESS |
| 前端与构建 | 34文件524项通过，Vite构建通过 |
| 隔离及追加SOURCE后Node | 均98/98通过 |
| 交付核对 | 26轮826case928请求；旧25轮822case、40个interface及inputHashes逐对象保持；原4满额不变；原12/新4共16项完整身份绑定、错run和无身份歧义拒绝 |

最终各项验证失败/错误/跳过均0。1132已包含Probe及相关Tushare类，专项不与全量重复相加；该计数取自真实Probe执行前保存的acceptance-summary.json，不把后来新增的Probe显式入口报告混入构建计数。静态语法及两种diff检查通过。

主要命令：

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -Pacceptance verify
TENSOR_TUSHARE_LIVE_E2E=1 mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbe -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
git diff --cached --check
```

真实命令仅由已审查的私有wrapper执行一次，凭据通过受控环境注入，不进入命令行或仓库。[Task1独立/限定复审](../../.superpowers/sdd/2026-09-14-issue-029/task-1-review.md)、[Task2审查](../../.superpowers/sdd/2026-09-14-issue-029/task-2-review.md)及[整体和实际证据终审](../../.superpowers/sdd/2026-09-14-issue-029/final-review.md)规格/质量均PASS。全历史schema校验、新规则完整来源引用、参数键顺序三项发现已修复并复审关闭。终审独立重算42个节点的二分/覆盖/阈值、1299行投影、879文件/两包身份及16项绑定，未发现剩余问题；四项Acceptance成立，ISSUE-029记录COMPLETED。

## 交付边界

正式schemaVersion已升2，所有interface对象与生产策略字节保持，仍4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY；十一项当前UNKNOWN没有直接开放。四新SOURCE只追加run/case，原mainbz当前引用仍包含旧满额观察，因此当前聚合状态按旧引用保留。离线副本加入新引用后证明原12和新4分别精确绑定；正式候选引用/版本交给ISSUE-030，真实TASK/SQL交给ISSUE-031。母任务保持IN_PROGRESS。

ISSUE-029完成记录后，已完成并先链接[ISSUE-030详细设计](../task-designs/ISSUE-030-design.md)，再写入并链接[后继交接](../task-handoffs/ISSUE-030-handoff.md)，子看板记录ISSUE-030 NOT_STARTED → READY。后继尚未实施；准备设计/交接没有改变本次源码、真实索引或冻结构建身份。
