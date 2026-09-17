# 数据完整性检验任务看板

## Project

- **Project ID:** `DATA-INTEGRITY`。
- **Goal:** 按 [数据完整性检验设计 v1](../task-designs/DATA-INTEGRITY-design.md) 实现本地数据检查：选择数据源、股票、日期及接口，保存可追溯报告，并从前端定位具体问题日期和业务键。
- **Scope:** 插件可选能力、固定检查范围、40 个 Tushare 接口口径、只读扫描、通用规则与行情候选缺口、后台检查任务、持久报告、HTTP API、前端创建/历史/详情及受控验收。首版不包含上游在线对账、自动补数、定时检查、跨来源评分、生产基线资料库、规则编辑器、导出或报告自动清理；不改变 SINGLE/RANGE 下载能力。
- **Completion condition:** 13 项任务按各自验收条件完成；共享设计“如何验证”的 10 项结果都有证据；受控真实 MySQL、真实后端 fixture 浏览器闭环、既有功能回归及合同校验通过。任务拆分、API stub 页面或 fixture 的可靠全集不能代替生产 Tushare 覆盖证明。
- **Design authority:** `docs/task-designs/DATA-INTEGRITY-design.md` 是共享功能设计。本板仅拆分责任、顺序、依赖及验收；各任务的详细设计在启动或后继交接时补齐，`Design document` 初始化为 `None`，不把共享设计冒充已完成的任务专属设计。
- **Workspace:** `.worktrees/data-integrity`，分支 `feat/data-integrity`，起点提交 `211094822aa061c2f9217d367cc06f8ddcae3b4e`。2026-09-16 从 `feat/studio-frontend` 工作区复制当时全部 142 个已暂存改动，包含共享设计及 Studio 当前代码；复制后保持暂存，未为基线创建提交。后续原工作区修改不会自动同步。本次仅创建看板并补充目录入口，未开始功能实现。
- **Integration:** 2026-09-16 用户要求将拆分内容合回当前分支 `feat/studio-frontend`；合入范围为本看板和任务交接目录入口，不回写独立工作区中复制的旧代码基线。合入后的任务状态以当前分支本板为准。2026-09-16 用户随后要求在隔离工作区完成 T01，T01 及后续任务状态与交接以 `.worktrees/data-integrity` 内本板为准，当时尚未回写原工作区。2026-09-18 已按用户明确授权集成本地main并完成T13正式合同门禁；根工作区main内本板与保留隔离分支同步后的副本保持一致，未推送远程。

## Workflow

- **Execution:** 串行执行由用户掌握；看板不执行跨任务互斥检查。按 `Order` 分为合同与检查基础（T01–T04）、持久化与执行（T05–T07）、来源规则与 API（T08–T09）、前端与最终验收（T10–T13）。
- **Next-task selection:** 当前任务完成后，选择 `Order` 大于当前任务且最小的未完成任务；不按文件名、依赖形状或临时优先级另选。
- **Successor preparation:** 完成并回填后继任务专属设计后，才写入其 `next-task` 交接并准备为 `READY`；后继设计失败不改变前项已记录的完成状态。任务设计不得改变共享设计的已定口径。
- **Allowed transitions:** `NOT_STARTED -> READY`、`READY -> IN_PROGRESS`、`IN_PROGRESS -> PAUSED`、`PAUSED -> IN_PROGRESS`、`READY -> BLOCKED`、`IN_PROGRESS -> BLOCKED`、`BLOCKED -> READY`、`IN_PROGRESS -> COMPLETED`。
- **Initialization:** T01 为 `READY`，其余为 `NOT_STARTED`，全部 `Handoff=None`。`READY` 表示可以承接任务，不表示已有任务专属设计或已经开始编码；启动实施需明确启动请求及设计就绪。
- **Evidence:** 每项实现同时承担其专项测试；T13 承担整体集成与回归，不替前项补记未经验证的完成。各项 Acceptance 的测试名、命令和预期结果是验收要求；实际已执行结果见各项 State evidence 和链接的验收记录，不能把未执行的后续用例记为通过。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
|---:|---|---|---|---|---|---|
| 1 | DATA-INTEGRITY-T01 | 插件能力、规则与报告合同 | COMPLETED | None | docs/task-designs/DATA-INTEGRITY-T01-design.md | None |
| 2 | DATA-INTEGRITY-T02 | 本地能力发现与 40 接口口径 | COMPLETED | DATA-INTEGRITY-T01 | docs/task-designs/DATA-INTEGRITY-T02-design.md | docs/task-handoffs/DATA-INTEGRITY-T02-handoff.md |
| 3 | DATA-INTEGRITY-T03 | 一致快照与受限只读扫描 | COMPLETED | DATA-INTEGRITY-T01 | docs/task-designs/DATA-INTEGRITY-T03-design.md | docs/task-handoffs/DATA-INTEGRITY-T03-handoff.md |
| 4 | DATA-INTEGRITY-T04 | 精确集合比较、通用规则与 fixture | COMPLETED | DATA-INTEGRITY-T01, DATA-INTEGRITY-T03 | docs/task-designs/DATA-INTEGRITY-T04-design.md | docs/task-handoffs/DATA-INTEGRITY-T04-handoff.md |
| 5 | DATA-INTEGRITY-T05 | 报告表、原子持久化与分页查询 | COMPLETED | DATA-INTEGRITY-T01 | docs/task-designs/DATA-INTEGRITY-T05-design.md | docs/task-handoffs/DATA-INTEGRITY-T05-handoff.md |
| 6 | DATA-INTEGRITY-T06 | 范围固定、幂等受理与队列准入 | COMPLETED | DATA-INTEGRITY-T02, DATA-INTEGRITY-T05 | docs/task-designs/DATA-INTEGRITY-T06-design.md | docs/task-handoffs/DATA-INTEGRITY-T06-handoff.md |
| 7 | DATA-INTEGRITY-T07 | 后台执行、预算与中断处理 | COMPLETED | DATA-INTEGRITY-T03, DATA-INTEGRITY-T04, DATA-INTEGRITY-T05, DATA-INTEGRITY-T06 | docs/task-designs/DATA-INTEGRITY-T07-design.md | docs/task-handoffs/DATA-INTEGRITY-T07-handoff.md |
| 8 | DATA-INTEGRITY-T08 | Tushare 日周月候选缺口规则 | COMPLETED | DATA-INTEGRITY-T02, DATA-INTEGRITY-T03, DATA-INTEGRITY-T04, DATA-INTEGRITY-T07 | docs/task-designs/DATA-INTEGRITY-T08-design.md | docs/task-handoffs/DATA-INTEGRITY-T08-handoff.md |
| 9 | DATA-INTEGRITY-T09 | HTTP API、错误与公开合同 | COMPLETED | DATA-INTEGRITY-T02, DATA-INTEGRITY-T05, DATA-INTEGRITY-T06, DATA-INTEGRITY-T07 | docs/task-designs/DATA-INTEGRITY-T09-design.md | docs/task-handoffs/DATA-INTEGRITY-T09-handoff.md |
| 10 | DATA-INTEGRITY-T10 | 前端请求、精确 DTO 与检查状态 | COMPLETED | DATA-INTEGRITY-T09 | docs/task-designs/DATA-INTEGRITY-T10-design.md | docs/task-handoffs/DATA-INTEGRITY-T10-handoff.md |
| 11 | DATA-INTEGRITY-T11 | 创建检查、口径预览与历史页面 | COMPLETED | DATA-INTEGRITY-T10 | docs/task-designs/DATA-INTEGRITY-T11-design.md | docs/task-handoffs/DATA-INTEGRITY-T11-handoff.md |
| 12 | DATA-INTEGRITY-T12 | 报告详情、问题定位与再次检查 | COMPLETED | DATA-INTEGRITY-T10, DATA-INTEGRITY-T11 | docs/task-designs/DATA-INTEGRITY-T12-design.md | docs/task-handoffs/DATA-INTEGRITY-T12-handoff.md |
| 13 | DATA-INTEGRITY-T13 | 真实 fixture 闭环、回归与文档验收 | COMPLETED | DATA-INTEGRITY-T07, DATA-INTEGRITY-T08, DATA-INTEGRITY-T09, DATA-INTEGRITY-T11, DATA-INTEGRITY-T12 | docs/task-designs/DATA-INTEGRITY-T13-design.md | docs/task-handoffs/DATA-INTEGRITY-T13-handoff.md |

