# ISSUE-026：完成剩余区间任务验收与 T14 收尾

## 当前状态

IN_PROGRESS（2026-09-14）。用户要求“完成issue27”，母任务随首个子issue实际启动。ISSUE-027后端采集实现及受控验收已完成；HTTP/页面、候选、278项真实任务及最终门禁继续由ISSUE-028～032承担。准确状态见[后续看板](../../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md)及[子issue看板](../../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)。[共享设计](../../task-designs/ISSUE-026-design.md)和[入口交接](../../task-handoffs/ISSUE-026-handoff.md)继续适用。下文NOT_STARTED是各输入交付当时的快照；原真实SOURCE/TASK/SQL及生产准入没有因后端局部完成而改写。

## 目标与范围

消费 ISSUE-019～025 的逐接口规则和来源证据，完成剩余 30 接口的真实 RANGE / SQL、回归与母任务验收。

覆盖 ISSUE-019～025 主责的全部 30 个待验证接口；保留既有 4 个开放与 6 个 SINGLE_ONLY。

## 已知问题与证据

- 基线为 40 接口：4 AVAILABLE、30 NEEDS_VERIFICATION、6 SINGLE_ONLY；完整 SINGLE 74 任务 / 148 查询已通过。
- 已开放四接口的 25 RANGE 任务 / 50 查询通过；其余 30 项未完成 RANGE TASK / SQL。
- 创建时索引8轮/475case；ISSUE-019补证后为10轮/513case，累计461次来源请求；旧 T13 三轮 / 329 case 与成功任务原样保留，不能重包装为新通过。

来源：[当前验收报告](../../verification/ISSUE-018-range-acceptance.md)、[唯一 JSON 索引](../../verification/ISSUE-018-range-acceptance.json)、[公开补证](../../verification/ISSUE-018-T14-official-evidence.md)。这些是已有事实，创建本 issue 没有产生新的 API / TASK / SQL 结果。

## 依赖与处理顺序

ISSUE-019、ISSUE-020、ISSUE-021、ISSUE-022、ISSUE-023、ISSUE-024、ISSUE-025 的规则与有效来源结果。具体顺序为 ISSUE-019 → ISSUE-020 → ISSUE-021 → ISSUE-022 → ISSUE-023 → ISSUE-024 → ISSUE-025 → ISSUE-026；不得将顺序误作已经完成的上游输入。

第一动作：进入子issue看板的ISSUE-027，按已分配的后端范围完成其专属设计；后续按027→028→029→030→031→032实施，不在总任务下重复启动全部范围。

## 子issue分工（2026-09-14）

用户已明确要求将讨论的六部分继续拆成issue。总任务状态由原后续看板管理，子项身份/顺序/状态唯一由[子issue看板](../../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)管理；此处仅列分工及直接子项前置。

| 子issue | 工作范围 | 直接前置 |
| --- | --- | --- |
| ISSUE-027 | [实现允许不完整的后端响应采集](ISSUE-027-response-only-backend.md) | None |
| ISSUE-028 | [贯通响应采集接口合同与页面说明](ISSUE-028-response-only-contract-ui.md) | ISSUE-027 |
| ISSUE-029 | [升级验收工具并补齐主营业务拆分来源](ISSUE-029-range-evidence-and-split-source.md) | ISSUE-028 |
| ISSUE-030 | [配置剩余接口候选策略与日历映射](ISSUE-030-range-candidate-policies.md) | ISSUE-027, ISSUE-028, ISSUE-029 |
| ISSUE-031 | [完成278项真实区间任务与SQL验收](ISSUE-031-range-live-task-verification.md) | ISSUE-029, ISSUE-030 |
| ISSUE-032 | [完成最终回归与母任务收尾](ISSUE-032-range-final-closure.md) | ISSUE-031 |

ISSUE-027初始READY，其余NOT_STARTED；专属设计尚未创建，实施前各自完成并回填。所有子项实际完成后仍需逐条满足本issue原关闭条件，拆分不作为关闭依据。278任务仍按既定10轮执行，不再拆10个issue。四项mainbz新SOURCE由029负责，四项对应TASK及至少一次真实拆分由031负责；既有case/run命名、原输入和历史结果保留。

## 关闭条件

- 每个开放接口都有适用提取规则、有效来源及匹配候选包的页面 / 全批次 / SQL / 日志；十一项按ISSUE-025已批准RESPONSE_ONLY采集验收并显式标注可能漏数，其余接口完整性合同保持。包括 daily 重叠更新与各专属代表场景。
- 新轮有固定输入、稳定完整源码、实际包摘要、新空 schema 和安全清理；失败 / 未运行不改为通过，成功数据保留。
- 相关测试、六条源码门禁、能力 / 版本 / 文档与最终审查通过；既有成功 SINGLE 证据按构建影响复核，不无故重跑全部账户任务。
- 复核 T14 Acceptance、T13 原 Acceptance 和母 issue 关闭条件；全部成立才按合法状态转换收尾，否则保留准确剩余项。

## 约束

沿用 [T14 设计](../../task-designs/ISSUE-018-T14-design.md)和 [T13 验收合同](../../task-designs/ISSUE-018-T13-design.md)。规则解释、默认参数、业务键、市场映射或历史承诺的实质变更须先记录依据及必要的用户决策；真实执行前完成本 issue 的专属设计。保留旧轮次和未验证状态，不在 issue 文档另造验收索引，不因拆分关闭 ISSUE-017 / ISSUE-018。

## ISSUE-019 已交付输入

