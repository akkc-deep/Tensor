package com.akkc.tensor.plugin.api.integrity;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.akkc.tensor.plugin.api.constant.ValidationConstants;

/** Defensive copies of the exact, immutable values allowed at the rule boundary. */
final class IntegrityValues {
    private IntegrityValues() {}
    static String text(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required text is missing");
        return value;
    }
    static String column(String value) {
        if (!text(value).matches(ValidationConstants.IDENTIFIER_REGEX))
            throw new IllegalArgumentException("Invalid column name");
        return value;
    }
    static List<String> columns(List<String> values) {
        var copy = List.copyOf(values);
        copy.forEach(IntegrityValues::column);
        if (new HashSet<>(copy).size() != copy.size()) throw new IllegalArgumentException("Duplicate columns");
        return copy;
    }
    static Map<String, Object> values(Map<String, Object> values) {
        var copy = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> {
            column(key);
            if (value != null && !(value instanceof String || value instanceof Long || value instanceof Integer
                    || value instanceof BigDecimal || value instanceof LocalDate || value instanceof YearMonth
                    || value instanceof Instant || value instanceof Boolean))
                throw new IllegalArgumentException("Expected an immutable exact scalar");
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
