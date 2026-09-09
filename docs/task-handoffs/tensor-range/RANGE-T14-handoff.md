# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T13`，已记录 `COMPLETED`，完成记录先于本设计和交接创建。
- **Next task:** `RANGE-T14`，按预定义 Order 选择，Order为14；准备时观察为NOT_STARTED。
- **Design document:** `docs/task-designs/RANGE-T14-design.md`（[完整设计](../../task-designs/RANGE-T14-design.md)），已完整读取、通过独立就绪／规格／质量评审并链接到看板。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，不自动启动实现。

## Next Task

`RANGE-T14`：下载与失败任务 HTTP 接口。

同版迁移元数据下载投影及 `POST /api/v1/downloads`，增加失败任务列表、详情与原任务execute三个端点；接入现有参数绑定、首次／重试用例和只读存储查询，返回准确的本轮结果、错误快照和安全保存条件。Controller保持输入、调用和响应职责。

可观察验收：

- 38项使用新区间参数，11项及fixture保留原条件；公开策略仅五字段。旧日期alone/mixed按现行合同报PARAM_INVALID，缺新区间一端且无旧键仍PARAM_REQUIRED。
- 正常五种outcome为200；保存／提交未确认及其他停止错误带原Core快照。18字段结果、数值／null和requestId准确，不用输入taskId补回未知的任务入口。
- 列表独立筛选、分页20/50/100、稳定双DESC；详情展示原始日期四状态、原公共条件及全部当前失败选择器，同日不同股不合并，离线保存标识不隐藏。
- GET只读，静态blocker按设计优先级及同一个slot Snapshot计算，零来源回调／槽位获取／写入；可解码不兼容任务返回详情和blocker，不可解码或空孤儿主表返回QUERY_FAILED。
- execute只接受零字节body，直接一次调用原重试用例，零预读。合法输入busy409优先于不存在404；同步线程和Core槽位不因客户端等待超时取消。
- 按设计执行Web／Core、八类显式MySQL、两次完整构建、合同和独立评审，记录本轮证据。页面、真实socket／进程中断和生产来源由后继任务验收。

## Dependencies

### RANGE-T03

- **Artifact:** [T03设计](../../task-designs/RANGE-T03-design.md)、[合同验证](../../verification/RANGE-T03-contracts.md)、[OpenAPI](../../contracts/openapi-v1.yaml)、[错误目录](../../contracts/error-codes.md)。
- **Decision:** `/api/v1`四下载／任务端点、38＋11参数、五字段策略、18字段结果、四原始日期状态、26码及阶段响应是现行合同；200明确部分失败和错误快照不能混淆。
- **Rationale:** 前后端需要区分确认结果、当前失败记录和未知范围，不能将HTTP通信失败当作业务回滚或历史完成。
- **Constraint:** 不新增取消、编辑、核对、幂等令牌或历史协议；GET不联网／占槽，execute不接收任何可编辑body；保留required null和数字JSON，X-Request-Id一致。
- **Usage:** 按设计将DTO逐字段映射，显式覆盖错误码／快照阶段、失败任务JSON与分页；合同文档冻结，运行迁移事实记入T14验证和追踪。
- **Readiness evidence:** T03已COMPLETED；本轮T13合同8组及4个变异检查PASS，T14设计独立复核再次对照当前schema／目录，无未解决合同冲突。这不表示T14运行接口已通过。

### RANGE-T04

