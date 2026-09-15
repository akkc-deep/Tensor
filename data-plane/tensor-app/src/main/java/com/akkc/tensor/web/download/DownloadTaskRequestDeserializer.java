package com.akkc.tensor.web.download;

import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.web.dto.DownloadTaskRequest;
import com.akkc.tensor.web.dto.FieldErrorResponse;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;

public final class DownloadTaskRequestDeserializer extends JsonDeserializer<DownloadTaskRequest> {
    private static final Set<String> FIELDS = Set.of("submissionId", "pluginId", "apiName", "mode", "params");
    private final DownloadTaskService tasks;
    private final DownloadParameterResolver resolver;

    public DownloadTaskRequestDeserializer(DownloadTaskService tasks, DownloadParameterResolver resolver) {
        this.tasks = Objects.requireNonNull(tasks);
        this.resolver = Objects.requireNonNull(resolver);
    }

    @Override
    public DownloadTaskRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode input = object(parser, context, FIELDS);
        Map<String, Boolean> errors = new TreeMap<>();
        for (String name : FIELDS) {
            JsonNode value = input.get(name);
            if (value == null || value.isNull()) errors.put(name, false);
            else if (!name.equals("params") && (!value.isTextual() || !valid(name, value.textValue())))
                errors.put(name, true);
        }
        JsonNode params = input.get("params");
        if (params != null && !params.isNull() && !params.isObject()) throw invalid("request");
        reject(errors);
        Map<String, Object> raw = new LinkedHashMap<>();
        params.fields().forEachRemaining(entry -> {
            if (!identifier(entry.getKey())) throw invalid("params");
            if (!entry.getValue().isTextual()) throw invalid(entry.getKey());
            raw.put(entry.getKey(), entry.getValue().textValue());
        });
        try {
            var submission = new DownloadTaskService.Submission(UUID.fromString(input.get("submissionId").textValue()),
                    DatasetKey.of(PluginId.of(input.get("pluginId").textValue()), ApiName.of(input.get("apiName").textValue())),
                    DownloadMode.valueOf(input.get("mode").textValue()), raw);
            var binding = tasks.prepareSubmission(submission);
            if (binding.replay()) return new DownloadTaskRequest.Replay(binding.submission());
            var normalized = binding.submission();
            return new DownloadTaskRequest.Bound(normalized.submissionId(), normalized.datasetKey(), normalized.mode(),
                    resolver.resolve(binding.api(), normalized.params()), normalized.params().keySet());
        } catch (TensorException failure) {
            throw DownloadBindingException.from(failure);
        }
    }

    @Override
    public DownloadTaskRequest getNullValue(DeserializationContext context) { throw invalid("request"); }

    static JsonNode object(JsonParser parser, DeserializationContext context, Set<String> fields) throws IOException {
        parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        JsonNode input = context.readTree(parser);
        if (!input.isObject() || parser.nextToken() != null) throw invalid("request");
        var names = input.fieldNames();
        while (names.hasNext()) if (!fields.contains(names.next())) throw invalid("request");
        return input;
    }

    static void reject(Map<String, Boolean> errors) {
        if (!errors.isEmpty()) throw new DownloadBindingException(
                errors.containsValue(true) ? ErrorCode.PARAM_INVALID : ErrorCode.PARAM_REQUIRED,
                errors.entrySet().stream().map(e -> new FieldErrorResponse(e.getKey(),
                        e.getValue() ? "has invalid value" : "is required")).toList());
    }

    static DownloadBindingException invalid(String field) {
        return new DownloadBindingException(ErrorCode.PARAM_INVALID,
                List.of(new FieldErrorResponse(field, "has invalid value")));
    }

    private static boolean valid(String name, String value) {
        return switch (name) {
            case "submissionId" -> value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
            case "mode" -> value.equals("SINGLE") || value.equals("RANGE");
            default -> identifier(value);
        };
    }

    private static boolean identifier(String value) { return value.matches("^[a-z][a-z0-9_]{1,63}$"); }
}
