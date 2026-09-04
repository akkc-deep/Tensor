# ISSUE-003：Spring JDBC 数据库层重构方案

方案状态：架构设计已确认，待文档复核。

关联问题：[ISSUE-003：数据库交互逻辑较复杂](../problems/ISSUE-003-database-access-complexity.md)

## 背景与结论

当前业务数据访问使用 Spring JDBC。复杂度主要来自运行时元数据决定的表名、列、逻辑类型、业务键、过滤条件和批大小，而不是缺少 ORM 或 Mapper 框架。MyBatis 仍需保留这些动态规则，并会额外引入 `SqlProvider`、动态结果映射和 Batch Executor 配置，因此本次不迁移 MyBatis。

本方案允许调整 Service、Repository 和 Spring 装配，但不改变数据库。通过启动期 `DatasetJdbcPlanRegistry`、统一 JDBC 类型编解码以及面向用例的读写 Repository，把分散的元数据解析、固定 SQL 生成、参数绑定和结果读取收敛到数据库层。

数据库往返次数和外部行为保持不变：

- 写入仍为“已有键查询 → Batch Upsert”。
- 查询仍为“COUNT → 页码归一 → Page Query”。
- 精确 `insertedRows/updatedRows`、超界页自动归一、事务、锁、批处理、插件协议和 HTTP 行为全部保留。

预期收益是降低代码复杂度、减少重复 JVM 计算并提高数据库层的可测试性，不承诺明显提升数据库吞吐量。

## 目标

- 每个已接纳的数据集只在启动时解析一次 JDBC 元数据和固定 SQL。
- 将三个数据库 Repository 收敛为读、写两个用例级 Repository。
- 统一 `LogicalType` 到 JDBC 类型的映射、参数绑定和结果读取。
- Service 不再了解已有键查询、Upsert SQL、COUNT SQL 或分页 SQL 中间对象。
- 保持现有 SQL 语义、数据库往返、事务、锁、计数、分页和异常传播行为。
- 用最小数量的专用组件完成重构，不建立通用 DAO 框架。

## 非目标

- 不引入 MyBatis、JPA、jOOQ、ORM、代码生成或反射映射。
- 不修改数据库表、索引、约束或 Flyway。
- 不改变插件 API、数据集元数据、Controller、HTTP DTO、状态码或响应结构。
- 不增加缓存、重试、SQL 日志、查询能力或新的异常包装。
- 不把 `SchemaInspector` 纳入业务读写 Repository。
- 不以本次代码重构承诺数据库侧性能提升。

## 现状问题

当前数据库逻辑跨 Service、Repository 和 SQL Factory 分散：

- `PersistenceService` 同时编排业务键提取、已有键查询、计数、锁和事务。
- `DatasetQueryService` 生成 `QuerySql`、执行 COUNT、归一页码并再次生成分页 SQL。
- `ExistingKeyRepository` 同时承担元数据解析、分块、SQL 构造、绑定、读取和结果组装。
- `GenericUpsertRepository` 每次写入都重新生成固定 Upsert SQL 和 JDBC 类型列表。
- `GenericQueryRepository` 重复维护查询值绑定及结果类型读取。
- `JdbcValueBinder` 只覆盖写入的一部分类型规则，读取和查询绑定仍散落在 Repository。

这使得每次请求或写入批次都会重复处理不变的数据集元数据，并让新增逻辑类型或调整 JDBC 行为需要修改多个位置。

## 总体结构

```text
DatasetCatalog
      │ accepted definitions
      ▼
DatasetJdbcPlanRegistry ── startup compile ──► immutable DatasetJdbcPlan
      ▲                                                │
      │ require(datasetKey)                            ├──────────────┐
      │                                                ▼              ▼
PersistenceService                           DatasetWriteRepository  DatasetReadRepository
 lock + transaction                           existing keys + upsert  count + page
      │                                                │              │
      └────────────────────────────────────────────────┴──────────────┘
                                                       ▼
                                                  JdbcTemplate
```

`DatasetJdbcPlan` 是启动期元数据与 SQL 的不可变快照；Service 只负责用例边界；Repository 封装完整的数据库交互协议；`JdbcValueCodec` 是唯一 JDBC 类型规则入口。

## DatasetJdbcPlanRegistry

公开边界固定为：

```java
public final class DatasetJdbcPlanRegistry {
    public DatasetJdbcPlanRegistry(DatasetCatalog catalog);
    public DatasetJdbcPlan require(DatasetKey datasetKey);
}
```

为支持启动期遍历，`DatasetCatalog` 增加 `public List<DatasetDefinition> definitions()`，返回不可变有序定义列表。Registry 只接收已经由 `DatasetStartupValidator` 接纳的数据集，并在构造时为每个数据集生成一个 `DatasetJdbcPlan`。构造完成后 Registry 和所有 Plan 均不可变，不在运行时延迟创建或缓存新 Plan。

