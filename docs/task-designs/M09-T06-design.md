# M09-T06 配置、脱敏、指标、健康和静态资源安全——任务设计

任务编号：`M09-T06`
对应任务：[M09-T06](../superpowers/plans/tensor-modules/M09-app-api.md#task-m09-t06-配置脱敏指标与健康25h)
实施产物：`tensor-app` 的完整 Servlet 生产 Bean 图、安全配置、一次性操作完成事件、低基数指标、MySQL 健康和静态资源响应策略

## Goal

让 `TensorApplication` 在提供有效 MySQL 配置时形成可运行的完整 Servlet Bean 图，并为下载与查询提供一次且仅一次的安全完成事件和 TRD 17.3 指标。应用必须只从环境读取数据库凭证与 Tushare Token，默认只公开必要健康端点；MySQL 不可用时健康状态下降，缺少 Tushare Token 时只禁用该插件下载。所有响应必须带固定安全头，`index.html` 不长期缓存，哈希静态资源可长期缓存。

## Scope

包含：

- 创建 `application.yml`，精确绑定 TRD 14.1/附录 B 的七个环境变量，并冻结 Flyway、Tushare、持久化、查询和 Actuator 默认值；
- 创建 Servlet 条件下的应用装配配置，组装 `PluginRegistry`、候选/有效 adapter、经 Flyway 和真实 schema 校验的 `DatasetCatalog`、JDBC repository、事务持久化、下载和查询服务；
- 让 Tushare plugin 与 49 个 `GenericDatasetAdapter` 复用同一次 YAML 加载得到的不可变 `DatasetDefinition` 列表，同时继续收集 acceptance profile 下以普通 Bean 贡献的 fixture adapter；
- 创建 `TensorMetrics`，只为启动时已注册的插件/API 建立 TRD 17.3 五项指标和固定低基数标签；
- 创建 `OperationLogger`，显式包装下载和 records 查询，原样返回结果或重新抛出同一异常，只记录一次无秘密的最终完成事件；
- 最小修改下载与查询 Controller 接入包装，不改变 DTO、参数校验、服务编排、HTTP 路由或异常传播；
- 创建统一 Servlet 安全/缓存 Filter，覆盖 API、Actuator、错误与静态资源响应；
- 创建普通观测测试和显式 MySQL 8.4.6 生产上下文 IT，并最小调整因构造器或 Tushare 配置公共方法变化而受影响的既有测试；
- 执行严格 TDD、聚焦/回归/显式 IT、mutation、敏感信息、Actuator、JAR、范围、格式、Git 跟踪和 clean 门禁。

排除：

- 不修改 parent/app POM、OpenAPI、错误码目录、TRD/PRD、Core/plugin-api、Flyway migration、现有 DTO、`GlobalExceptionHandler` 或 `RequestIdFilter`；
- 不把观测逻辑放入 Core，不改变下载事务边界、查询语义、错误状态/摘要、requestId 生成或 Jackson 精度策略；
- 不记录参数值、筛选值、Token、Authorization、Cookie、数据库密码、原始响应、异常 message/cause、SQL、堆栈或内部路径；
- 不把 requestId、证券代码、参数/筛选值、错误文本或任意客户端字符串用作指标标签；
- 不增加 AOP、请求/响应 body 缓存、自动重试、Tushare 周期网络探测、应用内认证或远程遥测出口；
- 不实现 M10 前端、M13-T02 单 JAR 检查、M13-T03 CORS/SPA fallback/生产部署覆盖/优雅停机；不设置 HSTS，HTTPS 强制属于部署入口；
- 不默认公开 `/actuator/metrics`、`/actuator/env`、`/actuator/configprops`、beans、loggers、heapdump 或其他管理端点。

项目所有者于 2026-09-04 批准扩展原任务卡的五文件范围，以承接 M09-T02～T05 明确延期到本任务的生产 Bean 总装配，并批准显式 Controller 包装方案及受影响测试调整；该裁决不扩展上述功能边界。

## Approach

### 环境配置与管理端点

`application.yml` 固定为以下配置语义：

- `spring.datasource.url|username|password` 分别且仅从无默认值的 `TENSOR_DB_URL|TENSOR_DB_USERNAME|TENSOR_DB_PASSWORD` 读取；缺失必需数据库配置不得伪造内存库或匿名凭证；
- `spring.flyway.enabled=true`；数据集 schema 校验必须在 Flyway 数据库初始化之后执行；
- `tensor.display-zone=${TENSOR_DISPLAY_ZONE:Asia/Shanghai}`；
- `tensor.plugins.tushare-pro.enabled=${TENSOR_TUSHARE_ENABLED:true}`、`base-url=${TENSOR_TUSHARE_BASE_URL:https://api.tushare.pro}`、`token=${TENSOR_TUSHARE_TOKEN:}`，连接/读取超时保持既有 `5s`/`120s`，`max-response-bytes=67108864`；
- `tensor.persistence.batch-size=500`、`tensor.query.default-page-size=50`、`tensor.query.allowed-page-sizes=[20,50,100]` 只冻结 TRD 名称和值；本任务不复制已经由 M06/M09 固定的 repository 或 HTTP 分页行为；
- `management.endpoints.web.base-path=/actuator`、discovery 关闭且默认 exposure 只有 `health`；health probes 启用、components 始终显示、details 永不显示，数据库 health contributor 保持启用；
- 即使外部环境将 env/configprops 端点加入 exposure，`management.endpoint.env.show-values=never` 与 `management.endpoint.configprops.show-values=never` 仍禁止显示值。

因此默认可访问的管理路径只有 `/actuator/health`、`/actuator/health/liveness` 和 `/actuator/health/readiness`，`/actuator` discovery 也不开放。Micrometer 指标保存在应用 registry 中，但默认没有 HTTP 暴露；部署方未来只能通过受保护的外部配置显式增加采集通道。

### Servlet 生产 Bean 图

创建 `com.akkc.tensor.config.ApplicationConfiguration`，声明 `@Configuration(proxyBeanMethods = false)` 与 `@ConditionalOnWebApplication(SERVLET)`。非 Web smoke context 继续只验证 Boot 根、Filter 和插件本地配置，不要求数据库 Bean。

`TusharePluginConfiguration` 增加命名 Bean `tushareDatasetDefinitions`，唯一执行现有 `DatasetDefinitionLoader.loadAll(..., "classpath*:datasets/tushare_pro/*.yaml")` 并返回 49 项不可变列表；`tushareProPlugin` 改为注入该命名列表，不再自行重复加载。现有 `TushareProPlugin` 公共合同不变。

`ApplicationConfiguration` 按以下顺序和唯一实例关系组装：

1. `tensorDatasetAdapters` 命名 Bean 把 `tushareDatasetDefinitions` 映射为 49 个 `GenericDatasetAdapter`，每个使用新建的无状态 `ValueConverter` 和 `FingerprintKeyCodec`；随后追加 Spring 中普通 `DatasetAdapter` 扩展 Bean。production 只有 49 个 Tushare candidates，`acceptance + tensor.plugins.fixture.enabled=true` 时另有 fixture candidate；
2. `PluginRegistry` 接收 Spring 收集的 `List<DataSourcePlugin>`，保留 M05 的本地 readiness、重复隔离和不可变快照；插件构造和注册不得访问网络；
3. 标有 `@DependsOnDatabaseInitialization` 的 `DatasetCatalog` 从 candidate adapters 的 `definition()` 建立定义列表，并通过 `new DatasetStartupValidator(definitions, new SchemaInspector(dataSource)).validate()` 在 Flyway 后校验真实表；
4. `AdapterRegistry` 只接收 `DatasetCatalog.find(adapter.datasetKey()).isPresent()` 的 candidates。被 schema、定义或重复校验排除的数据集因此不会在下载前取得 adapter；
5. Boot 自动配置提供唯一 `DataSource`、`JdbcTemplate` 和 `PlatformTransactionManager`。配置据此创建 `ExistingKeyRepository`、`GenericUpsertRepository`、`DatasetLockManager`、`PersistenceService`、`ParameterValidator`、UTC `Clock`、`DownloadService`、`GenericQueryRepository` 和 `DatasetQueryService`；
6. 同一配置创建唯一 `TensorMetrics` 和 `OperationLogger` Bean，供两个 Controller 构造器注入。

所有集合在配置边界使用 `List.copyOf`。不得以反射访问 `DatasetCatalog`、建立第二个 catalog、让无效 adapter 绕过目录、把数据库事务包在上游调用外层，或为具体 API 写条件分支。

### 指标合同

`TensorMetrics` 是 `public final` 普通 Java 类，唯一公开构造器接收非 null `MeterRegistry` 和 `PluginRegistry`。构造期从 registry descriptor 快照冻结允许的 `DatasetKey` 集；运行时未知 key 直接跳过自定义业务指标，禁止把任意路径值变成新标签。

指标名称与标签精确为：

| 名称 | 类型 | 标签 |
|---|---|---|
| `tensor_download_total` | Counter | `plugin`, `api`, `outcome` |
| `tensor_download_duration_seconds` | Timer | `plugin`, `api`, `outcome` |
| `tensor_download_rows_total` | Counter | `plugin`, `api`, `kind` |
| `tensor_query_total` | Counter | `plugin`, `api`, `outcome` |
| `tensor_query_duration_seconds` | Timer | `plugin`, `api`, `outcome` |

`outcome` 闭集为 `success|empty|failure`；query 只使用 `success|failure`。`kind` 闭集为 `source|inserted|updated`。Timer 接收 `System.nanoTime()` 差值换算的非负 `Duration` 并以 seconds 为 base unit；一次已知操作只增加一个 total、一个 duration，下载成功或空结果还分别把三类非负行数加入 rows counter。失败不伪造行数，也不增加 rows counter。

`TensorMetrics` 只暴露 operation logger 所需的 `supports`、下载记录和查询记录方法；不暴露 registry、meter、凭证或任意标签入口。

### 单次完成日志与 Controller 接入

`OperationLogger` 是 `public final` 普通 Java 类，唯一公开构造器接收 `PluginRegistry` 与 `TensorMetrics`。它从同一 descriptor 快照建立 `DatasetKey -> 参数名原序` 白名单，提供两个公开包装方法：

```java
public DownloadResponse download(
        DatasetKey key,
        Map<String, Object> parameters,
        Supplier<DownloadResponse> operation);

public PageResponse query(
        DatasetKey key,
        List<String> filterNames,
        int requestedPage,
        int requestedPageSize,
        Supplier<PageResponse> operation);
```

包装器先确认 key 是启动快照中的已知插件/API。未知 key 不产生业务完成日志或指标，但必须原样执行 supplier，让既有 Controller/handler 返回批准的 400/409；该安全拒绝仍由 M09-T05 的全局日志覆盖。已知 key 使用单调时钟包围 supplier：成功时记录结果后返回同一对象；`RuntimeException` 时记录失败后重新抛出同一实例，不吞掉、不替换、不重试。

下载成功完成事件名固定为 `tensor.operation.completed`，字段为 `requestId, operation=download, pluginId, apiName, paramSummary, sourceRowCount, insertedRows, updatedRows, durationMs, outcome, failureStage, errorCode`。查询事件字段为 `requestId, operation=query, pluginId, apiName, filterNames, page, pageSize, resultCount, totalElements, durationMs, outcome, failureStage, errorCode`。使用 SLF4J 参数化 `key=value` 文本，不使用字符串拼接或 Throwable 参数。

`paramSummary` 只列 descriptor 中声明且本次 map 包含的参数名，保持 descriptor 原序；不记录任何值，未声明键和大小写包含 `token|authorization|cookie|password|credential` 的键不会出现。`filterNames` 只由 Controller 按 `ts_code,trade_date,ann_date` 固定顺序传入存在值的已批准筛选名；不记录证券代码或日期值。MDC 缺少 requestId 继续由现有 Controller 在调用包装器前失败。

失败 `outcome=failure`，`errorCode` 优先使用现有 `TensorException.code()`；下载中的 `DataAccessException|TransactionException` 固定为 `PERSISTENCE_FAILED`，其余非领域异常为 `INTERNAL_ERROR`；查询中的非领域异常固定为 `QUERY_FAILED`。`failureStage` 精确由 code/类型映射为 `parameter|registration|source|adapter|persistence|query|internal`。事件不携带异常对象、类名、message、cause 或 stack；M09-T05 已有脱敏诊断日志保持不变，因此每个操作只有一个 `tensor.operation.completed` 统计事件，不声称诊断日志被删除。

`DownloadController` 在已解析 `PluginId`、`ApiName` 和 MDC requestId 后创建 `DatasetKey`，以 logger 包装现有 `downloadService.execute` 与 `DownloadResponse.from`。`DatasetController` 在目录、筛选、`QueryCriteria` 和 MDC 校验后，以 logger 包装现有 service 调用、既有 `IllegalArgumentException -> DatasetQueryAccessException` 转换和 `PageResponse.from`。这保证 Bean Validation、非法路径和未知目录不会产生高基数业务指标，同时不改变任何已有 HTTP 结果。

### 健康、安全头与静态缓存

不创建 Tushare `HealthIndicator`，也不周期调用真实上游。Boot Actuator 的 JDBC health contributor 是唯一外部依赖健康输入：数据库在启动校验后中断时，根 health 为 `DOWN`/HTTP 503；Token 为空时 `TushareProperties.readiness()` 只令 plugin descriptor `downloadAvailable=false`，在数据库健康时根 health 保持 `UP`/HTTP 200。

`WebSecurityHeadersConfiguration` 声明 Servlet 条件配置，并以 `FilterRegistrationBean<OncePerRequestFilter>` 在 `RequestIdFilter` 后一位运行。Filter 在进入 chain 前对所有响应覆盖以下固定头：

- `Content-Security-Policy: default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'`；
- `X-Content-Type-Options: nosniff`；
- `X-Frame-Options: DENY`；
- `Referrer-Policy: no-referrer`；
- `Permissions-Policy: camera=(), microphone=(), geolocation=()`；
- `Cross-Origin-Opener-Policy: same-origin`。

缓存规则同样由该 Filter 按 context-relative request URI 设置：`/assets/**` 为 `public, max-age=31536000, immutable`；`/`、`/index.html`、`/api/**`、`/actuator/**` 为 `no-store`；其他响应为 `no-cache`。本任务不注册 SPA fallback 或 CORS；M13-T03 后续仍负责把未知前端路由映射到 `index.html`，并必须保留本规则。

### 直接依赖与约束比较

- M09-T01 提供 `TensorApplication`、最高优先级 `RequestIdFilter` 和公共响应 DTO；本任务只补 Servlet 配置，在 logger 中读取既有 MDC 值，安全 Filter 明确排在 requestId Filter 后，不生成第二个 ID；
- M09-T02 提供只依赖 `PluginRegistry`/`DatasetCatalog` 的元数据 Controller，并明确把真实 registry/catalog Bean 留给本任务；本配置提供相同不可变快照和经 schema 校验目录，不改变缺 Token 仍可查询元数据的 409/200 语义；
- M09-T03 提供线性 `DownloadService`、事务提交后 response 和下载 Controller，并明确把全部协作者生产装配留给本任务；本配置精确复用既有构造器，logger 只包围 Controller 调用，不移动事务或上游边界；
- M09-T04 提供只读 `DatasetQueryService`、records Controller 和精度 module，并明确把 catalog/JDBC/query Bean 留给本任务；本配置复用同一 catalog/repository，logger 不重算 page、totals、rows 或 JSON；
- M09-T05 提供唯一异常映射和脱敏诊断日志，并要求本任务验证完整 Filter/advice 注册；本任务不修改 handler，只增加无 Throwable 的一次性业务完成事件，错误 code/stage 映射与其冻结的持久化/查询路由分类一致。

五项直接输入职责互补且无冲突。它们共同留下的唯一缺口是 Servlet 生产装配和观测/健康/安全默认值；项目所有者已明确批准本任务扩展文件范围填补该缺口。

## Files

创建：

- `data-plane/tensor-app/src/main/resources/application.yml`：环境变量、Tushare/Core 默认值和仅 health 的 Actuator 策略；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`：Servlet 生产 Bean 图、候选/有效 adapter、Flyway 后 catalog 和 observability Bean；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/TensorMetrics.java`：五项低基数 Micrometer 指标；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/OperationLogger.java`：下载/查询完成包装、白名单摘要、安全结果和失败分类；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/WebSecurityHeadersConfiguration.java`：全响应安全头及缓存 Filter；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ObservabilityTest.java`：18 次无网络/数据库普通测试；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/ProductionApplicationContextIT.java`：固定 MySQL 8.4.6 的一次完整 Servlet 生产上下文测试。

修改：

- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TusharePluginConfiguration.java`：增加唯一命名 definition 列表 Bean，plugin 注入同一列表；
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/TushareProPluginTest.java`：把配置公共面与本地 context 断言更新为两个 Bean 方法和同一 49 项列表；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`：构造注入并调用下载 operation wrapper；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java`：构造注入并调用查询 operation wrapper；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java`：为 standalone Controller 提供空白名单的真实 logger，不改变既有十项场景；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java`：为 standalone Controller 提供空白名单的真实 logger，不改变既有八项场景。

不修改或删除其他文件。实现提交消息固定为 `feat(app): add safe configuration and observability`，提交精确包含上述 13 个文件；设计、计划、交接、看板、`target/` 或临时日志不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前在允许 Mockito/Byte Buddy self-attach 的环境运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 61，共 320/320，0 failure、0 error、0 skipped；六层 Enforcer、app ArchUnit 和禁止 Git 能力门禁通过。

严格 RED 时先完整创建 `ObservabilityTest` 与 `ProductionApplicationContextIT`，但不创建四个新生产 Java 类型，不修改生产配置/Controller。运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=ObservabilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期依赖解析和既有 main 编译成功，随后只在 `tensor-app:testCompile` 因 `ApplicationConfiguration`、`TensorMetrics`、`OperationLogger`、`WebSecurityHeadersConfiguration` 四个目标类型不存在而退出非 0。测试语法、Spring/Micrometer API、既有代码或额外缺失依赖不是有效 RED。

### 普通 GREEN：18 次执行

完成最小生产实现和三个受影响既有测试调整后，重跑相同聚焦命令，`ObservabilityTest` 固定 18/18：

1. download success 形成唯一完成事件，并各增加一次 total/duration 与 source/inserted/updated 行数；
2. download empty 使用 `outcome=empty`、三项零计数且不伪造成功行；
3. 六次参数化 failure 分别证明 parameter、registration、source、adapter、persistence、internal stage/code，supplier 异常实例原样抛出；
4. query success 记录唯一 total/duration、固定 filterNames、规范 page/pageSize、resultCount 和 totalElements；
5. query 非领域失败记录 `outcome=failure`、`failureStage=query`、`QUERY_FAILED` 并原样抛出；
6. 参数 map 中 Token/Authorization/Cookie/password/credential 键、所有参数值及异常 message/cause 哨兵均不出现在 captured event；
7. 未知 DatasetKey 仍执行 supplier，但不创建 meter 或 `tensor.operation.completed`；
8. registry 中所有 meter 名、类型、tag key/value 均属于五项合同和闭集，不出现 requestId、参数/筛选值、错误文本或任意额外 tag；
9. 两次参数化路径（API 与静态资源）都获得六个精确安全头；
10. 两次参数化缓存路径证明 `index.html` 为 `no-store`、`assets` 为一年 public immutable；
11. YAML 加载结果精确包含七个环境变量占位符、固定默认值、health-only exposure、never-show-values，且不含实际 Token/密码。

上述计数为 1 + 1 + 6 + 1 + 1 + 1 + 1 + 1 + 2 + 2 + 1 = 18。测试使用 `SimpleMeterRegistry`、Logback `ListAppender`、Spring mock servlet 对象和测试内最小 plugin；不使用 Mockito、Docker、网络、真实 Token、wall clock 或 sleep。

随后运行现有受影响聚焦回归：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-plugin-tushare,tensor-app -am \
  -Dtest=TushareProPluginTest,DownloadControllerIT,DatasetControllerIT,GlobalExceptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期 Tushare 8/8、app 43/43，共 51/51，0 failure、0 error、0 skipped。Tushare 配置测试必须证明 plugin descriptor 的 49 个 API/dataset key 与命名 Bean 的 49 项 definition 精确对应；两个 Controller IT 原有 10/8 项行为和全局 handler 25 项保持不变。

### 完整生产上下文 IT

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=ProductionApplicationContextIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

测试使用唯一 `mysql:8.4.6` Testcontainer 和 `server.port=0`，不访问 Tushare 网络。一个测试执行依次完成：

1. 以 DB URL/username/password 哨兵、production profile 和空 Token 启动完整 Servlet app；断言 Flyway 六项迁移、唯一 registry/catalog/services/metrics/logger、49 个 Tushare definitions 与 49 个有效 adapters、三个 Controller、request/security Filter 和全局 advice 均存在；
2. 断言 Tushare descriptor 为 enabled、credentialConfigured false、downloadAvailable false；`/actuator/health` 为 200/UP 且显示 db component，不出现 JDBC URL、用户名、密码、Token 或配置值；
3. 断言 `/api/v1/data-sources` 仍返回安全插件摘要和 request/security headers；`/actuator`、`/actuator/env`、`/actuator/configprops`、`/actuator/metrics` 均不可访问且响应不含秘密；
4. 关闭首个 context，再以同一数据库和非空 Token 哨兵启动第二个 context，扫描启动日志、health 和元数据 JSON 均不含 Token/密码；
5. 停止 MySQL 后在固定短 Hikari connection timeout 下调用 health，断言 503/DOWN；不把 Tushare 网络纳入探测，也不因插件 Token 状态改变数据库结论。

最终预期 `ProductionApplicationContextIT` 1/1，0 failure、0 error、0 skipped。与 schema 合同联跑：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=FlywaySchemaContractIT,ProductionApplicationContextIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期固定 MySQL 8.4.6 上 53/53，0 failure、0 error、0 skipped；schema 52 项与生产上下文 1 项都不可由普通测试替代。

### Mutation、reactor 与安全门禁

至少执行以下受控 mutation，每次只改一个行为、运行 `ObservabilityTest`，确认出现预期失败后恢复并重跑 18/18：

1. 将未知 key 也注册指标，预期低基数/额外 meter 测试失败；
2. 把任一参数值或异常 message 放入完成日志，预期秘密扫描测试失败；
3. 删除任一 download rows `kind` 或改写 metric 名/tag，预期指标 schema/success 测试失败；
4. 让 wrapper 包装新异常或吞掉异常，预期六项 failure/query failure 的 identity 断言失败；
5. 把 `/actuator/metrics` 加入默认 exposure 或把 health details 改为 always，预期 YAML/生产上下文安全测试失败。

运行默认 reactor：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

两条命令均预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 79，共 338/338，0 failure、0 error、0 skipped；六层 Enforcer、ArchUnit 和禁止 Git 能力门禁通过。显式 `*IT` 不进入默认 Surefire 计数。

执行静态、JAR、秘密和范围门禁：

```bash
rg -n 'tensor_(download|query)_(total|duration_seconds|rows_total)|plugin|api|outcome|kind' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/observability
rg -n 'Authorization|Cookie|password|credential|\.value\(\)|getMessage|printStackTrace|Throwable|addTag|tag\("(requestId|ts_code|error)' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/observability
rg -n 'TENSOR_DB_(URL|USERNAME|PASSWORD)|TENSOR_TUSHARE_(TOKEN|ENABLED|BASE_URL)|TENSOR_DISPLAY_ZONE|exposure|show-values' \
  data-plane/tensor-app/src/main/resources/application.yml
rg -n 'env|configprops|heapdump|loggers|prometheus' \
  data-plane/tensor-app/src/main/resources/application.yml
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'application.yml|com/akkc/tensor/(config/(ApplicationConfiguration|WebSecurityHeadersConfiguration)|observability/(TensorMetrics|OperationLogger)).*'
git diff --quiet -- \
  docs data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-core data-plane/tensor-plugin-api data-plane/tensor-plugin-fixture \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto \
  data-plane/tensor-app/src/main/resources/db
git diff --check
git status --short --untracked-files=all -- data-plane
```

第一项命中五个授权指标及固定标签；第二项只允许命中代码中的禁止键名白名单，不得命中取值、异常消息/Throwable 或动态标签 API；配置扫描命中七个变量和安全端点策略，禁止端点扫描只能命中 `show-values` 防御配置中的 `env/configprops` 路径键，不得出现在 exposure include；JAR 命中四个新类及 YAML；受保护路径和格式退出 0。提交前 scoped status 精确显示 Files 节的 13 个文件，所有新增文件均已加入 Git，且没有 `target/` 或临时日志。

实现提交后运行：

```bash
git show --stat --oneline --summary HEAD
git show --format= --name-status HEAD
git diff --check HEAD^ HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
```

固定提交消息和 13 文件范围精确匹配；clean 退出 0；最终工作树为空且不存在 `target/`。clean 后必须重新运行显式生产上下文 1/1，再运行默认 reactor `verify` 338/338，作为提交态最终证据。

## Acceptance

- 有效 MySQL 配置下，完整 production Servlet context 在 Flyway 后建立唯一 plugin/adapter/catalog/JDBC/transaction/download/query/observability Bean 图，49 个 Tushare definition 与 adapter 全部通过真实 schema 校验；非 Web smoke 不被数据库装配破坏；
- `application.yml` 只引用 TRD 附录 B 七个环境变量，不含真实秘密；数据库三项无默认值，Tushare Token 可空，既有超时/大小和 Core 分页/批次默认值保持冻结；
- 缺 Token 时 Tushare 只表现为 download unavailable，元数据/既有数据查询和根 health 仍可用；MySQL 中断后根 health 为 DOWN/503；不探测真实 Tushare 网络；
- 默认只公开 health/liveness/readiness，Actuator discovery 关闭；env、configprops、metrics 及其他管理端点不可访问；日志、health 和响应不包含 Token/密码，health 和响应也不包含 JDBC URL 或配置值；
- 五项指标名、meter 类型、tag key 与 outcome/kind 闭集精确符合 TRD 17.3；只有启动快照中的插件/API 产生指标，没有 requestId、证券代码、参数/筛选值、错误文本或任意客户端标签；
- 每个已知 download/query 只产生一个 `tensor.operation.completed`，成功计数与 response 一致，失败 stage/code 稳定；日志只含白名单参数/筛选名，不含值、原异常/cause/stack/SQL/秘密，supplier 的结果和异常 identity 不变；
- API、Actuator、错误与静态资源响应具有六个固定安全头；`/assets/**` 一年 public immutable，`/`/`index.html`/API/Actuator no-store，其他 no-cache；不提前实现 CORS 或 SPA fallback；
- 严格 RED 只来自四个缺失生产类型；普通 18/18、受影响回归 51/51、显式生产上下文 1/1、schema 联跑 53/53、默认 reactor test/verify 338/338、五项 mutation、Enforcer/ArchUnit/禁止 Git/JAR/秘密/Actuator/范围/格式/跟踪/clean 门禁均得到预期结果；
- 实现提交消息为 `feat(app): add safe configuration and observability` 且精确包含 Files 节 13 个文件，不混入 POM、合同、Core、迁移、GlobalExceptionHandler、RequestIdFilter、DTO、后继任务、生成物或临时日志。

## Risks

- `tushareDatasetDefinitions` 是 Spring 命名的 `List<DatasetDefinition>` Bean；app 装配必须使用 qualifier，普通 `List<DatasetAdapter>` 扩展仍只收集独立 adapter Bean，避免 Spring 泛型集合解析歧义。生产上下文 IT 会验证最终恰有 49 个有效 Tushare adapters。
- `@DependsOnDatabaseInitialization` 把 catalog schema 校验排在 Flyway 后；数据库在启动时不可用会使应用无法完成上下文，而已启动数据库随后中断则由 health 报 DOWN。这与 TRD “不对外提供业务接口”一致。
- 完成事件使用 SLF4J `key=value` 文本而非外部 JSON encoder；字段集合稳定且可采集，不新增日志依赖。未来若部署平台要求特定 JSON schema，应独立增加 encoder 配置并保持本设计的字段与脱敏边界。
- 默认不通过 HTTP 暴露业务 metrics，以满足生产仅开放必要健康端点。部署方需要采集时必须在受保护网络/代理后显式覆盖 exposure；不得顺带开放 env/configprops。
- CSP 不允许 inline script/style。M10～M13 前端构建和 SPA fallback 必须保持外部哈希资源；若将来工具链生成不可消除的 inline 内容，应先独立评审 nonce/hash，而不是加入 `unsafe-inline`。
- 显式生产上下文 IT 连续启动两个 context 并在末尾停止 MySQL，成本高于普通测试，因此继续以 `*IT` 显式运行；默认 338 项回归不能替代该证据。