2026-09-13用户明确同意[方案A](../proposals/ISSUE-019-documented-range-limits.md#决策记录)。以下输入供本issue执行时使用，不改变当前NOT_STARTED状态或替代ISSUE-020～025依赖。直接合同为[ISSUE-019设计](../../task-designs/ISSUE-019-design.md)、[固定计划与实际结果](../../verification/ISSUE-018-T14-runs.md#issue-019-独立来源取证)及[唯一JSON索引](../../verification/ISSUE-018-range-acceptance.json)。

| API | 已采用规则及请求范围 | SOURCE输入（精确caseId组成） | TASK项数 |
| --- | --- | --- | ---: |
| daily | L6000；单股票trade_date原生区间 | 前缀`issue019-source-20260913T122126Z-daily-` + `000001`或`600000` + `-whole/-lower/-upper/-overlap` | 8 |
| forecast | L3500；单股票ann_date原生区间 | 前缀`issue019-source-20260913T122126Z-forecast-` + `000001`或`600000`或`000005` + `-whole/-event-at-lower/-event-at-upper/-event` | 12 |
| dividend | L2000；单股票逐自然日ann_date | `issue019-source-20260913T122126Z-dividend-000001-`和`issue019-dividend-source-20260913T122801Z-`两个前缀各加`whole/event/event-at-lower/event-at-upper` | 8 |

- 共28项SOURCE PASS，来自两个exit0/cleanup PASS的新独立run。每个TASK复制所列SOURCE的apiName、精确params、dateAxis，start/end等于对应params值；使用本issue执行时新登记的runId/caseId，不能复用SOURCE或旧submissionId。evidenceRefs包含对应接口completeness的全部引用和该SOURCE身份。未触及阈值时预计36次来源请求（8+12+8+8），这只是新TASK预算，不是已发生计数。
- 现有selectTaskCases已在内存中的离线清单上验证28项，并逐项核对绑定的真实SOURCE runId/caseId；没有伪造TASK结果或写入正式run。参数无需重新猜测；实际执行前按T13/T14重建并绑定候选包、新空schema和私有固定输入。三个生产策略仍sourceVerified=false/v1/NEEDS_VERIFICATION，登记本地候选时才逐项更新证据、日期和v2；任务失败保留结果并按合同撤回候选。
- 当前dividend的SOURCE聚合EVIDENCE_MISSING，是因为当前引用保留了全部18项新SOURCE，其中10项是空对照：首轮平安银行上下邻日2项、浦发同周末6项、第二轮浦发上下邻日2项。它们不能用作非空代表性证据。建立可开放候选的当前引用时，仅选择上表对应8项非空dividend SOURCE并保留有效SINGLE TASK引用；全部10项空对照仍留在原runs和本交接说明，不修改原状态，不删除历史。daily/forecast当前SOURCE已经分别是上表8/12项。
- 两个分红whole case各含全部连续三个自然日，已保留中间非空和两侧空的成功叶子；选择whole并未跳过空日。平安银行20260815为周六预案，浦发银行20260331为预案公告，公开依据及安全观察可复查。不要改为交易日计划或实施公告日。
- daily先执行各股票whole，再执行端点与overlap；从真实任务及SQL核对完整业务键、股票归属、实际source/insert/update和第二股票写入后的第一股票历史保留，不从SOURCE数量推导写入数，也不假设上游价格永久不变。forecast核对ann_date边界，dividend核对整段完整自然日叶子及进度业务键；所有TASK均检查页面、202/Location、终态/批次、SQL与日志。
- 阈值按原始行数判断，小于L按已接受口径判定本片完整；等于或超过L要求拆分，原生区间到最小单日仍满额则失败，dividend单日满额也失败。UNKNOWN十一项及repurchase无参数默认值不在本决定范围。完整性口径、SOURCE、TASK与SQL各自保留，不单凭本次用户决定或SOURCE PASS记录AVAILABLE。

## ISSUE-020 已交付输入

2026-09-13用户明确“同意方案A（推荐）”，以下已采用规则、来源及任务样本正式交付，决定见[方案A记录](../proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)。本issue仍NOT_STARTED，其他依赖及实际任务验收要求保留。原始事实在[唯一索引](../../verification/ISSUE-018-range-acceptance.json)及[T14登记](../../verification/ISSUE-018-T14-runs.md#issue-020-分类与限量独立取证)，不得复制PASS另建run。

- 推荐覆盖：前缀`issue020-boundary-source-20260913T131859Z-` + `000001/600000` + `-whole`（2项），以及`issue020-source-20260913T131245Z-` + `000001/600000` + `-half/-annual/-event-at-upper/-event-at-lower`（8项）。另有首轮000001的`-whole/-both-boundaries`2项，合计12项RANGE SOURCE PASS。每TASK复制对应SOURCE精确apiName/params/dateAxis及start/end，不使用SOURCE caseId或旧submissionId，evidenceRefs须包含fina_mainbz.completeness的全部已采用引用及对应真实SOURCE身份；未触及阈值时12项任务预计12次来源请求，属于执行预算，尚未发生。
- 两股票首轮SINGLE各150行仅为SOURCE，已有T14两股票SINGLE任务/SQL另有真实身份，不能由本次计数生成SQL事实。
- 4个满额对照：首轮600000的`-whole/-both-boundaries`各110行，两股票`-wide-limit`各150行，均保留EVIDENCE_MISSING。它们不能通过删除警告、重标PASS或直接选择来绕过selectTaskCases；全年/宽区间实际拆分验证需要另外固定有效来源覆盖计划。
- 按已确认方案A，后续候选保留默认省略type和8列/原键，以100作工程拆分阈值，<100采用该口径，>=100拆分，单日满额失败，版本从v1递增v2；该口径为用户接受的工程约定，不是上游实际硬上限保证。默认P/D/I是源响应所含分类，不逐类拼接。当前所有新增样本键唯一且无跨类冲突，不足以承诺所有历史无冲突；任务SQL仍须复查键、股属、实际写入及内容。
- 采用后当前引用为新18项SOURCE和T14两项清洁SINGLE TASK，已移出旧失败轮的当前引用；旧run及其所有case仍原样保留。因4个满额对照，sourceStatus仍EVIDENCE_MISSING。建立未来候选时可选上述12项干净RANGE SOURCE及2项干净SINGLE TASK引用，旧观察全部保留在runs，不追认旧失败轮。不凭本节提前改sourceVerified、版本、生产准入或母issue状态。
- 已完成离线消费者核对：12项候选参数与上述真实SOURCE run/case逐项匹配，4项满额对照全部拒绝。此为结构核对，无TASK提交，采用决定和来源/任务事实分别保存，当前没有RANGE TASK/SQL。

## ISSUE-021 已交付输入

本项交付`trade_cal`、`margin`、`top_list`的规则、市场语义和38项可匹配SOURCE输入；生产映射、候选开放及TASK/SQL仍由本issue实施。原事实在[唯一JSON](../../verification/ISSUE-018-range-acceptance.json)和[固定计划/实际结果](../../verification/ISSUE-018-T14-runs.md#issue-021-日历交易所与交易日独立取证)，适用[ISSUE-021设计](../../task-designs/ISSUE-021-design.md)。不改变本issue NOT_STARTED或其他依赖。

以下A=`issue021-source-20260913T135006Z-`，B=`issue021-bj-source-20260913T135704Z-`。每项TASK必须复制对应SOURCE的`apiName/mode/params/dateAxis`，`start/end`等于其params中的日期；使用本issue执行时的新runId/caseId，evidenceRefs包含届时完整规则引用和准确SOURCE身份，不重用来源caseId或旧submissionId。

| API / 范围 | 精确SOURCE caseId组成 | 项数 | 规则与任务观察 |
| --- | --- | ---: | --- |
| trade_cal / SSE、SZSE | A + `trade_cal-` + `sse`或`szse` + `-whole/-lower/-upper/-closed/-cross-year/-current` | 12 | 每自然日恰一条，含全部休市日；两端及跨年来源已完整。SQL键为exchange/cal_date，休市日历仍有行 |
| trade_cal / 2024 BJ参照窗口 | B + `calendar-sse/calendar-szse` | 2 | 精确20240927～20241008，12自然日，开市20240927/20240930/20241008 |
| margin / SSE、SZSE、BSE | A + `margin-` + `sse`或`szse`或`bse` + `-whole/-lower/-upper` | 9 | 20260803～20260810及两端；保留exchange_id和原业务键。官网L4000，原始行数<L本片完整，>=L拆分，最小单日满额失败；SQL交易所不得串写 |
| top_list / SH、SZ | A + `top_list-` + `000007`或`600318` + `-whole/-event/-event-at-lower/-event-at-upper/-closed` | 10 | 官网L10000/单日；先校验完整SSE/SZSE日历再逐开市日。保留无证券数据的开市日叶子；SQL股票、日期和reason业务键复核 |
| top_list / BJ | B + `920008-whole/920008-event/920008-event-at-lower/920008-event-at-upper/920008-closed` | 5 | 测试来源已确认BJ→SSE参照，证券仍920008.BJ。生产exchangeForStock须在候选包中明确加入BJ→SSE并回归；不能把证券或直接BSE请求改成SSE |

合计38项：35非空、3个top_list完整休市零叶子；预计同一参数单次任务共62次来源请求（含规划日历），具体写入/更新数必须来自真实TASK和SQL，不能由SOURCE行数推导。两股票代表样本基于公开事件新增，原000001.SZ/600000.SH空样本和其SINGLE TASK原样保留。

- 生产当前仍三接口NEEDS_VERIFICATION/v1。满足规则与来源后先固定候选：trade_cal仅SSE/SZSE完整日历；margin原exchange_id三值；top_list依赖trade_cal并加入已验证的BJ→SSE映射，首次候选版本v2。重建稳定包后运行上述固定TASK、完整日期序列/叶子树及SQL；执行生产代码六门禁。不能凭本次SOURCE开放或宣称生产BJ已支持。
- BSE直接负例`issue021-bse-source-20260913T135304Z-direct`必须保留：合法Envelope0行、BATCH_COMPLETENESS_UNCONFIRMED、exit1/cleanup PASS，不能选为可成功TASK来源、不能改成PASS、不能用SSE代替exchange=BSE。生产BSE RANGE继续明确拒绝；新候选测试仍须验证拒绝在无来源调用时发生。只证明本次输入没有完整日历，不声称HTTP不支持或所有历史为空。
- 三接口当前引用仅本issue新SOURCE和原干净SINGLE TASK；旧空/失败SOURCE保留在runs。trade_cal当前sourceStatus=FAILED源自仍引用的新BSE负例，不表示14项沪深来源失败。候选开放时需在设计明确“支持的SSE/SZSE输入验收”和“BSE拒绝对照”分别归属后整理当前候选引用，保留负例原run，不伪造整体来源PASS。
- 本次未修改生产代码或新建任务数据库，原74个SINGLE TASK及四接口25个RANGE TASK未重跑；是否需因候选包改动补验，按本issue构建影响决定。

## ISSUE-022 已交付输入

五接口日期语义、两股票/原非股票整段及有效边界SOURCE已完成，原事实以[唯一JSON](../../verification/ISSUE-018-range-acceptance.json)和[两轮计划/实际结果](../../verification/ISSUE-018-T14-runs.md#issue-022-第一轮固定来源计划)为准，适用[专属设计](../../task-designs/ISSUE-022-design.md)。交付31项五接口非空来源和4项辅助日历，共35项候选；本issue继续NOT_STARTED，未提交TASK。

A=`issue022-source-20260913T141544Z-`，B=`issue022-holder-source-20260913T142200Z-`。下表后缀与A/B拼成准确SOURCE caseId；对应runId是去掉A/B末尾短横线的字符串。候选TASK caseId固定为`issue026-issue022-`加该后缀，执行时使用新runId、不重用来源caseId/submissionId。复制对应SOURCE的`apiName/mode/params/dateAxis`，start/end精确等于params日期，evidenceRefs包含完整规则引用及本表对应SOURCE身份。精确参数已全部持久登记于唯一JSON和运行表，不依赖私有临时清单恢复。

| API | SOURCE caseId组成 | 项数 | 规则与TASK观察 |
| --- | --- | ---: | --- |
| trade_cal辅助日历 | A + `cal-week-SSE/cal-week-SZSE/cal-month-SSE/cal-month-SZSE` | 4 | 每自然日恰一条；周参照20240923～20241013各21行，月参照20180901～20181031各61行。与ISSUE-021候选日历规则一致，SQL保留开休市记录 |
| weekly两股票 | A + `weekly-` + `000001/600000` + `-whole/-lower/-upper/-holiday-last` | 8 | L6000；整段20240927～20241011的主轴0927/0930/1011，单日两端及国庆周一0930均非空；与完整日历逐周最后开市日核对，不能固定周五 |
| monthly两股票 | A + `monthly-` + `000001/600000` + `-whole/-lower/-upper` | 6 | L4500；整段20180928～20181031、两端日期同名，实际最后交易日；不能以官网旧20180930样例改写返回日期 |
| fina_indicator两股票 | A + `indicator-` + `000001/600000` + `-whole/-lower/-upper` | 6 | L100；REPORT_PERIOD/end_date，20250331～20251231及两端；公告日在窗外合法，000001下端同报告期2行按原业务键保留，SQL不能按股票/报告期先去重 |
| stk_holdernumber首股票 | A + `holder-000001-whole/-event/-lower-edge/-upper-edge`（每项均保留holder-000001前缀） | 4 | L3000；公告窗20260801～20260831，事件0815，单日/事件在下端0815～0831/在上端0801～0815；ann_date筛选，end_date为统计截止日，不加入enddate参数 |
| stk_holdernumber第二股票 | A + `holder-600000-whole`；B + `holder-600000-event/holder-600000-lower-edge/holder-600000-upper-edge` | 4 | 同一公告窗，真实事件0828；单日及0828～0831/0801～0828。8项均同一行公告在内而截止日在外，TASK按各自ts_code独立核对 |
| new_share原非股票请求 | A + `ipo-whole/ipo-lower/ipo-upper` | 3 | L2000；20180905～20180927及两端，主轴ipo_date申购；issue_date是上市日期，不能替代筛选。SOURCE10/1/2行，两端有效且日期对照明确；保留多股票及原键 |

35项候选参数已由现有selectTaskCases全部接受，sourceBindings精确匹配本表run/case；4项空对照全部拒绝。私有离线输入`/private/tmp/issue022-control/issue026-candidate-inputs.json`和验证脚本/日志用于本次复查，不是另一个验收索引，不是已登记执行的TASK run。预计同参数任务35次来源请求，实际来源/插入/更新数量必须由TASK与SQL记录，不从SOURCE重叠样本推导去重行数。

- 四项空对照为A + `weekly-000001-holiday-friday/weekly-600000-holiday-friday/monthly-000001-natural-end/monthly-600000-natural-end`，分别20241004和20180930。原EVIDENCE_MISSING保持，不作为非空边界或成功TASK来源。
- 主责五接口当前引用为本轮所有SOURCE及原清洁SINGLE TASK；旧15轮和全部case保持。monthly的旧同参数20180928仍在历史run，但已不在该接口当前引用，实际消费者因此精确绑定A + `monthly-000001-lower`；没有修改消费者选取逻辑或篡改旧事实。
- weekly/monthly当前聚合EVIDENCE_MISSING仅因保留这4项空对照，其他三个主责接口SOURCE聚合PASS；生产五接口仍NEEDS_VERIFICATION/v1。候选开放时先明确成功样本与空对照的引用归属，保留原run；达到/超过L拆分、单日仍满额失败，首次候选版本v2，不能凭SOURCE或整数投影提前开放。
- 随后重建候选包并固定TASK输入、新空schema，核对完整批次、原始行数、业务键、股票归属、SQL和日志；生产策略改变再执行六门禁。原74个SINGLE及已通过四接口RANGE任务的补验需求按构建影响判断，不无故重跑全账户。

## ISSUE-023 已交付输入

四接口稀疏事件SOURCE已完成。按[专属设计](../../task-designs/ISSUE-023-design.md)及用户明确同意的质押样本决定，交付35项唯一参数的非空RANGE候选；全部事实仍仅以[唯一JSON](../../verification/ISSUE-018-range-acceptance.json)和[三轮登记](../../verification/ISSUE-018-T14-runs.md#issue-023-第一轮固定来源计划)为准。本ISSUE-026仍NOT_STARTED，未提交TASK或连接数据库。

A=`issue023-source-20260913T144308Z`。

B=`issue023-events-source-20260913T145005Z`。

C=`issue023-boundary-source-20260913T145716Z`。

下表SOURCE caseId为所列A/B/C runId加短横线及后缀；TASK caseId严格为`issue026-`加完整SOURCE caseId。mode全部RANGE，block_trade日期轴TRADE_DATE，其余ANNOUNCEMENT_DATE；start/end等于params内两日期。evidenceRefs保留该接口现有完整性引用，另附`docs/verification/ISSUE-018-range-acceptance.json#<SOURCE runId>/<SOURCE caseId>`。执行时创建新的真实TASK runId/submissionId及候选包身份，不复用SOURCE run/case。

| SOURCE run / case后缀 | API | 精确params |
| --- | --- | --- |
| A / `block-official` | block_trade | `{"ts_code":"601318.SH","start_date":"20181226","end_date":"20181228"}` |
| A / `holder-official` | stk_holdertrade | `{"ts_code":"300115.SZ","start_date":"20190425","end_date":"20190427"}` |
| A / `pledge-official` | pledge_detail | `{"ts_code":"000014.SZ","start_date":"20171216","end_date":"20180106"}` |
| A / `disclosure-official` | disclosure_date | `{"ts_code":"300619.SZ","start_date":"20181227","end_date":"20181229"}` |
| A / `block-000001-whole` | block_trade | `{"ts_code":"000001.SZ","start_date":"20230219","end_date":"20230221"}` |
| A / `block-000001-event` | block_trade | `{"ts_code":"000001.SZ","start_date":"20230220","end_date":"20230220"}` |
| A / `block-000001-lower-edge` | block_trade | `{"ts_code":"000001.SZ","start_date":"20230220","end_date":"20230221"}` |
| A / `block-000001-upper-edge` | block_trade | `{"ts_code":"000001.SZ","start_date":"20230219","end_date":"20230220"}` |
| A / `block-600000-whole` | block_trade | `{"ts_code":"600000.SH","start_date":"20220331","end_date":"20220402"}` |
| A / `block-600000-event` | block_trade | `{"ts_code":"600000.SH","start_date":"20220401","end_date":"20220401"}` |
| A / `block-600000-lower-edge` | block_trade | `{"ts_code":"600000.SH","start_date":"20220401","end_date":"20220402"}` |
| A / `block-600000-upper-edge` | block_trade | `{"ts_code":"600000.SH","start_date":"20220331","end_date":"20220401"}` |
| B / `holder-000001-whole` | stk_holdertrade | `{"ts_code":"000001.SZ","start_date":"20210906","end_date":"20210908"}` |
| B / `holder-000001-event` | stk_holdertrade | `{"ts_code":"000001.SZ","start_date":"20210907","end_date":"20210907"}` |
| B / `holder-000001-lower-edge` | stk_holdertrade | `{"ts_code":"000001.SZ","start_date":"20210907","end_date":"20210908"}` |
| B / `holder-000001-upper-edge` | stk_holdertrade | `{"ts_code":"000001.SZ","start_date":"20210906","end_date":"20210907"}` |
| B / `holder-600000-whole` | stk_holdertrade | `{"ts_code":"600000.SH","start_date":"20241219","end_date":"20241221"}` |
| B / `holder-600000-event` | stk_holdertrade | `{"ts_code":"600000.SH","start_date":"20241220","end_date":"20241220"}` |
| B / `holder-600000-lower-edge` | stk_holdertrade | `{"ts_code":"600000.SH","start_date":"20241220","end_date":"20241221"}` |
| B / `holder-600000-upper-edge` | stk_holdertrade | `{"ts_code":"600000.SH","start_date":"20241219","end_date":"20241220"}` |
| B / `disclosure-000001-revision-window` | disclosure_date | `{"ts_code":"000001.SZ","start_date":"20240930","end_date":"20241026"}` |
| B / `disclosure-600000-revision-window` | disclosure_date | `{"ts_code":"600000.SH","start_date":"20240630","end_date":"20240829"}` |
| C / `block-official-event` | block_trade | `{"ts_code":"601318.SH","start_date":"20181227","end_date":"20181227"}` |
| C / `pledge-000014-lower` | pledge_detail | `{"ts_code":"000014.SZ","start_date":"20171216","end_date":"20171216"}` |
| C / `pledge-000014-upper` | pledge_detail | `{"ts_code":"000014.SZ","start_date":"20180106","end_date":"20180106"}` |
| C / `pledge-600000-whole` | pledge_detail | `{"ts_code":"600000.SH","start_date":"20140323","end_date":"20140325"}` |
| C / `pledge-600000-event` | pledge_detail | `{"ts_code":"600000.SH","start_date":"20140324","end_date":"20140324"}` |
| C / `pledge-600000-lower-edge` | pledge_detail | `{"ts_code":"600000.SH","start_date":"20140324","end_date":"20140325"}` |
| C / `pledge-600000-upper-edge` | pledge_detail | `{"ts_code":"600000.SH","start_date":"20140323","end_date":"20140324"}` |
| C / `disclosure-000001-event` | disclosure_date | `{"ts_code":"000001.SZ","start_date":"20241009","end_date":"20241009"}` |
| C / `disclosure-000001-lower-edge` | disclosure_date | `{"ts_code":"000001.SZ","start_date":"20241009","end_date":"20241010"}` |
| C / `disclosure-000001-upper-edge` | disclosure_date | `{"ts_code":"000001.SZ","start_date":"20241008","end_date":"20241009"}` |
| C / `disclosure-600000-event` | disclosure_date | `{"ts_code":"600000.SH","start_date":"20240813","end_date":"20240813"}` |
| C / `disclosure-600000-lower-edge` | disclosure_date | `{"ts_code":"600000.SH","start_date":"20240813","end_date":"20240814"}` |
| C / `disclosure-600000-upper-edge` | disclosure_date | `{"ts_code":"600000.SH","start_date":"20240812","end_date":"20240813"}` |

- 现有selectTaskCases已接受全部35项，sourceBindings逐项精确绑定上表来源；原10项空/失败运行中的RANGE候选全部拒绝，空SINGLE也不能充当RANGE证据。私有核对输入`/private/tmp/issue023-control/issue026-candidate-inputs.json`和`source-bindings.json`只是可重建交付产物，不是另一个验收索引或已执行TASK。
- 三轮实际39case/131请求、38非空PASS、1空；其中37个非空RANGE里，C的`disclosure-000001-same-key-recheck`和`disclosure-600000-same-key-recheck`分别与已选event参数完全相同，仅作安全摘要复查，保留原case而不重复计35项唯一参数。若TASK验证需要再次执行相同参数，应另登记新caseId，并绑定上表event的有效SOURCE。
- block_trade L1000，保留原`[trade_date,ts_code,buyer,seller,price,vol]`键，官网单日5行/5键/5买卖方组合；两基准各2行/2键。不得按股票/日期去重；SQL分别核对源计数、原键数、归属和其他股票历史保留。
- stk_holdertrade L3000，基准两股票公告单日6/1行，begin/close均与公告不同且在单日窗外；这些两个附加字段只用于SOURCE对照，实际TASK仍使用原11列及原键，不能把业务起止日当请求轴。
- pledge_detail L1000，按用户决定采用000014.SZ与600000.SH：前者整段及20171216/20180106两端，后者20140324事件及上下包含窗口。原14列FINGERPRINT键保持；解押日期缺失是不可比较，不能算作范围外。000001.SZ新SINGLE0行仍在A及当前引用，不能按它证明非空或全历史为空；候选开放时选有效RANGE引用，所有空run/case原样保留。
- disclosure_date L6000，逐自然日ann_date；两基准实际最新公告20241009/20240813，对应报告期20240930/20240630。公开首次预约20241026/20240829已修改为20241019/20240820，实际SOURCE五列记录摘要与修改后预约/实际日期精确一致；modify_date为空，同键重复请求摘要稳定。这证明当次读到的记录对应公开修订结果，没有观察到本轮跨时点变化，不承诺每个旧版可回放。原`[ts_code,end_date]`键保持，TASK/SQL必须验证重复/更新操作及最终键数，不能只用sourceRowCount或相同摘要宣称已更新入库。需要受控修订场景时按T13/T14区分受控写入验证与真实上游事实，不伪造旧版本来源。
- 所有原始来源行数均小于各自L，没有新完整性例外。TASK阶段达到/超过L依原合同拆分，单日满额失败；逐日全部自然日叶子必须完整，SOURCE的空日不转成缺日。生产四接口仍NEEDS_VERIFICATION/v1，首次候选v2及真实TASK/SQL、六门禁由本issue后续验收。

## ISSUE-024 已交付输入

2026-09-14用户明确“同意方案 A（推荐）”，按[历史支持决定](../proposals/ISSUE-024-historical-support.md#决策记录)，三接口历史查询口径及两轮SOURCE的33项唯一非空参数正式交付；既有`selectTaskCases`逐项绑定通过，本节不代表生产准入或TASK/SQL通过。事实仅以[唯一JSON](../../verification/ISSUE-018-range-acceptance.json)及[两轮登记](../../verification/ISSUE-018-T14-runs.md#issue-024-历史来源验证)为准。ISSUE-026仍NOT_STARTED，未提交TASK或连接数据库。

A=`issue024-source-20260913T153146Z`。

B=`issue024-boundary-source-20260913T153639Z`。

下表SOURCE caseId为A/B runId加短横线及后缀；TASK caseId为`issue026-`加完整SOURCE caseId。全部mode=RANGE、dateAxis=TRADE_DATE，start/end等于params内日期。evidenceRefs保留现有完整性引用及`docs/issues/proposals/ISSUE-024-historical-support.md#决策记录`，另附`docs/verification/ISSUE-018-range-acceptance.json#<SOURCE runId>/<SOURCE caseId>`。实际TASK须预登记新的runId/submissionId并绑定候选包，不能复用SOURCE身份。

| SOURCE run / case后缀 | API | 精确params |
| --- | --- | --- |
| A / `len-whole` | slb_len | `{"start_date":"20240601","end_date":"20240620"}` |
| A / `len-lower` | slb_len | `{"start_date":"20240603","end_date":"20240603"}` |
| A / `len-upper` | slb_len | `{"start_date":"20240620","end_date":"20240620"}` |
| A / `slb_sec-000001-whole` | slb_sec | `{"ts_code":"000001.SZ","start_date":"20240601","end_date":"20240620"}` |
| A / `slb_sec-000001-official-day` | slb_sec | `{"ts_code":"000001.SZ","start_date":"20240620","end_date":"20240620"}` |
| A / `slb_sec-600000-whole` | slb_sec | `{"ts_code":"600000.SH","start_date":"20240601","end_date":"20240620"}` |
| A / `slb_sec-600000-official-day` | slb_sec | `{"ts_code":"600000.SH","start_date":"20240620","end_date":"20240620"}` |
| A / `slb_sec_detail-000001-whole` | slb_sec_detail | `{"ts_code":"000001.SZ","start_date":"20240601","end_date":"20240620"}` |
| A / `slb_sec_detail-000001-official-day` | slb_sec_detail | `{"ts_code":"000001.SZ","start_date":"20240620","end_date":"20240620"}` |
| A / `slb_sec_detail-600000-whole` | slb_sec_detail | `{"ts_code":"600000.SH","start_date":"20240601","end_date":"20240620"}` |
| A / `slb_sec_detail-600000-official-day` | slb_sec_detail | `{"ts_code":"600000.SH","start_date":"20240620","end_date":"20240620"}` |
| A / `detail-000001-long` | slb_sec_detail | `{"ts_code":"000001.SZ","start_date":"20230101","end_date":"20240620"}` |
| A / `detail-600000-long` | slb_sec_detail | `{"ts_code":"600000.SH","start_date":"20230101","end_date":"20240620"}` |
| A / `slb_len-all-suspension` | slb_len | `{"start_date":"20240701","end_date":"20240711"}` |
| A / `slb_len-all-settlement` | slb_len | `{"start_date":"20240930","end_date":"20241001"}` |
| A / `slb_sec-000001-suspension` | slb_sec | `{"ts_code":"000001.SZ","start_date":"20240701","end_date":"20240711"}` |
| A / `slb_sec-600000-suspension` | slb_sec | `{"ts_code":"600000.SH","start_date":"20240701","end_date":"20240711"}` |
| A / `slb_sec_detail-000001-suspension` | slb_sec_detail | `{"ts_code":"000001.SZ","start_date":"20240701","end_date":"20240711"}` |
| A / `slb_sec_detail-600000-suspension` | slb_sec_detail | `{"ts_code":"600000.SH","start_date":"20240701","end_date":"20240711"}` |
| B / `slb_sec-000001-lower` | slb_sec | `{"ts_code":"000001.SZ","start_date":"20240603","end_date":"20240603"}` |
| B / `slb_sec-000001-lower-edge` | slb_sec | `{"ts_code":"000001.SZ","start_date":"20240603","end_date":"20240620"}` |
| B / `slb_sec-000001-20240710` | slb_sec | `{"ts_code":"000001.SZ","start_date":"20240710","end_date":"20240710"}` |
| B / `slb_sec-000001-20240711` | slb_sec | `{"ts_code":"000001.SZ","start_date":"20240711","end_date":"20240711"}` |
| B / `slb_sec-600000-lower` | slb_sec | `{"ts_code":"600000.SH","start_date":"20240603","end_date":"20240603"}` |
| B / `slb_sec-600000-lower-edge` | slb_sec | `{"ts_code":"600000.SH","start_date":"20240603","end_date":"20240620"}` |
| B / `slb_sec-600000-20240710` | slb_sec | `{"ts_code":"600000.SH","start_date":"20240710","end_date":"20240710"}` |
| B / `slb_sec-600000-20240711` | slb_sec | `{"ts_code":"600000.SH","start_date":"20240711","end_date":"20240711"}` |
| B / `slb_sec_detail-000001-lower` | slb_sec_detail | `{"ts_code":"000001.SZ","start_date":"20240611","end_date":"20240611"}` |
| B / `slb_sec_detail-000001-lower-edge` | slb_sec_detail | `{"ts_code":"000001.SZ","start_date":"20240611","end_date":"20240620"}` |
| B / `slb_sec_detail-000001-20240710` | slb_sec_detail | `{"ts_code":"000001.SZ","start_date":"20240710","end_date":"20240710"}` |
| B / `slb_sec_detail-600000-lower` | slb_sec_detail | `{"ts_code":"600000.SH","start_date":"20240605","end_date":"20240605"}` |
| B / `slb_sec_detail-600000-lower-edge` | slb_sec_detail | `{"ts_code":"600000.SH","start_date":"20240605","end_date":"20240620"}` |
| B / `slb_sec_detail-600000-20240710` | slb_sec_detail | `{"ts_code":"600000.SH","start_date":"20240710","end_date":"20240710"}` |

- 33项来源绑定逐项精确匹配；6项新空及9项旧空/失败RANGE共15个负例全部拒绝。`/private/tmp/issue024-control/issue026-candidate-inputs.json`和`source-bindings.json`是可重建交付产物，不是新的验收索引或已执行TASK。预期同参数TASK为33次请求，SQL插入/更新和去重行数必须由新任务实测，不按SOURCE重叠行数相加推算。
- 三接口L=5000，参数形状分别DATES/STOCK/STOCK；原键分别`[trade_date,ob]`、`[trade_date,ts_code]`、`[trade_date,ts_code,tenor,fee_rate]`。融资不发明期限键；明细不能按股票/日期去重，必须保留同日不同期限和费率的原完整键，检查SQL原键数/归属/摘要及其他股票历史保留。
- 六个新空样本：A中slb_sec及slb_sec_detail两股票的`settlement`（0930～1001）共4个，B中slb_sec_detail两股票20240711共2个。空日只是该请求观察，不推导全部历史范围；候选开放时区分有效SOURCE引用与空对照，所有历史run/case保持。
- 融资与证券汇总20240711仍有数据，融资20240930仍有数据；这些数据和明细空日不能定义API停用或全历史截止。用户已批准方案A，T13/T14已限定修订为历史查询能力验收，继续保留“未知起止/保留承诺”；不靠继续换日期推导上游保证。
- 历史采用决定已明确；后续逐项候选v2、重建双包、准备固定TASK与独立schema，核对202/Location、完整批次树、SOURCE/insert/update、SQL和日志；生产源码变化后执行六门禁。原SINGLE任务不因本取证重复执行。

## ISSUE-025 条件输入（完整性未确认）

[三轮SOURCE](../../verification/ISSUE-018-T14-runs.md#issue-025-十一接口提取规则与来源)已取得135项/135请求，92非空与43空原样保存。以下87组唯一非空参数可精确重建来源绑定；它们尚不具备完整性合同，现有`selectTaskCases`逐一拒绝全部87项，不能当作已准入TASK。用户已于2026-09-14明确“可以接受不完整”，采用[方案 A](../proposals/ISSUE-025-extraction-contracts.md#决策记录)。87组现作为正式来源输入交付；本节保留“条件输入”标题供旧链接定位，条件变为RESPONSE_ONLY实现和候选包门禁，完整性保证仍未取得。ISSUE-026尚未执行。

A=`issue025-source-20260913T162759Z`。

B=`issue025-boundaries-20260913T163148Z`。

C=`issue025-express-20260913T163418Z`。

SOURCE caseId为runId加短横线及后缀；未来TASK caseId为`issue026-`加完整SOURCE caseId，mode=RANGE，start/end等于params起止。实际执行须另登记新runId/submissionId及新候选包和独立schema，evidenceRefs绑定`docs/verification/ISSUE-018-range-acceptance.json#<runId>/<caseId>`并追加届时已明确的规则依据。evidenceRefs还须包含ISSUE-025决策记录。当前索引UNKNOWN/v1不变；先贯通RESPONSE_ONLY独立规则、批次评估、持久化快照、能力/页面和证据消费者，再在候选包中逐项升v2；未来TASK/SQL字段只按真实结果填写。

| SOURCE run / case后缀 | API / dateAxis | 精确params |
| --- | --- | --- |
| A / `adj_factor-000001-whole` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20251229","end_date":"20260105"}` |
| A / `adj_factor-600000-whole` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20251229","end_date":"20260105"}` |
| A / `income-000001-whole` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240101","end_date":"20241231"}` |
| A / `income-600000-whole` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240101","end_date":"20241231"}` |
| A / `balancesheet-000001-whole` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240101","end_date":"20241231"}` |
| A / `balancesheet-600000-whole` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240101","end_date":"20241231"}` |
| A / `cashflow-000001-whole` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240101","end_date":"20241231"}` |
| A / `cashflow-600000-whole` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240101","end_date":"20241231"}` |
| A / `fina_audit-000001-whole` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240101","end_date":"20241231"}` |
| A / `fina_audit-600000-whole` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240101","end_date":"20241231"}` |
| A / `express-600000-whole` | express / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180101","end_date":"20180701"}` |
| A / `top10_holders-000001-whole` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170101","end_date":"20171231"}` |
| A / `top10_holders-600000-whole` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170101","end_date":"20171231"}` |
| A / `top10_floatholders-000001-whole` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170101","end_date":"20171231"}` |
| A / `top10_floatholders-600000-whole` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170101","end_date":"20171231"}` |
| A / `stk_managers-000001-whole` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20180101","end_date":"20190630"}` |
| A / `stk_managers-600000-whole` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180101","end_date":"20190630"}` |
| A / `suspend_d-000001-whole` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20000101","end_date":"20251231"}` |
| A / `suspend_d-600000-whole` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20000101","end_date":"20251231"}` |
| A / `repurchase-all-whole` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260801","end_date":"20260831"}` |
| A / `suspend_d-000029-whole` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200309","end_date":"20200313"}` |
| A / `suspend_d-600310-whole` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200309","end_date":"20200313"}` |
| B / `adj_factor-000001-lower` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20251229","end_date":"20251229"}` |
| B / `adj_factor-000001-upper` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20260105","end_date":"20260105"}` |
| B / `adj_factor-000001-after` | adj_factor / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20260106","end_date":"20260106"}` |
| B / `adj_factor-600000-lower` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20251229","end_date":"20251229"}` |
| B / `adj_factor-600000-upper` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20260105","end_date":"20260105"}` |
| B / `adj_factor-600000-after` | adj_factor / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20260106","end_date":"20260106"}` |
| B / `income-000001-bounded` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20241019"}` |
| B / `income-000001-lower` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20240315"}` |
| B / `income-000001-upper` | income / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241019","end_date":"20241019"}` |
| B / `income-600000-bounded` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20241031"}` |
| B / `income-600000-lower` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20240430"}` |
| B / `income-600000-upper` | income / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241031","end_date":"20241031"}` |
| B / `balancesheet-000001-bounded` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20241019"}` |
| B / `balancesheet-000001-lower` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20240315"}` |
| B / `balancesheet-000001-upper` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241019","end_date":"20241019"}` |
| B / `balancesheet-600000-bounded` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20241031"}` |
| B / `balancesheet-600000-lower` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20240430"}` |
| B / `balancesheet-600000-upper` | balancesheet / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241031","end_date":"20241031"}` |
| B / `cashflow-000001-bounded` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20241019"}` |
| B / `cashflow-000001-lower` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20240315"}` |
| B / `cashflow-000001-upper` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20241019","end_date":"20241019"}` |
| B / `cashflow-600000-bounded` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20241031"}` |
| B / `cashflow-600000-lower` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20240430"}` |
| B / `cashflow-600000-upper` | cashflow / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20241031","end_date":"20241031"}` |
| B / `fina_audit-000001-bounded` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20240315","end_date":"20240315"}` |
| B / `fina_audit-600000-bounded` | fina_audit / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20240430","end_date":"20240430"}` |
| B / `express-600000-bounded` | express / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180106","end_date":"20180106"}` |
| B / `top10_holders-000001-bounded` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170331","end_date":"20171231"}` |
| B / `top10_holders-000001-lower` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170331","end_date":"20170331"}` |
| B / `top10_holders-000001-upper` | top10_holders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20171231","end_date":"20171231"}` |
| B / `top10_holders-600000-bounded` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170331","end_date":"20171231"}` |
| B / `top10_holders-600000-lower` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170331","end_date":"20170331"}` |
| B / `top10_holders-600000-upper` | top10_holders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20171231","end_date":"20171231"}` |
| B / `top10_floatholders-000001-bounded` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170331","end_date":"20171231"}` |
| B / `top10_floatholders-000001-lower` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20170331","end_date":"20170331"}` |
| B / `top10_floatholders-000001-upper` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"000001.SZ","start_date":"20171231","end_date":"20171231"}` |
| B / `top10_floatholders-600000-bounded` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170331","end_date":"20171231"}` |
| B / `top10_floatholders-600000-lower` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20170331","end_date":"20170331"}` |
| B / `top10_floatholders-600000-upper` | top10_floatholders / REPORT_PERIOD | `{"ts_code":"600000.SH","start_date":"20171231","end_date":"20171231"}` |
| B / `stk_managers-000001-bounded` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20180816","end_date":"20190307"}` |
| B / `stk_managers-000001-lower` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20180816","end_date":"20180816"}` |
| B / `stk_managers-000001-upper` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20190307","end_date":"20190307"}` |
| B / `stk_managers-600000-bounded` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180428","end_date":"20190326"}` |
| B / `stk_managers-600000-lower` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20180428","end_date":"20180428"}` |
| B / `stk_managers-600000-upper` | stk_managers / ANNOUNCEMENT_DATE | `{"ts_code":"600000.SH","start_date":"20190326","end_date":"20190326"}` |
| B / `suspend_d-000001-bounded` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20000622","end_date":"20140716"}` |
| B / `suspend_d-000001-lower` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20000622","end_date":"20000622"}` |
| B / `suspend_d-000001-upper` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20140716","end_date":"20140716"}` |
| B / `suspend_d-600000-bounded` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20000508","end_date":"20160311"}` |
| B / `suspend_d-600000-lower` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20000508","end_date":"20000508"}` |
| B / `suspend_d-600000-upper` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20160311","end_date":"20160311"}` |
| B / `repurchase-all-lower` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260801","end_date":"20260801"}` |
| B / `repurchase-all-upper` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260831","end_date":"20260831"}` |
| B / `repurchase-all-before` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260731","end_date":"20260731"}` |
| B / `repurchase-all-after` | repurchase / ANNOUNCEMENT_DATE | `{"start_date":"20260901","end_date":"20260901"}` |
| B / `suspend_d-000029-lower` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200309","end_date":"20200309"}` |
| B / `suspend_d-000029-upper` | suspend_d / TRADE_DATE | `{"ts_code":"000029.SZ","start_date":"20200313","end_date":"20200313"}` |
| B / `suspend_d-600310-lower` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200309","end_date":"20200309"}` |
| B / `suspend_d-600310-upper` | suspend_d / TRADE_DATE | `{"ts_code":"600310.SH","start_date":"20200313","end_date":"20200313"}` |
| B / `suspend_d-000001-consecutive` | suspend_d / TRADE_DATE | `{"ts_code":"000001.SZ","start_date":"20100705","end_date":"20100709"}` |
| B / `suspend_d-600000-consecutive` | suspend_d / TRADE_DATE | `{"ts_code":"600000.SH","start_date":"20150608","end_date":"20150612"}` |
| C / `whole` | express / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20220113","end_date":"20220115"}` |
| C / `day` | express / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20220114","end_date":"20220114"}` |
| C / `lower-window` | express / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20220114","end_date":"20220115"}` |
| C / `upper-window` | express / ANNOUNCEMENT_DATE | `{"ts_code":"000001.SZ","start_date":"20220113","end_date":"20220114"}` |

- 私有`/private/tmp/issue025-control/conditional-inputs.json`和`source-bindings.json`仅是从唯一验收索引重建的条件清单，非新验收索引。87项结构/精确参数绑定通过、UNKNOWN准入87/87拒绝；43项新空不在候选清单。批准RESPONSE_ONLY后仍需保留这87项绑定和43项空历史；未来候选引用只选择适用的有效SOURCE，并保留空对照在runs，不能回填旧SOURCE或用决定绕过当前UNKNOWN门禁。
- 十项单股票、repurchase无股票；原业务键保持。suspend两基准股票均有S/R历史与连续五日，官网两额外股票只是补充，不替换基准。cashflow本轮ann/f_ann均相同，仍不得互换；top10_holders存在非季末报告日，不按季度或每期10条推导全集。
- SOURCE无TASK/SQL；未来按选定的真实产品承诺核对202/Location、所有批次、计数、原业务键/股票归属/摘要及第二股票写入后第一股票保留。规则/候选版本/双包/生产门禁及任务失败处理仍由后续专属设计落实。

## 设计与执行入口（2026-09-14）

[专属设计](../../task-designs/ISSUE-026-design.md)已完成并独立复审，记录NOT_STARTED → READY。消费以上268组既有来源；另按ISSUE-020已批准要求先取得两股票全年/六年宽窗四项真实二分SOURCE并执行四TASK，增加两项disclosure重复更新与四项已有接口回归，合计278计划TASK。十一RESPONSE_ONLY的独立语义、持久快照/HTTP/页面、schema2、按指定run/case绑定及失败规则均明确。实际执行结果尚未发生；该记录为拆分前的总设计入口，拆分后从子issue看板ISSUE-027开始，产品承诺和上述输入保持。
