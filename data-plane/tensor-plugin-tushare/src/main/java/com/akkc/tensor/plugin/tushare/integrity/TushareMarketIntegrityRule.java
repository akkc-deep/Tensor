package com.akkc.tensor.plugin.tushare.integrity;

import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Consumer;

import static com.akkc.tensor.plugin.api.integrity.IntegrityExpectedKeys.Basis.UNCONFIRMED;
import static com.akkc.tensor.plugin.api.integrity.IntegrityIssue.Type.REFERENCE_INCOMPLETE;

/** Bounded local market-calendar candidate derivation; it never treats candidates as proven. */
final class TushareMarketIntegrityRule implements IntegrityRule {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private final DatasetKey datasetKey;
    private final IntegrityRuleDescriptor descriptor;

    TushareMarketIntegrityRule(DatasetKey datasetKey, IntegrityRuleDescriptor descriptor) {
        this.datasetKey = Objects.requireNonNull(datasetKey, "datasetKey");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override public IntegrityRuleDescriptor descriptor() { return descriptor; }

    static List<IntegrityReadRequest> referenceReads(IntegrityScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (!scope.datasetKey().pluginId().equals(PluginId.of("tushare_pro")))
            throw new IllegalArgumentException("Foreign Tushare integrity scope");
        requireSymbol(scope.symbol());
        String api = scope.datasetKey().apiName().value();
        if (!Set.of("daily", "weekly", "monthly").contains(api) || scope.symbol().endsWith(".BJ")) return List.of();
        String exchange = scope.symbol().endsWith(".SH") ? "SSE" : "SZSE";
        IntegrityDateRange calendarRange = switch (api) {
            case "weekly" -> new IntegrityDateRange(
                    scope.startDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    scope.endDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            case "monthly" -> new IntegrityDateRange(scope.startDate().withDayOfMonth(1),
                    scope.endDate().with(TemporalAdjusters.lastDayOfMonth()));
            default -> scope.range();
        };
        IntegrityDependency calendar = dependency("trade_cal");
        IntegrityDependency listing = dependency("stock_basic");
        IntegrityDependency suspension = dependency("suspend_d");
        return List.of(
                new IntegrityReadRequest(calendar.datasetKey(), calendar.columns(), Map.of("exchange", exchange),
                        "cal_date", calendarRange, false, calendar.purpose()),
                new IntegrityReadRequest(listing.datasetKey(), listing.columns(), Map.of(), null, null, false, listing.purpose()),
                new IntegrityReadRequest(suspension.datasetKey(), suspension.columns(), Map.of(), "trade_date",
                        scope.range(), false, suspension.purpose()));
    }

    @Override
    public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink issues) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(issues, "issues");
        if (scope == null || !datasetKey.equals(scope.datasetKey()) || scope.snapshotStartedAt() == null)
            throw new IllegalArgumentException("Market coverage requires a started scope for its dataset");
        requireSymbol(scope.symbol());

        if (scope.symbol().endsWith(".BJ")) {
            var evidence = List.of(evidence(scope, scope.range(), "BJ 缺少已证明适用的本地交易日历；未生成候选"));
            issues.add(issue(scope, null, Map.of(), "CALENDAR_BASIS_UNPROVEN", "BJ 交易日历适用依据不足", evidence));
            IntegrityStatistics statistics = context.compare(new IntegrityExpectedKeys(scope, UNCONFIRMED, List.of(), evidence));
            return result("CALENDAR_BASIS_UNPROVEN", statistics, evidence);
        }

        List<IntegrityReadRequest> requests = referenceReads(scope);
        var calendarRows = new ArrayList<Map<String, Object>>();
        var listingRows = new ArrayList<Map<String, Object>>();
        scan(context, requests.get(0), calendarRows);
        scan(context, requests.get(1), listingRows);

        IntegrityDateRange calendarRange = requests.getFirst().dateRange();
        String exchange = (String) requests.getFirst().equalities().get("exchange");
        var calendar = validateCalendar(scope, calendarRange, exchange, calendarRows, issues);
        LocalDate readDate = scope.snapshotStartedAt().atZone(MARKET_ZONE).toLocalDate();
        var candidates = candidates(scope, calendarRange, calendar.validDays, readDate, issues);

        LocalDate listDate = listingDate(scope, listingRows);
        if (listDate == null) addRangeIssue(scope, issues, "LISTING_HISTORY_UNPROVEN", "上市日期线索缺失或矛盾", scope.range());
        else candidates.removeIf(date -> date.isBefore(listDate));
        addRangeIssue(scope, issues, "LIFECYCLE_UNPROVEN", "当前股票快照不能证明完整历史生命周期", scope.range());
        int suspensionCount = scanSuspensions(context, requests.get(2), scope, issues, candidates);
        addRangeIssue(scope, issues, "SUSPENSION_BASIS_UNPROVEN", "停复牌记录仅作线索，不能证明停牌全集", scope.range());
        addRangeIssue(scope, issues, "SERVICE_BOUNDARY_UNPROVEN", "来源服务历史边界未经证明", scope.range());
        addRangeIssue(scope, issues, "PUBLISH_TIME_UNPROVEN", "来源发布时间未经证明", scope.range());

        candidates.sort(Comparator.naturalOrder());
        String summary = "dependencies=" + dependencySummary(requests) + "; trade_calRange="
                + calendarRange.startDate() + ".." + calendarRange.endDate() + ",exchange=" + exchange
                + "; suspendRange=" + scope.startDate() + ".." + scope.endDate()
                + "; snapshot=" + scope.snapshotStartedAt() + "; ruleVersion=" + descriptor.version()
                + "; calendarRows=" + calendarRows.size() + "; suspensionRows=" + suspensionCount
                + "; lifecycle/service/publish/suspension basis unproven";
        var evidence = List.of(evidence(scope, calendarRange, summary));
        List<Map<String, Object>> keys = candidates.stream()
                .map(date -> Map.<String, Object>of("ts_code", scope.symbol(), "trade_date", date)).toList();
        IntegrityStatistics statistics = context.compare(new IntegrityExpectedKeys(scope, UNCONFIRMED, keys, evidence));
        return result(calendar.incomplete ? "REFERENCE_INCOMPLETE" : "EXPECTED_SET_UNPROVEN", statistics, evidence);
    }

