package com.akkc.tensor.plugin.tushare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.CalendarScope;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.client.TushareBatchSource;
import com.akkc.tensor.plugin.tushare.client.TushareCompleteBatchFetcher;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class TushareProPluginTest {
    private static final String SECRET = "m07-t04-secret-sentinel";
    private static final List<String> API_NAMES = List.of(
            "adj_factor", "balancesheet", "block_trade", "broker_recommend", "cashflow", "daily",
            "daily_basic", "disclosure_date", "dividend", "express", "fina_audit", "fina_indicator",
            "fina_mainbz", "forecast", "hk_hold", "hs_const", "hsgt_top10", "income", "index_classify",
            "index_member", "index_member_all", "margin", "margin_detail", "moneyflow", "moneyflow_hsgt",
            "monthly", "namechange", "new_share", "pledge_detail", "pledge_stat", "repurchase", "share_float",
            "slb_len", "slb_sec", "slb_sec_detail", "stk_holdernumber", "stk_holdertrade", "stk_limit",
            "stk_managers", "stk_rewards", "stock_basic", "stock_company", "suspend_d", "top10_floatholders",
            "top10_holders", "top_inst", "top_list", "trade_cal", "weekly");

    @Test
    void exposesOnlyTheApprovedPluginConfigurationAndUnavailableFailureSurface() {
        assertThat(Modifier.isPublic(TushareProPlugin.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(TushareProPlugin.class.getModifiers())).isTrue();
        assertThat(TushareProPlugin.class.getInterfaces()).containsExactly(DataSourcePlugin.class);
        assertThat(TushareProPlugin.class.getConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        TushareProperties.class, TushareProClient.class, List.class, Map.class));
        assertThat(publicDeclaredMethods(TushareProPlugin.class)).extracting(Method::getName)
                .containsExactlyInAnyOrder("descriptor", "readiness", "download", "confirmCalendar", "fetchBatch", "planBatch");

        assertThat(Modifier.isPublic(TusharePluginConfiguration.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(TusharePluginConfiguration.class.getModifiers())).isTrue();
        Configuration configuration = TusharePluginConfiguration.class.getAnnotation(Configuration.class);
        assertThat(configuration).isNotNull();
        assertThat(configuration.proxyBeanMethods()).isFalse();
        assertThat(TusharePluginConfiguration.class.getAnnotation(EnableConfigurationProperties.class).value())
                .containsExactly(TushareProperties.class);
        assertThat(TusharePluginConfiguration.class.getConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount()).isZero());
        assertThat(publicDeclaredMethods(TusharePluginConfiguration.class))
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("tushareDatasetDefinitions", "tushareDownloadPolicies", "tushareProPlugin");
        Method definitionsBean = Arrays.stream(publicDeclaredMethods(TusharePluginConfiguration.class))
                .filter(method -> method.getName().equals("tushareDatasetDefinitions"))
                .findFirst().orElseThrow();
        assertThat(definitionsBean.getParameterTypes()).isEmpty();
        assertThat(definitionsBean.getReturnType()).isEqualTo(List.class);
        assertThat(definitionsBean.getAnnotation(Bean.class).value())
                .containsExactly("tushareDatasetDefinitions");
        Method pluginBean = Arrays.stream(publicDeclaredMethods(TusharePluginConfiguration.class))
                .filter(method -> method.getName().equals("tushareProPlugin"))
                .findFirst().orElseThrow();
        assertThat(pluginBean.getParameterTypes())
                .containsExactly(TushareProperties.class, List.class, Map.class);
        assertThat(pluginBean.getReturnType()).isEqualTo(TushareProPlugin.class);
        assertThat(pluginBean.getAnnotation(Bean.class)).isNotNull();

        assertThat(Arrays.stream(TushareProPlugin.class.getDeclaredClasses()).filter(type -> type.getSimpleName().equals("PluginUnavailableException"))).singleElement().satisfies(type -> {
            int modifiers = type.getModifiers();
            assertThat(Modifier.isPrivate(modifiers)).isTrue();
            assertThat(Modifier.isStatic(modifiers)).isTrue();
            assertThat(Modifier.isFinal(modifiers)).isTrue();
            assertThat(type.getSuperclass()).isEqualTo(TensorException.class);
            assertThat(type.getDeclaredConstructors()).singleElement()
                    .satisfies(constructor -> {
                        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
                        assertThat(constructor.getParameterCount()).isZero();
                    });
            assertThat(publicDeclaredMethods(type)).isEmpty();
        });
    }

    @Test
    void projectsAllDefinitionMetadataIntoTheFixedReadyDescriptor() {
        List<DatasetDefinition> definitions = definitions();
        TushareProPlugin plugin = plugin(properties(true, SECRET), mock(TushareProClient.class), definitions);

        PluginDescriptor descriptor = plugin.descriptor();
        PluginReadiness readiness = plugin.readiness();
        assertThat(descriptor.pluginId().value()).isEqualTo("tushare_pro");
        assertThat(descriptor.displayName()).isEqualTo("Tushare Pro");
        assertThat(descriptor.description()).isEqualTo("Tushare Pro 证券数据源");
        assertDescriptorReadiness(descriptor, readiness);
        assertThat(descriptor.apis()).hasSize(49);
        assertThat(descriptor.datasets()).hasSize(49);

        for (int index = 0; index < definitions.size(); index++) {
            DatasetDefinition definition = definitions.get(index);
            ApiDescriptor api = descriptor.apis().get(index);
            assertThat(api.apiName()).isEqualTo(definition.datasetKey().apiName());
            assertThat(api.displayName()).isEqualTo(definition.displayName());
            assertThat(api.category()).isEqualTo(definition.category());
            assertThat(api.queryMode()).isEqualTo(definition.queryMode());
            assertThat(api.sourceParameters()).isEqualTo(definition.parameters());
            assertThat(descriptor.datasets().get(index)).isEqualTo(definition.datasetKey());
        }
        assertThat(!String.valueOf(descriptor).contains(SECRET)).as("descriptor omits credentials").isTrue();
    }

    @Test
    void requiresExactlyTheIndependentOrderedSetOfFortyNineApis() {
        List<DatasetDefinition> definitions = definitions();
        TushareProPlugin plugin = plugin(properties(true, SECRET), mock(TushareProClient.class), definitions);

        assertThat(plugin.descriptor().apis()).extracting(api -> api.apiName().value())
                .containsExactlyElementsOf(API_NAMES);
        assertThat(plugin.descriptor().datasets()).extracting(key -> key.apiName().value())
                .containsExactlyElementsOf(API_NAMES);
        TushareProPlugin reversed = plugin(
                properties(true, SECRET), mock(TushareProClient.class), definitions.reversed());
        assertThat(reversed.descriptor().apis()).extracting(api -> api.apiName().value())
                .containsExactlyElementsOf(API_NAMES.reversed());
        assertThat(reversed.descriptor().datasets()).extracting(key -> key.apiName().value())
                .containsExactlyElementsOf(API_NAMES.reversed());
        assertThatThrownBy(() -> plugin(properties(true, SECRET), mock(TushareProClient.class),
                definitions.subList(0, 48)))
                .isInstanceOf(TensorException.class)
                .hasMessage("Invalid Tushare download metadata");
        List<DatasetDefinition> fiftyDefinitions = new ArrayList<>(definitions);
        fiftyDefinitions.add(definitions.getFirst());
        assertThatThrownBy(() -> plugin(properties(true, SECRET), mock(TushareProClient.class), fiftyDefinitions))
                .isInstanceOf(TensorException.class)
                .hasMessage("Invalid Tushare download metadata");
    }

    @Test
    void keepsDescriptorAndReadinessAlignedForEveryLocalConfigurationState() {
        List<TushareProperties> states = List.of(
                properties(false, ""),
                properties(false, SECRET),
                properties(true, ""),
                properties(true, "   "),
                properties(true, SECRET));

        for (TushareProperties state : states) {
            TushareProPlugin plugin = plugin(state, mock(TushareProClient.class), definitions());
            assertDescriptorReadiness(plugin.descriptor(), state.readiness());
            assertThat(plugin.readiness()).isEqualTo(state.readiness());
            assertThat(!String.valueOf(plugin.descriptor()).contains(SECRET)
                    && !String.valueOf(plugin.readiness()).contains(SECRET)
                    && !String.valueOf(state).contains(SECRET))
                    .as("configuration surfaces omit credentials")
                    .isTrue();
        }
    }

    @Test
    void createsOnePluginBeanWithAllMetadataWhenMissingCredentialsOrDisabled() {
        assertLocalContext(Map.of("tensor.plugins.tushare-pro.base-url", "https://m07-t04.invalid"),
                true, false, "Credentials missing");
        assertLocalContext(Map.of(
                "tensor.plugins.tushare-pro.enabled", "false",
                "tensor.plugins.tushare-pro.base-url", "https://m07-t04.invalid",
                "tensor.plugins.tushare-pro.token", SECRET), false, true, "Disabled");
    }

    @Test
    void rejectsUnavailableDownloadsBeforeAnyClientInteraction() {
        for (TushareProperties state : List.of(
                properties(false, ""), properties(false, SECRET), properties(true, ""))) {
            TushareProClient client = mock(TushareProClient.class);
            TushareProPlugin plugin = plugin(state, client, definitions());

            Throwable failure = catchThrowable(() -> plugin.download(ApiName.of("daily"), Map.of(), () -> {}));

            assertThat(failure).isInstanceOf(TensorException.class);
            TensorException unavailable = (TensorException) failure;
            assertThat(unavailable.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
            assertThat(unavailable.getMessage()).isEqualTo("Tushare Pro download is unavailable");
            assertThat(unavailable.retryable()).isFalse();
            assertThat(unavailable.getCause()).isNull();
            assertThat(unavailable.getSuppressed()).isEmpty();
            verifyNoInteractions(client);
        }
    }

    @Test
    void productionBatchPlanningRefusesCompletenessBeforeCallingTheClient() {
        var client = mock(TushareProClient.class);
        var plugin = plugin(properties(true, SECRET), client, definitions());
        var counts = new java.util.EnumMap<ErrorCode, Integer>(ErrorCode.class);
        var codesByApi = new java.util.HashMap<String, ErrorCode>();
        for (var api : plugin.descriptor().apis()) {
            var batch = new FetchBatch(Map.of(), api.downloadPolicy().recoveryPolicy());
            assertThatThrownBy(() -> plugin.planBatch(api.apiName(), batch, () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, failure -> {
                        counts.merge(failure.code(), 1, Integer::sum);
                        codesByApi.put(api.apiName().value(), failure.code());
                        assertThat(failure.getCause()).isNull();
                        assertThat(failure.getSuppressed()).isEmpty();
                    });
        }
        assertThat(counts).containsEntry(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, 9)
                .containsEntry(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, 40);
        Set<String> requestBlocked = Set.of(
                "express", "fina_mainbz", "forecast", "hk_hold", "hs_const", "index_member",
                "moneyflow_hsgt", "top10_floatholders", "top10_holders");
        assertThat(codesByApi.entrySet().stream()
                .filter(entry -> entry.getValue() == ErrorCode.SOURCE_REQUEST_UNCONFIRMED)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(requestBlocked);
        assertThat(codesByApi.entrySet().stream()
                .filter(entry -> entry.getValue() == ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(API_NAMES.stream()
                        .filter(name -> !requestBlocked.contains(name)).toList());

        var stockBasic = plugin.descriptor().apis().stream()
                .filter(api -> api.apiName().value().equals("stock_basic")).findFirst().orElseThrow();
        for (String status : List.of("L", "P", "D")) {
            var stockBatch = new FetchBatch(Map.of("list_status", status),
                    stockBasic.downloadPolicy().recoveryPolicy());
            assertThatThrownBy(() -> plugin.planBatch(stockBasic.apiName(), stockBatch, () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, failure ->
                            assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED));
        }

        var tradeCal = plugin.descriptor().apis().stream()
                .filter(api -> api.apiName().value().equals("trade_cal")).findFirst().orElseThrow();
        var bse = new FetchBatch(Map.of("exchange", "BSE", "start_date", "20260903", "end_date", "20260904"),
                tradeCal.downloadPolicy().recoveryPolicy());
        assertThatThrownBy(() -> plugin.planBatch(tradeCal.apiName(), bse, () -> {}))
                .isInstanceOfSatisfying(SourceException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
                    assertThat(failure.getMessage()).isEqualTo("Tushare request conditions are unconfirmed");
                });
        for (String exchange : List.of("SSE", "SZSE")) {
            var calendarBatch = new FetchBatch(Map.of("exchange", exchange,
                    "start_date", "20260903", "end_date", "20260904"),
                    tradeCal.downloadPolicy().recoveryPolicy());
            assertThatThrownBy(() -> plugin.planBatch(tradeCal.apiName(), calendarBatch, () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, failure ->
                            assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED));
        }
        verifyNoInteractions(client);
    }

    @Test
    void batchPlanningValidatesInputsReadinessLookupAndContextInTheSpecifiedOrder() {
        var client = mock(TushareProClient.class);
        var ready = plugin(properties(true, SECRET), client, definitions());
        var daily = ready.descriptor().apis().stream()
                .filter(api -> api.apiName().value().equals("daily")).findFirst().orElseThrow();
        var batch = new FetchBatch(Map.of("trade_date", "20260903"), daily.downloadPolicy().recoveryPolicy());
        assertThatThrownBy(() -> ready.planBatch(null, batch, () -> {}))
                .isInstanceOf(NullPointerException.class).hasMessage("apiName");
        assertThatThrownBy(() -> ready.planBatch(daily.apiName(), null, () -> {}))
                .isInstanceOf(NullPointerException.class).hasMessage("batch");
        AtomicInteger checks = new AtomicInteger();
        assertThatThrownBy(() -> ready.planBatch(ApiName.of("unknown_api"), batch, checks::incrementAndGet))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Tushare API");
        assertThat(checks).hasValue(0);

        var disabled = plugin(properties(false, SECRET), client, definitions());
        assertThatThrownBy(() -> disabled.planBatch(ApiName.of("unknown_api"), batch, checks::incrementAndGet))
                .isInstanceOfSatisfying(TensorException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED));
        assertThat(checks).hasValue(0);

        RuntimeException fault = new IllegalStateException("server unavailable");
        assertThatThrownBy(() -> ready.planBatch(daily.apiName(), batch, () -> { throw fault; })).isSameAs(fault);
        assertThatThrownBy(() -> ready.planBatch(daily.apiName(), batch, checks::incrementAndGet))
                .isInstanceOf(SourceException.class);
        assertThat(checks).hasValue(2);
        verifyNoInteractions(client);
    }

    @Test
    void productionBatchFetchRefusesCompletenessBeforeCallingTheClient() {
        var client = mock(TushareProClient.class);
        var plugin = plugin(properties(true, SECRET), client, definitions());
        var counts = new java.util.EnumMap<ErrorCode, Integer>(ErrorCode.class);
        var codesByApi = new java.util.HashMap<String, ErrorCode>();
        for (var api : plugin.descriptor().apis()) {
            var batch = new FetchBatch(Map.of(), api.downloadPolicy().recoveryPolicy());
            assertThatThrownBy(() -> plugin.fetchBatch(api.apiName(), batch, () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, failure -> {
                        counts.merge(failure.code(), 1, Integer::sum);
                        codesByApi.put(api.apiName().value(), failure.code());
                        assertThat(failure.getCause()).isNull();
                        assertThat(failure.getSuppressed()).isEmpty();
                    });
        }
        assertThat(counts).containsEntry(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, 9)
                .containsEntry(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, 40);
        Set<String> requestBlocked = Set.of(
                "express", "fina_mainbz", "forecast", "hk_hold", "hs_const", "index_member",
                "moneyflow_hsgt", "top10_floatholders", "top10_holders");
        assertThat(codesByApi.entrySet().stream()
                .filter(entry -> entry.getValue() == ErrorCode.SOURCE_REQUEST_UNCONFIRMED)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(requestBlocked);
        assertThat(codesByApi.entrySet().stream()
                .filter(entry -> entry.getValue() == ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(API_NAMES.stream()
                        .filter(name -> !requestBlocked.contains(name)).toList());

        var stockBasic = plugin.descriptor().apis().stream()
                .filter(api -> api.apiName().value().equals("stock_basic")).findFirst().orElseThrow();
        for (String status : List.of("L", "P", "D")) {
            var stockBatch = new FetchBatch(Map.of("list_status", status),
                    stockBasic.downloadPolicy().recoveryPolicy());
            assertThatThrownBy(() -> plugin.fetchBatch(stockBasic.apiName(), stockBatch, () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, failure ->
                            assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED));
        }

        var tradeCal = plugin.descriptor().apis().stream()
                .filter(api -> api.apiName().value().equals("trade_cal")).findFirst().orElseThrow();
        var bse = new FetchBatch(Map.of("exchange", "BSE", "start_date", "20260903", "end_date", "20260904"),
                tradeCal.downloadPolicy().recoveryPolicy());
        assertThatThrownBy(() -> plugin.fetchBatch(tradeCal.apiName(), bse, () -> {}))
                .isInstanceOfSatisfying(SourceException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
                    assertThat(failure.getMessage()).isEqualTo("Tushare request conditions are unconfirmed");
                });
        for (String exchange : List.of("SSE", "SZSE")) {
            var calendarBatch = new FetchBatch(Map.of("exchange", exchange,
                    "start_date", "20260903", "end_date", "20260904"),
                    tradeCal.downloadPolicy().recoveryPolicy());
            assertThatThrownBy(() -> plugin.fetchBatch(tradeCal.apiName(), calendarBatch, () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, failure ->
                            assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED));
        }
        verifyNoInteractions(client);
    }

    @Test
    void injectedPlannerUsesExactParametersAndThreeContextChecksWithoutOpening() {
        var definitions = definitions(); var policies = new TusharePluginConfiguration().tushareDownloadPolicies();
        var client = mock(TushareProClient.class);
        var params = Map.<String,Object>of("trade_date", "20260903"); var plans = new AtomicInteger();
        var fault = new SourceException(ErrorCode.SOURCE_TIMEOUT, "Controlled failure");
        TushareBatchSource source = new TushareBatchSource() {
            public DownloadPolicy.BatchPlanning plan(DatasetDefinition definition, ApiDescriptor api, FetchBatch batch) {
                plans.incrementAndGet();
                assertThat(definition.datasetKey().apiName()).isEqualTo(ApiName.of("daily"));
                assertThat(api.apiName()).isEqualTo(ApiName.of("daily"));
                if (!batch.sourceParams().equals(params)) throw fault;
                return DownloadPolicy.BatchPlanning.SINGLE_DATE;
            }
            public Session open(DatasetDefinition definition, ApiDescriptor api, FetchBatch batch) { throw new AssertionError("open"); }
        };
        var plugin = new TushareProPlugin(properties(true, SECRET), client, definitions, policies,
                new TushareCompleteBatchFetcher(client, Map.of(ApiName.of("daily"), source)));
        var api = plugin.descriptor().apis().stream().filter(a -> a.apiName().value().equals("daily")).findFirst().orElseThrow();
        var checks = new AtomicInteger();
        assertThat(plugin.planBatch(api.apiName(), new FetchBatch(params, api.downloadPolicy().recoveryPolicy()), checks::incrementAndGet)).isEqualTo(DownloadPolicy.BatchPlanning.SINGLE_DATE);
        assertThat(checks).hasValue(3); assertThat(plans).hasValue(1);
        assertThatThrownBy(() -> plugin.planBatch(api.apiName(), new FetchBatch(Map.of("trade_date", "20260904"), api.downloadPolicy().recoveryPolicy()), () -> {})).isSameAs(fault);
        assertThat(plans).hasValue(2); verifyNoInteractions(client);
    }

    @Test
    void injectedBatchFetcherCompletesTwoPagesWithSevenPluginContextChecks() {
        var definitions = definitions();
        var policies = new TusharePluginConfiguration().tushareDownloadPolicies();
        var daily = definitions.stream()
                .filter(definition -> definition.datasetKey().apiName().value().equals("daily"))
                .findFirst().orElseThrow();
        var params = Map.<String, Object>of("trade_date", "20260903");
        var secondParams = Map.<String, Object>of("trade_date", "20260903", "page_token", "p2");
        var fields = daily.columns().stream().map(column -> column.name()).toList();
        var client = mock(TushareProClient.class);
        when(client.execute(daily, params)).thenReturn(new DownloadEnvelope(
                daily.datasetKey().pluginId(), daily.datasetKey().apiName(), params, fields, 1,
                List.of(List.of("000001.SZ", "20260903", 1, 2, 3, 4, 5, 6, 7, 8, 9)),
                DownloadStatus.SUCCESS, null));
        when(client.execute(daily, secondParams)).thenReturn(new DownloadEnvelope(
                daily.datasetKey().pluginId(), daily.datasetKey().apiName(), secondParams, fields, 1,
                List.of(List.of("000002.SZ", "20260903", 1, 2, 3, 4, 5, 6, 7, 8, 9)),
                DownloadStatus.SUCCESS, null));
        TushareBatchSource source = (definition, api, batch) -> new TushareBatchSource.Session() {
            private int page;
            public Set<String> paginationParameters() { return Set.of("page_token"); }
            public Map<String, Object> pageParameters(String cursor) {
                return cursor == null ? Map.of() : Map.of("page_token", cursor);
            }
            public TushareBatchSource.Observation observe(String cursor, DownloadEnvelope envelope) {
                return page++ == 0
                        ? new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", 2L)
                        : new TushareBatchSource.Observation(TushareBatchSource.End.COMPLETE, null, 2L);
            }
            public void validateComplete(DownloadEnvelope complete) {
                assertThat(complete.rowCount()).isEqualTo(2);
            }
            @Override public String toString() { return "InjectedSession[REDACTED]"; }
        };
        var fetcher = new TushareCompleteBatchFetcher(client, Map.of(ApiName.of("daily"), source));
        var plugin = new TushareProPlugin(properties(true, SECRET), client, definitions, policies, fetcher);
        var api = plugin.descriptor().apis().stream()
                .filter(candidate -> candidate.apiName().value().equals("daily")).findFirst().orElseThrow();
        var checks = new AtomicInteger();

        var result = plugin.fetchBatch(api.apiName(),
                new FetchBatch(params, api.downloadPolicy().recoveryPolicy()), checks::incrementAndGet);

        assertThat(result.envelope().data()).extracting(row -> row.getFirst())
                .containsExactly("000001.SZ", "000002.SZ");
        assertThat(result.envelope().params()).isEqualTo(params);
        assertThat(result.failures()).isEmpty();
        assertThat(checks).hasValue(7);
        verify(client).execute(daily, params);
        verify(client).execute(daily, secondParams);
        verifyNoMoreInteractions(client);
    }

    @Test
    void batchFetchValidatesInputsReadinessLookupAndContextInTheSpecifiedOrder() {
        var client = mock(TushareProClient.class);
        var ready = plugin(properties(true, SECRET), client, definitions());
        var daily = ready.descriptor().apis().stream()
                .filter(api -> api.apiName().value().equals("daily")).findFirst().orElseThrow();
        var batch = new FetchBatch(Map.of("trade_date", "20260903"), daily.downloadPolicy().recoveryPolicy());
        assertThatThrownBy(() -> ready.fetchBatch(null, batch, () -> {}))
                .isInstanceOf(NullPointerException.class).hasMessage("apiName");
        assertThatThrownBy(() -> ready.fetchBatch(daily.apiName(), null, () -> {}))
                .isInstanceOf(NullPointerException.class).hasMessage("batch");
        AtomicInteger checks = new AtomicInteger();
        assertThatThrownBy(() -> ready.fetchBatch(ApiName.of("unknown_api"), batch, checks::incrementAndGet))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Tushare API");
        assertThat(checks).hasValue(0);

        var disabled = plugin(properties(false, SECRET), client, definitions());
        assertThatThrownBy(() -> disabled.fetchBatch(ApiName.of("unknown_api"), batch, checks::incrementAndGet))
                .isInstanceOfSatisfying(TensorException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED));
        assertThat(checks).hasValue(0);

        RuntimeException fault = new IllegalStateException("server unavailable");
        assertThatThrownBy(() -> ready.fetchBatch(daily.apiName(), batch, () -> { throw fault; })).isSameAs(fault);
        assertThatThrownBy(() -> ready.fetchBatch(daily.apiName(), batch, checks::incrementAndGet))
                .isInstanceOf(SourceException.class);
        assertThat(checks).hasValue(2);
        verifyNoInteractions(client);
    }

    @Test
    void rejectsNullAndUnknownInputsInTheApprovedOrderWithoutCallingTheClient() {
        TushareProClient readyClient = mock(TushareProClient.class);
        TushareProPlugin ready = plugin(properties(true, SECRET), readyClient, definitions());

        assertThatThrownBy(() -> ready.download(null, Map.of(), () -> {}))
                .isInstanceOf(NullPointerException.class).hasMessage("apiName");
        assertThatThrownBy(() -> ready.download(ApiName.of("daily"), null, () -> {}))
                .isInstanceOf(NullPointerException.class).hasMessage("params");
        Throwable unknown = catchThrowable(() -> ready.download(ApiName.of("unknown_api"), Map.of(), () -> {}));
        assertThat(unknown).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown Tushare API");
        assertThat(!String.valueOf(unknown).contains("unknown_api"))
                .as("unknown API failure omits caller input").isTrue();
        verifyNoInteractions(readyClient);

        TushareProClient disabledClient = mock(TushareProClient.class);
        TushareProPlugin disabled = plugin(properties(false, SECRET), disabledClient, definitions());
        Throwable disabledUnknown = catchThrowable(
                () -> disabled.download(ApiName.of("unknown_api"), Map.of(), () -> {}));
        assertThat(disabledUnknown).isInstanceOf(TensorException.class);
        assertThat(((TensorException) disabledUnknown).code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
        verifyNoInteractions(disabledClient);

        TushareProClient missingCredentialClient = mock(TushareProClient.class);
        TushareProPlugin missingCredential = plugin(
                properties(true, ""), missingCredentialClient, definitions());
        Throwable missingCredentialUnknown = catchThrowable(
                () -> missingCredential.download(ApiName.of("unknown_api"), Map.of(), () -> {}));
        assertThat(missingCredentialUnknown).isInstanceOf(TensorException.class);
        assertThat(((TensorException) missingCredentialUnknown).code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
        verifyNoInteractions(missingCredentialClient);
    }

    @Test
    void delegatesTheExactDailyCallOnceAndPreservesResultAndSourceFailureIdentity() {
        List<DatasetDefinition> definitions = definitions();
        DatasetDefinition daily = definitions.stream()
                .filter(definition -> definition.datasetKey().apiName().value().equals("daily"))
                .findFirst().orElseThrow();
        Map<String, Object> params = Map.of("trade_date", "20260903");
        DownloadEnvelope envelope = mock(DownloadEnvelope.class);
        TushareProClient successClient = mock(TushareProClient.class);
        when(successClient.execute(same(daily), same(params))).thenReturn(envelope);

        DownloadEnvelope result = plugin(properties(true, SECRET), successClient, definitions)
                .download(ApiName.of("daily"), params, () -> {}).envelope();

        assertThat(result).isSameAs(envelope);
        verify(successClient).execute(same(daily), same(params));
        verifyNoMoreInteractions(successClient);

        SourceException sourceFailure = new SourceException(
                ErrorCode.SOURCE_TIMEOUT, "Tushare response timed out");
        TushareProClient failureClient = mock(TushareProClient.class);
        when(failureClient.execute(same(daily), same(params))).thenThrow(sourceFailure);

        assertThatThrownBy(() -> plugin(properties(true, SECRET), failureClient, definitions)
                .download(ApiName.of("daily"), params, () -> {})).isSameAs(sourceFailure);
        verify(failureClient).execute(same(daily), same(params));
        verifyNoMoreInteractions(failureClient);
    }

    @Test
    void checksServerBeforeAndAfterFetchAndUnconfirmedCalendarNeverCallsBusinessSource() {
        var definitions = definitions();
        var daily = definitions.stream().filter(d -> d.datasetKey().apiName().value().equals("daily")).findFirst().orElseThrow();
        var params = Map.<String,Object>of("trade_date", "20260903");
        var client = mock(TushareProClient.class);
        var plugin = plugin(properties(true, SECRET), client, definitions);
        var fault = new IllegalStateException("server unavailable");
        assertThatThrownBy(() -> plugin.download(ApiName.of("daily"), params, () -> { throw fault; })).isSameAs(fault);
        verifyNoInteractions(client);
        assertThatThrownBy(() -> plugin.confirmCalendar(ApiName.of("daily"),
                new com.akkc.tensor.plugin.api.download.CalendarScope(Map.of(), java.util.Set.of(java.time.LocalDate.of(2026, 9, 3))), () -> {}))
                .isInstanceOf(com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException.class);
        verifyNoInteractions(client);
        var envelope = new DownloadEnvelope(daily.datasetKey().pluginId(), daily.datasetKey().apiName(), params,
                List.of("ts_code"), 0, List.of(), com.akkc.tensor.plugin.api.download.DownloadStatus.SUCCESS, null);
        when(client.execute(daily, params)).thenReturn(envelope);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(() -> plugin.download(ApiName.of("daily"), params, () -> {
            if (checks.incrementAndGet() == 2) throw fault;
        })).isSameAs(fault);
        assertThat(checks).hasValue(2);
        verify(client).execute(daily, params);
        verifyNoMoreInteractions(client);
    }

    @Test
    void refusesAllProductionCalendarsWithoutCallingTheBusinessClient() {
        var client = mock(TushareProClient.class);
        var plugin = plugin(properties(true, SECRET), client, definitions());
        var scope = new CalendarScope(Map.of("start_date", "20260901", "end_date", "20260910"),
                Set.of(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 7)));
        assertThat(plugin.descriptor().apis().stream().filter(api ->
                api.downloadPolicy().mode() == DownloadPolicy.Mode.TRADE_DATE_RANGE)).hasSize(19);
        for (var api : plugin.descriptor().apis()) {
            assertThatThrownBy(() -> plugin.confirmCalendar(api.apiName(), scope, () -> {}))
                    .isInstanceOfSatisfying(CalendarUnconfirmedException.class, failure -> {
                        assertThat(failure.code()).isEqualTo(ErrorCode.CALENDAR_UNCONFIRMED);
                        assertThat(failure.getMessage()).isEqualTo("Applicable calendars are unconfirmed");
                        assertThat(failure.getCause()).isNull();
                        assertThat(failure.getSuppressed()).isEmpty();
                    });
        }
        verifyNoInteractions(client);
    }

    @Test
    void validatesCalendarInputsAndReadinessBeforeLookingUpTheApi() {
        var client = mock(TushareProClient.class);
        var ready = plugin(properties(true, SECRET), client, definitions());
        var scope = new CalendarScope(Map.of(), Set.of(LocalDate.of(2026, 9, 3)));
        var checks = new AtomicInteger();
        DownloadContext context = checks::incrementAndGet;
        assertThatThrownBy(() -> ready.confirmCalendar(null, scope, context))
                .isInstanceOf(NullPointerException.class).hasMessage("apiName");
        assertThatThrownBy(() -> ready.confirmCalendar(ApiName.of("daily"), null, context))
                .isInstanceOf(NullPointerException.class).hasMessage("scope");
        assertThatThrownBy(() -> ready.confirmCalendar(ApiName.of("daily"), scope, null))
                .isInstanceOf(NullPointerException.class).hasMessage("context");
        assertThat(checks).hasValue(0);
        assertThatThrownBy(() -> ready.confirmCalendar(ApiName.of("unknown_api"), scope, context))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Tushare API");
        assertThat(checks).hasValue(1);
        for (var state : List.of(properties(false, ""), properties(false, SECRET), properties(true, ""))) {
            var unavailable = plugin(state, client, definitions());
            assertThatThrownBy(() -> unavailable.confirmCalendar(ApiName.of("unknown_api"), scope, context))
                    .isInstanceOfSatisfying(TensorException.class, failure -> {
                        assertThat(failure.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
                        assertThat(failure.getMessage()).isEqualTo("Tushare Pro download is unavailable");
                    });
        }
        assertThat(checks).hasValue(4);
        verifyNoInteractions(client);
    }

    @Test
    void propagatesCalendarServerFailuresBeforeReadinessAndAtProviderEntry() {
        var client = mock(TushareProClient.class);
        var scope = new CalendarScope(Map.of(), Set.of(LocalDate.of(2026, 9, 3)));
        var fault = new SourceException(ErrorCode.SOURCE_TIMEOUT, "server state sentinel");
        var unavailable = plugin(properties(false, ""), client, definitions());
        assertThatThrownBy(() -> unavailable.confirmCalendar(ApiName.of("daily"), scope, () -> {
            throw fault;
        })).isSameAs(fault);
        var ready = plugin(properties(true, SECRET), client, definitions());
        var checks = new AtomicInteger();
        assertThatThrownBy(() -> ready.confirmCalendar(ApiName.of("hsgt_top10"), scope, () -> {
            if (checks.incrementAndGet() == 2) throw fault;
        })).isSameAs(fault);
        assertThat(checks).hasValue(2);
        verifyNoInteractions(client);
    }

    @Test
    void rejectsDuplicateDefinitionApiWithSafeMetadataCode() {
        var duplicated = new ArrayList<>(definitions());
        duplicated.set(1, duplicated.getFirst());
        assertThatThrownBy(() -> plugin(properties(true, SECRET), mock(TushareProClient.class), duplicated))
                .isInstanceOfSatisfying(TensorException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED);
                    assertThat(e.getMessage()).isEqualTo("Invalid Tushare download metadata");
                });
    }

    @Test
    void rejectsAlteredPolicyMapsBeforeRegistration() {
        var policies = new java.util.HashMap<>(new TusharePluginConfiguration().tushareDownloadPolicies());
        policies.remove(ApiName.of("daily"));
        assertThatThrownBy(() -> new TushareProPlugin(properties(true, SECRET), mock(TushareProClient.class), definitions(), policies))
                .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED));
        policies.put(ApiName.of("extra_api"), policies.get(ApiName.of("weekly")));
        assertThatThrownBy(() -> new TushareProPlugin(properties(true, SECRET), mock(TushareProClient.class), definitions(), policies))
                .isInstanceOf(TensorException.class);
    }

    private static TushareProPlugin plugin(
            TushareProperties properties, TushareProClient client, List<DatasetDefinition> definitions) {
        return new TushareProPlugin(properties, client, definitions, new TusharePluginConfiguration().tushareDownloadPolicies());
    }

    private static TushareProperties properties(boolean enabled, String token) {
        return new TushareProperties(
                enabled,
                URI.create("https://m07-t04.invalid"),
                new TushareProperties.Credential(token),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                1_024);
    }

    private static List<DatasetDefinition> definitions() {
        return new DatasetDefinitionLoader().loadAll(
                new PathMatchingResourcePatternResolver(),
                "classpath*:datasets/tushare_pro/*.yaml");
    }

    private static Method[] publicDeclaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                .toArray(Method[]::new);
    }

    private static void assertDescriptorReadiness(PluginDescriptor descriptor, PluginReadiness readiness) {
        assertThat(descriptor.enabled()).isEqualTo(readiness.enabled());
        assertThat(descriptor.credentialConfigured()).isEqualTo(readiness.credentialConfigured());
        assertThat(descriptor.downloadAvailable()).isEqualTo(readiness.downloadAvailable());
        assertThat(descriptor.unavailableReason()).isEqualTo(readiness.unavailableReason());
    }

    private static void assertLocalContext(
            Map<String, Object> properties,
            boolean enabled,
            boolean credentialConfigured,
            String unavailableReason) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("m07-t04", properties));
            context.register(TusharePluginConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(TushareProperties.class)).hasSize(1);
            assertThat(context.getBeansOfType(DataSourcePlugin.class)).hasSize(1);
            assertThat(context.getBeansOfType(TushareProPlugin.class)).hasSize(1);
            List<?> definitions = context.getBean("tushareDatasetDefinitions", List.class);
            assertThat(definitions).hasSize(49).allSatisfy(
                    definition -> assertThat(definition).isInstanceOf(DatasetDefinition.class));
            TushareProPlugin plugin = context.getBean(TushareProPlugin.class);
            assertThat(definitions).extracting(definition ->
                            ((DatasetDefinition) definition).datasetKey().apiName().value())
                    .containsExactlyElementsOf(API_NAMES);
            assertThat(plugin.descriptor().apis()).extracting(api -> api.apiName().value())
                    .containsExactlyElementsOf(API_NAMES);
            assertThat(plugin.descriptor().datasets()).extracting(key -> key.apiName().value())
                    .containsExactlyElementsOf(API_NAMES);
            assertThat(plugin.readiness()).isEqualTo(
                    new PluginReadiness(enabled, credentialConfigured, false, unavailableReason));
        }
    }
}
