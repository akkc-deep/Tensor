package com.akkc.tensor.core.integrity;

import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.IntegrityCheckSupport;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityRule;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class IntegrityPluginContractTest {
    private static final PluginId ID = PluginId.of("local");

    @Test
    void discoversLocalCapabilityWithoutCredentialsAndKeepsDownloadGate() {
        var plugin = new LocalPlugin(new PluginReadiness(true, false, false, "Credentials missing"));
        var input = new ArrayList<DataSourcePlugin>(List.of(plugin));
        var registry = new PluginRegistry(input);
        input.clear();

        assertThat(registry.findIntegrity(ID).available()).isTrue();
        assertThat(registry.findIntegrity(ID).support()).isSameAs(plugin);
        assertThat(registry.findIntegrity(ID).unavailableReason()).isNull();
        assertThat(registry.find(ID)).isEmpty();
        assertThat(registry.descriptors()).singleElement().satisfies(d -> {
            assertThat(d.enabled()).isTrue();
            assertThat(d.credentialConfigured()).isFalse();
            assertThat(d.downloadAvailable()).isFalse();
        });
        assertThat(plugin.descriptorCalls).isOne();
        assertThat(plugin.readinessCalls).isOne();
    }

    @Test
    void retainsDownloadableLegacyPluginWithExplicitUnsupportedReason() {
        var plugin = new LegacyPlugin(new PluginReadiness(true, true, true, null));
        var registry = new PluginRegistry(List.of(plugin));
        assertUnavailable(registry, "integrity check unsupported");
        assertThat(registry.find(ID)).containsSame(plugin);
    }

    @Test
    void disabledTakesPrecedenceOverCapabilityAndCredentials() {
        for (var plugin : List.of(
                new LegacyPlugin(new PluginReadiness(false, true, false, "Disabled")),
                new LocalPlugin(new PluginReadiness(false, false, false, "Disabled")))) {
            var registry = new PluginRegistry(List.of(plugin));
            assertUnavailable(registry, "plugin disabled");
            assertThat(registry.find(ID)).isEmpty();
        }
    }

    @Test
    void duplicateIdRejectsBothEntrypointsEvenIfOneReadinessFails() {
        var ready = new LocalPlugin(new PluginReadiness(true, true, true, null));
        var broken = new LocalPlugin(null);
        var registry = new PluginRegistry(List.of(ready, broken));
        assertUnavailable(registry, "duplicate plugin id");
        assertThat(registry.find(ID)).isEmpty();
        assertThat(registry.descriptors()).hasSize(2).allSatisfy(d ->
                assertThat(d.unavailableReason()).isEqualTo("duplicate plugin id"));
        assertThat(ready.readinessCalls).isOne();
        assertThat(broken.readinessCalls).isOne();
    }

    @Test
    void readinessFailureHasSafeReasonAndDoesNotReadCapabilityMetadata() {
        var registry = new PluginRegistry(List.of(new LocalPlugin(null)));
        assertUnavailable(registry, "plugin readiness unavailable");
        assertThat(registry.find(ID)).isEmpty();
    }

    @Test
    void enabledCapabilityIgnoresDownloadReasonAndDoesNotRecheckReadiness() {
        var plugin = new LocalPlugin(new PluginReadiness(true, false, false, "plugin readiness unavailable"));
        var registry = new PluginRegistry(List.of(plugin));
        assertThat(registry.findIntegrity(ID).support()).isSameAs(plugin);
        assertThat(plugin.readinessCalls).isOne();
    }

    @Test
    void unknownAndNullIdsHaveDeterministicResults() {
        var registry = new PluginRegistry(List.of());
        assertUnavailable(registry, "plugin not found");
        assertThatThrownBy(() -> registry.findIntegrity(null)).isInstanceOf(NullPointerException.class);
    }

    private static void assertUnavailable(PluginRegistry registry, String reason) {
        var result = registry.findIntegrity(ID);
        assertThat(result.available()).isFalse();
        assertThat(result.support()).isNull();
        assertThat(result.unavailableReason()).isEqualTo(reason);
    }

    private static class LegacyPlugin implements DataSourcePlugin {
        private final PluginReadiness readiness;
        int descriptorCalls;
        int readinessCalls;

        LegacyPlugin(PluginReadiness readiness) { this.readiness = readiness; }

        public PluginDescriptor descriptor() {
            descriptorCalls++;
            return new PluginDescriptor(ID, "Local", "Local check", false, false, false,
                    "stale descriptor", List.of(), List.of());
        }

        public PluginReadiness readiness() {
            readinessCalls++;
            if (readiness == null) throw new IllegalStateException("secret sentinel");
            return readiness;
        }

        public DownloadEnvelope download(ApiName api, Map<String, Object> params) {
            throw new AssertionError("No download during local discovery");
        }
    }

    private static final class LocalPlugin extends LegacyPlugin implements IntegrityCheckSupport {
        LocalPlugin(PluginReadiness readiness) { super(readiness); }
        public String normalizeIntegritySymbol(String symbol) { throw new AssertionError("No normalization"); }
        public Optional<IntegrityDescriptor> integrityDescriptor(ApiName api) { throw new AssertionError("No metadata"); }
        public List<IntegrityRule> integrityRules(ApiName api) { throw new AssertionError("No rules"); }
    }
}
