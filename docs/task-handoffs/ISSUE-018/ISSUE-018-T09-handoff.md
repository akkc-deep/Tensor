# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T08`，已先记录 COMPLETED、结果、四条 Maven 门禁和独立审查证据。
- **Next task:** `ISSUE-018-T09`，按预定义 Order 选中的下一项。
- **Design document:** `docs/task-designs/ISSUE-018-T09-design.md`，全文复核、独立就绪审查通过，并已先回填看板。
- **Expected next status:** `READY`；本交接写入、核对并链接后执行 `NOT_STARTED -> READY`，尚未启动实施。

## Next Task

`ISSUE-018-T09`：任务 HTTP 合同、应用装配与日志。

提供七类任务/能力路由，严格绑定 SINGLE/RANGE 与控制请求；白名单 DTO、JSON Schema、示例和 OpenAPI 一致。显式导入 T08 生命周期，为 Service/Runner/Coordinator 注入同一 runId、数据库和配置。增加向后兼容的核心只读提交准备与已提交事件观察接口，app 负责脱敏日志和 MDC；不得以 HTTP 202 记为下载成功。

验收包括：首次接收 202+Location、等价重放 200、未知任务 404、状态/定义冲突 409、队列满 429；后台失败查询仍 200。插件停用、关闭或恢复时已有任务仍可找回；新请求强类型绑定、非法输入零任务/零来源。上游阻塞时先返回已持久接收结果。列表每行与详情使用同一快照的状态/计数；控制操作保留客户端版本。生产装配、提交后日志、观察异常隔离及旧同步回归有真实 MySQL/HTTP 和构建证据。

## Dependencies

### ISSUE-018-T03

- **Artifact:** `docs/task-designs/ISSUE-018-T03-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskQueryService.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskJson.java`；同模块 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskServiceTest.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskServiceIT.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskQueryServiceTest.java`。核心类型的完整目录均为 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/`。
- **Decision:** submit 在当前开关/插件/容量检查之前查 submissionId；同义请求返回已存记录，不更新版本或重复入队。RANGE 重放使用存储策略，SINGLE 元数据移除后仅原样规范化快照可找回。新接收零来源，QueryService 只依赖数据库，detail 使用一次 snapshot。
- **Rationale:** HTTP 响应丢失后必须找回原任务，停用或定义变化不能使已接收任务失联；跨快照拼接会使拆分期间的状态/计数不一致。
- **Constraint:** 所有服务入口拒绝外层事务；Controller 不直接访问仓储/插件。保留定义先于新参数校验的顺序、未知完整性门禁、8 KiB 参数边界、原 SQL 排序与分页。参数/请求基本结构错误与旧键等价性冲突按 T09 设计区分。
- **Usage:** T09 的 prepareSubmission 只读入口复用现有私有 replay/current/normalize；新的 Bound/Replay 请求变体避免旧键被当前能力或 Codec 提前拒绝。Controller 最终调用 submit 再次裁决。列表先固定分页成员，再对各 ID 调 detail，状态和计数一起映射；现有核心没有 HTTP DTO，不直接序列化内部记录。
- **Readiness evidence:** 看板 T03 为 COMPLETED；原专属专项70、全单元679及双包门禁通过。当前 T08 全回归947仍通过，显式专项再次执行 ServiceTest20、ServiceIT11；查询合同在全单元中继续通过。T03 历史证据及实际命令保留在看板，不以这些结果代替尚未实施的 T09 HTTP 证据。

### ISSUE-018-T08

