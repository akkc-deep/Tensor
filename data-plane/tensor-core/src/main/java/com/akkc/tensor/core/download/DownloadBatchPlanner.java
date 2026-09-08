package com.akkc.tensor.core.download;

import com.akkc.tensor.core.validation.ValidatedParameters;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.akkc.tensor.plugin.api.download.DownloadPolicy.*;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.*;

/** Plans frozen first requests without fetching business data or determining recovery units. */
public final class DownloadBatchPlanner {
    private final DownloadParameterConverter converter;

    public DownloadBatchPlanner(DownloadParameterConverter converter) {
        this.converter = Objects.requireNonNull(converter, "converter");
    }

    public Plan planInitial(DataSourcePlugin plugin, ApiDescriptor api,
            Map<String,Object> rawParams, DownloadContext context) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(rawParams, "rawParams");
        Objects.requireNonNull(context, "context");
        var original = converter.bindInitial(api, rawParams);
        context.checkServerState();
        var descriptor = plugin.descriptor();
        var key = new DatasetKey(descriptor.pluginId(), api.apiName());
        if (!descriptor.apis().contains(api) || !descriptor.datasets().contains(key)) {
            throw new UnavailableException(ErrorCode.DATASET_MISCONFIGURED, "Download dataset is unavailable");
        }
        if (!plugin.readiness().downloadAvailable()) {
            throw new UnavailableException(ErrorCode.PLUGIN_DISABLED, "Download plugin is unavailable");
        }
        var policy = api.downloadPolicy();
        var originalRange = originalRange(api, original);
        var dates = originalRange == null ? new TreeSet<LocalDate>()
                : dates(originalRange.startDate(), originalRange.endDate());
        var skipped = new HashSet<LocalDate>();
        if (policy.mode() == Mode.TRADE_DATE_RANGE) {
            var scope = new CalendarScope(original.values(), dates);
            context.checkServerState();
            var decision = plugin.confirmCalendar(api.apiName(), scope, context);
            context.checkServerState();
            if (decision == null || !scope.equals(decision.scope())) throw new CalendarUnconfirmedException();
            skipped.addAll(dates);
            skipped.removeAll(decision.openDates());
            dates.removeAll(skipped);
        }
        var batches = new ArrayList<PlannedBatch>();
        if (policy.mode() != Mode.TRADE_DATE_RANGE || !dates.isEmpty()) {
            if (policy.requestEvidenceStatus() != RequestEvidenceStatus.DOCUMENTED_CANDIDATE
                    || policy.sourceRequestMode() == null) throw requestUnconfirmed();
            List<RecoverySelector> candidates = switch (policy.mode()) {
                case ORIGINAL_PARAMS -> List.of(scope(NONE, ""));
                case MONTH_RANGE -> months(originalRange).stream().map(m -> scope(MONTH, m.toString())).toList();
                case NATIVE_RANGE -> List.of(dateScope(dates.first(), dates.last()));
                case TRADE_DATE_RANGE, ANN_DATE_RANGE -> policy.sourceRequestMode() == SourceRequestMode.DATE
                        ? dates.stream().map(d -> dateScope(d, d)).toList() : segments(dates);
            };
            for (var candidate : candidates) {
                precheck(plugin, api, original, originalRange, candidate, context, batches);
            }
        }
        validateCoverage(api, original, skipped, batches);
        context.checkServerState();
        return new Plan(key, original, originalRange, skipped, batches);
    }

    private void precheck(DataSourcePlugin plugin, ApiDescriptor api, ValidatedParameters original,
            DownloadParameterConverter.OriginalDateRange range, RecoverySelector scope,
            DownloadContext context, List<PlannedBatch> batches) {
        var mapped = converter.mapInitial(api, original, scope);
        if (!Objects.equals(range, mapped.originalDateRange())) throw requestUnconfirmed();
        var batch = new FetchBatch(mapped.sourceParams().values(), api.downloadPolicy().recoveryPolicy());
        context.checkServerState();
        var advice = plugin.planBatch(api.apiName(), batch, context);
        context.checkServerState();
        if (advice == null || advice == BatchPlanning.UNCONFIRMED) throw completenessUnconfirmed();
        var policy = api.downloadPolicy();
        boolean split = policy.sourceRequestMode() == SourceRequestMode.RANGE
                && (policy.mode() == Mode.TRADE_DATE_RANGE || policy.mode() == Mode.ANN_DATE_RANGE)
                && advice == BatchPlanning.SINGLE_DATE && scope.timeType() == RANGE;
        if (split) {
            for (LocalDate day : scopeDates(scope)) {
                precheck(plugin, api, original, range, dateScope(day, day), context, batches);
            }
            return;
        }
        boolean accepted = switch (policy.sourceRequestMode()) {
            case DATE -> advice == BatchPlanning.SINGLE_DATE;
            case MONTH -> advice == BatchPlanning.SINGLE_MONTH;
            case NONE -> advice == BatchPlanning.ORIGINAL_PARAMS;
            case RANGE -> advice == BatchPlanning.SOURCE_RANGE
                    || policy.mode() != Mode.NATIVE_RANGE && scope.timeType() == DATE
                    && advice == BatchPlanning.SINGLE_DATE;
        };
        if (!accepted) throw requestUnconfirmed();
        batches.add(new PlannedBatch(scope, batch));
    }

    void validateCoverage(ApiDescriptor api, ValidatedParameters original,
            Set<LocalDate> skippedDates, List<PlannedBatch> batches) {
        var policy = api.downloadPolicy();
        var range = originalRange(api, original);
        var expected = new TreeSet<String>();
        if (policy.mode() == Mode.ORIGINAL_PARAMS) {
            expected.add("");
        } else if (policy.mode() == Mode.MONTH_RANGE) {
            months(range).forEach(m -> expected.add(m.toString()));
        } else {
            var days = dates(range.startDate(), range.endDate());
            if (!days.containsAll(skippedDates)) throw requestUnconfirmed();
            days.removeAll(skippedDates);
            days.forEach(d -> expected.add(d.toString()));
        }
        if (policy.mode() != Mode.TRADE_DATE_RANGE && !skippedDates.isEmpty()
                || (policy.mode() == Mode.NATIVE_RANGE || policy.mode() == Mode.ORIGINAL_PARAMS)
                    && batches.size() != 1) throw requestUnconfirmed();
        var publicConditions = new HashMap<>(original.values());
        publicConditions.remove("start_date");
        publicConditions.remove("end_date");
        var object = Map.copyOf(publicConditions);
        var actual = new HashSet<Map.Entry<Map<String,Object>, String>>();
        String previous = null;
        for (var planned : batches) {
            var scope = planned.scope();
            if (scope.targetType() != RecoverySelector.TargetType.REQUEST || !scope.targetValue().isEmpty()
                    || !planned.fetchBatch().recoveryPolicy().equals(policy.recoveryPolicy())) throw requestUnconfirmed();
            if (policy.sourceRequestMode() == null) throw requestUnconfirmed();
            boolean shape = switch (policy.sourceRequestMode()) {
                case DATE -> scope.timeType() == DATE;
                case MONTH -> scope.timeType() == MONTH;
                case NONE -> scope.timeType() == NONE;
                case RANGE -> scope.timeType() == DATE || scope.timeType() == RANGE;
            };
            if (!shape) throw requestUnconfirmed();
            var mapped = converter.mapInitial(api, original, scope);
            if (!Objects.equals(mapped.originalDateRange(), range)
                    || !mapped.sourceParams().values().equals(planned.fetchBatch().sourceParams())) throw requestUnconfirmed();
            var atoms = scope.timeType() == DATE || scope.timeType() == RANGE
                    ? scopeDates(scope).stream().map(LocalDate::toString).toList() : List.of(scope.timeValue());
            for (String atom : atoms) {
                if (previous != null && previous.compareTo(atom) >= 0 || !actual.add(Map.entry(object, atom))) throw requestUnconfirmed();
                previous = atom;
            }
        }
        var expectedCoverage = new HashSet<Map.Entry<Map<String,Object>, String>>();
        expected.forEach(atom -> expectedCoverage.add(Map.entry(object, atom)));
        if (!actual.equals(expectedCoverage)) throw requestUnconfirmed();
    }

    private static DownloadParameterConverter.OriginalDateRange originalRange(ApiDescriptor api, ValidatedParameters original) {
        if (api.downloadPolicy().mode() == Mode.ORIGINAL_PARAMS) return null;
        return new DownloadParameterConverter.OriginalDateRange(
                LocalDate.parse((String) original.values().get("start_date"), DateTimeFormatter.BASIC_ISO_DATE),
                LocalDate.parse((String) original.values().get("end_date"), DateTimeFormatter.BASIC_ISO_DATE));
    }

    private static SortedSet<LocalDate> scopeDates(RecoverySelector scope) {
        String[] ends = scope.timeValue().split("/");
        return dates(LocalDate.parse(ends[0]), LocalDate.parse(ends[ends.length - 1]));
    }

    private static TreeSet<LocalDate> dates(LocalDate start, LocalDate end) {
        var result = new TreeSet<LocalDate>();
        for (LocalDate day = start;; day = day.plusDays(1)) {
            result.add(day);
            if (day.equals(end)) return result;
        }
    }

    private static List<YearMonth> months(DownloadParameterConverter.OriginalDateRange range) {
        var result = new ArrayList<YearMonth>();
        var end = YearMonth.from(range.endDate());
        for (var month = YearMonth.from(range.startDate());; month = month.plusMonths(1)) {
            result.add(month);
            if (month.equals(end)) return result;
        }
    }

    private static List<RecoverySelector> segments(SortedSet<LocalDate> dates) {
        var result = new ArrayList<RecoverySelector>();
        LocalDate start = null, end = null;
        for (LocalDate day : dates) {
            if (end != null && !end.plusDays(1).equals(day)) {
                result.add(dateScope(start, end));
                start = null;
            }
            if (start == null) start = day;
            end = day;
        }
        if (start != null) result.add(dateScope(start, end));
        return result;
    }

    private static RecoverySelector dateScope(LocalDate start, LocalDate end) {
        return start.equals(end) ? scope(DATE, start.toString()) : scope(RANGE, start + "/" + end);
    }

    private static RecoverySelector scope(RecoverySelector.TimeType type, String value) {
        return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", type, value);
    }

    private static SourceException requestUnconfirmed() {
        return new SourceException(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, "Source request conditions are unconfirmed");
    }

    private static SourceException completenessUnconfirmed() {
        return new SourceException(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, "Source completeness is unconfirmed");
    }

    private static final class UnavailableException extends TensorException {
        private UnavailableException(ErrorCode code, String message) { super(code, message); }
    }

    public record PlannedBatch(RecoverySelector scope, FetchBatch fetchBatch) {
        public PlannedBatch {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(fetchBatch, "fetchBatch");
        }
    }

    public record Plan(DatasetKey datasetKey, ValidatedParameters originalParams,
            DownloadParameterConverter.OriginalDateRange originalDateRange,
            Set<LocalDate> skippedDates, List<PlannedBatch> batches) {
        public Plan {
            Objects.requireNonNull(datasetKey, "datasetKey");
            Objects.requireNonNull(originalParams, "originalParams");
            skippedDates = Set.copyOf(skippedDates);
            batches = List.copyOf(batches);
        }
    }
}
