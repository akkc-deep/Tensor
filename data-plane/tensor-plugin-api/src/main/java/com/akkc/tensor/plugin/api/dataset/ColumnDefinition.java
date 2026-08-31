package com.akkc.tensor.plugin.api.dataset;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record ColumnDefinition(
        String name,
        String label,
        LogicalType logicalType,
        boolean nullable,
        int displayOrder,
        Integer length,
        Integer precision,
        Integer scale,
        List<String> allowedValues,
        boolean longText
) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public ColumnDefinition {
        Objects.requireNonNull(name, "name");
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid name: " + name);
        }
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        Objects.requireNonNull(logicalType, "logicalType");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must be non-negative");
        }
        if (length != null && length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        if (precision != null && (precision < 1 || precision > 65)) {
            throw new IllegalArgumentException("precision must be between 1 and 65");
        }
        if (scale != null && (scale < 0 || scale > 30)) {
            throw new IllegalArgumentException("scale must be between 0 and 30");
        }
        if ((logicalType == LogicalType.STRING || logicalType == LogicalType.ENUM) && length == null) {
            throw new IllegalArgumentException(logicalType + " columns require length");
        }
        if (logicalType == LogicalType.DECIMAL && (precision == null || scale == null)) {
            throw new IllegalArgumentException("DECIMAL columns require precision and scale");
        }
        allowedValues = List.copyOf(Objects.requireNonNull(allowedValues, "allowedValues"));
        if (allowedValues.size() != new HashSet<>(allowedValues).size()) {
            throw new IllegalArgumentException("allowedValues must not contain duplicates");
        }
    }
}
