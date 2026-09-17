package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.plugin.api.IntegrityCheckSupport;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityRule;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.fixture.integrity.FixtureIntegrityExtensionRule;
import com.akkc.tensor.plugin.fixture.integrity.FixtureIntegrityRules;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FixturePlugin implements IntegrityCheckSupport {
    private static final String DISPLAY_NAME = "Fixture";
    private static final String DESCRIPTION = "Fixture 验收数据源";
    private static final DatasetKey DATASET_KEY =
            DatasetKey.of(PluginId.of(FixtureConstants.PLUGIN_ID), ApiName.of(FixtureConstants.API_NAME));
    private final FixtureIntegrityRules integrity;
    private final List<IntegrityRule> integrityRules;
    private final FixtureEnvelopeFactory envelopeFactory;
    private final PluginReadiness readiness;
    private final PluginDescriptor descriptor;

    public FixturePlugin(DatasetDefinition definition, FixtureEnvelopeFactory envelopeFactory) {
        this(definition, envelopeFactory, 2);
    }

    public FixturePlugin(DatasetDefinition definition, FixtureEnvelopeFactory envelopeFactory, int integrityVersion) {
        definition = Objects.requireNonNull(definition, "definition");
        if (!DATASET_KEY.equals(definition.datasetKey())) {
            throw new IllegalArgumentException("definition must be fixture_daily");
        }
        this.envelopeFactory = Objects.requireNonNull(envelopeFactory, "envelopeFactory");
        integrity = new FixtureIntegrityRules(DATASET_KEY, integrityVersion);
        integrityRules = integrityVersion == 3
                ? List.of(integrity, new FixtureIntegrityExtensionRule()) : List.of(integrity);
        readiness = new PluginReadiness(true, true, true, null);
        ApiDescriptor api = new ApiDescriptor(
                definition.datasetKey().apiName(),
                definition.displayName(),
                definition.category(),
                definition.queryMode(),
                definition.parameters());
        descriptor = new PluginDescriptor(
                DATASET_KEY.pluginId(),
                DISPLAY_NAME,
                DESCRIPTION,
                readiness.enabled(),
                readiness.credentialConfigured(),
                readiness.downloadAvailable(),
                readiness.unavailableReason(),
                List.of(api),
                List.of(DATASET_KEY));
    }

    @Override
    public String normalizeIntegritySymbol(String symbol) {
        if (symbol == null || symbol.isBlank() || symbol.trim().length() > 64)
            throw new IllegalArgumentException("Invalid fixture symbol");
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public Optional<IntegrityDescriptor> integrityDescriptor(ApiName apiName) {
        return DATASET_KEY.apiName().equals(apiName) ? Optional.of(integrity.capability()) : Optional.empty();
    }

    @Override
    public List<IntegrityRule> integrityRules(ApiName apiName) {
        return DATASET_KEY.apiName().equals(apiName) ? integrityRules : List.of();
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
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        if (!DATASET_KEY.apiName().equals(apiName)) {
            throw new IllegalArgumentException("Unknown Fixture API");
        }
        Object scenarioValue = params.get(FixtureConstants.SCENARIO);
        if (!(scenarioValue instanceof String scenarioName)) {
            throw new IllegalArgumentException("Unknown Fixture scenario");
        }
        FixtureScenario scenario;
        try {
            scenario = FixtureScenario.valueOf(scenarioName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown Fixture scenario");
        }
        return envelopeFactory.create(scenario, params);
    }
}
