package com.akkc.tensor.observability;

import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class TensorMetrics {
    private static final String PLUGIN_TAG = "plugin";
    private static final String API_TAG = "api";
    private static final String OUTCOME_TAG = "outcome";
    private static final String KIND_TAG = "kind";
    private static final String SOURCE_KIND = "source";
    private static final String INSERTED_KIND = "inserted";
    private static final String UPDATED_KIND = "updated";

    private static final String DOWNLOAD_TOTAL = "tensor_download_total";
    private static final String DOWNLOAD_DURATION =
            "tensor_download_duration_seconds";
    private static final String DOWNLOAD_ROWS = "tensor_download_rows_total";
    private static final String QUERY_TOTAL = "tensor_query_total";
    private static final String QUERY_DURATION =
            "tensor_query_duration_seconds";

    private final MeterRegistry registry;
    private final Set<DatasetKey> supported;

    public TensorMetrics(MeterRegistry registry, PluginRegistry plugins) {
        this.registry = Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(plugins, "plugins");
        supported = plugins.descriptors().stream()
                .flatMap(descriptor -> descriptor.datasets().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean supports(DatasetKey key) {
        return supported.contains(Objects.requireNonNull(key, "key"));
    }

    public void recordDownload(
            DatasetKey key,
            Outcome outcome,
            Duration duration,
            long sourceRows,
            long insertedRows,
            long updatedRows) {
        requireDuration(duration);
        requireRows(sourceRows, insertedRows, updatedRows);
        if (!supports(key)) {
            return;
        }
        counter(DOWNLOAD_TOTAL, key, OUTCOME_TAG, outcome.value()).increment();
        timer(DOWNLOAD_DURATION, key, outcome).record(duration);
        if (outcome != Outcome.FAILURE) {
            counter(DOWNLOAD_ROWS, key, KIND_TAG, SOURCE_KIND).increment(sourceRows);
            counter(DOWNLOAD_ROWS, key, KIND_TAG, INSERTED_KIND).increment(insertedRows);
            counter(DOWNLOAD_ROWS, key, KIND_TAG, UPDATED_KIND).increment(updatedRows);
        }
    }

    public void recordQuery(
            DatasetKey key, Outcome outcome, Duration duration) {
        requireDuration(duration);
        if (outcome == Outcome.EMPTY) {
            throw new IllegalArgumentException("Query outcome must not be empty");
        }
        if (!supports(key)) {
            return;
        }
        counter(QUERY_TOTAL, key, OUTCOME_TAG, outcome.value()).increment();
        timer(QUERY_DURATION, key, outcome).record(duration);
    }

    private Counter counter(
            String name, DatasetKey key, String extraName, String extraValue) {
        return Counter.builder(name)
                .tags(PLUGIN_TAG, key.pluginId().value(),
                        API_TAG, key.apiName().value(), extraName, extraValue)
                .register(registry);
    }

    private Timer timer(String name, DatasetKey key, Outcome outcome) {
        return Timer.builder(name)
                .tags(PLUGIN_TAG, key.pluginId().value(),
                        API_TAG, key.apiName().value(),
                        OUTCOME_TAG, outcome.value())
                .register(registry);
    }

    private static void requireDuration(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    private static void requireRows(long source, long inserted, long updated) {
        if (source < 0 || inserted < 0 || updated < 0) {
            throw new IllegalArgumentException("row counts must not be negative");
        }
    }

    public enum Outcome {
        SUCCESS("success"),
        EMPTY("empty"),
        FAILURE("failure");

        private final String value;

        Outcome(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }
}
