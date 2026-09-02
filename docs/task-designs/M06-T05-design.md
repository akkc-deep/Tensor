# M06-T05 查询条件白名单和 COUNT/分页 SQL——任务设计

任务编号：`M06-T05`
对应任务：[M06-T05](../superpowers/plans/tensor-modules/M06-core-persistence-query.md#task-m06-t05-查询条件和分页-sql35h)
实施产物：`QueryCriteria`、`QuerySql`、`QuerySqlFactory` 和 `QuerySqlFactoryTest`

## Goal

在 `tensor-core` 中交付元数据驱动的只读查询 SQL 合同：把固定形状的可选证券代码、交易日期范围、公告日期范围和分页条件先做值级校验，再只允许目标 `DatasetDefinition.filters()` 明确声明的筛选，生成参数化 `COUNT(*)` 与明确列分页 SQL。表名、列名和稳定排序全部来自已验证的不可变元数据；COMPOSITE 数据集按业务键升序，FINGERPRINT 数据集按身份字段原序及内部 `business_key` 升序，保证 M06-T06 可以在计数后归一页码并执行稳定服务端分页。

## Scope

包含：

- 创建不可变 `QueryCriteria`，表达可选 `tsCode`、`tradeDateFrom/To`、`annDateFrom/To` 以及必填 `page`、`pageSize`，统一证券代码规范化、日期关系和分页值校验；
- 创建不可变 `QuerySql`，分别保存 COUNT SQL/绑定值与分页 SQL/绑定值，并保序复制两个值列表；
- 创建 `QuerySqlFactory`，校验元数据筛选白名单，生成无筛选、单条件、单边日期、闭区间日期及多条件 `AND` SQL；
- 明确选择业务列加 `source_plugin`、`source_api`、`ingested_at`，不选择内部 `business_key`，并生成 COMPOSITE/FINGERPRINT 的稳定排序；
- 使用八项纯单元测试执行严格 RED/GREEN、两项受控 mutation、reactor 回归、Enforcer、静态、范围、格式和清理门禁。

排除：

- 不访问数据库，不创建 repository/service/page DTO，不执行 COUNT、查询或页码归一化；这些属于 M06-T06；
- 不实现 REST 参数解析、默认 page/pageSize、HTTP/错误 DTO、精度序列化、日志或指标；这些属于 M09；
- 不修改 plugin-api 元数据、M04 迁移、YAML、POM、现有 persistence/catalog/validation 生产代码或 Surefire/Failsafe 生命周期；
- 不接受客户端表名、列名、排序字段、operator、SQL 片段或通用 filter map，不新增动态查询 DSL；
- 不使用 `SELECT *`、字符串插入查询值、`String.format`/`formatted`、客户端 SQL、数据库 metadata、JPA、Criteria API 或第三方 SQL builder；
- 不增加额外生产接口、异常类型、重载、builder、配置或文件。

## Approach

### 公开合同和值不变量

在 `com.akkc.tensor.core.query` 中冻结以下唯一生产表面：

```java
public record QueryCriteria(
        String tsCode,
        LocalDate tradeDateFrom,
        LocalDate tradeDateTo,
        LocalDate annDateFrom,
        LocalDate annDateTo,
        int page,
        int pageSize) {}

public record QuerySql(
        String countSql,
        List<Object> countValues,
        String pageSql,
        List<Object> pageValues) {}

public final class QuerySqlFactory {
    public QuerySqlFactory();
    public QuerySql create(DatasetDefinition definition, QueryCriteria criteria);
}
```

不得增加其他 public/protected 构造器、字段或方法。三个类型只依赖 Java 21、plugin-api 的 `DatasetDefinition`/相关元数据类型，以及现有 public `SqlIdentifierPolicy`；不引入 Spring/JDBC 类型。

`QueryCriteria` 的五个筛选值均可为 null。非 null `tsCode` 使用 `String.strip()` 去除首尾 Unicode 空白，再按 `Locale.ROOT` 转为大写，并必须完整匹配 `[A-Z0-9]+\.[A-Z0-9]+`；该规则与现有下载参数校验一致，可接受 `000001.SZ`、`X12125.SZ` 等“代码.市场”值，拒绝空白、缺点号、多段、空段、空格、连字符或 SQL 片段。构造后保存规范化值。

两个日期范围允许全部为空、只给 from、只给 to 或同时给定；同时给定时 from 不得晚于 to。`page` 必须至少为 1；`pageSize` 只允许 20、50、100。固定异常边界为：null 保持“未提供”，内容不合法抛 `IllegalArgumentException`，消息分别为 `tsCode has invalid format`、`tradeDateFrom must not be after tradeDateTo`、`annDateFrom must not be after annDateTo`、`page must be at least 1`、`pageSize must be one of 20, 50, 100`。本类型不提供默认构造器或默认值；M09 把缺省 HTTP 参数映射为 page=1/pageSize=50。

`QuerySql` 对四个组件使用 `Objects.requireNonNull`，参数名与组件名一致；两个值列表使用 `List.copyOf` 保序复制并保持不可修改。它不解释 SQL 或值，factory 是唯一项目内生产构造方。

### 元数据筛选白名单

`QuerySqlFactory.create` 先拒绝 null `definition`/`criteria`，再从 `definition.filters()` 形成不可修改的字段集合。查询层只支持已冻结的三种筛选字段：

| criteria | 元数据 field | SQL |
|---|---|---|
| `tsCode` | `ts_code` | `` `ts_code` = ? `` |
| `tradeDateFrom/To` | `trade_date` | 单边为 `>= ?`/`<= ?`，双边为 `BETWEEN ? AND ?` |
| `annDateFrom/To` | `ann_date` | 单边为 `>= ?`/`<= ?`，双边为 `BETWEEN ? AND ?` |

若 definition 声明了三者之外的 filter field，固定抛 `IllegalArgumentException("Unsupported dataset filter metadata")`，不尝试推断 operator。若 criteria 提供了某个值而 definition 没有声明对应 field，固定抛 `IllegalArgumentException("Filter is not supported by dataset")`。同一日期字段的任一边界都要求该字段被声明；合法无筛选 criteria 对 `filters: []` 数据集仍可生成全表分页 SQL。

条件与绑定值顺序固定为 `ts_code`、`trade_date`、`ann_date`，不取决于调用方、map 或 filter 元数据排列；日期字段内按 from、to 排序。多条件之间只用 `AND`。查询值只进入 `countValues`/`pageValues`，绝不拼入 SQL。

### COUNT、明确列和稳定分页

所有标识符均通过现有 `SqlIdentifierPolicy.quote`：表名来自 `definition.tableName().value()`；业务列来自 `definition.columns()` 原序；筛选列只来自上述固定映射；排序列来自业务键定义及固定内部列。即使 `DatasetDefinition` 已做标识校验，也不得复制正则或直接拼接裸标识符。

COUNT SQL 固定为：

```sql
SELECT COUNT(*) FROM `<table>`[ WHERE <conditions>]
```

分页 SELECT 列固定为 `definition.columns()` 原序，随后追加 `source_plugin`、`source_api`、`ingested_at`；FINGERPRINT 的内部 `business_key` 永不进入 SELECT。排序固定为：

- COMPOSITE：`definition.businessKey().fields()` 原序，每列显式 `ASC`；
- FINGERPRINT：身份字段同上，最后追加 `` `business_key` ASC `` 作为物理唯一决胜列。

分页 SQL 固定为：

```sql
SELECT `<business columns...>`, `source_plugin`, `source_api`, `ingested_at`
FROM `<table>`[ WHERE <conditions>]
ORDER BY `<stable columns...>` ASC LIMIT ? OFFSET ?
```

`countValues` 只含条件值。`pageValues` 先复制相同条件值，再追加 `pageSize`（`Integer`）与 `(long) (page - 1) * pageSize`（`Long`）。factory 不查询总数、不修正超界页；M06-T06 先执行 COUNT，算出规范页后以新的 `QueryCriteria` 再生成/选择分页 SQL。总数为零时是否跳过分页查询同样由 M06-T06 决定。

例如 COMPOSITE 定义列为 `ts_code, trade_date, close`、业务键为 `ts_code, trade_date`、无条件 page=1/pageSize=50 时，结果逐字为：

```sql
SELECT COUNT(*) FROM `tushare_pro__daily`
SELECT `ts_code`, `trade_date`, `close`, `source_plugin`, `source_api`, `ingested_at` FROM `tushare_pro__daily` ORDER BY `ts_code` ASC, `trade_date` ASC LIMIT ? OFFSET ?
```

对应值为 `[]` 和 `[50, 0L]`。有全部筛选时 WHERE 片段依次为 `` `ts_code` = ? ``、`` `trade_date` BETWEEN ? AND ? ``、`` `ann_date` BETWEEN ? AND ? ``，COUNT/分页的前五个值完全相同。

### 直接依赖与约束比较

- M02-T03 的 `DatasetDefinition`/`ColumnDefinition`/`FilterDefinition`/`BusinessKeyDefinition` 提供已校验、保序不可变的表、业务列、筛选和身份字段；本任务只读取这些值，不排序或修改元数据，不把 REST operator/control type 写回 plugin-api。
- M04-T06 证明生产 49 表与元数据逐表一致，COMPOSITE 主键顺序等于业务键字段，FINGERPRINT 只在 `stk_managers`/`pledge_detail` 使用内部 `business_key`，每表都有三个尾部来源字段；本任务按同一物理合同生成选择列与排序，不读取 migration SQL 或数据库 metadata。

两项输入互补且无冲突：M02-T03 冻结运行时白名单与有序字段，M04-T06 冻结这些字段在 MySQL 中的物理存在、主键和来源列。TRD 11.2～11.3 进一步冻结值规范化、闭区间/AND、20/50/100、明确列、业务键稳定排序和全参数绑定；本任务不改变任何上游合同。

## Files

- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QueryCriteria.java`：保存并校验固定查询值与分页输入。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QuerySql.java`：保存不可变的 COUNT/分页 SQL 与绑定值。
- Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QuerySqlFactory.java`：执行元数据白名单校验、安全标识符引用、条件/绑定、明确列与稳定排序生成。
- Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/query/QuerySqlFactoryTest.java`：八项公开合同、SQL、绑定、安全和失败边界测试。

不修改或删除其他文件。实现提交只暂存上述四个新增 Java 文件，固定消息为 `feat(core): build safe dataset query SQL`；设计、交接、看板、POM、既有 Java、YAML、迁移、临时文件和 `target` 不得混入实现提交。

## Tests

### 基线与可归因 RED

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期 plugin-api 79 项、core 67 项，共 146/146，0 failure、0 error、0 skipped，父项目/plugin-api/core 三层 Enforcer 通过。在当前工作站 Mockito/Byte Buddy 测试需要允许 JVM attach 的执行环境；attach 权限失败不是产品 RED。

随后只完整创建 `QuerySqlFactoryTest.java`，不创建三个生产文件，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=QuerySqlFactoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `QueryCriteria`、`QuerySql` 和 `QuerySqlFactory` 不存在而在 `tensor-core:testCompile` 非零；不得因测试语法、依赖解析或上游未匹配测试形成伪 RED。

### 固定八项 GREEN

创建最小生产实现后重跑同一定向命令，预期 `QuerySqlFactoryTest` 恰有八个普通 `@Test`，8/8 通过：

1. 反射确认两个 records 的精确 components、factory final/唯一公开表面；覆盖所有构造器 null、列表复制、tsCode strip+大写/格式拒绝、两组反向日期、page<1 和非法 pageSize；
2. 无筛选 COMPOSITE 定义生成逐字 COUNT 与分页 SQL，业务列/三个来源列明确且原序，按复合业务键 `ASC`，值精确为 `[]` 与 `[50, 0L]`；
3. 分别验证 tsCode、trade-date from-only/to-only/both、ann-date from-only/to-only/both 的运算符和单项绑定顺序；
4. 全部五个筛选值只以固定顺序形成 `AND`，COUNT 值与分页前缀相同，page=3/pageSize=20 追加 `[20, 40L]`；
5. definition 未声明对应 filter 时，只要 criteria 提供其任一值就以固定安全消息失败；`filters: []` 加无条件仍成功；
6. definition 声明三种稳定字段之外的 filter metadata 时，在 SQL 生成前以固定安全消息失败；
7. FINGERPRINT 分页 SQL 选择业务列/来源列但不选择内部键，并按全部身份字段原序、`business_key` 最后升序；
8. 证券代码、日期和 LIMIT/OFFSET 值均不出现在 SQL，SQL 只含 `?`；恶意 tsCode 在 criteria 构造期失败，所有表/列/排序标识符仅来自 definition 并经 `SqlIdentifierPolicy` 引用。

测试只使用 JUnit 5、AssertJ 和真实 plugin-api/core 类型；不使用 Mockito、Spring context、数据库、H2、Testcontainers 或生产测试钩子。

### Mutation、回归与静态门禁

受控 mutation A：临时跳过 criteria 与 `definition.filters()` 的成员校验，重跑第 5 项，预期原本不支持的 tsCode 被接受而测试失败；恢复源码后该项通过。受控 mutation B：临时移除 FINGERPRINT 排序末尾的 `business_key`，重跑第 7 项，预期排序逐字断言失败；恢复源码后该项通过。mutation 不提交。

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期 plugin-api 79 项、core 75 项，共 154/154，0 failure、0 error、0 skipped，三层 Enforcer 通过且无新增警告类别。

运行静态、范围、格式和清理门禁：

```bash
rg -n 'SELECT \*|String\.format|formatted\(|setObject|createStatement|@Transactional|(?i:token|credential)|RestClient|ServiceLoader' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query
rg -n 'SELECT COUNT\(\*\)|ORDER BY|LIMIT \? OFFSET \?|SqlIdentifierPolicy|List\.copyOf' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare \
  data-plane/tensor-plugin-fixture \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation
git diff --check
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
```

第一项禁止扫描应无输出并以 1 退出；第二项必须显示 COUNT、稳定排序、分页占位符、既有标识符策略与不可变列表；受保护路径退出 0；格式和 clean 退出 0。clean 后 Git 状态只能列 Files 节四个新增 Java 文件。提交后 `git show --stat --oneline HEAD` 必须显示固定消息与精确四文件范围，工作树干净。

## Acceptance

- 三个生产类型的公开表面、record components、null/不可变性和固定错误边界与设计一致，没有额外生产抽象或配置；
- QueryCriteria 对可选证券代码、两组单边/闭区间日期和 20/50/100 分页值执行确定规范化与校验，不承担 REST 默认值或超界页归一；
- 只有 `DatasetDefinition.filters()` 声明的 `ts_code`、`trade_date`、`ann_date` 能形成条件，其他 metadata 或未声明 criteria 安全失败，多条件固定用 `AND`；
- COUNT 与分页 SQL 的表、选择列、条件列和排序列全部来自不可变元数据/固定技术列并经 `SqlIdentifierPolicy`，所有查询值与分页值只通过占位符绑定；
- 分页 SELECT 明确返回全部业务列加三个来源字段，不返回内部 `business_key`；COMPOSITE/FINGERPRINT 均有唯一稳定升序，LIMIT/OFFSET 绑定和值顺序精确；
- 严格 TDD 得到缺三个生产类型的可归因 RED 后，定向 8/8、两项 mutation 按预期失败并恢复、reactor `test`/`verify` 154/154、三层 Enforcer、静态、范围、格式、清理和精确四文件提交门禁全部得到预期结果；
- 未修改 POM、plugin-api、迁移/YAML、现有生产合同、其他模块或测试生命周期，未提前实现数据库执行、页码归一、REST、序列化或前端职责。

## Risks

- `QuerySqlFactory` 只生成 SQL；M06-T06 必须先执行 COUNT，再把超界页规范为最后一页后重新生成或选择分页 SQL，总数为零时跳过行查询。不得直接执行最初请求页的 page SQL 后再修正响应。
- FINGERPRINT 排序会引用未选择的内部 `business_key`；MySQL 8.4 允许非 DISTINCT 查询按未选择列排序，M04-T06 已证明该列存在且唯一。未来若引入 DISTINCT/GROUP BY，必须重新设计排序和选择列合同。
- SQL 正确性依赖传入已验证的 DatasetDefinition 与 M04 物理 schema 一致；当前 `DatasetCatalog` 保证该前置条件。直接用合成 definition 的调用方只能获得语法模板，不获得表存在性保证。
- 本任务使用和下载参数校验一致的 `[A-Z0-9]+\.[A-Z0-9]+` 证券代码规则；若未来支持含其他字符或无市场后缀的标识，必须同时修订下载、查询、OpenAPI 和页面契约，不能只放宽本类。
