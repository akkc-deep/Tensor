package com.akkc.tensor.core.download.task;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Bounded canonical JSON used by persisted download tasks. */
public final class DownloadTaskJson {
    private static final int TASK_BYTES = 8 * 1024;
    private static final int SNAPSHOT_BYTES = 16 * 1024;
    private static final int INPUT_BYTES = 128 * 1024;
    private static final Pattern PARAMETER_NAME = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Set<String> SECRET_KEYS = Set.of(
            "token", "access_token", "refresh_token", "authorization", "password", "secret", "api_key");
    private static final Set<String> POLICY_FIELDS = Set.of(
            "schemaVersion", "mode", "parameters", "startParameter", "endParameter", "dateAxis",
            "dateLabel", "planningMode", "splittable", "availability", "unavailableReason",
            "policyVersion", "completenessRule");
    private static final Set<String> PARAMETER_FIELDS = Set.of(
            "name", "label", "description", "type", "required", "defaultValue", "allowedValues",
            "pattern", "relatedParameter");
    private static final Set<String> RULE_FIELDS = Set.of("kind", "rowLimit", "evidence");

    private final ObjectMapper mapper;

    public DownloadTaskJson() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).build())
                .build();
        mapper = new ObjectMapper(factory);
    }

    public String writeTaskParams(Map<String, Object> normalized) {
        return writeParams(normalized, TASK_BYTES);
    }

    public String writeBatchParams(Map<String, Object> normalized) {
        return writeParams(normalized, SNAPSHOT_BYTES);
    }

    public Map<String, Object> readTaskParams(String json) {
        return readParams(json, TASK_BYTES);
    }

    public Map<String, Object> readBatchParams(String json) {
        return readParams(json, SNAPSHOT_BYTES);
    }

    public String policySnapshot(DownloadMode mode, BatchDownloadDescriptor range) {
        if (mode == null) throw invalid();
        if (mode == DownloadMode.SINGLE) {
            if (range != null) {
                throw invalid();
            }
            return bounded(canonical(Map.of("schemaVersion", 1, "mode", mode.name())), SNAPSHOT_BYTES);
        }
        if (range == null) {
            throw invalid();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", 1);
        value.put("mode", mode.name());
        value.put("parameters", range.parameters().stream().map(this::fullParameter).toList());
        value.put("startParameter", range.startParameter());
        value.put("endParameter", range.endParameter());
        value.put("dateAxis", name(range.dateAxis()));
        value.put("dateLabel", range.dateLabel());
        value.put("planningMode", name(range.planningMode()));
        value.put("splittable", range.splittable());
        value.put("availability", range.availability().name());
        value.put("unavailableReason", range.unavailableReason());
        value.put("policyVersion", range.policyVersion());
        value.put("completenessRule", rule(range.completenessRule()));
        String result = bounded(canonical(value), SNAPSHOT_BYTES);
        validatePolicySnapshot(result);
        return result;
    }

    public String validatePolicySnapshot(String json) {
        try {
            return validatePolicySnapshotValue(json);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    BatchDownloadDescriptor readRangePolicy(String json) {
        try {
            ObjectNode root = requireObject(parse(validatePolicySnapshotValue(json)));
            if (enumValue(root, "mode", DownloadMode.class) != DownloadMode.RANGE) {
                throw invalid();
            }
            return rangePolicy(root);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private String validatePolicySnapshotValue(String json) {
        ObjectNode root = requireObject(parse(json));
        rejectUnknown(root, POLICY_FIELDS);
        requireInt(root, "schemaVersion", 1);
        DownloadMode mode = enumValue(root, "mode", DownloadMode.class);
        if (mode == DownloadMode.SINGLE) {
            if (root.size() != 2) {
                throw invalid();
            }
        } else {
            if (!root.fieldNames().hasNext() || root.size() != POLICY_FIELDS.size()) {
                throw invalid();
            }
            rangePolicy(root);
        }
        return bounded(canonical(root), SNAPSHOT_BYTES);
    }

    private BatchDownloadDescriptor rangePolicy(ObjectNode root) {
        List<ParameterDescriptor> parameters = readParameters(requireArray(root, "parameters"));
        ObjectNode rule = requireObject(root.get("completenessRule"));
        rejectUnknown(rule, RULE_FIELDS);
        var completeness = new BatchDownloadDescriptor.CompletenessRule(
                enumValue(rule, "kind", BatchDownloadDescriptor.CompletenessRule.Kind.class),
                nullableLong(rule, "rowLimit"), nullableText(rule, "evidence"));
        return new BatchDownloadDescriptor(parameters, nullableText(root, "startParameter"),
                nullableText(root, "endParameter"),
                nullableEnumValue(root, "dateAxis", BatchDownloadDescriptor.DateAxis.class),
                nullableText(root, "dateLabel"),
                nullableEnumValue(root, "planningMode", BatchDownloadDescriptor.PlanningMode.class),
                bool(root, "splittable"),
                enumValue(root, "availability", BatchDownloadDescriptor.Availability.class),
                nullableText(root, "unavailableReason"), text(root, "policyVersion"), completeness);
    }

    public String requestHash(DatasetKey dataset, DownloadMode mode, Map<String, Object> normalized) {
        if (dataset == null || mode == null) throw invalid();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("pluginId", dataset.pluginId().value());
        value.put("apiName", dataset.apiName().value());
        value.put("mode", mode.name());
        value.put("params", validatedParams(normalized));
        return sha256(canonical(value));
    }

    public String definitionHash(ApiDescriptor selectedApi, DatasetDefinition dataset,
            DownloadMode mode, BatchDownloadDescriptor range) {
        if (selectedApi == null || dataset == null || mode == null) throw invalid();
        if (!selectedApi.apiName().equals(dataset.datasetKey().apiName())) {
            throw invalid();
        }
        if ((mode == DownloadMode.SINGLE) != (range == null)) {
            throw invalid();
        }
        if (range != null && !selectedApi.parameters().equals(range.parameters())) {
            throw invalid();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", 1);
        value.put("datasetKey", Map.of("pluginId", dataset.datasetKey().pluginId().value(),
                "apiName", dataset.datasetKey().apiName().value()));
        value.put("tableName", dataset.tableName().value());
        value.put("mode", mode.name());
        value.put("queryMode", selectedApi.queryMode().name());
        value.put("parameters", selectedApi.parameters().stream().map(this::contractParameter).toList());
        value.put("rangePolicy", range == null ? Map.of("kind", "SINGLE") : rangePolicy(range));
        value.put("columns", dataset.columns().stream().map(this::column).toList());
        value.put("businessKey", Map.of("mode", dataset.businessKey().mode().name(),
                "fields", dataset.businessKey().fields()));
        return sha256(canonical(value));
    }

    private String writeParams(Map<String, Object> normalized, int limit) {
        return bounded(canonical(validatedParams(normalized)), limit);
    }

    private Map<String, Object> readParams(String json, int limit) {
        ObjectNode root = requireObject(parse(json));
        Map<String, Object> values = new TreeMap<>();
        root.properties().forEach(field -> values.put(field.getKey(), scalarText(field.getKey(), field.getValue())));
        String canonical = bounded(canonical(values), limit);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = mapper.readValue(canonical, LinkedHashMap.class);
            return Map.copyOf(result);
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private Map<String, String> validatedParams(Map<String, Object> normalized) {
        if (normalized == null) throw invalid();
        Map<String, String> result = new TreeMap<>();
        normalized.forEach((key, value) -> {
            if (!(value instanceof String text)) {
                throw invalid();
            }
            result.put(key, scalarText(key, mapper.getNodeFactory().textNode(text)));
        });
        return result;
    }

    private String scalarText(String key, JsonNode value) {
        if (key == null || !PARAMETER_NAME.matcher(key).matches()
                || SECRET_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT))
                || value == null || !value.isTextual()) {
            throw invalid();
        }
        return value.textValue();
    }

    private JsonNode parse(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > INPUT_BYTES) {
            throw invalid();
        }
        try (JsonParser parser = mapper.createParser(json)) {
            JsonNode node = mapper.readTree(parser);
            if (node == null || parser.nextToken() != null) {
                throw invalid();
            }
            return node;
        } catch (IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    private String canonical(Object value) {
        try {
            return mapper.writeValueAsString(sort(mapper.valueToTree(value)));
        } catch (IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> sorted.set(name, sort(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            node.forEach(item -> result.add(sort(item)));
            return result;
        }
        return node;
    }

    private Map<String, Object> fullParameter(ParameterDescriptor p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", p.name());
        value.put("label", p.label());
        value.put("description", p.description());
        value.put("type", p.type().name());
        value.put("required", p.required());
        value.put("defaultValue", p.defaultValue());
        value.put("allowedValues", p.allowedValues().stream().sorted().toList());
        value.put("pattern", p.pattern());
        value.put("relatedParameter", p.relatedParameter());
        return value;
    }

    private Map<String, Object> contractParameter(ParameterDescriptor p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", p.name());
        value.put("type", p.type().name());
        value.put("required", p.required());
        value.put("defaultValue", p.defaultValue());
        value.put("allowedValues", p.allowedValues());
        value.put("pattern", p.pattern());
        value.put("relatedParameter", p.relatedParameter());
        return value;
    }

    private Map<String, Object> rangePolicy(BatchDownloadDescriptor range) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("startParameter", range.startParameter());
        value.put("endParameter", range.endParameter());
        value.put("dateAxis", name(range.dateAxis()));
        value.put("planningMode", name(range.planningMode()));
        value.put("splittable", range.splittable());
        value.put("policyVersion", range.policyVersion());
        value.put("completenessRule", rule(range.completenessRule()));
        return value;
    }

    private Map<String, Object> rule(BatchDownloadDescriptor.CompletenessRule rule) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", rule.kind().name());
        value.put("rowLimit", rule.rowLimit());
        value.put("evidence", rule.evidence());
        return value;
    }

    private Map<String, Object> column(ColumnDefinition column) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", column.name());
        value.put("logicalType", column.logicalType().name());
        value.put("nullable", column.nullable());
        value.put("displayOrder", column.displayOrder());
        value.put("length", column.length());
        value.put("precision", column.precision());
        value.put("scale", column.scale());
        value.put("allowedValues", column.allowedValues());
        return value;
    }

    private List<ParameterDescriptor> readParameters(ArrayNode values) {
        List<ParameterDescriptor> result = new ArrayList<>();
        values.forEach(value -> {
            ObjectNode parameter = requireObject(value);
            rejectUnknown(parameter, PARAMETER_FIELDS);
            if (parameter.size() != PARAMETER_FIELDS.size()) {
                throw invalid();
            }
            ArrayNode allowed = requireArray(parameter, "allowedValues");
            List<String> allowedValues = new ArrayList<>();
            allowed.forEach(item -> {
                if (!item.isTextual()) throw invalid();
                allowedValues.add(item.textValue());
            });
            if (!allowedValues.equals(allowedValues.stream().sorted().toList())) throw invalid();
            result.add(new ParameterDescriptor(text(parameter, "name"), text(parameter, "label"),
                    nullableText(parameter, "description"), enumValue(parameter, "type", ParameterType.class),
                    bool(parameter, "required"), nullableText(parameter, "defaultValue"), allowedValues,
                    nullableText(parameter, "pattern"), nullableText(parameter, "relatedParameter")));
        });
        return result;
    }

    private static ObjectNode requireObject(JsonNode node) {
        if (node == null || !node.isObject()) throw invalid();
        return (ObjectNode) node;
    }

    private static ArrayNode requireArray(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) throw invalid();
        return (ArrayNode) value;
    }

    private static void rejectUnknown(ObjectNode node, Set<String> fields) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) if (!fields.contains(names.next())) throw invalid();
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static String nullableText(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) throw invalid();
        if (value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static boolean bool(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) throw invalid();
        return value.booleanValue();
    }

    private static Long nullableLong(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) throw invalid();
        if (value.isNull()) return null;
        if (!value.canConvertToLong() || !value.isIntegralNumber()) throw invalid();
        return value.longValue();
    }

    private static void requireInt(ObjectNode node, String field, int expected) {
        JsonNode value = node.get(field);
        if (value == null || !value.isInt() || value.intValue() != expected) throw invalid();
    }

    private static <E extends Enum<E>> E enumValue(ObjectNode node, String field, Class<E> type) {
        try {
            return Enum.valueOf(type, text(node, field));
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static <E extends Enum<E>> E nullableEnumValue(ObjectNode node, String field, Class<E> type) {
        if (node.get(field) == null) throw invalid();
        if (node.get(field).isNull()) return null;
        return enumValue(node, field, type);
    }

    private static String bounded(String json, int limit) {
        if (json.getBytes(StandardCharsets.UTF_8).length > limit) throw invalid();
        return json;
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid download task JSON");
    }
}
