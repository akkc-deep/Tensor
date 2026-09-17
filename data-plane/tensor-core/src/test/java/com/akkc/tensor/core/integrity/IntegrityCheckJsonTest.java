package com.akkc.tensor.core.integrity;

import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class IntegrityCheckJsonTest {
    private final IntegrityCheckJson json = new IntegrityCheckJson();

    @Test
    void unitKeySeparatesNullFromLiteralAndUsesOrderedCanonicalArray() {
        assertThat(json.unitKey(ApiName.of("daily"), null))
                .isEqualTo("f08cc0f7e26fad0bf904eeccee6c76d334d92e4f96d576041714326db21d81ca");
        assertThat(json.unitKey(ApiName.of("daily"), "null"))
                .isEqualTo("8d7b929bf6acc1bbab62c2b08721d0aea7e39d9b68ce9b6bdc6c1434473023dc");
        assertThat(json.unitKey(ApiName.of("daily"), "000001.SZ"))
                .isEqualTo("b6518e2be9a09bc34a923bd405220fe54883afb3a28ed0e697f42824d7cf4456");
        assertThatThrownBy(() -> json.unitKey(null, "AA")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storagePreservesExactCountsDecimalsNullAndNanosecondInstant() {
        var values = new LinkedHashMap<String, Object>();
        values.put("count", 9007199254740993L);
        values.put("decimal", new BigDecimal("1.000000000000000001"));
        values.put("unknown", null);
        values.put("at", Instant.parse("2026-09-16T02:03:04.123456789Z"));
        values.put("date", LocalDate.parse("2026-09-16"));
        var stored = json.writeDocument(values);
        assertThat(json.readValue(stored).get("schemaVersion").intValue()).isEqualTo(1);
        var read = json.readDocument(stored);
        assertThat(read.get("count").isTextual()).isTrue();
        assertThat(read.get("count").textValue()).isEqualTo("9007199254740993");
        assertThat(read.get("decimal").textValue()).isEqualTo("1.000000000000000001");
        assertThat(read.get("unknown").isNull()).isTrue();
        assertThat(read.get("at").textValue()).isEqualTo("2026-09-16T02:03:04.123456789Z");
        assertThat(read.get("date").textValue()).isEqualTo("2026-09-16");
    }

    @Test
    void supportsRecordAndArrayDocumentsAndRawIssueStructures() {
        var date = LocalDate.parse("2026-01-01");
        assertThat(json.readDocument(json.writeDocument(new IntegrityDateRange(date, date)))
                .get("startDate").textValue()).isEqualTo("2026-01-01");
        assertThat(json.readDocument(json.writeDocument(List.of(Map.of("id", 1))))).hasSize(1);
        assertThat(json.readValue(json.write(Map.of("ann_date", date))).get("ann_date").textValue())
                .isEqualTo("2026-01-01");
        assertThat(json.readValue(json.write(List.of()))).isEmpty();
        for (Object value : Arrays.asList(null, "scalar", 1, true))
            assertThatThrownBy(() -> json.writeDocument(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leavesExistingCanonicalBytesAndRequestHashUnchanged() {
        var input = new LinkedHashMap<String, Object>();
        input.put("c", null);
        input.put("b", new BigDecimal("1.000000000000000001"));
        input.put("a", 9007199254740993L);
        assertThat(json.write(input)).isEqualTo("{\"a\":\"9007199254740993\",\"b\":\"1.000000000000000001\",\"c\":null}");
        assertThat(json.requestHash(input)).isEqualTo("0ab22309cb4951550ab52011f7d8eab2d1d6c3d27e3a87ceafb575eac9da3dc1");
    }

    @Test
    void retainsDefinitionAndCapabilityHashesFromThePreStorageEncoder() {
        var key = new DatasetKey(new PluginId("fixture"), new ApiName("daily"));
        var definition = new DatasetDefinition(key, "Daily", "fixture", QueryMode.trade_date,
                List.of(), TableName.from(key), List.of(
                new ColumnDefinition("symbol", "Symbol", LogicalType.STRING, false, 0, 255, null, null, List.of(), false),
                new ColumnDefinition("day", "Day", LogicalType.DATE, false, 1, null, null, null, List.of(), false)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("symbol", "day")), List.of(), null);
        // Captured with the pre-T05 encoder from the existing Git index, before changing it.
        assertThat(json.definitionHash(definition))
                .isEqualTo("7b49233fbf02b273d3f5bbbb083f7868f6306ce2b9e19d71f38fca53bdef849c");
        assertThat(json.capabilityHash(key.pluginId(), List.of(
                new IntegrityCheckJson.ApiSnapshot(definition, null, List.of(), List.of()))))
                .isEqualTo("8573272abd7ea4777dd7c0e23782fb1a74a1015ad4693cc3812de11857f3d028");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{}", "{\"schemaVersion\":2,\"payload\":{}}",
            "{\"schemaVersion\":\"1\",\"payload\":{}}", "{\"schemaVersion\":1,\"payload\":null}",
            "{\"schemaVersion\":1,\"payload\":5}", "{\"schemaVersion\":1,\"payload\":{},\"extra\":0}",
            "{\"schemaVersion\":1,\"schemaVersion\":1,\"payload\":{}}",
            "{\"schemaVersion\":1,\"payload\":{\"a\":1,\"a\":2}}",
            "{\"schemaVersion\":1,\"payload\":{}} {}"})
    void rejectsMalformedOrUnknownDocumentInsteadOfReturningEmpty(String document) {
        assertThatThrownBy(() -> json.readDocument(document)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nested\":{\"access_token\":\"secret-sentinel\"}}",
            "{\"rawResponse\":\"secret-sentinel\"}", "{\"STACK_TRACE\":\"secret-sentinel\"}",
            "{\"a\":0.1}", "{\"a\":9007199254740993}", "{\"a\":1,\"a\":2}", "{} []", "secret-sentinel"})
    void strictRawReaderRejectsUnsafeOrInexactDataWithSafeError(String value) {
        assertThatThrownBy(() -> json.readValue(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-sentinel").hasNoCause();
    }

    @Test
    void enforcesDepthOnReadAndWriteAndRejectsSensitiveInput() {
        String deepest = "[".repeat(32) + "0" + "]".repeat(32);
        assertThat(json.readValue(deepest).isArray()).isTrue();
        assertThatThrownBy(() -> json.readValue("[" + deepest + "]"))
                .isInstanceOf(IllegalArgumentException.class);
        Object value = Map.of("id", 1);
        for (int i = 0; i < 32; i++) value = List.of(value);
        Object deepValue = value;
        assertThatThrownBy(() -> json.writeDocument(deepValue)).isInstanceOf(IllegalArgumentException.class);
        for (var unsafe : List.of(Map.of("token", "secret-sentinel"), Map.of("rate", 0.1d), Map.of("rate", 0.1f)))
            assertThatThrownBy(() -> json.writeDocument(unsafe)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("secret-sentinel");
        assertThatThrownBy(() -> json.readValue(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void strictlyDecodesSavedScopeAndDescriptorWithDefensiveCollections() {
        var scope = new IntegrityScope(new DatasetKey(new PluginId("fixture"), new ApiName("daily")), "000001.SZ",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"),
                Instant.parse("2026-09-16T02:03:04.123456789Z"), Instant.parse("2026-09-16T02:04:05.987654321Z"));
        var dependency = new IntegrityDependency(new DatasetKey(new PluginId("fixture"), new ApiName("calendar")),
                List.of("exchange", "trade_date"), "Trading calendar");
        var rule = new IntegrityRuleDescriptor("fixture.coverage", "v2", "Coverage",
                IntegrityRuleDescriptor.Dimension.COVERAGE, List.of("symbol", "day"), List.of(dependency), "Saved rule");
        var descriptor = new IntegrityDescriptor(scope.datasetKey(), IntegrityDescriptor.ScopeKind.STOCK_DATE,
                "symbol", "day", "Trading day", ZoneId.of("Asia/Shanghai"), "cap-v2",
                List.of(dependency), List.of(rule), List.of("Saved limitation"));

        assertThat(json.readScope(json.readValue(json.write(scope)))).isEqualTo(scope);
        var decoded = json.readDescriptor(json.readValue(json.write(descriptor)));
        assertThat(decoded).isEqualTo(descriptor);
        assertThatThrownBy(() -> decoded.dependencies().add(dependency)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> decoded.rules().getFirst().requiredColumns().add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(json.readDescriptor(com.fasterxml.jackson.databind.node.NullNode.instance)).isNull();
    }

    @Test
    void rejectsNonCanonicalOrMalformedSavedScopeAndDescriptorWithoutLeakingValues() {
        var scope = json.readValue(json.write(new IntegrityScope(
                new DatasetKey(new PluginId("fixture"), new ApiName("daily")), "secret-sentinel",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"),
                Instant.parse("2026-09-16T02:03:04Z"), null)));
        var descriptor = json.readValue(json.write(new IntegrityDescriptor(
                new DatasetKey(new PluginId("fixture"), new ApiName("daily")), IntegrityDescriptor.ScopeKind.NON_STOCK,
                null, null, "Not dated", ZoneId.of("UTC"), "v1", List.of(), List.of(), List.of())));
        var bad = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        bad.add(((ObjectNode) scope.deepCopy()).without("acceptedAt"));
        bad.add(((ObjectNode) scope.deepCopy()).put("extra", true));
        bad.add(((ObjectNode) scope.deepCopy()).put("startDate", "2026-02-30"));
        bad.add(((ObjectNode) scope.deepCopy()).put("acceptedAt", "2026-09-16T02:03:04"));
        bad.add(((ObjectNode) scope.deepCopy()).put("symbol", 1));
        for (var value : bad)
            assertThatThrownBy(() -> json.readScope(value)).isInstanceOfSatisfying(TensorException.class,
                    failure -> assertThat(failure.code()).isEqualTo(ErrorCode.QUERY_FAILED))
                    .hasMessage(ErrorCode.QUERY_FAILED.message()).hasMessageNotContaining("secret-sentinel").hasNoCause();

        bad.clear();
        bad.add(((ObjectNode) descriptor.deepCopy()).without("limitations"));
        bad.add(((ObjectNode) descriptor.deepCopy()).put("extra", true));
        bad.add(((ObjectNode) descriptor.deepCopy()).put("scopeKind", "STOCK"));
        bad.add(((ObjectNode) descriptor.deepCopy()).put("marketZone", "private/secret-sentinel"));
        bad.add(((ObjectNode) descriptor.deepCopy()).put("dependencies", "wrong"));
        for (var value : bad)
            assertThatThrownBy(() -> json.readDescriptor(value)).isInstanceOfSatisfying(TensorException.class,
                    failure -> assertThat(failure.code()).isEqualTo(ErrorCode.QUERY_FAILED))
                    .hasMessage(ErrorCode.QUERY_FAILED.message()).hasMessageNotContaining("secret-sentinel").hasNoCause();
    }
}
