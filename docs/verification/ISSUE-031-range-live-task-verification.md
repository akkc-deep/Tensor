# ISSUE-031：真实区间任务与SQL验收

## 2026-09-15后续最终验收

后续ISSUE-032六门禁及独立终审已完成，T14/T13、017/018与026按本次批准范围收尾，见[最终报告](ISSUE-032-range-final-closure.md)。以下“本次不开始032、不关闭母任务”等措辞保留为031交付时的阶段结论；251项真实验收、四接口27项排除和全部历史原件保持。

## 当前结果

COMPLETED（2026-09-15，按修订后的本次范围）。按[用户明确决定](../issues/proposals/ISSUE-026-range-scope.md)，当前251个纳入项均PASS；原27项按范围排除，保留4 FAILED/23 NOT_RUN。正式处置30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY，运行能力仍30 AVAILABLE/4 NEEDS_VERIFICATION/6 UNSUPPORTED；四接口RANGE继续拒绝、SINGLE保持。本次不开始032，不关闭母任务；下文旧阻塞和计数保留为历史。

通过数只计完整运行、SQL、清理及独立审查均合格的计划任务。income在两个失败轮中的PASS是同8个计划位置的历史观察，已由独立成功轮取代，不重复计数。最新总数与逐接口处置以唯一JSON索引和本文当前结果/最后运行记录为准；下文早期计数与构建结论均为当时快照。

## 输入与构建

- ISSUE-030冻结副本`/private/tmp/issue030-work-20260914T025010Z`：893文件snapshot `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`；422相关源码逐文件相同，两包、manifest/examples、272输入/绑定与正式索引哈希均核对通过。只有后续任务登记文档变化。完整核对`/private/tmp/issue031-control/preflight-identity.json`。
- 278项固定清单`/private/tmp/issue031-control/ten-round-plan.json`，SHA-256 `6e24193f2cc085711429ee39db54318034a088797550625672ddfc3f48978f3f`。严格validateEvidence/validateCasePlan/selectTaskCases通过；278不同TASK caseId、276完整SOURCE身份、278绑定，计划records查询556。272既有参数、ID和来源保持，新2披露重下紧随原event，新4回归按指定SOURCE复制。
- 独立准备审查确认272来源有效；mainbz旧annual实为单日端点，已按实际日期排到新annual/wide之后。trade_cal/margin另需按交易所只读SQL复核；此处记录运行前准备结论，实际结果见下文。

## 首次十轮登记（历史）

| 顺序 | 组名 | 计划TASK | SOURCE旧观察请求（仅预算参考） | 实际结果 |
| ---: | --- | ---: | ---: | --- |
| 1 | limits | 28 | 36 | 28/28 PASS；exit0 |
| 2 | mainbz | 16 | 54 | 16/16 PASS；exit0 |
| 3 | calendar | 38 | 62 | 38/38 PASS；exit0 |
| 4 | dates | 35 | 35 | 1 FAILED、34 NOT_RUN；exit1 |
| 5 | events | 37 | 129 | NOT_RUN |
| 6 | history | 33 | 33 | NOT_RUN |
| 7 | response-trade | 24 | 24 | NOT_RUN |
| 8 | response-announcement | 42 | 42 | NOT_RUN |
| 9 | response-holders-repurchase | 21 | 21 | NOT_RUN |
| 10 | regression | 4 | 4 | NOT_RUN |

各轮独立新空schema、5000请求/30分钟硬上限、间隔≥2000ms，fixture的2提交/3查询独立计数。运行前后实际身份、请求、批次/SQL和清理事实追加到[唯一登记](ISSUE-018-T14-runs.md)，原26轮826case928请求保留。

### limits 运行验收

`issue026-range-limits-20260914T174909Z`：28/28 PASS，实际36次来源/56次records，source 48、insert 18、update 30。exit0/cleanup PASS；完整逐项计数与SQL见[运行登记](ISSUE-018-T14-runs.md)和`/private/tmp/issue026-range-limits-20260914T174909Z/sql-supplement.json`。独立复核完成；daily、forecast、dividend的当前处置按唯一索引记录，未执行轮次不计通过。

### mainbz 运行验收

`issue026-range-mainbz-20260914T175905Z`：16/16 PASS，实际54次来源/32次records，source 1907、insert 1115、update 792。exit0/cleanup PASS；完整逐项计数与SQL见[运行登记](ISSUE-018-T14-runs.md)和`/private/tmp/issue026-range-mainbz-20260914T175905Z/sql-supplement.json`。独立复核完成；fina_mainbz的当前处置按唯一索引记录，未执行轮次不计通过。

主营业务新四TASK逐项绑定`issue026-mainbz-split-source-20260914T013736Z`同名SOURCE，旧12仍全部执行。真实新四结果如下；全部35个成功叶最大82行，低于100阈值，19个SPLIT父的source/insert/update均0，完整相邻闭区间无缺口/重叠。

| 新SOURCE/TASK后缀 | 请求 | SPLIT父 | 成功来源行 |
| --- | ---: | ---: | ---: |
| 000001-annual | 1 | 0 | 74 |
| 000001-wide | 15 | 7 | 458 |
| 600000-annual | 3 | 1 | 110 |
| 600000-wide | 23 | 11 | 657 |

SQL原键仍为`ts_code,end_date,bz_item,curr_type`。000001.SZ最终458键（P326/D84/I48），600000.SH最终657键（P443/D117/I97）；后股票写入时前股票摘要保持。两轮均核对每API内SQL前后链、插入增长、两次records与完整批次及持久v2规则；mainbz另直接只读核对登记schema和全部16项相同100/NATIVE_RANGE/可拆策略。单日满额失败约束沿用ISSUE-030已经通过的受控回归，未新增负例真实请求。

limits/mainbz两轮在SQL和独立审查通过后，已删除各自临时`client.cnf`/`environment.json`，自有JVM停止；成功schema和前序MySQL容器/数据全部保留，实际记录为各轮`secret-cleanup.json`。前两轮完成时新增开放daily/forecast/dividend/fina_mainbz，其余跨轮或未执行任务当时仍有缺口。

### calendar 运行验收

`issue026-range-calendar-20260914T180604Z`：38/38 PASS，实际62次来源/76次records，source 138、insert 103、update 35。exit0/cleanup PASS；完整逐项计数与SQL见[运行登记](ISSUE-018-T14-runs.md)和`/private/tmp/issue026-range-calendar-20260914T180604Z/sql-supplement.json`。独立复核完成；margin、top_list的当前处置按唯一索引记录，未执行轮次不计通过。

