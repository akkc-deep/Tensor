# M14-T03 查询、分页、宽表、竞态和无障碍 E2E——任务设计

任务编号：`M14-T03`。权威来源：[任务看板](../task-handoffs/tensor-v1-task-board.md) Order 73 与 [任务卡](../superpowers/plans/tensor-modules/M14-integration-release.md#task-m14-t03-查询分页宽表与无障碍40h)。直接依赖只有 M14-T01。本设计在 M14-T02 完成记录 `71d9618` 之后编制；不启动本任务实施。

## Goal

从原样验收 JAR 的真实页面验证 AC-012～016、PRD 6、TRD 13.5～13.7：动态筛选、服务端分页、完整宽表、只读交互、过期查询隔离和键盘可访问性。使用确定性的本机上游数据经页面下载写入真实 MySQL，再通过页面查询验证，形成能复跑的 JavaScript 测试与实际证据。

## Scope

实施只新增 `control-plane/e2e/dataset-query.spec.js` 和 `docs/verification/M14-T03-dataset-query.md`。测试文件自行包含最小 Node 标准库上游替身、JAR 生命周期、只读 MySQL CLI 证据、页面操作及请求延迟门闩；不新增依赖、公共 helper 或全局配置。

覆盖五个既有数据集、超过100行、20/50/100分页、两种日期筛选、152业务列、null/零/空字符串/高精度、真实数据变化后的最后页归一化、旧响应和重置竞态、查询网络失败恢复与键盘操作。

不读取 M00～M13 后端生产实现，不修改 Java/Vue/YAML/SQL、迁移、配置、package/lock、POM、runbook、既有 E2E 或 JAR。不用直接业务 API 或 SQL 种数；不伪造元数据、分页 DTO、成功或错误响应，不调用 Vue/composable 内部状态。排除真实 Token/真实 Tushare、49接口总验收、下载失败矩阵、数据库破坏、性能和完整安全矩阵。产品缺陷保留失败断言和安全证据，按既定看板流程准备独立单语言修复任务，不自行发明任务 ID/Order，不靠 skip、重试或删断言完成。

## Approach

### 输入及兼容性

- [M14-T01 设计](M14-T01-design.md)、[测试](../../control-plane/e2e/fixture-flow.spec.js)、[证据](../verification/M14-T01-fixture-flow.md)：`23addbe` 实现、`afb3b85` 完成记录，提供真实页面、role/label选择器、原验收JAR、空schema、进程所有权与安全证据模式。仅借鉴局部方法，不 import 注册了测试的 spec，不重跑其生产包负向/禁用矩阵。
- [验收说明](../runbook/acceptance.md)、[配置](../runbook/configuration.md)：验收双开关、MySQL8.4、最小应用权限、默认显示时区、5s连接/120s读取/130s前端及70s每阶段正常停机。均不覆盖。
- [OpenAPI](../contracts/openapi-v1.yaml)：页面六种业务操作、元数据和精确九字段 PageResponse、requestId头体一致、字符串/null记录值。
- [M03-T02](M03-T02-design.md)、[M03-T03](M03-T03-design.md)、[M03-T06](M03-T06-design.md)、[M03-T09](M03-T09-design.md) 及 `docs/data-template/{daily,stock_company,index_classify,balancesheet,disclosure_date}.json`：独立字段原序、类型、参数、业务键和筛选基线；只读模板的 `fields`，不复制其中真实样例响应。
- [M05-T04](M05-T04-design.md)、[M05-T05](M05-T05-design.md)、[M06-T05](M06-T05-design.md)、[M06-T06](M06-T06-design.md)：TEXT保留空串、其他字符串trim后空转null，COMPOSITE按业务键字段原序ASC排序，先COUNT再归一页码，DATE/DECIMAL/LONG值合同。
- [M12-T01](M12-T01-design.md)～[M12-T05](M12-T05-design.md) 及其既有组件/页面测试、[M10-T03](M10-T03-design.md)、[M09-T06](M09-T06-design.md)：可访问标签、五态、分页、格式化、查询世代、固定网络错误、完成事件和分表面脱敏。

两个兼容性事实必须保留：当前49数据集没有同时声明 `trade_date` 与 `ann_date` 的定义，分别以daily和disclosure_date/balancesheet覆盖两类日期，不为条件性要求制造新字段或假元数据；balancesheet没有TEXT列，其STRING空串依法转null，空字符串与null的页面区别由stock_company的真实TEXT列补足。不能把合成balancesheet空串当作应原样入库的产品合同。

### 运行环境与清理

每次完整运行/重跑创建本任务专用**全新空**MySQL8.4 schema，名称匹配 `^tensor_m14_t03_[a-f0-9]+$`，`utf8mb4/utf8mb4_0900_as_cs`。运行者按runbook准备，应用账号只授予本schema的CREATE/SELECT/INSERT/UPDATE。既有验收JAR通过非秘密绝对路径 `ACCEPTANCE_JAR` 定位，原入口SHA-256为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`；记录实际哈希，不改包。若分发物缺失，由提供方走原验收构建流程重新提供。

三个 `TENSOR_DB_*` 通过隐藏输入/私有环境注入。额外测试输入为 `M14_DB_SCHEMA` 和 `M14_MYSQL_DEFAULTS_FILE`。后者是当前用户所有、0600、非符号链接的绝对普通文件，CLI使用与应用相同的专用账号，仅执行本文只读证据SQL。测试不生成或改写用户凭证文件。

在任何CLI调用前以内存严格校验默认文件：UTF-8无BOM，LF或CRLF，允许恰一个终端换行，恰六行；首行 `[client]`，其后host/port/user/password/protocol五个唯一key=value，次序不限。禁止空行、注释、其他group、include、额外key和选项。host匹配 `[A-Za-z0-9.-]+`，port十进制1～65535，protocol=TCP；user/password非空可打印ASCII，排除所有空白、单双引号、反斜线、#和分号，按第一个等号拆分，不trim秘密。非法输入以固定非秘密检查名失败，不回显文件内容、URL或原值，也不更改账号。

JDBC必须为无嵌入凭证及凭证query参数的MySQL URL，host/port/schema与CLI一致；文件user/password与三个应用变量匹配，全部用布尔校验。CLI固定shell:false，`--defaults-file=<path>`为首选项，其后 `--no-login-paths --host=<verified> --port=<verified> --protocol=TCP --batch --skip-column-names --raw --database=<verified schema>`；env只传PATH、LANG=C、LC_ALL=C。SQL走stdin，15秒上限，不回显原stdout/stderr，安全解析只读投影。

启动前验证Java21、Node/npm、MySQL8.4客户端/服务器、JAR普通文件、上述输入、初始表数0、8080未监听，以及PLAYWRIGHT_BASE_URL未设置或恰为 `http://127.0.0.1:8080`。不复用或停止未知进程。启动后只读验证V1～V6六条成功迁移、50业务表、五目标表初始均0行。启动前、健康后、最终三个阶段的只读证据分别合并为一次CLI batch，每次总上限15秒；不逐条另开15秒调用。

测试先以node:http监听127.0.0.1动态端口，生成只在内存/子进程环境保存的随机假Token，再启动自己拥有的JVM：

```sh
java -jar "$ACCEPTANCE_JAR" \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

用spawn、shell:false、独立mkdtemp工作目录与0600日志。净化继承的TENSOR_*/SPRING_*/SERVER_*/MYSQL_*/M14_*环境，仅回填三个DB变量、生成的TENSOR_TUSHARE_TOKEN和本机TENSOR_TUSHARE_BASE_URL；不读取调用者真实Token，不传工具默认文件，不改超时/日志级别。

根 `/actuator/health` 每次2秒、总90秒等待HTTP200/UP，提前退出或就绪超时安全失败。beforeAll为300秒；普通用例180秒；afterAll180秒。正常清理只对拥有的JVM发SIGTERM，最多150秒等close和8080关闭，不自动SIGKILL。独立关闭本机stub和本测试socket；每项请求门闩在finally释放，等待真实请求完成并移除route后才停止应用。主断言与清理异常都保留，双失败用AggregateError；一个检查失败不能跳过其余清理。afterAll最多用150秒停JVM、15秒执行最终只读batch，余下15秒关闭stub/socket并读取已落盘完整日志/扫描/汇总，不额外逐事件轮询。所有清理阶段各自try/finally收集错误；JVM停机超时也继续关闭自有stub并保留所有失败。route释放/等待/unroute已在各用例finally完成，不拖到afterAll。最终由运行者清理本次凭证及自有环境，保留安全本地证据，不删其他任务资源。

### 固定合成数据与页面下载

读取五模板的 `fields` 数组作为独立顺序基线，分别核对字段数11/18/7/152/5。不得由实际API列或后端源码反向生成期望。替身只接受JVM的 `POST /`，JSON精确键为api_name/token/params/fields；fields必须为该独立数组join(',')，token等于生成假值，参数/模式按下表精确校验。请求体上限64KiB，仅保存安全计数/模式，不记录原请求或完整上游响应；未知模式、额外调用或字段不匹配失败，无成功fallback和外部网络。

应答统一为 `{code:0,msg:null,data:{fields,items}}`，items依fields逐项投影为数组。下表未特别指定的业务列均为null。所有数值用字符串，避免JavaScript浮点丢精度；compact日期经真实适配/入库后应为ISO日期。固定长文 `LONG_TEXT = 'M14_T03_TEXT_' + '查询说明'.repeat(80)`。

| 模式/页面接口 | 页面参数及原始行 | 下载响应三计数 |
|---|---|---|
| daily-main / 日线行情 | `{trade_date:'20260807'}`；i=1～123，ts_code=六位补零i+'.SZ'、trade_date='20260807'；open/high/low/close/pre_close='11.23'，change/pct_chg/vol/amount='0'。 | 123/123/0 |
| daily-earlier / 日线行情 | `{trade_date:'20260806'}`；同样公式i=1～3，trade_date改为20260806。 | 3/3/0 |
| company / 上市公司基本信息 | `{exchange:'SZSE'}`；ts_code='000001.SZ'、com_name='M14 查询公司'、exchange='SZSE'、employees='0'、reg_capital='9007199254740993.123456789012345678'、introduction=LONG_TEXT、business_scope=''、main_business=null。其余null。 | 1/1/0 |
| index / 行业指数分类 | `{}`；index_code='801001.SI'、industry_name='M14 行业'、level='L1'、industry_code='M14'、parent_code='0'、src='SW2021'、is_pub=null。 | 1/1/0 |
| balance / 资产负债表 | `{ts_code:'000001.SZ',ann_date:'20260807'}`；ts_code/ann_date对应参数，end_date='20260630'、report_type='1'、total_share='9007199254740993.123456789012345678'、cap_rese='0'；其他146列全部null。 | 1/1/0 |
| disclosure-initial / 财报披露计划 | `{ann_date:'20260807'}`；i=1～123，ts_code=六位补零(900000+i)+'.SZ'、ann_date='20260807'、end_date='20260630'、pre_date='20260820'、actual_date=null。 | 123/123/0 |
| disclosure-corrected / 财报披露计划 | 同一页面参数、同一123业务键；i=1保留ann_date='20260807'，i=2～123改为'20260808'，其他值不变。只在末页归一化用例通过第二个页面执行。 | 123/0/123 |

前六个模式在第1项用例依次从“数据下载”真实选择/填写/点击建立基线；第七模式只在第6项执行。合计恰7页面下载POST、7上游调用，各模式一次。源码中的合成输入不属于秘密或禁止分享的真实上游响应；证据只保存业务结果投影，不复制完整Tushare包络。

disclosure_date的已批准业务键只有ts_code/end_date，ann_date可以更新。因此第七次真实页面下载能把122行移出查询条件，既不删除数据也不修改业务键。这是PRD 6.6“数据变化”条件，无需管理员写SQL或伪造超界响应。

### 页面与响应共同合同

一个serial describe、retries=0，恰11个稳定标题，Chromium/1440×1000；trace/video/screenshot自动采集关闭，只在安全扫描后显式截图。禁止only/skip/fixme、固定sleep/waitForTimeout和JS产品状态访问。采用Playwright自动等待和动作前waitForResponse。

选择源/接口/数据集使用role=combobox与可见label，展开后用可见option；来源为 `Tushare Pro`，接口/数据集名称按下表匹配，选后必须核对页面实际API身份。除第11项外可用公开控件click/fill/press；第11项只用键盘导航/输入/触发。

| 表面 | 固定定位/值 |
|---|---|
| 页面 | level1 heading“数据下载”/“数据查看”；导航link相同名称；入口 `/downloads`、`/datasets` 的200及至少一次真实导航。 |
| 源/接口/数据集 | combobox exact“数据源”/“数据接口”/“数据集”；daily option `/^日线行情daily$/`，其他为 `/^上市公司基本信息stock_company$/`、`/^行业指数分类index_classify$/`、`/^资产负债表balancesheet$/`、`/^财报披露计划disclosure_date$/`。 |
| 下载参数 | label“交易日期”/“公告日期”/“股票代码”（允许必填星号），exchange combobox label“交易所”、option“SZSE”；日期键入YYYY-MM-DD后Tab提交，POST应为compact日期。 |
| 查询字段 | label exact `证券代码 (ts_code)`、`交易日期开始 (trade_date)`、`交易日期结束 (trade_date)`、`公告日期开始 (ann_date)`、`公告日期结束 (ann_date)`；日期键入ISO后Tab，query始终ISO。 |
| 动作 | button exact“开始下载”/“查询”/“重置”/“重新查询”。 |
| 分页 | navigation exact“数据集分页”，内部唯一combobox，option exact `20/page`/`50/page`/`100/page`；前后按钮用role=button且可见文本“上一页”/“下一页”筛选，保留库的非空accessible name，不假定aria-label与可见文字相同。页码用区域内可见数字。 |
| 状态 | UNQUERIED“设置筛选条件后查询”；LOADING“正在查询数据”；EMPTY“未找到符合条件的数据”；FAILURE“查询失败”。INITIAL不要求live region，LOADING/EMPTY为status+aria-live=polite，FAILURE为alert隐含播报。 |
| 分页摘要 | status+aria-live=polite+aria-atomic=true，精确 `共 N 条，第 P / T 页`。空态仍有可用size选择、摘要为 `共 0 条，第 1 / 0 页`。 |

每次records GET只能由页面动作发出，监听精确plugin/API路径及参数。PageResponse恰九键requestId/pluginId/apiName/page/pageSize/totalElements/totalPages/columns/items，头体requestId相等、非空且唯一，身份与路由匹配；columns等于独立业务字段原序+三个来源列；每行完整键和值核对。所有数字/日期/来源列都按公开合同核对，ingested_at为有效带偏移instant，页面由独立Intl/Asia/Shanghai计算，不调用产品formatter。

daily全量期望为上述126行，按ts_code/trade_date字典ASC独立排序后取对应页；不得只验证长度、第一页或API返回值自身。来源与首次真实ingested_at保存安全基线，后续未更新的行深比较；页面每条业务row的cell完整文本与该页响应/独立期望一致。分页响应不得含全表126项冒充当前页。业务表头使用定义label，另核对定义name顺序与模板一致；不假定任意翻译label等于name。

记录pageerror、写请求、records请求/完成与非同源请求；非预期HTTP>=400、requestfailed、非同源请求、额外写入都失败。仅第10项预登记的一个records网络中断允许requestfailed；不全局忽略失败。每个已送到后端且收到响应的页面下载/records请求按完整requestId关联恰一条实际 `tensor.operation.completed`，解析精确白名单字段和值，最终停机再核对总数；不把metadata/health或未送出的中断请求要求为服务端事件。安全投影记录每项实际请求数与响应页数，不预填预计通过结果。

完成事件精确字段：download为 `requestId,operation,pluginId,apiName,paramSummary,sourceRowCount,insertedRows,updatedRows,durationMs,outcome,failureStage,errorCode`；query为 `requestId,operation,pluginId,apiName,filterNames,page,pageSize,resultCount,totalElements,durationMs,outcome,failureStage,errorCode`。本任务真实服务端业务都成功，download outcome=success、query含空查询也为success，failureStage/errorCode均none；durationMs为非负整数，计数/规范页匹配实际响应。paramSummary只含声明参数名，filterNames只含实际非空条件映射的ts_code/trade_date/ann_date，保持合同原序且不含值；不得存message/cause/stack。

### 可控等待和过期响应

仅第8～10项允许窄范围 `page.route` 故障/时序准备：第8/9项扣住**页面已经发起的一个真实records GET**，捕获后等待测试内release promise，然后 `route.continue()` 原样放行；不使用route.fetch/fulfill，不改URL/headers/方法/正文/响应。其他请求立即continue。10秒内必须收到目标请求，持有最多30秒；超时安全失败且finally释放。

第8项在旧daily请求被扣住且页面LOADING时选择index_classify，等待新定义并由页面查询取得真实index行；再释放旧daily，等待旧请求的真实200及requestfinished，确认其body确为daily/126行总数。随后等待两次requestAnimationFrame让浏览器处理Promise/渲染，页面仍是index定义、行、总数、分页，无旧daily表格或错误。不能以“旧请求尚未返回”宣称隔离成功。

第9项在请求被扣住时用“重置”，验证保留来源/数据集、筛选清空、UNQUERIED、表格/分页隐藏，再释放真实旧响应并确认页面仍未查询；之后新页面查询应为page1/pageSize50。各项finally无条件释放且等待路由handler结束、unroute；异常与清理分别记录。

第10项只对下一次页面records请求 `route.abort('failed')` 一次，模拟浏览器网络故障，不制造HTTP ApiError。精确显示M10固定消息“无法连接服务，请检查网络后重试。”、alert、“重新查询”，无旧表格/分页/虚构requestId。移除route后由可见重试按钮发起真实GET，按失败时条件/页大小恢复成功；不把浏览器中断误记为QUERY_FAILED或服务端完成事件。

### 宽表、文字和键盘的实证

balancesheet的定义恰152业务列，页面按原序含155个columnheader和每行155个cell，来源在最后；响应不存在内部business_key或额外列。完整核对152值（上述六非null字段，其余null）及三个来源值。total_share保留完整高精度字符串、cap_rese显示 `0.000000000000000000`，其他null显示 `--`。

固定列通过真实布局证明：初始记录首个ts_code header/cell的bounding box，滚动到末尾ingested_at可见，再检查首列仍可见且x位移不超过2px；其他普通列发生位移，页面横向溢出实际存在。用目标cell.scrollIntoViewIfNeeded和真实鼠标横向wheel滚动，不访问Element Plus私有class或组件props。无ts_code的index_classify同样验证其首个index_code固定，其他列不固定。必要DOM只读检查限于角色元素的几何/计算样式与scrollWidth/clientWidth，不注入DOM、改style或调用产品代码。

stock_company用于真实TEXT区别：business_scope的cell.textContent恰空字符串，main_business为 `--`，employees为`0`，reg_capital完整高精度，introduction完整LONG_TEXT。不能用trim后的统一空值断言把空串/null混合。对溢出的LONG_TEXT与balancesheet高精度cell分别hover，role=tooltip全文精确相等，内容只为文本；截图同时记录表格与tooltip，不把被省略的单元格截图当作完整值证明。

第11项从数据下载页以Tab/Shift+Tab找到“数据查看”link并Enter导航；继续Tab到来源、数据集、筛选、查询、分页。使用ArrowDown/ArrowUp/Enter/Escape操作combobox，dataset可键入daily搜索；检查aria-expanded和当前活动option后选择，不凭DOM内部ID猜序号。文本/日期用键盘全选、输入、Tab提交；查询与下一页以Enter触发，page-size以键盘选择20。除初始页面导航外禁止click/fill/locator.focus/程序化dispatchEvent完成该闭环。每个寻焦步最多80次Tab，超出直接失败。

实际焦点必须等于对应原生控件，所经表单控件有可见关联label；截图人工确认键盘焦点可见。先输入非法代码，Enter查询：固定文字“请输入代码.市场格式，例如 000001.SZ”、aria-invalid、aria-describedby和首错焦点，records零新增；再用合法代码和日期完成查询，清空代码后查询123条日期子集，以键盘size20和下一页观察page1/7→2/7、条件保留。控件不得出现新增/编辑/删除/导出/排序/行选择/列配置入口。

### 安全证据

测试/文档、完整API/health响应、页面可见文本、私有应用日志及PNG前置文本/共享JSON都扫描生成假Token和应用/工具密码；公开表面另禁止实际账号、JDBC/host/schema配置值、原始SQL和完整上游包络。响应在JSON解析/对象断言前扫描，页面在路由/状态/用例边界扫描，JSON/截图写前扫描；失败仅报告固定检查名，不能由matcher回显秘密。随机假Token独立保留到最终JSON检查结束。

私有正常启动/Flyway/JDBC日志可含不带凭证的连接信息；业务完成事件与公开证据不得含JDBC标记/配置。日志只保存0600私有文件，不分享原文或堆栈。事件按完整字段解析并保留白名单投影，不用子串匹配预期。安全泄漏算失败，隔离有问题的本地产物，不通过脱敏改写为通过。

至少保存：分页第2页与末页、服务端归一后的摘要、balancesheet左右两端、TEXT空/null/零对照及完整tooltip、旧响应完成后的新数据集、键盘焦点与文字错误。实际路径/SHA-256逐项记录，最终逐张人工查看。PNG/JSON/日志/凭证只在本地忽略目录/受限临时目录保存，不提交Git。独立只读最终库证据为6成功迁移、50业务表、daily126/company1/index1/balance1/disclosure123，其中disclosure ann_date两组为1/122；无故障DDL或数据删除。

## Files

- Create `control-plane/e2e/dataset-query.spec.js`：上述同文件辅助代码、固定数据、11项串行真实页面用例。
- Create `docs/verification/M14-T03-dataset-query.md`：实际环境/JAR哈希、输入准备、命令与退出码、每项结果、请求/行/页/事件投影、竞态释放顺序、截图路径/哈希/人工核对、只读DB证据、安全与清理、失败归因。

两个实施文件加入Git，提交消息 `test(e2e): verify read-only dataset UX`。设计/看板/交接独立提交；不修改或删除其他实施文件。

## Tests

恰11项稳定标题，按顺序执行：

| # / 标题 | 场景与必须结果 |
|---|---|
| 1 `seedsQueryDatasetsThroughDownloadPages` | 五表初始0行；前六模式由页面下载，精确6POST/6stub，逐项SUCCESS、三计数及页面反馈一致；入口导航正常。 |
| 2 `showsOnlyDeclaredFiltersWithoutAutoQuery` | 依次选择daily、stock_company、index_classify、balancesheet、disclosure_date；对应筛选分别为代码+交易日期两端、仅代码、无筛选、代码+公告日期两端、同前。定义到达后UNQUERIED、records新增0；旧字段/错误/表格/分页不残留。切换来源Fixture后无旧Tushare定义，回Tushare仍未选数据集。 |
| 3 `paginatesAllRowsWithServerTotals` | daily无筛选page1/50→2/50→3/50，total126/pages3，items50/50/26；切20回page1/pages7、下一页2/20；切100回1/pages2、下一页2/100为26。每页完整独立行序和值/分页摘要一致，查询始终只有page/pageSize。 |
| 4 `combinesTradeDateFiltersAndKeepsEmptyPaging` | daily代码带空格小写+8/7闭区间→精确1行；只from8/7→123，只to8/6→3；代码000001+8/5两端→0，证明AND而非OR。EMPTY保留筛选、无表格、page1/totalPages0，size20仍可用并真实重查0。 |
| 5 `resetsSelectionStateAndRejectsInvalidRanges` | daily先以tradeDateFrom=2026-08-07查询123行，切20并下一页到2/20后reset，来源/数据集保留、筛选清空、结果/分页消失且0自动查询；再查询page1/50。非法代码invalid-code、交易日期2026-08-08～2026-08-07逆序、切disclosure后的公告日期相同逆序各0records、固定文字和首错ARIA；结束reset清掉错误。 |
| 6 `normalizesLastPageAfterAnnDateCorrection` | disclosure ann_date=8/7闭区间total123/pages3/page1/50；第二页面执行corrected模式123/0/123后，原页面仍显示旧总数。点击原页码3，真实GET意图page3，但200响应及UI为page1/pages1/total1，唯一900001.SZ。改size20后请求page1；再查900002+8/7→0，900002+8/8→1，公告日AND真实成立。随后清空tsCode及另一端，只传annDateFrom=2026-08-08、page1/pageSize20，应total122/pages7、20行且首行900002.SZ；再只传annDateTo=2026-08-07、page1/pageSize20，应total1/pages1、唯一900001.SZ。两次ISO参数和对端省略均精确核对。 |
| 7 `rendersWideColumnsAndExactTextValues` | balance完整155列/值、来源、固定ts_code与横向滚动、高精度tooltip；company空TEXT/null/零/长文和tooltip区别；index无ts_code固定index_code。全部通过页面查询，截图不冒充完整DOM值证明。 |
| 8 `ignoresReleasedResponseFromPreviousDataset` | 按门闩流程daily旧表成功→新daily请求pending时旧表/分页隐藏、筛选/查询禁用而reset可用→切index真实成功→放行旧daily并确认真实完成→仍只显示index结果。 |
| 9 `keepsResetStateAfterPendingQueryCompletes` | daily以tradeDateFrom=2026-08-07查询123行并切20；扣住下一页2/20请求时reset。释放旧200后仍UNQUERIED且选择保留、日期清空；再查询无筛选为1/50、total126。不取消/伪造原响应。 |
| 10 `recoversFromQueryNetworkFailureWithoutOldRows` | daily以tradeDateFrom=2026-08-07查询123行、page1/50后，对下一页2/50的GET一次abort；alert固定NETWORK消息/重试、旧表隐藏，无虚构响应ID；移除route后按钮重试原条件/page2/pageSize50，真实200恢复50行与2/3摘要。只允许这一个预登记requestfailed。 |
| 11 `queriesAndPaginatesUsingKeyboard` | 键盘导航/选择/错误定位/有效查询/20条/下一页闭环，真实请求、label/focus/live/非颜色错误及只读限制全部成立，留下焦点截图。 |

第2项每次定义完成后至少等待两次requestAnimationFrame，并在整个用例请求监听中保持records为0；不用固定sleep或只在点击前检查计数。所有页码、筛选和重置测试同时比较页面与实际GET/响应，不只调用函数或断言网络。

运行者准备空库/私有输入/JAR后执行：

```sh
cd control-plane
node --check e2e/dataset-query.spec.js
npx playwright test e2e/dataset-query.spec.js --list
npx playwright test e2e/dataset-query.spec.js
npm run test:unit -- --run
```

预期依次退出0、发现恰11个Chromium用例、11 passed/0 failed/0 skipped/0 retry且所有清理成功、既有前端20文件/120测试通过。浏览器未装时先 `npx playwright install chromium`，不硬编码系统Chrome。先写完整黑盒测试，不制造产品RED；任何测试修订/失败修复后使用另一个空schema重跑完整11项。产品问题保留具体失败场景/语言和证据，等待独立修复后执行精确失败项所需基线及完整矩阵。

此外用同文件helper的纯合成探针验证：默认文件合法CRLF/非法秘密安全拒绝、带凭证JDBC安全拒绝、额外公开字段/页面秘密拒绝、主断言和清理双异常均保留、MYSQL_*不进入JVM；不得使用真实密码构造失败输出。无需新增永久探针文件或注册额外E2E用例。

根目录提交前：

```sh
git diff --check
git diff --quiet -- data-plane control-plane/src control-plane/package.json control-plane/package-lock.json control-plane/playwright.config.js control-plane/e2e/fixture-flow.spec.js control-plane/e2e/download-outcomes.spec.js docs/contracts docs/runbook
git status --short --untracked-files=all
git add control-plane/e2e/dataset-query.spec.js docs/verification/M14-T03-dataset-query.md
git diff --cached --name-status
git diff --cached --check
```

受保护路径/格式退出0，实施暂存精确两新增100644；原target与其他任务产物保留。不把设计期预期写成已运行结果。

## Acceptance

- 两个实施文件以一条真实Playwright命令完成固定11项，无伪造业务响应/直接业务API/SQL种数/生产改动；7页面下载和7本机上游调用精确一致。
- 五数据集动态筛选、两类日期的闭区间/单边/AND、无自动查询、reset与首错可访问性均得到页面和请求证据。
- 超100行按20/50/100真实服务端分页，行序/当前页/totals/条件保持正确；真实数据更新后超界页由服务端归一，并成为后续请求的页事实。
- balance152+3全列可滚动且首列固定，来源/精度/日期正确；TEXT空串、null和零各有真实入库/查询/页面区别，tooltip完整纯文本。
- pending隐藏旧结果；旧真实响应在新数据集成功或reset之后完成仍不能覆盖状态；网络失败不复用旧表、手动重试恢复。
- 键盘能完成导航、选择、输入、查询和分页，焦点/标签/播报/文字错误正确，无写入编辑功能。
- 11/11、前端120/120、语法/范围/格式、实际事件/行/截图/安全扫描与所有自有资源清理通过；无未解决产品缺陷或被跳过结果。

## Risks

- 同时具备两种日期字段的条件性产品规则没有现成注册数据集可从JAR页面触发；保留已批准元数据，明确只对现有两类日期分别给E2E证据，不冒称验证了不存在的组合。
- STRING与TEXT空串的适配规则不同；使用company的TEXT检验空串，不能修改balance类型或直接SQL写入制造该场景。
- 最后页归一化依赖disclosure_date非键ann_date的真实更新和原页面旧总数；不得刷新原查询页或先重查，让超界请求消失。
- 请求门闩必须按真实到达/完成信号协调并在失败时释放；未返回的旧请求不能证明竞态隔离，未清理的route不能进入下一用例。
- 本设计依据公开组件标签制定选择器；实施若真实accessible name与既有合同有展示差异，应保存安全快照，最小修订定位且继续严格核对API身份/结果，不修改产品。
- 此文仅为后继完成设计，新增11项尚未执行；真实产品或环境缺陷只能由实施时证据确定。
