package com.akkc.tensor.core.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.adapter.BusinessContentCodec;
import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.persistence.BusinessKeyExtractor;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.retry.RetryTaskRepository;
import com.akkc.tensor.core.retry.RetryTaskRepository.Header;
import com.akkc.tensor.core.retry.RetryTaskRepository.Item;
import com.akkc.tensor.core.retry.RetryTaskRepository.ItemKey;
import com.akkc.tensor.core.retry.RetryTaskRepository.SavedFailure;
import com.akkc.tensor.core.retry.RetryTaskRepository.Task;
import com.akkc.tensor.core.retry.RetryTaskStorageService;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.CalendarDecision;
import com.akkc.tensor.plugin.api.download.CalendarScope;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadParameterProjection;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RetryDownloadServiceTest {
    private static final PluginId PLUGIN = PluginId.of("retry_test");
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    /** Catches using the original 1-10 display range, splitting a saved range, or updating the wrong key. */
    @Test
    void marginRetriesOnlySavedDatesAndKeepsSavedRangeWhole() {
        var fixture = Fixture.request("margin", DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
        UUID taskId = UUID.randomUUID();
        var day3 = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        var day7 = request(RecoverySelector.TimeType.DATE, "2026-09-07");
        var original = Map.<String, Object>of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910");
        fixture.find(taskId, task(taskId, fixture.key, original, day3, day7));
        fixture.fetch = batch -> {
            if ("20260907".equals(batch.sourceParams().get("start_date"))) {
                throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "secret timeout");
            }
            return fixture.result(batch, List.of(List.of("margin-3")));
        };

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.requests).containsExactly(
                Map.of("exchange_id", "SSE", "start_date", "20260903", "end_date", "20260903"),
                Map.of("exchange_id", "SSE", "start_date", "20260907", "end_date", "20260907"));
        assertThat(fixture.committed).containsExactly(new ItemKey(taskId, day3));
        assertThat(fixture.updatedReasons).containsExactly(Map.entry(new ItemKey(taskId, day7), ErrorCode.SOURCE_TIMEOUT));
        assertThat(result.completedUnits()).isEqualTo(1);
        assertThat(result.failedUnits()).isEqualTo(1);
        assertThat(result.notStartedUnits()).isZero();
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.remainingFailedUnits()).isEqualTo(1);
        assertThat(original).containsExactlyInAnyOrderEntriesOf(Map.of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910"));

        UUID rangeTask = UUID.randomUUID();
        var range = request(RecoverySelector.TimeType.RANGE, "2026-09-04/2026-09-06");
        fixture.find(rangeTask, task(rangeTask, fixture.key, original, range));
        fixture.requests.clear();
        fixture.fetch = batch -> fixture.result(batch, List.of(List.of("margin-range")));
        fixture.service().execute(rangeTask, RequestId.newId());
        assertThat(fixture.requests).containsExactly(Map.of(
                "exchange_id", "SSE", "start_date", "20260904", "end_date", "20260906"));
        assertThat(fixture.committed).contains(new ItemKey(rangeTask, range));
    }

    /** Catches coalescing same-day stocks, deleting the sibling key, or allocating a new retry task id. */
    @Test
    void sameDayStocksRetryAndDeleteByTheirCompleteOriginalKeys() {
        var fixture = Fixture.stock("fina_audit");
        UUID taskId = UUID.randomUUID();
        var stockA = stock("000001.SZ", "2026-09-03");
        var stockB = stock("600000.SH", "2026-09-03");
        var original = Map.<String, Object>of("start_date", "20260901", "end_date", "20260910");
        var attempts = new AtomicInteger();
        fixture.find(taskId,
                task(taskId, fixture.key, original, stockA, stockB),
                task(taskId, fixture.key, original, stockB));
        fixture.fetch = batch -> {
            String code = (String) batch.sourceParams().get("ts_code");
            if (code.equals("600000.SH") && attempts.getAndIncrement() == 0) {
                throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "secret timeout");
            }
            return fixture.result(batch, List.of(List.of("audit-" + code, code, "20260903")));
        };

        var first = fixture.service().execute(taskId, RequestId.newId());
        var second = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.requests).containsExactly(
                stockRequest("000001.SZ"), stockRequest("600000.SH"), stockRequest("600000.SH"));
        assertThat(fixture.committed).containsExactly(new ItemKey(taskId, stockA), new ItemKey(taskId, stockB));
        assertThat(fixture.updatedReasons).containsExactly(Map.entry(new ItemKey(taskId, stockB), ErrorCode.SOURCE_TIMEOUT));
        assertThat(first.completedUnits()).isEqualTo(1);
        assertThat(first.failedUnits()).isEqualTo(1);
        assertThat(first.taskId()).isEqualTo(taskId);
        assertThat(first.remainingFailedUnits()).isEqualTo(1);
        assertThat(second.completedUnits()).isEqualTo(1);
        assertThat(second.failedUnits()).isZero();
        assertThat(second.taskId()).isNull();
        assertThat(second.remainingFailedUnits()).isZero();
    }

    /** Catches emitting trade_date/ann_date for native ranges or preserving an empty successful item. */
    @Test
    void nativeTradeCalendarDateUsesEqualEndpointsAndDeletesAConfirmedEmptyItem() {
        var fixture = Fixture.request("trade_cal", DownloadPolicy.Mode.NATIVE_RANGE, "exchange");
        UUID taskId = UUID.randomUUID();
        var day3 = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        fixture.find(taskId, task(taskId, fixture.key, Map.of(
                "exchange", "SSE", "start_date", "20260901", "end_date", "20260910"), day3));
        fixture.fetch = batch -> fixture.result(batch, List.of());

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.requests).containsExactly(Map.of(
                "exchange", "SSE", "start_date", "20260903", "end_date", "20260903"));
        assertThat(fixture.requests.getFirst()).doesNotContainKeys("trade_date", "ann_date");
        assertThat(fixture.committed).containsExactly(new ItemKey(taskId, day3));
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.EMPTY);
        assertThat(result.completedUnits()).isEqualTo(1);
        assertThat(result.sourceRowCount()).isZero();
        assertThat(result.insertedRows()).isZero();
        assertThat(result.updatedRows()).isZero();
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isZero();
    }

    @Test
    void publicSurfaceInputSlotAndStoredTaskChecksPrecedeAllExternalCallbacks() {
        assertThat(RetryDownloadService.class.getDeclaredConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        PluginRegistry.class, AdapterRegistry.class, ParameterValidator.class,
                        BatchCommitService.class, RetryTaskStorageService.class,
                        DownloadExecutionSlot.class, Clock.class));
        assertThat(java.util.Arrays.stream(RetryDownloadService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .singleElement().satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("execute");
                    assertThat(method.getParameterTypes()).containsExactly(UUID.class, RequestId.class);
                    assertThat(method.getReturnType()).isEqualTo(DownloadExecutionResult.class);
                });

        var fixture = Fixture.request("margin", DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
        UUID taskId = UUID.randomUUID();
        fixture.find(taskId, task(taskId, fixture.key, Map.of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910"),
                request(RecoverySelector.TimeType.DATE, "2026-09-03")));
        assertThatThrownBy(() -> fixture.service().execute(null, RequestId.newId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> fixture.service().execute(taskId, null))
                .isInstanceOf(NullPointerException.class);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> fixture.service().execute(taskId, RequestId.newId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Retry orchestration must not run in a transaction");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(fixture.findCalls).hasValue(0);
        try (var ignored = fixture.slot.acquire(null)) {
            assertCode(() -> fixture.service().execute(taskId, RequestId.newId()), ErrorCode.DOWNLOAD_BUSY);
        }
        assertThat(fixture.findCalls).hasValue(0);
        assertCode(() -> fixture.service().execute(UUID.randomUUID(), RequestId.newId()),
                ErrorCode.RETRY_TASK_NOT_FOUND);
        UUID emptyTask = UUID.randomUUID();
        fixture.find(emptyTask, new Task(new Header(emptyTask, fixture.key, Map.of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910"),
                NOW, NOW), List.of()));
        assertCode(() -> fixture.service().execute(emptyTask, RequestId.newId()),
                ErrorCode.RETRY_TASK_INVALID);
        assertThat(fixture.requests).isEmpty();
        assertThat(fixture.planned).isEmpty();
        assertThat(fixture.committed).isEmpty();
        assertThat(fixture.updatedReasons).isEmpty();
    }

    @Test
    void pluginAdapterLastMappingAndLastPlanFailuresLeaveEveryItemUntouched() {
        var fixture = Fixture.stock("fina_audit");
        UUID taskId = UUID.randomUUID();
        var first = stock("000001.SZ", "2026-09-03");
        var incompatible = new RecoverySelector(RecoverySelector.TargetType.STOCK, "600000.SH",
                RecoverySelector.TimeType.RANGE, "2026-09-04/2026-09-06");
        fixture.find(taskId, task(taskId, fixture.key,
                Map.of("start_date", "20260901", "end_date", "20260910"), first, incompatible));
        assertCode(() -> fixture.service().execute(taskId, RequestId.newId()), ErrorCode.RETRY_TASK_INVALID);
        assertThat(fixture.planned).isEmpty();
        assertThat(fixture.requests).isEmpty();
        assertThat(fixture.committed).isEmpty();
        assertThat(fixture.updatedReasons).isEmpty();

        var margin = Fixture.request("margin", DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
        UUID marginTask = UUID.randomUUID();
        margin.find(marginTask, task(marginTask, margin.key, Map.of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910"),
                request(RecoverySelector.TimeType.DATE, "2026-09-03"),
                request(RecoverySelector.TimeType.DATE, "2026-09-07")));
        margin.planning = batch -> batch.sourceParams().get("start_date").equals("20260907")
                ? DownloadPolicy.BatchPlanning.SINGLE_MONTH
                : DownloadPolicy.BatchPlanning.SOURCE_RANGE;
        assertCode(() -> margin.service().execute(marginTask, RequestId.newId()),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertThat(margin.planned).hasSize(2);
        assertThat(margin.requests).isEmpty();
        assertThat(margin.committed).isEmpty();
        assertThat(margin.updatedReasons).isEmpty();

        assertCode(() -> margin.service(new PluginRegistry(List.of()),
                new AdapterRegistry(List.of(margin.adapter))).execute(marginTask, RequestId.newId()),
                ErrorCode.PLUGIN_DISABLED);
        assertCode(() -> margin.service(new PluginRegistry(List.of(margin.plugin)),
                new AdapterRegistry(List.of())).execute(marginTask, RequestId.newId()),
                ErrorCode.DATASET_MISCONFIGURED);
    }

    @Test
    void monthAndOriginalSelectorsRebuildOnlyTheirSavedSourceParameters() {
        var month = Fixture.month("broker_recommend");
        UUID monthTask = UUID.randomUUID();
        var february = request(RecoverySelector.TimeType.MONTH, "2026-02");
        month.find(monthTask, task(monthTask, month.key,
                Map.of("start_date", "20260131", "end_date", "20260302"), february));
        month.fetch = batch -> month.result(batch, List.of(List.of("february")));
        month.service().execute(monthTask, RequestId.newId());
        assertThat(month.requests).containsExactly(Map.of("month", "202602"));
        assertThat(month.calendarScopes).isEmpty();

        var original = Fixture.original("stock_basic");
        UUID originalTask = UUID.randomUUID();
        var none = request(RecoverySelector.TimeType.NONE, "");
        original.find(originalTask, task(originalTask, original.key,
                Map.of("list_status", "L"), none));
        original.fetch = batch -> original.result(batch, List.of(List.of("listed")));
        original.service().execute(originalTask, RequestId.newId());
        assertThat(original.requests).containsExactly(Map.of("list_status", "L"));
        assertThat(original.calendarScopes).isEmpty();
    }

    @Test
    void fullCalendarPreflightUsesOnlyCurrentSelectorsAndAnyFailurePreventsAllWork() {
        var fixture = Fixture.request("margin", DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
        UUID taskId = UUID.randomUUID();
        var day3 = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        var range = request(RecoverySelector.TimeType.RANGE, "2026-09-04/2026-09-06");
        var params = Map.<String, Object>of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910");
        fixture.find(taskId, task(taskId, fixture.key, params, day3, range));
        fixture.fetch = batch -> fixture.result(batch, List.of());
        fixture.service().execute(taskId, RequestId.newId());
        assertThat(fixture.calendarScopes).containsExactly(new CalendarScope(params, Set.of(
                java.time.LocalDate.parse("2026-09-03"), java.time.LocalDate.parse("2026-09-04"),
                java.time.LocalDate.parse("2026-09-05"), java.time.LocalDate.parse("2026-09-06"))));

        var rejected = Fixture.request("margin", DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
        rejected.find(taskId, task(taskId, rejected.key, params, day3, range));
        rejected.calendar = scope -> { throw new com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException(); };
        assertCode(() -> rejected.service().execute(taskId, RequestId.newId()),
                ErrorCode.CALENDAR_UNCONFIRMED);
        assertThat(rejected.planned).isEmpty();
        assertThat(rejected.requests).isEmpty();
        assertThat(rejected.committed).isEmpty();
        assertThat(rejected.closed).isEmpty();
        assertThat(rejected.updatedReasons).isEmpty();
    }

    @Test
    void sameClosedDateAcrossTwoStocksCountsOnceAndNeverCallsBusinessSource() {
        var fixture = Fixture.tradeStock("margin_detail");
        UUID taskId = UUID.randomUUID();
        var a = stock("000001.SZ", "2026-09-04");
        var b = stock("600000.SH", "2026-09-04");
        var params = Map.<String, Object>of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910");
        fixture.find(taskId, task(taskId, fixture.key, params, a, b));
        fixture.calendar = scope -> closed(scope, "SSE");

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.planned).isEmpty();
        assertThat(fixture.requests).isEmpty();
        assertThat(fixture.closed).containsExactly(new ItemKey(taskId, a), new ItemKey(taskId, b));
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.NO_OPEN_DATES);
        assertThat(result.completedUnits()).isZero();
        assertThat(result.skippedClosedDates()).isEqualTo(1);
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isZero();
    }

    @Test
    void mixedRangeStaysWholeAndMakesOverlappingClosedDateNotSkipped() {
        var fixture = Fixture.request("margin", DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
        UUID taskId = UUID.randomUUID();
        var whole = request(RecoverySelector.TimeType.RANGE, "2026-09-03/2026-09-07");
        var closedDay = request(RecoverySelector.TimeType.DATE, "2026-09-04");
        var params = Map.<String, Object>of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910");
        fixture.find(taskId, task(taskId, fixture.key, params, whole, closedDay));
        fixture.calendar = scope -> {
            var dates = new HashMap<java.time.LocalDate, Boolean>();
            scope.dates().forEach(date -> dates.put(date,
                    date.getDayOfMonth() == 3 || date.getDayOfMonth() == 7));
            return new CalendarDecision(scope, Map.of("SSE", dates));
        };
        fixture.fetch = batch -> fixture.result(batch, List.of(List.of("whole-range")));

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.requests).containsExactly(Map.of(
                "exchange_id", "SSE", "start_date", "20260903", "end_date", "20260907"));
        assertThat(fixture.committed).containsExactly(new ItemKey(taskId, whole));
        assertThat(fixture.closed).containsExactly(new ItemKey(taskId, closedDay));
        assertThat(result.completedUnits()).isEqualTo(1);
        assertThat(result.skippedClosedDates()).isZero();
        assertThat(result.taskId()).isNull();
    }

    @Test
    void allNineExplicitSourceFailuresUpdateTheirOriginalKeysAndContinue() {
        var fixture = Fixture.request("income", DownloadPolicy.Mode.ANN_DATE_RANGE, null);
        UUID taskId = UUID.randomUUID();
        var codes = List.of(ErrorCode.SOURCE_AUTH_FAILED, ErrorCode.SOURCE_PERMISSION_DENIED,
                ErrorCode.SOURCE_RATE_LIMITED, ErrorCode.SOURCE_UNAVAILABLE,
                ErrorCode.SOURCE_NETWORK_ERROR, ErrorCode.SOURCE_TIMEOUT,
                ErrorCode.SOURCE_PAYLOAD_INVALID, ErrorCode.SOURCE_TRUNCATED,
                ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
        var selectors = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(day -> request(RecoverySelector.TimeType.DATE,
                        "2026-09-%02d".formatted(day))).toArray(RecoverySelector[]::new);
        fixture.find(taskId, task(taskId, fixture.key,
                Map.of("start_date", "20260901", "end_date", "20260910"), selectors));
        var next = new AtomicInteger();
        fixture.fetch = batch -> { throw new SourceException(codes.get(next.getAndIncrement()), "secret"); };

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.requests).hasSize(9);
        assertThat(fixture.updatedReasons).extracting(Map.Entry::getValue)
                .containsExactlyElementsOf(codes);
        assertThat(result.completedUnits()).isZero();
        assertThat(result.failedUnits()).isEqualTo(9);
        assertThat(result.remainingFailedUnits()).isEqualTo(9);
        assertThat(result.taskId()).isEqualTo(taskId);
    }

    @Test
    void explicitUnitFailureKeepsTheWholeOriginalItemAndWaitsForItsConfirmedUpdate() {
        var fixture = Fixture.request("income", DownloadPolicy.Mode.ANN_DATE_RANGE, null);
        UUID taskId = UUID.randomUUID();
        var first = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        var second = request(RecoverySelector.TimeType.DATE, "2026-09-07");
        fixture.find(taskId, task(taskId, fixture.key,
                Map.of("start_date", "20260901", "end_date", "20260910"), first, second));
        var updateConfirmed = new AtomicBoolean();
        var calls = new AtomicInteger();
        fixture.fetch = batch -> {
            if (calls.getAndIncrement() == 0) {
                var result = fixture.result(batch, List.of(List.of("must-not-commit")));
                return new FetchResult(result.envelope(), List.of(new FetchResult.UnitFailure(
                        first, ErrorCode.SOURCE_RATE_LIMITED, "source-secret")));
            }
            assertThat(updateConfirmed).isTrue();
            return fixture.result(batch, List.of(List.of("later")));
        };
        fixture.update = (item, code) -> {
            assertThat(item).isEqualTo(new ItemKey(taskId, first));
            assertThat(code).isEqualTo(ErrorCode.SOURCE_RATE_LIMITED);
            updateConfirmed.set(true);
            return new SavedFailure(item);
        };

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.committed).containsExactly(new ItemKey(taskId, second));
        assertThat(fixture.updatedReasons).containsExactly(
                Map.entry(new ItemKey(taskId, first), ErrorCode.SOURCE_RATE_LIMITED));
        assertThat(result.completedUnits()).isEqualTo(1);
        assertThat(result.failedUnits()).isEqualTo(1);
        assertThat(result.sourceRowCount()).isEqualTo(1);
        assertThat(result.remainingFailedUnits()).isEqualTo(1);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(first);
            assertThat(failure.errorMessage()).doesNotContain("secret", "must-not-commit");
        });
    }

    @Test
    void trailingForeignOwnerRejectsTheWholeStockItemBeforeTheNextItemStarts() {
        var fixture = Fixture.stock("fina_audit");
        UUID taskId = UUID.randomUUID();
        var first = stock("000001.SZ", "2026-09-03");
        var second = stock("000002.SZ", "2026-09-07");
        fixture.find(taskId, task(taskId, fixture.key,
                Map.of("start_date", "20260901", "end_date", "20260910"), first, second));
        var updateConfirmed = new AtomicBoolean();
        var calls = new AtomicInteger();
        fixture.fetch = batch -> {
            if (calls.getAndIncrement() == 0) {
                return fixture.result(batch, List.of(
                        List.of("valid-prefix", "000001.SZ", "20260903"),
                        List.of("foreign-tail", "600000.SH", "20260903")));
            }
            assertThat(updateConfirmed).isTrue();
            return fixture.result(batch, List.of(
                    List.of("later", "000002.SZ", "20260907")));
        };
        fixture.update = (item, code) -> {
            assertThat(item).isEqualTo(new ItemKey(taskId, first));
            assertThat(code).isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
            updateConfirmed.set(true);
            return new SavedFailure(item);
        };

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.committed).containsExactly(new ItemKey(taskId, second));
        assertThat(fixture.updatedReasons).containsExactly(
                Map.entry(new ItemKey(taskId, first), ErrorCode.SOURCE_PAYLOAD_INVALID));
        assertThat(result.completedUnits()).isEqualTo(1);
        assertThat(result.failedUnits()).isEqualTo(1);
        assertThat(result.sourceRowCount()).isEqualTo(1);
        assertThat(result.remainingFailedUnits()).isEqualTo(1);
    }

    @Test
    void adapterMissingTypeAndConflictFailuresKeepTheirOriginalKeysAndContinue() {
        record Case(String name, ErrorCode code, List<List<Object>> rows) {}
        var cases = List.of(
                new Case("missing", ErrorCode.ADAPTER_FIELD_MISSING,
                        List.of(java.util.Arrays.<Object>asList("missing", null))),
                new Case("type", ErrorCode.ADAPTER_TYPE_INVALID,
                        List.of(List.of("type", "not-a-decimal"))),
                new Case("conflict", ErrorCode.DATA_CONFLICT,
                        List.of(List.of("same-key", "1.00"), List.of("same-key", "2.00"))));
        for (Case candidate : cases) {
            var fixture = Fixture.valuedRequest("valued_" + candidate.name());
            UUID taskId = UUID.randomUUID();
            var first = request(RecoverySelector.TimeType.DATE, "2026-09-03");
            var second = request(RecoverySelector.TimeType.DATE, "2026-09-07");
            fixture.find(taskId, task(taskId, fixture.key,
                    Map.of("start_date", "20260901", "end_date", "20260910"), first, second));
            var updateConfirmed = new AtomicBoolean();
            var calls = new AtomicInteger();
            fixture.fetch = batch -> {
                if (calls.getAndIncrement() == 0) return fixture.result(batch, candidate.rows());
                assertThat(updateConfirmed).as(candidate.name()).isTrue();
                return fixture.result(batch, List.of(List.of("later", "3.00")));
            };
            fixture.update = (item, code) -> {
                assertThat(item).as(candidate.name()).isEqualTo(new ItemKey(taskId, first));
                assertThat(code).as(candidate.name()).isEqualTo(candidate.code());
                updateConfirmed.set(true);
                return new SavedFailure(item);
            };

            var result = fixture.service().execute(taskId, RequestId.newId());

            assertThat(fixture.committed).as(candidate.name())
                    .containsExactly(new ItemKey(taskId, second));
            assertThat(fixture.updatedReasons).as(candidate.name()).containsExactly(
                    Map.entry(new ItemKey(taskId, first), candidate.code()));
            assertThat(result.completedUnits()).as(candidate.name()).isEqualTo(1);
            assertThat(result.failedUnits()).as(candidate.name()).isEqualTo(1);
            assertThat(result.sourceRowCount()).as(candidate.name()).isEqualTo(1);
            assertThat(result.insertedRows()).as(candidate.name()).isEqualTo(1);
            assertThat(result.remainingFailedUnits()).as(candidate.name()).isEqualTo(1);
        }
    }

    @Test
    void sameRoundIdenticalBusinessKeyKeepsFullSourceRowsButWritesOnlyOnce() {
        var fixture = Fixture.valuedRequest("same_round");
        UUID taskId = UUID.randomUUID();
        var first = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        var second = request(RecoverySelector.TimeType.DATE, "2026-09-07");
        fixture.find(taskId, task(taskId, fixture.key,
                Map.of("start_date", "20260901", "end_date", "20260910"), first, second));
        fixture.fetch = batch -> fixture.result(batch, List.of(List.of("same-key", "1.00")));
        var committedRows = new ArrayList<Integer>();
        fixture.commit = (item, ready) -> {
            committedRows.add(ready.batch().rows().size());
            return new BatchCommitService.Committed(item.selector(), ready.sourceRowCount(),
                    new WriteCounts(ready.batch().rows().size(), 0), false);
        };

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(committedRows).containsExactly(1, 0);
        assertThat(fixture.committed).containsExactly(
                new ItemKey(taskId, first), new ItemKey(taskId, second));
        assertThat(result.completedUnits()).isEqualTo(2);
        assertThat(result.sourceRowCount()).isEqualTo(2);
        assertThat(result.insertedRows()).isEqualTo(1);
        assertThat(result.updatedRows()).isZero();
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isZero();
    }

    @Test
    void aNewManualRoundUsesAFreshIndexAndAllowsChangedContentToUpsert() {
        var fixture = Fixture.valuedRequest("two_rounds");
        UUID taskId = UUID.randomUUID();
        var first = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        var second = request(RecoverySelector.TimeType.DATE, "2026-09-07");
        var params = Map.<String, Object>of(
                "start_date", "20260901", "end_date", "20260910");
        fixture.find(taskId,
                task(taskId, fixture.key, params, first, second),
                task(taskId, fixture.key, params, second));
        var fetches = new AtomicInteger();
        fixture.fetch = batch -> switch (fetches.getAndIncrement()) {
            case 0 -> fixture.result(batch, List.of(List.of("same-key", "1.00")));
            case 1 -> throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "saved for next round");
            case 2 -> fixture.result(batch, List.of(List.of("same-key", "2.00")));
            default -> throw new AssertionError("unexpected fetch");
        };
        var commits = new AtomicInteger();
        fixture.commit = (item, ready) -> {
            assertThat(ready.batch().rows()).hasSize(1);
            return new BatchCommitService.Committed(item.selector(), ready.sourceRowCount(),
                    commits.getAndIncrement() == 0 ? new WriteCounts(1, 0) : new WriteCounts(0, 1), false);
        };
        var service = fixture.service();

        var firstResult = service.execute(taskId, RequestId.newId());
        var secondResult = service.execute(taskId, RequestId.newId());

        assertThat(firstResult.completedUnits()).isEqualTo(1);
        assertThat(firstResult.failedUnits()).isEqualTo(1);
        assertThat(firstResult.sourceRowCount()).isEqualTo(1);
        assertThat(firstResult.insertedRows()).isEqualTo(1);
        assertThat(firstResult.updatedRows()).isZero();
        assertThat(firstResult.taskId()).isEqualTo(taskId);
        assertThat(firstResult.remainingFailedUnits()).isEqualTo(1);
        assertThat(secondResult.completedUnits()).isEqualTo(1);
        assertThat(secondResult.failedUnits()).isZero();
        assertThat(secondResult.sourceRowCount()).isEqualTo(1);
        assertThat(secondResult.insertedRows()).isZero();
        assertThat(secondResult.updatedRows()).isEqualTo(1);
        assertThat(secondResult.taskId()).isNull();
        assertThat(secondResult.remainingFailedUnits()).isZero();
        assertThat(fixture.updatedReasons).containsExactly(
                Map.entry(new ItemKey(taskId, second), ErrorCode.SOURCE_TIMEOUT));
        assertThat(fixture.committed).containsExactly(
                new ItemKey(taskId, first), new ItemKey(taskId, second));
    }

    @Test
    void lateSessionAndPlanningFailuresLeaveAllOriginalItemsUntouched() {
        var session = twoDateFixture();
        var realValidator = new ParameterValidator();
        var validator = mock(ParameterValidator.class);
        var validations = new AtomicInteger();
        when(validator.validate(
                org.mockito.ArgumentMatchers.<List<ParameterDescriptor>>any(),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenAnswer(call -> {
                    if (validations.incrementAndGet() == 8) {
                        throw new IllegalArgumentException("last session rejected");
                    }
                    List<ParameterDescriptor> parameters = call.getArgument(0);
                    Map<String, Object> values = call.getArgument(1);
                    return realValidator.validate(parameters, values);
                });
        assertCode(() -> session.service(validator).execute(session.taskId, RequestId.newId()),
                ErrorCode.RETRY_TASK_INVALID);
        assertThat(validations).hasValue(8);
        assertNoExecutionEffects(session);
        assertThat(session.planned).isEmpty();

        var planningException = twoDateFixture();
        var unexpected = new IllegalStateException("last plan failed");
        planningException.planning = batch -> {
            if (batch.sourceParams().get("start_date").equals("20260907")) throw unexpected;
            return DownloadPolicy.BatchPlanning.SOURCE_RANGE;
        };
        assertThatThrownBy(() -> planningException.service().execute(
                planningException.taskId, RequestId.newId())).isSameAs(unexpected);
        assertThat(planningException.planned).hasSize(2);
        assertNoExecutionEffects(planningException);

        for (DownloadPolicy.BatchPlanning advice : java.util.Arrays.asList(
                null, DownloadPolicy.BatchPlanning.UNCONFIRMED)) {
            var completeness = twoDateFixture();
            completeness.planning = batch -> batch.sourceParams().get("start_date").equals("20260907")
                    ? advice : DownloadPolicy.BatchPlanning.SOURCE_RANGE;
            assertCode(() -> completeness.service().execute(completeness.taskId, RequestId.newId()),
                    ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
            assertThat(completeness.planned).hasSize(2);
            assertNoExecutionEffects(completeness);
        }

        var incompatibleType = twoDateFixture();
        DatasetAdapter adapter = mock(DatasetAdapter.class);
        when(adapter.datasetKey()).thenReturn(incompatibleType.key);
        assertCode(() -> incompatibleType.service(
                new PluginRegistry(List.of(incompatibleType.plugin)),
                new AdapterRegistry(List.of(adapter))).execute(
                        incompatibleType.taskId, RequestId.newId()),
                ErrorCode.DATASET_MISCONFIGURED);
        assertNoExecutionEffects(incompatibleType);
        assertThat(incompatibleType.planned).isEmpty();
    }

    @Test
    void dateAndNativePlanningAcceptOnlyTheirExactBoundaries() {
        var dateAccepted = Fixture.dateRequest("daily_date");
        UUID dateTask = UUID.randomUUID();
        var day = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        var params = Map.<String, Object>of(
                "start_date", "20260901", "end_date", "20260910");
        dateAccepted.find(dateTask, task(dateTask, dateAccepted.key, params, day));
        dateAccepted.fetch = batch -> dateAccepted.result(batch, List.of(List.of("date")));
        assertThat(dateAccepted.service().execute(dateTask, RequestId.newId()).completedUnits())
                .isEqualTo(1);
        assertThat(dateAccepted.planned).containsExactly(Map.of("ann_date", "20260903"));
        assertThat(dateAccepted.requests).containsExactly(Map.of("ann_date", "20260903"));

        var dateRejected = Fixture.dateRequest("daily_date_rejected");
        UUID rejectedDateTask = UUID.randomUUID();
        dateRejected.find(rejectedDateTask, task(rejectedDateTask, dateRejected.key, params, day));
        dateRejected.planning = batch -> DownloadPolicy.BatchPlanning.SOURCE_RANGE;
        assertCode(() -> dateRejected.service().execute(rejectedDateTask, RequestId.newId()),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertNoExecutionEffects(dateRejected);

        var nativeAccepted = Fixture.request("native_accepted",
                DownloadPolicy.Mode.NATIVE_RANGE, null);
        UUID nativeTask = UUID.randomUUID();
        nativeAccepted.find(nativeTask, task(nativeTask, nativeAccepted.key, params, day));
        nativeAccepted.fetch = batch -> nativeAccepted.result(batch, List.of(List.of("native")));
        assertThat(nativeAccepted.service().execute(nativeTask, RequestId.newId()).completedUnits())
                .isEqualTo(1);
        assertThat(nativeAccepted.requests).containsExactly(Map.of(
                "start_date", "20260903", "end_date", "20260903"));

        var nativeRejected = Fixture.request("native_rejected",
                DownloadPolicy.Mode.NATIVE_RANGE, null);
        UUID rejectedNativeTask = UUID.randomUUID();
        nativeRejected.find(rejectedNativeTask, task(
                rejectedNativeTask, nativeRejected.key, params, day));
        nativeRejected.planning = batch -> DownloadPolicy.BatchPlanning.SINGLE_DATE;
        assertCode(() -> nativeRejected.service().execute(rejectedNativeTask, RequestId.newId()),
                ErrorCode.SOURCE_REQUEST_UNCONFIRMED);
        assertNoExecutionEffects(nativeRejected);

        var rangeDate = Fixture.request("range_single_date",
                DownloadPolicy.Mode.ANN_DATE_RANGE, null);
        UUID rangeTask = UUID.randomUUID();
        rangeDate.find(rangeTask, task(rangeTask, rangeDate.key, params, day));
        rangeDate.planning = batch -> DownloadPolicy.BatchPlanning.SINGLE_DATE;
        rangeDate.fetch = batch -> rangeDate.result(batch, List.of(List.of("range-date")));
        assertThat(rangeDate.service().execute(rangeTask, RequestId.newId()).completedUnits())
                .isEqualTo(1);
    }

    @Test
    void rolledBackCommitUpdatesReasonBeforeContinuingAndUnavailableStopsWithFutureCount() {
        var fixture = twoDateFixture();
        UUID taskId = fixture.taskId;
        fixture.commit = (item, ready) -> item.selector().timeValue().endsWith("03")
                ? new BatchCommitService.RolledBack(item.selector(), false)
                : new BatchCommitService.Committed(item.selector(), ready.sourceRowCount(),
                        new WriteCounts(ready.batch().rows().size(), 0), false);
        var continued = fixture.service().execute(taskId, RequestId.newId());
        assertThat(fixture.updatedReasons).containsExactly(Map.entry(
                new ItemKey(taskId, request(RecoverySelector.TimeType.DATE, "2026-09-03")),
                ErrorCode.PERSISTENCE_FAILED));
        assertThat(continued.completedUnits()).isEqualTo(1);
        assertThat(continued.failedUnits()).isEqualTo(1);

        for (boolean unavailableResult : List.of(false, true)) {
            var stopped = twoDateFixture();
            stopped.commit = (item, ready) -> unavailableResult
                    ? new BatchCommitService.Unavailable(item.selector())
                    : new BatchCommitService.RolledBack(item.selector(), true);
            var error = stopped(stopped.service(), stopped.taskId, ErrorCode.PERSISTENCE_FAILED);
            assertThat(error.downloadResult().failedUnits()).isEqualTo(1);
            assertThat(error.downloadResult().notStartedUnits()).isEqualTo(1);
            assertThat(error.downloadResult().notStartedScopes()).containsExactly(
                    request(RecoverySelector.TimeType.DATE, "2026-09-07"));
            assertThat(error.downloadResult().remainingFailedUnits()).isEqualTo(2);
            assertThat(error.downloadResult().taskId()).isEqualTo(stopped.taskId);
            assertThat(stopped.updatedReasons).isEmpty();
        }
    }

    @Test
    void commitUnknownUsesSiblingKnowledgeAndLastUnknownDoesNotExposeAFalseTaskEntry() {
        var siblings = twoDateFixture();
        siblings.commit = (item, ready) -> new BatchCommitService.Unconfirmed(item.selector());
        var first = stopped(siblings.service(), siblings.taskId, ErrorCode.COMMIT_UNCONFIRMED)
                .downloadResult();
        assertThat(first.completedUnits()).isZero();
        assertThat(first.failedUnits()).isZero();
        assertThat(first.notStartedUnits()).isEqualTo(1);
        assertThat(first.unconfirmedScopes()).containsExactly(
                request(RecoverySelector.TimeType.DATE, "2026-09-03"));
        assertThat(first.taskId()).isEqualTo(siblings.taskId);
        assertThat(first.remainingFailedUnits()).isNull();

        var last = Fixture.request("income", DownloadPolicy.Mode.ANN_DATE_RANGE, null);
        UUID lastId = UUID.randomUUID();
        var only = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        last.find(lastId, task(lastId, last.key,
                Map.of("start_date", "20260901", "end_date", "20260910"), only));
        last.fetch = batch -> last.result(batch, List.of(List.of("only")));
        last.commit = (item, ready) -> new BatchCommitService.Unconfirmed(item.selector());
        var unknownLast = stopped(last.service(), lastId, ErrorCode.COMMIT_UNCONFIRMED)
                .downloadResult();
        assertThat(unknownLast.taskId()).isNull();
        assertThat(unknownLast.remainingFailedUnits()).isNull();
        assertThat(unknownLast.notStartedUnits()).isZero();
    }

    @Test
    void confirmedCommitThenFrameworkStopKeepsCountsAndConfirmedDeletion() {
        var fixture = twoDateFixture();
        fixture.commit = (item, ready) -> new BatchCommitService.Committed(item.selector(),
                ready.sourceRowCount(), new WriteCounts(1, 0), true);
        var result = stopped(fixture.service(), fixture.taskId, ErrorCode.INTERNAL_ERROR)
                .downloadResult();
        assertThat(result.completedUnits()).isEqualTo(1);
        assertThat(result.sourceRowCount()).isEqualTo(1);
        assertThat(result.insertedRows()).isEqualTo(1);
        assertThat(result.failedUnits()).isZero();
        assertThat(result.notStartedUnits()).isEqualTo(1);
        assertThat(result.taskId()).isEqualTo(fixture.taskId);
        assertThat(result.remainingFailedUnits()).isEqualTo(1);
        assertThat(result.unconfirmedScopes()).isEmpty();

        var closedFixture = Fixture.request("margin", DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
        UUID closedTask = UUID.randomUUID();
        var day4 = request(RecoverySelector.TimeType.DATE, "2026-09-04");
        var day5 = request(RecoverySelector.TimeType.DATE, "2026-09-05");
        closedFixture.find(closedTask, task(closedTask, closedFixture.key, Map.of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910"),
                day4, day5));
        closedFixture.calendar = scope -> closed(scope, "SSE");
        closedFixture.close = item -> new BatchCommitService.Committed(item.selector(), 0,
                new WriteCounts(0, 0), true);
        var closedStop = stopped(closedFixture.service(), closedTask, ErrorCode.INTERNAL_ERROR)
                .downloadResult();
        assertThat(closedStop.completedUnits()).isZero();
        assertThat(closedStop.skippedClosedDates()).isEqualTo(2);
        assertThat(closedStop.notStartedUnits()).isZero();
        assertThat(closedStop.notStartedScopes()).containsExactly(day5);
        assertThat(closedStop.taskId()).isEqualTo(closedTask);
        assertThat(closedStop.remainingFailedUnits()).isEqualTo(1);
    }

    @Test
    void businessAndClosedT11BranchesKeepPriorFactsAndClassifyBothFutureKinds() {
        var branches = List.of("committed", "committed-stop", "rolled-back",
                "rolled-back-unavailable", "unavailable", "unconfirmed");
        for (boolean closedTarget : List.of(false, true)) {
            for (String branch : branches) {
                var fixture = Fixture.request("t11_" + branch.replace('-', '_')
                        + (closedTarget ? "_closed" : "_business"),
                        DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
                UUID taskId = UUID.randomUUID();
                var day1 = request(RecoverySelector.TimeType.DATE, "2026-09-01");
                var day2 = request(RecoverySelector.TimeType.DATE, "2026-09-02");
                var day3 = request(RecoverySelector.TimeType.DATE, "2026-09-03");
                var day4 = request(RecoverySelector.TimeType.DATE, "2026-09-04");
                var day5 = request(RecoverySelector.TimeType.DATE, "2026-09-05");
                fixture.find(taskId, task(taskId, fixture.key, Map.of(
                        "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260905"),
                        day1, day2, day3, day4, day5));
                fixture.calendar = scope -> {
                    var dates = new HashMap<java.time.LocalDate, Boolean>();
                    scope.dates().forEach(date -> dates.put(date,
                            date.getDayOfMonth() != 5
                                    && (!closedTarget || date.getDayOfMonth() != 3)));
                    return new CalendarDecision(scope, Map.of("SSE", dates));
                };
                fixture.fetch = batch -> {
                    String start = (String) batch.sourceParams().get("start_date");
                    if (start.equals("20260902")) {
                        throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "prior-secret");
                    }
                    return fixture.result(batch, start.equals("20260901")
                            ? List.of(List.of("prior-a"), List.of("prior-b"))
                            : List.of(List.of("row-" + start)));
                };
                fixture.commit = (item, ready) -> {
                    String date = item.selector().timeValue();
                    if (date.endsWith("01")) {
                        return new BatchCommitService.Committed(item.selector(),
                                ready.sourceRowCount(), new WriteCounts(1, 1), false);
                    }
                    if (date.endsWith("03")) {
                        assertThat(closedTarget).as(branch).isFalse();
                        return branchResult(branch, item.selector(), ready.sourceRowCount(),
                                new WriteCounts(1, 0));
                    }
                    return new BatchCommitService.Committed(item.selector(),
                            ready.sourceRowCount(), new WriteCounts(1, 0), false);
                };
                fixture.close = item -> {
                    if (item.selector().timeValue().endsWith("03")) {
                        assertThat(closedTarget).as(branch).isTrue();
                        return branchResult(branch, item.selector(), 0, new WriteCounts(0, 0));
                    }
                    return new BatchCommitService.Committed(item.selector(), 0,
                            new WriteCounts(0, 0), false);
                };

                DownloadExecutionResult result;
                if (branch.equals("committed") || branch.equals("rolled-back")) {
                    result = fixture.service().execute(taskId, RequestId.newId());
                } else {
                    ErrorCode code = switch (branch) {
                        case "committed-stop" -> ErrorCode.INTERNAL_ERROR;
                        case "unconfirmed" -> ErrorCode.COMMIT_UNCONFIRMED;
                        default -> ErrorCode.PERSISTENCE_FAILED;
                    };
                    result = stopped(fixture.service(), taskId, code).downloadResult();
                }

                String label = branch + (closedTarget ? " closed" : " business");
                assertThat(result.skippedClosedDates()).as(label)
                        .isEqualTo(closedTarget ? 2 : 1);
                assertThat(result.failures()).as(label).anySatisfy(failure -> {
                    assertThat(failure.selector()).isEqualTo(day2);
                    assertThat(failure.errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
                });
                assertThat(fixture.committed).as(label)
                        .contains(new ItemKey(taskId, day1));
                if (branch.equals("committed") || branch.equals("rolled-back")) {
                    assertThat(result.outcome()).as(label)
                            .isEqualTo(DownloadExecutionResult.Outcome.PARTIAL);
                    assertThat(result.completedUnits()).as(label).isEqualTo(
                            branch.equals("committed") && !closedTarget ? 3 : 2);
                    assertThat(result.failedUnits()).as(label).isEqualTo(
                            branch.equals("committed") ? 1 : 2);
                    assertThat(result.sourceRowCount()).as(label).isEqualTo(
                            branch.equals("committed") && !closedTarget ? 4 : 3);
                    assertThat(result.insertedRows()).as(label).isEqualTo(
                            branch.equals("committed") && !closedTarget ? 3 : 2);
                    assertThat(result.updatedRows()).as(label).isEqualTo(1);
                    assertThat(result.notStartedUnits()).as(label).isZero();
                    assertThat(result.notStartedScopes()).as(label).isEmpty();
                    assertThat(result.taskId()).as(label).isEqualTo(taskId);
                    assertThat(result.remainingFailedUnits()).as(label).isEqualTo(
                            branch.equals("committed") ? 1 : 2);
                    assertThat(fixture.updatedReasons).as(label).containsExactlyElementsOf(
                            branch.equals("committed")
                                    ? List.of(Map.entry(new ItemKey(taskId, day2),
                                            ErrorCode.SOURCE_TIMEOUT))
                                    : List.of(
                                            Map.entry(new ItemKey(taskId, day2),
                                                    ErrorCode.SOURCE_TIMEOUT),
                                            Map.entry(new ItemKey(taskId, day3),
                                                    ErrorCode.PERSISTENCE_FAILED)));
                    assertThat(fixture.requests).as(label).hasSize(closedTarget ? 3 : 4);
                    assertThat(fixture.closed).as(label).containsExactlyElementsOf(closedTarget
                            ? List.of(new ItemKey(taskId, day3), new ItemKey(taskId, day5))
                            : List.of(new ItemKey(taskId, day5)));
                    continue;
                }

                assertThat(result.outcome()).as(label)
                        .isEqualTo(DownloadExecutionResult.Outcome.UNCONFIRMED);
                assertThat(result.notStartedUnits()).as(label).isEqualTo(1);
                assertThat(result.notStartedScopes()).as(label).containsExactly(day4, day5);
                assertThat(fixture.requests).as(label).hasSize(closedTarget ? 2 : 3);
                assertThat(fixture.closed).as(label).containsExactlyElementsOf(closedTarget
                        ? List.of(new ItemKey(taskId, day3)) : List.of());
                assertThat(result.taskId()).as(label).isEqualTo(taskId);
                if (branch.equals("committed-stop")) {
                    assertThat(result.completedUnits()).as(label).isEqualTo(closedTarget ? 1 : 2);
                    assertThat(result.failedUnits()).as(label).isEqualTo(1);
                    assertThat(result.sourceRowCount()).as(label).isEqualTo(closedTarget ? 2 : 3);
                    assertThat(result.insertedRows()).as(label).isEqualTo(closedTarget ? 1 : 2);
                    assertThat(result.updatedRows()).as(label).isEqualTo(1);
                    assertThat(result.remainingFailedUnits()).as(label).isEqualTo(3);
                    assertThat(result.unconfirmedScopes()).as(label).isEmpty();
                } else if (branch.equals("unconfirmed")) {
                    assertThat(result.completedUnits()).as(label).isEqualTo(1);
                    assertThat(result.failedUnits()).as(label).isEqualTo(1);
                    assertThat(result.sourceRowCount()).as(label).isEqualTo(2);
                    assertThat(result.insertedRows()).as(label).isEqualTo(1);
                    assertThat(result.updatedRows()).as(label).isEqualTo(1);
                    assertThat(result.remainingFailedUnits()).as(label).isNull();
                    assertThat(result.unconfirmedScopes()).as(label).containsExactly(day3);
                } else {
                    assertThat(result.completedUnits()).as(label).isEqualTo(1);
                    assertThat(result.failedUnits()).as(label).isEqualTo(2);
                    assertThat(result.sourceRowCount()).as(label).isEqualTo(2);
                    assertThat(result.insertedRows()).as(label).isEqualTo(1);
                    assertThat(result.updatedRows()).as(label).isEqualTo(1);
                    assertThat(result.remainingFailedUnits()).as(label).isEqualTo(4);
                    assertThat(result.unconfirmedScopes()).as(label).isEmpty();
                    assertThat(result.failures()).as(label).anySatisfy(failure -> {
                        assertThat(failure.selector()).isEqualTo(day3);
                        assertThat(failure.errorCode()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
                    });
                }
                assertThat(fixture.updatedReasons).as(label).containsExactly(
                        Map.entry(new ItemKey(taskId, day2), ErrorCode.SOURCE_TIMEOUT));
            }
        }
    }

    @Test
    void lastClosedItemDistinguishesConfirmedFailureAndUnknownOwnership() {
        for (String branch : List.of("committed", "committed-stop", "rolled-back",
                "rolled-back-unavailable", "unavailable", "unconfirmed")) {
            var fixture = Fixture.request("last_closed_" + branch.replace('-', '_'),
                    DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id");
            UUID taskId = UUID.randomUUID();
            var day = request(RecoverySelector.TimeType.DATE, "2026-09-05");
            fixture.find(taskId, task(taskId, fixture.key, Map.of(
                    "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260905"), day));
            fixture.calendar = scope -> closed(scope, "SSE");
            fixture.close = item -> branch.equals("committed")
                    ? new BatchCommitService.Committed(item.selector(), 0,
                            new WriteCounts(0, 0), false)
                    : branchResult(branch, item.selector(), 0, new WriteCounts(0, 0));

            DownloadExecutionResult result;
            if (branch.equals("committed") || branch.equals("rolled-back")) {
                result = fixture.service().execute(taskId, RequestId.newId());
            } else {
                ErrorCode code = switch (branch) {
                    case "committed-stop" -> ErrorCode.INTERNAL_ERROR;
                    case "unconfirmed" -> ErrorCode.COMMIT_UNCONFIRMED;
                    default -> ErrorCode.PERSISTENCE_FAILED;
                };
                result = stopped(fixture.service(), taskId, code).downloadResult();
            }

            assertThat(result.skippedClosedDates()).as(branch).isEqualTo(1);
            assertThat(result.completedUnits()).as(branch).isZero();
            assertThat(result.sourceRowCount()).as(branch).isZero();
            assertThat(result.insertedRows()).as(branch).isZero();
            assertThat(result.updatedRows()).as(branch).isZero();
            assertThat(result.notStartedUnits()).as(branch).isZero();
            assertThat(result.notStartedScopes()).as(branch).isEmpty();
            assertThat(fixture.planned).as(branch).isEmpty();
            assertThat(fixture.requests).as(branch).isEmpty();
            assertThat(fixture.closed).as(branch).containsExactly(new ItemKey(taskId, day));
            switch (branch) {
                case "committed" -> {
                    assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.NO_OPEN_DATES);
                    assertThat(result.failedUnits()).isZero();
                    assertThat(result.taskId()).isNull();
                    assertThat(result.remainingFailedUnits()).isZero();
                }
                case "committed-stop" -> {
                    assertThat(result.failedUnits()).isZero();
                    assertThat(result.taskId()).isNull();
                    assertThat(result.remainingFailedUnits()).isZero();
                }
                case "rolled-back" -> {
                    assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.FAILED);
                    assertThat(result.failedUnits()).isEqualTo(1);
                    assertThat(result.taskId()).isEqualTo(taskId);
                    assertThat(result.remainingFailedUnits()).isEqualTo(1);
                    assertThat(fixture.updatedReasons).containsExactly(
                            Map.entry(new ItemKey(taskId, day), ErrorCode.PERSISTENCE_FAILED));
                }
                case "rolled-back-unavailable", "unavailable" -> {
                    assertThat(result.failedUnits()).isEqualTo(1);
                    assertThat(result.taskId()).isEqualTo(taskId);
                    assertThat(result.remainingFailedUnits()).isEqualTo(1);
                    assertThat(fixture.updatedReasons).isEmpty();
                }
                case "unconfirmed" -> {
                    assertThat(result.failedUnits()).isZero();
                    assertThat(result.taskId()).isNull();
                    assertThat(result.remainingFailedUnits()).isNull();
                    assertThat(result.unconfirmedScopes()).containsExactly(day);
                    assertThat(fixture.updatedReasons).isEmpty();
                }
                default -> throw new AssertionError(branch);
            }
        }
    }

    @Test
    void reasonSaveUnknownKeepsFailureAndTaskButMakesRemainingUnknown() {
        var fixture = twoDateFixture();
        fixture.fetch = batch -> { throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "secret"); };
        fixture.update = (item, code) -> { throw saveUnknown(); };
        var error = stopped(fixture.service(), fixture.taskId,
                ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED).downloadResult();
        assertThat(error.failedUnits()).isEqualTo(1);
        assertThat(error.failures()).extracting(RecoveryUnitProcessor.Failure::errorCode)
                .containsExactly(ErrorCode.SOURCE_TIMEOUT);
        assertThat(error.taskId()).isEqualTo(fixture.taskId);
        assertThat(error.remainingFailedUnits()).isNull();
        assertThat(error.notStartedUnits()).isEqualTo(1);
        assertThat(error.unconfirmedScopes()).isEmpty();
    }

    @Test
    void slotIsHeldWhileReasonSaveIsBlockedAndWaiterTimeoutDoesNotCancelTheOwner() throws Exception {
        var fixture = twoDateFixture();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        fixture.fetch = batch -> {
            if (attempts.getAndIncrement() == 0) {
                throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "secret");
            }
            return fixture.result(batch, List.of(List.of("second")));
        };
        fixture.update = (item, code) -> {
            entered.countDown();
            await(release);
            return new SavedFailure(item);
        };
        try (var executor = Executors.newSingleThreadExecutor()) {
            var execution = executor.submit(() -> fixture.service().execute(
                    fixture.taskId, RequestId.newId()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.slot.retrying(fixture.taskId)).isTrue();
            assertThatThrownBy(() -> execution.get(50, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertCode(() -> fixture.service().execute(fixture.taskId, RequestId.newId()),
                    ErrorCode.DOWNLOAD_BUSY);
            assertThat(fixture.requests).hasSize(1);
            release.countDown();
            assertThat(execution.get(5, TimeUnit.SECONDS).notStartedUnits()).isZero();
            assertThat(fixture.requests).hasSize(2);
            assertThat(fixture.slot.busy()).isFalse();
        }
    }

    @Test
    void unexpectedFetchFailureReleasesTheSharedSlotWithoutChangingTheTask() {
        var fixture = twoDateFixture();
        fixture.fetch = batch -> { throw new IllegalStateException("unexpected"); };
        assertThatThrownBy(() -> fixture.service().execute(fixture.taskId, RequestId.newId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("unexpected");
        assertThat(fixture.slot.busy()).isFalse();
        assertThat(fixture.updatedReasons).isEmpty();
        assertThat(fixture.committed).isEmpty();
    }

    @Test
    void sharedSlotStaysOwnedWhileFindFetchCommitAndClosedCleanupAreActuallyBlocked()
            throws Exception {
        for (String phase : List.of("find", "fetch", "commitRetry", "commitClosedRetry")) {
            boolean closedPhase = phase.equals("commitClosedRetry");
            var fixture = closedPhase
                    ? Fixture.request("margin", DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id")
                    : Fixture.request("income", DownloadPolicy.Mode.ANN_DATE_RANGE, null);
            UUID taskId = UUID.randomUUID();
            var selector = request(RecoverySelector.TimeType.DATE, closedPhase
                    ? "2026-09-04" : "2026-09-03");
            Map<String, Object> params = closedPhase
                    ? Map.of("exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910")
                    : Map.of("start_date", "20260901", "end_date", "20260910");
            Task snapshot = task(taskId, fixture.key, params, selector);
            fixture.find(taskId, snapshot);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            if (phase.equals("find")) {
                when(fixture.failures.find(taskId)).thenAnswer(call -> {
                    entered.countDown();
                    await(release);
                    return Optional.of(snapshot);
                });
            } else if (phase.equals("fetch")) {
                fixture.fetch = batch -> {
                    entered.countDown();
                    await(release);
                    return fixture.result(batch, List.of(List.of("row")));
                };
            } else if (phase.equals("commitRetry")) {
                fixture.fetch = batch -> fixture.result(batch, List.of(List.of("row")));
                fixture.commit = (item, ready) -> {
                    entered.countDown();
                    await(release);
                    return new BatchCommitService.Committed(item.selector(), ready.sourceRowCount(),
                            new WriteCounts(1, 0), false);
                };
            } else {
                fixture.calendar = scope -> closed(scope, "SSE");
                fixture.close = item -> {
                    entered.countDown();
                    await(release);
                    return new BatchCommitService.Committed(item.selector(), 0,
                            new WriteCounts(0, 0), false);
                };
            }
            try (var executor = Executors.newSingleThreadExecutor()) {
                var execution = executor.submit(() -> fixture.service().execute(taskId, RequestId.newId()));
                assertThat(entered.await(5, TimeUnit.SECONDS)).as(phase).isTrue();
                assertThat(fixture.slot.retrying(taskId)).as(phase).isTrue();
                assertCode(() -> fixture.slot.acquire(null), ErrorCode.DOWNLOAD_BUSY);
                assertThatThrownBy(() -> execution.get(30, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                release.countDown();
                assertThat(execution.get(5, TimeUnit.SECONDS).remainingFailedUnits()).isZero();
                assertThat(fixture.slot.busy()).as(phase).isFalse();
            }
        }
    }

    @Test
    void requestSelectorRemainsOneUnitEvenWhenTheCurrentPolicySupportsStockRecovery() {
        var fixture = Fixture.tradeStock("margin_detail");
        UUID taskId = UUID.randomUUID();
        var request = request(RecoverySelector.TimeType.DATE, "2026-09-03");
        var params = Map.<String, Object>of(
                "exchange_id", "SSE", "start_date", "20260901", "end_date", "20260910");
        fixture.find(taskId, task(taskId, fixture.key, params, request));
        fixture.fetch = batch -> fixture.result(batch, List.of(
                List.of("a", "000001.SZ", "20260903"),
                List.of("b", "600000.SH", "20260903")));

        var result = fixture.service().execute(taskId, RequestId.newId());

        assertThat(fixture.committed).containsExactly(new ItemKey(taskId, request));
        assertThat(result.completedUnits()).isEqualTo(1);
        assertThat(result.sourceRowCount()).isEqualTo(2);
        assertThat(result.failedUnits()).isZero();
    }

    @Test
    void exactSavedAndCommitKeysAreRequiredBeforeContinuation() {
        var save = twoDateFixture();
        var wrong = new ItemKey(save.taskId,
                request(RecoverySelector.TimeType.DATE, "2026-09-07"));
        save.fetch = batch -> { throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "secret"); };
        save.update = (item, code) -> new SavedFailure(wrong);
        assertThatThrownBy(() -> save.service().execute(save.taskId, RequestId.newId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Saved failure key mismatch");
        assertThat(save.requests).hasSize(1);
        assertThat(save.slot.busy()).isFalse();

        var commit = twoDateFixture();
        commit.commit = (item, ready) -> new BatchCommitService.Committed(wrong.selector(),
                ready.sourceRowCount(), new WriteCounts(1, 0), false);
        assertThatThrownBy(() -> commit.service().execute(commit.taskId, RequestId.newId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Commit selector mismatch");
        assertThat(commit.requests).hasSize(1);
        assertThat(commit.slot.busy()).isFalse();
    }

    @Test
    void confirmedSuccessAndSavedFailureRemainInEveryLaterStopSnapshot() {
        for (String branch : List.of("committed-stop", "rolled-back-unavailable",
                "unavailable", "commit-unknown", "reason-save-unknown")) {
            var fixture = fourDateFixture();
            var commitCalls = new AtomicInteger();
            fixture.commit = (item, ready) -> {
                int call = commitCalls.getAndIncrement();
                if (call == 0) {
                    return new BatchCommitService.Committed(item.selector(), ready.sourceRowCount(),
                            new WriteCounts(1, 1), false);
                }
                return switch (branch) {
                    case "committed-stop" -> new BatchCommitService.Committed(item.selector(),
                            ready.sourceRowCount(), new WriteCounts(1, 0), true);
                    case "rolled-back-unavailable" -> new BatchCommitService.RolledBack(
                            item.selector(), true);
                    case "unavailable" -> new BatchCommitService.Unavailable(item.selector());
                    case "commit-unknown" -> new BatchCommitService.Unconfirmed(item.selector());
                    case "reason-save-unknown" -> throw new AssertionError("third item must fail at source");
                    default -> throw new AssertionError(branch);
                };
            };
            var fetchCalls = new AtomicInteger();
            fixture.fetch = batch -> {
                int call = fetchCalls.getAndIncrement();
                if (call == 1 || branch.equals("reason-save-unknown") && call == 2) {
                    throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "secret");
                }
                return fixture.result(batch, call == 0
                        ? List.of(List.of("one-a"), List.of("one-b"))
                        : List.of(List.of("three")));
            };
            var updates = new AtomicInteger();
            fixture.update = (item, code) -> {
                if (branch.equals("reason-save-unknown") && updates.getAndIncrement() == 1) {
                    throw saveUnknown();
                }
                return new SavedFailure(item);
            };
            ErrorCode code = switch (branch) {
                case "committed-stop" -> ErrorCode.INTERNAL_ERROR;
                case "commit-unknown" -> ErrorCode.COMMIT_UNCONFIRMED;
                case "reason-save-unknown" -> ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED;
                default -> ErrorCode.PERSISTENCE_FAILED;
            };
            var result = stopped(fixture.service(), fixture.taskId, code).downloadResult();
            assertThat(result.sourceRowCount()).as(branch).isEqualTo(
                    branch.equals("committed-stop") ? 3 : 2);
            assertThat(result.insertedRows()).as(branch).isEqualTo(
                    branch.equals("committed-stop") ? 2 : 1);
            assertThat(result.updatedRows()).as(branch).isEqualTo(1);
            assertThat(result.failures()).as(branch).anySatisfy(failure -> {
                assertThat(failure.selector().timeValue()).isEqualTo("2026-09-02");
                assertThat(failure.errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
            });
            assertThat(result.notStartedScopes()).as(branch).containsExactly(
                    request(RecoverySelector.TimeType.DATE, "2026-09-04"));
            assertThat(result.notStartedUnits()).as(branch).isEqualTo(1);
            assertThat(result.taskId()).as(branch).isEqualTo(fixture.taskId);
            if (branch.equals("commit-unknown") || branch.equals("reason-save-unknown")) {
                assertThat(result.remainingFailedUnits()).as(branch).isNull();
            } else {
                assertThat(result.remainingFailedUnits()).as(branch).isEqualTo(
                        branch.equals("committed-stop") ? 2 : 3);
            }
        }
    }

    private static Fixture twoDateFixture() {
        var fixture = Fixture.request("income", DownloadPolicy.Mode.ANN_DATE_RANGE, null);
        fixture.taskId = UUID.randomUUID();
        fixture.find(fixture.taskId, task(fixture.taskId, fixture.key,
                Map.of("start_date", "20260901", "end_date", "20260910"),
                request(RecoverySelector.TimeType.DATE, "2026-09-03"),
                request(RecoverySelector.TimeType.DATE, "2026-09-07")));
        fixture.fetch = batch -> fixture.result(batch, List.of(List.of(
                "row-" + batch.sourceParams().get("start_date"))));
        return fixture;
    }

    private static Fixture fourDateFixture() {
        var fixture = Fixture.request("income", DownloadPolicy.Mode.ANN_DATE_RANGE, null);
        fixture.taskId = UUID.randomUUID();
        var selectors = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(day -> request(RecoverySelector.TimeType.DATE,
                        "2026-09-%02d".formatted(day))).toArray(RecoverySelector[]::new);
        fixture.find(fixture.taskId, task(fixture.taskId, fixture.key,
                Map.of("start_date", "20260901", "end_date", "20260910"), selectors));
        return fixture;
    }

    private static BatchCommitService.CommitResult branchResult(String branch,
            RecoverySelector selector, long sourceRows, WriteCounts counts) {
        return switch (branch) {
            case "committed" -> new BatchCommitService.Committed(
                    selector, sourceRows, counts, false);
            case "committed-stop" -> new BatchCommitService.Committed(
                    selector, sourceRows, counts, true);
            case "rolled-back" -> new BatchCommitService.RolledBack(selector, false);
            case "rolled-back-unavailable" -> new BatchCommitService.RolledBack(selector, true);
            case "unavailable" -> new BatchCommitService.Unavailable(selector);
            case "unconfirmed" -> new BatchCommitService.Unconfirmed(selector);
            default -> throw new AssertionError(branch);
        };
    }

    private static void assertNoExecutionEffects(Fixture fixture) {
        assertThat(fixture.requests).isEmpty();
        assertThat(fixture.committed).isEmpty();
        assertThat(fixture.closed).isEmpty();
        assertThat(fixture.updatedReasons).isEmpty();
    }

    private static CalendarDecision closed(CalendarScope scope, String identity) {
        var dates = new HashMap<java.time.LocalDate, Boolean>();
        scope.dates().forEach(date -> dates.put(date, false));
        return new CalendarDecision(scope, Map.of(identity, dates));
    }

    private static TensorException saveUnknown() {
        return new TensorException(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED,
                "Failure record save is unconfirmed") {};
    }

    private static DownloadExecutionException stopped(RetryDownloadService service,
            UUID taskId, ErrorCode code) {
        try {
            service.execute(taskId, RequestId.newId());
            throw new AssertionError("Expected stopped retry execution");
        } catch (DownloadExecutionException error) {
            assertThat(error.code()).isEqualTo(code);
            assertThat(error).hasNoCause();
            return error;
        }
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(TensorException.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static Task task(UUID taskId, DatasetKey key, Map<String, Object> params,
            RecoverySelector... selectors) {
        var header = new Header(taskId, key, params, NOW, NOW);
        return new Task(header, java.util.Arrays.stream(selectors)
                .map(selector -> new Item(new ItemKey(taskId, selector), ErrorCode.SOURCE_TIMEOUT,
                        "Source request timed out", NOW))
                .toList());
    }

    private static RecoverySelector request(RecoverySelector.TimeType type, String value) {
        return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", type, value);
    }

    private static RecoverySelector stock(String code, String date) {
        return new RecoverySelector(RecoverySelector.TargetType.STOCK, code,
                RecoverySelector.TimeType.DATE, date);
    }

    private static Map<String, Object> stockRequest(String code) {
        return Map.of("ts_code", code, "start_date", "20260903", "end_date", "20260903");
    }

    private static final class Fixture {
        private final ApiDescriptor api;
        private final DatasetKey key;
        private final GenericDatasetAdapter adapter;
        private final DataSourcePlugin plugin;
        private final BatchCommitService commits = mock(BatchCommitService.class);
        private final RetryTaskStorageService failures = mock(RetryTaskStorageService.class);
        private final DownloadExecutionSlot slot = new DownloadExecutionSlot();
        private final List<Map<String, Object>> requests = new ArrayList<>();
        private final List<Map<String, Object>> planned = new ArrayList<>();
        private final List<CalendarScope> calendarScopes = new ArrayList<>();
        private final List<ItemKey> committed = new ArrayList<>();
        private final List<ItemKey> closed = new ArrayList<>();
        private final List<Map.Entry<ItemKey, ErrorCode>> updatedReasons = new ArrayList<>();
        private final AtomicInteger findCalls = new AtomicInteger();
        private UUID taskId;
        private Function<FetchBatch, FetchResult> fetch;
        private Function<FetchBatch, DownloadPolicy.BatchPlanning> planning;
        private Function<CalendarScope, CalendarDecision> calendar;
        private BiFunction<ItemKey, RecoveryUnitProcessor.ReadyUnit,
                BatchCommitService.CommitResult> commit;
        private Function<ItemKey, BatchCommitService.CommitResult> close;
        private BiFunction<ItemKey, ErrorCode, SavedFailure> update;

        private Fixture(String name, DownloadPolicy.Mode mode, String condition, boolean stock) {
            this(name, mode, condition, stock, DownloadPolicy.SourceRequestMode.RANGE);
        }

        private Fixture(String name, DownloadPolicy.Mode mode, String condition, boolean stock,
                DownloadPolicy.SourceRequestMode sourceMode) {
            this(name, mode, condition, stock, sourceMode, false);
        }

        private Fixture(String name, DownloadPolicy.Mode mode, String condition, boolean stock,
                DownloadPolicy.SourceRequestMode sourceMode, boolean valued) {
            ApiName apiName = ApiName.of(name);
            key = DatasetKey.of(PLUGIN, apiName);
            var source = sourceParameters(mode, sourceMode, condition, stock);
            var recovery = stock
                    ? new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date",
                            RecoverySelector.TimeType.DATE, true, List.of("docs/test.md"))
                    : new RecoveryPolicy(RecoveryPolicy.Mode.REQUEST, null, null, null, false,
                            List.of("docs/test.md"));
            var base = com.akkc.tensor.test.DownloadPolicies.original();
            var policy = new DownloadPolicy(mode, semantic(mode),
                    "Controlled retry", mode == DownloadPolicy.Mode.TRADE_DATE_RANGE
                            ? DownloadPolicy.CalendarProfile.C_M : null,
                    mode == DownloadPolicy.Mode.ORIGINAL_PARAMS ? null : new DownloadPolicy.Limits(31),
                    sourceMode, sourceMode == DownloadPolicy.SourceRequestMode.DATE
                            ? mode == DownloadPolicy.Mode.TRADE_DATE_RANGE ? "trade_date" : "ann_date"
                            : sourceMode == DownloadPolicy.SourceRequestMode.MONTH ? "month" : null,
                    DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                    switch (sourceMode) {
                        case DATE -> DownloadPolicy.BatchPlanning.SINGLE_DATE;
                        case RANGE -> DownloadPolicy.BatchPlanning.SOURCE_RANGE;
                        case MONTH -> DownloadPolicy.BatchPlanning.SINGLE_MONTH;
                        case NONE -> DownloadPolicy.BatchPlanning.ORIGINAL_PARAMS;
                    }, recovery, base.completenessPolicy(),
                    mode == DownloadPolicy.Mode.TRADE_DATE_RANGE
                            ? DownloadPolicy.CalendarEvidenceStatus.DOCUMENTED : null,
                    List.of("docs/test.md"));
            api = new ApiDescriptor(apiName, name, "test", QueryMode.snapshot,
                    DownloadParameterProjection.project(source, policy), policy, source);
            var columns = new ArrayList<ColumnDefinition>();
            columns.add(column("record_id", LogicalType.STRING));
            if (stock) {
                columns.add(column("ts_code", LogicalType.STRING));
                columns.add(column("ann_date", LogicalType.DATE));
            }
            if (valued) {
                columns.add(new ColumnDefinition("amount", "amount", LogicalType.DECIMAL,
                        false, 0, null, 12, 2, List.of(), false));
            }
            var definition = new DatasetDefinition(key, name, "test", QueryMode.snapshot, source,
                    TableName.from(key), columns,
                    new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("record_id")),
                    List.of(), null, 100);
            adapter = new GenericDatasetAdapter(definition, new ValueConverter(), new FingerprintKeyCodec());
            plugin = new DataSourcePlugin() {
                @Override public PluginDescriptor descriptor() {
                    return new PluginDescriptor(PLUGIN, "Retry test", "Retry test", true, true, true,
                            null, List.of(api), List.of(key));
                }
                @Override public PluginReadiness readiness() {
                    return new PluginReadiness(true, true, true, null);
                }
                @Override public FetchResult download(ApiName ignored, Map<String, Object> params,
                        DownloadContext context) {
                    throw new AssertionError("Legacy download must not be used");
                }
                @Override public CalendarDecision confirmCalendar(ApiName ignored, CalendarScope scope,
                        DownloadContext context) {
                    calendarScopes.add(scope);
                    return calendar.apply(scope);
                }
                @Override public DownloadPolicy.BatchPlanning planBatch(ApiName ignored, FetchBatch batch,
                        DownloadContext context) {
                    planned.add(batch.sourceParams());
                    return planning.apply(batch);
                }
                @Override public FetchResult fetchBatch(ApiName ignored, FetchBatch batch,
                        DownloadContext context) {
                    requests.add(batch.sourceParams());
                    return fetch.apply(batch);
                }
            };
            fetch = batch -> result(batch, List.of());
            planning = batch -> policy.batchPlanning();
            calendar = scope -> {
                var dates = new HashMap<java.time.LocalDate, Boolean>();
                scope.dates().forEach(date -> dates.put(date, true));
                return new CalendarDecision(scope, Map.of("SSE", dates));
            };
            commit = (item, ready) -> new BatchCommitService.Committed(item.selector(),
                    ready.sourceRowCount(), new WriteCounts(ready.batch().rows().size(), 0), false);
            close = item -> new BatchCommitService.Committed(item.selector(), 0,
                    new WriteCounts(0, 0), false);
            update = (item, code) -> new SavedFailure(item);
            when(commits.commitRetry(any(), any())).thenAnswer(call -> {
                ItemKey item = call.getArgument(0);
                RecoveryUnitProcessor.ReadyUnit ready = call.getArgument(1);
                committed.add(item);
                return commit.apply(item, ready);
            });
            when(commits.commitClosedRetry(any(), any(), any(), any())).thenAnswer(call -> {
                ItemKey item = call.getArgument(2);
                closed.add(item);
                return close.apply(item);
            });
            when(failures.updateReason(any(), any())).thenAnswer(call -> {
                ItemKey item = call.getArgument(0);
                ErrorCode code = call.getArgument(1);
                updatedReasons.add(Map.entry(item, code));
                return update.apply(item, code);
            });
        }

        private static Fixture request(String name, DownloadPolicy.Mode mode, String condition) {
            return new Fixture(name, mode, condition, false);
        }

        private static Fixture stock(String name) {
            return new Fixture(name, DownloadPolicy.Mode.ANN_DATE_RANGE, null, true);
        }

        private static Fixture tradeStock(String name) {
            return new Fixture(name, DownloadPolicy.Mode.TRADE_DATE_RANGE, "exchange_id", true);
        }

        private static Fixture month(String name) {
            return new Fixture(name, DownloadPolicy.Mode.MONTH_RANGE, null, false,
                    DownloadPolicy.SourceRequestMode.MONTH);
        }

        private static Fixture original(String name) {
            return new Fixture(name, DownloadPolicy.Mode.ORIGINAL_PARAMS, "list_status", false,
                    DownloadPolicy.SourceRequestMode.NONE);
        }

        private static Fixture valuedRequest(String name) {
            return new Fixture(name, DownloadPolicy.Mode.ANN_DATE_RANGE, null, false,
                    DownloadPolicy.SourceRequestMode.RANGE, true);
        }

        private static Fixture dateRequest(String name) {
            return new Fixture(name, DownloadPolicy.Mode.ANN_DATE_RANGE, null, false,
                    DownloadPolicy.SourceRequestMode.DATE);
        }

        private void find(UUID taskId, Task... snapshots) {
            var index = new AtomicInteger();
            when(failures.find(taskId)).thenAnswer(call -> {
                findCalls.incrementAndGet();
                return Optional.of(snapshots[Math.min(index.getAndIncrement(), snapshots.length - 1)]);
            });
        }

        private RetryDownloadService service() {
            return service(new ParameterValidator());
        }

        private RetryDownloadService service(ParameterValidator validator) {
            return service(new PluginRegistry(List.of(plugin)),
                    new AdapterRegistry(List.of(adapter)), validator);
        }

        private RetryDownloadService service(PluginRegistry plugins, AdapterRegistry adapters) {
            return service(plugins, adapters, new ParameterValidator());
        }

        private RetryDownloadService service(PluginRegistry plugins, AdapterRegistry adapters,
                ParameterValidator validator) {
            return new RetryDownloadService(plugins, adapters, validator, commits,
                    failures, slot, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private FetchResult result(FetchBatch batch, List<List<Object>> rows) {
            return new FetchResult(new DownloadEnvelope(PLUGIN, api.apiName(), batch.sourceParams(),
                    adapter.definition().columns().stream().map(ColumnDefinition::name).toList(),
                    rows.size(), rows, DownloadStatus.SUCCESS, null), List.of());
        }
    }

    private static List<ParameterDescriptor> sourceParameters(DownloadPolicy.Mode mode,
            DownloadPolicy.SourceRequestMode sourceMode, String condition, boolean stock) {
        var source = new ArrayList<ParameterDescriptor>();
        if (condition != null) {
            source.add(condition.equals("list_status")
                    ? parameter(condition, ParameterType.TEXT, false, null)
                    : new ParameterDescriptor(condition, condition, null, ParameterType.ENUM,
                            true, null, List.of("SSE"), null, null));
        }
        if (stock) {
            source.add(parameter("ts_code", ParameterType.TS_CODE, false, null));
        }
        if (mode == DownloadPolicy.Mode.NATIVE_RANGE) {
            source.add(parameter("start_date", ParameterType.DATE_RANGE_MEMBER, true, "end_date"));
            source.add(parameter("end_date", ParameterType.DATE_RANGE_MEMBER, true, "start_date"));
        } else if (sourceMode == DownloadPolicy.SourceRequestMode.MONTH) {
            source.add(parameter("month", ParameterType.MONTH, false, null));
        } else if (sourceMode != DownloadPolicy.SourceRequestMode.NONE) {
            source.add(parameter(mode == DownloadPolicy.Mode.TRADE_DATE_RANGE ? "trade_date" : "ann_date",
                    ParameterType.DATE, false, null));
        }
        return source;
    }

    private static DownloadPolicy.DateSemantic semantic(DownloadPolicy.Mode mode) {
        return switch (mode) {
            case TRADE_DATE_RANGE -> DownloadPolicy.DateSemantic.TRADE_DATE;
            case ANN_DATE_RANGE -> DownloadPolicy.DateSemantic.ANN_DATE;
            case MONTH_RANGE -> DownloadPolicy.DateSemantic.COVERED_MONTH;
            case NATIVE_RANGE -> DownloadPolicy.DateSemantic.CALENDAR_DATE;
            case ORIGINAL_PARAMS -> DownloadPolicy.DateSemantic.NONE;
        };
    }

    private static ParameterDescriptor parameter(String name, ParameterType type, boolean required,
            String related) {
        return new ParameterDescriptor(name, name, null, type, required, null, List.of(), null, related);
    }

    private static ColumnDefinition column(String name, LogicalType type) {
        return new ColumnDefinition(name, name, type, false, 0,
                type == LogicalType.STRING ? 64 : null, null, null, List.of(), false);
    }
}