- **Artifact:** [T04设计](../../task-designs/RANGE-T04-design.md)、[插件合同验证](../../verification/RANGE-T04-plugin-contracts.md)；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ApiDescriptor.java`、`download/DownloadPolicy.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`。
- **Decision:** parameters是下载投影，sourceParameters是原来源定义；HTTP只输出公开策略五字段。registry描述符保留下线元数据，find仅提供可下载插件。
- **Rationale:** 公共区间形状和实际来源能力不同；来源／恢复／日历内部证据不能作为公开可执行承诺。
- **Constraint:** 元数据、绑定及实际执行同版切换；不修改49份Dataset、策略或生产空来源／日历注册表。显示名不可用时保留保存ID，不因下线过滤任务。
- **Usage:** ApiDescriptorResponse改为parameters和专属policy DTO；只读查询使用registry快照，明确已知source/calendar静态拒绝，不能调用plugin回调来试探canExecute。
- **Readiness evidence:** T04已COMPLETED；T13两次完整构建继续覆盖当前SPI／生产图，58份受保护资源摘要保持。T14设计已独立核对实际descriptor、registry和公开schema，READY／PASS。

### RANGE-T05

- **Artifact:** [T05设计](../../task-designs/RANGE-T05-design.md)、[参数验证](../../verification/RANGE-T05-parameter-conversion.md)；`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/DownloadParameterResolver.java`、`DownloadRequestDeserializer.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadParameterConverter.java`。
- **Decision:** resolveDownload负责下载投影绑定及31天；mapRetry从原公共map和完整selector精确重建，原始日期仅供展示，旧缺两端可恢复。
- **Rationale:** 当前失败3／7日不能用原1～10日重取；公共输入不能发给旧单次下载入口，非法旧字段需按现行HTTP合同拒绝。
- **Constraint:** T05通用validator的required-before-invalid不修改。T14设计已明确在Web resolveDownload解析descriptor后先拒绝range旧日期键，解决旧日期alone错误码差异；查询兼容检查先于source拒绝，不伪造策略或清洗原map后放行。
- **Usage:** Deserializer切resolveDownload；新增只读查询先按设计检查原参数／selector，再复用mapRetry核对已知候选。原始日期独立计算四状态，不能由明细反推或用完整selector长度新增上限。
- **Readiness evidence:** T05已COMPLETED；T13最终Core130项包括转换器、精确重试及旧缺日期等相关回归，App helper31项通过。上述优先级差异已在T14设计固定最小修正，独立就绪评审PASS，无待决输入。

### RANGE-T12

- **Artifact:** [T12设计](../../task-designs/RANGE-T12-design.md)、[首次编排验证](../../verification/RANGE-T12-initial-execution.md)；`DownloadService.java`、`DownloadExecutionResult.java`、`DownloadExecutionException.java`、`DownloadExecutionSlot.java`，均位于 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/`。
- **Decision:** executeInitial四参用例返回不可变本轮结果及安全停止快照；新旧首次与重试共用同一个进程槽位，实际执行线程finally释放。
- **Rationale:** HTTP只投影确认事实，不按请求数或数组长度重新分类；客户端超时和数据库确认是独立事实。
- **Constraint:** 保留旧Core八参构造器／两个四参方法，不在HTTP回退旧execute。T14仅给slot加一次原子读取的只读Snapshot，不改变lease生命周期或新增异步／取消入口。
- **Usage:** 首次POST调用executeInitial一次；结果／异常统一映射18字段。新增观测入口使用真实outcome，nullable Long显式数字序列化，不改变全局records精度或将PARTIAL／UNCONFIRMED记为success。
- **Readiness evidence:** T12已COMPLETED；T13最终Core130项包含首次Service20及slot/result回归，显式InitialDownloadServiceIT11项通过；两次完整构建及T13最终集成评审PASS，生产执行代码保持。

### RANGE-T13

