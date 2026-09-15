# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Task ID:** `ISSUE-018-T14`，Order14。
- **Transition:** `IN_PROGRESS -> BLOCKED`。
- **Design document:** `docs/task-designs/ISSUE-018-T14-design.md`。

## Current State

2026-09-15后续最终验收已完成，本任务权威看板记录COMPLETED，见[032最终报告](../../verification/ISSUE-032-range-final-closure.md)。本文件继续保留为历史pause入口；以下Current State旧正文、Remaining Work、Resume Task及Blocker均是当时快照，不再是当前执行指令。四接口范围排除及全部原失败事实保留。

2026-09-13 用户要求“完成issue18的t14任务”后实施。本交接替换原READY入口快照；原转交与启动事实保留在看板，T13历史交接不改。下述为实际成果，T14全部Acceptance尚未成立。

- 已隔离并逐文件验证822个Git管理文件，重建两包；新的账户SINGLE/RANGE各用独立初始0表schema，8迁移/52表已核实。原工作树、T13暂存成果、旧三轮329case及15个成功任务保留。
- 新优先SOURCE33/33 PASS；历史SOURCE5 PASS/1 EVIDENCE_MISSING；代表SOURCE7 PASS/1 EVIDENCE_MISSING。每轮固定输入、无重试、exit0/cleanup PASS、输入稳定；空或不完整样本未升级。
- 完整SINGLE覆盖40API、34股票接口各两只股票和6非股票原方式：74任务/148查询全部PASS，SQL来源5265/插入5265/更新0，股票归属、业务键/摘要与第一股票历史保留通过。fixture另2提交/3查询。SINGLE不承诺完整历史。收尾只读SQL已核对原15、本次74及25个task_id全部保留且SUCCEEDED。
- 四接口RANGE25任务/50查询全部PASS，SQL来源68/插入52/更新16；两股票整段/两端及daily_basic跨年成立，fixture另2提交/3查询。当前 `daily_basic`、`stk_limit`、`moneyflow`、`margin_detail` 为AVAILABLE / `tushare-range-v2`，对应官方ROW_LIMIT分别6000/5800/6000/6000，干净SOURCE及匹配候选包TASK/SQL全部具备。
- 唯一索引40接口/8轮/475case、累计411次Tushare请求；30项NEEDS_VERIFICATION、6项SINGLE_ONLY。未改迁移、证券业务列/键、manifest或股票范围；UNKNOWN、BJ/BSE日历、阈值等号/单点满额及定义变更拒绝保持。
- 全部账户数据保留；六条源码门禁及独立最终审查通过。自有测试容器/秘密与preview已清理，4173/8080空闲，原账户容器保留。T14不标COMPLETED，T13仍BLOCKED，母issue未解决，不创建T15；没有提交、合并或发布。

## Changed Files

T14修改/新增如下；T13原有22项暂存成果未丢弃，原完整清单仍见 `docs/task-handoffs/ISSUE-018/ISSUE-018-T13-handoff.md`。以下文件连同已有成果需完整带入下一轮，不能只checkout当前HEAD。

