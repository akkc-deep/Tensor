# ISSUE-018-T12：跨模块故障验证、浏览器闭环与交付门禁

## Goal

使用 MySQL 8.4.6、生产应用装配、受控来源及真实 Chromium，证明任务执行不依赖浏览器连接、同库重建后只允许手动继续，并为当前源码、生产包、验收包和普通浏览器回归提供可追溯证据。

身份来自 [ISSUE-018 看板 T12](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t12)，Order 12，直接依赖 T08、T09、T11。T11 已记录 COMPLETED：专项 297、全前端 468、构建及受控浏览器 3/3 通过。本设计在该完成记录之后创建；下列新增测试及命令是待实施合同，不是已通过的结果。

## Scope

新增真实应用生命周期 IT 及由它启动的浏览器 spec；逐项核对总体设计 §3.14、§5.1 故障窗口，复用已有事务/恢复测试，补足跨模块缺口。迁移普通浏览器页面下载断言到 task API，明确受控、验收包和外部账户套件入口。更新 V8 精确发布断言及 configuration/first-run，建立 `docs/verification/ISSUE-018-task-infrastructure.md`。

仅修复这些验证暴露的本项集成缺陷；已知最小修正包括开发 CORS 暴露 Location、普通套件逐文件空库配置和串行调度。保持单实例/单 worker、手动 retry/resume、旧同步 API、40 项生产注册、34 股票必填/6 非股票与现有策略门禁。不得为测试将生产 RANGE 改为 AVAILABLE。真实 Tushare 账户调用、11 项完整性依据、34 项逐项开放与母 issue 关闭属于 T13。本项不自动提交、合并、发布或修改外部业务库。

## Approach

### 直接输入与已有覆盖

- T08 的 `DownloadTaskCoordinator`、`DownloadTaskService`、`DownloadTaskRecoveryIT` 固定启动恢复先于接收/派发、旧许可失效、同一数据库历史保留、运行中的来源实际退出后才能释放所有权。其四条 Maven 门禁已通过；本项要用当前源码重新取得跨模块证据。
- T09 的 `DownloadTaskController`、`DownloadTaskConfiguration`、schema/examples 和生产配置固定七路由、202 已提交回执、数值版本、同 runId/settings 装配与固定 StoredError。`DownloadTaskControllerIT` 已有失败中批 retry、双控制、重启 resume、提交回执丢失；`DownloadTaskApplicationConfigurationIT` 已提供完整 Servlet 应用的启动方式。T09 专项 602、单元 1019 及双包合同通过。
- T11 的真实 Axios/DTO、`useDownloadTask`、详情路由和三个 `download-tasks.spec.js` 场景固定无损 bigint、2 秒/5-10-30 秒调度、控制结果只查不自动重发、动态批数及返回缓存。其 BrowserContext route mock 只证明交互，不证明后台执行；本项以真实 HTTP 补齐。

三个直接输入无冲突。不将已有 Flow/手动 tick 测试当作新生产生命周期测试；也不复制所有 core 故障用例到浏览器。

### 真实应用与测试来源

