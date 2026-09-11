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
    TASK_NOT_FOUND(false),
    SUBMISSION_CONFLICT(false),
    TASK_STATE_CONFLICT(false),
    TASK_DEFINITION_CHANGED(false),
    BATCH_DOWNLOAD_UNAVAILABLE(false),
    TASK_QUEUE_FULL(true),
    BATCH_COMPLETENESS_UNCONFIRMED(false),
    SOURCE_RANGE_MISMATCH(false),
    TASK_LIMIT_EXCEEDED(false),
    EXECUTION_INTERRUPTED(false);

    private final boolean retryable;

    ErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
