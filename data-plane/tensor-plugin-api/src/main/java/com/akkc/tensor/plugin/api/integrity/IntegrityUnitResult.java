package com.akkc.tensor.plugin.api.integrity;

import java.time.Instant;
import java.util.*;

public record IntegrityUnitResult(IntegrityScope scope, IntegrityDescriptor descriptor,
        String definitionHash, IntegrityDateRange publishedRange, IntegrityUnitStatus unitStatus,
        IntegrityStatus coverageStatus, IntegrityStatus keyStatus, IntegrityStatus fieldStatus,
        IntegrityStatistics statistics, List<IntegrityRuleResult> ruleResults,
        List<IntegrityEvidence> evidence, Instant finishedAt, boolean incomplete, boolean issuesComplete, String reasonCode, String message) {
    public IntegrityUnitResult {
        Objects.requireNonNull(scope, "scope");
        IntegrityValues.text(reasonCode);
        IntegrityValues.text(message);
        // Missing descriptor is a planned UNKNOWN/RULE_NOT_IMPLEMENTED unit.
        if (descriptor != null && !descriptor.datasetKey().equals(scope.datasetKey()))
            throw new IllegalArgumentException("Scope and descriptor differ");
        if (definitionHash == null || !definitionHash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid definition hash");
        Objects.requireNonNull(unitStatus, "unitStatus");
        Objects.requireNonNull(coverageStatus, "coverageStatus");
        Objects.requireNonNull(keyStatus, "keyStatus");
        Objects.requireNonNull(fieldStatus, "fieldStatus");
        Objects.requireNonNull(statistics, "statistics");
        ruleResults = List.copyOf(ruleResults);
        evidence = List.copyOf(evidence);
        if (publishedRange != null && !scope.range().contains(publishedRange))
            throw new IllegalArgumentException("Published range exceeds original scope");
        if ((incomplete || unitStatus != IntegrityUnitStatus.COMPLETED)
                && statistics.expectedCount() != null)
            throw new IllegalArgumentException("Incomplete result cannot publish whole-range coverage");
        if (unitStatus == IntegrityUnitStatus.ERROR && !incomplete)
            throw new IllegalArgumentException("Execution error must be incomplete");
        if (!issuesComplete && !incomplete) throw new IllegalArgumentException("Truncated issues must be incomplete");
    }
    public IntegrityStatus overallStatus() {
        var statuses = new ArrayList<>(List.of(coverageStatus, keyStatus, fieldStatus));
        ruleResults.forEach(r -> statuses.add(r.status()));
        if (incomplete || unitStatus != IntegrityUnitStatus.COMPLETED) statuses.add(IntegrityStatus.UNKNOWN);
        return IntegrityStatus.aggregate(statuses);
    }
}
