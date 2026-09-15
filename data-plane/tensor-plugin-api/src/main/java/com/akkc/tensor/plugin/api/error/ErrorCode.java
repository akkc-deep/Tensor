package com.akkc.tensor.plugin.api.error;

public enum ErrorCode {
    PARAM_REQUIRED("Required parameters are missing", false),
    PARAM_INVALID("Parameters are invalid", false),
    PLUGIN_DISABLED("Plugin is unavailable", false),
    DATASET_MISCONFIGURED("Dataset metadata is unavailable", false),
    SOURCE_AUTH_FAILED("Source authentication failed", false),
    SOURCE_PERMISSION_DENIED("Source permission denied", false),
    SOURCE_RATE_LIMITED("Source rate limit exceeded", true),
    SOURCE_UNAVAILABLE("Source is unavailable", true),
    SOURCE_NETWORK_ERROR("Source network request failed", true),
    SOURCE_TIMEOUT("Source request timed out", true),
    SOURCE_PAYLOAD_INVALID("Source returned an invalid payload", true),
    ADAPTER_FIELD_MISSING("Source data is missing a required field", false),
    ADAPTER_TYPE_INVALID("Source data contains an invalid value", false),
    PERSISTENCE_FAILED("Persistence failed", true),
    QUERY_FAILED("Query failed", true),
    INTERNAL_ERROR("Internal server error", false),
    TASK_NOT_FOUND("Download task was not found", false),
    SUBMISSION_CONFLICT("Submission ID belongs to a different request", false),
    TASK_STATE_CONFLICT("Download task state has changed", false),
    TASK_DEFINITION_CHANGED("Download task definition has changed", false),
    BATCH_DOWNLOAD_UNAVAILABLE("Batch download is unavailable", false),
    TASK_QUEUE_FULL("Download task queue is full", true),
    BATCH_COMPLETENESS_UNCONFIRMED("Batch completeness is unconfirmed", false),
    SOURCE_RANGE_MISMATCH("Source data is outside the requested range", false),
    TASK_LIMIT_EXCEEDED("Download task limit exceeded", false),
    EXECUTION_INTERRUPTED("Download task execution was interrupted", false);

    private final String message;
    private final boolean retryable;

    ErrorCode(String message, boolean retryable) {
        this.message = message;
        this.retryable = retryable;
    }

    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }
}
