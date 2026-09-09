# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T12`，已记录 `COMPLETED`。
- **Next task:** `RANGE-T13`，按预定义 Order 选择，Order 为13。
- **Design document:** `docs/task-designs/RANGE-T13-design.md`（[完整设计](../../task-designs/RANGE-T13-design.md)），已完整读取、通过独立就绪／质量评审并链接到本项看板行。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，不启动实现。

## Next Task

`RANGE-T13`：原任务精确重试与中断边界。

新增独立Core `RetryDownloadService`，在共用槽位内重新读取原任务，只处理仍存在的失败明细。成功业务与精确删除原子确认，再次失败更新原项原因并在确认保存后继续；不创建新任务、不重建已删项、不按原始展示区间取数。App只装配服务，HTTP接入留T14。

可观察验收：

- 忙时DOWNLOAD_BUSY、原任务不存在时RETRY_TASK_NOT_FOUND，分别供T14映射409／404；槽位获取先于唯一一次读取。全部参数／Session、整轮必要日历及来源规划成功前无业务或任务写入。
- 原始1～10日、当前3／7日，只请求3／7；3成功后仅留下7，第二次只请求7，原JSON及created_at不变。同日两股精确删除，最后一项成功删除主表；REQUEST及STOCK RANGE不拆小、不合并。
- 来源级或局部明确失败更新原key后继续；保存未知、存储不可用或提交／删除未知按设计停止，保留前序确认小计和删除事实，零readback／补记／重插。
- 全闭只清理原项，不增加S/R/I/U；混合开闭的合法RANGE仍按原完整范围一次获取。remaining由初始项减确认删除项得出；最后删除未知不输出虚假存续taskId。
- 新旧首次和重试共用同一slot，持有至实际结束；等待者超时不取消。规定Core、七类显式MySQL、helpers、完整构建及独立评审通过后才记录本项机制验收，真实HTTP断连不提前记为通过。

## Dependencies

### RANGE-T05

