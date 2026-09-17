package com.akkc.tensor.core.registry;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.IntegrityCheckSupport;
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
    private static final String DUPLICATE_PLUGIN_REASON = "duplicate plugin id";
    private static final String READINESS_UNAVAILABLE_REASON = "plugin readiness unavailable";

    private final Map<PluginId, DataSourcePlugin> plugins;
    private final Map<PluginId, IntegrityAvailability> integrity;
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
        Map<PluginId, IntegrityAvailability> local = new LinkedHashMap<>();
        List<PluginDescriptor> snapshot = new ArrayList<>();
        for (List<Candidate> candidates : candidatesById.values()) {
            if (candidates.size() == 1) {
                Candidate candidate = candidates.get(0);
                snapshot.add(candidate.descriptor());
                local.put(candidate.descriptor().pluginId(), integrityAvailability(candidate));
                if (candidate.descriptor().downloadAvailable()) {
                    registered.put(candidate.descriptor().pluginId(), candidate.plugin());
                }
            } else {
                LOGGER.log(System.Logger.Level.WARNING, "Duplicate plugin id disabled");
                local.put(candidates.getFirst().descriptor().pluginId(),
                        new IntegrityAvailability(null, DUPLICATE_PLUGIN_REASON));
                for (Candidate candidate : candidates) {
                    snapshot.add(withReadiness(candidate.descriptor(), candidate.descriptor().enabled(),
                            candidate.descriptor().credentialConfigured(), false, DUPLICATE_PLUGIN_REASON));
                }
            }
        }
        snapshot.sort(Comparator.comparing((PluginDescriptor descriptor) -> descriptor.pluginId().value())
                .thenComparing(PluginDescriptor::displayName));
        this.plugins = Map.copyOf(registered);
        this.integrity = Map.copyOf(local);
        this.descriptors = List.copyOf(snapshot);
    }

    public Optional<DataSourcePlugin> find(PluginId pluginId) {
        return Optional.ofNullable(plugins.get(Objects.requireNonNull(pluginId, "pluginId")));
    }

    public List<PluginDescriptor> descriptors() {
        return descriptors;
    }

    public IntegrityAvailability findIntegrity(PluginId pluginId) {
        return integrity.getOrDefault(Objects.requireNonNull(pluginId, "pluginId"),
                new IntegrityAvailability(null, "plugin not found"));
    }

    public record IntegrityAvailability(IntegrityCheckSupport support, String unavailableReason) {
        public IntegrityAvailability {
            if (support == null ? unavailableReason == null || unavailableReason.isBlank() : unavailableReason != null) {
                throw new IllegalArgumentException("Integrity availability requires support or an unavailable reason");
            }
        }

        public boolean available() { return support != null; }
    }

    private static IntegrityAvailability integrityAvailability(Candidate candidate) {
        if (candidate.readinessFailed()) return new IntegrityAvailability(null, READINESS_UNAVAILABLE_REASON);
        if (!candidate.descriptor().enabled()) return new IntegrityAvailability(null, "plugin disabled");
        return candidate.plugin() instanceof IntegrityCheckSupport support
                ? new IntegrityAvailability(support, null)
                : new IntegrityAvailability(null, "integrity check unsupported");
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
                    readiness.downloadAvailable(), readiness.unavailableReason()), false);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Plugin readiness unavailable");
            return new Candidate(plugin, withReadiness(
                    descriptor, false, false, false, READINESS_UNAVAILABLE_REASON), true);
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

    private record Candidate(DataSourcePlugin plugin, PluginDescriptor descriptor, boolean readinessFailed) {
    }
}
