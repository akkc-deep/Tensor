# Spring JDBC 数据库访问复杂度收敛设计

状态：已确认，待制定实施计划。

关联问题：[ISSUE-003：数据库交互逻辑较复杂](../../issues/problems/ISSUE-003-database-access-complexity.md)

## 背景与结论

当前业务数据访问使用 Spring JDBC。复杂度主要来自运行时元数据决定的表名、列、逻辑类型、业务键和批大小，而不是缺少 ORM 或 Mapper 框架。MyBatis 仍需保留这些动态规则，并会额外引入 SqlProvider、动态结果映射和 Batch Executor 配置，因此本次不迁移 MyBatis。

本设计保留现有 Spring JDBC 和业务边界，以最小重构收敛重复的 JDBC 类型处理，并从已有键 Repository 中分离 SQL 构造。目标是让类型变化只有一个维护点，让 Repository 只保留各自的查询或写入编排，不改变任何外部行为。

## 目标

- 统一 `LogicalType` 到 JDBC 类型的映射。
- 统一 String、日期、整数、小数、UTC 时间和带类型 null 的参数绑定。
- 统一业务列、业务键和技术列的 JDBC 结果读取。
- 将已有业务键查询 SQL 从校验、分块、绑定和结果组装中分离。
- 保持现有 SQL、事务、锁、批处理、计数、分页、异常和 HTTP 行为。
- 遵循最小代码原则，不建立新的通用数据访问框架。

## 非目标

- 不引入 MyBatis、JPA、jOOQ、ORM、代码生成或反射映射。
- 不改变数据库结构、Flyway 迁移、插件协议或数据集元数据。
- 不改变 `PersistenceService`、`DatasetQueryService` 或三个 Repository 的公开方法。
- 不改变 SQL 文本、占位符顺序、批大小、事务超时或锁生命周期。
- 不增加重试、缓存、SQL 日志、查询能力或新的异常包装。
- 不处理 Controller 分层或 HTTP 入参问题。

## 现状问题

JDBC 类型规则目前分散在多个类中：

- `JdbcValueBinder` 处理部分写入绑定。
- `ExistingKeyRepository` 自行映射 `LogicalType`、生成查询 SQL 并读取业务键。
- `GenericUpsertRepository` 再次映射 `LogicalType`。
- `GenericQueryRepository` 自行校验和绑定查询参数，并再次读取相同逻辑类型。

因此新增或修改一种逻辑类型时，查询、已有键读取和写入可能需要分别调整，存在规则漂移风险。`ExistingKeyRepository` 还同时承担校验、去重、分块、SQL 生成、参数绑定、结果读取和领域对象组装，职责过多。

## 总体结构

```text
PersistenceService / DatasetQueryService
                  ↓
ExistingKeyRepository / GenericUpsertRepository / GenericQueryRepository
        ↓                         ↓
ExistingKeySqlFactory       JdbcValueCodec
        ↓                         ↓
               JdbcTemplate
```

Service 与 Repository 公开边界不变。`ExistingKeySqlFactory` 只生成已有业务键查询 SQL，`JdbcValueCodec` 只处理 JDBC 类型和值。Repository 继续负责用例所需的校验、分块、批处理和结果组装。

## JdbcValueCodec

在 `com.akkc.tensor.core.persistence` 中用 `JdbcValueCodec` 替代 `JdbcValueBinder`。该类保持无状态，不访问 Spring 容器、数据库或数据集目录。

公开表面固定为：

```java
public final class JdbcValueCodec {
    public JdbcValueCodec();
    public int jdbcType(LogicalType logicalType);
    public void bind(
            PreparedStatement statement,
            int index,
            Object value,
            int jdbcType) throws SQLException;
    public void bindQueryValue(
            PreparedStatement statement,
            int index,
            Object value) throws SQLException;
    public Object read(
            ResultSet resultSet,
            int index,
            int jdbcType) throws SQLException;
}
```

它集中提供以下能力：

