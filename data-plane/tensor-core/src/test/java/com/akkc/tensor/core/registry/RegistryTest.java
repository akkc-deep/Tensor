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
        assertPluginCalls(plugin);
    }

    @Test
    void sortsDescriptorsAndDefensivelyCopiesInputAndOutput() {
        // Catches a registry that exposes insertion order or mutable input/output collections.
        TestPlugin bravo = new TestPlugin(descriptor(PluginId.of("bravo"), "Bravo", false, "disabled"), disabled());
        TestPlugin zulu = new TestPlugin(descriptor(PluginId.of("alpha"), "Zulu", false, "disabled"), disabled());
        TestPlugin able = new TestPlugin(descriptor(PluginId.of("alpha"), "Able", false, "disabled"), disabled());
        List<DataSourcePlugin> plugins = new ArrayList<>(List.of(bravo, zulu, able));

        PluginRegistry registry = new PluginRegistry(plugins);
        plugins.clear();

        assertThat(registry.descriptors()).extracting(PluginDescriptor::displayName)
                .containsExactly("Able", "Zulu", "Bravo");
        assertThatThrownBy(() -> registry.descriptors().add(
                descriptor(PluginId.of("charlie"), "Charlie", false, "disabled")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertPluginCalls(bravo, zulu, able);
    }

    @Test
    void retainsDisabledPluginDescriptorButDoesNotFindPlugin() {
        // Catches a registry that treats disabled plugins as downloadable or drops their descriptor.
        PluginId pluginId = PluginId.of("disabled");
        TestPlugin plugin = new TestPlugin(descriptor(pluginId, "Disabled", false, "plugin disabled"), disabled());
        PluginRegistry registry = new PluginRegistry(List.of(plugin));

        assertThat(registry.find(pluginId)).isEmpty();
        assertThat(registry.descriptors()).singleElement().satisfies(value -> {
            assertThat(value.downloadAvailable()).isFalse();
            assertThat(value.unavailableReason()).isEqualTo("plugin disabled");
        });
        assertPluginCalls(plugin);
    }

    @Test
    void retainsMissingCredentialDescriptorButDoesNotFindPlugin() {
        // Catches a registry that erases a credential-missing plugin instead of exposing its safe status.
        PluginId pluginId = PluginId.of("credentials");
        TestPlugin plugin = new TestPlugin(
                descriptor(pluginId, "Credentials", false, "missing credential"), missingCredential());
        PluginRegistry registry = new PluginRegistry(List.of(plugin));

        assertThat(registry.find(pluginId)).isEmpty();
        assertThat(registry.descriptors()).singleElement().satisfies(value -> {
            assertThat(value.credentialConfigured()).isFalse();
            assertThat(value.downloadAvailable()).isFalse();
            assertThat(value.unavailableReason()).isEqualTo("missing credential");
        });
        assertPluginCalls(plugin);
    }

    @Test
    void excludesEveryDuplicatePluginIdAndMarksEachDescriptorUnavailable() {
        // Catches first-wins/last-wins handling, mutation of preserved metadata, unstable equal-key sorting,
        // or duplicates that hide a unique valid sibling.
        PluginId pluginId = PluginId.of("duplicate");
        TestPlugin first = new TestPlugin(descriptor(pluginId, "Same", "First metadata", ApiName.of("first_api"),
                true, "old"), ready());
        TestPlugin second = new TestPlugin(descriptor(pluginId, "Same", "Second metadata", ApiName.of("second_api"),
                true, "old"), ready());
        PluginId siblingId = PluginId.of("sibling");
        TestPlugin sibling = new TestPlugin(descriptor(siblingId, "Sibling", true, "old"), ready());
        PluginRegistry registry = new PluginRegistry(List.of(
                first,
                second,
                sibling));

        assertThat(registry.find(pluginId)).isEmpty();
        assertThat(registry.find(siblingId)).containsSame(sibling);
        assertDuplicateDescriptor(registry.descriptors().get(0), first.descriptor);
        assertDuplicateDescriptor(registry.descriptors().get(1), second.descriptor);
        assertThat(registry.descriptors()).extracting(PluginDescriptor::description)
                .containsExactly("First metadata", "Second metadata", "Test plugin");
        assertPluginCalls(first, second, sibling);
    }

    @Test
    void isolatesInvalidPluginDescriptorAndReadinessAlongsideValidPlugin() {
        // Catches boundary exceptions that abort construction or make a readiness failure look downloadable.
        PluginId validId = PluginId.of("valid");
        PluginId brokenId = PluginId.of("broken");
        TestPlugin invalidDescriptor = TestPlugin.withDescriptorFailure(
                new IllegalStateException("credential=top-secret"));
        TestPlugin invalidReadiness = TestPlugin.withReadinessFailure(
                descriptor(brokenId, "Broken", true, "old"), new IllegalStateException("token=top-secret"));
        TestPlugin validPlugin = new TestPlugin(descriptor(validId, "Valid", true, "old"), ready());

        PluginRegistry registry = new PluginRegistry(List.of(
                invalidDescriptor,
                invalidReadiness,
                validPlugin));

        assertThat(registry.find(validId)).isPresent();
        assertThat(registry.find(brokenId)).isEmpty();
        assertThat(registry.descriptors()).extracting(PluginDescriptor::pluginId)
                .containsExactly(brokenId, validId);
        assertThat(registry.descriptors().get(0).unavailableReason())
                .isEqualTo("plugin readiness unavailable");
        assertPluginCalls(invalidDescriptor, invalidReadiness, validPlugin);
        assertThat(invalidDescriptor.readinessCalls).isZero();
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
        assertThat(adapter.datasetKeyCalls).isEqualTo(1);
    }

    @Test
    void excludesEveryDuplicateDatasetKey() {
        // Catches first-wins/last-wins duplicate handling or duplicates that hide a unique valid sibling.
        DatasetKey key = datasetKey("duplicate_adapter");
        TestAdapter first = new TestAdapter(key);
        TestAdapter second = new TestAdapter(key);
        DatasetKey siblingKey = datasetKey("adapter_sibling");
        TestAdapter sibling = new TestAdapter(siblingKey);
        AdapterRegistry registry = new AdapterRegistry(List.of(first, second, sibling));

        assertThat(registry.find(key)).isEmpty();
        assertThat(registry.find(siblingKey)).containsSame(sibling);
        assertThat(first.datasetKeyCalls).isEqualTo(1);
        assertThat(second.datasetKeyCalls).isEqualTo(1);
        assertThat(sibling.datasetKeyCalls).isEqualTo(1);
    }

    @Test
    void isolatesInvalidAdapterDatasetKeyAlongsideValidAdapter() {
        // Catches a datasetKey boundary exception that prevents a valid sibling adapter from registering.
        DatasetKey validKey = datasetKey("valid_adapter");
        TestAdapter invalidAdapter = TestAdapter.withDatasetKeyFailure(
                new IllegalStateException("database password=top-secret"));
        TestAdapter validAdapter = new TestAdapter(validKey);

        AdapterRegistry registry = new AdapterRegistry(List.of(invalidAdapter, validAdapter));

        assertThat(registry.find(validKey)).containsSame(validAdapter);
        assertThat(invalidAdapter.datasetKeyCalls).isEqualTo(1);
        assertThat(validAdapter.datasetKeyCalls).isEqualTo(1);
    }

    @Test
    void rejectsNullInputsAndSkipsNullExtensionsWithoutCallingWorkMethods() {
        // Catches missing null boundaries, registry work-method calls, or a future catch-all of Error/AssertionError.
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
        assertPluginCalls(plugin);
        assertThat(adapter.datasetKeyCalls).isEqualTo(1);

        Error pluginError = new Error("plugin error must propagate");
        TestPlugin failingPlugin = TestPlugin.withDescriptorError(pluginError);
        assertThatThrownBy(() -> new PluginRegistry(List.of(failingPlugin))).isSameAs(pluginError);
        assertThat(failingPlugin.descriptorCalls).isEqualTo(1);
        assertThat(failingPlugin.readinessCalls).isZero();

        AssertionError adapterError = new AssertionError("adapter assertion must propagate");
        TestAdapter failingAdapter = TestAdapter.withDatasetKeyError(adapterError);
        assertThatThrownBy(() -> new AdapterRegistry(List.of(failingAdapter))).isSameAs(adapterError);
        assertThat(failingAdapter.datasetKeyCalls).isEqualTo(1);
    }

    private static PluginDescriptor descriptor(PluginId pluginId, String displayName, boolean available, String reason) {
        return descriptor(pluginId, displayName, "Test plugin", ApiName.of("snapshot"), available, reason);
    }

    private static PluginDescriptor descriptor(
            PluginId pluginId,
            String displayName,
            String description,
            ApiName apiName,
            boolean available,
            String reason) {
        DatasetKey datasetKey = DatasetKey.of(pluginId, apiName);
        return new PluginDescriptor(pluginId, displayName, description, available, available, available,
                available ? null : reason,
                List.of(new ApiDescriptor(apiName, "Snapshot", "test", QueryMode.snapshot, List.of())),
                List.of(datasetKey));
    }

    private static void assertDuplicateDescriptor(PluginDescriptor actual, PluginDescriptor source) {
        assertThat(actual.pluginId()).isEqualTo(source.pluginId());
        assertThat(actual.displayName()).isEqualTo(source.displayName());
        assertThat(actual.description()).isEqualTo(source.description());
        assertThat(actual.enabled()).isEqualTo(source.enabled());
        assertThat(actual.credentialConfigured()).isEqualTo(source.credentialConfigured());
        assertThat(actual.downloadAvailable()).isFalse();
        assertThat(actual.unavailableReason()).isEqualTo("duplicate plugin id");
        assertThat(actual.apis()).isEqualTo(source.apis());
        assertThat(actual.datasets()).isEqualTo(source.datasets());
    }

    private static void assertPluginCalls(TestPlugin... plugins) {
        for (TestPlugin plugin : plugins) {
            assertThat(plugin.descriptorCalls).isEqualTo(1);
            assertThat(plugin.readinessCalls).isLessThanOrEqualTo(1);
        }
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
        private final RuntimeException descriptorFailure;
        private final RuntimeException readinessFailure;
        private final Error descriptorError;
        private int descriptorCalls;
        private int readinessCalls;

        private TestPlugin(PluginDescriptor descriptor, PluginReadiness readiness) {
            this(descriptor, readiness, null, null, null);
        }

        private TestPlugin(
                PluginDescriptor descriptor,
                PluginReadiness readiness,
                RuntimeException descriptorFailure,
                RuntimeException readinessFailure,
                Error descriptorError) {
            this.descriptor = descriptor;
            this.readiness = readiness;
            this.descriptorFailure = descriptorFailure;
            this.readinessFailure = readinessFailure;
            this.descriptorError = descriptorError;
        }

        private static TestPlugin withDescriptorFailure(RuntimeException failure) {
            return new TestPlugin(null, null, failure, null, null);
        }

        private static TestPlugin withReadinessFailure(PluginDescriptor descriptor, RuntimeException failure) {
            return new TestPlugin(descriptor, null, null, failure, null);
        }

        private static TestPlugin withDescriptorError(Error error) {
            return new TestPlugin(null, null, null, null, error);
        }

        @Override
        public PluginDescriptor descriptor() {
            descriptorCalls++;
            if (descriptorError != null) {
                throw descriptorError;
            }
            if (descriptorFailure != null) {
                throw descriptorFailure;
            }
            return descriptor;
        }

        @Override
        public PluginReadiness readiness() {
            readinessCalls++;
            if (readinessFailure != null) {
                throw readinessFailure;
            }
            return readiness;
        }

        @Override
        public DownloadEnvelope download(ApiName apiName, Map<String, Object> params) {
            throw new AssertionError("download must not be called");
        }
    }

    private static final class TestAdapter implements DatasetAdapter {
        private final DatasetKey datasetKey;
        private final RuntimeException datasetKeyFailure;
        private final Error datasetKeyError;
        private int datasetKeyCalls;

        private TestAdapter(DatasetKey datasetKey) {
            this(datasetKey, null, null);
        }

        private TestAdapter(DatasetKey datasetKey, RuntimeException datasetKeyFailure, Error datasetKeyError) {
            this.datasetKey = datasetKey;
            this.datasetKeyFailure = datasetKeyFailure;
            this.datasetKeyError = datasetKeyError;
        }

        private static TestAdapter withDatasetKeyFailure(RuntimeException failure) {
            return new TestAdapter(null, failure, null);
        }

        private static TestAdapter withDatasetKeyError(Error error) {
            return new TestAdapter(null, null, error);
        }

        @Override
        public DatasetKey datasetKey() {
            datasetKeyCalls++;
            if (datasetKeyError != null) {
                throw datasetKeyError;
            }
            if (datasetKeyFailure != null) {
                throw datasetKeyFailure;
            }
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
