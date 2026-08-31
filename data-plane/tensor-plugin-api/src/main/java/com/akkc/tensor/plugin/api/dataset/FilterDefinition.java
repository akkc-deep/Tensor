package com.akkc.tensor.plugin.api.dataset;

import java.util.Objects;
import java.util.regex.Pattern;

public record FilterDefinition(String field) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public FilterDefinition {
        Objects.requireNonNull(field, "field");
        if (!IDENTIFIER_PATTERN.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field: " + field);
        }
    }
}
