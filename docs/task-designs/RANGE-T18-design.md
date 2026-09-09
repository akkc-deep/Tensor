# RANGE-T18：受控来源与 MySQL 故障集成验收设计

## Goal

用可运行的 acceptance fixture、真实 HTTP／子进程及独立 MySQL 核对，证明首次下载、精确重试和故障停止的实际边界。逐项交付 TRD §12.3 的 17 组证据，供 T19 浏览器闭环直接使用受控后端。

权威为 [区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md) 的 RANGE-T18（Order 18，直接依赖 T12、T13、T14）。按 [PRD §9](../design/Tensor_区间下载_PRD_v1.0.md)、[TRD §9～§12](../design/Tensor_区间下载_TRD_v1.0.md)、现有 fixture／集成测试、[T12](../verification/RANGE-T12-initial-execution.md)、[T13](../verification/RANGE-T13-exact-retry.md)、[T14](../verification/RANGE-T14-http.md) 的顺序消费来源。本设计在 T17 COMPLETED 记录之后编写；设计就绪不表示 T18 已启动或下述测试已经运行。

## Scope

- 扩展现有 `fixture/fixture_daily` 的 acceptance 运行能力：五种下载模式、完整分页、局部股票失败、日历及可重复故障场景。保留旧五种 scenario 和原条件默认行为。
- 通过真实服务、适配器、事务与 Web 映射，覆盖多批、多股、多页、SQL 分组／明细删除失败、保存失败、提交未知、重启和断连。数据库为隔离 MySQL 8.4.6，运行真实 Flyway 迁移，直接核对业务表及两张失败表。
- 覆盖 AC-PRD-RANGE-02～18、21～29 的后端适用部分及 49 项参数合同；安全检查及三类负载测量。记录本轮运行与依赖历史证据的区别。
- 不增加真实数据源或 API、业务表／失败表字段与索引、产品端点、自动重试、取消、执行上限、持久执行状态、历史／账本或来源启用项。不实施 T19 页面验收、T20 真实来源验证；ISSUE-008 九项真实调用继续不依赖、未解决。
- 保护开始时分支、HEAD、原索引及任务外文件。49 份生产 Dataset YAML、两份生产来源／日历能力表及七份生产迁移共 58 份资源保持摘要；fixture 的既有 V6 和四业务列也不改。新增正式文件加入 Git，不提交／发布。

## Approach

### 1. 验收分层及入口

保留 `FixtureFlowIT` 的 plugin → adapter → persistence/query 旧回归；它不经过 HTTP，不能替代新入口验收。T14 `DownloadControllerIT` 的正向 fixture 包装只补了测试方法，未交付 runtime 批次能力：本项须让真实 `FixturePlugin` 实现 `planBatch`／`fetchBatch`，有意识替换“未包装 fixture 新 HTTP 安全拒绝”的旧断言，并去掉正向 fixture 包装。其他用于拒绝边界的测试插件保留。

新增四类验收，不构建通用故障框架：

1. `RangeFixtureHttpIT`：真实随机端口 Spring Boot、实际 fixture bean、生产 mapper／controller／service／repository、MySQL；覆盖来源、日期、计数及列表／详情／execute。
2. `RangeFailureMySqlIT`：使用已有 T12／T13 的服务装配和局部 DataSource 探针，验证 SQL 故障及少量真实定义的受控策略；不替换事务／存储为 mock。
3. `RangeProcessIT`：启动本轮 acceptance JAR 子 JVM，真实 HTTP socket、父进程控制的来源服务与独立 MySQL；验证进程退出／重启和初次／重试断连。
4. `RangeLoadIT`：实际入库的多日多页、单日高量及 85 列 income 宽表测量，记录计数、内存与耗时，不设置产品执行上限。

依赖的单元／旧 IT 继续运行，但本轮 17 组均须有对应实际 SQL 核对；不能仅链接旧报告或把默认 Maven verify 误记为显式 MySQL 验收。

### 2. Acceptance fixture 的最小运行合同

仍只由 `@Profile("acceptance")` 且 `tensor.plugins.fixture.enabled=true` 注册。新增启动属性，不作为下载参数或产品配置入口：