新增 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskLifecycleIT.java`。使用 `@Testcontainers` 和 `mysql:8.4.6`，保留同一容器/数据库跨应用上下文。测试前清理仅自有库；每个测试 finally 释放 latch、关闭自己的上下文/进程，JUnit 清理容器。禁止 H2、用 mock repository 代替真实提交、连接个人业务库或按端口批量杀进程。

应用通过 `new SpringApplicationBuilder(TensorApplication.class).web(SERVLET)` 启动，`server.address=127.0.0.1`、随机端口、生产 profile、容器数据库，复用 T09 的显式 TestTypeExcludeFilter 防止扫描其他测试 Controller。Flyway 位置显式指向当前编译生产资源 `target/classes/db/migration`，库存恰为 V1–V5/V7/V8；不能因测试 classpath 的 V6 意外得到“生产 8 次迁移”。前端使用本次 Maven generate-resources 构建后复制的静态资源，不依赖旧 JAR 或 Vite。读取 WebServerApplicationContext 实际端口，并在浏览器前检查 health、下载页与静态脚本可访问。

只在本 IT 显式测试配置中注册独立 `http_test/prices` 插件、DatasetAdapter 与控制 Controller；沿用 T09 Source 的 `start_date/end_date` DATE_RANGE_MEMBER 形状、CALENDAR_DAYS、AVAILABLE/VERIFIED_RULE、`http-test-v1` 和固定 `value` 业务键。先用生产 Flyway 初始化自有库，再创建 T09 相同 `http_test__prices(value, source_plugin, source_api, ingested_at)` 测试表，随后启动应用，借已有额外 DatasetAdapter bean 通道进入 catalog；不加生产 YAML、V9 或改全局发现机制。适配器直接采用 T09 Flow 的字段/事务接线。Tushare 可以停用；40 项生产定义仍注册，额外来源只存在于此测试上下文。测试配置不得被普通生产/验收包扫描或打包。

来源每次先执行 `context.beforeRequest()`，按日期记录调用次数；每个成功叶子产生一条 value=日期的证券数据。disconnect 固定 2026-09-01～03，partial 固定 2026-09-10～12，resume 固定 2026-09-20～22，三个场景业务键不重叠；SQL snapshot 与每场景行数断言均按该范围过滤，保留已完成场景的历史。可控场景仅为三天断开继续、第二天首次 SOURCE_TIMEOUT、以及恢复测试的第三天阻塞。latch 为每日期独立的可释放门；用事件/有界 await/expect.poll 同步，不用长 sleep 推断已执行。异常携带测试 canary，StoredError 必须仍为后端固定文案。

测试 Controller 路径固定 `/__test/download-task-lifecycle`，仅注册以下控制：

| 方法 | 请求/响应与约束 |
| --- | --- |
| POST `/scenario` | `{scenario:"disconnect"\|"partial"}`；只有上一场景终态、无在途来源才能配置下一场景，否则 409；清本场景内存计数，不清持久历史 |
| POST `/release` | `{date:"YYYY-MM-DD"}`，只释放已声明日期的门；非法日期/未知键 400，不执行任意 SQL |
| GET `/snapshot` | 仅返回场景、各日期 calls、已提交业务键列表/行数、是否存在在途来源；行数与业务键每次 JDBC 读取，不能从调用数推算提交数 |

控制从 Node APIRequestContext 调用，不修改页面应用状态；真实业务请求全部走正式 `/api/v1`。不得使用 `page.route`、BrowserContext API mock 或返回伪造任务 JSON。测试 Controller 不提供应用重启或外部数据库操作能力。

### 生命周期与浏览器编排

IT 至少包含下列三个方法，断言各自实际执行；第三项不改变 FlywaySchemaContractIT 的 47 项基线。

1. `browserDisconnectAndRetryUseTheRealApplication`：启动应用后，以 ProcessBuilder 从仓库根执行专用浏览器命令。spec 注册以下两项，`retries=0`、一 worker：
   - `liveDisconnectReloadAndReopen`：页面提交三天 RANGE，先让第一批停在来源门内，确认 202/taskId 已持久化且页面仅接收；刷新同一详情 URL。记录 calls=1/committed=0，关闭整个 BrowserContext，确认所有 Page 已关；用独立 Node request 释放第一批，观察第二次来源调用且数据库已提交第一批（两种计数都增长）。再释放其余门到终态，新 BrowserContext 从下载页服务端列表找到同 taskId，直接详情和刷新均 SUCCEEDED、3 个业务键、3 成功叶子、仅一次提交 POST。计数观察发生在无打开页面的窗口中。
   - `livePartialFailureRetriesOnlyTheMiddleBatch`：三天中第二天第一次抛 SOURCE_TIMEOUT，等待 PARTIAL_FAILED；页面展示该区间、固定错误和 attemptCount=1。数据库只有第一/第三天，调用各一次。点击 retry，原版本 POST 只一次，等 SUCCEEDED；数据库三键，第一/第三天 calls/attempt/已提交计数不变，第二天 calls/attempt=2，任务累计请求=4、sourceRows/insertedRows=3、updatedRows=0。最终页面与 SQL 事实一致。
2. `recreatedApplicationRecoversWithoutAutomaticSourceCalls`：同库创建/关闭两个完整应用上下文，runId 必须不同，旧上下文关闭后才能启新上下文。使用真实 repository/事务构造故障点已提交快照：旧 QUEUED；旧 RUNNING/planReady=false；三叶子含 SUCCEEDED、普通 FAILED、RUNNING/PENDING 的计划（覆盖组合可拆为参数化场景）；全部叶子成功但任务仍 RUNNING。借现有 RecoveryIT 的准备方法语义，不直接绕过约束写任意状态，不宣称这是操作系统 SIGKILL 实测。新启动后队列/未完工作为 INTERRUPTED，普通失败保留；全成功任务重算 SUCCEEDED、计数不重复。启动完成及多次 GET 期间来源 calls=0。对“第一天成功、第二天普通失败、第三天中断”的任务再启动浏览器 `liveResumeAfterRestart`：环境传同 taskId，页面只 resume，操作后只第三天被请求，保留第二天 FAILED，最终 PARTIAL_FAILED；成功第一天不再请求。用数据库断言 source/insert/update、attempt、version 和业务键；不以 UI 字样代替查询。未规划任务用正式 resume 恢复计划并完成；旧 QUEUED 同样须手动 resume。
3. `documentedSchemaPrivilegesSupportV8AndTaskWrites`：在自有 MySQL 创建专用应用账号，仅赋予单 schema 的 CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX/REFERENCES，以该账号执行生产七次迁移、初始化应用、提交并完成一个测试任务。验证 V8 外键成立且任务/批次可写；账号无 DROP/DELETE/全局权限。REFERENCES 是 V8 外键所需的运行手册补充，不向运行账号授予测试 harness 所需 TRIGGER。测试清理由容器管理员执行。

ProcessBuilder 的 argv 固定为 `npm --prefix control-plane run test:e2e -- e2e/download-task-lifecycle.spec.js`，工作目录通过父目录链找到同时包含 `data-plane/pom.xml` 与 `control-plane/package.json` 的仓库根并校验，不能依赖 Surefire 模块 cwd。显式设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:<实际端口>`、`TENSOR_TASK_LIVE_E2E=1`、`TENSOR_TASK_LIFECYCLE_SCENARIO=flow|resume`；resume 还需合法 `TENSOR_TASK_LIFECYCLE_TASK_ID`。流量只能到该回环应用。子进程环境保留 PATH/临时目录/浏览器缓存所需设置，剔除数据库秘密、真实 Token、JAVA_TOOL_OPTIONS 等应用注入；不把容器凭证传给 Chromium。

