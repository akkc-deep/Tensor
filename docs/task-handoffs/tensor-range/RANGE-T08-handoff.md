# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`。
- **Completed task:** `RANGE-T07`，已先记录 COMPLETED 及本轮验证证据。
- **Next task:** `RANGE-T08`，按预定义 Order 选择的后继（Order 8）。
- **Design document:** `docs/task-designs/RANGE-T08-design.md`，详细设计已完成、完整读取、独立评审 READY，并链接本项看板行。
- **Expected next status:** `READY`；写本交接时为 NOT_STARTED，先链接本交接，再执行 NOT_STARTED -> READY。READY 只表示实施准备完成。

## Next Task

`RANGE-T08`：五类下载的内存批次规划。

实现可独立调用的 `DownloadBatchPlanner.planInitial`，将一次合法首次输入转换为不可变计划，冻结规范原参数、原始展示区间、确认休市日期及精确 REQUEST 时间范围。先校验 31 天，再对交易日期确认完整日历并按连续开盘片段规划；公告日期保留全部自然日，月份展开完整年月，原生范围保持一批，原条件保持原 map。联合对象和时间校验必须拒绝重叠、遗漏、扩大条件及不稳定顺序。

按[专属设计](../../task-designs/RANGE-T08-design.md)新增精确批次的 `planBatch` 预检 SPI，复用 T07 同一只读来源注册表。静态 RANGE 候选不能代表取全许可；只有正常返回的 SINGLE_DATE 建议允许交易／公告范围在业务调用前逐日重建并分别预检。任何异常都中止规划且无前缀 Plan，不据错误拆批。原生单日用 DATE scope 转相等起止，但仍只接受 SOURCE_RANGE。全闭市跳过来源预检，完成覆盖和最终 context 检查后才返回零批。

验收包含 `20260131～20260302` 三个完整月、32 天零回调拒绝、交易空档不跨越、股票／市场条件不变、不可变与重复调用、服务端异常实例及精确事件顺序。11 个原条件接口按现有资源区分 9 个 NONE 候选受控正例和 hs_const／index_member 两个来源未确认拒绝；pledge_stat 无参数，不补股票。完成规定测试、资源审计、独立评审及 8 项规划层 AC 追踪。本项不接入现有 DownloadService／HTTP，不实现恢复单元、数据库、失败任务、重试规划或业务取数。

## Dependencies

### RANGE-T05

- **Artifact:** `docs/task-designs/RANGE-T05-design.md`、`docs/verification/RANGE-T05-parameter-conversion.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadParameterConverter.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ValidatedParameters.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/SourceParameterMapper.java` 及对应测试。
- **Decision:** bindInitial 先校验投影输入、真实日期、规范股票及含两端 31 天；mapInitial 以精确 selector 转换合法候选，不授予执行许可。REQUEST 保留原股票和市场，MONTH 表示完整年月，RANGE 来源的 DATE selector 产生相等起止。
- **Rationale:** 原始展示区间与实际来源范围职责不同；先过滤或展开再限长会接受非法首次输入，重新手写来源参数会丢条件或误用日期语义。
- **Constraint:** DATE 候选不接受 RANGE selector，不猜起止字段；未知来源方式及冲突为 SOURCE_REQUEST_UNCONFIRMED。初始参数错误保留 PARAM_REQUIRED／PARAM_INVALID。所有首次 scope 为 REQUEST，不以 STOCK_TIME 策略提前生成股票单元。当前 HTTP 仍消费旧 sourceParameters 过渡路径。
- **Usage:** 规划器先 bindInitial 并冻结原输入；除已确认全闭外，在候选构造前检查 DOCUMENTED_CANDIDATE 状态与非 null sourceRequestMode，再按五类规则调用 mapInitial。冻结原始范围不从月度扩展或开盘片段倒推；覆盖校验重新本地映射并逐键比较，不再次来源预检。
- **Readiness evidence:** T05 已 COMPLETED；49 项投影、31 天边界、首次／恢复精确映射及原 HTTP 回归通过，规定验证记录 593 个 Java 单测、170 个前端测试及两种打包合同，独立评审无遗留。该输入可用于候选转换，不证明来源可取全。

### RANGE-T06

