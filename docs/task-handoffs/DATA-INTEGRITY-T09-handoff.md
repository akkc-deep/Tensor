# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`。
- **Completed task:** `DATA-INTEGRITY-T08`，已记录COMPLETED。
- **Next task:** `DATA-INTEGRITY-T09`，按Order=9选择。
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T09-design.md`，完整设计已完成并回填。
- **Expected next status:** READY；链接本交接后执行NOT_STARTED→READY，不启动实现。

## Next Task

**DATA-INTEGRITY-T09 — HTTP API、错误与公开合同。** 通过六个端点提供本地能力、幂等创建、历史、进度详情、结果和问题页。具体DTO、严格参数、分页、精度、错误、历史兼容和合同验证均按专属设计实施，控制器复用既有服务，不新增规则/SQL/后台线程。

验收：首次202+Location、重放200、请求冲突409；无Token能力可用、业务UNKNOWN为200；三个列表固定过滤和排序，默认1/20、pageSize=1..100、越末页空；404任务缺失、其他已有错误映射保留。精确计数为字符串，null和保存的原范围/规则证据不变。HTTP专项、独立schema/既有合同、真实应用MySQL及全量单元测试通过，才能完成。

## Dependencies

### DATA-INTEGRITY-T02

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T02-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/integrity/TushareIntegrityPolicies.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/integrity/IntegrityDescriptor.java`。
- **Decision:** 本地能力与下载readiness独立，40个接口各有固定股票/日期轴/依赖/限制；不存在描述不冒充N/A。
- **Rationale:** 缺Token仍须检查本地数据，接口日期轴不能由下载模式猜测。
- **Constraint:** 不调用来源客户端，不使用下载find门禁阻挡本地检查；能力响应按当前服务返回的快照/顺序/hash映射，不硬编码版本。当前三行情为@2，其余来源覆盖仍@1。
- **Usage:** 调用service.capability；API descriptor按专属设计公开，downloadAvailability复用DownloadTaskService.capabilities和DownloadCapabilitiesResponse，保持localCheckAvailable独立。
- **Readiness evidence:** 看板已COMPLETED；`docs/verification/DATA-INTEGRITY-T02.md`记录100项策略与注册合同；当前T08最终回归Tushare474项通过，实际40接口及版本兼容仍受测试约束。

### DATA-INTEGRITY-T05

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T05-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckRepository.java`、`IntegrityCheckJson.java`；`docs/verification/DATA-INTEGRITY-T05.md`。
- **Decision:** task/result/issue长期保存，结果和问题原子提交；列表总数和items同一读取事务、稳定排序、过滤针对持久字段。
- **Rationale:** 前端必须能回查具体问题与当时规则，不使用当前规则改写历史。
- **Constraint:** 统计与键精确编码，未知为null；问题date=null不伪造日期，日期筛选排除null；历史不能重新计算当前覆盖。
- **Usage:** Repository.tasks/results/issues直接供薄控制器使用，find/progress缺失转新404；Result.report和Issue.issue原JSON映射，不暴露requestHash/unitKey/物理表定义。
- **Readiness evidence:** 看板已COMPLETED；T05真实MySQL原子写入、分页和约束有验收记录；T07最终真实MySQL组再次覆盖RepositoryIT27项。T08全量1411项及真实应用持久报告查询通过。

### DATA-INTEGRITY-T06

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T06-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckService.java`、`IntegrityCheckQueue.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/IntegrityCheckConfiguration.java`；`docs/verification/DATA-INTEGRITY-T06.md`。
- **Decision:** 原请求哈希重放先于当前能力校验；首次才规范股票、固定接口/范围和计划，预留队列后原子受理。
- **Rationale:** 请求响应丢失后可用同submissionId找回旧检查，不能在重放时因规则升级或默认API变化创建新报告。
- **Constraint:** 控制器保留原始对象、数组顺序与字段是否省略，不提前填默认接口或调用当前能力拒绝重放；已存在的4项INTEGRITY错误继续原映射。
- **Usage:** 严格JSON解析后直接service.submit，created决定202/200；Receipt使用返回的已有任务，limits取实际Settings。未知JSON字段/超限/规范化仍由service负责。
- **Readiness evidence:** 看板已COMPLETED；T06专项和真实受理/并发验证见其验收文档，T07再次运行ServiceIT8项；T08无Token真实受理与完整后端回归通过。

### DATA-INTEGRITY-T07

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T07-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckRunner.java`、`IntegrityCheckCoordinator.java`、`IntegrityCheckRepository.java`的Progress；`docs/verification/DATA-INTEGRITY-T07.md`。
- **Decision:** 后台已有独立单worker生命周期；完成进度只按已提交COMPLETED/ERROR算，执行状态与数据结论分离，未完成项保持UNKNOWN。
- **Rationale:** HTTP受理不是检查完成，任务COMPLETED也不代表数据PASS。
- **Constraint:** 读取历史不启动执行或恢复旧快照；COMPLETED计数包含ERROR子集，不能相加重复计数；保留INTERRUPTED、NOT_RUN、incomplete/issuesComplete。
- **Usage:** GET详情映射一次repository.progress的任务/状态数量，其他列表只读保存内容；复用现有装配，不新增生命周期Bean。
- **Readiness evidence:** 看板已COMPLETED；T07真实MySQL71项/1399项当时回归有证据；T08再次通过RunnerIT17项、ReadRepositoryIT12项及最终真实生产应用报告流程。

上述依赖一致：本地能力→原请求幂等受理→后台提交→持久只读HTTP映射。没有未解决的直接依赖冲突。

## Start Here

1. 完整阅读 `docs/task-designs/DATA-INTEGRITY-T09-design.md`。
2. 阅读共享 `docs/task-designs/DATA-INTEGRITY-design.md` 第1、2、5、7、8节。
3. 阅读上述Service、Repository、Json和Configuration的现有接口，以及 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadTaskController.java`、`GlobalExceptionHandler.java`、`web/dto/DownloadCapabilitiesResponse.java`。
4. 阅读 `docs/contracts/openapi-v1.yaml`、`error-codes.md` 和现有 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskContractTest.java`；核对Maven打包测试与普通test阶段的边界。

**First action:** 在 `IntegrityCheckControllerTest` 写专属设计首个RED：合法原请求POST，stub service返回created=true，要求202、Location、checkId、requestId且原始请求未改写；确认现有无路由返回404，再实现控制器/绑定/响应。用户显式启动后才将T09 READY→IN_PROGRESS。

## Risks

- 不能把HTTP200、COMPLETED或本地无缺口解释成Tushare生产完整；真实资料不足仍UNKNOWN。
- 公开JSON二次数值转换、当前描述覆盖旧报告、默认字段预填会破坏已验收语义。
- `verify-contracts.sh`的clean-main/已提交输入门禁在本隔离区不满足；T09按设计运行变更HTTP的独立schema及既有合同测试，全量发布门禁保留T13，不改门禁、不假报通过。
- 隔离区含Studio及T01–T08混合暂存基线。保留现状，仅暂存自己的修改，不全量提交或合回外部工作区。
