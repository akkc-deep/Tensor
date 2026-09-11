package com.akkc.tensor.core.download.task;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.BatchDownloadSupport;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Local admission and replay validation for durable download tasks. */
public final class DownloadTaskService {
    private static final Pattern PARAMETER_NAME = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final String RANGE_UNSUPPORTED = "Range download is not supported";
    private static final String INPUT_UNAVAILABLE = "Plugin or dataset is unavailable";

    private final PluginRegistry plugins;
    private final DatasetCatalog datasets;
    private final AdapterRegistry adapters;
    private final ParameterValidator validator;
    private final DownloadTaskRepository repository;
    private final DownloadTaskJson json;
    private final Clock clock;
    private final UUID activeRunId;
    private final Settings settings;
    private final ReentrantLock admissionLock = new ReentrantLock();
    private DownloadTaskCoordinator coordinator;

    public DownloadTaskService(
            PluginRegistry plugins,
            DatasetCatalog datasets,
            AdapterRegistry adapters,
            ParameterValidator validator,
            DownloadTaskRepository repository,
            DownloadTaskJson json,
            Clock clock,
            UUID activeRunId,
            Settings settings) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.datasets = Objects.requireNonNull(datasets, "datasets");
        this.adapters = Objects.requireNonNull(adapters, "adapters");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.activeRunId = Objects.requireNonNull(activeRunId, "activeRunId");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public record Settings(boolean enabled, int maxQueuedTasks, int maxRangeDays) {
        public Settings {
            if (maxQueuedTasks <= 0 || maxRangeDays <= 0)
                throw new IllegalArgumentException("Task limits must be positive");
        }

        public static Settings defaults() {
            return new Settings(true, 100, 36_600);
        }
    }

