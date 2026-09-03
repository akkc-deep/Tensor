# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M09-T02`
- **Next task:** `M09-T03`
- **Design document:** `docs/task-designs/M09-T03-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **Task ID:** `M09-T03`
- **Title:** 同步下载 API 与事务提交后结果
- **Goal:** 交付 `POST /api/v1/downloads` 的同步下载闭环：在数据库事务外完成插件/API/适配器定位、参数准入、上游下载和适配，只由既有持久化服务建立单事务写入，并仅在实际提交后形成 `SUCCESS`；合法空数据形成无时钟、无适配、无写入的 `EMPTY`。
- **Scope:** 只创建 `DownloadService.java`、`DownloadController.java`、`DownloadRequest.java`、`DownloadResponse.java` 和 `DownloadControllerIT.java`；以注入的五项协作者、MDC 请求 ID、真实 acceptance fixture、固定 MySQL 8.4.6 和手工测试装配实现并验证同步下载。不得修改 POM、合同、迁移、既有代码或测试生命周期，不实现标准错误体、查询 API、生产 Bean 总装配、重试、异步、进度、取消或历史。
- **Acceptance criteria:** 入口先拒绝已有数据库事务，再严格执行插件/API/适配器定位、参数校验、一次下载、包络校验、空短路或单次时钟/适配/持久化；插件不可用、数据集误配和无效包络分别形成冻结错误，参数、来源、适配及数据库异常保持既有边界；Controller/两个 DTO 与 OpenAPI 和 `RequestIdFilter` 一致；严格 RED 只因四个生产类型缺失，固定 MySQL 新 IT 10/10、与 `FixtureFlowIT` 联跑 15/15，默认 reactor `test`/`verify` 295/295，mutation、Enforcer、ArchUnit、禁止 Git、JAR、静态、范围、格式、跟踪和 clean 门禁取得设计规定结果；实现提交精确包含五个新 Java 文件并使用 `feat(api): execute synchronous dataset downloads`。

## Dependencies

### `M05-T01`

- **Artifact:** `docs/task-designs/M05-T01-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java` 与 `AdapterRegistry.java`，源自实现提交 `7ea252c`。
- **Decision:** 两个注册表在构造期形成不可变快照；`PluginRegistry.find` 只返回 ID 唯一且可下载的插件，`descriptors()` 保留安全 readiness 元数据，`AdapterRegistry.find` 只返回 key 唯一的 adapter。
- **Rationale:** 通用下载编排必须通过统一扩展发现边界定位插件和适配器，并在上游调用前隔离禁用、缺凭证、重复或损坏扩展，不能依赖具体实现或 first-wins。
- **Constraint:** `DownloadService` 只调用 `find` 和构造期 `descriptors()` 快照，不重新调用插件 `descriptor()`/`readiness()`，不扫描 Bean、不直接注入具体插件/adapter，也不按插件或数据集名字分支。
- **Usage:** 先用 `find(pluginId)` 判定下载能力，再从描述符快照选择同一插件的唯一 API，最后用 `find(DatasetKey)` 取得 adapter；任一缺失均在参数校验和上游调用前停止。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；严格 TDD、聚焦 10/10、模块 `test`/`verify` 89/89、三层 Enforcer、静态/范围/格式/clean 和独立复审均已记录通过，当前两项生产文件的最后实现提交仍为 `7ea252c`。

### `M05-T03`

- **Artifact:** `docs/task-designs/M05-T03-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java` 与 `ValidatedParameters.java`，源自实现提交 `6e86d46` 和固定宽度日期修复 `be26e31`。
- **Decision:** `ParameterValidator.validate(ApiDescriptor, Map<String,Object>)` 是唯一参数准入边界，按元数据产生有序、不可变且实际值均为字符串的 `ValidatedParameters.values()`，并以 `PARAM_REQUIRED|PARAM_INVALID` 及确定字段错误拒绝输入。
- **Rationale:** 上游只能收到已按同一描述符合同完成默认值、类型、枚举、pattern 和范围校验的参数；Controller 与插件都不应复制校验规则。
- **Constraint:** 下载服务必须在 `plugin.download` 前调用 validator，并把返回的同一 values map 传给插件；不得重排、改写、补 Token、回显原始非法值或包装 `ParameterValidationException`。
- **Usage:** 用已选中的 `ApiDescriptor` 校验 `DownloadRequest.params`；成功 map 同时作为插件调用参数和成功包络参数一致性基准。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；最终聚焦 10/10、reactor `test`/`verify` 109/109、三层 Enforcer、静态/范围/格式/clean 和无发现复审均已记录通过，当前生产文件包含 `be26e31` 的严格日期/月边界。

### `M05-T05`

- **Artifact:** `docs/task-designs/M05-T05-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`，源自实现提交 `d7ec551`。
- **Decision:** `DatasetAdapter.adapt(DownloadEnvelope, Instant)` 只接受身份和字段与定义一致的成功包络，按元数据转换完整批次、稳定去重并保留调用方提供的唯一 `ingestedAt`；任一字段、类型或键失败使整批失败。
- **Rationale:** 同步 API 需要复用唯一通用适配边界，把来源数据变成可持久化的不可变 typed batch，而不在服务或 fixture 分支中复制转换逻辑。
- **Constraint:** 只有非空、身份/参数已校验的成功包络可调用 adapter；每请求至多调用一次并传入唯一 `Clock.instant()`，`AdapterException` 原样传播，服务不读取或改写适配行。
- **Usage:** `DownloadService` 从 `AdapterRegistry` 取得 adapter，在非空路径把包络与固定批次时间交给 `adapt`，再把返回的 `AdaptedBatch` 交给持久化服务。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；严格 RED/GREEN、聚焦 11/11、reactor `test`/`verify` 132/132、指纹固定向量、静态/范围/clean 和最终无发现复审均已记录通过；后续 `8ca49d0` 只加固既有测试。

### `M06-T04`

- **Artifact:** `docs/task-designs/M06-T04-design.md`；当前 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java` 与 `GenericUpsertRepository.java`，源自实现提交 `da56663`。
- **Decision:** `PersistenceService.persist(AdaptedBatch)` 对非空批次以 `REQUIRED`/60 秒事务完成预查、准确计数和元数据批量 Upsert，实际提交或回滚完成后才返回或抛出；合法空批次在锁、事务和 JDBC 前返回零计数。
- **Rationale:** API 的插入/更新数必须来自同一真实事务中的预查，任何 SQL 失败必须整批回滚，且成功响应不能在提交前发布。
- **Constraint:** 下载服务不得在外层事务中执行，也不得围绕上游和适配创建事务；只调用一次 `persist`，正常返回后才形成 `SUCCESS`，Spring `DataAccessException`/事务异常及 cause 原样传播给后续 M09-T05。
- **Usage:** 非空适配批次进入 `persist`；返回的 `WriteCounts` 填入 `insertedRows`/`updatedRows`，上游包络原始行数独立填入 `sourceRowCount`。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；固定 MySQL 8.4.6 定向 8/8、reactor `test`/`verify` 146/146、第二 batch 回滚、外层事务锁生命周期、两项 mutation、静态/范围/clean 和独立复审均已记录通过。

