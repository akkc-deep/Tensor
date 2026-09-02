package com.akkc.tensor.core.persistence;

import java.util.List;
import java.util.Objects;

public record BusinessKey(List<Object> values) {
    public BusinessKey {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("values must not contain null");
        }
        values = List.copyOf(values);
    }
}