| 属性（前缀 `tensor.plugins.fixture`） | 值与行为 |
|---|---|
| `mode` | 默认 `ORIGINAL_PARAMS`；另支持 `TRADE_DATE_RANGE`、`ANN_DATE_RANGE`、`MONTH_RANGE`、`NATIVE_RANGE`；进程运行中不改模式 |
| `recovery` | 默认 `REQUEST`；仅两个 DATE 模式可选 `STOCK_TIME`，其他组合启动拒绝 |
| `source-url` | 默认空，走既有本地 envelope factory；非空只能为 `http://127.0.0.1:<port>`，无 userinfo／query／fragment／路径，不重定向。仅 acceptance 受控来源，不接真实账号 |

模式定义由 `FixtureConfiguration` 创建一次，plugin 与 adapter 共享同一 `DatasetDefinition`。`ApiDescriptor.sourceParameters()` 必须与 `adapter.definition().parameters()` 完全相等，公开日期参数使用现有 `DownloadParameterProjection.project`。不只改单侧 descriptor，也不重写 `SourceParameterMapper`。默认 ORIGINAL 定义与旧 YAML／五种 scenario 完全相同。

| 模式 | 来源参数（均另保留必填 scenario） | 策略／存储语义 |
|---|---|---|
| ORIGINAL_PARAMS | 原五值 scenario，不加日期／股票参数 | NONE、ORIGINAL_PARAMS、REQUEST |
| TRADE_DATE_RANGE | `trade_date: DATE` 必填，`ts_code: TS_CODE` 可选 | TRADE_DATE、DATE、SINGLE_DATE；C-A 受控日历 |
| ANN_DATE_RANGE | `ann_date: DATE` 必填，`ts_code: TS_CODE` 可选 | ANN_DATE、DATE、SINGLE_DATE；不使用日历 |
| MONTH_RANGE | `month: MONTH` 必填 | COVERED_MONTH、MONTH、SINGLE_MONTH、REQUEST |
| NATIVE_RANGE | `start_date/end_date: DATE_RANGE_MEMBER` 必填 | CALENDAR_DATE、RANGE、SOURCE_RANGE、REQUEST |

四区间模式 limits=31；requestEvidenceStatus=`DOCUMENTED_CANDIDATE`，证据只指向 fixture 测试／报告。`CompletenessStatus` 现有唯一值为 `UNCONFIRMED`，不得发明 VERIFIED 或将受控证明写入生产注册表；取全的运行依据由 fixture 实际分页合同保证。TRADE 的 calendarEvidenceStatus 为 DOCUMENTED，明确仅 fixture。DATE 的 STOCK_TIME 使用 `ts_code`、`trade_date`、DATE、independentRecoveryVerified=true；ANN 的受控行把公告日期放入 fixture 的既有 `trade_date` 列，这是 fixture 映射，不改变真实接口的日期语义。

保留 `FixturePlugin(DatasetDefinition, FixtureEnvelopeFactory)` 公开构造器及默认下载行为；新增带受控选项的包内构造器供配置／测试使用，无需扩大产品公共构造 API。旧 `download` 仍按原 scenario 返回；`planBatch` 核对 API／参数／策略并返回上述固定规划，`fetchBatch` 使用收到的精确 `FetchBatch.sourceParams()`，不从原始 task_params 再规划。每个来源请求前后调用 `DownloadContext.checkServerState()`，不把客户端取消信号引入上下文。

无 source-url 时 ORIGINAL 完全复用旧 envelope factory；四区间模式复用五 scenario 的字段／错误规则，根据已映射来源参数生成合法行：DATE 为对应日期，MONTH 为该月首日，RANGE 为每个自然日一行；股票默认 `000001.SZ` 或指定 ts_code。SUCCESS／EMPTY／SOURCE_FAILURE／TYPE_FAILURE／PERSISTENCE_FAILURE 的意义不变。本地 TRADE 日历将所请求日期全部明确判开市，文本标明受控；休市／缺日／日历失败由下面来源脚本测试，不声称真实交易日。

### 3. 受控分页来源及 T19 可复用入口

