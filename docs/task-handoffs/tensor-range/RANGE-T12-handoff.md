# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T11`，已记录 `COMPLETED`。
- **Next task:** `RANGE-T12`，按预定义 Order 选择的后继，Order 为12。
- **Design document:** `docs/task-designs/RANGE-T12-design.md`（[完整设计](../../task-designs/RANGE-T12-design.md)），已完成、完整读取、通过独立就绪／质量评审并链接到本项看板行。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，不启动实现。

## Next Task

`RANGE-T12`：首次下载编排、执行槽位与本轮结果。

目标是在 DownloadService 串行遍历首次下载的全部冻结计划范围，来源失败保存确认后继续，返回与实际提交一致的本轮结果。范围包括初始校验、完整日历／规划、取全、T09单元处理、T11提交、T10首次创建／后续追加、共用进程槽位和S／F／N／H及R／I／U汇总。

可观察验收：

- 参数、插件、辅助日历或规划失败不新增失败记录；成功、合法空和全休市同样不建任务。31天边界保留。
- 3／7日及认证、权限、限流、超时等实际来源失败确认保存后，后续日期仍各尝试一次；同轮最多一个新任务，原始日期与公共条件冻结，不自动重试或拆小失败批次。
- 只有确认提交才累计成功／行数，只有SavedFailure才确认任务ID。存储不可用、保存未知或提交未知停止后续工作；未知与未开始范围不补建失败，前面已确认结果保留。
- 新／旧下载和T13预留重试共用一个槽位，来源／提交／保存实际结束前不释放，不读取客户端取消状态。真实HTTP断连验证留T14／T18，本项Core等待者测试不冒充网络证据。
- 设计规定的Core、六类显式MySQL、完整构建及合同验证与独立评审通过后，才可记录本项机制验收。涉及AC-PRD-RANGE-05／07／08／10／12／13／15／17／23／26／27／28；不提前升级最终功能AC。

## Dependencies

### RANGE-T08

