package com.akkc.tensor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.core.download.task.BatchCommitService;
import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskCoordinator;
import com.akkc.tensor.core.download.task.DownloadTaskJson;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.persistence.ExistingKeyRepository;
import com.akkc.tensor.core.persistence.GenericUpsertRepository;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.fixture.FixtureConfiguration;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DownloadTaskConfigurationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-12T02:03:04Z"), ZoneOffset.UTC);
    static final UUID OLD_RUN = UUID.fromString("00000000-0000-0000-0000-000000000018");

    @BeforeEach
    void resetSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(rawDataSource());
        jdbc.execute("DROP TABLE IF EXISTS tensor_download_batch");
        jdbc.execute("DROP TABLE IF EXISTS tensor_download_task");
        jdbc.execute("DROP TABLE IF EXISTS fixture__fixture_daily");
    }

    @Test
    void initializesDatabaseAndValidatedCatalogBeforeRunIdThenStartsTheSharedRun() throws Exception {
        Fixture fixture = new Fixture(Failure.NONE);
        try (AnnotationConfigApplicationContext context = context(fixture)) {
            context.refresh();
            UUID runId = context.getBean("downloadTaskRunId", UUID.class);
            assertThat(runId).isNotEqualTo(OLD_RUN);
            assertThat(fixture.events).containsExactly("database", "catalog", "runId", "service", "runner", "coordinator");
            assertThat(context.getBeansOfType(UUID.class)).hasSize(1);
            assertThat(context.getBeansOfType(DownloadTaskCoordinator.class)).hasSize(1);
            assertThat(context.getBean(DownloadTaskCoordinator.class).isRunning()).isTrue();
            DownloadTask recovered = fixture.repository.findTask(fixture.oldTaskId).orElseThrow();
            assertThat(recovered.status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
            assertThat(recovered.activeRunId()).isEqualTo(OLD_RUN);
            assertThat(fixture.downloads).hasValue(0);

            DownloadTask accepted = context.getBean(DownloadTaskService.class).submit(
                    fixture.submission()).task();
            assertThat(accepted.activeRunId()).isEqualTo(runId);
            assertThat(await(() -> fixture.repository.findTask(accepted.taskId()).orElseThrow().status()
                    == DownloadTask.Status.SUCCEEDED)).isTrue();
            DownloadTask completed = fixture.repository.findTask(accepted.taskId()).orElseThrow();
            assertThat(completed.activeRunId()).isEqualTo(runId);
            assertThat(completed.requestCount()).isEqualTo(1);
            assertThat(fixture.downloads).hasValue(1);
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM fixture__fixture_daily", Integer.class))
                    .isEqualTo(1);
        }
    }

    @Test
    void databaseInitializationFailurePreventsRunIdAndSourceExecution() {
        Fixture fixture = new Fixture(Failure.DATABASE);
        try (AnnotationConfigApplicationContext context = context(fixture)) {
            assertThatThrownBy(context::refresh).isInstanceOf(RuntimeException.class);
            assertThat(fixture.events).isEmpty();
            assertThat(fixture.downloads).hasValue(0);
            assertThat(fixture.tasks).isNull();
        }
    }

    @Test
    void catalogInspectionFailurePreventsRunIdAndSourceExecution() {
        Fixture fixture = new Fixture(Failure.CATALOG);
        try (AnnotationConfigApplicationContext context = context(fixture)) {
            assertThatThrownBy(context::refresh).hasRootCauseMessage("Schema inspection failed");
            assertThat(fixture.events).containsExactly("database");
            assertThat(fixture.downloads).hasValue(0);
            assertThat(fixture.tasks).isNull();
        }
    }

    @Test
    void startupRecoveryFailureFailsRefreshWithoutAdmittingOrSending() {
        Fixture fixture = new Fixture(Failure.RECOVERY);
        try (AnnotationConfigApplicationContext context = context(fixture)) {
            assertThatThrownBy(context::refresh).isInstanceOf(ApplicationContextException.class);
            assertThat(fixture.downloads).hasValue(0);
            fixture.unavailable.set(false);
            assertThat(fixture.repository.findTask(fixture.oldTaskId).orElseThrow().status())
                    .isEqualTo(DownloadTask.Status.QUEUED);
            assertThatThrownBy(() -> fixture.tasks.submit(fixture.submission()))
                    .isInstanceOfSatisfying(TensorException.class,
                            error -> assertThat(error.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED));
            assertThat(fixture.repository.queuedCount()).isEqualTo(1);
        }
    }

    private static AnnotationConfigApplicationContext context(Fixture fixture) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(DownloadTaskConfiguration.class, DatabaseConfiguration.class);
        context.registerBean(Fixture.class, () -> fixture);
        context.registerBean(Clock.class, () -> CLOCK);
        context.registerBean(DownloadTaskRepository.class, () -> fixture.repository);
        context.registerBean(DownloadTaskService.class, () -> {
            fixture.tasks = new DownloadTaskService(new PluginRegistry(List.of(fixture.plugin)),
                    context.getBean(DatasetCatalog.class), new AdapterRegistry(List.of(fixture.adapter)),
                    new ParameterValidator(), fixture.repository, fixture.json, CLOCK,
                    context.getBean("downloadTaskRunId", UUID.class), DownloadTaskService.Settings.defaults());
            fixture.events.add("service");
            return fixture.tasks;
        });
        context.registerBean(DownloadTaskRunner.class, () -> {
            DownloadTaskService tasks = context.getBean(DownloadTaskService.class);
            PersistenceService persistence = new PersistenceService(context.getBean(DatasetCatalog.class),
                    new DatasetLockManager(), new ExistingKeyRepository(fixture.jdbc),
                    new GenericUpsertRepository(fixture.jdbc), fixture.transactions);
            DownloadTaskRunner runner = new DownloadTaskRunner(tasks, fixture.repository,
                    new BatchCommitService(persistence, fixture.repository, CLOCK), fixture.json, CLOCK,
                    context.getBean("downloadTaskRunId", UUID.class), DownloadTaskRunner.Settings.defaults());
            fixture.events.add("runner");
            return runner;
        });
        context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof DataSourceScriptDatabaseInitializer) {
                    fixture.events.add("database");
                    fixture.repository.insert(new DownloadTaskRepository.NewTask(fixture.oldTaskId,
                            UUID.randomUUID(), fixture.adapter.datasetKey(), DownloadMode.SINGLE,
                            Map.of("scenario", "SUCCESS"), "0".repeat(64),
                            "{\"schemaVersion\":1,\"mode\":\"SINGLE\"}", OLD_RUN, CLOCK.instant()));
                    if (fixture.failure == Failure.CATALOG) fixture.unavailable.set(true);
                } else if (name.equals("downloadTaskRunId")) {
                    fixture.events.add("runId");
                } else if (bean instanceof DownloadTaskCoordinator) {
                    fixture.events.add("coordinator");
                    if (fixture.failure == Failure.RECOVERY) fixture.unavailable.set(true);
                }
                return bean;
            }
        });
        return context;
    }

    @Import(DatabaseInitializationDependencyConfigurer.class)
    static final class DatabaseConfiguration {
        @Bean
        DataSourceScriptDatabaseInitializer databaseInitializer(Fixture fixture) {
            DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
            settings.setMode(DatabaseInitializationMode.ALWAYS);
            List<String> schemas = new ArrayList<>(List.of(
                    "classpath:db/migration/V6__create_fixture_tables.sql",
                    "classpath:db/migration/V8__create_download_task_tables.sql"));
            if (fixture.failure == Failure.DATABASE) schemas.add(schemas.getFirst());
            settings.setSchemaLocations(schemas);
            // Spring's initializer runs these original migrations through ScriptUtils.
            return new DataSourceScriptDatabaseInitializer(fixture.dataSource, settings);
        }

        @Bean
        @DependsOnDatabaseInitialization
        DatasetCatalog datasetCatalog(Fixture fixture) {
            DatasetCatalog catalog = new DatasetStartupValidator(List.of(fixture.adapter.definition()),
                    new SchemaInspector(fixture.dataSource)).validate();
            assertThat(catalog.find(fixture.adapter.datasetKey())).contains(fixture.adapter.definition());
            fixture.events.add("catalog");
            return catalog;
        }
    }

    enum Failure { NONE, DATABASE, CATALOG, RECOVERY }

    static final class Fixture {
        final Failure failure;
        final List<String> events = new ArrayList<>();
        final AtomicBoolean unavailable = new AtomicBoolean();
        final AtomicInteger downloads = new AtomicInteger();
        final UUID oldTaskId = UUID.randomUUID();
        final DownloadTaskJson json = new DownloadTaskJson();
        final FixtureConfiguration sources = new FixtureConfiguration();
        final DatasetAdapter adapter = sources.fixtureDatasetAdapter();
        final DataSourcePlugin delegate = sources.fixturePlugin();
        final DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override public PluginDescriptor descriptor() { return delegate.descriptor(); }
            @Override public PluginReadiness readiness() { return delegate.readiness(); }
            @Override public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
                downloads.incrementAndGet();
                return delegate.download(apiName, params);
            }
        };
        final DelegatingDataSource dataSource = new DelegatingDataSource(rawDataSource()) {
            @Override public Connection getConnection() throws SQLException {
                if (unavailable.get()) throw new SQLException("Controlled database outage");
                return super.getConnection();
            }
        };
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        final DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        final DownloadTaskRepository repository = new DownloadTaskRepository(jdbc, transactions, json);
        DownloadTaskService tasks;

        Fixture(Failure failure) { this.failure = failure; }

        DownloadTaskService.Submission submission() {
            return new DownloadTaskService.Submission(UUID.randomUUID(), adapter.datasetKey(),
                    DownloadMode.SINGLE, Map.of("scenario", "SUCCESS"));
        }
    }

    private static DriverManagerDataSource rawDataSource() {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        return condition.getAsBoolean();
    }
}
