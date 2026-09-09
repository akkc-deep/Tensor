package com.akkc.tensor.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.QueryCriteria;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

class OperationLoggerTest {
    private static final String REQUEST_ID = "89a09af7-e54b-440b-9e46-ff7aa2184b1a";
    private static final String SECRET = "m09-t06-token-password-secret";
    private static final DatasetKey KNOWN =
            DatasetKey.of(PluginId.of("test_plugin"), ApiName.of("daily"));
    private static final RequestId REQUEST = new RequestId(UUID.fromString(REQUEST_ID));
    private static final DownloadResult DOWNLOAD_SUCCESS = new DownloadResult(
            REQUEST, DownloadOutcome.SUCCESS, KNOWN.pluginId(), KNOWN.apiName(),
            5, 2, 3, "下载成功");
    private static final DownloadResult DOWNLOAD_EMPTY = new DownloadResult(
            REQUEST, DownloadOutcome.EMPTY, KNOWN.pluginId(), KNOWN.apiName(),
            0, 0, 0, "下载成功，0 条数据");
    private static final QueryCriteria CRITERIA = new QueryCriteria(
            "000001.SZ", LocalDate.of(2026, 8, 7), null,
            null, LocalDate.of(2026, 8, 8), 9, 20);
    private static final DatasetPage PAGE = new DatasetPage(
            List.of("ts_code"),
            List.of(Map.of("ts_code", "000001.SZ"), Map.of("ts_code", "000002.SZ")),
            1, 20, 2, 1);
    private static final DatasetPage EMPTY_PAGE = new DatasetPage(
            List.of("ts_code"), List.of(), 1, 20, 0, 0);

