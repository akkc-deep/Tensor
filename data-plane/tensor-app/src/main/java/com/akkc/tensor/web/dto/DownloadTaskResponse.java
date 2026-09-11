package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.task.*;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Public facts from a single repository snapshot; execution permissions stay internal. */
public record DownloadTaskResponse(UUID taskId, UUID submissionId, String pluginId, String apiName,
        DownloadMode mode, Map<String, Object> params, DownloadTask.Status status, long version, boolean planReady,
        TaskCounts counts, StoredErrorResponse lastError, boolean canRetry, boolean canResume,
        long requestCount, long runRequestCount, Instant createdAt, Instant updatedAt, Instant queuedAt,
        Instant startedAt, Instant finishedAt, Instant deadlineAt) {
    public DownloadTaskResponse { params = Map.copyOf(params); }

    public static DownloadTaskResponse from(DownloadTaskRepository.TaskSnapshot snapshot,
            DownloadTaskService.ControlAvailability controls) {
        var task = snapshot.task().orElseThrow();
        return new DownloadTaskResponse(task.taskId(), task.submissionId(), task.datasetKey().pluginId().value(),
                task.datasetKey().apiName().value(), task.mode(), task.params(), task.status(), task.version(), task.planReady(),
                TaskCounts.from(snapshot.counts()), StoredErrorResponse.from(task.lastError()), controls.canRetry(), controls.canResume(),
                task.requestCount(), task.runRequestCount(), task.createdAt(), task.updatedAt(), task.queuedAt(),
                task.startedAt(), task.finishedAt(), task.deadlineAt());
    }

    public record TaskCounts(long totalBatches, long pendingBatches, long runningBatches, long succeededBatches,
            long failedBatches, long splitBatches, long sourceRows, long insertedRows, long updatedRows) {
        static TaskCounts from(DownloadTaskRepository.Counts counts) {
            return new TaskCounts(counts.totalBatches(), counts.pending(), counts.running(), counts.succeeded(),
                    counts.failed(), counts.splitBatches(), counts.sourceRows(), counts.insertedRows(), counts.updatedRows());
        }
    }

    public record StoredErrorResponse(ErrorCode code, String message) {
        public static StoredErrorResponse from(DownloadTaskRepository.StoredError error) {
            return error == null ? null : new StoredErrorResponse(error.code(), error.message());
        }
    }
}
