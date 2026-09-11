# ISSUE-018-T09：任务 HTTP 合同、应用装配与日志

## Goal

向页面和调用方提供完整的持久化下载任务 HTTP 合同：首次提交只等待接收事务，返回 202 和任务身份；随后查询如实显示后台结果，手动 retry/resume 使用客户端版本。生产应用显式接入 T08 生命周期，接收日志与实际执行、提交和完成日志分开。

任务身份来自 [ISSUE-018 看板 T09](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t09)，Order 9，直接依赖已完成 [T03](ISSUE-018-T03-design.md) 和 [T08](ISSUE-018-T08-design.md)。共享依据是[总体设计](ISSUE-018-design.md) §3.3、§3.11、§3.13、§4、§5.1。T08 已记录专项 184、全单元 947、生产 951、验收 954 项通过，四个生命周期各有前端 170 项及构建通过；这些证据不代表本项 HTTP 已实现。

## Scope

新增任务 Controller、严格请求绑定和白名单 DTO；在 DataSourceController 增加能力查询；绑定任务属性并装配同一数据库、runId 和设置；提供脱敏、提交后任务/批次事件及 HTTP 接收日志；落地 JSON Schema、示例、OpenAPI 和错误语义。允许为 HTTP 幂等绑定增加核心只读准备入口，为已提交事件增加向后兼容的仓储观察接口；不改变既有状态转换、事务归属和来源执行合同。

保留旧 `POST /api/v1/downloads`、40 项注册、34 项股票必填/6 项非股票规则、V1–V8 和单 worker。Tushare 34 项 RANGE 仍全部为 NEEDS_VERIFICATION，6 项仅 SINGLE；受控测试插件的 AVAILABLE 不开放生产策略。T10/T11 负责前端，T12 负责进程/浏览器闭环和发布门禁，T13 负责真实来源逐项开放。本项不增加取消、删除、定时、自动重试、重启自动续跑、多实例、多 worker、任务指标体系或外部日志传输。

## Approach

### 既有输入与最小扩展

已存在的公开用例：`DownloadTaskService.capabilities(DatasetKey)`、`submit(Submission)`、`retry(UUID,long)`、`resume(UUID,long)`、`controls(DownloadTask)`；`DownloadTaskQueryService.tasks(TaskFilter,int,int)`、`findSubmission(UUID)`、`detail(UUID)`、`batches(UUID,BatchFilter,int,int)`。Service 与 QueryService 均拒绝调用方外层事务，Controller 不加 `@Transactional`，不访问 Repository、插件、注册表或持久化协作者。

T08 的 `DownloadTaskConfiguration` 是 public final lite 配置，已有 runId、coordinator 和最大 phase 的 SmartLifecycle。本项由生产 `ApplicationConfiguration` 显式 `@Import(DownloadTaskConfiguration.class)`；保留其生命周期实现和关闭等待合同，不另加执行器或启动线程。`controls` 只是当前生命周期、设置、租约和状态组合的提示，不读插件/数据库；HTTP 操作继续由 retry/resume 完整复验。

### 七类路由与状态码

所有请求/响应继续经过 RequestIdFilter，`X-Request-Id` 与 ApiErrorResponse/接收响应的 requestId 一致；JSON 响应使用 `application/json`。未知查询参数、重复同名参数在对应路由绑定时拒绝，POST 不接受额外查询参数；详情和能力 GET 也不接受查询参数。

| 方法和 `/api/v1` 后的路径 | 入参 | 成功响应 |
| --- | --- | --- |
| POST `/download-tasks` | 下述 SubmissionRequest | 新任务 202；相同 submissionId 的等价重放 200；均返回 Receipt 和 `Location: /api/v1/download-tasks/{taskId}` |
| GET `/download-tasks` | 仅 page、pageSize、pluginId、apiName、status、submissionId | 200 `Page<TaskResponse>` |
| GET `/download-tasks/{taskId}` | 严格 UUID 路径 | 200 TaskResponse；未知 UUID 为 404 TASK_NOT_FOUND |
| GET `/download-tasks/{taskId}/batches` | 仅 page、pageSize、status、includeSplit | 200 `Page<BatchResponse>`；未知任务 404 |
| POST `/download-tasks/{taskId}/retry` | 仅 `{expectedVersion}` | 202 Receipt、同一任务 Location；FAILED/PARTIAL_FAILED 才允许 |
| POST `/download-tasks/{taskId}/resume` | 仅 `{expectedVersion}` | 202 Receipt、同一任务 Location；INTERRUPTED 才允许 |
| GET `/data-sources/{pluginId}/apis/{apiName}/download-capabilities` | 两个合法标识路径 | 200 CapabilitiesResponse；身份不存在沿用 core 的 409 DATASET_MISCONFIGURED |

