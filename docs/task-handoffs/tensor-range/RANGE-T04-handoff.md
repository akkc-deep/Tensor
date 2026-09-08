# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`
- **Completed task:** `RANGE-T03`
- **Next task:** `RANGE-T04`，由既定Order选择的后继（Order 4）。
- **Design document:** `docs/task-designs/RANGE-T04-design.md`，已完成并链接到本项看板行。
- **Expected next status:** `READY`；本交接写入前观察为NOT_STARTED，先链接本交接，再执行NOT_STARTED -> READY。READY不表示已启动实现。

## Next Task

`RANGE-T04`：插件 SPI、下载策略与描述符投影。

目标是为下载规划、完整取数和恢复单元提供统一插件合同及49项显式策略。范围包括ApiDescriptor、DataSourcePlugin、内存选择器／批次／结果／日历合同、独立策略资源，以及Tushare、fixture和测试插件的同版迁移；保持编译和既有调用回归，后续任务再接入完整区间行为。

验收按[详细设计](../../task-designs/RANGE-T04-design.md)：策略与49项数据集一一对应，分类19／15／1／3／11；下载投影、原来源参数和queryMode分离，原附加条件保持；独立恢复与完整性不因登记而误启用，缺省日历明确拒绝；STOCK／REQUEST及DATE／MONTH／RANGE／NONE有正反验证；无旧二进制SPI适配层。设计规定的Maven测试／verify、合同检查及文档检查通过，形成本项验证报告。AC-PRD-RANGE-01／14／18／29仅登记本项合同层证据，不将功能或真实来源验收提前写成通过。

## Dependencies

### RANGE-T01

