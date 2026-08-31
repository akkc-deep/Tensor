# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M02-T02`
- **Next task:** `M02-T03`
- **Design document:** `docs/task-designs/M02-T03-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M02-T03`
- **Title:** 数据集字段、业务键、筛选和展示定义
- **Goal:** 在 Java 21 `tensor-plugin-api` 模块中交付不可变的数据集元数据公共契约，使后续 YAML 加载、通用适配、白名单 SQL、查询和 REST 映射共享同一组已校验的字段、业务键、筛选与展示定义。
- **Scope:** 创建任务卡指定的六个公开生产类型和 `DatasetDefinitionTest.java`，执行构造期局部/引用不变量、严格 TDD、模块回归和 Enforcer 门禁；不修改 M00 schema、POM、既有类型或其他模块，不实现 YAML/REST/数据库/适配/SPI/前端职责。
- **Acceptance criteria:** 两个枚举、四个 records、默认批量构造器、保序不可变集合、表名和字段引用约束与 `docs/task-designs/M02-T03-design.md` 逐项一致；完整 `daily` 定义成功，任务卡反例被拒绝；聚焦测试经历可归因的缺失类型 RED 后 GREEN，模块 `test`/`verify`、范围、格式和精确七文件提交门禁全部通过。

## Dependencies

### `M02-T01`

- **Artifact:** 提交 `4078dad6f2becb2cbcd4239c5aa5bace21fed5a5` 中的 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/DatasetKey.java` 与 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/TableName.java`。
- **Decision:** `DatasetKey` 只保存已校验的 `PluginId`/`ApiName`，`TableName.from(DatasetKey)` 唯一派生 `<plugin_id>__<api_name>`。
- **Rationale:** 后续公共契约复用稳定值对象，避免裸字符串重复校验或形成第三份表名状态。
- **Constraint:** 不 trim、不改写标识符；不得绕过 `TableName.from` 接受与数据集键不一致的表名。
- **Usage:** `DatasetDefinition.datasetKey` 和 `tableName` 直接使用这两个类型，并在构造期断言 `tableName.equals(TableName.from(datasetKey))`。
- **Readiness evidence:** M02-T01 在权威看板中为 `COMPLETED`；其 26 项聚焦测试、模块 `test`/`verify`、Enforcer 和精确六文件范围已记录通过，当前执行前模块基线 45/45 测试通过。

### `M02-T02`

- **Artifact:** 提交 `7984f0c` 中的 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/QueryMode.java` 与 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ParameterDescriptor.java`。
- **Decision:** `QueryMode` 固定为四个小写 enum constants；`ParameterDescriptor` 保存已校验、保序不可变的参数局部定义，参数名满足统一标识正则。
- **Rationale:** 数据集定义与已发布 API/参数描述符共用同一公共类型，避免复制枚举或参数形状。
- **Constraint:** 不新增查询模式别名，不复制或改写参数对象；跨参数引用与日期关系仍留给后续 M03/M05。
- **Usage:** `DatasetDefinition.queryMode` 与 `parameters` 直接使用这两个类型，并仅在聚合层拒绝重复参数名和复制参数列表。
- **Readiness evidence:** M02-T02 在权威看板中为 `COMPLETED`；其 19 项聚焦测试、模块 `test`/`verify`、Enforcer 和精确七文件范围已记录通过，当前执行前模块基线 45/45 测试通过。

- **Dependency comparison:** 两项依赖的标识正则、不可变集合和不规范化约束一致；M02-T01 提供数据集/表标识，M02-T02 提供查询模式/参数描述符，职责不重叠且与已批准设计无冲突。

## Start Here

1. 完整读取 `docs/task-designs/M02-T03-design.md`。
2. 按 `docs/superpowers/plans/2026-08-31-m02-t03-dataset-metadata-model.md` 的八个步骤执行。
3. 核对 `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 Global Constraints、Task M02-T03 与 Module Gate。
4. 核对 `docs/contracts/dataset-definition.schema.json`、上述 M02-T01/M02-T02 直接依赖类型及当前模块测试基线。
5. 首个实施动作：只创建完整 `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinitionTest.java`，不创建生产类型，然后运行计划中的聚焦 Maven 命令，确认因六个生产类型缺失在 `testCompile` 退出非 0。

## Risks

- M00-T02 schema 的 `filters` 是字符串数组且没有 `batchSize`；已批准设计固定 `FilterDefinition` 仅包装 `field`，并以省略 batchSize 的 Java 构造器默认 500，不得修改已冻结 schema 或提前实现后续映射。
- Maven 基线存在已记录的平台编码警告；它不属于本任务范围，但不得引入新的警告类别。
