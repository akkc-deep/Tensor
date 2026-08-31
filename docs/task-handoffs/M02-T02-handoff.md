# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M02-T01`
- **Next task:** `M02-T02`
- **Design document:** `docs/task-designs/M02-T02-designs.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M02-T02`
- **Title:** 参数、API、插件描述符和 readiness
- **Goal:** 在无 Spring 业务依赖的 `tensor-plugin-api` 中交付参数类型、查询模式、参数/API/插件描述符和非敏感 readiness，使后续元数据加载、插件注册、参数校验与 REST 映射共享同一不可变契约。
- **Scope:** 只在 `com.akkc.tensor.plugin.api.descriptor` 创建六个生产类型和一个 `PluginDescriptorTest.java`；不修改 POM、M02-T01 值对象、其他模块、资源或配置，不提前实现 `DatasetDefinition`、下载包络、SPI、异常、参数校验器、REST DTO 或 YAML 加载器。
- **Acceptance:** 六个公开类型及其 components、枚举闭集、不可变集合、重复名/引用/readiness 构造不变量与 `List<DatasetKey>` 数据集契约精确符合 `docs/task-designs/M02-T02-designs.md`；目标 RED、聚焦 GREEN、模块 `test`/`verify`、M02-T01 回归、Enforcer、七文件范围和固定提交消息均得到设计注明的预期结果，且公开契约不保存 Token、凭证值、配置路径或认证头。

## Dependencies

### `M02-T01`

- **Artifact:** 提交 `4078dad6f2becb2cbcd4239c5aa5bace21fed5a5` 中 `com.akkc.tensor.plugin.api.model` 的 `PluginId`、`ApiName` 和 `DatasetKey`，以及同提交的值对象测试基线。
- **Decision:** 三个值对象由 public canonical constructors 执行 null/格式不变量；`PluginId` 与 `ApiName` 精确使用 `^[a-z][a-z0-9_]{1,63}$`，不 trim、不改写；`DatasetKey` 只保存非 null 的两个精确组件。M02-T02 已批准把 `PluginDescriptor.datasets` 固定为 `List<DatasetKey>`。
- **Rationale:** 复用已校验值对象避免描述符重复传递裸字符串；使用 `DatasetKey` 让 M02-T02 只依赖已完成任务并可独立编译，不提前引入 M02-T03 的完整 `DatasetDefinition`。
- **Constraint:** 不修改或绕过三个值对象的 canonical constructor；API 和插件描述符直接持有这些类型。每个数据集键必须属于描述符插件且引用已声明 API，列表有序、不可变且无重复。
- **Usage:** `ApiDescriptor.apiName` 使用 `ApiName`，`PluginDescriptor.pluginId` 使用 `PluginId`，`PluginDescriptor.datasets` 使用 `List<DatasetKey>`，并以这些值对象执行重复与引用一致性检查。
- **Readiness evidence:** M02-T01 在权威看板中为 `COMPLETED`；实现提交精确六个 Java 文件。控制器新鲜聚焦、模块 `test` 与 `verify` 均执行 26 项、0 failure、0 error、0 skipped，reactor 2/2 `SUCCESS`，Enforcer 通过；任务级修复复审全部 addressed，最终整体审查为 `Ready to merge: Yes`，无 Critical/Important/Minor。

## Start Here

1. `docs/task-designs/M02-T02-designs.md` 全文。
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M02-T02 行与任务详情。
3. `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 Global Constraints、Task M02-T02 和 Module Gate。
4. `docs/contracts/dataset-definition.schema.json` 的参数对象、六个参数类型与四个查询模式，以及 `docs/contracts/openapi-v1.yaml` 的 `DataSourceSummary`/`ApiDescriptor`。
5. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 5.2、5.3、6.1、6.2。
6. M02-T01 的三个直接消费类型及 `data-plane/pom.xml`、`data-plane/tensor-plugin-api/pom.xml`。
7. **First action:** 不创建任何生产 descriptor 类型，先按设计完整创建 `PluginDescriptorTest.java`，运行设计给出的聚焦 Maven 命令，并记录它因六个生产类型缺失而在 `testCompile` 非 0 的 RED。

## Risks

- `QueryMode` 的四个 enum constants 有意直接使用小写契约值，使 `name()` 与 YAML/OpenAPI 一致；不得另加大小写转换器或别名。
- `PluginReadiness` 与 `PluginDescriptor` 都携带四个状态 components，必须执行同一真值约束，但不得向 `PluginDescriptor` 增加第三份 readiness 状态或额外别名方法。
- M01 POM 基线会产生平台编码提示；本任务不得以消除既有提示为由修改 POM，实际新增警告、失败或异常仍需处理。
