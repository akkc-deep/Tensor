# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Task ID:** `ISSUE-018-T13`，Order13。
- **Transition:** `IN_PROGRESS -> BLOCKED`。
- **Design document:** `docs/task-designs/ISSUE-018-T13-design.md`，本次收尾已全文读取；本文件替换同路径历史next-task入口，权威状态以看板为准。
- **Handoff refresh:** 用户确认恢复方向后要求将工作交给新任务/新会话；本次仅刷新同一T13交接，观察状态仍为BLOCKED，不重复转换，不创建T14。
- **Explicit transfer (2026-09-13):** 此后用户明确要求“把t13当前剩余的任务，重新创建t14然后我在t14里面完成剩余工作”。本文件保留为 T13 历史暂停快照，T13 仍 BLOCKED；全部六项 Remaining Work 已移交 [T14 设计](../../task-designs/ISSUE-018-T14-design.md)与 [T14 交接](ISSUE-018-T14-handoff.md)。以下“继续T13 / 无T14”是迁移前约定，后续实施从 T14 开始，不将此次移交冒充真实阻塞解除。

## Current State

2026-09-13：证据校验器、测试侧SOURCE Probe、真实task API/批次/SQL账户harness及离线修复已完成逐项审查。导航入口和MySQL字符集缺陷已修复；未来income/fina_indicator同一行跨日期比较及repurchase证券数量安全投影已实现，不回填旧run。40页官方正文及189条引文已复核。整体审查另发现公开规则未进入索引、TASK_QUERY错误证据缺失，两项已修复；查询错误现在核对sent/header/body请求身份并关联已有task/route/case，不改写任务状态。

真实验收仍不完整：索引含40接口、3轮、329个case、0 AVAILABLE。公开规则与运行结果分别记录；19项已公布数值上界及独立日历覆盖合同不代表实际适用性与任务验收已完成，fina_mainbz默认type及150对100疑点仍保留。34项生产RANGE保持NEEDS_VERIFICATION，6项SINGLE_ONLY，未修改策略版本、生产代码或业务键，未执行RANGE任务。ISSUE-017只追加部分证据；T13与两项母issue均未完成。

SOURCE固定181项以2000ms间隔运行：244请求，88 PASS/89 EVIDENCE_MISSING/1 FAILED/3 NOT_RUN，退出1、cleanup PASS。trade_cal-bse-direct为BATCH_COMPLETENESS_UNCONFIRMED；后续top_list-closed-calendar、top_list-sh-calendar、margin-bse-direct未执行。固定74个SOURCE SINGLE均已尝试，36 PASS/38 EVIDENCE_MISSING；无任务/SQL事实。

SINGLE首次bootstrap零任务/零Tushare调用而导航失败，74样本全NOT_RUN，cleanup PASS。修复后独立新库运行15次Tushare任务/15次来源调用，观察均SUCCEEDED；harness标记14 PASS/1 EVIDENCE_MISSING/59 NOT_RUN，Playwright7接口通过/1失败/32未运行。fina_mainbz首股票写入150行后SQL观察失败；同时并行前端改动导致inputUnchanged=false，最终退出1、canonical cleanup FAILED。历史PASS标记不构成完整有效验收，不自动重试。

现有分支`feat/download-by-date-range`，实施基线`5dd840d71509398afde4f14d2b2b501a0c882e40`；收尾HEAD`34f3e283c0ee7f58c19bf72e8a470c1c858ac26d`。整体审查时HEAD为`35299da`；之后并行前端工作形成`a717172`、`34f3e28`两次提交，只涉及demo/vite等非T13文件。保留这些提交和当前成果，本任务未创建提交、合并或发布。

**新会话接续约定：** 用户已确认“隔离源码/新空库 → 优先四接口SOURCE → 真实任务闭环 → 单列疑难项”的恢复方向，并明确将实施留给新会话。本次没有创建隔离工作区、新schema或新的可执行case清单，也没有重新运行测试或真实来源。T13的20个自有交付文件已加入Git暂存区，尚未提交；不能只从HEAD开始而遗漏这些成果。新会话可先完成解除阻塞所需的准备，无须等11项UNKNOWN全部解决；实际准备证据成立后再更新状态。

## Changed Files

