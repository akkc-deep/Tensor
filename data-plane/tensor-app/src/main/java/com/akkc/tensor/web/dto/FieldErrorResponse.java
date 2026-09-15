package com.akkc.tensor.web.dto;

import com.akkc.tensor.plugin.api.constant.ValidationMessages;
import java.util.Objects;

public record FieldErrorResponse(String field, String message) {
    public FieldErrorResponse {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
        if (field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException(ValidationMessages.MESSAGE_BLANK);
        }
    }
}
