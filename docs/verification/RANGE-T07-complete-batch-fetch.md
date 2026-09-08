# RANGE-T07 完整批次获取验证

2026-09-08执行，任务为[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的RANGE-T07，依据为[专属设计](../task-designs/RANGE-T07-design.md)。实现、规定验证及独立复审均已完成。本项证明来源层机制和生产拒绝边界，不表示真实Tushare取全合同已启用。

## 交付行为

- 共享SPI新增`fetchBatch`，缺省检查context后明确拒绝完整性，不委托旧download。Tushare入口保留readiness、API查找和安全错误顺序，委托独立完整批次执行器。
- 来源会话逐批创建；生产注册表为空。先核对身份、恢复策略与请求依据，再查完整来源合同。8项未确认和forecast冲突返回SOURCE_REQUEST_UNCONFIRMED，其余40项返回SOURCE_COMPLETENESS_UNCONFIRMED；trade_cal的BSE条件额外拒绝。所有生产检查均无业务调用。
- 只允许声明的分页控制键。原批次键、接口来源／公开字段和固定业务键均受保护，分页参数、注册表和批次参数防御复制；不生成通用offset或limit。
- 每页经现有真实客户端解码，再核对包络身份、实际参数、字段顺序、行宽、游标与实际请求进展、累计总数。所有页通过且全集回调通过后才返回完整包络，参数恢复原批次，行顺序、BigDecimal及null保持，failures为空。
- 后页失败、协议矛盾、明确截断和完整性未知均不返回部分结果；不自动重试、拆小或续页。再次主动fetch使用新会话并从第一页开始。
- 服务端context在初始、每次来源前后和最终提交给调用者之前检查，异常实例原样传播。没有客户端取消、自动重发、持久化或事务副作用；旧download、HTTP和fixture路径保持。

## 本轮实际验证

Maven沿用前序任务已确认的沙箱外环境，支持Mockito附加测试JVM；未修改依赖或禁用断言。WireMock仅监听测试本机，未读取真实凭证或调用真实来源。

| 命令 | 退出码 | 实际结果 |
|---|---:|---|
| `mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-plugin-tushare -am test` | 0 | 最终244项Java测试（API98／Tushare146），零失败／错误／跳过；8.365秒 |
| `mvn -f data-plane/pom.xml test` | 0 | 635项Java测试（98／97／141／13／286）、170项前端测试及构建通过；27.301秒 |
| `mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareCompleteBatchFetcherTest,TushareProPluginTest -Dsurefire.failIfNoSpecifiedTests=false test` | 0 | 补强后的执行器21项及插件17项，共38项通过；5.766秒，后续评审修正由最终模块及verify再次覆盖 |
| `mvn -f data-plane/pom.xml verify` | 0 | 最终640项Java单测（98／97／146／13／286）及4项生产JAR合同，共644项；170项前端测试及构建通过；30.529秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 0 | 最终640项Java单测、4项生产JAR及3项acceptance JAR合同，共647项；170项前端测试及构建通过；36.111秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 0 | 8组合同和4个内存变异反例全部PASS |
| `PYTHONDONTWRITEBYTECODE=1 python3 /private/tmp/tensor-range-t07/audit-resources.py` | 0 | 51份受保护资源、T01摘要及分类／拒绝／排除边界逐项通过 |

实际调度测试均零失败、零错误、零跳过。保留既有Maven编码及Mockito动态代理／JVM附加警告。完整日志临时保存在`/private/tmp/tensor-range-t07/`；本报告保存持久结果。全量test的635项与最终640项之差来自后续补强的5个测试方法；它们均已纳入最终模块及两次verify验证。

当前POM默认Surefire不调度`*IT.java`，Failsafe仅运行上述JAR合同。数据库／容器IT仅编译、未执行：PersistenceServiceIT、ExistingKeyRepositoryIT、DatasetQueryServiceIT、DownloadControllerIT、DatasetControllerIT、FixtureFlowIT、FlywaySchemaContractIT、DividendBusinessKeyMigrationIT、ProductionApplicationContextIT。浏览器E2E、真实日历及真实业务API均未运行。

## 测试先行与修正

1. 首先添加共享SPI签名、默认拒绝／context及Tushare生产候选拒绝测试。模块命令退出1，停在API的testCompile：`DataSourcePlugin.fetchBatch`尚不存在，5处缺符号。这是入口缺失的编译证据，不是分页运行行为反例。
2. 新增入口后的首轮模块测试中API98项及插件入口通过，旧TushareErrorClassifierTest的方法穷举未包含3个新工厂，整轮失败。同步精确方法及固定安全错误断言后通过；`entry-green.log`实际是这轮失败日志，不记为成功。
3. 两页合并及后页失败用例先于页循环实现写入；针对骨架运行时2项中1失败、1错误：成功用例收到SOURCE_COMPLETENESS_UNCONFIRMED，后页失败用例未获得预期SOURCE_RATE_LIMITED实例。日志为`pagination-red.log`。实现循环后同2项通过，随后逐步扩展边界矩阵。
4. 主流程复核发现T05形状仅验到mock客户端，补成真实mapper→客户端→WireMock逐键断言，并补三原生单日、生产具体条件、后页超时及插件7次context检查。首次补强运行中测试配置的1024字节响应保护不足以容纳宽表字段列表，触发SOURCE_PAYLOAD_INVALID；仅调整该测试的响应额度，生产保护不变。最终38项及两次完整verify均通过。

5. 独立评审指出精确事件／边界计数、真实observe回调、可选总数分支及分页声明变更的断言不足；补齐后复审通过。同时逐API核对8项未确认＋forecast冲突，增加受控来源零open、正确含休市日历及日志哨兵。构造器null client的固定异常先行测试失败（预期IllegalArgumentException，实际NullPointerException），修正后通过；最终两次verify在该生产修正后执行。

## 可观察覆盖

| 范围 | 本轮结果 |
|---|---|
| 生产及默认拒绝 | 真实49项9／40分组拒绝；stock_basic L／P／D及trade_cal SSE／SZSE不误启用，BSE请求未确认；业务client为零。默认fetchBatch不调用旧download，服务端异常实例保留 |
| 两页、单次与空 | 2＋1行两页有序合并，合成page_token只出现在后页；单次及有明确保证的空结果通过，未知空与未声明分页的CONTINUE拒绝 |
| 真实请求形状 | SourceParameterMapper及真实client到WireMock覆盖daily、income、margin、broker_recommend、stock_basic、index_classify和三原生接口单日；捕获api_name与params逐键相等，股票／市场不丢，原生单日两端相等 |
| 精确内容 | 0.1、12345.123456789012345678、1e-18、1.2300与null经真实JSON解码及跨页合并后保留，原批次参数不变 |
| 后页错误 | HTTP429／503、真实超时、非法JSON、缺字段、错宽及业务权限错误均整批无结果，恰2次请求，无第三页或自动重试；主动再次调用从首页开始 |
| 协议与进展 | 重复游标、A→B→A、不同游标同实际请求、变化／超出／结束不足总数，以及错误身份／参数／列顺序／FAILURE包络拒绝；可选总数缺省和明确结束规则分别验证 |
| 参数与结构 | 保留业务键／非法声明、null会话／声明／页参数／观察、未知键、非字符串值和非法Observation均固定安全来源错误；外部修改不污染冻结结果 |
| 全集范围 | daily越界及非法日期拒绝；trade_cal缺日、相同行数但重复日、错市场、越界拒绝，错误无cause／suppressed。脚本仅为测试合同，未注册生产 |
| 服务端与安全 | 初始故障时open／分页／client均零；来源后和最终检查故障不返回结果。直接两页6次、插件入口7次check，来源及服务端异常分别保持既定边界，安全摘要不携带来源内容 |

## 独立核对与评审

实施前保存49份Dataset及两份生产策略资源SHA-256，最终51个文件逐项相同；49份Dataset同时与T01登记摘要相同。T01分类19／15／1／3／11、请求状态40／8／1、全部REQUEST及分页未确认、九项真实排除不变。Core／Web／fixture／生产配置及其他前序运行代码与本轮基线相同。

独立首轮评审确认生产及集成边界合理，提出2项重要测试证据缺口和2项次要问题；合并修正后完整复审规格PASS、质量PASS、最终集成PASS，无遗留Critical／Important／Minor。首轮通过的verify641／acceptance644后，因构造器及测试修正再次执行最终verify644／acceptance647，上表为最终结果。

日志哨兵测试实际捕获JUL、stdout和stderr，分别用无敏感内容的探针证明捕获生效，结束时恢复流及handler。当前模块只有SLF4J API而无测试provider，该结果限定于现有捕获渠道，不认证未来任意日志后端；固定错误状态独立验证，新增执行器和既有client均无日志调用，未增加日志依赖。

`git diff --check`及`git diff --cached --check`均退出0；交付代码和验证／追踪文件已加入Git暂存，未提交或发布。

## 验收边界

[来源层增量追踪](../traceability/tensor-range-requirements.md#range-t07-完整批次来源层增量证据)只回填AC-PRD-RANGE-10／11／12／14／29的本项机制证据。生产完整批次注册表保持空，49项尚无已启用的完整取数合同；合成分页键及受控结束观察不代表Tushare真实分页协议。现有三参download与HTTP过渡路径保留，后续完整区间执行必须调用fetchBatch。

T09所有行归属、恢复单元隔离、数据库事务、失败保存／后续批次继续、HTTP区间及最终功能AC仍由后续任务验证。ISSUE-008仍“不依赖，未解决”。
