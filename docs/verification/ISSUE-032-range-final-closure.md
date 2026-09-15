# ISSUE-032：最终回归与母任务收尾验收

## 结论与范围

2026-09-15，用户最新要求“完成issue32”，取代此前“不开始32”的时序要求；[四接口范围决定](../issues/proposals/ISSUE-026-range-scope.md)继续生效。六门禁、历史/身份审计已通过；最终增量独立审查PASS，本次母任务状态已合法收尾。

- 当前纳入 **30个RANGE接口、251项固定TASK**，均有准确SOURCE/参数/日期轴/原包、TASK/SQL及清洁运行证据。原278项总追踪中的另外27项明确排除，原4 FAILED/23 NOT_RUN保持；不计PASS，不修复或重新调用排除接口。
- 唯一索引为 **30 AVAILABLE / 4 EXCLUDED / 6 SINGLE_ONLY**。运行能力为30 AVAILABLE /4 NEEDS_VERIFICATION(v3)/6 UNSUPPORTED，四接口RANGE继续拒绝；40接口SINGLE保持。
- 本次测试增量包括应用装配集成测试的过期能力数量断言（34/0→30/4），及两个旧SINGLE浏览器场景的显式模式选择；生产代码、参数/业务键/迁移及真实任务记录无本次改动。其余变更是设计、报告和状态收尾。
- 新增文件加入Git；没有提交、推送或发布。完整`sh scripts/verify-contracts.sh`未运行：当前分支`feat/download-by-date-range`且受保护输入有未提交成果，不满足main/干净输入/HEAD发布前置。下列六门禁不等同于发布验证。

## 完整源码与包身份

所有本次原件在`/private/tmp/issue032-final-m_or9ii3/`。`baseline/`、`initial-status.txt`及两份初始patch保留会话起点；独立clone `source/`逐文件带入全部工作树字节。Java21.0.11、Node24.15.0、npm11.12.1、Maven3.9.15、MySQL8.4.6。普通命令净化真实账户、数据库和live变量。

| 身份 | SHA-256（HEAD为Git提交ID） |
| --- | --- |
| 工作树基点HEAD（未提交成果另见快照） | `f563bd9fb57ca00508dd997548c296d7f5cafb93` |
| 最终900文件快照 | `6ffe6a09505a399b5e4e0601e80481bf5a8723c9bdf3a2a2cc9ae1dff3d76ca1` |
| 422源码/合同文件 | `4424be07e60f076da8fb8eed3026334959d3e1760bb11b41150b7334d3d5d0d1` |
| G3生产JAR | `74138a3921897a69a8e7caf0baf927824f436c91c0637de4944fc1b5812106ab` |
| G4生产JAR | `60c1730ed8f3b20f07cba8007bffd1087e42f9ef0ee47316cbe78df6fc118d6e` |
| G4验收JAR（G5/G6绑定） | `fdc0f51b8b93b44618fa02e9449e2125c68aabb41698222bc4f70dc7482f45da` |
| 唯一验收索引（本次未改） | `6ef89b6692fdef4791b0ead64c8646ecde7646115e9aae2e907eac0850399cb3` |
| manifest | `386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984` |
| 当前请求样例 | `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f` |

`source-files.json`为完整900文件清单；`source-audit.json`记录422个源码/合同文件与最后真实任务快照的比较。差异包括本次`DownloadTaskApplicationConfigurationIT.java`、`dataset-query.spec.js`、`download-outcomes.spec.js`与此前已有的`tushare-range-evidence.test.js`；运行代码、正式合同与最后真实包一致。最终文档相对冻结源码的差异另存，不将文档更新冒充重新构建。旧真实任务各自绑定原包，未替换成上表新哈希。

## 六项门禁

从同一最终源码按下列顺序串行执行。G5/G6只追加`--reporter=list,json`输出机器报告，不改变测试选择、workers=1或retries=0。日志/报告位于本次目录的`gateN/command.log`、`result.json`和`reports/`或`playwright.json`。

