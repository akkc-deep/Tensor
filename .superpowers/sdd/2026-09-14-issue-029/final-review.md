# ISSUE-029 整体独立审查

## 真实 SOURCE 前置结论

- 规格符合性：**PASS（代码与执行前置门禁）**。
- 代码质量：**PASS**。
- 可操作发现：**无**；没有发现阻止已登记四项 SOURCE 执行的具体代码或就绪问题。
- ISSUE-029 最终来源验收：**待真实执行及限定证据复审**。本结论不表示四项 SOURCE 已执行或通过，也不开放生产候选或授权 TASK/SQL。

审查依据为已完成设计、实施计划、`final-code.diff`、Task 1/2 报告和独立审查，以及私有 prepare/build/run-source/freeze-identity/verify-delivery 脚本与冻结输入。Task 1 fix round 1 的限定复审 PASS 已取代最初 FAIL，三项已知发现全部关闭。ISSUE-027/028 的继承改动未当作本次新增差异。

## 整体合同核对

1. `tushare-range-evidence.js:420-434,484-555` 对 schema 1/2 和全部历史 case 实施 RESPONSE_ONLY 规则；新语义仅限决定中的十一接口，TASK 必须绑定独立清洁、同 API/日期轴/参数的完整 SOURCE 身份。PASS 继续要求完整单请求、整窗单成功叶与通用 TASK/SQL 字段；UNKNOWN 不可选择或 AVAILABLE。原树、ROW_LIMIT/CALENDAR_COVERAGE 与 AVAILABLE 检查未被放宽。
2. `tushare-range-evidence.js:585-643` 按明确 runId/caseId 选择来源，并检查 interface 引用、清洁 run 和全部规则依据；错误身份、多身份、歧义均拒绝。RESPONSE_ONLY 新清单要求完整身份，严格规则保留既定旧清单兼容。`tushare-live.spec.js:517-531,1562-1572` 的初始证据与运行阶段复用同一 Map，参数比较不依赖键插入顺序。
3. `tushare-live.spec.js:1047-1069,1330-1424` 与 `tushare-range-evidence.js:692-734` 消费严格 HTTP DTO、持久 extraction 版本/规则、全部分页、一次请求/尝试和整窗叶子；Int64 经有界安全整数转换。页面持续展示未确认提示，原始行、写入计数、SQL 及页面事实分别核对。已知非空来源的空 TASK 保留 SUCCEEDED 事实并降为 EVIDENCE_MISSING。
4. `TushareRangeSourceProbe.java:233-263,316-356,451-460` 仅对 mainbz RANGE 启用 100 阈值递归；每响应先校验，满额父保留 SPLIT 且不投影，两个相邻子节点先登记再左后右执行。单日满额失败；成功汇总只使用成功叶，合法空叶可存在，整窗全空不 PASS。生产策略未打开，POM 仅增加 test 依赖复用 DateRangePlanner。
5. Probe 输出与通用树消费者匹配：成功树覆盖父窗，失败保留实际叶及 NOT_RUN 兄弟；请求计数包含父请求，父行数/写入保持 null。`candidateLimitReached` 与拆分父数、未解决满额叶分离，不会把完整拆分误判为失败。业务键唯一数与原始叶行数保持不同含义。

## 执行前置身份与边界

已独立读取登记清单并核对 SHA-256 为 `305121ee329ae4ba8c5836bab04cbf01cd9602cedca80a9e573f8e168d6541ea`。runId 为 `issue026-mainbz-split-source-20260914T013736Z`，四个后缀依次为 `000001-annual`、`600000-annual`、`000001-wide`、`600000-wide`，股票分别为 000001.SZ/600000.SH，年窗 20250101～20251231，宽窗 20200101～20251231，均为 REPORT_PERIOD、省略 type。清单与私有目录权限分别为 0600/0700。审查时运行目录只有 cases.json，没有真实输出。

