# M06-T04 Atomic Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `PersistenceService.persist(AdaptedBatch)` so preflight counting and metadata-sized JDBC Upsert batches run atomically under the per-dataset JVM lock, with unlock delayed until the actual outer transaction completes.

**Architecture:** `PersistenceService` validates the catalog/batch contract and extracts keys before locking, then composes the existing lock, key lookup, count, and Upsert collaborators in a programmatic Spring `REQUIRED` transaction. `GenericUpsertRepository` revalidates the batch boundary, requires an active transaction, and binds one safe `UpsertSqlFactory` statement through Spring JDBC collection batching using the metadata batch size.

**Tech Stack:** Java 21, Spring JDBC and Transactions 6.2.x through Spring Boot 3.5.16, JUnit 5.12.2, AssertJ 3.27.7, Testcontainers 1.21.4, MySQL 8.4.6.

## Global Constraints

- Work directly on the current `main` checkout and modify only the three Java files named by the approved task design.
- Do not modify any POM, existing production type, migration, YAML, test lifecycle, or other module.
- Keep upstream download and adaptation outside the transaction; only existing-key lookup and all Upsert batches execute inside one transaction.
- Use `TransactionTemplate` with `PROPAGATION_REQUIRED` and a 60-second timeout; when joining an outer transaction, retain the dataset lock until that outer transaction's `afterCompletion`.
- Derive SQL, column order, JDBC types, physical keys, and batch size only from the validated `DatasetDefinition` and existing M06 collaborators.
- Do not use affected-row counts, `setObject`, client SQL, statement value interpolation, row-at-a-time updates, batch commits, production failure hooks, or unconditional `finally` unlock after synchronization ownership transfers.
- Fixed MySQL validation uses `mysql:8.4.6`; Docker unavailability is a blocker, never a reason to skip or replace the test database.

---

### Task 1: Add the complete transaction and batch contract as a failing integration test

**Files:**
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`
- Reference: `docs/task-designs/M06-T04-design.md`
- Reference: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/ExistingKeyRepositoryIT.java`
- Reference: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/catalog/DatasetStartupValidatorTest.java`

**Interfaces:**
- Consumes: `DatasetCatalog.find(DatasetKey)`, `DatasetLockManager.acquire(DatasetKey)`, `ExistingKeyRepository.findExisting(DatasetDefinition,List<BusinessKey>)`, `WriteCounts.from(List<BusinessKey>,Set<BusinessKey>)`, `AdaptedBatch`, and real MySQL schema validation.
- Produces: a permanent, explicit `*IT` contract for `GenericUpsertRepository` and `PersistenceService` without changing default Surefire discovery.

- [ ] **Step 1: Confirm the clean 146-test baseline**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

Expected: plugin-api 79/79 and core 67/67, total 146/146 with zero failures, errors, or skips; parent/plugin-api/core Enforcer executions pass. If Mockito cannot attach in the restricted JVM, rerun the identical command in the already authorized JVM environment before classifying a failure.

- [ ] **Step 2: Create one complete eight-test `PersistenceServiceIT`**

Use a static Testcontainers database and no Spring application context:

```java
@Testcontainers(disabledWithoutDocker = false)
final class PersistenceServiceIT {
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"));

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        // Drop/create the isolated COMPOSITE and FINGERPRINT tables used below.
    }
}
```

The file must contain exactly these eight ordinary `@Test` methods and concrete assertions:

```java
@Test void enforcesPublicSurfaceAndRejectsInvalidBatchesBeforeSideEffects();
@Test void insertsCompositeRowsInMetadataSizedJdbcBatches();
@Test void updatesExistingCompositeRowsInRequiredSixtySecondTransaction();
@Test void countsMixedCompositeRowsFromPreflightMembership();
@Test void rollsBackEarlierJdbcBatchAndReleasesLockWhenLaterBatchFails();
@Test void retainsDatasetLockUntilJoinedOuterTransactionCompletes();
@Test void keepsFingerprintWritesIdempotentWithoutRehashing();
@Test void bindsOneBatchIngestedAtAcrossAllJdbcBatches();
```

Implement test helpers in the same test file only. They must:

- construct real `DatasetDefinition` and `AdaptedBatch` values for `batchSize=2`, with COMPOSITE columns `(ts_code, trade_date, amount, note)` and a FINGERPRINT definition whose adapted columns append `business_key`;
- build a validated `DatasetCatalog` through the existing startup validator against the actual test tables, not by adding a production constructor or bypass;
- assemble the production graph from `DatasetCatalog`, `DatasetLockManager`, `ExistingKeyRepository`, `GenericUpsertRepository`, and `DataSourceTransactionManager`;
- use a delegating JDK proxy around the real `DataSource`/`Connection`/`PreparedStatement` to record `executeBatch` sizes while still executing against MySQL;
- use a recording transaction-manager wrapper only to inspect propagation and timeout while delegating every transaction operation to the real manager;
- create/drop a per-test MySQL trigger that raises `SIGNAL SQLSTATE '45000'` only for the sentinel row in the second batch;
- use `CountDownLatch`, bounded `Future.get`, and a fixed executor for the outer-transaction concurrency test; close executors in `finally` and never use an unbounded sleep;
- reflect the private `DatasetLockManager.locks` map only to prove empty-batch and post-completion cleanup; do not add a production inspection hook.

- [ ] **Step 3: Run the focused test and verify a pure compile RED**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=PersistenceServiceIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-zero only in `tensor-core:testCompile`, with unresolved symbols for `GenericUpsertRepository` and `PersistenceService`. There must be no test syntax, dependency, Docker, MySQL, or pre-existing failure.

### Task 2: Implement transaction-bound metadata-sized Upsert

**Files:**
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`