## Task Details

### DATA-INTEGRITY-T01

- **Goal:** 来源插件和核心层使用同一份可选检查、规则、范围、证据与报告合同，规则升级可以被稳定识别。
- **Scope:** 按共享设计第 1、2、5、6 节新增 plugin-api 的 `IntegrityCheckSupport` 与 `integrity/` 不可变合同，以及 core 的 `IntegrityCheckJson` 稳定编码；定义规则/能力版本、精确业务键与计数、任务/单元执行状态和数据结论的边界；在现有 fixture 开关内准备可靠与未知预期集合样例。此项不实现 SQL 扫描、任务调度或页面；fixture 的实际比较在 T04 验收。
- **Acceptance:** 旧 `DataSourcePlugin` 不实现能力时仍保持原合同；STOCK_DATE/STOCK_SNAPSHOT 各有且只有一个 COVERAGE 规则，NON_STOCK 无执行规则；规则描述与实现一致、规则 ID 不重复、依赖列有效。哈希包含有序 API、定义、口径及全部规则版本，不含凭据/时间，版本或定义改变导致哈希改变。报告保留原范围、完整键、问题主日期/关联日期及证据，Long 为十进制字符串、未知为 null、覆盖率采用 6 位 HALF_UP；FAIL > UNKNOWN > WARN > PASS，全 N/A 不得 PASS。`IntegrityPluginContractTest` 覆盖本项合同与哈希，fixture 明确区分 PROVEN/UNCONFIRMED。
- **Dependencies:** `None`。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、2、5、6 节；`docs/runbook/data-integrity-rules.md` 第 1、3 节；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/BatchDownloadSupport.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinition.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskJson.java`；`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`。
- **First action:** 对照现有插件/数据集合同逐项落定共享设计第 1 节的不可变类型、规则校验及 fixture 样例，完成 T01 专属设计并回填本板。
- **State evidence:** 2026-09-16 用户明确要求“在隔离工作区完成数据检验T1任务”；完整阅读共享设计并完成 T01 专属设计，按 `READY -> IN_PROGRESS` 启动，在 `.worktrees/data-integrity` 实施。随后 `IN_PROGRESS -> COMPLETED`：可选能力/不可变合同、完整元数据校验、精确报告 JSON/稳定哈希及 fixture 输入已落地；`IntegrityPluginContractTest` 17 项与完整后端单元回归 1158 项通过（0 失败/错误/跳过），独立审查问题已修复并复查。验收证据：`docs/verification/DATA-INTEGRITY-T01.md`。既有合同门禁要求 clean main，未在本隔离分支通过；SQL/比较/执行器实际行为仍按后续任务验收。

### DATA-INTEGRITY-T02

- **Goal:** 无上游 Token 也能发现已启用插件的本地检查能力，并为全部 40 个 Tushare 接口明确检查口径。
- **Scope:** 扩展 `PluginRegistry` 的独立本地能力查找；接入 `TushareProPlugin` 与来源 `integrity/` 描述/覆盖规则注册；实现 Tushare 股票 trim、大写及六位代码加 SH/SZ/BJ 的规范化。按共享设计第 3 节列出日期轴、股票列、依赖及下载限制，当前无法证明的覆盖返回明确 UNKNOWN；daily/weekly/monthly 的候选推导留给 T08。
- **Acceptance:** 启用但缺 Token 的插件可本地检查；旧插件给出不支持说明，显式停用与重复 ID 仍拒绝，现有下载 `find` 语义不变。本地没有记录的合法股票不被剔除。`TushareIntegrityPoliciesTest` 逐项验证与 manifest/YAML 恰好 40 项对应、所需字段存在，ann_date/end_date/ipo_date/in_date 不被替换；两项快照保留 HISTORY_NOT_STORED，四项 NON_STOCK 保留 N/A，未知规则不伪装 N/A。撤回 RANGE 的接口仍可描述本地检查，描述查询不发网络请求；补齐 `IntegrityPluginContractTest` 的注册/兼容案例。
- **Dependencies:** `DATA-INTEGRITY-T01`，消费可选能力、规则描述校验与稳定能力哈希合同。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1–3、10 节；`docs/data-template/manifest.json`；`docs/data-template/` 中对应接口定义；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`；`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/registry/RegistryTest.java`；T01 的已验收合同。
- **First action:** 按 T02 专属设计先为缺 Token 但启用的可选检查插件编写 findIntegrity 可用、find 仍为空的失败测试，再新增独立入口。
- **State evidence:** 2026-09-16 T01 完成后按 Order 选定本项；已核对 40 个 manifest/YAML 的成员、股票列和日期轴，完成并回填 `docs/task-designs/DATA-INTEGRITY-T02-design.md`，随后写入 next-task 交接，按 `NOT_STARTED -> READY` 准备。2026-09-16 用户明确要求“在隔离工作区完成数据检验任务T2”；完整阅读本项专属设计和交接，沿用 `.worktrees/data-integrity`，按 `READY -> IN_PROGRESS` 启动。基线 RegistryTest、TushareProPluginTest 与 T01 IntegrityPluginContractTest 通过。随后按 `IN_PROGRESS -> COMPLETED` 完成：独立本地能力入口、40 项 Tushare 固定口径/依赖/限制、股票规范化和 UNKNOWN 规则已落地；新增 7 项注册合同及 100 项策略测试通过，完整后端回归 1265 项通过（0 失败/错误/跳过），独立审查无问题。验收记录：`docs/verification/DATA-INTEGRITY-T02.md`。未实施 SQL 扫描、候选推导或后续任务功能。

