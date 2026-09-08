package com.akkc.tensor.core.download;

import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.akkc.tensor.plugin.api.download.DownloadPolicy.*;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.*;

class DownloadBatchPlannerTest {
    private final DownloadParameterConverter converter = new DownloadParameterConverter(new ParameterValidator());
    private final DownloadBatchPlanner planner = new DownloadBatchPlanner(converter);

    @Test void thirtyOneDaysCoverThreeCompleteMonths() {
        var api = api("broker_recommend", Mode.MONTH_RANGE, SourceRequestMode.MONTH);
        var plugin = new ControlledPlugin(api);
        for (String month : List.of("202601", "202602", "202603"))
            plugin.advice.put(Map.of("month", month), BatchPlanning.SINGLE_MONTH);
        var plan = planner.planInitial(plugin, api, range("20260131", "20260302"), () -> {});
        assertThat(plan.datasetKey()).isEqualTo(new DatasetKey(PluginId.of("controlled"), api.apiName()));
        assertThat(plan.originalParams().values()).isEqualTo(range("20260131", "20260302"));
        assertThat(plan.originalDateRange()).isEqualTo(new DownloadParameterConverter.OriginalDateRange(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 2)));
        assertThat(plan.skippedDates()).isEmpty();
        assertThat(plan.batches()).extracting(b -> b.scope()).containsExactly(
                scope(MONTH, "2026-01"), scope(MONTH, "2026-02"), scope(MONTH, "2026-03"));
        assertThat(plan.batches()).extracting(b -> b.fetchBatch().sourceParams()).containsExactly(
                Map.of("month", "202601"), Map.of("month", "202602"), Map.of("month", "202603"));
        assertThat(plan.batches()).allSatisfy(b -> assertThat(b.fetchBatch().recoveryPolicy()).isEqualTo(api.downloadPolicy().recoveryPolicy()));
        assertThat(plugin.events).containsExactly("plan", "plan", "plan");
    }

    @Test void thirtyTwoDaysFailBeforeAnyCallback() {
        var api = api("broker_recommend", Mode.MONTH_RANGE, SourceRequestMode.MONTH);
        var plugin = new ControlledPlugin(api);
        code(() -> planner.planInitial(plugin, api, range("20260131", "20260303"), () -> { throw new AssertionError(); }), ErrorCode.PARAM_INVALID);
        assertThat(plugin.events).isEmpty();
    }

    @Test void explicitSingleDateAdviceRechecksEachExactDay() {
        var api = api("income", Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE, parameter("ts_code", ParameterType.TS_CODE, null));
        var plugin = new ControlledPlugin(api);
        var raw = new HashMap<String,Object>(range("20260904", "20260907")); raw.put("ts_code", " 000001.sz ");
        var whole = new HashMap<String,Object>(range("20260904", "20260907")); whole.put("ts_code", "000001.SZ");
        plugin.advice.put(whole, BatchPlanning.SINGLE_DATE);
        var expected = new ArrayList<Map<String,Object>>();
        for (String day : List.of("20260904", "20260905", "20260906", "20260907")) {
            var exact = new HashMap<String,Object>(range(day, day)); exact.put("ts_code", "000001.SZ");
            plugin.advice.put(exact, BatchPlanning.SINGLE_DATE); expected.add(exact);
        }
        var plan = planner.planInitial(plugin, api, raw, () -> {});
        assertThat(plan.batches()).extracting(b -> b.fetchBatch().sourceParams()).containsExactlyElementsOf(expected);
        assertThat(plan.batches()).extracting(b -> b.scope()).containsExactly(scope(DATE, "2026-09-04"), scope(DATE, "2026-09-05"), scope(DATE, "2026-09-06"), scope(DATE, "2026-09-07"));
        expected.addFirst(whole);
        assertThat(plugin.requests).extracting(FetchBatch::sourceParams).containsExactlyElementsOf(expected);
        assertThat(plugin.events).containsExactly("plan", "plan", "plan", "plan", "plan");
    }

    @Test void tradingRangesUseMaximalOpenSegmentsAndExplicitDailyAdvice() {
        for (boolean split : List.of(false, true)) {
            var api = api("margin", Mode.TRADE_DATE_RANGE, SourceRequestMode.RANGE, enumeration("exchange_id", "SSE", "SZSE", "BSE"));
            var plugin = new ControlledPlugin(api);
            plugin.openDates = Set.of(day(1), day(2), day(4), day(7));
            var raw = with(range("20260901", "20260907"), "exchange_id", "SSE");
            plugin.advice.put(with(range("20260901", "20260902"), "exchange_id", "SSE"), split ? BatchPlanning.SINGLE_DATE : BatchPlanning.SOURCE_RANGE);
            var expectedScopes = new ArrayList<RecoverySelector>();
            if (!split) expectedScopes.add(scope(RANGE, "2026-09-01/2026-09-02"));
            for (int d : split ? List.of(1, 2, 4, 7) : List.of(4, 7)) {
                plugin.advice.put(with(range("2026090" + d, "2026090" + d), "exchange_id", "SSE"), BatchPlanning.SOURCE_RANGE);
                expectedScopes.add(scope(DATE, day(d).toString()));
            }
            var plan = planner.planInitial(plugin, api, raw, () -> plugin.events.add("check"));
            assertThat(plan.batches()).extracting(DownloadBatchPlanner.PlannedBatch::scope).containsExactlyElementsOf(expectedScopes);
            assertThat(plan.skippedDates()).containsExactlyInAnyOrder(day(3), day(5), day(6));
            assertThat(plan.originalParams().values()).isEqualTo(raw);
            assertThat(plan.originalDateRange()).isEqualTo(new DownloadParameterConverter.OriginalDateRange(day(1), day(7)));
            assertThat(plugin.calendarScopes).containsExactly(new CalendarScope(raw, Set.of(day(1), day(2), day(3), day(4), day(5), day(6), day(7))));
            assertThat(plugin.requests).hasSize(split ? 5 : 3);
            assertThat(plugin.events.subList(0, 5)).containsExactly("check", "check", "calendar", "check", "check");
            for (var batch : plan.batches()) assertThat(batch.fetchBatch().sourceParams()).isEqualTo(converter.mapInitial(api, plan.originalParams(), batch.scope()).sourceParams().values());
        }
    }

    @Test void calendarUnionKeepsOtherMarketsOpenAndAllClosedSkipsSourceGates() {
        var api = api("daily", Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE);
        var plugin = new ControlledPlugin(api);
        plugin.calendar = scope -> new CalendarDecision(scope, Map.of("SSE", Map.of(day(1), true, day(2), false), "SZSE", Map.of(day(1), false, day(2), true)));
        plugin.advice.put(Map.of("trade_date", "20260901"), BatchPlanning.SINGLE_DATE);
        plugin.advice.put(Map.of("trade_date", "20260902"), BatchPlanning.SINGLE_DATE);
        var plan = planner.planInitial(plugin, api, range("20260901", "20260902"), () -> {});
        assertThat(plan.batches()).extracting(b -> b.scope()).containsExactly(scope(DATE, day(1).toString()), scope(DATE, day(2).toString()));
        assertThat(plan.skippedDates()).isEmpty();
        var unknown = api("hk_hold", Mode.TRADE_DATE_RANGE, null);
        var closed = new ControlledPlugin(unknown); closed.openDates = Set.of();
        var empty = planner.planInitial(closed, unknown, range("20260901", "20260902"), () -> closed.events.add("check"));
        assertThat(empty.batches()).isEmpty(); assertThat(empty.skippedDates()).containsExactlyInAnyOrder(day(1), day(2));
        assertThat(closed.events).containsExactly("check", "check", "calendar", "check", "check");
    }

    @Test void dateCandidatesUseOnlyTheDeclaredDateParameter() {
        for (String name : List.of("daily", "top_list", "top_inst", "dividend", "disclosure_date", "fina_indicator")) {
            boolean trade = List.of("daily", "top_list", "top_inst").contains(name);
            var common = name.equals("fina_indicator") ? new ParameterDescriptor[]{parameter("ts_code", ParameterType.TS_CODE, null)} : new ParameterDescriptor[0];
            var api = api(name, trade ? Mode.TRADE_DATE_RANGE : Mode.ANN_DATE_RANGE, SourceRequestMode.DATE, common);
            var plugin = new ControlledPlugin(api); plugin.openDates = Set.of(day(1), day(2), day(4), day(7));
            var raw = range("20260901", "20260907");
            if (common.length > 0) raw = with(raw, "ts_code", "000001.SZ");
            var expected = new ArrayList<Map<String,Object>>();
            for (int d : trade ? List.of(1, 2, 4, 7) : List.of(1, 2, 3, 4, 5, 6, 7)) {
                Map<String,Object> request = Map.of(trade ? "trade_date" : "ann_date", "2026090" + d);
                if (common.length > 0) request = with(request, "ts_code", "000001.SZ");
                plugin.advice.put(request, BatchPlanning.SINGLE_DATE); expected.add(request);
            }
            var plan = planner.planInitial(plugin, api, raw, () -> {});
            assertThat(plan.batches()).extracting(b -> b.fetchBatch().sourceParams()).containsExactlyElementsOf(expected);
            assertThat(plan.batches()).allSatisfy(b -> assertThat(b.scope().timeType()).isEqualTo(DATE));
            assertThat(plugin.calendarScopes).hasSize(trade ? 1 : 0);
        }
    }

    @Test void announcementRangesKeepWeekendsAndNativeRangesAlwaysKeepOneExactRequest() {
        for (String name : List.of("income", "trade_cal", "new_share", "namechange")) {
            var mode = name.equals("income") ? Mode.ANN_DATE_RANGE : Mode.NATIVE_RANGE;
            var common = name.equals("income") ? new ParameterDescriptor[]{parameter("ts_code", ParameterType.TS_CODE, null)}
                    : name.equals("trade_cal") ? new ParameterDescriptor[]{enumeration("exchange", "SSE", "SZSE", "BSE")} : new ParameterDescriptor[0];
            var api = api(name, mode, SourceRequestMode.RANGE, common);
            for (var interval : List.of(range("20260904", name.equals("income") ? "20260907" : "20260906"), range("20260903", "20260903"))) {
                var raw = new HashMap<>(interval);
                if (name.equals("income")) raw.put("ts_code", "000001.SZ");
                if (name.equals("trade_cal")) raw.put("exchange", "SSE");
                var plugin = new ControlledPlugin(api); plugin.advice.put(raw, BatchPlanning.SOURCE_RANGE);
                var plan = planner.planInitial(plugin, api, raw, () -> {});
                assertThat(plan.batches()).hasSize(1);
                assertThat(plan.batches().getFirst().fetchBatch().sourceParams()).isEqualTo(raw);
                assertThat(plan.batches().getFirst().scope()).isEqualTo(interval.get("start_date").equals("20260903") ? scope(DATE, "2026-09-03") : scope(RANGE, name.equals("income") ? "2026-09-04/2026-09-07" : "2026-09-04/2026-09-06"));
                assertThat(plan.skippedDates()).isEmpty(); assertThat(plugin.events).containsExactly("plan");
            }
        }
    }

    @Test void fullMonthsKeepYearIdentityAndBoundaryDatesDoNotOverflow() {
        for (var interval : List.of(range("20260215", "20260215"), range("20261231", "20270101"), range("00010101", "00010101"), range("99991231", "99991231"))) {
            var api = api("broker_recommend", Mode.MONTH_RANGE, SourceRequestMode.MONTH);
            var plugin = new ControlledPlugin(api);
            var expected = interval.get("start_date").equals("20260215") ? List.of("202602")
                    : interval.get("start_date").equals("20261231") ? List.of("202612", "202701")
                    : List.of(((String) interval.get("start_date")).substring(0, 6));
            expected.forEach(m -> plugin.advice.put(Map.of("month", m), BatchPlanning.SINGLE_MONTH));
            var plan = planner.planInitial(plugin, api, interval, () -> {});
            assertThat(plan.batches()).extracting(b -> b.fetchBatch().sourceParams()).containsExactlyElementsOf(expected.stream().map(m -> Map.<String,Object>of("month", m)).toList());
            assertThat(plan.batches()).extracting(b -> b.scope()).containsExactlyElementsOf(expected.stream().map(m -> scope(MONTH, m.substring(0,4) + "-" + m.substring(4))).toList());
            assertThat(plugin.calendarScopes).isEmpty();
        }
        for (String value : List.of("00010101", "99991231", "20240229")) {
            var api = api("income", Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE);
            var plugin = new ControlledPlugin(api); plugin.advice.put(range(value, value), BatchPlanning.SOURCE_RANGE);
            assertThat(planner.planInitial(plugin, api, range(value, value), () -> {}).batches()).hasSize(1);
        }
    }

    @Test void allElevenOriginalInterfacesKeepTheirExactConditionsOrRefuseUnknownMode() {
        var cases = new LinkedHashMap<String,List<Map<String,Object>>>();
        cases.put("stock_basic", List.of(Map.of("list_status", "L"), Map.of("list_status", "P"), Map.of("list_status", "D")));
        cases.put("stock_company", List.of(Map.of("exchange", "SSE"), Map.of("exchange", "SZSE"), Map.of("exchange", "BSE")));
        for (String name : List.of("stk_holdernumber", "stk_rewards")) cases.put(name, List.of(Map.of("ts_code", "000001.SZ")));
        for (String name : List.of("index_classify", "index_member_all", "pledge_detail", "pledge_stat", "stk_managers", "index_member")) cases.put(name, List.of(Map.of()));
        cases.put("hs_const", List.of(Map.of("hs_type", "SH"), Map.of("hs_type", "SZ")));
        for (var entry : cases.entrySet()) for (var raw : entry.getValue()) {
            ParameterDescriptor[] common = raw.isEmpty() ? new ParameterDescriptor[0]
                    : new ParameterDescriptor[]{raw.containsKey("ts_code") ? parameter("ts_code", ParameterType.TS_CODE, null)
                    : enumeration(raw.keySet().iterator().next(), entry.getValue().stream().map(m -> (String) m.values().iterator().next()).toArray(String[]::new))};
            boolean unknown = Set.of("hs_const", "index_member").contains(entry.getKey());
            var api = api(entry.getKey(), Mode.ORIGINAL_PARAMS, unknown ? null : SourceRequestMode.NONE, common);
            var plugin = new ControlledPlugin(api); plugin.advice.put(raw, BatchPlanning.ORIGINAL_PARAMS);
            if (unknown) {
                code(() -> planner.planInitial(plugin, api, raw, () -> {}), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
                assertThat(plugin.events).isEmpty();
            } else {
                var plan = planner.planInitial(plugin, api, raw, () -> {});
                assertThat(plan.batches()).containsExactly(new DownloadBatchPlanner.PlannedBatch(scope(NONE, ""), new FetchBatch(raw, api.downloadPolicy().recoveryPolicy())));
                assertThat(plan.originalParams().values()).isEqualTo(raw); assertThat(plan.originalDateRange()).isNull();
                assertThat(plan.skippedDates()).isEmpty(); assertThat(plugin.events).containsExactly("plan");
            }
        }
    }

    @Test void incompatibleAndUnknownAdviceNeverFallsBack() {
        for (var mode : Mode.values()) {
            var sourceMode = switch(mode) { case TRADE_DATE_RANGE, ANN_DATE_RANGE -> SourceRequestMode.DATE; case MONTH_RANGE -> SourceRequestMode.MONTH; case NATIVE_RANGE -> SourceRequestMode.RANGE; case ORIGINAL_PARAMS -> SourceRequestMode.NONE; };
            var api = api("controlled", mode, sourceMode);
            var raw = mode == Mode.ORIGINAL_PARAMS ? Map.<String,Object>of() : range("20260901", "20260902");
            var original = converter.bindInitial(api, raw);
            var first = switch(mode) { case TRADE_DATE_RANGE, ANN_DATE_RANGE -> scope(DATE, day(1).toString()); case MONTH_RANGE -> scope(MONTH, "2026-09"); case NATIVE_RANGE -> scope(RANGE, "2026-09-01/2026-09-02"); case ORIGINAL_PARAMS -> scope(NONE, ""); };
            var mapped = converter.mapInitial(api, original, first).sourceParams().values();
            for (var advice : Arrays.asList(null, BatchPlanning.UNCONFIRMED, sourceMode == SourceRequestMode.DATE ? BatchPlanning.SOURCE_RANGE : BatchPlanning.SINGLE_DATE)) {
                var plugin = new ControlledPlugin(api); plugin.openDates = Set.of(day(1), day(2)); plugin.advice.put(mapped, advice);
                code(() -> planner.planInitial(plugin, api, raw, () -> {}), advice == null || advice == BatchPlanning.UNCONFIRMED ? ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED : ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
                assertThat(plugin.requests).hasSize(1);
            }
        }
        var api = api("trade_cal", Mode.NATIVE_RANGE, SourceRequestMode.RANGE);
        var plugin = new ControlledPlugin(api); plugin.advice.put(range("20260901", "20260901"), BatchPlanning.SINGLE_DATE);
        code(() -> planner.planInitial(plugin, api, range("20260901", "20260901"), () -> {}), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertThat(plugin.requests).hasSize(1);
    }

    @Test void exceptionsStopPrechecksWithoutSplittingOrReturningAPrefix() {
        var api = api("income", Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE);
        for (ErrorCode code : List.of(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, ErrorCode.SOURCE_TIMEOUT)) {
            var plugin = new ControlledPlugin(api); var failure = new SourceException(code, "Controlled failure"); plugin.failures.put(range("20260901", "20260904"), failure);
            assertThatThrownBy(() -> planner.planInitial(plugin, api, range("20260901", "20260904"), () -> {})).isSameAs(failure);
            assertThat(plugin.requests).hasSize(1);
        }
        var plugin = new ControlledPlugin(api);
        plugin.advice.put(range("20260901", "20260904"), BatchPlanning.SINGLE_DATE);
        plugin.advice.put(range("20260901", "20260901"), BatchPlanning.SOURCE_RANGE);
        var failure = new SourceException(ErrorCode.SOURCE_TIMEOUT, "Controlled failure"); plugin.failures.put(range("20260902", "20260902"), failure);
        assertThatThrownBy(() -> planner.planInitial(plugin, api, range("20260901", "20260904"), () -> {})).isSameAs(failure);
        assertThat(plugin.requests).extracting(FetchBatch::sourceParams).containsExactly(range("20260901", "20260904"), range("20260901", "20260901"), range("20260902", "20260902"));
    }

    @Test void invalidInitialParametersPrecedeCalendarAndSourceCallbacks() {
        var api = api("margin", Mode.TRADE_DATE_RANGE, SourceRequestMode.RANGE, enumeration("exchange_id", "SSE", "SZSE"));
        var cases = List.of(range("20260131", "20260303"), range("20260229", "20260229"), range("00000101", "00000101"), range("20260902", "20260901"), Map.<String,Object>of("start_date", "20260901"), Map.<String,Object>of("start_date", 20260901, "end_date", "20260902"), with(range("20260901", "20260902"), "trade_date", "20260901"));
        for (var raw : cases) {
            var plugin = new ControlledPlugin(api);
            assertThatThrownBy(() -> planner.planInitial(plugin, api, with(raw, "exchange_id", "SSE"), () -> { throw new AssertionError("context before binding"); }))
                    .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isIn(ErrorCode.PARAM_REQUIRED, ErrorCode.PARAM_INVALID));
            assertThat(plugin.events).isEmpty();
        }
        var plugin = new ControlledPlugin(api);
        code(() -> planner.planInitial(plugin, api, with(range("20260901", "20260902"), "exchange_id", "INVALID"), () -> {}), ErrorCode.PARAM_INVALID);
        assertThat(plugin.events).isEmpty();
    }

    @Test void mismatchedAndNullCalendarScopesRefuseBeforePlanning() {
        var api = api("daily", Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE);
        for (int variant : List.of(0, 1, 2)) {
            var plugin = new ControlledPlugin(api);
            plugin.calendar = exact -> {
                if (variant == 0) return null;
                var changed = variant == 1 ? new CalendarScope(with(exact.publicParams(), "exchange", "SSE"), exact.dates()) : new CalendarScope(exact.publicParams(), Set.of(day(2)));
                var table = new HashMap<LocalDate,Boolean>(); changed.dates().forEach(d -> table.put(d, true));
                return new CalendarDecision(changed, Map.of("SSE", table));
            };
            code(() -> planner.planInitial(plugin, api, range("20260901", "20260902"), () -> {}), ErrorCode.CALENDAR_UNCONFIRMED);
            assertThat(plugin.events).containsExactly("calendar");
        }
    }

    @Test void contextFailuresKeepTheSameInstanceAtEveryBoundary() {
        var api = api("daily", Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE);
        for (int failAt = 1; failAt <= 6; failAt++) {
            final int stop = failAt;
            var plugin = new ControlledPlugin(api); plugin.openDates = Set.of(day(1)); plugin.advice.put(Map.of("trade_date", "20260901"), BatchPlanning.SINGLE_DATE);
            var checks = new java.util.concurrent.atomic.AtomicInteger(); var failure = new IllegalStateException("server");
            assertThatThrownBy(() -> planner.planInitial(plugin, api, range("20260901", "20260901"), () -> { plugin.events.add("check"); if (checks.incrementAndGet() == stop) throw failure; })).isSameAs(failure);
            assertThat(checks).hasValue(stop);
            assertThat(plugin.calendarScopes).hasSize(stop <= 2 ? 0 : 1);
            assertThat(plugin.requests).hasSize(stop <= 4 ? 0 : 1);
            assertThat(plugin.events).doesNotContain("fetch", "download");
        }
    }

    @Test void plansFreezeInputsCollectionsAndReconfirmEveryCall() {
        var api = api("daily", Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE);
        var plugin = new ControlledPlugin(api); plugin.openDates = Set.of(day(1)); plugin.advice.put(Map.of("trade_date", "20260901"), BatchPlanning.SINGLE_DATE);
        var raw = new HashMap<>(range("20260901", "20260902"));
        var plan = planner.planInitial(plugin, api, raw, () -> {});
        planner.planInitial(plugin, api, raw, () -> {});
        raw.clear();
        assertThat(plan.originalParams().values()).isEqualTo(range("20260901", "20260902"));
        assertThat(plugin.events).containsExactly("calendar", "plan", "calendar", "plan");
        assertThatThrownBy(() -> plan.originalParams().values().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.skippedDates().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.batches().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.batches().getFirst().fetchBatch().sourceParams().clear()).isInstanceOf(UnsupportedOperationException.class);
        var skipped = new HashSet<>(plan.skippedDates()); var batches = new ArrayList<>(plan.batches());
        var copied = new DownloadBatchPlanner.Plan(plan.datasetKey(), plan.originalParams(), plan.originalDateRange(), skipped, batches);
        skipped.clear(); batches.clear(); assertThat(copied).isEqualTo(plan);
    }

    @Test void coverageRejectsDuplicateMissingExpandedOverlappingReversedAndClosedDates() {
        var api = api("income", Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE);
        var original = converter.bindInitial(api, range("20260901", "20260904"));
        var a = batch(api, original, scope(DATE, day(1).toString()));
        var b = batch(api, original, scope(RANGE, "2026-09-02/2026-09-04"));
        for (var bad : List.of(List.of(a, a, b), List.of(a), List.of(b, a), List.of(a, b, batch(api, original, scope(DATE, day(4).toString()))), List.<DownloadBatchPlanner.PlannedBatch>of(), List.of(batch(api, original, scope(RANGE, "2026-09-01/2026-09-03")), b))) {
            badCoverage(api, original, Set.of(), bad);
        }
        badCoverage(api, original, Set.of(day(4)), List.of(a, b));
        var expanded = new DownloadBatchPlanner.PlannedBatch(scope(RANGE, "2026-09-01/2026-09-05"), new FetchBatch(range("20260901", "20260905"), api.downloadPolicy().recoveryPolicy()));
        badCoverage(api, original, Set.of(), List.of(expanded));
        var trade = api("margin", Mode.TRADE_DATE_RANGE, SourceRequestMode.RANGE);
        var tradeOriginal = converter.bindInitial(trade, original.values());
        badCoverage(trade, tradeOriginal, Set.of(day(2)), List.of(batch(trade, tradeOriginal, scope(RANGE, "2026-09-01/2026-09-04"))));
        badCoverage(trade, tradeOriginal, Set.of(day(7)), List.of());
    }

    @Test void coverageRejectsWrongMonthShapesNativeSplitsAndDuplicateOriginalRequests() {
        var month = api("broker_recommend", Mode.MONTH_RANGE, SourceRequestMode.MONTH);
        var original = converter.bindInitial(month, range("20260131", "20260302"));
        var jan = batch(month, original, scope(MONTH, "2026-01")); var feb = batch(month, original, scope(MONTH, "2026-02")); var mar = batch(month, original, scope(MONTH, "2026-03"));
        for (var bad : List.of(List.of(jan, feb, feb, mar), List.of(jan, mar), List.of(mar, feb, jan), List.<DownloadBatchPlanner.PlannedBatch>of())) badCoverage(month, original, Set.of(), bad);
        var nativeApi = api("new_share", Mode.NATIVE_RANGE, SourceRequestMode.RANGE); var nativeOriginal = converter.bindInitial(nativeApi, range("20260901", "20260902"));
        badCoverage(nativeApi, nativeOriginal, Set.of(), List.of(batch(nativeApi, nativeOriginal, scope(DATE, day(1).toString())), batch(nativeApi, nativeOriginal, scope(DATE, day(2).toString()))));
        var none = api("index_classify", Mode.ORIGINAL_PARAMS, SourceRequestMode.NONE); var noParams = converter.bindInitial(none, Map.of()); var one = batch(none, noParams, scope(NONE, ""));
        badCoverage(none, noParams, Set.of(), List.of(one, one)); badCoverage(none, noParams, Set.of(), List.of());
        var date = api("daily", Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE); var dateOriginal = converter.bindInitial(date, range("20260901", "20260902"));
        badCoverage(date, dateOriginal, Set.of(), List.of(new DownloadBatchPlanner.PlannedBatch(scope(RANGE, "2026-09-01/2026-09-02"), new FetchBatch(range("20260901", "20260902"), date.downloadPolicy().recoveryPolicy()))));
    }

    @Test void coverageKeepsAllObjectConditionsAndOriginalRecoveryPolicy() {
        for (var common : List.of(parameter("ts_code", ParameterType.TS_CODE, null), enumeration("exchange_id", "SSE", "SZSE"), enumeration("exchange", "SSE", "SZSE"), enumeration("list_status", "L", "D"))) {
            var api = api("controlled", Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE, common);
            var value = common.name().equals("ts_code") ? "000001.SZ" : common.name().equals("list_status") ? "L" : "SSE";
            var raw = with(range("20260901", "20260901"), common.name(), value); var original = converter.bindInitial(api, raw); var exact = batch(api, original, scope(DATE, day(1).toString()));
            var deleted = new HashMap<>(raw); deleted.remove(common.name());
            for (var changed : List.of(deleted, with(raw, common.name(), common.name().equals("ts_code") ? "600000.SH" : "OTHER"), with(raw, "ann_date", "20260901"), with(raw, "other_stock", "600000.SH")))
                badCoverage(api, original, Set.of(), List.of(new DownloadBatchPlanner.PlannedBatch(exact.scope(), new FetchBatch(changed, exact.fetchBatch().recoveryPolicy()))));
            var independent = new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date", RANGE, true, List.of("docs/test.md"));
            badCoverage(api, original, Set.of(), List.of(new DownloadBatchPlanner.PlannedBatch(exact.scope(), new FetchBatch(raw, independent))));
            badCoverage(api, original, Set.of(), List.of(new DownloadBatchPlanner.PlannedBatch(new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ", DATE, day(1).toString()), exact.fetchBatch())));
        }
        var base = api("income", Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE, parameter("ts_code", ParameterType.TS_CODE, null));
        var p = base.downloadPolicy();
        var independent = new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date", RANGE, true, List.of("docs/test.md"));
        var policy = new DownloadPolicy(p.mode(), p.dateSemantic(), p.description(), p.calendarProfile(), p.limits(), p.sourceRequestMode(), p.sourceDateParameter(), p.requestEvidenceStatus(), p.batchPlanning(), independent, p.completenessPolicy(), p.calendarEvidenceStatus(), p.evidenceRefs());
        var api = new ApiDescriptor(base.apiName(), "Test", "Test", base.queryMode(), base.parameters(), policy, base.sourceParameters());
        for (String stock : List.of("000001.SZ", "600000.SH")) {
            var raw = with(range("20260901", "20260901"), "ts_code", stock); var plugin = new ControlledPlugin(api); plugin.advice.put(raw, BatchPlanning.SOURCE_RANGE);
            var plan = planner.planInitial(plugin, api, raw, () -> {});
            assertThat(plan.batches()).containsExactly(new DownloadBatchPlanner.PlannedBatch(scope(DATE, day(1).toString()), new FetchBatch(raw, independent)));
        }
    }

    private DownloadBatchPlanner.PlannedBatch batch(ApiDescriptor api, com.akkc.tensor.core.validation.ValidatedParameters original, RecoverySelector scope) {
        return new DownloadBatchPlanner.PlannedBatch(scope, new FetchBatch(converter.mapInitial(api, original, scope).sourceParams().values(), api.downloadPolicy().recoveryPolicy()));
    }
    private void badCoverage(ApiDescriptor api, com.akkc.tensor.core.validation.ValidatedParameters original, Set<LocalDate> skipped, List<DownloadBatchPlanner.PlannedBatch> batches) {
        assertThatThrownBy(() -> planner.validateCoverage(api, original, skipped, batches)).isInstanceOfSatisfying(SourceException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED); assertThat(e).hasMessage("Source request conditions are unconfirmed").hasNoCause(); assertThat(e.getSuppressed()).isEmpty();
        });
    }
    private static ParameterDescriptor enumeration(String name, String... values) { return new ParameterDescriptor(name, name, name, ParameterType.ENUM, true, null, List.of(values), null, null); }
    private static LocalDate day(int d) { return LocalDate.of(2026, 9, d); }
    private static Map<String,Object> with(Map<String,Object> base, String key, Object value) { var result = new HashMap<>(base); result.put(key, value); return result; }

    @Test void validatesIdentityAndReadinessBeforeCalendarOrSourcePlanning() {
        var api = api("daily", Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE);
        for (int variant : List.of(0, 1, 2)) {
            var plugin = new ControlledPlugin(api);
            if (variant == 0) plugin.declaredApi = new ApiDescriptor(api.apiName(), "Different", "Test", api.queryMode(), api.parameters(), api.downloadPolicy(), api.sourceParameters());
            if (variant == 1) plugin.includeDataset = false;
            if (variant == 2) plugin.available = false;
            assertThatThrownBy(() -> planner.planInitial(plugin, api, range("20260901", "20260901"), () -> plugin.events.add("check")))
                    .isInstanceOfSatisfying(TensorException.class, e -> {
                        assertThat(e.code()).isEqualTo(variant == 2 ? ErrorCode.PLUGIN_DISABLED : ErrorCode.DATASET_MISCONFIGURED);
                        assertThat(e).hasMessage(variant == 2 ? "Download plugin is unavailable" : "Download dataset is unavailable").hasNoCause();
                        assertThat(e.getSuppressed()).isEmpty();
                    });
            assertThat(plugin.events).containsExactly("check");
        }
        var plugin = new ControlledPlugin(api);
        assertThatThrownBy(() -> new DownloadBatchPlanner(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> planner.planInitial(null, api, range("20260901", "20260901"), () -> {})).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> planner.planInitial(plugin, null, Map.of(), () -> {})).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> planner.planInitial(plugin, api, null, () -> {})).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> planner.planInitial(plugin, api, Map.of(), null)).isInstanceOf(NullPointerException.class);
        assertThat(plugin.events).isEmpty();
    }

    @Test void conflictingRequestEvidenceRefusesBeforeAnySourcePlanning() {
        var base = api("forecast", Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE, parameter("ts_code", ParameterType.TS_CODE, null));
        var p = base.downloadPolicy();
        var policy = new DownloadPolicy(p.mode(), p.dateSemantic(), p.description(), p.calendarProfile(), p.limits(), p.sourceRequestMode(), p.sourceDateParameter(), RequestEvidenceStatus.CONFLICT, p.batchPlanning(), p.recoveryPolicy(), p.completenessPolicy(), p.calendarEvidenceStatus(), p.evidenceRefs());
        var api = new ApiDescriptor(base.apiName(), "Test", "Test", base.queryMode(), base.parameters(), policy, base.sourceParameters());
        var plugin = new ControlledPlugin(api);
        code(() -> planner.planInitial(plugin, api, with(range("20260901", "20260902"), "ts_code", "000001.SZ"), () -> {}), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertThat(plugin.events).isEmpty();
        var unknown = api("fina_mainbz", Mode.ANN_DATE_RANGE, null, parameter("ts_code", ParameterType.TS_CODE, null));
        var unknownPlugin = new ControlledPlugin(unknown);
        code(() -> planner.planInitial(unknownPlugin, unknown, with(range("20260901", "20260902"), "ts_code", "000001.SZ"), () -> {}), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertThat(unknownPlugin.events).isEmpty();
    }

    @Test void everyModeRejectsThirtyTwoDaysBeforeAnyCallback() {
        for (var mode : List.of(Mode.TRADE_DATE_RANGE, Mode.ANN_DATE_RANGE, Mode.NATIVE_RANGE, Mode.MONTH_RANGE)) {
            var api = api("controlled", mode, mode == Mode.MONTH_RANGE ? SourceRequestMode.MONTH : SourceRequestMode.RANGE);
            var plugin = new ControlledPlugin(api);
            code(() -> planner.planInitial(plugin, api, range("20260131", "20260303"), () -> { throw new AssertionError("context"); }), ErrorCode.PARAM_INVALID);
            assertThat(plugin.events).isEmpty();
        }
    }

    private static void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
    private static Map<String,Object> range(String start, String end) { return Map.of("start_date", start, "end_date", end); }
    private static RecoverySelector scope(RecoverySelector.TimeType type, String value) {
        return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", type, value);
    }
    private static ApiDescriptor api(String name, Mode mode, SourceRequestMode sourceMode, ParameterDescriptor... common) {
        var base = com.akkc.tensor.test.DownloadPolicies.original();
        String date = mode == Mode.TRADE_DATE_RANGE ? "trade_date" : mode == Mode.MONTH_RANGE ? "month" : "ann_date";
        var policy = new DownloadPolicy(mode, switch(mode) {
            case TRADE_DATE_RANGE -> DateSemantic.TRADE_DATE; case ANN_DATE_RANGE -> DateSemantic.ANN_DATE;
            case MONTH_RANGE -> DateSemantic.COVERED_MONTH; case NATIVE_RANGE -> DateSemantic.CALENDAR_DATE;
            case ORIGINAL_PARAMS -> DateSemantic.NONE;
        }, "Controlled planning", mode == Mode.TRADE_DATE_RANGE ? CalendarProfile.C_A : null,
                mode == Mode.ORIGINAL_PARAMS ? null : new Limits(31), sourceMode,
                sourceMode == SourceRequestMode.DATE || sourceMode == SourceRequestMode.MONTH ? date : null,
                sourceMode == null ? RequestEvidenceStatus.UNCONFIRMED : RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                sourceMode == null ? BatchPlanning.UNCONFIRMED : switch(sourceMode) {
                    case DATE -> BatchPlanning.SINGLE_DATE; case RANGE -> BatchPlanning.SOURCE_RANGE;
                    case MONTH -> BatchPlanning.SINGLE_MONTH; case NONE -> BatchPlanning.ORIGINAL_PARAMS;
                }, base.recoveryPolicy(), base.completenessPolicy(), mode == Mode.TRADE_DATE_RANGE ? CalendarEvidenceStatus.UNCONFIRMED : null, base.evidenceRefs());
        var source = new ArrayList<>(List.of(common));
        if (mode == Mode.NATIVE_RANGE) {
            source.add(parameter("start_date", ParameterType.DATE_RANGE_MEMBER, "end_date"));
            source.add(parameter("end_date", ParameterType.DATE_RANGE_MEMBER, "start_date"));
        } else if (mode != Mode.ORIGINAL_PARAMS) source.add(parameter(date, mode == Mode.MONTH_RANGE ? ParameterType.MONTH : ParameterType.DATE, null));
        return new ApiDescriptor(ApiName.of(name), "Test", "Test", QueryMode.snapshot,
                DownloadParameterProjection.project(source, policy), policy, source);
    }
    private static ParameterDescriptor parameter(String name, ParameterType type, String related) {
        return new ParameterDescriptor(name, name, name, type, true, null, List.of(), null, related);
    }
    private static final class ControlledPlugin implements DataSourcePlugin {
        final ApiDescriptor api;
        final List<String> events = new ArrayList<>();
        final List<FetchBatch> requests = new ArrayList<>();
        final Map<Map<String,Object>, BatchPlanning> advice = new HashMap<>();
        final Map<Map<String,Object>, RuntimeException> failures = new HashMap<>();
        final List<CalendarScope> calendarScopes = new ArrayList<>();
        Set<LocalDate> openDates;
        java.util.function.Function<CalendarScope, CalendarDecision> calendar;
        ApiDescriptor declaredApi;
        boolean includeDataset = true;
        boolean available = true;
        ControlledPlugin(ApiDescriptor api) { this.api = api; this.declaredApi = api; }
        public PluginDescriptor descriptor() {
            return new PluginDescriptor(PluginId.of("controlled"), "Test", "Test", true, true, true, null,
                    List.of(declaredApi), includeDataset ? List.of(new DatasetKey(PluginId.of("controlled"), api.apiName())) : List.of());
        }
        public PluginReadiness readiness() { return new PluginReadiness(available, true, available, available ? null : "Unavailable"); }
        public BatchPlanning planBatch(ApiName name, FetchBatch batch, DownloadContext context) {
            events.add("plan"); requests.add(batch);
            if (failures.containsKey(batch.sourceParams())) throw failures.get(batch.sourceParams());
            if (!advice.containsKey(batch.sourceParams())) throw new SourceException(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, "Source completeness is unconfirmed");
            return advice.get(batch.sourceParams());
        }
        public CalendarDecision confirmCalendar(ApiName name, CalendarScope scope, DownloadContext context) {
            events.add("calendar"); calendarScopes.add(scope);
            if (calendar != null) return calendar.apply(scope);
            if (openDates == null) throw new AssertionError("Unexpected calendar");
            var table = new HashMap<LocalDate,Boolean>(); scope.dates().forEach(d -> table.put(d, openDates.contains(d)));
            return new CalendarDecision(scope, Map.of("SSE", table));
        }
        public FetchResult fetchBatch(ApiName name, FetchBatch batch, DownloadContext context) { events.add("fetch"); throw new AssertionError("Business fetch during planning"); }
        public FetchResult download(ApiName name, Map<String,Object> params, DownloadContext context) { events.add("download"); throw new AssertionError("Business download during planning"); }
    }
}
