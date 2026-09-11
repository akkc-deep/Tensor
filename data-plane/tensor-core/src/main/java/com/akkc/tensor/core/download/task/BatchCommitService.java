package com.akkc.tensor.core.download.task;

import com.akkc.tensor.core.download.task.DownloadTaskRepository.ExecutionPermit;
import com.akkc.tensor.core.persistence.PersistenceParticipant;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class BatchCommitService {
    private final PersistenceService persistence;
    private final DownloadTaskRepository repository;
    private final Clock clock;

    public BatchCommitService(PersistenceService persistence, DownloadTaskRepository repository, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WriteCounts commit(ExecutionPermit permit, UUID batchId, AdaptedBatch batch, long sourceRows) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Batch commit must own its transaction");
        }
        Objects.requireNonNull(permit, "permit");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(batch, "batch");
        if (sourceRows < 0) throw new IllegalArgumentException("sourceRows must be non-negative");

        DownloadTask task = repository.findTask(permit.taskId())
                .orElseThrow(() -> new CommitException(ErrorCode.TASK_STATE_CONFLICT));
        if (task.status() != DownloadTask.Status.RUNNING
                || !task.activeRunId().equals(permit.activeRunId())
                || task.runGeneration() != permit.runGeneration()) {
            throw new CommitException(ErrorCode.TASK_STATE_CONFLICT);
        }
        validateWrite(task, batch);
        try {
            return persistence.persist(batch, new PersistenceParticipant() {
                @Override
                public void beforeWrite() {
                    DownloadTask lockedTask = repository.lockTask(permit);
                    repository.lockBatch(permit, batchId);
                    validateWrite(lockedTask, batch);
                }

                @Override
                public void afterWrite(WriteCounts counts) {
                    repository.succeedBatch(permit, batchId, sourceRows,
                            counts.insertedRows(), counts.updatedRows(), clock.instant());
                }
            });
        } catch (DataAccessException | TransactionException failure) {
            throw new CommitException(ErrorCode.PERSISTENCE_FAILED);
        } catch (IllegalArgumentException failure) {
            throw new CommitException(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    private void validateWrite(DownloadTask task, AdaptedBatch batch) {
        if (!task.datasetKey().equals(batch.datasetKey())) {
            throw new CommitException(ErrorCode.DATASET_MISCONFIGURED);
        }
        if (task.deadlineAt() == null || !clock.instant().isBefore(task.deadlineAt())) {
            throw new CommitException(ErrorCode.TASK_LIMIT_EXCEEDED);
        }
    }

    private static final class CommitException extends TensorException {
        private CommitException(ErrorCode code) {
            super(code, new DownloadTaskRepository.StoredError(code).message());
        }
    }
}