### DATA-INTEGRITY-T03

- **Goal:** 每个检查单元在同一只读一致快照内安全、完整地读取目标数据与已声明参考数据。
- **Scope:** 新增 `IntegrityReadRepository` 及 `IntegrityContext.scan` 的读取支撑；复用 catalog、SQL 标识符校验和类型绑定；按 COMPOSITE 全物理键或 FINGERPRINT 的 business_key 稳定游标扫描。提供累计行数/时间预算的扫描执行边界，任务级调度由 T07 接入；不复用页面 records 分页，不开放任意 SQL 或证券表写入。
- **Acceptance:** `IntegrityReadRepositoryIT` 实际连接 MySQL，证明 REPEATABLE_READ 下跨页及并发写入结果一致，无重复或遗漏；批大小默认 500，所有目标/参考扫描累计计费，SQL 超时不超过剩余单元预算。非法表/列、未声明参考与跨股票/范围读取被拒绝；DATE/DECIMAL/LONG 按合同精确处理。另查选中股票的空日期行，标记范围无法确定，不归入任意日期/命中数；周月参考范围仅按声明用途扩展。扫描前后证券表内容不变，读取中断不产生伪全量统计。
- **Dependencies:** `DATA-INTEGRITY-T01`，消费范围、读取请求、数据集/依赖声明及上下文合同。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、3、4、7 节；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QuerySqlFactory.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`；T01 的已验收读取合同。
- **First action:** 按 T03 专属设计编写 batchSize=2 的真实 MySQL 复合键跨页/并发快照失败测试，再实现只读连接与受限游标入口。
- **State evidence:** 2026-09-16 T02 完成后按 Order 选定本项；完成并回填 `docs/task-designs/DATA-INTEGRITY-T03-design.md`，固定快照/授权/游标/精确读取/空日期/预算接口及真实 MySQL 验收用例；随后写入 `docs/task-handoffs/DATA-INTEGRITY-T03-handoff.md`，按 `NOT_STARTED -> READY` 准备。2026-09-16 用户明确要求“完成数据检验任务的T3”；完整阅读专属设计与交接，沿用隔离工作区，按 `READY -> IN_PROGRESS` 启动实施。随后按 `IN_PROGRESS -> COMPLETED` 完成：独占只读 REPEATABLE_READ 快照、目标/参考授权、完整物理键游标、精确类型、空日期独立批次和累计预算已实现；30 项专项测试通过（18 unit + 12 真实 MySQL IT），最终完整后端 1283 项通过，全部 0 失败/错误/跳过；独立复审无阻断问题，新增文件已加入 Git。验收记录：`docs/verification/DATA-INTEGRITY-T03.md`。

### DATA-INTEGRITY-T04

- **Goal:** 用完整精确业务键计算缺失与额外记录，并独立输出覆盖、键和必填字段结论。
- **Scope:** 实现 `IntegrityContext.compare`、问题收集与结果聚合，以及 `core.required-fields@1`、`core.business-key@1`、`core.source-identity@1`；接入 T01 的 fixture 可靠/未知/局部证据样例。FINGERPRINT 复用现有 codec；不引入 Tushare 专用分支或额外行情合理性规则。
- **Acceptance:** `IntegrityComparisonTest` 证明可靠 E=20、命中=19、额外=1 时覆盖率为 95%，额外行不能抵消缺失；可靠空集为已验证空且比例 null；UNCONFIRMED 只产疑似缺口、不判 EXTRA，整体 expectedCount/coverageRate=null。局部确认缺失与未知同时保留。DECIMAL 不同 scale 按数值比较，非法精度拒绝而非截断；财报版本/同日多事件保留完整键。nullable 空值不误报必填，非法键和来源身份给具体问题，无实际行的通用规则为 N/A/NO_ROWS。已知 FAIL 不被 PASS 或其他规则 UNKNOWN 覆盖；仅展示时舍入百分比。fixture 的可靠集合不被表述为 Tushare 生产基线。
- **Dependencies:** `DATA-INTEGRITY-T01` 的比较/报告合同及 fixture 样例；`DATA-INTEGRITY-T03` 的同快照分批读取与累计预算边界。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、4–6 节；`docs/runbook/data-integrity-rules.md` 第 2.3、3 节；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinition.java`；`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java`；T01/T03 的已验收合同和扫描实现。
- **First action:** 按 T04 专属设计编写合法 Jan1..21 范围内 E20/命中19/额外1 的失败测试，再实现精确键比较与同步单元入口。
- **State evidence:** 2026-09-16 T03 完成后按 Order 选定本项；核对 T01/T03 已验收合同及实际代码，完成并回填 `docs/task-designs/DATA-INTEGRITY-T04-design.md`，固定比较、三条规则、问题归属/聚合、失败传播与合法 fixture 测试输入；随后写入 `docs/task-handoffs/DATA-INTEGRITY-T04-handoff.md`，按 `NOT_STARTED -> READY` 准备。2026-09-16 用户明确要求“完成数据检验的T4任务，在隔离工作区完成”；完整阅读 T04 专属设计及交接，沿用 `.worktrees/data-integrity`，按 `READY -> IN_PROGRESS` 启动。基线合同/读取单测通过，开始精确比较、通用规则及实际 fixture 验证。随后按 `IN_PROGRESS -> COMPLETED` 完成：精确全键比较、三条 core 规则、同步单元入口、归属校验/问题聚合及预算/异常边界已实现；专项59项、最终完整后端1318项和真实MySQL专项37项均0失败/错误/跳过。真实20/19/1为0.950000且FAIL，未知全集/局部确认及完整版本键按设计保留；错误单元及规则子结果均无整体覆盖率。独立审查发现的问题已修复并复审，无遗留Critical/Important项。验收记录：`docs/verification/DATA-INTEGRITY-T04.md`；新增文件加入Git，工作保留隔离工作区，未合并原分支。

### DATA-INTEGRITY-T05

