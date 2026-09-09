package com.akkc.tensor.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.akkc.tensor.TensorApplication;
import com.akkc.tensor.config.ApplicationConfiguration;
import com.akkc.tensor.web.download.DownloadDescriptorResolver;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.download.DownloadRequestDeserializer;
import com.akkc.tensor.web.download.DownloadParameters.DateRangeParameters;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.download.BatchCommitService;
import com.akkc.tensor.core.download.DownloadExecutionSlot;
import com.akkc.tensor.core.download.DownloadParameterConverter;
import com.akkc.tensor.core.download.RetryDownloadService;
import com.akkc.tensor.core.retry.RetryTaskStorageService;
import com.akkc.tensor.core.retry.RetryTaskRepository.Failure;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.metadata.MetadataQueryService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.web.DataSourceController;
import com.akkc.tensor.web.DatasetController;
import com.akkc.tensor.web.DownloadController;
import com.akkc.tensor.web.GlobalExceptionHandler;
import com.akkc.tensor.web.RequestIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.web.client.RestClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class ProductionApplicationContextIT {
    private static final String SECRET = "m09-t06-token-password-secret";
    private static final String INVALID_PAGE = "not-a-number";
    private static final List<String> HIDDEN_ENDPOINTS = List.of(
            "/actuator", "/actuator/env", "/actuator/configprops", "/actuator/metrics",
            "/actuator/beans", "/actuator/heapdump", "/actuator/logfile", "/actuator/mappings",
            "/actuator/health/db", "/actuator/health/liveness/livenessState",
            "/actuator/health/readiness/readinessState", "/actuator/health/unknown");

    @Test
    void startsTheSafeProductionServletGraphAndTracksOnlyDatabaseHealth() throws Exception {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
                .withDatabaseName("tensor")
                .withUsername("tensor")
                .withPassword(SECRET);
        ConfigurableApplicationContext first = null;
        ConfigurableApplicationContext second = null;
        CapturedLog captured = null;
        try {
            mysql.start();
            String jdbcUrl = mysql.getJdbcUrl();
            String username = mysql.getUsername();
            first = start(mysql, "");
            assertProductionGraph(first);
            HttpResponse firstHealth = get(first, "/actuator/health");
            assertHealth(first, firstHealth, 200, "UP");
            assertProbesUp(first);
            HttpResponse metadata = get(first, "/api/v1/data-sources");
            assertMetadata(first, metadata, false, false);
            HttpResponse invalid = get(first,
                    "/api/v1/data-sources/tushare_pro/datasets/daily/records?page="
                            + INVALID_PAGE + "&pageSize=50");
            assertSafeParameterError(first, invalid);
            assertSafeResponse(firstHealth, jdbcUrl, username);
            assertSafeResponse(metadata, jdbcUrl, username);
            assertSafeResponse(invalid, jdbcUrl, username);
            assertHidden(first, jdbcUrl, username);
            first.close();
            first = null;

            captured = captureRootLog();
            second = start(mysql, SECRET);
            assertProductionGraph(second);
            assertInitialProductionCapabilitiesRemainClosed(second);
            DownloadRequest bound = second.getBean(ObjectMapper.class).readValue(
                    "{\"pluginId\":\"tushare_pro\",\"apiName\":\"daily\",\"params\":{\"start_date\":\"20260905\",\"end_date\":\"20260905\"}}",
                    DownloadRequest.class);
            assertThat(bound.params()).isEqualTo(new DateRangeParameters("20260905", "20260905"));
            HttpResponse secondHealth = get(second, "/actuator/health");
            assertHealth(second, secondHealth, 200, "UP");
            assertProbesUp(second);
            HttpResponse secondMetadata = get(second, "/api/v1/data-sources");
            assertMetadata(second, secondMetadata, true, true);
            assertSafeResponse(secondHealth, jdbcUrl, username);
            assertSafeResponse(secondMetadata, jdbcUrl, username);
            assertHidden(second, jdbcUrl, username);
            assertThat(captured.text()).doesNotContain(SECRET, "unknown-outer-body-sentinel", "identifier-body-sentinel");

            mysql.stop();
            HttpResponse down = get(second, "/actuator/health");
            assertHealth(second, down, 503, "DOWN");
            assertProbesUp(second);
            assertSafeResponse(down, jdbcUrl, username);
        } finally {
            if (captured != null) {
                captured.close();
            }
            if (second != null) {
                second.close();
            }
            if (first != null) {
                first.close();
            }
            if (mysql.isRunning()) {
                mysql.stop();
            }
        }
    }

    private static ConfigurableApplicationContext start(
            MySQLContainer<?> mysql, String token) {
        return new SpringApplicationBuilder(TensorApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(context -> context.getBeanFactory().registerSingleton(
                        "productionTestTypeExcludeFilter", new TestTypeExcludeFilter()))
                .properties(
                        "server.port=0",
                        "spring.profiles.active=production",
                        "spring.datasource.hikari.connection-timeout=250",
                        "TENSOR_DB_URL=" + mysql.getJdbcUrl(),
                        "TENSOR_DB_USERNAME=" + mysql.getUsername(),
                        "TENSOR_DB_PASSWORD=" + SECRET,
                        "TENSOR_TUSHARE_TOKEN=" + token)
                .run();
    }

    private static final class TestTypeExcludeFilter extends TypeExcludeFilter {
        @Override
        public boolean match(
                MetadataReader reader, MetadataReaderFactory factory) {
            return reader.getClassMetadata().getClassName().equals(
                    "com.akkc.tensor.web.GlobalExceptionHandlerTest$FailureController");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestTypeExcludeFilter;
        }

        @Override
        public int hashCode() {
            return TestTypeExcludeFilter.class.hashCode();
        }
    }

    private static void assertProductionGraph(ConfigurableApplicationContext context) {
        assertProductionMapper(context.getBean(ObjectMapper.class));
        assertUnique(context, ApplicationConfiguration.class);
        assertUnique(context, PluginRegistry.class);
        assertUnique(context, DatasetCatalog.class);
        assertUnique(context, AdapterRegistry.class);
        assertUnique(context, DownloadService.class);
        assertUnique(context, DownloadExecutionSlot.class);
        assertUnique(context, BatchCommitService.class);
        assertUnique(context, RetryDownloadService.class);
        assertUnique(context, com.akkc.tensor.core.retry.RetryTaskQueryService.class);
        var queries = context.getBean(com.akkc.tensor.core.retry.RetryTaskQueryService.class);
        var shared = Map.of("storage", RetryTaskStorageService.class, "plugins", PluginRegistry.class, "adapters", AdapterRegistry.class,
                "validator", ParameterValidator.class, "slot", DownloadExecutionSlot.class);
        shared.forEach((field, type) -> assertThat(org.springframework.test.util.ReflectionTestUtils.getField(queries, field)).isSameAs(context.getBean(type)));
        DownloadService downloads = context.getBean(DownloadService.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(downloads, "slot"))
                .isSameAs(context.getBean(DownloadExecutionSlot.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(downloads, "commits"))
                .isSameAs(context.getBean(BatchCommitService.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(downloads, "failures"))
                .isSameAs(context.getBean(RetryTaskStorageService.class));
        RetryDownloadService retries = context.getBean(RetryDownloadService.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(retries, "pluginRegistry"))
                .isSameAs(context.getBean(PluginRegistry.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(retries, "adapterRegistry"))
                .isSameAs(context.getBean(AdapterRegistry.class));
        Object retryConverter = org.springframework.test.util.ReflectionTestUtils.getField(retries, "converter");
        assertThat(retryConverter).isInstanceOf(DownloadParameterConverter.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(retryConverter, "validator"))
                .isSameAs(context.getBean(ParameterValidator.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(retries, "commits"))
                .isSameAs(context.getBean(BatchCommitService.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(retries, "failures"))
                .isSameAs(context.getBean(RetryTaskStorageService.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(retries, "slot"))
                .isSameAs(context.getBean(DownloadExecutionSlot.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(retries, "clock"))
                .isSameAs(context.getBean(java.time.Clock.class));
        assertUnique(context, DatasetQueryService.class);
        assertUnique(context, MetadataQueryService.class);
        assertUnique(context, TensorMetrics.class);
        assertUnique(context, OperationLogger.class);
        assertUnique(context, DataSourceController.class);
        assertUnique(context, DownloadController.class);
        assertUnique(context, DownloadDescriptorResolver.class);
        assertUnique(context, DownloadParameterResolver.class);
        assertUnique(context, DownloadRequestDeserializer.class);
        assertUnique(context, DatasetController.class);
        assertUnique(context, GlobalExceptionHandler.class);
        assertUnique(context, RequestIdFilter.class);
        assertThat(context.getBean("securityHeadersFilter"))
                .isInstanceOf(FilterRegistrationBean.class);
        assertThat(context.getBeansOfType(FilterRegistrationBean.class))
                .containsKey("securityHeadersFilter");

        assertUnique(context, com.akkc.tensor.core.retry.TaskParametersJson.class);
        assertUnique(context, com.akkc.tensor.core.retry.RetryTaskRepository.class);
        assertUnique(context, com.akkc.tensor.core.retry.RetryTaskStorageService.class);

        Flyway flyway = context.getBean(Flyway.class);
        assertThat(flyway.info().applied()).hasSize(8);
        List<?> definitions = context.getBean("tushareDatasetDefinitions", List.class);
        List<?> adapters = context.getBean("tensorDatasetAdapters", List.class);
        assertThat(definitions).hasSize(49).allSatisfy(
                definition -> assertThat(definition).isInstanceOf(DatasetDefinition.class));
        assertThat(adapters).hasSize(49).allSatisfy(
                adapter -> assertThat(adapter).isInstanceOf(DatasetAdapter.class));

        DatasetCatalog catalog = context.getBean(DatasetCatalog.class);
        AdapterRegistry adapterRegistry = context.getBean(AdapterRegistry.class);
        for (Object candidate : definitions) {
            DatasetDefinition definition = (DatasetDefinition) candidate;
            assertThat(catalog.find(definition.datasetKey())).isPresent();
            assertThat(adapterRegistry.find(definition.datasetKey())).isPresent();
        }
    }

    private static void assertProductionMapper(ObjectMapper mapper) {
        var result = new com.akkc.tensor.web.dto.DownloadResponse("request",
                com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.UNCONFIRMED,
                "fixture", "fixture_daily", 3000000001L, 3000000000L, 1, "Safe", 2, 0,
                null, 0, null, null,
                com.akkc.tensor.core.download.DownloadExecutionResult.FailureRecordStatus.UNCONFIRMED,
                List.of(), List.of(), List.of());
        JsonNode json = mapper.valueToTree(result);
        assertThat(json.size()).isEqualTo(18);
        for (String field : List.of("sourceRowCount", "insertedRows", "updatedRows", "completedUnits", "failedUnits", "skippedClosedDates")) {
            assertThat(json.path(field).isIntegralNumber()).as(field).isTrue();
        }
        assertThat(json.path("insertedRows").longValue()).isEqualTo(3000000000L);
        for (String field : List.of("notStartedUnits", "remainingFailedUnits", "taskId")) {
            assertThat(json.path(field).isNull()).as(field).isTrue();
        }
        var known = new com.akkc.tensor.web.dto.DownloadResponse("request", result.outcome(),
                "fixture", "fixture_daily", 0, 0, 0, "Safe", 0, 0, 3000000000L, 0,
                null, 3000000001L, result.failureRecordStatus(), List.of(), List.of(), List.of());
        JsonNode numbers = mapper.valueToTree(known);
        for (String field : List.of("notStartedUnits", "remainingFailedUnits")) {
            assertThat(numbers.path(field).isIntegralNumber()).as(field).isTrue();
            assertThat(numbers.path(field).longValue()).isGreaterThan(Integer.MAX_VALUE);
        }
        var summary = new com.akkc.tensor.web.dto.RetryTaskResponse.Summary(UUID.randomUUID().toString(),
                "fixture", "fixture_daily", null, null, null,
                com.akkc.tensor.core.retry.RetryTaskQueryService.OriginalRangeStatus.UNCONFIRMED,
                3000000000L, List.of(), "1970-01-01T00:00:00.000Z", "1970-01-01T00:00:00.123Z");
        JsonNode page = mapper.valueToTree(new com.akkc.tensor.web.dto.RetryTaskResponse.Page(
                "request", 1, 20, 3000000000L, 150000000L, List.of(summary)));
        assertThat(page.path("totalElements").isIntegralNumber()).isTrue();
        assertThat(page.path("totalPages").isIntegralNumber()).isTrue();
        assertThat(page.path("items").get(0).path("failedItemCount").isIntegralNumber()).isTrue();
        JsonNode records = mapper.valueToTree(Map.of("bigint", 9007199254740993L,
                "decimal", new java.math.BigDecimal("12345678901234567890.123456789")));
        assertThat(records.path("bigint").isTextual()).isTrue();
        assertThat(records.path("bigint").asText()).isEqualTo("9007199254740993");
        assertThat(records.path("decimal").isTextual()).isTrue();
        assertThat(records.path("decimal").asText()).isEqualTo("12345678901234567890.123456789");
    }

    private static void assertInitialProductionCapabilitiesRemainClosed(ConfigurableApplicationContext context) throws Exception {
        var downloads = context.getBean(DownloadService.class);
        var plugin = new PluginId("tushare_pro");
        var request = new RequestId(UUID.randomUUID());
        assertThatThrownBy(() -> downloads.executeInitial(plugin,
                new ApiName("daily"),
                Map.of("start_date", "20260901", "end_date", "20260903"), request))
                .isInstanceOfSatisfying(TensorException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.CALENDAR_UNCONFIRMED));
        assertThatThrownBy(() -> downloads.executeInitial(plugin,
                new ApiName("index_classify"), Map.of(), request))
                .isInstanceOfSatisfying(TensorException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED));
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        assertThat(mapper.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        for (boolean first : List.of(false, true)) {
            String fields = "\"pluginId\":\"tushare_pro\",\"apiName\":\"daily\",\"params\":{\"start_date\":\"20260901\",\"end_date\":\"20260903\"}";
            String unknown = "\"unknown\":\"unknown-outer-body-sentinel\"";
            RestClient.create("http://127.0.0.1:" + port).post().uri("/api/v1/downloads").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{" + (first ? unknown + "," + fields : fields + "," + unknown) + "}")
                    .exchange((httpRequest, response) -> {
                        assertThat(response.getStatusCode().value()).isEqualTo(400);
                        var body = mapper.readTree(response.getBody());
                        assertThat(body.path("code").asText()).isEqualTo("PARAM_INVALID");
                        assertThat(body.path("fieldErrors")).isEqualTo(mapper.readTree("[{\"field\":\"request\",\"message\":\"has invalid value\"}]"));
                        assertThat(body.has("downloadResult")).isFalse();
                        assertThat(body.path("requestId").asText()).isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
                        assertThat(body.toString()).doesNotContain("unknown-outer-body-sentinel", "unknown");
                        return null;
                    });
        }
        assertThat(mapper.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.convertValue(true, String.class)).isEqualTo("true");
        for (String field : List.of("pluginId", "apiName")) {
            for (Object value : List.of(true, false, 123, 1.5, Map.of("secret", "identifier-body-sentinel"), List.of("identifier-body-sentinel"))) {
                var input = new LinkedHashMap<String, Object>();
                input.put("pluginId", "tushare_pro"); input.put("apiName", "daily");
                input.put("params", Map.of("start_date", "20260901", "end_date", "20260903")); input.put(field, value);
                RestClient.create("http://127.0.0.1:" + port).post().uri("/api/v1/downloads").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(input))
                        .exchange((httpRequest, response) -> {
                            assertThat(response.getStatusCode().value()).isEqualTo(400);
                            var body = mapper.readTree(response.getBody());
                            assertThat(body.path("code").asText()).isEqualTo("PARAM_INVALID");
                            assertThat(body.path("fieldErrors")).isEqualTo(mapper.valueToTree(List.of(Map.of("field", field, "message", "has invalid value"))));
                            assertThat(body.has("downloadResult")).isFalse();
                            assertThat(body.path("requestId").asText()).isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
                            assertThat(body.toString()).doesNotContain("identifier-body-sentinel");
                            return null;
                        });
            }
        }
        assertThat(mapper.convertValue(false, String.class)).isEqualTo("false");
        for (String name : List.of("daily", "forecast", "index_classify")) {
            String params = name.equals("index_classify") ? "{}" : "{\"start_date\":\"20260901\",\"end_date\":\"20260903\"}";
            String expected = name.equals("daily") ? "CALENDAR_UNCONFIRMED" : name.equals("forecast") ? "SOURCE_REQUEST_UNCONFIRMED" : "SOURCE_COMPLETENESS_UNCONFIRMED";
            RestClient.create("http://127.0.0.1:" + port).post().uri("/api/v1/downloads").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"pluginId\":\"tushare_pro\",\"apiName\":\"" + name + "\",\"params\":" + params + "}")
                    .exchange((httpRequest, response) -> {
                        assertThat(response.getStatusCode().value()).isEqualTo(name.equals("forecast") ? 409 : 502);
                        var body = context.getBean(ObjectMapper.class).readTree(response.getBody());
                        assertThat(body.path("code").asText()).isEqualTo(expected);
                        assertThat(body.has("downloadResult")).isFalse();
                        assertThat(body.path("requestId").asText()).isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
                        return null;
                    });
        }
        var jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tensor_download_task", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tensor_download_task_item", Integer.class)).isZero();
        var selector = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "",
                RecoverySelector.TimeType.DATE, "2026-09-02");
        UUID taskId = context.getBean(RetryTaskStorageService.class).create(
                DatasetKey.of(plugin, new ApiName("daily")),
                Map.of("start_date", "20260901", "end_date", "20260903"),
                new Failure(selector, ErrorCode.SOURCE_TIMEOUT)).key().taskId();
        assertThatThrownBy(() -> context.getBean(RetryDownloadService.class).execute(taskId, request))
                .isInstanceOfSatisfying(TensorException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.CALENDAR_UNCONFIRMED));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tensor_download_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tensor_download_task_item", Integer.class)).isEqualTo(1);
        assertThat(context.getBean(DownloadExecutionSlot.class).busy()).isFalse();
    }

    private static void assertUnique(
            ConfigurableApplicationContext context, Class<?> type) {
        assertThat(context.getBeansOfType(type)).hasSize(1);
    }

    private static void assertMetadata(
            ConfigurableApplicationContext context,
            HttpResponse response,
            boolean credentialConfigured,
            boolean downloadAvailable) throws Exception {
        assertThat(response.status()).isEqualTo(200);
        assertSecurityHeaders(response);
        JsonNode body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body.isArray()).isTrue();
        JsonNode tushare = null;
        for (JsonNode candidate : body) {
            if ("tushare_pro".equals(candidate.path("pluginId").textValue())) {
                tushare = candidate;
            }
        }
        assertThat(tushare).isNotNull();
        assertThat(tushare.path("credentialConfigured").booleanValue())
                .isEqualTo(credentialConfigured);
        assertThat(tushare.path("downloadAvailable").booleanValue())
                .isEqualTo(downloadAvailable);

        PluginDescriptor descriptor = context.getBean(PluginRegistry.class).descriptors().stream()
                .filter(value -> value.pluginId().value().equals("tushare_pro"))
                .findFirst()
                .orElseThrow();
        assertThat(descriptor.enabled()).isTrue();
        assertThat(descriptor.credentialConfigured()).isEqualTo(credentialConfigured);
        assertThat(descriptor.downloadAvailable()).isEqualTo(downloadAvailable);
    }

    private static void assertHealth(
            ConfigurableApplicationContext context,
            HttpResponse response,
            int status,
            String health) throws Exception {
        assertThat(response.status()).isEqualTo(status);
        assertSecurityHeaders(response);
        JsonNode body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body).isEqualTo(context.getBean(ObjectMapper.class)
                .valueToTree(Map.of("status", health)));
    }

    private static void assertProbesUp(ConfigurableApplicationContext context) throws Exception {
        for (String probe : List.of("liveness", "readiness")) {
            assertHealth(context, get(context, "/actuator/health/" + probe), 200, "UP");
        }
    }

    private static void assertSafeParameterError(
            ConfigurableApplicationContext context, HttpResponse response) throws Exception {
        assertThat(response.status()).isEqualTo(400);
        assertSecurityHeaders(response);
        JsonNode body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body.path("code").textValue()).isEqualTo("PARAM_INVALID");
        assertThat(body.path("message").textValue()).isEqualTo("Parameters are invalid");
        assertThat(body.path("fieldErrors").path(0).path("field").textValue())
                .isEqualTo("page");
        assertThat(response.body()).doesNotContain(INVALID_PAGE);
    }

    private static void assertHidden(
            ConfigurableApplicationContext context, String jdbcUrl, String username) {
        for (String path : HIDDEN_ENDPOINTS) {
            HttpResponse response = get(context, path);
            assertThat(response.status()).isEqualTo(404);
            assertSecurityHeaders(response);
            assertSafeResponse(response, jdbcUrl, username);
        }
    }

    private static void assertSafeResponse(
            HttpResponse response, String jdbcUrl, String username) {
        assertThat(response.body()).doesNotContain(SECRET, jdbcUrl, username);
    }

    private static void assertSecurityHeaders(HttpResponse response) {
        assertThat(response.header(RequestIdFilter.HEADER_NAME)).isNotBlank();
        assertThat(response.header("Content-Security-Policy")).isEqualTo(
                "default-src 'self'; base-uri 'none'; object-src 'none'; "
                        + "frame-ancestors 'none'; form-action 'self'; "
                        + "script-src 'self'; style-src 'self'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'");
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.header("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.header("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.header("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(response.header("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
        assertThat(response.header("Cache-Control")).isEqualTo("no-store");
    }

    private static HttpResponse get(
            ConfigurableApplicationContext context, String path) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return RestClient.create("http://127.0.0.1:" + port)
                .get()
                .uri(path)
                .exchange((request, response) -> {
                    Map<String, String> headers = new LinkedHashMap<>();
                    response.getHeaders().forEach((name, values) -> {
                        if (!values.isEmpty()) {
                            headers.put(name, values.getFirst());
                        }
                    });
                    try {
                        return new HttpResponse(
                                response.getStatusCode().value(),
                                headers,
                                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
                    } catch (IOException exception) {
                        throw new IllegalStateException("Unable to read test response", exception);
                    }
                });
    }

    private static CapturedLog captureRootLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new CapturedLog(logger, appender);
    }

    private record HttpResponse(int status, Map<String, String> headers, String body) {
        private HttpResponse {
            headers = Map.copyOf(headers);
        }

        private String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }

    private record CapturedLog(
            Logger logger,
            ListAppender<ILoggingEvent> appender) implements AutoCloseable {
        private String text() {
            List<String> values = new ArrayList<>();
            for (ILoggingEvent event : appender.list) {
                values.add(event.getFormattedMessage());
                IThrowableProxy throwable = event.getThrowableProxy();
                while (throwable != null) {
                    values.add(String.valueOf(throwable.getMessage()));
                    throwable = throwable.getCause();
                }
            }
            return String.join("\n", values);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
