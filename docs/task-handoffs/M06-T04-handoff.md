# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M06-T03`
- **Next task:** `M06-T04`
- **Design document:** `docs/task-designs/M06-T04-design.md`
- **Expected next status:** `READY`；在本交接完整写入并链接后执行真实的 `NOT_STARTED -> READY`。

## Next Task

- **ID:** `M06-T04`
- **Title:** 单事务批量 Upsert 与回滚
- **Goal:** 在 `tensor-core` 中把目录准入、业务键提取、公平数据集锁、已有键预查、准确计数和元数据批大小 Upsert 组合为一个 Spring `REQUIRED` 事务；锁在实际最外层事务提交或回滚后才释放，任一中间 JDBC batch 失败不留下部分写入。
- **Scope:** 只创建 `GenericUpsertRepository.java`、`PersistenceService.java` 和 `PersistenceServiceIT.java` 三个文件；复用现有目录、SQL、绑定、键、锁、预查与计数合同。排除 POM、既有生产类、Flyway/YAML、下载/适配/API/查询、Spring Bean 配置、生产故障注入、声明式事务、数据库/分布式锁和测试生命周期修改。
- **Acceptance criteria:** 两个 final 生产类保持冻结公开表面；空批次零锁/零事务/零数据库；非空批次在锁内以同一 `REQUIRED` 事务完成预查、计数和全部元数据大小 JDBC batch，加入既有事务时锁保持到最外层 `afterCompletion`；固定 MySQL 8.4.6 的插入、更新、混合、第二批失败回滚、并发、指纹幂等、统一 `ingested_at` 和边界测试 8/8，两项 mutation、标准 reactor 146/146、Enforcer、静态、范围、格式、清理和精确三文件提交门禁全部通过。

## Dependencies

### `M06-T03`

- **Artifact:** `docs/task-designs/M06-T03-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/DatasetLockManager.java`、`ExistingKeyRepository.java`、`WriteCounts.java`；实现提交 `65ad1d7` 与审查修复提交 `d49d6c2`。
- **Decision:** 同一 `DatasetKey` 使用公平、可重入、引用安全清理的 JVM 锁；已有物理键在调用方事务连接上用最多 1000 个绑定参数的 scalar/row-constructor `IN` 分块预查；`WriteCounts.from` 只按不同输入键与已有集合成员关系计算 inserted/updated，且不读取 MySQL affected-row。
- **Rationale:** 锁覆盖“预查到实际事务完成”才能让同 JVM 并发请求串行看到稳定数据库快照并得到准确计数；参数化分块限制查询规模并保持物理键语义；集合计数消除 MySQL Upsert affected-row 模式差异。
- **Constraint:** M06-T04 必须在事务外先提取键和获取锁，在同一 Spring 线程绑定事务内依次调用预查、计数和全部 Upsert；正常、异常、事务启动失败和同步注册失败都必须恰释放一次锁，加入外层事务时不得在 `persist` 返回时提前解锁。不得修改、复制、重新哈希或绕过 M06-T03 及其冻结的 M06-T01/T02 SQL、键和 binder 合同。
- **Usage:** `PersistenceService` 获取 M06-T03 锁句柄并把其所有权移交给事务同步的 `afterCompletion`，在 callback 中用 `ExistingKeyRepository.findExisting` 和 `WriteCounts.from` 得到结果；`GenericUpsertRepository` 机械复用传递冻结的 `UpsertSqlFactory` 与 `JdbcValueBinder` 执行写入。
- **Readiness evidence:** M06-T03 在权威看板中为 `COMPLETED`；固定 MySQL 8.4.6 定向测试 8/8、reactor `test`/`verify` 146/146、三层 Enforcer、依赖/静态/范围/格式/清理门禁和分块/跨线程隔离两项受控 mutation 均通过，独立复审无 Critical、Important 或 Minor，最终代码相对 `d49d6c2` 无差异。

该唯一直接输入内部约束一致：锁负责单 JVM 串行化与完成后释放，预查负责同事务物理键快照，计数负责集合语义；M06-T04 的完整设计只增加事务生命周期和安全批量执行，不改变任何输入合同。M06-T03 设计中较早的“`finally` 解锁”消费说明已由项目所有者于 2026-09-03 批准的 M06-T04 事务同步方案具体化：只有同步注册前失败才由外层 `finally` 解锁，注册后由最外层事务 `afterCompletion` 解锁，因此不存在未解决冲突。

## Start Here

1. 完整读取 `docs/task-designs/M06-T04-design.md`。
2. 按 `docs/superpowers/plans/2026-09-03-m06-t04-atomic-persistence.md` 的严格 TDD 顺序执行。
3. 核对 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 Global Constraints、Task M06-T04 与 Module Gate，再读取 `docs/task-designs/M06-T03-design.md` 及上述三个直接消费生产类。
4. 首个实施动作：在干净工作树运行设计中的 reactor 基线，确认 plugin-api 79 + core 67 = 146/146；随后只完整创建 `PersistenceServiceIT.java`，运行显式 `-Dtest=PersistenceServiceIT -Dsurefire.failIfNoSpecifiedTests=false`，取得只因两个生产类型缺失而产生的 `tensor-core:testCompile` RED。

## Risks

- 锁和准确计数只适用于 TRD 冻结的单应用实例；多实例必须另行设计数据库级协调。
- 加入既有 `REQUIRED` 事务时，`persist` 返回的 counts 只有外层最终提交后才代表已提交结果；调用方不得提前发布成功。
- `ReentrantLock` 必须由获取线程释放；当前方案依赖同步 Spring 事务在同线程触发 `afterCompletion`，不适用于异步或响应式事务完成模型。
- 60 秒 timeout 只约束本服务新建的事务；加入既有事务时沿用外层定义。
- `PersistenceServiceIT` 不被当前默认 Surefire 模式发现，必须显式运行定向 8/8；标准 `test`/`verify` 保持 146/146，不得改生命周期。
- Testcontainers 依赖 Docker daemon 和固定官方 `mysql:8.4.6`；不可用时属于环境阻塞，不得 skip、改用 H2 或浮动标签。
