package com.akkc.tensor.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.akkc.tensor.TensorApplication;
import com.akkc.tensor.config.ApplicationConfiguration;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.DatasetAdapter;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class ProductionApplicationContextIT {
    private static final String SECRET = "m09-t06-token-password-secret";
    private static final String INVALID_PAGE = "not-a-number";
    private static final List<String> HIDDEN_ENDPOINTS = List.of(
            "/actuator", "/actuator/env", "/actuator/configprops", "/actuator/metrics");

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
            assertHealth(first, firstHealth, 200, "UP", "UP");
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
            HttpResponse secondHealth = get(second, "/actuator/health");
            assertHealth(second, secondHealth, 200, "UP", "UP");
            HttpResponse secondMetadata = get(second, "/api/v1/data-sources");
            assertMetadata(second, secondMetadata, true, true);
            assertSafeResponse(secondHealth, jdbcUrl, username);
            assertSafeResponse(secondMetadata, jdbcUrl, username);
            assertHidden(second, jdbcUrl, username);
            assertThat(captured.text()).doesNotContain(SECRET);

            mysql.stop();
            HttpResponse down = get(second, "/actuator/health");
            assertHealth(second, down, 503, "DOWN", "DOWN");
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
        assertUnique(context, ApplicationConfiguration.class);
        assertUnique(context, PluginRegistry.class);
        assertUnique(context, DatasetCatalog.class);
        assertUnique(context, AdapterRegistry.class);
        assertUnique(context, DownloadService.class);
        assertUnique(context, DatasetQueryService.class);
        assertUnique(context, TensorMetrics.class);
        assertUnique(context, OperationLogger.class);
        assertUnique(context, DataSourceController.class);
        assertUnique(context, DownloadController.class);
        assertUnique(context, DatasetController.class);
        assertUnique(context, GlobalExceptionHandler.class);
        assertUnique(context, RequestIdFilter.class);
        assertThat(context.getBean("securityHeadersFilter"))
                .isInstanceOf(FilterRegistrationBean.class);
        assertThat(context.getBeansOfType(FilterRegistrationBean.class))
                .containsKey("securityHeadersFilter");

        Flyway flyway = context.getBean(Flyway.class);
        assertThat(flyway.info().applied()).hasSize(6);
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
            String health,
            String database) throws Exception {
        assertThat(response.status()).isEqualTo(status);
        assertSecurityHeaders(response);
        JsonNode body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body.path("status").textValue()).isEqualTo(health);
        assertThat(body.path("components").path("db").path("status").textValue())
                .isEqualTo(database);
        assertThat(body.path("components").path("db").has("details")).isFalse();
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
