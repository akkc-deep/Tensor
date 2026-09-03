# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`.
- **Completed task:** `M09-T03`.
- **Next task:** `M09-T04`.
- **Design document:** `docs/task-designs/M09-T04-design.md`.
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **ID:** `M09-T04`.
- **Title:** 数据集定义与只读分页查询 API。
- **Goal:** 在 `tensor-app` 暴露 OpenAPI records 只读分页端点，把固定 HTTP 查询参数映射为 M06 查询合同，只查询 M05 启动校验通过的数据集，并在 REST 边界无损输出 DECIMAL/BIGINT 业务值，同时保持分页控制量为 JSON number。
- **Scope:** 只创建 `DatasetController.java`、`PageResponse.java`、`JacksonPrecisionConfiguration.java` 和 `DatasetControllerIT.java`；实现 catalog-first 409、固定筛选/分页输入、MDC 请求标识、Core 页面投影和 boxed Long/BigDecimal 精度序列化。不得修改 POM、OpenAPI、Core/plugin、迁移、资源、既有 app 文件或测试生命周期；不得实现统一错误体、生产 Bean 装配、客户端排序、任意筛选/SQL、写接口或其他预定义任务。
- **Acceptance criteria:** 唯一 records GET 路由及 camelCase 参数/default 1/50 与 OpenAPI 一致；不存在/不安全元数据在数据库访问前为 `409 + DATASET_MISCONFIGURED`，未声明/非法查询输入在数据库访问前为 `400 + PARAM_INVALID`；查询结果保持 Core 的空结果、COUNT-first、稳定排序、超界归一、列/行顺序和深不可变性；业务 boxed BIGINT/DECIMAL 是无损 plain JSON string，四项分页控制仍为 number；不存在 records 写路由；严格 RED、MySQL 聚焦 8/8、主闭环联跑 23/23、reactor 295/295、mutation、Enforcer、ArchUnit、禁止 Git、JAR、范围、格式、跟踪和 clean 门禁取得设计结果；固定实现提交精确包含四个 Java 文件。

## Dependencies

### `M05-T02`

- **Artifact:** `docs/task-designs/M05-T02-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java`、`DatasetStartupValidator.java`、`SchemaInspector.java`，源自实现提交 `57771b0`。
- **Decision:** `DatasetCatalog` 只保存定义关系与实际 schema 验证通过、DatasetKey 唯一的定义；`find(DatasetKey)` 精确返回 optional，目录只能由公开 `DatasetStartupValidator(...).validate()` 建立。
- **Rationale:** records 查询的表名、列名和筛选元数据必须来自启动时已验证的不可变目录，Controller 才能在访问数据库前可靠区分数据集不可用。
- **Constraint:** 不反射或绕过 package-private catalog 构造器，不从数据库生成期望定义，不修改/自动修复 schema，也不根据 `DatasetQueryService` 异常消息猜测目录缺失；不存在或查询层不能安全消费的元数据必须在 service 前形成 409。
- **Usage:** `DatasetController` 调用 `find` 建立 catalog-first 边界；`DatasetControllerIT` 通过真实 validator/inspector 为同一 Controller 与查询服务创建目录。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；实现提交记录聚焦 10/10、模块 test/verify 99/99、三层 Enforcer、静态/范围/格式/clean 与最终审查通过。当前三项消费产物相对提交 `57771b0` 无差异。

### `M06-T06`

- **Artifact:** `docs/task-designs/M06-T06-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QueryCriteria.java`、`DatasetPage.java`、`GenericQueryRepository.java`、`DatasetQueryService.java`，源自实现提交 `9c3fa44`。
- **Decision:** `DatasetQueryService.query(DatasetKey, QueryCriteria)` 只查询目录中已验证 definition，严格执行 COUNT-first、空结果短路、超界页归一和规范页 SQL；`DatasetPage` 保存六组件深不可变页面，repository 保留业务列原序、三个来源列以及 `BigDecimal`、`Long`、`LocalDate`、`Instant` 类型。
- **Rationale:** Core 统一承担参数化 SQL、稳定排序、分页语义和数据库类型保真，让 HTTP 层只负责固定输入、错误边界与 JSON 投影。
- **Constraint:** 不修改或复制 Core 查询规则，不重新计算页码/totals、重排列/行、裁剪宽表或把精确值转为 double/float；`QueryCriteria` 仍只允许 pageSize 20/50/100，HTTP 缺省值由 M09-T04 提供。
- **Usage:** Controller 创建 `QueryCriteria` 后调用唯一 query 方法；PageResponse 直接投影 DatasetPage；IT 使用真实 repository/service/MySQL 验证筛选、默认/三档分页、空结果、超界页和精确类型。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；实现提交记录固定 MySQL 8.4.6 聚焦 8/8、两项 mutation、reactor 154/154、三层 Enforcer 与范围/格式/clean 通过。当前四项消费产物相对提交 `9c3fa44` 无差异。

