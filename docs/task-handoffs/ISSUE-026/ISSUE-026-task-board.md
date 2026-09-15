# ISSUE-026 子 Issue 任务看板

## 2026-09-15最终收尾

2026-09-15最终验收完成：六门禁1401/524/1134/1137/15/138和独立终审通过，当前30个RANGE接口/251项纳入、四接口27项明确排除、40接口SINGLE保持。T14/T13、017/018、026及032均已按证据收尾；033仍NOT_STARTED。详见[最终报告](../../verification/ISSUE-032-range-final-closure.md)。下文早期“未开始/阻塞/母任务未关闭”均为保留的阶段记录，当前状态以任务表及最新收尾证据为准。

## Project

- **Project ID:** `ISSUE-026`。
- **Goal:** 按用户2026-09-14“继续拆吧，继续拆成issue”，把已确认的六部分分成ISSUE-027～032，各自验收并完成母任务。
- **Scope:** 后端响应采集、HTTP/页面、证据工具与四项mainbz新SOURCE、30项候选策略、278 TASK/十轮SQL验收、最终回归与收尾。范围和采用决定仍以[共享设计](../../task-designs/ISSUE-026-design.md)及[母issue输入表](../../issues/problems/ISSUE-026-range-task-final-acceptance.md)为准；十轮不再拆成额外issue。
- **Completion condition:** 六子issue实际完成且ISSUE-026/T13/T14/母issue按各自验收和合法状态收尾；拆分、文档或候选布尔准入不等于实现/真实验收完成。
- **Authority:** 本看板管理原六项ISSUE-027～032及用户另行登记的递延项ISSUE-033；[后续看板](../ISSUE-018/ISSUE-018-followups-task-board.md)继续唯一管理ISSUE-026总任务，[原看板](../ISSUE-018/ISSUE-018-task-board.md)管理T13/T14。母issue的子项清单和issues README只作索引，不另存权威状态。
- **Baseline（初始化时）:** 25轮822case886请求，生产4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY；既有74 SINGLE及四接口25 RANGE任务保留。初始化未改生产、唯一验收索引或真实结果；后续实际变化见各任务完成证据。

## Workflow

- **最新启动指令（2026-09-15）：** 用户要求“完成issue32”，现按修订设计执行最终验收；仅取代此前不启动032的时序要求，四接口排除及033不启动继续有效。下方此前不启动措辞均为历史决定，当前状态以Tasks及最新证据为准。

- **2026-09-15范围决定：** [用户明确排除四接口RANGE](../../issues/proposals/ISSUE-026-range-scope.md)，本次30个RANGE接口/251项纳入、四接口27项排除、原六个SINGLE_ONLY保持；原34/278和仅财务指标递延要求受此限定。用户同时要求不开始032，覆盖自动后继准备流程：032保持NOT_STARTED、Handoff=None，033保留未开始的后续问题。

