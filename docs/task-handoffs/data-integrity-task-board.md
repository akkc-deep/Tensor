# 数据完整性检验任务看板

## Project

- **Project ID:** `DATA-INTEGRITY`。
- **Goal:** 按 [数据完整性检验设计 v1](../task-designs/DATA-INTEGRITY-design.md) 实现本地数据检查：选择数据源、股票、日期及接口，保存可追溯报告，并从前端定位具体问题日期和业务键。
- **Scope:** 插件可选能力、固定检查范围、40 个 Tushare 接口口径、只读扫描、通用规则与行情候选缺口、后台检查任务、持久报告、HTTP API、前端创建/历史/详情及受控验收。首版不包含上游在线对账、自动补数、定时检查、跨来源评分、生产基线资料库、规则编辑器、导出或报告自动清理；不改变 SINGLE/RANGE 下载能力。
- **Completion condition:** 13 项任务按各自验收条件完成；共享设计“如何验证”的 10 项结果都有证据；受控真实 MySQL、真实后端 fixture 浏览器闭环、既有功能回归及合同校验通过。任务拆分、API stub 页面或 fixture 的可靠全集不能代替生产 Tushare 覆盖证明。
- **Design authority:** `docs/task-designs/DATA-INTEGRITY-design.md` 是共享功能设计。本板仅拆分责任、顺序、依赖及验收；各任务的详细设计尚未创建，`Design document` 初始化为 `None`，不把共享设计冒充已完成的任务专属设计。
- **Workspace:** `.worktrees/data-integrity`，分支 `feat/data-integrity`，起点提交 `211094822aa061c2f9217d367cc06f8ddcae3b4e`。2026-09-16 从 `feat/studio-frontend` 工作区复制当时全部 142 个已暂存改动，包含共享设计及 Studio 当前代码；复制后保持暂存，未为基线创建提交。后续原工作区修改不会自动同步。本次仅创建看板并补充目录入口，未开始功能实现。
- **Integration:** 2026-09-16 用户要求将拆分内容合回当前分支 `feat/studio-frontend`；合入范围为本看板和任务交接目录入口，不回写独立工作区中复制的旧代码基线。合入后的任务状态以当前分支本板为准。

## Workflow