    private static void scan(IntegrityContext context, IntegrityReadRequest request, List<Map<String, Object>> result) {
        context.scan(request, rows -> result.addAll(List.copyOf(rows)));
    }

    private CalendarResult validateCalendar(IntegrityScope scope, IntegrityDateRange range, String exchange,
            List<Map<String, Object>> rows, IntegrityIssueSink issues) {
        if (rows.isEmpty()) {
            addRangeIssue(scope, issues, "REFERENCE_INCOMPLETE", "交易日历参考窗口为空", range);
            return new CalendarResult(Map.of(), true);
        }
        var indexed = new HashMap<LocalDate, List<Map<String, Object>>>();
        boolean incomplete = false;
        for (var row : rows) {
            Object value = row.get("cal_date");
            if (!(value instanceof LocalDate date) || date.isBefore(range.startDate()) || date.isAfter(range.endDate())) {
                incomplete = true;
                addRangeIssue(scope, issues, "REFERENCE_INCOMPLETE", "交易日历包含无效日期", range);
            } else indexed.computeIfAbsent(date, ignored -> new ArrayList<>()).add(row);
        }
        var valid = new LinkedHashMap<LocalDate, Boolean>();
        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
            List<Map<String, Object>> found = indexed.getOrDefault(date, List.of());
            boolean cellValid = found.size() == 1 && Objects.equals(found.getFirst().get("exchange"), exchange)
                    && (Objects.equals(found.getFirst().get("is_open"), 0L)
                    || Objects.equals(found.getFirst().get("is_open"), 1L));
            if (!cellValid) {
                incomplete = true;
                addCalendarIssue(scope, issues, date, range,
                        found.isEmpty() ? "交易日历缺少自然日" : found.size() > 1 ? "交易日历日期重复" : "交易日历字段无效");
            } else valid.put(date, Objects.equals(found.getFirst().get("is_open"), 1L));
        }
        return new CalendarResult(valid, incomplete);
    }

    private List<LocalDate> candidates(IntegrityScope scope, IntegrityDateRange reference,
            Map<LocalDate, Boolean> calendar, LocalDate readDate, IntegrityIssueSink issues) {
        var result = new ArrayList<LocalDate>();
        String api = datasetKey.apiName().value();
        if (api.equals("daily")) {
            for (LocalDate date = scope.startDate(); !date.isAfter(scope.endDate()); date = date.plusDays(1)) {
                if (!date.isBefore(readDate)) {
                    addPeriodIssue(scope, issues, new IntegrityDateRange(date, date));
                } else if (Boolean.TRUE.equals(calendar.get(date))) result.add(date);
            }
            return result;
        }
        LocalDate period = reference.startDate();
        while (!period.isAfter(reference.endDate())) {
            LocalDate end = api.equals("weekly") ? period.plusDays(6)
                    : period.with(TemporalAdjusters.lastDayOfMonth());
            IntegrityDateRange natural = new IntegrityDateRange(period, end);
            IntegrityDateRange intersection = intersection(natural, scope.range());
            if (intersection != null && !end.isBefore(readDate)) {
                addPeriodIssue(scope, issues, intersection);
            } else if (intersection != null && complete(calendar, natural)) {
                LocalDate marker = lastOpen(calendar, natural);
                if (marker != null && !marker.isBefore(scope.startDate()) && !marker.isAfter(scope.endDate())) result.add(marker);
            }
            period = api.equals("weekly") ? period.plusWeeks(1) : period.plusMonths(1).withDayOfMonth(1);
        }
        return result;
    }

    private static boolean complete(Map<LocalDate, Boolean> calendar, IntegrityDateRange range) {
        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1))
            if (!calendar.containsKey(date)) return false;
        return true;
    }

    private static LocalDate lastOpen(Map<LocalDate, Boolean> calendar, IntegrityDateRange range) {
        for (LocalDate date = range.endDate(); !date.isBefore(range.startDate()); date = date.minusDays(1))
            if (Boolean.TRUE.equals(calendar.get(date))) return date;
        return null;
    }

    private static IntegrityDateRange intersection(IntegrityDateRange left, IntegrityDateRange right) {
        LocalDate start = left.startDate().isAfter(right.startDate()) ? left.startDate() : right.startDate();
        LocalDate end = left.endDate().isBefore(right.endDate()) ? left.endDate() : right.endDate();
        return start.isAfter(end) ? null : new IntegrityDateRange(start, end);
    }

    private static LocalDate listingDate(IntegrityScope scope, List<Map<String, Object>> rows) {
        if (rows.size() != 1 || !Objects.equals(rows.getFirst().get("ts_code"), scope.symbol())
                || !(rows.getFirst().get("list_date") instanceof LocalDate date)) return null;
        return date;
    }

    private int scanSuspensions(IntegrityContext context, IntegrityReadRequest request, IntegrityScope scope,
            IntegrityIssueSink issues, List<LocalDate> candidates) {
        Set<LocalDate> dates = Set.copyOf(candidates);
        int[] count = {0};
        context.scan(request, rows -> {
            count[0] += rows.size();
            rows.stream().filter(row -> row.get("trade_date") instanceof LocalDate date && dates.contains(date))
                .forEach(row -> {
                    LocalDate date = (LocalDate) row.get("trade_date");
                    String summary = "停牌线索 date=" + date + ",suspend_type=" + row.get("suspend_type")
                            + ",suspend_timing=" + row.get("suspend_timing");
                    var evidence = List.of(evidence(scope, scope.range(), summary));
                    issues.add(issue(scope, null, Map.of("trade_date", date),
                            "SUSPENSION_BASIS_UNPROVEN", "停牌记录仅作候选日期线索", evidence));
                });
        });
        return count[0];
    }

    private static String dependencySummary(List<IntegrityReadRequest> requests) {
        return requests.stream().map(request -> request.datasetKey().pluginId().value() + ":"
                + request.datasetKey().apiName().value() + " purpose=" + request.purpose()).toList().toString();
    }

    private void addPeriodIssue(IntegrityScope scope, IntegrityIssueSink issues, IntegrityDateRange range) {
        addRangeIssue(scope, issues, "PERIOD_NOT_FINISHED", "读取时周期尚未结束，不生成候选", range);
    }

    private void addCalendarIssue(IntegrityScope scope, IntegrityIssueSink issues, LocalDate date,
            IntegrityDateRange range, String message) {
        boolean targetDate = !date.isBefore(scope.startDate()) && !date.isAfter(scope.endDate());
        var evidence = List.of(evidence(scope, range, message + ": " + date));
        issues.add(issue(scope, targetDate ? date : null, targetDate ? Map.of() : Map.of("cal_date", date),
                "REFERENCE_INCOMPLETE", message, evidence));
    }

    private void addRangeIssue(IntegrityScope scope, IntegrityIssueSink issues, String reason,
            String message, IntegrityDateRange range) {
        var evidence = List.of(evidence(scope, range, message));
        issues.add(issue(scope, null, Map.of(), reason, message, evidence));
    }

    private IntegrityIssue issue(IntegrityScope scope, LocalDate date, Map<String, LocalDate> related,
            String reason, String message, List<IntegrityEvidence> evidence) {
        return new IntegrityIssue(REFERENCE_INCOMPLETE, IntegrityStatus.UNKNOWN, scope.symbol(),
                scope.datasetKey().apiName(), "trade_date", date, Map.of(), null, related,
                reason, message, evidence, false);
    }

    private IntegrityEvidence evidence(IntegrityScope scope, IntegrityDateRange range, String summary) {
        return new IntegrityEvidence("tushare-local-reference:" + datasetKey.apiName().value(), descriptor.version(),
                range, scope.snapshotStartedAt(), summary);
    }

    private IntegrityRuleResult result(String reason, IntegrityStatistics statistics, List<IntegrityEvidence> evidence) {
        return new IntegrityRuleResult(descriptor, IntegrityStatus.UNKNOWN, reason, descriptor.description(), statistics, evidence);
    }

    private static DatasetKey key(String api) {
        return new DatasetKey(PluginId.of("tushare_pro"), ApiName.of(api));
    }

    private static IntegrityDependency dependency(String api) {
        return TushareIntegrityPolicies.MARKET_DEPENDENCIES.stream()
                .filter(value -> value.datasetKey().equals(key(api))).findFirst().orElseThrow();
    }

    private static void requireSymbol(String symbol) {
        if (symbol == null || !symbol.matches("[0-9]{6}[.](SH|SZ|BJ)"))
            throw new IllegalArgumentException("Invalid Tushare stock code");
    }

    private record CalendarResult(Map<LocalDate, Boolean> validDays, boolean incomplete) {}
}
