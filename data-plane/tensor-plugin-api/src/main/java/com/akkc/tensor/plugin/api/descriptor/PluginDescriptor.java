package com.akkc.tensor.plugin.api.descriptor;

import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.List;
import java.util.Objects;

public record PluginDescriptor(
        PluginId pluginId,
        String displayName,
        String description,
        boolean enabled,
        boolean credentialConfigured,
        boolean downloadAvailable,
        String unavailableReason,
        List<ApiDescriptor> apis,
        List<DatasetKey> datasets
) {
    public PluginDescriptor {
        Objects.requireNonNull(pluginId, "pluginId");
        requireNonBlank(displayName, "displayName");
        requireNonBlank(description, "description");
        new PluginReadiness(enabled, credentialConfigured, downloadAvailable, unavailableReason);
        apis = List.copyOf(Objects.requireNonNull(apis, "apis"));
        datasets = List.copyOf(Objects.requireNonNull(datasets, "datasets"));
        if (apis.stream().map(ApiDescriptor::apiName).distinct().count() != apis.size()) {
            throw new IllegalArgumentException("apis must not contain duplicate api names");
        }
        if (datasets.stream().distinct().count() != datasets.size()) {
            throw new IllegalArgumentException("datasets must not contain duplicates");
        }
        for (DatasetKey dataset : datasets) {
            if (!dataset.pluginId().equals(pluginId)) {
                throw new IllegalArgumentException("dataset plugin id must match pluginId");
            }
            if (apis.stream().map(ApiDescriptor::apiName).noneMatch(dataset.apiName()::equals)) {
                throw new IllegalArgumentException("dataset api name must be declared by apis");
            }
        }
    }

    private static void requireNonBlank(String value, String component) {
        Objects.requireNonNull(value, component);
        if (value.isBlank()) {
            throw new IllegalArgumentException(component + " must not be blank");
        }
    }
}
