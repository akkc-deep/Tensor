# RANGE-T18 受控来源与 MySQL 故障集成验收

2026-09-09，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 RANGE-T18，依据[完整设计](../task-designs/RANGE-T18-design.md)与[入口交接](../task-handoffs/tensor-range/RANGE-T18-handoff.md)。本轮功能与规定验证已完成；最终独立集成／证据评审结论见文末。看板状态以其完成记录为准。

## 运行范围

本轮只使用 acceptance fixture、loopback 受控来源及隔离 `mysql:8.4.6`。受控真实定义保持原业务表与列；合成取全、日历和恢复能力不进入生产能力登记。不调用真实 Tushare，ISSUE-008 九项仍“不依赖，未解决”。

正式运行时 fixture 支持 `ORIGINAL_PARAMS`、`TRADE_DATE_RANGE`、`ANN_DATE_RANGE`、`MONTH_RANGE`、`NATIVE_RANGE`；五种既有 scenario 保持。日期模式可选受控 `STOCK_TIME`，其他模式为 `REQUEST`。分页客户端在终页完整证明前不发布数据，后页失败以精确 REQUEST 保存；成功恢复单元和失败记录的事务行为沿用既有服务。

## 实施发现

- HTTP 先行测试记录在 `.superpowers/sdd/RANGE-T18-design/fixture-red-http/`：未包装 fixture 缺运行批次能力，区间参数尚未投影；三个断言失败与前期测试设置／编译错误分别归档，不把忽略失败的 reactor 退出码当通过。
- 现有 `ParameterCodec` 按明确形状选择强类型下载参数，缺少 fixture 的 scenario＋区间及可选股票组合。该实际接入缺口按设计的最小修复规则处理；新增形状不得改变49项生产合同。
- runtime分页客户端与独立脚本reader均启用BigDecimal读取。脚本reader原始JSON数字反例的真实HTTP轮回从错误的 `12345678901234567000` 修正为精确 `12345678901234567890.123456789012345678`；`support-decimal-red/`为1项实际断言失败，`ControlledRangeSourceTest`在后续本轮verify中通过。四脚本原本金额为字符串，该问题不改其既有数据。
- SQL阶段run-1仅编译失败，run-2为43项中8个测试装配错误，无产品断言失败；修正受控参数图、精确日历条件及遵守DB CHECK的边界注入后run-3为43项、run-4补强为45项通过。保留每次失败及修正证据，不将其误记为产品行为RED。

## 验收层次与17项定位

下表编号严格对应 TRD §12.3。`Http` 指 [RangeFixtureHttpIT](../../data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/RangeFixtureHttpIT.java)，使用真实随机端口 Spring Boot；`MySql` 指 [RangeFailureMySqlIT](../../data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/RangeFailureMySqlIT.java)，使用真实服务／事务／repository及生产响应映射，其中三个原生DATE案例直接调用生产controller，**不是网络HTTP**；`Process` 指 [RangeProcessIT](../../data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/RangeProcessIT.java)，使用本轮 acceptance JAR 的真实子JVM与socket。

所有层次均使用真实 Flyway V1～V8（包含既有 test V6）和独立 MySQL 8.4.6。来源捕获、业务表和两失败表SQL来自本轮；原始SQL使用绕过故障代理／业务repository的新连接，核对结果不回流服务补计数。业务行按完整业务键排序，明细按五字段主键排序。JSON比较按内容，created_at保持，updated_at允许原因刷新。

原始证据根目录为忽略的 `.superpowers/sdd/RANGE-T18-design/`。各次命令分别归档 stdout、仅本次新鲜XML、实际数量及退出码。表中 `http/`、`mysql/`、`process/` 文件名是稳定场景索引；最终归档目录和重跑范围在下一节注明，不能用旧运行覆盖新源码的证据。

