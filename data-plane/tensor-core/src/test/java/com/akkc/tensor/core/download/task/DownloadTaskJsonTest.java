package com.akkc.tensor.core.download.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DownloadTaskJsonTest {
    private final DownloadTaskJson json = new DownloadTaskJson();

    @Test
    void requestHashUsesCanonicalKeyOrder() {
        var first = new LinkedHashMap<String, Object>();
        first.put("ts_code", "000001.SZ");
        first.put("start_date", "20260901");
        first.put("end_date", "20260911");
        var second = new LinkedHashMap<String, Object>();
        second.put("end_date", "20260911");
        second.put("ts_code", "000001.SZ");
        second.put("start_date", "20260901");

        assertThat(json.requestHash(dataset(), DownloadMode.RANGE, first))
                .isEqualTo("c23121e069559decf27a30a0369c23dad4ba3f5c6888b728ff95832add5d0931")
                .isEqualTo(json.requestHash(dataset(), DownloadMode.RANGE, second));
    }

    @Test
    void requestHashChangesWithValueSourceAndMode() {
        String baseline = json.requestHash(dataset(), DownloadMode.RANGE, Map.of("aa", "one"));

        assertThat(json.requestHash(dataset(), DownloadMode.RANGE, Map.of("aa", "two"))).isNotEqualTo(baseline);
        assertThat(json.requestHash(DatasetKey.of(PluginId.of("fixture"), ApiName.of("daily_prices")),
                DownloadMode.RANGE, Map.of("aa", "one"))).isNotEqualTo(baseline);
        assertThat(json.requestHash(dataset(), DownloadMode.SINGLE, Map.of("aa", "one"))).isNotEqualTo(baseline);
    }

    @Test
    void taskParamsAllowExactlyEightKibAndRejectOneByteMore() {
        assertThat(json.writeTaskParams(Map.of("pp", "a".repeat(8183))))
                .hasSize(8192);
        assertThatThrownBy(() -> json.writeTaskParams(Map.of("pp", "a".repeat(8184))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchParamsUseSixteenKibUtf8Boundary() {
        assertThat(json.writeBatchParams(Map.of("pp", "中".repeat(5458) + "a")))
                .hasSize(5468);
        assertThatThrownBy(() -> json.writeBatchParams(Map.of("pp", "中".repeat(5458) + "aa")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsWhitespacePaddedDatabaseJsonBeforeApplyingCanonicalLimit() {
        String padded = " ".repeat(100_000) + "{\"zz\":\"ok\"}";

        assertThat(json.readTaskParams(padded)).containsExactly(Map.entry("zz", "ok"));
    }

    @Test
    void rejectsInputOverParsingLimitBeforeNormalization() {
        String oversized = " ".repeat(128 * 1024) + "{}";

        assertThatThrownBy(() -> json.readTaskParams(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid download task JSON");
    }

    @Test
    void rejectsNonStringNestedAndSecretParametersWithoutLeakingValues() {
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("aa", 1), Map.of("aa", true), Map.of("aa", List.of("private-value")),
                Map.of("aa", Map.of("bb", "private-value")), Map.of("access_token", "private-value"))) {
            assertThatThrownBy(() -> json.writeTaskParams(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid download task JSON")
                    .hasMessageNotContaining("private-value");
        }
    }

    @Test
    void invalidNullInputsUseThePublicBoundaryException() {
        assertThatThrownBy(() -> json.writeTaskParams(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.policySnapshot(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.requestHash(null, DownloadMode.SINGLE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsArbitraryObjectsWithoutInvokingTheirGetters() {
        var invoked = new AtomicBoolean();
        Object value = new Object() {
            @SuppressWarnings("unused")
            public String getSecret() {
                invoked.set(true);
                throw new AssertionError("getter must not run");
            }
        };

        assertThatThrownBy(() -> json.writeTaskParams(Map.of("aa", value)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid download task JSON");
        assertThat(invoked).isFalse();
    }

    @Test
    void rejectsDuplicateKeysTrailingTokensAndExcessiveDepth() {
        for (String invalid : List.of(
                "{\"aa\":\"one\",\"aa\":\"two\"}",
                "{\"aa\":\"one\"} {}",
                "[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]")) {
            assertThatThrownBy(() -> json.readTaskParams(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid download task JSON");
        }
    }

    @Test
    void singlePolicyHasOnlyVersionAndModeAndRejectsRangePromise() {
        assertThat(json.policySnapshot(DownloadMode.SINGLE, null))
                .isEqualTo("{\"mode\":\"SINGLE\",\"schemaVersion\":1}");
        assertThatThrownBy(() -> json.policySnapshot(DownloadMode.SINGLE, rangePolicy()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangePolicyRoundTripsCanonicallyAndRejectsUnknownFields() {
        String snapshot = json.policySnapshot(DownloadMode.RANGE, rangePolicy());

        assertThat(json.validatePolicySnapshot("  " + snapshot + "  ")).isEqualTo(snapshot);
        BatchDownloadDescriptor restored = json.readRangePolicy(snapshot);
        assertThat(restored.parameters()).extracting(ParameterDescriptor::name)
                .containsExactly("start_date", "end_date");
        assertThat(restored.parameters().getFirst().allowedValues()).containsExactly("A", "Z");
        assertThat(restored.startParameter()).isEqualTo("start_date");
        assertThat(restored.endParameter()).isEqualTo("end_date");
        assertThat(restored.policyVersion()).isEqualTo("v1");
        assertThat(snapshot).contains("\"allowedValues\":[\"A\",\"Z\"]")
                .contains("\"description\":null", "\"unavailableReason\":null");
        assertThatThrownBy(() -> json.validatePolicySnapshot(
                snapshot.substring(0, snapshot.length() - 1) + ",\"unknown\":1}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.readRangePolicy(json.policySnapshot(DownloadMode.SINGLE, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid download task JSON");
    }

    @Test
    void unsupportedRangePolicyRoundTripsExplicitNullSemantics() {
        var unsupported = new BatchDownloadDescriptor(List.of(), null, null, null, null, null, false,
                BatchDownloadDescriptor.Availability.UNSUPPORTED, "Not supported", "v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.UNKNOWN, null, null));

        String snapshot = json.policySnapshot(DownloadMode.RANGE, unsupported);

        assertThat(json.validatePolicySnapshot(snapshot)).isEqualTo(snapshot);
        assertThat(snapshot).contains("\"dateAxis\":null", "\"planningMode\":null", "\"startParameter\":null");
    }

    @Test
    void policySnapshotEnforcesSixteenKibCanonicalBoundary() {
        int overhead = json.policySnapshot(DownloadMode.RANGE, rangePolicy("x"))
                .getBytes(StandardCharsets.UTF_8).length - 1;
        int labelLength = 16 * 1024 - overhead;
        String exact = json.policySnapshot(DownloadMode.RANGE, rangePolicy("x".repeat(labelLength)));

        assertThat(exact).hasSize(16 * 1024);
        assertThat(json.readRangePolicy(" ".repeat(100_000) + exact).dateLabel()).hasSize(labelLength);
        assertThatThrownBy(() -> json.policySnapshot(
                DownloadMode.RANGE, rangePolicy("x".repeat(labelLength + 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.readRangePolicy(exact.replace(
                "\"dateLabel\":\"" + "x".repeat(labelLength) + "\"",
                "\"dateLabel\":\"" + "x".repeat(labelLength + 1) + "\"")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definitionHashIncludesStorageContractAndExcludesPresentationAndBatchSize() {
        var api = api("API display", rangePolicy().parameters());
        String baseline = json.definitionHash(api, definition("Dataset display", false, 100),
                DownloadMode.RANGE, rangePolicy());

        assertThat(json.definitionHash(api("Changed display", rangePolicy().parameters()),
                definition("Changed dataset display", false, 500), DownloadMode.RANGE, rangePolicy()))
                .isEqualTo(baseline);
        assertThat(json.definitionHash(api, definition("Dataset display", true, 100),
                DownloadMode.RANGE, rangePolicy())).isNotEqualTo(baseline);
        assertThat(json.definitionHash(api, definition("Dataset display", false, 100),
                DownloadMode.RANGE, rangePolicy(BatchDownloadDescriptor.DateAxis.REPORT_PERIOD)))
                .isNotEqualTo(baseline);
    }

    @Test
    void definitionHashRejectsMismatchedApiAndRangeContracts() {
        var otherApi = new ApiDescriptor(ApiName.of("other_api"), "Other", "prices", QueryMode.date_range,
                rangePolicy().parameters());
        assertThatThrownBy(() -> json.definitionHash(otherApi, definition("Dataset", false, 100),
                DownloadMode.RANGE, rangePolicy())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.definitionHash(api("API", rangePolicy().parameters()),
                definition("Dataset", false, 100), DownloadMode.SINGLE, rangePolicy()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definitionHashTracksBusinessKeyPolicyVersionCompletenessAndParameterOrder() {
        var dataset = definition("Dataset", false, 100);
        var range = rangePolicy();
        String baseline = json.definitionHash(api("API", range.parameters()), dataset, DownloadMode.RANGE, range);
        var fingerprint = new DatasetDefinition(dataset.datasetKey(), dataset.displayName(), dataset.category(),
                dataset.queryMode(), dataset.parameters(), dataset.tableName(), dataset.columns(),
                new BusinessKeyDefinition(BusinessKeyMode.FINGERPRINT, List.of("trade_date")),
                dataset.filters(), dataset.fixedColumn(), dataset.batchSize());
        assertThat(json.definitionHash(api("API", range.parameters()), fingerprint, DownloadMode.RANGE, range))
                .isNotEqualTo(baseline);
        var version = withPolicy(range.parameters(), "v2", range.completenessRule(), range.availability(), null);
        var rule = withPolicy(range.parameters(), "v1", new BatchDownloadDescriptor.CompletenessRule(
                BatchDownloadDescriptor.CompletenessRule.Kind.CONFIRMED_ROW_LIMIT, 4000L, "documented"),
                range.availability(), null);
        for (var changed : List.of(version, rule)) {
            assertThat(json.definitionHash(api("API", changed.parameters()), dataset, DownloadMode.RANGE, changed))
                    .isNotEqualTo(baseline);
        }
        var symbol = new ParameterDescriptor("symbol", "Symbol", null, ParameterType.TEXT, false,
                null, List.of(), null, null);
        var first = withPolicy(List.of(symbol, range.parameters().get(0), range.parameters().get(1)),
                "v1", range.completenessRule(), range.availability(), null);
        var last = withPolicy(List.of(range.parameters().get(0), range.parameters().get(1), symbol),
                "v1", range.completenessRule(), range.availability(), null);
        assertThat(json.definitionHash(api("API", first.parameters()), dataset, DownloadMode.RANGE, first))
                .isNotEqualTo(json.definitionHash(api("API", last.parameters()), dataset, DownloadMode.RANGE, last));
        assertThat(json.policySnapshot(DownloadMode.RANGE, first).indexOf("\"name\":\"symbol\""))
                .isLessThan(json.policySnapshot(DownloadMode.RANGE, first).indexOf("\"name\":\"start_date\""));
        var unavailable = withPolicy(range.parameters(), "v1", range.completenessRule(),
                BatchDownloadDescriptor.Availability.NEEDS_VERIFICATION, "Waiting for evidence");
        assertThat(json.definitionHash(api("API", range.parameters()), dataset, DownloadMode.RANGE, unavailable))
                .isEqualTo(baseline);
        assertThat(json.definitionHash(api("API", range.parameters()), dataset, DownloadMode.RANGE, rangePolicy("New label")))
                .isEqualTo(baseline);
    }

    @Test
    void damagedPolicyCannotAddPromisesOrChangeParameterTypes() {
        String snapshot = json.policySnapshot(DownloadMode.RANGE, rangePolicy());
        for (String invalid : List.of(
                snapshot.replace("\"kind\":\"CONFIRMED_ROW_LIMIT\"", "\"kind\":\"UNKNOWN\""),
                snapshot.replace("\"splittable\":true", "\"splittable\":\"true\""),
                snapshot.replace("\"rowLimit\":5000", "\"rowLimit\":5000.5"),
                snapshot.replace("\"defaultValue\":null", "\"defaultValue\":12"),
                snapshot.replace("\"startParameter\":\"start_date\"", "\"startParameter\":\"missing\""),
                snapshot.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                "{\"mode\":\"SINGLE\",\"schemaVersion\":1,\"splittable\":false}")) {
            assertThatThrownBy(() -> json.validatePolicySnapshot(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void damagedPolicyAlwaysUsesSanitizedBoundaryError() {
        String snapshot = json.policySnapshot(DownloadMode.RANGE, rangePolicy());
        for (String invalid : List.of(
                snapshot.replace("\"name\":\"start_date\"", "\"name\":\"PRIVATE-BAD-NAME\""),
                snapshot.replace("\"relatedParameter\":\"end_date\"",
                        "\"relatedParameter\":\"PRIVATE-BAD-RELATED\""),
                snapshot.replace("\"dateAxis\":\"TRADE_DATE\"", "\"dateAxis\":null"))) {
            assertThatThrownBy(() -> json.validatePolicySnapshot(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid download task JSON")
                    .hasNoCause()
                    .hasMessageNotContaining("PRIVATE");
        }
    }

    @Test
    void definitionHashSupportsUnavailableRangeWithoutDateSemantics() {
        var unsupported = new BatchDownloadDescriptor(List.of(), null, null, null, null, null, false,
                BatchDownloadDescriptor.Availability.UNSUPPORTED, "Not supported", "v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.UNKNOWN, null, null));
        var emptyApi = api("API", List.of());

        assertThat(json.definitionHash(emptyApi, definition("Dataset", false, 100),
                DownloadMode.RANGE, unsupported))
                .matches("[0-9a-f]{64}")
                .isEqualTo(json.definitionHash(emptyApi, definition("Changed presentation", false, 500),
                        DownloadMode.RANGE, unsupported));
    }

    private static BatchDownloadDescriptor withPolicy(List<ParameterDescriptor> parameters, String version,
            BatchDownloadDescriptor.CompletenessRule rule, BatchDownloadDescriptor.Availability availability,
            String reason) {
        return new BatchDownloadDescriptor(parameters, "start_date", "end_date",
                BatchDownloadDescriptor.DateAxis.TRADE_DATE, "Trade date",
                BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true, availability, reason, version, rule);
    }

    private static DatasetKey dataset() {
        return DatasetKey.of(PluginId.of("tushare"), ApiName.of("daily_prices"));
    }

    private static BatchDownloadDescriptor rangePolicy() {
        return rangePolicy(BatchDownloadDescriptor.DateAxis.TRADE_DATE);
    }

    private static BatchDownloadDescriptor rangePolicy(BatchDownloadDescriptor.DateAxis axis) {
        return rangePolicy("Trade date", axis);
    }

    private static BatchDownloadDescriptor rangePolicy(String label) {
        return rangePolicy(label, BatchDownloadDescriptor.DateAxis.TRADE_DATE);
    }

    private static BatchDownloadDescriptor rangePolicy(String label, BatchDownloadDescriptor.DateAxis axis) {
        return new BatchDownloadDescriptor(List.of(
                endpoint("start_date", "end_date"), endpoint("end_date", "start_date")),
                "start_date", "end_date", axis, label,
                BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true,
                BatchDownloadDescriptor.Availability.AVAILABLE, null, "v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.CONFIRMED_ROW_LIMIT, 5000L, "documented"));
    }

    private static ParameterDescriptor endpoint(String name, String related) {
        return new ParameterDescriptor(name, name, null, ParameterType.DATE_RANGE_MEMBER, true,
                null, List.of("Z", "A"), null, related);
    }

    private static ApiDescriptor api(String display, List<ParameterDescriptor> parameters) {
        return new ApiDescriptor(ApiName.of("daily_prices"), display, "prices", QueryMode.date_range, parameters);
    }

    private static DatasetDefinition definition(String display, boolean nullable, int batchSize) {
        return new DatasetDefinition(dataset(), display, "prices", QueryMode.date_range,
                rangePolicy().parameters(), TableName.from(dataset()),
                List.of(new ColumnDefinition("trade_date", "Trade date", LogicalType.DATE, nullable,
                        0, null, null, null, List.of(), false)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("trade_date")),
                List.of(), null, batchSize);
    }
}
