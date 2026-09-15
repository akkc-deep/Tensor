package com.akkc.tensor.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.akkc.tensor.core.download.task.DownloadBatch;
import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskObserver.BatchFinished;
import com.akkc.tensor.core.download.task.DownloadTaskObserver.TaskFinished;
import com.akkc.tensor.core.download.task.DownloadTaskObserver.TaskStarted;
import com.akkc.tensor.core.download.task.DownloadTaskRepository.Counts;
import com.akkc.tensor.observability.DownloadTaskOperationLogger.AcceptanceKind;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class DownloadTaskOperationLoggerTest {
    private static final DatasetKey KEY = DatasetKey.of(PluginId.of("test_plugin"), ApiName.of("daily"));
    private static final String SECRET = "token-password-jdbc:mysql://private-body";
    private static final UUID TASK_ID = UUID.randomUUID();
    private final DownloadTaskOperationLogger operations = new DownloadTaskOperationLogger();

    @AfterEach
    void clearContext() { MDC.clear(); }

    @Test
    void acceptanceDistinguishesReplaysAndControlsWithoutLoggingTheTaskOrParameters() {
        DownloadTask task = task();
        RequestId request = new RequestId(UUID.randomUUID());
        try (CapturedLog log = capture()) {
            for (AcceptanceKind kind : AcceptanceKind.values()) {
                operations.recordAccepted(request, task, kind, Duration.ofMillis(17));
            }
            assertThat(log.appender.list).hasSize(4).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).startsWith("tensor.download_task.accepted ")
                        .contains("requestId=" + request.value(), "taskId=" + TASK_ID,
                                "pluginId=test_plugin", "apiName=daily", "status=QUEUED", "version=1", "durationMs=17")
                        .doesNotContain(SECRET, "params=", "requestHash=", "tensor.operation.completed", "outcome=success");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(log.appender.list.get(1).getFormattedMessage()).contains("outcome=replayed");
            assertThat(log.appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("kind=RETRY"))
                    .anyMatch(message -> message.contains("kind=RESUME"));
        }
    }

    @Test
    void backgroundEventsUseOnlyTheirOwnMdcAndRestoreTheCallerContext() {
        UUID batch = UUID.randomUUID();
        Map<String, String> caller = Map.of("requestId", SECRET, "taskId", "previous", "credential", SECRET);
        MDC.setContextMap(caller);
        try (CapturedLog log = capture()) {
            operations.taskStarted(new TaskStarted(TASK_ID, KEY, 2));
            operations.batchFinished(new BatchFinished(TASK_ID, KEY, 2, batch, DownloadBatch.Status.SUCCEEDED,
                    1, 23, 5, 3, 2, null));
            operations.taskFinished(new TaskFinished(TASK_ID, KEY, 2, DownloadTask.Status.PARTIAL_FAILED, true,
                    41, 7, 3, new Counts(3, 0, 0, 2, 1, 1, 5, 3, 2), ErrorCode.SOURCE_TIMEOUT));
            assertThat(MDC.getCopyOfContextMap()).isEqualTo(caller);
            assertThat(log.appender.list).hasSize(3).allSatisfy(event -> {
                assertThat(event.getMDCPropertyMap()).containsAllEntriesOf(Map.of(
                        "taskId", TASK_ID.toString(), "pluginId", "test_plugin", "apiName", "daily", "runGeneration", "2"))
                        .doesNotContainKeys("requestId", "credential");
                assertThat(event.getFormattedMessage()).doesNotContain(SECRET, "requestId");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(log.appender.list.get(0).getFormattedMessage()).startsWith("tensor.download_task.started ");
            assertThat(log.appender.list.get(0).getMDCPropertyMap()).hasSize(4).doesNotContainKey("batchId");
            assertThat(log.appender.list.get(1).getFormattedMessage()).startsWith("tensor.download_batch.finished ")
                    .contains("status=SUCCEEDED", "attemptCount=1", "durationMs=23", "sourceRows=5", "insertedRows=3", "updatedRows=2", "errorCode=none");
            assertThat(log.appender.list.get(1).getMDCPropertyMap()).hasSize(5).containsEntry("batchId", batch.toString());
            assertThat(log.appender.list.get(2).getFormattedMessage()).startsWith("tensor.download_task.finished ")
                    .contains("status=PARTIAL_FAILED", "recovered=true", "requestCount=7", "runRequestCount=3", "totalBatches=3",
                            "succeededBatches=2", "failedBatches=1", "splitBatches=1", "errorCode=SOURCE_TIMEOUT");
        }
    }

    @Test
    void consecutiveTasksNeverRetainPreviousTaskOrBatchIds() {
        UUID nextTask = UUID.randomUUID();
        try (CapturedLog log = capture()) {
            operations.batchFinished(new BatchFinished(TASK_ID, KEY, 1, UUID.randomUUID(), DownloadBatch.Status.SPLIT,
                    1, 0, 0, 0, 0, null));
            operations.taskStarted(new TaskStarted(nextTask, KEY, 1));
            assertThat(log.appender.list.getLast().getMDCPropertyMap()).containsEntry("taskId", nextTask.toString())
                    .doesNotContainKey("batchId");
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }
    }

    @Test
    void appenderAndFallbackFailuresCannotEscapeOrLeaveMdcBehind() {
        Logger logger = (Logger) LoggerFactory.getLogger(DownloadTaskOperationLogger.class);
        ListAppender<ILoggingEvent> failure = new ListAppender<>() {
            @Override public void doAppend(ILoggingEvent event) { throw new IllegalStateException(SECRET); }
        };
        failure.start();
        logger.addAppender(failure);
        Map<String, String> caller = Map.of("requestId", "original-request");
        MDC.setContextMap(caller);
        try {
            assertThatNoException().isThrownBy(() -> operations.taskStarted(new TaskStarted(TASK_ID, KEY, 1)));
            assertThatNoException().isThrownBy(() -> operations.batchFinished(new BatchFinished(TASK_ID, KEY, 1,
                    UUID.randomUUID(), DownloadBatch.Status.FAILED, 1, 11, 0, 0, 0, ErrorCode.SOURCE_TIMEOUT)));
            assertThatNoException().isThrownBy(() -> operations.taskFinished(new TaskFinished(TASK_ID, KEY, 1,
                    DownloadTask.Status.FAILED, false, 11, 1, 1,
                    new Counts(1, 0, 0, 0, 1, 0, 0, 0, 0), ErrorCode.SOURCE_TIMEOUT)));
            assertThatNoException().isThrownBy(() -> operations.recordAccepted(new RequestId(UUID.randomUUID()), task(),
                    AcceptanceKind.CREATED, Duration.ZERO));
            assertThat(MDC.getCopyOfContextMap()).isEqualTo(caller);
        } finally {
            logger.detachAppender(failure);
            failure.stop();
        }
        try (CapturedLog log = capture()) {
            operations.taskStarted(new TaskStarted(UUID.randomUUID(), KEY, 1));
            assertThat(log.appender.list).hasSize(1);
            assertThat(log.appender.list.getFirst().getMDCPropertyMap()).doesNotContainKey("requestId");
        }
    }

    private static DownloadTask task() {
        Instant now = Instant.parse("2026-09-12T00:00:00Z");
        return new DownloadTask(TASK_ID, UUID.randomUUID(), SECRET, KEY, DownloadMode.SINGLE,
                Map.of("token", SECRET), SECRET, SECRET, DownloadTask.Status.QUEUED, false,
                UUID.randomUUID(), 1, 1, 0, 0, null, now, now, now, null, null, null);
    }

    private static CapturedLog capture() {
        Logger logger = (Logger) LoggerFactory.getLogger(DownloadTaskOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        return new CapturedLog(logger, appender);
    }

    private record CapturedLog(Logger logger, ListAppender<ILoggingEvent> appender) implements AutoCloseable {
        @Override public void close() { logger.detachAppender(appender); appender.stop(); }
    }
}
