package com.akkc.tensor.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.akkc.tensor.config.WebSecurityHeadersConfiguration;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.RequestIdFilter;
import com.akkc.tensor.web.dto.DownloadResponse;
import com.akkc.tensor.web.dto.PageResponse;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.boot.env.YamlPropertySourceLoader;

class ObservabilityTest {
    private static final String REQUEST_ID =
            "89a09af7-e54b-440b-9e46-ff7aa2184b1a";
    private static final String SECRET = "m09-t06-token-password-secret";
    private static final DatasetKey KNOWN =
            DatasetKey.of(PluginId.of("test_plugin"), ApiName.of("daily"));
    private static final DownloadResponse DOWNLOAD_SUCCESS = new DownloadResponse(
            REQUEST_ID,
            DownloadOutcome.SUCCESS,
            "test_plugin",
            "daily",
            5,
            2,
            3,
            "下载成功");
    private static final DownloadResponse DOWNLOAD_EMPTY = new DownloadResponse(
            REQUEST_ID,
            DownloadOutcome.EMPTY,
            "test_plugin",
            "daily",
            0,
            0,
            0,
            "下载成功，0 条数据");
    private static final PageResponse PAGE = new PageResponse(
            REQUEST_ID,
            "test_plugin",
            "daily",
            1,
            50,
            2,
            1,
            List.of("ts_code"),
            List.of(
                    Map.of("ts_code", "000001.SZ"),
                    Map.of("ts_code", "000002.SZ")));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordsDownloadSuccessOnce() {
        Subjects subjects = subjects();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
        try (CapturedLog log = capturedLog()) {
            assertThat(subjects.logger().download(
                    KNOWN,
                    Map.of("trade_date", "20260807"),
                    () -> DOWNLOAD_SUCCESS)).isSameAs(DOWNLOAD_SUCCESS);

            assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .startsWith("tensor.operation.completed")
                        .contains(
                                "requestId=" + REQUEST_ID,
                                "operation=download",
                                "pluginId=test_plugin",
                                "apiName=daily",
                                "paramSummary=[trade_date]",
                                "sourceRowCount=5",
                                "insertedRows=2",
                                "updatedRows=3",
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
                .timer().count()).isEqualTo(1L);
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
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
        try (CapturedLog log = capturedLog()) {
            assertThat(subjects.logger().download(KNOWN, Map.of(), () -> DOWNLOAD_EMPTY))
                    .isSameAs(DOWNLOAD_EMPTY);
            assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains(
                        "sourceRowCount=0",
                        "insertedRows=0",
                        "updatedRows=0",
                        "outcome=empty",
                        "failureStage=none",
                        "errorCode=none");
                assertThat(event.getThrowableProxy()).isNull();
            });
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

    @ParameterizedTest
    @MethodSource("downloadFailures")
    void classifiesDownloadFailureWithoutReplacingIt(
            RuntimeException failure, ErrorCode code, String stage) {
        Subjects subjects = subjects();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
        try (CapturedLog log = capturedLog()) {
            Throwable thrown = catchThrowable(() -> subjects.logger().download(
                    KNOWN, Map.of(), throwing(failure)));

            assertThat(thrown).isSameAs(failure);
            assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains(
                        "operation=download",
                        "sourceRowCount=unavailable",
                        "outcome=failure",
                        "failureStage=" + stage,
                        "errorCode=" + code);
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
        assertThat(subjects.registry().get("tensor_download_total")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "failure")
                .counter().count()).isEqualTo(1.0);
        assertThat(subjects.registry().get("tensor_download_duration_seconds")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "failure")
                .timer().count()).isEqualTo(1L);
        assertThat(subjects.registry().find("tensor_download_rows_total").counter()).isNull();
    }

