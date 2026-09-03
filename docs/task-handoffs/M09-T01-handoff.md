# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M08-T03`
- **Next task:** `M09-T01`
- **Design document:** `docs/task-designs/M09-T01-design.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M09-T01`
- **Title:** Boot 入口、请求标识和通用 API DTO
- **Goal:** 在 `tensor-app` 建立 Spring Boot 根入口和统一请求关联边界，使每个请求在响应头与 MDC 中使用同一规范 UUID，并交付与 OpenAPI/M02 错误合同一致的不可变通用错误 DTO。
- **Scope:** 创建 `TensorApplication`、最高优先级 `RequestIdFilter`、`ApiErrorResponse`、`FieldErrorResponse` 和唯一 `RequestIdFilterTest`，删除旧示例 `data-plane/src/main/java/com/akkc/Main.java`，执行严格 RED/GREEN、283 项 reactor、架构/JAR/范围/clean 门禁；不修改 POM、资源、配置、plugin-api/core/plugin、已有测试，也不提前实现 M09-T02～T06 的 Controller、下载/查询、全局异常、精度、观测、健康或安全配置。
- **Acceptance:** Boot 根入口和 Filter Bean 在隔离尚未交付数据库配置的 smoke context 中启动；只沿用规范小写 UUID，其他值由 `RequestId.newId()` 替换，响应头与链内 MDC 相同且正常/异常后 MDC 为空；两个错误 DTO 的组件、JSON、错误码、retryable 和不可变字段列表符合设计；聚焦 11/11、默认 reactor `test`/`verify` 283/283、Enforcer、ArchUnit、JAR、范围、格式、跟踪和 clean 获得设计规定结果；实现提交精确为五个新增 Java 文件和旧 Main 删除。

## Dependencies

### `M01-T03`

- **Artifact:** 当前 `data-plane/pom.xml`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java` 与 `ForbiddenGitCapabilityTest.java`；门禁源自提交 `efe755a`、`3a6d910`、`d56f683` 并保留后续已验证修订。
- **Decision:** Maven Enforcer 3.6.3 在六层 reactor 禁止 Git/代码托管依赖；ArchUnit 固定模块生产包依赖方向；生产源码扫描拒绝 Git API、子进程和脚本能力。
- **Rationale:** app Boot/API 表面必须在既有模块边界内扩展，且不能把 Git 或代码托管能力引入运行时。
- **Constraint:** M09-T01 不修改父/子 POM或现有门禁测试；新增 `com.akkc.tensor` app 生产类只能沿允许方向依赖 plugin-api/core/plugin，并必须通过真实生产源码扫描。
- **Usage:** 复用现有 app 依赖实施设计的四个生产类型和一个测试，以 `tensor-app -am test/verify` 同时运行 Enforcer、ArchUnit 与禁止能力门禁。
- **Readiness evidence:** M01-T03 在权威看板中为 `COMPLETED`；其最终 `validate`、聚焦/全 reactor `test` 与 `verify`、13 项 app 架构测试和六层 Enforcer 已记录通过。M08-T03 提交态最近一次完整 reactor `test`/`verify` 各为 272/272，Enforcer 与 ArchUnit 继续通过。

### `M02-T01`

- **Artifact:** 提交 `4078dad6f2becb2cbcd4239c5aa5bace21fed5a5` 的 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/RequestId.java`。
- **Decision:** `RequestId` 是只保存非 null `UUID` 的不可变 record；唯一服务端工厂 `newId()` 不接收用户输入并生成 UUID version 4、RFC 4122 variant 2。客户端头沿用逻辑明确留给 app 的 `RequestIdFilter`。
- **Rationale:** 请求关联需要一个不携带客户端任意字符串或外部状态的服务端身份来源，同时允许 app 在自身信任边界内决定是否沿用客户端值。
- **Constraint:** 不修改 plugin-api 或增加字符串解析工厂；Filter 只沿用项目所有者批准的规范小写 UUID，解析后仍形成现有 `RequestId`，其余值一律调用 `newId()`。
- **Usage:** `RequestIdFilter` 用 `UUID.fromString` 把已通过固定正则的客户端值构造成 `RequestId`，或调用 `RequestId.newId()`，再把 `value().toString()` 同步写入响应头和 MDC。
- **Readiness evidence:** M02-T01 在权威看板中为 `COMPLETED`；提交精确包含五个值对象和 `IdentifierTest`，聚焦/模块 `test`/`verify` 均记录 26/26，Enforcer、JDK-only、范围、clean 和独立审查通过；最近完整 reactor 272/272 继续覆盖该合同。