calendar独立补核：14项trade_cal最终80键（SSE/SZSE各40），9项margin最终18键（三exchange_id各6），所有固定窗口原键集合及交易所摘要吻合。15项top_list最终5键（000007.SZ=3、600318.SH=1、920008.BJ=1），每项真实持久单日叶与精确SOURCE的开市日序列一致，来源请求=1次日历+叶数，原股票/日期参数不变；三个closed任务各1次日历、零叶/零写。BJ任务保留920008.BJ，参照SSE由冻结映射和SOURCE身份共同核对。calendar轮清理临时凭据后保留成功数据库；trade_cal等待dates轮4项辅助任务。

### dates 运行验收

`issue026-range-dates-20260914T181447Z`：0/35 PASS，实际1次来源/2次records，source 0、insert 0、update 0。exit1/cleanup PASS；完整逐项计数与SQL见[运行登记](ISSUE-018-T14-runs.md)和`/private/tmp/issue026-range-dates-20260914T181447Z/sql-supplement.json`。独立复核完成；本轮接口的当前处置按唯一索引记录，未执行轮次不计通过。


### 失败保全与撤回

首个失败为`issue026-issue022-indicator-000001-whole`，参数`{"ts_code":"000001.SZ","start_date":"20250331","end_date":"20251231"}`，TASK `506ba45f-ed85-483e-91ac-a132e183a6a1`，batch `8f26fa6f-9bac-4c82-a5e0-8a220049ed3e`。真实任务及批次均FAILED / ADAPTER_TYPE_INVALID，attempt=1、request=1、SQL原键0→0、insert/update=0。sourceRows=0是成功写入计数，不能推断上游空响应。安全记录的空表ownershipSummary也不改变它的股票归属合同。

持久策略仍为失败时的v2 / REPORT_PERIOD / end_date / CONFIRMED_ROW_LIMIT100 / NATIVE_RANGE / splittable=true。原始输入、旧包与前后身份保持，exit1、cleanup PASS；只读SQL确认其他Tushare任务均未创建。本轮临时凭据已删除，失败schema和前三轮成功schema全部保留。没有自动retry/resume、重新SOURCE或换样本。

首次dates失败保全时，实现仅撤回fina_indicator准入：版本递增v3、sourceVerified=false、verificationEvidence=null；公开能力为NEEDS_VERIFICATION及UNKNOWN规则，保留原参数、内部100阈值、日期轴、字段/业务键和SINGLE。此变更不代表适配错误已修复。唯一索引保留失败v2的实际run，在接口当前版本记录v3及未解决项；当时正式10 AVAILABLE / 24 NEEDS_VERIFICATION / 6 SINGLE_ONLY，候选运行能力为33 AVAILABLE / 1 NEEDS_VERIFICATION / 6 UNSUPPORTED；这不是当前状态。

独立排查确认现有安全证据不足以定位根因：字段转换/精度/日期错误与同原业务键内容冲突均可能产生ADAPTER_TYPE_INVALID，持久统一消息已丢失异常分支；旧SOURCE仅日期/行数投影，模板与旧成功SINGLE为空。没有证据支持放宽字段、精度或业务键。恢复须先取得安全失败诊断或脱敏复现样本，区分VALUE_CONVERSION与CONFLICTING_KEY，定向修复并重新冻结；重试须明确新的run/case与原SOURCE映射、新空schema和预算，不能复用已登记ID。详见[阻塞交接](../task-handoffs/ISSUE-031-handoff.md)。


### 撤回后离线验证与新身份

定向三个Java类155/155 PASS（Policy121、Download30、Availability4），新准入拒绝用例先RED（预期NEEDS_VERIFICATION、实际AVAILABLE），再GREEN；编译与沙箱Mockito初始化失败排除后才观察到该有效RED。Node证据104/104 PASS，负例保留SOURCE-only不能开放与已使用TASK ID不能再选。正式前26轮与运行前baseline逐对象相同，`JSON.stringify(runs)`（保持键顺序）SHA `95b2e7e0a4743f982d1818540ea8075f694050b9e0ac51a13617f63529381313`。

新隔离副本`/private/tmp/issue031-withdrawal-20260914T182807Z`由当前897个受Git跟踪文件的工作字节构建，包含前序未提交成果；snapshot `632a8308f20b33908542f2cb65e821a0a3037a3abba78194ae95dbda54e064cf`。运行`mvn -o -f data-plane/pom.xml -Pacceptance verify`退出0：后端/两包1134项、前端524项全部通过，失败/错误/跳过0。日志`/private/tmp/issue031-control/withdrawal/build.log`，汇总`acceptance-summary.json`；422个相关源码/合同文件与当前工作树逐文件一致，后续只更新交接/验收说明。

