# ISSUE-018 Follow-up Issues Task Board

## Project

- **Project ID:** `ISSUE-018-followups`。
- **Goal:** 按用户 2026-09-13“把这些问题分成多个issue，然后挨个解决，过程中可以找我决策”的要求，消除 T14 当前剩余缺口。
- **Scope:** ISSUE-019～025 各自负责一个不重叠接口集合，3 + 1 + 3 + 5 + 4 + 3 + 11 = 30；ISSUE-026 汇总真实 RANGE TASK / SQL 和最终验收。四类问题（完整性、语义、样本、任务验收）作为分析分类，不重复计算接口。
- **Completion condition:** 本看板八项均完成，并且 T14 / T13 原验收及母 issue 关闭条件有实际证据。若用户修订范围，先记录精确决定与原目标差异，不能把排除项写成已验证。
- **Authority:** 本看板只管理新建 ISSUE-019～026 的顺序、状态、设计及交接；[原看板](ISSUE-018-task-board.md)继续唯一管理 T01～T14，T13 / T14 的 BLOCKED 不因新 issue 创建而改变。
- **Baseline:** 40 接口 / 4 AVAILABLE / 30 NEEDS_VERIFICATION / 6 SINGLE_ONLY；8 轮 / 475 case，累计 411 次 Tushare 请求。已有 74 个 SINGLE 任务和四接口 25 个 RANGE 任务通过。

## Workflow

- **Execution:** 按用户要求串行处理。初始化第一项 READY，其余 NOT_STARTED；用户已授权逐项实施，但具体执行仍以完成设计、明确决策和固定输入为前提。
- **Next-task selection:** 当前 issue 完成后选择 Order 更大的未完成项中最小者；未解决的决策不靠跳过 issue 自动视为解除。
- **Successor preparation:** 完成并链接后继专属设计，再写 next-task 交接与准备 READY；缺设计事实不生成占位交接。
- **Allowed transitions:** NOT_STARTED -> READY、READY -> IN_PROGRESS、IN_PROGRESS -> PAUSED、PAUSED -> IN_PROGRESS、READY -> BLOCKED、IN_PROGRESS -> BLOCKED、BLOCKED -> READY、IN_PROGRESS -> COMPLETED。
- **Evidence ownership:** 新证据追加到现有 T14 运行登记和唯一验收索引，标出负责的新 issue；不复制旧 PASS 建造新 run。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | ISSUE-019 | 确认三接口限量规则并补齐来源证据 | COMPLETED | None | [设计](../../task-designs/ISSUE-019-design.md) | None |
| 2 | ISSUE-020 | 厘清主营业务构成默认分类与数量限制 | COMPLETED | None | [设计](../../task-designs/ISSUE-020-design.md) | None |
| 3 | ISSUE-021 | 验证交易所日历与交易日下载 | COMPLETED | None | [设计](../../task-designs/ISSUE-021-design.md) | None |
| 4 | ISSUE-022 | 补齐日期口径与区间边界证据 | COMPLETED | None | [设计](../../task-designs/ISSUE-022-design.md) | None |
| 5 | ISSUE-023 | 补齐稀疏事件与记录修订证据 | COMPLETED | None | [设计](../../task-designs/ISSUE-023-design.md) | None |
| 6 | ISSUE-024 | 验证转融资与转融券历史范围 | COMPLETED | None | [设计](../../task-designs/ISSUE-024-design.md) | None |
| 7 | ISSUE-025 | 明确十一接口响应采集合同与来源 | COMPLETED | None | [设计](../../task-designs/ISSUE-025-design.md) | None |
| 8 | ISSUE-026 | 完成剩余区间任务验收与 T14 收尾 | IN_PROGRESS | ISSUE-019, ISSUE-020, ISSUE-021, ISSUE-022, ISSUE-023, ISSUE-024, ISSUE-025 | [设计](../../task-designs/ISSUE-026-design.md) | [交接](../ISSUE-026-handoff.md) |

## Task Details

### ISSUE-019

