# ISSUE-018-T13：真实接口完整性验收与逐项开放

## Goal

为40项现有接口建立可追查的最终处理清单；仅在真实参数语义、完整提取依据和任务端到端结果均成立时开放对应RANGE。身份来自 `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md` 的T13、Order13，直接依赖T06和T12。

本设计创建于T12已记录COMPLETED之后。T12最终G6为7文件126/126通过，基础设施已有真实Servlet/MySQL/受控来源证据；这不证明任何Tushare RANGE已经验收。本次只准备设计与交接，T13实施、真实账户调用和策略修改均未启动。

## Scope

消费T06的31原生+3逐日策略及6项SINGLE决定，补官方/可核验完整性依据、真实来源语义与任务页面/SQL/日志证据，按证据修改对应策略版本及开放标记、精确回归和运行说明。记录全部40项，关联ISSUE-017尚未完成的真实股票归属与fina_mainbz默认type。

保留40生产注册、34股票必填/6非股票、RANGE29股票/5非股票、全部40项SINGLE、原业务键及历史manifest。不恢复9项退役接口，不添加多股票合并、type/P/D/I混合、VIP、limit/offset通用分页、新供应商、生产验证开关、自动重试或自动发布。若取得的完整性依据要求现有ROW_LIMIT/CALENDAR_COVERAGE以外的算法，先记录依据并修订本设计中的该项规则与测试；修订前保持关闭，不自行发明可用规则。

## Approach

### 直接输入及依据优先级

1. `docs/task-designs/ISSUE-018-design.md` §3.4–3.5、§5.3、§6–7与母issue逐项官网表固定产品范围、日期轴和完成标准。
2. `docs/task-designs/ISSUE-018-T06-design.md`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`、`TushareTradeCalendar.java`固定实际Policy字段、不可变注册表、UNKNOWN拒绝、纯参数转换及日历门禁。看板T06记录专项449、单测867及双包通过，但未调用真实来源。
3. `docs/task-designs/ISSUE-018-T12-design.md`、`docs/verification/ISSUE-018-task-infrastructure.md`固定当前可复用harness与证据边界。G1为1282、G2为468、G3为1025、G4为1028、G5为3、G6为126；故障/满额拆分使用其中精确方法，不为真实账户制造故障或反复取数。
4. `docs/issues/problems/ISSUE-017-stock-scoped-downloads.md`、`docs/verification/ISSUE-017-stock-scoped-downloads.md`及 `docs/contracts/download-request-examples.json`固定当前SINGLE参数。fina_mainbz已纠正为snapshot+ts_code；历史manifest的ann_date不再作为有效请求依据，历史内容不改写。

以上输入无冲突：T06提供尚未开放的策略，T12证明基础设施，两者都未提供真实完整性证据。母issue调研表的“当前参数”是调研时快照；现行请求以T06纠正及当前YAML为准。

### 40项实施矩阵

S=`ts_code,start_date,end_date`，D=`start_date,end_date`，E=`exchange,start_date,end_date`，I=`exchange_id,start_date,end_date`。N=原生，C=自然日逐日，T=交易日逐日。L只是候选官网阈值，必须核验其为实际截断合同后才能使用。官网URL统一为 `https://tushare.pro/document/2?doc_id=<编号>`，原核对日期2026-09-11；新读取另记实际时间，不改历史日期。