新增仅由 acceptance fixture 包提供的 `public FixtureBatchSource(URI sourceUrl, DatasetDefinition definition)`，固定公开方法 `FetchResult fetch(FetchBatch batch, DownloadContext context)` 和 `CalendarDecision confirmCalendar(CalendarScope scope, DownloadContext context)`。构造时冻结definition的pluginId/apiName和有序fields、校验loopback URL；使用 JDK HttpClient 和已有 Jackson 类型（fixture pom 显式声明 jackson-databind，版本沿父管理）。runtime只用fixture definition构造，测试内受控插件可传income／fina_audit／daily definition复用同一分页收集器，不注册其他产品API、不复制分页逻辑；无source-url时不构造此客户端，本地range响应留FixturePlugin私有方法。不新增依赖版本、生产 connector 或测试控制 HTTP 端点。连接超时 5 秒，单页请求超时 120 秒；不自动重试。使用 JDK HttpServer 的 test-support `ControlledRangeSource` 只监听 127.0.0.1、随机端口，父进程持有它，child 重启不丢场景与调用记录。

固定内部协议，不作为 Tensor OpenAPI：

- `POST /batch` body 为 `{ "apiName":"fixture_daily", "params":{...全部精确来源参数...}, "page":1 }`；apiName实际取构造definition的值，runtime为fixture_daily，宽表测试为income。page 从 1 开始，同一批后页 apiName／params 必须不变；成功envelope身份取冻结的definition，不采信来源自报身份。
- 成功页字段为 `fields`（有序列名）、`data`（二维数组）、`totalRows`（本批数据总行数）、`nextPage`（下一个连续页码或 null）、`complete`（boolean）、`unitFailures`（仅终页允许，默认空）。每页 JSON 完整读取后验证结构；字段／totalRows 跨页不变、nextPage 只能当前+1、累计行数不能超过 totalRows。终页必须 nextPage=null、complete=true 且累计行数=totalRows；中间页 complete=false。零行也必须明确终页完整。
- `unitFailures` 使用现有 `FetchResult.UnitFailure` 的 selector 四字段及 errorCode／errorMessage；仅已完整终页、已核实范围的成员可发布，并交给现有 processor 校验重叠／归属。正常数据不要求虚构逐股状态，也不从未出现的股票推断失败。
- 所有页确认前只缓存在本批内存，不发布任何 envelope／恢复单元。后页失败、截断、未确认终页或归属错误不得提交前页数据。此 fixture 对缺失页没有独立的完整成员目录，统一保存精确 REQUEST 批次；完整响应的显式 UnitFailure 才可按已证明的 STOCK 保存。
- 失败页可为 `{ "errorCode":"SOURCE_RATE_LIMITED" }`。仅接受九类 `SOURCE_AUTH_FAILED/PERMISSION_DENIED/RATE_LIMITED/UNAVAILABLE/NETWORK_ERROR/TIMEOUT/PAYLOAD_INVALID/TRUNCATED/COMPLETENESS_UNCONFIRMED`（均带 SOURCE_ 前缀）；固定安全消息，不回显响应、URL 或异常原文。HTTP 401/403/429 分别映射认证／权限／限流，5xx→UNAVAILABLE，I/O→NETWORK_ERROR，超时→TIMEOUT，JSON／字段结构错误→PAYLOAD_INVALID，分页总数／截断→TRUNCATED，终页未证明完整→COMPLETENESS_UNCONFIRMED。其余非成功 HTTP→UNAVAILABLE。
- `POST /calendar` body 为 `{ "apiName":"fixture_daily", "params":{...公共条件...}, "dates":["2026-09-01",...] }`，apiName同样来自definition；响应 `{ "calendars":{ "fixture":{"2026-09-01":true,...} } }`，每份日历覆盖且仅覆盖 scope 全部日期，使用现有 `CalendarDecision`。缺日／非法值／错误均 CALENDAR_UNCONFIRMED，业务批次调用为零。

`ControlledRangeSource` 提供测试中的 `replaceScript`、只读调用记录、按 `(apiName,params,page)` 的 entered／release 事件。脚本为 JSON：`batchRules` 每项含精确 `apiName`、`params`、`page` 和 `response`（上述 JSON）或 `errorCode`；同一 phase 不允许重复键；找不到规则返回安全 SOURCE_UNAVAILABLE。`calendarRules`每项按apiName／params／dates精确匹配，保存上述calendar响应或errorCode。测试内 hold 使用 latch；服务关闭释放所有 hold，超时使测试失败，不生成业务取消结果。半个 JSON 后关闭 socket、非2xx、延迟响应由测试内 handler 注入，不给脚本增加通用表达式语言。