Plan 的公开数据结构固定为：

```java
public record DatasetJdbcPlan(
        DatasetDefinition definition,
        List<Column> writeColumns,
        List<Column> keyColumns,
        List<Column> resultColumns,
        Set<String> declaredFilters,
        String quotedTable,
        String upsertSql,
        String selectColumnsSql,
        String orderBySql,
        int existingKeyChunkSize) {
    public record Column(String name, String quotedName, int jdbcType) {}
}
```

`writeColumns` 包含业务列、可选指纹列和三个技术列；`keyColumns` 使用直接业务键列或指纹列；`resultColumns` 包含业务列和三个查询可见技术列。所有集合在构造时防御性复制，列顺序与现有参数及结果顺序一致。

已有键占位符数量、动态 WHERE 条件、LIMIT 和 OFFSET 取决于运行时输入，仍在 Repository 内生成；表名、列名、类型、SELECT 列表、排序和 Upsert SQL 不再重复解析。

`require` 收到 null 时失败；找不到数据集时保持 `IllegalArgumentException("Dataset is not available")`。

## DatasetWriteRepository

`ExistingKeyRepository` 和 `GenericUpsertRepository` 合并为：

```java
public final class DatasetWriteRepository {
    public DatasetWriteRepository(JdbcTemplate jdbcTemplate);
    public WriteCounts persist(DatasetJdbcPlan plan, AdaptedBatch batch);
}
```

职责固定为：

1. 接收已经由 Service 验证、且与 Plan 匹配的批次。
2. 使用 Plan 的业务键元数据提取并去重 `BusinessKey`。
3. 按最多 1000 个绑定参数分块查询已有键。
4. 使用 `WriteCounts.from` 计算精确插入和更新数量。
5. 使用 Plan 中已经生成的 Upsert SQL、列顺序和批大小执行 `JdbcTemplate.batchUpdate`。
6. 返回 `WriteCounts`。

Repository 不创建事务、不获取锁，也不提交或回滚。它要求调用线程已有活动事务，保持当前写入安全边界。业务键查询和 Batch Upsert 任一步失败时，异常向上传播，由外层事务整体回滚。

单列业务键继续生成 `IN (?, ...)`，复合业务键继续生成元组 `IN ((?, ...), ...)`。所有值只通过 `PreparedStatement` 绑定。

## DatasetReadRepository

`GenericQueryRepository`、`QuerySqlFactory` 和公开 `QuerySql` 中间对象收敛为：

```java
public final class DatasetReadRepository {
    public DatasetReadRepository(JdbcTemplate jdbcTemplate);
    public DatasetPage query(DatasetJdbcPlan plan, QueryCriteria criteria);
}
```

职责固定为：

1. 根据 Plan 中的数据集过滤元数据校验 `QueryCriteria`。
2. 在 Plan 的固定 SQL 片段上补充参数化 WHERE 条件。
3. 执行 COUNT 并验证结果必须是唯一、非 null、非负数值。
4. 总数为零时返回第 1 页空结果。
5. 根据总页数归一超界页码。
6. 使用归一后的 LIMIT、OFFSET 执行 Page Query。
7. 按 Plan 的结果列及 JDBC 类型映射不可变行，并返回 `DatasetPage`。

COUNT SQL 和 Page SQL 可以使用 Repository 内部的私有值对象传递，但不再作为 Service 或公开数据库 API 的一部分。COUNT 和分页查询之间不新增事务，保持当前并发可见性语义。

## JdbcValueCodec

`JdbcValueCodec` 替代 `JdbcValueBinder`，作为数据库层唯一的类型规则入口：

- 将所有 `LogicalType` 映射为明确的 JDBC `Types` 常量；
- 绑定 String、LocalDate、Integer、Long、BigDecimal、Instant 和带明确类型的 null；
- 读取字符、日期、整数、长整数、小数和 UTC 时间；
- 对整数读取使用 `wasNull`，避免把数据库 null 映射为零；
- 对 Instant 显式使用 UTC `Calendar`，不依赖 JVM 默认时区；
- 保留查询参数只允许 String、LocalDate、Integer 和 Long 的白名单。

Codec 不决定列顺序、业务键、查询条件或 SQL，不调用 `setObject` 或通用反射映射，也不做字符串到业务类型的隐式转换。

`DatasetStartupValidator` 也改用 `JdbcValueCodec` 获取期望 JDBC 类型，删除其本地 `LogicalType` switch，确保启动校验与运行时读写使用同一套类型规则。

## Service 与装配调整

### PersistenceService

