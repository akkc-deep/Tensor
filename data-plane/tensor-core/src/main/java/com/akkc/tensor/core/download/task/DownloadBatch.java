package com.akkc.tensor.core.download.task;

import com.akkc.tensor.plugin.api.download.batch.DateRange;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DownloadBatch(
        UUID batchId,
        UUID taskId,
        UUID parentBatchId,
        String batchKey,
        DateRange range,
        Map<String, Object> sourceParams,
        Status status,
        int attemptCount,
        Integer runGeneration,
        long sourceRows,
        long insertedRows,
        long updatedRows,
        DownloadTaskRepository.StoredError error,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt) {
    public DownloadBatch {
        sourceParams = Map.copyOf(sourceParams);
    }

    public enum Status {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        SPLIT
    }
}