### `M02-T05`

- **Artifact:** 提交 `445b941` 的 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java` 与 `TensorException.java`，以及修复提交 `dd495ee` 加固的 `PluginApiSurfaceTest.java` 公共错误合同门禁。
- **Decision:** `ErrorCode` 是 16 项闭集并保存唯一 retryable 真值；`TensorException.retryable()` 只委托该 enum，安全消息不携带 HTTP、Token、原始响应、请求头、SQL、堆栈或内部路径。
- **Rationale:** app 错误 DTO 和后续异常映射必须复用同一错误身份与重试判断，避免在 HTTP 层形成可漂移的第二套矩阵或泄漏异常内部状态。
- **Constraint:** `ApiErrorResponse.code` 直接使用 `ErrorCode`，其 `retryable` 必须等于 `code.retryable()`；DTO 不序列化 Throwable/cause/stack 或新增错误码、HTTP 映射和敏感诊断字段。
- **Usage:** 两个错误 DTO 以 record 公开 OpenAPI 字段；`ApiErrorResponse` 在 compact constructor 中校验 ErrorCode/retryable 一致性并防御复制 `FieldErrorResponse` 列表。
- **Readiness evidence:** M02-T05 在权威看板中为 `COMPLETED`；修复后的聚焦测试 8/8、模块 `verify` 79/79、Enforcer、`jdeps=java.base`、禁用依赖、clean、范围和独立复审均记录通过；最近完整 reactor 272/272 继续覆盖该合同。

- **Dependency comparison:** 三项输入职责互补且无冲突：M01-T03 约束模块和禁止能力，M02-T01 提供 UUID 请求身份，M02-T05 提供错误码与 retryable 真值。M09-T01 只在 app 信任边界校验客户端 UUID、传播字符串并定义 HTTP DTO，不反向修改任何输入合同，也不提前承担 HTTP 异常映射。

## Start Here

1. 完整读取 `docs/task-designs/M09-T01-design.md`。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M09-T01 行、任务详情和本交接路径。
3. 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 Global Constraints、Task M09-T01 与 Module Gate。
4. 对照 `docs/contracts/openapi-v1.yaml` 的 `ApiError`/`FieldError` 与 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 17.1 请求关联。
5. 核对 M01-T03 三项当前门禁文件、M02-T01 `RequestId.java`、M02-T05 `ErrorCode.java`/`TensorException.java` 和 `data-plane/tensor-app/pom.xml` 的现有依赖。
6. **First action:** 不创建任何生产类型或删除旧 Main，先完整创建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RequestIdFilterTest.java` 的 11 次测试执行，运行设计给出的聚焦 Maven 命令，并确认它只因四个生产交付类型缺失在 `tensor-app:testCompile` 非零，记录为严格 RED。

## Risks

- 客户端 uppercase UUID 和其他 tracing 格式不会沿用，而会收到新服务端 UUID；这是项目所有者批准的兼容合同，不得在实现中放宽或静默规范化。
- `OncePerRequestFilter` 默认 async/error dispatch 语义只保证当前处理线程的 MDC 生命周期；本任务不实现跨线程传播，未来异步 Controller 需要独立设计。
- Boot smoke test 只在测试 builder 排除尚未交付的 JDBC/Flyway 自动配置；生产入口不得携带该排除，测试结果也不代表无数据库配置的完整生产应用已可运行。
- Mockito/Byte Buddy 在受限沙箱可能无法 self-attach；需在允许 attach 的环境取得 GREEN，不得修改 POM或把环境失败当成有效 RED。
