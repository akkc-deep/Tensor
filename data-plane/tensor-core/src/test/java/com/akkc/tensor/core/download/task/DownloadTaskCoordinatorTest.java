package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class DownloadTaskCoordinatorTest {
    static final UUID RUN = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    static final DownloadTaskRunner.RunResult IDLE =
            new DownloadTaskRunner.RunResult(null, null, DownloadTaskRunner.Disposition.IDLE, null);

    @Test
    void leaseCoversSubmissionWindowAndIsReleasedOnlyAfterRunnableExits() {
        try (Harness h = new Harness()) {
            code(ErrorCode.PLUGIN_DISABLED, h.coordinator::checkAdmission);
            h.coordinator.start();
            h.coordinator.start();
            verify(h.poller, times(1)).scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1L), eq(TimeUnit.SECONDS));
            assertThat(h.coordinator.controlAllowed()).isTrue();
            h.coordinator.tick();
            h.coordinator.tick();
            assertThat(h.worker.queued()).isEqualTo(1);
            assertThat(h.coordinator.controlAllowed()).isFalse();
            UUID interrupted = UUID.randomUUID();
            when(h.repository.findTask(interrupted)).thenReturn(Optional.of(
                    task(interrupted, RUN, 1, 5, DownloadTask.Status.INTERRUPTED)));
            code(ErrorCode.TASK_STATE_CONFLICT, () -> h.tasks.resume(interrupted, 5));
            h.coordinator.checkAdmission(); // New submissions may queue while a worker is active.
            verifyNoInteractions(h.runner);
            h.worker.runOne();
            assertThat(h.coordinator.controlAllowed()).isTrue();
            h.coordinator.tick();
            h.worker.runOne();
            verify(h.runner, times(2)).runNext(any());
        }
    }

    @Test
    void startupRecoversOnlyOtherRunsAndNeverDispatchesBeforeRecoverySucceeds() {
        try (Harness h = new Harness()) {
            DownloadTask old = task(UUID.randomUUID(), UUID.randomUUID(), 3, 9, DownloadTask.Status.RUNNING);
            DownloadTask current = task(UUID.randomUUID(), RUN, 0, 1, DownloadTask.Status.QUEUED);
            when(h.repository.unfinishedTasks()).thenReturn(List.of(old, current));
            h.coordinator.start();
            verify(h.repository).recoverStoppedTask(old.taskId(), old.activeRunId(), 3, 9, NOW);
            verify(h.repository, never()).recoverStoppedTask(eq(current.taskId()), any(), anyInt(), anyLong(), any());
            assertThat(h.coordinator.isRunning()).isTrue();
            verifyNoInteractions(h.runner);
        }
    }

    @Test
    void startupFailureDoesNotOpenAdmissionOrScheduleAndCannotBeRestartedInPlace() {
        try (Harness h = new Harness()) {
            when(h.repository.unfinishedTasks()).thenThrow(new IllegalStateException("private database detail"));
            code(ErrorCode.QUERY_FAILED, h.coordinator::start);
            assertThat(h.coordinator.isRunning()).isFalse();
            code(ErrorCode.PLUGIN_DISABLED, h.coordinator::checkAdmission);
            assertThat(h.coordinator.controlAllowed()).isFalse();
            assertThatThrownBy(h.coordinator::start).isInstanceOf(IllegalStateException.class);
            h.coordinator.tick();
            verifyNoInteractions(h.runner, h.poller);
        }
    }

    @Test
    void rejectedDispatchFaultsInsteadOfRunningOnPollerOrAutomaticallyRecovering() {
        try (Harness h = new Harness()) {
            h.coordinator.start();
            h.worker.shutdown();
            code(ErrorCode.INTERNAL_ERROR, h.coordinator::tick);
            assertThat(h.coordinator.isRunning()).isTrue();
            code(ErrorCode.TASK_STATE_CONFLICT, h.coordinator::checkAdmission);
            assertThat(h.coordinator.controlAllowed()).isFalse();
            h.coordinator.tick();
            verifyNoInteractions(h.runner);
            verify(h.repository, never()).queuedCount();
            assertThatThrownBy(h.coordinator::start).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectedSchedulerClosesResourcesAndNeverOpensAdmission() {
        try (Harness h = new Harness()) {
            when(h.poller.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any()))
                    .thenThrow(new RejectedExecutionException("private"));
            code(ErrorCode.INTERNAL_ERROR, h.coordinator::start);
            assertThat(h.worker.isShutdown()).isTrue();
            verify(h.poller).shutdown();
            code(ErrorCode.TASK_STATE_CONFLICT, h.coordinator::checkAdmission);
            h.coordinator.tick();
            verifyNoInteractions(h.runner);
        }
    }

    @Test
    void unexpectedWorkerFailuresReleaseActualLeaseButPermanentlyFault() {
        for (Throwable failure : List.of(new IllegalStateException("private"), new AssertionError("private"))) {
            try (Harness h = new Harness()) {
                when(h.runner.runNext(any())).thenThrow(failure);
                h.coordinator.start();
                h.coordinator.tick();
                if (failure instanceof Error) assertThatThrownBy(h.worker::runOne).isSameAs(failure);
                else code(ErrorCode.INTERNAL_ERROR, h.worker::runOne);
                assertThat(h.coordinator.isRunning()).isTrue();
                assertThat(h.coordinator.controlAllowed()).isFalse();
                code(ErrorCode.TASK_STATE_CONFLICT, h.coordinator::checkAdmission);
                h.coordinator.tick();
                assertThat(h.worker.queued()).isZero();
                verify(h.repository, never()).queuedCount();
                verify(h.runner).runNext(any());
            }
        }
    }

    @Test
    void recoveryWaitsForDbThenUsesFreshVersionAndDefersDispatchToAnotherTick() {
        try (Harness h = new Harness()) {
            UUID id = UUID.randomUUID();
            var permit = new DownloadTaskRepository.ExecutionPermit(id, RUN, 2);
            when(h.runner.runNext(any())).thenReturn(new DownloadTaskRunner.RunResult(id, permit,
                    DownloadTaskRunner.Disposition.NEEDS_RECOVERY, ErrorCode.PERSISTENCE_FAILED));
            when(h.repository.snapshot(id)).thenThrow(new IllegalStateException("private"))
                    .thenReturn(snapshot(task(id, RUN, 2, 17, DownloadTask.Status.RUNNING)));
            h.coordinator.start();
            h.coordinator.tick();
            h.worker.runOne();
            code(ErrorCode.TASK_STATE_CONFLICT, h.coordinator::checkAdmission);
            h.coordinator.tick();
            assertThat(h.coordinator.controlAllowed()).isFalse();
            verify(h.repository, never()).recoverStoppedTask(any(), any(), anyInt(), anyLong(), any());
            h.coordinator.tick();
            verify(h.repository).recoverStoppedTask(id, RUN, 2, 17, NOW);
            assertThat(h.coordinator.controlAllowed()).isTrue();
            assertThat(h.worker.queued()).isZero();
            verify(h.runner).runNext(any());
        }
    }

    @Test
    void recoveryIgnoresGoneTerminalNewRunAndNewGenerationFacts() {
        for (int scenario = 0; scenario < 4; scenario++) {
            try (Harness h = new Harness()) {
                UUID id = UUID.randomUUID();
                var permit = new DownloadTaskRepository.ExecutionPermit(id, RUN, 2);
                when(h.runner.runNext(any())).thenReturn(new DownloadTaskRunner.RunResult(id, permit,
                        DownloadTaskRunner.Disposition.NEEDS_RECOVERY, ErrorCode.PERSISTENCE_FAILED));
                var facts = switch (scenario) {
                    case 0 -> new DownloadTaskRepository.TaskSnapshot(Optional.empty(), zeroCounts());
                    case 1 -> snapshot(task(id, RUN, 2, 17, DownloadTask.Status.SUCCEEDED));
                    case 2 -> snapshot(task(id, UUID.randomUUID(), 2, 17, DownloadTask.Status.RUNNING));
                    default -> snapshot(task(id, RUN, 3, 17, DownloadTask.Status.RUNNING));
                };
                when(h.repository.snapshot(id)).thenReturn(facts);
                h.coordinator.start(); h.coordinator.tick(); h.worker.runOne(); h.coordinator.tick();
                assertThat(h.coordinator.controlAllowed()).isTrue();
                verify(h.repository, never()).recoverStoppedTask(any(), any(), anyInt(), anyLong(), any());
            }
        }
    }

    @Test
    void unknownClaimReceiptUsesCandidateFactsButUnknownCandidateOnlyProbesDatabase() {
        for (UUID id : Arrays.asList(null, UUID.randomUUID())) {
            try (Harness h = new Harness()) {
                when(h.runner.runNext(any())).thenReturn(new DownloadTaskRunner.RunResult(id, null,
                        DownloadTaskRunner.Disposition.NEEDS_RECOVERY, ErrorCode.QUERY_FAILED));
                if (id != null) when(h.repository.snapshot(id))
                        .thenReturn(snapshot(task(id, RUN, 5, 19, DownloadTask.Status.RUNNING)));
                h.coordinator.start(); h.coordinator.tick(); h.worker.runOne(); h.coordinator.tick();
                if (id == null) {
                    verify(h.repository).queuedCount();
                    verify(h.repository, never()).snapshot(any());
                    verify(h.repository, never()).recoverStoppedTask(any(), any(), anyInt(), anyLong(), any());
                } else {
                    verify(h.repository).recoverStoppedTask(id, RUN, 5, 19, NOW);
                    verify(h.repository, never()).queuedCount();
                }
                assertThat(h.coordinator.controlAllowed()).isTrue();
                verify(h.runner).runNext(any());
            }
        }
    }

    @Test
    void recoveryWriteConflictKeepsPauseAndRereadsFactsNextTick() {
        try (Harness h = new Harness()) {
            UUID id = UUID.randomUUID();
            when(h.runner.runNext(any())).thenReturn(new DownloadTaskRunner.RunResult(id, null,
                    DownloadTaskRunner.Disposition.NEEDS_RECOVERY, ErrorCode.PERSISTENCE_FAILED));
            when(h.repository.snapshot(id)).thenReturn(snapshot(task(id, RUN, 1, 5, DownloadTask.Status.RUNNING)),
                    snapshot(task(id, RUN, 1, 6, DownloadTask.Status.RUNNING)));
            when(h.repository.recoverStoppedTask(id, RUN, 1, 5, NOW))
                    .thenThrow(new DownloadTaskService.TaskException(ErrorCode.TASK_STATE_CONFLICT));
            h.coordinator.start(); h.coordinator.tick(); h.worker.runOne(); h.coordinator.tick();
            assertThat(h.coordinator.controlAllowed()).isFalse();
            h.coordinator.tick();
            verify(h.repository).recoverStoppedTask(id, RUN, 1, 6, NOW);
            assertThat(h.coordinator.controlAllowed()).isTrue();
        }
    }

    @Test
    void finishedAndLostPermitNeverRewriteResultsAndCloseSkipsLostTask() {
        for (var disposition : List.of(DownloadTaskRunner.Disposition.FINISHED, DownloadTaskRunner.Disposition.PERMIT_LOST)) {
            try (Harness h = new Harness()) {
                UUID id = UUID.randomUUID();
                var permit = new DownloadTaskRepository.ExecutionPermit(id, RUN, 1);
                when(h.runner.runNext(any())).thenReturn(new DownloadTaskRunner.RunResult(id, permit, disposition,
                        disposition == DownloadTaskRunner.Disposition.PERMIT_LOST ? ErrorCode.TASK_STATE_CONFLICT : null));
                h.coordinator.start(); h.coordinator.tick(); h.worker.runOne();
                assertThat(h.coordinator.controlAllowed()).isTrue();
                verify(h.repository, never()).snapshot(any());
                if (disposition == DownloadTaskRunner.Disposition.PERMIT_LOST)
                    when(h.repository.unfinishedTasks()).thenReturn(List.of(task(id, RUN, 2, 10, DownloadTask.Status.RUNNING)));
                h.close();
                verify(h.repository, never()).recoverStoppedTask(any(), any(), anyInt(), anyLong(), any());
            }
        }
    }

    @Test
    void closeRecoversCurrentQueuedTasksAndIgnoresOtherRuns() {
        Harness h = new Harness();
        h.coordinator.start();
        DownloadTask queued = task(UUID.randomUUID(), RUN, 0, 1, DownloadTask.Status.QUEUED);
        DownloadTask other = task(UUID.randomUUID(), UUID.randomUUID(), 2, 9, DownloadTask.Status.RUNNING);
        when(h.repository.unfinishedTasks()).thenReturn(List.of(queued, other));
        h.close(); h.close();
        verify(h.repository).recoverStoppedTask(queued.taskId(), RUN, 0, 1, NOW);
        verify(h.repository, never()).recoverStoppedTask(eq(other.taskId()), any(), anyInt(), anyLong(), any());
        assertThat(h.coordinator.isRunning()).isFalse();
        assertThat(h.worker.isTerminated()).isTrue();
        code(ErrorCode.PLUGIN_DISABLED, h.coordinator::checkAdmission);
        assertThatThrownBy(h.coordinator::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeWaitsForActualExitWithoutInterruptingWorkerAndRestoresClosingThreadInterrupt() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (Harness h = new Harness(worker)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<BooleanSupplier> stop = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            when(h.runner.runNext(any())).thenAnswer(call -> {
                stop.set(call.getArgument(0)); entered.countDown();
                try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                finally { interrupted.set(Thread.currentThread().isInterrupted()); }
                return IDLE;
            });
            h.coordinator.start(); h.coordinator.tick();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch closed = new CountDownLatch(1);
            AtomicBoolean closeInterrupted = new AtomicBoolean();
            Thread closer = new Thread(() -> {
                Thread.currentThread().interrupt();
                h.close(); closeInterrupted.set(Thread.currentThread().isInterrupted()); closed.countDown();
            });
            closer.start();
            try {
                awaitTrue(stop.get());
                assertThat(h.coordinator.isRunning()).isFalse();
                assertThat(closed.getCount()).isEqualTo(1);
                assertThat(h.coordinator.controlAllowed()).isFalse();
                h.coordinator.tick();
                verify(h.runner).runNext(any());
            } finally { release.countDown(); }
            assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted).isFalse();
            assertThat(closeInterrupted).isTrue();
        }
    }

    @Test
    void closePreservesQueuedRunnableUntilItObservesTheStopSignal() throws Exception {
        try (Harness h = new Harness()) {
            h.coordinator.start();
            h.coordinator.tick();
            AtomicBoolean observedStop = new AtomicBoolean();
            when(h.runner.runNext(any())).thenAnswer(call -> {
                observedStop.set(((BooleanSupplier) call.getArgument(0)).getAsBoolean());
                return IDLE;
            });
            FutureTask<Void> closing = new FutureTask<>(() -> { h.close(); return null; });
            new Thread(closing, "queued-close-test").start();
            try {
                awaitTrue(h.worker::isShutdown);
                assertThat(closing.isDone()).isFalse();
                assertThat(h.worker.queued()).isEqualTo(1);
            } finally { h.worker.runOne(); }
            closing.get(5, TimeUnit.SECONDS);
            assertThat(observedStop).isTrue();
            assertThat(h.worker.isTerminated()).isTrue();
        }
    }

    @Test
    void disabledServiceDoesNotDispatchButStillRecoversOldTasks() {
        try (Harness h = new Harness(new ManualWorker(), false)) {
            h.coordinator.start(); h.coordinator.tick();
            assertThat(h.tasks.controls(task(UUID.randomUUID(), RUN, 1, 5, DownloadTask.Status.FAILED)))
                    .isEqualTo(new DownloadTaskService.ControlAvailability(false, false));
            verify(h.repository).unfinishedTasks();
            verifyNoInteractions(h.runner);
        }
    }

    @Test
    void bindingRejectsMismatchedRunIdAndDuplicateCoordinator() {
        try (Harness h = new Harness()) {
            assertThatThrownBy(() -> new DownloadTaskCoordinator(h.tasks, h.repository, h.runner,
                    Clock.fixed(NOW, ZoneOffset.UTC), UUID.randomUUID(), h.poller, h.worker))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new DownloadTaskCoordinator(h.tasks, h.repository, h.runner,
                    Clock.fixed(NOW, ZoneOffset.UTC), RUN, h.poller, h.worker))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    static void awaitTrue(BooleanSupplier value) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!value.getAsBoolean() && System.nanoTime() < until) Thread.sleep(1);
        assertThat(value.getAsBoolean()).isTrue();
    }

    static void code(ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(TensorException.class, error -> {
            assertThat(error.code()).isEqualTo(code);
            assertThat(error.getCause()).isNull();
            assertThat(error.getMessage()).doesNotContain("private");
        });
    }

    static DownloadTask task(UUID id, UUID run, int generation, long version, DownloadTask.Status status) {
        return new DownloadTask(id, UUID.randomUUID(), "hash", DatasetKey.of(PluginId.of("test"), ApiName.of("prices")),
                DownloadMode.SINGLE, Map.of(), "definition", "{}", status, false, run, generation, version,
                0, 0, null, NOW, NOW, NOW, null, null, null);
    }
    static DownloadTaskRepository.Counts zeroCounts() { return new DownloadTaskRepository.Counts(0,0,0,0,0,0,0,0,0); }
    static DownloadTaskRepository.TaskSnapshot snapshot(DownloadTask task) {
        return new DownloadTaskRepository.TaskSnapshot(Optional.of(task), zeroCounts());
    }

    static final class Harness implements AutoCloseable {
        final DownloadTaskRepository repository = mock(DownloadTaskRepository.class);
        final DownloadTaskRunner runner = mock(DownloadTaskRunner.class);
        final ScheduledExecutorService poller = mock(ScheduledExecutorService.class);
        final ManualWorker worker;
        final DownloadTaskService tasks;
        final DownloadTaskCoordinator coordinator;
        Harness() { this(new ManualWorker()); }
        Harness(ExecutorService executor) { this(executor, true); }
        Harness(ExecutorService executor, boolean enabled) {
            worker = executor instanceof ManualWorker manual ? manual : null;
            tasks = new DownloadTaskService(new PluginRegistry(List.of()), mock(DatasetCatalog.class),
                    new AdapterRegistry(List.of()), new ParameterValidator(), repository, new DownloadTaskJson(),
                    Clock.fixed(NOW, ZoneOffset.UTC), RUN, new DownloadTaskService.Settings(enabled, 100, 36600));
            when(repository.unfinishedTasks()).thenReturn(List.of());
            when(runner.runNext(any())).thenReturn(IDLE);
            try { when(poller.awaitTermination(anyLong(), any())).thenReturn(true); }
            catch (InterruptedException impossible) { throw new AssertionError(impossible); }
            coordinator = new DownloadTaskCoordinator(tasks, repository, runner,
                    Clock.fixed(NOW, ZoneOffset.UTC), RUN, poller, executor);
        }
        @Override public void close() { coordinator.close(); }
    }

    static final class ManualWorker extends AbstractExecutorService {
        final Queue<Runnable> queue = new ArrayDeque<>();
        boolean stopped;
        int running;
        @Override public synchronized void execute(Runnable command) {
            if (stopped) throw new RejectedExecutionException();
            queue.add(command);
        }
        synchronized int queued() { return queue.size(); }
        void runOne() {
            Runnable command;
            synchronized (this) { command = queue.remove(); running++; }
            try { command.run(); }
            finally { synchronized (this) { running--; notifyAll(); } }
        }
        @Override public synchronized void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { throw new AssertionError("Must not cancel actual work"); }
        @Override public synchronized boolean isShutdown() { return stopped; }
        @Override public synchronized boolean isTerminated() { return stopped && queue.isEmpty() && running == 0; }
        @Override public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            if (!isTerminated()) wait(Math.max(1, unit.toMillis(timeout)));
            return isTerminated();
        }
    }
}