其 `main(scriptPath, port)` 可独立启动相同来源：每次请求读取本地脚本快照，测试／T19 用同目录临时文件原子替换脚本切换阶段，不加控制端点、轮询、公共参数或持久执行历史。交付 `range-browser-initial.json`（1～10 日中的3／7失败）、`range-browser-partial.json`（3成功／7仍失败）、`range-browser-success.json`、`range-browser-two-stocks.json` 四份完整脚本；每个实际计划日期均有规则。本轮 IT 验证这些文件，输出测试 JVM 实际完整 classpath 到 `tensor-app/target/range-acceptance/test-classpath.txt`，供下游启动相同 main，不能让 T19 再实现批次能力。

四脚本固定公共scenario=SUCCESS：initial／partial／success均含20260901～10的精确ann_date规则；two-stocks在20260901返回000001.SZ及000002.SZ的两个字段错误单元。partial额外包含这两股的精确ts_code+ann_date规则，A成功、B仍失败；success包含两股各自成功规则。因STOCK重试会增加ts_code，不能用无股票的日期规则冒充匹配；文件覆盖“首次→部分→最后解决”两套序列，并由IT直接读取验证。

下游来源启动形态：

```sh
java -cp "$(cat data-plane/tensor-app/target/range-acceptance/test-classpath.txt)" com.akkc.tensor.fixture.support.ControlledRangeSource /tmp/tensor-range-browser/scenario.json 4189
```

报告交付已实际验证的 acceptance JAR 启动命令，配置 acceptance、fixture.enabled=true、mode=ANN_DATE_RANGE、recovery=REQUEST、source-url=http://127.0.0.1:4189、Tushare关闭及独立数据库；不写入实际账号秘密。T19 的同日两股阶段改为 STOCK_TIME 并重启独立进程，旧数据按用例隔离。

### 4. 数据库及故障注入

真实 Flyway 加载生产 V1～V5／V7／V8 和既有 test V6，不复制 DDL、不用 H2，不允许 disabledWithoutDocker／skip 把未执行计成通过。每组独立清理仅本轮 container 的表；业务预置／SQL注入／独立核对使用原始 DataSource 的新连接，不经过故障代理或业务 repository。

每个阶段至少保存：来源实际参数／页序，安全 HTTP 快照，按完整业务键排序的业务行，主表 task_id/plugin_id/api_name/task_params/created_at/updated_at，明细完整五字段主键及原因。独立查询两表，不以 GET 或结果计数替代；保存 `SHOW CREATE TABLE`／`SHOW INDEX` 前后比较。主表原始日期和公共条件用 JSON 内容等价比较，created_at 保持；updated_at 允许失败原因刷新而变化，不错误要求 MySQL JSON 字节顺序相同。

故障只注入指定单元／阶段一次：

- SQL 分组：测试 batchSize=2，使同一恢复单元至少3行；第二 SQL 组以 SQLSTATE 45000 trigger 拒绝。fixture 四列同股同日只有一个复合键，此场景复用 `InitialDownloadServiceIT.independentStocksPreserveAAndCAfterLaterSqlGroupRollsBackOnlyB` 的受控 fina_audit 图：内存definition与descriptor同时将ts_code改为可选，真实列／复合键／`tushare_pro__fina_audit`表不变。仅此受控来源接受多股，B同一ann_date用不同end_date形成3个合法键；生产YAML／DDL不变，不声称真实fina_audit支持全市场请求。证明B整单元回滚，A已提交不变，回滚已确认且数据库健康时C继续。
- 明细 DELETE／最后主表 DELETE：仅目标 task/selector 的 trigger 拒绝，业务写入也回滚，原项保留。单独移除 trigger 后才显式重试；SQL分组与删除失败均覆盖 I/U 混合，已存在业务值不得被失败事务改变。
- 保存失败：首次明细 INSERT、后续追加及原因 UPDATE 分别拒绝，主子首次原子性、先前已确认 taskId／业务小计及 nullable remaining 按T12～T14快照核对；后续计划不得调用。
- commit 未知：复用现有 DataSource 代理的真实 delegate.commit 后抛 SQLState 08，以及提交前连接故障；区分“数据库已提交但调用方未知”和真实回滚。覆盖业务单元、首失败创建、重试原因更新、最后项成功删除。独立 SQL 可核实实际存储，但业务响应仍按事务框架确认口径保留未知，不能反查补计数、重插或继续。
- 事务框架已经确认 COMMITTED 后的异常单独回归，计为已确认事实；不能和 delegate.commit 后框架未确认混为一类。