Receipt 固定为 `{requestId,taskId,status,version,createdAt}`。首次创建采用 submit 返回的已提交记录，不为构造 Receipt 再查询或等待 worker；status 初始为 QUEUED，重放可以返回任何已有状态。retry/resume 使用用例回读结果，createdAt 保留原任务创建时间。到达客户端时 worker 可能已开始或结束，客户端以 GET 为准；202 绝不表示下载成功。

分页默认 `page=1,pageSize=20`，page 为 1..Integer.MAX_VALUE 的十进制整数，pageSize 仅 20/50/100；不接受空值、空白、符号、小数、指数、溢出、数组拼写或重复值。status 大小写严格，任务枚举为 QUEUED/RUNNING/SUCCEEDED/PARTIAL_FAILED/FAILED/INTERRUPTED，批次枚举为 PENDING/RUNNING/SUCCEEDED/FAILED/SPLIT。includeSplit 仅 `true`/`false`，默认 false；`status=SPLIT&includeSplit=false` 合法空页，显式 true 才显示父节点。pluginId/apiName 可独立组合筛选，不要求当前插件存在；submissionId 与其他筛选取 AND，查不到为 total=0/items=[]，不是 404。超尾页保留原 page，不折回最后一页。

UUID 文本严格为 36 字符 `8-4-4-4-12` 十六进制形式，接受大小写并规范化为小写，拒绝 Java UUID.fromString 容忍的缩写形式；请求 ID 头沿用 RequestIdFilter 自己的规则。pluginId/apiName 使用现有 `^[a-z][a-z0-9_]{1,63}$`。路径/查询格式错误统一 400 PARAM_INVALID，字段名固定且不回显原值。

### 提交绑定与历史幂等优先级

SubmissionRequest 必须是 JSON object，顶层恰好 `submissionId,pluginId,apiName,mode,params`；mode 必须显式为 SINGLE 或 RANGE，不设默认。params 必须是 object，键遵循既有参数名模式，值只能是 JSON string，显式 null、number、boolean、array、object 均拒绝，不能经过 Jackson 标量强制转换。启用 STRICT_DUPLICATE_DETECTION，重复顶层/params 字段、未知顶层字段、尾随第二个 JSON 值、非法结构均为 PARAM_INVALID；params 缺省/null、顶层必填值缺省/null 为 PARAM_REQUIRED。已出现非法顶层值时 PARAM_INVALID 优先于顶层缺失，fieldErrors 按字段名排序；普通结构错误固定 `request: has invalid value`。合法形状中的必填业务字段及日期/股票错误沿用 ParameterValidator 的既有优先级和字段顺序。

新增 `DownloadTaskRequestDeserializer`，复用现有 DownloadParameterResolver/ParameterJsonReader/唯一 Codec，不能直接套用先检查当前可下载性的旧 DownloadRequestDeserializer。新增核心公开只读入口：

```java
public SubmissionBinding prepareSubmission(Submission request);
public record SubmissionBinding(Submission submission, ApiDescriptor api, boolean replay) {}
```

prepareSubmission 拒绝外层事务，在同一 admissionLock 中先 findSubmission：存在则直接复用现有私有 replay 比较，成功后返回其已存规范化参数构成的 Submission、api=null、replay=true；身份/模式/等价性不匹配仍是 SUBMISSION_CONFLICT。RANGE 的同义比较继续使用 DownloadTaskJson 包内 readRangePolicy；SINGLE 使用仍保留的描述，永久移除描述时只允许原样规范化快照找回。不得在 app 复制旧策略解析、hash 或规范化算法。

新键分支检查已绑定协调器准入，再复用 current→normalize 和 8 KiB JSON 检查，返回规范化 Submission、选定的 ApiDescriptor、replay=false；不查容量、不 INSERT、不建立租约或调用来源。record 校验 replay 与 api 的空值关系，防御复制由 Submission 保证。此入口不是接收，锁释放后条件可以变化；Controller 最终必须再次调用 submit，仍由其在同一锁中复验既有键、状态、当前定义及容量。prepareSubmission 的新键结果不能缓存为长期授权。

`DownloadTaskRequest` 使用同文件 sealed 请求类型及两个内嵌 record：`Bound(submissionId,dataset,mode,DownloadParameters params,Set<String> suppliedFields)` 和 `Replay(Submission submission)`。新键将选定描述交给 `resolver.resolve(api, normalizedParams)`，得到具体参数类型，suppliedFields 取规范化参数的键，Controller 用 toRawValues 转回 Service 入参。已有键已由核心确认等价，使用 Replay 的不可变规范化 Submission；它只允许找回已存在的任务，不能因缺当前 Codec 而重绑定/新建。提交前再次 submit 的数据库查询是最终裁决者，任务不支持删除，因此已确认 Replay 不会合法变成新建分支。

