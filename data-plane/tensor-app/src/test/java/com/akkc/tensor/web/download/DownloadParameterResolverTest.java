package com.akkc.tensor.web.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.download.DownloadParameterConverter;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.DownloadParameterProjection;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.fixture.FixtureConfiguration;
import com.akkc.tensor.plugin.tushare.TusharePluginConfiguration;
import com.akkc.tensor.web.download.DownloadParameters.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DownloadParameterResolverTest {
    private static final Map<ApiName, DownloadPolicy> POLICIES = new TusharePluginConfiguration().tushareDownloadPolicies();
    private static final ParameterValidator VALIDATOR = new ParameterValidator();
    private static final Map<String, String> VALUES = Map.ofEntries(
            Map.entry("trade_date", "20260905"), Map.entry("ann_date", "20260905"),
            Map.entry("ts_code", " 000001.sz "), Map.entry("exchange", "SSE"),
            Map.entry("exchange_id", "SZSE"), Map.entry("hs_type", "SH"),
            Map.entry("list_status", "L"), Map.entry("month", "202609"),
            Map.entry("start_date", "20260901"), Map.entry("end_date", "20260905"),
            Map.entry("scenario", "EMPTY"));

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"TRADE_DATE_RANGE","ANN_DATE_RANGE","MONTH_RANGE","NATIVE_RANGE"})
    void acceptanceFixtureRangeShapesRoundTripOptionalStockAndCommonScenario(String mode) {
        var config = new FixtureConfiguration(mode, "REQUEST", "");
        var plugin = config.fixturePlugin();
        var resolver = new DownloadParameterResolver(new DownloadDescriptorResolver(
                new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(config.fixtureDatasetAdapter()))), VALIDATOR);
        var key = DatasetKey.of(PluginId.of("fixture"), ApiName.of("fixture_daily"));
        var raw = new LinkedHashMap<String,Object>(Map.of("scenario","SUCCESS","start_date","20260901","end_date","20260903"));
        var bound = resolver.resolveDownload(key,raw);
        assertThat(resolver.toRawValues(bound,raw.keySet())).isEqualTo(raw);
        var invalid = new LinkedHashMap<>(raw); invalid.put("scenario","UNKNOWN");
        assertThatThrownBy(() -> resolver.resolveDownload(key,invalid)).isInstanceOf(DownloadBindingException.class);
        var unknown = new LinkedHashMap<>(raw); unknown.put("extra","value");
        assertThatThrownBy(() -> resolver.resolveDownload(key,unknown)).isInstanceOf(DownloadBindingException.class);
        if (mode.equals("TRADE_DATE_RANGE") || mode.equals("ANN_DATE_RANGE")) {
            raw.put("ts_code","000002.SZ");
            bound = resolver.resolveDownload(key,raw);
            assertThat(resolver.toRawValues(bound,raw.keySet())).isEqualTo(raw);
        }
    }

    @ParameterizedTest
    @MethodSource("apis")
    void uniquelyMatchesAllFiftyApisAndRoundTripsRawValues(ApiDescriptor api) {
        var matches = ParameterCodec.supported().stream()
                .filter(codec -> codec.shape().equals(ParameterShape.from(api.sourceParameters()))).toList();
        assertThat(matches).singleElement();
        Map<String, Object> raw = new LinkedHashMap<>();
        api.sourceParameters().forEach(parameter -> raw.put(parameter.name(), VALUES.get(parameter.name())));
        var codec = matches.getFirst();
        DownloadParameters parameters = codec.read(new ParameterJsonReader(raw, api.sourceParameters(), VALIDATOR));
        assertThat(parameters).isInstanceOf(expectedType(api.apiName().value()));
        assertThat(codec.write(parameters)).isEqualTo(raw);
    }

    @ParameterizedTest
    @MethodSource("apis")
    void bindsAllProjectedShapesWithNormalizedValuesAndRejectsOldMixedOrUnknownFields(ApiDescriptor api) {
        var resolver = projectedResolver(api);
        var key = key(api);
        Map<String, Object> raw = new LinkedHashMap<>();
        api.parameters().forEach(p -> raw.put(p.name(), VALUES.get(p.name())));
        var result = resolver.resolveDownload(key, raw);
        var expected = new LinkedHashMap<>(raw);
        if (expected.containsKey("ts_code")) expected.put("ts_code", "000001.SZ");
        assertThat(resolver.toRawValues(result, raw.keySet())).isEqualTo(expected);
        Class<?> type = switch (expectedType(api.apiName().value()).getSimpleName()) {
            case "TradeDateParameters", "AnnDateParameters", "MonthParameters" -> DateRangeParameters.class;
            case "TsCodeAnnDateParameters" -> TsCodeDateRangeParameters.class;
            case "ExchangeTradeDateParameters" -> ExchangeIdDateRangeParameters.class;
            default -> expectedType(api.apiName().value());
        };
        assertThat(result).isInstanceOf(type);
        assertThat(resolver.toRawValues(result, java.util.Set.of())).isEmpty();
        var unknown = new LinkedHashMap<>(raw); unknown.put("unknown_field", "secret");
        assertBindingCode(() -> resolver.resolveDownload(key, unknown), ErrorCode.PARAM_INVALID);
        if (api.downloadPolicy().mode() != DownloadPolicy.Mode.ORIGINAL_PARAMS) {
            for (String old : List.of("trade_date", "ann_date", "month")) {
                assertBindingCode(() -> resolver.resolveDownload(key, Map.of(old, "20260905")), ErrorCode.PARAM_INVALID);
                var mixed = new LinkedHashMap<>(raw); mixed.put(old, "20260905");
                assertBindingCode(() -> resolver.resolveDownload(key, mixed), ErrorCode.PARAM_INVALID);
            }
            for (Object bad : List.of(20260901, "00000101", "20260229", "2026-09-01")) {
                var invalid = new LinkedHashMap<>(raw); invalid.put("start_date", bad);
                assertBindingCode(() -> resolver.resolveDownload(key, invalid), ErrorCode.PARAM_INVALID);
            }
            var longRange = new LinkedHashMap<>(raw); longRange.put("start_date", "20260131"); longRange.put("end_date", "20260303");
            assertBindingCode(() -> resolver.resolveDownload(key, longRange), ErrorCode.PARAM_INVALID);
            longRange.put("end_date", "20260302");
            assertThat(resolver.toRawValues(resolver.resolveDownload(key, longRange), longRange.keySet())).containsEntry("end_date", "20260302");
        } else {
            var dated = new LinkedHashMap<>(raw); dated.put("start_date", "20260901"); dated.put("end_date", "20260905");
            assertBindingCode(() -> resolver.resolveDownload(key, dated), ErrorCode.PARAM_INVALID);
        }
    }

    @Test
    void bindsExactly38RangeAnd11OriginalApisUsingNineProductionShapes() {
        var production = apis().filter(api -> !api.apiName().value().equals("fixture_daily")).toList();
        assertThat(production).hasSize(49);
        assertThat(production.stream().filter(api -> api.parameters().stream().anyMatch(p -> p.name().equals("start_date")))).hasSize(38);
        assertThat(production.stream().map(ParameterShape::from).distinct()).hasSize(9);
    }

    @Test
    void actualPoliciesMapExactSourceParametersWithoutUpgradingUnconfirmedOrStockRecovery() {
        var converter = new DownloadParameterConverter(VALIDATOR);
        var production = apis().filter(api -> !api.apiName().value().equals("fixture_daily"))
                .collect(java.util.stream.Collectors.toMap(api -> api.apiName().value(), api -> api));
        Map<String, Map<String, Object>> expected = Map.ofEntries(
                Map.entry("daily", Map.of("trade_date", "20260903")),
                Map.entry("income", Map.of("ts_code", "000001.SZ", "start_date", "20260903", "end_date", "20260903")),
                Map.entry("margin", Map.of("exchange_id", "SZSE", "start_date", "20260903", "end_date", "20260903")),
                Map.entry("fina_indicator", Map.of("ts_code", "000001.SZ", "ann_date", "20260903")),
                Map.entry("trade_cal", Map.of("exchange", "SSE", "start_date", "20260903", "end_date", "20260903")),
                Map.entry("new_share", Map.of("start_date", "20260903", "end_date", "20260903")),
                Map.entry("namechange", Map.of("start_date", "20260903", "end_date", "20260903")),
                Map.entry("broker_recommend", Map.of("month", "202609")),
                Map.entry("stock_basic", Map.of("list_status", "L")), Map.entry("index_classify", Map.of()));
        for (var entry : production.entrySet()) {
            var api = entry.getValue();
            var raw = new LinkedHashMap<String, Object>();
            api.parameters().forEach(p -> raw.put(p.name(), VALUES.get(p.name())));
            var original = converter.bindInitial(api, raw);
            var time = switch (api.downloadPolicy().mode()) {
                case MONTH_RANGE -> RecoverySelector.TimeType.MONTH;
                case ORIGINAL_PARAMS -> RecoverySelector.TimeType.NONE;
                default -> RecoverySelector.TimeType.DATE;
            };
            String value = switch (time) { case MONTH -> "2026-09"; case NONE -> ""; default -> "2026-09-03"; };
            var selector = new RecoverySelector(
                    RecoverySelector.TargetType.REQUEST, "", time, value);
            if (expected.containsKey(entry.getKey())) {
                var initial = converter.mapInitial(api, original, selector);
                assertThat(initial.sourceParams().values()).as(entry.getKey()).isEqualTo(expected.get(entry.getKey()));
                assertThat(converter.mapRetry(api, converter.taskParameters(api, original,
                        RecoverySelector.TargetType.REQUEST), selector)).isEqualTo(initial);
            }
            if (api.downloadPolicy().requestEvidenceStatus() != DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE) {
                assertThatThrownBy(() -> converter.mapInitial(api, original, selector))
                        .isInstanceOfSatisfying(SourceException.class,
                                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED));
            }
            var stock = new RecoverySelector(
                    RecoverySelector.TargetType.STOCK, "000001.SZ", time, value);
            assertThatThrownBy(() -> converter.mapRetry(api, original.values(), stock))
                    .isInstanceOf(TensorException.class);
        }
    }

    private static void assertBindingCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DownloadBindingException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static DatasetKey key(ApiDescriptor api) {
        return new DatasetKey(PluginId.of(
                api.apiName().value().equals("fixture_daily") ? "fixture" : "tushare_pro"), api.apiName());
    }

    private static DownloadParameterResolver projectedResolver(ApiDescriptor api) {
        if (api.apiName().value().equals("fixture_daily")) return resolver(api);
        var key = key(api);
        var definition = new TusharePluginConfiguration().tushareDatasetDefinitions().stream()
                .filter(d -> d.datasetKey().equals(key)).findFirst().orElseThrow();
        DataSourcePlugin plugin = new DataSourcePlugin() {
            public PluginDescriptor descriptor() { return new PluginDescriptor(key.pluginId(), "Test", "Test", true, true, true, null, List.of(api), List.of(key)); }
            public PluginReadiness readiness() { return new PluginReadiness(true, true, true, null); }
            public FetchResult download(ApiName name, Map<String, Object> params, DownloadContext context) { throw new AssertionError("Binding must not download"); }
        };
        var adapter = new GenericDatasetAdapter(definition,
                new ValueConverter(), new FingerprintKeyCodec());
        return new DownloadParameterResolver(new DownloadDescriptorResolver(new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(adapter))), VALIDATOR);
    }

    static Stream<ApiDescriptor> apis() {
        List<DatasetDefinition> definitions = new TusharePluginConfiguration().tushareDatasetDefinitions();
        assertThat(definitions).hasSize(49);
        return Stream.concat(definitions.stream().map(definition -> new ApiDescriptor(
                definition.datasetKey().apiName(), definition.displayName(), definition.category(),
                definition.queryMode(), DownloadParameterProjection.project(definition.parameters(), POLICIES.get(definition.datasetKey().apiName())), POLICIES.get(definition.datasetKey().apiName()), definition.parameters())),
                new FixtureConfiguration().fixturePlugin().descriptor().apis().stream());
    }

    private static Class<?> expectedType(String api) {
        return switch (api) {
            case "index_classify", "index_member", "index_member_all", "pledge_detail", "pledge_stat", "stk_managers" -> SnapshotParameters.class;
            case "disclosure_date", "dividend", "express", "forecast", "repurchase", "share_float", "stk_holdertrade", "top10_floatholders", "top10_holders" -> AnnDateParameters.class;
            case "stock_company" -> ExchangeParameters.class;
            case "trade_cal" -> ExchangeDateRangeParameters.class;
            case "margin" -> ExchangeTradeDateParameters.class;
            case "hs_const" -> HsTypeParameters.class;
            case "stock_basic" -> ListStatusParameters.class;
            case "broker_recommend" -> MonthParameters.class;
            case "namechange", "new_share" -> DateRangeParameters.class;
            case "adj_factor", "block_trade", "daily", "daily_basic", "hk_hold", "hsgt_top10", "margin_detail", "moneyflow", "moneyflow_hsgt", "monthly", "slb_len", "slb_sec", "slb_sec_detail", "stk_limit", "suspend_d", "top_inst", "top_list", "weekly" -> TradeDateParameters.class;
            case "stk_holdernumber", "stk_rewards" -> TsCodeParameters.class;
            case "balancesheet", "cashflow", "fina_audit", "fina_indicator", "fina_mainbz", "income" -> TsCodeAnnDateParameters.class;
            case "fixture_daily" -> ScenarioParameters.class;
            default -> throw new AssertionError("Unspecified API: " + api);
        };
    }

    @Test
    void ignoresPresentationAndDeclarationOrderButRejectsDifferentConstraints() {
        ApiDescriptor api = apis().filter(value -> value.apiName().value().equals("trade_cal")).findFirst().orElseThrow();
        List<ParameterDescriptor> reversed = new ArrayList<>(api.parameters());
        Collections.reverse(reversed);
        ParameterDescriptor exchange = reversed.getLast();
        reversed.set(reversed.size() - 1, new ParameterDescriptor(exchange.name(), "Other label", "Other description",
                exchange.type(), exchange.required(), exchange.defaultValue(), List.of("BSE", "SZSE", "SSE"),
                exchange.pattern(), exchange.relatedParameter()));
        assertThat(ParameterShape.from(withParameters(api, reversed))).isEqualTo(ParameterShape.from(api.sourceParameters()));

        var fixture = new FixtureConfiguration();
        ApiDescriptor original = fixture.fixturePlugin().descriptor().apis().getFirst();
        List<ParameterDescriptor> variants = List.of(
                new ParameterDescriptor("scenario", "Scenario", null, ParameterType.TEXT, true, "SUCCESS", List.of(), null, null),
                new ParameterDescriptor("scenario", "Scenario", null, ParameterType.ENUM, false, "SUCCESS", original.parameters().getFirst().allowedValues(), null, null),
                new ParameterDescriptor("scenario", "Scenario", null, ParameterType.ENUM, true, "EMPTY", original.parameters().getFirst().allowedValues(), null, null),
                new ParameterDescriptor("scenario", "Scenario", null, ParameterType.ENUM, true, "SUCCESS", List.of("SUCCESS", "EMPTY"), null, null),
                new ParameterDescriptor("scenario", "Scenario", null, ParameterType.ENUM, true, "SUCCESS", original.parameters().getFirst().allowedValues(), "[A-Z_]+", null),
                new ParameterDescriptor("scenario", "Scenario", null, ParameterType.ENUM, true, "SUCCESS", original.parameters().getFirst().allowedValues(), null, "other"),
                new ParameterDescriptor("other", "Other", null, ParameterType.TEXT, true, null, List.of(), null, null));
        for (ParameterDescriptor variant : variants) {
            DownloadParameterResolver resolver = resolver(withParameters(original, List.of(variant)));
            assertThatThrownBy(() -> resolver.resolve(fixture.fixtureDatasetAdapter().datasetKey(), Map.of("scenario", "SUCCESS")))
                    .isInstanceOfSatisfying(DownloadBindingException.class,
                            failure -> assertThat(failure.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED));
        }
    }

    @Test
    void preservesMissingNullAndBlankValuesUntilServiceValidation() {
        var fixture = new FixtureConfiguration();
        DownloadParameterResolver resolver = resolver(fixture.fixturePlugin().descriptor().apis().getFirst());
        var key = fixture.fixtureDatasetAdapter().datasetKey();
        for (Map<String, Object> raw : List.of(Map.<String, Object>of(),
                Collections.<String, Object>singletonMap("scenario", null), Map.<String, Object>of("scenario", " "))) {
            var parameters = resolver.resolve(key, raw);
            assertThat(parameters).isInstanceOf(ScenarioParameters.class);
            assertThat(resolver.toRawValues(parameters, raw.keySet())).isEqualTo(raw);
        }
    }

    private static ApiDescriptor withParameters(ApiDescriptor api, List<ParameterDescriptor> parameters) {
        return new ApiDescriptor(api.apiName(), api.displayName(), api.category(), api.queryMode(), parameters, com.akkc.tensor.test.DownloadPolicies.original(), parameters);
    }

    private static DownloadParameterResolver resolver(ApiDescriptor api) {
        var fixture = new FixtureConfiguration();
        var key = fixture.fixtureDatasetAdapter().datasetKey();
        DataSourcePlugin plugin = new DataSourcePlugin() {
            public PluginDescriptor descriptor() {
                return new PluginDescriptor(key.pluginId(), "Fixture", "Fixture", true, true, true, null,
                        List.of(api), List.of(key));
            }
            public PluginReadiness readiness() { return new PluginReadiness(true, true, true, null); }
            public FetchResult download(ApiName name, Map<String, Object> params, DownloadContext context) {
                throw new AssertionError("Binding must not download");
            }
        };
        PluginRegistry plugins = new PluginRegistry(List.of(plugin));
        return new DownloadParameterResolver(new DownloadDescriptorResolver(plugins,
                new AdapterRegistry(List.of(fixture.fixtureDatasetAdapter()))), VALIDATOR);
    }
}