使用 repo Node >=24.15.0 <25，优先将 `data-plane/tensor-app/target/frontend/node` 加入 PATH。输出重定向到本 IT 的 `target` 目录以避免 pipe 堵塞，进程有 180 秒期限；超时终止自己创建的子进程并失败。通过唯一绝对路径 `PLAYWRIGHT_JSON_OUTPUT_FILE` 与 list+json reporter 记录机器结果；读取报告核对精确 spec basename、flow 的两项名称或 resume 的一项名称、实际 attempts 均1、全 passed、无 skipped/unexpected，不能仅搜索“passed”或接受退出0/零测试。缺环境、缺 Chromium、报告缺失、URL/场景不合法均硬失败，不 test.skip。失败清理不能覆盖原始失败原因。

### 故障窗口的精确证据映射

本项先逐条核对现有测试的真实断言，只有下面明确缺口才补测试。结果写入验证文档“场景→类/方法→命令→报告→数据事实”表，保留真实 MySQL 与受控注入标签。

| 故障/范围 | 测试与控制 | 必须观察的持久事实 |
| --- | --- | --- |
| 接收后内存唤醒丢失 | LifecycleIT 的真实默认 poller；在接收处不主动 dispatch。已有 coordinator 单测的漏通知场景同时列证据 | QUEUED 被正常轮询领取；重建场景则先 INTERRUPTED，不能自动重发 |
| 202 在网络中丢失 | LifecycleIT `lostSubmissionResponseFindsOneCommittedTask` 在真实 Servlet 上补仅测试 Filter，提交链完成后丢弃回执/断开响应；ControllerIT 保留 committed requeue receipt 故障 | 同 submissionId GET/重放找回唯一 taskId，一次接收；无第二行/第二代任务。Filter 只限定一个测试请求，不改变生产 Controller |
| 规划完成但计划事务前退出 | LifecycleIT 未规划持久快照；RepositoryIT `planAndSplitAreAtomicOnSecondInsertFailure` | planReady=false 无半计划；手动恢复重规划 |
| 收到来源响应但证券事务前退出 | RecoveryIT `closeWaitsForBlockedOrdinarySingleAndDropsItsLateEnvelopeBeforeAdaptation`；补同许可 RANGE 的边界断言（如已有 runner 断言不足） | 迟到 envelope 不入库；resume 只重新请求未成功叶子 |
| 数据写入后成功标记失败 | BatchCommitServiceIT `successUpdateFailureRollsBackBothNewAndUpdatedSecurities` | INSERT 与 UPDATE 均回滚，之前成功兄弟保留；没有数据成功标记分离 |
| 成功提交后终态未更新/回执丢失 | RunnerIT `committedPlanSplitDataAndFinishAreNeverRetriedAfterAcknowledgementErrors`；LifecycleIT 全成功快照 | SQL/计数已提交事实保留，只重算终态，不重发来源 |
| 父拆分中断 | RepositoryIT `planAndSplitAreAtomicOnSecondInsertFailure`、RunnerIT `nativeSplitPersistsOnlyChildrenAndNodeFailureCreatesNoHalfTree` | 父 SPLIT + 两子全提交或全回滚；父响应零证券写入，叶子计数排除父 |
| 旧轮次迟到、手动重排回执丢失 | CommitServiceIT `locksCurrentTaskAfterStalePrecheckSnapshotAndCannotOverwriteNewWorker`；RecoveryIT `manualRequeueRetiresAnOlderLostPermitEvenWhenTheCommitReceiptIsLost` | 旧许可写回回滚，新 version/generation 与成功批不被覆盖 |
| 两页同时 retry/resume | ControllerIT `retriesOnlyTheFailedMiddleBatchAndReturnsStoredTimeoutAsQueryData` 及 core versionRace；补 resume 的 HTTP 并发同版本场景 | 一 202 一 409，版本仅一次转换，另一请求拒绝不改任何行/计数 |
| 数据库不可用、无法记录失败 | RecoveryIT `uncertainCommitPausesUntilDatabaseReturnsThenRecoversCommittedSuccessWithoutReplay`、`unknownTaskQueryFailureOnlyProbesAndClaimReceiptFailureRecoversKnownCandidate`；ControllerIT 数据库查询500 | 故障期间不继续来源；恢复只探测/重算，成功批不重发；GET失败不变任务 FAILED |
| 三批部分失败、独立来源 | 新 livePartialFailure + T09 HTTP Source；core RunnerIT 三批及 `symbol/from/to` 夹具 | 非 Tushare 完成接收→事务→retry；34 项生产 RANGE 无变更 |
| 未知 HTTP 形状 | 扩展 DownloadTaskRequestBindingTest/DownloadTaskControllerIT，纯能力描述 `symbol/from/to` 与已支持 start_date/end_date 对照 | core 可使用独立字段；HTTP 对未注册 codec 描述/参数拒绝且零任务/来源调用，不顺带注册通用形状 |
| 完整性/交易日/空计划/预算 | 现有 BatchDownloadDescriptorTest、DateRangePlannerTest、TushareBatchPoliciesTest、TushareBatchDownloadTest、RunnerTest/IT、PersistenceServiceIT | 总体 §5.1 对应断言与本次 XML 报告齐全；UNKNOWN 不成功，满额父不写、最小满额失败、空计划合法但规划失败不误成功 |

