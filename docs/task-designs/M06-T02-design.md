# M06-T02 复合键与指纹键编码/绑定——任务设计

任务编号：`M06-T02`
对应任务：[M06-T02](../superpowers/plans/tensor-modules/M06-core-persistence-query.md#task-m06-t02-业务键编码与绑定30h)
实施产物：`BusinessKey`、`BusinessKeyExtractor`、`JdbcValueBinder` 和 `BusinessKeyExtractorTest`

## Goal

在 `tensor-core` 中交付持久化层唯一的业务键提取和值绑定边界：把 M05 已适配的行转换为具有结构相等语义的不可变 `BusinessKey`，并把允许的 Java 值通过明确的 JDBC setter 绑定到 `PreparedStatement`。COMPOSITE 键保持元数据字段原序，FINGERPRINT 键直接消费 M05 `FingerprintKeyCodec` 已写入行的 `business_key`，null 通过调用方提供的 JDBC type 明确绑定，避免 `setObject` 的驱动歧义。

## Scope

包含：

- 创建不可变 `BusinessKey`，以防御性复制的非空有序值列表提供结构化 `equals`/`hashCode`；
- 创建无状态 `BusinessKeyExtractor`，公开 `extract(DatasetDefinition, Map<String,Object>)`；
- 为 COMPOSITE 数据集按 `definition.businessKey().fields()` 原序提取非 null 键值；
- 为 FINGERPRINT 数据集直接提取 M05 适配行中的内部 `business_key`，并重新校验其为 64 位小写十六进制 SHA-256 文本；
- 创建无状态 `JdbcValueBinder`，公开 `bind(PreparedStatement, int, Object, int)` 并使用明确 JDBC setter 处理 `String`、`LocalDate`、`Long`、`BigDecimal`、`Instant` 和 null；
- 对 `Instant` 使用 UTC `Calendar` 绑定，对 null 使用调用方提供的 JDBC type 调用 `setNull`；
- 以严格 TDD 覆盖公开表面、键顺序和相等性、M05 指纹一致性、缺失键、全部允许值、UTC、精度、typed null 和失败边界；
- 执行聚焦、模块回归、Enforcer、静态、范围、格式与清理门禁。

排除：

- 不修改 POM、plugin-api、M05 adapter/codec、M06-T01 SQL 类、YAML、Flyway SQL 或其他模块；
- 不重新实现、调用或复制 `FingerprintKeyCodec` 的规范序列化和 SHA-256 算法；
- 不从任意 FINGERPRINT identity fields 重新计算摘要，也不比较 `business_key` 与行内容；该一致性已由 M05 适配边界保证；
- 不实现 SQL 生成、已有键查询、键查询分批、数据集锁、插入/更新计数、Upsert repository、事务、回滚或查询；
- 不引入 Spring bean、`JdbcTemplate`、`DataSource`、数据库连接、Testcontainers、真实 MySQL、网络、时钟或配置；
- 不使用 `PreparedStatement.setObject`，不根据 JVM 默认时区绑定 `Instant`，不记录或回显业务键和值；
- 不创建键类型层次、模式专用子类、binding DTO、类型注册表、策略接口、builder、工厂或重载。

## Approach

### 公开表面与不可变键

在 `com.akkc.tensor.core.persistence` 中冻结以下公开合同，不增加其他 public/protected 构造器、字段或业务方法：

```java
public record BusinessKey(List<Object> values) {
    public BusinessKey {
        // Validation and defensive-copy contract specified below.
    }
}

public final class BusinessKeyExtractor {
    public BusinessKeyExtractor();
    public BusinessKey extract(DatasetDefinition definition, Map<String, Object> row);
}

public final class JdbcValueBinder {
    public JdbcValueBinder();
    public void bind(
            PreparedStatement statement,
            int index,
            Object value,
            int jdbcType) throws SQLException;
}
```

`BusinessKey` 的 compact constructor 用 `Objects.requireNonNull(values, "values")` 拒绝 null 列表，拒绝空列表和 null 元素，并以 `List.copyOf(values)` 保存快照。空列表固定抛 `IllegalArgumentException("values must not be empty")`；null 元素固定抛 `IllegalArgumentException("values must not contain null")`。record 自动生成的 `values()` 返回不可变列表，`equals`/`hashCode` 使用列表的有序结构语义：相同顺序和相同值相等，值顺序不同则不相等。实现和后继不得把 record 的 `toString()` 写入日志或外部错误。

`BusinessKeyExtractor` 和 `JdbcValueBinder` 均为 final、无实例字段、public 无参构造器不执行 I/O。extractor 用 `Objects.requireNonNull(definition, "definition")` 和 `Objects.requireNonNull(row, "row")` 拒绝 null；binder 用 `Objects.requireNonNull(statement, "statement")` 拒绝 null，并在任何 JDBC 调用前拒绝小于 1 的参数位置，固定抛 `IllegalArgumentException("index must be positive")`。`jdbcType` 是调用方从已验证 schema/元数据取得的 `java.sql.Types` 值；本任务不增加第二套 schema/type 映射。

### COMPOSITE 与 FINGERPRINT 提取

COMPOSITE 提取按 `definition.businessKey().fields()` 的原声明顺序逐项检查 `row.containsKey(field)`，随后读取值。任一字段缺失或值为 null 均在构造 `BusinessKey` 前固定抛 `IllegalArgumentException("Missing business key")`；消息不包含字段名、键或行值。成功时以同一顺序创建 `BusinessKey`，不排序、不按 row 迭代顺序提取、不修改 definition、fields 或 row。

FINGERPRINT 提取只检查和读取固定内部列 `business_key`，不遍历 identity fields，不调用 `FingerprintKeyCodec`，也不重新计算摘要：

- 列不存在或值为 null时，抛 `IllegalArgumentException("Missing business key")`；
- 值不是 `String`，或不满足精确正则 `^[0-9a-f]{64}$` 时，抛 `IllegalArgumentException("Invalid fingerprint business key")`；
- 合法值作为唯一元素形成 `BusinessKey(List.of(fingerprint))`。

该规则使持久化键与 M05 已附加到 `AdaptedBatch` 行的物理主键完全相同，并让 M05 `FingerprintKeyCodec` 继续成为唯一摘要编码实现。测试使用真实 codec 生成已知摘要再交给 extractor，证明两层值一致；生产 extractor 不依赖 codec，避免重复哈希或产生第二种规范。

### 明确的 JDBC setter

`JdbcValueBinder.bind` 只按非 null 值的批准运行时类型分派一次：

| Java 值 | JDBC 调用 |
|---|---|
| `String` | `statement.setString(index, value)` |
| `LocalDate` | `statement.setDate(index, java.sql.Date.valueOf(value))` |
| `Long` | `statement.setLong(index, value)` |
| `BigDecimal` | `statement.setBigDecimal(index, value)` |
| `Instant` | `statement.setTimestamp(index, Timestamp.from(value), UTC calendar)` |
| null | `statement.setNull(index, jdbcType)` |

UTC calendar 必须显式使用 `TimeZone.getTimeZone("UTC")`，不得使用系统默认时区；每次 Instant 绑定创建独立 calendar，不保存可变共享状态。`Timestamp.from(instant)` 保留传入 instant，binder 不生成当前时间、不主动截断到毫秒；MySQL `DATETIME(3)` 的存储精度由已验证 schema/driver 负责。

`jdbcType` 对 null 决定 `setNull` 的确切 SQL 类型；对非 null 值仍由运行时类型选择明确 setter，binder 不用 `setObject`，也不重复验证调用方已从定义/schema 取得的 JDBC type。M05 适配结果保证业务值只为 `String`、`LocalDate`、`Long`、`BigDecimal` 或 null；`Instant` 只用于批次 `ingestedAt`。其他非 null 运行时类型在 JDBC 调用前固定抛 `IllegalArgumentException("Unsupported JDBC value type")`，不回显类名或值。

任何明确 setter 抛出的 `SQLException` 原样传播，不包装、不替换消息、不附加 cause。binder 不关闭或保存 `PreparedStatement`，不推进参数索引，不返回下一个索引；调用方负责以 Upsert/预查模板的占位符顺序传入从 1 开始的位置。

### 数据流与直接输入

后继持久化流程对每个已适配行执行：

1. 将同一已验证 `DatasetDefinition` 和行交给 `BusinessKeyExtractor`；
2. COMPOSITE 得到定义键字段的有序值，FINGERPRINT 得到单个 M05 `business_key`；
3. 使用 `BusinessKey` 的结构相等语义进行 M06-T03 的已有键集合比较；
4. 由后继调用方根据 SQL 占位符顺序逐值调用 `JdbcValueBinder`，并为 nullable 值传入已验证 schema 对应的 JDBC type。

唯一直接任务依赖 M05-T05 已完成并提供：不可变且列顺序稳定的 `AdaptedBatch` 行、非 null COMPOSITE 键、FINGERPRINT 行末尾由唯一 `FingerprintKeyCodec` 生成的 `business_key`，以及 `String`/`LocalDate`/`Long`/`BigDecimal`/null 的适配值边界。M06-T02 不改变这些合同，只把逻辑行键转换为持久化层结构键并提供类型明确的 JDBC 调用。模块任务卡同时冻结 `Instant` 来源与 `PreparedStatement` 绑定目标；两项输入无冲突。

## Files

- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java`：实现有序、不可变、结构相等的键值对象及其构造不变量。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java`：实现 COMPOSITE 原序提取和 FINGERPRINT 物理键消费/格式校验。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java`：实现五类非 null 值、UTC Instant 与 typed null 的明确 JDBC setter 分派。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/BusinessKeyExtractorTest.java`：以真实定义、真实 M05 codec 和窄 `PreparedStatement` mock 覆盖提取与绑定合同。

不修改或删除其他文件。实现提交只暂存上述四个 Java 文件，固定消息为 `feat(core): bind dataset keys and JDBC values`；设计、交接、看板、POM、M05 Java、M06-T01 Java、YAML、SQL、临时文件和生成的 `target` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前在允许 Mockito/Byte Buddy 本地 JVM attach 的环境运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期 `tensor-plugin-api` 79 项、当前 `tensor-core` 59 项，共 138/138，0 failure、0 error、0 skipped；父项目、plugin-api、core 三层 Enforcer 通过。已有 platform-encoding、Mockito/JDK 动态 agent 和测试刻意触发的固定安全 WARNING 允许保留，不得新增其他构建警告类别。attach 受限沙箱中的既有十项 `MockMaker` 初始化错误是环境失败，不能作为代码 RED 或回归结论。

随后只完整创建 `BusinessKeyExtractorTest.java`，不创建三个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=BusinessKeyExtractorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `BusinessKey`、`BusinessKeyExtractor` 和 `JdbcValueBinder` 不存在而在 `tensor-core:testCompile` 非零；不得因依赖解析、上游未匹配测试、测试语法、Mockito 或既有失败形成伪 RED。

### 聚焦 GREEN

创建最小生产实现后重跑同一聚焦命令。`BusinessKeyExtractorTest` 固定恰有 8 个普通 `@Test`，8/8 通过：

1. 反射确认 `BusinessKey` 是 record；确认两个协作者 final、各自唯一 public 无参构造器、各自声明 public 方法及参数/返回/throws 精确且均无实例字段；覆盖所有 null、空 values 与 1-based index 构造边界；
2. COMPOSITE 使用定义字段原序而非 row 迭代顺序，输入不变；相同有序值结构相等且 hash 相同，不同值或顺序不相等，`values()` 不可修改且来源列表后续修改不影响键；
3. 真实 `FingerprintKeyCodec` 对批准固定向量生成摘要，extractor 从 `business_key` 取得精确单元素键；重复提取相等且不调用第二种编码路径；
4. COMPOSITE 字段缺失/null 与 FINGERPRINT 列缺失/null 使用固定 `Missing business key`；FINGERPRINT 非 String、长度错误、大写或非十六进制使用固定 `Invalid fingerprint business key`，所有消息不含值；
5. 对 `String`、`LocalDate`、`Long`、`BigDecimal` 分别只调用 `setString`、`setDate`、`setLong`、`setBigDecimal`，日期值相同且 BigDecimal 的数值和 scale 原样保留；
6. 对一个含非零纳秒且跨日边界的固定 `Instant` 只调用三参数 `setTimestamp`，捕获的 `Timestamp.toInstant()` 与输入相同且 Calendar 时区为 UTC；
7. 对代表性 `Types.VARCHAR`、`Types.DECIMAL` 和 `Types.TIMESTAMP` null 分别只调用 `setNull(index, jdbcType)`，从不调用任何 `setObject`；
8. 未支持的非 null 类型在 JDBC 调用前产生固定安全异常，非法 index 不触发 statement；明确 setter 的受控 `SQLException` 同一实例原样传播。

测试只使用 JUnit 5、AssertJ、真实 `DatasetDefinition`/值对象、真实 `FingerprintKeyCodec`，以及 Mockito 对 `PreparedStatement` 这一外部 JDBC 调用边界的窄替身。期望键、摘要、JDBC 参数位置、SQL type、setter 参数和 UTC 均手工断言，不调用生产 private helper 生成期望。不得使用数据库、Spring context、网络、系统时钟、真实 YAML、Token 或凭证。

### 模块、静态与范围门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期 `tensor-plugin-api` 79 项、既有 `tensor-core` 59 项加新测试 8 项，共 146/146，0 failure、0 error、0 skipped；三层 Enforcer 通过。

运行：

```bash
rg -n 'org\.springframework|javax\.sql|DataSource|JdbcTemplate|tushare|RestClient|ServiceLoader|(?i:token|credential)|setObject|Instant\.now|ZoneId\.systemDefault|TimeZone\.getDefault' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java
rg -n 'MessageDigest|SHA-256|FingerprintKeyCodec|ByteBuffer|StandardCharsets' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence
git diff --check
```

两项源码扫描均预期无输出并退出 1；`clean` 退出 0；POM、其他模块、M05 adapter/codec 与 M06-T01 实现无差异；提交前 scoped status 精确新增本任务四个 Java 文件且不列 `target`；格式检查退出 0。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确四文件范围，工作树干净。

## Acceptance

- `BusinessKey`、`BusinessKeyExtractor`、`JdbcValueBinder` 的公开表面、不可变/无状态边界和固定安全错误与设计精确一致，没有额外生产类型、重载、Spring 或数据库依赖；
- COMPOSITE 键只按定义字段原序提取且值必须存在、非 null；键以有序列表提供稳定结构相等和 hash，输入集合均不被修改；
- FINGERPRINT 键只消费 M05 行中合法的 64 位小写 `business_key` 并形成单元素结构键，生产代码不重新编码或哈希；真实 `FingerprintKeyCodec` 一致性测试通过；
- `String`、`LocalDate`、`Long`、`BigDecimal` 和 `Instant` 分别通过明确 setter 绑定，BigDecimal scale 和 Instant 时间点不丢失，Instant 显式使用 UTC；
- null 精确调用 `setNull(index, jdbcType)`，生产代码没有 `setObject`；未支持类型、非法 index 和 SQLException 遵守固定失败边界；
- 严格 TDD 得到缺三个生产类型的可归因 RED 后 8/8 GREEN；模块 `test`/`verify` 146/146、三层 Enforcer、静态、范围、格式、清理和精确四文件提交门禁全部得到预期结果；
- 未修改 POM、plugin-api、M05/M06-T01 实现、YAML、SQL 或其他模块，未提前实现预查、锁、计数、Upsert、事务、查询、REST 或前端职责。

## Risks

- FINGERPRINT extractor 信任 M05 适配边界对 `business_key` 与 identity fields 的内容一致性，只重新校验物理键文本格式；绕过 `GenericDatasetAdapter` 手工构造行可能提供格式正确但内容不一致的摘要。后继装配必须只持久化已验证的 `AdaptedBatch`，本任务不复制 codec 来建立第二种一致性规则。
- `jdbcType` 由后继调用方依据已验证 `DatasetDefinition` 和固定技术列提供；传入错误 type 会使 null 以错误 SQL 类型绑定。M06-T03/M06-T04 设计必须冻结从业务列逻辑类型与技术列到既有 schema JDBC type 的唯一映射，并按同一占位符顺序调用 binder。
- UTC calendar 消除 JVM 默认时区影响，但 MySQL DATETIME 不携带时区；连接会话仍必须遵守 TRD 已冻结的 UTC 配置。切换数据库、驱动或时区策略需要重新验证，不在本任务引入方言层。
- 完整 reactor 门禁必须允许 Mockito/Byte Buddy 本地 JVM attach；受限沙箱的既有 `MockMaker` 错误不能误判为本任务回归。
