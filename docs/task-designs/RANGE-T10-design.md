# RANGE-T10 两张失败记录表与存储访问设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T10`：用两张 MySQL 表保存当前尚未解决的明确失败，提供首次原子创建、后续追加、精确原因更新和稳定分页读取；为 T11 提供可加入同一事务的存储原语。这里只交付存储能力，不提前接入下载执行。

## Scope

- 新增 V8 Flyway 迁移、Core JSON 编解码／仓储／存储服务及 App 装配；补齐真实 MySQL 迁移、约束、回滚和读取验证，以及受影响的 schema／打包清单。
- 直接消费已完成的 T04 `RecoverySelector`、`DatasetKey`、`ErrorCode` 和 T05 `DownloadParameterConverter.taskParameters` 输出。T10 不依赖 T09 才能实现；选择本项依据是 T09 完成后的 Order 10，直接依赖仍为 T04／T05。
- 保存公共条件和首次原始日期，首次保存后不变；精确区分对象及时间的完整联合键，重复明确失败更新原因。
- 不实现业务 Upsert 与成功删除的组合服务、首次／重试循环、执行槽位、日历或来源调用、HTTP 端点／DTO、页面、统计快照、历史任务、自动重试和启动恢复。成功删除的业务编排归 T11，首次／重试调用归 T12／T13，HTTP 展示解释归 T14。
- 保留全部旧迁移、49 份 Dataset YAML、生产策略资源和既有业务数据。V6 不进入生产包；不新增第三张管理表、展示范围列、状态、版本、请求哈希、计划、凭证或响应存储。

## Approach

### 1. 已确认输入与实施起点

按看板顺序读取 [TRD §7.1～§7.2、§8.1、§9.3](../design/Tensor_区间下载_TRD_v1.0.md)，生产迁移目录、测试 V6、当前 `core/persistence/`，再读 [T04设计](RANGE-T04-design.md)／[验证](../verification/RANGE-T04-plugin-contracts.md)、[T05设计](RANGE-T05-design.md)／[验证](../verification/RANGE-T05-parameter-conversion.md)及实际代码。分页补充以已完成 T03 的 [OpenAPI `/retry-tasks`](../contracts/openapi-v1.yaml) 为准。共享需求与实际代码是依据，临时 source inventory 只是查找指针。

设计时生产版本恰为 V1／V2／V3／V4／V5／V7，测试资源只有 V6；故本项固定创建 `V8__create_download_failure_tables.sql`，不能复用 V6 或修改 V7。现有 `PersistenceService.persist` 为 REQUIRED、60 秒短事务，`GenericUpsertRepository` 强制活动事务，`JdbcValueBinder` 用 UTC Calendar 绑定 Instant。新仓储复用同一个 `JdbcTemplate`／DataSource，服务复用 App 的 `PlatformTransactionManager` 和 `Clock`。

开始实施先记录当前分支／工作树／暂存清单，并在临时目录保存旧迁移、49 YAML、两份生产策略资源的 SHA-256；不重置已有暂存、不切分支、不清理用户数据库。第一个行为用例是在新 `RetryTaskStorageIT` 中用真实迁移和明细插入故障验证首次创建全回滚，然后实现本节 DDL 与最小存储链路。T10 完成前只按本设计验证，不开始 T11。

### 2. 固定 DDL

V8 全文按以下结构实现，所有列都显式非空；不使用 `IF NOT EXISTS` 隐藏错误结构，不修改旧迁移校验和：

