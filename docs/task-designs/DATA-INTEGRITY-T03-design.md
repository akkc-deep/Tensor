# DATA-INTEGRITY-T03 一致快照与受限只读扫描

## Goal

为同一“股票 × 接口”单元提供只读、一致、分批且受预算限制的本地读取。目标表、空日期记录和已声明参考表必须使用同一 MySQL REPEATABLE_READ 快照；完整物理键游标不得重复或遗漏。承接 T01 读取合同，不实现完整性结论计算。

## Scope

实现 `IntegrityReadRepository`、核心层读取授权值、累计预算和安全读取异常；提供 T04 的 `IntegrityContext.scan` 委托入口。包含实际 MySQL IT、类型精度、游标、并发快照、范围授权、空日期和资源释放验证。

不写证券表、不建立报告表、不调用上游、不复用 records 页码查询；不实现 `compare`、通用规则或 Tushare 候选推导。授权计划的任务装配归 T07，来源参考窗口选择归 T08；T03 交付可直接调用且自行校验边界的读取组件。不得给插件暴露 JDBC、连接或授权计划构造入口。

## Approach

### 入口与所有权

新增核心类，使用现有 `DatasetCatalog`、`SqlIdentifierPolicy` 和 `JdbcValueBinder`；不引入依赖。接口固定为：

```java
public IntegrityReadRepository(DataSource dataSource, DatasetCatalog catalog, Clock clock);
public <T> T withSnapshot(IntegrityScope scope, IntegrityReadPlan plan,
        IntegrityReadBudget budget, int batchSize, Function<ReadSession, T> action);

public interface ReadSession {
    IntegrityScope scope();
    void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> rows);
    void scanTarget(Consumer<TargetBatch> rows);
    List<IntegrityReadRequest> readRequests();
}
public record TargetBatch(boolean dateScopeUnresolved, List<Map<String, Object>> rows) {}
```

`ReadSession` 和 `TargetBatch` 是 repository 的嵌套 public 类型。它们不实现 `IntegrityContext`；T04 的真实 context 将 scan 委托到 session，并实现 compare，不能用返回假统计的占位 compare。`scanTarget` 为通用规则读取完整目标列（含物理元数据），先交付范围内批次，再交付空日期批次；两者通过 `dateScopeUnresolved` 明确分开。普通来源规则继续使用已有 `IntegrityReadRequest`。

`scope` 输入须是已受理的股票单元（symbol 非空），datasetKey 与 plan.target 一致。仓库在事务启动后构造含真实 `snapshotStartedAt` 的 scope，保持 acceptedAt、股票和原闭区间不变；消费者必须使用 `session.scope()`。默认 batchSize=500 由后续配置传入；拒绝非正值，不静默更改配置。

### 单连接一致快照

仓库从 DataSource 取得本次独占连接，保存原设置，设置 REPEATABLE_READ、readOnly=true、autoCommit=false，然后执行固定内部语句 `START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY`。该语句成功后记录 `clock.instant()` 为 snapshotStartedAt，再调用 action。所有目标、空日期和参考扫描直接使用该连接，无额外获取连接或嵌套事务。

不复用调用者的现有事务：入口发现 `TransactionSynchronizationManager.isActualTransactionActive()` 时拒绝；调用方必须在报告写事务之外运行。连接直接由仓库管理，不能用 `DataSourceUtils` 取得外层绑定连接；正式装配传入普通 DataSource。快照启动 SQL 是常量，来源没有 SQL 入参。

结束时总是 rollback 只读事务，再恢复隔离级别、readOnly、autoCommit 并关闭连接；异常路径同样清理。session 在 finally 中失效，逃逸后再次调用及异线程调用均拒绝。禁止 consumer 内递归 scan，避免未结束 ResultSet 与另一扫描重入；同一 action 内顺序多次 scan 合法。T03 不持有其他任务的连接、线程池或队列。

### 声明与授权计划

新增 `IntegrityReadPlan`，由可信核心执行器在调用 withSnapshot 前构造，不提供给插件：

```java
public record IntegrityReadPlan(IntegrityDescriptor target,
        List<ReferencePermit> references) {
    public record ReferencePermit(IntegrityDescriptor descriptor,
            String dateField, IntegrityDateRange range,
            Map<String, Object> fixedEqualities, String purpose) {}
}
```

