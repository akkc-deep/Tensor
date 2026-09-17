package com.akkc.tensor.core.integrity;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Stable, exact JSON for immutable capability snapshots and stored reports. */
public final class IntegrityCheckJson {
    private static final Set<String> SECRETS = Set.of("token", "accesstoken", "refreshtoken", "authorization",
            "password", "secret", "apikey", "credentials", "rawresponse", "stacktrace");
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public record ApiSnapshot(DatasetDefinition definition, IntegrityDescriptor descriptor,
            List<DatasetDefinition> referenceDefinitions, List<IntegrityRuleDescriptor> coreRules) {
        public ApiSnapshot {
            Objects.requireNonNull(definition, "definition");
            referenceDefinitions = List.copyOf(referenceDefinitions);
            coreRules = List.copyOf(coreRules);
            if (descriptor != null && !definition.datasetKey().equals(descriptor.datasetKey()))
                throw new IllegalArgumentException("Descriptor does not match definition");
        }
    }

    public String capabilitySnapshot(PluginId pluginId, List<ApiSnapshot> apis) {
        Objects.requireNonNull(pluginId, "pluginId");
        apis = List.copyOf(apis);
        var keys = new HashSet<DatasetKey>();
        var descriptors = new ArrayList<IntegrityDescriptor>();
        for (var api : apis) {
            var key = api.definition().datasetKey();
            if (!key.pluginId().equals(pluginId) || !keys.add(key))
                throw new IllegalArgumentException("Duplicate or foreign API");
            var definitions = new HashMap<DatasetKey, DatasetDefinition>();
            definitions.put(key, api.definition());
            for (var reference : api.referenceDefinitions()) {
                var old = definitions.putIfAbsent(reference.datasetKey(), reference);
                if (old != null && !old.equals(reference)) throw new IllegalArgumentException("Conflicting definitions");
            }
            var descriptor = api.descriptor();
            if (descriptor != null) {
                IntegrityContracts.validateDescriptor(descriptor, definitions);
                descriptors.add(descriptor);
            }
            boolean stock = descriptor != null && descriptor.scopeKind() != IntegrityDescriptor.ScopeKind.NON_STOCK;
            var expected = stock ? IntegrityContracts.coreRules(api.definition()) : List.<IntegrityRuleDescriptor>of();
            var actual = new HashMap<String, IntegrityRuleDescriptor>();
            for (var rule : api.coreRules())
                if (actual.putIfAbsent(rule.ruleId(), rule) != null) throw new IllegalArgumentException("Duplicate core rule");
            if (actual.size() != expected.size()) throw new IllegalArgumentException("Missing core rules");
            for (var required : expected) {
                var rule = actual.get(required.ruleId());
                if (rule == null || rule.dimension() != required.dimension()
                        || !rule.requiredColumns().equals(required.requiredColumns()) || !rule.dependencies().isEmpty())
                    throw new IllegalArgumentException("Invalid core rule declaration");
            }
        }
        IntegrityContracts.validatePlugin(descriptors);
        return write(Map.of("schemaVersion", 1, "pluginId", pluginId, "apis", apis));
    }

    public String capabilityHash(PluginId pluginId, List<ApiSnapshot> apis) {
        return sha256(capabilitySnapshot(pluginId, apis));
    }
    public String definitionHash(DatasetDefinition definition) { return sha256(write(Objects.requireNonNull(definition))); }
    public String requestHash(Map<String, Object> request) { return sha256(write(Objects.requireNonNull(request))); }

    public String unitKey(ApiName apiName, String symbol) {
        if (apiName == null) throw new IllegalArgumentException("Missing integrity API");
        return sha256(write(Arrays.asList(apiName, symbol)));
    }

    public String writeDocument(Object value) {
        var payload = canonical(value, 1);
        if (!(payload instanceof Map<?, ?> || payload instanceof List<?>))
            throw new IllegalArgumentException("Invalid integrity document payload");
        return write(Map.of("schemaVersion", 1, "payload", payload));
    }

    public JsonNode readDocument(String value) {
        var document = readValue(value);
        if (!document.isObject() || document.size() != 2
                || !document.has("schemaVersion") || !document.get("schemaVersion").isInt()
                || document.get("schemaVersion").intValue() != 1 || !document.has("payload")
                || !(document.get("payload").isObject() || document.get("payload").isArray()))
            throw new IllegalArgumentException("Invalid integrity document");
        return document.get("payload");
    }

    public JsonNode readValue(String value) {
        if (value == null) throw new IllegalArgumentException("Invalid integrity JSON");
        try (var parser = mapper.createParser(value)) {
            JsonNode node = mapper.readTree(parser);
            if (node == null || parser.nextToken() != null)
                throw new IllegalArgumentException("Invalid integrity JSON");
            validateTree(node, 0);
            return node;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid integrity JSON");
        }
    }

    public IntegrityScope readScope(JsonNode value) {
        return decode(value, () -> {
            object(value, "acceptedAt", "datasetKey", "endDate", "snapshotStartedAt", "startDate", "symbol");
            var scope = new IntegrityScope(datasetKey(value.get("datasetKey")), nullableText(value.get("symbol")),
                    LocalDate.parse(text(value.get("startDate"))), LocalDate.parse(text(value.get("endDate"))),
                    Instant.parse(text(value.get("acceptedAt"))), nullableInstant(value.get("snapshotStartedAt")));
            exact(value, scope);
            return scope;
        });
    }