- **Artifact:** [T05设计](../../task-designs/RANGE-T05-design.md)、[参数验证](../../verification/RANGE-T05-parameter-conversion.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadParameterConverter.java`，以及 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/SourceParameterMapper.java`。
- **Decision:** mapRetry根据保存的公共条件与精确selector重建来源请求；originalDateRange只用于展示。REQUEST保留原股票／市场，STOCK从selector补股票，MONTH保持整月，原生DATE为相等起止。
- **Rationale:** 剩余失败范围与原始下载范围不同，重试不能重新获取已成功范围或扩大对象条件。
- **Constraint:** 不调用bindInitial验证完整header，不因旧记录缺两端展示日期而误拒绝合法selector；单边／非法日期及不兼容条件明确拒绝。REQUEST／STOCK RANGE保持原范围，不新增重试项数或日期并集上限。
- **Usage:** 槽位内读取全部items后逐项mapRetry，先完成全轮参数与Session预检，再调用任何业务或任务写入。
- **Readiness evidence:** 看板T05为COMPLETED；[验证报告](../../verification/RANGE-T05-parameter-conversion.md)已确认49项投影、精确选择器、原始日期和安全拒绝；T12的114项Core及两次完整构建继续覆盖当前转换器依赖，不表示T13已通过。

### RANGE-T06

- **Artifact:** [T06设计](../../task-designs/RANGE-T06-design.md)、[日历验证](../../verification/RANGE-T06-calendar-confirmation.md)；`CalendarScope`／`CalendarDecision`及 `DataSourcePlugin.confirmCalendar` 位于 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/`；生产提供器 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/calendar/TushareCalendarProvider.java`。
- **Decision:** 仅交易日期模式，按当前所有失败selector的实际日期并集一次确认全部必要日历；publicParams仍是原header的完整map。
- **Rationale:** 首项闭市但末项日历未知时不能先删除首项；原1～10日不能替代当前3／7日的确认范围。
- **Constraint:** 未确认即零业务／零清理，不能把未知当闭市、用周末或单一市场替代；非交易模式不调用辅助日历。混合RANGE保留完整原范围，H按T13设计冻结的实际跳过日期集合计。
- **Usage:** 先完整Decision，再分类全闭／保留业务entries；同一个原Decision交T11清理，不删除必要身份或重写publicParams，每次手动执行重新确认。
- **Readiness evidence:** 看板T06为COMPLETED；[验证报告](../../verification/RANGE-T06-calendar-confirmation.md)记录完整覆盖、并集及生产拒绝。T12受控日历、全闭零事务及生产空来源拒绝已通过；不作为真实生产日历已启用的证据。

### RANGE-T10

- **Artifact:** [T10设计](../../task-designs/RANGE-T10-design.md)、[两表验证](../../verification/RANGE-T10-failure-storage.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/retry/RetryTaskStorageService.java`、`RetryTaskRepository.java`、`TaskParametersJson.java`；App的 `V8__create_download_failure_tables.sql`。
- **Decision:** find返回原Header及当前唯一明细的只读快照；updateReason锁定并更新原ItemKey，仅SavedFailure正常返回确认保存，不会重插已不存在项。
- **Rationale:** 本轮失败F与失败原因保存确认不同；任务和公共JSON沿原记录，不用新UUID或新一轮主表表示重试。
- **Constraint:** T13只读一次find并更新原原因，不调用create／append、不改task_params／created_at；保存未知停止，不额外查询推定结果，不补造缺项。原始范围不随剩余失败变化。
- **Usage:** acquire后find，持有冻结items／header；再次明确失败只传原key及白名单错误码，检查SavedFailure.key精确一致后进入下一项。
- **Readiness evidence:** 看板T10为COMPLETED；T12最终显式RetryTaskStorageIT13项通过，真实首明细／append故障及冻参由InitialDownloadServiceIT组合验证，六类总62项均零失败／错误／跳过。

### RANGE-T11

- **Artifact:** [T11设计](../../task-designs/RANGE-T11-design.md)、[事务验证](../../verification/RANGE-T11-unit-commit.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/BatchCommitService.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/db/BatchCommitServiceIT.java`。
- **Decision:** commitRetry把业务与精确删项／主表收尾放在同一短事务，commitClosedRetry仅完整确认全闭时清理。四类结果及stopExecution／storageUnavailable标志是独立事实。
- **Rationale:** 已提交业务或已删除原项不能因后续异常撤销；提交未知不能冒充回滚并重插原任务。
- **Constraint:** 只Committed确认删除及业务小计，健康明确回滚必须独立updateReason确认才继续；其他存储／提交未知停止。全闭消费采用现行OpenAPI的S=0：T11旧设计中“累计已完成单元”措辞已在T13设计明确澄清，T11代码只返回零行候选且不改计数，故无未解决输入冲突。
- **Usage:** 业务／合法空调用commitRetry，完整全闭调用commitClosedRetry并传原Decision；严格检查selector，维护私有确认删除集合，按T13结果表处理最后项未知与确认后异常，不readback。
- **Readiness evidence:** 看板T11为COMPLETED；T12最终BatchCommitServiceIT22项再次通过，InitialDownloadServiceIT实测SQL组回滚、物理提交丢回复及COMMITTED后框架异常；Core114项包含前序taskId及非零I/U保留的四类停止证据。T11完整原子删除证据仍见其报告。

### RANGE-T12

- **Artifact:** [T12设计](../../task-designs/RANGE-T12-design.md)、[首次编排验证](../../verification/RANGE-T12-initial-execution.md)；`DownloadExecutionSlot.java`、`DownloadExecutionResult.java`、`DownloadExecutionException.java`及 `DownloadService.java` 位于 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/`；App `ApplicationConfiguration.java`。
- **Decision:** 一个单例slot保护新旧首次及重试lease；不可变本轮结果和安全停止异常已经可用，S/F/N/H及R/I/U分开确认。旧DownloadService唯一八参构造器和两个四参公开方法保持。
- **Rationale:** 重试需共享正在执行事实，并沿现行响应合同表达已确认结果，不能另建并发入口或持久RUNNING状态。
- **Constraint:** 新建独立七参RetryDownloadService与一个App bean，复用既有依赖；不修改T12表面、不重新定义结果、不读客户端取消状态、不引入worker或执行上限。源码和生产能力已通过验证，不借本项扩大HTTP或来源范围。
- **Usage:** acquire原taskId，finally至实际结束；复用结果／异常固定文案及排序规则。重试使用自己的初始项／removed集合确认剩余，不把T12首次saved集合或S当删除数量。
- **Readiness evidence:** T12已记录COMPLETED。Service20／Core114／六类App IT62／helpers31项全部零失败／错误／跳过；verify775、acceptance778项Java及各170前端测试／构建通过；R1同批STOCK与R2前序确认成果保留已补齐并复审，规格／质量／最终集成PASS，58份资源保持。新文件已暂存，不提交／发布。

上述五个直接输入的决定与约束已逐项比较；全闭计数旧措辞已按现行合同明确解决，无未解决冲突。依赖通过不计为T13已实施或验收。

## Start Here

1. 完整读取 [T13专属设计](../../task-designs/RANGE-T13-design.md)，尤其§3整轮前置、§4全闭／混合RANGE、§6提交分支和§7剩余项／最后删除未知。
2. 按看板读取 [PRD §5.6～§5.8](../../design/Tensor_区间下载_PRD_v1.0.md)、[TRD §3.3／§7／§8.2／§9](../../design/Tensor_区间下载_TRD_v1.0.md)，核对[现行结果／重试合同](../../contracts/openapi-v1.yaml)及上述T05／T06／T10／T11／T12直接输入。
3. 核对当前converter、openRetry、find／updateReason、commitRetry／commitClosedRetry、slot及App共享装配；保留T12的旧Web及两个具名入口。

**第一个实施动作：**在明确启动后，保存分支／HEAD／原暂存和58份资源摘要；新建 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/RetryDownloadServiceTest.java`，先写设计§1的三组精确场景：margin原任务仅3／7日（及原RANGE4～6）、同日两股分别删除、trade_cal单日相等起止。运行 `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RetryDownloadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，区分缺接口编译与实际行为RED，再实现固定七参服务。

后续按设计Tests执行七类显式MySQL、helpers及两次完整构建，逐XML保存本轮证据；Colima仅为测试子进程配置，取消其TENSOR_TUSHARE_TOKEN覆盖，不读取凭证。新增文件加入Git，保留现有分支及全部并行内容，不提交／发布。本交接不授权自动开始T13实现。

## Risks

- 单进程slot不提供跨进程／重启永久幂等，不协调外部SQL直接编辑原任务；不存在项报错并保留事实，不重建。
- 最后项提交未知可能已删除主表，taskId及remaining不可假装确认；业务表或后验find不能替代本次提交证明。确认全闭清理不计成功单元，H也不等于删除数。
- 混合开闭RANGE保持原完整请求，不因为其中有闭市日期拆小或拒绝合法原单元；相关H、N及停止范围按已完成设计固定，不在实现时另选口径。
- 生产完整来源／日历表仍为空，49项REQUEST及ISSUE-008九项排除不变。受控MySQL／Core证明机制；T14新HTTP、T18真实中断／断连及T20真实来源继续由对应任务验证。