### `M07-T04`

- **Artifact:** `docs/task-designs/M07-T04-design.md`；当前 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`，源自实现提交 `608a7a0`。
- **Decision:** `TushareProPlugin` 以不可变 49 API 元数据和 readiness 提供同步单次下载；禁用或缺 Token 在 client 前失败，ready 调用只委托一次 `TushareProClient`，成功/空包络和已分类 `SourceException` 原样传播。
- **Rationale:** 通用下载编排必须能消费真实来源插件的统一 SPI，同时保持来源分类和不可用前置门禁，不引入 Tushare 专用流程。
- **Constraint:** `DownloadService` 不读取 Token、具体 properties/client 或 49 元数据文件，不捕获、重试或改写插件抛出的 `SourceException`，也不按 `tushare_pro` 或 API 名分支。
- **Usage:** 生产后续装配把该插件放入 `PluginRegistry`；本任务通过统一 `DataSourcePlugin.download` 边界消费，其可用性由 registry 先行裁决。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；聚焦 8/8、插件与元数据 58/58、reactor `test`/`verify` 172/172、三项 mutation、秘密/静态/范围/clean 和最终无发现复审均已记录通过；后续 `ae1a7c2` 只加固既有测试。

### `M09-T01`

- **Artifact:** `docs/task-designs/M09-T01-design.md`；当前 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java`，源自实现提交 `367b0d1`。
- **Decision:** `RequestIdFilter` 在过滤链内把规范小写 UUID 同时放入 `X-Request-Id` 和 MDC `requestId`，结束时无条件清理。
- **Rationale:** 下载响应 header、body、日志关联和领域结果必须使用同一请求身份，Controller 不应生成第二个 ID；标准错误包络仍由后续 M09-T05 统一形成。
- **Constraint:** Controller 只从 `MDC.get(RequestIdFilter.MDC_KEY)` 读取并解析现有 UUID，缺失时固定内部失败；不修改 Filter/通用错误 DTO，不新增本地异常 handler，也不把请求 ID 放入可变字段。
- **Usage:** standalone MockMvc 显式安装真实 Filter；Controller 把 MDC UUID 构造成 `RequestId` 传给服务，`DownloadResponse.from` 再投影为 body 字符串并与响应头比较。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；严格 RED、聚焦 11/11、reactor `test`/`verify` 283/283、六层 Enforcer、ArchUnit、禁止 Git、JAR、范围/格式/clean 和最终复审均已记录通过，当前 `RequestIdFilter.java` 的最后实现提交仍为 `367b0d1`。

