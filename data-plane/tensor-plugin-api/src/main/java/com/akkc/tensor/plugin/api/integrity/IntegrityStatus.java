package com.akkc.tensor.plugin.api.integrity;

import java.util.Collection;
import java.util.Objects;

public enum IntegrityStatus {
    NOT_APPLICABLE, PASS, WARN, UNKNOWN, FAIL;

    public static IntegrityStatus aggregate(Collection<IntegrityStatus> statuses) {
        if (statuses.isEmpty()) return UNKNOWN;
        IntegrityStatus result = NOT_APPLICABLE;
        for (var status : statuses) {
            Objects.requireNonNull(status, "status");
            if (status.ordinal() > result.ordinal()) result = status;
        }
        return result;
    }
}