这样既保持新请求的强类型绑定，也保留停用/关闭/恢复/FAULTED/定义移除后的已有任务找回。结构非法仍 400；合法形状但与旧请求不同的参数、未知业务字段、不可证明的同义值为 409 SUBMISSION_CONFLICT。所有核心 TensorException 在反序列化器转换为 `DownloadBindingException.from`，保证 HttpMessageNotReadableException 中的 QUERY_FAILED/PLUGIN_DISABLED/冲突等不被误映射成 PARAM_INVALID。

RANGE 使用独立 ApiDescriptor（QueryMode.date_range、range.parameters），不混入 SINGLE 的 trade_date/ann_date。四种已存在形状 DateRangeParameters、ExchangeDateRangeParameters、ExchangeIdDateRangeParameters、TsCodeDateRangeParameters 各唯一 Codec；不增加按插件/API 名分支。普通新插件只可使用已支持的形状，未知形状 DATASET_MISCONFIGURED、零任务；已存规范化 Replay 不受此限制。日期业务参数仍 YYYYMMDD，包含端点的自然日上限、未来日期、纯组合预检、生产 NEEDS_VERIFICATION/BSE/BJ 门禁均交现有 core/plugin 判断。

retry/resume 使用独立 `DownloadTaskControlRequestDeserializer`：body 是且仅是 `{expectedVersion}`，必须 JSON integer token，1..Long.MAX_VALUE；缺省/null 为 PARAM_REQUIRED，0/负数/字符串/小数/指数/boolean/溢出/未知或重复字段/非 object 为 PARAM_INVALID，不截断、不宽松转换。null task body 由 unreadable 边界返回固定 400。绑定器无状态转换；把客户端数值原样传给用例，不能替换成新查询版本。

### 白名单 DTO 与一致性

DTO 不直接序列化 DownloadTask、DownloadBatch、Repository.Page/Counts 或策略 JSON。`TaskResponse` 同时作为列表项和详情，避免两套计数映射；仅以下字段对外：

| DTO | 完整字段与类型 |
| --- | --- |
| Receipt | requestId/taskId：UUID string；status：任务枚举；version：正 JSON integer；createdAt：UTC 时间 |
| Page<T> | page/pageSize：JSON integer；total：非负 JSON integer；items：数组 |
| TaskResponse | taskId/submissionId、pluginId/apiName、mode、params（规范化 string map）、status、version、planReady；counts、lastError、canRetry/canResume；requestCount/runRequestCount；createdAt/updatedAt/queuedAt；可空 startedAt/finishedAt/deadlineAt |
| TaskCounts | totalBatches、pendingBatches、runningBatches、succeededBatches、failedBatches、splitBatches、sourceRows、insertedRows、updatedRows，全部非负 JSON integer |
| BatchResponse | batchId、可空 parentBatchId、batchKey；可空 rangeStart/rangeEnd；sourceParams；status、attemptCount；sourceRows/insertedRows/updatedRows；可空 error、startedAt/finishedAt；createdAt/updatedAt |
| StoredErrorResponse | code：完整 ErrorCode 枚举；message：StoredError.message() 固定分类文案。无原异常、cause、stack、retryable 或参数回显 |
| CapabilitiesResponse.single | available；parameters：复用 ApiDescriptorResponse.ParameterResponse.from，字段及省略 null 规则不变 |
| CapabilitiesResponse.range | availability、unavailableReason、dateAxis、dateLabel、startParameter/endParameter、parameters、planningMode、splittable、policyVersion、completenessRule |
| completenessRule | kind（UNKNOWN/CONFIRMED_ROW_LIMIT/VERIFIED_RULE）、可空 rowLimit、可空 evidence；使用实际 BatchDownloadDescriptor.CompletenessRule 枚举名称，不映射为无限量 |

TaskResponse 不暴露 requestHash、definitionHash、policySnapshot、activeRunId/runGeneration 等内部许可字段。SINGLE 批次两个范围字段均 null，RANGE 为 YYYY-MM-DD；其他时间是 UTC ISO-8601（Instant，允许毫秒），可空字段显式输出 null，业务参数保留 YYYYMMDD 字符串。sourceParams 仅输出已被仓储验证的非敏感业务快照。

