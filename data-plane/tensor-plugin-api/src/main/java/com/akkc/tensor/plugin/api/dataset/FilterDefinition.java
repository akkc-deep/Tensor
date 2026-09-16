package com.akkc.tensor.plugin.api.dataset;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import java.util.Objects;
import java.util.regex.Pattern;

public record FilterDefinition(String field) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(ValidationConstants.IDENTIFIER_REGEX);

    public FilterDefinition {
        Objects.requireNonNull(field, "field");
        if (!IDENTIFIER_PATTERN.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field: " + field);
        }
    }
}