| 文件 | 本轮变化 |
| --- | --- |
| `control-plane/e2e/tushare-range-evidence.js` | 可选固定canonical74 SINGLE清单，保留case身份；严格兼容utf8mb4 defaults |
| `control-plane/e2e/tushare-live.spec.js` | SINGLE使用固定run/case身份并在清理重核输入权限/摘要；模式组内可见标签切换并确认选中 |
| `control-plane/e2e/tushare-range-evidence.test.js` | 固定计划、字符集拒绝边界及最终4AVAILABLE/25RANGE证据回归 |
| `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java` | 四项明确v2策略及真实SOURCE引用，其余保持关闭 |
| `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPoliciesTest.java` | 独立四项规则与阈值等号边界 |
| `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchDownloadTest.java` | 四项生产策略下载行为与既有拒绝 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java` | 四项两股票准入、其余零写入/来源拒绝 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/DownloadTaskApplicationConfigurationIT.java` | 实际配置4/30/6集合 |
| `control-plane/e2e/ui-redesign.fixtures.js` | 独立四项阈值/版本/证据预期 |
| `control-plane/e2e/ui-redesign.spec.js` | 四项RANGE按钮可用，其余禁用 |
| `control-plane/e2e/tushare-metadata.spec.js` | 真实能力响应与独立v2预期对照 |
| `control-plane/e2e/stock-download-parameters.spec.js` | 明确选择SINGLE后验证34股票原参数；与另两普通套件共同修正默认RANGE测试前提 |
| `docs/verification/ISSUE-018-range-acceptance.json` | 唯一8轮475case及当前40决定，旧3轮不改写 |
| `docs/verification/ISSUE-018-range-acceptance.md` | 当前40项表/逐项缺口与原T13历史分开记录 |
| `docs/verification/ISSUE-018-T14-runs.md` | 固定计划、实际5新轮、包/输入/SQL/门禁身份 |
| `docs/verification/ISSUE-018-T14-official-evidence.md` | 官方HTML/静态文档/证监会原文补证与未解决项 |
| `docs/verification/ISSUE-018-T14-fixed-single-plan.md` | SINGLE固定计划与utf8mb4工具实施/审查 |
| `docs/verification/ISSUE-018-T14-priority-policy.md` | 四项策略及独立测试实施证据 |
| `docs/verification/ISSUE-018-T14-priority-review.md` | 四项候选限定审查 |
| `docs/verification/ISSUE-018-T14-policy-review.md` | 规则、拒绝与独立能力预期复审 |
| `docs/verification/ISSUE-018-T14-final-review.md` | 整体证据独立审查、G6发现及测试修正复审 |
| `docs/runbook/configuration.md` | 当前四项开放/上界/证据及其余接口限制 |
| `docs/runbook/first-run.md` | 按实际包能力选择四项RANGE，SINGLE历史边界 |
| `docs/verification/ISSUE-017-stock-scoped-downloads.md` | 新74任务/148查询及两股票SQL验收，默认type仍缺 |
| `docs/issues/problems/ISSUE-017-stock-scoped-downloads.md` | 股票验收已执行事实与剩余fina_mainbz问题 |
| `docs/issues/problems/ISSUE-018-date-range-batch-downloads.md` | 四项开放与其余30待验证，不关闭母issue |
| `docs/issues/README.md` | ISSUE017/018当前结果索引 |
| `docs/task-handoffs/README.md` | T14成果和后续入口 |
| `docs/task-designs/ISSUE-018-T14-design.md` | 最小固定SINGLE输入执行合同补充 |
| `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md` | 启动、实际执行、验证与阻塞状态证据 |
| `docs/task-handoffs/ISSUE-018/ISSUE-018-T14-handoff.md` | 本pause快照及精确恢复条件 |
| `docs/superpowers/plans/2026-09-13-issue-018-t14.md` | 当前执行检查点 |

## Verification

以下为本次已执行结果的交接，编写交接不额外重跑来源。全部命令使用Java21、项目Node24与隔离源码；普通命令移除账户变量。

| 命令 | 实际结果 |
| --- | --- |
| `node --test control-plane/e2e/tushare-range-evidence.test.js` | 最终77/77、0失败/跳过、exit0；曾观察固定计划缺陷/缺RANGE证据RED，再GREEN |
| `mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` | G1=1318、0失败/错误/跳过、exit0 |
| `npm --prefix control-plane test` | G2=34文件/468测试、exit0 |
| `mvn -f data-plane/pom.xml clean verify` | G3=1060、0失败/错误/跳过、exit0 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | G4=1063、0失败/错误/跳过、exit0 |
| `npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js` | G5=3/3、exit0 |
| `npm --prefix control-plane run test:e2e` | 最终G6=7文件/126全部PASS、0失败/跳过、exit0（15.9m）；此前全量exit1/exit130及聚焦失败另存，未追认 |
| `TENSOR_TUSHARE_LIVE_E2E=1 mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbe -Dsurefire.failIfNoSpecifiedTests=false test` | 三个固定SOURCE新轮33/33、5/6（另1缺证）、7/8（另1缺证），各exit0/cleanup PASS |
| `TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=single npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js` | 40API/74任务PASS、148查询，exit0/cleanup PASS |
| `TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js` | 4API/25任务PASS、50查询，exit0/cleanup PASS |
| `git diff --check` 与 `git diff --cached --check` | exit0；旧3轮逐对象不变、本地链接存在、10份运行源码与主工作区一致、已知秘密值扫描0匹配 |
| `sh scripts/verify-contracts.sh` | 未运行：main/干净输入/HEAD前置未满足，不自动提交或切分支制造条件 |

独立最终及定位修正复审无剩余Critical/Important/Minor；聚焦4/4之后完整G6另取得126/126。当前harness模式切换后来改为模式组内可见标签；旧真实SINGLE/RANGE的specSHA保留为当时字节，没有重跑账户或追认当前harness已执行真实验收。

初始SINGLE包与最终RANGE包不同；各run保存实际身份，不能混用。最终生产SHA `b6f1a90dc9fbe54916895fe1c896a7b69e1982dd3a2c8e26124e7e6451811cfa`，验收SHA `cb6c7a750c0e75768ee54ad89cf5ed368690edd00f7210ac10dff33a1cdc1a5f`。固定计划、独立schema和全部安全SQL结果见运行登记，专项/独立审查结果不代替全任务验收。

## Remaining Work