版本、计数、total 使用 primitive long，page/attemptCount 使用 int；避免受 `JacksonPrecisionConfiguration` 中 Long.class→string 影响。可空 rowLimit 使用字段级 `@JsonSerialize(using=RowLimitSerializer.class)`；该 serializer 内嵌于 DownloadCapabilitiesResponse，继承 JsonSerializer<Long> 并明确调用 generator.writeNumber(value)，null 走默认 null 输出。不能改全局证券 LONG/DECIMAL 精度规则；必须使用真实 precisionModule 验证最终 JSON token 类型，schema 定义 int64 数值上界。JS 不安全整数的客户端显示/版本承载边界在合同中如实注明，不自行截断或改既定数值形状。

详情只调用一次 QueryService.detail，再用该 snapshot.task 和 snapshot.counts 映射，最后调用 controls(snapshot.task)；不单独拼接 findTask/counts。列表先 tasks(filter,page,size) 固定 total、成员 ID 和顺序，再对每个 ID 调 detail，全部行字段/计数来自各自同一快照，不沿用分页旧状态拼接新计数；最多 100 行。跨行不是同一时刻快照，筛选后的任务可在补详情时改变状态，但不得重新筛选、排序、补位或改变 total；任何详情读取失败使本次 GET 整体返回分类错误，不伪造/丢弃行。无删除入口，成员正常不会消失。该最小方案保留 QueryService 既有接口，接受有界 N+1，后续有实际性能证据再优化。

totalBatches=四类叶子数之和，splitBatches 独立；planReady=false 的 0 批次不推导 SUCCEEDED。累计 source/insert/update 保留 T08 语义；insertedRows/updatedRows 是已提交写入次数。controls 是瞬时提示，插件变化/容量/版本仍可能使后续操作拒绝。批次分页直接映射 QueryService.batches 的真实记录与 total。

能力查询直接使用 tasks.capabilities，不调用 MetadataQueryService.listApis 的可下载性过滤；已知但停用来源仍可返回 single.available=false 和明确不可用 RANGE，未知身份保持固定分类。能力不是接收承诺，任务 enabled=false 不删除元数据。

### 错误合同

复用 ApiErrorResponse `{requestId,code,message,retryable,fieldErrors}` 及 GlobalExceptionHandler 固定文案，不新增 ErrorCode。HTTP 状态完整分组：PARAM_REQUIRED/PARAM_INVALID→400；TASK_NOT_FOUND→404；PLUGIN_DISABLED/DATASET_MISCONFIGURED/SUBMISSION_CONFLICT/TASK_STATE_CONFLICT/TASK_DEFINITION_CHANGED/BATCH_DOWNLOAD_UNAVAILABLE→409；TASK_QUEUE_FULL→429；PERSISTENCE_FAILED/QUERY_FAILED/INTERNAL_ERROR→500；ADAPTER_FIELD_MISSING/ADAPTER_TYPE_INVALID→422；SOURCE_TIMEOUT→504；其余 SOURCE_AUTH_FAILED/PERMISSION_DENIED/RATE_LIMITED/UNAVAILABLE/NETWORK_ERROR/PAYLOAD_INVALID/RANGE_MISMATCH→502。意外到达 HTTP 的 BATCH_COMPLETENESS_UNCONFIRMED/TASK_LIMIT_EXCEEDED/EXECUTION_INTERRUPTED→409。

失败任务/批次作为查询数据仍是 HTTP 200，包括 SOURCE_TIMEOUT、持久失败和中断；GET 只在本次查询自身失败时返回 5xx。ErrorCode.retryable 原值是错误分类属性，不替代 canRetry/canResume。补正 error-codes.md 中 PERSISTENCE_FAILED “必已回滚”的过强文字：提交回执可能丢失，调用方应按 taskId/submissionId 查询，不保证记录未提交、不得盲目换键重发。

### 生产属性与装配

新增 `config/DownloadTaskProperties.java`，`@ConfigurationProperties("tensor.download-tasks")` 的构造绑定 record，用 `@DefaultValue` 并在 application.yml 明列同一组默认值：

| 属性 | 类型/默认值 | 启动校验 |
| --- | --- | --- |
| enabled | boolean / true | 关闭后仍创建仓储、查询和生命周期，启动恢复仍执行，禁止新接收/派发 |
| max-queued-tasks | int / 100 | >0 |
| max-range-days | int / 36600 | >0，与 Service/Runner 共用 |
| max-batch-nodes | int / 10000 | 1..999999，包含 SPLIT |
| max-requests-per-run | long / 5000 | >0 |
| max-run-duration | Duration / 30m | 非空、正、至少 1ms、toMillis 不溢出 |
| max-source-rows-per-task | long / 1000000 | >0，累计成功批 sourceRows |

