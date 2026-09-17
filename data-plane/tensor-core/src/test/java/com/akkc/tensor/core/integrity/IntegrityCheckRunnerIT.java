package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.akkc.tensor.core.integrity.IntegrityCheckServiceTest.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.api.error.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class IntegrityCheckRunnerIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    final IntegrityCheckJson json = new IntegrityCheckJson();
    final CheckPlugin plugin = new CheckPlugin();
    IntegrityCheckRepository repository;
    IntegrityCheckService service;
    IntegrityCheckQueue queue;
    IntegrityCheckRunner runner;
    IntegrityCheckService.Settings settings = IntegrityCheckService.Settings.defaults();

    @BeforeAll static void schema() throws Exception {
        source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    "../tensor-app/src/main/resources/db/migration/V9__create_integrity_check_tables.sql"));
        }
        for (String api : List.of("daily", "snapshot")) jdbc.execute("CREATE TABLE local__" + api
                + " (symbol VARCHAR(255) NOT NULL, day DATE NOT NULL, source_plugin VARCHAR(64) DEFAULT 'local',"
                + " source_api VARCHAR(64) DEFAULT '" + api + "', ingested_at TIMESTAMP(3) DEFAULT '2026-01-01 00:00:00',"
                + " PRIMARY KEY(symbol,day)) ENGINE=InnoDB");
    }
    @BeforeEach void setup() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_issue");
        jdbc.update("DELETE FROM tensor_integrity_check_issue");
        jdbc.update("DELETE FROM tensor_integrity_check_result");
        jdbc.update("DELETE FROM tensor_integrity_check_task");
        jdbc.update("DELETE FROM local__daily"); jdbc.update("DELETE FROM local__snapshot");
        repository = spy(new IntegrityCheckRepository(jdbc, new DataSourceTransactionManager(source), json));
        assemble(CLOCK);
    }
    void assemble(Clock clock) {
        var plugins = new PluginRegistry(List.of(plugin));
        var catalog = catalog(plugin.definitions());
        queue = new IntegrityCheckQueue(20);
        service = new IntegrityCheckService(plugins, catalog, repository, json, queue, clock, settings);
        runner = new IntegrityCheckRunner(repository, service, plugins, json,
                new IntegrityUnitEvaluator(new IntegrityReadRepository(source, catalog, clock), clock), clock, settings);
    }
    @Test void persistsCompleteFixedPlanAndMissingKeysWithoutChangingSecurities() {
        jdbc.update("INSERT INTO local__daily(symbol,day) VALUES ('A','2026-01-01')");
        var before = jdbc.queryForList("SELECT * FROM local__daily");
        var task = service.submit(request()).task();
        runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(progress.completedUnits()).isEqualTo(7);
        assertThat(progress.errorUnits()).isZero();
        assertThat(progress.overallStatus()).isEqualTo(IntegrityStatus.FAIL);
        assertThat(repository.issues(task.checkId(), null, 1, 100).items()).hasSize(3);
        assertThat(jdbc.queryForList("SELECT * FROM local__daily")).isEqualTo(before);
        var reports = repository.results(task.checkId(), null, 1, 100).items();
        assertThat(reports).hasSize(7);
        assertThat(reports.stream().filter(r -> r.apiName().value().equals("non_stock"))).singleElement()
                .satisfies(r -> assertThat(r.report().get("overallStatus").asText()).isEqualTo("NOT_APPLICABLE"));
        assertThat(reports.stream().filter(r -> r.apiName().value().equals("missing"))).allSatisfy(r -> {
            assertThat(r.report().get("overallStatus").asText()).isEqualTo("UNKNOWN");
            assertThat(r.report().get("scope").get("snapshotStartedAt").isNull()).isTrue();
        });
    }
    @Test void acceptedDefinitionChangeNeverScansOrRewritesCompletedReports() {
        var task = service.submit(request()).task(); plugin.version = "2";
        runner.run(task.checkId(), () -> false);
        assertThat(repository.progress(task.checkId()).orElseThrow().errorUnits()).isEqualTo(7);
        var units = repository.results(task.checkId(), null, 1, 100).items();
        assertThat(units).allSatisfy(u -> {
            assertThat(u.report().get("reasonCode").asText()).isEqualTo("DEFINITION_CHANGED");
            assertThat(u.report().get("scope").get("snapshotStartedAt").isNull()).isTrue();
        });
        runner.run(task.checkId(), () -> false);
        assertThat(repository.results(task.checkId(), null, 1, 100).items()).isEqualTo(units);
    }

    @Test void unavailablePluginLeavesEveryOriginalUnitAsAnError() {
        var task = service.submit(request()).task();
        var empty = new PluginRegistry(List.of()); var catalog = catalog(plugin.definitions());
        var missing = new IntegrityCheckService(empty, catalog, repository, json, new IntegrityCheckQueue(20), CLOCK, settings);
        var unavailable = new IntegrityCheckRunner(repository, missing, empty, json,
                new IntegrityUnitEvaluator(new IntegrityReadRepository(source, catalog, CLOCK), CLOCK), CLOCK, settings);
        unavailable.run(task.checkId(), () -> false);
        assertThat(repository.progress(task.checkId()).orElseThrow().errorUnits()).isEqualTo(7);
        assertThat(repository.results(task.checkId(), null, 1, 100).items()).allSatisfy(u ->
                assertThat(u.report().get("reasonCode").asText()).isEqualTo("INTEGRITY_UNAVAILABLE"));
    }

    @Test void ruleFailureDoesNotHideIndependentConfirmedMissingKeys() {
        plugin.failRule = true;
        var request = request(); request.put("apiNames", List.of("daily"));
        var task = service.submit(request).task(); runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(progress.errorUnits()).isZero(); assertThat(progress.overallStatus()).isEqualTo(IntegrityStatus.FAIL);
        var issues = repository.issues(task.checkId(), null, 1, 100).items();
        assertThat(issues).hasSize(4).anySatisfy(i -> assertThat(i.issue().get("reasonCode").asText()).isEqualTo("RULE_EXECUTION_FAILED"))
                .anySatisfy(i -> assertThat(i.issue().get("type").asText()).isEqualTo("MISSING"));
        assertThat(plugin.executed).containsExactly("local.daily:A", "local.failure:A", "local.daily:B", "local.failure:B");
        assertThat(issues.toString()).doesNotContain("secret upstream");
    }

    @Test void scanFailureIsAUnitErrorAndOtherUnitsContinue() {
        var request = request(); request.put("apiNames", List.of("daily"));
        var task = service.submit(request).task();
        var reads = spy(new IntegrityReadRepository(source, catalog(plugin.definitions()), CLOCK));
        var first = new AtomicBoolean(true);
        doAnswer(call -> {
            if (first.getAndSet(false)) throw new IntegrityReadException("READ_FAILED", "Integrity read failed");
            return call.callRealMethod();
        }).when(reads).withSnapshot(any(), any(), any(), anyInt(), any());
        runner = new IntegrityCheckRunner(repository, service, new PluginRegistry(List.of(plugin)), json,
                new IntegrityUnitEvaluator(reads, CLOCK), CLOCK, settings);
        runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(progress.completedUnits()).isEqualTo(2); assertThat(progress.errorUnits()).isEqualTo(1);
        assertThat(repository.results(task.checkId(), null, 1, 100).items().getFirst().report().get("reasonCode").asText())
                .isEqualTo("READ_FAILED");
        assertThat(repository.issues(task.checkId(), null, 1, 100).total()).isOne();
    }

    @Test void failedIssueInsertRollsBackTheReportThenFailsTaskWithRemainingNotRun() {
        jdbc.execute("CREATE TRIGGER reject_issue BEFORE INSERT ON tensor_integrity_check_issue FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private SQL token'");
        var task = service.submit(request()).task(); runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.FAILED);
        assertThat(progress.task().errorCode()).isEqualTo("PERSISTENCE_FAILED");
        assertThat(progress.completedUnits()).isZero(); assertThat(progress.notRunUnits()).isEqualTo(7);
        assertThat(repository.issues(task.checkId(), null, 1, 100).items()).isEmpty();
        assertThat(progress.task().errorMessage()).doesNotContain("private SQL token");
    }

    @Test void planQueryFailureIsTaskFailureAndFailedTerminationRemainsRecoverable() {
        var task = service.submit(request()).task();
        doThrow(new TestFailure(ErrorCode.QUERY_FAILED)).when(repository).results(eq(task.checkId()), any(), anyInt(), anyInt());
        doThrow(new TestFailure(ErrorCode.PERSISTENCE_FAILED)).when(repository).terminate(eq(task.checkId()), any(), any(), any());
        runner.run(task.checkId(), () -> false);
        assertThat(repository.find(task.checkId()).orElseThrow().status()).isEqualTo(IntegrityTaskStatus.RUNNING);
        doCallRealMethod().when(repository).terminate(any(), any(), any(), any());
        assertThat(repository.interruptUnfinished(NOW)).isOne();
        assertThat(repository.progress(task.checkId()).orElseThrow().notRunUnits()).isEqualTo(7);
    }

    @Test void queryFailureTerminatesWithSafeQueryReason() {
        var task = service.submit(request()).task();
        doThrow(new TestFailure(ErrorCode.QUERY_FAILED)).when(repository).results(eq(task.checkId()), any(), anyInt(), anyInt());
        runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.FAILED);
        assertThat(progress.task().errorCode()).isEqualTo("QUERY_FAILED");
        assertThat(progress.notRunUnits()).isEqualTo(7);
    }

    @Test void exactScanBudgetAllowsTheLimitButTheNextGeneratedKeyStopsOnlyItsUnit() {
        jdbc.update("INSERT INTO local__daily(symbol,day) VALUES ('A','2026-01-01')");
        settings = limits(2, 20, 120, 1800); assemble(CLOCK);
        var request = request(); request.put("apiNames", List.of("daily"));
        var first = service.submit(request).task(); runner.run(first.checkId(), () -> false);
        assertThat(repository.progress(first.checkId()).orElseThrow().errorUnits()).isZero();
        settings = limits(1, 20, 120, 1800); assemble(CLOCK);
        request.put("submissionId", UUID.randomUUID().toString());
        var over = service.submit(request).task(); runner.run(over.checkId(), () -> false);
        var progress = repository.progress(over.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(progress.errorUnits()).isOne();
        var report = repository.results(over.checkId(), null, 1, 100).items().getFirst().report();
        assertThat(report.get("reasonCode").asText()).isEqualTo("SCAN_LIMIT_EXCEEDED");
        assertThat(report.get("statistics").get("coverageRate").isNull()).isTrue();
    }

    @Test void issueLimitPreservesKnownFailureAndIncompleteDetails() {
        plugin.expectedKeys = 3; settings = limits(100, 2, 120, 1800); assemble(CLOCK);
        var request = request(); request.put("apiNames", List.of("daily")); request.put("endDate", "2026-01-03");
        var task = service.submit(request).task(); runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(progress.errorUnits()).isEqualTo(2); assertThat(progress.overallStatus()).isEqualTo(IntegrityStatus.FAIL);
        assertThat(repository.issues(task.checkId(), null, 1, 100).items()).hasSize(4).allSatisfy(i ->
                assertThat(i.issue().get("incomplete").asBoolean()).isTrue());
        assertThat(repository.results(task.checkId(), null, 1, 100).items()).allSatisfy(r -> {
            assertThat(r.report().get("reasonCode").asText()).isEqualTo("ISSUE_LIMIT_EXCEEDED");
            assertThat(r.report().get("statistics").get("coverageRate").isNull()).isTrue();
        });
    }

    @Test void taskDeadlineInterruptsExactlyAtDeadlineAfterSavingKnownIssues() {
        var clock = new MutableClock(); settings = limits(100, 20, 120, 1); assemble(clock);
        plugin.afterCompare = () -> clock.now = NOW.plusSeconds(1);
        var task = service.submit(request()).task(); runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.INTERRUPTED);
        assertThat(progress.task().errorCode()).isEqualTo("TASK_TIME_BUDGET_EXHAUSTED");
        assertThat(progress.completedUnits()).isOne(); assertThat(progress.notRunUnits()).isEqualTo(6);
        assertThat(progress.overallStatus()).isEqualTo(IntegrityStatus.FAIL);
        assertThat(repository.issues(task.checkId(), null, 1, 100).items()).singleElement()
                .satisfies(i -> assertThat(i.issue().get("incomplete").asBoolean()).isTrue());
    }

    @Test void unitDeadlineDoesNotInterruptOtherUnitsAndQueueTimeIsNotCharged() {
        var clock = new MutableClock(); settings = limits(100, 20, 1, 1800); assemble(clock);
        var task = service.submit(request()).task(); clock.now = NOW.plusSeconds(5000);
        var once = new AtomicBoolean();
        plugin.afterCompare = () -> { if (!once.getAndSet(true)) clock.now = clock.now.plusSeconds(1); };
        runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(progress.task().startedAt()).isEqualTo(NOW.plusSeconds(5000).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(progress.errorUnits()).isOne(); assertThat(progress.completedUnits()).isEqualTo(7);
    }

    @Test void stoppingBeforeFirstUnitPreservesEntirePlanAsNotRun() {
        var task = service.submit(request()).task(); runner.run(task.checkId(), () -> true);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.INTERRUPTED);
        assertThat(progress.notRunUnits()).isEqualTo(7); assertThat(progress.completedUnits()).isZero();
        assertThat(progress.overallStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
        assertThat(repository.results(task.checkId(), null, 1, 100).items()).allSatisfy(r -> {
            assertThat(r.report().get("statistics").get("actualCount").isNull()).isTrue();
            assertThat(r.report().get("scope").get("snapshotStartedAt").isNull()).isTrue();
        });
    }

    @Test void targetAndReferenceUseOneSnapshotWhileTheNextUnitSeesNewRows() {
        plugin.references = true; plugin.scanReferences = true;
        var request = request(); request.put("apiNames", List.of("daily"));
        var task = service.submit(request).task(); runner.run(task.checkId(), () -> false);
        assertThat(repository.progress(task.checkId()).orElseThrow().errorUnits()).isZero();
        assertThat(plugin.referenceSizes).containsExactly(0, 1);
    }

    @Test void stablePaginationKeepsEveryPlannedUnitBeyondTheFirstPage() {
        var request = request(); request.put("symbols", java.util.stream.IntStream.range(0, 100).mapToObj(i -> "S" + i).toList());
        request.put("apiNames", List.of("missing", "non_stock"));
        var task = service.submit(request).task(); runner.run(task.checkId(), () -> false);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(progress.completedUnits()).isEqualTo(101);
        assertThat(progress.statusCounts()).containsEntry(IntegrityStatus.UNKNOWN, 100L)
                .containsEntry(IntegrityStatus.NOT_APPLICABLE, 1L);
    }

    @Test void cancellationDuringEvaluationCommitsKnownIssuesBeforeInterruptingRemainder() {
        var stop = new AtomicBoolean(); plugin.afterCompare = () -> stop.set(true);
        var task = service.submit(request()).task(); runner.run(task.checkId(), stop::get);
        var progress = repository.progress(task.checkId()).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.INTERRUPTED);
        assertThat(progress.task().errorCode()).isEqualTo("EXECUTION_INTERRUPTED");
        assertThat(progress.completedUnits()).isOne(); assertThat(progress.notRunUnits()).isEqualTo(6);
        assertThat(repository.issues(task.checkId(), null, 1, 100).items()).singleElement()
                .satisfies(i -> assertThat(i.issue().get("incomplete").asBoolean()).isTrue());
    }

    @Test void realWorkerShutdownWaitsForSnapshotAndInterruptsQueuedWork() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var gated = new CountDownLatch(1);
        plugin.afterCompare = () -> {
            entered.countDown();
            try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException e) { throw new AssertionError("Active snapshot must use cooperative cancellation", e); }
        };
        var admission = spy(service);
        try (var coordinator = new IntegrityCheckCoordinator(queue, runner, repository, admission, CLOCK);
                var executor = Executors.newSingleThreadExecutor()) {
            coordinator.start();
            var task = service.submit(request()).task();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var queued = service.submit(request()).task();
            doAnswer(call -> { call.callRealMethod(); gated.countDown(); return null; }).when(admission).stopAccepting();
            var closing = executor.submit(coordinator::close);
            try {
                assertThat(gated.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(closing.isDone()).isFalse();
            } finally { release.countDown(); }
            closing.get(5, TimeUnit.SECONDS);
            assertThat(repository.progress(task.checkId()).orElseThrow().task().status()).isEqualTo(IntegrityTaskStatus.INTERRUPTED);
            assertThat(repository.progress(task.checkId()).orElseThrow().completedUnits()).isOne();
            assertThat(repository.progress(queued.checkId()).orElseThrow().notRunUnits()).isEqualTo(7);
            assertThat(queue.poll(Duration.ZERO)).isEmpty();
            assertThat(coordinator.isRunning()).isFalse();
        } finally { release.countDown(); }
    }

    static IntegrityCheckService.Settings limits(long items, int issues, long unitSeconds, long taskSeconds) {
        return new IntegrityCheckService.Settings(100, 36600, 4000, 20, 1, 1, items, issues, unitSeconds, taskSeconds);
    }
    static final class MutableClock extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    Map<String,Object> request() {
        return new LinkedHashMap<>(Map.of("submissionId", UUID.randomUUID().toString(), "pluginId", "local",
                "capabilityHash", service.capability(PLUGIN).capabilityHash(), "symbols", List.of("A", "B"),
                "startDate", "2026-01-01", "endDate", "2026-01-02"));
    }
    static class CheckPlugin extends LocalPlugin {
        boolean failRule, scanReferences;
        int expectedKeys = 1;
        Runnable afterCompare = () -> {};
        final List<String> executed = new ArrayList<>();
        final List<Integer> referenceSizes = new ArrayList<>();
        @Override public Optional<IntegrityDescriptor> integrityDescriptor(ApiName api) {
            var base = super.integrityDescriptor(api);
            if (!failRule || !api.value().equals("daily")) return base;
            var d = base.orElseThrow(); var rules = new ArrayList<>(d.rules());
            rules.add(new IntegrityRuleDescriptor("local.failure", version, "Failing rule", IntegrityRuleDescriptor.Dimension.KEY,
                    List.of(), List.of(), "Synthetic failure isolation"));
            Collections.reverse(rules);
            return Optional.of(new IntegrityDescriptor(d.datasetKey(), d.scopeKind(), d.symbolField(), d.dateField(),
                    d.dateLabel(), d.marketZone(), d.capabilityVersion(), d.dependencies(), rules, d.limitations()));
        }
        @Override public List<IntegrityReadRequest> integrityReferenceReads(IntegrityScope scope) {
            return !scanReferences || !scope.datasetKey().apiName().value().equals("daily") ? List.of()
                    : List.of(new IntegrityReadRequest(new DatasetKey(PLUGIN, new ApiName("snapshot")),
                            List.of("symbol"), Map.of(), null, null, false, "stock"));
        }
        @Override public List<IntegrityRule> integrityRules(ApiName api) {
            return integrityDescriptor(api).stream().flatMap(d -> d.rules().stream()).map(r -> (IntegrityRule) new IntegrityRule() {
                public IntegrityRuleDescriptor descriptor() { return r; }
                public IntegrityRuleResult evaluate(IntegrityScope scope, IntegrityContext context, IntegrityIssueSink sink) {
                    executed.add(r.ruleId() + ":" + scope.symbol());
                    if (r.ruleId().equals("local.failure")) throw new IllegalStateException("secret upstream");
                    if (scanReferences && api.value().equals("daily")) {
                        if (scope.symbol().equals("A")) jdbc.update("INSERT INTO local__snapshot(symbol,day) VALUES ('A','2026-01-01'),('B','2026-01-01')");
                        var count = new AtomicInteger();
                        context.scan(integrityReferenceReads(scope).getFirst(), rows -> count.addAndGet(rows.size()));
                        referenceSizes.add(count.get());
                    }
                    var evidence = List.of(new IntegrityEvidence("test:synthetic", "1", scope.range(), scope.snapshotStartedAt(),
                            "Controlled fixture; not a production baseline"));
                    var keys = java.util.stream.IntStream.range(0, expectedKeys).<Map<String,Object>>mapToObj(i ->
                            Map.of("symbol", scope.symbol(), "day", scope.startDate().plusDays(i))).toList();
                    var stats = context.compare(new IntegrityExpectedKeys(scope, IntegrityExpectedKeys.Basis.PROVEN, keys, evidence));
                    afterCompare.run();
                    return new IntegrityRuleResult(r, IntegrityStatus.PASS, "FIXTURE", "Fixture comparison", stats, evidence);
                }
            }).toList();
        }
    }
}
