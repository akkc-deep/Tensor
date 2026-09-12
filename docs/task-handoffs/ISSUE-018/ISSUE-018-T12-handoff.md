# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Task ID:** `ISSUE-018-T12`，Order 12。
- **Transition:** `IN_PROGRESS -> PAUSED`。
- **Design document:** `docs/task-designs/ISSUE-018-T12-design.md`，暂停前已全文复核。
- **Pause request:** 2026-09-12 用户要求“把这些工作交接给下一个任务”。本交接供下一工作会话继续未完成的T12，不把未通过的验收转移成T13完成输入。

## Current State

T12实现与验证尚未全部完成。G1–G5已取得最终生产源码的通过证据；G6普通七文件126项尚未全通过，不能记录COMPLETED。T13仍NOT_STARTED，Design document/Handoff均None；没有设计、启动、真实账户调用或生产RANGE开放。

已实现并验证：真实Servlet/MySQL/http_test生命周期、无页面窗口继续调用和SQL提交、同库重建/手动恢复、只重试失败中批、已提交202回执被吞后找回唯一任务、双resume版本竞争、RANGE迟到响应丢弃、最小REFERENCES权限、CORS Location、严格子进程报告、V8/双包/脚本数字与运行手册。故障矩阵及精确方法证据在 `docs/verification/ISSUE-018-task-infrastructure.md`。

普通浏览器已迁移任务API及四独立空库环境，但仍有以下未完成事实：

- 第四轮G6为106 passed、3 failed、17 did not run（13.0分钟）。真实metadata40项、Fixture3项、stock3项在该轮通过；失败为query响应扫描、outcomes日线表头旧断言、ui-redesign trade_cal交易所点击未选中。
- 最新query定向运行退出1，首项600000ms超时、10项未运行。252个初始造数任务实际SUCCEEDED；测试到达monitor.assertClean后最终报页面边界不可读。尚未确认是哪一响应扫描等待未结束，不能称已证明导航/CDP原因。后续更正场景未运行，afterAll仍准确拒绝375/252及disclosure日期分布不符。
- 两个spec的日线14列中文标签+英文字段名精确UI断言已修，但最新query未运行到该场景，outcomes尚未复跑；API英文columns及行值合同保留。
- ENUM点击竞态已由trace定位：option.click内部自动滚动令根页452→167，Popper由bottom-start翻top-start，pointer坐标失效，selected仍false。两个helper已把option滚动、完整入视口条件与click分开，补弹层关闭/选中文本断言；修正后尚未运行浏览器。

工作区为 `feat/download-by-date-range`，保留原T10/T11暂存成果；不提交、不合并、不发布。暂停后不留测试进程：最后query runner正常清理容器和临时凭证；自有preview PID74785/74764核验身份后停止；4173/8080空闲，T12标签容器0、临时环境映射0。

## Changed Files

以下是T12直接相关改动；工作区另外保留T10/T11的已暂存实现、设计与交接，不得清掉或重复实现。完整实现责任及现有测试路径仍以T12设计Files节为准。

