package com.akkc.tensor.web.dto;

import com.akkc.tensor.plugin.api.error.ErrorCode;

import java.util.List;
import java.util.Objects;

public record ApiErrorResponse(
        String requestId,
        ErrorCode code,
        String message,
        boolean retryable,
        List<FieldErrorResponse> fieldErrors,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        DownloadResponse downloadResult) {
    public ApiErrorResponse(String requestId, ErrorCode code, String message, boolean retryable, List<FieldErrorResponse> fieldErrors) {
        this(requestId, code, message, retryable, fieldErrors, null);
    }
    public ApiErrorResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(fieldErrors, "fieldErrors");
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (retryable != code.retryable()) {
            throw new IllegalArgumentException("retryable must match code");
        }
        fieldErrors = List.copyOf(fieldErrors);
    }
}
