package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FixturePlugin implements DataSourcePlugin {
    private static final DatasetKey DATASET_KEY =
            DatasetKey.of(PluginId.of("fixture"), ApiName.of("fixture_daily"));
    private static final String UNAVAILABLE = "Fixture scenarios are not configured";

    private final PluginReadiness readiness;
    private final PluginDescriptor descriptor;

    public FixturePlugin(DatasetDefinition definition) {
        definition = Objects.requireNonNull(definition, "definition");
        if (!DATASET_KEY.equals(definition.datasetKey())) {
            throw new IllegalArgumentException("definition must be fixture_daily");
        }
        readiness = new PluginReadiness(true, true, false, UNAVAILABLE);
        ApiDescriptor api = new ApiDescriptor(
                definition.datasetKey().apiName(),
                definition.displayName(),
                definition.category(),
                definition.queryMode(),
                definition.parameters());
        descriptor = new PluginDescriptor(
                DATASET_KEY.pluginId(),
                "Fixture",
                "Fixture 验收数据源",
                readiness.enabled(),
                readiness.credentialConfigured(),
                readiness.downloadAvailable(),
                readiness.unavailableReason(),
                List.of(api),
                List.of(DATASET_KEY));
    }

    @Override
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public PluginReadiness readiness() {
        return readiness;
    }

    @Override
    public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        if (!DATASET_KEY.apiName().equals(apiName)) {
            throw new IllegalArgumentException("Unknown Fixture API");
        }
        throw new SourceException(ErrorCode.SOURCE_UNAVAILABLE, UNAVAILABLE);
    }
}