- sourceDiff SHA：`bb737f6f9cbc549d45b3d78797f30b24fdc10bd6d1537bb3b9ca7b14eef54b2f`。
- 新生产JAR SHA：`ff30109702e240d34782618f62ab9efed4bdd977e8a691f322d05f6806838bb5`。
- 新验收JAR SHA：`6522cbbd6d02decf91136bc47766de59ec55e369b84fd3e9d00390bd47e5d7f1`。
- manifest SHA：`386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`；request examples SHA：`6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。

该次离线交付时，以上v3构建尚未执行真实来源或TASK；当时四轮实测属于前文保存的旧v2包。其后使用该构建完成dates29/events37/history33共99项，见后文续办记录。没有运行ISSUE-032最终六门禁或发布脚本。

新包普通浏览器命令`npm --prefix control-plane run test:e2e -- e2e/tushare-metadata.spec.js`：40/40 PASS（3.0分钟），exit0。实际40个能力响应逐项匹配独立预期，fina_indicator日期区间禁用、SINGLE可用。安全结果taskPosts/synchronousDownloadPosts/recordsGets/upstreamCalls均0，jvm/sentinel/privateLogScanned全部true，验收包前后SHA一致；外层自有临时MySQL容器和全部凭据文件已删除，旧四个实测schema保留。原件`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-5KbmQO/metadata-evidence.json`，SHA `5ad837a993cb03bb18f7900cf4554e002e04257971c0655dca22600be4feb0a0`，汇总`/private/tmp/issue031-control/withdrawal/metadata-summary.json`。

最终保全复核`/private/tmp/issue031-control/final-preservation-audit.json`确认旧893 snapshot/两包、原26轮完整对象、四轮临时秘密清理；未执行任何额外来源诊断。独立代码/证据复核确认v3撤回、422相关文件与两包身份、1134后端及524前端结果，支持BLOCKED而非COMPLETED。最后限定复核进一步确认metadata安全原件40项全通过、零TASK/零上游、清理与包身份一致；下述未执行诊断提案的单请求和安全输出边界无发现。

### 历史财务指标诊断提案（已递延至ISSUE-033，不执行）

恢复前拟额外执行1次SOURCE-only诊断：固定`fina_indicator`及原失败params（000001.SZ，20250331～20251231），新独立diagnostic runId，最多1个来源请求，无重试、分页或换样本；不给数据库配置，不创建TASK，不写业务表。响应只在内存中按冻结原字段和原适配器验证，输出仅白名单元数据：请求计数、适配成功/失败、VALUE_CONVERSION或CONFLICTING_KEY分支、rowIndex、field/逻辑类型、数值类型及精度/小数位分类；不输出或保存Token、原始响应、财务值、业务键内容或原始异常消息。结果单独归档，不覆盖失败v2 run，不计为TASK通过，也不重新开放v3。诊断完成或失败立即停止；若未复现，仅记录本次观察，不宣告历史失败根因已解决。

该额外来源请求超出本设计“不重新调查或重跑已合格SOURCE”及失败停止边界，须用户明确同意后才执行。即使得到根因，后续TASK补验仍须明确新的版本/冻结身份、run/case与原SOURCE映射、新空schema和预算。


### 2026-09-15跳过财务指标后的续办准备

用户明确“财务指标先跳过，记录一个issue，再处理其他数据源”。建立ISSUE-033后，范围改为当前272项+递延6项；权威看板BLOCKED → READY → IN_PROGRESS。新私有清单`/private/tmp/issue031-resume-control/ten-round-plan.json` SHA `0d006914759f96164cccdd6cdbeff838df9a820b1f34ff369c83d6b6ca85ebb8`，29条ID映射`replacement-mapping.json` SHA `48ea97763c833f0413650f355f5fc3294cdd47b4721a8641c42ad96c06518a24`；全部190项通过validateEvidence/validateCasePlan/selectTaskCases与完整sourceBindings机械核对，API/params/dateAxis及原顺序保持。

dates29/events37/history33/response-trade24/response-announcement42/response-holders-repurchase21/regression4待执行；旧dates35条原记录不变，财务指标不进入本次清单。续办使用前述897文件撤回构建与验收包6522cbbd…d7f1，422相关文件与当前HEAD f563bd9的工作字节一致；原成功82项仍归原v2包，不重跑、不追认新包结果。本次未授权/未执行额外财务指标SOURCE-only诊断；前述提案仅保留历史。

续办准备独立复核通过：只递延6项；dates29映射/SOURCE/顺序、后六轮161原对象、190纯选择、897 snapshot/422相关文件/两包均核对一致。新归档脚本明确区分旧82与新包，只有替代项真实通过后才移除旧NOT_RUN当前引用，旧run不改；trade_cal必须覆盖旧14+新4，weekly/monthly必须先完整日历求最后开市日。该审查仅证明准备，不替代后续实际SQL/清理/运行审查。

### dates 续办运行验收

issue031-range-dates-20260914T185851Z：29/29 PASS，0 FAILED/0 NOT_RUN；来源29请求、records 58次，source 205、insert 186、update 19。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-range-dates-20260914T185851Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：stk_holdernumber、trade_cal、weekly、monthly、new_share。当前111/272项PASS，财务指标6项仍递延。

### events 续办运行验收

issue031-range-events-20260914T191040Z：37/37 PASS，0 FAILED/0 NOT_RUN；来源129请求、records 74次，source 98、insert 40、update 58。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-range-events-20260914T191040Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：block_trade、disclosure_date、stk_holdertrade、pledge_detail。当前148/272项PASS，财务指标6项仍递延。

### history 续办运行验收

issue031-range-history-20260914T192313Z：33/33 PASS，0 FAILED/0 NOT_RUN；来源33请求、records 66次，source 563、insert 511、update 52。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-range-history-20260914T192313Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：slb_len、slb_sec、slb_sec_detail。当前181/272项PASS，财务指标6项仍递延。

历史窗口专项只读SQL补充：`/private/tmp/issue031-range-history-20260914T192313Z/history-sql.json` SHA `e08afd89421634726547a96e07ef32deda0f355e14ba39002d9b5863306b5a44`。33项最终窗口日期集合/原键基数/同日多键组均匹配准确SOURCE；len23、sec44（两股各22）、detail444（249/195）最终键保留。明细两长窗242/190键、196/150日期、41/33同日多键组、9/7期限、54/39费率；早期窗口及另一股票持续保留。独立审查PASS；这些观察不构成上游历史起止或持续保留保证。

### response-trade 续办运行验收

issue031-range-response-trade-20260914T193118Z：0/24 PASS，0 FAILED/23 NOT_RUN；来源1请求、records 1次，source 0、insert 0、update 0。exit1/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-range-response-trade-20260914T193118Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：无。当前181/272项PASS，财务指标6项仍递延。

本轮中断说明：上表null字段汇总为0仅表示未完成验收计数，不能解释为零来源/零写入。实际adj_factor TASK `74417c73-0f69-4bb7-82f0-10c8238881e3` 与批次SUCCEEDED，1请求/1尝试/source4/insert4/update0；只读SQL原键0→4，日期20251229、20251230、20251231、20260105，suspend_d仍0记录。harness在成功状态文案处使用过期“已成功”断言，早于RESPONSE_ONLY检查及AFTER records/SQL；保留1 EVIDENCE_MISSING/23 NOT_RUN，exit1，不计清洁合格TASK。独立审查确认工具缺陷、原33轮/策略与清理保持；没有数据源失败，不撤回候选或递增策略。本轮sql-supplement.json为中断后只读观察，不能补造缺少的浏览器闭环。修复/重新冻结和明确24项新ID补验见ISSUE-031设计修订。

## ISSUE-031验收脚本文案修复构建与续验身份

最小harness修复已独立审查：通用SUCCEEDED状态按RESPONSE_ONLY非空/空使用既有正确文案，严格RANGE/SINGLE仍为“已成功”；保留提示/计数/SQL。实际submitDownload路径新回归先出现“返回记录已采集 !== 已成功”RED，再四场景GREEN。阶段证据同步后Node106/106通过（0失败/跳过）；日志`/private/tmp/issue031-resume-control/response-fix-full-green.log`。

隔离副本 `/private/tmp/issue031-harnessfix-20260914T194130Z`，898文件snapshot `c67cff6a43c75b35344ab78806e67bae0dfcd4d0fefb3a97f26af044f643ee21`，422相关源码与当前工作字节一致。完整离线 `mvn -o -f data-plane/pom.xml -Pacceptance verify` exit0，后端/两包1134、前端524通过，0失败/错误/跳过；受控download-tasks浏览器15/15 PASS、exit0，自有Vite已停止，未给测试提供真实上游令牌。日志/汇总位于 `/private/tmp/issue031-harnessfix-control/build/`。

- sourceDiffSha256：`9e1f478b96a08497338274972d77b5f73d89d4670e9a92066ddefa6e4fb96cc4`。
- productionJarSha256：`c3369f07a44a3da401984f6ce6206715c2616216b0e44fe552ae6d1ca9f5e442`。
- acceptanceJarSha256：`99ae75e9370a6b4fdf45391d2092524c382461106b9893622b65260eb6c1dda5`。
- manifestSha256：`386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256：`6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。

剩余91项按trade24/announcement42/holders21/regression4串行；24项明确新ID，原33项历史轮与其他旧记录不变。53条总替代映射（dates29+trade24）含原状态/任务ID约束，逐case构建表分别绑定旧82、续办99、本次91，禁止把旧TASK算成新包结果。纯选择91绑定通过；实际任务仍待执行。
- `/private/tmp/issue031-harnessfix-control/ten-round-plan.json` SHA `e319702b4cdc9d890221db48def5efc252be536e6c1810465b8613dc9c213dbe`。
- `/private/tmp/issue031-harnessfix-control/replacement-mapping.json` SHA `e1aea36a98761efd30c81946e240482186441c936b21d1abd6bf263b068ae1e9`。
- `/private/tmp/issue031-harnessfix-control/replacement-statuses.json` SHA `05b14537582f71f196d547bedf7344f613194b6c6fe54e95f1a3b1f0b1517833`。
- `/private/tmp/issue031-harnessfix-control/case-build-bindings.json` SHA `6f58ece6668ecf97d30f212681410228d7a6dfe86dc01ce1487a28f08f51abe4`。

### response-trade 续办运行验收

issue031-statusfix-range-response-trade-20260914T194412Z：24/24 PASS，0 FAILED/0 NOT_RUN；来源24请求、records 48次，source 776、insert 387、update 389。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-statusfix-range-response-trade-20260914T194412Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：adj_factor、suspend_d。当前205/272项PASS，财务指标6项仍递延。

交易日期窗口专项SQL：`/private/tmp/issue031-statusfix-range-response-trade-20260914T194412Z/trade-window-sql.json` SHA `ea67c3c2641082cc67b4b9bbcf9fbd8a65c952cecc170abab4b89031dce62b09`。24项各自日期集合、逐股票原键数/摘要和最终窗口并集一致；adj_factor10键（两股各5），suspend_d377键（000001=220、600000=147、000029=5、600310=5），较早窗口保留。独立审查PASS。新24项完整通过后按显式映射移除中断轮的1 EVIDENCE_MISSING/23 NOT_RUN当前引用；原24个run/case对象完整保留，未将后端成功追认为旧轮验收PASS。RESPONSE_ONLY仍不保证上游完整性。

### response-announcement 续办运行验收

issue031-statusfix-range-response-announcement-20260914T195234Z：8/42 PASS，1 FAILED/33 NOT_RUN；来源9请求、records 17次，source 21、insert 8、update 13。exit1/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-statusfix-range-response-announcement-20260914T195234Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：无。当前205/272项PASS，财务指标6项仍递延。

失败case为`issue026-issue025-source-20260913T162759Z-balancesheet-000001-whole`，原参数`000001.SZ/20240101/20241231`；TASK `67a9a231-1849-41c1-880a-ef8519da99d8`，batch `43753528-195c-466c-accf-738e6f457beb`。任务及批次真实FAILED/ADAPTER_TYPE_INVALID，1请求/1尝试，failedLeaf1/successLeaf0/emptyLeaf0，持久sourceRows/insert/update均0。只读SQL确认0→0；持久来源计数不代表上游空响应。8项income的21来源行/8插入/13更新及最终两股各4原键得到SQL核对，但失败轮不构成清洁通过，不开放income。

原始`artifacts/run/safe-results.json` SHA `10dde355950e4476b812fc67b81967a2a41ba4e15a0d6432ad91b4c47ec80667`保持，因harness先执行成功专用RESPONSE_ONLY校验而将该case留为EVIDENCE_MISSING。仅规范candidate索引依据原始taskObservations及补充SQL投影为真实FAILED；没有补造缺少的AFTER records/连续SQL步骤。`failure-enrichment.json`说明映射，规范`candidate-index.json` SHA `352ca7b2759431d64aa070709b621176bd03710271c9a22fbc8c84f69e79218e`，`sql-supplement.json` SHA `3ac782b1589ef2073fc7b9bdc164061abf9386b8b2ec3eb3250774513a344e6c`。独立审查通过后追加；旧35个run对象保持。

balancesheet已撤回v3/NEEDS_VERIFICATION，原字段、精度、业务键`ts_code,end_date,report_type,ann_date`和SINGLE未改。定向Java三个类通过，日志`/private/tmp/issue031-control/balancesheet-withdrawal-green.log`；针对原参数的准入用例先RED再GREEN。验收工具现在先保存真实FAILED及批次，再执行原严格运行规则；回归先观察原错误（未记录download），修后通过且SQL-after保持null。当前全部Node证据测试107/107 PASS，`/private/tmp/issue031-control/evidence-current36.log`，包含旧26轮哈希、两次中断、显式替代映射、SOURCE-only及重复TASK拒绝。新工作树尚未完整构建或执行真实任务。

已按[有界诊断计划](../task-designs/ISSUE-031-design.md#2026-09-15资产负债表失败保全与有界定位)完成1次只读适配诊断：原冻结包/原参数返回6行，adapter复现KEY_CONFLICT；逐字段转换失败0，两对零基行2/3、4/5的转换后差异仅total_share/update_flag。safe-result SHA `473cc864541a24ecdcb53fc703fc28516995b54a026b34218bab3655e2d197bf`，execution-start SHA `2e0aa4672fc8772b4db05500a66c39705c5dd7072eead3cf0fd9121bf06b65c7`，位于`/private/tmp/issue031-balancesheet-diagnostic/`。独立复核原JAR及54依赖、启动时4项代码/类哈希、1请求、输出白名单与私有权限均PASS。无DB/TASK或原始响应持久化；此请求单独登记，不追加TASK或覆盖原SOURCE，正式索引仍1306请求，另诊断1次。

该复现可排除本次响应的字段转换错误，但原失败响应没有保存，不能宣称两次逐值相同。官方doc36仅称update_flag为“更新标识”，没有保留优先级；不推断标记值、哪行最新、按返回顺序覆盖或扩展键即可解决。版本保留规则尚未决定，balancesheet继续撤回，8项仍在272目标中待处理。财务指标始终未调用。其余59项依据独立恢复计划准备新包/新ID与原SOURCE映射，尚未执行。

## ISSUE-031 冲突保全后构建与独立续验准备（2026-09-15）

资产负债表仍撤回且保留8项未决；其余59项按更新设计独立恢复，财务指标6项仍递延033。公告日期34项新ID/完整SOURCE绑定，holders21与regression4原ID未使用。计划仍272项，205旧接受项按原包归属，held8仍保留原失败轮包；替代映射87条（旧53加公告34），income的原8观察PASS只在指定exit1轮中按显式映射替代，原FAILED不适用。纯validateEvidence/validateCasePlan/selectTaskCases共59项通过，未发出任务请求。

新冻结副本`/private/tmp/issue031-keyconflict-20260914T201740Z`，898文件snapshot `c0d0c13144763d9866281566b362aa4f393df554f797140a2e1ffb619aa7d28f`。完整acceptance构建exit0：后端/两包1135、前端524项通过；Node107、受控任务页面15与新包metadata40项通过，失败/错误/跳过0。metadata零TASK/同步下载/records/上游，JVM/sentinel/日志检查true，外层自有容器与临时凭据已清理。安全原件`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-bcWZro/metadata-evidence.json` SHA `ad7742c1ed0a089f147877217891c16d33927fc215db9276126e5076ec999bff`；各日志与摘要位于`/private/tmp/issue031-keyconflict-control/build/`。

- sourceDiffSha256: `88e5fa50483860f7b20168fee0beef11c1b3aff920e24de7cc6ba03441852c27`。
- productionJarSha256: `b975e8a68810fb792b7e02fb2ac7dfc6bdd74583adf0e47ccb4e1757083fc4f5`。
- acceptanceJarSha256: `65a809227e4c3d485799af51a9826d37943ecc623c9deaf6aed3167aed19654e`。
- manifestSha256: `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256: `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。
- `/private/tmp/issue031-keyconflict-control/ten-round-plan.json` SHA `c58ec57b6395b744a071ea594f948b0a20b36e09875edbf8672af2063c5617ef`。
- `/private/tmp/issue031-keyconflict-control/replacement-mapping.json` SHA `5189b9fa888918bbb5d448ce60810fcaaecdb6b1dbfd196dbd71bdc06b26db94`。
- `/private/tmp/issue031-keyconflict-control/replacement-statuses.json` SHA `34e87bafbd4003ffb6728efb86957efd7d88678cc01f0c32cf798f2fe3788e99`。
- `/private/tmp/issue031-keyconflict-control/case-build-bindings.json` SHA `faf1727914e00c140b79db4e253b2c16ec5ab426eee5146a74ed04a6e118dcee`。

运行包装器从新config读取snapshot；运行前与registration、运行后collector均交叉核对同一snapshot和五项构建身份。旧包、旧run及原参数/日期轴/业务键保持；本准备不表示59项真实验收已经通过。

### response-announcement-rest 续办运行验收

issue031-keyconflict-range-response-announcement-rest-20260914T202312Z：8/34 PASS，1 FAILED/25 NOT_RUN；来源9请求、records 17次，source 21、insert 8、update 13。exit1/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-keyconflict-range-response-announcement-rest-20260914T202312Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：无。当前205/272项PASS，财务指标6项仍递延。

### cashflow失败保全与逐接口续验准备

`issue031-keyconflict-range-response-announcement-rest-20260914T202312Z`已按失败归档，未开放接口。cashflow TASK `4675518e-eba7-4d86-bc62-e6d613f54843` / batch `4398c8d3-1d5d-4444-9395-21c515c82acf`，原参数000001.SZ/20240101/20241231，FAILED/ADAPTER_TYPE_INVALID、1请求/1尝试，失败叶1/成功叶0/空叶0。新harness原始case直接保留FAILED，SQL-before/after字段均null；safe.sql保留BEFORE0、AFTER缺失，只有BEFORE records。后续只读SQL确认表0，单列不补造AFTER验收。实际上游行数未知，也不能借balancesheet诊断判定本次根因。

安全输出SHA `92271d5a02e8c4209e3f6ff94787cc32746cc1e8f20375a455ac943f2f4372e2`，SQL SHA `aa357d54b20b58f827cf9e9af70bb354b7e4c75038f75dc4d0c002073693e509`，income SQL SHA `3844ce14d76d2260ed0ec5c9e707d2bc7f5550d4e39d12fc4043e6f763b71a19`，均位于本轮同名私有目录；后者确认8个原键、两股历史保留及21source/8insert/13update。独立审查、进程/秘密清理通过，37个run共1166case/1315请求，另balancesheet诊断1次单列。income同8个计划位置在两个exit1轮各观察PASS，尚不计清洁接受。

cashflow撤回v3/NEEDS_VERIFICATION，SINGLE保持；现候选31 AVAILABLE/3 NEEDS_VERIFICATION/6 UNSUPPORTED，正式仍24/10/6。原参数拒绝测试先RED（实际AVAILABLE），随后157个定向Java回归通过；现金流量表f_ann_date日期轴的原规则改用已有受控策略验证，生产撤回另验，规则未放宽。Node108项通过，所有旧run和SQL缺失事实保持。日志`/private/tmp/issue031-control/cashflow-withdrawal-red.log`、`cashflow-withdrawal-green2.log`、`evidence-current37.log`。

剩余51项按[逐接口续验计划](../task-designs/ISSUE-031-design.md#现金流量表失败后的逐接口续验)独立运行，balancesheet8/cashflow8暂挂且仍在272目标内，财务指标033仍未调用。新副本`/private/tmp/issue031-perapi-20260914T203858Z`，898文件snapshot `49f9c70bc960af962fbf0d2504c678c06a0e98a76eaec275eb45eb9c18537723`；完整构建exit0，后端/两包1136、前端524、Node108通过。新包定向metadata仅3项（两旧撤回加cashflow），3/3通过、零上游/任务/records、JVM/sentinel/日志安全和自有容器/秘密清理通过；原件 `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-6GXj4G/metadata-evidence.json` SHA `a4d97938cf973be3650b8e5ee1a2904f039df8a488ddd37c0e2de17a875b3d5c`。上一包40metadata/15受控页面属于历史适用检查，未冒称新包跑了40项。

- sourceDiffSha256: `962c28560ae2bc6811c0fbc936a33d35930bcd9c567a3730f15560da47d5f87a`。
- productionJarSha256: `586020fa2d294b7a9d7d46d22199239fd0fbb646b26a54b2554bdd4615ced533`。
- acceptanceJarSha256: `a77d99aca450fe0345869a89392a9889480753f23c41c814bf81ef6f70c732d3`。
- manifestSha256: `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`。
- requestExamplesSha256: `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。
- `/private/tmp/issue031-perapi-control/ten-round-plan.json` SHA `b6c0e943f191d8ed272f68cd38a519013a117342ebcc3395b0b278e8b40aaaf9`。
- `/private/tmp/issue031-perapi-control/replacement-mapping.json` SHA `338b0c5fe86fa0f6fccb66d56cad5c7e387373df7264075e931d98907f804aac`。
- `/private/tmp/issue031-perapi-control/replacement-statuses.json` SHA `ef132fa8670141fbe4dc49647bafb2f1b0bcf19ad00cdb83972305d6651ff3ce`。
- `/private/tmp/issue031-perapi-control/case-build-bindings.json` SHA `e351fe41832a36893e5873dff283172a73c4f8dbaabf71359b20299f23229a06`。

