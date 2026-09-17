# DATA-INTEGRITY-T06 范围固定、幂等受理与队列准入

## Goal

把用户原始请求固定为可追溯的检查范围、完整能力快照与计划单元；相同提交可找回原任务，首次提交只有在范围合法且获得队列名额后才持久受理。直接消费 T02 的本地能力与 T05 的原子存储，不调用上游、不依赖本地股票是否已有数据。

## Scope

实现 core 的 `IntegrityCheckService`、独立有界队列、严格输入校验、原请求幂等、完整能力快照及单元计划；装配并校验 `tensor.integrity` 配置，补齐受理使用的错误码和配置说明。补充来源可选能力的纯本地日期校验扩展以落实 Tushare 上海日期边界。

本项不启动工作线程，不扫描证券数据、不执行规则、不实现状态推进或重启恢复，不新增 HTTP 入口、公开 DTO 或前端。队列消费和遗留任务中断由 T07 接入，HTTP 合同由 T09 实现。既有下载队列、下载能力与 SINGLE/RANGE 行为保持兼容。

## Approach

### 1. 服务与不可变输入

新增 `com.akkc.tensor.core.integrity.IntegrityCheckService`，构造依赖为 `PluginRegistry`、`DatasetCatalog`、`IntegrityCheckRepository`、`IntegrityCheckJson`、`IntegrityCheckQueue`、`Clock` 和嵌套 `Settings`。不在服务上添加 `@Transactional`；T05 仓库独占自己的短事务，明确拒绝外部事务。

公开接口：

```java
public SubmissionResult submit(Map<String, Object> originalRequest);
public Capability capability(PluginId pluginId);
public record SubmissionResult(IntegrityCheckRepository.TaskRecord task, boolean created) {}
public record Capability(PluginId pluginId, boolean localCheckAvailable,
        String unavailableReason, String capabilityHash, Settings limits,
        List<IntegrityCheckJson.ApiSnapshot> apis) {}
public record Settings(int maxSymbols, int maxRangeDays, int maxUnits,
        int queueCapacity, int workers, int scanBatchSize,
        long maxScannedRowsPerUnit, int maxIssuesPerUnit,
        long unitTimeoutSeconds, long taskTimeoutSeconds) {}
```

Settings 构造时校验第 5 节全部正数和 workers=1，提供 `defaults()`；Capability 防御复制列表。T09 可从这里组装自己的公开响应，但本项不提前固定 HTTP DTO。下载限制仍消费来源元数据，不把 downloadAvailable 当本地检查门禁。

submit 入口先防御复制原请求的 map/list，保持对象字段值、数组顺序、字符串空白、大小写及省略字段状态；复制完成后调用 T01/T05 `requestHash`，不能先把股票规范化或补全默认 apiNames 再求哈希。副本不随调用方后续修改变化。报告编码器会把 Long/BigDecimal 转成字符串，因此这些不属于任何合法请求字段的数值类型须在复制阶段拒绝，不能与已保存的字符串请求碰撞后被重放。null 请求和缺 submissionId 为 PARAM_REQUIRED；非法 UUID、非法值形状及不可编码值为 PARAM_INVALID。UUID 字符串须符合完整 8-4-4-4-12 形式，不能接受 `UUID.fromString` 的短段宽松输入。T09 解析 JSON 时另行拒绝重复对象键，不能通过 Map 恢复已被解析器覆盖的字段。

### 2. 受理顺序与幂等

每个应用单实例服务用一个私有受理锁覆盖以下流程，避免并发首次提交抢占重复名额；消费者使用队列自己的同步机制，不持有服务锁。读取 capability 不需要受理锁。此锁不包含上游请求或证券扫描。

