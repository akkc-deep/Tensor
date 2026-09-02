package com.akkc.tensor.core.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public record ValidatedParameters(Map<String, Object> values) {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public ValidatedParameters {
        Objects.requireNonNull(values, "values");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            Object key = Objects.requireNonNull(entry.getKey(), "key");
            Object value = Objects.requireNonNull(entry.getValue(), "value");
            if (!(key instanceof String stringKey) || !KEY_PATTERN.matcher(stringKey).matches()) {
                throw new IllegalArgumentException("Invalid parameter key");
            }
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Parameter values must be strings");
            }
            snapshot.put(stringKey, value);
        }
        values = Collections.unmodifiableMap(snapshot);
    }
}