| # | 本轮稳定测试／参数集 | 来源、独立SQL及可观察事实 |
|---:|---|---|
| 1 | Http `bareFixtureFirstHttpExecutesWithoutWrapper`、`preflightRejectsThirtyTwoDaysAndBadParamsWithoutSourceOrTasks`、`calendarMissingDayFailsBeforeBusinessAndAllClosedPreservesOldRows`；Process初始kill | 前置失败无业务来源调用；启动／全成功／全闭无失败记录，旧业务不删除；`bare-fixture`、`preflight-rejections`、`calendar-*`及kill前后SQL。 |
| 2 | Http `completePagesIsolateBadStockWithoutInventingMissingMembersAndEmptyRetryDeletesOnlyItem` | A/C提交、B字段错，S/F=2/1，I=2；仅B为STOCK失败，`complete-stock-isolation`。 |
| 3 | Http `twoStockScriptsKeepSeparateKeysUpdateReasonAndDeleteIndividually` | 同日两股完整键不合并，2→1→0明细，0→1→2业务；重复失败只留B，原UUID／条件不变，`two-stocks-*`。 |
| 4 | MySql `independentStocksKeepAAndCWhenBSecondSqlGroupRollsBackAndRetryDeletesPrecisely`、`finalHeaderDeleteFailureRollsBackInsertAndUpdateTogether` | fina_audit B三行跨batchSize=2的第二组被trigger拒绝；原B值8不变，A/C保留；明细／主表DELETE失败均将I/U一起回滚，移除trigger后显式retry；`stock-second-sql-group`、`precise-item-delete-*`、`final-header-delete-*`。 |
| 5 | Http `pageTwoFailurePublishesNoPriorRowsAndSavesExactRequest`、`incompleteOrUnattributedPayloadStaysRequestAndCompleteMemberFailureIsStock` | 第2页失败、总数／终页证据／归属错误时前页业务0，精确REQUEST；完整显式成员失败才为STOCK，`page-two-failure`、`payload-*`。 |
| 6 | Http完整股票、缺股及合法空案例 | 缺行不推断失败；B合法空重试只删其项，A/C业务保留；`empty-stock-retry`、`missing-stock-is-not-failure`。 |
| 7 | MySql `exactRangeRetryNeverCropsOrUpgradesSavedObject[REQUEST_VALIDATION,REQUEST_SQL,STOCK_RANGE]`、`committedStockIsNotRewrappedWhenNextDateRequestFails` | 原01～10内保存01～03完整RANGE；字段／SQL失败保留原对象，成功只删除该项；先提交STOCK不纳入后批REQUEST，`exact-range-*`、`stock-success-then-request-failure`。 |
| 8 | Http `everySourceFailureContinuesLaterDatesWithoutAutomaticRetry`九码；MySql `firstMultiDaySegmentFailsAndSecondCommitsInSameInitialExecution` | 每码首次连续两日期失败后第3日成功；同次retry第一项再失败、下一项仍成功；另04休市，01～03段失败已保存后，同一次execute实际请求05～07并提交3行；`source-code-*`、`two-multi-day-segments`。 |
| 9 | MySql `failureSaveRejectionStopsLaterSourceAndKeepsOnlyConfirmedFacts[first-insert,append,reason-update]`、commit未知create/reason各提交前后 | 首次主子原子0/0、追加保留先前任务／业务、原因失败保留原项；后续来源0，remaining=null，`save-rejection-*`、`commit-unknown-create/reason-*`。 |
| 10 | Process `suddenExitPreservesOnlySavedFailureAndRestartDoesNotResume`、`suddenExitDuringRetryDoesNotRestoreAlreadyDeletedItem`、最后项断连；MySql最后DELETE丢回复 | day5来源entered后强杀，SQL仅1/2/4业务及day3失败；重启仅显示3，显式只取3。retry先删3、day7强杀后仅7保留。最终删除后execute404，无重插。 |
| 11 | Http `localModesHonorRangeBoundariesAndIndependentInsertUpdateCounts`、31/32日、日历；MySql `closedRetryDeletesFailureWithoutTouchingOldBusinessAndCountsClosedDate` | 同日、2028闰日、跨年、完整月、NONE；全闭retry H=1/S=0，无业务调用，删除失败但旧行保持；合法空只删该项。 |
| 12 | MySql `invalidStoredTasksRefuseBeforeSourceAndPreserveActualSql[structure,selector,unknown-type,code,conflict,non-executable,json-text]` | 来源0且实际SQL原记录不变。合法JSON内非法结构、非法日期等真实入库；坏JSON文本／未知enum受MySQL约束禁止，**仅在真实repository的JDBC ResultSet读取边界注入**，不禁约束，不冒充坏文本实际存入DB。 |
| 13 | Http A/C、空／闭及I/U；MySql八种commit未知、`frameworkConfirmedCommitCountsFactBeforeStopping`、`futureStockCardinalityRemainsNullWhenSaveOrDatabaseConnectionStopsExecution` | 每种业务／创建／原因／删除分别实际rollback及delegate.commit后SQLState08；未知S/R/I/U=0，不反查补计数，remaining或未来STOCK N=null；框架确认COMMITTED后异常单独S=1/I=1。四停止HTTP映射另由本轮重跑的原HTTP IT核对。 |
| 14 | Process `initialDisconnectKeepsSlotUntilActualExecutionEnds`、`lastRetryDisconnectDeletesAtomicallyAndNeverReinserts`，各RST／read-timeout-close | 真实客户端关闭，来源仍hold时另一首次／execute均409、GET可读；release后SQL实际提交／删除，真实完成才释放槽位，没有取消／自动重发。 |
| 15 | MySql `nativeDateInitialAndRetryUseIdenticalCompactNativeKeysThroughRealControllers[trade_cal,new_share,namechange]` | 实际定义／adapter／controller映射保留真实日期语义；首错与retry均start_date=end_date=20260903，trade_cal exchange=SSE保留，无trade_date/ann_date；retry真实各入1行并删两表。namechange仅合成受控。 |
| 16 | Http `browserScriptsRetryOnlyThreeAndSevenThenSevenAndPreserveOriginalTask`及Process两种kill | 原始01～10，来源10→仅3/7→仅7，业务8→9→10、明细2→1→0；列表／详情保留原区间与created_at，`sparse-*`，重启不推断或重新登记中断日。 |
| 17 | Http边界；MySql `monthRetryUsesFullSeptemberAndOldMissingDatesAreNotInferred`、`elevenOriginalContractsKeepExactConditionsWithoutDateEndpoints` | 原08/15～09/03不变、仅9月失败且retry整月202609；旧缺日期为NOT_RECORDED，按明细恢复。11实际原条件定义不新增日期端点，NONE按原条件请求／空retry。49合同由resolver及静态合同另核对。 |

