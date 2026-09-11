package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.*;
import com.akkc.tensor.core.download.task.DownloadTaskService.*;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.*;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DownloadTaskServiceIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    static final Instant NOW = Instant.parse("2026-09-11T02:03:04.123Z");
    static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000018");
    static final DatasetKey KEY = DatasetKey.of(PluginId.of("local_source"), ApiName.of("prices"));
    final DownloadTaskJson json = new DownloadTaskJson();
    final LocalPlugin plugin = new LocalPlugin();
    DownloadTaskRepository repository;
    DownloadTaskService service;
    DownloadTaskQueryService query;

    @BeforeAll
    static void schema() throws Exception {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    "../tensor-app/src/main/resources/db/migration/V8__create_download_task_tables.sql"));
        }
    }

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_task");
        for (String id : jdbc.queryForList(
                "SELECT batch_id FROM tensor_download_batch ORDER BY LENGTH(batch_key) DESC", String.class)) {
            jdbc.update("DELETE FROM tensor_download_batch WHERE batch_id=?", id);
        }
        jdbc.update("DELETE FROM tensor_download_task");
        repository = new DownloadTaskRepository(jdbc, transactions, json);
        service = service(repository, true, Settings.defaults());
        query = new DownloadTaskQueryService(repository);
    }

    @AfterEach
    void neverCallsUpstreamOrAdapter() {
        assertThat(plugin.executions.get()).isZero();
    }

    @Test
    void singleAcceptanceCommitsBeforeReturningAndReplayPreservesAllFacts() {
        var request = single(UUID.randomUUID(), " 000001.sz ");
        var result = service.submit(request);
        var task = result.task();
        assertThat(result.created()).isTrue();
        assertThat(task.params()).containsExactlyInAnyOrderEntriesOf(Map.of("symbol", "000001.SZ", "kind", "basic"));
        assertThat(task.status()).isEqualTo(DownloadTask.Status.QUEUED);
        assertThat(task.planReady()).isFalse();
        assertThat(task.runGeneration()).isZero();
        assertThat(task.version()).isEqualTo(1);
        assertThat(task.activeRunId()).isEqualTo(RUN);
        assertThat(task.createdAt()).isEqualTo(NOW);
        assertThat(task.updatedAt()).isEqualTo(NOW);
        assertThat(task.queuedAt()).isEqualTo(NOW);
        assertThat(task.requestCount()).isZero();
        assertThat(task.requestHash()).matches("[0-9a-f]{64}");
        assertThat(task.definitionHash()).matches("[0-9a-f]{64}");
        assertThat(task.policySnapshot()).isEqualTo("{\"mode\":\"SINGLE\",\"schemaVersion\":1}");
        // JdbcTemplate gets a separate connection after submit has returned.
        assertThat(jdbc.queryForObject("SELECT status FROM tensor_download_task WHERE task_id=?",
                String.class, task.taskId().toString())).isEqualTo("QUEUED");
        assertThat(query.detail(task.taskId()).counts().totalBatches()).isZero();
        assertThat(query.batches(task.taskId(), null, 1, 20).total()).isZero();
        assertThat(service.submit(single(request.submissionId(), "000001.SZ")))
                .isEqualTo(new SubmissionResult(task, false));
        assertThat(query.findSubmission(request.submissionId())).contains(task);
        assertThat(query.tasks(null, 1, 20).items()).containsExactly(task);
        assertThat(plugin.combinations.get()).isZero();
    }

    @Test
    void normalizedReplayWinsOverDisabledPluginDisabledAcceptanceFullQueueAndTerminalState() throws Exception {
        var request = single(UUID.randomUUID(), "000001.SZ");
        var task = service.submit(request).task();
        var disabled = service(repository, false, new Settings(false, 1, 1));
        assertThat(disabled.submit(single(request.submissionId(), " 000001.sz ")).task()).isEqualTo(task);
        code(ErrorCode.SUBMISSION_CONFLICT,
                () -> disabled.submit(single(request.submissionId(), "000002.SZ")));
        var running = repository.claimTask(task.taskId(), RUN, NOW, NOW.plusSeconds(60)).orElseThrow();
        repository.finishTask(permit(running), DownloadTask.Status.FAILED, ErrorCode.SOURCE_AUTH_FAILED, NOW);
        var failed = query.detail(task.taskId()).task().orElseThrow();
        assertThat(failed.status()).isEqualTo(DownloadTask.Status.FAILED);
        assertThat(disabled.submit(single(request.submissionId(), " 000001.sz ")))
                .isEqualTo(new SubmissionResult(failed, false));
        assertThat(query.detail(task.taskId()).counts().totalBatches()).isZero();
        assertThat(query.tasks(null, 1, 20).total()).isEqualTo(1);
    }

    @Test
    void rangeAcceptanceUsesNonTushareNamesAndStoredPolicyEvenAfterMetadataRemoval() throws Exception {
        var request = range(UUID.randomUUID(), " 000001.sz ", "20280228", "20280301");
        var task = service.submit(request).task();
        assertThat(task.params()).containsEntry("symbol", "000001.SZ");
        assertThat(plugin.lastRange).isEqualTo(new DateRange(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1)));
        assertThat(plugin.combinations.get()).isEqualTo(1);
        assertThat(query.detail(task.taskId()).counts().totalBatches()).isZero();
        var removed = new DownloadTaskService(new PluginRegistry(List.of()), catalog(), new AdapterRegistry(List.of()),
                new ParameterValidator(), repository, json, Clock.fixed(NOW, ZoneOffset.UTC), RUN,
                new Settings(false, 1, 1));
        assertThat(removed.submit(range(request.submissionId(), "000001.sz", "20280228", "20280301")))
                .isEqualTo(new SubmissionResult(task, false));
        assertThat(plugin.combinations.get()).isEqualTo(1);
    }

    @Test
    void invalidParametersLimitsAndPureCombinationRejectionsLeaveNoTask() throws Exception {
        var limited = service(repository, true, new Settings(true, 100, 3));
        code(ErrorCode.TASK_LIMIT_EXCEEDED,
                () -> limited.submit(range(UUID.randomUUID(), "000001.SZ", "20280228", "20280302")));
        code(ErrorCode.PARAM_INVALID,
                () -> limited.submit(range(UUID.randomUUID(), "000001.SZ", "20280230", "20280301")));
        code(ErrorCode.PARAM_REQUIRED, () -> limited.submit(new Submission(UUID.randomUUID(), KEY,
                DownloadMode.RANGE, Map.of("symbol", "000001.SZ", "from", "20280228"))));
        plugin.rejectCombination = true;
        code(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE,
                () -> limited.submit(range(UUID.randomUUID(), "000001.SZ", "20280228", "20280301")));
        assertThat(query.tasks(null, 1, 20).total()).isZero();
        plugin.rejectCombination = false;
        assertThat(limited.submit(range(UUID.randomUUID(), "000001.SZ", "20280228", "20280301")).created()).isTrue();
    }

    @Test
    void concurrentCapacityOneAcceptsExactlyOneAndCountsOlderRunQueuedTasks() throws Exception {
        var limited = service(repository, true, new Settings(true, 1, 36600));
        var results = DownloadTaskRepositoryIT.race(
                () -> limited.submit(single(UUID.randomUUID(), "000001.SZ")),
                () -> limited.submit(single(UUID.randomUUID(), "000002.SZ")));
        assertThat(results.stream().filter(SubmissionResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(TensorException.class::isInstance)
                .map(v -> ((TensorException) v).code())).containsExactly(ErrorCode.TASK_QUEUE_FULL);
        assertThat(repository.queuedCount()).isEqualTo(1);
        jdbc.update("UPDATE tensor_download_task SET active_run_id=?", UUID.randomUUID().toString());
        code(ErrorCode.TASK_QUEUE_FULL, () -> limited.submit(single(UUID.randomUUID(), "000003.SZ")));
        assertThat(query.tasks(null, 1, 20).total()).isEqualTo(1);
    }

    @Test
    void databaseUniqueCollisionReturnsExistingEquivalentRequest() throws Exception {
        duplicateRace(false);
    }

    @Test
    void databaseUniqueCollisionRejectsDifferentRequest() throws Exception {
        duplicateRace(true);
    }

    private void duplicateRace(boolean different) throws Exception {
        var gate = new DownloadTaskRepositoryIT.GatedDataSource(jdbc.getDataSource(),
                "SELECT * FROM tensor_download_task WHERE submission_id");
        var gatedRepository = new DownloadTaskRepository(new JdbcTemplate(gate), new DataSourceTransactionManager(gate), json);
        var gatedService = service(gatedRepository, true, Settings.defaults());
        var id = UUID.randomUUID();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> gatedService.submit(single(id, " 000001.sz ")));
            DownloadTask winner;
            try {
                assertThat(gate.entered.await(5, TimeUnit.SECONDS)).isTrue();
                winner = service.submit(single(id, different ? "000002.SZ" : "000001.SZ")).task();
            } finally {
                gate.release.countDown();
            }
            if (different) {
                assertThatThrownBy(() -> pending.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .satisfies(error -> assertThat(error.getCause()).isInstanceOf(TensorException.class)
                                .extracting(cause -> ((TensorException) cause).code())
                                .isEqualTo(ErrorCode.SUBMISSION_CONFLICT));
            } else {
                assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo(new SubmissionResult(winner, false));
            }
            assertThat(query.findSubmission(id)).contains(winner);
            assertThat(repository.queuedCount()).isEqualTo(1);
        }
    }

    @Test
    void failedDatabaseInsertCannotReportAcceptanceAndLockIsReleased() {
        jdbc.execute("CREATE TRIGGER reject_task BEFORE INSERT ON tensor_download_task FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-database-value'");
        code(ErrorCode.PERSISTENCE_FAILED, () -> service.submit(single(UUID.randomUUID(), "000001.SZ")));
        assertThat(query.tasks(null, 1, 20).total()).isZero();
        jdbc.execute("DROP TRIGGER reject_task");
        assertThat(service.submit(single(UUID.randomUUID(), "000001.SZ")).created()).isTrue();
    }

    @Test
    void allPublicUsesRejectAnOuterTransactionBeforeReturningOrWriting() {
        var task = service.submit(single(UUID.randomUUID(), "000001.SZ")).task();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (org.assertj.core.api.ThrowableAssert.ThrowingCallable action : List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                    () -> service.submit(single(UUID.randomUUID(), "000002.SZ")),
                    () -> service.capabilities(KEY), () -> service.validateReplay(task),
                    () -> query.tasks(null, 1, 20), () -> query.findSubmission(task.submissionId()),
                    () -> query.detail(task.taskId()), () -> query.batches(task.taskId(), null, 1, 20))) {
                assertThatThrownBy(action).isInstanceOf(IllegalStateException.class);
            }
            status.setRollbackOnly();
        });
        assertThat(query.tasks(null, 1, 20).items()).containsExactly(task);
    }

    @Test
    void queriesPreservePaginationFiltersAndStableOrderWithoutPluginAccess() {
        var all = new ArrayList<DownloadTask>();
        for (int i = 0; i < 21; i++) all.add(service.submit(single(UUID.randomUUID(), "000001.SZ")).task());
        var sorted = all.stream().sorted(Comparator.comparing(t -> t.taskId().toString(), Comparator.reverseOrder())).toList();
        assertThat(query.tasks(null, 1, 20).items()).isEqualTo(sorted.subList(0, 20));
        assertThat(query.tasks(null, 2, 20).items()).containsExactly(sorted.getLast());
        assertThat(query.tasks(null, 99, 20).items()).isEmpty();
        for (int size : List.of(50, 100)) assertThat(query.tasks(null, 1, size).items()).isEqualTo(sorted);
        var target = all.getFirst();
        assertThat(query.tasks(new TaskFilter("local_source", "prices", DownloadTask.Status.QUEUED,
                target.submissionId()), 1, 20).items()).containsExactly(target);
        assertThat(query.tasks(new TaskFilter("other_source", null, null, null), 1, 20).total()).isZero();
        code(ErrorCode.PARAM_INVALID, () -> query.tasks(null, 0, 20));
        code(ErrorCode.PARAM_INVALID, () -> query.tasks(null, 1, 10));
        code(ErrorCode.PARAM_INVALID, () -> query.tasks(new TaskFilter("bad-source", null, null, null), 1, 20));
        code(ErrorCode.PARAM_INVALID, () -> query.tasks(new TaskFilter(null, "bad-api", null, null), 1, 20));
        code(ErrorCode.PARAM_INVALID, () -> query.batches(target.taskId(), null, 0, 20));
        code(ErrorCode.TASK_NOT_FOUND, () -> query.detail(UUID.randomUUID()));
        code(ErrorCode.TASK_NOT_FOUND, () -> query.batches(UUID.randomUUID(), null, 1, 20));
    }

    @Test
    void batchesDefaultToLeavesAndDetailKeepsSnapshotDuringSplit() throws Exception {
        var task = service.submit(range(UUID.randomUUID(), "000001.SZ", "20280228", "20280301")).task();
        var running = repository.claimTask(task.taskId(), RUN, NOW, NOW.plusSeconds(60)).orElseThrow();
        var permit = permit(running);
        var root = batch("000001", "2028-02-28", "2028-03-01");
        repository.savePlan(permit, List.of(root), 3, NOW);
        repository.claimBatch(permit, root.batchId(), NOW);
        var gate = new DownloadTaskRepositoryIT.GatedDataSource(jdbc.getDataSource(), "SELECT * FROM tensor_download_task");
        var gatedQuery = new DownloadTaskQueryService(new DownloadTaskRepository(new JdbcTemplate(gate),
                new DataSourceTransactionManager(gate), json));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var snapshot = executor.submit(() -> gatedQuery.detail(task.taskId()));
            try {
                assertThat(gate.entered.await(5, TimeUnit.SECONDS)).isTrue();
                repository.split(permit, root.batchId(), batch("000001/0", "2028-02-28", "2028-02-29"),
                        batch("000001/1", "2028-03-01", "2028-03-01"), 3, NOW.plusSeconds(1));
            } finally {
                gate.release.countDown();
            }
            var before = snapshot.get(10, TimeUnit.SECONDS);
            assertThat(before.task().orElseThrow().updatedAt()).isEqualTo(NOW);
            assertThat(before.counts()).isEqualTo(new Counts(1, 0, 1, 0, 0, 0, 0, 0, 0));
        }
        assertThat(query.batches(task.taskId(), null, 1, 20).items()).extracting(DownloadBatch::batchKey)
                .containsExactly("000001/0", "000001/1");
        assertThat(query.batches(task.taskId(), new BatchFilter(null, true), 1, 50).total()).isEqualTo(3);
        assertThat(query.batches(task.taskId(), new BatchFilter(DownloadBatch.Status.SPLIT, true), 1, 100).items())
                .extracting(DownloadBatch::batchKey).containsExactly("000001");
        assertThat(query.batches(task.taskId(), new BatchFilter(DownloadBatch.Status.SPLIT, false), 1, 20).items()).isEmpty();
        assertThat(query.detail(task.taskId()).counts()).isEqualTo(new Counts(2, 2, 0, 0, 0, 1, 0, 0, 0));
    }

    private DownloadTaskService service(DownloadTaskRepository repo, boolean available, Settings settings) throws Exception {
        plugin.available = available;
        return new DownloadTaskService(new PluginRegistry(List.of(plugin)), catalog(),
                new AdapterRegistry(List.of(plugin)), new ParameterValidator(), repo, json,
                Clock.fixed(NOW, ZoneOffset.UTC), RUN, settings);
    }

    private DatasetCatalog catalog() throws Exception {
        var constructor = DatasetCatalog.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(List.of(plugin.definition()));
    }

    private static Submission single(UUID id, String symbol) {
        return new Submission(id, KEY, DownloadMode.SINGLE, Map.of("symbol", symbol));
    }

    private static Submission range(UUID id, String symbol, String from, String to) {
        return new Submission(id, KEY, DownloadMode.RANGE, Map.of("symbol", symbol, "from", from, "to", to));
    }

    private static ExecutionPermit permit(DownloadTask task) {
        return new ExecutionPermit(task.taskId(), task.activeRunId(), task.runGeneration());
    }

    private static NewBatch batch(String key, String from, String to) {
        return new NewBatch(UUID.randomUUID(), key, new DateRange(LocalDate.parse(from), LocalDate.parse(to)),
                Map.of("symbol", "000001.SZ"));
    }

    private static void code(ErrorCode expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(TensorException.class).hasNoCause()
                .hasMessageNotContaining("private-database-value")
                .extracting(error -> ((TensorException) error).code()).isEqualTo(expected);
    }

    static class LocalPlugin implements BatchDownloadSupport, DatasetAdapter {
        final AtomicInteger executions = new AtomicInteger();
        final AtomicInteger combinations = new AtomicInteger();
        boolean available = true;
        boolean rejectCombination;
        DateRange lastRange;
        final ParameterDescriptor symbol = new ParameterDescriptor("symbol", "Symbol", null, ParameterType.TS_CODE,
                true, null, List.of(), null, null);
        final ApiDescriptor single = new ApiDescriptor(KEY.apiName(), "Prices", "prices", QueryMode.snapshot,
                List.of(symbol, new ParameterDescriptor("kind", "Kind", null, ParameterType.TEXT,
                        false, "basic", List.of(), null, null)));

        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor(KEY.pluginId(), "Local source", "Controlled source", available, true,
                    available, available ? null : "Disabled", List.of(single), List.of(KEY));
        }
        @Override public PluginReadiness readiness() {
            return new PluginReadiness(available, true, available, available ? null : "Disabled");
        }
        @Override public DatasetKey datasetKey() { return KEY; }
        @Override public DatasetDefinition definition() {
            return new DatasetDefinition(KEY, "Prices", "prices", QueryMode.snapshot, single.parameters(), TableName.from(KEY),
                    List.of(new ColumnDefinition("symbol", "Symbol", LogicalType.STRING, false, 0, 32, null, null, List.of(), false)),
                    new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("symbol")), List.of(), null, 100);
        }
        @Override public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName api) {
            return Optional.of(new BatchDownloadDescriptor(List.of(symbol, endpoint("from", "to"), endpoint("to", "from")),
                    "from", "to", BatchDownloadDescriptor.DateAxis.TRADE_DATE, "Trade date",
                    BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true, BatchDownloadDescriptor.Availability.AVAILABLE,
                    null, "test-v1", new BatchDownloadDescriptor.CompletenessRule(
                            BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE, null, "Controlled test source")));
        }
        @Override public Map<String, Object> sourceParameters(ApiName api, Map<String, Object> params, DateRange range) {
            combinations.incrementAndGet();
            if (rejectCombination) throw new TaskException(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
            lastRange = range;
            return params;
        }
        @Override public DownloadEnvelope download(ApiName api, Map<String, Object> params) { throw forbidden(); }
        @Override public DownloadEnvelope downloadBatch(ApiName api, Map<String, Object> params, BatchCallContext context) { throw forbidden(); }
        @Override public List<DateRange> plan(ApiName api, Map<String, Object> params, BatchCallContext context) { throw forbidden(); }
        @Override public BatchAssessment assess(ApiName api, DateRange range, DownloadEnvelope envelope) { throw forbidden(); }
        @Override public AdaptedBatch adapt(DownloadEnvelope envelope, Instant time) { throw forbidden(); }
        private AssertionError forbidden() {
            executions.incrementAndGet();
            return new AssertionError("Submission must not execute upstream or adaptation");
        }
        private static ParameterDescriptor endpoint(String name, String related) {
            return new ParameterDescriptor(name, name, null, ParameterType.DATE_RANGE_MEMBER,
                    true, null, List.of(), null, related);
        }
    }
}