- **Goal:** 检查任务、每个单元和问题明细可以原子保存、稳定查询并长期保留当时口径。
- **Scope:** 新增三张 `tensor_integrity_check_*` 报告表、`IntegrityCheckRepository` 及持久 JSON；实现任务和计划单元创建、结果与问题的原子提交、历史/结果/问题分页筛选。迁移采用当前可用的 V9；实施时如被占用，使用下一版本并同步共享设计。此项不启动工作线程或改变证券表。
- **Acceptance:** `IntegrityCheckRepositoryIT` 实际验证 submission_id、(check_id, unit_key)、外键、状态、非负计数及日期约束；unit_key 按有序 [apiName,symbol] 规范 JSON SHA-256 生成。结果和问题同事务可见，故障回滚无孤立/半份报告；保存定义/规则快照、原范围、时点、incomplete/issuesComplete，历史查询不按当前规则重算。三种列表总数/页数据同一读取事务；严格采用第 8 节全部筛选、稳定排序、page=1/pageSize=20/max=100、越末页空列表。问题日期过滤针对 issue_date，空日期排最后；不保存 Token、原始响应或堆栈，不新增自动清理。
- **Dependencies:** `DATA-INTEGRITY-T01`，消费报告字段、版本/定义快照及精确 JSON 合同。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 5–8 节；`data-plane/tensor-app/src/main/resources/db/migration/V8__create_download_task_tables.sql`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskJson.java`；`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRepositoryIT.java`；T01 的已验收报告合同。
- **First action:** 按 T05 专属设计先核对 V9 仍可用，再编写真实 MySQL 任务+两个计划单元创建及第二单元失败整体回滚的 RED 测试，然后实现迁移与 create。
- **State evidence:** 2026-09-16 T04 完成后按 Order 选定本项；已读取共享设计第5–8节、T01精确报告合同及现有下载持久化实现，完成并回填 `docs/task-designs/DATA-INTEGRITY-T05-design.md`。设计已明确三表约束、原子事务、存储JSON、精确值与三种列表的完整接口/测试，并经独立就绪审查；随后刷新并链接 `docs/task-handoffs/DATA-INTEGRITY-T05-handoff.md`，按 `NOT_STARTED -> READY` 准备。未启动T05实现或运行其验收。
- **Start evidence:** 2026-09-16 用户明确要求“在隔离工作区完成数据检验任务T5”；已完整读取本项专属设计与交接，沿用 `.worktrees/data-integrity`，按 `READY -> IN_PROGRESS` 启动。V9仍可用，按设计先验证基线和持久化失败测试。
- **Completion evidence:** 2026-09-16 按 `IN_PROGRESS -> COMPLETED` 完成：V9三表、完整计划原子创建、单元及问题批量原子提交、历史精确JSON和三类RR分页已实现。最终专项66项（真实MySQL40+JSON26）、后端单元1344项、分组应用MySQL73项及生产JAR4项通过，均无跳过；独立最终复审无阻断问题。验收与旧下载浏览器回归维护/偶发竞争记录见 `docs/verification/DATA-INTEGRITY-T05.md`。工作保留隔离分支，新增文件加入Git，未实施T06/T07/T09。

### DATA-INTEGRITY-T06

- **Goal:** 固定用户完整检查范围，以可恢复的幂等请求受理任务，并在超限或队列满时明确拒绝。
- **Scope:** 实现 `IntegrityCheckService` 的校验、规范化、能力快照、计划单元生成、submissionId 重放及有界队列名额预留；装配并校验 `tensor.integrity` 配置，写入配置说明。受理事务完成后入队，工作线程消费由 T07 实现；不扩大或缩小用户范围。
- **Acceptance:** 缺省 apiNames 固定当前全量、空数组/未知接口/未知 JSON 字段拒绝；股票去重，非法代码、反向/非法日期和晚于受理当日的 Tushare endDate 拒绝；本地完全无记录股票仍受理。股票接口按股票建单元，NON_STOCK 每接口一条 symbol=null，缺描述按股票建 UNKNOWN 单元，计划数固定。原请求哈希对象键排序、数组顺序保留；同 ID 同请求返回原任务，不同请求冲突，重放先于当前能力检查，规则升级后仍找回旧报告。首次请求核验 capabilityHash；并发重复只创建一份。股票/天数/单元默认 100/36600/4000，排队 20，等于上限允许，超一项拒绝；队列满不留已受理记录，写失败释放预留。全部配置正数且 workers=1，否则阻止启动；`IntegrityCheckRepositoryIT`/受理专项验证幂等与并发准入。
- **Dependencies:** `DATA-INTEGRITY-T02` 的本地能力、股票规范化和接口口径（含 T01 稳定哈希）；`DATA-INTEGRITY-T05` 的任务/计划单元事务及 submission_id 唯一约束。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 2、7、8 节；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadTaskConfiguration.java`；`docs/runbook/configuration.md`；T02/T05 的已验收能力与持久化实现。
- **First action:** 按已完成的 T06 专属设计编写 IntegrityCheckServiceTest：同 submissionId/原请求在当前插件变化或队列满时仍返回旧任务，并断言不查询能力、不创建、不再次入队；观察失败后实现服务入口。
- **State evidence:** 2026-09-16 用户明确要求“在隔离区完成数据检验任务的T6”；完整阅读本项设计与交接，复用 `.worktrees/data-integrity`，按 `READY -> IN_PROGRESS` 启动。2026-09-16 T05 完成后按 Order 选定本项；核对 T02 本地能力与 T05 实际仓库/JSON，完成并回填 `docs/task-designs/DATA-INTEGRITY-T06-design.md`，明确原请求先重放、全量快照/子范围、来源日期扩展、队列预留生命周期、配置及并发失败验收。随后写入并链接 `docs/task-handoffs/DATA-INTEGRITY-T06-handoff.md`，按 `NOT_STARTED -> READY` 准备；未启动实现或运行 T06 验收。

- **Completion evidence:** 2026-09-16 按 `IN_PROGRESS -> COMPLETED`：范围固定/全量能力快照、原请求先重放、原子计划与有界预留队列、来源上海日校验和十项配置已完成。后端1385项、前端609项、真实MySQL受理/存储29项、真实应用启动1项通过，均0失败/错误/跳过；独立审查发现的数值/字符串请求hash碰撞已用RED/GREEN修复并复查。完整证据 `docs/verification/DATA-INTEGRITY-T06.md`；保留隔离区及混合暂存基线，未启动T07执行器或T09路由。

### DATA-INTEGRITY-T07