- `control-plane/e2e/tushare-range-evidence.js`：独立40项索引/树/运行身份校验与白名单投影。
- `control-plane/e2e/tushare-range-evidence.test.js`：证据拒绝、任务harness行为、导航及SQL编码离线回归。
- `control-plane/e2e/tushare-live.spec.js`：正式任务提交/查询、全批次、两股票SQL、失败与清理证据。
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbe.java`：显式真实入口、私有输入、来源预算与安全输出。
- 同目录`TushareRangeSourceProbeTest.java`：零网络输入/范围/日历/代表场景投影测试。
- `docs/verification/ISSUE-018-range-acceptance.md`及`.json`：官方引用、全部三轮安全结果、40项结论和未决事实。
- `docs/task-designs/ISSUE-018-T13-design.md`：明确monthly文档冲突、slb_len字段、完整SOURCE运行身份及未来安全投影合同。
- `docs/superpowers/plans/2026-09-12-issue-018-t13.md`：已执行步骤与未完成的真实开放验收分开记录。
- `docs/verification/ISSUE-017-stock-scoped-downloads.md`、`docs/issues/problems/ISSUE-017-stock-scoped-downloads.md`：追加部分新请求证据，保留未勾选条件。
- `docs/issues/problems/ISSUE-018-date-range-batch-downloads.md`、`docs/issues/README.md`、`docs/task-handoffs/README.md`、T13看板与本交接：当前阻塞、证据边界与恢复入口。
- `.superpowers/sdd/2026-09-12-issue-018-t13/official-review.md`及`task-4-candidate-assessment.md`：安全官方审查及40项候选依据。该工作目录保留实施ledger和限定审查报告供恢复使用。
- `docs/verification/ISSUE-018-T13-review.md`：整体独立审查及两项修复来源；`docs/verification/ISSUE-018-T13-rereview.md`：两项修复的限定复审。

## Verification

以下为已执行结果，不在写交接时重跑：

- `data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`：最终修复67/67，0失败/跳过、退出0，先RED为67项中5失败；此前导航RED为59项中2失败、SQL编码RED为61项中1失败，对应修复已分别独立复审PASS。最终修复使用`env -i PATH=/usr/bin:/bin:/usr/sbin:/sbin LANG=C LC_ALL=C`，移除所有真实账户/数据库变量。
- `mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test`：未来投影最终30/30、0失败/错误/跳过，XML核对一致、退出0；新增4项先RED。真实账户/DB/证据变量从子进程移除。
- `mvn -f data-plane/pom.xml -Dtest=TushareRangeSourceProbeTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test`：此前158/158，含当时Probe26；同生命周期前端468及构建通过，不与新Probe30重复累计。日志`/private/tmp/issue018-t13-five-class-verification.log`。
- `TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=single npm --prefix control-plane run test:e2e -- --list`：只发现40接口。合成RANGE3样本/2接口、空清单拒绝、有效retries0/workers1均验证，不属于真实执行。
- `TENSOR_TUSHARE_LIVE_E2E=1 mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbe -Dsurefire.failIfNoSpecifiedTests=false test`：真实SOURCE退出1，结果如上，已停止。
- `TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=single npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js`：两轮均退出1；第二轮wrapper因输入变化退出2，canonical保留exit1/cleanup FAILED。
- SQL只读诊断：LANG=C旧连接latin1下150行/150原始键/68转换键；显式`--default-character-set=utf8mb4`后150/150/150，未改数据或重复下载。
- 生产JAR SHA-256 `a408d3e69575d3386c4d6236eedabfc896c054d17e0db5270b65a970bd3a3a4d`；验收JAR `31ade90bf11c948c712de657446f12c0adb819b8b27e96373cbadde986bc2ba8`；manifest `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`；examples `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。各run保留各自源码差异哈希，不替换为最新工作树身份。
- 六条完整源码门禁及`sh scripts/verify-contracts.sh`本次未运行；不将T12历史结果冒充本次完整发布验证。

整体审查两项Important已修复，限定复审Spec PASS / Quality PASS、无新增发现；见`docs/verification/ISSUE-018-T13-review.md`及`docs/verification/ISSUE-018-T13-rereview.md`。最终harness语法、40行表/索引一致性、规则引用目标和自有差异检查通过；历史runs/cases及接口状态/版本/处置逐字段保持不变。

## Remaining Work

以下六项完整转入 T14，保留迁移时原文以便核对范围；不再作为启动 T13 旧实施流程的指令。

1. 将当前已审查成果完整带入独立工作目录，核对源码/构建身份，并准备独立新空schema；保留现有工作树、暂存区和旧数据库。
2. 优先为`daily_basic`、`stk_limit`、`moneyflow`、`margin_detail`登记新的固定SOURCE轮次。这四项来自已记录的候选评估，尚不具备开放资格；新清单须明确参数、case/run身份、依据、旧失败/未执行项关系和请求预算。取得新的有效运行证据，不剪裁成功子集伪装清洁run。
3. 完成34股票两只股票与6项原方式的真实任务/SQL闭环；保留15个既有任务，不自动重试。SINGLE新库回归须作为明确登记的新轮次，不能直接再次调用旧runner。
4. 补齐11项UNKNOWN、daily/forecast/dividend数值合同、BJ/BSE、标停接口历史范围、monthly月末冲突、fina_mainbz默认type及150对100的适用关系；stock_basic实际状态、特殊事件和同一行日期关系均须真实证据。疑难项单独登记调查范围，未取得依据的接口继续关闭，保留全部40项目标。
5. 仅在规则与有效SOURCE证据成立后更新候选策略/版本、重建并重新绑定哈希，执行RANGE任务及必要回归；未决项继续关闭。
6. 所有设计Acceptance与母issue关闭条件满足后再最终审查、完成T13。无预定义T14，不创建后继。