表中全名路径以 Files 节为前缀，具体既有方法名已核对源码；CommitServiceIT 指 BatchCommitServiceIT。真实202网络丢失固定新增 LifecycleIT 方法 `lostSubmissionResponseFindsOneCommittedTask`，Filter嵌套于其完整Servlet测试配置，只对携带测试标记的一次POST吞掉/中断响应；不能以MockMvc伪造网络中断。resume双控制新增 ControllerIT 方法 `concurrentResumeAcceptsOnlyOneVersionTransition`，复用它现有真实核心/数据库与并发HTTP绑定测试接线。验证文档记录这两个精确归属。

### 普通浏览器套件的运行边界与迁移

当前 `playwright.config.js` 无排除规则。现有 `download-outcomes`、`fixture-flow`、`dataset-query`、`tushare-metadata` 各自启动真实验收 JAR，固定占用8080；不是全部 route mock。`tushare-live` 无默认 gating 且必需真实 Token、账户样本与额外证据目录，因此不能把普通自动化套件绑到 T13 外部前置。

配置保留一个 chromium project，按下表选择 testMatch/testIgnore；两个专用 flag 同时为1时报错，缺少专用参数不得静默降级。普通模式 workers 固定1，避免四个 harness 抢8080；显式选择文件仍走同一模式。保留 forbidOnly 和失败追踪；真实账户既有禁 trace/screenshot 的约束不放宽。

| 模式 | 纳入与启动条件 |
| --- | --- |
| 默认 ordinary | 纳入现有七个非真实账户 spec：ui-redesign、stock-download-parameters、download-tasks、download-outcomes、fixture-flow、dataset-query、tushare-metadata；排除 lifecycle 与 tushare-live。这些是明确的 suite 边界，不计作通过/跳过的用例 |
| `TENSOR_TASK_LIVE_E2E=1` | 只纳入 download-task-lifecycle.spec.js；要求显式 loopback PLAYWRIGHT_BASE_URL、scenario，IT 启动随机端口服务 |
| `TENSOR_TUSHARE_LIVE_E2E=1` | 只纳入既有 tushare-live.spec.js，保留其全部账户/JAR/哈希/隔离库前置检查；T12只验证测试发现，不发真实来源请求。其当前同步页面验收助手在 T13 按真实任务合同适配并实际验收，不能当作普通套件通过 |

