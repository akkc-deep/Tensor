package com.akkc.tensor.web;

import com.akkc.tensor.core.metadata.MetadataQueryService;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.dto.ApiDescriptorResponse;
import com.akkc.tensor.web.dto.DataSourceResponse;
import com.akkc.tensor.web.dto.DatasetDefinitionResponse;
import com.akkc.tensor.web.dto.DatasetPath;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-sources")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DataSourceController {
    private final MetadataQueryService metadataQueryService;

    public DataSourceController(MetadataQueryService metadataQueryService) {
        this.metadataQueryService = Objects.requireNonNull(metadataQueryService, "metadataQueryService");
    }

    @GetMapping
    public List<DataSourceResponse> listDataSources() {
        return metadataQueryService.listDataSources().stream().map(DataSourceResponse::from).toList();
    }

    @GetMapping("/{pluginId}/apis")
    public List<ApiDescriptorResponse> listPluginApis(@PathVariable("pluginId") String pluginId) {
        return metadataQueryService.listApis(PluginId.of(pluginId)).stream()
                .map(ApiDescriptorResponse::from).toList();
    }

    @GetMapping("/{pluginId}/datasets")
    public List<DatasetDefinitionResponse.DatasetSummary> listPluginDatasets(
            @PathVariable("pluginId") String pluginId) {
        return metadataQueryService.listDatasets(PluginId.of(pluginId)).stream()
                .map(DatasetDefinitionResponse.DatasetSummary::from).toList();
    }

    @GetMapping("/{pluginId}/datasets/{apiName}")
    public DatasetDefinitionResponse getDatasetDefinition(DatasetPath path) {
        DatasetKey key = DatasetKey.of(PluginId.of(path.pluginId()), ApiName.of(path.apiName()));
        return DatasetDefinitionResponse.from(metadataQueryService.getDataset(key));
    }
}