## Resume Task

当前执行入口为`ISSUE-018-T14`“剩余真实验收、完整性补证与逐项开放”，见[专属交接](ISSUE-018-T14-handoff.md)。T13 保留原验收要求与 BLOCKED 历史，T14 取得完整结果后再据实办理 T13 最终验收收尾。

前次“新任务”曾指继续 T13 的新会话；本次用户明确要求新增 T14 编号，已经替代此前约定。产品范围和未完成事实均不变。

## Start Here

以下保留迁移前的恢复路径与执行条件作为历史上下文；当前先读 T14 设计及交接，不启动旧 T13 流程。

按顺序读取：

1. 权威看板T13、本交接、`docs/task-designs/ISSUE-018-T13-design.md`全文。
2. `docs/verification/ISSUE-018-range-acceptance.md`和`.json`；`.superpowers/sdd/2026-09-12-issue-018-t13/task-4-candidate-assessment.md`及同目录`progress.md`（本地恢复缓存）。
3. 总体设计§6–7、T06设计与TushareBatchPolicies/TushareTradeCalendar；T12基础设施验证；ISSUE-017新增证据与未关闭条件。
4. 当前helper、Probe、harness，以及对应专项/限定审查报告；未来投影没有旧run数据，不据其存在补回历史事实。

**第一动作：** 先核对当前`git status`、HEAD与暂存差异，按上述Changed Files保存并迁移完整的T13成果到独立工作目录，逐文件核对内容一致。仅创建基于HEAD的worktree会漏掉当前尚未提交的实现，必须一并带入已审查的暂存改动；保留原工作树及并行前端提交，不自动提交或清理暂存区。

随后登记上述四接口的新固定SOURCE轮次，并准备/验证独立新空schema、受限配置、实际源码和两包哈希。当前只确定优先级，具体新case清单与身份仍须执行前固定，不能把本交接当作已存在的运行输入。取得下述解除证据后才记录`BLOCKED -> READY`，启动另行`READY -> IN_PROGRESS`；不要直接运行旧私有脚本或重复导入旧run。每轮失败停止，旧失败与未执行项继续留在全量记录中。

## Blocker

- **Reason:** 本轮SOURCE因BSE覆盖未确认停止且3项未运行；SINGLE真实任务轮次因SQL观察缺陷和并行源码变化未形成有效完整验收。工具缺陷已修复，但旧运行身份不能追认；完整性规则、默认类型及特殊样本的外部事实仍缺，当前0项可开放。
- **Resolution condition:** 已提供并登记可核验的补充来源依据/样本或明确的新固定取证轮次安排，明确处理既有失败及未运行项；稳定源码输入和独立新空schema可供后续执行，且执行边界明确、不自动重试已提交任务。看板记录这些实际解除证据后可转READY；工具测试通过或用户仅说“继续”不证明外部事实已经解决。

解除条件针对恢复执行所需的准备，不要求先满足全部最终Acceptance。用户已确认的恢复方向允许新会话落实这些准备；本次交接没有把尚未建立的环境或尚未运行的样本登记为已完成。

## Risks

- 原SOURCE和15次账户任务不得自动重跑、换日期直到成功、剪裁或回填结果。累计259次Tushare来源调用，fixture另2次；继续仍至少2000ms、30分钟/5000请求边界、串行及retries0。
- 自有MySQL容器`tensor-issue018-t13-0bc7f37f27ff`保留原数据库，不删除已成功任务/证券数据。应用、浏览器和SOURCE进程已退出。私有配置在`/private/tmp/issue018-t13-single-db-20260912/environment.json`及`/private/tmp/issue018-t13-single-db-20260912T173736Z/environment.json`，父0700/文件0600；恢复时只检查存在/权限，不打印凭证。
- 私有结果目录分别为`/private/tmp/issue018-t13-source-20260912T123840Z/`、`/private/tmp/issue018-t13-single-20260912T172838Z/`、`/private/tmp/issue018-t13-single-20260912T173736Z/`。临时文件可能失效，持久安全结论以Git中的索引/验证记录为准，不能编造遗失证据。
- 并行前端`control-plane/vite.config.js`、`public/tensor-demo.svg`、`ui-demos.html`、`src/demos/*`不是T13成果；期间已由并行工作提交，保持其当前Git状态，不缩小源码指纹范围来让失败身份通过。
- 未知完整性、数值上界、空样本和任务SUCCEEDED分别有不同含义；不据其中任意一项单独开放，不混type/VIP/多股票，不删除失败历史。