构造依赖调整为：

```java
public PersistenceService(
        DatasetJdbcPlanRegistry plans,
        DatasetLockManager locks,
        DatasetWriteRepository writes,
        PlatformTransactionManager transactions);
```

执行流程为：

1. 根据 `batch.datasetKey()` 获取 Plan。
2. 在加锁前验证批次一致性；空批次直接返回 `WriteCounts(0, 0)`。
3. 获取数据集锁。
4. 使用现有 `PROPAGATION_REQUIRED`、60 秒超时执行事务。
5. 注册事务完成后的解锁回调。
6. 在事务内调用一次 `writes.persist(plan, batch)`。

`BusinessKeyExtractor`、已有键查询和精确计数从 Service 移入写 Repository。锁转交事务同步和异常路径解锁逻辑保持不变。

### DatasetQueryService

构造依赖调整为：

```java
public DatasetQueryService(
        DatasetJdbcPlanRegistry plans,
        DatasetReadRepository reads);
```

Service 校验 key 和 criteria、获取 Plan，然后调用一次 `reads.query(plan, criteria)`。COUNT、页码归一、分页 SQL 和结果组装不再泄露到 Service。

### ApplicationConfiguration

Spring 装配改为注册：

- 一个 `DatasetJdbcPlanRegistry`；
- 一个 `DatasetWriteRepository`；
- 一个 `DatasetReadRepository`；
- 使用新依赖构造的两个 Service。

不增加 Maven 依赖或配置项。

## 保留与删除

保留：

- `DatasetCatalog` 和 `DatasetStartupValidator`，但后者改用统一 JDBC 类型映射；
- `SchemaInspector` 及其原生 JDBC `DatabaseMetaData` 实现；
- `SqlIdentifierPolicy`；
- `UpsertSqlFactory`，但只允许 Registry 在启动编译 Plan 时调用；
- `BusinessKey`、`BusinessKeyExtractor`、`WriteCounts` 和 `DatasetLockManager`。

新增：

- `DatasetJdbcPlan`；
- `DatasetJdbcPlanRegistry`；
- `JdbcValueCodec`；
- `DatasetWriteRepository`；
- `DatasetReadRepository`。

删除：

- `ExistingKeyRepository`；
- `GenericUpsertRepository`；
- `GenericQueryRepository`；
- `JdbcValueBinder`；
- `QuerySql`；
- `QuerySqlFactory`。

新旧 Repository 可在中间实施步骤中短暂并存，但最终代码只能保留新的读写路径。

## 数据流

### 写入

```text
AdaptedBatch
  → require Plan
  → validate / empty shortcut
  → acquire dataset lock
  → begin or join transaction
  → extract and deduplicate keys
  → SELECT existing keys in chunks
  → calculate exact WriteCounts
  → batch upsert with compiled SQL
  → transaction completion unlock
  → WriteCounts
```

写入数据库往返仍是已有键查询加 Batch Upsert。分块较多时已有键查询次数仍按当前 1000 参数上限增长，本方案不改变该策略。

### 查询

```text
DatasetKey + QueryCriteria
  → require Plan
  → validate declared filters
  → build parameterized WHERE
  → COUNT
  → empty result or normalize page
  → Page Query
  → typed immutable rows
  → DatasetPage
```

非空查询仍固定为 COUNT 和 Page Query 两次数据库往返。

## 错误与安全边界

- 仅为 `DatasetCatalog` 已接纳的数据集创建 Plan。若已接纳定义无法编译为合法 Plan，视为内部配置不一致并使应用启动失败。
- 数据集不存在继续抛出 `IllegalArgumentException("Dataset is not available")`。
- 批次一致性校验发生在加锁和事务前；空批次不加锁、不开启事务、不访问数据库。
- 写 Repository 保留活动事务检查，不自行启动事务或重试。
- 已有键查询或 Batch Upsert 失败时整个事务回滚。
- COUNT 和 Page Query 不新增事务或快照隔离。
- 非唯一、null 或负数 COUNT 继续抛出 `IllegalStateException("Count query returned an invalid result")`。
- 总数为零时返回第 1 页；超界页在 Page Query 前归一。
- 标识符只能来自 Plan，并在启动编译时通过 `SqlIdentifierPolicy` 引用。
- 运行时过滤字段只能来自现有白名单，数据值全部通过 `PreparedStatement` 绑定。
- JDBC、连接、绑定和读取失败继续由 Spring 转换为 `DataAccessException`，Service 不包装或吞掉异常。
- 不记录或回显业务数据、SQL 参数、Token 或凭证。

唯一有意提前的失败时机是：已通过 Catalog 校验、但无法生成合法 Plan 的内部不一致，从首次访问失败提前为应用启动失败。

## 测试设计

