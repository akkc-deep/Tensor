# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`.
- **Completed task:** `M09-T04`.
- **Next task:** `M09-T05`.
- **Design document:** `docs/task-designs/M09-T05-design.md`.
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **ID:** `M09-T05`.
- **Title:** 全局异常和 HTTP 状态映射。
- **Goal:** 在 `tensor-app` 建立唯一 Servlet 全局异常出口，把当前 Controller、Core、plugin 和 Spring MVC 失败安全投影为既有 `ApiErrorResponse`，保持 Header/body requestId 一致，并严格执行 M00 冻结的 16 项错误码、HTTP 状态和 retryable 真值。
- **Scope:** 只创建 `GlobalExceptionHandler.java` 和 `GlobalExceptionHandlerTest.java`；统一处理领域、Core 字段、Bean Validation、MVC/值对象输入、持久化、查询和未知异常，固定安全客户端摘要和脱敏日志。不得修改 POM、合同、TRD、既有 Controller/DTO/Core/plugin/测试或资源，不实现 M09-T06，也不得自行增加或复用错误码实现 404/503。
- **Acceptance criteria:** 唯一 Servlet `@RestControllerAdvice` 复用现有 Filter/错误 DTO；16 码精确产生 400/409/422/500/502/504 和既定 retryable；Core/Bean/MVC/值对象输入字段与摘要安全稳定；downloads 数据库/事务失败为 `PERSISTENCE_FAILED`，records 未处理查询失败为 `QUERY_FAILED`，其他未知失败为 `INTERNAL_ERROR`；响应和日志不泄漏 SQL、Token、请求数据、异常消息/cause 或内部路径；严格 RED、聚焦 25/25、reactor test/verify 320/320、三项 mutation、静态/JAR/范围/格式/跟踪/clean 门禁取得设计结果；固定实现提交精确包含两个 Java 文件。

## Dependencies

### `M09-T02`

- **Artifact:** `docs/task-designs/M09-T02-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java`、`dto/DataSourceResponse.java`、`dto/ApiDescriptorResponse.java`、`dto/DatasetDefinitionResponse.java`，源自实现提交 `2c40b53`。
- **Decision:** 元数据 Controller 以私有 `TensorException` 携带 `PLUGIN_DISABLED|DATASET_MISCONFIGURED` 并形成预先可观察的 409；未知/不可下载 API 和未知插件/数据集不使用 404；非法 `PluginId|ApiName` 值对象输入原样传播 `IllegalArgumentException`，明确留给 M09-T05 映射 `PARAM_INVALID`；标准错误体留给唯一全局 advice。
- **Rationale:** 插件/数据集是已注册但不可用或目录不完整的 v1 配置状态，M00 闭集以 409 表达；Controller 只负责元数据投影和安全领域码，避免局部复制错误包络或输入映射。
- **Constraint:** 不改写 Controller、DTO 或既有 409 code，不按 `@ResponseStatus`、异常类名或消息生成响应，不新增 404/NOT_FOUND；全局 advice 必须捕获值对象 `IllegalArgumentException` 为固定 `400 + PARAM_INVALID`，且不得回显非法路径值。
- **Usage:** M09-T05 捕获现有私有 `TensorException` 并从 code 生成固定状态/message/retryable；独立值对象 handler 补齐 M09-T02 已延期的 400 输入边界。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；生产消费文件相对实现提交 `2c40b53` 无差异，后续 `2c57da8`、`05c1a69` 只加固既有测试。完成证据记录聚焦 12/12、reactor test/verify 295/295、mutation、Enforcer、ArchUnit、禁止 Git、JAR、敏感/范围/格式/clean 和最终零审查发现。

### `M09-T03`

- **Artifact:** `docs/task-designs/M09-T03-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`、`dto/DownloadRequest.java`、`dto/DownloadResponse.java`，源自实现提交 `ade4995`；既有 `ParameterValidator.ParameterValidationException`、`SourceException` 和 `AdapterException` 是该下载链路传播到 HTTP 边界的领域输入。
- **Decision:** 下载链路原样传播 `PARAM_REQUIRED|PARAM_INVALID` 及有序字段错误、`SOURCE_*`、`ADAPTER_*` 和私有 `PLUGIN_DISABLED|DATASET_MISCONFIGURED`；真实数据库/事务异常保持原异常与 cause，明确留给 M09-T05 映射 `PERSISTENCE_FAILED`；Controller 使用 `@Valid` 并只消费 Filter MDC。
- **Rationale:** 参数、来源和适配层已经拥有准确领域分类，只有持久化实现异常缺少公共领域包装；统一 advice 可以保持阶段短路和事务语义，同时形成一个错误响应出口。
- **Constraint:** 不修改服务阶段、事务、Controller 或 DTO，不吞/重试/重新包装来源和适配异常，不改变 Core 字段顺序；只有精确 `POST /api/v1/downloads` 上的 `DataAccessException|TransactionException` 可归入 `PERSISTENCE_FAILED`，不得按 SQL、消息或 cause 文本分类。
- **Usage:** M09-T05 直接按 `TensorException.code()` 覆盖参数/来源/适配/可用性矩阵，把 Core 字段错误投影为 `FieldErrorResponse`，把 Bean Validation 结构错误映射 400，并以固定 method/path/type 识别持久化失败。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；四个生产消费文件相对实现提交 `ade4995` 无差异，`c17346e`、`dad2ee4` 只加固同一 IT。完成证据记录固定 MySQL 8.4.6 聚焦 10/10、联跑 15/15、reactor verify 295/295、三项 mutation、事务回滚、Enforcer/ArchUnit/禁止 Git/JAR/敏感/范围/格式/clean 和最终无 Critical/Important。

