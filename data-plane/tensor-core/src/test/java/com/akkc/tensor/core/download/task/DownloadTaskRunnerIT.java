package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.BatchFilter;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.ExecutionPermit;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.NewBatch;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.RequeueMode;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.persistence.ExistingKeyRepository;
import com.akkc.tensor.core.persistence.GenericUpsertRepository;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.BatchDownloadSupport;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.batch.BatchAssessment;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DownloadTaskRunnerIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");

    static final Instant NOW = Instant.parse("2026-09-11T02:03:04.123Z");
    static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000107");
    static final DatasetKey KEY = DatasetKey.of(PluginId.of("runner_test"), ApiName.of("prices"));
    static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;

    final DownloadTaskJson json = new DownloadTaskJson();
    final MutableClock clock = new MutableClock();
    DownloadTaskRepository repository;
    ControlledSource source;
    GenericDatasetAdapter adapter;
    DownloadTaskService tasks;
    BatchCommitService commits;

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
                CREATE TABLE runner_test__prices (
                  symbol VARCHAR(32) NOT NULL, observed_on DATE NOT NULL, amount DECIMAL(18,2) NOT NULL,
                  source_plugin VARCHAR(64) NOT NULL, source_api VARCHAR(64) NOT NULL,
                  ingested_at TIMESTAMP(3) NOT NULL, PRIMARY KEY (symbol, observed_on)) ENGINE=InnoDB
                """);
    }

    @BeforeEach
    void reset() throws Exception {
        resetDatabase();
        clock.now = NOW;
        source = new ControlledSource();
        adapter = new GenericDatasetAdapter(source.definition(), new ValueConverter(), new FingerprintKeyCodec());
        repository = new DownloadTaskRepository(jdbc, transactions, json);
        tasks = taskService(repository, RUN);
        commits = commitService(repository);
    }

    @Test
    void t03TaskPlansThreeBatchesAndContinuesAfterTheMiddleNetworkFailure() throws Exception {
        source.mode = BatchDownloadDescriptor.PlanningMode.TRADING_DAYS;
        source.plan = List.of(day("2026-09-10"), day("2026-09-11"), day("2026-09-12"));
        source.failureOn.put("20260911", ErrorCode.SOURCE_NETWORK_ERROR);
        DownloadTask accepted = submitRange(RUN, "20260910", "20260912");

        var result = runner(repository, commits, RUN, settings(20, 20, 20)).runNext(() -> false);

        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(result.taskId()).isEqualTo(accepted.taskId());
        DownloadTask stored = task(accepted.taskId());
        assertThat(stored.status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
        assertThat(stored.lastError().code()).isEqualTo(ErrorCode.SOURCE_NETWORK_ERROR);
        assertThat(stored.requestCount()).isEqualTo(3);
        assertThat(repository.counts(stored.taskId()))
                .isEqualTo(new DownloadTaskRepository.Counts(3, 0, 0, 2, 1, 0, 2, 2, 0));
        assertThat(allBatches(stored.taskId())).extracting(DownloadBatch::status)
                .containsExactly(DownloadBatch.Status.SUCCEEDED, DownloadBatch.Status.FAILED,
                        DownloadBatch.Status.SUCCEEDED);
        assertThat(allBatches(stored.taskId())).extracting(DownloadBatch::attemptCount)
                .containsExactly(1, 1, 1);
        assertThat(priceFacts()).containsExactly("AA-2026-09-10=10.00", "AA-2026-09-12=10.00");
    }

    @Test
    void nativeSplitPersistsOnlyChildrenAndNodeFailureCreatesNoHalfTree() throws Exception {
        source.splitMultiDay = true;
        DownloadTask split = submitRange(RUN, "20260910", "20260911");
        var result = runner(repository, commits, RUN, settings(3, 20, 20)).runNext(() -> false);

        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(task(split.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(allBatches(split.taskId())).extracting(DownloadBatch::batchKey, DownloadBatch::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("000001", DownloadBatch.Status.SPLIT),
                        org.assertj.core.groups.Tuple.tuple("000001/0", DownloadBatch.Status.SUCCEEDED),
                        org.assertj.core.groups.Tuple.tuple("000001/1", DownloadBatch.Status.SUCCEEDED));
        assertThat(allBatches(split.taskId()).getFirst().sourceRows()).isZero();
        assertThat(repository.counts(split.taskId()).sourceRows()).isEqualTo(2);
        assertThat(priceFacts()).containsExactly("AA-2026-09-10=10.00", "AA-2026-09-11=10.00");
        assertThat(source.downloads.get()).isEqualTo(3);

        resetHarness();
        source.splitMultiDay = true;
        DownloadTask limited = submitRange(RUN, "20260910", "20260911");
        var refused = runner(repository, commits, RUN, settings(2, 20, 20)).runNext(() -> false);
        assertThat(refused.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(refused.error()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED);
        assertThat(task(limited.taskId()).status()).isEqualTo(DownloadTask.Status.FAILED);
        assertThat(allBatches(limited.taskId())).singleElement().satisfies(batch -> {
            assertThat(batch.batchKey()).isEqualTo("000001");
            assertThat(batch.status()).isEqualTo(DownloadBatch.Status.FAILED);
        });
        assertThat(repository.counts(limited.taskId()).splitBatches()).isZero();
        assertThat(priceFacts()).isEmpty();
    }

    @Test
    void emptyBatchesAndEmptyTradingPlanFinishButInvalidPlanCannotLookSuccessful() throws Exception {
        source.mode = BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        source.empty = true;
        DownloadTask emptyBatches = submitRange(RUN, "20260910", "20260911");
        runner(repository, commits, RUN, settings(20, 20, 20)).runNext(() -> false);
        assertThat(task(emptyBatches.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(repository.counts(emptyBatches.taskId()))
                .isEqualTo(new DownloadTaskRepository.Counts(2, 0, 0, 2, 0, 0, 0, 0, 0));

        resetHarness();
        source.mode = BatchDownloadDescriptor.PlanningMode.TRADING_DAYS;
        source.plan = List.of();
        DownloadTask noTradingDays = submitRange(RUN, "20260910", "20260911");
        runner(repository, commits, RUN, settings(20, 20, 20)).runNext(() -> false);
        assertThat(task(noTradingDays.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(task(noTradingDays.taskId()).planReady()).isTrue();
        assertThat(allBatches(noTradingDays.taskId())).isEmpty();

        resetHarness();
        source.mode = BatchDownloadDescriptor.PlanningMode.TRADING_DAYS;
        source.plan = List.of(day("2026-09-10"), day("2026-09-10"));
        DownloadTask invalid = submitRange(RUN, "20260910", "20260911");
        var result = runner(repository, commits, RUN, settings(20, 20, 20)).runNext(() -> false);
        assertThat(result.error()).isEqualTo(ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
        assertThat(task(invalid.taskId()).status()).isEqualTo(DownloadTask.Status.FAILED);
        assertThat(task(invalid.taskId()).planReady()).isFalse();
        assertThat(allBatches(invalid.taskId())).isEmpty();
        assertThat(priceFacts()).isEmpty();
    }

    @Test
    void securitiesRollbackBeforeFailureAndAnUnrecordableFailureNeedsRecovery() throws Exception {
        source.mode = BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        source.symbolOn.put("20260910", "REJECT");
        DownloadTask recoverable = submitRange(RUN, "20260910", "20260911");
        rejectPrice("REJECT");
        var finished = runner(repository, commits, RUN, settings(20, 20, 20)).runNext(() -> false);
        assertThat(finished.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(task(recoverable.taskId()).status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
        assertThat(priceFacts()).containsExactly("AA-2026-09-11=10.00");
        assertThat(repository.counts(recoverable.taskId()))
                .isEqualTo(new DownloadTaskRepository.Counts(2, 0, 0, 1, 1, 0, 1, 1, 0));

        resetHarness();
        source.mode = BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        source.symbolOn.put("20260910", "REJECT");
        DownloadTask uncertain = submitRange(RUN, "20260910", "20260911");
        rejectPrice("REJECT");
        jdbc.execute("""
                CREATE TRIGGER reject_runner_failure BEFORE UPDATE ON tensor_download_batch FOR EACH ROW
                BEGIN IF NEW.status='FAILED' THEN SIGNAL SQLSTATE '45000'
                  SET MESSAGE_TEXT='private-failure-detail'; END IF; END
                """);
        var recovery = runner(repository, commits, RUN, settings(20, 20, 20)).runNext(() -> false);
        assertThat(recovery.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(recovery.error()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
        assertThat(source.downloads.get()).isEqualTo(1);
        assertThat(priceFacts()).isEmpty();
        assertThat(repository.counts(uncertain.taskId()))
                .isEqualTo(new DownloadTaskRepository.Counts(2, 1, 1, 0, 0, 0, 0, 0, 0));

        jdbc.execute("DROP TRIGGER reject_runner_failure");
        jdbc.execute("DROP TRIGGER reject_runner_price");
        DownloadTask running = task(uncertain.taskId());
        DownloadTask recovered = repository.recoverStoppedTask(
                running.taskId(), running.activeRunId(), running.runGeneration(), running.version(), clock.instant());
        assertThat(recovered.status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(allBatches(recovered.taskId())).extracting(DownloadBatch::status)
                .containsExactly(DownloadBatch.Status.FAILED, DownloadBatch.Status.PENDING);
        assertThat(allBatches(recovered.taskId()).getFirst().error().code())
                .isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
    }

    @Test
    void committedPlanSplitDataAndFinishAreNeverRetriedAfterAcknowledgementErrors() throws Exception {
        DownloadTask planTask = submitRange(RUN, "20260910", "20260910");
        AcknowledgementFailingTransactionManager planTransactions = acknowledgementFailure();
        DownloadTaskRepository planRepo = spy(new DownloadTaskRepository(jdbc, planTransactions, json));
        doAnswer(invocation -> {
            planTransactions.arm();
            return invocation.callRealMethod();
        }).when(planRepo).savePlan(any(), anyList(), anyInt(), any());
        var planResult = runner(planRepo, commitService(planRepo, planTransactions), RUN, settings(20, 20, 20))
                .runNext(() -> false);
        assertThat(planResult.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(task(planTask.taskId()).planReady()).isTrue();
        assertThat(allBatches(planTask.taskId())).hasSize(1);
        assertThat(source.downloads.get()).isZero();

        resetHarness();
        source.splitMultiDay = true;
        DownloadTask splitTask = submitRange(RUN, "20260910", "20260911");
        AcknowledgementFailingTransactionManager splitTransactions = acknowledgementFailure();
        DownloadTaskRepository splitRepo = spy(new DownloadTaskRepository(jdbc, splitTransactions, json));
        doAnswer(invocation -> {
            splitTransactions.arm();
            return invocation.callRealMethod();
        }).when(splitRepo).split(any(), any(), any(), any(), anyInt(), any());
        var splitResult = runner(splitRepo, commitService(splitRepo, splitTransactions), RUN, settings(3, 20, 20))
                .runNext(() -> false);
        assertThat(splitResult.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(allBatches(splitTask.taskId())).extracting(DownloadBatch::status)
                .containsExactly(DownloadBatch.Status.SPLIT, DownloadBatch.Status.PENDING,
                        DownloadBatch.Status.PENDING);
        assertThat(priceFacts()).isEmpty();

        resetHarness();
        DownloadTask commitTask = submitRange(RUN, "20260910", "20260910");
        AcknowledgementFailingTransactionManager commitTransactions = acknowledgementFailure();
        DownloadTaskRepository commitRepo = new DownloadTaskRepository(jdbc, commitTransactions, json);
        BatchCommitService uncertainCommit = spy(commitService(commitRepo, commitTransactions));
        doAnswer(invocation -> {
            commitTransactions.arm();
            return invocation.callRealMethod();
        }).when(uncertainCommit).commit(any(), any(), any(), anyLong(), any());
        var commitResult = runner(commitRepo, uncertainCommit, RUN, settings(20, 20, 20)).runNext(() -> false);
        assertThat(commitResult.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(priceFacts()).containsExactly("AA-2026-09-10=10.00");
        assertThat(repository.counts(commitTask.taskId()).succeeded()).isOne();
        assertThat(repository.counts(commitTask.taskId()).failed()).isZero();

        resetHarness();
        DownloadTask finishTask = submitRange(RUN, "20260910", "20260910");
        AcknowledgementFailingTransactionManager finishTransactions = acknowledgementFailure();
        DownloadTaskRepository finishRepo = spy(new DownloadTaskRepository(jdbc, finishTransactions, json));
        doAnswer(invocation -> {
            finishTransactions.arm();
            return invocation.callRealMethod();
        }).when(finishRepo).finishTask(any(), any(), nullable(ErrorCode.class), any());
        var finishResult = runner(finishRepo, commitService(finishRepo, finishTransactions), RUN,
                settings(20, 20, 20)).runNext(() -> false);
        assertThat(finishResult.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(task(finishTask.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        int requests = source.downloads.get();
        assertThat(runner(repository, commits, RUN, settings(20, 20, 20)).runNext(() -> false).disposition())
                .isEqualTo(DownloadTaskRunner.Disposition.IDLE);
        assertThat(source.downloads.get()).isEqualTo(requests);
    }

    @Test
    void realRequestAndSourceBudgetsSurviveARequeueAndNodeLimitIsAtomic() throws Exception {
        source.mode = BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        DownloadTask task = submitRange(RUN, "20260910", "20260911");
        var first = runner(repository, commits, RUN, settings(20, 20, 1)).runNext(() -> false);
        assertThat(first.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(first.error()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED);
        DownloadTask partial = task(task.taskId());
        assertThat(partial.status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
        assertThat(partial.requestCount()).isEqualTo(2);
        assertThat(partial.runRequestCount()).isEqualTo(2);
        assertThat(repository.counts(partial.taskId()).sourceRows()).isOne();
        assertThat(priceFacts()).containsExactly("AA-2026-09-10=10.00");

        source.emptyOn.add("20260911");
        UUID nextRun = UUID.randomUUID();
        repository.requeue(partial.taskId(), partial.version(), nextRun, RequeueMode.RETRY, clock.instant());
        var second = runner(repository, commits, nextRun, settings(20, 20, 1)).runNext(() -> false);
        assertThat(second.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        DownloadTask succeeded = task(partial.taskId());
        assertThat(succeeded.status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(succeeded.requestCount()).isEqualTo(3);
        assertThat(succeeded.runRequestCount()).isEqualTo(1);
        assertThat(repository.counts(succeeded.taskId()).sourceRows()).isOne();
        assertThat(priceFacts()).containsExactly("AA-2026-09-10=10.00");
    }

    @Test
    void queryReservationAndClaimReceiptFailuresStopWithTheAvailableIdentity() throws Exception {
        DownloadTaskRepository queryRepo = spy(repository);
        doThrow(classified(ErrorCode.QUERY_FAILED)).when(queryRepo).queuedTasks(any(), anyInt());
        var query = runner(queryRepo, commitService(queryRepo), RUN, settings(20, 20, 20)).runNext(() -> false);
        assertThat(query.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(query.taskId()).isNull();
        assertThat(query.permit()).isNull();
        assertThat(source.downloads.get()).isZero();

        DownloadTask claimTask = submitRange(RUN, "20260910", "20260910");
        DownloadTaskRepository claimRepo = spy(repository);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw classified(ErrorCode.PERSISTENCE_FAILED);
        }).when(claimRepo).claimTask(any(), any(), any(), any());
        var claim = runner(claimRepo, commitService(claimRepo), RUN, settings(20, 20, 20)).runNext(() -> false);
        assertThat(claim.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(claim.taskId()).isEqualTo(claimTask.taskId());
        assertThat(claim.permit()).isNull();
        assertThat(task(claimTask.taskId()).status()).isEqualTo(DownloadTask.Status.RUNNING);
        assertThat(source.downloads.get()).isZero();

        resetHarness();
        DownloadTask reserveTask = submitRange(RUN, "20260910", "20260910");
        DownloadTaskRepository reserveRepo = spy(repository);
        doThrow(classified(ErrorCode.PERSISTENCE_FAILED))
                .when(reserveRepo).reserveRequest(any(), anyLong(), any());
        var reserve = runner(reserveRepo, commitService(reserveRepo), RUN, settings(20, 20, 20))
                .runNext(() -> false);
        assertThat(reserve.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(reserve.taskId()).isEqualTo(reserveTask.taskId());
        assertThat(reserve.permit()).isNotNull();
        assertThat(source.downloads.get()).isZero();
        assertThat(repository.counts(reserveTask.taskId()).running()).isOne();
    }

    @Test
    void coordinatorKeepsOwnershipUntilTheInterruptedPluginActuallyReturnsThenRecovers() throws Exception {
        source.entered = new CountDownLatch(1);
        source.release = new CountDownLatch(1);
        DownloadTask first = submitRange(RUN, "20260910", "20260911");
        DownloadTaskRunner runner = runner(repository, commits, RUN, settings(20, 20, 20));
        AtomicBoolean lease = new AtomicBoolean(true);
        var executor = Executors.newSingleThreadExecutor();
        Future<?> running = executor.submit(() -> {
            try {
                runner.runNext(() -> false);
            } finally {
                lease.set(false);
            }
        });
        try {
            assertThat(source.entered.await(5, TimeUnit.SECONDS)).isTrue();
            DownloadTask second = submitRange(RUN, "20260912", "20260912");
            assertThat(running.cancel(true)).isTrue();
            assertThat(running.isCancelled()).isTrue();
            assertThat(lease).isTrue();
            assertThatThrownBy(() -> runner.runNext(() -> false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Download runner is already active");
            assertThat(task(second.taskId()).status()).isEqualTo(DownloadTask.Status.QUEUED);
            assertThat(source.downloads.get()).isEqualTo(1);
        } finally {
            source.release.countDown();
        }
        assertThat(waitUntil(() -> !lease.get())).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(priceFacts()).isEmpty();

        DownloadTask stopped = task(first.taskId());
        ExecutionPermit oldPermit = permit(stopped);
        List<DownloadBatch> beforeRecovery = allBatches(stopped.taskId());
        assertThat(beforeRecovery).singleElement().extracting(DownloadBatch::status)
                .isEqualTo(DownloadBatch.Status.RUNNING);
        DownloadTask recovered = repository.recoverStoppedTask(stopped.taskId(), stopped.activeRunId(),
                stopped.runGeneration(), stopped.version(), clock.instant());
        UUID nextRun = UUID.randomUUID();
        repository.requeue(recovered.taskId(), recovered.version(), nextRun, RequeueMode.RESUME, clock.instant());
        DownloadTask claimed = repository.claimTask(recovered.taskId(), nextRun, clock.instant(),
                clock.instant().plusSeconds(60)).orElseThrow();
        ExecutionPermit currentPermit = permit(claimed);
        DownloadBatch currentBatch = repository.claimBatch(currentPermit,
                allBatches(claimed.taskId()).getFirst().batchId(), clock.instant()).orElseThrow();

        assertCode(ErrorCode.TASK_STATE_CONFLICT,
                () -> commits.commit(oldPermit, currentBatch.batchId(), emptyAdapted(), 0, () -> false));
        assertCode(ErrorCode.TASK_STATE_CONFLICT,
                () -> repository.split(oldPermit, currentBatch.batchId(),
                        child(currentBatch, "/0", currentBatch.range().start(), currentBatch.range().start()),
                        child(currentBatch, "/1", currentBatch.range().end(), currentBatch.range().end()),
                        20, clock.instant()));
        assertThat(task(claimed.taskId()).runGeneration()).isEqualTo(oldPermit.runGeneration() + 1);
        assertThat(priceFacts()).isEmpty();
    }

    private DownloadTaskRunner runner(DownloadTaskRepository repo, BatchCommitService commit,
            UUID activeRunId, DownloadTaskRunner.Settings settings) throws Exception {
        return new DownloadTaskRunner(taskService(repo, activeRunId), repo, commit, json, clock, activeRunId, settings);
    }

    private DownloadTaskService taskService(DownloadTaskRepository repo, UUID activeRunId) throws Exception {
        return new DownloadTaskService(new PluginRegistry(List.of(source)), catalog(),
                new AdapterRegistry(List.of(adapter)), new ParameterValidator(), repo, json, clock, activeRunId,
                DownloadTaskService.Settings.defaults());
    }

    private BatchCommitService commitService(DownloadTaskRepository repo) throws Exception {
        return commitService(repo, transactions);
    }

    private BatchCommitService commitService(
            DownloadTaskRepository repo, DataSourceTransactionManager transactionManager) throws Exception {
        var persistence = new PersistenceService(catalog(), new DatasetLockManager(),
                new ExistingKeyRepository(jdbc), new GenericUpsertRepository(jdbc), transactionManager);
        return new BatchCommitService(persistence, repo, clock);
    }

    private static AcknowledgementFailingTransactionManager acknowledgementFailure() {
        return new AcknowledgementFailingTransactionManager(dataSource);
    }

    private DatasetCatalog catalog() throws Exception {
        var constructor = DatasetCatalog.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(List.of(source.definition()));
    }

    private DownloadTask submitRange(UUID run, String from, String to) throws Exception {
        if (!run.equals(RUN)) tasks = taskService(repository, run);
        return tasks.submit(new DownloadTaskService.Submission(UUID.randomUUID(), KEY, DownloadMode.RANGE,
                Map.of("symbol", "AA", "from", from, "to", to))).task();
    }

    private void resetHarness() throws Exception {
        resetDatabase();
        clock.now = NOW;
        source = new ControlledSource();
        adapter = new GenericDatasetAdapter(source.definition(), new ValueConverter(), new FingerprintKeyCodec());
        repository = new DownloadTaskRepository(jdbc, transactions, json);
        tasks = taskService(repository, RUN);
        commits = commitService(repository);
    }

    private static void resetDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_runner_price");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_runner_failure");
        for (String batchId : jdbc.queryForList(
                "SELECT batch_id FROM tensor_download_batch ORDER BY LENGTH(batch_key) DESC", String.class)) {
            jdbc.update("DELETE FROM tensor_download_batch WHERE batch_id=?", batchId);
        }
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM runner_test__prices");
    }

    private static void rejectPrice(String symbol) {
        jdbc.execute("""
                CREATE TRIGGER reject_runner_price BEFORE INSERT ON runner_test__prices FOR EACH ROW
                BEGIN IF NEW.symbol='%s' THEN SIGNAL SQLSTATE '45000'
                  SET MESSAGE_TEXT='private-price-detail'; END IF; END
                """.formatted(symbol));
    }

    private DownloadTask task(UUID taskId) {
        return repository.findTask(taskId).orElseThrow();
    }

    private List<DownloadBatch> allBatches(UUID taskId) {
        return repository.batches(taskId, new BatchFilter(null, true), 1, 100).items();
    }

    private static DateRange day(String date) {
        LocalDate value = LocalDate.parse(date);
        return new DateRange(value, value);
    }

    private static DownloadTaskRunner.Settings settings(int nodes, long requests, long rows) {
        return new DownloadTaskRunner.Settings(true, 36600, nodes, requests, Duration.ofMinutes(30), rows);
    }

    private static ExecutionPermit permit(DownloadTask task) {
        return new ExecutionPermit(task.taskId(), task.activeRunId(), task.runGeneration());
    }

    private static NewBatch child(DownloadBatch parent, String suffix, LocalDate from, LocalDate to) {
        return new NewBatch(UUID.randomUUID(), parent.batchKey() + suffix, new DateRange(from, to),
                Map.of("symbol", "AA", "from", BASIC.format(from), "to", BASIC.format(to)));
    }

    private com.akkc.tensor.plugin.api.download.AdaptedBatch emptyAdapted() {
        return adapter.adapt(source.envelope(Map.of("symbol", "AA", "from", "20260910", "to", "20260910"),
                List.of()), clock.instant());
    }

    private static TensorException classified(ErrorCode code) {
        return new TensorException(code, new DownloadTaskRepository.StoredError(code).message()) {};
    }

    private static void assertCode(ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(TensorException.class).hasNoCause()
                .extracting(error -> ((TensorException) error).code()).isEqualTo(code);
    }

    private static boolean waitUntil(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        return condition.getAsBoolean();
    }

    private static List<String> priceFacts() {
        return jdbc.query("SELECT symbol,observed_on,amount FROM runner_test__prices ORDER BY observed_on,symbol",
                (row, number) -> row.getString(1) + "-" + row.getObject(2, LocalDate.class) + "="
                        + row.getBigDecimal(3).setScale(2));
    }

    static final class ControlledSource implements BatchDownloadSupport {
        final AtomicInteger downloads = new AtomicInteger();
        final Map<String, ErrorCode> failureOn = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<String, String> symbolOn = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Set<String> emptyOn = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final List<Map<String, Object>> requested = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile BatchDownloadDescriptor.PlanningMode mode = BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE;
        volatile List<DateRange> plan;
        volatile boolean splitMultiDay;
        volatile boolean empty;
        volatile boolean available = true;
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        final ParameterDescriptor symbol = new ParameterDescriptor("symbol", "Symbol", null, ParameterType.TEXT,
                true, null, List.of(), "[A-Z]{1,8}", null);
        final ApiDescriptor api = new ApiDescriptor(KEY.apiName(), "Prices", "test", QueryMode.snapshot,
                List.of(symbol));

        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor(KEY.pluginId(), "Runner test", "Controlled runner source",
                    available, true, available, available ? null : "Disabled", List.of(api), List.of(KEY));
        }

        @Override
        public PluginReadiness readiness() {
            return new PluginReadiness(available, true, available, available ? null : "Disabled");
        }

        @Override
        public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName apiName) {
            return Optional.of(new BatchDownloadDescriptor(
                    List.of(symbol, endpoint("from", "to"), endpoint("to", "from")), "from", "to",
                    BatchDownloadDescriptor.DateAxis.CALENDAR_DATE, "Observed date", mode,
                    mode == BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE,
                    BatchDownloadDescriptor.Availability.AVAILABLE, null, "runner-test-v1",
                    new BatchDownloadDescriptor.CompletenessRule(
                            BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE, null,
                            "Controlled complete response")));
        }

        @Override
        public List<DateRange> plan(ApiName apiName, Map<String, Object> params, BatchCallContext context) {
            if (plan != null) return plan;
            DateRange requested = range(params);
            return switch (mode) {
                case NATIVE_RANGE -> List.of(requested);
                case CALENDAR_DAYS -> calendarDays(requested);
                case TRADING_DAYS -> List.of();
            };
        }

        @Override
        public Map<String, Object> sourceParameters(ApiName apiName, Map<String, Object> params, DateRange range) {
            return Map.of("symbol", params.get("symbol"), "from", BASIC.format(range.start()),
                    "to", BASIC.format(range.end()));
        }

        @Override
        public DownloadEnvelope downloadBatch(ApiName apiName, Map<String, Object> params, BatchCallContext context) {
            context.beforeRequest();
            downloads.incrementAndGet();
            requested.add(Map.copyOf(params));
            if (entered != null) {
                entered.countDown();
                awaitIgnoringInterrupts(release);
            }
            String from = (String) params.get("from");
            ErrorCode failure = failureOn.get(from);
            if (failure != null) throw classified(failure);
            List<List<Object>> rows = empty || emptyOn.contains(from) ? List.of() : List.of(List.of(
                    symbolOn.getOrDefault(from, (String) params.get("symbol")), from, "10.00"));
            return envelope(params, rows);
        }

        DownloadEnvelope envelope(Map<String, Object> params, List<List<Object>> rows) {
            return new DownloadEnvelope(KEY.pluginId(), KEY.apiName(), params,
                    List.of("symbol", "observed_on", "amount"), rows.size(), rows,
                    DownloadStatus.SUCCESS, null);
        }

        @Override
        public BatchAssessment assess(ApiName apiName, DateRange range, DownloadEnvelope envelope) {
            return splitMultiDay && range.start().isBefore(range.end())
                    ? BatchAssessment.SPLIT_REQUIRED : BatchAssessment.COMPLETE;
        }

        @Override
        public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
            throw new AssertionError("RANGE runner must use downloadBatch");
        }

        DatasetDefinition definition() {
            return new DatasetDefinition(KEY, "Prices", "test", QueryMode.snapshot, api.parameters(),
                    TableName.from(KEY), List.of(
                            new ColumnDefinition("symbol", "Symbol", LogicalType.STRING, false, 0, 32,
                                    null, null, List.of(), false),
                            new ColumnDefinition("observed_on", "Observed date", LogicalType.DATE, false, 1,
                                    null, null, null, List.of(), false),
                            new ColumnDefinition("amount", "Amount", LogicalType.DECIMAL, false, 2,
                                    null, 18, 2, List.of(), false)),
                    new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("symbol", "observed_on")),
                    List.of(), "symbol", 100);
        }

        private static ParameterDescriptor endpoint(String name, String related) {
            return new ParameterDescriptor(name, name, null, ParameterType.DATE_RANGE_MEMBER,
                    true, null, List.of(), null, related);
        }

        private static DateRange range(Map<String, Object> params) {
            return new DateRange(LocalDate.parse((String) params.get("from"), BASIC),
                    LocalDate.parse((String) params.get("to"), BASIC));
        }

        private static List<DateRange> calendarDays(DateRange range) {
            List<DateRange> days = new ArrayList<>();
            for (LocalDate date = range.start();; date = date.plusDays(1)) {
                days.add(new DateRange(date, date));
                if (date.equals(range.end())) return List.copyOf(days);
            }
        }

        private static void awaitIgnoringInterrupts(CountDownLatch latch) {
            boolean interrupted = false;
            while (true) {
                try {
                    latch.await();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    static final class MutableClock extends Clock {
        volatile Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final class AcknowledgementFailingTransactionManager extends DataSourceTransactionManager {
        private final AtomicBoolean armed = new AtomicBoolean();

        AcknowledgementFailingTransactionManager(DriverManagerDataSource dataSource) {
            super(dataSource);
        }

        void arm() {
            assertThat(armed.compareAndSet(false, true)).isTrue();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            boolean rejectAcknowledgement = !status.isReadOnly() && armed.compareAndSet(true, false);
            super.doCommit(status);
            if (rejectAcknowledgement) {
                throw new TransactionSystemException("private-post-commit-acknowledgement");
            }
        }
    }
}
