package com.akkc.tensor.plugin.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.BatchCommitService;
import com.akkc.tensor.core.download.task.DownloadBatch;
import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskJson;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.Counts;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.ExecutionPermit;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.NewBatch;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.NewTask;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.Page;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.StoredError;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.TaskSnapshot;
import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.BatchDownloadSupport;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FixtureDownloadTaskRunnerTest {
    private static final Instant NOW = Instant.parse("2026-08-07T12:34:56Z");
    private static final UUID RUN = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final DatasetKey KEY = DatasetKey.of(PluginId.of("fixture"), ApiName.of("fixture_daily"));

    @Test
    void successUsesTheOrdinaryFixturePluginAndRealAdapter() throws Exception {
        var harness = new Harness("SUCCESS");

        var result = harness.run();

        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(result.error()).isNull();
        assertThat(harness.plugin).isInstanceOf(DataSourcePlugin.class)
                .isNotInstanceOf(BatchDownloadSupport.class);
        AdaptedBatch adapted = harness.committed(1);
        assertThat(adapted.columns()).containsExactly("ts_code", "trade_date", "amount", "note");
        assertThat(adapted.rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("ts_code", "000001.SZ");
            assertThat(row).containsEntry("trade_date", LocalDate.of(2026, 8, 7));
            assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("11.23");
            assertThat(row).containsEntry("note", null);
        });
        assertThat(adapted.ingestedAt()).isEqualTo(NOW);
        harness.assertReservedOnce();
    }

    @Test
    void emptyStillAdaptsAndCommitsAfterOneReservation() throws Exception {
        var harness = new Harness("EMPTY");

        var result = harness.run();

        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(result.error()).isNull();
        assertThat(harness.committed(0).rows()).isEmpty();
        harness.assertReservedOnce();
    }

    @Test
    void sourceFailureRecordsTheFixtureErrorWithoutCommit() throws Exception {
        var harness = new Harness("SOURCE_FAILURE");

        var result = harness.run();

        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(result.error()).isEqualTo(ErrorCode.SOURCE_UNAVAILABLE);
        verify(harness.repository).failBatch(
                harness.permit, harness.batch.batchId(), ErrorCode.SOURCE_UNAVAILABLE, NOW);
        verify(harness.commits, never()).commit(any(), any(), any(), anyLong(), any());
        harness.assertReservedOnce();
    }

    @Test
    void typeFailureRecordsTheRealAdapterErrorWithoutCommit() throws Exception {
        var harness = new Harness("TYPE_FAILURE");

        var result = harness.run();

        assertThat(result.disposition()).isEqualTo(DownloadTaskRunner.Disposition.FINISHED);
        assertThat(result.error()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
        verify(harness.repository).failBatch(
                harness.permit, harness.batch.batchId(), ErrorCode.ADAPTER_TYPE_INVALID, NOW);
        verify(harness.commits, never()).commit(any(), any(), any(), anyLong(), any());
        harness.assertReservedOnce();
    }

    private static final class Harness {
        final DownloadTaskRepository repository = mock(DownloadTaskRepository.class);
        final BatchCommitService commits = mock(BatchCommitService.class);
        final DownloadTaskJson json = new DownloadTaskJson();
        final FixturePlugin plugin;
        final DatasetAdapter adapter;
        final DownloadTaskService tasks;
        final String scenario;
        DownloadTask task;
        DownloadBatch batch;
        ExecutionPermit permit;

        Harness(String scenario) throws Exception {
            this.scenario = scenario;
            var configuration = new FixtureConfiguration();
            plugin = configuration.fixturePlugin();
            adapter = configuration.fixtureDatasetAdapter();
            var constructor = DatasetCatalog.class.getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            var catalog = constructor.newInstance(List.of(adapter.definition()));
            tasks = new DownloadTaskService(new PluginRegistry(List.of(plugin)), catalog,
                    new AdapterRegistry(List.of(adapter)), new ParameterValidator(), repository, json,
                    Clock.fixed(NOW, ZoneOffset.UTC), RUN, DownloadTaskService.Settings.defaults());
            stubRepository();
        }

        DownloadTaskRunner.RunResult run() {
            tasks.submit(new DownloadTaskService.Submission(UUID.randomUUID(), KEY, DownloadMode.SINGLE,
                    Map.of("scenario", scenario)));
            return new DownloadTaskRunner(tasks, repository, commits, json, Clock.fixed(NOW, ZoneOffset.UTC),
                    RUN, DownloadTaskRunner.Settings.defaults()).runNext(() -> false);
        }

        AdaptedBatch committed(long sourceRows) {
            var captured = ArgumentCaptor.forClass(AdaptedBatch.class);
            verify(commits).commit(eq(permit), eq(batch.batchId()), captured.capture(), eq(sourceRows),
                    any(BooleanSupplier.class));
            return captured.getValue();
        }

        void assertReservedOnce() {
            verify(repository).reserveRequest(permit, 5000, NOW);
        }

        private void stubRepository() {
            when(repository.findSubmission(any())).thenReturn(Optional.empty());
            when(repository.insert(any())).thenAnswer(invocation -> {
                NewTask input = invocation.getArgument(0);
                task = new DownloadTask(input.taskId(), input.submissionId(),
                        json.requestHash(KEY, input.mode(), input.normalizedParams()), KEY, input.mode(),
                        input.normalizedParams(), input.definitionHash(), input.policySnapshot(),
                        DownloadTask.Status.QUEUED, false, RUN, 0, 1, 0, 0, null,
                        NOW, NOW, NOW, null, null, null);
                return task;
            });
            when(repository.queuedTasks(RUN, 1)).thenAnswer(ignored -> List.of(task));
            when(repository.claimTask(any(), eq(RUN), any(), any())).thenAnswer(invocation -> {
                task = copyTask(DownloadTask.Status.RUNNING, false, 1, invocation.getArgument(3), null);
                permit = new ExecutionPermit(task.taskId(), RUN, task.runGeneration());
                return Optional.of(task);
            });
            doAnswer(invocation -> {
                List<NewBatch> roots = invocation.getArgument(1);
                NewBatch root = roots.getFirst();
                batch = new DownloadBatch(root.batchId(), task.taskId(), null, root.batchKey(), null,
                        root.sourceParams(), DownloadBatch.Status.PENDING, 0, null,
                        0, 0, 0, null, NOW, NOW, null, null);
                task = copyTask(DownloadTask.Status.RUNNING, true, 1, task.deadlineAt(), null);
                return null;
            }).when(repository).savePlan(any(), any(), anyInt(), any());
            when(repository.pendingBatches(any())).thenAnswer(ignored ->
                    batch != null && batch.status() == DownloadBatch.Status.PENDING ? List.of(batch) : List.of());
            when(repository.claimBatch(any(), any(), any())).thenAnswer(ignored -> {
                batch = copyBatch(DownloadBatch.Status.RUNNING, 0, null);
                return Optional.of(batch);
            });
            when(repository.counts(any())).thenAnswer(ignored -> counts());
            when(repository.snapshot(any())).thenAnswer(ignored -> new TaskSnapshot(Optional.of(task), counts()));
            when(repository.batches(any(), any(), anyInt(), anyInt())).thenAnswer(ignored ->
                    new Page<>(batch != null && batch.status() == DownloadBatch.Status.FAILED ? 1 : 0,
                            batch != null && batch.status() == DownloadBatch.Status.FAILED
                                    ? List.of(batch) : List.of()));
            when(commits.commit(any(), any(), any(), anyLong(), any())).thenAnswer(invocation -> {
                AdaptedBatch adapted = invocation.getArgument(2);
                long sourceRows = invocation.getArgument(3);
                batch = copyBatch(DownloadBatch.Status.SUCCEEDED, sourceRows, null);
                return new WriteCounts(adapted.rows().size(), 0);
            });
            doAnswer(invocation -> {
                batch = copyBatch(DownloadBatch.Status.FAILED, 0, invocation.getArgument(2));
                return null;
            }).when(repository).failBatch(any(), any(), any(), any());
            doAnswer(invocation -> {
                task = copyTask(invocation.getArgument(1), true, 1, task.deadlineAt(), invocation.getArgument(2));
                return null;
            }).when(repository).finishTask(any(), any(), nullable(ErrorCode.class), any());
        }

        private Counts counts() {
            if (batch == null) return new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0);
            return new Counts(1,
                    batch.status() == DownloadBatch.Status.PENDING ? 1 : 0,
                    batch.status() == DownloadBatch.Status.RUNNING ? 1 : 0,
                    batch.status() == DownloadBatch.Status.SUCCEEDED ? 1 : 0,
                    batch.status() == DownloadBatch.Status.FAILED ? 1 : 0,
                    0, batch.sourceRows(), batch.insertedRows(), batch.updatedRows());
        }

        private DownloadTask copyTask(DownloadTask.Status status, boolean planReady, int generation,
                Instant deadline, ErrorCode error) {
            return new DownloadTask(task.taskId(), task.submissionId(), task.requestHash(), task.datasetKey(),
                    task.mode(), task.params(), task.definitionHash(), task.policySnapshot(), status, planReady,
                    RUN, generation, task.version() + 1, task.requestCount(), task.runRequestCount(),
                    error == null ? null : new StoredError(error), task.createdAt(), NOW, task.queuedAt(),
                    status == DownloadTask.Status.QUEUED ? null : NOW,
                    status == DownloadTask.Status.RUNNING || status == DownloadTask.Status.QUEUED ? null : NOW,
                    deadline);
        }

        private DownloadBatch copyBatch(DownloadBatch.Status status, long sourceRows, ErrorCode error) {
            return new DownloadBatch(batch.batchId(), batch.taskId(), null, batch.batchKey(), null,
                    batch.sourceParams(), status, status == DownloadBatch.Status.PENDING ? 0 : 1,
                    status == DownloadBatch.Status.PENDING ? null : 1, sourceRows,
                    status == DownloadBatch.Status.SUCCEEDED ? sourceRows : 0, 0,
                    error == null ? null : new StoredError(error), batch.createdAt(), NOW,
                    status == DownloadBatch.Status.PENDING ? null : NOW,
                    status == DownloadBatch.Status.RUNNING || status == DownloadBatch.Status.PENDING ? null : NOW);
        }
    }
}