### 5. 真实进程与客户端断连

`RangeProcessIT` 必须用本轮 `target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，不向包加入 test classes 或故障控制 bean；package 合同仍是“生产包 + fixture JAR + V6”两项增量。child 环境移除 TENSOR_TUSHARE_TOKEN，设置 TENSOR_TUSHARE_ENABLED=false、独立 container DB；仅绑定 loopback。记录 JAR摘要、子PID、随机端口、日志位置、真实 HTTP 和数据库证据。

1. 启动等待该子进程实际只读 metadata HTTP 成功，限时90秒；不能 sleep 猜启动。首次1～10日执行，来源在第5日 entered 后hold，独立 SQL确认1／2／4业务已提交、3日失败已保存、5～10未预登记。仅对本轮child调用 `Process.destroyForcibly()` 并等待实际退出，模拟突发终止；不用普通destroy触发既有70秒graceful shutdown。保留父来源和DB。重新启动同JAR／配置，GET只看到3日；无新来源调用。显式重试只调用3日，未开始／中断日不补入任务。
2. 初次和重试各用真实 `Socket` 写 HTTP，请求 body 精确长度，重试零字节。服务端来源 entered 后，独立 GET 验证 retrying／blocker，关闭客户端socket（SO_LINGER=0产生RST）；另一个用例由客户端 read timeout 后主动close。此时第二个初次／execute实际HTTP均409，GET可读。release来源后独立SQL等实际结果，最后请求可重新获取槽位。不得 Future.timeout／MockMvc writer替代实际断连证据。
3. 最后一项重试执行中先断开客户端，然后release并观察业务提交、两表删除；客户端失去响应后再次显式execute为404，不重插。另做多项重试先成功删除A、后在B来源hold时同样destroyForcibly终止child／重启，仅B还在。GET查不到不用于推断丢失响应的成功计数。

事件等待均有测试超时和失败输出；清理仅关闭本轮server／child／container，finally记录清理结果。进程kill是验收外部故障，不是产品取消接口。不得终止用户已有服务或依赖固定sleep证明顺序。

### 6. TRD §12.3 的逐项矩阵

下表编号严格对应TRD原17项；每行包含HTTP或服务结果、来源序列及独立业务／两表SQL，不把上游请求次数当恢复单元数。日期示例以2026-09为主，A/B/C为合法不同股票代码。

| # | 测试类／场景与注入点 | 必须观察的结果 |
|---:|---|---|
| 1 | Http／Process：启动、32天／参数失败、缺日历、全部成功／空、全休市 | 前置失败业务调用0；启动／未开始／全成功／全闭两表0；旧业务不删 |
| 2 | Http：STOCK_TIME完整A/B/C，B amount非法；相同批次无逐股状态 | A/C各自提交，B无业务、仅B失败；S/F=2/1、独立I/U与真实行一致 |
| 3 | Http／MySql：同日A/B失败、重复原因更新、A后B分别成功 | 完整五字段主键2项不合并；重复只更新原因；A删B留，最后主表删除；原taskId复用 |
| 4 | MySql：B第二SQL组失败、明细／主表删除失败、I/U混合 | B全部回滚、A保留、健康时C继续；业务与对应删除同事务，故障恢复后显式retry |
| 5 | Http：第2页失败／截断、总数不符、终页缺证、ts_code／日期归属不明；完整显式成员失败作对照 | 前页业务0；不完整批次精确REQUEST；完整证据成员可STOCK；不猜单股、不提前发布 |
| 6 | Http：全页取全但无逐股状态，B业务字段错；来源缺某股；合法空 | 局部隔离、缺行不等于失败、无占位；合法空不新增记录，重试空只删该项 |
| 7 | MySql：REQUEST校验／SQL失败；既有STOCK RANGE受控真实定义；先成功STOCK后下一批失败 | REQUEST对象及完整RANGE不升级／裁剪；失败整项留，已提交STOCK不包回REQUEST |
| 8 | Http／MySql：ANN 1～10日3／7失败；九类来源码参数化；连续失败后成功；受控TRADE+RANGE的两个多日开市段 | 首次十日按序各一次，8个业务成功、2失败；每类错误保存后继续所有后续计划；第一多日段保存失败后同一execute继续第二段，不能以另一次retry替代；无本轮自动重试 |
| 9 | MySql：首明细INSERT、追加／原因UPDATE失败、保存commit未知 | 首两表原子性；后续不调用，先前业务保留；TASK_RECORD_SAVE_UNCONFIRMED／COMMIT_UNCONFIRMED与快照相符，不声称完整保存 |
| 10 | Process：§5真实kill／重启、部分及最后项丢失响应、并发 | 不补缺失记录／重插已删项；新进程GET只读实际项；忙409、不存在404 |
| 11 | Http／MySql：31/32天、2028闰日、跨年、完整月、NONE；重试空／全休市 | 边界与真实来源参数正确；空／闭删除原项不删旧业务；H独立计数 |
| 12 | Http／MySql：主表JSON副本、selector非法／未知、代码错误／冲突／不可执行 | 公共条件与原始两端不变；重试去展示日期再合明细；拒绝保留记录、来源调用0。MySQL JSON列无法插入非法JSON文本：该分支由真实repository返回边界的测试桩注入损坏文本并保持旁路SQL原记录，另用合法JSON但非法结构在真实DB覆盖；不禁约束 |
| 13 | Http／MySql：2成功1失败、空／闭、既有插入／更新、四停止快照 | S/F/N/H按单元、R/I/U仅框架确认提交；N／remaining未知为null，不填0；HTTP实际mapper与SQL并列 |
| 14 | Process：初次／重试真实RST和读超时后关闭 | 断连后继续，actual execution结束前409；结束后槽位可用；无cancel状态／预登记／自动重发 |
| 15 | MySql：用实际trade_cal/new_share/namechange定义装配受控插件和真实controller映射，单日失败后retry | 首次与retry均相同start_date=end_date紧凑日，trade_cal exchange保留，无trade_date/ann_date；另核对DATE模式原生来源键。namechange仅受控，无真实网络 |
| 16 | Http／Process：原1～10、3／7→7→无；再次失败、重启／重新GET | task_params原始两端、公共条件及created_at不变、schema不变；来源仅3和7后仅7，不请求原十日／合并3～7；列表／详情读取同原区间 |
| 17 | Http／MySql：同日起止、休市过滤、8/15～9/3仅9月失败、11原条件、旧记录 | 原输入不改；MONTH retry完整9月；49元数据合同中11个原条件均不增两端；旧缺日期返回未记录并按明细恢复，不推算 |

真实定义受控图只在测试构造 descriptor／plugin，保留真实 adapter.definition 参数等价及 source mapper；严禁启动真实 TusharePlugin 或修改生产 capability 表。第7的 STOCK RANGE、第15的三真实API及第17的11原条件在 `RangeFailureMySqlIT` 使用这一路径，报告标注“受控真实定义”，不冒称fixture四列覆盖真实宽表。

第8多日继续也在该测试类：用真实daily列／键／表，内存definition与descriptor同时使用必填start_date/end_date来源参数；策略TRADE_DATE_RANGE、TRADE_DATE、C-A、RANGE、SOURCE_RANGE、REQUEST。受控日历2026-09-01～07只有04休市，规划恰为01～03及05～07两段；第一段来源失败并确认保存REQUEST RANGE后，同一execute实际获取第二段并提交3行业务。独立SQL只见第一段失败，原始01～07不变。该受控RANGE能力不写入真实daily策略。

### 7. 测量、安全和证据

固定三组输入，每组一次小规模热身、三轮正式运行，每轮仅清本轮独立DB数据并重新计数：

| 负载 | 规模与实际表 | 测量含义 |
|---|---|---|
| 多日多页 | 31日 × 每日3页 × 每页200个不同股票，共18,600行；fixture表，REQUEST按日 | 31批／93页取全后才逐批提交，无少页／重复页 |
| 单日高量 | 10,000股同日 × 1行，20页 × 500；fixture表，STOCK_TIME | 10,000个独立恢复单元真实提交；不是单一事务替代 |
| 宽表 | 真实income定义85列；100股同公告日，每股50个不同合法end_date，5,000行、每页10；受控测试图 | 实际85列包括可空列都填合法非空值，复合键唯一；ts_code公共条件按真实必填要求逐股调用，单股数据分5页×10行，共500页；不改真实参数以制造全市场请求 |

宽表总页数以逐股方式为准（100×5），每次完整单股50行，不跨股冒充一个合法请求。记录每轮实际 HTTP批／页数、S/F/N/H/R/I/U、真实行数，错误／失败表应为0。每轮额外以相同数据显式执行一次更新回归并单列I=0/U=已有行数，不能把更新轮混入三轮首次耗时。

耗时采用单调时钟，范围从首次计划／来源调用到最后事务确认；来源服务生成数据开销单列。50ms采样执行 JVM 的 MemoryMXBean heap-used 和该PID RSS（macOS用 `ps -o rss= -p <pid>`），同时记录采样峰值、开始／结束值、采样周期、JDK、CPU／内存、JVM -Xmx、MySQL及batchSize。报告每轮与中位数／最大值，不将采样峰值称绝对瞬时峰值；不以固定性能门槛或额外执行上限掩盖未完成工作。若出现OOM／超时／错误，记录失败并定位，不能降低规模后仍报原场景通过。

安全用虚构 canary 放入来源错误body／header和异常cause；核对child日志、Web错误、task_params/error_message无canary、凭证、URL query或完整响应。正常业务字段本来就应入业务表，不把合法业务入库误判为泄漏。source请求捕获只保存合成公共参数／页号，不记录环境变量或秘密。来源URL只loopback、Tushare关闭；正式报告不含实际DB密码。

原始 stdout／stderr、Surefire XML、来源调用JSON、独立SQL、JAR摘要、PID/时间事件、测量CSV保存在忽略的 `.superpowers/sdd/RANGE-T18-design/`，每次clean前归档XML。正式报告用稳定用例名／参数集逐行索引17场景，列实际命令、退出码、数量、零失败／错误／跳过、环境和未验边界；追踪表只更新本项后端证据。

## Files

以下是实施路径，不表示本设计阶段已创建源码：

| 路径 | 责任 |
|---|---|
| `data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java`、`FixturePlugin.java`（同目录） | 共享动态definition、启动选项、真实batch/calendar能力；保留默认API |
| 同目录新增 `FixtureBatchSource.java` | acceptance专用分页／日历客户端与安全错误映射；runtime及受控真实定义图共用 |
| `data-plane/tensor-plugin-fixture/pom.xml` | 显式已有受管Jackson依赖，无版本升级 |
| `data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java`、新增 `FixtureBatchSourceTest.java` | 默认兼容、五模式投影、取全／错误／日历／配置边界 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/RangeFixtureHttpIT.java`、`RangeFailureMySqlIT.java`、`RangeProcessIT.java`、`RangeLoadIT.java` | §1四层验收；小helper留所在测试类，避免复制已有故障逻辑 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/support/ControlledRangeSource.java` | 父来源服务器、脚本、精确捕获／hold及可独立main |
| `data-plane/tensor-app/src/test/resources/fixture/range-browser-initial.json`、`range-browser-partial.json`、`range-browser-success.json`、`range-browser-two-stocks.json`（同目录） | T19可直接使用且本轮实际跑过的来源脚本 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java` | 真实fixture批次正向合同取代旧安全拒绝；保留其他错误回归 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java` | 仅在现有断言需补运行能力／依赖安全时增量；仍保留精确包差异 |
| `docs/verification/RANGE-T18-controlled-mysql.md`（新增）、`docs/traceability/tensor-range-requirements.md` | 本轮矩阵、命令／SQL／进程／测量／安全证据、T19运行配方 |
| 本看板、交接索引及按既定流程准备的T19设计／交接 | 完成证据及后继准备；仅T18验收完成后进行 |

不预先授权改 Core／App生产业务逻辑来迎合测试。若新用例暴露真实缺陷，先保留行为RED、定位违反的既有合同，再做最小同范围修复和对应回归／独立复审；在报告列出新增实际路径。需求或来源事实变化不能靠受控测试自行升级。

## Tests

先记录新的工作区／原索引／58资源基线，不复用T17的数量作为T18起点。按TDD先写实际行为RED：无包装fixture首次HTTP可执行，分页第2页失败业务0且精确REQUEST，原1～10／3、7→7的真实来源捕获。缺文件／编译失败与行为RED分列。随后实现最小fixture，再逐组加入SQL、进程及负载证据。

仓库根执行；普通IT必须显式选择。Docker前提是本机Colima socket确实可用，缺失时报告阻塞事实，不能skip。每条结果分别保存，不能把多个运行拼成一次：

```sh
mvn -f data-plane/pom.xml -pl tensor-plugin-fixture -am -Dtest=FixturePluginTest,FixtureEnvelopeFactoryTest,FixtureBatchSourceTest,DownloadParameterProjectionTest,SourceParameterMapperTest -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadServiceTest,RetryDownloadServiceTest,DownloadBatchPlannerTest,RecoveryUnitProcessorTest,BatchCommitServiceTest,RetryTaskStorageServiceTest,RetryTaskQueryServiceTest,DownloadParameterConverterTest,TaskParametersJsonTest -Dsurefire.failIfNoSpecifiedTests=false test

