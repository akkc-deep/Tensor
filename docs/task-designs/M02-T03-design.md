# M02-T03 数据集字段、业务键、筛选和展示定义——任务设计

任务编号：`M02-T03`
对应任务：[M02-T03](../superpowers/plans/tensor-modules/M02-plugin-api.md#task-m02-t03-数据集定义25h)
实施产物：`com.akkc.tensor.plugin.api.dataset` 下六个公开类型，以及 `DatasetDefinitionTest.java`

## Goal

在 Java 21 `tensor-plugin-api` 模块中交付不可变的数据集元数据公共契约，使后续 YAML 加载、通用适配、白名单 SQL、查询和 REST 映射共享同一组已校验的字段、业务键、筛选与展示定义。

Java 契约保持与 M00-T02 schema 的最小兼容：schema 的每个 `filters` 字符串映射为只含 `field` 的 `FilterDefinition`；`batchSize` 是任务卡要求的 Java 运行时配置，未显式提供时默认为 500，不修改已冻结且封闭的 M00-T02 schema。

## Scope

包含：

- 创建 `LogicalType`、`ColumnDefinition`、`BusinessKeyMode`、`BusinessKeyDefinition`、`FilterDefinition` 和 `DatasetDefinition`；
- 冻结公开 record components、枚举闭集、构造期局部约束和跨组件引用约束；
- 使用一个真实的 `DatasetDefinitionTest` 覆盖完整 `daily` 定义、不变量、不可变性和任务卡反例；
- 运行聚焦测试、`tensor-plugin-api` 模块回归和 Enforcer `verify` 门禁。

排除：

- 不修改 M00-T02 JSON Schema、示例 YAML、OpenAPI、POM 或既有 M02-T01/M02-T02 类型；
- 不实现 YAML 加载、Jackson 映射、参数关系校验、REST filter operator/control type 映射、数据库类型、Flyway、SQL、适配、SPI 或前端；
- 不在本任务加入额外生产文件、builder、共享基类、Spring/JDBC/HTTP 类型或具体插件依赖。

## Approach

在包 `com.akkc.tensor.plugin.api.dataset` 中创建以下公开类型：

```java
public enum LogicalType {
    STRING, TEXT, DATE, MONTH, LONG, DECIMAL, ENUM
}

public record ColumnDefinition(
    String name,
    String label,
    LogicalType logicalType,
    boolean nullable,
    int displayOrder,
    Integer length,
    Integer precision,
    Integer scale,
    List<String> allowedValues,
    boolean longText
) {}

public enum BusinessKeyMode {
    COMPOSITE, FINGERPRINT
}

public record BusinessKeyDefinition(
    BusinessKeyMode mode,
    List<String> fields
) {}

public record FilterDefinition(String field) {}

public record DatasetDefinition(
    DatasetKey datasetKey,
    String displayName,
    String category,
    QueryMode queryMode,
    List<ParameterDescriptor> parameters,
    TableName tableName,
    List<ColumnDefinition> columns,
    BusinessKeyDefinition businessKey,
    List<FilterDefinition> filters,
    String fixedColumn,
    int batchSize
) {}
```

`DatasetDefinition` 另提供组件顺序相同但省略末尾 `batchSize` 的 public constructor，该构造器委托 canonical constructor 并传入默认值 500。不得把 0、负数或 null 解释为默认值；显式 `batchSize` 只允许 1～500。

所有 records 在 public canonical/compact constructor 中执行校验。必填引用组件用 `Objects.requireNonNull` 拒绝 null；`length`、`precision`、`scale` 和 `fixedColumn` 按 schema 保持可 null。内容非法或状态不一致时抛 `IllegalArgumentException`。标识字段精确匹配 `^[a-z][a-z0-9_]{1,63}$`，不 trim、不改写大小写。所有列表先拒绝 null 与 null 元素，再用 `List.copyOf` 保存并保持输入顺序。

`ColumnDefinition` 执行下列局部约束：

- `name` 满足标识正则，`label` 非空白，`logicalType` 非 null，`displayOrder >= 0`；
- 非 null `length >= 1`，非 null `precision` 为 1～65，非 null `scale` 为 0～30；
- `STRING` 和 `ENUM` 必须提供 `length`；`DECIMAL` 必须同时提供 `precision` 与 `scale`；
- `allowedValues` 非 null、元素非 null 且无重复；空列表表示 YAML 省略该可选属性，并允许开放 `ENUM`；
- 不额外禁止 schema 已允许的可选字段组合；展示顺序唯一性、`scale <= precision` 和数据库类型一致性留给 M03/M04 的跨定义校验。

`BusinessKeyDefinition` 拒绝 null mode、null/空字段列表、null 元素、重复字段和非法字段名。`COMPOSITE` 与 `FINGERPRINT` 都必须至少包含一个身份字段，因此空 `FINGERPRINT` 在构造时失败。

`FilterDefinition.field` 必须满足标识正则。它逐项映射 M00-T02 `filters: string[]`；`operator` 和 `controlType` 不进入 plugin-api，本任务不根据字段名推断 REST 行为。

`DatasetDefinition` 执行下列组合约束：

- `datasetKey`、`queryMode`、`tableName`、`businessKey` 和三个直接列表非 null；`displayName` 非空白且不超过 128 字符，`category` 非空白且不超过 64 字符；
- `columns` 至少一项；参数名、列名和 filter field 在各自列表中唯一；
- `tableName` 必须等于 `TableName.from(datasetKey)`；
- 每个 business-key field 和 filter field 必须引用一个已声明列；非 null `fixedColumn` 必须满足标识正则并引用一个已声明列；null `fixedColumn` 保留 schema 的可选语义，默认列选择留给 REST 映射；
- `batchSize` 为 1～500；省略它的重载构造器固定使用 500；
- 保持列、参数、业务键字段和筛选字段的声明顺序，不排序，也不在本任务执行 M03 的全量启动语义校验。

M00-T02 到 Java 的映射固定如下：`pluginId/apiName -> DatasetKey`、`tableName -> TableName`、`queryMode -> QueryMode`、`parameters -> List<ParameterDescriptor>`、`columns -> List<ColumnDefinition>`、`businessKey -> BusinessKeyDefinition`、每个 `filters[]` 字符串 `-> FilterDefinition.field`、`fixedColumn -> nullable String`。`batchSize` 是经项目所有者批准的唯一 Java-only 组件。

## Files

- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/LogicalType.java`：冻结七个逻辑类型。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/ColumnDefinition.java`：表达单列的类型、可空性、顺序和展示元数据。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyMode.java`：冻结复合键与指纹键模式。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyDefinition.java`：表达有序且唯一的身份字段。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/FilterDefinition.java`：封装 schema 中单个筛选字段名。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinition.java`：聚合并校验完整数据集定义。
- Create `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinitionTest.java`：执行真实类型的契约与不变量测试。

实现提交只暂存上述七个 Java 文件，提交消息固定为 `feat(plugin-api): define dataset metadata model`。任务准备文档、生成的 `target`、POM、其他模块和既有 Java 文件不得混入该提交。

## Tests

先完整创建 `DatasetDefinitionTest.java`，不创建六个生产类型，然后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am \
  -Dtest=DatasetDefinitionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `testCompile` 因六个生产类型不存在而退出非 0；失败必须来自缺失交付物，而不是测试语法、依赖解析或环境错误，作为 RED。

测试必须覆盖：

- 通过 reflection 断言两个枚举的精确值与顺序、四个 records 的精确组件名称/类型，以及 `DatasetDefinition` 的默认批量构造器；
- 按 M00-T02 示例构造含 11 列、复合键、两个 filters、`ts_code` 固定列的完整 `tushare_pro/daily` 定义，并断言默认 `batchSize == 500`；
- 拒绝非法列名、空白 label、负 display order、非法长度/精度/scale、缺少 STRING/ENUM length、缺少 DECIMAL precision/scale 和重复 allowed values；
- 拒绝空或重复 business-key fields、空 `FINGERPRINT`、非法 filter field、重复参数名/列名/filter field；
- 拒绝表名不匹配、业务键/筛选/固定列引用缺失及 0、负数、501 的 batch size；接受显式 1 和 500；
- 证明参数、列、allowed values、业务键字段和筛选列表是保序不可变副本，访问器返回列表不可修改；
- 证明 null `fixedColumn` 合法且不会在 plugin-api 内产生 REST 默认值。

完成最小实现后重跑聚焦命令，预期 `DatasetDefinitionTest` 全部通过，0 failure、0 error、0 skipped，命令退出 0。随后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am test
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am verify
```

两条命令均预期退出 0；M02-T01 与 M02-T02 既有测试继续通过，`verify` 显示 `ban-git-capabilities` 对父项目和 `tensor-plugin-api` 通过。不得引入新的构建警告类别。

最后运行：

```bash
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app
git status --short --untracked-files=all -- data-plane/tensor-plugin-api
git diff --check
```

预期第一条退出 0；提交前 plugin-api 状态只列六个生产类型和一个测试且不列 `target`；格式检查退出 0。提交后以 `git show --stat --oneline HEAD` 确认固定消息和七文件范围。

## Acceptance

- 六个生产类型的公开枚举值、record components 和默认构造行为与本设计逐项一致，没有额外公共 DTO 或依赖。
- Java 局部形状与 M00-T02 schema 一致；已批准的两个差异仅为 field-only `FilterDefinition` 包装和 Java-only `batchSize` 默认值。
- 所有集合都是保序不可变副本；重复名、非法值、表名不匹配和业务键/筛选/固定列悬空引用均在构造期失败。
- 完整 `daily` 定义构造成功，11 列顺序、复合业务键、筛选、固定列和默认批量值正确。
- 聚焦测试经历可归因的 RED 后 GREEN；模块 `test`、`verify`、Enforcer、范围和格式门禁全部得到预期结果。
- 净实现和提交精确包含任务卡列出的七个 Java 文件，未修改 schema、POM、既有类型或其他模块。

## Risks

无未决设计选择。已接受的兼容边界是：M03 YAML 加载器需把每个 filter 字符串包装为 `FilterDefinition`，并在 YAML 不含 `batchSize` 时调用默认 500 的构造路径；M09 REST 映射负责补充 operator/control type 与默认固定列行为。这些均属于后续预定义任务，不在 M02-T03 实现。
