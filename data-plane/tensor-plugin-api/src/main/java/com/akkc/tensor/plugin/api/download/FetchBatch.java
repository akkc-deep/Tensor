package com.akkc.tensor.plugin.api.download;

import java.util.Map;
import java.util.Objects;

public record FetchBatch(Map<String, Object> sourceParams, RecoveryPolicy recoveryPolicy) {
    public FetchBatch {
        sourceParams = copyParameters(sourceParams);
        Objects.requireNonNull(recoveryPolicy, "recoveryPolicy");
    }

    static Map<String, Object> copyParameters(Map<String, Object> values) {
        Objects.requireNonNull(values, "parameters");
        values.forEach((key, value) -> {
            Objects.requireNonNull(key, "parameter name");
            Objects.requireNonNull(value, "parameter value");
            if (!RecoveryPolicy.identifier(key) || !(value instanceof String)) {
                throw new IllegalArgumentException("Parameters must contain identifiers and string values");
            }
        });
        return Map.copyOf(values);
    }
}
