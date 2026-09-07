# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1/tensor-v1-task-board.md`
- **Completed task:** `M14-T07`，Order 78；沿用看板既有 COMPLETED，仅表示用户决定的任务收尾，原安全验收失败不改判。
- **Next task:** `M14-T08`，Order 79，AC 映射与发布证据收尾。
- **Design document:** `docs/task-designs/M14-T08-designs.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。用户将另开任务启动实施，本次不执行 `READY -> IN_PROGRESS`。

本交接依据用户 2026-09-07 的明确请求：“完成交接文档，完成后，我会单独启动一个任务来完成这个任务”。M14-T07 的历史 pause 交接不作为本任务恢复安全验收的指令；M14-T08 的最新范围以权威看板和上述设计为准。

## Next Task

**M14-T08 — AC 映射与发布证据收尾。** 目标是整理现有验收记录，交付以下三份相互链接的文档；本交接创建时三份文件均尚未创建，文档实施尚未启动。

| 交付文件 | 内容与完成要求 |
|---|---|
| `docs/verification/release-checklist.md` | 现有证据索引、原版本/日期/命令/退出码/计数与散列、门禁汇总、延期和风险、文档核对结果；原执行结果与当前处置分栏。 |
| `docs/verification/ac-001-018.md` | 恰 18 项 AC、31 项 PRD-F 与 PRD 10.1～10.6，保留原标题、P0/P1 和直接/部分覆盖语义，逐项记录来源、覆盖程度及局限。每项 AC 另列“本次未复验”。 |
| `docs/verification/release-summary.md` | 已有证据摘要、文档交付结论、未验证项及风险，并链接清单、AC 矩阵和 runbook。固定区分“已有证据完成归档”与“当前候选版本尚未完成本轮整体验收”。 |

**范围：** 只读已有文档并形成上述映射与摘要，做编号、来源、版本、数据准确性、链接和差异核对。原第 1～5 项的候选包冻结/构建、工具接入、新环境首跑、自动/安全回归及真实接口复验均已移出范围和完成前提，不新增这些子任务。

**验收：** 文档完整、准确、可追踪，缺失证据、版本差异、历史失败、用户延期和已接受风险均如实披露即可完成；不要求先消除产品验证缺口。不可将不同版本/专项通过拼为同一候选包整体通过，不宣称本轮发布就绪。

不运行历史命令，不读取 Token，不启动 JVM/MySQL/浏览器，不修改生产实现、测试工具、原始证据或其他问题状态。真实行、完整响应、配置及原始日志不进入交付文件。来源未提供的字段填写“原记录未提供”，不可定位的临时产物披露为局限，不重建或恢复历史环境。

## Dependencies

### M14-T01

- **Artifact:** `docs/verification/M14-T01-fixture-flow.md`。
- **Decision:** fixture SUCCESS/EMPTY、来源字段和禁用重启按原验收包/原轮次引用。
- **Rationale:** 该记录直接提供测试插件通过既有核心完成页面闭环的证据。
- **Constraint:** acceptance 包与生产包身份分开，旧包通过不等于当前候选包通过；不复跑旧启动流程。
- **Usage:** 映射 AC-001、AC-005、AC-011、AC-017，并为 AC-018 提供有限的历史页面证据。
- **Readiness evidence:** 看板已记录 COMPLETED；验收文档可访问，原三个用例通过属于已有结果，本交接不增加测试结果。

### M14-T02

- **Artifact:** `docs/verification/M14-T02-download-outcomes.md`。
- **Decision:** 参数拦截、失败分类、幂等、来源信息、适配零写入与事务回滚分别采用原页面/数据库记录。
- **Rationale:** 各项分别对应下载异常与数据一致性要求，不能用一个成功下载概括。
- **Constraint:** 合成上游和 fixture 故障注入属于原测试场景；不算真实接口验收，也不重建触发器或数据库。
- **Usage:** 映射 AC-003～011 的相应要求，保留各场景与原命令/计数/版本。
- **Readiness evidence:** 看板已记录 COMPLETED；原 15 项矩阵及安全清理摘要在可访问的验收文档中。

### M14-T03

- **Artifact:** `docs/verification/M14-T03-dataset-query.md`。
- **Decision:** 查询/分页/宽表/精度/竞态/键盘行为按原最终轮记录，历史失败轮继续保留。
- **Rationale:** 这些是数据查看可用性及只读行为的直接证据。
- **Constraint:** 152 业务列加三来源列的合成宽表不能证明真实 balancesheet 非空；分页功能通过不证明性能目标通过。
- **Usage:** 映射 AC-011～016、对应 PRD-F 和兼容性/可用性要求。
- **Readiness evidence:** 看板已记录 COMPLETED；文档包含原最终 11 项通过及独立数据库核对，不需要恢复私有截图/数据库来消费这份记录。

### M14-T04

- **Artifact:** `docs/verification/M14-T04-49-contracts.md`。
- **Decision:** 49 自动契约与 49 项元数据页面覆盖分别引用，并与真实接口验收分列。
- **Rationale:** 自动契约/页面描述符覆盖并不产生真实上游下载。
- **Constraint:** 沿用原 manifest/源码/包的记录身份与计数，不将旧六迁移结果改写为新包七迁移结果，不运行契约脚本。
- **Usage:** 映射 AC-001～003、AC-006、AC-013 及全部 49 数据集定义/表结构相关需求。
- **Readiness evidence:** 看板已记录 COMPLETED；既有 metadata 50/schema 52/package 4 和页面 49 项结果在文档中可定位，仅支持其原版本。

### M14-T09

- **Artifact:** `docs/verification/M14-T09-tushare-live-rerun-02.md`。
- **Decision:** 只消费固定 40 项最后完整同轮结果：40 通过/0 失败/0 未运行、48 次下载/80 次查询，fixture 另计 2/3。
- **Rationale:** 用户已将原 49 目标中的九项缺口移出本轮前置条件，并明确本任务只做已有证据收尾。
- **Constraint:** 保留原参数、包身份、扫描/清理和九项排除；不拼接失败轮的部分通过，不重新调用真实接口，不运行旧控制器。
- **Usage:** 引用原 daily/empty/来源和页面/独立数据库结果，形成真实范围摘要并支持相应 AC。
- **Readiness evidence:** 看板已记录 COMPLETED 及该完整同轮结果；最终验收文档可访问。M14-T05 原 49 目标仍未完成，不影响该文档作为历史输入。

### M14-T07

- **Artifact:** `docs/verification/M14-T07-security.md`；管理完成依据为 `docs/task-handoffs/tensor-v1/tensor-v1-task-board.md` 中 M14-T07 的 owner-directed completion 记录。
- **Decision:** 原正式安全验收 18 pass / 6 fail / 0 not-run、退出 1，与用户决定的任务 COMPLETED 分开记录。
- **Rationale:** 管理收尾没有把技术失败变成通过；本任务要保留这一事实并关联后续专项处置。
- **Constraint:** 不执行其历史 pause 的恢复步骤、不复扫、不放宽安全规则。原失败文档和后续修复证据不在同一版本上自动合并。
- **Usage:** 记录安全原门禁、失败项、已完成页面/扫描项目及覆盖局限，并指向下述 ISSUE 专项记录。
- **Readiness evidence:** 看板已记载前驱 COMPLETED；原报告可访问。失败是必须归档的结果，按已收窄设计不阻止文档交付准备。

### 需求与首跑/UI 补充输入

- **Artifact:** `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md`、`docs/traceability/tensor-v1-requirements.md`、`docs/verification/ISSUE-004-ui-redesign.md`、`docs/task-handoffs/tensor-v1/M13-T04-handoff.md`、`docs/runbook/first-run.md`、`docs/runbook/configuration.md`、`docs/runbook/acceptance.md`。
- **Decision:** 保留 PRD 原要求/优先级与直接/部分/内联映射；UI 和首跑各自按原版本记录。
- **Rationale:** 文档交付需要完整需求集合，以及现有 UI/运行能力的来源和使用边界。
- **Constraint:** runbook 是说明，不是执行证据；UI route stub 结果不是打包后端或真实上游验收；AC-018 缺少当前同版整体验收时仅部分支持。
- **Usage:** 构建 18/31/6 的映射表，补充 UI、首跑和生产/验收包职责，在发布摘要链接操作说明。

### 安全专项与用户处置

- **Artifact:** `docs/verification/ISSUE-010-health.md`、`docs/verification/ISSUE-011-dataset-method-status.md`、`docs/verification/ISSUE-012-query-extra-parameters.md`、`docs/verification/ISSUE-013-query-completion-events.md`、`docs/verification/ISSUE-014-security-maven-verification.md`、`docs/verification/ISSUE-015-backend-dependency-audit.md`；当前问题状态见 `docs/issues/README.md`；处置依据见 `docs/issues/problems/ISSUE-008-tushare-live-coverage-gap.md`、`docs/issues/problems/ISSUE-009-query-performance-verification.md`、`docs/issues/problems/ISSUE-015-backend-dependency-audit.md`、`docs/issues/problems/ISSUE-016-ui-fixture-yaml-validation.md`。
- **Decision:** ISSUE-010～014 采用各自专项修复/验证记录；ISSUE-015 的 35 条高阈值发现（27 个不同 CVE）和四类分析缺口按用户原决定“不需要处理”。ISSUE-008 九项真实接口及 ISSUE-009 性能验证均“不依赖，用户后续单独处理”。
- **Rationale:** 已有问题处置决定必须在发布证据中呈现，不能用旧暂停快照替代后续已有决定。
- **Constraint:** 风险接受只归档原报告的已知范围，不代表完整扫描通过或对新产物的风险接受；保留原退出码。其他 issue 状态不变，缺少当前版本验证如实披露。
- **Usage:** 为安全原失败增加专项来源和当前处置列，登记延期/未实测/Minor 局限，避免要求下一任务解决这些问题。

**输入一致性：** 上述记录可能来自不同提交/JAR，这是应分列的证据局限；原安全失败、后续专项修复和用户风险接受分别有明确适用范围，与本任务“现有证据归档”的设计一致，不要求先获得一次新的全流程通过。九项排除和性能未验证事实继续保留。

## Start Here

进入新任务后先定位权威看板的 M14-T08 行，核对当前身份/状态/Design/Handoff，再按以下顺序消费来源：

1. 完整读取 `docs/task-designs/M14-T08-designs.md`，随后读取本交接与 `docs/superpowers/plans/tensor-modules/M14-integration-release.md` 的 M14-T08 卡。不要采用旧“全新环境验收”版本的工作清单。
2. 读取 PRD 的功能/非功能/验收章节及 `docs/traceability/tensor-v1-requirements.md`，固定 18 项 AC、31 项 PRD-F 和六类非功能要求。
3. 依次读取上述 M14-T01、T02、T03、T04、T09、T07 原报告，逐份建立原验证轮次/版本/结果和局限；命令只摘录，不执行。
4. 读取 ISSUE-010～015 专项、ISSUE-004 UI 和 M13-T04 首跑记录，再读 runbook 与问题索引/处置文件，保留后续决定和范围差异。

**第一项实施动作：** 用户在新任务发出启动请求后，按看板记录 `READY -> IN_PROGRESS` 并保留本交接路径；创建 `docs/verification/release-checklist.md` 的现有证据索引，逐项填入来源、原日期/版本、命令/退出码、计数、文档当前 SHA-256 与局限。文档当前散列和原源码/JAR 散列分栏，不冻结新候选包。然后按设计完成另外两份文档及文档核对。

交付前检查编号集合、标题/P0/P1、来源链接、引用数值/版本和脱敏范围，执行 `git diff --check`，将新文件加入 Git并记录文档检查结果。只有三份文档满足设计才记录 `IN_PROGRESS -> COMPLETED`；不将本任务的文档完成写成技术发布准入通过，不新增更高 Order 的后继任务。

## Risks

- 工作区已有其他任务的暂存/未暂存修复及证据文件；新任务先读 `git status --short`，直接消费工作区的已跟踪文档内容，避免 `git archive HEAD` 漏掉尚未提交的专项记录。保护既有修改，不执行 reset/clean、不合并暂存或提交无关代码。本任务不要求先清空或提交整个工作区。
- M14-T07 历史 handoff 中“ISSUE-010～015 未解决”“M14-T08 未准备”的文字属于旧时点快照；当前入口以看板的新准备记录和本交接为准，issue 当前状态以其已有后续记录核对。
- 现有文档来自不同版本，旧包/截图/临时报告可能已不可定位。明确披露来源局限即可，不通过重建或重跑弥补本任务范围外缺口。
- AC-018、原 49 真实目标、性能及整体安全/发布准入不能因文档归档被宣称通过；每项 AC 均标“本次未复验”。
- 三份交付文件尚未创建，READY 仅表示设计/输入/交接已就绪。用户将在独立新任务启动实施，本次交接不执行文档交付、不记录 IN_PROGRESS 或 COMPLETED。
