package com.akkc.tensor.plugin.api.model;

import java.util.Objects;

public record DatasetKey(PluginId pluginId, ApiName apiName) {
    public DatasetKey {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
    }

    public static DatasetKey of(PluginId pluginId, ApiName apiName) {
        return new DatasetKey(pluginId, apiName);
    }
}
