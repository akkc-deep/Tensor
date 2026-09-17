package com.akkc.tensor.plugin.api.integrity;

import java.util.*;

/**
 * Full business keys, never display JSON or physical fingerprints. Lazy producers must be
 * repeatable and stable within the snapshot. Core validates each key and charges its budget.
 */
public record IntegrityExpectedKeys(IntegrityScope scope, Basis basis,
        Iterable<Map<String, Object>> keys, List<IntegrityEvidence> evidence) {
    public enum Basis { PROVEN, UNCONFIRMED }
    public IntegrityExpectedKeys {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(keys, "keys");
        evidence = List.copyOf(evidence);
        if (evidence.isEmpty()) throw new IllegalArgumentException("Expected keys require evidence");
        if (basis == Basis.PROVEN && evidence.stream().noneMatch(e -> e.range().contains(scope.range())))
            throw new IllegalArgumentException("PROVEN evidence must cover the whole scope");
        if (keys instanceof Collection<Map<String, Object>> collection) {
            keys = collection.stream().map(IntegrityValues::values).toList();
        } else {
            var source = keys;
            keys = () -> new Iterator<>() {
                private final Iterator<Map<String, Object>> iterator = source.iterator();
                public boolean hasNext() { return iterator.hasNext(); }
                public Map<String, Object> next() { return IntegrityValues.values(iterator.next()); }
            };
        }
    }
}
