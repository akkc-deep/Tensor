package com.akkc.tensor.observability;

import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.core.query.QueryCriteria;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OperationLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogger.class);
    private static final Pattern SENSITIVE_NAME = Pattern.compile(
            "token|authorization|cookie|password|credential",
            Pattern.CASE_INSENSITIVE);

    private final Map<DatasetKey, List<String>> parameterNames;
    private final TensorMetrics metrics;

    public OperationLogger(PluginRegistry plugins, TensorMetrics metrics) {
        Objects.requireNonNull(plugins, "plugins");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        LinkedHashMap<DatasetKey, List<String>> names = new LinkedHashMap<>();
        plugins.descriptors().forEach(descriptor -> descriptor.apis().forEach(api -> {
            DatasetKey key = DatasetKey.of(descriptor.pluginId(), api.apiName());
            if (descriptor.datasets().contains(key)) {
                names.putIfAbsent(key, api.sourceParameters().stream()
                        .map(ParameterDescriptor::name)
                        .toList());
            }
        }));
        parameterNames = Collections.unmodifiableMap(names);
    }

    public void recordQuerySuccess(
            RequestId requestId,
            DatasetKey key,
            QueryCriteria criteria,
            DatasetPage result,
            Duration duration) {
        try {
            if (!metrics.supports(key)) {
                return;
            }
            List<String> filters = filterNames(criteria);
            recordQueryMetrics(key, duration);
            LOGGER.info(
                    "tensor.operation.completed requestId={} operation=query pluginId={} apiName={} filterNames={} page={} pageSize={} resultCount={} totalElements={} durationMs={} outcome=success failureStage=none errorCode=none",
                    requestId.value(), key.pluginId().value(), key.apiName().value(), filters,
                    result.page(), result.pageSize(), result.items().size(),
                    result.totalElements(), duration.toMillis());
        } catch (RuntimeException ignored) {
            observationFailed("query");
        }
    }

    public void recordDownloadSuccess(
            RequestId requestId,
            DatasetKey key,
            Map<String, Object> parameters,
            DownloadResult result,
            Duration duration) {
        try {
            if (!metrics.supports(key)) {
                return;
            }
            List<String> summary = parameterNames.getOrDefault(key, List.of()).stream()
                    .filter(parameters::containsKey)
                    .filter(name -> !SENSITIVE_NAME.matcher(name).find())
                    .toList();
            TensorMetrics.Outcome outcome = result.outcome() == DownloadOutcome.EMPTY
                    ? TensorMetrics.Outcome.EMPTY
                    : TensorMetrics.Outcome.SUCCESS;
            recordDownloadMetrics(key, outcome, duration, result);
            LOGGER.info(
                    "tensor.operation.completed requestId={} operation=download pluginId={} apiName={} paramSummary={} sourceRowCount={} insertedRows={} updatedRows={} durationMs={} outcome={} failureStage=none errorCode=none",
                    requestId.value(), key.pluginId().value(), key.apiName().value(), summary,
                    result.sourceRowCount(), result.insertedRows(), result.updatedRows(),
                    duration.toMillis(), outcome.value());
        } catch (RuntimeException ignored) {
            observationFailed("download");
        }
    }

    public void recordDownloadExecution(com.akkc.tensor.core.download.DownloadExecutionResult result, Duration duration) {
        try {
            var key = DatasetKey.of(result.pluginId(), result.apiName());
            var outcome = switch (result.outcome()) {
                case SUCCESS -> TensorMetrics.Outcome.SUCCESS;
                case EMPTY, NO_OPEN_DATES -> TensorMetrics.Outcome.EMPTY;
                case PARTIAL, FAILED, UNCONFIRMED -> TensorMetrics.Outcome.FAILURE;
            };
            try {
                metrics.recordDownload(key, outcome, duration, result.sourceRowCount(), result.insertedRows(), result.updatedRows());
            } catch (RuntimeException ignored) { observationFailed("download"); }
            LOGGER.info("tensor.operation.completed requestId={} taskId={} operation=download pluginId={} apiName={} outcome={} completedUnits={} failedUnits={} notStartedUnits={} skippedClosedDates={} sourceRowCount={} insertedRows={} updatedRows={} remainingFailedUnits={} failureRecordStatus={} durationMs={}",
                    result.requestId().value(), result.taskId(), result.pluginId().value(), result.apiName().value(), result.outcome(),
                    result.completedUnits(), result.failedUnits(), result.notStartedUnits(), result.skippedClosedDates(),
                    result.sourceRowCount(), result.insertedRows(), result.updatedRows(), result.remainingFailedUnits(), result.failureRecordStatus(), duration.toMillis());
        } catch (RuntimeException ignored) { observationFailed("download"); }
    }

    private void recordDownloadMetrics(
            DatasetKey key,
            TensorMetrics.Outcome outcome,
            Duration duration,
            DownloadResult result) {
        try {
            metrics.recordDownload(key, outcome, duration,
                    result.sourceRowCount(), result.insertedRows(), result.updatedRows());
        } catch (RuntimeException ignored) {
            observationFailed("download");
        }
    }

    private void recordQueryMetrics(DatasetKey key, Duration duration) {
        try {
            metrics.recordQuery(key, TensorMetrics.Outcome.SUCCESS, duration);
        } catch (RuntimeException ignored) {
            observationFailed("query");
        }
    }

    private static List<String> filterNames(QueryCriteria criteria) {
        ArrayList<String> names = new ArrayList<>(3);
        if (criteria.tsCode() != null) {
            names.add("ts_code");
        }
        if (criteria.tradeDateFrom() != null || criteria.tradeDateTo() != null) {
            names.add("trade_date");
        }
        if (criteria.annDateFrom() != null || criteria.annDateTo() != null) {
            names.add("ann_date");
        }
        return List.copyOf(names);
    }

    private static void observationFailed(String operation) {
        try {
            LOGGER.warn("tensor.observation.failed operation={}", operation);
        } catch (RuntimeException ignored) {
            // Observability must never affect a completed business operation.
        }
    }
}