最坏请求数 729、729、4383、4383 合计 10224，已登记且不作为突破预算依据。`Context:815-826` 固定共享 30 分钟/5000 次，HTTPS gate 复用 context 和 2000ms 间隔；wrapper 的 35 分钟进程上限用于外层收尾，不扩展 Context。`run-source.py:64-94,115-130` 在前后核对完整快照、两包、manifest/examples 及输入哈希，已有输出拒绝重跑，输出与身份排他创建，环境白名单只向受控 Maven/Probe 进程注入来源凭据，没有业务数据库环境或 TASK 入口。未读取凭据值。

隔离快照 `/private/tmp/issue029-work-20260914T020500Z` 的 **879 个文件已由本审查重新逐文件哈希**，整体 SHA-256 与冻结值一致：`647b11c1cdfda9949e9a20b072246acfdad3c899cdd787b40cb4cb8d0511f79b`。两个 JAR、manifest/examples 也重新计算并匹配。工作区与快照唯一差异为进行中的 `docs/verification/ISSUE-018-T14-runs.md` 登记文档；执行代码一致，代码范围没有未纳管文件。

| 身份 | SHA-256 |
| --- | --- |
| SOURCE diff | f81c6de542e22d3e28d8505cf4997765b370cb2878a833126a49275c3754eadb |
| 生产包 | f58bc3073e7ea5b6ed19cb9c4561f26e2a4ac08e9607de29c723f475882eaca8 |
| 验收包 | 82444713eb4336ac12de10b8da8a4cddbbee9b2ef26979aaff7635c61efb10d1 |
| manifest | 386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984 |
| request examples | 6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f |

## 验证证据及后置门禁

读取 `isolated-node.log` 确认 Node **98/98**；`build.log` 与 `acceptance-summary.json` 确认隔离 acceptance verify **BUILD SUCCESS**，后端/包 **1132 tests、66 suites、0 failures/errors/skips**，前端 **524/524**、Vite 构建通过。Task 2 独立复审还确认 Probe **67/67**、相关回归 **154/154**。本审查未重跑广泛测试，也未执行真实 API、SQL 或浏览器。

已独立深比较正式索引与基线：schemaVersion 为 2，旧 **25 runs/822 cases**、全部 **40 interfaces** 和 inputHashes 完全相同。四项真实输出尚不能作为验收事实。

执行后必须保持以下既定验收门禁：

- 按原登记一次执行，失败/预算问题保留实情，不换参数或重试；通过必须四 case 均 PASS、清洁退出/清理且冻结身份未变。
- 使用实际完整树核对相邻覆盖、全部成功叶 <100、每 case 至少一叶非空、父不计行及请求数/节点数一致；原始行与唯一业务键分别记录，SOURCE 的 TASK/写入/SQL 字段仍为 null。
- 仅追加实际 SOURCE run，保留旧对象/四项历史失败/生产策略；`verify-delivery.mjs:9-69` 已具备该断言及内存候选的旧 12/新 4 完整身份绑定检查，并明确拒绝新 000001 年窗的歧义及错误 run 身份。该脚本此时尚未作为真实结果运行。
- 实际 run、索引及交付文档完成后做限定证据复审，再判定 ISSUE-029 最终 Acceptance。接口配置留给 ISSUE-030，TASK/SQL 留给 ISSUE-031。

## 真实 SOURCE 后限定证据复审

### 最终结论

- ISSUE-029 规格与实际证据验收：**PASS**。
- 整体代码/证据质量：**PASS**。
- 阻断或非阻断发现：**无**。四项真实结果已满足前置审查列出的后置门禁，本结论将前文“待真实执行及限定证据复审”更新为已完成。

本轮仅核对真实 SOURCE、冻结身份、正式索引及对应说明；代码没有后续修改。没有再次执行 Tushare、TASK、SQL、浏览器或广泛测试，也没有修改索引/生产代码。

### 实际结果与完整树

