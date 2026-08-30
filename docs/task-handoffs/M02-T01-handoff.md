# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M01-T03`
- **Next task:** `M02-T01`
- **Design document:** `docs/task-designs/M02-T01-designs.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M02-T01`
- **Title:** `PluginId`、`ApiName`、`DatasetKey`、`TableName`、`RequestId`
- **Goal:** 在无 Spring 业务依赖的 `tensor-plugin-api` 中交付五个受校验不可变 records，统一插件/接口标识、数据集组合、表名派生和服务端 UUID 请求标识。
- **Scope:** 只在 `com.akkc.tensor.plugin.api.model` 创建五个生产 records 和一个 `IdentifierTest.java`；不修改 POM、M01 门禁、其他模块、资源、配置、旧 Main 或前端，不提前实现描述符、数据集定义、下载包络、SPI、异常和 RequestIdFilter。
- **Acceptance:** 两个字符串标识精确执行 2～64 字符冻结正则且拒绝任务卡列出的非法输入；`DatasetKey` 只保存非 null 组件；`TableName.from(tushare_pro/daily)` 精确产生 `tushare_pro__daily`；`RequestId.newId()` 产生 version 4 / variant 2 UUID；目标 RED、聚焦 GREEN、模块 `test`/`verify`、范围和六文件提交门禁全部得到设计注明的预期结果。

## Dependencies

### `M01-T02`

- **Artifact:** 提交 `6e692b9229cba8bbe5e83307402bcc5d1bfad14c` 中的 `data-plane/pom.xml` 与 `data-plane/tensor-plugin-api/pom.xml`。
- **Decision:** Java release 固定为 21，JUnit Jupiter 为 5.12.2、AssertJ 为 3.27.7、Surefire 为 3.5.6；`tensor-plugin-api` 只有 JUnit/AssertJ/Mockito 测试依赖，没有 Spring、JDBC、HTTP 或其他内部模块编译依赖。
- **Rationale:** 稳定的 Java 21 与测试栈让首个 Plugin API 任务只新增 JDK records 和真实单元测试，不选择或修改构建依赖。
- **Constraint:** 保留父/子 POM、版本与依赖 scope；五个值对象只能依赖 JDK，实施提交不得混入 POM或其他模块变化。
- **Usage:** 在既有 `tensor-plugin-api` 源/测试目录按 `docs/task-designs/M02-T01-designs.md` 创建六个 Java 文件，复用现有 JUnit 5 与 AssertJ 执行 RED/GREEN 和模块回归。
- **Readiness evidence:** M01-T02 在权威看板中为 `COMPLETED`；提交范围为六个 POM，两项 XML 契约、effective POM、Java 21、旧入口/范围/格式检查均退出 0，最终 `mvn test` 为六项目 `SUCCESS`，任务级审查 `Approved`，最终整体审查 `Ready to merge: Yes`。

## Start Here

1. `docs/task-designs/M02-T01-designs.md` 全文。
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M02-T01 行与任务详情。
3. `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 Global Constraints、Task M02-T01 和 Module Gate。
4. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 5.1。
5. `docs/contracts/dataset-definition.schema.json` 的 `pluginId`、`apiName`、`tableName` 约束。
6. `data-plane/pom.xml` 与 `data-plane/tensor-plugin-api/pom.xml`，并确认当前 M01 Enforcer 门禁仍在构建中生效。
7. **First action:** 不创建任何生产 record，先按设计完整创建 `IdentifierTest.java`，运行设计给出的聚焦 Maven 命令，并记录它因五个生产类型缺失而在 `testCompile` 非 0 的 RED。

## Risks

- `RequestId` 本任务只冻结服务端无参 UUID 工厂；客户端头沿用/白名单行为明确留给后续 `RequestIdFilter`，不得在本任务增加字符串解析工厂。
- 公开 record canonical constructors 仍须执行设计中的全部不变量；实现不能只在静态工厂校验，否则直接构造会绕过约束。
- Maven Resources/Compiler 的平台编码提示来自现有 M01 POM基线；本任务不得以消除提示为由修改 POM，实际测试失败或新警告仍需按设计处理。
