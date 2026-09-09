package com.akkc.tensor.web.download;

import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import java.util.List;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DownloadParameterResolver {
    private final DownloadDescriptorResolver descriptors;
    private final ParameterValidator validator;
    private final Map<ParameterShape, ParameterCodec<?>> byShape;
    private final Map<Class<?>, ParameterCodec<?>> byType;

    public DownloadParameterResolver(DownloadDescriptorResolver descriptors,
            ParameterValidator validator) {
        this.descriptors = descriptors;
        this.validator = validator;
        var codecs = ParameterCodec.supported();
        byShape = codecs.stream().collect(Collectors.toUnmodifiableMap(ParameterCodec::shape, Function.identity()));
        byType = codecs.stream().collect(Collectors.toUnmodifiableMap(ParameterCodec::parameterType, Function.identity()));
    }

    public DownloadParameters resolve(DatasetKey dataset, Map<String, Object> values) {
        try {
            ApiDescriptor api = descriptors.requireApi(dataset);
            return bind(api.sourceParameters(), values);
        } catch (TensorException failure) {
            throw DownloadBindingException.from(failure);
        }
    }

    /** Projected range binding; the running HTTP deserializer switches here in RANGE-T14. */
    public DownloadParameters resolveDownload(DatasetKey dataset, Map<String, Object> values) {
        try {
            ApiDescriptor api = descriptors.requireApi(dataset);
            if (api.downloadPolicy().mode() != com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode.ORIGINAL_PARAMS) {
                for (String old : List.of("trade_date", "ann_date", "month")) {
                    if (values.containsKey(old)) throw new DownloadBindingException(
                            com.akkc.tensor.plugin.api.error.ErrorCode.PARAM_INVALID,
                            List.of(new com.akkc.tensor.web.dto.FieldErrorResponse(old, "is no longer accepted")));
                }
            }
            return bind(api.parameters(), validator.validate(api, values).values());
        } catch (TensorException failure) {
            throw DownloadBindingException.from(failure);
        }
    }

    private DownloadParameters bind(List<ParameterDescriptor> parameters, Map<String, Object> values) {
        ParameterCodec<?> codec = byShape.get(ParameterShape.from(parameters));
        if (codec == null) throw DownloadDescriptorResolver.misconfigured();
        return codec.read(new ParameterJsonReader(values, parameters, validator));
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
