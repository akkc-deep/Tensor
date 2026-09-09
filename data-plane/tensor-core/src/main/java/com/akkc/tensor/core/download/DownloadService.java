package com.akkc.tensor.core.download;

import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.validation.ValidatedParameters;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.time.Clock;
import com.akkc.tensor.core.adapter.BusinessContentCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.persistence.BusinessKeyExtractor;
import com.akkc.tensor.core.retry.RetryTaskRepository;
import com.akkc.tensor.core.retry.RetryTaskStorageService;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class DownloadService {
    private final PluginRegistry pluginRegistry;
    private final AdapterRegistry adapterRegistry;
    private final ParameterValidator parameterValidator;
    private final PersistenceService persistenceService;
    private final Clock clock;
    private final BatchCommitService commits;
    private final RetryTaskStorageService failures;
    private final DownloadExecutionSlot slot;
    private final DownloadParameterConverter converter;
    private final DownloadBatchPlanner planner;

    public DownloadService(
            PluginRegistry pluginRegistry,
            AdapterRegistry adapterRegistry,
            ParameterValidator parameterValidator,
            PersistenceService persistenceService,
            BatchCommitService commits,
            com.akkc.tensor.core.retry.RetryTaskStorageService failures,
            DownloadExecutionSlot slot,
            Clock clock) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        this.parameterValidator = Objects.requireNonNull(parameterValidator, "parameterValidator");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.commits = Objects.requireNonNull(commits, "commits");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.converter = new DownloadParameterConverter(parameterValidator);
        this.planner = new DownloadBatchPlanner(converter);
    }

    public DownloadExecutionResult executeInitial(PluginId pluginId, ApiName apiName,
            Map<String,Object> params, RequestId requestId) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(requestId, "requestId");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Download orchestration must not run in a transaction");
        }
        DataSourcePlugin plugin = pluginRegistry.find(pluginId).orElseThrow(() -> access(ErrorCode.PLUGIN_DISABLED));
        ApiDescriptor api = descriptor(pluginId).apis().stream().filter(candidate -> candidate.apiName().equals(apiName))
                .findFirst().orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        DatasetKey key = DatasetKey.of(pluginId, apiName);
        DatasetAdapter registered = adapterRegistry.find(key).orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        if (!(registered instanceof GenericDatasetAdapter adapter) || !adapter.datasetKey().equals(key)) {
            throw access(ErrorCode.DATASET_MISCONFIGURED);
        }
        ValidatedParameters original = converter.bindInitial(api, params);
        try (var ignored = slot.acquire(null)) {
            DownloadContext context = () -> {};
            var plan = planner.planInitial(plugin, api, original.values(), context);
            var processor = new RecoveryUnitProcessor(adapter, converter, new BusinessKeyExtractor(), new BusinessContentCodec());
            var sessions = new ArrayList<RecoveryUnitProcessor.BatchSession>();
            for (var batch : plan.batches()) {
                sessions.add(processor.openInitial(api, plan.originalParams(), batch.scope(), batch.fetchBatch(), context));
            }
            return new InitialExecution(requestId, api, plan, processor, sessions).execute(plugin, context);
        }
    }

    private final class InitialExecution {
        private final RequestId requestId;
        private final ApiDescriptor api;
        private final DownloadBatchPlanner.Plan plan;
        private final RecoveryUnitProcessor processor;
        private final List<RecoveryUnitProcessor.BatchSession> sessions;
        private final CommittedKeyIndex index;
        private final Map<String,Object> taskParams;
        private final List<RecoveryUnitProcessor.Failure> knownFailures = new ArrayList<>();
        private final Set<RecoverySelector> saved = new HashSet<>();
        private final ArrayDeque<Event> pending = new ArrayDeque<>();
        private long completed, sourceRows, inserted, updated;
        private UUID taskId;
        private int nextBatch;
        private boolean saveUnknown;

        private InitialExecution(RequestId requestId, ApiDescriptor api, DownloadBatchPlanner.Plan plan,
                RecoveryUnitProcessor processor, List<RecoveryUnitProcessor.BatchSession> sessions) {
            this.requestId = requestId;
            this.api = api;
            this.plan = plan;
            this.processor = processor;
            this.sessions = sessions;
            index = new CommittedKeyIndex(plan.datasetKey());
            taskParams = converter.taskParameters(api, plan.originalParams(), RecoverySelector.TargetType.REQUEST);
        }

        private DownloadExecutionResult execute(DataSourcePlugin plugin, DownloadContext context) {
            while (nextBatch < plan.batches().size()) {
                int current = nextBatch++;
                prepare(plugin, context, current);
                while (!pending.isEmpty()) {
                    Event event = pending.removeFirst();
                    if (event.failure() != null) save(event.failure());
                    else {
                        var validation = processor.validate(event.unit(), index, clock.instant());
                        if (validation instanceof RecoveryUnitProcessor.RejectedUnit rejected) fail(rejected.failure());
                        else commit((RecoveryUnitProcessor.ReadyUnit) validation);
                    }
                }
            }
            var outcome = plan.batches().isEmpty() ? DownloadExecutionResult.Outcome.NO_OPEN_DATES
                    : knownFailures.isEmpty() ? sourceRows == 0 ? DownloadExecutionResult.Outcome.EMPTY : DownloadExecutionResult.Outcome.SUCCESS
                    : completed == 0 ? DownloadExecutionResult.Outcome.FAILED : DownloadExecutionResult.Outcome.PARTIAL;
            String message = switch (outcome) {
                case NO_OPEN_DATES -> "所选区间无开盘日期";
                case EMPTY -> "下载成功，0 条数据";
                case SUCCESS -> "下载成功";
                case PARTIAL -> "部分完成，失败范围已保存";
                case FAILED -> "下载失败，失败范围已保存";
                case UNCONFIRMED -> throw new IllegalStateException();
            };
            return result(outcome, message, 0L, List.of(), List.of());
        }

        // This frame releases the source envelope and PreparedBatch before processing any event.
        private void prepare(DataSourcePlugin plugin, DownloadContext context, int current) {
            FetchResult fetched = null;
            SourceException sourceFailure = null;
            try { fetched = plugin.fetchBatch(api.apiName(), plan.batches().get(current).fetchBatch(), context); }
            catch (SourceException failure) { sourceFailure = failure; }
            var prepared = sourceFailure == null ? sessions.get(current).accept(fetched) : sessions.get(current).sourceFailed(sourceFailure);
            knownFailures.addAll(prepared.failures());
            var events = new ArrayList<Event>();
            prepared.units().forEach(unit -> events.add(new Event(unit.selector(), unit, null)));
            prepared.failures().forEach(failure -> events.add(new Event(failure.selector(), null, failure)));
            events.sort(Comparator.comparing(Event::selector, SELECTOR_ORDER));
            pending.addAll(events);
        }

        private void commit(RecoveryUnitProcessor.ReadyUnit ready) {
            var result = commits.commitInitial(ready);
            if (!ready.selector().equals(result.selector())) throw new IllegalStateException("Commit selector mismatch");
            if (result instanceof BatchCommitService.Committed committed) {
                index.confirmCommitted(ready);
                completed++;
                sourceRows += committed.sourceRowCount();
                inserted += committed.writeCounts().insertedRows();
                updated += committed.writeCounts().updatedRows();
                if (committed.stopExecution()) throw stop(ErrorCode.INTERNAL_ERROR, List.of());
            } else if (result instanceof BatchCommitService.Unconfirmed) {
                throw stop(ErrorCode.COMMIT_UNCONFIRMED, List.of(ready.selector()));
            } else {
                var failure = new RecoveryUnitProcessor.Failure(ready.selector(), ErrorCode.PERSISTENCE_FAILED, "Persistence failed");
                knownFailures.add(failure);
                if (result instanceof BatchCommitService.Unavailable
                        || result instanceof BatchCommitService.RolledBack rolledBack && rolledBack.storageUnavailable()) {
                    throw stop(ErrorCode.PERSISTENCE_FAILED, List.of());
                }
                save(failure);
            }
        }

        private void fail(RecoveryUnitProcessor.Failure failure) {
            knownFailures.add(failure);
            save(failure);
        }

        private void save(RecoveryUnitProcessor.Failure failure) {
            if (!taskParams.equals(converter.taskParameters(api, plan.originalParams(), failure.selector().targetType()))) {
                throw new IllegalStateException("Failure task parameters mismatch");
            }
            var item = new RetryTaskRepository.Failure(failure.selector(), failure.errorCode());
            RetryTaskRepository.SavedFailure confirmed;
            try {
                confirmed = taskId == null ? failures.create(plan.datasetKey(), taskParams, item) : failures.append(taskId, item);
            } catch (TensorException exception) {
                if (exception.code() != ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED) throw exception;
                saveUnknown = true;
                throw stop(exception.code(), List.of());
            }
            if (!confirmed.key().selector().equals(failure.selector())
                    || taskId != null && !taskId.equals(confirmed.key().taskId())) {
                throw new IllegalStateException("Saved failure key mismatch");
            }
            taskId = confirmed.key().taskId();
            saved.add(failure.selector());
        }

        private DownloadExecutionException stop(ErrorCode code, List<RecoverySelector> unknown) {
            var scopes = new ArrayList<RecoverySelector>();
            pending.stream().filter(event -> event.unit() != null).map(Event::selector).forEach(scopes::add);
            Long remaining = (long) scopes.size();
            for (int i = nextBatch; i < sessions.size(); i++) {
                scopes.add(plan.batches().get(i).scope());
                if (sessions.get(i).usesIndependentUnits()) remaining = null;
                else if (remaining != null) remaining++;
            }
            scopes.sort(SELECTOR_ORDER);
            return new DownloadExecutionException(code, result(DownloadExecutionResult.Outcome.UNCONFIRMED,
                    "结果未确认；计数仅包含此前确认项", remaining, scopes, unknown));
        }

        private DownloadExecutionResult result(DownloadExecutionResult.Outcome outcome, String message,
                Long notStarted, List<RecoverySelector> scopes, List<RecoverySelector> unknown) {
            knownFailures.sort(Comparator.comparing(RecoveryUnitProcessor.Failure::selector, SELECTOR_ORDER));
            var status = outcome == DownloadExecutionResult.Outcome.UNCONFIRMED ? DownloadExecutionResult.FailureRecordStatus.UNCONFIRMED
                    : knownFailures.isEmpty() ? DownloadExecutionResult.FailureRecordStatus.NOT_REQUIRED : DownloadExecutionResult.FailureRecordStatus.CONFIRMED;
            return new DownloadExecutionResult(requestId, outcome, plan.datasetKey().pluginId(), api.apiName(),
                    sourceRows, inserted, updated, message, completed, knownFailures.size(), notStarted, plan.skippedDates().size(),
                    taskId, saveUnknown ? null : (long) saved.size(), status, knownFailures, scopes, unknown);
        }
    }

    private record Event(RecoverySelector selector, RecoveryUnitProcessor.UnitInput unit, RecoveryUnitProcessor.Failure failure) {}
    private static final Comparator<RecoverySelector> SELECTOR_ORDER = Comparator
            .comparing((RecoverySelector selector) -> endpoint(selector, false))
            .thenComparing(selector -> endpoint(selector, true))
            .thenComparing(selector -> selector.targetType().name()).thenComparing(RecoverySelector::targetValue)
            .thenComparing(selector -> selector.timeType().name()).thenComparing(RecoverySelector::timeValue);
    private static LocalDate endpoint(RecoverySelector selector, boolean end) {
        return switch (selector.timeType()) {
            case NONE -> LocalDate.MIN;
            case DATE -> LocalDate.parse(selector.timeValue());
            case MONTH -> end ? YearMonth.parse(selector.timeValue()).atEndOfMonth() : YearMonth.parse(selector.timeValue()).atDay(1);
            case RANGE -> LocalDate.parse(selector.timeValue().split("/")[end ? 1 : 0]);
        };
    }

    public DownloadResult execute(
            PluginId pluginId,
            ApiName apiName,
            Map<String, Object> params,
            RequestId requestId) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(requestId, "requestId");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Download orchestration must not run in a transaction");
        }

        DataSourcePlugin plugin = pluginRegistry.find(pluginId)
                .orElseThrow(() -> access(ErrorCode.PLUGIN_DISABLED));
        ApiDescriptor api = descriptor(pluginId).apis().stream()
                .filter(candidate -> candidate.apiName().equals(apiName))
                .findFirst()
                .orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        DatasetKey key = DatasetKey.of(pluginId, apiName);
        DatasetAdapter adapter = adapterRegistry.find(key)
                .orElseThrow(() -> access(ErrorCode.DATASET_MISCONFIGURED));
        ValidatedParameters validated = parameterValidator.validate(api.sourceParameters(), params);
        try (var ignored = slot.acquire(null)) {
        FetchResult fetched = plugin.download(apiName, validated.values(), () -> {});
        if (fetched == null || fetched.envelope() == null || !fetched.failures().isEmpty()) {
            throw invalidPayload();
        }
        DownloadEnvelope envelope = fetched.envelope();
        if (envelope.status() == DownloadStatus.FAILURE) {
            throw new SourceException(ErrorCode.SOURCE_PAYLOAD_INVALID, envelope.error());
        }
        if (!envelope.pluginId().equals(pluginId)
                || !envelope.apiName().equals(apiName)
                || !envelope.params().equals(validated.values())) {
            throw invalidPayload();
        }
        if (envelope.rowCount() == 0) {
            return new DownloadResult(
                    requestId, DownloadOutcome.EMPTY, pluginId, apiName, 0, 0, 0,
                    "下载成功，0 条数据");
        }

        AdaptedBatch batch = adapter.adapt(envelope, clock.instant());
        WriteCounts counts;
        try {
            counts = persistenceService.persist(batch);
        } catch (DataAccessException | TransactionException exception) {
            throw persistenceFailure(exception);
        }
        return new DownloadResult(
                requestId,
                DownloadOutcome.SUCCESS,
                pluginId,
                apiName,
                envelope.rowCount(),
                counts.insertedRows(),
                counts.updatedRows(),
                "下载成功");
        }
    }

    private PluginDescriptor descriptor(PluginId pluginId) {
        List<PluginDescriptor> matches = pluginRegistry.descriptors().stream()
                .filter(candidate -> candidate.pluginId().equals(pluginId))
                .filter(PluginDescriptor::downloadAvailable)
                .toList();
        if (matches.size() != 1) {
            throw access(ErrorCode.DATASET_MISCONFIGURED);
        }
        return matches.get(0);
    }

    private static DownloadAccessException access(ErrorCode code) {
        return new DownloadAccessException(
                code,
                code == ErrorCode.PLUGIN_DISABLED
                        ? "Download plugin is unavailable"
                        : "Download dataset is unavailable");
    }

    private static SourceException invalidPayload() {
        return new SourceException(
                ErrorCode.SOURCE_PAYLOAD_INVALID, "Source returned an invalid payload");
    }

    private static PersistenceException persistenceFailure(RuntimeException cause) {
        PersistenceException exception = new PersistenceException();
        exception.initCause(cause);
        return exception;
    }

    private static final class PersistenceException extends TensorException {
        private PersistenceException() {
            super(ErrorCode.PERSISTENCE_FAILED, "Dataset persistence failed");
        }
    }

    private static final class DownloadAccessException extends TensorException {
        private DownloadAccessException(ErrorCode code, String message) {
            super(requireAccessCode(code), message);
        }

        private static ErrorCode requireAccessCode(ErrorCode code) {
            if (code != ErrorCode.PLUGIN_DISABLED && code != ErrorCode.DATASET_MISCONFIGURED) {
                throw new IllegalArgumentException("Unsupported download access error code");
            }
            return code;
        }
    }
}
