# DATA-INTEGRITY-T13 验收记录

日期：2026-09-17。工作区 `.worktrees/data-integrity`；分支 `feat/data-integrity`。依据：[任务设计](../task-designs/DATA-INTEGRITY-T13-design.md)。本记录区分本次实测、待执行与最终集成门禁。

## 当前状态

T13 **BLOCKED**（仅剩设计第6节的main集成门禁）。真实fixture/HTTP、五套浏览器回归、acceptance/production完整构建及限定复审已通过，最终独立审查已通过，新增问题0；最终 `verify-contracts.sh` 需要代码一致的clean committed main，当前混合暂存隔离区不满足。未提交、合并或推送。

## 基线与浏览器 stub

- Java21/Maven离线，既有fixture/完整性基线52项通过，0失败/错误/跳过。
- Node24.15.0，前端52文件740项单测通过；生产构建通过，保留既有大于500kB chunk提示。
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4178 npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js`：Chromium **39项通过，0失败/跳过，34.8秒**。
- stub覆盖1440/1024/390、空结果、全UNKNOWN/N/A、中断/长值、提交丢失、确认/重复提交及轮询。它不代替真实SQL或后端验收。[本次摘要与截图哈希](data-integrity-t13/stub-summary.json)。
- 首次前端基线与Maven安装前端依赖并行，出现依赖文件暂时缺失；串行重跑通过。后续Java专项设置 `-Dskip.npm=true` 仅复用已验的前端产物，不跳Java测试；完整构建仍单独执行前端生命周期。

## 测试驱动

真实HTTP/MySQL首个RED：`IntegrityFixtureFlowIT` 实际MySQL8.4.6及9次迁移，请求PROVEN_EXTRA的Jan1..21，完成报告返回UNKNOWN而预期FAIL；1失败、0错误、0跳过。未以编译或环境故障作为RED。

## 合同脚本准备

脚本中的Flyway方法名及证据统计由V8最小同步到V9：生产54表、验收55表，生产/验收迁移8/9、验收1110列、55主索引、56非主索引。脚本main、干净受保护输入和HEAD归档约束保持不变。`sh -n`与提取的原内部helper预检通过：manifest40项、11项拒绝性自检。该预检**不是最终合同门禁通过**。直接在本隔离分支执行 `M14_MAVEN_REPO=/Users/qiangzhiwei/.m2/repository sh scripts/verify-contracts.sh`，exit1、`M14-T04 failed: branch`（[最终实测摘要](data-integrity-t13/contract-gate.json)）；脚本按预期阻止未集成分支进入最终门禁。真实Flyway报告校验已通过；仍需代码一致的clean committed main运行。

## 剩余验收前提

隔离区运行验证全部通过，十项结果与十五项边界的实际证据见下文。最终独立审查已通过，新增问题0；最终合同门禁仍缺代码一致的clean committed main，不将内部helper预检计为正式通过。

## 十项结果的本次证据索引

| 结果 | 直接验证入口 | 本次状态 |
| --- | --- | --- |
| 1 原范围、接口、版本、时点 | IntegrityFixtureFlowIT；Service#freezesCompleteCapabilityAndExactMixedPlanWithoutScanning；Repository#exactValuesAndSnapshotsAreHistoricalAndDefensivelyCopied | Java/真实HTTP/本轮真实浏览器通过 |
| 2 具体缺失键与可复算数量 | PROVEN_EXTRA Jan20/Jan21；Comparison#extraCannotOffsetMissingInTwentyExpectedKeys；独立递归CTE | 真实HTTP及本轮浏览器/SQL通过 |
| 3 UNKNOWN/null，完成不代表完整 | ProductionApplicationContextIT三行情；fixture UNCONFIRMED；39项stub状态显示 | 441项Java专项及39项stub通过 |
| 4 无Token/无上游/证券不变 | 上游接收器0、全字段行/摘要前后一致；ReadRepository#actualDatabaseReadOnlyTransactionAndCleanupProtectSecurities | Java/真实HTTP/本轮真实浏览器通过 |
| 5 N/A/未支持不被删除 | Runner#persistsCompleteFixedPlanAndMissingKeysWithoutChangingSecurities；HTTP40能力；stub全N/A | 441项Java专项及39项stub通过 |
| 6 扩展仅改来源模块 | fixture版本3 FIELD规则；extension-boundary.json；通用报告展示 | core/HTTP/UI等生产代码增量0（fixture模块除外）；真实HTTP及本轮浏览器版本3扩展展示通过 |
| 7 前端完整流程及刷新 | integrity-fixture.spec.js，从创建到定位、历史、再次检查 | 本轮真实浏览器闭环通过 |
| 8 新旧版本独立保存 | 同库应用2→3，旧A JSON不变、新B有extension；旧submission重放和旧hash拒绝 | 真实HTTP及本轮浏览器/SQL通过 |
| 9 40接口日期轴与未知限制 | TushareIntegrityPoliciesTest#declaresTheIndependentDateAxisAndCompleteKeyForEveryYaml；TushareIntegrityRulesTest | 本次98项Policies与14项Rules测试通过 |
| 10 部分结果与FAIL共存 | Runner限额/Clock/规则错误；Repository原子性/中断恢复；stub中断长值 | 441项Java专项及39项stub通过 |

## 十五条边界的精确方法映射

下列15条边界均有本次通过的执行结果：Java方法可查 `integrity-tests.json` 与完整构建摘要；UI方法来自本次740项前端测试，stub来自39项专跑。表中41个具名Java方法已逐一匹配到本次XML摘要（含参数化调用），见[方法核对](data-integrity-t13/coverage-method-check.json)。`Test/IT`缩写均对应 `data-plane` 下相同文件名；fixture位于fixture模块、Tushare位于tushare模块，其余位于core模块（ProductionApplicationContextIT位于app）。

| # | 边界 | 直接方法与本次结果 |
| --- | --- | --- |
| 1 | 20/19/1/95% | IntegrityComparisonTest#extraCannotOffsetMissingInTwentyExpectedKeys；IntegrityComparisonIT#extraRowCannotOffsetMissingDayAcrossRealKeysetPages  通过；Java专项441项 |
| 2 | 可靠空、未知、局部依据 | IntegrityComparisonTest#provenEmptySetIsVerifiedAndExtraRowsRemainExtra、#unconfirmedNeverPublishesWholeRangeCountsOrPassEvenWithNoCandidateGap、#localConfirmationUpgradesCandidateInEitherArrivalOrder  通过；Java专项441项 |
| 3 | 行情周期、上市、停牌、未发布 | TushareIntegrityRulesTest#weeklyUsesThursdayWhenFridayIsClosed、#listingDateExcludesOnlyStrictlyEarlierCandidatesAndNeverProvesLifecycle、#suspensionRowsRemainTraceableWithoutRemovingCandidates、#unfinishedDailyWeekAndMonthAreExcludedWithRangeEvidence  通过；Java专项441项 |
| 4 | 财报版本、同日事件、日期轴 | IntegrityComparisonTest#fullFinancialVersionKeyPreservesAnnouncementAxisAndRelatedPeriodDate；TushareIntegrityPoliciesTest#declaresTheIndependentDateAxisAndCompleteKeyForEveryYaml  通过；Java专项441项 |
| 5 | 参考不足 | TushareIntegrityRulesTest#malformedCalendarCellsAreUnknownWhileIndependentDatesStillCompare、#expandedCalendarGapIsLocatedAsReferenceOutsideTargetRange；ProductionApplicationContextIT#startsTheSafeProductionServletGraphAndTracksOnlyDatabaseHealth  通过；Java专项441项 |
| 6 | nullable与必填 | IntegrityCoreRulesTest#requiredFieldsReportsEachMissingNonNullableFieldAndIgnoresNullableFields  通过；Java专项441项 |
| 7 | 分页/快照/并发/重启/批量问题 | IntegrityReadRepositoryIT#compositePagesAndFirstReferenceReadShareSnapshotDespiteConcurrentCommits；IntegrityCheckRepositoryIT#terminationFailureRollsBackReportsAndTaskAndRecoveryInterruptsEveryUnfinishedTask、#storesTwentyThousandIssuesWithLongRuleIdentityAndMessagesWithoutTruncation  通过；Java专项441项 |
| 8 | 旧插件/无Token/重复/停用 | core IntegrityPluginContractTest#retainsDownloadableLegacyPluginWithExplicitUnsupportedReason、#discoversLocalCapabilityWithoutCredentialsAndKeepsDownloadGate、#duplicateIdRejectsBothEntrypointsEvenIfOneReadinessFails、#disabledTakesPrecedenceOverCapabilityAndCredentials  通过；Java专项441项 |
| 9 | 日期口径及COMPLETED+FAIL/UNKNOWN/N/A | IntegrityScopePreview.spec.js；IntegritySummary.spec.js；integrity-checks.spec.js（stub）  通过；前端740项与stub39项 |
| 10 | 整只未下载股票 | FixtureIntegrityComparisonTest#absentStockHasAllProvenMissingKeysAndNoRowsForCoreRules；TushareIntegrityPoliciesTest#normalizesFormatWithoutRequiringLocalRows；IntegrityCheckServiceIT#realRepositoryKeepsRawRequestFullSnapshotAndPendingMixedUnits  通过；Java专项441项 |
| 11 | 等于限额与超一 | IntegrityCheckServiceTest#boundariesAreInclusiveAndDoNotShrinkUserScope；IntegrityReadRepositoryIT#exactlyFullAndEmptyScansSucceedButExtraRowsAndCombinedScansExhaustOneBudget；IntegrityCheckRunnerIT#exactScanBudgetAllowsTheLimitButTheNextGeneratedKeyStopsOnlyItsUnit、#issueLimitPreservesKnownFailureAndIncompleteDetails  通过；Java专项441项 |
| 12 | 空日期与relatedDates | IntegrityReadRepositoryIT#nullDatesAreSeparateAndSnapshotReadsNeverApplyHistoricalDateFilters；IntegrityComparisonTest#fullFinancialVersionKeyPreservesAnnouncementAxisAndRelatedPeriodDate；IntegrityIssuesTable.spec.js  通过；Java专项441项与前端740项 |
| 13 | 规则异常不盖FAIL，问题原子提交 | IntegrityCheckRunnerIT#ruleFailureDoesNotHideIndependentConfirmedMissingKeys；IntegrityCheckRepositoryIT#issuesAndReportRollBackOnBothInsertAndUpdateFailuresAndCannotBeOverwritten  通过；Java专项441项 |
| 14 | 幂等/冲突/规则升级 | IntegrityCheckServiceIT#concurrentIdenticalSubmissionsCreateOneCommittedPlanAndOneQueueEntry、#differentRequestsWithOneSubmissionIdHaveOneWinnerAndOneConflict；IntegrityCheckServiceTest#capabilityChangesRejectNewSubmissionsButKeepHistoricalReplay  通过；Java专项441项 |
| 15 | 通用规则扩展 | FixtureIntegrityComparisonTest#versionThreeExecutesIndependentFieldExtension；fixture IntegrityPluginContractTest#configuredVersionThreeChangesCapabilityAndRegistersTheExtension；最终diff  通过；Java专项441项及最终HTTP专跑 |
## 完整性专项（本轮首次完整运行）

Java21、MySQL8.4.6、Maven离线；命令如下，日志 `/tmp/tensor-t13-integrity-final.log`：

```sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml \
  '-Dtest=*Integrity*Test,*Integrity*IT,FixturePluginTest,FixtureFlowIT,ProductionApplicationContextIT,FlywaySchemaContractIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dskip.npm=true -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：BUILD SUCCESS，1分57秒；本次更新的28份报告共 **441项，0失败/错误/跳过**。其中9个IT类实际执行125项，均未跳过。逐类/逐方法及XML哈希见[integrity-tests.json](data-integrity-t13/integrity-tests.json)。真实Flyway的47项报告经更新后的原合同helper验证通过：[schema-report.json](data-integrity-t13/schema-report.json)。这包含Tushare40项能力/日期轴、候选规则、真实生产HTTP UNKNOWN、扫描一致快照、报告原子性、预算/重启和fixture闭环。

