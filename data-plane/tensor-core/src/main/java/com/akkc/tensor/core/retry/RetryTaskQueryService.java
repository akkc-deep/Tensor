package com.akkc.tensor.core.retry;

import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.download.DownloadExecutionSlot;
import com.akkc.tensor.core.download.DownloadParameterConverter;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import static com.akkc.tensor.plugin.api.download.DownloadPolicy.*;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType.STOCK;
import static com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType.*;

/** Projects committed retry records without acquiring an execution lease or invoking a source. */
public final class RetryTaskQueryService {
    private static final Set<String> DATES = Set.of("start_date", "end_date");
    private static final Set<String> PRIVATE = Set.of("offset", "limit", "cursor", "page", "page_size", "trade_date", "ann_date", "month");
    private static final Pattern SENSITIVE = Pattern.compile("token|authorization|cookie|password|credential", Pattern.CASE_INSENSITIVE);
    private final RetryTaskStorageService storage;
    private final PluginRegistry plugins;
    private final AdapterRegistry adapters;
    private final ParameterValidator validator;
    private final DownloadParameterConverter converter;
    private final DownloadExecutionSlot slot;

    public RetryTaskQueryService(RetryTaskStorageService storage, PluginRegistry plugins,
            AdapterRegistry adapters, ParameterValidator validator, DownloadExecutionSlot slot) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.adapters = Objects.requireNonNull(adapters, "adapters");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.converter = new DownloadParameterConverter(validator);
        this.slot = Objects.requireNonNull(slot, "slot");
    }

    public TaskPage list(RetryTaskRepository.Criteria criteria) {
        RetryTaskRepository.Page page;
        try { page = storage.list(criteria); }
        catch (RuntimeException failure) { throw queryFailed(); }
        var descriptors = plugins.descriptors();
        var items = page.items().stream().map(task -> summary(task, metadata(task, descriptors))).toList();
        return new TaskPage(items, page.page(), page.pageSize(), page.totalElements(), page.totalPages());
    }

    public TaskDetail get(UUID taskId) {
        Optional<RetryTaskRepository.Task> found;
        try { found = storage.find(taskId); }
        catch (RuntimeException failure) { throw queryFailed(); }
        var task = found.orElseThrow(() -> new QueryException(ErrorCode.RETRY_TASK_NOT_FOUND, "Retry task was not found"));
        var metadata = metadata(task, plugins.descriptors());
        var summary = summary(task, metadata);
        var snapshot = slot.snapshot();
        var blocker = blocker(task, metadata, summary.originalDateRangeStatus(), snapshot);
        return new TaskDetail(summary, safeParams(task.header().taskParams(), metadata.api()),
                task.items().stream().map(item -> new TaskItem(item.key().selector(), item.errorCode(), item.updatedAt())).toList(),
                snapshot.busy() && taskId.equals(snapshot.retryTaskId()), blocker == null, blocker);
    }

    private TaskSummary summary(RetryTaskRepository.Task task, Metadata metadata) {
        if (task.items().isEmpty()) throw queryFailed();
        var header = task.header();
        var range = originalRange(header.taskParams(), metadata.api());
        return new TaskSummary(header.taskId(), header.datasetKey().pluginId(), header.datasetKey().apiName(),
                metadata.plugin() == null ? null : metadata.plugin().displayName(),
                metadata.api() == null ? null : metadata.api().displayName(), range.range(), range.status(),
                task.items().size(), task.items().stream().map(item -> item.key().selector()).toList(), header.createdAt(), header.updatedAt());
    }

    private static Metadata metadata(RetryTaskRepository.Task task, List<PluginDescriptor> descriptors) {
        var key = task.header().datasetKey();
        var matching = descriptors.stream().filter(plugin -> plugin.pluginId().equals(key.pluginId())).toList();
        if (matching.size() != 1) return new Metadata(null, null);
        var plugin = matching.getFirst();
        var apis = plugin.apis().stream().filter(api -> api.apiName().equals(key.apiName())).toList();
        return new Metadata(plugin, apis.size() == 1 ? apis.getFirst() : null);
    }

    private RangeState originalRange(Map<String, Object> params, ApiDescriptor api) {
        if (api == null) return new RangeState(null, OriginalRangeStatus.UNCONFIRMED);
        boolean start = params.containsKey("start_date"), end = params.containsKey("end_date");
        if (!start && !end) return new RangeState(null, api.downloadPolicy().mode() == Mode.ORIGINAL_PARAMS
                ? OriginalRangeStatus.NOT_APPLICABLE : OriginalRangeStatus.NOT_RECORDED);
        if (!start || !end || api.downloadPolicy().mode() == Mode.ORIGINAL_PARAMS)
            return new RangeState(null, OriginalRangeStatus.UNCONFIRMED);
        try {
            var dates = new LinkedHashMap<String, Object>();
            DATES.forEach(key -> dates.put(key, params.get(key)));
            var values = validator.validate(api.parameters().stream().filter(p -> DATES.contains(p.name())).toList(), dates).values();
            String first = (String) values.get("start_date"), last = (String) values.get("end_date");
            new DownloadParameterConverter.OriginalDateRange(LocalDate.parse(first, DateTimeFormatter.BASIC_ISO_DATE), LocalDate.parse(last, DateTimeFormatter.BASIC_ISO_DATE));
            return new RangeState(new OriginalRange(first, last), OriginalRangeStatus.RECORDED);
        } catch (IllegalArgumentException | TensorException failure) {
            return new RangeState(null, OriginalRangeStatus.UNCONFIRMED);
        }
    }

    private Map<String, Object> safeParams(Map<String, Object> params, ApiDescriptor api) {
        var safe = new LinkedHashMap<String, Object>();
        for (var entry : params.entrySet()) {
            String name = entry.getKey();
            if (forbidden(name) || !(entry.getValue() instanceof String value)) continue;
            if (DATES.contains(name)) {
                if (value.matches("[0-9]{8}")) safe.put(name, value);
            } else if (api != null) {
                var descriptor = api.parameters().stream().filter(p -> p.name().equals(name)).findFirst();
                if (descriptor.isPresent()) {
                    try { validator.validate(List.of(descriptor.get()), Map.of(name, value)); safe.put(name, value); }
                    catch (IllegalArgumentException | TensorException ignored) { /* Unproven display values are omitted. */ }
                }
            }
        }
        return Map.copyOf(safe);
    }

    private ExecutionBlocker blocker(RetryTaskRepository.Task task, Metadata metadata,
            OriginalRangeStatus range, DownloadExecutionSlot.Snapshot snapshot) {
        if (snapshot.busy()) return blocked(ErrorCode.DOWNLOAD_BUSY, "A download is already running");
        var plugin = metadata.plugin();
        if (plugin == null || !plugin.downloadAvailable()) return blocked(ErrorCode.PLUGIN_DISABLED, "Plugin is unavailable");
        var key = task.header().datasetKey();
        var api = metadata.api();
        if (api == null || !plugin.datasets().contains(key)
                || !(adapters.find(key).orElse(null) instanceof GenericDatasetAdapter adapter) || !adapter.datasetKey().equals(key))
            return blocked(ErrorCode.DATASET_MISCONFIGURED, "Dataset metadata is unavailable");
        if (range == OriginalRangeStatus.UNCONFIRMED || !compatible(task, api))
            return blocked(ErrorCode.RETRY_TASK_INVALID, "Saved retry task is invalid");
        var policy = api.downloadPolicy();
        if (policy.requestEvidenceStatus() != RequestEvidenceStatus.DOCUMENTED_CANDIDATE || policy.sourceRequestMode() == null)
            return blocked(ErrorCode.SOURCE_REQUEST_UNCONFIRMED, "Source request conditions are unconfirmed");
        if (policy.mode() == Mode.TRADE_DATE_RANGE && policy.calendarEvidenceStatus() != CalendarEvidenceStatus.DOCUMENTED)
            return blocked(ErrorCode.CALENDAR_UNCONFIRMED, "Applicable calendars are unconfirmed");
        return null;
    }

    private boolean compatible(RetryTaskRepository.Task task, ApiDescriptor api) {
        var original = task.header().taskParams();
        if (original.entrySet().stream().anyMatch(e -> forbidden(e.getKey()) || !(e.getValue() instanceof String))) return false;
        var common = new LinkedHashMap<>(original);
        DATES.forEach(common::remove);
        var policy = api.downloadPolicy();
        try {
            for (var item : task.items()) {
                var selector = item.key().selector();
                var time = selector.timeType();
                if (policy.mode() == Mode.ORIGINAL_PARAMS ? time != NONE
                        : policy.mode() == Mode.MONTH_RANGE ? time != MONTH : time == NONE || time == MONTH) return false;
                boolean stock = selector.targetType() == STOCK;
                if (stock) {
                    var recovery = policy.recoveryPolicy();
                    if (common.containsKey("ts_code") || recovery.mode() != RecoveryPolicy.Mode.STOCK_TIME || !recovery.independentRecoveryVerified()
                            || api.sourceParameters().stream().noneMatch(p -> p.name().equals("ts_code") && p.type() == ParameterType.TS_CODE)
                            || time != recovery.unitTimeType() && !(recovery.unitTimeType() == RANGE && time == DATE)) return false;
                }
                validator.validate(api.parameters().stream().filter(p -> !DATES.contains(p.name()) && !(stock && p.name().equals("ts_code"))).toList(), common);
                if (policy.sourceRequestMode() != null && !switch (policy.sourceRequestMode()) {
                    case DATE -> time == DATE; case MONTH -> time == MONTH; case RANGE -> time == DATE || time == RANGE; case NONE -> time == NONE;
                }) return false;
                if (policy.requestEvidenceStatus() == RequestEvidenceStatus.DOCUMENTED_CANDIDATE) converter.mapRetry(api, original, selector);
            }
            return true;
        } catch (IllegalArgumentException failure) { return false; }
        catch (TensorException failure) {
            if (failure.code() == ErrorCode.SOURCE_REQUEST_UNCONFIRMED) return true;
            if (failure.code() == ErrorCode.RETRY_TASK_INVALID || failure.code() == ErrorCode.PARAM_INVALID || failure.code() == ErrorCode.PARAM_REQUIRED) return false;
            throw failure;
        }
    }

    private static boolean forbidden(String key) { return PRIVATE.contains(key) || SENSITIVE.matcher(key).find(); }
    private static ExecutionBlocker blocked(ErrorCode code, String message) { return new ExecutionBlocker(code, message); }
    private static TensorException queryFailed() { return new QueryException(ErrorCode.QUERY_FAILED, "Retry task query failed"); }
    private static final class QueryException extends TensorException { private QueryException(ErrorCode code, String message) { super(code, message); } }
    private record Metadata(PluginDescriptor plugin, ApiDescriptor api) {}
    private record RangeState(OriginalRange range, OriginalRangeStatus status) {}
    public enum OriginalRangeStatus { RECORDED, NOT_APPLICABLE, NOT_RECORDED, UNCONFIRMED }
    public record OriginalRange(String startDate, String endDate) {}
    public record ExecutionBlocker(ErrorCode code, String message) {}
    public record TaskItem(RecoverySelector selector, ErrorCode errorCode, Instant updatedAt) {}
    public record TaskSummary(UUID taskId, PluginId pluginId, ApiName apiName, String pluginDisplayName, String apiDisplayName,
            OriginalRange originalDateRange, OriginalRangeStatus originalDateRangeStatus, long failedItemCount,
            List<RecoverySelector> failedScopes, Instant createdAt, Instant updatedAt) {
        public TaskSummary { failedScopes = List.copyOf(failedScopes); }
    }
    public record TaskPage(List<TaskSummary> items, int page, int pageSize, long totalElements, long totalPages) {
        public TaskPage { items = List.copyOf(items); }
    }
    public record TaskDetail(TaskSummary summary, Map<String, Object> taskParams, List<TaskItem> items,
            boolean retrying, boolean canExecute, ExecutionBlocker executionBlocker) {
        public TaskDetail { taskParams = Map.copyOf(taskParams); items = List.copyOf(items); }
    }
}
