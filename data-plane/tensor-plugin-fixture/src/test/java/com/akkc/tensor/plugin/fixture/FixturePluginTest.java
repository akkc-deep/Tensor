package com.akkc.tensor.plugin.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class FixturePluginTest {
    private static final PluginId PLUGIN_ID = PluginId.of("fixture");
    private static final ApiName API_NAME = ApiName.of("fixture_daily");
    private static final String YAML = """
            pluginId: fixture
            apiName: fixture_daily
            tableName: fixture__fixture_daily
            category: 验收
            displayName: Fixture 日线
            queryMode: trade_date
            parameters:
              - name: scenario
                label: 场景
                description: 确定性验收场景
                type: ENUM
                required: true
                defaultValue: SUCCESS
                allowedValues: [SUCCESS, EMPTY, SOURCE_FAILURE, TYPE_FAILURE, PERSISTENCE_FAILURE]
            columns:
              - { name: ts_code, label: ts_code, logicalType: STRING, nullable: false, displayOrder: 0, length: 64 }
              - { name: trade_date, label: trade_date, logicalType: DATE, nullable: false, displayOrder: 1 }
              - { name: amount, label: amount, logicalType: DECIMAL, nullable: false, displayOrder: 2, precision: 38, scale: 18 }
              - { name: note, label: note, logicalType: STRING, nullable: true, displayOrder: 3, length: 255 }
            businessKey: { mode: COMPOSITE, fields: [ts_code, trade_date] }
            filters: [ts_code]
            fixedColumn: ts_code
            """;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"ORIGINAL_PARAMS,REQUEST", "TRADE_DATE_RANGE,REQUEST", "TRADE_DATE_RANGE,STOCK_TIME", "ANN_DATE_RANGE,REQUEST", "ANN_DATE_RANGE,STOCK_TIME", "MONTH_RANGE,REQUEST", "NATIVE_RANGE,REQUEST"})
    void modesShareDefinitionAndExposeExecutableExactBatches(String mode, String recovery) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("acceptance");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("modes", Map.of(
                    "tensor.plugins.fixture.enabled", "true", "tensor.plugins.fixture.mode", mode,
                    "tensor.plugins.fixture.recovery", recovery)));
            context.register(FixtureConfiguration.class); context.refresh();
            var plugin = context.getBean(FixturePlugin.class);
            var adapter = context.getBean(DatasetAdapter.class);
            var api = plugin.descriptor().apis().getFirst();
            assertThat(api.sourceParameters()).isEqualTo(adapter.definition().parameters());
            assertThat(api.downloadPolicy().mode().name()).isEqualTo(mode);
            assertThat(api.downloadPolicy().recoveryPolicy().mode().name()).isEqualTo(recovery);
            var params = new java.util.HashMap<String,Object>(); params.put("scenario", "SUCCESS");
            switch (mode) {
                case "TRADE_DATE_RANGE" -> params.put("trade_date", "20260903");
                case "ANN_DATE_RANGE" -> params.put("ann_date", "20260903");
                case "MONTH_RANGE" -> params.put("month", "202609");
                case "NATIVE_RANGE" -> { params.put("start_date", "20260902"); params.put("end_date", "20260903"); }
            }
            var batch = new com.akkc.tensor.plugin.api.download.FetchBatch(params, api.downloadPolicy().recoveryPolicy());
            assertThat(plugin.planBatch(API_NAME, batch, () -> {})).isEqualTo(api.downloadPolicy().batchPlanning());
            var fetched = plugin.fetchBatch(API_NAME, batch, () -> {});
            assertThat(fetched.envelope().params()).isEqualTo(params);
            assertThat(fetched.envelope().data()).hasSize(mode.equals("NATIVE_RANGE") ? 2 : 1);
            if (!mode.equals("ORIGINAL_PARAMS")) {
                assertThat(api.parameters()).extracting(ParameterDescriptor::name).contains("start_date", "end_date");
                assertThat(fetched.envelope().data().getFirst().get(1)).isEqualTo(mode.equals("NATIVE_RANGE") ? "20260902" : mode.equals("MONTH_RANGE") ? "20260901" : "20260903");
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"ORIGINAL_PARAMS,STOCK_TIME", "MONTH_RANGE,STOCK_TIME", "NATIVE_RANGE,STOCK_TIME", "INVALID,REQUEST", "ANN_DATE_RANGE,INVALID"})
    void invalidModeRecoveryCombinationsFailAtConfiguration(String mode, String recovery) {
        assertThatThrownBy(() -> new FixtureConfiguration(mode,recovery,"")).isInstanceOf(IllegalArgumentException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ORIGINAL_PARAMS","ANN_DATE_RANGE"})
    void checksStateAfterLocalSourceFailureAsWellAsSuccess(String mode) {
        var plugin = new FixtureConfiguration(mode,"REQUEST","").fixturePlugin();
        var params = new java.util.HashMap<String,Object>(); params.put("scenario","SOURCE_FAILURE");
        if (mode.equals("ANN_DATE_RANGE")) params.put("ann_date","20260901");
        var batch = new com.akkc.tensor.plugin.api.download.FetchBatch(params,plugin.descriptor().apis().getFirst().downloadPolicy().recoveryPolicy());
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(() -> plugin.fetchBatch(API_NAME,batch,checks::incrementAndGet)).isInstanceOf(SourceException.class);
        assertThat(checks).hasValue(2);
    }

    @Test void rejectsMismatchedBatchPolicyUnknownApiAndParameters() {
        var plugin = new FixtureConfiguration("ANN_DATE_RANGE","REQUEST","").fixturePlugin();
        var api = plugin.descriptor().apis().getFirst();
        var batch = new com.akkc.tensor.plugin.api.download.FetchBatch(Map.of("scenario","SUCCESS","ann_date","20260901"),api.downloadPolicy().recoveryPolicy());
        assertThatThrownBy(() -> plugin.planBatch(ApiName.of("other_api"),batch,()->{})).isInstanceOf(IllegalArgumentException.class);
        var unknown = new com.akkc.tensor.plugin.api.download.FetchBatch(Map.of("scenario","SUCCESS","ann_date","20260901","extra","value"),batch.recoveryPolicy());
        assertThatThrownBy(() -> plugin.fetchBatch(API_NAME,unknown,()->{})).isInstanceOf(IllegalArgumentException.class);
        var wrong = new com.akkc.tensor.plugin.api.download.FetchBatch(batch.sourceParams(),
                new FixtureConfiguration("ANN_DATE_RANGE","STOCK_TIME","").fixturePlugin().descriptor().apis().getFirst().downloadPolicy().recoveryPolicy());
        assertThatThrownBy(() -> plugin.fetchBatch(API_NAME,wrong,()->{})).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void localTradeCalendarIsExplicitlyControlledAndCoversAllRequestedDates() {
        var plugin = new FixtureConfiguration("TRADE_DATE_RANGE","REQUEST","").fixturePlugin();
        var scope = new com.akkc.tensor.plugin.api.download.CalendarScope(Map.of("scenario","SUCCESS"),java.util.Set.of(java.time.LocalDate.of(2026,9,1),java.time.LocalDate.of(2026,9,2)));
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        var result = plugin.confirmCalendar(API_NAME,scope,checks::incrementAndGet);
        assertThat(result.openDates()).isEqualTo(scope.dates());
        assertThat(result.calendars()).containsOnlyKeys("fixture-controlled-all-open");
        assertThat(checks).hasValue(2);
    }

    @Test
    void checksServerAroundFixtureResponseAndRefusesUnconfirmedCalendar() {
        var factory = org.mockito.Mockito.mock(FixtureEnvelopeFactory.class);
        var plugin = new FixturePlugin(expectedDefinition(), factory);
        var params = Map.<String,Object>of("scenario", "EMPTY");
        var fault = new IllegalStateException("server unavailable");
        assertThatThrownBy(() -> plugin.download(API_NAME, params, () -> { throw fault; })).isSameAs(fault);
        org.mockito.Mockito.verifyNoInteractions(factory);
        assertThatThrownBy(() -> plugin.confirmCalendar(API_NAME,
                new com.akkc.tensor.plugin.api.download.CalendarScope(Map.of(), java.util.Set.of(java.time.LocalDate.of(2026, 9, 3))), () -> {}))
                .isInstanceOf(com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException.class);
        org.mockito.Mockito.verifyNoInteractions(factory);
        var envelope = new FixtureEnvelopeFactory().create(FixtureScenario.EMPTY, params);
        org.mockito.Mockito.when(factory.create(FixtureScenario.EMPTY, params)).thenReturn(envelope);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        var result = plugin.download(API_NAME, params, checks::incrementAndGet);
        assertThat(checks).hasValue(2);
        assertThat(result.envelope()).isSameAs(envelope);
        assertThat(result.failures()).isEmpty();
        checks.set(0);
        assertThatThrownBy(() -> plugin.download(API_NAME, params, () -> { if (checks.incrementAndGet() == 2) throw fault; })).isSameAs(fault);
        assertThat(checks).hasValue(2);
        org.mockito.Mockito.verify(factory, org.mockito.Mockito.times(2)).create(FixtureScenario.EMPTY, params);
        org.mockito.Mockito.verifyNoMoreInteractions(factory);
        var api = plugin.descriptor().apis().getFirst();
        assertThat(api.parameters()).isEqualTo(api.sourceParameters());
        assertThat(api.downloadPolicy().mode()).isEqualTo(com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode.ORIGINAL_PARAMS);
    }

    @Test
    void exposesTheNewConstructorAndRejectsInvalidDependencies() {
        assertThat(FixturePlugin.class.getModifiers()).satisfies(modifiers ->
                assertThat(java.lang.reflect.Modifier.isFinal(modifiers)).isTrue());
        assertThat(FixturePlugin.class.getConstructors()).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).containsExactly(
                        DatasetDefinition.class, FixtureEnvelopeFactory.class));
        assertThat(FixturePlugin.class.getInterfaces()).containsExactly(DataSourcePlugin.class);

        assertThatNullPointerException().isThrownBy(() -> new FixturePlugin(null, new FixtureEnvelopeFactory()));
        assertThatNullPointerException().isThrownBy(() -> new FixturePlugin(expectedDefinition(), null));
        DatasetDefinition valid = expectedDefinition();
        DatasetKey wrongKey = DatasetKey.of(PLUGIN_ID, ApiName.of("fixture_other"));
        DatasetDefinition wrong = new DatasetDefinition(
                wrongKey, valid.displayName(), valid.category(), valid.queryMode(), valid.parameters(),
                TableName.from(wrongKey), valid.columns(), valid.businessKey(), valid.filters(),
                valid.fixedColumn(), valid.batchSize());
        assertThatThrownBy(() -> new FixturePlugin(wrong, new FixtureEnvelopeFactory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("definition must be fixture_daily");
    }

    @Test
    void exposesExactDescriptorAndDownloadReadyReadiness() {
        FixturePlugin plugin = plugin();
        var descriptor = plugin.descriptor();

        assertThat(descriptor.pluginId()).isEqualTo(PLUGIN_ID);
        assertThat(descriptor.displayName()).isEqualTo("Fixture");
        assertThat(descriptor.description()).isEqualTo("Fixture 验收数据源");
        assertThat(plugin.readiness()).isSameAs(plugin.readiness())
                .isEqualTo(new PluginReadiness(true, true, true, null));
        assertThat(descriptor.enabled()).isTrue();
        assertThat(descriptor.credentialConfigured()).isTrue();
        assertThat(descriptor.downloadAvailable()).isTrue();
        assertThat(descriptor.unavailableReason()).isNull();
        assertThat(descriptor.datasets()).containsExactly(expectedDefinition().datasetKey());
        assertThat(descriptor.apis()).singleElement().satisfies(api -> {
            assertThat(api.apiName()).isEqualTo(API_NAME);
            assertThat(api.displayName()).isEqualTo("Fixture 日线");
            assertThat(api.category()).isEqualTo("验收");
            assertThat(api.queryMode()).isEqualTo(QueryMode.trade_date);
            assertThat(api.parameters()).containsExactly(expectedParameter());
        });
    }

    @Test
    void matchesExactJavaAndYamlMetadata() throws IOException {
        try (AnnotationConfigApplicationContext context = context("acceptance", "true")) {
            assertThat(context.getBean(DatasetAdapter.class).definition()).isEqualTo(expectedDefinition());
        }
        try (var input = FixturePluginTest.class.getClassLoader()
                .getResourceAsStream("datasets/fixture/fixture_daily.yaml")) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(YAML);
        }
    }

    @Test
    void registersOnlyPluginAndGenericAdapterWhenBothConditionsMatch() {
        try (AnnotationConfigApplicationContext context = context("acceptance", "true")) {
            assertThat(context.getBeansOfType(FixturePlugin.class)).hasSize(1);
            assertThat(context.getBeansOfType(DatasetAdapter.class)).hasSize(1);
            assertThat(context.getBeansOfType(FixtureEnvelopeFactory.class)).isEmpty();
            DatasetAdapter adapter = context.getBean(DatasetAdapter.class);
            assertThat(adapter).isExactlyInstanceOf(GenericDatasetAdapter.class);
            assertThat(adapter.definition()).isEqualTo(expectedDefinition());
        }
    }

    @Test
    void staysAbsentOutsideAcceptanceOrWhenDisabled() {
        try (AnnotationConfigApplicationContext context = context(null, "true")) {
            assertNoFixtureBeans(context);
        }
        try (AnnotationConfigApplicationContext context = context("production", "true")) {
            assertNoFixtureBeans(context);
        }
        try (AnnotationConfigApplicationContext context = context("acceptance", null)) {
            assertNoFixtureBeans(context);
        }
        try (AnnotationConfigApplicationContext context = context("acceptance", "false")) {
            assertNoFixtureBeans(context);
        }
    }

    @Test
    void routesExactScenariosAndRejectsInvalidDirectInputsSafely() {
        FixturePlugin plugin = plugin();
        assertThat(plugin.download(API_NAME, Map.of("scenario", "SUCCESS"), () -> {}).envelope().data())
                .containsExactly(Arrays.asList("000001.SZ", "20260807", "11.23", null));
        assertThat(plugin.download(API_NAME, Map.of("scenario", "EMPTY"), () -> {}).envelope().data()).isEmpty();
        assertThat(plugin.download(API_NAME, Map.of("scenario", "TYPE_FAILURE"), () -> {}).envelope().data())
                .containsExactly(Arrays.asList("000001.SZ", "20260807", "not-a-decimal", null));
        assertThat(plugin.download(API_NAME, Map.of("scenario", "PERSISTENCE_FAILURE"), () -> {}).envelope().data())
                .containsExactly(List.of("000001.SZ", "20260807", "11.23", "PERSISTENCE_FAILURE"));
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of("scenario", "SOURCE_FAILURE"), () -> {}).envelope())
                .isInstanceOfSatisfying(SourceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.SOURCE_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("Fixture source unavailable");
                    assertThat(exception.retryable()).isTrue();
                    assertThat(exception).hasNoCause();
                });
        assertThatThrownBy(() -> plugin.download(ApiName.of("fixture_other"), Map.of(), () -> {}).envelope())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Fixture API");
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of(), () -> {}).envelope())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Fixture scenario");
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of("scenario", 7), () -> {}).envelope())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Fixture scenario");
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of("scenario", "success"), () -> {}).envelope())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Fixture scenario");
        assertThatNullPointerException().isThrownBy(() -> plugin.download(null, Map.of(), () -> {}).envelope());
        assertThatNullPointerException().isThrownBy(() -> plugin.download(API_NAME, null, () -> {}).envelope());
    }

    private static FixturePlugin plugin() {
        return new FixturePlugin(expectedDefinition(), new FixtureEnvelopeFactory());
    }

    private static ParameterDescriptor expectedParameter() {
        return new ParameterDescriptor(
                "scenario", "场景", "确定性验收场景", ParameterType.ENUM, true, "SUCCESS",
                List.of("SUCCESS", "EMPTY", "SOURCE_FAILURE", "TYPE_FAILURE", "PERSISTENCE_FAILURE"),
                null, null);
    }

    private static DatasetDefinition expectedDefinition() {
        DatasetKey key = DatasetKey.of(PLUGIN_ID, API_NAME);
        return new DatasetDefinition(
                key, "Fixture 日线", "验收", QueryMode.trade_date, List.of(expectedParameter()), TableName.from(key),
                List.of(
                        column("ts_code", LogicalType.STRING, false, 0, 64, null, null),
                        column("trade_date", LogicalType.DATE, false, 1, null, null, null),
                        column("amount", LogicalType.DECIMAL, false, 2, null, 38, 18),
                        column("note", LogicalType.STRING, true, 3, 255, null, null)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(new FilterDefinition("ts_code")), "ts_code");
    }

    private static ColumnDefinition column(
            String name, LogicalType type, boolean nullable, int order,
            Integer length, Integer precision, Integer scale) {
        return new ColumnDefinition(
                name, name, type, nullable, order, length, precision, scale, List.of(), false);
    }

    private static AnnotationConfigApplicationContext context(String profile, String enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (profile != null) {
            context.getEnvironment().setActiveProfiles(profile);
        }
        if (enabled != null) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "fixture-test", Map.of("tensor.plugins.fixture.enabled", enabled)));
        }
        context.register(FixtureConfiguration.class);
        context.refresh();
        return context;
    }

    private static void assertNoFixtureBeans(AnnotationConfigApplicationContext context) {
        assertThat(context.getBeansOfType(FixturePlugin.class)).isEmpty();
        assertThat(context.getBeansOfType(DatasetAdapter.class)).isEmpty();
        assertThat(context.getBeansOfType(FixtureEnvelopeFactory.class)).isEmpty();
    }
}
