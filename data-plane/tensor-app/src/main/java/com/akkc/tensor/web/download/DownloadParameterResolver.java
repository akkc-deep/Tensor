package com.akkc.tensor.web.download;

import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DownloadParameterResolver {
    private final DownloadDescriptorResolver descriptors;
    private final ParameterValidator validator;
    private final OperationLogger operations;
    private final Map<ParameterShape, ParameterCodec<?>> byShape;
    private final Map<Class<?>, ParameterCodec<?>> byType;

    public DownloadParameterResolver(DownloadDescriptorResolver descriptors,
            ParameterValidator validator, OperationLogger operations) {
        this.descriptors = descriptors;
        this.validator = validator;
        this.operations = operations;
        var codecs = ParameterCodec.supported();
        byShape = codecs.stream().collect(Collectors.toUnmodifiableMap(ParameterCodec::shape, Function.identity()));
        byType = codecs.stream().collect(Collectors.toUnmodifiableMap(ParameterCodec::parameterType, Function.identity()));
    }

    public DownloadParameters resolve(DatasetKey dataset, Map<String, Object> values) {
        try {
            ApiDescriptor api = descriptors.requireApi(dataset);
            ParameterCodec<?> codec = byShape.get(ParameterShape.from(api));
            if (codec == null) {
                throw DownloadDescriptorResolver.misconfigured();
            }
            return codec.read(new ParameterJsonReader(values, api, validator));
        } catch (TensorException failure) {
            // Binding failures do not reach the Controller; emit the existing failure event here.
            DownloadBindingException binding = DownloadBindingException.from(failure);
            operations.download(dataset, values, () -> { throw binding; });
            throw binding;
        }
    }

    public Map<String, Object> toRawValues(DownloadParameters parameters, Set<String> suppliedFields) {
        ParameterCodec<?> codec = byType.get(parameters.getClass());
        if (codec == null) {
            throw DownloadDescriptorResolver.misconfigured();
        }
        Map<String, Object> values = codec.write(parameters);
        values.keySet().retainAll(suppliedFields);
        return values;
    }
}
