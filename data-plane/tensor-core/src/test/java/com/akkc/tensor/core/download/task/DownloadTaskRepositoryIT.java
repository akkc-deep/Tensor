package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.download.task.DownloadTaskRepository.BatchFilter;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.Counts;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.ExecutionPermit;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.NewBatch;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.NewTask;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.RequeueMode;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.StoredError;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.TaskFilter;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

@Testcontainers
class DownloadTaskRepositoryIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.6").withCommand("--log-bin-trust-function-creators=1");

    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    DownloadTaskRepository repository;
    final DownloadTaskJson json = new DownloadTaskJson();
    static final Instant NOW = Instant.parse("2026-09-11T02:03:04.123Z");

    @BeforeAll
    static void schema() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(ds);
        manager = new DataSourceTransactionManager(ds);
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new FileSystemResource(
                            "../tensor-app/src/main/resources/db/migration/V8__create_download_task_tables.sql"));
        }
    }

    @BeforeEach
    void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_batch");
        for (String id :
                jdbc.queryForList(
                        "SELECT batch_id FROM tensor_download_batch ORDER BY LENGTH(batch_key)"
                                + " DESC",
                        String.class))
            jdbc.update("DELETE FROM tensor_download_batch WHERE batch_id=?", id);
        jdbc.update("DELETE FROM tensor_download_task");
        repository = new DownloadTaskRepository(jdbc, manager, json);
    }

    NewTask input() {
        return new NewTask(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new DatasetKey(new PluginId("test"), new ApiName("daily")),
                DownloadMode.SINGLE,
                Map.of("ts_code", "AA"),
                "a".repeat(64),
                json.policySnapshot(DownloadMode.SINGLE, null),
                UUID.randomUUID(),
                NOW);
    }

    ExecutionPermit permit(DownloadTask t) {
        return new ExecutionPermit(t.taskId(), t.activeRunId(), t.runGeneration());
    }

    DownloadTask running() {
        var t = repository.insert(input());
        return repository
                .claimTask(t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60))
                .orElseThrow();
    }

    NewBatch root() {
        return new NewBatch(UUID.randomUUID(), "000001", null, Map.of("ts_code", "AA"));
    }

    void code(ErrorCode expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(TensorException.class)
                .extracting(e -> ((TensorException) e).code())
                .isEqualTo(expected);
    }

    @Test
    void insertAndClaimPreserveFactsAndRejectOldPermit() {
        var in = input();
        var t = repository.insert(in);
        assertThat(repository.findSubmission(in.submissionId())).contains(t);
        assertThat(t.version()).isEqualTo(1);
        assertThat(t.createdAt()).isEqualTo(NOW);
        assertThat(t.params()).isEqualTo(in.normalizedParams());
        assertThat(t.requestHash())
                .isEqualTo(json.requestHash(in.datasetKey(), in.mode(), in.normalizedParams()));
        assertThat(repository.claimTask(t.taskId(), UUID.randomUUID(), NOW, NOW.plusSeconds(60)))
                .isEmpty();
        var claimed =
                repository
                        .claimTask(t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60))
                        .orElseThrow();
        assertThat(claimed.runGeneration()).isEqualTo(1);
        assertThat(claimed.version()).isEqualTo(2);
        repository.reserveRequest(permit(claimed), 1, NOW);
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> repository.reserveRequest(permit(claimed), 1, NOW));
        assertThat(repository.findTask(t.taskId()).orElseThrow().requestCount()).isEqualTo(1);
        assertThat(repository.claimTask(t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60)))
                .isEmpty();
    }

    @Test
    void planSuccessJoinsOuterTransactionAndRollsBack() {
        var t = running();
        var p = permit(t);
        var b = root();
        repository.savePlan(p, List.of(b), 1, NOW);
        repository.claimBatch(p, b.batchId(), NOW).orElseThrow();
        assertThatThrownBy(() -> repository.succeedBatch(p, b.batchId(), 10, 6, 4, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .execute(
                                                s -> {
                                                    repository.lockTask(p);
                                                    repository.lockBatch(p, b.batchId());
                                                    repository.succeedBatch(
                                                            p, b.batchId(), 10, 6, 4, NOW);
                                                    throw new IllegalArgumentException();
                                                }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.counts(t.taskId()).running()).isEqualTo(1);
        new TransactionTemplate(manager)
                .executeWithoutResult(s -> repository.succeedBatch(p, b.batchId(), 10, 6, 4, NOW));
        repository.finishTask(p, DownloadTask.Status.SUCCEEDED, null, NOW);
        assertThat(repository.snapshot(t.taskId()).counts().sourceRows()).isEqualTo(10);
        code(ErrorCode.TASK_STATE_CONFLICT, () -> repository.reserveRequest(p, 5, NOW));
    }

    @Test
    void ownTransactionsRejectOuterAndDeadlineIsExclusive() {
        var t = running();
        var p = permit(t);
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        s -> {
                            assertThatThrownBy(() -> repository.insert(input()))
                                    .isInstanceOf(IllegalStateException.class);
                            assertThatThrownBy(() -> repository.snapshot(t.taskId()))
                                    .isInstanceOf(IllegalStateException.class);
                            assertThatThrownBy(
                                            () ->
                                                    repository.claimTask(
                                                            t.taskId(),
                                                            t.activeRunId(),
                                                            NOW,
                                                            NOW.plusSeconds(1)))
                                    .isInstanceOf(IllegalStateException.class);
                        });
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> repository.savePlan(p, List.of(root()), 1, NOW.plusSeconds(60)));
        assertThat(repository.counts(t.taskId()).totalBatches()).isZero();
        assertThatThrownBy(() -> repository.savePlan(p, List.of(), 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.finishTask(p, DownloadTask.Status.SUCCEEDED, null, NOW));
    }

    NewTask rangeInput() {
        var in = input();
        var descriptor =
                new BatchDownloadDescriptor(
                        List.of(endpoint("from_date", "to_date"), endpoint("to_date", "from_date")),
                        "from_date",
                        "to_date",
                        BatchDownloadDescriptor.DateAxis.REPORT_PERIOD,
                        "Period",
                        BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE,
                        true,
                        BatchDownloadDescriptor.Availability.AVAILABLE,
                        null,
                        "v1",
                        new BatchDownloadDescriptor.CompletenessRule(
                                BatchDownloadDescriptor.CompletenessRule.Kind.CONFIRMED_ROW_LIMIT,
                                100L,
                                "documented"));
        return new NewTask(
                in.taskId(),
                in.submissionId(),
                in.datasetKey(),
                DownloadMode.RANGE,
                Map.of("from_date", "20231231", "to_date", "20240301"),
                in.definitionHash(),
                json.policySnapshot(DownloadMode.RANGE, descriptor),
                in.activeRunId(),
                NOW);
    }

    static com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor endpoint(
            String name, String related) {
        return new com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor(
                name,
                name,
                null,
                com.akkc.tensor.plugin.api.descriptor.ParameterType.DATE_RANGE_MEMBER,
                true,
                null,
                List.of(),
                null,
                related);
    }

    DownloadTask rangeRunning() {
        var t = repository.insert(rangeInput());
        return repository
                .claimTask(t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60))
                .orElseThrow();
    }

    NewBatch batch(String key, String start, String end) {
        return new NewBatch(
                UUID.randomUUID(),
                key,
                new DateRange(LocalDate.parse(start), LocalDate.parse(end)),
                Map.of("from_date", start.replace("-", ""), "to_date", end.replace("-", "")));
    }

    void succeed(ExecutionPermit p, UUID id, long source, long inserted, long updated) {
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        s -> repository.succeedBatch(p, id, source, inserted, updated, NOW));
    }

    void trigger(String key) {
        jdbc.execute(
                "CREATE TRIGGER reject_batch BEFORE INSERT ON tensor_download_batch FOR EACH ROW"
                        + " BEGIN IF NEW.batch_key='"
                        + key
                        + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='secret-value'; END IF;"
                        + " END");
    }

    @Test
    void planAndSplitAreAtomicOnSecondInsertFailure() {
        var t = rangeRunning();
        var p = permit(t);
        var a = batch("000001", "2023-12-31", "2024-02-29");
        var b = batch("000002", "2024-03-01", "2024-03-01");
        trigger("000002");
        code(ErrorCode.PERSISTENCE_FAILED, () -> repository.savePlan(p, List.of(a, b), 4, NOW));
        assertThat(repository.snapshot(t.taskId()).counts().totalBatches()).isZero();
        assertThat(repository.findTask(t.taskId()).orElseThrow().planReady()).isFalse();
        jdbc.execute("DROP TRIGGER reject_batch");
        repository.savePlan(p, List.of(a, b), 4, NOW);
        repository.claimBatch(p, a.batchId(), NOW).orElseThrow();
        var left = batch("000001/0", "2023-12-31", "2023-12-31");
        var right = batch("000001/1", "2024-01-01", "2024-02-29");
        trigger("000001/1");
        code(
                ErrorCode.PERSISTENCE_FAILED,
                () -> repository.split(p, a.batchId(), left, right, 4, NOW));
        assertThat(repository.counts(t.taskId()).running()).isEqualTo(1);
        assertThat(repository.counts(t.taskId()).totalBatches()).isEqualTo(2);
        jdbc.execute("DROP TRIGGER reject_batch");
        repository.split(p, a.batchId(), left, right, 4, NOW);
        assertThat(repository.counts(t.taskId())).isEqualTo(new Counts(3, 3, 0, 0, 0, 1, 0, 0, 0));
        repository.claimBatch(p, left.batchId(), NOW).orElseThrow();
        succeed(p, left.batchId(), 10, 7, 3);
        repository.claimBatch(p, right.batchId(), NOW).orElseThrow();
        repository.failBatch(p, right.batchId(), ErrorCode.SOURCE_TIMEOUT, NOW);
        assertThat(repository.counts(t.taskId())).isEqualTo(new Counts(3, 1, 0, 1, 1, 1, 10, 7, 3));
        assertThat(repository.batches(t.taskId(), null, 1, 20).items())
                .extracting(DownloadBatch::batchKey)
                .containsExactly("000001/0", "000001/1", "000002");
        assertThat(repository.batches(t.taskId(), new BatchFilter(null, true), 1, 20).total())
                .isEqualTo(4);
        assertThat(
                        repository
                                .batches(
                                        t.taskId(),
                                        new BatchFilter(DownloadBatch.Status.SPLIT, true),
                                        1,
                                        20)
                                .items())
                .extracting(DownloadBatch::batchId)
                .containsExactly(a.batchId());
        repository.finishTask(p, DownloadTask.Status.PARTIAL_FAILED, ErrorCode.SOURCE_TIMEOUT, NOW);
        var done = repository.findTask(t.taskId()).orElseThrow();
        var nextRun = UUID.randomUUID();
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () ->
                        repository.requeue(
                                t.taskId(), done.version() - 1, nextRun, RequeueMode.RETRY, NOW));
        repository.requeue(t.taskId(), done.version(), nextRun, RequeueMode.RETRY, NOW);
        assertThat(repository.pendingBatches(t.taskId()))
                .extracting(DownloadBatch::batchId)
                .containsExactly(right.batchId(), b.batchId());
        assertThat(repository.pendingBatches(t.taskId()).getFirst().attemptCount()).isEqualTo(1);
        var next =
                repository.claimTask(t.taskId(), nextRun, NOW, NOW.plusSeconds(60)).orElseThrow();
        assertThat(
                        repository
                                .claimBatch(permit(next), right.batchId(), NOW)
                                .orElseThrow()
                                .attemptCount())
                .isEqualTo(2);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.failBatch(p, right.batchId(), ErrorCode.INTERNAL_ERROR, NOW));
        assertThat(repository.counts(t.taskId()).sourceRows()).isEqualTo(10);
    }

    @Test
    void emptyPlanRecoveryAndResumePreserveSuccessfulAndOrdinaryFailedFacts() {
        var empty = rangeRunning();
        repository.savePlan(permit(empty), List.of(), 0, NOW);
        assertThat(
                        repository
                                .recoverStoppedTask(
                                        empty.taskId(),
                                        empty.activeRunId(),
                                        empty.runGeneration(),
                                        empty.version(),
                                        NOW)
                                .status())
                .isEqualTo(DownloadTask.Status.SUCCEEDED);
        var t = rangeRunning();
        var p = permit(t);
        var a = batch("000001", "2023-12-31", "2023-12-31");
        var b = batch("000002", "2024-01-01", "2024-01-01");
        var c = batch("000003", "2024-02-29", "2024-02-29");
        var d = batch("000004", "2024-03-01", "2024-03-01");
        repository.savePlan(p, List.of(a, b, c, d), 4, NOW);
        repository.claimBatch(p, a.batchId(), NOW);
        succeed(p, a.batchId(), 3, 2, 1);
        repository.claimBatch(p, b.batchId(), NOW);
        repository.failBatch(p, b.batchId(), ErrorCode.SOURCE_AUTH_FAILED, NOW);
        repository.claimBatch(p, c.batchId(), NOW);
        repository.reserveRequest(p, 10, NOW);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.finishTask(p, DownloadTask.Status.PARTIAL_FAILED, null, NOW));
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () ->
                        repository.recoverStoppedTask(
                                t.taskId(),
                                UUID.randomUUID(),
                                p.runGeneration(),
                                t.version(),
                                NOW));
        var interrupted =
                repository.recoverStoppedTask(
                        t.taskId(), p.activeRunId(), p.runGeneration(), t.version(), NOW);
        assertThat(interrupted.status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(repository.counts(t.taskId())).isEqualTo(new Counts(4, 1, 0, 1, 2, 0, 3, 2, 1));
        var run = UUID.randomUUID();
        repository.requeue(t.taskId(), interrupted.version(), run, RequeueMode.RESUME, NOW);
        var queued = repository.findTask(t.taskId()).orElseThrow();
        assertThat(queued.startedAt()).isNull();
        assertThat(queued.finishedAt()).isNull();
        assertThat(queued.deadlineAt()).isNull();
        assertThat(queued.lastError()).isNull();
        assertThat(queued.requestCount()).isEqualTo(1);
        assertThat(queued.runRequestCount()).isEqualTo(1);
        assertThat(repository.pendingBatches(t.taskId()))
                .extracting(DownloadBatch::batchId)
                .containsExactly(c.batchId(), d.batchId());
        var next = repository.claimTask(t.taskId(), run, NOW, NOW.plusSeconds(60)).orElseThrow();
        assertThat(next.runRequestCount()).isZero();
        assertThat(next.requestCount()).isEqualTo(1);
        assertThat(next.runGeneration()).isEqualTo(2);
        code(ErrorCode.TASK_STATE_CONFLICT, () -> repository.claimBatch(p, c.batchId(), NOW));
        assertThat(
                        repository
                                .batches(
                                        t.taskId(),
                                        new BatchFilter(DownloadBatch.Status.FAILED, false),
                                        1,
                                        20)
                                .items()
                                .getFirst()
                                .error()
                                .code())
                .isEqualTo(ErrorCode.SOURCE_AUTH_FAILED);
    }

    @Test
    void validatesRangesKeysNodeBudgetAndForeignTaskBatches() {
        var t = rangeRunning();
        var p = permit(t);
        var a = batch("000001", "2023-12-31", "2024-03-01");
        code(ErrorCode.TASK_LIMIT_EXCEEDED, () -> repository.savePlan(p, List.of(a), 0, NOW));
        assertThatThrownBy(() -> repository.savePlan(p, List.of(a, a), 2, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                repository.savePlan(
                                        p,
                                        List.of(batch("000001", "2023-12-30", "2024-03-01")),
                                        1,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        repository.savePlan(p, List.of(a), 3, NOW);
        repository.claimBatch(p, a.batchId(), NOW);
        var left = batch("000001/0", "2023-12-31", "2024-02-28");
        var right = batch("000001/1", "2024-02-29", "2024-03-01");
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> repository.split(p, a.batchId(), left, right, 2, NOW));
        assertThatThrownBy(
                        () ->
                                repository.split(
                                        p,
                                        a.batchId(),
                                        left,
                                        batch("000001/1", "2024-03-01", "2024-03-01"),
                                        3,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        var other = rangeRunning();
        repository.savePlan(
                permit(other), List.of(batch("000001", "2023-12-31", "2024-03-01")), 1, NOW);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.split(permit(other), a.batchId(), left, right, 3, NOW));
        assertThat(repository.claimBatch(permit(other), a.batchId(), NOW)).isEmpty();
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> repository.split(p, a.batchId(), left, right, 3, NOW.plusSeconds(60)));
        repository.split(p, a.batchId(), left, right, 3, NOW);
        assertThat(repository.pendingBatches(t.taskId()))
                .extracting(DownloadBatch::range)
                .containsExactly(left.range(), right.range());
    }

    @Test
    void countersAndVersionNeverOverflowAndDeadlineAllowsFailureRecording() {
        var t = running();
        var p = permit(t);
        var b = root();
        repository.savePlan(p, List.of(b), 1, NOW);
        jdbc.update(
                "UPDATE tensor_download_batch SET attempt_count=? WHERE batch_id=?",
                Integer.MAX_VALUE,
                b.batchId().toString());
        code(ErrorCode.TASK_LIMIT_EXCEEDED, () -> repository.claimBatch(p, b.batchId(), NOW));
        jdbc.update(
                "UPDATE tensor_download_batch SET attempt_count=0 WHERE batch_id=?",
                b.batchId().toString());
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> repository.claimBatch(p, b.batchId(), NOW.plusSeconds(60)));
        jdbc.update(
                "UPDATE tensor_download_task SET request_count=?,run_request_count=? WHERE"
                        + " task_id=?",
                Long.MAX_VALUE,
                Long.MAX_VALUE - 1,
                t.taskId().toString());
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> repository.reserveRequest(p, Long.MAX_VALUE, NOW));
        assertThat(repository.findTask(t.taskId()).orElseThrow().runRequestCount())
                .isEqualTo(Long.MAX_VALUE - 1);
        repository.claimBatch(p, b.batchId(), NOW);
        repository.failBatch(p, b.batchId(), ErrorCode.TASK_LIMIT_EXCEEDED, NOW.plusSeconds(61));
        jdbc.update(
                "UPDATE tensor_download_task SET version=? WHERE task_id=?",
                Long.MAX_VALUE,
                t.taskId().toString());
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () ->
                        repository.finishTask(
                                p,
                                DownloadTask.Status.FAILED,
                                ErrorCode.TASK_LIMIT_EXCEEDED,
                                NOW.plusSeconds(61)));
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () ->
                        repository.recoverStoppedTask(
                                t.taskId(), t.activeRunId(), 1, Long.MAX_VALUE, NOW));
        jdbc.update(
                "UPDATE tensor_download_task SET version=2 WHERE task_id=?", t.taskId().toString());
        repository.finishTask(
                p, DownloadTask.Status.FAILED, ErrorCode.TASK_LIMIT_EXCEEDED, NOW.plusSeconds(61));
        jdbc.update(
                "UPDATE tensor_download_task SET version=? WHERE task_id=?",
                Long.MAX_VALUE,
                t.taskId().toString());
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () ->
                        repository.requeue(
                                t.taskId(),
                                Long.MAX_VALUE,
                                UUID.randomUUID(),
                                RequeueMode.RETRY,
                                NOW));
        var queued = repository.insert(input());
        jdbc.update(
                "UPDATE tensor_download_task SET run_generation=? WHERE task_id=?",
                Integer.MAX_VALUE,
                queued.taskId().toString());
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () ->
                        repository.claimTask(
                                queued.taskId(), queued.activeRunId(), NOW, NOW.plusSeconds(60)));
        jdbc.update(
                "UPDATE tensor_download_task SET run_generation=0,version=? WHERE task_id=?",
                Long.MAX_VALUE,
                queued.taskId().toString());
        code(
                ErrorCode.TASK_LIMIT_EXCEEDED,
                () ->
                        repository.claimTask(
                                queued.taskId(), queued.activeRunId(), NOW, NOW.plusSeconds(60)));
    }

    @Test
    void utcAndCanonicalJsonBoundariesRoundTripThroughMysqlWhitespace() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var in = input();
            var params = Map.<String, Object>of("aa", "x".repeat(8183));
            var task =
                    repository.insert(
                            new NewTask(
                                    in.taskId(),
                                    in.submissionId(),
                                    in.datasetKey(),
                                    in.mode(),
                                    params,
                                    in.definitionHash(),
                                    in.policySnapshot(),
                                    in.activeRunId(),
                                    NOW.plusNanos(456789)));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertThat(repository.findTask(task.taskId()).orElseThrow().createdAt()).isEqualTo(NOW);
            assertThat(repository.findTask(task.taskId()).orElseThrow().params()).isEqualTo(params);
            assertThat(
                            jdbc.queryForObject(
                                            "SELECT params FROM tensor_download_task WHERE"
                                                    + " task_id=?",
                                            String.class,
                                            task.taskId().toString())
                                    .length())
                    .isGreaterThan(8192);
            assertThatThrownBy(
                            () ->
                                    repository.insert(
                                            new NewTask(
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    in.datasetKey(),
                                                    in.mode(),
                                                    Map.of("aa", "x".repeat(8184)),
                                                    in.definitionHash(),
                                                    in.policySnapshot(),
                                                    in.activeRunId(),
                                                    NOW)))
                    .isInstanceOf(IllegalArgumentException.class);
            var range = rangeInput();
            var tree =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(range.policySnapshot());
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree)
                    .put(
                            "dateLabel",
                            "x"
                                    .repeat(
                                            16384
                                                    - range.policySnapshot().length()
                                                    + "Period".length()));
            String policy = json.validatePolicySnapshot(tree.toString());
            assertThat(policy.length()).isEqualTo(16384);
            var t =
                    repository.insert(
                            new NewTask(
                                    range.taskId(),
                                    range.submissionId(),
                                    range.datasetKey(),
                                    range.mode(),
                                    range.normalizedParams(),
                                    range.definitionHash(),
                                    policy,
                                    range.activeRunId(),
                                    NOW));
            assertThat(repository.findTask(t.taskId()).orElseThrow().policySnapshot())
                    .isEqualTo(policy);
            var run =
                    repository
                            .claimTask(t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60))
                            .orElseThrow();
            var b =
                    new NewBatch(
                            UUID.randomUUID(),
                            "000001",
                            new DateRange(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 29)),
                            Map.of("aa", "x".repeat(16375)));
            repository.savePlan(permit(run), List.of(b), 1, NOW);
            assertThat(repository.pendingBatches(t.taskId()).getFirst().sourceParams())
                    .isEqualTo(b.sourceParams());
            assertThatThrownBy(() -> json.writeBatchParams(Map.of("aa", "x".repeat(16376))))
                    .isInstanceOf(IllegalArgumentException.class);
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree)
                    .put("dateLabel", tree.get("dateLabel").textValue() + "x");
            assertThatThrownBy(
                            () ->
                                    repository.insert(
                                            new NewTask(
                                                    UUID.randomUUID(),
                                                    UUID.randomUUID(),
                                                    range.datasetKey(),
                                                    range.mode(),
                                                    range.normalizedParams(),
                                                    range.definitionHash(),
                                                    tree.toString(),
                                                    range.activeRunId(),
                                                    NOW)))
                    .isInstanceOf(IllegalArgumentException.class);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void duplicateSubmissionAndClaimAreSerializedAcrossConnections() throws Exception {
        var in = input();
        var other =
                new NewTask(
                        UUID.randomUUID(),
                        in.submissionId(),
                        in.datasetKey(),
                        in.mode(),
                        in.normalizedParams(),
                        in.definitionHash(),
                        in.policySnapshot(),
                        in.activeRunId(),
                        NOW);
        var results = race(() -> repository.insert(in), () -> repository.insert(other));
        assertThat(results.stream().filter(DownloadTask.class::isInstance)).hasSize(1);
        assertThat(
                        results.stream()
                                .filter(
                                        org.springframework.dao.DuplicateKeyException.class
                                                ::isInstance))
                .hasSize(1);
        assertThat(repository.tasks(null, 1, 20).total()).isEqualTo(1);
        var t = repository.findSubmission(in.submissionId()).orElseThrow();
        var claims =
                race(
                        () ->
                                repository.claimTask(
                                        t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60)),
                        () ->
                                repository.claimTask(
                                        t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60)));
        assertThat(claims.stream().map(v -> (Optional<?>) v).filter(Optional::isPresent))
                .hasSize(1);
        assertThat(repository.findTask(t.taskId()).orElseThrow().version()).isEqualTo(2);
    }

    static List<Object> race(java.util.concurrent.Callable<?> a, java.util.concurrent.Callable<?> b)
            throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> capture(start, a));
            var second = executor.submit(() -> capture(start, b));
            start.countDown();
            return List.of(
                    first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    static Object capture(
            java.util.concurrent.CountDownLatch start, java.util.concurrent.Callable<?> call)
            throws Exception {
        start.await();
        try {
            return call.call();
        } catch (RuntimeException e) {
            return e;
        }
    }

    @Test
    void queriesUseStableFiltersPaginationAndReadCorruptionFailsSafely() {
        var inserted = new ArrayList<DownloadTask>();
        for (int i = 0; i < 21; i++) inserted.add(repository.insert(input()));
        assertThat(repository.queuedCount()).isEqualTo(21);
        assertThat(repository.unfinishedTasks()).hasSize(21);
        assertThat(repository.queuedTasks(inserted.getFirst().activeRunId(), 5))
                .containsExactly(inserted.getFirst());
        var expected =
                inserted.stream()
                        .sorted(
                                Comparator.comparing(
                                        t -> t.taskId().toString(), Comparator.reverseOrder()))
                        .toList();
        assertThat(repository.tasks(null, 1, 20).items()).isEqualTo(expected.subList(0, 20));
        assertThat(repository.tasks(null, 2, 20).items()).containsExactly(expected.getLast());
        assertThat(repository.tasks(null, 1, 50).items()).hasSize(21);
        assertThat(repository.tasks(null, 1, 100).items()).hasSize(21);
        var t = inserted.getFirst();
        assertThat(
                        repository
                                .tasks(
                                        new TaskFilter(
                                                "test",
                                                "daily",
                                                DownloadTask.Status.QUEUED,
                                                t.submissionId()),
                                        1,
                                        20)
                                .items())
                .containsExactly(t);
        assertThatThrownBy(() -> repository.tasks(null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.tasks(null, 1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update(
                "UPDATE tensor_download_task SET params=JSON_OBJECT('password','secret-value')"
                        + " WHERE task_id=?",
                t.taskId().toString());
        code(ErrorCode.QUERY_FAILED, () -> repository.findTask(t.taskId()));
        jdbc.update(
                "UPDATE tensor_download_task SET"
                    + " params=JSON_OBJECT(),last_error_code='UNKNOWN',last_error_message='secret-value'"
                    + " WHERE task_id=?",
                t.taskId().toString());
        assertThatThrownBy(() -> repository.findTask(t.taskId()))
                .hasMessage("Query failed")
                .hasNoCause();
        jdbc.update(
                "UPDATE tensor_download_task SET"
                    + " last_error_code=NULL,last_error_message=NULL,policy_snapshot=JSON_OBJECT('schemaVersion',9)"
                    + " WHERE task_id=?",
                t.taskId().toString());
        code(ErrorCode.QUERY_FAILED, () -> repository.findTask(t.taskId()));
        for (var error : ErrorCode.values())
            assertThat(new StoredError(error).message())
                    .isNotBlank()
                    .doesNotContain("secret-value");
    }

    @Test
    void snapshotAndPageKeepIndependentRepeatableReadViewsDuringConcurrentWrites()
            throws Exception {
        var t = rangeRunning();
        var p = permit(t);
        var a = batch("000001", "2023-12-31", "2024-03-01");
        repository.savePlan(p, List.of(a), 3, NOW);
        repository.claimBatch(p, a.batchId(), NOW);
        var left = batch("000001/0", "2023-12-31", "2024-02-28");
        var right = batch("000001/1", "2024-02-29", "2024-03-01");
        var gate = new GatedDataSource(jdbc.getDataSource(), "SELECT * FROM tensor_download_task");
        var isolated =
                new DownloadTaskRepository(
                        new JdbcTemplate(gate), new DataSourceTransactionManager(gate), json);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var snapshot = executor.submit(() -> isolated.snapshot(t.taskId()));
            assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                repository.split(p, a.batchId(), left, right, 3, NOW.plusSeconds(1));
            } finally {
                gate.release.countDown();
            }
            var result = snapshot.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.counts()).isEqualTo(new Counts(1, 0, 1, 0, 0, 0, 0, 0, 0));
            assertThat(result.task().orElseThrow().updatedAt()).isEqualTo(NOW);
            assertThat(repository.counts(t.taskId()).splitBatches()).isEqualTo(1);
            assertThat(gate.timeouts).allMatch(timeout -> timeout > 0 && timeout <= 60);
            assertThat(gate.repeatableRead).isTrue();
            assertThat(gate.readOnly).isTrue();
        }
        var pageGate =
                new GatedDataSource(
                        jdbc.getDataSource(), "SELECT COUNT(*) FROM tensor_download_task");
        var pageRepository =
                new DownloadTaskRepository(
                        new JdbcTemplate(pageGate),
                        new DataSourceTransactionManager(pageGate),
                        json);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var page = executor.submit(() -> pageRepository.tasks(null, 1, 20));
            assertThat(pageGate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                repository.insert(input());
            } finally {
                pageGate.release.countDown();
            }
            var result = page.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.total()).isEqualTo(1);
            assertThat(result.items()).hasSize(1);
            assertThat(repository.tasks(null, 1, 20).total()).isEqualTo(2);
        }
        var batchGate =
                new GatedDataSource(
                        jdbc.getDataSource(), "SELECT COUNT(*) FROM tensor_download_batch");
        var batchRepository =
                new DownloadTaskRepository(
                        new JdbcTemplate(batchGate),
                        new DataSourceTransactionManager(batchGate),
                        json);
        repository.claimBatch(p, right.batchId(), NOW);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var page = executor.submit(() -> batchRepository.batches(t.taskId(), null, 1, 20));
            assertThat(batchGate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                repository.split(
                        p,
                        right.batchId(),
                        batch("000001/1/0", "2024-02-29", "2024-02-29"),
                        batch("000001/1/1", "2024-03-01", "2024-03-01"),
                        5,
                        NOW);
            } finally {
                batchGate.release.countDown();
            }
            var result = page.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.total()).isEqualTo(2);
            assertThat(result.items()).hasSize(2);
            assertThat(repository.batches(t.taskId(), null, 1, 20).total()).isEqualTo(3);
        }
    }

    static final class GatedDataSource extends AbstractDataSource {
        final javax.sql.DataSource delegate;
        final String prefix;
        final java.util.concurrent.CountDownLatch
                entered = new java.util.concurrent.CountDownLatch(1),
                release = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicBoolean first =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        final List<Integer> timeouts = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile boolean repeatableRead, readOnly;

        GatedDataSource(javax.sql.DataSource delegate, String prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }

        @Override
        public java.sql.Connection getConnection() throws java.sql.SQLException {
            return wrap(delegate.getConnection());
        }

        @Override
        public java.sql.Connection getConnection(String u, String p) throws java.sql.SQLException {
            return wrap(delegate.getConnection(u, p));
        }

        java.sql.Connection wrap(java.sql.Connection connection) {
            return (java.sql.Connection)
                    java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {java.sql.Connection.class},
                            (proxy, method, args) -> {
                                var value = invoke(connection, method, args);
                                if (method.getName().equals("prepareStatement")
                                        && value instanceof java.sql.PreparedStatement statement) {
                                    String sql = (String) args[0];
                                    return java.lang.reflect.Proxy.newProxyInstance(
                                            getClass().getClassLoader(),
                                            new Class<?>[] {java.sql.PreparedStatement.class},
                                            (ignored, call, values) -> {
                                                if (call.getName().equals("setQueryTimeout"))
                                                    timeouts.add((Integer) values[0]);
                                                Object result = invoke(statement, call, values);
                                                if (call.getName().equals("executeQuery")
                                                        && sql.startsWith(prefix)
                                                        && first.compareAndSet(true, false)) {
                                                    repeatableRead =
                                                            connection.getTransactionIsolation()
                                                                    == java.sql.Connection
                                                                            .TRANSACTION_REPEATABLE_READ;
                                                    readOnly = connection.isReadOnly();
                                                    entered.countDown();
                                                    if (!release.await(
                                                            10,
                                                            java.util.concurrent.TimeUnit.SECONDS))
                                                        throw new AssertionError(
                                                                "reader gate timed out");
                                                }
                                                return result;
                                            });
                                }
                                return value;
                            });
        }

        static Object invoke(Object target, java.lang.reflect.Method method, Object[] args)
                throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (java.lang.reflect.InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    @Test
    void recoversQueuedCompletedPlansAndRejectsAllInvalidTerminalTargets() {
        var t = running();
        var p = permit(t);
        var b = root();
        repository.savePlan(p, List.of(b), 1, NOW);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.finishTask(p, DownloadTask.Status.SUCCEEDED, null, NOW));
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.finishTask(p, DownloadTask.Status.PARTIAL_FAILED, null, NOW));
        repository.claimBatch(p, b.batchId(), NOW);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.finishTask(p, DownloadTask.Status.FAILED, null, NOW));
        succeed(p, b.batchId(), 4, 3, 1);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.finishTask(p, DownloadTask.Status.FAILED, null, NOW));
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.finishTask(p, DownloadTask.Status.QUEUED, null, NOW));
        jdbc.update(
                "UPDATE tensor_download_task SET status='QUEUED' WHERE task_id=?",
                t.taskId().toString());
        var done =
                repository.recoverStoppedTask(
                        t.taskId(), t.activeRunId(), t.runGeneration(), t.version(), NOW);
        assertThat(done.status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(done.requestCount()).isZero();
        assertThat(repository.counts(t.taskId()).sourceRows()).isEqualTo(4);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () ->
                        repository.recoverStoppedTask(
                                t.taskId(), t.activeRunId(), t.runGeneration(), t.version(), NOW));
        var unplanned = repository.insert(input());
        assertThat(
                        repository
                                .recoverStoppedTask(
                                        unplanned.taskId(), unplanned.activeRunId(), 0, 1, NOW)
                                .status())
                .isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThatThrownBy(() -> repository.lockTask(p)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.lockBatch(p, b.batchId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void
            rejectsInvalidBoundariesWithoutLeakingInputAndAllHighLevelMethodsRejectOuterTransactions() {
        var t = running();
        var p = permit(t);
        var b = root();
        assertThatThrownBy(
                        () ->
                                repository.tasks(
                                        new TaskFilter("secret-value", null, null, null), 1, 20))
                .hasMessage("Invalid download task input");
        assertThatThrownBy(() -> new NewBatch(UUID.randomUUID(), "000001", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        var mutable = new HashMap<String, Object>();
        mutable.put("aa", "value");
        var batch = new NewBatch(UUID.randomUUID(), "000001", null, mutable);
        mutable.put("aa", "changed");
        assertThat(batch.sourceParams().get("aa")).isEqualTo("value");
        assertThatThrownBy(() -> new ExecutionPermit(t.taskId(), t.activeRunId(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        code(
                ErrorCode.TASK_STATE_CONFLICT,
                () ->
                        repository.savePlan(
                                new ExecutionPermit(t.taskId(), t.activeRunId(), 2),
                                List.of(b),
                                1,
                                NOW));
        var calls =
                List.<Runnable>of(
                        () -> repository.findTask(t.taskId()),
                        () -> repository.findSubmission(t.submissionId()),
                        () -> repository.queuedCount(),
                        () -> repository.queuedTasks(t.activeRunId(), 1),
                        () -> repository.unfinishedTasks(),
                        () -> repository.tasks(null, 1, 20),
                        () -> repository.batches(t.taskId(), null, 1, 20),
                        () -> repository.pendingBatches(t.taskId()),
                        () -> repository.counts(t.taskId()),
                        () -> repository.snapshot(t.taskId()),
                        () -> repository.insert(input()),
                        () ->
                                repository.claimTask(
                                        t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60)),
                        () -> repository.claimBatch(p, b.batchId(), NOW),
                        () -> repository.reserveRequest(p, 10, NOW),
                        () -> repository.savePlan(p, List.of(b), 1, NOW),
                        () -> repository.split(p, b.batchId(), b, b, 1, NOW),
                        () -> repository.failBatch(p, b.batchId(), ErrorCode.INTERNAL_ERROR, NOW),
                        () -> repository.finishTask(p, DownloadTask.Status.FAILED, null, NOW),
                        () ->
                                repository.requeue(
                                        t.taskId(),
                                        t.version(),
                                        UUID.randomUUID(),
                                        RequeueMode.RETRY,
                                        NOW),
                        () ->
                                repository.recoverStoppedTask(
                                        t.taskId(), t.activeRunId(), 1, t.version(), NOW));
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status ->
                                calls.forEach(
                                        call ->
                                                assertThatThrownBy(call::run)
                                                        .isInstanceOf(
                                                                IllegalStateException.class)));
        assertThat(repository.findTask(t.taskId()).orElseThrow()).isEqualTo(t);
    }

    @Test
    void corruptStoredStatesAndDatabaseFailuresHaveFixedErrorCodes() throws Exception {
        var t = running();
        var p = permit(t);
        var b = root();
        repository.savePlan(p, List.of(b), 1, NOW);
        jdbc.execute(
                "ALTER TABLE tensor_download_task ALTER CHECK ck_download_task_status NOT"
                        + " ENFORCED");
        jdbc.execute(
                "ALTER TABLE tensor_download_batch ALTER CHECK ck_download_batch_status NOT"
                        + " ENFORCED");
        try {
            jdbc.update(
                    "UPDATE tensor_download_task SET status='UNKNOWN' WHERE task_id=?",
                    t.taskId().toString());
            jdbc.update(
                    "UPDATE tensor_download_batch SET status='UNKNOWN' WHERE batch_id=?",
                    b.batchId().toString());
            code(ErrorCode.QUERY_FAILED, () -> repository.findTask(t.taskId()));
            code(ErrorCode.QUERY_FAILED, () -> repository.batches(t.taskId(), null, 1, 20));
        } finally {
            jdbc.update(
                    "UPDATE tensor_download_task SET status='RUNNING' WHERE task_id=?",
                    t.taskId().toString());
            jdbc.update(
                    "UPDATE tensor_download_batch SET status='PENDING' WHERE batch_id=?",
                    b.batchId().toString());
            jdbc.execute(
                    "ALTER TABLE tensor_download_task ALTER CHECK ck_download_task_status"
                            + " ENFORCED");
            jdbc.execute(
                    "ALTER TABLE tensor_download_batch ALTER CHECK ck_download_batch_status"
                            + " ENFORCED");
        }
        var failing =
                new AbstractDataSource() {
                    @Override
                    public java.sql.Connection getConnection() throws java.sql.SQLException {
                        throw new java.sql.SQLException("secret-value");
                    }

                    @Override
                    public java.sql.Connection getConnection(String u, String p)
                            throws java.sql.SQLException {
                        return getConnection();
                    }
                };
        var broken =
                new DownloadTaskRepository(
                        new JdbcTemplate(failing), new DataSourceTransactionManager(failing), json);
        code(ErrorCode.QUERY_FAILED, () -> broken.snapshot(t.taskId()));
        assertThatThrownBy(() -> broken.insert(input()))
                .hasMessage("Persistence failed")
                .hasNoCause();
    }

    @Test
    void successfulBatchCountersCannotOverflowTaskTotals() {
        var t = rangeRunning();
        var p = permit(t);
        var a = batch("000001", "2023-12-31", "2023-12-31");
        var b = batch("000002", "2024-02-29", "2024-02-29");
        repository.savePlan(p, List.of(a, b), 2, NOW);
        repository.claimBatch(p, a.batchId(), NOW);
        repository.claimBatch(p, b.batchId(), NOW);
        succeed(p, a.batchId(), Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        code(ErrorCode.TASK_LIMIT_EXCEEDED, () -> succeed(p, b.batchId(), 1, 0, 0));
        code(ErrorCode.TASK_LIMIT_EXCEEDED, () -> succeed(p, b.batchId(), 0, 1, 0));
        code(ErrorCode.TASK_LIMIT_EXCEEDED, () -> succeed(p, b.batchId(), 0, 0, 1));
        assertThat(repository.counts(t.taskId()).running()).isEqualTo(1);
        assertThat(repository.counts(t.taskId()).sourceRows()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void successOverflowGuardUsesCurrentRowsEvenWhenCallerAlreadyHasAnOldSnapshot() {
        var t = rangeRunning();
        var p = permit(t);
        var a = batch("000001", "2023-12-31", "2023-12-31");
        var b = batch("000002", "2024-02-29", "2024-02-29");
        repository.savePlan(p, List.of(a, b), 2, NOW);
        repository.claimBatch(p, a.batchId(), NOW);
        repository.claimBatch(p, b.batchId(), NOW);
        var outer = new TransactionTemplate(manager);
        outer.setIsolationLevel(
                org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        outer.executeWithoutResult(
                status -> {
                    assertThat(
                                    jdbc.queryForObject(
                                            "SELECT COUNT(*) FROM tensor_download_batch WHERE"
                                                    + " status='SUCCEEDED'",
                                            Long.class))
                            .isZero();
                    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                        executor.submit(() -> succeed(p, a.batchId(), Long.MAX_VALUE, 0, 0))
                                .get(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                    code(
                            ErrorCode.TASK_LIMIT_EXCEEDED,
                            () -> repository.succeedBatch(p, b.batchId(), 1, 0, 0, NOW));
                });
        assertThat(repository.counts(t.taskId()).running()).isEqualTo(1);
    }

    @Test
    void rangeEndpointsRequireExactlyEightDigitsAndInvalidBatchPayloadNeverPersists() {
        var in = rangeInput();
        var bad =
                new NewTask(
                        in.taskId(),
                        in.submissionId(),
                        in.datasetKey(),
                        in.mode(),
                        Map.of("from_date", "20231231Z", "to_date", "20240301"),
                        in.definitionHash(),
                        in.policySnapshot(),
                        in.activeRunId(),
                        NOW);
        var t = repository.insert(bad);
        var running =
                repository
                        .claimTask(t.taskId(), t.activeRunId(), NOW, NOW.plusSeconds(60))
                        .orElseThrow();
        assertThatThrownBy(
                        () ->
                                repository.savePlan(
                                        permit(running),
                                        List.of(batch("000001", "2024-02-29", "2024-02-29")),
                                        1,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        var valid = rangeRunning();
        var big =
                new NewBatch(
                        UUID.randomUUID(),
                        "000001",
                        new DateRange(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 29)),
                        Map.of("aa", "x".repeat(16376)));
        assertThatThrownBy(() -> repository.savePlan(permit(valid), List.of(big), 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.snapshot(valid.taskId()).counts().totalBatches()).isZero();
        assertThat(repository.findTask(valid.taskId()).orElseThrow().planReady()).isFalse();
    }
}
