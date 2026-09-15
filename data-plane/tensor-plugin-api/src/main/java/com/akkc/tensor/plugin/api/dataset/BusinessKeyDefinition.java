package com.akkc.tensor.plugin.api.dataset;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record BusinessKeyDefinition(BusinessKeyMode mode, List<String> fields) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(ValidationConstants.IDENTIFIER_REGEX);

    public BusinessKeyDefinition {
        Objects.requireNonNull(mode, "mode");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        if (fields.size() != new HashSet<>(fields).size()) {
            throw new IllegalArgumentException("fields must not contain duplicates");
        }
        for (String field : fields) {
            if (!IDENTIFIER_PATTERN.matcher(field).matches()) {
                throw new IllegalArgumentException("Invalid field: " + field);
            }
        }
    }
}
