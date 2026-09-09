package com.akkc.tensor.core.download;

import com.akkc.tensor.core.adapter.BusinessContentCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.persistence.BusinessKeyExtractor;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.retry.RetryTaskRepository.ItemKey;
import com.akkc.tensor.core.retry.RetryTaskRepository.Task;
import com.akkc.tensor.core.retry.RetryTaskStorageService;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.download.CalendarDecision;
import com.akkc.tensor.plugin.api.download.CalendarScope;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class RetryDownloadService {
    private final PluginRegistry pluginRegistry;
    private final AdapterRegistry adapterRegistry;
    private final DownloadParameterConverter converter;
    private final BatchCommitService commits;
    private final RetryTaskStorageService failures;
    private final DownloadExecutionSlot slot;
    private final Clock clock;

    public RetryDownloadService(PluginRegistry pluginRegistry, AdapterRegistry adapterRegistry,
            ParameterValidator validator, BatchCommitService commits,
            RetryTaskStorageService failures, DownloadExecutionSlot slot, Clock clock) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        this.converter = new DownloadParameterConverter(Objects.requireNonNull(validator, "validator"));
        this.commits = Objects.requireNonNull(commits, "commits");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DownloadExecutionResult execute(UUID taskId, RequestId requestId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(requestId, "requestId");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Retry orchestration must not run in a transaction");
        }
        try (var ignored = slot.acquire(taskId)) {
            Task task = failures.find(taskId).orElseThrow(RetryDownloadService::notFound);
            validateTask(taskId, task);
            DatasetKey dataset = task.header().datasetKey();
            DataSourcePlugin plugin = pluginRegistry.find(dataset.pluginId())
                    .orElseThrow(() -> access(ErrorCode.PLUGIN_DISABLED));
            ApiDescriptor api = api(dataset);
            var registered = adapterRegistry.find(dataset)
                    .orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
            if (!(registered instanceof GenericDatasetAdapter adapter)
                    || !adapter.datasetKey().equals(dataset)) {
                throw access(ErrorCode.DATASET_MISCONFIGURED);
            }
            DownloadContext context = () -> {};
            var processor = new RecoveryUnitProcessor(adapter, converter,
                    new BusinessKeyExtractor(), new BusinessContentCodec());
            List<RetryEntry> entries = preflight(task, api, processor, context);
            CalendarDecision calendar = calendar(plugin, task, api, entries, context);
            entries = classify(entries, calendar);
            preflightSources(plugin, api, entries, context);
            long skipped = skippedDates(api, entries);
            return new RetryExecution(requestId, taskId, dataset, api, processor,
                    entries, calendar, skipped).execute(plugin, context);
        }
    }

    private List<RetryEntry> preflight(Task task, ApiDescriptor api,
            RecoveryUnitProcessor processor, DownloadContext context) {
        var items = new ArrayList<>(task.items());
        items.sort(Comparator.comparing(item -> item.key().selector(), SELECTOR_ORDER));
        var entries = new ArrayList<RetryEntry>();
        for (var item : items) {
            var mapped = converter.mapRetry(api, task.header().taskParams(), item.key().selector());
            var batch = new FetchBatch(mapped.sourceParams().values(), api.downloadPolicy().recoveryPolicy());
            var session = processor.openRetry(api, task.header().taskParams(),
                    item.key().selector(), batch, context);
            entries.add(new RetryEntry(item.key(), batch, session, false));
        }
        return List.copyOf(entries);
    }

    private static CalendarDecision calendar(DataSourcePlugin plugin, Task task, ApiDescriptor api,
            List<RetryEntry> entries, DownloadContext context) {
        if (api.downloadPolicy().mode() != DownloadPolicy.Mode.TRADE_DATE_RANGE) return null;
        Set<LocalDate> dates = entries.stream().flatMap(entry -> dates(entry.item().selector()).stream())
                .collect(Collectors.toUnmodifiableSet());
        var scope = new CalendarScope(task.header().taskParams(), dates);
        CalendarDecision decision = plugin.confirmCalendar(api.apiName(), scope, context);
        if (decision == null || !decision.scope().equals(scope)) throw new CalendarUnconfirmedException();
        return decision;
    }

    private static List<RetryEntry> classify(List<RetryEntry> entries, CalendarDecision decision) {
        if (decision == null) return entries;
        return entries.stream().map(entry -> new RetryEntry(entry.item(), entry.batch(), entry.session(),
                decision.calendars().values().stream().allMatch(calendar ->
                        dates(entry.item().selector()).stream()
                                .allMatch(date -> Boolean.FALSE.equals(calendar.get(date))))))
                .toList();
    }

    private static void preflightSources(DataSourcePlugin plugin, ApiDescriptor api,
            List<RetryEntry> entries, DownloadContext context) {
        for (RetryEntry entry : entries) {
            if (entry.closed()) continue;
            var advice = plugin.planBatch(api.apiName(), entry.batch(), context);
            if (advice == null || advice == DownloadPolicy.BatchPlanning.UNCONFIRMED) {
                throw new SourceException(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED,
                        "Source completeness is unconfirmed");
            }
            var policy = api.downloadPolicy();
            boolean accepted = switch (policy.sourceRequestMode()) {
                case DATE -> advice == DownloadPolicy.BatchPlanning.SINGLE_DATE;
                case MONTH -> advice == DownloadPolicy.BatchPlanning.SINGLE_MONTH;
                case NONE -> advice == DownloadPolicy.BatchPlanning.ORIGINAL_PARAMS;
                case RANGE -> advice == DownloadPolicy.BatchPlanning.SOURCE_RANGE
                        || entry.item().selector().timeType() == RecoverySelector.TimeType.DATE
                        && policy.mode() != DownloadPolicy.Mode.NATIVE_RANGE
                        && advice == DownloadPolicy.BatchPlanning.SINGLE_DATE;
            };
            if (!accepted) {
                throw new SourceException(ErrorCode.SOURCE_REQUEST_UNCONFIRMED,
                        "Source request conditions are unconfirmed");
            }
        }
    }

    private static long skippedDates(ApiDescriptor api, List<RetryEntry> entries) {
        if (api.downloadPolicy().mode() != DownloadPolicy.Mode.TRADE_DATE_RANGE) return 0;
        var closed = new HashSet<LocalDate>();
        var retained = new HashSet<LocalDate>();
        for (RetryEntry entry : entries) {
            (entry.closed() ? closed : retained).addAll(dates(entry.item().selector()));
        }
        closed.removeAll(retained);
        return closed.size();
    }

    private static Set<LocalDate> dates(RecoverySelector selector) {
        return switch (selector.timeType()) {
            case DATE -> Set.of(LocalDate.parse(selector.timeValue()));
            case RANGE -> {
                String[] values = selector.timeValue().split("/");
                LocalDate start = LocalDate.parse(values[0]);
                yield start.datesUntil(LocalDate.parse(values[1]).plusDays(1))
                        .collect(Collectors.toUnmodifiableSet());
            }
            case MONTH, NONE -> throw invalid();
        };
    }

    private ApiDescriptor api(DatasetKey dataset) {
        List<PluginDescriptor> descriptors = pluginRegistry.descriptors().stream()
                .filter(candidate -> candidate.pluginId().equals(dataset.pluginId()))
                .filter(PluginDescriptor::downloadAvailable).toList();
        if (descriptors.size() != 1 || !descriptors.getFirst().datasets().contains(dataset)) {
            throw access(ErrorCode.DATASET_MISCONFIGURED);
        }
        return descriptors.getFirst().apis().stream()
                .filter(candidate -> candidate.apiName().equals(dataset.apiName()))
                .findFirst().orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
    }

    private static void validateTask(UUID taskId, Task task) {
        if (!task.header().taskId().equals(taskId) || task.items().isEmpty()
                || task.items().stream().anyMatch(item -> !item.key().taskId().equals(taskId))
                || task.items().stream().map(item -> item.key()).distinct().count() != task.items().size()) {
            throw invalid();
        }
    }

    private final class RetryExecution {
        private final RequestId requestId;
        private final UUID taskId;
        private final DatasetKey dataset;
        private final ApiDescriptor api;
        private final RecoveryUnitProcessor processor;
        private final List<RetryEntry> entries;
        private final CalendarDecision calendar;
        private final long skipped;
        private final Set<ItemKey> initial;
        private final Set<ItemKey> removed = new HashSet<>();
        private final List<RecoveryUnitProcessor.Failure> knownFailures = new ArrayList<>();
        private final CommittedKeyIndex index;
        private long completed;
        private long sourceRows;
        private long inserted;
        private long updated;
        private int current;

        private RetryExecution(RequestId requestId, UUID taskId, DatasetKey dataset, ApiDescriptor api,
                RecoveryUnitProcessor processor, List<RetryEntry> entries,
                CalendarDecision calendar, long skipped) {
            this.requestId = requestId;
            this.taskId = taskId;
            this.dataset = dataset;
            this.api = api;
            this.processor = processor;
            this.entries = entries;
            this.calendar = calendar;
            this.skipped = skipped;
            this.initial = entries.stream().map(RetryEntry::item).collect(Collectors.toUnmodifiableSet());
            this.index = new CommittedKeyIndex(dataset);
        }

        private DownloadExecutionResult execute(DataSourcePlugin plugin, DownloadContext context) {
            for (current = 0; current < entries.size(); current++) {
                RetryEntry entry = entries.get(current);
                if (entry.closed()) commit(entry, null,
                        commits.commitClosedRetry(dataset, api, entry.item(), calendar));
                else execute(entry, plugin, context);
            }
            DownloadExecutionResult.Outcome outcome = knownFailures.isEmpty()
                    ? completed > 0 ? sourceRows > 0
                            ? DownloadExecutionResult.Outcome.SUCCESS
                            : DownloadExecutionResult.Outcome.EMPTY
                            : DownloadExecutionResult.Outcome.NO_OPEN_DATES
                    : completed > 0 ? DownloadExecutionResult.Outcome.PARTIAL
                            : DownloadExecutionResult.Outcome.FAILED;
            String message = switch (outcome) {
                case SUCCESS -> "下载成功";
                case EMPTY -> "下载成功，0 条数据";
                case NO_OPEN_DATES -> "所选范围无开盘日期";
                case PARTIAL -> "部分完成，失败范围已保存";
                case FAILED -> "下载失败，失败范围已保存";
                case UNCONFIRMED -> throw new IllegalStateException();
            };
            return result(outcome, message, 0L, List.of(), List.of(), State.CONFIRMED);
        }

        private void execute(RetryEntry entry, DataSourcePlugin plugin, DownloadContext context) {
            FetchResult fetched = null;
            SourceException sourceFailure = null;
            try {
                fetched = plugin.fetchBatch(api.apiName(), entry.batch(), context);
            } catch (SourceException failure) {
                sourceFailure = failure;
            }
            var prepared = sourceFailure == null
                    ? entry.session().accept(fetched)
                    : entry.session().sourceFailed(sourceFailure);
            if (prepared.units().size() + prepared.failures().size() != 1) selectorMismatch();
            if (!prepared.failures().isEmpty()) {
                var failure = prepared.failures().getFirst();
                if (!failure.selector().equals(entry.item().selector())) selectorMismatch();
                fail(entry.item(), failure);
                return;
            }
            var unit = prepared.units().getFirst();
            if (!unit.selector().equals(entry.item().selector())) selectorMismatch();
            var validation = processor.validate(unit, index, clock.instant());
            if (validation instanceof RecoveryUnitProcessor.RejectedUnit rejected) {
                fail(entry.item(), rejected.failure());
            } else {
                var ready = (RecoveryUnitProcessor.ReadyUnit) validation;
                commit(entry, ready, commits.commitRetry(entry.item(), ready));
            }
        }

        private void commit(RetryEntry entry, RecoveryUnitProcessor.ReadyUnit ready,
                BatchCommitService.CommitResult result) {
            if (!entry.item().selector().equals(result.selector())) {
                throw new IllegalStateException("Commit selector mismatch");
            }
            if (result instanceof BatchCommitService.Committed committed) {
                if (ready != null) {
                    index.confirmCommitted(ready);
                    completed++;
                    sourceRows += committed.sourceRowCount();
                    inserted += committed.writeCounts().insertedRows();
                    updated += committed.writeCounts().updatedRows();
                }
                removed.add(entry.item());
                if (committed.stopExecution()) {
                    throw stop(ErrorCode.INTERNAL_ERROR, State.CONFIRMED, List.of());
                }
            } else if (result instanceof BatchCommitService.Unconfirmed) {
                throw stop(ErrorCode.COMMIT_UNCONFIRMED, State.COMMIT_UNKNOWN,
                        List.of(entry.item().selector()));
            } else {
                var failure = new RecoveryUnitProcessor.Failure(entry.item().selector(),
                        ErrorCode.PERSISTENCE_FAILED, "Persistence failed");
                knownFailures.add(failure);
                if (result instanceof BatchCommitService.Unavailable
                        || result instanceof BatchCommitService.RolledBack rolledBack
                        && rolledBack.storageUnavailable()) {
                    throw stop(ErrorCode.PERSISTENCE_FAILED, State.CONFIRMED, List.of());
                }
                update(entry.item(), ErrorCode.PERSISTENCE_FAILED);
            }
        }

        private void fail(ItemKey item, RecoveryUnitProcessor.Failure failure) {
            knownFailures.add(failure);
            update(item, failure.errorCode());
        }

        private void update(ItemKey item, ErrorCode code) {
            try {
                var saved = failures.updateReason(item, code);
                if (!saved.key().equals(item)) throw new IllegalStateException("Saved failure key mismatch");
            } catch (TensorException failure) {
                if (failure.code() != ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED) throw failure;
                throw stop(failure.code(), State.UPDATE_UNKNOWN, List.of());
            }
        }

        private DownloadExecutionException stop(ErrorCode code, State state,
                List<RecoverySelector> unknown) {
            var future = entries.subList(current + 1, entries.size());
            long notStarted = future.stream().filter(entry -> !entry.closed()).count();
            List<RecoverySelector> scopes = future.stream().map(entry -> entry.item().selector()).toList();
            return new DownloadExecutionException(code, result(DownloadExecutionResult.Outcome.UNCONFIRMED,
                    "结果未确认；计数仅包含此前确认项", notStarted, scopes, unknown, state));
        }

        private DownloadExecutionResult result(DownloadExecutionResult.Outcome outcome, String message,
                Long notStarted, List<RecoverySelector> scopes, List<RecoverySelector> unknown,
                State state) {
            Set<ItemKey> retained = new HashSet<>(initial);
            retained.removeAll(removed);
            UUID resultTaskId;
            Long remaining;
            if (state == State.UPDATE_UNKNOWN) {
                resultTaskId = taskId;
                remaining = null;
            } else if (state == State.COMMIT_UNKNOWN) {
                var certain = new HashSet<>(retained);
                unknown.stream().map(selector -> new ItemKey(taskId, selector)).forEach(certain::remove);
                resultTaskId = certain.isEmpty() ? null : taskId;
                remaining = null;
            } else {
                remaining = (long) retained.size();
                resultTaskId = retained.isEmpty() ? null : taskId;
            }
            var orderedFailures = knownFailures.stream()
                    .sorted(Comparator.comparing(RecoveryUnitProcessor.Failure::selector, SELECTOR_ORDER)).toList();
            var status = outcome == DownloadExecutionResult.Outcome.UNCONFIRMED
                    ? DownloadExecutionResult.FailureRecordStatus.UNCONFIRMED
                    : orderedFailures.isEmpty()
                            ? DownloadExecutionResult.FailureRecordStatus.NOT_REQUIRED
                            : DownloadExecutionResult.FailureRecordStatus.CONFIRMED;
            return new DownloadExecutionResult(requestId, outcome, dataset.pluginId(), api.apiName(),
                    sourceRows, inserted, updated, message, completed, orderedFailures.size(),
                    notStarted, skipped, resultTaskId, remaining, status, orderedFailures,
                    List.copyOf(scopes), List.copyOf(unknown));
        }
    }

    private record RetryEntry(ItemKey item, FetchBatch batch,
            RecoveryUnitProcessor.BatchSession session, boolean closed) {
        private RetryEntry {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(batch, "batch");
            Objects.requireNonNull(session, "session");
        }
    }

    private enum State { CONFIRMED, UPDATE_UNKNOWN, COMMIT_UNKNOWN }

    private static final Comparator<RecoverySelector> SELECTOR_ORDER = Comparator
            .comparing((RecoverySelector selector) -> endpoint(selector, false))
            .thenComparing(selector -> endpoint(selector, true))
            .thenComparing(selector -> selector.targetType().name())
            .thenComparing(RecoverySelector::targetValue)
            .thenComparing(selector -> selector.timeType().name())
            .thenComparing(RecoverySelector::timeValue);

    private static LocalDate endpoint(RecoverySelector selector, boolean end) {
        return switch (selector.timeType()) {
            case NONE -> LocalDate.MIN;
            case DATE -> LocalDate.parse(selector.timeValue());
            case MONTH -> end ? YearMonth.parse(selector.timeValue()).atEndOfMonth()
                    : YearMonth.parse(selector.timeValue()).atDay(1);
            case RANGE -> LocalDate.parse(selector.timeValue().split("/")[end ? 1 : 0]);
        };
    }

    private static void selectorMismatch() {
        throw new IllegalStateException("Retry unit selector mismatch");
    }

    private static TensorException notFound() {
        return new RetryException(ErrorCode.RETRY_TASK_NOT_FOUND, "Retry task was not found");
    }

    private static TensorException invalid() {
        return new RetryException(ErrorCode.RETRY_TASK_INVALID, "Saved retry task is invalid");
    }

    private static TensorException access(ErrorCode code) {
        return new RetryException(code, code == ErrorCode.PLUGIN_DISABLED
                ? "Download plugin is unavailable" : "Download dataset is unavailable");
    }

    private static final class RetryException extends TensorException {
        private RetryException(ErrorCode code, String message) {
            super(code, message);
        }
    }
}
