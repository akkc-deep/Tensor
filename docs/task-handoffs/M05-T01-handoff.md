# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M04-T06`
- **Next task:** `M05-T01`
- **Design document:** `docs/task-designs/M05-T01-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **ID and title:** `M05-T01`，`PluginRegistry` 与 `AdapterRegistry`。
- **Goal:** 在 `tensor-core` 中建立构造期不可变的插件与适配器注册表，只读取 M02 SPI 的本地描述、当前 readiness 和数据集身份；按值对象查找有效扩展，同时隔离禁用、缺凭证、重复和局部损坏的扩展。
- **Scope:** 只创建 `PluginRegistry.java`、`AdapterRegistry.java` 和 `RegistryTest.java`；不修改 POM、M02 类型、迁移、YAML、配置、其他模块或既有源码，不增加 Spring/扫描/刷新/公开 diagnostics，不调用下载、适配、定义、网络、数据库或 M05-T02～T05 职责。
- **Acceptance criteria:** 两个 final 注册表只暴露设计冻结的构造器与三个查询方法；唯一且可下载的插件、唯一 DatasetKey 的适配器可查找，禁用/缺凭证/readiness 失败保留安全插件描述，重复 PluginId 的每个参与者安全标记且全部不可查找，重复 DatasetKey 全部不可查找，单个 descriptor/readiness/datasetKey 失败不隐藏有效兄弟；描述符确定排序、视图不可变且不泄露异常/凭证；严格 TDD 取得缺类 RED 后 10/10 GREEN，模块 `test`/`verify` 89/89、三层 Enforcer、静态/范围/格式/清理和精确三文件提交门禁通过。

## Dependencies

### `M02-T05`

- **Artifact:** `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`、`DatasetAdapter.java`，及其签名直接引用的 `PluginDescriptor`、`PluginReadiness`、`PluginId`、`DatasetKey`、`DatasetDefinition`、`DownloadEnvelope` 和 `AdaptedBatch` 公共契约；SPI 实现提交 `445b941`，契约测试修复提交 `dd495ee`。
- **Decision:** `DataSourcePlugin` 精确提供 `descriptor()`、当前 `readiness()` 和同步 `download(...)`；`DatasetAdapter` 精确提供 `datasetKey()`、`definition()` 和 `adapt(...)`。描述符/readiness/身份/数据集类型均为已校验且不含 Token、凭证值、路径或原始响应的公共类型。
- **Rationale:** Core 注册表必须只依赖稳定 SPI 和不可变值对象，使具体 Tushare/fixture 插件可以编译期接入而无需修改核心注册逻辑，并把网络、适配和数据库职责留在各自边界。
- **Constraint:** M05-T01 不得修改或复制 M02 类型，不得在注册阶段调用 `download()`、`definition()` 或 `adapt()`；当前 readiness 必须通过 `readiness()` 读取，安全描述和日志不得携带原始异常、Token、凭证、响应、请求头、SQL、路径或栈。
- **Usage:** `PluginRegistry` 以 `descriptor()` 取得身份/展示元数据、以 `readiness()` 形成构造期状态快照并用 `PluginId` 建立可下载查找；`AdapterRegistry` 只以 `datasetKey()` 建立适配器查找。两个注册表只捕获扩展边界的 `RuntimeException` 做局部隔离。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；最终聚焦测试 8/8、模块 `verify` 79/79、两层 Enforcer、`jdeps java.base`、安全/范围/格式/清理门禁和独立复审已记录通过。当前直接消费类型相对最终修复提交 `dd495ee` 无差异。

该唯一直接依赖内部决策一致：描述符、当前 readiness、值对象身份和适配边界职责互补；均要求无框架泄漏、无敏感状态、无网络/数据库副作用。与用户批准的重复项全部隔离、不可用插件从 `find` 排除、安全描述保留和确定排序语义无冲突。

## Start Here

1. 完整读取 `docs/task-designs/M05-T01-design.md`，以其中公共表面、readiness 快照、重复/损坏/null 规则、固定安全文本、10 项测试和三文件范围作为唯一实施合同。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M05-T01 行与详情。
3. 核对 `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 Global Constraints、Task M05-T01 与 Module Gate。
4. 读取 TRD 6.1/6.2、`docs/task-designs/M02-T05-design.md` 和上述直接消费的 plugin-api Java 类型。
5. 读取 `data-plane/tensor-core/pom.xml`，确认现有 Java 21、JUnit 5 和 AssertJ 已满足任务且不修改 POM。

首个实施动作：运行设计 Tests 节的基线命令 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test`，确认现有 79/79 后，先完整创建 `RegistryTest.java`、不创建两个生产类，并运行设计中的聚焦命令取得只因缺 `PluginRegistry`/`AdapterRegistry` 的 `testCompile` RED。

## Risks

- readiness 只在构造时形成不可变快照；首期无热加载，配置变化需重启生效。
- 用户批准重复 PluginId 的每个不可用参与者均保留在 `descriptors()`，因此该列表允许相同 ID；消费者不能把它重新收集为唯一 key map。
- `System.Logger` 只记录固定安全文本；若后续健康/API 需要结构化诊断，必须由对应任务新增设计，不能扩展本任务冻结的公共表面。