纯选择51绑定通过，26个公告新ID、25个未用旧ID、held16及旧205身份分别核对；113条替代映射直接指向最终case，旧PASS例外只限两个固定失败run的income观察，原FAILED不适用。此处仅准备事实，尚未执行逐接口真实任务。

### income 续办运行验收

issue031-perapi-range-income-20260914T204158Z：8/8 PASS，0 FAILED/0 NOT_RUN；来源8请求、records 16次，source 21、insert 8、update 13。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-perapi-range-income-20260914T204158Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：income。当前213/272项PASS，财务指标6项仍递延。

### fina_audit 续办运行验收

issue031-perapi-range-fina_audit-20260914T205049Z：4/4 PASS，0 FAILED/0 NOT_RUN；来源4请求、records 8次，source 4、insert 2、update 2。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-perapi-range-fina_audit-20260914T205049Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：fina_audit。当前217/272项PASS，财务指标6项仍递延。

### express 续办运行验收

issue031-perapi-range-express-20260914T205520Z：6/6 PASS，0 FAILED/0 NOT_RUN；来源6请求、records 12次，source 6、insert 2、update 4。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-perapi-range-express-20260914T205520Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：express。当前223/272项PASS，财务指标6项仍递延。

### stk_managers 续办运行验收

issue031-perapi-range-stk_managers-20260914T210252Z：8/8 PASS，0 FAILED/0 NOT_RUN；来源8请求、records 16次，source 37、insert 13、update 24。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-perapi-range-stk_managers-20260914T210252Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：stk_managers。当前231/272项PASS，财务指标6项仍递延。

