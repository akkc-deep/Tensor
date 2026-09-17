# DATA-INTEGRITY-T09 HTTP API、错误与公开合同

## Goal

把已验收的本地能力、幂等受理、固定计划、持久报告和已提交进度通过六个HTTP端点提供给前端。资料不足是可查询的UNKNOWN报告，网络状态码只表达请求是否成功。

## Scope

实现控制器、严格请求/查询绑定、公开响应映射、缺失任务错误及OpenAPI/JSON schema/错误文档和HTTP合同测试。直接复用T02、T05、T06、T07服务；T08当前规则随能力快照自然可见。不增加规则、SQL、任务线程、数据库迁移、前端页面、取消/删除/导出接口；不修改下载受理或下载合同。

## Approach

### 1. 装配和调用边界

新增 `web/IntegrityCheckController.java`，使用 `@RestController`、JSON produces 和仅SERVLET装配；依赖现有 `IntegrityCheckService`、`IntegrityCheckJson`，以及只用于能力展示的 `DownloadTaskService`。报告查询通过现有Service的薄读取方法委托 `IntegrityCheckRepository`；控制器不直接依赖Repository，以遵守已存在的 `ModuleDependencyTest` 架构规则。六端点完整路径见下表，不给不同资源强套一个类级路径。既有 `IntegrityCheckConfiguration` 已装配所有运行服务，无需重建生命周期。

新增 `web/integrity/IntegrityCheckQuery.java` 负责解析 `MultiValueMap<String,String>` 查询参数和路径标识；返回现成Repository三个Filter和分页值，不实现SQL。参数错误用局部 `TensorException` 子类承载PARAM_INVALID，沿用全局安全响应，不能把原异常消息给用户。控制器调用Service读取入口前完成参数校验，分页/排序/事务由仓库保持。

新增 `web/dto/IntegrityCheckResponses.java`，集中嵌套record和映射方法；所有HTTP公开字段在本节固定。不直接返回TaskRecord、ApiSnapshot或仓库记录，以免暴露内部requestHash、unitKey及物理表定义。

### 2. 端点及请求

| 方法 / 路径 | 调用及结果 |
| --- | --- |
| GET `/api/v1/data-sources/{pluginId}/integrity-capabilities` | `service.capability(PluginId)`；200能力响应。合法但未知/停用/不支持的来源沿用service结果：localCheckAvailable=false、明确unavailableReason、capabilityHash=null、apis=[]；缺Token仍可用。 |
| POST `/api/v1/integrity-checks` | 严格读取原JSON对象，原样传 `service.submit(Map<String,Object>)`；created=true为202，否则200；两者都返回Receipt及相同任务的Location。 |
| GET `/api/v1/integrity-checks` | `service.tasks(TaskFilter,page,pageSize)`，返回TaskPage。 |
| GET `/api/v1/integrity-checks/{checkId}` | `service.progress(UUID)`；Service将仓库空Optional映射为404，其余一次同事务快照映射Detail。 |
| GET `/api/v1/integrity-checks/{checkId}/results` | 调用 `service.results`，由Service先 `repository.find(checkId)` 验证任务存在，再委托仓库 `results`；不存在404，存在但无匹配数据为200空页。 |
| GET `/api/v1/integrity-checks/{checkId}/issues` | 调用 `service.issues`，同上验证任务，再委托仓库 `issues`；resultId只是过滤条件，不属于此任务时为空页，不跨任务取数据。 |

POST固定字段与共享设计一致：submissionId、pluginId、capabilityHash、symbols、startDate、endDate必需，apiNames可省略。控制器不得预先补apiNames、排序数组、规范化股票、刷新hash或用当前能力阻挡重放；全部交给service既有顺序处理。

请求体可接收原JSON字符串，用 `IntegrityCheckJson.readValue` 严格读取（拒绝重复键、尾随内容、非对象），再由应用ObjectMapper转为 `Map<String,Object>`。仅解析阶段的失败映射PARAM_INVALID，不能将service的409/429/数据库异常一并转换为400。缺体由既有Spring异常处理，缺必需业务字段由service返回PARAM_REQUIRED。null、空数组、未知字段、非法日期/符号等保留service现有语义。禁止新增一次性反序列化DTO默认值导致重放请求哈希改变。

