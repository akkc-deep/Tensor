package com.akkc.tensor.plugin.api.error;

import java.util.Objects;

public final class AdapterException extends TensorException {
    public AdapterException(ErrorCode code, String message) {
        super(requireAdapterCode(code), message);
    }

    private static ErrorCode requireAdapterCode(ErrorCode code) {
        Objects.requireNonNull(code, "code");
        if (code != ErrorCode.ADAPTER_FIELD_MISSING && code != ErrorCode.ADAPTER_TYPE_INVALID) {
            throw new IllegalArgumentException("code must identify an adapter failure");
        }
        return code;
    }
}
