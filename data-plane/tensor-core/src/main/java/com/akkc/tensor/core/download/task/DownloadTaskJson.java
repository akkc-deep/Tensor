package com.akkc.tensor.core.download.task;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.plugin.api.constant.RequestFields;
import com.akkc.tensor.plugin.api.constant.ValidationConstants;
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
    private static final int SINGLE_POLICY_FIELD_COUNT = 2;
    private static final int MAX_NESTING_DEPTH = 16;

    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String PARAMETERS = "parameters";
    static final String START_PARAMETER = "startParameter";
    static final String END_PARAMETER = "endParameter";
    private static final String DATE_AXIS = "dateAxis";
    private static final String DATE_LABEL = "dateLabel";
    private static final String PLANNING_MODE = "planningMode";
    private static final String SPLITTABLE = "splittable";
    private static final String AVAILABILITY = "availability";
    private static final String UNAVAILABLE_REASON = "unavailableReason";
    private static final String POLICY_VERSION = "policyVersion";
    private static final String COMPLETENESS_RULE = "completenessRule";
    private static final String NAME = "name";
    private static final String LABEL = "label";
    private static final String DESCRIPTION = "description";
    private static final String TYPE = "type";
    private static final String REQUIRED = "required";
    private static final String DEFAULT_VALUE = "defaultValue";
    private static final String ALLOWED_VALUES = "allowedValues";
    private static final String PATTERN = "pattern";
    private static final String RELATED_PARAMETER = "relatedParameter";
    private static final String KIND = "kind";
    private static final String ROW_LIMIT = "rowLimit";
    private static final String EVIDENCE = "evidence";

    private static final int TASK_BYTES = 8_192;
    private static final int SNAPSHOT_BYTES = 16_384;
    private static final int INPUT_BYTES = 131_072;
    private static final Pattern PARAMETER_NAME = Pattern.compile(ValidationConstants.IDENTIFIER_REGEX);
    private static final Set<String> SECRET_KEYS = Set.of(
            RequestFields.TOKEN, "access_token", "refresh_token", "authorization", "password", "secret", "api_key");
    private static final Set<String> POLICY_FIELDS = Set.of(
            SCHEMA_VERSION, RequestFields.MODE, PARAMETERS, START_PARAMETER, END_PARAMETER, DATE_AXIS,
            DATE_LABEL, PLANNING_MODE, SPLITTABLE, AVAILABILITY, UNAVAILABLE_REASON,
            POLICY_VERSION, COMPLETENESS_RULE);
    private static final Set<String> PARAMETER_FIELDS = Set.of(
            NAME, LABEL, DESCRIPTION, TYPE, REQUIRED, DEFAULT_VALUE, ALLOWED_VALUES,
            PATTERN, RELATED_PARAMETER);
    private static final Set<String> RULE_FIELDS = Set.of(KIND, ROW_LIMIT, EVIDENCE);

    private final ObjectMapper mapper;

    public DownloadTaskJson() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_NESTING_DEPTH).build())
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
            return bounded(canonical(Map.of(SCHEMA_VERSION, 1, RequestFields.MODE, mode.name())), SNAPSHOT_BYTES);
        }
        if (range == null) {
            throw invalid();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(SCHEMA_VERSION, 1);
        value.put(RequestFields.MODE, mode.name());
        value.put(PARAMETERS, range.parameters().stream().map(this::fullParameter).toList());
        value.put(START_PARAMETER, range.startParameter());
        value.put(END_PARAMETER, range.endParameter());
        value.put(DATE_AXIS, name(range.dateAxis()));
        value.put(DATE_LABEL, range.dateLabel());
        value.put(PLANNING_MODE, name(range.planningMode()));
        value.put(SPLITTABLE, range.splittable());
        value.put(AVAILABILITY, range.availability().name());
        value.put(UNAVAILABLE_REASON, range.unavailableReason());
        value.put(POLICY_VERSION, range.policyVersion());
        value.put(COMPLETENESS_RULE, rule(range.completenessRule()));
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
            if (enumValue(root, RequestFields.MODE, DownloadMode.class) != DownloadMode.RANGE) {
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
        requireInt(root, SCHEMA_VERSION, 1);
        DownloadMode mode = enumValue(root, RequestFields.MODE, DownloadMode.class);
        if (mode == DownloadMode.SINGLE) {
            if (root.size() != SINGLE_POLICY_FIELD_COUNT) {
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
        List<ParameterDescriptor> parameters = readParameters(requireArray(root, PARAMETERS));
        ObjectNode rule = requireObject(root.get(COMPLETENESS_RULE));
        rejectUnknown(rule, RULE_FIELDS);
        var completeness = new BatchDownloadDescriptor.CompletenessRule(
                enumValue(rule, KIND, BatchDownloadDescriptor.CompletenessRule.Kind.class),
                nullableLong(rule, ROW_LIMIT), nullableText(rule, EVIDENCE));
        return new BatchDownloadDescriptor(parameters, nullableText(root, START_PARAMETER),
                nullableText(root, END_PARAMETER),
                nullableEnumValue(root, DATE_AXIS, BatchDownloadDescriptor.DateAxis.class),
                nullableText(root, DATE_LABEL),
                nullableEnumValue(root, PLANNING_MODE, BatchDownloadDescriptor.PlanningMode.class),
                bool(root, SPLITTABLE),
                enumValue(root, AVAILABILITY, BatchDownloadDescriptor.Availability.class),
                nullableText(root, UNAVAILABLE_REASON), text(root, POLICY_VERSION), completeness);
    }

    public String requestHash(DatasetKey dataset, DownloadMode mode, Map<String, Object> normalized) {
        if (dataset == null || mode == null) throw invalid();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(RequestFields.PLUGIN_ID, dataset.pluginId().value());
        value.put(RequestFields.API_NAME, dataset.apiName().value());
        value.put(RequestFields.MODE, mode.name());
        value.put(RequestFields.PARAMS, validatedParams(normalized));
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
        value.put(SCHEMA_VERSION, 1);
        value.put("datasetKey", Map.of(RequestFields.PLUGIN_ID, dataset.datasetKey().pluginId().value(),
                RequestFields.API_NAME, dataset.datasetKey().apiName().value()));
        value.put("tableName", dataset.tableName().value());
        value.put(RequestFields.MODE, mode.name());
        value.put("queryMode", selectedApi.queryMode().name());
        value.put(PARAMETERS, selectedApi.parameters().stream().map(this::contractParameter).toList());
        value.put("rangePolicy", range == null ? Map.of(KIND, DownloadMode.SINGLE.name()) : rangePolicy(range));
        value.put("columns", dataset.columns().stream().map(this::column).toList());
        value.put("businessKey", Map.of(RequestFields.MODE, dataset.businessKey().mode().name(),
                RequestFields.FIELDS, dataset.businessKey().fields()));
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
        value.put(NAME, p.name());
        value.put(LABEL, p.label());
        value.put(DESCRIPTION, p.description());
        value.put(TYPE, p.type().name());
        value.put(REQUIRED, p.required());
        value.put(DEFAULT_VALUE, p.defaultValue());
        value.put(ALLOWED_VALUES, p.allowedValues().stream().sorted().toList());
        value.put(PATTERN, p.pattern());
        value.put(RELATED_PARAMETER, p.relatedParameter());
        return value;
    }

    private Map<String, Object> contractParameter(ParameterDescriptor p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(NAME, p.name());
        value.put(TYPE, p.type().name());
        value.put(REQUIRED, p.required());
        value.put(DEFAULT_VALUE, p.defaultValue());
        value.put(ALLOWED_VALUES, p.allowedValues());
        value.put(PATTERN, p.pattern());
        value.put(RELATED_PARAMETER, p.relatedParameter());
        return value;
    }

    private Map<String, Object> rangePolicy(BatchDownloadDescriptor range) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(START_PARAMETER, range.startParameter());
        value.put(END_PARAMETER, range.endParameter());
        value.put(DATE_AXIS, name(range.dateAxis()));
        value.put(PLANNING_MODE, name(range.planningMode()));
        value.put(SPLITTABLE, range.splittable());
        value.put(POLICY_VERSION, range.policyVersion());
        value.put(COMPLETENESS_RULE, rule(range.completenessRule()));
        return value;
    }

    private Map<String, Object> rule(BatchDownloadDescriptor.CompletenessRule rule) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(KIND, rule.kind().name());
        value.put(ROW_LIMIT, rule.rowLimit());
        value.put(EVIDENCE, rule.evidence());
        return value;
    }

    private Map<String, Object> column(ColumnDefinition column) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(NAME, column.name());
        value.put("logicalType", column.logicalType().name());
        value.put("nullable", column.nullable());
        value.put("displayOrder", column.displayOrder());
        value.put("length", column.length());
        value.put("precision", column.precision());
        value.put("scale", column.scale());
        value.put(ALLOWED_VALUES, column.allowedValues());
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
            ArrayNode allowed = requireArray(parameter, ALLOWED_VALUES);
            List<String> allowedValues = new ArrayList<>();
            allowed.forEach(item -> {
                if (!item.isTextual()) throw invalid();
                allowedValues.add(item.textValue());
            });
            if (!allowedValues.equals(allowedValues.stream().sorted().toList())) throw invalid();
            result.add(new ParameterDescriptor(text(parameter, NAME), text(parameter, LABEL),
                    nullableText(parameter, DESCRIPTION), enumValue(parameter, TYPE, ParameterType.class),
                    bool(parameter, REQUIRED), nullableText(parameter, DEFAULT_VALUE), allowedValues,
                    nullableText(parameter, PATTERN), nullableText(parameter, RELATED_PARAMETER)));
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
                    MessageDigest.getInstance(FingerprintKeyCodec.HASH_ALGORITHM).digest(value.getBytes(StandardCharsets.UTF_8)));
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