```sql
SET time_zone = '+00:00';

CREATE TABLE `tensor_download_task` (
    `task_id` CHAR(36) NOT NULL,
    `plugin_id` VARCHAR(64) NOT NULL,
    `api_name` VARCHAR(64) NOT NULL,
    `task_params` JSON NOT NULL,
    `created_at` DATETIME(3) NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`task_id`),
    KEY `idx_download_task_updated` (`updated_at`, `task_id`),
    CONSTRAINT `chk_download_task_params_object`
        CHECK (JSON_TYPE(`task_params`) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tensor_download_task_item` (
    `task_id` CHAR(36) NOT NULL,
    `target_type` VARCHAR(16) NOT NULL,
    `target_value` VARCHAR(64) NOT NULL,
    `time_type` VARCHAR(16) NOT NULL,
    `time_value` VARCHAR(64) NOT NULL,
    `error_code` VARCHAR(64) NOT NULL,
    `error_message` VARCHAR(512) NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`task_id`, `target_type`, `target_value`, `time_type`, `time_value`),
    CONSTRAINT `fk_download_task_item_task` FOREIGN KEY (`task_id`)
        REFERENCES `tensor_download_task` (`task_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `chk_download_task_item_target` CHECK (
        (`target_type` = 'REQUEST' AND `target_value` = '') OR
        (`target_type` = 'STOCK' AND `target_value` <> '')),
    CONSTRAINT `chk_download_task_item_time` CHECK (
        (`time_type` = 'NONE' AND `time_value` = '') OR
        (`time_type` IN ('DATE', 'MONTH', 'RANGE') AND `time_value` <> ''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
```

主表只有这一个非唯一列表索引；升序索引可反向扫描 `updated_at DESC, task_id DESC`。子表主键的 task_id 前缀满足外键索引，不另建重复索引。JSON 列保证合法 JSON，CHECK 排除数组、字符串、数字、布尔及 JSON null，NOT NULL 排除 SQL NULL。对象／时间 CHECK 保证类型和空串规则；严格股票／日期／月份／范围规范继续由现有 `RecoverySelector` 检查，不在 SQL 复制日期解析器。数据库不替代策略兼容性校验。

时间由应用每次操作取一次 `clock.instant().truncatedTo(MILLIS)`，同事务的主／明细使用同一 Instant。绑定复用 `JdbcValueBinder` 的 UTC TIMESTAMP 分支；读取使用 `getTimestamp(column, UTC Calendar).toInstant()`。不使用 JVM 默认时区，不依赖仅作用于 Flyway 连接的 SET，不添加数据库 CURRENT_TIMESTAMP／ON UPDATE 默认行为。服务事务内部在锁定主表后取更新时间；不会更新 created_at。

### 3. JSON 对象与冻结边界

新增 `core.retry.TaskParametersJson`，使用现有项目采用的 Jackson 2。当前 Core 没有 Jackson 依赖，App 的 web starter 及 Tushare 模块有 Jackson；在 `tensor-core/pom.xml` 显式增加 `com.fasterxml.jackson.core:jackson-databind`，版本由父 POM 的 Spring Boot 3.5.16 BOM 管理。不让 Core 依赖 App／Tushare，不把存储序列化放到 plugin-api，也不使用 Web 的精度输出 ObjectMapper。现有模块架构规则允许 Core 使用 Jackson。

固定公开操作：`String write(Map<String,Object> values)`、`Map<String,Object> read(String json)`、`Map<String,Object> freeze(Map<String,Object> values)`。三个操作共用专用、构建后不再修改的 mapper；启用严格重复键检测、尾随 token 拒绝，以对象为根，禁用多态类型启用和 POJO 自动接受。解析器关闭源内容进入错误位置／诊断；任何失败只抛固定安全 `IllegalArgumentException("Task parameters must be a JSON object")`，不附解析 cause。

`freeze` 递归复制 JSON 值：对象键只接受非 null String，值可为 String、Boolean、null、有限 JSON 数字、嵌套对象或 List；Java 编解码保留数值精度（整数规范为 BigInteger，非整数为 BigDecimal），拒绝 NaN／Infinity、任意 POJO、日期对象、非字符串键、循环引用。这不承诺 MySQL JSON 能无损保存任意精度数字，数据库边界按下段实际回读校验。返回递归不可变 Map／List，容许 JSON null 值，不使用会拒绝合法 null 的 `Map.copyOf`／`List.copyOf` 直接复制通用对象。读写不依赖键顺序、空白或原文本；不将日期字符串自动转为时间类型。当前 T05 只输出 String 值，这是当前参数合同，通用 JSON 支持不等于新增合法接口参数。

写入只接受 Map，不暴露接收任意 JSON 文本的创建 API。`write` 将对象序列化一次，用 `PreparedStatement.setString` 绑定 JSON 列；禁止序列化后再序列化字符串。读时 `getString(task_params)` 再严格 `read`。重复键必须在 MySQL 规范化前拒绝；MySQL 原生 JSON 已丢弃的重复键无法事后恢复，不能声称直接 SQL 写入的历史重复键可被检测。新入库路径只有 Map，原始 JSON 解析入口由 codec 拒绝重复键。

[MySQL 8.4 JSON 文档](https://dev.mysql.com/doc/refman/8.4/en/json.html)区分 JSON 中的精确整数与近似数值；通过文本绑定不能把 Java BigDecimal／BigInteger 的任意精度等同于原生 JSON 的保存能力。因此 create 在同一事务插入主表后、插入首明细前，必须用同一 JdbcTemplate 连接 `SELECT task_params FROM tensor_download_task WHERE task_id=?`，严格解析实际保存对象，与冻结输入作递归语义比较。对象比较完整键集合及各值（忽略键顺序），List 比较长度及顺序，String／Boolean／null 精确相等；数字以 BigInteger 精确转 BigDecimal 后的 `compareTo==0` 比较，允许 `1.2300` 与 `1.23`，不允许丢失有效数字，不把数字和字符串等同。比较方法为 `TaskParametersJson` 包内可见的 `sameValue(Object left, Object right)`，不增加新公共类型或另一种序列化表示。

若语义不同，在事务回调内抛固定无 cause 的 `IllegalArgumentException("Task parameters cannot be stored without loss")`，正常回滚后再向外报告非法值；没有首明细或 SavedFailure，不补写字符串化／标签化数字。若 INSERT／回读 SQL 本身失败，或回滚／提交失败，仍按保存未确认映射。此检查是依赖数据库实际表示的事务内值校验，不是假设所有校验都能在 SQL 前完成，不限制未来参数名或 JSON 合法形状；可无损保存的数字、嵌套对象／数组／null 仍支持。append／updateReason 不写 task_params，无需重复该检查；读取无法检测外部 SQL 在历史上已经丢失的精度。

调用者必须传 T05 `taskParameters(api, validatedOriginal, actualTargetType)` 的结果：38 项保留日历过滤／月份展开前的 start_date/end_date，11 项不增加日期；REQUEST 保留原股票／市场公共条件，STOCK 不在主表重复 ts_code。存储层不加载插件或复制字段白名单／日期映射，不能靠猜 key 删除合法未来参数。T12／T13 在调用前负责策略和完整请求合法性；凭证、分页、状态、响应从不成为该 Map 的来源。

创建时立即冻结输入，落库后仅读取；append／update 的签名不接收公共条件，SQL 也从不更新 plugin_id、api_name、task_params、created_at。`{}` 合法；旧任务两端均缺失仍可读，单边或非法日期可读但 T05 重建拒绝；T10 不通过当前元数据过滤旧标识，不补造原始区间。展示的 originalDateRange／可执行性由 T14 结合 T05／元数据派生，不新增存储列或本项 HTTP DTO。

### 4. 类型和调用接口

仅新增三个生产类：`TaskParametersJson`、`RetryTaskRepository`、`RetryTaskStorageService`，同包 `com.akkc.tensor.core.retry`。小 record 嵌套于 Repository；不另造一组选择器枚举。

```java
// Nested public records in RetryTaskRepository
record ItemKey(UUID taskId, RecoverySelector selector) {}
record Failure(RecoverySelector selector, ErrorCode errorCode) {}
record Header(UUID taskId, DatasetKey datasetKey, Map<String,Object> taskParams,
              Instant createdAt, Instant updatedAt) {}
record Item(ItemKey key, ErrorCode errorCode, String errorMessage, Instant updatedAt) {}
record Task(Header header, List<Item> items) {}
record Criteria(PluginId pluginId, ApiName apiName, int page, int pageSize) {}
record Page(List<Task> items, int page, int pageSize, long totalElements, long totalPages) {}
record SavedFailure(ItemKey key) {}

// Constructor: RetryTaskRepository(JdbcTemplate jdbc, TaskParametersJson json)
// Constructor: RetryTaskStorageService(RetryTaskRepository repository,
//     PlatformTransactionManager transactions, Clock clock)
// Public methods in RetryTaskStorageService
SavedFailure create(DatasetKey dataset, Map<String,Object> frozenTaskParams, Failure firstFailure);
SavedFailure append(UUID taskId, Failure failure);
SavedFailure updateReason(ItemKey key, ErrorCode errorCode);
Optional<Task> find(UUID taskId);
Page list(Criteria criteria);

// Public transaction-participating primitives in RetryTaskRepository (T11 inputs)
Optional<Header> lockTask(UUID taskId);
boolean containsItem(ItemKey key);
int deleteItem(ItemKey key);
boolean hasItems(UUID taskId);
int touchTask(UUID taskId, Instant updatedAt);
int deleteEmptyTask(UUID taskId);
```

所有 record 必要字段非 null；Criteria 的两个标识是独立可空筛选。Header 的 Map 和 Task／Page 的列表防御性深复制；Task 中每项 taskId 必须等于 header.taskId，不允许重复完整 key。所有 UUID 绑定为标准小写 `uuid.toString()`；不增生成策略配置和请求幂等含义。读取 UUID／标识／枚举／selector 不合法时转换为安全保存数据错误，不把包装类型可能包含原值的错误直接泄露。

Failure 只接收结构化 errorCode，不接收 Throwable 或任意来源正文；error_message 由仓储内部固定 switch 生成，写入和更新统一处理。允许值和消息固定为：

| errorCode | 存储 error_message |
|---|---|
| SOURCE_AUTH_FAILED | Source authentication failed |
| SOURCE_PERMISSION_DENIED | Source permission denied |
| SOURCE_RATE_LIMITED | Source rate limit exceeded |
| SOURCE_UNAVAILABLE | Source is unavailable |
| SOURCE_NETWORK_ERROR | Source network request failed |
| SOURCE_TIMEOUT | Source request timed out |
| SOURCE_PAYLOAD_INVALID | Source returned an invalid payload |
| SOURCE_TRUNCATED | Source response is truncated |
| SOURCE_COMPLETENESS_UNCONFIRMED | Source response completeness is unconfirmed |
| ADAPTER_FIELD_MISSING | Source data is missing a required field |
| ADAPTER_TYPE_INVALID | Source data contains an invalid value |
| DATA_CONFLICT | Source data contains conflicting values |
| PERSISTENCE_FAILED | Persistence failed |

以上均为非空、无控制字符、远小于 512 字符的安全原因。其他码拒绝作为明确失败：参数／插件／日历／请求未确认尚未开始，存储保存／提交未知也不是可预登记失败。`PERSISTENCE_FAILED` 必须由未来调用者在确认当前业务事务回滚后传入；T10 不自行判定业务提交结果。T09／插件的安全描述在 T12 接入时按 selector＋code 转为本 record，无需 T10 引用 T09 类型。读取保留已保存的安全原因；若读到空、超长或控制字符原因／未知错误码，报告保存数据无效，绝不回显原值或修复记录。

### 5. 写事务、提交确认与错误

服务的 create／append／updateReason 为独立短事务入口，调用前禁止已存在活动事务，抛固定 IllegalStateException，防止 REQUIRED 加入外层事务却提前返回“已保存”。三个操作用 REQUIRED、60 秒 TransactionTemplate；不存在覆盖多个失败单元的外层事务。T11 使用 Repository 原语加入它自己的事务，不调用这三个服务写入口，也不使用 REQUIRES_NEW 分拆业务与删除。

| 操作 | 固定顺序和结果 |
|---|---|
| create | 先验证 dataset／failure 和冻结 JSON；生成一个随机 UUID；事务内取一个 UTC Instant，插入主表，按§3回读并比较JSON语义，通过后才插入首个明细；任一失败全回滚。不同调用即使同插件／参数也创建不同任务。不在下载开始时调用，不接受空明细创建 |
| append | 事务内按 taskId `SELECT ... FOR UPDATE` 主表，不存在则 RETRY_TASK_NOT_FOUND；取一次时间；按完整五列键 INSERT ... ON DUPLICATE KEY UPDATE，仅更新 error_code／error_message／updated_at；随后 touchTask。新明细／重复原因和主表更新时间一起提交，既有内容不改 |
| updateReason | 先锁主表，再 containsItem 完整键；主表或该项不存在均 RETRY_TASK_NOT_FOUND，不重新插入；只 UPDATE 当前项三字段，再 touchTask。不能按日期更新同日另一股票 |

Repository 的 insertHeader／insertItem／upsertItem／updateItemReason 及无锁查询 helpers 为包内可见，避免额外公开流程。所有写与锁原语要求实际活动事务且 JdbcTemplate 对应 DataSource 有绑定连接；否则在 SQL 前固定 IllegalStateException。同一连接全部通过 JdbcTemplate 获取，不调用 DriverManager 或手动 connection.commit。SQL 为固定表／列和绑定值，不拼接用户输入。重复更新和 touch 不能把 JDBC affectedRows=0 误判不存在：MySQL 对相同值更新的计数受 foundRows 配置影响，存在性已在锁内确定；无需依赖 UPSERT 返回值区分新增／重复。

`SavedFailure` 只在 `TransactionTemplate.execute` 正常返回之后构造／返回；回调中只计算待返回 key，禁止发布 taskId、更新外部计数或发事件。捕获事务执行边界的 DataAccessException／TransactionException，统一转固定无 cause 的 TensorException：`TASK_RECORD_SAVE_UNCONFIRMED / Failure record save is unconfirmed`。即使底层异常发生在 commit 后也不能确认保存，不能自动重试、重新生成 UUID、重新建表或文件补记。先前已经确认保存的 taskId 由 T12 保留，但不能用本次未返回的 key 冒充本次确认。

可提前完成的预校验在事务前抛固定安全 IllegalArgumentException（非法对象／参数／errorCode）；§3数据库JSON无损比较是明确的事务内校验例外，正常回滚后才传播其固定非法值异常，回滚失败仍归 TASK_RECORD_SAVE_UNCONFIRMED。锁内明确找不到对象为 `RETRY_TASK_NOT_FOUND / Retry task was not found`。保存内容解码／选择器不合法为 `RETRY_TASK_INVALID / Saved retry task is invalid`。查询 SQL／事务异常为 `QUERY_FAILED / Retry task query failed`。异常采用各类私有固定 TensorException 子类，不新增公共错误码，均不附 SQL／原 JSON／原异常 cause；不宽泛吞掉 Error 或所有编程异常。App 现有 GlobalExceptionHandler 会用 requestId＋code＋异常类型及脱敏堆栈记录；本项不引入 Core 对 App 日志的反向依赖。

保存事务异常统一“不确认”，不推断 commit／rollback；只有明确完成的数据库状态检查能作为测试证据，不能将 SQL 抛异常普遍等同于确认回滚。`COMMIT_UNCONFIRMED` 的业务提交分类属于 T11，T10 不实现它。

### 6. 读取和完整主键原语

find／list 在独立的 readOnly、REPEATABLE_READ、REQUIRED、60 秒事务内读取，入口同样不接受外层活动事务。无 FOR UPDATE、不取进程执行槽位、不访问插件或日历。快照让页计数、主表和明细相互一致；结果返回后可立即变化，不提供跨请求快照或版本协议。

- `find(UUID)`：先普通 SELECT 六列主表，不存在返回 Optional.empty；存在则按 taskId 取完整八列明细，构造不可变 Task。不在底层将 Optional.empty 强制映射 HTTP 404，T14 决定响应。
- `Criteria`：缺省值由静态 `defaults()` 返回 `(null,null,1,20)`；显式 page 必须 >=1，pageSize 仅 20／50／100，否则固定安全 IllegalArgumentException。两个标识由既有类型合法性约束，独立使用：apiName 单独过滤也合法，不要求插件当前注册；空串不作“全部”，在绑定阶段拒绝。不支持模糊搜索、排序参数或日期筛选。
- WHERE 只有可选 `plugin_id = ?`、`api_name = ?`，二者都提供则 AND。先 COUNT(*)；0 返回 page=1、原 pageSize、totalElements=totalPages=0、items=[]。非零 `totalPages=1+(total-1)/pageSize`，`page=min(requestedPage,totalPages)`，用 long 计算 `(page-1)*pageSize`，再 `ORDER BY updated_at DESC, task_id DESC LIMIT ? OFFSET ?`。超过尾页规范到最后一页，不返回伪空页。
- 分页针对主表。查出本页主表后，以最多100个 UUID 占位符 `WHERE task_id IN (...)` 一次批量取明细，按 taskId 分组并保持主表排序，避免 JOIN LIMIT 截断子项；空页不执行 IN 查询。T14 使用完整失败选择器构造范围摘要，不从原日期推断成功数。内部返回整份失败项不意味着 HTTP 列表要传完整详情。
- 明细固定 `ORDER BY task_id ASC, target_type ASC, target_value ASC, time_type ASC, time_value ASC`，比较使用表的区分大小写／重音排序规则；单任务顺序等价于四选择器字段升序。只保证稳定展示，不把字符串序当作下载执行顺序。两股同日始终两条。

T11 原语的全部 SQL 条件固定如下：

| 原语 | SQL／责任 |
|---|---|
| lockTask | `SELECT task_id, plugin_id, api_name, task_params, created_at, updated_at FROM tensor_download_task WHERE task_id=? FOR UPDATE`；Optional.empty 表示不存在，锁到外层事务完成 |
| containsItem | `SELECT 1 ... WHERE task_id=? AND target_type=? AND target_value=? AND time_type=? AND time_value=?`；已锁主表后调用，false 不产生业务写入 |
| deleteItem | `DELETE ...` 使用同一个五条件谓词；返回0／1，不负责计数、主表删除或业务 Upsert |
| hasItems | `SELECT 1 FROM tensor_download_task_item WHERE task_id=? LIMIT 1`，不借历史计数判断最后一项 |
| touchTask | `UPDATE tensor_download_task SET updated_at=? WHERE task_id=?`；仅更新指定字段，返回 JDBC 计数 |
| deleteEmptyTask | `DELETE FROM tensor_download_task WHERE task_id=? AND NOT EXISTS (SELECT 1 FROM tensor_download_task_item WHERE task_id=?)`，两个参数同一 UUID；返回0／1，FK继续保护仍有子项的主表 |

所有这些原语都必须加入调用者同一个事务；delete／touch 不自己开启／提交事务。调用者按主表锁→精确项存在→数据集锁顺序操作，T10 仓储不获取数据集锁。T11 未来组合 persist、deleteItem、hasItems、touchTask／deleteEmptyTask，并在它的提交确认后更新计数。本项仅测试原语的连接参与和回滚，不编写或调用该业务组合服务。

### 7. 装配与现有验证清单迁移

在 `ApplicationConfiguration` 增加 codec、repository、storage service 三个 bean，复用已有 JDBC／transaction manager／UTC clock，不修改现有 DownloadService 的构造器或运行路径。不把两表加入 DatasetCatalog／AdapterRegistry，不新增 Dataset YAML。

`FlywaySchemaContractIT` 当前扫描测试 classpath，实际同时有 V6。新增管理表应独立断言，不伪装成51个业务数据集：

| 清单 | 当前 | V8 后 |
|---|---:|---:|
| 测试 classpath 首次迁移 | 7 | 8 |
| 除 Flyway history 外全部表（49业务＋fixture＋管理） | 50 | 52 |
| 全部列 | 1008 | 1022（新增6＋8） |
| 全部主键 | 50 | 52 |
| 全部非主键索引 | 41 | 42 |
| 49业务表／列／主键／非主键 | 49／1001／49／41 | 完全不变 |

继续独立核对49定义、851业务列及46 composite／3 fingerprint。新增两个 management schema 断言检查精确列序／类型／非空／长度／DATETIME精度、引擎／collation、主键、唯一的列表索引、外键列与 RESTRICT 规则、三个 CHECK 的存在且 enforced。JSON 单独按 information_schema 的 DATA_TYPE=json、IS_NULLABLE=NO 断言，不强行当作字符串加入旧业务 `jdbcType` switch；该列 character/numeric metadata 不作为业务 VARCHAR 处理。

`keepsV6InTestOutputOnly` 的 main-output 名单加入 V8，test-output 仍精确只有 V6。`PackagedJarContractTest.PRODUCTION_MIGRATIONS` 加入 V8，仍只有七个生产迁移，49 YAML 和生产无fixture规则不变。`AcceptancePackagedJarContractTest` 现有按生产包逐条 SHA-256 比较会自动涵盖 V8，保留该检查，无需修改 packaging POM 或放宽“只增加 fixture＋V6”的集合。

`DividendBusinessKeyMigrationIT` 的 fresh-latest 首次迁移计数改8；两个 V6→V7 场景（`upgradesV6RowsWithoutChangingBusinessOrSourceValuesAndMatchesJavaFingerprint`、`updatesMigratedStageAcrossBatchAndInsertsDifferentStage`）显式配置 `.target("7")` 后继续期待只执行一次；保留 V6 基线六次、V7三个SQL语句故障测试和历史主键／数据断言。不要把所有 `.isOne()` 机械改成2，使 V7专项测试实际变为混合迁移测试。

新增 `DownloadFailureMigrationIT` 专门覆盖 V7→V8：在 App 测试模块用 `.locations("filesystem:src/main/resources/db/migration")`，在隔离容器 `.target("7")` 建库（六次），插入已有 `tushare_pro__stock_basic` 和 `tushare_pro__dividend` 哨兵数据并保存完整行；同一生产location取消target升级，只执行 V8一次，validate通过、再次migrate=0，业务行／字段／业务键／来源时间完全不变。另在新隔离schema验证生产全量七次、51表（无fixture）；现有 FlywaySchemaContractIT 验证含fixture的八次／52表。禁止对非测试数据库调用 clean／drop，不修复／重写历史 Flyway history。

## Files

以下均为 T10 启动后待实施路径；本设计准备阶段只创建本 Markdown。

- 新建 `data-plane/tensor-app/src/main/resources/db/migration/V8__create_download_failure_tables.sql`：唯一新迁移，两张管理表。
- 修改 `data-plane/tensor-core/pom.xml`：仅增加 BOM 管理的 jackson-databind 直接依赖。
- 新建 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/retry/TaskParametersJson.java`、`RetryTaskRepository.java`、`RetryTaskStorageService.java`：分别承担严格对象JSON／不可变复制、SQL与嵌套record／事务原语、写入确认及快照查询。
- 修改 `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`：三个存储 bean。
- 新建 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/retry/TaskParametersJsonTest.java`、`RetryTaskStorageServiceTest.java`：codec与服务边界单测；后者可用 Mockito 或事务代理验证返回时机，不能替代 MySQL回滚证据。
- 新建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/RetryTaskStorageIT.java`、`DownloadFailureMigrationIT.java`：使用 App 已有 Flyway／MySQL／Testcontainers 依赖及真实 V8，无须为Core新增Flyway测试依赖或复制DDL。
- 修改 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`、`DividendBusinessKeyMigrationIT.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java`：上述精确清单与版本边界；`AcceptancePackagedJarContractTest.java` 保持原逻辑并执行回归。
- 新建 `docs/verification/RANGE-T10-failure-storage.md`，修改 `docs/traceability/tensor-range-requirements.md`：记录本轮存储 AC-PRD-RANGE-21／22／26 证据，不升级后续端到端／真实来源结论。任务收尾按看板流程更新本项状态并准备 T11 设计／交接，不自动实施T11。
- 不删除文件，不改旧迁移／Dataset YAML／策略／HTTP入口／业务持久化服务。沿用当前分支和已有暂存，显式将本项新文件加入Git，保持其他人的暂存内容，不提交／发布。

## Tests

本节是 T10 实施时必须执行的验证，设计准备没有执行功能测试。容器固定 `mysql:8.4.6`、utf8mb4、`utf8mb4_0900_as_cs`，沿用已有显式 `MYSQL.start()` 或 `@Testcontainers`（不设 disabledWithoutDocker）。注入 SQL 故障时可沿用 `PersistenceServiceIT` 的测试专用 trigger 或 DataSource 代理；必须在真实 MySQL 事务内产生故障，不用纯 mock／H2代替。

| 套件／场景 | 必须观察的结果 |
|---|---|
| TaskParametersJsonTest | `{}`、中文、引号、反斜线、数值精度、嵌套对象／数组／null 值语义往返；拒绝SQL null输入、JSON null／scalar／array根、非法JSON、顶层和嵌套重复键、尾随文档、POJO／循环；所有失败无原输入／cause；输入和嵌套修改不影响输出，输出递归不可改 |
| RetryTaskStorageServiceTest | 非法分页／错误码／null／外层活动事务在SQL前拒绝；创建返回 UUID 必须在 commit 正常返回后；模拟事务管理器 commit 抛错（含内部提交完成再抛）时不返回 SavedFailure、固定 TASK_RECORD_SAVE_UNCONFIRMED、零重试；未知SQL异常含凭证哨兵时公开异常无哨兵／SQL／cause |
| 首次创建真实事务 | 正常 create 恰一主一子，值正确；先插入主表后触发首明细 INSERT失败，另一个独立 JDBC连接确认两表该任务都没有，已有任务不变。单测 mock 不能作为此验收 |
| 追加回滚 | 创建已保存任务与首项；append 先成功插入第二项，再用只对该任务主表 UPDATE生效的 trigger `SIGNAL SQLSTATE '45000'` 使touch失败；独立连接确认新增项消失、原项及主表原JSON／created_at／updated_at完全保留。再对重复项原因更新→touch失败做同样断言，证明旧原因没被部分覆盖 |
| 原因与完整键 | 原1～10日，3／7日追加及同完整key重复更新；主表一直只有一条且日期不变。受控两股同日各一项，updateReason仅改变指定股票；不同taskId／target_type／target_value／time_type／time_value的键各自独立；缺项 updateReason 不重建项，缺主表 append 不重建任务 |
| 数据库约束 | 原生JSON拒绝非法文本；CHECK拒绝JSON null／字符串化对象／数组／标量，NOT NULL拒绝SQLNULL，正常JSON_TYPE=OBJECT；直接SQL相同五键插入违反PK，两股同日合法。孤儿明细违反FK，主表有子项直接删除违反RESTRICT；REQUEST／NONE空串可作PK，错误类型／空串组合违反CHECK。Java selector拒绝非规范股票／非法日期／未知类型，不宣称DB完整解析日期 |
| JSON实际落库语义 | RetryTaskStorageIT通过真实setString→MySQL JSON→getString路径，验证整数42、小数1.25、1.2300（允许读回1.23）、嵌套List／对象／Boolean／null语义保留。分别传 BigDecimal `12345678901234567890.123456789` 与 BigInteger `18446744073709551617`（超过uint64且不能被DOUBLE精确保留），断言回读检测有效数字变化、固定无cause非法值异常、未返回SavedFailure；独立连接确认该次主／明细均不存在、旧任务完整不变。包含嵌套有损数字反例以证明递归校验；不以Java codec单测冒充MySQL任意精度支持，不对所有超过uint64数值一律拒绝，仅按实际语义比较判定 |
| 事务原语 | 无活动事务调用锁／写原语失败且零SQL；同DataSource外层TransactionTemplate先锁后deleteItem，再抛错，独立连接见原项仍在；单独验证删除精确股而其他股保留、hasItems准确、deleteEmptyTask仍有项返回0。测试对测试数据手动组合删除和回滚，不连接PersistenceService，不冒充T11业务＋删除原子性验收 |
| 锁生命周期 | 两个连接竞争同主表：第一事务lockTask后第二个任务写入等待，直到第一提交／回滚才完成；采用latch及有限future timeout，验证锁不提前释放。另一taskId可独立锁定；不增加全局数据库锁 |
| UTC | 固定 Clock 的 `.123456Z`落库 `.123Z`，主／明细一致；在JVM非UTC及连接session `+08:00`的条件下读写仍等于相同UTC Instant，直接读取DATE_FORMAT看到UTC墙上时间；测试finally恢复JVM时区，不污染其他测试 |
| 列表／详情 | 创建至少23任务，包含相同updated_at的固定UUID组；默认20，50／100可用，page=2剩3条，超尾页归2，空归1且总页0；按时间DESC再UUID文本DESC跨页无重复／丢失。plugin-only、api-only、组合、不存在合法标识、元数据未注册插件均正确；page0／非法size拒绝，SQL注入字符标识拒绝；全部子项完整且顺序固定，同日两股不合并；find不存在返回empty |
| 冻结与旧记录 | 用T05真实converter的受控ApiDescriptor生成REQUEST／STOCK公共map，再create／append／update／read；原始两端和公共值逐键不变，STOCK不重复ts_code。无条件{}、旧缺两端和单边日期仍可read，T05 mapRetry分别兼容／拒绝；读取不发来源调用、不覆盖JSON |
| 重建服务读取 | 已提交数据在销毁并新建repository／service后仍可读，未提交连接回滚后没有半条任务；不增加启动扫描。这是持久化证据，不声称已实现进程中断恢复 |
| 迁移与旧数据 | DownloadFailureMigrationIT的生产新库／V7升级及原行快照；FlywaySchemaContractIT的完整52表、两管理表约束和49业务定义；DividendBusinessKeyMigrationIT仍覆盖V7历史行及故障。生产包含V8且不含V6／fixture，acceptance保持生产字节并只增加fixture／V6 |

从仓库根依次实际执行：

```sh
mvn -f data-plane/pom.xml -pl tensor-core -am test
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RetryTaskStorageIT,DownloadFailureMigrationIT,FlywaySchemaContractIT,DividendBusinessKeyMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=PersistenceServiceIT,ExistingKeyRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

默认 verify 的 Failsafe 只执行打包合同，不能代替两条显式 `-Dtest=...IT`。`failIfNoSpecifiedTests=false` 仅允许 reactor中不含指定类的上游模块继续；最终必须核对六个指定IT的Surefire XML均存在、tests>0、failure/error/skipped均0，并记录真实计数。若Docker不可用或受环境限制而未运行，不得把本任务标COMPLETED，不得写“数据库验证通过／自动跳过也通过”。在下一次 clean前将对应报告的命令、时间、实际类名、测试数及结果整理到验证文档或复制临时证据目录，避免 acceptance clean 抹掉唯一的IT记录。

前述命令均须退出0。按实际授权环境处理Mockito附加／Docker权限，不改依赖／关闭断言规避失败。重新核对旧迁移与49 YAML／策略摘要完全一致，检查分支未变、既有暂存未被撤销；只对新增变更或失败补跑相关检查。浏览器和真实业务API本项不调用，受控STOCK不升级49项生产REQUEST或取全／日历证据。

## Acceptance

1. 唯一V8只新增两表，精确字段、JSON对象CHECK、联合PK、非级联FK、collation、UTC精度和列表索引均在MySQL验证；生产七迁移／51表、测试及acceptance八迁移／52表，49业务定义与历史数据不变。
2. 首次一主一子同事务；追加、重复原因更新与主表更新时间同事务。实测首次及追加后半段SQL失败全回滚，之前已保存记录保留，不产生半条结构。
3. 完整五列键贯穿存在检查／更新／删除；同日两股与不连续日期独立。JSON为对象且不可变，原始1～10日不会被3／7日追加或重试原因更新覆盖；无任务参数更新接口。
4. 仅正常事务提交返回SavedFailure，保存SQL／提交异常安全报告未确认且不自动重试／补造。未知、未开始、检查拒绝不会被允许错误码伪装成明确失败。
5. 默认20、20／50／100分页、独立筛选、超尾／空页规范及双DESC顺序有实际证据；下线插件记录可读，不依赖当前元数据，不推算历史计数或原始区间。
6. T11所需原语可参与同连接外层事务，锁到事务结束、回滚保留原项；本项未实现业务＋删除组合、下载循环或HTTP入口切换。
7. 规定命令与实际MySQLIT均通过，报告与追踪仅确认AC-PRD-RANGE-21／22／26存储部分；新增文件已加入Git且原分支／已有暂存保持。按Order完成T11设计／交接准备，不自动实施后继。

## Risks

- 没有阻塞本项设计的缺失产品／来源事实；实施选择已固定。T04／T05的来源、日历和独立恢复缺口不影响两表存储可实施性，也不能被本项受控数据库测试关闭。
- MySQL DDL不是应用事务；首次业务失败的主／子写入原子性与迁移执行原子性是不同边界。V8不提供自动DDL补偿，迁移失败按现有Flyway运维处理，不能自动删旧表／数据。
- MySQL JSON入库会规范化且数值表示有限；应用严格对象解析并在首次保存事务内回读比较，拒绝语义损失。外部SQL已丢失的重复键或数值精度无法事后追溯；Java codec精度不代表数据库任意精度承诺。
- 保存commit异常可能已经落库；服务统一“不确认”，未来T12须停止后续执行、不能报告未返回taskId为确认保存。T11的业务提交未知分类仍是后继范围。
- 当前运行下载入口仍是原sourceParameters单次路径。三个bean就绪不表示完整区间或失败任务HTTP已开放；T12～T14负责接入。本文件是后继设计准备，不表示T10实现／测试已开始。
