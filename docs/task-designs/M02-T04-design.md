# M02-T04 `DownloadEnvelope`、`AdaptedBatch` 和执行结果——任务设计

任务编号：`M02-T04`
对应任务：[M02-T04](../superpowers/plans/tensor-modules/M02-plugin-api.md#task-m02-t04-下载包络适配批次和结果20h)
实施产物：`com.akkc.tensor.plugin.api.download` 下五个公开类型，以及两个任务卡指定的测试文件

## Goal

在 Java 21 `tensor-plugin-api` 模块中交付不可变的下载包络、适配批次和下载结果公共契约，使插件、适配器、核心编排、持久化和 REST 映射共享同一组已校验的数据形状，并明确区分上游成功、上游失败、合法空结果和有数据结果。

## Scope

包含：

- 创建 `DownloadStatus`、`DownloadEnvelope`、`AdaptedBatch`、`DownloadOutcome` 和 `DownloadResult`；
- 冻结公开 record components、枚举闭集、集合复制、嵌套行复制和状态相关不变量；
- 使用 `DownloadEnvelopeTest.java` 与 `AdaptedBatchTest.java` 覆盖完整成功、合法空结果、失败一致性、批次结构、结果计数和不可变性；
- 执行严格 TDD、`tensor-plugin-api` 模块回归和 Enforcer `verify` 门禁。

排除：

- 不修改 POM、M00 契约、M02-T01/M02-T03 类型或其他模块；
- 不创建 `DataSourcePlugin`、`DatasetAdapter`、`ErrorCode`、异常类、`WriteCounts`、下载服务、数据库、HTTP/REST DTO、具体插件或前端类型；
- 不执行参数语义校验、来源字段与某个 `DatasetDefinition` 的完整对照、类型转换、重复业务键处理、持久化或结果消息本地化；
- 不把 M02-T05 的领域错误反向引入本任务，避免形成任务依赖环。

## Approach

在包 `com.akkc.tensor.plugin.api.download` 中创建以下公开类型，record components 的名称、类型和顺序固定：

```java
public enum DownloadStatus {
    SUCCESS, FAILURE
}

public record DownloadEnvelope(
    PluginId pluginId,
    ApiName apiName,
    Map<String, Object> params,
    List<String> fields,
    int rowCount,
    List<List<Object>> data,
    DownloadStatus status,
    String error
) {}

public record AdaptedBatch(
    DatasetKey datasetKey,
    TableName tableName,
    List<String> columns,
    List<Map<String, Object>> rows,
    BusinessKeyDefinition businessKeyDefinition,
    Instant ingestedAt
) {}

public enum DownloadOutcome {
    SUCCESS, EMPTY
}

public record DownloadResult(
    RequestId requestId,
    DownloadOutcome outcome,
    PluginId pluginId,
    ApiName apiName,
    long sourceRowCount,
    long insertedRows,
    long updatedRows,
    String message
) {}
```

用户已批准 `DownloadStatus` 精确使用 `SUCCESS|FAILURE`，`DownloadEnvelope.error` 使用 nullable `String`，包络 `rowCount` 使用 `int`，`DownloadResult` 的三个计数使用与 OpenAPI `int64` 对齐的 `long`。`error` 只保存已分类且可安全展示的摘要，不保存 Token、原始上游响应、请求头、堆栈或内部路径。

所有 records 在 public compact constructor 中校验。必填引用组件用 `Objects.requireNonNull` 拒绝 null；状态或内容不一致时抛 `IllegalArgumentException`。新的字段名和参数名使用统一正则 `^[a-z][a-z0-9_]{1,63}$`，不 trim、不改写大小写。

`DownloadEnvelope` 执行以下约束：

- `pluginId`、`apiName`、`params`、`fields`、`data` 和 `status` 非 null；`params` 用 `Map.copyOf` 保存，拒绝 null 键、null 值和非法参数名；
- `fields` 用 `List.copyOf` 保存，拒绝 null、重复和非法字段名；
- `data` 的外层和每个非 null 行都复制为不可修改列表；行内 null 单元格合法，以表达来源空值，不得用会拒绝 null 单元格的 `List.copyOf(row)`；
- 对所有状态要求 `rowCount >= 0` 且 `rowCount == data.size()`；
- `SUCCESS` 要求 `error == null`、`fields` 至少一项，且每行元素数严格等于 `fields.size()`；`rowCount=0` 与空 `data` 是合法成功，仍保留非空字段定义；
- `FAILURE` 要求 `error` 非 null 且非空白，并要求 `fields`、`data` 均为空且 `rowCount=0`，拒绝携带部分成功载荷的半包络；
- 构造器只验证包络自身形状；字段是否与目标 `DatasetDefinition` 完全一致属于后续适配器/目录的跨对象校验。

`AdaptedBatch` 执行以下约束：

- 六个 components 均非 null，`tableName` 必须等于 `TableName.from(datasetKey)`；
- `columns` 至少一项、满足统一标识正则、无 null、无重复并以 `List.copyOf` 保序保存；
- `rows` 外层复制为不可修改列表，每个非 null 行复制为保持迭代顺序的不可修改 map；行 key 非 null，行内 null value 合法，以表达可空目标列；
- 每行 key 集合必须与 `columns` 精确相等，既不缺列也不含额外列；不要求调用者的 map 迭代顺序承担列顺序，批次列顺序只由 `columns` 表达；
- `businessKeyDefinition.fields()` 必须全部引用 `columns`；允许空 `rows`，空结果是否创建批次或直接短路由后续核心编排决定；
- 单个非 null `ingestedAt` 是整批唯一时间值；本类型不逐行生成时间，也不执行数据库操作。

`DownloadResult` 执行以下约束：

- `requestId`、`outcome`、`pluginId`、`apiName` 和 `message` 非 null，`message` 非空白；
- 三个 `long` 计数均非负；
- `EMPTY` 要求 `sourceRowCount == insertedRows == updatedRows == 0`；
- `SUCCESS` 要求 `sourceRowCount >= 1`；不在本任务强制 `insertedRows + updatedRows == sourceRowCount`，因为后续适配可对完全重复来源行去重，准确计数关系由 M05/M06 的适配与持久化契约验证；
- 失败不构造 `DownloadResult`；后续 M02-T05/M09 通过领域错误和统一错误包络表达失败。

直接依赖固定为：M02-T01 的 `PluginId`、`ApiName`、`DatasetKey`、`TableName`、`RequestId`，以及 M02-T03 的 `BusinessKeyDefinition`。不得复制这些值对象或接受等价裸字符串替代。

## Files

- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadStatus.java`：冻结上游包络成功/失败状态。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadEnvelope.java`：表达并校验来源参数、字段、嵌套行、计数和错误一致性。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/AdaptedBatch.java`：表达并校验目标表、列、行、业务键和批次时间。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadOutcome.java`：冻结 REST 成功结果的 `SUCCESS|EMPTY` 闭集。
- Create `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/DownloadResult.java`：表达下载编排的成功/空结果和实际计数。
- Create `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/download/DownloadEnvelopeTest.java`：覆盖包络、枚举、结果和不可变性契约。
- Create `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/download/AdaptedBatchTest.java`：覆盖批次引用、行形状、空值、时间和不可变性契约。

实现提交只暂存上述七个 Java 文件，提交消息固定为 `feat(plugin-api): add download and adaptation contracts`。任务准备文档、生成的 `target`、POM、既有 Java 文件和其他模块不得混入该提交。

## Tests

先完整创建两个测试文件，不创建五个生产类型，然后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am \
  -Dtest=DownloadEnvelopeTest,AdaptedBatchTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期在 `testCompile` 因五个生产类型不存在而退出非 0；失败必须来自缺失交付物，而不是测试语法、依赖解析或环境错误，作为 RED。

测试必须覆盖：

- 通过 reflection 断言两个枚举的精确值与顺序、三个 records 的精确组件名称/类型；
- 构造完整 `tushare_pro/daily` 成功包络，断言参数、11 个来源字段、数据行、实际 `rowCount` 和 `error=null`；
- 构造 `rowCount=0`、`data=[]`、非空 fields 的合法成功包络；
- 拒绝 row count 不匹配、重复/非法 fields、null 行、行宽错误、成功携带 error、失败缺少/空白 error，以及失败携带 fields/data/非零 row count 的半包络；
- 证明 params、fields、data 外层和每个嵌套行都是不可修改副本，同时允许行内 null 单元格；
- 构造与 `DatasetKey` 一致的 `AdaptedBatch`，断言列顺序、行 key、业务键和唯一 `Instant`；
- 拒绝表名不匹配、空/重复/非法 columns、行缺列/多列、业务键悬空引用、null 行或 null key；允许空 rows 与行内 null value；
- 证明 columns、rows 外层和每个行 map 是不可修改副本，源集合后续变更不影响 record；
- 构造 `SUCCESS`/`EMPTY` `DownloadResult`，拒绝负计数、`EMPTY` 非零计数、`SUCCESS` 零来源数及空白 message，并验证不强制来源数等于插入数加更新数。

完成最小实现后重跑聚焦命令，预期两个测试类全部通过，0 failure、0 error、0 skipped，命令退出 0。随后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am test
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am verify
```

两条命令均预期退出 0；M02-T01～T03 的 54 项既有测试继续通过，新测试全部通过，`verify` 显示 `ban-git-capabilities` 对父项目和 `tensor-plugin-api` 通过，不引入新的构建警告类别。

最后运行：

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app
git status --short --untracked-files=all -- data-plane/tensor-plugin-api
git diff --check
```

预期 `clean` 成功；POM/app diff check 退出 0；提交前 plugin-api 状态只列五个生产类型和两个测试且不列 `target`；格式检查退出 0。提交后用 `git show --stat --oneline HEAD` 确认固定消息和精确七文件范围。

## Acceptance

- 两个枚举和三个 records 的公开值、components、类型和顺序与本设计逐项一致，没有额外公共 DTO 或依赖。
- 成功、合法空结果、失败和半包络规则在构造期可观察；失败不会伪装为空数据，失败包络不携带部分成功载荷。
- 包络与批次的外层及嵌套集合都是不可修改副本，同时保留业务所需的 null 单元格/值；字段、列、行宽、行 key、表名和业务键引用不一致均被拒绝。
- `DownloadResult` 与 OpenAPI 的 `SUCCESS|EMPTY`、`int64` 非负计数和空结果全零约束一致，不提前承担失败 DTO 或后续去重/持久化计数职责。
- 聚焦测试经历可归因的 RED 后 GREEN；模块 `test`、`verify`、Enforcer、范围、格式和精确七文件提交门禁全部得到预期结果。
- 未修改 POM、既有类型、M00 契约或其他模块，未提前实现 SPI、领域异常、核心编排、持久化、HTTP 或前端职责。

## Risks

`Map<String,Object>` 和嵌套单元格只复制容器，不可能通用地深复制任意可变 `Object`；当前跨模块稳定契约只承载参数标量和来源/目标单元值，后续实现不得向这些容器放入需要深复制的可变业务对象。`DownloadEnvelope.error` 是经项目所有者批准的安全字符串边界；M02-T05 的领域错误必须从该摘要或插件异常映射，不能把 Token、原始响应或内部诊断写回此字段。
