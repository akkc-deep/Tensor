package com.akkc.tensor.core.download;

import com.akkc.tensor.core.download.RecoveryUnitProcessor.ReadyUnit;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.core.retry.RetryTaskRepository;
import com.akkc.tensor.core.retry.RetryTaskRepository.Header;
import com.akkc.tensor.core.retry.RetryTaskRepository.ItemKey;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.download.CalendarDecision;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Commits one validated unit; its caller owns subsequent index confirmation and execution totals. */
public final class BatchCommitService {
    private final PersistenceService persistence;
    private final RetryTaskRepository tasks;
    private final DownloadParameterConverter converter;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public BatchCommitService(PersistenceService persistence, RetryTaskRepository tasks,
            DownloadParameterConverter converter, PlatformTransactionManager transactions, Clock clock) {
        this.persistence = required(persistence);
        this.tasks = required(tasks);
        this.converter = required(converter);
        this.transactions = new TransactionTemplate(required(transactions));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transactions.setTimeout(60);
        this.clock = required(clock);
    }

    public CommitResult commitInitial(ReadyUnit ready) {
        preflight();
        required(ready).index().checkConfirmable(ready);
        return execute(ready.selector(), ready.sourceRowCount(), () -> persistence.persist(ready.batch()));
    }

    public CommitResult commitRetry(ItemKey item, ReadyUnit ready) {
        preflight();
        required(item);
        required(ready).index().checkConfirmable(ready);
        if (!item.selector().equals(ready.selector())) throw invalid();
        return execute(ready.selector(), ready.sourceRowCount(), () -> {
            lockItem(item, ready.batch().datasetKey());
            WriteCounts counts = persistence.persist(ready.batch());
            cleanup(item);
            return counts;
        });
    }

    public CommitResult commitClosedRetry(DatasetKey dataset, ApiDescriptor api, ItemKey item,
            CalendarDecision decision) {
        preflight();
        required(dataset);
        required(api);
        required(item);
        if (!dataset.apiName().equals(api.apiName())
                || api.downloadPolicy().mode() != DownloadPolicy.Mode.TRADE_DATE_RANGE) throw invalid();
        Set<LocalDate> dates = dates(item.selector());
        if (decision == null || !decision.scope().dates().containsAll(dates)
                || decision.calendars().values().stream().anyMatch(calendar ->
                        dates.stream().anyMatch(date -> !Boolean.FALSE.equals(calendar.get(date))))) {
            throw new CalendarUnconfirmedException();
        }
        return execute(item.selector(), 0, () -> {
            Header header = lockItem(item, dataset);
            if (!decision.scope().publicParams().equals(header.taskParams())) throw new CalendarUnconfirmedException();
            converter.mapRetry(api, header.taskParams(), item.selector());
            cleanup(item);
            return new WriteCounts(0, 0);
        });
    }

    private Header lockItem(ItemKey item, DatasetKey dataset) {
        Header header = tasks.lockTask(item.taskId()).orElseThrow(BatchCommitService::notFound);
        if (!header.datasetKey().equals(dataset)) throw invalid();
        if (!tasks.containsItem(item)) throw notFound();
        return header;
    }

    private void cleanup(ItemKey item) {
        if (tasks.deleteItem(item) != 1) throw cleanupFailed();
        boolean remaining = tasks.hasItems(item.taskId());
        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (remaining) tasks.touchTask(item.taskId(), now);
        else if (tasks.deleteEmptyTask(item.taskId()) != 1) throw cleanupFailed();
    }