- **Goal:** 用独立单工作线程执行已受理检查，保留错误和未完成范围，正确处理中断与报告失败。
- **Scope:** 新增 `IntegrityCheckRunner` 与后台装配；串联只读快照、通用/来源规则、单元报告提交及进度汇总；实现规则异常隔离、定义再次核验、单元/任务预算和重启中断。不引入消息中间件、通用任务平台或旧快照自动恢复。
- **Acceptance:** `IntegrityCheckRunnerIT` 实际连接 MySQL：每单元目标/参考同一快照，不声称跨单元全库快照；按 ruleId 稳定执行，单规则异常 UNKNOWN/RULE_EXECUTION_FAILED 且其他独立规则继续，确定缺失仍使总体 FAIL。执行前定义变更产生 ERROR/DEFINITION_CHANGED；扫描/预算异常为单元 ERROR，保留已知问题但标 incomplete、coverageRate=null。累计扫描/生成预期键预算 500000、问题 20000、单元 120 秒、任务自运行起 1800 秒，计数达到上限允许、超过停止，时间到 deadline 停止。任务超时/重启为 INTERRUPTED，剩余 NOT_RUN；包含已提交未入队遗留任务。框架/报告写失败为 FAILED；只有所有单元为 COMPLETED/ERROR 才可任务 COMPLETED，进度按已提交单元计算，未计算结论保持 UNKNOWN。
- **Dependencies:** `DATA-INTEGRITY-T03` 的快照、游标及读取预算；`DATA-INTEGRITY-T04` 的规则、比较和聚合；`DATA-INTEGRITY-T05` 的原子报告；`DATA-INTEGRITY-T06` 的固定计划、配置与有界队列。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 1、5–7 节及“如何测试”；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRunner.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadTaskConfiguration.java`；T03–T06 的已验收实现和 fixture。
- **First action:** 按已完成的 T07 专属设计编写 IntegrityCheckRunnerIT：用 T06 受理多单元 fixture，断言任务完成但缺失使 overallStatus=FAIL，进度与具体问题可从 T05 查询；观察缺少 Runner 的失败后实施。
- **State evidence:** 2026-09-16 T06 已完成后按 Order 选定本项；完成并链接 `docs/task-designs/DATA-INTEGRITY-T07-design.md`，核对 T03–T06 实际接口，明确启动/关闭受理门禁、证券扫描与报告仓库错误分层、恢复保留已有读取时点。独立设计复查确认可实施；写入并链接 `docs/task-handoffs/DATA-INTEGRITY-T07-handoff.md` 后按 `NOT_STARTED -> READY` 准备。未启动实现或宣称 T07 测试通过。 2026-09-16 用户明确要求“在隔离区完成数据检验T7”，已完整读取专属设计和交接，READY→IN_PROGRESS。
- **Completion evidence:** 2026-09-16 按 IN_PROGRESS→COMPLETED 完成：独立单worker与受理门禁、固定计划执行/再次核验、来源参考计划、单元/任务预算、原子状态/进度、启动和关闭中断已落地。完整后端1399项、真实MySQL完整性组71项、生命周期/装配/真实生产启动验收全部通过，0失败/错误/跳过；两项独立审查问题经RED→GREEN修复，最终规格与质量审查无遗留Critical/Important项。新增文件已加入Git，隔离区与原暂存基线保留，不合并原工作区。证据：`docs/verification/DATA-INTEGRITY-T07.md`。

### DATA-INTEGRITY-T08

- **Goal:** Tushare 日/周/月行情能给出有解释依据的候选缺口，对基线不足保持 UNKNOWN。
- **Scope:** 在 T02 已注册的 `tushare.coverage.daily/weekly/monthly@1` 占位规则上接入参考校验和候选计算；依共享设计第6节将这三条规则及对应能力版本升为 `2`，其余接口保持 `1`；其他接口继续使用已定日期轴、通用规则及明确的覆盖限制。不新建生产可信基线、不改变下载 BJ 日历策略、不为其他时间序列套用 daily 规则。
- **Acceptance:** 仅三条行情规则/能力升级为2且完整hash改变；`TushareIntegrityRulesTest` 覆盖 SH→SSE、SZ→SZSE，BJ 缺可靠适用日历时 CALENDAR_BASIS_UNPROVEN；日历每自然日恰一条且 is_open=0/1，缺日明确 REFERENCE_INCOMPLETE，不能直接算行情缺失。周线 ISO 周/月线自然月取来源口径下最后开市日；参考可扩完整周期，但目标只含最终标记落入原区间的周期，不固定周五/月末。未结束/未发布周期单列。list_date 仅排除明确上市前候选，停牌仅作线索；生命周期、全天停牌全集、服务边界或发布证据不足时仅 SUSPECTED_MISSING，expectedCount/coverageRate=null，无候选缺口也不 PASS。覆盖节假日、周/月边界、空参考、上市/退市未知、全天/盘中停牌与部分窗口；生产执行不调用来源客户端。
- **Dependencies:** `DATA-INTEGRITY-T02` 的 Tushare 日期轴/依赖及规则注册；`DATA-INTEGRITY-T03` 的受限参考扫描；`DATA-INTEGRITY-T04` 的 UNCONFIRMED 比较、证据和问题聚合；`DATA-INTEGRITY-T07` 的来源参考hook、固定许可装配和实际执行。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 3、4、6 节；`docs/runbook/data-integrity-rules.md` 第 3 节；`docs/runbook/data-integrity-explained.md`；`docs/data-template/manifest.json`；`docs/data-template/` 的 daily、weekly、monthly、trade_cal、stock_basic、suspend_d 定义；T02–T04 的已验收来源口径与扫描/比较实现；T07 的 `IntegrityCheckSupport.integrityReferenceReads`、`IntegrityReadPlanner` 与 `IntegrityCheckRunner`。
- **First action:** 按已完成专属设计编写 weekly 周五闭市、周四最后开市的候选完整键 RED 用例，再实现三规则与来源参考 hook。
- **State evidence:** 2026-09-16 T07 已COMPLETED后按Order选择本项。完成并回填 `docs/task-designs/DATA-INTEGRITY-T08-design.md`，独立就绪复查确认可实施；同步修正看板三规则版本演进（1→2）和实际消费的T07依赖，保留共享设计判定口径。写入并链接 `docs/task-handoffs/DATA-INTEGRITY-T08-handoff.md` 后NOT_STARTED→READY，不启动实现。

- **Start evidence:** 2026-09-17 用户明确要求“在隔离区完成数据校验T8任务”；完整读取本项专属设计和入口交接，在既有 `.worktrees/data-integrity` 执行 READY→IN_PROGRESS，保留混合暂存基线。

- **Completion evidence:** 2026-09-17 在既有隔离区完成 IN_PROGRESS→COMPLETED。三条行情规则/能力升2并接入受限本地参考，完整周期最后开市日及原范围约束、日历缺损、未结束周期、上市/停牌线索均按专属设计落地；生产覆盖保持UNKNOWN/null，只有SUSPECTED_MISSING。专项119项、默认后端回归1411项、真实MySQL组30项及最终代码生产应用验收通过，均0失败/错误/跳过；同一Maven流程前端609项和构建通过。独立规格/质量审查批准，重复日历断言已加强，公开方法白名单回归遗漏已修复。证据见 `docs/verification/DATA-INTEGRITY-T08.md`；后续JAR阶段误选的命令失败单独记录，不计作通过。新文件纳入Git，保留隔离区，不混合提交或合回主工作区。

### DATA-INTEGRITY-T09

- **Goal:** 前端可以通过完整、稳定的六个 HTTP 端点发现能力、创建检查并查询历史和问题。
- **Scope:** 新增 `IntegrityCheckController`、DTO/装配及错误映射，更新 OpenAPI 与 error-codes。提供 `GET /api/v1/data-sources/{pluginId}/integrity-capabilities`，以及 `/api/v1/integrity-checks` 的 POST/GET、`/{checkId}`、`/{checkId}/results`、`/{checkId}/issues` 的 GET；控制器保持薄层，不重复规则和 SQL。
- **Acceptance:** `IntegrityCheckControllerTest` 覆盖首次 202+Location、幂等 200、SUBMISSION_CONFLICT；新增 INTEGRITY_UNAVAILABLE/INTEGRITY_DEFINITION_CHANGED 为 409，INTEGRITY_CHECK_NOT_FOUND 为 404，INTEGRITY_QUEUE_FULL 为 429，INTEGRITY_LIMIT_EXCEEDED 为 400，其余按设计复用。能力响应分开 localCheckAvailable/downloadAvailability 并给出 limits、哈希及全部 API 口径；详情含原范围、执行进度、结论数量及错误。三个列表严格支持第 8 节全部筛选/排序/分页，无效页数 400、越末页空列表；精确计数为字符串、null 不变。业务资料不足是 200 UNKNOWN 报告，统一 requestId，错误不泄露原始异常；公开文档与实现一致，合同校验通过。
- **Dependencies:** `DATA-INTEGRITY-T02` 的能力发现；`DATA-INTEGRITY-T05` 的历史/结果/问题查询；`DATA-INTEGRITY-T06` 的幂等受理；`DATA-INTEGRITY-T07` 的执行状态和已提交进度。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 2、5、7、8 节；`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadTaskController.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java`；`docs/contracts/openapi-v1.yaml`；`docs/contracts/error-codes.md`；`scripts/verify-contracts.sh`；T02/T05–T07 的已验收服务。
- **First action:** 按已完成专属设计编写合法原请求POST的202+Location+精确回执RED，确认当前404后实现六端点和绑定/映射。
- **State evidence:** 2026-09-17 按 `IN_PROGRESS -> COMPLETED` 验收：六HTTP端点、严格绑定/分页/筛选、不可变原请求重放、持久历史映射、精确字符串计数/null、安全错误和独立OpenAPI/Schema合同已落地。最终HTTP/合同专项113项（含真实MySQL8.4.6生产应用IT）、完整Java回归1457项及前端609项全部通过，0失败/错误/跳过，生产构建通过。架构门禁保持不变，查询经现有Service委托仓库；最终独立复审无Critical/Important问题。验收：`docs/verification/DATA-INTEGRITY-T09.md`。新增文件已加入Git，既有暂存基线保留，未提交或合并。clean-main发布合同门禁留待T13。启动与入口历史：2026-09-17 用户明确要求“在隔离工作区完成数据检验T9”；已完整阅读专属设计和入口交接，在既有 `.worktrees/data-integrity` 将READY→IN_PROGRESS，保留原交接作为入口依据。基线专项测试通过；实现与验收按 `docs/superpowers/plans/2026-09-17-data-integrity-t09.md` 执行。此前准备证据：2026-09-17 T08记录COMPLETED后按Order=9选择本项。完整设计固定六端点DTO、严格参数、幂等和历史边界、错误映射、合同与真实应用测试，已回填 `docs/task-designs/DATA-INTEGRITY-T09-design.md`；自查无未解决的依赖冲突和实施口径缺口。写入并链接 `docs/task-handoffs/DATA-INTEGRITY-T09-handoff.md` 后NOT_STARTED→READY，未启动T09实现。

### DATA-INTEGRITY-T10

- **Goal:** 前端准确读取报告，并在提交响应不确定或切换任务时避免重复创建和过期数据覆盖。
- **Scope:** 新增 `integrityChecks.js`、`integrityDtos.js` 及 `useIntegrityCheck.js`，复用 HTTP 错误/requestId 处理；实现不可变提交载荷、submissionId 查回/同 ID 重发、精确数值解析和轮询生命周期。此项不实现页面布局。
- **Acceptance:** 单元测试覆盖六端点参数与全部状态、超安全整数计数、null 与 0 区别；capabilityHash 变化要求重新获取能力并确认口径。请求中锁定提交；响应不确定先按 submissionId 查历史，需要重发仍用原 ID/载荷，明确再次检查才新建 ID。每 2 秒轮询详情和当前可见结果页，同一请求不重叠；终态、路由切换和卸载停止/取消，checkId/request generation 拒绝旧响应。临时查询失败保留数据/requestId、停轮询并可重连；重连只 GET，不自动 POST。
- **Dependencies:** `DATA-INTEGRITY-T09`，消费已验收能力、提交和报告 API/错误合同。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 2、5、8、9 节；`docs/contracts/openapi-v1.yaml`；`control-plane/src/api/http.js`；`control-plane/src/api/downloadTasks.js`；`control-plane/src/api/downloadTaskDtos.js`；`control-plane/src/utils/downloadTaskSubmission.js`；`control-plane/src/composables/useDownloadTask.js`；T09 的已验收完整性 API。
- **First action:** 按已完成专属设计，在integrityDtos.spec.js用T09示例编写Long.MAX_VALUE字符串转BigInt、null保留及数字token拒绝的RED，再实现精确DTO解析。
- **State evidence:** 2026-09-17 按 `IN_PROGRESS -> COMPLETED` 验收：六API、严格精确DTO、五错误、不可变提交/能力确认/会话恢复及2秒GET生命周期已落地。Node24.15.0专项4文件66项、完整前端40文件663项全部通过，0失败/跳过，生产构建成功；原生ESM导入通过。状态审查的两项存储P2修复后，最终独立审查Spec/Quality均PASS，无遗留问题。验收：`docs/verification/DATA-INTEGRITY-T10.md`。保留隔离区与既有暂存基线，新增文件加入Git，未提交或合并。启动与入口历史：2026-09-17 用户明确要求“在隔离工作区完成数据检验任务T10”；完整阅读专属设计和入口交接，在既有隔离工作区按 `READY -> IN_PROGRESS` 启动。实施计划：`docs/superpowers/plans/2026-09-17-data-integrity-t10.md`；保留既有暂存基线。此前准备证据：2026-09-17 T09记录COMPLETED后按Order=10选择本项，观察状态NOT_STARTED。核对T09六端点/schema/15示例/错误及现有前端工具，完成并回填 `docs/task-designs/DATA-INTEGRITY-T10-design.md`，固定六封装、精确DTO、能力确认、不可变提交/查回及停止/重连轮询的接口、失败规则与测试。直接依赖无未解决冲突。随后写入并链接 `docs/task-handoffs/DATA-INTEGRITY-T10-handoff.md`，按 `NOT_STARTED -> READY` 准备；未开始T10实现。

### DATA-INTEGRITY-T11

- **Goal:** 用户可以在现有工作区选择完整范围、理解接口口径、提交检查并进入历史报告。
- **Scope:** 新增 `/integrity` 创建/历史页面及职责明确的表单组件，在现有顶部导航添加“数据完整性”，沿用当前样式和通用控件；连接 T10 的能力、提交与历史状态。成功受理及历史条目进入约定详情 URL，详情内容由 T12 实现。
- **Acceptance:** 数据源单选；股票逐个或逗号/空白/换行粘贴并去重计数，不依赖本地股票清单；日期和限额按能力校验；接口按分类默认全选，支持部分选择/全选/清空，清空不可提交。提交前呈现股票数、接口数、日期轴、快照/N/A/未知及依赖说明，缺 Token 但本地可检查仍可提交。提交中禁重复，不确定结果保留原 ID 查回；历史分页和筛选遵循 API，展示执行状态与数据结论，不给跨接口总百分比。相关单元及页面 stub 用例覆盖能力变化、空态和错误；1440/1024/390 布局与键盘可操作，无页面横向溢出。
- **Dependencies:** `DATA-INTEGRITY-T10`，消费能力查询、提交恢复、精确 DTO 和历史 API。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 2、3、9 节；`control-plane/src/layouts/AppLayout.vue`；`control-plane/src/router/index.js`；`control-plane/src/views/DownloadView.vue`；`control-plane/src/components/common/PageHeading.vue`；`control-plane/src/style.css`；T10 的已验收请求/状态实现。
- **First action:** 按完整专属设计，在integrityForm.spec.js编写合法但本地无记录股票保留、大小写去重和限额等于/超过边界的RED，再实现表单纯校验与页面。
- **State evidence:** 2026-09-17 按 `IN_PROGRESS -> COMPLETED` 验收：创建/预览/历史、导航与最小报告入口已落地，原范围/能力确认、无 Token、本地无记录股票、原 ID 恢复、精确历史分页和三宽度键盘操作均有证据。Node24.15.0 完整前端46文件/696项、Chromium stub14项及生产构建通过，0失败/跳过；独立审查两项P2以RED/GREEN修复，最终Spec/Quality均PASS。验收：`docs/verification/DATA-INTEGRITY-T11.md`。保留隔离区与既有暂存基线，新增文件加入Git，未提交/合并。启动历史：2026-09-17 用户明确要求“在隔离工作区完成数据校验任务T11”；完整阅读专属设计与交接，在既有 `.worktrees/data-integrity` 按 `READY -> IN_PROGRESS` 启动。Node24.15.0 基线40文件/663项通过。实施计划 `docs/superpowers/plans/2026-09-17-data-integrity-t11.md`，保留原暂存基线。此前准备：2026-09-17 T10记录COMPLETED后按Order=11选择本项，观察状态NOT_STARTED。完成并回填 `docs/task-designs/DATA-INTEGRITY-T11-design.md`：固定创建/预览/历史与导航、能力确认/恢复、原范围与日期/限额校验、BigInt分页、无overallStatus的历史结论入口、组件职责和stub/响应式验收；直接依赖无未解决冲突。写入并链接 `docs/task-handoffs/DATA-INTEGRITY-T11-handoff.md` 后按 `NOT_STARTED -> READY` 准备；未开始T11页面实现。

### DATA-INTEGRITY-T12

- **Completion evidence:** 2026-09-17 按 `IN_PROGRESS -> COMPLETED` 验收：已保存报告总览、结果/问题筛选分页、精确统计与完整键、GET重连和复制范围再次检查全部落地；新旧报告独立、pending恢复优先、失效来源替换/非法编号/迟到读取边界已验证。Node24.15.0 最终52文件/740项前端测试、39项Chromium API stub（35.0秒）和生产构建通过，0失败/跳过；1440/1024/390可操作且无文档横溢，13张截图已检查。独立审查4项经两轮RED/GREEN修复并全部ADDRESSED，最终集成审查无遗留问题。证据：`docs/verification/DATA-INTEGRITY-T12.md`。新增文件加入Git，保留既有暂存基线与隔离工作区，未提交/合并；真实MySQL/fixture及clean-main合同门禁属于T13。

- **Start evidence:** 2026-09-17 用户明确要求“在隔离工作区完成数据检验任务的T12”；完整阅读专属设计与入口交接，核对现有隔离工作区后按 READY→IN_PROGRESS 启动。Node24.15.0 基线46文件/696项全部通过。保留既有暂存基线，不提交混合基线或合并。实施计划：`docs/superpowers/plans/2026-09-17-data-integrity-t12.md`。

- **Goal:** 用户能从报告结论定位到具体股票、接口、日期及完整业务键，重新检查后保留新旧报告。
- **Scope:** 新增 `/integrity/checks/:checkId` 详情、汇总、结果/问题组件及筛选分页，连接轮询和重连；实现从旧报告复制条件到创建页、刷新能力并用新 ID 检查。历史始终展示保存的规则/定义/证据。
- **Acceptance:** 详情可独立刷新/深链接加载；执行状态和数据结论分开，部分进度/ERROR/NOT_RUN/截断明确，FAIL、UNKNOWN、N/A 不互相掩盖。结果一行一个单元，NON_STOCK 保留范围说明，精确计数、null 与 0 正确显示；问题展示主日期、完整键、规则名/版本、relatedDates、类型和依据。日期过滤明确排除未知日期，重置可找回；无日期显示整个范围/日期未知。切换任务/页不被旧响应覆盖，查询失败保留数据并可 GET 重连；再次检查获取当前能力、产生新报告，旧报告不改写。单元及 stub 浏览器测试覆盖全 UNKNOWN/全 N/A、任务完成但 FAIL、空结果、长名称、中断及分页；1440/1024/390 可操作且无页面溢出，状态有文字/图标。
- **Dependencies:** `DATA-INTEGRITY-T10` 的报告 API、精确 DTO、轮询及重连；`DATA-INTEGRITY-T11` 的导航、历史入口与创建表单，用于详情跳转及复制条件。
- **Sources:** 依次读取 `docs/task-designs/DATA-INTEGRITY-design.md` 第 5、6、8、9 节；`control-plane/src/router/index.js`；`control-plane/src/views/DownloadTaskView.vue`；`control-plane/src/components/dataset/DatasetPagination.vue`；T10/T11 的已验收请求、轮询和页面实现。
- **First action:** 按完整专属设计，在 `control-plane/src/utils/integrityReport.spec.js` 为最小未实现骨架编写 Long.MAX_VALUE 精确显示、null/0 区分和 `0.999999→99.9999%` 的行为 RED，观察断言失败后实现报告格式化。
- **State evidence:** 2026-09-17 T11记录COMPLETED后按Order=12选择本项，观察状态NOT_STARTED。核对T10精确DTO/报告API/轮询及T11创建/历史/路由，完成并先回填 `docs/task-designs/DATA-INTEGRITY-T12-design.md`：固定独立报告结构、保存口径、精确显示、结果/问题筛选分页、GET重连、复制范围与pending优先、失败/竞态规则及测试。直接依赖无未解决冲突。随后写入并链接 `docs/task-handoffs/DATA-INTEGRITY-T12-handoff.md`，按 `NOT_STARTED -> READY` 准备。T11最终46文件/696项、14项浏览器stub、构建和独立复审通过；未开始T12实现。

### DATA-INTEGRITY-T13

- **Goal:** 用真实受控数据证明设计的完整用户流程、只读边界、规则扩展及历史可追溯，并完成回归与运维说明。
- **Scope:** 在既有验收 profile 中连接 fixture 与真实 MySQL，完成浏览器闭环和 SQL 核对；补全 `e2e/integrity-checks.spec.js` 集成场景，汇总各项验收证据，更新配置/规则说明与公开合同的最终一致性。此项不引入生产可信基线或在线来源调用。
- **Acceptance:** 真实后端完成“建数据 → 创建检查 → 查看缺失日 → 重查新报告”，可靠 E=20/命中=19/额外=1 的 95% 可从 SQL 复算，整只本地缺失股票在可靠 fixture 下列出全部缺失；生产依据不足仍 UNKNOWN。覆盖共享设计“如何测试”的全部边界及“如何验证”的 10 项结果，包括无 Token/无来源网络调用、证券表前后不变、问题原子提交、预算/重启/提交丢失、定义升级后旧报告不重算。增加测试专用规则只改规则/注册，core 与前端无需新增来源/规则 ID 分支。真实 IT 不得用 skip 计通过；API stub 和真实闭环分别记录。1440/1024/390 覆盖空态、全 UNKNOWN/N/A、超长值及中断，无重复创建/无限轮询/页面溢出；既有下载、数据查看、打包与合同回归通过。文档准确区分首版已实现检查与规则规范中未实现的在线对账等能力。
- **Dependencies:** `DATA-INTEGRITY-T07` 的执行/故障边界及贯通 fixture；`DATA-INTEGRITY-T08` 的生产候选规则与 UNKNOWN 限制；`DATA-INTEGRITY-T09` 的公开 API；`DATA-INTEGRITY-T11` 的创建/历史；`DATA-INTEGRITY-T12` 的问题定位与再次检查。
- **Sources:** 依次完整读取 `docs/task-designs/DATA-INTEGRITY-design.md`；`docs/runbook/data-integrity-rules.md`；`docs/runbook/configuration.md`；`docs/contracts/openapi-v1.yaml`；`docs/contracts/error-codes.md`；`control-plane/e2e/fixture-flow.spec.js`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java`；`scripts/verify-contracts.sh`；T01–T12 各专项验收证据。
- **First action:** 按完整专属设计在真实MySQL/HTTP的 `IntegrityFixtureFlowIT` 构造PROVEN_EXTRA Jan1..19+21，请求Jan1..21；先观察可靠20/19/1/95%断言在当前UNKNOWN规则下的行为RED，再实现acceptance fixture版本2与受控窗口。
- **State evidence:** 2026-09-17 先记录T12 COMPLETED，再按Order=13选定本项并观察NOT_STARTED。完成并回填 `docs/task-designs/DATA-INTEGRITY-T13-design.md`：明确PROVEN_EXTRA合法可靠窗口、20/19/1 SQL复算、空/未知场景、acceptance规则版本2/3与扩展、真实HTTP/浏览器、自有MySQL/JVM、四套既有回归、十项证据与clean-main门禁前提；直接依赖约束无冲突。随后写入并链接 `docs/task-handoffs/DATA-INTEGRITY-T13-handoff.md`，按NOT_STARTED→READY准备。2026-09-17 用户明确要求“在隔离工作区中完成数据检验T13”；完整读取专属设计与交接，沿用 `.worktrees/data-integrity`，按 READY → IN_PROGRESS 启动。保留当前混合暂存基线，最终验收仍须记录已授权干净 main 门禁结果。 2026-09-17 隔离实施及验收完成：Java完整性专项441项（含125真实IT）及最终HTTP修复1项、前端740项、stub39项、真实五套浏览器70项、acceptance1469项和production1466项全部通过，0失败/错误/跳过；SQL95%→100%、证券前后不变、上游0及同库2→3历史已核对。最终独立审查Spec/Quality APPROVED，新增问题0。详情见 `docs/verification/DATA-INTEGRITY-T13.md`。正式合同命令仍exit1 `M14-T04 failed: branch`；按专属设计第6节先写入同路径pause交接，再记录 IN_PROGRESS → BLOCKED。唯一解除条件为已授权代码一致的clean committed main及正式合同门禁成功；当时未提交/合并/推送，T01–T12状态保留。

