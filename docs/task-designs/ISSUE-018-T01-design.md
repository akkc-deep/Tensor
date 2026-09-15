# ISSUE-018-T01：可选批量插件合同与区间参数绑定

任务：[ISSUE-018 看板 T01](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t01)。依据：[总体设计](ISSUE-018-design.md) §3.2–3.3、§3.11、§4、§5.1。

## 做什么

提供不依赖 Web、数据库或具体来源的可选批量插件合同；HTTP 参数层能显式选择 SINGLE / RANGE 的参数描述，四类 RANGE 形状唯一绑定。补全公共错误码、HTTP 与前端识别规则。

不接收或持久化任务，不调用上游，不实现日期规划算法或 worker，不修改 40 份 YAML，不开放任何真实 RANGE 接口。保留 `DataSourcePlugin.download`，旧插件无需实现新方法。

## 怎么做

### 插件合同

在 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/BatchDownloadSupport.java` 实现总体设计 §3.2 的五个方法签名。`plan` 只能由 worker 调用；`sourceParameters` 是纯函数且仅返回可持久化业务参数；`downloadBatch` 返回身份和参数匹配的 envelope；`assess` 在适配前验证范围，异常使用分类错误。上述职责写入接口 Javadoc，本任务不提供默认的上游执行实现。

在同模块 `download/batch/` 增加：

- `DateRange(LocalDate start, LocalDate end)`：两端非 null，start 不晚于 end，闭区间允许同日。
- `DownloadMode`：`SINGLE / RANGE`。
- `BatchAssessment`：`COMPLETE / SPLIT_REQUIRED / UNKNOWN`，无默认成功或布尔降级。
- `BatchCallContext` 接口：`Instant deadline()`、`boolean stopRequested()`、`void beforeRequest()`。每个真实请求先预约一次预算，再节流 / 调用；预算或停止可抛已分类异常；实现者必须使用剩余时限限制等待和网络超时。预算计数、可控时钟实现属于 T05 / T07。
- `BatchDownloadDescriptor` record：`List<ParameterDescriptor> parameters, String startParameter, String endParameter, DateAxis dateAxis, String dateLabel, PlanningMode planningMode, boolean splittable, Availability availability, String unavailableReason, String policyVersion, CompletenessRule completenessRule`。枚举和规则 record 作为其公共嵌套类型，减少文件。
- `DateAxis` 五值、`PlanningMode` 三值、`Availability` 三值完全采用总体设计。`CompletenessRule(Kind kind, Long rowLimit, String evidence)`；Kind 为 `CONFIRMED_ROW_LIMIT / VERIFIED_RULE / UNKNOWN`。行数规则必须有正数上限和非空依据；已验证规则必须有非空依据且无上限；UNKNOWN 不允许上限或依据，不能构造 AVAILABLE 描述。

描述复制参数集合并拒绝同名字段。policyVersion 始终非空。AVAILABLE 的 unavailableReason 必须为 null；其他状态必须提供非空原因。AVAILABLE / NEEDS_VERIFICATION 必须有日期轴、标签、规划模式与一对不同的必填 DATE_RANGE_MEMBER，互相 related，恰好两项区间端点且起点在参数列表中先于终点（复用 validator 的顺序语义）；允许来源自行使用 `from/to` 等参数名。逐日规划不得声明 splittable。UNSUPPORTED 使用空参数、空日期字段和规划模式、splittable=false、UNKNOWN 规则，防止暴露可提交表单。描述不声称能证明插件实际行为或官方依据真实性；开放核验仍由 T13 负责。

### 参数入口

修改 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/`：

