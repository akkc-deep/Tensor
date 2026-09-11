package com.akkc.tensor.core.download.task;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.UUID;

/**
 * Best-effort observations of confirmed commits. Callbacks may run before transaction resources and
 * dataset locks are released: implementations must be bounded, local, and never reenter task services.
 */
public interface DownloadTaskObserver {
    void taskStarted(TaskStarted event);

    void batchFinished(BatchFinished event);

    void taskFinished(TaskFinished event);

    DownloadTaskObserver NOOP = new DownloadTaskObserver() {
        @Override public void taskStarted(TaskStarted event) {}
        @Override public void batchFinished(BatchFinished event) {}
        @Override public void taskFinished(TaskFinished event) {}
    };

    record TaskStarted(UUID taskId, DatasetKey datasetKey, int runGeneration) {}

    record BatchFinished(UUID taskId, DatasetKey datasetKey, int runGeneration, UUID batchId,
            DownloadBatch.Status status, int attemptCount, long durationMs, long sourceRows,
            long insertedRows, long updatedRows, ErrorCode errorCode) {}

    record TaskFinished(UUID taskId, DatasetKey datasetKey, int runGeneration, DownloadTask.Status status,
            boolean recovered, long durationMs, long requestCount, long runRequestCount,
            DownloadTaskRepository.Counts counts, ErrorCode errorCode) {}
}
