package com.akkc.tensor.plugin.tushare;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.*;
import java.util.Set;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.tushare.calendar.TushareCalendarProvider;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.client.TushareCompleteBatchFetcher;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TushareProPlugin implements DataSourcePlugin {
    private final TushareProperties properties;
    private final TushareProClient client;
    private final PluginDescriptor descriptor;
    private final Map<ApiName, DatasetDefinition> definitionsByApi;
    private final Map<ApiName, ApiDescriptor> apisByName;
    private final TushareCalendarProvider calendarProvider = new TushareCalendarProvider(Map.of());
    private final TushareCompleteBatchFetcher completeBatchFetcher;

    public TushareProPlugin(
            TushareProperties properties,
            TushareProClient client,
            List<DatasetDefinition> definitions, Map<ApiName, DownloadPolicy> policies) {
        this(properties, client, definitions, policies, new TushareCompleteBatchFetcher(client, Map.of()));
    }

    TushareProPlugin(
            TushareProperties properties,
            TushareProClient client,
            List<DatasetDefinition> definitions,
            Map<ApiName, DownloadPolicy> policies,
            TushareCompleteBatchFetcher completeBatchFetcher) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.client = Objects.requireNonNull(client, "client");
        this.completeBatchFetcher = Objects.requireNonNull(completeBatchFetcher, "completeBatchFetcher");
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
        if (definitions.size() != 49) {
            throw new DownloadMetadataException();
        }
        policies = Map.copyOf(Objects.requireNonNull(policies, "policies"));
        try {
            definitionsByApi = definitions.stream().collect(Collectors.toUnmodifiableMap(
                    definition -> definition.datasetKey().apiName(), Function.identity()));
            validatePolicies(policies);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new DownloadMetadataException();
        }
        final Map<ApiName, DownloadPolicy> frozenPolicies = policies;
        PluginReadiness readiness = properties.readiness();
        descriptor = new PluginDescriptor(
                PluginId.of("tushare_pro"),
                "Tushare Pro",
                "Tushare Pro 证券数据源",
                readiness.enabled(),
                readiness.credentialConfigured(),
                readiness.downloadAvailable(),
                readiness.unavailableReason(),
                definitions.stream().map(definition -> new ApiDescriptor(
                        definition.datasetKey().apiName(),
                        definition.displayName(),
                        definition.category(),
                        definition.queryMode(),
                        project(definition.parameters(), frozenPolicies.get(definition.datasetKey().apiName())),
                        frozenPolicies.get(definition.datasetKey().apiName()), definition.parameters())).toList(),
                definitions.stream().map(DatasetDefinition::datasetKey).toList());
        apisByName = descriptor.apis().stream().collect(Collectors.toUnmodifiableMap(
                ApiDescriptor::apiName, Function.identity()));
    }

    @Override
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public PluginReadiness readiness() {
        return properties.readiness();
    }

    @Override
    public CalendarDecision confirmCalendar(ApiName apiName, CalendarScope scope, DownloadContext context) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(context, "context").checkServerState();
        if (!readiness().downloadAvailable()) {
            throw new PluginUnavailableException();
        }
        ApiDescriptor api = apisByName.get(apiName);
        if (api == null) {
            throw new IllegalArgumentException("Unknown Tushare API");
        }
        return calendarProvider.confirm(api, scope, context);
    }

    @Override
    public FetchResult download(ApiName apiName, Map<String, Object> params, DownloadContext context) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        if (!readiness().downloadAvailable()) {
            throw new PluginUnavailableException();
        }
        DatasetDefinition definition = definitionsByApi.get(apiName);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Tushare API");
        }
        Objects.requireNonNull(context, "context").checkServerState();
        DownloadEnvelope envelope = client.execute(definition, params);
        context.checkServerState();
        return new FetchResult(envelope, List.of());
    }

    @Override
    public FetchResult fetchBatch(ApiName apiName, FetchBatch batch, DownloadContext context) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(batch, "batch");
        if (!readiness().downloadAvailable()) {
            throw new PluginUnavailableException();
        }
        DatasetDefinition definition = definitionsByApi.get(apiName);
        ApiDescriptor api = apisByName.get(apiName);
        if (definition == null || api == null) {
            throw new IllegalArgumentException("Unknown Tushare API");
        }
        Objects.requireNonNull(context, "context").checkServerState();
        return completeBatchFetcher.fetch(definition, api, batch, context);
    }

    @Override
    public DownloadPolicy.BatchPlanning planBatch(ApiName apiName, FetchBatch batch, DownloadContext context) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(batch, "batch");
        if (!readiness().downloadAvailable()) {
            throw new PluginUnavailableException();
        }
        DatasetDefinition definition = definitionsByApi.get(apiName);
        ApiDescriptor api = apisByName.get(apiName);
        if (definition == null || api == null) {
            throw new IllegalArgumentException("Unknown Tushare API");
        }
        Objects.requireNonNull(context, "context").checkServerState();
        return completeBatchFetcher.plan(definition, api, batch, context);
    }

    private void validatePolicies(Map<ApiName, DownloadPolicy> policies) {
        if (!policies.keySet().equals(definitionsByApi.keySet())) throw new DownloadMetadataException();
        int[] expected = {19, 15, 1, 3, 11};
        for (var mode : DownloadPolicy.Mode.values()) {
            if (policies.values().stream().filter(p -> p.mode() == mode).count() != expected[mode.ordinal()]) {
                throw new DownloadMetadataException();
            }
        }
        Map<DownloadPolicy.CalendarProfile, Set<String>> calendars = Map.of(
                DownloadPolicy.CalendarProfile.C_A, Set.of("adj_factor", "block_trade", "daily", "daily_basic", "margin_detail", "moneyflow", "monthly", "stk_limit", "suspend_d", "top_inst", "top_list", "weekly"),
                DownloadPolicy.CalendarProfile.C_M, Set.of("margin"),
                DownloadPolicy.CalendarProfile.C_S, Set.of("slb_len", "slb_sec", "slb_sec_detail"),
                DownloadPolicy.CalendarProfile.C_N, Set.of("hk_hold", "hsgt_top10"),
                DownloadPolicy.CalendarProfile.C_X, Set.of("moneyflow_hsgt"));
        for (var entry : policies.entrySet()) {
            var policy = entry.getValue();
            if (policy.recoveryPolicy().mode() != RecoveryPolicy.Mode.REQUEST
                    || policy.calendarProfile() != null && !calendars.get(policy.calendarProfile()).contains(entry.getKey().value())) {
                throw new DownloadMetadataException();
            }
        }
    }

    private static List<com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor> project(
            List<com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor> parameters, DownloadPolicy policy) {
        try {
            return DownloadParameterProjection.project(parameters, policy);
        } catch (IllegalArgumentException exception) {
            throw new DownloadMetadataException();
        }
    }

    private static final class DownloadMetadataException extends TensorException {
        private DownloadMetadataException() { super(ErrorCode.DATASET_MISCONFIGURED, "Invalid Tushare download metadata"); }
    }

    private static final class PluginUnavailableException extends TensorException {
        private PluginUnavailableException() {
            super(ErrorCode.PLUGIN_DISABLED, "Tushare Pro download is unavailable");
        }
    }
}
