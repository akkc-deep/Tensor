# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T07`，已先记录 COMPLETED、实现结果、四条 Maven 门禁及独立审查证据。
- **Next task:** `ISSUE-018-T08`，按预定义 Order 选择的后继。
- **Design document:** `docs/task-designs/ISSUE-018-T08-design.md`，已全文复核、通过独立就绪评审并回填看板，可直接实施。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，本次仅完成后继准备。

## Next Task

`ISSUE-018-T08`：手动重试、恢复与后台生命周期。

在现有 DownloadTaskService 增加 `retry(UUID,long)`、`resume(UUID,long)` 和 `controls(DownloadTask)`，新增 DownloadTaskCoordinator 管理单 worker、每秒数据库轮询、启动恢复、活动租约与正常关闭。通过 app 的 DownloadTaskConfiguration 提供可显式导入的生命周期 bean；T09 再提供生产服务与属性装配、导入配置并暴露 HTTP。

验收要求：FAILED / PARTIAL_FAILED 重试只重排 FAILED，INTERRUPTED 恢复只重排中断失败批并继续 PENDING；成功叶子、SPLIT、计划和累计计数保留。三批第二批失败后仅再次下载第二批，成功计数从 2 增至 3，成功叶子尝试数不变。版本竞争只有一次转换成功，队列满、定义变化或动态插件不可用均不改原任务。启动只按数据库事实重算成功或标中断，零自动上游请求；旧实际调用未退出时不得释放租约、恢复或启动替代 worker。正常关闭等待实际退出，保留已获准事务和全部成功终态，并恢复本启动未领取的 QUEUED。显式 MySQL 专项、全单元、生产包和验收包门禁须取得实际证据。

## Dependencies

### ISSUE-018-T07

- **Artifact:** `docs/task-designs/ISSUE-018-T07-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRunner.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTask.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/BatchCommitService.java`；`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRunnerTest.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRunnerIT.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskServiceTest.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskServiceIT.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRepositoryIT.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/BatchCommitServiceIT.java`；`data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixtureDownloadTaskRunnerTest.java`。仓储、任务及原子提交是 T07 消费的既有输入，不新增看板直接依赖。
- **Decision:** runner 同步执行一次 `runNext(BooleanSupplier)`，不创建线程或恢复任务；返回 IDLE / FINISHED / NEEDS_RECOVERY / PERMIT_LOST 和已知任务、许可、固定错误码。已有计划和 sourceParams 重用，成功叶子不重发。Service 包内 executionDefinition 复用 current → definitionHash → normalize；动态 readiness 由执行或控制入口另行检查。T08 的租约必须在提交 runnable 前登记，实际调用退出后才释放。
- **Rationale:** 数据库提交异常可能只是回执丢失，Future.cancel 也不证明来源退出。共同事务及实际调用所有权决定是否可以恢复，不能依据取消标记或一次异常猜测状态。共用定义校验避免控制操作用新参数错误掩盖定义变化。
- **Constraint:** 沿用 Service 的 admissionLock 作为唯一协调锁，保留原构造器及未绑定时的既有 submit 语义；同键找回优先于动态准入门禁。retry/resume 使用客户端 expectedVersion，先验证状态、协调器和无全局活动租约，再验证定义与当前 readiness、容量，最后 requeue。requeue 保留累计计数，runRequestCount 到下一次 claim 才清零。保留 runner 公开表面、Repository / V8、插件合同、40 项注册、旧同步入口和 34 项生产 RANGE 待验证门禁。证券事务保持数据集锁 → 任务行 → 批次行；不在持有协调锁时调用来源，close 不在锁内等待 worker。
- **Usage:** 通过已有 requeue / recoverStoppedTask 完成受版本及启动 ID / generation 约束的转换；FINISHED 只消费已存终态，PERMIT_LOST 不以旧许可改写。NEEDS_RECOVERY 在实际退出后核对 snapshot 并恢复，连接不可用保持暂停；taskId=null 仅作数据库探针，不猜任务。executor 拒绝或 worker 意外异常进入独立 FAULTED，禁止自动探针解除；仅保留查询、幂等找回与关闭。复用 T07 三批、阻塞 SINGLE、真实提交回执故障和旧许可测试输入，证明生命周期边界。
- **Readiness evidence:** 看板 T07 为 COMPLETED。2026-09-12 四条设计 Maven 命令均退出 0：显式专项 149 项（core 145、fixture 4），含 MySQL 8.4.6 的 RunnerIT 8 / CommitIT 18 / RepositoryIT 17 / ServiceIT 11 / PersistenceIT 16；全单元 926 项，生产 926 + 4 包合同，验收 926 + 4 + 3 包合同。失败 / 错误 / 跳过均为 0，各完整生命周期前端 24 文件 / 170 项及 Vite 构建通过。独立审查无重要生产缺陷，五项测试证据缺口补齐并复核通过；真实 doCommit 后回执故障证明已提交计划、树、数据及成功状态不会自动重发或降级。完整命令与日志路径见看板 T07 Verification evidence；这些已记录结果不代表 T08 生命周期已实现。

