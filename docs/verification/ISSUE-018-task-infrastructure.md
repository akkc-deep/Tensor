# ISSUE-018 任务基础设施验证

- 任务：`ISSUE-018-T12`；权威看板：`docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- 实施合同：`docs/task-designs/ISSUE-018-T12-design.md`；实施计划：`docs/superpowers/plans/2026-09-12-issue-018-t12.md`。
- 状态：2026-09-12 T12已记录COMPLETED，六条源码门禁通过，独立审查规格/质量PASS、运行门禁CLOSED、零开放发现。历史暂停交接为 `docs/task-handoffs/ISSUE-018/ISSUE-018-T12-handoff.md`；以下区分已有门禁与本次恢复验证。
- 环境：2026-09-12，Java 21.0.11、Node 24.15.0、npm 11.12.1；Docker 29.5.2/Colima，MySQL 测试固定 8.4.6，已安装 Chromium 1234。Docker、JVM 与浏览器须在正常本地权限下执行。

## 已取得的专项证据

| 验证 | 实际结果 | 日志 |
| --- | --- | --- |
| CORS RED | ProductionWebConfigurationTest 28 项中 1 项失败：期望 `X-Request-Id, Location`，实际仅 `X-Request-Id` | `/tmp/issue018-t12-cors-red.log` |
| CORS GREEN | 最小增加 Location 暴露；合法 origin 的 202 Location 与原非法 origin/UI/assets/Actuator 边界共 29 项通过，无失败/错误/跳过 | `/tmp/issue018-t12-cors-green.log` |
| 私有环境 helper RED | 新测试实际因尚不存在 packaged-test-environment.js 而失败 | `/tmp/issue018-t12-env-red.log` |
| 私有环境 helper GREEN | Node 内置测试 19 项通过：四文件五字段选择、原单文件兼容、0600/当前用户/普通文件/非符号链接、字段/前缀/JDBC/路径拒绝及不局部赋值 | `/tmp/issue018-t12-env-green.log` |
| 三个受控浏览器 spec | 57/57 通过（2.5 分钟），无失败/跳过；40 项 UI 元数据均经任务接收、详情 GET、成功计数核对，保留 34/6 参数规则、原键重确认、主题/布局/键盘/查询与 T11 三场景 | `/tmp/issue018-t12-mock-final.log` |

CORS 命令为 `mvn -f data-plane/pom.xml -Dtest=ProductionWebConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`；helper 命令为 `data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/packaged-test-environment.test.js`。受控浏览器命令为 `npm --prefix control-plane run test:e2e -- e2e/ui-redesign.spec.js e2e/stock-download-parameters.spec.js e2e/download-tasks.spec.js`，使用 Node 24 的 PATH 与本次回环 production preview4173。

首轮受控浏览器 47 项通过、3 项失败、7 项未运行：原分页样例超过八行后缺值，布局场景未填股票必填字段。修正夹具后相关 10 项通过；增加详情断言时修正“来源行数”的精确文案，最终完整 57 项通过。保留失败日志 `/tmp/issue018-t12-mock-first.log`、`/tmp/issue018-t12-mock-second.log`、`/tmp/issue018-t12-mock-detail.log`。原 ISSUE-004 已提交截图保留，新本次截图输出测试缓存，不覆写其历史证据。

Maven generate-resources 会执行 npm ci 并重写 node_modules；一次与浏览器并行运行造成 ENOTEMPTY，故后续 Maven/npm/浏览器共享执行槽串行验证。此环境失败不算通过，生命周期必须以完整重跑结果为准。普通套件排除 lifecycle 与真实账户套件，是显式运行边界，不是执行/跳过结果。

## 生命周期的持久事实与审查

真实 Servlet/MySQL 的五个方法为 `browserDisconnectAndRetryUseTheRealApplication`、`recreatedApplicationRecoversWithoutAutomaticSourceCalls`、`lostSubmissionResponseFindsOneCommittedTask`、`documentedSchemaPrivilegesSupportV8AndTaskWrites` 和 `rejectsMissingSpecAndInvalidBrowserReports`。聚焦日志 `/tmp/issue018-t12-gate1-fixes.log` 为80项全通过；随后补PENDING快照的首轮全量 `/tmp/issue018-t12-gate1-final.log` 为82类/1282项全通过。最终审查后的重跑结果在下文门禁表另记。

- flow断开：第一天 calls=1、SQL=0时关闭整个BrowserContext；无页面期间释放来源门，第二天calls=1且SQL已有第一天。最终三天各调用一次、SQL三键、三个成功叶子；原提交POST始终一次。新context从近期列表找同taskId，详情/刷新均成功。
- flow重试：首次三天calls各1，中日SOURCE_TIMEOUT且只保存首/末两键；一次原version retry后，中日calls/attempt=2，其余=1；source/insert/update=3/3/0、累计requestCount=4。
- resume：同库完整应用先关闭再重建、runId变化；QUEUED、未规划RUNNING、含RUNNING或PENDING的混合计划均中断；普通失败和成功叶子保留，全成功计划只重算。启动及重复GET总calls=0。浏览器resume只请求第三天，先在来源门内观察SQL仍只有第一天，再释放；最后PARTIAL_FAILED，第二天普通失败保留，attempt=1/1/2、source/insert/update=2/2/0。额外PENDING快照以HTTPresume只执行第三天（attempt=1/1/1）；旧队列和未规划任务同样必须人工HTTPresume。
- 已提交回执被吞：测试Filter等待Controller产生202后丢弃回执，不拷贝已缓存body/header；客户端只能观察无任务回执的500或IOException。以submissionId查询及重放找回唯一taskId，来源调用一次。该注入不等同TCP设备故障，实际观察分支以最终日志为准。
- 最低权限：专用账号仅单schema CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX/REFERENCES，执行七次生产迁移、启动应用、完成一个任务及一条证券写入，验证两个任务外键；无DROP/DELETE/全局数据权限，不把测试harness的TRIGGER写进生产授权。
- 子进程：flow精确两项、resume精确一项，必须一attempt、retry0、全passed、expected精确且skipped/unexpected/flaky0。缺spec真实子进程退出非0；缺失/畸形/零测试报告、错误spec、缺retry、retry1、flaky1均拒绝。每页pageerror空、所有请求origin固定为自有回环应用。

独立审查记录：`.superpowers/sdd/2026-09-12-issue-018-t12/root-review.md`、`lifecycle-review.md`、`packaged-review.md`。RANGE夹具矩阵、生命周期总来源调用、回执故障描述和请求来源边界已逐项复核，当前无开放实质静态问题。审查本身不替代下列运行门禁。

## 故障覆盖映射

以下列出当前源码的实际断言，对应第一条全量门禁 G1；当次 XML 保存在 `/tmp/issue018-t12-final-gate1-reports/TEST-<全限定类名>.xml`。核心测试路径为 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/`，HTTP 测试路径为 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/`。

| 场景 | 类 / 方法 | 证据类型与持久事实 |
|---|---|---|
| 三批部分失败后只重试中间批 | `DownloadTaskControllerIT.retriesOnlyTheFailedMiddleBatchAndReturnsStoredTimeoutAsQueryData` | 真实 MySQL + 受控来源；第一/第三批成功事实与 attempt 保留，中间批二次请求，StoredError 作为查询数据返回，计数只累计已提交结果。 |
| 两页以同版本 resume | `DownloadTaskControllerIT.concurrentResumeAcceptsOnlyOneVersionTransition` | 真实 MySQL + 并发 HTTP；精确一项 202、一项 409，`Location`/`TASK_STATE_CONFLICT` 正确，version 只加一；仅一个中断批从 failed 转 pending，其余批次/行数计数和 requestCount 不变、来源零调用，旧版本重放不改 DB facts。 |
| 手动重排提交成功但回执失败 | `DownloadTaskControllerIT.findsCommittedRequeueAfterReceiptFailureAndExecutesOnlyFailedWorkOnce` | 真实 MySQL + 受控提交故障；查询找回已提交代次，只执行失败工作一次。 |
| 计划/拆分第二次插入失败 | `DownloadTaskRepositoryIT.planAndSplitAreAtomicOnSecondInsertFailure` | 真实 MySQL + 注入第二次 insert 失败；plan 与父/子拆分全提交或全回滚，不留半树。 |
| SINGLE 来源响应在停机后迟到 | `DownloadTaskRecoveryIT.closeWaitsForBlockedOrdinarySingleAndDropsItsLateEnvelopeBeforeAdaptation` | 真实 MySQL + latch 来源；close 等在途请求退出，任务/批次转 INTERRUPTED/`EXECUTION_INTERRUPTED`，迟到响应零 adaptation、零证券写入。 |
| RANGE 来源响应在停机后迟到 | `DownloadTaskRecoveryIT.closeWaitsForBlockedRangeAndDropsItsLateEnvelopeBeforeAdaptation` | 真实 MySQL + RANGE 计划/latch 来源；与 SINGLE 相同地等待真实退出，迟到 envelope 在 adaptation 前丢弃，批次行数全零、证券表零行、来源恰一次。 |
| 证券写入后成功标记失败 | `BatchCommitServiceIT.successUpdateFailureRollsBackBothNewAndUpdatedSecurities` | 真实 MySQL + 提交故障；INSERT 与 UPDATE 同事务回滚，数据与成功状态不分离。 |
| 计划/拆分/数据/终态提交后 ack 失败 | `DownloadTaskRunnerIT.committedPlanSplitDataAndFinishAreNeverRetriedAfterAcknowledgementErrors` | 真实 MySQL + ack 故障；已提交事实只探测/重算，不重发来源或重复累计。 |
| native split 父节点失败 | `DownloadTaskRunnerIT.nativeSplitPersistsOnlyChildrenAndNodeFailureCreatesNoHalfTree` | 真实 MySQL + 受控 native source；父 SPLIT 与两子原子保存，父响应不写证券，失败不留半树。 |
| 旧许可迟到 | `BatchCommitServiceIT.locksCurrentTaskAfterStalePrecheckSnapshotAndCannotOverwriteNewWorker` | 真实 MySQL + 并发锁窗口；锁内重读当前 task，旧 version/generation 不能覆盖新 worker。 |
| 手动重排回执丢失 | `DownloadTaskRecoveryIT.manualRequeueRetiresAnOlderLostPermitEvenWhenTheCommitReceiptIsLost` | 真实 MySQL + retry/resume 参数化故障；旧许可退休，新代次和成功批不被覆盖。 |
| 数据库中断且不能记录失败 | `DownloadTaskRecoveryIT.uncertainCommitPausesUntilDatabaseReturnsThenRecoversCommittedSuccessWithoutReplay` | 真实 MySQL + repository 故障；不可判定期间暂停来源，恢复后探测已提交成功且不重放。 |
| 查询/claim 回执不确定 | `DownloadTaskRecoveryIT.unknownTaskQueryFailureOnlyProbesAndClaimReceiptFailureRecoversKnownCandidate` | 真实 MySQL + repository 故障；仅探测已知候选，GET 故障不把任务写成 FAILED，成功工作不重发。 |
| 同库启动恢复旧队列和混合运行事实 | `DownloadTaskRecoveryIT.restartRecoversOldQueuedAndMixedRunningFactsWithoutAnyAutomaticDownload` | 真实 MySQL + 新 runId；未完成事实转 INTERRUPTED，普通 FAILED 保留，启动和查询零来源调用。 |
| 全成功叶子但终态未写 | `DownloadTaskRecoveryIT.restartRecomputesAllSuccessfulLeavesWithoutReissuingRequests` | 真实 MySQL；从成功叶子重算 SUCCEEDED，行数/计数不重复，来源零调用。 |
| core 合法、HTTP 未知的 RANGE 参数形状 | `DownloadTaskRequestBindingTest.refusesUnknownSymbolFromToHttpShapeButBindsSupportedShape`；`DownloadTaskControllerIT.rejectsBadInputsBeforeCreatingTasksAndMapsMissingAndConflictingRequests` 中新增 body | binding 受控 descriptor 对 `symbol/from/to` 返回 `DATASET_MISCONFIGURED`，零 insert/queuedCount；HTTP 返回 400、零任务/来源；对照 `ts_code/start_date/end_date` 绑定为 `TsCodeDateRangeParameters`。 |
| 完整性、交易日、空计划和预算 | `BatchDownloadDescriptorTest`、`DateRangePlannerTest`、`TushareBatchPoliciesTest`、`TushareBatchDownloadTest`、`DownloadTaskRunnerTest/IT`、`PersistenceServiceIT` | 受控策略与真实 MySQL 组合；UNKNOWN 不成功，满额父不写、最小满额失败、空计划合法且规划失败不能误报成功。生产 34 项 RANGE 保持 `NEEDS_VERIFICATION`。 |

## 总体设计 §5.1 逐项核对

下表标为 G1 的后端方法均出现在最终 G1 的实际 XML 中且通过；参数化方法以方法基名记录，数量包含各次参数化执行。每类报告路径为上述 `TEST-<全限定类名>.xml`，不以类存在或历史报告代替本次执行。前端、包合同和浏览器分别取 G2、G3/G4、G5/G6 的各自门禁结果，不属于 G1 XML。

| §5.1 范围 | 本次执行的类 / 关键方法 | 结果与事实 |
| --- | --- | --- |
| 插件合同与日期算法 | `BatchDownloadDescriptorTest`（8）、`DateRangePlannerTest`（3）：`rejectsAmbiguousOrInvalidRangeDescriptions`、`distinguishesUnverifiedCompletenessFromAnAvailableRule`、`splitsLeapDaysAndYearBoundariesWithoutGaps` | 非法能力拒绝，闭区间二分覆盖闰年/跨年；四种 HTTP 形状另由 Binding31 / Resolver87 的 `bindsAllFourRangeShapesUsingTheirIndependentDescriptions` 和 `bindsEveryActualProductionRangeDescriptorWithExactlyOneExistingCodec` 通过。 |
| 34 项策略 / 6 项 SINGLE | `TushareBatchPoliciesTest`（109）：`exactMembershipAndCountsAreImmutable`、`everyProductionPolicyMatchesIndependentSpecificationAndStaysClosed`、`pureMappingAndProductionGateCoverEveryPolicy` | 精确 31+3 和 6；逐项股票、日期轴、规划及版本匹配；生产未开放。 |
| 上限与 UNKNOWN | 同类 `confirmedThresholdUsesRawRowsIncludingEqualityAndSingleDay`；`DownloadTaskRunnerTest`（51）的 `unknownAndUnsplittableResponsesNeverCommit`、`splitParentDoesNotAdaptOrConsumeSuccessfulRowBudget` | `<L/=L/>L` 受控输入、最小满额失败、UNKNOWN 拒绝；满额父不适配/提交或消费成功行预算。 |
| 交易日规划 | `TushareTradeCalendarTest`（4）：`sortsOnlyAfterFullCalendarCoverageAndHonorsSourceIncludingFridayClosures`、`missingDuplicateAndOpenOnlyCalendarsNeverBecomePartialPlans`；`TushareBatchDownloadTest`（17）的 `wholeClosedCalendarMakesEmptyPlanButEmptyResponseFailsAndNoResultsAreCached` | 完整日历后按来源筛交易日；周五休市不硬编码；缺日/重复/空响应失败，全休市合法空计划。 |
| 持久化状态机 | `DownloadTaskRepositoryIT`（17）：`insertAndClaimPreserveFactsAndRejectOldPermit`、`recoversQueuedCompletedPlansAndRejectsAllInvalidTerminalTargets`、`emptyPlanRecoveryAndResumePreserveSuccessfulAndOrdinaryFailedFacts`；Runner51 的 `changesBeforeAndDuringDownloadCannotCommitNewMeaning`、`invalidPlansAreRejectedBeforeSavingAnyRoot` | 非法转换/定义变化拒绝，计划失败不误成功；SPLIT 叶子分页另由 Controller12 的 `listsOnlyLeavesUnlessSplitParentsAreExplicitlyIncluded` 验证。 |
| 分批事务 | `BatchCommitServiceIT`（18）：`secondJdbcBatchFailureRollsBackFirstRowAndCallerRecordsFailureSeparately`、`successUpdateFailureRollsBackBothNewAndUpdatedSecurities`、`emptyBatchStillCommitsSuccessAndZeroCounts`；`PersistenceServiceIT`（16）的 `rejectsBothEntrypointsInsideOuterTransactionEvenForEmptyBatch`、`executesParticipantAroundWritesWithActualCountsBeforeCommit` | 证券/成功状态共同回滚或提交；空批记成功；禁止外层长事务，旧 persist 回归。 |
| 部分成功与重试 | `DownloadTaskRecoveryIT`（17）：`retryDownloadsOnlyTheFailedMiddleBatchAndPreservesSuccessfulLeaves`；Lifecycle5 的 flow 浏览器 | 仅中间失败批次再执行，首尾成功与计数不重复；真实页面/SQL 事实见生命周期段。 |
| 故障恢复 | `DownloadTaskLifecycleIT`（5）：`recreatedApplicationRecoversWithoutAutomaticSourceCalls` | 同库新 runId 恢复 QUEUED/未规划/混合状态，全成功重算；启动/GET 零来源，手动继续；未伪称硬件崩溃。 |
| 并发与迟到 | Recovery17 的 `versionRaceAcceptsExactlyOneRetryAndRejectedControlsPreserveEveryStoredFact`、`uncertainCommitPausesUntilDatabaseReturnsThenRecoversCommittedSuccessWithoutReplay`；Commit18 的 `locksCurrentTaskAfterStalePrecheckSnapshotAndCannotOverwriteNewWorker`；RunnerIT8 的 `securitiesRollbackBeforeFailureAndAnUnrecordableFailureNeedsRecovery` | 同版本只一成功，旧轮次不写，无法记录失败时停止来源；HTTP resume 并发见上表。 |
| HTTP 合同 | `DownloadTaskControllerIT`（12）：`returnsCommittedReceiptBeforeBlockedSourceFinishesAndReplaysWithoutAnotherTask`、`preservesPaginationFiltersHistoricalQueriesAndReportsDatabaseFailure`、`rejectsBadInputsBeforeCreatingTasksAndMapsMissingAndConflictingRequests`；`DownloadTaskRequestBindingTest`（31） | 202 先于来源完成、同键幂等/冲突、分页/未知参数拒绝、版本409，失败批次以GET200查询。 |
| 元数据与旧接口 | `DownloadParameterResolverTest`（87）、`StockScopedDownloadTest`（5）的 `rejectsMixedStockBatchesForAll34DefinitionsBeforePersistence`、`downloadsMainBusinessWithOnlyStockAndPersistsItsOriginalCompositeKey`；`DownloadControllerIT`（10）；`TushareProClientTest`（17）的 `sendsAllStockScopedRequestsWithEitherRequestedCodeAndKeepsSixLegacyShapes` | 新旧形状唯一，34股票约束/6非股票及旧同步成功、空响应、回滚保留；fina_mainbz 只接收当前股票形状。 |
| 多来源复用 | `DownloadTaskRunnerIT`（8）的 `t03TaskPlansThreeBatchesAndContinuesAfterTheMiddleNetworkFailure`；Lifecycle5；Binding31 的 `refusesUnknownSymbolFromToHttpShapeButBindsSupportedShape` | core 独立字段来源、HTTP 已支持形状和真实SQL/retry闭环；未知HTTP形状拒绝，生产40项不变。 |
| 迁移与打包 | `FlywaySchemaContractIT`（47）：`upgradesV7WithoutChangingChecksumsOrExistingSecurities`、`productionMigrationInventoryCreates51TablesWithoutFixture`、`migratesAndValidatesRepeatablyOnMySql846`；G3/G4 的生产4/验收3包合同；`DownloadTaskApplicationConfigurationIT.productionServletGraphStartsAfterFlywayAndCatalogWithOneSharedRunAndBudget` | V8升级/幂等、旧checksum保留；生产7迁移/51表/1044列/51主索引/48非主索引，测试8/52/1051/52/48；生产排除fixture与Lifecycle测试类型，运行JSON验证依赖实际入包。生产装配仍40项定义及34/6门禁；G6 metadata另核对数据集HTTP清单精确40项，不含任务表。 |
| 前端单测 | G2 的 `useDownloadFlow.spec.js`、`useDownloadTask.spec.js`、`DownloadTaskView.spec.js`，包含 `queries the URL identity without setup, polls at two seconds, and stops at a terminal snapshot`、`captures the original bigint version, waits for GETs, and prevents double or different operations` | 全34文件/468通过；接收不误报成功，轮询互斥/终态停止、切换丢弃迟到、查询失败保留状态和原版本控制。 |
| 浏览器交互 | G5 的 `download-tasks.spec.js` 三项 | 三项通过，覆盖接收/详情/刷新/近期列表重开、部分失败retry和中断resume；这些夹具证据不冒充后台持续执行。 |
| 真实后台闭环 | G1 Lifecycle5 启动 `download-task-lifecycle.spec.js`，flow2/resume1 | 全部实际一次执行，无跳过/重试；无页面期间calls和SQL均增长，重建和手动控制以真实应用验证。 |

接收后丢失内存通知由真实 Lifecycle 默认生产 poller 领取持久 QUEUED 任务验证；该测试应用在接收处没有手动 tick/dispatch。资源预算由 Runner51 的 `defaultNodeBudgetAllowsExactly10000RootsAndRejects10001Atomically`、`defaultNodeBudgetIncludesSplitParentsAt9998And9999`、`defaultRequestBudgetCountsPlanningRequestsAndRejects5001BeforeSending`、`respectsDefaultRangeAndSuccessfulRowsAtEqualityAndOneOver`，及 RunnerIT8 的 `realRequestAndSourceBudgetsSurviveARequeueAndNodeLimitIsAtomic` 共同覆盖边界与跨轮次累计成功来源行。

## 当前源码门禁

按设计顺序串行执行，Docker使用自有Colima/MySQL8.4.6容器，npm使用项目Node24的PATH。G1的XML按当次Maven `Running` 类清单筛选，排除未执行/陈旧包报告；后续clean之前已保存。

| 门禁 | 命令（仓库根） | 结果 | 日志/报告 |
| --- | --- | --- | --- |
| G1 | `mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；82类/1282项，failures/errors/skips=0；Lifecycle5、schema47；flow2/resume1各一次且无重试 | `/tmp/issue018-t12-final-gate1.log`、`/tmp/issue018-t12-final-gate1-reports/` |
| G2 | `npm --prefix control-plane test` | 退出0；34文件/468项全部通过 | `/tmp/issue018-t12-final-gate2.log` |
| G3 | `mvn -f data-plane/pom.xml clean verify` | 退出0；1021单测+4生产包合同，failures/errors/skips=0，包含运行依赖回归 | `/tmp/issue018-t12-final-gate3.log`、`/tmp/issue018-t12-final-gate3-reports/` |
| G4 | `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 退出0；1021单测+4生产包+3验收包合同，failures/errors/skips=0 | `/tmp/issue018-t12-final-gate4.log`、`/tmp/issue018-t12-final-gate4-reports/` |
| G5 | `npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js` | 退出0，最终3/3通过（5.2秒），无跳过 | `/tmp/issue018-t12-final-gate5-rerun.log` |
| G6 | `npm --prefix control-plane run test:e2e` | 退出0；最终7文件/126项全部通过（15.8分钟），无失败/跳过/未运行/重试，最终IntersectionObserver断言已执行 | `/tmp/issue018-t12-resume-gate6.log` |

最终G1的回执注入分支实际打印：`committed 202 discarded; client received HTTP 500 without receipt`。flow两项耗时11.23秒、resume一项3.34秒，JSON的skipped/unexpected/flaky均0。新截图 `/tmp/issue018-t12-lifecycle-desktop.png`（1440宽）与 `/tmp/issue018-t12-lifecycle-mobile.png`（390宽）已目视核对：任务状态、日期、计数及局部批次表清晰，无页面横向溢出；窄屏批次表保留局部滚动。

`sh -n scripts/verify-contracts.sh` 退出0；从当前脚本原heredoc提取运行的合成preflight退出0，结果 `{"manifestCount":40,"syntheticRejections":11}`，日志 `/tmp/issue018-t12-script-preflight.log`。完整发布脚本仍未运行，原因见边界。

发现检查（不算执行通过）：ordinary126项/7文件；task-live flow2/resume1；tushare-live40项/1文件。日志为 `/tmp/issue018-t12-{ordinary-list,flow-list,resume-list,tushare-live-list}.log`；list JSON的skipped仅表示发现未执行，实际Lifecycle JSON无跳过。helper最终19项通过，`/tmp/issue018-t12-helper.log`。

最终验收构建的生产JAR SHA-256为 `a408d3e69575d3386c4d6236eedabfc896c054d17e0db5270b65a970bd3a3a4d`，验收JAR为 `31ade90bf11c948c712de657446f12c0adb819b8b27e96373cbadde986bc2ba8`。普通套件绑定该验收哈希。manifest保持 `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`，请求示例保持 `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`，V1–V8迁移文件无本项修改。

G5有一次截图调用超时，`/tmp/issue018-t12-final-gate5.log`为2通过/1失败；trace显示业务断言结束后卡在screenshot（字体已就绪），`/tmp/issue018-t12-task-screenshot-timeout.zip`保存完整记录。未改变代码或放宽超时，原命令重新创建自有环境后最终3/3通过。该失败保留，不计入最终通过。

## 普通整套发现的运行期打包缺陷

首次G6（`/tmp/issue018-t12-gate6-ordinary.log`）为57 passed、4个beforeAll失败、65 did not run，退出1，不能作为整套通过。三套验收JAR启动时缺 `com/networknt/schema/SpecVersion$VersionFlag`：tensor-app把json-schema-validator直接声明为test，覆盖了tushare插件compile依赖，生产/验收JAR都未包含该库。app POM删除这一行scope，使显式依赖恢复compile/runtime；现有4项生产包合同中的主方法增加精确jar及缺失class断言。旧包新断言实际RED：4项中1失败，`/tmp/issue018-t12-runtime-dependency-red.log`。独立复核见 `.superpowers/sdd/2026-09-12-issue-018-t12/packaging-review.md`。

dataset-query先在净化环境的`java -version`前置失败：macOS的`/usr/bin/java`依赖JAVA_HOME，而该工具检查仅保留PATH/LANG/LC_ALL。本次临时runner将已安装JDK的bin加入PATH，使同一真实Java21二进制可在净化环境解析；没有放宽harness秘密/空库校验。失败后自建容器/秘密文件已清理、缓存错误上下文保存在 `/tmp/issue018-t12-gate6-first-failures/`；此修复后的六门禁重新按顺序执行。

第二轮G6（`/tmp/issue018-t12-final-gate6.log`）已成功启动修复后的验收包，但旧导航定位仍用精确“数据查看/数据下载”，遗漏当前可访问名称中的02/01，query与metadata等待超时。outcomes的“仅缺日期”场景还遗漏必填股票代码，页面正确聚焦第一个无效股票字段，日期焦点断言失败。将四文件导航改为包含编号的精确正则，并在该日期场景先填写合法股票；原焦点、字段错误、零POST/零来源和导航断言均保留，没有改生产代码或放宽超时。此轮在三个失败已确定后向自有Playwright发SIGINT：3 failed、1 interrupted、119 did not run、3 passed，退出130；原afterAll/finally完成自有容器和秘密清理。错误上下文保存在 `/tmp/issue018-t12-gate6-ui-failures/`。独立静态复核未发现额外具体UI/合同错配；新四库整套重跑以门禁表记录为准。

第三轮（`/tmp/issue018-t12-gate6-ui-rerun.log`）101 passed、5 failed、20 did not run，退出1。252次页面提交的初始证券造数通过（6.4分钟），随后发现：query白名单遗漏既有切换Fixture时的 `/api/v1/data-sources/fixture/datasets` 无query GET；fixture返回缓存页面时部分名称“Fixture”同时匹配来源和“Fixture 日线”，改为字符串选项精确匹配；metadata无参数场景把新增近期任务分页combobox误计入下载表单，改为在“下载配置”区域精确检查2个选择器及0个文本框。原场景、来源边界、网络错误和提交计数检查均保留。错误上下文位于 `/tmp/issue018-t12-gate6-third-failures/`。

outcomes故障触发器另因自有MySQL的 `log_bin=1 / log_bin_trust_function_creators=0` 返回1419；使用同账号/同表的受控探针确认错误要求SUPER，而应用不应取得此权限。临时runner只给自己创建的MySQL容器设置 `--log-bin-trust-function-creators=ON`，schema账号权限保持原样。聚焦 outcomes+fixture 重跑（`/tmp/issue018-t12-outcomes-fixture-rerun.log`）9 passed、1 failed、8 did not run：Fixture三项全部通过，触发器创建/任务持久化错误已可执行；旧泄漏断言误匹配任务合法params中的PERSISTENCE_FAILURE和updatedAt。将该断言对准task.lastError与batch.error，两份完整任务/批次响应仍经过凭证/canary扫描，固定错误值断言和SQL回滚检查保留。

第三轮 stock-download-parameters 的六非股票表单偶发未保留交易所选值；未据此猜测生产修复。原用例带trace连续5次复跑全部通过（37.2秒），`/tmp/issue018-t12-stock-race-probe.log`，trace保存 `/tmp/issue018-t12-stock-race-traces/`。该轮用例源码和超时未改，当时仍需最终整套；其后ENUM修正及G6结果见恢复段。所有失败轮次的runner均打印已删除自有容器和秘密文件。

第四轮（`/tmp/issue018-t12-gate6-contract-rerun.log`）106 passed、3 failed、17 did not run，退出1，耗时13.0分钟。真实metadata40项、Fixture3项及stock3项通过，但整套未通过。query完成252次初始造数后出现一次响应扫描失败；新增脱敏分类以区分响应体读取失败与凭证/canary断言，当时尚待定向结果。outcomes已通过持久化故障与SQL回滚，随后日线查询表头仍期待英文单标签；已按当前页面补14列中文标签+字段名的精确UI断言，API列名/行值合同保留。ui-redesign的trade_cal再次出现交易所未选中；保存trace `/tmp/issue018-t12-trade-cal-selection-failure.zip` 显示选项点击触发滚动后弹层从bottom-start翻到top-start，点击后aria-selected仍false，当时正在定位同步边界。未以原样重复通过代替根因修复。容器和秘密文件已清理。

暂停前的定向命令 `python3 /tmp/issue018-t12-packaged-runner.py dataset-query /tmp/issue018-t12-query-diagnostic.log`（内部执行 `npm --prefix control-plane run test:e2e -- e2e/dataset-query.spec.js`）退出1：首项600000ms超时，10项未运行。SQL只读进度确认252个任务均SUCCEEDED；首项到达monitor.assertClean，最终报 `test boundary visible text is readable`，堆栈指向assertPageSafety；未取得可判定的响应读取分类，扫描等待的具体停点仍待定位。afterAll精确合同继续报375/252不一致，disclosure_date仍是初始123/0而非更正后的1/122；这是后续更正场景未运行的事实，不能删去最终合同。脱敏证据位于 `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t03-1exYQc/evidence.json`。runner正常删除自有容器/临时秘密。此轮未运行到日线表头场景。

trade_cal失败trace经独立只读复核确认option.click内部滚动与Popper重定位发生坐标竞态。两个ENUM helper已将option.scrollIntoViewIfNeeded、完整入视口条件与click分开，并增加弹层关闭和选中文本精确断言；这是暂停时的待验证修正，用户当时要求交接后未再启动浏览器。原14列双标签表头修正在暂停时同样待验证；恢复结果见下文。暂停收尾时已核验并终止自有preview PID74785/74764；4173/8080均空闲，T12标签容器为0，临时环境映射为0。T13仍NOT_STARTED。

## 恢复执行的证据

2026-09-12 用户要求继续T12。已全文读取专属设计及pause交接，核对看板PAUSED后记录IN_PROGRESS，保留原交接为历史上下文。恢复时工作区无未暂存差异，原暂存索引已保存清单供收尾核对。生产/验收包哈希与上述最终产物一致；重新解析已有G1/G3/G4报告，分别82类/1282、64类/1025、65类/1028，失败/错误/跳过均0，G2/G5日志数量也一致。本次恢复没有产品源码修改，已有门禁不冒充重新执行结果。

重新执行 `sh -n scripts/verify-contracts.sh`、从当前脚本原heredoc提取的合成preflight、`git diff --check`及`git diff --cached --check`均退出0。preflight仍为manifestCount40/syntheticRejections11，日志 `/tmp/issue018-t12-resume-preflight.log`。manifest与请求示例哈希不变，V1–V8没有本项修改。重新目视核对已有真实详情1440/390截图，未发现布局问题。

按交接第一实施动作增加query的脱敏扫描路径/阶段诊断后，以新空库定向执行11场景。`/tmp/issue018-t12-query-resume-diagnostic.log`退出1：首项6.4分钟失败、10未运行；252次初始造数完成，模式导航前可见一个批次响应尚未扫描完，导航后扫描结清；最终精确拒绝 `API response body resource unavailable: /api/v1/download-tasks`。该轮未重现600秒超时，不能仅凭这些快照断言近期列表请求的精确开始时刻。后续更正未运行，375/252和disclosure分布的最终SQL拒绝仍保留。针对导航与读取交叠的假设，补请求开始/结束跟踪并在模式导航前等待请求及扫描结清；后续新空库定向验证结果如下；未增加超时或忽略失败。

两处ENUM helper定向命令 `npm --prefix control-plane run test:e2e -- e2e/ui-redesign.spec.js e2e/stock-download-parameters.spec.js --grep 'trade_cal|six unchanged'` 退出0，2/2通过（7.5秒），日志 `/tmp/issue018-t12-resume-enum.log`。outcomes初次恢复运行7通过/1失败/7未运行（29秒），日线双标签表头及行值已通过，上游夹具因JSON字段顺序误判params。改为精确键集及逐字段值比较后，新空库完整15/15通过（3.0分钟），包含真实120秒超时，日志 `/tmp/issue018-t12-outcomes-fixed.log`；实际14次提交、17次查询、8次受控来源调用，triggerAbsent/jvmStopped/stubStopped均true，runner删除自有容器及秘密文件。原失败 `/tmp/issue018-t12-resume-outcomes.log` 保留。

query导航修正的首轮 `/tmp/issue018-t12-query-resume-passing.log` 退出1，6通过/1失败/4未运行（10.0分钟）。初始252与更正123均通过，最终SQL和375次accepted/batch/finished日志事件一致，无响应读取/扫描错误；第7项宽表测试测量内部文字span，未计外层cell的实际裁剪，错误判定高精度文本未溢出。截图显示真实省略号，已调整为检查裁剪祖先，保留原精确数字、文字、tooltip断言；后续定向结果按各轮分别记录如下。

随后query `/tmp/issue018-t12-query-final.log` 为6通过/1失败/4未运行（10.1分钟）：新增请求/扫描探针与祖先裁剪测量通过，但高精度tooltip未出现。将自动溢出tooltip的悬停目标改为拥有该文本的单元格后，`/tmp/issue018-t12-query-final-2.log` 的精度tooltip精确内容通过；第7项随后因公司长文本“不横向溢出”失败，整轮仍6通过/1失败/4未运行（10.0分钟）。不能将这些运行称为完整query通过。组件对longText使用独立el-tooltip并允许换行，需将其触发合同与数值自动溢出tooltip区分；先做短浏览器诊断，再重跑真实查询。关于子元素mouseenter冒泡的早期解释不成立，未作为根因证据；保留实际观察的悬停目标及结果。

query短预览诊断确认自动数值tooltip应在对应单元格触发、长文本独立tooltip允许换行，精确纯文本内容保留；`/tmp/issue018-t12-query-final-3.log` 随后通过这两部分，整轮6通过/1失败/4未运行（7.0分钟），截图检查的亚像素交集0.999641478与严格ratio1不符。最终将三格可见性定义为至多1个CSS像素的几何误差，并通过IntersectionObserver保留祖先裁剪检查。更改过程中的 `/tmp/issue018-t12-query-final-4.log` 在已通过10项后被中断，退出130，runner已清理容器和秘密文件；不作完整通过证据。`/tmp/issue018-t12-query-final-5.log` 最终11/11通过（7.1分钟）：375次提交/375次受控来源、39次查询响应；8迁移/52表，daily126/company1/index1/balance1/disclosure123，公告日期分布1+122；JVM与上游均停止。其加载后细化的IntersectionObserver断言已由下述最终G6实际执行，不能倒算为该定向进程的覆盖。

为避免本地假上游沿用生产1500ms节流造成无关等待，仅query自有验收进程的argv设置 `--tensor.plugins.tushare-pro.min-request-interval=0ms`。生产默认、真实账户套件及共享节流测试未变；375次真实HTTP/事务/日志、安全及超时合同均保留。初始252造数实测由6.4分钟降至4.3分钟，后台轮询仍执行。短探针验证精度裁剪/tooltip、长文本换行/tooltip、三格空值/零值、index滚动/固定列/截图，未知API为0；这些只用于诊断，不替代真实query/G6结果。

独立整体审查记录在 `.superpowers/sdd/2026-09-12-issue-018-t12/resume-review.md`：发现一组生命周期页面断言缺口，需补重开后的三个成功叶子，以及重试前中间批的区间/固定错误/attempt1。其余非query范围未发现具体问题。该发现是验收证据缺口，不代表产品行为失败；已补逐行DOM断言，静态复核关闭，真实LifecycleIT结果如下。

恢复后的生命周期首轮 `/tmp/issue018-t12-lifecycle-dom.log` 退出1：缺少DOCKER_HOST，Testcontainers连接默认socket失败，未执行生命周期场景；其前端34文件/468测试及build已通过。随后明确使用 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` 执行 `mvn -f data-plane/pom.xml -Dtest=DownloadTaskLifecycleIT -Dsurefire.failIfNoSpecifiedTests=false test`，`/tmp/issue018-t12-lifecycle-dom-final.log` 退出0：5项，失败/错误/跳过0，前端468及生产build通过。已直接解析当前XML与两份JSON：flow恰为 `liveDisconnectReloadAndReopen`、`livePartialFailureRetriesOnlyTheMiddleBatch`，resume恰为 `liveResumeAfterRestart`，每项passed、attempt1/retry0。报告位于 `data-plane/tensor-app/target/surefire-reports/TEST-com.akkc.tensor.web.DownloadTaskLifecycleIT.xml`、`target/download-task-lifecycle-flow-f160f92e-fdf0-426c-ac4b-261f0c42c0aa.json`、`target/download-task-lifecycle-resume-79538138-84d1-49e4-b5c7-eaf42b6cab97.json`（后两路径相对tensor-app）。自有Testcontainers容器已清理，生产/验收JAR哈希保持不变。完整实施报告见 `.superpowers/sdd/2026-09-12-issue-018-t12/query-resume-report.md`。

