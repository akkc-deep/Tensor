package com.akkc.tensor.observability;

import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskObserver;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Best-effort local observation of accepted requests and committed task facts. */
public final class DownloadTaskOperationLogger implements DownloadTaskObserver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadTaskOperationLogger.class);

    public enum AcceptanceKind { CREATED, REPLAYED, RETRY, RESUME }

    public void recordAccepted(RequestId requestId, DownloadTask task, AcceptanceKind kind, Duration duration) {
        try {
            LOGGER.info("tensor.download_task.accepted requestId={} taskId={} pluginId={} apiName={} status={} version={} kind={} outcome={} durationMs={}",
                    requestId.value(), task.taskId(), task.datasetKey().pluginId().value(), task.datasetKey().apiName().value(),
                    task.status(), task.version(), kind, kind == AcceptanceKind.REPLAYED ? "replayed" : "accepted", duration.toMillis());
        } catch (RuntimeException ignored) {
            observationFailed();
        }
    }

    @Override
    public void taskStarted(TaskStarted event) {
        background(event.taskId(), event.datasetKey(), event.runGeneration(), null, () -> LOGGER.info(
                "tensor.download_task.started taskId={} pluginId={} apiName={} runGeneration={}",
                event.taskId(), event.datasetKey().pluginId().value(), event.datasetKey().apiName().value(), event.runGeneration()));
    }

    @Override
    public void batchFinished(BatchFinished event) {
        background(event.taskId(), event.datasetKey(), event.runGeneration(), event.batchId(), () -> LOGGER.info(
                "tensor.download_batch.finished taskId={} batchId={} pluginId={} apiName={} runGeneration={} status={} attemptCount={} durationMs={} sourceRows={} insertedRows={} updatedRows={} errorCode={}",
                event.taskId(), event.batchId(), event.datasetKey().pluginId().value(), event.datasetKey().apiName().value(),
                event.runGeneration(), event.status(), event.attemptCount(), event.durationMs(), event.sourceRows(),
                event.insertedRows(), event.updatedRows(), error(event.errorCode())));
    }

    @Override
    public void taskFinished(TaskFinished event) {
        background(event.taskId(), event.datasetKey(), event.runGeneration(), null, () -> LOGGER.info(
                "tensor.download_task.finished taskId={} pluginId={} apiName={} runGeneration={} status={} recovered={} durationMs={} requestCount={} runRequestCount={} totalBatches={} pendingBatches={} runningBatches={} succeededBatches={} failedBatches={} splitBatches={} sourceRows={} insertedRows={} updatedRows={} errorCode={}",
                event.taskId(), event.datasetKey().pluginId().value(), event.datasetKey().apiName().value(), event.runGeneration(),
                event.status(), event.recovered(), event.durationMs(), event.requestCount(), event.runRequestCount(),
                event.counts().totalBatches(), event.counts().pending(), event.counts().running(), event.counts().succeeded(),
                event.counts().failed(), event.counts().splitBatches(), event.counts().sourceRows(),
                event.counts().insertedRows(), event.counts().updatedRows(), error(event.errorCode())));
    }

    private static void background(UUID taskId, DatasetKey key, int generation, UUID batchId, Runnable log) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.clear();
            MDC.put("taskId", taskId.toString());
            MDC.put("pluginId", key.pluginId().value());
            MDC.put("apiName", key.apiName().value());
            MDC.put("runGeneration", Integer.toString(generation));
            if (batchId != null) MDC.put("batchId", batchId.toString());
            log.run();
        } catch (RuntimeException ignored) {
            observationFailed();
        } finally {
            MDC.clear();
            if (previous != null) MDC.setContextMap(previous);
        }
    }

    private static String error(ErrorCode code) { return code == null ? "none" : code.name(); }

    private static void observationFailed() {
        try {
            LOGGER.warn("tensor.observation.failed operation=download_task");
        } catch (RuntimeException ignored) {
            // Logging failure cannot change an accepted request or a committed business fact.
        }
    }
}
