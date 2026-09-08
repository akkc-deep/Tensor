# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`
- **Completed task:** `RANGE-T05`，已记录COMPLETED及本轮验证证据。
- **Next task:** `RANGE-T06`，按既定Order选择的后继（Order 6）。
- **Design document:** `docs/task-designs/RANGE-T06-design.md`，已完成、完整读取、评审通过并链接本项看板行。
- **Expected next status:** `READY`；写本交接时状态为NOT_STARTED，先链接本交接，再执行NOT_STARTED -> READY。READY不表示T06已启动。

## Next Task

`RANGE-T06`：适用日历获取与执行前确认。

在插件内提供必要身份解析、日历来源获取端口及完整确认逻辑。只有全部必要来源的身份、实际日期、有效范围和本轮修订均已确认，才返回不可变CalendarDecision及开盘并集；未知、缺失、空、冲突、覆盖不足或修订未确认安全拒绝。同一来源在单次确认内读取一次，下一次重新获取；非连续3／7日只消费3／7日。

验收按[详细设计](../../task-designs/RANGE-T06-design.md)：受控多市场并集、全休市与缺日／冲突区分、margin三分支、CSF／互联互通不代用普通日历、精确日期集合、同轮复用、无跨次缓存、context故障原样传播及固定来源错误通过；现有业务下载回归保持。当前没有完整运行依据的生产日历适配器，生产注册表固定为空，19项继续CALENDAR_UNCONFIRMED；不能把受控测试计为正式来源可用。不实施业务取数、规划／失败表／HTTP切换或真实调用。

## Dependencies

### RANGE-T02

- **Artifact:** `docs/research/RANGE-T02-calendar-capabilities.md`、`docs/research/RANGE-T02-calendar-evidence.json`；研究合同为`docs/task-designs/RANGE-T02-design.md`。
- **Decision:** 19项分类C-A／C-M／C-S／C-N／C-X=12／1／3／2／1；状态1 DOCUMENTED、16 UNCONFIRMED、2 CONFLICT。只有hsgt_top10静态DOCUMENTED，必要方向NORTHBOUND_SH／NORTHBOUND_SZ，且真实调用排除。moneyflow必要SSE／SZSE、stk_limit必要SSE／SZSE／BSE；margin按exchange_id只取所选市场；CSF专属业务与普通交易所等价未完成。其他未确认完整市场及hk_hold／moneyflow_hsgt未知方向不得推测。
- **Rationale:** 原页样例只能证明已出现市场，不能证明完整覆盖；日历身份／业务日期、权威安排、历史生效及特殊修订必须分别确认，不能用普通周末、单一SSE、港股日历或API空数据替代。
- **Constraint:** DOCUMENTED不证明本轮运行获取或修订已确认。逐轮获取／确认，只在同轮复用；缺日、空、冲突、身份错误、过期或新鲜度未确认均CALENDAR_UNCONFIRMED，零业务／任务写入。monthly及margin_detail冲突保留；4项日历相关真实排除及全项目ISSUE-008九项均不解除。
- **Usage:** 按T06设计固定明确必要身份和安全拒绝分支，受控来源验证确认算法。未知完整市场不使用JSON已证实部分驱动执行；生产不加载研究JSON、不注册缺运行证据的真实适配器。重试只消费实际失败日期。
- **Readiness evidence:** 看板T02已COMPLETED；19行／5类／40来源引用与摘要、状态及排除边界、margin三分支、HKEX261个工作日和monthly周末样例校验PASS。研究未调用业务API或执行功能测试；19项liveStatus均NOT_RUN。以上证据可用于实现拒绝与受控规则，不证明生产过滤已可用。

### RANGE-T04

