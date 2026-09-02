# M06-T04 单事务批量 Upsert 与回滚——任务设计

任务编号：`M06-T04`
对应任务：[M06-T04](../superpowers/plans/tensor-modules/M06-core-persistence-query.md#task-m06-t04-单事务批量-upsert40h)
实施产物：`GenericUpsertRepository`、`PersistenceService` 和 `PersistenceServiceIT`

## Goal

在 `tensor-core` 中交付元数据驱动的单事务批量 Upsert：`PersistenceService` 对已适配批次先完成目录一致性校验和业务键提取，再按 `DatasetKey` 获取 M06-T03 的公平 JVM 锁，在 Spring `REQUIRED` 事务内执行已有键预查、基于预查集合的准确计数和按元数据批大小执行的参数化 Upsert。事务实际提交或回滚后才释放锁；加入既有事务时锁必须保持到最外层事务 `afterCompletion`，从而让同数据集并发请求得到一致的插入/更新计数，任一中间批次 SQL 失败不留下部分写入。

## Scope

包含：

- 创建只负责安全批量写入的 `GenericUpsertRepository`，复用 `UpsertSqlFactory` 和 `JdbcValueBinder`，按 `DatasetDefinition.batchSize()` 拆分 JDBC batch；
- 创建 `PersistenceService`，公开任务卡固定的 `WriteCounts persist(AdaptedBatch batch)`，并通过构造器接收已验证 `DatasetCatalog`、M06-T03 锁/预查协作者、Upsert repository 和 `PlatformTransactionManager`；
- 在事务外验证批次与目录定义的 dataset、table、columns 和 business-key 合同完全一致；合法空批次返回零计数且不获取锁、不打开事务；
- 对非空批次执行“提取键 → 获取数据集锁 → `REQUIRED` 事务 → 注册事务完成解锁 → 预查 → 计数 → 分批 Upsert → 实际提交/回滚后解锁”；
- 固定新事务默认超时 60 秒；加入既有事务时沿用外层事务既有超时，并把锁持有到最外层完成；
- 使用固定 MySQL `8.4.6` 验证全插入、全更新、混合计数、中间批次失败回滚、同数据集并发、指纹幂等、统一 `ingested_at`、空批次和事务合同；
- 执行严格 RED/GREEN、显式 `*IT`、标准 reactor 回归、Enforcer、静态、范围、格式和清理门禁。

排除：

- 不实现下载、参数校验、上游调用、适配、REST/API 错误映射、查询、分页、指标、日志或 Spring Bean 配置；
- 不创建或修改 `DownloadService`，不把上游调用或适配放入数据库事务，不提前实现 M08/M09 职责；
- 不修改 POM、plugin-api、现有 M05/M06-T01/M06-T02/M06-T03 生产代码、Flyway SQL、YAML、Surefire/Failsafe 生命周期或其他模块；
- 不增加生产故障注入接口、事务注解、AOP、`REQUIRES_NEW`、数据库锁、分布式锁、暂存表、存储过程或自定义事务管理器；
- 不使用 MySQL affected-row 推断计数，不使用 `setObject`、字符串插入值、客户端 SQL、逐行 `update`、单行独立事务或批次间提交；
- 不重新计算 FINGERPRINT，不改变重复键、物理键、SQL 模板、JDBC 类型、锁或计数的既有合同；
- 不增加额外生产接口、DTO、异常类型、配置项、重载、公开测试钩子或文件。

## Approach

### 公开表面与协作者边界

在 `com.akkc.tensor.core.persistence` 中冻结以下唯一公开合同，不增加其他 public/protected 构造器、字段或方法：

```java
public final class GenericUpsertRepository {
    public GenericUpsertRepository(JdbcTemplate jdbcTemplate);
    public void upsert(DatasetDefinition definition, AdaptedBatch batch);
}

public final class PersistenceService {
    public PersistenceService(
            DatasetCatalog datasetCatalog,
            DatasetLockManager datasetLockManager,
            ExistingKeyRepository existingKeyRepository,
            GenericUpsertRepository genericUpsertRepository,
            PlatformTransactionManager transactionManager);
    public WriteCounts persist(AdaptedBatch batch);
}
```

所有构造器参数和方法引用参数均用 `Objects.requireNonNull` 拒绝 null，参数名精确为 `jdbcTemplate`、`definition`、`batch`、`datasetCatalog`、`datasetLockManager`、`existingKeyRepository`、`genericUpsertRepository` 和 `transactionManager`。两个生产类保持 final；`GenericUpsertRepository` 只保存 `JdbcTemplate`，内部按需创建无状态 `UpsertSqlFactory` 与 `JdbcValueBinder`；`PersistenceService` 只保存构造器协作者和一个配置好的 `TransactionTemplate`，内部按需创建无状态 `BusinessKeyExtractor`。

`GenericUpsertRepository` 可使用 package-private 的批次一致性校验辅助方法供 `PersistenceService` 在空批次短路前复用，但不暴露新的 public/protected 表面。repository 的 public `upsert` 仍自行调用同一校验，避免直接调用时绕过边界；空 rows 直接返回且不访问数据库。非空 rows 在首次 JDBC 调用前还必须确认 `TransactionSynchronizationManager.isActualTransactionActive()`；没有实际事务时固定抛 `IllegalStateException("Upsert requires an active transaction")`，从而禁止绕过 service 形成自动提交或跨 batch 部分写入。

### 目录准入、空批次与键提取

`PersistenceService.persist` 先以 `batch.datasetKey()` 查询已验证 `DatasetCatalog`。目录中不存在时固定抛 `IllegalArgumentException("Dataset is not available")`，不获取锁、不打开事务且不访问数据库。找到定义后，批次必须同时满足：

- `batch.datasetKey()` 等于 `definition.datasetKey()`；
- `batch.tableName()` 等于 `definition.tableName()`；
- `batch.businessKeyDefinition()` 等于 `definition.businessKey()`；
- `batch.columns()` 精确等于 `definition.columns()` 名称原序；FINGERPRINT 模式仅在该序列末尾再追加固定 `business_key`。

任一不一致固定抛 `IllegalArgumentException("Adapted batch does not match dataset")`，消息不回显 dataset、table、column、键值或行值。`AdaptedBatch` 已保证 rows/columns 不可变、每行键集合等于 columns，因此本任务不重复复制或重新验证容器形状。

完成目录一致性校验后，空 `batch.rows()` 直接返回 `new WriteCounts(0, 0)`；不得获取数据集锁、调用 transaction manager、执行已有键查询或 Upsert。非空批次在获取锁和打开事务前，按 rows 原序用 `BusinessKeyExtractor.extract(definition, row)` 生成 keys；提取失败原样传播且零数据库交互。服务不重新去重 rows，计数继续由 `WriteCounts.from` 按结构相等的不同键语义计算。

### REQUIRED 事务、锁所有权与完成时释放

`PersistenceService` 构造时以传入 `PlatformTransactionManager` 创建一个 `TransactionTemplate`，显式设置 `TransactionDefinition.PROPAGATION_REQUIRED` 和 60 秒 timeout。每次非空 persist 先调用 `datasetLockManager.acquire(batch.datasetKey())`，再执行该 template；锁获取发生在新事务开始或加入既有事务之前。

事务 callback 必须在首次 JDBC 调用前确认 `TransactionSynchronizationManager.isSynchronizationActive()`，否则固定抛 `IllegalStateException("Transaction synchronization is not active")`。随后注册一个一次性 `TransactionSynchronization`，其 `afterCompletion(int status)` 对本次 acquisition 句柄调用一次 `unlock()`。只有注册成功后，锁句柄所有权才移交给事务同步：

- 新建事务时，callback 返回后由 transaction manager 提交；`afterCompletion` 在 `TransactionTemplate.execute` 返回前释放锁，服务随后返回计数；
- 加入既有 `REQUIRED` 事务时，persist 可把计数返回给外层事务内调用者，但锁继续由同步持有，直到最外层实际提交或回滚后释放；外层不得在提交前把该计数作为成功响应发布；
- callback 中预查、计数或 Upsert 抛出运行时异常时，Spring 回滚，`afterCompletion` 在回滚完成后释放锁，异常原样传播；
- transaction manager 在 callback 前启动失败、同步未激活或同步注册失败时，外层 `finally` 负责直接释放尚未移交的句柄；移交后外层不得再次 unlock。

该设计由项目所有者于 2026-09-03 明确批准。不得用 `finally` 无条件解锁，因为加入既有事务时那会在最外层提交前过早释放；不得改用 `REQUIRES_NEW` 或拒绝既有事务来规避同步生命周期。

事务 callback 内按固定顺序执行：

1. `existingKeyRepository.findExisting(definition, keys)`；
2. `WriteCounts.from(keys, existingKeys)`；
3. `genericUpsertRepository.upsert(definition, batch)`；
4. 返回 counts 给 `TransactionTemplate`。

预查和全部 JDBC batch 使用同一 Spring 线程绑定连接与同一物理事务。服务不读取、求和或解释 Upsert affected rows；只有事务成功路径返回基于预查集合的 counts。

### 参数化批量 Upsert 与 JDBC 类型

`GenericUpsertRepository.upsert` 只从已验证 `DatasetDefinition` 调用现有 `UpsertSqlFactory.create(definition)` 得到单行参数化模板，然后使用 Spring JDBC 的 collection/batch-size `batchUpdate` 重复执行同一 `PreparedStatement`。批大小精确取 `definition.batchSize()`，不得硬编码 500、按 rows 数覆盖、为每块创建事务或在块间提交。

每行按以下固定顺序从参数 1 开始调用现有 `JdbcValueBinder.bind`：

1. `definition.columns()` 原序的业务值；
2. FINGERPRINT 模式追加 row 中既有 `business_key`，类型 `Types.CHAR`；COMPOSITE 不追加；
3. `definition.datasetKey().pluginId().value()`，类型 `Types.VARCHAR`；
4. `definition.datasetKey().apiName().value()`，类型 `Types.VARCHAR`；
5. `batch.ingestedAt()`，类型 `Types.TIMESTAMP`。

业务列沿用启动校验已冻结的映射：`STRING -> VARCHAR`、`TEXT -> LONGVARCHAR`、`DATE -> DATE`、`MONTH|ENUM -> CHAR`、`LONG -> BIGINT`、`DECIMAL -> DECIMAL`。null 值携带对应 typed-null；其他值由 M06-T02 binder 的明确 setter 处理。不得从 runtime value、ResultSet metadata 或实际 schema 反向推断类型。`source_plugin`、`source_api` 和 `ingested_at` 对同一批次每行完全一致；FINGERPRINT 只消费适配器已生成的 64 位十六进制 `business_key`，不重复哈希。

Spring `batchUpdate` 返回的 `int[][]` 被明确忽略。SQL、连接、绑定、batch execute、commit 或 rollback 失败沿 Spring JDBC/transaction 原异常边界传播，不包装为新的生产异常，不记录 SQL 参数、业务键、行值或凭证；未来 M09 负责映射 `PERSISTENCE_FAILED`。

### 直接依赖与约束比较

- M06-T03 的 `DatasetLockManager` 提供公平、可重入、按 dataset 隔离且引用安全清理的一次性锁句柄；`ExistingKeyRepository` 在当前事务连接上执行受 1000 bind 上限保护的 scalar/row-constructor 预查；`WriteCounts` 只按不同输入键与已有集合计算并守卫总和不变量。实现提交 `65ad1d7` 与审查修复 `d49d6c2` 已通过固定 MySQL 8.4.6 8/8、reactor 146/146、两项受控 mutation 和无发现独立复审。
- M06-T03 已冻结的传递输入包括 M06-T01 `UpsertSqlFactory`/`SqlIdentifierPolicy` 和 M06-T02 `BusinessKeyExtractor`/`JdbcValueBinder`：前者决定物理键排除与安全 SQL，后者决定有序键值、FINGERPRINT 复用和明确 JDBC setter；本任务只组合这些既有合同。
- `DatasetCatalog` 只暴露通过定义关系与实际 MySQL schema 启动校验的定义；`AdaptedBatch` 保存不可变表名、列序、行、business-key 定义和批次统一 `ingestedAt`；`DatasetDefinition.batchSize()` 已限制在 1～500。

这些输入互补且无冲突：目录决定唯一获准定义，batch 提供已适配值，M06-T01/T02/T03 分别提供 SQL、绑定、键、锁、预查与计数；本任务只增加事务生命周期和批量执行。TRD 10.2～10.5 的短事务、原子回滚、REQUIRED/60 秒、默认/元数据批大小、预查计数和提交后解锁均由上述顺序满足。

## Files

- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java`：实现目录定义/批次一致性边界、安全模板复用、固定列序与类型绑定，以及按 `DatasetDefinition.batchSize()` 的 JDBC batch Upsert。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java`：实现目录定位、空批次短路、键提取、锁、REQUIRED/60 秒事务、事务同步解锁、预查、计数和 Upsert 编排。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`：固定 MySQL 8.4.6 的 8 项事务、批处理、计数、回滚、并发和幂等合同。

不修改或删除其他文件。实现提交只暂存上述精确三个新增文件，固定消息为 `feat(core): persist adapted batches atomically`；设计、实施计划、交接、看板、POM、其他 Java、YAML、SQL、临时文件和生成的 `target` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期 `tensor-plugin-api` 79 项、当前 `tensor-core` 67 项，共 146/146，0 failure、0 error、0 skipped，父项目/plugin-api/core 三层 Enforcer 通过。在本机受限执行环境中，Mockito/Byte Buddy 测试必须在允许 JVM attach 的执行环境运行；不得把 attach 权限失败误判为产品 RED。

随后只完整创建 `PersistenceServiceIT.java`，不创建两个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=PersistenceServiceIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `GenericUpsertRepository` 和 `PersistenceService` 不存在而在 `tensor-core:testCompile` 非零；不得因测试语法、依赖解析、上游未匹配测试、Docker、MySQL 或既有失败形成伪 RED。

### 固定 8 项 MySQL/事务 GREEN

创建最小生产实现后重跑同一定向命令。`PersistenceServiceIT` 固定使用静态 `MySQLContainer` 与 `DockerImageName.parse("mysql:8.4.6")`，不启动 Spring context，不在 Docker 不可用时 skip；用 `DriverManagerDataSource`、`JdbcTemplate`、`DataSourceTransactionManager` 和经真实 schema 校验得到的 `DatasetCatalog` 组装生产协作者。当前 Colima 工作站运行定向测试时附加：

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=PersistenceServiceIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期恰有 8 个普通 `@Test`，8/8 通过：

1. 反射确认两个生产类 final、唯一公开表面和构造器 null 边界；覆盖未知 dataset、columns/business-key 不一致的安全固定错误；`AdaptedBatch` 与 `DatasetDefinition` 构造合同已使同 dataset 的 table mismatch 不可构造，仍由生产一致性校验防御；合法空批次返回 `(0,0)`，记录型 transaction manager 与私有锁 map 证明未获取锁、未启动事务且未访问缺失表；直接对 repository 传入非空批次但没有实际事务时，在 JDBC 前固定失败；
2. COMPOSITE 全新三行、元数据 `batchSize=2` 时返回 inserted=3/updated=0；代理 DataSource 在仍委托真实 MySQL 的同时记录两次 `executeBatch` 大小精确为 2、1，存储值和技术来源正确；
3. COMPOSITE 全部已有时返回 inserted=0/updated=2，不新增行，所有非键业务值、source 和 `ingested_at` 更新；记录 transaction definition 为 `PROPAGATION_REQUIRED`、timeout=60；
4. COMPOSITE 新旧混合且请求含不同键时返回 inserted=1/updated=1，总和为 2，最终两行值正确且不使用 affected-row；
5. `batchSize=2` 的第二个 JDBC batch 通过测试专用 MySQL trigger 对哨兵行 `SIGNAL`；代理记录第一批 2 行已成功 execute、第二批 1 行已尝试并失败，调用抛 `DataAccessException`，事务后表为零行；移除 trigger 后同 dataset 下一次 persist 成功，证明整批回滚且锁已在回滚后释放；
6. 在外层 `TransactionTemplate` 中调用 persist 后、外层 callback 返回前启动同 dataset 第二线程；第二线程在有界等待内保持阻塞，外层实际提交后才继续，两个结果按串行顺序精确为一次 insert、一次 update且最终值来自第二次请求，证明加入既有 REQUIRED 事务时锁由 `afterCompletion` 释放；
7. FINGERPRINT 相同行的两次非空 persist 第一次返回 insert、第二次返回 update，表中始终只有一个 `business_key`，非键业务值和技术列按第二批更新但不重新计算指纹；
8. 三行跨 2+1 两个 JDBC batch 仍全部保存 `batch.ingestedAt()` 的同一毫秒 UTC 时刻；任一行或任一 batch 不得自行取当前时间。

测试只使用 JUnit 5、AssertJ、Spring JDBC/Transactions、真实 plugin-api 定义、固定 MySQL、JDK proxy/latch/future 和既有 core 类型；不用 Mockito、H2、生产故障钩子或无界 sleep。并发测试用 latch 与有界 future 验证阻塞/完成；超时即失败。中间批次失败只由测试创建/删除的 MySQL trigger 触发，生产代码无注入分支。代理 DataSource 必须委托真实连接/statement，只记录 batch 边界，不伪造数据库结果。

### 标准回归、静态与范围门禁

`PersistenceServiceIT` 的 `*IT` 名称不会被当前 Surefire 默认发现；定向 8/8 是本任务永久 MySQL 门禁。运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期 plugin-api 79 项、core 67 项，共 146/146，0 failure、0 error、0 skipped，三层 Enforcer 通过；不得修改 Surefire/Failsafe 改变计数。

运行：

```bash
rg -n '@Transactional|PROPAGATION_REQUIRES_NEW|setObject|createStatement|SELECT \*|String\.format|formatted\(|(?i:token|credential)|tushare|RestClient|ServiceLoader' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java
rg -n 'TransactionTemplate|PROPAGATION_REQUIRED|setTimeout\(60\)|isActualTransactionActive|registerSynchronization|afterCompletion|batchSize\(\)|UpsertSqlFactory|JdbcValueBinder' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare \
  data-plane/tensor-plugin-fixture \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/DatasetLockManager.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/ExistingKeyRepository.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/WriteCounts.java
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence
git diff --check
```

第一项扫描预期无输出并退出 1；第二项必须显示程序化 REQUIRED/60 秒事务、事务同步完成解锁、元数据批大小、既有 SQL factory 和 binder。`clean` 退出 0；受保护路径无差异；实现提交前 scoped status 精确显示本任务三个新增 Java 文件且不列 `target`；格式检查退出 0。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确三文件范围，工作树干净。

为证明关键测试不是只验证结果而遗漏机制，审查前执行两项受控 mutation 并在每次失败后恢复生产实现：删除事务同步、改为 `persist` 返回时直接 unlock，外层事务并发测试必须超时失败；忽略 `definition.batchSize()`、把全部 rows 放入一个 JDBC batch，2+1 边界测试必须观测单次 3 行并失败。恢复后定向 8/8 必须再次通过。

## Acceptance

- 两个生产类的公开表面、构造器依赖、null/目录/批次错误和空批次合同与设计精确一致，没有额外生产抽象、异常、重载、配置或测试钩子；
- 空批次不获取锁、不打开事务且零数据库访问；非空批次的键提取在事务外，只有预查和全部 Upsert 位于同一 REQUIRED 事务；
- 新事务使用 60 秒 timeout；加入既有事务时不新开事务，数据集公平锁保持到最外层实际提交/回滚后的 `afterCompletion`，启动/同步注册失败与正常/异常完成均恰释放一次；
- `GenericUpsertRepository` 只消费已验证定义和匹配批次，非空写入拒绝无实际事务的直接调用；它复用安全 SQL factory 与明确 binder，按定义列序、物理 FINGERPRINT 列和三个技术列绑定，按 `DatasetDefinition.batchSize()` 形成真实 JDBC batch；
- 全插入、全更新、混合和同数据集并发的 inserted/updated 只来自事务内预查集合，总和等于不同业务键数，不读取 MySQL affected-row；
- 第二 JDBC batch 的确定 SQL 失败使第一批和当前批全部回滚，失败后同 dataset 可再次持久化；FINGERPRINT 重复写保持单行幂等，同批所有行跨 batch 使用完全相同的 `ingested_at`；
- 严格 TDD 得到缺两个生产类型的可归因 RED 后，固定 MySQL 8.4.6 定向测试 8/8、两项受控 mutation 按预期失败并恢复、标准 reactor `test`/`verify` 146/146、三层 Enforcer、静态、范围、格式、清理和精确三文件提交门禁全部得到预期结果；
- 未修改 POM、plugin-api、现有生产合同、Flyway/YAML、其他模块或测试生命周期，未实现下载/API/查询、生产故障注入、声明式事务或跨实例锁。

## Risks

- 数据集锁仍只保证单 JVM；首期 TRD 明确为单应用实例。扩为多实例前必须另行设计数据库锁或暂存表合并，不能把本服务宣称为跨实例准确计数。
- 加入既有 REQUIRED 事务时，persist 返回的 counts 只有外层事务最终提交后才代表已提交结果；外层回滚会丢弃该写入，调用方不得在事务完成前发布成功。未来下载/API 编排必须保持该边界。
- `ReentrantLock` 句柄必须由获取线程释放；本设计依赖 Spring 同步事务在同一线程触发 `afterCompletion`。不得把事务完成转移到异步线程或响应式 transaction manager；此类运行模型需要重新设计锁所有权。
- 60 秒 timeout 只在本服务新建事务时生效；加入既有事务时 Spring 沿用外层定义，不能由内层延长或覆盖。外层若改变 timeout，必须仍满足项目验收规模和优雅停机边界。
- MySQL trigger 仅是测试中制造第二 batch 确定失败的手段，必须在每项测试隔离创建并清理；生产 migrations 和实现不得包含 trigger 或哨兵分支。
- `PersistenceServiceIT` 的 `*IT` 名称不会被当前 Surefire 默认发现；显式 `-Dtest` 是任务门禁，标准 `test`/`verify` 保持 146/146。Docker daemon 或固定官方 `mysql:8.4.6` 不可用时必须报告环境阻塞，不能 skip、替换 H2 或改用浮动标签。
