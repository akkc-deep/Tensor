package com.akkc.tensor.plugin.api.dataset;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import com.akkc.tensor.plugin.api.constant.ValidationMessages;
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
            throw new IllegalArgumentException(ValidationMessages.FIELDS_EMPTY);
        }
        if (fields.size() != new HashSet<>(fields).size()) {
            throw new IllegalArgumentException(ValidationMessages.DUPLICATE_FIELDS);
        }
        for (String field : fields) {
            if (!IDENTIFIER_PATTERN.matcher(field).matches()) {
                throw new IllegalArgumentException(ValidationMessages.INVALID_FIELD_PREFIX + field);
            }
        }
    }
}
