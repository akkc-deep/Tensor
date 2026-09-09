package com.akkc.tensor.core.retry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.akkc.tensor.core.download.DownloadExecutionSlot;
import com.akkc.tensor.core.registry.*;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.Instant;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.core.adapter.*;
import static com.akkc.tensor.plugin.api.download.DownloadPolicy.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RetryTaskQueryServiceTest {
    @Test void offlineRecordsRemainVisibleWithSafeParamsAndBusyWins() {
        var id = UUID.randomUUID();
        var storage = mock(RetryTaskStorageService.class);
        var slot = new DownloadExecutionSlot();
        var header = new RetryTaskRepository.Header(id, DatasetKey.of(com.akkc.tensor.plugin.api.model.PluginId.of("removed"), com.akkc.tensor.plugin.api.model.ApiName.of("daily")),
                Map.of("start_date", "20260901", "end_date", "20260910", "token", "SECRET"), Instant.EPOCH, Instant.EPOCH);
        var scope = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE, "2026-09-03");
        var task = new RetryTaskRepository.Task(header, List.of(new RetryTaskRepository.Item(new RetryTaskRepository.ItemKey(id, scope),
                ErrorCode.SOURCE_TIMEOUT, "SQL_SECRET", Instant.EPOCH)));
        when(storage.find(id)).thenReturn(Optional.of(task));
        var queries = new RetryTaskQueryService(storage, new PluginRegistry(List.of()), new AdapterRegistry(List.of()), new ParameterValidator(), slot);
        var detail = queries.get(id);
        assertThat(detail.summary().originalDateRangeStatus().name()).isEqualTo("UNCONFIRMED");
        assertThat(detail.summary().failedScopes()).containsExactly(scope);
        assertThat(detail.taskParams()).containsOnlyKeys("start_date", "end_date");
        assertThat(detail.executionBlocker().code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
        try (var lease = slot.acquire(id)) {
            var busy = queries.get(id);
            assertThat(busy.retrying()).isTrue();
            assertThat(busy.canExecute()).isFalse();
            assertThat(busy.executionBlocker().code()).isEqualTo(ErrorCode.DOWNLOAD_BUSY);
        }
        assertThat(header.taskParams()).containsEntry("token", "SECRET");
        assertThat(task.items().getFirst().errorMessage()).isEqualTo("SQL_SECRET");
    }
    @Test void readableOrphanAndBrokenReadBecomeQueryFailedWithoutDeletingRecords() {
        var storage = mock(RetryTaskStorageService.class);
        var id = UUID.randomUUID();
        var queries = new RetryTaskQueryService(storage, new PluginRegistry(List.of()), new AdapterRegistry(List.of()), new ParameterValidator(), new DownloadExecutionSlot());
        when(storage.find(id)).thenReturn(Optional.of(new RetryTaskRepository.Task(new RetryTaskRepository.Header(id,
                DatasetKey.of(com.akkc.tensor.plugin.api.model.PluginId.of("removed"), com.akkc.tensor.plugin.api.model.ApiName.of("daily")), Map.of(), Instant.EPOCH, Instant.EPOCH), List.of())));
        assertThatThrownBy(() -> queries.get(id)).isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.QUERY_FAILED));
        when(storage.find(id)).thenThrow(new IllegalArgumentException("SQL_SECRET"));
        assertThatThrownBy(() -> queries.get(id)).isInstanceOfSatisfying(TensorException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.QUERY_FAILED); assertThat(e).hasNoCause();
        });
    }
    @Test void originalDatesAreIndependentOfCurrentScopesAndOtherBadConditions() {
        var api = api(Mode.ANN_DATE_RANGE, SourceRequestMode.DATE, RequestEvidenceStatus.DOCUMENTED_CANDIDATE, null, false);
        var fixture = fixture(api, true, true);
        var cases = List.<Map<String, Object>>of(Map.of("exchange", "SSE", "start_date", "20260901", "end_date", "20260910"),
                Map.of("exchange", "SSE"), Map.of("exchange", "SSE", "start_date", "20260901"),
                Map.of("exchange", "SSE", "start_date", "20260230", "end_date", "20260301"),
                Map.of("exchange", "SSE", "start_date", "20260131", "end_date", "20260303"),
                Map.of("exchange", "BAD", "start_date", "20260901", "end_date", "20260910"));
        var statuses = List.of("RECORDED", "NOT_RECORDED", "UNCONFIRMED", "UNCONFIRMED", "UNCONFIRMED", "RECORDED");
        for (int i = 0; i < cases.size(); i++) {
            fixture.store(cases.get(i), scope(RecoverySelector.TimeType.DATE, "2026-09-03"), scope(RecoverySelector.TimeType.DATE, "2026-09-07"));
            var result = fixture.queries().get(fixture.id());
            assertThat(result.summary().originalDateRangeStatus().name()).as("case " + i).isEqualTo(statuses.get(i));
            assertThat(result.summary().failedItemCount()).isEqualTo(2);
            assertThat(result.canExecute()).isEqualTo(i < 2);
            if (i == 0 || i == 5) assertThat(result.summary().originalDateRange()).isEqualTo(new RetryTaskQueryService.OriginalRange("20260901", "20260910"));
        }
        verifyNoInteractions(fixture.plugin());
    }

    @Test void knownOriginalAndOfflineModesPreserveAccurateDateStatus() {
        var original = fixture(api(Mode.ORIGINAL_PARAMS, SourceRequestMode.NONE, RequestEvidenceStatus.DOCUMENTED_CANDIDATE, null, false), true, true);
        original.store(Map.of("exchange", "SSE"), scope(RecoverySelector.TimeType.NONE, ""));
        assertThat(original.queries().get(original.id()).summary().originalDateRangeStatus().name()).isEqualTo("NOT_APPLICABLE");
        original.store(Map.of("exchange", "SSE", "start_date", "20260901"), scope(RecoverySelector.TimeType.NONE, ""));
        assertThat(original.queries().get(original.id()).executionBlocker().code()).isEqualTo(ErrorCode.RETRY_TASK_INVALID);
        var offline = fixture(api(Mode.ANN_DATE_RANGE, SourceRequestMode.DATE, RequestEvidenceStatus.DOCUMENTED_CANDIDATE, null, false), false, true);
        offline.store(Map.of("exchange", "SSE", "start_date", "20260901", "end_date", "20260910"), scope(RecoverySelector.TimeType.DATE, "2026-09-03"));
        var detail = offline.queries().get(offline.id());
        assertThat(detail.summary().originalDateRangeStatus().name()).isEqualTo("RECORDED");
        assertThat(detail.summary().pluginDisplayName()).isEqualTo("Controlled plugin");
        assertThat(detail.executionBlocker().code()).isEqualTo(ErrorCode.PLUGIN_DISABLED);
    }

    @Test void compatibilityPrecedesSourceAndCalendarAndDoesNotTreatCompletenessAsStaticDenial() {
        for (var evidence : List.of(RequestEvidenceStatus.UNCONFIRMED, RequestEvidenceStatus.CONFLICT, RequestEvidenceStatus.DOCUMENTED_CANDIDATE)) {
            for (var calendar : CalendarEvidenceStatus.values()) {
                var fixture = fixture(api(Mode.TRADE_DATE_RANGE, evidence == RequestEvidenceStatus.UNCONFIRMED ? null : SourceRequestMode.DATE, evidence, calendar, false), true, true);
                fixture.store(Map.of("exchange", "BAD"), scope(RecoverySelector.TimeType.DATE, "2026-09-03"));
                assertThat(fixture.queries().get(fixture.id()).executionBlocker().code()).isEqualTo(ErrorCode.RETRY_TASK_INVALID);
                fixture.store(Map.of("exchange", "SSE"), scope(RecoverySelector.TimeType.DATE, "2026-09-03"));
                var detail = fixture.queries().get(fixture.id());
                ErrorCode expected = evidence != RequestEvidenceStatus.DOCUMENTED_CANDIDATE ? ErrorCode.SOURCE_REQUEST_UNCONFIRMED
                        : calendar != CalendarEvidenceStatus.DOCUMENTED ? ErrorCode.CALENDAR_UNCONFIRMED : null;
                assertThat(detail.executionBlocker() == null ? null : detail.executionBlocker().code()).isEqualTo(expected);
                assertThat(detail.canExecute()).isEqualTo(expected == null);
                try (var lease = fixture.slot().acquire(UUID.randomUUID())) {
                    assertThat(fixture.queries().get(fixture.id()).executionBlocker().code()).isEqualTo(ErrorCode.DOWNLOAD_BUSY);
                }
                verifyNoInteractions(fixture.plugin());
            }
        }
        var missing = fixture(api(Mode.ORIGINAL_PARAMS, SourceRequestMode.NONE, RequestEvidenceStatus.DOCUMENTED_CANDIDATE, null, false), true, false);
        missing.store(Map.of("exchange", "SSE"), scope(RecoverySelector.TimeType.NONE, ""));
        assertThat(missing.queries().get(missing.id()).executionBlocker().code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED);
    }

    @Test void stockAuthorizationAndTimeCompatibilityAreCheckedOnEveryItem() {
        var request = scope(RecoverySelector.TimeType.DATE, "2026-09-03");
        var stock = new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ", RecoverySelector.TimeType.DATE, "2026-09-03");
        for (boolean independent : List.of(false, true)) {
            var f = fixture(api(Mode.ANN_DATE_RANGE, SourceRequestMode.RANGE, RequestEvidenceStatus.DOCUMENTED_CANDIDATE, null, independent), true, true);
            f.store(Map.of("exchange", "SSE", "ts_code", "000001.SZ"), request);
            assertThat(f.queries().get(f.id()).canExecute()).isTrue();
            f.store(Map.of("exchange", "SSE"), stock);
            assertThat(f.queries().get(f.id()).canExecute()).isEqualTo(independent);
            f.store(Map.of("exchange", "SSE", "ts_code", "000001.SZ"), stock);
            assertThat(f.queries().get(f.id()).executionBlocker().code()).isEqualTo(ErrorCode.RETRY_TASK_INVALID);
            f.store(Map.of("exchange", "SSE"), new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ", RecoverySelector.TimeType.MONTH, "2026-09"));
            assertThat(f.queries().get(f.id()).executionBlocker().code()).isEqualTo(ErrorCode.RETRY_TASK_INVALID);
        }
    }

    @Test void displayFilteringNeverMakesAnInvalidOriginalMapExecutableAndListDoesNotFindEachTask() {
        var f = fixture(api(Mode.ANN_DATE_RANGE, SourceRequestMode.DATE, RequestEvidenceStatus.DOCUMENTED_CANDIDATE, null, false), true, true);
        var original = new LinkedHashMap<String, Object>(Map.of("exchange", "SSE", "token", "SECRET", "page", "12", "arbitrary", Map.of("secret", "NESTED"), "start_date", "20260230", "end_date", "20260301"));
        var task = f.store(original, scope(RecoverySelector.TimeType.DATE, "2026-09-03"));
        var detail = f.queries().get(f.id());
        assertThat(detail.taskParams()).containsOnlyKeys("exchange", "start_date", "end_date");
        assertThat(detail.executionBlocker().code()).isEqualTo(ErrorCode.RETRY_TASK_INVALID);
        assertThat(task.header().taskParams()).isEqualTo(original);
        clearInvocations(f.storage());
        when(f.storage().list(RetryTaskRepository.Criteria.defaults())).thenAnswer(invocation -> {
            assertThat(f.slot().busy()).isFalse();
            return new RetryTaskRepository.Page(List.of(task), 1, 20, 1, 1);
        });
        var page = f.queries().list(RetryTaskRepository.Criteria.defaults());
        assertThat(page.items()).hasSize(1);
        verify(f.storage()).list(RetryTaskRepository.Criteria.defaults());
        verifyNoMoreInteractions(f.storage());
        verifyNoInteractions(f.plugin());
    }

    @Test void wholeMonthsAndLongSavedSelectorsDoNotReapplyOriginalInputLimit() {
        for (var mode : List.of(Mode.MONTH_RANGE, Mode.NATIVE_RANGE)) {
            var f = fixture(api(mode, mode == Mode.MONTH_RANGE ? SourceRequestMode.MONTH : SourceRequestMode.RANGE, RequestEvidenceStatus.DOCUMENTED_CANDIDATE, null, false), true, true);
            f.store(Map.of("exchange", "SSE"), mode == Mode.MONTH_RANGE ? scope(RecoverySelector.TimeType.MONTH, "2026-02") : scope(RecoverySelector.TimeType.RANGE, "2026-01-01/2026-03-31"));
            assertThat(f.queries().get(f.id()).canExecute()).isTrue();
        }
    }

    @Test void validDatesDoNotMaskInvalidOriginalConditionsAndLaterItemsAreAlsoChecked() {
        var f = fixture(api(Mode.ANN_DATE_RANGE, SourceRequestMode.DATE, RequestEvidenceStatus.DOCUMENTED_CANDIDATE, null, false), true, true);
        for (String key : List.of("unknown", "token", "offset", "trade_date", "ann_date", "month", "page_size", "exchange")) {
            var original = new LinkedHashMap<String, Object>(Map.of("exchange", "SSE", "start_date", "20260901", "end_date", "20260910"));
            original.put(key, key.equals("exchange") ? 123 : "SECRET");
            var task = f.store(original, scope(RecoverySelector.TimeType.DATE, "2026-09-03"));
            var savedBefore = new LinkedHashMap<>(task.header().taskParams());
            var detail = f.queries().get(f.id());
            assertThat(detail.summary().originalDateRangeStatus().name()).isEqualTo("RECORDED");
            assertThat(detail.executionBlocker().code()).isEqualTo(ErrorCode.RETRY_TASK_INVALID);
            assertThat(detail.taskParams()).doesNotContainKey(key);
            assertThat(task.header().taskParams()).isEqualTo(savedBefore);
        }
        f.store(Map.of("exchange", "SSE"), scope(RecoverySelector.TimeType.DATE, "2026-09-03"), scope(RecoverySelector.TimeType.MONTH, "2026-09"));
        assertThat(f.queries().get(f.id()).executionBlocker().code()).isEqualTo(ErrorCode.RETRY_TASK_INVALID);
        verifyNoInteractions(f.plugin());
    }

    private static RecoverySelector scope(RecoverySelector.TimeType time, String value) { return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", time, value); }
    private static ApiDescriptor api(Mode mode, SourceRequestMode sourceMode, RequestEvidenceStatus evidence, CalendarEvidenceStatus calendar, boolean stock) {
        var base = com.akkc.tensor.test.DownloadPolicies.original();
        var recovery = stock ? new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date", RecoverySelector.TimeType.RANGE, true, List.of("docs/test.md")) : base.recoveryPolicy();
        String sourceDate = mode == Mode.TRADE_DATE_RANGE ? "trade_date" : mode == Mode.MONTH_RANGE ? "month" : "ann_date";
        var policy = new DownloadPolicy(mode, switch(mode) {
            case TRADE_DATE_RANGE -> DateSemantic.TRADE_DATE; case ANN_DATE_RANGE -> DateSemantic.ANN_DATE; case MONTH_RANGE -> DateSemantic.COVERED_MONTH;
            case NATIVE_RANGE -> DateSemantic.CALENDAR_DATE; case ORIGINAL_PARAMS -> DateSemantic.NONE;
        }, "Controlled", mode == Mode.TRADE_DATE_RANGE ? CalendarProfile.C_A : null, mode == Mode.ORIGINAL_PARAMS ? null : new Limits(31), sourceMode,
                sourceMode == SourceRequestMode.DATE || sourceMode == SourceRequestMode.MONTH ? sourceDate : null, evidence,
                sourceMode == null ? BatchPlanning.UNCONFIRMED : switch(sourceMode) { case DATE -> BatchPlanning.SINGLE_DATE; case MONTH -> BatchPlanning.SINGLE_MONTH; case RANGE -> BatchPlanning.SOURCE_RANGE; case NONE -> BatchPlanning.ORIGINAL_PARAMS; },
                recovery, base.completenessPolicy(), calendar, base.evidenceRefs());
        var source = new ArrayList<ParameterDescriptor>();
        source.add(new ParameterDescriptor("exchange", "Exchange", null, ParameterType.ENUM, true, null, List.of("SSE", "SZSE"), null, null));
        if (stock || sourceMode == SourceRequestMode.RANGE && mode == Mode.ANN_DATE_RANGE) source.add(new ParameterDescriptor("ts_code", "Stock", null, ParameterType.TS_CODE, true, null, List.of(), null, null));
        if (mode == Mode.NATIVE_RANGE) {
            source.add(new ParameterDescriptor("start_date", "Start", null, ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, "end_date"));
            source.add(new ParameterDescriptor("end_date", "End", null, ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, "start_date"));
        } else if (mode != Mode.ORIGINAL_PARAMS) source.add(new ParameterDescriptor(sourceDate, "Date", null, mode == Mode.MONTH_RANGE ? ParameterType.MONTH : ParameterType.DATE, true, null, List.of(), null, null));
        return new ApiDescriptor(ApiName.of("controlled"), "Controlled API", "test", QueryMode.snapshot, DownloadParameterProjection.project(source, policy), policy, source);
    }
    private static Fixture fixture(ApiDescriptor api, boolean available, boolean withAdapter) {
        var key = DatasetKey.of(PluginId.of("controlled"), api.apiName());
        var plugin = mock(DataSourcePlugin.class);
        when(plugin.descriptor()).thenReturn(new PluginDescriptor(key.pluginId(), "Controlled plugin", "test", true, available, available, available ? null : "Unavailable", List.of(api), List.of(key)));
        when(plugin.readiness()).thenReturn(new PluginReadiness(true, available, available, available ? null : "Unavailable"));
        var plugins = new PluginRegistry(List.of(plugin));
        clearInvocations(plugin);
        var column = new ColumnDefinition("record_id", "record_id", LogicalType.STRING, false, 0, 64, null, null, List.of(), false);
        var definition = new DatasetDefinition(key, "Controlled", "test", QueryMode.snapshot, api.sourceParameters(), TableName.from(key), List.of(column),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("record_id")), List.of(), null, 100);
        var adapters = new AdapterRegistry(withAdapter ? List.of(new GenericDatasetAdapter(definition, new ValueConverter(), new FingerprintKeyCodec())) : List.of());
        var storage = mock(RetryTaskStorageService.class);
        var slot = new DownloadExecutionSlot();
        return new Fixture(UUID.randomUUID(), key, storage, plugin, slot, new RetryTaskQueryService(storage, plugins, adapters, new ParameterValidator(), slot));
    }
    private record Fixture(UUID id, DatasetKey key, RetryTaskStorageService storage, DataSourcePlugin plugin, DownloadExecutionSlot slot, RetryTaskQueryService queries) {
        RetryTaskRepository.Task store(Map<String, Object> params, RecoverySelector... scopes) {
            var task = new RetryTaskRepository.Task(new RetryTaskRepository.Header(id, key, params, Instant.EPOCH, Instant.EPOCH),
                    Arrays.stream(scopes).map(scope -> new RetryTaskRepository.Item(new RetryTaskRepository.ItemKey(id, scope), ErrorCode.SOURCE_TIMEOUT, "HISTORY_SECRET", Instant.EPOCH)).toList());
            when(storage.find(id)).thenAnswer(invocation -> task == null ? Optional.empty() : Optional.of(task));
            return task;
        }
    }

}
