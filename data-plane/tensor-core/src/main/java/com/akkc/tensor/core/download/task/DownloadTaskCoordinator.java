package com.akkc.tensor.core.download.task;

import com.akkc.tensor.core.download.task.DownloadTaskRunner.RunResult;
import com.akkc.tensor.core.download.task.DownloadTaskService.TaskException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.time.Clock;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Single-instance ownership of dispatch, manual controls and stopped-worker recovery. */
public final class DownloadTaskCoordinator implements AutoCloseable {
    private enum State { STARTING, RUNNING, RECOVERING, FAULTED, CLOSING, CLOSED }

    private final DownloadTaskService tasks;
    private final DownloadTaskRepository repository;
    private final DownloadTaskRunner runner;
    private final Clock clock;
    private final UUID activeRunId;
    private final ScheduledExecutorService poller;
    private final ExecutorService worker;
    private final ReentrantLock lock;
    private final Condition closed;
    private final Set<UUID> lostPermits = new HashSet<>();
    private State state = State.STARTING;
    private boolean startAttempted;
    private Lease active;
    private RunResult recovery;

    public DownloadTaskCoordinator(DownloadTaskService tasks, DownloadTaskRepository repository,
            DownloadTaskRunner runner, Clock clock, UUID activeRunId) {
        this(tasks, repository, runner, clock, activeRunId,
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "tensor-download-poller")),
                Executors.newSingleThreadExecutor(r -> new Thread(r, "tensor-download-worker")));
    }

    DownloadTaskCoordinator(DownloadTaskService tasks, DownloadTaskRepository repository,
            DownloadTaskRunner runner, Clock clock, UUID activeRunId,
            ScheduledExecutorService poller, ExecutorService worker) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.activeRunId = Objects.requireNonNull(activeRunId, "activeRunId");
        this.poller = Objects.requireNonNull(poller, "poller");
        this.worker = Objects.requireNonNull(worker, "worker");
        lock = tasks.coordinationLock();
        closed = lock.newCondition();
        lock.lock();
        try {
            if (!activeRunId.equals(tasks.activeRunId()))
                throw new IllegalArgumentException("Download task run IDs must match");
            tasks.bindCoordinator(this);
        } finally { lock.unlock(); }
    }

    public void start() {
        lock.lock();
        try {
            if (state == State.RUNNING || state == State.RECOVERING) return;
            if (state != State.STARTING || startAttempted)
                throw new IllegalStateException("Download task coordinator cannot restart");
            startAttempted = true;
            try {
                var unfinished = repository.unfinishedTasks();
                for (DownloadTask task : unfinished)
                    if (!task.activeRunId().equals(activeRunId)) recover(task);
            } catch (RuntimeException failure) {
                throw fixed(failure, ErrorCode.QUERY_FAILED);
            }
            state = State.RUNNING;
            try {
                poller.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
            } catch (RuntimeException rejected) {
                state = State.FAULTED;
                poller.shutdown();
                worker.shutdown();
                throw new TaskException(ErrorCode.INTERNAL_ERROR);
            }
        } finally { lock.unlock(); }
    }

    public boolean isRunning() {
        lock.lock();
        try { return state == State.RUNNING || state == State.RECOVERING || state == State.FAULTED; }
        finally { lock.unlock(); }
    }

    // Service calls these under the same reentrant admission lock.
    void checkAdmission() {
        lock.lock();
        try {
            if (state == State.RECOVERING || state == State.FAULTED)
                throw new TaskException(ErrorCode.TASK_STATE_CONFLICT);
            if (state != State.RUNNING) throw new TaskException(ErrorCode.PLUGIN_DISABLED);
        } finally { lock.unlock(); }
    }

    boolean controlAllowed() {
        lock.lock();
        try { return state == State.RUNNING && active == null; }
        finally { lock.unlock(); }
    }

    void requeueValidated(UUID taskId) {
        // Service has verified a terminal state under this lock, even if requeue later loses its receipt.
        lostPermits.remove(taskId);
    }

    void tick() {
        lock.lock();
        try {
            if (active != null || state == State.CLOSING || state == State.CLOSED
                    || state == State.STARTING || state == State.FAULTED) return;
            if (state == State.RECOVERING) {
                recoverResult();
                return;
            }
            if (!tasks.settings().enabled()) return;
            Lease lease = new Lease();
            active = lease; // Own the queued runnable before execute can start it.
            try {
                worker.execute(() -> run(lease));
            } catch (RuntimeException rejected) {
                active = null;
                state = State.FAULTED;
                throw new TaskException(ErrorCode.INTERNAL_ERROR);
            }
        } finally { lock.unlock(); }
    }

    private void run(Lease lease) {
        RunResult result = null;
        try {
            result = Objects.requireNonNull(runner.runNext(lease.stop::get));
        } catch (RuntimeException unexpected) {
            throw new TaskException(ErrorCode.INTERNAL_ERROR);
        } finally {
            lock.lock();
            try {
                if (result != null && result.disposition() == DownloadTaskRunner.Disposition.PERMIT_LOST)
                    lostPermits.add(result.taskId());
                if (active == lease) active = null;
                if (state != State.CLOSING && state != State.CLOSED) {
                    if (result == null) state = State.FAULTED;
                    else if (result.disposition() == DownloadTaskRunner.Disposition.NEEDS_RECOVERY) {
                        recovery = result;
                        state = State.RECOVERING;
                    }
                }
            } finally { lock.unlock(); }
        }
    }

    private void recoverResult() {
        try {
            if (recovery.taskId() == null) {
                repository.queuedCount(); // A failed candidate read has no task identity to recover.
            } else {
                var snapshot = repository.snapshot(recovery.taskId());
                if (snapshot.task().isPresent()) {
                    DownloadTask task = snapshot.task().get();
                    if (unfinished(task) && activeRunId.equals(task.activeRunId())
                            && (recovery.permit() == null
                                    || recovery.permit().runGeneration() == task.runGeneration()))
                        recover(task);
                }
            }
            recovery = null;
            state = State.RUNNING;
        } catch (RuntimeException unavailable) {
            // Keep only the original fixed RunResult; retry database facts on the next tick.
        }
    }

    private void recover(DownloadTask task) {
        try {
            repository.recoverStoppedTask(task.taskId(), task.activeRunId(), task.runGeneration(),
                    task.version(), clock.instant());
        } catch (RuntimeException failure) {
            throw fixed(failure, ErrorCode.PERSISTENCE_FAILED);
        }
    }

    @Override public void close() {
        lock.lock();
        try {
            if (state == State.CLOSING) {
                while (state != State.CLOSED) closed.awaitUninterruptibly();
                return;
            }
            if (state == State.CLOSED) return;
            state = State.CLOSING;
            if (active != null) active.stop.set(true);
        } finally { lock.unlock(); }

        // Never wait for the worker while owning its finally/admission lock.
        poller.shutdown();
        worker.shutdown();
        boolean interrupted = awaitTermination(worker);
        interrupted |= awaitTermination(poller);
        lock.lock();
        try {
            try {
                for (DownloadTask task : repository.unfinishedTasks())
                    if (activeRunId.equals(task.activeRunId()) && !lostPermits.contains(task.taskId())) recover(task);
            } catch (RuntimeException unavailable) {
                // Next process startup will recover any facts we could not persist during shutdown.
            } finally {
                state = State.CLOSED;
                closed.signalAll();
            }
        } finally {
            lock.unlock();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static boolean awaitTermination(ExecutorService executor) {
        boolean interrupted = false;
        for (;;) {
            try {
                if (executor.awaitTermination(1, TimeUnit.DAYS)) return interrupted;
            } catch (InterruptedException ignored) { interrupted = true; }
        }
    }

    private static boolean unfinished(DownloadTask task) {
        return task.status() == DownloadTask.Status.QUEUED || task.status() == DownloadTask.Status.RUNNING;
    }

    private static TaskException fixed(RuntimeException failure, ErrorCode fallback) {
        return new TaskException(failure instanceof TensorException classified ? classified.code() : fallback);
    }

    private static final class Lease {
        final AtomicBoolean stop = new AtomicBoolean();
    }
}