普通模式的三个 route mock spec 用各自 `test.use({baseURL: process.env.TENSOR_UI_BASE_URL || 'http://127.0.0.1:4173'})`。手动启动本次 production preview4173，配置不再偷偷启动另一个服务；其余四个验收包 spec 保持8080自有应用。普通整套不设置全局 PLAYWRIGHT_BASE_URL 或置8080，不能用4173覆盖验收包的固定端口检查。T11专用 spec 单跑时使用 TENSOR_UI_BASE_URL 选择预览地址。

四个验收包文件分别使用同一自有 MySQL 8.4.6 容器内的四个新空 schema，禁止共用库或删掉空库前置。固定对应：download-outcomes=`tensor_m14_t02_<hex>`，dataset-query=`tensor_m14_t03_<hex>`，tushare-metadata=`tensor_m14_t04_<hex>`，fixture-flow=`tensor_issue018_t12_fixture_<hex>`。前三个保留各自原正则，无需扩展共同前缀。outcomes/query 的 initial tables=0 检查保持，四个 schema 在整套开始前一次性创建为空，不在文件间清表；失败后重跑须准备新的四库。整套结束仅删除自己创建的容器；各文件保留原自有应用/trigger清理，不能操作外部schema。

为同一个普通 Playwright 命令提供逐文件环境，新增小 helper `control-plane/e2e/packaged-test-environment.js`，导出 `configurePackagedEnvironment(key)`。四个验收文件在 beforeAll 中、任何环境验证/启动/证据采集之前调用，key精确为对应spec去掉`.spec.js`的名称。没有 `TENSOR_PACKAGED_E2E_ENV_FILE` 时保持现有单文件调用环境；有该变量时，读取它指向的绝对路径JSON，先验证普通文件/非符号链接/当前用户/0600，根仅上述四key、每entry仅五个字符串环境字段 `TENSOR_DB_URL`、`TENSOR_DB_USERNAME`、`TENSOR_DB_PASSWORD`、`M14_DB_SCHEMA`、`M14_MYSQL_DEFAULTS_FILE`，字段完整且非空，路径/指定schema前缀和JDBC路径一致；验证成功后只赋值本文件五项process.env，不修改其他环境。格式/缺key等错误固定文案，不输出JSON/凭证。既有harness继续负责defaults内容、JDBC、用户、权限等验证。helper用Node内置fs/path，不引入依赖或数据库框架；用临时文件测试四key选择、字段白名单/权限/缺项拒绝，失败不局部赋值。

运行前共同提供绝对 ACCEPTANCE_JAR（当前 `-Pacceptance clean verify` 的产物）与实际 SHA-256 至 ISSUE_017_ACCEPTANCE_JAR_SHA256。将四套五字段映射写入本次0600临时JSON，再仅传其路径至TENSOR_PACKAGED_E2E_ENV_FILE；不把任何秘密纳入repo。每套defaults文件也为0600，六行精确 `[client]`、host、port、user、password、protocol=TCP，账号与该JDBC相同；MySQL CLI连接同容器。测试库账号只拥有自己四个合成schema与trigger所需权限，不混同first-run生产最小权限；不需DROP DATABASE授权。fixture/metadata的其他固定清单哈希保持，下载模式变更不修改证券manifest。

迁移落点固定：