1. 补齐11个UNKNOWN：`adj_factor, suspend_d, income, balancesheet, cashflow, fina_audit, express, repurchase, stk_managers, top10_holders, top10_floatholders` 的可核验完整提取规则，以及 `daily / forecast / dividend` 数值上界的适用合同；样本少或宽窄相同不能补成规则。
2. 明确fina_mainbz省略type的默认分类及SINGLE150/官方100适用关系，禁止混P/D/I、改业务键或使用VIP绕过；ISSUE017该问题仍未解决。
3. 完成其余30项逐接口SOURCE/代表场景和RANGE TASK/SQL：BJ映射/BSE日历、周/月线日期与第二股票边界、标停API完整历史窗口、非空事件与非交易日公告、最新公告修订、daily重叠更新及其他索引列出的缺口。新增income/fina_indicator同一行对照、repurchase585股票、monthly20180928和slb20240620观察可复用为对应清洁来源证据，不能扩写成未执行的范围。旧trade_cal BSE失败及新top_list SH空样本保留。
4. 取得逐项依据后先登记独立固定轮次及旧case关系，验证完整源码/包身份、受限输入和新空schema；按设计逐项更新策略/版本、重建、做真实TASK/SQL与必要门禁。失败保留并按设计撤回本轮开放标记，不自动重试或删除数据。
5. 全部T14 Acceptance、总体§6、T13原Acceptance与母issue关闭条件成立后再作最终完整审查和关闭证据；按合法转换处理T14和T13，不重复74项成功任务或创建T15。

## Resume Task

继续 `ISSUE-018-T14`：完成剩余真实验收、完整性补证和逐项开放，保留40接口及全部历史证据。T13只在T14取得完整结果后做原验收收尾，不能先把T13标完成。

## Start Here

按顺序读取：

1. 权威看板T14行、本交接及 `docs/task-designs/ISSUE-018-T14-design.md` 全文。
2. `docs/verification/ISSUE-018-range-acceptance.md` / `.json` 的当前40决定和缺口，`docs/verification/ISSUE-018-T14-official-evidence.md` 与 `ISSUE-018-T14-runs.md`。
3. `docs/task-designs/ISSUE-018-T13-design.md` 全文和T13历史handoff、T14限定审查；T06/T12设计及受控基础设施证据。
4. 总体设计§6–7、ISSUE017当前验证、ISSUE018关闭条件与运行手册。

**第一动作：** 对照当前逐接口缺口，核验新提供或新找到的上游完整提取/默认type/日历或历史样本依据，并在T14运行登记中记录它解决的具体缺口及固定新case计划。若无新增依据，不调用旧runner或仅换日期重试；公开补证已经覆盖HTML、静态Markdown、公共规则/FAQ与监管原文，不能把其仍未给出的合同推定为存在。

有实际解除证据后先记录 `BLOCKED -> READY`，重新开始另行 `READY -> IN_PROGRESS`。接续前核对当前Git/源码差异，完整携带未提交成果，重新验证临时环境和包摘要；本交接中的旧路径/哈希不是当前环境已就绪的保证。

## Blocker

- **Reason:** T14 Acceptance第4/6项要求全部未决接口、代表场景及母issue关闭条件成立。现有公开材料和固定实测仍缺11UNKNOWN/三项含糊限量/fina_mainbz默认类型与上界适用性，以及其余接口的日历、历史、特殊样本或RANGE任务闭环。四项通过不足以满足全任务；不能无依据开放或自行排除剩余范围。
- **Resolution condition:** 取得可追查的新上游规则/默认分类/日历/历史或事件依据，明确可解决的逐项缺口，并登记相应独立固定SOURCE或TASK轮次及原失败关系；执行所需稳定源码、实际两包摘要、0700/0600私有配置和新空schema核验成立。该准备证据可解除对应执行阻塞，但不等于全部验收完成；未解决项保持关闭，完成仍以全部Acceptance为准。

## Risks

- 当前HEAD `34f3e283c0ee7f58c19bf72e8a470c1c858ac26d`、分支 `feat/download-by-date-range`，存在原T13和本T14未提交成果；不得reset暂存或只按HEAD重建而遗漏它们。新增项目文件应在Git中，不自动提交、合并、发布。
- 原自有MySQL容器 `tensor-issue018-t13-0bc7f37f27ff` 和旧/新账户库保留，不能删除15个旧任务或新成功任务。私有配置只核验存在/权限，不输出Token/密码/URL；临时路径可能失效，失效时按合同重建，不猜结果。
- 隔离源码 `/private/tmp/issue018-t14-work-20260913T092556Z`；控制/原3轮基线 `/private/tmp/issue018-t14-control-20260913T092556Z`；SINGLE安全产物 `/private/tmp/issue018-t14-single-20260913T092822Z`；RANGE `/private/tmp/issue018-t14-range-20260913T094505Z`。SOURCE与固定计划的完整路径见运行登记；私有文件不提交Git。
- 每轮串行、workers1/retries0、来源间隔至少2000ms、30分钟/5000请求，关闭trace/screenshot/video。清理失败或输入变化使该轮不能作为干净验收；旧SOURCE中的PASS和修复后的工具不能追认旧失败。
- 四项沪深样本不证明北交所覆盖；SINGLE成功、空数据、监管业务暂停日都不证明全部历史或Tushare API截止日。
