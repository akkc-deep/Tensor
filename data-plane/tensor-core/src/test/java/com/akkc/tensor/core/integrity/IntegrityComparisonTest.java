package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IntegrityComparisonTest {
    static final Instant ACCEPTED = Instant.parse("2026-09-16T00:00:00Z");
    static final Instant SNAPSHOT = ACCEPTED.plusSeconds(1);
    static final Clock CLOCK = Clock.fixed(SNAPSHOT, ZoneOffset.UTC);
    static final LocalDate FIRST = LocalDate.of(2026, 1, 1);
    static final DatasetKey KEY = new DatasetKey(new PluginId("fixture"), new ApiName("daily"));
    static final IntegrityRuleDescriptor COVERAGE = new IntegrityRuleDescriptor("fixture.coverage", "1", "coverage",
            IntegrityRuleDescriptor.Dimension.COVERAGE, List.of("ts_code", "trade_date"), List.of(), "test coverage");
    static final DatasetDefinition DEFINITION = new DatasetDefinition(KEY, "daily", "test", QueryMode.date_range,
            List.of(), TableName.from(KEY), List.of(
            new ColumnDefinition("ts_code", "symbol", LogicalType.STRING, false, 0, 32, null, null, List.of(), false),
            new ColumnDefinition("trade_date", "date", LogicalType.DATE, false, 1, null, null, null, List.of(), false)),
            new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")), List.of(), null);

    @Test
    void extraCannotOffsetMissingInTwentyExpectedKeys() {
        var rows = new ArrayList<>(rows(19));
        rows.add(row(21));
        var evaluation = evaluate(rows, rule(IntegrityExpectedKeys.Basis.PROVEN, keys(20)), 100, 100);
        var result = evaluation.result();
        assertThat(result.statistics()).isEqualTo(new IntegrityStatistics(20L, 20L, 19L, 1L, 0L, 1L, 0L));
        assertThat(result.statistics().coverageRate()).isEqualByComparingTo("0.950000");
        assertThat(result.coverageStatus()).isEqualTo(IntegrityStatus.FAIL);
        assertThat(result.overallStatus()).isEqualTo(IntegrityStatus.FAIL);
        assertThat(evaluation.issues()).extracting(i -> i.issue().type()).containsExactly(IntegrityIssue.Type.MISSING, IntegrityIssue.Type.EXTRA);
        assertThat(evaluation.issues()).extracting(i -> i.issue().date()).containsExactly(FIRST.plusDays(19), FIRST.plusDays(20));
        assertThat(evaluation.issues()).allMatch(i -> i.ruleId().equals(COVERAGE.ruleId()) && i.ruleVersion().equals("1"));
        assertThat(result.scope().snapshotStartedAt()).isEqualTo(SNAPSHOT);
    }

    @Test
    void provenEmptySetIsVerifiedAndExtraRowsRemainExtra() {
        var empty = evaluate(List.of(), rule(IntegrityExpectedKeys.Basis.PROVEN, List.of()), 10, 10);
        assertThat(empty.result().statistics()).isEqualTo(new IntegrityStatistics(0L,0L,0L,0L,0L,0L,0L));
        assertThat(empty.result().statistics().coverageRate()).isNull();
        assertThat(empty.result().reasonCode()).isEqualTo("VERIFIED_EMPTY");
        assertThat(empty.result().coverageStatus()).isEqualTo(IntegrityStatus.PASS);
        assertThat(empty.result().ruleResults().stream().filter(r -> r.descriptor().ruleId().startsWith("core.")))
                .allMatch(r -> r.status() == IntegrityStatus.NOT_APPLICABLE && r.reasonCode().equals("NO_ROWS"));
        var extra = evaluate(rows(1), rule(IntegrityExpectedKeys.Basis.PROVEN, List.of()), 10, 10);
        assertThat(extra.result().statistics().extraCount()).isEqualTo(1L);
        assertThat(extra.result().coverageStatus()).isEqualTo(IntegrityStatus.WARN);
    }

    @Test
    void unconfirmedNeverPublishesWholeRangeCountsOrPassEvenWithNoCandidateGap() {
        for (int actual : new int[]{0,19,20}) for (int expected : new int[]{0,20}) {
            var value = evaluate(rows(actual), rule(IntegrityExpectedKeys.Basis.UNCONFIRMED, keys(expected)), 100, 100);
            assertThat(value.result().statistics().expectedCount()).isNull();
            assertThat(value.result().statistics().matchedCount()).isNull();
            assertThat(value.result().statistics().missingCount()).isNull();
            assertThat(value.result().statistics().extraCount()).isNull();
            assertThat(value.result().statistics().coverageRate()).isNull();
            assertThat(value.result().coverageStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
            assertThat(value.issues()).allMatch(i -> i.issue().type() == IntegrityIssue.Type.SUSPECTED_MISSING);
        }
    }

    @Test
    void localConfirmationUpgradesCandidateInEitherArrivalOrder() {
        for (boolean first : new boolean[]{true,false}) {
            var custom = custom((scope, context, sink) -> {
                var found = new java.util.concurrent.atomic.AtomicBoolean();
                context.scan(new IntegrityReadRequest(KEY,List.of("ts_code","trade_date"),Map.of(),
                        "trade_date",scope.range(),false,"target"), batch -> batch.forEach(row -> {
                    if (FIRST.plusDays(18).equals(row.get("trade_date"))) found.set(true);
                }));
                assertThat(found).isFalse();
                var local = missing(scope, 19);
                if (first) sink.add(local);
                var evidence = List.of(new IntegrityEvidence("synthetic", "1", scope.range(), scope.snapshotStartedAt(), "unknown elsewhere"));
                var stats = context.compare(new IntegrityExpectedKeys(scope, IntegrityExpectedKeys.Basis.UNCONFIRMED, keys(20), evidence));
                if (!first) sink.add(local);
                return new IntegrityRuleResult(COVERAGE, IntegrityStatus.UNKNOWN, "EXPECTED_SET_UNPROVEN", "unknown elsewhere", stats, evidence);
            });
            var value = evaluate(rows(18), custom, 100, 2);
            assertThat(value.issues()).hasSize(2);
            assertThat(value.issues().stream().filter(i -> i.issue().date().equals(FIRST.plusDays(18))))
                    .allMatch(i -> i.issue().type() == IntegrityIssue.Type.MISSING);
            assertThat(value.result().statistics()).isEqualTo(new IntegrityStatistics(18L,null,null,1L,1L,null,0L));
            assertThat(value.result().coverageStatus()).isEqualTo(IntegrityStatus.FAIL);
            assertThat(value.result().ruleResults().getLast().reasonCode()).isEqualTo("EXPECTED_SET_UNPROVEN");
        }
    }

    @Test
    void lateInvalidExpectedKeyLeavesNoPartialDifference() {
        var invalid = new ArrayList<>(keys(3)); invalid.add(Map.of("ts_code", "OTHER", "trade_date", FIRST));
        var value = evaluate(List.of(), rule(IntegrityExpectedKeys.Basis.PROVEN, invalid), 100, 10);
        assertThat(value.result().unitStatus()).isEqualTo(IntegrityUnitStatus.COMPLETED);
        assertThat(value.result().coverageStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
        assertThat(value.result().statistics().expectedCount()).isNull();
        assertThat(value.issues()).singleElement().satisfies(bound -> {
            assertThat(bound.issue().type()).isEqualTo(IntegrityIssue.Type.RULE_EXECUTION_FAILED);
            assertThat(bound.issue().message()).contains("INVALID_EXPECTED_KEY");
        });
    }

    @Test
    void ordinaryIteratorFailureIsUnknownAndNeverAnEmptyExpectedSet() {
        Iterable<Map<String,Object>> broken = () -> new Iterator<>() {
            public boolean hasNext() { throw new IllegalStateException("secret credentials"); }
            public Map<String,Object> next() { throw new NoSuchElementException(); }
        };
        var value = evaluate(rows(1), rule(IntegrityExpectedKeys.Basis.PROVEN, broken), 100, 10);
        assertThat(value.result().unitStatus()).isEqualTo(IntegrityUnitStatus.COMPLETED);
        assertThat(value.result().coverageStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
        assertThat(value.issues()).allMatch(i -> !i.issue().message().contains("secret"));
    }

    @Test
    void repeatedExpectedKeysConsumeSharedBudgetAndCaughtOverflowStillTerminates() {
        var duplicate = List.of(key(1), key(1));
        assertThat(evaluate(rows(2), rule(IntegrityExpectedKeys.Basis.PROVEN, duplicate), 4, 10).result().unitStatus())
                .isEqualTo(IntegrityUnitStatus.COMPLETED);
        var caught = custom((scope, context, sink) -> {
            try { context.compare(new IntegrityExpectedKeys(scope, IntegrityExpectedKeys.Basis.PROVEN, duplicate,
                    List.of(new IntegrityEvidence("test", "1", scope.range(), scope.snapshotStartedAt(), "test")))); }
            catch (RuntimeException ignored) { }
            return new IntegrityRuleResult(COVERAGE, IntegrityStatus.PASS, "PASS", "pass", new IntegrityStatistics(null,null,null,null,null,null,null), List.of());
        });
        var stopped = evaluate(rows(2), caught, 3, 10);
        assertThat(stopped.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
        assertThat(stopped.result().reasonCode()).isEqualTo("SCAN_LIMIT_EXCEEDED");
        assertThat(stopped.result().statistics().actualCount()).isEqualTo(2L);
        assertThat(stopped.result().statistics().expectedCount()).isNull();
        assertThat(stopped.result().issuesComplete()).isFalse();
    }

    @Test
    void issueLimitRetainsExactlyTheLimitAndConfirmedFailures() {
        var exact = evaluate(List.of(), rule(IntegrityExpectedKeys.Basis.PROVEN, keys(2)), 10, 2);
        assertThat(exact.result().unitStatus()).isEqualTo(IntegrityUnitStatus.COMPLETED);
        var exceeded = evaluate(List.of(), rule(IntegrityExpectedKeys.Basis.PROVEN, keys(3)), 10, 2);
        assertThat(exceeded.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
        assertThat(exceeded.result().reasonCode()).isEqualTo("ISSUE_LIMIT_EXCEEDED");
        assertThat(exceeded.issues()).hasSize(2).allMatch(i -> i.issue().incomplete());
        assertThat(exceeded.result().overallStatus()).isEqualTo(IntegrityStatus.FAIL);
        assertThat(exceeded.result().statistics().missingCount()).isEqualTo(2L);
        assertThat(exceeded.result().statistics().expectedCount()).isNull();
    }

    @Test
    void ruleCannotRetainContextOrSinkAfterItsInvocation() {
        var contextRef = new java.util.concurrent.atomic.AtomicReference<IntegrityContext>();
        var sinkRef = new java.util.concurrent.atomic.AtomicReference<IntegrityIssueSink>();
        var value = evaluate(rows(1), custom((scope, context, sink) -> {
            contextRef.set(context); sinkRef.set(sink);
            return rule(IntegrityExpectedKeys.Basis.PROVEN, keys(1)).evaluate(scope, context, sink);
        }), 10, 10);
        assertThat(value.result().overallStatus()).isEqualTo(IntegrityStatus.PASS);
        assertThatThrownBy(() -> contextRef.get().compare(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> sinkRef.get().add(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongIssueOwnershipAndSecondComparisonBecomeRuleFailures() {
        for (boolean wrongOwner : new boolean[]{true,false}) {
            var value = evaluate(List.of(), custom((scope, context, sink) -> {
                if (wrongOwner) {
                    IntegrityIssue issue = missing(scope, 1);
                    sink.add(new IntegrityIssue(issue.type(), issue.status(), "OTHER", issue.apiName(), issue.dateField(), issue.date(),
                            issue.businessKey(), issue.field(), issue.relatedDates(), issue.reasonCode(), issue.message(), issue.evidence(), false));
                }
                rule(IntegrityExpectedKeys.Basis.PROVEN, List.of()).evaluate(scope, context, sink);
                return rule(IntegrityExpectedKeys.Basis.PROVEN, List.of()).evaluate(scope, context, sink);
            }), 10, 10);
            assertThat(value.result().coverageStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
            assertThat(value.issues()).singleElement().satisfies(i -> assertThat(i.issue().type()).isEqualTo(IntegrityIssue.Type.RULE_EXECUTION_FAILED));
        }
    }

    @Test
    void exactDecimalAndLargeLongMatchThroughBothKeyModes() {
        for (BusinessKeyMode mode : BusinessKeyMode.values()) {
            var columns = new ArrayList<>(DEFINITION.columns());
            columns.add(new ColumnDefinition("amount", "amount", LogicalType.DECIMAL, false, 2, null, 6, 2, List.of(), false));
            columns.add(new ColumnDefinition("sequence", "sequence", LogicalType.LONG, false, 3, null, null, null, List.of(), false));
            var definition = definition(columns, List.of("ts_code","trade_date","amount","sequence"), mode);
            var expected = new LinkedHashMap<>(key(1)); expected.put("amount", new java.math.BigDecimal("1.0")); expected.put("sequence", 9007199254740993L);
            var actual = new LinkedHashMap<>(row(1)); actual.put("amount", new java.math.BigDecimal("1.00")); actual.put("sequence", 9007199254740993L);
            if (mode == BusinessKeyMode.FINGERPRINT) actual.put("business_key",
                    new com.akkc.tensor.core.adapter.FingerprintKeyCodec().sha256(definition.businessKey().fields(), actual));
            var harness = new Harness().rows(List.of(actual)).rules(List.of(rule(IntegrityExpectedKeys.Basis.PROVEN,List.of(expected))));
            harness.definition = definition;
            var value = harness.run(10,10);
            assertThat(value.result().statistics().matchedCount()).isEqualTo(1L);
            assertThat(value.result().overallStatus()).isEqualTo(IntegrityStatus.PASS);
            assertThat(value.issues()).isEmpty();
            assertThat(new IntegrityCheckJson().write(expected)).contains("\"9007199254740993\"");
        }
    }

    @Test
    void fullFinancialVersionKeyPreservesAnnouncementAxisAndRelatedPeriodDate() {
        var columns = List.of(DEFINITION.columns().getFirst(), dateColumn("end_date",1),
                new ColumnDefinition("report_type","type",LogicalType.STRING,false,2,8,null,null,List.of(),false),dateColumn("ann_date",3));
        var definition = definition(columns,List.of("ts_code","end_date","report_type","ann_date"),BusinessKeyMode.COMPOSITE);
        var expected = new ArrayList<Map<String,Object>>();
        for (String type : List.of("A","B")) for (int day : new int[]{1,2}) expected.add(Map.of("ts_code","PROVEN",
                "end_date", LocalDate.of(2025,12,31),"report_type",type,"ann_date",FIRST.plusDays(day-1)));
        var actual = expected.subList(0,3).stream().map(IntegrityComparisonTest::withSource).toList();
        var harness = new Harness().rows(actual).rules(List.of(rule(IntegrityExpectedKeys.Basis.PROVEN,expected)));
        harness.definition = definition; harness.axis = "ann_date";
        var value = harness.run(20,10);
        assertThat(value.result().statistics()).isEqualTo(new IntegrityStatistics(3L,4L,3L,1L,0L,0L,0L));
        assertThat(value.issues()).singleElement().satisfies(bound -> {
            assertThat(bound.issue().businessKey()).isEqualTo(expected.getLast());
            assertThat(bound.issue().dateField()).isEqualTo("ann_date");
            assertThat(bound.issue().date()).isEqualTo(FIRST.plusDays(1));
            assertThat(bound.issue().relatedDates()).containsExactlyEntriesOf(Map.of("end_date",LocalDate.of(2025,12,31)));
        });
    }

    @Test
    void independentRuleFailureCannotEraseConfirmedMissingAndOrderingIsStable() {
        var badDescriptor = new IntegrityRuleDescriptor("aaa.field", "1", "field", IntegrityRuleDescriptor.Dimension.FIELD,List.of(),List.of(),"field");
        var bad = new IntegrityRule() {
            public IntegrityRuleDescriptor descriptor() { return badDescriptor; }
            public IntegrityRuleResult evaluate(IntegrityScope scope,IntegrityContext context,IntegrityIssueSink sink) { throw new IllegalStateException("private secret"); }
        };
        var coverage = custom((scope,context,sink) -> {
            sink.add(missing(scope,1));
            return new IntegrityRuleResult(COVERAGE,IntegrityStatus.PASS,"PASS","pass",new IntegrityStatistics(null,null,null,null,null,null,null),List.of());
        });
        for (var rules : List.of(List.of(coverage,bad),List.of(bad,coverage))) {
            var result = new Harness().rows(rows(1)).rules(rules).run(10,10);
            assertThat(result.result().overallStatus()).isEqualTo(IntegrityStatus.FAIL);
            assertThat(result.result().fieldStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
            assertThat(result.result().ruleResults()).extracting(r -> r.descriptor().ruleId()).isSorted();
            assertThat(result.issues()).extracting(i -> i.issue().type()).containsExactly(IntegrityIssue.Type.RULE_EXECUTION_FAILED,IntegrityIssue.Type.MISSING);
        }
    }

    @Test
    void staleExpectedScopeAndReadTimeAreRejected() {
        for (boolean wrongScope : new boolean[]{true,false}) {
            var value = evaluate(List.of(),custom((scope,context,sink) -> {
                var expectedScope = wrongScope ? new IntegrityScope(KEY,"PROVEN",FIRST,FIRST.plusDays(19),ACCEPTED,SNAPSHOT) : scope;
                var evidence = List.of(new IntegrityEvidence("test","1",scope.range(), wrongScope ? SNAPSHOT : SNAPSHOT.minusSeconds(1),"test"));
                var stats = context.compare(new IntegrityExpectedKeys(expectedScope,IntegrityExpectedKeys.Basis.PROVEN,keys(1),evidence));
                return new IntegrityRuleResult(COVERAGE,IntegrityStatus.PASS,"PASS","pass",stats,evidence);
            }),10,10);
            assertThat(value.result().coverageStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
            assertThat(value.result().statistics().expectedCount()).isNull();
            assertThat(value.issues()).singleElement().satisfies(i -> assertThat(i.issue().message()).contains("INVALID_EXPECTED_KEY"));
        }
    }

    @Test
    void failedTargetScanCannotPublishItsPartialActualCount() {
        var harness = new Harness().rows(rows(2)).rules(List.of(rule(IntegrityExpectedKeys.Basis.PROVEN,keys(2))));
        harness.failScan = true;
        var value = harness.run(20,10);
        assertThat(value.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
        assertThat(value.result().statistics().actualCount()).isNull();
        assertThat(value.result().ruleResults()).isEmpty();
        assertThat(value.result().scope().snapshotStartedAt()).isEqualTo(SNAPSHOT);
    }

    @Test
    void postActionCleanupFailureInvalidatesResultButRetainsKnownFailures() {
        var harness = new Harness().rows(rows(1)).rules(List.of(rule(IntegrityExpectedKeys.Basis.PROVEN,keys(2))));
        harness.afterAction = () -> { throw new IntegrityReadException("READ_FAILED","private cleanup failure"); };
        var value = harness.run(20,10);
        assertThat(value.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
        assertThat(value.result().statistics().actualCount()).isEqualTo(1L);
        assertThat(value.result().statistics().matchedCount()).isNull();
        assertThat(value.result().statistics().coverageRate()).isNull();
        assertThat(value.result().ruleResults()).allSatisfy(result -> {
            assertThat(result.statistics().expectedCount()).isNull();
            assertThat(result.statistics().matchedCount()).isNull();
            assertThat(result.statistics().coverageRate()).isNull();
        });
        assertThat(value.result().overallStatus()).isEqualTo(IntegrityStatus.FAIL);
        assertThat(value.issues()).singleElement().satisfies(i -> assertThat(i.issue().incomplete()).isTrue());
    }

    @Test
    void deadlineDuringExpectedGenerationAfterRuleReturnAndAfterActionTerminates() {
        for (int phase : new int[]{0,1,2}) {
            var clock = new MutableClock();
            var harness = new Harness().rows(rows(1)); harness.clock = clock;
            Iterable<Map<String,Object>> expected = phase == 0 ? () -> new Iterator<>() {
                public boolean hasNext() { clock.now = SNAPSHOT.plusSeconds(60); return true; }
                public Map<String,Object> next() { return key(1); }
            } : keys(2);
            harness.rules = List.of(custom((scope,context,sink) -> {
                if (phase == 0) sink.add(missing(scope,2));
                var result = rule(IntegrityExpectedKeys.Basis.PROVEN,expected).evaluate(scope,context,sink);
                if (phase == 1) clock.now = SNAPSHOT.plusSeconds(60);
                return result;
            }));
            if (phase == 2) harness.afterAction = () -> clock.now = SNAPSHOT.plusSeconds(60);
            var value = harness.run(20,10);
            assertThat(value.result().reasonCode()).isEqualTo("UNIT_TIME_BUDGET_EXHAUSTED");
            assertThat(value.result().unitStatus()).isEqualTo(IntegrityUnitStatus.ERROR);
            assertThat(value.result().issuesComplete()).isFalse();
            assertThat(value.result().overallStatus()).isEqualTo(IntegrityStatus.FAIL);
            assertThat(value.issues()).allMatch(i -> i.issue().incomplete());
        }
    }

    @Test
    void crossThreadContextAndSinkRejectUseDuringInvocation() {
        var value = evaluate(rows(1),custom((scope,context,sink) -> {
            var failures = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
            Thread thread = new Thread(() -> {
                try { context.compare(null); } catch (Throwable failure) { failures.add(failure); }
                try { sink.add(missing(scope,2)); } catch (Throwable failure) { failures.add(failure); }
            });
            thread.start();
            try { thread.join(); } catch (InterruptedException exception) { throw new IllegalStateException(exception); }
            assertThat(failures).hasSize(2).allMatch(f -> f instanceof IllegalStateException);
            return rule(IntegrityExpectedKeys.Basis.PROVEN,keys(1)).evaluate(scope,context,sink);
        }),10,10);
        assertThat(value.result().overallStatus()).isEqualTo(IntegrityStatus.PASS);
    }

    @Test
    void gapIssuesRequireCompleteKeysAndConsistentStockAndDate() {
        var otherStock = new LinkedHashMap<>(key(1)); otherStock.put("ts_code", "OTHER");
        var outside = new LinkedHashMap<>(key(1)); outside.put("trade_date", FIRST.minusDays(1));
        for (Map<String,Object> invalid : List.of(Map.<String,Object>of(),otherStock,key(2),outside)) {
            var value = evaluate(List.of(),custom((scope,context,sink) -> {
                sink.add(new IntegrityIssue(IntegrityIssue.Type.MISSING,IntegrityStatus.FAIL,scope.symbol(),KEY.apiName(),
                        "trade_date",FIRST,invalid,null,Map.of(),"LOCAL_CONFIRMED","local gap",List.of(),false));
                return new IntegrityRuleResult(COVERAGE,IntegrityStatus.PASS,"PASS","pass",
                        new IntegrityStatistics(null,null,null,null,null,null,null),List.of());
            }),10,10);
            assertThat(value.result().coverageStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
            assertThat(value.issues()).singleElement().satisfies(bound -> {
                assertThat(bound.issue().type()).isEqualTo(IntegrityIssue.Type.RULE_EXECUTION_FAILED);
                assertThat(bound.issue().businessKey()).isEmpty();
            });
        }
    }

    @Test
    void malformedActualKeyKeepsKeyFailureAndMakesMissingOnlySuspected() {
        var row = new LinkedHashMap<>(row(1)); row.put("ts_code", "  ");
        var value = evaluate(List.of(row),rule(IntegrityExpectedKeys.Basis.PROVEN,keys(1)),10,10);
        assertThat(value.result().statistics().actualCount()).isZero();
        assertThat(value.result().statistics().expectedCount()).isNull();
        assertThat(value.result().statistics().matchedCount()).isNull();
        assertThat(value.result().coverageStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
        assertThat(value.result().keyStatus()).isEqualTo(IntegrityStatus.FAIL);
        assertThat(value.issues()).extracting(i -> i.issue().type()).contains(IntegrityIssue.Type.BUSINESS_KEY_INVALID,
                IntegrityIssue.Type.SUSPECTED_MISSING).doesNotContain(IntegrityIssue.Type.MISSING);
    }

    @Test
    void endlessDuplicateExpectedStreamAndRepeatedScansShareTheItemBudget() {
        Iterable<Map<String,Object>> endless = () -> new Iterator<>() {
            public boolean hasNext() { return true; }
            public Map<String,Object> next() { return key(1); }
        };
        var stopped = evaluate(rows(1),rule(IntegrityExpectedKeys.Basis.PROVEN,endless),4,10);
        assertThat(stopped.result().reasonCode()).isEqualTo("SCAN_LIMIT_EXCEEDED");
        assertThat(stopped.issues()).isEmpty();
        var scanning = custom((scope,context,sink) -> {
            var request = new IntegrityReadRequest(KEY,List.of("ts_code","trade_date"),Map.of(),"trade_date",scope.range(),false,"target");
            context.scan(request,rows -> {}); context.scan(request,rows -> {});
            return new IntegrityRuleResult(COVERAGE,IntegrityStatus.UNKNOWN,"EXPECTED_SET_UNPROVEN","unknown",
                    new IntegrityStatistics(null,null,null,null,null,null,null),List.of());
        });
        assertThat(evaluate(rows(2),scanning,6,10).result().unitStatus()).isEqualTo(IntegrityUnitStatus.COMPLETED);
        assertThat(evaluate(rows(2),scanning,5,10).result().reasonCode()).isEqualTo("SCAN_LIMIT_EXCEEDED");
    }

    @Test
    void roundedCoverageNeverOverridesAnActualMissingIssue() {
        var rounded = new IntegrityStatistics(2000000L,2000001L,2000000L,1L,0L,0L,null);
        assertThat(rounded.coverageRate()).isEqualByComparingTo("1.000000");
        var value = evaluate(rows(1),custom((scope,context,sink) -> {
            sink.add(missing(scope,2));
            return new IntegrityRuleResult(COVERAGE,IntegrityStatus.PASS,"PASS","pass",
                    new IntegrityStatistics(null,null,null,null,null,null,null),List.of());
        }),10,10);
        assertThat(value.result().overallStatus()).isEqualTo(IntegrityStatus.FAIL);
    }

    static final class MutableClock extends Clock {
        Instant now = SNAPSHOT;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static ColumnDefinition dateColumn(String name,int order) { return new ColumnDefinition(name,name,LogicalType.DATE,false,order,null,null,null,List.of(),false); }
    static DatasetDefinition definition(List<ColumnDefinition> columns,List<String> key,BusinessKeyMode mode) {
        return new DatasetDefinition(KEY,"test","test",QueryMode.date_range,List.of(),TableName.from(KEY),columns,
                new BusinessKeyDefinition(mode,key),List.of(),null);
    }
    static Map<String,Object> withSource(Map<String,Object> key) {
        var actual = new LinkedHashMap<>(key); actual.put("source_plugin","fixture"); actual.put("source_api","daily"); return actual;
    }

    static IntegrityIssue missing(IntegrityScope scope, int day) {
        return new IntegrityIssue(IntegrityIssue.Type.MISSING, IntegrityStatus.FAIL, scope.symbol(), scope.datasetKey().apiName(),
                "trade_date", FIRST.plusDays(day-1), key(day), null, Map.of(), "LOCAL_CONFIRMED", "local gap",
                List.of(new IntegrityEvidence("local", "1", new IntegrityDateRange(FIRST.plusDays(day-1), FIRST.plusDays(day-1)), scope.snapshotStartedAt(), "local confirmed")), false);
    }
    @FunctionalInterface interface EvaluationBody {
        IntegrityRuleResult run(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink sink);
    }
    static IntegrityRule custom(EvaluationBody body) {
        return new IntegrityRule() {
            public IntegrityRuleDescriptor descriptor() { return COVERAGE; }
            public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink sink) { return body.run(scope,context,sink); }
        };
    }

    static IntegrityUnitEvaluator.Evaluation evaluate(List<Map<String,Object>> rows, IntegrityRule rule, long items, int issues) {
        return new Harness().rows(rows).rules(List.of(rule)).run(items, issues);
    }

    static final class Harness {
        DatasetDefinition definition = DEFINITION;
        String axis = "trade_date";
        List<IntegrityReadRepository.TargetBatch> batches = List.of();
        List<IntegrityRule> rules = List.of();
        Clock clock = CLOCK;
        Runnable afterAction = () -> {};
        boolean failScan;
        Harness rows(List<Map<String,Object>> rows) { batches = List.of(new IntegrityReadRepository.TargetBatch(false, rows)); return this; }
        Harness rules(List<IntegrityRule> rules) { this.rules = rules; return this; }
        IntegrityUnitEvaluator.Evaluation run(long items, int issues) {
            var repository = mock(IntegrityReadRepository.class);
            var budget = new IntegrityReadBudget(items, SNAPSHOT.plusSeconds(60), clock);
            var scope = new IntegrityScope(definition.datasetKey(), "PROVEN", FIRST, FIRST.plusDays(20), ACCEPTED, null);
            var target = new IntegrityDescriptor(definition.datasetKey(), IntegrityDescriptor.ScopeKind.STOCK_DATE, "ts_code", axis, "date",
                    ZoneOffset.UTC, "1", List.of(), rules.stream().map(IntegrityRule::descriptor).toList(), List.of());
            when(repository.withSnapshot(any(), any(), any(), anyInt(), any())).thenAnswer(invocation -> {
                IntegrityReadRepository.ReadSession session = new IntegrityReadRepository.ReadSession() {
                    public IntegrityScope scope() { return new IntegrityScope(definition.datasetKey(), "PROVEN", FIRST, FIRST.plusDays(20), ACCEPTED, SNAPSHOT); }
                    public void scanTarget(Consumer<IntegrityReadRepository.TargetBatch> consumer) {
                        for (var batch : batches) {
                            batch.rows().forEach(row -> budget.consume(1));
                            consumer.accept(batch);
                        }
                        if (failScan) throw new IntegrityReadException("READ_FAILED", "private database message");
                    }
                    public void scan(IntegrityReadRequest request, Consumer<List<Map<String,Object>>> consumer) {
                        for (var batch : batches) { batch.rows().forEach(row -> budget.consume(1)); consumer.accept(batch.rows()); }
                    }
                    public List<IntegrityReadRequest> readRequests() { return List.of(); }
                };
                Object value = ((Function<IntegrityReadRepository.ReadSession, ?>) invocation.getArgument(4)).apply(session);
                afterAction.run(); budget.check();
                return value;
            });
            return new IntegrityUnitEvaluator(repository, clock).evaluate(scope, new IntegrityReadPlan(target, List.of()),
                    definition, rules, budget, 2, issues);
        }
    }

    static IntegrityRule rule(IntegrityExpectedKeys.Basis basis, Iterable<Map<String,Object>> keys) {
        return new IntegrityRule() {
            public IntegrityRuleDescriptor descriptor() { return COVERAGE; }
            public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink sink) {
                var evidence = List.of(new IntegrityEvidence("synthetic", "1", scope.range(), scope.snapshotStartedAt(), "test keys"));
                var statistics = context.compare(new IntegrityExpectedKeys(scope, basis, keys, evidence));
                return new IntegrityRuleResult(COVERAGE, IntegrityStatus.PASS, "TEST", "test", statistics, evidence);
            }
        };
    }
    static Map<String,Object> key(int day) { return Map.of("ts_code", "PROVEN", "trade_date", FIRST.plusDays(day - 1)); }
    static Map<String,Object> row(int day) { var row = new LinkedHashMap<>(key(day)); row.put("source_plugin", "fixture"); row.put("source_api", "daily"); return row; }
    static List<Map<String,Object>> keys(int count) { return IntStream.rangeClosed(1, count).mapToObj(IntegrityComparisonTest::key).toList(); }
    static List<Map<String,Object>> rows(int count) { return IntStream.rangeClosed(1, count).mapToObj(IntegrityComparisonTest::row).toList(); }
}
