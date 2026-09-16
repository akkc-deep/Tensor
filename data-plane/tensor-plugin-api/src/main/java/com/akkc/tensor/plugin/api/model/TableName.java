package com.akkc.tensor.plugin.api.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record TableName(String value) {
    private static final String TABLE_NAME_REGEX =
            "^[a-z][a-z0-9_]{1,63}__[a-z][a-z0-9_]{1,63}$";
    private static final String PART_SEPARATOR = "__";
    private static final Pattern PATTERN = Pattern.compile(TABLE_NAME_REGEX);

    public TableName {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid table name: " + value);
        }
    }

    public static TableName from(DatasetKey datasetKey) {
        Objects.requireNonNull(datasetKey, "datasetKey");
        return new TableName(datasetKey.pluginId().value() + PART_SEPARATOR + datasetKey.apiName().value());
    }
}
