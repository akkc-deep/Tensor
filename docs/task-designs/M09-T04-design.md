# M09-T04 数据集定义与只读分页查询 API——任务设计

任务编号：`M09-T04`
对应任务：[M09-T04](../superpowers/plans/tensor-modules/M09-app-api.md#task-m09-t04-数据集只读分页-api30h)
实施产物：`DatasetController`、`PageResponse`、`JacksonPrecisionConfiguration` 和 `DatasetControllerIT`

## Goal

在 `tensor-app` 暴露与 OpenAPI 一致的数据集 records 只读分页端点：把固定 HTTP 查询参数映射为 M06 的 `QueryCriteria`，只查询 M05 启动校验通过的 `DatasetCatalog` 定义，并把 M06 已完成的空结果、COUNT-first、稳定排序和超界页归一结果原样投影为带请求标识的数据页。REST 边界必须把业务行中的 DECIMAL/BIGINT 精确值输出为十进制字符串，同时保持页码和总数为 JSON number，避免 JavaScript 精度损失且不改变 Core 类型合同。

## Scope

包含：

- 创建 `DatasetController`，只提供 `GET /api/v1/data-sources/{pluginId}/datasets/{apiName}/records`；
- 同时注入 `DatasetCatalog` 与 `DatasetQueryService`，先建立可观察的元数据 409 边界，再调用查询服务；
- 接收 `tsCode`、`tradeDateFrom/To`、`annDateFrom/To`、`page`、`pageSize`，缺省页码映射为 1/50；
- 拒绝未由目标数据集声明的筛选、Core 已冻结的非法条件和值，以及查询层不支持的筛选元数据；
- 创建深不可变 `PageResponse`，保留 Core 页面的列顺序、行键顺序、null 和规范分页值；
- 创建 Jackson module Bean，只将 boxed `Long` 与 `BigDecimal` 输出为十进制字符串，其中 `BigDecimal` 固定使用 `toPlainString()`；
- 使用真实 MySQL 8.4.6、真实启动校验目录和真实查询服务完成八项 Controller 集成测试；
- 执行严格 RED/GREEN、两项受控 mutation、聚焦/联跑/reactor 回归、Enforcer、ArchUnit、禁止 Git、JAR、只读路由、范围、格式、Git 跟踪和清理门禁。

排除：

- 不修改 `DatasetCatalog`、`QueryCriteria`、`DatasetPage`、`QuerySqlFactory`、`GenericQueryRepository`、`DatasetQueryService` 或其他 Core/plugin 类型；
- 不修改 OpenAPI、POM、YAML、Flyway migration、资源、现有 Controller/DTO/测试或其他模块；
- 不创建或装配生产 `DataSource`、`JdbcTemplate`、目录、查询服务或插件 Bean；真实生产装配仍属于 M09-T06；
- 不实现 M09-T05 的 `@RestControllerAdvice`、标准 `ApiErrorResponse`、字段错误、日志或完整 HTTP 异常矩阵；
- 不提供客户端排序、任意筛选字段、任意表/列、SQL、导出、流式响应、缓存、写入或 records 的 POST/PUT/PATCH/DELETE；
- 不把 Core 行值提前转为字符串，不把 `Long`/`BigDecimal` 转为 `double`/`float`，不把分页控制字段字符串化；
- 不重新实现 COUNT、总页数、超界页、稳定排序、明确列或数据库类型读取逻辑。

## Approach

### Controller 与 DTO 公共表面

`DatasetController` 是 `com.akkc.tensor.web` 下的 `public final` 类，声明 `@RestController`、`@RequestMapping("/api/v1/data-sources")` 和 `@ConditionalOnWebApplication(type = SERVLET)`。唯一公开构造器同时接收且拒绝 null 的 `DatasetCatalog`、`DatasetQueryService`；不使用字段注入、`ObjectProvider`、可选依赖、具体插件或 JDBC 类型。

唯一公开业务方法为：

```java
@GetMapping("/{pluginId}/datasets/{apiName}/records")
public PageResponse listDatasetRecords(
        @PathVariable("pluginId") String pluginId,
        @PathVariable("apiName") String apiName,
        @RequestParam(value = "tsCode", required = false) String tsCode,
        @RequestParam(value = "tradeDateFrom", required = false)
                @DateTimeFormat(iso = ISO.DATE) LocalDate tradeDateFrom,
        @RequestParam(value = "tradeDateTo", required = false)
                @DateTimeFormat(iso = ISO.DATE) LocalDate tradeDateTo,
        @RequestParam(value = "annDateFrom", required = false)
                @DateTimeFormat(iso = ISO.DATE) LocalDate annDateFrom,
        @RequestParam(value = "annDateTo", required = false)
                @DateTimeFormat(iso = ISO.DATE) LocalDate annDateTo,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "pageSize", defaultValue = "50") int pageSize);
```

参数名精确使用 OpenAPI camelCase。Spring 的 ISO `LocalDate` 绑定负责拒绝不存在的日期或非 `YYYY-MM-DD` 文本；整数绑定失败同样由 MVC 返回 400，M09-T05 后续统一形成 `PARAM_INVALID` 错误体。进入方法后的业务校验使用下述私有 `TensorException` 边界。

`PageResponse` 是 `com.akkc.tensor.web.dto` 下的 public record，组件和顺序固定为：

```java
public record PageResponse(
        String requestId,
        String pluginId,
        String apiName,
        int page,
        int pageSize,
        long totalElements,
        long totalPages,
        List<String> columns,
        List<Map<String, Object>> items) {
    public static PageResponse from(String requestId, DatasetKey key, DatasetPage page);
}
```

compact constructor 拒绝 null；三个文本身份必须非 blank。构造器以相同六个页面组件创建一个 `DatasetPage`，复用其列非空/唯一、行键精确有序、null 值允许、深不可变和分页数值不变量，然后保存该验证副本的 `columns/items`，不复制 Core 规则。`from` 拒绝 null，把 `DatasetKey` 值和 `DatasetPage` 的规范结果逐项投影；不重新计算 totals、重排列/行或改变值类型。

### 元数据、参数和查询数据流

Controller 按以下固定顺序执行：

1. 用路径文本创建 `PluginId`、`ApiName` 和 `DatasetKey`；非法标识符转换为 `400 + PARAM_INVALID`；
2. 调用 `DatasetCatalog.find(key)`；不存在立即抛 `409 + DATASET_MISCONFIGURED`，不得调用查询服务；
3. 从找到的不可变 definition 读取声明的 filter field 集合；若包含 `ts_code`、`trade_date`、`ann_date` 之外的字段，视为查询层无法安全消费的元数据，立即抛 `409 + DATASET_MISCONFIGURED`；
4. 非 null `tsCode` 要求声明 `ts_code`；任一 trade date 参数要求声明 `trade_date`；任一 ann date 参数要求声明 `ann_date`。未声明则抛 `400 + PARAM_INVALID`，不得访问数据库；
5. 创建 `QueryCriteria(tsCode, tradeDateFrom, tradeDateTo, annDateFrom, annDateTo, page, pageSize)`；构造器的格式、日期关系和分页 `IllegalArgumentException` 转为 `400 + PARAM_INVALID`；
6. 从 `MDC.get(RequestIdFilter.MDC_KEY)` 取得请求 ID；缺失时抛固定 `IllegalStateException("Request ID is unavailable")`，不得执行查询；
7. 调用 `DatasetQueryService.query(key, criteria)`。同一个不可变 catalog 已在步骤 2 验证 key；若其仍以 `IllegalArgumentException` 报告目录/查询前置条件漂移，转换为 `409 + DATASET_MISCONFIGURED`，不按异常消息分支；
8. 以 `PageResponse.from(requestId, key, page)` 返回结果。

Controller 不检查插件 download readiness。只要 dataset 已进入启动校验目录，即使插件禁用或缺 Token，已有数据仍可只读查询，与 M09-T02 既定语义一致。

### 失败边界

Controller 使用两个 private static final `TensorException` 子类，不增加公共异常类型：

- `InvalidQueryException` 声明 `@ResponseStatus(HttpStatus.BAD_REQUEST)`，固定 `ErrorCode.PARAM_INVALID` 与安全消息 `Query parameters are invalid`；
- `DatasetQueryAccessException` 声明 `@ResponseStatus(HttpStatus.CONFLICT)`，固定 `ErrorCode.DATASET_MISCONFIGURED` 与安全消息 `Dataset metadata is unavailable`。

不得把路径、SQL、表名、列值、异常 cause、数据库信息或请求原值拼入错误消息。Spring 参数绑定错误保持 MVC 400；数据库 `DataAccessException`、COUNT 结构异常和其他运行时查询失败不在 Controller 中捕获、包装或记录，保留给 M09-T05 统一映射为 `QUERY_FAILED`。本任务的独立 MockMvc 测试只断言状态和 resolved `TensorException`；不伪造尚未交付的标准错误 JSON。

### Jackson 精度边界

`JacksonPrecisionConfiguration` 位于 `com.akkc.tensor.web`，是声明 `@Configuration(proxyBeanMethods = false)` 的 `public final` 类。它只公开一个 `@Bean Module precisionModule()`，返回命名的 `SimpleModule`：

- 为 `Long.class` 注册字符串 serializer，但不为 primitive `long.class` 注册；
- 为 `BigDecimal.class` 注册 serializer，调用 `JsonGenerator.writeString(value.toPlainString())`；
- 不替换 Boot 的 `ObjectMapper`，不改变 `Integer`、primitive `long`、日期、时间、String、null、enum 或其他类型的默认序列化。

`DatasetPage.items` 声明为 `Map<String,Object>`，运行时 BIGINT 值是 boxed `Long`，因此输出 JSON string；DECIMAL 值是 `BigDecimal`，同样输出 string。`PageResponse.page/pageSize` 是 primitive `int`，`totalElements/totalPages` 是 primitive `long`，因此继续输出 JSON number。当前 app 没有其他 boxed `Long`/`BigDecimal` 响应组件；测试同时锁定业务值字符串、`1E+3 -> "1000"` 和四个分页字段的 number node。

### 直接依赖与约束比较

- M05-T02 的 `DatasetCatalog`/`DatasetStartupValidator` 只暴露定义关系与实际 schema 验证通过的数据集；Controller 直接使用 `find` 建立稳定的 409 边界，不根据 `DatasetQueryService` 的异常文本猜测不存在原因。
- M06-T06 的 `DatasetQueryService.query(DatasetKey, QueryCriteria)` 返回深不可变 `DatasetPage`，负责 COUNT-first、空结果、超界归一、明确列、稳定排序及 `BigDecimal`/`Long`/`LocalDate`/`Instant` 类型保真；本任务只做 HTTP 输入和 JSON 输出边界。
- M09-T01 的 `RequestIdFilter` 保证请求期间 MDC `requestId` 与响应头一致，并提供 Servlet 条件注册与通用 DTO 先例；本任务复用同一 MDC key，不产生第二个请求 ID。

三项输入互补且无冲突。原看板只列 M06-T06、M09-T01，但方案直接调用 `DatasetCatalog.find`，因此项目所有者已批准把 M05-T02 补为直接依赖。

## Files

创建：

- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java`：只读 records 路由、catalog/筛选/criteria/MDC 边界和查询调用；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/PageResponse.java`：带请求/数据集身份的深不可变规范页面投影；
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java`：boxed BIGINT 与 DECIMAL 十进制字符串 module Bean；
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java`：真实 MySQL、目录、查询服务、MockMvc 和 JSON 精度集成测试。

不修改或删除其他文件。实现提交只暂存上述四个新增 Java 文件，固定消息为 `feat(api): expose read-only dataset paging`；设计、计划、交接、看板、POM、现有 Java、资源、其他模块、临时日志和 `target/` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，共 295/295，0 failure、0 error、0 skipped，六层 Enforcer、app ArchUnit 与禁止 Git 能力门禁通过。

随后只完整创建 `DatasetControllerIT.java`，不创建三个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DatasetControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `DatasetController`、`PageResponse`、`JacksonPrecisionConfiguration` 不存在而在 `tensor-app:testCompile` 非零；不得因测试语法、依赖、Docker、上游无匹配测试、日期模块或环境配置形成伪 RED。

### 固定 MySQL 8.4.6 八项 GREEN

测试使用现有 Testcontainers/MySQL/JUnit/AssertJ/Spring Test/Jackson 依赖和唯一 class container `mysql:8.4.6`，不修改 POM，不使用 H2/JPA，不启动完整 Boot context。测试创建唯一 `fixture__query_records` 表，定义六个原序业务列 `ts_code STRING`、`trade_date DATE`、`ann_date DATE`、`amount DECIMAL(38,18)`、`volume LONG`、`note STRING nullable`，随后固定三个来源列，以 `ts_code,trade_date,ann_date` 为 COMPOSITE 主键。默认 definition 声明 `ts_code,trade_date,ann_date` 三个 filters；同表 definition 变体只改变 filters，用于未声明和不安全元数据场景。每个 catalog 都必须通过真实 `DatasetStartupValidator`/`SchemaInspector` 创建，不反射或绕过 package-private 构造器。

MockMvc 使用真实 `DatasetController`、`DatasetQueryService`、`GenericQueryRepository`、`RequestIdFilter`，以及注册生产 precision module 和 Java time module 的 `ObjectMapper`。窄 delegating DataSource 只记录连接获取次数，setup/启动校验后归零，用于证明边界失败不访问数据库；它不改变 SQL 或结果。创建最小生产实现后重跑聚焦命令，`DatasetControllerIT` 固定恰有八个普通 `@Test`，8/8 通过：

1. 反射确认 Controller/config final、Controller 唯一双依赖构造器和唯一 GET 方法、PageResponse 精确九组件与唯一 `from` 工厂、Servlet guard；直接构造 DTO 验证 null/blank、页面不变量、行/列表深复制和不可修改；precision module 验证 boxed `Long`、`BigDecimal("1E+3")` 为字符串且 primitive 控制值仍为 number；
2. 空表且不传 query 参数时返回 200、相同 `X-Request-Id`/body requestId、`page=1,pageSize=50,totalElements=0,totalPages=0`、定义原序六业务列加三个来源列及空 items；
3. 插入 101 行后无筛选请求分别使用 pageSize 20/50/100，均返回正确 totals、页大小、行数和复合键稳定顺序；
4. 多行数据上同时提交 `tsCode`、trade date 闭区间和 ann date 闭区间，只返回 AND 后匹配行；单边日期仍由 Core 既定绑定语义覆盖，columns 与 row keys 精确同序；
5. 使用只声明 `ts_code` 的同表 definition，trade/ann 请求被拒绝；另依次验证非法 tsCode、反向 trade/ann 日期、page=0、pageSize=10，均为 `400 + PARAM_INVALID` 且记录连接数保持零；非法 ISO 日期和非整数页由 MVC 返回 400；
6. 空 catalog 的未知 key 与声明 `note` filter 的同表不安全 definition 均为 `409 + DATASET_MISCONFIGURED`，固定安全消息且记录连接数保持零；缺失数据集即使同时带非法分页也保持 catalog-first 的 409；
7. 插入 23 行后请求 page=99/pageSize=20，响应归一到 page=2、返回最后 3 行；其中最大 BIGINT `9223372036854775807` 与高精度 DECIMAL 按精确十进制字符串输出，日期/时间为字符串，null 保留，page/pageSize/totals 四项仍为 JSON number；
8. 对同一路径执行 POST、PUT、PATCH、DELETE 均为 405，连接数保持零；Controller 生产代码没有其他 mapping 或写入协作者。

### 联跑、mutation 与回归门禁

运行真实主闭环联跑：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am \
  -Dtest=DatasetControllerIT,DownloadControllerIT,FixtureFlowIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期 `DatasetControllerIT` 8、`DownloadControllerIT` 10、`FixtureFlowIT` 5，共 23/23，0 failure、0 error、0 skipped。三类测试各自使用固定 MySQL 8.4.6，证明新 Jackson module/Controller 不破坏下载及 fixture 既有闭环。

受控 mutation A：临时绕过 Controller 的 catalog-first/不安全 metadata 检查，重跑第 6 项，预期 409、错误码或零数据库访问断言失败；恢复源码后通过。受控 mutation B：临时移除 boxed `Long` serializer、把 BigDecimal 改用普通 `toString()`，或额外为 primitive `long` 注册字符串 serializer，重跑第 1/7 项，预期业务精度字符串、plain notation 或 control number 断言失败；恢复源码后通过。mutation 不提交。

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

`DatasetControllerIT` 按 Maven 默认命名不进入普通 Surefire 扫描，因此两条命令仍预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，共 295/295，0 failure、0 error、0 skipped；六层 Enforcer、app ArchUnit 和禁止 Git 能力门禁通过。显式 8/8 与 23/23 是不可替代的真实 MySQL 证据。

运行静态、JAR、范围、格式、Git 跟踪和清理门禁：

```bash
rg -n '@RestController|@GetMapping|DatasetCatalog|DatasetQueryService|QueryCriteria|PARAM_INVALID|DATASET_MISCONFIGURED|SimpleModule|Long\.class|BigDecimal\.class|toPlainString' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java
rg -n '@(Post|Put|Patch|Delete)Mapping|JdbcTemplate|SELECT |INSERT |UPDATE |DELETE |setObject|getObject|doubleValue|floatValue|(?i:token|credential)' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/PageResponse.java
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/web/(DatasetController|JacksonPrecisionConfiguration|dto/PageResponse)\.class'
git diff --quiet -- \
  data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/main/resources \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DataSourceResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DatasetDefinitionResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/db \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DataSourceControllerTest.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RequestIdFilterTest.java \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-fixture data-plane/tensor-plugin-tushare
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/PageResponse.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am clean
```

授权扫描命中设计规定的 Controller/catalog/service/criteria/error/precision 符号；禁用扫描无输出并退出 1；JAR 至少精确命中三个顶层生产类型；受保护路径和格式退出 0。提交前 scoped status 精确列出四个新增文件且都已加入 Git，无 `target/`。提交后 `git show --stat --oneline --summary HEAD` 必须显示固定消息和精确四文件实现范围，最终工作树干净。

## Acceptance

- 唯一 records GET 路由、camelCase 参数、默认 1/50 和 PageResponse 九组件与 OpenAPI 一致；不存在 records 写路由；
- Controller 同时注入 catalog/service，catalog-first 区分不存在/不安全元数据的 `409 + DATASET_MISCONFIGURED`，不按异常消息猜测且不访问数据库；
- 未声明筛选、非法 tsCode/日期关系/page/pageSize 为 `400 + PARAM_INVALID`，MVC 类型/日期绑定失败为 400，所有边界失败不执行查询；
- 查询成功只复用 M06 的 COUNT-first、空结果、稳定排序和超界规范页面，不重复 SQL、页数或列逻辑；
- PageResponse 保留请求 ID、数据集身份、列/行顺序、null、页面不变量和深不可变性，MDC 缺失不访问数据库；
- 业务行 boxed BIGINT/DECIMAL 使用无损十进制 JSON string，BigDecimal 不使用科学计数法；page/pageSize/totalElements/totalPages 保持 JSON number；
- 数据库查询失败不泄漏或被 Controller 吞掉，统一 `QUERY_FAILED` 错误体明确留给 M09-T05；生产 Bean 总装配明确留给 M09-T06；
- 严格 RED 只来自三个缺失生产类型；GREEN 聚焦 8/8、主闭环联跑 23/23、默认 reactor `test`/`verify` 295/295、两项 mutation、Enforcer、ArchUnit、禁止 Git、JAR、只读、范围、格式、跟踪和 clean 门禁得到预期结果；
- 实现提交只包含 Files 节四个新增 Java 文件，消息为 `feat(api): expose read-only dataset paging`，未修改 POM、OpenAPI、Core、plugin、迁移、配置、既有 app 文件或其他任务。

## Risks

- Jackson module 按运行时/声明类型全局处理 boxed `Long` 与 `BigDecimal`。当前 app 响应没有业务行之外的这两类 boxed 组件，分页控制量均为 primitive；本任务用 JSON node 类型锁定现状。未来若增加 boxed `Long`/`BigDecimal` 控制字段，必须重新评估或收窄 serializer 作用域。
- M06-T06 的 COUNT 与 page 查询不在同一数据库快照；并发写入可能造成短暂的不一致。本任务不增加长事务或改变既定性能边界。
- `ingested_at` 继续依赖 M06-T06 的 UTC JDBC 读取；本任务使用 Jackson Java time 默认 ISO 输出，不提前裁决 M09-T06 的部署时区展示配置。
- `DatasetControllerIT` 不进入默认 Surefire；显式 MySQL 8.4.6 的 8/8 与联跑 23/23 不能由 reactor 295/295 替代。
- 当前生产代码尚未装配真实 `DatasetCatalog`/`DatasetQueryService` Bean。独立 MockMvc 加真实查询协作者证明本任务边界，不代表完整生产 Servlet context 已可启动；M09-T06 必须完成装配与启动验证。