- 将 `LogicalType` 确定地映射为当前 JDBC `Types` 常量。
- 使用明确 setter 绑定 `String`、`LocalDate`、`Integer`、`Long`、`BigDecimal`、`Instant` 和 null。
- 仅在调用方提供明确 JDBC 类型时绑定 null。
- 使用明确 getter 读取 VARCHAR、LONGVARCHAR、CHAR、DATE、INTEGER、BIGINT、DECIMAL 和 TIMESTAMP。
- 将 DATE、BIGINT、DECIMAL 和 TIMESTAMP 分别规范为 `LocalDate`、`Long`、`BigDecimal` 和 `Instant`。
- 使用 UTC `Calendar` 读写 `Instant`，不依赖 JVM 默认时区。

Codec 不决定数据集列顺序、业务键、查询条件或 SQL。它不调用 `setObject` 或 `getObject`，不做字符串到业务类型的隐式转换。

查询参数继续遵守当前白名单，只允许 String、LocalDate、Integer 和 Long。`bindQueryValue` 完成该场景校验并保留 `Unsupported query value type` 错误。`bind` 继续使用 `Unsupported JDBC value type`；`read` 对不支持的 JDBC 类型使用固定的 `Unsupported JDBC result type`。不支持的绑定值抛 `IllegalArgumentException`，不支持的读取类型抛 `IllegalStateException`，错误中不回显数据值。当前业务键 JDBC 类型全部由受支持的 `LogicalType` 确定，因此旧的不可达 `Unsupported business key JDBC type` 分支不再保留。

## ExistingKeySqlFactory

在 `com.akkc.tensor.core.persistence` 中新增无状态 `ExistingKeySqlFactory`，只负责生成 `findExisting` 使用的 SELECT：

```java
public final class ExistingKeySqlFactory {
    public ExistingKeySqlFactory();
    public String create(
            DatasetDefinition definition,
            List<String> columnNames,
            int keyCount);
}
```

- 输入为已验证的 `DatasetDefinition`、有序物理业务键列和本分块的键数量。
- 单列键生成 `column IN (?, ...)`。
- 复合键生成 `(column1, column2, ...) IN ((?, ...), ...)`。
- SELECT 列顺序与物理业务键列顺序完全一致。
- 表名和每个列名都重新通过 `SqlIdentifierPolicy` 引用。
- 键数量必须为正，列集合必须非空且不含 null。
- 所有值位置仅生成 `?`，不接收也不读取业务键值。

工厂不决定 1000 个绑定参数上限或分块大小；这些仍是 `ExistingKeyRepository` 的执行策略。

## Repository 调整

### ExistingKeyRepository

继续负责：

- 输入和业务键宽度校验；
- 业务键去重；
- 按最多 1000 个绑定参数计算分块；
- 调用 `ExistingKeySqlFactory`；
- 通过 `JdbcValueCodec` 绑定和读取；
- 按原顺序组装不可变 `BusinessKey` 集合。

删除其本地 SQL 构造、`LogicalType` 到 JDBC 类型映射和 ResultSet 类型 switch。

### GenericUpsertRepository

继续负责批次一致性校验、事务存在性检查、列顺序和 `JdbcTemplate.batchUpdate`。它通过 `JdbcValueCodec` 获取逻辑类型对应的 JDBC 类型并绑定业务列及技术列，删除本地 `jdbcType` switch。

`UpsertSqlFactory`、单行参数顺序、元数据批大小以及 MySQL `ON DUPLICATE KEY UPDATE` 语义均不变。

### GenericQueryRepository

继续执行 `QuerySqlFactory` 已生成的 COUNT 和分页 SQL，验证 COUNT 结构，并按 `DatasetDefinition` 顺序组装不可变行 Map。它通过 `JdbcValueCodec` 绑定查询值并读取业务列、`source_plugin`、`source_api` 和 `ingested_at`，删除本地绑定与读取 switch。

`QuerySql`、`QuerySqlFactory`、COUNT-first、空结果、超界页归一、排序和列暴露规则均不变。

## 保留的组件

- `SqlIdentifierPolicy` 继续作为动态表名和列名的唯一白名单引用规则。
- `UpsertSqlFactory` 继续生成参数化 MySQL upsert。
- `QuerySqlFactory` 继续生成参数化 COUNT 和分页查询。
- `SchemaInspector` 继续使用原生 JDBC `DatabaseMetaData`；它不属于业务数据查询或写入。
- `PersistenceService` 继续管理数据集锁、事务同步、60 秒超时、已有键预查、upsert 和准确计数。
- `DatasetQueryService` 继续管理数据集查找、COUNT、页码归一和记录查询。

