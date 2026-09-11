# ISSUE-018-T02：任务与批次模型、迁移和仓储

任务：[ISSUE-018 看板 T02](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t02)。直接依赖：[T01 合同设计](ISSUE-018-T01-design.md)。共同约束：[总体设计](ISSUE-018-design.md) §3.6–3.11、§3.13–3.14、§4、§5.1。

## 做什么

将规范化请求、任务状态、执行许可、完整计划和批次树持久化在 MySQL，通过有条件的 JDBC 操作保护状态、版本、计数与原子性。后续接收、提交证券数据、worker 和生命周期任务直接消费该仓储。

新增两张表、领域记录、受限 JSON / SHA-256 工具和仓储；更新迁移与包结构精确断言。接收锁 / 能力判断属于 T03，证券数据与成功状态共同提交属于 T04，实际执行 / 重试 / 恢复协调属于 T07 / T08，HTTP 属于 T09。本项不装配或启动 worker，不调用插件，不改 V1–V7 或证券数据集注册。

## 怎么做

### 文件落点与依赖方向

- `data-plane/tensor-app/src/main/resources/db/migration/V8__create_download_task_tables.sql`：唯一新增生产迁移。
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTask.java`、`DownloadBatch.java`：不可变数据库事实记录，各自嵌套 Status；同包 `DownloadTaskRepository.java`：固定表名的 JdbcTemplate 查询、条件写入和事务；`DownloadTaskJson.java`：规范化 JSON、策略快照和摘要。
- 辅助输入 / 结果 record（NewTask、NewBatch、ExecutionPermit、StoredError、Counts、Page、筛选条件）放在 Repository 内作为公共嵌套类型，不建立通用仓储框架。StoredError 只接收 ErrorCode，按本文件固定文案生成 message，不允许传入原始异常文本。
- `data-plane/tensor-core/pom.xml` 增加 BOM 管理的 `com.fasterxml.jackson.core:jackson-databind`；不增加 Web、Flyway 或具体插件运行依赖。
- 新增 core `download/task/DownloadTaskJsonTest.java`、`DownloadTaskRepositoryIT.java`；扩展 app `db/FlywaySchemaContractIT.java`、`build/PackagedJarContractTest.java`、`build/AcceptancePackagedJarContractTest.java`。core IT 使用 app 生产 V8 文件，通过从模块目录解析 `../tensor-app/src/main/resources/db/migration/V8__create_download_task_tables.sql` 后 ScriptUtils 执行，避免复制另一份测试 DDL；完整 Flyway 新建 / 升级验证放 app。

### V8 DDL

两表均 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs`，不使用 MySQL ENUM、级联删除、SQL 自动更新时间或自动增量 ID。列顺序按下表，未列默认值则不设置 DEFAULT；可空列初始传 null。

| tensor_download_task 列（24 列） | SQL 类型 / 约束 |
| --- | --- |
| task_id | CHAR(36) NOT NULL PRIMARY KEY |
| submission_id | CHAR(36) NOT NULL |
| request_hash | CHAR(64) NOT NULL |
| plugin_id, api_name | 各 VARCHAR(64) NOT NULL |
| mode | VARCHAR(8) NOT NULL |
| params | JSON NOT NULL |
| definition_hash | CHAR(64) NOT NULL |
| policy_snapshot | JSON NOT NULL |
| status | VARCHAR(24) NOT NULL |
| plan_ready | BOOLEAN NOT NULL DEFAULT FALSE |
| active_run_id | CHAR(36) NOT NULL |
| run_generation | INT NOT NULL DEFAULT 0 |
| version | BIGINT NOT NULL DEFAULT 1 |
| request_count, run_request_count | 各 BIGINT NOT NULL DEFAULT 0 |
| last_error_code, last_error_message | VARCHAR(64) NULL / VARCHAR(512) NULL |
| created_at, updated_at, queued_at | 各 DATETIME(3) NOT NULL |
| started_at, finished_at, deadline_at | 各 DATETIME(3) NULL |