### repurchase 续办运行验收

issue031-perapi-range-repurchase-20260914T211042Z：0/5 PASS，1 FAILED/4 NOT_RUN；来源1请求、records 1次，source 0、insert 0、update 0。exit1/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-perapi-range-repurchase-20260914T211042Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：无。当前231/272项PASS，财务指标6项仍递延。

### 回购失败保全与续验范围

`issue031-perapi-range-repurchase-20260914T211042Z`：原DATES参数20260801～20260831、无ts_code。task `938ab8cc-0b39-476d-bb77-abbfd683285e` / batch `31115b76-6521-4185-8f16-264121f4f557` 实际FAILED / ADAPTER_TYPE_INVALID，1请求/1尝试；1次BEFORE records，AFTER缺失，规范case的SQL前后均null。上文source/insert/update0仅为成功计数，不证明来源为空。补充只读SQL确认回购表0，未冒充完整TASK闭环。其余4项NOT_RUN，exit1/cleanup PASS；独立审查通过，失败schema保留、临时凭据已清理，旧41轮和其他接口不变。

仅repurchase当前准入撤回v3，SINGLE保持；根因尚未确定，无诊断或自动重试。当前231/272清洁接受，3 FAILED/38 NOT_RUN；其中balancesheet8、cashflow8、repurchase5暂挂，剩余holders16和regression4按新冻结包继续，21项暂挂没有另行递延。财务指标另6项仍归ISSUE-033。当前正式28 AVAILABLE/6 NEEDS_VERIFICATION/6 SINGLE_ONLY；候选30 AVAILABLE/4 NEEDS_VERIFICATION/6 UNSUPPORTED。

