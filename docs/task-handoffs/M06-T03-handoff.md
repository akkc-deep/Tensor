# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M06-T02`
- **Next task:** `M06-T03`
- **Design document:** `docs/task-designs/M06-T03-design.md`
- **Expected next status:** `READY`；在本交接完整写入后执行真实的 `NOT_STARTED -> READY`。

## Next Task

- **ID:** `M06-T03`
- **Title:** 已有键预查、数据集锁和插入/更新计数
- **Goal:** 在 `tensor-core` 中交付按 `DatasetKey` 隔离的公平可重入 JVM 锁、最多 1000 个绑定参数的 MySQL 已有物理键分块预查，以及只按不同输入键与已有集合成员关系计算的插入/更新计数，供 M06-T04 在同一事务和锁范围内组合。
- **Scope:** 只修改 `data-plane/tensor-core/pom.xml`，且只增加 BOM 管理的 MySQL connector 测试依赖；只创建 `DatasetLockManager.java`、`ExistingKeyRepository.java`、`WriteCounts.java` 和 `ExistingKeyRepositoryIT.java`。不实现 Upsert、事务、服务编排、提交/回滚、查询 API，不修改 Surefire/Failsafe、既有生产合同或其他模块。
- **Acceptance criteria:** 冻结的三个生产公开合同和失败边界形成；同键公平/可重入/安全清理及不同键隔离成立；单列 scalar IN、多列 row-constructor IN、COMPOSITE 原序、FINGERPRINT `business_key`、1000 参数分块与明确 JDBC 类型均在固定官方 MySQL 8.4.6 上验证；`WriteCounts.from` 对不同键满足 `insertedRows + updatedRows == distinct keys`。严格 RED 后定向 8/8、标准 reactor `test`/`verify` 146/146、三层 Enforcer、依赖、静态、范围、格式、清理和精确五文件提交门禁全部通过。

## Dependencies

### `M06-T01`

- **Artifact:** `docs/task-designs/M06-T01-design.md`，`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java`、`UpsertSqlFactory.java`，`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/UpsertSqlFactoryTest.java`；实现提交 `029b344`。
- **Decision:** 所有定义派生的表名和列名必须再次满足 `^[a-z][a-z0-9_]{1,63}$` 并统一反引号引用；COMPOSITE 物理键是 `definition.businessKey().fields()` 原序，FINGERPRINT 物理键只为内部 `business_key`。
- **Rationale:** 已验证的定义仍不能替代 SQL 边界白名单；物理键必须与已验证 MySQL schema 及后续 Upsert 模板完全一致，且任何行值都不能进入 SQL 文本。
- **Constraint:** M06-T03 必须复用 `SqlIdentifierPolicy`，不得接受调用方表名/列名、复制或放宽正则、查询 FINGERPRINT identity fields、使用 `SELECT *` 或拼接业务键值；不得修改两个既有生产类。
- **Usage:** `ExistingKeyRepository` 用 policy 引用定义表名和有序物理键列，并用同一键模式决定 SELECT 列和 WHERE tuple 结构。
- **Readiness evidence:** M06-T01 为 `COMPLETED`；`029b344` 精确交付两个生产类和 6 项测试，聚焦 6/6、模块 138/138，完成时全仓 `verify` 209/209、Enforcer、静态、范围、清理和最终无 Critical/Important 审查均通过。

### `M06-T02`

- **Artifact:** `docs/task-designs/M06-T02-design.md`，`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java`、`BusinessKeyExtractor.java`、`JdbcValueBinder.java`，`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/BusinessKeyExtractorTest.java`；实现提交 `2bd8996`。
- **Decision:** `BusinessKey` 是不可变、非空、有序值列表的结构键；COMPOSITE 按定义 fields 原序，FINGERPRINT 是 M05 已生成 `business_key` 的单元素键；JDBC 值只用 `setString`、`setDate`、`setLong`、`setBigDecimal`、UTC `setTimestamp` 和 typed `setNull`，不用 `setObject`。
- **Rationale:** 有序结构相等让预查结果与本批不同键集合可直接比较；直接消费物理指纹避免第二种摘要算法；明确 setter 和冻结 schema JDBC type 消除驱动对 null/类型的歧义。
- **Constraint:** M06-T03 必须按键值原序绑定和映射结果，不排序、不重新哈希、不记录键值、不修改 `BusinessKey`/extractor/binder；COMPOSITE 类型继续使用已冻结的 STRING→VARCHAR、TEXT→LONGVARCHAR、DATE→DATE、MONTH/ENUM→CHAR、LONG→BIGINT、DECIMAL→DECIMAL 映射，FINGERPRINT 使用 CHAR。
- **Usage:** `ExistingKeyRepository` 先按 `BusinessKey` 结构相等去重和校验宽度，再用 `JdbcValueBinder` 逐参数绑定；RowMapper 按同一物理列原序重建 `BusinessKey`，`WriteCounts.from` 用其集合成员关系计算计数。
- **Readiness evidence:** M06-T02 为 `COMPLETED`；`2bd8996` 精确交付三个生产类和 8 项测试，聚焦 8/8、reactor `test`/`verify` 146/146、三层 Enforcer、静态、范围、格式、清理和最终无 Critical/Important/Minor 审查均通过。

两项直接输入没有冲突：M06-T01 冻结“哪些物理键标识符可安全进入 SQL”，M06-T02 冻结“这些物理键值如何形成结构键并明确绑定”；M06-T03 只把两者机械组合为参数化 SELECT、不可修改已有集合和成员计数。项目所有者另已批准单列 scalar IN、多列 MySQL row-constructor IN、每查询最多 1000 个绑定参数，以及仅增加 MySQL connector 测试依赖；完整设计已把这些裁决转换为精确 SQL、分块、公开合同和测试门禁，全部裁决已经确定且相容。

## Start Here

1. 完整读取 `docs/task-designs/M06-T03-design.md`。
2. 核对 `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 Global Constraints、Task M06-T03 和 M06-T04 的消费边界，以及 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 10.2～10.4。
3. 完整读取 `docs/task-designs/M06-T01-design.md`、`docs/task-designs/M06-T02-design.md`，再核对上述五个直接消费的生产类及两份测试风格。
4. 首个实施动作：在干净工作树运行设计中的 reactor 基线，确认 plugin-api 79 + core 67 = 146/146，并用 dependency tree 再确认 MySQL driver 尚不存在；随后只增加批准的 POM test 依赖并完整创建 `ExistingKeyRepositoryIT.java`，运行显式 `-Dtest=ExistingKeyRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false`，取得只因三个生产类型缺失而产生的 `testCompile` RED。

## Risks

- 锁和准确计数只适用于 TRD 冻结的单应用实例；多实例必须另行设计数据库级协调。
- row-constructor IN 是 MySQL 8.4 专用策略；数据库变更需要重新设计，不能在本任务增加未验证方言。
- `ExistingKeyRepositoryIT` 不被当前默认 Surefire 模式发现，必须显式运行定向 8/8；标准 `test`/`verify` 仍应为 146/146，不得改生命周期。
- Testcontainers 依赖 Docker daemon 和固定官方 `mysql:8.4.6`；不可用时属于环境阻塞，不得 skip、改用 H2 或浮动标签。
- 准确性最终依赖 M06-T04 把预查和 Upsert 放在同一 Spring 事务并让锁覆盖 commit/rollback；本任务只提供可组合边界，不声明事务外原子性。
