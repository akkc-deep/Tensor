package com.akkc.tensor.web.dto;

import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

public record ApiDescriptorResponse(
        String apiName,
        String displayName,
        String category,
        QueryMode queryMode,
        List<ParameterResponse> parameters) {

    public ApiDescriptorResponse {
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(queryMode, "queryMode");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }

    public static ApiDescriptorResponse from(ApiDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return new ApiDescriptorResponse(
                descriptor.apiName().value(),
                descriptor.displayName(),
                descriptor.category(),
                descriptor.queryMode(),
                descriptor.parameters().stream().map(ParameterResponse::from).toList());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParameterResponse(
            String name,
            String label,
            ParameterType type,
            boolean required,
            String description,
            String defaultValue,
            List<String> allowedValues,
            String pattern,
            String relatedParameter) {

        public ParameterResponse {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(type, "type");
            if (allowedValues != null) {
                allowedValues = List.copyOf(allowedValues);
            }
        }

        public static ParameterResponse from(ParameterDescriptor descriptor) {
            Objects.requireNonNull(descriptor, "descriptor");
            return new ParameterResponse(
                    descriptor.name(),
                    descriptor.label(),
                    descriptor.type(),
                    descriptor.required(),
                    descriptor.description(),
                    descriptor.defaultValue(),
                    descriptor.allowedValues().isEmpty() ? null : descriptor.allowedValues(),
                    descriptor.pattern(),
                    descriptor.relatedParameter());
        }
    }
}