1. 只解析 submissionId 并计算原请求哈希，调用 `findBySubmissionId`。已有任务且 hash 相同，立即返回原 TaskRecord、created=false；不访问当前插件、日期规则、能力、队列，不重新入队。已有 ID 但 hash 不同，抛 SUBMISSION_CONFLICT；包括大小写/空白、数组次序、apiNames 省略与显式全选等原请求差异。
2. 新 ID 才检查允许字段与必填项、查找本地能力、固定 `acceptedAt=clock.instant()`、规范化股票/日期/API、构造第 3 节完整能力快照并比较 capabilityHash。缺省 apiNames 在这里展开，不能影响第 1 步的原请求哈希。
3. 计算第 4 节完整单元数并校验上限，生成任务及单元 UUID。调用 queue.reserve()；无名额抛 INTEGRITY_QUEUE_FULL，数据库中没有新任务或单元。
4. 在 reservation 的 try-with-resources 范围内调用仓库 `create(NewTask, List<NewUnit>)`。仓库返回意味着事务已经提交；随后 reservation.publish(checkId)，只发布一次。createdAt 和各 scope.acceptedAt 使用同一个 acceptedAt，snapshotStartedAt=null。
5. `find(checkId)` 读取已保存 TaskRecord 后返回 created=true；读取失败保留已经受理且已入队的任务，客户端用同一请求重放找回，不能删除、再建或重复发布。

仓库创建抛 `DuplicateKeyException` 时，先释放本次未发布名额，再按 submissionId 查询已提交赢家。存在且 hash 相同返回 created=false，不再入队；不同则 SUBMISSION_CONFLICT。不存在则 PERSISTENCE_FAILED，不能把任何 UUID/单元唯一冲突都冒充幂等成功。保留仓库 QUERY_FAILED/PERSISTENCE_FAILED 的安全错误，不回传 SQL、输入内容或异常堆栈。

同请求在能力规则升级、完整接口列表变化、插件停用或队列已满之后仍能重放旧任务。当前应用内队列不是跨实例调度：数据库唯一键只兜底并发提交，不据此宣称支持多实例共同执行。

### 3. 严格校验与完整能力快照

首次请求仅允许 submissionId、pluginId、capabilityHash、symbols、startDate、endDate、apiNames 七个键。前六项缺失/null 为 PARAM_REQUIRED；错误类型、空值、未知键及 apiNames 显式 null 为 PARAM_INVALID。pluginId/API 使用既有值对象的格式约束；capabilityHash 必须为 64 位小写十六进制。

symbols 必须为非空字符串数组。每个输入由 `IntegrityCheckSupport.normalizeIntegritySymbol` 解释，规范化后按首次出现顺序去重，再验证非空和 T05 可保存的 255 字符上限。插件校验异常转为安全 PARAM_INVALID。禁止查询 stock_basic、证券表或调用来源客户端筛除股票。格式合法且整只未下载的股票保留；全部选择 NON_STOCK 时也不取消原请求的股票必填合同。

日期只接受匹配 `[0-9]{4}-[0-9]{2}-[0-9]{2}` 且严格解析成功的字符串，满足 MySQL DATE 的 1000-01-01..9999-12-31 和 startDate<=endDate。闭区间天数使用 long 的 `ChronoUnit.DAYS.between(start,end)+1`，不截断日期范围。

在 `IntegrityCheckSupport` 增加向后兼容的默认方法：

```java
default void validateIntegrityRange(IntegrityDateRange range, Instant acceptedAt)
```

默认只检查非null参数，日期正反向由 range 合同保证，不加来源限制。`TushareProPlugin` 覆盖该方法，以 `acceptedAt.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()` 判断 endDate 不得晚于受理当日，非法抛 IllegalArgumentException，由服务转 PARAM_INVALID。此方法不读取系统当前时钟、不联网、不扫描数据库，不挪用 snapshotStartedAt。已有 fixture/旧可选能力实现无需改写，core 不硬编码 tushare_pro 或证券后缀。当天已受理不等于当天数据已发布；发布判定仍属于执行规则。

`capability(pluginId)` 使用 `PluginRegistry.findIntegrity`，不可用返回 available=false、既有安全原因、hash=null、apis空列表和 limits；submit 首次请求遇到不可用抛 INTEGRITY_UNAVAILABLE。可用时：

