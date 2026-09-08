package com.akkc.tensor.plugin.api.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType.*;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.*;

import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DownloadContractsTest {
    @Test void defaultPlanningChecksOnceAndRefusesWithoutOtherCallbacks() {
        var checks = new AtomicInteger();
        DataSourcePlugin plugin = new DataSourcePlugin() {
            public PluginDescriptor descriptor() { throw new AssertionError(); }
            public PluginReadiness readiness() { throw new AssertionError(); }
            public CalendarDecision confirmCalendar(ApiName api, CalendarScope scope, DownloadContext context) { throw new AssertionError(); }
            public FetchResult fetchBatch(ApiName api, FetchBatch batch, DownloadContext context) { throw new AssertionError(); }
            public FetchResult download(ApiName api, Map<String,Object> params, DownloadContext context) { throw new AssertionError(); }
        };
        var name = ApiName.of("daily");
        var batch = new FetchBatch(Map.of("trade_date", "20260903"), requestPolicy());
        assertThatThrownBy(() -> plugin.planBatch(name, batch, checks::incrementAndGet))
                .isInstanceOfSatisfying(SourceException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
                    assertThat(failure).hasMessage("Source completeness is unconfirmed").hasNoCause();
                    assertThat(failure.getSuppressed()).isEmpty();
                });
        assertThat(checks).hasValue(1);
        assertThatNullPointerException().isThrownBy(() -> plugin.planBatch(null, batch, checks::incrementAndGet));
        assertThatNullPointerException().isThrownBy(() -> plugin.planBatch(name, null, checks::incrementAndGet));
        assertThatNullPointerException().isThrownBy(() -> plugin.planBatch(name, batch, null));
        var failure = new IllegalStateException("server");
        assertThatThrownBy(() -> plugin.planBatch(name, batch, () -> { throw failure; })).isSameAs(failure);
        assertThat(checks).hasValue(1);
    }

    @Test void selectorsKeepBothObjectAndExactTime() {
        assertThat(new RecoverySelector(STOCK, "000001.SZ", DATE, "2024-02-29"))
                .isNotEqualTo(new RecoverySelector(STOCK, "600000.SH", DATE, "2024-02-29"));
        new RecoverySelector(REQUEST, "", DATE, "2024-02-29");
        new RecoverySelector(REQUEST, "", MONTH, "2024-02");
        new RecoverySelector(REQUEST, "", RANGE, "2024-02-29/2024-03-01");
        new RecoverySelector(REQUEST, "", NONE, "");
        for (String value : List.of("2023-02-29", "2024-04-31", "20240229", "0000-01-01")) {
            assertThatIllegalArgumentException().isThrownBy(() -> new RecoverySelector(REQUEST, "", DATE, value));
        }
        for (String value : List.of("000001.sz", "000001.SZ,600000.SH", " 000001.SZ", "a".repeat(65))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new RecoverySelector(STOCK, value, NONE, ""));
        }
        for (String value : List.of("2024", "2024-13", "2024-2")) {
            assertThatIllegalArgumentException().isThrownBy(() -> new RecoverySelector(REQUEST, "", MONTH, value));
        }
        for (String value : List.of("2024-03-01/2024-03-01", "2024-03-02/2024-03-01", "2024-02-30/2024-03-01")) {
            assertThatIllegalArgumentException().isThrownBy(() -> new RecoverySelector(REQUEST, "", RANGE, value));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new RecoverySelector(REQUEST, "stock", NONE, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecoverySelector(REQUEST, "", NONE, "date"));
        assertThatNullPointerException().isThrownBy(() -> new RecoverySelector(null, "", NONE, ""));
    }

    @Test void recoveryRequiresEvidenceAndVerifiedMappings() {
        List<String> refs = new ArrayList<>(List.of("docs/evidence.md"));
        RecoveryPolicy request = new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false, refs);
        refs.clear();
        assertThat(request.evidenceRefs()).containsExactly("docs/evidence.md");
        new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "trade_date", DATE, true, List.of("docs/evidence.md"));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, null, "trade_date", DATE, true, request.evidenceRefs()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "trade_date", NONE, true, request.evidenceRefs()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "trade_date", DATE, false, request.evidenceRefs()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, true, request.evidenceRefs()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false, List.of()));
    }

    @Test void batchesFreezeOnlyStringSourceConditions() {
        var params = new HashMap<String, Object>(Map.of("start_date", "20240101", "end_date", "20240102"));
        var batch = new FetchBatch(params, requestPolicy());
        params.clear();
        assertThat(batch.sourceParams()).hasSize(2);
        assertThatThrownBy(() -> batch.sourceParams().clear()).isInstanceOf(UnsupportedOperationException.class);
        new FetchBatch(Map.of(), requestPolicy());
        assertThatIllegalArgumentException().isThrownBy(() -> new FetchBatch(Map.of("trade_date", List.of("20240101")), requestPolicy()));
        assertThatIllegalArgumentException().isThrownBy(() -> new FetchBatch(Map.of("bad-key", "value"), requestPolicy()));
    }

    @Test void calendarsRequireExactCoverageAndDeepFreezeAndUnion() {
        LocalDate a = LocalDate.of(2026, 9, 3), b = LocalDate.of(2026, 9, 7);
        var dates = new HashSet<>(Set.of(a, b));
        var params = new HashMap<String,Object>(Map.of("exchange_id", "SSE"));
        var scope = new CalendarScope(params, dates);
        dates.clear(); params.clear();
        var sse = new HashMap<>(Map.of(a, true, b, false));
        var maps = new HashMap<String,Map<LocalDate,Boolean>>(Map.of("SSE", sse, "SZSE", Map.of(a, false, b, true)));
        var decision = new CalendarDecision(scope, maps);
        sse.clear(); maps.clear();
        assertThat(decision.openDates()).containsExactlyInAnyOrder(a, b);
        assertThat(scope.publicParams()).containsEntry("exchange_id", "SSE");
        assertThat(new CalendarDecision(scope, Map.of("SSE", Map.of(a, false, b, false))).openDates()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new CalendarDecision(scope, Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new CalendarDecision(scope, Map.of("SSE", Map.of(a, true))));
        assertThatIllegalArgumentException().isThrownBy(() -> new CalendarDecision(scope, Map.of("SSE", Map.of(a, true, b, true, b.plusDays(1), true))));
        assertThatIllegalArgumentException().isThrownBy(() -> new CalendarScope(Map.of(), Set.of()));
        var nullable = new HashMap<LocalDate,Boolean>(); nullable.put(a, null); nullable.put(b, true);
        assertThatNullPointerException().isThrownBy(() -> new CalendarDecision(scope, Map.of("SSE", nullable)));
        assertThatThrownBy(() -> decision.calendars().get("SSE").clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void defaultCalendarChecksServerThenExplicitlyRefusesWithoutDownload() {
        AtomicInteger checks = new AtomicInteger();
        DataSourcePlugin plugin = new DataSourcePlugin() {
            public PluginDescriptor descriptor() { throw new AssertionError(); }
            public PluginReadiness readiness() { throw new AssertionError(); }
            public FetchResult download(ApiName api, Map<String,Object> params, DownloadContext context) { throw new AssertionError(); }
        };
        CalendarScope scope = new CalendarScope(Map.of(), Set.of(LocalDate.of(2026, 9, 3)));
        assertThatThrownBy(() -> plugin.confirmCalendar(ApiName.of("daily"), scope, checks::incrementAndGet))
                .isInstanceOf(CalendarUnconfirmedException.class)
                .hasMessage("Applicable calendars are unconfirmed")
                .satisfies(e -> assertThat(((TensorException)e).code()).isEqualTo(ErrorCode.CALENDAR_UNCONFIRMED));
        assertThat(checks).hasValue(1);
        RuntimeException fault = new IllegalStateException("server unavailable");
        assertThatThrownBy(() -> plugin.confirmCalendar(ApiName.of("daily"), scope, () -> { throw fault; })).isSameAs(fault);
        assertThatNullPointerException().isThrownBy(() -> plugin.confirmCalendar(null, scope, () -> {}));
    }

    @Test void defaultBatchFetchChecksServerThenExplicitlyRefusesWithoutDownload() {
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();
        DataSourcePlugin plugin = new DataSourcePlugin() {
            public PluginDescriptor descriptor() { throw new AssertionError(); }
            public PluginReadiness readiness() { throw new AssertionError(); }
            public FetchResult download(ApiName api, Map<String,Object> params, DownloadContext context) {
                downloads.incrementAndGet();
                throw new AssertionError();
            }
        };
        FetchBatch batch = new FetchBatch(Map.of("trade_date", "20260903"), requestPolicy());
        assertThatThrownBy(() -> plugin.fetchBatch(ApiName.of("daily"), batch, checks::incrementAndGet))
                .isInstanceOfSatisfying(SourceException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
                    assertThat(failure.getMessage()).isEqualTo("Source completeness is unconfirmed");
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getSuppressed()).isEmpty();
                });
        assertThat(checks).hasValue(1);
        assertThat(downloads).hasValue(0);
        RuntimeException fault = new IllegalStateException("server unavailable");
        assertThatThrownBy(() -> plugin.fetchBatch(ApiName.of("daily"), batch, () -> { throw fault; }))
                .isSameAs(fault);
        assertThatNullPointerException().isThrownBy(() -> plugin.fetchBatch(null, batch, () -> {}));
        assertThatNullPointerException().isThrownBy(() -> plugin.fetchBatch(ApiName.of("daily"), null, () -> {}));
        assertThatNullPointerException().isThrownBy(() -> plugin.fetchBatch(ApiName.of("daily"), batch, null));
    }

    @Test void fetchFailuresAreExplicitUniqueAndSourceOnly() {
        var selector = new RecoverySelector(REQUEST, "", NONE, "");
        var failure = new FetchResult.UnitFailure(selector, ErrorCode.SOURCE_TRUNCATED, "Source is truncated");
        var failures = new ArrayList<>(List.of(failure));
        var result = new FetchResult(envelope(DownloadStatus.SUCCESS), failures);
        failures.clear();
        assertThat(result.failures()).containsExactly(failure);
        assertThatIllegalArgumentException().isThrownBy(() -> new FetchResult(envelope(DownloadStatus.SUCCESS), List.of(failure, failure)));
        assertThatIllegalArgumentException().isThrownBy(() -> new FetchResult(envelope(DownloadStatus.FAILURE), List.of(failure)));
        assertThatNullPointerException().isThrownBy(() -> new FetchResult(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new FetchResult(envelope(DownloadStatus.SUCCESS), Arrays.asList((FetchResult.UnitFailure)null)));
        for (ErrorCode code : ErrorCode.values()) {
            if (!Set.of(ErrorCode.SOURCE_AUTH_FAILED, ErrorCode.SOURCE_PERMISSION_DENIED, ErrorCode.SOURCE_RATE_LIMITED, ErrorCode.SOURCE_UNAVAILABLE, ErrorCode.SOURCE_NETWORK_ERROR, ErrorCode.SOURCE_TIMEOUT, ErrorCode.SOURCE_PAYLOAD_INVALID, ErrorCode.SOURCE_TRUNCATED, ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED).contains(code)) {
                assertThatIllegalArgumentException().isThrownBy(() -> new FetchResult.UnitFailure(selector, code, "failed"));
            } else { new FetchResult.UnitFailure(selector, code, "failed"); }
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new FetchResult.UnitFailure(selector, ErrorCode.SOURCE_TIMEOUT, "x".repeat(513)));
        assertThatIllegalArgumentException().isThrownBy(() -> new FetchResult.UnitFailure(selector, ErrorCode.SOURCE_TIMEOUT, " "));
    }

    private static RecoveryPolicy requestPolicy() {
        return new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false, List.of("docs/evidence.md"));
    }
    private static DownloadEnvelope envelope(DownloadStatus status) {
        return new DownloadEnvelope(PluginId.of("fixture"), ApiName.of("daily"), Map.of(),
                status == DownloadStatus.SUCCESS ? List.of("ts_code") : List.of(), 0, List.of(), status,
                status == DownloadStatus.SUCCESS ? null : "Source failed");
    }
}
