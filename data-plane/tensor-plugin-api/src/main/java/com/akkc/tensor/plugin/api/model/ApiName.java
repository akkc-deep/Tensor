package com.akkc.tensor.plugin.api.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record ApiName(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public ApiName {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid API name: " + value);
        }
    }

    public static ApiName of(String value) {
        return new ApiName(value);
    }
}
