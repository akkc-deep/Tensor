package com.akkc.tensor.observability;

import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.web.RequestIdFilter;
import com.akkc.tensor.web.dto.DownloadResponse;
import com.akkc.tensor.web.dto.PageResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

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
                names.putIfAbsent(key, api.parameters().stream()
                        .map(ParameterDescriptor::name)
                        .toList());
            }
        }));
        parameterNames = Collections.unmodifiableMap(names);
    }

    public DownloadResponse download(
            DatasetKey key,
            Map<String, Object> parameters,
            Supplier<DownloadResponse> operation) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(operation, "operation");
        if (!metrics.supports(key)) {
            return operation.get();
        }
        String requestId = requestId();
        List<String> summary = parameterNames.getOrDefault(key, List.of()).stream()
                .filter(parameters::containsKey)
                .filter(name -> !SENSITIVE_NAME.matcher(name).find())
                .toList();
        long started = System.nanoTime();
        try {
            DownloadResponse response = Objects.requireNonNull(
                    operation.get(), "download response");
            Duration duration = elapsed(started);
            TensorMetrics.Outcome outcome = response.outcome() == DownloadOutcome.EMPTY
                    ? TensorMetrics.Outcome.EMPTY
                    : TensorMetrics.Outcome.SUCCESS;
            recordDownloadMetrics(key, outcome, duration,
                    response.sourceRowCount(), response.insertedRows(), response.updatedRows());
            LOGGER.info(
                    "tensor.operation.completed requestId={} operation=download pluginId={} apiName={} paramSummary={} sourceRowCount={} insertedRows={} updatedRows={} durationMs={} outcome={} failureStage=none errorCode=none",
                    requestId, key.pluginId().value(), key.apiName().value(), summary,
                    response.sourceRowCount(), response.insertedRows(), response.updatedRows(),
                    duration.toMillis(), outcome.value());
            return response;
        } catch (RuntimeException failure) {
            Duration duration = elapsed(started);
            Failure classified = downloadFailure(failure);
            recordDownloadMetrics(key, TensorMetrics.Outcome.FAILURE, duration, 0, 0, 0);
            LOGGER.info(
                    "tensor.operation.completed requestId={} operation=download pluginId={} apiName={} paramSummary={} sourceRowCount=unavailable insertedRows=unavailable updatedRows=unavailable durationMs={} outcome=failure failureStage={} errorCode={}",
                    requestId, key.pluginId().value(), key.apiName().value(), summary,
                    duration.toMillis(), classified.stage(), classified.code());
            throw failure;
        }
    }

    public PageResponse query(
            DatasetKey key,
            List<String> filterNames,
            int requestedPage,
            int requestedPageSize,
            Supplier<PageResponse> operation) {
        Objects.requireNonNull(key, "key");
        filterNames = List.copyOf(Objects.requireNonNull(filterNames, "filterNames"));
        Objects.requireNonNull(operation, "operation");
        if (!metrics.supports(key)) {
            return operation.get();
        }
        String requestId = requestId();
        long started = System.nanoTime();
        try {
            PageResponse response = Objects.requireNonNull(
                    operation.get(), "query response");
            Duration duration = elapsed(started);
            recordQueryMetrics(key, TensorMetrics.Outcome.SUCCESS, duration);
            LOGGER.info(
                    "tensor.operation.completed requestId={} operation=query pluginId={} apiName={} filterNames={} page={} pageSize={} resultCount={} totalElements={} durationMs={} outcome=success failureStage=none errorCode=none",
                    requestId, key.pluginId().value(), key.apiName().value(), filterNames,
                    response.page(), response.pageSize(), response.items().size(),
                    response.totalElements(), duration.toMillis());
            return response;
        } catch (RuntimeException failure) {
            Duration duration = elapsed(started);
            Failure classified = domainFailure(failure, ErrorCode.QUERY_FAILED, "query");
            recordQueryMetrics(key, TensorMetrics.Outcome.FAILURE, duration);
            LOGGER.info(
                    "tensor.operation.completed requestId={} operation=query pluginId={} apiName={} filterNames={} page={} pageSize={} resultCount=unavailable totalElements=unavailable durationMs={} outcome=failure failureStage={} errorCode={}",
                    requestId, key.pluginId().value(), key.apiName().value(), filterNames,
                    requestedPage, requestedPageSize, duration.toMillis(),
                    classified.stage(), classified.code());
            throw failure;
        }
    }

    private void recordDownloadMetrics(
            DatasetKey key,
            TensorMetrics.Outcome outcome,
            Duration duration,
            long sourceRows,
            long insertedRows,
            long updatedRows) {
        try {
            metrics.recordDownload(
                    key, outcome, duration, sourceRows, insertedRows, updatedRows);
        } catch (RuntimeException ignored) {
            LOGGER.warn("tensor.observation.failed operation=download");
        }
    }

    private void recordQueryMetrics(
            DatasetKey key,
            TensorMetrics.Outcome outcome,
            Duration duration) {
        try {
            metrics.recordQuery(key, outcome, duration);
        } catch (RuntimeException ignored) {
            LOGGER.warn("tensor.observation.failed operation=query");
        }
    }

    private static Failure downloadFailure(RuntimeException failure) {
        if (failure instanceof DataAccessException
                || failure instanceof TransactionException) {
            return new Failure(ErrorCode.PERSISTENCE_FAILED, "persistence");
        }
        return domainFailure(failure, ErrorCode.INTERNAL_ERROR, "internal");
    }

    private static Failure domainFailure(
            RuntimeException failure, ErrorCode fallback, String fallbackStage) {
        if (!(failure instanceof TensorException tensor)) {
            return new Failure(fallback, fallbackStage);
        }
        ErrorCode code = tensor.code();
        return new Failure(code, switch (code) {
            case PARAM_REQUIRED, PARAM_INVALID -> "parameter";
            case PLUGIN_DISABLED, DATASET_MISCONFIGURED -> "registration";
            case SOURCE_AUTH_FAILED, SOURCE_PERMISSION_DENIED, SOURCE_RATE_LIMITED,
                    SOURCE_UNAVAILABLE, SOURCE_NETWORK_ERROR, SOURCE_TIMEOUT,
                    SOURCE_PAYLOAD_INVALID -> "source";
            case ADAPTER_FIELD_MISSING, ADAPTER_TYPE_INVALID -> "adapter";
            case PERSISTENCE_FAILED -> "persistence";
            case QUERY_FAILED -> "query";
            case INTERNAL_ERROR -> "internal";
        });
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - started));
    }

    private static String requestId() {
        String value = MDC.get(RequestIdFilter.MDC_KEY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        return value;
    }

    private record Failure(ErrorCode code, String stage) {
    }
}