独立审查后的任何代码修改另记录其对应复测，不把这一轮早于修复的结果当成修复后的结果。故障注入和无凭据/停用注册测试会输出预期安全错误或警告；Flyway保留MySQL8.4高于其验证版本的兼容提示。

## 后端审查修复复测

仅 `IntegrityFixtureFlowIT.java` 补强证据：在Tushare启用且配置合成Token时连接受控loopback接收器，版本2/3上游请求计数均为0；PROVEN无行场景独立SQL枚举Jan1..20全部20个完整业务键并逐项比对API；公开计数/rate先检查JSON字符串类型；覆盖版本3的descriptor、ruleResult、evidence和issues一致。

复测命令为上述Maven命令将 `-Dtest` 缩小为 `IntegrityFixtureFlowIT`（保留 `-Dskip.npm=true` 及Docker/Mockito参数）：**1项通过，0失败/错误/跳过，BUILD SUCCESS 17.447秒，真实IT 12.96秒**。随后完整acceptance构建验证所有默认unit/package模式；最终真实IT专跑另记如下。

## Acceptance 完整构建

执行 `mvn -o -f data-plane/pom.xml -Pacceptance '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' clean verify`，使用上述Docker环境及Node24.15.0路径。**BUILD SUCCESS，56.090秒；84份Java报告1469项，0失败/错误/跳过**，同轮前端52文件740项与构建通过。默认verify模式不选择 `*IT`，所以真实MySQL结果单独以上述441项专项及修复专跑为依据，不能将1469写成含真实IT。