    @Test
    void recordsQuerySuccessOnce() {
        Subjects subjects = subjects();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
        try (CapturedLog log = capturedLog()) {
            assertThat(subjects.logger().query(
                    KNOWN, List.of("ts_code", "trade_date"), 9, 20, () -> PAGE))
                    .isSameAs(PAGE);
            assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains(
                        "operation=query",
                        "filterNames=[ts_code, trade_date]",
                        "page=1",
                        "pageSize=50",
                        "resultCount=2",
                        "totalElements=2",
                        "outcome=success",
                        "failureStage=none",
                        "errorCode=none");
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
        assertThat(subjects.registry().get("tensor_query_total")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(subjects.registry().get("tensor_query_duration_seconds")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "success")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void classifiesQueryFailureWithoutReplacingIt() {
        Subjects subjects = subjects();
        IllegalStateException failure = new IllegalStateException(SECRET);
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
        try (CapturedLog log = capturedLog()) {
            Throwable thrown = catchThrowable(() -> subjects.logger().query(
                    KNOWN, List.of("ann_date"), 3, 100, throwing(failure)));

            assertThat(thrown).isSameAs(failure);
            assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains(
                        "operation=query",
                        "filterNames=[ann_date]",
                        "page=3",
                        "pageSize=100",
                        "resultCount=unavailable",
                        "totalElements=unavailable",
                        "outcome=failure",
                        "failureStage=query",
                        "errorCode=QUERY_FAILED");
                assertThat(event.getFormattedMessage()).doesNotContain(SECRET);
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
        assertThat(subjects.registry().get("tensor_query_total")
                .tags("plugin", "test_plugin", "api", "daily", "outcome", "failure")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void removesSecretsValuesAndThrowableTextFromCompletionEvents() {
        Subjects subjects = subjects();
        LinkedHashMap<String, Object> unsafe = new LinkedHashMap<>();
        unsafe.put("trade_date", SECRET);
        unsafe.put("ts_code", SECRET);
        unsafe.put("token", SECRET);
        unsafe.put("Authorization", SECRET);
        unsafe.put("Cookie", SECRET);
        unsafe.put("db_password", SECRET);
        unsafe.put("credential", SECRET);
        TestTensorException failure = new TestTensorException(ErrorCode.PARAM_INVALID);
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);

        try (CapturedLog log = capturedLog()) {
            assertThat(catchThrowable(() -> subjects.logger().download(
                    KNOWN, unsafe, throwing(failure)))).isSameAs(failure);
            assertThat(completionEvents(log)).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .contains("paramSummary=[trade_date, ts_code]")
                        .doesNotContain(
                                SECRET,
                                "token",
                                "Authorization",
                                "Cookie",
                                "db_password",
                                "credential");
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
    }

    @Test
    void skipsUnknownKeysWithoutSkippingTheOperation() {
        Subjects subjects = subjects();
        DatasetKey unknown = DatasetKey.of(PluginId.of("other_plugin"), ApiName.of("daily"));
        AtomicBoolean called = new AtomicBoolean();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);

        try (CapturedLog log = capturedLog()) {
            DownloadResponse response = subjects.logger().download(unknown, Map.of(), () -> {
                called.set(true);
                return DOWNLOAD_SUCCESS;
            });
            assertThat(response).isSameAs(DOWNLOAD_SUCCESS);
            assertThat(called).isTrue();
            assertThat(completionEvents(log)).isEmpty();
        }
        assertThat(subjects.registry().getMeters()).isEmpty();
    }

    @Test
    void exposesOnlyTheFrozenMeterSchema() {
        Subjects subjects = subjects();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
        subjects.logger().download(KNOWN, Map.of(), () -> DOWNLOAD_SUCCESS);
        subjects.logger().download(KNOWN, Map.of(), () -> DOWNLOAD_EMPTY);
        catchThrowable(() -> subjects.logger().download(
                KNOWN, Map.of(), throwing(new IllegalStateException(SECRET))));
        subjects.logger().query(KNOWN, List.of(), 1, 50, () -> PAGE);
        catchThrowable(() -> subjects.logger().query(
                KNOWN, List.of(), 1, 50, throwing(new IllegalStateException(SECRET))));

        assertThat(subjects.registry().getMeters()).extracting(meter -> meter.getId().getName())
                .containsOnly(
                        "tensor_download_total",
                        "tensor_download_duration_seconds",
                        "tensor_download_rows_total",
                        "tensor_query_total",
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
                assertThat(tags.get("outcome")).isIn("success", "empty", "failure");
                if (name.startsWith("tensor_query")) {
                    assertThat(tags.get("outcome")).isNotEqualTo("empty");
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/downloads", "/assets/app-deadbeef.js"})
    void writesAllSecurityHeaders(String path) throws Exception {
        MockHttpServletResponse response = filtered(path);

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(
                "default-src 'self'; base-uri 'none'; object-src 'none'; "
                        + "frame-ancestors 'none'; form-action 'self'; "
                        + "script-src 'self'; style-src 'self'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(response.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
    }

    @ParameterizedTest
    @CsvSource({
        "/index.html, no-store",
        "/assets/app-deadbeef.js, 'public, max-age=31536000, immutable'"
    })
    void appliesStaticCachePolicy(String path, String expected) throws Exception {
        assertThat(filtered(path).getHeader("Cache-Control")).isEqualTo(expected);
    }

    @Test
    void loadsOnlyTheApprovedEnvironmentAndActuatorDefaults() throws Exception {
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(source.getProperty("spring.datasource.url")).isEqualTo("${TENSOR_DB_URL}");
        assertThat(source.getProperty("spring.datasource.username"))
                .isEqualTo("${TENSOR_DB_USERNAME}");
        assertThat(source.getProperty("spring.datasource.password"))
                .isEqualTo("${TENSOR_DB_PASSWORD}");
        assertThat(source.getProperty("tensor.display-zone"))
                .isEqualTo("${TENSOR_DISPLAY_ZONE:Asia/Shanghai}");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.enabled"))
                .isEqualTo("${TENSOR_TUSHARE_ENABLED:true}");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.base-url"))
                .isEqualTo("${TENSOR_TUSHARE_BASE_URL:https://api.tushare.pro}");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.token"))
                .isEqualTo("${TENSOR_TUSHARE_TOKEN:}");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.connect-timeout"))
                .isEqualTo("5s");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.read-timeout"))
                .isEqualTo("120s");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.max-response-bytes"))
                .isEqualTo(67108864);
        assertThat(source.getProperty("tensor.persistence.batch-size")).isEqualTo(500);
        assertThat(source.getProperty("tensor.query.default-page-size")).isEqualTo(50);
        assertThat(List.of(
                source.getProperty("tensor.query.allowed-page-sizes[0]"),
                source.getProperty("tensor.query.allowed-page-sizes[1]"),
                source.getProperty("tensor.query.allowed-page-sizes[2]")))
                .containsExactly(20, 50, 100);
        assertThat(source.getProperty("management.endpoints.web.base-path"))
                .isEqualTo("/actuator");
        assertThat(source.getProperty("management.endpoints.web.discovery.enabled"))
                .isEqualTo(false);
        assertThat(source.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
        assertThat(source.getProperty("management.endpoint.health.probes.enabled")).isEqualTo(true);
        assertThat(source.getProperty("management.endpoint.health.show-components"))
                .isEqualTo("always");
        assertThat(source.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(source.getProperty("management.endpoint.env.show-values")).isEqualTo("never");
        assertThat(source.getProperty("management.endpoint.configprops.show-values"))
                .isEqualTo("never");
        assertThat(source.getSource().toString()).doesNotContain(SECRET);
    }

    private static Stream<Arguments> downloadFailures() {
        return Stream.of(
                Arguments.of(new TestTensorException(ErrorCode.PARAM_INVALID),
                        ErrorCode.PARAM_INVALID, "parameter"),
                Arguments.of(new TestTensorException(ErrorCode.PLUGIN_DISABLED),
                        ErrorCode.PLUGIN_DISABLED, "registration"),
                Arguments.of(new TestTensorException(ErrorCode.SOURCE_TIMEOUT),
                        ErrorCode.SOURCE_TIMEOUT, "source"),
                Arguments.of(new TestTensorException(ErrorCode.ADAPTER_TYPE_INVALID),
                        ErrorCode.ADAPTER_TYPE_INVALID, "adapter"),
                Arguments.of(new DataAccessResourceFailureException(SECRET),
                        ErrorCode.PERSISTENCE_FAILED, "persistence"),
                Arguments.of(new IllegalStateException(SECRET),
                        ErrorCode.INTERNAL_ERROR, "internal"));
    }

    private static Subjects subjects() {
        PluginRegistry plugins = new PluginRegistry(List.of(new TestPlugin()));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TensorMetrics metrics = new TensorMetrics(registry, plugins);
        return new Subjects(registry, metrics, new OperationLogger(plugins, metrics));
    }

    private static <T> Supplier<T> throwing(RuntimeException failure) {
        return () -> {
            throw failure;
        };
    }

    private static List<ILoggingEvent> completionEvents(CapturedLog log) {
        return log.appender().list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .filter(event -> event.getFormattedMessage().startsWith("tensor.operation.completed"))
                .toList();
    }

    private static CapturedLog capturedLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(OperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new CapturedLog(logger, appender);
    }

    private static MockHttpServletResponse filtered(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new WebSecurityHeadersConfiguration().securityHeadersFilter().getFilter()
                .doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });
        return response;
    }

    private record Subjects(
            SimpleMeterRegistry registry,
            TensorMetrics metrics,
            OperationLogger logger) {
    }

    private record CapturedLog(
            Logger logger,
            ListAppender<ILoggingEvent> appender) implements AutoCloseable {
        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static final class TestPlugin implements DataSourcePlugin {
        private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
                KNOWN.pluginId(),
                "Test plugin",
                "Test data source",
                true,
                true,
                true,
                null,
                List.of(new ApiDescriptor(
                        KNOWN.apiName(),
                        "Daily",
                        "Market",
                        QueryMode.trade_date,
                        List.of(
                                new ParameterDescriptor(
                                        "trade_date", "Trade date", null, ParameterType.DATE,
                                        false, null, List.of(), null, null),
                                new ParameterDescriptor(
                                        "ts_code", "Security code", null, ParameterType.TS_CODE,
                                        false, null, List.of(), null, null)))),
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
        public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
            throw new UnsupportedOperationException("logger tests do not call plugins");
        }
    }

    private static final class TestTensorException extends TensorException {
        private TestTensorException(ErrorCode code) {
            super(code, SECRET);
            initCause(new IllegalStateException(SECRET));
        }
    }
}
