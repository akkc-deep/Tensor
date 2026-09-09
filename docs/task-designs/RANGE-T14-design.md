# RANGE-T14 下载与失败任务 HTTP 接口设计

## Goal

完成[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T14`：将已交付的首次区间下载、原任务精确重试和两表只读查询接入 `/api/v1`，使元数据、请求绑定、结果、错误及当前失败任务展示符合现行 [OpenAPI](../contracts/openapi-v1.yaml) 和[错误目录](../contracts/error-codes.md)。下一位按本文直接实施，不再选择产品合同。

本设计在 T13 已记录 COMPLETED 后按 Order 选择 T14；观察到 T14 为 NOT_STARTED、Design document／Handoff 均为 None。设计就绪不表示实现、HTTP 验收或真实来源验收已完成。

## Scope

- 同版切换 API 元数据下载投影和 `POST /downloads`；新增 `GET /retry-tasks`、`GET /retry-tasks/{taskId}`、`POST /retry-tasks/{taskId}/execute`。接入现有绑定器、两个执行用例、只读存储查询和统一异常处理。
- 新增最小只读任务查询／投影服务、显式 Web DTO、安全结果／原因投影；处理分页、UUID、零字节 execute body、原始日期四状态、离线插件、当前进程 retrying／canExecute、同步生命周期和安全观测。
- 新增并运行 Web／Core／MySQL 合同验证，更新本项验证报告和增量追踪。仅确认 AC-PRD-RANGE-01、06、13、19、22、23、25、26、27、28 中本项负责的 HTTP 行为。
- 不改变 OpenAPI 的路径、字段、错误码、枚举或阶段语义；不修改生产 Dataset、来源／日历能力资源、SPI 来源协议、迁移、T12／T13 执行算法或两表写事务。不实现前端、真实进程 kill／socket 断连、真实来源调用；这些分别属于 T15～T20。不增加取消、异步任务、排队、自动重放、历史、编辑原任务、幂等令牌或核对端点。
- 保留当前分支、HEAD、原暂存和并行工作。实施新增正式文件显式纳入 Git，不提交／发布。本文准备阶段仅写设计和内部报告，不改代码、看板、交接，不运行 Maven 或浏览器。

## Approach

### 1. 按序输入、当前表面和已解决差异

先读 [TRD v1.10 §8、§10.3](../design/Tensor_区间下载_TRD_v1.0.md)，再读当前 OpenAPI／错误目录、`tensor-app/.../web/DownloadController.java`／`dto/`、`DownloadControllerIT.java`，最后完整消费下列直接输入。BRD v1.4、PRD v1.4 和 TRD 的文件名 v1.0 不代表正文版本；日期、成功保留及不能取消的既有决定不变。

| 输入 | 已确认可调用能力及本项消费方式 |
|---|---|
| [T03设计](RANGE-T03-design.md)／[验证](../verification/RANGE-T03-contracts.md) | 49 项／九种参数形状、38＋11 投影、五字段公开策略、18 字段本轮结果、失败任务 schema、26 码及阶段映射是最终 HTTP 依据；示例不是来源通过证据 |
| [T04设计](RANGE-T04-design.md)／[验证](../verification/RANGE-T04-plugin-contracts.md) | `ApiDescriptor.parameters()` 已是下载投影，`sourceParameters()` 保留原 YAML；完整 `downloadPolicy()` 含私有能力，HTTP 只取五字段。`PluginRegistry.descriptors()` 保留下线插件描述符，`find()` 仅返回可下载插件 |
| [T05设计](RANGE-T05-design.md)／[验证](../verification/RANGE-T05-parameter-conversion.md) | `DownloadParameterResolver.resolveDownload(dataset,values)` 已严格绑定投影、31 天及规范化；Deserializer 尚调用旧 `resolve`。`DownloadParameterConverter.mapRetry(api,rawTaskParams,selector)` 无副作用、保留原 map、按精确 selector 重建，缺两端可用 |
| [T12设计](RANGE-T12-design.md)／[验证](../verification/RANGE-T12-initial-execution.md) | `DownloadService.executeInitial(PluginId,ApiName,Map<String,Object>,RequestId)` 返回 `DownloadExecutionResult`；旧四参 `execute` 返回 `DownloadResult`，HTTP 尚用旧入口。唯一八参构造器及旧入口保留为既有 Core 表面，本项 HTTP 不再调用旧入口 |
| [T13设计](RANGE-T13-design.md)／[验证](../verification/RANGE-T13-exact-retry.md) | `RetryDownloadService.execute(UUID,RequestId)` 已在共享槽位内重读原任务、全部前置、精确执行和原子删除。唯一七参构造器及生产 bean 已存在，不再造重试执行服务 |
| 当前 T10 读取实现 | `RetryTaskStorageService.list(Criteria)` 返回 `Page(List<Task>,int page,int pageSize,long totalElements,long totalPages)`；`find(UUID)` 返回 `Optional<Task>`。两者使用独立 REPEATABLE_READ 只读事务；list 已实现双独立筛选、updated_at DESC/task_id DESC、越界页回末页、批量读取所有当前明细，无 N+1 |

当前 `GlobalExceptionHandler` 已含26码 HTTP 映射，但 `ApiErrorResponse` 没有 downloadResult；`DownloadResponse` 只有旧8字段；元数据 DTO 仍输出 sourceParameters。T12／T13 的快照必须逐字段投影，不能重新按 HTTP 请求数、失败数组或数据库查询推导计数。

已核对并解决的实现差异：

1. `JacksonPrecisionConfiguration` 全局把 boxed Long／BigDecimal 写成字符串。新增响应的 int64 计数必须显式用 `@JsonSerialize(using=NumberSerializer.class)` 数值序列化（包括 nullable Long）；不改变既有 records 精确数值配置。
   - 正式实施核实补正（2026-09-09）：当前Jackson的`NumberSerializer`没有无参构造器，直接用于`@JsonSerialize`会在HTTP响应序列化时报错。采用`DownloadResponse`内一个公开静态`LongNumberSerializer extends NumberSerializer`，无参构造仅调用`super(Long.class)`；结果和任务DTO的计数注解共用该适配类。数字／null合同和全局records精度保持不变，由实际Spring mapper与HTTP测试验证。
2. 存储列表／详情的 Item 顺序是 targetType、targetValue、timeType、timeValue 升序，与执行用例“时间再股票”的内部次序不同。HTTP 保留合同的存储顺序，不为统一外观改变执行顺序。
3. `mapRetry` 内的 SourceParameterMapper 先检查来源证据再检查部分形状。详情 blocker 要求“静态不兼容先于已知来源拒绝”；增加本地兼容性先行检查，不通过假造 DOCUMENTED 策略调用 converter 来绕过来源门槛。
4. 静态完整性字段目前统一 UNCONFIRMED，fixture 的运行取全能力仍可已实现；不能据此把所有任务的 canExecute 永久设为 false。GET 只报告已知静态拒绝，运行取全／实际日历覆盖仍由 execute 检查。
5. 存储只验证历史 errorMessage 的长度和控制字符，不能保证被外部修改的内容不含敏感信息；HTTP 按已验证 errorCode 重新取固定安全原因，不直接回显原文本。
6. T05 的通用 validator 保持 required-before-invalid，当前 resolveDownload 会将“只给旧日期、缺新两端”报 PARAM_REQUIRED；现行错误目录明确旧日期 alone/mixed 均 PARAM_INVALID。本项只在 Web resolveDownload 的区间模式增加旧日期键先行拒绝，通用 validator 和 Core 来源合同不改。
7. T11 旧设计的“全闭累计完成”措辞已由 T13 按现行合同处理为 S0/H>0。HTTP 原样保留 T13 结果；最后项删除未知的 taskId=null／remaining=null 也不能用输入路径 ID 补回。

### 2. 最小接口与数据流

新增 Core `com.akkc.tensor.core.retry.RetryTaskQueryService`，唯一构造器和两个查询方法：

```java
RetryTaskQueryService(RetryTaskStorageService storage, PluginRegistry plugins,
    AdapterRegistry adapters, ParameterValidator validator, DownloadExecutionSlot slot);
TaskPage list(RetryTaskRepository.Criteria criteria);
TaskDetail get(UUID taskId);
```

在该类内嵌套不可变 `TaskPage`、`TaskSummary`、`TaskDetail`、`TaskItem`、`OriginalRange`、`OriginalRangeStatus`、`ExecutionBlocker` records／enum，字段与 §4～§6 对应；不带 Jackson、Servlet、来源客户端或请求 ID。TaskDetail 可组合 TaskSummary，Web DTO 将其展平，不能在 wire 新增 `summary` 包装。内部集合防御复制。服务仅调用 storage.list／find、注册表快照、validator／converter 和槽位只读快照；不能获取槽位、读写业务表、加 Controller 事务或调用任何 plugin 回调。

在 `DownloadExecutionSlot` 增加唯一只读 `Snapshot snapshot()`，返回嵌套 `Snapshot(boolean busy, UUID retryTaskId)`；一次 `current.get()` 同时提取两个事实，不泄露 Lease／线程、不能用于释放。现有 acquire／busy／retrying 保留，执行表面不变。详情一次使用同一 Snapshot 计算 busy 和“是否正重试此 ID”，避免两次 AtomicReference 读取产生 `retrying=true` 却 `canExecute=true` 的矛盾。

App `ApplicationConfiguration` 装配一个查询服务 bean，共用已有 storage／plugins／adapters／validator／slot。新增 `RetryTaskController`，依赖 queries、RetryDownloadService、OperationLogger；已有 DownloadController 继续三依赖，只将业务调用切为 executeInitial。Controller 职责仅输入校验、获取现有 MDC RequestId、一次用例调用、DTO 投影及安全观测。

| 路径 | 调用与响应 |
|---|---|
| `GET /api/v1/data-sources/{pluginId}/apis` | 仍用 MetadataQueryService.listApis；ApiDescriptorResponse.from 改读 parameters 并投影公开 policy。已有插件访问错误边界不改 |
| `POST /api/v1/downloads` | Deserializer 改用 resolveDownload；Controller 用现有 toRawValues 和 suppliedFields 得到规范化值，只调用 executeInitial；返回新 DownloadResponse |
| `GET /api/v1/retry-tasks` | 校验四个 query 参数后调用 queries.list(Criteria)，将 TaskPage 投影并加当前 requestId |
| `GET /api/v1/retry-tasks/{taskId}` | 校验标准 UUID 后调用 queries.get；空 Optional 为 RETRY_TASK_NOT_FOUND，实际已保存内容作为只读快照返回 |
| `POST /api/v1/retry-tasks/{taskId}/execute` | 先校验 UUID／body，随后直接调用 retries.execute(id,requestId)。不预读详情、不用页面 canExecute 拦截、不调用 queries.get；T13 自己保证 busy 优先于不存在 |

### 3. 输入与元数据同版迁移

下载外层三必填字段复用 T05 Deserializer。pluginId／apiName必须先验证原始JSON类型为string，不能依赖Jackson将布尔／数字强转为String；null／缺失仍产生必填字段错误，外层聚合沿既有规则：任一字段无效则PARAM_INVALID，否则全为缺失时PARAM_REQUIRED；保留排序和固定安全fieldErrors，验证后才构造标识。外层规则不替代参数validator的required-before-invalid。正式集成评审核实boolean会被转换为合法标识字符串，须用实际Spring mapper／servlet回归覆盖两字段各类非字符串并确认零用例调用；不修改全局标量转换配置。未知外层字段必须在请求本地明确拒绝。正式独立评审核实生产Spring mapper默认忽略未知字段，裸ObjectMapper单测的默认拒绝不能证明生产行为；在私有WireValues上用`@JsonAnySetter`抛固定PARAM_INVALID／field=request，既不回显未知键和值，也不修改全局配置。实际Spring mapper与servlet首尾未知字段回归必须通过且零执行。严格重复键在本项补齐：正式实施核实旧实现对重复键采用最后值且旧测试期望200，不能将其误记为已有拒绝。只在当前DownloadRequest解析器启用`JsonParser.Feature.STRICT_DUPLICATE_DETECTION`，外层和params内重复字段均400 PARAM_INVALID，零业务调用；不改变其他端点的全局Jackson配置。禁止把区间 map 发给旧 execute。38 项要求 string start_date/end_date、严格公历、包含两端最多31自然日；31允许、32拒绝，先于日历／月份展开。缺一端 PARAM_REQUIRED，旧 trade_date／ann_date／month 单独或混用 PARAM_INVALID，未知字段／非字符串／逆序／非法日期 PARAM_INVALID；在 resolveDownload 完成 requireApi 后，对非 ORIGINAL_PARAMS 的 raw map 先检测 trade_date／ann_date／month 键是否存在（即使值为null），存在即 DownloadBindingException(PARAM_INVALID)，field为对应固定旧键、message为固定“is no longer accepted”；随后才用原 validator。这保证旧键单独提交也PARAM_INVALID；没有旧键的缺端继续PARAM_REQUIRED。其他错误复用 validator 的字段错误，不回显用户值。11 项原条件和 fixture scenario 保留，不能给它们添加日期。旧参数仅在 Core 来源调用中合法，HTTP 无兼容双入口或灰度开关。

`ApiDescriptorResponse` 继续 apiName/displayName/category/queryMode/parameters，新增必填 `downloadPolicy`，使用嵌套 `DownloadPolicyResponse(mode,dateSemantic,description,String calendarProfile,LimitsResponse limits)`。calendarProfile 取 `.value()` 产生 C-A 等，不输出 C_A；非交易 null、原条件 limits=null，其余精确 `{maxRangeDays:31}`。必须显式保留两个 null，不对整个策略使用 NON_NULL。ParameterResponse 保持已有可选字段省略方式。

独立核对真实49项：19／15／1／3／11；38项只有 start/end 两个日期 DATE_RANGE_MEMBER，relatedParameter 互指，附加股票／市场条件在日期前，11项参数数组与来源定义完全相等。查询 queryMode、dataset filters／columns／businessKey、现有记录查询和其 JSON 精确数值均不改变。公开策略不包含 sourceRequestMode、sourceParameters、requestEvidenceStatus、recoveryPolicy、batchPlanning、completenessPolicy、calendarEvidenceStatus、evidenceRefs 或来源 URL。DataSourceSummary.downloadAvailable 继续仅配置可用性，不能被改写成真实来源通过。

列表 page 缺省1、pageSize缺省20；仅接受整型 page>=1 和 pageSize=20/50/100。使用原始字符串／单值 query 解析再构造 Criteria，明确拒绝空串、非整数、小数、负值、0、整型溢出及重复同名值，返回字段 page/pageSize 的 PARAM_INVALID。pluginId、apiName 分别可缺省；存在则各自按已有标识正则校验，空串无效，不要求两者同时提供，也不向当前注册表验证存在。只按存储标识绑定筛选值，离线／已移除插件仍能查询；合法未知筛选返回空页。忽略未声明 query 字段沿用既有 MVC 行为，不把它们当新筛选功能。

两个 taskId 路径都先匹配大小写不敏感的完整 `8-4-4-4-12` 十六进制 UUID 形状，再 UUID.fromString；拒绝 Java 宽松接受的短段、空白或多余字符，400 PARAM_INVALID／field=taskId，不限定 UUID 版本。输出由 UUID.toString 规范化为小写。

execute 接收 HttpServletRequest，只读 `getInputStream().read()` 判断是否等于 -1：不能只看 Content-Length，也不能让 Jackson 把 null／空白当无 body。任何首字节（包括 `{}`、`null`、空白、换行、非 JSON、未知媒体类型）均400 PARAM_INVALID／field=request；无需把整个错误 body 读入内存或记录。真正零字节 body 无论是否声明 Content-Type 都可进入用例；不得设置 consumes=application/json 导致空 body 415。UUID及body拒绝均在 service 调用前，不占槽、不查询、不回调。请求 query 不是可编辑任务输入，Controller 不读取／透传其中任何范围或条件；执行始终只有 UUID 和 RequestId。

### 4. 本轮结果与异常映射

替换旧 DownloadResponse record 为 OpenAPI DownloadExecutionResult 的18个字段，提供唯一 `from(DownloadExecutionResult)` 映射，正常200及 ApiError.downloadResult 共用这个 Web record（后者允许 UNCONFIRMED）。不要复用旧 DownloadOutcome／DownloadResult，也不要直接序列化 Core record。

| 字段组 | 映射规则 |
|---|---|
| requestId、pluginId、apiName、taskId | value/toString，nullable taskId 保留 null；正常与错误嵌套 requestId 等于头及 ApiError requestId |
| outcome、failureRecordStatus、message | 使用 Core 已验证 enum 及安全本轮 message；六 outcome、三 status 全覆盖，不从 F、剩余明细数或 HTTP 状态重新分类 |
| R/I/U、S/F/H | long，显式数字序列化；仅 Core 确认小计，F不等于旧失败项总数 |
| N、remainingFailedUnits | nullable Long，数字或显式 null；不按 scope 数组长度推算，不将 null 省略／改零／写字符串 |
| failures | 将每个 Failure 展平为 selector 四字段＋errorCode/errorMessage；固定安全原因。不能输出 `selector` 嵌套、batch、map、SQL、cause或内部结果票据 |
| notStartedScopes、unconfirmedScopes | 每项仅 selector 四字段；保留原范围、不合并稀疏日期或同日不同股；空时为[] |

`ApiErrorResponse` 追加可空 DownloadResponse downloadResult，仅此字段 `@JsonInclude(NON_NULL)`；保留原五参构造器委托新六参 null，避免无关非下载调用点迁移。fieldErrors 永远存在，无字段为[]。不能全局 NON_NULL，否则结果和详情必需 null 会丢失。

GlobalExceptionHandler 在现有 TensorException 分支识别 DownloadExecutionException，使用同一 status(code)、固定安全 message、retryable、fieldErrors=[]，将 exception.downloadResult() 原样投影。其他异常不附快照；不得从异常 cause 或 Request 属性伪造结果。现有26码映射保留，重点如下：

| 情况 | HTTP／结果 |
|---|---|
| 全部正常尝试完毕，SUCCESS／EMPTY／NO_OPEN_DATES／PARTIAL／FAILED | 200，后三种不是异常；PARTIAL／FAILED 必有确认 taskId、remaining>0、CONFIRMED，N0、unknown／notStarted空 |
| 业务前输入／插件／合法请求／日历拒绝 | 原目录400／409／502等 ApiError，不带 downloadResult，不新建首次任务，重试记录保留 |
| 忙／缺失 | DOWNLOAD_BUSY 409 true；RETRY_TASK_NOT_FOUND 404 false。有效输入且busy时先409、绝不为查不存在先执行SQL |
| 业务中来源／适配／冲突明确失败且可靠保存后继续 | 用例正常返回200 PARTIAL／FAILED，不能因错误目录422／502／504将整轮改为相应错误状态 |
| TASK_RECORD_SAVE_UNCONFIRMED | 500 false＋UNCONFIRMED快照；当前明确F/failures保留，不放unknown，不承诺已保存 |
| COMMIT_UNCONFIRMED | 500 false＋UNCONFIRMED快照；当前仅unknown、不增S/F/R/I/U；taskId和remaining按Core事实，尤其最后删项未知不能补回路径ID |
| 用例 PERSISTENCE_FAILED／INTERNAL_ERROR 停止 | 500，分别true／false＋现有UNCONFIRMED快照，保留确认小计、实际未开始范围和已确认删除；不据 HTTP500 撤销成功 |
| 只读SQL或事务读取失败 | QUERY_FAILED 500 true，无下载快照 |

错误目录容许更一般的 PARTIAL／FAILED 停止快照，但当前 T12/T13 专用异常仅产生 UNCONFIRMED；本项忠实接入当前结果，不为了制造示例扩大 Core 结果构造器。保留本轮 message，不复制来源原始消息。保存失败总文案说明“保存未确认”，不能臆断确认回滚或成功。

### 5. 列表、详情与安全公共条件投影

queries.list 只调用一次 storage.list，保留其页码／总数／排序和完整 Task 快照；不可对每个 task 再 find。`TaskSummary` 包含 taskId、保存 pluginId/apiName、可空 pluginDisplayName/apiDisplayName、originalDateRange／status、failedItemCount、完整 failedScopes、createdAt/updatedAt。failedItemCount 等于当前 items 数，failedScopes 与 items 一一对应、不去掉不同股票或合并范围。列表不增加 retrying/canExecute、taskParams或历史计数。

queries.get 只调用一次 storage.find；不存在直接404。两种GET都在投影前检查每个Task至少含一个item；可读但空主表是存储结构异常，QUERY_FAILED／500，不返回不合schema的200、不滤掉该行、不删除或作为可执行性blocker。详情展平 Summary 的全部字段，再加 requestId、taskParams JSON对象、所有 items、retrying、canExecute、executionBlocker。items 与 failedScopes 使用同一排序，item 仅 selector四字段、errorCode、安全errorMessage、updatedAt，不含父taskId或公共参数。UTC时间用 `DateTimeFormatterBuilder().appendInstant(3)` 固定三位毫秒和 Z，不能让 Instant 默认把 `.000` 省掉；计数／分页总数用数字，所有可空显示名／原始范围／blocker必须输出 null。

注册表查找使用 descriptors 快照，不调用 MetadataQueryService.listApis（它会排除 offline），也不调用 plugin.readiness／descriptor。唯一匹配插件时取其 displayName，唯一 API 时取其 displayName和policy；插件被禁用但描述符仍注册，名称和模式仍可用。插件不存在或重复无法唯一判断时不任选第一个：可确认的名称保留，其余null、mode未知；保存的 pluginId/apiName 永远保留。缺API／adapter属于静态不可执行，不能把列表行过滤掉。

安全投影针对原 header.taskParams 做副本，绝不改存储、清洗后回写或把清洗 map 用于执行／兼容判断。当前API可确定时，白名单为其公开 parameters 字段和原始 start_date/end_date；额外排除匹配 token/authorization/cookie/password/credential 的字段名及已知来源分页 offset/limit/cursor/page/page_size。仅输出字符串值，不展开任意对象／数组。对已知参数，用单字段类型／enum／pattern验证选择可安全展示的值，敏感名和不合约值省略；日期只有安全的八位数字文本可展示，非法公历状态仍由原map计算。缺少API时只允许安全八位数字的两个原始日期字段，其余条件无法证明白名单则省略；不推断模式或范围。合法保存公共值保持原值，STOCK 不从 selector 复制 ts_code，时间 selector不复制回任务参数。未公开的畸形／敏感条件仍参与下节阻断，不能因展示过滤而把原任务变成可执行。

新增 App `dto/DownloadFailureResponse`（可含嵌套 ScopeResponse 和安全静态原因方法），统一13种允许保存的来源／适配／冲突／持久化原因表，逐码与当前 RetryTaskRepository.errorMessage 字面核对；不扩大仓储公开表面，不接受任意错误字符串。查询 Core TaskItem 只携 errorCode，不携原 errorMessage 到 Web 投影。结果 Failure.errorMessage也按相同表投影，避免同码两种不一致原因。非法存储枚举／JSON／selector致 T10 RETRY_TASK_INVALID 的读取异常，查询边界将其映射为安全 QUERY_FAILED，符合两个GET声明的错误范围；不能跳过坏行伪造完整列表。能读取但公共条件语义不兼容仍200详情＋blocker，不隐藏记录。

### 6. 原始日期和执行条件快照

原始范围只检查原保存map和已注册mode，不依赖 items／现在的表单／当前来源参数，不调用 bindInitial 校验整个历史任务。使用现有 ParameterValidator 对API的两个公开日期描述符单独校验，再以 `DownloadParameterConverter.OriginalDateRange` 验证严格两端和31天；未记录双方时不补默认，未知mode不猜。

| 原map／模式 | originalDateRangeStatus／范围 |
|---|---|
| 已知区间mode，两个有效八位日期、有序且<=31天 | RECORDED＋原 compact startDate/endDate；即使当前失败仅3／7或公共条件其他字段失效，展示仍原1～10 |
| 已知 ORIGINAL_PARAMS，原map无两个日期键 | NOT_APPLICABLE＋null |
| 已知区间mode，两个日期键都缺 | NOT_RECORDED＋null；允许后续按合法selector重试，不能单因此阻断 |
| 仅一端、null／类型错／非法日期／逆序／32天、ORIGINAL_PARAMS带任一日期键、未知mode | UNCONFIRMED＋null；canExecute必须false |

日期检查失败不从当前失败首尾日期重建，也不把原范围当执行范围。合法旧selector可长于31天或MONTH覆盖整月；31天只校验已保存的**原输入日期对**，不得再次限制selector或重试项总量。

详情完成数据库快照后读取一次 slot.snapshot。`retrying = snapshot.busy && taskId.equals(snapshot.retryTaskId)`；首次执行或别的task占槽时false，当前任务执行时true；重启空槽false。这些字段不落库、不缓存、不持久化。blocker 按以下顺序选择第一个；canExecute精确等于 blocker==null：

1. 任意执行占槽：DOWNLOAD_BUSY，固定等待文案，即使插件／记录同时失效也优先。只观测、不获取／释放。
2. 插件不存在／重复／downloadAvailable=false：PLUGIN_DISABLED。凭证未配置沿既有 PluginReadiness 同样不可用，不读取凭证本身。
3. 当前API／同dataset GenericDatasetAdapter缺失：DATASET_MISCONFIGURED；随后原始日期UNCONFIRMED或公共参数／selector不兼容：RETRY_TASK_INVALID。未知mode即不可兼容，不以过滤后的map检查。
4. 已注册policy的 requestEvidenceStatus=UNCONFIRMED／CONFLICT 或 sourceRequestMode=null：SOURCE_REQUEST_UNCONFIRMED。
5. TRADE_DATE_RANGE 的 calendarEvidenceStatus=UNCONFIRMED／CONFLICT：CALENDAR_UNCONFIRMED。DOCUMENTED仅没有这个静态拒绝，不表示本轮日期／新鲜度确认；其他模式不套日历。
6. 其余无已知静态拒绝：null／canExecute=true。完整来源注册、实际日历、来源权限和交易执行仍可能失败，GET不试探。

第3步的本地检查固定如下，复用 validator 和 selector 构造不变量，写在查询服务私有方法中：

- 用原map检测未知／敏感／分页字段、非string、旧时间键、缺必需公共条件及非法枚举／股票。日期对按上表独立处理；对每个selector，在副本剥离start/end，然后校验剩余公开非日期descriptor列表。REQUEST 保留必填股票；STOCK 要求原map不含ts_code、当前sourceParameters明确声明TS_CODE、recoveryPolicy为独立已核实STOCK_TIME；从被校验公共descriptor列表去掉ts_code，不能修改原map。
- REQUEST 不因当前策略增强为STOCK_TIME而拒绝；STOCK时间必须匹配 recoveryPolicy.unitTimeType，RANGE能力兼容DATE。同日期不同股票是不同selector。ORIGINAL_PARAMS仅NONE；MONTH_RANGE仅MONTH；其余不能NONE／MONTH。若sourceRequestMode已知，DATE候选仅DATE、MONTH仅MONTH、RANGE仅DATE/RANGE、NONE仅NONE；无候选时只判断已知mode能证明的限制，随后返回来源拒绝，不猜未知合法映射。
- 当没有上述错误且来源候选 DOCUMENTED_CANDIDATE 时，对**所有**items用原map调用 converter.mapRetry；捕获 RETRY_TASK_INVALID 为第3类，SOURCE_REQUEST_UNCONFIRMED留第4类。不得调用 processor、calendar、planBatch、fetchBatch或执行用例来计算按钮。

第3步只做数据形状和注册兼容性，不能伪造完整前置已通过；T13点击时仍重新读取、重新校验全部项。比如页面显示 canExecute=true 后被另一执行抢槽，POST应409；页面读到的明细之后全部解决，POST空槽应404。每次GET是当前已提交记录的快照，不能推断缺失项曾成功或网络失联的完整执行结果。

### 7. 同步生命周期与安全日志

两个POST都在原Servlet线程同步调用现有用例；不返回 Callable／DeferredResult／CompletableFuture、不使用@Async、不创建线程池、不让Servlet异步超时管理业务。Controller无 `@Transactional`；只读查询事务由storage持有，来源HTTP仍在业务事务外。

槽位的所有权／finally完全由T12／T13执行线程管理。响应序列化发生在用例已完成之后；客户端等待超时、响应写入失败、读取详情和 RequestIdFilter finally 都无权释放槽位或取消剩余范围。不得传 HttpServletRequest、Thread.interrupted、客户端存活回调、Future.cancel到Core。重用同一 X-Request-Id 也不会去重或自动重发。强制进程退出仍仅保证已提交数据和已保存失败，不补写未开始／未保存／unknown。

`OperationLogger` 新增 `recordDownloadExecution(DownloadExecutionResult result, Duration duration)`，两个POST正常返回时先成功完成 DTO 投影，再记录一次；捕获 DownloadExecutionException 时记录其确认快照后原样抛出供统一handler响应。前置异常仍走原handler，不假造结果；记录失败吞受控观测异常，不能改变业务返回或再次执行。

日志只用合法requestId、存在的确认taskId、plugin/api、outcome、S/F/N/H、R/I/U、remaining、failureRecordStatus及已知错误码／单元类型；不日志整个result、failure列表、参数值、来源正文、原历史errorMessage、SQL或异常cause。参数日志旧方法保留供旧Core调用者，新方法无需参数map。不得把PARTIAL／FAILED／UNCONFIRMED调用旧recordDownloadSuccess后记录成SUCCESS。

为最小兼容，现有 TensorMetrics 三个 outcome 标签和行计数语义保留：新SUCCESS映射SUCCESS，EMPTY和NO_OPEN_DATES映射EMPTY，PARTIAL／FAILED／UNCONFIRMED映射FAILURE；真实细分 outcome 写安全日志。既有FAILURE行计数不增，部分完成的已确认R/I/U仍完整存在响应及日志；不为本项增加新指标、高基数taskId/requestId标签或监控页。每个有结果的POST只调用一次metrics；GET不计作下载。GlobalExceptionHandler保留固定安全错误消息和脱敏堆栈方式，不输出原异常message/cause，测试同时捕获响应和日志哨兵。

## Files

以下为 T14 正式启动后的变更；相同小record放同文件，不按字段拆文件，不删文件。

| 文件（仓库相对路径） | 责任 |
|---|---|
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/retry/RetryTaskQueryService.java`（新） | 两个只读入口、注册元数据查找、完整任务投影、公共参数白名单、日期四状态、静态兼容和blocker；嵌套Core只读records |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadExecutionSlot.java` | 仅增加单次原子读取Snapshot，不改Lease生命周期 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java` | 一个共享依赖查询bean |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RetryTaskController.java`（新） | 三个任务端点、输入、RequestId及调用／投影 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java` | 切executeInitial、共用新result投影和观测 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/DownloadRequestDeserializer.java`、`DownloadParameterResolver.java`（同目录） | resolve→resolveDownload；区间旧日期键先行PARAM_INVALID，保留其余严格解析和通用validator |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java` | parameters和五字段public policy投影 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadResponse.java` | 18字段、数值null、显式Core映射 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadFailureResponse.java`（新） | flat failure、嵌套ScopeResponse、固定13码安全原因 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/RetryTaskResponse.java`（新） | 嵌套Summary／Page／Detail／Item／OriginalRange／Blocker Web records，展平、时间和number格式 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java` | 可选嵌套result及DownloadExecutionException分支；既有码表不变 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/OperationLogger.java` | 安全执行结果观测入口，三标签兼容映射 |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/retry/RetryTaskQueryServiceTest.java`（新）、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadExecutionSlotTest.java` | 只读投影矩阵及原子快照 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RetryTaskControllerTest.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RangeResponseContractTest.java`（新） | 输入／优先级、原mapper完整JSON、null／number／字段集、静态合同 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RetryTaskControllerIT.java`（新） | 真实MySQL＋MockMvc三任务端点、精确闭环及并发只读 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java` | 迁移新HTTP期望和真实区间结果／保存故障映射；保留旧Core反射与独立调用验证 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadRequestBindingTest.java`、`DataSourceControllerTest.java`、`ControllerUseCaseTest.java`、`GlobalExceptionHandlerTest.java`（同目录） | 38＋11 HTTP迁移、公开元数据、薄Controller和26码回归 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/observability/OperationLoggerTest.java`、`ProductionApplicationContextIT.java` | 全部outcome观测、安全哨兵、唯一共享查询bean |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/FixtureFlowIT.java` | 原样回归现有plugin.download→adapter→persistence/query流程；正式实施核实该类没有HTTP断言，无需制造代码改动 |
| `docs/verification/RANGE-T14-http.md`（新）、`docs/traceability/tensor-range-requirements.md` | 本轮HTTP证据、实际命令／计数及验收边界 |

正式实施核实的fixture边界：当前FixturePlugin只实现旧download，默认planBatch／fetchBatch拒绝完整性未确认。DownloadControllerIT可在测试内包装相同scenario提供显式受控批次能力，复用真实adapter／commit／storage；同时验证未包装fixture通过新HTTP安全拒绝且零写入。不得将测试内包装视为运行fixture能力已迁移，生产fixture扩展仍由T18完成。

复用不修改 T10 repository／storage、T05 converter／validator、T12／T13用例、plugin-api／Tushare／fixture生产来源、TensorMetrics和全局JacksonPrecisionConfiguration。已有手工构造helper只在新增查询bean／DTO或HTTP行为实际影响时最小调整，不能通过删断言使回归通过。合同文档内容本项冻结；运行迁移的完成事实记验证／追踪，T21统一更新其旧“尚未迁移”说明。

## Tests

正式实施第一动作：记录当前分支／HEAD／原索引、58份受保护资源摘要（49 Dataset、2策略、7生产迁移），然后先写三个可观察HTTP期望：①原daily.trade_date拒绝且range调用executeInitial；②有busy槽的有效不存在task执行返回409且find零次，非空body优先400；③带null N／remaining的COMMIT_UNCONFIRMED保持number/null及原确认小计。记录接口缺失编译和可执行行为RED的区别，再实施最小变更。

| 验证组 | 必须观察的结果 |
|---|---|
| 49项元数据／绑定 | 从真实Tushare策略／Dataset加载，对照独立OpenAPI target清单逐项核验38＋11、九形状、5字段policy、null及C-A写法；HTTP实际Deserializer逐项接受目标值，旧日期／混用／11项加日期拒绝。合法但source未知不能报参数错。fixture scenario仍接受 |
| 日期／输入完整矩阵 | 31允许32拒绝、闰日／0000／逆序／缺一端、stock trim大写／多股、枚举／非string、未知／重复字段、输入顺序；错误字段与requestId安全，绑定失败零业务和两表写入 |
| execute真实空body | 零字节、有Content-Type零字节均可执行；{}、null、空格、换行、二进制／非JSON、chunked/未知Content-Length的一个字节均400；不靠content length或Jackson判空，不进行查询／执行。完整大小写UUID可用、短段／空白／非法UUID400 |
| 400/409/404顺序 | 无效UUID/body即使busy先400；合法输入busy先409且SQL0；空槽实际不存在404；GET不存在404即使busy，GET有效记录在busy仍200；旧页面按钮true后来抢槽仍409、记录删完后404 |
| 列表 | 两独立筛选、联合筛选、offline/未知标识、20/50/100、默认页、重复值／非法分页400；真实同timestamp按UUID DESC、越界回末页、空page1/total0/items[]；每任务全部selectors、两股同日不合并；只调用一次storage.list无N+1 |
| 详情投影 | taskParams是对象、summary展平、所有required null存在、时间`.000Z`与`.123Z`、数字节点；原1～10与剩余3/7独立；items/scopes/count精确相等，字符串次序稳定；不出现历史、totalUnits、参数到子项复制 |
| 日期四状态 | RECORDED、NOT_APPLICABLE、NOT_RECORDED、UNCONFIRMED全部；旧缺两端仍可重试；单端／非法原日期阻断；合法日期＋其他坏公共条件仍RECORDED；未知mode不猜；offline有注册mode保持准确；MONTH完整月和长合法selector不套31天 |
| blocker优先级 | busy>plugin/credential>metadata/参数selector>source>calendar；非法enum＋source未确认先INVALID、合法条件＋source缺失先SOURCE_REQUEST_UNCONFIRMED；STOCK缺授权／重复股票／时间错、REQUEST增强后仍可用；static完整性UNCONFIRMED不一律拒绝fixture；已知日历UNCONFIRMED/CONFLICT拒绝，DOCUMENTED不伪造实际确认 |
| GET零副作用 | storage读取时无下载Lease；plugin所有descriptor/readiness/confirmCalendar/planBatch/fetchBatch/download回调计数0；adapter不adapt、不写业务／两表；伪造未知字段只展示过滤但原map仍不兼容。读取后原taskParams、createdAt及原因不变 |
| number／null和精度回归 | 使用实际Spring ObjectMapper及precisionModule，断言所有int64 JSON节点isIntegralNumber，nullable Long为number/null，包含大于Integer.MAX_VALUE值；records BIGINT/DECIMAL仍精确字符串，未全局改serializer。严格比对result／summary／detail／item字段集合，额外内部字段视失败 |
| 正常五outcome | 首次／重试分别SUCCESS、EMPTY、NO_OPEN_DATES、PARTIAL、FAILED200；F0／合法空＋失败／全闭S0/H>0；已保存失败taskId及remaining一致，不把来源504中断转整轮504 |
| 错误快照 | 对四停止码逐项500及retryable；此前非零I/U保留，N=null或已知正确；保存未知仍F、commit未知仅unknown；最后删除未知taskId/remaining=null，确认最后删除后框架错taskId=null/remaining0；业务前错误省略downloadResult，26码仍精确匹配目录 |
| 安全 | 来源／SQL／历史原因／凭证／Authorization／正文／cause中放独立哨兵，捕获HTTP和OperationLogger/handler日志均不得出现。taskParams非白名单／敏感键不输出，嵌套对象不展开；合法公共值及原map不被修改；13码理由固定；畸形存储读取、可读但无明细的孤儿主表均QUERY_FAILED、不返回非法200／删行，不泄露诊断 |
| 观测 | 全部6outcome日志真实，metrics success/empty/failure映射如§7；PARTIAL/UNCONFIRMED绝无success记录，ID不做metric标签；有结果每次一次、GET0次；观测故障不改变结果／重新执行 |
| MySQL＋HTTP闭环 | 新下载原1～10的3/7失败，HTTP200 PARTIAL和真实两表一致；GET原日期／当前selectors；execute只请求3/7、3删7原因更新，再GET仅7原JSON不变；下一execute成功删最后主表，再GET/execute404。两股同日分别删、其他同条件任务不动 |
| MySQL错误／只读 | 真实迁移、独立SQL确认保存失败／commit丢回复的HTTP快照；复用T12/T13受控连接代理方式，不能用查库反推返回。SQL读失败500QUERY_FAILED；执行中latch停在fetch／commit／updateReason时并行GET可见已提交快照且不排队占槽，另一POST409 |
| 同步与响应丢失边界 | MockMvc请求执行线程用有界latch阻塞真实用例；等待者get超时但不cancel，slot仍busy，GET200／POST409，释放latch后剩余单元完成、真实结束才空槽。另响应序列化写出失败发生在业务已结束后，已保存记录不改、不重发。明确这不是实际socket断连证据 |
| 生产图和旧查询 | 一个查询bean、两个已有执行bean及同一个slot/storage/registry/adapter；新HTTP生产请求在缺日历／合法来源／完整性时安全拒绝、零业务；已有dataset查询、精确数值、分页与fixture受控流程通过 |

从仓库根依次执行；以下是正式实施必跑命令，本设计准备不运行：

```sh
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,RetryTaskControllerTest,RangeResponseContractTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RetryTaskQueryServiceTest,DownloadExecutionSlotTest,RetryDownloadServiceTest,DownloadServiceTest,DownloadExecutionResultTest,DownloadParameterConverterTest,RetryTaskStorageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,DownloadParameterResolverTest,DataSourceControllerTest,ControllerUseCaseTest,GlobalExceptionHandlerTest,RetryTaskControllerTest,RangeResponseContractTest,OperationLoggerTest,RequestIdFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RetryTaskControllerIT,DownloadControllerIT,DatasetControllerIT,FixtureFlowIT,ProductionApplicationContextIT,RetryDownloadServiceIT,InitialDownloadServiceIT,RetryTaskStorageIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py
git diff --check
git diff --cached --check
```

除首轮记录实际RED，最终命令全部实际退出0；每个明确指定的测试类XML tests>0、failures/errors/skipped=0。八类显式App IT必须真正运行，clean前另存本轮日志/XML；默认verify/acceptance只调度打包合同，不能替代MySQL。完整构建的前端测试／build仍需成功，但前端源码不改、不运行浏览器；新后端HTTP已迁移不等于旧页面可用，页面合同迁移由T15～T17完成。

MySQL用隔离 `mysql:8.4.6` Testcontainers 和App真实Flyway迁移，不复制DDL、不用H2、disabledWithoutDocker或跳过。受控日历／来源／STOCK策略仅测试内副本，保持生产策略／Dataset不变。不读取真实凭证、不调用真实来源；必要JVM附加／测试端口／Colima沿已授权测试环境处理，环境失败如实单列，不改依赖或关闭断言。单次有界并发等待不超过测试合理超时，不调用Future.cancel伪造断连。

最终报告实际本轮计数／JSON／SQL／请求顺序、各error阶段、同步证据和静态合同校验；不照抄T13的130／73／795／798。核对58份摘要、分支／HEAD和任务外暂存不变，新增正式文件显式stage；保存独立规格、质量及最终集成／证据评审。新差异／失败／未解决问题修复后定向复验，已通过项无新变化无需反复全量。

## Acceptance

1. 四个下载／任务端点与元数据符合现行OpenAPI；38项HTTP参数实际切换，11项及fixture原条件保留；Controller只调用指定用例，旧下载入口不再被HTTP使用。
2. 正常5种outcome和停止错误快照均按Core确认事实输出，number／null／字段集／requestId准确；F、remaining、N及任务存续互不替代，unknown不冒充失败或已保存。
3. 列表筛选／分页／排序和详情完整selectors、公共条件、四种原始日期状态可观察；原map及保存标识不变，插件下线／移除仍能看见实际记录，GET失败统一安全映射。
4. retrying／canExecute由同一个进程槽位快照和已知静态兼容条件产生，优先级固定，GET零来源回调／槽位获取／写入；POST仍重新验证、忙409优先缺失404，非零body400。
5. 同步调用不引入取消或断连释放通道，当前执行未结束时另一POST始终忙、GET可用；本轮等待者／写出失败证据不冒称真实网络断连验收。
6. HTTP及日志无敏感正文／诊断，结果不误记SUCCESS；规定测试、MySQL、两次构建、合同检查、资源保护及独立评审通过，新增正式文件纳入Git。验证报告准确区分本项HTTP／受控通过与后续页面、真实中断、真实来源未验证。

## Risks

- 无待补的产品事实；实现风险已在上文固定选择。源码仍有旧DTO/旧HTTP测试和直接Core旧入口，必须迁移实际Web行为而保留未要求删除的Core表面，不能为维持旧Web期望加参数兼容回退。
- GET是已提交记录与随后进程槽位的短暂快照，二者不是跨库／进程原子版本。Snapshot只保证busy/retrying自洽，不能承诺点击时状态不变；POST重新读取解决正常竞态，不增版本领取协议。
- 历史公共条件可安全读取却不可执行时返回详情及blocker；若底层JSON／枚举／selector已损坏到T10无法读取，返回QUERY_FAILED，不能猜修复、隐藏坏行或新增修复接口。安全展示可能省略无法证明白名单的旧字段，但执行检查始终读取原map，绝不因省略而放行。
- 静态候选／日历DOCUMENTED不保证真实可用。生产完整来源／日历仍为空、49项REQUEST不变；本项不升级T01／T02结论，不恢复ISSUE-008九项真实调用。
- backend同版迁移完成后，前端仍需T15～T17接入；完整前端单测通过不表示浏览器HTTP闭环已通过。真正socket断连、进程中断和完整故障矩阵由T18提供，不使用本项latch或MockMvc证据替代。
- 默认构建不运行显式MySQL IT，全局Long序列化可能使只测DTO accessor的测试漏掉wire错误；必须使用实际mapper和指定IT命令，保留本轮XML。旧metrics仅提供三类结果和全成功行数，本项细分及部分小计以真实HTTP／安全日志为准。
