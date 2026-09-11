# ISSUE-018-T03：能力发现、幂等接收与任务查询

任务：[ISSUE-018 看板 T03](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t03)。直接输入：[T01 合同](ISSUE-018-T01-design.md)、[T02 仓储](ISSUE-018-T02-design.md)。共同来源：[总体设计](ISSUE-018-design.md) §3.3、§3.6–3.8、§3.10–3.11、§3.13。

## 做什么

实现核心任务接收与历史查询：单次 / 区间能力可组合，合法提交只经本地检查创建持久化 QUEUED 任务，同键相同规范化请求找回原任务，同键不同请求拒绝；查询以数据库快照为准。

新增 `DownloadTaskService`、`DownloadTaskQueryService` 及相应单元 / MySQL 集成测试。接收流程不调用 download、downloadBatch、plan、assess、adapt 或 persist，不启动线程、worker、通知器、Controller、retry / resume 状态转换，不改变来源 YAML、40 项注册、V8 或旧同步 DownloadService。允许调用可选插件的本地 batchDescriptor 和纯 sourceParameters 完成能力及具体参数组合检查。

## 怎么做

### 文件、类型与装配边界

所有新生产代码放 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/`，不导入 app / Web / Tushare。DTO 使用服务内嵌 record，不添加通用服务框架。

```java
DownloadTaskService(PluginRegistry plugins, DatasetCatalog datasets, AdapterRegistry adapters,
        ParameterValidator validator, DownloadTaskRepository repository, DownloadTaskJson json,
        Clock clock, UUID activeRunId, Settings settings);
record Settings(boolean enabled, int maxQueuedTasks, int maxRangeDays) {}
record Submission(UUID submissionId, DatasetKey datasetKey, DownloadMode mode,
                  Map<String, Object> params) {}
record SubmissionResult(DownloadTask task, boolean created) {}
record SingleCapability(boolean available, ApiDescriptor api) {}
record DownloadCapabilities(SingleCapability single, BatchDownloadDescriptor range) {}
DownloadCapabilities capabilities(DatasetKey key);
SubmissionResult submit(Submission request);
void validateReplay(DownloadTask task);

DownloadTaskQueryService(DownloadTaskRepository repository);
DownloadTaskRepository.Page<DownloadTask> tasks(DownloadTaskRepository.TaskFilter filter,
                                               int page, int pageSize);
Optional<DownloadTask> findSubmission(UUID submissionId);
DownloadTaskRepository.TaskSnapshot detail(UUID taskId);
DownloadTaskRepository.Page<DownloadBatch> batches(UUID taskId,
        DownloadTaskRepository.BatchFilter filter, int page, int pageSize);