    @Test
    void executionObservationsKeepEveryOutcomeAndConfirmedSubtotalWithoutSensitiveDetails() {
        for (var outcome : com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.values()) {
            var subjects = subjects();
            var result = execution(outcome);
            try (CapturedLog log = capturedLog()) {
                subjects.logger().recordDownloadExecution(result, Duration.ofMillis(13));
                assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                    assertThat(event.getFormattedMessage()).contains("outcome=" + outcome, "requestId=" + REQUEST_ID,
                            "completedUnits=" + result.completedUnits(), "insertedRows=" + result.insertedRows(), "failureRecordStatus=" + result.failureRecordStatus());
                    assertThat(event.getFormattedMessage()).doesNotContain(SECRET, "failure-message-secret", "outcome=success", "taskParams");
                    assertThat(event.getThrowableProxy()).isNull();
                });
            }
            String metric = switch (outcome) { case SUCCESS -> "success"; case EMPTY, NO_OPEN_DATES -> "empty"; default -> "failure"; };
            assertThat(subjects.registry().get("tensor_download_total").tags("outcome", metric).counter().count()).isEqualTo(1);
            assertThat(subjects.registry().getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).noneMatch(tag -> tag.getKey().equals("taskId") || tag.getKey().equals("requestId")));
            if (metric.equals("failure")) assertThat(subjects.registry().find("tensor_download_rows_total").counters()).isEmpty();
        }
    }

    @Test
    void failedMetricsCannotPreventSafeExecutionLogOrChangeReturnedBusinessFacts() {
        var plugins = new PluginRegistry(List.of());
        var metrics = mock(TensorMetrics.class);
        doThrow(new IllegalStateException(SECRET)).when(metrics).recordDownload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        var logger = new OperationLogger(plugins, metrics);
        try (CapturedLog log = capturedLog()) {
            assertThatNoException().isThrownBy(() -> logger.recordDownloadExecution(execution(com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.PARTIAL), Duration.ZERO));
            assertThat(completionEvents(log)).hasSize(1);
            assertThat(log.appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(SECRET));
        }
    }

    private static com.akkc.tensor.core.download.DownloadExecutionResult execution(com.akkc.tensor.core.download.DownloadExecutionResult.Outcome outcome) {
        boolean failed = outcome == com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.PARTIAL || outcome == com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.FAILED;
        long completed = outcome == com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.FAILED || outcome == com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.NO_OPEN_DATES ? 0 : 1;
        long rows = completed == 0 || outcome == com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.EMPTY ? 0 : 3;
        var scope = new com.akkc.tensor.plugin.api.download.RecoverySelector(com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType.REQUEST, "", com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.DATE, "2026-09-03");
        return new com.akkc.tensor.core.download.DownloadExecutionResult(REQUEST, outcome, KNOWN.pluginId(), KNOWN.apiName(), rows, rows == 0 ? 0 : 2, rows == 0 ? 0 : 1,
                SECRET, completed, failed ? 1 : 0, 0L, outcome == com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.NO_OPEN_DATES ? 1 : 0,
                failed ? UUID.randomUUID() : null, failed ? 1L : 0L,
                outcome == com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.UNCONFIRMED ? com.akkc.tensor.core.download.DownloadExecutionResult.FailureRecordStatus.UNCONFIRMED
                        : failed ? com.akkc.tensor.core.download.DownloadExecutionResult.FailureRecordStatus.CONFIRMED : com.akkc.tensor.core.download.DownloadExecutionResult.FailureRecordStatus.NOT_REQUIRED,
                failed ? List.of(new com.akkc.tensor.core.download.RecoveryUnitProcessor.Failure(scope, com.akkc.tensor.plugin.api.error.ErrorCode.SOURCE_TIMEOUT, "failure-message-secret")) : List.of(), List.of(), List.of());
    }

    @Test
    void recordsDownloadSuccessOnce() {
        Subjects subjects = subjects();
        try (CapturedLog log = capturedLog()) {
            subjects.logger().recordDownloadSuccess(
                    REQUEST, KNOWN, Map.of("trade_date", "20260807"),
                    DOWNLOAD_SUCCESS, Duration.ofMillis(37));

            assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).startsWith("tensor.operation.completed").contains(
                        "requestId=" + REQUEST_ID,
                        "operation=download",
                        "pluginId=test_plugin",
                        "apiName=daily",
                        "paramSummary=[trade_date]",
                        "sourceRowCount=5",
                        "insertedRows=2",
                        "updatedRows=3",
                        "durationMs=37",
                        "outcome=success",
                        "failureStage=none",
                        "errorCode=none");
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
        assertThat(subjects.registry().get("tensor_download_total")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(subjects.registry().get("tensor_download_duration_seconds")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
                .timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(37.0);
        assertThat(subjects.registry().get("tensor_download_rows_total")
                .tags("plugin", "test_plugin", "api", "daily", "kind", "source")
                .counter().count()).isEqualTo(5.0);
        assertThat(subjects.registry().get("tensor_download_rows_total")
                .tags("plugin", "test_plugin", "api", "daily", "kind", "inserted")
                .counter().count()).isEqualTo(2.0);
        assertThat(subjects.registry().get("tensor_download_rows_total")
                .tags("plugin", "test_plugin", "api", "daily", "kind", "updated")
                .counter().count()).isEqualTo(3.0);
    }

    @Test
    void recordsDownloadEmptyOnce() {
        Subjects subjects = subjects();
        try (CapturedLog log = capturedLog()) {
            subjects.logger().recordDownloadSuccess(
                    REQUEST, KNOWN, Map.of(), DOWNLOAD_EMPTY, Duration.ofMillis(11));

            assertThat(completionEvents(log)).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).contains(
                            "sourceRowCount=0", "insertedRows=0", "updatedRows=0",
                            "durationMs=11", "outcome=empty",
                            "failureStage=none", "errorCode=none"));
        }
        assertThat(subjects.registry().get("tensor_download_total")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "empty")
                .counter().count()).isEqualTo(1.0);
        for (String kind : List.of("source", "inserted", "updated")) {
            assertThat(subjects.registry().get("tensor_download_rows_total")
                    .tags("plugin", "test_plugin", "api", "daily", "kind", kind)
                    .counter().count()).isZero();
        }
    }

    @Test
    void recordsQuerySuccessFromCoreInputs() {
        Subjects subjects = subjects();
        try (CapturedLog log = capturedLog()) {
            subjects.logger().recordQuerySuccess(
                    REQUEST, KNOWN, CRITERIA, PAGE, Duration.ofMillis(23));

            assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains(
                        "requestId=" + REQUEST_ID,
                        "operation=query",
                        "pluginId=test_plugin",
                        "apiName=daily",
                        "filterNames=[ts_code, trade_date, ann_date]",
                        "page=1", "pageSize=20", "resultCount=2", "totalElements=2",
                        "durationMs=23", "outcome=success",
                        "failureStage=none", "errorCode=none")
                        .doesNotContain("000001.SZ", "2026-08");
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
        assertThat(subjects.registry().get("tensor_query_total")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(subjects.registry().get("tensor_query_duration_seconds")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
                .timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(23.0);
    }

    @Test
    void recordsEmptyQueryAsSuccess() {
        Subjects subjects = subjects();
        try (CapturedLog log = capturedLog()) {
            subjects.logger().recordQuerySuccess(
                    REQUEST, KNOWN,
                    new QueryCriteria(null, null, null, null, null, 99, 20),
                    EMPTY_PAGE, Duration.ZERO);

            assertThat(completionEvents(log)).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).contains(
                            "filterNames=[]", "page=1", "pageSize=20",
                            "resultCount=0", "totalElements=0", "durationMs=0",
                            "outcome=success"));
        }
        assertThat(subjects.registry().get("tensor_query_total")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void removesSensitiveParameterNamesAndAllValuesFromCompletionEvents() {
        Subjects subjects = subjects();
        LinkedHashMap<String, Object> unsafe = new LinkedHashMap<>();
        unsafe.put("trade_date", SECRET);
        unsafe.put("ts_code", SECRET);
        unsafe.put("token", SECRET);
        unsafe.put("authorization", SECRET);
        unsafe.put("cookie", SECRET);
        unsafe.put("db_password", SECRET);
        unsafe.put("credential", SECRET);

        try (CapturedLog log = capturedLog()) {
            subjects.logger().recordDownloadSuccess(
                    REQUEST, KNOWN, unsafe, DOWNLOAD_SUCCESS, Duration.ofMillis(5));

            assertThat(completionEvents(log)).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage())
                            .contains("paramSummary=[trade_date, ts_code]")
                            .doesNotContain(SECRET, "token", "authorization", "cookie",
                                    "db_password", "credential"));
        }
    }

    @Test
    void isolatesDownloadMetricFailureAndKeepsCompletionLog() {
        PluginRegistry plugins = plugins();
        TensorMetrics metrics = mock(TensorMetrics.class);
        when(metrics.supports(KNOWN)).thenReturn(true);
        doThrow(new IllegalStateException(SECRET)).when(metrics).recordDownload(
                KNOWN, TensorMetrics.Outcome.SUCCESS, Duration.ofMillis(7), 5, 2, 3);

        try (CapturedLog log = capturedLog()) {
            assertThatNoException().isThrownBy(() -> new OperationLogger(plugins, metrics)
                    .recordDownloadSuccess(
                            REQUEST, KNOWN, Map.of(), DOWNLOAD_SUCCESS, Duration.ofMillis(7)));
            assertThat(completionEvents(log)).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).contains(
                            "operation=download", "durationMs=7", "outcome=success"));
            assertThat(observationFailures(log)).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage())
                            .isEqualTo("tensor.observation.failed operation=download")
                            .doesNotContain(SECRET));
        }
    }

    @Test
    void isolatesQueryMetricFailureAndKeepsCompletionLog() {
        PluginRegistry plugins = plugins();
        TensorMetrics metrics = mock(TensorMetrics.class);
        when(metrics.supports(KNOWN)).thenReturn(true);
        doThrow(new IllegalStateException(SECRET)).when(metrics).recordQuery(
                KNOWN, TensorMetrics.Outcome.SUCCESS, Duration.ofMillis(7));

        try (CapturedLog log = capturedLog()) {
            assertThatNoException().isThrownBy(() -> new OperationLogger(plugins, metrics)
                    .recordQuerySuccess(REQUEST, KNOWN, CRITERIA, PAGE, Duration.ofMillis(7)));
            assertThat(completionEvents(log)).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).contains(
                            "operation=query", "durationMs=7", "outcome=success"));
            assertThat(observationFailures(log)).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage())
                            .isEqualTo("tensor.observation.failed operation=query")
                            .doesNotContain(SECRET));
        }
    }

    @Test
    void isolatesSupportsFailure() {
        PluginRegistry plugins = plugins();
        TensorMetrics metrics = mock(TensorMetrics.class);
        when(metrics.supports(KNOWN)).thenThrow(new IllegalStateException(SECRET));

        try (CapturedLog log = capturedLog()) {
            assertThatNoException().isThrownBy(() -> new OperationLogger(plugins, metrics)
                    .recordQuerySuccess(REQUEST, KNOWN, CRITERIA, PAGE, Duration.ofMillis(13)));
            assertThat(completionEvents(log)).isEmpty();
            assertThat(observationFailures(log)).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage())
                            .isEqualTo("tensor.observation.failed operation=query")
                            .doesNotContain(SECRET));
        }
    }

    @Test
    void isolatesLoggingBackendFailures() {
        Subjects subjects = subjects();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        TurboFilter failure = new TurboFilter() {
            @Override
            public FilterReply decide(
                    Marker marker,
                    Logger logger,
                    Level level,
                    String format,
                    Object[] params,
                    Throwable throwable) {
                throw new IllegalStateException(SECRET);
            }
        };
        failure.start();
        context.addTurboFilter(failure);
        try {
            assertThatNoException().isThrownBy(() -> subjects.logger().recordQuerySuccess(
                    REQUEST, KNOWN, CRITERIA, PAGE, Duration.ofMillis(3)));
        } finally {
            context.getTurboFilterList().remove(failure);
            failure.stop();
        }
    }

    @Test
    void exposesOnlyTheFrozenSuccessMeterSchema() {
        Subjects subjects = subjects();
        subjects.logger().recordDownloadSuccess(
                REQUEST, KNOWN, Map.of(), DOWNLOAD_SUCCESS, Duration.ofMillis(1));
        subjects.logger().recordDownloadSuccess(
                REQUEST, KNOWN, Map.of(), DOWNLOAD_EMPTY, Duration.ofMillis(1));
        subjects.logger().recordQuerySuccess(
                REQUEST, KNOWN, CRITERIA, PAGE, Duration.ofMillis(1));

        assertThat(subjects.registry().getMeters()).extracting(meter -> meter.getId().getName())
                .containsOnly("tensor_download_total", "tensor_download_duration_seconds",
                        "tensor_download_rows_total", "tensor_query_total",
                        "tensor_query_duration_seconds");
        for (Meter meter : subjects.registry().getMeters()) {
            String name = meter.getId().getName();
            assertThat(meter.getId().getType()).isEqualTo(
                    name.contains("duration") ? Meter.Type.TIMER : Meter.Type.COUNTER);
            Map<String, String> tags = meter.getId().getTags().stream()
                    .collect(java.util.stream.Collectors.toMap(Tag::getKey, Tag::getValue));
            assertThat(tags.get("plugin")).isEqualTo("test_plugin");
            assertThat(tags.get("api")).isEqualTo("daily");
            if (name.equals("tensor_download_rows_total")) {
                assertThat(tags.keySet()).containsExactlyInAnyOrder("plugin", "api", "kind");
                assertThat(tags.get("kind")).isIn("source", "inserted", "updated");
            } else {
                assertThat(tags.keySet()).containsExactlyInAnyOrder("plugin", "api", "outcome");
                assertThat(tags.get("outcome")).isIn("success", "empty");
                if (name.startsWith("tensor_query")) {
                    assertThat(tags.get("outcome")).isEqualTo("success");
                }
            }
        }
    }

    private static Subjects subjects() {
        PluginRegistry plugins = plugins();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TensorMetrics metrics = new TensorMetrics(registry, plugins);
        return new Subjects(registry, new OperationLogger(plugins, metrics));
    }

    private static PluginRegistry plugins() {
        return new PluginRegistry(List.of(new TestPlugin()));
    }

    private static List<ILoggingEvent> completionEvents(CapturedLog log) {
        return log.appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .filter(event -> event.getFormattedMessage().startsWith("tensor.operation.completed"))
                .toList();
    }

    private static List<ILoggingEvent> observationFailures(CapturedLog log) {
        return log.appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().startsWith("tensor.observation.failed"))
                .toList();
    }

    private static CapturedLog capturedLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(OperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new CapturedLog(logger, appender);
    }

    private record Subjects(SimpleMeterRegistry registry, OperationLogger logger) {
    }

    private record CapturedLog(
            Logger logger, ListAppender<ILoggingEvent> appender) implements AutoCloseable {
        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static final class TestPlugin implements DataSourcePlugin {
        private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
                KNOWN.pluginId(), "Test plugin", "Test data source", true, true, true, null,
                List.of(new ApiDescriptor(
                        KNOWN.apiName(), "Daily", "Market", QueryMode.trade_date,
                        List.of(parameter("start_date", ParameterType.DATE), parameter("end_date", ParameterType.DATE)), com.akkc.tensor.test.DownloadPolicies.tradeRange(), List.of(
                                parameter("trade_date", ParameterType.DATE),
                                parameter("ts_code", ParameterType.TS_CODE),
                                parameter("token", ParameterType.TEXT),
                                parameter("authorization", ParameterType.TEXT),
                                parameter("cookie", ParameterType.TEXT),
                                parameter("db_password", ParameterType.TEXT),
                                parameter("credential", ParameterType.TEXT)))),
                List.of(KNOWN));

        @Override
        public PluginDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public PluginReadiness readiness() {
            return new PluginReadiness(true, true, true, null);
        }

        @Override
        public FetchResult download(ApiName apiName, Map<String, Object> params, DownloadContext context) {
            throw new UnsupportedOperationException("logger tests do not call plugins");
        }

        private static ParameterDescriptor parameter(String name, ParameterType type) {
            return new ParameterDescriptor(
                    name, name, null, type, false, null, List.of(), null, null);
        }
    }
}
