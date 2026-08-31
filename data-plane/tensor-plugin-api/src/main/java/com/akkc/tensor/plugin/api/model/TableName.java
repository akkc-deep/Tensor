package com.akkc.tensor.plugin.api.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record TableName(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}__[a-z][a-z0-9_]{1,63}$");

    public TableName {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid table name: " + value);
        }
    }

    public static TableName from(DatasetKey datasetKey) {
        Objects.requireNonNull(datasetKey, "datasetKey");
        return new TableName(datasetKey.pluginId().value() + "__" + datasetKey.apiName().value());
    }
}
