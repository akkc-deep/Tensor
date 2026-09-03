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
        assertThat(plugin.download(API_NAME, Map.of("scenario", "SUCCESS")).data())
                .containsExactly(Arrays.asList("000001.SZ", "20260807", "11.23", null));
        assertThat(plugin.download(API_NAME, Map.of("scenario", "EMPTY")).data()).isEmpty();
        assertThat(plugin.download(API_NAME, Map.of("scenario", "TYPE_FAILURE")).data())
                .containsExactly(Arrays.asList("000001.SZ", "20260807", "not-a-decimal", null));
        assertThat(plugin.download(API_NAME, Map.of("scenario", "PERSISTENCE_FAILURE")).data())
                .containsExactly(List.of("000001.SZ", "20260807", "11.23", "PERSISTENCE_FAILURE"));
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of("scenario", "SOURCE_FAILURE")))
                .isInstanceOfSatisfying(SourceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.SOURCE_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("Fixture source unavailable");
                    assertThat(exception.retryable()).isTrue();
                    assertThat(exception).hasNoCause();
                });
        assertThatThrownBy(() -> plugin.download(ApiName.of("fixture_other"), Map.of("scenario", "SUCCESS")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Fixture API");
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Fixture scenario");
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of("scenario", 7)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Fixture scenario");
        assertThatThrownBy(() -> plugin.download(API_NAME, Map.of("scenario", "success")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Fixture scenario");
        assertThatNullPointerException().isThrownBy(() -> plugin.download(null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> plugin.download(API_NAME, null));
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
