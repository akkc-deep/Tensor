package com.akkc.tensor.core.download.task;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Historical database facts, independent of current plugin availability. */
public final class DownloadTaskQueryService {
    private final DownloadTaskRepository repository;

    public DownloadTaskQueryService(DownloadTaskRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public DownloadTaskRepository.Page<DownloadTask> tasks(
            DownloadTaskRepository.TaskFilter filter, int page, int pageSize) {
        return query(() -> repository.tasks(filter, page, pageSize));
    }

    public Optional<DownloadTask> findSubmission(UUID submissionId) {
        return query(() -> repository.findSubmission(identity(submissionId)));
    }

    public DownloadTaskRepository.TaskSnapshot detail(UUID taskId) {
        return query(() -> {
            var snapshot = repository.snapshot(identity(taskId));
            if (snapshot.task().isEmpty()) throw new DownloadTaskService.TaskException(ErrorCode.TASK_NOT_FOUND);
            return snapshot;
        });
    }

    public DownloadTaskRepository.Page<DownloadBatch> batches(
            UUID taskId, DownloadTaskRepository.BatchFilter filter, int page, int pageSize) {
        return query(() -> {
            if (repository.findTask(identity(taskId)).isEmpty())
                throw new DownloadTaskService.TaskException(ErrorCode.TASK_NOT_FOUND);
            return repository.batches(taskId, filter, page, pageSize);
        });
    }

    private static UUID identity(UUID id) {
        if (id == null) throw new IllegalArgumentException();
        return id;
    }

    private static <T> T query(Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Task queries require their own transaction");
        try {
            return action.get();
        } catch (IllegalArgumentException invalid) {
            throw new DownloadTaskService.TaskException(ErrorCode.PARAM_INVALID);
        }
    }
}