防御复制所有容器。目标必须 STOCK_DATE/STOCK_SNAPSHOT；每个参考 permit 必须与 target.dependencies 中一个 datasetKey/purpose 精确匹配，并且其 descriptor.datasetKey 指向 catalog 中同来源的真实定义。允许同表不同 purpose，重复 datasetKey/purpose 拒绝。调用 `IntegrityContracts.validateDescriptor` 校验目标/参考元数据和依赖定义，不能信任仅通过字符串校验的表名。

参考 descriptor 明确其股票定位列和是否为快照；参考没有描述时不授权，后续来源规则解释依赖不可用。T03 不依赖 T02 实现，也不在 core 硬编码 Tushare API、后缀或交易所。IT 使用合成的目标/参考描述和授权计划。

读取权限如下：

| 读取对象 | 强制边界 |
| --- | --- |
| 目标 STOCK_DATE | 股票列强制等于 scope.symbol；日期轴必须 target.dateField；正常扫描闭区间必须等于 scope.range；空日期扫描只允许同轴 `IS NULL`，不得附日期区间。 |
| 目标 STOCK_SNAPSHOT | 股票列强制等于 scope.symbol；无日期过滤，不将 list_date/setup_date 当历史窗口。 |
| 股票级参考 | 参考 descriptor.symbolField 强制等于 scope.symbol；不能用额外等值条件覆盖。STOCK_DATE permit 使用该参考的日期轴、scope.range；STOCK_SNAPSHOT permit 的 dateField/range 均 null。 |
| NON_STOCK 时间参考 | permit.dateField 必须为声明依赖中的 DATE 列；首版要求 COMPOSITE，其余物理业务键列须由非空 fixedEqualities 固定，防止全市场/全交易所扫描。range 只能是原范围、将原范围首尾分别向外取整到 ISO 周边界（周一至周日）、或向外取整到自然月边界（月初至月末）三者之一；支持原范围跨多个周期，但不得再多扩一周/月或任意向历史扩张。 |

参考 purpose 必须等于已声明用途，并在读取记录中保留实际窗口；较大的窗口只影响参考读取，原目标 scope 不变。以 Tushare 作为后续调用示例：日历 permit 固定单个 exchange，dateField=cal_date；daily 用原区间、weekly 用周一至周日、monthly 用月初至月末；stock_basic 同股票快照，suspend_d 同股票原区间。SH/SZ 与交易所的来源语义由 T08 负责，核心只执行已固定的授权值；BJ 不得由核心自动填 SSE。

扫描请求必须匹配目标或一个参考 permit；参考只能投影该 dependency.columns。请求的日期轴/区间/用途须匹配授权，fixedEqualities 由仓库注入，若请求给出不同值直接拒绝；额外等值条件只能使用已授权投影列且类型正确。目标同样注入股票条件；无股票条件的请求仍受限，有其他股票值的请求拒绝。所有投影、谓词、隐藏游标列必须来自 catalog 或下述固定物理元数据白名单，随后统一经 SqlIdentifierPolicy 引用。空投影、非法/未声明列、未声明参考、错误用途、异源引用、越界日期均在执行 SQL 前失败。

目标可读取物理列 `source_plugin`、`source_api`、`ingested_at`，以及 FINGERPRINT 的 `business_key`。这些列不能用来绕过股票/日期过滤；参考不因为内部游标需要而自动扩大对插件的投影权限。

### 游标与类型

每次 scan 独立从首键开始，但复用 session/预算。SQL 仅 SELECT，WHERE 使用绑定参数；无 OFFSET、COUNT 全表预读或 ingested_at 截止伪快照。

- COMPOSITE 按 `definition.businessKey().fields()` 的全部物理键 ASC 排序，游标使用同序元组严格 `>`（单键用标量）。
- FINGERPRINT 仅按物理 `business_key` ASC 游标；完整业务字段仍供 T04 重算，不用逻辑业务字段代替物理键排序。
- 隐式 SELECT 所需游标键，即便调用者未投影它们；交付 map 只含授权请求列。结果的游标值必须非空，发现缺失视为读取失败。
- LIMIT 用绑定参数，单批不超过 batchSize；提取该批最后一行的完整物理键供下一批使用。全部批次遵循数据库相同排序/比较语义，不在 Java 中另排序。
- DATE 绑定/读取为 LocalDate；LONG 只能是精确 Long（禁止 double/截断）；DECIMAL 使用 BigDecimal，按定义检查 precision/scale，`setScale(..., UNNECESSARY)` 允许去掉多余零但拒绝舍入；字符串/枚举按定义长度和值域校验。所有参数通过 JdbcValueBinder 绑定，禁止拼接值。
- 输出 LONG 使用 getLong + wasNull，DECIMAL 使用 getBigDecimal，DATE 使用 getDate/toLocalDate；ingested_at 按 UTC Instant。nullable 空值原样保留。批列表和每行 map 防御复制并不可修改，不能用拒绝 null 的 Map.copyOf 存 nullable 行。

