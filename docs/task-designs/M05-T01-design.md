# M05-T01 `PluginRegistry` 与 `AdapterRegistry`——任务设计

任务编号：`M05-T01`
对应任务：[M05-T01](../superpowers/plans/tensor-modules/M05-core-registry-adapter.md#task-m05-t01-插件与适配器注册表30h)
实施产物：`tensor-core` 中两个构造期不可变注册表和一个真实行为测试

## Goal

在 `tensor-core` 中建立不依赖具体插件的 `PluginRegistry` 与 `AdapterRegistry`：启动构造时只读取 M02 SPI 的本地描述信息、当前 readiness 和数据集身份，形成不可变查找视图；有效插件和适配器可按值对象定位，禁用、缺凭证、重复或局部损坏的扩展被隔离，不隐藏同批有效扩展，也不会在注册阶段触发下载、适配、网络或数据库行为。

## Scope

包含：

- 创建 `PluginRegistry`，通过构造器接收 `List<DataSourcePlugin>`，提供精确的 `find(PluginId)` 和 `descriptors()` 公共表面；
- 创建 `AdapterRegistry`，通过构造器接收 `List<DatasetAdapter>`，提供精确的 `find(DatasetKey)` 公共表面；
- 以插件的 `readiness()` 作为构造期当前状态，生成不含敏感信息的描述符快照；
- 实现用户批准的唯一语义：只有 ID 唯一且 `downloadAvailable=true` 的插件进入 `find`；重复 `PluginId` 的所有实例均从查找表排除，但各自以安全重复原因保留在描述符列表；重复 `DatasetKey` 的所有适配器均从查找表排除；
- 对单个扩展的 `descriptor()`、`readiness()` 或 `datasetKey()` 运行时失败做局部隔离，以固定安全日志记录，不传播原始异常消息；
- 创建 `RegistryTest`，以真实最小测试实现覆盖正常查找、不可用状态、重复、局部失败、排序和不可变性；
- 执行严格 TDD、模块回归、Enforcer、范围、格式、清理和精确提交门禁。

排除：

- 不修改任何 POM、M02 公共类型、M04 迁移、YAML、配置、其他模块或既有源码；
- 不增加 Spring annotation、Bean 配置、`ServiceLoader`、反射扫描、外部插件 JAR、热加载或刷新 API；
- 不实现 M05-T02～T05 的数据集目录/schema 校验、参数校验、类型转换、通用适配或指纹键；
- 不调用 `download()`、`adapt()` 或 `definition()`，不执行网络、鉴权、数据库、Flyway、持久化或查询；
- 不新增公开 diagnostics/error DTO、异常类型、状态枚举、注册结果类型或注册表重载；固定安全日志和不可用描述符即为本任务的错误记录边界；
- 不以原始异常消息、异常类型名、Token、凭证值、配置路径、请求头、响应体、SQL 或堆栈作为描述符原因或日志内容。

## Approach

### 公共表面与构造期快照

两个类均为 `public final`，位于 `com.akkc.tensor.core.registry`，不声明 Spring annotation、继承关系或额外 public/protected 成员。公共表面固定为：

```java
public final class PluginRegistry {
    public PluginRegistry(List<DataSourcePlugin> plugins);
    public Optional<DataSourcePlugin> find(PluginId pluginId);
    public List<PluginDescriptor> descriptors();
}

public final class AdapterRegistry {
    public AdapterRegistry(List<DatasetAdapter> adapters);
    public Optional<DatasetAdapter> find(DatasetKey datasetKey);
}
```

构造器拒绝 null 列表；null 元素按局部损坏扩展处理并以固定安全消息跳过，不能使同列表中的有效扩展失效。`find` 拒绝 null key。构造结束后，查找映射使用不可变副本，描述符使用不可变有序列表；之后修改原始输入列表不得改变注册表。没有延迟扫描、运行时写入或刷新。

注册阶段只允许调用：

- 每个非 null `DataSourcePlugin` 的 `descriptor()` 一次和 `readiness()` 至多一次；
- 每个非 null `DatasetAdapter` 的 `datasetKey()` 一次。

测试实现的 `download()`、`definition()` 和 `adapt()` 直接抛 `AssertionError`，从而证明构造与查找不会触碰这些职责。

### 插件注册、readiness 与排序

`PluginRegistry` 先逐项读取 `descriptor()`。若其抛出 `RuntimeException`，该实例没有可安全恢复的 `PluginId`，因此用 `System.Logger` 记录固定 WARNING 文本 `Skipping plugin with invalid descriptor` 后跳过；不拼接异常消息、类名或栈。

描述符成功后读取 `readiness()`，并把该 `PluginReadiness` 的四个字段覆盖到描述符的状态字段，保留 `pluginId`、展示名、说明、API 和数据集列表，形成构造期当前快照：

- readiness 正常且 `downloadAvailable=true`：候选插件可以进入查找表；
- readiness 正常但禁用或缺凭证等原因导致 `downloadAvailable=false`：保留当前安全原因的描述符，但不进入查找表；
- readiness 抛 `RuntimeException`：记录固定 WARNING 文本 `Plugin readiness unavailable`，以 `enabled=false`、`credentialConfigured=false`、`downloadAvailable=false`、`unavailableReason="plugin readiness unavailable"` 形成安全描述符，且不进入查找表。

随后按 `PluginId` 分组。某 ID 只有一个候选且当前下载可用时，保存该原始 `DataSourcePlugin`；同 ID 有两个或以上候选时，所有实例均从查找表排除，每个候选都复制为 `downloadAvailable=false`、`unavailableReason="duplicate plugin id"` 的安全描述符，其余非敏感元数据保持不变，并记录固定 WARNING 文本 `Duplicate plugin id disabled`。不采用 first-wins、last-wins 或抛出全局异常。

最终 `descriptors()` 按 `PluginDescriptor.pluginId().value()` 升序，再按 `displayName` 升序；比较键完全相同的条目保持输入相对顺序。该列表包含唯一插件的当前描述符，也包含重复 ID 的每个不可用参与者；不包含无法取得描述符的实例。返回列表不可修改。

`find(PluginId)` 只查询构造期不可变的可下载映射，因此禁用、缺凭证、readiness 失败、描述符失败和重复 ID 均返回 `Optional.empty()`；注册表本身不承担已入库数据查询，Token 缺失不会删除描述符或数据集定义。

### 适配器注册与重复隔离

`AdapterRegistry` 对每个非 null 适配器只调用一次 `datasetKey()`。若其抛 `RuntimeException`，记录固定 WARNING 文本 `Skipping adapter with invalid dataset key` 并跳过；不读取 `definition()` 来反推身份。按 `DatasetKey` 分组后：

- 只有一个实例的 key 进入不可变查找映射；
- 有两个或以上实例的 key 全部排除，并记录固定 WARNING 文本 `Duplicate dataset key disabled`；
- 不采用 first-wins、last-wins，不调用适配逻辑，也不新增公开诊断集合。

`find(DatasetKey)` 只查询该不可变映射；缺失、损坏或重复 key 返回 `Optional.empty()`。

### 失败边界与最小实现

- 只捕获扩展边界抛出的 `RuntimeException`；`Error` 代表 JVM/断言等不可恢复失败，必须传播；
- 固定日志不得包含原始异常、插件描述文本或任何敏感配置；安全值对象也不必写入日志即可满足隔离证据；
- `PluginDescriptor` 与 `PluginReadiness` 的构造不变量继续由 M02 类型执行，注册表不复制正则、字段或业务校验；
- 私有 helper、私有候选 record 和 `System.Logger` 仅在两个指定文件内按最小需要使用；不创建第四个生产文件或公共抽象。

## Files

创建：

- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`：构造当前 readiness 描述符、隔离损坏/重复插件、提供不可变可下载查找和确定性描述符列表；
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/AdapterRegistry.java`：隔离损坏/重复适配器并提供不可变 `DatasetKey` 查找；
- `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/registry/RegistryTest.java`：真实测试实现和注册表行为/TDD 门禁。

不修改或删除任何文件。实现提交消息固定为 `feat(core): add plugin and adapter registries`，提交精确包含上述三个新文件；设计、交接、看板、POM、其他模块、`target/` 或其他文件不得混入实现提交。

## Tests

### 基线与缺类 RED

实施前先运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期现有上游 `tensor-plugin-api` 79/79、`tensor-core` 0 项，合计 79/79，0 failure、0 error、0 skipped，父项目、plugin-api 和 core 三层 Enforcer 通过。

随后先完整创建 `RegistryTest.java`，但不创建两个生产类，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=RegistryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `PluginRegistry` 与 `AdapterRegistry` 不存在而在 `tensor-core:testCompile` 非 0，不能因依赖解析、测试语法、上游未匹配测试或既有失败形成伪 RED。

### 聚焦 GREEN

创建最小生产实现后重跑同一聚焦命令，预期 `RegistryTest` 恰有 10 项测试且 10/10 通过，0 failure、0 error、0 skipped：

1. 可下载且 ID 唯一的插件可查找，描述符状态使用当前 `readiness()` 快照；
2. 描述符按 `pluginId`、`displayName` 排序，列表与构造输入均不可变/隔离；
3. 禁用插件保留描述符但不能查找；
4. 缺凭证插件保留安全原因但不能查找；
5. 重复 `PluginId` 的所有插件均不可查找且每个描述符标记 `duplicate plugin id`；
6. 描述符失败和 readiness 失败与一个有效插件并存时均被隔离，有效插件仍可查找；
7. 唯一 `DatasetKey` 的适配器可查找，构造输入修改不影响结果；
8. 重复 `DatasetKey` 的所有适配器均不可查找；
9. `datasetKey()` 失败与一个有效适配器并存时被隔离，有效适配器仍可查找；
10. null 列表和 null lookup key 被拒绝，null 扩展元素被局部跳过，且注册阶段从未调用 download/definition/adapt。

测试只使用 JUnit 5、AssertJ、真实 `PluginDescriptor`/`PluginReadiness`/值对象和测试内最小 SPI 实现，不使用 Mockito，不断言日志文本或 mock 调用。描述符、readiness 和 dataset key 的预期均用字面值独立构造。

### 模块、范围与清理

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期合计 89/89（既有 plugin-api 79 项加新 Registry 10 项），0 failure、0 error、0 skipped，三层 Enforcer 通过，且不引入既有平台编码提示之外的新警告类别。

运行静态与范围门禁：

```bash
rg -n 'org\.springframework|java\.sql|javax\.sql|ServiceLoader|RestClient|JdbcTemplate' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/AdapterRegistry.java
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app data-plane/tensor-plugin-api
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/registry
git diff --check
```

扫描预期无输出且退出 1；`clean` 退出 0；POM/app/plugin-api 工作树无差异；提交前 scoped status 精确列出三个新文件且无 `target`；格式检查退出 0。按仓库规则把三个新文件加入 Git。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确三文件范围，工作树干净。

## Acceptance

- 两个 `public final` 注册表只暴露设计冻结的构造器和三个查询方法，不含 Spring、网络、数据库、扫描、刷新或额外诊断 API；
- 构造期视图不可变，原始列表后续修改不影响结果，注册阶段从未调用下载、适配、定义、网络或数据库职责；
- `readiness()` 是当前状态来源；只有唯一且可下载的插件进入 `find`，禁用/缺凭证/readiness 失败仍保留安全描述符；
- 重复 PluginId 的所有插件均不可查找且各自标记固定安全原因，描述符按 `pluginId`、`displayName` 确定排序；描述符失败只隔离自身且不泄露异常内容；
- 唯一 DatasetKey 的适配器可查找，重复、损坏 key 的适配器全部隔离；一个坏插件或适配器不隐藏有效兄弟；
- null 边界、只捕获 RuntimeException、固定安全日志和敏感信息边界符合设计；
- TDD 得到缺两个生产类的可归因 RED 后 10/10 GREEN；模块 `test`/`verify` 89/89、三层 Enforcer、静态扫描、范围、格式、清理和精确三文件提交门禁全部得到预期结果；
- 未修改 POM、M02 类型或其他模块，未提前实现 M05-T02～T05 或下载/持久化/查询职责。

## Risks

- readiness 在注册表构造时取一次不可变快照；首期没有热加载或自动刷新，配置变化需重启应用后生效，这与 TRD 的启动构造和首期排除范围一致。
- 用户批准重复插件的每个不可用参与者都保留在 `descriptors()`，因此列表允许出现相同 PluginId；消费者必须按列表展示不可用原因，不能把该列表重新收集为唯一 key map。后续若要折叠为单条诊断，需要新的公开契约设计。
- `System.Logger` 只提供安全、非结构化运行日志；当前任务卡未授权公开 diagnostics API。后续健康/API 若需要结构化注册诊断，必须在对应任务中单独设计，不能修改本任务的三个公开查询方法。
