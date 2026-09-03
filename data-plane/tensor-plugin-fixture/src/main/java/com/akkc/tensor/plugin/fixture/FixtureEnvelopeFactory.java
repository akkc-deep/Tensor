package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FixtureEnvelopeFactory {
    private static final PluginId PLUGIN_ID = PluginId.of("fixture");
    private static final ApiName API_NAME = ApiName.of("fixture_daily");
    private static final List<String> FIELDS = List.of("ts_code", "trade_date", "amount", "note");

    public FixtureEnvelopeFactory() {}

    public DownloadEnvelope create(FixtureScenario scenario, Map<String, Object> params) {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(params, "params");
        return switch (scenario) {
            case SUCCESS -> new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, params, FIELDS, 1,
                    List.of(Arrays.asList("000001.SZ", "20260807", "11.23", null)), DownloadStatus.SUCCESS, null);
            case EMPTY -> new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, params, FIELDS, 0, List.of(), DownloadStatus.SUCCESS, null);
            case SOURCE_FAILURE -> throw new SourceException(
                    ErrorCode.SOURCE_UNAVAILABLE, "Fixture source unavailable");
            case TYPE_FAILURE -> new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, params, FIELDS, 1,
                    List.of(Arrays.asList("000001.SZ", "20260807", "not-a-decimal", null)), DownloadStatus.SUCCESS, null);
            case PERSISTENCE_FAILURE -> new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, params, FIELDS, 1,
                    List.of(Arrays.asList("000001.SZ", "20260807", "11.23", "PERSISTENCE_FAILURE")),
                    DownloadStatus.SUCCESS, null);
        };
    }
}