- **Artifact:** [T13设计](../../task-designs/RANGE-T13-design.md)、[精确重试验证](../../verification/RANGE-T13-exact-retry.md)；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/RetryDownloadService.java`、App `ApplicationConfiguration.java`。另直接复用其已消费的T10只读合同：`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/retry/RetryTaskStorageService.java`、`RetryTaskRepository.java`及[存储验证](../../verification/RANGE-T10-failure-storage.md)。
- **Decision:** execute(UUID,RequestId)先占槽位，再唯一find当前原项、完整前置和精确执行；业务与删除原子确认，再次失败只更新原原因。storage.list(Criteria)和find(UUID)已提供独立只读快照，分页／双DESC及批量明细读取可直接复用。
- **Rationale:** 重试只处理实际仍存在的明细，不创建新任务、恢复已删除项或把展示范围当执行范围；GET展示当前提交事实，不重新构造执行结果。
- **Constraint:** POST不为日志／按钮先find或调用query服务；不传编辑参数，不用路径ID／后验查询补确认任务入口。全闭S/R/I/U=0，最后删除未知ID／remaining均null；确认最后删除后框架异常则ID=null／remaining0。读取结构坏／空孤儿的GET安全映射由T14查询边界负责，原记录不删／不修。
- **Usage:** 重试Controller直接委托一次execute，日志身份取结果或专用异常快照；新增五依赖RetryTaskQueryService复用storage只读方法和注册快照，不改T10写入／T13算法。App装配一个共享查询bean。
- **Readiness evidence:** T13已COMPLETED；最终Core130（Retry29）、Retry MySQL11、七类App最新复合73（原70加Retry11替换旧8）、helper31全绿。verify795、acceptance798项Java及各170前端测试／构建通过；评审后仅补测试并定向通过，生产摘要不变。Core／App规格质量及最终集成／证据评审PASS，58资源、分支／HEAD和任务外原暂存保持。

上述直接输入已逐项比较。旧日期alone错误优先、静态兼容先于来源拒绝、Long序列化、空孤儿读取、全闭计数及最后删除未知等差异均在T14完整设计中明确处理，无未解决冲突；前驱证据不计为T14实施通过。

## Start Here

1. 完整读取 [T14专属设计](../../task-designs/RANGE-T14-design.md)，重点§3输入迁移、§4结果／错误、§5只读安全投影、§6blocker／日期及Tests矩阵。
2. 按看板顺序读 [TRD §8／§10.3](../../design/Tensor_区间下载_TRD_v1.0.md)、[OpenAPI](../../contracts/openapi-v1.yaml)、[错误目录](../../contracts/error-codes.md)、当前DownloadController／DTO／DownloadControllerIT，再核对上述五组直接输入。
3. 核对当前T12／T13共享装配、T10只读接口、实际ObjectMapper和现有HTTP测试；保留原分支／HEAD与任务外暂存。

**第一个实施动作：**明确启动后先记录分支／HEAD／索引和58份资源摘要，在 `DownloadRequestBindingTest.java`、新 `RetryTaskControllerTest.java`、新 `RangeResponseContractTest.java` 写设计规定三组先行期望：旧daily.trade_date拒绝且range调用executeInitial；busy有效不存在ID为409／find零次且非空body先400；COMMIT_UNCONFIRMED的null N／remaining及确认小计正确输出。运行：

```sh
mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,RetryTaskControllerTest,RangeResponseContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

区分缺接口编译、测试设置／环境错误和可执行行为RED，再按完整设计实施。后续八类显式MySQL、两次完整构建和评审均按设计命令执行；clean前保存实际XML／日志，不照抄前驱数量。新增正式文件显式加入Git，不提交／发布。本交接不自动启动T14实现。

## Risks

- canExecute是静态兼容与进程快照，点击后仍可409／404或来源失败；不提供跨数据库／进程的原子版本或永久幂等。
- 全局Long／BigDecimal精度策略与新结果数值JSON不同，必须用实际mapper验证，保留必需null和旧records字符串精度。
- 历史条件或原因不能直接当安全HTTP文本；展示过滤不改原map、不作为执行输入。不可解码／孤儿记录只报告查询失败，不隐藏或修复。
- 后端接口迁移后前端仍需T15～T17更新；完整构建通过不等于浏览器闭环。真实socket／进程中断留T18，真实来源留T20；生产空能力、49项REQUEST及ISSUE-008九项“不依赖，未解决”保持。