- **Execution:** 沿用用户逐项处理的要求，按Order串行推进；本次只初始化第一项READY，其余NOT_STARTED，不自动开始实现。直接Dependencies只列本轮子issue前置，019～025的已交付决定和来源分别列在Sources，不计为新增工作。
- **Next-task selection:** 当前子issue完成后选取Order更大的未完成项中最小者；具体依赖以实际交付为准。
- **Successor preparation:** 初始化时专属设计均为None；实施前按共享设计中分配给本issue的范围完成并回填专属设计。后继准备须先完成并链接设计，再写next-task交接及准备READY；设计未就绪不写占位交接，前序已完成状态保留。
- **Allowed transitions:** NOT_STARTED -> READY、READY -> IN_PROGRESS、IN_PROGRESS -> PAUSED、PAUSED -> IN_PROGRESS、READY -> BLOCKED、IN_PROGRESS -> BLOCKED、BLOCKED -> READY、IN_PROGRESS -> COMPLETED。
- **Parent lifecycle:** ISSUE-026已随ISSUE-027实际启动，在母看板记录READY -> IN_PROGRESS；当前保持IN_PROGRESS。子issue局部完成不关闭母任务；本次范围排除四接口后，仍须ISSUE-032最终验收完成再按母任务实际状态收尾；ISSUE-033不再是本次关闭前置，用户要求暂不开始032。
- **Validation:** 各代码任务完成局部检查、必要集成/构建与审查；真实调用前的前置校验不延后。六条最终门禁由ISSUE-032汇总并补齐，同一最终源码且顺序合规的有效结果可复用，不为每个子issue重复整套账户任务。
- **Evidence ownership:** 真实运行仍登记到现有T14运行文档并追加唯一验收索引。ISSUE-029承担四项新SOURCE；ISSUE-031承担本次251项TASK/SQL收尾，原278项总追踪中四接口27项按明确决定排除，问题和原状态保留；ISSUE-032承担未来最终关闭，本次不启动。保持原issue026 run/case命名和精确输入，不因新编号重包装旧结果。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | ISSUE-027 | 实现允许不完整的后端响应采集 | COMPLETED | None | [设计](../../task-designs/ISSUE-027-design.md) | None |
| 2 | ISSUE-028 | 贯通响应采集接口合同与页面说明 | COMPLETED | ISSUE-027 | [设计](../../task-designs/ISSUE-028-design.md) | [交接](../ISSUE-028-handoff.md) |
| 3 | ISSUE-029 | 升级验收工具并补齐主营业务拆分来源 | COMPLETED | ISSUE-028 | [设计](../../task-designs/ISSUE-029-design.md) | [交接](../ISSUE-029-handoff.md) |
| 4 | ISSUE-030 | 配置剩余接口候选策略与日历映射 | COMPLETED | ISSUE-027, ISSUE-028, ISSUE-029 | [设计](../../task-designs/ISSUE-030-design.md) | [交接](../ISSUE-030-handoff.md) |
| 5 | ISSUE-031 | 完成区间任务与SQL验收（本次251项） | COMPLETED | ISSUE-029, ISSUE-030 | [设计](../../task-designs/ISSUE-031-design.md) | [交接](../ISSUE-031-handoff.md) |
| 6 | ISSUE-032 | 完成最终回归与母任务收尾 | COMPLETED | ISSUE-031 | [设计](../../task-designs/ISSUE-032-design.md) | None |
| 7 | ISSUE-033 | 修复财务指标RANGE适配失败 | NOT_STARTED | 既有失败与SOURCE证据 | None | None |

## Task Details

### ISSUE-027

- **完成证据（2026-09-14）：** 独立规则/评估、runner匹配和持久policySummary交付；专项280项（含11项MySQL）、两包7项及前端468项通过，失败/错误/跳过0；独立审查及增量审查无剩余发现。新设计与[验收记录](../../verification/ISSUE-027-response-only-backend.md)已加入Git，生产注册/版本与25轮822case886请求索引字节保持。四项Acceptance成立，记录IN_PROGRESS → COMPLETED；HTTP/页面和真实任务留后续，母任务仍IN_PROGRESS。

- **启动证据（2026-09-14）：** 用户明确“完成issue27”；读取共享合同与现有runner后完成专属设计，回填链接并记录READY → IN_PROGRESS。先补非法组合及评估匹配失败测试，生产候选与旧证据保持。

- **Goal:** 让后端明确执行RESPONSE_ONLY响应采集，保持严格完整性规则、失败处理和旧任务语义。
- **Scope:** 公共CompletenessRule.Kind、BatchAssessment和Tushare RuleKind支持独立RESPONSE_ONLY，要求原生区间、rowLimit为null且不可拆分；保留UNKNOWN拒绝。 runner按持久化策略精确匹配评估，响应仍经过结构、日期、股票归属、适配和原子入库；覆盖合法空、非空、错误及规则不匹配。 复用policy_snapshot保存规则/版本，提供只读policySummary，保留旧快照读取、definitionHash和人工重放校验。 Tushare策略实现支持新规则，但本issue不修改30项生产注册的候选开放标记/版本；HTTP响应字段、正式schema和页面交ISSUE-028，SOURCE/TASK取证交后续。 详见[问题文档](../../issues/problems/ISSUE-027-response-only-backend.md)。
- **Acceptance:** RESPONSE_ONLY仅在合法原生、无阈值、不可拆组合成立；UNKNOWN拒绝，严格规则与新评估互相冒用均失败且零入库。 合法非空/空经既有适配与事务完成；坏响应、错股票/日期、适配/写入失败、预算或中断不被接受漏数的决定豁免；受控来源及MySQL验证通过。 旧快照读取和摘要变更拒绝重放成立；policySummary只读取持久事实、SINGLE为null，不依赖当前插件能力。 局部回归及独立审查通过，向ISSUE-028交付确定的后端summary/枚举合同；现有4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY生产注册和真实证据保持。
- **Dependencies:** None。
- **Sources:** `docs/issues/problems/ISSUE-027-response-only-backend.md` → `docs/task-designs/ISSUE-026-design.md`（共享设计Approach第2节及第3节的持久化/summary部分；ISSUE-025已批准决定；现有公共批次合同与runner。）→ `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-runs.md`。
- **First action:** 核对共享设计第2节与持久化summary部分及现有BatchDownloadDescriptor/runner，完成并回填docs/task-designs/ISSUE-027-design.md，固定先验证RESPONSE_ONLY非法组合与规则匹配的实施步骤。
- **State evidence:** 2026-09-14用户明确批准按六部分继续拆成issue，按已有范围初始化READY；未开始实施，未创建专属设计/交接或生成新的验证结果。