## 错误与安全边界

- 所有当前精确 SQL 文本和参数顺序保持不变。
- 标识符只能来自已准入的 `DatasetDefinition` 或固定技术列，并在 SQL 边界再次校验。
- 所有数据值继续由 PreparedStatement 绑定，禁止值字符串插值。
- Codec 的 null、索引和不支持绑定类型失败保持当前异常类别及已测试消息；不支持的读取类型使用设计冻结的新固定消息。
- SQL、连接、绑定或读取失败继续由 Spring 转换为 `DataAccessException`。
- 本次不改变 Service 对数据库失败的处理；相关业务错误语义属于其他已记录问题。
- 不记录或回显业务数据、Token、凭证或完整 SQL 参数。

## 文件范围

新增：

- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueCodec.java`
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/ExistingKeySqlFactory.java`
- `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/JdbcValueCodecTest.java`
- `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/ExistingKeySqlFactoryTest.java`

修改：

- `ExistingKeyRepository.java`
- `GenericUpsertRepository.java`
- `GenericQueryRepository.java`
- 直接验证旧 Binder 或 Repository 内部 JDBC 细节的现有测试

删除：

- `JdbcValueBinder.java`

不修改 Maven POM、Service、Controller、插件、YAML 或 Flyway 文件。

## 测试设计

### JdbcValueCodecTest

- 验证全部 `LogicalType` 的 JDBC 类型映射。
- 验证 String、LocalDate、Integer、Long、BigDecimal、Instant 和带明确类型 null 的 setter。
- 验证 String、LocalDate、Integer、Long、BigDecimal 和 UTC Instant 的 getter 及 `wasNull` 行为。
- 验证索引、null statement、非法绑定值、非法查询值和不支持读取类型。
- 验证 Instant 读写显式使用 UTC Calendar。

### ExistingKeySqlFactoryTest

- 锁定单字段和复合字段的精确 SELECT SQL。
- 验证占位符数量等于键宽乘键数量。
- 验证保留字引用、非法标识符、空列和非正键数量。
- 验证 SQL 不包含任何业务键值。

### 回归验证

- `ExistingKeyRepositoryIT` 继续验证空、单键、复合键、去重和超过绑定上限的分块行为。
- `PersistenceServiceIT` 继续验证插入、更新、混合计数、批量失败回滚、并发锁和指纹幂等。
- `DatasetQueryServiceIT` 继续验证 COUNT、过滤、分页归一、稳定排序、152 列读取和数值精度。
- `UpsertSqlFactoryTest` 和 `QuerySqlFactoryTest` 的精确 SQL 断言保持不变。
- 完整执行 `mvn -f data-plane/pom.xml -pl tensor-core -am verify`。
- 静态扫描确认 persistence/query 业务代码中的重复逻辑类型映射和 JDBC setter/getter 已从 Repository 移除，仅由 `JdbcValueCodec` 承担；`SchemaInspector` 是明确例外。
- 执行 `git diff --check`。

## 验收条件

- `JdbcValueBinder` 已由唯一的 `JdbcValueCodec` 替代。
- 三个 Repository 不再分别维护 JDBC 类型映射、参数 setter 或结果 getter。
- `ExistingKeyRepository` 不再构造 SQL 文本。
- Repository、Service 和 HTTP 公开行为保持兼容。
- 当前 SQL、参数顺序、事务、锁、批大小、计数、分页、类型精度和错误边界均由测试证明未变化。
- 没有引入 MyBatis、其他数据访问框架、通用 DAO 或无关重构。
- 聚焦测试、MySQL 集成测试、模块 verify、静态范围检查和格式检查全部通过。

## 风险与控制

- 公共 Codec 可能意外放宽查询值类型；通过独立的查询绑定入口和现有非法值测试锁定白名单。
- UTC 时间处理集中后可能改变驱动行为；通过 Calendar 捕获测试和 MySQL 集成结果锁定。
- SQL 提取可能改变空格、引用或占位符顺序；通过迁移前后的精确 SQL 断言锁定。
- 重构多个 Repository 容易把行为整理扩大为业务变更；实施计划必须按 Codec、SQL 提取、Repository 接入三个可回滚阶段推进，每阶段运行对应回归。
