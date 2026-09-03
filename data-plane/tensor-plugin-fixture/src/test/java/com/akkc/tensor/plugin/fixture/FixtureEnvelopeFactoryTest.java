package com.akkc.tensor.plugin.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FixtureEnvelopeFactoryTest {
    private static final PluginId PLUGIN_ID = PluginId.of("fixture");
    private static final ApiName API_NAME = ApiName.of("fixture_daily");
    private static final Instant INGESTED_AT = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void exposesTheFrozenScenarioAndFactoryContracts() {
        assertThat(FixtureScenario.values()).containsExactly(
                FixtureScenario.SUCCESS,
                FixtureScenario.EMPTY,
                FixtureScenario.SOURCE_FAILURE,
                FixtureScenario.TYPE_FAILURE,
                FixtureScenario.PERSISTENCE_FAILURE);
        assertThat(Modifier.isFinal(FixtureEnvelopeFactory.class.getModifiers())).isTrue();
        assertThat(FixtureEnvelopeFactory.class.getDeclaredFields()).allSatisfy(field ->
                assertThat(Modifier.isStatic(field.getModifiers())).isTrue());
        assertThat(FixtureEnvelopeFactory.class.getConstructors()).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).isEmpty());
        assertThat(FixtureEnvelopeFactory.class.getDeclaredMethods()).singleElement().satisfies(method -> {
            assertThat(method.getName()).isEqualTo("create");
            assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
            assertThat(method.getReturnType()).isEqualTo(DownloadEnvelope.class);
            assertThat(method.getParameterTypes()).containsExactly(FixtureScenario.class, Map.class);
        });

        FixtureEnvelopeFactory factory = new FixtureEnvelopeFactory();
        assertThatNullPointerException().isThrownBy(() -> factory.create(null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> factory.create(FixtureScenario.SUCCESS, null));
    }

    @Test
    void createsTheStableSuccessEnvelopeAndRealAdaptedRow() {
        Map<String, Object> params = new LinkedHashMap<>(Map.of("scenario", "SUCCESS"));
        DownloadEnvelope envelope = factory().create(FixtureScenario.SUCCESS, params);
        params.put("scenario", "EMPTY");

        assertSuccessfulEnvelope(envelope, "SUCCESS", List.of(
                Arrays.asList("000001.SZ", "20260807", "11.23", null)));
        assertThat(adapter().adapt(envelope, INGESTED_AT).rows()).containsExactly(linkedRow(
                "ts_code", "000001.SZ",
                "trade_date", LocalDate.of(2026, 8, 7),
                "amount", new BigDecimal("11.230000000000000000"),
                "note", null));
    }

    @Test
    void createsAValidEmptyEnvelopeAndRealEmptyBatch() {
        Map<String, Object> params = new LinkedHashMap<>(Map.of("scenario", "EMPTY"));
        DownloadEnvelope envelope = factory().create(FixtureScenario.EMPTY, params);
        params.put("scenario", "SUCCESS");

        assertSuccessfulEnvelope(envelope, "EMPTY", List.of());
        assertThat(adapter().adapt(envelope, INGESTED_AT).rows()).isEmpty();
    }

    @Test
    void failsTheSourceWithoutConstructingAnEnvelope() {
        assertThatThrownBy(() -> factory().create(FixtureScenario.SOURCE_FAILURE, Map.of("scenario", "SOURCE_FAILURE")))
                .isInstanceOfSatisfying(SourceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.SOURCE_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("Fixture source unavailable");
                    assertThat(exception.retryable()).isTrue();
                    assertThat(exception).hasNoCause();
                });
    }

    @Test
    void letsTheRealAdapterRejectTheInvalidAmountWithoutPartialBatch() {
        Map<String, Object> params = new LinkedHashMap<>(Map.of("scenario", "TYPE_FAILURE"));
        DownloadEnvelope envelope = factory().create(FixtureScenario.TYPE_FAILURE, params);
        params.put("scenario", "SUCCESS");

        assertSuccessfulEnvelope(envelope, "TYPE_FAILURE", List.of(
                Arrays.asList("000001.SZ", "20260807", "not-a-decimal", null)));
        assertThatThrownBy(() -> adapter().adapt(envelope, INGESTED_AT))
                .isInstanceOfSatisfying(AdapterException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
                    assertThat(exception.getMessage())
                            .isEqualTo("Invalid adapter value: api=fixture_daily, row=0, field=amount");
                    assertThat(exception).hasNoCause();
                });
    }

    @Test
    void createsAnAdaptablePersistenceFailureMarker() {
        Map<String, Object> params = new LinkedHashMap<>(Map.of("scenario", "PERSISTENCE_FAILURE"));
        DownloadEnvelope envelope = factory().create(FixtureScenario.PERSISTENCE_FAILURE, params);
        params.put("scenario", "SUCCESS");

        assertSuccessfulEnvelope(envelope, "PERSISTENCE_FAILURE", List.of(
                List.of("000001.SZ", "20260807", "11.23", "PERSISTENCE_FAILURE")));
        assertThat(adapter().adapt(envelope, INGESTED_AT).rows()).containsExactly(linkedRow(
                "ts_code", "000001.SZ",
                "trade_date", LocalDate.of(2026, 8, 7),
                "amount", new BigDecimal("11.230000000000000000"),
                "note", "PERSISTENCE_FAILURE"));
    }

    private static FixtureEnvelopeFactory factory() {
        return new FixtureEnvelopeFactory();
    }

    private static void assertSuccessfulEnvelope(
            DownloadEnvelope envelope, String scenario, List<List<Object>> expectedData) {
        assertThat(envelope.pluginId()).isEqualTo(PLUGIN_ID);
        assertThat(envelope.apiName()).isEqualTo(API_NAME);
        assertThat(envelope.params()).containsExactly(Map.entry("scenario", scenario));
        assertThat(envelope.fields()).containsExactly("ts_code", "trade_date", "amount", "note");
        assertThat(envelope.rowCount()).isEqualTo(expectedData.size());
        assertThat(envelope.data()).containsExactlyElementsOf(expectedData);
        assertThat(envelope.status()).isEqualTo(DownloadStatus.SUCCESS);
        assertThat(envelope.error()).isNull();
    }

    private static GenericDatasetAdapter adapter() {
        return new GenericDatasetAdapter(definition(), new ValueConverter(), new FingerprintKeyCodec());
    }

    private static DatasetDefinition definition() {
        DatasetKey key = DatasetKey.of(PLUGIN_ID, API_NAME);
        return new DatasetDefinition(
                key,
                "Fixture 日线",
                "验收",
                QueryMode.trade_date,
                List.of(),
                TableName.from(key),
                List.of(
                        column("ts_code", LogicalType.STRING, false, 0, 64, null, null),
                        column("trade_date", LogicalType.DATE, false, 1, null, null, null),
                        column("amount", LogicalType.DECIMAL, false, 2, null, 38, 18),
                        column("note", LogicalType.STRING, true, 3, 255, null, null)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(),
                null);
    }

    private static ColumnDefinition column(
            String name, LogicalType type, boolean nullable, int order,
            Integer length, Integer precision, Integer scale) {
        return new ColumnDefinition(
                name, name, type, nullable, order, length, precision, scale, List.of(), false);
    }

    private static Map<String, Object> linkedRow(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }
}
