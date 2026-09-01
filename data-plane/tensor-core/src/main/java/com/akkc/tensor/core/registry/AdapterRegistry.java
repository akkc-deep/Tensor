package com.akkc.tensor.core.registry;

import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AdapterRegistry {
    private static final System.Logger LOGGER = System.getLogger(AdapterRegistry.class.getName());

    private final Map<DatasetKey, DatasetAdapter> adapters;

    public AdapterRegistry(List<DatasetAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters");
        Map<DatasetKey, List<DatasetAdapter>> candidatesByKey = new LinkedHashMap<>();
        for (DatasetAdapter adapter : adapters) {
            if (adapter == null) {
                LOGGER.log(System.Logger.Level.WARNING, "Skipping adapter with invalid dataset key");
                continue;
            }
            try {
                DatasetKey datasetKey = Objects.requireNonNull(adapter.datasetKey(), "datasetKey");
                candidatesByKey.computeIfAbsent(datasetKey, ignored -> new ArrayList<>()).add(adapter);
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Skipping adapter with invalid dataset key");
            }
        }

        Map<DatasetKey, DatasetAdapter> registered = new LinkedHashMap<>();
        for (Map.Entry<DatasetKey, List<DatasetAdapter>> entry : candidatesByKey.entrySet()) {
            if (entry.getValue().size() == 1) {
                registered.put(entry.getKey(), entry.getValue().get(0));
            } else {
                LOGGER.log(System.Logger.Level.WARNING, "Duplicate dataset key disabled");
            }
        }
        this.adapters = Map.copyOf(registered);
    }

    public Optional<DatasetAdapter> find(DatasetKey datasetKey) {
        return Optional.ofNullable(adapters.get(Objects.requireNonNull(datasetKey, "datasetKey")));
    }
}