受控fina_audit只在内存definition和descriptor共同将股票设为可选；真实列／键／表不改。受控daily RANGE图在两侧定义相同start/end和可选股票，生产daily YAML不改。宽表income保持真实必填ts_code逐股取数。这些能力均不登记为真实Tushare支持。

## 最终命令与构建

| 归档目录 | 实际Java测试 | 退出码／F／E／S | 总耗时秒 |
|---|---:|---|---:|
| `final-fixture/` | 82 | 0／0／0／0 | 4.286 |
| `final-core/` | 153 | 0／0／0／0 | 3.479 |
| `final-mysql/` | 216 | 0／0／0／0 | 178.504 |
| `final-verify/` | 891 | 0／0／0／0 | 44.842 |
| `final-acceptance/` | 894 | 0／0／0／0 | 46.627 |

各命令按下列顺序串行执行，MySQL／本机JVM测试使用 `env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` 环境；未启用真实来源。

```sh
mvn -f data-plane/pom.xml -pl tensor-plugin-fixture -am -Dtest=FixturePluginTest,FixtureEnvelopeFactoryTest,FixtureBatchSourceTest,DownloadParameterProjectionTest,SourceParameterMapperTest -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadServiceTest,RetryDownloadServiceTest,DownloadBatchPlannerTest,RecoveryUnitProcessorTest,BatchCommitServiceTest,RetryTaskStorageServiceTest,RetryTaskQueryServiceTest,DownloadParameterConverterTest,TaskParametersJsonTest -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RangeFixtureHttpIT,RangeFailureMySqlIT,InitialDownloadServiceIT,RetryDownloadServiceIT,BatchCommitServiceIT,RetryTaskStorageIT,RetryTaskControllerIT,DownloadControllerIT,DatasetControllerIT,FixtureFlowIT,ProductionApplicationContextIT,PersistenceServiceIT -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f data-plane/pom.xml verify

mvn -f data-plane/pom.xml -Pacceptance clean verify
```

每个明确指定类均在本次XML中出现且tests>0；`failIfNoSpecifiedTests=false`仅容许reactor无目标类的模块。final-mysql的12类分别为 BatchCommit22、Initial11、Retry11、Storage13、FixtureFlow5、RangeFailure45、RangeFixture27、ProductionContext1、Dataset48、Download11、RetryController14、Persistence8。两次完整构建均另有31文件356项前端测试通过及Vite1708模块构建成功。生产包4项／acceptance包3项合同确认：生产无fixture／test classes，acceptance仅fixture JAR及V6两项既定增量。普通verify不替代显式IT，本项没有运行浏览器e2e。

构建后仅测试证据层补强，207份runtime文件／fixture与JAR不改。`final-http-schema/`在53.953秒完成27项（0F/E/S），50份HTTP快照三表schema前后相等，此次替代final-mysql中的旧HTTP27证据；不是额外累计成一次243项。`final-process-load/`共10项、331.770秒通过；`final-sql-cause-fixed/`共46项、48.489秒通过，均0F/E/S。后者替代final-mysql中的45项SQL类，保留原单次216项结果，不能拼成同一次217项。