独立读取私有 `cases.json`、`source-input-identity.json`、`source-evidence.json`、`source-run.json`，确认预登记清单哈希未变、输入身份一致、原 Probe 输出与安全 run 中 cases 完全相等、正式索引最后一个 run 与私有 run 逐对象相等。run UTC 为 `2026-09-14T02:09:53.597Z`～`2026-09-14T02:11:21.644Z`，exit 0、cleanup PASS；日志记录实际 Probe 入口 **1 test、0 failures/errors/skips、BUILD SUCCESS**。

| case 后缀 | 请求 / SPLIT / 成功叶 | 成功叶原始行数（树节点顺序） | 原始行合计 | P / D / I |
| --- | --- | --- | ---: | --- |
| 000001-annual | 1 / 0 / 1 | 74 | 74 | 52 / 14 / 8 |
| 600000-annual | 3 / 1 / 2 | 55, 55 | 110 | 74 / 20 / 16 |
| 000001-wide | 15 / 7 / 8 | 41, 82, 38, 75, 37, 74, 37, 74 | 458 | 326 / 84 / 48 |
| 600000-wide | 23 / 11 / 12 | 58, 54, 55, 52, 55, 55, 55, 53, 55, 55, 55, 55 | 657 | 443 / 117 / 97 |

独立递归断言全部 **42 节点**：每项恰一根且等于登记父窗，19 个 SPLIT 父均恰有两个子节点，并且每个分点等于既定中点算法、左右闭区间相邻；所有节点可由根到达且没有重复，23 叶全部 SUCCEEDED、行数 <100，每项非空。父行数/写入为 null，请求数等于实际节点数，叶行数之和为 **1299**。因此不是仅按摘要声称覆盖；已对完整实际树逐节点核对。

全部 P/D/I 之和等于成功叶行数，日期集合在原父窗内、与报告期投影一致；未解决满额叶、分类/日期/业务键不可用数均为 0。本轮各项唯一业务键数恰等于原始行数，跨类同键数为 0，文档明确限定为本轮观察。SOURCE 的 taskId、submissionId、写入/更新、SQL、ownershipSummary、businessKeyDigest 均为 null；没有伪造 TASK/SQL。expectedCoverage 为 `ACCEPTED_ROW_LIMIT_COVERAGE`，来源依据精确等于登记 refs，上游完整性 UNKNOWN 说明保留。

### 执行后身份、历史保持及绑定

本轮再次重新计算 **879 文件 snapshot、sourceDiff、两包、manifest/examples**，全部与前置审查所列冻结 SHA-256 和实际 run 身份相同；当前工作区执行代码与隔离源码逐文件相等。清单 SHA-256、0700 目录与所有 0600 私有文件权限也再次确认。wrapper 已记录执行前后身份/日志清理 PASS；本轮 42 请求、约 88 秒，遵守既有共享预算，实际 Maven 日志只记录一次显式 Probe 入口。

正式索引 SHA-256 为 `2abab2e9ccef3fe0179989cccc9c2bc351326b4c39aea32ad7498a0e4023a510`。独立深比较确认旧 **25 runs/822 cases**、全部 **40 interfaces**、inputHashes 与基线完全相等；仅追加新 run 的四 SOURCE，现为 schema 2、**26 runs/826 cases/928 请求**。旧四项满额 EVIDENCE_MISSING 因完整旧 run 保持而未重标。

另以只读 Node 检查调用真实消费者 `validateEvidence`，并在内存候选中独立执行原 12/新 4 的完整身份选择：**16 项逐一匹配**，新 000001-annual 明确绑定新 run；去掉身份的同参数歧义、错误 run 身份均拒绝。该检查未将候选写入正式索引，未创建 TASK。已读取 `final-node.log` 确认追加后 **98/98**、0 失败/跳过，并新鲜执行两种 diff check 均退出 0。

`ISSUE-029-range-evidence-and-split-source.md`、`ISSUE-018-T14-runs.md` 实际结果节和 `ISSUE-018-range-acceptance.md` 新增章节的请求、叶子、行数、时间、身份与原件一致。ISSUE-029 可据此完成；生产候选配置/引用仍属 ISSUE-030，真实 TASK/SQL 仍属 ISSUE-031，不因本次来源通过而提前完成。
