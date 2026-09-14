# ISSUE-030 候选策略与来源交付验收

2026-09-14～15，按[专属设计](../task-designs/ISSUE-030-design.md)及[实施计划](../superpowers/plans/2026-09-14-issue-030.md)执行。候选实现、精确来源映射、新两包及相关验收通过；本项四项Acceptance均成立，记录完成。

## 实际交付

30项新候选按逐接口规则与准确来源配置：11个RESPONSE_ONLY、18个ROW_LIMIT、1个CALENDAR_COVERAGE，首次采用`tushare-range-v2`，依据复核日期2026-09-14。连同原四项v2，运行时候选为34 AVAILABLE/6 SINGLE_ONLY；40注册、31原生/3逐日、原参数/日期/字段/业务键保持。原四项规则、版本、依据与核对日期均有独立精确断言。

BJ只在top_list规划时参照SSE完整日历，实际证券请求保持原BJ代码和开市日；直接trade_cal BSE在来源调用前拒绝，margin三种exchange_id保持。RESPONSE_ONLY为完整父窗单请求单叶子、无阈值且不可拆，结构/范围/股票错误及严格限量/完整日历拒绝继续生效。页面持续说明完整性未确认，区分返回记录已采集与本次请求未返回记录；历史保存策略和SINGLE语义保持。

正式索引保持4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY。30项均保留RANGE_TASK_NOT_RUN、RANGE_SQL_NOT_VERIFIED及适用的重叠更新、拆分、披露更新、历史保留与完整性边界；没有把候选准入当作真实TASK验收。

## 精确来源与历史保全

[候选模块](../../control-plane/e2e/issue030-range-candidates.js)和[离线写入入口](../../control-plane/e2e/issue030-range-candidates-write.js)从母issue指定身份重建272项，使用现有validateEvidence、validateCasePlan、selectTaskCases核对，并保存sourceBindings。分组为28/12/38/35/35/33/87/4，每个case保留原params/dateAxis、全部规则依据和恰一个完整SOURCE runId/caseId引用；命名保持各交付表约定。

原12项mainbz与ISSUE-029新4项按完整身份区分；新run为`issue026-mainbz-split-source-20260914T013736Z`，后缀为000001-annual、600000-annual、000001-wide、600000-wide。两个同参数annual/whole不能互换。三项已证明完整休市的top_list零叶子样本保留，其他旧空、失败、四满额mainbz和BSE负例只留历史，未重标或删除。

父任务另从母issue准确ID及既有交付绑定独立重建272项，逐项检查清洁SOURCE/RANGE/PASS、API、日期轴、参数、当前引用与未来TASK ID；正式四AVAILABLE和六SINGLE_ONLY完整对象不变。所有历史runs深度相等，仍26轮826case928请求，canonical runs SHA-256（Python `json.dumps(runs, sort_keys=True, separators=(',', ':'), ensure_ascii=False)` UTF-8）为`7da2f75bbf00648df9157261704e9c99913fb1d9f3acd9775a6f948d3f5ef213`。初始索引SHA-256为`2abab2e9ccef3fe0179989cccc9c2bc351326b4c39aea32ad7498a0e4023a510`，候选索引为`a281a8460c4fdf57cf5a5d7ebdf13324f6579b15d61867d937f28d2e566c6490`。

私有目录`/private/tmp/issue030-control/`为0700，输入/绑定/摘要均0600：

| 文件 | SHA-256 |
| --- | --- |
| `candidate-inputs.json` | `9c2cb2a9daab04c13bf9aeed5ac69a0d403453611c4a3b40b85d8685837c7b3d` |
| `source-bindings.json` | `a4ce0c747643c91745726b87b4ea5ae682152ac2e542a6783bcd0402acbbba02` |
| `candidate-summary.json` | `fb00c64cc5de75ae7951ba640be771dfa97d079d271112a115e23c3dc1a3c4e0` |

`candidate-inputs.json`是272项离线输入，不是执行run；后继再按既定顺序加入两次disclosure重下和四项回归，形成10轮278TASK。该写入入口用于本次候选重建；后续已有TASK时须消费冻结输入并追加结果，不重新应用候选重置覆盖新TASK引用。机器核对在`delivery-audit.json`，独立来源清单在`independent-delivered-sources.json`。

## 源码与两包身份

沿用`feat/download-by-date-range`，保留全部前序暂存/未暂存成果，不commit/push。实施前884个Git管理文件保存于私有baseline；代码与输入通过组件审查后，将当时全部893个Git管理文件（含未提交内容）逐文件复制至`/private/tmp/issue030-work-20260914T025010Z`。完整snapshot SHA-256为`c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`，文件清单在`snapshot-files.json`。构建前清除该自有副本旧target编译结果，保留已安装前端工具，避免旧类影响。

| 身份 | SHA-256 |
| --- | --- |
| 完整相关源码diff | `5a6aeecda5269cdd2259602f2ca0a550faaa8218413644d3a892fb20b19372d6` |
| 生产JAR | `de8130e205f20493a01c566d74390a14483bb1296bcf955df5c2e4f389ff3ac1` |
| 验收JAR | `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a` |
| manifest | `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984` |
| request examples | `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f` |

