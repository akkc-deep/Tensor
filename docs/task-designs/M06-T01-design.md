# M06-T01 白名单 SQL 标识符和 Upsert 模板——任务设计

任务编号：`M06-T01`
对应任务：[M06-T01](../superpowers/plans/tensor-modules/M06-core-persistence-query.md#task-m06-t01-sql-标识符与-upsert-模板30h)
实施产物：`SqlIdentifierPolicy`、`UpsertSqlFactory` 和 `UpsertSqlFactoryTest`

## Goal

在 `tensor-core` 中交付唯一的白名单 Upsert SQL 模板边界：只从已经通过启动校验的 `DatasetDefinition` 派生表名、插入列、物理键和更新列，把所有标识符再次按冻结正则校验并统一反引号引用，生成列顺序稳定、值位置全部使用 `?` 的 MySQL 8.4 `INSERT ... ON DUPLICATE KEY UPDATE` 语句。后继 JDBC 仓储只绑定值，不再拼接表名、列名或更新列表。

## Scope

包含：

- 创建可复用的 `SqlIdentifierPolicy`，只接受 `^[a-z][a-z0-9_]{1,63}$`，并为每个合法标识符增加 MySQL 反引号；
- 创建无状态 `UpsertSqlFactory`，公开 `create(DatasetDefinition)`，按定义业务列、可选内部 `business_key`、三个来源技术列的固定顺序生成单条参数化 SQL；
- 为 COMPOSITE 数据集排除定义中的物理业务键列，只更新其余业务列和三个来源字段；
- 为 FINGERPRINT 数据集把内部 `business_key` 作为唯一物理键并排除更新，更新全部定义业务列和三个来源字段；
- 固定使用 TRD 10.3 已发布的 `VALUES(<column>)` 更新表达式，不把任何行值、参数值或客户端输入放入 SQL 文本；
- 以严格 TDD 覆盖公开表面、标识符边界、`daily` 精确 SQL、保留字 `change`、两种键模式、占位符/顺序和输入不变性；
- 执行聚焦、模块回归、Enforcer、静态、范围、格式与清理门禁。

排除：

- 不修改 POM、plugin-api、现有 M05 Java、YAML、Flyway SQL 或其他模块；
- 不读取 YAML、数据库或 `information_schema`，不重新执行 M05-T02 的 schema 准入，也不从表结构反向生成元数据；
- 不实现 JDBC 绑定、`PreparedStatement`、批大小、已有键预查、锁、计数、事务、回滚、仓储或服务；
- 不实现查询 SQL、COUNT、筛选、排序、分页、REST 或前端；
- 不接受调用方提供的表名、列名、更新列、排序、SQL 片段或任意值字符串；
- 不创建额外生产类型、SQL DTO、builder、方言接口、缓存、Spring Bean、配置项或重载。

## Approach

### 公开表面与失败边界

在 `com.akkc.tensor.core.persistence` 中冻结以下唯一公开合同，不增加其他 public/protected 构造器、字段或方法：

```java
public final class SqlIdentifierPolicy {
    public SqlIdentifierPolicy();
    public String quote(String identifier);
}

public final class UpsertSqlFactory {
    public UpsertSqlFactory();
    public String create(DatasetDefinition definition);
}
```

两个类均无实例字段且保持无状态；默认构造器不执行 I/O。`quote` 用 `Objects.requireNonNull(identifier, "identifier")` 拒绝 null；不 trim、不改写大小写。非 null 但不满足精确正则的输入统一抛 `IllegalArgumentException("Invalid SQL identifier")`，不回显输入。合法标识符一律返回单段 `` `identifier` ``，包括 `change` 等保留字；不接受点号限定名，不执行反引号转义路径，因为白名单已经排除反引号和点号。

`create` 用 `Objects.requireNonNull(definition, "definition")` 拒绝 null。工厂内部使用同一 `SqlIdentifierPolicy` 规则重新校验表名、业务列、业务键引用和固定技术列；`DatasetDefinition` 的构造不变量或 M05-T02 的准入不能成为跳过 SQL 边界白名单的理由。任何标识符失败均在 SQL 字符串返回前结束。

### 插入列与物理键

插入列按以下顺序构建一次不可变列表：

1. `definition.columns()` 的原声明顺序；
2. 仅当 `definition.businessKey().mode() == FINGERPRINT` 时追加内部 `business_key`；
3. 固定追加 `source_plugin`、`source_api`、`ingested_at`。

工厂不得排序、去重、读取 display order 或自行增加其他列。每个插入列精确对应一个 `?`，占位符数量必须等于插入列数量；`create` 不接收、更不读取行 map，所以 SQL 文本不可能包含行值。

物理键与 M04-T06 已验证的 MySQL schema 精确一致：

- COMPOSITE：`definition.businessKey().fields()` 是物理主键列；
- FINGERPRINT：内部 `business_key` 是唯一物理主键，定义中的有序 identity fields 保持普通业务列及原可空性，不进入数据库主键。

### 更新列与 SQL 格式

更新列按插入列顺序过滤物理键后得到：

- COMPOSITE：所有不在 `definition.businessKey().fields()` 中的定义业务列，随后三个来源字段；
- FINGERPRINT：全部定义业务列，随后三个来源字段；内部 `business_key` 永不更新。

该差异实现 TRD 10.3 的 `all_non_key_business_columns`：FINGERPRINT 的摘要是数据库幂等键，原 identity fields 仍是非物理键业务列。三个来源字段始终更新，使重复下载保留本次插件/API 身份与统一 `ingestedAt`。即使 COMPOSITE 的全部业务列都是键，三个来源更新也保证 `ON DUPLICATE KEY UPDATE` 列表非空。

返回 SQL 为单行、无末尾分号，空格和标点精确固定为：

```text
INSERT INTO <quoted-table> (<quoted-insert-columns joined by ", ">) VALUES (<"?" joined by ", ">) ON DUPLICATE KEY UPDATE <each quoted-update-column + " = VALUES(" + quoted-update-column + ")" joined by ", ">
```

`daily` 的精确期望为：

```sql
INSERT INTO `tushare_pro__daily` (`ts_code`, `trade_date`, `open`, `high`, `low`, `close`, `pre_close`, `change`, `pct_chg`, `vol`, `amount`, `source_plugin`, `source_api`, `ingested_at`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `open` = VALUES(`open`), `high` = VALUES(`high`), `low` = VALUES(`low`), `close` = VALUES(`close`), `pre_close` = VALUES(`pre_close`), `change` = VALUES(`change`), `pct_chg` = VALUES(`pct_chg`), `vol` = VALUES(`vol`), `amount` = VALUES(`amount`), `source_plugin` = VALUES(`source_plugin`), `source_api` = VALUES(`source_api`), `ingested_at` = VALUES(`ingested_at`)
```

所有标识符都来自定义或固定技术字面量并经白名单引用；所有值位置都保持 `?`。本任务不改变 TRD 已冻结的 `VALUES(column)` MySQL 表达式，也不提前引入别名方言或版本开关。

### 直接输入与约束比较

- M02-T03 提供不可变且保序的 `DatasetDefinition`、`ColumnDefinition`、`BusinessKeyDefinition`、`BusinessKeyMode` 和 `TableName`；本任务只消费名称、列顺序与键模式，不修改公共契约或根据逻辑类型改变 SQL。
- M04-T06 提供 MySQL 8.4.6 实证 schema：49 张生产表均按业务列原序，两个 FINGERPRINT 表只额外使用 `business_key` 主键，所有表最后都有三个来源字段；本任务的插入列和物理键规则必须与该顺序、技术列和主键关系一致。

两项直接输入无冲突：M02 冻结逻辑元数据及顺序，M04 冻结其物理列、技术列和主键映射；TRD 10.3 冻结参数化 Upsert 结构。本任务只把三者机械组合为确定 SQL，不读取具体插件实现或数据库。

## Files

- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java`：实现固定标识符正则、固定安全错误和统一反引号引用。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java`：实现插入列、物理键、更新列、占位符和确定 SQL 生成。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/UpsertSqlFactoryTest.java`：以真实 plugin-api 定义覆盖公开表面、标识符、精确 SQL、键模式和安全边界。

不修改或删除其他文件。实现提交只暂存上述三个 Java 文件，固定消息为 `feat(core): generate validated upsert SQL`；设计、交接、看板、POM、既有 Java、YAML、SQL、临时文件和生成的 `target` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前在允许 Mockito/Byte Buddy 本地 JVM attach 的环境运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期 plugin-api 79 项、当前 core 53 项，共 132/132，0 failure、0 error、0 skipped；父项目、plugin-api、core 三层 Enforcer 通过。已有 platform-encoding、Mockito/JDK 动态 agent 和测试刻意触发的固定安全 WARNING 允许保留，不得新增其他构建警告类别。attach 受限沙箱的既有十项 `MockMaker` 初始化错误是环境失败，不能作为代码 RED 或回归结论。

随后只完整创建 `UpsertSqlFactoryTest.java`，不创建两个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=UpsertSqlFactoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `SqlIdentifierPolicy` 和 `UpsertSqlFactory` 不存在而在 `tensor-core:testCompile` 非零；不得因依赖解析、上游未匹配测试、测试语法、Mockito 或既有失败形成伪 RED。

### 聚焦 GREEN

创建最小生产实现后重跑同一聚焦命令。`UpsertSqlFactoryTest` 固定恰有 6 个普通 `@Test`，6/6 通过且不使用 Mockito、数据库或 Spring context：

1. 反射确认两个类 final、唯一 public 无参构造器和唯一声明 public 方法；null 参数分别遵守固定 `NullPointerException` 边界且两个类无实例字段；
2. policy 接受边界内小写蛇形标识符并统一引用 `change`，拒绝空、单字符、首字符数字、大写、点号、反引号、空格、65 字符和其他不匹配输入，均使用固定安全 `IllegalArgumentException`；
3. 手工构造 11 列 COMPOSITE `daily` 定义，逐字符断言上文精确 SQL、14 个 `?`、键列不在 update、`change` 全程引用且三个来源字段均更新；
4. 构造业务列全部属于 COMPOSITE 键的最小定义，断言 update 仅按固定顺序包含三个来源字段，SQL 仍合法且无末尾分号；
5. 构造 FINGERPRINT 定义，断言 insert 在业务列后追加 `business_key` 再追加来源字段，update 排除 `business_key`、包含全部定义业务列和三个来源字段；
6. 对同一定义重复创建得到相同字符串，定义的列/键列表保持不变；逐段断言 SQL 只包含引用标识符、固定关键字、逗号/括号/空格和预期数量的 `?`，不包含任何测试行值或参数值。

测试只使用 JUnit 5、AssertJ 和真实 `DatasetDefinition`/值对象；精确 SQL 与占位符数量手工书写，不调用生产 helper 生成期望。不得使用 Mockito、JDBC、数据库、网络、时钟、真实 YAML、Token 或凭证。

### 模块、静态与范围门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期 plugin-api 79 项、core 既有 53 项加新测试 6 项，共 138/138，0 failure、0 error、0 skipped；三层 Enforcer 通过。

运行：

```bash
rg -n 'org\.springframework|java\.sql|javax\.sql|JdbcTemplate|PreparedStatement|Statement|ResultSet|tushare|RestClient|ServiceLoader|(?i:token|credential)|String\.format|formatted\(' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java
rg -n 'SELECT|DELETE|DROP|ALTER|CREATE|TRUNCATE|--|/\*|\*/' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence
git diff --check
```

2026-09-02 项目所有者批准修正第二项静态门禁：源码扫描只检查危险 SQL 关键字与注释，不再把 Java 语法必需的裸 `;` 作为源码匹配项；生成 SQL 无末尾分号继续由上节第 4 项 `doesNotEndWith(";")` 和第 6 项禁止字符行为断言验证。两项源码扫描均预期无输出并退出 1，聚焦 6/6 必须同时成立；`clean` 退出 0；非目标 POM/app/plugin-api/plugin-tushare 无差异；提交前 scoped status 精确新增本任务三个 Java 文件且不列 `target`；格式检查退出 0。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确三文件范围，工作树干净。

## Acceptance

- 两个生产类的公开表面、无状态边界和固定安全错误与设计精确一致，没有额外生产类型、重载、框架或 JDBC 依赖；
- 只有满足冻结正则的单段标识符可进入 SQL，所有表名和列名均统一反引号引用，保留字 `change` 正确且非法输入不被回显；
- insert 列精确为定义业务列原序、可选 `business_key`、三个来源字段，每列恰有一个 `?` 且 SQL 不含调用方行值、参数值或客户端 SQL 片段；
- COMPOSITE 更新排除定义业务键，FINGERPRINT 更新排除内部 `business_key` 并包含全部定义业务列；两种模式都按固定顺序更新三个来源字段；
- `daily` 精确 SQL、仅键 COMPOSITE 和 FINGERPRINT 代表合同通过，输入定义及其集合不被修改，重复调用确定；
- 严格 TDD 得到缺两个生产类型的可归因 RED 后 6/6 GREEN；模块 `test`/`verify` 138/138、三层 Enforcer、危险 SQL 关键字/注释源码扫描与无末尾分号行为断言、范围、格式、清理和精确三文件提交门禁全部得到预期结果；
- 未提前实现绑定、预查、锁、计数、事务、仓储、查询、REST 或其他 M06 职责。

## Risks

- TRD 10.3 当前固定 `VALUES(column)`；MySQL 将其标记为弃用但 MySQL 8.4.6 仍支持。未来若改为行别名语法，必须作为 SQL 合同变更同时更新精确测试与后继仓储，不在本任务增加双方言。
- 工厂依赖调用方只传入 M05-T02 已准入定义；本类仍重验标识符，但不具备数据库连接，不能证明实际 schema。后继装配不得绕过 `DatasetCatalog`。
- FINGERPRINT 的 identity fields 是摘要输入但不是物理主键，因此按 TRD 的非键业务列规则参与 update；在正常 SHA-256 幂等路径中它们与摘要对应的原值一致，理论碰撞风险仍由 M05 设计中的冲突治理和后续版本化键迁移承担。
