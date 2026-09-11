package com.akkc.tensor.core.download.task;

import com.akkc.tensor.core.download.task.DownloadTaskRepository.*;
import com.akkc.tensor.core.download.task.DownloadTaskService.ExecutionDefinition;
import com.akkc.tensor.plugin.api.BatchDownloadSupport;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.PlanningMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Synchronous, single-owner execution. Scheduling and recovery belong to the coordinator. */
public final class DownloadTaskRunner {
    private final DownloadTaskService tasks;
    private final DownloadTaskRepository repository;
    private final BatchCommitService commits;
    private final DownloadTaskJson json;
    private final Clock clock;
    private final UUID activeRunId;
    private final Settings settings;
    private final AtomicBoolean active = new AtomicBoolean();

    public DownloadTaskRunner(DownloadTaskService tasks, DownloadTaskRepository repository,
            BatchCommitService commits, DownloadTaskJson json, Clock clock, UUID activeRunId, Settings settings) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.commits = Objects.requireNonNull(commits, "commits");
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.activeRunId = Objects.requireNonNull(activeRunId, "activeRunId");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public record Settings(boolean enabled, int maxRangeDays, int maxBatchNodes, long maxRequestsPerRun,
            Duration maxRunDuration, long maxSourceRowsPerTask) {
        public Settings {
            boolean valid;
            try {
                valid = maxRangeDays > 0 && maxBatchNodes > 0 && maxBatchNodes <= 999999
                        && maxRequestsPerRun > 0 && maxSourceRowsPerTask > 0
                        && maxRunDuration != null && maxRunDuration.toMillis() >= 1;
            } catch (ArithmeticException invalid) {
                valid = false;
            }
            if (!valid) throw new IllegalArgumentException("Invalid download runner settings");
        }
        public static Settings defaults() { return new Settings(true, 36600, 10000, 5000, Duration.ofMinutes(30), 1000000); }
    }

    public enum Disposition { IDLE, FINISHED, NEEDS_RECOVERY, PERMIT_LOST }

    public record RunResult(UUID taskId, ExecutionPermit permit, Disposition disposition, ErrorCode error) {
        public RunResult {
            Objects.requireNonNull(disposition, "disposition");
            boolean valid = permit == null || permit.taskId().equals(taskId);
            valid &= switch (disposition) {
                case IDLE -> taskId == null && permit == null && error == null;
                case FINISHED -> taskId != null && permit != null;
                case PERMIT_LOST -> taskId != null && permit != null && error == ErrorCode.TASK_STATE_CONFLICT;
                case NEEDS_RECOVERY -> error != null;
            };
            if (!valid) throw new IllegalArgumentException("Invalid download run result");
        }
    }