属性构造校验委托既有 Service.Settings/Runner.Settings（提供 toServiceSettings/toRunnerSettings），固定非法设置异常，不静默回落默认。enabled 是启动配置，无热更新 API；不增加 worker-count 或第二套预算默认。保持 TushareProperties 的 min-request-interval=1500ms、120s 来源读超时/64MiB 和单批 60 秒事务期限。

ApplicationConfiguration 保持 servlet 条件，增加 `@EnableConfigurationProperties(DownloadTaskProperties.class)` 和显式 import；新增唯一 bean：DownloadTaskJson、带下述 observer 的 DownloadTaskRepository（同 JdbcTemplate/PlatformTransactionManager）、DownloadTaskQueryService、DownloadTaskService、BatchCommitService（现有 PersistenceService）、DownloadTaskRunner、DownloadTaskOperationLogger。Service/Runner/Coordinator 均注入 `@Qualifier("downloadTaskRunId") UUID`，Service/Runner 来自同一 properties；不存在默认随机 UUID 的第二入口。

顺序为 Flyway→既有 @DependsOnDatabaseInitialization DatasetCatalog→runId→Service/Runner→Coordinator 绑定→SmartLifecycle.start→HTTP 可接收。任务 Controller 构造依赖 coordinator 不作为业务调用；通过 `@DependsOn("downloadTaskCoordinator")` 确保其发布前已绑定，Controller 仅注入 Service/QueryService/参数解析/日志协作者。没有未绑定的生产 Controller；数据库或启动恢复失败导致上下文启动失败。Spring Boot 开放请求时仍由 Service 动态门禁裁决。

`DownloadBindingConfiguration` 增加新请求解析器和独立 `tensor-download-task-request` Jackson Module，保留旧 module/method 签名；使用单独 `DownloadTaskRequestWebConfiguration` 注册任务 query/path 参数解析器，保持旧 DatasetRequestArgumentResolver。DataSourceController 增加 tasks 构造依赖并更新相关直接构造测试，旧路由返回形状不变。不因 enabled=false 条件移除 Controller/bean，否则历史无法查询。

### 已提交事件与日志边界

现有 runner/repository 没有观察接口；禁止通过 Controller 轮询猜批次结果，禁止在 persist 的 afterWrite 内直接输出成功。新增 core `DownloadTaskObserver`，不依赖 app、SLF4J、MDC、Micrometer 或 HTTP：

```java
public interface DownloadTaskObserver {
    void taskStarted(TaskStarted event);
    void batchFinished(BatchFinished event);
    void taskFinished(TaskFinished event);
    DownloadTaskObserver NOOP = /* 三个空方法 */;
    // 三种事件为此接口的内嵌 immutable record。
}
```

所有事件只含白名单值：TaskStarted 含 taskId、DatasetKey、runGeneration；BatchFinished 加 batchId、status(SUCCEEDED/FAILED/SPLIT)、attemptCount、durationMs、三项行计数、可空 ErrorCode；TaskFinished 含 taskId、DatasetKey、runGeneration、任务终态、recovered(boolean)、durationMs、requestCount/runRequestCount、Counts、可空 ErrorCode。事件不携带 DownloadTask/DownloadBatch、params、策略、Throwable 或原始 message。durationMs 使用已存 startedAt 到本次完成 now 的非负差，无 startedAt 为 0。

Repository 保留现有三参构造器委托 NOOP，增加 `(JdbcTemplate,PlatformTransactionManager,DownloadTaskJson,DownloadTaskObserver)` 重载。仅在已完成条件写入后注册 TransactionSynchronization.afterCommit：claimTask 成功→TaskStarted；split 两子节点均成功插入后→SPLIT；failBatch→FAILED；succeedBatch→SUCCEEDED；finishTask→TaskFinished(recovered=false)；recoverStoppedTask→相应 RUNNING 批失败事件及 TaskFinished(recovered=true)。insert/requeue 的 HTTP 接收事件由 Controller 在用例成功返回后记录，避免两处重复；无变化/冲突不注册事件。

succeedBatch 的同步注册加入 T04 当前证券事务；不能在内部另开事务或在方法返回即报成功。事件身份、attempt/time/counters 从本事务已有锁定记录及已验证转换参数捕获；finish/recovery 复用现有聚合读取，恢复后的 pending/running/failed 分项按已执行转换生成，不为日志另读数据库。回调消费已捕获不可变事件，不查询或修改业务状态。

