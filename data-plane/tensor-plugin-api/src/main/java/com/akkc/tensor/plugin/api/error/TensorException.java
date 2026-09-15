package com.akkc.tensor.plugin.api.error;

import com.akkc.tensor.plugin.api.constant.ValidationMessages;
import java.util.Objects;

public abstract class TensorException extends RuntimeException {
    private final ErrorCode code;

    protected TensorException(ErrorCode code, String message) {
        super(requireMessage(message));
        this.code = Objects.requireNonNull(code, "code");
    }

    public final ErrorCode code() {
        return code;
    }

    public final boolean retryable() {
        return code.retryable();
    }

    private static String requireMessage(String message) {
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException(ValidationMessages.MESSAGE_BLANK);
        }
        return message;
    }
}