    public IntegrityDescriptor readDescriptor(JsonNode value) {
        if (value != null && value.isNull()) return null;
        return decode(value, () -> {
            object(value, "capabilityVersion", "datasetKey", "dateField", "dateLabel", "dependencies", "limitations",
                    "marketZone", "rules", "scopeKind", "symbolField");
            var descriptor = new IntegrityDescriptor(datasetKey(value.get("datasetKey")),
                    enumeration(value.get("scopeKind"), IntegrityDescriptor.ScopeKind.class),
                    nullableText(value.get("symbolField")), nullableText(value.get("dateField")), text(value.get("dateLabel")),
                    ZoneId.of(text(value.get("marketZone"))), text(value.get("capabilityVersion")),
                    dependencies(value.get("dependencies")), rules(value.get("rules")), strings(value.get("limitations")));
            exact(value, descriptor);
            return descriptor;
        });
    }

    private DatasetKey datasetKey(JsonNode value) {
        object(value, "apiName", "pluginId");
        return new DatasetKey(new PluginId(text(value.get("pluginId"))), new ApiName(text(value.get("apiName"))));
    }

    private List<IntegrityDependency> dependencies(JsonNode value) {
        array(value);
        var result = new ArrayList<IntegrityDependency>();
        value.forEach(item -> {
            object(item, "columns", "datasetKey", "purpose");
            result.add(new IntegrityDependency(datasetKey(item.get("datasetKey")), strings(item.get("columns")),
                    text(item.get("purpose"))));
        });
        return List.copyOf(result);
    }

    private List<IntegrityRuleDescriptor> rules(JsonNode value) {
        array(value);
        var result = new ArrayList<IntegrityRuleDescriptor>();
        value.forEach(item -> {
            object(item, "dependencies", "description", "dimension", "displayName", "requiredColumns", "ruleId", "version");
            result.add(new IntegrityRuleDescriptor(text(item.get("ruleId")), text(item.get("version")),
                    text(item.get("displayName")), enumeration(item.get("dimension"), IntegrityRuleDescriptor.Dimension.class),
                    strings(item.get("requiredColumns")), dependencies(item.get("dependencies")), text(item.get("description"))));
        });
        return List.copyOf(result);
    }

    private static List<String> strings(JsonNode value) {
        array(value);
        var result = new ArrayList<String>();
        value.forEach(item -> result.add(text(item)));
        return List.copyOf(result);
    }

    private static void object(JsonNode value, String... fields) {
        if (value == null || !value.isObject() || value.size() != fields.length) throw invalidJson();
        for (var field : fields) if (!value.has(field)) throw invalidJson();
    }

    private static void array(JsonNode value) {
        if (value == null || !value.isArray()) throw invalidJson();
    }

    private static String text(JsonNode value) {
        if (value == null || !value.isTextual()) throw invalidJson();
        return value.textValue();
    }

    private static String nullableText(JsonNode value) {
        if (value != null && value.isNull()) return null;
        return text(value);
    }

    private static Instant nullableInstant(JsonNode value) {
        if (value != null && value.isNull()) return null;
        return Instant.parse(text(value));
    }

    private static <E extends Enum<E>> E enumeration(JsonNode value, Class<E> type) {
        return Enum.valueOf(type, text(value));
    }

    private void exact(JsonNode value, Object decoded) {
        if (!readValue(write(decoded)).equals(value)) throw invalidJson();
    }

    private static <T> T decode(JsonNode value, Supplier<T> decoder) {
        try {
            return decoder.get();
        } catch (RuntimeException failure) {
            throw new StoredJsonException();
        }
    }

    private static IllegalArgumentException invalidJson() {
        return new IllegalArgumentException("Invalid integrity JSON");
    }

    private static final class StoredJsonException extends TensorException {
        StoredJsonException() { super(ErrorCode.QUERY_FAILED, ErrorCode.QUERY_FAILED.message()); }
    }

    private static void validateTree(JsonNode node, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Integrity JSON is too deeply nested");
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                validateField(field.getKey());
                validateTree(field.getValue(), depth + 1);
            });
        } else if (node.isArray()) {
            node.forEach(item -> validateTree(item, depth + 1));
        } else if (!(node.isNull() || node.isTextual() || node.isBoolean() || node.isInt())) {
            throw new IllegalArgumentException("Unsupported integrity JSON value");
        }
    }

    private static void validateField(String name) {
        if (SECRETS.contains(name.replaceAll("[-_]", "").toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Invalid or sensitive integrity JSON field");
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(canonical(value, 0));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid integrity JSON");
        }
    }

    private Object canonical(Object value, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Integrity JSON is too deeply nested");
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer) return value;
        if (value instanceof Long number) return number.toString();
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof LocalDate || value instanceof Instant || value instanceof YearMonth) return value.toString();
        if (value instanceof ZoneId zone) return zone.getId();
        if (value instanceof Enum<?> enumeration) return enumeration.name();
        if (value instanceof PluginId id) return id.value();
        if (value instanceof ApiName name) return name.value();
        if (value instanceof TableName name) return name.value();
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, item) -> {
                if (!(key instanceof String name))
                    throw new IllegalArgumentException("Invalid or sensitive integrity JSON field");
                validateField(name);
                result.put(name, canonical(item, depth + 1));
            });
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(item -> canonical(item, depth + 1)).toList();
        if (value.getClass().isRecord() && value.getClass().getPackageName().startsWith("com.akkc.tensor.")) {
            var fields = new LinkedHashMap<String, Object>();
            try {
                for (var component : value.getClass().getRecordComponents())
                    fields.put(component.getName(), component.getAccessor().invoke(value));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalArgumentException("Invalid integrity record");
            }
            if (value instanceof IntegrityStatistics statistics) fields.put("coverageRate", statistics.coverageRate());
            if (value instanceof IntegrityUnitResult result) fields.put("overallStatus", result.overallStatus());
            return canonical(fields, depth + 1);
        }
        throw new IllegalArgumentException("Unsupported integrity JSON value");
    }

    private static String sha256(String json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
