package com.akkc.tensor.plugin.api.error;

import java.util.Objects;

public final class SourceException extends TensorException {
    public SourceException(ErrorCode code, String message) {
        super(requireSourceCode(code), message);
    }

    private static ErrorCode requireSourceCode(ErrorCode code) {
        Objects.requireNonNull(code, "code");
        if (code != ErrorCode.SOURCE_AUTH_FAILED
                && code != ErrorCode.SOURCE_PERMISSION_DENIED
                && code != ErrorCode.SOURCE_RATE_LIMITED
                && code != ErrorCode.SOURCE_UNAVAILABLE
                && code != ErrorCode.SOURCE_NETWORK_ERROR
                && code != ErrorCode.SOURCE_TIMEOUT
                && code != ErrorCode.SOURCE_PAYLOAD_INVALID) {
            throw new IllegalArgumentException("code must identify a source failure");
        }
        return code;
    }
}
