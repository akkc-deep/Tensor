package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.time.Instant;
import java.util.UUID;

public record DownloadTaskReceipt(UUID requestId, UUID taskId, DownloadTask.Status status, long version, Instant createdAt) {
    public static DownloadTaskReceipt from(RequestId requestId, DownloadTask task) {
        return new DownloadTaskReceipt(requestId.value(), task.taskId(), task.status(), task.version(), task.createdAt());
    }
}
