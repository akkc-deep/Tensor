package com.akkc.tensor.web.download;

import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

record ParameterShape(List<Field> fields) {
    ParameterShape {
        fields = fields.stream().sorted(Comparator.comparing(Field::name)).toList();
    }

    static ParameterShape from(ApiDescriptor api) {
        return from(api.parameters());
    }

    static ParameterShape from(List<ParameterDescriptor> parameters) {
        return new ParameterShape(parameters.stream().map(Field::from).toList());
    }

    static ParameterShape of(Field... fields) {
        return new ParameterShape(List.of(fields));
    }

    record Field(String name, ParameterType type, boolean required, String defaultValue,
            Set<String> allowedValues, String pattern, String relatedParameter) {
        Field {
            allowedValues = Set.copyOf(allowedValues);
        }

        static Field from(ParameterDescriptor parameter) {
            return new Field(parameter.name(), parameter.type(), parameter.required(),
                    parameter.defaultValue(), Set.copyOf(parameter.allowedValues()),
                    parameter.pattern(), parameter.relatedParameter());
        }
    }
}