| tensor_download_batch 列（19 列） | SQL 类型 / 约束 |
| --- | --- |
| batch_id | CHAR(36) NOT NULL PRIMARY KEY |
| task_id | CHAR(36) NOT NULL |
| parent_batch_id | CHAR(36) NULL |
| batch_key | VARCHAR(128) NOT NULL |
| range_start, range_end | 各 DATE NULL |
| source_params | JSON NOT NULL |
| status | VARCHAR(16) NOT NULL |
| attempt_count | INT NOT NULL DEFAULT 0 |
| run_generation | INT NULL |
| source_rows, inserted_rows, updated_rows | 各 BIGINT NOT NULL DEFAULT 0 |
| error_code, error_message | VARCHAR(64) NULL / VARCHAR(512) NULL |
| created_at, updated_at | 各 DATETIME(3) NOT NULL |
| started_at, finished_at | 各 DATETIME(3) NULL |

索引与外键名字固定：

```sql
-- task，除 PRIMARY 外 4 个索引
UNIQUE KEY uk_download_task_submission (submission_id)
KEY idx_download_task_queue (status, active_run_id, queued_at, task_id)
KEY idx_download_task_created (created_at, task_id)
KEY idx_download_task_source (plugin_id, api_name, created_at, task_id)
-- batch，除 PRIMARY 外 3 个索引
UNIQUE KEY uk_download_batch_key (task_id, batch_key)
KEY idx_download_batch_status (task_id, status, batch_key)
KEY idx_download_batch_parent (parent_batch_id)
CONSTRAINT fk_download_batch_task FOREIGN KEY (task_id)
  REFERENCES tensor_download_task(task_id) ON DELETE RESTRICT ON UPDATE RESTRICT
CONSTRAINT fk_download_batch_parent FOREIGN KEY (parent_batch_id)
  REFERENCES tensor_download_batch(batch_id) ON DELETE RESTRICT ON UPDATE RESTRICT
```

每表添加命名 CHECK：task 前缀 `ck_download_task_`，batch 前缀 `ck_download_batch_`。

- task `mode` 仅 SINGLE / RANGE；`status` 仅 QUEUED / RUNNING / SUCCEEDED / PARTIAL_FAILED / FAILED / INTERRUPTED；`plan_ready` 仅 0 / 1；`counters` 限制 generation >= 0、version >= 1、request_count >= 0、0 <= run_request_count <= request_count。
- task `params`、`policy` 分别验证 JSON_TYPE 为 OBJECT；`error` 要求 error code / message 同空或同非空。
- batch `status` 仅 PENDING / RUNNING / SUCCEEDED / FAILED / SPLIT；`range` 要求两端同空，或均非空且 start <= end；`counters` 要求 attempt_count 和三计数非负、generation 空或 >= 1。
- batch `params` 要求 JSON_TYPE 为 OBJECT；`error` 要求 code / message 同空或同非空；`parent` 要求 parent_batch_id 空或与 batch_id 不同；`success_counts` 要求非 SUCCEEDED 时三计数均为 0。

UUID / hash 字符串规范、业务参数大小、父子同任务、任务 mode 与批次日期是否匹配由 Java 边界校验；CHECK 不跨表，不用 JSON_STORAGE_SIZE 的 MySQL 二进制大小替代本设计的 UTF-8 JSON 字节限制。状态转换依赖条件 SQL，而非仅依赖 CHECK。

### Java 事实记录与规范化

DownloadTask 按 task 表完整映射：taskId / submissionId / activeRunId 为 UUID，来源为 DatasetKey（映射 plugin_id 和 api_name），mode 复用 T01 DownloadMode；params 为不可变 Map<String,Object>；policySnapshot 为已验证规范化 JSON 字符串；两个 hash 为 64 位小写十六进制；状态为嵌套 Status；generation 为 int、version / 两请求计数为 long；错误两列组合为可空 StoredError；时间为 Instant。DownloadBatch 同样完整映射：UUID 三身份（parent 可空）、batchKey、可空 DateRange、不可变 sourceParams、Status、attemptCount int、可空 Integer generation、三 long 计数、可空 StoredError、四个 Instant 时间。

ExecutionPermit 固定为 `(UUID taskId, UUID activeRunId, int runGeneration)`，generation >= 1；它不是认证凭证，SQL 必须同时验证 task.status=RUNNING。StoredError 的错误码覆盖现有 ErrorCode，message 采用 GlobalExceptionHandler 当前固定英文文案的核心层对应映射；core 不导入 app。该映射以 `docs/contracts/error-codes.md` 的含义为准，不能从 Throwable.getMessage() 获取。读取未知状态 / 错误码 / 非法快照要明确失败，不默认成成功。

