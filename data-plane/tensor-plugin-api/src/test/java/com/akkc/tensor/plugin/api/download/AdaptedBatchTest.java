package com.akkc.tensor.plugin.api.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdaptedBatchTest {

    @Test
    void exposesFrozenRecordComponents() {
        assertThat(componentNames(AdaptedBatch.class)).containsExactly(
                "datasetKey", "tableName", "columns", "rows", "businessKeyDefinition", "ingestedAt");
        assertThat(componentTypes(AdaptedBatch.class)).containsExactly(
                DatasetKey.class, TableName.class, List.class, List.class,
                BusinessKeyDefinition.class, Instant.class);
    }

    @Test
    void constructsDailyBatchWithOneTimestamp() {
        Instant ingestedAt = Instant.parse("2026-08-31T08:00:00Z");
        Map<String, Object> row = dailyRow("10.40");

        AdaptedBatch batch = batch(dailyColumns(), List.of(row), businessKey(), ingestedAt);

        assertThat(batch.datasetKey()).isEqualTo(datasetKey());
        assertThat(batch.tableName()).isEqualTo(TableName.from(datasetKey()));
        assertThat(batch.columns()).containsExactly("ts_code", "trade_date", "close");
        assertThat(batch.rows()).containsExactly(row);
        assertThat(batch.businessKeyDefinition()).isEqualTo(businessKey());
        assertThat(batch.ingestedAt()).isSameAs(ingestedAt);
    }

    @Test
    void allowsEmptyRowsAndNullBusinessValues() {
        AdaptedBatch empty = batch(dailyColumns(), List.of(), businessKey(), Instant.EPOCH);
        Map<String, Object> row = dailyRow(null);
        AdaptedBatch withNullValue = batch(dailyColumns(), List.of(row), businessKey(), Instant.EPOCH);

        assertThat(empty.rows()).isEmpty();
        assertThat(withNullValue.rows().get(0)).containsEntry("close", null);
    }

    @Test
    void rejectsNullComponents() {
        assertThatNullPointerException().isThrownBy(() -> new AdaptedBatch(
                null, TableName.from(datasetKey()), dailyColumns(), List.of(), businessKey(), Instant.EPOCH));
        assertThatNullPointerException().isThrownBy(() -> new AdaptedBatch(
                datasetKey(), null, dailyColumns(), List.of(), businessKey(), Instant.EPOCH));
        assertThatNullPointerException().isThrownBy(() -> new AdaptedBatch(
                datasetKey(), TableName.from(datasetKey()), null, List.of(), businessKey(), Instant.EPOCH));
        assertThatNullPointerException().isThrownBy(() -> new AdaptedBatch(
                datasetKey(), TableName.from(datasetKey()), dailyColumns(), null, businessKey(), Instant.EPOCH));
        assertThatNullPointerException().isThrownBy(() -> new AdaptedBatch(
                datasetKey(), TableName.from(datasetKey()), dailyColumns(), List.of(), null, Instant.EPOCH));
        assertThatNullPointerException().isThrownBy(() -> new AdaptedBatch(
                datasetKey(), TableName.from(datasetKey()), dailyColumns(), List.of(), businessKey(), null));
    }

    @Test
    void rejectsMismatchedTableAndInvalidColumns() {
        DatasetKey weekly = DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("weekly"));
        assertThatIllegalArgumentException().isThrownBy(() -> new AdaptedBatch(
                datasetKey(), TableName.from(weekly), dailyColumns(), List.of(), businessKey(), Instant.EPOCH));
        assertThatIllegalArgumentException().isThrownBy(
                () -> batch(List.of(), List.of(), businessKey(), Instant.EPOCH));
        assertThatIllegalArgumentException().isThrownBy(
                () -> batch(List.of("ts_code", "ts_code"), List.of(),
                        new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code")), Instant.EPOCH));
        assertThatIllegalArgumentException().isThrownBy(
                () -> batch(List.of("Bad-Column"), List.of(),
                        new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("Bad-Column")), Instant.EPOCH));
        assertThatNullPointerException().isThrownBy(
                () -> batch(Arrays.asList("ts_code", null), List.of(),
                        new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code")), Instant.EPOCH));
    }

    @Test
    void rejectsRowsWhoseKeysDoNotExactlyMatchColumns() {
        Map<String, Object> missingColumn = new LinkedHashMap<>();
        missingColumn.put("ts_code", "000001.SZ");
        missingColumn.put("trade_date", "20260831");
        Map<String, Object> extraColumn = dailyRow("10.40");
        extraColumn.put("open", "10.10");

        assertThatIllegalArgumentException().isThrownBy(
                () -> batch(dailyColumns(), List.of(missingColumn), businessKey(), Instant.EPOCH));
        assertThatIllegalArgumentException().isThrownBy(
                () -> batch(dailyColumns(), List.of(extraColumn), businessKey(), Instant.EPOCH));
        assertThatNullPointerException().isThrownBy(
                () -> batch(dailyColumns(), Arrays.asList((Map<String, Object>) null), businessKey(), Instant.EPOCH));

        Map<String, Object> nullKey = dailyRow("10.40");
        nullKey.put(null, "unexpected");
        assertThatNullPointerException().isThrownBy(
                () -> batch(dailyColumns(), List.of(nullKey), businessKey(), Instant.EPOCH));
    }

    @Test
    void rejectsBusinessKeyReferencesOutsideColumns() {
        BusinessKeyDefinition missingKey = new BusinessKeyDefinition(
                BusinessKeyMode.COMPOSITE, List.of("ts_code", "open"));

        assertThatIllegalArgumentException().isThrownBy(
                () -> batch(dailyColumns(), List.of(), missingKey, Instant.EPOCH));
    }

    @Test
    void makesBatchContainersImmutableCopiesInDeclaredOrder() {
        List<String> columns = new ArrayList<>(dailyColumns());
        Map<String, Object> row = dailyRow("10.40");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);

        AdaptedBatch batch = batch(columns, rows, businessKey(), Instant.EPOCH);
        columns.add("open");
        row.put("close", "changed");
        rows.clear();

        assertThat(batch.columns()).containsExactly("ts_code", "trade_date", "close");
        assertThat(batch.rows()).hasSize(1);
        assertThat(batch.rows().get(0).keySet()).containsExactly("ts_code", "trade_date", "close");
        assertThat(batch.rows().get(0)).containsEntry("close", "10.40");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> batch.columns().add("open"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> batch.rows().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> batch.rows().get(0).put("close", "changed"));
    }

    private static AdaptedBatch batch(
            List<String> columns,
            List<Map<String, Object>> rows,
            BusinessKeyDefinition businessKey,
            Instant ingestedAt) {
        DatasetKey key = datasetKey();
        return new AdaptedBatch(key, TableName.from(key), columns, rows, businessKey, ingestedAt);
    }

    private static DatasetKey datasetKey() {
        return DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily"));
    }

    private static List<String> dailyColumns() {
        return List.of("ts_code", "trade_date", "close");
    }

    private static BusinessKeyDefinition businessKey() {
        return new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date"));
    }

    private static Map<String, Object> dailyRow(Object close) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ts_code", "000001.SZ");
        row.put("trade_date", "20260831");
        row.put("close", close);
        return row;
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<Class<?>> componentTypes(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toList();
    }
}