```

Settings 的队列 / 范围上限必须为正，提供 `Settings.defaults()` 返回 true / 100 / 36600。构造参数不可空，activeRunId 由外部提供；T08 / T09 在 Flyway 和数据集验证完成后提供启动 ID 与配置并装配唯一服务实例，本项测试直接传入固定 UUID / Clock，不提前增加 Spring bean。

Submission 防御复制 map，边界拒绝空身份 / 模式 / params 及非法 key / 非字符串值（含显式 null），错误为 PARAM_INVALID，不能借复制 map 的 NPE 泄漏输入。缺少已声明必填参数继续由 ParameterValidator 返回 PARAM_REQUIRED；它负责股票、日期、默认值、未知字段及顺序相关的区间规范化。所有最终持久化参数再经 `writeTaskParams` 的 8 KiB 限制。

新增服务内嵌 `static final class TaskException extends TensorException`，构造只接收 ErrorCode，message 复用 `new DownloadTaskRepository.StoredError(code).message()`。不保留原始 Throwable cause。已分类 TensorException 原样传播；参数错误沿用 ParameterValidationException 字段信息；元数据提供者的意外异常转换 DATASET_MISCONFIGURED；JSON 入参验证异常转 PARAM_INVALID；写入 / 查询错误保留仓储分类。配置关闭的新接收用 PLUGIN_DISABLED，队列满用 TASK_QUEUE_FULL。

### 能力与当前定义

1. 在 PluginRegistry.descriptors 中按 pluginId 找唯一记录，再按 apiName 找唯一原 ApiDescriptor。不存在或重复为 DATASET_MISCONFIGURED；不使用会先过滤 downloadAvailable 的 MetadataQueryService.listApis。
2. `single.api` 保留原单次描述；`single.available` 取当前 registry.find、DatasetCatalog.find、AdapterRegistry.find 均存在。能力查询是元数据查询，不受 settings.enabled 的接收开关影响。接口身份不存在仍失败，不伪造元数据。
3. 当前插件可用且注册输入完整、并实现 BatchDownloadSupport 时调用本地 batchDescriptor；返回 present 描述直接保留其参数顺序、日期轴、规划方式、完整性规则、版本和 availability。null Optional / 抛出意外异常为 DATASET_MISCONFIGURED。
4. 普通插件、empty descriptor 或当前插件 / 数据集 / 适配器不可用时返回明确不可用 RANGE 描述：空参数、空端点 / 日期字段 / 规划方式、splittable=false、UNKNOWN、availability=UNSUPPORTED、policyVersion=`unsupported-v1`。原因分别为固定 `Range download is not supported` 或 `Plugin or dataset is unavailable`。这是本地不可用说明，不作为可执行策略保存。
5. 新提交先要求 settings.enabled、registry.find 存在及唯一描述 downloadAvailable；不满足为 PLUGIN_DISABLED。缺 DatasetDefinition / DatasetAdapter 为 DATASET_MISCONFIGURED。SINGLE 选原 ApiDescriptor；RANGE 要求可选描述 AVAILABLE，否则 BATCH_DOWNLOAD_UNAVAILABLE，并用原 apiName/displayName/category、QueryMode.date_range、range.parameters 构造独立 ApiDescriptor。不覆盖旧 SINGLE 描述。
6. `ParameterValidator.validate(selectedApi, raw)` 得到规范化值。RANGE 从 descriptor 的 startParameter / endParameter 读取日期，沿用严格 YYYYMMDD 和闭区间，`DAYS.between(start,end)+1 <= maxRangeDays`；同日合法，不限制为过去日期。超过上限为 TASK_LIMIT_EXCEEDED。
7. RANGE 再调用纯 `sourceParameters(apiName, normalized, requestedRange)` 验证组合条件，返回值经 `writeBatchParams` 检查字符串 map / 凭证 key / 16 KiB。它不创建初始批次，也不保存这次试算参数。插件分类拒绝直接传播；非法返回值为 DATASET_MISCONFIGURED。这使 T06 的 BSE / 未验证 BJ 等纯参数拒绝在接收前生效，core 不硬编码交易所或 Tushare 字段名。SINGLE 不增加该步骤。
8. 使用 T02 的 `definitionHash(selectedApi,dataset,mode,rangeOrNull)` 和 `policySnapshot(mode,rangeOrNull)`，不将凭证、开关或预算写入摘要。当前定义选择、参数规范化 / 纯组合检查各组织为私有方法，在 submit 与 validateReplay 共用，禁止分别复制一套规则。

### 提交顺序与幂等

服务持有一个私有 final ReentrantLock，单实例所有 submit 共享。每个公开用例先拒绝调用方已有数据库事务，抛 IllegalStateException；接收锁在任何仓储事务之前取得，并以 finally 释放。锁中只执行本地元数据 / 参数操作与仓储短事务，不等待上游。T08 在同一服务增加控制方法时复用这个锁与检查方法，维持接收锁→任务行→批次行顺序。

`submit` 的固定顺序：

1. 校验请求基本形状，取得接收锁，`repository.findSubmission(submissionId)`；必须在当前接收开关、插件可下载性、能力、范围预算和队列检查之前查既有键。
2. 若已存在：先比较 datasetKey 和 mode，不同直接 SUBMISSION_CONFLICT；若 params 已与已存规范化 params 相等，直接返回原 task、created=false，不访问插件或再校验当前定义 / 容量。否则按下面的重放规范化规则比较 requestHash；相同仍返回原 task，不更新任务、版本、时间、批次或队列，任何任务状态都可找回。不同为 SUBMISSION_CONFLICT。
3. 若不存在：按“能力与当前定义”完成检查和规范化，计算快照。最后在同一接收锁内执行 queuedCount；`>= maxQueuedTasks` 时 TASK_QUEUE_FULL，不 INSERT。计数包含所有 QUEUED，不能只数当前启动 ID。
4. 生成 UUID.randomUUID() 的 taskId，使用一次 clock.instant() 和构造时 activeRunId，调用 `repository.insert(NewTask(...))`。仓储提交成功后才返回 SubmissionResult(task,true)。不在服务包外再包事务，不使用 afterCommit 返回尚未提交的对象。
5. DuplicateKeyException 时只按 submissionId 重读：查到则复用步骤 2 比较；未查到说明不是该幂等键竞争（例如 taskId 冲突），返回固定 PERSISTENCE_FAILED，不把所有唯一冲突都当作合法重放。数据库唯一约束仍是最后裁决者，接收锁不是幂等数据来源。

重放规范化不检查当前能否执行：RANGE 用已存策略的有序 ParameterDescriptor 构造校验描述；SINGLE 使用 registry.descriptors 中仍保留的原单次参数元数据，忽略 enabled / credentialConfigured / downloadAvailable。已停用插件仍保留这些描述，因而空白 / 大小写等同义请求仍能找回。RANGE 为此给 DownloadTaskJson 增加包内 `BatchDownloadDescriptor readRangePolicy(String snapshot)`：复用已有严格解析和 record 重建，要求 mode=RANGE，返回重建描述；不新增第二套 JSON schema 或公开任意反序列化能力。现有 validatePolicySnapshot 及全部大小 / 错误边界不变。

若 SINGLE 元数据已被永久移除，规范化快照原样重放仍由步骤 2 的无元数据分支支持；无法依据已有元数据确认等价的其他参数返回 SUBMISSION_CONFLICT，不能猜测旧类型或按字段名猜股票规则。历史仍可按 taskId / submissionId 查询。重放分支参数校验不通过同样为 SUBMISSION_CONFLICT；基本 JSON 形状错误仍为 PARAM_INVALID。定义变化不会阻止原样找回已有任务，但会阻止 validateReplay 重执行。

### 为后续控制复用的检查

`validateReplay(task)` 先检查当前接收开关、插件 / 适配器 / 数据集和对应模式可用性，解析当前定义并计算 definitionHash；与已存值不同立即返回 TASK_DEFINITION_CHANGED。必须先比较定义再用当前参数合同校验已存参数，避免字段改名导致 PARAM_REQUIRED / PARAM_INVALID 掩盖定义变化。摘要相同才继续规范化参数、范围及纯组合检查；同一含义的可用性、展示文案、凭证或预算调整不改变 hash。不检查 ErrorCode.retryable，不根据 task.status 决定是否允许 retry / resume，不改变任务 / 批次、版本或队列。

队列检查与入队必须由未来控制方法在上述同一接收锁里包住；validateReplay 单独通过不预留队列名额，不代表 worker 已退出或状态允许。这些协调条件和实际 requeue 是 T08 的职责。T03 不对外提供任意状态修改或任意事务回调。

### 查询与返回事实

DownloadTaskQueryService 只依赖 Repository，不依赖注册表、当前定义、凭证或任务开关。tasks / findSubmission 直接调用对应仓储读方法，参数检查异常转 PARAM_INVALID（固定文案）。detail 调用一次 `repository.snapshot(taskId)`，task empty 为 TASK_NOT_FOUND；原样返回同快照的 task 与 Counts，不分开 findTask/counts。batches 先 findTask 确认存在（没有删除操作），再委托仓储分页；不存在 TASK_NOT_FOUND。

页码保留请求 page，page>=1、pageSize=20/50/100，默认值由未来 HTTP 层提供 1/20，不把超尾页改成最后一页。复用 Repository 的 TaskFilter / BatchFilter，排序、where/count 一致性、默认排除 SPLIT 及显式 includeSplit 由 T02 保证。Page 只有 total/items；T09 增加外部 page/pageSize 并做白名单 DTO 映射。

Counts 的实际访问器为 totalBatches/pending/running/succeeded/failed/splitBatches/sourceRows/insertedRows/updatedRows；T09 再映射 pendingBatches 等 HTTP 名称。planReady=false、0 批次仍返回真实 QUEUED/RUNNING/FAILED 状态，不推导成功。canRetry / canResume 需要 T08 的控制条件，T03 返回事实不提前伪造操作许可。

## 如何测试

新增 core `download/task/DownloadTaskServiceTest.java`、`DownloadTaskServiceIT.java`、`DownloadTaskQueryServiceTest.java`，扩展 `DownloadTaskJsonTest.java` 覆盖包内策略读取入口。使用真实自定义插件 / 描述与标准 ParameterValidator；所有可能请求或写证券的方法在测试插件中计数并主动抛错，所有接收 / 查询场景断言调用数为 0。能力与纯 sourceParameters 调用单独记录，不混作上游请求。

第一步先写 SINGLE 同键重放和规范化等价测试，观察缺少服务的失败，再实现接收最小闭环；按以下场景增加行为断言。

- 普通单次插件不实现可选接口也可接收；RANGE 为 UNSUPPORTED，错误码明确。可选插件 AVAILABLE / NEEDS_VERIFICATION / UNSUPPORTED 和 empty 描述正确，不将 UNKNOWN 变成功；意外元数据异常固定脱敏。
- 使用 `symbol/from/to` 的非 Tushare 描述证明未硬编码股票 / 日期字段；现有四类 RANGE 形状各用已成立参数合同验证规范化。RANGE 必填 / 未知 / null / 非字符串、逆序、闰日、同日、上限恰好 / 超一天、未来区间、8 KiB / +1；纯参数组合拒绝时零持久任务。
- 相同 submissionId、同义股票大小写 / 空白 / 默认值参数只一 task；来源 / API / mode / 规范化值不同为 SUBMISSION_CONFLICT。已存在任务在停用插件、关闭任务接收、队列满和终态后仍找回，原 task/version/createdAt/queuedAt 不变。RANGE 重放使用持久策略；SINGLE 原样快照重放不依赖元数据，缺元数据的其他请求明确冲突。
- 首次创建持久 QUEUED、generation=0、version=1、planReady=false、无批次，快照 / 两 hash / 时间与返回 task 一致；另一连接在 submit 返回后立即可见。原始凭证 / 错误正文不进入快照。
- validateReplay：字段 / 业务键 / 参数合同 / 策略版本变化拒绝，文案 / 可用性恢复不改变 hash；参数能力或队列检查不能产生状态变更；旧错误码 retryable=false 不自行禁止检查。
- query service 的未知任务、合法空页、20/50/100、非法 page / filter、稳定排序和默认 / 显式 SPLIT 视图；停用插件或关闭接收仍查到数据库事实。detail 只消费 snapshot；MySQL 中并发 split 的一致性基础复用 T02 测试，新增用例断言服务不二次拼接状态 / 计数。

ServiceIT 使用 Testcontainers MySQL 8.4.6，以 ScriptUtils 执行 app 唯一生产 V8，JdbcTemplate / DataSourceTransactionManager 创建真实仓储。测试辅助方法通过 `DatasetCatalog.class.getDeclaredConstructor(List.class)`、`setAccessible(true)` 和 `newInstance(List.of(definition))` 构造只含显式测试定义的目录；反射仅位于测试中，不修改生产构造器、不新建无关证券表、不扩大生产注册集合。

并发容量场景使用同一服务、maxQueuedTasks=1、两个不同键和 latch 同时提交，结果必须一创建、一 TASK_QUEUE_FULL，最终 QUEUED 恰好 1。另用测试 JDBC 代理在 findSubmission 空结果后交错另一连接插入相同键，恢复 INSERT，真实触发唯一冲突并证明同请求返回原任务、不同请求冲突；生产类不增加测试开关。数据库故障导致提交未成功、无新任务；所有公开用例在已有事务中拒绝，不能由调用方 rollback 撤销一个已经报告接收的任务。

从根目录执行（Java 21；MySQL IT 使用与 T02 相同的 Colima/Docker 环境）：

```sh
mvn -f data-plane/pom.xml -Dtest=DownloadTaskJsonTest,DownloadTaskServiceTest,DownloadTaskServiceIT,DownloadTaskQueryServiceTest,DownloadTaskRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

