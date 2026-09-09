# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T17`，已记录 COMPLETED；本项设计及交接在该完成记录之后准备。
- **Next task:** `RANGE-T18`，按预定义 Order 选择18，观察为 NOT_STARTED。
- **Design document:** `docs/task-designs/RANGE-T18-design.md`（[完整设计](../../task-designs/RANGE-T18-design.md)），已全文读取、自审及独立就绪评审通过，先回填看板 Design document，保持原状态与 Handoff。最终 SHA-256：`28fb6d543f4956048213e68c009287f62a018c25206e78c5af495b560d70afbd`。
- **Expected next status:** `READY`；本交接写入、链接后执行 NOT_STARTED -> READY，不自动启动本项实施。

## Next Task

`RANGE-T18`：受控来源与 MySQL 故障集成验收。

交付可运行的 acceptance fixture 批次能力和受控后端，用真实 HTTP、隔离 MySQL、真实子 JVM／socket 证明失败隔离、精确重试及故障停止。覆盖多批、多股、多页、局部字段错误、SQL 分组／明细删除／记录保存失败、commit 未知、真实进程重启及断连；为 T19 提供本轮实际验证的脚本及启动配方。

可观察验收：

- 设计 Approach §6 严格对应 TRD §12.3 的17组场景，每组有本轮测试、真实来源参数／页序及业务表／两失败表独立 SQL；A/C提交、B全部回滚，同日两股分别删除，最后项成功删除主表。
- 原始1～10日与当前3／7→7分离，只请求实际明细；REQUEST／单股RANGE保持边界。九类来源失败不预判后续结果；多日开市段失败保存后，同一execute仍调用下一段。不预登记、不自动重试、不补造中断缺失项。
- 记录保存／commit未知停止，保留先前确认小计，未知数量不填0。真实子进程突发退出及真实socket RST／超时关闭证明断连不取消、实际结束前槽位仍忙；404不重插、409不排队。
- fixture真实实现planBatch／fetchBatch，原scenario默认行为保持；交付四份可切换脚本，覆盖REQUEST和同日STOCK的首次／部分／最后成功。受控真实定义图复用同一分页客户端，不重新实现另一套分页框架。
- 显式MySQL测试及两次完整verify、参数迁移／打包／合同检查通过；测量18,600行多日多页、10,000股单日高量、5,000行85列income的耗时与采样内存峰值，不新增产品执行上限。
- 安全证据、正式报告、需求追踪及独立最终评审完成。仅升级AC-PRD-RANGE-02～18、21～29后端适用事实；T19页面、T20真实来源及ISSUE-008边界保持。

## Dependencies

### RANGE-T12：首次编排与保存门槛

