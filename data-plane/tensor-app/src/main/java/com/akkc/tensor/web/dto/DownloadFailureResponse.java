package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.RecoveryUnitProcessor;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.ErrorCode;

public record DownloadFailureResponse(RecoverySelector.TargetType targetType, String targetValue,
        RecoverySelector.TimeType timeType, String timeValue, ErrorCode errorCode, String errorMessage) {
    public static DownloadFailureResponse from(RecoveryUnitProcessor.Failure failure) {
        var scope = failure.selector();
        return new DownloadFailureResponse(scope.targetType(), scope.targetValue(), scope.timeType(), scope.timeValue(), failure.errorCode(), safeReason(failure.errorCode()));
    }
    public record ScopeResponse(RecoverySelector.TargetType targetType, String targetValue,
            RecoverySelector.TimeType timeType, String timeValue) {
        public static ScopeResponse from(RecoverySelector scope) {
            return new ScopeResponse(scope.targetType(), scope.targetValue(), scope.timeType(), scope.timeValue());
        }
    }
    public static String safeReason(ErrorCode code) {
        return switch (code) {
            case SOURCE_AUTH_FAILED -> "Source authentication failed";
            case SOURCE_PERMISSION_DENIED -> "Source permission denied";
            case SOURCE_RATE_LIMITED -> "Source rate limit exceeded";
            case SOURCE_UNAVAILABLE -> "Source is unavailable";
            case SOURCE_NETWORK_ERROR -> "Source network request failed";
            case SOURCE_TIMEOUT -> "Source request timed out";
            case SOURCE_PAYLOAD_INVALID -> "Source returned an invalid payload";
            case SOURCE_TRUNCATED -> "Source response is truncated";
            case SOURCE_COMPLETENESS_UNCONFIRMED -> "Source response completeness is unconfirmed";
            case ADAPTER_FIELD_MISSING -> "Source data is missing a required field";
            case ADAPTER_TYPE_INVALID -> "Source data contains an invalid value";
            case DATA_CONFLICT -> "Source data contains conflicting values";
            case PERSISTENCE_FAILED -> "Persistence failed";
            default -> throw new IllegalArgumentException("Invalid failure code");
        };
    }
}
