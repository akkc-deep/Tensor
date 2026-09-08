package com.akkc.tensor.core.retry;

import com.akkc.tensor.core.retry.RetryTaskRepository.Criteria;
import com.akkc.tensor.core.retry.RetryTaskRepository.Failure;
import com.akkc.tensor.core.retry.RetryTaskRepository.Header;
import com.akkc.tensor.core.retry.RetryTaskRepository.Item;
import com.akkc.tensor.core.retry.RetryTaskRepository.ItemKey;
import com.akkc.tensor.core.retry.RetryTaskRepository.Page;
import com.akkc.tensor.core.retry.RetryTaskRepository.SavedFailure;
import com.akkc.tensor.core.retry.RetryTaskRepository.Task;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class RetryTaskStorageService {
    private static final String OUTER_TRANSACTION =
            "Retry task storage must not join an existing transaction";
    private static final String LOSS = "Task parameters cannot be stored without loss";

    private final RetryTaskRepository repository;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;
    private final Clock clock;
    private final TaskParametersJson json = new TaskParametersJson();

    public RetryTaskStorageService(
            RetryTaskRepository repository, PlatformTransactionManager transactions, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        writes = template(transactions, false);
        reads = template(transactions, true);
    }

    public SavedFailure create(
            DatasetKey dataset, Map<String, Object> frozenTaskParams, Failure firstFailure) {
        requireInput(dataset);
        requireInput(firstFailure);
        Map<String, Object> params = repository.freezeTaskParams(frozenTaskParams);
        rejectOuterTransaction();
        UUID taskId = UUID.randomUUID();
        ItemKey key = new ItemKey(taskId, firstFailure.selector());
        try {
            ItemKey committed = writes.execute(status -> {
                Instant now = now();
                repository.insertHeader(new Header(taskId, dataset, params, now, now));
                if (!json.sameValue(params, repository.readTaskParams(taskId))) {
                    throw new IllegalArgumentException(LOSS);
                }
                repository.insertItem(key, firstFailure.errorCode(), now);
                return key;
            });
            return new SavedFailure(Objects.requireNonNull(committed, "transaction result"));
        } catch (DataAccessException | TransactionException exception) {
            throw saveUnconfirmed();
        }
    }

    public SavedFailure append(UUID taskId, Failure failure) {
        requireInput(taskId);
        requireInput(failure);
        rejectOuterTransaction();
        ItemKey key = new ItemKey(taskId, failure.selector());
        try {
            ItemKey committed = writes.execute(status -> {
                repository.lockTask(taskId).orElseThrow(RetryTaskStorageService::notFound);
                Instant now = now();
                repository.upsertItem(key, failure.errorCode(), now);
                repository.touchTask(taskId, now);
                return key;
            });
            return new SavedFailure(Objects.requireNonNull(committed, "transaction result"));
        } catch (DataAccessException | TransactionException exception) {
            throw saveUnconfirmed();
        }
    }

    public SavedFailure updateReason(ItemKey key, ErrorCode errorCode) {
        requireInput(key);
        RetryTaskRepository.errorMessage(errorCode);
        rejectOuterTransaction();
        try {
            ItemKey committed = writes.execute(status -> {
                repository.lockTask(key.taskId()).orElseThrow(RetryTaskStorageService::notFound);
                if (!repository.containsItem(key)) {
                    throw notFound();
                }
                Instant now = now();
                repository.updateItemReason(key, errorCode, now);
                repository.touchTask(key.taskId(), now);
                return key;
            });
            return new SavedFailure(Objects.requireNonNull(committed, "transaction result"));
        } catch (DataAccessException | TransactionException exception) {
            throw saveUnconfirmed();
        }
    }

    public Optional<Task> find(UUID taskId) {
        requireInput(taskId);
        rejectOuterTransaction();
        try {
            return Objects.requireNonNull(reads.execute(status -> repository.findHeader(taskId)
                    .map(header -> new Task(header, repository.findItems(taskId)))), "transaction result");
        } catch (DataAccessException | TransactionException exception) {
            throw queryFailed();
        }
    }

    public Page list(Criteria criteria) {
        requireInput(criteria);
        rejectOuterTransaction();
        try {
            return Objects.requireNonNull(reads.execute(status -> listSnapshot(criteria)), "transaction result");
        } catch (DataAccessException | TransactionException exception) {
            throw queryFailed();
        }
    }

    private Page listSnapshot(Criteria criteria) {
        long total = repository.count(criteria);
        if (total == 0) {
            return new Page(List.of(), 1, criteria.pageSize(), 0, 0);
        }
        long totalPages = 1 + (total - 1) / criteria.pageSize();
        int page = (int) Math.min(criteria.page(), totalPages);
        long offset = ((long) page - 1) * criteria.pageSize();
        List<Header> headers = repository.findHeaders(criteria, criteria.pageSize(), offset);
        List<UUID> taskIds = headers.stream().map(Header::taskId).toList();
        Map<UUID, List<Item>> itemsByTask = new LinkedHashMap<>();
        taskIds.forEach(taskId -> itemsByTask.put(taskId, new ArrayList<>()));
        for (Item item : repository.findItems(taskIds)) {
            List<Item> items = itemsByTask.get(item.key().taskId());
            if (items == null) {
                throw invalidSaved();
            }
            items.add(item);
        }
        List<Task> tasks = headers.stream()
                .map(header -> new Task(header, itemsByTask.get(header.taskId())))
                .toList();
        return new Page(tasks, page, criteria.pageSize(), total, totalPages);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static TransactionTemplate template(
            PlatformTransactionManager transactions, boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setTimeout(60);
        if (readOnly) {
            template.setReadOnly(true);
            template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        }
        return template;
    }

    private static void rejectOuterTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(OUTER_TRANSACTION);
        }
    }

    private static <T> T requireInput(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid retry task request");
        }
        return value;
    }

    private static SaveUnconfirmedException saveUnconfirmed() {
        return new SaveUnconfirmedException();
    }

    private static RetryTaskNotFoundException notFound() {
        return new RetryTaskNotFoundException();
    }

    private static QueryFailedException queryFailed() {
        return new QueryFailedException();
    }

    private static InvalidSavedTaskException invalidSaved() {
        return new InvalidSavedTaskException();
    }

    private static final class SaveUnconfirmedException extends TensorException {
        private SaveUnconfirmedException() {
            super(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED, "Failure record save is unconfirmed");
        }
    }

    private static final class RetryTaskNotFoundException extends TensorException {
        private RetryTaskNotFoundException() {
            super(ErrorCode.RETRY_TASK_NOT_FOUND, "Retry task was not found");
        }
    }

    private static final class QueryFailedException extends TensorException {
        private QueryFailedException() {
            super(ErrorCode.QUERY_FAILED, "Retry task query failed");
        }
    }

    private static final class InvalidSavedTaskException extends TensorException {
        private InvalidSavedTaskException() {
            super(ErrorCode.RETRY_TASK_INVALID, "Saved retry task is invalid");
        }
    }
}