```sh
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RangeFixtureHttpIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RangeProcessIT,RangeLoadIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RangeFailureMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

## 进程、性能与安全

Process只启动和清理本轮持有的child／来源／container，绑定loopback、移除Tushare token并显式禁用真实插件；等待实际metadata HTTP、来源entered、独立SQL和实际进程退出，未用sleep推断业务完成。每场景保存JAR SHA256、PID、端口、child日志、HTTP、source、schema和事件链。`destroyForcibly`模拟突发退出；重启不是产品恢复执行。

[RangeLoadIT](../../data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/RangeLoadIT.java)三类负载各一次热身、三次插入和三次独立更新：18,600行（31日×3页×200股，31 REQUEST）；10,000股同日（20页×500行，10,000独立STOCK事务）；真实income 85列5,000行（100股×50合法季度键，500页，100单元）。三类全部以实际SQL行数、七计数、来源完整页序及两失败表0核对。每轮开始／结束保存schema及索引（去统计Cardinality），失败路径也保留已有采样、结果、来源和SQL，原异常不被证据失败覆盖。

最终 `final-process-load/` 组合运行331.770秒，Process7项／Load3项共10项，0失败／错误／跳过。7个子进程场景均记录同一新JAR摘要 `626e0e840370b8b44d4f9a280444a1adb88aa9c658a16a78bf99a83d900ca0e0`；207份runtime文件从clean构建到最终运行未变。最终18轮SQL、来源页数、85列非空值、两失败表0、schema和采样文件独立复核通过。

环境为macOS／Apple M1 Pro／16GiB，JDK21.0.11+9-LTS-211、MySQL8.4.6、batchSize500。测试JVM没有显式-Xmx，实际maxHeap4GiB；Process子JAR为-Xmx512m。

| 轮次 | 耗时秒 | heap采样峰值MiB | RSS采样峰值MiB | 来源脚本生成ms |
|---|---:|---:|---:|---:|
| multi-day-1-insert | 8.196 | 438.87 | 770.88 | 15.952 |
| multi-day-1-update | 8.615 | 400.37 | 753.27 | 15.952 |
| multi-day-2-insert | 7.651 | 176.29 | 456.95 | 15.952 |
| multi-day-2-update | 8.041 | 172.15 | 441.44 | 15.952 |
| multi-day-3-insert | 7.771 | 169.27 | 441.80 | 15.952 |
| multi-day-3-update | 8.014 | 164.18 | 433.66 | 15.952 |
| high-stock-1-insert | 29.500 | 166.70 | 439.27 | 7.597 |
| high-stock-1-update | 29.728 | 127.58 | 387.75 | 7.597 |
| high-stock-2-insert | 29.461 | 126.68 | 315.62 | 7.597 |
| high-stock-2-update | 29.494 | 132.94 | 331.44 | 7.597 |
| high-stock-3-insert | 28.399 | 121.57 | 309.31 | 7.597 |
| high-stock-3-update | 28.564 | 108.43 | 281.78 | 7.597 |
| income-1-insert | 7.496 | 95.27 | 335.45 | 56.313 |
| income-1-update | 4.655 | 241.46 | 497.81 | 56.313 |
| income-2-insert | 4.506 | 505.77 | 798.88 | 56.313 |
| income-2-update | 4.511 | 245.36 | 508.84 | 56.313 |
| income-3-insert | 4.372 | 420.31 | 735.98 | 56.313 |
| income-3-update | 4.537 | 407.67 | 747.67 | 56.313 |

| 负载／阶段 | 耗时秒 中位／最大 | heap峰值MiB 中位／最大 | RSS峰值MiB 中位／最大 |
|---|---:|---:|---:|
| multi-day insert | 7.771／8.196 | 176.294／438.869 | 456.953／770.875 |
| multi-day update | 8.041／8.615 | 172.147／400.366 | 441.438／753.266 |
| high-stock insert | 29.461／29.500 | 126.681／166.697 | 315.625／439.266 |
| high-stock update | 29.494／29.728 | 127.584／132.935 | 331.438／387.750 |
| income insert | 4.506／7.496 | 420.313／505.768 | 735.984／798.875 |
| income update | 4.537／4.655 | 245.365／407.674 | 508.844／747.672 |

耗时使用单调时钟，覆盖规划／来源到最后事务确认；脚本生成开销单列。每50ms采样heap-used和执行PID RSS；来源与执行图共享测试JVM，包含来源内存开销，子JAR进程场景另计。采样峰值不是绝对瞬时峰值，不作为SLA或产品执行上限。

安全分层：来源body/header的canary在真实子JAR中核对Web错误、任务参数／原因及child日志；SQL异常cause的canary在MySql层通过真实trigger／JDBC异常、生产controller映射、两失败表及应用日志核对（最终46项复跑通过，`realSqlCauseCanaryIsRemovedFromControllerStorageAndApplicationLogs`，原始SQLException含canary／SQLSTATE45000，结果、两表和完整异常链日志均无canary，`sql-cause-*`快照）。runtime client将错误映射为固定安全码／消息，正常业务字段仍应入库。来源URL限定http loopback origin、无query／userinfo／路径、不跟重定向、无自动重试。来源的捕获只含合成条件／页号，正式报告不含实际账号或数据库密码。

## T19可复用入口

四份本轮实际HTTP测试消费的脚本位于 `data-plane/tensor-app/src/test/resources/fixture/`：`range-browser-initial.json`（01～10仅3/7失败）、`range-browser-partial.json`（3成功、7仍失败）、`range-browser-success.json`及`range-browser-two-stocks.json`（同日两股失败）。partial／success另含每股精确ts_code＋ann_date规则，不能用无股票规则替代STOCK retry。

最终 Process run 已在 `data-plane/tensor-app/target/range-acceptance/test-classpath.txt` 写出该次测试JVM完整classpath。先把initial复制到本轮临时目录的scenario.json，再独立运行已验证main：

```sh
java -cp "$(cat data-plane/tensor-app/target/range-acceptance/test-classpath.txt)" com.akkc.tensor.fixture.support.ControlledRangeSource /tmp/tensor-range-browser/scenario.json 4189
```

每次请求读取本地脚本快照；在同目录写临时文件后原子rename到scenario.json切换阶段，无测试控制端点。main实际进程启动／精确响应／原子替换后成功／只清理自有child的证据在 `standalone-source.json`。

独立数据库的URL／用户名／密码由T19本轮隔离环境提供，通过既有 `TENSOR_DB_URL/TENSOR_DB_USERNAME/TENSOR_DB_PASSWORD` 环境变量注入；不可指向已有用户数据库。以下与Process实际启动相同，仅端口改为浏览器固定测试端口：

```sh
env -u TENSOR_TUSHARE_TOKEN TENSOR_TUSHARE_ENABLED=false java -Xmx512m \
  -jar data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar \
  --spring.profiles.active=acceptance --server.address=127.0.0.1 --server.port=8080 \
  --tensor.plugins.fixture.enabled=true --tensor.plugins.fixture.mode=ANN_DATE_RANGE \
  --tensor.plugins.fixture.recovery=REQUEST --tensor.plugins.fixture.source-url=http://127.0.0.1:4189 \
  --tensor.plugins.tushare-pro.enabled=false