- 使用 `DatasetCatalog.list(pluginId)` 的 API 名升序获取该插件完整定义列表，空列表视为 INTEGRITY_UNAVAILABLE，不生成空计划。
- 对每项读取一次 descriptor 和规则实现；已知 API 缺 descriptor 合法，生成 descriptor=null/coreRules空的快照。若无 descriptor 却注册了规则，按元数据错误拒绝，不能执行未声明规则。
- 有 descriptor 时，从 catalog 收集其声明的参考定义，按 datasetKey 去重并稳定排序，调用 `IntegrityContracts.validate(descriptor, implementations, definitions)`；股票级 coreRules 使用 `IntegrityContracts.coreRules(definition)`，NON_STOCK 为空。所有快照经 `IntegrityCheckJson.capabilitySnapshot/capabilityHash` 的跨接口与稳定编码校验。缺参考或坏规则合同为 DATASET_MISCONFIGURED，不能静默丢弃接口。
- hash 覆盖完整插件 API 顺序、定义、参考、口径、来源规则及 core 规则；不含 Token、readiness、运行时间。提交只选择部分 API 时也使用同一全量 hash。格式合法但值不同抛 INTEGRITY_DEFINITION_CHANGED。
- apiNames 省略时固定全量列表；显式提供须为非空字符串数组，成员全部存在于该插件 catalog，未知成员拒绝。重复成员规范化去重，执行范围按 catalog 顺序排列；原数组及其顺序仍完整保留在原请求 hash 中。

受理读取并校验的整组 ApiSnapshot 同时用于 hash 比较、任务 definitionSnapshot 和每个 NewUnit，不在这些步骤之间再次查询元数据。后续执行前的版本复核属于 T07。

### 4. 固定计划与有界队列

任务保存 T05 `TaskScope(pluginId,symbols,startDate,endDate,apiNames,acceptedAt)` 和完整插件快照；每个选中 API 使用同一份对应 ApiSnapshot。对 STOCK_DATE、STOCK_SNAPSHOT 和缺 descriptor 的接口按每只规范股票生成 NewUnit；NON_STOCK 每接口只生成一个 symbol=null 单元。按 API 升序、股票首次出现顺序建立计划，计数先用 long 累加再与 maxUnits 比较，避免乘法溢出。完整计划一次提交，由 T05 再次校验恰好覆盖范围。

T05 创建的所有单元均为 PENDING/UNKNOWN。缺实现最终 UNKNOWN/RULE_NOT_IMPLEMENTED 和 NON_STOCK 最终 N/A 由 T07 执行入口保存；T06 不伪造已完成结果，不读取数据或另建参考单元。参考定义只保留在目标快照中，不增加用户勾选接口数。

新增 `IntegrityCheckQueue(int capacity)`，内部有界 `BlockingQueue<UUID>` 加相同容量的 `Semaphore`，提供：

```java
public Reservation reserve(); // 非阻塞；满时抛 INTEGRITY_QUEUE_FULL
public Optional<UUID> poll(Duration timeout) throws InterruptedException;
public final class Reservation implements AutoCloseable {
    public void publish(UUID checkId);
    @Override public void close();
}
```

名额覆盖已预留未提交与已入队未取出的任务。Reservation 内部只允许 OPEN->PUBLISHED 或 OPEN->RELEASED；重复 publish 拒绝，close 幂等，已发布后 close 不释放。publish 将非null UUID 加入队列；根据已预留总数<=capacity 的不变量必须可放入，不在事务提交后重新进行可失败的容量竞争。poll 成功取出时释放一个名额，因此 workers 正在执行的任务不占排队容量；超时/中断不释放未取出的名额。禁止暴露绕开 reservation 的 offer/add。

正常创建失败/校验失败不留名额。极端进程崩溃可能发生在数据库提交与 publish 之间，T06 不用伪事务包住两者：共享设计规定由 T07 启动时把所有未终结遗留任务标记 INTERRUPTED，不自动恢复旧快照。T06 的队列不启动线程、没有重启恢复职责。

### 5. 配置、错误和应用装配

新增 `IntegrityCheckProperties`（`@ConfigurationProperties("tensor.integrity")`），以 `@DefaultValue` 绑定下表，构造时复用 Settings 校验，`toSettings()` 返回同一组值。所有数字必须为正，workers 必须恰好为 1；不设置静默回退、不钳制值，也不新增 enabled 开关。

| 属性 | 默认值 |
| --- | ---: |
| max-symbols | 100 |
| max-range-days | 36600 |
| max-units | 4000 |
| queue-capacity | 20 |
| workers | 1 |
| scan-batch-size | 500 |
| max-scanned-rows-per-unit | 500000 |
| max-issues-per-unit | 20000 |
| unit-timeout-seconds | 120 |
| task-timeout-seconds | 1800 |