afterCommit 证明数据库提交成功，但此时 Spring 可能尚未清理事务资源，PersistenceService 也可能仍持有数据集锁；准确保留该时序。观察实现必须有界、只写本地日志，不访问数据库/来源、不调用 Service/Repository、不等待工作线程、不加入协调锁，不引入异步队列/额外线程。包装观察回调的 RuntimeException，任何日志格式器/appender 运行时异常只丢失观察，不能让已提交成功被 runner 标成失败或重发；JVM Error 不伪装成可重试业务异常，继续遵守 T08 的 fatal/FAULTED 边界。无 observer 时不增加日志、事务或来源调用。

提交回执故障有两种时序：afterCommit 已执行后调用方才收到异常，事件仍是已提交事实；doCommit 真实提交后立即抛错、尚未执行 Spring afterCommit 时，本次不产生成功事件。后者仍由 T08 恢复数据库事实，恢复只输出恢复终态，不重新下载或补造历史逐批事件。日志是尽力观察，不承诺故障/进程崩溃下恰好一次或每个提交必有日志；不能用日志代替数据库事实。

app `DownloadTaskOperationLogger implements DownloadTaskObserver` 输出固定事件名：`tensor.download_task.started`、`tensor.download_batch.finished`、`tensor.download_task.finished`，recovered 字段区分事实恢复。Controller 调用 `recordAccepted(RequestId,DownloadTask,AcceptanceKind,Duration)`，AcceptanceKind 为 CREATED/REPLAYED/RETRY/RESUME，输出 `tensor.download_task.accepted`（重放 outcome=replayed，不表示新接收）；仅记录 requestId/taskId/pluginId/apiName/status/version/耗时，不记录参数或完整对象。不调用现有 OperationLogger.recordDownloadSuccess，不增加 tensor_download_total/rows 指标，旧同步/查询指标语义保持。

后台每个日志回调创建独立 MDC 作用域：先保存调用方上下文、清空，再只设置 taskId、可选 batchId、pluginId、apiName、runGeneration；finally 清理本次字段并恢复原上下文。不得复制 servlet requestId 到后台事件。线程本身仍由 T08 创建，不传递 servlet MDC；测试验证前后任务不串 ID，抛异常后无本次残留。所有日志只输出 ErrorCode 和安全固定字段，不调用异常 toString、不输出 token、认证头、密码、响应正文、JDBC URL 或未知参数值。logger 自身及备用 observation-failed 警告都捕获 RuntimeException。

### 外部合同产物

`download-task.schema.json` 使用项目现有 JSON Schema 方言，独立 `$defs` 固定 SubmissionRequest、ControlRequest、Receipt、TaskResponse、BatchResponse、两种 Page、CapabilitiesResponse 和 Error；object 默认 additionalProperties=false，明确 required/nullability、数值类型、枚举、UUID/日期格式、params 的字符串值。重复 JSON 字段/HTTP query 重复需运行时拒绝，schema 不声称能表达。params 的具体形状由能力描述和 Codec 决定，不在新 schema 再复制 40 项参数表。

`download-task-examples.json` 包含 SINGLE 创建、受控 AVAILABLE RANGE 创建、200 重放、retry/resume、QUEUED/RUNNING/三批 PARTIAL_FAILED/SUCCEEDED/INTERRUPTED、未规划失败、SPLIT 父/子、空分页、三类能力、400/404/409/429/500 错误。三批示例写明 2 成功/1 失败及明确计数；RANGE 示例标注为合同示例、生产当前不可提交，不能宣称 Tushare 已开放。OpenAPI-v1 增加七类路径并引用同样 DTO，不修改旧 downloads schemas。T12 消费本项属性与外部合同，负责更新 configuration/first-run 运行手册；本项不编辑运行手册。

## Files

以下均为 T09 实施路径；本设计自身只创建本文。

