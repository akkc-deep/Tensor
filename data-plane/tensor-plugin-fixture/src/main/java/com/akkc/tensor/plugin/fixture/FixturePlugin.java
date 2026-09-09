package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FixturePlugin implements DataSourcePlugin {
    private static final DatasetKey DATASET_KEY =
            DatasetKey.of(PluginId.of("fixture"), ApiName.of("fixture_daily"));
    private final FixtureEnvelopeFactory envelopeFactory;
    private final PluginReadiness readiness;
    private final PluginDescriptor descriptor;
    private final DownloadPolicy policy;
    private final FixtureBatchSource source;

    public FixturePlugin(DatasetDefinition definition, FixtureEnvelopeFactory envelopeFactory) {
        this(definition, envelopeFactory, DownloadPolicy.Mode.ORIGINAL_PARAMS, RecoveryPolicy.Mode.REQUEST, null);
    }

    FixturePlugin(DatasetDefinition definition, FixtureEnvelopeFactory envelopeFactory,
            DownloadPolicy.Mode mode, RecoveryPolicy.Mode recovery, java.net.URI sourceUrl) {
        definition = Objects.requireNonNull(definition, "definition");
        if (!DATASET_KEY.equals(definition.datasetKey())) {
            throw new IllegalArgumentException("definition must be fixture_daily");
        }
        this.envelopeFactory = Objects.requireNonNull(envelopeFactory, "envelopeFactory");
        policy = policy(mode, recovery);
        source = sourceUrl == null ? null : new FixtureBatchSource(sourceUrl, definition);
        readiness = new PluginReadiness(true, true, true, null);
        ApiDescriptor api = new ApiDescriptor(
                definition.datasetKey().apiName(),
                definition.displayName(),
                definition.category(),
                definition.queryMode(),
                DownloadParameterProjection.project(definition.parameters(), policy), policy, definition.parameters());
        descriptor = new PluginDescriptor(
                DATASET_KEY.pluginId(),
                "Fixture",
                "Fixture 验收数据源",
                readiness.enabled(),
                readiness.credentialConfigured(),
                readiness.downloadAvailable(),
                readiness.unavailableReason(),
                List.of(api),
                List.of(DATASET_KEY));
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
    public FetchResult download(ApiName apiName, Map<String, Object> params, DownloadContext context) {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        if (!DATASET_KEY.apiName().equals(apiName)) {
            throw new IllegalArgumentException("Unknown Fixture API");
        }
        Object scenarioValue = params.get("scenario");
        if (!(scenarioValue instanceof String scenarioName)) {
            throw new IllegalArgumentException("Unknown Fixture scenario");
        }
        FixtureScenario scenario;
        try {
            scenario = FixtureScenario.valueOf(scenarioName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown Fixture scenario");
        }
        Objects.requireNonNull(context, "context").checkServerState();
        DownloadEnvelope envelope;
        try { envelope = envelopeFactory.create(scenario, params); }
        finally { context.checkServerState(); }
        return new FetchResult(envelope, List.of());
    }

    @Override
    public DownloadPolicy.BatchPlanning planBatch(ApiName apiName, FetchBatch batch, DownloadContext context) {
        Objects.requireNonNull(context, "context").checkServerState();
        validate(apiName, batch);
        return policy.batchPlanning();
    }

    @Override
    public FetchResult fetchBatch(ApiName apiName, FetchBatch batch, DownloadContext context) {
        validate(apiName, batch);
        if (source != null) return source.fetch(batch, context);
        if (policy.mode() == DownloadPolicy.Mode.ORIGINAL_PARAMS) return download(apiName, batch.sourceParams(), context);
        context.checkServerState();
        var params = batch.sourceParams();
        DownloadEnvelope original;
        try { original = envelopeFactory.create(FixtureScenario.valueOf((String) params.get("scenario")), params); }
        finally { context.checkServerState(); }
        var rows = new java.util.ArrayList<List<Object>>();
        if (!original.data().isEmpty()) {
            java.time.LocalDate start;
            java.time.LocalDate end;
            if (policy.mode() == DownloadPolicy.Mode.MONTH_RANGE) {
                start = java.time.YearMonth.parse((String) params.get("month"), java.time.format.DateTimeFormatter.ofPattern("uuuuMM")).atDay(1); end = start;
            } else if (policy.mode() == DownloadPolicy.Mode.NATIVE_RANGE) {
                start = date(params.get("start_date")); end = date(params.get("end_date"));
            } else { start = date(params.get(policy.sourceDateParameter())); end = start; }
            for (var day = start; !day.isAfter(end); day = day.plusDays(1)) {
                var row = new java.util.ArrayList<>(original.data().getFirst());
                row.set(0, params.getOrDefault("ts_code", "000001.SZ"));
                row.set(1, day.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)); rows.add(row);
            }
        }
        return new FetchResult(new DownloadEnvelope(DATASET_KEY.pluginId(), DATASET_KEY.apiName(), params,
                original.fields(), rows.size(), rows, DownloadStatus.SUCCESS, null), List.of());
    }

    @Override
    public CalendarDecision confirmCalendar(ApiName apiName, CalendarScope scope, DownloadContext context) {
        Objects.requireNonNull(context, "context").checkServerState();
        if (!DATASET_KEY.apiName().equals(apiName) || policy.mode() != DownloadPolicy.Mode.TRADE_DATE_RANGE)
            throw new com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException();
        if (source != null) return source.confirmCalendar(scope, context);
        var dates = new java.util.HashMap<java.time.LocalDate, Boolean>();
        scope.dates().forEach(date -> dates.put(date, true));
        context.checkServerState();
        return new CalendarDecision(scope, Map.of("fixture-controlled-all-open", dates));
    }

    private void validate(ApiName apiName, FetchBatch batch) {
        if (!DATASET_KEY.apiName().equals(apiName) || !policy.recoveryPolicy().equals(batch.recoveryPolicy()))
            throw new IllegalArgumentException("Fixture batch policy mismatch");
        var params = batch.sourceParams();
        var definitions = descriptor.apis().getFirst().sourceParameters();
        if (!definitions.stream().map(com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor::name).toList().containsAll(params.keySet()))
            throw new IllegalArgumentException("Unknown fixture source parameter");
        for (var parameter : definitions) if (parameter.required() && !params.containsKey(parameter.name()))
            throw new IllegalArgumentException("Missing fixture source parameter");
        FixtureScenario.valueOf((String) params.get("scenario"));
        if (params.containsKey("ts_code") && !((String) params.get("ts_code")).matches("[A-Z0-9]+\\.[A-Z0-9]+"))
            throw new IllegalArgumentException("Invalid fixture stock");
        if (policy.sourceRequestMode() == DownloadPolicy.SourceRequestMode.DATE) date(params.get(policy.sourceDateParameter()));
        if (policy.sourceRequestMode() == DownloadPolicy.SourceRequestMode.MONTH)
            java.time.YearMonth.parse((String) params.get("month"), java.time.format.DateTimeFormatter.ofPattern("uuuuMM"));
        if (policy.sourceRequestMode() == DownloadPolicy.SourceRequestMode.RANGE && date(params.get("start_date")).isAfter(date(params.get("end_date"))))
            throw new IllegalArgumentException("Invalid fixture range");
    }
    private static java.time.LocalDate date(Object value) {
        return java.time.LocalDate.parse((String) value, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static DownloadPolicy policy(DownloadPolicy.Mode mode, RecoveryPolicy.Mode recovery) {
        List<String> refs = List.of("data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java");
        String completeness = "受控fixture完整分页，不代表真实来源取全。";
        var semantics = switch (mode) {
            case ORIGINAL_PARAMS -> DownloadPolicy.DateSemantic.NONE;
            case TRADE_DATE_RANGE -> DownloadPolicy.DateSemantic.TRADE_DATE;
            case ANN_DATE_RANGE -> DownloadPolicy.DateSemantic.ANN_DATE;
            case MONTH_RANGE -> DownloadPolicy.DateSemantic.COVERED_MONTH;
            case NATIVE_RANGE -> DownloadPolicy.DateSemantic.CALENDAR_DATE;
        };
        var request = switch (mode) {
            case ORIGINAL_PARAMS -> DownloadPolicy.SourceRequestMode.NONE;
            case TRADE_DATE_RANGE, ANN_DATE_RANGE -> DownloadPolicy.SourceRequestMode.DATE;
            case MONTH_RANGE -> DownloadPolicy.SourceRequestMode.MONTH;
            case NATIVE_RANGE -> DownloadPolicy.SourceRequestMode.RANGE;
        };
        var planning = switch (request) {
            case NONE -> DownloadPolicy.BatchPlanning.ORIGINAL_PARAMS;
            case DATE -> DownloadPolicy.BatchPlanning.SINGLE_DATE;
            case MONTH -> DownloadPolicy.BatchPlanning.SINGLE_MONTH;
            case RANGE -> DownloadPolicy.BatchPlanning.SOURCE_RANGE;
        };
        String parameter = switch (mode) { case TRADE_DATE_RANGE -> "trade_date"; case ANN_DATE_RANGE -> "ann_date"; case MONTH_RANGE -> "month"; default -> null; };
        var recoveryPolicy = recovery == RecoveryPolicy.Mode.REQUEST
                ? new RecoveryPolicy(recovery, null, null, null, false, refs)
                : new RecoveryPolicy(recovery, "ts_code", "trade_date", RecoverySelector.TimeType.DATE, true, refs);
        return new DownloadPolicy(mode, semantics, "受控fixture场景，仅用于测试。",
                mode == DownloadPolicy.Mode.TRADE_DATE_RANGE ? DownloadPolicy.CalendarProfile.C_A : null,
                mode == DownloadPolicy.Mode.ORIGINAL_PARAMS ? null : new DownloadPolicy.Limits(31), request, parameter,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE, planning, recoveryPolicy,
                new DownloadPolicy.CompletenessPolicy(DownloadPolicy.CompletenessStatus.UNCONFIRMED,
                        DownloadPolicy.CompletenessStatus.UNCONFIRMED, completeness, completeness),
                mode == DownloadPolicy.Mode.TRADE_DATE_RANGE ? DownloadPolicy.CalendarEvidenceStatus.DOCUMENTED : null, refs);
    }
}