### ISSUE-028

- **完成证据（2026-09-14）：** HTTP持久extraction、正式schema/examples/OpenAPI、严格前端及持续页面说明交付。最终acceptance后端1125项（含两包7项）、独立HTTP/MySQL19项、前端524项及受控浏览器66项全部通过，失败/错误/跳过0；独立前端与整体增量审查APPROVE。生产策略与25轮822case886请求索引字节保持，未执行真实SOURCE/TASK。[验收记录](../../verification/ISSUE-028-response-only-contract-ui.md)证明四项Acceptance，记录IN_PROGRESS → COMPLETED；母任务仍IN_PROGRESS。

- **启动证据（2026-09-14）：** 用户明确“完成issue28”，消费现有专属设计及ISSUE-027交付，记录READY → IN_PROGRESS；从正式schema/前端DTO失败测试开始，保留既有暂存与生产/真实证据。

- **准备证据（2026-09-14）：** ISSUE-027已记录COMPLETED；按Order2选取本项，完成专属设计并先回填链接，再写入[后继交接](../ISSUE-028-handoff.md)，记录NOT_STARTED → READY。输入为已验证的public summary/新枚举；第一动作是按设计补正式schema与DTO失败测试，尚未开始HTTP/页面实现。

- **Goal:** 让接口和页面准确展示采集规则与任务结果，历史任务的说明来自其保存的策略。
- **Scope:** 消费ISSUE-027的policySummary，增加TaskResponse.extraction并保持列表/详情一致；SINGLE为null，RANGE为准确规则/版本对象。 同步download-task.schema.json、download-task-examples.json和openapi-v1.yaml的新枚举、模式约束及示例；保持严格字段校验。 更新前端DTO解析、下载页、任务列表/详情及对应fixture；持续展示完整性未确认说明，区分非空采集成功、空响应和失败。 新旧任务与当前能力变化分别回归；不改生产候选矩阵，不升级验收索引或执行真实SOURCE/TASK。 详见[问题文档](../../issues/problems/ISSUE-028-response-only-contract-ui.md)。
- **Acceptance:** HTTP实际响应、正式schema/examples/OpenAPI和严格前端解析一致；RESPONSE_ONLY要求无阈值/不可拆/原生，缺失或错误extraction拒绝。 提交前及任务列表/详情持续显示“数据完整性未确认，可能存在上游截断”；非空成功为“返回记录已采集”，空为“本次请求未返回记录”，失败不显示成功。 旧RANGE显示保存的规则/版本，当前能力变化不改写历史含义；SINGLE保留单次语义，不新增确认弹窗或勾选步骤。 后端契约、前端和受控页面回归及独立审查通过；向ISSUE-029交付可被harness严格检查的能力/任务响应合同，生产开放状态保持。
- **Dependencies:** ISSUE-027。
- **Sources:** `docs/issues/problems/ISSUE-028-response-only-contract-ui.md` → `docs/task-designs/ISSUE-026-design.md`（共享设计Approach第3节；ISSUE-027实际交付的summary与枚举；正式HTTP合同和当前前端严格解析/页面。）→ `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-runs.md`。
- **First action:** 读取ISSUE-027实际交付的summary合同，对照共享设计第3节的正式schema及页面规则，完成并回填docs/task-designs/ISSUE-028-design.md。
- **State evidence:** 2026-09-14用户明确批准按六部分继续拆成issue，按已有范围初始化NOT_STARTED；未开始实施，未创建专属设计/交接或生成新的验证结果。

### ISSUE-029