- **Artifact:** `docs/task-designs/RANGE-T06-design.md`、`docs/verification/RANGE-T06-calendar-confirmation.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/CalendarScope.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/CalendarDecision.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/calendar/CalendarSource.java`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/calendar/TushareCalendarProvider.java` 及对应测试。
- **Decision:** confirmCalendar 返回所有必要身份对精确日期集合的完整不可变结论，openDates 是其并集；同轮同源可复用，下次重新确认。生产日历注册表为空，当前 19 项仍拒绝确认，静态 1／16／2 证据状态不变。
- **Rationale:** 普通交易所、市场子集、融资及互联互通日历不能互换；缺失或未确认的日历不能当作休市或空数据。返回数据的对象、日期和修订须整体可靠。
- **Constraint:** 仅交易日期使用日历；非交易类别不能误滤周末／假日。Core 不复制必要身份映射，不自行猜日历，不用停牌代替休市，不缓存跨次结论。来源故障转换为固定 CALENDAR_UNCONFIRMED，服务端异常实例保留。
- **Usage:** 将完整原参数与最多 31 个原始自然日传给一次 confirmCalendar，验证返回非 null 且 scope 完全相等；取开盘并集和休市差集。全闭仍走覆盖／最终 context，无 planBatch 或业务调用；有开盘日才构造精确连续片段。生产交易输入优先日历拒绝，与直接来源 SPI 的错误分组分开验证。
- **Readiness evidence:** T06 已 COMPLETED；必要身份、缺源零 fetch、精确范围、完整覆盖、同源复用及安全异常受控验证通过。报告记录 verify 616、acceptance 619 项 Java 检查及 170 项前端测试，独立评审通过。数据库 IT 仅编译，真实日历未调用；足以消费受控 Decision 和拒绝边界。

### RANGE-T07

- **Artifact:** `docs/task-designs/RANGE-T07-design.md`、`docs/verification/RANGE-T07-complete-batch-fetch.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadPolicy.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/FetchBatch.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareBatchSource.java`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareCompleteBatchFetcher.java`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java` 及对应测试。
- **Decision:** fetchBatch 是独立完整取数入口，默认拒绝、不回退旧 download；每批新 Session 完成全部页及全集检查后才返回。生产 sources 固定为空，8 项请求未确认加 forecast 冲突为请求拒绝，40 项候选为完整性拒绝；trade_cal BSE 额外请求拒绝。来源和服务端异常保持既定边界。
- **Rationale:** 支持参数形状、实际完整取数与独立恢复是不同前提；短页、行数一致或静态候选不能替代来源合同。规划前没有现成的精确来源建议入口，因此 T08 设计补充只读预检，不放宽 fetch 的要求。
- **Constraint:** 新 planBatch／source.plan 不调用 open、Session、client、旧 download 或 fetchBatch，也不新增第二张来源表、生产配置开关、完整性枚举或免检标记。保留 T07 fetch 检查顺序和事件次数；生产策略、49 份 Dataset 及两份策略资源不变。
- **Usage:** 添加默认拒绝的 planBatch 和 source.plan，Tushare 通过现有执行器共享请求守门及只读 sources；精确建议只用于本次候选。仅显式 SINGLE_DATE 可前置逐日，异常不降级；测试证明预检成功后 fetch 的 open／完整性未知／截断／后页失败仍独立拒绝。不切换当前 Core／Web／fixture 路径。
- **Readiness evidence:** T07 已 COMPLETED，完成记录先于本设计准备；最终模块 244 项，verify 640 单测加 4 个 JAR 合同共 644，acceptance 另加 3 个 JAR 合同共 647，两者均含 170 项前端测试及构建，全部退出 0。51 资源摘要、合同 8 组加 4 变异反例及独立复审 PASS，无遗留。生产零业务调用；真实来源、数据库 IT 和浏览器验收未执行。

三个直接输入一致：T05 冻结并精确映射，T06 在交易模式提供完整日期结论，T07 独立守住实际取全。新增只读精确建议填补取数前的规划依据，不升级生产未知能力，不改变恢复粒度或旧 HTTP 接入边界；没有未解决的依赖冲突。

## Start Here

按顺序读取：

1. 完整读取 `docs/task-designs/RANGE-T08-design.md`，核对本交接与看板本项；收到明确启动请求后执行 READY -> IN_PROGRESS，保留本交接为入口上下文。
2. `docs/design/Tensor_区间下载_PRD_v1.0.md` §3～§5.1、`docs/design/Tensor_区间下载_TRD_v1.0.md` §4.4。
3. 上述 T05 的 converter／validator／mapper 及测试，T06 的 CalendarScope／Decision／provider，T07 的 SPI／source／fetcher 和插件入口。
4. T05～T07 专属设计及验证记录；按 T08 Tests 的两阶段命令保存 51 份资源基线，保留现有分支及前项暂存内容。

**First action:** 在待新增的 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadBatchPlannerTest.java` 写入 broker_recommend 的 `20260131～20260302` 三完整月正例及 `20260131～20260303` 的 32 天零回调反例，运行 `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadBatchPlannerTest -Dsurefire.failIfNoSpecifiedTests=false test`，记录规划器缺失的先行失败，再按已完成设计实施最小骨架。后续按设计先补 planBatch 默认拒绝及 RANGE 显式逐日建议用例，再实施对应接口与算法。

## Risks

- T08 规划器、planBatch 及其测试尚未实施；本次只完成设计与交接，READY 不表示功能验证通过。
- 生产日历及完整批次均未启用。受控建议和枚举不能证明真实来源支持；未来注册来源仍须精确条件、日期含义及取全依据，实际 fetch 必须独立验证。
- 首次计划的原始展示区间不能被后续重试用作实际失败范围；本项没有 planRetry，也不保存计划、预登记失败或构造股票全集。
- T07 日志证据限定于现有 JUL／stdout／stderr 捕获渠道；本项仍不记录原参数、来源内容或凭证，不把前项测试扩大为任意未来日志后端保证。
- 最终功能 AC、数据库／浏览器及真实来源仍待后续任务验证；ISSUE-008 继续“不依赖，未解决”。