### 回购撤回后的新冻结构建

隔离副本`/private/tmp/issue031-repurchase-20260914T211953Z`，898文件snapshot `258b7df2a635a9cd3f53f7aaf6abb9257a55ab2551549269551bf09602da3b9b`；sourceDiff `453a552545163b0b18ccfeab043241ab86bb3bee6f4f8f651ddfcb6e07f1cc7c`，生产JAR `4d4f1e3985c10aca803a0ca4d8d1a535e2637909b0f68192c977d193c40735be`，验收JAR `5ecb993e13d6c1340013eb48ca87ea4a91782906dd06bd51f2108b7b51c1bfd5`。完整离线acceptance构建1137后端/打包、524前端，失败/错误/跳过0；定向158和当前阶段Node110均PASS。旧包和旧结果保持。

新包metadata核对4个撤回接口4/4 PASS，TASK提交/同步下载/records/上游调用均0，内部JVM/sentinel/日志检查及外层临时容器/凭据清理完成。原件`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-wI3ns8/metadata-evidence.json` SHA `afd24914aa306d58e3bbc633d73c96188042b6eefb4968966d3491139b7df8c8`；这是本包4接口检查，不将旧metadata40/受控页面15冒充新包结果。构建报告与准确身份见`/private/tmp/issue031-repurchase-control/build/`。

272逐case构建绑定中只更新未用的holders8+8/regression4共20项为新包，252个既有绑定保持各自历史包；20项纯选择与完整SOURCE绑定通过、尚未新增真实请求。两个运行包装器已指新副本，冻结前后源码/包/输入门禁保持。实际运行须逐轮预登记、列表/新空schema检查和独立复核后再执行。

### top10_holders 续办运行验收

issue031-repurchase-range-top10_holders-20260914T212506Z：8/8 PASS，0 FAILED/0 NOT_RUN；来源8请求、records 16次，source 240、insert 100、update 140。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-repurchase-range-top10_holders-20260914T212506Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：top10_holders。当前239/272项PASS，财务指标6项仍递延。

### top10_floatholders 续办运行验收

issue031-repurchase-range-top10_floatholders-20260914T213321Z：8/8 PASS，0 FAILED/0 NOT_RUN；来源8请求、records 16次，source 247、insert 100、update 147。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-repurchase-range-top10_floatholders-20260914T213321Z/sql-supplement.json。临时凭据已清理，schema保留。经证据核对开放：top10_floatholders。当前247/272项PASS，财务指标6项仍递延。

### regression 续办运行验收

issue031-repurchase-range-regression-20260914T213809Z：4/4 PASS，0 FAILED/0 NOT_RUN；来源4请求、records 8次，source 22、insert 22、update 0。exit0/cleanup PASS；SQL及独立审查完成，完整记录见[运行登记](ISSUE-018-T14-runs.md)和私有 /private/tmp/issue031-repurchase-range-regression-20260914T213809Z/sql-supplement.json。临时凭据已清理，schema保留。daily_basic、moneyflow、stk_limit、margin_detail回归通过，维持原AVAILABLE。当前251/272项PASS，财务指标6项仍递延。

## 三接口冲突诊断与剩余阻塞

