# ISSUE-031：真实区间任务与SQL验收

## 当前结果

BLOCKED（2026-09-15）。前三轮82项TASK通过；第4轮首项FAILED，其余34项及后六轮161项未运行，全计划82 PASS / 1 FAILED / 195 NOT_RUN。按专属设计停止真实执行；fina_indicator候选已撤回为NEEDS_VERIFICATION / v3。尚未满足278项验收条件。

## 输入与构建

- ISSUE-030冻结副本`/private/tmp/issue030-work-20260914T025010Z`：893文件snapshot `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`；422相关源码逐文件相同，两包、manifest/examples、272输入/绑定与正式索引哈希均核对通过。只有后续任务登记文档变化。完整核对`/private/tmp/issue031-control/preflight-identity.json`。
- 278项固定清单`/private/tmp/issue031-control/ten-round-plan.json`，SHA-256 `6e24193f2cc085711429ee39db54318034a088797550625672ddfc3f48978f3f`。严格validateEvidence/validateCasePlan/selectTaskCases通过；278不同TASK caseId、276完整SOURCE身份、278绑定，计划records查询556。272既有参数、ID和来源保持，新2披露重下紧随原event，新4回归按指定SOURCE复制。
- 独立准备审查确认272来源有效；mainbz旧annual实为单日端点，已按实际日期排到新annual/wide之后。trade_cal/margin另需按交易所只读SQL复核；此处记录运行前准备结论，实际结果见下文。

## 十轮登记

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

当前实现仅撤回fina_indicator准入：版本递增v3、sourceVerified=false、verificationEvidence=null；公开能力为NEEDS_VERIFICATION及UNKNOWN规则，保留原参数、内部100阈值、日期轴、字段/业务键和SINGLE。此变更不代表适配错误已修复。唯一索引保留失败v2的实际run，在接口当前版本记录v3及未解决项；正式10 AVAILABLE / 24 NEEDS_VERIFICATION / 6 SINGLE_ONLY，当前候选运行能力为33 AVAILABLE / 1 NEEDS_VERIFICATION / 6 UNSUPPORTED。

独立排查确认现有安全证据不足以定位根因：字段转换/精度/日期错误与同原业务键内容冲突均可能产生ADAPTER_TYPE_INVALID，持久统一消息已丢失异常分支；旧SOURCE仅日期/行数投影，模板与旧成功SINGLE为空。没有证据支持放宽字段、精度或业务键。恢复须先取得安全失败诊断或脱敏复现样本，区分VALUE_CONVERSION与CONFLICTING_KEY，定向修复并重新冻结；重试须明确新的run/case与原SOURCE映射、新空schema和预算，不能复用已登记ID。详见[阻塞交接](../task-handoffs/ISSUE-031-handoff.md)。


### 撤回后离线验证与新身份

定向三个Java类155/155 PASS（Policy121、Download30、Availability4），新准入拒绝用例先RED（预期NEEDS_VERIFICATION、实际AVAILABLE），再GREEN；编译与沙箱Mockito初始化失败排除后才观察到该有效RED。Node证据104/104 PASS，负例保留SOURCE-only不能开放与已使用TASK ID不能再选。正式前26轮与运行前baseline逐对象相同，`JSON.stringify(runs)`（保持键顺序）SHA `95b2e7e0a4743f982d1818540ea8075f694050b9e0ac51a13617f63529381313`。

新隔离副本`/private/tmp/issue031-withdrawal-20260914T182807Z`由当前897个受Git跟踪文件的工作字节构建，包含前序未提交成果；snapshot `632a8308f20b33908542f2cb65e821a0a3037a3abba78194ae95dbda54e064cf`。运行`mvn -o -f data-plane/pom.xml -Pacceptance verify`退出0：后端/两包1134项、前端524项全部通过，失败/错误/跳过0。日志`/private/tmp/issue031-control/withdrawal/build.log`，汇总`acceptance-summary.json`；422个相关源码/合同文件与当前工作树逐文件一致，后续只更新交接/验收说明。

- sourceDiff SHA：`bb737f6f9cbc549d45b3d78797f30b24fdc10bd6d1537bb3b9ca7b14eef54b2f`。
- 新生产JAR SHA：`ff30109702e240d34782618f62ab9efed4bdd977e8a691f322d05f6806838bb5`。
- 新验收JAR SHA：`6522cbbd6d02decf91136bc47766de59ec55e369b84fd3e9d00390bd47e5d7f1`。
- manifest SHA：`386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984`；request examples SHA：`6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`。

以上v3构建没有执行真实来源或TASK；四轮实测仍属于前文保存的旧v2包。没有运行ISSUE-032最终六门禁或发布脚本。

新包普通浏览器命令`npm --prefix control-plane run test:e2e -- e2e/tushare-metadata.spec.js`：40/40 PASS（3.0分钟），exit0。实际40个能力响应逐项匹配独立预期，fina_indicator日期区间禁用、SINGLE可用。安全结果taskPosts/synchronousDownloadPosts/recordsGets/upstreamCalls均0，jvm/sentinel/privateLogScanned全部true，验收包前后SHA一致；外层自有临时MySQL容器和全部凭据文件已删除，旧四个实测schema保留。原件`/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t04-5KbmQO/metadata-evidence.json`，SHA `5ad837a993cb03bb18f7900cf4554e002e04257971c0655dca22600be4feb0a0`，汇总`/private/tmp/issue031-control/withdrawal/metadata-summary.json`。

最终保全复核`/private/tmp/issue031-control/final-preservation-audit.json`确认旧893 snapshot/两包、原26轮完整对象、四轮临时秘密清理；未执行任何额外来源诊断。独立代码/证据复核确认v3撤回、422相关文件与两包身份、1134后端及524前端结果，支持BLOCKED而非COMPLETED。最后限定复核进一步确认metadata安全原件40项全通过、零TASK/零上游、清理与包身份一致；下述未执行诊断提案的单请求和安全输出边界无发现。

### 下一次诊断提案（未授权、未执行）

恢复前拟额外执行1次SOURCE-only诊断：固定`fina_indicator`及原失败params（000001.SZ，20250331～20251231），新独立diagnostic runId，最多1个来源请求，无重试、分页或换样本；不给数据库配置，不创建TASK，不写业务表。响应只在内存中按冻结原字段和原适配器验证，输出仅白名单元数据：请求计数、适配成功/失败、VALUE_CONVERSION或CONFLICTING_KEY分支、rowIndex、field/逻辑类型、数值类型及精度/小数位分类；不输出或保存Token、原始响应、财务值、业务键内容或原始异常消息。结果单独归档，不覆盖失败v2 run，不计为TASK通过，也不重新开放v3。诊断完成或失败立即停止；若未复现，仅记录本次观察，不宣告历史失败根因已解决。

该额外来源请求超出本设计“不重新调查或重跑已合格SOURCE”及失败停止边界，须用户明确同意后才执行。即使得到根因，后续TASK补验仍须明确新的版本/冻结身份、run/case与原SOURCE映射、新空schema和预算。
