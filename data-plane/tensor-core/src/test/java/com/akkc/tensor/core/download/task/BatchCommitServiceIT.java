package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.*;
import com.akkc.tensor.core.persistence.*;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BatchCommitServiceIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static final Instant NOW = Instant.parse("2026-09-11T02:03:04.123Z");
    static final DatasetKey KEY = DatasetKey.of(PluginId.of("commit_test"), ApiName.of("prices"));
    static final BusinessKeyDefinition BUSINESS_KEY = new BusinessKeyDefinition(
            BusinessKeyMode.COMPOSITE, List.of("symbol", "trade_date"));
    static final List<String> COLUMNS = List.of("symbol", "trade_date", "amount");
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    DownloadTaskRepository repository;
    DatasetLockManager locks;
    PersistenceService persistence;
    BatchCommitService service;
    final DownloadTaskJson json = new DownloadTaskJson();
    final MutableClock clock = new MutableClock();

    @BeforeAll
    static void schema() throws Exception {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    "../tensor-app/src/main/resources/db/migration/V8__create_download_task_tables.sql"));
        }
        jdbc.execute("""
                CREATE TABLE commit_test__prices (
                  symbol VARCHAR(32) NOT NULL, trade_date DATE NOT NULL, amount DECIMAL(18,2) NOT NULL,
                  source_plugin VARCHAR(64) NOT NULL, source_api VARCHAR(64) NOT NULL,
                  ingested_at TIMESTAMP(3) NOT NULL, PRIMARY KEY (symbol, trade_date)) ENGINE=InnoDB
                """);
    }

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_price");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_success");
        jdbc.update("DELETE FROM tensor_download_batch");
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM commit_test__prices");
        repository = new DownloadTaskRepository(jdbc, transactions, json);
        locks = new DatasetLockManager();
        persistence = new PersistenceService(catalog(), locks,
                new ExistingKeyRepository(jdbc), new GenericUpsertRepository(jdbc), transactions);
        service = new BatchCommitService(persistence, repository, clock);
    }

    @Test
    void commitsSecuritiesAndSuccessTogetherWithActualDistinctBusinessKeyCounts() {
        persistence.persist(batch(row("AA", "10")));
        var run = running();
        clock.now = NOW.plusSeconds(1);
        assertThat(service.commit(run.permit(), run.batchId(),
                batch(row("AA", "20"), row("BB", "30"), row("BB", "31")), 3))
                .isEqualTo(new WriteCounts(1, 1));
        assertThat(prices()).containsExactly(new BigDecimal("20.00"), new BigDecimal("31.00"));
        var result = storedBatch(run);
        assertThat(result.status()).isEqualTo(DownloadBatch.Status.SUCCEEDED);
        assertThat(result.sourceRows()).isEqualTo(3);
        assertThat(result.insertedRows()).isEqualTo(1);
        assertThat(result.updatedRows()).isEqualTo(1);
        assertThat(result.finishedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(result.updatedAt()).isEqualTo(result.finishedAt());
        assertThat(result.error()).isNull();
        assertThat(repository.findTask(run.permit().taskId()).orElseThrow().status())
                .isEqualTo(DownloadTask.Status.RUNNING);
    }

    @Test
    void emptyBatchStillCommitsSuccessAndZeroCounts() {
        var run = running();
        assertThat(service.commit(run.permit(), run.batchId(), batch(), 0)).isEqualTo(new WriteCounts(0, 0));
        assertThat(prices()).isEmpty();
        var result = storedBatch(run);
        assertThat(result.status()).isEqualTo(DownloadBatch.Status.SUCCEEDED);
        assertThat(result.sourceRows()).isZero();
        assertThat(result.insertedRows()).isZero();
        assertThat(result.updatedRows()).isZero();
        assertThat(result.finishedAt()).isEqualTo(NOW);
    }

    @Test
    void secondJdbcBatchFailureRollsBackFirstRowAndCallerRecordsFailureSeparately() {
        var run = running();
        jdbc.execute("""
                CREATE TRIGGER reject_price BEFORE INSERT ON commit_test__prices FOR EACH ROW
                BEGIN IF NEW.symbol='BB' THEN SIGNAL SQLSTATE '45000'
                  SET MESSAGE_TEXT='secret-db-detail'; END IF; END
                """);
        code(ErrorCode.PERSISTENCE_FAILED,
                () -> service.commit(run.permit(), run.batchId(), batch(row("AA", "10"), row("BB", "20")), 2));
        assertThat(prices()).isEmpty();
        assertRunningWithZeroCounts(run);
        jdbc.execute("DROP TRIGGER reject_price");
        recordFailure(run);
    }

    @Test
    void successUpdateFailureRollsBackBothNewAndUpdatedSecurities() {
        persistence.persist(batch(row("AA", "10")));
        var run = running();
        jdbc.execute("""
                CREATE TRIGGER reject_success BEFORE UPDATE ON tensor_download_batch FOR EACH ROW
                BEGIN IF NEW.status='SUCCEEDED' THEN SIGNAL SQLSTATE '45000'
                  SET MESSAGE_TEXT='secret-state-detail'; END IF; END
                """);
        code(ErrorCode.PERSISTENCE_FAILED,
                () -> service.commit(run.permit(), run.batchId(), batch(row("AA", "20"), row("BB", "30")), 2));
        assertThat(prices()).containsExactly(new BigDecimal("10.00"));
        assertRunningWithZeroCounts(run);
        jdbc.execute("DROP TRIGGER reject_success");
        recordFailure(run);
        assertThat(prices()).containsExactly(new BigDecimal("10.00"));
    }

    @Test
    void accumulatedCountOverflowRollsBackCurrentSecuritiesAndKeepsSuccessfulSibling() {
        var descriptor = new BatchDownloadDescriptor(List.of(endpoint("from", "to"), endpoint("to", "from")),
                "from", "to", BatchDownloadDescriptor.DateAxis.TRADE_DATE, "Trade date",
                BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true,
                BatchDownloadDescriptor.Availability.AVAILABLE, null, "v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.CONFIRMED_ROW_LIMIT, 100L, "Test rule"));
        var task = repository.insert(new NewTask(UUID.randomUUID(), UUID.randomUUID(), KEY, DownloadMode.RANGE,
                Map.of("from", "20260910", "to", "20260911"), "a".repeat(64),
                json.policySnapshot(DownloadMode.RANGE, descriptor), UUID.randomUUID(), NOW));
        task = repository.claimTask(task.taskId(), task.activeRunId(), NOW, NOW.plusSeconds(60)).orElseThrow();
        var permit = new ExecutionPermit(task.taskId(), task.activeRunId(), task.runGeneration());
        var first = new NewBatch(UUID.randomUUID(), "000001",
                new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)), Map.of());
        var second = new NewBatch(UUID.randomUUID(), "000002",
                new DateRange(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11)), Map.of());
        repository.savePlan(permit, List.of(first, second), 2, NOW);
        repository.claimBatch(permit, first.batchId(), NOW).orElseThrow();
        service.commit(permit, first.batchId(), batch(row("AA", "10")), Long.MAX_VALUE - 1);
        var succeeded = storedBatch(new Run(permit, first.batchId()));
        repository.claimBatch(permit, second.batchId(), NOW).orElseThrow();
        var current = new Run(permit, second.batchId());
        code(ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> service.commit(permit, second.batchId(), batch(row("BB", "20")), 2));
        assertThat(prices()).containsExactly(new BigDecimal("10.00"));
        assertThat(storedBatch(new Run(permit, first.batchId()))).isEqualTo(succeeded);
        assertRunningWithZeroCounts(current);
        assertThat(repository.counts(permit.taskId()).sourceRows()).isEqualTo(Long.MAX_VALUE - 1);
    }

    @Test
    void rejectsOldRunGenerationMissingTaskAndWrongBatchWithoutChangingFacts() {
        var run = running();
        var other = running();
        var before = storedBatch(run);
        for (var permit : List.of(
                new ExecutionPermit(run.permit().taskId(), UUID.randomUUID(), 1),
                new ExecutionPermit(run.permit().taskId(), run.permit().activeRunId(), 2),
                new ExecutionPermit(UUID.randomUUID(), run.permit().activeRunId(), 1))) {
            code(ErrorCode.TASK_STATE_CONFLICT, () -> service.commit(permit, run.batchId(), batch(row("AA", "10")), 1));
        }
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> service.commit(run.permit(), other.batchId(), batch(row("AA", "10")), 1));
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> service.commit(run.permit(), UUID.randomUUID(), batch(row("AA", "10")), 1));
        assertThat(storedBatch(run)).isEqualTo(before);
        assertRunningWithZeroCounts(other);
        assertThat(prices()).isEmpty();
    }

    @Test
    void rejectsNonRunningTaskAndNonRunningOrWrongGenerationBatch() {
        var run = running();
        jdbc.update("UPDATE tensor_download_batch SET run_generation=2 WHERE batch_id=?", run.batchId().toString());
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> service.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1));
        jdbc.update("UPDATE tensor_download_batch SET run_generation=1 WHERE batch_id=?", run.batchId().toString());
        repository.failBatch(run.permit(), run.batchId(), ErrorCode.SOURCE_NETWORK_ERROR, NOW);
        var failed = storedBatch(run);
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> service.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1));
        repository.finishTask(run.permit(), DownloadTask.Status.FAILED, ErrorCode.SOURCE_NETWORK_ERROR, NOW);
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> service.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1));
        assertThat(storedBatch(run)).isEqualTo(failed);
        assertThat(prices()).isEmpty();
    }

    @Test
    void refusesToCommitSuccessfulBatchTwice() {
        var run = running();
        service.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1);
        var succeeded = storedBatch(run);
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> service.commit(run.permit(), run.batchId(), batch(row("AA", "20")), 1));
        assertThat(storedBatch(run)).isEqualTo(succeeded);
        assertThat(prices()).containsExactly(new BigDecimal("10.00"));
        assertThat(repository.counts(run.permit().taskId()).sourceRows()).isEqualTo(1);
    }

    @Test
    void rejectsDifferentDatasetAndInvalidRegisteredColumnOrderBeforeWriting() {
        var run = running();
        var otherKey = DatasetKey.of(PluginId.of("other_source"), ApiName.of("prices"));
        var other = new AdaptedBatch(otherKey, TableName.from(otherKey), COLUMNS,
                List.of(row("AA", "10")), BUSINESS_KEY, NOW);
        code(ErrorCode.DATASET_MISCONFIGURED,
                () -> service.commit(run.permit(), run.batchId(), other, 1));
        var wrongColumns = new AdaptedBatch(KEY, TableName.from(KEY), List.of("amount", "symbol", "trade_date"),
                List.of(row("AA", "10")), BUSINESS_KEY, NOW);
        code(ErrorCode.DATASET_MISCONFIGURED,
                () -> service.commit(run.permit(), run.batchId(), wrongColumns, 1));
        assertRunningWithZeroCounts(run);
        assertThat(prices()).isEmpty();
    }

    @Test
    void refusesMissingOrReachedDeadlineBeforeWriting() {
        var run = running();
        clock.now = NOW.plusSeconds(60);
        code(ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> service.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1));
        clock.now = NOW;
        jdbc.update("UPDATE tensor_download_task SET deadline_at=NULL WHERE task_id=?", run.permit().taskId().toString());
        code(ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> service.commit(run.permit(), run.batchId(), batch(), 0));
        assertRunningWithZeroCounts(run);
        assertThat(prices()).isEmpty();
    }

    @Test
    void refusesCommitWhenStopWasAlreadyRequested() {
        var run = running();

        code(ErrorCode.EXECUTION_INTERRUPTED,
                () -> service.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1, () -> true));

        assertThat(prices()).isEmpty();
        assertRunningWithZeroCounts(run);
    }

    @Test
    void locksCurrentTaskAfterStalePrecheckSnapshotAndCannotOverwriteNewWorker() throws Exception {
        var run = running();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var once = new AtomicBoolean();
        var gatedJdbc = new JdbcTemplate(dataSource) {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                var result = super.query(sql, mapper, args);
                if (sql.startsWith("SELECT * FROM tensor_download_task") && once.compareAndSet(false, true)) {
                    entered.countDown();
                    await(release);
                }
                return result;
            }
        };
        var gatedRepository = new DownloadTaskRepository(gatedJdbc, transactions, json);
        var gatedService = new BatchCommitService(persistence, gatedRepository, clock);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> failureCode(
                    () -> gatedService.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var interrupted = repository.recoverStoppedTask(run.permit().taskId(), run.permit().activeRunId(), 1, 2, NOW);
                var nextRun = UUID.randomUUID();
                repository.requeue(interrupted.taskId(), interrupted.version(), nextRun, RequeueMode.RESUME, NOW);
                var next = repository.claimTask(interrupted.taskId(), nextRun, NOW, NOW.plusSeconds(60)).orElseThrow();
                var nextPermit = new ExecutionPermit(next.taskId(), nextRun, next.runGeneration());
                repository.claimBatch(nextPermit, run.batchId(), NOW).orElseThrow();
                var current = storedBatch(run);
                release.countDown();
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(ErrorCode.TASK_STATE_CONFLICT);
                code(ErrorCode.TASK_STATE_CONFLICT,
                        () -> repository.failBatch(run.permit(), run.batchId(), ErrorCode.PERSISTENCE_FAILED, NOW));
                assertThat(storedBatch(run)).isEqualTo(current);
                assertThat(repository.findTask(next.taskId())).contains(next);
                assertThat(prices()).isEmpty();
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void rechecksDeadlineAfterWaitingForDatasetLock() throws Exception {
        var run = running();
        var checked = new CountDownLatch(1);
        var observedClock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() {
                Instant instant = clock.instant();
                checked.countDown();
                return instant;
            }
        };
        var waiting = new BatchCommitService(persistence, repository, observedClock);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var lock = locks.acquire(KEY);
            Future<ErrorCode> result;
            try {
                result = executor.submit(() -> failureCode(
                        () -> waiting.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1)));
                assertThat(checked.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
                clock.now = NOW.plusSeconds(60);
            } finally {
                lock.unlock();
            }
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED);
        }
        assertThat(prices()).isEmpty();
        assertRunningWithZeroCounts(run);
    }

    @Test
    void rechecksStopAfterWaitingForDatasetLock() throws Exception {
        var run = running();
        var checked = new CountDownLatch(1);
        var stopped = new AtomicBoolean();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var lock = locks.acquire(KEY);
            Future<ErrorCode> result;
            try {
                result = executor.submit(() -> failureCode(() -> service.commit(
                        run.permit(), run.batchId(), batch(row("AA", "10")), 1,
                        () -> {
                            checked.countDown();
                            return stopped.get();
                        })));
                assertThat(checked.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
                stopped.set(true);
            } finally {
                lock.unlock();
            }
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
        }
        assertThat(prices()).isEmpty();
        assertRunningWithZeroCounts(run);
    }

    @Test
    void permittedTransactionCanCommitAfterTaskDeadline() throws Exception {
        var run = running();
        var advancingJdbc = new JdbcTemplate(dataSource) {
            @Override
            public <T> int[][] batchUpdate(String sql, Collection<T> args, int size,
                    ParameterizedPreparedStatementSetter<T> setter) {
                clock.now = NOW.plusSeconds(61);
                return super.batchUpdate(sql, args, size, setter);
            }
        };
        var advancing = new PersistenceService(catalog(), locks, new ExistingKeyRepository(jdbc),
                new GenericUpsertRepository(advancingJdbc), transactions);
        var permitted = new BatchCommitService(advancing, repository, clock);
        assertThat(permitted.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1))
                .isEqualTo(new WriteCounts(1, 0));
        assertThat(prices()).containsExactly(new BigDecimal("10.00"));
        assertThat(storedBatch(run).status()).isEqualTo(DownloadBatch.Status.SUCCEEDED);
        assertThat(storedBatch(run).finishedAt()).isEqualTo(NOW.plusSeconds(61));
    }

    @Test
    void permittedTransactionCanCommitAfterStopIsRequested() throws Exception {
        var run = running();
        var stopped = new AtomicBoolean();
        var stoppingJdbc = new JdbcTemplate(dataSource) {
            @Override
            public <T> int[][] batchUpdate(String sql, Collection<T> args, int size,
                    ParameterizedPreparedStatementSetter<T> setter) {
                stopped.set(true);
                return super.batchUpdate(sql, args, size, setter);
            }
        };
        var stoppingPersistence = new PersistenceService(catalog(), locks, new ExistingKeyRepository(jdbc),
                new GenericUpsertRepository(stoppingJdbc), transactions);
        var permitted = new BatchCommitService(stoppingPersistence, repository, clock);

        assertThat(permitted.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1, stopped::get))
                .isEqualTo(new WriteCounts(1, 0));

        assertThat(stopped).isTrue();
        assertThat(prices()).containsExactly(new BigDecimal("10.00"));
        assertThat(storedBatch(run).status()).isEqualTo(DownloadBatch.Status.SUCCEEDED);
    }

    @Test
    void rejectsCallerTransactionAndInvalidCallArgumentsWithoutChangingState() {
        var run = running();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThatThrownBy(() -> service.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.commit(run.permit(), run.batchId(), batch(), 0))
                    .isInstanceOf(IllegalStateException.class);
            status.setRollbackOnly();
        });
        assertThatThrownBy(() -> service.commit(null, run.batchId(), batch(), 0))
                .isInstanceOf(NullPointerException.class).hasMessage("permit");
        assertThatThrownBy(() -> service.commit(run.permit(), null, batch(), 0))
                .isInstanceOf(NullPointerException.class).hasMessage("batchId");
        assertThatThrownBy(() -> service.commit(run.permit(), run.batchId(), null, 0))
                .isInstanceOf(NullPointerException.class).hasMessage("batch");
        assertThatThrownBy(() -> service.commit(run.permit(), run.batchId(), batch(), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCommitService(null, repository, clock)).hasMessage("persistence");
        assertThatThrownBy(() -> new BatchCommitService(persistence, null, clock)).hasMessage("repository");
        assertThatThrownBy(() -> new BatchCommitService(persistence, repository, null)).hasMessage("clock");
        assertRunningWithZeroCounts(run);
        assertThat(prices()).isEmpty();
    }

    @Test
    void transactionStartFailureHasFixedPersistenceErrorAndNoCause() throws Exception {
        var run = running();
        var rejecting = new DataSourceTransactionManager(dataSource) {
            @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
                throw new org.springframework.transaction.CannotCreateTransactionException("secret-connection-detail");
            }
        };
        var failedPersistence = new PersistenceService(catalog(), locks,
                new ExistingKeyRepository(jdbc), new GenericUpsertRepository(jdbc), rejecting);
        var failedService = new BatchCommitService(failedPersistence, repository, clock);
        code(ErrorCode.PERSISTENCE_FAILED,
                () -> failedService.commit(run.permit(), run.batchId(), batch(row("AA", "10")), 1));
        assertThat(prices()).isEmpty();
        assertRunningWithZeroCounts(run);
        // Another thread must acquire the lock; same-thread reentry could hide a leak.
        var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().factory());
        try {
            assertThat(executor.submit(() -> service.commit(run.permit(), run.batchId(), batch(), 0))
                    .get(5, TimeUnit.SECONDS)).isEqualTo(new WriteCounts(0, 0));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    static com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor endpoint(String name, String related) {
        return new com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor(name, name, null,
                com.akkc.tensor.plugin.api.descriptor.ParameterType.DATE_RANGE_MEMBER,
                true, null, List.of(), null, related);
    }

    void assertRunningWithZeroCounts(Run run) {
        var result = storedBatch(run);
        assertThat(result.status()).isEqualTo(DownloadBatch.Status.RUNNING);
        assertThat(result.sourceRows()).isZero();
        assertThat(result.insertedRows()).isZero();
        assertThat(result.updatedRows()).isZero();
        assertThat(result.finishedAt()).isNull();
    }

    void recordFailure(Run run) {
        repository.failBatch(run.permit(), run.batchId(), ErrorCode.PERSISTENCE_FAILED, clock.instant());
        var failed = storedBatch(run);
        assertThat(failed.status()).isEqualTo(DownloadBatch.Status.FAILED);
        assertThat(failed.error()).isEqualTo(new StoredError(ErrorCode.PERSISTENCE_FAILED));
        assertThat(failed.sourceRows()).isZero();
        assertThat(failed.insertedRows()).isZero();
        assertThat(failed.updatedRows()).isZero();
        assertThat(failed.finishedAt()).isEqualTo(clock.instant());
    }

    static void code(ErrorCode expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThat(failureCode(call)).isEqualTo(expected);
    }

    static ErrorCode failureCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        try {
            call.call();
        } catch (TensorException failure) {
            assertThat(failure.getCause()).isNull();
            assertThat(failure.getMessage()).isEqualTo(new StoredError(failure.code()).message());
            return failure.code();
        } catch (Throwable failure) {
            throw new AssertionError("Expected a classified failure", failure);
        }
        throw new AssertionError("Expected commit to fail");
    }

    static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    Run running() {
        var task = repository.insert(new NewTask(UUID.randomUUID(), UUID.randomUUID(), KEY,
                DownloadMode.SINGLE, Map.of("symbol", "AA"), "a".repeat(64),
                json.policySnapshot(DownloadMode.SINGLE, null), UUID.randomUUID(), NOW));
        task = repository.claimTask(task.taskId(), task.activeRunId(), NOW, NOW.plusSeconds(60)).orElseThrow();
        var permit = new ExecutionPermit(task.taskId(), task.activeRunId(), task.runGeneration());
        var batchId = UUID.randomUUID();
        repository.savePlan(permit, List.of(new NewBatch(batchId, "000001", null, task.params())), 1, NOW);
        repository.claimBatch(permit, batchId, NOW).orElseThrow();
        return new Run(permit, batchId);
    }

    DownloadBatch storedBatch(Run run) {
        return repository.batches(run.permit().taskId(), null, 1, 20).items().stream()
                .filter(b -> b.batchId().equals(run.batchId())).findFirst().orElseThrow();
    }

    List<BigDecimal> prices() {
        // No caller transaction: these reads use independent connections after commit/rollback returns.
        return jdbc.queryForList("SELECT amount FROM commit_test__prices ORDER BY symbol", BigDecimal.class);
    }

    static Map<String, Object> row(String symbol, String amount) {
        return Map.of("symbol", symbol, "trade_date", LocalDate.of(2026, 9, 11), "amount", new BigDecimal(amount));
    }

    @SafeVarargs
    static AdaptedBatch batch(Map<String, Object>... rows) {
        return new AdaptedBatch(KEY, TableName.from(KEY), COLUMNS, List.of(rows), BUSINESS_KEY, NOW);
    }

    static DatasetCatalog catalog() throws Exception {
        var definition = new DatasetDefinition(KEY, "Prices", "test", QueryMode.snapshot, List.of(),
                TableName.from(KEY), List.of(
                        new ColumnDefinition("symbol", "Symbol", LogicalType.STRING, false, 0, 32, null, null, List.of(), false),
                        new ColumnDefinition("trade_date", "Date", LogicalType.DATE, false, 1, null, null, null, List.of(), false),
                        new ColumnDefinition("amount", "Amount", LogicalType.DECIMAL, false, 2, null, 18, 2, List.of(), false)),
                BUSINESS_KEY, List.of(), "symbol", 1);
        var constructor = DatasetCatalog.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(List.of(definition));
    }

    record Run(ExecutionPermit permit, UUID batchId) {}

    static final class MutableClock extends Clock {
        volatile Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