| 门禁 | 实际命令 | 结果 | UTC起止 |
| --- | --- | --- | --- |
| G1 | `mvn -f data-plane/pom.xml '-Dtest=*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` | 83 suites / 1401 JUnit，全部PASS，exit0 | 04:06:50～04:11:28 |
| G2 | `npm --prefix control-plane test` | 34文件 / 524前端单测，全部PASS，exit0 | 04:11:28～04:11:36 |
| G3 | `mvn -f data-plane/pom.xml clean verify` | 65 suites / 1134 JUnit，全部PASS，exit0 | 04:11:36～04:12:21 |
| G4 | `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 66 suites / 1137 JUnit，全部PASS，exit0 | 04:12:21～04:13:05 |
| G5 | `npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js --reporter=list,json` | 1文件 / 15浏览器，全部PASS，exit0 | 04:13:38～04:13:55 |
| G6 | `npm --prefix control-plane run test:e2e -- --reporter=list,json` | 7文件 / 138浏览器，全部PASS，exit0 | 04:13:55～04:29:56 |

所有最终JUnit失败/错误/跳过均0；G1/G3/G4 Maven各自还执行524项前端单测和Vite构建，与G2分开记录。G1的生命周期5项内实际启动flow2/resume1浏览器，各一次且无跳过；独立JSON另存gate1/lifecycle-summary.json。G3包含4项生产包合同，G4包含4项生产包和3项验收包合同。G6逐文件实际数：dataset-query=11、download-outcomes=15、download-tasks=15、fixture-flow=3、stock-download-parameters=3、tushare-metadata=40、ui-redesign=51。相对原126项普通基线增加12项响应采集/历史语义场景，未删旧场景；专用账户套件不在普通七文件范围，也不计作通过或跳过。

G6包级原件另归档到`gate6/packaged-evidence/`及`packaged-evidence-summary.json`：数据查询为375次本地受控TASK/来源请求、39次records响应，最终披露123键（旧公告日1/新公告日122）；下载结果为14次TASK、17次查询、8次本地stub请求；40项metadata/数据集均通过，39个必填表单阻止空提交、1个无参表单，TASK/同步下载/records/上游调用均0。以上受控请求不计入真实账户45轮历史。

### 首轮失败与最小修正

首轮G1（03:33:09～03:37:49 UTC）实际83 suites/1401项，1400通过、1失败、0错误/跳过，退出1。失败为`DownloadTaskApplicationConfigurationIT.productionServletGraphStartsAfterFlywayAndCatalogWithOneSharedRunAndBudget`第139行：期待34 AVAILABLE，实际30；紧随其后的NEEDS_VERIFICATION空集合断言同样陈旧。四项撤回早已由031和明确范围决定落实，独立Policies/Availability矩阵与实际应用均为30/4/6，根因是全量IT断言未同步。

仅将这两条断言更新为30 AVAILABLE/4 NEEDS_VERIFICATION，保留40定义、6 UNSUPPORTED、零来源调用和所有装配/预算断言。`failed-gate1/`保存原完整900文件输入、日志、XML及失败身份`3ae67c2d4ce178fc2a62054a9765825ba61e3623e3226962b6d92a9330fa3552`。新冻结源码从G1重新执行全部六项，不沿用失败结果或删测试。

### 首次完整浏览器轮失败

03:46:55～04:01:17 UTC的G6实际138项，83通过、3失败、52因串行前例失败而未运行，退出1。该轮G1～G5已通过，连同完整900文件输入、当时两包及G6原报告保留在`failed-browser-round/`，快照为`18795465c3dc77bcce5a1ba41432c275685d76f4ce341970d8630ead426481f0`；全部自有资源清理成功。

`dataset-query`首例及`download-outcomes`缺日期场景均沿用旧默认模式：当前AVAILABLE接口默认RANGE，但原用例明确断言SINGLE并填写交易日期。参照现有股票参数/metadata用例，在各自`chooseTushareDownload`中添加选择“单次请求”及选中断言两行，保留所有精确请求、任务/SQL计数和不应提交的断言。

第三处为`metadataContract:margin`的全页“零表头”断言实际读到7个节点。单独运行原包/原用例通过，随后在新页面重复原步骤20次也全部通过；原主仓库metadata断言、日期控件和生产实现均未改。`diagnostic-repeat2/`保存加诊断的临时spec及实际指纹、20次步骤和清理；它不属于最终门禁，其继承的snapshot字段只标识诊断前基线。首次单场景诊断的Playwright退出0，外层仍沿用七文件检查而退出1；另有一版诊断循环因监视器主动关闭page而失败，已记录并修正为每轮新page，不能计作产品失败或门禁通过。首次7表头的具体节点来源未捕获，根因未确定；最终完整G6保留原严格断言，40项metadata和全部138项普通浏览器用例各一次通过；这些局部诊断没有计入最终门禁。

## 真实证据、历史与采用决定

本次`evidence-audit.json`和最终快照复验`evidence-tests-final.log`（启动时的`evidence-tests.log`也保留）：scope-audit退出0；111/111证据测试通过，失败/取消/跳过0。读取原272项计划及逐case替代/包绑定，排除三个冲突接口21项后精确剩251项，再独立核对财务指标6项，形成251/27；不能简单相加接口历史引用来统计当前固定计划。

| 固定纳入组 | 项数 | 证据入口 |
| --- | ---: | --- |
| limits / mainbz / calendar | 28 / 16 / 38 | [031前四轮](ISSUE-031-range-live-task-verification.md)及[T14登记](ISSUE-018-T14-runs.md)：限量、默认分类、真实拆分、完整日历/BJ |
| dates / events / history | 29 / 37 / 33 | 031独立续验：周/月/IPO/户数日期轴、非交易日/修订、历史期限费率 |
| response-trade | 24 | adj_factor8 / suspend_d16，单请求单叶子及SQL |
| income / fina_audit / express / stk_managers | 8 / 4 / 6 / 8 | 031各接口清洁独立轮，准确原键与两股票保留 |
| top10_holders / top10_floatholders / regression | 8 / 8 / 4 | 031最后三轮安全结果、SQL补充与各自原包 |
| 合计 | 251 | 30 API；413来源请求、4312成功来源行、2607插入操作、1705更新操作；操作数不冒充最终键数 |

唯一JSON保持会话起始字节：45轮/1217case/1362请求，SHA见身份表；原26轮哈希、42轮基线和全部45轮逐对象保全。最后三轮safe-results、输入清单、身份和清理原件重算匹配。原74 SINGLE/148 records查询来自完整清洁轮`issue018-t14-single-20260913T093339Z`，两股票SQL及历史保留见[017验收](ISSUE-017-stock-scoped-downloads.md#issue-018-t14-完整两股票验收2026-09-13)。原失败SINGLE轮、15个旧任务、旧25优先RANGE任务及成功数据库保留，不重新发账户请求。

| 已批准口径 | 本次证据与说明 |
| --- | --- |
| [019工程阈值](../issues/proposals/ISSUE-019-documented-range-limits.md#决策记录) | daily6000/forecast3500/dividend2000按原始行数判断，小于阈值采用完整口径、满额拆分、单日满额失败；工程决定不写成新上游保证 |
| [020默认分类/100阈值](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录) | 保留省略type的一次实际P/D/I及原键；四个新SOURCE和四匹配TASK的全年/六年宽窗验收完成，其中三项TASK实际触发SPLIT、合计19个父节点；父不写，成功叶<100；原4满额缺证记录保持 |
| [021日历](../task-designs/ISSUE-021-design.md) | SSE/SZSE完整日历，BJ top_list参照SSE但保留BJ证券；直接trade_cal BSE拒绝，margin保留三exchange_id；全休市与空响应分开 |
| [022/023日期事件](../task-designs/ISSUE-022-design.md) | weekly/monthly以实际最后交易日，IPO不用上市日；公告/报告期/业务日期分开；daily重叠、block_trade多键、dividend非交易日、disclosure两次原键重下均有SOURCE/TASK/SQL。真实disclosure重下未观察值变化，实际更新操作/原键保留；内容修订由受控事务覆盖 |
| [023质押样本](../task-designs/ISSUE-023-design.md#质押非空样本决定2026-09-13) | 000014.SZ/600000.SH非空代表性；000001.SZ历史空记录保持，不扩大为无质押历史结论 |
| [024历史支持](../issues/proposals/ISSUE-024-historical-support.md#决策记录) | 三slb接口5000阈值、33项原历史窗口/边界/期限费率通过；精确历史起止及持续保留保证仍未知，不把停业日设为API截止 |
| [025响应采集](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录) | 原11条RESPONSE_ONLY保留，本次8纳入、3排除；只有返回记录采集，不保证完整。非空、空、失败提示与持久规则一致；21 ROW_LIMIT+1 CALENDAR_COVERAGE纳入 |
| holders观察边界 | 两接口原四字段键/两股票/报告期保留；未观察同股东同报告期多公告版本，不以每期超过10行替代。原合同要求报告期/公告日语义，没有要求该样本必须出现多公告版本 |
| [四接口排除](../issues/proposals/ISSUE-026-range-scope.md#决策记录) | balancesheet8/cashflow8/repurchase5/fina_indicator6问题保留；原4 FAILED/23 NOT_RUN不改；033未开始，重新纳入需另行解决原问题 |

## 兼容性与正式合同

下列方法已在本次G1 XML或G5/G6 JSON逐项匹配；具体报告映射另存`contract-tests.json`，没有仅凭类存在判定通过。

| 范围 | 实际检查 |
| --- | --- |
| 旧快照/当前能力变化 | `DownloadTaskJsonTest.responseOnlySnapshotsRetainStrictReadingAndOldPolicyVersions`；`DownloadTaskServiceTest`历史summary不查当前插件、SINGLE=null、损坏快照不回退、策略/版本变化拒绝retry/resume |
| 响应采集/严格规则 | `DownloadTaskRunnerIT`原生单叶非空/空/更新保留、适配与中途写入回滚、错评估/UNKNOWN零写入；`TushareBatchPoliciesTest`等号/单日满额和四接口撤回 |
| HTTP/schema/OpenAPI | `DownloadTaskContractTest`新能力组合、持久extraction、SINGLE=null、schema/examples与OpenAPI；`DownloadTaskControllerIT`实际HTTP/SQL |
| 页面 | G5的15场景覆盖提交前说明、RESPONSE_ONLY非空/空/FAILED/PARTIAL_FAILED/INTERRUPTED、旧严格/UNKNOWN/SINGLE及retry/resume；G6 metadata40对比独立矩阵 |
| SINGLE及参数链 | G1 `StockScopedDownloadTest`、Resolver/Binding、Service/Runner；G6 stock-download-parameters与验收包outcomes/fixture/query，保留34股票/6非股票及旧同步合同 |

## 母合同逐条核对

本表是结果核对，不以母任务已关闭作为子任务通过的前置。所有条款均按已批准四接口范围及上述采用口径解释。

### ISSUE-026 Acceptance

| 条款 | 结果与依据 |
| --- | --- |
| 1 实现与消费者一致 | PASS：027/028交付与G1/G2/G5/G6；响应采集、UNKNOWN拒绝及严格规则边界见上表 |
| 2 固定来源/任务/SQL | PASS：251原参数/各自包精确绑定；27明确排除；mainbz四新来源/四匹配真实任务（其中三项实际触发SPLIT，合计19个父节点）、两披露重下、四回归及74 SINGLE保全 |
| 3 逐项开放/版本 | PASS：30项都有适用规则、有效SOURCE与清洁TASK/SQL；40矩阵和四接口v3撤回与G1/G6及手册一致 |
| 4 门禁/安全/历史/终审 | PASS：六门禁/安全/历史审计及最终增量独立审查通过 |
| 5 母任务收尾 | PASS：T14/T13、总体§6、017/018逐条事实成立，已按下述顺序完成各状态并最后完成026 |

### T14与T13各六条Acceptance

| 任务/条款 | 结果与依据 |
| --- | --- |
| T14-1 六项范围与历史 | PASS：工具、来源、SINGLE、疑难决定、RANGE与最终回归已交付；40/34股票/6非股票、原31原生/3逐日目录身份保持，四接口按范围决定排除；旧3轮329case保全 |
| T14-2 固定运行/身份/新库 | PASS：T14/029/031登记与逐轮哈希/新空schema/SQL/清理；失败身份不追认 |
| T14-3 完整SINGLE | PASS：完整74任务/148查询，两股票归属和历史保留；旧14 PASS不替代完整清洁轮 |
| T14-4 可用项与代表场景 | PASS：30项真实证据、日期/日历/历史/默认分类及上表限定决定；四接口27项明确排除 |
| T14-5 版本/回归/说明 | PASS：G1–G6及兼容性表；RESPONSE_ONLY空、失败、非空不冒充完整；原最小满额拒绝 |
| T14-6 总体/原T13/母issue | 本节其余逐条结果成立后办理T14完成，再以同批证据收尾T13 |
| T13-1 40项矩阵 | PASS：唯一索引及G1/G6；明确30/4/6，股票约束/原键不变 |
| T13-2 SOURCE与匹配TASK | PASS：scope-audit及每个AVAILABLE的原来源/参数/包/SQL；不靠网页或小样本推定完整 |
| T13-3 代表场景与失败 | PASS：上述真实代表场景与G1受控满额/回滚/恢复分开，四接口按范围决定排除；父批/日历不累计证券成功行 |
| T13-4 疑难口径 | PASS：019～025明确采用决定及实际证据，SINGLE股票验证已补；不将用户决定冒充上游保证 |
| T13-5 能力/历史/页面/回归 | PASS：G1–G6、旧快照/定义变化拒绝及40项能力/手册；本次无账户重复请求 |
| T13-6 总体/母issue | 本节总体§6及017/018逐条事实成立后，仅办理最终验收状态，不重复旧实施 |

### 总体设计§6八条

| 条款 | 结果与实际证据 |
| --- | --- |
| 1 异步ID/刷新关闭后继续 | PASS：G1 `DownloadTaskLifecycleIT.browserDisconnectAndRetryUseTheRealApplication`，真实Servlet/MySQL及flow2；关闭整个BrowserContext期间来源和提交继续，重开同ID |
| 2 模式/日期/参数/股票 | PASS：G1 Resolver/Binding、Policies/Availability；74 SINGLE和251 RANGE逐项SOURCE/TASK/SQL，最终30/4/6有明确范围 |
| 3 批次原子提交/部分失败 | PASS：G1 `BatchCommitServiceIT`18项和RunnerIT，写入/成功标记共回滚、成功兄弟保持；HTTP/页面真实失败状态 |
| 4 retry/resume/故障 | PASS：G1 RecoveryIT、ControllerIT、LifecycleIT及G5；只重排失败/未完成、启动不自动续跑、迟到许可与重复点击拒绝、提交回执丢失找回 |
| 5 拆分/覆盖/计数 | PASS：G1 RepositoryIT拆分原子性、Runner边界及mainbz真实树；父无写入、未知/单点满额/未完不算完整成功，RESPONSE_ONLY按限定合同 |
| 6 预算/节流/后台生命周期 | PASS：G1 RunnerTest/IT节点/请求/行数/时间预算和跨轮次计数、TushareProClient/Throttle测试；Lifecycle后台执行不依赖servlet/浏览器 |
| 7 非Tushare/旧SINGLE | PASS：G1 http_test通用来源/事务/retry、架构测试及SINGLE回归，G6 Fixture真实验收包 |
| 8 源码/迁移/包/浏览器/真实接口 | PASS：G1 schema47、G3/G4包合同、G5/G6；30项真实证据齐全，四项明确排除，基础设施与账户证据分别记录 |

### ISSUE-017九条关闭条件

| 条款 | 结果与依据 |
| --- | --- |
| 1 40入口/34股票/6非股票 | PASS：G1定义/能力、G6 metadata40/股票参数页面 |
| 2 股票必填/错误零访问 | PASS：G1 StockScopedDownloadTest与Binding/Resolver；G6缺失/非法表单拒绝 |
| 3 非股票原方式 | PASS：完整SINGLE六项及G1/G6，合法旧参数不注入ts_code |
| 4 参数record/codec唯一映射 | PASS：G1 Resolver87及Binding往返，四RANGE形状与全部40原请求 |
| 5 上游股票/无回退 | PASS：G1 StockScopedDownloadTest、TushareClient/Policies；真实74 SINGLE及纳入RANGE参数保留 |
| 6 两股票归属/混股拒绝/空 | PASS：完整74任务SQL及251项历史保留；混股受控整批拒绝/空响应不扩大请求 |
| 7 接口差异/完整性说法 | PASS：top_list日历与交易日、mainbz纠正及批准默认分类、index_member_all股票语义；采用口径准确说明 |
| 8 真实逐项记录 | PASS：34×2+6的清洁SINGLE轮及SQL，未知历史保留/空/失败继续记录 |
| 9 元数据/合同/样例/回归 | PASS：G1–G6、40项独立能力预期与manifest/examples哈希保持，链接本报告关闭 |

### ISSUE-018六条关闭条件

| 条款 | 结果与依据 |
| --- | --- |
| 1 最终支持清单 | PASS：27个原生+3逐日纳入，4原生按明确决定排除，6 SINGLE_ONLY保持 |
| 2 全参数链/日期标签 | PASS：G1/G2/G6及逐项真实SOURCE/TASK，34股票/6非股票保持 |
| 3 日期/股票/边界/空 | PASS：G1日期/绑定/策略与G5/G6；真实整段/两端、跨年、周/月最后交易日、公告与报告期分离 |
| 4 拆分/满额/失败/幂等 | PASS：G1真实MySQL/受控阈值/故障、mainbz真实拆分、daily重叠及disclosure原键重下，操作次数与最终键数分开 |
| 5 特殊接口结论 | PASS：mainbz默认分类/100工程阈值、slb历史限定、RESPONSE_ONLY及BJ/BSE日历均有明确决定与证据 |
| 6 回归/逐项真实API | PASS：G1–G6及30纳入/251项SOURCE/TASK/SQL，调查文档与旧单日样例没有代替区间验收 |

## 独立审查与状态

独立审查已核对源码、原合同、251/27精确统计、SINGLE和holders观察边界，无新的实质阻断；两行IT断言及两个SINGLE浏览器helper的模式选择修正另经独立复审通过。浏览器编排预审通过，并补强finally：分别清理进程/容器/秘密，保存所有清理错误且不覆盖原测试失败。最终增量独立审查PASS：独立复算JUnit原始报告、六门禁顺序/最终快照、全部浏览器单次结果、受保护源码哈希、原包级证据及清理。HEAD身份文字与031交接模板顺序建议已落实；没有剩余实质阻断。

已按顺序记录：ISSUE-032 IN_PROGRESS → COMPLETED；T14、T13各自BLOCKED → READY → IN_PROGRESS → COMPLETED；随后017/018逐条确认已解决，最后ISSUE-026 IN_PROGRESS → COMPLETED。各权威看板保留原状态证据及历史handoff；033仍NOT_STARTED，不创建后继交接或T15。

## 清理与交付边界

本次自有preview已停止，自有MySQL容器已删除，临时0600秘密文件及0700目录已移除；8080/4173均空闲，清理错误0，见browser-cleanup.json（UTC 2026-09-15T04:29:57.105977+00:00）。G1真实生命周期桌面1440与窄屏390截图已目视核对，状态、日期与计数清晰，批次表保持局部滚动。G5/G6截图和报告归档至各gate目录。

文档收尾后再次核对最终源码、唯一索引原字节、563个本地链接、10次状态记录及Git暂存/未暂存差异格式，均通过；见本次目录`final-check.json`、`source-audit.json`和`state-transitions.json`。

日志、XML、JSON和安全证据保持；旧真实数据库/任务和SOURCE、成功/失败/空/未运行历史未删除。新包仅用于当前源码验收，不声称已部署；四排除接口问题及ISSUE-033继续保留。
