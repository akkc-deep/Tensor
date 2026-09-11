package com.akkc.tensor.plugin.api.download.batch;

import static com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.*;
import static org.assertj.core.api.Assertions.*;

import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BatchDownloadDescriptorTest {
    private static final CompletenessRule KNOWN =
            new CompletenessRule(CompletenessRule.Kind.CONFIRMED_ROW_LIMIT, 100L, "Verified source limit");
    private static final CompletenessRule UNKNOWN =
            new CompletenessRule(CompletenessRule.Kind.UNKNOWN, null, null);

    @Test
    void acceptsClosedRangesAndRejectsMissingOrReversedEndpoints() {
        var leapDay = LocalDate.of(2024, 2, 29);
        assertThat(new DateRange(leapDay, leapDay).end()).isEqualTo(leapDay);
        assertThat(new DateRange(leapDay, LocalDate.of(2025, 1, 1)).start()).isEqualTo(leapDay);
        assertThatIllegalArgumentException().isThrownBy(() -> new DateRange(leapDay, leapDay.minusDays(1)));
        assertThatNullPointerException().isThrownBy(() -> new DateRange(null, leapDay));
        assertThatNullPointerException().isThrownBy(() -> new DateRange(leapDay, null));
    }

    @Test
    void distinguishesUnverifiedCompletenessFromAnAvailableRule() {
        assertThatIllegalArgumentException().isThrownBy(() -> descriptor(parameters(), Availability.AVAILABLE, null, UNKNOWN));
        assertThat(descriptor(parameters(), Availability.NEEDS_VERIFICATION, "Needs source evidence", UNKNOWN)
                .completenessRule().kind()).isEqualTo(CompletenessRule.Kind.UNKNOWN);
        var coverage = new CompletenessRule(CompletenessRule.Kind.VERIFIED_RULE, null, "Every calendar day exactly once");
        assertThat(descriptor(parameters(), Availability.AVAILABLE, null, coverage).completenessRule()).isEqualTo(coverage);
        assertThatIllegalArgumentException().isThrownBy(() -> new CompletenessRule(CompletenessRule.Kind.UNKNOWN, 100L, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompletenessRule(CompletenessRule.Kind.UNKNOWN, null, "unproven"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompletenessRule(CompletenessRule.Kind.VERIFIED_RULE, 100L, "coverage"));
        for (Long limit : new Long[] {null, 0L, -1L}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new CompletenessRule(CompletenessRule.Kind.CONFIRMED_ROW_LIMIT, limit, "evidence"));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void requiresEvidenceForConfirmedRules(String evidence) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CompletenessRule(CompletenessRule.Kind.CONFIRMED_ROW_LIMIT, 100L, evidence));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CompletenessRule(CompletenessRule.Kind.VERIFIED_RULE, null, evidence));
    }

    @Test
    void retainsSourceSpecificNamesAndAnImmutableParameterSnapshot() {
        var mutable = new ArrayList<>(parameters());
        var descriptor = descriptor(mutable, Availability.AVAILABLE, null, KNOWN);
        mutable.clear();
        assertThat(descriptor.parameters()).extracting(ParameterDescriptor::name).containsExactly("from", "to");
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> descriptor.parameters().clear());
    }

    @Test
    void rejectsAmbiguousOrInvalidRangeDescriptions() {
        var from = parameters().getFirst();
        var to = parameters().getLast();
        List<List<ParameterDescriptor>> invalid = List.of(
                List.of(), List.of(from), List.of(from, from, to), List.of(to, from),
                List.of(member("from", "to", false), to),
                List.of(from, member("to", "other", true)),
                List.of(from, to, member("extra", "from", true)),
                List.of(new ParameterDescriptor("from", "From", null, ParameterType.DATE, true,
                        null, List.of(), null, null), to));
        for (var parameters : invalid) {
            assertThatIllegalArgumentException().isThrownBy(() -> descriptor(parameters, Availability.AVAILABLE, null, KNOWN));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> descriptor(parameters(), Availability.AVAILABLE, "disabled", KNOWN));
        assertThatIllegalArgumentException().isThrownBy(() -> descriptor(parameters(), Availability.NEEDS_VERIFICATION, null, KNOWN));
        assertThatIllegalArgumentException().isThrownBy(() -> descriptor(parameters(), Availability.UNSUPPORTED, "unsupported", UNKNOWN));
        assertThat(new BatchDownloadDescriptor(List.of(), null, null, null, null, null, false,
                Availability.UNSUPPORTED, "No range support", "v1", UNKNOWN).parameters()).isEmpty();
    }

    @Test
    void requiresDateMeaningVersionAndNonSplittableDailyPlanning() {
        for (var mode : PlanningMode.values()) {
            for (var axis : DateAxis.values()) {
                assertThat(new BatchDownloadDescriptor(parameters(), "from", "to", axis, "Date", mode,
                        false, Availability.AVAILABLE, null, "v1", KNOWN).dateAxis()).isEqualTo(axis);
            }
        }
        for (var mode : List.of(PlanningMode.CALENDAR_DAYS, PlanningMode.TRADING_DAYS)) {
            assertThatIllegalArgumentException().isThrownBy(() -> new BatchDownloadDescriptor(parameters(),
                    "from", "to", DateAxis.TRADE_DATE, "Date", mode, true, Availability.AVAILABLE, null, "v1", KNOWN));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new BatchDownloadDescriptor(parameters(),
                "from", "from", DateAxis.TRADE_DATE, "Date", PlanningMode.NATIVE_RANGE, true,
                Availability.AVAILABLE, null, "v1", KNOWN));
        assertThatIllegalArgumentException().isThrownBy(() -> new BatchDownloadDescriptor(parameters(),
                "from", "to", DateAxis.TRADE_DATE, " ", PlanningMode.NATIVE_RANGE, true,
                Availability.AVAILABLE, null, "v1", KNOWN));
        assertThatIllegalArgumentException().isThrownBy(() -> new BatchDownloadDescriptor(parameters(),
                "from", "to", DateAxis.TRADE_DATE, "Date", PlanningMode.NATIVE_RANGE, true,
                Availability.AVAILABLE, null, " ", KNOWN));
    }

    private static BatchDownloadDescriptor descriptor(List<ParameterDescriptor> parameters,
            Availability availability, String reason, CompletenessRule rule) {
        return new BatchDownloadDescriptor(parameters, "from", "to", DateAxis.REPORT_PERIOD,
                "Report period", PlanningMode.NATIVE_RANGE, true, availability, reason, "v1", rule);
    }

    private static List<ParameterDescriptor> parameters() {
        return List.of(member("from", "to", true), member("to", "from", true));
    }

    private static ParameterDescriptor member(String name, String related, boolean required) {
        return new ParameterDescriptor(name, name, null, ParameterType.DATE_RANGE_MEMBER,
                required, null, List.of(), null, related);
    }
}
