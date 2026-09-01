package com.akkc.tensor.core.catalog;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DatasetCatalog {
    private final Map<DatasetKey, DatasetDefinition> definitions;

    DatasetCatalog(List<DatasetDefinition> definitions) {
        LinkedHashMap<DatasetKey, DatasetDefinition> byKey = new LinkedHashMap<>();
        for (DatasetDefinition definition : Objects.requireNonNull(definitions, "definitions")) {
            DatasetDefinition previous = byKey.put(definition.datasetKey(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("definitions must have unique dataset keys");
            }
        }
        this.definitions = Collections.unmodifiableMap(byKey);
    }

    public Optional<DatasetDefinition> find(DatasetKey datasetKey) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(datasetKey, "datasetKey")));
    }

    public List<DatasetDefinition> list(PluginId pluginId) {
        Objects.requireNonNull(pluginId, "pluginId");
        return definitions.values().stream()
                .filter(definition -> definition.datasetKey().pluginId().equals(pluginId))
                .sorted(java.util.Comparator.comparing(definition -> definition.datasetKey().apiName().value()))
                .toList();
    }
}
