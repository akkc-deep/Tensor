# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`.
- **Completed task:** `M09-T05`.
- **Next task:** `M09-T06`.
- **Design document:** `docs/task-designs/M09-T06-design.md`.
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **ID:** `M09-T06`.
- **Title:** 配置、脱敏、指标、健康和静态资源安全。
- **Goal:** 让 `TensorApplication` 在有效 MySQL 配置下形成完整可运行的 Servlet 生产 Bean 图，并为下载与查询提供一次且仅一次的安全完成事件和 TRD 17.3 五项低基数指标；数据库健康、缺 Token 插件降级、Actuator 暴露、响应安全头和静态资源缓存均使用冻结的安全默认值。
- **Scope:** 精确创建 `application.yml`、`ApplicationConfiguration`、`TensorMetrics`、`OperationLogger`、`WebSecurityHeadersConfiguration`、`ObservabilityTest`、`ProductionApplicationContextIT` 七个文件，精确修改 `TusharePluginConfiguration`、`TushareProPluginTest`、两个 Controller 和两个既有 Controller IT 六个文件。组装 M09-T02～T04 延期的真实生产协作者，显式包装两条操作并保持结果/异常 identity；不修改 POM、Core、plugin-api、fixture、OpenAPI、迁移、DTO、`TensorApplication`、`RequestIdFilter`、`GlobalExceptionHandler` 或 `JacksonPrecisionConfiguration`，不实现 CORS、SPA fallback、单 JAR 检查、优雅停机或前端。
- **Acceptance criteria:** 有效 MySQL 下唯一完整 production Servlet Bean 图在 Flyway 后通过 49 项真实 schema 校验；七个批准环境变量和安全默认值精确；缺 Tushare Token 只禁用下载而不拉低数据库健康，MySQL 中断使 health 为 DOWN/503；默认只公开 health/liveness/readiness 且关闭 discovery；日志、health 和响应不泄漏 Token/密码，health/响应不泄漏 JDBC 配置；五项指标和标签闭集精确且未知 key 不产生观测；每个已知 download/query 只记录一次无值、无 Throwable 的完成事件并保持业务结果/异常不变；所有响应有六个安全头和冻结缓存策略；严格 RED、普通 18/18、受影响回归 51/51、生产上下文 1/1、schema 联跑 53/53、默认 reactor 338/338、五项 mutation 及全部静态/秘密/Actuator/JAR/范围/格式/跟踪/clean 门禁取得设计结果；固定实现提交精确包含 13 个文件。

## Dependencies

### `M09-T01`

- **Artifact:** `docs/task-designs/M09-T01-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java`，源自实现提交 `367b0d1`。
- **Decision:** Boot 入口位于根包；最高优先级 Filter 在进入链前建立规范 UUID 的 `X-Request-Id`/MDC 同值并在结束时清理；通用错误 DTO 不携带异常内部状态。
- **Rationale:** 完整生产 Servlet 上下文、后续日志和所有 HTTP 响应需要共享一个请求身份与一个安全错误表面，且不能生成第二个请求 ID。
- **Constraint:** 不修改入口、Filter 或 DTO；`OperationLogger` 只读取既有 MDC，安全 Filter 的顺序固定在 `RequestIdFilter` 后一位；不得把 requestId 作为指标标签或延长 MDC 生命周期。
- **Usage:** M09-T06 从既有入口启动生产上下文，验证 Filter/advice/Controller 总注册，让完成事件复用 MDC requestId，并用后置安全 Filter 覆盖 API、Actuator、错误与静态响应。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；四个当前生产消费文件相对实现提交 `367b0d1` 无差异。完成证据记录聚焦 11/11、reactor test/verify 283/283、mutation、Enforcer、ArchUnit、禁止 Git、JAR、范围、格式、跟踪和 clean 门禁通过，最终审查无 Critical/Important。

### `M09-T02`

