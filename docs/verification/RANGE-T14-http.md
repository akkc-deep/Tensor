# RANGE-T14 下载与失败任务 HTTP 验证

2026-09-09，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 RANGE-T14，依据[专属设计](../task-designs/RANGE-T14-design.md)及[入口交接](../task-handoffs/tensor-range/RANGE-T14-handoff.md)。本轮元数据、首次下载与三个失败任务端点已实现，规定聚焦测试、显式 MySQL、两次全量构建和独立规格／质量／集成评审均通过；两项独立评审发现已修复并重新验收，满足T14完成条件。

## 交付与可观察结果

- 元数据输出下载 `parameters` 与恰好五字段的公开 `downloadPolicy`。真实49项逐项对照独立 OpenAPI target 清单：19／15／1／3／11，38项起止日期和11项原条件、九种参数形状；股票／市场条件保留，`calendarProfile` 为 C-A 等字符串或 null，原条件 limits=null。内部来源能力和证据不进入响应。
- `POST /api/v1/downloads` 经严格下载绑定后只调用 `executeInitial`。31天允许、32天拒绝，包含同日、闰日、非法年份、逆序、缺端、股票规范化／多股／枚举／类型／未知及重复字段；旧 trade_date／ann_date／month 单独或混用均 PARAM_INVALID。11项不接受新增区间，fixture scenario 仍可绑定。拒绝先于业务调用。
- `GET /api/v1/retry-tasks` 保留存储的稳定分页和全部当前选择器；两个筛选独立可用，支持20／50／100，拒绝重复值及非法分页。真实MySQL同时间按UUID降序、越界回末页、空页和未知筛选均验证。单次 storage.list，无逐任务 find。
- `GET /api/v1/retry-tasks/{taskId}` 返回展平详情、安全公共条件及完整当前明细。原始日期 RECORDED／NOT_APPLICABLE／NOT_RECORDED／UNCONFIRMED 可区分；原1～10日不随当前3／7日或仅7日改变。GET保持原JSON／时间／原因，零来源回调、零适配、零写入、零槽位获取；可读孤儿主表、损坏选择器和SQL读取失败返回安全 QUERY_FAILED。
- 详情以一次原子 slot Snapshot 计算 retrying 和 canExecute；busy、插件／凭证、元数据／原参数与选择器、来源、日历按固定优先级选择 blocker。先静态兼容再来源拒绝，不能用过滤后的展示map放行。已知静态条件可用不保证实际来源、日历或执行成功。
- `POST /api/v1/retry-tasks/{taskId}/execute` 接受完整UUID和真正零字节body，仅向既有重试用例传UUID及RequestId。大小写完整UUID可用，Java宽松短段不可用；未知长度／媒体类型／空白／null／非JSON等任何首字节均400。无效输入400先于busy；合法busy409先于SQL和缺失404；无预读、可编辑参数、取消、排队或异步执行。

## JSON、错误与安全证据

[RangeResponseContractTest](../../data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RangeResponseContractTest.java)严格比较字段集合：结果18字段、任务summary／page／detail／item展平，无Core内部包装。实际生产 Spring ObjectMapper 的验证位于 [ProductionApplicationContextIT](../../data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ProductionApplicationContextIT.java) 的 `assertProductionMapper`；大于 Integer.MAX_VALUE 的计数仍为JSON整数，nullable Long为数字或显式null，原records BIGINT／DECIMAL仍为精确字符串。时间固定UTC三位毫秒，覆盖`.000Z`／`.123Z`；必需null和空数组不省略。

首次与重试两个HTTP入口分别覆盖 SUCCESS／EMPTY／NO_OPEN_DATES／PARTIAL／FAILED 的200结果；全闭S=0、H>0，合法空单元与明确失败分别计数。四个停止码 TASK_RECORD_SAVE_UNCONFIRMED／COMMIT_UNCONFIRMED／PERSISTENCE_FAILED／INTERNAL_ERROR 返回500及准确retryable、UNCONFIRMED快照；此前确认R/I/U、S/F/N/H、taskId和remaining按Core事实投影。业务前拒绝省略downloadResult，26码既有映射回归通过。请求头、正常结果、ApiError及嵌套结果RequestId一致。

来源／SQL／历史原因／Authorization／正文／cause哨兵在HTTP及捕获的OperationLogger／handler日志中不出现；13种持久失败码只映射固定安全原因。日志保留真实六种outcome及确认计数；metrics维持原SUCCESS／EMPTY／FAILURE三类映射，PARTIAL／FAILED／UNCONFIRMED不记为成功。每个有结果POST一次观测、GET零次；受控观测故障不改变结果或重新执行。

## MySQL闭环、故障与同步边界

[RetryTaskControllerIT](../../data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RetryTaskControllerIT.java)使用隔离 `mysql:8.4.6` Testcontainers、App真实Flyway迁移、受控来源及连接／事务探针；不复制DDL，不用H2或跳过Docker。HTTP快照先独立断言，再由SQL确认实际保存状态，不能从查库结果倒推客户端已获确认。

