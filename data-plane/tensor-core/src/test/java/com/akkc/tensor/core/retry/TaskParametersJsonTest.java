package com.akkc.tensor.core.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskParametersJsonTest {
    private static final String ERROR = "Task parameters must be a JSON object";
    private final TaskParametersJson json = new TaskParametersJson();

    @Test
    void roundTripsObjectsWithoutLosingJsonTypesOrPrecision() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("中文", "引号\"和反斜线\\");
        nested.put("integer", new BigInteger("123456789012345678901234567890"));
        nested.put("decimal", new BigDecimal("1.230000000000000000000001"));
        nested.put("array", List.of(true, new BigInteger("42"), new BigDecimal("1.25")));
        nested.put("nothing", null);

        Map<String, Object> read = json.read(json.write(nested));

        assertThat(read).isEqualTo(nested);
        assertThat(json.read("{}")).isEmpty();
        assertThat(read.get("integer")).isInstanceOf(BigInteger.class);
        assertThat(read.get("decimal")).isInstanceOf(BigDecimal.class);
    }

    @Test
    void rejectsNonObjectInvalidDuplicateAndTrailingJsonWithOneSafeFailure() {
        List<String> invalid = new ArrayList<>(List.of(
                "null", "1", "true", "\"text\"", "[]", "{", "{\"a\":1,\"a\":2}",
                "{\"nested\":{\"a\":1,\"a\":2}}", "{} {}"));
        invalid.add(null);

        for (String value : invalid) {
            assertSafeFailure(() -> json.read(value));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void freezeCopiesRecursivelyCanonicalizesNumbersAndAllowsNull() {
        List<Object> inner = new ArrayList<>();
        inner.add(1);
        inner.add(1.5d);
        inner.add(null);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("inner", inner);

        Map<String, Object> frozen = json.freeze(source);
        inner.set(0, 9);
        source.put("later", "change");

        assertThat(frozen).containsOnlyKeys("inner");
        assertThat((List<Object>) frozen.get("inner"))
                .containsExactly(new BigInteger("1"), new BigDecimal("1.5"), null);
        assertThatThrownBy(() -> frozen.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        List<Object> frozenInner = (List<Object>) frozen.get("inner");
        assertThatThrownBy(() -> frozenInner.add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsUnsupportedValuesNonStringKeysNonFiniteNumbersAndCycles() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<String, Object> nonStringKey = (Map) Map.of(1, "value");

        List<Map<String, Object>> invalid = List.of(
                Map.of("pojo", Instant.EPOCH),
                Map.of("nan", Double.NaN),
                Map.of("infinity", Float.POSITIVE_INFINITY),
                nonStringKey,
                cyclic);

        invalid.forEach(value -> assertSafeFailure(() -> json.freeze(value)));
        assertSafeFailure(() -> json.write(Map.of("pojo", Instant.EPOCH)));
        assertSafeFailure(() -> json.freeze(null));
    }

    @Test
    void semanticComparisonIgnoresObjectOrderAndDecimalScaleButKeepsArrayOrderAndTypes() {
        assertThat(json.sameValue(
                Map.of("a", new BigInteger("1"), "b", List.of(new BigDecimal("1.2300"), true)),
                Map.of("b", List.of(new BigDecimal("1.23"), true), "a", new BigDecimal("1.0"))))
                .isTrue();
        assertThat(json.sameValue(List.of(1, 2), List.of(2, 1))).isFalse();
        assertThat(json.sameValue(new BigInteger("1"), "1")).isFalse();
        assertThat(json.sameValue(new BigDecimal("1.0000000000000000001"), new BigDecimal("1"))).isFalse();
    }

    private static void assertSafeFailure(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(ERROR)
                .hasNoCause();
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
