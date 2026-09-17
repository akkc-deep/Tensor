package com.akkc.tensor.core.integrity;

import static com.akkc.tensor.plugin.api.integrity.IntegrityStatus.FAIL;
import static com.akkc.tensor.plugin.api.integrity.IntegrityStatus.NOT_APPLICABLE;
import static com.akkc.tensor.plugin.api.integrity.IntegrityStatus.PASS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.integrity.IntegrityReadRepository.TargetBatch;
import com.akkc.tensor.core.integrity.rules.BusinessKeyRule;
import com.akkc.tensor.core.integrity.rules.RequiredFieldsRule;
import com.akkc.tensor.core.integrity.rules.SourceIdentityRule;
import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.integrity.IntegrityContext;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssue;
import com.akkc.tensor.plugin.api.integrity.IntegrityReadRequest;
import com.akkc.tensor.plugin.api.integrity.IntegrityRule;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleResult;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatistics;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class IntegrityCoreRulesTest {
    private static final DatasetKey KEY = DatasetKey.of(PluginId.of("reader"), ApiName.of("daily"));
    private static final LocalDate DATE = LocalDate.of(2026, 1, 2);
    private static final IntegrityScope SCOPE = new IntegrityScope(KEY, "000001.SZ", DATE, DATE,
            Instant.parse("2026-09-16T00:00:00Z"), Instant.parse("2026-09-16T00:00:01Z"));
    private static final IntegrityContext UNUSED_CONTEXT = new IntegrityContext() {
        @Override public void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> rows) {
            throw new AssertionError("core rules must not scan");
        }
        @Override public IntegrityStatistics compare(com.akkc.tensor.plugin.api.integrity.IntegrityExpectedKeys expected) {
            throw new AssertionError("core rules must not compare");
        }
    };

    @Test
    void businessKeysNormalizeExactTypesAndRejectInexactExpectedShapes() {
        DatasetDefinition definition = definition(BusinessKeyMode.COMPOSITE,
                List.of("ts_code", "ann_date", "amount", "sequence", "kind"));
        IntegrityBusinessKeys keys = new IntegrityBusinessKeys(definition);
        Map<String, Object> expected = row(
                "kind", " A ", "sequence", new BigInteger("9007199254740993"),
                "amount", new BigDecimal("1.0"), "ann_date", DATE, "ts_code", " 000001.SZ ");

        IntegrityBusinessKeys.Key normalized = keys.normalize(expected, true);

        assertThat(normalized.values()).containsExactly("000001.SZ", DATE,
                new BigDecimal("1.00"), 9007199254740993L, "A");
        assertThat(normalized.fields()).containsExactlyEntriesOf(row(
                "ts_code", "000001.SZ", "ann_date", DATE, "amount", new BigDecimal("1.00"),
                "sequence", 9007199254740993L, "kind", "A"));
        assertThatIllegalArgumentException().isThrownBy(() -> keys.normalize(
                row("ts_code", "000001.SZ", "ann_date", DATE, "amount", "1.00",
                        "sequence", 1L, "kind", "A", "extra", "x"), true));
        assertThatIllegalArgumentException().isThrownBy(() -> keys.normalize(
                row("ts_code", "000001.SZ", "ann_date", DATE, "amount", "1.001",
                        "sequence", 1L, "kind", "A"), true));
        assertThatIllegalArgumentException().isThrownBy(() -> keys.normalize(
                row("ts_code", "000001.SZ", "ann_date", DATE, "amount", 1,
                        "sequence", 1.5d, "kind", "A"), true));
    }

    @Test
    void fingerprintNormalizationAllowsNullableInputsButRequiresMatchingPhysicalHash() {
        DatasetDefinition definition = fingerprintDefinition();
        IntegrityBusinessKeys keys = new IntegrityBusinessKeys(definition);
        Map<String, Object> logical = row("ts_code", "000001.SZ", "amount", null);
        String hash = new FingerprintKeyCodec().sha256(List.of("ts_code", "amount"),
                row("ts_code", "000001.SZ", "amount", null));

        Map<String, Object> actual = row("ts_code", "000001.SZ", "amount", null,
                DatasetFields.BUSINESS_KEY, hash, "payload", "ignored");
        assertThat(keys.normalize(actual, false).fields()).containsExactlyEntriesOf(logical);
        assertThatIllegalArgumentException().isThrownBy(() -> keys.normalize(logical, false));
        assertThatIllegalArgumentException().isThrownBy(() -> keys.normalize(
                row("ts_code", "000001.SZ", "amount", null, DatasetFields.BUSINESS_KEY, "0".repeat(64)), false));
        assertThatIllegalArgumentException().isThrownBy(() -> keys.normalize(
                row("ts_code", "000001.SZ", "amount", null, DatasetFields.BUSINESS_KEY, hash), true));
    }

    @Test
    void invalidKeyLocationsPreserveDistinctRepresentableValuesAndMissingness() {
        DatasetDefinition definition = definition(BusinessKeyMode.COMPOSITE, List.of("kind", "sequence"));
        IntegrityBusinessKeys keys = new IntegrityBusinessKeys(definition);

        assertThat(keys.locatableFields(row("kind", "X", "sequence", 1L)))
                .containsExactlyEntriesOf(row("kind", "X", "sequence", 1L));
        assertThat(keys.locatableFields(row("kind", "Y", "sequence", 1L)))
                .containsExactlyEntriesOf(row("kind", "Y", "sequence", 1L));
        assertThat(keys.locatableFields(row("kind", "X")))
                .containsExactlyEntriesOf(row("kind", "X")).doesNotContainKey("sequence");
        assertThat(keys.locatableFields(row("kind", "X", "sequence", null)))
                .containsEntry("sequence", null);
    }

    @Test
    void requiredFieldsReportsEachMissingNonNullableFieldAndIgnoresNullableFields() {
        DatasetDefinition definition = definition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "ann_date"));
        Map<String, Object> actual = baseRow();
        actual.put("required_name", "  ");
        actual.put("note", null);
        actual.put("amount", null);
        List<IntegrityIssue> issues = new ArrayList<>();

        IntegrityRuleResult result = new RequiredFieldsRule(definition, batches(actual), () -> {}, "ann_date")
                .evaluate(SCOPE, UNUSED_CONTEXT, issues::add);

        assertThat(result.status()).isEqualTo(FAIL);
        assertThat(result.reasonCode()).isEqualTo("REQUIRED_FIELD_MISSING");
        assertThat(result.statistics().requiredFieldIssueCount()).isEqualTo(1L);
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.type()).isEqualTo(IntegrityIssue.Type.REQUIRED_FIELD_MISSING);
            assertThat(issue.field()).isEqualTo("required_name");
            assertThat(issue.dateField()).isEqualTo("ann_date");
            assertThat(issue.date()).isEqualTo(DATE);
            assertThat(issue.businessKey()).containsEntry("ts_code", "000001.SZ").containsEntry("ann_date", DATE);
        });
    }

    @Test
    void businessKeyRuleLocatesInvalidFieldFingerprintMismatchAndNormalizedCollision() {
        DatasetDefinition composite = definition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "ann_date"));
        Map<String, Object> missing = baseRow();
        missing.put("ts_code", "  ");
        Map<String, Object> first = baseRow();
        Map<String, Object> collision = baseRow();
        collision.put("ts_code", " 000001.SZ ");
        List<IntegrityIssue> issues = new ArrayList<>();

        IntegrityRuleResult result = new BusinessKeyRule(composite,
                batches(missing, first, collision), () -> {}, "ann_date")
                .evaluate(SCOPE, UNUSED_CONTEXT, issues::add);

        assertThat(result.status()).isEqualTo(FAIL);
        assertThat(issues).extracting(IntegrityIssue::reasonCode)
                .containsExactly("KEY_FIELD_MISSING", "NORMALIZED_KEY_COLLISION");
        assertThat(issues).extracting(IntegrityIssue::field).containsExactly("ts_code", null);

        DatasetDefinition fingerprint = fingerprintDefinition();
        Map<String, Object> damaged = row("ts_code", "000001.SZ", "amount", new BigDecimal("1.00"),
                DatasetFields.BUSINESS_KEY, "0".repeat(64), DatasetFields.SOURCE_PLUGIN, "reader",
                DatasetFields.SOURCE_API, "daily");
        List<IntegrityIssue> fingerprintIssues = new ArrayList<>();
        new BusinessKeyRule(fingerprint, batches(damaged)).evaluate(SCOPE, UNUSED_CONTEXT, fingerprintIssues::add);
        assertThat(fingerprintIssues).singleElement().satisfies(issue -> {
            assertThat(issue.reasonCode()).isEqualTo("FINGERPRINT_MISMATCH");
            assertThat(issue.field()).isEqualTo(DatasetFields.BUSINESS_KEY);
            assertThat(issue.businessKey()).containsEntry("amount", new BigDecimal("1.00"));
        });
    }

    @Test
    void businessKeyRuleLocalizesUnrepresentableInvalidScalarsInsteadOfThrowing() {
        DatasetDefinition definition = definition(BusinessKeyMode.COMPOSITE,
                List.of("ts_code", "ann_date", "sequence"));
        Map<String, Object> actual = baseRow();
        actual.put("sequence", 1.5d);
        List<IntegrityIssue> issues = new ArrayList<>();

        IntegrityRuleResult result = new BusinessKeyRule(definition, batches(actual), () -> {}, "ann_date")
                .evaluate(SCOPE, UNUSED_CONTEXT, issues::add);

        assertThat(result.status()).isEqualTo(FAIL);
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.reasonCode()).isEqualTo("KEY_VALUE_INVALID");
            assertThat(issue.field()).isEqualTo("sequence");
            assertThat(issue.businessKey()).containsEntry("ts_code", "000001.SZ")
                    .containsEntry("ann_date", DATE).containsEntry("sequence", null);
        });
    }

    @Test
    void sourceIdentityReportsOnlyWrongPhysicalMetadataFields() {
        Map<String, Object> actual = baseRow();
        actual.put(DatasetFields.SOURCE_PLUGIN, "other");
        actual.put(DatasetFields.SOURCE_API, null);
        List<IntegrityIssue> issues = new ArrayList<>();

        IntegrityRuleResult result = new SourceIdentityRule(
                definition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "ann_date")),
                batches(actual), () -> {}, "ann_date").evaluate(SCOPE, UNUSED_CONTEXT, issues::add);

        assertThat(result.status()).isEqualTo(FAIL);
        assertThat(result.statistics().requiredFieldIssueCount()).isNull();
        assertThat(issues).extracting(IntegrityIssue::field)
                .containsExactly(DatasetFields.SOURCE_PLUGIN, DatasetFields.SOURCE_API);
        assertThat(issues).allSatisfy(issue -> {
            assertThat(issue.type()).isEqualTo(IntegrityIssue.Type.SOURCE_IDENTITY_MISMATCH);
            assertThat(issue.businessKey()).containsEntry("ts_code", "000001.SZ");
        });
    }

    @Test
    void coreRulesReturnNoRowsAndCheckCpuBudgetForEveryVisitedValue() {
        DatasetDefinition definition = definition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "ann_date"));
        List<IntegrityRule> emptyRules = List.of(new RequiredFieldsRule(definition, List.of()),
                new BusinessKeyRule(definition, List.of()), new SourceIdentityRule(definition, List.of()));
        for (IntegrityRule rule : emptyRules) {
            IntegrityRuleResult result = rule.evaluate(SCOPE, UNUSED_CONTEXT, issue -> {});
            assertThat(result.status()).isEqualTo(NOT_APPLICABLE);
            assertThat(result.reasonCode()).isEqualTo("NO_ROWS");
        }

        AtomicInteger checks = new AtomicInteger();
        IntegrityRuleResult result = new RequiredFieldsRule(definition, batches(baseRow()), checks::incrementAndGet,
                "ann_date").evaluate(SCOPE, UNUSED_CONTEXT, issue -> {});
        assertThat(result.status()).isEqualTo(PASS);
        assertThat(checks).hasPositiveValue();
    }

    private static DatasetDefinition fingerprintDefinition() {
        return definition(BusinessKeyMode.FINGERPRINT, List.of("ts_code", "amount"));
    }

    private static DatasetDefinition definition(BusinessKeyMode mode, List<String> businessKey) {
        return new DatasetDefinition(KEY, "Daily", "test", QueryMode.ann_date, List.of(), TableName.from(KEY),
                List.of(
                        column("ts_code", LogicalType.STRING, false, 0, 32, null, null, List.of()),
                        column("ann_date", LogicalType.DATE, false, 1, null, null, null, List.of()),
                        column("end_date", LogicalType.DATE, true, 2, null, null, null, List.of()),
                        column("required_name", LogicalType.STRING, false, 3, 16, null, null, List.of()),
                        column("note", LogicalType.STRING, true, 4, 16, null, null, List.of()),
                        column("amount", LogicalType.DECIMAL, true, 5, null, 6, 2, List.of()),
                        column("sequence", LogicalType.LONG, true, 6, null, null, null, List.of()),
                        column("kind", LogicalType.ENUM, true, 7, 2, null, null, List.of("A", "B"))),
                new BusinessKeyDefinition(mode, businessKey), List.of(), "ts_code", 500);
    }

    private static ColumnDefinition column(String name, LogicalType type, boolean nullable, int order,
            Integer length, Integer precision, Integer scale, List<String> allowed) {
        return new ColumnDefinition(name, name, type, nullable, order, length, precision, scale, allowed, false);
    }

    private static List<TargetBatch> batches(Map<String, Object>... rows) {
        return List.of(new TargetBatch(false, List.of(rows)));
    }

    private static Map<String, Object> baseRow() {
        return row("ts_code", "000001.SZ", "ann_date", DATE, "end_date", LocalDate.of(2025, 12, 31),
                "required_name", "name", "note", null, "amount", new BigDecimal("1.00"), "sequence", 1L,
                "kind", "A", DatasetFields.SOURCE_PLUGIN, "reader", DatasetFields.SOURCE_API, "daily");
    }

    private static Map<String, Object> row(Object... pairs) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) row.put((String) pairs[i], pairs[i + 1]);
        return row;
    }
}