2026-09-15，其他可独立验收的任务已结束；当前目标272 = 251清洁PASS + 3 FAILED + 18 NOT_RUN。唯一索引45轮/1217case/1362来源请求，另三个有界诊断各1请求单独登记，不加入TASK/SOURCE验收。正式30 AVAILABLE/4 NEEDS_VERIFICATION/6 SINGLE_ONLY；构建能力30 AVAILABLE/4 NEEDS_VERIFICATION/6 UNSUPPORTED。fina_indicator另6项递延ISSUE-033，本次未调用。

| 当前受阻接口 | FAILED / NOT_RUN | 已观察的原因 | 尚缺的规则 |
| --- | ---: | --- | --- |
| balancesheet | 1 / 7 | 原键下total_share/update_flag不同，转换失败0 | doc36只称更新标识，版本保留优先级未定 |
| cashflow | 1 / 7 | 原键下两组内容冲突，转换失败0 | 是否采用唯一最新版本；无最新/多最新如何处理 |
| repurchase | 1 / 4 | 原键下15组内容冲突，转换失败0 | 如何识别不同回购记录或版本并保留数据 |

### 现金流量表诊断实际结果

`issue031-cashflow-diagnostic-20260914T210414Z`已按修订2登记执行并独立审查PASS。私有目录`/private/tmp/issue031-cashflow-diagnostic`，登记SHA `8af2822b2b0b4971a4367be71e7df03f32364cc34e183a4c2b912d4c8f772b49`；原失败验收JAR `65a809227e4c3d485799af51a9826d37943ecc623c9deaf6aed3167aed19654e`，原000001.SZ/20240101～20241231参数，1请求返回6行。原adapter结果ADAPTER_FAILED/ADAPTER_TYPE_INVALID/KEY_CONFLICT，字段转换失败0。6行完整转换为4个原键组，其中2个冲突组；每组恰有1个update_flag字符串为1的不同完整行，无最新组0、多最新组0。零基行1/2差异为end_type/prov_depr_assets/oth_loss_asset/update_flag，4/5为prov_depr_assets/oth_loss_asset/update_flag。原键保持ts_code,end_date,report_type,ann_date。

