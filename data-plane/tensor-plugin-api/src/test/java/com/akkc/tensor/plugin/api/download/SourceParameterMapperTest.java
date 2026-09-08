package com.akkc.tensor.plugin.api.download;

import static com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType.REQUEST;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType.STOCK;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.DATE;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.MONTH;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.NONE;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.RANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.BatchPlanning;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.CalendarEvidenceStatus;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.CompletenessPolicy;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.CompletenessStatus;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.DateSemantic;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.RequestEvidenceStatus;
import com.akkc.tensor.plugin.api.download.DownloadPolicy.SourceRequestMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceParameterMapperTest {
    private static final String INCOMPATIBLE = "Source parameters are incompatible";

    @Test
    void mapsEveryDocumentedRequestTimeShapeExactly() {
        ApiDescriptor daily = api(Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE, "trade_date",
                requestRecovery(), List.of(parameter("trade_date", ParameterType.DATE)), rangeParameters());
        assertMapped(daily, Map.of(), selector(REQUEST, "", DATE, "2026-09-03"),
                Map.of("trade_date", "20260903"), daily.sourceParameters());

        ApiDescriptor margin = api(Mode.TRADE_DATE_RANGE, SourceRequestMode.RANGE, null,
                requestRecovery(), List.of(parameter("exchange_id", ParameterType.TEXT),
                        parameter("trade_date", ParameterType.DATE)),
                with(parameter("exchange_id", ParameterType.TEXT), rangeParameters()));
        assertMapped(margin, Map.of("exchange_id", "SSE"), selector(REQUEST, "", DATE, "2026-09-03"),
                Map.of("exchange_id", "SSE", "start_date", "20260903", "end_date", "20260903"),
                margin.parameters());

        ApiDescriptor income = api(Mode.NATIVE_RANGE, SourceRequestMode.RANGE, null,
                requestRecovery(), with(parameter("ts_code", ParameterType.TS_CODE), rangeParameters()),
                with(parameter("ts_code", ParameterType.TS_CODE), rangeParameters()));
        assertMapped(income, Map.of("ts_code", "000001.SZ"),
                selector(REQUEST, "", RANGE, "2026-09-03/2026-09-07"),
                Map.of("ts_code", "000001.SZ", "start_date", "20260903", "end_date", "20260907"),
                income.parameters());

        ApiDescriptor broker = api(Mode.MONTH_RANGE, SourceRequestMode.MONTH, "month",
                requestRecovery(), List.of(parameter("month", ParameterType.MONTH)), rangeParameters());
        assertMapped(broker, Map.of(), selector(REQUEST, "", MONTH, "2026-02"),
                Map.of("month", "202602"), broker.sourceParameters());

        ApiDescriptor stockBasic = api(Mode.ORIGINAL_PARAMS, SourceRequestMode.NONE, null,
                requestRecovery(), List.of(parameter("list_status", ParameterType.ENUM)),
                List.of(parameter("list_status", ParameterType.ENUM)));
        assertMapped(stockBasic, Map.of("list_status", "L"), selector(REQUEST, "", NONE, ""),
                Map.of("list_status", "L"), stockBasic.sourceParameters());
    }

    @Test
    void nativeRangeSingleDaysAlwaysUseEqualEndpoints() {
        for (Map<String, Object> common : List.<Map<String, Object>>of(
                Map.of("exchange", "SSE"), Map.of())) {
            ApiDescriptor api = nativeRange(common.keySet().stream()
                    .map(name -> parameter(name, ParameterType.TEXT)).toList(), requestRecovery());
            var mapped = SourceParameterMapper.map(api, common, selector(REQUEST, "", DATE, "2026-09-03"));
            assertThat(mapped.values()).containsEntry("start_date", "20260903")
                    .containsEntry("end_date", "20260903")
                    .doesNotContainKeys("trade_date", "ann_date");
        }
    }

    @Test
    void rejectsUnconfirmedEvidenceBeforeInspectingOtherInputs() {
        ApiDescriptor unconfirmed = api(Mode.TRADE_DATE_RANGE, null, null, requestRecovery(),
                List.of(parameter("trade_date", ParameterType.DATE)), rangeParameters(),
                RequestEvidenceStatus.UNCONFIRMED);
        assertThatThrownBy(() -> SourceParameterMapper.map(unconfirmed, null, null))
                .isInstanceOf(SourceException.class)
                .hasMessage("Source request conditions are unconfirmed")
                .satisfies(error -> assertThat(((SourceException) error).code())
                        .isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED));

        ApiDescriptor conflict = api(Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE, "trade_date",
                requestRecovery(), List.of(parameter("trade_date", ParameterType.DATE)), rangeParameters(),
                RequestEvidenceStatus.CONFLICT);
        assertThatThrownBy(() -> SourceParameterMapper.map(conflict,
                Map.of("secret", new Object()), selector(REQUEST, "", DATE, "2026-09-03")))
                .isInstanceOf(SourceException.class)
                .hasMessage("Source request conditions are unconfirmed");
    }

    @Test
    void rejectsUnknownOldNonStringAndConflictingCommonConditions() {
        ApiDescriptor range = nativeRange(List.of(parameter("exchange", ParameterType.TEXT)), requestRecovery());
        var invalid = new ArrayList<Map<String, Object>>();
        invalid.add(Map.of("unknown", "value"));
        invalid.add(Map.of("start_date", "20260901"));
        invalid.add(Map.of("end_date", "20260910"));
        invalid.add(Map.of("trade_date", "20260903"));
        invalid.add(Map.of("ann_date", "20260903"));
        invalid.add(Map.of("month", "202609"));
        invalid.add(Map.of("exchange", 1));
        var nullValue = new HashMap<String, Object>();
        nullValue.put("exchange", null);
        invalid.add(nullValue);
        var nullKey = new HashMap<String, Object>();
        nullKey.put(null, "SSE");
        invalid.add(nullKey);

        for (Map<String, Object> common : invalid) {
            assertIncompatible(() -> SourceParameterMapper.map(range, common,
                    selector(REQUEST, "", DATE, "2026-09-03")));
        }
        assertIncompatible(() -> SourceParameterMapper.map(range, null,
                selector(REQUEST, "", DATE, "2026-09-03")));
    }

    @Test
    void rejectsEveryIncompatibleSourceAndSelectorTimeCombination() {
        ApiDescriptor date = api(Mode.ANN_DATE_RANGE, SourceRequestMode.DATE, "ann_date",
                requestRecovery(), List.of(parameter("ann_date", ParameterType.DATE)), rangeParameters());
        ApiDescriptor month = api(Mode.MONTH_RANGE, SourceRequestMode.MONTH, "month",
                requestRecovery(), List.of(parameter("month", ParameterType.MONTH)), rangeParameters());
        ApiDescriptor range = nativeRange(List.of(), requestRecovery());
        ApiDescriptor none = api(Mode.ORIGINAL_PARAMS, SourceRequestMode.NONE, null,
                requestRecovery(), List.of(), List.of());

        assertIncompatible(() -> SourceParameterMapper.map(date, Map.of(),
                selector(REQUEST, "", RANGE, "2026-09-03/2026-09-07")));
        assertIncompatible(() -> SourceParameterMapper.map(month, Map.of(),
                selector(REQUEST, "", DATE, "2026-09-03")));
        assertIncompatible(() -> SourceParameterMapper.map(month, Map.of(),
                selector(REQUEST, "", RANGE, "2026-09-03/2026-09-07")));
        assertIncompatible(() -> SourceParameterMapper.map(range, Map.of(),
                selector(REQUEST, "", MONTH, "2026-09")));
        assertIncompatible(() -> SourceParameterMapper.map(range, Map.of(),
                selector(REQUEST, "", NONE, "")));
        assertIncompatible(() -> SourceParameterMapper.map(none, Map.of(),
                selector(REQUEST, "", DATE, "2026-09-03")));
    }

    @Test
    void stockMappingRequiresVerifiedPolicyDeclaredTsCodeAndMatchingTime() {
        RecoveryPolicy stockDate = stockRecovery(DATE);
        ApiDescriptor income = nativeRange(List.of(parameter("ts_code", ParameterType.TS_CODE)), stockDate);

        assertThat(SourceParameterMapper.map(income, Map.of(),
                selector(STOCK, "000001.SZ", DATE, "2026-09-03")).values())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "ts_code", "000001.SZ", "start_date", "20260903", "end_date", "20260903"));
        assertThat(SourceParameterMapper.map(income, Map.of(),
                selector(STOCK, "600000.SH", DATE, "2026-09-03")).values())
                .containsEntry("ts_code", "600000.SH");

        assertIncompatible(() -> SourceParameterMapper.map(income, Map.of("ts_code", "000001.SZ"),
                selector(STOCK, "000001.SZ", DATE, "2026-09-03")));
        assertThat(SourceParameterMapper.map(income, Map.of("ts_code", "000001.SZ"),
                selector(REQUEST, "", RANGE, "2026-09-03/2026-09-07")).values())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "ts_code", "000001.SZ", "start_date", "20260903", "end_date", "20260907"));
        assertIncompatible(() -> SourceParameterMapper.map(
                nativeRange(List.of(), stockDate), Map.of(),
                selector(STOCK, "000001.SZ", DATE, "2026-09-03")));
        assertIncompatible(() -> SourceParameterMapper.map(
                nativeRange(List.of(parameter("ts_code", ParameterType.TEXT)), stockDate), Map.of(),
                selector(STOCK, "000001.SZ", DATE, "2026-09-03")));
        assertIncompatible(() -> SourceParameterMapper.map(income, Map.of(),
                selector(STOCK, "000001.SZ", RANGE, "2026-09-03/2026-09-07")));
    }

    @Test
    void rangeStockUnitsMayDegradeToOneDayButRequestPoliciesRejectStock() {
        ApiDescriptor stockRange = nativeRange(
                List.of(parameter("ts_code", ParameterType.TS_CODE)), stockRecovery(RANGE));
        assertThat(SourceParameterMapper.map(stockRange, Map.of(),
                selector(STOCK, "000001.SZ", DATE, "2026-09-03")).values())
                .containsEntry("start_date", "20260903").containsEntry("end_date", "20260903");
        assertThat(SourceParameterMapper.map(stockRange, Map.of(),
                selector(STOCK, "000001.SZ", RANGE, "2026-09-03/2026-09-07")).values())
                .containsEntry("start_date", "20260903").containsEntry("end_date", "20260907");

        ApiDescriptor currentProductionShape = nativeRange(
                List.of(parameter("ts_code", ParameterType.TS_CODE)), requestRecovery());
        assertIncompatible(() -> SourceParameterMapper.map(currentProductionShape, Map.of(),
                selector(STOCK, "000001.SZ", DATE, "2026-09-03")));
    }

    @Test
    void rejectsCandidateKeysMissingFromTheSelectedDescriptorShape() {
        ApiDescriptor missingDate = api(Mode.TRADE_DATE_RANGE, SourceRequestMode.DATE, "trade_date",
                requestRecovery(), List.of(), rangeParameters());
        assertIncompatible(() -> SourceParameterMapper.map(missingDate, Map.of(),
                selector(REQUEST, "", DATE, "2026-09-03")));

        ApiDescriptor missingRange = api(Mode.TRADE_DATE_RANGE, SourceRequestMode.RANGE, null,
                requestRecovery(), List.of(parameter("trade_date", ParameterType.DATE)), List.of());
        assertIncompatible(() -> SourceParameterMapper.map(missingRange, Map.of(),
                selector(REQUEST, "", DATE, "2026-09-03")));
    }

    @Test
    void mappedResultsDefensivelyCopyBothCollectionsAndStayImmutable() {
        var values = new HashMap<String, Object>(Map.of("exchange", "SSE"));
        var descriptors = new ArrayList<>(List.of(parameter("exchange", ParameterType.TEXT)));
        var mapped = new SourceParameterMapper.MappedParameters(values, descriptors);
        values.clear();
        descriptors.clear();

        assertThat(mapped.values()).containsExactlyInAnyOrderEntriesOf(Map.of("exchange", "SSE"));
        assertThat(mapped.descriptors()).hasSize(1);
        assertThatThrownBy(() -> mapped.values().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapped.descriptors().clear()).isInstanceOf(UnsupportedOperationException.class);

        assertIncompatible(() -> new SourceParameterMapper.MappedParameters(Map.of("bad-key", "value"), List.of()));
        assertIncompatible(() -> new SourceParameterMapper.MappedParameters(Map.of("exchange", 1), List.of()));
    }

    private static void assertMapped(ApiDescriptor api, Map<String, Object> common, RecoverySelector selector,
            Map<String, Object> expected, List<ParameterDescriptor> descriptors) {
        var mapped = SourceParameterMapper.map(api, common, selector);
        assertThat(mapped.values()).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(mapped.descriptors()).containsExactlyElementsOf(descriptors);
    }

    private static void assertIncompatible(Runnable action) {
        assertThatIllegalArgumentException().isThrownBy(action::run).withMessage(INCOMPATIBLE);
    }

    private static RecoverySelector selector(RecoverySelector.TargetType targetType, String targetValue,
            RecoverySelector.TimeType timeType, String timeValue) {
        return new RecoverySelector(targetType, targetValue, timeType, timeValue);
    }

    private static ApiDescriptor nativeRange(List<ParameterDescriptor> common, RecoveryPolicy recovery) {
        var source = withAll(common, rangeParameters());
        return api(Mode.NATIVE_RANGE, SourceRequestMode.RANGE, null, recovery, source, source);
    }

    private static ApiDescriptor api(Mode mode, SourceRequestMode requestMode, String sourceDate,
            RecoveryPolicy recovery, List<ParameterDescriptor> source, List<ParameterDescriptor> projected) {
        return api(mode, requestMode, sourceDate, recovery, source, projected,
                RequestEvidenceStatus.DOCUMENTED_CANDIDATE);
    }

    private static ApiDescriptor api(Mode mode, SourceRequestMode requestMode, String sourceDate,
            RecoveryPolicy recovery, List<ParameterDescriptor> source, List<ParameterDescriptor> projected,
            RequestEvidenceStatus evidence) {
        DateSemantic semantic = switch (mode) {
            case TRADE_DATE_RANGE -> DateSemantic.TRADE_DATE;
            case ANN_DATE_RANGE -> DateSemantic.ANN_DATE;
            case MONTH_RANGE -> DateSemantic.COVERED_MONTH;
            case NATIVE_RANGE -> DateSemantic.CALENDAR_DATE;
            case ORIGINAL_PARAMS -> DateSemantic.NONE;
        };
        BatchPlanning planning = evidence == RequestEvidenceStatus.UNCONFIRMED
                ? BatchPlanning.UNCONFIRMED
                : switch (requestMode) {
                    case DATE -> BatchPlanning.SINGLE_DATE;
                    case MONTH -> BatchPlanning.SINGLE_MONTH;
                    case RANGE -> BatchPlanning.SOURCE_RANGE;
                    case NONE -> BatchPlanning.ORIGINAL_PARAMS;
                };
        DownloadPolicy policy = new DownloadPolicy(mode, semantic, "Controlled mapper test",
                mode == Mode.TRADE_DATE_RANGE ? DownloadPolicy.CalendarProfile.C_A : null,
                mode == Mode.ORIGINAL_PARAMS ? null : new DownloadPolicy.Limits(31), requestMode, sourceDate,
                evidence, planning, recovery, completeness(),
                mode == Mode.TRADE_DATE_RANGE ? CalendarEvidenceStatus.UNCONFIRMED : null,
                List.of("docs/test.md"));
        return new ApiDescriptor(ApiName.of("mapper_api"), "Mapper API", "test", QueryMode.date_range,
                projected, policy, source);
    }

    private static RecoveryPolicy requestRecovery() {
        return new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false,
                List.of("docs/test.md"));
    }

    private static RecoveryPolicy stockRecovery(RecoverySelector.TimeType timeType) {
        return new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "trade_date", timeType, true,
                List.of("docs/test.md"));
    }

    private static CompletenessPolicy completeness() {
        return new CompletenessPolicy(CompletenessStatus.UNCONFIRMED, CompletenessStatus.UNCONFIRMED,
                "Unconfirmed", "Controlled mapper test only");
    }

    private static List<ParameterDescriptor> rangeParameters() {
        return List.of(parameter("start_date", ParameterType.DATE_RANGE_MEMBER),
                parameter("end_date", ParameterType.DATE_RANGE_MEMBER));
    }

    private static List<ParameterDescriptor> with(ParameterDescriptor first,
            List<ParameterDescriptor> remaining) {
        return withAll(List.of(first), remaining);
    }

    private static List<ParameterDescriptor> withAll(List<ParameterDescriptor> first,
            List<ParameterDescriptor> remaining) {
        var result = new ArrayList<>(first);
        result.addAll(remaining);
        return List.copyOf(result);
    }

    private static ParameterDescriptor parameter(String name, ParameterType type) {
        String related = switch (name) {
            case "start_date" -> "end_date";
            case "end_date" -> "start_date";
            default -> null;
        };
        List<String> allowed = type == ParameterType.ENUM ? List.of("L", "D", "P") : List.of();
        return new ParameterDescriptor(name, name, null, type, true, null, allowed, null, related);
    }
}