- **Artifact:** [T08设计](../../task-designs/RANGE-T08-design.md)、[T08验证](../../verification/RANGE-T08-memory-batch-planning.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadBatchPlanner.java`、同目录 `DownloadParameterConverter.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`。
- **Decision:** planInitial先验证和冻结原条件，再完整确认日历及每批精确planBatch；Plan保留原始展示日期、skippedDates和有序PlannedBatch。每个scope是请求范围，不是已知股票单元全集。
- **Rationale:** 任一日历或计划条件未确认时不能先执行前缀；逐日降级必须来自肯定建议，预检通过仍不证明后续来源取全。
- **Constraint:** 保留五类映射、31天、完整月份、原生单批、公共参数和时间覆盖。不得异常后拆批、跨休市合并或回退旧download；全闭零业务请求。生产来源／日历表仍为空。
- **Usage:** 槽位内先获得完整Plan，并完成所有轻量Session预检，再按顺序调用fetchBatch；H取不同skippedDates数，未来未开始范围使用原REQUEST scope，N由已冻结的独立恢复能力决定是否可知。
- **Readiness evidence:** T08最终规划器21项、三模块370项、verify673／acceptance676项Java及各170项前端测试／构建通过，独立规格／质量／集成评审PASS；证明受控规划及生产拒绝边界，不证明真实来源可用。

### RANGE-T09

- **Artifact:** [T09设计](../../task-designs/RANGE-T09-design.md)、[T09验证](../../verification/RANGE-T09-recovery-units.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/RecoveryUnitProcessor.java`、同目录 `CommittedKeyIndex.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/BusinessContentCodec.java`。
- **Decision:** 真实注册GenericDatasetAdapter产生Processor；Session只能accept或sourceFailed一次，全局归属／键检查先于单元发布。validate产生私有构造的ReadyUnit或明确RejectedUnit，索引只存本轮确认提交摘要。
- **Rationale:** 前页成功、待提交或未知结果不能成为后续去重依据；局部可独立失败要保留A／C成功，缺少合法独立依据仍保持完整REQUEST。
- **Constraint:** 保留同线程串行validate→commit→confirm→累计。REQUEST／STOCK公共map必须兼容，不扩大已保存范围，不推测未知成员；空与全重复的来源行数／写入行数不同。只新增设计规定的只读usesIndependentUnits，不改split算法或开放KnownMembers。
- **Usage:** 每轮创建Processor与索引；按时间再股票合并PreparedBatch的units／failures。已知来源失败当场计F，保存确认后才继续事件；未访问UnitInput计N。只有T11 Committed后调用confirmCommitted；结果复用T09安全Failure消息。
- **Readiness evidence:** T09六类89项、verify729／acceptance732项Java及各170项前端通过，独立复审无遗留；T11随后以实际Processor票据通过单元事务验证。T09受控STOCK及提交替身不升级生产策略。

### RANGE-T10

- **Artifact:** [T10设计](../../task-designs/RANGE-T10-design.md)、[T10验证](../../verification/RANGE-T10-failure-storage.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/retry/RetryTaskStorageService.java`、同目录 `RetryTaskRepository.java`／`TaskParametersJson.java`；`data-plane/tensor-app/src/main/resources/db/migration/V8__create_download_failure_tables.sql`。
- **Decision:** 两表只保留当前明确失败；create内生成UUID、首次主子同事务，后续append同一主表。SavedFailure仅在事务正常完成后返回，公共JSON和原始日期创建后不变。
- **Rationale:** F描述已知失败，保存是否确认另行表达；预生成ID或查询到事后状态不能替代本次保存确认，原范围不能被剩余失败选择器覆盖。
- **Constraint:** create／append是独立短事务，不嵌入T11业务事务；完整selector作为明细身份，只使用现有错误白名单。TASK_RECORD_SAVE_UNCONFIRMED必须停止，不自动补记／readback／重建；T12不调用updateReason或删除明细。
- **Usage:** 首个Failure调用create，SavedFailure确认后接纳taskId；后续append检查精确返回key，记录已确认selector集合。冻结REQUEST公共map，并逐Failure用实际targetType验证相等；首次保存未知ID仍null，后续未知保留旧ID而remaining为null。
- **Readiness evidence:** T10 Core reactor289项、六类规定MySQL89项及额外App16项通过；verify745／acceptance748项Java及各170项前端通过，独立规格／质量／集成复审PASS。T11后续RetryTaskStorageIT13项再次通过；保存边界有真实MySQL证据。

### RANGE-T11

- **Artifact:** [T11设计](../../task-designs/RANGE-T11-design.md)、[T11验证](../../verification/RANGE-T11-unit-commit.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/BatchCommitService.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/db/BatchCommitServiceIT.java`；App既有 `ApplicationConfiguration.java` 中的共享事务装配。
- **Decision:** commitInitial每单元REQUIRED／60秒真实事务；Committed、RolledBack、Unavailable、Unconfirmed是不同事实。T11本身不确认索引或累计。重试业务与精确删除原子组合已供T13使用。
- **Rationale:** 正常execute返回或框架异常位置不能单独证明物理提交；已确认提交必须保留，未知不能按回滚重建失败。
- **Constraint:** 只Committed可发布R／I／U；stopExecution=true仍累计当前成功，随后INTERNAL_ERROR停止。RolledBack(false)必须在独立失败保存确认后继续；RolledBack(true)和Unavailable明确失败但存储不可用，停止且不保存；Unconfirmed不计S／F／N、不确认索引、不补失败。
- **Usage:** 首次调用仅commitInitial，严格逐结果分支；按照T12设计构建UNCONFIRMED错误快照、保留此前确认小计／ID并列出准确未开始或未知范围。不得通过事务代理或事后读库猜结果；不修改T11或既有PersistenceService。
- **Readiness evidence:** 规定四条定向命令分别22／70／16／89项通过；六类最终MySQL XML共105项零失败／错误／跳过；verify755、acceptance758项Java及各170项前端测试／构建通过，合同8组＋4变异通过。唯一R1测试清理问题已修复、完整22项重跑及范围复审PASS，最终独立集成／证据评审PASS且无遗留。两次全量构建后仅改测试清理，生产版本相同；58份资源、分支／HEAD及原暂存审计通过。

上述四个直接输入的决定及约束已逐项比较，无未解决冲突。计数、保存确认和提交确认分别由设计明确组合；依赖通过不计作T12已经实现或验收。

## Start Here

1. 完整读取 [T12专属设计](../../task-designs/RANGE-T12-design.md)，尤其Approach的四类提交结果、已知失败与N、槽位生命周期及Tests的精确命令。
2. 按看板顺序读取 [PRD §2／§5／§6](../../design/Tensor_区间下载_PRD_v1.0.md)、[TRD §2／§3.2／§6.4／§8.2／§9～§10](../../design/Tensor_区间下载_TRD_v1.0.md)，核对[OpenAPI结果／错误合同](../../contracts/openapi-v1.yaml)及[错误码](../../contracts/error-codes.md)。
3. 阅读当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java` 及对应 `DownloadServiceTest.java`，再按上述T08～T11顺序核对设计、验证和实际API；保留旧execute／HTTP直至T14。

**第一个实施动作：**按当前启动工作流核对READY后，保留分支、HEAD和原暂存，保存58份资源摘要；在 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadServiceTest.java` 保留原测试并新增公告DATE的20260901～20260910场景：3日限流、7日超时、十日各调用一次、create／append返回SavedFailure前不得进入下一日，最终8成功／2失败且原始日期冻结。执行设计首条 `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 保存先行结果，再按完成设计实施最小编排；编译缺接口与实际行为反例分别记录。

设计已规定新增文件、唯一八参构造器、两个具名入口、六类显式App IT和两次完整构建；不得把默认verify的JAR合同当作MySQL执行。沿T11 Colima测试子进程配置并取消测试进程TENSOR_TUSHARE_TOKEN覆盖，不读取凭证。新文件加入Git，保留 `feat/date-range-download`、已有暂存及并行ISSUE-017成果，不提交／发布。

## Risks

- 单进程槽位不提供跨实例或重启永久幂等；进程退出、未知提交及未开始范围没有持久恢复保证，不补执行账本或任务。
- SOURCE_REQUEST_UNCONFIRMED、初始配置及不可预期内部异常不是可补造的业务失败；明确来源失败、失败记录未知和业务提交未知必须按设计分别处理。
- 当前生产完整来源／日历注册表仍为空，49项策略仍REQUEST。受控完整批次、STOCK与MySQL证据不代表真实来源已启用；ISSUE-008九项保持“不依赖，未解决”。
- T12只证明Core生命周期与首次编排；T13精确重试、T14运行HTTP、T18实际断连和T20真实来源仍由相应任务验证。本次READY仅表示设计和交接就绪，未执行T12功能测试。