本次实际JAR为 `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA256 `7b4963f4719c56a7e6e2bdbd28de3971969bd2ca3ab06aa537ed080c08e72f60`。产物大小与命令见[构建摘要](data-integrity-t13/acceptance-build.json)，逐方法见[acceptance-tests.json](data-integrity-t13/acceptance-tests.json)。

后续fix2保留两种独立证据：版本2显式空上游Token完成六端点/全部fixture场景，版本3配置合成Token；同一受控接收器两阶段累计仍为0。最终专跑 **1项通过，0失败/错误/跳过，17.432秒**；[精确方法与配置摘要](data-integrity-t13/fixture-http-final.json)。限定复审已关闭全部发现。该修改只涉及测试配置，不改变上述acceptance JAR代码。

## 真实浏览器实施中的环境与既有用例修正

启动器首轮在环境准备阶段失败、0套件执行；其结果不能算浏览器通过。后续运行确认精简子进程环境下系统Java启动器无法发现实际JDK，需将已发现的JAVA_HOME/bin置于PATH前部。四套既有用例还保留Studio改版前的下载页标题/导航定位；实际页面为“下载工作台”。控制器逐项比较本次JAR的19个静态资产与dist，全部SHA相同：[资产核对](data-integrity-t13/packaged-static-assets.json)。据此仅同步测试展示定位，保留SQL、HTTP及下载业务断言，未修改生产UI。最终通过结果在后续小节单独登记。

## 真实完整性浏览器闭环（最终五套运行的首套）

`python3 scripts/verify-integrity-fixture.py --acceptance-jar "$PWD/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar"` 在自有MySQL8.4.6中串行运行。首套 `integrity-fixture` 已通过1/1，使用本页记录的同一JAR SHA。

[真实证据](data-integrity-t13/real/browser-evidence.json)保存六份独立check/submission身份、范围、能力哈希、逐次证券全字段行与SHA、SQL、三宽度截图及生命周期。初始PROVEN_EXTRA为20/20/19/1/1、rate0.950000，Jan20 MISSING与Jan21 EXTRA逐行绑定完整键；检查间唯一UPDATE把Jan21改为Jan20后，新报告20/20/20/0/0、rate1.000000，旧报告仍95%/FAIL。PROVEN另用递归SQL独立算出20个有序完整缺失键、0命中、rate0.000000；可靠空与UNKNOWN分别保留null及候选语义。

同库版本2→3后，旧依据仍@2，新报告显示coverage@3与extension@1及合成证据。六次检查的七字段证券行和摘要前后完全一致，受控上游累计0请求；v2/v3均有停机/日志安全记录，JVM、接收器与8080端口清理成功。1440/1024/390无文档溢出，键盘可进入问题；窄屏表格在自己的容器内横向滚动。

限定复审的3项Important与1项Minor全部关闭：进程组中断清理、问题行与完整键绑定、PROVEN独立SQL、v3停机记录。SIGTERM/SIGINT的独立真实进程树探针均证明父/子/孙进程消失后清理sentinel，见[安全摘要](data-integrity-t13/real/signal-probe-summary.json)；该探针使用临时假npm，不计作MySQL或浏览器测试。最终完整五套结果见下节，均通过。


## 五套真实浏览器最终回归

正式启动器运行时间：2026-09-17T10:45:07.610559Z 至 10:57:57.113808Z。共 **70项通过，0失败/跳过**；[启动器摘要](data-integrity-t13/real/launcher-summary.json)记录同一acceptance JAR SHA、工具版本和逐套退出码。

| 套件 | 通过 | 失败/跳过 | 秒 |
| --- | ---: | --- | ---: |
| integrity-fixture | 1 | 0/0 | 26.176 |
| download-outcomes | 15 | 0/0 | 161.394 |
| dataset-query | 11 | 0/0 | 415.625 |
| tushare-metadata | 40 | 0/0 | 140.744 |
| fixture-flow | 3 | 0/0 | 18.761 |

自有MySQL容器、临时凭据文件均已删除，全部JVM/浏览器进程退出，8080空闲。既有下载回归保留真实120秒超时、触发器回滚和上游错误验证。为使受限应用账号执行原有触发器故障注入，自有MySQL设置 `log_bin_trust_function_creators=ON`；仍仅授予五个自有schema的CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX/REFERENCES/TRIGGER，无全局或SUPER权限。

本轮三张真实截图均已人工复核：[1440](data-integrity-t13/real/report-1440.png)、[1024](data-integrity-t13/real/report-1024.png)、[390](data-integrity-t13/real/report-390.png)。原始范围、计算完成与数据有问题分别显示；95%和Jan20/21问题符合SQL。窄屏长表在容器内滚动，页面没有横向溢出。

## Production 完整构建

浏览器全部清理后，执行 `mvn -o -f data-plane/pom.xml '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' clean verify`，使用前述Node24.15.0与Docker环境。**BUILD SUCCESS，54.109秒；83份Java报告1466项，0失败/错误/跳过**；同轮前端52文件740项及生产构建通过。默认verify不选择真实IT，真实数据库结果单独登记。

生产JAR只含8项生产迁移，无fixture依赖、fixture数据集、V6验收迁移或acceptance配置；打包合同4项通过，额外核对嵌套Tensor模块内容。见[生产构建/包摘要](data-integrity-t13/production-build.json)及[逐方法结果](data-integrity-t13/production-tests.json)。`clean`已按预期删除前一acceptance产物；重现浏览器验收时须先重新构建acceptance，不复用未核对的旧JAR。

## 差异范围与审查

T13以实施前混合暂存树 `1728e704532dca83032c35aaf4cc576307e37ca5` 为比较基线，保留Studio/T01–T12既有改动；逐blob核对1183个非T13既有文件仍与原暂存树相同，见[基线保护核对](data-integrity-t13/baseline-preservation.json)。最终[扩展范围核对](data-integrity-t13/extension-boundary.json)确认core、HTTP/app、plugin-api、Tushare与control-plane/src均无本任务增量；产品代码只扩展fixture模块，其余为测试、启动器和文档。限定后端和浏览器审查已通过。最终独立审查亦为Spec/Quality APPROVED，Critical/Important/Minor均0，审查树为 `16949da7bacb7701082f2f562fc015416d66dd44`；审查后仅补最终证据链接/摘要与状态交接，未改产品或测试代码。完整记录见[最终审查](../../.superpowers/sdd/2026-09-17-data-integrity-t13/final-review.md)。

按设计第6节，已写[pause交接](../task-handoffs/DATA-INTEGRITY-T13-handoff.md)并将看板T13从IN_PROGRESS转为BLOCKED；唯一剩余项为用户授权后建立代码一致的clean committed main、记录集成SHA并实际通过原合同脚本。隔离区实现、运行验证、文档和审查工作均已完成；本记录不宣称T13或项目COMPLETED。
