# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M02-T04`
- **Next task:** `M02-T05`
- **Design document:** `docs/task-designs/M02-T05-design.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M02-T05`
- **Title:** `DataSourcePlugin`、`DatasetAdapter` 和领域错误
- **Goal:** 在 Java 21 `tensor-plugin-api` 模块中发布无 Spring、JDBC、HTTP 或具体插件依赖的最小数据源/适配器 SPI，并以统一错误码和 retryable 真值表达受控领域失败。
- **Scope:** 创建两个公开 SPI、`error` 子包下四个公开领域错误类型和一个真实反射测试，执行严格 TDD、模块回归、Enforcer、`jdeps`、范围与提交门禁；不修改 POM、M00 契约、M02-T01～T04 既有类型或其他模块，不提前实现插件、适配器、注册、参数校验、持久化、REST/HTTP 或前端职责。
- **Acceptance:** 两个 SPI 的精确方法表面、16 项错误码顺序/retryable 真值、抽象基类与两个受限最终异常均符合 `docs/task-designs/M02-T05-design.md`；聚焦测试经历可归因 RED 后 GREEN，模块 `test`/`verify`、Enforcer、`jdeps`、禁用依赖扫描、范围、格式和精确七文件提交门禁得到设计注明的结果。

## Dependencies

### `M00-T03`

- **Artifact:** `docs/contracts/error-codes.md` 与 `docs/contracts/openapi-v1.yaml` 的 `ApiError.code` 契约。
- **Decision:** 错误闭集固定为目录顺序的 16 项，retryable 真值逐项固定；HTTP 状态属于后续 REST 映射，不进入 plugin-api。
- **Rationale:** 插件、适配器、核心服务和 REST 层必须共享同一错误身份与重试判断，同时保持 plugin-api 不依赖 HTTP。
- **Constraint:** 不得改名、重排、增删错误码或接受独立 retryable 参数；异常消息不得包含 Token、原始上游响应、请求头、SQL、堆栈或内部路径。
- **Usage:** `ErrorCode` 按目录顺序声明常量并保存 retryable 真值，`TensorException.retryable()` 只委托该值。
- **Readiness evidence:** M00-T03 在权威看板中为 `COMPLETED`；OpenAPI、错误目录及相关结构/语义/示例验证均已记录通过，最终审查无未解决 Critical 或 Important。

### `M02-T02`

- **Artifact:** 提交 `7984f0c` 中的 `PluginDescriptor`、`PluginReadiness` 及其复用的 `ApiName` 公共契约。
- **Decision:** 插件描述符和 readiness 是不含凭证值/路径的不可变公共类型，API 身份继续使用已校验 `ApiName` 值对象。
- **Rationale:** 数据源 SPI 应直接复用已发布描述符和 readiness，避免平行 DTO、裸字符串身份或敏感配置表面。
- **Constraint:** `DataSourcePlugin` 只能暴露任务卡指定的三个方法，不增加 Spring annotation、Bean、凭证字段、重载或 default/static 方法。
- **Usage:** `descriptor()`、`readiness()` 和 `download(ApiName, Map<String,Object>)` 直接引用这些类型。
- **Readiness evidence:** M02-T02 在权威看板中为 `COMPLETED`；聚焦测试、模块 `test`/`verify`、Enforcer 与精确七文件范围已记录通过，最终审查无 Critical/Important/Minor。

### `M02-T03`

- **Artifact:** 提交 `551c18f20674da29d8fb962765184bd6e105a596` 与修复提交 `0a74740` 中的 `DatasetDefinition` 及其复用的 `DatasetKey` 公共契约。
- **Decision:** 数据集身份和定义使用已校验、不可变、保序的公共类型，适配器不得复制元数据形状或执行数据库职责。
- **Rationale:** `DatasetAdapter` 需要以统一身份声明自身数据集，并向注册、适配、持久化和查询链路提供同一元数据定义。
- **Constraint:** `DatasetAdapter` 只能暴露任务卡指定的三个方法；不得增加业务键生成、类型转换、持久化、查询或框架依赖。
- **Usage:** `datasetKey()` 和 `definition()` 直接返回现有类型，`adapt(...)` 的输出继续遵守数据集定义与业务键约束。
- **Readiness evidence:** M02-T03 在权威看板中为 `COMPLETED`；最终聚焦测试 9/9、模块 `test`/`verify` 54/54、两层 Enforcer、范围与格式门禁已记录通过，范围化复审无 Critical/Important/Minor。

### `M02-T04`

- **Artifact:** 提交 `075d1d4` 中的 `DownloadEnvelope` 与 `AdaptedBatch` 公共契约。
- **Decision:** 下载包络以 `SUCCESS|FAILURE` 区分来源结果并只携带 nullable 安全字符串错误；适配批次保存已校验数据集/表/列/行/业务键和唯一摄取时间。
- **Rationale:** 两个 SPI 只需要连接已经冻结的同步下载边界和适配边界，不应反向改变数据形状或形成错误类型依赖环。
- **Constraint:** `DataSourcePlugin.download(...)` 与 `DatasetAdapter.adapt(...)` 必须逐字使用现有类型；领域异常不得反向进入 M02-T04 records，SPI 不执行网络或数据库实现。
- **Usage:** `download(...)` 返回 `DownloadEnvelope`；`adapt(DownloadEnvelope, Instant)` 返回 `AdaptedBatch`。
- **Readiness evidence:** M02-T04 在权威看板中为 `COMPLETED`；最终聚焦测试 17/17、模块 `test`/`verify` 71/71、Enforcer、`clean`、范围与格式门禁已记录通过，独立审查无 Critical/Important/Minor。

- **Dependency comparison:** 四项输入职责互补且无冲突：M00-T03 冻结错误身份和 retryable，M02-T02/M02-T03/M02-T04 分别提供数据源描述、数据集定义与下载/适配数据形状；共同约束是复用既有不可变类型、不保存敏感信息、不引入框架或反向依赖。

## Start Here

1. 完整读取 `docs/task-designs/M02-T05-design.md`。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M02-T05 行与任务详情。
3. 核对 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 Global Constraints、Task M02-T05 与 Module Gate。
4. 核对 `docs/contracts/error-codes.md`、`docs/contracts/openapi-v1.yaml` 的 `ApiError.code`，以及上述 M02-T02～T04 直接消费类型。
5. **First action:** 不创建任何生产类型，先完整创建 `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/PluginApiSurfaceTest.java`，运行设计给出的聚焦 Maven 命令，并确认它因六个交付类型缺失在 `testCompile` 非 0 的 RED。

## Risks

- `RuntimeException` 继承的 cause、stack 和 suppressed 状态仍存在；本任务通过最小构造器和无新增诊断字段收窄主动表面，但后续实现仍不得把不安全 Throwable 跨模块或响应传播。
- M01 POM 基线可能继续输出已知的平台编码提示；本任务不得为消除既有提示而修改 POM，也不得引入新的警告类别。