### `M09-T01`

- **Artifact:** `docs/task-designs/M09-T01-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java`、`dto/ApiErrorResponse.java`、`dto/FieldErrorResponse.java`，源自实现提交 `367b0d1`。
- **Decision:** `RequestIdFilter` 在 Servlet 请求期间令响应 `X-Request-Id` 与 MDC `requestId` 使用同一规范 UUID并在 finally 清理；app Controller 使用 Servlet 条件注册，标准错误 DTO 不保存 Throwable 或内部状态。
- **Rationale:** 所有成功/错误响应需要同一请求关联身份，且后续统一异常处理必须复用稳定、安全的公共错误合同。
- **Constraint:** M09-T04 只读取 `RequestIdFilter.MDC_KEY`，不生成第二个 ID、不恢复陈旧 MDC、不提前构造标准错误体；MDC 缺失必须在数据库查询前失败。Controller 保持 `@ConditionalOnWebApplication(SERVLET)`。
- **Usage:** Controller 从 MDC 取得 PageResponse.requestId，MockMvc 安装真实 Filter 并断言 header/body 同值；M09-T04 的私有 TensorException 只携带 M09-T05 后续消费的 ErrorCode。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；实现提交记录聚焦 11/11、reactor test/verify 283/283、六层 Enforcer、ArchUnit、禁止 Git、JAR、mutation、范围/格式/clean 与最终复审通过。当前三项消费产物相对提交 `367b0d1` 无差异。

三项直接输入的职责与约束互补且无冲突：M05 提供可信元数据准入，M06 提供查询与页面语义，M09-T01 提供 HTTP 请求身份和错误合同边界；M09-T04 只在三者之间执行已批准的薄映射。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M09-T04-design.md`；
2. `docs/superpowers/plans/2026-09-04-m09-t04-read-only-dataset-paging.md`；
3. `docs/task-handoffs/M09-T04-handoff.md`；
4. `docs/task-handoffs/tensor-v1-task-board.md` 的 M09-T04 行与详情；
5. `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 Global Constraints、Task M09-T04 和 Module Gate；
6. `docs/contracts/openapi-v1.yaml` 的 records 路径与 `PageResponse`；
7. `docs/task-designs/M05-T02-design.md`、`docs/task-designs/M06-T06-design.md`、`docs/task-designs/M09-T01-design.md`；
8. 交接 Dependencies 节列出的当前生产产物。

首个实施动作：确认 Git 工作树为空后运行计划 Step 1 的 295/295 reactor 基线；基线通过后只完整创建 `DatasetControllerIT.java`，不创建三个生产类型，运行计划 Step 3 并取得只因 `DatasetController`、`PageResponse`、`JacksonPrecisionConfiguration` 缺失而产生的 `tensor-app:testCompile` RED。

## Risks

- Jackson module 会按类型全局处理 boxed `Long`/`BigDecimal`；当前控制量均为 primitive，实施必须用 JSON node 与 mutation 锁定业务字符串和控制 number。未来新增 boxed 控制字段需要重新评估。
- COUNT 与 page 查询继承 M06-T06 的非快照一致性边界；不得为本任务引入长事务。
- `ingested_at` 继承 Core 的 UTC JDBC 读取与 Jackson ISO 输出；部署时区展示仍属于后续配置/UI。
- `DatasetControllerIT` 不进入默认 Surefire；显式 MySQL 8.4.6 的 8/8 和联跑 23/23 不能由默认 reactor 295/295 替代。
- 当前生产代码尚未装配真实 catalog/query Bean；独立 MockMvc 加真实查询协作者不代表完整生产 Servlet context 可启动，M09-T06 必须完成装配。