六项直接依赖的决策和约束无冲突：M05-T01 提供通用可用性与唯一查找，M05-T03/M05-T05 分别负责参数和数据适配，M06-T04 提供唯一数据库事务及提交后计数，M07-T04 提供具体但由统一 SPI 隔离的来源，M09-T01 提供请求关联与结果身份。同步服务依次组合这些边界；生产 Bean 总装配和标准错误 JSON 仍分别留给 M09-T06 与 M09-T05。

## Start Here

1. 完整读取 `docs/task-designs/M09-T03-design.md`，以其中冻结的五文件范围、公开表面、线性阶段、错误矩阵、10 项 IT 和验收命令作为唯一实施合同。
2. 完整读取 `docs/superpowers/plans/2026-09-04-m09-t03-synchronous-download.md`，按 checkbox 顺序执行严格 TDD、mutation、提交和提交态复验。
3. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M09-T03 行、任务详情、六项直接依赖和本交接路径。
4. 读取 `docs/superpowers/plans/tensor-modules/M09-app-api.md` 的 Global Constraints、Task M09-T03 与 Module Gate，并对照 `docs/contracts/openapi-v1.yaml` 的下载路径和两个 schema。
5. 完整读取六项直接依赖设计，再核对当前 registries、validator/validated parameters、adapter、persistence、Tushare plugin、`RequestIdFilter` 和相关 plugin-api records。
6. **First action:** 在干净工作树运行 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am test`，确认基线为 295/295；随后只完整创建 `DownloadControllerIT.java`，保持四个生产类型不存在，并运行计划的聚焦命令取得只因这四个类型缺失的 `tensor-app:testCompile` RED。

## Risks

- 本任务用真实组件手工装配 IT 证明 API 与事务行为，但不交付生产 Servlet Bean 总装配；只有 M09-T06 完成后才能宣称完整生产应用上下文可运行。
- `DownloadEnvelope.FAILURE` 只有安全字符串，没有来源错误码；兼容路径固定映射为 `SOURCE_PAYLOAD_INVALID`，而正常 Tushare/fixture 已分类 `SourceException` 必须原样传播。
- `Clock.instant()` 在数据库事务外、适配前生成，数据库保存精确到毫秒；它是统一批次时间而非 commit timestamp。
- `PersistenceService` 允许加入已有事务，因此同步入口事务守卫不可省略；否则可能把上游纳入事务或在最外层提交前发布成功。
- `DownloadControllerIT` 依赖 Docker/Colima 和官方 `mysql:8.4.6`；环境不可用时必须报告阻塞，不得 skip、替换 H2、浮动镜像或用默认 reactor 结果代替显式 IT。