所有端点拒绝未知或重复查询参数。分页默认page=1、pageSize=20；正十进制整数，page不超过Integer.MAX_VALUE，pageSize可取1..100任意整数（不能复制下载列表的离散白名单）。空串、0、负数、小数、溢出为400；越末页为200空items。只接受下列过滤项：

- 历史：pluginId、status（IntegrityTaskStatus）、submissionId。
- 结果：symbol、apiName、overallStatus（IntegrityStatus）。
- 问题：resultId、symbol、apiName、type（IntegrityIssue.Type）、status（IntegrityStatus）、dateFrom、dateTo。
- 能力、详情、POST无查询参数。

UUID必须匹配已有 `ValidationConstants.UUID_REGEX` 后再解析，避免Java宽松UUID缩写；pluginId/apiName用现有值对象及identifier规则。枚举区分大小写、不得用N/A代替NOT_APPLICABLE。symbol是历史精确筛选值，非空且最多255码点，不调用当前来源规范化器或要求股票仍存在。日期为严格四位年ISO、1000..9999、真实日历日期；两个日期都给出时要求dateFrom≤dateTo，单边允许。date筛选针对已存issue_date，自动排除null日期；不传日期即可重新看到范围级问题。

### 3. 公开响应形状和精度

复用应用现有精确数值序列化：Long/long为十进制字符串，BigDecimal为十进制字符串，Integer/int为JSON整数；null必须显式保留，时间为UTC ISO、日期为ISO。分页page/pageSize、plannedUnits及Settings的int项为整数；total、completedUnits/errorUnits/notRunUnits、各结论计数和issueId为字符串。

**Capability**：`pluginId,localCheckAvailable,unavailableReason,capabilityHash,limits,apis`。limits逐字段映射service.Settings的10项（maxSymbols/maxRangeDays/maxUnits/queueCapacity/workers/scanBatchSize/maxScannedRowsPerUnit/maxIssuesPerUnit/unitTimeoutSeconds/taskTimeoutSeconds），保留实际配置值。

每个API条目为 `apiName,displayName,descriptor,downloadAvailability`：apiName/displayName来自保存于本次能力对象的definition；descriptor是第1节既有IntegrityDescriptor完整公开形状（datasetKey、scopeKind、symbolField、dateField、dateLabel、marketZone、capabilityVersion、dependencies、rules、limitations），缺实现时null。downloadAvailability复用 `DownloadCapabilitiesResponse.from(downloads.capabilities(datasetKey))` 的现有single/range形状，纯本地调用；缺Token时下载不可用不能关闭localCheckAvailable。检查limitations仍采用来源描述，不从下载成功推导覆盖。apis顺序与service能力hash的有序API列表一致；不重新计算hash。

**Receipt**：`requestId,checkId,submissionId,pluginId,status,plannedUnits,createdAt`，来自submit返回的已有任务；requestId取RequestIdFilter/MDC，Location=`/api/v1/integrity-checks/{checkId}`。响应状态使用提交返回的实际状态，不能假定重放仍QUEUED。

**TaskSummary**（历史items）：`checkId,submissionId,pluginId,originalRequest,scope,capabilityHash,status,plannedUnits,createdAt,updatedAt,startedAt,finishedAt,errorCode,errorMessage`。scope直接使用TaskRecord.normalizedScope，originalRequest直接使用已保存原请求；不重建默认接口清单。错误来自持久安全字段，不追加原异常。列表不对每项另查当前进度。

**Detail**：TaskSummary的所有字段平铺，加 `overallStatus,completedUnits,errorUnits,notRunUnits,statusCounts`。由一次repository.progress返回映射；statusCounts固定包含PASS/FAIL/WARN/UNKNOWN/NOT_APPLICABLE，值为精确字符串。未完成单元按仓库口径计UNKNOWN，completedUnits包含已提交COMPLETED/ERROR，errorUnits是其中子集；不把两者相加作为完成数。不提供跨接口总百分比。

