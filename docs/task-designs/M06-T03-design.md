# M06-T03 已有键预查、数据集锁和插入/更新计数——任务设计

任务编号：`M06-T03`
对应任务：[M06-T03](../superpowers/plans/tensor-modules/M06-core-persistence-query.md#task-m06-t03-已有键数据集锁和计数35h)
实施产物：`DatasetLockManager`、`ExistingKeyRepository`、`WriteCounts` 和 `ExistingKeyRepositoryIT`

## Goal

在 `tensor-core` 中交付写入前的三个确定性边界：按 `DatasetKey` 获取 JVM 内公平可重入数据集锁，以参数化且受 1000 个绑定参数上限约束的 MySQL 查询预查本批不同物理业务键，并只依据预查集合计算插入/更新计数。M06-T04 可在同一 Spring 事务内组合“锁 → 预查 → Upsert → 提交 → 解锁”，不依赖 MySQL affected-row 语义，且始终满足 `insertedRows + updatedRows == distinct adapted business keys`。

## Scope

包含：

- 创建按 `DatasetKey` 隔离的 `DatasetLockManager`，内部使用公平 `ReentrantLock(true)`，支持同线程再次 `acquire`，并在最后一个持有者/等待者释放后清理映射；
- 创建基于 `JdbcTemplate` 的 `ExistingKeyRepository`，只从已验证 `DatasetDefinition` 派生表名、物理键列、列顺序和 JDBC 类型；
- 单物理键列使用参数化 `IN (?, ...)`，多物理键列使用 MySQL row-constructor `(<k1>, ..., <kn>) IN ((?, ..., ?), ...)`；
- 每条查询最多绑定 1000 个参数，分块键数固定为 `floor(1000 / physicalKeyWidth)`；
- COMPOSITE 键按 `definition.businessKey().fields()` 原序绑定和映射，FINGERPRINT 只查询内部 `business_key`；
- 创建 `WriteCounts`，从本批不同 `BusinessKey` 与预查已有集合的成员关系计算非负 `long` 计数，并显式守卫总和不变量；
- 只为 `tensor-core` 增加 BOM 管理的 `com.mysql:mysql-connector-j` 测试依赖，以固定官方 MySQL `8.4.6` 运行 8 项集成/并发合同；
- 执行严格 RED/GREEN、显式 `*IT`、标准 reactor 回归、Enforcer、依赖、静态、范围、格式和清理门禁。

排除：

- 不实现 Upsert、批量写入、事务声明/事务管理器、提交、回滚、服务编排或 M06-T04 的 `GenericUpsertRepository`/`PersistenceService`；
- 不在本任务内自行开启事务或获取连接；`JdbcTemplate` 必须参加 M06-T04 将提供的同一 Spring `REQUIRED` 事务；
- 不修改父 POM、plugin-api、现有 M05/M06-T01/M06-T02 Java、YAML、Flyway SQL、Surefire/Failsafe 生命周期或其他模块；
- 不使用 MySQL affected-row、`SELECT *`、字符串插入键值、临时表、数据库锁、H2、浮动 MySQL 标签或多实例协调；
- 不把重复输入键重复计数，不查询非物理 FINGERPRINT identity fields，不重新计算指纹；
- 不创建额外生产接口、DTO、SQL builder、类型注册表、Spring Bean、配置项、重载或公开测试钩子。

项目所有者已批准任务卡文件范围之外只修改 `data-plane/tensor-core/pom.xml`，且只增加无显式版本的 `com.mysql:mysql-connector-j` `test` scope 依赖；版本继续由现有 Spring Boot `3.5.16` BOM 管理为 `9.7.0`。该扩展是永久 MySQL 集成测试的必要条件：`tensor-core` 已有 Testcontainers MySQL 依赖，但修改前 `dependency:tree -Dscope=test` 已证明没有 MySQL JDBC driver。

## Approach

### 公开表面与失败边界

在 `com.akkc.tensor.core.persistence` 中冻结以下唯一公开合同，不增加其他 public/protected 构造器、字段或方法：

```java
public final class DatasetLockManager {
    public DatasetLockManager();
    public Lock acquire(DatasetKey datasetKey);
}

public final class ExistingKeyRepository {
    public ExistingKeyRepository(JdbcTemplate jdbcTemplate);
    public Set<BusinessKey> findExisting(
            DatasetDefinition definition,
            List<BusinessKey> keys);
}

public record WriteCounts(long insertedRows, long updatedRows) {
    public WriteCounts {
        // Validation contract specified below.
    }
    public static WriteCounts from(
            List<BusinessKey> keys,
            Set<BusinessKey> existingKeys);
}
```

所有引用参数均用 `Objects.requireNonNull` 拒绝 null，参数名分别为 `datasetKey`、`jdbcTemplate`、`definition`、`keys` 和 `existingKeys`。`keys` 或 `existingKeys` 含 null 时固定抛 `IllegalArgumentException("business keys must not contain null")`。`findExisting` 对空 `keys` 返回不可修改空集合且不访问数据库；其他输入在首次 JDBC 调用前完成全部键宽校验。任一键的 `values().size()` 不等于物理键宽时固定抛 `IllegalArgumentException("Business key width does not match dataset")`，不回显表、列或键值。

`ExistingKeyRepository` 不捕获 `JdbcTemplate` 的 `DataAccessException`；SQL、连接和结果读取失败按 Spring JDBC 原异常边界传播，不记录 SQL 参数或业务键。结果中出现 null 物理键表示已验证 schema 漂移，固定抛 `IllegalStateException("Existing business key contains null")`。

`WriteCounts` canonical constructor 拒绝任一负数，固定抛 `IllegalArgumentException("write counts must be non-negative")`。`from` 先按 `BusinessKey` 结构相等语义去重输入；若 `existingKeys` 不是不同输入键集合的子集，固定抛 `IllegalArgumentException("existingKeys must be a subset of keys")`。随后 `updatedRows = existingKeys.size()`、`insertedRows = distinctKeys.size() - updatedRows`，使用 `Math.addExact` 复核两者之和等于不同键数；内部不一致固定抛 `IllegalStateException("Write count invariant violated")`。重复输入不增加任一计数，返回值不保存或暴露输入集合。

### 数据集锁、释放句柄与清理

`DatasetLockManager` 内部只维护 `ConcurrentHashMap<DatasetKey, LockEntry>`。每个 `LockEntry` 恰有一个 `ReentrantLock(true)` 和一个在 map 原子 `compute` 内增减的引用数；引用同时覆盖当前持有者和已经登记、可能正在等待的 `acquire` 调用。

`acquire` 先在 `compute` 中创建/复用 entry 并增加引用，再调用其不可中断 `lock()`；成功后返回一个已经持锁的一次性私有 `Lock` 句柄。相同线程再次对同一 `DatasetKey` 调用 `acquire` 会复用底层可重入锁并得到第二个句柄；不同数据集使用不同 entry，不互相阻塞。公平性只约束已经排队的竞争线程，不改变同线程重入语义。

调用句柄 `unlock()` 恰释放对应的一次 acquisition；只有底层 `unlock` 成功后才标记句柄已释放并在 map `computeIfPresent` 中减少引用。当引用归零时，仅在 entry 身份仍匹配时删除映射，因此释放与新 acquire 竞争不会创建两个仍有参与者的同键锁。错误线程释放沿用 `ReentrantLock` 的 `IllegalMonitorStateException` 且不减引用；同一句柄二次释放固定抛 `IllegalStateException("Lock handle already released")`。句柄已经由 `acquire` 持锁，所以其 `lock`、`lockInterruptibly`、两种 `tryLock` 和 `newCondition` 均固定抛 `UnsupportedOperationException("Lock handle is already acquired")`；调用方唯一正常操作是在 `finally` 中调用一次 `unlock()`。

本任务只保证单应用实例内互斥。M06-T04 必须在事务外先 acquire，在提交或回滚完成后的 `finally` 才 unlock；本任务不提前实现该编排。

### 物理键、SQL 与分块

每次 `findExisting` 只从定义构造一个有序物理键描述：

- COMPOSITE：逐项使用 `definition.businessKey().fields()` 原序，并在 `definition.columns()` 中取得同名 `ColumnDefinition`；
- FINGERPRINT：忽略 identity fields，只使用固定内部列 `business_key`，宽度为 1，JDBC 类型为 `Types.CHAR`。

表名和每个物理键列均在生成 SQL 时通过现有 `SqlIdentifierPolicy.quote` 再次校验和反引号引用。输入列表先复制并验证，再放入 `LinkedHashSet` 按首次出现顺序去重；查询分块大小固定为 `1000 / keyWidth` 的整数除法，因此任何 SQL 的占位符数都不超过 1000。结果累积进 `LinkedHashSet`，最终返回其不可修改快照；集合不承诺数据库行顺序。

宽度为 1 时，每块 SQL 精确为单行、无末尾分号：

```text
SELECT <quoted-key> FROM <quoted-table> WHERE <quoted-key> IN (<"?" joined by ", ">)
```

宽度大于 1 时，每块 SQL 精确为：

```text
SELECT <quoted-keys joined by ", "> FROM <quoted-table> WHERE (<quoted-keys joined by ", ">) IN (<one parenthesized "?" tuple per key joined by ", ">)
```

例如二列两键查询为：

```sql
SELECT `ts_code`, `trade_date` FROM `m06__composite` WHERE (`ts_code`, `trade_date`) IN ((?, ?), (?, ?))
```

不得增加 ORDER BY、COUNT、锁提示、客户端 SQL、空 `IN ()` 或值字面量。单列 1001 个不同键必须拆为 1000 + 1；二列 501 个不同键必须拆为 500 + 1。每块按“键首次出现顺序 → 每键物理列原序”形成绑定序列，从参数 1 开始逐项调用现有 `JdbcValueBinder.bind`。

### JDBC 类型与结果映射

COMPOSITE 业务键列复用 `DatasetStartupValidator` 已冻结的唯一逻辑类型映射，不建立第二套可配置类型表：

| Logical type | JDBC type | 绑定/读取 Java 值 |
|---|---:|---|
| `STRING` | `Types.VARCHAR` | `String` |
| `TEXT` | `Types.LONGVARCHAR` | `String` |
| `DATE` | `Types.DATE` | `LocalDate`，由 `java.sql.Date.toLocalDate()` 读取 |
| `MONTH`, `ENUM` | `Types.CHAR` | `String` |
| `LONG` | `Types.BIGINT` | `Long`，由 `getLong` 加 `wasNull` 读取 |
| `DECIMAL` | `Types.DECIMAL` | `BigDecimal` |

FINGERPRINT 的 `business_key` 使用 `Types.CHAR` 和 `String`。绑定继续由 M06-T02 `JdbcValueBinder` 的明确 setter 完成，不使用 `setObject`。RowMapper 只按从 1 开始的选择列位置和上述类型调用 `getString`、`getDate`、`getLong` 或 `getBigDecimal`，按同一原序构造 `BusinessKey`；不按列标签、ResultSet metadata 或实际 schema 反向推断类型。

### 直接输入与约束比较

- M06-T01 的 `SqlIdentifierPolicy` 是表名和键列进入预查 SQL 的唯一白名单/引用边界；`UpsertSqlFactory` 已冻结 COMPOSITE 物理键为定义 fields、FINGERPRINT 物理键为 `business_key`。实现提交 `029b344` 已通过聚焦 6/6 和 reactor 138/138 精确 SQL 门禁，当前文件未被本任务修改。
- M06-T02 的 `BusinessKey` 提供不可变有序结构相等，`BusinessKeyExtractor` 保证 COMPOSITE 原序和 FINGERPRINT 单元素物理键，`JdbcValueBinder` 提供明确 setter 与 typed-null 边界；实现提交 `2bd8996` 已通过聚焦 8/8、reactor `test`/`verify` 146/146 和无发现独立审查。
- `DatasetStartupValidator` 冻结同一逻辑类型到 JDBC type 的映射，并已验证实际 MySQL 主键：COMPOSITE 按 fields 原序，FINGERPRINT 只用 `business_key`。

这些输入互补且无冲突：M06-T01 决定安全标识符与物理键列，M06-T02 决定键值的有序结构和绑定，启动校验决定类型/物理 schema；本任务只按三者生成参数化 SELECT、映射结果集合并计算计数，不修改任何输入合同。TRD 10.2～10.4 要求预查与未来 Upsert 同事务、公平 JVM 数据集锁和基于预查的计数，也与本设计一致。

## Files

- Modify `data-plane/tensor-core/pom.xml`：只增加无显式版本的 `com.mysql:mysql-connector-j` `test` scope 依赖；不调整既有依赖或构建插件。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/DatasetLockManager.java`：实现公平、可重入、按数据集隔离且引用安全清理的锁与释放句柄。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/ExistingKeyRepository.java`：实现物理键描述、1000 参数分块、参数化查询、明确绑定和有序结果映射。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/WriteCounts.java`：实现非负计数、集合成员计算、子集校验和总和不变量。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/ExistingKeyRepositoryIT.java`：固定 MySQL 8.4.6 的 8 项查询、计数、边界、分块与锁合同。

不修改或删除其他文件。实现提交只暂存上述精确五个文件，固定消息为 `feat(core): preflight dataset keys and write counts`；设计、交接、看板、其他 Java、YAML、SQL、临时文件和生成的 `target` 不得混入实现提交。

## Tests

### 基线、依赖证据与可归因 RED

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-core/pom.xml dependency:tree \
  -Dscope=test -Dincludes=com.mysql:mysql-connector-j
```

第一条预期 `tensor-plugin-api` 79 项、当前 `tensor-core` 67 项，共 146/146，0 failure、0 error、0 skipped，父项目/plugin-api/core 三层 Enforcer 通过。第二条修改前不显示 MySQL driver；这正是批准 POM 扩展的依赖证据。

随后只修改批准的 POM 并完整创建 `ExistingKeyRepositoryIT.java`，不创建三个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=ExistingKeyRepositoryIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `DatasetLockManager`、`ExistingKeyRepository` 和 `WriteCounts` 不存在而在 `tensor-core:testCompile` 非零；不得因依赖解析、上游未匹配测试、测试语法、Docker 或既有失败形成伪 RED。`-am` 引入的 plugin-api 没有该测试类，所以必须保留 `surefire.failIfNoSpecifiedTests=false`。

### 固定 8 项 MySQL/并发 GREEN

创建最小生产实现后重跑同一定向命令。`ExistingKeyRepositoryIT` 固定使用静态 `MySQLContainer` 与官方 `DockerImageName.parse("mysql:8.4.6")`，不启动 Spring context，不在 Docker 不可用时 skip；通过 `DriverManagerDataSource` 和 `JdbcTemplate` 建立三个测试表并在每项前清空。预期恰有 8 个普通 `@Test`，8/8 通过：

1. 反射确认三个生产类型的 final/record、唯一公开表面和 `ExistingKeyRepository` 的单一 `JdbcTemplate` 构造；覆盖全部 null、空集合不访问缺失表、键宽不符、null 元素、负计数、已有集合非子集、一次性锁句柄禁用操作和 SQL 失败 `DataAccessException` 边界；
2. 单列 COMPOSITE 表无任何匹配时返回不可修改空集合；
3. FINGERPRINT 表所有请求键均已存在时，只按 `business_key` 返回全部单元素 `BusinessKey`；
4. 单列 COMPOSITE 表混合已有/不存在且输入含重复键时，预查只返回已有不同键，`WriteCounts.from` 得到精确 inserted/updated，重复不计数且两者之和等于不同输入键数；
5. 二列 COMPOSITE 表以 `(String, LocalDate)` 原序覆盖无、全、混合 tuple，证明 row-constructor 不产生列交叉匹配且结果键顺序/类型正确；
6. 单列 1001 个不同请求键在 1000 + 1 边界两侧各有匹配，结果精确包含两者且无值进入 SQL；
7. 二列 501 个不同请求键在 500 + 1 边界两侧各有匹配，结果精确包含两者，证明每条查询不超过 1000 个绑定参数；
8. 主线程持有同一数据集锁时按受控先后让两个线程进入等待，释放后严格按排队顺序获得；同时验证另一数据集不受阻塞、同线程嵌套 acquire 可重入、错误线程/二次 unlock 边界，以及所有句柄释放后私有 `locks` map 为空。

测试期望均手工构造；只使用 JUnit 5、AssertJ、Spring JDBC、真实 plugin-api 定义、固定 MySQL 和 JDK 并发原语。锁顺序测试用 latch 逐一启动竞争者，并在有界等待内确认前一线程已进入等待再启动后一线程；任一超时均失败，不用无界 sleep。不得使用 Mockito 生成数据库结果，不记录键值、凭证或完整 SQL 参数。

### 标准回归、依赖、静态与范围门禁

非定向命令仍不会发现 `*IT`；定向 8/8 是本任务永久 MySQL 门禁。运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -f data-plane/tensor-core/pom.xml dependency:tree \
  -Dscope=test -Dincludes=com.mysql:mysql-connector-j
```

前两条均预期 plugin-api 79 项、core 67 项，共 146/146，0 failure、0 error、0 skipped，三层 Enforcer 通过；不得修改 Surefire/Failsafe 来改变计数。依赖树必须只显示 direct `com.mysql:mysql-connector-j:jar:9.7.0:test`，版本来自 Spring Boot BOM，POM 中无 `<version>`。

运行：

```bash
rg -n 'SELECT \*|createStatement|Statement[^;]*=|setObject|String\.format|formatted\(|(?i:token|credential)|tushare|RestClient|ServiceLoader' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/DatasetLockManager.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/ExistingKeyRepository.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/WriteCounts.java
rg -n 'new ReentrantLock\(true\)|MAX_BIND_PARAMETERS = 1000|SqlIdentifierPolicy|JdbcValueBinder' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/DatasetLockManager.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/ExistingKeyRepository.java
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare \
  data-plane/tensor-plugin-fixture \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java
git status --short --untracked-files=all -- \
  data-plane/tensor-core/pom.xml \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence
git diff --check
```

第一项扫描预期无输出并退出 1；第二项必须只显示设计要求的公平锁、1000 上限和现有安全/绑定协作者引用。`clean` 退出 0；非目标模块与既有 M05/M06 文件无差异；提交前 scoped status 精确显示 POM 修改和本任务四个新增 Java 文件且不列 `target`；格式检查退出 0。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确五文件范围，工作树干净。

## Acceptance

- 三个生产类型的公开表面、锁句柄、失败消息、不可修改结果和非负计数合同与设计精确一致，没有额外生产抽象、重载或配置；
- 同一 `DatasetKey` 使用公平可重入 JVM 锁，不同数据集互不阻塞；持有者/等待者引用在竞争下安全，最后释放后映射清理，M06-T04 可把解锁放在事务完成后的 `finally`；
- 单物理键和多物理键分别使用批准的 scalar IN 与 MySQL row-constructor IN，所有表/列经 `SqlIdentifierPolicy`，所有键值经 `JdbcValueBinder`，SQL 无值插值、`SELECT *` 或空 IN；
- 每条查询最多 1000 个绑定参数；1001 个单列键和 501 个二列键均跨精确分块边界返回正确集合，空/重复/宽度不符和 SQL 失败遵守冻结边界；
- COMPOSITE 按定义 fields 原序和冻结 JDBC 类型绑定/读取，FINGERPRINT 只使用 `business_key`；无已有、全已有、混合和多列 tuple 场景均在固定 MySQL 8.4.6 通过；
- `WriteCounts.from` 只按不同输入键与已有集合成员关系产生 `long` inserted/updated，拒绝非子集，显式保证总和等于不同输入键数，不使用 affected-row；
- POM 只增加 BOM 管理的 MySQL connector `test` 依赖；严格 TDD 得到缺三个生产类型的可归因 RED 后，定向 MySQL/并发测试 8/8、标准 reactor `test`/`verify` 146/146、三层 Enforcer、依赖、静态、范围、格式、清理和精确五文件提交门禁全部得到预期结果；
- 未实现或修改 Upsert、事务、服务编排、提交/回滚、查询 API、现有生产合同或其他任务职责。

## Risks

- 公平 `ReentrantLock` 和引用清理只保证单 JVM；TRD 首期明确为单应用实例。扩为多实例前必须另行设计数据库锁或暂存表合并，不能复用本实现宣称跨实例准确计数。
- MySQL row-constructor `IN` 是已批准的 MySQL 8.4 专用策略；切换数据库必须重新设计查询方言和实际数据库合同，本任务不增加抽象层。
- 每查询 1000 个绑定参数是项目保护上限，不是 MySQL 自动发现值；未来若基于观测调整，必须同步修改常量、两项分块边界测试和设计。
- `ExistingKeyRepositoryIT` 的 `*IT` 名称不会被当前 Surefire 默认发现；显式 `-Dtest` 是任务门禁，标准 `test`/`verify` 保持 146/146。Docker daemon 或固定官方 `mysql:8.4.6` 不可用时必须报告环境阻塞，不能 skip、替换 H2 或改用浮动标签。
- 预查只有与 M06-T04 的 Upsert 同处一事务并让锁覆盖提交/回滚时才能保证准确计数；绕过未来服务编排单独调用 repository 只得到调用时数据库快照，本任务不声称事务外原子性。