- **Execution:** 串行执行由用户掌握；看板不执行跨任务互斥检查。按 `Order` 分为合同与检查基础（T01–T04）、持久化与执行（T05–T07）、来源规则与 API（T08–T09）、前端与最终验收（T10–T13）。
- **Next-task selection:** 当前任务完成后，选择 `Order` 大于当前任务且最小的未完成任务；不按文件名、依赖形状或临时优先级另选。
- **Successor preparation:** 完成并回填后继任务专属设计后，才写入其 `next-task` 交接并准备为 `READY`；后继设计失败不改变前项已记录的完成状态。任务设计不得改变共享设计的已定口径。
- **Allowed transitions:** `NOT_STARTED -> READY`、`READY -> IN_PROGRESS`、`IN_PROGRESS -> PAUSED`、`PAUSED -> IN_PROGRESS`、`READY -> BLOCKED`、`IN_PROGRESS -> BLOCKED`、`BLOCKED -> READY`、`IN_PROGRESS -> COMPLETED`。
- **Initialization:** T01 为 `READY`，其余为 `NOT_STARTED`，全部 `Handoff=None`。`READY` 表示可以承接任务，不表示已有任务专属设计或已经开始编码；启动实施需明确启动请求及设计就绪。
- **Evidence:** 每项实现同时承担其专项测试；T13 承担整体集成与回归，不替前项补记未经验证的完成。下文测试名、命令和预期结果均为后续验收要求，本次未运行功能测试，也不宣称已有实现通过。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
|---:|---|---|---|---|---|---|
| 1 | DATA-INTEGRITY-T01 | 插件能力、规则与报告合同 | READY | None | None | None |
| 2 | DATA-INTEGRITY-T02 | 本地能力发现与 40 接口口径 | NOT_STARTED | DATA-INTEGRITY-T01 | None | None |
| 3 | DATA-INTEGRITY-T03 | 一致快照与受限只读扫描 | NOT_STARTED | DATA-INTEGRITY-T01 | None | None |
| 4 | DATA-INTEGRITY-T04 | 精确集合比较、通用规则与 fixture | NOT_STARTED | DATA-INTEGRITY-T01, DATA-INTEGRITY-T03 | None | None |
| 5 | DATA-INTEGRITY-T05 | 报告表、原子持久化与分页查询 | NOT_STARTED | DATA-INTEGRITY-T01 | None | None |
| 6 | DATA-INTEGRITY-T06 | 范围固定、幂等受理与队列准入 | NOT_STARTED | DATA-INTEGRITY-T02, DATA-INTEGRITY-T05 | None | None |
| 7 | DATA-INTEGRITY-T07 | 后台执行、预算与中断处理 | NOT_STARTED | DATA-INTEGRITY-T03, DATA-INTEGRITY-T04, DATA-INTEGRITY-T05, DATA-INTEGRITY-T06 | None | None |
| 8 | DATA-INTEGRITY-T08 | Tushare 日周月候选缺口规则 | NOT_STARTED | DATA-INTEGRITY-T02, DATA-INTEGRITY-T03, DATA-INTEGRITY-T04 | None | None |
| 9 | DATA-INTEGRITY-T09 | HTTP API、错误与公开合同 | NOT_STARTED | DATA-INTEGRITY-T02, DATA-INTEGRITY-T05, DATA-INTEGRITY-T06, DATA-INTEGRITY-T07 | None | None |
| 10 | DATA-INTEGRITY-T10 | 前端请求、精确 DTO 与检查状态 | NOT_STARTED | DATA-INTEGRITY-T09 | None | None |
| 11 | DATA-INTEGRITY-T11 | 创建检查、口径预览与历史页面 | NOT_STARTED | DATA-INTEGRITY-T10 | None | None |
| 12 | DATA-INTEGRITY-T12 | 报告详情、问题定位与再次检查 | NOT_STARTED | DATA-INTEGRITY-T10, DATA-INTEGRITY-T11 | None | None |
| 13 | DATA-INTEGRITY-T13 | 真实 fixture 闭环、回归与文档验收 | NOT_STARTED | DATA-INTEGRITY-T07, DATA-INTEGRITY-T08, DATA-INTEGRITY-T09, DATA-INTEGRITY-T11, DATA-INTEGRITY-T12 | None | None |

## Task Details

### DATA-INTEGRITY-T01

