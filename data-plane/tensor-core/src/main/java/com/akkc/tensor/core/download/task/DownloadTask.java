package com.akkc.tensor.core.download.task;

import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.model.DatasetKey;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DownloadTask(
        UUID taskId,
        UUID submissionId,
        String requestHash,
        DatasetKey datasetKey,
        DownloadMode mode,
        Map<String, Object> params,
        String definitionHash,
        String policySnapshot,
        Status status,
        boolean planReady,
        UUID activeRunId,
        int runGeneration,
        long version,
        long requestCount,
        long runRequestCount,
        DownloadTaskRepository.StoredError lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        Instant deadlineAt) {
    public DownloadTask {
        params = Map.copyOf(params);
    }

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        PARTIAL_FAILED,
        FAILED,
        INTERRUPTED
    }
}