env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RangeFixtureHttpIT,RangeFailureMySqlIT,InitialDownloadServiceIT,RetryDownloadServiceIT,BatchCommitServiceIT,RetryTaskStorageIT,RetryTaskControllerIT,DownloadControllerIT,DatasetControllerIT,FixtureFlowIT,ProductionApplicationContextIT,PersistenceServiceIT -Dsurefire.failIfNoSpecifiedTests=false test

mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify

env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RangeProcessIT,RangeLoadIT -Dsurefire.failIfNoSpecifiedTests=false test

PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

acceptance clean verify 后再跑 Process／Load，保证JAR存在且无旧包；该次显式test不得修改production／fixture源码，报告对比源码与包摘要。需要修源码就重新build及受影响验收。两次verify各自前端test/build由现有构建驱动并记录实际数量；T19再运行配置环境后的test:e2e，本项不把未运行e2e计通过。

各指定测试类必须真实出现且 tests>0、failures/errors/skipped=0；`failIfNoSpecifiedTests=false`只允许无所选类的reactor模块，不能允许目标类缺失。17行均有本轮执行证据和SQL。包检查证明生产JAR无fixture/test classes、acceptance只有既定增量；受控场景未触碰真实源。

实现收尾按既定独立规格／质量及最终集成／证据评审；复审关闭Important／Critical，证据与最终源码一致后才记录T18 COMPLETED。随后依看板Order准备T19专属设计／交接，不自动启动T19。