- `ui-redesign.fixtures.js` 增加 capabilities、submit/list/detail/batches、retry/resume，复用 T11 DTO 字段/原始 number token 序列化方法（可导出小 helper），不让未知路由回泛化200。40项生产门禁默认 SINGLE + NEEDS_VERIFICATION/UNSUPPORTED；专用 RANGE 场景仍只在受控 fixture AVAILABLE。
- `ui-redesign.spec.js`、`stock-download-parameters.spec.js` 将页面 POST断言改 `/api/v1/download-tasks`，body 含 submissionId/mode/规范params。成功改“任务已接收→详情GET终态”，提交响应不明的手动确认保持同 submissionId；不要把原“重试下载”测试误改为批次 retry。保留40项元数据、34/6股票、键盘、主题、分页和布局断言。
- `download-outcomes.spec.js`、`fixture-flow.spec.js` 的页面交互同样先202后查询详情/批次；受控 SOURCE_TIMEOUT、PERSISTENCE_FAILURE 是任务/批次里的固定错误（GET200），不再期待提交返回上游错误。SINGLE空成功是 sourceRows=0 的 SUCCEEDED。保留真实 SQL回滚、敏感 canary、关闭后数据查询、禁用插件、原生页面刷新与日志检查；202不能触发旧同步成功日志/指标，改检查任务事件，旧 `/api/v1/downloads` 的兼容行为继续由后端测试及必要直接HTTP断言保证。
- `download-outcomes.spec.js` 的 verifyMigratedSchema、`dataset-query.spec.js` 的 verifyMigratedDatabase/verifyFinalDatabase 及输出evidence同步更新：精确history=`1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1`、八次迁移、52表（不含Flyway history）。initial tables=0与原证券行数断言保留；新任务表不混入证券数量。全文件搜索残留的迁移7/表50等固定证据，不只修改一个expect。
- `dataset-query.spec.js` 保留证券查询覆盖，调整页面网络白名单、下载页等待、上述V8断言与逐文件环境入口。`tushare-metadata.spec.js` 保留40项元数据、原JDBC前缀与零上游断言，增加逐文件环境入口并监控新增 capabilities/近期列表GET；无点击提交时两种下载POST均应为0。
- 页面 monitors 允许精确 UUID 详情/叶子页及合法 query，不用广泛 `/api/**` 放行。请求头/Location、pageerror、未知API、原始上游泄漏失败条件保留；总测试数变化须解释新增/改名映射，禁止删除难跑场景凑通过。

### CORS、迁移、包装与运行说明

当前 `SpaWebConfiguration` 仅暴露 X-Request-Id，而 T10/T11严格读取 Location。将开发精确origin映射改为暴露 `X-Request-Id, Location`；允许请求头/方法、credentials=false及生产不开放CORS保持。`ProductionWebConfigurationTest` 先补失败断言，并验证任务202在合法origin返回可见Location；非法origin、UI/assets/Actuator不开放回归保留。记录为T12发现的跨模块缺口，不倒写T11受控浏览器为跨源证据。

V1–V8原字节不变；生产七迁移/51表（49证券来源历史表+2任务表），验收/测试八迁移/52表。`FlywaySchemaContractIT` 当前47项，测试1051列、52主索引、48非主索引；40注册数据集912物理列/34非主索引，9遗留来源表不注册。证券业务列与任务字段分开记，不把任务列混为金融业务列。生产物理列1044（1051减fixture7）、非主索引48，应由真实信息schema核对后写手册；若实际不同先解释差异，不能更改断言迎合错误数字。

`scripts/verify-contracts.sh` 的 REPORTS schema43改47，精确新增四方法：taskTablesHaveExactColumnsDefaultsIndexesAndConstraints、upgradesV7WithoutChangingChecksumsOrExistingSecurities、productionMigrationInventoryCreates51TablesWithoutFixture、mysqlRejectsTaskAndBatchConstraintViolations 各1。metadata41/package4保持。输出的productionTables49改51、fixtureTotals50/1008/50改52/1051/52，明确49证券来源表+2任务表、legacy9、注册40、生产7/测试8迁移与48非主索引；最终固定成功消息同步47/51。合成拒绝探针仍验证缺失、重复、数量错误、隐藏目录等，不能降低严格度或删掉探针。已有生产/验收包合同核对V8原字节及排除测试配置，必要时补 LifecycleIT/控制入口不进入包的精确断言。

发布脚本的 main、保护输入干净、HEAD archive、输入哈希前后不变及自有容器清理一律保留。当前分支和暂存内容不满足发布前置：本项静态/合成验证脚本改动，完整 `sh scripts/verify-contracts.sh` 仅在用户既有发布流程满足条件后运行。不能自动提交/切main来制造通过。若未运行，在证据表明确“未运行：发布前置未满足”，这不代替六条当前源码门禁，也不把脚本称已通过。

configuration/first-run 增补：任务接收只表示持久化、URL/近期列表找回、SINGLE不代表完整历史、RANGE成功只表示计划范围、动态拆分与写入操作次数；单实例、一个worker、停止/重启不自动重发、历史查询不依赖当前插件、retry与resume范围、查询暂时失败不改任务状态、定义变化拒绝手动继续。任务enabled=false仍启动恢复并保留查询，禁止新接收/控制/派发；不以health UP等同插件已配置或RANGE已开放。