**Result**（结果items）：`resultId,checkId,report`。report直接使用ResultRecord.report的保存JSON，不用当前descriptor重算。该对象已有scope、descriptor、definitionHash、publishedRange、unitStatus、coverageStatus/keyStatus/fieldStatus/overallStatus、statistics、ruleResults、evidence、finishedAt、incomplete、issuesComplete、reasonCode、message。scope包含datasetKey/symbol/startDate/endDate/acceptedAt/snapshotStartedAt，保持完整原范围。statistics完整7项计数加coverageRate，未知值null，比例只允许6位十进制字符串。

**Issue**（问题items）：`issueId,resultId,ruleId,ruleVersion,issue`。issue直接使用保存JSON，完整保留type/status/symbol/apiName/dateField/date/businessKey/field/relatedDates/reasonCode/message/evidence/incomplete。完整业务键不做展示格式转换；规则归属取已保存ruleId/version。date=null不填任务创建日期。

三个Page固定 `{page,pageSize,total,items}`，由仓库总数和items映射，不另做COUNT或跨事务拼接；排序原样沿用T05。Result/Issue页不暴露内部JSON的schemaVersion包装。

### 4. 错误和历史兼容

新增 `ErrorCode.INTEGRITY_CHECK_NOT_FOUND("Integrity check was not found", false)`，缺失判断在Service读取入口执行，全局映射404；同步错误枚举公开合同及既有ErrorCode完整枚举测试。其余完整性错误已由T06接入，核实并保留：INTEGRITY_UNAVAILABLE/INTEGRITY_DEFINITION_CHANGED=409，INTEGRITY_QUEUE_FULL=429，INTEGRITY_LIMIT_EXCEEDED=400，SUBMISSION_CONFLICT=409，PARAM_REQUIRED/PARAM_INVALID=400，PERSISTENCE_FAILED/QUERY_FAILED=500。复用统一ApiErrorResponse、X-Request-Id及安全日志；所有业务UNKNOWN都为正常200查询。

GET历史/详情/结果/问题不访问当前插件，不要求当前Token、规则仍注册或hash仍相等。以旧报告保存的@1/@2描述解释历史；当前能力仅用于能力查询和首次submit校验。find检查与列表读取不合并新事务；项目无删除报告功能，不引入新的生命周期竞争。

### 5. 公共合同

在 `docs/contracts/openapi-v1.yaml` 声明六端点、全部参数/状态/Location、上面各DTO和枚举；新增 `integrity-check.schema.json` 供独立JSON Schema验证，以及 `integrity-check-examples.json` 存能力、首次/重放回执、进行中详情、FAIL/UNKNOWN/N/A、不完整结果、null日期问题和空页示例。

对象固定字段并拒绝未知字段，nullable字段显式列出。计数的字符串正则为非负十进制整数；coverageRate为null或0..1的六位字符串；原始请求采用已定请求schema，保存JSON键值保持精确字符串/null。OpenAPI与独立schema共享相同字段和枚举语义；不能把ApiSnapshot内部表/列结构直接当公开能力DTO。

## Files

- 新增 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/IntegrityCheckController.java`：六端点和薄层编排。
- 新增 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/integrity/IntegrityCheckQuery.java`：查询/路径严格解析和局部安全参数异常。
- 新增 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/IntegrityCheckResponses.java`：公开record、持久JSON映射及精度边界。
- 修改 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckService.java`：增加薄只读查询入口和父任务不存在404，避免控制器依赖Repository；沿用仓库事务与排序，不增加SQL。
- 修改 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`：补缺失任务404及枚举契约。
- 新增 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/IntegrityCheckControllerTest.java`、`IntegrityCheckContractTest.java`：MockMvc真实绑定/映射与schema一致性。
- 修改同模块 `GlobalExceptionHandlerTest.java` 及现有受ErrorCode枚举变化影响的合同测试；`observability/ProductionApplicationContextIT.java`：真实应用图包含新控制器并用HTTP验证无Token能力、POST/GET及报告查询。
- 修改 `docs/contracts/openapi-v1.yaml`、`error-codes.md`；新增上述两个integrity JSON合同文件及 `docs/verification/DATA-INTEGRITY-T09.md`。
- 不改报告表/规则/Runner、下载接口和全局Jackson数值策略。