直接输入与 T08 设计无未解决冲突：T07 提供同步执行和退出协议，T08 提供共享锁、租约、手动控制及恢复；T09 负责生产接线和 HTTP。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T08-design.md` 全文，使用其固定接口、校验顺序、生命周期状态、恢复规则、测试命令及验收标准。
2. `docs/task-designs/ISSUE-018-design.md` §3.7–3.10、§3.13–3.14、§5.1–5.2，以及看板 T08 的任务边界。
3. 上述 T07 专属设计、同步 runner、Service 的接收锁和 executionDefinition、Repository 的 requeue / recoverStoppedTask、BatchCommitService 的停止边界及对应测试。
4. `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`、`data-plane/tensor-app/src/main/resources/application.yml`，核对既有数据库 / catalog 装配；本项新增 lite 配置由测试显式 register，T09 才以 @Import 接入生产。

第一个实施动作：新增 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRecoveryIT.java`，建立 T07 真实三批任务，第二批 SOURCE_NETWORK_ERROR、第一和第三批已提交；调用尚不存在的 service.retry(taskId, expectedVersion)，先观察缺少入口失败，再实现最小控制路径，证明下一轮仅第二批再次下载、累计计数与尝试数正确。不要重复创建或补写设计。

收到明确启动请求后记录 `READY -> IN_PROGRESS`，保留本交接作为入口上下文。按 T08 设计依次运行显式 MySQL 专项、全单元、生产和验收构建；不能用 clean verify 替代显式 IT。保持 `feat/download-by-date-range` 及已有 T06 / T07 暂存成果，新增文件加入 Git，不自动提交。完成时先记录 T08 outcome 与验证，再按 Order 准备 T09 专属设计和交接。

## Risks

- 单实例前提不变；启动 ID / generation 的旧许可隔离不等于多实例协调，新进程启动前须停止旧实例。
- 普通插件须自行提供有限 I/O 超时；永不返回时正常 close 也须等待，不能用取消或等待超时释放租约。已获准事务仍允许提交，最后全部成功时保留 SUCCEEDED。
- 提交回执不明按数据库事实恢复；数据库不可用时保持暂停，关闭可保留未恢复事实给下一次启动，不声称已保存 INTERRUPTED。FAULTED 须重启应用，不能混入可恢复数据库探针路径。
- 全局 worker 活动期间 retry/resume 暂不可用，普通新 submit 仍可排队；controls 只是提示，操作仍复验版本、定义、可用性与容量。
- T09 必须导入 lite 配置并给 Service / Runner / Coordinator 注入同一 runId 与一致设置；本项配置测试不能替代 T09 生产接线或 T12 进程验收。
- MySQL 使用现有 Colima 和 8.4.6 镜像，先核对连接，不清理用户容器。34 项生产 RANGE 仍由 T13 逐项验证开放，当前基础设施证据不关闭母 issue。