股票数在规范去重后计数，天数为闭区间自然日，单元数按第 4 节；等于各上限允许，超一项才抛 INTEGRITY_LIMIT_EXCEEDED。执行预算在本项绑定并暴露，T07 再消费；不宣称已经生效为运行时超时。

新增 `IntegrityCheckConfiguration`，启用该 properties，提供 IntegrityCheckJson、IntegrityCheckRepository、IntegrityCheckQueue 和 IntegrityCheckService Bean，复用现有 JdbcTemplate/PlatformTransactionManager/PluginRegistry/DatasetCatalog/UTC Clock。由 `ApplicationConfiguration` 显式 import；不得提前添加 SmartLifecycle 或 worker。

ErrorCode 增加 INTEGRITY_UNAVAILABLE、INTEGRITY_DEFINITION_CHANGED、INTEGRITY_QUEUE_FULL、INTEGRITY_LIMIT_EXCEEDED；仅 queue full 为 retryable=true。全量回归需同步 `GlobalExceptionHandler.status` 的穷尽 switch，分别映射 409、409、429、400。这是枚举编译和既有错误基础设施所需的最小适配，不新增完整性路由。既有 DownloadTaskContractTest 要求公共 ApiError 与 storedError 枚举覆盖整个 ErrorCode，因此同时追加 openapi-v1.yaml、download-task.schema.json 的共享错误枚举及 error-codes.md 说明，不新增完整性请求/响应模型。INTEGRITY_CHECK_NOT_FOUND 在 T09 查询入口实现时加入。

## Files

| 路径 | 责任 |
| --- | --- |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckService.java` | 受理、能力快照、严格范围、幂等与 Settings |
| `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckQueue.java` | 名额生命周期、有界发布与消费入口 |
| `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/IntegrityCheckSupport.java` | 默认纯本地日期校验扩展 |
| `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java` | 四个受理错误码 |
| `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java` | 上海受理日截止校验 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/IntegrityCheckProperties.java` | 默认值与启动配置校验 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/IntegrityCheckConfiguration.java` | 显式 Bean 装配，不启动线程 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java` | import 检查配置 |
| `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java` | 新枚举状态码分支 |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityCheckServiceTest.java` | 规范范围、全量哈希、先重放、边界和安全错误 |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityCheckQueueTest.java` | 预留、发布、关闭、消费和中断的容量不变量 |
| `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/integrity/IntegrityCheckServiceIT.java` | 实际 MySQL 的并发幂等、原子受理及失败释放 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/IntegrityCheckPropertiesTest.java` | 配置绑定、全部非法值阻止上下文启动 |
| `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/IntegrityCheckConfigurationTest.java` | Bean 装配与未启动 worker 的边界 |
| 既有 `TushareProPluginTest`、`GlobalExceptionHandlerTest`、完整性插件合同测试 | 纯本地日期、兼容性及新错误码回归 |
| `docs/runbook/configuration.md`、`docs/verification/DATA-INTEGRITY-T06.md` | 配置行为及实施后的真实证据 |

不修改 T05 V9 或证券表结构；如实施发现接口需要调整，先证明与既有 T05 合同兼容，不能绕过其完整计划和独立事务防御。保留隔离工作区已有暂存改动，新文件加入 Git。

## Tests

先写 service 的“原请求重放在插件变化/队列满之前返回旧任务”失败测试，再实现受理。IT 沿用 T05 Testcontainers MySQL 8.4.6 和实际 V9，不以 mock 事务或 skip 代替结果。

1. 对象键换序 hash 不变，symbols/apiNames 数组换序 hash 改变；同 ID 同请求只返回原任务，不同原请求冲突。修改调用方容器不污染保存请求。规则升级、插件停用和新增 API 后，旧请求仍不调用能力而找回旧报告；新 ID 使用旧 hash 拒绝。
2. apiNames 省略固定全量，显式子集仍使用全插件 hash；空数组、null、未知成员、未知 JSON 字段、错误类型拒绝。股票规范化去重且保留首次顺序；合法无本地记录股票保留。混合股票/NON_STOCK/缺描述的计划、快照及计数精确，依赖不额外生成单元。
3. 严格 ISO、非法日历日、反向日期、数据库日期边界、Tushare 上海午夜前后、endDate 等于受理日/晚一天；使用固定 Clock，确认插件方法不读系统时间或联网。旧 fixture 不覆盖新 default 方法仍兼容。
4. maxSymbols/maxRangeDays/maxUnits 分别测试等于上限与上限+1；测试去重前超限但去重后合法、闭区间单日为1和长计算防溢出。超限不触发 create/reserve，不缩减范围。
5. reserve 达 capacity、额外一次拒绝；未发布 close 释放、重复 close 不多放名额、发布后 close 不释放、成功 poll 释放、超时/中断不泄漏或多放名额；容量20的默认值以及容量1的并发竞争。publish 之前 poll 不可见任务。
6. 实际 MySQL 并发相同 ID/请求只有一份 task、完整单元与一次队列发布；不同请求相同 ID 一方成功一方冲突。额外用两个服务实例共用仓库/队列和 barrier 绕过单服务锁，覆盖 DuplicateKeyException 恢复分支，不据此实现分布式消费。
7. 实际创建第二个单元失败时 task/首单元回滚且名额可再使用；满队列提交后查询不到该 ID。并发不同 ID 接近容量时成功数量恰好等于剩余名额，无队列溢出或孤立已受理任务。保留队列未消费时新连接可读完整已提交计划，证明提交后才发布。
8. 原任务写入并发布后注入返回读取失败，随后同请求重放取得原 ID且没有二次发布；唯一冲突却查不到 submissionId 时返回 PERSISTENCE_FAILED，不能误报受理成功。仓库错误、日期/符号错误响应不泄漏输入与 SQL。
9. 每个配置独立设置0/负数失败，workers=2失败，默认值和自定义正值完整绑定。应用上下文装配服务/仓库/队列且没有完整性工作线程；scan/时间预算只绑定，T07 才执行。新错误码状态映射正确，既有下载/registry/插件合同回归。

```sh
mvn -o -f data-plane/pom.xml \
  '-Dtest=IntegrityCheckServiceTest,IntegrityCheckQueueTest,IntegrityCheckPropertiesTest,IntegrityCheckConfigurationTest,IntegrityPluginContractTest,TushareProPluginTest,GlobalExceptionHandlerTest' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -pl tensor-core -am \
  '-Dtest=IntegrityCheckServiceIT,IntegrityCheckRepositoryIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test

