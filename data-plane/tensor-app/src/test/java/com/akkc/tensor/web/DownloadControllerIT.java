package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.persistence.ExistingKeyRepository;
import com.akkc.tensor.core.persistence.GenericUpsertRepository;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.validation.ParameterValidator.ParameterValidationException;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.plugin.fixture.FixtureConfiguration;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.web.dto.DownloadResponse;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.BiFunction;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class DownloadControllerIT {
    private static final String TABLE = "fixture__fixture_daily";
    private static final String REQUEST_ID = "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";
    private static final PluginId PLUGIN_ID = PluginId.of("fixture");
    private static final ApiName API_NAME = ApiName.of("fixture_daily");
    private static final DatasetKey DATASET_KEY = DatasetKey.of(PLUGIN_ID, API_NAME);
    private static final Instant INGESTED_AT = Instant.parse("2026-08-07T08:09:10.123Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(INGESTED_AT, ZoneOffset.UTC);
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4.6"))
            .withDatabaseName("tensor")
            .withUsername("tensor")
            .withPassword("tensor")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_as_cs",
                    // Required for non-root tensor to create the real SIGNAL rollback trigger.
                    "--log-bin-trust-function-creators=1");

    private static DriverManagerDataSource dataSource;
    private static AnnotationConfigApplicationContext fixtureContext;
    private static DataSourcePlugin fixturePlugin;
    private static DatasetAdapter fixtureAdapter;
    private static LocalValidatorFactoryBean beanValidator;

    @BeforeAll
    static void startEnvironment() {
        MYSQL.start();
        dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        fixtureContext = new AnnotationConfigApplicationContext();
        fixtureContext.getEnvironment().setActiveProfiles("acceptance");
        fixtureContext.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "m09-t03", Map.of("tensor.plugins.fixture.enabled", "true")));
        fixtureContext.register(FixtureConfiguration.class);
        fixtureContext.refresh();
        fixturePlugin = fixtureContext.getBean(DataSourcePlugin.class);
        fixtureAdapter = fixtureContext.getBean(DatasetAdapter.class);

        beanValidator = new LocalValidatorFactoryBean();
        beanValidator.afterPropertiesSet();
    }

    @BeforeEach
    void clearDatabase() {
        JdbcTemplate jdbc = jdbc();
        jdbc.execute("DROP TRIGGER IF EXISTS m09_t03_fail");
        jdbc.update("DELETE FROM " + TABLE);
        MDC.clear();
    }

    @AfterAll
    static void stopEnvironment() {
        if (beanValidator != null) {
            beanValidator.close();
        }
        if (fixtureContext != null) {
            fixtureContext.close();
        }
        MYSQL.stop();
    }

    @Test
    void exposesExactSurfacesAndImmutableDtos() throws Exception {
        assertThat(Modifier.isFinal(DownloadService.class.getModifiers())).isTrue();
        assertThat(DownloadService.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        PluginRegistry.class,
                        AdapterRegistry.class,
                        ParameterValidator.class,
                        PersistenceService.class,
                        Clock.class));
        assertThat(Arrays.stream(DownloadService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())))
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("execute");
                    assertThat(method.getParameterTypes()).containsExactly(
                            PluginId.class, ApiName.class, Map.class, RequestId.class);
                    assertThat(method.getReturnType()).isEqualTo(DownloadResult.class);
                });

        assertThat(Modifier.isFinal(DownloadController.class.getModifiers())).isTrue();
        assertThat(DownloadController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(DownloadService.class));
        assertThat(Arrays.stream(DownloadController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())))
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("download");
                    assertThat(method.getParameterTypes()).containsExactly(DownloadRequest.class);
                    assertThat(method.getReturnType()).isEqualTo(DownloadResponse.class);
                });

        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("scenario", "SUCCESS");
        DownloadRequest request = new DownloadRequest("fixture", "fixture_daily", source);
        source.put("extra", "not-copied");
        assertThat(request.params()).containsExactly(Map.entry("scenario", "SUCCESS"));
        assertThatThrownBy(() -> request.params().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(beanValidator.validate(new DownloadRequest("Fixture", "FIXTURE", null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("pluginId", "apiName", "params");

        RequestId requestId = requestId();
        DownloadResponse empty = DownloadResponse.from(new DownloadResult(
                requestId, DownloadOutcome.EMPTY, PLUGIN_ID, API_NAME, 0, 0, 0,
                "下载成功，0 条数据"));
        DownloadResponse success = DownloadResponse.from(new DownloadResult(
                requestId, DownloadOutcome.SUCCESS, PLUGIN_ID, API_NAME, 1, 1, 0,
                "下载成功"));
        assertThat(Arrays.stream(DownloadResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("requestId", "outcome", "pluginId", "apiName", "sourceRowCount",
                        "insertedRows", "updatedRows", "message");
        assertThat(empty.outcome()).isEqualTo(DownloadOutcome.EMPTY);
        assertThat(success.sourceRowCount()).isEqualTo(1);
        assertThatThrownBy(() -> new DownloadResponse(
                " ", DownloadOutcome.EMPTY, "fixture", "fixture_daily", 0, 0, 0, "ok"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadResponse(
                REQUEST_ID, DownloadOutcome.SUCCESS, "fixture", "fixture_daily", -1, 0, 0, "ok"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadResponse(
                REQUEST_ID, DownloadOutcome.EMPTY, "fixture", "fixture_daily", 1, 0, 0, "ok"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadResponse(
                REQUEST_ID, DownloadOutcome.SUCCESS, "fixture", "fixture_daily", 0, 0, 0, "ok"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DownloadResponse.from(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("result");
    }

    @Test
    void rejectsInvalidRequestAndParametersBeforeDownload() throws Exception {
        DownloadService controllerService = mock(DownloadService.class);
        MockMvc controllerMvc = mockMvc(controllerService);
        controllerMvc.perform(post("/api/v1/downloads")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pluginId\":\"Fixture\",\"apiName\":\"fixture_daily\",\"params\":{}}"))
                .andExpect(status().isBadRequest());
        controllerMvc.perform(post("/api/v1/downloads")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pluginId\":\"fixture\",\"apiName\":\"fixture_daily\",\"params\":null}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(controllerService);

        ApiDescriptor requiredApi = requiredNoDefaultApi();
        CountingPlugin requiredPlugin = new CountingPlugin(new ReturningPlugin(
                readyDescriptor(requiredApi), (apiName, params) -> {
                    throw new AssertionError("download must not run");
                }));
        CountingAdapter requiredAdapter = new CountingAdapter(fixtureAdapter);
        DownloadService requiredService = service(
                requiredPlugin, requiredAdapter, mock(PersistenceService.class), FIXED_CLOCK);
        assertThatThrownBy(() -> requiredService.execute(PLUGIN_ID, API_NAME, Map.of(), requestId()))
                .isInstanceOfSatisfying(ParameterValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.PARAM_REQUIRED));
        assertThat(requiredPlugin.downloads).isZero();
        assertThat(requiredAdapter.adaptations).isZero();

        CountingPlugin plugin = new CountingPlugin(fixturePlugin);
        CountingAdapter adapter = new CountingAdapter(fixtureAdapter);
        DownloadService fixtureService = realService(plugin, adapter, FIXED_CLOCK);
        assertThatThrownBy(() -> fixtureService.execute(
                        PLUGIN_ID, API_NAME, Map.of("scenario", "UNKNOWN"), requestId()))
                .isInstanceOfSatisfying(ParameterValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.PARAM_INVALID));
        assertThatThrownBy(() -> fixtureService.execute(
                        PLUGIN_ID, API_NAME, Map.of("scenario", 1), requestId()))
                .isInstanceOfSatisfying(ParameterValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.PARAM_INVALID));
        assertThat(plugin.downloads).isZero();
        assertThat(adapter.adaptations).isZero();
        assertThat(rowCount()).isZero();
    }

    @Test
    void rejectsUnavailablePluginAndMisconfiguredDatasetBeforeDownload() {
        DataSourcePlugin unavailable = mock(DataSourcePlugin.class);
        when(unavailable.descriptor()).thenReturn(unavailableDescriptor());
        when(unavailable.readiness()).thenReturn(new PluginReadiness(false, false, false, "Unavailable"));
        DownloadService unavailableService = new DownloadService(
                new PluginRegistry(List.of(unavailable)),
                new AdapterRegistry(List.of(fixtureAdapter)),
                new ParameterValidator(),
                mock(PersistenceService.class),
                FIXED_CLOCK);
        assertThatThrownBy(() -> unavailableService.execute(
                        PLUGIN_ID, API_NAME, Map.of("scenario", "SUCCESS"), requestId()))
                .isInstanceOfSatisfying(TensorException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
                    assertThat(exception).hasMessage("Download plugin is unavailable");
                });
        verify(unavailable, never()).download(any(), any());

        CountingPlugin unknownApiPlugin = new CountingPlugin(fixturePlugin);
        DownloadService unknownApiService = service(
                unknownApiPlugin, fixtureAdapter, mock(PersistenceService.class), FIXED_CLOCK);
        assertThatThrownBy(() -> unknownApiService.execute(
                        PLUGIN_ID,
                        ApiName.of("missing_api"),
                        Map.of("scenario", "SUCCESS"),
                        requestId()))
                .isInstanceOfSatisfying(TensorException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED);
                    assertThat(exception).hasMessage("Download dataset is unavailable");
                });
        assertThat(unknownApiPlugin.downloads).isZero();

        CountingPlugin missingAdapterPlugin = new CountingPlugin(fixturePlugin);
        DownloadService missingAdapterService = new DownloadService(
                new PluginRegistry(List.of(missingAdapterPlugin)),
                new AdapterRegistry(List.of()),
                new ParameterValidator(),
                mock(PersistenceService.class),
                FIXED_CLOCK);
        assertThatThrownBy(() -> missingAdapterService.execute(
                        PLUGIN_ID, API_NAME, Map.of("scenario", "SUCCESS"), requestId()))
                .isInstanceOfSatisfying(TensorException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED);
                    assertThat(exception).hasMessage("Download dataset is unavailable");
                });
        assertThat(missingAdapterPlugin.downloads).isZero();
        assertThat(rowCount()).isZero();
    }

    @Test
    void preservesSourceFailureAndRejectsInvalidEnvelopesBeforeAdaptation() {
        CountingPlugin sourcePlugin = new CountingPlugin(fixturePlugin);
        CountingAdapter adapter = new CountingAdapter(fixtureAdapter);
        PersistenceService persistence = mock(PersistenceService.class);
        DownloadService fixtureService = service(sourcePlugin, adapter, persistence, FIXED_CLOCK);
        assertThatThrownBy(() -> fixtureService.execute(
                        PLUGIN_ID, API_NAME, Map.of("scenario", "SOURCE_FAILURE"), requestId()))
                .isInstanceOfSatisfying(SourceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.SOURCE_UNAVAILABLE);
                    assertThat(exception).hasMessage("Fixture source unavailable");
                });

        DownloadEnvelope failure = failureEnvelope();
        DownloadEnvelope mismatched = identityMismatchEmptyEnvelope();

        assertInvalidEnvelope(failure, "Safe source failure", adapter, persistence);
        assertInvalidEnvelope(null, "Source returned an invalid payload", adapter, persistence);
        assertInvalidEnvelope(mismatched, "Source returned an invalid payload", adapter, persistence);
        assertThat(sourcePlugin.downloads).isEqualTo(1);
        assertThat(adapter.adaptations).isZero();
        verifyNoInteractions(persistence);
        assertThat(rowCount()).isZero();
    }

    @Test
    void returnsEmptyResponseWithoutClockAdaptationOrPersistence() throws Exception {
        CountingPlugin plugin = new CountingPlugin(fixturePlugin);
        CountingAdapter adapter = new CountingAdapter(fixtureAdapter);
        Clock clock = mock(Clock.class);
        PersistenceService persistence = mock(PersistenceService.class);
        DownloadService service = service(plugin, adapter, persistence, clock);

        MvcResult result = mockMvc(service).perform(post("/api/v1/downloads")
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .contentType(APPLICATION_JSON)
                        .content(downloadJson("EMPTY")))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo(
                "{\"requestId\":\"" + REQUEST_ID
                        + "\",\"outcome\":\"EMPTY\",\"pluginId\":\"fixture\",\"apiName\":\"fixture_daily\""
                        + ",\"sourceRowCount\":0,\"insertedRows\":0,\"updatedRows\":0"
                        + ",\"message\":\"下载成功，0 条数据\"}");
        assertThat(plugin.downloads).isEqualTo(1);
        assertThat(adapter.adaptations).isZero();
        verifyNoInteractions(clock, persistence);
        assertThat(rowCount()).isZero();
    }

    @Test
    void persistsSuccessBeforeReturningExactResponse() throws Exception {
        DownloadService service = realService(fixturePlugin, fixtureAdapter, FIXED_CLOCK);

        MvcResult result = mockMvc(service).perform(post("/api/v1/downloads")
                        .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                        .contentType(APPLICATION_JSON)
                        .content(downloadJson("SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo(
                "{\"requestId\":\"" + REQUEST_ID
                        + "\",\"outcome\":\"SUCCESS\",\"pluginId\":\"fixture\",\"apiName\":\"fixture_daily\""
                        + ",\"sourceRowCount\":1,\"insertedRows\":1,\"updatedRows\":0"
                        + ",\"message\":\"下载成功\"}");
        assertThat(row()).isEqualTo(new StoredRow(
                "000001.SZ",
                LocalDate.of(2026, 8, 7),
                new BigDecimal("11.230000000000000000"),
                null,
                "fixture",
                "fixture_daily",
                INGESTED_AT));
    }

    @Test
    void reportsAnUpdateForARepeatedUniqueFixtureRow() {
        DownloadService service = realService(fixturePlugin, fixtureAdapter, FIXED_CLOCK);

        DownloadResult first = service.execute(
                PLUGIN_ID, API_NAME, Map.of("scenario", "SUCCESS"), requestId());
        DownloadResult second = service.execute(
                PLUGIN_ID, API_NAME, Map.of("scenario", "SUCCESS"), requestId());

        assertThat(List.of(first.sourceRowCount(), first.insertedRows(), first.updatedRows()))
                .containsExactly(1L, 1L, 0L);
        assertThat(List.of(second.sourceRowCount(), second.insertedRows(), second.updatedRows()))
                .containsExactly(1L, 0L, 1L);
        assertThat(first.sourceRowCount()).isEqualTo(first.insertedRows() + first.updatedRows());
        assertThat(second.sourceRowCount()).isEqualTo(second.insertedRows() + second.updatedRows());
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void stopsAdapterFailureBeforeDatabaseAccess() {
        DownloadService service = realService(fixturePlugin, fixtureAdapter, FIXED_CLOCK);

        assertThatThrownBy(() -> service.execute(
                        PLUGIN_ID, API_NAME, Map.of("scenario", "TYPE_FAILURE"), requestId()))
                .isInstanceOfSatisfying(AdapterException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
                    assertThat(exception).hasMessage(
                            "Invalid adapter value: api=fixture_daily, row=0, field=amount");
                });
        assertThat(rowCount()).isZero();
    }

    @Test
    void rollsBackPersistenceFailureWithoutReturningSuccess() {
        DownloadService seedService = realService(fixturePlugin, fixtureAdapter, FIXED_CLOCK);
        seedService.execute(PLUGIN_ID, API_NAME, Map.of("scenario", "SUCCESS"), requestId());
        StoredRow seed = row();
        JdbcTemplate jdbc = jdbc();
        jdbc.execute("CREATE TRIGGER m09_t03_fail BEFORE UPDATE ON " + TABLE
                + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fixture persistence failure'");

        try {
            DownloadService failingService = realService(
                    fixturePlugin,
                    fixtureAdapter,
                    Clock.fixed(INGESTED_AT.plusMillis(1), ZoneOffset.UTC));
            assertThatThrownBy(() -> failingService.execute(
                            PLUGIN_ID,
                            API_NAME,
                            Map.of("scenario", "PERSISTENCE_FAILURE"),
                            requestId()))
                    .isInstanceOf(DataAccessException.class)
                    .hasRootCauseMessage("Fixture persistence failure");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS m09_t03_fail");
        }

        assertThat(rowCount()).isEqualTo(1);
        assertThat(row()).isEqualTo(seed);
        assertThat(seed.note()).isNull();
        assertThat(seed.amount()).isEqualByComparingTo("11.230000000000000000");
        assertThat(seed.ingestedAt()).isEqualTo(INGESTED_AT);
    }

    @Test
    void rejectsAnOuterTransactionBeforeUpstreamWork() {
        CountingPlugin plugin = new CountingPlugin(fixturePlugin);
        DownloadService service = realService(plugin, fixtureAdapter, FIXED_CLOCK);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.execute(status -> service.execute(
                        PLUGIN_ID, API_NAME, Map.of("scenario", "SUCCESS"), requestId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Download orchestration must not run in a transaction");
        assertThat(plugin.downloads).isZero();
        assertThat(rowCount()).isZero();
    }

    private static DownloadService realService(
            DataSourcePlugin plugin, DatasetAdapter adapter, Clock clock) {
        DatasetCatalog catalog = new DatasetStartupValidator(
                List.of(adapter.definition()), new SchemaInspector(dataSource)).validate();
        JdbcTemplate jdbc = jdbc();
        return new DownloadService(
                new PluginRegistry(List.of(plugin)),
                new AdapterRegistry(List.of(adapter)),
                new ParameterValidator(),
                new PersistenceService(
                        catalog,
                        new DatasetLockManager(),
                        new ExistingKeyRepository(jdbc),
                        new GenericUpsertRepository(jdbc),
                        new DataSourceTransactionManager(dataSource)),
                clock);
    }

    private static DownloadService service(
            DataSourcePlugin plugin,
            DatasetAdapter adapter,
            PersistenceService persistence,
            Clock clock) {
        return new DownloadService(
                new PluginRegistry(List.of(plugin)),
                new AdapterRegistry(List.of(adapter)),
                new ParameterValidator(),
                persistence,
                clock);
    }

    private static MockMvc mockMvc(DownloadService service) {
        return MockMvcBuilders.standaloneSetup(new DownloadController(service))
                .setValidator(beanValidator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    private static void assertInvalidEnvelope(
            DownloadEnvelope envelope,
            String message,
            CountingAdapter adapter,
            PersistenceService persistence) {
        CountingPlugin plugin = new CountingPlugin(new ReturningPlugin(
                readyDescriptor(fixtureApi()), (apiName, params) -> envelope));
        DownloadService service = service(plugin, adapter, persistence, FIXED_CLOCK);
        assertThatThrownBy(() -> service.execute(
                        PLUGIN_ID, API_NAME, Map.of("scenario", "SUCCESS"), requestId()))
                .isInstanceOfSatisfying(SourceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
                    assertThat(exception).hasMessage(message);
                });
        assertThat(plugin.downloads).isEqualTo(1);
    }

    private static ApiDescriptor fixtureApi() {
        return fixturePlugin.descriptor().apis().get(0);
    }

    private static ApiDescriptor requiredNoDefaultApi() {
        return new ApiDescriptor(
                API_NAME,
                "Required test API",
                "test",
                QueryMode.snapshot,
                List.of(new ParameterDescriptor(
                        "required_value", "Required", null, ParameterType.TEXT,
                        true, null, List.of(), null, null)));
    }

    private static DownloadEnvelope failureEnvelope() {
        return new DownloadEnvelope(
                PLUGIN_ID,
                API_NAME,
                Map.of("scenario", "SUCCESS"),
                List.of(),
                0,
                List.of(),
                DownloadStatus.FAILURE,
                "Safe source failure");
    }

    private static DownloadEnvelope identityMismatchEmptyEnvelope() {
        return new DownloadEnvelope(
                PluginId.of("other"),
                API_NAME,
                Map.of("scenario", "SUCCESS"),
                List.of("ts_code", "trade_date", "amount", "note"),
                0,
                List.of(),
                DownloadStatus.SUCCESS,
                null);
    }

    private static PluginDescriptor readyDescriptor(ApiDescriptor api) {
        return new PluginDescriptor(
                PLUGIN_ID,
                "Test Plugin",
                "Test download plugin",
                true,
                true,
                true,
                null,
                List.of(api),
                List.of(DATASET_KEY));
    }

    private static PluginDescriptor unavailableDescriptor() {
        return new PluginDescriptor(
                PLUGIN_ID,
                "Unavailable Plugin",
                "Unavailable download plugin",
                false,
                false,
                false,
                "Unavailable",
                List.of(fixtureApi()),
                List.of(DATASET_KEY));
    }

    private static RequestId requestId() {
        return new RequestId(UUID.fromString(REQUEST_ID));
    }

    private static String downloadJson(String scenario) {
        return "{\"pluginId\":\"fixture\",\"apiName\":\"fixture_daily\",\"params\":{\"scenario\":\""
                + scenario + "\"}}";
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private static long rowCount() {
        Long count = jdbc().queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class);
        return count == null ? 0 : count;
    }

    private static StoredRow row() {
        return jdbc().queryForObject(
                "SELECT ts_code, trade_date, amount, note, source_plugin, source_api, ingested_at FROM " + TABLE,
                (result, rowNumber) -> new StoredRow(
                        result.getString("ts_code"),
                        result.getDate("trade_date").toLocalDate(),
                        result.getBigDecimal("amount"),
                        result.getString("note"),
                        result.getString("source_plugin"),
                        result.getString("source_api"),
                        timestamp(result.getTimestamp(
                                "ingested_at", Calendar.getInstance(TimeZone.getTimeZone("UTC"))))));
    }

    private static Instant timestamp(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private static final class CountingPlugin implements DataSourcePlugin {
        private final DataSourcePlugin delegate;
        private int downloads;

        private CountingPlugin(DataSourcePlugin delegate) {
            this.delegate = delegate;
        }

        @Override
        public PluginDescriptor descriptor() {
            return delegate.descriptor();
        }

        @Override
        public PluginReadiness readiness() {
            return delegate.readiness();
        }

        @Override
        public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
            downloads++;
            return delegate.download(apiName, params);
        }
    }

    private static final class CountingAdapter implements DatasetAdapter {
        private final DatasetAdapter delegate;
        private int adaptations;

        private CountingAdapter(DatasetAdapter delegate) {
            this.delegate = delegate;
        }

        @Override
        public DatasetKey datasetKey() {
            return delegate.datasetKey();
        }

        @Override
        public DatasetDefinition definition() {
            return delegate.definition();
        }

        @Override
        public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt) {
            adaptations++;
            return delegate.adapt(envelope, ingestedAt);
        }
    }

    private static final class ReturningPlugin implements DataSourcePlugin {
        private final PluginDescriptor descriptor;
        private final BiFunction<ApiName, Map<String, Object>, DownloadEnvelope> downloader;

        private ReturningPlugin(
                PluginDescriptor descriptor,
                BiFunction<ApiName, Map<String, Object>, DownloadEnvelope> downloader) {
            this.descriptor = descriptor;
            this.downloader = downloader;
        }

        @Override
        public PluginDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public PluginReadiness readiness() {
            return new PluginReadiness(true, true, true, null);
        }

        @Override
        public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
            return downloader.apply(apiName, params);
        }
    }

    private record StoredRow(
            String code,
            LocalDate tradeDate,
            BigDecimal amount,
            String note,
            String sourcePlugin,
            String sourceApi,
            Instant ingestedAt) {
    }
}
