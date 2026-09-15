package com.akkc.tensor.core.metadata;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.query.QueryCapabilities;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.List;
import java.util.Objects;

public final class MetadataQueryService {
    private final PluginRegistry pluginRegistry;
    private final DatasetCatalog datasetCatalog;

    public MetadataQueryService(PluginRegistry pluginRegistry, DatasetCatalog datasetCatalog) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry");
        this.datasetCatalog = Objects.requireNonNull(datasetCatalog, "datasetCatalog");
    }

    public List<PluginDescriptor> listDataSources() {
        return pluginRegistry.descriptors();
    }

    public List<ApiDescriptor> listApis(PluginId pluginId) {
        Objects.requireNonNull(pluginId, "pluginId");
        List<PluginDescriptor> matches = pluginRegistry.descriptors().stream()
                .filter(descriptor -> descriptor.pluginId().equals(pluginId))
                .filter(PluginDescriptor::downloadAvailable)
                .toList();
        if (matches.size() != 1) {
            throw access(ErrorCode.PLUGIN_DISABLED);
        }
        return matches.getFirst().apis();
    }

    public List<DatasetDefinition> listDatasets(PluginId pluginId) {
        requireKnown(pluginId);
        List<DatasetDefinition> definitions = datasetCatalog.list(pluginId);
        definitions.forEach(MetadataQueryService::requireSupported);
        return definitions;
    }

    public DatasetDefinition getDataset(DatasetKey key) {
        Objects.requireNonNull(key, "key");
        requireKnown(key.pluginId());
        DatasetDefinition definition = datasetCatalog.find(key)
                .orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        requireSupported(definition);
        return definition;
    }

    private void requireKnown(PluginId pluginId) {
        Objects.requireNonNull(pluginId, "pluginId");
        if (pluginRegistry.descriptors().stream().noneMatch(
                descriptor -> descriptor.pluginId().equals(pluginId))) {
            throw access(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    private static void requireSupported(DatasetDefinition definition) {
        if (!QueryCapabilities.supports(definition)) {
            throw access(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    private static MetadataAccessException access(ErrorCode code) {
        return new MetadataAccessException(
                code,
                code == ErrorCode.PLUGIN_DISABLED
                        ? "Plugin metadata is unavailable"
                        : "Dataset metadata is unavailable");
    }

    private static final class MetadataAccessException extends TensorException {
        private MetadataAccessException(ErrorCode code, String message) {
            super(code, message);
        }
    }
}