官方[doc44](https://tushare.pro/document/2?doc_id=44)写明update_flag为“更新标志(1最新）”；保存的official-doc44.html SHA `c72936927f2038160b357766e350aba40241404f02aebe6e12edac9283e17b9c`。这支持提出cashflow专属方案：冲突组只有一个不同的最新完整行时采用该版本，无最新或多个最新仍失败。该方案尚未决定或实现，不能推广到资产负债表和回购。safe-result.json SHA `6867e1802f09d1ddeb3ce64a482e0c19f7b3253deb8ee49bfcd27b7171e6e491`；execution-start.json SHA `9888e2fb812f2ae77e664d0b5e407253822c56593778913d0a9550bc00513d62`。修订1留在revision1-prepared-not-run/，从未执行。

### 回购诊断实际结果

`issue031-repurchase-diagnostic-20260914T212829Z`已执行并独立审查PASS。私有目录`/private/tmp/issue031-repurchase-diagnostic`，登记SHA `57b870e3af74e97a5928a349c59b91d18da7341d03642c291f23f8ffe662e463`；原失败验收JAR `a77d99aca450fe0345869a89392a9889480753f23c41c814bf81ef6f70c732d3`，原DATES参数20260801～20260831、无ts_code，1请求返回852行。原adapter结果ADAPTER_FAILED/ADAPTER_TYPE_INVALID/KEY_CONFLICT，转换失败0；按不同firstRowIndex得到15个冲突组，相对各组首行共有17次不同内容比较。差异字段仅end_date/vol/amount/high_limit/low_limit，原键保持ts_code,ann_date,proc。没有最新版本统计，也未单独计数完全重复行，不能用852减17推算唯一键数。

safe-result.json SHA `e4b9c1395f037ec258411b173a0ee8e67a53405998a7363c63a020cb058d73e8`；execution-start.json SHA `9e8fd714f432e4d3ad2528002fe4abff40dd352911d98362f1be5b107233b049`。

两次新诊断均在最后regression真实轮结束后串行执行。独立复核原包/Java/源码/类/包装器/54依赖及白名单输入身份通过；无DB/TASK、无原响应或字段值持久化、无重试。原失败响应未保留，结论仅描述原包/原参数下此次诊断观察，不声称重现了完全相同的原始payload。cashflow和repurchase原FAILED及规范SQL前后null保持，补充表0没有补成AFTER验收。

此前balancesheet诊断`issue031-balancesheet-diagnostic-20260914T200411Z`保持唯一一次请求：6行、KEY_CONFLICT、转换失败0、零基行2/3和4/5仅total_share/update_flag不同；没有测得最新标记或唯一键总数。safe SHA `473cc864541a24ecdcb53fc703fc28516995b54a026b34218bab3655e2d197bf`。三个接口保持v3撤回，原参数/字段/精度/键/SINGLE均未改变。只更新cashflow和repurchase当前未决原因为版本/身份规则未解决，全部45轮原case对象保持。

三个接口的21项仍在ISSUE-031目标中，尚未获准另行递延；ISSUE-031不能完成，ISSUE-032不启动。恢复条件是明确各接口可执行的冲突保留/身份规则并据此修订设计，或用户明确批准另行递延和目标变更；诊断请求不重复执行。

## 独立续验的专项SQL汇总

income8/fina_audit4/express6/stk_managers8四个独立成功轮按各自原业务键最终保留8/2/2/13键；income和express分别两股4+4及1+1，managers为5+8。managers原八字段元组与fingerprint计数匹配，没有独立重算fingerprint编码。对应各轮announcement-sql.json已与通用SQL一并审查；SHA依次为`4ec8ecc4b0e6ccdf2d821142f3f00d3fff3d5155255f45d732108e7f48aaeb74`、`01c290c52c75d5af6cae5fd80957a0b5890525f3745facb944db8f9ac6046b74`、`986928555dce15f97a9906b8d304353a115bfbf43f0742594b1b30290e904874`、`3c69261d144bc3a1d2abb75b0e3883310b8168e202896c5e036103e88f644393`。

top10_holders8与top10_floatholders8均保留原四字段键ts_code,end_date,holder_name,ann_date，最终各100键，股票分布分别40+60、54+46；后股票写入没有覆盖前股票。holders的600000额外保留20170831/20170904报告期各10键；floatholders两股各报告期键数分别15/15/14/10和12/12/12/10。两个接口各8项的“同股东同报告期不同公告日”组数都为0；部分报告期超过10行不能证明观察到了同一股东的多公告版本。各轮holders-sql.json SHA分别`5e6ba250e85474885f9c4d2d7d92da882219ef73a39f134f678a4c5245e9f916`和`338238115ac35f114cbbcbf59acf2612a12038960d73e561cfa0b89a8b0e8b9e`；最终键摘要分别`4b457f387aa7db4254ff5683e4eea3a63791291aca3cc48e001c6fab4af5dde3`和`e98f2805ed7a3ad1434cd5cf0af409bf37ba38964c0e06c64e0e213a97f9007f`。

regression4依次为daily_basic/moneyflow/stk_limit/margin_detail，最终键数4/6/6/6，来源及插入各22、更新0。SQL键摘要由准确SOURCE日期、股票和原字段顺序独立重算一致；保持原v2、6000/5800阈值、TRADE_DATE及NATIVE_RANGE/可拆合同。sql-supplement.json SHA `ec1f55a08737f5a8fbd76f2df798e536ed83fbe6911c214da252f73be46faf09`。上述三轮在5ecb993e…验收包上完成并清理秘密，数据库保留；没有把前四轮a77d99ac…包结果归到新包。

## 本次续办最终离线核对

真实调用全部结束后，使用当前唯一索引执行以下命令，exit0，111/111 PASS、失败/取消/跳过0；日志`/private/tmp/issue031-repurchase-control/final-stage-final.log`。阶段断言此前先出现109/110（旧阶段预期失败），按真实新结果更新并增加三轮绑定/历史保全检查后通过111项；没有修改生产运行代码或重置索引。

```sh
/private/tmp/issue031-repurchase-20260914T211953Z/data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js
/private/tmp/issue031-repurchase-20260914T211953Z/data-plane/tensor-app/target/frontend/node/node /private/tmp/issue031-repurchase-control/final-audit.mjs
git diff --check
git diff --cached --check
```

最终audit为PASS：272精确计划/参数/日期轴/逐case构建匹配，251/3/18和正式30/4/6吻合；原26轮哈希`95b2e7e0a4743f982d1818540ea8075f694050b9e0ac51a13617f63529381313`及全部42轮基线对象保持，新三轮原始/规范输出、冻结身份、输入、清理和凭据文件移除均核对。报告`/private/tmp/issue031-repurchase-control/final-audit.json`，当前索引SHA `1a83add3b0288d2823c1f2bf1d862bc279ac0b8c59cd0ab912c1a2a1c2321558`。

`post-live-source-audit.json`同目录：原898文件冻结快照及两JAR保持；422相关源码/合同中，当前工作树相对真实运行包只有tushare-range-evidence.test.js阶段断言变化，生产与合同源码一致。登记文档另有更新，不能宣称最终工作树全部字节等同旧快照。适用完整acceptance构建为此前同一冻结包的1137后端/打包（66 suites）与524前端，定向158通过；新包metadata只做4个撤回接口，4/4通过且零上游/TASK/records，清理通过。旧包metadata40/受控页面15不计作本包检查，本次文档/证据测试变更不重复完整构建或真实请求。

ISSUE-031以251/272及三接口21项原键冲突暂停，按[暂停交接](../task-handoffs/ISSUE-031-handoff.md)记录IN_PROGRESS → BLOCKED。ISSUE-032与ISSUE-033保持NOT_STARTED，母任务未关闭；财务指标已按用户要求单独登记并加入Git。此次续办变更未commit/push，先前要求的一次远程提交已由f563bd9完成。

最终增量独立审查未发现阻断问题；审查指出的看板旧278范围/首次执行动作及回归四接口“开放”措辞均已修正。当前文档、索引未决原因、阶段测试与BLOCKED状态一致，未扩大诊断、递延或实现范围。

## 2026-09-15四接口排除与ISSUE-031收尾

用户明确本次不支持balancesheet/cashflow/repurchase/fina_indicator的RANGE批量下载，并要求不开始032；[范围决定](../issues/proposals/ISSUE-026-range-scope.md)限定替代原34/278全量要求。当前30个RANGE接口、251项固定TASK全部有既有合格证据，原27项排除并保留4 FAILED/23 NOT_RUN；没有将其改为PASS或删除原run。

本次仅修改范围文档、四接口的正式EXCLUDED处置/依据引用及现有证据测试的阶段预期。JSON中原45轮/1217case/1362来源请求逐对象保持，原inputHashes与其余36个接口逐对象保持；四接口的SOURCE/TASK状态、日期/参数、cases、unresolved、原提取规则和v3均保留。RESPONSE_ONLY的decisionRef继续指向已补充本次排除决定的ISSUE-025决策节，另在四接口evidenceRefs加入本次精确范围引用；财务指标decisionRef指向本决定，其原规则依据仍保留。

本次离线验证：

- `data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`：111/111通过，失败/取消/跳过0、exit0。日志`/private/tmp/issue031-scope-6edtmtx5/evidence-tests.log`。
- 同一Node执行`/private/tmp/issue031-scope-6edtmtx5/scope-audit.mjs`：PASS，exit0。机械核对原272项的参数/日期轴/包绑定，排除三个接口21项后精确剩251项、30 API，均PASS且原所属run清洁；另核对财务指标6项，合计27项排除及原状态。45轮逐对象、最后三轮原始安全结果/清单/身份/清理及当前30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY通过。报告`scope-audit.json`，唯一索引SHA `6ef89b6692fdef4791b0ead64c8646ecde7646115e9aae2e907eac0850399cb3`。
- 422个运行/合同相关文件对比原冻结副本，仅证据测试变化；生产与合同源码保持，未构建新包或重写旧任务包归属。本次真实SOURCE/TASK/SQL新增均0。

| ISSUE-031修订后Acceptance | 结果与依据 |
| --- | --- |
| 1 固定纳入任务、SQL及历史保全 | 251/251 PASS，各自原SOURCE/参数/包、SQL及清洁身份已归档；本次scope-audit复核，原27排除项历史保持 |
| 2 mainbz、disclosure与日期/历史/BJ场景 | 原mainbz16项（含四新TASK）共19父/35成功叶、父零写入，四新TASK中三项实际SPLIT；两次disclosure重下及日期/历史/BJ的原SQL与独立运行审查保留，见前文对应轮次与专项SQL汇总 |
| 3 纳入的响应采集与逐接口准入 | 八个纳入的RESPONSE_ONLY及其余22个严格规则接口有实际TASK/SQL/页面和清洁证据；四排除接口保持失败与v3拒绝，不声称修复 |
| 4 相关回归、审查与准确汇总 | 111项证据回归和本次离线审计通过；此前各真实轮的独立审查保留，当前范围/能力/手册一致 |

据此仅完成修订范围内的ISSUE-031，记录IN_PROGRESS → COMPLETED。四接口问题继续保留，本次不处理；ISSUE-033保持NOT_STARTED。遵循用户“不开始32”，ISSUE-032保持NOT_STARTED、无后继交接、无六门禁执行；ISSUE-026/T13/T14/母issue均未关闭，不提交、推送或发布。
