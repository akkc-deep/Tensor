package com.akkc.tensor.plugin.api.model;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import java.util.Objects;
import java.util.regex.Pattern;

public record ApiName(String value) {
    private static final Pattern PATTERN = Pattern.compile(ValidationConstants.IDENTIFIER_REGEX);

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