- **Goal:** 来源插件和核心层使用同一份可选检查、规则、范围、证据与报告合同，规则升级可以被稳定识别。
- **Scope:** 按共享设计第 1、2、5、6 节新增 plugin-api 的 `IntegrityCheckSupport` 与 `integrity/` 不可变合同，以及 core 的 `IntegrityCheckJson` 稳定编码；定义规则/能力版本、精确业务键与计数、任务/单元执行状态和数据结论的边界；在现有 fixture 开关内准备可靠与未知预期集合样例。此项不实现 SQL 扫描、任务调度或页面；fixture 的实际比较在 T04 验收。
- **Acceptance:** 旧 `DataSourcePlugin` 不实现能力时仍保持原合同；STOCK_DATE/STOCK_SNAPSHOT 各有且只有一个 COVERAGE 规则，NON_STOCK 无执行规则；规则描述与实现一致、规则 ID 不重复、依赖列有效。哈希包含有序 API、定义、口径及全部规则版本，不含凭据/时间，版本或定义改变导致哈希改变。报告保留原范围、完整键、问题主日期/关联日期及证据，Long 为十进制字符串、未知为 null、覆盖率采用 6 位 HALF_UP；FAIL > UNKNOWN > WARN > PASS，全 N/A 不得 PASS。`IntegrityPluginContractTest` 覆盖本项合同与哈希，fixture 明确区分 PROVEN/UNCONFIRMED。
- **Dependencies:** `None`。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、2、5、6 节；`docs/runbook/data-integrity-rules.md` 第 1、3 节；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/BatchDownloadSupport.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinition.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskJson.java`；`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`。
- **First action:** 对照现有插件/数据集合同逐项落定共享设计第 1 节的不可变类型、规则校验及 fixture 样例，完成 T01 专属设计并回填本板。
- **State evidence:** 2026-09-16 用户要求“创建独立工作区，按照数据完整性检验设计拆分任务”；按初始化规则设为 `READY`，没有实施启动或验收证据。

### DATA-INTEGRITY-T02

- **Goal:** 无上游 Token 也能发现已启用插件的本地检查能力，并为全部 40 个 Tushare 接口明确检查口径。
- **Scope:** 扩展 `PluginRegistry` 的独立本地能力查找；接入 `TushareProPlugin` 与来源 `integrity/` 描述/覆盖规则注册；实现 Tushare 股票 trim、大写及六位代码加 SH/SZ/BJ 的规范化。按共享设计第 3 节列出日期轴、股票列、依赖及下载限制，当前无法证明的覆盖返回明确 UNKNOWN；daily/weekly/monthly 的候选推导留给 T08。
- **Acceptance:** 启用但缺 Token 的插件可本地检查；旧插件给出不支持说明，显式停用与重复 ID 仍拒绝，现有下载 `find` 语义不变。本地没有记录的合法股票不被剔除。`TushareIntegrityPoliciesTest` 逐项验证与 manifest/YAML 恰好 40 项对应、所需字段存在，ann_date/end_date/ipo_date/in_date 不被替换；两项快照保留 HISTORY_NOT_STORED，四项 NON_STOCK 保留 N/A，未知规则不伪装 N/A。撤回 RANGE 的接口仍可描述本地检查，描述查询不发网络请求；补齐 `IntegrityPluginContractTest` 的注册/兼容案例。
- **Dependencies:** `DATA-INTEGRITY-T01`，消费可选能力、规则描述校验与稳定能力哈希合同。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1–3、10 节；`docs/data-template/manifest.json`；`docs/data-template/` 中对应接口定义；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`；`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/registry/RegistryTest.java`；T01 的已验收合同。
- **First action:** 将共享设计日期轴表逐项对应到 manifest/YAML 和现有插件注册门禁，完成 T02 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T03

- **Goal:** 每个检查单元在同一只读一致快照内安全、完整地读取目标数据与已声明参考数据。
- **Scope:** 新增 `IntegrityReadRepository` 及 `IntegrityContext.scan` 的读取支撑；复用 catalog、SQL 标识符校验和类型绑定；按 COMPOSITE 全物理键或 FINGERPRINT 的 business_key 稳定游标扫描。提供累计行数/时间预算的扫描执行边界，任务级调度由 T07 接入；不复用页面 records 分页，不开放任意 SQL 或证券表写入。
- **Acceptance:** `IntegrityReadRepositoryIT` 实际连接 MySQL，证明 REPEATABLE_READ 下跨页及并发写入结果一致，无重复或遗漏；批大小默认 500，所有目标/参考扫描累计计费，SQL 超时不超过剩余单元预算。非法表/列、未声明参考与跨股票/范围读取被拒绝；DATE/DECIMAL/LONG 按合同精确处理。另查选中股票的空日期行，标记范围无法确定，不归入任意日期/命中数；周月参考范围仅按声明用途扩展。扫描前后证券表内容不变，读取中断不产生伪全量统计。
- **Dependencies:** `DATA-INTEGRITY-T01`，消费范围、读取请求、数据集/依赖声明及上下文合同。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、3、4、7 节；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QuerySqlFactory.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`；T01 的已验收读取合同。
- **First action:** 对照物理键定义及现有事务工具，确定游标、目标/参考授权和单元预算传递方式，完成 T03 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T04

- **Goal:** 用完整精确业务键计算缺失与额外记录，并独立输出覆盖、键和必填字段结论。
- **Scope:** 实现 `IntegrityContext.compare`、问题收集与结果聚合，以及 `core.required-fields@1`、`core.business-key@1`、`core.source-identity@1`；接入 T01 的 fixture 可靠/未知/局部证据样例。FINGERPRINT 复用现有 codec；不引入 Tushare 专用分支或额外行情合理性规则。
- **Acceptance:** `IntegrityComparisonTest` 证明可靠 E=20、命中=19、额外=1 时覆盖率为 95%，额外行不能抵消缺失；可靠空集为已验证空且比例 null；UNCONFIRMED 只产疑似缺口、不判 EXTRA，整体 expectedCount/coverageRate=null。局部确认缺失与未知同时保留。DECIMAL 不同 scale 按数值比较，非法精度拒绝而非截断；财报版本/同日多事件保留完整键。nullable 空值不误报必填，非法键和来源身份给具体问题，无实际行的通用规则为 N/A/NO_ROWS。已知 FAIL 不被 PASS 或其他规则 UNKNOWN 覆盖；仅展示时舍入百分比。fixture 的可靠集合不被表述为 Tushare 生产基线。
- **Dependencies:** `DATA-INTEGRITY-T01` 的比较/报告合同及 fixture 样例；`DATA-INTEGRITY-T03` 的同快照分批读取与累计预算边界。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、4–6 节；`docs/runbook/data-integrity-rules.md` 第 2.3、3 节；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinition.java`；`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`；T01/T03 的已验收合同和扫描实现。
- **First action:** 将共享设计的集合案例和三条通用规则对应到完整业务键及 fixture 输入，完成 T04 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T05

