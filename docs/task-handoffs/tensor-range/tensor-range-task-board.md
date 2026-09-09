# Tensor 区间下载 Project Task Board

## Project

- **Project ID:** `tensor-range`；任务 ID 为 `RANGE-T01`～`RANGE-T21`。
- **Goal:** 交付一次提交区间、按合法来源方式取数、保留成功恢复单元、仅保存明确失败并在原任务内手动重试的下载闭环。
- **Scope:** 38 个区间目标接口（19 个交易日期、15 个公告日期、1 个月份、3 个原生范围）与 11 个保留原条件接口；包含来源核实、合同、元数据、适用日历、批次规划、单元校验与事务、两表失败记录、HTTP、下载表单、结果及失败任务页面、验证和运行文档。沿用 31 个自然日上限、单实例同步执行及现有只读数据查看。
- **Exclusions:** 不增加单股下载新入口、股票集合选择、其他接口或数据源、定时调度、自动重试、取消／暂停、实时进度、完整历史中心、父子任务、未执行项预登记、完整中断恢复、永久幂等或查询能力扩展。其他项目和 issue 保留各自范围。
- **Completion condition:** 21 项任务均满足各自验收；PRD 的 29 项 AC 有可追踪的本轮证据，49 项合同和策略登记、38 项区间目标及具体账号真实可执行性分别报告。来源未核实不能计为真实通过，影响功能验收的缺口不能仅靠登记风险关闭。ISSUE-008 的 9 项真实调用保持“不依赖，未解决，用户后续单独处理”，不作为本项目完成前提；其目标登记及受控合同覆盖仍保留。
- **Sources:** 按顺序使用 [BRD 正文 v1.4](../../design/Tensor_区间下载_BRD_v1.0.md)、[PRD 正文 v1.4](../../design/Tensor_区间下载_PRD_v1.0.md)、[TRD 正文 v1.10](../../design/Tensor_区间下载_TRD_v1.0.md)、[官方能力调研](../../research/2026-09-08-tushare-range-batch-research.md)及当前代码。文件名中的 v1.0 不代表正文版本；调研只提供来源事实，旧“请求与事务同批”讨论不作为当前需求。
- **Authority:** 本看板只管理区间下载增量，任务身份、顺序、定义、依赖、设计、交接与状态以本看板为准；不修改 `tensor-v1` 或 `ISSUE-004` 的历史任务。

## Workflow

- **Execution:** RANGE-T18已按本轮验收及最终独立评审记录COMPLETED；之后按Order完成并链接T19专属设计及交接，执行NOT_STARTED -> READY。T19尚未实施，其浏览器验收仍待明确启动后执行。
- **Initialization:** `RANGE-T01` 为 `READY`，其他任务为 `NOT_STARTED`；全部 `Design document` 与 `Handoff` 为 `None`，首项无前驱交接。
- **Next-task selection:** 当前任务完成后，选择 Order 更大的未完成任务中 Order 最小的一项，不根据文件名或依赖形状另选后继。
- **Successor preparation:** 当前任务完成后，先完成并链接所选后继的专属设计，再写 `next-task` 交接和准备 `READY`；后继设计未完成不改变前项已完成事实。启动、暂停、恢复和完成时按链接完整读取设计和适用交接。
- **Allowed transitions:** `NOT_STARTED -> READY`、`READY -> IN_PROGRESS`、`IN_PROGRESS -> PAUSED`、`PAUSED -> IN_PROGRESS`、`READY -> BLOCKED`、`IN_PROGRESS -> BLOCKED`、`BLOCKED -> READY`、`IN_PROGRESS -> COMPLETED`。
- **Design references:** BRD／PRD／TRD 是共享来源，不冒充逐项设计；专属设计沿用 `docs/task-designs/<task-id>-design.md`，完成后回填对应单元格。各项下列 First action 是进入该任务设计时的具体起点；后继交接应改用已完成设计中的实施起点，不让接手人补设计。
- **Artifact paths:** 沿用按项目归档方式，本看板位于 `docs/task-handoffs/tensor-range/tensor-range-task-board.md`；后续交接统一为 `docs/task-handoffs/tensor-range/<task-id>-handoff.md`。任务输出的具体文件和验证命令在专属设计中确定；未创建的文件不记作已交付来源。
- **Evidence gates:** T01／T02 交付逐接口证据和启用条件，不要求用猜测填满支持项。缺少合法请求方式时不可执行；日历未确认时业务调用为零；缺少独立恢复依据时采用 REQUEST，但 REQUEST 仍须证明来源整体取全。依赖任务只消费已核实能力及明确拒绝规则；新的产品条件或范围变更须先更新确认依据。
- **Verification:** 实现任务完成前执行与改动相符的行为验证，T18／T19 汇总故障与端到端证据。后端基线命令为 `mvn -f data-plane/pom.xml verify`、`mvn -f data-plane/pom.xml -Pacceptance clean verify`；前端在 `control-plane/` 执行 `npm test`、`npm run build`，配置验收环境后执行 `npm run test:e2e`。本次拆分没有运行功能测试，也没有沿用历史通过结果作为新区间验收。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
|---:|---|---|---|---|---|---|
| 1 | RANGE-T01 | 49 项请求、完整性与恢复能力核实 | COMPLETED | None | docs/task-designs/RANGE-T01-design.md | None |
| 2 | RANGE-T02 | 19 项适用日历与来源核实 | COMPLETED | None | docs/task-designs/RANGE-T02-design.md | docs/task-handoffs/tensor-range/RANGE-T02-handoff.md |
| 3 | RANGE-T03 | HTTP 合同、错误码与需求追踪 | COMPLETED | RANGE-T01, RANGE-T02 | docs/task-designs/RANGE-T03-design.md | docs/task-handoffs/tensor-range/RANGE-T03-handoff.md |
| 4 | RANGE-T04 | 插件 SPI、下载策略与描述符投影 | COMPLETED | RANGE-T01, RANGE-T02, RANGE-T03 | docs/task-designs/RANGE-T04-design.md | docs/task-handoffs/tensor-range/RANGE-T04-handoff.md |
| 5 | RANGE-T05 | 区间参数绑定与恢复请求转换 | COMPLETED | RANGE-T04 | docs/task-designs/RANGE-T05-design.md | docs/task-handoffs/tensor-range/RANGE-T05-handoff.md |
| 6 | RANGE-T06 | 适用日历获取与执行前确认 | COMPLETED | RANGE-T02, RANGE-T04 | docs/task-designs/RANGE-T06-design.md | docs/task-handoffs/tensor-range/RANGE-T06-handoff.md |
| 7 | RANGE-T07 | Tushare 完整批次获取与来源错误 | COMPLETED | RANGE-T01, RANGE-T04, RANGE-T05 | docs/task-designs/RANGE-T07-design.md | docs/task-handoffs/tensor-range/RANGE-T07-handoff.md |
| 8 | RANGE-T08 | 五类下载的内存批次规划 | COMPLETED | RANGE-T05, RANGE-T06, RANGE-T07 | docs/task-designs/RANGE-T08-design.md | docs/task-handoffs/tensor-range/RANGE-T08-handoff.md |
| 9 | RANGE-T09 | 恢复单元划分、适配与同轮冲突 | COMPLETED | RANGE-T04, RANGE-T07 | docs/task-designs/RANGE-T09-design.md | docs/task-handoffs/tensor-range/RANGE-T09-handoff.md |
| 10 | RANGE-T10 | 两张失败记录表与存储访问 | COMPLETED | RANGE-T04, RANGE-T05 | docs/task-designs/RANGE-T10-design.md | docs/task-handoffs/tensor-range/RANGE-T10-handoff.md |
| 11 | RANGE-T11 | 恢复单元提交与失败明细原子删除 | COMPLETED | RANGE-T09, RANGE-T10 | docs/task-designs/RANGE-T11-design.md | docs/task-handoffs/tensor-range/RANGE-T11-handoff.md |
| 12 | RANGE-T12 | 首次下载编排、执行槽位与本轮结果 | COMPLETED | RANGE-T08, RANGE-T09, RANGE-T10, RANGE-T11 | docs/task-designs/RANGE-T12-design.md | docs/task-handoffs/tensor-range/RANGE-T12-handoff.md |
| 13 | RANGE-T13 | 原任务精确重试与中断边界 | COMPLETED | RANGE-T05, RANGE-T06, RANGE-T10, RANGE-T11, RANGE-T12 | docs/task-designs/RANGE-T13-design.md | docs/task-handoffs/tensor-range/RANGE-T13-handoff.md |
| 14 | RANGE-T14 | 下载与失败任务 HTTP 接口 | COMPLETED | RANGE-T03, RANGE-T04, RANGE-T05, RANGE-T12, RANGE-T13 | docs/task-designs/RANGE-T14-design.md | docs/task-handoffs/tensor-range/RANGE-T14-handoff.md |
| 15 | RANGE-T15 | 区间表单、日期说明与参数迁移 | COMPLETED | RANGE-T14 | docs/task-designs/RANGE-T15-design.md | docs/task-handoffs/tensor-range/RANGE-T15-handoff.md |
| 16 | RANGE-T16 | 下载状态、部分结果与断连提示 | COMPLETED | RANGE-T14, RANGE-T15 | docs/task-designs/RANGE-T16-design.md | docs/task-handoffs/tensor-range/RANGE-T16-handoff.md |
| 17 | RANGE-T17 | 失败任务列表、详情与手动重试页面 | COMPLETED | RANGE-T14, RANGE-T16 | docs/task-designs/RANGE-T17-design.md | docs/task-handoffs/tensor-range/RANGE-T17-handoff.md |
| 18 | RANGE-T18 | 受控来源与 MySQL 故障集成验收 | COMPLETED | RANGE-T12, RANGE-T13, RANGE-T14 | docs/task-designs/RANGE-T18-design.md | docs/task-handoffs/tensor-range/RANGE-T18-handoff.md |
| 19 | RANGE-T19 | 浏览器闭环与现有页面回归 | READY | RANGE-T15, RANGE-T16, RANGE-T17, RANGE-T18 | docs/task-designs/RANGE-T19-design.md | docs/task-handoffs/tensor-range/RANGE-T19-handoff.md |
| 20 | RANGE-T20 | 代表接口真实区间与恢复验证 | NOT_STARTED | RANGE-T01, RANGE-T02, RANGE-T18, RANGE-T19 | None | None |
| 21 | RANGE-T21 | 迁移运行说明与验收证据收尾 | NOT_STARTED | RANGE-T03, RANGE-T19, RANGE-T20 | None | None |

## Task Details

以下 AC 编号均为 PRD §9.1 的 `AC-PRD-RANGE-xx`，表示本任务承担的验收范围，不表示已通过。各 Sources 按编号顺序读取；代码路径均相对仓库根目录。

### RANGE-T01