## Tests

首个RED：MockMvc向 `/api/v1/integrity-checks` POST合法原请求，真实请求解析和DTO映射配合service stub返回created=true，断言202、Location、checkId、requestId以及未改写的原请求。当前没有路由应得到404；确认失败再实现。

专项必须覆盖：

1. 首次202、同ID原请求重放200、不同请求409、规则升级后原请求仍交给service重放；POST未知/重复JSON键、尾随内容、数字替代字符串、null/缺字段、apiNames省略与空数组各按合同。
2. 无Token能力可用且下载不可用；40API顺序、三行情@2、其他@1、NON_STOCK和缺实现descriptor=null、旧/停用/未知来源不可用说明；stub来源客户端无交互。
3. 三种列表所有过滤参数精确传递到对应Repository Filter，默认1/20、任意合法1..100、边界100、越末页空；未知/重复参数、空值、UUID缩写、枚举/日期/分页错误400。结果symbol不依赖当前插件。
4. 详情404及各列表父任务404，合法不存在resultId过滤为空；已保存数据FAIL和UNKNOWN均200；FAILED/INTERRUPTED/NOT_RUN、incomplete/issuesComplete及null日期保留。
5. Long.MAX_VALUE计数/issueId精确字符串、0与null区分，比例0.950000；原范围、完整多字段键、relatedDates、旧版本规则/evidence读取不重算，响应无requestHash/unitKey/原异常内容。
6. 真实生产应用测试走HTTP提交三行情、按submissionId找回，再查详情/results/issues及日期筛选；真实后台提交，来源无Token，证券数据不变。已有T08受控数据可直接复用。
7. IntegrityCheckContractTest读取生产DTO输出和examples逐一做独立schema验证，并核对OpenAPI六路由/方法、精确字段、枚举与错误映射；既有下载合同回归不退化。

```sh
mvn -o -f data-plane/pom.xml \
  '-Dtest=IntegrityCheckControllerTest,IntegrityCheckContractTest,GlobalExceptionHandlerTest,DownloadTaskContractTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml '-Dtest=ProductionApplicationContextIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

预期均BUILD SUCCESS、0失败/错误/跳过，IT实际使用MySQL8.4.6。不以编译通过或stub代替真实HTTP+数据库证据。不要用 `-Dtest=*Test` 重写POM排除项而误选打包阶段测试。

`scripts/verify-contracts.sh` 是项目全量发布合同门禁，要求clean main与已提交输入；不为本任务修改其前置条件。T09以IntegrityCheckContractTest和既有DownloadTaskContractTest验证变更的HTTP公开合同；T13在集成前置条件满足后运行完整脚本。验收文档分别记录，不能声称隔离区已通过clean-main发布门禁。

## Acceptance

六端点可从真实应用访问，所有固定DTO/过滤/分页/错误码和精度合同有测试；首次/重放保持相同请求与checkId、原范围不变。缺Token可本地执行，UNKNOWN报告可正常200查询；任务执行状态与数据结论分开。历史不依赖当前插件规则，旧版本证据原样可见。专项、真实应用、全量单元和HTTP合同测试通过，新文件加入Git；仅在T09显式启动后实现。

## Risks

- 2026-09-17实施中，全量回归发现原设计要求Controller直接依赖Repository与既有架构门禁冲突；按最小改动将读取入口放入既有IntegrityCheckService，保持公开HTTP合同、事务和SQL不变。前端页面仍由后续任务实现。
- 原JSON默认值、数组重排或用当前能力预校验重放都会破坏T06幂等，必须直接复用既有service。
- 历史JSON已经精确编码，二次转成JS安全整数/Double或用当前descriptor重建会破坏精度/历史口径。
- task完成可以同时overall=FAIL/UNKNOWN，前端不得把HTTP200或COMPLETED当成数据完整。
- 全量发布合同脚本的clean-main要求与此隔离区混合暂存现状不同；本任务不能合入旧基线来满足该门禁。