**Interfaces:**
- Consumes: the exact existing M06-T01/T02/T03 collaborators and `DatasetCatalog`/`AdaptedBatch` contracts listed in Task 1.
- Produces: `GenericUpsertRepository(JdbcTemplate)`, `void upsert(DatasetDefinition,AdaptedBatch)`, `PersistenceService(DatasetCatalog,DatasetLockManager,ExistingKeyRepository,GenericUpsertRepository,PlatformTransactionManager)`, and `WriteCounts persist(AdaptedBatch)`.

- [ ] **Step 1: Implement `GenericUpsertRepository` with one shared batch validator**

Use this exact shape, keeping helpers package-private or private:

```java
public final class GenericUpsertRepository {
    private static final String FINGERPRINT_COLUMN = "business_key";
    private final JdbcTemplate jdbcTemplate;

    public GenericUpsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public void upsert(DatasetDefinition definition, AdaptedBatch batch) {
        validateBatch(definition, batch);
        if (batch.rows().isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Upsert requires an active transaction");
        }
        String sql = new UpsertSqlFactory().create(definition);
        JdbcValueBinder binder = new JdbcValueBinder();
        jdbcTemplate.batchUpdate(sql, batch.rows(), definition.batchSize(),
                (statement, row) -> bindRow(statement, definition, batch, row, binder));
    }

    static void validateBatch(DatasetDefinition definition, AdaptedBatch batch) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(batch, "batch");
        List<String> expected = new ArrayList<>(definition.columns().stream()
                .map(ColumnDefinition::name).toList());
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            expected.add(FINGERPRINT_COLUMN);
        }
        if (!batch.datasetKey().equals(definition.datasetKey())
                || !batch.tableName().equals(definition.tableName())
                || !batch.businessKeyDefinition().equals(definition.businessKey())
                || !batch.columns().equals(expected)) {
            throw new IllegalArgumentException("Adapted batch does not match dataset");
        }
    }
}
```

`bindRow` starts at parameter 1, binds every `definition.columns()` value with this total switch, appends FINGERPRINT `business_key` as `Types.CHAR`, then `source_plugin`/`source_api` as `Types.VARCHAR`, and finally the one `batch.ingestedAt()` as `Types.TIMESTAMP`:

```java
private static int jdbcType(LogicalType type) {
    return switch (type) {
        case STRING -> Types.VARCHAR;
        case TEXT -> Types.LONGVARCHAR;
        case DATE -> Types.DATE;
        case MONTH, ENUM -> Types.CHAR;
        case LONG -> Types.BIGINT;
        case DECIMAL -> Types.DECIMAL;
    };
}
```

Ignore the returned `int[][]`; do not read affected-row values.

- [ ] **Step 2: Implement `PersistenceService` with synchronization-owned unlock**

Configure one `TransactionTemplate` in the constructor:

```java
this.transactionTemplate = new TransactionTemplate(
        Objects.requireNonNull(transactionManager, "transactionManager"));
this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
this.transactionTemplate.setTimeout(60);
```

Implement `persist` in this exact sequence:

