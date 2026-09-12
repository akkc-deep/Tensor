# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T12`，已记录COMPLETED。
- **Next task:** `ISSUE-018-T13`，按预定义Order选中的下一项，Order13。
- **Design document:** `docs/task-designs/ISSUE-018-T13-design.md`，已完成、全文复核并先链接看板；独立就绪审查PASS。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。本次不启动T13实施。

## Next Task

`ISSUE-018-T13`：真实接口完整性验收与逐项开放。为拟开放RANGE取得真实参数/日期轴/边界与完整提取依据，形成40项最终处理清单和母issue关闭证据。

范围为31原生+3逐日的34个RANGE目标、6项SINGLE处理、真实账户harness迁移task API、逐项策略版本/验证标记及相关回归。接受标准是每个AVAILABLE项同时具备可核验完整性、真实SOURCE请求和真实任务页面/全部批次/SQL/日志证据；八类代表场景、11UNKNOWN、BJ/BSE、两项标停历史范围及fina_mainbz默认type均按设计如实处理。所有纳入项满足总体设计§6及母issue关闭条件后才完成T13；未决项不得通过隐性排除、空样本或受控测试冒充通过。

设计已固定40行矩阵、结果索引字段、首轮样本、离线拒绝测试、SOURCE取证与本地候选开放/真实TASK复核的顺序、失败撤回及版本递增、私有环境和验证命令。当前34生产RANGE全部NEEDS_VERIFICATION，T13没有实现或真实调用结果。

## Dependencies

### ISSUE-018-T06

- **Artifact:** `docs/task-designs/ISSUE-018-T06-design.md`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`、同目录 `TushareTradeCalendar.java`；`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/fina_mainbz.yaml`；`docs/contracts/download-request-examples.json`；看板T06完成证据。
- **Decision:** 34项策略精确31原生+3逐日，RANGE29股票/5非股票；另6项只有SINGLE，整体40项34股票/6非股票。生产策略全部sourceVerified=false、公开完整性UNKNOWN；内部19原生数值+3逐日数值+1日历仅是候选规则，11项UNKNOWN无完整依据。fina_mainbz SINGLE为snapshot+ts_code，RANGE为报告期end_date，不传type。
- **Rationale:** 文档支持参数不等于真实截断合同；UNKNOWN或最小满额不能表示完整成功。日历先完整覆盖再筛开市日；当前业务键不含主营类型，混P/D/I可能冲突。
- **Constraint:** 不凭测试开放、不省略股票、不换VIP、不混type、不恢复9项退役接口。BJ日历映射/BSE直接输入未证实仍本地拒绝，不能偷偷替换交易所；sourceParameters的多日首日探针只用于预检，不是完整计划。保持每次来源通过同一TushareProClient/context，不自动重发。
- **Usage:** 从实际Policy和设计40矩阵建立独立结果索引及校验；先取得官方/可核验规则和真实SOURCE证据，再逐项更新规则/验证标记/版本，重建包后做TASK闭环。若新依据需要现有规则之外的算法，先补该项设计再实现，保持未决项关闭。
- **Readiness evidence:** T06看板已COMPLETED；专项449、单测867、生产867+4、验收867+4+3、受控浏览器3项通过；生产门禁和纯转换/日历/阈值覆盖可复用。没有真实Tushare验收，这正是T13待补输入。当前请求示例SHA-256为 `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`，manifest为 `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`；历史fina_mainbz ann_date样本不得改写成新请求证据。

### ISSUE-018-T12

- **Artifact:** `docs/task-designs/ISSUE-018-T12-design.md`；`docs/verification/ISSUE-018-task-infrastructure.md`；`control-plane/playwright.config.js`、`control-plane/e2e/packaged-test-environment.js`、`download-task-lifecycle.spec.js`、`download-outcomes.spec.js`、`dataset-query.spec.js`、`tushare-metadata.spec.js`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskLifecycleIT.java`；`docs/runbook/configuration.md`、`docs/runbook/first-run.md`。
- **Decision:** 真实Servlet/MySQL/非Tushare来源证明任务脱离浏览器继续、同库新runId启动不自动来源、手动retry/resume及事务/拆分/故障事实。普通7文件和task-live/tushare-live明确分离，Maven/npm/浏览器串行。账户套件旧同步helper留给T13迁移，不是已通过的真实任务验收。
- **Rationale:** 受控上游适合稳定触发满额与故障，真实Tushare负责语义/完整性/权限；两类证据分别记录。202仅表示持久接收，任务/批次与SQL才证明完成；写入操作数不等于去重净增数。
- **Constraint:** 普通套件继续4新空schema/自有8080应用和mock preview4173，不降低安全扫描、请求白名单、初始空库或精确SQL/日志合同。T13账户使用自己 `tensor_m14_t05_<hex>` 新库，不能复用已清理的T12库。T12的query-only假上游0ms禁止带入真实账户；真实来源保持设计规定的至少2000ms间隔。V1–V8原字节、生产7迁移/51表、验收8/52、40注册保持。完整发布脚本仍要求main/干净输入/HEAD，不自动提交、合并或发布。
- **Usage:** 复用task API接收/查询、固定错误、精确批次及私有证据模式迁移tushare-live；按验证文档引用受控拆分/故障实际方法，无须用真实账户制造故障。候选策略变更后重建双包并重绑哈希，再取得真实TASK证据和相关六条源码门禁；清理仅自己的进程/容器/秘密文件。
- **Readiness evidence:** T12已COMPLETED。G1=1282、G2=468、G3=1025、G4=1028、G5=3；最终G6 `/tmp/issue018-t12-resume-gate6.log` 退出0，7文件126/126，15.8分钟，无失败/跳过/未运行/重试。最终LifecycleIT5/flow2/resume1通过；query375提交/来源及各375事件、outcomes14提交/17查询/8来源、metadata40且零来源均有安全证据和清理。独立审查规格/质量PASS、运行门禁CLOSED。生产JAR哈希 `a408d3e69575d3386c4d6236eedabfc896c054d17e0db5270b65a970bd3a3a4d`，验收JAR `31ade90bf11c948c712de657446f12c0adb819b8b27e96373cbadde986bc2ba8`，重建后须重新核对。自有preview、容器、秘密映射已清理，4173/8080空闲；临时缓存不保证长期存在，持久结论以验证文档和看板为准。

