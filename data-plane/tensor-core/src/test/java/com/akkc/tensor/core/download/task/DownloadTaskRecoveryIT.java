package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.persistence.ExistingKeyRepository;
import com.akkc.tensor.core.persistence.GenericUpsertRepository;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DownloadTaskRecoveryIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6");
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    final DownloadTaskJson json = new DownloadTaskJson();
    final DownloadTaskRunnerIT.MutableClock clock = new DownloadTaskRunnerIT.MutableClock();
    final UUID runId = UUID.randomUUID();
    DownloadTaskRunnerIT.ControlledSource source;
    DownloadTaskRepository repository;
    DownloadTaskService tasks;
    DownloadTaskRunner runner;
    DatasetCatalog catalog;
    GenericDatasetAdapter adapter;
    BatchCommitService commits;
    final List<DownloadTaskCoordinator> coordinators = new ArrayList<>();

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
        for (String batchId : jdbc.queryForList(
                "SELECT batch_id FROM tensor_download_batch ORDER BY LENGTH(batch_key) DESC", String.class)) {
            jdbc.update("DELETE FROM tensor_download_batch WHERE batch_id=?", batchId);
        }
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM runner_test__prices");
        source = spy(new DownloadTaskRunnerIT.ControlledSource());
        source.mode = BatchDownloadDescriptor.PlanningMode.TRADING_DAYS;
        source.plan = List.of(day("2026-09-10"), day("2026-09-11"), day("2026-09-12"));
        repository = new DownloadTaskRepository(jdbc, transactions, json);
        adapter = new GenericDatasetAdapter(source.definition(), new ValueConverter(), new FingerprintKeyCodec());
        var constructor = DatasetCatalog.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        catalog = constructor.newInstance(List.of(source.definition()));
        tasks = service(repository, runId, DownloadTaskService.Settings.defaults(), source);
        commits = commits(repository, transactions);
        runner = runner(tasks, repository, commits, runId);
    }

    @AfterEach
    void closeCoordinators() {
        coordinators.forEach(DownloadTaskCoordinator::close);
    }

    @Test
    void retryDownloadsOnlyTheFailedMiddleBatchAndPreservesSuccessfulLeaves() {
        source.failureOn.put("20260911", ErrorCode.SOURCE_NETWORK_ERROR);
        DownloadTask accepted = submit();
        assertThat(runner.runNext(() -> false).disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        DownloadTask failed = task(accepted.taskId());
        assertThat(failed.status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
        assertThat(repository.counts(failed.taskId()).succeeded()).isEqualTo(2);
        List<DownloadBatch> before = batches(failed.taskId());

        start(tasks, repository, runner, runId, new QueuedWorker());
        DownloadTask queued = tasks.retry(failed.taskId(), failed.version());

        assertThat(queued.status()).isEqualTo(DownloadTask.Status.QUEUED);
        assertThat(queued.runRequestCount()).isEqualTo(3);
        source.failureOn.clear();
        source.plan = List.of(); // Saved plans, not a fresh upstream plan, own the second run.
        assertThat(runner.runNext(() -> false).disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        DownloadTask succeeded = task(accepted.taskId());
        assertThat(succeeded.status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(succeeded.requestCount()).isEqualTo(4);
        assertThat(succeeded.runRequestCount()).isEqualTo(1);
        assertThat(repository.counts(succeeded.taskId()))
                .isEqualTo(new DownloadTaskRepository.Counts(3, 0, 0, 3, 0, 0, 3, 3, 0));
        assertThat(batches(succeeded.taskId())).extracting(DownloadBatch::attemptCount).containsExactly(1, 2, 1);
        assertThat(batches(succeeded.taskId()).getFirst()).isEqualTo(before.getFirst());
        assertThat(batches(succeeded.taskId()).getLast()).isEqualTo(before.getLast());
        assertThat(source.requested).extracting(params -> params.get("from"))
                .containsExactly("20260910", "20260911", "20260912", "20260911");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM runner_test__prices", Integer.class)).isEqualTo(3);
    }

    @Test
    void resumeRetainsOrdinaryFailureAndContinuesInterruptedAndPendingBatches() {
        DownloadTask accepted = submit();
        var claimed = repository.claimTask(accepted.taskId(), runId, clock.instant(),
                clock.instant().plusSeconds(60)).orElseThrow();
        var permit = permit(claimed);
        List<DownloadTaskRepository.NewBatch> roots = List.of(root("000001", "2026-09-10"),
                root("000002", "2026-09-11"), root("000003", "2026-09-12"));
        repository.savePlan(permit, roots, 20, clock.instant());
        repository.claimBatch(permit, roots.getFirst().batchId(), clock.instant());
        repository.reserveRequest(permit, 20, clock.instant());
        repository.failBatch(permit, roots.getFirst().batchId(), ErrorCode.SOURCE_NETWORK_ERROR, clock.instant());
        repository.claimBatch(permit, roots.get(1).batchId(), clock.instant());
        repository.reserveRequest(permit, 20, clock.instant());
        DownloadTask running = task(accepted.taskId());
        DownloadTask interrupted = repository.recoverStoppedTask(running.taskId(), runId,
                running.runGeneration(), running.version(), clock.instant());
        DownloadBatch ordinaryFailure = batches(accepted.taskId()).getFirst();
        start(tasks, repository, runner, runId, new QueuedWorker());

        DownloadTask resumed = tasks.resume(interrupted.taskId(), interrupted.version());

        assertThat(resumed.runRequestCount()).isEqualTo(2);
        assertThat(batches(resumed.taskId())).extracting(DownloadBatch::status)
                .containsExactly(DownloadBatch.Status.FAILED, DownloadBatch.Status.PENDING, DownloadBatch.Status.PENDING);
        source.plan = List.of();
        runner.runNext(() -> false);
        assertThat(task(resumed.taskId()).status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
        assertThat(task(resumed.taskId()).requestCount()).isEqualTo(4);
        assertThat(task(resumed.taskId()).runRequestCount()).isEqualTo(2);
        assertThat(batches(resumed.taskId()).getFirst()).isEqualTo(ordinaryFailure);
        assertThat(batches(resumed.taskId())).extracting(DownloadBatch::attemptCount).containsExactly(1, 2, 1);
        assertThat(source.requested).extracting(params -> params.get("from"))
                .containsExactly("20260911", "20260912");
    }

    @Test
    void failedUnplannedTaskRetriesPlanningAndSplitTreesKeepTheirSuccessfulLeaves() {
        source.plan = List.of(day("2026-09-10"), day("2026-09-10"));
        DownloadTask accepted = submit();
        runner.runNext(() -> false);
        DownloadTask failed = task(accepted.taskId());
        assertThat(failed.status()).isEqualTo(DownloadTask.Status.FAILED);
        assertThat(failed.planReady()).isFalse();
        start(tasks, repository, runner, runId, new QueuedWorker());
        tasks.retry(failed.taskId(), failed.version());
        source.plan = List.of(day("2026-09-10"));
        runner.runNext(() -> false);
        assertThat(task(accepted.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(source.downloads.get()).isEqualTo(1);

        source.mode = BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE;
        source.plan = null;
        source.splitMultiDay = true;
        source.failureOn.put("20260912", ErrorCode.SOURCE_NETWORK_ERROR);
        DownloadTask split = submit();
        runner.runNext(() -> false);
        DownloadTask splitFailed = task(split.taskId());
        assertThat(splitFailed.status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
        List<DownloadBatch> preserved = batches(split.taskId()).stream()
                .filter(batch -> batch.status() != DownloadBatch.Status.FAILED).toList();
        int requests = source.downloads.get();
        tasks.retry(split.taskId(), splitFailed.version());
        source.failureOn.clear();
        runner.runNext(() -> false);
        assertThat(task(split.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(source.downloads.get()).isEqualTo(requests + 1);
        assertThat(batches(split.taskId())).containsAll(preserved);
    }

    @Test
    void versionRaceAcceptsExactlyOneRetryAndRejectedControlsPreserveEveryStoredFact() throws Exception {
        source.failureOn.put("20260911", ErrorCode.SOURCE_NETWORK_ERROR);
        DownloadTask accepted = submit();
        runner.runNext(() -> false);
        DownloadTask failed = task(accepted.taskId());
        start(tasks, repository, runner, runId, new QueuedWorker());
        List<DownloadBatch> before = batches(failed.taskId());
        source.available = false;
        code(ErrorCode.PLUGIN_DISABLED, () -> tasks.retry(failed.taskId(), failed.version()));
        assertThat(task(failed.taskId())).isEqualTo(failed);
        source.available = true;
        source.mode = BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        code(ErrorCode.TASK_DEFINITION_CHANGED, () -> tasks.retry(failed.taskId(), failed.version()));
        assertThat(task(failed.taskId())).isEqualTo(failed);
        assertThat(batches(failed.taskId())).isEqualTo(before);
        source.mode = BatchDownloadDescriptor.PlanningMode.TRADING_DAYS;
        var results = DownloadTaskRepositoryIT.race(
                () -> tasks.retry(failed.taskId(), failed.version()),
                () -> tasks.retry(failed.taskId(), failed.version()));
        assertThat(results.stream().filter(DownloadTask.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(TensorException.class::isInstance).map(v -> ((TensorException) v).code()))
                .containsExactly(ErrorCode.TASK_STATE_CONFLICT);
        assertThat(task(failed.taskId()).version()).isEqualTo(failed.version() + 1);
        assertThat(source.downloads.get()).isEqualTo(3);
    }

    @Test
    void fullQueueRejectsRetryWithoutChangingTaskBatchesOrCounters() {
        source.failureOn.put("20260911", ErrorCode.SOURCE_NETWORK_ERROR);
        DownloadTask accepted = submit();
        runner.runNext(() -> false);
        DownloadTask failed = task(accepted.taskId());
        List<DownloadBatch> before = batches(failed.taskId());
        tasks = service(repository, runId, new DownloadTaskService.Settings(true, 1, 36600), source);
        runner = runner(tasks, repository, commits, runId);
        start(tasks, repository, runner, runId, new QueuedWorker());
        submit();
        code(ErrorCode.TASK_QUEUE_FULL, () -> tasks.retry(failed.taskId(), failed.version()));
        assertThat(task(failed.taskId())).isEqualTo(failed);
        assertThat(batches(failed.taskId())).isEqualTo(before);
        assertThat(source.downloads.get()).isEqualTo(3);
    }

    @Test
    void requeueAcknowledgementAndReadBackFailuresLeaveOneCommittedQueuedTask() {
        source.failureOn.put("20260911", ErrorCode.SOURCE_NETWORK_ERROR);
        DownloadTask accepted = submit();
        runner.runNext(() -> false);
        DownloadTask failed = task(accepted.taskId());
        var transactionManager = new DownloadTaskRunnerIT.AcknowledgementFailingTransactionManager(dataSource);
        var uncertain = spy(new DownloadTaskRepository(jdbc, transactionManager, json));
        doAnswer(invocation -> {
            transactionManager.arm();
            return invocation.callRealMethod();
        }).when(uncertain).requeue(any(), anyLong(), any(), any(), any());
        DownloadTaskService controls = service(uncertain, runId, DownloadTaskService.Settings.defaults(), source);
        start(controls, uncertain, runner, runId, new QueuedWorker());
        code(ErrorCode.PERSISTENCE_FAILED, () -> controls.retry(failed.taskId(), failed.version()));
        assertThat(task(failed.taskId()).status()).isEqualTo(DownloadTask.Status.QUEUED);
        assertThat(task(failed.taskId()).version()).isEqualTo(failed.version() + 1);
        code(ErrorCode.TASK_STATE_CONFLICT, () -> controls.retry(failed.taskId(), failed.version()));
        runner.runNext(() -> false);
        DownloadTask failedAgain = task(failed.taskId());
        AtomicBoolean committed = new AtomicBoolean();
        var unreadable = spy(repository);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            committed.set(true);
            return null;
        }).when(unreadable).requeue(any(), anyLong(), any(), any(), any());
        doAnswer(invocation -> {
            if (committed.get()) throw classified(ErrorCode.QUERY_FAILED);
            return invocation.callRealMethod();
        }).when(unreadable).findTask(any());
        DownloadTaskService readBack = service(unreadable, runId, DownloadTaskService.Settings.defaults(), source);
        start(readBack, unreadable, runner, runId, new QueuedWorker());
        code(ErrorCode.QUERY_FAILED, () -> readBack.retry(failedAgain.taskId(), failedAgain.version()));
        assertThat(task(failed.taskId()).status()).isEqualTo(DownloadTask.Status.QUEUED);
        assertThat(task(failed.taskId()).version()).isEqualTo(failedAgain.version() + 1);
        committed.set(false);
    }

    @Test
    void restartRecoversOldQueuedAndMixedRunningFactsWithoutAnyAutomaticDownload() {
        DownloadTask mixed = submit();
        var claimed = repository.claimTask(mixed.taskId(), runId, clock.instant(),
                clock.instant().plusSeconds(60)).orElseThrow();
        var permit = permit(claimed);
        List<DownloadTaskRepository.NewBatch> roots = List.of(root("000001", "2026-09-10"),
                root("000002", "2026-09-11"), root("000003", "2026-09-12"));
        repository.savePlan(permit, roots, 20, clock.instant());
        repository.claimBatch(permit, roots.getFirst().batchId(), clock.instant());
        repository.failBatch(permit, roots.getFirst().batchId(), ErrorCode.SOURCE_NETWORK_ERROR, clock.instant());
        repository.claimBatch(permit, roots.get(1).batchId(), clock.instant());
        DownloadTask queued = submit();
        DownloadTask oldRunning = task(mixed.taskId());
        UUID nextId = UUID.randomUUID();
        DownloadTaskService next = service(repository, nextId, DownloadTaskService.Settings.defaults(), source);
        var worker = new QueuedWorker();
        DownloadTaskCoordinator coordinator = start(next, repository,
                runner(next, repository, commits, nextId), nextId, worker);
        assertThat(task(queued.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(task(mixed.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(task(mixed.taskId()).planReady()).isTrue();
        assertThat(batches(mixed.taskId())).extracting(DownloadBatch::status)
                .containsExactly(DownloadBatch.Status.FAILED, DownloadBatch.Status.FAILED, DownloadBatch.Status.PENDING);
        assertThat(batches(mixed.taskId()).getFirst().error().code()).isEqualTo(ErrorCode.SOURCE_NETWORK_ERROR);
        assertThat(batches(mixed.taskId()).get(1).error().code()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED);
        assertThat(task(mixed.taskId()).version()).isEqualTo(oldRunning.version() + 1);
        assertThat(source.downloads.get()).isZero();
        coordinator.start();
        coordinator.tick();
        worker.runNext();
        assertThat(source.downloads.get()).isZero();
        assertThat(task(mixed.taskId()).version()).isEqualTo(oldRunning.version() + 1);
        source.available = false;
        DownloadTask interrupted = task(mixed.taskId());
        code(ErrorCode.PLUGIN_DISABLED, () -> next.resume(interrupted.taskId(), interrupted.version()));
        assertThat(task(mixed.taskId())).isEqualTo(interrupted);
    }

    @Test
    void restartRecomputesAllSuccessfulLeavesWithoutReissuingRequests() {
        DownloadTask accepted = submit();
        var uncertain = spy(repository);
        doThrow(classified(ErrorCode.PERSISTENCE_FAILED)).when(uncertain)
                .finishTask(any(), any(), any(), any());
        var first = runner(tasks, uncertain, commits, runId).runNext(() -> false);
        assertThat(first.disposition()).isEqualTo(DownloadTaskRunner.Disposition.NEEDS_RECOVERY);
        assertThat(task(accepted.taskId()).status()).isEqualTo(DownloadTask.Status.RUNNING);
        assertThat(repository.counts(accepted.taskId()).succeeded()).isEqualTo(3);
        List<DownloadBatch> successful = batches(accepted.taskId());
        UUID nextId = UUID.randomUUID();
        DownloadTaskService next = service(repository, nextId, DownloadTaskService.Settings.defaults(), source);
        start(next, repository, runner(next, repository, commits, nextId), nextId, new QueuedWorker());
        assertThat(task(accepted.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(batches(accepted.taskId())).isEqualTo(successful);
        assertThat(source.downloads.get()).isEqualTo(3);
    }

    @Test
    void startupRecoveryFailureKeepsAdmissionClosedAndPreservesAlreadyRecoveredTasks() {
        DownloadTask first = submit();
        clock.now = clock.now.plusSeconds(1);
        DownloadTask second = submit();
        var unavailable = spy(repository);
        AtomicInteger recovered = new AtomicInteger();
        doAnswer(invocation -> {
            if (recovered.incrementAndGet() == 2) throw classified(ErrorCode.PERSISTENCE_FAILED);
            return invocation.callRealMethod();
        }).when(unavailable).recoverStoppedTask(any(), any(), anyInt(), anyLong(), any());
        UUID nextId = UUID.randomUUID();
        DownloadTaskService next = service(unavailable, nextId, DownloadTaskService.Settings.defaults(), source);
        var coordinator = coordinator(next, unavailable, runner(next, unavailable, commits, nextId), nextId,
                new QueuedWorker());
        code(ErrorCode.PERSISTENCE_FAILED, coordinator::start);
        assertThat(coordinator.isRunning()).isFalse();
        code(ErrorCode.PLUGIN_DISABLED, () -> next.submit(submission()));
        assertThat(task(first.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(task(second.taskId()).status()).isEqualTo(DownloadTask.Status.QUEUED);
        assertThat(source.downloads.get()).isZero();
        assertThat(next.submit(new DownloadTaskService.Submission(second.submissionId(), second.datasetKey(),
                second.mode(), second.params())).task()).isEqualTo(second);
    }

    @Test
    void uncertainCommitPausesUntilDatabaseReturnsThenRecoversCommittedSuccessWithoutReplay() {
        source.plan = List.of(day("2026-09-10"));
        DownloadTask accepted = submit();
        var transactionManager = new DownloadTaskRunnerIT.AcknowledgementFailingTransactionManager(dataSource);
        var uncertain = spy(new DownloadTaskRepository(jdbc, transactionManager, json));
        AtomicBoolean unavailable = new AtomicBoolean();
        doAnswer(invocation -> {
            if (unavailable.get()) throw classified(ErrorCode.QUERY_FAILED);
            return invocation.callRealMethod();
        }).when(uncertain).snapshot(any());
        var uncertainCommit = spy(commits(uncertain, transactionManager));
        doAnswer(invocation -> {
            transactionManager.arm();
            try {
                return invocation.callRealMethod();
            } finally {
                unavailable.set(true);
            }
        }).when(uncertainCommit).commit(any(), any(), any(), anyLong(), any());
        var controlled = service(uncertain, runId, DownloadTaskService.Settings.defaults(), source);
        var worker = new QueuedWorker();
        var coordinator = start(controlled, uncertain, runner(controlled, uncertain, uncertainCommit, runId),
                runId, worker);
        coordinator.tick();
        worker.runNext();
        assertThat(repository.counts(accepted.taskId()).succeeded()).isEqualTo(1);
        assertThat(task(accepted.taskId()).status()).isEqualTo(DownloadTask.Status.RUNNING);
        code(ErrorCode.TASK_STATE_CONFLICT, () -> controlled.submit(submission()));
        assertThat(controlled.submit(new DownloadTaskService.Submission(accepted.submissionId(),
                accepted.datasetKey(), accepted.mode(), accepted.params())).created()).isFalse();
        coordinator.tick();
        assertThat(worker.queued).isEmpty();
        assertThat(source.downloads.get()).isEqualTo(1);
        unavailable.set(false);
        coordinator.tick();
        assertThat(task(accepted.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(worker.queued).isEmpty();
        coordinator.tick();
        worker.runNext();
        assertThat(source.downloads.get()).isEqualTo(1);
    }

    @Test
    void unknownTaskQueryFailureOnlyProbesAndClaimReceiptFailureRecoversKnownCandidate() {
        DownloadTask accepted = submit();
        var unavailable = spy(repository);
        AtomicBoolean failQuery = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failQuery.get()) throw classified(ErrorCode.QUERY_FAILED);
            return invocation.callRealMethod();
        }).when(unavailable).queuedTasks(any(), anyInt());
        var transactionManager = new DownloadTaskRunnerIT.AcknowledgementFailingTransactionManager(dataSource);
        var claimReceipt = spy(new DownloadTaskRepository(jdbc, transactionManager, json));
        doAnswer(invocation -> {
            transactionManager.arm();
            return invocation.callRealMethod();
        }).when(claimReceipt).claimTask(any(), any(), any(), any());
        DownloadTaskService controlled = service(unavailable, runId, DownloadTaskService.Settings.defaults(), source);
        var worker = new QueuedWorker();
        var coordinator = start(controlled, unavailable, runner(controlled, unavailable, commits, runId), runId, worker);
        coordinator.tick();
        worker.runNext();
        coordinator.tick();
        assertThat(task(accepted.taskId())).isEqualTo(accepted);
        assertThat(source.downloads.get()).isZero();
        coordinator.close();
        // A distinct process can accept and recover the next ambiguous claim.
        UUID nextId = UUID.randomUUID();
        DownloadTaskService next = service(claimReceipt, nextId, DownloadTaskService.Settings.defaults(), source);
        var nextWorker = new QueuedWorker();
        var nextCoordinator = start(next, claimReceipt, runner(next, claimReceipt, commits, nextId), nextId, nextWorker);
        DownloadTask nextTask = next.submit(submission()).task();
        nextCoordinator.tick();
        nextWorker.runNext();
        assertThat(task(nextTask.taskId()).status()).isEqualTo(DownloadTask.Status.RUNNING);
        assertThat(task(nextTask.taskId()).planReady()).isFalse();
        nextCoordinator.tick();
        assertThat(task(nextTask.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(source.downloads.get()).isZero();
    }

    @Test
    void closeWaitsForBlockedOrdinarySingleAndDropsItsLateEnvelopeBeforeAdaptation() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        AtomicInteger adapted = new AtomicInteger();
        adapter = spy(adapter);
        doAnswer(invocation -> {
            adapted.incrementAndGet();
            return invocation.callRealMethod();
        }).when(adapter).adapt(any(), any());
        DataSourcePlugin ordinary = new DataSourcePlugin() {
            @Override public PluginDescriptor descriptor() { return source.descriptor(); }
            @Override public PluginReadiness readiness() { return source.readiness(); }
            @Override public DownloadEnvelope download(ApiName api, Map<String, Object> params) {
                source.downloads.incrementAndGet();
                entered.countDown();
                await(release);
                return source.envelope(params, List.of(List.of("AA", "20260910", "10.00")));
            }
        };
        tasks = service(repository, runId, DownloadTaskService.Settings.defaults(), ordinary);
        runner = runner(tasks, repository, commits, runId);
        var worker = Executors.newSingleThreadExecutor();
        var coordinator = start(tasks, repository, runner, runId, worker);
        var request = new DownloadTaskService.Submission(UUID.randomUUID(), DownloadTaskRunnerIT.KEY,
                DownloadMode.SINGLE, Map.of("symbol", "AA"));
        DownloadTask active = tasks.submit(request).task();
        coordinator.tick();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        DownloadTask queued = tasks.submit(new DownloadTaskService.Submission(UUID.randomUUID(),
                DownloadTaskRunnerIT.KEY, DownloadMode.SINGLE, Map.of("symbol", "BB"))).task();
        var closingExecutor = Executors.newSingleThreadExecutor();
        var closing = closingExecutor.submit(coordinator::close);
        try {
            assertThat(waitUntil(() -> !coordinator.isRunning())).isTrue();
            assertThat(closing.isDone()).isFalse();
            assertThat(task(active.taskId()).status()).isEqualTo(DownloadTask.Status.RUNNING);
            assertThat(task(queued.taskId()).status()).isEqualTo(DownloadTask.Status.QUEUED);
            assertThat(tasks.controls(task(active.taskId())))
                    .isEqualTo(new DownloadTaskService.ControlAvailability(false, false));
            code(ErrorCode.PLUGIN_DISABLED, () -> tasks.submit(submission()));
            assertThat(tasks.submit(request).created()).isFalse();
            assertThat(source.downloads.get()).isEqualTo(1);
        } finally {
            release.countDown();
            closing.get(5, TimeUnit.SECONDS);
            closingExecutor.shutdown();
        }
        assertThat(task(active.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(task(queued.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(adapted.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM runner_test__prices", Integer.class)).isZero();
        assertThat(source.downloads.get()).isEqualTo(1);
    }

    @Test
    void closeRecoversOnlyThisRunsQueuedTasksAndLeavesUnavailableDatabaseForNextStartup() {
        var unavailable = spy(repository);
        AtomicBoolean fail = new AtomicBoolean();
        doAnswer(invocation -> {
            if (fail.get()) throw classified(ErrorCode.QUERY_FAILED);
            return invocation.callRealMethod();
        }).when(unavailable).unfinishedTasks();
        tasks = service(unavailable, runId, DownloadTaskService.Settings.defaults(), source);
        runner = runner(tasks, unavailable, commits, runId);
        var coordinator = start(tasks, unavailable, runner, runId, new QueuedWorker());
        DownloadTask current = submit();
        UUID foreignId = UUID.randomUUID();
        DownloadTaskService foreign = service(repository, foreignId, DownloadTaskService.Settings.defaults(), source);
        DownloadTask other = foreign.submit(submission()).task();
        coordinator.close();
        assertThat(task(current.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(task(other.taskId())).isEqualTo(other);
        UUID nextId = UUID.randomUUID();
        DownloadTaskService next = service(unavailable, nextId, DownloadTaskService.Settings.defaults(), source);
        var nextCoordinator = start(next, unavailable, runner(next, unavailable, commits, nextId),
                nextId, new QueuedWorker());
        DownloadTask deferred = next.submit(submission()).task();
        fail.set(true);
        nextCoordinator.close();
        assertThat(task(deferred.taskId())).isEqualTo(deferred);
        fail.set(false);
        UUID restartedId = UUID.randomUUID();
        DownloadTaskService restarted = service(repository, restartedId, DownloadTaskService.Settings.defaults(), source);
        start(restarted, repository, runner(restarted, repository, commits, restartedId), restartedId, new QueuedWorker());
        assertThat(task(deferred.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(source.downloads.get()).isZero();
    }

    @Test
    void closeLetsAnApprovedSecuritiesTransactionCommitAndPreservesAllSuccessfulLeaves() throws Exception {
        source.plan = List.of(day("2026-09-10"));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var upserts = spy(new GenericUpsertRepository(jdbc));
        doAnswer(invocation -> {
            // PersistenceParticipant.beforeWrite has already checked the execution permit and stop flag.
            entered.countDown();
            await(release);
            return invocation.callRealMethod();
        }).when(upserts).upsert(any(), any());
        var persistence = new PersistenceService(catalog, new DatasetLockManager(),
                new ExistingKeyRepository(jdbc), upserts, transactions);
        var committingRunner = runner(tasks, repository, new BatchCommitService(persistence, repository, clock), runId);
        var coordinator = start(tasks, repository, committingRunner, runId, Executors.newSingleThreadExecutor());
        DownloadTask accepted = submit();
        coordinator.tick();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var closingExecutor = Executors.newSingleThreadExecutor();
        var closing = closingExecutor.submit(coordinator::close);
        try {
            assertThat(waitUntil(() -> !coordinator.isRunning())).isTrue();
            assertThat(closing.isDone()).isFalse();
        } finally {
            release.countDown();
            closing.get(5, TimeUnit.SECONDS);
            closingExecutor.shutdown();
        }
        assertThat(task(accepted.taskId()).status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
        assertThat(repository.counts(accepted.taskId()))
                .isEqualTo(new DownloadTaskRepository.Counts(1, 0, 0, 1, 0, 0, 1, 1, 0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM runner_test__prices", Integer.class)).isEqualTo(1);
        assertThat(source.downloads.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void manualRequeueRetiresAnOlderLostPermitEvenWhenTheCommitReceiptIsLost(boolean resume) {
        source.failureOn.put("20260911", ErrorCode.SOURCE_NETWORK_ERROR);
        DownloadTask accepted = submit();
        runner.runNext(() -> false);
        DownloadTask failed = task(accepted.taskId());
        var oldPermit = permit(failed);
        if (resume) {
            repository.requeue(failed.taskId(), failed.version(), runId,
                    DownloadTaskRepository.RequeueMode.RETRY, clock.instant());
            DownloadTask queued = task(failed.taskId());
            repository.recoverStoppedTask(queued.taskId(), runId, queued.runGeneration(), queued.version(), clock.instant());
        }
        DownloadTask terminal = task(accepted.taskId());
        var transactionManager = new DownloadTaskRunnerIT.AcknowledgementFailingTransactionManager(dataSource);
        var uncertain = spy(new DownloadTaskRepository(jdbc, transactionManager, json));
        doAnswer(invocation -> {
            transactionManager.arm();
            return invocation.callRealMethod();
        }).when(uncertain).requeue(any(), anyLong(), any(), any(), any());
        var controls = service(uncertain, runId, DownloadTaskService.Settings.defaults(), source);
        DownloadTaskRunner oldResult = mock(DownloadTaskRunner.class);
        org.mockito.Mockito.when(oldResult.runNext(any())).thenReturn(new DownloadTaskRunner.RunResult(
                terminal.taskId(), oldPermit, DownloadTaskRunner.Disposition.PERMIT_LOST, ErrorCode.TASK_STATE_CONFLICT));
        var worker = new QueuedWorker();
        var coordinator = start(controls, uncertain, oldResult, runId, worker);
        coordinator.tick();
        worker.runNext();
        code(ErrorCode.PERSISTENCE_FAILED, () -> {
            if (resume) controls.resume(terminal.taskId(), terminal.version());
            else controls.retry(terminal.taskId(), terminal.version());
        });
        assertThat(task(terminal.taskId()).status()).isEqualTo(DownloadTask.Status.QUEUED);
        coordinator.close();
        assertThat(task(terminal.taskId()).status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
        assertThat(source.downloads.get()).isEqualTo(3);
    }

    private DownloadTask submit() {
        return tasks.submit(submission()).task();
    }

    private DownloadTaskService.Submission submission() {
        return new DownloadTaskService.Submission(UUID.randomUUID(), DownloadTaskRunnerIT.KEY,
                DownloadMode.RANGE, Map.of("symbol", "AA", "from", "20260910", "to", "20260912"));
    }

    private DownloadTaskService service(DownloadTaskRepository repo, UUID id,
            DownloadTaskService.Settings settings, DataSourcePlugin plugin) {
        return new DownloadTaskService(new PluginRegistry(List.of(plugin)), catalog,
                new AdapterRegistry(List.of(adapter)), new ParameterValidator(), repo, json, clock, id, settings);
    }

    private BatchCommitService commits(DownloadTaskRepository repo, DataSourceTransactionManager transactionManager) {
        var persistence = new PersistenceService(catalog, new DatasetLockManager(),
                new ExistingKeyRepository(jdbc), new GenericUpsertRepository(jdbc), transactionManager);
        return new BatchCommitService(persistence, repo, clock);
    }

    private DownloadTaskRunner runner(DownloadTaskService service, DownloadTaskRepository repo,
            BatchCommitService commit, UUID id) {
        return new DownloadTaskRunner(service, repo, commit, json, clock, id,
                new DownloadTaskRunner.Settings(true, 36600, 20, 20, Duration.ofMinutes(30), 20));
    }

    private DownloadTaskCoordinator coordinator(DownloadTaskService service, DownloadTaskRepository repo,
            DownloadTaskRunner execution, UUID id, ExecutorService worker) {
        ScheduledExecutorService poller = mock(ScheduledExecutorService.class);
        try {
            doAnswer(invocation -> true).when(poller).awaitTermination(anyLong(), any());
        } catch (InterruptedException impossible) {
            throw new AssertionError(impossible);
        }
        var coordinator = new DownloadTaskCoordinator(service, repo, execution, clock, id,
                poller, worker);
        coordinators.add(coordinator);
        return coordinator;
    }

    private DownloadTaskCoordinator start(DownloadTaskService service, DownloadTaskRepository repo,
            DownloadTaskRunner execution, UUID id, ExecutorService worker) {
        var coordinator = coordinator(service, repo, execution, id, worker);
        coordinator.start();
        return coordinator;
    }

    private DownloadTaskRepository.ExecutionPermit permit(DownloadTask task) {
        return new DownloadTaskRepository.ExecutionPermit(task.taskId(), task.activeRunId(), task.runGeneration());
    }

    private DownloadTaskRepository.NewBatch root(String key, String date) {
        DateRange range = day(date);
        return new DownloadTaskRepository.NewBatch(UUID.randomUUID(), key, range,
                Map.of("symbol", "AA", "from", DownloadTaskRunnerIT.BASIC.format(range.start()),
                        "to", DownloadTaskRunnerIT.BASIC.format(range.end())));
    }

    private static TensorException classified(ErrorCode code) {
        return new DownloadTaskService.TaskException(code);
    }

    private static void code(ErrorCode expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(TensorException.class).hasNoCause()
                .extracting(error -> ((TensorException) error).code()).isEqualTo(expected);
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        for (;;) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Source was not released");
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        return condition.getAsBoolean();
    }

    private static final class QueuedWorker extends AbstractExecutorService {
        final ArrayDeque<Runnable> queued = new ArrayDeque<>();
        boolean shutdown;

        void runNext() { queued.removeFirst().run(); }
        @Override public void execute(Runnable command) { queued.add(command); }
        @Override public void shutdown() {
            shutdown = true;
            while (!queued.isEmpty()) runNext();
        }
        @Override public List<Runnable> shutdownNow() { throw new AssertionError("Must await actual work"); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown && queued.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }

    private DownloadTask task(UUID id) {
        return repository.findTask(id).orElseThrow();
    }

    private List<DownloadBatch> batches(UUID id) {
        return repository.batches(id, new DownloadTaskRepository.BatchFilter(null, true), 1, 100).items();
    }

    private static DateRange day(String value) {
        LocalDate date = LocalDate.parse(value);
        return new DateRange(date, date);
    }
}
