package com.akkc.tensor.core.retry;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaskParametersJson {
    private static final String ERROR = "Task parameters must be a JSON object";
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};

    private final ObjectMapper mapper;

    public TaskParametersJson() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .build();
        mapper = new ObjectMapper(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public String write(Map<String, Object> values) {
        try {
            return mapper.writeValueAsString(freeze(values));
        } catch (RuntimeException | com.fasterxml.jackson.core.JacksonException exception) {
            throw invalid();
        }
    }

    public Map<String, Object> read(String json) {
        try {
            if (json == null) {
                throw invalid();
            }
            Map<String, Object> values = mapper.readValue(json, OBJECT);
            if (values == null) {
                throw invalid();
            }
            return freeze(values);
        } catch (RuntimeException | com.fasterxml.jackson.core.JacksonException exception) {
            throw invalid();
        }
    }

    public Map<String, Object> freeze(Map<String, Object> values) {
        try {
            if (values == null) {
                throw invalid();
            }
            return freezeMap(values, new IdentityHashMap<>());
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    boolean sameValue(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            BigDecimal leftDecimal = decimal(leftNumber);
            BigDecimal rightDecimal = decimal(rightNumber);
            return leftDecimal != null && rightDecimal != null && leftDecimal.compareTo(rightDecimal) == 0;
        }
        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            if (!leftMap.keySet().equals(rightMap.keySet())) {
                return false;
            }
            return leftMap.keySet().stream().allMatch(key -> sameValue(leftMap.get(key), rightMap.get(key)));
        }
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            if (leftList.size() != rightList.size()) {
                return false;
            }
            for (int index = 0; index < leftList.size(); index++) {
                if (!sameValue(leftList.get(index), rightList.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return java.util.Objects.equals(left, right);
    }

    private static Map<String, Object> freezeMap(Map<?, ?> values, IdentityHashMap<Object, Boolean> active) {
        enter(values, active);
        try {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw invalid();
                }
                copy.put(key, freezeValue(entry.getValue(), active));
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            active.remove(values);
        }
    }

    private static List<Object> freezeList(List<?> values, IdentityHashMap<Object, Boolean> active) {
        enter(values, active);
        try {
            ArrayList<Object> copy = new ArrayList<>(values.size());
            for (Object value : values) {
                copy.add(freezeValue(value, active));
            }
            return Collections.unmodifiableList(copy);
        } finally {
            active.remove(values);
        }
    }

    private static Object freezeValue(Object value, IdentityHashMap<Object, Boolean> active) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) {
                throw invalid();
            }
            return new BigDecimal(number.toString());
        }
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) {
                throw invalid();
            }
            return BigDecimal.valueOf(number);
        }
        if (value instanceof Map<?, ?> map) {
            return freezeMap(map, active);
        }
        if (value instanceof List<?> list) {
            return freezeList(list, active);
        }
        throw invalid();
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> active) {
        if (active.put(value, Boolean.TRUE) != null) {
            throw invalid();
        }
    }

    private static BigDecimal decimal(Number value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        if (value instanceof Float number && Float.isFinite(number)) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof Double number && Double.isFinite(number)) {
            return BigDecimal.valueOf(number);
        }
        return null;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(ERROR);
    }
}
