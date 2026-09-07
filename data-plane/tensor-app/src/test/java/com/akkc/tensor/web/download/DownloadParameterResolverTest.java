package com.akkc.tensor.web.download;

import static org.assertj.core.api.Assertions.assertThat;
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
            Map.entry("exchange_id", "SZSE"), Map.entry("hs_type", "SH"),
            Map.entry("list_status", "L"), Map.entry("month", "202609"),
            Map.entry("start_date", "20260901"), Map.entry("end_date", "20260905"),
            Map.entry("scenario", "EMPTY"));

    @ParameterizedTest
    @MethodSource("apis")
    void uniquelyMatchesAllFiftyApisAndRoundTripsRawValues(ApiDescriptor api) {
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
        assertThat(definitions).hasSize(49);
        return Stream.concat(definitions.stream().map(definition -> new ApiDescriptor(
                definition.datasetKey().apiName(), definition.displayName(), definition.category(),
                definition.queryMode(), definition.parameters())),
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