- **Artifact:** [T12设计](../../task-designs/RANGE-T12-design.md)、[首次编排验证](../../verification/RANGE-T12-initial-execution.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`、`DownloadBatchPlanner.java`、`RecoveryUnitProcessor.java`、`BatchCommitService.java`、`DownloadExecutionSlot.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/db/InitialDownloadServiceIT.java`、`BatchCommitServiceIT.java`。
- **Decision:** 先完成所有参数／Session／日历／规划预检，再取数；完整批次到齐后独立单元适配／提交，首次失败确认保存才接纳taskId，后续追加原任务。来源失败保存后继续全部计划，存储停止按实际确认结果处理。
- **Rationale:** 请求批次和恢复事务的边界不同；成功单元不能被相邻失败撤销，未知保存也不能虚构已持久化任务或未开始范围。
- **Constraint:** 来源取数不在业务事务内；S/R/I/U只累计确认提交，未来股票成员未知时N=null；同一进程槽位覆盖新／旧下载和重试，实际结束才释放。真实生产策略不升级。
- **Usage:** 复用实际服务及DataSource探针，扩展为本轮Web／MySQL证据；SQL分组场景复用 `independentStocksPreserveAAndCAfterLaterSqlGroupRollsBackOnlyB` 的受控fina_audit图，仅内存definition和descriptor共同将ts_code可选，列／键／真实表不变。另以受控TRADE+RANGE和两个多日开市段证明同轮继续。
- **Readiness evidence:** T12已COMPLETED；Core最终114项、显式MySQL6类62项、helper31项通过；verify775／acceptance778项Java及各170项前端和构建通过。初次十日3／7、同批A/C保留B回滚、保存／commit未知均有证据，独立规格／质量／集成PASS。已有等待者超时／服务图不等于T18真实进程／socket。

### RANGE-T13：原任务精确重试与事务确认

- **Artifact:** [T13设计](../../task-designs/RANGE-T13-design.md)、[精确重试验证](../../verification/RANGE-T13-exact-retry.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/RetryDownloadService.java`、`BatchCommitService.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/SourceParameterMapper.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/db/RetryDownloadServiceIT.java`、`RetryTaskStorageIT.java`。
- **Decision:** 重读原任务当前明细，冻结公共条件副本，移除仅供展示的日期，再从完整selector生成真实来源参数。成功业务与精确明细删除同事务，最后项删除主表；失败只更新原原因，不创建子任务／新历史。
- **Rationale:** 原始区间只解释任务来源，恢复范围由剩余明细确定；响应丢失不能恢复已删记录，数据库实际已提交与调用方确认是不同事实。
- **Constraint:** REQUEST不升级STOCK、RANGE不裁剪，完整五字段键匹配；原JSON／created_at保持，updated_at可按原因更新。delegate.commit后异常仍可能未知；框架已确认COMMITTED后的异常保留已确认计数。禁止事后读回补计数、自动重插或继续未知事务。
- **Usage:** 复用真实事务／存储／mapper及SQLSTATE45000、commit后08探针，扩展首次与重试的故障矩阵；将“服务重建”升级为真实acceptance JAR突发终止／重启，独立SQL确认仅原剩余项。完整MONTH、原生单日、空／全闭、非法记录均依设计验收。
- **Readiness evidence:** T13已COMPLETED；Core最终130项；MySQL原七类70项加后续11项Retry替换旧8项，形成73项复合证据，**不是一次73项运行**；helper31、verify795／acceptance798及各170项前端／build通过。3／7→7、同日两股、最后项commit丢回复、原因保存未知、COMMITTED后异常均有独立SQL；规格／质量／集成PASS。真实kill／socket尚未由该输入证明。

### RANGE-T14：生产Web入口、只读任务和错误快照

- **Artifact:** [T14设计](../../task-designs/RANGE-T14-design.md)、[HTTP验证](../../verification/RANGE-T14-http.md)、[OpenAPI](../../contracts/openapi-v1.yaml)、[错误目录](../../contracts/error-codes.md)；`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`、`RetryTaskController.java`、`dto/DownloadResponse.java`、`dto/RetryTaskResponse.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RetryTaskControllerIT.java`、`DownloadControllerIT.java`、`RangeResponseContractTest.java`。
- **Decision:** 首次唯一入口调用executeInitial；任务GET只读当前保存记录，原始区间四status独立于当前失败范围。execute只接受完整UUID及真正零字节body，不接受可编辑条件。结果18字段与任务detail17字段不同；实际mapper保留数字／null和统一RequestId。
- **Rationale:** GET和POST之间记录／槽位可变，GET快照不是执行承诺；客户端失去响应不能凭列表推断本轮成功数量。
- **Constraint:** 合法busy409先于不存在404，GET不占执行槽也不调用来源；输入拒绝400先于busy。四停止码及UNCONFIRMED快照保持确认口径，安全正文／日志不回显来源响应或凭证；49项参数迁移与旧只读查询必须回归。
- **Usage:** 新HttpIT使用真实随机端口与生产mapper／controller/service；ProcessIT使用本轮真实JAR，客户端Socket实际写出零body并关闭连接，再由另一HTTP请求验证409和GET。保留现有MockMvc／latch用例作回归，不将其当真实网络中断证据。
- **Readiness evidence:** T14已COMPLETED；首三HTTP39、Core80、App236、显式MySQL8类114；verify829／acceptance832项Java及各170项前端／build通过。三任务端点、真实mapper、四停止快照、MySQL闭环及安全证据、最终规格／质量／集成PASS。报告明确fixture正向HTTP使用测试包装，真实runtime批次能力留本项；响应写出错误是MockMvc，非实际socket。

### 现有fixture、验收打包和隔离数据库

- **Artifact:** `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java`、`FixturePlugin.java`、`FixtureEnvelopeFactory.java`、`FixtureScenario.java`；`data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`；`data-plane/tensor-app/pom.xml`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java`；既有test V6及七份生产Flyway迁移。
- **Decision:** fixture只在acceptance profile且enabled=true注册；默认ORIGINAL_PARAMS保留SUCCESS／EMPTY／SOURCE_FAILURE／TYPE_FAILURE／PERSISTENCE_FAILURE。表`fixture__fixture_daily`四列及(ts_code,trade_date)键保持。acceptance包严格为生产包加fixture JAR与V6，不含test classes。
- **Rationale:** 下游浏览器需要真实可运行fixture，但合成来源不能进入生产支持范围；故障控制留父测试进程，重启应用不会丢失受控来源阶段。
- **Constraint:** 默认两参公开构造器及旧download兼容；plugin.sourceParameters与adapter.definition参数等价，日期投影／转换复用已有代码。SOURCE完整状态没有VERIFIED枚举。默认verify不跑普通IT；MySQL须显式选择、真实Flyway、不skip、不复制DDL。scope仅本轮child／server／container，禁止清理用户已有服务。
- **Usage:** 按已完成设计扩展启动mode/recovery/source-url、共享definition；交付public acceptance专用 `FixtureBatchSource(URI, DatasetDefinition)` 的fetch／confirmCalendar供真实fixture和受控定义图共用，受控来源规则含apiName／精确参数／page。先构建acceptance JAR，再显式执行Process／Load；输出四脚本、真实classpath和启动配方给T19。
- **Readiness evidence:** T12～T14每次显式回归包含FixtureFlowIT，T14最新5项通过；它实际为plugin→adapter→persistence/query，不含HTTP。T14两次完整构建含既有生产／acceptance包合同，当前未包装FixturePlugin新HTTP安全拒绝有明确证据；本交接不运行T18功能测试，不把待实现能力记作已具备。

以上输入一致：首次／重试共同采用完整批次和独立单元事务，Web只投影确认事实；生产fixture尚缺批次能力是本项明确交付，非可跳过依赖。设计就绪评审已关闭两处Important：多日失败后同轮继续的场景缺口，以及宽表／真实定义图复用分页收集器的接口缺口；已固定突发kill和STOCK脚本精确匹配，没有未决产品事实。

## Start Here

1. 完整读取[本项设计](../../task-designs/RANGE-T18-design.md)，核对[看板](tensor-range-task-board.md)的RANGE-T18与本交接；READY仅表示准备就绪。
2. 按设计顺序读取PRD §9、TRD §9～§12及其17场景，然后读fixture实现／测试、FixtureFlowIT、PersistenceServiceIT，再消费上述T12、T13、T14设计／验证及对应实际接口。
3. 获得本项明确启动请求后记录READY -> IN_PROGRESS，保存当时原分支／HEAD／索引、任务外文件及58资源的新基线；本交接的依赖历史测试数量不是T18验收结果。
4. **实施第一动作：** 按设计在`RangeFixtureHttpIT`建立隔离MySQL与真实Web的行为RED：未包装fixture首次HTTP可执行；第2页失败时业务0且保存精确REQUEST；原1～10／失败3、7，显式重试后来源仅3／7、当前仅7。先区分缺文件／编译失败与行为失败，再实现固定的fixture批次客户端及共享definition，无需另补设计。随后按17行推进SQL故障、真实进程／socket和负载验收。

## Risks

- READY不表示T18已经实现或测试通过；依赖报告只提供可用入口及历史结果，本项必须运行设计规定的所有命令、17场景和独立SQL。
- 生产日历／完整来源保持保守关闭；合成fixture及测试内fina_audit／daily能力不可外推真实支持，income宽表仍按真实必填股票条件逐股请求。ISSUE-008九项仍不依赖、未解决。
- 真实process测试使用本轮acceptance JAR摘要和Process.destroyForcibly，避免普通destroy测成graceful等待；初次／重试RST和读超时后close均需事件链证明槽位持续占用。无产品取消入口。
- MySQL JSON列不能保存非法JSON文本，该边界仅测试读取侧损坏文本，真实DB另测合法JSON中的非法结构，不能伪造坏文本入库证据。独立SQL不反馈业务计数。
- 两次clean会覆盖XML，先归档；默认verify不替代普通IT。性能是固定规模、当前机器的采样观察，不是硬SLA或来源上限。
- 保护当前原索引／任务外工作和58生产资源，新正式文件加入Git，不提交／发布；本轮交接不自动开始实施。
