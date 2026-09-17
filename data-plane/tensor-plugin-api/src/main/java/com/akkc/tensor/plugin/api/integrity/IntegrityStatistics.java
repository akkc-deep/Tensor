package com.akkc.tensor.plugin.api.integrity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Null means not computed/proven, never zero. Coverage is for display, not a verdict. */
public record IntegrityStatistics(Long actualCount, Long expectedCount, Long matchedCount,
        Long missingCount, Long suspectedMissingCount, Long extraCount, Long requiredFieldIssueCount) {
    public IntegrityStatistics {
        for (Long count : new Long[]{actualCount, expectedCount, matchedCount, missingCount,
                suspectedMissingCount, extraCount, requiredFieldIssueCount})
            if (count != null && count < 0) throw new IllegalArgumentException("Negative count");
        if (matchedCount != null && (actualCount != null && matchedCount > actualCount
                || expectedCount != null && matchedCount > expectedCount))
            throw new IllegalArgumentException("Matched count exceeds total");
        if (expectedCount != null && matchedCount != null && missingCount != null
                && expectedCount - matchedCount != missingCount)
            throw new IllegalArgumentException("Missing count does not match exact totals");
        if (actualCount != null && matchedCount != null && extraCount != null
                && actualCount - matchedCount != extraCount)
            throw new IllegalArgumentException("Extra count does not match exact totals");
    }
    public BigDecimal coverageRate() {
        return expectedCount == null || expectedCount == 0 || matchedCount == null ? null
                : BigDecimal.valueOf(matchedCount).divide(BigDecimal.valueOf(expectedCount), 6, RoundingMode.HALF_UP);
    }
}
