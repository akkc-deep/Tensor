# ISSUE-018-T12 故障覆盖与交付合同报告

## 范围与实现

本工作补齐三个已确认缺口：HTTP 同版本并发 resume、core 可表达但 HTTP 未注册的 `symbol/from/to` RANGE 形状拒绝、以及 RANGE 许可在停机后的迟到 envelope 丢弃。测试沿用真实 MySQL repository/事务和受控来源；没有修改生产行为、打开生产 RANGE 或调用外部 Tushare 账户。

`scripts/verify-contracts.sh` 的 Flyway 合同由 43 项更新为 47 项，纳入 V8 的四个精确方法，并将生产/测试库存更新为 7/8 次迁移、51/52 张业务表、1044/1051 个物理列、51/52 个主索引和 48 个非主索引。原有 main、受保护输入干净、HEAD archive/hash、容器归属/清理及 11 个合成拒绝探针均保留。

运行手册记录实际任务属性和默认值、单实例单 worker、持久化 202、SINGLE/RANGE 语义、计数、人工 retry/resume、停机恢复、CORS `Location`、共享 Tushare 节流及 V8 REFERENCES/库存/回退边界。

## 故障证据矩阵

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

浏览器断连、同库完整应用重建、真实 Servlet 202 回执丢失和最小权限 V8 写入由独立 `DownloadTaskLifecycleIT` 所有者实现；本报告不把其尚未提供的结果冒充本工作亲自执行的证据。

## 验证结果

- `sh -n scripts/verify-contracts.sh`：退出 0。
- 从发布脚本 heredoc 原文提取并运行合成 preflight：`{"manifestCount": 40, "syntheticRejections": 11}`；临时目录 `/tmp/tensor-t12-fault-probe.zJEekz`。
- 聚焦 Maven 命令：`DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -Dtest=DownloadTaskControllerIT,DownloadTaskRequestBindingTest,DownloadTaskRecoveryIT -Dsurefire.failIfNoSpecifiedTests=false test`。
  - 首次 `/tmp/issue018-t12-faults-focused.log` 准确暴露一条测试预期错误：接受 resume 后，一个 `EXECUTION_INTERRUPTED` 批次按合同由 failed 转 pending，不能断言整个 status-count 对象不变。数据库/生产行为没有改动；测试改为断言 pending `+1`、failed `-1`，其余批次/行数计数及 requestCount 不变。
  - 最终 `/tmp/issue018-t12-faults-focused-green.log`：退出 0，`BUILD SUCCESS`。Surefire XML 为 `DownloadTaskRecoveryIT` 17、`DownloadTaskControllerIT` 12、`DownloadTaskRequestBindingTest` 31，共 60 项，failures/errors/skipped 均为 0，三个新增方法均出现在报告中。该 Maven 生命周期同时执行前端单元 468/468 和 production build，均通过。
- scoped `git diff --check`：退出 0。

完整发布脚本未运行：当前分支为 `feat/download-by-date-range`，且受保护输入存在已暂存/未暂存改动，不满足脚本的 main 与干净输入前置。没有绕过发布门禁。

## 边界与关注项

真实 MySQL 测试中的来源均为受控 fixture；停机用协调器 close/latch 和显式故障点复现，不等同于操作系统、硬件或网络设备级崩溃。生命周期与浏览器结果只引用根协调者最终提供的报告。生产 34 项 RANGE 仍为 `NEEDS_VERIFICATION`，逐项真实账户完整性验证属于 T13。

## 总体设计 §5.1 映射复核

独立只读复核确认先前四处映射问题已关闭：前导语现已将 G1 XML 与 G2、G3/G4、G5/G6 证据边界分开；`TushareProClientTest.sendsAllStockScopedRequestsWithEitherRequestedCodeAndKeepsSixLegacyShapes` 补齐 34 个股票形状与 6 个非股票旧形状的直接证据；`DownloadTaskRunnerIT.securitiesRollbackBeforeFailureAndAnUnrecordableFailureNeedsRecovery` 补齐无法记录失败时停止来源的直接证据；迁移行补入 `DownloadTaskApplicationConfigurationIT.productionServletGraphStartsAfterFlywayAndCatalogWithOneSharedRunAndBudget` 的生产装配 40 项定义、34/6 能力断言，并以 G6 中已通过的 40 项 UI 元数据矩阵核对数据集 HTTP 清单不含任务表。上述三个 Java 方法均存在于最终 G1 当次 XML，所在类分别为 17/0/0/0、8/0/0/0、6/0/0/0；G6 整体结论仍以门禁完成后的最终结果为准。

## 暂停前的 ENUM 竞态复核

只读trace复核：call@3741的option click在actionability后自行滚动根页452→167，input snapshot仍bottom-start且pointer=(394,703)，after snapshot已top-start，aria-selected=false、expanded=true。wrapper click此前已经visible/enabled/stable并滚动到位，因此只提前滚字段不足。建议把option滚动与click分开，以完整入视口条件等待重定位，再精确验证选中状态。root已在ui-redesign/stock-download-parameters两个helper实现standalone scroll、ratio1、click、expanded=false及选中文本断言；未改鼠标路径、未添加盲目重试或sleep，尚未执行修正后的浏览器。输入框本身是Element Plus只读搜索input，值断言使用实际selected-item文本。该结论不表示G6通过。