### 空日期分支

`scanTarget` 对 STOCK_DATE 发起正常区间扫描及同股票 dateField IS NULL 扫描（后者不加日期上下界）。后者交付 `TargetBatch(true, rows)`，其日期仍为 null；不能混入正常命中数或任意日期。STOCK_SNAPSHOT 只产生 `TargetBatch(false, rows)`。

T04 将 true 批次转成 UNKNOWN/DATE_SCOPE_UNRESOLVED、issue_date=null 的问题，并独立执行可适用的键/字段检查；T03 不提前创建 report/result。NULL 分支也计入同一预算，使用相同物理键游标、快照与授权。日期是 COMPOSITE 非空物理键时返回零行，允许为空的事件日期用 FINGERPRINT 测试实际分支。

### 预算、终止与读取记录

新增 `IntegrityReadBudget(long maxItems, Instant deadline, Clock clock)`，提供 `consume(long items)`、`check()`、`remainingItems()`、`remainingTime()`；所有数值须合法。默认 maxItems=500000、deadline=单元开始+120秒（后续 T07 取单元/任务 deadline 较早者）。计数包含每次扫描真正读到的目标、参考、空日期行，重复扫描不去重；T04 预期键生成使用同一 consume，不创建新预算。

恰好到达计数上限允许。为区分“刚好读完”与“仍有下一行”，每批最多读取 `min(batchSize, remainingItems + 1)`；remaining=0 时可探测 1 行，空结果成功，遇到第 1 行即超限并停止，不将超限行交给 consumer。每行交付前消耗预算，异常不能产生伪全量结果。

时间达到 deadline 立即停止，在语句执行前、读行、每批前后及 action 返回前检查。每个 PreparedStatement 的 queryTimeout 取剩余预算的整秒下界，不能使用向上取整超出预算或 0（无限等待）；不足 1 秒时不再启动 SQL，报告剩余预算不足。长查询仍受语句超时控制，查询返回后再校验真实 deadline。原始截止时刻和查询超时早退分别可解释，不能把早退标成正常读完。

新增 `IntegrityReadException`，暴露安全 reasonCode：INVALID_READ_REQUEST、SCAN_LIMIT_EXCEEDED、UNIT_TIME_BUDGET_EXHAUSTED、READ_FAILED、READ_SESSION_CLOSED；message 不包含 SQL、绑定值、数据库 URL 或驱动原文。扫描授权/类型异常、SQLException、预算超限、consumer 异常都使 session 标记失败并结束；即使 action 捕获它们，仓库返回前也必须拒绝成功。T07 将此类终止映射到 ERROR/incomplete，保留已知问题且覆盖率=null，T03 不返回包含“已完成”含义的计数对象。

在 session 内保留实际读取请求的不可变记录（datasetKey、purpose、dateField/range、nullDates、snapshotStartedAt），通过 `readRequests()` 返回 `List<IntegrityReadRequest>`；读取时点由 `scope()` 获取，不记录 SQL 或凭据。包含受限股票/交易所筛选，供 T04/T08 构建参考用途证据。只在授权校验通过后记录；空返回也记录一次，不按每页重复。

## Files

- 新增 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityReadRepository.java`：连接/快照、授权、游标、精确绑定/读取；嵌套 ReadSession、TargetBatch。
- 新增同目录 `IntegrityReadPlan.java`：目标元数据与参考授权，嵌套 ReferencePermit。
- 新增同目录 `IntegrityReadBudget.java`：跨扫描与预期键的累计计数/时间边界。
- 新增同目录 `IntegrityReadException.java`：安全异常及 reasonCode。
- 新增 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityReadRepositoryTest.java`：授权/预算/失效会话等不需要 MySQL 的边界测试。
- 新增同目录 `IntegrityReadRepositoryIT.java`：真实 mysql:8.4.6，一致快照、游标、精度与只读验收。复用既有 Testcontainers 依赖与测试装配方式，不改生产迁移。
- 新增 `docs/verification/DATA-INTEGRITY-T03.md`，完成时更新本任务看板及后继交接。

