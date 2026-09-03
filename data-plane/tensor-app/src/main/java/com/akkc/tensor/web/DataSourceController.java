package com.akkc.tensor.web;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.dto.ApiDescriptorResponse;
import com.akkc.tensor.web.dto.DataSourceResponse;
import com.akkc.tensor.web.dto.DatasetDefinitionResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-sources")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DataSourceController {
    private final PluginRegistry pluginRegistry;
    private final DatasetCatalog datasetCatalog;

    public DataSourceController(PluginRegistry pluginRegistry, DatasetCatalog datasetCatalog) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry");
        this.datasetCatalog = Objects.requireNonNull(datasetCatalog, "datasetCatalog");
    }

    @GetMapping
    public List<DataSourceResponse> listDataSources() {
        return pluginRegistry.descriptors().stream().map(DataSourceResponse::from).toList();
    }

    @GetMapping("/{pluginId}/apis")
    public List<ApiDescriptorResponse> listPluginApis(@PathVariable("pluginId") String pluginId) {
        PluginId id = PluginId.of(pluginId);
        List<PluginDescriptor> matches = pluginRegistry.descriptors().stream()
                .filter(descriptor -> descriptor.pluginId().equals(id) && descriptor.downloadAvailable())
                .toList();
        if (matches.size() != 1) {
            throw new MetadataAccessException(ErrorCode.PLUGIN_DISABLED);
        }
        return matches.get(0).apis().stream().map(ApiDescriptorResponse::from).toList();
    }

    @GetMapping("/{pluginId}/datasets")
    public List<DatasetDefinitionResponse.DatasetSummary> listPluginDatasets(
            @PathVariable("pluginId") String pluginId) {
        PluginId id = PluginId.of(pluginId);
        requireRegistered(id);
        return datasetCatalog.list(id).stream().map(DataSourceController::summary).toList();
    }

    @GetMapping("/{pluginId}/datasets/{apiName}")
    public DatasetDefinitionResponse getDatasetDefinition(
            @PathVariable("pluginId") String pluginId,
            @PathVariable("apiName") String apiName) {
        PluginId id = PluginId.of(pluginId);
        requireRegistered(id);
        DatasetDefinition definition = datasetCatalog.find(DatasetKey.of(id, ApiName.of(apiName)))
                .orElseThrow(() -> new MetadataAccessException(ErrorCode.DATASET_MISCONFIGURED));
        try {
            return DatasetDefinitionResponse.from(definition);
        } catch (IllegalArgumentException exception) {
            throw new MetadataAccessException(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    private void requireRegistered(PluginId pluginId) {
        if (pluginRegistry.descriptors().stream().noneMatch(
                descriptor -> descriptor.pluginId().equals(pluginId))) {
            throw new MetadataAccessException(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    private static DatasetDefinitionResponse.DatasetSummary summary(DatasetDefinition definition) {
        try {
            return DatasetDefinitionResponse.DatasetSummary.from(definition);
        } catch (IllegalArgumentException exception) {
            throw new MetadataAccessException(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    private static final class MetadataAccessException extends TensorException {
        private MetadataAccessException(ErrorCode code) {
            super(requireMetadataCode(code),
                    code == ErrorCode.PLUGIN_DISABLED
                            ? "Plugin metadata is unavailable"
                            : "Dataset metadata is unavailable");
        }

        private static ErrorCode requireMetadataCode(ErrorCode code) {
            if (code != ErrorCode.PLUGIN_DISABLED && code != ErrorCode.DATASET_MISCONFIGURED) {
                throw new IllegalArgumentException("Unsupported metadata error code");
            }
            return code;
        }
    }
}