git diff --check
git diff --cached --check
```

期望实际执行、0失败/错误/跳过并 BUILD SUCCESS，验收记录填写实际数量和失败修复情况。MySQL/本机端口按环境授权运行，不改变既有 clean-main 合同门禁；本设计中的命令未因文档创建而被执行。

## Acceptance

全部看板 T06 结果可观察：新任务范围、全量能力快照和计划原子固定；旧请求在当前能力变化前重放且不重复入队；队列满/超限明确拒绝且不留已受理任务，事务失败释放名额；并发只有一份受理结果。全部默认值及正数/workers限制装配生效，Tushare 日期按受理上海日校验且股票存在性不影响受理。真实 MySQL、专项和既有后端回归通过，验收记录给出证据，新文件加入 Git。设计就绪不代表 T06 已实施。

## Risks

- 数据库提交和内存入队之间存在进程崩溃窗口；T07 必须按共享设计中断所有遗留非终结任务，不重新计算历史快照。本项不声称提供进程崩溃后的自动执行保证。
- 原请求与规范范围承担不同职责，不能为了友好去重/排序而改变 requestHash；能力全量快照不能因用户选了子集而缩减。
- 默认日期扩展是对 T02 可选能力的兼容补充，不能扩大为 core 内的来源分支、在线日历验证或发布时间推断。
- T05 工作区含既有 Studio/T01–T04 混合暂存基线，继续在 `.worktrees/data-integrity` 保留它们；不得全量提交/覆盖原分支。旧下载浏览器回归存在已记录的偶发 TASK_STATE_CONFLICT，不属于本项受理队列。
- 无待用户决定的产品范围问题；T07 执行、T09 HTTP 和后续前端仍分别验收。
