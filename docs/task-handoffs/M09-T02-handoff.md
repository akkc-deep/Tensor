# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M09-T01`
- **Next task:** `M09-T02`
- **Design document:** `docs/task-designs/M09-T02-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **Task ID:** `M09-T02`
- **Title:** 数据源、接口和数据集元数据 API
- **Goal:** 在 `tensor-app` 中交付 OpenAPI 已冻结的四条只读元数据路由，使调用方查看安全的数据源 readiness 摘要、可下载插件的 API/参数定义和启动校验通过的数据集展示定义；插件缺 Token 时仍能取得已入库数据查询所需的元数据。
- **Scope:** 创建 `DataSourceController.java`、`DataSourceResponse.java`、`ApiDescriptorResponse.java`、`DatasetDefinitionResponse.java` 和 `DataSourceControllerTest.java`，仅在 `tensor-app/pom.xml` 增加 test scope 的 `spring-test`；实现四条 GET 路由、精确 DTO 投影、两种受控 409 和 12 次独立 MockMvc 测试。不装配真实 registry/catalog Bean，不修改 plugin-api/core/plugin/OpenAPI/既有 app 类型，不实现下载、records 分页、统一错误体、配置、健康、指标或安全。
- **Acceptance criteria:** 四条路由只消费 `PluginRegistry`/`DatasetCatalog`，Controller 仅在 Servlet Web 应用中注册；ready/unavailable 数据源、49 API、数据集摘要和完整定义 JSON 与 OpenAPI 一致且无敏感/内部字段；未知或不可下载插件的 API 请求为 `409 + PLUGIN_DISABLED`，未知插件/数据集或不可安全投影定义为 `409 + DATASET_MISCONFIGURED`，缺 Token/禁用不阻止已验证数据集查询；严格 RED 后聚焦 12/12、reactor test/verify 295/295 及 Enforcer、ArchUnit、禁止 Git、JAR、范围、格式、跟踪和 clean 门禁取得设计规定结果；实现提交精确包含五个 Java 文件和一个 POM 修改。

## Dependencies

### `M05-T01`

- **Artifact:** `docs/task-designs/M05-T01-design.md` 与 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`；实现提交 `7ea252c`，测试增强提交 `ca39a34`。
- **Decision:** `PluginRegistry` 在构造期形成不可变 readiness 快照；`descriptors()` 按 pluginId/displayName 稳定排序并保留禁用、缺凭证和重复冲突描述符，`find()` 只暴露 ID 唯一且当前可下载的插件。
- **Rationale:** 元数据 API 需要同时展示不可用插件的安全状态，并严格区分“已注册”与“可下载”，不能重新调用插件或用下载查找结果判断已入库数据能否查询。
- **Constraint:** Controller 直接消费 `descriptors()` 快照，不调用 `DataSourcePlugin.descriptor()`/`readiness()`，不把允许重复 pluginId 的列表收集为唯一 key map，也不按具体插件分支；API 列表只接受可下载描述符，数据集路由只要求存在同 ID 描述符。
- **Usage:** 数据源列表逐项投影整个描述符快照；API 路由按 pluginId 和 `downloadAvailable` 选择描述符；数据集路由用同一快照确认插件已注册。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；最终聚焦 10/10、模块 test/verify 89/89、三层 Enforcer、静态/范围/格式/clean 和独立复审均已记录通过。当前 `PluginRegistry.java` 相对实现提交 `7ea252c` 无差异。

### `M05-T02`

