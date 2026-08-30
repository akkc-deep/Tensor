# M06 Core Persistence and Query Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现白名单 SQL、业务键预查、单事务批量 Upsert、准确计数和稳定服务端分页查询。

**Architecture:** SQL 标识符只来自启动验证后的 `DatasetDefinition`，值全部使用 `PreparedStatement` 绑定。写入在数据集 JVM 锁内执行“预查键 + Upsert”单事务；查询使用 COUNT、固定排序和 LIMIT/OFFSET。

**Tech Stack:** Java 21、Spring JDBC、Spring Transactions、MySQL 8.4、Testcontainers。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 上游调用和适配不在数据库事务内；只有预查与 Upsert 在事务内。
- JDBC 默认批大小 500，宽表可由元数据下调；单请求仍为一个事务。
- `insertedRows + updatedRows` 等于适配后的不同业务键行数。
- 查询不使用 `SELECT *`，客户端不能提交表名、列名、排序或 SQL 片段。
- 页码从 1 开始；页大小仅 20、50、100；空结果返回 `page=1,totalPages=0`。

## Project Inputs

候选输入为 persistence/query 文件和稳定 DatasetDefinition 契约。持久化与查询不依赖 Tushare 或 Vue 实现。

---

### Task M06-T01: SQL 标识符与 Upsert 模板（3.0h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/UpsertSqlFactoryTest.java`

**Interfaces:** `String create(DatasetDefinition definition)` returns one parameterized INSERT/ON DUPLICATE KEY UPDATE statement; identifiers are quoted after whitelist validation.

- [ ] Confirm task design; determine update columns for composite and fingerprint keys.
- [ ] Test exact daily SQL, reserved column `change`, invalid identifier rejection, business keys excluded from update and all source fields updated.
- [ ] Run targeted test; expect missing factory failure.
- [ ] Implement identifier regex/quoting and deterministic column-order SQL generation with only `?` value placeholders.
- [ ] Run tests and scan generated SQL to confirm no user values are interpolated.
- [ ] Commit as `feat(core): generate validated upsert SQL` when Git exists.

### Task M06-T02: 业务键编码与绑定（3.0h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/BusinessKeyExtractorTest.java`

**Interfaces:** `BusinessKey extract(DatasetDefinition, Map<String,Object>)`; binder maps `LocalDate`, `BigDecimal`, `Long`, `String`, `Instant`, null to correct JDBC setters.

- [ ] Confirm task design; align fingerprint value with M05 `FingerprintKeyCodec`.
- [ ] Test ordered composite values, fingerprint key, missing key rejection, null binding, UTC instant and BigDecimal preservation.
- [ ] Run targeted tests and confirm missing types fail.
- [ ] Implement immutable keys with structural equality and typed JDBC binding without `setObject` ambiguity for nulls.
- [ ] Run module tests.
- [ ] Commit as `feat(core): bind dataset keys and JDBC values` when Git exists.

### Task M06-T03: 已有键、数据集锁和计数（3.5h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/DatasetLockManager.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/ExistingKeyRepository.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/WriteCounts.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/ExistingKeyRepositoryIT.java`

**Interfaces:** `Lock acquire(DatasetKey)` uses fair `ReentrantLock`; `Set<BusinessKey> findExisting(DatasetDefinition,List<BusinessKey>)` batches bound-key queries.

- [ ] Confirm task design; freeze composite-key query strategy and maximum bind count.
- [ ] Write MySQL tests for no existing keys, all existing, mixed keys, composite keys and concurrent same-dataset lock ordering.
- [ ] Run the integration test; expect missing implementation failure.
- [ ] Implement fair per-dataset locks with cleanup and parameterized batched key lookup.
- [ ] Compute inserted/updated counts from set membership and assert the sum invariant.
- [ ] Run targeted integration and module tests; commit as `feat(core): preflight dataset keys and write counts` when Git exists.

### Task M06-T04: 单事务批量 Upsert（4.0h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`

**Interfaces:** `WriteCounts persist(AdaptedBatch batch)`; transaction propagation `REQUIRED`, default timeout 60 seconds.

- [ ] Confirm task design; confirm lock scope covers transaction commit and release.
- [ ] Write MySQL tests for all insert, all update, mixed, mid-batch SQL failure rollback, same-dataset concurrency, fingerprint idempotency and one batch `ingested_at`.
- [ ] Run integration tests; expect missing service failure.
- [ ] Implement acquire lock → transaction → existing-key lookup → JDBC batch Upsert → commit → unlock; batch size comes from metadata.
- [ ] Inject a deterministic failure after the first JDBC batch and prove the first batch rolls back.
- [ ] Run integration and module regression; commit as `feat(core): persist adapted batches atomically` when Git exists.

### Task M06-T05: 查询条件和分页 SQL（3.5h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QueryCriteria.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QuerySql.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/QuerySqlFactory.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/query/QuerySqlFactoryTest.java`

**Interfaces:** Criteria contains optional `tsCode`, `tradeDateFrom/To`, `annDateFrom/To`, page and pageSize; factory returns bound COUNT and page SQL plus values.

- [ ] Confirm task design; freeze stable ordering for composite and fingerprint datasets.
- [ ] Test no filters, each filter, AND combinations, unsupported filter, reversed date, invalid page size, explicit columns, fixed order and LIMIT/OFFSET binding.
- [ ] Run targeted test; expect missing factory failure.
- [ ] Implement metadata whitelist validation and SQL generation; never accept a client-supplied identifier.
- [ ] Run tests and inspect all SQL for explicit column lists.
- [ ] Commit as `feat(core): build safe dataset query SQL` when Git exists.

### Task M06-T06: 查询服务与页码归一化（3.0h）

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/DatasetPage.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/GenericQueryRepository.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/query/DatasetQueryService.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/query/DatasetQueryServiceIT.java`

**Interfaces:** `DatasetPage query(DatasetKey key, QueryCriteria criteria)` returns columns, map items, normalized page/pageSize, totalElements and totalPages.

- [ ] Confirm task design; freeze empty and out-of-range page semantics.
- [ ] Write MySQL tests for empty table, unfiltered paging, combined filter, total count, page beyond last, stable order and 152-column row.
- [ ] Run integration test; expect missing service failure.
- [ ] Implement COUNT first, normalize page, skip row query when total zero, then query current page with a row mapper preserving column order/types.
- [ ] Assert DECIMAL/BIGINT remain Java precision types for later string serialization.
- [ ] Run integration/module regression; commit as `feat(core): query datasets with stable server paging` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml -pl tensor-core -am verify`. M06 is complete only when MySQL 8.4 tests prove atomic rollback, accurate counts, idempotency, bound SQL, stable paging and full-width row retrieval.