- **Artifact:** `docs/research/RANGE-T01-source-capabilities.md`、`docs/research/RANGE-T01-source-evidence.json`；当前来源参数及业务定义位于 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/`。
- **Decision:** 49目标及38＋11范围不变；40个DOCUMENTED_CANDIDATE、8个UNCONFIRMED、forecast为CONFLICT；49项暂为有整体取全前提的REQUEST。候选DATE／MONTH／RANGE／NONE与页面mode分开，未知候选在T04映射null。
- **Rationale:** 上游支持起止字段不保证当前股票／市场条件下合法、公告语义等价或完整取全；列名本身不证明逐股完整与精确重试。
- **Constraint:** 不猜limit/offset、股票集合或缺失条件，不换VIP接口，不将报告期／解禁日当公告范围。fina_mainbz与三项缺股票条件的缺口保留；ISSUE-008九项真实排除继续不依赖且不执行。REQUEST仍受Q／C／P门槛约束，STOCK_TIME须满足R。
- **Usage:** 按apiName映射请求候选、sourceDateParameter、证据状态、完整性限制文本及REQUEST恢复引用；DatasetDefinition.parameters是sourceParameters唯一来源，资源不复制一份原参数。
- **Readiness evidence:** 看板已记录T01完成；49集合／分组、49 YAML摘要、9排除、5差异、报告行／链接及JSON校验PASS，真实业务调用0。该输入可用于策略登记和拒绝规则，不提供真实可执行性证明。

### RANGE-T02

- **Artifact:** `docs/research/RANGE-T02-calendar-capabilities.md`、`docs/research/RANGE-T02-calendar-evidence.json`。
- **Decision:** 19项日历类别精确12／1／3／2／1；1项静态DOCUMENTED（hsgt_top10且真实排除）、16项UNCONFIRMED、2项CONFLICT（monthly、margin_detail）。只有全部必要日历逐日确认后才计算开盘并集；缺失不能视为休市。
- **Rationale:** SSE单一日历、周末过滤和普通港股日历都不能替代全部适用市场／业务方向；静态来源说明不保证当前日期覆盖及新鲜度。
- **Constraint:** 来源未确认时CALENDAR_UNCONFIRMED、业务调用为零；非交易日期不经过辅助日历屏障。margin的exchange_id条件、转融通有条件等价与暂停、互联互通方向和BSE缺口均不得猜测补齐。
- **Usage:** 只将profile、calendarEvidenceStatus及T02引用登记进策略；设计CalendarScope精确日期集合和CalendarDecision覆盖不变量。T04缺省confirmCalendar拒绝，T06负责提供器和证据检查。
- **Readiness evidence:** 看板已记录T02完成；19项／40来源、摘要、分类、引用、margin三分支及关键日期样例核对PASS；未调用业务API。此研究可用，运行日历尚未确认或实现。

### RANGE-T03

- **Artifact:** `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`、`docs/contracts/verify_range_contract.py`、`docs/traceability/tensor-range-requirements.md`、`docs/verification/RANGE-T03-contracts.md`。
- **Decision:** 49目标九种请求形状、38迁移／11保留；公共策略只有mode、dateSemantic、description、calendarProfile、limits；内部策略与证据不向HTTP透出。选择器两对象四时间，单日原生来源保留相等起止请求。26码的HTTP／retryable值已固定。
- **Rationale:** 参数、策略与恢复单元需要同一可验证合同，原条件和查询合同必须保持；本轮确认结果与保存事实、未知范围各有不同语义。
- **Constraint:** 不把内部DownloadPolicy整体序列化为公共DTO；sourceParameters用于T04原运行入口，HTTP投影切换必须与后续执行集成一致。保留原16码、查询／精确数值合同，新增10码的穷举映射按目录补齐。FetchResult不替代HTTP DownloadResult，不提前生成任务或计数。
- **Usage:** 用x-tensor-range-targets逐项核对策略／投影，复用公开枚举、日期和选择器约束；运行合同校验并保留29项AC尚未功能验收的状态。
- **Readiness evidence:** 看板已记录T03完成且先于本设计创建。验证报告记录八组合同检查、四项内存变异均PASS；独立核对原非下载路径／查询schema不变、16旧码元组保留、29行追踪准确；评审发现已修复，规格／质量复审PASS，diff检查退出0。交付文件已暂存；运行接口尚未迁移，未运行功能或真实API验证。

以上直接输入约束一致。T02的monthly冲突是对T01 DATE候选的附加限制，不是删除交易日期目标的要求；所有缺口均以未确认／冲突保留。本交接不要求接手人新增产品条件或补做T04设计。

## Start Here

按以下顺序读取：

1. 完整读取 `docs/task-designs/RANGE-T04-design.md`，再核对 `docs/task-handoffs/tensor-range/tensor-range-task-board.md` 本项状态和本交接；收到启动请求后才执行READY -> IN_PROGRESS。
2. `docs/design/Tensor_区间下载_TRD_v1.0.md` §4.1～§4.2、§4.4、§5及附录A，必要时对照PRD §3～§4。
3. `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`、`descriptor/ApiDescriptor.java`、`download/DownloadEnvelope.java`；Tushare的 `TushareProPlugin.java`、`TusharePluginConfiguration.java` 和 `metadata/DatasetDefinitionLoader.java`；再按设计§6读Core／Web／日志消费者。
4. 上述T01、T02报告及JSON，随后T03 OpenAPI、错误目录、追踪和验证报告。保留现有暂存文档，不提交或发布工作区。

**First action:** 在 `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/PluginApiSurfaceTest.java` 及设计指定的新 `download/DownloadContractsTest.java` 中写出新三参SPI、缺省日历拒绝、选择器和集合不变量、26码的预期断言，运行plugin-api测试确认旧实现不满足合同，再按设计§7实施共享类型与同版迁移。

## Risks

- T04代码、生产策略资源及验证报告尚未实施；本次只完成设计和后继准备，T03合同通过不表示范围下载已可运行。
- 当前HTTP绑定、元数据、DownloadService及OperationLogger都依赖旧参数形状；必须按设计统一迁移到sourceParameters，不能先向页面暴露区间参数，也不能丢掉FetchResult明确失败后继续入库。
- 49项策略登记不表示来源能力已启用；整体完整性、逐股恢复和日历证据继续由后续任务关闭。受控测试不能升级T01／T02或恢复ISSUE-008真实调用。
- Maven集成检查如因环境跳过数据库测试，报告须列明跳过；类型／打包回归不冒充数据库或实际来源验证。
