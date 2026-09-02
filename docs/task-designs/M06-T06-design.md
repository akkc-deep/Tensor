# M06-T06 DatasetQueryService、页码归一化和精度序列化——任务设计

任务编号：`M06-T06`
对应任务：[M06-T06](../superpowers/plans/tensor-modules/M06-core-persistence-query.md#task-m06-t06-查询服务与页码归一化30h)
实施产物：`DatasetPage`、`GenericQueryRepository`、`DatasetQueryService` 和 `DatasetQueryServiceIT`

## Goal

在 `tensor-core` 中完成只读查询执行闭环：按已验证 `DatasetCatalog` 查找数据集，使用 M06-T05 的参数化 SQL 先执行 COUNT，计算总页数并把超界请求归一到最后一页，再只读取规范页；空结果固定返回第 1 页并跳过行查询。查询结果保持元数据列顺序、完整 152 业务列宽表以及 `BigDecimal`、`Long`、`LocalDate`、`Instant` 等精确 Java 类型，使 M09 可以在 REST 边界把 DECIMAL/BIGINT 序列化为字符串而不发生精度损失。

## Scope

包含：

- 创建不可变 `DatasetPage`，保存业务列加三个来源列、深不可变有序行、规范页码/页大小、总行数和总页数，并拒绝不自洽页面；
- 创建 `GenericQueryRepository`，使用 `JdbcTemplate`、`PreparedStatement` 和 M06-T05 `QuerySql` 执行参数化 COUNT/分页查询，按 `DatasetDefinition` 显式读取每种逻辑类型；
- 创建 `DatasetQueryService`，执行目录查找、COUNT-first、空结果短路、总页数计算、超界页归一和规范页 SQL 重建；
- 在固定官方 `mysql:8.4.6` 上覆盖空表、无筛选分页、组合筛选、总数、超界页、COMPOSITE/FINGERPRINT 稳定排序、152 业务列宽表和精确类型；
- 使用八项 Testcontainers 集成测试执行严格 RED/GREEN、两项受控 mutation、reactor 回归、Enforcer、静态、范围、格式和清理门禁。

排除：

- 不创建 Controller、REST DTO、请求参数解析、默认 page/pageSize、错误包络、Jackson 配置或字符串序列化；这些属于 M09；
- 不修改 M06-T05 的 `QueryCriteria`、`QuerySql`、`QuerySqlFactory`，不新增筛选、排序、客户端标识符或 SQL DSL；
- 不修改 `DatasetCatalog`、plugin-api、YAML、Flyway、POM、事务/Upsert 代码、Surefire/Failsafe 生命周期或其他模块；
- 不引入 JPA、数据库 metadata、`SELECT *`、`getObject`、`setObject`、客户端 SQL 或查询值插值；
- 不建立长事务、快照隔离、缓存、导出、流式响应、性能报告、日志或指标；
- 不增加额外生产接口、异常类型、重载、builder、配置或文件。

## Approach

### 唯一公开合同与页面不变量

在 `com.akkc.tensor.core.query` 中冻结以下唯一生产表面：

```java
public record DatasetPage(
        List<String> columns,
        List<Map<String, Object>> items,
        int page,
        int pageSize,
        long totalElements,
        long totalPages) {}

public final class GenericQueryRepository {
    public GenericQueryRepository(JdbcTemplate jdbcTemplate);
    public long count(QuerySql querySql);
    public List<Map<String, Object>> query(DatasetDefinition definition, QuerySql querySql);
}

public final class DatasetQueryService {
    public DatasetQueryService(DatasetCatalog datasetCatalog, GenericQueryRepository repository);
    public DatasetPage query(DatasetKey key, QueryCriteria criteria);
}
```

不得增加其他 public/protected 构造器、字段或方法。`DatasetQueryService` 内部直接持有唯一 `QuerySqlFactory`；factory 是无状态纯对象，不新增依赖注入表面。`GenericQueryRepository` 可以提供包级静态列名 helper，供自身行映射和同包 service 复用，但该 helper 不得成为 public/protected API。

`DatasetPage` 对 `columns`、`items` 使用组件名执行 null 校验。列必须非空、无 null、无重复且保持输入顺序；每行必须非 null，键集合和迭代顺序必须逐项等于 `columns`。行使用 `Collections.unmodifiableMap(new LinkedHashMap<>(row))` 深复制，因此允许 nullable 业务值但不允许外部修改，items 再用 `List.copyOf` 保序复制。

页面数值不变量固定为：`page >= 1`；`pageSize` 只允许 20/50/100；`totalElements`、`totalPages` 非负；`totalPages` 必须等于 `totalElements == 0 ? 0 : 1 + (totalElements - 1) / pageSize`；空结果必须 `page=1` 且 items 为空；非空结果 `page <= totalPages`；items 数量不得超过 pageSize 或 totalElements。固定安全消息分别为 `columns must not be empty`、`columns must not contain duplicates`、`row keys must exactly match columns in order`、`page must be at least 1`、`pageSize must be one of 20, 50, 100`、`totals must be non-negative`、`totalPages must match totalElements and pageSize`、`empty pages must use page 1 and no items`、`page must not exceed totalPages`、`items must not exceed pageSize or totalElements`。

### 参数绑定与类型保真读取

`GenericQueryRepository.count` 只执行 `querySql.countSql()` 并按原序绑定 `countValues`；`query` 只执行 `querySql.pageSql()` 并按原序绑定 `pageValues`。两者先校验输入，Spring `DataAccessException` 原样传播，不包装、不记录 SQL、参数或行值。COUNT 必须恰取得一个非 null、非负 long；异常结构固定抛 `IllegalStateException("Count query returned an invalid result")`。

查询值类型只允许 M06-T05 能产生的 `String`、`LocalDate`、`Integer`、`Long`，分别调用 `setString`、`setDate`、`setInt`、`setLong`；null 或其他类型固定抛 `IllegalArgumentException("Unsupported query value type")`，不调用 `setObject`。所有 SQL 来自 `QuerySqlFactory`，repository 不接收客户端字段、operator 或 SQL 片段。

分页行按 SELECT 序号而不是数据库 metadata 读取。业务列严格遍历 `definition.columns()` 原序：

| `LogicalType` | JDBC 读取 | Java 值 |
|---|---|---|
| `STRING`、`TEXT`、`MONTH`、`ENUM` | `ResultSet.getString` | `String` 或 null |
| `DATE` | `ResultSet.getDate` | `LocalDate` 或 null |
| `LONG` | `ResultSet.getLong` + `wasNull` | `Long` 或 null |
| `DECIMAL` | `ResultSet.getBigDecimal` | `BigDecimal` 或 null |

随后按固定顺序读取 `source_plugin`、`source_api` 为 String，`ingested_at` 使用 UTC `Calendar` 的 `getTimestamp` 并转为 `Instant`。内部 `business_key` 不在 SELECT、columns 或 items 中。每行放入 `LinkedHashMap`，最终返回不可修改的保序列表；不把任何精确数值转为 `double`/`float`/JSON 字符串。

### COUNT-first、页码归一和查询顺序

`DatasetQueryService.query` 先校验 `key`、`criteria`，再从 `DatasetCatalog.find(key)` 取得已通过启动 metadata/schema 校验的 definition；不存在时固定抛 `IllegalArgumentException("Dataset is not available")`，且不访问数据库。

找到 definition 后按以下唯一顺序执行：

1. 用原始 criteria 调用 `QuerySqlFactory.create`；
2. repository 执行 COUNT；
3. 从 definition 形成业务列原序加 `source_plugin`、`source_api`、`ingested_at`；
4. 若 totalElements 为 0，立即返回 `DatasetPage(columns, [], 1, pageSize, 0, 0)`，不得执行 page SQL；
5. 使用 `1 + (totalElements - 1) / pageSize` 计算 long `totalPages`，避免加法溢出；规范页为 `(int) Math.min(criteria.page(), totalPages)`，该值不超过原 int page；
6. 若规范页不同于请求页，以原五个筛选值、规范页和原 pageSize 创建新的 `QueryCriteria`，重新调用 factory；禁止执行最初请求页的 page SQL 后再修正响应；
7. repository 只执行规范页 page SQL，返回 `DatasetPage`。

COUNT 和 page 查询不包在事务或快照中；本任务保证参数绑定和稳定唯一排序，不承诺并发写入期间的同一快照。M09 只把 `DatasetPage` 映射为带 requestId/pluginId/apiName 的 REST `PageResponse` 并执行精确值字符串序列化，不重新计算页码或总数。

### 直接依赖与约束比较

- M06-T05 的提交 `263513d` 提供已验证的 `QueryCriteria`、`QuerySql`、`QuerySqlFactory`：固定 filter 白名单、明确列、COUNT/分页占位符、绑定顺序及 COMPOSITE/FINGERPRINT 稳定排序；本任务只执行该合同，不复制 SQL 拼装或放宽值/标识符边界。
- M05-T02 的 `DatasetCatalog` 与 `DatasetStartupValidator` 保证 service 只取得元数据和实际 MySQL 表结构一致的 definition；M04-T06 已在 MySQL 8.4.6 证明 49 个生产表的业务列、来源列和物理唯一键存在。本任务测试通过同一 validator 构造 catalog，不绕过启动校验。
- 稳定路线图冻结 `DatasetQueryService.query(DatasetKey, QueryCriteria)` 和 `DatasetPage` 六组件；TRD 11.1～11.3、12.4 冻结 COUNT-first、超界归一、空结果、明确列、稳定排序和 DECIMAL/BIGINT 精度边界。

这些输入无冲突：M06-T05 负责生成安全 SQL，M06-T06 负责执行与页面语义，M09 才负责 HTTP 默认值、错误映射和字符串序列化。

## Files

- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/DatasetPage.java`：六组件深不可变页面及结构/数值不变量。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/GenericQueryRepository.java`：参数化 COUNT/分页执行、有序显式类型行映射和共享包级列 helper。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/DatasetQueryService.java`：目录查找、COUNT-first、空结果短路、超界归一和规范页执行。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/query/DatasetQueryServiceIT.java`：固定 MySQL 8.4.6 的八项结果级集成测试。

不修改或删除其他文件。实现提交只暂存上述四个新增 Java 文件，固定消息为 `feat(core): query datasets with stable server paging`；设计、交接、看板、POM、M06-T05 文件、其他生产代码、临时文件和 `target` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期 plugin-api 79 项、core 75 项，共 154/154，0 failure、0 error、0 skipped，父项目/plugin-api/core 三层 Enforcer 通过。在当前工作站 Mockito/Byte Buddy 测试需要允许 JVM attach 的执行环境；attach 权限失败不是产品 RED。

随后只完整创建 `DatasetQueryServiceIT.java`，不创建三个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=DatasetQueryServiceIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `DatasetPage`、`GenericQueryRepository`、`DatasetQueryService` 不存在而在 `tensor-core:testCompile` 非零；不得因测试语法、依赖、Docker、上游无匹配测试或环境配置形成伪 RED。

### 固定 MySQL 8.4.6 八项 GREEN

测试使用现有 Testcontainers/MySQL/JUnit/AssertJ 依赖、`@Testcontainers` 与唯一 `mysql:8.4.6` class container；不修改 POM，不使用 Mockito/H2/JPA。测试创建 COMPOSITE、FINGERPRINT 和由 helper 机械生成的 152 业务列宽表，全部带三个来源列，并用 `DatasetStartupValidator`/`SchemaInspector` 产生真实 `DatasetCatalog`。

创建最小生产实现后重跑同一定向命令，预期 `DatasetQueryServiceIT` 恰有八个普通 `@Test`，8/8 通过：

1. 反射确认 record/final、精确 components、三个类型的唯一公开表面；覆盖所有构造器/方法 null、页面深复制/不可修改、列/行/页/总数不变量，以及未知 DatasetKey 在任何 JDBC 访问前以固定消息失败；
2. 空表仍返回全部业务列加三个来源列、`page=1`、原 pageSize、零 totals 和空 items，并由记录 DataSource 证明只执行 COUNT、未准备或执行 page SQL；
3. COMPOSITE 表插入 25 行后，无筛选 page=2/pageSize=20 返回后 5 行、totalElements=25、totalPages=2，按复合业务键稳定升序；DECIMAL 为 `BigDecimal`、BIGINT 为 `Long`、DATE 为 `LocalDate`、来源时间为 `Instant`，精确大数无损；
4. `ts_code`、trade-date 闭区间和 ann-date 闭区间组合筛选只返回匹配行，COUNT 与 items 一致，绑定值不出现在记录 SQL 中且顺序与 M06-T05 相同；
5. 23 行请求 page=99/pageSize=20 被归一为 page=2、返回最后 3 行；记录绑定必须为 `[20, 20L]`，证明重建并执行规范页 SQL而非原请求 OFFSET 1960；
6. FINGERPRINT 表分页不返回内部 `business_key`，按全部身份字段原序加 `business_key` 得到确定顺序；columns/items 只含业务列和三个来源列；
7. 152 个业务列的宽表返回恰 155 个有序 columns/row keys，首尾业务列与三个来源列位置正确，nullable 值保留，DECIMAL 不转 double，证明不裁列且只读取当前页；
8. repository 的 COUNT/page SQL、连接、绑定或行读取失败按 Spring `DataAccessException` 原边界传播；非法 QuerySql value 在 JDBC 执行前以固定消息失败，COUNT 异常结构以固定 `IllegalStateException` 失败。

测试 helper 可以记录 PreparedStatement SQL、绑定和值及访问次数，但不得成为生产测试钩子；期望 SQL、列数、页码和值必须使用独立字面量/手工推导，不调用生产 factory 计算期望。

### Mutation、回归与静态门禁

受控 mutation A：临时跳过超界页 `Math.min` 归一或复用原始 page SQL，重跑第 5 项，预期 page/offset/rows 断言失败；恢复源码后该项通过。受控 mutation B：临时把 DECIMAL 的 `getBigDecimal` 改为 `getDouble`，重跑第 7 项，预期类型/精度断言失败；恢复源码后该项通过。mutation 不提交。

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

`DatasetQueryServiceIT` 按 Maven 默认命名不进入普通 Surefire 扫描，因此两条回归命令仍预期 plugin-api 79 项、core 75 项，共 154/154，0 failure、0 error、0 skipped，三层 Enforcer 通过且无新增警告类别；MySQL 结果级证据来自前述显式 `-Dtest=DatasetQueryServiceIT` 的 8/8。

运行静态、范围、格式和清理门禁：

```bash
rg -n 'SELECT \*|String\.format|formatted\(|setObject|getObject|createStatement|@Transactional|(?i:token|credential)|RestClient|ServiceLoader|doubleValue|floatValue' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query
rg -n 'JdbcTemplate|PreparedStatement|getBigDecimal|getLong|getDate|getTimestamp|setString|setDate|setInt|setLong|LinkedHashMap|QuerySqlFactory|Math\.min' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare \
  data-plane/tensor-plugin-fixture \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QueryCriteria.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QuerySql.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QuerySqlFactory.java \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/query/QuerySqlFactoryTest.java
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
```

第一项禁止扫描应无输出并以 1 退出；第二项显示显式 JDBC 绑定/读取、保序行、factory 和归一化；受保护路径与格式退出 0，clean 成功。clean 后 Git 状态只能列 Files 节四个新增 Java 文件。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确四文件范围，工作树干净。

## Acceptance

- `DatasetPage`、`GenericQueryRepository`、`DatasetQueryService` 的公开表面、null/深不可变性、页面不变量和固定安全消息与设计一致，无额外生产抽象；
- service 只查询 `DatasetCatalog` 中已验证 definition，严格执行 COUNT-first；空结果跳过 page SQL，超界请求重建并只执行规范页 SQL；
- repository 只执行 M06-T05 生成的参数化 SQL，条件和 LIMIT/OFFSET 全绑定，不使用客户端标识符、`SELECT *`、数据库 metadata 或通用对象绑定；
- columns 与 items 完整保持业务列原序加三个来源列，FINGERPRINT 不暴露内部键，COMPOSITE/FINGERPRINT 分页均稳定；
- DECIMAL、BIGINT、DATE、ingested_at 分别保留为 `BigDecimal`、`Long`、`LocalDate`、`Instant`，152 业务列宽表不裁列；字符串精确序列化明确留给 M09；
- 严格 TDD 得到缺三个生产类型的可归因 RED 后，固定 MySQL 8.4.6 定向 8/8、两项 mutation、reactor `test`/`verify` 154/154、三层 Enforcer、静态/范围/格式/清理和精确四文件提交门禁全部得到预期结果；
- 未修改 M06-T05、POM、catalog/plugin-api、YAML/迁移、持久化代码、其他模块或测试生命周期，未提前实现 REST/序列化/前端职责。

## Risks

- COUNT 与 page 查询不在同一快照中；并发写入可能让返回 items 与先前 total 短暂不完全一致。本任务不引入长事务，稳定排序和超界归一只针对 COUNT 时刻的结果；若未来要求快照一致性，必须另行设计隔离级别与性能影响。
- `ingested_at` 物理类型为 MySQL `DATETIME(3)`；repository 按项目 UTC 约定使用 UTC Calendar 读取为 `Instant`。部署连接必须继续固定 UTC，否则 M09 输出可能偏移。
- `DatasetQueryServiceIT` 的 `IT` 命名不进入默认 Surefire 扫描；显式定向 8/8 是不可替代的 MySQL 结果证据，普通 reactor 154/154 只证明无回归。
- 152 业务列测试使用机械生成的等宽定义与真实 MySQL 表，验证完整读取和顺序，不替代 M04 已完成的真实 `balancesheet` 列名/schema 合同；M14 仍负责生产宽表性能与页面验收。
