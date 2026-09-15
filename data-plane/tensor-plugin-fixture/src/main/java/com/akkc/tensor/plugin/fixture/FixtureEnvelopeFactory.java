package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.plugin.api.constant.DatasetFields;
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
    private static final PluginId PLUGIN_ID = PluginId.of(FixtureConstants.PLUGIN_ID);
    private static final ApiName API_NAME = ApiName.of(FixtureConstants.API_NAME);
    private static final List<String> FIELDS = List.of(DatasetFields.TS_CODE, DatasetFields.TRADE_DATE, FixtureConstants.AMOUNT, FixtureConstants.NOTE);

    public FixtureEnvelopeFactory() {}

    public DownloadEnvelope create(FixtureScenario scenario, Map<String, Object> params) {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(params, "params");
        return switch (scenario) {
            case SUCCESS -> new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, params, FIELDS, 1,
                    List.of(Arrays.asList(FixtureConstants.SAMPLE_TS_CODE, FixtureConstants.SAMPLE_TRADE_DATE, FixtureConstants.SAMPLE_AMOUNT, null)), DownloadStatus.SUCCESS, null);
            case EMPTY -> new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, params, FIELDS, 0, List.of(), DownloadStatus.SUCCESS, null);
            case SOURCE_FAILURE -> throw new SourceException(
                    ErrorCode.SOURCE_UNAVAILABLE, "Fixture source unavailable");
            case TYPE_FAILURE -> new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, params, FIELDS, 1,
                    List.of(Arrays.asList(FixtureConstants.SAMPLE_TS_CODE, FixtureConstants.SAMPLE_TRADE_DATE, "not-a-decimal", null)), DownloadStatus.SUCCESS, null);
            case PERSISTENCE_FAILURE -> new DownloadEnvelope(
                    PLUGIN_ID, API_NAME, params, FIELDS, 1,
                    List.of(Arrays.asList(FixtureConstants.SAMPLE_TS_CODE, FixtureConstants.SAMPLE_TRADE_DATE, FixtureConstants.SAMPLE_AMOUNT, FixtureScenario.PERSISTENCE_FAILURE.name())),
                    DownloadStatus.SUCCESS, null);
        };
    }
}