| 观察 | 本轮结果 |
|---|---|
| 原1～10日、失败3／7日 | 首次200 PARTIAL，一主两子；GET保持原范围。重试只请求3再7，3删除、7原因更新，原JSON不变；下一次只请求7，最后项成功删除主表，随后GET／execute404 |
| 对象和任务隔离 | 同日两股分别保存／删除；另一同条件任务不受影响，不合并稀疏范围 |
| 首次保存失败 | 保留已知F及失败原因，停止；不伪造确认taskId或已保存状态 |
| 物理commit后丢回复 | HTTP当前仅unknown、不增当前R/I/U/S/F；首次不补建失败。最后项实际已删但回复未知时taskId／remaining均null，不用路径ID补回 |
| 确认最后删除后框架异常 | 保留已确认小计，taskId=null、remaining=0；不因500撤销已知事实 |
| 原原因更新commit丢回复 | 已知失败仍F，保存状态未确认，原公共JSON及邻项不改；无自动重试 |
| 业务事务不可用／读取失败 | 前者带停止快照的PERSISTENCE_FAILED；后者QUERY_FAILED且无下载快照 |
| fetch／commit／原因更新中查询 | 执行线程由有界latch停留，GET仍读取已提交快照；竞争POST409。等待者Future.get超时但不cancel原请求，槽位持续busy，释放latch后剩余单元完成，实际结束才释放 |
| 响应写出失败 | 业务结束后输出IOException，不重放、不修改已保存失败；此项是MockMvc写出边界，**不是实际socket断连** |

生产应用图确认一个查询bean、两个已有执行bean及共享slot／storage／registry／adapter。真实servlet路径对 daily 日历未确认、forecast 合法请求未确认、index_classify 完整性未确认均安全拒绝，无downloadResult；未调用真实来源。DatasetControllerIT保留旧只读查询、分页及精确数字回归。

## 实施中修正及真实失败记录

1. 当前Jackson的 `NumberSerializer` 无无参构造器，原设计直接注解造成序列化错误。改用 `DownloadResponse.LongNumberSerializer extends NumberSerializer`，唯一无参构造仅`super(Long.class)`，新结果／任务计数共用；全局精度模块不变。初次App迁移组28项出现2失败／26错误，同时暴露MVC需显式 `@PathVariable("taskId")`；修正后该组通过，最终扩展组结果见下表。
2. 原Deserializer对重复字段采用最后值。按T14严格合同，当前DownloadRequest parser局部开启STRICT_DUPLICATE_DETECTION，外层及params重复均400且零执行。先行1项行为RED（原200、期望400）后同项GREEN。兼容影响明确：发送重复键的旧调用现在被拒绝；其他Jackson消费者不变。
3. 生产FixturePlugin只实现旧download，默认planBatch／fetchBatch拒绝完整性未确认。正向HTTP fixture测试使用明确的测试内批次包装；另保留未包装fixture通过新HTTP拒绝且零写入的测试。[FixtureFlowIT](../../data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java)实际直接测试plugin→adapter→persistence/query，没有HTTP断言，原样回归。**生产fixture新区间能力仍由T18交付**，不能把包装测试记为已部署能力。

4. 独立App评审发现生产Spring mapper默认`FAIL_ON_UNKNOWN_PROPERTIES=false`，旧WireValues依赖该配置而忽略外层未知字段，裸ObjectMapper测试未暴露。采用私有WireValues上的`@JsonAnySetter`固定拒绝，不回显未知键和值、不改全局配置。Spring配置HTTP测试先RED（1项／1失败，实际200、期望400），最小修复后GREEN；实际servlet同时验证首尾未知字段400、固定字段错误／RequestId、无快照／敏感哨兵及零两表写入。该Important发现修复后的完整矩阵和评审另行采用最新结果。

5. 最终集成评审发现外层String标识会被Jackson从boolean强转，`pluginId:true`实际进入注册表并返回409，而合同要求非string为400。先行1项行为RED（实际409 PLUGIN_DISABLED、期望400）已复现；最小修复让私有WireValues的两个标识先保留Object，经null／原始string类型／正则验证后才cast。外层仍任一无效优先PARAM_INVALID、仅缺失为PARAM_REQUIRED，参数内部的required-before-invalid规则不变；固定字段错误不泄露值，也不修改全局scalar coercion。双标识boolean／整数／小数／object／array及有效string、混合缺失由Spring配置和实际servlet回归覆盖，最终结果以下列修复后归档为准。

测试先行记录区分编译和行为：首次规定三类测试因缺Query／Controller编译RED；隔离可执行场景后2项／2断言失败／0错误，旧daily.trade_date未拒绝且停止响应缺downloadResult。Core先行还有一个DatasetKey重载设置错误，不作行为RED。Core首审补强两个测试观察；一次冻结前后数字类型比较失败后改为比较实际保存map，最终80项通过。名为`core-review-green`的旧归档实际有1失败，不引用为通过。

