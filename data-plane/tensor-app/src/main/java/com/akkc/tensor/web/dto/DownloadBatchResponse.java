package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.task.DownloadBatch;
import com.akkc.tensor.web.dto.DownloadTaskResponse.StoredErrorResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record DownloadBatchResponse(UUID batchId, UUID parentBatchId, String batchKey,
        LocalDate rangeStart, LocalDate rangeEnd, Map<String, Object> sourceParams, DownloadBatch.Status status,
        int attemptCount, long sourceRows, long insertedRows, long updatedRows, StoredErrorResponse error,
        Instant startedAt, Instant finishedAt, Instant createdAt, Instant updatedAt) {
    public DownloadBatchResponse { sourceParams = Map.copyOf(sourceParams); }

    public static DownloadBatchResponse from(DownloadBatch batch) {
        return new DownloadBatchResponse(batch.batchId(), batch.parentBatchId(), batch.batchKey(),
                batch.range() == null ? null : batch.range().start(), batch.range() == null ? null : batch.range().end(),
                batch.sourceParams(), batch.status(), batch.attemptCount(), batch.sourceRows(), batch.insertedRows(), batch.updatedRows(),
                StoredErrorResponse.from(batch.error()), batch.startedAt(), batch.finishedAt(), batch.createdAt(), batch.updatedAt());
    }
}
