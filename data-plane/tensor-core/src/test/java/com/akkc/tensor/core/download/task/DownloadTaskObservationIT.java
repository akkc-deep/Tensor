package com.akkc.tensor.core.download.task;

import static com.akkc.tensor.core.download.task.BatchCommitServiceIT.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.download.task.DownloadTaskObserver.*;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.*;
import com.akkc.tensor.core.persistence.*;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DownloadTaskObservationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    final DownloadTaskJson json = new DownloadTaskJson();
    final RecordingObserver observer = new RecordingObserver();
    DownloadTaskRepository repository;

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
    void reset() {
        for (String trigger : List.of("reject_price", "reject_success", "reject_failed", "reject_child"))
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger);
        for (String id : jdbc.queryForList(
                "SELECT batch_id FROM tensor_download_batch ORDER BY LENGTH(batch_key) DESC", String.class))
            jdbc.update("DELETE FROM tensor_download_batch WHERE batch_id=?", id);
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM commit_test__prices");
        repository = new DownloadTaskRepository(jdbc, transactions, json, observer);
    }

    @Test
    void startsOnlyAfterCommitAndNoopClaimsDoNotProduceEvents() {
        var task = queued(false);
        assertThat(observer.events).isEmpty();
        assertThat(repository.claimTask(task.taskId(), UUID.randomUUID(), NOW, NOW.plusSeconds(60))).isEmpty();
        observer.check = event -> {
            assertThat(event).isEqualTo(new TaskStarted(task.taskId(), KEY, 1));
            assertThat(independentStatus("tensor_download_task", "task_id", task.taskId())).isEqualTo("RUNNING");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        };
        repository.claimTask(task.taskId(), task.activeRunId(), NOW, NOW.plusSeconds(60)).orElseThrow();
        assertThat(observer.events).hasSize(1);
        assertThat(repository.claimTask(task.taskId(), task.activeRunId(), NOW, NOW.plusSeconds(60))).isEmpty();
        assertThat(repository.claimTask(UUID.randomUUID(), task.activeRunId(), NOW, NOW.plusSeconds(60))).isEmpty();
        assertThat(observer.events).hasSize(1);
    }

    @Test
    void securitiesSuccessAndEmptyBatchAreObservedAsCommittedFacts() throws Exception {
        var run = running(false, 1);
        var id = run.batches().getFirst().batchId();
        repository.reserveRequest(run.permit(), 20, NOW);
        observer.check = event -> {
            assertThat(independentStatus("tensor_download_batch", "batch_id", id)).isEqualTo("SUCCEEDED");
            assertThat(independentNumber("SELECT COUNT(*) FROM commit_test__prices")).isEqualTo(2);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        };
        commits(repository, transactions, NOW.plusMillis(1750)).commit(run.permit(), id,
                batch(row("AA", "10"), row("BB", "20")), 3);
        observer.check = event -> {};
        repository.finishTask(run.permit(), DownloadTask.Status.SUCCEEDED, null, NOW.plusSeconds(2));
        assertThat(observer.events).containsExactly(
                new BatchFinished(run.permit().taskId(), KEY, 1, id, DownloadBatch.Status.SUCCEEDED,
                        1, 1750, 3, 2, 0, null),
                new TaskFinished(run.permit().taskId(), KEY, 1, DownloadTask.Status.SUCCEEDED,
                        false, 2000, 1, 1, new Counts(1, 0, 0, 1, 0, 0, 3, 2, 0), null));

        var empty = running(false, 1);
        var emptyId = empty.batches().getFirst().batchId();
        commits(repository, transactions, NOW.minusMillis(100)).commit(empty.permit(), emptyId, batch(), 0);
        assertThat(observer.events).containsExactly(new BatchFinished(empty.permit().taskId(), KEY, 1,
                emptyId, DownloadBatch.Status.SUCCEEDED, 1, 0, 0, 0, 0, null));
    }

    @Test
    void successInParticipatingTransactionIsSilentUntilCommitAndSilentOnRollback() {
        var run = running(false, 1);
        var id = run.batches().getFirst().batchId();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            repository.succeedBatch(run.permit(), id, 5, 2, 3, NOW.plusSeconds(1));
            assertThat(observer.events).isEmpty();
            assertThat(independentStatus("tensor_download_batch", "batch_id", id)).isEqualTo("RUNNING");
            status.setRollbackOnly();
        });
        assertThat(observer.events).isEmpty();
        assertThat(independentStatus("tensor_download_batch", "batch_id", id)).isEqualTo("RUNNING");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            repository.succeedBatch(run.permit(), id, 5, 2, 3, NOW.plusSeconds(1));
            assertThat(observer.events).isEmpty();
        });
        assertThat(observer.events).containsExactly(new BatchFinished(run.permit().taskId(), KEY, 1,
                id, DownloadBatch.Status.SUCCEEDED, 1, 1000, 5, 2, 3, null));
    }

    @Test
    void securitiesOrSuccessMarkerRollbackCannotProduceSuccessfulBatchEvents() throws Exception {
        var run = running(false, 1);
        var id = run.batches().getFirst().batchId();
        jdbc.execute("""
                CREATE TRIGGER reject_price BEFORE INSERT ON commit_test__prices FOR EACH ROW
                BEGIN IF NEW.symbol='BB' THEN SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT='private-price'; END IF; END
                """);
        var commit = commits(repository, transactions, NOW);
        code(ErrorCode.PERSISTENCE_FAILED,
                () -> commit.commit(run.permit(), id, batch(row("AA", "10"), row("BB", "20")), 2));
        assertThat(independentNumber("SELECT COUNT(*) FROM commit_test__prices")).isZero();
        assertThat(observer.events).isEmpty();
        jdbc.execute("DROP TRIGGER reject_price");
        jdbc.execute("""
                CREATE TRIGGER reject_success BEFORE UPDATE ON tensor_download_batch FOR EACH ROW
                BEGIN IF NEW.status='SUCCEEDED' THEN SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT='private-success'; END IF; END
                """);
        code(ErrorCode.PERSISTENCE_FAILED, () -> commit.commit(run.permit(), id, batch(row("AA", "10")), 1));
        assertThat(independentNumber("SELECT COUNT(*) FROM commit_test__prices")).isZero();
        assertThat(independentStatus("tensor_download_batch", "batch_id", id)).isEqualTo("RUNNING");
        assertThat(observer.events).isEmpty();
    }

    @Test
    void failedWriteAndStalePermitsProduceNoEventsButCommittedFailureDoes() {
        var run = running(false, 1);
        var id = run.batches().getFirst().batchId();
        jdbc.execute("""
                CREATE TRIGGER reject_failed BEFORE UPDATE ON tensor_download_batch FOR EACH ROW
                BEGIN IF NEW.status='FAILED' THEN SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT='private-failure'; END IF; END
                """);
        code(ErrorCode.PERSISTENCE_FAILED,
                () -> repository.failBatch(run.permit(), id, ErrorCode.SOURCE_TIMEOUT, NOW));
        assertThat(observer.events).isEmpty();
        jdbc.execute("DROP TRIGGER reject_failed");
        var stale = new ExecutionPermit(run.permit().taskId(), run.permit().activeRunId(), 2);
        code(ErrorCode.TASK_STATE_CONFLICT, () -> repository.failBatch(stale, id, ErrorCode.SOURCE_TIMEOUT, NOW));
        code(ErrorCode.TASK_STATE_CONFLICT, () -> new TransactionTemplate(transactions).executeWithoutResult(
                status -> repository.succeedBatch(stale, id, 0, 0, 0, NOW)));
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.finishTask(stale, DownloadTask.Status.FAILED, ErrorCode.SOURCE_TIMEOUT, NOW));
        assertThat(observer.events).isEmpty();
        observer.check = event -> assertThat(independentStatus("tensor_download_batch", "batch_id", id))
                .isEqualTo("FAILED");
        repository.failBatch(run.permit(), id, ErrorCode.SOURCE_TIMEOUT, NOW.plusMillis(700));
        repository.finishTask(run.permit(), DownloadTask.Status.FAILED, ErrorCode.SOURCE_TIMEOUT, NOW.plusSeconds(1));
        assertThat(observer.events).containsExactly(
                new BatchFinished(run.permit().taskId(), KEY, 1, id, DownloadBatch.Status.FAILED,
                        1, 700, 0, 0, 0, ErrorCode.SOURCE_TIMEOUT),
                new TaskFinished(run.permit().taskId(), KEY, 1, DownloadTask.Status.FAILED, false,
                        1000, 0, 0, new Counts(1, 0, 0, 0, 1, 0, 0, 0, 0), ErrorCode.SOURCE_TIMEOUT));
    }

    @Test
    void splitPublishesOnlyWhenBothChildrenCommit() {
        var run = running(true, 1);
        var parent = run.batches().getFirst();
        var left = new NewBatch(UUID.randomUUID(), "000001/0", day(10), Map.of());
        var right = new NewBatch(UUID.randomUUID(), "000001/1", day(11), Map.of());
        jdbc.execute("""
                CREATE TRIGGER reject_child BEFORE INSERT ON tensor_download_batch FOR EACH ROW
                BEGIN IF NEW.batch_key='000001/1' THEN SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT='private-child'; END IF; END
                """);
        code(ErrorCode.PERSISTENCE_FAILED,
                () -> repository.split(run.permit(), parent.batchId(), left, right, 3, NOW));
        assertThat(observer.events).isEmpty();
        assertThat(independentNumber("SELECT COUNT(*) FROM tensor_download_batch")).isEqualTo(1);
        assertThat(independentStatus("tensor_download_batch", "batch_id", parent.batchId())).isEqualTo("RUNNING");
        jdbc.execute("DROP TRIGGER reject_child");
        observer.check = event -> {
            assertThat(independentStatus("tensor_download_batch", "batch_id", parent.batchId())).isEqualTo("SPLIT");
            assertThat(independentNumber("SELECT COUNT(*) FROM tensor_download_batch")).isEqualTo(3);
        };
        repository.split(run.permit(), parent.batchId(), left, right, 3, NOW.plusSeconds(3));
        assertThat(observer.events).containsExactly(new BatchFinished(run.permit().taskId(), KEY, 1,
                parent.batchId(), DownloadBatch.Status.SPLIT, 1, 3000, 0, 0, 0, null));
    }

    @Test
    void recoveryPublishesOnlyChangedRunningBatchesAndAccurateFinalCounts() {
        var run = running(true, 4);
        var ids = run.batches().stream().map(NewBatch::batchId).toList();
        new TransactionTemplate(transactions).executeWithoutResult(
                status -> repository.succeedBatch(run.permit(), ids.get(0), 4, 3, 1, NOW));
        repository.claimBatch(run.permit(), ids.get(1), NOW).orElseThrow();
        repository.failBatch(run.permit(), ids.get(1), ErrorCode.SOURCE_AUTH_FAILED, NOW);
        repository.claimBatch(run.permit(), ids.get(2), NOW).orElseThrow();
        repository.reserveRequest(run.permit(), 20, NOW);
        repository.reserveRequest(run.permit(), 20, NOW);
        repository.reserveRequest(run.permit(), 20, NOW);
        observer.events.clear();
        var before = repository.findTask(run.permit().taskId()).orElseThrow();
        observer.check = event -> assertThat(independentStatus("tensor_download_task", "task_id", before.taskId()))
                .isEqualTo("INTERRUPTED");
        var recovered = repository.recoverStoppedTask(before.taskId(), before.activeRunId(),
                before.runGeneration(), before.version(), NOW.plusSeconds(4));
        assertThat(observer.events).containsExactly(
                new BatchFinished(before.taskId(), KEY, 1, ids.get(2), DownloadBatch.Status.FAILED,
                        1, 4000, 0, 0, 0, ErrorCode.EXECUTION_INTERRUPTED),
                new TaskFinished(before.taskId(), KEY, 1, DownloadTask.Status.INTERRUPTED, true,
                        4000, 3, 3, new Counts(4, 1, 0, 1, 2, 0, 4, 3, 1), ErrorCode.EXECUTION_INTERRUPTED));
        code(ErrorCode.TASK_STATE_CONFLICT, () -> repository.recoverStoppedTask(recovered.taskId(),
                recovered.activeRunId(), recovered.runGeneration(), recovered.version(), NOW.plusSeconds(5)));
        assertThat(observer.events).hasSize(2);
        observer.check = event -> {};
        repository.requeue(recovered.taskId(), recovered.version(), recovered.activeRunId(), RequeueMode.RESUME, NOW);
        assertThat(observer.events).hasSize(2);
    }

    @Test
    void retryEventsPreserveCumulativeRequestsAndUseNewGenerationAndAttempt() throws Exception {
        var run = running(false, 1);
        var id = run.batches().getFirst().batchId();
        repository.reserveRequest(run.permit(), 20, NOW);
        repository.failBatch(run.permit(), id, ErrorCode.SOURCE_TIMEOUT, NOW.plusSeconds(1));
        repository.finishTask(run.permit(), DownloadTask.Status.FAILED, ErrorCode.SOURCE_TIMEOUT, NOW.plusSeconds(2));
        var failed = repository.findTask(run.permit().taskId()).orElseThrow();
        observer.events.clear();
        repository.requeue(failed.taskId(), failed.version(), failed.activeRunId(), RequeueMode.RETRY, NOW.plusSeconds(3));
        assertThat(observer.events).isEmpty();
        var retry = repository.claimTask(failed.taskId(), failed.activeRunId(), NOW.plusSeconds(4), NOW.plusSeconds(60))
                .orElseThrow();
        var permit = new ExecutionPermit(retry.taskId(), retry.activeRunId(), retry.runGeneration());
        repository.claimBatch(permit, id, NOW.plusSeconds(5)).orElseThrow();
        repository.reserveRequest(permit, 20, NOW.plusSeconds(5));
        commits(repository, transactions, NOW.plusSeconds(6)).commit(permit, id, batch(), 0);
        repository.finishTask(permit, DownloadTask.Status.SUCCEEDED, null, NOW.plusSeconds(7));
        assertThat(observer.events).containsExactly(
                new TaskStarted(retry.taskId(), KEY, 2),
                new BatchFinished(retry.taskId(), KEY, 2, id, DownloadBatch.Status.SUCCEEDED, 2, 1000, 0, 0, 0, null),
                new TaskFinished(retry.taskId(), KEY, 2, DownloadTask.Status.SUCCEEDED, false,
                        3000, 2, 1, new Counts(1, 0, 0, 1, 0, 0, 0, 0, 0), null));
    }

    @Test
    void recoveringUnstartedTaskHasZeroDurationAndNoInventedBatchEvents() {
        var task = queued(false);
        repository.recoverStoppedTask(task.taskId(), task.activeRunId(), 0, 1, NOW.plusSeconds(20));
        assertThat(observer.events).containsExactly(new TaskFinished(task.taskId(), KEY, 0,
                DownloadTask.Status.INTERRUPTED, true, 0, 0, 0,
                new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0), ErrorCode.EXECUTION_INTERRUPTED));
    }

    @Test
    void exceptionAfterRealCommitBeforeAfterCommitProducesNoHistoricalSuccessEvent() throws Exception {
        var run = running(false, 1);
        var manager = new DownloadTaskRunnerIT.AcknowledgementFailingTransactionManager(dataSource);
        var uncertain = new DownloadTaskRepository(jdbc, manager, json, observer);
        manager.arm();
        code(ErrorCode.PERSISTENCE_FAILED, () -> commits(uncertain, manager, NOW)
                .commit(run.permit(), run.batches().getFirst().batchId(), batch(row("AA", "10")), 1));
        assertThat(observer.events).isEmpty();
        assertThat(independentNumber("SELECT COUNT(*) FROM commit_test__prices")).isEqualTo(1);
        var task = repository.findTask(run.permit().taskId()).orElseThrow();
        var recovered = repository.recoverStoppedTask(task.taskId(), task.activeRunId(),
                task.runGeneration(), task.version(), NOW.plusSeconds(1));
        assertThat(recovered.status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(observer.events).containsExactly(new TaskFinished(task.taskId(), KEY, 1,
                DownloadTask.Status.SUCCEEDED, true, 1000, 0, 0,
                new Counts(1, 0, 0, 1, 0, 0, 1, 1, 0), null));
    }

    @Test
    void exceptionAfterAfterCommitDoesNotEraseFactOrAllowBatchReplay() throws Exception {
        var run = running(false, 1);
        var id = run.batches().getFirst().batchId();
        var manager = new DataSourceTransactionManager(dataSource) {
            @Override protected void doCommit(DefaultTransactionStatus status) {
                super.doCommit(status);
                if (!status.isReadOnly()) TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override public void afterCommit() {
                                throw new TransactionSystemException("private-receipt-after-callback");
                            }
                        });
            }
        };
        var uncertain = new DownloadTaskRepository(jdbc, manager, json, observer);
        code(ErrorCode.PERSISTENCE_FAILED,
                () -> commits(uncertain, manager, NOW).commit(run.permit(), id, batch(row("AA", "10")), 1));
        assertThat(observer.events).containsExactly(new BatchFinished(run.permit().taskId(), KEY, 1,
                id, DownloadBatch.Status.SUCCEEDED, 1, 0, 1, 1, 0, null));
        code(ErrorCode.TASK_STATE_CONFLICT,
                () -> commits(repository, transactions, NOW).commit(run.permit(), id, batch(row("AA", "20")), 1));
        assertThat(observer.events).hasSize(1);
        assertThat(independentNumber("SELECT amount FROM commit_test__prices WHERE symbol='AA'")).isEqualTo(10);
    }

    @Test
    void observerRuntimeFailuresCannotChangeCommittedResultsOrPreventFollowingBatches() throws Exception {
        observer.check = event -> { throw new IllegalStateException("private-observer-failure"); };
        var run = running(true, 2);
        var commit = commits(repository, transactions, NOW);
        var first = run.batches().getFirst().batchId();
        var second = run.batches().get(1).batchId();
        assertThat(commit.commit(run.permit(), first, batch(row("AA", "10")), 1))
                .isEqualTo(new WriteCounts(1, 0));
        repository.claimBatch(run.permit(), second, NOW).orElseThrow();
        assertThat(commit.commit(run.permit(), second, batch(), 0)).isEqualTo(new WriteCounts(0, 0));
        repository.finishTask(run.permit(), DownloadTask.Status.SUCCEEDED, null, NOW);
        assertThat(repository.findTask(run.permit().taskId()).orElseThrow().status())
                .isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(observer.events).hasSize(3);
        assertThat(repository.counts(run.permit().taskId())).isEqualTo(new Counts(2, 0, 0, 2, 0, 0, 1, 1, 0));
    }

    @Test
    void fatalObserverErrorsRemainFatalAfterDatabaseCommit() {
        var task = queued(false);
        var fatal = new LinkageError("fatal-observer");
        observer.check = event -> { throw fatal; };
        assertThatThrownBy(() -> repository.claimTask(task.taskId(), task.activeRunId(), NOW, NOW.plusSeconds(60)))
                .isSameAs(fatal);
        assertThat(independentStatus("tensor_download_task", "task_id", task.taskId())).isEqualTo("RUNNING");
    }

    private DownloadTask queued(boolean range) {
        var policy = new BatchDownloadDescriptor(List.of(endpoint("from", "to"), endpoint("to", "from")),
                "from", "to", BatchDownloadDescriptor.DateAxis.TRADE_DATE, "Trade date",
                BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true,
                BatchDownloadDescriptor.Availability.AVAILABLE, null, "v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE, null, "Test rule"));
        var mode = range ? DownloadMode.RANGE : DownloadMode.SINGLE;
        return repository.insert(new NewTask(UUID.randomUUID(), UUID.randomUUID(), KEY, mode,
                range ? Map.of("from", "20260910", "to", "20260911") : Map.of("symbol", "AA"),
                "a".repeat(64), json.policySnapshot(mode, range ? policy : null), UUID.randomUUID(), NOW));
    }

    private Run running(boolean range, int count) {
        var task = queued(range);
        task = repository.claimTask(task.taskId(), task.activeRunId(), NOW, NOW.plusSeconds(60)).orElseThrow();
        var permit = new ExecutionPermit(task.taskId(), task.activeRunId(), task.runGeneration());
        var batches = new ArrayList<NewBatch>();
        for (int i = 1; i <= count; i++) batches.add(new NewBatch(UUID.randomUUID(),
                String.format(Locale.ROOT, "%06d", i),
                range ? new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)) : null,
                range ? Map.of() : task.params()));
        repository.savePlan(permit, batches, count, NOW);
        repository.claimBatch(permit, batches.getFirst().batchId(), NOW).orElseThrow();
        observer.events.clear();
        return new Run(permit, batches);
    }

    private static BatchCommitService commits(DownloadTaskRepository repo, DataSourceTransactionManager manager,
            Instant now) throws Exception {
        var persistence = new PersistenceService(catalog(), new DatasetLockManager(),
                new ExistingKeyRepository(jdbc), new GenericUpsertRepository(jdbc), manager);
        return new BatchCommitService(persistence, repo, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static DateRange day(int day) {
        return new DateRange(LocalDate.of(2026, 9, day), LocalDate.of(2026, 9, day));
    }

    private static String independentStatus(String table, String column, UUID id) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT status FROM " + table + " WHERE " + column + "=?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static long independentNumber(String sql) {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private record Run(ExecutionPermit permit, List<NewBatch> batches) {}

    private static final class RecordingObserver implements DownloadTaskObserver {
        final List<Object> events = new ArrayList<>();
        Consumer<Object> check = event -> {};
        private void record(Object event) {
            events.add(event);
            check.accept(event);
        }
        @Override public void taskStarted(TaskStarted event) { record(event); }
        @Override public void batchFinished(BatchFinished event) { record(event); }
        @Override public void taskFinished(TaskFinished event) { record(event); }
    }
}
