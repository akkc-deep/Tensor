# M02-T02 参数、API、插件描述符和 readiness——任务设计

任务编号：`M02-T02`
对应任务：[M02-T02](../superpowers/plans/tensor-modules/M02-plugin-api.md#task-m02-t02-参数api-和插件描述符20h)
实施产物：`com.akkc.tensor.plugin.api.descriptor` 下六个公开类型，以及 `PluginDescriptorTest.java`

## 做什么

在 Java 21 `tensor-plugin-api` 模块中交付参数、API、插件元数据和运行态可用性的最小公共契约：`ParameterType`、`ParameterDescriptor`、`QueryMode`、`ApiDescriptor`、`PluginReadiness` 和 `PluginDescriptor`。这些类型使后续元数据加载、插件注册、参数校验、数据源 API 和下载页面共享同一组已校验、不可变且不含敏感信息的描述符。

`ParameterType` 与 M00-T02 的六个参数类型逐字一致；`QueryMode` 与 M00-T02/OpenAPI 的四个小写值逐字一致。`PluginReadiness` 和 `PluginDescriptor` 只暴露启用、凭证是否已配置、下载是否可用及不可用原因，不保存 Token、凭证值、配置路径或认证头。`PluginDescriptor.datasets` 固定为 `List<DatasetKey>`，从而只消费已完成 M02-T01 的公开类型，并保持 M02-T02 可独立编译；完整 `DatasetDefinition` 留给 M02-T03。

本任务只创建任务卡列出的六个生产类型和一个测试文件；不创建 `DatasetDefinition`、下载包络、SPI、异常、参数校验器、Spring Bean、REST DTO 或 YAML 加载器；不修改 POM、M01 门禁、M02-T01 值对象、其他模块、资源、配置或前端。

## 怎么做

在包 `com.akkc.tensor.plugin.api.descriptor` 中创建以下公开类型，不增加别名工厂、builder、共享基类或序列化依赖：

```java
public enum ParameterType {
    DATE, DATE_RANGE_MEMBER, MONTH, TS_CODE, ENUM, TEXT
}

public enum QueryMode {
    trade_date, ann_date, snapshot, date_range
}

public record ParameterDescriptor(
    String name,
    String label,
    String description,
    ParameterType type,
    boolean required,
    String defaultValue,
    List<String> allowedValues,
    String pattern,
    String relatedParameter
) {}

public record ApiDescriptor(
    ApiName apiName,
    String displayName,
    String category,
    QueryMode queryMode,
    List<ParameterDescriptor> parameters
) {}

public record PluginReadiness(
    boolean enabled,
    boolean credentialConfigured,
    boolean downloadAvailable,
    String unavailableReason
) {}

public record PluginDescriptor(
    PluginId pluginId,
    String displayName,
    String description,
    boolean enabled,
    boolean credentialConfigured,
    boolean downloadAvailable,
    String unavailableReason,
    List<ApiDescriptor> apis,
    List<DatasetKey> datasets
) {}
```

所有 records 在 public canonical/compact constructor 中执行不变量，使直接构造无法绕过校验。null 组件用 `Objects.requireNonNull` 拒绝；内容非法或组件状态不一致时抛 `IllegalArgumentException`。不 trim、不改写大小写，也不以空白字符串替代 null。

### 参数描述符

`ParameterDescriptor.name` 和非 null 的 `relatedParameter` 精确匹配 `^[a-z][a-z0-9_]{1,63}$`。`label` 必须非空白；非 null 的 `description` 必须非空白。`type` 和 `allowedValues` 必须非 null；`allowedValues` 用 `List.copyOf` 保存，允许空数组，但元素不得为 null 或重复。`defaultValue` 和 `pattern` 按 M00-T02 保持可 null 字符串，不在本任务解析默认值或编译正则。

`type == ENUM` 时 `allowedValues` 必须非空；`type == DATE_RANGE_MEMBER` 时 `relatedParameter` 必须存在、满足标识符正则且不得与 `name` 相同。其他类型不额外禁止 M00-T02 schema 已允许的可选字段；关联参数是否存在及日期范围先后顺序留给 M03/M05 的跨描述符校验。

### API 描述符

`ApiDescriptor` 拒绝 null `ApiName`、`QueryMode` 和参数列表；`displayName`、`category` 必须非空白，`category` 最长 64 字符。`parameters` 用 `List.copyOf` 保存并保持声明顺序，允许空列表，拒绝 null 元素和重复 `ParameterDescriptor.name`。

`QueryMode` 直接使用四个小写 enum constants，使 `name()` 与 YAML/OpenAPI 的稳定值一致，不增加转换器或字符串别名。`ParameterType` 使用六个大写 enum constants，使 `name()` 与 M00-T02 参数类型逐字一致。

### readiness 与插件描述符

`PluginReadiness` 执行以下真值约束：

- `downloadAvailable == true` 时 `enabled` 与 `credentialConfigured` 都必须为 true，且 `unavailableReason` 必须为 null；
- `downloadAvailable == false` 时 `unavailableReason` 必须为非空白字符串；
- 禁用或未配置凭证时不得把 `downloadAvailable` 设为 true；两者均为 true 时仍允许因其他运行态原因不可用，但必须说明原因。

`PluginDescriptor` 对四个 readiness 组件执行完全相同的约束；实现通过构造临时 `PluginReadiness` 复用校验即可，不保存第三份状态，也不新增 `readiness()` 方法。`pluginId`、`apis` 和 `datasets` 非 null，展示名与说明非空白；两个列表均用 `List.copyOf` 保存并保持声明顺序。

`apis` 按 `ApiDescriptor.apiName` 唯一；`datasets` 按 `DatasetKey` 唯一。每个 `DatasetKey.pluginId` 必须等于描述符的 `pluginId`，且其 `apiName` 必须出现在 `apis` 中；因此数据集可以是 API 的有序子集，但不能引用其他插件或未声明 API。列表允许为空。公开 record components 恰为上面的字段，不加入 Token、credential value、配置路径、URL、Header 或任意敏感对象。

创建 `PluginDescriptorTest.java`，只使用 JUnit 5、AssertJ 和真实 records，不使用 mocks。测试以字面量和显式组件列表形成预期结果，并覆盖：

- 两个 enums 的值与顺序精确匹配冻结闭集；
- 合法普通参数、无参数 API 和 `tushare_pro/daily` 插件描述符可构造；
- 参数名、关联名、空白展示文本、null 列表/元素、重复 allowed value、空 ENUM 取值、自关联日期范围被拒绝；
- 参数列表、API 列表、数据集列表和 allowed values 对原列表修改免疫，访问器返回的列表不可修改；
- API 内重复参数名、插件内重复 API 名、重复数据集键、跨插件数据集键和未声明 API 的数据集键被拒绝；
- enabled/configured/available 的合法和非法组合、available/unavailable reason 的 null/非空白互斥规则；
- 通过 reflection 断言 `PluginReadiness` 恰含四个状态 components、`PluginDescriptor` 恰含任务卡规定的九个 components，且 `datasets` 的泛型参数是 `DatasetKey`，没有凭证值或路径字段。

只暂存六个生产类型和 `PluginDescriptorTest.java`，提交消息固定为 `feat(plugin-api): define plugin descriptors`；不得混入任务准备文档、POM、其他模块或生成的 `target` 内容。

## 如何测试

实施前先完整创建 `PluginDescriptorTest.java`，不创建六个生产类型，然后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am \
  -Dtest=PluginDescriptorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须因 `ParameterType`、`ParameterDescriptor`、`QueryMode`、`ApiDescriptor`、`PluginReadiness` 和 `PluginDescriptor` 不存在而在 `testCompile` 退出非 0；失败必须来自缺失交付物，而不是测试语法、依赖解析或环境错误，作为 RED。

完成最小实现后重跑同一命令，预期 `PluginDescriptorTest` 全部通过，0 failure、0 error、0 skipped，命令退出 0。随后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am test
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am verify
```

两条命令均预期退出 0；M02-T01 的 26 项 `IdentifierTest` 继续通过，`verify` 的 `ban-git-capabilities` 对父项目和 `tensor-plugin-api` 均通过。M01 已存在的平台编码提示不属于本任务修复范围；不得引入新的警告类别、测试失败或异常输出。

最后运行：

```bash
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app
git status --short --untracked-files=all -- data-plane/tensor-plugin-api
git diff --check
```

预期第一条退出 0；提交前任务范围只列六个生产类型和一个测试，不列 `target`；格式检查退出 0。提交后用 `git show --stat --oneline HEAD` 确认固定消息和七文件范围。

## 如何验证

- 枚举闭集：`ParameterType` 恰为六个 M00-T02 大写值，`QueryMode` 恰为四个 M00-T02/OpenAPI 小写值，没有别名或额外状态；
- 参数契约：record components、局部形状和条件字段与 M00-T02 schema/TRD 5.3 一致，所有集合为有序不可变副本，重复名和不一致状态在构造期失败；
- 插件契约：`ApiDescriptor`、`PluginReadiness` 与 `PluginDescriptor` 的公开 components 精确匹配本设计，`datasets` 固定为 `List<DatasetKey>`，插件/API/数据集引用关系一致；
- 可用性与安全：禁用或未配置凭证时下载不可用；可用/不可用原因互斥；任何公开 component 都不保存 Token、凭证值、路径、认证头或其他敏感信息；
- TDD 与回归：目标测试先因六类缺失 RED，后 GREEN；模块 `test`/`verify` 退出 0，M02-T01 标识测试与 Enforcer 门禁继续通过；
- 范围与 Git：净实现仅七个指定 Java 文件，POM、既有值对象、app 与其他模块无差异；提交消息和文件范围精确匹配任务卡。

## 依赖什么信息

| 依赖或来源 | 用途 | 稳定约束或前置条件 |
|---|---|---|
| `docs/task-handoffs/tensor-v1-task-board.md` 的 M02-T02 行与详情 | 确定任务 ID、目标、范围、直接依赖、状态和设计回填位置 | 权威看板是任务身份、顺序和状态的唯一来源；M02-T02 只直接依赖 M02-T01 |
| M02-T01 提交 `4078dad6f2becb2cbcd4239c5aa5bace21fed5a5` | 提供已校验 `PluginId`、`ApiName` 与 `DatasetKey` | 提交精确六文件，聚焦/模块测试与 verify 均通过；本任务不修改这些值对象 |
| `docs/superpowers/plans/tensor-modules/M02-plugin-api.md` 的 Global Constraints、Task M02-T02 与 Module Gate | 冻结六个生产文件、测试文件、PluginDescriptor 组件、集合/重复名/readiness 门禁、命令和提交消息 | Plugin API 不依赖 Spring、数据库、HTTP、具体插件或 Vue；不提前创建 M02-T03～T05 类型 |
| `docs/contracts/dataset-definition.schema.json` 与 `docs/task-designs/M00-T02-designs.md` | 冻结参数字段、标识符正则、六个参数类型、四个查询模式和条件字段 | Java 描述符保持同一局部形状；跨参数引用与顺序留给 M03/M05 |
| `docs/contracts/openapi-v1.yaml` 的 `DataSourceSummary` 与 `ApiDescriptor` | 对照数据源状态字段、可用/不可用原因互斥、API 展示字段和公开枚举 | Plugin API 不直接依赖 OpenAPI/Jackson；M09 负责映射 REST DTO |
| TRD 5.2、5.3、6.1、6.2 | 冻结插件/API/参数元数据、readiness 非敏感视图及禁用/缺少 Token 行为 | Token 和配置路径不得进入公共 DTO；禁用或未配置凭证时不得下载 |
| `data-plane/pom.xml` 与 `data-plane/tensor-plugin-api/pom.xml` | 提供 Java 21、JUnit 5、AssertJ、Surefire 与 Enforcer 基线 | 不修改 POM；生产类型只依赖 JDK 与 M02-T01 同模块类型 |
| 已批准的 M02-T02 `datasets` 类型裁决 | 冻结 `PluginDescriptor.datasets` 为 `List<DatasetKey>` | 避免提前依赖 M02-T03 的 `DatasetDefinition`，保持本任务独立编译并允许后续完整定义独立注册 |
