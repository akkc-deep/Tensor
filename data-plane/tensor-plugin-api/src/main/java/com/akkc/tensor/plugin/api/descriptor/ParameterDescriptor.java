package com.akkc.tensor.plugin.api.descriptor;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import com.akkc.tensor.plugin.api.constant.ValidationMessages;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record ParameterDescriptor(
        String name,
        String label,
        String description,
        ParameterType type,
        boolean required,
        String defaultValue,
        List<String> allowedValues,
        String pattern,
        String relatedParameter
) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(ValidationConstants.IDENTIFIER_REGEX);

    public ParameterDescriptor {
        requireIdentifier(name, "name");
        requireNonBlank(label, "label");
        if (description != null) {
            requireNonBlank(description, "description");
        }
        Objects.requireNonNull(type, "type");
        allowedValues = List.copyOf(Objects.requireNonNull(allowedValues, "allowedValues"));
        if (allowedValues.size() != new HashSet<>(allowedValues).size()) {
            throw new IllegalArgumentException(ValidationMessages.DUPLICATE_ALLOWED_VALUES);
        }
        if (type == ParameterType.ENUM && allowedValues.isEmpty()) {
            throw new IllegalArgumentException("ENUM parameters require allowedValues");
        }
        if (type == ParameterType.DATE_RANGE_MEMBER) {
            if (relatedParameter == null) {
                throw new IllegalArgumentException("DATE_RANGE_MEMBER parameters require relatedParameter");
            }
            requireIdentifier(relatedParameter, "relatedParameter");
            if (name.equals(relatedParameter)) {
                throw new IllegalArgumentException("relatedParameter must differ from name");
            }
        } else if (relatedParameter != null) {
            requireIdentifier(relatedParameter, "relatedParameter");
        }
    }

    private static void requireIdentifier(String value, String component) {
        Objects.requireNonNull(value, component);
        if (!IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + component + ": " + value);
        }
    }

    private static void requireNonBlank(String value, String component) {
        Objects.requireNonNull(value, component);
        if (value.isBlank()) {
            throw new IllegalArgumentException(component + ValidationMessages.MUST_NOT_BE_BLANK);
        }
    }
}
