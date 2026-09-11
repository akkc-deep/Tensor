package com.akkc.tensor.config;

import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tensor.download-tasks")
public record DownloadTaskProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") int maxQueuedTasks,
        @DefaultValue("36600") int maxRangeDays,
        @DefaultValue("10000") int maxBatchNodes,
        @DefaultValue("5000") long maxRequestsPerRun,
        @DefaultValue("30m") Duration maxRunDuration,
        @DefaultValue("1000000") long maxSourceRowsPerTask) {
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