## 恢复后的最终 G6 与清理

执行 `python3 /tmp/issue018-t12-packaged-runner.py all /tmp/issue018-t12-resume-gate6.log`，内部命令为 `npm --prefix control-plane run test:e2e`。本次MySQL8.4.6容器提供四个新空schema，绑定上述验收JAR哈希、私有0700目录/0600映射和defaults；四个真实包套件使用8080串行，三个受控UI套件使用本次preview4173。退出0，126/126通过（15.8分钟）：query11、outcomes15、metadata40、download-tasks3、fixture3、stock3、ui-redesign51。已解析逐项日志核对精确数量，没有失败、未运行、跳过或重试；最终spec哈希与本轮执行源码一致。

| 套件 | 本轮证据 | 已核对的事实 |
| --- | --- | --- |
| query | `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t03-ohZM8T/evidence.json` | 11结果均passed；375提交/375来源/39查询响应，accepted/batches/finished各375；8迁移/52表，daily126/company1/index1/balance1/disclosure123，公告日期1+122；最终三格高度523、交集522.8125，误差0.1875 CSS像素；请求/读取/安全负探针保留，JVM/upstream清理true。 |
| outcomes | `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t02-OKPjdy/evidence.json` | 14提交/17查询/8来源，accepted/batches/finished各14；15浏览器测试含真实120秒timeout、SQL回滚、日线14双标签表头和精确参数；triggerAbsent/jvmStopped/stubStopped均true。JSON的14结果记录不冒充15个浏览器测试数，测试数以本轮逐项日志为准。 |
| metadata | `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-BTIkQE/metadata-evidence.json` | cases/apiPassed/datasetsPassed各40，requiredBlocked39/parameterless1；taskPosts/synchronousDownloadPosts/recordsGets/upstreamCalls均0，截图11；前后JAR哈希一致，jvm/sentinel/privateLogScanned均true。 |
| fixture | `control-plane/node_modules/.cache/tensor-playwright/fixture-flow-fixture-page--ac687-tureOnBothPagesAfterRestart-chromium/flow-evidence.json` | 3项通过；成功行000001.SZ/2026-08-07/amount11.230000000000000000/note null，EMPTY后原行不变；两次提交，关闭fixture后两页隐藏该来源，保留未配凭证的Tushare元数据。afterAll停止自有应用，下一套可正常使用8080。 |