sourceDiff按隔离副本HEAD、data-plane/control-plane/scripts/docs/contracts完整二进制diff及这些目录未跟踪文件的准确内容计算；不是只看HEAD或遗漏前序暂存。两包分别为隔离副本`data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`和`target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，属于本次新候选身份。893文件snapshot记录构建时文档状态；后续验收/看板/交接文档更新不进入两包。最终另核对422个构建/测试相关文件与工作树一致，源码、包、manifest/examples及私有输入摘要保持；机器证据在`frozen-identity.json`及`final-source-audit.json`。

## 已执行验证

Java21.0.11、Maven3.9.15、Node24.15.0、Docker29.5.2。普通检查使用白名单环境，不注入真实Tushare账户或业务DB；集成使用受控来源与自有MySQL8.4.6，浏览器与Maven串行。日志和逐类XML摘要保存在上述私有目录。

| 检查 | 实际结果 / 证据 |
| --- | --- |
| 策略矩阵RED | 121项，67断言失败、31预期旧候选不可用错误；`task1-red.log` |
| 独立HTTP RED | 隔离旧策略+新AvailabilityTest：3项中1通过、1候选状态失败、1入队错误；`task1-http-red-local.log` |
| 策略/下载/日历/HTTP GREEN | 158/158通过；原四精确策略强化后121/121；`task1-green.log`、`task1-policy-final.log` |
| 应用注册MySQL集成 | 6/6通过；`app-registry-integration-final.log` |
| Probe跟随BJ候选修正 | 67/67通过；测试确认生产保留BJ参数，部分日历负例仍拒绝；随后完整构建再次覆盖 |
| Node证据工具 | 初始98/98；新正式索引定向RED观察11项旧UNKNOWN；完成后104/104，0失败/取消/跳过；`candidate-node-final.log` |
| 完整隔离acceptance | `mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0/BUILD SUCCESS；66类1133项，0失败/错误/跳过；`build.log`、`acceptance-summary.json` |
| 前端单测及打包 | 完整构建中34文件524/524通过、Vite构建通过；前序同源码定向单测亦524/524 |
| 受控UI及下载任务 | `ui-redesign.spec.js` + `download-tasks.spec.js` 66/66通过，exit0；`ui-browser.log`；自有Vite已停止 |
| 新验收包metadata | `tushare-metadata.spec.js` 40/40通过，exit0；40 API/数据集、0上游调用/任务提交；`metadata-browser.log`、`metadata-summary.json` |

metadata实际证据为`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-cUZRsh/metadata-evidence.json`：40 API/数据集通过、39个必填阻止、1无参数、0任务/同步下载提交、0records/上游调用、11截图；JVM、sentinel停止及日志扫描均true，包前后SHA相等。wrapper随后移除自有容器与临时秘密；本项标签容器查询为空。

完整acceptance包含原runner/HTTP/契约/适配及两个包合同回归。日志中的Mockito动态附加/JVM与既有工具警告、负例拒绝日志没有作为失败隐藏；未改与本项无关的依赖配置。

两次受影响浏览器尝试均保留：第一轮45通过/14失败/7未运行，其中提示定位器匹配两个段落、旧SINGLE默认假设经test-only修正；尾段因父任务误并行Maven触发npm ci造成依赖中断，后续严格串行。第二轮跨夜中断，首项13.6小时超时，恢复后65项通过，exit1；`ui-browser-red.log`、`ui-browser-interrupted.log`及对应错误产物保留。两次均不算最终GREEN。最初独立HTTP尝试的Mockito沙箱初始化错误也未作为功能RED；经本地测试许可后的实际RED另记。没有用重试/删测试掩盖产品失败。

## 审查与验收对应

- [候选配置审查](../../.superpowers/sdd/2026-09-14-issue-030/task-1-review.md)：规格PASS、质量APPROVED。
- [证据映射审查](../../.superpowers/sdd/2026-09-14-issue-030/task-2-review.md)：规格PASS、质量APPROVED，零发现。
- [UI修正限定复审](../../.superpowers/sdd/2026-09-14-issue-030/ui-fix-review.md)：两类问题已解决，未删断言/场景，未发现新问题。
- [整体独立审查](../../.superpowers/sdd/2026-09-14-issue-030/final-review.md)：代码/规格APPROVED，零Critical/Important/Minor；独立核对893文件、422源码、两包、1133项XML和272绑定。最终浏览器结果及收尾文档亦经同一审查补核通过，完成判定YES、无文档缺口。

专属设计Acceptance 1由30+4独立矩阵、明确采用/来源及结构保持验证；Acceptance 2由BJ/BSE、限量/响应/日历及HTTP/页面/保存摘要测试验证；Acceptance 3由精确272绑定、新snapshot/两包和独立审查验证；Acceptance 4由历史深度相等、正式4/30/6以及没有新真实run/case验证。全部实现与运行验收成立，2026-09-15记录ISSUE-030 IN_PROGRESS → COMPLETED；后继设计及交接在该转换后准备。

## 交付边界

本项没有执行真实SOURCE、RANGE TASK或业务SQL，没有改变V1–V8迁移、成功数据、原业务键或生产字段；受控MySQL只用于测试。278项真实任务及每轮SQL/日志验收仍归ISSUE-031，最终六门禁与母任务收尾归ISSUE-032。本次候选可用不证明十一项上游完整性，也不关闭ISSUE-026、T13/T14或母issue。未运行发布脚本，不声称发布通过。

ISSUE-030完成后，已依次完成并链接[ISSUE-031详细设计](../task-designs/ISSUE-031-design.md)、写入并链接[后继交接](../task-handoffs/ISSUE-031-handoff.md)，记录ISSUE-031 NOT_STARTED → READY；没有执行后继任务。最终文件/锚点与Git差异空白检查通过，新增仓库文件已加入Git，未提交。