| 文件（相对仓库根；相同目录按类型展开） | 职责 |
| --- | --- |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadTaskController.java` | 六类任务路由及 Receipt 状态/Location |
| 同目录 `DataSourceController.java` | 能力路由，保留旧元数据输出 |
| 同目录 `DownloadTaskRequestArgumentResolver.java` | 严格 path/query 白名单、重复值、分页/筛选 |
| `.../web/download/DownloadTaskRequestDeserializer.java`、`DownloadTaskControlRequestDeserializer.java` | 严格提交/控制解析，绑定新请求和历史 Replay |
| `.../web/dto/DownloadTaskRequest.java`、`DownloadTaskControlRequest.java`、`DownloadTaskResponse.java`、`DownloadTaskReceipt.java`、`DownloadBatchResponse.java`、`DownloadCapabilitiesResponse.java`、`DownloadTaskPage.java`、`DownloadTaskQuery.java` | 白名单 records；counts/error 等小 record 内嵌；query 内嵌任务/批次请求 |
| `.../config/ApplicationConfiguration.java`、`DownloadTaskProperties.java`、`DownloadBindingConfiguration.java`、`DownloadTaskRequestWebConfiguration.java`、`src/main/resources/application.yml` | 显式生命周期接线、属性、Jackson/参数解析注册 |
| `.../observability/DownloadTaskOperationLogger.java` | 独立接收和持久事件、本地 MDC、异常隔离 |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java` | prepareSubmission 与既有校验/重放共用 |
| 同目录 `DownloadTaskObserver.java`、`DownloadTaskRepository.java` | 最小安全事件、兼容构造器及共享事务 afterCommit |
| `docs/contracts/download-task.schema.json`、`download-task-examples.json`、`openapi-v1.yaml`、`error-codes.md` | 外部合同和真实持久语义 |
| `data-plane/tensor-app/pom.xml` | 增加 test-scope com.networknt:json-schema-validator，使用父 POM 已管理的 1.5.9 |
| app 测试：`web/DownloadTaskRequestBindingTest.java`、`DownloadTaskControllerIT.java`、`DownloadTaskContractTest.java`、`DownloadTaskRequestArgumentResolverTest.java`；`config/DownloadTaskPropertiesTest.java`、`DownloadTaskApplicationConfigurationIT.java`；`observability/DownloadTaskOperationLoggerTest.java` | 新 HTTP、生产装配和日志验证 |
| core 测试：`download/task/DownloadTaskObservationIT.java`，扩展 `DownloadTaskServiceTest/IT.java`；按受影响构造器扩展 app 的 `DataSourceControllerTest`、`ControllerUseCaseTest`、`ProductionApplicationContextIT`、`ModuleDependencyTest` | 提交事件/故障事实、绑定回归及依赖边界 |

不改 V8、证券表、40 份 YAML、T07 runner 公共入口、T08 调度/锁/恢复算法、旧同步绑定模块或前端页面；仅为必要测试夹具调整已有测试装配。

## Tests

**首个实施动作**：新增 app `DownloadTaskRequestBindingTest`，用已有生产 DownloadBindingConfiguration、precisionModule 与受控现有形状，读取合法 SINGLE 提交为尚不存在的 DownloadTaskRequest，观察缺失绑定类型/模块的 RED；随后实现最小绑定，马上补同 submissionId 停用后重放不访问当前能力的失败用例。不要把“重新设计 T09”当成实施入口，不先写生产 Controller。

| 场景 | 必须观察的结果 |
| --- | --- |
| 强类型绑定 | SINGLE 40 项及四种 RANGE 形状唯一；模式错配、未知/重复字段、非字符串、日期、股票、8KiB、非法 UUID、无 mode、control 数值类型严格拒绝；零新任务/来源；旧同步绑定行为保持 |
| 幂等绑定 | 精确/空白大小写默认值等价重放 200，同 ID/版本/创建时间；关闭、恢复、FAULTED、满队列、停用插件仍可找回；RANGE 描述删除后使用存储策略，SINGLE 元数据删除仅原样规范化可找回；不同请求 409，不被能力错误/400 掩盖 |
| MySQL HTTP 接收 | 来源由有界 latch 阻塞，POST 在释放来源前返回 202+Location；另一连接立即读到接收记录；后台继续而 servlet 已退出；receipt 不被轮询终态替换、无同步成功日志/指标 |
| 查询合同 | 默认/显式分页、筛选 AND、重复/未知/空值/大小写/溢出；未知任务 404、未知 submission 空页；SPLIT 默认排除；列表页成员稳定，每项用同一 snapshot 状态/计数；已存 timeout 的 GET 200；查询 DB 故障 500 QUERY_FAILED |
| 控制 HTTP | 三批仅第二批 retry、累计 2→3、attempt 1/2/1；resume 保留普通失败；版本竞争一 202 一 409；活动租约/队列满/定义变化/readiness 拒绝无状态污染；回执丢失后查询找回，不重发 requeue |
| 生产装配 | 真实 Spring Boot servlet 上下文及 MySQL8.4.6/Flyway，验证 production ApplicationConfiguration 显式导入、唯一 UUID/Service/Runner/Coordinator，catalog先于start；默认/覆盖/非法属性、enabled=false仍能查历史；初始化失败零来源；生产40注册与34 RANGE门禁保持 |
| 提交事件 | 真实 MySQL observer从独立连接看到已提交事实；证券写入/成功标记/拆分第二子节点故障回滚零成功事件；空批成功有事件；failBatch未保存零失败事件；旧permit冲突零事件；恢复仅实际改变时输出，重复启动不补造 |
| 回执与观察故障 | 真实 doCommit 后故障验证上述两种 afterCommit 时序；有事件也不重发，未确认回调不猜成功；抛 RuntimeException 的观察器/appender 不改变已提交业务结果，不阻止其他正常批次；MDC无原requestId/跨任务污染，无敏感信息 |
| JSON/文档 | 真实生产 ObjectMapper/precisionModule 下所有版本/计数为 number，日期/null/参数准确；schema与所有例子校验通过，OpenAPI七类路由形状一致，完整错误枚举映射保持；历史证券 LONG/DECIMAL仍为字符串 |

