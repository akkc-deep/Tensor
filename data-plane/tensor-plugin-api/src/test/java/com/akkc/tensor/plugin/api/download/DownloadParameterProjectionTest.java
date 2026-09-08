package com.akkc.tensor.plugin.api.download;

import static org.assertj.core.api.Assertions.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.test.DownloadPolicies;
import java.util.*;
import org.junit.jupiter.api.Test;

class DownloadParameterProjectionTest {
    @Test void removesOnlyModeDateAndAppendsRequiredPairPreservingAdditionalFields() {
        var stock = parameter("ts_code", ParameterType.TS_CODE, null);
        var source = new ArrayList<>(List.of(parameter("ann_date", ParameterType.DATE, null), stock));
        var policy = policy(DownloadPolicy.Mode.ANN_DATE_RANGE);
        var projected = DownloadParameterProjection.project(source, policy);
        assertThat(projected).extracting(ParameterDescriptor::name).containsExactly("ts_code", "start_date", "end_date");
        assertThat(projected.getFirst()).isEqualTo(stock);
        assertThat(projected.subList(1, 3)).allSatisfy(p -> {
            assertThat(p.type()).isEqualTo(ParameterType.DATE_RANGE_MEMBER);
            assertThat(p.required()).isTrue(); assertThat(p.defaultValue()).isNull();
            assertThat(p.allowedValues()).isEmpty(); assertThat(p.pattern()).isNull();
            assertThat(p.description()).isEqualTo(policy.description());
        });
        assertThat(projected.get(1).relatedParameter()).isEqualTo("end_date");
        assertThat(projected.get(2).relatedParameter()).isEqualTo("start_date");
        var api = new ApiDescriptor(ApiName.of("income"), "Income", "Financial", QueryMode.ann_date,
                projected, policy, source);
        source.clear();
        assertThat(api.sourceParameters()).hasSize(2);
        assertThat(api.parameters()).isEqualTo(projected);
        assertThatThrownBy(() -> api.sourceParameters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ApiDescriptor.class.getConstructors()).singleElement().satisfies(c -> assertThat(c.getParameterCount()).isEqualTo(7));
    }

    @Test void projectsTradeAndMonthButPreservesNativeAndOriginalSemantics() {
        assertThat(DownloadParameterProjection.project(List.of(parameter("trade_date", ParameterType.DATE, null)), policy(DownloadPolicy.Mode.TRADE_DATE_RANGE)))
                .extracting(ParameterDescriptor::name).containsExactly("start_date", "end_date");
        assertThat(DownloadParameterProjection.project(List.of(parameter("month", ParameterType.MONTH, null)), policy(DownloadPolicy.Mode.MONTH_RANGE)))
                .extracting(ParameterDescriptor::name).containsExactly("start_date", "end_date");
        var start = parameter("start_date", ParameterType.DATE_RANGE_MEMBER, "end_date");
        var end = parameter("end_date", ParameterType.DATE_RANGE_MEMBER, "start_date");
        var exchange = new ParameterDescriptor("exchange", "Market", "Source market", ParameterType.ENUM, true, null, List.of("SSE", "SZSE", "BSE"), null, null);
        assertThat(DownloadParameterProjection.project(List.of(start, end, exchange), policy(DownloadPolicy.Mode.NATIVE_RANGE)))
                .containsExactly(exchange, start, end);
        var original = List.of(parameter("scenario", ParameterType.TEXT, null));
        assertThat(DownloadParameterProjection.project(original, DownloadPolicies.original())).isEqualTo(original);
    }

    @Test void rejectsMissingOrConflictingDateMetadataAndDuplicateSourceFields() {
        for (var source : List.of(List.<ParameterDescriptor>of(), List.of(parameter("ann_date", ParameterType.DATE, null)),
                List.of(parameter("trade_date", ParameterType.MONTH, null)),
                List.of(parameter("trade_date", ParameterType.DATE, null), parameter("month", ParameterType.MONTH, null)))) {
            assertThatIllegalArgumentException().isThrownBy(() -> DownloadParameterProjection.project(source, policy(DownloadPolicy.Mode.TRADE_DATE_RANGE)));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> DownloadParameterProjection.project(List.of(parameter("start_date", ParameterType.DATE, null), parameter("end_date", ParameterType.DATE, null)), policy(DownloadPolicy.Mode.NATIVE_RANGE)));
        var same = parameter("scenario", ParameterType.TEXT, null);
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiDescriptor(ApiName.of("test"), "Test", "Test", QueryMode.snapshot, List.of(), DownloadPolicies.original(), List.of(same, same)));
        assertThatNullPointerException().isThrownBy(() -> new ApiDescriptor(ApiName.of("test"), "Test", "Test", QueryMode.snapshot, List.of(), null, List.of()));
    }

    private static ParameterDescriptor parameter(String name, ParameterType type, String related) {
        return new ParameterDescriptor(name, name, "Source condition", type, true, null, List.of(), null, related);
    }
    private static DownloadPolicy policy(DownloadPolicy.Mode mode) {
        var original = DownloadPolicies.original();
        return new DownloadPolicy(mode, switch(mode) {
            case TRADE_DATE_RANGE -> DownloadPolicy.DateSemantic.TRADE_DATE;
            case ANN_DATE_RANGE, NATIVE_RANGE -> DownloadPolicy.DateSemantic.ANN_DATE;
            case MONTH_RANGE -> DownloadPolicy.DateSemantic.COVERED_MONTH;
            case ORIGINAL_PARAMS -> DownloadPolicy.DateSemantic.NONE;
        }, "Controlled range", mode == DownloadPolicy.Mode.TRADE_DATE_RANGE ? DownloadPolicy.CalendarProfile.C_A : null,
                new DownloadPolicy.Limits(31), null, null, DownloadPolicy.RequestEvidenceStatus.UNCONFIRMED,
                DownloadPolicy.BatchPlanning.UNCONFIRMED, original.recoveryPolicy(), original.completenessPolicy(),
                mode == DownloadPolicy.Mode.TRADE_DATE_RANGE ? DownloadPolicy.CalendarEvidenceStatus.UNCONFIRMED : null,
                original.evidenceRefs());
    }
}