    public record Submission(
            UUID submissionId, DatasetKey datasetKey, DownloadMode mode, Map<String, Object> params) {
        public Submission {
            if (submissionId == null || datasetKey == null || mode == null || params == null)
                throw new TaskException(ErrorCode.PARAM_INVALID);
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : params.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !PARAMETER_NAME.matcher(key).matches()
                        || !(entry.getValue() instanceof String value))
                    throw new TaskException(ErrorCode.PARAM_INVALID);
                copy.put(key, value);
            }
            params = Collections.unmodifiableMap(copy);
        }
    }

    public record SubmissionResult(DownloadTask task, boolean created) {
        public SubmissionResult {
            Objects.requireNonNull(task, "task");
        }
    }

    public record SingleCapability(boolean available, ApiDescriptor api) {
        public SingleCapability {
            Objects.requireNonNull(api, "api");
        }
    }

    public record DownloadCapabilities(SingleCapability single, BatchDownloadDescriptor range) {
        public DownloadCapabilities {
            Objects.requireNonNull(single, "single");
            Objects.requireNonNull(range, "range");
        }
    }

    public DownloadCapabilities capabilities(DatasetKey key) {
        outsideTransaction();
        if (key == null) throw new TaskException(ErrorCode.PARAM_INVALID);
        ApiDescriptor single = descriptor(key);
        Optional<DataSourcePlugin> current = plugins.find(key.pluginId());
        boolean inputsAvailable = current.isPresent()
                && datasets.find(key).isPresent()
                && adapters.find(key).isPresent();
        BatchDownloadDescriptor range;
        if (!inputsAvailable) {
            range = unsupported(INPUT_UNAVAILABLE);
        } else if (current.get() instanceof BatchDownloadSupport batch) {
            range = batchDescriptor(batch, key).orElseGet(() -> unsupported(RANGE_UNSUPPORTED));
        } else {
            range = unsupported(RANGE_UNSUPPORTED);
        }
        return new DownloadCapabilities(new SingleCapability(inputsAvailable, single), range);
    }

    public SubmissionResult submit(Submission request) {
        outsideTransaction();
        if (request == null) throw new TaskException(ErrorCode.PARAM_INVALID);
        admissionLock.lock();
        try {
            Optional<DownloadTask> existing = repository.findSubmission(request.submissionId());
            if (existing.isPresent()) return replay(request, existing.get());
            if (coordinator != null) coordinator.checkAdmission();
            Current current = current(request.datasetKey(), request.mode());
            Map<String, Object> normalized = normalize(current, request.params());
            try {
                json.writeTaskParams(normalized);
            } catch (IllegalArgumentException invalid) {
                throw new TaskException(ErrorCode.PARAM_INVALID);
            }
            String definition = definitionHash(current);
            String policy = policySnapshot(current);
            if (repository.queuedCount() >= settings.maxQueuedTasks())
                throw new TaskException(ErrorCode.TASK_QUEUE_FULL);
            try {
                DownloadTask task = repository.insert(new DownloadTaskRepository.NewTask(
                        UUID.randomUUID(), request.submissionId(), request.datasetKey(), request.mode(),
                        normalized, definition, policy, activeRunId, clock.instant()));
                return new SubmissionResult(task, true);
            } catch (DuplicateKeyException duplicate) {
                return repository.findSubmission(request.submissionId())
                        .map(task -> replay(request, task))
                        .orElseThrow(() -> new TaskException(ErrorCode.PERSISTENCE_FAILED));
            }
        } finally {
            admissionLock.unlock();
        }
    }

    public void validateReplay(DownloadTask task) {
        validatedCurrent(task);
    }

    public DownloadTask retry(UUID taskId, long expectedVersion) {
        return requeue(taskId, expectedVersion, DownloadTaskRepository.RequeueMode.RETRY);
    }

    public DownloadTask resume(UUID taskId, long expectedVersion) {
        return requeue(taskId, expectedVersion, DownloadTaskRepository.RequeueMode.RESUME);
    }

    public record ControlAvailability(boolean canRetry, boolean canResume) {}

    public ControlAvailability controls(DownloadTask task) {
        outsideTransaction();
        if (task == null) throw new TaskException(ErrorCode.PARAM_INVALID);
        admissionLock.lock();
        try {
            boolean allowed = settings.enabled() && coordinator != null && coordinator.controlAllowed();
            return new ControlAvailability(allowed && retryable(task),
                    allowed && task.status() == DownloadTask.Status.INTERRUPTED);
        } finally {
            admissionLock.unlock();
        }
    }

    private DownloadTask requeue(UUID taskId, long expectedVersion, DownloadTaskRepository.RequeueMode mode) {
        outsideTransaction();
        if (taskId == null || expectedVersion < 1) throw new TaskException(ErrorCode.PARAM_INVALID);
        admissionLock.lock();
        try {
            DownloadTask task = repository.findTask(taskId)
                    .orElseThrow(() -> new TaskException(ErrorCode.TASK_NOT_FOUND));
            boolean statusAllowed = mode == DownloadTaskRepository.RequeueMode.RETRY
                    ? retryable(task) : task.status() == DownloadTask.Status.INTERRUPTED;
            if (task.version() != expectedVersion || !statusAllowed
                    || coordinator == null || !coordinator.controlAllowed())
                throw new TaskException(ErrorCode.TASK_STATE_CONFLICT);
            ExecutionDefinition definition = executionDefinition(task);
            PluginReadiness readiness;
            try {
                readiness = Objects.requireNonNull(definition.plugin().readiness());
            } catch (RuntimeException invalid) {
                throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
            }
            if (!readiness.downloadAvailable()) throw new TaskException(ErrorCode.PLUGIN_DISABLED);
            if (repository.queuedCount() >= settings.maxQueuedTasks())
                throw new TaskException(ErrorCode.TASK_QUEUE_FULL);
            coordinator.requeueValidated(taskId);
            repository.requeue(taskId, expectedVersion, activeRunId, mode, clock.instant());
            return repository.findTask(taskId).orElseThrow(() -> new TaskException(ErrorCode.PERSISTENCE_FAILED));
        } finally {
            admissionLock.unlock();
        }
    }

    private static boolean retryable(DownloadTask task) {
        return task.status() == DownloadTask.Status.FAILED || task.status() == DownloadTask.Status.PARTIAL_FAILED;
    }

    ReentrantLock coordinationLock() {
        return admissionLock;
    }

    UUID activeRunId() {
        return activeRunId;
    }

    Settings settings() {
        return settings;
    }

    void bindCoordinator(DownloadTaskCoordinator value) {
        Objects.requireNonNull(value, "coordinator");
        admissionLock.lock();
        try {
            if (coordinator != null) throw new IllegalStateException("Download task coordinator is already bound");
            coordinator = value;
        } finally {
            admissionLock.unlock();
        }
    }

    ExecutionDefinition executionDefinition(DownloadTask task) {
        Current current = validatedCurrent(task);
        return new ExecutionDefinition(current.plugin(), adapters.find(task.datasetKey()).orElseThrow(),
                current.dataset(), current.range());
    }

    record ExecutionDefinition(DataSourcePlugin plugin, DatasetAdapter adapter,
            DatasetDefinition dataset, BatchDownloadDescriptor range) {}

    private Current validatedCurrent(DownloadTask task) {
        outsideTransaction();
        if (task == null) throw new TaskException(ErrorCode.PARAM_INVALID);
        Current current = current(task.datasetKey(), task.mode());
        if (!definitionHash(current).equals(task.definitionHash()))
            throw new TaskException(ErrorCode.TASK_DEFINITION_CHANGED);
        normalize(current, task.params());
        return current;
    }

    private SubmissionResult replay(Submission request, DownloadTask task) {
        if (!task.datasetKey().equals(request.datasetKey()) || task.mode() != request.mode())
            throw new TaskException(ErrorCode.SUBMISSION_CONFLICT);
        if (task.params().equals(request.params())) return new SubmissionResult(task, false);
        try {
            ApiDescriptor replayApi;
            if (task.mode() == DownloadMode.RANGE) {
                BatchDownloadDescriptor range = json.readRangePolicy(task.policySnapshot());
                replayApi = rangeApi(descriptorOrFallback(task.datasetKey()), range);
            } else {
                replayApi = descriptor(task.datasetKey());
            }
            Map<String, Object> normalized = validator.validate(replayApi, request.params()).values();
            if (task.requestHash().equals(json.requestHash(task.datasetKey(), task.mode(), normalized)))
                return new SubmissionResult(task, false);
        } catch (RuntimeException ignored) {
            // Existing keys expose only the stable conflict result when equivalence cannot be proven.
        }
        throw new TaskException(ErrorCode.SUBMISSION_CONFLICT);
    }

    private Current current(DatasetKey key, DownloadMode mode) {
        if (!settings.enabled()) throw new TaskException(ErrorCode.PLUGIN_DISABLED);
        DataSourcePlugin plugin = plugins.find(key.pluginId())
                .orElseThrow(() -> new TaskException(ErrorCode.PLUGIN_DISABLED));
        ApiDescriptor single = descriptor(key);
        DatasetDefinition dataset = datasets.find(key)
                .orElseThrow(() -> new TaskException(ErrorCode.DATASET_MISCONFIGURED));
        if (adapters.find(key).isEmpty()) throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
        if (mode == DownloadMode.SINGLE) return new Current(plugin, single, dataset, mode, null);
        if (!(plugin instanceof BatchDownloadSupport batch))
            throw new TaskException(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        BatchDownloadDescriptor range = batchDescriptor(batch, key)
                .filter(value -> value.availability() == BatchDownloadDescriptor.Availability.AVAILABLE)
                .orElseThrow(() -> new TaskException(ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE));
        return new Current(plugin, rangeApi(single, range), dataset, mode, range);
    }

    private Map<String, Object> normalize(Current current, Map<String, Object> raw) {
        Map<String, Object> normalized;
        try {
            normalized = validator.validate(current.api(), raw).values();
        } catch (TensorException classified) {
            throw classified;
        } catch (RuntimeException invalidMetadata) {
            throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
        }
        if (current.mode() == DownloadMode.SINGLE) return normalized;
        BatchDownloadDescriptor range = current.range();
        DateRange requested;
        try {
            requested = new DateRange(
                    LocalDate.parse((String) normalized.get(range.startParameter()), DateTimeFormatter.BASIC_ISO_DATE),
                    LocalDate.parse((String) normalized.get(range.endParameter()), DateTimeFormatter.BASIC_ISO_DATE));
        } catch (RuntimeException invalid) {
            throw new TaskException(ErrorCode.PARAM_INVALID);
        }
        if (ChronoUnit.DAYS.between(requested.start(), requested.end()) + 1 > settings.maxRangeDays())
            throw new TaskException(ErrorCode.TASK_LIMIT_EXCEEDED);
        BatchDownloadSupport batch = (BatchDownloadSupport) current.plugin();
        try {
            Map<String, Object> source = batch.sourceParameters(current.api().apiName(), normalized, requested);
            json.writeBatchParams(source);
        } catch (TensorException classified) {
            throw classified;
        } catch (RuntimeException invalid) {
            throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
        }
        return normalized;
    }

    private ApiDescriptor descriptor(DatasetKey key) {
        List<com.akkc.tensor.plugin.api.descriptor.PluginDescriptor> sources = plugins.descriptors().stream()
                .filter(value -> value.pluginId().equals(key.pluginId())).toList();
        if (sources.size() != 1) throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
        List<ApiDescriptor> apis = sources.getFirst().apis().stream()
                .filter(value -> value.apiName().equals(key.apiName())).toList();
        if (apis.size() != 1) throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
        return apis.getFirst();
    }

    private ApiDescriptor descriptorOrFallback(DatasetKey key) {
        try {
            return descriptor(key);
        } catch (TaskException missing) {
            return new ApiDescriptor(key.apiName(), key.apiName().value(), "historical", QueryMode.date_range,
                    List.of());
        }
    }

    private Optional<BatchDownloadDescriptor> batchDescriptor(BatchDownloadSupport plugin, DatasetKey key) {
        try {
            return Objects.requireNonNull(plugin.batchDescriptor(key.apiName()), "batchDescriptor");
        } catch (TensorException classified) {
            throw classified;
        } catch (RuntimeException failure) {
            throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    private String definitionHash(Current current) {
        try {
            return json.definitionHash(current.api(), current.dataset(), current.mode(), current.range());
        } catch (IllegalArgumentException invalid) {
            throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    private String policySnapshot(Current current) {
        try {
            return json.policySnapshot(current.mode(), current.range());
        } catch (IllegalArgumentException invalid) {
            throw new TaskException(ErrorCode.DATASET_MISCONFIGURED);
        }
    }

    private static ApiDescriptor rangeApi(ApiDescriptor single, BatchDownloadDescriptor range) {
        return new ApiDescriptor(single.apiName(), single.displayName(), single.category(),
                QueryMode.date_range, range.parameters());
    }

    private static BatchDownloadDescriptor unsupported(String reason) {
        return new BatchDownloadDescriptor(List.of(), null, null, null, null, null, false,
                BatchDownloadDescriptor.Availability.UNSUPPORTED, reason, "unsupported-v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.UNKNOWN, null, null));
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Download task service requires its own transaction");
    }

    private record Current(
            DataSourcePlugin plugin,
            ApiDescriptor api,
            DatasetDefinition dataset,
            DownloadMode mode,
            BatchDownloadDescriptor range) {}

    static final class TaskException extends TensorException {
        TaskException(ErrorCode code) {
            super(code, new DownloadTaskRepository.StoredError(code).message());
        }
    }
}