MySQL 集成固定 8.4.6，使用现有 Colima；不得使用 H2。来源在 app HTTP IT 采用已有受支持字段形状的独立测试插件，注册仅在测试配置；不扩展生产注册表。并发使用有界 latch/future/Clock，避免长时间真实轮询；观察事务测试复用 T04/T07 真实事务故障方式。ControllerIT 消费真实 Service/Repository/Runner/Coordinator，不能纯 mock 证明 202 和持久状态。app 新增 test-scope `com.networknt:json-schema-validator`，直接使用 data-plane 父 POM 已管理的 1.5.9，按 JSON Schema draft 2020-12 验证示例和实际 HTTP JSON，不进入生产包。

从仓库根、Java21运行，先核对已记录Docker连接；正常本机权限可能为 Mockito JVM attach 和 Colima 所需，不清理用户容器：

```sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -Dtest=DownloadTaskRequestBindingTest,DownloadTaskControllerIT,DownloadTaskContractTest,DownloadTaskRequestArgumentResolverTest,DownloadTaskPropertiesTest,DownloadTaskApplicationConfigurationIT,DownloadTaskOperationLoggerTest,DownloadTaskObservationIT,DownloadTaskServiceTest,DownloadTaskServiceIT,DownloadTaskQueryServiceTest,DownloadTaskCoordinatorTest,DownloadTaskRecoveryIT,DownloadTaskConfigurationTest,DownloadTaskConfigurationIT,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskRepositoryIT,BatchCommitServiceIT,PersistenceServiceIT,FixtureDownloadTaskRunnerTest,DownloadRequestBindingTest,DownloadParameterResolverTest,StockScopedDownloadTest,DownloadControllerIT,DataSourceControllerTest,GlobalExceptionHandlerTest,ControllerUseCaseTest,OperationLoggerTest,ObservabilityTest,ProductionApplicationContextIT,ModuleDependencyTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
git diff --check
```

四条 Maven 全部退出 0，新增测试和列出的 MySQL IT 实际执行，失败/错误/跳过 0；各完整生命周期前端现有170项和构建通过。`clean verify` 不代替显式 IT；T09 不运行尚未实现的 T12 浏览器生命周期 spec，也不把当前测试当作 T13 来源验收。新增文件加入 Git，不自动提交，保持当前分支与已有成果。

## Acceptance

1. 七类路由按字段、HTTP状态、Location、严格绑定与错误优先级成立；202是持久接收，200重放只找回原任务，后台失败查询仍200。
2. 生产同数据库/runId/settings完整接线，Service在Controller可接收前已绑定；disabled保留历史，启动失败不开放；T08单worker和实际退出约束保持。
3. HTTP强类型入口不破坏T03历史幂等；分页身份/顺序稳定，每个任务的状态和计数一致，所有响应只输出白名单且数值类型符合schema。
4. 成功/失败批次事件只在对应事务提交确认后产生；观察异常不改变已完成业务，回执不明不触发自动来源重放；接收与完成日志、MDC与敏感信息边界有真实测试。
5. 合同、示例、OpenAPI 和错误语义一致，四条验证命令及独立审查具备实际证据。先记录T09完成，再按Order准备T10设计/交接；本设计不改变看板状态或启动实现。

## Risks

- 生产34项RANGE依然NEEDS_VERIFICATION；当前范围只交付基础设施与HTTP，不消除上游完整性/权限条件。
- 列表使用有界N+1快照，成员过滤时刻与各行详情时刻不同；合同已明示，不用不一致计数换取少一次查询。后续性能改进需保留一致性。
- JSON数值为int64，超过JavaScript安全整数范围的通用客户端需要保真解析；本项不截断数据库版本或改旧精度模块。T10消费时必须显式处理。
- afterCommit发生在资源/数据集锁释放前，观察器仅允许有界本地日志；不能放入阻塞网络日志、数据库查写或业务重入。JVM fatal Error沿用既有进程故障边界。
- 日志不提供事务 outbox 或恰好一次保证；进程退出/回执故障可能缺事件，持久表始终为事实来源。
- 没有待用户补充的实质需求；上述是基于既有合同的明确实现选择。T09须通过实际测试与独立审查才能记录完成。
