package com.akkc.tensor.plugin.api.descriptor;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import com.akkc.tensor.plugin.api.constant.ValidationMessages;
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
        if (category.length() > ValidationConstants.MAX_CATEGORY_LENGTH) {
            throw new IllegalArgumentException(ValidationMessages.CATEGORY_TOO_LONG);
        }
        Objects.requireNonNull(queryMode, "queryMode");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        if (parameters.stream().map(ParameterDescriptor::name).collect(java.util.stream.Collectors.toSet()).size()
                != parameters.size()) {
            throw new IllegalArgumentException(ValidationMessages.DUPLICATE_PARAMETERS);
        }
    }

    private static void requireNonBlank(String value, String component) {
        Objects.requireNonNull(value, component);
        if (value.isBlank()) {
            throw new IllegalArgumentException(component + ValidationMessages.MUST_NOT_BE_BLANK);
        }
    }
}