### DatasetJdbcPlanRegistryTest

- 验证全部 `LogicalType` 的 JDBC 类型映射。
- 验证直接业务键和指纹业务键的有序列及类型。
- 锁定固定 Upsert SQL、查询列、排序片段、键宽和分块大小。
- 验证 Registry 和 Plan 不可变。
- 验证数据集缺失、集合防御性复制和 Plan 构造约束。

### JdbcValueCodecTest

- 验证 String、LocalDate、Integer、Long、BigDecimal、Instant 和带明确类型 null 的 setter。
- 验证字符、日期、整数、长整数、小数和 UTC Instant 的 getter 及 `wasNull` 行为。
- 验证索引、null statement、非法绑定值、非法查询值和不支持读取类型。
- 验证 Instant 读写显式使用 UTC `Calendar`。

### DatasetWriteRepositoryIT

- 验证单键、复合键、指纹键、重复键和超过 1000 个参数的分块。
- 验证插入、更新、混合批次及批内重复键的精确计数。
- 验证列顺序、批大小和 Upsert 参数顺序。
- 验证无活动事务拒绝执行。
- 验证已有键查询或批量写入失败时整体回滚。

### DatasetReadRepositoryIT

- 验证无过滤及全部受支持过滤组合。
- 验证空结果、超界页归一、稳定排序和分页参数。
- 验证 152 列读取、null、数值精度和 UTC 时间。
- 验证非法过滤元数据、非法 COUNT 结构及数据库异常传播。

### 回归验证

- 更新 `PersistenceServiceIT` 和 `DatasetQueryServiceIT`，锁定新的职责边界以及原有锁、事务、计数和分页行为。
- 更新 `DownloadControllerIT`、`DatasetControllerIT` 和 `FixtureFlowIT` 的测试装配，不改变 HTTP 断言。
- 验证 Spring 应用上下文只装配新的 Registry 和两个 Repository。
- 完整执行 `mvn -f data-plane/pom.xml verify`。
- 静态扫描确认 `DatasetStartupValidator` 和业务 Repository 中不再有重复的逻辑类型 switch、JDBC setter/getter 和每请求固定 SQL 构造；`SchemaInspector` 只读取数据库元数据，不承担业务值编解码。
- 执行 `git diff --check`。

## 分阶段迁移

1. 测试驱动引入 `JdbcValueCodec`、`DatasetJdbcPlan` 和 Registry，旧 Repository 暂时继续工作。
2. 新增并验证 `DatasetWriteRepository`，迁入已有键查询、计数和 Batch Upsert。
3. 新增并验证 `DatasetReadRepository`，迁入 COUNT、页码归一、分页查询和结果映射。
4. 原子切换 Service、Spring 装配和测试构造方式。
5. 删除旧 Repository、Binder、`QuerySql` 和 `QuerySqlFactory`，完成全量回归及静态检查。

每一阶段都必须通过对应聚焦测试后才能进入下一阶段；最终不得保留双重数据库访问路径。

## 验收条件

- 每个已接纳数据集只在启动时生成一次固定 SQL、列顺序和 JDBC 类型计划。
- 三个旧 Repository 已替换为 `DatasetWriteRepository` 和 `DatasetReadRepository`。
- Service 不再生成 SQL、读取结果集或编排多个数据库 Repository。
- `JdbcValueCodec` 是业务数据库层唯一的 JDBC 类型映射、绑定和读取实现。
- 写入仍返回精确 `insertedRows/updatedRows`，查询仍自动归一超界页。
- 数据库往返、SQL 语义、事务、锁、批大小、类型精度、异常传播、插件协议和 HTTP 行为没有退化。
- 没有修改数据库、Flyway、Maven 依赖、Controller 或配置文件。
- 聚焦测试、完整 Maven verify、静态范围检查和 `git diff --check` 全部通过。

## 风险与控制

- Plan 可能固化错误的列顺序或 JDBC 类型：使用精确 Plan 单元测试和现有 152 列集成测试锁定。
- 合并写 Repository 可能改变计数或事务顺序：保留“已有键查询 → 计数 → Batch Upsert”的原顺序，并用混合批次和回滚测试验证。
- 合并读 Repository 可能改变超界页行为：保留 COUNT-first 和重新计算分页参数的顺序，并锁定空结果及超界页测试。
- 启动编译可能扩大单个组件职责：Plan 只保存不可变数据库元数据，运行时行为仍分别属于读写 Repository。
- 重构范围较大，可能出现新旧路径并存：最后阶段通过静态扫描和 Spring 装配测试确保旧组件全部删除。
- JVM 侧重复工作减少不等于数据库吞吐提升：验收只要求无性能退化，不作未经基准测试支持的收益承诺。