- **Goal:** 明确官方 6000 / 3500 / 2000 行说明的采用口径，补齐三接口可用于 RANGE 的来源证据。
- **Scope:** `daily`、`forecast`、`dividend`；详细范围见[问题文档](../../issues/problems/ISSUE-019-documented-range-limits.md)。
- **Acceptance:** 三项限量的适用请求、依据、采用口径和异常处理明确；用户决策与上游事实分开记录。 固定新 SOURCE 计划后取得有效整段、边界、两股票及非交易日公告证据；失败与空样本如实保留。 已有真实结果只追加，新规则不得单独把接口标为 AVAILABLE；将匹配的 RANGE TASK / SQL 验收输入交给 ISSUE-026。
- **Dependencies:** None；共享 T14 已有设计与证据。
- **Sources:** `docs/issues/problems/ISSUE-019-documented-range-limits.md` → `docs/task-designs/ISSUE-018-T14-design.md` → `docs/task-designs/ISSUE-018-T13-design.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-official-evidence.md`。
- **First action:** 核对三项官方引文及 T13 的截断合同要求，提交采用限量说明的具体决策方案。
- **State evidence:** 2026-09-13 先初始化 READY；依据用户“挨个解决”启动请求，单独记录 READY -> IN_PROGRESS，开始原文 / 设计 / 策略核对。已形成并提交 `docs/issues/proposals/ISSUE-019-documented-range-limits.md` 两种方案；采用口径待用户决策，未创建实施设计、未修改规则、未运行新来源，None 设计引用保持。
- **2026-09-13续办证据：** 用户要求“解决issue19”；已完成专属设计并链接、公开公告核对及两轮独立SOURCE。38case/50请求，28非空PASS、10空对照，exit0/cleanup PASS；新索引10轮/513case，旧8轮/475case未改。daily/forecast两股票和dividend两股票事件/周六场景已有新观察，已列ISSUE-026的28项非空候选参数映射。限量A/B仍待用户选择，未修改UNKNOWN、生产准入、版本或原验收要求，IN_PROGRESS保持；不启动后继。

- **完成证据（2026-09-13）：** 用户明确回复“同意”方案A；三接口采用6000/3500/2000的工程阈值，T13/T14限定修订及官方/用户证据分开记录。两轮独立SOURCE已覆盖两股票、整段/边界、daily重叠与非交易日公告；28非空和10空对照全部保留，10轮/513case及其他37接口记录未改。决定后Node78/78、相关Maven160/160通过，RED/不提前开放的拒绝检查有实际证据。[ISSUE-026输入](../../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-019-已交付输入)已交付28项参数和精确SOURCE绑定，离线消费者核对通过。按本任务三项关闭条件记录 `IN_PROGRESS -> COMPLETED`；生产仍4 AVAILABLE，三接口RANGE及母issue由ISSUE-026后续验收，未提前开放或关闭。详见[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-019-方案a确认与最终验收)。
- **后续顺序：** 下一项为既定Order2的ISSUE-020；本轮完成ISSUE-019及其验收输入交付，未执行后续issue，ISSUE-020仍NOT_STARTED。

### ISSUE-020

- **Goal:** 解释省略 type 的默认分类集合，以及官方最大 100 行与 SINGLE 实测 150 行的适用关系。
- **Scope:** `fina_mainbz`；详细范围见[问题文档](../../issues/problems/ISSUE-020-fina-mainbz-default-type.md)。
- **Acceptance:** 形成可核验的默认分类与 SINGLE / RANGE 上限适用结论；如需改参数或业务键，先取得用户明确范围决策并修订设计。 不混 P / D / I、不改键掩盖冲突、不以 VIP 绕过；取得报告期 SOURCE 的两股票整段和边界证据。 向 ISSUE-026 提供规则、来源证据及任务样本；同步 ISSUE-017 剩余问题事实，不能提前关闭母 issue。
- **Dependencies:** None；共享 T14 已有设计与证据。
- **Sources:** `docs/issues/problems/ISSUE-020-fina-mainbz-default-type.md` → `docs/task-designs/ISSUE-018-T14-design.md` → `docs/task-designs/ISSUE-018-T13-design.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-official-evidence.md`。
- **First action:** 对照官方默认 type 说明、现有业务键和新 SINGLE 安全摘要，明确最小取证方案与需要用户决定的参数范围。
- **State evidence:** 2026-09-13 用户要求“完成issue20”；本项原为NOT_STARTED、无设计/交接，按本项既定事实记录NOT_STARTED -> READY，再以明确启动请求记录READY -> IN_PROGRESS。专属取证设计已完成并链接；先核对官方分类说明、补充安全摘要和固定新SOURCE，实质范围变化仍须明确决定。