- **完成证据（2026-09-14）：** schema1/2、RESPONSE_ONLY严格证据和完整来源绑定交付；新run `issue026-mainbz-split-source-20260914T013736Z` 四SOURCE均PASS，42请求、19 SPLIT父、23成功叶、1299来源行，exit0/cleanup PASS及冻结源码/两包稳定。旧25轮822case、40个interface及旧四满额逐对象保留，原12/新4精确身份绑定通过；索引现26轮826case928请求，生产仍4/30/6。Node98、Probe67、相关154及隔离acceptance后端/包1132、前端524通过，失败/错误/跳过0；组件、整体及实际证据独立审查均PASS。[验收记录](../../verification/ISSUE-029-range-evidence-and-split-source.md)证明四项Acceptance，记录IN_PROGRESS → COMPLETED；候选和TASK/SQL留后继，母任务保持IN_PROGRESS。

- **启动证据（2026-09-14）：** 用户明确“完成issue29”，完整消费专属设计及ISSUE-028交接，记录READY → IN_PROGRESS。按计划从指定来源/schema失败测试开始；旧来源、生产注册与前序未提交成果保留，真实四项SOURCE尚未执行。

- **准备证据（2026-09-14）：** ISSUE-028已完成并交付严格HTTP/页面合同；按Order3选取本项，完成并先链接专属设计，再写入并链接[后继交接](../ISSUE-029-handoff.md)，记录NOT_STARTED → READY。第一动作为指定来源绑定及schema版本失败测试；没有开始实现、升级索引或执行新SOURCE。

- **Goal:** 让证据工具识别响应采集并精确绑定来源，取得主营业务全年及六年宽窗的四项完整拆分SOURCE。
- **Scope:** 实现共享设计第4节的schema1/2兼容、独立RESPONSE_ONLY证据规则，以及harness对能力、持久extraction、单请求单叶子的核对；通过后才升级正式索引schemaVersion。 selectTaskCases按明确runId/caseId来源引用选择，拒绝错配和歧义；受限兼容旧#caseId/唯一候选，初始证据与执行共用sourceBindings。 仅扩展测试侧fina_mainbz RANGE探针，按已批准100阈值递归日期二分，记录完整真实SPLIT树，父不计成功行/投影，单日满额失败。 固定两股票000001.SZ/600000.SH各20250101～20251231、20200101～20251231，执行四新SOURCE并保存真实身份；不创建TASK/SQL，不修改生产候选开放标记/版本。 详见[问题文档](../../issues/problems/ISSUE-029-range-evidence-and-split-source.md)。
- **Acceptance:** schema1历史可读且不能承载新规则；schema2缺决定/错接口/伪完整/错身份/无任务或SQL不能通过。旧25轮822case逐对象保留，新增来源仅追加。 同参数两个清洁SOURCE分别按指定身份绑定，错误不回退first-match；特别是新000001-annual不能绑定回旧74行whole，原12项仍绑定指定旧来源。 四新SOURCE均有完整合法覆盖、成功叶子<100且至少一叶子非空、清洁退出/清理和稳定源码/两包；旧四满额EVIDENCE_MISSING不重标，未执行或失败不当PASS。 Node及Probe局部回归、隔离构建与独立审查通过；交付四项新精确来源供ISSUE-030/031。SOURCE没有TASK/SQL事实，十一项当前UNKNOWN不因工具支持而直接开放。
- **Dependencies:** ISSUE-028。
- **Sources:** `docs/issues/problems/ISSUE-029-range-evidence-and-split-source.md` → `docs/task-designs/ISSUE-026-design.md`（共享设计Approach第4节及“fina_mainbz 已承诺的真实拆分来源与任务”；ISSUE-028接口合同；ISSUE-020已批准方案、原12有效/4满额来源和唯一索引。）→ `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-runs.md`。
- **First action:** 消费已完成的ISSUE-029-design.md，在tushare-range-evidence.test.js补两个同参数清洁来源的指定run/case选择与歧义拒绝、schema1/2新规则测试，运行观察RED。
- **State evidence:** 2026-09-14用户明确批准按六部分继续拆成issue，按已有范围初始化NOT_STARTED；未开始实施，未创建专属设计/交接或生成新的验证结果。

### ISSUE-030

- **完成证据（2026-09-15）：** 30候选（11响应采集/18限量/1完整日历）、BJ→SSE规划和272精确来源绑定交付；893文件新snapshot/两包稳定。完整acceptance后端/包1133、前端524、Node104、受控UI66及新验收包metadata40全部通过，失败/错误/跳过0；metadata真实上游与任务提交0、进程/容器/临时秘密清理。独立组件、修正复审及整体源码/身份审查通过；正式4/30/6与全部26轮826case928请求保持，没有新真实SOURCE/TASK/SQL。[验收记录](../../verification/ISSUE-030-range-candidate-policies.md)证明四项Acceptance，记录IN_PROGRESS → COMPLETED；母任务保持IN_PROGRESS。

