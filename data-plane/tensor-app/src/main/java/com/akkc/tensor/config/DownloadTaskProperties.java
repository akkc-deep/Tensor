package com.akkc.tensor.config;

import com.akkc.tensor.core.download.task.DownloadTaskConstants;
import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.plugin.api.constant.StringConstants;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tensor.download-tasks")
public record DownloadTaskProperties(
        @DefaultValue(StringConstants.TRUE) boolean enabled,
        @DefaultValue(StringConstants.EMPTY + DownloadTaskConstants.DEFAULT_MAX_QUEUED_TASKS) int maxQueuedTasks,
        @DefaultValue(StringConstants.EMPTY + DownloadTaskConstants.DEFAULT_MAX_RANGE_DAYS) int maxRangeDays,
        @DefaultValue(StringConstants.EMPTY + DownloadTaskConstants.DEFAULT_MAX_BATCH_NODES) int maxBatchNodes,
        @DefaultValue(StringConstants.EMPTY + DownloadTaskConstants.DEFAULT_MAX_REQUESTS_PER_RUN) long maxRequestsPerRun,
        @DefaultValue(DownloadTaskConstants.DEFAULT_MAX_RUN_DURATION_MINUTES + "m") Duration maxRunDuration,
        @DefaultValue(StringConstants.EMPTY + DownloadTaskConstants.DEFAULT_MAX_SOURCE_ROWS_PER_TASK) long maxSourceRowsPerTask) {
    public DownloadTaskProperties {
        new DownloadTaskService.Settings(enabled, maxQueuedTasks, maxRangeDays);
        new DownloadTaskRunner.Settings(enabled, maxRangeDays, maxBatchNodes, maxRequestsPerRun,
                maxRunDuration, maxSourceRowsPerTask);
    }

    public DownloadTaskService.Settings toServiceSettings() {
        return new DownloadTaskService.Settings(enabled, maxQueuedTasks, maxRangeDays);
    }

    public DownloadTaskRunner.Settings toRunnerSettings() {
        return new DownloadTaskRunner.Settings(enabled, maxRangeDays, maxBatchNodes, maxRequestsPerRun,
                maxRunDuration, maxSourceRowsPerTask);
    }
}
