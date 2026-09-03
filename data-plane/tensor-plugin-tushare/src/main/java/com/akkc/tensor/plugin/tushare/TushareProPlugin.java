package com.akkc.tensor.plugin.tushare;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TushareProPlugin implements DataSourcePlugin {
    private final TushareProperties properties;
    private final TushareProClient client;
    private final PluginDescriptor descriptor;
    private final Map<ApiName, DatasetDefinition> definitionsByApi;

    public TushareProPlugin(
            TushareProperties properties,
            TushareProClient client,
            List<DatasetDefinition> definitions) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.client = Objects.requireNonNull(client, "client");
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
        if (definitions.size() != 49) {
            throw new IllegalArgumentException("definitions must contain exactly 49 datasets");
        }
        definitionsByApi = definitions.stream().collect(Collectors.toUnmodifiableMap(
                definition -> definition.datasetKey().apiName(), Function.identity()));
        PluginReadiness readiness = properties.readiness();
        descriptor = new PluginDescriptor(
                PluginId.of("tushare_pro"),
                "Tushare Pro",
                "Tushare Pro 证券数据源",
                readiness.enabled(),
                readiness.credentialConfigured(),
                readiness.downloadAvailable(),
                readiness.unavailableReason(),
                definitions.stream().map(definition -> new ApiDescriptor(
                        definition.datasetKey().apiName(),
                        definition.displayName(),
                        definition.category(),
                        definition.queryMode(),
                        definition.parameters())).toList(),
                definitions.stream().map(DatasetDefinition::datasetKey).toList());
    }

    @Override
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public PluginReadiness readiness() {
        return properties.readiness();
    }

    @Override
    public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        if (!readiness().downloadAvailable()) {
            throw new PluginUnavailableException();
        }
        DatasetDefinition definition = definitionsByApi.get(apiName);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Tushare API");
        }
        return client.execute(definition, params);
    }

    private static final class PluginUnavailableException extends TensorException {
        private PluginUnavailableException() {
            super(ErrorCode.PLUGIN_DISABLED, "Tushare Pro download is unavailable");
        }
    }
}
