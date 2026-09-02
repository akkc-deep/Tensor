package com.akkc.tensor.core.persistence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record WriteCounts(long insertedRows, long updatedRows) {
    public WriteCounts {
        if (insertedRows < 0 || updatedRows < 0) {
            throw new IllegalArgumentException("write counts must be non-negative");
        }
    }

    public static WriteCounts from(List<BusinessKey> keys, Set<BusinessKey> existingKeys) {
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(existingKeys, "existingKeys");
        if (keys.stream().anyMatch(Objects::isNull) || existingKeys.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("business keys must not contain null");
        }

        Set<BusinessKey> distinctKeys = new LinkedHashSet<>(keys);
        if (!distinctKeys.containsAll(existingKeys)) {
            throw new IllegalArgumentException("existingKeys must be a subset of keys");
        }
        long updatedRows = existingKeys.size();
        long insertedRows = distinctKeys.size() - updatedRows;
        if (Math.addExact(insertedRows, updatedRows) != distinctKeys.size()) {
            throw new IllegalStateException("Write count invariant violated");
        }
        return new WriteCounts(insertedRows, updatedRows);
    }
}