- **本轮取证（2026-09-13）：** [专属设计](../../task-designs/ISSUE-020-design.md)落实安全摘要，RED33项3失败、GREEN33/33；隔离构建1066后端/包检查及468前端通过，证据Node78/78。两轮18case/18请求，14 PASS、4满额EVIDENCE_MISSING，均exit0/cleanup PASS；默认P/D/I与RANGE110/150已观察，两股票报告期整段/非空边界补齐。索引12轮/531case、479请求，旧10轮/513case及其他39接口不变。[采用方案](../../issues/proposals/ISSUE-020-fina-mainbz-default-type.md)待用户决定；首项关闭条件未满足，保持IN_PROGRESS，不启动ISSUE-021。

- **完成证据（2026-09-13）：** 用户明确“同意方案A（推荐）”，默认一次上游实际分类、SINGLE快照和100工程拆分阈值已限定修订T13/T14及专属设计，原上游矛盾不变成保证。两轮18case/18请求完整保留，12项RANGE SOURCE及规则/任务输入正式交付ISSUE-026，4个满额未确认不重标。决定后Node78/78、Maven163/163，12绑定/4拒绝及独立复审通过；12轮/531case和其他39接口未改。全文复核专属设计三项验收成立，记录`IN_PROGRESS -> COMPLETED`。详见[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-020-方案a确认与最终验收)。生产准入和母issue仍由后续真实任务验收决定。
- **后续顺序：** 既定下一项为ISSUE-021，本次仅完成ISSUE-020及向ISSUE-026交付输入，未开始后续issue；ISSUE-021保持NOT_STARTED，无占位设计或交接。

### ISSUE-021

- **Goal:** 明确沪深完整日历、北交所映射以及交易日枚举，补齐交易所和非空证券样本。
- **Scope:** `trade_cal`、`margin`、`top_list`；详细范围见[问题文档](../../issues/problems/ISSUE-021-exchange-calendar-semantics.md)。
- **Acceptance:** SSE / SZSE 完整区间、两端和全休市场景有有效证据；BJ 映射及 BSE 直接输入分别形成结论。 若映射需要代码变化，先依据事实修订对应设计；margin 保留 exchange_id，交易所归属正确。 top_list 按完整日历枚举，取得非空两股票及边界证据；准备 ISSUE-026 的匹配任务计划。
- **Dependencies:** None；共享 T14 已有设计与证据。
- **Sources:** `docs/issues/problems/ISSUE-021-exchange-calendar-semantics.md` → `docs/task-designs/ISSUE-018-T14-design.md` → `docs/task-designs/ISSUE-018-T13-design.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-official-evidence.md`。
- **First action:** 核对 TushareTradeCalendar 与 exchangeForStock 的拒绝路径，依据官方日历说明提出可区分 BSE 直接输入与 BJ 映射的固定验证计划。
- **State evidence:** 2026-09-13 用户要求拆分并逐项解决；初始化 NOT_STARTED。

- **启动（2026-09-13）：** 用户明确“完成issue21”，记录NOT_STARTED → READY → IN_PROGRESS。专属设计已完成；核对日历校验及BSE/BJ拒绝路径，先修复安全取证摘要，再固定真实SOURCE，生产准入与母issue状态保持。