配置表用真实属性名和默认值：`tensor.download-tasks.enabled=true`、max-queued-tasks=100、max-range-days=36600、max-batch-nodes=10000、max-requests-per-run=5000、max-run-duration=30m、max-source-rows-per-task=1000000；除enabled外正值，duration可转纳秒且正值，非法启动失败。`tensor.plugins.tushare-pro.min-request-interval=1500ms`（非负），与原connect5s/read120s/max-response-bytes67108864并存；节流跨同客户端新旧入口共享。旧同步超时说明保留并与后台每轮截止/已许可事务可结束分开；70秒是每停机阶段上限，不能称浏览器关闭取消任务或保证任意插件立即退出。V8增加REFERENCES授权及两表用途/迁移数量，不授DROP/DELETE；备份/前向迁移/旧V7回退规则保留。

## Files

| 路径 | 责任 |
| --- | --- |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskLifecycleIT.java` | 新真实应用/同库重建/最小权限/浏览器子进程测试，嵌套测试来源、适配器和控制入口 |
| `control-plane/e2e/download-task-lifecycle.spec.js` | 新flow两项、resume一项真实HTTP页面闭环，缺配置硬失败 |
| `control-plane/playwright.config.js` | 三套件显式发现规则、普通串行、专用reporter；无自动部署服务 |
| `control-plane/e2e/ui-redesign.fixtures.js`、`ui-redesign.spec.js`、`stock-download-parameters.spec.js`、`download-tasks.fixtures.js`、`download-tasks.spec.js` | task mock/提交恢复/模式与页面回归、preview baseURL；T11已有三项继续执行 |
| `control-plane/e2e/download-outcomes.spec.js`、`fixture-flow.spec.js`、`dataset-query.spec.js`、`tushare-metadata.spec.js` | 保留真实验收包harness，迁移任务断言/网络监控/V8数字，接入逐文件独立空库环境 |
| `control-plane/e2e/packaged-test-environment.js`、`packaged-test-environment.test.js` | 新最小环境helper及Node内置test文件；不属于Playwright spec，校验四个隔离库配置、秘密文件边界和原调用兼容 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskControllerIT.java`、`DownloadTaskRequestBindingTest.java` | 补resume双控制与未知HTTP形状，保留既有事务回执丢失覆盖；真实网络回执丢失新增在LifecycleIT |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRecoveryIT.java`、`DownloadTaskRunnerIT.java`、`BatchCommitServiceIT.java`、`DownloadTaskRepositoryIT.java` | 既有故障证据归档，仅实际缺口补测试；PersistenceServiceIT/插件与算法测试同样纳入全量 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java`、`src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java` | 最小Location暴露修正与CORS回归（第二路径相对tensor-app） |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`、`build/PackagedJarContractTest.java`、`build/AcceptancePackagedJarContractTest.java` | 精确迁移/包证据，后两路径相对同一com/akkc/tensor测试目录 |
| `scripts/verify-contracts.sh` | 固定schema47、生产51、测试52/1051/52/48等断言；发布前置不动 |
| `docs/runbook/configuration.md`、`docs/runbook/first-run.md` | 实际任务配置、权限、手动恢复和计数含义 |
| `docs/verification/ISSUE-018-task-infrastructure.md` | 新持久证据矩阵、命令/实际数量/结果/边界/日志位置；不得记录凭证 |

不新建通用测试服务器框架、供应商或生产配置开关；能在现有IT嵌套完成的辅助类型不拆成多个文件。新增产物加入Git，保留已有暂存成果。

## Tests

第一项实施动作：在 `ProductionWebConfigurationTest` 的既有精确 origin 场景增加 `Access-Control-Expose-Headers` 必须包含 Location 的失败断言，实际观察 RED 后对 SpaWebConfiguration 做一行修正并运行该类；随后建立 LifecycleIT 的真实Servlet/MySQL/source最小fixture，先让尚不存在的专用spec调用失败，再完成三项真实浏览器断言。不要先扩大生产接口或改RANGE门禁。

环境：Java21、Docker、MySQL8.4.6镜像、Maven、Node24.15.x/npm11.12.x、安装后的Chromium和MySQL CLI。当前可用 Docker 地址曾为 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`，执行时核实当前连通性；不将旧环境可用写成这次证据。npm前加 `env PATH="$PWD/data-plane/tensor-app/target/frontend/node:$PATH"`。

专项开发验证（仓库根）：