- **Goal:** 为 49 个目标接口形成可追溯的请求及恢复能力依据，供实现选择策略。
- **Scope:** 核实日期含义、两端包含性、必填条件、全市场／单股限制、分页或合法分段、取全判据、对象时间归属及单独重试方式；保留 38＋11 范围，不改成 VIP 接口或新增股票入口。
- **Acceptance:** 49 项逐行记录官方来源、当前参数、核实结论、证据缺口及启用／拒绝条件；单独列出 5 项报告期／解禁日期差异、`fina_mainbz` 公告参数及 `express/top10_holders/top10_floatholders` 股票条件问题。区分原生日期公开说明与实际筛选验证；REQUEST 回退不能替代取全证据。AC-PRD-RANGE-01、03、14、18、19、25、29 的来源前提可追溯；需真实调用验证的事实移交 T20，ISSUE-008 的 9 项不调用。
- **Dependencies:** None。
- **Sources:** ① [PRD §3、§6、§9.2、§10及附录](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §4.4、§5.2、§13.2](../../design/Tensor_区间下载_TRD_v1.0.md)；③ [官方能力调研](../../research/2026-09-08-tushare-range-batch-research.md)；④ `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/`；⑤ [ISSUE-008](../../issues/problems/ISSUE-008-tushare-live-coverage-gap.md)。
- **First action:** 对照现有 49 份 Dataset YAML 与官方调研逐行列出证据缺口，完成本任务核实范围和证据表设计并回填设计路径。
- **State evidence:** 2026-09-08 初始化为 READY。本轮用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”；读取本项来源、完成并链接专属设计后执行 READY -> IN_PROGRESS。首项无前驱交接；本轮仅核实公开资料及本地合同，不调用业务数据 API。
- **Completion evidence:** 2026-09-08 执行 IN_PROGRESS -> COMPLETED。交付 [49 项核实结论](../../research/RANGE-T01-source-capabilities.md) 与 [逐项证据](../../research/RANGE-T01-source-evidence.json)：49 个接口、本地原参数／业务键、官方链接／摘录／摘要、候选方式、日期／取全／恢复门槛及 T20 待验证事实均已登记；5 项日期差异、fina_mainbz 公告依据缺失、3 项股票条件缺失另列，补充 forecast 同页冲突和 trade_cal 的 BSE 缺口。公开请求候选 40、合法依据未确认 8、同页冲突 1，不代表 40 项真实可执行；49 项暂按有整体取全前提的 REQUEST 登记。报告中的 Python 校验 PASS（49 集合及分组、49 YAML 摘要、9 排除、5 差异、报告行及链接），JSON 语法、git diff --check、git diff --cached --check 均退出 0；49 份既有同日官方 HTML 摘要复核一致。未调用业务 API、未运行功能测试，未关闭 ISSUE-008；AC-PRD-RANGE-01／03／14／18／19／25／29 仅来源前提可追溯，实际来源与功能验收仍由后续任务完成。

### RANGE-T02