- **完成证据（2026-09-13）：** 三轮39case/63请求，38 PASS（35非空/3闭市零证券）及1 BSE直接合法结构0行的完整性FAILED，三个cleanup PASS。沪深14项完整日历、margin三交易所9项整段/边界及top_list SH/SZ/BJ各5项来源完成；BJ→SSE仅测试候选，生产实现与TASK/SQL留ISSUE-026。相关Maven170/170、最终Node78/78、两次隔离acceptance构建通过；38项匹配输入/负例拒绝、旧12轮531case/其他37接口保留及40行报告一致性核对通过。独立最终复审确认三项关闭条件成立，记录IN_PROGRESS → COMPLETED；[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-021-最终验收)，[ISSUE-026交付](../../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-021-已交付输入)。母issue和T13/T14未关闭，三接口生产仍NEEDS_VERIFICATION/v1。
- **后续顺序：** 下一项为既定Order4的ISSUE-022；用户本轮仅要求完成ISSUE-021，未执行后续issue，ISSUE-022保持NOT_STARTED，不创建占位设计/交接。

### ISSUE-022

- **Goal:** 验证周期行情、财务报告期、公告日与新股申购日的真实筛选含义。
- **Scope:** `weekly`、`monthly`、`fina_indicator`、`stk_holdernumber`、`new_share`；详细范围见[问题文档](../../issues/problems/ISSUE-022-range-date-axis-evidence.md)。
- **Acceptance:** 周期行情用真实最后交易日，不硬编码周五或自然月末。 报告期、公告日、统计截止日、申购日和上市日通过同一行日期对照区分。 各接口补足两股票或原非股票方式的有效整段 / 边界 SOURCE，向 ISSUE-026 提供固定任务输入。
- **Dependencies:** None；共享 T14 已有设计与证据。
- **Sources:** `docs/issues/problems/ISSUE-022-range-date-axis-evidence.md` → `docs/task-designs/ISSUE-018-T14-design.md` → `docs/task-designs/ISSUE-018-T13-design.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-official-evidence.md`。
- **First action:** 从已有 monthly 和 fina_indicator 有效样本出发，结合官方公开样例登记缺失的第二股票、特殊日期与边界计划。
- **State evidence:** 2026-09-13 用户要求拆分并逐项解决；初始化 NOT_STARTED。

- **启动（2026-09-13）：** 用户明确“完成issue22”，记录NOT_STARTED → READY → IN_PROGRESS。专属设计与固定样本方案已完成，先补安全日期投影再执行新SOURCE；沿用已授权日期轴及范围。

- **完成证据（2026-09-13）：** 两轮39case/39请求，35非空PASS（含4项辅助日历）、4空对照保留，两个exit0/cleanup PASS。五接口两股票/原非股票整段、边界、特殊周期及同一行日期对照补齐；35项精确候选交ISSUE-026，4空拒绝。专项Maven173/173、隔离acceptance1072后端/包+468前端、最终Node78/78通过；旧15轮570case/其他34接口保留，trade_cal仅追加4辅助引用。独立最终复审无剩余发现，三项关闭条件成立，记录IN_PROGRESS → COMPLETED；[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-022-最终验收)、[ISSUE-026交付](../../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-022-已交付输入)。生产准入和TASK/SQL留后续，母issue/T13/T14未关闭。
- **后续顺序：** 下一项为既定Order5的ISSUE-023；用户本轮仅要求完成ISSUE-022，未执行后续issue，ISSUE-023保持NOT_STARTED，不创建占位设计/交接。

### ISSUE-023

- **Goal:** 用有事件依据的样本验证多笔交易、最新披露修订和公告日筛选。
- **Scope:** `block_trade`、`disclosure_date`、`stk_holdertrade`、`pledge_detail`；详细范围见[问题文档](../../issues/problems/ISSUE-023-sparse-event-range-evidence.md)。
- **Acceptance:** 先有可追查事件日期再登记新 SOURCE，不用反复换日期直到成功替代固定计划。 取得多笔交易、最新公告 / 修订、业务日期区别等对应代表性证据，不改变原业务键。 补足各接口整段和边界；disclosure_date 的实际更新入库由 ISSUE-026 验证。
- **Dependencies:** None；共享 T14 已有设计与证据。
- **Sources:** `docs/issues/problems/ISSUE-023-sparse-event-range-evidence.md` → `docs/task-designs/ISSUE-018-T14-design.md` → `docs/task-designs/ISSUE-018-T13-design.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-official-evidence.md`。
- **First action:** 利用官方样例和现有安全日期摘要查找可核验事件，为四接口制定固定非空来源计划。
- **State evidence:** 2026-09-13 用户要求拆分并逐项解决；初始化 NOT_STARTED。