```

等待 `/api/v1/data-sources/fixture/apis` 实际200。公共HTTP日期必须紧凑YYYYMMDD，scenario=SUCCESS；原始区间响应也为紧凑格式，存储selector DATE为ISO日期。同日两股阶段在隔离数据／独立child改recovery=STOCK_TIME；模式运行中不可改。所有启动和清理只涉及本轮进程／容器。T19无需再实现fixture分页能力，但仍须完成正式页面及配置后的e2e；本报告不把后端HTTP通过记成浏览器通过。

## 评审与工作区保护

开始时分支 `feat/date-range-download`、HEAD `758f940503ded2d1185040bc8e324815c716a300`；816份原跟踪文件摘要／原索引保留。任务外跟踪内容、58份生产资源（49 Dataset、2能力表、7迁移）及fixture YAML／V6与开始时相同。12个本轮新建源码／测试／脚本及本报告纳入Git；原有索引项均未改，不提交／发布。

独立fixture／SQL／Process-Load规格与质量评审及复审通过。已关闭的Important：独立脚本数字精度、Load失败证据保留与schema、HTTP三表schema前后比较、明确SQL异常cause canary。后两项在完整构建后只补测试，运行源码／JAR摘要未变，分别以27HTTP及46SQL复跑核对。最终cause补强首次为编译失败（SQLException实现Iterable导致AssertJ重载歧义），下一次为46项中1个测试参数图绑定错误；修正为真实daily定义／受控日历后46项全部通过。两次失败原始证据仍保存在final-sql-cause及final-sql-cause-green，均不计作通过或产品行为RED。

最终独立规格／质量／集成／证据评审均PASS，无剩余Important／Critical，报告路径 `.superpowers/sdd/RANGE-T18-design/final-review.md`。合同8组＋4变异拒绝、两种diff空白检查及工作区保护审核通过。T19正式页面闭环、T20真实来源尚未验收；ISSUE-008继续“不依赖，未解决”。
