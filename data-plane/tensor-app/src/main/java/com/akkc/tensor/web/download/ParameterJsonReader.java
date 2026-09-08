package com.akkc.tensor.web.download;

import com.akkc.tensor.core.validation.ParameterValidator;
import java.util.List;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ParameterJsonReader {
    private final Map<String, Object> values;

    ParameterJsonReader(Map<String, Object> values, List<ParameterDescriptor> parameters, ParameterValidator validator) {
        this.values = values;
        Set<String> declared = parameters.stream()
                .map(ParameterDescriptor::name).collect(Collectors.toSet());
        if (!declared.containsAll(values.keySet())
                || values.values().stream().anyMatch(value -> value != null && !(value instanceof String))) {
            // Reuse required-before-invalid ordering and all field errors; never coerce JSON values.
            validator.validate(parameters, values);
        }
    }

    String nullableText(String name) {
        return (String) values.get(name);
    }
}
