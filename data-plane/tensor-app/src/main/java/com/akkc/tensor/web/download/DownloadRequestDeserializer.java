package com.akkc.tensor.web.download;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.web.dto.FieldErrorResponse;
import com.fasterxml.jackson.annotation.JsonAnySetter;
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
        // This parser belongs to one download request; reject duplicate fields at both object levels.
        parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        WireValues input = context.readValue(parser, WireValues.class);
        Object pluginId = input.pluginId();
        Object apiName = input.apiName();
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
        DatasetKey dataset = DatasetKey.of(PluginId.of((String) pluginId), ApiName.of((String) apiName));
        Map<String, Object> values = input.params();
        return new DownloadRequest(dataset, resolver.resolveDownload(dataset, values), values.keySet());
    }

    // Transient wire values only; Controller requests always contain a concrete parameter type.
    private record WireValues(Object pluginId, Object apiName, Map<String, Object> params) {
        @JsonAnySetter
        void rejectUnknown(String name, Object value) {
            throw new DownloadBindingException(ErrorCode.PARAM_INVALID,
                    List.of(new FieldErrorResponse("request", "has invalid value")));
        }
    }

    private static void identifier(String name, Object value, Map<String, Boolean> errors) {
        if (value == null) {
            errors.put(name, false);
        } else if (!(value instanceof String text) || !text.matches("^[a-z][a-z0-9_]{1,63}$")) {
            errors.put(name, true);
        }
    }
}