- **启动（2026-09-13）：** 用户明确“完成issue23”，记录NOT_STARTED → READY → IN_PROGRESS。[专属设计](../../task-designs/ISSUE-023-design.md)与[计划](../../superpowers/plans/2026-09-13-issue-023.md)已完成；先核对公开事件，补测试侧安全计数和辅助字段，再固定SOURCE。生产列、业务键、参数和准入保持，TASK/SQL交ISSUE-026。

- **完成证据（2026-09-13）：** 用户明确同意质押非空股票改为000014.SZ/600000.SH，000001.SZ空结果保留；三轮39case/131请求，38非空PASS、1空，三个exit0/cleanup PASS。四接口事件、两股票整段/边界、公开修订后披露与原键复查成立，35项唯一候选交ISSUE-026，10旧空/失败RANGE及空SINGLE拒绝。相关Maven184/184、隔离1083后端/包+468前端、Node78/78及独立最终复审通过；历史17轮/609case和其他36接口保持，唯一JSON20轮/648case/712请求。三项关闭条件满足，记录IN_PROGRESS → COMPLETED；[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-023-最终验收)、[ISSUE-026交付](../../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-023-已交付输入)。生产四接口仍NEEDS_VERIFICATION/v1，实际更新/TASK/SQL及母issue不提前完成。
- **后续顺序：** 下一项为既定Order6的ISSUE-024；本轮只完成用户要求的ISSUE-023，未执行后继，ISSUE-024仍NOT_STARTED，不创建占位设计/交接。

### ISSUE-024

- **Goal:** 核验转融资与转融券的历史调用能力，并补齐汇总和明细代表性数据。
- **Scope:** `slb_len`、`slb_sec`、`slb_sec_detail`；详细范围见[问题文档](../../issues/problems/ISSUE-024-slb-historical-range-evidence.md)。
- **Acceptance:** 按2026-09-14用户批准方案A，取得可核验历史查询能力及两股票 / 原非股票方式的整段和边界证据；精确起止/持续保留保证仍未知，不再作为关闭前提，实际可查日期与上游承诺区分。 明细覆盖期限 / 费率差异；汇总不发明不存在的期限列或新业务键。 如无法取得完整历史支持依据，记录准确缺口并交用户决策；不得自行排除接口或把空结果当全历史通过。
- **Dependencies:** None；共享 T14 已有设计与证据。
- **Sources:** `docs/issues/problems/ISSUE-024-slb-historical-range-evidence.md` → `docs/task-designs/ISSUE-018-T14-design.md` → `docs/task-designs/ISSUE-018-T13-design.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-official-evidence.md`。
- **First action:** 复核官方历史样例和监管原文，登记第二股票、汇总非空及期限 / 费率差异的固定来源计划。
- **State evidence:** 2026-09-13 用户要求拆分并逐项解决；初始化 NOT_STARTED。

- **启动（2026-09-13）：** 用户明确“完成issue24”，记录NOT_STARTED → READY → IN_PROGRESS。[专属设计](../../task-designs/ISSUE-024-design.md)及[计划](../../superpowers/plans/2026-09-13-issue-024.md)已完成，先补安全原键/期限费率投影，再固定历史SOURCE；实际可查日期与上游历史承诺分开，必要实质修订交用户决定。

- **本轮已完成取证（2026-09-13）：** 两轮39case/39请求，33非空PASS、6空保留，两个exit0/cleanup PASS。融资13行及两端、两股票汇总/明细整段和边界、明细长窗期限9/7与费率54/39差异成立；190相关回归、隔离1089后端/包+468前端、Node78/78通过。33项精确候选及15负例核对已交[ISSUE-026条件输入](../../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-024-已交付输入)。历史精确起止/持续保留合同仍无依据，已提交[采用方案](../../issues/proposals/ISSUE-024-historical-support.md)；原关闭条件未被自行修订，保持IN_PROGRESS，不启动ISSUE-025。

