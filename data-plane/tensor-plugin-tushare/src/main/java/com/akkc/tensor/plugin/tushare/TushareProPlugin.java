package com.akkc.tensor.plugin.tushare;

import com.akkc.tensor.plugin.api.BatchDownloadSupport;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.batch.BatchAssessment;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.tushare.batch.TushareBatchPolicies;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TushareProPlugin implements BatchDownloadSupport {
    private final TushareProperties properties;
    private final TushareProClient client;
    private final PluginDescriptor descriptor;
    private final TushareBatchPolicies batches;
    private final Map<ApiName, DatasetDefinition> definitionsByApi;

    public TushareProPlugin(
            TushareProperties properties,
            TushareProClient client,
            List<DatasetDefinition> definitions) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.client = Objects.requireNonNull(client, "client");
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
        if (definitions.size() != 40) {
            throw new IllegalArgumentException("definitions must contain exactly 40 datasets");
        }
        definitionsByApi = definitions.stream().collect(Collectors.toUnmodifiableMap(
                definition -> definition.datasetKey().apiName(), Function.identity()));
        batches = new TushareBatchPolicies(client, definitions);
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
        return client.execute(downloadDefinition(apiName, params), params);
    }

    @Override
    public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName apiName) {
        return batches.batchDescriptor(apiName);
    }

    @Override
    public List<DateRange> plan(ApiName apiName, Map<String, Object> params, BatchCallContext context) {
        return batches.plan(apiName, params, context);
    }

    @Override
    public Map<String, Object> sourceParameters(ApiName apiName, Map<String, Object> params, DateRange range) {
        return batches.sourceParameters(apiName, params, range);
    }

    @Override
    public DownloadEnvelope downloadBatch(ApiName apiName, Map<String, Object> sourceParams, BatchCallContext context) {
        return client.execute(downloadDefinition(apiName, sourceParams), sourceParams, context);
    }

    @Override
    public BatchAssessment assess(ApiName apiName, DateRange range, DownloadEnvelope envelope) {
        return batches.assess(apiName, range, envelope);
    }

    private DatasetDefinition downloadDefinition(ApiName apiName, Map<String, Object> params) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        if (!readiness().downloadAvailable()) {
            throw new PluginUnavailableException();
        }
        DatasetDefinition definition = definitionsByApi.get(apiName);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Tushare API");
        }
        return definition;
    }

    private static final class PluginUnavailableException extends TensorException {
        private PluginUnavailableException() {
            super(ErrorCode.PLUGIN_DISABLED, "Tushare Pro download is unavailable");
        }
    }
}
