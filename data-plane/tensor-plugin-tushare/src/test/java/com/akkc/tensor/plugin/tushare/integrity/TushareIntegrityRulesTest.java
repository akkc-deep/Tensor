package com.akkc.tensor.plugin.tushare.integrity;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.tushare.TushareProPlugin;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TushareIntegrityRulesTest {
    private static final List<DatasetDefinition> DEFINITIONS = new DatasetDefinitionLoader().loadAll(
            new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml");

    @Test
    void weeklyUsesThursdayWhenFridayIsClosed() {
        var context = new RecordingContext(Map.of(
                "trade_cal", calendar(LocalDate.of(2024, 3, 25), 7, Set.of(
                        LocalDate.of(2024, 3, 25), LocalDate.of(2024, 3, 26),
                        LocalDate.of(2024, 3, 27), LocalDate.of(2024, 3, 28))),
                "stock_basic", List.of(Map.of("ts_code", "600000.SH", "list_date", LocalDate.of(1999, 11, 10))),
                "suspend_d", List.of()));

        rule("weekly").evaluate(scope("weekly", "600000.SH", LocalDate.of(2024, 3, 25),
                LocalDate.of(2024, 3, 31), Instant.parse("2024-04-02T00:00:00Z")), context, issue -> {});

        assertThat(context.expected).isNotNull();
        assertThat(context.expected.basis()).isEqualTo(IntegrityExpectedKeys.Basis.UNCONFIRMED);
        assertThat(StreamSupport.stream(context.expected.keys().spliterator(), false).toList())
                .containsExactly(Map.of("ts_code", "600000.SH", "trade_date", LocalDate.of(2024, 3, 28)));
    }

    @Test
    void hookDeclaresExactSharedWindowsExchangeAndPurpose() {
        var plugin = plugin();
        var daily = plugin.integrityReferenceReads(scope("daily", "000001.SZ", LocalDate.of(2024, 2, 27),
                LocalDate.of(2024, 2, 29), null));
        assertThat(daily).containsExactly(
                request("trade_cal", List.of("exchange", "cal_date", "is_open"), Map.of("exchange", "SZSE"),
                        "cal_date", LocalDate.of(2024, 2, 27), LocalDate.of(2024, 2, 29), "行情交易日历与周期边界"),
                request("stock_basic", List.of("ts_code", "list_date"), Map.of(), null, null, null, "上市日期线索"),
                request("suspend_d", List.of("ts_code", "trade_date", "suspend_timing", "suspend_type"), Map.of(),
                        "trade_date", LocalDate.of(2024, 2, 27), LocalDate.of(2024, 2, 29), "停复牌解释线索"));
        assertThat(plugin.integrityReferenceReads(scope("weekly", "600000.SH", LocalDate.of(2024, 2, 27),
                LocalDate.of(2024, 3, 1), null)).getFirst().dateRange())
                .isEqualTo(new IntegrityDateRange(LocalDate.of(2024, 2, 26), LocalDate.of(2024, 3, 3)));
        assertThat(plugin.integrityReferenceReads(scope("monthly", "600000.SH", LocalDate.of(2024, 2, 27),
                LocalDate.of(2024, 3, 1), null)).getFirst().dateRange())
                .isEqualTo(new IntegrityDateRange(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 31)));
        assertThat(plugin.integrityReferenceReads(scope("income", "600000.SH", LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 2), null))).isEmpty();
        assertThat(plugin.integrityReferenceReads(scope("daily", "920008.BJ", LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 2), null))).isEmpty();
    }

    @Test
    void hookRejectsNullForeignAndInvalidStockScopes() {
        var plugin = plugin();
        assertThatThrownBy(() -> plugin.integrityReferenceReads(null)).isInstanceOf(NullPointerException.class);
        var normal = scope("daily", "600000.SH", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), null);
        assertThatThrownBy(() -> plugin.integrityReferenceReads(new IntegrityScope(
                new DatasetKey(PluginId.of("other"), normal.datasetKey().apiName()), normal.symbol(),
                normal.startDate(), normal.endDate(), normal.acceptedAt(), null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plugin.integrityReferenceReads(new IntegrityScope(normal.datasetKey(), "600000.HK",
                normal.startDate(), normal.endDate(), normal.acceptedAt(), null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bjHasNoCalendarFallbackCandidateOrRead() {
        var context = new RecordingContext(Map.of());
        var found = new ArrayList<IntegrityIssue>();
        var result = rule("daily").evaluate(scope("daily", "920008.BJ", LocalDate.of(2024, 3, 28),
                LocalDate.of(2024, 3, 28), Instant.parse("2024-04-02T00:00:00Z")), context, found::add);
        assertThat(result.status()).isEqualTo(IntegrityStatus.UNKNOWN);
        assertThat(result.reasonCode()).isEqualTo("CALENDAR_BASIS_UNPROVEN");
        assertThat(context.requests).isEmpty();
        assertThat(context.expected.basis()).isEqualTo(IntegrityExpectedKeys.Basis.UNCONFIRMED);
        assertThat(keys(context)).isEmpty();
        assertThat(context.compareCalls).isEqualTo(1);
        assertThat(found).singleElement().extracting(IntegrityIssue::reasonCode).isEqualTo("CALENDAR_BASIS_UNPROVEN");
    }

    @Test
    void malformedCalendarCellsAreUnknownWhileIndependentDatesStillCompare() {
        LocalDate start = LocalDate.of(2024, 3, 25);
        var valid = calendar(start, 5, Set.of(start, start.plusDays(1), start.plusDays(2), start.plusDays(3)));
        var cases = new ArrayList<List<Map<String, Object>>>();
        cases.add(valid.stream().filter(row -> !row.get("cal_date").equals(start.plusDays(1))).toList());
        var duplicate = new ArrayList<>(valid); duplicate.add(valid.get(1)); cases.add(duplicate);
        cases.add(replace(valid, 1, "is_open", null));
        cases.add(replace(valid, 1, "is_open", 2L));
        cases.add(replace(valid, 1, "is_open", "1"));
        cases.add(replace(valid, 1, "exchange", "SZSE"));
        cases.add(replace(valid, 1, "cal_date", "20240326"));
        cases.add(List.of());
        for (var rows : cases) {
            var context = context(rows, listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
            var issues = new ArrayList<IntegrityIssue>();
            var result = rule("daily").evaluate(scope("daily", "600000.SH", start, start.plusDays(4),
                    Instant.parse("2024-04-02T00:00:00Z")), context, issues::add);
            assertThat(result.reasonCode()).isEqualTo("REFERENCE_INCOMPLETE");
            assertThat(issues).anyMatch(issue -> issue.reasonCode().equals("REFERENCE_INCOMPLETE"));
            assertThat(keys(context)).extracting(key -> key.get("trade_date"))
                    .containsExactlyElementsOf(rows.isEmpty() ? List.of()
                            : List.of(start, start.plusDays(2), start.plusDays(3)));
            if (rows.isEmpty()) assertThat(issues.stream().filter(issue -> issue.reasonCode().equals("REFERENCE_INCOMPLETE")))
                    .singleElement().satisfies(issue -> assertThat(issue.date()).isNull());
        }
    }

    @Test
    void weeklyCrossYearAndLeapMonthUseActualLastOpenDate() {
        var week = context(calendar(LocalDate.of(2024, 12, 30), 7,
                        Set.of(LocalDate.of(2024, 12, 31), LocalDate.of(2025, 1, 2))),
                listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("weekly").evaluate(scope("weekly", "600000.SH", LocalDate.of(2024, 12, 30), LocalDate.of(2025, 1, 5),
                Instant.parse("2025-01-07T00:00:00Z")), week, issue -> {});
        assertThat(keys(week)).extracting(key -> key.get("trade_date")).containsExactly(LocalDate.of(2025, 1, 2));

        var month = context(calendar(LocalDate.of(2024, 2, 1), 29,
                        Set.of(LocalDate.of(2024, 2, 27), LocalDate.of(2024, 2, 28))),
                listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("monthly").evaluate(scope("monthly", "600000.SH", LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29),
                Instant.parse("2024-03-02T00:00:00Z")), month, issue -> {});
        assertThat(keys(month)).extracting(key -> key.get("trade_date")).containsExactly(LocalDate.of(2024, 2, 28));
    }

    @Test
    void partialPeriodsUseFullCalendarAndOnlyIncludeMarkersInsideOriginalRange() {
        var rows = calendar(LocalDate.of(2024, 3, 25), 7, Set.of(LocalDate.of(2024, 3, 28)));
        var includes = context(rows, listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("weekly").evaluate(scope("weekly", "600000.SH", LocalDate.of(2024, 3, 27), LocalDate.of(2024, 3, 28),
                Instant.parse("2024-04-02T00:00:00Z")), includes, issue -> {});
        assertThat(keys(includes)).extracting(key -> key.get("trade_date")).containsExactly(LocalDate.of(2024, 3, 28));
        assertThat(includes.requests.getFirst().dateRange())
                .isEqualTo(new IntegrityDateRange(LocalDate.of(2024, 3, 25), LocalDate.of(2024, 3, 31)));

        var excludes = context(rows, listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("weekly").evaluate(scope("weekly", "600000.SH", LocalDate.of(2024, 3, 25), LocalDate.of(2024, 3, 27),
                Instant.parse("2024-04-02T00:00:00Z")), excludes, issue -> {});
        assertThat(keys(excludes)).isEmpty();

        var february = calendar(LocalDate.of(2024, 2, 1), 29, Set.of(LocalDate.of(2024, 2, 28)));
        var monthIncludes = context(february, listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("monthly").evaluate(scope("monthly", "600000.SH", LocalDate.of(2024, 2, 15), LocalDate.of(2024, 2, 29),
                Instant.parse("2024-03-02T00:00:00Z")), monthIncludes, issue -> {});
        assertThat(keys(monthIncludes)).extracting(key -> key.get("trade_date")).containsExactly(LocalDate.of(2024, 2, 28));
        var monthExcludes = context(february, listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("monthly").evaluate(scope("monthly", "600000.SH", LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 20),
                Instant.parse("2024-03-02T00:00:00Z")), monthExcludes, issue -> {});
        assertThat(keys(monthExcludes)).isEmpty();
    }

    @Test
    void expandedCalendarGapIsLocatedAsReferenceOutsideTargetRange() {
        var rows = new ArrayList<>(calendar(LocalDate.of(2024, 3, 25), 7, Set.of(LocalDate.of(2024, 3, 28))));
        rows.removeLast();
        var context = context(rows, listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        var issues = new ArrayList<IntegrityIssue>();
        var result = rule("weekly").evaluate(scope("weekly", "600000.SH", LocalDate.of(2024, 3, 27),
                LocalDate.of(2024, 3, 28), Instant.parse("2024-04-02T00:00:00Z")), context, issues::add);
        assertThat(result.reasonCode()).isEqualTo("REFERENCE_INCOMPLETE");
        assertThat(keys(context)).isEmpty();
        assertThat(issues).anySatisfy(issue -> {
            if (!issue.relatedDates().containsKey("cal_date")) return;
            assertThat(issue.date()).isNull();
            assertThat(issue.businessKey()).isEmpty();
            assertThat(issue.relatedDates()).containsEntry("cal_date", LocalDate.of(2024, 3, 31));
            assertThat(issue.evidence().getFirst().range()).isEqualTo(
                    new IntegrityDateRange(LocalDate.of(2024, 3, 25), LocalDate.of(2024, 3, 31)));
        });
    }

    @Test
    void unfinishedDailyWeekAndMonthAreExcludedWithRangeEvidence() {
        var daily = context(calendar(LocalDate.of(2024, 3, 28), 1, Set.of(LocalDate.of(2024, 3, 28))),
                listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        var dailyIssues = new ArrayList<IntegrityIssue>();
        rule("daily").evaluate(scope("daily", "600000.SH", LocalDate.of(2024, 3, 28), LocalDate.of(2024, 3, 28),
                Instant.parse("2024-03-28T01:00:00Z")), daily, dailyIssues::add);
        assertThat(keys(daily)).isEmpty();
        assertThat(dailyIssues).anyMatch(issue -> issue.reasonCode().equals("PERIOD_NOT_FINISHED"));

        var week = context(calendar(LocalDate.of(2024, 3, 25), 7, Set.of(LocalDate.of(2024, 3, 28))),
                listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("weekly").evaluate(scope("weekly", "600000.SH", LocalDate.of(2024, 3, 25), LocalDate.of(2024, 3, 31),
                Instant.parse("2024-03-29T00:00:00Z")), week, issue -> {});
        assertThat(keys(week)).isEmpty();

        var month = context(calendar(LocalDate.of(2024, 3, 1), 31, Set.of(LocalDate.of(2024, 3, 28))),
                listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("monthly").evaluate(scope("monthly", "600000.SH", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31),
                Instant.parse("2024-03-29T00:00:00Z")), month, issue -> {});
        assertThat(keys(month)).isEmpty();
    }

    @Test
    void listingDateExcludesOnlyStrictlyEarlierCandidatesAndNeverProvesLifecycle() {
        LocalDate candidate = LocalDate.of(2024, 3, 28);
        for (LocalDate listed : List.of(candidate.minusDays(1), candidate, candidate.plusDays(1))) {
            var context = context(calendar(candidate, 1, Set.of(candidate)), listed("600000.SH", listed), List.of());
            var issues = new ArrayList<IntegrityIssue>();
            rule("daily").evaluate(scope("daily", "600000.SH", candidate, candidate,
                    Instant.parse("2024-04-02T00:00:00Z")), context, issues::add);
            assertThat(keys(context)).hasSize(listed.isAfter(candidate) ? 0 : 1);
            assertThat(issues).anyMatch(issue -> issue.reasonCode().equals("LIFECYCLE_UNPROVEN"));
        }
        for (List<Map<String, Object>> listing : List.<List<Map<String, Object>>>of(List.of(), listed("600000.SH", null),
                List.of(Map.<String, Object>of("ts_code", "600000.SH", "list_date", candidate.minusDays(1)),
                        Map.<String, Object>of("ts_code", "600000.SH", "list_date", candidate)))) {
            var context = context(calendar(candidate, 1, Set.of(candidate)), listing, List.of());
            var issues = new ArrayList<IntegrityIssue>();
            rule("daily").evaluate(scope("daily", "600000.SH", candidate, candidate,
                    Instant.parse("2024-04-02T00:00:00Z")), context, issues::add);
            assertThat(keys(context)).hasSize(1);
            assertThat(issues).anyMatch(issue -> issue.reasonCode().equals("LISTING_HISTORY_UNPROVEN"));
        }
    }

    @Test
    void suspensionRowsRemainTraceableWithoutRemovingCandidates() {
        LocalDate date = LocalDate.of(2024, 3, 28);
        for (Map<String, Object> suspension : List.of(
                suspension(date, "S", "全天"), suspension(date, "R", "盘中"), suspension(date, "S", null))) {
            var context = context(calendar(date, 1, Set.of(date)), listed("600000.SH", LocalDate.of(2000, 1, 1)),
                    List.of(suspension));
            var issues = new ArrayList<IntegrityIssue>();
            var result = rule("daily").evaluate(scope("daily", "600000.SH", date, date,
                    Instant.parse("2024-04-02T00:00:00Z")), context, issues::add);
            assertThat(keys(context)).hasSize(1);
            assertThat(result.evidence().getFirst().summary())
                    .contains("suspensionRows=1", "tushare_pro:trade_cal purpose=行情交易日历与周期边界",
                            "tushare_pro:stock_basic purpose=上市日期线索",
                            "tushare_pro:suspend_d purpose=停复牌解释线索");
            assertThat(issues).anyMatch(issue -> Objects.equals(issue.relatedDates().get("trade_date"), date)
                    && issue.evidence().getFirst().summary().contains(String.valueOf(suspension.get("suspend_type"))));
        }
        var empty = context(calendar(date, 1, Set.of(date)), listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        var issues = new ArrayList<IntegrityIssue>();
        rule("daily").evaluate(scope("daily", "600000.SH", date, date,
                Instant.parse("2024-04-02T00:00:00Z")), empty, issues::add);
        assertThat(keys(empty)).hasSize(1);
        assertThat(issues).anyMatch(issue -> issue.reasonCode().equals("SUSPENSION_BASIS_UNPROVEN"));
    }

    @Test
    void comparisonIsAlwaysUnconfirmedAndResultNeverUpgradesFromUnknown() {
        LocalDate date = LocalDate.of(2024, 3, 28);
        var context = context(calendar(date, 1, Set.of(date)), listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        context.statistics = new IntegrityStatistics(0L, null, null, null, 1L, null, null);
        var issues = new ArrayList<IntegrityIssue>();
        var result = rule("daily").evaluate(scope("daily", "600000.SH", date, date,
                Instant.parse("2024-04-02T00:00:00Z")), context, issues::add);
        assertThat(context.compareCalls).isEqualTo(1);
        assertThat(context.expected.basis()).isEqualTo(IntegrityExpectedKeys.Basis.UNCONFIRMED);
        assertThat(result.status()).isEqualTo(IntegrityStatus.UNKNOWN);
        assertThat(result.statistics()).isEqualTo(context.statistics);
        assertThat(result.statistics().coverageRate()).isNull();
        assertThat(issues).noneMatch(issue -> Set.of(IntegrityIssue.Type.MISSING, IntegrityIssue.Type.EXTRA).contains(issue.type()));

        var matched = context(calendar(date, 1, Set.of(date)), listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        matched.statistics = new IntegrityStatistics(1L, null, null, null, 0L, null, null);
        var matchedResult = rule("daily").evaluate(scope("daily", "600000.SH", date, date,
                Instant.parse("2024-04-02T00:00:00Z")), matched, issue -> {});
        assertThat(matchedResult.status()).isEqualTo(IntegrityStatus.UNKNOWN);
        assertThat(matchedResult.statistics().coverageRate()).isNull();

        var closed = context(calendar(date, 1, Set.of()), listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        rule("daily").evaluate(scope("daily", "600000.SH", date, date,
                Instant.parse("2024-04-02T00:00:00Z")), closed, issue -> {});
        assertThat(keys(closed)).isEmpty();
        assertThat(closed.compareCalls).isEqualTo(1);
    }

    @Test
    void scanAndComparisonFailuresPropagate() {
        LocalDate date = LocalDate.of(2024, 3, 28);
        var scanFailure = context(calendar(date, 1, Set.of(date)), listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        scanFailure.scanFailure = new IllegalStateException("budget cancelled");
        assertThatThrownBy(() -> rule("daily").evaluate(scope("daily", "600000.SH", date, date,
                Instant.parse("2024-04-02T00:00:00Z")), scanFailure, issue -> {}))
                .isSameAs(scanFailure.scanFailure);

        var compareFailure = context(calendar(date, 1, Set.of(date)), listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        compareFailure.compareFailure = new IllegalStateException("read failed");
        assertThatThrownBy(() -> rule("daily").evaluate(scope("daily", "600000.SH", date, date,
                Instant.parse("2024-04-02T00:00:00Z")), compareFailure, issue -> {}))
                .isSameAs(compareFailure.compareFailure);
    }

    @Test
    void ruleHookAndCapabilityMetadataNeverUseTheSourceClient() {
        var client = mock(TushareProClient.class);
        var plugin = plugin(client);
        LocalDate date = LocalDate.of(2024, 3, 28);
        var context = context(calendar(date, 1, Set.of(date)), listed("600000.SH", LocalDate.of(2000, 1, 1)), List.of());
        var scope = scope("daily", "600000.SH", date, date, Instant.parse("2024-04-02T00:00:00Z"));
        plugin.integrityReferenceReads(scope);
        plugin.integrityDescriptor(ApiName.of("daily"));
        plugin.integrityRules(ApiName.of("daily")).getFirst().evaluate(scope, context, issue -> {});
        verifyNoInteractions(client);
    }

    private static List<Map<String, Object>> calendar(LocalDate start, int days, Set<LocalDate> open) {
        var rows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < days; i++) {
            var date = start.plusDays(i);
            rows.add(Map.of("exchange", "SSE", "cal_date", date, "is_open", open.contains(date) ? 1L : 0L));
        }
        return rows;
    }

    private static List<Map<String, Object>> replace(List<Map<String, Object>> rows, int index, String field, Object value) {
        var result = new ArrayList<>(rows);
        var replacement = new LinkedHashMap<>(result.get(index));
        replacement.put(field, value);
        result.set(index, replacement);
        return result;
    }

    private static List<Map<String, Object>> listed(String symbol, LocalDate date) {
        var row = new LinkedHashMap<String, Object>();
        row.put("ts_code", symbol);
        row.put("list_date", date);
        return List.of(row);
    }

    private static Map<String, Object> suspension(LocalDate date, String type, String timing) {
        var row = new LinkedHashMap<String, Object>();
        row.put("ts_code", "600000.SH");
        row.put("trade_date", date);
        row.put("suspend_type", type);
        row.put("suspend_timing", timing);
        return row;
    }

    private static RecordingContext context(List<Map<String, Object>> calendar,
            List<Map<String, Object>> listing, List<Map<String, Object>> suspension) {
        return new RecordingContext(Map.of("trade_cal", calendar, "stock_basic", listing, "suspend_d", suspension));
    }

    private static List<Map<String, Object>> keys(RecordingContext context) {
        return StreamSupport.stream(context.expected.keys().spliterator(), false).toList();
    }

    private static IntegrityReadRequest request(String api, List<String> columns, Map<String, Object> equalities,
            String dateField, LocalDate start, LocalDate end, String purpose) {
        return new IntegrityReadRequest(key(api), columns, equalities, dateField,
                start == null ? null : new IntegrityDateRange(start, end), false, purpose);
    }

    private static IntegrityRule rule(String api) {
        return plugin().integrityRules(ApiName.of(api)).getFirst();
    }

    private static TushareProPlugin plugin() {
        return plugin(mock(TushareProClient.class));
    }

    private static TushareProPlugin plugin(TushareProClient client) {
        return new TushareProPlugin(new TushareProperties(true, URI.create("https://integrity.invalid"),
                new TushareProperties.Credential(""), Duration.ofSeconds(1), Duration.ofSeconds(2), 1024),
                client, DEFINITIONS);
    }

    private static IntegrityScope scope(String api, String symbol, LocalDate start, LocalDate end, Instant snapshot) {
        Instant accepted = snapshot == null ? end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : snapshot.minusSeconds(1);
        return new IntegrityScope(key(api), symbol, start, end, accepted, snapshot);
    }

    private static DatasetKey key(String api) {
        return new DatasetKey(PluginId.of("tushare_pro"), ApiName.of(api));
    }

    private static final class RecordingContext implements IntegrityContext {
        private final Map<String, List<Map<String, Object>>> rows;
        private final List<IntegrityReadRequest> requests = new ArrayList<>();
        private IntegrityExpectedKeys expected;
        private IntegrityStatistics statistics = new IntegrityStatistics(0L, null, null, null, 1L, null, null);
        private RuntimeException scanFailure;
        private RuntimeException compareFailure;
        private int compareCalls;

        private RecordingContext(Map<String, List<Map<String, Object>>> rows) { this.rows = rows; }

        public void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> consumer) {
            if (scanFailure != null) throw scanFailure;
            requests.add(request);
            consumer.accept(rows.getOrDefault(request.datasetKey().apiName().value(), List.of()));
        }

        public IntegrityStatistics compare(IntegrityExpectedKeys expected) {
            if (compareFailure != null) throw compareFailure;
            compareCalls++;
            this.expected = expected;
            return statistics;
        }
    }
}