时间写入前截断到毫秒，通过 `LocalDateTime.ofInstant(value, UTC)` 及 JDBC 4.2 setObject / getObject(LocalDateTime.class) 绑定 DATETIME；读取用 toInstant(UTC)。DATE 使用 LocalDate。禁用 JVM 默认时区推断，参数日期仍是 YYYYMMDD。

NewTask 输入：`taskId, submissionId, datasetKey, mode, normalizedParams, definitionHash, policySnapshot, activeRunId, now`；requestHash 由仓储委托 JSON 工具计算，不接受外部提供。插入固定 QUEUED、planReady=false、generation=0、version=1、请求计数 0、created/updated/queued=now，其他时间及错误为空。只通过数据库 submission_id 唯一约束裁决重复；DuplicateKeyException 交 T03 用键重新查找及比较请求，禁止 Upsert 覆盖旧任务。

NewBatch 输入：`batchId, batchKey, DateRange range, Map<String,Object> sourceParams`。taskId / parent 由当前仓储操作指定，不接受与父任务无关的 ID；初始 PENDING、attempt=0、generation / 错误 / 开始结束为空、计数 0。根 key 为六位从 000001 递增的路径，子 key 固定 parentKey + `/0` 和 `/1`。完整路径仅 `[0-9]{6}(?:/[01])*`、长度 <= 128；参数 / 数据集身份使用已有值对象限制。

### 受限 JSON 与 SHA-256

DownloadTaskJson 使用私有 ObjectMapper / JsonFactory，严格重复键检测、禁止 trailing tokens、不启用 default typing，不序列化任意 Java 对象或类名。公开方法：

```java
String writeTaskParams(Map<String, Object> normalized); // <= 8192 UTF-8 bytes
String writeBatchParams(Map<String, Object> normalized); // <= 16384 UTF-8 bytes
Map<String, Object> readTaskParams(String json);
Map<String, Object> readBatchParams(String json);
String policySnapshot(DownloadMode mode, BatchDownloadDescriptor range); // <= 16384 UTF-8 bytes
String validatePolicySnapshot(String json); // 校验并重排对象键，返回规范化字符串
String requestHash(DatasetKey dataset, DownloadMode mode, Map<String, Object> normalized);
String definitionHash(ApiDescriptor selectedApi, DatasetDefinition dataset,
                      DownloadMode mode, BatchDownloadDescriptor range);
```

参数只接收已经由 ParameterValidator 规范化的字符串值，key 符合现有参数标识规则。拒绝 null、数字、布尔、集合、对象及凭证 key；凭证 key 以不区分大小写的 `token, access_token, refresh_token, authorization, password, secret, api_key` 精确集合拦截。对象按 key 字典序输出紧凑 JSON，无 BOM / 末尾换行；业务大小限制始终按重排键后紧凑JSON的UTF-8字节计算，读写都检查，等于上限允许，超过拒绝。数据库JSON列读出的文本可能带MySQL增加的空格，不能在规范化前直接套用8/16KiB业务限制；所有JSON读取在解析前先执行独立的128KiB防御上限，解析后再规范化并检查各自业务上限，最大嵌套深度16。128KiB只是有界解析保护，不提高可持久化业务载荷上限。参数快照以白名单字符串 map 验证；策略按以下固定字段 schema 验证，拒绝未知字段和超长 / 类型错误。不能以截断参数或删除未知字段使写入成功。

策略 JSON 用 `schemaVersion:1`，`mode` 字符串。SINGLE 只允许这两个字段，range 参数必须为 null，表示一次请求，不伪装成完整历史。RANGE 附带 `parameters,startParameter,endParameter,dateAxis,dateLabel,planningMode,splittable,availability,unavailableReason,policyVersion,completenessRule`，值来自 T01 BatchDownloadDescriptor，读回时逐字段构造该 record 复验不变量。parameters 保留原数组顺序，每项完整保存 ParameterDescriptor 的 9 个组件；空可选字段显式 null，allowedValues 按字符串排序。嵌套 completenessRule 固定 `kind,rowLimit,evidence`，依据 UNKNOWN / CONFIRMED_ROW_LIMIT / VERIFIED_RULE 使用 T01 构造规则。

