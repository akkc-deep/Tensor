# ISSUE-026：响应采集实现、剩余 RANGE 任务与最终验收

## 2026-09-15最终验收结果

本次批准范围已完成最终验收与状态收尾，详见[逐条结果](../verification/ISSUE-032-range-final-closure.md)。六门禁1401/524/1134/1137/15/138、111证据测试与独立终审通过；30个RANGE/251项纳入、四接口27项排除，SINGLE保持。032先完成，T14→T13→017/018→026随后完成；033未开始。下文旧阶段数字和不启动032的决定保留为历史，不作为当前未完成状态；原合同按明确范围决定解释。

## 2026-09-15本次范围调整

用户明确排除balancesheet、cashflow、repurchase、fina_indicator的RANGE批量下载，并要求不开始032，见[范围决定](../issues/proposals/ISSUE-026-range-scope.md)。当前目标为30个RANGE接口、251项TASK，原27项按EXCLUDED保留问题；本次关闭不再以其修复为前置。40接口SINGLE、原六个SINGLE_ONLY及纳入项的所有验收要求保持。下文34/278、272+6及四接口阻塞为旧阶段范围，本节优先；母任务仍待032最终验收，本次不关闭。

## 2026-09-15执行范围调整

用户要求先跳过财务指标fina_indicator，另记ISSUE-033并继续其他接口。原278项保持总追踪：ISSUE-031当前执行272项，另6项（1失败/5未运行）交ISSUE-033；原参数/来源/历史和母任务完整关闭要求不变。续办映射、撤回构建与顺序以[ISSUE-031补充合同](ISSUE-031-design.md#2026-09-15用户批准的续办范围)为准。新指令不授权额外财务指标来源诊断，当前准入仍v3/NEEDS_VERIFICATION。

## Goal

消费 ISSUE-019～025 已交付合同和来源，将剩余30接口逐项完成候选实现、真实任务/SQL和最终验收。十一接口按用户已接受的RESPONSE_ONLY采集返回记录，其他接口按既定限量或完整日历规则处理。最终目标为34 RANGE可用、6 SINGLE_ONLY；可用不等于十一项已证明完整。

身份为[后续看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md)Order8的ISSUE-026。创建时ISSUE-025已记录COMPLETED，本设计仅准备后继，未修改生产或执行账户任务。

## Scope

- 实现RESPONSE_ONLY公共合同、Tushare策略、runner、持久化策略摘要、HTTP/前端说明与证据消费者；只对ISSUE-025明确列出的十一项采用。
- 按ISSUE-019～024已确认规则开放其余19项候选；实现已取证的top_list BJ→SSE日历映射。保持trade_cal直接BSE RANGE拒绝，股票仍使用原BJ代码。
- 消费[问题文档的七组已交付输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md)：28+12+38+35+35+33+87=268项，其中4项为周/月线辅助日历；另加两项disclosure_date同参数重下、四项已开放接口回归，以及ISSUE-020已承诺的两股票全年/六年宽窗四项真实拆分TASK，共278个计划TASK。
- 保留40注册、34股票/6非股票、31原生/3逐日/6SINGLE_ONLY、原字段/业务键、V1–V8迁移和所有成功数据。既有25轮822case886请求及74项SINGLE/25项四接口RANGE原样保存，不回填或复制成新run。
- 不新增分页、type/VIP、通用逐日扫描、多股票合并、生产验证开关、自动重试或发布；不修改用户已接受的限量、历史查询及质押样本决定。

## Approach

### 子issue执行分工（2026-09-14）

用户明确“继续拆吧，继续拆成issue”，采用已讨论的六部分。ISSUE-026保留总设计、精确输入和最终验收责任，执行入口改为[ISSUE-027～032子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)。以下只分配已有范围，不增加功能、删验收或改变已批准决定。

| 子issue | 本设计责任 | 完成边界 |
| --- | --- | --- |
| ISSUE-027 | 第2节的公共规则/评估、Tushare通用规则分支；第3节的快照与policySummary | 后端受控验证；30项生产注册的候选标记/版本保持 |
| ISSUE-028 | 第3节的HTTP DTO、正式schema/examples/OpenAPI、前端解析与页面 | 接口/页面一致；不执行来源或开放候选 |
| ISSUE-029 | 第4节的证据工具/schema2/指定来源绑定；mainbz二分Probe和四项新SOURCE | 工具及四项清洁来源交付；没有TASK/SQL |
| ISSUE-030 | 第1/2节的30项生产候选注册、逐项证据/版本、BJ映射、独立能力预期及候选构建 | 本地候选可供验收，正式证据仍须等待真实TASK |
| ISSUE-031 | 第5节十轮278 TASK/SQL、四项mainbz真实任务拆分、两次披露重下及四接口回归 | 逐项真实结果；运行失败的候选撤回/版本递增由本项处理 |
| ISSUE-032 | 第6节、最终六门禁和五项Acceptance及母任务状态核对 | 最终代码/证据/文档一致并按合法状态收尾 |

各子issue先完成并回填自己的专属设计，当前设计引用为None；不能把本总设计全量实施当作任一子issue的范围。初始化ISSUE-027 READY，其余NOT_STARTED；本次不启动实现，ISSUE-026 READY与T13/T14状态保持。日后实际开始子issue实施时单独记录母任务启动证据。

局部检查、必要构建及真实调用前环境核验仍随相应任务执行；最终六门禁由ISSUE-032汇总，同一最终源码且顺序合规的有效结果可复用。268既有输入、四项mainbz新SOURCE、278 TASK和10轮计划全部保持，既有issue026命名及唯一索引不随子issue编号重写。这里的六项是总任务内部issue，不是新增T15，也不代替原看板的T13/T14验收。


### 1. 确定的规则与直接输入

参数形状、日期轴及每条SOURCE精确参数采用[T13矩阵](ISSUE-018-T13-design.md#40项实施矩阵)与[ISSUE-026输入表](../issues/problems/ISSUE-026-range-task-final-acceptance.md)。以下为全部30候选的规则，不由生产注册表生成独立测试预期。

| 所属输入 | API与规则 | 保留的语义 |
| --- | --- | --- |
| ISSUE-019 | daily 6000、forecast 3500、dividend 2000 | 用户接受的工程阈值；dividend逐自然日，其余原生 |
| ISSUE-020 | fina_mainbz 100 | 默认单次实际P/D/I分类，原键；不逐类拼接；100是工程拆分阈值 |
| ISSUE-021 | trade_cal完整日历、margin 4000、top_list 10000 | trade_cal仅SSE/SZSE；margin保留exchange_id三交易所；top_list完整日历后逐开市日，BJ参照SSE |
| ISSUE-022 | weekly 6000、monthly 4500、fina_indicator 100、stk_holdernumber 3000、new_share 2000 | 实际最后交易日；报告期/公告/申购轴不互换 |
| ISSUE-023 | block_trade 1000、disclosure_date 6000、stk_holdertrade 3000、pledge_detail 1000 | 同日多笔原键；披露逐自然日和最新修订；质押代表股票000014.SZ/600000.SH |
| ISSUE-024 | slb_len/slb_sec/slb_sec_detail均5000 | 已接受历史查询能力，起止和保留承诺仍未知；期限/费率原键不变 |
| ISSUE-025 | adj_factor、suspend_d、income、balancesheet、cashflow、fina_audit、express、repurchase、stk_managers、top10_holders、top10_floatholders均RESPONSE_ONLY | [逐接口与结果合同](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)，无rowLimit，原生单请求单叶子 |

ROW_LIMIT仍按原始行数<L为COMPLETE，>=L为SPLIT_REQUIRED；原生到单日仍满额失败，逐日接口当日满额失败。日历每天恰一条、含休市、无缺失重复，不能套RESPONSE_ONLY。四个已开放API的规则和v2不改变。

### 2. 公共合同和执行

在`BatchDownloadDescriptor.CompletenessRule.Kind`添加`RESPONSE_ONLY`，rowLimit必须null、evidence必须非空；descriptor要求该规则为NATIVE_RANGE且splittable=false。UNKNOWN原验证与AVAILABLE拒绝保持，CONFIRMED_ROW_LIMIT/VERIFIED_RULE原意保持。外部字段名completenessRule继续兼容，RESPONSE_ONLY明确定义为采集合同，不是完整保证。

在`BatchAssessment`添加独立`RESPONSE_ONLY`。`TushareBatchPolicies.RuleKind`同名扩展；十一项经过原Envelope、API/字段/行宽、请求、日期闭区间及归属检查后才返回此值。未sourceVerified仍返回UNKNOWN。计划为原请求一个叶子，sourceParameters保持，禁止数值阈值、自动拆分和额外补数。合法空同样返回RESPONSE_ONLY；response大小、时间和行数预算照常执行。

`DownloadTaskRunner.executeBatch`按持久化policy规则明确匹配评估：RESPONSE_ONLY策略仅接受RESPONSE_ONLY；严格策略只接受COMPLETE或适用的SPLIT_REQUIRED；UNKNOWN失败，规则/评估不一致按DATASET_MISCONFIGURED失败且不适配/入库。通过后复用现有budget→adapt→原子commit→终态，无新的成功状态。SINGLE仍走现有路径，不将其绑定RESPONSE_ONLY。

Policy构造不再用“所有原生非日历均splittable”推导；RESPONSE_ONLY明确不可拆。准入要求精确接口、已批准决定、有效SOURCE与本地回归，不能只把十一sourceVerified改true。生产候选依次v1→tushare-range-v2，verificationEvidence关联本决定、逐接口报告和有效case，documentationCheckedOn采用实际核对日期。失败撤回/再次开放都递增版本；旧任务摘要变化仍拒绝人工重放。

### 3. 持久化和HTTP/页面语义

继续使用现有`policy_snapshot` JSON记录规则和policyVersion，不改表/迁移，也不回填旧任务。`DownloadTaskJson`现有枚举序列化/严格读取须覆盖新规则和旧快照。保持definitionHash包含规则/版本，现有单批事务、失败、中断与重放行为不变。

在`DownloadTaskService`增加只读`policySummary(DownloadTask)`及`TaskPolicySummary(String policyVersion, CompletenessRule.Kind ruleKind)`：SINGLE返回null；RANGE只解析该任务持久化快照，不查当前插件或能力。非法快照沿用现有存储内容错误处理，不能回退为当前策略。`DownloadTaskResponse`添加必需的`extraction`字段，值为该summary或null；controller详情、列表使用同一转换，提交receipt和批次DTO不增加字段。此字段只含规则/版本，不暴露完整内部快照。

前端`downloadTaskDtos.js`严格接受新能力枚举，RESPONSE_ONLY须null行数、非空依据、NATIVE_RANGE且不可拆；任务RANGE必须有精确`extraction:{policyVersion,ruleKind}`，SINGLE必须null。旧RANGE任务从原快照返回原枚举，不因当前能力变化而变成响应采集。随打包前后端同步更新HTTP契约和所有受影响fixture。

正式发布合同同步`docs/contracts/download-task.schema.json`的CompletenessRule新枚举/组合约束及TaskResponse.extraction必需字段（additionalProperties=false保持，SINGLE null、RANGE精确对象）；`download-task-examples.json`补合法RESPONSE_ONLY能力、非空/空任务及旧严格规则/SINGLE示例，并保持原例合法。核对`openapi-v1.yaml`引用和说明，明确SUCCEEDED在该规则下只代表返回记录处理成功。`DownloadTaskContractTest`直接验证schema/examples，不能仅修改Java DTO和前端fixture。

复用现有页面布局，仅增加有用的说明：

- 下载页在RANGE表单与提交按钮附近，对RESPONSE_ONLY显示“按所选日期区间采集本次接口返回的记录。数据完整性未确认，可能存在上游截断。”不新增确认弹窗或勾选步骤。
- 任务列表/详情根据task.extraction.ruleKind持续显示“数据完整性未确认，可能存在上游截断”，与终态分开。SUCCEEDED且sourceRows>0显示“返回记录已采集”；为0显示“本次请求未返回记录”。其他状态保留排队/运行/失败/中断，不能因源返回或部分写入提前成功。
- 历史UNKNOWN RANGE只说明完整性未确认，不称完整下载；严格规则和SINGLE原说明保留。日期/计数/业务键展示不变，不能写“该区间没有数据”或“已全部下载”。

### 4. 证据消费者与候选索引

仅在消费者实现并通过拒绝测试后，将唯一索引schemaVersion从1升2；顶层和run/case字段保持不变，所有旧run逐对象不动。校验器可读取schema1/2，只有schema2允许interface.completeness.kind=RESPONSE_ONLY，限制为本十一API、NATIVE_RANGE、rowLimit null、非空evidenceRefs且decisionRef为ISSUE-025决定。evidenceRefs含决定、逐接口报告和原官方来源说明；官方未给出上限的事实仍在报告与原SOURCE，不改为官网保证。

现有UNKNOWN不可选择且不可AVAILABLE。RESPONSE_ONLY候选可用已校验、准确同参数的旧SOURCE PASS进行语义/归属绑定；它们的UNKNOWN文本保留，不伪造新来源。`selectTaskCases`仍检查唯一身份、精确参数、清洁SOURCE run与全部规则引用。未取得TASK前interface保持NEEDS_VERIFICATION；任务消费者须核对能力RESPONSE_ONLY、不可拆、rowLimit null、候选版本及task.extraction的持久规则/版本。

`selectTaskCases`必须从候选evidenceRefs解析指定来源，替换现有按runs顺序取第一个同参数PASS的逻辑：本issue全部新清单精确包含一个`docs/verification/ISSUE-018-range-acceptance.json#<runId>/<caseId>` SOURCE引用，先按该身份查找，再核对当前接口引用、清洁退出、SOURCE/RANGE/PASS、API、日期轴和精确params。引用不存在、run/case不对应、多个指定SOURCE或任一匹配条件不符均拒绝，不回退first-match。旧`#caseId`格式仅在全局唯一caseId明确指向合法来源时兼容；旧清单未指定来源身份时，仅允许恰一个合法同参数候选，有两个及以上必须拒绝歧义。历史run/case不重写，未来机械重建的清单一律使用完整身份；初始证据与实际harness共享同一sourceBindings对象。

必须测试同参数、两个清洁SOURCE都在当前引用时分别绑定指定来源及歧义/错身份拒绝。具体真实回归是新`000001-annual`与旧`issue020-source-20260913T131245Z-000001-whole`均为000001.SZ/20250101～20251231；原12项继续绑定指定旧SOURCE，新四项annual任务必须绑定新拆分SOURCE，不能因74行旧来源先出现而绑定回旧run。

新增RESPONSE_ONLY TASK的expectedCoverage固定`RESPONSE_ONLY`，expectedCoverageSource关联决定与准确SOURCE。harness不再把旧SOURCE的预期原样作为新TASK的结果语义；SOURCE绑定仍独立保留。reviewMethod记录已核对持久快照、单请求单成功叶子、原始/写入数、SQL及完整性未确认提示，实际固定字段由harness生成。PASS依然要求全部批次终态、SQL、日志和清洁身份；不得用自由文本关键词放行。

AVAILABLE校验对RESPONSE_ONLY仍要求同参数SOURCE/TASK两阶段、>=v2、清洁run及任务身份；新增该规则的TASK必须expectedCoverage=RESPONSE_ONLY、恰一个覆盖整段的成功叶子且requestCount=1。不能接受COMPLETE冒充此语义。严格规则原完整性/拆分/日历检查不变。

候选引用按七组交付表选择有效SOURCE、保留适用SINGLE TASK并追加新RANGE TASK；所有空/失败继续保存在runs，移出“当前可用候选引用”不是删除或重标。上游不完整这一已接受限制记录在RESPONSE_ONLY、decisionRef及报告中，不作为待用户决定；`unresolved`仅在实际实现/任务等验收缺口消除后清空。任何源码/包/输入/SQL/清理缺口仍阻止AVAILABLE。

合法空响应在产品层可SUCCEEDED；本次278项固定清单除已证实的三个top_list闭市计划外均有非空代表性预期。真实任务意外为空时保留任务真实SUCCEEDED，但对应代表性证据为EVIDENCE_MISSING，不算该非空验收通过，也不反写旧SOURCE。RESPONSE_ONLY空任务行为用受控来源与真实MySQL验证，不通过43个旧空SOURCE绕过当前正样本选择要求。

### 5. 固定真实TASK与SQL

从交付表和唯一索引机械重建268项，逐项保存来源run/case、精确params/dateAxis、采用规则、预期观察和请求预算。新增TASK caseId不与全部旧case重名，沿用交付表已指定的命名；每轮runId=`issue026-range-<组名>-<UTC>`，运行前固定实际值及私有清单摘要，不在设计中填造执行时间或结果。

按下列顺序分成十个独立新schema任务轮次；268项旧来源已有依据，新增四项fina_mainbz须先按下节取得新的完整拆分SOURCE，不重做其他来源调查：

| 顺序/组名 | 固定TASK集合 | 项数 |
| --- | --- | ---: |
| 1/limits | ISSUE-019交付全部 | 28 |
| 2/mainbz | ISSUE-020交付12项，加两股票全年/六年宽窗4项 | 16 |
| 3/calendar | ISSUE-021交付全部 | 38 |
| 4/dates | ISSUE-022交付全部，含4辅助日历 | 35 |
| 5/events | ISSUE-023交付35项，另各重下两个disclosure_date event一次 | 37 |
| 6/history | ISSUE-024交付全部 | 33 |
| 7/response-trade | ISSUE-025的adj_factor 8、suspend_d 16 | 24 |
| 8/response-announcement | income/balancesheet/cashflow各8、fina_audit 4、express 6、stk_managers 8 | 42 |
| 9/response-holders-repurchase | top10两项各8、repurchase 5 | 21 |
| 10/regression | 四个已开放接口各一个固定RANGE | 4 |

组内按API、股票分组，whole在该股票端点/重叠之前，第一股票全部处理后处理第二股票；一API全部在同一schema以复核历史保留。其余同组有效case均执行，不用宽窗替换已交付边界。disclosure两项新增caseId为`issue026-disclosure-000001-update-recheck`和`issue026-disclosure-600000-update-recheck`，分别复制交付表C的两项event参数及SOURCE绑定，紧随该股票原event执行，必须核对原键更新计数和最终SQL键数。受控修订另验证同键新内容替换；真实上游值未改变则如实说明，不能制造旧披露版本。

四项回归SOURCE来自`issue018-t14-priority-source-20260913T092938Z`，case后缀为`daily_basic-cross-year`及`stk_limit-range/moneyflow-range/margin_detail-range`，均000001.SZ；精确日期从索引复制。新caseId为`issue026-regression-<完整SOURCE caseId>`。只验证本次通用runner/HTTP改动未破坏既有严格规则，四策略保持v2；旧完整两股票和边界证据保留。

本次改动不改变SINGLE请求/客户端/适配/YAML路径；既有74个真实SINGLE保留，以现有SINGLE runner/HTTP/页面受控回归验证新字段null和隔离，无需重跑完整账户SINGLE。若实施超出上述边界触及SINGLE下载路径，先记录新影响范围和对应固定补验，不默默沿用旧身份。

每轮复用T13/T14正式harness：完整源码（含未提交文件）带入隔离clone并逐文件核对，冻结两包及manifest/examples实际SHA-256；MySQL8.4.6新空schema、8迁移/52表，成功数据保留。目录0700/文件0600，Token只进后端，HTTPS、间隔>=2000ms、每轮30分钟/5000请求、串行workers1/retries0、无trace/screenshot/video。固定请求预算从各case规划和已知日历计算；ROW_LIMIT拆分仍受全局预算，不预填实际请求数。按登记先后执行，不并行占账户配额。

每TASK核对页面→202/Location→详情与所有批次→两次records查询→SQL原业务键/股票或交易所归属/摘要→日志与构建身份。SOURCE原始行数、实际插入/更新操作、最终键数分开；父SPLIT不适配/写入。fina_mainbz全年/六年宽窗按下节新固定SOURCE和四TASK实际验收，旧4满额SOURCE保持不可选择；受控>=100场景仅证明算法和失败边界，不能替代这四项真实任务。

失败、输入变化或清理不通过立即停止该轮并记录已执行/未执行，失败接口撤回候选且递增版本；不自动重试、删成功数据或改样本。需要新来源时必须基于新可核验证据登记，单纯为空不能触发换日期直到成功。

### fina_mainbz 已承诺的真实拆分来源与任务

ISSUE-020[已批准方案第4项](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)保留全年和六年宽窗真实拆分验收。执行前登记新的`issue026-mainbz-split-source-<UTC>`，四case后缀为`000001-annual/600000-annual/000001-wide/600000-wide`；股票分别000001.SZ/600000.SH，annual精确20250101～20251231，wide精确20200101～20251231，均REPORT_PERIOD/end_date、省略type。这是原已观察窗口的新规则取证，不更换日期追求非空。

仅扩展测试侧`TushareRangeSourceProbe`的新fina_mainbz RANGE执行：复用当前请求白名单、客户端、SOURCE无库入口和Envelope/范围/股票校验；用已批准100阈值对原始响应评估，>=100且多日则调用现有`DateRangePlanner.split`产生左右相邻闭区间，深度优先先左后右；<100为该采用口径下的成功叶子（含合法空），单日>=100以BATCH_COMPLETENESS_UNCONFIRMED失败。不调用被sourceVerified阻止的生产plan，不把生产开关改true供探针取数；其他API/SINGLE取证行为不改。

初始四个完整父窗口、二分算法、case/run身份、最坏预算与实际清单哈希先固定；子窗口是预定算法结果，不能根据有数据日期跳日或换窗。每次请求仍预约现有5000/30分钟预算和>=2000ms节流；失败停止当前轮，保留在途/未运行事实，不自动重试。新SOURCE可能需要多次请求，准确数量必须实测；不得沿用旧每case一请求假设。

在现有batchNodes中记录真实根及每次SPLIT父、两相邻子节点和所有最终叶子；SPLIT父不计来源成功行数、不做分类/键/日期投影，不把父响应合并入叶子。请求数包含父和子。只对完整成功叶子投影原安全P/D/I、业务键及日期计数；整棵树覆盖父请求、所有叶子<100且至少一叶子非空，清洁退出后SOURCE才PASS。任一未执行/失败/最小满额保留实际失败或EVIDENCE_MISSING，不能生成成功覆盖。`candidateLimitReached`如仍记录，明确代表遇过满额父，不等于存在未解决满额叶子；reviewMethod分别记录拆分父数、规则ROW_LIMIT=100及是否仍有未解决叶子，不回填旧case。

新SOURCE case字段沿用既有schema，expectedCoverage=`ACCEPTED_ROW_LIMIT_COVERAGE`，expectedCoverageSource关联ISSUE-020决定；真实完整树通过原validateTree/来源身份校验后追加唯一索引。四个旧满额EVIDENCE_MISSING及其SOURCE当前历史引用保持；候选选择新四case并绑定其实际run，不重标旧case或允许任意EVIDENCE_MISSING通过消费者。

新增四TASK caseId=`issue026-task-<完整新SOURCE caseId>`，复制对应完整父窗口/股票/日期轴，与mainbz原12项在同一schema执行；每股票whole/annual/wide在端点重下前。任务生产runner自行按100规则拆分，核对真实SPLIT父未写、完整无重叠成功叶子、原业务键/分类、实际source/insert/update及SQL最终键集合。至少一项须在真实TASK观察到SPLIT父和成功子树；若本轮上游均少于100导致未触发，合法任务状态如实保留，但真实拆分场景仍EVIDENCE_MISSING，不能用受控拆分或这十一项RESPONSE_ONLY决定代替。

Probe本地回归覆盖多层左右二分、合法空叶子、父不计行/分类/键、单日满额、坏Envelope/越界、预算/停止、部分树和其他API/SINGLE隔离；Node验证分裂SOURCE与同父窗口TASK严格绑定，拒绝树缺口/重叠、引用旧4满额或错SOURCE身份。完成测试后冻结新SOURCE源码/两包再执行四case；生产候选包TASK另绑定实际身份。

### 6. 最终验收与状态

先完成本issue全部范围与门禁，逐接口更新唯一索引和40行报告。用户接受的漏数、未知历史保留、工程阈值和默认分类分别保留，不能写成上游新保证。最终受影响来源/候选包、SQL、版本/能力/页面和手册一致，再独立复审。

复核[T14 Acceptance](ISSUE-018-T14-design.md#acceptance)、[T13 Acceptance](ISSUE-018-T13-design.md#acceptance)、总体设计§6与ISSUE-017/018关闭条件，均使用已明确的限定决定。后续看板只管理本issue；原看板上的T14/T13按当时实际状态和既定BLOCKED→READY→IN_PROGRESS→COMPLETED路径收尾，不跳状态。仍有缺口则保留准确未完成状态，不能因34个布尔准入均true就关闭母issue。无后继，不创建T15；不自动commit/push/发布。

## Files

| 路径 | 责任 |
| --- | --- |
| `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/BatchDownloadDescriptor.java`、`BatchAssessment.java`及对应Descriptor测试 | 公共独立规则/评估与组合校验 |
| `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java` | 十一响应采集、其余19候选、BJ映射和逐项证据/版本 |
| 同插件测试`TushareBatchPoliciesTest.java`、`TushareBatchDownloadTest.java`、`TushareTradeCalendarTest.java` | 独立矩阵、非法组合/归属/空与限量/映射回归 |
| 同插件测试`TushareRangeSourceProbe.java`、`TushareRangeSourceProbeTest.java` | 仅新fina_mainbz RANGE的100阈值二分SOURCE，安全完整树及投影 |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRunner.java`、`DownloadTaskService.java`、`DownloadTaskJson.java`及对应Test/IT | 精确评估匹配、持久快照摘要、事务/旧任务/新旧JSON |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskResponse.java`、`DownloadTaskController.java`及控制器/契约/准入测试 | 只读extraction对象及列表/详情一致性 |
| `docs/contracts/download-task.schema.json`、`download-task-examples.json`、`openapi-v1.yaml` | 正式HTTP新枚举、extraction必需/模式约束、示例及说明，原严格拒绝保持 |
| `control-plane/src/api/downloadTaskDtos.js`与相关spec；`views/DownloadView.vue`、`views/DownloadTaskView.vue`、`components/download/DownloadTaskList.vue`及对应spec | 严格解析、提交前说明、历史任务与终态文案 |
| `control-plane/e2e/tushare-range-evidence.js`、`.test.js`、`tushare-live.spec.js` | schema2、SOURCE绑定与RESPONSE_ONLY TASK验证 |
| `control-plane/e2e/ui-redesign.fixtures.js`、`tushare-metadata.spec.js`、`download-tasks.spec.js`及受影响fixture | 独立能力/HTTP字段预期、普通浏览器合同 |
| `docs/verification/ISSUE-018-range-acceptance.json`及`.md`、`ISSUE-018-T14-runs.md` | 唯一索引升级、原run保留、固定计划/实际结果/最终证据 |
| `docs/runbook/configuration.md`、`docs/runbook/first-run.md`；设计、issues与两看板 | 产品说明、限定决定和合法状态收尾 |

只按实际消费者影响增加最小修改；不为新枚举创建新数据库表或保存原始响应。新增文件加入Git；独立源码包含全部暂存成果，不从HEAD遗漏已有工作。

## Tests

从隔离仓库根使用Java21/项目Node24。普通命令移除真实账户、数据库和live环境；先补以下有意义的缺失行为测试再最小实现：

- RESPONSE_ONLY合法非空/空且一请求一叶子；跨股票/错日期/字段错误仍拒绝；没有rowLimit、不能拆分；UNKNOWN和规则/assessment不匹配零入库。真实MySQL验证所有响应行按原键提交、空零写入、适配/写入失败不成功；严格阈值等号/单日与完整日历旧回归保持。
- 旧v1/v2快照读取、当前能力变化后历史说明稳定、摘要变化拒绝人工重放；详情/列表新字段严格，SINGLE为null、旧UNKNOWN不冒充完整。
- 页面提交前和终态提示持续可见，非空/空/失败及旧严格规则区分；schema1历史可读、schema1不能写新规则，schema2非法范围/缺决定/伪完整/无任务/错版本或SQL均拒绝，全部旧run逐对象保持。

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -o -f data-plane/pom.xml -Dtest=TushareRangeSourceProbeTest,BatchDownloadDescriptorTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskServiceTest,DownloadTaskJsonTest,DownloadTaskContractTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
```

真实执行前在私有环境按T13/T14核对SOURCE索引、清单权限、sourceBindings、完整源码及两包、schema和SQL defaults。先发现每组精确API及case数；发现不算执行。Token不进命令行，以下命令只在当前轮准备证据成立后执行，全部十轮逐轮登记实际结果：

```sh
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js --list
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js
```

生产改动完成后按既定顺序运行六条门禁，实际数量从报告核对，失败/未解释跳过不算通过；不把测试fixtures算真实Tushare：

```sh
mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js
npm --prefix control-plane run test:e2e
git diff --check
git diff --cached --check
```

普通浏览器沿用T12四独立库/preview及7文件126项基线，新增场景记录实际增量，不删旧检查。完整`sh scripts/verify-contracts.sh`仍受main/干净受保护输入/HEAD条件限制；不自动提交、切分支制造条件，未运行如实记载。

## Acceptance

1. 十一接口已实现明确RESPONSE_ONLY且所有消费者一致；仅采集返回行，完整性未确认可见，合法空和失败含义准确。UNKNOWN仍拒绝；其余23项RANGE的严格/已采用阈值或完整日历规则保持。
2. 七组268来源输入和四项新fina_mainbz拆分SOURCE均精确绑定，并完成278项固定TASK（另含2重复更新、4回归）的页面、全批次、SQL和清洁身份验证；母合同各代表场景成立，原SINGLE和历史失败/空/未运行事实保留。
3. 每个开放项具备采用合同、有效SOURCE和匹配候选版本/包的真实TASK；能力/独立测试/任务持久快照/页面/运行说明一致。全部34目标具备实际结果才形成34 AVAILABLE/6 SINGLE_ONLY，不靠排除项或更名UNKNOWN完成。
4. 局部回归、六条源码门禁、输入/私有权限/清理/原对象保持和最终独立审查通过；任何未执行或失败如实记录。278是计划数，不是本设计已执行的结果。
5. T14/T13与母issue按已批准差异逐条复核成立并合法收尾。未满足则记录具体剩余项，不宣称完成，不自动提交或发布。

## Risks

- 用户接受漏数不等于上游已保证完整；十一接口仍可能截断，不能用重复请求或窄窗样本消除这一限制。
- 上游可修订或变空、权限与临时环境可变化。旧SOURCE是有身份的证据，当前TASK仍必须独立验证；无有效新证据时不能重新选择日期直到成功。
- 新规则穿过通用runner、HTTP与严格前端解析，必须验证规则匹配和旧任务兼容；SINGLE路径或生产字段/键若发生额外变化须先记录设计影响。
- 完整发布脚本前置可能未满足；六门禁与真实任务验收不能冒充已发布。已成功数据库/任务和原工作树继续保留。