- **完成证据（2026-09-14）：** 用户明确“同意方案 A（推荐）”，历史查询能力采用口径已限定修订T13/T14和专属设计，未知起止/持续保留事实保留。两轮39case/39请求、33非空及6空、退出/清理和此前构建证据全部保持；决定后Node78/78、33绑定/15负例、全部22轮687case751请求及其他37接口不变核对通过，独立复审无剩余发现。三项关闭条件已满足，记录`IN_PROGRESS → COMPLETED`；[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-024-方案a确认与最终验收)、[ISSUE-026正式交付](../../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-024-已交付输入)。生产三接口仍NEEDS_VERIFICATION/v1，实际TASK/SQL与母issue继续后续验收。
- **后续顺序：** 既定下一项为ISSUE-025；本次完成ISSUE-024及向ISSUE-026交付，未执行后继，ISSUE-025保持NOT_STARTED，不创建占位设计或交接。

### ISSUE-025

- **启动证据（2026-09-14）：** 用户明确“完成issue25”；按本项既定事实记录NOT_STARTED → READY → IN_PROGRESS。专属设计完成并回填；开展已授权公开调查、安全投影和固定SOURCE，完整性/代表股票的实质调整待具体决定。

- **Goal:** 按用户接受不完整的决定，为十一接口明确响应采集合同并补齐日期 / 事件 / 边界来源。
- **Scope:** `adj_factor`、`suspend_d`、`income`、`balancesheet`、`cashflow`、`fina_audit`、`express`、`repurchase`、`stk_managers`、`top10_holders`、`top10_floatholders`；详细范围见[问题文档](../../issues/problems/ISSUE-025-unknown-extraction-contracts.md)。
- **Acceptance:** 按2026-09-14用户“可以接受不完整”，逐接口明确RESPONSE_ONLY请求/日期/响应处理/空失败/页面/版本合同并保留原完整提取差异；UNKNOWN不记作上游保证。来源及代表场景、清洁运行、回归与独立复审通过；87组匹配来源及合同交ISSUE-026承担生产实现和真实TASK/SQL。
- **Dependencies:** None；共享 T14 已有设计与证据。
- **Sources:** `docs/issues/problems/ISSUE-025-unknown-extraction-contracts.md` → `docs/task-designs/ISSUE-018-T14-design.md` → `docs/task-designs/ISSUE-018-T13-design.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-official-evidence.md`。
- **First action:** 逐项对照已有公开调查，列出确实仍缺的上游合同和可由技术验证解决的缺口，向用户提交无法由现有材料回答的决策。
- **State evidence:** 2026-09-13 用户要求拆分并逐项解决；初始化 NOT_STARTED。