| 文件 | 改动/当前状态 |
| --- | --- |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java` | CORS暴露Location，生产源码最终验证通过。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java` | Location RED/GREEN、精确origin及合法202回归。 |
| `data-plane/tensor-app/pom.xml` | 删除json-schema-validator直接依赖的test scope，修复运行包漏库。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java` | 精确运行依赖jar/class及测试类型排除合同。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java` | 验收包库存及测试类型排除。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskLifecycleIT.java` | 新真实生命周期/同库恢复/回执丢失/最小权限/报告拒绝五方法。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskControllerIT.java` | 同version双resume一202一409、未知HTTP形状拒绝。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskRequestBindingTest.java` | 拒绝未知symbol/from/to，支持形状对照。 |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRecoveryIT.java` | RANGE迟到envelope在adaptation前丢弃。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java` | V8及真实information_schema精确列/索引合同，仍47项。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/DividendBusinessKeyMigrationIT.java` | 既有迁移增量断言补V8，业务/回滚合同保留。 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java` | 新库迁移数8，原业务断言保留。 |
| `control-plane/e2e/download-task-lifecycle.spec.js` | 新flow2/resume1真实HTTP浏览器闭环，无route mock。 |
| `control-plane/playwright.config.js` | ordinary/task-live/tushare-live三模式、串行及严格专用reporter。 |
| `control-plane/e2e/packaged-test-environment.js`、`control-plane/e2e/packaged-test-environment.test.js` | 私有四文件五字段环境helper，Node19项通过。 |
| `control-plane/e2e/ui-redesign.fixtures.js` | 任务API及独立34策略/6SINGLE能力夹具。 |
| `control-plane/e2e/ui-redesign.spec.js` | 40项task链路、原布局等回归；最新ENUM滚动同步待验证。 |
| `control-plane/e2e/stock-download-parameters.spec.js` | 34/6参数和submissionId恢复；最新ENUM同步待验证。 |
| `control-plane/e2e/download-tasks.spec.js`、`control-plane/e2e/download-tasks.fixtures.js` | 保留T11三项交互；T12接入普通preview边界与夹具复用。 |
| `control-plane/e2e/download-outcomes.spec.js` | 真实任务结果、固定错误、SQL回滚/查询/禁用；最新日线14表头待验证。 |
| `control-plane/e2e/dataset-query.spec.js` | 375次任务造数/证券查询/日志网络合同迁移；响应读取诊断与日线14表头，扫描超时待定位。 |
| `control-plane/e2e/fixture-flow.spec.js` | task接收/详情/查询、精确选项与导航，真实三项曾通过。 |
| `control-plane/e2e/tushare-metadata.spec.js` | 实际40项能力、表单作用域、零来源和独立库，第四轮40项通过。 |
| `scripts/verify-contracts.sh` | schema47、生产51、测试52/1051/52/48等，发布前置保留。 |
| `docs/runbook/configuration.md`、`docs/runbook/first-run.md` | 实际配置、REFERENCES、单worker/手动恢复/计数含义。 |
| `docs/verification/ISSUE-018-task-infrastructure.md` | 精确故障矩阵、G1–G5、G6失败/修复/暂停证据。 |
| `docs/superpowers/plans/2026-09-12-issue-018-t12.md` | 已完成checkbox与未完成G6/收尾、暂停检查点。 |
| `docs/task-designs/ISSUE-018-T12-design.md` | 已完成设计，继续依此执行，不重新设计T12。 |
| `docs/task-handoffs/ISSUE-018/ISSUE-018-T12-handoff.md`、`docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md` | 本暂停交接及权威PAUSED状态。 |
| `docs/issues/README.md`、`docs/issues/problems/ISSUE-018-date-range-batch-downloads.md`、`docs/task-handoffs/README.md` | 索引同步暂停，母issue保持处理中。 |
| `.superpowers/sdd/2026-09-12-issue-018-t12/progress.md` | 历史实施流水及最新暂停事实，早期pending不代表当前实现状态。 |
| 同目录 `faults-report.md`、`lifecycle-review.md`、`packaged-report.md`、`packaged-review.md`、`packaging-review.md`、`root-review.md` | 已有独立复核；faults报告补ENUM根因与待运行边界。 |

## Verification

本节仅汇总已运行结果；用户要求交接后没有启动新测试。

| 已执行命令（仓库根，使用项目Node24 PATH） | 实际结果/证据 |
| --- | --- |
| `mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` | G1退出0：82类/1282项，失败/错误/跳过0；Lifecycle5，flow2/resume1各attempt1/retry0。`/tmp/issue018-t12-final-gate1.log`、`/tmp/issue018-t12-final-gate1-reports/`。 |
| `npm --prefix control-plane test` | G2退出0：34文件/468项。`/tmp/issue018-t12-final-gate2.log`。 |
| `mvn -f data-plane/pom.xml clean verify` | G3退出0：1021单测+4包合同。`/tmp/issue018-t12-final-gate3.log`及同名前缀`-reports/`。 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | G4退出0：1021单测+4生产包+3验收包合同。`/tmp/issue018-t12-final-gate4.log`及`-reports/`。 |
| `npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js` | G5最终3/3通过，5.2秒。`/tmp/issue018-t12-final-gate5-rerun.log`。一次旧截图超时保留，不能计为通过。 |
| `npm --prefix control-plane run test:e2e` | G6第四轮退出1：106通过/3失败/17未运行，13.0分钟。`/tmp/issue018-t12-gate6-contract-rerun.log`；前三轮及修正详情见验证文档。 |
| `python3 /tmp/issue018-t12-packaged-runner.py dataset-query /tmp/issue018-t12-query-diagnostic.log` | 内部执行 `npm --prefix control-plane run test:e2e -- e2e/dataset-query.spec.js`；退出1，1超时失败/10未运行，初始252任务成功。安全证据：`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t03-1exYQc/evidence.json`。 |
| `node --test control-plane/e2e/packaged-test-environment.test.js` | 19通过。`/tmp/issue018-t12-helper.log`。 |
| `npm --prefix control-plane run test:e2e -- --list`，及设计列明task-live flow/resume、tushare-live的`--list` | ordinary126/7、flow2、resume1、tushare-live40；仅发现，不能称执行通过。`/tmp/issue018-t12-{ordinary-list,flow-list,resume-list,tushare-live-list}.log`。 |
| `sh -n scripts/verify-contracts.sh` | 退出0；另从原heredoc提取的合成preflight退出0，manifest40、syntheticRejections11，`/tmp/issue018-t12-script-preflight.log`。 |
| `sh scripts/verify-contracts.sh` | 未运行：当前非main且受保护输入未提交，不绕过发布前置。 |

G1已观察的回执故障是“committed 202 discarded; client received HTTP 500 without receipt”，不称TCP断连实测。同库上下文重建+已提交故障快照，不称SIGKILL实测。真实详情桌面1440/窄屏390截图已目视核对，无页面横向溢出：`/tmp/issue018-t12-lifecycle-desktop.png`、`/tmp/issue018-t12-lifecycle-mobile.png`。

最终JAR路径及SHA-256：

- production：`data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`，`a408d3e69575d3386c4d6236eedabfc896c054d17e0db5270b65a970bd3a3a4d`。
- acceptance：`data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，`31ade90bf11c948c712de657446f12c0adb819b8b27e96373cbadde986bc2ba8`。
- 这些包对应最后生产源码；后续仅e2e/文档改动，最新e2e修正尚未通过。若恢复时改产品代码，须重建并重新绑定哈希/验证受影响门禁。

