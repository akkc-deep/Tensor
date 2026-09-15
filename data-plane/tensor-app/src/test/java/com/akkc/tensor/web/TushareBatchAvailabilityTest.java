package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskJson;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.Availability;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.CompletenessRule.Kind;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.DateAxis;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.PlanningMode;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.tushare.TusharePluginConfiguration;
import com.akkc.tensor.plugin.tushare.TushareProPlugin;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TushareBatchAvailabilityTest {
    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final List<DatasetDefinition> DEFINITIONS =
            new TusharePluginConfiguration().tushareDatasetDefinitions();
    private static final Set<String> SINGLE_ONLY = Set.of(
            "pledge_stat", "stk_rewards", "stock_basic", "stock_company", "index_classify", "index_member_all");
    private static final Set<String> WITHDRAWN = Set.of("fina_indicator", "balancesheet", "cashflow", "repurchase");
    private static final Map<String, RangeExpectation> RANGE = rangeExpectations();
    private static final Map<String, String> VALUES = Map.of(
            "ts_code", "000001.SZ", "trade_date", "20260903", "ann_date", "20260903",
            "exchange", "SSE", "exchange_id", "SSE", "start_date", "20260901",
            "end_date", "20260903", "list_status", "L");

    @ParameterizedTest
    @CsvSource({"fina_indicator,20250331,20251231", "balancesheet,20240101,20241231",
            "cashflow,20240101,20241231", "repurchase,20260801,20260831"})
    void failedRangesAreWithdrawnBeforeQueueOrSourceAccess(String apiName, String start, String end) {
        var h = harness();
        var key = DEFINITIONS.stream().map(DatasetDefinition::datasetKey)
                .filter(k -> k.apiName().value().equals(apiName)).findFirst().orElseThrow();
        var capabilities = h.service().capabilities(key);
        assertThat(capabilities.single().available()).isTrue();
        assertThat(capabilities.range().availability()).isEqualTo(Availability.NEEDS_VERIFICATION);
        assertThat(capabilities.range().policyVersion()).isEqualTo("tushare-range-v3");
        assertThat(capabilities.range().completenessRule().kind()).isEqualTo(Kind.UNKNOWN);
        assertThatThrownBy(() -> h.service().submit(new DownloadTaskService.Submission(
                UUID.randomUUID(), key, DownloadMode.RANGE,
                apiName.equals("repurchase") ? Map.of("start_date", start, "end_date", end)
                        : Map.of("ts_code", "000001.SZ", "start_date", start, "end_date", end))))
                .isInstanceOfSatisfying(TensorException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE));
        verify(h.repository(), never()).queuedCount();
        verify(h.repository(), never()).insert(any());
        h.upstream().verify();
    }

    @Test
    void exposesCandidatesAndWithdrawnRanges() {
        var h = harness();
        var candidates = new ArrayList<String>();
        for (var definition : DEFINITIONS) {
            var key = definition.datasetKey();
            var capability = h.service().capabilities(key);
            assertThat(capability.single().available()).isTrue();
            if (SINGLE_ONLY.contains(key.apiName().value())) {
                assertThat(capability.range().availability()).isEqualTo(Availability.UNSUPPORTED);
                assertThat(capability.range().parameters()).isEmpty();
            } else {
                String name = key.apiName().value(); var expected = RANGE.get(name); var range = capability.range();
                assertThat(expected).as(name).isNotNull();
                boolean withdrawn = WITHDRAWN.contains(name);
                assertThat(range.availability()).as(name).isEqualTo(withdrawn ? Availability.NEEDS_VERIFICATION : Availability.AVAILABLE);
                assertThat(range.policyVersion()).as(name).isEqualTo(withdrawn ? "tushare-range-v3" : "tushare-range-v2");
                assertThat(range.dateAxis()).as(name).isEqualTo(expected.dateAxis());
                assertThat(range.planningMode()).as(name).isEqualTo(expected.planningMode());
                assertThat(range.splittable()).as(name).isEqualTo(expected.splittable());
                assertThat(range.completenessRule().kind()).as(name).isEqualTo(withdrawn ? Kind.UNKNOWN : expected.ruleKind());
                assertThat(range.completenessRule().rowLimit()).as(name).isEqualTo(withdrawn ? null : expected.rowLimit());
                if (withdrawn) {
                    assertThat(range.completenessRule().evidence()).isNull();
                    assertThat(range.unavailableReason()).isNotBlank();
                } else {
                    assertThat(range.completenessRule().evidence()).as(name).isNotBlank();
                    assertThat(range.unavailableReason()).as(name).isNull();
                }
                assertThat(range.parameters()).extracting(p -> p.name()).containsExactlyElementsOf(expected.parameters());
                candidates.add(name);
            }
        }
        assertThat(candidates).hasSize(34).containsExactlyInAnyOrderElementsOf(RANGE.keySet()).doesNotHaveDuplicates();
        assertThat(RANGE.values().stream().filter(v -> v.ruleKind() == Kind.CONFIRMED_ROW_LIMIT)).hasSize(22);
        assertThat(RANGE.values().stream().filter(v -> v.ruleKind() == Kind.RESPONSE_ONLY)).hasSize(11);
        assertThat(RANGE.values().stream().filter(v -> v.ruleKind() == Kind.VERIFIED_RULE)).hasSize(1);
        assertThat(h.plugin().descriptor().apis()).hasSize(40);
        verify(h.repository(), never()).insert(any());
        verify(h.repository(), never()).queuedCount();
        h.upstream().verify(); // Any HTTP would fail: no expectations are registered.
    }

    @Test
    void acceptsThirtyCandidatesAsQueuedWithoutCallingUpstream() {
        var h = harness();
        queuedInserts(h);
        var accepted = new ArrayList<String>();
        for (var definition : DEFINITIONS) {
            String apiName = definition.datasetKey().apiName().value();
            if (!RANGE.containsKey(apiName) || WITHDRAWN.contains(apiName)) continue;
            var range = h.service().capabilities(definition.datasetKey()).range();
            var variants = apiName.equals("trade_cal") ? List.of("SSE", "SZSE")
                    : apiName.equals("margin") ? List.of("SSE", "SZSE", "BSE")
                    : apiName.equals("top_list") ? List.of("SSE", "SZSE", "BJ") : List.of("SZSE");
            for (String variant : variants) {
                var params = new LinkedHashMap<String, Object>();
                range.parameters().forEach(p -> params.put(p.name(), switch (p.name()) {
                    case "ts_code" -> switch (variant) { case "SSE" -> "600000.SH"; case "BJ" -> "920008.BJ"; default -> "000001.SZ"; };
                    case "exchange", "exchange_id" -> variant;
                    case "start_date" -> "20260803";
                    case "end_date" -> "20260810";
                    default -> throw new IllegalStateException(p.name());
                }));
                var result = h.service().submit(new DownloadTaskService.Submission(
                        UUID.randomUUID(), definition.datasetKey(), DownloadMode.RANGE, params));
                assertThat(result.created()).isTrue();
                assertThat(result.task().mode()).isEqualTo(DownloadMode.RANGE);
                assertThat(result.task().status()).isEqualTo(DownloadTask.Status.QUEUED);
                assertThat(result.task().planReady()).isFalse();
                assertThat(result.task().params()).isEqualTo(params);
            }
            accepted.add(apiName);
        }
        assertThat(accepted).hasSize(30).containsExactlyInAnyOrderElementsOf(
                RANGE.keySet().stream().filter(n -> !WITHDRAWN.contains(n)).toList());
        h.upstream().verify();
    }

    @Test
    void locallyAcceptsAllFortySingleShapesAndKeepsMainBusinessAsStockSnapshot() {
        var h = harness();
        var accepted = new ArrayList<DownloadTask>();
        queuedInserts(h);
        for (var definition : DEFINITIONS) {
            var params = new LinkedHashMap<String, Object>();
            definition.parameters().forEach(p -> params.put(p.name(), VALUES.get(p.name())));
            var result = h.service().submit(new DownloadTaskService.Submission(
                    UUID.randomUUID(), definition.datasetKey(), DownloadMode.SINGLE, params));
            assertThat(result.created()).isTrue();
            assertThat(result.task().mode()).isEqualTo(DownloadMode.SINGLE);
            assertThat(result.task().status()).isEqualTo(DownloadTask.Status.QUEUED);
            assertThat(result.task().planReady()).isFalse();
            accepted.add(result.task());
        }
        assertThat(accepted).hasSize(40);
        assertThat(accepted.stream().filter(t -> t.datasetKey().apiName().value().equals("fina_mainbz")))
                .singleElement().satisfies(task -> assertThat(task.params()).isEqualTo(Map.of("ts_code", "000001.SZ")));
        h.upstream().verify();
    }

    private static Map<String, RangeExpectation> rangeExpectations() {
        var result = new LinkedHashMap<String, RangeExpectation>();
        """
            daily S N TRADE_DATE ROW 6000
            weekly S N TRADE_DATE ROW 6000
            monthly S N TRADE_DATE ROW 4500
            adj_factor S N TRADE_DATE RESPONSE -
            daily_basic S N TRADE_DATE ROW 6000
            stk_limit S N TRADE_DATE ROW 5800
            suspend_d S N TRADE_DATE RESPONSE -
            moneyflow S N TRADE_DATE ROW 6000
            margin I N TRADE_DATE ROW 4000
            margin_detail S N TRADE_DATE ROW 6000
            block_trade S N TRADE_DATE ROW 1000
            slb_len D N TRADE_DATE ROW 5000
            slb_sec S N TRADE_DATE ROW 5000
            slb_sec_detail S N TRADE_DATE ROW 5000
            trade_cal E N CALENDAR_DATE CALENDAR -
            new_share D N ISSUE_DATE ROW 2000
            income S N ANNOUNCEMENT_DATE RESPONSE -
            balancesheet S N ANNOUNCEMENT_DATE RESPONSE -
            cashflow S N ANNOUNCEMENT_DATE RESPONSE -
            fina_audit S N ANNOUNCEMENT_DATE RESPONSE -
            forecast S N ANNOUNCEMENT_DATE ROW 3500
            express S N ANNOUNCEMENT_DATE RESPONSE -
            repurchase D N ANNOUNCEMENT_DATE RESPONSE -
            stk_managers S N ANNOUNCEMENT_DATE RESPONSE -
            stk_holdernumber S N ANNOUNCEMENT_DATE ROW 3000
            stk_holdertrade S N ANNOUNCEMENT_DATE ROW 3000
            pledge_detail S N ANNOUNCEMENT_DATE ROW 1000
            fina_indicator S N REPORT_PERIOD ROW 100
            fina_mainbz S N REPORT_PERIOD ROW 100
            top10_holders S N REPORT_PERIOD RESPONSE -
            top10_floatholders S N REPORT_PERIOD RESPONSE -
            top_list S T TRADE_DATE ROW 10000
            dividend S C ANNOUNCEMENT_DATE ROW 2000
            disclosure_date S C ANNOUNCEMENT_DATE ROW 6000
            """.strip().lines().forEach(row -> {
                String[] r = row.split(" ");
                var parameters = new ArrayList<String>();
                switch (r[1]) { case "S" -> parameters.add("ts_code"); case "E" -> parameters.add("exchange"); case "I" -> parameters.add("exchange_id"); default -> { } }
                parameters.add("start_date"); parameters.add("end_date");
                var expected = new RangeExpectation(DateAxis.valueOf(r[3]), switch (r[2]) {
                    case "T" -> PlanningMode.TRADING_DAYS; case "C" -> PlanningMode.CALENDAR_DAYS; default -> PlanningMode.NATIVE_RANGE;
                }, r[2].equals("N") && r[4].equals("ROW"), switch (r[4]) {
                    case "ROW" -> Kind.CONFIRMED_ROW_LIMIT; case "CALENDAR" -> Kind.VERIFIED_RULE; default -> Kind.RESPONSE_ONLY;
                }, r[5].equals("-") ? null : Long.valueOf(r[5]), List.copyOf(parameters));
                if (result.put(r[0], expected) != null) throw new IllegalStateException(r[0]);
            });
        return Map.copyOf(result);
    }

    private static void queuedInserts(Harness h) {
        when(h.repository().insert(any())).thenAnswer(call -> {
            DownloadTaskRepository.NewTask request = call.getArgument(0);
            return new DownloadTask(request.taskId(), request.submissionId(), "request-hash", request.datasetKey(),
                    request.mode(), request.normalizedParams(), request.definitionHash(), request.policySnapshot(),
                    DownloadTask.Status.QUEUED, false, request.activeRunId(), 0, 1, 0, 0, null,
                    NOW, NOW, NOW, null, null, null);
        });
    }

    private static Harness harness() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://batch-admission.invalid");
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(builder).build();
        var properties = new TushareProperties(true, URI.create("https://batch-admission.invalid"),
                new TushareProperties.Credential("controlled-admission-token"), Duration.ofSeconds(1),
                Duration.ofSeconds(1), 1_048_576, Duration.ZERO);
        var plugin = new TushareProPlugin(properties, new TushareProClient(builder.build(), properties), DEFINITIONS);
        // Catalog and repository are the database boundaries; plugin, adapters and admission are real.
        var catalog = mock(DatasetCatalog.class);
        for (var definition : DEFINITIONS) when(catalog.find(definition.datasetKey())).thenReturn(Optional.of(definition));
        var adapters = new AdapterRegistry(DEFINITIONS.stream().<DatasetAdapter>map(d ->
                new GenericDatasetAdapter(d, new ValueConverter(), new FingerprintKeyCodec())).toList());
        var repository = mock(DownloadTaskRepository.class);
        var service = new DownloadTaskService(new PluginRegistry(List.of(plugin)), catalog, adapters,
                new ParameterValidator(), repository, new DownloadTaskJson(), Clock.fixed(NOW, ZoneOffset.UTC),
                UUID.randomUUID(), DownloadTaskService.Settings.defaults());
        return new Harness(service, plugin, repository, upstream);
    }

    private record Harness(DownloadTaskService service, TushareProPlugin plugin,
            DownloadTaskRepository repository, MockRestServiceServer upstream) {}
    private record RangeExpectation(DateAxis dateAxis, PlanningMode planningMode, boolean splittable,
            Kind ruleKind, Long rowLimit, List<String> parameters) {}
}