## Acceptance

1. 可直接启动的 acceptance fixture 真正支持五模式的 `planBatch/fetchBatch`，保留旧scenario／旧流，生产包和58资源保护通过；T19取得本轮验证的四脚本和完整运行配方。
2. §6的17行都有可定位的本轮测试、来源参数／页序、业务表和两失败表独立SQL；非法JSON文本不可由MySQL存入的层次限制明确，不能伪造真实数据库坏文本证据。
3. A/C保留B回滚、同日两股分别删除、REQUEST/RANGE原边界、1～10／3、7→7原参数不变、九类来源失败继续等核心闭环通过；无预登记或本轮自动重试。
4. 保存／commit未知按真实事务确认停止、nullable不补0，不通过反查或重插恢复已删除项；真实进程重启与真实socket关闭／超时证明槽位及恢复边界。
5. 所有指定测试、两次完整构建、包／合同／参数迁移检查通过；实际数量和原始日志/XML一致。三类固定规模测量及安全结果完整，不新增性能硬门槛或产品上限。
6. 正式验证／追踪仅升级本项后端受控事实，T19页面、T20真实支持、ISSUE-008边界保持；独立最终评审通过、新文件纳入Git、原索引／任务外工作保留，再按工作流完成和准备后继。

## Risks

- acceptance fixture 的可执行来源合同属于合成环境；`DOCUMENTED_CANDIDATE`及取全证明不能外推到Tushare。生产日历／完整能力表仍按既有保守行为拒绝。
- 默认fixture四列不能表达同股同日多SQL组或85列宽表，因此特定测试使用真实定义／真实迁移的受控插件图；报告必须准确标注层次。
- 真实进程／网络测试容易把等待超时误当业务结论；只接受entered／SQL／HTTP／进程退出事件链及有界等待，异常清理不能杀用户进程。
- 两次clean／verify会覆盖XML，先归档；显式普通IT和真实JAR进程验收缺一不可。性能为当前机器采样观察值，不能当SLA或来源吞吐证明。
- 当前设计无未决产品需求；运行环境可用性及实际测试结果待T18启动后验证。READY只表示本设计和交接可执行，不提前声称通过。
