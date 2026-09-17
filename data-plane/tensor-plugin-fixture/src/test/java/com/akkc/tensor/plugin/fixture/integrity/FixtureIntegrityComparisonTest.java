package com.akkc.tensor.plugin.fixture.integrity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityStatus.*;

import com.akkc.tensor.core.integrity.*;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.fixture.FixtureConfiguration;
import com.akkc.tensor.plugin.fixture.FixtureEnvelopeFactory;
import com.akkc.tensor.plugin.fixture.FixturePlugin;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Uses the registered fixture rule and production evaluator; only database rows are supplied locally. */
class FixtureIntegrityComparisonTest {
    static final Instant NOW = Instant.parse("2026-09-16T00:01:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final LocalDate FIRST = LocalDate.of(2026, 1, 1);
    final FixtureConfiguration configuration = new FixtureConfiguration();
    final DatasetDefinition definition = configuration.fixtureDatasetAdapter().definition();

    @Test void provenFixtureIdentifiesTwentiethDayWithExactCoverage() {
        var evaluation = evaluate("PROVEN", 20, 19);
        var result = evaluation.result();
        assertThat(result.unitStatus()).isEqualTo(IntegrityUnitStatus.COMPLETED);
        assertThat(result.statistics()).isEqualTo(new IntegrityStatistics(19L, 20L, 19L, 1L, 0L, 0L, 0L));
        assertThat(result.statistics().coverageRate()).isEqualByComparingTo("0.950000");
        assertThat(result.overallStatus()).isEqualTo(FAIL);
        assertThat(result.keyStatus()).isEqualTo(PASS);
        assertThat(result.fieldStatus()).isEqualTo(PASS);
        assertThat(evaluation.issues()).singleElement().satisfies(bound -> {
            assertThat(bound.ruleId()).isEqualTo("fixture.coverage.fixture_daily");
            assertThat(bound.ruleVersion()).isEqualTo("2");
            assertThat(bound.issue().type()).isEqualTo(IntegrityIssue.Type.MISSING);
            assertThat(bound.issue().date()).isEqualTo(FIRST.plusDays(19));
            assertThat(bound.issue().businessKey()).containsExactlyInAnyOrderEntriesOf(
                    Map.of("ts_code", "PROVEN", "trade_date", FIRST.plusDays(19)));
        });
        assertThat(result.evidence()).anySatisfy(e -> assertThat(e.summary()).contains("非 Tushare 生产基线"));
        assertThat(result.scope().snapshotStartedAt()).isEqualTo(NOW);
        assertThat(result.scope().endDate()).isEqualTo(FIRST.plusDays(19));
    }

    @Test void unconfirmedFixtureReportsOnlySuspectedMissing() {
        var evaluation = evaluate("UNCONFIRMED", 20, 19);
        assertThat(evaluation.result().overallStatus()).isEqualTo(UNKNOWN);
        assertUnknown(evaluation.result().statistics());
        assertThat(evaluation.result().statistics().suspectedMissingCount()).isEqualTo(1L);
        assertThat(evaluation.issues()).singleElement().satisfies(bound -> {
            assertThat(bound.issue().type()).isEqualTo(IntegrityIssue.Type.SUSPECTED_MISSING);
            assertThat(bound.issue().status()).isEqualTo(WARN);
            assertThat(bound.issue().date()).isEqualTo(FIRST.plusDays(19));
        });
    }

    @Test void widerRequestedWindowCannotUpgradeFixtureEvidence() {
        var evaluation = evaluate("PROVEN", 21, 19);
        assertThat(evaluation.result().overallStatus()).isEqualTo(UNKNOWN);
        assertUnknown(evaluation.result().statistics());
        assertThat(evaluation.result().scope().endDate()).isEqualTo(FIRST.plusDays(20));
        assertThat(evaluation.issues()).allSatisfy(bound ->
                assertThat(bound.issue().type()).isEqualTo(IntegrityIssue.Type.SUSPECTED_MISSING));
    }

    @Test void fullyPresentCandidatesRemainUnknownWithoutProvenBasis() {
        var evaluation = evaluate("UNCONFIRMED", 20, 20);
        assertThat(evaluation.result().overallStatus()).isEqualTo(UNKNOWN);
        assertUnknown(evaluation.result().statistics());
        assertThat(evaluation.issues()).isEmpty();
    }

    @Test void absentStockHasAllProvenMissingKeysAndNoRowsForCoreRules() {
        var evaluation = evaluate("PROVEN", 20, 0);
        assertThat(evaluation.result().statistics().missingCount()).isEqualTo(20L);
        assertThat(evaluation.result().statistics().coverageRate()).isEqualByComparingTo("0");
        assertThat(evaluation.issues()).hasSize(20);
        assertThat(evaluation.result().ruleResults().stream()
                .filter(r -> r.descriptor().ruleId().startsWith("core."))).allSatisfy(r -> {
                    assertThat(r.status()).isEqualTo(NOT_APPLICABLE);
                    assertThat(r.reasonCode()).isEqualTo("NO_ROWS");
                });
    }

    @Test void provenExtraUsesTwentyExpectedDatesAndReportsBothSetDifferences() {
        var dates = new ArrayList<LocalDate>();
        IntStream.range(0, 19).mapToObj(FIRST::plusDays).forEach(dates::add);
        dates.add(FIRST.plusDays(20));
        var evaluation = evaluate("PROVEN_EXTRA", 21, dates, 2);
        assertThat(evaluation.result().statistics())
                .isEqualTo(new IntegrityStatistics(20L, 20L, 19L, 1L, 0L, 1L, 0L));
        assertThat(evaluation.result().statistics().coverageRate()).isEqualByComparingTo("0.950000");
        assertThat(evaluation.result().overallStatus()).isEqualTo(FAIL);
        assertThat(evaluation.issues()).extracting(bound -> bound.issue().type())
                .containsExactlyInAnyOrder(IntegrityIssue.Type.MISSING, IntegrityIssue.Type.EXTRA);
        assertThat(evaluation.issues()).extracting(bound -> bound.issue().date())
                .containsExactlyInAnyOrder(FIRST.plusDays(19), FIRST.plusDays(20));
    }

    @Test void reliableEmptySetIsPassWithNullCoverageRate() {
        var result = evaluate("PROVEN_EMPTY", 21, List.of(), 2).result();
        assertThat(result.overallStatus()).isEqualTo(PASS);
        assertThat(result.reasonCode()).isEqualTo("VERIFIED_EMPTY");
        assertThat(result.statistics()).isEqualTo(new IntegrityStatistics(0L, 0L, 0L, 0L, 0L, 0L, 0L));
        assertThat(result.statistics().coverageRate()).isNull();
    }

    @Test void versionThreeExecutesIndependentFieldExtension() {
        var result = evaluate("PROVEN_EMPTY", 21, List.of(), 3).result();
        assertThat(result.descriptor().capabilityVersion()).isEqualTo("3");
        assertThat(result.ruleResults()).anySatisfy(rule -> {
            assertThat(rule.descriptor().ruleId()).isEqualTo("fixture.acceptance.extension");
            assertThat(rule.descriptor().version()).isEqualTo("1");
            assertThat(rule.status()).isEqualTo(PASS);
            assertThat(rule.reasonCode()).isEqualTo("FIXTURE_EXTENSION_VERIFIED");
            assertThat(rule.evidence()).singleElement().satisfies(evidence ->
                    assertThat(evidence.summary()).contains("验收扩展规则已执行", "acceptance"));
        });
    }

    private void assertUnknown(IntegrityStatistics statistics) {
        assertThat(statistics.expectedCount()).isNull();
        assertThat(statistics.matchedCount()).isNull();
        assertThat(statistics.missingCount()).isNull();
        assertThat(statistics.extraCount()).isNull();
        assertThat(statistics.coverageRate()).isNull();
    }

    @SuppressWarnings("unchecked")
    private IntegrityUnitEvaluator.Evaluation evaluate(String symbol, int rangeDays, int rows) {
        return evaluate(symbol, rangeDays,
                IntStream.range(0, rows).mapToObj(FIRST::plusDays).toList(), 2);
    }

    @SuppressWarnings("unchecked")
    private IntegrityUnitEvaluator.Evaluation evaluate(
            String symbol, int rangeDays, List<LocalDate> dates, int version) {
        var plugin = new FixturePlugin(definition, new FixtureEnvelopeFactory(), version);
        var capability = plugin.integrityDescriptor(definition.datasetKey().apiName()).orElseThrow();
        var scope = new IntegrityScope(definition.datasetKey(), symbol, FIRST, FIRST.plusDays(rangeDays - 1),
                NOW.minusSeconds(60), null);
        var snapshot = new IntegrityScope(scope.datasetKey(), symbol, scope.startDate(), scope.endDate(),
                scope.acceptedAt(), NOW);
        var data = dates.stream().<Map<String, Object>>map(date -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("ts_code", symbol);
            row.put("trade_date", date);
            row.put("amount", new BigDecimal("1.000000000000000001"));
            row.put("note", null);
            row.put("source_plugin", definition.datasetKey().pluginId().value());
            row.put("source_api", definition.datasetKey().apiName().value());
            row.put("ingested_at", NOW);
            return Collections.unmodifiableMap(row);
        }).toList();
        var budget = new IntegrityReadBudget(100, NOW.plusSeconds(120), CLOCK);
        var session = new IntegrityReadRepository.ReadSession() {
            public IntegrityScope scope() { return snapshot; }
            public void scanTarget(Consumer<IntegrityReadRepository.TargetBatch> consumer) {
                budget.consume(data.size());
                if (!data.isEmpty()) consumer.accept(new IntegrityReadRepository.TargetBatch(false, data));
            }
            public void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> consumer) {
                throw new AssertionError("Existing fixture must not scan references or invoke source download");
            }
            public List<IntegrityReadRequest> readRequests() { return List.of(); }
        };
        var repository = mock(IntegrityReadRepository.class);
        when(repository.withSnapshot(eq(scope), any(), same(budget), eq(2), any())).thenAnswer(call -> {
            var action = (Function<IntegrityReadRepository.ReadSession, Object>) call.getArgument(4);
            Object value = action.apply(session);
            budget.check();
            return value;
        });
        return new IntegrityUnitEvaluator(repository, CLOCK).evaluate(scope, new IntegrityReadPlan(capability, List.of()),
                definition, plugin.integrityRules(definition.datasetKey().apiName()), budget, 2, 100);
    }
}
