package com.akkc.tensor.plugin.api.integrity;

import java.time.Instant;
import java.util.Objects;

/** Explanatory source/summary only: never credentials, raw responses or stack traces. */
public record IntegrityEvidence(String source, String ruleVersion, IntegrityDateRange range,
        Instant readAt, String summary) {
    public IntegrityEvidence {
        IntegrityValues.text(source);
        IntegrityValues.text(ruleVersion);
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(readAt, "readAt");
        IntegrityValues.text(summary);
    }
}