requestHash 输入是紧凑规范化 JSON 对象 `{pluginId,apiName,mode,params}`，SHA-256 输出 64 位小写 hex；不包含 submissionId、时间或凭证。相同规范化 map 的插入顺序不影响摘要；工具不自行猜测股票 / 日期语义。

definitionHash 输入采用 schemaVersion=1 的显式结构：datasetKey / tableName、mode、selectedApi.queryMode、所选参数合同、RANGE 策略语义或 SINGLE 规则、来源列与业务键。参数合同包含 `name,type,required,defaultValue,allowedValues,pattern,relatedParameter` 并保留数组顺序；列按定义顺序包含 `name,logicalType,nullable,displayOrder,length,precision,scale,allowedValues`；业务键包含 mode 和有序 fields。RANGE 策略语义包含端点名、dateAxis、planningMode、splittable、policyVersion、completenessRule；不包含 presentation label / description、availability、凭证、启用状态、过滤 UI、数据库 batchSize 或任务资源预算。可用性变化由接收 / 重试检查负责，凭证修复不使旧定义过期；日期轴 / 规则 / 字段 / 业务键 / 参数数组顺序变化必须改变 hash。若 selectedApi.apiName 与 dataset.datasetKey.apiName 不一致，或 selectedApi.parameters 与 RANGE descriptor.parameters 不一致，则拒绝；SINGLE 要求 range=null。

写入边界输入不合格抛 IllegalArgumentException，不能带输入正文；T03 接收时转换适当公共错误。读取已存损坏 JSON 和数据库读取失败包装 QUERY_FAILED，写入 / 事务失败包装 PERSISTENCE_FAILED，所有消息固定且脱敏。唯一键冲突与显式状态冲突保持可区分，不包成不可识别的 500。

### 仓储操作与事务边界

构造器：`DownloadTaskRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions, DownloadTaskJson json)`。使用固定表名、固定枚举列名和参数绑定，动态查询仅拼接预定义筛选子句；不用用户值形成 SQL 标识符。除下述三个T04低层参与方法外，所有公共仓储操作在进入模板前拒绝调用方已有数据库事务（检查isActualTransactionActive，抛IllegalStateException），确保自己拥有完整事务边界。内部写TransactionTemplate REQUIRED、60秒，读快照模板只读REPEATABLE_READ、60秒；高层操作只能在自己的事务提交之后返回，不能继承调用方隔离级别或超时。模板内部组合查询使用私有JdbcTemplate辅助方法，不重入公共事务入口；T04低层方法是唯一允许参与调用方事务的公共例外。

公开查询：

- `Optional<DownloadTask> findTask(UUID taskId)`、`findSubmission(UUID submissionId)`；找不到返回 empty，由上层决定 404。
- `long queuedCount()`；`List<DownloadTask> queuedTasks(UUID activeRunId, int limit)` 按 queued_at,task_id 升序；`List<DownloadTask> unfinishedTasks()` 只读 QUEUED / RUNNING 候选，由生命周期判断哪些执行器已退出，不在仓储推断存活。
- `Page<DownloadTask> tasks(TaskFilter filter, int page, int pageSize)`，TaskFilter 可空 pluginId/apiName/status/submissionId；page>=1、pageSize 为20/50/100；按 created_at DESC,task_id DESC；where 及 count 一致。
- `Page<DownloadBatch> batches(UUID taskId, BatchFilter filter, int page, int pageSize)`，BatchFilter 为可空 status 和 includeSplit 布尔；默认排除 SPLIT，按 batch_key ASC，父批显式查询时才能出现；`List<DownloadBatch> pendingBatches(UUID taskId)` 同序。
- `Counts counts(UUID taskId)`：totalBatches 只含叶子；pending/running/succeeded/failed 分别计数，splitBatches 单列；三行数仅 SUM SUCCEEDED。空集合返回 0；不从零批次推断任务成功。
- `TaskSnapshot snapshot(UUID taskId)` 在同一只读一致性事务返回 Optional task 与 counts；分页的 total 和 items 也必须同一个只读快照。TaskSnapshot 为 Repository 嵌套 record。

状态写入使用以下固定操作，不提供任意 updateStatus / 任意列 Map：