## Remaining Work

1. 定位query响应扫描等待/页面边界超时。先检查`monitorPage`、`responseScans`、`assertClean`与`performDownload`每模式首项full goto之间的时序；新增诊断只输出允许的API路径、去掉UUID和秘密。当前未证实具体未完成响应，不得直接忽略读取错误或requestfailed，也不泛化networkidle（后续竞态用例故意挂起响应）。保留375次真实任务及全部证券/日志/安全合同。
2. 定向验证已写的日线14表头（query/outcomes）及两个ENUM同步修正。ENUM已保留真实鼠标点击、无sleep或额外重试；若仍失败，回到trace证据定位，不能凭重复通过声称根因关闭。失败trace：`/tmp/issue018-t12-trade-cal-selection-failure.zip`，call@3741。
3. 通过受影响定向用例后，以新四库执行完整普通七文件126项，最终无失败/未解释未运行；核对四packaged evidence、SQL/日志、安全扫描与清理。G1–G5既有结果不替代G6。
4. 更新验证文档、计划、ledger和最终审查，检查差异/稳定哈希/新文件Git纳管；只清理本次创建的资源。
5. 全文复核T12验收，确实达成后先记录COMPLETED。然后按看板Order使用`designing-task-contracts`完成并链接T13专属设计，再写T13 next-task交接、置READY；不启动T13、不关闭母issue。当前不提前写T13设计或交接。

