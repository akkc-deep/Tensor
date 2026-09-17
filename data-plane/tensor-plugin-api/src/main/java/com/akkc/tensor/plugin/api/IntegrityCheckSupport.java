package com.akkc.tensor.plugin.api;

import com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityRule;
import com.akkc.tensor.plugin.api.integrity.IntegrityReadRequest;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.integrity.IntegrityDateRange;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Optional local-only capability. Metadata lookup and rules must never call upstream services. */
public interface IntegrityCheckSupport extends DataSourcePlugin {
    String normalizeIntegritySymbol(String symbol);
    default void validateIntegrityRange(IntegrityDateRange range, Instant acceptedAt) {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
    }
    /** Pure local proposals; the core reader independently enforces every permit. */
    default List<IntegrityReadRequest> integrityReferenceReads(IntegrityScope scope) {
        Objects.requireNonNull(scope, "scope");
        return List.of();
    }
    Optional<IntegrityDescriptor> integrityDescriptor(ApiName apiName);
    List<IntegrityRule> integrityRules(ApiName apiName);
}