```java
public WriteCounts persist(AdaptedBatch batch) {
    Objects.requireNonNull(batch, "batch");
    DatasetDefinition definition = datasetCatalog.find(batch.datasetKey())
            .orElseThrow(() -> new IllegalArgumentException("Dataset is not available"));
    GenericUpsertRepository.validateBatch(definition, batch);
    if (batch.rows().isEmpty()) {
        return new WriteCounts(0, 0);
    }
    BusinessKeyExtractor extractor = new BusinessKeyExtractor();
    List<BusinessKey> keys = batch.rows().stream()
            .map(row -> extractor.extract(definition, row))
            .toList();
    Lock lock = datasetLockManager.acquire(batch.datasetKey());
    AtomicBoolean transferred = new AtomicBoolean();
    try {
        return transactionTemplate.execute(status -> {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("Transaction synchronization is not active");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int completionStatus) {
                    lock.unlock();
                }
            });
            transferred.set(true);
            Set<BusinessKey> existing = existingKeyRepository.findExisting(definition, keys);
            WriteCounts counts = WriteCounts.from(keys, existing);
            genericUpsertRepository.upsert(definition, batch);
            return counts;
        });
    } finally {
        if (!transferred.get()) {
            lock.unlock();
        }
    }
}
```

Store only the five approved collaborators and the configured template. Reject every constructor dependency with its exact parameter name. Do not catch or wrap Spring JDBC/transaction runtime exceptions. If static review shows `TransactionTemplate.execute` nullability needs an explicit guard, use `Objects.requireNonNull(result, "transaction result")` without changing the public error contracts.

- [ ] **Step 3: Run the permanent MySQL GREEN gate**

Run on the current Colima workstation:

```bash
env \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=PersistenceServiceIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: exactly 8/8 passing. Assert actual table contents and counts, recorded batch sizes `2,1`, `PROPAGATION_REQUIRED`, timeout 60, full rollback after the trigger failure, outer-transaction blocking until commit, one fingerprint row after two calls, and one batch-wide `ingested_at` instant.

- [ ] **Step 4: Run standard regressions**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am verify
```

Expected: both commands report plugin-api 79/79 and core 67/67, total 146/146 with zero failures, errors, or skips; three Enforcer layers pass. `PersistenceServiceIT` remains excluded from these non-directed counts.

### Task 3: Prove mechanisms, enforce scope, and commit

**Files:**
- Verify: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java`
- Verify: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java`
- Verify: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`

**Interfaces:**
- Consumes: Task 2's final implementation and test suite.
- Produces: one reviewed implementation commit containing exactly the three approved Java files.

- [ ] **Step 1: Run the two controlled mutations one at a time**

Mutation A: temporarily replace transaction-synchronization ownership with unlock when `persist` returns. Run only `retainsDatasetLockUntilJoinedOuterTransactionCompletes`; expected failure is the second writer completing before the outer transaction exits. Restore the implementation and rerun the test to PASS.

Mutation B: temporarily replace `definition.batchSize()` in the Spring batch call with `batch.rows().size()`. Run only `insertsCompositeRowsInMetadataSizedJdbcBatches`; expected failure reports one recorded batch of size 3 instead of `2,1`. Restore the implementation and rerun the full directed suite to 8/8 PASS.

- [ ] **Step 2: Run static, scope, formatting, and cleanup gates**

Run the two design scans, expecting the forbidden scan to have no output/exit 1 and the required scan to show every approved mechanism:

```bash
rg -n '@Transactional|PROPAGATION_REQUIRES_NEW|setObject|createStatement|SELECT \*|String\.format|formatted\(|(?i:token|credential)|tushare|RestClient|ServiceLoader' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java
rg -n 'TransactionTemplate|PROPAGATION_REQUIRED|setTimeout\(60\)|isActualTransactionActive|registerSynchronization|afterCompletion|batchSize\(\)|UpsertSqlFactory|JdbcValueBinder' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java
```

Run the exact protected-path diff and scoped status commands from `docs/task-designs/M06-T04-design.md`, then `git diff --check`. Expected: protected paths have no diff; status lists only the three new Java files; formatting passes.

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am clean
```

Expected: exit 0 and no `target` path in scoped status.

- [ ] **Step 3: Commit exactly the implementation files**

```bash
git add \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/GenericUpsertRepository.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java
git commit -m "feat(core): persist adapted batches atomically"
```

Expected: `git show --stat --oneline HEAD` reports the fixed message and exactly three files. The worktree is clean before task-board completion evidence is recorded separately.

- [ ] **Step 4: Perform final review and fresh verification**

Review the final commit against every Acceptance bullet in `docs/task-designs/M06-T04-design.md`. Freshly rerun the directed MySQL 8/8 gate, standard reactor `test` and `verify` 146/146 gates, static scans, protected-path diff, `git diff --check`, and `clean`. Do not record `IN_PROGRESS -> COMPLETED` until all outcome-level acceptance evidence is present.
