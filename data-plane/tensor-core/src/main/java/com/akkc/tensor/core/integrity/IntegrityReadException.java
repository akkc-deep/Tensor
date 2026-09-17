package com.akkc.tensor.core.integrity;

import java.util.Objects;

public final class IntegrityReadException extends RuntimeException {
    public static final String INVALID_READ_REQUEST = "INVALID_READ_REQUEST";
    public static final String SCAN_LIMIT_EXCEEDED = "SCAN_LIMIT_EXCEEDED";
    public static final String UNIT_TIME_BUDGET_EXHAUSTED = "UNIT_TIME_BUDGET_EXHAUSTED";
    public static final String READ_FAILED = "READ_FAILED";
    public static final String READ_SESSION_CLOSED = "READ_SESSION_CLOSED";

    private final String reasonCode;

    public IntegrityReadException(String reasonCode, String message) {
        super(message);
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    }

    public String reasonCode() {
        return reasonCode;
    }
}
