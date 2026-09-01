package com.akkc.tensor.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistryTest {
    @Test
    void findsUniqueDownloadablePluginUsingCurrentReadinessSnapshot() {
        // Catches a registry that returns a plugin without applying readiness to its descriptor snapshot.
        PluginId pluginId = PluginId.of("alpha");
        TestPlugin plugin = new TestPlugin(descriptor(pluginId, "Alpha", true,
                "original unavailable"), ready());

        PluginRegistry registry = new PluginRegistry(List.of(plugin));

        assertThat(registry.find(pluginId)).containsSame(plugin);
        assertThat(registry.descriptors()).singleElement().satisfies(value -> {
            assertThat(value.enabled()).isTrue();
            assertThat(value.credentialConfigured()).isTrue();
            assertThat(value.downloadAvailable()).isTrue();
            assertThat(value.unavailableReason()).isNull();
        });
    }

    @Test
    void sortsDescriptorsAndDefensivelyCopiesInputAndOutput() {
        // Catches a registry that exposes insertion order or mutable input/output collections.
        List<DataSourcePlugin> plugins = new ArrayList<>(List.of(
                new TestPlugin(descriptor(PluginId.of("bravo"), "Bravo", false, "disabled"), disabled()),
                new TestPlugin(descriptor(PluginId.of("alpha"), "Zulu", false, "disabled"), disabled()),
                new TestPlugin(descriptor(PluginId.of("alpha"), "Able", false, "disabled"), disabled())));

        PluginRegistry registry = new PluginRegistry(plugins);
        plugins.clear();

        assertThat(registry.descriptors()).extracting(PluginDescriptor::displayName)
                .containsExactly("Able", "Zulu", "Bravo");
        assertThatThrownBy(() -> registry.descriptors().add(
                descriptor(PluginId.of("charlie"), "Charlie", false, "disabled")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void retainsDisabledPluginDescriptorButDoesNotFindPlugin() {
        // Catches a registry that treats disabled plugins as downloadable or drops their descriptor.
        PluginId pluginId = PluginId.of("disabled");
        PluginRegistry registry = new PluginRegistry(List.of(
                new TestPlugin(descriptor(pluginId, "Disabled", false, "plugin disabled"), disabled())));

        assertThat(registry.find(pluginId)).isEmpty();
        assertThat(registry.descriptors()).singleElement().satisfies(value -> {
            assertThat(value.downloadAvailable()).isFalse();
            assertThat(value.unavailableReason()).isEqualTo("plugin disabled");
        });
    }

    @Test
    void retainsMissingCredentialDescriptorButDoesNotFindPlugin() {
        // Catches a registry that erases a credential-missing plugin instead of exposing its safe status.
        PluginId pluginId = PluginId.of("credentials");
        PluginRegistry registry = new PluginRegistry(List.of(
                new TestPlugin(descriptor(pluginId, "Credentials", false, "missing credential"), missingCredential())));

        assertThat(registry.find(pluginId)).isEmpty();
        assertThat(registry.descriptors()).singleElement().satisfies(value -> {
            assertThat(value.credentialConfigured()).isFalse();
            assertThat(value.downloadAvailable()).isFalse();
            assertThat(value.unavailableReason()).isEqualTo("missing credential");
        });
    }

    @Test
    void excludesEveryDuplicatePluginIdAndMarksEachDescriptorUnavailable() {
        // Catches first-wins/last-wins duplicate handling or a missing duplicate safety reason.
        PluginId pluginId = PluginId.of("duplicate");
        PluginRegistry registry = new PluginRegistry(List.of(
                new TestPlugin(descriptor(pluginId, "First", true, "old"), ready()),
                new TestPlugin(descriptor(pluginId, "Second", true, "old"), ready())));

        assertThat(registry.find(pluginId)).isEmpty();
        assertThat(registry.descriptors()).extracting(PluginDescriptor::unavailableReason)
                .containsExactly("duplicate plugin id", "duplicate plugin id");
        assertThat(registry.descriptors()).allSatisfy(value -> {
            assertThat(value.enabled()).isTrue();
            assertThat(value.credentialConfigured()).isTrue();
            assertThat(value.downloadAvailable()).isFalse();
        });
    }

    @Test
    void isolatesInvalidPluginDescriptorAndReadinessAlongsideValidPlugin() {
        // Catches boundary exceptions that abort construction or make a readiness failure look downloadable.
        PluginId validId = PluginId.of("valid");
        PluginId brokenId = PluginId.of("broken");
        DataSourcePlugin invalidDescriptor = new DataSourcePlugin() {
            @Override
            public PluginDescriptor descriptor() {
                throw new IllegalStateException("credential=top-secret");
            }

            @Override
            public PluginReadiness readiness() {
                throw new AssertionError("readiness must not be reached");
            }

            @Override
            public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
                throw new AssertionError("download must not be called");
            }
        };
        DataSourcePlugin invalidReadiness = new TestPlugin(descriptor(brokenId, "Broken", true, "old"), null) {
            @Override
            public PluginReadiness readiness() {
                throw new IllegalStateException("token=top-secret");
            }
        };

        PluginRegistry registry = new PluginRegistry(List.of(
                invalidDescriptor,
                invalidReadiness,
                new TestPlugin(descriptor(validId, "Valid", true, "old"), ready())));

        assertThat(registry.find(validId)).isPresent();
        assertThat(registry.find(brokenId)).isEmpty();
        assertThat(registry.descriptors()).extracting(PluginDescriptor::pluginId)
                .containsExactly(brokenId, validId);
        assertThat(registry.descriptors().get(0).unavailableReason())
                .isEqualTo("plugin readiness unavailable");
    }

    @Test
    void findsUniqueAdapterAndDefensivelyCopiesInput() {
        // Catches a registry that does not retain a unique adapter or retains a mutable input list reference.
        DatasetKey key = datasetKey("adapter");
        TestAdapter adapter = new TestAdapter(key);
        List<DatasetAdapter> adapters = new ArrayList<>(List.of(adapter));

        AdapterRegistry registry = new AdapterRegistry(adapters);
        adapters.clear();

        assertThat(registry.find(key)).containsSame(adapter);
    }

    @Test
    void excludesEveryDuplicateDatasetKey() {
        // Catches first-wins/last-wins handling of adapters sharing a dataset key.
        DatasetKey key = datasetKey("duplicate_adapter");
        AdapterRegistry registry = new AdapterRegistry(List.of(new TestAdapter(key), new TestAdapter(key)));

        assertThat(registry.find(key)).isEmpty();
    }

    @Test
    void isolatesInvalidAdapterDatasetKeyAlongsideValidAdapter() {
        // Catches a datasetKey boundary exception that prevents a valid sibling adapter from registering.
        DatasetKey validKey = datasetKey("valid_adapter");
        DatasetAdapter invalidAdapter = new DatasetAdapter() {
            @Override
            public DatasetKey datasetKey() {
                throw new IllegalStateException("database password=top-secret");
            }

            @Override
            public DatasetDefinition definition() {
                throw new AssertionError("definition must not be called");
            }

            @Override
            public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt) {
                throw new AssertionError("adapt must not be called");
            }
        };

        AdapterRegistry registry = new AdapterRegistry(List.of(invalidAdapter, new TestAdapter(validKey)));

        assertThat(registry.find(validKey)).isPresent();
    }

    @Test
    void rejectsNullInputsAndSkipsNullExtensionsWithoutCallingWorkMethods() {
        // Catches missing null boundaries or registry code that invokes download, definition, or adapt.
        PluginId pluginId = PluginId.of("null_safe");
        DatasetKey key = datasetKey("null_adapter");
        TestPlugin plugin = new TestPlugin(descriptor(pluginId, "Null safe", true, "old"), ready());
        TestAdapter adapter = new TestAdapter(key);

        assertThatThrownBy(() -> new PluginRegistry(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdapterRegistry(null)).isInstanceOf(NullPointerException.class);
        PluginRegistry pluginRegistry = new PluginRegistry(java.util.Arrays.asList(null, plugin));
        AdapterRegistry adapterRegistry = new AdapterRegistry(java.util.Arrays.asList(null, adapter));

        assertThatThrownBy(() -> pluginRegistry.find(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapterRegistry.find(null)).isInstanceOf(NullPointerException.class);
        assertThat(pluginRegistry.find(pluginId)).containsSame(plugin);
        assertThat(adapterRegistry.find(key)).containsSame(adapter);
    }

    private static PluginDescriptor descriptor(PluginId pluginId, String displayName, boolean available, String reason) {
        ApiName apiName = ApiName.of("snapshot");
        DatasetKey datasetKey = DatasetKey.of(pluginId, apiName);
        return new PluginDescriptor(pluginId, displayName, "Test plugin", available, available, available,
                available ? null : reason,
                List.of(new ApiDescriptor(apiName, "Snapshot", "test", QueryMode.snapshot, List.of())),
                List.of(datasetKey));
    }

    private static PluginReadiness ready() {
        return new PluginReadiness(true, true, true, null);
    }

    private static PluginReadiness disabled() {
        return new PluginReadiness(false, true, false, "plugin disabled");
    }

    private static PluginReadiness missingCredential() {
        return new PluginReadiness(true, false, false, "missing credential");
    }

    private static DatasetKey datasetKey(String pluginId) {
        return DatasetKey.of(PluginId.of(pluginId), ApiName.of("snapshot"));
    }

    private static class TestPlugin implements DataSourcePlugin {
        private final PluginDescriptor descriptor;
        private final PluginReadiness readiness;

        private TestPlugin(PluginDescriptor descriptor, PluginReadiness readiness) {
            this.descriptor = descriptor;
            this.readiness = readiness;
        }

        @Override
        public PluginDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public PluginReadiness readiness() {
            return readiness;
        }

        @Override
        public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
            throw new AssertionError("download must not be called");
        }
    }

    private static final class TestAdapter implements DatasetAdapter {
        private final DatasetKey datasetKey;

        private TestAdapter(DatasetKey datasetKey) {
            this.datasetKey = datasetKey;
        }

        @Override
        public DatasetKey datasetKey() {
            return datasetKey;
        }

        @Override
        public DatasetDefinition definition() {
            throw new AssertionError("definition must not be called");
        }

        @Override
        public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt) {
            throw new AssertionError("adapt must not be called");
        }
    }
}