- **Goal:** 检查任务、每个单元和问题明细可以原子保存、稳定查询并长期保留当时口径。
- **Scope:** 新增三张 `tensor_integrity_check_*` 报告表、`IntegrityCheckRepository` 及持久 JSON；实现任务和计划单元创建、结果与问题的原子提交、历史/结果/问题分页筛选。迁移采用当前可用的 V9；实施时如被占用，使用下一版本并同步共享设计。此项不启动工作线程或改变证券表。
- **Acceptance:** `IntegrityCheckRepositoryIT` 实际验证 submission_id、(check_id, unit_key)、外键、状态、非负计数及日期约束；unit_key 按有序 [apiName,symbol] 规范 JSON SHA-256 生成。结果和问题同事务可见，故障回滚无孤立/半份报告；保存定义/规则快照、原范围、时点、incomplete/issuesComplete，历史查询不按当前规则重算。三种列表总数/页数据同一读取事务；严格采用第 8 节全部筛选、稳定排序、page=1/pageSize=20/max=100、越末页空列表。问题日期过滤针对 issue_date，空日期排最后；不保存 Token、原始响应或堆栈，不新增自动清理。
- **Dependencies:** `DATA-INTEGRITY-T01`，消费报告字段、版本/定义快照及精确 JSON 合同。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 5–8 节；`data-plane/tensor-app/src/main/resources/db/migration/V8__create_download_task_tables.sql`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskJson.java`；`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRepositoryIT.java`；T01 的已验收报告合同。
- **First action:** 核对最新迁移编号，将报告字段与原子写入/查询条件对应到三表，完成 T05 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T06

- **Goal:** 固定用户完整检查范围，以可恢复的幂等请求受理任务，并在超限或队列满时明确拒绝。
- **Scope:** 实现 `IntegrityCheckService` 的校验、规范化、能力快照、计划单元生成、submissionId 重放及有界队列名额预留；装配并校验 `tensor.integrity` 配置，写入配置说明。受理事务完成后入队，工作线程消费由 T07 实现；不扩大或缩小用户范围。
- **Acceptance:** 缺省 apiNames 固定当前全量、空数组/未知接口/未知 JSON 字段拒绝；股票去重，非法代码、反向/非法日期和晚于受理当日的 Tushare endDate 拒绝；本地完全无记录股票仍受理。股票接口按股票建单元，NON_STOCK 每接口一条 symbol=null，缺描述按股票建 UNKNOWN 单元，计划数固定。原请求哈希对象键排序、数组顺序保留；同 ID 同请求返回原任务，不同请求冲突，重放先于当前能力检查，规则升级后仍找回旧报告。首次请求核验 capabilityHash；并发重复只创建一份。股票/天数/单元默认 100/36600/4000，排队 20，等于上限允许，超一项拒绝；队列满不留已受理记录，写失败释放预留。全部配置正数且 workers=1，否则阻止启动；`IntegrityCheckRepositoryIT`/受理专项验证幂等与并发准入。
- **Dependencies:** `DATA-INTEGRITY-T02` 的本地能力、股票规范化和接口口径（含 T01 稳定哈希）；`DATA-INTEGRITY-T05` 的任务/计划单元事务及 submission_id 唯一约束。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 2、7、8 节；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadTaskConfiguration.java`；`docs/runbook/configuration.md`；T02/T05 的已验收能力与持久化实现。
- **First action:** 按“先幂等重放、再能力/范围校验、预留、事务、入队”梳理各失败出口和全部配置边界，完成 T06 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T07