所有命令退出 0，必选 MySQL IT 实际执行且无失败 / 跳过。测试必须证明队列和返回结果，不以 mock 调用次数替代数据库接收事实。

## 如何验证

看板 T03 的接受、幂等、零上游、容量锁和历史一致查询条件逐项有测试证据；状态查询不依赖当前插件。保留旧同步、40 项 YAML、V1–V8 和证券事务；没有启动 worker 或新增 HTTP 路由。服务可直接在 T09 装配，T08 可复用同一锁与检查而不重写接收逻辑。

实际实现 / 审查 / 命令与结果写看板；先记录 T03 完成，再按 Order 准备 T04 专属设计和交接。本文只规定实现，不记录执行结果。

## 依赖什么信息

- T01：BatchDownloadSupport 的 batchDescriptor 是本地读取、sourceParameters 是纯函数；DateRange 闭区间、参数顺序和 UNKNOWN 门禁不可改变。ParameterValidator / T01 显式 ApiDescriptor 绑定入口分别供 core 校验与后续 T09 强类型 HTTP 绑定；core 不导入 Codec。
- T02：固定 insert/findSubmission/queuedCount/snapshot/分页 API、独立事务、唯一约束、UTC 和 JSON/hash 是接收事实基础；NewTask 不接受外部 requestHash。仓储异常仅有固定分类，服务不解析数据库文本识别冲突。
- PluginRegistry.descriptors 保留停用插件的单次描述，find 只返回可下载插件；DatasetCatalog 与 AdapterRegistry 给出注册输入。历史查询必须绕开这些可用性条件。
- 项目设计的单实例、100 队列和 36600 自然日默认值；活跃启动 ID、启动恢复、关闭和活动 worker 判定由 T08/T09 装配。T03 通过不代表 HTTP 202、后台执行或真实完整性已经交付。
- RANGE 真实开放与 BSE/BJ 的规则依据仍归 T06/T13；本项用受控纯参数规则证明拒绝发生在入库之前，不凭测试开放接口。SINGLE mode-only 策略不保存完整旧参数 schema；永久删除元数据后不猜旧规范化规则，原样规范化快照重放和历史查询继续可用。