第一轮显式MySQL八类114项有1错误：ProductionApplicationContextIT仍直接反序列化旧trade_date，迁移为新两端后原八类通过；最后再跑纳入实际生产mapper断言的八类114项。App九类首轮229项有1个ApiError字段反射旧期望失败，更新新增字段期望后最终234项通过。上述均为真实开发记录，不称作环境失败。

## 最终命令与新鲜结果

工作目录为仓库根；Maven 3.9.15、Java 21.0.11、Colima Docker 29.5.2。所有Maven串行运行，环境移除TENSOR_TUSHARE_TOKEN；需要Mockito附加／Docker时使用已授权本地测试权限。显式MySQL使用下表环境。仅采用命令开始后生成的XML，并在clean前复制归档。各行是独立运行计数，不能相加当唯一测试数量。

| 组 | 实际命令 | 最终结果／归档tag |
|---|---|---|
| 首三类HTTP | `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,RetryTaskControllerTest,RangeResponseContractTest -Dsurefire.failIfNoSpecifiedTests=false test` | exit0；3类39项；`app-three-types-final` |
| Core七类 | `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RetryTaskQueryServiceTest,DownloadExecutionSlotTest,RetryDownloadServiceTest,DownloadServiceTest,DownloadExecutionResultTest,DownloadParameterConverterTest,RetryTaskStorageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | exit0；7类80项；`core-reviewed-final` |
| App九类 | `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,DownloadParameterResolverTest,DataSourceControllerTest,ControllerUseCaseTest,GlobalExceptionHandlerTest,RetryTaskControllerTest,RangeResponseContractTest,OperationLoggerTest,RequestIdFilterTest -Dsurefire.failIfNoSpecifiedTests=false test` | exit0；9类236项；`app-nine-types-final` |
| MySQL八类 | `env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RetryTaskControllerIT,DownloadControllerIT,DatasetControllerIT,FixtureFlowIT,ProductionApplicationContextIT,RetryDownloadServiceIT,InitialDownloadServiceIT,RetryTaskStorageIT -Dsurefire.failIfNoSpecifiedTests=false test` | exit0；8类114项；`mysql-eight-types-final` |
| 普通完整构建 | `mvn -f data-plane/pom.xml verify` | exit0；62份Java XML、829项；前端24文件170项和build通过；`verify-types-final` |
| acceptance完整构建 | `mvn -f data-plane/pom.xml -Pacceptance clean verify` | exit0；63份Java XML、832项；前端24文件170项和build通过；`acceptance-types-final` |
| 静态合同 | `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | exit0；8组及4项变异拒绝，49目标／38迁移／11保留／29AC |
| Git空白 | `git diff --check`、`git diff --cached --check` | 两项exit0 |

上列Java各最终类tests>0，failures/errors/skipped均0；显式MySQL分别为Initial11、Retry11、Storage13、Fixture5、Production1、Dataset48、Download11、Retry HTTP14。默认verify及acceptance调度的打包合同不替代这八类MySQL。

日志含既有平台编码、Mockito／JDK agent、SLF4J警告；成功表示检查通过，不声称输出无警告，也未修改依赖以消除提示。

## 评审、保护与验收口径

原始日志、每组XML及summary、开始时基线和独立评审保存在仓库忽略目录 `.superpowers/sdd/RANGE-T14-design/`；报告是正式Git产物，内部归档保留供本地复核。代码评审使用相对任务开始时内容的diff及文件SHA-256，排除原有暂存T12／T13与ISSUE-017工作。

- Core规格／质量及两个测试观察复审：PASS／Approved，未发现生产缺陷，两个观察已补强且80项最终通过。
- App规格／质量：初审一个Important未知外层字段缺陷，修复后定向复审PASS／Approved；已解析最新38／235／114项XML及实际生产servlet回归，无新增发现。原FAIL报告保留为历史，`app-review-fix-report.md`记录发现已解决。
- 最终集成／证据：PASS／Spec PASS／Quality Approved。I1标识原始类型缺陷已解决；独立解析最终39／80／236／114／829／832项XML、前端构建及保护／暂存证据，27份冻结代码摘要一致，无未解决发现。`integration-review-report.md`保留初审FAIL和最终通过记录。
- 58份受保护摘要一致（49 Dataset、2策略、7生产迁移）；原分支 `feat/date-range-download`、HEAD `758f940503ded2d1185040bc8e324815c716a300`及780条原索引记录保留。任务外源码／UI提案及原未跟踪图片保持原状。八份新增Java已精确加入Git；本报告及后续正式设计／交接按流程加入Git。无提交、发布、分支切换或重置。

本项确认AC-PRD-RANGE-01、06、13、19、22、23、25、26、27、28的HTTP增量，见[追踪索引](../traceability/tensor-range-requirements.md)。生产日历和完整来源注册表仍为空，49项REQUEST策略不变；未读取真实凭证或调用真实来源，ISSUE-008九项仍不依赖、未解决。T15～T17负责页面接入，T18负责受控运行fixture及真实中断边界，T19／T20负责浏览器和真实来源验收；现有前端测试通过不等于旧页面已适配新HTTP，latch和响应写出失败不替代真实socket或进程kill证据。