- **Goal:** 用独立单工作线程执行已受理检查，保留错误和未完成范围，正确处理中断与报告失败。
- **Scope:** 新增 `IntegrityCheckRunner` 与后台装配；串联只读快照、通用/来源规则、单元报告提交及进度汇总；实现规则异常隔离、定义再次核验、单元/任务预算和重启中断。不引入消息中间件、通用任务平台或旧快照自动恢复。
- **Acceptance:** `IntegrityCheckRunnerIT` 实际连接 MySQL：每单元目标/参考同一快照，不声称跨单元全库快照；按 ruleId 稳定执行，单规则异常 UNKNOWN/RULE_EXECUTION_FAILED 且其他独立规则继续，确定缺失仍使总体 FAIL。执行前定义变更产生 ERROR/DEFINITION_CHANGED；扫描/预算异常为单元 ERROR，保留已知问题但标 incomplete、coverageRate=null。累计扫描/生成预期键预算 500000、问题 20000、单元 120 秒、任务自运行起 1800 秒，计数达到上限允许、超过停止，时间到 deadline 停止。任务超时/重启为 INTERRUPTED，剩余 NOT_RUN；包含已提交未入队遗留任务。框架/报告写失败为 FAILED；只有所有单元为 COMPLETED/ERROR 才可任务 COMPLETED，进度按已提交单元计算，未计算结论保持 UNKNOWN。
- **Dependencies:** `DATA-INTEGRITY-T03` 的快照、游标及读取预算；`DATA-INTEGRITY-T04` 的规则、比较和聚合；`DATA-INTEGRITY-T05` 的原子报告；`DATA-INTEGRITY-T06` 的固定计划、配置与有界队列。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、5–7 节及“如何测试”；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRunner.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadTaskConfiguration.java`；T03–T06 的已验收实现和 fixture。
- **First action:** 对照共享设计列出任务/单元状态变化、事务边界和预算退出后保存内容，完成 T07 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T08

- **Goal:** Tushare 日/周/月行情能给出有解释依据的候选缺口，对基线不足保持 UNKNOWN。
- **Scope:** 在 T02 已注册的 `tushare.coverage.daily/weekly/monthly@1` 内接入参考校验和候选计算；其他接口继续使用已定日期轴、通用规则及明确的覆盖限制。不新建生产可信基线、不改变下载 BJ 日历策略、不为其他时间序列套用 daily 规则。
- **Acceptance:** `TushareIntegrityRulesTest` 覆盖 SH→SSE、SZ→SZSE，BJ 缺可靠适用日历时 CALENDAR_BASIS_UNPROVEN；日历每自然日恰一条且 is_open=0/1，缺日明确 REFERENCE_INCOMPLETE，不能直接算行情缺失。周线 ISO 周/月线自然月取来源口径下最后开市日；参考可扩完整周期，但目标只含最终标记落入原区间的周期，不固定周五/月末。未结束/未发布周期单列。list_date 仅排除明确上市前候选，停牌仅作线索；生命周期、全天停牌全集、服务边界或发布证据不足时仅 SUSPECTED_MISSING，expectedCount/coverageRate=null，无候选缺口也不 PASS。覆盖节假日、周/月边界、空参考、上市/退市未知、全天/盘中停牌与部分窗口；生产执行不调用来源客户端。
- **Dependencies:** `DATA-INTEGRITY-T02` 的 Tushare 日期轴/依赖及规则注册；`DATA-INTEGRITY-T03` 的受限参考扫描；`DATA-INTEGRITY-T04` 的 UNCONFIRMED 比较、证据和问题聚合。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 3、4、6 节；`docs/runbook/data-integrity-rules.md` 第 3 节；`docs/runbook/data-integrity-explained.md`；`docs/data-template/manifest.json`；`docs/data-template/` 的 daily、weekly、monthly、trade_cal、stock_basic、suspend_d 定义；T02–T04 的已验收来源口径与扫描/比较实现。
- **First action:** 将日/周/月每一步候选推导对应到已存在参考字段和无法证明的条件，完成 T08 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T09

- **Goal:** 前端可以通过完整、稳定的六个 HTTP 端点发现能力、创建检查并查询历史和问题。
- **Scope:** 新增 `IntegrityCheckController`、DTO/装配及错误映射，更新 OpenAPI 与 error-codes。提供 `GET /api/v1/data-sources/{pluginId}/integrity-capabilities`，以及 `/api/v1/integrity-checks` 的 POST/GET、`/{checkId}`、`/{checkId}/results`、`/{checkId}/issues` 的 GET；控制器保持薄层，不重复规则和 SQL。
- **Acceptance:** `IntegrityCheckControllerTest` 覆盖首次 202+Location、幂等 200、SUBMISSION_CONFLICT；新增 INTEGRITY_UNAVAILABLE/INTEGRITY_DEFINITION_CHANGED 为 409，INTEGRITY_CHECK_NOT_FOUND 为 404，INTEGRITY_QUEUE_FULL 为 429，INTEGRITY_LIMIT_EXCEEDED 为 400，其余按设计复用。能力响应分开 localCheckAvailable/downloadAvailability 并给出 limits、哈希及全部 API 口径；详情含原范围、执行进度、结论数量及错误。三个列表严格支持第 8 节全部筛选/排序/分页，无效页数 400、越末页空列表；精确计数为字符串、null 不变。业务资料不足是 200 UNKNOWN 报告，统一 requestId，错误不泄露原始异常；公开文档与实现一致，合同校验通过。
- **Dependencies:** `DATA-INTEGRITY-T02` 的能力发现；`DATA-INTEGRITY-T05` 的历史/结果/问题查询；`DATA-INTEGRITY-T06` 的幂等受理；`DATA-INTEGRITY-T07` 的执行状态和已提交进度。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 2、5、7、8 节；`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadTaskController.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java`；`docs/contracts/openapi-v1.yaml`；`docs/contracts/error-codes.md`；`scripts/verify-contracts.sh`；T02/T05–T07 的已验收服务。
- **First action:** 按六个端点逐一列出 DTO、参数、状态码和统一错误映射，完成 T09 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T10

- **Goal:** 前端准确读取报告，并在提交响应不确定或切换任务时避免重复创建和过期数据覆盖。
- **Scope:** 新增 `integrityChecks.js`、`integrityDtos.js` 及 `useIntegrityCheck.js`，复用 HTTP 错误/requestId 处理；实现不可变提交载荷、submissionId 查回/同 ID 重发、精确数值解析和轮询生命周期。此项不实现页面布局。
- **Acceptance:** 单元测试覆盖六端点参数与全部状态、超安全整数计数、null 与 0 区别；capabilityHash 变化要求重新获取能力并确认口径。请求中锁定提交；响应不确定先按 submissionId 查历史，需要重发仍用原 ID/载荷，明确再次检查才新建 ID。每 2 秒轮询详情和当前可见结果页，同一请求不重叠；终态、路由切换和卸载停止/取消，checkId/request generation 拒绝旧响应。临时查询失败保留数据/requestId、停轮询并可重连；重连只 GET，不自动 POST。
- **Dependencies:** `DATA-INTEGRITY-T09`，消费已验收能力、提交和报告 API/错误合同。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 2、5、8、9 节；`docs/contracts/openapi-v1.yaml`；`control-plane/src/api/http.js`；`control-plane/src/api/downloadTasks.js`；`control-plane/src/api/downloadTaskDtos.js`；`control-plane/src/utils/downloadTaskSubmission.js`；`control-plane/src/composables/useDownloadTask.js`；T09 的已验收完整性 API。
- **First action:** 将完整性 API 字段和提交/轮询状态对应到现有 HTTP 与精确 DTO 工具，完成 T10 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T11

- **Goal:** 用户可以在现有工作区选择完整范围、理解接口口径、提交检查并进入历史报告。
- **Scope:** 新增 `/integrity` 创建/历史页面及职责明确的表单组件，在现有顶部导航添加“数据完整性”，沿用当前样式和通用控件；连接 T10 的能力、提交与历史状态。成功受理及历史条目进入约定详情 URL，详情内容由 T12 实现。
- **Acceptance:** 数据源单选；股票逐个或逗号/空白/换行粘贴并去重计数，不依赖本地股票清单；日期和限额按能力校验；接口按分类默认全选，支持部分选择/全选/清空，清空不可提交。提交前呈现股票数、接口数、日期轴、快照/N/A/未知及依赖说明，缺 Token 但本地可检查仍可提交。提交中禁重复，不确定结果保留原 ID 查回；历史分页和筛选遵循 API，展示执行状态与数据结论，不给跨接口总百分比。相关单元及页面 stub 用例覆盖能力变化、空态和错误；1440/1024/390 布局与键盘可操作，无页面横向溢出。
- **Dependencies:** `DATA-INTEGRITY-T10`，消费能力查询、提交恢复、精确 DTO 和历史 API。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 2、3、9 节；`control-plane/src/layouts/AppLayout.vue`；`control-plane/src/router/index.js`；`control-plane/src/views/DownloadView.vue`；`control-plane/src/components/common/PageHeading.vue`；`control-plane/src/style.css`；T10 的已验收请求/状态实现。
- **First action:** 对照当前顶部导航、通用表单与设计第 9 节，落定创建/历史页区域和异常交互，完成 T11 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T12

- **Goal:** 用户能从报告结论定位到具体股票、接口、日期及完整业务键，重新检查后保留新旧报告。
- **Scope:** 新增 `/integrity/checks/:checkId` 详情、汇总、结果/问题组件及筛选分页，连接轮询和重连；实现从旧报告复制条件到创建页、刷新能力并用新 ID 检查。历史始终展示保存的规则/定义/证据。
- **Acceptance:** 详情可独立刷新/深链接加载；执行状态和数据结论分开，部分进度/ERROR/NOT_RUN/截断明确，FAIL、UNKNOWN、N/A 不互相掩盖。结果一行一个单元，NON_STOCK 保留范围说明，精确计数、null 与 0 正确显示；问题展示主日期、完整键、规则名/版本、relatedDates、类型和依据。日期过滤明确排除未知日期，重置可找回；无日期显示整个范围/日期未知。切换任务/页不被旧响应覆盖，查询失败保留数据并可 GET 重连；再次检查获取当前能力、产生新报告，旧报告不改写。单元及 stub 浏览器测试覆盖全 UNKNOWN/全 N/A、任务完成但 FAIL、空结果、长名称、中断及分页；1440/1024/390 可操作且无页面溢出，状态有文字/图标。
- **Dependencies:** `DATA-INTEGRITY-T10` 的报告 API、精确 DTO、轮询及重连；`DATA-INTEGRITY-T11` 的导航、历史入口与创建表单，用于详情跳转及复制条件。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 5、6、8、9 节；`control-plane/src/router/index.js`；`control-plane/src/views/DownloadTaskView.vue`；`control-plane/src/components/dataset/DatasetPagination.vue`；T10/T11 的已验收请求、轮询和页面实现。
- **First action:** 将保存的报告字段对应到总览、结果行、问题筛选和再次检查入口，完成 T12 专属设计。
- **State evidence:** `None`。

### DATA-INTEGRITY-T13

- **Goal:** 用真实受控数据证明设计的完整用户流程、只读边界、规则扩展及历史可追溯，并完成回归与运维说明。
- **Scope:** 在既有验收 profile 中连接 fixture 与真实 MySQL，完成浏览器闭环和 SQL 核对；补全 `e2e/integrity-checks.spec.js` 集成场景，汇总各项验收证据，更新配置/规则说明与公开合同的最终一致性。此项不引入生产可信基线或在线来源调用。
- **Acceptance:** 真实后端完成“建数据 → 创建检查 → 查看缺失日 → 重查新报告”，可靠 E=20/命中=19/额外=1 的 95% 可从 SQL 复算，整只本地缺失股票在可靠 fixture 下列出全部缺失；生产依据不足仍 UNKNOWN。覆盖共享设计“如何测试”的全部边界及“如何验证”的 10 项结果，包括无 Token/无来源网络调用、证券表前后不变、问题原子提交、预算/重启/提交丢失、定义升级后旧报告不重算。增加测试专用规则只改规则/注册，core 与前端无需新增来源/规则 ID 分支。真实 IT 不得用 skip 计通过；API stub 和真实闭环分别记录。1440/1024/390 覆盖空态、全 UNKNOWN/N/A、超长值及中断，无重复创建/无限轮询/页面溢出；既有下载、数据查看、打包与合同回归通过。文档准确区分首版已实现检查与规则规范中未实现的在线对账等能力。
- **Dependencies:** `DATA-INTEGRITY-T07` 的执行/故障边界及贯通 fixture；`DATA-INTEGRITY-T08` 的生产候选规则与 UNKNOWN 限制；`DATA-INTEGRITY-T09` 的公开 API；`DATA-INTEGRITY-T11` 的创建/历史；`DATA-INTEGRITY-T12` 的问题定位与再次检查。
- **Sources:** 依次完整读取 `docs/task-designs/DATA-INTEGRITY-design.md`；`docs/runbook/data-integrity-rules.md`；`docs/runbook/configuration.md`；`docs/contracts/openapi-v1.yaml`；`docs/contracts/error-codes.md`；`control-plane/e2e/fixture-flow.spec.js`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`；`scripts/verify-contracts.sh`；T01–T12 各专项验收证据。
- **First action:** 将共享设计的测试场景和 10 项验收结果逐项对应到专项证据、真实 fixture 数据与浏览器/SQL 核对步骤，完成 T13 专属设计。
- **State evidence:** `None`。

