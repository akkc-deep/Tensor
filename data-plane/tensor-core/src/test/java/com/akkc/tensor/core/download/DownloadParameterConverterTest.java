package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.akkc.tensor.plugin.api.download.DownloadPolicy.*;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType.*;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.*;

import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class DownloadParameterConverterTest {
    private final DownloadParameterConverter converter = new DownloadParameterConverter(new ParameterValidator());

    @Test
    void freezesInputAndReconstructsOnlyNoncontiguousFailedDaysWithoutChangingDisplayRange() {
        var api = api(Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE, false, false);
        var raw = new LinkedHashMap<String, Object>(range("20260901", "20260910"));
        var original = converter.bindInitial(api, raw);
        var task = converter.taskParameters(api, original, REQUEST);
        raw.put("start_date", "20260902");
        assertThat(task).isEqualTo(range("20260901", "20260910"));
        assertThatThrownBy(() -> task.clear()).isInstanceOf(UnsupportedOperationException.class);
        for (String day : List.of("2026-09-03", "2026-09-07")) {
            var selector = request(DATE, day);
            var initial = converter.mapInitial(api, original, selector);
            assertThat(initial).isEqualTo(converter.mapRetry(api, task, selector));
            assertThat(initial.sourceParams().values()).isEqualTo(Map.of("trade_date", day.replace("-", "")));
            assertThat(initial.originalDateRange()).isEqualTo(new DownloadParameterConverter.OriginalDateRange(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)));
            assertThatThrownBy(() -> initial.sourceParams().values().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(original.values()).isEqualTo(task);
    }

    @Test
    void requestKeepsNormalizedStockAndNativeEndpointsWhileStockTasksCarryObjectOnlyInSelector() {
        var requestApi = api(Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE, true, false);
        var raw = new LinkedHashMap<String, Object>(range("20260901", "20260910"));
        raw.put("ts_code", " 000001.sz ");
        var original = converter.bindInitial(requestApi, raw);
        var task = converter.taskParameters(requestApi, original, REQUEST);
        assertThat(task).containsEntry("ts_code", "000001.SZ");
        var selection = request(RANGE, "2026-09-03/2026-09-07");
        assertThat(converter.mapInitial(requestApi, original, selection).sourceParams().values())
                .isEqualTo(Map.of("ts_code", "000001.SZ", "start_date", "20260903", "end_date", "20260907"));
        var stockApi = api(Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE, true, true);
        var fallback = converter.taskParameters(stockApi, original, REQUEST);
        assertThat(fallback).isEqualTo(task);
        assertThat(converter.mapRetry(stockApi, fallback, selection).sourceParams().values())
                .isEqualTo(Map.of("ts_code", "000001.SZ", "start_date", "20260903", "end_date", "20260907"));
        for (String code : List.of("000001.SZ", "600000.SH")) {
            raw.put("ts_code", code);
            var stockOriginal = converter.bindInitial(stockApi, raw);
            var stockTask = converter.taskParameters(stockApi, stockOriginal, STOCK);
            assertThat(stockTask).isEqualTo(range("20260901", "20260910"));
            var stock = new RecoverySelector(STOCK, code, DATE, "2026-09-03");
            var expected = Map.of("ts_code", code, "start_date", "20260903", "end_date", "20260903");
            assertThat(converter.mapInitial(stockApi, stockOriginal, stock).sourceParams().values()).isEqualTo(expected);
            assertThat(converter.mapRetry(stockApi, stockTask, stock).sourceParams().values()).isEqualTo(expected);
        }
        var stock = new RecoverySelector(STOCK, "600000.SH", DATE, "2026-09-03");
        assertCode(() -> converter.mapInitial(stockApi, original, stock), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertCode(() -> converter.mapRetry(stockApi, task, stock), ErrorCode.RETRY_TASK_INVALID);
        assertCode(() -> converter.mapRetry(requestApi, task, stock), ErrorCode.RETRY_TASK_INVALID);
    }

    @Test
    void monthRemainsWholeEvenOutsideDisplayDaysAndInitialUnitsCannotExceedInput() {
        var monthApi = api(Mode.MONTH_RANGE, SourceRequestMode.MONTH, false, false);
        var original = converter.bindInitial(monthApi, range("20260131", "20260302"));
        for (String month : List.of("2026-01", "2026-02", "2026-03")) {
            assertThat(converter.mapInitial(monthApi, original, request(MONTH, month)).sourceParams().values())
                    .isEqualTo(Map.of("month", month.replace("-", "")));
        }
        assertCode(() -> converter.mapInitial(monthApi, original, request(MONTH, "2026-04")), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        var rangeApi = api(Mode.NATIVE_RANGE, SourceRequestMode.RANGE, false, false);
        var dateOriginal = converter.bindInitial(rangeApi, range("20260901", "20260910"));
        for (RecoverySelector selector : List.of(request(DATE, "2026-08-31"), request(DATE, "2026-09-11"),
                request(RANGE, "2026-08-31/2026-09-03"), request(RANGE, "2026-09-07/2026-09-11"), request(NONE, ""))) {
            assertCode(() -> converter.mapInitial(rangeApi, dateOriginal, selector), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        }
        assertThat(converter.mapRetry(rangeApi, dateOriginal.values(), request(DATE, "2026-09-11")).sourceParams().values())
                .isEqualTo(range("20260911", "20260911"));
    }

    @Test
    void legacyMissingBothDisplayDatesIsAllowedButMalformedSavedDatesAreRejectedSafely() {
        var api = api(Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE, false, false);
        var selector = request(DATE, "2026-09-03");
        assertThat(converter.mapRetry(api, Map.of(), selector).originalDateRange()).isNull();
        List<Map<String, Object>> invalid = new ArrayList<>(List.of(
                Map.of("start_date", "20260901"), Map.of("end_date", "20260910"),
                range("00000101", "00000102"), range("20260229", "20260301"),
                range("20260910", "20260901"), range("20260131", "20260303"),
                range(" 20260901", "20260910"), Map.of("start_date", 20260901, "end_date", "20260910")));
        var nullDate = new LinkedHashMap<String, Object>(range("20260901", "20260910"));
        nullDate.put("start_date", null); invalid.add(nullDate);
        for (var task : invalid) {
            var snapshot = new LinkedHashMap<>(task);
            assertCode(() -> converter.mapRetry(api, task, selector), ErrorCode.RETRY_TASK_INVALID);
            assertThat(task).isEqualTo(snapshot);
        }
    }

    @Test
    void rejectsOldTimeKeysUnknownFieldsBadTypesAndMissingRequiredConditionsWithoutEcho() {
        var api = api(Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE, true, false);
        var selector = request(DATE, "2026-09-03");
        for (var task : List.<Map<String, Object>>of(Map.of(), Map.of("ts_code", "SECRET"),
                Map.of("ts_code", "000001.SZ", "ann_date", "20260903"),
                Map.of("ts_code", "000001.SZ", "trade_date", "20260903"),
                Map.of("ts_code", "000001.SZ", "month", "202609"),
                Map.of("ts_code", "000001.SZ", "token", "SECRET"), Map.of("ts_code", 12))) {
            assertCode(() -> converter.mapRetry(api, task, selector), ErrorCode.RETRY_TASK_INVALID);
        }
        var unsafe = new LinkedHashMap<String, Object>(); unsafe.put(null, "SECRET");
        assertCode(() -> converter.mapRetry(api, unsafe, selector), ErrorCode.RETRY_TASK_INVALID);
    }

    @Test
    void originalParamsKeepConditionsAndNeverAcceptDisplayDatesOrTimeSelectors() {
        var api = api(Mode.ORIGINAL_PARAMS, SourceRequestMode.NONE, false, false);
        var original = converter.bindInitial(api, Map.of());
        assertThat(converter.taskParameters(api, original, REQUEST)).isEmpty();
        var result = converter.mapInitial(api, original, request(NONE, ""));
        assertThat(result.sourceParams().values()).isEmpty();
        assertThat(result.originalDateRange()).isNull();
        assertCode(() -> converter.mapRetry(api, range("20260901", "20260910"), request(NONE, "")), ErrorCode.RETRY_TASK_INVALID);
        assertCode(() -> converter.mapRetry(api, Map.of(), request(DATE, "2026-09-03")), ErrorCode.RETRY_TASK_INVALID);
    }

    @Test
    void preservesSourceEvidenceErrorsWithoutConfusingThemWithInputErrors() {
        var base = api(Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE, false, false);
        var p = base.downloadPolicy();
        for (var status : List.of(RequestEvidenceStatus.UNCONFIRMED, RequestEvidenceStatus.CONFLICT)) {
            var policy = new DownloadPolicy(p.mode(), p.dateSemantic(), p.description(), p.calendarProfile(), p.limits(),
                    status == RequestEvidenceStatus.UNCONFIRMED ? null : p.sourceRequestMode(),
                    status == RequestEvidenceStatus.UNCONFIRMED ? null : p.sourceDateParameter(), status,
                    status == RequestEvidenceStatus.UNCONFIRMED ? BatchPlanning.UNCONFIRMED : p.batchPlanning(),
                    p.recoveryPolicy(), p.completenessPolicy(), p.calendarEvidenceStatus(), p.evidenceRefs());
            var api = new ApiDescriptor(base.apiName(), "Test", "Test", base.queryMode(), base.parameters(), policy, base.sourceParameters());
            var initial = converter.bindInitial(api, range("20260901", "20260910"));
            assertCode(() -> converter.mapInitial(api, initial, request(DATE, "2026-09-03")), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
            assertCode(() -> converter.mapRetry(api, initial.values(), request(DATE, "2026-09-03")), ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        }
    }

    @Test
    void originalDateRangeEnforcesOrderedNonNull31DayBounds() {
        assertThatThrownBy(() -> new DownloadParameterConverter.OriginalDateRange(null, LocalDate.now())).isInstanceOf(IllegalArgumentException.class);
        for (var endpoints : List.of(List.of("2026-09-10", "2026-09-01"), List.of("2026-01-31", "2026-03-03"))) {
            assertThatThrownBy(() -> new DownloadParameterConverter.OriginalDateRange(
                    LocalDate.parse(endpoints.get(0)), LocalDate.parse(endpoints.get(1)))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(TensorException.class, failure -> {
            assertThat(failure.code()).isEqualTo(code);
            assertThat(failure).hasMessage(code == ErrorCode.RETRY_TASK_INVALID
                    ? "Saved download parameters are incompatible" : "Source request conditions are unconfirmed").hasNoCause();
        });
    }
    private static Map<String, Object> range(String start, String end) { return Map.of("start_date", start, "end_date", end); }
    private static RecoverySelector request(RecoverySelector.TimeType time, String value) { return new RecoverySelector(REQUEST, "", time, value); }
    private static ParameterDescriptor parameter(String name, ParameterType type, String related) {
        return new ParameterDescriptor(name, name, name, type, true, null, List.of(), null, related);
    }
    private static ApiDescriptor api(Mode mode, SourceRequestMode sourceMode, boolean stockInput, boolean independent) {
        var base = com.akkc.tensor.test.DownloadPolicies.original();
        var recovery = independent ? new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date", RANGE, true, List.of("docs/test.md")) : base.recoveryPolicy();
        String sourceDate = mode == Mode.TRADE_DATE_RANGE ? "trade_date" : mode == Mode.MONTH_RANGE ? "month" : "ann_date";
        var policy = new DownloadPolicy(mode, switch(mode) {
            case TRADE_DATE_RANGE -> DateSemantic.TRADE_DATE; case ANN_DATE_RANGE -> DateSemantic.ANN_DATE;
            case MONTH_RANGE -> DateSemantic.COVERED_MONTH; case NATIVE_RANGE -> DateSemantic.CALENDAR_DATE;
            case ORIGINAL_PARAMS -> DateSemantic.NONE;
        }, "Controlled mapping", mode == Mode.TRADE_DATE_RANGE ? CalendarProfile.C_A : null,
                mode == Mode.ORIGINAL_PARAMS ? null : new Limits(31), sourceMode,
                sourceMode == SourceRequestMode.DATE || sourceMode == SourceRequestMode.MONTH ? sourceDate : null,
                RequestEvidenceStatus.DOCUMENTED_CANDIDATE, switch(sourceMode) {
                    case DATE -> BatchPlanning.SINGLE_DATE; case RANGE -> BatchPlanning.SOURCE_RANGE;
                    case MONTH -> BatchPlanning.SINGLE_MONTH; case NONE -> BatchPlanning.ORIGINAL_PARAMS;
                }, recovery, base.completenessPolicy(), mode == Mode.TRADE_DATE_RANGE ? CalendarEvidenceStatus.UNCONFIRMED : null, base.evidenceRefs());
        var source = new ArrayList<ParameterDescriptor>();
        if (stockInput) source.add(parameter("ts_code", ParameterType.TS_CODE, null));
        if (mode == Mode.NATIVE_RANGE) {
            source.add(parameter("start_date", ParameterType.DATE_RANGE_MEMBER, "end_date"));
            source.add(parameter("end_date", ParameterType.DATE_RANGE_MEMBER, "start_date"));
        } else if (mode != Mode.ORIGINAL_PARAMS) source.add(parameter(sourceDate, mode == Mode.MONTH_RANGE ? ParameterType.MONTH : ParameterType.DATE, null));
        return new ApiDescriptor(new ApiName("controlled"), "Test", "Test", QueryMode.snapshot,
                DownloadParameterProjection.project(source, policy), policy, source);
    }
}
