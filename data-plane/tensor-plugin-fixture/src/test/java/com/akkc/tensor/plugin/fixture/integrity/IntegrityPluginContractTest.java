package com.akkc.tensor.plugin.fixture.integrity;

import com.akkc.tensor.core.integrity.IntegrityCheckJson;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.IntegrityCheckSupport;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.fixture.FixtureConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityStatus.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor.ScopeKind.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityRuleDescriptor.Dimension.*;

class IntegrityPluginContractTest {
    private final DatasetDefinition definition = new FixtureConfiguration().fixtureDatasetAdapter().definition();
    private final DatasetKey key = definition.datasetKey();
    private final Instant now = Instant.parse("2026-09-16T00:00:00Z");
    private final LocalDate first = LocalDate.of(2026, 1, 1);
    private final LocalDate last = LocalDate.of(2026, 1, 20);
    private final IntegrityCheckJson json = new IntegrityCheckJson();

    @Test
    void existingPluginsKeepTheirOriginalDownloadContract() {
        var fixture = new FixtureConfiguration().fixturePlugin();
        DataSourcePlugin legacy = new DataSourcePlugin() {
            public PluginDescriptor descriptor() { return fixture.descriptor(); }
            public PluginReadiness readiness() { return fixture.readiness(); }
            public DownloadEnvelope download(ApiName api, Map<String, Object> params) {
                return fixture.download(api, params);
            }
        };
        assertThat(legacy).isNotInstanceOf(IntegrityCheckSupport.class);
        assertThat(legacy.download(key.apiName(), Map.of("scenario", "SUCCESS")).data()).hasSize(1);
    }

    @Test
    void stockScopesRequireExactlyOneCoverageRuleAndNonStockCannotExecute() {
        var coverage = rule("fixture.coverage", "1", COVERAGE, List.of("ts_code", "trade_date"));
        for (var kind : List.of(STOCK_DATE, STOCK_SNAPSHOT)) {
            assertThat(descriptor(kind, List.of(coverage)).rules()).containsExactly(coverage);
            assertThatIllegalArgumentException().isThrownBy(() -> descriptor(kind, List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> descriptor(kind,
                    List.of(coverage, rule("second.coverage", "1", COVERAGE, List.of()))));
        }
        assertThat(descriptor(NON_STOCK, List.of()).rules()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> descriptor(NON_STOCK, List.of(coverage)));
        assertThatIllegalArgumentException().isThrownBy(() -> descriptor(STOCK_DATE, List.of(coverage, coverage)));
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityDescriptor(key, STOCK_DATE,
                "ts_code", null, "日期", ZoneId.of("Asia/Shanghai"), "1", List.of(), List.of(coverage), List.of()));
    }

    @Test
    void validatesActualRulesColumnsAndReferenceDeclarations() {
        var coverage = rule("fixture.coverage", "1", COVERAGE, List.of("trade_date"));
        var descriptor = descriptor(STOCK_DATE, List.of(coverage));
        IntegrityContracts.validate(descriptor, List.of(implementation(coverage)), Map.of(key, definition));
        assertThatIllegalArgumentException().isThrownBy(() -> IntegrityContracts.validate(
                descriptor, List.of(implementation(rule("fixture.coverage", "2", COVERAGE, List.of("trade_date")))),
                Map.of(key, definition)));
        assertThatIllegalArgumentException().isThrownBy(() -> IntegrityContracts.validate(descriptor, List.of(), Map.of(key, definition)));
        var bad = rule("fixture.coverage", "1", COVERAGE, List.of("missing_column"));
        assertThatIllegalArgumentException().isThrownBy(() -> IntegrityContracts.validate(
                descriptor(STOCK_DATE, List.of(bad)), List.of(implementation(bad)), Map.of(key, definition)));
        var dep = new IntegrityDependency(key, List.of("note"), "参考说明");
        var undeclared = new IntegrityRuleDescriptor("fixture.coverage", "1", "覆盖", COVERAGE,
                List.of(), List.of(dep), "依赖必须预先声明");
        assertThatIllegalArgumentException().isThrownBy(() -> IntegrityContracts.validate(
                descriptor(STOCK_DATE, List.of(undeclared)), List.of(implementation(undeclared)), Map.of(key, definition)));
        var wrongAxis = new IntegrityDescriptor(key, STOCK_DATE, "ts_code", "amount", "日期",
                ZoneId.of("Asia/Shanghai"), "1", List.of(), List.of(coverage), List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> IntegrityContracts.validate(
                wrongAxis, List.of(implementation(coverage)), Map.of(key, definition)));
    }