- **本轮取证（2026-09-14）：** [三轮固定SOURCE](../../verification/ISSUE-018-T14-runs.md#issue-025-十一接口提取规则与来源)135case/135请求，92非空与43空保持，全部exit0/cleanup PASS；两基准股票/原非股票代表整段、非空两端及日期/停复牌场景已观察。Maven194/194、隔离1093后端/包与468前端、Node78/78通过；87组[条件输入](../../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-025-条件输入完整性未确认)绑定通过且UNKNOWN准入87/87拒绝。完整提取规则仍未取得，[具体方案](../../issues/proposals/ISSUE-025-extraction-contracts.md)待用户选择，保持IN_PROGRESS；不推广其他issue采用决定、不提前开放生产或启动ISSUE-026。

- **完成证据（2026-09-14）：** 用户明确“可以接受不完整”，采用[方案A](../../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)，已完成精确合同及T13/T14/总体设计/母issue限定修订。决定后Node78/78、87项Markdown重建绑定与UNKNOWN拒绝、全部25轮822case886请求和其他29接口保持核对通过；独立复审修正任期字段后无剩余阻断。三项验收实际成立，记录`IN_PROGRESS → COMPLETED`；[最终验收](../../verification/ISSUE-018-T14-runs.md#issue-025-方案a确认与最终验收2026-09-14)。生产和版本保持，未新增真实TASK/SQL，母issue不关闭。

### ISSUE-026

- **子任务进展（2026-09-14）：** ISSUE-027已交付后端RESPONSE_ONLY、runner匹配及只读持久摘要，287项后端/包与468项前端检查及独立审查通过；见[验收记录](../../verification/ISSUE-027-response-only-backend.md)。母任务尚余028～032，保持IN_PROGRESS，真实索引与生产准入不变。

- **启动证据（2026-09-14）：** 用户要求“完成issue27”，首个子issue按专属设计实际启动，记录母任务READY → IN_PROGRESS；其余子issue与最终验收仍按子看板推进。

- **Goal:** 消费 ISSUE-019～025 的逐接口规则和来源证据，完成剩余 30 接口的真实 RANGE / SQL、回归与母任务验收。
- **Scope:** 全部 30 待验证接口的 RANGE TASK / SQL 与 T14 最终收尾；详细范围见[问题文档](../../issues/problems/ISSUE-026-range-task-final-acceptance.md)。
- **Acceptance:** 每个开放接口都有适用提取合同、有效来源及匹配候选包的页面 / 全批次 / SQL / 日志；十一项按已批准RESPONSE_ONLY响应采集，其余完整性要求保持；包括 daily 重叠更新与各专属代表场景。 新轮有固定输入、稳定完整源码、实际包摘要、新空 schema 和安全清理；失败 / 未运行不改为通过，成功数据保留。 相关测试、六条源码门禁、能力 / 版本 / 文档与最终审查通过；既有成功 SINGLE 证据按构建影响复核，不无故重跑全部账户任务。 复核 T14 Acceptance、T13 原 Acceptance 和母 issue 关闭条件；全部成立才按合法状态转换收尾，否则保留准确剩余项。
- **Dependencies:** ISSUE-019～025 的有效规则与 SOURCE。
- **Sources:** `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md` → `docs/task-designs/ISSUE-018-T14-design.md` → `docs/task-designs/ISSUE-018-T13-design.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-official-evidence.md`。
- **First action:** 进入[ISSUE-027～032子看板](../ISSUE-026/ISSUE-026-task-board.md)的ISSUE-027，按共享设计中分配的后端范围完成并链接其专属设计；不在本总任务下重复实施全部六部分。
- **State evidence:** 2026-09-13 用户要求拆分并逐项解决；初始化 NOT_STARTED。

- **后继准备证据（2026-09-14）：** ISSUE-025已记录COMPLETED后，按预定义Order选定本项，观察NOT_STARTED。完成并链接[专属设计](../../task-designs/ISSUE-026-design.md)：公共规则/runner/快照/HTTP/页面/正式契约/证据schema2，以及268既有来源、四项新fina_mainbz拆分SOURCE和278 TASK计划明确。独立设计复审补齐已批准全年/六年真实拆分、正式HTTP合同和同参数指定SOURCE绑定后无剩余material gap，可实施。七直接依赖的决定与约束核对一致，先回填设计，再写并链接[交接](../ISSUE-026-handoff.md)，记录`NOT_STARTED → READY`。本次仅准备，未记录IN_PROGRESS、未修改生产或执行新SOURCE/TASK/SQL。

- **继续拆分（2026-09-14）：** 用户明确“继续拆吧，继续拆成issue”，按已讨论六部分创建ISSUE-027～032及[子看板](../ISSUE-026/ISSUE-026-task-board.md)。本看板继续唯一管理ISSUE-026总任务，现有READY/Design/Handoff引用保持；子项初始027 READY、其余NOT_STARTED，尚未实施且专属设计均为None。总设计新增责任分配、原交接入口同步；278 TASK/10轮、四项新mainbz SOURCE、全部原采用决定及母关闭条件不缩减。

## Risks

- 用户决策只能变更明确的产品 / 验收口径，不能充当上游返回、来源请求、SQL 或测试通过的事实。
- 现有暂存成果与成功账户数据必须保留；新增文件加入 Git，不自动提交、合并或发布。
- 公开调查已有覆盖范围完整保留；新的账户样本先有依据并登记，再按 T14 节流、预算、权限、源码 / 包身份及新空库合同执行。