- `DownloadParameters` 增加嵌套 `TsCodeDateRangeParameters(tsCode,startDate,endDate)`、`ExchangeIdDateRangeParameters(exchangeId,startDate,endDate)`。
- `ParameterCodec` 各新增一个 Codec，exchange_id 保留 `SSE/SZSE/BSE` 枚举；无股票与 exchange 组合复用现有两类。Java 类型和形状均不重复注册。
- `DownloadParameterResolver.resolve(ApiDescriptor api, Map<String,Object> values)`：直接使用调用方提供的描述，先匹配 Codec，再调用现有 validator 完整校验 / 规范化，最后通过 ParameterJsonReader 生成具体类型；不查询 SINGLE 元数据、不调用插件。匹配失败映射 DATASET_MISCONFIGURED，参数错误转 DownloadBindingException。调用方在 T03 / T09 组合独立 RANGE 描述。旧 `resolve(DatasetKey,Map)` 继续保留原始值到服务层验证及错误优先级，内部共用 Codec 查找。
- 在 `DownloadRequestDeserializer` 开启当前 JSON parser 的严格重复字段检测，顶层和 params 重复均返回 PARAM_INVALID / 400；这是总体设计明确要求的兼容收紧。其余未知字段和非字符串参数继续沿用现有绑定 / validator 路径。

本任务不新增任务请求 DTO 或 mode 反序列化器；它们由 T09 消费本入口。不在 plugin-api 导入 app 类型。

### 错误合同

ErrorCode 追加总体设计 §3.11 的十个枚举；`TASK_QUEUE_FULL.retryable=true`，其余新增枚举为 false。该布尔值只描述当前 HTTP 请求是否可稍后重试，后台 retry / resume 必须检查状态、能力和定义。

`TASK_NOT_FOUND` → 404；`TASK_QUEUE_FULL` → 429；`SOURCE_RANGE_MISMATCH` → 502；其余七项 → 409。后四项批次错误到达 HTTP 时采用这个兜底，正常后台查询仍返回 200 加持久化状态。同步更新 GlobalExceptionHandler 的两个穷尽 switch、前端 `errors.js` 的类型和 API_RULES、公开错误合同及受影响的精确枚举断言。SourceException 接受 SOURCE_RANGE_MISMATCH，保留其他错误家族限制。

## 如何测试

先写并观察失败，再实现以下行为：

- plugin-api `BatchDownloadDescriptorTest`：闭区间、同日、逆序和空端点；未知完整性不能 AVAILABLE；规则上限和依据；不可变参数、重复名、端点缺失 / 可选 / 关系错误 / 顺序错误、非法不可用状态；不同来源参数名合法。
- app `DownloadParameterResolverTest`：四类区间唯一 Codec，显式描述覆盖原 SINGLE，股票规范化、同日和闰年；缺字段、null、空白、非法日期、逆序、未知字段、非字符串与额外股票拒绝；旧 40 项和 fixture 形状回归。
- app `DownloadRequestBindingTest`：重复顶层 / 参数字段均 400，零操作事件与零上游；旧合法请求保持成功，保留非法值的错误优先级。
- `PluginApiSurfaceTest`、`GlobalExceptionHandlerTest`、前端 `api.spec.js`：新旧错误 HTTP / retryable / 脱敏响应均匹配，插件旧接口保持可实现。

仓库根运行：

```sh
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
npm --prefix control-plane run build
```

预期全部退出 0，无失败；使用 Java 21 与 package.json 要求的 Node 24。本项未改数据库，不以容器测试或真实上游取数作为合同实现的前置。

## 如何验证

实现与测试对应看板 T01 的每项 Acceptance；检查 plugin-api 无新增运行依赖、DataSourcePlugin 未变、YAML 未变、任务 HTTP / DB / worker 未提前实施；新增文件加入 Git。完成证据写看板，不写入设计。

T01 完成后按 Order 选择 T02，完成并回填其专属设计，再写 `docs/task-handoffs/ISSUE-018/ISSUE-018-T02-handoff.md` 并准备 READY；本轮不启动 T02 实现。

## 依赖什么信息

- 总体设计固定方法签名、范围形状、错误映射、分层和后续消费者，用户已要求按看板实施。
- 现有 ParameterDescriptor / ApiDescriptor 提供字段元数据和不可变集合；ParameterValidator 提供默认值、日期 / 股票规范化、字段错误及范围验证，范围端点按描述顺序比较。
- 旧绑定测试明确允许重复字段后值覆盖；本项依据 issue18 的严格拒绝要求修正该例，其余旧绑定语义保留。
- 官方完整性证据、真实请求时限和资源预算实现不由本项证明，分别由 T13、T05 / T07 负责；本项不得将未知值默认成已验证。