- **Artifact:** `docs/task-designs/M09-T02-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DataSourceResponse.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DatasetDefinitionResponse.java`，源自实现提交 `2c40b53`。
- **Decision:** 元数据 Controller 只在 Servlet 应用注册并通过构造器直接依赖 `PluginRegistry` 与 `DatasetCatalog`；真实 registry/catalog Bean 装配明确延期到 M09-T06；缺 Token/禁用不阻止已验证数据集元数据查询。
- **Rationale:** Controller 已独立证明 HTTP 投影与 409 边界，但完整生产应用必须复用 registry 的 readiness 快照和 Flyway 后校验的 catalog，不能以测试替身或插件专用分支代替。
- **Constraint:** 不修改元数据 Controller/DTO/HTTP 语义，不在启动时访问 Tushare 网络，不因缺 Token 移除数据集目录；生产配置必须提供唯一 `PluginRegistry` 和唯一经 schema 校验的 `DatasetCatalog`。
- **Usage:** `ApplicationConfiguration` 收集 `DataSourcePlugin`、构建 registry，并从候选 adapter 定义在 Flyway 后建立 catalog，使既有四条元数据路由在完整 Servlet 上下文中取得真实协作者。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；四个当前生产消费文件相对实现提交 `2c40b53` 无差异。完成证据记录聚焦 12/12、reactor test/verify 295/295、mutation、Enforcer、ArchUnit、禁止 Git、JAR、敏感/范围/格式/clean 门禁和最终零审查发现。

### `M09-T03`

- **Artifact:** `docs/task-designs/M09-T03-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java` 和 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java`，生产产物源自实现提交 `ade4995`。
- **Decision:** `DownloadService` 的五依赖线性编排保持事务外上游、单事务持久化和提交后响应；生产 registry/adapter/validator/persistence/service 装配延期到 M09-T06；Controller 只允许显式增加不改变业务结果的操作包装。
- **Rationale:** 已有真实 fixture/MySQL IT 证明下载和回滚行为，M09-T06 只需补齐生产对象图和边界观测，不能让观测移动事务、重试或替换异常。
- **Constraint:** 不修改 Core、DTO、路由、参数/来源/适配/持久化分类或事务边界；`OperationLogger.download` 必须在标识和 MDC 解析后包围既有 service/response 调用，原样返回同一响应或重新抛出同一异常；standalone IT 的十项场景不得削弱。
- **Usage:** `ApplicationConfiguration` 创建 `AdapterRegistry`、`ParameterValidator`、JDBC repositories、`PersistenceService`、UTC `Clock` 和 `DownloadService`；Controller 注入 `OperationLogger` 形成一次安全完成事件和下载指标。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；四个当前生产消费文件相对实现提交 `ade4995` 无差异，后续提交只加固同一 IT。完成证据记录固定 MySQL 8.4.6 聚焦 10/10、联跑 15/15、reactor verify 295/295、三项 mutation、真实回滚及全部结构/安全/范围/clean 门禁通过，最终无 Critical/Important。

### `M09-T04`

- **Artifact:** `docs/task-designs/M09-T04-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/PageResponse.java` 和 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java`，生产产物源自实现提交 `4617f22`。
- **Decision:** records Controller 通过构造器依赖真实 `DatasetCatalog`/`DatasetQueryService`，保持 catalog-first、只读查询、规范分页与精度序列化；生产 catalog/JDBC/query Bean 装配延期到 M09-T06；观测只在目录、筛选、criteria 和 MDC 校验后显式包装查询。
- **Rationale:** 未知或非法客户端输入不能创建高基数指标，而已知数据集的成功/失败必须可观测且继续复用 M06 已验证的查询语义。
- **Constraint:** 不修改 `JacksonPrecisionConfiguration`、`PageResponse`、Core 查询或 HTTP 结果；`OperationLogger.query` 不重算 page/totals/items，不记录筛选值，必须保留既有 `IllegalArgumentException -> DATASET_MISCONFIGURED` 转换和异常 identity；既有八项 MySQL IT 不得削弱。
- **Usage:** `ApplicationConfiguration` 从同一 catalog 创建 `GenericQueryRepository`/`DatasetQueryService`；Controller 仅增加固定筛选名列表和 logger wrapper，形成查询 total/duration 及单次安全事件。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；三个当前生产消费文件相对实现提交 `4617f22` 无差异。完成证据记录 MySQL 聚焦 8/8、主闭环联跑 23/23、reactor verify 295/295、四项 mutation、只读/精度/JAR/敏感/范围/clean 门禁通过，任务级与最终审查零发现。

### `M09-T05`