| 方法 / 输入 | 先决与原子效果 |
| --- | --- |
| `DownloadTask insert(NewTask input)` | 规范化 / 大小检查后 INSERT；事务提交成功才返回任务。唯一键冲突保持 DuplicateKeyException。 |
| `Optional<DownloadTask> claimTask(UUID id, UUID activeRunId, Instant now, Instant deadline)` | 条件为 QUEUED 且 active_run_id 匹配；写 RUNNING，generation+1，version+1，started/deadline，finished=null，run_request_count=0，updated=now；deadline>now。失败返回 empty。 |
| `Optional<DownloadBatch> claimBatch(ExecutionPermit permit, UUID batchId, Instant now)` | 锁任务验证许可，再锁同任务 PENDING 批次；写 RUNNING、attempt+1、generation、started=now、finished=null、清批错误；失败返回 empty，许可过期抛 TASK_STATE_CONFLICT。 |
| `void reserveRequest(ExecutionPermit permit, long maxRequests, Instant now)` | 锁任务许可，now 必须早于 deadline，run_request_count<maxRequests，递增两个请求数及 updated；超限抛 TASK_LIMIT_EXCEEDED，不递增、不调用上游。停止信号由上下文调用者处理。 |
| `void savePlan(ExecutionPermit permit, List<NewBatch> roots, int maxNodes, Instant now)` | 锁任务许可且 plan_ready=false、当前无节点；校验整个输入、原子插根批与 plan_ready=true / updated。计划允许合法空列表；覆盖核验由 T07，仓储仍验证 SINGLE 恰好1条无日期且参数等于请求，RANGE 每条非空范围在请求端点内。 |
| `void split(ExecutionPermit permit, UUID parentId, NewBatch left, NewBatch right, int maxNodes, Instant now)` | 锁任务后父批，父 RUNNING 且 generation 匹配，mode=RANGE；子 key固定、范围严格等于父闭区间的有序无重叠分割、均缩小，父 range 不能同日；节点数+2<=maxNodes。父 SPLIT、finished=now、三计数0，两子 PENDING 同事务；任一失败回滚全部。 |
| `void failBatch(ExecutionPermit permit, UUID batchId, ErrorCode error, Instant now)` | 锁任务后批次验证同任务 / RUNNING / generation，写 FAILED、固定脱敏错误、finished/updated，三计数0；许可 / 状态不匹配抛 TASK_STATE_CONFLICT。 |
| `void finishTask(ExecutionPermit permit, DownloadTask.Status target, ErrorCode error, Instant now)` | target 仅 SUCCEEDED/PARTIAL_FAILED/FAILED；锁许可，version+1、终态和错误/finished/updated。SUCCEEDED 必须 plan_ready 且全部叶子 SUCCEEDED（允许已规划0批）；PARTIAL_FAILED 必须至少1成功叶子且有未成功叶子；FAILED 必须无成功叶子且未满足成功条件；RUNNING 批次尚存则拒绝。 |
| `void requeue(UUID id, long expectedVersion, UUID newRunId, RequeueMode mode, Instant now)` | 锁任务后按 batch_key 锁批次；RETRY只允许FAILED/PARTIAL_FAILED，重排全部FAILED；RESUME只允许INTERRUPTED，仅重排 EXECUTION_INTERRUPTED 的FAILED；PENDING保留，SUCCEEDED/SPLIT及普通失败不改。任务QUEUED、新启动ID、version+1、queued/updated=now，清任务错误和本轮 started/finished/deadline；generation、attempt和两请求计数保留，直到下一次领取才改变本轮计数。容量 / 无活动worker / 当前定义与能力由 T03/T08 调用前验证。 |
| `DownloadTask recoverStoppedTask(UUID id, UUID expectedRunId, int expectedGeneration, long expectedVersion, Instant now)` | 仅QUEUED/RUNNING且四条件匹配，先任务后批锁；若plan_ready且全部叶子成功（含合法0批），重算任务SUCCEEDED；否则RUNNING批改FAILED/EXECUTION_INTERRUPTED且清计数、其他批保留，任务INTERRUPTED及固定错误。两分支都写finished/updated、version+1。只供已确认worker停止的协调器调用；不能从数据库状态自行推断停止，也不发上游请求。 |

