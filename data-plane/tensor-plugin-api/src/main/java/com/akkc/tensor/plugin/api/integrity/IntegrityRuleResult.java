package com.akkc.tensor.plugin.api.integrity;

import java.util.*;

public record IntegrityRuleResult(IntegrityRuleDescriptor descriptor, IntegrityStatus status,
        String reasonCode, String message, IntegrityStatistics statistics, List<IntegrityEvidence> evidence) {
    public IntegrityRuleResult {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(status, "status");
        IntegrityValues.text(reasonCode);
        IntegrityValues.text(message);
        Objects.requireNonNull(statistics, "statistics");
        evidence = List.copyOf(evidence);
        if (descriptor.dimension() != IntegrityRuleDescriptor.Dimension.COVERAGE
                && (statistics.expectedCount() != null || statistics.matchedCount() != null
                || statistics.missingCount() != null || statistics.suspectedMissingCount() != null
                || statistics.extraCount() != null))
            throw new IllegalArgumentException("Only coverage rules may publish coverage counts");
    }
}
