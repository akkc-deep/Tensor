package com.akkc.tensor.plugin.api.model;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import java.util.Objects;
import java.util.regex.Pattern;

public record PluginId(String value) {
    private static final Pattern PATTERN = Pattern.compile(ValidationConstants.IDENTIFIER_REGEX);

    public PluginId {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid plugin id: " + value);
        }
    }

    public static PluginId of(String value) {
        return new PluginId(value);
    }
}