| API | 形状/规划 | 范围输出列 | 候选依据/文档编号 | 该项必须记录的语义或缺口 |
| --- | --- | --- | --- | --- |
| daily | S/N | trade_date | L6000 / 27 | 多日、两端、重叠重下；停牌缺行不能按每天一行判错 |
| weekly | S/N | trade_date | L6000 / 144 | 实际每周最后交易日，不能固定周五 |
| monthly | S/N | trade_date | L4500 / 145 | 实际每月最后交易日，不能固定自然月末 |
| adj_factor | S/N | trade_date | UNKNOWN / 28 | 全历史声明不等于无限量；补完整提取规则 |
| daily_basic | S/N | trade_date | L6000 / 32 | 股票条件与交易日期都有效，确认截断合同 |
| stk_limit | S/N | trade_date | L5800 / 183 | 循环获取声明与每片截断规则分别核对 |
| suspend_d | S/N | trade_date | UNKNOWN / 214 | 停复牌事件稀疏；补完整性，空样本不证明持续支持 |
| moneyflow | S/N | trade_date | L6000 / 170 | 2010起历史说明与实际样本范围 |
| margin | I/N | trade_date | L4000 / 58 | 保留exchange_id，核对返回交易所；BSE单列实际结果 |
| margin_detail | S/N | trade_date | L6000 / 59 | 股票/日期与业务键归属 |
| block_trade | S/N | trade_date | L1000 / 161 | 同股同日多条，不按股票日期先去重 |
| slb_len | D/N | trade_date | L5000 / 331 | 无股票输入，期限/规模多行不套股票校验 |
| slb_sec | S/N | trade_date | L5000 / 332 | 官网标停；记录有依据的历史窗口和实际可用范围 |
| slb_sec_detail | S/N | trade_date | L5000 / 333 | 同上；当前空结果不能证明历史或持续更新 |
| trade_cal | E/N | cal_date | 每自然日恰1条 / 26 | SSE/SZSE完整日历、全休市；BSE直接输入独立核验 |
| new_share | D/N | ipo_date | L2000 / 123 | 上网发行日期，不能用issue_date上市日期 |
| income | S/N | ann_date | UNKNOWN / 33 | 公告区间，普通单股票；补完整提取规则 |
| balancesheet | S/N | ann_date | UNKNOWN / 36 | 公告区间与报告期区分；补完整性 |
| cashflow | S/N | ann_date | UNKNOWN / 44 | 不用f_ann_date代替ann_date；补完整性 |
| fina_audit | S/N | ann_date | UNKNOWN / 80 | 公告区间不传period；补完整性 |
| forecast | S/N | ann_date | L3500 / 45 | 保留必填股票，不因空结果改VIP |
| express | S/N | ann_date | UNKNOWN / 46 | 普通单股票；补完整提取规则 |
| repurchase | D/N | ann_date | UNKNOWN / 124 | 默认无参数2000不是区间硬上限；合法多股票保留 |
| stk_managers | S/N | ann_date | UNKNOWN / 193 | snapshot SINGLE之外的公告区间需真实证明 |
| stk_holdernumber | S/N | ann_date | L3000 / 166 | 区间end_date、输入enddate和输出截止日不同义 |
| stk_holdertrade | S/N | ann_date | L3000 / 175 | 不按实际增减持起止日期校验 |
| pledge_detail | S/N | ann_date | L1000 / 111 | 输出start_date/end_date为质押业务日期 |
| fina_indicator | S/N | end_date | L100 / 79 | 报告期；ann_date在区间外合法；满额证据分层 |
| fina_mainbz | S/N | end_date | L100 / 81 | 未传type的实际默认范围；业务键无类型，不混P/D/I |
| top10_holders | S/N | end_date | UNKNOWN / 61 | 报告期，公告日可越出区间；补完整性 |
| top10_floatholders | S/N | end_date | UNKNOWN / 62 | 同上 |
| top_list | S/T | trade_date | L10000 / 106 | 完整日历后才逐交易日传trade_date；BJ映射独立核验 |
| dividend | S/C | ann_date | L2000 / 103 | 自然日含非交易日公告，不替换为交易日历 |
| disclosure_date | S/C | ann_date | L6000 / 162 | 最新披露公告，不承诺所有历史计划修改版本 |
| pledge_stat | SINGLE | 无RANGE | 110 | 仅ts_code；截止日声明不足以构造日期枚举 |
| stk_rewards | SINGLE | 无RANGE | 194 | 仅ts_code；单报告期不推成区间 |
| stock_basic | SINGLE | 无RANGE | 25 | ts_code+list_status与目标状态一致 |
| stock_company | SINGLE | 无RANGE | 112 | ts_code+exchange必须一致 |
| index_classify | SINGLE | 无RANGE | 181 | 无股票/日期输入，保留字典请求 |
| index_member_all | SINGLE | 无RANGE | 335 | ts_code为股票，行业代码不是股票；保留默认最新归属语义 |