- **启动证据（2026-09-14）：** 用户明确“完成issue30”；完整消费专属设计与入口交接，保存前序工作快照并制定[实施计划](../../superpowers/plans/2026-09-14-issue-030.md)，记录READY → IN_PROGRESS。第一动作是策略/HTTP独立矩阵RED；真实SOURCE/TASK/SQL不属于本项。

- **准备证据（2026-09-14）：** ISSUE-029先记录COMPLETED后，按Order4选取本项；完成30项明确规则/日历映射、272项精确来源和独立预期的专属设计并先回填链接，再写入并链接[后继交接](../ISSUE-030-handoff.md)，记录NOT_STARTED → READY。直接消费ISSUE-027/028合同及ISSUE-029四项真实SOURCE/严格工具；第一动作为按设计补策略和HTTP能力矩阵失败测试，尚未开始候选实现、构建新候选包或执行TASK/SQL。

- **Goal:** 将已交付合同落实为可用于本地真实任务验收的30项候选策略和稳定构建。
- **Scope:** 按共享设计规则表逐项配置11个RESPONSE_ONLY、其余19个限量/完整日历候选，记录准确依据、日期与首次v2；四个已开放策略保持v2。 实现top_list BJ→SSE参照，保留原BJ证券；trade_cal直接BSE RANGE继续零来源调用拒绝，margin保留exchange_id。 消费ISSUE-029的新四SOURCE和全部既有输入，建立精确候选引用/规则及独立能力预期；历史空/失败留在runs，正式验收索引在TASK通过前保持NEEDS_VERIFICATION。 固定包含前序完整成果的候选源码、生产/验收包及输入摘要；执行必要局部/集成检查，不提交真实RANGE TASK或宣称最终开放。 详见[问题文档](../../issues/problems/ISSUE-030-range-candidate-policies.md)。
- **Acceptance:** 30候选分别匹配已批准规则、日期轴、参数/原键和有效来源；不能全表默认true、为UNKNOWN猜阈值或把RESPONSE_ONLY映射成完整规则。 BJ参照与直接BSE拒绝、严格限量等号/最小满额、11项单请求及独立能力/页面预期全部通过；保留40/34/6和31/3/6结构。 候选包具有实际可复核源码/两包/manifest/examples身份，供ISSUE-031运行；本地运行时候选AVAILABLE与最终证据AVAILABLE明确区分。 独立审查和相关回归通过；正式验收索引未仅因候选就绪将30项标AVAILABLE，向ISSUE-031交付稳定候选和来源映射。
- **Dependencies:** ISSUE-027, ISSUE-028, ISSUE-029。
- **Sources:** `docs/issues/problems/ISSUE-030-range-candidate-policies.md` → `docs/task-designs/ISSUE-026-design.md`（共享设计Approach第1/2/4节；ISSUE-027～029实际产物；ISSUE-026问题文档七组输入及唯一索引。）→ `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-runs.md`。
- **First action:** 消费已完成的ISSUE-030-design.md，在TushareBatchPoliciesTest和TushareBatchAvailabilityTest补上30项独立能力预期（含十一项不可拆RESPONSE_ONLY及新v2），运行定向Maven测试观察RED，再最小实现候选配置。
- **State evidence:** 2026-09-14用户明确批准按六部分继续拆成issue，按已有范围初始化NOT_STARTED；未开始实施，未创建专属设计/交接或生成新的验证结果。

### ISSUE-031