    @Test
    void scopesAndReadRequestsPreserveTheWholeRangeAndDefendMutableInputs() {
        var scope = scope("PROVEN");
        assertThat(scope.startDate()).isEqualTo(first);
        assertThat(scope.endDate()).isEqualTo(last);
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityScope(key, "PROVEN", last, first, now, now));
        var values = new LinkedHashMap<String, Object>();
        values.put("ts_code", "PROVEN");
        var request = new IntegrityReadRequest(key, List.of("ts_code", "trade_date"), values,
                "trade_date", new IntegrityDateRange(first, last), false, "目标范围");
        values.put("ts_code", "OTHER");
        assertThat(request.equalities()).containsEntry("ts_code", "PROVEN");
        assertThatThrownBy(() -> request.equalities().put("ts_code", "OTHER")).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityReadRequest(key, List.of("x;drop"),
                Map.of(), null, null, false, "目标"));
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityReadRequest(key, List.of("trade_date"),
                Map.of(), "trade_date", new IntegrityDateRange(first, last), true, "目标"));
    }

    @Test
    void expectedKeysAreExactImmutableAndLazyWithRequiredEvidence() {
        var mutableKey = new LinkedHashMap<String, Object>();
        mutableKey.put("ts_code", "PROVEN");
        mutableKey.put("trade_date", first);
        mutableKey.put("version", 9007199254740993L);
        mutableKey.put("amount", new BigDecimal("1.000000000000000001"));
        var expected = new IntegrityExpectedKeys(scope("PROVEN"), IntegrityExpectedKeys.Basis.PROVEN,
                List.of(mutableKey), List.of(evidence()));
        mutableKey.put("version", 1L);
        assertThat(expected.keys().iterator().next()).containsEntry("version", 9007199254740993L);
        assertThatThrownBy(() -> expected.keys().iterator().next().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityExpectedKeys(scope("PROVEN"),
                IntegrityExpectedKeys.Basis.PROVEN, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityExpectedKeys(scope("PROVEN"),
                IntegrityExpectedKeys.Basis.PROVEN, List.of(), List.of(new IntegrityEvidence("fixture", "1",
                        new IntegrityDateRange(first, first), now, "仅局部"))));
        int[] opened = {0};
        Iterable<Map<String, Object>> lazy = () -> { opened[0]++; return List.<Map<String, Object>>of(Map.of("trade_date", first)).iterator(); };
        var sequence = new IntegrityExpectedKeys(scope("PROVEN"), IntegrityExpectedKeys.Basis.UNCONFIRMED, lazy, List.of(evidence()));
        assertThat(opened[0]).isZero();
        assertThat(sequence.keys().iterator().next()).containsEntry("trade_date", first);
        assertThat(opened[0]).isEqualTo(1);
    }

    @Test
    void exactCountsDetermineCoverageAndUnknownIsNeverZero() {
        assertThat(stats(20L, 19L).coverageRate()).isEqualTo(new BigDecimal("0.950000"));
        assertThat(stats(6L, 1L).coverageRate()).isEqualTo(new BigDecimal("0.166667"));
        assertThat(stats(0L, 0L).coverageRate()).isNull();
        assertThat(stats(null, null).expectedCount()).isNull();
        assertThat(stats(null, null).coverageRate()).isNull();
        assertThat(stats(10000000L, 9999999L).coverageRate()).isEqualTo(new BigDecimal("1.000000"));
        assertThat(stats(10000000L, 9999999L).missingCount()).isEqualTo(1L);
        assertThatIllegalArgumentException().isThrownBy(() -> stats(1L, 2L));
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityStatistics(-1L, null, null, null, null, null, null));
        var field = rule("core.field", "1", FIELD, List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityRuleResult(field, PASS, "OK", "字段",
                stats(20L, 19L), List.of(evidence())));
    }

    @Test
    void statusPrecedenceNeverHidesKnownFailureOrTreatsAllNotApplicableAsPass() {
        assertThat(IntegrityStatus.aggregate(List.of(PASS, WARN, UNKNOWN, FAIL))).isEqualTo(FAIL);
        assertThat(IntegrityStatus.aggregate(List.of(PASS, WARN, UNKNOWN))).isEqualTo(UNKNOWN);
        assertThat(IntegrityStatus.aggregate(List.of(PASS, WARN))).isEqualTo(WARN);
        assertThat(IntegrityStatus.aggregate(List.of(NOT_APPLICABLE, PASS))).isEqualTo(PASS);
        assertThat(IntegrityStatus.aggregate(List.of(NOT_APPLICABLE))).isEqualTo(NOT_APPLICABLE);
        assertThat(IntegrityStatus.aggregate(List.of())).isEqualTo(UNKNOWN);
    }

    @Test
    void reportEncodingKeepsLargeCountsDatesCompleteKeysAndNulls() throws Exception {
        var businessKey = new LinkedHashMap<String, Object>();
        businessKey.put("ts_code", "PROVEN");
        businessKey.put("ann_date", first);
        businessKey.put("end_date", LocalDate.of(2025, 12, 31));
        businessKey.put("report_type", "1");
        businessKey.put("version", 9007199254740993L);
        businessKey.put("amount", new BigDecimal("1.000000000000000001"));
        var issue = new IntegrityIssue(IntegrityIssue.Type.MISSING, FAIL, "PROVEN", key.apiName(),
                "ann_date", first, businessKey, null, Map.of("end_date", LocalDate.of(2025, 12, 31)),
                "MISSING", "确认缺少该版本", List.of(evidence()), false);
        businessKey.clear();
        var tree = new ObjectMapper().readTree(json.write(Map.of("scope", scope("PROVEN"), "issue", issue,
                "statistics", new IntegrityStatistics(9007199254740993L, null, null, null, null, null, null))));
        assertThat(tree.at("/statistics/actualCount").textValue()).isEqualTo("9007199254740993");
        assertThat(tree.at("/statistics/expectedCount").isNull()).isTrue();
        assertThat(tree.at("/issue/businessKey").size()).isEqualTo(6);
        assertThat(tree.at("/issue/businessKey/amount").textValue()).isEqualTo("1.000000000000000001");
        assertThat(tree.at("/issue/date").textValue()).isEqualTo("2026-01-01");
        assertThat(tree.at("/issue/relatedDates/end_date").textValue()).isEqualTo("2025-12-31");
        assertThat(tree.at("/scope/endDate").textValue()).isEqualTo("2026-01-20");
        assertThat(tree.at("/issue/evidence/0/readAt").textValue()).isEqualTo("2026-09-16T00:00:00Z");
        assertThat(new ObjectMapper().readTree(json.write(stats(6L, 1L))).get("coverageRate").textValue()).isEqualTo("0.166667");
    }

    @Test
    void unitExecutionAndDataConclusionsRemainSeparateAndIncompleteHasNoCoverage() throws Exception {
        var d = descriptor(STOCK_DATE, List.of(rule("fixture.coverage", "1", COVERAGE, List.of())));
        var report = new IntegrityUnitResult(scope("PROVEN"), d, json.definitionHash(definition), null,
                IntegrityUnitStatus.COMPLETED, FAIL, PASS, UNKNOWN, stats(20L, 19L), List.of(), List.of(evidence()),
                now, false, true, "MISSING", "确认缺失");
        assertThat(report.overallStatus()).isEqualTo(FAIL);
        var tree = new ObjectMapper().readTree(json.write(report));
        assertThat(tree.get("unitStatus").textValue()).isEqualTo("COMPLETED");
        assertThat(tree.get("overallStatus").textValue()).isEqualTo("FAIL");
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityUnitResult(scope("PROVEN"), d,
                json.definitionHash(definition), null, IntegrityUnitStatus.ERROR, UNKNOWN, PASS, PASS,
                stats(20L, 19L), List.of(), List.of(evidence()), now, true, false, "SCAN_FAILED", "扫描失败"));
    }

    @Test
    void canonicalHashesIncludeRuleVersionsDefinitionsAndApiOrder() {
        var d = descriptor(STOCK_DATE, List.of(rule("fixture.coverage", "1", COVERAGE, List.of("trade_date"))));
        var snapshot = snapshot(definition, d);
        String original = json.capabilityHash(key.pluginId(), List.of(snapshot));
        assertThat(original).matches("[a-f0-9]{64}");
        assertThat(json.capabilitySnapshot(key.pluginId(), List.of(snapshot)))
                .doesNotContain("acceptedAt", "snapshotStartedAt", "credential", "token");
        var changed = descriptor(STOCK_DATE, List.of(rule("fixture.coverage", "2", COVERAGE, List.of("trade_date"))));
        assertThat(json.capabilityHash(key.pluginId(), List.of(snapshot(definition, changed)))).isNotEqualTo(original);
        var coreRules = new ArrayList<>(IntegrityContracts.coreRules(definition));
        var firstRule = coreRules.getFirst();
        coreRules.set(0, new IntegrityRuleDescriptor(firstRule.ruleId(), "2", firstRule.displayName(), firstRule.dimension(),
                firstRule.requiredColumns(), firstRule.dependencies(), firstRule.description()));
        assertThat(json.capabilityHash(key.pluginId(), List.of(new IntegrityCheckJson.ApiSnapshot(definition, d, List.of(), coreRules))))
                .isNotEqualTo(original);
        var columns = new ArrayList<>(definition.columns());
        var note = columns.removeLast();
        columns.add(new ColumnDefinition(note.name(), note.label(), note.logicalType(), false, note.displayOrder(),
                note.length(), note.precision(), note.scale(), note.allowedValues(), note.longText()));
        var modified = copyDefinition(key, columns);
        assertThat(json.capabilityHash(key.pluginId(), List.of(snapshot(modified, d)))).isNotEqualTo(original);
        var secondKey = DatasetKey.of(key.pluginId(), ApiName.of("other"));
        var second = snapshot(copyDefinition(secondKey, definition.columns()), null);
        assertThat(json.capabilityHash(key.pluginId(), List.of(snapshot, second)))
                .isNotEqualTo(json.capabilityHash(key.pluginId(), List.of(second, snapshot)));
        assertThatIllegalArgumentException().isThrownBy(() -> json.capabilityHash(key.pluginId(), List.of(snapshot, snapshot)));
        assertThatIllegalArgumentException().isThrownBy(() -> json.capabilityHash(key.pluginId(),
                List.of(new IntegrityCheckJson.ApiSnapshot(definition, d, List.of(), List.of()))));
    }

    @Test
    void canonicalObjectsIgnoreInsertionOrderButRejectSensitiveOrInexactValues() {
        var left = new LinkedHashMap<String, Object>();
        left.put("z", 2); left.put("a", Map.of("second", 2, "first", 1));
        var right = new LinkedHashMap<String, Object>();
        right.put("a", Map.of("first", 1, "second", 2)); right.put("z", 2);
        assertThat(json.requestHash(left)).isEqualTo(json.requestHash(right));
        assertThat(json.requestHash(Map.of("apis", List.of("one", "two"))))
                .isNotEqualTo(json.requestHash(Map.of("apis", List.of("two", "one"))));
        assertThatIllegalArgumentException().isThrownBy(() -> json.write(Map.of("token", "secret")));
        assertThatIllegalArgumentException().isThrownBy(() -> json.write(Map.of("value", 0.1D)));
        assertThatIllegalArgumentException().isThrownBy(() -> json.write(Map.of("raw", new Object())));
    }

    @Test
    void fixtureOffersProvenAndUnconfirmedInputsWithoutPerformingScansOrDownloads() {
        IntegrityCheckSupport plugin = new FixtureConfiguration().fixturePlugin();
        var d = plugin.integrityDescriptor(key.apiName()).orElseThrow();
        assertThat(d.capabilityVersion()).isEqualTo("2");
        assertThat(d.rules()).extracting(IntegrityRuleDescriptor::version).containsExactly("2");
        IntegrityContracts.validate(d, plugin.integrityRules(key.apiName()), Map.of(key, definition));
        assertThat(plugin.normalizeIntegritySymbol(" proven ")).isEqualTo("PROVEN");
        assertThat(plugin.integrityDescriptor(ApiName.of("unknown"))).isEmpty();
        assertThat(plugin.integrityRules(ApiName.of("unknown"))).isEmpty();
        for (String symbol : List.of("PROVEN", "UNCONFIRMED")) {
            var context = new CaptureContext();
            plugin.integrityRules(key.apiName()).getFirst().evaluate(scope(symbol), context, issue -> {});
            assertThat(context.expected.basis()).isEqualTo(symbol.equals("PROVEN")
                    ? IntegrityExpectedKeys.Basis.PROVEN : IntegrityExpectedKeys.Basis.UNCONFIRMED);
            var keys = StreamSupport.stream(context.expected.keys().spliterator(), false).toList();
            assertThat(keys).hasSize(20);
            assertThat(keys.getFirst()).containsExactlyInAnyOrderEntriesOf(Map.of("ts_code", symbol, "trade_date", first));
            assertThat(context.expected.evidence().getFirst().summary()).contains("fixture", "非 Tushare");
        }
        var context = new CaptureContext();
        plugin.integrityRules(key.apiName()).getFirst().evaluate(new IntegrityScope(key, "PROVEN", first,
                last.plusDays(1), now, now), context, issue -> {});
        assertThat(context.expected.basis()).isEqualTo(IntegrityExpectedKeys.Basis.UNCONFIRMED);
        assertThat(context.expected.scope().endDate()).isEqualTo(last.plusDays(1));
    }

    @Test
    void configuredVersionThreeChangesCapabilityAndRegistersTheExtension() {
        var configuration = new FixtureConfiguration();
        IntegrityCheckSupport versionTwo = configuration.fixturePlugin(2);
        IntegrityCheckSupport versionThree = configuration.fixturePlugin(3);
        var descriptorTwo = versionTwo.integrityDescriptor(key.apiName()).orElseThrow();
        var descriptorThree = versionThree.integrityDescriptor(key.apiName()).orElseThrow();
        IntegrityContracts.validate(descriptorTwo, versionTwo.integrityRules(key.apiName()), Map.of(key, definition));
        IntegrityContracts.validate(descriptorThree, versionThree.integrityRules(key.apiName()), Map.of(key, definition));
        assertThat(descriptorThree.capabilityVersion()).isEqualTo("3");
        assertThat(descriptorThree.rules()).extracting(IntegrityRuleDescriptor::ruleId)
                .containsExactly("fixture.coverage.fixture_daily", "fixture.acceptance.extension");
        assertThat(descriptorThree.rules()).extracting(IntegrityRuleDescriptor::version)
                .containsExactly("3", "1");
        assertThat(json.capabilityHash(key.pluginId(), List.of(snapshot(definition, descriptorThree))))
                .isNotEqualTo(json.capabilityHash(key.pluginId(), List.of(snapshot(definition, descriptorTwo))));
    }

    @Test
    void pluginWideRuleIdsAndReferenceColumnContractsAreEnforced() {
        var r = rule("fixture.coverage", "1", COVERAGE, List.of());
        var d = descriptor(STOCK_DATE, List.of(r));
        var otherKey = DatasetKey.of(key.pluginId(), ApiName.of("other"));
        var other = new IntegrityDescriptor(otherKey, STOCK_DATE, "ts_code", "trade_date", "交易日期",
                ZoneId.of("Asia/Shanghai"), "1", List.of(), List.of(r), List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> IntegrityContracts.validatePlugin(List.of(d, other)));
        var badDependency = new IntegrityDependency(otherKey, List.of("missing"), "本地参考");
        var badDescriptor = new IntegrityDescriptor(key, STOCK_DATE, "ts_code", "trade_date", "日期",
                ZoneId.of("Asia/Shanghai"), "1", List.of(badDependency), List.of(r), List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> IntegrityContracts.validateDescriptor(badDescriptor,
                Map.of(key, definition, otherKey, copyDefinition(otherKey, definition.columns()))));
        var dependency = new IntegrityDependency(otherKey, List.of("trade_date"), "本地参考");
        var dependent = new IntegrityRuleDescriptor("fixture.coverage", "1", "参考覆盖", COVERAGE,
                List.of(), List.of(dependency), "参考投影");
        var valid = new IntegrityDescriptor(key, STOCK_DATE, "ts_code", "trade_date", "日期",
                ZoneId.of("Asia/Shanghai"), "1", List.of(dependency), List.of(dependent), List.of());
        var reference = copyDefinition(otherKey, definition.columns());
        IntegrityContracts.validate(valid, List.of(implementation(dependent)), Map.of(key, definition, otherKey, reference));
        var original = new IntegrityCheckJson.ApiSnapshot(definition, valid, List.of(reference), IntegrityContracts.coreRules(definition));
        var columns = new ArrayList<>(reference.columns());
        var old = columns.removeLast();
        columns.add(new ColumnDefinition(old.name(), old.label(), old.logicalType(), old.nullable(), old.displayOrder(),
                254, old.precision(), old.scale(), old.allowedValues(), old.longText()));
        var changed = new IntegrityCheckJson.ApiSnapshot(definition, valid, List.of(copyDefinition(otherKey, columns)), IntegrityContracts.coreRules(definition));
        assertThat(json.capabilityHash(key.pluginId(), List.of(changed)))
                .isNotEqualTo(json.capabilityHash(key.pluginId(), List.of(original)));
    }

    @Test
    void capabilityVersionDateAxisAndBusinessKeyOrderChangeTheHash() {
        var d = descriptor(STOCK_DATE, List.of(rule("fixture.coverage", "1", COVERAGE, List.of())));
        String before = json.capabilityHash(key.pluginId(), List.of(snapshot(definition, d)));
        var upgraded = new IntegrityDescriptor(key, d.scopeKind(), d.symbolField(), d.dateField(), d.dateLabel(),
                d.marketZone(), "2", d.dependencies(), d.rules(), d.limitations());
        assertThat(json.capabilityHash(key.pluginId(), List.of(snapshot(definition, upgraded)))).isNotEqualTo(before);
        var snapshotScope = descriptor(STOCK_SNAPSHOT, d.rules());
        assertThat(json.capabilityHash(key.pluginId(), List.of(snapshot(definition, snapshotScope)))).isNotEqualTo(before);
        var reordered = new DatasetDefinition(key, definition.displayName(), definition.category(), definition.queryMode(),
                definition.parameters(), definition.tableName(), definition.columns(),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("trade_date", "ts_code")),
                definition.filters(), definition.fixedColumn());
        assertThat(json.capabilityHash(key.pluginId(), List.of(snapshot(reordered, d)))).isNotEqualTo(before);
    }

    @Test
    void unknownProblemDatesAreNotReplacedByAcceptanceTime() throws Exception {
        var issue = new IntegrityIssue(IntegrityIssue.Type.DATE_SCOPE_UNRESOLVED, UNKNOWN, "PROVEN", key.apiName(),
                "trade_date", null, Map.of(), null, Map.of(), "DATE_SCOPE_UNRESOLVED", "日期为空", List.of(evidence()), false);
        assertThat(new ObjectMapper().readTree(json.write(issue)).get("date").isNull()).isTrue();
        var descriptor = descriptor(STOCK_DATE, List.of(rule("fixture.coverage", "1", COVERAGE, List.of())));
        var unknownRule = new IntegrityRuleResult(descriptor.rules().getFirst(), UNKNOWN, "RULE_EXECUTION_FAILED", "资料不足",
                stats(null, null), List.of(evidence()));
        var result = new IntegrityUnitResult(scope("PROVEN"), descriptor, json.definitionHash(definition), null,
                IntegrityUnitStatus.ERROR, FAIL, PASS, PASS, stats(null, null), List.of(unknownRule), List.of(evidence()), now, true, false, "SCAN_FAILED", "扫描失败");
        assertThat(result.overallStatus()).isEqualTo(FAIL);
        assertThat(result.statistics().coverageRate()).isNull();
    }

    @Test
    void unitsWithoutRunnableRulesKeepTheirOwnFailureReason() throws Exception {
        var result = new IntegrityUnitResult(scope("PROVEN"), null, json.definitionHash(definition), null,
                IntegrityUnitStatus.ERROR, UNKNOWN, UNKNOWN, UNKNOWN, stats(null, null), List.of(), List.of(), now,
                true, false, "DEFINITION_CHANGED", "受理后数据集定义发生变化");
        var tree = new ObjectMapper().readTree(json.write(result));
        assertThat(tree.get("reasonCode").textValue()).isEqualTo("DEFINITION_CHANGED");
        assertThat(tree.get("message").textValue()).isEqualTo("受理后数据集定义发生变化");
        assertThat(tree.get("ruleResults")).isEmpty();
        assertThat(tree.get("overallStatus").textValue()).isEqualTo("UNKNOWN");
    }

    @Test
    void fixtureNeverConvertsUncomputedComparisonCountsIntoPass() {
        var rule = new FixtureConfiguration().fixturePlugin().integrityRules(key.apiName()).getFirst();
        var partial = new FixedContext(new IntegrityStatistics(null, 20L, null, null, null, null, null));
        assertThat(rule.evaluate(scope("PROVEN"), partial, issue -> {}).status()).isEqualTo(UNKNOWN);
        var missing = new FixedContext(new IntegrityStatistics(null, null, null, 1L, null, null, null));
        assertThat(rule.evaluate(scope("PROVEN"), missing, issue -> {}).status()).isEqualTo(FAIL);
        var complete = new FixedContext(new IntegrityStatistics(20L, 20L, 20L, 0L, 0L, 0L, null));
        assertThat(rule.evaluate(scope("PROVEN"), complete, issue -> {}).status()).isEqualTo(PASS);
        var withExtra = new FixedContext(new IntegrityStatistics(20L, 20L, 19L, 1L, 0L, 1L, null));
        assertThat(rule.evaluate(scope("PROVEN"), withExtra, issue -> {}).status()).isEqualTo(FAIL);
    }

    private record FixedContext(IntegrityStatistics statistics) implements IntegrityContext {
        public void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> rows) { throw new AssertionError(); }
        public IntegrityStatistics compare(IntegrityExpectedKeys expected) { return statistics; }
    }

    private IntegrityCheckJson.ApiSnapshot snapshot(DatasetDefinition def, IntegrityDescriptor descriptor) {
        return new IntegrityCheckJson.ApiSnapshot(def, descriptor, List.of(),
                descriptor == null ? List.of() : IntegrityContracts.coreRules(def));
    }
    private DatasetDefinition copyDefinition(DatasetKey k, List<ColumnDefinition> columns) {
        return new DatasetDefinition(k, definition.displayName(), definition.category(), definition.queryMode(),
                definition.parameters(), TableName.from(k), columns, definition.businessKey(), definition.filters(), definition.fixedColumn());
    }
    private IntegrityDescriptor descriptor(IntegrityDescriptor.ScopeKind kind, List<IntegrityRuleDescriptor> rules) {
        return new IntegrityDescriptor(key, kind, kind == NON_STOCK ? null : "ts_code",
                kind == STOCK_DATE ? "trade_date" : null, kind == STOCK_DATE ? "交易日期" : "范围",
                ZoneId.of("Asia/Shanghai"), "1", List.of(), rules, List.of("本地检查"));
    }
    private IntegrityRuleDescriptor rule(String id, String version, IntegrityRuleDescriptor.Dimension dimension, List<String> columns) {
        return new IntegrityRuleDescriptor(id, version, id, dimension, columns, List.of(), "规则解释");
    }
    private IntegrityRule implementation(IntegrityRuleDescriptor descriptor) {
        return new IntegrityRule() {
            public IntegrityRuleDescriptor descriptor() { return descriptor; }
            public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink sink) {
                throw new AssertionError("Metadata validation must not run rules");
            }
        };
    }
    private IntegrityScope scope(String symbol) { return new IntegrityScope(key, symbol, first, last, now, now); }
    private IntegrityEvidence evidence() { return new IntegrityEvidence("fixture", "1", new IntegrityDateRange(first, last), now, "合成完整集合"); }
    private IntegrityStatistics stats(Long expected, Long matched) {
        return new IntegrityStatistics(matched, expected, matched, expected == null ? null : Math.max(0, expected - matched), null, null, null);
    }
    private static class CaptureContext implements IntegrityContext {
        private IntegrityExpectedKeys expected;
        public void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> rows) {
            throw new AssertionError("fixture must delegate comparison, not scan directly");
        }
        public IntegrityStatistics compare(IntegrityExpectedKeys keys) {
            expected = keys;
            return new IntegrityStatistics(0L, null, null, null, 20L, null, null);
        }
    }
}