真实Lifecycle桌面1440/窄屏390最终截图再次目视核对通过。runner最终输出 `Browser exit=0` 与 `Owned container and temporary secrets removed`；核验本次preview PID82694身份后停止，T12标签容器0、私有环境映射0，端口4173/8080释放。共享缓存、外部库和非本次进程未清理。临时日志/报告/截图是本机可追查位置，长期结论保存在本文和看板，不能假定缓存永久存在。

## 全量验证中的失败与修复

首条全 Test/IT 命令日志 `/tmp/issue018-t12-gate1-all-tests.log` 退出 1：app 模块 580 项，5 failures、6 errors、0 skips；LifecycleIT 自身四项通过。新嵌套 LifecycleController 被其他应用测试扫描，导致它们缺 ControlledSource，测试来源的显式隔离需修复。同时 FixtureFlowIT 的首次迁移数仍写7，DividendBusinessKeyMigrationIT 的首次迁移数7和V6之后增量1均遗漏V8。将它们更新为8/2，保留业务指纹、更新/插入及失败回滚断言。FlywaySchemaContractIT 既有生产库存方法补真实 information_schema 的1044列、51主索引、48非主索引断言；方法总数仍47。双包合同补排除 LifecycleIT 及其所有嵌套类型。聚焦80项通过后，完整首轮82类/1282项通过。独立审查进一步要求精确回执丢弃与loopback流量断言，修正后最终G1再次82类/1282项通过（见上表）。

## 证据边界

- 以上 route mock 仅证明页面交互；真实后台持续执行、同库应用重建、故障恢复与最低权限，以已执行 LifecycleIT/MySQL 的实际 SQL 和调用事实为准。
- 生产保留 40 项注册、34 股票必填/6 非股票；34 项 RANGE 仍为 NEEDS_VERIFICATION，六项仅 SINGLE。测试 AVAILABLE 来源不改变生产门禁。
- 本项不执行真实 Tushare 账户验收，不更新策略开放标记；T13 与母 issue 未完成。
- 完整 `sh scripts/verify-contracts.sh` 未运行：当前 `feat/download-by-date-range` 分支及已有暂存输入不满足 main/干净源码/HEAD 发布前置。保留这些前置，静态/合成检查与完整发布结果分别记录；不自动提交、合并或发布。
- 同库上下文重建与约束内的故障快照覆盖指定故障窗口，不宣称任意硬件崩溃或操作系统 SIGKILL 测试。
