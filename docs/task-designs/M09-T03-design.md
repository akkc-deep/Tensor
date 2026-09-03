# M09-T03 同步下载 API 与事务提交后结果——任务设计

- **任务编号：** `M09-T03`
- **对应任务：** [M09-T03](../superpowers/plans/tensor-modules/M09-app-api.md#task-m09-t03-同步下载-api30h)
- **实施产物：** `tensor-core` 下载编排服务、`tensor-app` 下载 Controller/请求响应 DTO，以及真实 fixture/MySQL 集成测试

## Goal

交付 `POST /api/v1/downloads` 的同步下载闭环：服务端按已注册插件和已验证 API 元数据完成参数准入，在数据库事务外调用上游和执行适配，仅把预查与批量 Upsert 放入 M06-T04 的单事务边界，并且只在该事务提交后形成 `SUCCESS` 响应。合法空数据返回 `EMPTY` 和三个零计数；任一参数、来源、适配或持久化失败都停止后续阶段，不形成成功结果。

该闭环必须保持请求 ID、插件/API 身份、上游原始行数、去重后的插入/更新数和统一批次时间，并通过 acceptance-only fixture、固定 MySQL 8.4.6、真实 Controller/Filter/核心服务证明成功、空、失败和回滚语义。

## Scope

包含：

- 创建 `DownloadService`，使用已批准的 `PluginRegistry`、`AdapterRegistry`、`ParameterValidator`、`PersistenceService` 和 `Clock` 完成线性同步编排；
- 沿用路线图已冻结的 `execute(PluginId, ApiName, Map<String,Object>, RequestId)` 公开签名；
- 在调用插件前完成插件、API、适配器可用性和参数语义校验，禁止在已有数据库事务内启动下载；
- 对插件成功包络执行身份/参数一致性校验，合法零行直接返回 `EMPTY`，非空数据使用同一注入时钟生成唯一 `ingestedAt` 后适配、持久化；
- 创建 `DownloadController`，实现 `POST /api/v1/downloads`，只做请求 DTO、值对象、MDC 请求 ID 和响应 DTO 映射；
- 创建与 OpenAPI 精确对齐的 `DownloadRequest`、`DownloadResponse`，保持请求参数快照和响应字段顺序；
- 创建 `DownloadControllerIT`，使用真实 fixture、固定 MySQL、Flyway、`RequestIdFilter`、Controller、核心服务和持久化链路覆盖任务卡失败矩阵；
- 执行严格 TDD、定向集成测试、完整 reactor 回归、Enforcer、ArchUnit、禁止 Git 能力、JAR、静态、范围、格式、Git 跟踪和清理门禁。

排除：

- 不修改 POM、OpenAPI、错误码目录、M09-T01/M09-T02 既有代码、plugin-api、既有 core/plugin/fixture 生产代码、YAML 或 Flyway；
- 不创建额外生产配置或 Bean 装配文件，不修改 `TensorApplication`，不把 49 个 Tushare adapters、registries、catalog、JDBC services 装配为生产 Spring Bean；该总装配按项目所有者批准留给 M09-T06；
- 不实现 M09-T04 查询 API、M09-T05 标准错误 JSON/HTTP 映射、M09-T06 配置/指标/健康/安全；
- 不增加异步、队列、进度、取消、历史、重试、跨请求缓存、并行下载或批次外事务；
- 不在 Controller 重复参数语义、不按具体插件/API/数据集分支、不读取 Token、原始上游响应、SQL、堆栈或内部路径；
- 不改变 M05 参数/适配、M06 事务/锁/计数或 M07 来源分类合同，不把来源/适配放入数据库事务；
- 不用 mock-only 测试替代真实 fixture/MySQL 成功与回滚证据，也不把本任务结果描述为生产 Bean 图已经完成。

## Approach

### 下载服务公开表面

在 `com.akkc.tensor.core.download` 创建 `public final DownloadService`，只暴露以下一个构造器和一个业务方法：

```java
public final class DownloadService {
    public DownloadService(
            PluginRegistry pluginRegistry,
            AdapterRegistry adapterRegistry,
            ParameterValidator parameterValidator,
            PersistenceService persistenceService,
            Clock clock);

    public DownloadResult execute(
            PluginId pluginId,
            ApiName apiName,
            Map<String, Object> params,
            RequestId requestId);
}
```

五个构造器参数与四个方法参数分别用参数名拒绝 null；类不声明 Spring annotation、额外 public/protected 方法、重载、builder、命令 DTO 或可变状态。`Clock` 是唯一时间来源，不调用 `Instant.now()`、系统毫秒或默认时区。

服务首先检查 `TransactionSynchronizationManager.isActualTransactionActive()`；已有实际事务时固定抛 `IllegalStateException("Download orchestration must not run in a transaction")`，且不查插件、不调用上游、不适配、不持久化。这样即使非 Controller 调用者错误地从外层事务调用服务，也不能把上游请求包入数据库事务或在外层提交前发布成功结果。

### 查找、参数与线性编排

无已有事务时按以下固定顺序执行，任一步失败都不进入后继：

1. 调用 `pluginRegistry.find(pluginId)`；缺失表示未知、禁用、缺凭证、重复或 readiness 失败，抛内部 `DownloadAccessException(PLUGIN_DISABLED, "Download plugin is unavailable")`。
2. 从 `pluginRegistry.descriptors()` 的构造期安全快照中定位同一 `pluginId` 且 `downloadAvailable=true` 的唯一描述符，再按 `apiName` 定位唯一 `ApiDescriptor`；描述符或 API 缺失抛 `DATASET_MISCONFIGURED` 和固定消息 `Download dataset is unavailable`。不得再次调用插件的 `descriptor()`/`readiness()`。
3. 构造 `DatasetKey.of(pluginId, apiName)` 并调用 `adapterRegistry.find(key)`；缺失同样抛 `DATASET_MISCONFIGURED`，且不调用上游。
4. 调用 `parameterValidator.validate(apiDescriptor, params)`；把返回的同一不可变、有序 `ValidatedParameters.values()` 传给插件。`PARAM_REQUIRED|PARAM_INVALID` 及字段错误原样传播，不复制规则或错误。
5. 调用一次 `plugin.download(apiName, validated.values())`；`SourceException` 原样传播，不捕获、重试或生成半包络。插件返回 null 时固定抛 `SourceException(SOURCE_PAYLOAD_INVALID, "Source returned an invalid payload")`。
6. 若非 null 包络为 `DownloadStatus.FAILURE`，以其 M02 已保证安全且非空白的 `error` 构造 `SourceException(SOURCE_PAYLOAD_INVALID, envelope.error())`；失败包络不能转成 `EMPTY`。
7. 成功包络的 `pluginId`、`apiName` 和 `params` 必须分别等于请求身份及已验证参数，否则抛固定 `SourceException(SOURCE_PAYLOAD_INVALID, "Source returned an invalid payload")`，不回显实际身份或参数。
8. 身份一致且 `rowCount == 0` 时直接构造 `DownloadResult(requestId, EMPTY, pluginId, apiName, 0, 0, 0, "下载成功，0 条数据")`；不读取时钟、不调用 adapter 或 persistence。
9. 非空时只调用一次 `clock.instant()`，把该值传给 `adapter.adapt(envelope, ingestedAt)`；`AdapterException` 原样传播且不会调用 persistence。
10. 调用一次 `persistenceService.persist(batch)`。该调用自行建立 M06-T04 的 `REQUIRED` 事务并在实际提交/回滚后返回/抛出；服务不增加外层事务。只有正常返回 `WriteCounts` 后才构造 `DownloadResult(requestId, SUCCESS, pluginId, apiName, envelope.rowCount(), insertedRows, updatedRows, "下载成功")`。

`sourceRowCount` 始终使用上游包络原始行数；插入/更新数使用 M06 预查后的不同适配键计数，不强制对含重复来源行的任意批次满足两者相等。任务卡的 fixture 唯一单行场景必须满足 `sourceRowCount == insertedRows + updatedRows == 1`。

`DownloadAccessException` 是 `DownloadService` 内唯一 `private static final` 嵌套异常，继承 `TensorException`，只允许 `PLUGIN_DISABLED|DATASET_MISCONFIGURED`，不增加字段、cause、suppressed、公开访问器或动态消息。参数、来源、适配异常保持原实例；Spring `DataAccessException`/事务异常保持原异常与 cause，由 M09-T05 映射为 `PERSISTENCE_FAILED` 并写受控日志。服务不做 catch-all 包装。

### REST 边界与 DTO

`DownloadController` 位于 `com.akkc.tensor.web`，为 `public final`，声明 `@RestController`、`@RequestMapping("/api/v1/downloads")` 与 `@ConditionalOnWebApplication(type = SERVLET)`。唯一构造器接收非 null `DownloadService`。唯一 HTTP 方法为：

```java
@PostMapping
public DownloadResponse download(@Valid @RequestBody DownloadRequest request);
```

Controller 不注入 registries、adapter、validator、persistence、clock 或插件。它把 `request.pluginId()`/`apiName()` 分别交给现有值对象工厂，把参数快照原样交给服务；从 `MDC.get(RequestIdFilter.MDC_KEY)` 取得 Filter 已写入的规范小写 UUID，使用 `new RequestId(UUID.fromString(value))` 构造领域 ID。MDC 值缺失时固定抛 `IllegalStateException("Request ID is unavailable")`，不得生成第二个请求 ID；非法 UUID 作为内部边界失败原样传播。成功结果只通过 `DownloadResponse.from(result)` 投影。

`DownloadRequest` 是以下 public record：

```java
public record DownloadRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String pluginId,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String apiName,
        @NotNull Map<String, Object> params) {}
```

compact constructor 只在 `params != null` 时用 `Collections.unmodifiableMap(new LinkedHashMap<>(params))` 保存保序快照，使 Bean Validation 仍能观察 null，同时允许 M05 validator 对 null/非字符串值给出既定字段语义。DTO 不把 `Map` 收窄为 `Map<String,String>`，避免 Jackson 把非法 JSON 标量强制转换为字符串；不在 record 内复制参数业务规则。

`DownloadResponse` 是以下 public record，并提供唯一公开静态工厂：

```java
public record DownloadResponse(
        String requestId,
        DownloadOutcome outcome,
        String pluginId,
        String apiName,
        long sourceRowCount,
        long insertedRows,
        long updatedRows,
        String message) {
    public static DownloadResponse from(DownloadResult result);
}
```

compact constructor 对必填文本和 enum 执行非 null/非空白检查，对计数执行非负、`EMPTY` 全零和 `SUCCESS` 来源数大于零的不变量；`from` 把三个值对象投影为字符串并保留结果值，不增加字段或本地化分支。component 顺序即 OpenAPI JSON 顺序。

Bean Validation 的结构错误、值对象失败、服务领域异常和数据库异常在本任务不构造标准错误体。M09-T05 将统一映射其 HTTP 状态与 `ApiErrorResponse`；当前 IT 通过 MockMvc 断言结构错误和成功响应，通过直接调用同一真实服务断言领域 code、无后继副作用及真实回滚，不新增局部 `@ExceptionHandler` 或第二套错误 DTO。

### 直接依赖与约束比较

- M05-T01 的 `PluginRegistry`/`AdapterRegistry` 是本任务新增并经项目所有者批准的直接依赖：前者只查找 ID 唯一且可下载的插件并提供构造期安全描述符快照，后者只查找 key 唯一的 adapter。服务必须同时消费两者，不能直接注入具体插件/adapter、采用 first-wins 或重新扫描 Bean。
- M05-T03 的 `ParameterValidator`/`ValidatedParameters` 是插件调用前唯一参数准入边界；服务不修改参数顺序，不把 Token 加入 map，不重复默认值、类型、枚举、范围或字段错误规则。
- M05-T05 的 `DatasetAdapter`/`GenericDatasetAdapter` 把成功非空包络转换为完整 `AdaptedBatch`，保留注入的唯一 `ingestedAt`，并原样抛出安全适配错误；服务只按 registry 选择并调用一次。
- M06-T04 的 `PersistenceService.persist` 对非空批次建立单一事务，准确计数、提交/回滚后返回/抛出，并允许加入已有事务；本服务的入口事务守卫确保下载链路不会加入外层事务，因此正常返回的计数已经提交。
- M07-T04 的 `TushareProPlugin` 只在 ready 时按 API 委托一次客户端，缺 Token/禁用在上游前产生 `PLUGIN_DISABLED`，来源异常保持 M07 分类；服务通过 registry 统一处理，不写 Tushare 分支。
- M09-T01 的 `RequestIdFilter` 在链内 MDC 和响应头保存同一规范 UUID；Controller 只消费该值，`DownloadResult`/`DownloadResponse` 保持相同 requestId。

六项依赖职责互补。M05-T01 解决通用插件/adapter 定位，M05-T03/M05-T05 分别处理参数与数据，M06-T04 提供唯一事务，M07-T04 提供具体来源，M09-T01 提供请求关联。项目所有者已批准把原看板遗漏的 `M05-T01` 补入直接依赖；其他依赖、顺序与公开合同不变。

## Files

创建：

- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`：实现事务外线性编排、可用性/包络边界、空短路、固定时钟和提交后结果。
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`：实现唯一 POST 路由、MDC 请求 ID 和服务/DTO 映射。
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java`：保存 OpenAPI 请求字段、结构注解和参数快照。
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java`：投影并校验 OpenAPI 成功/空响应。
- `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java`：固定 MySQL、fixture、MockMvc 和真实下载链路测试。

不修改或删除其他文件。实现提交固定消息为 `feat(api): execute synchronous dataset downloads`，精确包含上述五个新 Java 文件；所有新文件加入 Git。设计、计划、交接、看板、POM、既有源码/测试、临时日志和 `target/` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前在允许 Mockito/Byte Buddy attach 的环境运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

预期 plugin-api 79、core 75、Tushare 93、fixture 12、app 36，共 295/295，0 failure、0 error、0 skipped；六层 Enforcer 与现有 ArchUnit/禁止 Git 能力测试通过。`*IT` 仍不进入默认 Surefire 计数。

随后只完整创建 `DownloadControllerIT.java`，不创建四个生产类型，运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DownloadControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

必须在 `tensor-app:testCompile` 只因 `DownloadService`、`DownloadController`、`DownloadRequest`、`DownloadResponse` 缺失而非零；不得因依赖、Docker、MySQL、测试语法、既有代码或环境形成伪 RED。

### 固定 MySQL/fixture GREEN

创建四个最小生产类型后，在当前 Colima 工作站使用：

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DownloadControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

测试固定使用官方 `mysql:8.4.6`、六个 Flyway migration、`acceptance` fixture 配置、固定 `Clock`、真实 registries/validator/adapter/persistence、`RequestIdFilter` 和 standalone MockMvc；不访问公网，不使用 Tushare Token，不在 Docker 不可用时 skip。恰有 10 个普通 `@Test`，10/10 通过：

1. 反射确认四个生产类/records 的 final、构造器、方法、record components、Bean Validation 和 Servlet-only Controller 表面精确；DTO null/blank/计数不变量、参数快照和 `DownloadResponse.from` 正确。
2. 非法请求结构、测试专用描述符的缺失必填参数，以及 fixture 的未知、非字符串场景参数均在插件调用前失败为 Bean Validation 或 M05 `PARAM_REQUIRED|PARAM_INVALID`，插件计数为零且数据库为空；fixture 自身缺失场景继续使用既有默认值 `SUCCESS`，不得错误断言为缺失失败。
3. 禁用/缺失插件产生固定 `PLUGIN_DISABLED`；已注册插件缺 API 描述符或 adapter 产生固定 `DATASET_MISCONFIGURED`，均无上游、适配或数据库副作用。
4. fixture `SOURCE_FAILURE` 的同一 `SOURCE_UNAVAILABLE` 原样传播；测试专用失败包络转为 `SOURCE_PAYLOAD_INVALID`，身份/参数不一致成功包络转为固定 invalid-payload failure，均不适配、不持久化。
5. `EMPTY` 的 HTTP 200 JSON 精确为同一 requestId、`EMPTY`、请求身份、三个零计数和固定消息；Filter header 与 body 相同，adapter、clock 和 persistence 均未调用，数据库为空。
6. 首次 `SUCCESS` 经真实 Controller/服务/fixture/adapter/MySQL 返回 source=1、inserted=1、updated=0；JSON 字段/顺序/消息与 OpenAPI 一致，数据库类型、来源列和固定毫秒 `ingested_at` 正确，响应只在行可查询后返回。
7. 对同一 fixture 业务键再次 `SUCCESS` 返回 source=1、inserted=0、updated=1，表仍一行；两次均满足唯一 fixture 的 `sourceRowCount == insertedRows + updatedRows`。
8. `TYPE_FAILURE` 原样产生 amount 的 `ADAPTER_TYPE_INVALID`，持久化未调用且数据库为空。
9. 先成功插入，再由测试创建的 MySQL trigger 对 `PERSISTENCE_FAILURE` note 执行 `SIGNAL`；请求抛原 `DataAccessException`，删除 trigger 后查询仍为提交前 seed 值、note/amount/ingested_at 未部分更新，证明真实整事务回滚。
10. 在外层真实事务中调用服务固定被事务守卫拒绝，插件计数为零；正常非事务路径只调用一次时钟、adapter 和 persistence，任何异常都不构造 `DownloadResponse`。

测试内允许最小 counting SPI wrapper、Mockito final collaborator 和 MySQL trigger，仅用于观察阶段短路；真实 success/update/rollback 必须走 production fixture、adapter 和 persistence。不得复制生产编排、使用 H2、伪造提交、吞异常或增加生产测试钩子。

再运行既有 fixture 流程与新 API 流程：

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=DownloadControllerIT,FixtureFlowIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期两类 IT 共 15/15，0 failure、0 error、0 skipped。

### Reactor、结构与范围门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

两条命令仍预期 295/295，0 failure、0 error、0 skipped；六层 Enforcer、app ArchUnit 和禁止 Git 能力门禁通过。IT 文件必须成功编译但不改变默认 Surefire 生命周期或计数。

运行：

```bash
rg -n 'TransactionSynchronizationManager|PluginRegistry|AdapterRegistry|ParameterValidator|plugin\.download|adapter\.adapt|persistenceService\.persist|clock\.instant|DownloadOutcome\.(SUCCESS|EMPTY)' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java
rg -n '@RestController|/api/v1/downloads|@Valid|RequestIdFilter\.MDC_KEY|DownloadResponse\.from' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java
rg -n 'Tushare|Fixture|RestClient|JdbcTemplate|DataSource|Thread|CompletableFuture|Executor|Retry|Authorization|Cookie|(?i:token|credential)|System\.currentTimeMillis|Instant\.now' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java
jar tf data-plane/tensor-core/target/tensor-core-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/core/download/DownloadService.*\.class'
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/web/(DownloadController|dto/DownloadRequest|dto/DownloadResponse).*\.class'
git diff --quiet -- \
  docs data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare data-plane/tensor-plugin-fixture \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DataSourceResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DatasetDefinitionResponse.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation
git diff --check
```

前两项授权扫描命中设计顺序；禁用能力扫描无输出并退出 1；两个 JAR 只命中批准的新生产类及嵌套异常；受保护路径和格式退出 0。提交前 `git status --short --untracked-files=all -- data-plane` 精确列出五个新 Java 文件且无 `target/`，全部已加入 Git。

提交实现后运行：

```bash
git show --stat --oneline --summary HEAD
git show --format= --name-status HEAD
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml clean
git status --short
```

提交消息和五文件范围精确，clean 退出 0，最终工作树为空且不存在 `target/`。

实施审查前执行三项受控 mutation，每次只临时修改生产实现、观察预期失败并恢复：把参数校验移到插件调用后，非法参数/零调用测试必须失败；删除空短路，empty 的零 adapter/clock/persistence 测试必须失败；在 `persist` 返回前构造/返回成功或绕过 persist，真实 success/rollback 测试必须失败。恢复后定向 10/10 和 15/15 必须再次通过，mutation 不提交。

## Acceptance

- `DownloadService` 的公开表面精确，五个依赖和四个参数非 null，唯一时间来自 `Clock`；已有事务在任何上游行为前被拒绝；
- 编排严格按 registry/API/adapter → 参数 → 插件 → 包络 → 空短路或适配 → 单事务持久化 → 结果执行，不按具体插件/API 分支，不重试、不并行、不扩大事务；
- 插件/API/adapter 缺失分别形成批准的 `PLUGIN_DISABLED|DATASET_MISCONFIGURED`，失败/不一致包络形成 `SOURCE_PAYLOAD_INVALID`；参数、来源、适配及数据库异常保持批准边界，无 catch-all 包装；
- `EMPTY` 不读时钟、不适配、不持久化，返回三个零计数；`SUCCESS` 只在 `persist` 提交后形成，保留上游行数和准确插入/更新数，同批使用固定服务端时间；
- Controller 只做 DTO、值对象、MDC 请求 ID 和结果投影；header/body requestId 一致，两个 DTO 的字段、顺序、验证、快照与 OpenAPI 一致；
- 固定 MySQL 8.4.6 上，新 IT 10/10、与既有 fixture 联跑 15/15，真实成功、空、更新、来源/适配失败、数据库 trigger 回滚和固定时间均取得预期结果；
- 严格 RED 只来自四个缺失生产类型；完整 reactor `test`/`verify` 295/295、六层 Enforcer、ArchUnit、禁止 Git、JAR、静态、mutation、范围、格式、跟踪和 clean 门禁通过；
- 实现提交精确包含五个新增 Java 文件并使用固定消息；未修改 POM、合同、既有实现或其他任务，未提前实现标准错误体或生产 Bean 总装配。

## Risks

- 本任务按批准范围以真实组件手工装配 IT 证明 API 和事务行为，但生产 Servlet Bean 图仍不完整；M09-T06 必须设计并交付 registries、49 adapters、已验证 catalog、JDBC services 和下载/查询服务的生产装配，才能宣称完整应用上下文可运行。
- `DownloadEnvelope.FAILURE` 只有安全字符串而没有来源错误码；本任务把该兼容路径固定归类为 `SOURCE_PAYLOAD_INVALID`。当前 Tushare/fixture 的正常失败直接抛已分类 `SourceException`，不会丢失准确来源类别。
- `Clock.instant()` 在适配前、数据库事务外生成；数据库保存精确到毫秒。同一批次一致，但该时间不是数据库 commit timestamp，不能用于跨请求全局排序或事务持续时间测量。
- `PersistenceService` 本身允许加入已有事务；入口 guard 是同步下载“上游不在事务内、成功仅在提交后发布”的必要保护。未来若增加非 HTTP 调用方，不得绕过 `DownloadService` 直接组合上游与外层事务。
- `DownloadControllerIT` 依赖 Docker/Colima 与固定官方 MySQL 8.4.6。环境不可用时必须报告阻塞，不得 skip、替换 H2、浮动镜像或把默认 reactor 通过当作集成验收。
