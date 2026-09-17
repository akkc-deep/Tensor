package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityStatus.*;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.catalog.*;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IntegrityComparisonIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6");
    static final Instant NOW = Instant.parse("2026-09-16T00:01:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final LocalDate FIRST = LocalDate.of(2026, 1, 1);
    static final IntegrityDateRange RANGE = new IntegrityDateRange(FIRST, FIRST.plusDays(20));
    static final DatasetKey DAILY = key("daily"), EVENTS = key("events"), PRICES = key("prices"), REF = key("reference");
    static final DatasetDefinition DAILY_DEF = definition(DAILY, List.of(
            column("symbol", LogicalType.STRING, false, 0), column("day", LogicalType.DATE, false, 1)),
            BusinessKeyMode.COMPOSITE, List.of("symbol", "day"));
    static final DatasetDefinition EVENTS_DEF = definition(EVENTS, List.of(
            column("symbol", LogicalType.STRING, false, 0), column("event", LogicalType.LONG, false, 1),
            column("day", LogicalType.DATE, true, 2)), BusinessKeyMode.COMPOSITE, List.of("symbol", "event"));
    static final DatasetDefinition PRICES_DEF = definition(PRICES, List.of(
            column("symbol", LogicalType.STRING, false, 0), column("day", LogicalType.DATE, false, 1),
            column("event", LogicalType.LONG, false, 2), column("amount", LogicalType.DECIMAL, true, 3)),
            BusinessKeyMode.FINGERPRINT, List.of("symbol", "day", "event", "amount"));
    static final DatasetDefinition REF_DEF = definition(REF, List.of(
            column("symbol", LogicalType.STRING, false, 0), column("day", LogicalType.DATE, false, 1)),
            BusinessKeyMode.COMPOSITE, List.of("symbol", "day"));
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    IntegrityUnitEvaluator evaluator;

    @BeforeAll static void schema() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        for (String api : List.of("daily", "reference"))
            jdbc.execute("CREATE TABLE comparison__" + api + " (symbol VARCHAR(32) NOT NULL, day DATE NOT NULL, "
                    + metadata(api) + ", PRIMARY KEY(symbol,day)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE comparison__events (symbol VARCHAR(32) NOT NULL, event BIGINT NOT NULL, day DATE, "
                + metadata("events") + ", PRIMARY KEY(symbol,event)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE comparison__prices (symbol VARCHAR(32) NOT NULL, day DATE NOT NULL, event BIGINT NOT NULL, "
                + "amount DECIMAL(6,2), business_key CHAR(64) NOT NULL, " + metadata("prices")
                + ", PRIMARY KEY(business_key)) ENGINE=InnoDB");
    }

    @BeforeEach void reset() {
        for (String api : List.of("daily", "reference", "events", "prices")) jdbc.update("DELETE FROM comparison__" + api);
        var catalog = new DatasetStartupValidator(List.of(DAILY_DEF, EVENTS_DEF, PRICES_DEF, REF_DEF),
                new SchemaInspector(dataSource)).validate();
        assertThat(catalog.list(DAILY.pluginId())).hasSize(4);
        evaluator = new IntegrityUnitEvaluator(new IntegrityReadRepository(dataSource, catalog, CLOCK), CLOCK);
    }

    @Test void extraRowCannotOffsetMissingDayAcrossRealKeysetPages() {
        IntStream.rangeClosed(1, 19).forEach(this::daily);
        daily(21);
        var before = contents("daily");
        var target = descriptor(DAILY, List.of());
        var rule = comparisonRule(target, dayKeys(20), null);
        var evaluation = evaluate(DAILY_DEF, new IntegrityReadPlan(target, List.of()), rule, 100, 100);
        var result = evaluation.result();
        assertThat(result.unitStatus()).isEqualTo(IntegrityUnitStatus.COMPLETED);
        assertThat(result.statistics()).isEqualTo(new IntegrityStatistics(20L, 20L, 19L, 1L, 0L, 1L, 0L));
        assertThat(result.statistics().coverageRate()).isEqualByComparingTo("0.950000");
        assertThat(result.coverageStatus()).isEqualTo(FAIL);
        assertThat(result.overallStatus()).isEqualTo(FAIL);
        assertThat(result.keyStatus()).isEqualTo(PASS);
        assertThat(result.fieldStatus()).isEqualTo(PASS);
        assertThat(result.scope().snapshotStartedAt()).isEqualTo(NOW);
        assertThat(evaluation.issues()).hasSize(2).anySatisfy(bound -> {
            assertThat(bound.issue().type()).isEqualTo(IntegrityIssue.Type.MISSING);
            assertThat(bound.issue().date()).isEqualTo(FIRST.plusDays(19));
        }).anySatisfy(bound -> {
            assertThat(bound.issue().type()).isEqualTo(IntegrityIssue.Type.EXTRA);
            assertThat(bound.issue().date()).isEqualTo(FIRST.plusDays(20));
        });
        assertThat(contents("daily")).isEqualTo(before);
    }

    @Test void unresolvedDateCannotBeMatchedOrDeclaredDefinitelyAbsent() {
        jdbc.update("INSERT INTO comparison__events(symbol,event,day) VALUES ('PROVEN',1,?),('PROVEN',2,NULL)", FIRST);
        var target = descriptor(EVENTS, List.of());
        var expected = List.<Map<String, Object>>of(Map.of("symbol", "PROVEN", "event", 1L),
                Map.of("symbol", "PROVEN", "event", 2L));
        var evaluation = evaluate(EVENTS_DEF, new IntegrityReadPlan(target, List.of()),
                comparisonRule(target, expected, null), 100, 100);
        assertThat(evaluation.result().unitStatus()).isEqualTo(IntegrityUnitStatus.COMPLETED);
        assertThat(evaluation.result().incomplete()).isFalse();
        assertThat(evaluation.result().coverageStatus()).isEqualTo(UNKNOWN);
        assertThat(evaluation.result().statistics().actualCount()).isEqualTo(1L);
        assertThat(evaluation.result().statistics().expectedCount()).isNull();
        assertThat(evaluation.result().statistics().matchedCount()).isNull();
        assertThat(evaluation.result().statistics().coverageRate()).isNull();
        assertThat(evaluation.issues()).hasSize(2).allSatisfy(bound -> {
            assertThat(bound.issue().date()).isNull();
            assertThat(bound.issue().businessKey()).containsEntry("event", 2L);
            assertThat(bound.issue().type()).isIn(IntegrityIssue.Type.DATE_SCOPE_UNRESOLVED,
                    IntegrityIssue.Type.SUSPECTED_MISSING);
        });
    }

    @Test void logicalDecimalKeyStillMatchesWhenStoredFingerprintIsCorrupt() {
        jdbc.update("INSERT INTO comparison__prices(symbol,day,event,amount,business_key) VALUES ('PROVEN',?,?,?,?)",
                FIRST, 9007199254740993L, new BigDecimal("1.00"), "0".repeat(64));
        var target = descriptor(PRICES, List.of());
        var expected = List.<Map<String, Object>>of(Map.of("symbol", "PROVEN", "day", FIRST,
                "event", 9007199254740993L, "amount", new BigDecimal("1.0")));
        var evaluation = evaluate(PRICES_DEF, new IntegrityReadPlan(target, List.of()),
                comparisonRule(target, expected, null), 100, 100);
        assertThat(evaluation.result().statistics().matchedCount()).isEqualTo(1L);
        assertThat(evaluation.result().statistics().missingCount()).isZero();
        assertThat(evaluation.result().keyStatus()).isEqualTo(FAIL);
        assertThat(evaluation.result().overallStatus()).isEqualTo(FAIL);
        assertThat(evaluation.issues()).singleElement().satisfies(bound -> {
            assertThat(bound.ruleId()).isEqualTo("core.business-key");
            assertThat(bound.issue().reasonCode()).isEqualTo("FINGERPRINT_MISMATCH");
            assertThat(bound.issue().field()).isEqualTo("business_key");
            assertThat(bound.issue().businessKey()).containsEntry("event", 9007199254740993L)
                    .containsEntry("amount", new BigDecimal("1.00"));
        });
        var row = new LinkedHashMap<>(expected.getFirst());
        row.put("amount", new BigDecimal("1.00"));
        jdbc.update("UPDATE comparison__prices SET business_key=?",
                new FingerprintKeyCodec().sha256(PRICES_DEF.businessKey().fields(), row));
        var fixed = evaluate(PRICES_DEF, new IntegrityReadPlan(target, List.of()),
                comparisonRule(target, expected, null), 100, 100);
        assertThat(fixed.result().overallStatus()).isEqualTo(PASS);
    }

    @Test void targetReferenceAndDuplicateExpectedKeysShareOneBudget() {
        daily(1); daily(2);
        jdbc.update("INSERT INTO comparison__reference(symbol,day) VALUES ('PROVEN',?)", FIRST);
        var before = contents("daily");
        var referenceBefore = contents("reference");
        var dependency = new IntegrityDependency(REF, List.of("symbol", "day"), "baseline");
        var target = descriptor(DAILY, List.of(dependency));
        var plan = new IntegrityReadPlan(target, List.of(new IntegrityReadPlan.ReferencePermit(
                descriptor(REF, List.of()), "day", RANGE, Map.of(), "baseline")));
        var rule = comparisonRule(target, List.of(dayKeys(1).getFirst(), dayKeys(1).getFirst()),
                (scope, context) -> {
                    context.scan(new IntegrityReadRequest(REF, List.of("symbol", "day"), Map.of(),
                            "day", RANGE, false, "baseline"), rows -> assertThat(rows).hasSize(1));
                    return null;
                });
        assertThat(evaluate(DAILY_DEF, plan, rule, 5, 100).result().unitStatus()).isEqualTo(IntegrityUnitStatus.COMPLETED);
        var limited = evaluate(DAILY_DEF, plan, rule, 4, 100);
        assertThat(limited.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
        assertThat(limited.result().reasonCode()).isEqualTo("SCAN_LIMIT_EXCEEDED");
        assertThat(limited.result().statistics().coverageRate()).isNull();
        assertThat(limited.result().incomplete()).isTrue();
        assertThat(limited.result().issuesComplete()).isFalse();
        assertThat(contents("daily")).isEqualTo(before);
        assertThat(contents("reference")).isEqualTo(referenceBefore);
    }

    @Test void caughtSourceScanCallbackFailureStillAbortsWholeUnit() {
        daily(1);
        var target = descriptor(DAILY, List.of());
        var rule = comparisonRule(target, dayKeys(1), (scope, context) -> {
            try {
                context.scan(new IntegrityReadRequest(DAILY, List.of("symbol", "day"), Map.of(),
                        "day", RANGE, false, "target"), rows -> { throw new IllegalStateException("secret upstream token"); });
            } catch (RuntimeException ignored) { }
            return null;
        });
        var evaluation = evaluate(DAILY_DEF, new IntegrityReadPlan(target, List.of()), rule, 100, 100);
        assertThat(evaluation.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
        assertThat(evaluation.result().reasonCode()).isEqualTo("READ_FAILED");
        assertThat(evaluation.result().statistics().expectedCount()).isNull();
        assertThat(new IntegrityCheckJson().write(evaluation.result())).doesNotContain("secret upstream token");
    }

    @Test void issueLimitPreservesKnownFailuresAndLeavesDatabaseUnchanged() {
        var target = descriptor(DAILY, List.of());
        var evaluation = evaluate(DAILY_DEF, new IntegrityReadPlan(target, List.of()),
                comparisonRule(target, dayKeys(3), null), 100, 2);
        assertThat(evaluation.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
        assertThat(evaluation.result().reasonCode()).isEqualTo("ISSUE_LIMIT_EXCEEDED");
        assertThat(evaluation.result().statistics().expectedCount()).isNull();
        assertThat(evaluation.result().issuesComplete()).isFalse();
        assertThat(evaluation.result().overallStatus()).isEqualTo(FAIL);
        assertThat(evaluation.issues()).hasSize(2).allSatisfy(bound -> assertThat(bound.issue().incomplete()).isTrue());
        assertThat(contents("daily")).isEmpty();
    }

    @Test void callbackWrappingCannotHideLatchedIssueLimit() {
        daily(1);
        var target = descriptor(DAILY, List.of());
        var rule = new IntegrityRule() {
            public IntegrityRuleDescriptor descriptor() { return target.rules().getFirst(); }
            public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink sink) {
                var evidence = List.of(new IntegrityEvidence("test:local-proof", "1", RANGE, NOW, "Proven local gaps"));
                try {
                    context.scan(new IntegrityReadRequest(DAILY, List.of("symbol", "day"), Map.of(),
                            "day", RANGE, false, "target"), rows -> {
                        for (int day : List.of(2, 3)) {
                            var date = FIRST.plusDays(day - 1);
                            sink.add(new IntegrityIssue(IntegrityIssue.Type.MISSING, FAIL, scope.symbol(), DAILY.apiName(),
                                    "day", date, Map.of("symbol", "PROVEN", "day", date), null, Map.of(),
                                    "MISSING", "Confirmed local gap", evidence, false));
                        }
                    });
                } catch (RuntimeException ignored) { }
                return new IntegrityRuleResult(descriptor(), PASS, "CLAIMED_COMPLETE", "Must not publish success",
                        new IntegrityStatistics(1L, null, null, null, null, null, null), evidence);
            }
        };
        var evaluation = evaluate(DAILY_DEF, new IntegrityReadPlan(target, List.of()), rule, 100, 1);
        assertThat(evaluation.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
        assertThat(evaluation.result().reasonCode()).isEqualTo("ISSUE_LIMIT_EXCEEDED");
        assertThat(evaluation.result().overallStatus()).isEqualTo(FAIL);
        assertThat(evaluation.result().issuesComplete()).isFalse();
        assertThat(evaluation.issues()).singleElement().satisfies(bound -> {
            assertThat(bound.issue().date()).isEqualTo(FIRST.plusDays(1));
            assertThat(bound.issue().incomplete()).isTrue();
        });
    }

    private IntegrityUnitEvaluator.Evaluation evaluate(DatasetDefinition definition, IntegrityReadPlan plan,
            IntegrityRule rule, long maxItems, int maxIssues) {
        var scope = new IntegrityScope(definition.datasetKey(), "PROVEN", RANGE.startDate(), RANGE.endDate(),
                NOW.minusSeconds(60), null);
        return evaluator.evaluate(scope, plan, definition, List.of(rule),
                new IntegrityReadBudget(maxItems, NOW.plusSeconds(120), CLOCK), 2, maxIssues);
    }
    private IntegrityRule comparisonRule(IntegrityDescriptor target, List<Map<String, Object>> keys,
            BiFunction<IntegrityScope, IntegrityContext, Void> before) {
        return new IntegrityRule() {
            public IntegrityRuleDescriptor descriptor() { return target.rules().getFirst(); }
            public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink sink) {
                if (before != null) before.apply(scope, context);
                var evidence = List.of(new IntegrityEvidence("test:synthetic", "1", scope.range(),
                        scope.snapshotStartedAt(), "Synthetic complete set, not a production baseline"));
                var stats = context.compare(new IntegrityExpectedKeys(scope, IntegrityExpectedKeys.Basis.PROVEN, keys, evidence));
                return new IntegrityRuleResult(descriptor(), PASS, "TEST_SET", "Compare synthetic complete keys", stats, evidence);
            }
        };
    }
    private void daily(int day) { jdbc.update("INSERT INTO comparison__daily(symbol,day) VALUES ('PROVEN',?)", FIRST.plusDays(day - 1)); }
    private List<Map<String, Object>> contents(String api) { return jdbc.queryForList("SELECT * FROM comparison__" + api + " ORDER BY symbol"); }
    private List<Map<String, Object>> dayKeys(int days) {
        return IntStream.range(0, days).<Map<String, Object>>mapToObj(i -> Map.of("symbol", "PROVEN", "day", FIRST.plusDays(i))).toList();
    }
    private static DatasetKey key(String api) { return DatasetKey.of(PluginId.of("comparison"), ApiName.of(api)); }
    private static ColumnDefinition column(String name, LogicalType type, boolean nullable, int order) {
        return new ColumnDefinition(name, name, type, nullable, order, type == LogicalType.STRING ? 32 : null,
                type == LogicalType.DECIMAL ? 6 : null, type == LogicalType.DECIMAL ? 2 : null, List.of(), false);
    }
    private static DatasetDefinition definition(DatasetKey key, List<ColumnDefinition> columns, BusinessKeyMode mode, List<String> fields) {
        return new DatasetDefinition(key, key.apiName().value(), "test", QueryMode.snapshot, List.of(), TableName.from(key),
                columns, new BusinessKeyDefinition(mode, fields), List.of(), null);
    }
    private static IntegrityDescriptor descriptor(DatasetKey key, List<IntegrityDependency> dependencies) {
        var rule = new IntegrityRuleDescriptor("comparison.coverage." + key.apiName().value(), "1", "Coverage",
                IntegrityRuleDescriptor.Dimension.COVERAGE, List.of(), dependencies, "Synthetic exact comparison");
        return new IntegrityDescriptor(key, IntegrityDescriptor.ScopeKind.STOCK_DATE, "symbol", "day", "Date",
                ZoneOffset.UTC, "1", dependencies, List.of(rule), List.of());
    }
    private static String metadata(String api) {
        return "source_plugin VARCHAR(64) NOT NULL DEFAULT 'comparison', source_api VARCHAR(64) NOT NULL DEFAULT '"
                + api + "', ingested_at TIMESTAMP(3) NOT NULL DEFAULT '2026-01-01 00:00:00'";
    }
}