### `M09-T04`

- **Artifact:** `docs/task-designs/M09-T04-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java`、`JacksonPrecisionConfiguration.java`、`dto/PageResponse.java`，源自实现提交 `4617f22`。
- **Decision:** records Controller 在数据库访问前用私有 `TensorException` 形成 `400 + PARAM_INVALID` 和 `409 + DATASET_MISCONFIGURED`，Spring 日期/整数绑定失败保持 MVC 400；JDBC `DataAccessException`、COUNT 结构异常和其他运行时查询失败原样传播，明确留给 M09-T05 统一为 `QUERY_FAILED`；MDC 是唯一请求 ID 来源。
- **Rationale:** records 层只负责 catalog/criteria/DTO 边界，Core 保持查询与页面语义；全局 advice 才能统一数据库失败、安全响应和日志，而不让 Controller 复制错误协议。
- **Constraint:** 不修改 records Controller、查询服务、PageResponse 或精度 module，不把成功/空/精度行为混入异常处理；精确 records GET 路径的未处理异常统一为 `QUERY_FAILED`，领域 400/409 必须先由更具体 handler 保留，响应不得泄漏 SQL、列/表、异常消息或请求值。
- **Usage:** M09-T05 捕获现有 `TensorException` 与 MVC 类型异常，并在 catch-all 中以固定 GET/prefix/middle/suffix 路由谓词覆盖 M09-T04 延期的查询失败。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；三个生产消费文件相对实现提交 `4617f22` 无差异。完成证据记录 MySQL 聚焦 8/8、三类联跑 23/23、reactor verify 295/295、四项 mutation、静态/JAR/只读/敏感/范围/格式/clean、任务级与最终审查零发现；缺失 MDC 的零数据库访问断言已纳入既定八项测试。

三项直接输入的职责与约束互补且无冲突：M09-T02 提供元数据 409 和延期的值对象 400，M09-T03 提供下载领域/字段/持久化边界，M09-T04 提供 records 领域/MVC/查询边界；三者都复用同一 M09-T01 请求标识并明确把标准错误体留给 M09-T05。依赖一致性检查已将 `IllegalArgumentException -> PARAM_INVALID` 补入批准设计和实施计划，未改变 16 码闭集、两文件范围或 25/320 测试计数。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M09-T05-design.md`；
2. `docs/superpowers/plans/2026-09-04-m09-t05-global-exception-mapping.md`；
3. `docs/task-handoffs/M09-T05-handoff.md`；
4. `docs/task-handoffs/tensor-v1-task-board.md` 的 M09-T05 行与详情；
5. `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 Global Constraints、Task M09-T05 和 Module Gate；
6. `docs/contracts/error-codes.md`、`docs/contracts/openapi-v1.yaml` 的错误响应/`ApiError`/`FieldError`，以及 TRD 12.5/12.6；
7. `docs/task-designs/M09-T02-design.md`、`docs/task-designs/M09-T03-design.md`、`docs/task-designs/M09-T04-design.md`；
8. 交接 Dependencies 节列出的当前生产产物，以及 `ErrorCode`、`TensorException`、`ParameterValidator`、`RequestIdFilter`、`ApiErrorResponse`、`FieldErrorResponse`。

首个实施动作：确认 Git 工作树为空后，在允许 Mockito/Byte Buddy self-attach 的环境运行实施计划 Step 1 的 295/295 reactor 基线；基线通过后只完整创建 `GlobalExceptionHandlerTest.java`，不创建生产 handler，执行 Step 3 并取得只因 `GlobalExceptionHandler` 缺失而产生的 `tensor-app:testCompile` RED。

## Risks

- M00 闭集没有 404/503；项目所有者已批准契约优先，只实现 400/409/422/500/502/504。未来需要 404/503 时必须先独立修订错误目录、OpenAPI、`ErrorCode` 和消费者。
- M09-T03/M09-T04 刻意传播无公共领域类型的持久化/查询异常，因此 handler 依赖当前固定 method/path；新增数据库 API 必须独立扩展设计。
- 5xx 安全日志只保留异常类型和去除 message/cause/suppressed 的栈位置，诊断细节低于原 Throwable；更丰富的白名单诊断属于 M09-T06。
- Advice 依赖 `RequestIdFilter` 在 DispatcherServlet 前建立 MDC；独立测试安装真实 Filter，完整生产 Servlet Bean 图和顺序仍由 M09-T06 验证。