## Resume Task

继续 `ISSUE-018-T12`：用真实Servlet/MySQL与受控来源证明后台任务基础设施闭环，补齐普通浏览器与交付门禁证据。消费本交接后，按用户恢复请求记录 `PAUSED -> IN_PROGRESS`，保留本交接路径作为历史上下文。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T12-design.md`全文，随后权威看板T12行和本交接。
2. `docs/verification/ISSUE-018-task-infrastructure.md`及 `docs/superpowers/plans/2026-09-12-issue-018-t12.md`，区分已通过与未验证修正。
3. `/tmp/issue018-t12-query-diagnostic.log`，`control-plane/e2e/dataset-query.spec.js`的monitorPage/assertClean/performDownload，及本交接列出的trade_cal失败trace。
4. `control-plane/e2e/ui-redesign.spec.js`、`stock-download-parameters.spec.js`的ENUM helper和 `download-outcomes.spec.js`表头；`.superpowers/sdd/2026-09-12-issue-018-t12/faults-report.md`最新只读结论。
5. `control-plane/playwright.config.js`、`packaged-test-environment.js`和现存 `/tmp/issue018-t12-packaged-runner.py`；需要进一步理解故障合同时再读总体设计§3.14/§5.1–5.2及T08/T09/T11直接输入。

**第一项实施动作：** 在恢复状态后，针对query的responseScans增加未完成扫描的脱敏路径/阶段诊断，核对导航前后扫描结清情况，再用自有新空库定向执行dataset-query；不要直接再跑整套或增加超时。

环境恢复说明：

- 暂停时Java21.0.11、Node24.15.0/npm11.12.1、Docker29.5.2/Colima、MySQL8.4.6、Chromium1234可用，恢复时重新核实。Maven/npm/Playwright必须串行；Maven会npm ci重写node_modules。
- Docker地址：`DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock`，`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。本机Docker/JVM/browser/端口/Git staging需正常本地权限；此前没有自动审核拒绝。
- 临时runner支持`all`或逗号分隔的spec名；绑定当前验收JAR哈希，创建MySQL8.4.6和四个各自精确前缀的新空schema，私有0700目录/0600映射与defaults，finally清理。PATH前置项目Node及现有JAVA_HOME/bin，避免净化环境落到macOS java stub。
- runner仅对自有MySQL添加`--log-bin-trust-function-creators=ON`。此前log_bin1/trust0的schema账号CREATE TRIGGER实际1419；不授应用SUPER、不改生产配置/运行手册权限。四schema账号权限为CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX/REFERENCES/TRIGGER；生产最小权限测试不含TRIGGER。
- 三mock需重新自建preview：`npm --prefix control-plane run preview -- --host 127.0.0.1 --port 4173 --strictPort`；四验收包串行用8080。禁止停止未知端口进程。临时runner或日志若已丢失，按T12设计重建环境并重新取得证据，不能假设临时文件长期存在。

## Blocker

None

## Risks

- 用户暂停交接不代表T12已完成，也不授权跳过G6或启动T13。任务失败应记录真实结果，不能用排除/list/重复小样本替代完整门禁。
- 保留T10/T11暂存成果与本项全部修改。没有提交、合并、发布；不要切main或提交来制造发布脚本前置。
- 生产40数据集、34股票必填/6非股票、34 RANGE NEEDS_VERIFICATION不变；没有真实Tushare调用。T13的11项UNKNOWN、BJ/BSE、标停两项历史范围和fina_mainbz默认type仍待真实依据。
- manifest SHA-256保持 `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，request examples保持 `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`，V1–V8原字节不变。fina_mainbz历史manifest的ann_date样本与T06当前snapshot/ts_code元数据分别断言，不篡改历史样本。
- 凭证不得进入repo、日志输出或浏览器；仅共享脱敏证据。原始测试私有日志保留在临时目录，不将其当可公开产物。
