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
    INTERNAL_ERROR(false),
    SOURCE_REQUEST_UNCONFIRMED(false),
    CALENDAR_UNCONFIRMED(true),
    SOURCE_TRUNCATED(false),
    SOURCE_COMPLETENESS_UNCONFIRMED(false),
    DATA_CONFLICT(false),
    RETRY_TASK_NOT_FOUND(false),
    DOWNLOAD_BUSY(true),
    RETRY_TASK_INVALID(false),
    TASK_RECORD_SAVE_UNCONFIRMED(false),
    COMMIT_UNCONFIRMED(false);

    private final boolean retryable;

    ErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
