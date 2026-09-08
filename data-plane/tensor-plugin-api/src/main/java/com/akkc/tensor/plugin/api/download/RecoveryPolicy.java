package com.akkc.tensor.plugin.api.download;

import java.util.List;
import java.util.Objects;

public record RecoveryPolicy(Mode mode, String targetField, String timeField,
        RecoverySelector.TimeType unitTimeType, boolean independentRecoveryVerified, List<String> evidenceRefs) {
    public enum Mode { REQUEST, STOCK_TIME }

    public RecoveryPolicy {
        Objects.requireNonNull(mode, "mode");
        evidenceRefs = DownloadPolicy.copyEvidence(evidenceRefs);
        if (mode == Mode.REQUEST) {
            if (targetField != null || timeField != null || unitTimeType != null || independentRecoveryVerified) {
                throw new IllegalArgumentException("REQUEST recovery cannot declare independent mappings");
            }
        } else if (!identifier(targetField) || !identifier(timeField) || !independentRecoveryVerified
                || unitTimeType == null || unitTimeType == RecoverySelector.TimeType.NONE) {
            throw new IllegalArgumentException("STOCK_TIME recovery requires verified mappings");
        }
    }

    static boolean identifier(String value) {
        return value != null && value.matches("[a-z][a-z0-9_]{1,63}");
    }
}