    public RunResult runNext(BooleanSupplier stopRequested) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Download runner must not run in a transaction");
        Objects.requireNonNull(stopRequested, "stopRequested");
        if (!active.compareAndSet(false, true)) throw new IllegalStateException("Download runner is already active");
        try {
            return new Run(stopRequested).execute();
        } finally {
            // A cancelled Future does not release this guard: only the actual call's return does.
            active.set(false);
        }
    }

    private final class Run implements BatchCallContext {
        private final BooleanSupplier stop;
        private final Thread owner = Thread.currentThread();
        private UUID taskId;
        private DownloadTask task;
        private ExecutionPermit permit;
        private DownloadBatch current;
        private BatchDownloadDescriptor policy;
        private DateRange requested;

        Run(BooleanSupplier stop) { this.stop = stop; }
        @Override public Instant deadline() { return task.deadlineAt(); }
        @Override public boolean stopRequested() { return stop.getAsBoolean() || owner.isInterrupted() || Thread.currentThread().isInterrupted(); }
        @Override public void beforeRequest() {
            control();
            write(() -> repository.reserveRequest(permit, settings.maxRequestsPerRun(), clock.instant()));
            control();
        }

        RunResult execute() {
            try {
                if (!settings.enabled() || stopRequested()) return idle();
                List<DownloadTask> candidates = read(() -> repository.queuedTasks(activeRunId, 1));
                if (candidates.isEmpty() || stopRequested()) return idle();
                taskId = candidates.getFirst().taskId();
                Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
                Instant end;
                try { end = now.plus(settings.maxRunDuration()).truncatedTo(ChronoUnit.MILLIS); }
                catch (DateTimeException | ArithmeticException invalid) {
                    return result(Disposition.NEEDS_RECOVERY, ErrorCode.TASK_LIMIT_EXCEEDED);
                }
                if (stopRequested()) return idle();
                Optional<DownloadTask> claimed = store(false, () -> repository.claimTask(taskId, activeRunId, now, end));
                if (claimed.isEmpty()) return idle();
                task = claimed.get();
                permit = new ExecutionPermit(taskId, task.activeRunId(), task.runGeneration());
                ErrorCode stopped = null;
                try {
                    ExecutionDefinition definition = definition();
                    control();
                    if (task.mode() == DownloadMode.RANGE) readPolicy();
                    budget(read(() -> repository.counts(taskId)), 0, 0);
                    if (!task.planReady()) plan(definition);
                    while (true) {
                        List<DownloadBatch> pending = read(() -> repository.pendingBatches(taskId));
                        if (pending.isEmpty()) break;
                        try {
                            control();
                            definition = definition();
                            DownloadBatch next = pending.getFirst();
                            validateSaved(next);
                            current = store(false, () -> repository.claimBatch(permit, next.batchId(), clock.instant()))
                                    .orElseThrow(() -> halt(Disposition.PERMIT_LOST, ErrorCode.TASK_STATE_CONFLICT));
                            executeBatch(definition);
                            current = null;
                        } catch (TensorException failure) {
                            ErrorCode code = handle(failure);
                            if (code != null) { stopped = code; break; }
                        }
                    }
                } catch (TensorException failure) {
                    stopped = handle(failure);
                }
                return finish(stopped);
            } catch (Halt failure) {
                return result(failure.disposition, failure.code());
            } catch (TensorException failure) {
                // No permit is available if claiming itself was rejected.
                return result(Disposition.NEEDS_RECOVERY, failure.code());
            }
        }

        private ExecutionDefinition definition() {
            ExecutionDefinition definition = call(() -> tasks.executionDefinition(task), ErrorCode.DATASET_MISCONFIGURED);
            var readiness = call(() -> definition.plugin().readiness(), ErrorCode.DATASET_MISCONFIGURED);
            require(readiness != null, ErrorCode.DATASET_MISCONFIGURED);
            require(readiness.downloadAvailable(), ErrorCode.PLUGIN_DISABLED);
            return definition;
        }

        private void readPolicy() {
            try {
                policy = json.readRangePolicy(task.policySnapshot());
                String start = (String) task.params().get(policy.startParameter());
                String end = (String) task.params().get(policy.endParameter());
                if (start == null || end == null || !start.matches("[0-9]{8}") || !end.matches("[0-9]{8}"))
                    throw new IllegalArgumentException();
                requested = new DateRange(LocalDate.parse(start, DateTimeFormatter.BASIC_ISO_DATE),
                        LocalDate.parse(end, DateTimeFormatter.BASIC_ISO_DATE));
            } catch (RuntimeException invalid) {
                throw error(ErrorCode.DATASET_MISCONFIGURED);
            }
            require(days(requested) <= settings.maxRangeDays(), ErrorCode.TASK_LIMIT_EXCEEDED);
        }

        private void plan(ExecutionDefinition definition) {
            List<NewBatch> roots = new ArrayList<>();
            if (task.mode() == DownloadMode.SINGLE) {
                roots.add(new NewBatch(UUID.randomUUID(), "000001", null, task.params()));
            } else {
                var source = (BatchDownloadSupport) definition.plugin();
                List<DateRange> planned = call(() -> source.plan(task.datasetKey().apiName(), task.params(), this), ErrorCode.INTERNAL_ERROR);
                control();
                validatePlan(planned);
                for (int i = 0; i < planned.size(); i++) {
                    control();
                    roots.add(newBatch(source, String.format(Locale.ROOT, "%06d", i + 1), planned.get(i)));
                }
            }
            definition();
            control();
            write(() -> repository.savePlan(permit, roots, settings.maxBatchNodes(), clock.instant()));
        }

        private void validatePlan(List<DateRange> planned) {
            require(planned != null, ErrorCode.DATASET_MISCONFIGURED);
            require(planned.size() <= settings.maxBatchNodes(), ErrorCode.TASK_LIMIT_EXCEEDED);
            LocalDate previous = null;
            for (DateRange range : planned) {
                control();
                require(range != null, ErrorCode.DATASET_MISCONFIGURED);
                contained(range);
                if (policy.planningMode() != PlanningMode.NATIVE_RANGE) {
                    require(range.start().equals(range.end()) && (previous == null || previous.isBefore(range.start())),
                            ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
                    if (policy.planningMode() == PlanningMode.CALENDAR_DAYS && previous != null)
                        require(ChronoUnit.DAYS.between(previous, range.start()) == 1, ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
                }
                previous = range.end();
            }
            if (policy.planningMode() == PlanningMode.NATIVE_RANGE)
                require(planned.size() == 1 && planned.getFirst().equals(requested), ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
            if (policy.planningMode() == PlanningMode.CALENDAR_DAYS)
                require(planned.size() == days(requested) && !planned.isEmpty()
                        && planned.getFirst().start().equals(requested.start()) && planned.getLast().end().equals(requested.end()),
                        ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
        }

        private void validateSaved(DownloadBatch batch) {
            if (policy != null) {
                require(batch.range() != null, ErrorCode.DATASET_MISCONFIGURED);
                contained(batch.range());
                require(policy.planningMode() == PlanningMode.NATIVE_RANGE || batch.range().start().equals(batch.range().end()),
                        ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
            } else require(batch.range() == null, ErrorCode.DATASET_MISCONFIGURED);
            call(() -> json.writeBatchParams(batch.sourceParams()), ErrorCode.DATASET_MISCONFIGURED);
        }

        private NewBatch newBatch(BatchDownloadSupport source, String key, DateRange range) {
            control();
            Map<String, Object> params = call(() -> source.sourceParameters(task.datasetKey().apiName(), task.params(), range),
                    ErrorCode.DATASET_MISCONFIGURED);
            call(() -> json.writeBatchParams(params), ErrorCode.DATASET_MISCONFIGURED);
            return call(() -> new NewBatch(UUID.randomUUID(), key, range, params), ErrorCode.DATASET_MISCONFIGURED);
        }

        private void executeBatch(ExecutionDefinition definition) {
            DownloadEnvelope envelope;
            if (definition.plugin() instanceof BatchDownloadSupport source) {
                envelope = call(() -> source.downloadBatch(task.datasetKey().apiName(), current.sourceParams(), this), ErrorCode.INTERNAL_ERROR);
            } else {
                beforeRequest();
                control();
                var source = definition.plugin();
                envelope = call(() -> source.download(task.datasetKey().apiName(), current.sourceParams()), ErrorCode.INTERNAL_ERROR);
            }
            control();
            definition = definition();
            validateEnvelope(definition, envelope);
            if (policy != null) {
                var source = (BatchDownloadSupport) definition.plugin();
                BatchAssessment assessment = call(() -> source.assess(task.datasetKey().apiName(), current.range(), envelope), ErrorCode.INTERNAL_ERROR);
                require(assessment != null, ErrorCode.DATASET_MISCONFIGURED);
                require(assessment != BatchAssessment.UNKNOWN, ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
                if (assessment == BatchAssessment.SPLIT_REQUIRED) {
                    require(policy.splittable() && policy.planningMode() == PlanningMode.NATIVE_RANGE
                            && current.range().start().isBefore(current.range().end()), ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
                    budget(read(() -> repository.counts(taskId)), 2, 0);
                    List<DateRange> halves = DateRangePlanner.split(current.range());
                    NewBatch left = newBatch(source, current.batchKey() + "/0", halves.get(0));
                    NewBatch right = newBatch(source, current.batchKey() + "/1", halves.get(1));
                    definition();
                    control();
                    write(() -> repository.split(permit, current.batchId(), left, right, settings.maxBatchNodes(), clock.instant()));
                    return;
                }
            }
            budget(read(() -> repository.counts(taskId)), 0, envelope.rowCount());
            ExecutionDefinition verified = definition;
            AdaptedBatch adapted = call(() -> verified.adapter().adapt(envelope, clock.instant()), ErrorCode.INTERNAL_ERROR);
            require(adapted != null && task.datasetKey().equals(adapted.datasetKey()), ErrorCode.DATASET_MISCONFIGURED);
            control();
            definition();
            control();
            try {
                commits.commit(permit, current.batchId(), adapted, envelope.rowCount(), this::stopRequested);
            } catch (TensorException failure) {
                if (failure.code() != ErrorCode.PERSISTENCE_FAILED) throw failure;
                // Only a successful RUNNING -> FAILED proves this particular commit did not succeed.
                try { write(() -> repository.failBatch(permit, current.batchId(), ErrorCode.PERSISTENCE_FAILED, clock.instant())); }
                catch (TensorException unclear) { throw halt(Disposition.NEEDS_RECOVERY, unclear.code()); }
            } catch (RuntimeException unclear) {
                throw halt(Disposition.NEEDS_RECOVERY, ErrorCode.PERSISTENCE_FAILED);
            }
        }

        private void validateEnvelope(ExecutionDefinition definition, DownloadEnvelope envelope) {
            require(envelope != null && envelope.status() == DownloadStatus.SUCCESS, ErrorCode.SOURCE_PAYLOAD_INVALID);
            require(task.datasetKey().pluginId().equals(envelope.pluginId()) && task.datasetKey().apiName().equals(envelope.apiName())
                    && current.sourceParams().equals(envelope.params()), ErrorCode.SOURCE_RANGE_MISMATCH);
            require(definition.dataset().columns().stream().map(column -> column.name()).toList().equals(envelope.fields())
                    && envelope.rowCount() == envelope.data().size(), ErrorCode.SOURCE_PAYLOAD_INVALID);
            for (List<Object> row : envelope.data()) {
                control();
                require(row != null && row.size() == envelope.fields().size(), ErrorCode.SOURCE_PAYLOAD_INVALID);
            }
        }

        private ErrorCode handle(TensorException failure) {
            if (failure instanceof Halt halt) throw halt;
            ErrorCode code = failure.code();
            if (code == ErrorCode.EXECUTION_INTERRUPTED || code == ErrorCode.QUERY_FAILED || code == ErrorCode.PERSISTENCE_FAILED)
                throw halt(Disposition.NEEDS_RECOVERY, code);
            if (code == ErrorCode.TASK_STATE_CONFLICT) throw halt(Disposition.PERMIT_LOST, code);
            boolean hadBatch = current != null;
            if (hadBatch) {
                write(() -> repository.failBatch(permit, current.batchId(), code, clock.instant()));
                current = null;
            }
            return hadBatch && switch (code) {
                case SOURCE_UNAVAILABLE, SOURCE_NETWORK_ERROR, SOURCE_TIMEOUT, SOURCE_PAYLOAD_INVALID, SOURCE_RANGE_MISMATCH,
                        ADAPTER_FIELD_MISSING, ADAPTER_TYPE_INVALID, BATCH_COMPLETENESS_UNCONFIRMED -> true;
                default -> false;
            } ? null : code;
        }

        private RunResult finish(ErrorCode stopped) {
            TaskSnapshot snapshot = read(() -> repository.snapshot(taskId));
            DownloadTask saved = snapshot.task().orElseThrow(() -> halt(Disposition.PERMIT_LOST, ErrorCode.TASK_STATE_CONFLICT));
            if (saved.status() != DownloadTask.Status.RUNNING || !permit.activeRunId().equals(saved.activeRunId())
                    || permit.runGeneration() != saved.runGeneration()) throw halt(Disposition.PERMIT_LOST, ErrorCode.TASK_STATE_CONFLICT);
            Counts c = snapshot.counts();
            if (c.running() != 0) throw halt(Disposition.NEEDS_RECOVERY, ErrorCode.INTERNAL_ERROR);
            boolean success = saved.planReady() && c.succeeded() == c.totalBatches();
            ErrorCode code = success ? null : stopped;
            if (!success && code == null) {
                List<DownloadBatch> failed = read(() -> repository.batches(taskId, new BatchFilter(DownloadBatch.Status.FAILED, false), 1, 20)).items();
                code = failed.isEmpty() || failed.getFirst().error() == null ? ErrorCode.INTERNAL_ERROR : failed.getFirst().error().code();
            }
            DownloadTask.Status target = success ? DownloadTask.Status.SUCCEEDED : c.succeeded() > 0
                    ? DownloadTask.Status.PARTIAL_FAILED : DownloadTask.Status.FAILED;
            ErrorCode finalCode = code;
            write(() -> repository.finishTask(permit, target, finalCode, clock.instant()));
            return result(Disposition.FINISHED, code);
        }

        private void budget(Counts c, int extraNodes, long extraRows) {
            require(c.totalBatches() <= (long) settings.maxBatchNodes() - extraNodes - c.splitBatches()
                    && c.sourceRows() <= settings.maxSourceRowsPerTask()
                    && extraRows <= settings.maxSourceRowsPerTask() - c.sourceRows(), ErrorCode.TASK_LIMIT_EXCEEDED);
        }
        private void contained(DateRange range) {
            require(!range.start().isBefore(requested.start()) && !range.end().isAfter(requested.end()), ErrorCode.SOURCE_RANGE_MISMATCH);
        }
        private void control() {
            require(!stopRequested(), ErrorCode.EXECUTION_INTERRUPTED);
            require(task.deadlineAt() != null && clock.instant().isBefore(task.deadlineAt()), ErrorCode.TASK_LIMIT_EXCEEDED);
        }
        private <T> T read(Supplier<T> action) { return store(true, action); }
        private void write(Runnable action) { store(false, () -> { action.run(); return null; }); }
        private <T> T store(boolean reading, Supplier<T> action) {
            try { return action.get(); }
            catch (TensorException failure) {
                if (failure.code() == ErrorCode.QUERY_FAILED || failure.code() == ErrorCode.PERSISTENCE_FAILED)
                    throw halt(Disposition.NEEDS_RECOVERY, failure.code());
                if (failure.code() == ErrorCode.TASK_STATE_CONFLICT && permit != null)
                    throw halt(Disposition.PERMIT_LOST, failure.code());
                throw failure;
            } catch (RuntimeException failure) {
                throw halt(Disposition.NEEDS_RECOVERY, reading ? ErrorCode.QUERY_FAILED : ErrorCode.PERSISTENCE_FAILED);
            }
        }
        private RunResult result(Disposition disposition, ErrorCode error) { return new RunResult(taskId, permit, disposition, error); }
    }

    private static RunResult idle() { return new RunResult(null, null, Disposition.IDLE, null); }
    private static long days(DateRange range) { return ChronoUnit.DAYS.between(range.start(), range.end()) + 1; }
    private static <T> T call(Supplier<T> action, ErrorCode unexpected) {
        try { return action.get(); }
        catch (TensorException failure) { throw failure; }
        catch (RuntimeException failure) { throw error(unexpected); }
    }
    private static void require(boolean condition, ErrorCode code) { if (!condition) throw error(code); }
    private static TensorException error(ErrorCode code) { return new DownloadTaskService.TaskException(code); }
    private static Halt halt(Disposition disposition, ErrorCode code) { return new Halt(disposition, code); }
    private static final class Halt extends TensorException {
        private final Disposition disposition;
        Halt(Disposition disposition, ErrorCode code) {
            super(code, new StoredError(code).message());
            this.disposition = disposition;
        }
    }
}