- **Artifact:** `docs/task-designs/RANGE-T04-design.md`、`docs/verification/RANGE-T04-plugin-contracts.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`；同模块`download/CalendarScope.java`、`CalendarDecision.java`、`DownloadContext.java`、`DownloadPolicy.java`及`error/CalendarUnconfirmedException.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`和`data-plane/tensor-plugin-tushare/src/main/resources/download/tushare-pro-policies.yaml`。
- **Decision:** confirmCalendar使用ApiName、CalendarScope、DownloadContext；CalendarScope冻结公共条件及非空实际日期集合，Decision要求非空来源、每份日历精确覆盖scope日期并深复制，openDates按并集。默认日历明确拒绝；19项策略登记profile／研究状态，其他30项无日历。当前HTTP及业务download仍走原sourceParameters路径。
- **Rationale:** 共享结构校验不能证明必要身份和新鲜度；这些由T06提供器在返回Decision之前验证。服务端状态检查与客户端取消分离；本轮日历资料不进入任务表或业务计数。
- **Constraint:** 不改SPI、49份Dataset或生产策略，不提前切换HTTP／Core执行。DownloadContext异常原样传播，来源故障才转换为固定日历错误；来源fetch端口不接收context，提供器在来源异常捕获块之外检查服务端状态。不创建日历缓存、假全开盘或取消／重试开关。
- **Usage:** 在插件内部新增CalendarSource及TushareCalendarProvider，TushareProPlugin覆盖confirmCalendar并保持四参构造器／原download。所有身份／覆盖／修订验证后复用现有Decision；受控来源只放测试内，生产空注册表保留未确认。
- **Readiness evidence:** 看板T04已COMPLETED，SPI／49策略／日历类型及旧运行迁移经独立评审和规定验证通过；其报告记录521个Java＋170个前端测试、生产及acceptance JAR合同通过，数据库IT仅编译未执行。T05本轮再次验证全部模块及旧运行回归，未改上述日历合同或来源资源；不能将这些打包／单测结果写成T06生产日历通过。

两个直接输入一致：T02提供静态适用结论与缺口，T04类型表达这些边界；hsgt_top10静态DOCUMENTED而运行拒绝并不冲突。当前缺少可信生产日历适配器作为明确拒绝条件写入详细设计，不由接手人选择替代来源、TTL或伪确认默认值。

## Start Here

按顺序读取：

1. 完整读取`docs/task-designs/RANGE-T06-design.md`，再核对看板本项状态和本交接；只有收到明确启动请求后才执行READY -> IN_PROGRESS，保留本交接为入口上下文。
2. `docs/design/Tensor_区间下载_PRD_v1.0.md` §4、`docs/design/Tensor_区间下载_TRD_v1.0.md` §5.1。
3. `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`及`client/TushareProClient.java`。
4. T02报告及逐项JSON，T04设计、验证及上述共享日历合同／生产策略。

**First action:** 新建`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/calendar/TushareCalendarProviderTest.java`中的moneyflow受控两来源用例：日期为2026-09-03／07，SSE均闭、SZSE仅3日开；测试内DOCUMENTED副本和受控来源应返回仅3日开盘的精确Decision。先执行`mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am test`确认提供器缺失，再按设计实施最小来源端口和确认逻辑，继续故障／复用及SPI用例。

## Risks

- T06代码、来源端口、测试及本项验证报告尚未实施；本交接只准备后继，不能沿用T05结果作为T06完成证据。
- 当前生产来源注册表为空，19项仍不能确认运行日历。T06受控通过不消除权威获取、业务等价、历史生效和修订证据缺口，后续不能只改状态或返回true来启用。
- CalendarData的修订字段是可信适配器的运行观察，类型构造本身不证明权威；本项没有对外注入或配置开关，也不将测试源部署成真实源。
- 业务调用及数据库层首次无任务／重试原项保留的实际闭环由T12／T13／T18验证；非交易日期在未来编排中绕过日历由T08负责，不能用本提供器单测提前宣称通过。
- 无真实业务调用、数据库IT或浏览器验证；ISSUE-008继续“不依赖，未解决”。不自动实施T07。