maxNodes 包含 SPLIT 父节点；plan / split 插入前在任务行锁内读取 count，竞争不能绕过上限。计数溢出、version / generation / attempt达到Java有符号上界应拒绝该转换为 TASK_LIMIT_EXCEEDED，不让 SQL 回绕。claimBatch、reserveRequest、savePlan、split 在许可通过后还应检查 now<deadline，等于deadline也拒绝；failBatch / finishTask允许在截止后记录失败及终态，但仍验证执行许可。succeedBatch由T04在进入证券事务前检查截止，已经开始的事务允许在自身60秒期限内结束。条件失败不做部分写入。

T04 需要参与证券事务的低层操作，提供 `DownloadTask lockTask(ExecutionPermit permit)`、`DownloadBatch lockBatch(ExecutionPermit permit, UUID batchId)` 和 `void succeedBatch(ExecutionPermit permit, UUID batchId, long sourceRows, long insertedRows, long updatedRows, Instant now)`。三者要求 `TransactionSynchronizationManager.isActualTransactionActive()`，缺少外层事务则立即 IllegalStateException；不自行打开 / 提交事务。锁任务→批次，同任务 RUNNING 和 generation 条件再次检查。成功写三计数、SUCCEEDED、finished/updated和清错误。核心不得在持有任务行锁时取得 DatasetLock；T04 在取得数据集锁之后建立事务。本项IT只证明状态操作可以加入调用方事务并整体回滚，不宣称证券写入原子性已经实现。

finish / requeue / recoverStoppedTask 的状态与版本条件异常统一 TASK_STATE_CONFLICT；Unknown task 由 findTask + 上层404区分。T08确认worker已停止后调用recoverStoppedTask；仓储在锁内从plan_ready和叶子事实重算，包括旧QUEUED / RUNNING，不根据一次锁外snapshot决定成功。该方法不实现启动扫描或判断worker生死。批次失败记录与证券事务分开由T04 / T07负责。

### 迁移与打包精确断言

生产 V1/V2/V3/V4/V5/V7/V8 共7份，测试另含 V6 共8份。49张历史来源表保留，生产加两张任务表为51张，测试再加fixture为52张；Flyway history 表不计入业务表。测试表列总数1008+24+19=1051，主索引52，非主索引41+4+3=48。按命名索引精确检查唯一性和列顺序，不能只放宽总数。

40个注册证券数据集、789声明业务列、912生产注册表实际列、34个注册表非主索引维持原断言。任务表单独验证全部43列 / 类型 / null / 默认 / CHECK / FK / 索引，扩展 schema inspector 测试映射支持任务JSON列，不把任务表塞入 DatasetDefinition。

新增升级场景：同一个MySQL容器的独立schema先迁至7，记录已应用迁移checksum并插入一个证券数据样例，再加载8；只执行1次迁移，证券样例保留、两新表存在，旧checksum完全不变，重复执行0迁移。新建测试schema首次8次迁移。生产包必须包含V8且不含V6；验收包保留生产V8字节、只额外加入fixture及V6。

## 如何测试

### JSON / 领域单元

`DownloadTaskJsonTest` 验证插入顺序不同的 map 得到相同固定hash（以手算固定JSON字符串的已知SHA-256字面值作期望），值 / 来源 / mode 改变则hash改变。验证字段、业务键、日期轴、策略version、完整性rule和参数顺序改变影响definitionHash，凭证 / 启用 / presentation / batchSize不参与。请求8KiB、批次16KiB、策略16KiB的等于与+1边界，中文多字节、未知字段、重复键、null / 数字 / 嵌套值、trailing token、深度限制和秘密key拒绝且异常不泄漏输入。数据库带空白JSON输入在128KiB解析保护内按规范化结果检查业务限制，超过128KiB在解析前拒绝。RANGE通过T01构造约束，SINGLE不能携带区间承诺。

### MySQL 8.4.6 仓储IT

使用 Testcontainers，不以 H2 替代。测试调用真实V8和JdbcTemplate；不通过mock仓储声明事务已验证。