- **Synchronization history:** 用户随后要求“把远程main的代码拉到数据检验隔离工作区，然后继续完成T13”。已同步 `origin/main@5aaf6ad`、解决25处冲突并通过独立合并审查；恢复stash `ee0c3d3b52f576e5bb52459caff1fc3ae6ca1707` 保留。代码提交 `a7deb7d` 与607项被测输入清单一致；本轮前端738项、stub39项、Java专项441项（125真实IT）、production1466项、acceptance1469项、真实浏览器70项全部通过。证据：`docs/verification/data-integrity-t13/main-sync/README.md`。本地main快进合并被自动审批拒绝，要求用户明确授权311文件默认分支变更，授权待答复；main仍为 `5aaf6ad`，未推送。已刷新同路径pause交接；T13保持BLOCKED，未记录解除或完成。

- **BLOCKED → READY:** 2026-09-18 用户明确“授权本地合并并继续完成 T13”。真实main已快进集成到1e3b039，精确元数据门禁清单修复提交6c3c13f经限定复审通过；正式main合同门禁exit0，metadata42/schema47/package4、前端738及11拒绝自检通过。607项归档输入与集成提交一致，606项与既有被测清单相同，仅门禁脚本同步上游新增测试。解除证据：`docs/verification/data-integrity-t13/main-sync/contract-summary.json`。