两项直接输入决定和约束一致；T06的未验证门禁与T12的基础设施通过没有冲突。没有将旧官网调研、SINGLE样例或T12受控证据当作T13真实完整性结果。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T13-design.md`全文，以及权威看板T13行、本交接。
2. `docs/task-designs/ISSUE-018-design.md` §3.4–3.5、§5.3、§6–7；`docs/issues/problems/ISSUE-018-date-range-batch-downloads.md` 的40项官方链接/关闭条件。
3. T06设计全文、上述两策略文件及 `docs/contracts/download-request-examples.json`；区分候选L与已验证规则，保留fina_mainbz当前请求。
4. `docs/verification/ISSUE-018-task-infrastructure.md`、T12设计及 `control-plane/e2e/tushare-live.spec.js` 当前旧同步流程；T12普通harness是迁移参考，不能直接当账户结果。
5. `docs/issues/problems/ISSUE-017-stock-scoped-downloads.md`、`docs/verification/ISSUE-017-stock-scoped-downloads.md`，随后configuration/first-run与 `docs/issues/README.md`。

**第一项实施动作：** 用户明确启动T13并按看板记录 `READY -> IN_PROGRESS` 后，新建 `control-plane/e2e/tushare-range-evidence.test.js`，独立列出40项及34/6、31/3/6不变量，先让“漏项”和“UNKNOWN被标AVAILABLE”的合成结果实际RED，再实现最小校验helper及结果索引。使用项目Node24的PATH运行 `node --test control-plane/e2e/tushare-range-evidence.test.js`。该动作无网络、无Token、不修改生产Policy，不需要再次创建设计。

后续真实取证必须遵循设计固定的私有case清单、授权账户、间隔、源探针与新空库前置；本交接和READY不代表已经启动这些步骤。

## Risks

- 11项UNKNOWN完整性、BJ/BSE、标停历史范围和fina_mainbz默认type仍无真实结论；应取得依据或如实保留EVIDENCE_MISSING，不伪造开放条件。
- 账户现时权限、额度和事件样本可能不满足首轮请求；失败不自动重试、扩范围或换VIP。新样本须登记可核验理由，未完成项保持可追查。
- 当前分支 `feat/download-by-date-range`、HEAD `885d559b87de84009a15e5a334991f709fc3d204`，保留T10/T11/T12现有暂存成果及本次设计/交接；不reset、清暂存或切main来满足发布前置。
- T13尚未实施；本次没有创建来源Probe/校验helper/真实结果文件，没有真实Tushare调用或RANGE开放。母issue继续处理中。