- **Artifact:** `docs/task-designs/M05-T02-design.md` 与 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java`；实现提交 `57771b0`。
- **Decision:** `DatasetCatalog` 只保存启动元数据和实际 schema 校验通过、DatasetKey 唯一的定义；`find(DatasetKey)` 精确返回 optional，`list(PluginId)` 按 apiName 升序返回不可修改列表，缺失插件返回空列表。
- **Rationale:** REST 只能公开可安全用于查询的定义；单个缺表或 schema 漂移应由启动准入边界隔离，不应由 Controller 重新读取 YAML、数据库或诊断集合。
- **Constraint:** Controller 只调用 `find/list`，不构造目录、不重复启动校验、不访问 JDBC metadata、不恢复被隔离定义，也不把已知插件的合法空目录误判为错误。
- **Usage:** 数据集列表把 `list(pluginId)` 映射为摘要；完整定义路由用 `find(DatasetKey)` 取得唯一可信定义，再投影 filters、fixedColumn 和业务 columns。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；实现提交在最终状态记录聚焦 10/10、模块 test/verify 99/99、三层 Enforcer、静态/范围/格式/clean 和无 Critical/Important 复审通过。当前 `DatasetCatalog.java` 相对提交 `57771b0` 无差异。

### `M09-T01`

- **Artifact:** `docs/task-designs/M09-T01-design.md`，以及实现提交 `367b0d1` 的 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java` 和 `FieldErrorResponse.java`。
- **Decision:** `RequestIdFilter` 以最高优先级为每个请求设置规范 `X-Request-Id` 和同值 MDC，并在结束时无条件清理；`ApiErrorResponse` 直接使用闭集 `ErrorCode` 及其 retryable 真值，不序列化异常内部状态。
- **Rationale:** 元数据 API 的成功与失败响应必须共用已验证的请求关联边界；M09-T02 只需要携带领域错误码，标准错误包络应由预定义 M09-T05 统一生成，避免 Controller 复制一套错误体。
- **Constraint:** 独立 MockMvc 显式安装真实 `RequestIdFilter` 并断言每个响应头；不修改 Filter/通用错误 DTO，不在 Controller 保存 request ID 或构造 `ApiErrorResponse`。M09-T02 私有异常必须继承 `TensorException`，只携带 `PLUGIN_DISABLED`/`DATASET_MISCONFIGURED`，使 M09-T05 可统一消费。
- **Usage:** MockMvc 请求通过真实 Filter；成功响应只返回本任务 DTO，受控失败由 `@ResponseStatus(CONFLICT)` 提供当前 409，并从 resolved `TensorException` 验证 code。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；提交 `367b0d1` 的聚焦测试 11/11、完整 reactor test/verify 283/283、六层 Enforcer、ArchUnit、禁止 Git、JAR、范围、格式、跟踪、clean 和范围化复审均已记录通过。当前三项消费产物相对该提交无差异。

三项依赖决策和约束无冲突：M05-T01 提供“已注册/可下载”的安全插件快照，M05-T02 提供“可查询”的已验证数据集目录，M09-T01 提供所有路由共用的请求关联和后续错误包络类型。M09-T02 只在 app REST 边界组合这三项输入；缺 Token 只影响 API 下载能力，不删除描述符或 catalog 定义。真实 Bean 装配和标准错误 JSON 仍分别属于后续装配边界与 M09-T05。

## Start Here

1. 完整读取 `docs/task-designs/M09-T02-design.md`，以其中冻结的四条路由、DTO components、投影/错误矩阵、12 次测试和六文件范围作为唯一实施合同。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M09-T02 行、任务详情、三项直接依赖和本交接路径。
3. 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 Global Constraints、Task M09-T02 与 Module Gate，并对照 `docs/contracts/openapi-v1.yaml` 的四条元数据路径和四个相关 schema。
4. 完整读取 `docs/task-designs/M05-T01-design.md`、`docs/task-designs/M05-T02-design.md`、`docs/task-designs/M09-T01-design.md`，再核对当前 `PluginRegistry.java`、`DatasetCatalog.java`、`RequestIdFilter.java`、通用错误 DTO、领域 descriptor/dataset records 和 `tensor-app/pom.xml`。
5. **First action:** 只在 `tensor-app/pom.xml` 增加 test scope 的 `org.springframework:spring-test`，完整创建 `DataSourceControllerTest.java`，保持四个生产交付类型不存在；随后运行设计的聚焦 Maven 命令，取得只因 `DataSourceController`、`DataSourceResponse`、`ApiDescriptorResponse`、`DatasetDefinitionResponse` 缺失而在 `tensor-app:testCompile` 失败的严格 RED。

## Risks

- 当前生产代码尚无 `PluginRegistry` 和 `DatasetCatalog` Spring Bean 装配；本任务的独立 MockMvc 结果不得表述为完整生产 Servlet context 已可启动。
- M09-T05 交付前，受控失败只有 HTTP 409 和 resolved exception 中的准确错误码，没有标准 `ApiErrorResponse` JSON；Controller 不得临时形成第二套错误体。
- `PluginRegistry.descriptors()` 允许相同 pluginId 的重复冲突描述符；不得把它收集成唯一 key map。API 路由只接受可下载项，数据集路由按已注册描述符与 catalog 分别决定。
- Mockito/Byte Buddy 在受限沙箱可能无法 self-attach；测试需在允许 attach 的 Maven 环境取得真实 GREEN，不得修改依赖或降低测试绕过环境失败。
