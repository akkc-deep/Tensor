package com.akkc.tensor.plugin.tushare.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.BatchPlanning;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.download.SourceParameterMapper;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.tushare.TusharePluginConfiguration;
import com.akkc.tensor.plugin.tushare.TushareProPlugin;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class TushareCompleteBatchFetcherTest {
    private static final ApiName DAILY = ApiName.of("daily");
    private final List<DatasetDefinition> definitions = new DatasetDefinitionLoader().loadAll(
            new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml");
    private final DatasetDefinition daily = definitions.stream()
            .filter(definition -> definition.datasetKey().apiName().equals(DAILY)).findFirst().orElseThrow();
    @RegisterExtension
    private final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    @Test
    void successfulPlanningNeverBypassesFreshFetchOrItsCompletenessChecks() {
        for (String scenario : List.of("open", "unknown", "truncated", "later_page")) {
            var client = mock(TushareProClient.class); var api = dailyApi(client);
            var base = Map.<String,Object>of("trade_date", "20260903");
            var batch = new FetchBatch(base, api.downloadPolicy().recoveryPolicy());
            var failure = new SourceException(ErrorCode.SOURCE_TIMEOUT, "Controlled timeout");
            var events = new ArrayList<String>();
            TushareBatchSource delegate = scenario.equals("later_page")
                    ? source(Set.of("page_token"), c -> c == null ? Map.of() : Map.of("page_token", c), List.of(observation("p2", null)), ignored -> { throw new AssertionError("complete"); })
                    : source(Set.of(), c -> Map.of(), List.of(new TushareBatchSource.Observation(scenario.equals("unknown") ? TushareBatchSource.End.UNCONFIRMED : TushareBatchSource.End.TRUNCATED, null, null)), ignored -> { throw new AssertionError("complete"); });
            TushareBatchSource contract = new TushareBatchSource() {
                public BatchPlanning plan(DatasetDefinition definition, ApiDescriptor actual, FetchBatch exact) {
                    events.add("plan");
                    if (!exact.equals(batch)) throw TushareErrorClassifier.requestUnconfirmed();
                    return BatchPlanning.SINGLE_DATE;
                }
                public Session open(DatasetDefinition definition, ApiDescriptor actual, FetchBatch exact) {
                    events.add("open"); if (scenario.equals("open")) throw failure;
                    return delegate.open(definition, actual, exact);
                }
            };
            when(client.execute(any(), any())).thenAnswer(invocation -> {
                Map<String,Object> params = invocation.getArgument(1); events.add("client");
                if (params.containsKey("page_token")) throw failure;
                return page(params, List.of(row("000001.SZ")));
            });
            var fetcher = new TushareCompleteBatchFetcher(client, Map.of(DAILY, contract));
            assertThat(fetcher.plan(daily, api, batch, () -> {})).isEqualTo(BatchPlanning.SINGLE_DATE);
            assertThat(events).containsExactly("plan"); verifyNoInteractions(client);
            var thrown = catchThrowable(() -> fetcher.fetch(daily, api, batch, () -> {}));
            if (scenario.equals("open") || scenario.equals("later_page")) assertThat(thrown).isSameAs(failure);
            else assertThat(thrown).isInstanceOfSatisfying(SourceException.class, e -> assertThat(e.code()).isEqualTo(scenario.equals("unknown") ? ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED : ErrorCode.SOURCE_TRUNCATED));
            assertThat(events).containsExactlyElementsOf(scenario.equals("open") ? List.of("plan", "open") : scenario.equals("later_page") ? List.of("plan", "open", "client", "client") : List.of("plan", "open", "client"));
        }
    }

    @Test
    void planningRequestIdentityAndEvidenceGatesPrecedeTheRegisteredCallback() {
        var client = mock(TushareProClient.class);
        var plugin = new TushareProPlugin(properties(), client, definitions, new TusharePluginConfiguration().tushareDownloadPolicies());
        var callbacks = new AtomicInteger();
        var forbidden = new TushareBatchSource() {
            public BatchPlanning plan(DatasetDefinition definition, ApiDescriptor api, FetchBatch exact) { callbacks.incrementAndGet(); throw new AssertionError("plan"); }
            public Session open(DatasetDefinition definition, ApiDescriptor api, FetchBatch exact) { throw new AssertionError("open"); }
        };
        var registered = new HashMap<ApiName,TushareBatchSource>(); plugin.descriptor().apis().forEach(a -> registered.put(a.apiName(), forbidden));
        var fetcher = new TushareCompleteBatchFetcher(client, registered);
        for (var api : plugin.descriptor().apis()) {
            if (api.downloadPolicy().requestEvidenceStatus() == com.akkc.tensor.plugin.api.download.DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE && !api.apiName().value().equals("trade_cal")) continue;
            var definition = definitions.stream().filter(d -> d.datasetKey().apiName().equals(api.apiName())).findFirst().orElseThrow();
            var params = api.apiName().value().equals("trade_cal") ? Map.<String,Object>of("exchange", "BSE", "start_date", "20260901", "end_date", "20260902") : Map.<String,Object>of();
            assertThatThrownBy(() -> fetcher.plan(definition, api, new FetchBatch(params, api.downloadPolicy().recoveryPolicy()), () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED));
        }
        var api = dailyApi(client); var exact = new FetchBatch(Map.of("trade_date", "20260903"), api.downloadPolicy().recoveryPolicy());
        var otherApi = plugin.descriptor().apis().stream().filter(a -> !a.apiName().equals(DAILY)).findFirst().orElseThrow();
        assertThatThrownBy(() -> fetcher.plan(daily, otherApi, exact, () -> {})).isInstanceOfSatisfying(SourceException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED));
        var recovery = new com.akkc.tensor.plugin.api.download.RecoveryPolicy(com.akkc.tensor.plugin.api.download.RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "trade_date", RecoverySelector.TimeType.DATE, true, List.of("docs/test.md"));
        assertThatThrownBy(() -> fetcher.plan(daily, api, new FetchBatch(exact.sourceParams(), recovery), () -> {})).isInstanceOfSatisfying(SourceException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED));
        var checks = new AtomicInteger();
        assertThatThrownBy(() -> fetcher.plan(null, api, exact, checks::incrementAndGet)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> fetcher.plan(daily, null, exact, checks::incrementAndGet)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> fetcher.plan(daily, api, null, checks::incrementAndGet)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> fetcher.plan(daily, api, exact, null)).isInstanceOf(NullPointerException.class);
        assertThat(checks).hasValue(0); assertThat(callbacks).hasValue(0); verifyNoInteractions(client);
    }

    @Test
    void planningUsesExactContractsWithoutOpeningOrCallingClient() {
        var client = mock(TushareProClient.class);
        var api = dailyApi(client);
        var batch = new FetchBatch(Map.of("trade_date", "20260903"), api.downloadPolicy().recoveryPolicy());
        var events = new ArrayList<String>();
        TushareBatchSource source = new TushareBatchSource() {
            public BatchPlanning plan(DatasetDefinition definition, ApiDescriptor actual, FetchBatch exact) {
                assertThat(definition).isSameAs(daily); assertThat(actual).isEqualTo(api);
                events.add("plan");
                if (!exact.equals(batch)) throw TushareErrorClassifier.requestUnconfirmed();
                return BatchPlanning.SINGLE_DATE;
            }
            public Session open(DatasetDefinition definition, ApiDescriptor actual, FetchBatch exact) { throw new AssertionError("open"); }
        };
        var fetcher = new TushareCompleteBatchFetcher(client, Map.of(DAILY, source));
        for (int i = 0; i < 2; i++) assertThat(fetcher.plan(daily, api, batch, () -> events.add("check"))).isEqualTo(BatchPlanning.SINGLE_DATE);
        assertThat(events).containsExactly("check", "plan", "check", "check", "plan", "check");
        assertThatThrownBy(() -> fetcher.plan(daily, api, new FetchBatch(Map.of("trade_date", "20260904"), batch.recoveryPolicy()), () -> {}))
                .isInstanceOfSatisfying(SourceException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED));
        verifyNoInteractions(client);
    }

    @Test
    void planningDefaultsUnknownAdviceAndServerFailuresRefuseWithoutOpen() {
        var client = mock(TushareProClient.class);
        var api = dailyApi(client);
        var batch = new FetchBatch(Map.of("trade_date", "20260903"), api.downloadPolicy().recoveryPolicy());
        TushareBatchSource defaultSource = (definition, actual, exact) -> { throw new AssertionError("open"); };
        var sources = new ArrayList<TushareBatchSource>(); sources.add(defaultSource);
        for (BatchPlanning advice : java.util.Arrays.asList(null, BatchPlanning.UNCONFIRMED)) {
            sources.add(new TushareBatchSource() {
                public BatchPlanning plan(DatasetDefinition definition, ApiDescriptor actual, FetchBatch exact) { return advice; }
                public Session open(DatasetDefinition definition, ApiDescriptor actual, FetchBatch exact) { throw new AssertionError("open"); }
            });
        }
        for (var source : sources) {
            assertThatThrownBy(() -> new TushareCompleteBatchFetcher(client, Map.of(DAILY, source)).plan(daily, api, batch, () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED));
        }
        var fault = new IllegalStateException("server");
        for (int failAt : List.of(1, 2)) {
            var checks = new AtomicInteger(); var plans = new AtomicInteger();
            var source = new TushareBatchSource() {
                public BatchPlanning plan(DatasetDefinition definition, ApiDescriptor actual, FetchBatch exact) { plans.incrementAndGet(); return BatchPlanning.SINGLE_DATE; }
                public Session open(DatasetDefinition definition, ApiDescriptor actual, FetchBatch exact) { throw new AssertionError("open"); }
            };
            assertThatThrownBy(() -> new TushareCompleteBatchFetcher(client, Map.of(DAILY, source)).plan(daily, api, batch,
                    () -> { if (checks.incrementAndGet() == failAt) throw fault; })).isSameAs(fault);
            assertThat(plans).hasValue(failAt - 1);
        }
        verifyNoInteractions(client);
    }

    @Test
    void mergesTwoPagesInOrderAndRestoresTheOriginalBatchParameters() {
        TushareProClient client = mock(TushareProClient.class);
        Map<String, Object> base = Map.of("trade_date", "20260903");
        List<List<Object>> firstRows = List.of(
                java.util.Arrays.asList("000001.SZ", "20260903", new BigDecimal("0.1"),
                        new BigDecimal("12345.123456789012345678"), new BigDecimal("1e-18"),
                        new BigDecimal("1.2300"), 2, null, 0, 3, 4),
                List.of("000002.SZ", "20260903", 2, 3, 4, 5, 6, 7, 8, 9, 10));
        List<List<Object>> secondRows = List.of(
                List.of("000003.SZ", "20260903", 3, 4, 5, 6, 7, 8, 9, 10, 11));
        when(client.execute(daily, base)).thenReturn(page(base, firstRows));
        when(client.execute(daily, Map.of("trade_date", "20260903", "page_token", "p2")))
                .thenReturn(page(Map.of("trade_date", "20260903", "page_token", "p2"), secondRows));
        TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, Map.of(
                DAILY, scripted(List.of(
                        new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", 3L),
                        new TushareBatchSource.Observation(TushareBatchSource.End.COMPLETE, null, 3L)),
                        complete -> assertThat(complete.rowCount()).isEqualTo(3))));

        var result = fetcher.fetch(daily, dailyApi(client),
                new FetchBatch(base, dailyApi(client).downloadPolicy().recoveryPolicy()), () -> {});

        assertThat(result.envelope().params()).isEqualTo(base);
        assertThat(result.envelope().data()).containsExactlyElementsOf(
                java.util.stream.Stream.concat(firstRows.stream(), secondRows.stream()).toList());
        assertThat(result.envelope().data().getFirst().subList(2, 6)).containsExactly(
                new BigDecimal("0.1"), new BigDecimal("12345.123456789012345678"),
                new BigDecimal("1e-18"), new BigDecimal("1.2300"));
        assertThat(result.envelope().data().getFirst().get(7)).isNull();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void discardsTheFirstPageWhenTheSecondPageFailsAndStartsAgainOnManualRetry() {
        TushareProClient client = mock(TushareProClient.class);
        Map<String, Object> base = Map.of("trade_date", "20260903");
        SourceException failure = new SourceException(ErrorCode.SOURCE_RATE_LIMITED, "Tushare rate limit was reached");
        AtomicInteger firstPageCalls = new AtomicInteger();
        when(client.execute(any(), any())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            if (params.equals(base)) {
                firstPageCalls.incrementAndGet();
                return page(base, List.of(row("000001.SZ"), row("000002.SZ")));
            }
            throw failure;
        });
        TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, Map.of(
                DAILY, scripted(List.of(
                        new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", 3L),
                        new TushareBatchSource.Observation(TushareBatchSource.End.COMPLETE, null, 3L)), ignored -> {})));
        ApiDescriptor api = dailyApi(client);
        FetchBatch batch = new FetchBatch(base, api.downloadPolicy().recoveryPolicy());

        assertThatThrownBy(() -> fetcher.fetch(daily, api, batch, () -> {})).isSameAs(failure);
        assertThatThrownBy(() -> fetcher.fetch(daily, api, batch, () -> {})).isSameAs(failure);
        assertThat(firstPageCalls).hasValue(2);
    }

    @Test
    void acceptsExplicitlyCompleteSingleAndEmptyBatches() {
        for (List<List<Object>> rows : List.<List<List<Object>>>of(List.of(row("000001.SZ")), List.of())) {
            TushareProClient client = mock(TushareProClient.class);
            Map<String, Object> base = Map.of("trade_date", "20260903");
            when(client.execute(daily, base)).thenReturn(page(base, rows));
            long total = rows.size();
            TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of(), cursor -> Map.of(),
                    List.of(new TushareBatchSource.Observation(
                            TushareBatchSource.End.COMPLETE, null, total)), ignored -> {});

            assertThat(fetch(fetcher, client, base).envelope().data()).containsExactlyElementsOf(rows);
        }
    }

    @Test
    void rejectsUnknownAndTruncatedEndsWithoutReturningAnEnvelope() {
        for (var expected : Map.of(
                TushareBatchSource.End.TRUNCATED, ErrorCode.SOURCE_TRUNCATED,
                TushareBatchSource.End.UNCONFIRMED, ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED).entrySet()) {
            TushareProClient client = mock(TushareProClient.class);
            Map<String, Object> base = Map.of("trade_date", "20260903");
            when(client.execute(daily, base)).thenReturn(page(base, List.of()));
            TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of(), cursor -> Map.of(),
                    List.of(new TushareBatchSource.Observation(expected.getKey(), null, null)), ignored -> {});

            assertSource(fetcher, client, base, expected.getValue());
        }
        TushareProClient client = mock(TushareProClient.class);
        Map<String, Object> base = Map.of("trade_date", "20260903");
        when(client.execute(daily, base)).thenReturn(page(base, List.of()));
        assertSource(fetcher(client, Set.of(), cursor -> Map.of(), List.of(
                new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", null)), ignored -> {}),
                client, base, ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
    }

    @Test
    void observationRejectsEveryInvalidShapeWithASafePayloadFailure() {
        List<org.assertj.core.api.ThrowableAssert.ThrowingCallable> invalid = List.of(
                () -> new TushareBatchSource.Observation(null, null, null),
                () -> new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, null, null),
                () -> new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "", null),
                () -> new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "   ", null),
                () -> new TushareBatchSource.Observation(TushareBatchSource.End.COMPLETE, "secret-cursor", null),
                () -> new TushareBatchSource.Observation(TushareBatchSource.End.COMPLETE, null, -1L));
        for (var constructor : invalid) {
            assertThatThrownBy(constructor).isInstanceOfSatisfying(SourceException.class, failure -> {
                assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
                assertThat(failure.getCause()).isNull();
                assertThat(failure.getSuppressed()).isEmpty();
                assertThat(failure.getMessage()).doesNotContain("secret-cursor");
            });
        }
        assertThat(new TushareBatchSource.Observation(
                TushareBatchSource.End.CONTINUE, "secret-cursor", 0L).toString())
                .isEqualTo("Observation[REDACTED]");
    }

    @Test
    void rejectsReservedAndMalformedPaginationDeclarationsBeforeAnyPageCall() {
        for (Set<String> declared : List.of(Set.of("trade_date"), Set.of("ts_code"),
                Set.of("exchange"), Set.of("X"), Set.of("x"))) {
            TushareProClient client = mock(TushareProClient.class);
            AtomicInteger pageParameters = new AtomicInteger();
            TushareBatchSource source = source(declared, cursor -> {
                pageParameters.incrementAndGet();
                return Map.of();
            }, List.of(), ignored -> {});
            TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, Map.of(DAILY, source));

            assertSource(fetcher, client, Map.of("trade_date", "20260903"), ErrorCode.SOURCE_PAYLOAD_INVALID);
            assertThat(pageParameters).hasValue(0);
            verifyNoInteractions(client);
        }
    }

    @Test
    void rejectsNullUnknownBusinessAndNonStringPageParametersBeforeTheClient() {
        List<Map<String, Object>> invalid = new ArrayList<>();
        invalid.add(null);
        invalid.add(Map.of("trade_date", "20260903"));
        invalid.add(Map.of("offset", "1"));
        invalid.add(Map.of("page_token", 1));
        HashMap<String, Object> nullValue = new HashMap<>();
        nullValue.put("page_token", null);
        invalid.add(nullValue);
        for (Map<String, Object> pageParameters : invalid) {
            TushareProClient client = mock(TushareProClient.class);
            TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of("page_token"),
                    cursor -> pageParameters, List.of(), ignored -> {});

            assertSource(fetcher, client, Map.of("trade_date", "20260903"), ErrorCode.SOURCE_PAYLOAD_INVALID);
            verifyNoInteractions(client);
        }
    }

    @Test
    void rejectsCursorCyclesAndRepeatedActualRequestsBeforeResending() {
        assertCycle(List.of(
                new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", null),
                new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", null)),
                cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor), 2);
        assertCycle(List.of(
                new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", null),
                new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p3", null),
                new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", null)),
                cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor), 3);
        assertCycle(List.of(
                new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p2", null),
                new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, "p3", null)),
                cursor -> cursor == null ? Map.of() : Map.of("page_token", "fixed"), 2);
    }

    @Test
    void rejectsChangingExceededAndIncompleteTotalsButAllowsMissingLaterTotals() {
        List<List<TushareBatchSource.Observation>> invalid = List.of(
                List.of(observation("p2", 3L), complete(4L)),
                List.of(observation("p2", 1L), complete(1L)),
                List.of(complete(3L)));
        for (List<TushareBatchSource.Observation> observations : invalid) {
            TushareProClient client = twoPageClient();
            TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of("page_token"),
                    cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor), observations, ignored -> {});
            assertSource(fetcher, client, Map.of("trade_date", "20260903"), ErrorCode.SOURCE_PAYLOAD_INVALID);
        }

        TushareProClient client = twoPageClient();
        TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of("page_token"),
                cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor),
                List.of(observation("p2", 3L), complete(null)), ignored -> {});
        assertThat(fetch(fetcher, client, Map.of("trade_date", "20260903")).envelope().rowCount()).isEqualTo(3);

        for (List<TushareBatchSource.Observation> observations : List.of(
                List.of(observation("p2", null), complete(3L)),
                List.of(observation("p2", null), complete(null)))) {
            client = twoPageClient();
            fetcher = fetcher(client, Set.of("page_token"),
                    cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor),
                    observations, complete -> assertThat(complete.rowCount()).isEqualTo(3));
            assertThat(fetch(fetcher, client, Map.of("trade_date", "20260903")).envelope().rowCount())
                    .isEqualTo(3);
        }

        client = mock(TushareProClient.class);
        when(client.execute(any(), any())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            return page(params, params.containsKey("page_token")
                    ? List.of(row("000003.SZ"), row("000004.SZ"))
                    : List.of(row("000001.SZ"), row("000002.SZ")));
        });
        fetcher = fetcher(client, Set.of("page_token"),
                cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor),
                List.of(observation("p2", 3L), complete(3L)), ignored -> {});
        assertSource(fetcher, client, Map.of("trade_date", "20260903"), ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    @Test
    void enforcesEveryPageEnvelopeIdentityBeforeObservation() {
        Map<String, Object> base = Map.of("trade_date", "20260903");
        List<DownloadEnvelope> invalid = List.of(
                new DownloadEnvelope(com.akkc.tensor.plugin.api.model.PluginId.of("fixture"), DAILY, base,
                        fields(), 0, List.of(), DownloadStatus.SUCCESS, null),
                new DownloadEnvelope(daily.datasetKey().pluginId(), ApiName.of("weekly"), base,
                        fields(), 0, List.of(), DownloadStatus.SUCCESS, null),
                page(Map.of("trade_date", "20260904"), List.of()),
                new DownloadEnvelope(daily.datasetKey().pluginId(), DAILY, base,
                        fields().reversed(), 0, List.of(), DownloadStatus.SUCCESS, null),
                new DownloadEnvelope(daily.datasetKey().pluginId(), DAILY, base,
                        List.of(), 0, List.of(), DownloadStatus.FAILURE, "failed"));
        for (DownloadEnvelope envelope : invalid) {
            TushareProClient client = mock(TushareProClient.class);
            when(client.execute(daily, base)).thenReturn(envelope);
            AtomicInteger observes = new AtomicInteger();
            TushareBatchSource source = (definition, api, batch) -> new TushareBatchSource.Session() {
                public Set<String> paginationParameters() { return Set.of(); }
                public Map<String, Object> pageParameters(String cursor) { return Map.of(); }
                public TushareBatchSource.Observation observe(String cursor, DownloadEnvelope page) {
                    observes.incrementAndGet();
                    return complete(0L);
                }
                public void validateComplete(DownloadEnvelope complete) {}
            };
            TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, Map.of(DAILY, source));
            assertSource(fetcher, client, base, ErrorCode.SOURCE_PAYLOAD_INVALID);
            assertThat(observes).hasValue(0);
        }
    }

    @Test
    void checksServerAtTheInitialPageBoundariesAndAfterValidation() {
        List<String> events = new ArrayList<>();
        TushareProClient client = mock(TushareProClient.class);
        when(client.execute(any(), any())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            String cursor = (String) params.get("page_token");
            events.add("post:" + cursor);
            return page(params, cursor == null
                    ? List.of(row("000001.SZ"), row("000002.SZ")) : List.of(row("000003.SZ")));
        });
        TushareBatchSource source = (definition, api, batch) -> {
            events.add("open");
            return new TushareBatchSource.Session() {
                private int page;
                public Set<String> paginationParameters() {
                    events.add("declaration");
                    return Set.of("page_token");
                }
                public Map<String, Object> pageParameters(String cursor) {
                    events.add("pageParameters:" + cursor);
                    return cursor == null ? Map.of() : Map.of("page_token", cursor);
                }
                public TushareBatchSource.Observation observe(String cursor, DownloadEnvelope envelope) {
                    events.add("observe:" + cursor);
                    return page++ == 0 ? observation("p2", 3L) : complete(3L);
                }
                public void validateComplete(DownloadEnvelope complete) { events.add("validateComplete"); }
            };
        };
        TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, Map.of(DAILY, source));
        ApiDescriptor api = dailyApi(client);

        var result = fetcher.fetch(daily, api,
                new FetchBatch(Map.of("trade_date", "20260903"), api.downloadPolicy().recoveryPolicy()),
                () -> events.add("check"));

        assertThat(result.envelope().rowCount()).isEqualTo(3);
        assertThat(events).containsExactly(
                "check", "open", "declaration", "pageParameters:null", "check", "post:null", "check",
                "observe:null", "pageParameters:p2", "check", "post:p2", "check", "observe:p2",
                "validateComplete", "check");
    }

    @Test
    void preservesServerFaultIdentityAtInitialPostPageAndFinalChecks() {
        for (int failingCheck : List.of(1, 3, 6)) {
            TushareProClient client = mock(TushareProClient.class);
            SourceException fault = new SourceException(ErrorCode.SOURCE_TIMEOUT, "server state sentinel");
            AtomicInteger checks = new AtomicInteger();
            AtomicInteger validations = new AtomicInteger();
            AtomicInteger opens = new AtomicInteger();
            AtomicInteger declarations = new AtomicInteger();
            AtomicInteger pageParameters = new AtomicInteger();
            AtomicInteger clientCalls = new AtomicInteger();
            AtomicInteger observes = new AtomicInteger();
            when(client.execute(any(), any())).thenAnswer(invocation -> {
                clientCalls.incrementAndGet();
                Map<String, Object> params = invocation.getArgument(1);
                return page(params, params.containsKey("page_token")
                        ? List.of(row("000003.SZ")) : List.of(row("000001.SZ"), row("000002.SZ")));
            });
            TushareBatchSource counted = (definition, api, batch) -> {
                opens.incrementAndGet();
                return new TushareBatchSource.Session() {
                    private int page;
                    public Set<String> paginationParameters() {
                        declarations.incrementAndGet();
                        return Set.of("page_token");
                    }
                    public Map<String, Object> pageParameters(String cursor) {
                        pageParameters.incrementAndGet();
                        return cursor == null ? Map.of() : Map.of("page_token", cursor);
                    }
                    public TushareBatchSource.Observation observe(String cursor, DownloadEnvelope envelope) {
                        observes.incrementAndGet();
                        return page++ == 0 ? observation("p2", 3L) : complete(3L);
                    }
                    public void validateComplete(DownloadEnvelope complete) { validations.incrementAndGet(); }
                };
            };
            TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, Map.of(DAILY, counted));
            ApiDescriptor api = dailyApi(client);
            assertThatThrownBy(() -> fetcher.fetch(daily, api,
                    new FetchBatch(Map.of("trade_date", "20260903"), api.downloadPolicy().recoveryPolicy()), () -> {
                        if (checks.incrementAndGet() == failingCheck) throw fault;
                    })).isSameAs(fault);
            assertThat(opens.get()).isEqualTo(failingCheck == 1 ? 0 : 1);
            assertThat(declarations.get()).isEqualTo(failingCheck == 1 ? 0 : 1);
            assertThat(pageParameters.get()).isEqualTo(failingCheck == 1 ? 0 : failingCheck == 3 ? 1 : 2);
            assertThat(clientCalls.get()).isEqualTo(failingCheck == 1 ? 0 : failingCheck == 3 ? 1 : 2);
            assertThat(observes.get()).isEqualTo(failingCheck == 6 ? 2 : 0);
            assertThat(validations.get()).isEqualTo(failingCheck == 6 ? 1 : 0);
            assertThat(checks).hasValue(failingCheck);
        }
    }

    @Test
    void defensiveCopiesTheSourceRegistryAndPaginationDeclaration() {
        TushareProClient client = twoPageClient();
        Map<String, Object> base = Map.of("trade_date", "20260903");
        Set<String> declared = new java.util.HashSet<>(Set.of("page_token"));
        Map<ApiName, TushareBatchSource> registry = new HashMap<>();
        registry.put(DAILY, source(declared, cursor -> {
            if (cursor == null) {
                declared.clear();
                return Map.of();
            }
            return Map.of("page_token", cursor);
        }, List.of(observation("p2", 3L), complete(3L)), ignored -> {}));
        TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, registry);
        registry.clear();

        assertThat(fetch(fetcher, client, base).envelope().rowCount()).isEqualTo(3);
        assertThat(declared).isEmpty();
        Map<ApiName, TushareBatchSource> invalid = new HashMap<>();
        invalid.put(DAILY, null);
        assertThatThrownBy(() -> new TushareCompleteBatchFetcher(client, invalid))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Tushare batch sources");
        assertThatThrownBy(() -> new TushareCompleteBatchFetcher(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Tushare batch sources");
    }

    @Test
    void sendsOnlyVerifiedPageParametersThroughTheRealClientAndKeepsExactNumbers() throws Exception {
        String fields = String.join("\",\"", fields());
        wireMock.stubFor(post(urlEqualTo("/")).inScenario("two-pages").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                        "{\"code\":0,\"msg\":null,\"data\":{\"fields\":[\"" + fields
                                + "\"],\"items\":[[\"000001.SZ\",\"20260903\",0.1,12345.123456789012345678,1e-18,1.2300,2,null,0,3,4],[\"000002.SZ\",\"20260903\",2,3,4,5,6,7,8,9,10]]}}"))
                .willSetStateTo("second"));
        wireMock.stubFor(post(urlEqualTo("/")).inScenario("two-pages").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                        "{\"code\":0,\"msg\":null,\"data\":{\"fields\":[\"" + fields
                                + "\"],\"items\":[[\"000003.SZ\",\"20260903\",3,4,5,6,7,8,9,10,11]]}}")));
        TushareProClient client = realClient();
        TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of("page_token"),
                cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor),
                List.of(observation("p2", 3L), complete(3L)), ignored -> {});

        var result = fetch(fetcher, client, Map.of("trade_date", "20260903"));

        assertThat(result.envelope().rowCount()).isEqualTo(3);
        assertThat(result.envelope().data().getFirst().subList(2, 6)).containsExactly(
                new BigDecimal("0.1"), new BigDecimal("12345.123456789012345678"),
                new BigDecimal("1e-18"), new BigDecimal("1.2300"));
        assertThat(wireMock.getAllServeEvents()).hasSize(2);
        List<Map<String, Object>> sent = new ArrayList<>();
        for (var event : wireMock.getAllServeEvents()) {
            sent.add(new ObjectMapper().readValue(event.getRequest().getBodyAsString(),
                    new TypeReference<Map<String, Object>>() {}));
        }
        assertThat(sent).extracting(request -> request.get("params")).containsExactlyInAnyOrder(
                Map.of("trade_date", "20260903"),
                Map.of("trade_date", "20260903", "page_token", "p2"));
    }

    @Test
    void laterHttpFailuresFromTheRealClientAbortTheWholeBatchWithoutRetry() {
        for (int status : List.of(429, 503)) {
            wireMock.resetAll();
            wireMock.stubFor(post(urlEqualTo("/")).inScenario("failure").whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                            .withBody(successJson(List.of(row("000001.SZ"), row("000002.SZ")))))
                    .willSetStateTo("second"));
            wireMock.stubFor(post(urlEqualTo("/")).inScenario("failure").whenScenarioStateIs("second")
                    .willReturn(aResponse().withStatus(status).withBody("secret-body")));
            TushareProClient client = realClient();
            TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of("page_token"),
                    cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor),
                    List.of(observation("p2", 3L), complete(3L)), ignored -> {});

            assertSource(fetcher, client, Map.of("trade_date", "20260903"),
                    status == 429 ? ErrorCode.SOURCE_RATE_LIMITED : ErrorCode.SOURCE_UNAVAILABLE);
            assertThat(wireMock.getAllServeEvents()).hasSize(2);
        }
    }

    @Test
    void laterMalformedAndBusinessResponsesFromTheRealClientAbortWithoutAThirdRequest() {
        String joinedFields = String.join("\",\"", fields());
        Map<String, ErrorCode> failures = Map.of(
                "{", ErrorCode.SOURCE_PAYLOAD_INVALID,
                "{\"code\":0,\"msg\":null,\"data\":{\"items\":[]}}", ErrorCode.SOURCE_PAYLOAD_INVALID,
                "{\"code\":0,\"msg\":null,\"data\":{\"fields\":[\"" + joinedFields
                        + "\"],\"items\":[[\"wrong-width\"]]}}", ErrorCode.SOURCE_PAYLOAD_INVALID,
                "{\"code\":2002,\"msg\":\"权限不足 secret-body\",\"data\":null}",
                ErrorCode.SOURCE_PERMISSION_DENIED);
        for (var failure : failures.entrySet()) {
            wireMock.resetAll();
            wireMock.stubFor(post(urlEqualTo("/")).inScenario("protocol-failure").whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                            .withBody(successJson(List.of(row("000001.SZ"), row("000002.SZ")))))
                    .willSetStateTo("second"));
            wireMock.stubFor(post(urlEqualTo("/")).inScenario("protocol-failure").whenScenarioStateIs("second")
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                            .withBody(failure.getKey())));
            TushareProClient client = realClient();
            TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of("page_token"),
                    cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor),
                    List.of(observation("p2", 3L), complete(3L)), ignored -> {});

            assertSource(fetcher, client, Map.of("trade_date", "20260903"), failure.getValue());
            assertThat(wireMock.getAllServeEvents()).hasSize(2);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void passesEveryMappedT05SourceShapeThroughWithoutChangingIt() {
        record Case(String apiName, Map<String, Object> common, RecoverySelector selector,
                    Map<String, Object> expected) {}
        List<Case> cases = List.of(
                new Case("daily", Map.of(), selector(RecoverySelector.TimeType.DATE, "2026-09-03"),
                        Map.of("trade_date", "20260903")),
                new Case("income", Map.of("ts_code", "000001.SZ"),
                        selector(RecoverySelector.TimeType.RANGE, "2026-09-03/2026-09-07"),
                        Map.of("ts_code", "000001.SZ", "start_date", "20260903", "end_date", "20260907")),
                new Case("margin", Map.of("exchange_id", "SSE"),
                        selector(RecoverySelector.TimeType.DATE, "2026-09-03"),
                        Map.of("exchange_id", "SSE", "start_date", "20260903", "end_date", "20260903")),
                new Case("broker_recommend", Map.of(),
                        selector(RecoverySelector.TimeType.MONTH, "2026-02"), Map.of("month", "202602")),
                new Case("stock_basic", Map.of("list_status", "L"),
                        selector(RecoverySelector.TimeType.NONE, ""), Map.of("list_status", "L")),
                new Case("index_classify", Map.of(), selector(RecoverySelector.TimeType.NONE, ""), Map.of()),
                new Case("new_share", Map.of(), selector(RecoverySelector.TimeType.DATE, "2026-09-03"),
                        Map.of("start_date", "20260903", "end_date", "20260903")),
                new Case("trade_cal", Map.of("exchange", "SSE"),
                        selector(RecoverySelector.TimeType.DATE, "2026-09-03"),
                        Map.of("exchange", "SSE", "start_date", "20260903", "end_date", "20260903")),
                new Case("namechange", Map.of(),
                        selector(RecoverySelector.TimeType.DATE, "2026-09-03"),
                        Map.of("start_date", "20260903", "end_date", "20260903")));
        TushareProClient client = realClient();
        Map<ApiName, TushareBatchSource> sources = new HashMap<>();
        cases.forEach(item -> sources.put(ApiName.of(item.apiName()),
                source(Set.of(), cursor -> Map.of(), List.of(complete(0L)), ignored -> {})));
        TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, sources);
        TushareProPlugin plugin = new TushareProPlugin(properties(), client, definitions,
                new TusharePluginConfiguration().tushareDownloadPolicies());

        for (Case item : cases) {
            ApiDescriptor api = plugin.descriptor().apis().stream()
                    .filter(candidate -> candidate.apiName().value().equals(item.apiName())).findFirst().orElseThrow();
            DatasetDefinition definition = definitions.stream()
                    .filter(candidate -> candidate.datasetKey().apiName().equals(api.apiName())).findFirst().orElseThrow();
            wireMock.stubFor(post(urlEqualTo("/")).withRequestBody(
                            matchingJsonPath("$.api_name", equalTo(item.apiName())))
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                            .withBody(successJson(definition, List.of()))));
            Map<String, Object> mapped = SourceParameterMapper.map(api, item.common(), item.selector()).values();
            assertThat(mapped).isEqualTo(item.expected());
            var result = fetcher.fetch(definition, api,
                    new FetchBatch(mapped, api.downloadPolicy().recoveryPolicy()), () -> {});
            assertThat(result.envelope().params()).isEqualTo(item.expected());
        }
        Map<String, Map<String, Object>> actual = new HashMap<>();
        for (var event : wireMock.getAllServeEvents()) {
            Map<String, Object> request = readRequest(event.getRequest().getBodyAsString());
            actual.put((String) request.get("api_name"), (Map<String, Object>) request.get("params"));
        }
        assertThat(actual).hasSize(cases.size());
        for (Case item : cases) assertThat(actual.get(item.apiName())).isEqualTo(item.expected());
    }

    @Test
    void aControlledDailyContractRejectsWrongAndUnparseableDatesAsWholeBatchFailures() {
        for (String tradeDate : List.of("20260904", "not-a-date")) {
            TushareProClient client = mock(TushareProClient.class);
            Map<String, Object> base = Map.of("trade_date", "20260903");
            List<Object> bad = new ArrayList<>(row("000001.SZ"));
            bad.set(1, tradeDate);
            when(client.execute(daily, base)).thenReturn(page(base, List.of(bad)));
            TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of(), cursor -> Map.of(),
                    List.of(complete(1L)), complete -> {
                        try {
                            LocalDate date = LocalDate.parse((String) complete.data().getFirst().get(1),
                                    DateTimeFormatter.BASIC_ISO_DATE);
                            if (!date.equals(LocalDate.of(2026, 9, 3))) throw TushareErrorClassifier.invalidPayload();
                        } catch (RuntimeException failure) {
                            if (failure instanceof SourceException) throw failure;
                            throw TushareErrorClassifier.invalidPayload();
                        }
                    });
            assertSource(fetcher, client, base, ErrorCode.SOURCE_PAYLOAD_INVALID);
        }
    }

    @Test
    void aRealClientTimeoutOnTheSecondPageAbortsWithoutRetry() {
        wireMock.stubFor(post(urlEqualTo("/")).inScenario("timeout").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(successJson(List.of(row("000001.SZ"), row("000002.SZ")))))
                .willSetStateTo("second"));
        wireMock.stubFor(post(urlEqualTo("/")).inScenario("timeout").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withFixedDelay(300)
                        .withHeader("Content-Type", "application/json").withBody(successJson(List.of(row("000003.SZ"))))));
        TushareProperties shortTimeout = new TushareProperties(true, URI.create(wireMock.baseUrl()),
                new TushareProperties.Credential("secret"), Duration.ofSeconds(1), Duration.ofMillis(50), 1_024);
        TushareProClient client = new TushareProClient(
                new TushareRestClientFactory().create(shortTimeout), shortTimeout);
        TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of("page_token"),
                cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor),
                List.of(observation("p2", 3L), complete(3L)), ignored -> {});

        assertSource(fetcher, client, Map.of("trade_date", "20260903"), ErrorCode.SOURCE_TIMEOUT);
        assertThat(wireMock.getAllServeEvents()).hasSize(2);
    }

    @Test
    void batchFailureLogsOmitTokenUrlBodyAndCursorSentinels() {
        String token = "batch-token-sentinel";
        String cursor = "batch-cursor-sentinel";
        String body = "batch-body-sentinel https://batch-url-sentinel.invalid";
        wireMock.stubFor(post(urlEqualTo("/")).inScenario("safe-logs").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(successJson(List.of(row("000001.SZ"), row("000002.SZ")))))
                .willSetStateTo("second"));
        wireMock.stubFor(post(urlEqualTo("/")).inScenario("safe-logs").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503).withBody(body)));
        TushareProperties properties = new TushareProperties(true, URI.create(wireMock.baseUrl()),
                new TushareProperties.Credential(token), Duration.ofSeconds(1), Duration.ofSeconds(2),
                1_024 * 1_024);
        TushareProClient client = new TushareProClient(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(), properties);
        TushareCompleteBatchFetcher fetcher = fetcher(client, Set.of("page_token"),
                pageCursor -> pageCursor == null ? Map.of() : Map.of("page_token", pageCursor),
                List.of(observation(cursor, 3L), complete(3L)), ignored -> {});

        List<String> logs = new ArrayList<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        Handler capture = new Handler() {
            public void publish(LogRecord record) { logs.add(record.getMessage()); }
            public void flush() {}
            public void close() {}
        };
        Logger root = Logger.getLogger("");
        root.addHandler(capture);
        Throwable failure;
        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                PrintStream capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            root.info("batch-jul-capture-probe");
            System.out.print("batch-stdout-capture-probe");
            System.err.print("batch-stderr-capture-probe");
            failure = catchThrowable(() -> fetch(fetcher, client, Map.of("trade_date", "20260903")));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            root.removeHandler(capture);
        }

        assertThat(failure).isInstanceOfSatisfying(SourceException.class, sourceFailure -> {
            assertThat(sourceFailure.code()).isEqualTo(ErrorCode.SOURCE_UNAVAILABLE);
            assertThat(sourceFailure.getMessage()).isEqualTo("Tushare service is unavailable");
            assertThat(sourceFailure.getCause()).isNull();
            assertThat(sourceFailure.getSuppressed()).isEmpty();
        });
        assertThat(String.valueOf(failure)).doesNotContain(token, cursor, body, "batch-url-sentinel");
        String capturedOutput = String.join("\n", logs)
                + stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        assertThat(capturedOutput)
                .contains("batch-jul-capture-probe", "batch-stdout-capture-probe", "batch-stderr-capture-probe")
                .doesNotContain(token, cursor, body, "batch-url-sentinel");
        assertThat(wireMock.getAllServeEvents()).hasSize(2);
    }

    @Test
    void aControlledTradeCalendarContractRequiresEveryDateAndTheExactMarket() {
        ApiName name = ApiName.of("trade_cal");
        DatasetDefinition definition = definitions.stream()
                .filter(candidate -> candidate.datasetKey().apiName().equals(name)).findFirst().orElseThrow();
        TushareProClient descriptorClient = mock(TushareProClient.class);
        ApiDescriptor api = new TushareProPlugin(properties(), descriptorClient, definitions,
                new TusharePluginConfiguration().tushareDownloadPolicies()).descriptor().apis().stream()
                .filter(candidate -> candidate.apiName().equals(name)).findFirst().orElseThrow();
        Map<String, Object> params = Map.of(
                "exchange", "SSE", "start_date", "20260904", "end_date", "20260906");
        List<List<List<Object>>> invalidRows = List.of(
                List.of(List.of("SSE", "20260904", 1, "20260903"),
                        List.of("SSE", "20260906", 0, "20260904")),
                List.of(List.of("SSE", "20260904", 1, "20260903"),
                        List.of("SSE", "20260904", 1, "20260903"),
                        List.of("SSE", "20260906", 0, "20260904")),
                List.of(List.of("SZSE", "20260904", 1, "20260903"),
                        List.of("SSE", "20260905", 0, "20260904"),
                        List.of("SSE", "20260906", 0, "20260904")),
                List.of(List.of("SSE", "20260904", 1, "20260903"),
                        List.of("SSE", "20260905", 0, "20260904"),
                        List.of("SSE", "20260907", 0, "20260904")));
        for (List<List<Object>> rows : invalidRows) {
            TushareProClient client = mock(TushareProClient.class);
            when(client.execute(definition, params)).thenReturn(new DownloadEnvelope(
                    definition.datasetKey().pluginId(), name, params,
                    definition.columns().stream().map(column -> column.name()).toList(), rows.size(), rows,
                    DownloadStatus.SUCCESS, null));
            TushareBatchSource source = source(Set.of(), cursor -> Map.of(), List.of(complete((long) rows.size())),
                    complete -> validateTradeCalendar(complete, "SSE", Set.of(
                            "20260904", "20260905", "20260906")));
            TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, Map.of(name, source));

            assertThatThrownBy(() -> fetcher.fetch(definition, api,
                    new FetchBatch(params, api.downloadPolicy().recoveryPolicy()), () -> {}))
                    .isInstanceOfSatisfying(SourceException.class,
                            failure -> {
                                assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
                                assertThat(failure.getCause()).isNull();
                                assertThat(failure.getSuppressed()).isEmpty();
                            });
        }
    }

    @Test
    void aControlledTradeCalendarContractAcceptsThreeDaysIncludingClosedDays() {
        ApiName name = ApiName.of("trade_cal");
        DatasetDefinition definition = definitions.stream()
                .filter(candidate -> candidate.datasetKey().apiName().equals(name)).findFirst().orElseThrow();
        TushareProClient client = mock(TushareProClient.class);
        ApiDescriptor api = new TushareProPlugin(properties(), client, definitions,
                new TusharePluginConfiguration().tushareDownloadPolicies()).descriptor().apis().stream()
                .filter(candidate -> candidate.apiName().equals(name)).findFirst().orElseThrow();
        Map<String, Object> params = Map.of(
                "exchange", "SSE", "start_date", "20260904", "end_date", "20260906");
        List<List<Object>> rows = List.of(
                List.of("SSE", "20260904", 1, "20260903"),
                List.of("SSE", "20260905", 0, "20260904"),
                List.of("SSE", "20260906", 0, "20260904"));
        when(client.execute(definition, params)).thenReturn(new DownloadEnvelope(
                definition.datasetKey().pluginId(), name, params,
                definition.columns().stream().map(column -> column.name()).toList(), rows.size(), rows,
                DownloadStatus.SUCCESS, null));
        TushareBatchSource source = source(Set.of(), cursor -> Map.of(), List.of(complete(3L)),
                complete -> validateTradeCalendar(complete, "SSE", Set.of(
                        "20260904", "20260905", "20260906")));

        var result = new TushareCompleteBatchFetcher(client, Map.of(name, source)).fetch(
                definition, api, new FetchBatch(params, api.downloadPolicy().recoveryPolicy()), () -> {});

        assertThat(result.envelope().data()).containsExactlyElementsOf(rows);
        assertThat(result.envelope().data()).extracting(row -> row.get(2)).containsExactly(1, 0, 0);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void nullSourceSessionDeclarationsPagesAndObservationsArePayloadFailures() {
        TushareProClient client = mock(TushareProClient.class);
        Map<String, Object> base = Map.of("trade_date", "20260903");
        when(client.execute(daily, base)).thenReturn(page(base, List.of()));
        List<TushareBatchSource> invalid = List.of(
                (definition, api, batch) -> null,
                (definition, api, batch) -> source(null, cursor -> Map.of(), List.of(), ignored -> {})
                        .open(definition, api, batch),
                (definition, api, batch) -> source(Set.of(), cursor -> null, List.of(), ignored -> {})
                        .open(definition, api, batch),
                (definition, api, batch) -> source(Set.of(), cursor -> Map.of(),
                        java.util.Arrays.asList((TushareBatchSource.Observation) null), ignored -> {})
                        .open(definition, api, batch));
        for (TushareBatchSource source : invalid) {
            assertSource(new TushareCompleteBatchFetcher(client, Map.of(DAILY, source)), client, base,
                    ErrorCode.SOURCE_PAYLOAD_INVALID);
        }
    }

    @Test
    void requestAndBseGatesRunBeforeOpeningAControlledRegisteredSource() {
        TushareProClient client = mock(TushareProClient.class);
        AtomicInteger opens = new AtomicInteger();
        TushareBatchSource mustStayClosed = (definition, api, batch) -> {
            opens.incrementAndGet();
            throw new AssertionError("source must remain closed");
        };
        List<String> names = List.of("express", "forecast", "trade_cal");
        Map<ApiName, TushareBatchSource> sources = new HashMap<>();
        names.forEach(name -> sources.put(ApiName.of(name), mustStayClosed));
        TushareCompleteBatchFetcher fetcher = new TushareCompleteBatchFetcher(client, sources);
        TushareProPlugin plugin = new TushareProPlugin(properties(), client, definitions,
                new TusharePluginConfiguration().tushareDownloadPolicies());
        for (String name : names) {
            ApiDescriptor api = plugin.descriptor().apis().stream()
                    .filter(candidate -> candidate.apiName().value().equals(name)).findFirst().orElseThrow();
            DatasetDefinition definition = definitions.stream()
                    .filter(candidate -> candidate.datasetKey().apiName().equals(api.apiName())).findFirst().orElseThrow();
            Map<String, Object> params = name.equals("trade_cal")
                    ? Map.of("exchange", "BSE", "start_date", "20260903", "end_date", "20260904")
                    : Map.of();
            assertThatThrownBy(() -> fetcher.fetch(definition, api,
                    new FetchBatch(params, api.downloadPolicy().recoveryPolicy()), () -> {}))
                    .isInstanceOfSatisfying(SourceException.class, failure ->
                            assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED));
        }
        assertThat(opens).hasValue(0);
        verifyNoInteractions(client);
    }

    private ApiDescriptor dailyApi(TushareProClient client) {
        return new TushareProPlugin(properties(), client, definitions,
                new TusharePluginConfiguration().tushareDownloadPolicies())
                .descriptor().apis().stream().filter(api -> api.apiName().equals(DAILY)).findFirst().orElseThrow();
    }

    private TushareBatchSource scripted(
            List<TushareBatchSource.Observation> observations, Consumer<DownloadEnvelope> validator) {
        return source(Set.of("page_token"),
                cursor -> cursor == null ? Map.of() : Map.of("page_token", cursor), observations, validator);
    }

    private TushareCompleteBatchFetcher fetcher(TushareProClient client, Set<String> declared,
            Function<String, Map<String, Object>> pageParameters,
            List<TushareBatchSource.Observation> observations, Consumer<DownloadEnvelope> validator) {
        return new TushareCompleteBatchFetcher(client,
                Map.of(DAILY, source(declared, pageParameters, observations, validator)));
    }

    private TushareBatchSource source(Set<String> declared,
            Function<String, Map<String, Object>> pageParameters,
            List<TushareBatchSource.Observation> observations, Consumer<DownloadEnvelope> validator) {
        return (definition, api, batch) -> new TushareBatchSource.Session() {
            private int index;
            public Set<String> paginationParameters() { return declared; }
            public Map<String, Object> pageParameters(String cursor) { return pageParameters.apply(cursor); }
            public TushareBatchSource.Observation observe(String cursor, DownloadEnvelope page) {
                return observations.get(index++);
            }
            public void validateComplete(DownloadEnvelope complete) { validator.accept(complete); }
            @Override public String toString() { return "ScriptedSession[REDACTED]"; }
        };
    }

    private com.akkc.tensor.plugin.api.download.FetchResult fetch(
            TushareCompleteBatchFetcher fetcher, TushareProClient client, Map<String, Object> params) {
        ApiDescriptor api = dailyApi(client);
        return fetcher.fetch(daily, api, new FetchBatch(params, api.downloadPolicy().recoveryPolicy()), () -> {});
    }

    private void assertSource(TushareCompleteBatchFetcher fetcher, TushareProClient client,
            Map<String, Object> params, ErrorCode code) {
        assertThatThrownBy(() -> fetch(fetcher, client, params))
                .isInstanceOfSatisfying(SourceException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(code);
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getSuppressed()).isEmpty();
                    assertThat(failure.getMessage()).doesNotContain(
                            "secret", "cursor", "20260903", "https://");
                });
    }

    private void assertCycle(List<TushareBatchSource.Observation> observations,
            Function<String, Map<String, Object>> pageParameters, int expectedCalls) {
        TushareProClient client = mock(TushareProClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(client.execute(any(), any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return page(invocation.getArgument(1), List.of());
        });
        TushareCompleteBatchFetcher fetcher = fetcher(
                client, Set.of("page_token"), pageParameters, observations, ignored -> {});
        assertSource(fetcher, client, Map.of("trade_date", "20260903"), ErrorCode.SOURCE_PAYLOAD_INVALID);
        assertThat(calls).hasValue(expectedCalls);
    }

    private TushareProClient twoPageClient() {
        TushareProClient client = mock(TushareProClient.class);
        when(client.execute(any(), any())).thenAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(1);
            return page(params, params.containsKey("page_token")
                    ? List.of(row("000003.SZ")) : List.of(row("000001.SZ"), row("000002.SZ")));
        });
        return client;
    }

    private static TushareBatchSource.Observation observation(String cursor, Long total) {
        return new TushareBatchSource.Observation(TushareBatchSource.End.CONTINUE, cursor, total);
    }

    private static TushareBatchSource.Observation complete(Long total) {
        return new TushareBatchSource.Observation(TushareBatchSource.End.COMPLETE, null, total);
    }

    private List<String> fields() {
        return daily.columns().stream().map(column -> column.name()).toList();
    }

    private DownloadEnvelope page(Map<String, Object> params, List<List<Object>> rows) {
        return new DownloadEnvelope(daily.datasetKey().pluginId(), DAILY, params,
                fields(), rows.size(), rows,
                DownloadStatus.SUCCESS, null);
    }

    private static List<Object> row(String code) {
        return List.of(code, "20260903", 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    private static TushareProperties properties() {
        return new TushareProperties(true, URI.create("https://m07-t07.invalid"),
                new TushareProperties.Credential("secret"), Duration.ofSeconds(1), Duration.ofSeconds(2), 1_024);
    }

    private TushareProClient realClient() {
        TushareProperties properties = new TushareProperties(true, URI.create(wireMock.baseUrl()),
                new TushareProperties.Credential("secret"), Duration.ofSeconds(1), Duration.ofSeconds(2),
                1_024 * 1_024);
        return new TushareProClient(RestClient.builder().baseUrl(wireMock.baseUrl()).build(), properties);
    }

    private String successJson(List<List<Object>> rows) {
        return successJson(daily, rows);
    }

    private String successJson(DatasetDefinition definition, List<List<Object>> rows) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                    "code", 0, "msg", "", "data", Map.of(
                            "fields", definition.columns().stream().map(column -> column.name()).toList(),
                            "items", rows)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private Map<String, Object> readRequest(String body) {
        try {
            return new ObjectMapper().readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void validateTradeCalendar(
            DownloadEnvelope complete, String exchange, Set<String> expectedDates) {
        Set<String> actualDates = new java.util.HashSet<>();
        for (List<Object> row : complete.data()) {
            if (!exchange.equals(row.getFirst()) || !(row.get(1) instanceof String date)
                    || !expectedDates.contains(date) || !actualDates.add(date)) {
                throw TushareErrorClassifier.invalidPayload();
            }
        }
        if (!actualDates.equals(expectedDates)) throw TushareErrorClassifier.invalidPayload();
    }

    private static RecoverySelector selector(RecoverySelector.TimeType timeType, String timeValue) {
        return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", timeType, timeValue);
    }
}