    private CommitResult execute(RecoverySelector selector, long sourceRows, Supplier<WriteCounts> work) {
        Observation observation = new Observation();
        RuntimeException failure = null;
        try {
            transactions.executeWithoutResult(status -> {
                observation.entered = true;
                if (!TransactionSynchronizationManager.isActualTransactionActive()
                        || !TransactionSynchronizationManager.isSynchronizationActive()) {
                    throw new IllegalStateException("Recovery unit transaction synchronization is not active");
                }
                TransactionSynchronizationManager.registerSynchronization(observation);
                observation.candidate = required(work.get());
            });
        } catch (RuntimeException exception) {
            failure = exception;
        }
        if (observation.completion == TransactionSynchronization.STATUS_COMMITTED && observation.candidate != null) {
            return new Committed(selector, sourceRows, observation.candidate, failure != null);
        }
        boolean storageFailure = failure instanceof DataAccessException || failure instanceof TransactionException;
        if (!observation.entered && failure != null) {
            if (storageFailure) return new Unavailable(selector);
            throw failure;
        }
        if (observation.completion == TransactionSynchronization.STATUS_ROLLED_BACK
                && !observation.commitPhaseSeen && failure != null) {
            if (storageFailure) return new RolledBack(selector, storageUnavailable(failure));
            throw failure;
        }
        return new Unconfirmed(selector);
    }

    private static boolean storageUnavailable(RuntimeException failure) {
        if (failure instanceof TransactionException) return true;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Throwable>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof DataAccessResourceFailureException || current instanceof RecoverableDataAccessException
                    || current instanceof CannotCreateTransactionException || current instanceof SQLTransientConnectionException
                    || current instanceof SQLNonTransientConnectionException || current instanceof SQLRecoverableException) return true;
            if (current instanceof SQLException sql) {
                if (sql.getSQLState() != null && sql.getSQLState().startsWith("08")) return true;
                if (sql.getNextException() != null) pending.add(sql.getNextException());
            }
            if (current.getCause() != null) pending.add(current.getCause());
        }
        return false;
    }

    private static Set<LocalDate> dates(RecoverySelector selector) {
        return switch (selector.timeType()) {
            case DATE -> Set.of(LocalDate.parse(selector.timeValue()));
            case RANGE -> {
                String[] endpoints = selector.timeValue().split("/");
                LocalDate start = LocalDate.parse(endpoints[0]), end = LocalDate.parse(endpoints[1]);
                yield start.datesUntil(end.plusDays(1)).collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
            case NONE, MONTH -> throw invalid();
        };
    }

    private static void preflight() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Recovery unit commit must not join an existing transaction");
        }
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Recovery unit commit input is required");
        return value;
    }
    private static TensorException notFound() {
        return new SavedTaskException(ErrorCode.RETRY_TASK_NOT_FOUND, "Retry task was not found");
    }
    private static TensorException invalid() {
        return new SavedTaskException(ErrorCode.RETRY_TASK_INVALID, "Saved retry task is invalid");
    }
    private static DataRetrievalFailureException cleanupFailed() {
        return new DataRetrievalFailureException("Retry task cleanup did not affect the expected row");
    }

    private static final class SavedTaskException extends TensorException {
        SavedTaskException(ErrorCode code, String message) { super(code, message); }
    }

    private static final class Observation implements TransactionSynchronization {
        boolean entered;
        boolean commitPhaseSeen;
        int completion = -1;
        WriteCounts candidate;
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
        @Override public void beforeCommit(boolean readOnly) { commitPhaseSeen = true; }
        @Override public void afterCompletion(int status) { completion = status; }
    }

    public sealed interface CommitResult permits Committed, RolledBack, Unconfirmed, Unavailable {
        RecoverySelector selector();
    }
    public record Committed(RecoverySelector selector, long sourceRowCount, WriteCounts writeCounts,
            boolean stopExecution) implements CommitResult {
        public Committed {
            required(selector);
            required(writeCounts);
            if (sourceRowCount < 0) throw new IllegalArgumentException("Source row count must be non-negative");
        }
    }
    public record RolledBack(RecoverySelector selector, boolean storageUnavailable) implements CommitResult {
        public RolledBack { required(selector); }
    }
    public record Unconfirmed(RecoverySelector selector) implements CommitResult {
        public Unconfirmed { required(selector); }
    }
    public record Unavailable(RecoverySelector selector) implements CommitResult {
        public Unavailable { required(selector); }
    }
}