- **Artifact:** `docs/task-designs/ISSUE-018-T08-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskCoordinator.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRunner.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/BatchCommitService.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadTaskConfiguration.java`；core 同包测试 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskCoordinatorTest.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRecoveryIT.java`，app `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/DownloadTaskConfigurationTest.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/config/DownloadTaskConfigurationIT.java`。runner/仓储/共同事务是 T08 消费的既有输入，不新增看板直接依赖。
- **Decision:** retry/resume 复验版本、终态、生命周期和无活动租约，再检查定义/readiness/容量；controls 仅是纯提示。单个协调锁与提交前租约覆盖实际执行到 finally 退出；每秒轮询。STARTING 未完成时不接收，RECOVERING 只恢复数据库事实，FAULTED 不能自动解除；幂等找回仍可用。close 不取消/中断实际 worker，等待退出后恢复当前启动所有未完成任务。
- **Rationale:** Future 状态不能证明来源退出，数据库错误不能证明提交回滚。只有已退出和已核对的数据库事实允许恢复；成功叶子不重发，已获准事务允许完成。
- **Constraint:** runId 在数据库/catalog 验证后生成，Service/Runner/Coordinator 必须使用同一个 bean；enabled/maxRangeDays 同源。现有 lite 配置未导入生产，本项必须显式接线。正常关闭保留 SUCCEEDED，PERMIT_LOST 不以旧许可改写；新控制确认终态后会清除旧排除记录，即使重排回执丢失。不得增加执行器、改变锁序、事务归属、V8、40注册或34项生产 RANGE 门禁。
- **Usage:** 使用已实现 retry/resume/controls 与 lite 配置完成路由和装配。现有仓储尚无日志观察器；按 T09 设计新增兼容构造器及 afterCommit 事件，证券成功事件必须加入 T04 原事务，记录安全不可变值。观察回调可能仍处于资源/数据集锁释放之前，禁止数据库/来源/业务重入；RuntimeException 不能污染已提交结果。HTTP 接收独立记录，回执不明时不补造历史逐批事件。
- **Readiness evidence:** 2026-09-12 T08 四条设计命令均退出0：显式专项184（含 RecoveryIT16、CoordinatorTest16、ConfigurationTest3、ConfigurationIT4，以及真实仓储/事务回归），全单元947，生产947+4、验收947+4+3；各生命周期前端24文件/170项及 Vite 构建通过，失败/错误/跳过均0。MySQL8.4.6，Java21；独立最终审查通过。日志为 `/tmp/issue018-t08-focused-final.log`、`/tmp/issue018-t08-units-final.log`、`/tmp/issue018-t08-production-final.log`、`/tmp/issue018-t08-acceptance-final.log`，完整命令和结果见看板 T08 Verification evidence。

直接输入无未解决冲突：T03 提供持久接收/查询和幂等语义，T08 增加动态门禁、控制与生命周期；T09 负责正式 HTTP/配置/日志。T09 新增准备入口与观察器均是待实施内容，不冒充现有接口。运行手册由 T12 消费 T09 合同后完成。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T09-design.md` 全文，使用已固定的七类路由、绑定优先级、DTO、装配、事件时序及测试命令。
2. `docs/task-designs/ISSUE-018-design.md` §3.3、§3.11、§3.13、§4、§5.1，以及看板 T09 的范围与验收。
3. 本交接两个直接输入的专属设计、实际 Service/Query/Coordinator/Repository 和验证记录。
4. `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/DownloadBindingConfiguration.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/DownloadParameterResolver.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`；`docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`。

第一项实施动作：新增 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskRequestBindingTest.java`，以生产绑定配置、precisionModule 和受控既有参数形状读取合法 SINGLE 提交为尚不存在的 DownloadTaskRequest，先观察缺失类型/绑定模块的失败。再实现最小绑定，并立即补充同 submissionId 在插件停用后仍重放成功的用例；不重新设计任务，不先写生产 Controller。

收到明确启动请求后记录 `READY -> IN_PROGRESS`，保留本交接作为入口上下文。按专属设计运行显式 MySQL/HTTP 专项、全单元、生产和验收构建；新增文件加入 Git，保持 `feat/download-by-date-range` 及当前暂存成果，不自动提交。T09 完成后先记录结果，再按 Order 准备 T10。

## Risks

- 34 项生产 RANGE 仍需 T13 真实来源证据；受控 HTTP AVAILABLE 仅为测试，不能开放实际接口或关闭母 issue。
- 单实例和普通来源有限 I/O 超时前提不变；不能靠取消 Future 或 HTTP 断开释放执行许可。
- 列表采用有界 N+1，每行一致但跨行不是同一时刻；JSON int64 超过 JavaScript 安全整数范围时，客户端须保真处理。
- afterCommit 日志不承诺故障下恰好一次；持久表是唯一结果事实。不得把原始异常、凭证、JDBC URL 或完整响应写入日志。
- MySQL 使用既有 Colima 和8.4.6，正常本机权限可能为 Docker/Mockito 所需，不清理用户容器；main/干净HEAD发布脚本仍按 T12 和既有流程执行。