- **Goal:** 明确 19 个交易日期接口分别应使用哪些市场／业务日历及其可靠性判据。
- **Scope:** 核实 C-A／C-M／C-S／C-N／C-X 的实际覆盖、权威来源、互联互通方向、转融通等价依据、覆盖和更新规则；不以单一 SSE、港股普通日历或周末过滤替代。
- **Acceptance:** 19 项都有来源、必要市场集合、并集规则、覆盖／新鲜度判据及核实状态；未确认项明确返回 CALENDAR_UNCONFIRMED，不能计为已具备休市过滤能力。个股停牌与市场休市区分；非交易日期接口不套用日历屏障。AC-PRD-RANGE-02、15、16、17、18 的依据可追溯；ISSUE-008 仍不安排真实调用。
- **Dependencies:** None。
- **Sources:** ① [PRD §4及附录 A.1](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §5.1、§13.2](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/trade_cal.yaml`；④ [ISSUE-008](../../issues/problems/ISSUE-008-tushare-live-coverage-gap.md)。
- **First action:** 按 PRD 附录的 19 项日历类别建立“接口—必要市场—来源—证据缺口”清单，完成本任务核实设计。
- **State evidence:** 2026-09-08 在 RANGE-T01 完成记录之后，按 Order 选择本项；观察状态 NOT_STARTED、任务依赖 None。使用 designing-task-contracts 完成并完整读取专属研究设计，固定 19 项清单、证据 JSON／报告结构、逐项核实顺序、覆盖／新鲜度／拒绝规则和校验命令，回填设计；再按 next-task 模板写入并链接交接，执行 NOT_STARTED -> READY。设计／交接链接与模板校验 PASS；本项研究产物尚未创建、研究和真实调用均未开始，READY 仅表示后继已准备。
- **Start evidence:** 2026-09-08 用户再次明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取本项设计、入口交接与规定来源，核对 READY 及交接一致后执行 READY -> IN_PROGRESS；保留原交接为入口上下文。本轮只读公开研究，不调用业务 API。
- **Completion evidence:** 2026-09-08 执行 IN_PROGRESS -> COMPLETED。交付 [19项日历结论](../../research/RANGE-T02-calendar-capabilities.md) 与 [逐项来源证据](../../research/RANGE-T02-calendar-evidence.json)：19项分类12／1／3／2／1、40来源（35可读／5不可用）；1项DOCUMENTED（hsgt_top10，仅静态依据且真实排除）、16项UNCONFIRMED、2项CONFLICT（margin_detail市场说明、monthly交易日与月末样例）。逐项保存必要集合或未知范围、margin三分支、CSF第21条有条件等价及2024暂停、互联互通方向、覆盖／更新规则与零业务请求拒绝条件。JSON及设计规定Python校验PASS；40摘要、19报告行、引用／链接、margin三分支、HKEX261工作日及monthly周末样例复核PASS；git diff --check及--cached --check退出0。未调用业务API或运行功能测试；研究完成不表示19项过滤已可用，AC-PRD-RANGE-02／15／16／17／18仅来源前提可追踪。ISSUE-008九项保持不依赖、未解决，真实调用不恢复。

### RANGE-T03

- **Goal:** 让前后端和验收使用一致的区间下载、失败任务及结果合同。
- **Scope:** 更新 OpenAPI、错误目录和增量需求追踪；明确下载请求、策略投影、S／F／N 与 R／I／U、部分完成／未知结果、失败列表／详情／execute、originalDateRange、分页及 404／409。不新增取消或永久幂等协议。
- **Acceptance:** 38 项新日期形状和 11 项原条件均有合同；旧日期／混用拒绝、未知数量表达、记录保存失败与提交未知响应可区分；列表默认 20、可选 20／50／100，原始区间与失败选择器分别定义。29 项 AC 均映射到本看板实现／验证任务及预期证据；合同校验通过，不宣称运行接口已迁移。
- **Dependencies:** RANGE-T01、RANGE-T02；消费来源能力、日历证据及明确拒绝条件。
- **Sources:** ① [PRD §5～§10](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §4、§6.4、§8～§12](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`；④ `docs/traceability/tensor-v1-requirements.md`；⑤ T01／T02 的核实结论。
- **First action:** 对照 TRD §8 与现有 OpenAPI 列出请求、响应及错误差异，在本任务设计中固定具体字段和示例。
- **State evidence:** 2026-09-08 在 RANGE-T02 完成记录之后，按 Order 选择本项，观察状态 NOT_STARTED。使用 designing-task-contracts 完成并完整读取详细设计，固定49项／9种参数形状、策略投影、恢复选择器、本轮结果与未确认范围、失败任务端点、原始区间状态、10个新增错误码、示例、29项AC责任及合同校验。先回填设计单元格，再按 next-task 模板写入并链接交接，执行 NOT_STARTED -> READY。设计模板、49项参数与当前YAML逐项匹配（38迁移／11保留）、19日历集合、29项任务映射、10码及相对链接校验PASS；交接模板、两个直接输入、设计引用和实施起点校验PASS。T01／T02输入约束一致，monthly新增冲突作为候选限制保留；未将研究登记视为来源通过。T03运行合同、校验脚本和验证报告尚未实施或执行，READY仅表示可以按设计启动。

- **Start evidence:** 2026-09-08 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取本项设计及入口交接，并按顺序核读 PRD、TRD、既有合同／历史追踪、T01／T02 来源及拒绝条件；核对 READY 与交接一致后执行 READY -> IN_PROGRESS，保留原交接作为入口上下文。本轮仅实施文档合同和离线校验，运行接口尚未迁移，不调用业务 API。

- **Completion evidence:** 2026-09-08 执行 IN_PROGRESS -> COMPLETED。更新 `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`，交付 `docs/contracts/verify_range_contract.py`、[29项增量追踪](../../traceability/tensor-range-requirements.md)及[本轮验证报告](../../verification/RANGE-T03-contracts.md)。49目标19／15／1／3／11、38迁移／11保留、19日历与9排除映射、九种参数形状、公开策略、两对象／四时间、当前失败任务三端点、分页20／50／100、原始区间四状态及26码均可校验；S／F／N／H与R／I／U区分正常、明确失败和未知，原始日期与当前失败选择器独立。Python合同八组检查及四项内存变异均PASS；独立复核所有原非下载路径／查询schema不变，16旧码元组保留、29行PRD／任务／证据映射精确一致。完整评审发现的计数／任务确认独立性、原始参数一致性及null区别已修复，两轮定向复审规格与质量均PASS、无遗留；git diff --check与--cached --check退出0，交付文件已暂存。运行接口尚未迁移，未改运行代码／数据集／数据库，未运行功能或真实业务API验证；29功能AC仍未执行，ISSUE-008保持不依赖、未解决。此完成记录先于T04设计创建及交接。

### RANGE-T04

- **Goal:** 为下载规划、完整取数和恢复单元提供统一插件合同及 49 项显式策略。
- **Scope:** 扩展 ApiDescriptor、DataSourcePlugin 及内存选择器／批次／结果合同，新增独立于 Dataset YAML 的下载策略资源；同版迁移 Tushare、fixture 和测试插件，保持可编译及既有调用回归。后续任务再接入完整区间行为。
- **Acceptance:** 策略与 49 项数据集一一对应，分类恰为 19／15／1／3／11；页面参数投影与原来源参数、queryMode 分离。独立恢复与完整性策略仅按证据启用，未支持的日历明确拒绝；STOCK／REQUEST、DATE／MONTH／RANGE／NONE 可表达；无旧二进制 SPI 适配层。AC-PRD-RANGE-01、14、18、29 的合同检查通过。
- **Dependencies:** RANGE-T01、RANGE-T02、RANGE-T03；消费证据映射、日历类别与对外合同。
- **Sources:** ① [TRD §4.1～§4.2及附录 A](../../design/Tensor_区间下载_TRD_v1.0.md)；② `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ApiDescriptor.java`；③ `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`；④ `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`；⑤ T01～T03 的直接输入。
- **First action:** 列出 SPI 调用方与描述符映射位置，在本任务设计中确定增量类型、策略资源结构及同版迁移步骤。
- **State evidence:** 2026-09-08 在T03完成记录之后按Order选中本项，观察NOT_STARTED。使用designing-task-contracts完成并完整读取专属设计，固定内部DownloadPolicy与49资源／schema、parameters与sourceParameters分离、选择器／批次／取数／日历／context合同、原调用同版迁移、错误码及测试门槛；只读迁移审计和设计规格／质量复核均PASS、无遗留阻塞。先回填设计，再按next-task模板写入并链接交接，执行NOT_STARTED -> READY。设计／交接模板、三个直接输入、相对链接、实施起点和源状态校验PASS；T01／T02缺口及T03目标合同一致。SPI代码、生产策略资源和T04验证报告尚未实施；READY仅表示后继已准备，不代表运行区间能力或真实来源已通过。

- **Start evidence:** 2026-09-08 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T04设计和入口交接，核对READY及交接一致后执行READY -> IN_PROGRESS；保留交接路径为入口上下文。沿用现有feat/date-range-download工作区及已暂存文档，按既定设计实施SPI与策略，不提交或发布。49份Dataset YAML完整定义及SHA-256已保存到临时基线，验证将区分合同、既有调用回归和来源未确认事实。


- **Completion evidence:** 2026-09-08 执行IN_PROGRESS -> COMPLETED。交付共享三参SPI、不可变策略／选择器／批次／取数／日历合同、49项独立策略YAML及严格schema／loader；同版迁移Tushare、fixture及全部生产／测试调用方，旧HTTP绑定、元数据、执行和日志统一消费sourceParameters，明确单元失败在适配／入库前拒绝。49份Dataset定义及SHA-256完整不变；独立核对T01／T02／T03逐项一致，模式19／15／1／3／11、日历类别12／1／3／2／1、请求40／8／1、日历1／16／2；49项REQUEST及完整性未确认均保留，26码HTTP／retryable匹配。局部Maven命令、全量test（521 Java＋170前端）、verify（含4生产JAR合同）、acceptance clean verify（另含3验收JAR合同）均退出0；合同8组及4变异、diff检查PASS。独立规格／质量／跨模块评审PASS，无遗留问题。详见[本轮验证报告](../../verification/RANGE-T04-plugin-contracts.md)及[合同层追踪](../../traceability/tensor-range-requirements.md#range-t04-合同层增量证据)，实现／报告新文件已暂存，无提交或发布。数据库IT仅编译未执行；无真实业务API／浏览器验收。AC-PRD-RANGE-01／14／18／29仅本项合同层通过，区间执行、来源取全／日历和最终29项功能验收仍未通过。先记录本项完成，再按Order准备后继设计及交接。

### RANGE-T05

- **Goal:** 首次区间输入与失败选择器都能按相同规则还原合法、精确的来源参数。
- **Scope:** 修改 HTTP 参数形状／绑定、日期及条件校验，统一公共条件、对象和时间转换；保存原始输入与执行范围分离，不实现下载循环。
- **Acceptance:** 31 天允许、32 天在日历／业务请求前拒绝；缺失、非法、逆序、未知字段、旧日期或混用均拒绝，股票／交易所条件保留。DATE 可按策略生成单日期或起止相同范围，三个原生接口不误发 trade_date／ann_date；MONTH 保留完整月份，NONE 不加日期。重试先从副本提取原始展示日期再组合选择器，不改写原值、不静默覆盖冲突；旧区间记录缺少两端可按明细恢复，只有一端或非法日期则拒绝。覆盖 AC-PRD-RANGE-03、04、05、06、14、19、22、25。
- **Dependencies:** RANGE-T04；消费参数投影、来源方式和对象／时间合同。
- **Sources:** ① [PRD §3、§7.1](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §4.3、§7.1.1、§7.1.3](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/`；④ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java`；⑤ T04 的策略及选择器。
- **First action:** 对照 ParameterCodec、DownloadParameters 和 ParameterValidator 列出需要迁移的形状及转换边界，完成本任务设计。
- **State evidence:** 2026-09-08 在T04完成记录之后按Order选中本项，观察NOT_STARTED，直接依赖仅T04。使用designing-task-contracts完成详细设计，固定投影绑定入口／新records、31天与严格日期／股票校验、SourceParameterMapper与DownloadParameterConverter接口、首次对象／时间包含关系、原始展示日期提取、精确恢复及错误规则、文件与验证命令。独立设计评审发现的首次STOCK换股边界已补齐，复审READY、无未解决阻塞项；Core先行用例明确使用共享受控类型、不引入Tushare依赖。完整读取设计并回填Design document后，按next-task模板写入／校验／链接交接，再执行NOT_STARTED -> READY。设计／交接模板、唯一直接依赖、来源链接、实施起点及状态路径核对PASS。T05代码、转换测试及验证报告尚未实施；本轮仅准备后继，不自动启动。

- **Start evidence:** 2026-09-08 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T05设计、入口交接及规定来源，确认READY与交接一致后执行READY -> IN_PROGRESS；保留原交接为入口上下文。沿用现有工作区及已暂存的前置成果，按设计实施参数绑定与转换，当前HTTP入口不切换，不调用来源或数据库。

- **Completion evidence:** 2026-09-08 执行IN_PROGRESS -> COMPLETED。交付SourceParameterMapper、DownloadParameterConverter、两个参数records与resolveDownload入口，31天及严格年份／股票校验；[本轮验证](../../verification/RANGE-T05-parameter-conversion.md)和[参数层追踪](../../traceability/tensor-range-requirements.md#range-t05-参数层增量证据)记录38／11及9形状逐项绑定、精确来源map、原1～10日与3／7日恢复分离、完整月份、受控两股同日、REQUEST回退及安全拒绝。先行32天／年份／长度和REQUEST回退失败均修正；API＋Core194个测试、全量593个Java＋170个前端、verify含4个生产JAR合同共597个Java、acceptance clean verify再含3个验收JAR合同共600个Java，均退出0、零失败／错误／跳过。8组合同＋4个变异检查、49份Dataset及2份策略资源SHA-256不变、暂存与未暂存差异检查均PASS。独立规格／质量／跨模块评审PASS，无遗留问题。新代码、测试与报告已暂存，无提交或发布；当前HTTP仍消费sourceParameters，数据库IT仅编译未执行，无浏览器或真实来源调用。AC-PRD-RANGE-03／04／05／06／14／19／22／25只记参数层证据，功能／来源及ISSUE-008边界保留。

### RANGE-T06

- **Goal:** 在业务下载前完整确认该轮适用日历，未知日期不被当作休市跳过。
- **Scope:** 在插件内部实现已核实来源的日历提供器、必要市场解析、范围覆盖／冲突校验及本轮复用；不跨执行缓存、不要求预先下载 trade_cal。
- **Acceptance:** 必要来源全部确认后按开盘并集保留日期；缺失、空、冲突、覆盖不足或新鲜度未确认返回 CALENDAR_UNCONFIRMED。首次不建失败任务，重试保留原项；日历服务只消费实际待处理范围，非交易日期接口不进入屏障。用受控日历覆盖 AC-PRD-RANGE-02、15、16、17、18，来源适用性仍以 T02／T20 证据为准。
- **Dependencies:** RANGE-T02、RANGE-T04；消费已核实适用关系及 confirmCalendar 合同。
- **Sources:** ① [PRD §4](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §5.1](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/`；④ T02 的来源证据和 T04 的日历合同。
- **First action:** 根据 T02 为每个已核实日历来源列出输入、覆盖检查和失败返回，在本任务设计中确定提供器及受控样例。
- **State evidence:** 2026-09-08 在T05完成记录之后按Order选择本项，观察NOT_STARTED，直接依赖为T02／T04。使用designing-task-contracts完成并完整读取详细设计，固定CalendarSource／TushareCalendarProvider接口、必要身份解析、获取前全来源检查、同源本轮复用、逐日覆盖／冲突／有效期／修订确认、来源错误及context边界、SPI接入、文件与精确受控用例。T02的1／16／2状态及来源缺口保留，生产空来源注册表固定拒绝，未伪造适用或新鲜度。独立设计评审READY；复核将fetch的context参数移除，服务端检查只在提供器来源捕获块之外进行，复审READY。先链接设计，再按next-task模板写入／校验／链接交接，最后执行NOT_STARTED -> READY。模板、来源链接、19项／5类、1／16／2状态、4项真实排除、两个直接输入及实施起点核对PASS。T06代码及测试未实施，READY只表示后继已准备，不自动启动。

- **Start evidence:** 2026-09-08 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。完整读取T06专属设计及入口交接，核对READY与交接一致，读取规定来源后执行READY -> IN_PROGRESS；保留原交接为入口上下文。本轮按既定设计实现插件内日历确认与受控验证，生产来源注册表保持为空，不调用真实业务API。

- **Completion evidence:** 2026-09-08 执行IN_PROGRESS -> COMPLETED。交付插件内CalendarSource／TushareCalendarProvider、confirmCalendar接入、16个提供器受控测试及3个SPI行为测试、[本轮验证](../../verification/RANGE-T06-calendar-confirmation.md)和五项AC的提供器层追踪。必要身份及全部来源在任何fetch前确认，实际日期精确覆盖、开盘并集、同源单轮复用／下次重取、重复冲突／生效范围／修订拒绝及来源／context异常边界均通过；原download及四参构造器保持。规定模块test通过216个Java测试；全量test通过612个Java及170个前端测试／构建；verify通过616个Java；acceptance clean verify通过619个Java，均零失败／错误／跳过。合同8组＋4变异PASS，49 Dataset＋策略YAML／schema共51文件SHA-256不变，19项／5类及1／16／2状态独立核对PASS；两轮独立评审规格、质量及集成均PASS，无遗留；空白和文档链接检查通过，交付新文件已暂存。生产注册表固定为空，19交易项仍CALENDAR_UNCONFIRMED，不能计为真实过滤可用；数据库IT仅编译、浏览器及真实来源未运行，ISSUE-008不依赖、未解决。此完成记录先于T07设计及交接创建。

### RANGE-T07

- **Goal:** 每个来源批次只有在完整取数及整体协议检查通过后才交给单元处理。
- **Scope:** Tushare 客户端／插件接入经核实的单日、月份、区间及分页方式；保留精确数值解析、安全错误分类和原始批次参数，不自行循环重试失败请求。
- **Acceptance:** 只向支持的接口发送分页参数，完整获取所有页并校验结束依据；后续页失败、游标重复、总数矛盾、已知截断或无法证明取全均不得输出部分成功数据。SOURCE_TRUNCATED 与 SOURCE_COMPLETENESS_UNCONFIRMED 可区分；包络不被最后一页参数替代，不编造逐股来源状态。覆盖 AC-PRD-RANGE-10、11、12、14、29 的来源部分；实际来源支持由 T20 独立验证。
- **Dependencies:** RANGE-T01、RANGE-T04、RANGE-T05；消费取全依据、批次合同和共用参数转换。
- **Sources:** ① [TRD §4.4、§5.2](../../design/Tensor_区间下载_TRD_v1.0.md)；② [官方能力调研 §5](../../research/2026-09-08-tushare-range-batch-research.md)；③ `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`；④ `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareProClientTest.java`；⑤ T01、T04、T05 的直接输入。
- **First action:** 将 T01 的合法请求与取全判据映射到现有客户端调用链，完成本任务分页和错误处理设计。
- **State evidence:** 2026-09-08 在T06的COMPLETED及本轮证据记录之后按Order选择本项，观察NOT_STARTED，直接输入为T01／T04／T05。使用designing-task-contracts完成并完整读取详细设计，固定fetchBatch明确完整批次入口、默认拒绝／不回退旧download、生产空来源合同注册表、来源Session及页观察、参数保护／游标和实际请求循环／总数／全集校验、三类来源错误、服务端检查顺序、精确受控样例和规定验证。独立就绪评审提出4处合同明确性问题，已补齐Observation异常码、禁止分页业务键及语法、首次context时机和全集覆盖错误码；完整复审READY。三个直接输入无未解决冲突，生产证据未升级。先回填设计单元格，再按next-task模板写入／校验／链接交接，最后执行NOT_STARTED -> READY。设计及交接模板、相对链接、直接依赖及实施起点核对PASS；T07代码及测试尚未实施，READY只表示后继已准备，不自动启动。

- **Start evidence:** 2026-09-08 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T07设计及入口交接，核对READY和直接输入约束一致后执行READY -> IN_PROGRESS，保留原交接作为入口上下文。本轮按既定设计实施完整批次机制及受控验证；生产来源注册表保持空，原HTTP入口保持，不调用真实业务API。

- **Completion evidence:** 2026-09-08 执行IN_PROGRESS -> COMPLETED。完整读取的T07设计及入口交接与本轮基线相同。交付fetchBatch默认拒绝及Tushare完整批次执行器／来源会话、固定安全错误；生产49项按8未确认＋1冲突／40候选分别Q／C拒绝，BSE条件先拒绝，来源注册表为空、零业务调用。受控分页、精确参数／数值、后页失败、游标／实际请求／总数／全集、服务端边界与安全测试通过；原Core／Web／fixture／配置入口不变。最终模块244项，最终全量verify640单测＋4JAR=644、acceptance另3JAR=647，均含170前端测试及构建，全部退出0；51资源摘要、合同8组＋4变异、Git空白检查PASS。独立评审修正后规格／质量／集成均PASS且无遗留；[验证报告](../../verification/RANGE-T07-complete-batch-fetch.md)与5项来源层AC追踪已暂存。数据库IT仅编译、浏览器及真实来源未执行；生产取全未启用，ISSUE-008不依赖且未解决。本完成记录先于T08设计及交接准备。

### RANGE-T08

- **Goal:** 将一次合法输入转换为覆盖准确、顺序稳定的内存请求计划。
- **Scope:** 实现五类下载规划、休市过滤后的连续片段、完整年月去重及对象／时间覆盖校验；复用来源转换，不持久化计划或预登记任务。
- **Acceptance:** 请求范围不重叠、不遗漏、不扩大条件；多日请求仅在来源支持且取全可证时使用，原生范围保持一批，11 个原条件保持原请求。`20260131～20260302` 在 31 天校验后展开 1／2／3 月；单日月份输入仍覆盖整月；非交易日期不滤休市。计划冻结原始输入与实际范围，失败不自动拆小或合并。覆盖 AC-PRD-RANGE-02、03、04、05、06、14、15、18。
- **Dependencies:** RANGE-T05、RANGE-T06、RANGE-T07；消费合法参数转换、日历结论和来源获取方式。
- **Sources:** ① [PRD §3～§5.1](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §4.4](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/`；④ T05～T07 的直接输入。
- **First action:** 以五类模式和跨月／休市样例列出预期 FetchBatch 序列，完成规划器设计。
- **State evidence:** 2026-09-08 在 T07 的 COMPLETED 及本轮证据记录之后按 Order 选择本项，观察 NOT_STARTED，直接输入为 T05／T06／T07。使用 designing-task-contracts 完成并完整读取专属设计，明确不可变首次 Plan、五类精确序列、31 天先验、日历并集与连续片段、完整年月、联合对象／时间覆盖及无业务取数的精确 planBatch 预检。预检复用 T07 同一只读来源表，只有显式 SINGLE_DATE 建议允许前置分日，异常不触发拆批，fetchBatch 仍独立验证取全。就绪审查已补齐全闭覆盖／最终 context、候选 null 的拒绝位置、pledge_stat 无参数及 11 原条件的 9 候选／2 未确认边界，独立复审结论 READY，无待解决材料冲突。先回填设计单元格，再按 next-task 模板写入、校验并链接交接，最后执行 NOT_STARTED -> READY；设计／交接模板、相对链接、三个直接输入及实施起点核对 PASS。生产证据未升级，T08 代码和功能验证未启动；交接首动作为三完整月及 32 天零回调测试，不要求接手人补设计。

- **Start evidence:** 2026-09-08 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取 T08 设计、入口交接及 T05～T07 设计／验证记录，按顺序核读 PRD §3～§5.1、TRD §4.4 与直接代码输入；核对 READY 及入口快照一致后执行 READY -> IN_PROGRESS，保留原交接。沿用现有分支及前项暂存内容，已保存 51 份受保护资源基线；按设计先写三完整月与 32 天零回调测试，本项不调用真实业务 API。

- **Completion evidence:** 2026-09-08 执行 IN_PROGRESS -> COMPLETED。交付独立首次 DownloadBatchPlanner、精确 planBatch／Tushare 来源预检、联合对象／时间覆盖及不可变计划；五类序列、31天先验、交易日历并集与连续开盘片段、三完整月、原生一批及11原条件9候选／2未知均有受控证据。每个最终批独立预检，仅显式 SINGLE_DATE 前置分日；异常不降级，fetchBatch 仍独立取全。最终三模块370项，全量test669项Java及170项前端，verify673、acceptance676项Java（两者均含170项前端及构建），均退出0且无失败／错误／跳过；8组合同及4个变异反例、51资源摘要、分类／证据／排除、变更范围、相对链接及两种diff检查PASS。独立规格PASS、质量APPROVED、最终集成PASS，无遗留问题；详见[本轮验证](../../verification/RANGE-T08-memory-batch-planning.md)。新代码及文档已暂存，未提交／发布，保留原分支与前项工作。生产来源及日历表仍为空，HTTP／Core旧执行入口未切换；数据库IT仅编译未执行，浏览器和真实来源未运行，ISSUE-008不依赖且未解决。AC-PRD-RANGE-02／03／04／05／06／14／15／18仅规划层受控证据，最终功能AC不提前完成。

### RANGE-T09

- **Goal:** 在可靠归属和完整性基础上隔离恢复单元，并准确处理重复与冲突。
- **Scope:** 整体包络／行宽／对象时间归属检查、STOCK_TIME 或 REQUEST 划分、按单元适配、规范内容比较、同轮已提交键的内存索引；不改业务唯一键，不保存跨次成功指纹。
- **Acceptance:** 整体归属错误在任何单元提交前拦截；可独立完整的 A／B／C 中 B 字段错误只影响 B，无依据时整 REQUEST 处理。缺少股票或日期不自动判空／失败；无法确认成员集合的来源失败使用可重建的 REQUEST，回退不含已成功单元。单元内相同记录去重，同键异值失败；与同轮已提交键冲突仅影响当前单元，摘要仅在提交确认后加入；跨次仍沿用 Upsert。覆盖 AC-PRD-RANGE-07、08、09、11、14、29。
- **Dependencies:** RANGE-T04、RANGE-T07；消费恢复策略和完整批次包络。
- **Sources:** ① [TRD §4.2、§5.2、§6](../../design/Tensor_区间下载_TRD_v1.0.md)；② `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`；③ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java`；④ T04、T07 的直接输入。
- **First action:** 对照当前整体适配入口，列出必须先全局检查和可以按单元隔离的错误，完成划分与内容比较设计。
- **State evidence:** 2026-09-08 在 T08 的 COMPLETED 及本轮验证记录之后，按 Order 选择本项，观察 NOT_STARTED、Design／Handoff 为 None，直接依赖为 T04／T07。使用 designing-task-contracts 完成并完整读取专属设计，固定取数前冻结、一次性 Session、整体包络／归属／键检查、REQUEST 与规范 STOCK_TIME 划分、已知／可观察／缺失成员、逐行及键投影适配、版本化全业务内容编码、确认提交后键摘要索引与安全错误优先级。独立就绪评审提出的子 RANGE／DATE 与同股重叠成员问题已修正：KnownMembers 与独立 STOCK failure 使用同一规范单元边界，明确拒绝码及零来源调用反例；定向复审 READY，无遗留阻塞。公开入口直接消费 RecoverySelector 与 FetchBatch，T05 转换器列为现有辅助阅读输入，直接依赖不变。先只回填 Design document，再按 next-task 模板写入、校验并链接交接，最后执行 NOT_STARTED -> READY。设计／交接模板、相对及代码路径、两个直接输入、实施起点和状态引用核对 PASS；交接首动作是 A／B／C 隔离与最后一行整体归属错误测试，不要求补设计。T09 代码、测试与验证报告尚未实施，生产证据未升级；本轮只准备后继，不自动启动。

- **Start evidence:** 2026-09-08 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取本项设计及入口交接，核对 READY 与记录一致后执行 READY -> IN_PROGRESS；保留原交接为入口上下文，沿用当前 `feat/date-range-download` 分支及前项暂存成果。按设计先写受控行为测试，再实施内存恢复机制、验证和独立评审；不提交／发布、不调用真实业务 API。

- **Completion evidence:** 2026-09-09 执行 IN_PROGRESS -> COMPLETED。交付 RecoveryUnitProcessor、BusinessContentCodec、CommittedKeyIndex 及 GenericDatasetAdapter 的逐行／键投影入口，8个源／测文件、[本轮验证报告](../../verification/RANGE-T09-recovery-units.md)及[六条机制追踪](../../traceability/tensor-range-requirements.md#range-t09-恢复单元层增量证据)。完整预检查先于单元发布，独立股票单元均可由冻结参数重建；REQUEST／保存STOCK RANGE边界、已知与未知成员、合法空、局部适配及冲突、确认提交后索引更新通过受控验证。定向六类89项通过；最终生产代码verify为725项Java单测＋4项JAR合同共729，acceptance为732，两者均含170项前端测试及构建，零失败／错误／跳过；最后仅补1.200测试数据并复跑89项，生产代码与全量构建版本一致。合同8组＋4变异、51资源及337任务外文件摘要、分支／HEAD、文档链接及差异检查PASS。首审及两轮复审的边界／覆盖问题全部修正，最终独立规格PASS、质量／集成APPROVED，终轮测试／报告差异也已关闭，无遗留。更正设计误记的FingerprintKeyCodecTest为实际GenericDatasetAdapterTest旧指纹黄金断言，覆盖不变。交付纳入Git，未提交／发布；未接入运行入口、事务、失败表或HTTP，数据库IT仅编译，未执行真实API／浏览器，生产日历与完整来源仍为空、49项REQUEST，ISSUE-008保持不依赖、未解决。此完成记录先于T10设计及交接准备。

### RANGE-T10

- **Goal:** 用两张表精确保存当前尚未解决的明确失败，供查询和重试使用。
- **Scope:** 新增 Flyway 迁移、主表／明细存储访问、首次创建／后续追加／原因更新、按完整主键定位及分页读取；不接管执行流程，业务写入与明细删除的组合事务由 T11 实现。
- **Acceptance:** 仅新增 TRD 两张失败表，保留已有迁移和 49 张业务表；迁移版本在设计时确认。首次主表及明细同事务，无半条结构；JSON 对象保存公共条件及原始起止日期且后续不覆盖，展示范围不另增列。对象＋时间联合主键区分同日两股，重复项更新原因；不保存状态、版本、计划、凭证或响应。列表按 updated_at DESC、task_id DESC 读取。MySQL 验证创建／追加回滚和 JSON、主键约束，覆盖 AC-PRD-RANGE-21、22、26 的存储部分。
- **Dependencies:** RANGE-T04、RANGE-T05；消费选择器类型和规范任务参数。
- **Sources:** ① [TRD §7.1～§7.2、§8.1、§9.3](../../design/Tensor_区间下载_TRD_v1.0.md)；② `data-plane/tensor-app/src/main/resources/db/migration/`；③ `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`；④ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/`；⑤ T04、T05 的直接输入。
- **First action:** 核对当前生产与 acceptance 迁移版本，在本任务设计中固定两表 DDL、JSON 读写和仓储操作。
- **State evidence:** 2026-09-09 在RANGE-T09完成记录之后，按Order选择本项，观察状态NOT_STARTED，直接输入为T04／T05。使用designing-task-contracts完成并完整读取专属设计，固定V8两表DDL、严格JSON及冻结参数、完整键仓储、提交确认、T11同连接事务原语、分页、UTC、精确schema／打包清单及六类显式MySQL IT命令；独立就绪评审READY，数值编码与MySQL原生JSON精度边界已补充为事务内实际回读比较和有损值回滚验证，无遗留缺口。先仅回填Design document，再写并链接next-task交接，最后执行NOT_STARTED -> READY。最终设计摘要、两个直接输入及其约束、设计／交接模板、相对链接和实施起点核对PASS。本项代码、V8和数据库测试尚未实施，READY仅表示可按完成设计启动，不表示存储、业务事务或端到端AC通过。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T10设计、入口交接及规定来源，核对READY与交接一致，执行READY -> IN_PROGRESS；保留交接为入口上下文。沿用feat/date-range-download分支和既有暂存，已保存旧迁移、49 YAML及两份策略摘要；按设计先写首次创建真实MySQL回滚用例。

- **Completion evidence:** 2026-09-09 完整复核专属设计及最终证据后执行IN_PROGRESS -> COMPLETED。交付V8两表、严格JSON及MySQL实际回读无损校验、冻结参数、完整五列key仓储、短事务保存确认、快照分页和App装配；首次／追加后半段SQL失败回滚、同日两股隔离、锁生命周期与UTC均有实际MySQL证据。详见[本轮验证](../../verification/RANGE-T10-failure-storage.md)：Core reactor 289项、六类规定MySQL IT共89项、三类追加App IT共16项均通过且零失败／错误／跳过；verify为745项Java＋170项前端，acceptance clean verify为748项Java＋170项前端，构建均退出0；合同8组＋4个变异反例通过。独立规格／质量／集成复审PASS，非规范UUID与非法输入异常问题已关闭。57份受保护资源摘要一致，分支和HEAD不变，720份任务外原暂存基线内容保留；并发ISSUE-017文档保留且未纳入本项暂存。新增代码／测试／验证文件已显式加入Git，git diff --check及git diff --cached --check退出0；不提交／发布。仅确认AC-PRD-RANGE-21／22／26存储部分，不升级业务组合事务、HTTP、完整区间或真实来源验收。先保留本完成记录，再按Order准备T11设计与交接；最终文档链接和状态复核记入T11准备证据。

### RANGE-T11

- **Goal:** 每个恢复单元原子入库，重试成功时业务数据与失败记录同步提交。
- **Scope:** 实现恢复单元事务服务，复用 PersistenceService、数据集锁与 JDBC 批量 Upsert；组合业务写入、对应明细删除、最后一项主表删除或更新时间更新。
- **Acceptance:** 不用跨多个单元的外层事务，HTTP 获取不持有数据库锁；重试先锁任务再取数据集锁，使用同一事务管理器及连接。任一 SQL 分组或明细删除失败使当前单元全回滚，已提交单元保留；合法空／完整确认休市可删失败项但不删旧业务数据。提交确认后才更新摘要和计数，提交未知不当作明确回滚。MySQL 覆盖 AC-PRD-RANGE-07、13、15、21、24、26。
- **Dependencies:** RANGE-T09、RANGE-T10；消费校验后的单元及失败记录仓储。
- **Sources:** ① [TRD §6.2～§6.4、§7.3、§9](../../design/Tensor_区间下载_TRD_v1.0.md)；② `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java`；③ `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`；④ T09／T10 的直接输入。
- **First action:** 核对现有事务传播及锁释放回调，完成业务写入与任务删除同事务的服务设计和回滚用例。
- **State evidence:** 2026-09-09 在T10完成记录之后，按Order选择本项并观察NOT_STARTED，直接依赖仍为T09／T10。使用designing-task-contracts完成并完整读取专属设计，固定一个BatchCommitService、最小索引预检、App装配、同事务／同连接及任务先锁顺序、完成观察与未知边界、空／全重复／整轮日历全闭消费规则及六类显式MySQL验证。独立规格及实施就绪评审READY／PASS；整轮日历屏障与两处测试措辞已修正，无遗留发现。最终设计SHA-256为c9dc04c5443b804b52802b05d93ecdb8e9fc92615082fa88676d39c5086b3b43。先仅回填Design document，再依模板写并链接next-task交接，最后执行NOT_STARTED -> READY；直接输入决定／约束、模板、相对链接及首个实施用例核对通过。未创建T11生产代码、测试或迁移，READY仅表示可按完成设计启动，不能视为组合事务或端到端验收通过。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T11专属设计及入口交接，核对READY与交接一致后执行READY -> IN_PROGRESS；保留交接作为入口上下文。沿用feat/date-range-download，记录HEAD／原暂存及生产资源摘要；先行MySQL回滚用例、实现和本轮验证按设计执行，不调用真实来源。

- **Completion evidence:** 2026-09-09 执行IN_PROGRESS -> COMPLETED。交付一个BatchCommitService、索引只读预检提取、App单bean及三类测试变化；[事务验证](../../verification/RANGE-T11-unit-commit.md)与[增量追踪](../../traceability/tensor-range-requirements.md#range-t11-单元事务层增量证据)记录AC-PRD-RANGE-07／13／15／21／24／26的本项机制。实际MySQL证明同manager／同连接、任务先锁且两锁至完成，分组／精确明细删除／主表收尾失败整单元回滚、已提交A与旧数据保留；完整键、空／同轮重复、整轮日历全闭绑定及确认提交／明确回滚／启动不可用／结果未知分类通过。T11从不确认索引或累计，确认回滚后独立保存成功才允许下一项，未知不补建失败。规定命令1～4分别22／70／16／89项通过，六类最终MySQL XML逐一读取共105项零失败／错误／跳过；verify755、acceptance758项Java及各170项前端测试／构建通过，合同8组＋4变异通过。独立首审唯一R1并发测试清理缺陷已修正，22项MySQL重跑及范围复审PASS，最终独立集成／证据评审PASS、无遗留。两次全量构建后仅改测试清理，生产版本相同。58份资源摘要、分支／HEAD、原暂存和文档链接／空白检查通过，本项新文件已加入Git；保留并行ISSUE-017成果，不提交／发布。运行下载编排、HTTP、浏览器及真实来源尚未接入或验证，生产注册表仍为空，ISSUE-008保持不依赖、未解决。此完成记录先于T12设计、交接及READY准备。

### RANGE-T12

- **Goal:** 首次区间下载遍历本轮全部计划范围，并返回与真实提交一致的结果。
- **Scope:** 在 DownloadService 接入校验、日历、规划、获取、单元校验／提交及首次失败创建／后续追加；实现共用进程内执行槽位、S／F／N 和 R／I／U 汇总及存储故障停止边界。
- **Acceptance:** 参数／插件／辅助日历失败、全成功、合法空或全休市不新增失败记录。来源权限、认证、限流、超时及局部错误保存后继续后续计划，本轮不自动重试失败请求；3／7 日失败时其他日期仍实际尝试。仅失败保存确认后返回 taskId；存储不可用、记录保存失败或提交未知停止，未开始／未知不入表。计数只描述本轮确认结果，未知不填零；区间执行断连仍继续，槽位至实际结束才释放，无主动终止入口。覆盖 AC-PRD-RANGE-05、07、08、10、12、13、15、17、23、26、27、28。
- **Dependencies:** RANGE-T08、RANGE-T09、RANGE-T10、RANGE-T11；消费计划、单元校验、失败存储及事务提交结果。
- **Sources:** ① [PRD §2、§5、§6](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §2、§3.2、§6.4、§8.2、§9～§10](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`；④ `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadServiceTest.java`；⑤ T08～T11 的直接输入。
- **First action:** 对照当前单调用 DownloadService 绘出本轮循环、槽位生命周期和错误分支，完成编排及计数设计。
- **State evidence:** 2026-09-09 在T11完成记录之后，按Order选择本项，观察NOT_STARTED且Design／Handoff均None。使用designing-task-contracts完成并完整读取专属设计，核对T08～T11直接产物及实际接口；固定新executeInitial与保留旧入口、唯一构造器、共用槽位、串行事件／保存门槛、全部T11结果、S／F／N／H及R／I／U、精确文件与Core／MySQL／构建命令。独立规格／就绪及设计质量评审PASS，无实质发现；评审版本SHA-256为5996b35330df2f1bd34337d2ab495df76f85f8ac1b9366deea1c290be959c5ab。先只回填Design单元格，再按模板写入完整交接、核对四个直接输入无冲突并链接，随后执行NOT_STARTED -> READY。设计／交接模板、链接与实施起点检查通过；未实施本项、未运行T12功能测试，READY只表示后继已准备。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。完整读取T12设计、入口交接及规定来源，核对READY及交接一致，保存分支／HEAD／原暂存与58份受保护资源摘要后执行READY -> IN_PROGRESS；原交接保留为入口上下文。按设计先行测试、最小实现、规定验证及独立评审执行，保留并行ISSUE-017内容，不提交／发布。

- **Completion evidence:** 2026-09-09 执行IN_PROGRESS -> COMPLETED。交付首次executeInitial、共用进程槽位、不可变本轮结果及停止快照；十日3／7失败继续、同批STOCK A/C保留且B完整回滚、保存确认门槛、全部T11结果及S/F/N/H与R/I/U均有受控Core及真实MySQL证据。独立Service20、Core定向114、六类显式App IT62、App helper31项均零失败／错误／跳过；verify775、acceptance778项Java及各170项前端测试／构建通过。合同8组＋4变异、两差异检查、58份资源及135份生产Java摘要核对通过；独立规格／质量及最终集成评审PASS，R1同批STOCK和R2此前确认小计保留证据缺口均补齐并复审关闭。详见[本轮验证](../../verification/RANGE-T12-initial-execution.md)及[机制追踪](../../traceability/tensor-range-requirements.md#range-t12-首次编排层增量证据)。本项新增文件已加入Git暂存，原分支／HEAD和任务外原暂存保留，不提交／发布。旧HTTP仍用execute；生产来源／日历表及49项REQUEST不变，T13重试、T14新HTTP、T18真实断连及T20来源不计作本项通过。

### RANGE-T13

- **Goal:** 用户手动执行原任务时仅处理仍存在的失败单元，成功精确移除。
- **Scope:** 获取共用执行槽位后重新读取任务，校验当前插件／冻结参数／选择器，重新确认适用日历，依次完整重取并提交；再次失败更新原项，不生成新轮次任务。
- **Acceptance:** 只执行明细，不使用原始展示区间，不合并不连续日期，不将 REQUEST 转 STOCK 或拆小 RANGE。两股同日失败时成功一股只删除它，最后一项成功删除主表；3 日解决后只重试 7 日且原始 1～10 日不变。来源错误继续后续项；日历未确认、参数不兼容及未轮到的项保留，存储／提交未知停止。槽位忙返回 409、已不存在返回 404；丢失响应或重启不重建已删除项，不自动恢复缺失范围。覆盖 AC-PRD-RANGE-03、12、14、15、17、21、22、23、24、25、26、28。
- **Dependencies:** RANGE-T05、RANGE-T06、RANGE-T10、RANGE-T11、RANGE-T12；消费精确重建、日历、失败仓储、原子提交和共用槽位／结果规则。
- **Sources:** ① [PRD §5.6～§5.8](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §3.3、§7、§8.2、§9](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/`；④ T05、T06、T10～T12 的直接输入。
- **First action:** 以 REQUEST 范围、STOCK 同日两股和单日原生接口三组记录写出预期重试请求与删除结果，完成重试用例设计。
- **State evidence:** 2026-09-09 在T12完成记录之后，按Order选择本项，观察NOT_STARTED且Design／Handoff均None。使用designing-task-contracts完成并完整读取专属设计，核对T05／T06／T10／T11／T12直接输入；固定独立七参RetryDownloadService、共用槽位内唯一读取、全部前置屏障、原项精确请求／原因更新／原子删除、全闭及混合RANGE、S/F/N/H与剩余项／最后删除未知、文件和Core／七类MySQL／构建命令。T11旧设计的全闭累计措辞已依据现行OpenAPI明确为不计S/R/I/U，T11实现无需改动，无未解决输入冲突。独立设计就绪／质量评审READY／PASS，最终设计SHA-256为3ae5e03a020bc505f40607606516e902f141754feb0711745720b1702e7efc87。先只回填Design，再按next-task模板写入完整交接；五个直接输入、模板、相对链接及三组先行测试实施起点校验PASS，先链接Handoff后执行NOT_STARTED -> READY。未实施T13、未运行其功能测试，READY只表示后继已准备。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T13设计、入口交接及规定PRD／TRD来源，核对READY及交接一致后执行READY -> IN_PROGRESS，保留交接为入口上下文。保存当前分支／HEAD／原暂存与58份受保护资源摘要；按三组先行期望开展Core测试、精确重试实现与显式MySQL验证，HTTP留T14。

- **Completion evidence:** 2026-09-09 执行IN_PROGRESS -> COMPLETED。交付独立七参RetryDownloadService及App共享bean：槽位内唯一读取当前原项，全部映射／Session／日历／来源规划先行；精确DATE／MONTH／RANGE／NONE与同日股票分别重取，原因更新确认后继续，业务和删除原子确认。真实MySQL验证3／7日原JSON及created_at不变、同日三股与分组回滚、最后主表删除、原因保存失败、提交丢答复及业务／全闭COMMITTED后异常；最后删除未知不给虚假存续ID，全闭S/R/I/U=0，H与N遵守冻结范围口径。最终Core130（Retry29）、七类App最新复合73（原70运行＋Retry11替换旧8）、helpers31项均零失败／错误／跳过；verify795、acceptance798项Java和各170项前端测试／构建通过。完整构建后的评审修复只补测试，最终定向覆盖，生产摘要相同。App两项和Core两P1／报告P2均复审关闭，最终规格／质量／集成与证据评审全部PASS，最后普通Committed测试增量已核对，无遗留。详见[精确重试验证](../../verification/RANGE-T13-exact-retry.md)与[机制追踪](../../traceability/tensor-range-requirements.md#range-t13-精确重试层增量证据)。合同8组＋4变异、58资源摘要、分支／HEAD、任务外原暂存及链接／两种空白检查通过；新增正式交付显式加入Git，不提交／发布。HTTP映射、真实进程／socket中断及真实来源由T14／T18／T20验收，ISSUE-008仍不依赖、未解决。此完成记录先于任何T14设计／交接创建或READY准备。

### RANGE-T14

- **Goal:** 将元数据、首次下载、失败查询及手动重试通过一致 HTTP 合同提供给页面。
- **Scope:** 迁移元数据响应和 POST /downloads，增加 GET /retry-tasks、GET /retry-tasks/{taskId}、POST /retry-tasks/{taskId}/execute；接入绑定、用例、查询与错误映射，Controller 保持参数／调用／响应职责。
- **Acceptance:** `/api/v1` 下四个下载／任务端点符合 T03；execute 空请求体且不接收可编辑参数。列表筛选／分页／排序及详情返回公共参数、原始区间、当前失败对象时间和安全原因；原始区间缺失的“不适用／未记录”可区分，插件下线不隐藏保存标识。retrying／canExecute 来自当前进程和可执行条件；404／409、部分结果、保存失败和提交未知映射准确。断连不释放槽位，不提供取消或核对端点。覆盖 AC-PRD-RANGE-01、06、13、19、22、23、25、26、27、28。
- **Dependencies:** RANGE-T03、RANGE-T04、RANGE-T05、RANGE-T12、RANGE-T13；消费对外合同、策略投影、请求绑定及两个执行用例。
- **Sources:** ① [TRD §8、§10.3](../../design/Tensor_区间下载_TRD_v1.0.md)；② `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`；③ `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/`；④ `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java`；⑤ T03～T05、T12／T13 的直接输入。
- **First action:** 对照 T03 逐端点列出控制器、用例、DTO 与异常映射，完成 Web 层设计及合同用例。
- **State evidence:** 2026-09-09 在T13已记录COMPLETED之后，按预定义Order选择本项，观察NOT_STARTED、Design／Handoff均None。使用designing-task-contracts完成并完整读取专属设计，核对T03／T04／T05／T12／T13直接输入及现行只读仓储：固定元数据38＋11同版迁移、四HTTP端点、18字段结果和number/null、原始日期四状态、静态blocker优先、只读slot Snapshot、UUID／零字节body、同步POST零预读、安全投影／观测、具体文件和八类MySQL等验证命令。旧日期alone错误优先、mapper先来源拒绝、全局Long字符串、不可读／空孤儿记录及全闭计数差异均有明确最小处理，无待决事实。独立就绪／规格／质量评审READY／PASS／PASS，最终设计SHA-256为e2b57c8332510c3f2a3475fe4686448e400ce59a8a22cc7739518e8f863fa2af。先仅回填Design；按next-task模板完成五组直接输入及实施起点交接，模板／相对链接校验PASS；先链接Handoff，再执行NOT_STARTED -> READY。设计和交接加入Git，未实施T14、未运行其功能测试；READY只表示后继已准备。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T14专属设计和入口交接，核对READY及直接输入边界一致，记录原分支／HEAD／索引和58份受保护资源摘要后执行READY -> IN_PROGRESS；保留交接作为入口上下文。本轮按设计实施HTTP迁移、只读任务查询及规定验证，不启动T15实现。

- **Completion evidence:** 2026-09-09 已按专属设计完成元数据38＋11及首次下载同版迁移、失败任务三端点、只读投影／原始日期四状态／原子槽位快照、18字段number/null结果、安全错误及观测。MySQL＋HTTP原1～10／当前3和7／精确重试／最后删除404、忙409与零字节body、保存与提交未知、同步等待者／写出失败边界均有本轮证据。独立Core／App规格与质量及最终集成／证据评审PASS；未知外层字段和标识标量强转两项Important发现均经行为RED、最小请求内修复、生产Spring mapper／servlet和全矩阵复验关闭。最终Core80、首三HTTP39、App九类236、显式MySQL八类114、verify829／acceptance832项Java及各170项前端测试和build通过，全部零失败／错误／跳过；合同8组＋4项变异、58资源摘要、780原索引、分支／HEAD和任务外内容、两种diff检查均通过。正式[验证报告](../../verification/RANGE-T14-http.md)与[增量追踪](../../traceability/tensor-range-requirements.md)已记录证据；新增八份Java和报告加入Git，未提交／发布。最终设计SHA-256为9bb3aac0ff8d72a3829336f71c8c63bbb6665fa83cf21fcca66858e10c9fe2bd。fixture正向HTTP仅为测试内包装，生产fixture能力留T18；页面、真实socket／进程中断及来源验收仍由后续任务承担。执行IN_PROGRESS -> COMPLETED；本完成记录写入时T15仍NOT_STARTED且Design／Handoff均None，后继准备仅在此记录之后进行。

### RANGE-T15

- **Goal:** 下载表单准确呈现五类输入及时间含义，提交一次合法区间请求。
- **Scope:** 接入策略元数据，修改下载动态表单、日期摘要、完整月份摘要、字段校验及 API 参数迁移；复用既有日期控件和表单公共逻辑，不改只读查询筛选合同。
- **Acceptance:** 49 项恰按 19／15／1／3／11 呈现，必要股票／市场条件保留；38 项提交起止日期，11 项不增加无效日期。31 天上限来自服务端，31／32 天、相同日期、闰日、逆序及跨年月份处理正确；月份可覆盖三个自然月，原生日期文案只使用已确认语义。每次提交只有一个下载请求，不由浏览器按日循环；切换接口清理不适用字段及旧结果。覆盖 AC-PRD-RANGE-01、03、04、05、06、18、19、20。
- **Dependencies:** RANGE-T14；消费运行元数据及新下载请求合同。
- **Sources:** ① [PRD §2.2、§3、§7](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §4.3、§8.3、§10.1](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/composables/useParameterForm.js`；④ `control-plane/src/api/downloads.js`；⑤ T14 的元数据和参数合同。
- **First action:** 按现有动态表单逐模式列出控件、标签、摘要和精确请求示例，完成表单迁移设计。
- **State evidence:** 2026-09-09 在T14已记录COMPLETED之后，按预定义Order选择15，观察NOT_STARTED、Design／Handoff均None。使用designing-task-contracts完成专属设计并完整读取、自审就绪：固定五类公开策略／标签／原生保守文案、服务端上限与配对校验、日期低年份／时区安全算术、31天跨三月摘要、一次POST、零参数配置守卫及修改条件清理；列明具体组件接口、49项真实资源测试输入、首错聚焦／查询隔离、精确命令和T16～T20边界，无待决产品事实。最终设计SHA-256为4c8023d6d39833bb4b5ac775e214085dbe9cf37d2ba112e0f3eeada4feff2708。先仅回填Design，按next-task模板完成T14元数据／请求和当前表单三组直接输入交接；模板、完整性及相对链接检查PASS。先链接Handoff，再执行NOT_STARTED -> READY。新增设计／交接纳入Git；本轮未实施T15、未运行T15功能测试，READY仅表示已准备。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T15设计与入口交接、规定PRD／TRD来源、T14合同及当前前端，核对READY和交接一致后执行READY -> IN_PROGRESS；保留原交接。基线已保存至`.superpowers/sdd/RANGE-T15/baseline.json`，保留当前分支／HEAD、原索引及任务外工作；本轮仅实施T15表单范围，不提交／发布。

- **Completion evidence:** 2026-09-09 完成五类公开策略表单、日期语义／服务端上限、自然日／完整月份摘要、一次规范化POST及条件修改清理。49真实资源fixture逐项独立核对19／15／1／3／11、38＋11、九形状，并实际mount各表单；股票／市场／原条件、31／32天／跨三月／低年份／两时区、配置错误零请求及首错聚焦通过。两处本地核对发现（原生语义键碰撞、通用关联错误清理范围）已修正，独立初审的Important测试矩阵缺口经仅测试补强关闭。最终指定组7文件96项、UTC及LA各2文件22项、全量25文件215项零失败／跳过；build1700模块、合同8组＋4变异、58生产资源／791原索引／17文件摘要、分支HEAD及任务外文件保护、两种diff检查通过。本地受控Chromium1440／390宽度一次POST／32天拒绝／清理／聚焦／无溢出观察通过，仅作表单证据。独立规格／质量复审与最终集成／证据评审均PASS，无遗留。正式[验证报告](../../verification/RANGE-T15-form.md)与[追踪](../../traceability/tensor-range-requirements.md)记录范围；三份新增前端及报告纳入Git，未提交／发布。设计SHA-256仍为4c8023d6d39833bb4b5ac775e214085dbe9cf37d2ba112e0f3eeada4feff2708。T16新结果／断连、T17失败任务页及T18～T20受控后端／完整浏览器／真实来源仍未在本项验收。执行IN_PROGRESS -> COMPLETED；本完成记录写入时T16仍NOT_STARTED且Design／Handoff均None，后继准备仅在此后进行。

### RANGE-T16

- **Goal:** 页面显示本轮真实完成情况，并正确处理等待、切页和失去响应。
- **Scope:** 修改 useDownloadFlow、DownloadResult 和 DownloadView，接入部分完成、计数、保存的任务入口、未开始／未知说明及不可终止提示；保留现有布局和公共组件。
- **Acceptance:** 请求中禁用条件与重复提交，提示“区间下载已开始，不可终止，请等待结果。”；不显示进度或取消操作。A／C 成功、B 失败显示完成 2／失败 1，R／I／U 取确认结果，空／休市／未知不混淆。切页不产生自动重发或错配响应；HTTP 超时提示结果未确认，不声称回滚或补造任务。已保存失败走任务入口，重新主动提交原条件仍是独立下载。覆盖 AC-PRD-RANGE-07、13、15、17、20、26、27、28。
- **Dependencies:** RANGE-T14、RANGE-T15；消费本轮结果合同、运行接口及区间表单。
- **Sources:** ① [PRD §2、§5.4、§5.8](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §6.4、§8.3、§9](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/components/download/DownloadResult.vue`、`control-plane/src/views/DownloadView.vue`；④ `control-plane/src/api/http.js`；⑤ T14／T15 的直接输入。
- **First action:** 对照现有请求世代和页面状态列出新响应分支，完成结果、锁定与断连提示设计。
- **State evidence:** 2026-09-09 在T15已记录COMPLETED之后，按预定义Order选择16，观察NOT_STARTED、Design／Handoff均None。使用designing-task-contracts完成专属设计并完整读取、自审及独立就绪评审PASS：固定18字段结果守卫、26码及四停止快照、六种结果与通信未知、S/F/N/H和R/I/U、三组范围、仅metadata重载、现有KeepAlive及本地锁定；任务入口精确query由T17消费，实际任务内容仍由T17交付。明确具体文件／接口／失败规则、真实Axios测试矩阵、命令及八项AC，没有待决产品事实。最终设计SHA-256为f3c4b9fb8e1576df1a68afa8e2ce7a4ed8c2f7458ac84d97cec6a12bdafbbb9b。先仅回填Design，再按next-task模板完成T14结果／错误、T15表单／清理及现有页面生命周期三组直接输入交接；模板、相对链接、设计摘要和输入一致性检查PASS。先链接Handoff，复核NOT_STARTED后执行NOT_STARTED -> READY。新增设计／交接纳入Git；本轮未实施T16、未运行T16功能测试，READY仅表示已准备。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。已完整读取T16设计、入口交接及规定来源，核对READY和交接一致后执行READY -> IN_PROGRESS，保留原交接为入口上下文。开始时分支feat/date-range-download、HEAD 758f940503ded2d1185040bc8e324815c716a300、797条原索引和799份文件摘要已保存；不提交、不发布、不改任务外工作。

- **Completion evidence:** 2026-09-09 完成18字段严格结果守卫、26码及四停止快照、六outcome／通信未知、本轮固定上下文、七计数及三组范围、仅metadata重载和精确taskId路由入口。137项指定／278项全量前端测试零失败／跳过，build1701模块、合同8组＋4变异、两种diff检查通过；Chromium1440／390宽度PARTIAL／UNCONFIRMED、null／小计／长范围、切页同一POST、键盘任务入口和再次主动提交新RequestId观察通过。最终评审发现四停止快照Axios证据不全，补独立真实Axios四码探针，每码一次POST、500／retryable／null／复制冻结小计通过；冻结14文件未改，复核关闭唯一Important，UI／API独立规格质量及最终集成／证据APPROVED，无遗留。58生产资源、799份开始文件中的任务外内容、797原索引、分支HEAD保护通过。正式[验证报告](../../verification/RANGE-T16-results.md)和[追踪](../../traceability/tensor-range-requirements.md)记录八项AC前端增量；五个新增正式文件纳入Git，未提交／发布。设计SHA-256保持f3c4b9fb8e1576df1a68afa8e2ce7a4ed8c2f7458ac84d97cec6a12bdafbbb9b。T17任务内容／execute、T18～T20受控后端／完整浏览器／真实来源尚未在本项验收，ISSUE-008继续不依赖、未解决。执行IN_PROGRESS -> COMPLETED；本完成记录写入时T17仍NOT_STARTED、Design／Handoff均None，后继准备仅在此后进行。

### RANGE-T17

- **Goal:** 用户可查看当前失败任务并直接手动重试剩余项。
- **Scope:** 在下载页增加“发起下载／失败任务”页签，接入任务 API、筛选分页、列表、详情和“重试一次”；复用现有页面组件，不增加历史或任务编辑页面。
- **Acceptance:** 列表／详情分别显示原始下载区间与当前失败对象、时间和原因；首次 1～10 日、仅 3／7 日失败时展示准确，部分重试成功后原始区间不变。STOCK 显示股票代码，REQUEST 结合公共条件显示范围，不将同日两股折叠；原条件为“不适用”，旧区间无日期为“未记录”。直接执行无确认弹窗，忙时禁用／说明，404 刷新真实列表；成功项移除、最后一项解决移除任务。刷新／重启只读现存记录，不恢复历史计数，不推断查不到任务就是成功。覆盖 AC-PRD-RANGE-20、21、22、23、25、27。
- **Dependencies:** RANGE-T14、RANGE-T16；消费失败任务 API、执行结果及任务入口。
- **Sources:** ① [PRD §1.1、§5.5～§5.8](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §8](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `control-plane/src/views/DownloadView.vue`、`control-plane/src/components/common/`、`control-plane/src/api/`；④ T14／T16 的直接输入。
- **First action:** 按列表、详情、执行中、部分解决及任务消失场景列出交互与 API 消费，完成失败任务页面设计。
- **State evidence:** 2026-09-09 在T16已记录COMPLETED后，按预定义Order选择17，观察NOT_STARTED、Design／Handoff均None。使用designing-task-contracts完成专属设计并完整读取、自审及独立就绪评审PASS：固定两个页签、三任务Axios端点、零字节execute、独立筛选与20／50／100分页、原始区间和完整当前明细、双向本地互锁、路由GET／KeepAlive及404／409／未知刷新规则；具体模块／组件接口和测试矩阵／命令齐全，无待决事实。评审纠正初稿Detail18字段为实际17（Summary11＋六字段），并核对OpenAPI／Java DTO；无遗留发现。最终设计SHA-256为93dac155c8cc2ca31eb35aee999ee32ed16ae969d34757b9ac37e76178adace3。先仅回填Design，再按next-task模板完成T14任务HTTP、T16结果／入口及现有组件生命周期三组直接输入交接；模板、字段清单、相对链接和摘要检查PASS。先链接Handoff，复核NOT_STARTED后执行NOT_STARTED -> READY。新增设计／交接加入Git，本轮未实施T17、未运行T17功能测试；READY仅表示已准备。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。完整读取T17设计、入口交接及规定直接来源，核对READY及交接一致，执行READY -> IN_PROGRESS并保留入口交接。开始时分支 feat/date-range-download、HEAD 758f940503ded2d1185040bc8e324815c716a300；原索引及806份开始文件摘要／副本保存在`.superpowers/sdd/RANGE-T17-design/`；保留任务外工作，不提交／发布。

- **Completion evidence:** 2026-09-09 完成两个保留实例页签、精确任务入口GET、独立筛选／20／50／100分页、原始1～10与当前3／7→7、同日两股、原UUID零body直接重试及六种本轮结果。双向本地互锁、在途路由身份、404／409／通信未知和显式GET恢复权限通过实际Axios／页面验证。215项指定、31文件356项全量零失败／跳过，build1708模块、合同8组＋4变异、检测与两种diff检查通过。Chromium1440／390每尺寸35次HTTP／6次零字节POST、16张截图，无横向溢出或非预期console／pageerror，served与磁盘构建摘要相等。最终评审发现多页加载使ElementPlus隐式请求第1页；旧构建真实浏览器复现[1,2,2,1]，局部两绑定修复后，真实Axios及两尺寸延迟GET序列1／2／2／3保留第2页刷新／第3页导航、执行后当前页自动刷新与失败重载，已按新源码重跑完整验收。所有规格／质量复审及最终集成／证据APPROVED，无遗留。正式[验证报告](../../verification/RANGE-T17-retry-page.md)及[追踪](../../traceability/tensor-range-requirements.md)记录六项AC前端增量；806开始文件中的任务外内容、804原索引、58生产资源、原分支HEAD保护通过，10个新增正式文件纳入Git，不提交／发布。T17设计摘要保持93dac155c8cc2ca31eb35aee999ee32ed16ae969d34757b9ac37e76178adace3。执行IN_PROGRESS -> COMPLETED；本记录先于后继设计／交接准备，T18尚未启动，T18～T20受控后端／真实中断／完整浏览器／真实来源边界不升级，ISSUE-008仍不依赖、未解决。

### RANGE-T18

- **Goal:** 用受控来源和独立 MySQL 数据证明失败隔离、精确重试及故障边界。
- **Scope:** 扩展现有 fixture 场景和集成验收，覆盖多批、多股、多页、局部字段错误、SQL 分组失败、明细删除失败、保存失败、提交未知、进程重启和客户端断连；汇总前述实现任务的行为证据。
- **Acceptance:** TRD §12.3 的 17 个场景都有测试及实际业务表／两张失败表的独立核对。重点证明 B 失败时 A／C 保留、两股同日分别删除、原始区间不变且只请求剩余明细、来源错误仍尝试后续范围、记录／提交未知停止而不补造记录、断连不取消／释放槽位。覆盖 AC-PRD-RANGE-02～18、21～29 的后端行为，并回归参数迁移；后端 verify 和 acceptance clean verify 通过。受控通过不计作来源真实支持；安全日志／存储不含凭证及响应，测量多日多页、单日高量和宽表的内存峰值及耗时，不新增执行上限。
- **Dependencies:** RANGE-T12、RANGE-T13、RANGE-T14；消费首次／重试用例及真实 Web 入口，其内部直接输入的验证记录一并用于场景核对。
- **Sources:** ① [PRD §9](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §9～§12](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/`；④ `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`；⑤ T12～T14 的实现及验证结果。
- **First action:** 将 TRD §12.3 逐项映射到 fixture 故障注入点、调用序列和独立 SQL 核对，完成集成验收设计。
- **State evidence:** 2026-09-09 在T17已记录COMPLETED之后，按Order选择18并观察NOT_STARTED；消费T12／T13／T14直接输入，完成T18专属设计并全文自审、独立就绪评审PASS。两项Important已关闭：多日失败保存后同一execute继续下一多日段；真实定义宽表与runtime共用明确分页客户端接口。真实突发kill及STOCK脚本精确键已固定，设计最终SHA256为28fb6d543f4956048213e68c009287f62a018c25206e78c5af495b560d70afbd。先仅回填Design document，读取next-task模板后写完整交接并链接Handoff，最后执行NOT_STARTED -> READY。直接依赖决定／约束一致；READY仅表示设计和交接就绪，本轮未实施T18、未运行其功能验收。

- **Start evidence:** 2026-09-09 用户明确要求“按照区间下载任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。完整读取T18专属设计及入口交接，核对READY及直接输入后执行READY -> IN_PROGRESS；保留交接为入口上下文。在原feat/date-range-download分支工作，HEAD、816份已跟踪文件、原索引／差异及58份生产资源摘要已记录于`.superpowers/sdd/RANGE-T18-design/`。Colima Docker 29.5.2只读探测成功；功能验收待执行，不提交／发布。

- **Completion evidence:** 2026-09-09 完成runtime fixture五模式／完整分页、真实HTTP／MySQL／新JAR进程和socket验收，TRD §12.3的17行均有本轮来源／结果／独立业务及两失败表SQL。fixture82、Core153、显式MySQL12类216、verify891、acceptance clean verify894分别零失败／错误／跳过，两次构建各356前端及1708模块；构建后仅测试补强，HTTP27、Process7＋Load3共10、SQL46各独立通过，不拼成一次运行。三类固定18,600／10,000／5,000行各3插入＋3更新、50ms采样及schema通过；新JAR摘要626e0e840370b8b44d4f9a280444a1adb88aa9c658a16a78bf99a83d900ca0e0，207runtime文件冻结。脚本数值精度、Load失败证据/schema、HTTP三表schema及SQL cause canary等Important均修复复跑，最终独立规格／质量／集成／证据PASS，无遗留。正式[验证报告](../../verification/RANGE-T18-controlled-mysql.md)和[追踪](../../traceability/tensor-range-requirements.md)仅升级受控后端事实；四脚本／真实classpath及运行配方可供T19使用。58生产资源、fixture YAML/V6、816开始索引及任务外tracked内容、分支HEAD保护通过，13个新增正式文件加入Git，不提交／发布。执行IN_PROGRESS -> COMPLETED；此完成记录先于后继准备，T19仍NOT_STARTED、Design/Handoff均None。T19页面／T20真实来源尚未验收，ISSUE-008仍不依赖、未解决。

### RANGE-T19

- **Goal:** 在正式页面完成区间下载、查看已提交数据、失败查询和反复手动重试闭环。
- **Scope:** 基于 T18 受控后端执行浏览器验收，覆盖 49 项表单合同、日期边界、结果、任务页、切页／刷新／断连以及现有只读查询回归；不使用真实 Tushare 调用代替可控故障场景。
- **Acceptance:** 前端单测、构建和配置环境后的 test:e2e 通过；29 项 AC 的页面适用部分都有明确证据及后端核对引用。实际页面验证一次提交、无取消／自动重发、两股同日失败、原始 1～10 日与剩余 3／7 日、部分重试后仅剩 7 日、重启后查看及 404／409。现有数据查看、精确数值和分页可用，长错误与对象范围可读，日期／结果／重试可用键盘操作；截图和结果对应本轮构建。
- **Dependencies:** RANGE-T15、RANGE-T16、RANGE-T17、RANGE-T18；消费完整页面及受控后端故障场景。
- **Sources:** ① [PRD §2、§3、§5.6～§5.8、§9](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §8、§12](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `control-plane/e2e/fixture-flow.spec.js`、`control-plane/e2e/download-outcomes.spec.js`；④ `control-plane/package.json`；⑤ T15～T18 的页面、场景与验证结果。
- **First action:** 将 PRD 验收矩阵映射到页面操作、请求捕获及数据库证据，完成浏览器验收设计。
- **State evidence:** 2026-09-09 在T18已记录COMPLETED之后，按预定义Order选择19并观察NOT_STARTED；核对T15／T16／T17／T18直接输入、决定及约束，完成T19详细设计，全文读取／自审及独立就绪评审PASS，无Important／Critical。设计明确四spec／两尺寸、真实JAR／MySQL测试桥及透明代理协议、固定SQL样例／故障、29AC分层证据及最终构建验收。评审反馈三项已关闭：真实GET安全原因映射与前端长文本分层、只验证现有查询表格／tooltip、移除旧spec固定viewport覆盖。设计最终SHA256为6ba18160fc9e8c014bc2f5579b6a759b51f664e0e767c70d186b19ea9c806e01；先仅回填Design document，再按next-task模板写完整交接并链接Handoff，确认仍NOT_STARTED后执行NOT_STARTED -> READY。READY仅表示设计和交接就绪，未实施T19、未运行其e2e。两个新设计／交接文件纳入Git，原索引保留，不提交／发布；后续从交接中的具体实施动作开始，不需补设计。

### RANGE-T20

- **Goal:** 以本轮真实来源证据核实代表接口的区间语义、取全、日历及恢复策略。
- **Scope:** 按 PRD §9.2 从有权限的 daily、income、margin、weekly／monthly、trade_cal、new_share 等候选中确定实际范围；验证当前参数条件、两端包含性、批次取全及所启用的独立重试方式，不恢复 ISSUE-008 的 9 项真实调用。
- **Acceptance:** 专属设计先依据 T01／T02 明确实际接口、日期／对象条件、权限和验证方式；执行后的来源请求、日历、业务键集合／内容、数据库写入、失败明细与本轮计数可相互核对。区间结果与合法单日／单期基准比较；已启用的独立恢复方式有独立证据，无依据时不能计为通过。分别报告 49 项合同、38 项区间目标、49 项恢复策略和本轮账号实测范围；未验证明确保留，不用旧 40 项单日验收冒充通过。所选范围的验收失败或所需来源证据未关闭时不能标本任务完成。
- **Dependencies:** RANGE-T01、RANGE-T02、RANGE-T18、RANGE-T19；消费来源待验证事实、日历依据及已通过的受控后端／页面闭环。
- **Sources:** ① [PRD §3.4、§9.2、§10](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §1.4、§5、§12～§13](../../design/Tensor_区间下载_TRD_v1.0.md)；③ [官方能力调研](../../research/2026-09-08-tushare-range-batch-research.md)；④ [ISSUE-008](../../issues/problems/ISSUE-008-tushare-live-coverage-gap.md)；⑤ `docs/runbook/acceptance.md`；⑥ T01／T02、T18／T19 的直接输入。
- **First action:** 从 T01／T02 的待实测清单中剔除 ISSUE-008 排除项，结合验收环境确定代表接口和输入，完成真实验证设计。
- **State evidence:** None。

### RANGE-T21

- **Goal:** 让运行说明、迁移说明及验收结论准确对应最终区间下载行为。
- **Scope:** 同步 README、运行／配置／验收说明、接口示例和需求追踪；记录同版参数迁移、两表部署、等待在途执行结束后停旧进程及回退读取限制，汇总本轮证据。
- **Acceptance:** 旧 trade_date／ann_date／month 调用示例按 38 项迁移，11 项条件保留；文档说明 31 天上限、日历未确认、来源错误继续、存储未知停止、原任务重试和原始区间展示。保留既有迁移、失败记录和业务数据，版本切换不主动终止执行，回退不删表。29 项 AC 均链接具体结果，已实现／受控通过／真实通过／未验证分别披露，ISSUE-008 仍为“不依赖，未解决”。适用构建／启动／迁移证据对应最终版本；无凭证泄露或用历史记录替代本轮结果。
- **Dependencies:** RANGE-T03、RANGE-T19、RANGE-T20；消费合同与追踪、最终页面／受控验收和真实来源证据。
- **Sources:** ① [PRD §7、§9](../../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD §10～§13](../../design/Tensor_区间下载_TRD_v1.0.md)；③ `README.md`、`docs/runbook/first-run.md`、`docs/runbook/configuration.md`、`docs/runbook/acceptance.md`；④ `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`；⑤ T03、T19、T20 的合同及验收证据。
- **First action:** 对照最终合同和验收结果列出旧示例／运行说明差异，完成文档修订、迁移核对及证据收尾设计。
- **State evidence:** None。

## Risks

- **来源参数与日期含义：** 公开支持起止参数不保证当前条件可获取全市场，也不保证区间是公告日期。5 项报告期／解禁日期差异及缺失股票条件由 T01 明确处理；不能为了完成接口数量更换 API、加入未确认必填项或扩大取数范围。
- **日历证据：** T02已完成逐项研究，结论为1项静态DOCUMENTED、16项UNCONFIRMED、2项CONFLICT；来源适用、实际日期覆盖及更新缺口仍须由后续实现／T20依据证据关闭。CALENDAR_UNCONFIRMED 是失败边界，不是休市过滤正式验收通过；唯一静态DOCUMENTED项仍排除真实调用，其余缺口影响相关业务可用性结论。
- **整体取全与独立恢复：** 分页、单次上限、对象时间归属和单独重试分别需要证据。REQUEST 只降低恢复粒度要求，不豁免来源整体完整性；没有可靠成员集合不能从部分响应猜全部失败股票。
- **存储与中断：** 只保存实际明确失败，进程退出前未保存、未开始和提交未知均不保证可恢复；已有明细在原子成功提交前保留。相关实现和提示由 T10～T14、T16～T19 验证，不增补恢复账本。
- **同步执行：** 来源错误继续后续范围可能超过客户端现有等待时间。服务端执行槽位直到真实结束才释放，页面断连不取消，不能重新引入整轮时长／累计行数等已删除的执行上限。
- **真实验收与排除：** `top_inst`、`broker_recommend`、`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`namechange`、`hsgt_top10` 仅保留目标合同／策略登记及受控覆盖，真实调用继续不依赖且不执行；不关闭 ISSUE-008，不把 49－9 解释为当前区间支持数量。T20 的实际代表范围在专属设计中依据权限与证据确定，创建看板不表示这些验证已经安排或执行。
- **设计成熟度：** RANGE-T01～T18已完成专属设计及任务验收；T18已交付五模式runtime fixture／完整分页、17场景来源与独立SQL、真实进程／socket、三类18轮负载及安全验证，最终独立评审PASS。随后按Order完成T19详细设计与交接并经独立就绪评审，当前READY、尚未实施。生产日历和完整来源表仍为空；完整浏览器／真实来源按T19／T20验收，受控后端通过不升级真实来源能力，后续逐项准备专属设计。
