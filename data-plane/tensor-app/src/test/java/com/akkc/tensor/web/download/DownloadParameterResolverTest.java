package com.akkc.tensor.web.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private static final ParameterValidator VALIDATOR = new ParameterValidator();
    private static final Map<String, String> VALUES = Map.ofEntries(
            Map.entry("trade_date", "20260905"), Map.entry("ann_date", "20260905"),
            Map.entry("ts_code", " 000001.sz "), Map.entry("exchange", "SSE"),
            Map.entry("exchange_id", "SZSE"),
            Map.entry("list_status", "L"),
            Map.entry("start_date", "20260901"), Map.entry("end_date", "20260905"),
            Map.entry("scenario", "EMPTY"));

    @ParameterizedTest
    @MethodSource("apis")
    void uniquelyMatchesAllSupportedApisAndRoundTripsRawValues(ApiDescriptor api) {
        var matches = ParameterCodec.supported().stream()
                .filter(codec -> codec.shape().equals(ParameterShape.from(api))).toList();
        assertThat(matches).singleElement();
        Map<String, Object> raw = new LinkedHashMap<>();
        api.parameters().forEach(parameter -> raw.put(parameter.name(), VALUES.get(parameter.name())));
        var codec = matches.getFirst();
        DownloadParameters parameters = codec.read(new ParameterJsonReader(raw, api, VALIDATOR));
        assertThat(parameters).isInstanceOf(expectedType(api.apiName().value()));
        assertThat(codec.write(parameters)).isEqualTo(raw);
    }

    static Stream<ApiDescriptor> apis() {
        List<DatasetDefinition> definitions = new TusharePluginConfiguration().tushareDatasetDefinitions();
        assertThat(definitions).hasSize(40);
        return Stream.concat(definitions.stream().map(definition -> new ApiDescriptor(
                definition.datasetKey().apiName(), definition.displayName(), definition.category(),
                definition.queryMode(), definition.parameters())),
                new FixtureConfiguration().fixturePlugin().descriptor().apis().stream());
    }

    private static Class<?> expectedType(String api) {
        return switch (api) {
            case "index_classify" -> SnapshotParameters.class;
            case "index_member_all", "pledge_detail", "pledge_stat", "stk_managers" -> TsCodeParameters.class;
            case "repurchase" -> AnnDateParameters.class;
            case "disclosure_date", "dividend", "express", "forecast", "stk_holdertrade", "top10_floatholders", "top10_holders" -> TsCodeAnnDateParameters.class;
            case "stock_company" -> ExchangeParameters.class;
            case "trade_cal" -> ExchangeDateRangeParameters.class;
            case "margin" -> ExchangeTradeDateParameters.class;
            case "stock_basic" -> ListStatusParameters.class;
            case "new_share" -> DateRangeParameters.class;
            case "slb_len" -> TradeDateOnlyParameters.class;
            case "adj_factor", "block_trade", "daily", "daily_basic", "margin_detail", "moneyflow", "monthly", "slb_sec", "slb_sec_detail", "stk_limit", "suspend_d", "top_list", "weekly" -> TradeDateParameters.class;
            case "stk_holdernumber", "stk_rewards", "fina_mainbz" -> TsCodeParameters.class;
            case "balancesheet", "cashflow", "fina_audit", "fina_indicator", "income" -> TsCodeAnnDateParameters.class;
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
        assertThat(ParameterShape.from(withParameters(api, reversed))).isEqualTo(ParameterShape.from(api));

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

    @ParameterizedTest
    @MethodSource("stockApis")
    void preservesSuppliedStockFieldsUntilServiceValidation(ApiDescriptor api) {
        var key = new FixtureConfiguration().fixtureDatasetAdapter().datasetKey();
        var bindingApi = new ApiDescriptor(key.apiName(), api.displayName(), api.category(),
                api.queryMode(), api.parameters());
        DownloadParameterResolver resolver = resolver(bindingApi);
        for (int variant = 0; variant < 3; variant++) {
            Map<String, Object> raw = new LinkedHashMap<>();
            api.parameters().forEach(parameter -> raw.put(parameter.name(), VALUES.get(parameter.name())));
            if (variant == 0) raw.remove("ts_code");
            else raw.put("ts_code", variant == 1 ? null : " ");
            var parameters = resolver.resolve(key, raw);
            assertThat(resolver.toRawValues(parameters, raw.keySet())).isEqualTo(raw);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "none,DateRangeParameters", "exchange,ExchangeDateRangeParameters",
        "exchange_id,ExchangeIdDateRangeParameters", "ts_code,TsCodeDateRangeParameters"
    })
    void bindsExplicitRangeShapeWithoutLookingUpSingleMetadata(String scope, String type) {
        var api = rangeApi(scope);
        var resolver = resolver(new FixtureConfiguration().fixturePlugin().descriptor().apis().getFirst());
        var raw = rangeValues(scope);
        var result = resolver.resolve(api, raw);
        assertThat(result.getClass().getSimpleName()).isEqualTo(type);
        var normalized = new LinkedHashMap<>(raw);
        if (scope.equals("ts_code")) normalized.put("ts_code", "000001.SZ");
        assertThat(resolver.toRawValues(result, raw.keySet())).isEqualTo(normalized);
        assertThat(ParameterCodec.supported().stream().filter(codec -> codec.shape().equals(ParameterShape.from(api))))
                .hasSize(1);
        assertThat(ParameterCodec.supported()).extracting(ParameterCodec::parameterType).doesNotHaveDuplicates();
        assertThat(ParameterCodec.supported()).extracting(ParameterCodec::shape).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"none", "exchange", "exchange_id", "ts_code"})
    void explicitlyValidatesRangeEndpointsAndRejectsExtraConditions(String scope) {
        var api = rangeApi(scope);
        var resolver = resolver(api);
        var valid = rangeValues(scope);
        for (String day : List.of("20240229", "20261231")) {
            var sameDay = new LinkedHashMap<>(valid);
            sameDay.put("start_date", day);
            sameDay.put("end_date", day);
            assertThatCode(() -> resolver.resolve(api, sameDay)).doesNotThrowAnyException();
        }
        for (String field : valid.keySet()) {
            var missing = new LinkedHashMap<>(valid);
            missing.remove(field);
            assertBindingFailure(resolver, api, missing, ErrorCode.PARAM_REQUIRED);
            for (Object bad : new Object[] {null, " ", 123, true, List.of(), Map.of()}) {
                var values = new LinkedHashMap<>(valid);
                values.put(field, bad);
                assertBindingFailure(resolver, api, values,
                        bad == null || " ".equals(bad) ? ErrorCode.PARAM_REQUIRED : ErrorCode.PARAM_INVALID);
            }
        }
        for (String badDate : List.of("20230229", "20240230", "2024-02-29", "20250101")) {
            var invalid = new LinkedHashMap<>(valid);
            invalid.put("start_date", badDate);
            assertBindingFailure(resolver, api, invalid, ErrorCode.PARAM_INVALID);
        }
        for (String extra : List.of("unknown", "trade_date", "ann_date", "symbol", "ts_code")) {
            if (valid.containsKey(extra)) continue;
            var invalid = new LinkedHashMap<>(valid);
            invalid.put(extra, "000001.SZ");
            assertBindingFailure(resolver, api, invalid, ErrorCode.PARAM_INVALID);
        }
    }

    @Test
    void rejectsAnExplicitDescriptionWithUnsupportedConstraints() {
        var api = rangeApi("ts_code");
        var fields = new ArrayList<>(api.parameters());
        var original = fields.getFirst();
        fields.set(0, new ParameterDescriptor(original.name(), original.label(), null,
                original.type(), false, null, List.of(), null, null));
        assertBindingFailure(resolver(api), withParameters(api, fields), rangeValues("ts_code"),
                ErrorCode.DATASET_MISCONFIGURED);
    }

    private static void assertBindingFailure(DownloadParameterResolver resolver, ApiDescriptor api,
            Map<String, Object> values, ErrorCode code) {
        assertThatThrownBy(() -> resolver.resolve(api, values))
                .isInstanceOfSatisfying(DownloadBindingException.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static ApiDescriptor rangeApi(String scope) {
        var fields = new ArrayList<ParameterDescriptor>();
        if (!scope.equals("none")) fields.add(new ParameterDescriptor(scope, "Scope", null,
                scope.equals("ts_code") ? ParameterType.TS_CODE : ParameterType.ENUM,
                true, null, scope.equals("ts_code") ? List.of() : List.of("SSE", "SZSE", "BSE"), null, null));
        fields.add(new ParameterDescriptor("start_date", "Start", null, ParameterType.DATE_RANGE_MEMBER,
                true, null, List.of(), null, "end_date"));
        fields.add(new ParameterDescriptor("end_date", "End", null, ParameterType.DATE_RANGE_MEMBER,
                true, null, List.of(), null, "start_date"));
        return new ApiDescriptor(ApiName.of("range_api"), "Range", "Test",
                com.akkc.tensor.plugin.api.descriptor.QueryMode.date_range, fields);
    }

    private static Map<String, Object> rangeValues(String scope) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!scope.equals("none")) result.put(scope, scope.equals("ts_code") ? " 000001.sz " : "SSE");
        result.put("start_date", "20240228");
        result.put("end_date", "20240301");
        return result;
    }

    @Test
    void bindsEveryActualProductionRangeDescriptorWithExactlyOneExistingCodec() {
        var configuration = new TusharePluginConfiguration();
        var plugin = configuration.tushareProPlugin(
                new com.akkc.tensor.plugin.tushare.config.TushareProperties(true,
                        java.net.URI.create("https://range-binding.invalid"),
                        new com.akkc.tensor.plugin.tushare.config.TushareProperties.Credential(""),
                        java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1), 1024),
                configuration.tushareDatasetDefinitions());
        var resolver = resolver(new FixtureConfiguration().fixturePlugin().descriptor().apis().getFirst());
        int stockCount = 0;
        int nonStockCount = 0;
        for (var single : plugin.descriptor().apis()) {
            var candidate = plugin
                    .batchDescriptor(single.apiName());
            if (candidate.isEmpty()) continue;
            var descriptor = candidate.orElseThrow();
            var api = new ApiDescriptor(single.apiName(), single.displayName(), single.category(),
                    com.akkc.tensor.plugin.api.descriptor.QueryMode.date_range, descriptor.parameters());
            var raw = new LinkedHashMap<String, Object>();
            descriptor.parameters().forEach(p -> raw.put(p.name(), VALUES.get(p.name())));
            var parameters = resolver.resolve(api, raw);
            Class<?> expected = switch (single.apiName().value()) {
                case "trade_cal" -> ExchangeDateRangeParameters.class;
                case "margin" -> ExchangeIdDateRangeParameters.class;
                case "new_share", "repurchase", "slb_len" -> DateRangeParameters.class;
                default -> TsCodeDateRangeParameters.class;
            };
            assertThat(parameters).isInstanceOf(expected);
            assertThat(ParameterCodec.supported().stream()
                    .filter(codec -> codec.shape().equals(ParameterShape.from(api)))).hasSize(1);
            if (expected == TsCodeDateRangeParameters.class) {
                stockCount++;
                raw.put("ts_code", "000001.SZ");
            } else nonStockCount++;
            assertThat(resolver.toRawValues(parameters, raw.keySet())).isEqualTo(raw);
            if (raw.containsKey("exchange") || raw.containsKey("exchange_id")) {
                raw.put(raw.containsKey("exchange") ? "exchange" : "exchange_id", "BSE");
                assertThatCode(() -> resolver.resolve(api, raw)).doesNotThrowAnyException();
            }
        }
        assertThat(stockCount).isEqualTo(29);
        assertThat(nonStockCount).isEqualTo(5);
    }

    static Stream<ApiDescriptor> stockApis() {
        return apis().filter(api -> api.parameters().stream().anyMatch(parameter ->
                parameter.name().equals("ts_code") && parameter.type() == ParameterType.TS_CODE));
    }

    private static ApiDescriptor withParameters(ApiDescriptor api, List<ParameterDescriptor> parameters) {
        return new ApiDescriptor(api.apiName(), api.displayName(), api.category(), api.queryMode(), parameters);
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
            public DownloadEnvelope download(ApiName name, Map<String, Object> params) {
                throw new AssertionError("Binding must not download");
            }
        };
        PluginRegistry plugins = new PluginRegistry(List.of(plugin));
        return new DownloadParameterResolver(new DownloadDescriptorResolver(plugins,
                new AdapterRegistry(List.of(fixture.fixtureDatasetAdapter()))), VALIDATOR);
    }
}
