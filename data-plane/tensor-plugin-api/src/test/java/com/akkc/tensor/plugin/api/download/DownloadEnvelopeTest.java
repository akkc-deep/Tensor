package com.akkc.tensor.plugin.api.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DownloadEnvelopeTest {

    @Test
    void exposesFrozenEnumsAndRecordComponents() {
        assertThat(DownloadStatus.values()).containsExactly(DownloadStatus.SUCCESS, DownloadStatus.FAILURE);
        assertThat(DownloadOutcome.values()).containsExactly(DownloadOutcome.SUCCESS, DownloadOutcome.EMPTY);
        assertThat(componentNames(DownloadEnvelope.class)).containsExactly(
                "pluginId", "apiName", "params", "fields", "rowCount", "data", "status", "error");
        assertThat(componentTypes(DownloadEnvelope.class)).containsExactly(
                PluginId.class, ApiName.class, Map.class, List.class, int.class, List.class,
                DownloadStatus.class, String.class);
        assertThat(componentNames(DownloadResult.class)).containsExactly(
                "requestId", "outcome", "pluginId", "apiName", "sourceRowCount",
                "insertedRows", "updatedRows", "message");
        assertThat(componentTypes(DownloadResult.class)).containsExactly(
                RequestId.class, DownloadOutcome.class, PluginId.class, ApiName.class,
                long.class, long.class, long.class, String.class);
    }

    @Test
    void constructsCompleteDailySuccessEnvelope() {
        List<String> fields = dailyFields();
        List<Object> row = new ArrayList<>(Arrays.asList(
                "000001.SZ", "20260831", "10.10", "10.50", "10.00", "10.40",
                "10.05", "0.35", "3.48", "100000", null));

        DownloadEnvelope envelope = new DownloadEnvelope(
                pluginId(), apiName(), Map.of("trade_date", "20260831"), fields, 1,
                List.of(row), DownloadStatus.SUCCESS, null);

        assertThat(envelope.pluginId()).isEqualTo(pluginId());
        assertThat(envelope.apiName()).isEqualTo(apiName());
        assertThat(envelope.params()).containsExactlyEntriesOf(Map.of("trade_date", "20260831"));
        assertThat(envelope.fields()).containsExactlyElementsOf(fields);
        assertThat(envelope.data()).containsExactly(row);
        assertThat(envelope.rowCount()).isEqualTo(1);
        assertThat(envelope.status()).isEqualTo(DownloadStatus.SUCCESS);
        assertThat(envelope.error()).isNull();
    }

    @Test
    void allowsEmptySuccessWithDeclaredFields() {
        DownloadEnvelope envelope = successEnvelope(dailyFields(), 0, List.of());

        assertThat(envelope.rowCount()).isZero();
        assertThat(envelope.data()).isEmpty();
        assertThat(envelope.fields()).containsExactlyElementsOf(dailyFields());
    }

    @Test
    void rejectsInvalidEnvelopeShape() {
        assertThatIllegalArgumentException().isThrownBy(() -> successEnvelope(dailyFields(), -1, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> successEnvelope(dailyFields(), 2, List.of(dailyRow())));
        assertThatIllegalArgumentException().isThrownBy(
                () -> successEnvelope(List.of("ts_code", "ts_code"), 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> successEnvelope(List.of("Bad-Field"), 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> successEnvelope(List.of("ts_code", "trade_date"), 1, List.of(List.of("000001.SZ"))));
        assertThatNullPointerException().isThrownBy(
                () -> successEnvelope(dailyFields(), 1, Arrays.asList((List<Object>) null)));
        assertThatNullPointerException().isThrownBy(
                () -> successEnvelope(Arrays.asList("ts_code", null), 0, List.of()));
    }

    @Test
    void enforcesSuccessAndFailureConsistency() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), apiName(), Map.of(), dailyFields(), 0, List.of(), DownloadStatus.SUCCESS, "failed"));

        DownloadEnvelope failure = new DownloadEnvelope(
                pluginId(), apiName(), Map.of("trade_date", "20260831"), List.of(), 0,
                List.of(), DownloadStatus.FAILURE, "source unavailable");
        assertThat(failure.status()).isEqualTo(DownloadStatus.FAILURE);
        assertThat(failure.error()).isEqualTo("source unavailable");
        assertThat(failure.fields()).isEmpty();
        assertThat(failure.data()).isEmpty();

        assertThatNullPointerException().isThrownBy(() -> failureEnvelope(null, List.of(), 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> failureEnvelope(" ", List.of(), 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> failureEnvelope("failed", List.of("ts_code"), 0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> failureEnvelope("failed", List.of(), 1, List.of(List.of("000001.SZ"))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> failureEnvelope("failed", List.of(), 1, List.of()));
    }

    @Test
    void rejectsNullComponentsAndInvalidParams() {
        assertThatNullPointerException().isThrownBy(() -> new DownloadEnvelope(
                null, apiName(), Map.of(), dailyFields(), 0, List.of(), DownloadStatus.SUCCESS, null));
        assertThatNullPointerException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), null, Map.of(), dailyFields(), 0, List.of(), DownloadStatus.SUCCESS, null));
        assertThatNullPointerException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), apiName(), null, dailyFields(), 0, List.of(), DownloadStatus.SUCCESS, null));
        assertThatNullPointerException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), apiName(), Map.of(), null, 0, List.of(), DownloadStatus.SUCCESS, null));
        assertThatNullPointerException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), apiName(), Map.of(), dailyFields(), 0, null, DownloadStatus.SUCCESS, null));
        assertThatNullPointerException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), apiName(), Map.of(), dailyFields(), 0, List.of(), null, null));

        Map<String, Object> nullKey = new LinkedHashMap<>();
        nullKey.put(null, "20260831");
        Map<String, Object> nullValue = new LinkedHashMap<>();
        nullValue.put("trade_date", null);
        assertThatNullPointerException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), apiName(), nullKey, dailyFields(), 0, List.of(), DownloadStatus.SUCCESS, null));
        assertThatNullPointerException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), apiName(), nullValue, dailyFields(), 0, List.of(), DownloadStatus.SUCCESS, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new DownloadEnvelope(
                pluginId(), apiName(), Map.of("Trade-Date", "20260831"), dailyFields(), 0,
                List.of(), DownloadStatus.SUCCESS, null));
    }

    @Test
    void makesEnvelopeContainersImmutableCopiesWhileAllowingNullCells() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("trade_date", "20260831");
        List<String> fields = new ArrayList<>(List.of("ts_code", "amount"));
        List<Object> row = new ArrayList<>(Arrays.asList("000001.SZ", null));
        List<List<Object>> data = new ArrayList<>();
        data.add(row);

        DownloadEnvelope envelope = new DownloadEnvelope(
                pluginId(), apiName(), params, fields, 1, data, DownloadStatus.SUCCESS, null);
        params.put("ts_code", "000001.SZ");
        fields.add("trade_date");
        row.set(0, "changed");
        data.clear();

        assertThat(envelope.params()).containsOnlyKeys("trade_date");
        assertThat(envelope.fields()).containsExactly("ts_code", "amount");
        assertThat(envelope.data()).containsExactly(Arrays.asList("000001.SZ", null));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> envelope.params().put("ts_code", "000001.SZ"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> envelope.fields().add("trade_date"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> envelope.data().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> envelope.data().get(0).set(0, "changed"));
    }

    @Test
    void validatesSuccessAndEmptyDownloadResults() {
        DownloadResult success = new DownloadResult(
                requestId(), DownloadOutcome.SUCCESS, pluginId(), apiName(), 3, 1, 1, "downloaded");
        DownloadResult empty = new DownloadResult(
                requestId(), DownloadOutcome.EMPTY, pluginId(), apiName(), 0, 0, 0, "no data");

        assertThat(success.sourceRowCount()).isEqualTo(3);
        assertThat(success.insertedRows()).isEqualTo(1);
        assertThat(success.updatedRows()).isEqualTo(1);
        assertThat(empty.outcome()).isEqualTo(DownloadOutcome.EMPTY);

        assertThatIllegalArgumentException().isThrownBy(
                () -> result(DownloadOutcome.SUCCESS, 0, 0, 0, "downloaded"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> result(DownloadOutcome.EMPTY, 1, 0, 0, "no data"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> result(DownloadOutcome.EMPTY, 0, 1, 0, "no data"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> result(DownloadOutcome.EMPTY, 0, 0, 1, "no data"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> result(DownloadOutcome.SUCCESS, -1, 0, 0, "downloaded"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> result(DownloadOutcome.SUCCESS, 1, -1, 0, "downloaded"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> result(DownloadOutcome.SUCCESS, 1, 0, -1, "downloaded"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> result(DownloadOutcome.SUCCESS, 1, 0, 0, " "));
    }

    @Test
    void rejectsNullDownloadResultComponents() {
        assertThatNullPointerException().isThrownBy(() -> new DownloadResult(
                null, DownloadOutcome.EMPTY, pluginId(), apiName(), 0, 0, 0, "no data"));
        assertThatNullPointerException().isThrownBy(() -> new DownloadResult(
                requestId(), null, pluginId(), apiName(), 0, 0, 0, "no data"));
        assertThatNullPointerException().isThrownBy(() -> new DownloadResult(
                requestId(), DownloadOutcome.EMPTY, null, apiName(), 0, 0, 0, "no data"));
        assertThatNullPointerException().isThrownBy(() -> new DownloadResult(
                requestId(), DownloadOutcome.EMPTY, pluginId(), null, 0, 0, 0, "no data"));
        assertThatNullPointerException().isThrownBy(() -> new DownloadResult(
                requestId(), DownloadOutcome.EMPTY, pluginId(), apiName(), 0, 0, 0, null));
    }

    private static DownloadEnvelope successEnvelope(List<String> fields, int rowCount, List<List<Object>> data) {
        return new DownloadEnvelope(
                pluginId(), apiName(), Map.of(), fields, rowCount, data, DownloadStatus.SUCCESS, null);
    }

    private static DownloadEnvelope failureEnvelope(
            String error, List<String> fields, int rowCount, List<List<Object>> data) {
        return new DownloadEnvelope(
                pluginId(), apiName(), Map.of(), fields, rowCount, data, DownloadStatus.FAILURE, error);
    }

    private static DownloadResult result(
            DownloadOutcome outcome, long sourceRows, long insertedRows, long updatedRows, String message) {
        return new DownloadResult(
                requestId(), outcome, pluginId(), apiName(), sourceRows, insertedRows, updatedRows, message);
    }

    private static List<String> dailyFields() {
        return List.of(
                "ts_code", "trade_date", "open", "high", "low", "close", "pre_close",
                "change", "pct_chg", "vol", "amount");
    }

    private static List<Object> dailyRow() {
        return List.of(
                "000001.SZ", "20260831", "10.10", "10.50", "10.00", "10.40",
                "10.05", "0.35", "3.48", "100000", "1000000");
    }

    private static PluginId pluginId() {
        return PluginId.of("tushare_pro");
    }

    private static ApiName apiName() {
        return ApiName.of("daily");
    }

    private static RequestId requestId() {
        return new RequestId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<Class<?>> componentTypes(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toList();
    }
}
