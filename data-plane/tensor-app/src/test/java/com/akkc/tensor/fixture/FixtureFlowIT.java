package com.akkc.tensor.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.persistence.ExistingKeyRepository;
import com.akkc.tensor.core.persistence.GenericUpsertRepository;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.query.GenericQueryRepository;
import com.akkc.tensor.core.query.QueryCriteria;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.fixture.FixtureConfiguration;
import com.akkc.tensor.plugin.fixture.FixturePlugin;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class FixtureFlowIT {
    private static final String TABLE = "fixture__fixture_daily";
    private static final PluginId PLUGIN_ID = PluginId.of("fixture");
    private static final ApiName API_NAME = ApiName.of("fixture_daily");
    private static final DatasetKey DATASET_KEY = DatasetKey.of(PLUGIN_ID, API_NAME);
    private static final Instant INGESTED_AT = Instant.parse("2026-08-07T08:09:10.123Z");
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
            .withDatabaseName("tensor")
            .withUsername("tensor")
            .withPassword("tensor")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs");

    private static DriverManagerDataSource rawDataSource;
    private static AnnotationConfigApplicationContext acceptanceContext;

    @BeforeAll
    static void startFixtureEnvironment() {
        MYSQL.start();
        rawDataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway flyway = Flyway.configure()
                .dataSource(rawDataSource)
                .locations("classpath:db/migration")
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        acceptanceContext = fixtureContext("acceptance");
        assertThat(acceptanceContext.getBeansOfType(DataSourcePlugin.class)).hasSize(1);
        assertThat(acceptanceContext.getBeansOfType(DatasetAdapter.class)).hasSize(1);
    }

    @AfterAll
    static void stopFixtureEnvironment() {
        if (acceptanceContext != null) {
            acceptanceContext.close();
        }
        MYSQL.stop();
    }

    @BeforeEach
    void clearFixtureTable() throws SQLException {
        try (Connection connection = rawDataSource.getConnection();
                var statement = connection.prepareStatement("DELETE FROM " + TABLE)) {
            statement.executeUpdate();
        }
    }

    @Test
    void registeredFixtureFlowsThroughAdapterPersistenceAndTypedQuery() {
        Flow flow = flow();

        assertThat(flow.plugin().descriptor().downloadAvailable()).isTrue();
        assertThat(flow.adapter()).isInstanceOf(GenericDatasetAdapter.class);
        AdaptedBatch batch = adapt(flow, "SUCCESS", INGESTED_AT);

        assertThat(flow.persistenceService().persist(batch)).isEqualTo(new WriteCounts(1, 0));
        DatasetPage page = query(flow);

        assertThat(page.columns()).containsExactly(
                "ts_code", "trade_date", "amount", "note", "source_plugin", "source_api", "ingested_at");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(new ArrayList<>(row.keySet())).containsExactlyElementsOf(page.columns());
            assertThat(new ArrayList<>(row.values())).containsExactly(
                    "000001.SZ",
                    LocalDate.of(2026, 8, 7),
                    new BigDecimal("11.230000000000000000"),
                    null,
                    "fixture",
                    "fixture_daily",
                    INGESTED_AT);
        });
    }

    @Test
    void emptyFixtureBatchPersistsWithoutTransactionOrConnection() throws SQLException {
        Flow flow = flow();
        AdaptedBatch batch = adapt(flow, "EMPTY", INGESTED_AT);
        assertThat(batch.columns()).containsExactly("ts_code", "trade_date", "amount", "note");
        assertThat(batch.rows()).isEmpty();
        flow.dataSource().resetObservations();

        assertThat(flow.persistenceService().persist(batch)).isEqualTo(new WriteCounts(0, 0));

        assertThat(flow.dataSource().connectionCount()).isZero();
        assertThat(flow.dataSource().delegatedMarkedBatchCount()).isZero();
        assertThat(fixtureRowCount()).isZero();
    }

    @Test
    void typeFailureStopsAtAdapterWithoutTouchingDatabase() throws SQLException {
        Flow flow = flow();
        flow.dataSource().resetObservations();

        assertThatThrownBy(() -> adapt(flow, "TYPE_FAILURE", INGESTED_AT))
                .isInstanceOfSatisfying(AdapterException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
                    assertThat(exception).hasMessage(
                            "Invalid adapter value: api=fixture_daily, row=0, field=amount");
                });

        assertThat(flow.dataSource().connectionCount()).isZero();
        assertThat(flow.dataSource().delegatedMarkedBatchCount()).isZero();
        assertThat(fixtureRowCount()).isZero();
    }

    @Test
    void persistenceFailureAfterRealBatchExecutionRollsBackSeedUpdate() {
        Flow flow = flow();
        assertThat(flow.persistenceService().persist(adapt(flow, "SUCCESS", INGESTED_AT)))
                .isEqualTo(new WriteCounts(1, 0));
        flow.dataSource().resetObservations();

        AdaptedBatch failure = adapt(flow, "PERSISTENCE_FAILURE", INGESTED_AT.plusMillis(1));
        assertThatThrownBy(() -> flow.persistenceService().persist(failure))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .hasRootCauseMessage("Fixture persistence failure");
        assertThat(flow.dataSource().delegatedMarkedBatchCount()).isEqualTo(1);

        DatasetPage page = query(flow);
        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(row.get("amount")).isEqualTo(new BigDecimal("11.230000000000000000"));
            assertThat(row.get("note")).isNull();
            assertThat(row.get("ingested_at")).isEqualTo(INGESTED_AT);
        });
    }

    @Test
    void productionProfileNeverRegistersFixtureEvenWhenEnabled() {
        try (AnnotationConfigApplicationContext context = fixtureContext("production")) {
            assertThat(context.getBeansOfType(DataSourcePlugin.class)).isEmpty();
            assertThat(context.getBeansOfType(DatasetAdapter.class)).isEmpty();
            assertThat(context.getBeansOfType(FixturePlugin.class)).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext fixtureContext(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "fixture-test", Map.of("tensor.plugins.fixture.enabled", "true")));
        context.register(FixtureConfiguration.class);
        context.refresh();
        return context;
    }

    private static Flow flow() {
        List<DataSourcePlugin> plugins = List.copyOf(
                acceptanceContext.getBeansOfType(DataSourcePlugin.class).values());
        List<DatasetAdapter> adapters = List.copyOf(
                acceptanceContext.getBeansOfType(DatasetAdapter.class).values());
        assertThat(plugins).hasSize(1);
        assertThat(adapters).hasSize(1);

        DataSourcePlugin plugin = new PluginRegistry(plugins).find(PLUGIN_ID).orElseThrow();
        DatasetAdapter adapter = new AdapterRegistry(adapters).find(DATASET_KEY).orElseThrow();
        FixtureFaultDataSource dataSource = new FixtureFaultDataSource(rawDataSource);
        DatasetCatalog catalog = new DatasetStartupValidator(
                        List.of(adapter.definition()), new SchemaInspector(dataSource))
                .validate();
        assertThat(catalog.find(DATASET_KEY)).contains(adapter.definition());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        PersistenceService persistenceService = new PersistenceService(
                catalog,
                new DatasetLockManager(),
                new ExistingKeyRepository(jdbcTemplate),
                new GenericUpsertRepository(jdbcTemplate),
                new DataSourceTransactionManager(dataSource));
        DatasetQueryService queryService = new DatasetQueryService(
                catalog, new GenericQueryRepository(jdbcTemplate));
        return new Flow(plugin, adapter, persistenceService, queryService, dataSource);
    }

    private static AdaptedBatch adapt(Flow flow, String scenario, Instant ingestedAt) {
        return flow.adapter().adapt(
                flow.plugin().download(API_NAME, Map.of("scenario", scenario)), ingestedAt);
    }

    private static DatasetPage query(Flow flow) {
        return flow.queryService().query(
                DATASET_KEY, new QueryCriteria("000001.SZ", null, null, null, null, 1, 20));
    }

    private static long fixtureRowCount() throws SQLException {
        try (Connection connection = rawDataSource.getConnection();
                var statement = connection.prepareStatement("SELECT COUNT(*) FROM " + TABLE);
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private interface ObservedDataSource extends DataSource {
        void resetObservations();

        int connectionCount();

        int delegatedMarkedBatchCount();
    }

    private static final class FixtureFaultDataSource implements ObservedDataSource {
        private static final String FAILURE_MARKER = "PERSISTENCE_FAILURE";

        private final DataSource delegate;
        private final AtomicInteger connectionCount = new AtomicInteger();
        private final AtomicInteger delegatedMarkedBatchCount = new AtomicInteger();

        private FixtureFaultDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            connectionCount.incrementAndGet();
            return connectionProxy(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            connectionCount.incrementAndGet();
            return connectionProxy(delegate.getConnection(username, password));
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            return delegate.unwrap(type);
        }

        @Override
        public boolean isWrapperFor(Class<?> type) throws SQLException {
            return delegate.isWrapperFor(type);
        }

        @Override
        public void resetObservations() {
            connectionCount.set(0);
            delegatedMarkedBatchCount.set(0);
        }

        @Override
        public int connectionCount() {
            return connectionCount.get();
        }

        @Override
        public int delegatedMarkedBatchCount() {
            return delegatedMarkedBatchCount.get();
        }

        private Connection connectionProxy(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    FixtureFlowIT.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(connection, method, arguments);
                        if ("prepareStatement".equals(method.getName()) && result instanceof PreparedStatement statement) {
                            return statementProxy(statement);
                        }
                        return result;
                    });
        }

        private PreparedStatement statementProxy(PreparedStatement statement) {
            boolean[] marked = {false};
            return (PreparedStatement) Proxy.newProxyInstance(
                    FixtureFlowIT.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        if ("setString".equals(method.getName())
                                && arguments != null
                                && arguments.length == 2
                                && FAILURE_MARKER.equals(arguments[1])) {
                            marked[0] = true;
                        }
                        Object result = invoke(statement, method, arguments);
                        if ("executeBatch".equals(method.getName()) && marked[0]) {
                            delegatedMarkedBatchCount.incrementAndGet();
                            throw new SQLException("Fixture persistence failure");
                        }
                        return result;
                    });
        }

        private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }

    private record Flow(
            DataSourcePlugin plugin,
            DatasetAdapter adapter,
            PersistenceService persistenceService,
            DatasetQueryService queryService,
            ObservedDataSource dataSource) {
    }
}
