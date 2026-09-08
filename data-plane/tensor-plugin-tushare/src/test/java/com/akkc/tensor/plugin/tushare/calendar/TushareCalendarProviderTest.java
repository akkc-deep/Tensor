package com.akkc.tensor.plugin.tushare.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.CalendarScope;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TushareCalendarProviderTest {
    private static final LocalDate SEPTEMBER_3 = LocalDate.of(2026, 9, 3);
    private static final LocalDate SEPTEMBER_7 = LocalDate.of(2026, 9, 7);
    private static final Set<LocalDate> DATES = Set.of(SEPTEMBER_3, SEPTEMBER_7);

    @Test
    void returnsTheUnionOfCompleteMoneyflowCalendarsForOnlyTheRequestedDates() {
        CalendarSource sse = (identities, dates) -> data("SSE_CALENDAR", List.of(
                row("SSE", SEPTEMBER_3, "0"), row("SSE", SEPTEMBER_7, "0")));
        CalendarSource szse = (identities, dates) -> data("SZSE_CALENDAR", List.of(
                row("SZSE", SEPTEMBER_3, "1"), row("SZSE", SEPTEMBER_7, "0")));
        var provider = new TushareCalendarProvider(Map.of(
                "SSE_CALENDAR", sse, "SZSE_CALENDAR", szse));

        var decision = provider.confirm(api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)),
                new CalendarScope(Map.of("start_date", "20260901", "end_date", "20260910"), DATES), () -> {});

        assertThat(decision.calendars()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "SSE", Map.of(SEPTEMBER_3, false, SEPTEMBER_7, false),
                "SZSE", Map.of(SEPTEMBER_3, true, SEPTEMBER_7, false)));
        assertThat(decision.openDates()).containsExactly(SEPTEMBER_3);
    }

    @Test
    void returnsAnEmptyUnionWhenEveryCompleteCalendarIsClosed() {
        var provider = moneyflowProvider(rows("SSE", "0", "0"), rows("SZSE", "0", "0"));

        assertThat(confirm(provider, api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))).openDates())
                .isEmpty();
    }

    @Test
    void requiresEverySourceBeforeFetchingAnySource() {
        var calls = new AtomicInteger();
        CalendarSource sse = (identities, dates) -> { calls.incrementAndGet(); return data("SSE_CALENDAR", rows("SSE", "1", "1")); };

        assertUnconfirmed(() -> confirm(new TushareCalendarProvider(Map.of("SSE_CALENDAR", sse)),
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))));
        assertThat(calls).hasValue(0);

        assertUnconfirmed(() -> confirm(new TushareCalendarProvider(Map.of(
                "SSE_CALENDAR", sse,
                "SZSE_CALENDAR", source("SZSE_CALENDAR", rows("SZSE", "1", "1")))),
                api("stk_limit", documented(DownloadPolicy.CalendarProfile.C_A))));
        assertThat(calls).hasValue(0);
    }

    @Test
    void rejectsIncompleteEmptyWrongIdentityStaleAndUnconfirmedSources() {
        List<CalendarSource> invalid = List.of(
                source("SZSE_CALENDAR", List.of(row("SZSE", SEPTEMBER_3, "1"))),
                source("SZSE_CALENDAR", List.of()),
                source("SZSE_CALENDAR", List.of(row("SSE", SEPTEMBER_3, "1"), row("SSE", SEPTEMBER_7, "1"))),
                (identities, dates) -> new CalendarSource.CalendarData("SZSE_CALENDAR",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), true, rows("SZSE", "1", "1")),
                (identities, dates) -> new CalendarSource.CalendarData("SZSE_CALENDAR",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), false, rows("SZSE", "1", "1")));

        for (CalendarSource szse : invalid) {
            assertUnconfirmed(() -> confirm(new TushareCalendarProvider(Map.of(
                    "SSE_CALENDAR", source("SSE_CALENDAR", rows("SSE", "1", "1")),
                    "SZSE_CALENDAR", szse)), api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))));
        }
    }

    @Test
    void acceptsConsistentDuplicatesAndRejectsConflictingOnes() {
        var duplicate = new ArrayList<>(rows("SSE", "0", "0"));
        duplicate.add(row("SSE", SEPTEMBER_3, "0"));
        assertThat(confirm(moneyflowProvider(duplicate, rows("SZSE", "0", "0")),
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))).calendars().get("SSE"))
                .hasSize(2);

        duplicate.add(row("SSE", SEPTEMBER_3, "1"));
        assertUnconfirmed(() -> confirm(moneyflowProvider(duplicate, rows("SZSE", "0", "0")),
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))));
    }

    @Test
    void marginFetchesOnlyItsExactSelectedExchange() {
        for (String exchange : List.of("SSE", "SZSE", "BSE")) {
            var calls = new HashMap<String, Integer>();
            var sources = new HashMap<String, CalendarSource>();
            for (String candidate : List.of("SSE", "SZSE", "BSE")) {
                sources.put(candidate + "_CALENDAR", (identities, dates) -> {
                    calls.merge(candidate, 1, Integer::sum);
                    return data(candidate + "_CALENDAR", rows(candidate, "1", "0"));
                });
            }
            var scope = new CalendarScope(Map.of("exchange_id", exchange), DATES);

            var result = new TushareCalendarProvider(sources).confirm(
                    api("margin", documented(DownloadPolicy.CalendarProfile.C_M)), scope, () -> {});

            assertThat(calls).containsExactly(Map.entry(exchange, 1));
            assertThat(result.calendars()).containsOnlyKeys(exchange);
        }
    }

    @Test
    void marginRejectsMissingLowercaseUnknownAndNonStringExchangeWithoutFetching() {
        var calls = new AtomicInteger();
        var provider = new TushareCalendarProvider(Map.of("SSE_CALENDAR",
                (identities, dates) -> { calls.incrementAndGet(); return data("SSE_CALENDAR", rows("SSE", "1", "1")); }));
        for (Map<String, Object> params : List.<Map<String, Object>>of(Map.of(), Map.of("exchange_id", "sse"),
                Map.of("exchange_id", "CFFEX"))) {
            assertUnconfirmed(() -> provider.confirm(api("margin", documented(DownloadPolicy.CalendarProfile.C_M)),
                    new CalendarScope(params, DATES), () -> {}));
        }
        assertThatThrownBy(() -> new CalendarScope(Map.of("exchange_id", 1), DATES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void sharedNorthboundSourceFetchesOncePerConfirmationAndAgainNextTime() {
        var calls = new AtomicInteger();
        var seenIdentities = new ArrayList<Set<String>>();
        CalendarSource source = (identities, dates) -> {
            calls.incrementAndGet();
            seenIdentities.add(identities);
            var result = new ArrayList<CalendarSource.CalendarRow>();
            result.addAll(rows("NORTHBOUND_SH", "1", "0"));
            result.addAll(rows("NORTHBOUND_SZ", "0", "0"));
            return data("HKEX_NORTHBOUND_CALENDAR", result);
        };
        var provider = new TushareCalendarProvider(Map.of("HKEX_NORTHBOUND_CALENDAR", source));
        var descriptor = api("hsgt_top10", documented(DownloadPolicy.CalendarProfile.C_N));

        confirm(provider, descriptor);
        confirm(provider, descriptor);

        assertThat(calls).hasValue(2);
        assertThat(seenIdentities).allSatisfy(identities -> assertThat(identities)
                .containsExactlyInAnyOrder("NORTHBOUND_SH", "NORTHBOUND_SZ"));
    }

    @Test
    void ignoresValidExtraDatesButRejectsMalformedExtraRowsAndLeavesInputsUnchanged() {
        var params = new HashMap<String, Object>(Map.of("start_date", "20260901", "stock", "000001.SZ"));
        var scope = new CalendarScope(params, DATES);
        var seenDates = new ArrayList<Set<LocalDate>>();
        CalendarSource sse = (identities, dates) -> {
            seenDates.add(dates);
            var rows = new ArrayList<>(rows("SSE", "1", "0"));
            rows.add(row("SSE", LocalDate.of(2026, 9, 4), "1"));
            return data("SSE_CALENDAR", rows);
        };
        var provider = new TushareCalendarProvider(Map.of("SSE_CALENDAR", sse,
                "SZSE_CALENDAR", source("SZSE_CALENDAR", rows("SZSE", "0", "0"))));

        var decision = provider.confirm(api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)), scope, () -> {});

        assertThat(seenDates).containsExactly(DATES);
        assertThat(decision.calendars().get("SSE")).containsOnlyKeys(SEPTEMBER_3, SEPTEMBER_7);
        assertThat(scope.publicParams()).containsEntry("stock", "000001.SZ");
        assertThatThrownBy(() -> seenDates.get(0).add(LocalDate.of(2026, 9, 4)))
                .isInstanceOf(UnsupportedOperationException.class);

        CalendarSource malformedExtra = (identities, dates) -> data("SSE_CALENDAR", List.of(
                row("SSE", SEPTEMBER_3, "1"), row("SSE", SEPTEMBER_7, "0"),
                row("UNKNOWN", LocalDate.of(2026, 9, 4), "1")));
        assertUnconfirmed(() -> confirm(new TushareCalendarProvider(Map.of("SSE_CALENDAR", malformedExtra,
                "SZSE_CALENDAR", source("SZSE_CALENDAR", rows("SZSE", "0", "0")))),
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))));
    }

    @Test
    void rejectsMalformedSourceObservationsAndCoverageHiddenByDuplicates() {
        var invalid = new ArrayList<CalendarSource>();
        invalid.add((identities, dates) -> null);
        invalid.add((identities, dates) -> new CalendarSource.CalendarData(
                "SSE_CALENDAR", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true, null));
        invalid.add((identities, dates) -> new CalendarSource.CalendarData(
                "SSE_CALENDAR", null, LocalDate.of(2026, 12, 31), true, rows("SSE", "1", "1")));
        invalid.add((identities, dates) -> new CalendarSource.CalendarData(
                "SSE_CALENDAR", LocalDate.of(2026, 1, 1), null, true, rows("SSE", "1", "1")));
        invalid.add((identities, dates) -> data("SSE_CALENDAR", java.util.Arrays.asList(
                row("SSE", SEPTEMBER_3, "1"), null)));
        invalid.add(source("WRONG_CALENDAR", rows("SSE", "1", "1")));
        invalid.add((identities, dates) -> new CalendarSource.CalendarData("SSE_CALENDAR",
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), true, rows("SSE", "1", "1")));
        invalid.add(source("SSE_CALENDAR", List.of(new CalendarSource.CalendarRow(null, SEPTEMBER_3, "1"))));
        invalid.add(source("SSE_CALENDAR", List.of(new CalendarSource.CalendarRow("SSE", null, "1"))));
        invalid.add(source("SSE_CALENDAR", List.of(row("SSE", SEPTEMBER_3, "true"))));
        invalid.add(source("SSE_CALENDAR", List.of(row("SSE", SEPTEMBER_3, "1"), row("SSE", SEPTEMBER_3, "1"))));

        for (CalendarSource sse : invalid) {
            assertUnconfirmed(() -> confirm(new TushareCalendarProvider(Map.of("SSE_CALENDAR", sse,
                    "SZSE_CALENDAR", source("SZSE_CALENDAR", rows("SZSE", "1", "1")))),
                    api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))));
        }
    }

    @Test
    void supportsControlledCrossYearEvidenceCoveringEveryRequestedDate() {
        var dates = Set.of(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1));
        CalendarSource sse = (identities, requested) -> new CalendarSource.CalendarData("SSE_CALENDAR",
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), true, List.of(
                        row("SSE", LocalDate.of(2025, 12, 31), "1"), row("SSE", LocalDate.of(2026, 1, 1), "0")));
        CalendarSource szse = (identities, requested) -> new CalendarSource.CalendarData("SZSE_CALENDAR",
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), true, List.of(
                        row("SZSE", LocalDate.of(2025, 12, 31), "0"), row("SZSE", LocalDate.of(2026, 1, 1), "0")));

        var decision = new TushareCalendarProvider(Map.of("SSE_CALENDAR", sse, "SZSE_CALENDAR", szse)).confirm(
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)),
                new CalendarScope(Map.of(), dates), () -> {});

        assertThat(decision.openDates()).containsExactly(LocalDate.of(2025, 12, 31));
    }

    @Test
    void rejectsUnconfirmedWrongModeWrongProfileUnknownAndNonTradingApisBeforeFetching() {
        var calls = new AtomicInteger();
        CalendarSource source = (identities, dates) -> { calls.incrementAndGet(); return data("SSE_CALENDAR", rows("SSE", "1", "1")); };
        var provider = new TushareCalendarProvider(Map.of("SSE_CALENDAR", source, "SZSE_CALENDAR", source));
        var base = documented(DownloadPolicy.CalendarProfile.C_A);
        var original = originalPolicy();
        for (ApiDescriptor descriptor : List.of(
                api("moneyflow", withEvidence(base, DownloadPolicy.CalendarEvidenceStatus.UNCONFIRMED)),
                api("moneyflow", withEvidence(base, DownloadPolicy.CalendarEvidenceStatus.CONFLICT)),
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_N)),
                api("daily", documented(DownloadPolicy.CalendarProfile.C_A)),
                api("hk_hold", documented(DownloadPolicy.CalendarProfile.C_N)),
                api("moneyflow_hsgt", documented(DownloadPolicy.CalendarProfile.C_X)),
                api("trade_cal", original))) {
            assertUnconfirmed(() -> confirm(provider, descriptor));
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void csfMappingsUseOnlyTheirDedicatedSource() {
        var calls = new LinkedHashSet<String>();
        CalendarSource csf = (identities, dates) -> {
            calls.addAll(identities);
            return data("CSF_REFINANCING_CALENDAR", rows("CSF_REFINANCING", "1", "0"));
        };
        CalendarSource sse = (identities, dates) -> { throw new AssertionError("SSE must not be used"); };

        var decision = confirm(new TushareCalendarProvider(Map.of(
                "CSF_REFINANCING_CALENDAR", csf, "SSE_CALENDAR", sse)),
                api("slb_len", documented(DownloadPolicy.CalendarProfile.C_S)));

        assertThat(calls).containsExactly("CSF_REFINANCING");
        assertThat(decision.calendars()).containsOnlyKeys("CSF_REFINANCING");
    }

    @Test
    void mapsOnlySourceExceptionsAndPreservesCalendarAndContextFailures() {
        CalendarSource sourceFailure = (identities, dates) -> {
            throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "sensitive upstream detail");
        };
        Throwable mapped = catchThrowable(() -> confirm(new TushareCalendarProvider(Map.of(
                "SSE_CALENDAR", sourceFailure,
                "SZSE_CALENDAR", source("SZSE_CALENDAR", rows("SZSE", "1", "1")))),
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))));
        assertThat(mapped).isExactlyInstanceOf(CalendarUnconfirmedException.class)
                .hasMessage("Applicable calendars are unconfirmed").hasNoCause();

        var calendarFailure = new CalendarUnconfirmedException();
        CalendarSource alreadyMapped = (identities, dates) -> { throw calendarFailure; };
        assertThat(catchThrowable(() -> confirm(new TushareCalendarProvider(Map.of(
                "SSE_CALENDAR", alreadyMapped, "SZSE_CALENDAR", source("SZSE_CALENDAR", rows("SZSE", "1", "1")))),
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)))))
                .isSameAs(calendarFailure);

        var contextFailure = new IllegalStateException("server stopped");
        assertThat(catchThrowable(() -> new TushareCalendarProvider(Map.of()).confirm(
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)), scope(),
                () -> { throw contextFailure; }))).isSameAs(contextFailure);

        var runtimeFailure = new IllegalArgumentException("adapter programming error");
        CalendarSource brokenAdapter = (identities, dates) -> { throw runtimeFailure; };
        assertThat(catchThrowable(() -> confirm(new TushareCalendarProvider(Map.of(
                "SSE_CALENDAR", brokenAdapter,
                "SZSE_CALENDAR", source("SZSE_CALENDAR", rows("SZSE", "1", "1")))),
                api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)))))
                .isSameAs(runtimeFailure);
    }

    @Test
    void checksContextAfterFetchWithoutConvertingTheFailureOrRetrying() {
        var fetches = new AtomicInteger();
        CalendarSource sse = (identities, dates) -> {
            fetches.incrementAndGet();
            return data("SSE_CALENDAR", rows("SSE", "1", "1"));
        };
        var checks = new AtomicInteger();
        var failure = new IllegalStateException("stopped after source");

        Throwable thrown = catchThrowable(() -> new TushareCalendarProvider(Map.of(
                "SSE_CALENDAR", sse, "SZSE_CALENDAR", source("SZSE_CALENDAR", rows("SZSE", "1", "1"))))
                .confirm(api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)), scope(), () -> {
                    if (checks.incrementAndGet() == 3) throw failure;
                }));

        assertThat(thrown).isSameAs(failure);
        assertThat(fetches).hasValue(1);
    }

    @Test
    void enforcesNonNullContractsRegistrationAndDefensiveCopies() {
        assertThatThrownBy(() -> new TushareCalendarProvider(null)).isInstanceOf(NullPointerException.class);
        for (String key : List.of("x", "_SSE", "Sse", "A")) {
            assertThatThrownBy(() -> new TushareCalendarProvider(Map.of(key, source(key, List.of()))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var withNull = new HashMap<String, CalendarSource>();
        withNull.put(null, source("SSE_CALENDAR", List.of()));
        assertThatThrownBy(() -> new TushareCalendarProvider(withNull)).isInstanceOf(IllegalArgumentException.class);
        withNull.clear();
        withNull.put("SSE_CALENDAR", null);
        assertThatThrownBy(() -> new TushareCalendarProvider(withNull)).isInstanceOf(IllegalArgumentException.class);

        var mutableSources = new HashMap<String, CalendarSource>();
        mutableSources.put("SSE_CALENDAR", source("SSE_CALENDAR", rows("SSE", "1", "1")));
        var provider = new TushareCalendarProvider(mutableSources);
        mutableSources.clear();
        assertUnconfirmed(() -> confirm(provider, api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A))));

        var mutableRows = new ArrayList<>(rows("SSE", "1", "1"));
        var data = data("SSE_CALENDAR", mutableRows);
        mutableRows.clear();
        assertThat(data.rows()).hasSize(2);
        assertThatThrownBy(() -> data.rows().clear()).isInstanceOf(UnsupportedOperationException.class);

        var ready = moneyflowProvider(rows("SSE", "1", "1"), rows("SZSE", "1", "1"));
        assertThatThrownBy(() -> ready.confirm(null, scope(), () -> {})).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ready.confirm(api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)), null, () -> {}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ready.confirm(api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)), scope(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> confirm(ready, api("moneyflow", documented(DownloadPolicy.CalendarProfile.C_A)))
                .calendars().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static CalendarSource.CalendarData data(String sourceId, List<CalendarSource.CalendarRow> rows) {
        return new CalendarSource.CalendarData(sourceId, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), true, rows);
    }

    private static CalendarSource source(String sourceId, List<CalendarSource.CalendarRow> rows) {
        return (identities, dates) -> data(sourceId, rows);
    }

    private static List<CalendarSource.CalendarRow> rows(String identity, String first, String second) {
        return List.of(row(identity, SEPTEMBER_3, first), row(identity, SEPTEMBER_7, second));
    }

    private static TushareCalendarProvider moneyflowProvider(List<CalendarSource.CalendarRow> sse,
            List<CalendarSource.CalendarRow> szse) {
        return new TushareCalendarProvider(Map.of(
                "SSE_CALENDAR", source("SSE_CALENDAR", sse),
                "SZSE_CALENDAR", source("SZSE_CALENDAR", szse)));
    }

    private static CalendarScope scope() { return new CalendarScope(Map.of(), DATES); }

    private static com.akkc.tensor.plugin.api.download.CalendarDecision confirm(
            TushareCalendarProvider provider, ApiDescriptor api) {
        return provider.confirm(api, scope(), () -> {});
    }

    private static void assertUnconfirmed(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isExactlyInstanceOf(CalendarUnconfirmedException.class);
    }

    private static CalendarSource.CalendarRow row(String identity, LocalDate date, String isOpen) {
        return new CalendarSource.CalendarRow(identity, date, isOpen);
    }

    private static ApiDescriptor api(String name, DownloadPolicy policy) {
        return new ApiDescriptor(ApiName.of(name), name, "test", QueryMode.trade_date,
                List.of(), policy, List.of());
    }

    private static DownloadPolicy documented(DownloadPolicy.CalendarProfile profile) {
        var evidence = List.of("docs/test.md");
        var completeness = new DownloadPolicy.CompletenessPolicy(
                DownloadPolicy.CompletenessStatus.UNCONFIRMED,
                DownloadPolicy.CompletenessStatus.UNCONFIRMED, "Unconfirmed", "Controlled test only");
        return new DownloadPolicy(DownloadPolicy.Mode.TRADE_DATE_RANGE, DownloadPolicy.DateSemantic.TRADE_DATE,
                "Controlled trade range", profile, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.DATE, "trade_date",
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SINGLE_DATE,
                new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false, evidence),
                completeness, DownloadPolicy.CalendarEvidenceStatus.DOCUMENTED, evidence);
    }

    private static DownloadPolicy withEvidence(DownloadPolicy base, DownloadPolicy.CalendarEvidenceStatus status) {
        return new DownloadPolicy(base.mode(), base.dateSemantic(), base.description(), base.calendarProfile(),
                base.limits(), base.sourceRequestMode(), base.sourceDateParameter(), base.requestEvidenceStatus(),
                base.batchPlanning(), base.recoveryPolicy(), base.completenessPolicy(), status, base.evidenceRefs());
    }

    private static DownloadPolicy originalPolicy() {
        var evidence = List.of("docs/test.md");
        return new DownloadPolicy(DownloadPolicy.Mode.ORIGINAL_PARAMS, DownloadPolicy.DateSemantic.NONE,
                "Controlled original", null, null, DownloadPolicy.SourceRequestMode.NONE, null,
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.ORIGINAL_PARAMS,
                new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false, evidence),
                new DownloadPolicy.CompletenessPolicy(DownloadPolicy.CompletenessStatus.UNCONFIRMED,
                        DownloadPolicy.CompletenessStatus.UNCONFIRMED, "Unconfirmed", "Controlled test only"),
                null, evidence);
    }
}
