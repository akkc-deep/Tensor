package com.akkc.tensor.plugin.api.error;

public enum ErrorCode {
    PARAM_REQUIRED(false),
    PARAM_INVALID(false),
    PLUGIN_DISABLED(false),
    DATASET_MISCONFIGURED(false),
    SOURCE_AUTH_FAILED(false),
    SOURCE_PERMISSION_DENIED(false),
    SOURCE_RATE_LIMITED(true),
    SOURCE_UNAVAILABLE(true),
    SOURCE_NETWORK_ERROR(true),
    SOURCE_TIMEOUT(true),
    SOURCE_PAYLOAD_INVALID(true),
    ADAPTER_FIELD_MISSING(false),
    ADAPTER_TYPE_INVALID(false),
    PERSISTENCE_FAILED(true),
    QUERY_FAILED(true),
    INTERNAL_ERROR(false);

    private final boolean retryable;

    ErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