1. 插入 / 读回全部字段、毫秒UTC（JVM或连接时区改变后仍相同）、SINGLE空日期与RANGE闭区间；FK / CHECK / submission唯一 / task+key唯一真实拒绝非法SQL。任务参数8KiB、批次参数16KiB和策略16KiB规范化边界值分别写入JSON列再读回，MySQL文本空白不影响结果；规范化后+1字节仍拒绝。
2. 两个事务并发相同submission，仅一条任务持久化；另一条唯一冲突，原快照不被覆盖。相同batchKey在不同任务合法，跨任务parent经仓储拒绝。
3. 同一任务并发claim只一成功；旧启动ID、旧generation、旧version和不允许状态更新无副作用；reserve的最后允许次数和下一次超限，两计数一致。
4. 用测试专有MySQL触发器在第二条根批 / 第二条子批插入时 SIGNAL：初始计划失败留下0批且plan_ready=false；split失败父仍RUNNING且无子批。移除测试触发器再调用得到完整计划 / 两子批；生产代码不添加测试开关。
5. 已计划空RANGE与未计划空任务不同，SINGLE空计划拒绝；非法范围、重复key、超过node预算、跨任务批次拒绝。报告期跨年及闰日子区间覆盖无遗漏。
6. 三叶子一成功一失败一待执行，再有SPLIT父节点，断言叶子和三行数合计不含父；retry / resume只改允许批，成功计数和attempt保留，下一claim才增加attempt。
7. 外层TransactionTemplate里锁许可并succeedBatch，随后抛异常，批次状态和计数全回滚；成功提交后状态和三计数一起可见；三个T04参与方法无事务调用拒绝；高层insert / claim / snapshot在已有外层事务时拒绝且无写入，自己的提交后可由另一连接观察结果；错误与过期许可不覆盖新轮次。
8. 使用latch交错读取snapshot与split，返回的task/计数同一快照，页total与items一致；分页20/50/100、筛选、稳定排序、默认排除SPLIT与显式includeSplit正确。无需长sleep。
9. finish成功判定不接受未规划、失败 / 未执行 / 仍运行批；预算停止保留PENDING；recoverStoppedTask对未完成任务只改RUNNING批、保留普通FAILED和成功；已规划全成功的旧QUEUED / RUNNING只重算终态，无请求和重复计数，重排条件及版本冲突可观察。

### 命令

仓库根执行（Java21、Docker及MySQL8.4.6镜像、Node24.15.0）：

```sh
mvn -f data-plane/pom.xml -Dtest='DownloadTaskJsonTest,DownloadTaskRepositoryIT,FlywaySchemaContractIT' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

所有命令退出0且必选IT实际执行，无失败或静默跳过。`clean verify`不替代第一条IT。本任务不运行依赖main与干净HEAD的发布快照脚本来冒充当前工作树验证。

## 如何验证

T02看板Acceptance逐项有证据：V8真实新建 / 升级 / 重复迁移、原子计划 / 拆分、条件更新和已提交计数、稳定无凭证快照、独立查询任务表且保持40证券注册。生产 / 验收包迁移清单精确通过。实际命令、结果、已知限制只写看板和交接，不填入本设计。

先写T02完成证据，再按Order选择T03，完成其专属设计并链接后创建交接。只完成仓储测试不能宣称接收任务、后台执行或跨证券事务已交付。

## 依赖什么信息

- T01已实现的 `download/batch/DateRange`、`DownloadMode`、`BatchDownloadDescriptor` 及其嵌套CompletenessRule /枚举，定义规范化快照类型；不可将UNKNOWN变成已验证。BatchCallContext留给后续调用者，reserveRequest提供其持久化预算原语。
- T01参数数组方向有含义，生成和存储策略快照 / 摘要必须保留其顺序。app Codec仍只支持四类RANGE；core存储不硬编码ts_code/start_date字段，RANGE根范围从已存策略的startParameter/endParameter读取并严格解析YYYYMMDD。
- 总体设计确定两表、生命周期、错误规则和单实例约束；来源凭证不是持久化输入。任务行许可不能当多实例完整协调保证。
- 现有JdbcTemplate / TransactionTemplate / DataSourceTransactionManager与PersistenceService的60秒事务是事务基线；必须使用同一DataSource的事务资源参与T04。
- FlywaySchemaContractIT当前50表 / 1008列 / 50主索引 / 41非主索引及6生产+1测试迁移是升级基线；现有包合同分别证明生产排除fixture和验收只增fixture/V6。
- 外部API完整性和北交所日历仍待T13验证，本任务只保存明确策略，不开放RANGE。容量 / 插件启用 / worker实际退出属于上层条件，不由数据库静态状态推断。
