package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.akkc.tensor.core.download.DownloadExecutionResult.*;
import com.akkc.tensor.core.retry.RetryTaskRepository;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.error.SourceException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;

class DownloadServiceTest {
    private static final PluginId PLUGIN_ID = PluginId.of("download_test");
    private static final ApiName API_NAME = ApiName.of("daily");
    private static final DatasetKey KEY = DatasetKey.of(PLUGIN_ID, API_NAME);
    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

    @Test
    void initialAnnouncementAttemptsAllTenDatesOnlyAfterFailuresAreConfirmed() {
        var fixture = new InitialFixture();
        var events = fixture.events;
        var taskId = java.util.UUID.randomUUID();
        fixture.fetch = batch -> {
            String day = (String) batch.sourceParams().get("ann_date");
            events.add("fetch:" + day);
            if (day.equals("20260903") || day.equals("20260907")) {
                throw new com.akkc.tensor.plugin.api.error.SourceException(day.endsWith("03")
                        ? ErrorCode.SOURCE_RATE_LIMITED : ErrorCode.SOURCE_TIMEOUT, "TOKEN SQL sentinel");
            }
            return fixture.result(batch, List.of(List.of(day)));
        };
        when(fixture.failures.create(org.mockito.ArgumentMatchers.eq(KEY), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    assertThat(events.getLast()).isEqualTo("fetch:20260903");
                    assertThat(call.<Map<String,Object>>getArgument(1)).isEqualTo(initialRange());
                    var failure = call.<com.akkc.tensor.core.retry.RetryTaskRepository.Failure>getArgument(2);
                    events.add("saved:03");
                    return new com.akkc.tensor.core.retry.RetryTaskRepository.SavedFailure(
                            new com.akkc.tensor.core.retry.RetryTaskRepository.ItemKey(taskId, failure.selector()));
                });
        when(fixture.failures.append(org.mockito.ArgumentMatchers.eq(taskId), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    assertThat(events.getLast()).isEqualTo("fetch:20260907");
                    var failure = call.<com.akkc.tensor.core.retry.RetryTaskRepository.Failure>getArgument(1);
                    events.add("saved:07");
                    return new com.akkc.tensor.core.retry.RetryTaskRepository.SavedFailure(
                            new com.akkc.tensor.core.retry.RetryTaskRepository.ItemKey(taskId, failure.selector()));
                });
        var result = fixture.service().executeInitial(PLUGIN_ID, API_NAME, initialRange(), RequestId.newId());
        assertThat(events).containsExactly("fetch:20260901", "fetch:20260902", "fetch:20260903", "saved:03",
                "fetch:20260904", "fetch:20260905", "fetch:20260906", "fetch:20260907", "saved:07",
                "fetch:20260908", "fetch:20260909", "fetch:20260910");
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.PARTIAL);
        assertThat(result.completedUnits()).isEqualTo(8);
        assertThat(result.failedUnits()).isEqualTo(2);
        assertThat(result.sourceRowCount()).isEqualTo(8);
        assertThat(result.insertedRows()).isEqualTo(8);
        assertThat(result.updatedRows()).isZero();
        assertThat(result.notStartedUnits()).isZero();
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.remainingFailedUnits()).isEqualTo(2);
        assertThat(result.failureRecordStatus()).isEqualTo(DownloadExecutionResult.FailureRecordStatus.CONFIRMED);
        assertThat(result.toString()).doesNotContain("TOKEN", "SQL sentinel");
    }

    private static Map<String,Object> initialRange() {
        return Map.of("start_date", "20260901", "end_date", "20260910");
    }

    private static class InitialFixture {
        final List<String> events = new ArrayList<>();
        final List<Map<String,Object>> planned = new ArrayList<>();
        final BatchCommitService commits = mock(BatchCommitService.class);
        final com.akkc.tensor.core.retry.RetryTaskStorageService failures = mock(com.akkc.tensor.core.retry.RetryTaskStorageService.class);
        final DownloadExecutionSlot slot = new DownloadExecutionSlot();
        java.util.function.Function<FetchBatch, FetchResult> fetch;
        java.util.function.Function<FetchBatch, DownloadPolicy.BatchPlanning> planning;
        java.util.function.Function<CalendarScope, CalendarDecision> calendar;
        final ApiDescriptor api;
        final DataSourcePlugin plugin;
        final com.akkc.tensor.core.adapter.GenericDatasetAdapter adapter;

        InitialFixture() { this(false, DownloadPolicy.Mode.ANN_DATE_RANGE, DownloadPolicy.SourceRequestMode.DATE); }
        InitialFixture(boolean stock, DownloadPolicy.Mode mode, DownloadPolicy.SourceRequestMode sourceMode) {
            var original = com.akkc.tensor.test.DownloadPolicies.original();
            boolean trade = mode == DownloadPolicy.Mode.TRADE_DATE_RANGE;
            String dateField = trade ? "trade_date" : "ann_date";
            DownloadPolicy.DateSemantic semantic = switch (mode) {
                case TRADE_DATE_RANGE -> DownloadPolicy.DateSemantic.TRADE_DATE;
                case ANN_DATE_RANGE -> DownloadPolicy.DateSemantic.ANN_DATE;
                case MONTH_RANGE -> DownloadPolicy.DateSemantic.COVERED_MONTH;
                case NATIVE_RANGE -> DownloadPolicy.DateSemantic.CALENDAR_DATE;
                case ORIGINAL_PARAMS -> DownloadPolicy.DateSemantic.NONE;
            };
            var advice = switch (sourceMode) {
                case DATE -> DownloadPolicy.BatchPlanning.SINGLE_DATE;
                case MONTH -> DownloadPolicy.BatchPlanning.SINGLE_MONTH;
                case RANGE -> DownloadPolicy.BatchPlanning.SOURCE_RANGE;
                case NONE -> DownloadPolicy.BatchPlanning.ORIGINAL_PARAMS;
            };
            var recovery = stock ? new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date",
                    RecoverySelector.TimeType.DATE, true, List.of("docs/test.md")) : original.recoveryPolicy();
            var policy = new DownloadPolicy(mode, semantic, "Controlled", trade ? DownloadPolicy.CalendarProfile.C_A : null,
                    mode == DownloadPolicy.Mode.ORIGINAL_PARAMS ? null : new DownloadPolicy.Limits(31), sourceMode,
                    sourceMode == DownloadPolicy.SourceRequestMode.DATE ? dateField : sourceMode == DownloadPolicy.SourceRequestMode.MONTH ? "month" : null,
                    DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE, advice, recovery, original.completenessPolicy(),
                    trade ? DownloadPolicy.CalendarEvidenceStatus.UNCONFIRMED : null, original.evidenceRefs());
            var source = new ArrayList<ParameterDescriptor>();
            if (mode == DownloadPolicy.Mode.ANN_DATE_RANGE || mode == DownloadPolicy.Mode.TRADE_DATE_RANGE) source.add(parameter(dateField, ParameterType.DATE, true));
            if (mode == DownloadPolicy.Mode.NATIVE_RANGE) {
                source.add(new ParameterDescriptor("start_date", "Start", null, ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, "end_date"));
                source.add(new ParameterDescriptor("end_date", "End", null, ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, "start_date"));
            }
            if (sourceMode == DownloadPolicy.SourceRequestMode.MONTH) source.add(parameter("month", ParameterType.MONTH, true));
            if (stock) source.add(parameter("ts_code", ParameterType.TS_CODE, false));
            api = new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.snapshot,
                    DownloadParameterProjection.project(source, policy), policy, source);
            var base = definition();
            var columns = new ArrayList<>(base.columns());
            if (stock) {
                columns.add(new ColumnDefinition("ts_code", "Stock", LogicalType.STRING, false, 0, 64, null, null, List.of(), false));
                columns.add(new ColumnDefinition("ann_date", "Date", LogicalType.DATE, false, 0, null, null, null, List.of(), false));
                columns.add(new ColumnDefinition("amount", "Amount", LogicalType.LONG, false, 0, null, null, null, List.of(), false));
            }
            var dataset = new DatasetDefinition(KEY, base.displayName(), base.category(), base.queryMode(), source, base.tableName(),
                    columns, base.businessKey(), base.filters(), base.fixedColumn(), base.batchSize());
            adapter = new com.akkc.tensor.core.adapter.GenericDatasetAdapter(dataset,
                    new com.akkc.tensor.core.adapter.ValueConverter(), new com.akkc.tensor.core.adapter.FingerprintKeyCodec());
            planning = batch -> advice;
            calendar = scope -> {
                var values = new java.util.HashMap<java.time.LocalDate,Boolean>();
                scope.dates().forEach(date -> values.put(date, true));
                return new CalendarDecision(scope, Map.of("SSE", values));
            };
            plugin = new DataSourcePlugin() {
                public PluginDescriptor descriptor() { return new PluginDescriptor(PLUGIN_ID, "Test", "Test", true, true, true, null, List.of(api), List.of(KEY)); }
                public PluginReadiness readiness() { return new PluginReadiness(true, true, true, null); }
                public FetchResult download(ApiName name, Map<String,Object> params, DownloadContext context) { throw new AssertionError("No legacy fallback"); }
                public CalendarDecision confirmCalendar(ApiName name, CalendarScope scope, DownloadContext context) { outsideTransaction(); return calendar.apply(scope); }
                public DownloadPolicy.BatchPlanning planBatch(ApiName name, FetchBatch batch, DownloadContext context) {
                    outsideTransaction(); planned.add(batch.sourceParams()); return planning.apply(batch);
                }
                public FetchResult fetchBatch(ApiName name, FetchBatch batch, DownloadContext context) {
                    outsideTransaction(); return fetch.apply(batch);
                }
            };
            when(commits.commitInitial(any())).thenAnswer(call -> {
                var ready = call.<RecoveryUnitProcessor.ReadyUnit>getArgument(0);
                return new BatchCommitService.Committed(ready.selector(), ready.sourceRowCount(),
                        new com.akkc.tensor.core.persistence.WriteCounts(ready.batch().rows().size(), 0), false);
            });
            fetch = batch -> result(batch, List.of());
        }
        FetchResult result(FetchBatch batch, List<List<Object>> rows) {
            return new FetchResult(new DownloadEnvelope(PLUGIN_ID, API_NAME, batch.sourceParams(),
                    adapter.definition().columns().stream().map(ColumnDefinition::name).toList(), rows.size(), rows, DownloadStatus.SUCCESS, null), List.of());
        }
        UUID confirmFailures() {
            var task = UUID.randomUUID();
            when(failures.create(eq(KEY), anyMap(), any())).thenAnswer(call -> saved(task, call.getArgument(2)));
            when(failures.append(eq(task), any())).thenAnswer(call -> saved(task, call.getArgument(1)));
            return task;
        }
        DownloadService service() { return new DownloadService(new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(adapter)),
                new ParameterValidator(), mock(PersistenceService.class), commits, failures, slot, Clock.fixed(NOW, ZoneOffset.UTC)); }
        DownloadExecutionResult execute(int days) { return service().executeInitial(PLUGIN_ID, API_NAME, range(days), RequestId.newId()); }
    }

    private static ParameterDescriptor parameter(String name, ParameterType type, boolean required) {
        return new ParameterDescriptor(name, name, null, type, required, null, List.of(), null, null);
    }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private static Map<String,Object> range(int days) { return Map.of("start_date", "20260901", "end_date", "202609%02d".formatted(days)); }
    private static RetryTaskRepository.SavedFailure saved(UUID task, RetryTaskRepository.Failure failure) {
        return new RetryTaskRepository.SavedFailure(new RetryTaskRepository.ItemKey(task, failure.selector()));
    }
    private static TensorException saveUnknown() { return new TensorException(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED, "SQL TOKEN sentinel") {}; }
    private static RecoverySelector stock(String value, String date) { return new RecoverySelector(RecoverySelector.TargetType.STOCK, value, RecoverySelector.TimeType.DATE, date); }
    private static InitialFixture stocks(boolean range) { return new InitialFixture(true, DownloadPolicy.Mode.ANN_DATE_RANGE,
            range ? DownloadPolicy.SourceRequestMode.RANGE : DownloadPolicy.SourceRequestMode.DATE); }
    private static List<Object> stockRow(String key, String stock, String date, Object amount) { return List.of(key, stock, date, amount); }

    @Test void allNineBusinessSourceErrorsContinueEvenWhenNotRetryable() {
        var fixture = new InitialFixture();
        var errors = List.of(ErrorCode.SOURCE_AUTH_FAILED, ErrorCode.SOURCE_PERMISSION_DENIED, ErrorCode.SOURCE_RATE_LIMITED,
                ErrorCode.SOURCE_UNAVAILABLE, ErrorCode.SOURCE_NETWORK_ERROR, ErrorCode.SOURCE_TIMEOUT,
                ErrorCode.SOURCE_PAYLOAD_INVALID, ErrorCode.SOURCE_TRUNCATED, ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
        fixture.fetch = batch -> { fixture.events.add(batch.sourceParams().get("ann_date").toString()); throw new SourceException(errors.get(fixture.events.size()-1), "SECRET"); };
        UUID task = fixture.confirmFailures();
        var result = fixture.execute(9);
        assertThat(fixture.events).hasSize(9).doesNotHaveDuplicates();
        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.failures()).extracting(RecoveryUnitProcessor.Failure::errorCode).containsExactlyElementsOf(errors);
        assertThat(result.taskId()).isEqualTo(task);
        assertThat(result.remainingFailedUnits()).isEqualTo(9);
        verify(fixture.failures).create(eq(KEY), eq(range(9)), any());
        verify(fixture.failures, times(8)).append(eq(task), any());
        verifyNoInteractions(fixture.commits);
    }

    @Test void emptyDuplicateAndMixedResultsCountConfirmedUnitsAndFullSourceRows() {
        var fixture = new InitialFixture();
        var empty = fixture.execute(2);
        assertThat(empty.outcome()).isEqualTo(Outcome.EMPTY);
        assertThat(empty.completedUnits()).isEqualTo(2);
        fixture.fetch = batch -> fixture.result(batch, List.of(List.of("same"), List.of("same")));
        var duplicate = fixture.execute(2);
        assertThat(duplicate.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(duplicate.sourceRowCount()).isEqualTo(4);
        assertThat(duplicate.insertedRows()).isEqualTo(1);
        fixture.confirmFailures();
        fixture.fetch = batch -> {
            if (batch.sourceParams().get("ann_date").equals("20260902")) throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "bad");
            return fixture.result(batch, List.of());
        };
        var mixed = fixture.execute(2);
        assertThat(mixed.outcome()).isEqualTo(Outcome.PARTIAL);
        assertThat(mixed.completedUnits()).isEqualTo(1);
        assertThat(mixed.sourceRowCount()).isZero();
    }

    @Test void allCommitStopVariantsPreserveOnlyConfirmedFactsAndReleaseSlot() {
        for (int variant = 0; variant < 4; variant++) {
            var fixture = new InitialFixture();
            fixture.fetch = batch -> { fixture.events.add("fetch"); return fixture.result(batch, List.of(List.of("row"))); };
            int selected = variant;
            reset(fixture.commits);
        when(fixture.commits.commitInitial(any())).thenAnswer(call -> {
                var ready = call.<RecoveryUnitProcessor.ReadyUnit>getArgument(0);
                return switch (selected) {
                    case 0 -> new BatchCommitService.Committed(ready.selector(), 1, new com.akkc.tensor.core.persistence.WriteCounts(1, 0), true);
                    case 1 -> new BatchCommitService.RolledBack(ready.selector(), true);
                    case 2 -> new BatchCommitService.Unavailable(ready.selector());
                    default -> new BatchCommitService.Unconfirmed(ready.selector());
                };
            });
            assertThatThrownBy(() -> fixture.execute(3)).isInstanceOfSatisfying(DownloadExecutionException.class, e -> {
                var result = e.downloadResult();
                assertThat(result.outcome()).isEqualTo(Outcome.UNCONFIRMED);
                assertThat(result.completedUnits()).isEqualTo(selected == 0 ? 1 : 0);
                assertThat(result.sourceRowCount()).isEqualTo(selected == 0 ? 1 : 0);
                assertThat(result.failedUnits()).isEqualTo(selected == 1 || selected == 2 ? 1 : 0);
                assertThat(result.unconfirmedScopes()).hasSize(selected == 3 ? 1 : 0);
                assertThat(result.notStartedUnits()).isEqualTo(2);
                assertThat(result.notStartedScopes()).extracting(RecoverySelector::timeValue).containsExactly("2026-09-02", "2026-09-03");
                assertThat(result.remainingFailedUnits()).isZero();
                assertThat(e.code()).isEqualTo(selected == 0 ? ErrorCode.INTERNAL_ERROR : selected == 3 ? ErrorCode.COMMIT_UNCONFIRMED : ErrorCode.PERSISTENCE_FAILED);
            });
            verifyNoInteractions(fixture.failures);
            assertThat(fixture.events).containsExactly("fetch");
            assertThat(fixture.slot.busy()).isFalse();
        }
    }

    @Test void laterCommitStopsRetainPriorSavedTaskAndConfirmedInsertedAndUpdatedRows() {
        for (int variant = 0; variant < 4; variant++) {
            var fixture = new InitialFixture();
            UUID task = fixture.confirmFailures();
            fixture.fetch = batch -> {
                String day = batch.sourceParams().get("ann_date").toString();
                fixture.events.add(day);
                if (day.equals("20260901")) throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "Source sentinel");
                return fixture.result(batch, List.of(List.of(day + "-a"), List.of(day + "-b")));
            };
            int selected = variant;
            reset(fixture.commits);
            when(fixture.commits.commitInitial(any())).thenAnswer(call -> {
                var ready = call.<RecoveryUnitProcessor.ReadyUnit>getArgument(0);
                if (ready.selector().timeValue().equals("2026-09-02")) {
                    return new BatchCommitService.Committed(ready.selector(), 2,
                            new com.akkc.tensor.core.persistence.WriteCounts(1, 1), false);
                }
                return switch (selected) {
                    case 0 -> new BatchCommitService.Committed(ready.selector(), 2,
                            new com.akkc.tensor.core.persistence.WriteCounts(1, 1), true);
                    case 1 -> new BatchCommitService.RolledBack(ready.selector(), true);
                    case 2 -> new BatchCommitService.Unavailable(ready.selector());
                    default -> new BatchCommitService.Unconfirmed(ready.selector());
                };
            });
            assertThatThrownBy(() -> fixture.execute(4)).isInstanceOfSatisfying(DownloadExecutionException.class, e -> {
                var result = e.downloadResult();
                assertThat(e.code()).isEqualTo(selected == 0 ? ErrorCode.INTERNAL_ERROR
                        : selected == 3 ? ErrorCode.COMMIT_UNCONFIRMED : ErrorCode.PERSISTENCE_FAILED);
                assertThat(result.outcome()).isEqualTo(Outcome.UNCONFIRMED);
                assertThat(result.failureRecordStatus()).isEqualTo(FailureRecordStatus.UNCONFIRMED);
                assertThat(result.taskId()).isEqualTo(task);
                assertThat(result.remainingFailedUnits()).isEqualTo(1);
                assertThat(result.completedUnits()).isEqualTo(selected == 0 ? 2 : 1);
                assertThat(result.sourceRowCount()).isEqualTo(selected == 0 ? 4 : 2);
                assertThat(result.insertedRows()).isEqualTo(selected == 0 ? 2 : 1);
                assertThat(result.updatedRows()).isEqualTo(selected == 0 ? 2 : 1);
                assertThat(result.failedUnits()).isEqualTo(selected == 1 || selected == 2 ? 2 : 1);
                assertThat(result.failures().getFirst().selector().timeValue()).isEqualTo("2026-09-01");
                if (selected == 1 || selected == 2) {
                    assertThat(result.failures().getLast().selector().timeValue()).isEqualTo("2026-09-03");
                    assertThat(result.failures().getLast().errorCode()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
                }
                assertThat(result.notStartedUnits()).isEqualTo(1);
                assertThat(result.notStartedScopes()).extracting(RecoverySelector::timeValue).containsExactly("2026-09-04");
                assertThat(result.unconfirmedScopes()).extracting(RecoverySelector::timeValue)
                        .containsExactlyElementsOf(selected == 3 ? List.of("2026-09-03") : List.of());
                assertThat(result.toString()).doesNotContain("Source sentinel");
            });
            assertThat(fixture.events).containsExactly("20260901", "20260902", "20260903");
            verify(fixture.failures).create(eq(KEY), eq(range(4)), any());
            verify(fixture.failures, never()).append(any(), any());
            verify(fixture.commits, times(2)).commitInitial(any());
            assertThat(fixture.slot.busy()).isFalse();
        }
    }

    @Test void explicitRollbackIsSavedBeforeContinuingAndNeverConfirmsItsIndex() {
        var fixture = new InitialFixture(); fixture.confirmFailures();
        fixture.fetch = batch -> fixture.result(batch, List.of(List.of("same")));
        var calls = new AtomicInteger();
        reset(fixture.commits);
        when(fixture.commits.commitInitial(any())).thenAnswer(call -> {
            var ready = call.<RecoveryUnitProcessor.ReadyUnit>getArgument(0);
            assertThat(ready.batch().rows()).hasSize(1);
            if (calls.getAndIncrement() == 0) return new BatchCommitService.RolledBack(ready.selector(), false);
            verify(fixture.failures).create(eq(KEY), eq(range(2)), any());
            return new BatchCommitService.Committed(ready.selector(), 1, new com.akkc.tensor.core.persistence.WriteCounts(1, 0), false);
        });
        var result = fixture.execute(2);
        assertThat(result.completedUnits()).isEqualTo(1);
        assertThat(result.failedUnits()).isEqualTo(1);
        assertThat(result.sourceRowCount()).isEqualTo(1);
        assertThat(result.failures().getFirst().errorCode()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
    }

    @Test void firstAndAppendSaveUnknownKeepExplicitFailuresButNeverInventTaskOrRemainingCount() {
        for (boolean append : List.of(false, true)) {
            var fixture = new InitialFixture();
            UUID task = fixture.confirmFailures();
            fixture.fetch = batch -> { fixture.events.add("fetch"); throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "SECRET"); };
            if (append) when(fixture.failures.append(eq(task), any())).thenThrow(saveUnknown());
            else when(fixture.failures.create(any(), anyMap(), any())).thenThrow(saveUnknown());
            assertThatThrownBy(() -> fixture.execute(3)).isInstanceOfSatisfying(DownloadExecutionException.class, e -> {
                assertThat(e.code()).isEqualTo(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);
                var result = e.downloadResult();
                assertThat(result.taskId()).isEqualTo(append ? task : null);
                assertThat(result.failedUnits()).isEqualTo(append ? 2 : 1);
                assertThat(result.notStartedUnits()).isEqualTo(append ? 1 : 2);
                assertThat(result.remainingFailedUnits()).isNull();
                assertThat(result.unconfirmedScopes()).isEmpty();
                assertThat(e.toString()+result).doesNotContain("SECRET", "TOKEN", "SQL");
                assertThat(e).hasNoCause();
            });
            assertThat(fixture.events).hasSize(append ? 2 : 1);
            verifyNoInteractions(fixture.commits);
            verify(fixture.failures, never()).find(any());
            assertThat(fixture.slot.busy()).isFalse();
        }
    }

    @Test void stockEventsAreTimeThenStockOrderedAndMixExplicitFailuresBeforeLaterSuccess() {
        var fixture = stocks(true); var task = fixture.confirmFailures();
        fixture.fetch = batch -> {
            var result = fixture.result(batch, List.of(stockRow("c2", "600000.SH", "20260902", 1), stockRow("a2", "000001.SZ", "20260902", 1),
                    stockRow("c1", "600000.SH", "20260901", 1), stockRow("a1", "000001.SZ", "20260901", 1)));
            return new FetchResult(result.envelope(), List.of(new FetchResult.UnitFailure(stock("000002.SZ", "2026-09-01"), ErrorCode.SOURCE_TIMEOUT, "SECRET")));
        };
        reset(fixture.commits);
        when(fixture.commits.commitInitial(any())).thenAnswer(call -> {
            var ready = call.<RecoveryUnitProcessor.ReadyUnit>getArgument(0);
            fixture.events.add(ready.selector().timeValue()+":"+ready.selector().targetValue());
            return new BatchCommitService.Committed(ready.selector(), 1, new com.akkc.tensor.core.persistence.WriteCounts(1,0), false);
        });
        when(fixture.failures.create(eq(KEY), anyMap(), any())).thenAnswer(call -> {
            var failure = call.<RetryTaskRepository.Failure>getArgument(2);
            fixture.events.add(failure.selector().timeValue()+":"+failure.selector().targetValue()); return saved(task, failure);
        });
        var result = fixture.execute(2);
        assertThat(fixture.events).containsExactly("2026-09-01:000001.SZ", "2026-09-01:000002.SZ", "2026-09-01:600000.SH", "2026-09-02:000001.SZ", "2026-09-02:600000.SH");
        assertThat(result.completedUnits()).isEqualTo(4); assertThat(result.failedUnits()).isEqualTo(1);
    }

    @Test void allPreparedFailuresAreKnownBeforeFirstSaveButFutureStockMembershipMakesNUnknown() {
        for (boolean future : List.of(false,true)) {
            var fixture = stocks(false);
            fixture.fetch = batch -> {
                var result = fixture.result(batch, List.of(stockRow("c", "600000.SH", "20260901", 1)));
                return new FetchResult(result.envelope(), List.of(
                        new FetchResult.UnitFailure(stock("000001.SZ", "2026-09-01"), ErrorCode.SOURCE_TIMEOUT, "bad"),
                        new FetchResult.UnitFailure(stock("000002.SZ", "2026-09-01"), ErrorCode.SOURCE_RATE_LIMITED, "bad")));
            };
            when(fixture.failures.create(any(), anyMap(), any())).thenThrow(saveUnknown());
            assertThatThrownBy(() -> fixture.execute(future ? 2 : 1)).isInstanceOfSatisfying(DownloadExecutionException.class, e -> {
                var result = e.downloadResult();
                assertThat(result.failedUnits()).isEqualTo(2);
                assertThat(result.notStartedUnits()).isEqualTo(future ? null : 1L);
                assertThat(result.notStartedScopes()).hasSize(future ? 2 : 1);
                assertThat(result.notStartedScopes().getFirst()).isEqualTo(stock("600000.SH", "2026-09-01"));
                assertThat(result.unconfirmedScopes()).isEmpty();
            });
            verifyNoInteractions(fixture.commits);
            verify(fixture.failures, never()).append(any(), any());
        }
    }

    @Test void stockFieldFailureIsIsolatedAndLaterWholeRequestUsesSameFrozenTask() {
        var fixture = stocks(false); var task = fixture.confirmFailures();
        fixture.fetch = batch -> {
            if (batch.sourceParams().get("ann_date").equals("20260902")) throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "bad");
            return fixture.result(batch, List.of(stockRow("c", "600000.SH", "20260901", 1),
                    stockRow("b", "000002.SZ", "20260901", "bad"), stockRow("a", "000001.SZ", "20260901", 1)));
        };
        var result = fixture.execute(2);
        assertThat(result.completedUnits()).isEqualTo(2); assertThat(result.failedUnits()).isEqualTo(2);
        assertThat(result.failures()).extracting(f -> f.selector().targetType()).containsExactly(RecoverySelector.TargetType.STOCK, RecoverySelector.TargetType.REQUEST);
        verify(fixture.failures).create(eq(KEY), eq(range(2)), any()); verify(fixture.failures).append(eq(task), any());
    }

    @Test void finalBadOwnershipPreventsEveryCommitAndFrozenStockInputFallsBackToRequest() {
        var fixture = stocks(false); fixture.confirmFailures();
        fixture.fetch = batch -> fixture.result(batch, List.of(stockRow("a", "000001.SZ", "20260901", 1), stockRow("b", "bad", "20260901", 1)));
        var invalid = fixture.execute(1);
        assertThat(invalid.completedUnits()).isZero(); assertThat(invalid.failedUnits()).isEqualTo(1);
        assertThat(invalid.failures().getFirst().selector().targetType()).isEqualTo(RecoverySelector.TargetType.REQUEST);
        verifyNoInteractions(fixture.commits);
        fixture.fetch = batch -> fixture.result(batch, List.of(stockRow("a", "000001.SZ", "20260901", "bad")));
        var params = new java.util.HashMap<String,Object>(range(2)); params.put("ts_code", "000001.SZ");
        when(fixture.failures.create(any(), anyMap(), any())).thenThrow(saveUnknown());
        assertThatThrownBy(() -> fixture.service().executeInitial(PLUGIN_ID, API_NAME, params, RequestId.newId()))
                .isInstanceOfSatisfying(DownloadExecutionException.class, e -> {
                    assertThat(e.downloadResult().notStartedUnits()).isEqualTo(1);
                    assertThat(e.downloadResult().failures().getFirst().selector().targetType()).isEqualTo(RecoverySelector.TargetType.REQUEST);
                });
        verify(fixture.failures).create(eq(KEY), eq(params), any());
    }

    @Test void preflightErrorsAndBusyOrderingNeverReachSourceOrStorage() {
        var fixture = new InitialFixture();
        try (var ignored = fixture.slot.acquire(null)) {
            for (var params : List.of(Map.<String,Object>of(), Map.<String,Object>of("start_date", "20260901", "end_date", "20261002"),
                    Map.<String,Object>of("start_date", "20260230", "end_date", "20260301"), Map.<String,Object>of("ann_date", "20260901"),
                    Map.<String,Object>of("start_date", "20260901", "end_date", "20260901", "ann_date", "20260901"))) {
                assertThatThrownBy(() -> fixture.service().executeInitial(PLUGIN_ID, API_NAME, params, RequestId.newId()))
                        .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isIn(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_REQUIRED));
            }
            assertThatThrownBy(() -> fixture.service().executeInitial(PluginId.of("missing"), API_NAME, Map.of(), RequestId.newId()))
                    .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PLUGIN_DISABLED));
            assertThatThrownBy(() -> fixture.execute(1)).isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DOWNLOAD_BUSY));
            assertThat(fixture.planned).isEmpty(); verifyNoInteractions(fixture.commits, fixture.failures);
        }
        var params = Map.<String,Object>of("start_date", "20260831", "end_date", "20260930");
        assertThat(fixture.service().executeInitial(PLUGIN_ID, API_NAME, params, RequestId.newId()).completedUnits()).isEqualTo(31);
        assertThat(fixture.slot.busy()).isFalse();
    }

    @Test void outerActualTransactionOrSynchronizationRefusesBeforePluginCallbacks() {
        var fixture = new InitialFixture();
        for (boolean actual : List.of(false, true)) {
            if (actual) TransactionSynchronizationManager.setActualTransactionActive(true);
            else TransactionSynchronizationManager.initSynchronization();
            try { assertThatThrownBy(() -> fixture.execute(1)).isInstanceOf(IllegalStateException.class); }
            finally { if (actual) TransactionSynchronizationManager.setActualTransactionActive(false); else TransactionSynchronizationManager.clearSynchronization(); }
        }
        assertThat(fixture.planned).isEmpty(); verifyNoInteractions(fixture.commits, fixture.failures);
    }

    @Test void allPlanningCompletesBeforeBusinessAndUnexpectedSourceConditionsAreNotSaved() {
        var fixture = new InitialFixture();
        var unexpected = new IllegalStateException("unexpected");
        fixture.planning = batch -> { if (batch.sourceParams().get("ann_date").equals("20260902")) throw unexpected; return DownloadPolicy.BatchPlanning.SINGLE_DATE; };
        fixture.fetch = batch -> { throw new AssertionError("No prefix fetch"); };
        assertThatThrownBy(() -> fixture.execute(2)).isSameAs(unexpected);
        assertThat(fixture.slot.busy()).isFalse(); verifyNoInteractions(fixture.commits, fixture.failures);
        fixture.planning = batch -> DownloadPolicy.BatchPlanning.SINGLE_DATE;
        fixture.fetch = batch -> { throw new SourceException(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, "Unconfirmed"); };
        assertThatThrownBy(() -> fixture.execute(2)).isInstanceOfSatisfying(SourceException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_REQUEST_UNCONFIRMED));
        verifyNoInteractions(fixture.failures);
        fixture.fetch = batch -> { throw unexpected; };
        assertThatThrownBy(() -> fixture.execute(2)).isSameAs(unexpected);
        assertThat(fixture.slot.busy()).isFalse();
    }

    @Test void slotStaysBusyThroughFetchCommitAndFailureSaveEvenAfterWaiterTimesOut() throws Exception {
        for (String phase : List.of("fetch", "commit", "create", "append")) {
            var fixture = new InitialFixture();
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var fetched = new AtomicInteger(); var task = fixture.confirmFailures();
            fixture.fetch = batch -> {
                int count = fetched.incrementAndGet();
                if (phase.equals("fetch") && count == 1) block(entered, release);
                if (phase.equals("create") || phase.equals("append")) throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "bad");
                return fixture.result(batch, List.of(List.of(batch.sourceParams().get("ann_date"))));
            };
            if (phase.equals("commit")) {
                reset(fixture.commits);
                when(fixture.commits.commitInitial(any())).thenAnswer(call -> {
                    if (fetched.get() == 1) block(entered, release);
                    var ready = call.<RecoveryUnitProcessor.ReadyUnit>getArgument(0);
                    return new BatchCommitService.Committed(ready.selector(), 1, new com.akkc.tensor.core.persistence.WriteCounts(1,0), false);
                });
            }
            if (phase.equals("create")) doAnswer(call -> { block(entered, release); return saved(task, call.getArgument(2)); })
                    .when(fixture.failures).create(any(), anyMap(), any());
            if (phase.equals("append")) doAnswer(call -> { block(entered, release); return saved(task, call.getArgument(1)); })
                    .when(fixture.failures).append(any(), any());
            try (var worker = Executors.newSingleThreadExecutor()) {
                Future<DownloadExecutionResult> execution = worker.submit(() -> fixture.execute(3));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> execution.get(20, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    assertThat(fixture.slot.busy()).isTrue();
                    assertThat(fetched.get()).isEqualTo(phase.equals("append") ? 2 : 1);
                    assertThatThrownBy(() -> fixture.execute(1)).isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DOWNLOAD_BUSY));
                    assertThatThrownBy(() -> fixture.service().execute(PLUGIN_ID, API_NAME, Map.of("ann_date", "20260901"), RequestId.newId()))
                            .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DOWNLOAD_BUSY));
                    assertThatThrownBy(() -> fixture.slot.acquire(UUID.randomUUID())).isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DOWNLOAD_BUSY));
                } finally { release.countDown(); }
                assertThat(execution.get(5, TimeUnit.SECONDS).notStartedUnits()).isZero();
                assertThat(fetched.get()).isEqualTo(3);
                assertThat(fixture.slot.busy()).isFalse();
            }
        }
    }

    private static void block(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Release was not signalled"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    @Test void tradingSegmentsAllClosedMonthsNativeAndOriginalModesExecuteTheirFrozenPlans() {
        var trade = new InitialFixture(false, DownloadPolicy.Mode.TRADE_DATE_RANGE, DownloadPolicy.SourceRequestMode.RANGE);
        trade.calendar = scope -> {
            var first = new java.util.HashMap<java.time.LocalDate,Boolean>();
            var second = new java.util.HashMap<java.time.LocalDate,Boolean>();
            scope.dates().forEach(date -> { first.put(date, List.of(1,2,4).contains(date.getDayOfMonth())); second.put(date, date.getDayOfMonth() == 7); });
            return new CalendarDecision(scope, Map.of("SSE", first, "SZSE", second));
        };
        var fetched = new ArrayList<Map<String,Object>>();
        trade.fetch = batch -> { fetched.add(batch.sourceParams()); return trade.result(batch, List.of()); };
        var trading = trade.execute(7);
        assertThat(trading.completedUnits()).isEqualTo(3); assertThat(trading.skippedClosedDates()).isEqualTo(3);
        assertThat(fetched).containsExactly(Map.of("start_date","20260901","end_date","20260902"),
                Map.of("start_date","20260904","end_date","20260904"), Map.of("start_date","20260907","end_date","20260907"));
        trade.calendar = scope -> {
            var days = new java.util.HashMap<java.time.LocalDate,Boolean>(); scope.dates().forEach(date -> days.put(date,false));
            return new CalendarDecision(scope, Map.of("SSE", days, "SZSE", days));
        };
        trade.planned.clear(); fetched.clear(); clearInvocations(trade.commits);
        var closed = trade.execute(7);
        assertThat(closed.outcome()).isEqualTo(Outcome.NO_OPEN_DATES); assertThat(closed.completedUnits()).isZero();
        assertThat(closed.skippedClosedDates()).isEqualTo(7); assertThat(trade.planned).isEmpty(); assertThat(fetched).isEmpty();
        verifyNoInteractions(trade.commits, trade.failures);
        var month = new InitialFixture(false, DownloadPolicy.Mode.MONTH_RANGE, DownloadPolicy.SourceRequestMode.MONTH);
        month.confirmFailures(); month.fetch = batch -> { throw new SourceException(ErrorCode.SOURCE_TIMEOUT,"bad"); };
        var monthParams = Map.<String,Object>of("start_date","20260131","end_date","20260302");
        var monthly = month.service().executeInitial(PLUGIN_ID, API_NAME, monthParams, RequestId.newId());
        assertThat(monthly.failures()).extracting(f -> f.selector().timeValue()).containsExactly("2026-01","2026-02","2026-03");
        verify(month.failures).create(eq(KEY), eq(monthParams), any());
        for (var mode : List.of(DownloadPolicy.Mode.NATIVE_RANGE, DownloadPolicy.Mode.ORIGINAL_PARAMS)) {
            var fixture = new InitialFixture(false, mode, mode == DownloadPolicy.Mode.NATIVE_RANGE ? DownloadPolicy.SourceRequestMode.RANGE : DownloadPolicy.SourceRequestMode.NONE);
            var params = mode == DownloadPolicy.Mode.NATIVE_RANGE ? range(7) : Map.<String,Object>of();
            var result = fixture.service().executeInitial(PLUGIN_ID, API_NAME, params, RequestId.newId());
            assertThat(result.completedUnits()).isEqualTo(1); assertThat(fixture.planned).containsExactly(params);
            assertThat(result.skippedClosedDates()).isZero(); verifyNoInteractions(fixture.failures);
        }
    }

    @Test void missingCalendarOrIncompatibleAdapterAndSessionRejectBeforeBusiness() {
        var fixture = new InitialFixture(false, DownloadPolicy.Mode.TRADE_DATE_RANGE, DownloadPolicy.SourceRequestMode.DATE);
        fixture.calendar = scope -> null;
        fixture.fetch = batch -> { throw new AssertionError("No business fetch"); };
        assertThatThrownBy(() -> fixture.execute(3)).isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CALENDAR_UNCONFIRMED));
        assertThat(fixture.planned).isEmpty();
        var incompatible = mock(DatasetAdapter.class); when(incompatible.datasetKey()).thenReturn(KEY); when(incompatible.definition()).thenReturn(fixture.adapter.definition());
        var service = new DownloadService(new PluginRegistry(List.of(fixture.plugin)), new AdapterRegistry(List.of(incompatible)),
                new ParameterValidator(), mock(PersistenceService.class), fixture.commits, fixture.failures, fixture.slot, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.executeInitial(PLUGIN_ID, API_NAME, range(3), RequestId.newId()))
                .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED));
        var invalidAdapter = new com.akkc.tensor.core.adapter.GenericDatasetAdapter(definition(), new com.akkc.tensor.core.adapter.ValueConverter(), new com.akkc.tensor.core.adapter.FingerprintKeyCodec());
        var initial = new InitialFixture();
        var invalid = new DownloadService(new PluginRegistry(List.of(initial.plugin)), new AdapterRegistry(List.of(invalidAdapter)),
                new ParameterValidator(), mock(PersistenceService.class), initial.commits, initial.failures, initial.slot, Clock.fixed(NOW, ZoneOffset.UTC));
        initial.fetch = batch -> { throw new AssertionError("No fetch before sessions validated"); };
        assertThatThrownBy(() -> invalid.executeInitial(PLUGIN_ID, API_NAME, range(3), RequestId.newId()))
                .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED));
        assertThat(initial.planned).hasSize(3); verifyNoInteractions(initial.commits, initial.failures);
    }

    @Test
    void mapsDataAndTransactionPersistenceFailuresWithoutChangingEarlierFailureBoundaries() {
        for (RuntimeException failure : List.of(
                new DataAccessResourceFailureException("database failed"),
                new TransactionSystemException("transaction failed"))) {
            PersistenceService persistence = mock(PersistenceService.class);
            when(persistence.persist(org.mockito.ArgumentMatchers.any())).thenThrow(failure);

            assertThatThrownBy(() -> service(persistence).execute(
                            PLUGIN_ID, API_NAME, Map.of(), RequestId.newId()))
                    .isInstanceOfSatisfying(TensorException.class, exception -> {
                        assertThat(exception.code()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
                        assertThat(exception).hasMessage("Dataset persistence failed").hasCause(failure);
                    });
        }
    }

    @Test
    void sourceParametersRemainTheOnlyCurrentExecutionInputs() {
        var source = List.of(new com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor("trade_date", "Trade date", null,
                com.akkc.tensor.plugin.api.descriptor.ParameterType.DATE, true, null, List.of(), null, null));
        var policy = com.akkc.tensor.test.DownloadPolicies.tradeRange();
        var api = new ApiDescriptor(API_NAME, "Daily", "Market", QueryMode.trade_date,
                com.akkc.tensor.plugin.api.download.DownloadParameterProjection.project(source, policy), policy, source);
        var params = Map.<String,Object>of("trade_date", "20260903");
        var plugin = mock(DataSourcePlugin.class);
        when(plugin.descriptor()).thenReturn(new PluginDescriptor(PLUGIN_ID, "Test", "Test", true, true, true, null, List.of(api), List.of(KEY)));
        when(plugin.readiness()).thenReturn(new PluginReadiness(true, true, true, null));
        var envelope = new DownloadEnvelope(PLUGIN_ID, API_NAME, params, List.of("value"), 0, List.of(), DownloadStatus.SUCCESS, null);
        when(plugin.download(org.mockito.ArgumentMatchers.eq(API_NAME), org.mockito.ArgumentMatchers.eq(params), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FetchResult(envelope, List.of()));
        var adapter = mock(DatasetAdapter.class);
        when(adapter.datasetKey()).thenReturn(KEY);
        when(adapter.definition()).thenReturn(definition());
        var persistence = mock(PersistenceService.class);
        var service = new DownloadService(new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(adapter)),
                new ParameterValidator(), persistence, mock(BatchCommitService.class),
                mock(com.akkc.tensor.core.retry.RetryTaskStorageService.class), new DownloadExecutionSlot(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(service.execute(PLUGIN_ID, API_NAME, params, RequestId.newId()).outcome())
                .isEqualTo(com.akkc.tensor.plugin.api.download.DownloadOutcome.EMPTY);
        org.mockito.Mockito.verify(plugin).download(org.mockito.ArgumentMatchers.eq(API_NAME), org.mockito.ArgumentMatchers.eq(params), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).adapt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(persistence);
        assertThatThrownBy(() -> new ParameterValidator().validate(api, params))
                .isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PARAM_REQUIRED));
        assertThat(new ParameterValidator().validate(api.sourceParameters(), params).values()).isEqualTo(params);
    }

    @Test
    void rejectsNullOrExplicitUnitFailuresBeforeAdaptationOrPersistence() {
        var envelope = new DownloadEnvelope(PLUGIN_ID, API_NAME, Map.of(), List.of("value"), 1, List.of(List.of("row")), DownloadStatus.SUCCESS, null);
        var failure = new FetchResult.UnitFailure(new com.akkc.tensor.plugin.api.download.RecoverySelector(
                com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType.REQUEST, "",
                com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.NONE, ""), ErrorCode.SOURCE_TRUNCATED, "Source is truncated");
        var missingEnvelope = mock(FetchResult.class);
        for (FetchResult fetched : java.util.Arrays.asList(null, missingEnvelope, new FetchResult(envelope, List.of(failure)))) {
            var plugin = mock(DataSourcePlugin.class);
            var api = new ApiDescriptor(API_NAME, "Daily", "Market", QueryMode.snapshot, List.of(), com.akkc.tensor.test.DownloadPolicies.original(), List.of());
            when(plugin.descriptor()).thenReturn(new PluginDescriptor(PLUGIN_ID, "Test", "Test", true, true, true, null, List.of(api), List.of(KEY)));
            when(plugin.readiness()).thenReturn(new PluginReadiness(true, true, true, null));
            when(plugin.download(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(fetched);
            var adapter = mock(DatasetAdapter.class);
            when(adapter.datasetKey()).thenReturn(KEY); when(adapter.definition()).thenReturn(definition());
            var persistence = mock(PersistenceService.class);
            var service = new DownloadService(new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(adapter)),
                    new ParameterValidator(), persistence, mock(BatchCommitService.class),
                mock(com.akkc.tensor.core.retry.RetryTaskStorageService.class), new DownloadExecutionSlot(), Clock.fixed(NOW, ZoneOffset.UTC));
            assertThatThrownBy(() -> service.execute(PLUGIN_ID, API_NAME, Map.of(), RequestId.newId()))
                    .isInstanceOfSatisfying(TensorException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
                        assertThat(e.getMessage()).isEqualTo("Source returned an invalid payload");
                    });
            org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).adapt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.verifyNoInteractions(persistence);
        }
    }

    private static DownloadService service(PersistenceService persistence) {
        DatasetDefinition definition = definition();
        AdaptedBatch batch = new AdaptedBatch(
                KEY, TableName.from(KEY), List.of("value"), List.of(Map.of("value", "row")),
                definition.businessKey(), NOW);
        DataSourcePlugin plugin = new DataSourcePlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return new PluginDescriptor(
                        PLUGIN_ID, "Download test", "Download test", true, true, true, null,
                        List.of(new ApiDescriptor(API_NAME, "Daily", "market", QueryMode.snapshot, List.of(), com.akkc.tensor.test.DownloadPolicies.original(), List.of())),
                        List.of(KEY));
            }

            @Override
            public PluginReadiness readiness() {
                return new PluginReadiness(true, true, true, null);
            }

            @Override
            public FetchResult download(ApiName apiName, Map<String, Object> params, DownloadContext context) {
                return new FetchResult(new DownloadEnvelope(
                        PLUGIN_ID, API_NAME, Map.of(), List.of("value"), 1, List.of(List.of("row")),
                        DownloadStatus.SUCCESS, null), List.of());
            }
        };
        DatasetAdapter adapter = new DatasetAdapter() {
            @Override
            public DatasetKey datasetKey() {
                return KEY;
            }

            @Override
            public DatasetDefinition definition() {
                return definition;
            }

            @Override
            public AdaptedBatch adapt(DownloadEnvelope envelope, Instant ingestedAt) {
                return batch;
            }
        };
        return new DownloadService(
                new PluginRegistry(List.of(plugin)), new AdapterRegistry(List.of(adapter)),
                new ParameterValidator(), persistence, mock(BatchCommitService.class),
                mock(com.akkc.tensor.core.retry.RetryTaskStorageService.class), new DownloadExecutionSlot(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DatasetDefinition definition() {
        return new DatasetDefinition(
                KEY, "Daily", "market", QueryMode.snapshot, List.of(), TableName.from(KEY),
                List.of(new ColumnDefinition(
                        "value", "Value", LogicalType.STRING, false, 0, 64, null, null, List.of(), false)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("value")),
                List.of(), null, 500);
    }
}
