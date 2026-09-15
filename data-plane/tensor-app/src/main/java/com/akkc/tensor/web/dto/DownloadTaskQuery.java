package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.UUID;

public final class DownloadTaskQuery {
    private DownloadTaskQuery() {}
    public record Tasks(int page, int pageSize, DownloadTaskRepository.TaskFilter filter) {}
    public record Batches(UUID taskId, int page, int pageSize, DownloadTaskRepository.BatchFilter filter) {}
    public record TaskId(UUID value) {}
    public record Dataset(DatasetKey key) {}
    public record NoQuery() {}
}
