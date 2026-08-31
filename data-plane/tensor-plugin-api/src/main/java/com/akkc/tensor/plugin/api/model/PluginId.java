package com.akkc.tensor.plugin.api.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record PluginId(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

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
