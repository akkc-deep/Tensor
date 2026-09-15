# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T03`；已先记录 COMPLETED 和四条验证命令的通过证据。
- **Next task:** `ISSUE-018-T04`，按预定义 Order 选择的后继。
- **Design document:** `docs/task-designs/ISSUE-018-T04-design.md`，详细设计已完成、独立复核通过并回填看板。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，不启动实现。

## Next Task

`ISSUE-018-T04`：证券数据与批次成功状态原子提交。

使每批证券数据、计数与批次 SUCCEEDED 在同一数据库事务中提交或回滚，包括空批次；执行许可和锁顺序拒绝旧 worker 的迟到写入。

范围为 PersistenceParticipant、PersistenceService 重载、BatchCommitService，以及 PersistenceServiceIT / BatchCommitServiceIT。使用既有数据集锁、业务键计数、Upsert、T02 参与方法和 60 秒事务；不增加 worker、Controller、失败循环、重试 / 恢复、迁移或来源注册。

验收要求：数据集锁→任务行→批次行；入口拒绝外层事务；证券写入、计数或成功标记失败全部回滚，调用者随后用独立仓储事务记录 FAILED；空批成功同样原子。SQL 故障、旧许可、等待锁超时、截止后的在途提交和旧同步回归有真实 MySQL 证据。完整接口、错误分类、测试装配与命令已在专属设计固定。

## Dependencies

### ISSUE-018-T02

- **Artifact:** `docs/task-designs/ISSUE-018-T02-design.md`；core `download/task/DownloadTask.java`、`DownloadBatch.java`、`DownloadTaskRepository.java`、`DownloadTaskJson.java`；app 唯一 `db/migration/V8__create_download_task_tables.sql`；core `DownloadTaskRepositoryIT.java`。
- **Decision:** lockTask / lockBatch / succeedBatch 是仅有的参与调用方事务的仓储入口，要求实际事务存在且不自行提交。任务 RUNNING、activeRunId、generation 及批次归属 / RUNNING / generation 是执行许可。其他仓储公开用例拥有独立事务并拒绝外层事务。
- **Rationale:** 只有证券写入和批次成功标记参加相同事务，才能避免有证券数据却无成功状态；仅一次锁外快照不能隔离已失效的 worker。
- **Constraint:** 用同一 DataSource / 事务管理器，在取得数据集锁后才能开始证券事务并调用低层入口；先锁任务再锁批次。succeedBatch 内部锁兄弟批次以校验成功累计 long 计数；不能绕过或复制这套逻辑。failBatch 必须在证券事务已经结束后另行调用，旧许可不能覆盖新状态。
- **Usage:** BatchCommitService 先用独立 findTask 做快速许可 / 身份 / 截止检查，beforeWrite 用锁定的任务和批次再次验证；afterWrite 调用 succeedBatch 保存三计数和 SUCCEEDED。测试通过 insert / claimTask / savePlan / claimBatch 构造状态，通过 recoverStoppedTask / requeue / claimTask 构造旧许可窗口。只消费 T02 数据事实，不依赖 T03 TaskException 或接收服务。
- **Readiness evidence:** T02 看板已完成，原 MySQL 8.4.6 专项为 JSON 20 + 仓储 17 + Flyway 47 = 84，证明 V8 新建 / 升级 / 重复迁移、状态约束、许可和参与事务。T03 最终专项再次实际执行 RepositoryIT 17 + JSON 20，连同接收 / 查询测试共 70 项全部通过；全后端单元 679、生产包 679+4、验收包 679+4+3 均通过，失败 / 错误 / 跳过均 0，前端 170 项及构建通过。这些证据证明输入可用，尚未证明证券共同事务。

直接输入与总体设计的事务归属和锁顺序一致，无未解决的依赖冲突。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T04-design.md` 全文。
2. `docs/task-designs/ISSUE-018-design.md` §3.9、§3.13–3.14、§5.1。
3. T02 专属设计、仓储三个参与方法及上述状态 / 测试输入。
4. core `persistence/PersistenceService.java`、`DatasetLockManager.java`、`WriteCounts.java`、`GenericUpsertRepository.java` 和现有 `PersistenceServiceIT.java`。

第一个实施动作：在 PersistenceServiceIT 写带真实参与者的空批事务用例，断言 beforeWrite / afterWrite 在同一实际事务内、返回 (0,0)、回调 SQL 在返回后可见；观察缺少接口 / 重载的失败后，再实现设计规定的最小参与接口和重载。不重新设计本任务。

在用户下次明确启动请求后记录 `READY -> IN_PROGRESS`，保留本交接为入口上下文。

## Risks

- 旧 PersistenceService 曾加入外层事务，专属设计按本任务明确的新锁序要求收紧为两个重载都拒绝；需替换旧加入测试，新增独立事务提交前持锁证明。旧无参与空批快速返回仍保留，真实参与者的空批必须进入事务。
- 失败标记由事务外调用者（后续 T07）执行；T04 IT 直接调用 failBatch 证明其独立性，不在 BatchCommitService 新增失败循环，也不把提交回执不明当作自动重新下载依据。
- 进入证券写入前复验截止；等待锁期间过期要拒绝，已通过 beforeWrite 的在途事务仍可在自身 60 秒内完成。任务总体成功汇总不属于本项。
- 测试依赖 Java 21、MySQL 8.4.6 和 Docker；沿用 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。clean verify 不代替专属设计第一条真实 IT 命令。
- 当前 `feat/download-by-date-range` 工作区保留 T01/T02/T03 的暂存成果，未创建提交；不要重置这些输入。T04 仅准备 READY，尚未实现。