起始状态：前34项 `NEEDS_VERIFICATION`、sourceVerified=false、公开完整性UNKNOWN；后6项RANGE `UNSUPPORTED`。其中11个UNKNOWN行完整性证据未解决前绝不能开放。状态是实施前事实，不是本设计已经执行40项的结果。

### 证据文件与判定

实施时创建 `docs/verification/ISSUE-018-range-acceptance.md` 和同名 `.json`。Markdown按“范围与构建身份、官方依据、40项结果、代表场景、受控边界、未完成事项”组织；JSON是可校验的结果索引，不保存原始响应。顶层固定 `schemaVersion:1, inputHashes, interfaces, runs`。interfaces严格按manifest既有40项顺序，每项固定：

- `apiName, rangeTarget, shape, planningMode, dateAxis, outputDateColumn, officialUrl`：来自本表；SINGLE-only的区间字段为null。
- `completeness:{kind,rowLimit,evidenceRefs}`：kind为UNKNOWN/ROW_LIMIT/CALENDAR_COVERAGE；无数值用null；evidenceRefs指具体可复核资料、获取时间及摘要，不能填“测试通过”。
- `sourceStatus, taskStatus, disposition`：前两者为NOT_RUN/PASS/FAILED/EVIDENCE_MISSING；disposition为NEEDS_VERIFICATION/AVAILABLE/SINGLE_ONLY/EXCLUDED。EXCLUDED必须另有用户明确范围决定的准确引用，初始不得使用。
- `policyVersion, cases, unresolved, decisionRef`：cases引用run中的具体caseId；unresolved列事实缺口；无决定时decisionRef为null。未执行不填0计数或PASS。

每个run记录唯一runId、开始/结束UTC、源码差异指纹、production/acceptance JAR及manifest/examples SHA-256、执行命令（无环境秘密）、退出码和清理结果。每个case固定记录caseId/API/阶段SOURCE或TASK、mode、精确params、日期轴、预期覆盖及其来源、实际状态、固定错误码、taskId/submissionId（SOURCE为null）、请求数、所有批节点及叶子/成功/失败/空批数、source/insert/update计数、SQL前后业务键数/归属/摘要、复查方式及证据路径。未发生的任务/SQL字段为null，不能给SOURCE探针虚构持久化。

校验器拒绝漏项/重复项、额外API、34/6或31/3/6错配、UNKNOWN标AVAILABLE、缺证据的AVAILABLE、失败/未执行结果标PASS，以及遗漏构建身份/清理事实。阈值等号、空批、SPLIT父与成功叶子计数沿T06/T12；写入操作数与最终去重键数分别保存，不相减推导insert/update。只保留脱敏字段、数量、日期集合摘要和业务键摘要；Token/数据库凭证/JDBC及原始错误/响应不进入仓库或公开日志。

### 执行顺序和门禁闭环

1. **本地准备。** 从本表和当前40项请求样例建立结果索引及拒绝测试，先验证漏项、UNKNOWN误开放会失败。修正现有真实账户浏览器harness的同步页面假设，保留普通G6和专用账户套件分离。没有账户配置也能完成这些工作；不把缺配置当skip或通过。
2. **官方依据。** 逐行读取母issue官方链接，保存实际日期、接口正文/限量/日期语义的准确引文及摘要。ROW_LIMIT要求证据说明按该L截断且小于L可视为本片完整；默认返回数、数据库batchSize和一次样例不足。UNKNOWN行取得可核验规则前保持UNKNOWN。网页变更、权限/历史范围均如实记录。
3. **来源语义取证。** 生产门禁仍关闭时，使用下述测试侧Probe直接调用已有TushareProClient，验证真实请求条件、字段、股票、范围/边界及候选完整性；不经任务服务、不入库、不篡改sourceVerified来“启动测试”。一轮参数清单先固定再执行，失败停该轮、保存已执行与未执行项，不自动更换日期/股票或重试。
4. **逐项本地候选开放。** 只有官方/可核验完整性与真实SOURCE证据均满足时，才修改该项Policy的规则、核验日期、verificationEvidence和policyVersion。证据引用 `docs/verification/ISSUE-018-range-acceptance.md#<api>` 及对应caseId；每项版本固定由v1升为 `tushare-range-v2`，以后有新语义再递增。未知/未证实项保持原值；禁止全表默认true。top_list还依赖已验证trade_cal。修改用于本地待验收构建，尚不是发布。
5. **真实任务闭环。** 重建并绑定新包，对候选开放项执行页面RANGE→202/Location→详情/全部批次→SQL复核→记录查询；成功和空结果与SOURCE证据分别记录。失败立即保留原因并撤回该项本轮开放标记，不能把“接收202”当成功；撤回/再次开放均更新版本，防止旧任务按变化后的定义手动续跑。已成功SQL不回滚、不删除历史。
6. **回归与最终状态。** 按下文命令检查所有受影响范围。全部纳入项满足总体设计§6、40项清单有明确结论且母issue关闭条件逐条成立，才完成T13/母issue。仍有待验证项时T13不完成；按实际阻塞写pause交接。不得通过把未决项写成SINGLE_ONLY或EXCLUDED缩减34目标。