- **完成证据（2026-09-15，修订范围）：** 本次251/251项有既有准确SOURCE/参数/包绑定、TASK/SQL及清洁运行/独立审查；四接口27项按用户明确决定排除，保留4 FAILED/23 NOT_RUN及未解决问题。本次111/111证据测试及scope-audit通过，原45轮/1217case/1362请求逐对象、422相关源码中的运行/合同身份保持，正式30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY。四项修订后Acceptance与原件见[范围收尾验收](../../verification/ISSUE-031-range-live-task-verification.md#2026-09-15四接口排除与issue-031收尾)，记录IN_PROGRESS → COMPLETED。遵循用户要求不开始032，保持其NOT_STARTED/无交接；033本次跳过，母任务不关闭。

- **范围收尾启动（2026-09-15）：** 消费上述范围解除证据、专属设计和历史交接，按用户“先把这些问题跳过”执行本次范围记录、索引处置及离线保全核对，记录READY → IN_PROGRESS；不执行新的真实任务，不准备或启动032。

- **范围解除证据（2026-09-15）：** 用户明确排除balancesheet/cashflow/repurchase/fina_indicator的本次区间批量下载，并要求不开始032，已登记[范围决定](../../issues/proposals/ISSUE-026-range-scope.md)并修订专属设计。当前目标为251项，原27项保留为范围排除；三接口冲突不再阻塞本次，记录BLOCKED → READY。下面旧阻塞与运行记录继续保留为历史，入口交接不删除。

- **阻塞交接（2026-09-15）：** 独立续验及最后holders8+8/regression4已审查归档，当前272项为251清洁PASS/3 FAILED/18 NOT_RUN，唯一索引45轮/1217case/1362请求，另三个诊断各1请求单列。正式30/4/6与候选30/4/6一致，四失败接口保持v3撤回及SINGLE可用。balancesheet8/cashflow8/repurchase5仍在目标内，三个诊断均观察原键冲突、转换失败0；现金流doc44及两个唯一最新组只支持提案，尚未决定保留规则；回购记录身份未定。其他可独立工作已完成，先写入[暂停交接](../ISSUE-031-handoff.md)，再记录IN_PROGRESS → BLOCKED。恢复条件为明确三个接口的可执行保留/身份规则及设计，或用户明确批准另行递延/范围变更；未获准递延其余接口。最终Node111及272绑定/历史/身份/清理audit PASS；适用冻结包1137后端/524前端、定向158、metadata4通过。财务指标6项仍由033承接且未调用，032/033保持NOT_STARTED，母任务未关闭。

- **独立续验准备（2026-09-15）：** 已清洁接受205/272；公告失败轮8项income仅观察PASS，balancesheet1 FAILED/其余未执行。失败轮已停止保全，balancesheet撤回v3；一次原包原参数诊断复现原键冲突，仅total_share/update_flag不同，版本规则待用户决定。目标仍272，balancesheet8未默认递延；其余59按更新设计独立恢复。新冻结包后端1135/前端524/Node107/受控页面15/metadata40均PASS，纯选择59与新公告34预登记通过。当前IN_PROGRESS，033不执行，母任务不关闭。

- **续办启动（2026-09-15）：** 用户同一指令明确继续其他接口，消费更新后的设计与交接后记录READY → IN_PROGRESS。190项纯选择/完整SOURCE绑定校验通过，原82PASS保留；第一动作为dates29预登记与新空schema核验，未执行fina_indicator诊断。

- **阻塞范围解除（2026-09-15）：** 用户明确“财务指标先跳过，记录一个issue，再处理其他数据源”，承接为ISSUE-033；只解除其他接口的执行阻塞，不宣称适配根因已修复。先修订本项设计，记录BLOCKED → READY；当前272项有效范围，已通过82、待执行190，6项递延原事实保持。

- **阻塞证据（2026-09-15）：** 前三轮82 TASK通过；dates首项fina_indicator FAILED / ADAPTER_TYPE_INVALID，SQL0→0、attempt/request各1，另195项未运行。按设计停止，无自动重试/换样本，失败候选撤回v3，当前根因未确证。四轮cleanup PASS、旧26轮及四个schema保留；正式10/24/6。先写入[阻塞交接](../ISSUE-031-handoff.md)，再记录IN_PROGRESS → BLOCKED。恢复条件为安全诊断/脱敏复现、定向修复和明确重新冻结/补验映射，见[验收](../../verification/ISSUE-031-range-live-task-verification.md)。ISSUE-032不准备，母任务不关闭。

- **启动证据（2026-09-15）：** 用户明确“完成issue31”，完整消费专属设计与交接，记录READY → IN_PROGRESS。已只读核对893文件snapshot/422相关源码/两包/272绑定，机械扩展278项十轮清单并通过精确来源/数量/顺序校验。首轮limits已预登记，新库和真实任务待执行；前序未提交成果与全部历史保留。

- **准备证据（2026-09-15）：** ISSUE-030先记录COMPLETED后按Order5选取本项，完成十轮278项准确扩展、稳定两包输入、新库/预算、逐项SQL与失败撤回规则的[专属设计](../../task-designs/ISSUE-031-design.md)，先链接设计，再写入并链接[后继交接](../ISSUE-031-handoff.md)，记录NOT_STARTED → READY。直接消费ISSUE-029真实来源/严格工具和ISSUE-030候选/272绑定/新两包；没有启动本项、提交TASK或执行业务SQL。

- **Goal:** 按已批准的十轮固定计划，取得本次251项真实任务的页面、批次、数据库和日志验收证据；原278项中的四接口27项按用户决定排除。
- **Scope:** 复用ISSUE-029工具和ISSUE-030稳定候选，原十轮基线为28/16/38/35/37/33/24/42/21/4项，共278 TASK；用户已明确排除四接口27项，当前251项按原独立续验和补验映射核对，全部原记录保留。 覆盖268既有输入、四项mainbz全年/六年宽窗TASK、两项disclosure_date重下及四个已开放接口回归；精确来源身份与参数不得自行替换。 各轮新空schema、固定清单/身份/预算，核对页面、202/Location、全部批次、records、原键/归属/实际写入/摘要及日志；保留成功数据和旧SINGLE结果。 逐轮追加唯一验收索引/登记；失败或环境变化停止并记录实际结果，按母设计撤回失败接口候选且递增版本，不自动重试或换日期求通过。 详见[问题文档](../../issues/problems/ISSUE-031-range-live-task-verification.md)。
- **Acceptance:** 当前251计划项均有实际合格结果和清洁运行身份（另27项明确排除，不计PASS），source/insert/update与最终业务键数分开；整段/两端/股票保留和各代表场景有证据，未运行不冒充通过。 mainbz四TASK绑定新四SOURCE，至少一项真实观察到SPLIT父与完整成功子树；父无写入，原单日满额仍失败，受控拆分不能替代。 两次disclosure重下核对实际更新操作和SQL原键；本次8项RESPONSE_ONLY按响应采集合同验证，意外空任务的真实终态与非空代表性缺口分开记录。 所有拟标AVAILABLE的接口均有采用合同、有效SOURCE、匹配候选包TASK/SQL与清洁日志；局部核对/运行审查通过，交ISSUE-032统一回归和关闭，不提前关闭母issue。
- **Dependencies:** ISSUE-029, ISSUE-030。
- **Sources:** `docs/issues/problems/ISSUE-031-range-live-task-verification.md` → `docs/task-designs/ISSUE-026-design.md`（共享设计Approach第5节及mainbz真实拆分要求；ISSUE-029实际来源、ISSUE-030候选身份、ISSUE-026十轮输入和T13/T14执行合同。）→ `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-runs.md`。
- **First action:** 本次范围收尾已执行；四接口修复继续保留为后续问题，不诊断或补验，不准备或启动032。
- **State evidence:** 2026-09-14用户明确批准按六部分继续拆成issue，按已有范围初始化NOT_STARTED；未开始实施，未创建专属设计/交接或生成新的验证结果。

### ISSUE-032

- **最终收尾状态证据（2026-09-15）：** 本次30接口/251项准确验收、27项明确排除；最终六门禁1401/524/1134/1137/15/138全通过、0失败/错误/跳过，111证据测试、历史/源码/原包审计及最终增量独立审查PASS，清理完成。四条Acceptance成立，先完成本子issue；母任务随后逐条收尾，033不启动。 记录IN_PROGRESS → COMPLETED；[完整验收](../../verification/ISSUE-032-range-final-closure.md)。

- **启动证据（2026-09-15）：** 消费最新“完成issue32”指令及完整专属设计，记录READY → IN_PROGRESS。开始冻结全部工作字节、复核251/27精确绑定并按序运行六项门禁；保留原状态记录，033不启动。

- **准备证据（2026-09-15）：** 用户最新明确“完成issue32”，取代此前不启动的时序决定；四接口排除范围继续适用。专属设计已修订，前置031的251项实际验收与历史交接已读取，本项事实齐全，记录NOT_STARTED → READY；直接启动请求不生成031的后继交接，Handoff=None保持。

- **历史执行决定（2026-09-15，已被最新启动请求取代）：** 用户明确“不开始32”，保持NOT_STARTED与Handoff=None；已有预检草稿保留，不准备READY、不执行六门禁。

- **Goal:** 汇总所有子issue的真实结果，完成六条源码门禁、独立终审以及ISSUE-026/T14/T13和母issue关闭核对。
- **Scope:** 消费前序最终代码及ISSUE-031全部真实证据，复核六条源码门禁、受影响SINGLE/旧任务兼容和40项能力/版本/页面/手册的一致性。 在相同最终源码上补齐缺失或受改动影响的检查，保留测试结果与构建身份，不为每个子issue机械重复全套真实任务或回归。 完成独立终审及已批准完整性/历史/分类差异说明，确认30项RANGE纳入、4项明确排除和6 SINGLE_ONLY处理明确。 按各权威看板的实际状态和合法转换完成本issue、ISSUE-026、T14/T13及ISSUE-017/018关闭；保留旧状态证据，不自动提交、合并或发布。 详见[问题文档](../../issues/problems/ISSUE-032-range-final-closure.md)。
- **Acceptance:** 前序实现及251项纳入任务证据（原27项排除记录保留）满足共享设计第1～4项，并具备第5项母任务收尾所需的事实条件；原run/失败/空/成功数据保留，30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY有实际依据。母状态记录按下述完成顺序处理，不要求先关闭母任务才能完成本子issue。 六条门禁按既定顺序在最终适用源码上通过，数量/退出/跳过/清理有记录；已有同一代码且顺序合规的有效结果可复用，有新影响时补验。 接口规则、正式HTTP合同、历史快照、独立测试、页面和手册一致，独立终审无剩余阻断；接受漏数等用户决定不写成上游保证。 本issue验收成立后先记录其COMPLETED，再按实际状态核对并完成母任务关闭；任何母合同缺口如实保留，发布脚本未运行不声称发布通过。
- **Dependencies:** ISSUE-031。
- **Sources:** `docs/issues/problems/ISSUE-032-range-final-closure.md` → `docs/task-designs/ISSUE-026-design.md`（共享设计Approach第6节、Tests/Acceptance；ISSUE-031汇总证据与最终代码；T13/T14、总体设计§6及母issue关闭条件。）→ `docs/issues/problems/ISSUE-026-range-task-final-acceptance.md` → `docs/verification/ISSUE-018-range-acceptance.json` → `docs/verification/ISSUE-018-T14-runs.md`。
- **First action:** 本次设计已修订；冻结完整源码、复核精确251/27证据并按序执行六门禁。
- **State evidence:** 2026-09-14用户明确批准按六部分继续拆成issue，按已有范围初始化NOT_STARTED；未开始实施，未创建专属设计/交接或生成新的验证结果。

### ISSUE-033

- **当前范围决定（2026-09-15）：** 用户明确本次不需要fina_indicator的RANGE批量下载；本项保持NOT_STARTED并跳过，原6项及未解决根因保留，不再作为本次母任务关闭前置。

- **Goal:** 查明并修复fina_indicator适配失败，完成递延的6项原参数RANGE补验。
- **Scope:** 安全根因取证、最小修复、受控回归、新版本冻结及TASK/SQL闭环，详见[问题文档](../../issues/problems/ISSUE-033-fina-indicator-adaptation.md)。
- **Acceptance:** 可验证根因及回归；6项按新run/case与原SOURCE/参数在新schema通过，SQL/原键/日志/清理及独立审查成立后才恢复正式AVAILABLE；保留原FAILED/NOT_RUN与v3撤回事实。
- **Dependencies:** 已保留的ISSUE-031失败及ISSUE-022来源；本次范围已排除本接口，不构成本次母任务关闭前置。
- **Sources:** 问题文档 → ISSUE-031设计补充与验收 → 唯一索引 → 私有失败SQL和安全记录。
- **First action:** 用户恢复本接口时，完成专属安全取证/修复设计，先获取可验证分支或脱敏样本，禁止盲目改字段/键。
- **State evidence:** 2026-09-15用户明确先跳过、另记issue；只创建本项NOT_STARTED，不执行财务指标诊断或重试。Order7表示在当前其他接口工作之后处理；不以编号推定完整母任务可提前关闭。

## Risks

- 允许十一接口不完整不适用于fina_mainbz及其他19项；四项新SOURCE、至少一个真实TASK拆分及本次251项纳入任务仍是验收要求；原27项按明确范围排除。
- 后端与HTTP/前端、工具与候选策略共享类型或文件，按上述职责分步修改；候选开放标记/版本只归ISSUE-030，不能在早期任务顺带开放。运行失败撤回则归ISSUE-031。
- 权限/来源/临时环境可能变化，真实SOURCE/TASK必须重新核验当轮准备条件；意外空、失败与未运行如实记录，不能换日期制造成功。
- 看板初始化保留全部暂存成果、成功数据库和旧验收事实，只新增/更新任务文档并加入Git；后续实施仍保留前序成果，无自动提交、推送或发布。
