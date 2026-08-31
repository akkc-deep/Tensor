package com.akkc.tensor.plugin.api.descriptor;

import com.akkc.tensor.plugin.api.model.ApiName;
import java.util.List;
import java.util.Objects;

public record ApiDescriptor(
        ApiName apiName,
        String displayName,
        String category,
        QueryMode queryMode,
        List<ParameterDescriptor> parameters
) {
    public ApiDescriptor {
        Objects.requireNonNull(apiName, "apiName");
        requireNonBlank(displayName, "displayName");
        requireNonBlank(category, "category");
        if (category.length() > 64) {
            throw new IllegalArgumentException("category must be at most 64 characters");
        }
        Objects.requireNonNull(queryMode, "queryMode");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        if (parameters.stream().map(ParameterDescriptor::name).collect(java.util.stream.Collectors.toSet()).size()
                != parameters.size()) {
            throw new IllegalArgumentException("parameters must not contain duplicate names");
        }
    }

    private static void requireNonBlank(String value, String component) {
        Objects.requireNonNull(value, component);
        if (value.isBlank()) {
            throw new IllegalArgumentException(component + " must not be blank");
        }
    }
}