- **READY → IN_PROGRESS:** 依据同一明确继续完成T13的请求，读取原pause交接并保留历史，恢复最终结果归档、看板更新及本地工作区同步。

- **IN_PROGRESS → COMPLETED:** 十项结果与十五条边界已有隔离区实测证据；正式clean committed main合同门禁现已通过，集成SHA6c3c13f和输入一致性已登记，门禁修复复审无Critical/Important/Minor，全部自有门禁容器已清理。既有Java441（125真实IT）、前端738、stub39、真实浏览器70、acceptance1469及production1466结果继续由未变产品/测试输入支持。完整验收：`docs/verification/DATA-INTEGRITY-T13.md`。按最新授权仅本地合并，未推送；保留隔离工作区和恢复stash。

- **Project completion:** T13为最后一项预定义任务，原定T01–T13范围已完成；无后继任务，不创建新的handoff。Tushare可靠生产全集、在线对账等仍属于既定范围之外。

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
- 初始工作区含未提交的Studio/下载基线；已于2026-09-17同步远程main并保留最新布局/API，2026-09-18授权集成本地main。恢复stash和隔离工作区保留；后续改动仍须核对来源，避免覆盖既有功能。
- 初始化阶段的历史证据仅验证工作区复制、看板结构、引用、依赖及 Git 跟踪；当时未运行 Java/前端测试或数据库验收。后续运行结果以各任务 State evidence 和专项验收记录为准。
