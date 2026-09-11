package com.akkc.tensor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.akkc.tensor.TensorApplication;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.BatchCommitService;
import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskCoordinator;
import com.akkc.tensor.core.download.task.DownloadTaskJson;
import com.akkc.tensor.core.download.task.DownloadTaskQueryService;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.observability.DownloadTaskOperationLogger;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.Availability;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DownloadTaskApplicationConfigurationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6");
    private final AtomicInteger sourceRequests = new AtomicInteger();
    private final List<String> events = new ArrayList<>();
    private final CountDownLatch sourceEntered = new CountDownLatch(1);
    private CountDownLatch releaseSource = new CountDownLatch(0);
    private volatile byte[] sourceBody;
    private HttpServer source;

    @BeforeEach
    void resetDatabaseAndSource() throws Exception {
        flyway().clean();
        source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/", exchange -> {
            sourceRequests.incrementAndGet();
            sourceEntered.countDown();
            try {
                if (!releaseSource.await(10, TimeUnit.SECONDS) || sourceBody == null) {
                    exchange.sendResponseHeaders(500, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, sourceBody.length);
                exchange.getResponseBody().write(sourceBody);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        source.start();
    }

    @AfterEach
    void stopSource() { releaseSource.countDown(); source.stop(0); }

    @Test
    void productionServletGraphStartsAfterFlywayAndCatalogWithOneSharedRunAndBudget() {
        try (ConfigurableApplicationContext context = start(false)) {
            assertThat(events).containsSubsequence("flywayInitializer", "datasetCatalog", "downloadTaskRunId",
                    "downloadTaskService", "downloadTaskRunner", "downloadTaskCoordinator");
            for (Class<?> type : List.of(ApplicationConfiguration.class, DownloadTaskConfiguration.class,
                    DownloadTaskJson.class, DownloadTaskRepository.class, DownloadTaskQueryService.class,
                    DownloadTaskService.class, BatchCommitService.class, DownloadTaskRunner.class,
                    DownloadTaskCoordinator.class, DownloadTaskOperationLogger.class)) {
                assertThat(context.getBeansOfType(type)).as(type.getSimpleName()).hasSize(1);
            }
            assertThat(context.getBeansOfType(UUID.class)).containsOnlyKeys("downloadTaskRunId");
            UUID runId = context.getBean("downloadTaskRunId", UUID.class);
            for (Class<?> type : List.of(DownloadTaskService.class, DownloadTaskRunner.class, DownloadTaskCoordinator.class)) {
                assertThat(ReflectionTestUtils.getField(context.getBean(type), "activeRunId")).isEqualTo(runId);
            }
            DownloadTaskProperties properties = context.getBean(DownloadTaskProperties.class);
            assertThat(properties.toServiceSettings()).isEqualTo(DownloadTaskService.Settings.defaults());
            assertThat(properties.toRunnerSettings()).isEqualTo(DownloadTaskRunner.Settings.defaults());
            assertThat(ReflectionTestUtils.getField(context.getBean(DownloadTaskService.class), "settings"))
                    .isEqualTo(properties.toServiceSettings());
            assertThat(ReflectionTestUtils.getField(context.getBean(DownloadTaskRunner.class), "settings"))
                    .isEqualTo(properties.toRunnerSettings());
            assertThat(context.getBean("downloadTaskLifecycle", SmartLifecycle.class).isRunning()).isTrue();
            assertThat(context.getBean(DownloadTaskCoordinator.class).isRunning()).isTrue();
            List<?> definitions = context.getBean("tushareDatasetDefinitions", List.class);
            assertThat(definitions).hasSize(40);
            DownloadTaskService tasks = context.getBean(DownloadTaskService.class);
            List<Availability> ranges = definitions.stream().map(DatasetDefinition.class::cast)
                    .peek(definition -> assertThat(context.getBean(DatasetCatalog.class).find(definition.datasetKey())).isPresent())
                    .map(definition -> tasks.capabilities(definition.datasetKey()).range().availability()).toList();
            assertThat(ranges.stream().filter(value -> value == Availability.NEEDS_VERIFICATION)).hasSize(34);
            assertThat(ranges.stream().filter(value -> value == Availability.UNSUPPORTED)).hasSize(6);
            TushareProperties tushare = context.getBean(TushareProperties.class);
            assertThat(tushare.minRequestInterval()).isEqualTo(Duration.ofMillis(1500));
            assertThat(tushare.readTimeout()).isEqualTo(Duration.ofSeconds(120));
            assertThat(tushare.maxResponseBytes()).isEqualTo(67108864);
            assertThat(sourceRequests).hasValue(0);
        }
    }

    @Test
    void disabledProductionStillRecoversAndServesHistoryButCannotAdmitOrDispatch() {
        flyway().migrate();
        DriverManagerDataSource dataSource = dataSource();
        DownloadTaskRepository repository = new DownloadTaskRepository(new JdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource), new DownloadTaskJson());
        UUID oldRunId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        repository.insert(new DownloadTaskRepository.NewTask(taskId, UUID.randomUUID(), DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily")),
                DownloadMode.SINGLE, Map.of("ts_code", "000001.SZ", "trade_date", "20260901"), "0".repeat(64),
                "{\"schemaVersion\":1,\"mode\":\"SINGLE\"}", oldRunId, Instant.parse("2026-09-01T00:00:00Z")));
        try (ConfigurableApplicationContext context = start(false, "--tensor.download-tasks.enabled=false",
                "--tensor.download-tasks.max-queued-tasks=7", "--tensor.download-tasks.max-range-days=31")) {
            DownloadTaskQueryService queries = context.getBean(DownloadTaskQueryService.class);
            DownloadTask recovered = queries.detail(taskId).task().orElseThrow();
            assertThat(recovered.status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
            assertThat(recovered.activeRunId()).isEqualTo(oldRunId);
            assertThat(context.getBean(DownloadTaskCoordinator.class).isRunning()).isTrue();
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            JsonNode response = RestClient.create("http://127.0.0.1:" + port).get()
                    .uri("/api/v1/download-tasks/" + taskId).retrieve().body(JsonNode.class);
            assertThat(response.path("taskId").asText()).isEqualTo(taskId.toString());
            assertThat(response.path("status").asText()).isEqualTo("INTERRUPTED");
            assertThat(response.path("canResume").asBoolean()).isFalse();
            assertThatThrownBy(() -> context.getBean(DownloadTaskService.class).submit(new DownloadTaskService.Submission(
                    UUID.randomUUID(), recovered.datasetKey(), DownloadMode.SINGLE, recovered.params())))
                    .isInstanceOfSatisfying(TensorException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED));
            assertThat(context.getBean(DownloadTaskProperties.class).toServiceSettings())
                    .isEqualTo(new DownloadTaskService.Settings(false, 7, 31));
            assertThat(context.getBean(DownloadTaskProperties.class).toRunnerSettings().maxRangeDays()).isEqualTo(31);
            assertThat(sourceRequests).hasValue(0);
        }
    }

    @Test
    void acceptedHttpTaskNeverRecordsSynchronousSuccessAndAppenderFailureCannotUndoCompletion() throws Exception {
        releaseSource = new CountDownLatch(1);
        try (ConfigurableApplicationContext context = start(false);
                CapturedLog legacyLog = capture(OperationLogger.class);
                CapturedLog taskLog = capture(DownloadTaskOperationLogger.class)) {
            DatasetKey daily = DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily"));
            DatasetDefinition definition = context.getBean(DatasetCatalog.class).find(daily).orElseThrow();
            sourceBody = context.getBean(ObjectMapper.class).writeValueAsBytes(Map.of("code", 0, "data", Map.of(
                    "fields", definition.columns().stream().map(column -> column.name()).toList(), "items", List.of())));
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var response = RestClient.create("http://127.0.0.1:" + port).post().uri("/api/v1/download-tasks")
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("submissionId", UUID.randomUUID(),
                            "pluginId", "tushare_pro", "apiName", "daily", "mode", "SINGLE",
                            "params", Map.of("ts_code", "000001.SZ", "trade_date", "20260901")))
                    .retrieve().toEntity(JsonNode.class);
            assertThat(response.getStatusCode().value()).isEqualTo(202);
            UUID taskId = UUID.fromString(response.getBody().path("taskId").asText());
            assertThat(response.getBody().path("status").asText()).isEqualTo("QUEUED");
            assertThat(response.getHeaders().getFirst("Location")).isEqualTo("/api/v1/download-tasks/" + taskId);
            assertThat(sourceEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(new JdbcTemplate(dataSource()).queryForObject(
                    "SELECT COUNT(*) FROM tensor_download_task WHERE task_id = ?", Integer.class, taskId.toString())).isEqualTo(1);
            assertNoSynchronousSuccess(context, legacyLog);
            assertThat(taskLog.appender.list).filteredOn(event -> event.getFormattedMessage().startsWith("tensor.download_task.accepted "))
                    .singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("outcome=accepted"));
            AtomicInteger appenderFailures = new AtomicInteger();
            ListAppender<ILoggingEvent> failure = new ListAppender<>() {
                @Override public void doAppend(ILoggingEvent event) {
                    appenderFailures.incrementAndGet();
                    throw new IllegalStateException("private-appender-secret");
                }
            };
            failure.start();
            taskLog.logger.addAppender(failure);
            try {
                releaseSource.countDown();
                DownloadTaskRepository repository = context.getBean(DownloadTaskRepository.class);
                assertThat(await(() -> repository.findTask(taskId).orElseThrow().status() == DownloadTask.Status.SUCCEEDED)).isTrue();
                assertThat(await(() -> appenderFailures.get() >= 4)).isTrue();
                assertThat(repository.snapshot(taskId).counts().succeeded()).isEqualTo(1);
                assertThat(sourceRequests).hasValue(1);
                assertNoSynchronousSuccess(context, legacyLog);
                assertThat(taskLog.appender.list).allSatisfy(event -> {
                    assertThat(event.getFormattedMessage()).doesNotContain("controlled-task-token", "private-appender-secret");
                    assertThat(event.getThrowableProxy()).isNull();
                });
                assertThat(taskLog.appender.list).filteredOn(event -> !event.getFormattedMessage().startsWith("tensor.download_task.accepted "))
                        .allSatisfy(event -> assertThat(event.getMDCPropertyMap()).doesNotContainKey("requestId"));
            } finally {
                taskLog.logger.detachAppender(failure);
                failure.stop();
            }
        } finally {
            releaseSource.countDown();
        }
    }

    @Test
    void invalidProductionBudgetAbortsStartupWithoutCallingTheSource() {
        assertThatThrownBy(() -> {
            try (var ignored = start(false, "--tensor.download-tasks.max-queued-tasks=0")) { }
        })
                .hasRootCauseMessage("Task limits must be positive");
        assertThat(sourceRequests).hasValue(0);
        assertThat(events).doesNotContain("downloadTaskCoordinator");
    }

    @Test
    void failedDatabaseInitializationPreventsRunCreationAndSourceExecution() {
        assertThatThrownBy(() -> {
            try (var ignored = start(false, "--spring.flyway.locations=classpath:missing-task-migrations",
                    "--spring.flyway.fail-on-missing-locations=true")) { }
        })
                .isInstanceOf(RuntimeException.class);
        assertThat(events).doesNotContain("downloadTaskRunId", "downloadTaskService", "downloadTaskCoordinator");
        assertThat(sourceRequests).hasValue(0);
    }

    @Test
    void failedStartupRecoveryAbortsTheProductionContextBeforeSourceExecution() {
        assertThatThrownBy(() -> { try (var ignored = start(true)) { } }).isInstanceOf(RuntimeException.class);
        assertThat(events).contains("downloadTaskRunId", "downloadTaskService", "downloadTaskCoordinator");
        assertThat(sourceRequests).hasValue(0);
    }

    private ConfigurableApplicationContext start(boolean failRecovery, String... arguments) {
        return new SpringApplicationBuilder(TensorApplication.class).web(WebApplicationType.SERVLET)
                .initializers(context -> {
                    context.getBeanFactory().registerSingleton("productionTaskTestTypeExcludeFilter", new TestTypeExcludeFilter());
                    context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                        @Override public Object postProcessAfterInitialization(Object bean, String name) {
                            if (List.of("flywayInitializer", "datasetCatalog", "downloadTaskRunId", "downloadTaskService",
                                    "downloadTaskRunner", "downloadTaskCoordinator").contains(name)) events.add(name);
                            if (failRecovery && name.equals("downloadTaskCoordinator")) {
                                context.getBean(JdbcTemplate.class).execute("DROP TABLE tensor_download_batch");
                                context.getBean(JdbcTemplate.class).execute("DROP TABLE tensor_download_task");
                            }
                            return bean;
                        }
                    });
                }).properties("server.port=0", "spring.profiles.active=production", "spring.datasource.hikari.connection-timeout=250",
                        "TENSOR_DB_URL=" + MYSQL.getJdbcUrl(), "TENSOR_DB_USERNAME=" + MYSQL.getUsername(),
                        "TENSOR_DB_PASSWORD=" + MYSQL.getPassword(), "TENSOR_TUSHARE_TOKEN=controlled-task-token",
                        "TENSOR_TUSHARE_BASE_URL=http://127.0.0.1:" + source.getAddress().getPort()).run(arguments);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static Flyway flyway() { return Flyway.configure().dataSource(dataSource()).cleanDisabled(false).load(); }

    private static void assertNoSynchronousSuccess(ConfigurableApplicationContext context, CapturedLog legacyLog) {
        assertThat(legacyLog.appender.list).noneMatch(event -> event.getFormattedMessage().startsWith("tensor.operation.completed"));
        assertThat(context.getBean(MeterRegistry.class).getMeters())
                .noneMatch(meter -> meter.getId().getName().startsWith("tensor_download_"));
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        return condition.getAsBoolean();
    }

    private static CapturedLog capture(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override protected void append(ILoggingEvent event) { event.prepareForDeferredProcessing(); super.append(event); }
        };
        appender.list = new CopyOnWriteArrayList<>();
        appender.start();
        logger.addAppender(appender);
        return new CapturedLog(logger, appender);
    }

    private record CapturedLog(Logger logger, ListAppender<ILoggingEvent> appender) implements AutoCloseable {
        @Override public void close() { logger.detachAppender(appender); appender.stop(); }
    }

    private static final class TestTypeExcludeFilter extends TypeExcludeFilter {
        @Override public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
            return reader.getClassMetadata().getClassName().equals("com.akkc.tensor.web.GlobalExceptionHandlerTest$FailureController");
        }
        @Override public boolean equals(Object other) { return other instanceof TestTypeExcludeFilter; }
        @Override public int hashCode() { return TestTypeExcludeFilter.class.hashCode(); }
    }
}
