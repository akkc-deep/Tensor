package com.akkc.tensor.core.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenericDatasetAdapterTest {
    private static final PluginId PLUGIN_ID = new PluginId("fixture");
    private static final ApiName API_NAME = new ApiName("daily");
    private static final DatasetKey DATASET_KEY = new DatasetKey(PLUGIN_ID, API_NAME);
    private static final Instant INGESTED_AT = Instant.parse("2026-09-02T01:02:03Z");

    @Test
    void exposesOnlyTheSpecifiedAdapterAndCodecContracts() throws Exception {
        assertThat(Modifier.isFinal(GenericDatasetAdapter.class.getModifiers())).isTrue();
        assertThat(GenericDatasetAdapter.class.getConstructors()).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).containsExactly(
                        DatasetDefinition.class, ValueConverter.class, FingerprintKeyCodec.class));
        assertThat(GenericDatasetAdapter.class.getInterfaces()).containsExactly(DatasetAdapter.class);
        Method datasetKey = GenericDatasetAdapter.class.getDeclaredMethod("datasetKey");
        Method definition = GenericDatasetAdapter.class.getDeclaredMethod("definition");
        Method adapt = GenericDatasetAdapter.class.getDeclaredMethod("adapt", DownloadEnvelope.class, Instant.class);
        assertThat(GenericDatasetAdapter.class.getDeclaredMethods())
                .filteredOn(method -> Modifier.isPublic(method.getModifiers()))
                .containsExactlyInAnyOrder(datasetKey, definition, adapt);

        assertThat(Modifier.isFinal(FingerprintKeyCodec.class.getModifiers())).isTrue();
        assertThat(FingerprintKeyCodec.class.getConstructors()).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).isEmpty());
        Method sha256 = FingerprintKeyCodec.class.getDeclaredMethod("sha256", List.class, Map.class);
        assertThat(FingerprintKeyCodec.class.getDeclaredMethods())
                .filteredOn(method -> Modifier.isPublic(method.getModifiers()))
                .containsExactly(sha256);

        DatasetDefinition definitionValue = compositeDefinition();
        GenericDatasetAdapter adapter = adapter(definitionValue);
        assertThat(adapter.datasetKey()).isSameAs(definitionValue.datasetKey());
        assertThat(adapter.definition()).isSameAs(definitionValue);
        assertThatNullPointerException().isThrownBy(() -> new GenericDatasetAdapter(null, new ValueConverter(), new FingerprintKeyCodec()));
        assertThatNullPointerException().isThrownBy(() -> new GenericDatasetAdapter(definitionValue, null, new FingerprintKeyCodec()));
        assertThatNullPointerException().isThrownBy(() -> new GenericDatasetAdapter(definitionValue, new ValueConverter(), null));
    }

    @Test
    void adaptsCompositeRowsInDefinitionOrderWithoutMutatingInput() {
        DatasetDefinition definitionValue = compositeDefinition();
        List<Object> sourceRow = row(" 000001.SZ ", "20240229", "7", "1.2", null);
        DownloadEnvelope envelope = envelope(definitionValue, definitionValue.columns().stream().map(ColumnDefinition::name).toList(), List.of(sourceRow));

        AdaptedBatch batch = adapter(definitionValue).adapt(envelope, INGESTED_AT);

        assertThat(batch.datasetKey()).isEqualTo(DATASET_KEY);
        assertThat(batch.tableName()).isEqualTo(TableName.from(DATASET_KEY));
        assertThat(batch.columns()).containsExactly("code", "trade_date", "volume", "amount", "note");
        assertThat(batch.businessKeyDefinition()).isSameAs(definitionValue.businessKey());
        assertThat(batch.ingestedAt()).isSameAs(INGESTED_AT);
        assertThat(batch.rows()).containsExactly(linkedRow(
                "code", "000001.SZ", "trade_date", LocalDate.of(2024, 2, 29), "volume", 7L,
                "amount", new BigDecimal("1.20"), "note", null));
        assertThat(envelope.data()).containsExactly(sourceRow);
        assertThat(sourceRow).containsExactly(" 000001.SZ ", "20240229", "7", "1.2", null);
    }

    @Test
    void returnsValidEmptyBatchForSuccessfulEmptyEnvelope() {
        DatasetDefinition definitionValue = compositeDefinition();
        AdaptedBatch batch = adapter(definitionValue).adapt(envelope(definitionValue, List.of("code", "trade_date", "volume", "amount", "note"), List.of()), INGESTED_AT);

        assertThat(batch.columns()).containsExactly("code", "trade_date", "volume", "amount", "note");
        assertThat(batch.rows()).isEmpty();
        assertThat(batch.ingestedAt()).isSameAs(INGESTED_AT);
        assertThat(batch.tableName()).isSameAs(definitionValue.tableName());
        assertThat(batch.businessKeyDefinition()).isSameAs(definitionValue.businessKey());
    }

    @Test
    void rejectsProgrammingErrorsFailuresAndEnvelopeIdentityMismatches() {
        DatasetDefinition definitionValue = compositeDefinition();
        GenericDatasetAdapter adapter = adapter(definitionValue);
        assertThatNullPointerException().isThrownBy(() -> adapter.adapt(null, INGESTED_AT));
        assertThatNullPointerException().isThrownBy(() -> adapter.adapt(envelope(definitionValue, List.of("code", "trade_date", "volume", "amount", "note"), List.of()), null));
        DownloadEnvelope failure = new DownloadEnvelope(PLUGIN_ID, API_NAME, Map.of(), List.of(), 0, List.of(), DownloadStatus.FAILURE, "safe failure");
        assertThatThrownBy(() -> adapter.adapt(failure, INGESTED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("envelope must be successful");
        DownloadEnvelope mismatchedPlugin = new DownloadEnvelope(new PluginId("other"), API_NAME, Map.of(), List.of("code", "trade_date", "volume", "amount", "note"), 0, List.of(), DownloadStatus.SUCCESS, null);
        DownloadEnvelope mismatchedApi = new DownloadEnvelope(PLUGIN_ID, new ApiName("weekly"), Map.of(), List.of("code", "trade_date", "volume", "amount", "note"), 0, List.of(), DownloadStatus.SUCCESS, null);
        assertMissing(() -> adapter.adapt(mismatchedPlugin, INGESTED_AT), "Adapter envelope mismatch: api=daily");
        assertMissing(() -> adapter.adapt(mismatchedApi, INGESTED_AT), "Adapter envelope mismatch: api=daily");
    }

    @Test
    void rejectsMissingExtraAndReorderedSourceFieldsWithoutLeakingThem() {
        DatasetDefinition definitionValue = compositeDefinition();
        GenericDatasetAdapter adapter = adapter(definitionValue);
        for (List<String> fields : List.of(
                List.of("code", "trade_date", "volume", "amount"),
                List.of("code", "trade_date", "volume", "amount", "secret"),
                List.of("trade_date", "code", "volume", "amount", "note"))) {
            DownloadEnvelope envelope = envelope(definitionValue, fields, List.of());
            assertMissing(() -> adapter.adapt(envelope, INGESTED_AT), "Adapter fields do not match: api=daily");
        }
    }

    @Test
    void validatesRequiredAndBusinessKeyValuesAfterConversionAndPreservesAllowedNulls() {
        DatasetDefinition definitionValue = compositeDefinition();
        GenericDatasetAdapter adapter = adapter(definitionValue);
        assertMissing(
                () -> adapter.adapt(envelope(definitionValue, names(definitionValue), List.of(row(null, "20240229", "7", "1.2", null))), INGESTED_AT),
                "Missing adapter value: api=daily, row=0, field=code");
        DatasetDefinition nullableKeyDefinition = definition("nullable_key", BusinessKeyMode.COMPOSITE,
                List.of(column("id", LogicalType.STRING, true, 8, null, null), column("memo", LogicalType.TEXT, true, null, null, null)), List.of("id"));
        assertMissing(
                () -> adapter(nullableKeyDefinition).adapt(envelope(nullableKeyDefinition, names(nullableKeyDefinition), List.of(List.of(" ", "kept"))), INGESTED_AT),
                "Missing adapter value: api=nullable_key, row=0, field=id");
        AdaptedBatch valid = adapter.adapt(envelope(definitionValue, names(definitionValue), List.of(List.of("code", "20240229", "7", "1.2", "  "))), INGESTED_AT);
        assertThat(valid.rows().getFirst().get("note")).isEqualTo("  ");
    }

    @Test
    void propagatesValueConversionFailuresWithoutProducingPartialBatch() {
        DatasetDefinition definitionValue = compositeDefinition();
        GenericDatasetAdapter adapter = adapter(definitionValue);
        assertInvalid(
                () -> adapter.adapt(envelope(definitionValue, names(definitionValue), List.of(row("code", "20240230", "7", "1.2", null))), INGESTED_AT),
                "Invalid adapter value: api=daily, row=0, field=trade_date");
        assertInvalid(
                () -> adapter.adapt(envelope(definitionValue, names(definitionValue), List.of(row("code", "20240229", "7", "1.234", null))), INGESTED_AT),
                "Invalid adapter value: api=daily, row=0, field=amount");
    }

    @Test
    void encodesApprovedFingerprintValuesAndRejectsBrokenCodecContracts() {
        FingerprintKeyCodec codec = new FingerprintKeyCodec();
        List<String> fields = List.of("text", "missing", "count", "amount");
        Map<String, Object> row = linkedRow("text", "中", "missing", null, "count", 42L, "amount", new BigDecimal("1.20"), "extra", "ignored");
        assertThat(codec.sha256(fields, row)).isEqualTo("c593b786a7708a9b7a106e244094f1cabd200caa3e95fad3b041225c17ac19ad");
        assertThat(codec.sha256(List.of("amount", "count", "missing", "text"), row))
                .isNotEqualTo("c593b786a7708a9b7a106e244094f1cabd200caa3e95fad3b041225c17ac19ad");
        assertThat(codec.sha256(List.of("date"), Map.of("date", LocalDate.of(2024, 2, 29))))
                .isEqualTo("6a7047a518a2a1817930f7ae541ccf725bb62eb3fd99d0001bd895be15b9636a");
        assertThatNullPointerException().isThrownBy(() -> codec.sha256(null, row));
        assertThatNullPointerException().isThrownBy(() -> codec.sha256(fields, null));
        assertThatThrownBy(() -> codec.sha256(List.of(), row)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.sha256(List.of("text", "text"), row)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.sha256(List.of("absent"), row)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.sha256(List.of("bad"), Map.of("bad", Boolean.TRUE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unsupported fingerprint value type");
    }

    @Test
    void appendsStableFingerprintBusinessKeyAfterBusinessColumns() {
        DatasetDefinition definitionValue = fingerprintDefinition();
        AdaptedBatch batch = adapter(definitionValue).adapt(
                envelope(definitionValue, names(definitionValue), List.of(List.of("a", "20240229", "memo"))), INGESTED_AT);

        assertThat(batch.columns()).containsExactly("id", "trade_date", "memo", "business_key");
        assertThat(batch.rows().getFirst()).containsEntry("business_key", "1b1a87323e7089d0327b269f94e037161f2a79c2ab170276a18864c25ebd8e83");
        assertThat(batch.businessKeyDefinition()).isSameAs(definitionValue.businessKey());
        assertThat(adapter(definitionValue).adapt(envelope(definitionValue, names(definitionValue), List.of(List.of("a", "20240229", "memo"))), INGESTED_AT)
                .rows().getFirst().get("business_key")).isEqualTo(batch.rows().getFirst().get("business_key"));
    }

    @Test
    void retainsOnlyTheFirstIdenticalCompositeAndFingerprintRows() {
        DatasetDefinition composite = compositeDefinition();
        AdaptedBatch compositeBatch = adapter(composite).adapt(envelope(composite, names(composite), List.of(
                row("first", "20240229", "7", "1.2", null), row("first", "20240229", "7", "1.20", null), row("second", "20240229", "7", "1.20", null))), INGESTED_AT);
        assertThat(compositeBatch.rows()).hasSize(2).extracting(row -> row.get("code")).containsExactly("first", "second");

        DatasetDefinition fingerprint = fingerprintDefinition();
        AdaptedBatch fingerprintBatch = adapter(fingerprint).adapt(envelope(fingerprint, names(fingerprint), List.of(
                List.of("first", "20240229", "memo"), List.of("first", "20240229", "memo"), List.of("second", "20240229", "memo"))), INGESTED_AT);
        assertThat(fingerprintBatch.rows()).hasSize(2).extracting(row -> row.get("id")).containsExactly("first", "second");
    }

    @Test
    void rejectsConflictingCompositeAndFingerprintKeysWithoutLeakingValues() {
        DatasetDefinition composite = compositeDefinition();
        assertInvalid(
                () -> adapter(composite).adapt(envelope(composite, names(composite), List.of(
                        List.of("same", "20240229", "7", "1.20", "one"), List.of("same", "20240229", "8", "1.20", "two"))), INGESTED_AT),
                "Conflicting adapter key: api=daily, row=1");
        DatasetDefinition fingerprint = fingerprintDefinition();
        assertInvalid(
                () -> adapter(fingerprint).adapt(envelope(fingerprint, names(fingerprint), List.of(
                        List.of("same", "20240229", "one"), List.of("same", "20240229", "two"))), INGESTED_AT),
                "Conflicting adapter key: api=fingerprint, row=1");
    }

    private static GenericDatasetAdapter adapter(DatasetDefinition definitionValue) {
        return new GenericDatasetAdapter(definitionValue, new ValueConverter(), new FingerprintKeyCodec());
    }

    private static DatasetDefinition compositeDefinition() {
        return definition("daily", BusinessKeyMode.COMPOSITE, List.of(
                column("code", LogicalType.STRING, false, 16, null, null),
                column("trade_date", LogicalType.DATE, false, null, null, null),
                column("volume", LogicalType.LONG, true, null, null, null),
                column("amount", LogicalType.DECIMAL, true, null, 8, 2),
                column("note", LogicalType.TEXT, true, null, null, null)), List.of("code", "trade_date"));
    }

    private static DatasetDefinition fingerprintDefinition() {
        return definition("fingerprint", BusinessKeyMode.FINGERPRINT, List.of(
                column("id", LogicalType.STRING, false, 16, null, null),
                column("trade_date", LogicalType.DATE, false, null, null, null),
                column("memo", LogicalType.TEXT, true, null, null, null)), List.of("id", "trade_date"));
    }

    private static DatasetDefinition definition(String api, BusinessKeyMode mode, List<ColumnDefinition> columns, List<String> keyFields) {
        DatasetKey key = new DatasetKey(PLUGIN_ID, new ApiName(api));
        return new DatasetDefinition(key, api, "test", QueryMode.snapshot, List.of(), TableName.from(key), columns,
                new BusinessKeyDefinition(mode, keyFields), List.of(), null, 1);
    }

    private static ColumnDefinition column(String name, LogicalType type, boolean nullable, Integer length, Integer precision, Integer scale) {
        return new ColumnDefinition(name, name, type, nullable, 0, length, precision, scale, List.of(), false);
    }

    private static List<String> names(DatasetDefinition definitionValue) {
        return definitionValue.columns().stream().map(ColumnDefinition::name).toList();
    }

    private static DownloadEnvelope envelope(DatasetDefinition definitionValue, List<String> fields, List<List<Object>> data) {
        return new DownloadEnvelope(definitionValue.datasetKey().pluginId(), definitionValue.datasetKey().apiName(), Map.of(), fields,
                data.size(), data, DownloadStatus.SUCCESS, null);
    }

    private static Map<String, Object> linkedRow(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private static void assertMissing(ThrowingCall call, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AdapterException.class, exception -> {
            assertThat(exception.code()).isEqualTo(ErrorCode.ADAPTER_FIELD_MISSING);
            assertThat(exception).hasMessage(message).hasNoCause();
        });
    }

    private static void assertInvalid(ThrowingCall call, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AdapterException.class, exception -> {
            assertThat(exception.code()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
            assertThat(exception).hasMessage(message).hasNoCause();
        });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
