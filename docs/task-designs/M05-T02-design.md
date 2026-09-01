# M05-T02 `DatasetCatalog` 和启动元数据/表结构校验——任务设计

任务编号：`M05-T02`
对应任务：[M05-T02](../superpowers/plans/tensor-modules/M05-core-registry-adapter.md#task-m05-t02-数据集目录与启动校验30h)
实施产物：`tensor-core` 中 JDBC schema 快照、启动校验器、只读数据集目录和一个真实行为测试

## Goal

在 `tensor-core` 中建立启动期数据集准入边界：接收已经由插件层构造的不可变 `DatasetDefinition` 列表，通过真实 `DataSource` 的 JDBC metadata 读取表、原序列、JDBC 类型族、可空性、主键和唯一键快照，逐数据集与 M03/M04 稳定契约比较；只有元数据和实际表结构均有效且 DatasetKey 唯一的数据集进入只读 `DatasetCatalog`。单个缺表、配置或 schema 漂移只排除自身，不隐藏有效兄弟；数据库 metadata 整体不可用则使启动失败，避免应用带着未经验证的 SQL 标识符继续运行。

## Scope

包含：

- 创建 `DatasetCatalog`，只公开按 `DatasetKey` 精确查找和按 `PluginId` 确定排序列举已验证定义的能力；
- 创建用户批准的具体 `SchemaInspector(DataSource)`，以 JDBC `DatabaseMetaData` 读取当前连接 catalog 中一个 `TableName` 的不可变快照；
- 创建用户批准的 `DatasetStartupValidator(List<DatasetDefinition>, SchemaInspector)`，由 `validate()` 生成 `DatasetCatalog`；
- 复核 M02 未跨对象强制的 display order 和参数关系，并验证实际列名/顺序、JDBC 类型族、可空性、主键、唯一键及键引用；
- 对 null 定义、重复 DatasetKey、缺表和结构漂移使用固定安全 WARNING 做局部隔离，不增加公开 diagnostics API；
- 对 JDBC metadata 获取失败使用固定安全异常使整体启动失败，不把原始 SQLException、URL、凭证、SQL 或堆栈写入普通诊断文本；
- 创建恰好 10 项 `DatasetStartupValidatorTest`，覆盖 inspector 快照、有效目录、局部隔离、重复、元数据关系、schema 漂移、失败传播和不可变性；
- 执行严格 TDD、模块回归、Enforcer、静态/范围/格式/清理和精确四文件提交门禁。

排除：

- 不修改 POM、M02 records、M03 YAML/loader、M04 迁移/schema 测试、M05-T01 注册表或其他模块；
- `tensor-core` 不依赖 `tensor-plugin-tushare`，不扫描 classpath、不读取 YAML/JSON schema，不在本任务装配 Spring Bean；
- 不调用 `DatasetAdapter.definition()`，不改变 `AdapterRegistry`，定义列表由后续 app/plugin 装配边界提供；
- 不实现 M05-T03～T05 参数值校验、值转换、通用适配、重复来源行或指纹编码；
- 不执行 Flyway migrate/validate，不创建/修改表、索引或列，不修复 schema 漂移；
- 不实现 Upsert、事务、查询 SQL、下载、REST、健康端点或前端行为；
- 不增加公开诊断集合、错误 DTO、异常类型、状态枚举、刷新、热加载或第二套数据集加载器；
- 不重复 M04 对字符长度、DECIMAL precision/scale、datetime precision、引擎、collation 和二级查询索引名称/总量的实际 MySQL 8.4.6 门禁；本任务按用户批准边界校验 JDBC 类型族、nullability 和唯一性键。

## Approach

### 冻结公共表面

三个生产类均为 `public final`，位于 `com.akkc.tensor.core.catalog`。除 Java record 自动成员外，不增加其他 public/protected 构造器、方法或字段：

```java
public final class DatasetCatalog {
    public Optional<DatasetDefinition> find(DatasetKey datasetKey);
    public List<DatasetDefinition> list(PluginId pluginId);
}

public final class SchemaInspector {
    public SchemaInspector(DataSource dataSource);
    public Optional<TableSchema> inspect(TableName tableName);

    public record ColumnMetadata(String name, int jdbcType, boolean nullable) {}
    public record UniqueKeyMetadata(String name, List<String> columns) {}
    public record TableSchema(
            List<ColumnMetadata> columns,
            List<String> primaryKey,
            List<UniqueKeyMetadata> uniqueKeys) {}
}

public final class DatasetStartupValidator {
    public DatasetStartupValidator(
            List<DatasetDefinition> definitions,
            SchemaInspector schemaInspector);
    public DatasetCatalog validate();
}
```

`DatasetCatalog` 的构造器保持 package-private，只允许同包启动校验器创建已验证目录。`find` 和 `list` 均拒绝 null；缺失 key 返回 `Optional.empty()`，没有该插件的数据集返回空列表。目录内部按 `DatasetKey` 保存不可变映射；`list(pluginId)` 按 `apiName.value()` 升序返回不可修改列表。构造结束后修改原始 definitions 列表不得改变校验器或目录。

`SchemaInspector` 的三个 nested records 在 compact constructor 中拒绝 null/空白名称和 null 元素，并对所有 list 做不可变有序复制；它们不自行判断键引用是否存在，确保 validator 能把不一致快照作为该数据集的启动校验失败处理。

### JDBC schema 快照

`SchemaInspector` 构造器拒绝 null `DataSource`，`inspect` 拒绝 null `TableName`。每次调用只获取一个连接，并使用该连接的 `catalog` 与 `DatabaseMetaData`：

1. `getColumns(catalog, null, tableName.value(), null)` 读取名称、`DATA_TYPE`、`NULLABLE` 和 `ORDINAL_POSITION`，按 ordinal 升序形成 `ColumnMetadata`；无列时返回 `Optional.empty()` 表示缺表；
2. `getPrimaryKeys(catalog, null, tableName.value())` 读取 `COLUMN_NAME` 与 `KEY_SEQ`，按 key sequence 形成主键列；
3. `getIndexInfo(catalog, null, tableName.value(), true, false)` 只读取唯一索引，排除 `PRIMARY` 和 statistics/null-column 行，按索引名升序、`ORDINAL_POSITION` 升序形成 `UniqueKeyMetadata`；
4. 所有 result set、connection 都由 try-with-resources 关闭；快照不保留 JDBC 对象、连接或可变 collection。

任何 `SQLException` 都由 inspector 转换为无 cause、固定消息 `Schema inspection failed` 的 `IllegalStateException`；不记录或拼接原始异常、数据库 URL、账号、SQLState、vendor code 或查询文本。`DatasetStartupValidator` 不捕获该异常，因此数据库整体不可用或 metadata 读取失败会阻止启动，符合 TRD 的数据库级失败边界。其他 `RuntimeException` 与 `Error` 原样传播。

### 定义级启动校验

validator 构造器拒绝 null definitions 列表和 null inspector，并复制列表本身；null 元素保留到 `validate()` 作为局部损坏项处理。`validate()` 不修改输入定义，按以下顺序执行：

1. null 定义使用固定 WARNING `Dataset disabled by startup validation` 跳过；
2. 按 `DatasetKey` 分组；同一 key 出现两次或以上时，所有参与定义均排除，并为该组记录一次固定 WARNING `Duplicate dataset key disabled`；不采用 first/last-wins；
3. 对每个唯一 key 先做定义级关系检查：`columns[i].displayOrder() == i`；每个非 null `relatedParameter` 引用同一定义内已声明参数；`DATE_RANGE_MEMBER` 的关联目标也是 `DATE_RANGE_MEMBER` 且反向引用当前参数；
4. 定义级关系失败时使用固定 WARNING `Dataset disabled by startup validation` 排除，不调用 inspector；
5. 每个通过关系检查的定义只调用一次 `schemaInspector.inspect(tableName)`；`Optional.empty()` 表示缺表并局部排除；
6. 比较实际 schema；任一不匹配均以同一固定 WARNING 排除，不暴露表名、字段名、实际类型或异常内容；
7. 所有通过项按 `pluginId.value()`、`apiName.value()` 排序后创建不可变 `DatasetCatalog`。

固定日志只记录上述两种字面文本，不附加 Throwable、定义 `description`、路径、配置、SQL 或堆栈。目录没有诊断 getter；后续结构化健康诊断必须由对应任务单独设计。

### 期望 schema 与比较规则

每个定义的期望列严格按以下顺序构造：

1. `definition.columns()` 原序映射；
2. 仅当 `businessKey.mode() == FINGERPRINT` 时追加 `business_key`；
3. 固定追加 `source_plugin`、`source_api`、`ingested_at`。

业务列 JDBC 类型族固定复用 M04-T06 已在 MySQL 8.4.6 证明的映射：

| LogicalType | `java.sql.Types` |
|---|---:|
| `STRING` | `Types.VARCHAR` |
| `TEXT` | `Types.LONGVARCHAR` |
| `DATE` | `Types.DATE` |
| `MONTH` | `Types.CHAR` |
| `LONG` | `Types.BIGINT` |
| `DECIMAL` | `Types.DECIMAL` |
| `ENUM` | `Types.CHAR` |

业务列 nullable 精确等于 `ColumnDefinition.nullable()`。`business_key` 为 `Types.CHAR` 且不可空；`source_plugin`/`source_api` 为 `Types.VARCHAR` 且不可空；`ingested_at` 为 `Types.TIMESTAMP` 且不可空。

实际 columns 必须与期望在数量、名称、顺序、JDBC type 和 nullable 上逐项完全相等，不能忽略多余技术列或重排序。比较键之前先验证实际 `primaryKey` 和每个 `uniqueKeys[].columns` 的每一列均引用实际 columns；任何未知引用使该数据集失效。

主键必须精确为：

- `COMPOSITE`：`definition.businessKey().fields()` 的原顺序；
- `FINGERPRINT`：单列 `business_key`。

M04 已冻结 49 张生产表不含主键之外的 UNIQUE 索引，因此 `uniqueKeys` 必须为空；主键缺失、顺序漂移、被普通 UNIQUE 替代或出现额外 UNIQUE 均判为不一致。二级非唯一查询索引、长度/精度和表属性继续由 M04-T06 的实际 MySQL 门禁承担，不进入 `TableSchema`。

### 直接依赖与约束比较

- M03-T09 提供 49 份经公开 loader、manifest、模板字段、显式参数、业务键和 filters 总契约验证的 `DatasetDefinition` 语义：49 API、851 个原序业务列、47 个 COMPOSITE、2 个 FINGERPRINT、表名公式和默认 batchSize 均已冻结。Core 只消费装配边界提供的 `List<DatasetDefinition>`，不依赖 Tushare loader 实现。
- M04-T06 提供 V1～V5 的 49 张生产表和固定 MySQL 8.4.6 实证：1000 总列、49 PRIMARY、40 个非唯一二级索引、两个 FINGERPRINT 技术键、三个统一来源字段及逻辑类型到 JDBC 类型映射；测试专用 V6 不进入本任务的生产目录输入。

两项依赖约束一致：M03 定义提供期望身份、列序、类型/nullability 和业务键，M04 实际 schema 提供同一映射的物理结果；本任务只在启动时把两者相交验证，不读取 SQL 或重新裁决 YAML，也不以数据库快照生成期望。

## Files

创建：

- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetCatalog.java`：不可变、只含已验证定义的查找与插件内列表视图；
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/SchemaInspector.java`：从 `DataSource`/JDBC metadata 生成不可变表 schema 快照；
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog/DatasetStartupValidator.java`：执行定义关系、重复身份和实际 schema 校验并创建目录；
- `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/catalog/DatasetStartupValidatorTest.java`：真实 validator/catalog 测试与受控 JDBC metadata 边界测试。

不修改或删除其他文件。实现提交消息固定为 `feat(core): validate dataset catalog at startup`。实现提交精确包含上述四个新文件；设计、交接、看板、POM、M05-T01、其他模块、`target/` 或临时文件不得混入。

## Tests

### 基线与缺类 RED

实施前运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

预期现有 `tensor-plugin-api` 79 项、`tensor-core` 10 项，共 89/89，0 failure、0 error、0 skipped，父项目、plugin-api 和 core 三层 Enforcer 通过。

随后先完整创建 `DatasetStartupValidatorTest.java`，不创建三个生产类，再运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=DatasetStartupValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令必须只因 `DatasetCatalog`、`SchemaInspector`、`DatasetStartupValidator` 及其 nested snapshot 类型不存在而在 `tensor-core:testCompile` 非零；不得因依赖解析、测试语法、上游未匹配测试或既有失败形成伪 RED。

### 聚焦 GREEN

创建最小生产实现后重跑同一聚焦命令。`DatasetStartupValidatorTest` 固定恰有 10 项测试且 10/10 通过，0 failure、0 error、0 skipped：

1. 两个有效定义经真实 inspector 快照进入不可变目录，`find/list` 正确、列表按 apiName 排序，构造输入后续修改不影响结果；
2. `SchemaInspector` 从受控 JDBC metadata 形成按 ordinal/key sequence 排序的不可变 columns、primaryKey、uniqueKeys 快照，并在无列时返回 empty；
3. 缺表定义被排除，同批有效兄弟仍可查找；每个唯一有效定义只 inspect 一次；
4. 实际列缺失、多余或顺序漂移均只排除对应定义；
5. 任一业务或技术列 JDBC type 漂移只排除对应定义；
6. 任一业务或技术列 nullable 漂移只排除对应定义；
7. 主键缺失、顺序错误、被普通 UNIQUE 替代或出现额外 UNIQUE 均被排除；
8. 主键或 UNIQUE 引用不存在的实际列时被排除，不隐藏有效兄弟；
9. display order、参数关联或重复 DatasetKey 不合法时局部排除；重复 key 的所有定义均排除，唯一兄弟保留；
10. null 列表/inspector/lookup key 被拒绝，null 定义局部跳过；JDBC `SQLException` 只产生固定 `IllegalStateException` 并阻止 `validate()`，`RuntimeException`/`Error` 不被吞掉。

测试只使用 JUnit 5、AssertJ、真实 `DatasetDefinition`/值对象、真实三个生产类，以及 Mockito 对 `DataSource`、`Connection`、`DatabaseMetaData`、`ResultSet` 这一外部 JDBC metadata 边界的窄替身。断言只验证 inspector/catalog/validator 的真实行为，不断言 mock 存在性或日志文本；mock 行必须镜像 JDBC 文档字段并让错误的 ordinal、type、nullability 或 key sequence 无法满足期望。预期值使用字面 JDBC 类型和手工构造定义，不从 validator helper 反向生成。

### 模块、静态与范围门禁

运行：

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

两条命令均预期 `tensor-plugin-api` 79 项、`RegistryTest` 10 项、`DatasetStartupValidatorTest` 10 项，共 99/99，0 failure、0 error、0 skipped；三层 Enforcer 通过。测试输出允许设计固定的安全运行时 WARNING；Maven/编译不增加既有 platform-encoding 之外的警告类别。

运行静态与范围门禁：

```bash
rg -n 'ServiceLoader|RestClient|org\.springframework\.(stereotype|context\.annotation)|@Component|@Service|Flyway|createStatement|prepareStatement' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/catalog \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/catalog
git diff --check
```

扫描预期无输出并退出 1；`clean` 退出 0；POM/app/plugin-api/plugin-tushare 无差异；提交前 scoped status 精确列出四个新文件且无 `target`；格式检查退出 0。按仓库规则把四个新文件加入 Git。提交后 `git show --stat --oneline HEAD` 必须显示固定消息和精确四文件范围，工作树干净。

## Acceptance

- 三个 `public final` 类和 nested records 的公开表面精确符合本设计；Catalog 除 `find/list` 外无公开构造、刷新或 diagnostics；
- Core 只接收已构造定义列表并依赖 plugin-api/JDBC，不反向依赖 Tushare loader、YAML、Flyway 或 app；
- inspector 对当前 catalog 的实际表只读取一次不可变、有序 JDBC metadata 快照，正确表达缺表、列 type/nullability、主键和唯一键；SQL/JDBC 整体失败使用固定安全异常阻止启动；
- validator 对 null、重复 key、定义关系、缺表、列/类型/nullability、key 和无效引用做逐数据集隔离；坏数据集不进入目录且不隐藏有效兄弟；
- 目录只暴露验证通过且 key 唯一的定义，按值对象查找、按 apiName 确定排序，输入和返回集合均不可变；
- 期望列、JDBC 类型族、技术列和主键规则与 M03-T09/M04-T06 一致，不从数据库生成期望，不修改或自动修复 schema；
- TDD 得到缺三个生产类的可归因 RED 后 10/10 GREEN；模块 `test`/`verify` 99/99、三层 Enforcer、静态扫描、范围、格式、清理和精确四文件提交门禁全部得到预期结果；
- 未修改 POM、M02/M03/M04/M05-T01 或其他模块，未提前实现参数值校验、适配、持久化、查询、REST 或运行装配。

## Risks

- 用户批准的 `TableSchema` 只表达 JDBC 类型族、nullability 和唯一性键，因此 VARCHAR/CHAR 长度、DECIMAL precision/scale、DATETIME precision、引擎、collation 和非唯一查询索引不在运行时二次检查；这些属性继续由已完成 M04-T06 的固定 MySQL 8.4.6 schema 门禁和 Flyway 迁移保证。若运行时也必须重复校验，需扩展公开 snapshot 契约并重新设计本任务。
- JDBC metadata 的 catalog/schema 语义依赖 MySQL Connector/J；inspector 固定使用当前 connection catalog 和 null schema pattern。切换数据库或驱动不属于首期范围。
- 数据库权限、连接或 metadata 整体失败会阻止启动而不是把 49 个数据集全部静默排除；这是 TRD 对数据库整体不可用的失败边界。
- validator 只处理装配方传入的定义；后续 app/plugin 装配必须收集所有候选定义并调用一次 `validate()`。本任务不引入 Spring Bean 或改变注册表公开面。
- 按用户批准不提供公开 diagnostics API；固定日志能安全标记隔离事件，但无法由调用者结构化区分缺表、列漂移或 key 漂移。未来健康/API 若需要结构化原因，必须由对应任务单独设计。