```sh
mvn -f data-plane/pom.xml -Dtest=ProductionWebConfigurationTest,DownloadTaskLifecycleIT,DownloadTaskControllerIT,DownloadTaskRequestBindingTest,DownloadTaskRecoveryIT,DownloadTaskRunnerIT,BatchCommitServiceIT,DownloadTaskRepositoryIT,FlywaySchemaContractIT -Dsurefire.failIfNoSpecifiedTests=false test
node --test control-plane/e2e/packaged-test-environment.test.js
npm --prefix control-plane run test:e2e -- --list
TENSOR_TASK_LIVE_E2E=1 TENSOR_TASK_LIFECYCLE_SCENARIO=flow PLAYWRIGHT_BASE_URL=http://127.0.0.1:4173 npm --prefix control-plane run test:e2e -- e2e/download-task-lifecycle.spec.js --list
TENSOR_TUSHARE_LIVE_E2E=1 npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js --list
sh -n scripts/verify-contracts.sh
git diff --check
git diff --cached --check
```

`--list`仅测试发现：默认七spec、不含专用两spec；flow精确两项、resume额外检查精确一项；外部live保留既有注册数量。不能用这些list输出作为执行成功。脚本嵌入Python的合成preflight使用其原函数在自有临时目录运行全部拒绝探针；从脚本提取原文并执行，不修改发布入口绕过main。记录sh语法/合成检查和完整发布命令是不同证据。

总体设计 §5.2 六条门禁按以下顺序逐条保留退出码和日志；第一条实际触发 LifecycleIT 子进程（flow2+resume1），第四条结束后准备普通浏览器环境：

```sh
mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js
npm --prefix control-plane run test:e2e
```

后两条执行前，启动本次自有MySQL容器（随机回环映射端口），按上文四个精确前缀创建四个新空schema，准备0600临时JSON及defaults文件；将第四条产物的绝对路径/哈希与环境映射路径绑定进环境。另终端 `npm --prefix control-plane run preview -- --host 127.0.0.1 --port 4173 --strictPort`，三个mock spec访问4173，四个验收包harness各自使用独立schema、占有8080且串行。若8080被他人占用则停止门禁并记录环境缺口，不能杀其进程。后两条完成/失败都仅清理本次创建的preview、容器、秘密临时文件；共享Maven/浏览器缓存保留。

预期六条退出0，所有新spec实际执行，Maven/JUnit/Playwright报告无失败、错误或未解释跳过。总数从当前报告计算；不硬套T09后端1019/T11前端468基线。`clean verify`不包含全部*IT，不能替代第一条。保留完整失败→修复→最终通过关系；最终截图对真实详情桌面1440和窄屏390各一张进行目视核对，pageerror为空、无页面横向溢出。审查关闭后再完成T12；发布脚本未满足条件不伪报通过。

## Acceptance

1. 真实应用、MySQL与非Tushare来源完成页面提交/详情/刷新/全BrowserContext关闭再打开；无打开页面期间来源与SQL提交计数均继续增长，taskId和唯一提交保持。
2. 同库新应用runId不同，旧队列/运行任务恢复事实正确；多次GET不会发来源。手动resume仅执行未完成，普通失败保留；全成功只重算、不重复累计。
3. 三批部分失败/真实回执丢失/拆分原子性/证券回滚/旧许可/双控制/数据库故障与总体§5.1均映射到实际执行的方法和SQL/调用断言；未知HTTP参数形状拒绝、生产40项不变。
4. 专用IT准确验证子进程退出与flow2/resume1实际执行；普通七spec隔离运行并完成迁移，T13外部live边界显式可发现，不能用排除或list冒充通过。
5. 六条当前源码门禁最终通过；V8/包/脚本断言精确，main/干净输入/HEAD发布前置保留，未运行发布脚本清楚标明原因。
6. 运行手册与当前属性/CORS/REFERENCES权限/单实例/手动恢复/计数含义一致；验证文档脱敏、追踪实际数据与证据边界。34项生产RANGE仍NEEDS_VERIFICATION，T13和母issue未完成。

## Risks

- 真实浏览器/容器/验收包套件耗时且需本地端口；测试缺环境必须失败或据实记录阻塞，不能退回route mock或删除检查。
- 新同库启动测试覆盖应用上下文重建及显式故障点持久快照，不等于任意操作系统/硬件崩溃注入；报告必须写明故障控制方式。
- 旧普通浏览器harness包含证券查询、秘密检查与精确日志合同，迁移task API时不能删去这些覆盖；所有实际失败须定位后最小修复。
- 外部tushare-live脚本仍有旧同步页面助手，T13需按真实任务流程适配后执行；本项只保留明确入口/发现，不报告其数据或页面验收通过。
- 发布脚本只能在main且受保护输入干净时运行；当前已暂存开发分支不满足，六条当前源码证据与未运行发布证据须分别记录。
- 未发现阻止实现的需求缺口；环境条件在执行时核实。本设计不授权真实账户调用、开放RANGE或发布。
