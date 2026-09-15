package com.akkc.tensor.core.registry;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PluginRegistry {
    private static final System.Logger LOGGER = System.getLogger(PluginRegistry.class.getName());

    private final Map<PluginId, DataSourcePlugin> plugins;
    private final List<PluginDescriptor> descriptors;

    public PluginRegistry(List<DataSourcePlugin> plugins) {
        Objects.requireNonNull(plugins, "plugins");
        Map<PluginId, List<Candidate>> candidatesById = new LinkedHashMap<>();

        for (DataSourcePlugin plugin : plugins) {
            Candidate candidate = candidate(plugin);
            if (candidate != null) {
                candidatesById.computeIfAbsent(candidate.descriptor().pluginId(), ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }

        Map<PluginId, DataSourcePlugin> registered = new LinkedHashMap<>();
        List<PluginDescriptor> snapshot = new ArrayList<>();
        for (List<Candidate> candidates : candidatesById.values()) {
            if (candidates.size() == 1) {
                Candidate candidate = candidates.get(0);
                snapshot.add(candidate.descriptor());
                if (candidate.descriptor().downloadAvailable()) {
                    registered.put(candidate.descriptor().pluginId(), candidate.plugin());
                }
            } else {
                LOGGER.log(System.Logger.Level.WARNING, "Duplicate plugin id disabled");
                for (Candidate candidate : candidates) {
                    snapshot.add(withReadiness(candidate.descriptor(), candidate.descriptor().enabled(),
                            candidate.descriptor().credentialConfigured(), false, "duplicate plugin id"));
                }
            }
        }
        snapshot.sort(Comparator.comparing((PluginDescriptor descriptor) -> descriptor.pluginId().value())
                .thenComparing(PluginDescriptor::displayName));
        this.plugins = Map.copyOf(registered);
        this.descriptors = List.copyOf(snapshot);
    }

    public Optional<DataSourcePlugin> find(PluginId pluginId) {
        return Optional.ofNullable(plugins.get(Objects.requireNonNull(pluginId, "pluginId")));
    }

    public List<PluginDescriptor> descriptors() {
        return descriptors;
    }

    private static Candidate candidate(DataSourcePlugin plugin) {
        if (plugin == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Skipping plugin with invalid descriptor");
            return null;
        }
        PluginDescriptor descriptor;
        try {
            descriptor = Objects.requireNonNull(plugin.descriptor(), "descriptor");
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Skipping plugin with invalid descriptor");
            return null;
        }
        try {
            PluginReadiness readiness = plugin.readiness();
            return new Candidate(plugin, withReadiness(descriptor, readiness.enabled(), readiness.credentialConfigured(),
                    readiness.downloadAvailable(), readiness.unavailableReason()));
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Plugin readiness unavailable");
            return new Candidate(plugin, withReadiness(descriptor, false, false, false, "plugin readiness unavailable"));
        }
    }

    private static PluginDescriptor withReadiness(
            PluginDescriptor descriptor,
            boolean enabled,
            boolean credentialConfigured,
            boolean downloadAvailable,
            String unavailableReason) {
        return new PluginDescriptor(descriptor.pluginId(), descriptor.displayName(), descriptor.description(), enabled,
                credentialConfigured, downloadAvailable, unavailableReason, descriptor.apis(), descriptor.datasets());
    }

    private record Candidate(DataSourcePlugin plugin, PluginDescriptor descriptor) {
    }
}
