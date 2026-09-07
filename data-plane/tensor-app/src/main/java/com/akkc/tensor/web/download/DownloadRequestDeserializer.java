package com.akkc.tensor.web.download;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.web.dto.FieldErrorResponse;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DownloadRequestDeserializer extends JsonDeserializer<DownloadRequest> {
    private final DownloadParameterResolver resolver;

    public DownloadRequestDeserializer(DownloadParameterResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public DownloadRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        // Retain Jackson record binding, including errors in earlier duplicate fields.
        WireValues input = context.readValue(parser, WireValues.class);
        String pluginId = input.pluginId();
        String apiName = input.apiName();
        Map<String, Boolean> errors = new TreeMap<>();
        identifier("pluginId", pluginId, errors);
        identifier("apiName", apiName, errors);
        if (input.params() == null) {
            errors.put("params", false);
        }
        if (!errors.isEmpty()) {
            ErrorCode code = errors.containsValue(true) ? ErrorCode.PARAM_INVALID : ErrorCode.PARAM_REQUIRED;
            List<FieldErrorResponse> fields = errors.entrySet().stream()
                    .map(entry -> new FieldErrorResponse(entry.getKey(),
                            entry.getValue() ? "has invalid value" : "is required")).toList();
            throw new DownloadBindingException(code, fields);
        }
        DatasetKey dataset = DatasetKey.of(PluginId.of(pluginId), ApiName.of(apiName));
        Map<String, Object> values = input.params();
        return new DownloadRequest(dataset, resolver.resolve(dataset, values), values.keySet());
    }

    // Transient wire values only; Controller requests always contain a concrete parameter type.
    private record WireValues(String pluginId, String apiName, Map<String, Object> params) {}

    private static void identifier(String name, String value, Map<String, Boolean> errors) {
        if (value == null) {
            errors.put(name, false);
        } else if (!value.matches("^[a-z][a-z0-9_]{1,63}$")) {
            errors.put(name, true);
        }
    }
}
