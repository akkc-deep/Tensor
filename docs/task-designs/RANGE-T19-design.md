# RANGE-T19：浏览器闭环与现有页面回归设计

## Goal

在正式页面完成区间首次下载、查看已提交数据、失败查询及多次手动重试闭环，并把29项AC的页面适用证据与T18后端证据对应起来。权威为[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的RANGE-T19（Order19，直接依赖T15、T16、T17、T18）。本设计在T18的COMPLETED记录之后编写；设计就绪不代表T19已实施或e2e已通过。

## Scope

- 消费现有正式Vue页面与T18 acceptance JAR／ControlledRangeSource／四脚本，执行真实浏览器→真实HTTP→实际fixture／事务→隔离MySQL闭环。覆盖49表单合同、日期、七计数、原始／当前范围、精确零body重试、切页／刷新／响应丢失及只读查询回归。
- 迁移本轮直接消费的`fixture-flow.spec.js`和`download-outcomes.spec.js`旧断言，新增区间闭环及49合同浏览器测试、最小测试进程桥和运行helper。若验收发现现有UI或测试支持缺陷，保留可复现RED后只修违反既有合同的最小路径，独立复审及相应重跑。
- 不扩大产品功能、API、生产来源／日历能力、业务schema／索引、执行上限、取消、轮询、自动重试、历史或恢复账本；不执行真实Tushare调用。ISSUE-008九项继续“不依赖，未解决”。不实施T20。
- 保存本项新起点的分支／HEAD、索引及任务外文件摘要；58生产资源及fixture YAML/V6保持。新增正式文件加入Git，不提交／发布，不覆盖既有ISSUE工作。

## Approach

### 1. 输入与现状

按[PRD §2、§3、§5.6～§5.8、§9](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD §8、§12](../design/Tensor_区间下载_TRD_v1.0.md)、现有e2e／package／Playwright配置、T15～T18设计与验证报告顺序读取。精确字段仍以OpenAPI及实际DTO为准。 直接设计为[T15](RANGE-T15-design.md)、[T16](RANGE-T16-design.md)、[T17](RANGE-T17-design.md)、[T18](RANGE-T18-design.md)，与下列报告配对消费。

- [T15](../verification/RANGE-T15-form.md)交付49投影、19/15/1/3/11、38迁移／11原条件、真实公历／包含两端31天、原生日期语义、附加条件及首错聚焦。`src/test/fixtures/range-apis.json`是由真实合同生成的前端输入，不是生产来源可执行性证明。
- [T16](../verification/RANGE-T16-results.md)交付18字段严格结果守卫、五种200结果及UNCONFIRMED、七计数／nullable、三组范围、130000ms Axios等待、固定请求上下文、精确taskId入口和无自动重发。
- [T17](../verification/RANGE-T17-retry-page.md)交付两页签、任务列表／17字段详情、独立筛选／20/50/100分页、空body execute、404/409/unknown刷新规则；晚到GET与Element Plus自动回第1页已修复。356项前端和受控页面通过，但此前API为浏览器拦截，不能替代本项真实闭环。
- [T18](../verification/RANGE-T18-controlled-mysql.md)交付五mode runtime fixture、完整分页客户端、父进程来源API及四脚本，真实HTTP27、SQL46、最终JAR进程7和负载3分别通过，17行独立SQL已核对。T18 JAR摘要`626e0e840370b8b44d4f9a280444a1adb88aa9c658a16a78bf99a83d900ca0e0`仅是输入版本；本项重新构建后记录本轮摘要，不硬编码历史包要求。

当前`fixture-flow`仍期待“下载成功”及三计数，`download-outcomes`仍期待旧8字段结果、trade_date请求及真实TusharePlugin旧能力；必须迁移对应验收断言，不能恢复旧产品行为。`tushare-metadata`／`tushare-live`固定历史包／manifest及旧API形状；`dataset-query`、`ui-redesign`为其他历史任务的独立环境／截图工作流。它们不在本项配置选择范围，保留原文件和历史证据，本项另以真实fixture查询及全量单元回归覆盖当前页面。正式报告须列出本次选择的spec，不把未运行历史套件或真实来源标为通过。

### 2. 运行方式与最小测试支持

新增`control-plane/playwright.range.config.js`，继承现有基础use／reporter约定，固定testMatch为四个spec：`fixture-flow.spec.js`、`download-outcomes.spec.js`、`range-download.spec.js`、`range-contracts.spec.js`。workers=1、retries=0、forbidOnly=true；两个项目`range-desktop`（1440×1100）及`range-narrow`（390×844）均为Chromium。baseURL固定`http://127.0.0.1:8080`，输出到忽略目录`node_modules/.cache/tensor-range-playwright`。两个迁移spec移除现有`test.use`中的1440×1000 viewport覆盖，尺寸只来自项目配置，其他trace/video约定可保留；reporter为list及写入本项忽略目录results.json的JSON报告。断言实际window.innerWidth/Height等于项目尺寸。不修改默认配置以悄悄排除历史套件；本项用明确`--config`运行全部四spec、两个项目。

实际页面由本轮acceptance JAR提供静态资源，不用开发服务器冒充最终包。每个spec的serial组持有自己的Java测试桥／容器／应用，组内用显式reset隔离；不同项目及spec串行。8080已占用就报环境错误，不结束用户服务。Node／Playwright使用package已有24.x／1.62.1，不升级依赖。

新增test-only `com.akkc.tensor.fixture.support.RangeBrowserHarness`（位于App test source，不进入任一产品包）：复用现有Testcontainers/JDBC/Jackson和`ControlledRangeSource`，持有一个mysql:8.4.6、一个来源及一个新JAR child。应用使用真实Flyway V1～V8、loopback随机端口、acceptance／fixture.enabled，默认ANN_DATE_RANGE/REQUEST；显式禁用Tushare，移除token，只将本容器凭证注入child环境，不输出凭证。应用以`/actuator/health` UP和`/api/v1/data-sources`实际200就绪，限时90秒；fixture开启时另核对其apis，关闭场景核对列表无fixture。JAR进程关闭只处理该Process并等待实际退出；restart保留来源与DB，突发重启用SIGKILL/`destroyForcibly`；正常结束优先正常退出，超时再清理自有child并记录原因。

测试桥使用stdin逐行JSON命令，stdout只用`RANGE_BROWSER `前缀发响应，其他Java日志由Node保存到本轮证据，禁止经HTTP公开控制。每条命令有`id`、`op`、`args`，应答`{id,ok,result}`或安全`{id,ok:false,errorCode}`；Node匹配id，命令限时，错误不转成成功。固定操作如下，不做通用远程执行器：

| op | 固定输入／结果 |
|---|---|
| start | JAR绝对路径、证据目录及mode/recovery/scriptPath、fixtureEnabled（默认true）；scriptPath=null表示现有本地envelope且child不配置source-url，否则使用来源URL；启动本轮容器／来源／child，返回内部applicationUrl、sourceUrl、PID、JAR摘要，DB凭证不返回 |
| script | 现存脚本绝对路径；用`readScript`＋`replaceScript`，再`clearCalls`开始下一阶段；四T18脚本原文件保持不变，派生脚本只写本轮临时目录 |
| hold / entered / release | hold按apiName、完整params、page=1注册；entered用既有Hold.awaitEntered，45秒；release释放该hold。每个场景新建source，避免对同键重复注册；关闭时释放全部hold |
| snapshot | 返回并落盘source.calls、fixture业务完整有序行、主表全部字段、完整明细键／原因、三表SHOW CREATE／去Cardinality的SHOW INDEX；用独立原始DataSource连接，不经业务repository |
| reset | 仅本容器中两失败表和fixture表的测试数据，移除本桥已登记trigger，关闭／重建来源后总是重启child以绑定新的source-url及mode/recovery，实际就绪后才接受浏览器动作。不得清理其他schema／服务 |
| seed | 只接受§5定义的固定fixture样例名，使用参数化SQL及当前实际DDL，不接收任意SQL；返回新建taskId或行数 |
| fault / clearFault | 仅`save-first`（目标DATE明细INSERT）或`delete-item`（目标完整明细键DELETE）的SQLSTATE45000 trigger；登记确切名称，clearFault/finally移除；不关约束 |
| restart | 正常/突发两种；只重启自有child，mode/recovery及fixtureEnabled保持或按下一独立场景明确设置；保留指定当前DB及来源阶段；返回新PID并等实际HTTP就绪 |
| stop | 先释放hold，再关闭来源、自有child和本容器；所有清理放finally，记录实际退出；stdin EOF亦清理 |

命令字段固定：start/reset的args为`{jarPath,evidenceDir,mode,recovery,scriptPath,fixtureEnabled}`（reset沿用jarPath/evidenceDir）；script为`{scriptPath}`；hold为`{apiName,params,page}`并返回`{holdId}`，entered/release为`{holdId}`；snapshot为`{name}`；seed为`{name}`并统一返回`{taskIds:[],businessRows}`；fault为`{kind,taskId,selector}`，save-first省略taskId、selector固定REQUEST DATE目标日，delete-item必须原UUID及完整四选择器；clearFault/stop为`{}`；restart为`{force,mode,recovery,fixtureEnabled}`，未传配置沿用。start/reset/restart统一返回`{applicationUrl,sourceUrl,pid,jarSha256}`；snapshot返回`{sourceCalls,business,tasks,items,schema,artifactPath}`，普通无数据操作返回`{done:true}`。Node未收到对应id不能推进步骤。正常关闭限时150秒，start/restart/stop命令分别120/180/180秒，其余60秒（entered内部45秒）；测试与hooks用180秒，包含多次重启的完整主线360秒。这些是测试失败期限，不改变产品上限。

新增Node helper `control-plane/e2e/support/range-runtime.js`：spawn测试桥并匹配协议，提供`start/command/snapshot/stop`、现有页面选择器及请求捕获小函数。不复制分页收集逻辑。Java桥直接调用T18已有source API，满足来源精确调用／hold需求；独立main两参数协议不改。

该helper在8080提供一个透明loopback反向代理，转发到桥返回的JAR端口，仅记录浏览器method/path/query/body长度、X-Request-Id和安全响应。正常测试不得修改业务请求或响应。仅失去响应场景由helper保存某一已转发POST的下游连接，在source entered后主动关闭该下游；上游继续读取至真实完成。独立context的GET仍可经过代理，服务端来源／SQL证明执行继续。此证据准确称“浏览器到代理的真实连接丢失”；JAR直接socket RST/read-timeout的边界引用T18，不混称同一连接。无需修改产品130000ms timeout或等待130秒制造错误。

### 3. 真实后端场景分组

`fixture-flow`保留三个原意：ORIGINAL_PARAMS默认SUCCESS→真实查询、EMPTY不改旧行、fixture disabled重启后两个页面不再列出fixture。迁移为当前18字段、七计数、“下载完成”文案；Tushare保持disabled。原场景值不改，禁止因旧断言失败回退三计数。

`download-outcomes`改为当前fixture结果／故障矩阵，删除本文件内不再被场景使用的旧Tushare stub及旧日志断言，不改历史其他spec。每个小场景用bridge reset：

- ANN REQUEST全成功、全EMPTY，以及空／失败／成功三日期的PARTIAL（S2/F1、R/I=1，空日无占位）；TRADE混合开闭与全闭、缺日历：已有失败项场景先用完整开市日历和来源失败建立任务，再切全闭日历验证删除原项／旧业务保持，另切缺日历验证原项保持与业务调用0；完整MONTH跨年／31天跨三月及08/15～09/03仅9月失败、显式retry整月202609并保持原始日期；NATIVE同日起止。派生source规则必须匹配实际source params及calendar的原公共条件／完整日期列表；ANN／MONTH／NATIVE无日历调用。
- ANN STOCK_TIME同一天A=000001.SZ、B=000002.SZ、C=000003.SZ，B amount="bad"、A/C各一行，完整终页证明；UI S/F=2/1、R/I/U=2/2/0，SQL仅A/C及B失败。
- 同批第1页有效、第2页SOURCE_RATE_LIMITED，以及终页complete=false：UI显示精确REQUEST失败，SQL业务0，不显示从局部响应猜出的单股失败。
- 至少三天依次SOURCE_TIMEOUT、SOURCE_RATE_LIMITED、成功，UI F2/S1；逐项来源已调用，随后显式retry仍按当前两条，不自动重发。
- 实际初次09/01业务成功、09/02来源失败但save-first trigger拒绝，09/03未开始：真实500 TASK_RECORD_SAVE_UNCONFIRMED、UNCONFIRMED、确认S1/I1，remaining=null，无虚构taskId，后续来源0；移除trigger仅为清理，不自动重试。
- 原任务delete-item trigger拒绝：旧业务及明细保持，本轮失败计数／安全原因可读；clearFault后用户主动一次retry成功。深层多SQL组／commit未知组合以T18后端证据为准，本项不复制故障框架。

`range-download`的主线按两个尺寸完整跑：

1. 使用T18 initial脚本，页面选择fixture ANN REQUEST，09/01～10，键盘提交一次；捕获compact start/end及scenario=SUCCESS、一次POST、响应头／body同RequestId；来源10次，SQL业务8／主表1／明细3、7两项。结果S8/F2、I8，点击“查看重试任务”只GET，URL仅tab/taskId。
2. 列表／详情显示原始1～10及当前3、7，公共scenario，创建／更新时间。刷新页面及自有child重启后仅GET，仍相同记录；没有历史S8或新下载。保存原task_params/created_at比较基线。
3. 切partial脚本，点击“重试一次”无确认弹窗，实际POST `/retry-tasks/{id}/execute`零字节、无query、原UUID；只取3/7，原始区间不改，业务9／明细仅7。再次失败仍只取7，不改原始JSON/created_at。
4. 切success脚本再主动retry，只取7；业务10、两表0，列表“暂无失败任务”，原URL的显式GET404显示“任务不存在或已无剩余失败记录”，不声称丢失响应必定成功。不得通过GET自动执行。
5. 独立STOCK_TIME场景用T18 two-stocks→partial→success：同日两股2→1→0明细，0→1→2业务；两个完整选择器独立显示，失败原因刷新不合并，retry参数必须带对应ts_code。
6. 初次日5hold：entered后检查锁定及固定不可终止说明、无取消／暂停／进度；切页签、设置、数据查看再返回仍同一POST。另一context在占用前已加载可提交表单／可执行任务详情，占用后从该旧页面各主动一次首次／execute验证真实409，GET列表／详情可读，不增加source；释放后实际完成再解锁。
7. 初次用initial脚本并hold09/05；最后retry先按initial→partial建立仅09/07，再切success并hold09/07；两者均在source entered后由代理关闭浏览器响应连接。原页面显示“结果未确认”，无伪造计数／任务；不自动再POST。仍hold时另一context真实写入409；释放后独立SQL等实际完成。重试最后项已删时刷新404，不重插；刷新／重开页面只读当前实际记录。不能用API route假500代替这个场景。
8. 测试桥突发重启：初次day5 hold时已保存day3，强杀后新PID就绪、重新打开失败页只见3，未开始日无预登记；切success脚本后显式retry只取3并解决该项。这里只重新验证页面连接真实重启后的存储读法，详细进程边界引用T18同名SQL／事件。

### 4. 49合同及只需前端展示的分支

`range-contracts.spec.js`在同一新JAR页面上使用明确局部的Playwright route响应：只在此spec替换data-sources/apis/downloads/retry-tasks，为每个响应保留当前请求ID和当前精确合同。不得用于§3主线，不把拦截的200当真实来源或SQL证据。

- 从现有`range-apis.json`读取49项；按其真实参数顺序逐项选择API：两端固定2026-09-01/02；股票输入` 000001.sz `；枚举优先元数据defaultValue否则allowedValues首项；其他字段沿现有RangeDownloadForm.spec.js的同一明确填值表，捕获一次提交的精确keys/value（日期compact、去空白股票、枚举保留）；11原条件无两端，零参数{}合法。提交前清旧结果；weekly/monthly及三个原生语义文案、未来日期无额外禁限。合成应答只用于释放页面状态。
- 日期代表：缺失、非法2026-02-29、逆序、同日、2028闰日、跨年、01/31～03/02为31天三个月、延长一天32拒绝；键盘首错聚焦、零POST。income保留必填股票、margin／trade_cal保留市场条件；38项不提交旧trade_date/ann_date/month，混用服务端400由T18 HTTP引用。
- 结果代表：五种200 outcome、四停止码的真实T18形状快照、N/remaining=null与0、F不同于当前剩余、长范围/错误、HTML普通文本。复用现有`range-results.json`／`retry-tasks.json`并按本次requestId调整，不重造另一份字段合同。仅此spec覆盖难以从JAR稳定注入的COMMIT_UNCONFIRMED等，报告明确前端展示与T18 SQL组合证据。
- 固定旧taskParams非法／离线身份blocker、UNKNOWN形状安全拒绝的展示、GET404/409及刷新权限，真后端可构造的部分另在§5实测。不得根据错误retryable推导canExecute，不能把null替成输入taskId。

### 5. 当前任务列表与只读查询

桥的seed只创建以下明确定义样例，三表snapshot记录写入前后，DB CHECK始终启用：

- `old-range`：当前ANN fixture，task_params仅scenario=SUCCESS，REQUEST DATE=2026-09-07；页面原始为“未记录”，能按明细retry，不能从7推算起止。
- `original`：独立ORIGINAL_PARAMS fixture，task_params scenario=SUCCESS，REQUEST NONE/空值；“不适用”，零body按原条件retry。
- `invalid-range`：ANN fixture仅start_date=20260901而无end_date，合法JSON对象；GET可读且不可执行，原始“未确认”，来源0，原记录保留。`missing-plugin`为保存pluginId=offline_fixture的合法任务，列表按保存标识可筛选／可读、不可执行，禁止切换来源。
- `task-pages`：41个独立合法UUID任务、相同scenario与09/01～10原始区间、各REQUEST DATE=09/07；固定递增UTC毫秒时间确定排序，error_code为SOURCE_UNAVAILABLE，可预置error_message=512字符含转义样例，但REAL GET通过`RetryTaskResponse.Item.from`按errorCode调用`safeReason`，页面必须只见固定安全文案，不能将持久化原文当页面内容。512字符长原因的显示／转义只由§4 FRONTEND_CONTRACT合法详情响应覆盖。验证默认20页、page2刷新仍page2、延迟GET后page3无额外page1、50/100、独立插件／API筛选、当前页执行后以真实GET更新列表。这组REAL预置同时证明存储原文不会被直接投影到页面，不升级来源错误正文的存储／显示合同。
- `query-values`：fixture表61个合法不同业务键，包含相同000001.SZ在2026-01-01起61日的行；首行amount精确`12345678901234567890.123456789012345678`，另一行大整数形式amount，note分别null及<=255字符长转义文本，真实provenance三列和UTC时间。该预置只是查询样例，不是超过31天的一次下载。查询GET默认50、下一页11、切20、空筛选结果、清除／刷新、逐行单元格全文／现有tooltip／横向表内滚动、null显示--及北京时间；数字逐字比对HTTP字符串／原始SQL，禁止转JS Number。

列表／查询的已提交原行在合法空和失败retry后仍保留。所有只读页面动作捕获method为GET，没有隐式下载POST。元数据切换、旧queryMode细节和主题回归由全量单测及此处真实页面代表覆盖，不重写既有公共查询组件。

### 6. 29项页面证据映射

正式报告逐AC列出用例名、证据层（REAL或FRONTEND_CONTRACT）、请求／截图及独立SQL或T18具体行号；不可只写“全覆盖”。下表固定期望，不是已执行结果。

| AC | 本项观察与后端依据 |
|---:|---|
| 01 | 49实际表单形状／19/15/1/3/11；前端合同层，生产真实支持保持未验证 |
| 02 | TRADE开闭场景UI H/S、来源精确日期／SQL；T18矩阵1/11 |
| 03 | income股票、margin市场精确POST／任务公共条件展示；T18宽表与参数合同 |
| 04 | 31天三月／跨年摘要、MONTH原始与失败月分开；真实month来源／SQL，T18行17 |
| 05 | 同日TRADE/ANN与合法空、零占位；T18行11 |
| 06 | 缺失／非法／逆序／31/32与首错聚焦、零POST；T18前置拒绝SQL |
| 07 | 空与失败混合PARTIAL、全空EMPTY、旧业务不删；真实结果／查询／SQL |
| 08 | ABC的S2/F1、仅B失败；真实STOCK来源／三表 |
| 09 | 同轮冲突错误与确认计数展示；T18 BatchCommit/processor证据，前端合同层不伪造新SQL事务证明 |
| 10 | 连续来源错误后成功，全部实际来源序列；T18行8 |
| 11 | 页2失败／终页不完整显示REQUEST，真实业务0；T18行5 |
| 12 | 两次来源失败后续成功、手动retry仍遍历剩余；真实主线及T18九码证据 |
| 13 | 七计数／null／本轮与当前剩余分离；ABC真实，停止未知组合T18行13 |
| 14 | REQUEST稀疏范围、原条件NONE；真实重试及T18完整RANGE |
| 15 | 全闭标题与EMPTY区分、S0/H、失败项删除／旧业务保持；T18行11 |
| 16 | 页面不把停牌写为休市，H独立；多市场算法／真实SQL依据T18明确日历两用例，不宣称本项真实市场数据 |
| 17 | 日历缺日未开始文案、首次无task／已有项保留；真实GET／SQL |
| 18 | ANN、MONTH、NATIVE各语义与无calendar；三个原生字段前端合同＋T18行15 |
| 19 | 38新形状／11保留，股票市场不丢；旧混用拒绝引用T18参数／HTTP |
| 20 | 切页／刷新、旧结果清除、真实丢响应无重发，不推测成功 |
| 21 | two-stocks三阶段完整键、零body同UUID、SQL2→1→0 |
| 22 | REQUEST1～10／3、7→7与restart后GET、原JSON/created_at不变 |
| 23 | 实际并发409／过期404，双击仅一次POST，刷新权限恢复无自动execute |
| 24 | 真实delete-item失败回滚／显式恢复、结果与旧业务保持；多组/主表故障引用T18行4 |
| 25 | 实际invalid-range／offline任务保留、标识回退、禁execute；其他坏结构前端合同＋T18行12 |
| 26 | 实际save-first停止／null／确认小计；commit未知前端合同＋T18行9/13 |
| 27 | 所有开始及retry中无取消／暂停／停止／百分比，真实锁定到执行结束 |
| 28 | 真实浏览器连接丢失后执行继续与409；客户端timeout文案单测／合同，JAR直接socket及读取超时引用T18行14 |
| 29 | 缺股不猜失败、完整归属失败REQUEST的页面范围；真实ABC/分页及T18行5/6/7 |

### 7. 证据、视觉与完成门槛

每个真实场景开始／关键阶段／清理前保存三表schema和完整SQL、source实际params/page序、浏览器request/response、主task JSON/created_at、child PID和JAR摘要。snapshot不回流产品补计数。失败也保存已得到的证据，原异常保留；报错或缺环境不能skip或只重试到绿而丢弃失败。

每个项目保留原始区间／剩3/7、仅7、最后解决、同日两股、长原因、unknown、409、日期错误、精确查询及分页代表截图；记录浏览器版本、viewport、index及assets SHA。用served响应摘要对比本轮JAR中的static资源，同时记录源码摘要，截图必须属于该最终构建。人工检查桌面和窄屏代表图、文档无非预期横向溢出，查询宽表只在表容器滚动；键盘能选择日期、提交、进入任务、筛选分页、刷新与retry，focus可见，长文本转义且可读。保存非预期console/pageerror列表并要求为空，预期网络断连／404/409/500单独按精确请求列出，不能静默忽略全部错误。

本项使用superpowers既定规格／质量与最终集成／证据独立评审；所有Important/Critical关闭、最终源码及构建一致、全配置suite无skip且29行证据明确后记录COMPLETED。之后仅按Order准备T20专属设计／交接；T20所需实际权限／来源事实若缺失，按工作流保留T19完成而不伪造T20就绪。

## Files

| 路径 | 责任 |
|---|---|
| `control-plane/playwright.range.config.js`（新） | 固定本项四spec／双尺寸／serial／零重试配置，不改历史默认范围 |
| `control-plane/e2e/support/range-runtime.js`（新） | 测试桥协议、透明proxy／指定下游断连、页面和证据小helper |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/support/RangeBrowserHarness.java`（新） | 持有隔离MySQL／现有source／实际JAR，自有进程管理、精确hold及独立SQL／预置／两个trigger |
| `control-plane/e2e/fixture-flow.spec.js`、`download-outcomes.spec.js` | 迁移旧断言和当前受控后端矩阵，复用新helper |
| `control-plane/e2e/range-download.spec.js`、`range-contracts.spec.js`（新） | 真实闭环及明确分层的49合同／展示验证 |
| `control-plane/src/test/fixtures/range-apis.json`、`range-results.json`、`retry-tasks.json` | 只读取现有真实合同fixture；无实证合同缺陷不改 |
| `docs/verification/RANGE-T19-browser.md`（新）、`docs/traceability/tensor-range-requirements.md` | 最终命令／逐AC／图片／SQL／构建及未验证层次 |
| 本看板／交接索引、按流程的T20设计与交接 | 完成及后继准备，只在本项完成后进行 |

不预授权后端Core／App生产实现、生产资源或其他issue改动。UI若有真实RED，报告列出新增最小实际路径；对现有逻辑的变更遵守先定位／回归／独立复审，而不是修改断言隐藏失败。

## Tests

开始先保存本轮基线、完整读设计和交接、记录READY→IN_PROGRESS；先运行现有前端单元基线。第一项实施动作是迁移`fixture-flow`为当前七计数／18字段的真实页面断言，并建立本设计固定的最小测试桥和helper，先跑其SUCCESS→真实查询小闭环。旧脚本不匹配当前结果的失败只记为验收合同迁移证据，不声称产品缺陷RED；若新断言发现实际UI缺陷，先保留该行为RED再最小修复。缺classpath／导入／编译错误单列，不作为行为RED。逐场景加入本设计其余验收。

最终命令按顺序，Maven串行。根目录：

```sh
npm --prefix control-plane test
npm --prefix control-plane run build
mvn -f data-plane/pom.xml -Pacceptance clean verify

env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RangeFixtureHttpIT -Dsurefire.failIfNoSpecifiedTests=false test
```

最后一条是真实27项T18 HTTP回归，并让Surefire实际classpath写入`tensor-app/target/range-acceptance/test-classpath.txt`、编译新test-only桥；不用猜Maven依赖路径。若桥改动导致编译失败先修正，不能用skip通过。然后在根目录：

```sh
env -u TENSOR_TUSHARE_TOKEN \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  ACCEPTANCE_JAR="$PWD/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar" \
  RANGE_TEST_CLASSPATH_FILE="$PWD/data-plane/tensor-app/target/range-acceptance/test-classpath.txt" \
  npm --prefix control-plane run test:e2e -- --config playwright.range.config.js

PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

`test:e2e`此次配置必须四spec、两项目均真实出现，全部tests>0、failed/skipped=0；49项逐项枚举，不能以描述标题代替数量。测试桥由helper按实际classpath以`java -cp <classpath> com.akkc.tensor.fixture.support.RangeBrowserHarness`启动。Docker不可用、8080占用或浏览器未安装均先解决环境／记录阻塞，不能跳过本轮套件。CI／本地不自动跑真实来源或T20；配置选择范围在报告中公开。

clean构建之后，运行production／fixture及前端源码若再改，必须重新前端test/build＋acceptance clean verify＋bootstrap HTTP，再跑受影响e2e和完整本项配置；只有测试证据代码修改可定向复跑，不要求重建未变JAR，但必须核对摘要。记录实际数量，不将依赖356/894或T18历史数量直接填作本项结果。

## Acceptance

1. 本轮最终JAR真实供页，四spec／两个尺寸的明确配置test:e2e、前端全量单测及build、acceptance构建／27HTTP回归通过，无目标缺失或skip。
2. 真实主线1～10／3、7→7→无、同日两股、零body原UUID、原JSON及created_at、实际source序列与三表SQL完全对应；没有前端模拟业务成功替代主线。
3. 真实页面的切页／刷新／重启／连接丢失与409/404、无取消／自动重提成立；未确认不伪造计数或任务，不将查不到任务等同某次成功。
4. 49形状、日期边界、七计数／nullable、长文本／键盘／双尺寸，以及实际数据查看精确小数／null／分页可用；所有29项页面适用与T18后端证据逐项可定位，层次与未验范围明确。
5. 原始日志／SQL／source／截图／最终source与JAR/served资源摘要齐全；58生产资源、fixture schema及原索引／任务外保护通过，新文件纳入Git，不提交／发布，独立最终评审无重要遗留。
6. 仅在本项完成记录之后准备按Order选出的T20设计及交接，不自动实施真实来源；缺少T20必要输入时依流程保持已完成T19，不能以合成证据补造来源就绪。

## Risks

- 真实fixture场景和49前端metadata拦截分属不同证据层；源能力仍受生产保守策略约束，不允许为通过浏览器而启用真实Tushare或更改日历／取全注册表。
- 历史e2e含旧合同、独立凭证格式和固定包摘要；本项明确配置避免混用，未选历史套件不计通过，直接消费的两文件必须迁移。
- 浏览器网络代理只注入本轮下游连接丢失，不代表JAR连接被RST；后者引用T18实际socket证据，报告不得混淆。测试桥／trigger均留test source及隔离DB，不进产品包。
- 长文本样例受实际VARCHAR512/255及JSON/selector CHECK限制；不关闭约束或声称不可能的非法JSON已真实入库。
- 当前没有待决定的产品需求；实际环境可用性和验收结果待本项执行后确定。T20账号权限和来源代表范围不在本设计内猜定。
