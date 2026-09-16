package com.akkc.tensor.config;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.DownloadTaskCoordinator;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

/** Explicitly imported task lifecycle; production collaborator assembly belongs to its importer. */
public final class DownloadTaskConfiguration {
    @Bean("downloadTaskRunId")
    public UUID downloadTaskRunId(DatasetCatalog catalog) {
        return UUID.randomUUID();
    }

    @Bean(destroyMethod = "close")
    public DownloadTaskCoordinator downloadTaskCoordinator(DownloadTaskService tasks,
            DownloadTaskRepository repository, DownloadTaskRunner runner, Clock clock,
            @Qualifier("downloadTaskRunId") UUID runId) {
        return new DownloadTaskCoordinator(tasks, repository, runner, clock, runId);
    }

    @Bean
    public SmartLifecycle downloadTaskLifecycle(DownloadTaskCoordinator coordinator) {
        return new SmartLifecycle() {
            @Override public void start() { coordinator.start(); }
            @Override public boolean isRunning() { return coordinator.isRunning(); }
            @Override public boolean isAutoStartup() { return true; }
            @Override public int getPhase() { return Integer.MAX_VALUE; }
            @Override public void stop() { coordinator.close(); }
            @Override public void stop(Runnable callback) {
                coordinator.close();
                callback.run();
            }
        };
    }
}
