# M05-T05 `GenericDatasetAdapter`、重复键和指纹键——任务设计

任务编号：`M05-T05`
对应任务：[M05-T05](../superpowers/plans/tensor-modules/M05-core-registry-adapter.md#task-m05-t05-通用适配器与指纹键35h)
实施产物：`GenericDatasetAdapter`、`FingerprintKeyCodec` 和 `GenericDatasetAdapterTest`

## Goal

在 `tensor-core` 中交付唯一的元数据驱动批次适配边界：把已成功下载且身份、字段均与已验证 `DatasetDefinition` 一致的 `DownloadEnvelope`，逐行转换为不可变 `AdaptedBatch`；在转换后校验不可空列和业务键，按业务键稳定去重，并为 FINGERPRINT 数据集生成批准格式的 SHA-256 `business_key`。任一行的字段、类型或键失败都使整个适配调用失败，不产生可供持久化的部分批次。

## Scope

包含：

- 创建 `GenericDatasetAdapter` 并实现 M02-T05 的 `DatasetAdapter` 精确三方法合同；
- 由构造器接收一份已通过 M05-T02 目录准入的不可变 `DatasetDefinition`、已完成 M05-T04 的 `ValueConverter` 和本任务的 `FingerprintKeyCodec`；
- 对成功包络执行身份、来源字段全集和顺序校验，每批只构建一次字段索引，再按定义列序转换全部行；
- 在转换后校验所有 `nullable == false` 列以及所有业务键字段非 null，并使用安全 `ADAPTER_FIELD_MISSING` 摘要定位失败；
- 为 COMPOSITE 数据集使用有序转换值列表作为批次内键，为 FINGERPRINT 数据集按批准的规范序列化生成小写 SHA-256，并把 `business_key` 追加到适配行；
- 对完全相同的重复适配行保留首次出现项并记录固定安全警告；对相同键但内容不同的行以 `ADAPTER_TYPE_INVALID` 拒绝整个批次；
- 返回业务列原序加可选 `business_key` 的 `AdaptedBatch`，保留调用方传入的唯一 `ingestedAt`，包括零行成功包络对应的空行批次；
- 以严格 TDD 覆盖公开表面、映射、错误、去重和指纹字节合同，并执行模块回归、Enforcer、静态、范围、格式和清理门禁。

排除：

- 不修改 POM、plugin-api、M03 YAML、M04 Flyway、M05-T01～T04 既有 Java 或其他模块；
- 不创建 Spring Bean，不从 `DatasetCatalog` 自行查找定义，不读取 YAML/SQL，也不依赖具体 Tushare/fixture 插件；
- 不重复 M05-T03 的参数校验，不读取或改写 `DownloadEnvelope.params()`，不调用插件或网络；
- 不实现 M06 的 SQL、JDBC 值绑定、已有键预查、Upsert、事务、插入/更新计数或锁；
- 不把 `source_plugin`、`source_api`、`ingested_at` 放入适配行；M06 只能从 `AdaptedBatch.datasetKey()` 与 `AdaptedBatch.ingestedAt()` 绑定这三个技术列；
- 不生成当前时间，不记录原始值、业务键、完整行、Token、参数、上游响应或异常 cause；
- 不创建额外生产类型、公开 diagnostics、重复键结果 DTO、策略接口、builder、工厂或重载。

## Approach

### 公开表面与构造边界

在 `com.akkc.tensor.core.adapter` 中冻结以下公开合同，不增加其他 public/protected 构造器、字段或方法：

```java
public final class GenericDatasetAdapter implements DatasetAdapter {
    public GenericDatasetAdapter(
            DatasetDefinition definition,
            ValueConverter valueConverter,
            FingerprintKeyCodec fingerprintKeyCodec);

    @Override public DatasetKey datasetKey();
    @Override public DatasetDefinition definition();
    @Override public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt);
}

public final class FingerprintKeyCodec {
    public FingerprintKeyCodec();
    public String sha256(List<String> fields, Map<String, Object> row);
}
```

`GenericDatasetAdapter` 的唯一 public constructor 用 `Objects.requireNonNull` 拒绝三个 null 依赖，保存不可变定义及两个无状态协作者，不复制或重建公共 DTO。`datasetKey()` 返回 `definition.datasetKey()`，`definition()` 返回同一不可变定义。类不暴露字段索引、重复键集合、logger、转换 helper 或指纹内部状态。

构造器的 `definition` 必须来自 M05-T02 已验证目录；本类不增加无法证明来源的布尔标志，也不重复 JDBC/schema 准入。后续装配方从 `DatasetCatalog.find/list` 取得定义后创建适配器。

`FingerprintKeyCodec` 使用 public 无参构造器且不保存状态。`sha256` 是其唯一声明的 public 方法；没有可变 digest 字段，避免跨调用共享 `MessageDigest`。

### 包络准入与字段索引

`adapt` 用 `Objects.requireNonNull` 拒绝 null `envelope`/`ingestedAt`。`DownloadStatus.FAILURE` 表示调用方越过了来源失败处理边界，固定抛 `IllegalArgumentException("envelope must be successful")`；Generic adapter 不把来源失败重新分类为适配错误。

成功包络必须满足：

1. `envelope.pluginId()` 与 `definition.datasetKey().pluginId()` 相等，且 `envelope.apiName()` 与 `definition.datasetKey().apiName()` 相等；否则抛 `AdapterException(ADAPTER_FIELD_MISSING, "Adapter envelope mismatch: api=<expectedApi>")`；
2. `envelope.fields()` 必须与 `definition.columns().map(ColumnDefinition::name)` 在数量、成员和顺序上完全相等；任何缺失、额外或重排均抛 `AdapterException(ADAPTER_FIELD_MISSING, "Adapter fields do not match: api=<expectedApi>")`；
3. `DownloadEnvelope` 构造器已经保证 `rowCount == data.size()`、行宽和嵌套容器不变量，本类直接消费这些稳定合同，不维护第二套包络形状校验；
4. 精确相等后，用一个 `LinkedHashMap<String,Integer>` 在每批开始时把来源字段映射到索引；逐行、逐列只读取该映射，不为每行重建或线性搜索字段位置。

身份和字段失败的消息只含定义中已验证的预期 API 名，不回显实际来源身份、字段列表、参数或任意单元值。

### 行转换、必填校验与批次列

目标业务列顺序精确等于 `definition.columns()` 的声明顺序。每个来源行以从 0 开始的 `rowIndex` 创建 `ConversionContext(envelope.apiName(), rowIndex)`，并对每个 `ColumnDefinition` 调用一次 `ValueConverter.convert(source, column, context)`。转换结果按目标列顺序写入新的 `LinkedHashMap<String,Object>`；输入包络、嵌套行和来源对象不被修改。

每个值转换后立即执行缺失校验。若结果为 null 且满足以下任一条件，则抛 `AdapterException(ErrorCode.ADAPTER_FIELD_MISSING, message)`：

- `column.nullable() == false`；
- `definition.businessKey().fields()` 包含该列，即使该列元数据本身允许 null。

message 精确为：

```text
Missing adapter value: api=<ApiName.value>, row=<rowIndex>, field=<ColumnDefinition.name>
```

M05-T04 的 `ADAPTER_TYPE_INVALID` 原样传播，不捕获、包装或附加 cause。合法 nullable 非键列保留 null；TEXT 的空字符串和纯空白仍按 M05-T04 原样保留，不被缺失校验改写。

COMPOSITE 数据集的 `AdaptedBatch.columns()` 和每行 key 精确为业务列原序。FINGERPRINT 数据集在全部业务列之后追加内部列 `business_key`，其值为 `FingerprintKeyCodec.sha256(definition.businessKey().fields(), convertedRow)`；原 `BusinessKeyDefinition` 仍作为 `AdaptedBatch.businessKeyDefinition()`，身份字段继续引用业务列。`source_plugin`、`source_api` 和 `ingested_at` 不进入 columns/row map；批次通过 `datasetKey` 和唯一 `ingestedAt` 分别携带这些后继绑定输入。

零行成功包络仍返回合法 `AdaptedBatch`：columns 与有数据批次相同、rows 为空、definition 的表名/业务键和调用方 `ingestedAt` 原样保留。M09/M06 后继编排据 `rows().isEmpty()` 短路数据库写入；本任务不返回 null 或创建第二种空结果类型。

### 指纹规范序列化

`FingerprintKeyCodec.sha256(fields, row)` 拒绝 null 参数；`fields` 必须非空、无 null、无重复，且 `row` 必须包含每个字段。调用合同被破坏时抛不含行值的 `IllegalArgumentException`；row 的额外键被忽略，fields/row 不被修改。

每个字段按 `fields` 原序编码。值只允许 M05-T04 的转换结果：`String`、`LocalDate`、`Long`、`BigDecimal` 或 null。规范文本固定为：

- `String`：转换后字符串原样；
- `LocalDate`：ISO `uuuu-MM-dd`；
- `Long`：无前导零的十进制文本；
- `BigDecimal`：`toPlainString()`，保留 M05-T04 已固化的目标 scale，不用科学计数法；
- null：没有文本字节。

批准的二进制格式为：

- null：单字节 `0x00`；
- 非 null：单字节 `0x01`，随后是规范文本 UTF-8 字节长度的 4 字节大端无符号整数，再随后是该 UTF-8 字节序列；
- 所有字段片段直接拼接，不加入字段名、分隔符、平台换行或默认字符集字节；
- 对完整字节流计算 JDK `SHA-256`，用小写十六进制编码为精确 64 字符。

不得对任意对象调用 `toString()`、`String.valueOf` 或经过 `double`。未知运行时类型抛固定、无原始值的 `IllegalArgumentException("Unsupported fingerprint value type")`。JDK 缺少 SHA-256 时抛无 cause 的 `IllegalStateException("SHA-256 unavailable")`。

固定已知向量使用字段 `[text, missing, count, amount]` 与值 `中`、null、`42L`、`new BigDecimal("1.20")`：规范字节十六进制为 `0100000003e4b8ad00010000000234320100000004312e3230`，SHA-256 必须为 `c593b786a7708a9b7a106e244094f1cabd200caa3e95fad3b041225c17ac19ad`。字段顺序、null 标记、UTF-8 字节长度、数值文本或 scale 任一变化都必须改变相应测试结果。

### 批次内重复键

逐行转换完成并通过缺失校验后再计算批次键：

- COMPOSITE：按 `definition.businessKey().fields()` 原序取得转换值并形成不可变 `List<Object>`；
- FINGERPRINT：使用刚生成的 64 字符 `business_key` 字符串。

使用 `LinkedHashMap<Object,Map<String,Object>>` 保持首次出现顺序：

- 新键保存完整适配行；
- 已有键且完整适配行按 `Map.equals` 相等时，丢弃当前重复项、保留首项，并通过 `System.Logger` 记录一次固定 WARNING `Duplicate adapter row discarded`；不得附带 API、行号、键或值；
- 已有键但完整适配行不相等时，抛 `AdapterException(ErrorCode.ADAPTER_TYPE_INVALID, "Conflicting adapter key: api=<ApiName.value>, row=<rowIndex>")`，不回显键、冲突字段或两个行值。

M05-T04 已把 DECIMAL 固化到目标 scale，因此 `BigDecimal.equals` 可稳定判断适配行相等。任何异常在 `AdaptedBatch` 构造前传播，调用者拿不到部分 rows；logger 不是公共表面，测试不拦截或断言日志实现。

### 直接输入与约束比较

- M02-T04 提供不可变 `DownloadEnvelope`/`AdaptedBatch`：成功包络已保证计数、字段和行宽局部形状，批次允许空 rows、要求每行 key 与 columns 精确一致，并以单一 `ingestedAt` 表达批次时间。
- M02-T05 提供 `DatasetAdapter` 精确 SPI，以及只允许 `ADAPTER_FIELD_MISSING|ADAPTER_TYPE_INVALID` 的 `AdapterException`；本任务不修改公共接口、错误码或异常类型。
- M05-T02 提供只暴露 schema/元数据均验证通过定义的 `DatasetCatalog`，并冻结 FINGERPRINT 物理表在业务列后增加 `business_key`、技术来源列由后继持久化绑定的 schema 关系；本任务只接受装配方选出的定义，不重复 catalog/schema 校验。
- M05-T03 提供参数准入和有序不可变 `ValidatedParameters` 边界；adapter 只处理插件返回包络，不读取或重复校验 params，避免同一参数规则出现第二实现。
- M05-T04 提供七类严格 `ValueConverter`、从 0 开始的 `ConversionContext` 以及安全 `ADAPTER_TYPE_INVALID`；本任务在其后补齐 nullable/业务键缺失检查，不改变转换规则。

五项直接输入的职责互补且无冲突：M02 定义跨模块 SPI/数据形状/错误，M05-T02 保证定义可用，M05-T03 保证调用上游前的参数，M05-T04 保证单值转换；M05-T05 只聚合这些合同完成批次适配、指纹和批次内键语义。

## Files

- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`：实现包络准入、一次字段索引、逐值转换、缺失校验、业务键去重和 `AdaptedBatch` 构造。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java`：实现批准的类型规范文本、二进制片段、SHA-256 和小写十六进制合同。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/adapter/GenericDatasetAdapterTest.java`：以真实 plugin-api/M05 类型覆盖公开表面、输入映射、错误、指纹、去重和空批次。

不修改或删除其他文件。实现提交只暂存上述三个 Java 文件，固定消息为 `feat(core): adapt datasets from fixed metadata`；设计、交接、看板、POM、既有 Java、YAML、SQL、临时文件和生成的 `target` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前在允许 Mockito/Byte Buddy 本地 JVM attach 的环境运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期 `tensor-plugin-api` 79 项、`tensor-core` 42 项，共 121/121，0 failure、0 error、0 skipped；父项目、plugin-api、core 三层 Enforcer 通过。已有 platform-encoding、Mockito/JDK 动态 agent 和测试刻意触发的安全 WARNING 允许保留，不得新增其他 Maven/编译警告类别。attach 受限沙箱的既有十项 `MockMaker` 初始化错误是环境失败，不能作为代码 RED 或回归结论。

随后只完整创建 `GenericDatasetAdapterTest.java`，不创建两个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=GenericDatasetAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `GenericDatasetAdapter` 和 `FingerprintKeyCodec` 不存在而在 `tensor-core:testCompile` 非零；不得因依赖解析、上游未匹配测试、测试语法或既有失败形成伪 RED。

### 聚焦 GREEN

创建最小生产实现后重跑同一聚焦命令。`GenericDatasetAdapterTest` 固定恰有 11 个普通 `@Test`，11/11 通过且不使用 Mockito：

1. 反射确认两个生产类 final、构造器与 public 方法集合精确；构造器/null 参数不变量、`datasetKey()` 和 `definition()` 正确；
2. COMPOSITE 成功包络按定义列序完成 String/DATE/LONG/DECIMAL/null 转换，批次的 key、表、columns、业务键和唯一 `ingestedAt` 精确；输入不被修改；
3. 零行成功包络返回 columns 完整、rows 为空且保留同一 `ingestedAt` 的合法批次；
4. null/failure 包络遵守编程错误边界，plugin/api 身份不匹配产生精确安全 `ADAPTER_FIELD_MISSING`；
5. 来源字段缺失、额外或重排均产生同一精确安全 `ADAPTER_FIELD_MISSING`，不泄露实际字段或值；
6. 不可空列和 nullable 业务键在转换后为 null 时分别产生精确 `Missing adapter value`；合法 nullable 非键 null 与 TEXT 空白原样保留；
7. M05-T04 的七类类型失败至少选取 DATE 与 DECIMAL 反例，证明原 `ADAPTER_TYPE_INVALID` code/message 原样传播且没有部分批次；
8. codec 对 String/LocalDate/Long/BigDecimal/null 产生批准的规范文本、顺序、UTF-8 长度和固定已知向量；拒绝 null/空/重复/missing fields 与不支持类型；
9. FINGERPRINT 适配行在业务列后精确追加稳定 `business_key`，同一转换值和字段顺序重复计算一致，批次仍保留原身份字段定义；
10. COMPOSITE 与 FINGERPRINT 的完全相同重复行均只保留首次出现项且保持首现顺序；不通过日志拦截反向证明行为；
11. COMPOSITE 与 FINGERPRINT 的同键不同完整行均产生精确安全 `ADAPTER_TYPE_INVALID` 冲突摘要，不泄露键或值。

测试只使用 JUnit 5、AssertJ 和真实 `DatasetDefinition`、`DownloadEnvelope`、`AdaptedBatch`、`ValueConverter`、异常/值对象；期望值手工书写，不从生产 private helper 或 codec 反向生成。不得使用 Mockito、数据库、网络、时钟、日志断言、Token 或真实凭证。

### 模块、静态与范围门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期 `tensor-plugin-api` 79 项、既有 `tensor-core` 42 项、新测试 11 项，共 132/132，0 failure、0 error、0 skipped；三层 Enforcer 通过。

运行：

```bash
rg -n 'org\.springframework|java\.sql|javax\.sql|tushare|RestClient|ServiceLoader|(?i:token|credential)|String\.valueOf|\.doubleValue\(\)|Double|Float|initCause|getMessage\(\)' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java
rg -n 'source_plugin|source_api|ingested_at|Instant\.now|Clock|System\.currentTimeMillis|Charset\.defaultCharset|getBytes\(\)' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/adapter
git diff --check
```

两项扫描均预期无输出并退出 1；`clean` 退出 0；非目标 POM/app/plugin-api/plugin-tushare 无差异；提交前 scoped status 精确新增本任务三个 Java 文件且不列 `target`，M05-T04 三个既有文件无修改；格式检查退出 0。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确三文件范围，工作树干净。

## Acceptance

- `GenericDatasetAdapter`/`FingerprintKeyCodec` 的公开表面、构造依赖和无状态边界与设计精确一致，没有额外类型、重载或框架依赖；
- 成功包络身份、字段全集/顺序和行值只依据稳定元数据校验，一次字段索引、逐列转换、缺失检查和批次列顺序可观察；任一失败不产生部分 `AdaptedBatch`；
- 不可空列和所有业务键 null 均为精确安全 `ADAPTER_FIELD_MISSING`；M05-T04 类型失败原样传播；合法 nullable 非键 null 与 TEXT 内容不被改写；
- 指纹对批准的五类转换结果使用字段原序、显式 null tag、4 字节大端 UTF-8 长度、规范文本、SHA-256 和 64 位小写 hex，固定已知向量通过；
- COMPOSITE/FINGERPRINT 完全相同重复行只保留首项且顺序稳定，同键不同内容统一整批失败，消息不含键、值、实际字段或敏感内容；
- COMPOSITE 行只含业务列，FINGERPRINT 行只额外含 `business_key`；来源技术列由 batch components 留给 M06 绑定，唯一 `ingestedAt` 与空 rows 行为正确；
- 严格 TDD 得到缺两个生产类型的可归因 RED 后 11/11 GREEN；模块 `test`/`verify` 132/132、三层 Enforcer、静态、范围、格式、清理和精确三文件提交门禁全部得到预期结果；
- 未修改 POM、plugin-api、既有 Java、YAML、SQL 或其他模块，未提前实现参数、插件调用、持久化、事务、查询、REST 或前端职责。

## Risks

- 指纹字节格式和规范文本已成为数据库幂等合同；未来任何 tag、长度、字符集、字段顺序或数值文本变化都必须与版本化元数据、Flyway 唯一键迁移和历史数据重算一起发布，不能只改 codec。
- SHA-256 理论碰撞或同一 FINGERPRINT 身份字段对应不同非键内容在同批次都会表现为“同键不同完整行”并被拒绝；跨批次更新语义由 M06 Upsert 和业务键版本治理承担。
- M07-T02 必须让小数以 `BigDecimal` 进入 `DownloadEnvelope`；Float/Double 在 M05-T04 已被拒绝，codec 不尝试恢复已丢失精度。
- M05-T03 已完成参数准入，但本任务故意不验证 `envelope.params()`；后续下载编排必须先校验参数再调用插件，并只把成功包络交给 adapter。
- 完整 reactor 门禁必须允许 Mockito/Byte Buddy 本地 JVM attach；受限沙箱的既有 `MockMaker` 错误不能误判为本任务回归。