不修改 plugin-api 合同、Tushare/fixture 下载行为、数据集 YAML、现有 records 查询或证券表 schema；核心组件的 plan 只供框架装配，来源规则仍仅消费 IntegrityContext。

## Tests

Java 21；实际 MySQL 使用项目已有 Testcontainers `mysql:8.4.6`。先写 batchSize=2 的复合键跨页快照失败测试，再实现读取组件。命令：

```sh
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  -Dtest=IntegrityReadRepositoryTest,IntegrityReadRepositoryIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
git diff --check
```

IT 通过显式 -Dtest 运行，不依赖默认 Surefire 的 `*IT` 发现；Docker 未运行或连接不可达不能 skip 后算通过。普通全模块 test 的通过也不代替上述 IT。

必须证明：

1. COMPOSITE 5 行、至少三批，同日期不同业务键全部保留；FINGERPRINT 使用非逻辑顺序的物理键，同样逐条一次。请求投影省略物理键仍不漏，空表/正好整批/最后不足一批正常结束。
2. 快照启动后另一连接提交目标插入/更新/删除及参考更新；当前 session 的后续页、重复扫描和首次参考扫描均只见快照旧值，新 session 看到新值。用 latch 控制提交，不靠 sleep 猜测；断言 RR/readOnly，snapshotStartedAt 与 scope 一致。
3. 同股票区间外不返回；请求另一股票、越界窗口、错误日期轴、未声明表/列/用途/参考、其他交易所覆盖 fixedEqualities 全部拒绝；记录 SQL 调用计数证明非法请求没有执行证券 SELECT。
4. 当前股票 2 行合法日期、1 行 null 日期及另一股票 1 行 null 日期：普通批次只有前两行；UNRESOLVED 批次只有当前股票的空日期行且 date=null。快照接口不按历史日期过滤。
5. 日历引用的周/月边界扩展可读，任意更大范围拒绝；股票参考不允许越界或跨股票，所有引用必须已声明且固定必要键。
6. Long=9007199254740993、BigDecimal=1.000000000000000001、nullable null、LocalDate 精确保留；非法参数精度和类型在 SQL 前拒绝，无 double 路径。
7. maxItems=3 时恰好 3 行成功，4 行失败；目标2+参考2、目标2+空日期2、多规则重复扫描和预期键 consume 合计均触限，不重置。允许上限不代表省略尾部探测。
8. 注入可控 Clock：deadline 相等即失败，每批后到期停止；SQL queryTimeout 不超过剩余预算，剩余不足1秒不启动新 SQL。任何读取/回调中断都关闭 session；捕获异常后 action 也不能返回成功。
9. 正常/异常路径连接均 rollback/释放、会话逃逸及异线程/递归扫描拒绝；证券表内容在纯检查前后完全相同，数据库拒绝在该事务内写入。并发写入案例另行比较预期外部变化，不误算检查写入。

## Acceptance

看板 T03 全部结果有真实 MySQL 和专项断言：同单元一致快照，完整游标无重漏，授权不越股票/范围，精确数值，空日期单列，累计预算和安全终止，无证券写入。T04 可以直接将 ReadSession.scan 接到 IntegrityContext，T07 能在报告写事务之外运行一个完整读取单元；不得把中断扫描当全量统计。

## Risks

MySQL 和本机 Docker 权限是 IT 环境条件，不能用 H2 或跳过代替证据。MySQL JDBC queryTimeout 只有整秒精度，采用保守下界会提前少于一秒停止，必须保留未完成语义；测试不得期待超预算后继续查询。ReferencePermit 是可信框架的授权输入，来源只能提交受它约束的读取请求；后续 T07/T08 装配必须遵守这里固定的用途、股票和窗口规则。T03 已按本设计实施，实际 MySQL 和后端回归结果见 `docs/verification/DATA-INTEGRITY-T03.md`。本工作区含既有 Studio/T01/T02 暂存改动，保留它们，不自动合回原工作区。