T13 的目标命令沿用共享设计；Java 21、Node 24 与受控 Docker/MySQL 是执行前提，测试结果必须在实施时记录。`*IT` 是否实际执行以测试报告为准，不能只因命令退出成功就视为集成验收：

```sh
mvn -f data-plane/pom.xml -Dtest='*Integrity*Test,*Integrity*IT' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
npm --prefix control-plane run build
npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js
mvn -f data-plane/pom.xml clean verify
sh scripts/verify-contracts.sh
```

## Risks

- 当前 stock_basic 缺 list_status/delist_date，suspend_d 的 RESPONSE_ONLY 不能证明停牌全集，历史服务/发布边界也不足；首版按已定 UNKNOWN 交付，不能把补足生产可信基线偷偷纳入本项目，也不能因此阻塞通用框架和页面。
- 原始请求、所选股票、日期和接口不能为避限或提高通过率被删除；空数据、未实现、不适用和未完成单元都必须有明确结果，不能只展示通过项。
- 只读快照可能较长；单线程、累计扫描/问题限额和超时必须共同生效。部分统计或问题明细不是完整结果，截断须保留 incomplete/issuesComplete 和 null 覆盖率。
- 报告长期保留会增长；首版只有运维观察，没有自动删除。证据不得包含 Token、任意原始响应或异常堆栈。
- 当前工作区基线包含尚未提交的 Studio/下载改动；后续实现和合并应保留这些变更，避免用旧布局或旧 API 覆盖它们。源工作区继续演进时需要显式核对差异；V9 迁移编号在实施时再次确认。
- 本次只验证工作区复制、看板结构、引用、依赖及 Git 跟踪；没有运行 Java/前端测试或数据库验收，不能据此宣称运行基线或功能实现通过。
