# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M02-T03`
- **Next task:** `M02-T04`
- **Design document:** `docs/task-designs/M02-T04-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M02-T04`
- **Title:** `DownloadEnvelope`、`AdaptedBatch` 和执行结果
- **Goal:** 在 Java 21 `tensor-plugin-api` 模块中交付不可变的下载包络、适配批次和下载结果公共契约，使插件、适配器、核心编排、持久化和 REST 映射共享同一组已校验的数据形状，并明确区分上游成功、上游失败、合法空结果和有数据结果。
- **Scope:** 创建任务卡指定的五个公开生产类型和两个测试文件，执行集合/嵌套行复制、状态、计数、表名、列/行和业务键引用不变量、严格 TDD、模块回归及 Enforcer 门禁；不修改 POM、M00 契约、既有类型或其他模块，不提前实现 SPI、领域异常、核心编排、持久化、HTTP/REST、具体插件或前端职责。
- **Acceptance criteria:** 两个枚举和三个 records 的公开形状与 `docs/task-designs/M02-T04-design.md` 逐项一致；成功、合法空结果、失败、半包络、批次行形状、业务 null、唯一批次时间和结果计数规则可观察；聚焦测试经历可归因的缺失类型 RED 后 GREEN，模块 `test`/`verify`、Enforcer、范围、格式和精确七文件提交门禁全部通过。

## Dependencies

### `M02-T01`

- **Artifact:** 提交 `4078dad6f2becb2cbcd4239c5aa5bace21fed5a5` 中的 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/PluginId.java`、`ApiName.java`、`DatasetKey.java`、`TableName.java` 和 `RequestId.java`。
- **Decision:** 公共标识使用已校验值对象；`DatasetKey` 组合 `PluginId`/`ApiName`，`TableName.from(DatasetKey)` 唯一派生 `<plugin_id>__<api_name>`，`RequestId` 保存非 null UUID。
- **Rationale:** 下载公共契约复用稳定身份和值语义，避免裸字符串重复校验、表名分叉或请求标识重新建模。
- **Constraint:** 标识不 trim、不改写大小写；批次表名必须与数据集键派生值相等；不得在新 records 中用 `String` 替换这些值对象。
- **Usage:** `DownloadEnvelope` 与 `DownloadResult` 直接保存 `PluginId`/`ApiName`；`AdaptedBatch` 直接保存 `DatasetKey`/`TableName`；`DownloadResult` 直接保存 `RequestId`。
- **Readiness evidence:** M02-T01 在权威看板中为 `COMPLETED`；其标识符聚焦测试 26/26、模块回归、Enforcer、范围和最终独立审查均已记录通过。

### `M02-T03`

- **Artifact:** 提交 `551c18f20674da29d8fb962765184bd6e105a596` 与修复提交 `0a74740` 中的 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyDefinition.java` 及其已冻结设计 `docs/task-designs/M02-T03-design.md`。
- **Decision:** `BusinessKeyDefinition` 保存非 null `COMPOSITE|FINGERPRINT` 模式和至少一个有序、唯一、合法的身份字段；数据集列名使用统一标识正则并保持声明顺序。
- **Rationale:** 适配批次必须携带与数据集元数据相同的业务键定义，使后续持久化按统一身份契约处理而不复制字段列表或键模式。
- **Constraint:** `AdaptedBatch.businessKeyDefinition` 必须直接复用该类型，键字段必须引用批次 `columns`；不得在批次中重新定义键模式、排序或规范化字段名。
- **Usage:** `AdaptedBatch` 保存 `BusinessKeyDefinition`，在构造时核对其所有 fields 均存在于 `columns`；字段/列自身继续遵守 M02-T03 的标识和有序语义。
- **Readiness evidence:** M02-T03 在权威看板中为 `COMPLETED`；最终聚焦测试 9/9、模块 `test`/`verify` 54/54、两层 Enforcer、范围和格式门禁通过，范围化复审无 Critical、Important 或 Minor，结论为 `Ready to proceed: Yes`。

- **Dependency comparison:** M02-T01 提供下载与批次的身份、派生表名和请求标识，M02-T03 提供批次业务键及列名语义；两者使用相同的不规范化标识约束，职责互补且无冲突。M02-T04 已批准的 `SUCCESS|FAILURE`、安全字符串错误、`int rowCount` 和 `long` 结果计数只补充下载状态/计数，不改变任何依赖类型。

## Start Here

1. 完整读取 `docs/task-designs/M02-T04-design.md`。
2. 核对 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 Global Constraints、Task M02-T04 与 Module Gate。
3. 核对 `docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md` 的跨模块稳定接口和数据形状。
4. 核对 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 5.4、5.5，及 `docs/contracts/openapi-v1.yaml` 的 `DownloadResponse`。
5. 核对上述 M02-T01/M02-T03 直接依赖类型和当前模块测试基线。
6. 首个实施动作：只完整创建 `DownloadEnvelopeTest.java` 与 `AdaptedBatchTest.java`，不创建五个生产类型，然后运行设计中的聚焦 Maven 命令，确认因五个生产类型缺失在 `testCompile` 退出非 0。

## Risks

- 来源/目标行允许 null 单元格或 value，不能对单行直接使用会拒绝 null 的 `List.copyOf`/`Map.copyOf`；必须复制容器并保持其不可修改，同时仍拒绝 null 行和 null key。
- `Map<String,Object>` 与行单元只对容器做防御性复制；不要放入需要通用深复制的可变业务对象。
- `DownloadEnvelope.error` 只能保存已分类的安全摘要，不能包含 Token、原始上游响应、请求头、堆栈或内部路径；后续领域异常不得反向成为 M02-T04 的编译依赖。