### 测试侧来源Probe

新增同包测试源码 `TushareRangeSourceProbe.java`（名称不以Test/IT结尾），仅由显式 `-Dtest=TushareRangeSourceProbe` 选择；默认单测、全*Test/*IT和双构建均不调用真实来源。显式执行还要求 `TENSOR_TUSHARE_LIVE_E2E=1`、非空环境Token和已验证私有输入，缺失硬失败，不使用条件skip。新增 `TushareRangeSourceProbeTest.java` 仅用合成输入测试其参数/脱敏/拒绝逻辑，不能触发真实入口。

Probe复用DatasetDefinitionLoader、TushareProClient和TushareProperties，不新增HTTP客户端框架。baseUrl固定 `https://api.tushare.pro`，Token只从 `TENSOR_TUSHARE_TOKEN` 进入Credential；connect5s/read120s/maxResponseBytes67108864。`M14_T05_CALL_INTERVAL_MS` 仍要求2000～3600000，Probe与任务应用的来源节流均至少该间隔；严禁沿用T12假上游0ms。每轮BatchCallContext截止30分钟、beforeRequest最多5000次、收到停止信号不再预约；已有客户端逐次许可、共享节流、有限响应和错误分类保持。

输入 `ISSUE018_T13_CASES_FILE` 必须为绝对路径、当前用户普通非symlink的0600 JSON，所在本次目录0700；不含Token。顶层仅 `runId,cases`，每case仅 `caseId,apiName,mode,params,dateAxis,start,end,evidenceRefs`，全部API/参数/日期按矩阵校验，禁止type/VIP/offset/多股票。SINGLE遵循当前YAML，dateAxis/start/end均为null；RANGE这三项必须非空且与本表和params一致，使用T06 sourceParameters生成原生或单日参数。自然日Probe按闭区间枚举每一天；top_list Probe先直接取得SH→SSE/SZ→SZSE整段日历并调用同包TushareTradeCalendar.openDays校验，再按返回日期取数，不调用被生产availability阻止的plan。该测试侧编排不改变生产plan或缓存。trade_cal的BSE来源探针直接使用已白名单校验的exchange/start_date/end_date对象调用client，绕开的仅是测试取证侧本地准入，生产sourceParameters/plan仍保持拒绝；不得在任务服务内引入该入口。BJ日历参照只有取得明确官方/实际依据后才记录候选，不在Probe猜映射。输出至 `ISSUE018_T13_EVIDENCE_DIR` 私有目录的 `source-evidence.json`，只含上述安全投影；来源错误保留固定code，丢弃原cause/body。Probe不创建数据库连接，不改生产门禁，也不把assess对未验证策略返回UNKNOWN解释为失败的请求。

### 样本与代表场景的固定规则

当前 `download-request-examples.json` 的股票000001.SZ与日期20260807是首轮SINGLE输入，不把历史manifest的28成功/12空当预期。首轮RANGE使用同股票/交易所：交易日期窗口20260803～20260810，公告窗口20260801～20260831，报告期窗口20250101～20251231，上网发行窗口20260801～20260831；仅为请求计划，不预言有数据。每个原生窗口请求整段和两端单日，记录返回轴/归属与闭区间检查；宽窄相同仅支持边界观察，不能单独证明完整性。周/月线另以20260701～20260831记录实际最后交易日。

为闭合ISSUE-017，34股票项再使用600000.SH独立请求，stock_company对应SSE，其余合法辅助条件保持；两只股票分开任务/证据，绝不合成多股票请求。若事件/标停/历史样本为空或权限失败，不自动寻找直到成功；依据官方历史范围或可复核公告登记新case及理由后另开一轮。没有有意义的样本则EVIDENCE_MISSING，不硬填成功。

| 代表场景 | SOURCE与TASK必须分别观察 |
| --- | --- |
| daily多日与重叠 | 原区间成功后重下20260805～20260810；完整业务键集合、股属和已有行更新情况，不能断言上游价格跨时间绝对不变；实际source/insert/update来自持久任务与SQL |
| income公告 | 至少非空公告样本能区分ann_date和报告期，返回端点/邻界日期核对；完整性未知仍关闭 |
| fina_indicator报告期 | end_date在范围内而ann_date在外可合法；真实样本证明轴，`<100/=100/>100`拆分与最小满额由T12受控方法证明，不强迫账户刷出100行 |
| repurchase非股票 | 仅起止参数，多股票结果正常；默认2000不能升级为硬阈值 |
| top_list交易日 | SSE/SZSE完整自然日日历后逐开市日；日期序列持久化；20260808～20260809仅在实际完整日历确认全休市时作为空计划证据；BJ无依据仍拒绝 |
| dividend非交易日公告 | 以可核验的真实非交易日公告登记case，两个邻日也在自然日计划；当前首轮无此事件时如实未满足 |
| disclosure_date最新公告 | 对照实际最新公告日期/同业务键复查，说明修订可能；不声称历史每版可回放 |
| trade_cal | SSE/SZSE整段每天唯一、无缺日、含休市；全休市与空响应区分；BSE输入支持性单列，不能替换SSE |
| 日期/计数边界 | 同日、跨月、跨年、闰日、非法/缺端点/逆序由已执行自动化覆盖；真实已开放至少一项再用20251229～20260105核对跨年，两类证据分列 |

BJ/BSE若真实依据仍不足，保留本地拒绝及运行说明，不能以沪深成功推定北交所。两项标停接口必须明确实际历史窗口与支持限制；若官网/实际不可用，保留待验证，除非用户另有范围决定。fina_mainbz分别保留单股票SINGLE与报告期RANGE未传type的请求，依据官方默认说明与实际返回确定涵盖的分类；现有 `[ts_code,end_date,bz_item,curr_type]` 业务键不含type，不能通过多次P/D/I请求“补齐”。这几项外部事实是T13要取得的证据，不在本设计中填造答案。

### 真实账户浏览器与持久化证据

复用 `control-plane/e2e/tushare-live.spec.js` 的40项注册、私有目录/日志、输入哈希、RequestLedger、SafeLogSink、RunCounters和清理期限。将submitDownload/fixture准备/监控/日志关联改为正式task API：精确submit body含submissionId和mode，202校验Location/X-Request-Id，完整查询task/分页叶子并核对终态及固定错误；保留query记录来源列/精确小数/股票归属，旧同步接口另由后端回归保证。不以宽泛 `/api/**` 放行，新增capabilities/list/detail/batches必须逐一匹配；查询失败不是任务FAILED。

默认仍40项SINGLE测试：34项各两只股票、6项各原请求，精确74次真实任务提交，另有既有fixture两次；每样本固定执行提交前、终态后两次records查询，股票项均携带本样本股票筛选且核对SQL中另一股票历史保留，实际查询总数固定为148并单独计fixture三次，不沿用旧40下载/80查询总数。使用当前请求样例及第二股票样本；RANGE阶段由已校验case清单和当次实际AVAILABLE集合驱动，精确记录选中/未选中/失败，选中0项不可报告成功。专用 `TENSOR_TUSHARE_LIVE_E2E=1` 保持；新增 `ISSUE018_T13_PHASE=single|range`（缺省single），range要求case清单、对应真实SOURCE及完整性证据齐全。保留retries0、workers1、禁trace/screenshot/video，浏览器环境不含Token/DB；页面只访问回环应用，真实来源由后端访问。禁止page.route伪造任务成功。

应用仍由harness占用空闲8080，DB沿用明确隔离前缀 `tensor_m14_t05_<hex>`，每轮新MySQL8.4.6 schema、初始无表；八迁移/52表，不共用T12已销毁的库。Token仅后端环境，最小schema权限CREATE/SELECT/INSERT/UPDATE/ALTER/INDEX/REFERENCES；T13不制造持久化故障，不需要TRIGGER。为每轮只读核对提供同账号0600 defaults文件，路径变量 `ISSUE018_T13_MYSQL_DEFAULTS_FILE`；校验主机/端口/schema/账号与JDBC一致，不输出值。SQL只对自有schema查询，以YAML业务键读取前后数量、证券归属和稳定摘要；已有证券列与V8任务列分开核对，不能仅依赖任务自报计数。harness的固定迁移数、导航编号、14双标签表头沿T12当前源码，不能复制历史7迁移/50表断言。

能力变化同步 `TushareBatchPoliciesTest`、`TushareBatchAvailabilityTest`、TushareBatchDownload/Calendar测试和 `ui-redesign.fixtures.js`、`tushare-metadata.spec.js` 独立预期。预期必须逐项有证据对应，不能读取生产表直接生成期望来隐藏错配；未开放项继续严格NEEDS_VERIFICATION。ordinary套件继续只用受控来源、T12四隔离库与7文件边界。

## Files

以下是T13实施时的落点，本次准备只创建本设计及next-task交接。

| 路径 | 责任 |
| --- | --- |
| `docs/verification/ISSUE-018-range-acceptance.md`、`.json` | 新40项依据、结果索引、逐轮证据和未决项 |
| `control-plane/e2e/tushare-range-evidence.js`、`tushare-range-evidence.test.js` | 新小型纯校验/安全投影helper及Node测试；不注册Playwright用例，不发网络，不读取Token |
| `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbe.java`、`TushareRangeSourceProbeTest.java` | 显式选择的真实来源探针及零网络输入/脱敏测试 |
| `control-plane/e2e/tushare-live.spec.js` | 现有账户套件迁移task API，SINGLE/RANGE精确选择、来源/SQL证据与安全清理 |
| `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java` | 仅证据成立的逐项规则/日期/版本/验证标记；不增加生产开关 |
| 同包 `TushareTradeCalendar.java` | 仅在已有资料与实测明确支持后修正BJ/BSE相关处理；未决时不改 |
| `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPoliciesTest.java`、`TushareBatchDownloadTest.java`、`TushareTradeCalendarTest.java` | 独立开放矩阵、阈值/映射/日期与旧未知拒绝回归 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java` | 新可用集可接收、未验证集零上游拒绝、摘要变更拒绝旧语义 |
| `control-plane/e2e/ui-redesign.fixtures.js`、`tushare-metadata.spec.js` | 独立逐项能力预期随证据更新，40/34/6与普通harness安全约束不变 |
| `docs/runbook/configuration.md`、`docs/runbook/first-run.md` | 实际开放集、历史限制、计数/完整性和人工操作边界 |
| ISSUE-018/017母issue、验证记录、`docs/issues/README.md`及ISSUE-018看板 | 只按实际新增证据更新状态和未完成事项；保留历史报告 |

不改V1–V8、40份YAML业务列/键、历史manifest或历史截图；不为UNKNOWN创造占位规则。若证据证明需要额外公共接口或键迁移，属于必须明确修订的设计事实，不能在本设计下顺带实现。

## Tests

**第一项实施动作：** 新建 `control-plane/e2e/tushare-range-evidence.test.js`，独立列出上述40项与34/6、31/3/6不变量，先对“漏项”和“UNKNOWN被标AVAILABLE”两个合成结果运行Node测试观察RED，再实现最小校验helper及真实结果索引；此动作无网络，不修改生产Policy。

本地开发检查（从仓库根，项目Node24 PATH、Java21；Maven/npm/浏览器串行）：

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -f data-plane/pom.xml -Dtest=TushareRangeSourceProbeTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=single npm --prefix control-plane run test:e2e -- --list
```

前两条预期退出0且无网络/跳过；缺case、错权限/路径、未知API/字段、双股票、非法日期、证据缺失和秘密投影分别有拒绝测试。list仅发现，single仍精确40项；range数量等于清单中API数、每API内精确case数，并与实际结果逐项对应，不用list当执行证据。

下面三条只在用户明确启动T13、已有授权账户配置及本设计私有输入/频率/隔离环境齐备后执行；本次准备不执行。Token不得出现在命令行。Probe和browser先后运行，不并行占账户配额。

```sh
TENSOR_TUSHARE_LIVE_E2E=1 mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbe -Dsurefire.failIfNoSpecifiedTests=false test
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=single npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js
```

运行前提供上述CASE/EVIDENCE变量、`TENSOR_TUSHARE_TOKEN`、三个TENSOR_DB环境变量、`M14_T05_CALL_INTERVAL_MS`、0700的 `M14_T05_ARTIFACT_DIR`、当前绝对ACCEPTANCE_JAR及实际 `ISSUE_017_ACCEPTANCE_JAR_SHA256`、只读SQL的defaults路径。每次都校验包哈希；重建后旧哈希不能沿用。只接受实际成功/空结果或明确失败，任何缺凭证/权限/完整性/历史样本不能成为通过；安全失败立即停止，保留原失败并清理自有资源。

策略或应用源码变化后按T12相同顺序执行六条当前源码门禁：

```sh
mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js
npm --prefix control-plane run test:e2e
```

均须退出0、无未解释跳过。数量按新增本地测试的实际XML更新，不硬套T12的1282；普通浏览器保持7文件126项，新增场景需记录精确增量且不删旧场景。后两条重建自有preview4173和四新空库/0600映射，依T12设计执行；不能依赖已经清理的临时runner或旧进程。真实账户变量从普通命令环境移除，默认套件不得调用Probe或live。

`git diff --check`、暂存差异检查及输入哈希核对通过，新增文件显式加入Git。完整 `sh scripts/verify-contracts.sh` 仍须main/干净受保护输入/HEAD前置，不自动提交或切main制造满足；未执行须继续明确记录，不称发布通过。

## Acceptance

1. 40项结果索引与本表一致，31原生+3逐日+6SINGLE明确处理；34/6股票规则与业务键不变，不用排除未决接口制造完成。
2. 每个AVAILABLE项有可核验的完整提取规则、真实SOURCE参数/日期轴/边界证据，以及相同候选策略包的真实任务页面/全部叶子/SQL/日志证据。官方网页或少量样例不单独构成通过。
3. 代表场景逐项满足；真实来源语义、T12受控满额拆分/故障证据分开。未知、单点满额、失败或未完成叶子不能显示整段完成，父节点和规划日历不累计证券成功行。
4. 11 UNKNOWN、BJ/BSE、两项标停历史范围和fina_mainbz默认type均有真实处理依据；未解决则保持待验证及任务未完成。ISSUE-017新两股票证据只按实际补记，不自动关闭其其他缺口。
5. 开放集合/版本/证据引用与独立测试、能力HTTP、页面描述和运行手册一致；旧任务遇语义变化拒绝人工重放。相关六条源码门禁及真实任务回归有完整结果、安全投影和清理记录。
6. 只有所有纳入项满足总体设计§6和母issue关闭条件才记录T13 COMPLETED及母issue关闭；否则写明剩余项/依据缺口和继续动作。完成不自动授权提交、合并或发布。

## Risks

- 11项完整性依据、账户现时权限、事件样本和标停历史范围是待取得的外部事实；本设计规定取证与失败行为，没有填造验证结果。新证据若要求新算法须先更新对应设计，不能将UNKNOWN改名为已验证。
- 首轮固定日期可能为空，不能把空样本当日期筛选有效的证明，也不能无限换日期请求；新样本必须登记依据和单独轮次。
- BJ与BSE支持性、fina_mainbz默认类型若仍有歧义，将阻止相应最终验收；不得混类型、替换交易所或省略股票绕过。
- 跨批上游可修订，完整性不等于同一时刻快照。私有凭证/真实金融返回应留在本次受限环境，仓库只存安全证据。
- 当前旧live helper仍基于同步POST，是T13待实施的迁移工作；T12仅验证其明确发现入口。T12发布脚本未运行，本设计不把历史包、旧manifest或T12普通测试冒充真实账户通过。