- **Artifact:** `docs/task-designs/M09-T05-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java` 和 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/GlobalExceptionHandlerTest.java`，源自实现提交 `b2dbb09`。
- **Decision:** 既有 `GlobalExceptionHandler` 是唯一 Servlet 全局错误出口；16 项错误码只产生冻结的 400/409/422/500/502/504，5xx 诊断日志只保留脱敏栈；完整 Filter/advice 注册验证延期到 M09-T06。
- **Rationale:** 操作完成事件服务于低基数统计，不替代统一错误响应或安全诊断；完整生产上下文必须证明二者共同注册且都不泄漏秘密。
- **Constraint:** 不修改 handler 或其 25 项测试，不复制错误响应、HTTP 映射或原 Throwable 日志；`OperationLogger` 的 code/stage 必须与既有持久化/查询分类一致，但不得记录异常对象、类型、message、cause、stack、SQL 或内部路径。
- **Usage:** 生产上下文 IT 验证 advice 与两个 Filter 的真实注册；operation wrapper 只增加一次无 Throwable 的 `tensor.operation.completed`，失败仍交由同一 handler 生成响应与诊断日志。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；当前 handler 与测试相对实现提交 `b2dbb09` 无差异。完成证据记录聚焦 25/25、reactor test/verify 320/320、四项受控 mutation、授权/禁用扫描、JAR、秘密/范围/格式/clean 门禁全部通过，独立审查 `Ready to merge: Yes` 且零发现。

五项直接输入的决策与约束互补且无冲突：M09-T01 固定应用入口和请求身份，M09-T02 留出 registry/catalog 生产装配，M09-T03 留出下载协作者装配并固定事务语义，M09-T04 留出只读查询装配并固定精度/页面语义，M09-T05 固定唯一错误出口和脱敏诊断。本任务只补它们共同留下的 Servlet 生产装配、边界观测、健康与响应安全缺口；项目所有者已批准相应 13 文件范围。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M09-T06-design.md`；
2. `docs/superpowers/plans/2026-09-04-m09-t06-safe-configuration-observability.md`；
3. `docs/task-handoffs/M09-T06-handoff.md`；
4. `docs/task-handoffs/tensor-v1-task-board.md` 的 M09-T06 行与详情；
5. `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 Global Constraints、Task M09-T06 和 Module Gate；
6. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 6、7.2、14～17 与 Appendix B，以及 `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的 3.3、7.5、9、10.3、10.6、12.1；
7. `docs/task-designs/M09-T01-design.md` 至 `docs/task-designs/M09-T05-design.md`；
8. Dependencies 节列出的当前生产产物，以及实施计划 Step 1 列出的 Tushare 配置、插件、Controller、handler 和 Filter。

首个实施动作：确认 Git 工作树为空后，在允许 Mockito/Byte Buddy self-attach 的环境运行计划 Step 1 的 320/320 reactor 基线；基线通过后完整创建 `ObservabilityTest.java` 和 `ProductionApplicationContextIT.java`，不创建四个新生产类型或修改生产代码，再执行 Step 3 并取得只因 `ApplicationConfiguration`、`TensorMetrics`、`OperationLogger`、`WebSecurityHeadersConfiguration` 缺失而产生的 `tensor-app:testCompile` RED。

## Risks

- Spring 同时存在命名的 `List<DatasetDefinition>`、命名的 `List<DatasetAdapter>` 和普通 adapter 扩展 Bean；实现必须使用设计中的 qualifier/`ObjectProvider` 边界，生产上下文 IT 必须证明最终恰有 49 个有效 Tushare adapters。
- 启动 catalog 校验必须在 Flyway 后执行；数据库启动时不可用会阻止生产 context，运行中中断则只由 JDBC health 报 DOWN/503。不得以跳过 schema 或探测 Tushare 网络规避。
- 默认业务指标不经 HTTP 暴露；部署方若将来启用采集，仍必须置于受保护网络/代理后，且不得顺带开放 env/configprops 等端点。
- CSP 禁止 inline script/style；M10～M13 前端构建必须继续使用外部哈希资源，任何 nonce/hash 放宽需要独立设计。
- `ProductionApplicationContextIT` 依赖固定 MySQL 8.4.6，Mockito/Byte Buddy 回归依赖允许 self-attach 的环境；环境不可用必须报告，不得 skip、换 H2、改 POM 或把普通 reactor 结果替代显式 IT。
