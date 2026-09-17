package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityStatus.*;
import com.akkc.tensor.core.integrity.IntegrityCheckRepository.*;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class IntegrityCheckRepositoryIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static final Instant NOW = Instant.parse("2026-09-16T01:02:03.123456789Z");
    static final LocalDate DAY = LocalDate.of(2026, 1, 1);
    static final PluginId PLUGIN = new PluginId("fixture");
    static final ApiName API = new ApiName("daily");
    static final DatasetKey KEY = new DatasetKey(PLUGIN, API);
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    final IntegrityCheckJson json = new IntegrityCheckJson();
    IntegrityCheckRepository repository;

    @BeforeAll static void schema() throws Exception {
        source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    "../tensor-app/src/main/resources/db/migration/V9__create_integrity_check_tables.sql"));
        }
        jdbc.execute("CREATE TABLE securities_sentinel (symbol VARCHAR(255) PRIMARY KEY, amount DECIMAL(30,18))");
        jdbc.update("INSERT INTO securities_sentinel VALUES ('UNCHANGED',1.000000000000000001)");
    }

    @BeforeEach void reset() {
        for (String trigger : List.of("reject_unit", "reject_issue", "reject_report"))
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger);
        jdbc.update("DELETE FROM tensor_integrity_check_issue");
        jdbc.update("DELETE FROM tensor_integrity_check_result");
        jdbc.update("DELETE FROM tensor_integrity_check_task");
        repository = new IntegrityCheckRepository(jdbc, manager, json);
    }

    IntegrityCheckJson.ApiSnapshot snapshot() {
        var definition = new DatasetDefinition(KEY, "Daily", "fixture", QueryMode.trade_date,
                List.of(), TableName.from(KEY), List.of(
                new ColumnDefinition("symbol", "Symbol", LogicalType.STRING, false, 0, 255, null, null, List.of(), false),
                new ColumnDefinition("day", "Day", LogicalType.DATE, false, 1, null, null, null, List.of(), false)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("symbol", "day")), List.of(), null);
        var rule = new IntegrityRuleDescriptor("fixture.coverage", "v1", "Original coverage",
                IntegrityRuleDescriptor.Dimension.COVERAGE, List.of("symbol", "day"), List.of(), "Original rule");
        var descriptor = new IntegrityDescriptor(KEY, IntegrityDescriptor.ScopeKind.STOCK_DATE, "symbol", "day",
                "Day", ZoneOffset.UTC, "v1", List.of(), List.of(rule), List.of());
        return new IntegrityCheckJson.ApiSnapshot(definition, descriptor, List.of(), IntegrityContracts.coreRules(definition));
    }

    NewTask task(String... symbols) {
        var request = Map.<String, Object>of("symbols", List.of(symbols), "start", DAY);
        var snapshots = List.of(snapshot());
        return new NewTask(UUID.randomUUID(), UUID.randomUUID(), PLUGIN, request,
                new TaskScope(PLUGIN, List.of(symbols), DAY, DAY.plusDays(3), List.of(API), NOW),
                json.requestHash(request), json.capabilityHash(PLUGIN, snapshots), snapshots, NOW);
    }

    NewUnit unit(String symbol) {
        return new NewUnit(UUID.randomUUID(), new IntegrityScope(KEY, symbol, DAY, DAY.plusDays(3), NOW, null), snapshot());
    }

    @Test void createsCompletePlanAndPreservesOriginalPrecision() {
        var task = task("A", "B");
        var units = List.of(unit("A"), unit("B"));
        repository.create(task, units);
        var saved = repository.find(task.checkId()).orElseThrow();
        assertThat(saved.plannedUnits()).isEqualTo(2);
        assertThat(saved.status()).isEqualTo(IntegrityTaskStatus.QUEUED);
        assertThat(saved.normalizedScope().get("acceptedAt").textValue()).isEqualTo(NOW.toString());
        assertThat(repository.findBySubmissionId(task.submissionId())).contains(saved);
        assertThat(repository.results(task.checkId(), null, 1, 20).items()).hasSize(2).allSatisfy(result -> {
            assertThat(result.report().get("unitStatus").textValue()).isEqualTo("PENDING");
            assertThat(result.report().get("statistics").get("actualCount").isNull()).isTrue();
        });
    }

    @Test void secondPlanInsertFailureRollsBackTaskAndFirstUnit() {
        jdbc.execute("CREATE TRIGGER reject_unit BEFORE INSERT ON tensor_integrity_check_result FOR EACH ROW "
                + "BEGIN IF NEW.symbol='B' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-value'; END IF; END");
        var task = task("A", "B");
        assertThatThrownBy(() -> repository.create(task, List.of(unit("A"), unit("B"))))
                .isInstanceOf(TensorException.class).hasMessage(ErrorCode.PERSISTENCE_FAILED.message());
        assertThat(repository.find(task.checkId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_integrity_check_result", Long.class)).isZero();
    }

    IntegrityUnitResult completed(NewUnit unit, IntegrityStatus status, IntegrityStatistics statistics) {
        var scope = unit.scope();
        return new IntegrityUnitResult(new IntegrityScope(scope.datasetKey(), scope.symbol(), scope.startDate(), scope.endDate(),
                scope.acceptedAt(), NOW.plusNanos(1)), unit.snapshot().descriptor(), json.definitionHash(unit.snapshot().definition()),
                scope.range(), IntegrityUnitStatus.COMPLETED, status, PASS, PASS, statistics, List.of(), List.of(),
                NOW.plusSeconds(1), false, true, "SAVED", "Original message");
    }
    IntegrityStatistics unknownCounts() { return new IntegrityStatistics(null,null,null,null,null,null,null); }
    NewIssue issue(NewUnit unit, LocalDate date) {
        var key = new LinkedHashMap<String,Object>();
        key.put("symbol",unit.scope().symbol()); key.put("event",9007199254740993L);
        key.put("amount",new BigDecimal("1.000000000000000001")); key.put("unknown",null);
        return new NewIssue("fixture.coverage", "v1", new IntegrityIssue(IntegrityIssue.Type.MISSING, FAIL,
                unit.scope().symbol(), API, "day", date, key, null, Map.of("reported",DAY.plusDays(1)), "MISSING", "Original issue",
                List.of(new IntegrityEvidence("original source", "v1", unit.scope().range(), NOW, "Safe evidence")), false));
    }
    void save(NewTask task, NewUnit unit, List<NewIssue> issues) {
        repository.saveResult(task.checkId(), unit.resultId(), completed(unit, FAIL,
                new IntegrityStatistics(1L,2L,1L,1L,0L,0L,0L)), issues);
    }
    void assertPending(NewTask task, NewUnit unit) {
        assertThat(repository.result(task.checkId(), unit.resultId()).orElseThrow().report().get("unitStatus").asText()).isEqualTo("PENDING");
        assertThat(repository.issues(task.checkId(), null, 1, 20).total()).isZero();
    }

    IntegrityUnitResult terminal(NewUnit unit, IntegrityUnitStatus state, IntegrityStatus status) {
        var scope = unit.scope();
        return new IntegrityUnitResult(new IntegrityScope(scope.datasetKey(), scope.symbol(), scope.startDate(), scope.endDate(),
                scope.acceptedAt(), state == IntegrityUnitStatus.COMPLETED ? NOW.plusNanos(1) : null),
                unit.snapshot().descriptor(), json.definitionHash(unit.snapshot().definition()),
                state == IntegrityUnitStatus.COMPLETED ? scope.range() : null, state,
                status, status, status, unknownCounts(), List.of(), List.of(), NOW.plusSeconds(1),
                state != IntegrityUnitStatus.COMPLETED, state == IntegrityUnitStatus.COMPLETED, "TEST", "Safe test result");
    }

    @Test void lifecycleAndProgressUseOnlyCommittedUnitStates() {
        var task = task("A", "B", "C"); var a = unit("A"); var b = unit("B"); var c = unit("C");
        repository.create(task,List.of(a,b,c));
        var queued = repository.progress(task.checkId()).orElseThrow();
        assertThat(queued.completedUnits()).isZero();
        assertThat(queued.statusCounts()).containsEntry(UNKNOWN,3L).containsEntry(PASS,0L)
                .containsEntry(FAIL,0L).containsEntry(WARN,0L).containsEntry(NOT_APPLICABLE,0L);
        assertThat(queued.overallStatus()).isEqualTo(UNKNOWN);
        assertThatThrownBy(() -> queued.statusCounts().put(PASS,1L)).isInstanceOf(UnsupportedOperationException.class);

        assertThat(repository.start(task.checkId(),NOW.plusSeconds(1))).isTrue();
        assertThat(repository.start(task.checkId(),NOW.plusSeconds(2))).isFalse();
        repository.saveResult(task.checkId(),a.resultId(),terminal(a,IntegrityUnitStatus.COMPLETED,PASS),List.of());
        repository.saveResult(task.checkId(),b.resultId(),terminal(b,IntegrityUnitStatus.ERROR,UNKNOWN),List.of());
        var partial = repository.progress(task.checkId()).orElseThrow();
        assertThat(partial.completedUnits()).isEqualTo(2);
        assertThat(partial.errorUnits()).isEqualTo(1);
        assertThat(partial.notRunUnits()).isZero();
        assertThat(partial.statusCounts()).containsEntry(PASS,1L).containsEntry(UNKNOWN,2L);
        assertThatThrownBy(() -> repository.complete(task.checkId(),NOW.plusSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class);

        repository.saveResult(task.checkId(),c.resultId(),terminal(c,IntegrityUnitStatus.COMPLETED,NOT_APPLICABLE),List.of());
        repository.complete(task.checkId(),NOW.plusSeconds(3));
        var complete = repository.progress(task.checkId()).orElseThrow();
        assertThat(complete.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(complete.completedUnits()).isEqualTo(3);
        assertThat(complete.statusCounts()).containsEntry(PASS,1L).containsEntry(UNKNOWN,1L)
                .containsEntry(NOT_APPLICABLE,1L);
        assertThat(complete.overallStatus()).isEqualTo(UNKNOWN);
        assertThat(repository.start(task.checkId(),NOW.plusSeconds(4))).isFalse();
        assertThatThrownBy(() -> repository.complete(task.checkId(),NOW.plusSeconds(4)))
                .isInstanceOf(IllegalArgumentException.class);
        var report = repository.result(task.checkId(),c.resultId()).orElseThrow().report();
        repository.terminate(task.checkId(),IntegrityTaskStatus.FAILED,"INTERNAL_ERROR",NOW.plusSeconds(4));
        assertThat(repository.find(task.checkId()).orElseThrow().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(repository.result(task.checkId(),c.resultId()).orElseThrow().report()).isEqualTo(report);
        jdbc.update("UPDATE tensor_integrity_check_result SET unit_status='PENDING',"
                + "report=JSON_SET(report,'$.payload.unitStatus','PENDING') WHERE result_id=?",c.resultId().toString());
        assertThatThrownBy(() -> repository.saveResult(task.checkId(),c.resultId(),terminal(c,IntegrityUnitStatus.COMPLETED,PASS),List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void completeRejectsAPlanWithMissingResultRows() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        repository.start(task.checkId(),NOW.plusSeconds(1));
        jdbc.update("DELETE FROM tensor_integrity_check_result WHERE result_id=?",unit.resultId().toString());
        assertThatThrownBy(() -> repository.complete(task.checkId(),NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.find(task.checkId()).orElseThrow().status()).isEqualTo(IntegrityTaskStatus.RUNNING);
    }

    @Test void terminateAtomicallyKeepsCommittedResultsAndPreservesRunningSnapshot() {
        var task = task("A", "B", "C"); var a = unit("A"); var b = unit("B"); var c = unit("C");
        repository.create(task,List.of(a,b,c));
        repository.start(task.checkId(),NOW.plusSeconds(1));
        repository.saveResult(task.checkId(),a.resultId(),terminal(a,IntegrityUnitStatus.COMPLETED,PASS),List.of());
        var snapshot = NOW.plusSeconds(2);
        jdbc.update("UPDATE tensor_integrity_check_result SET unit_status='RUNNING',snapshot_started_at=?,"
                        + "report=JSON_SET(report,'$.payload.unitStatus','RUNNING','$.payload.scope.snapshotStartedAt',?) WHERE result_id=?",
                LocalDateTime.ofInstant(snapshot.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),ZoneOffset.UTC),
                snapshot.toString(),b.resultId().toString());

        repository.terminate(task.checkId(),IntegrityTaskStatus.INTERRUPTED,"EXECUTION_INTERRUPTED",NOW.plusSeconds(3));
        var terminated = repository.find(task.checkId()).orElseThrow();
        assertThat(terminated.status()).isEqualTo(IntegrityTaskStatus.INTERRUPTED);
        assertThat(terminated.errorCode()).isEqualTo("EXECUTION_INTERRUPTED");
        assertThat(terminated.errorMessage()).doesNotContain("secret").isNotBlank();
        assertThat(repository.result(task.checkId(),a.resultId()).orElseThrow().report().path("unitStatus").asText())
                .isEqualTo("COMPLETED");
        for (var unit : List.of(b,c)) {
            var report = repository.result(task.checkId(),unit.resultId()).orElseThrow().report();
            assertThat(report.path("unitStatus").asText()).isEqualTo("NOT_RUN");
            assertThat(report.path("reasonCode").asText()).isEqualTo("EXECUTION_INTERRUPTED");
            assertThat(report.path("publishedRange").isNull()).isTrue();
            assertThat(report.path("statistics").path("actualCount").isNull()).isTrue();
            assertThat(report.path("ruleResults")).isEmpty();
            assertThat(report.path("evidence")).isEmpty();
        }
        assertThat(repository.result(task.checkId(),b.resultId()).orElseThrow().report()
                .path("scope").path("snapshotStartedAt").asText()).isEqualTo(snapshot.toString());
        var before = repository.result(task.checkId(),c.resultId()).orElseThrow().report();
        repository.terminate(task.checkId(),IntegrityTaskStatus.INTERRUPTED,"EXECUTION_INTERRUPTED",NOW.plusSeconds(9));
        assertThat(repository.result(task.checkId(),c.resultId()).orElseThrow().report()).isEqualTo(before);
        assertThat(repository.find(task.checkId()).orElseThrow().finishedAt()).isEqualTo(NOW.plusSeconds(3).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test void terminateRejectsMismatchedStoredDefinitionHashWithoutSilentlyRepairingHistory() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        repository.start(task.checkId(),NOW.plusSeconds(1));
        var corruptHash = "f".repeat(64);
        jdbc.update("UPDATE tensor_integrity_check_result SET report=JSON_SET(report,'$.payload.definitionHash',?)"
                + " WHERE result_id=?",corruptHash,unit.resultId().toString());

        assertThatThrownBy(() -> repository.terminate(task.checkId(),IntegrityTaskStatus.INTERRUPTED,
                "EXECUTION_INTERRUPTED",NOW.plusSeconds(2))).isInstanceOfSatisfying(TensorException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.QUERY_FAILED))
                .hasMessage(ErrorCode.QUERY_FAILED.message()).hasNoCause();

        assertThat(repository.find(task.checkId()).orElseThrow().status()).isEqualTo(IntegrityTaskStatus.RUNNING);
        assertThat(jdbc.queryForObject("SELECT unit_status FROM tensor_integrity_check_result WHERE result_id=?",
                String.class,unit.resultId().toString())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(report,'$.payload.definitionHash'))"
                + " FROM tensor_integrity_check_result WHERE result_id=?",String.class,unit.resultId().toString()))
                .isEqualTo(corruptHash);
    }

    @Test void terminationFailureRollsBackReportsAndTaskAndRecoveryInterruptsEveryUnfinishedTask() {
        var failingTask = task("A"); var failingUnit = unit("A"); repository.create(failingTask,List.of(failingUnit));
        repository.start(failingTask.checkId(),NOW.plusSeconds(1));
        jdbc.execute("CREATE TRIGGER reject_report BEFORE UPDATE ON tensor_integrity_check_result FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-value'");
        assertThatThrownBy(() -> repository.terminate(failingTask.checkId(),IntegrityTaskStatus.FAILED,"PERSISTENCE_FAILED",NOW.plusSeconds(2)))
                .isInstanceOf(TensorException.class).hasMessage(ErrorCode.PERSISTENCE_FAILED.message()).hasNoCause();
        jdbc.execute("DROP TRIGGER reject_report");
        assertThat(repository.find(failingTask.checkId()).orElseThrow().status()).isEqualTo(IntegrityTaskStatus.RUNNING);
        assertPending(failingTask,failingUnit);

        var queued = task("B"); var queuedUnit = unit("B"); repository.create(queued,List.of(queuedUnit));
        assertThat(repository.interruptUnfinished(NOW.plusSeconds(4))).isEqualTo(2);
        for (var id : List.of(failingTask.checkId(),queued.checkId())) {
            var recovered = repository.progress(id).orElseThrow();
            assertThat(recovered.task().status()).isEqualTo(IntegrityTaskStatus.INTERRUPTED);
            assertThat(recovered.task().errorCode()).isEqualTo("EXECUTION_INTERRUPTED");
            assertThat(recovered.completedUnits()).isZero();
            assertThat(recovered.notRunUnits()).isEqualTo(1);
            assertThat(recovered.statusCounts()).containsEntry(UNKNOWN,1L);
        }
        assertThat(repository.interruptUnfinished(NOW.plusSeconds(5))).isZero();
    }

    @Test void lifecycleRejectsInvalidTransitionsReasonsTimesAndOuterTransactions() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        assertThat(repository.start(UUID.randomUUID(),NOW)).isFalse();
        assertThatThrownBy(() -> repository.start(task.checkId(),NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.complete(task.checkId(),NOW.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        for (var invalid : List.of(
                new Object[]{IntegrityTaskStatus.COMPLETED,"INTERNAL_ERROR"},
                new Object[]{IntegrityTaskStatus.INTERRUPTED,"INTERNAL_ERROR"},
                new Object[]{IntegrityTaskStatus.FAILED,"EXECUTION_INTERRUPTED"},
                new Object[]{IntegrityTaskStatus.FAILED,"private-value"}))
            assertThatThrownBy(() -> repository.terminate(task.checkId(),(IntegrityTaskStatus)invalid[0],(String)invalid[1],NOW.plusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("private-value");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            List<Runnable> calls = List.of(() -> repository.start(task.checkId(),NOW.plusSeconds(1)),
                    () -> repository.complete(task.checkId(),NOW.plusSeconds(1)),
                    () -> repository.terminate(task.checkId(),IntegrityTaskStatus.FAILED,"INTERNAL_ERROR",NOW.plusSeconds(1)),
                    () -> repository.interruptUnfinished(NOW.plusSeconds(1)), () -> repository.progress(task.checkId()));
            calls.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(IllegalStateException.class));
        });
    }
    @Test void submissionAndUnitIdentityAreUniqueAndConcurrentSubmissionHasOneCompleteWinner() throws Exception {
        var task = task("A"); var unit = unit("A");
        repository.create(task,List.of(unit));
        assertThatThrownBy(() -> repository.create(task,List.of(unit))).isInstanceOf(DuplicateKeyException.class);
        var duplicatePlan = task("A");
        assertThatThrownBy(() -> repository.create(duplicatePlan,List.of(unit("A"),unit("A"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.find(duplicatePlan.checkId())).isEmpty();
        var candidate = task("A","B");
        var competing = new NewTask(UUID.randomUUID(),candidate.submissionId(),candidate.pluginId(),candidate.originalRequest(),
                candidate.scope(),candidate.requestHash(),candidate.capabilityHash(),candidate.definitionSnapshot(),candidate.createdAt());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(candidate,competing).stream().map(t -> pool.submit(() -> {
                start.await();
                try { repository.create(t,List.of(unit("A"),unit("B"))); return "saved"; }
                catch (DuplicateKeyException e) { return "duplicate"; }
            })).toList();
            start.countDown();
            assertThat(List.of(attempts.get(0).get(20,TimeUnit.SECONDS),attempts.get(1).get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved","duplicate");
        }
        var winner = repository.findBySubmissionId(candidate.submissionId()).orElseThrow();
        assertThat(repository.results(winner.checkId(),null,1,20).total()).isEqualTo(2);
    }

    @Test void issuesAndReportRollBackOnBothInsertAndUpdateFailuresAndCannotBeOverwritten() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        jdbc.execute("CREATE TRIGGER reject_issue BEFORE INSERT ON tensor_integrity_check_issue FOR EACH ROW "
                + "BEGIN IF NEW.issue_date='2026-01-02' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-value'; END IF; END");
        var issues = List.of(issue(unit,DAY),issue(unit,DAY.plusDays(1)));
        assertThatThrownBy(() -> save(task,unit,issues)).isInstanceOf(TensorException.class)
                .hasMessage(ErrorCode.PERSISTENCE_FAILED.message()).hasNoCause();
        assertPending(task,unit);
        jdbc.execute("DROP TRIGGER reject_issue");
        jdbc.execute("CREATE TRIGGER reject_report BEFORE UPDATE ON tensor_integrity_check_result FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-value'");
        assertThatThrownBy(() -> save(task,unit,issues)).isInstanceOf(TensorException.class);
        assertPending(task,unit);
        jdbc.execute("DROP TRIGGER reject_report");
        save(task,unit,issues);
        assertThatThrownBy(() -> save(task,unit,issues)).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.issues(task.checkId(),null,1,20).total()).isEqualTo(2);
        assertThatThrownBy(() -> repository.saveResult(UUID.randomUUID(),unit.resultId(),completed(unit,UNKNOWN,unknownCounts()),List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.result(UUID.randomUUID(),unit.resultId())).isEmpty();
    }

    @Test void anotherConnectionSeesNeitherPartialIssuesNorFinalReportUntilCommit() throws Exception {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        var inserted = new CountDownLatch(1); var release = new CountDownLatch(1); var once = new AtomicBoolean();
        var intercepted = new JdbcTemplate(source) {
            @Override public int[] batchUpdate(String sql, List<Object[]> args) {
                int[] count = super.batchUpdate(sql,args);
                if (sql.startsWith("INSERT INTO tensor_integrity_check_issue") && once.compareAndSet(false,true)) {
                    inserted.countDown(); await(release);
                }
                return count;
            }
        };
        var writer = new IntegrityCheckRepository(intercepted,manager,json);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> writer.saveResult(task.checkId(),unit.resultId(),completed(unit,FAIL,unknownCounts()),
                    List.of(issue(unit,DAY),issue(unit,DAY.plusDays(1)))));
            try {
                assertThat(inserted.await(10,TimeUnit.SECONDS)).isTrue();
                assertPending(task,unit);
            } finally { release.countDown(); }
            future.get(20,TimeUnit.SECONDS);
        }
        assertThat(repository.issues(task.checkId(),null,1,20).total()).isEqualTo(2);
        assertThat(repository.result(task.checkId(),unit.resultId()).orElseThrow().report().get("unitStatus").asText()).isEqualTo("COMPLETED");
    }

    @Test void exactValuesAndSnapshotsAreHistoricalAndDefensivelyCopied() {
        var before = jdbc.queryForList("SELECT * FROM securities_sentinel");
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        var result = completed(unit,FAIL,new IntegrityStatistics(9007199254740993L,null,null,1L,null,null,null));
        repository.saveResult(task.checkId(),unit.resultId(),result,List.of(issue(unit,DAY)));
        var saved = repository.result(task.checkId(),unit.resultId()).orElseThrow();
        assertThat(saved.report()).isEqualTo(json.readDocument(json.writeDocument(result)));
        assertThat(saved.report().path("statistics").path("actualCount").asText()).isEqualTo("9007199254740993");
        assertThat(saved.report().path("scope").path("snapshotStartedAt").asText()).isEqualTo(NOW.plusNanos(1).toString());
        var storedIssue = repository.issues(task.checkId(),null,1,20).items().getFirst();
        assertThat(storedIssue.issue()).isEqualTo(json.readValue(json.write(issue(unit,DAY).issue())));
        assertThat(storedIssue.issue().path("businessKey").path("amount").asText()).isEqualTo("1.000000000000000001");
        ((ObjectNode)saved.report()).put("overallStatus","PASS");
        ((ObjectNode)saved.definitionSnapshot()).removeAll();
        ((ObjectNode)storedIssue.issue()).removeAll();
        var taskRecord = repository.find(task.checkId()).orElseThrow();
        ((ObjectNode)taskRecord.originalRequest()).removeAll();
        ((ObjectNode)taskRecord.normalizedScope()).removeAll();
        ((com.fasterxml.jackson.databind.node.ArrayNode)taskRecord.definitionSnapshot()).removeAll();
        assertThat(repository.result(task.checkId(),unit.resultId())).contains(saved);
        assertThat(repository.find(task.checkId())).contains(taskRecord);
        assertThat(repository.issues(task.checkId(),null,1,20).items()).containsExactly(storedIssue);
        // A new capability/definition is never supplied when reading the accepted historical report.
        assertThat(saved.definitionSnapshot().path("descriptor").path("rules").get(0).path("displayName").asText())
                .isEqualTo("Original coverage");
        assertThat(jdbc.queryForList("SELECT * FROM securities_sentinel")).isEqualTo(before);
    }

    @Test void rejectsChangedScopeDefinitionDescriptorAndRuleIdentity() {
        var task = task("A","B"); var unit = unit("A"); repository.create(task,List.of(unit,unit("B")));
        var foreign = unit("B");
        assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),completed(foreign,UNKNOWN,unknownCounts()),List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        var good = completed(unit,UNKNOWN,unknownCounts());
        var wrongHash = new IntegrityUnitResult(good.scope(),good.descriptor(),"f".repeat(64),good.publishedRange(),good.unitStatus(),
                good.coverageStatus(),good.keyStatus(),good.fieldStatus(),good.statistics(),List.of(),List.of(),good.finishedAt(),false,true,"X","X");
        assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),wrongHash,List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        var item = issue(unit,DAY);
        for (var changed : List.of(new NewIssue("foreign.rule","v1",item.issue()),new NewIssue(item.ruleId(),"v2",item.issue())))
            assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),good,List.of(changed)))
                    .isInstanceOf(IllegalArgumentException.class);
        var originalRule = unit.snapshot().descriptor().rules().getFirst();
        var changedRule = new IntegrityRuleDescriptor(originalRule.ruleId(),originalRule.version(),"New current name",originalRule.dimension(),
                originalRule.requiredColumns(),originalRule.dependencies(),originalRule.description());
        var changed = new IntegrityUnitResult(good.scope(),good.descriptor(),good.definitionHash(),good.publishedRange(),good.unitStatus(),
                good.coverageStatus(),good.keyStatus(),good.fieldStatus(),good.statistics(),
                List.of(new IntegrityRuleResult(changedRule,UNKNOWN,"X","X",unknownCounts(),List.of())),List.of(),good.finishedAt(),false,true,"X","X");
        assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),changed,List.of())).isInstanceOf(IllegalArgumentException.class);
        assertPending(task,unit);
    }

    @Test void persistsUnknownNotApplicableNotRunAndIncompleteErrorWithoutInventingCoverage() {
        var task = task("A","B","C","D"); var units = List.of(unit("A"),unit("B"),unit("C"),unit("D"));
        repository.create(task,units);
        repository.saveResult(task.checkId(),units.get(0).resultId(),completed(units.get(0),UNKNOWN,unknownCounts()),List.of());
        for (int index : List.of(1,2)) {
            var u = units.get(index); var state = index == 1 ? IntegrityUnitStatus.COMPLETED : IntegrityUnitStatus.NOT_RUN;
            var result = new IntegrityUnitResult(u.scope(),u.snapshot().descriptor(),json.definitionHash(u.snapshot().definition()),null,state,
                    NOT_APPLICABLE,NOT_APPLICABLE,NOT_APPLICABLE,unknownCounts(),List.of(),List.of(),NOW,false,true,"SKIPPED","No execution");
            repository.saveResult(task.checkId(),u.resultId(),result,List.of());
            assertThat(repository.result(task.checkId(),u.resultId()).orElseThrow().report().path("overallStatus").asText())
                    .isEqualTo(index==1 ? "NOT_APPLICABLE" : "UNKNOWN");
        }
        var u = units.get(3); var base = issue(u,DAY); var i = base.issue();
        var incomplete = new NewIssue(base.ruleId(),base.ruleVersion(),new IntegrityIssue(i.type(),i.status(),i.symbol(),i.apiName(),i.dateField(),
                i.date(),i.businessKey(),i.field(),i.relatedDates(),i.reasonCode(),i.message(),i.evidence(),true));
        var error = new IntegrityUnitResult(u.scope(),u.snapshot().descriptor(),json.definitionHash(u.snapshot().definition()),null,
                IntegrityUnitStatus.ERROR,FAIL,UNKNOWN,UNKNOWN,new IntegrityStatistics(null,null,null,1L,null,null,null),List.of(),List.of(),NOW,
                true,false,"ERROR","Stopped");
        assertThatThrownBy(() -> repository.saveResult(task.checkId(),u.resultId(),error,List.of(base))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.saveResult(task.checkId(),u.resultId(),error,List.of())).isInstanceOf(IllegalArgumentException.class);
        repository.saveResult(task.checkId(),u.resultId(),error,List.of(incomplete));
        var stored = repository.result(task.checkId(),u.resultId()).orElseThrow().report();
        assertThat(stored.path("overallStatus").asText()).isEqualTo("FAIL");
        assertThat(stored.path("statistics").path("coverageRate").isNull()).isTrue();
    }

    @Test void allListsFilterAndPageWithStableOrderingAndUnknownDatesLast() {
        String a = "A".repeat(63)+"A", b = "A".repeat(63)+"B";
        var task = task(a,b); var ua = unit(a); var ub = unit(b); repository.create(task,List.of(ub,ua));
        var second = task("C"); var uc = unit("C"); repository.create(second,List.of(uc));
        save(task,ua,List.of(issue(ua,null),issue(ua,DAY),issue(ua,DAY)));
        save(task,ub,List.of(issue(ub,DAY.plusDays(1))));
        save(second,uc,List.of(issue(uc,DAY)));
        assertThat(repository.tasks(new TaskFilter(PLUGIN,IntegrityTaskStatus.QUEUED,task.submissionId()),1,20).items())
                .extracting(TaskRecord::checkId).containsExactly(task.checkId());
        assertThat(repository.tasks(new TaskFilter(new PluginId("absent"),null,null),1,20).total()).isZero();
        assertThat(repository.tasks(new TaskFilter(null,IntegrityTaskStatus.COMPLETED,null),1,20).total()).isZero();
        var taskIds = repository.tasks(null,1,20).items().stream().map(TaskRecord::checkId).toList();
        assertThat(repository.tasks(null,1,1).items().getFirst().checkId()).isEqualTo(taskIds.get(0));
        assertThat(repository.tasks(null,2,1).items().getFirst().checkId()).isEqualTo(taskIds.get(1));
        assertThat(repository.results(task.checkId(),null,1,1).items()).extracting(ResultRecord::symbol).containsExactly(a);
        assertThat(repository.results(task.checkId(),null,2,1).items()).extracting(ResultRecord::symbol).containsExactly(b);
        assertThat(repository.results(task.checkId(),new ResultFilter(b,API,FAIL),1,100).items()).extracting(ResultRecord::resultId).containsExactly(ub.resultId());
        assertThat(repository.results(task.checkId(),new ResultFilter(null,API,PASS),1,20).total()).isZero();
        assertThat(repository.results(task.checkId(),new ResultFilter(null,new ApiName("absent"),null),1,20).total()).isZero();
        var all = repository.issues(task.checkId(),null,1,20);
        assertThat(all.total()).isEqualTo(4);
        assertThat(all.items()).extracting(i -> i.issue().path("date").isNull() ? null : i.issue().path("date").asText())
                .containsExactly(DAY.toString(),DAY.toString(),DAY.plusDays(1).toString(),null);
        assertThat(repository.issues(task.checkId(),null,1,1).items()).containsExactly(all.items().get(0));
        assertThat(repository.issues(task.checkId(),null,2,1).items()).containsExactly(all.items().get(1));
        assertThat(repository.issues(task.checkId(),new IssueFilter(ua.resultId(),a,API,IntegrityIssue.Type.MISSING,FAIL,DAY,DAY),1,20).total()).isEqualTo(2);
        assertThat(repository.issues(task.checkId(),new IssueFilter(null,null,null,null,null,DAY,null),1,20).total()).isEqualTo(3);
        assertThat(repository.issues(task.checkId(),new IssueFilter(null,null,null,null,null,null,DAY),1,20).total()).isEqualTo(2);
        assertThat(repository.issues(task.checkId(),new IssueFilter(uc.resultId(),null,null,null,null,null,null),1,20).total()).isZero();
        assertThat(repository.issues(task.checkId(),new IssueFilter(null,null,null,IntegrityIssue.Type.EXTRA,null,null,null),1,20).total()).isZero();
        assertThat(repository.issues(task.checkId(),new IssueFilter(null,null,null,null,PASS,null,null),1,20).total()).isZero();
        assertThat(repository.issues(task.checkId(),new IssueFilter(null,b,null,null,null,null,null),1,20).total()).isEqualTo(1);
        assertThat(repository.issues(task.checkId(),new IssueFilter(null,null,new ApiName("absent"),null,null,null,null),1,20).total()).isZero();
        assertThat(repository.issues(task.checkId(),null,1,100).total()).isEqualTo(4);
        var beyondTasks = repository.tasks(null,Integer.MAX_VALUE,100);
        assertThat(beyondTasks.total()).isEqualTo(2); assertThat(beyondTasks.items()).isEmpty();
        var beyondResults = repository.results(task.checkId(),null,Integer.MAX_VALUE,100);
        assertThat(beyondResults.total()).isEqualTo(2); assertThat(beyondResults.items()).isEmpty();
        var beyondIssues = repository.issues(task.checkId(),null,Integer.MAX_VALUE,100);
        assertThat(beyondIssues.total()).isEqualTo(4); assertThat(beyondIssues.items()).isEmpty();
    }

    @Test void eachCountAndPageUsesSameRealRepeatableReadSnapshotAcrossConcurrentCommit() throws Exception {
        var first = task("A"); var a = unit("A"); var b = unit("B");
        // Fixed plan can contain a second pending unit whose status later changes the filtered page.
        first = task("A","B"); repository.create(first,List.of(a,b)); save(first,a,List.of(issue(a,DAY)));
        var check = first;
        var extraTask = task("C");
        var taskReader = readAfterCount(() -> repository.create(extraTask,List.of(unit("C"))));
        assertThat(taskReader.tasks(null,1,20).items()).hasSize(1);
        assertThat(repository.tasks(null,1,20).total()).isEqualTo(2);
        var resultReader = readAfterCount(() -> repository.saveResult(check.checkId(),b.resultId(),completed(b,FAIL,unknownCounts()),List.of(issue(b,DAY))));
        var results = resultReader.results(check.checkId(),new ResultFilter(null,null,FAIL),1,20);
        assertThat(results.total()).isEqualTo(1); assertThat(results.items()).hasSize(1);
        assertThat(repository.results(check.checkId(),new ResultFilter(null,null,FAIL),1,20).total()).isEqualTo(2);
        // Separate accepted plan permits an actual concurrent report+issue commit between issue COUNT and SELECT.
        var c = unit("C"); var third = task("C"); repository.create(third,List.of(c));
        var issueReader = readAfterCount(() -> save(third,c,List.of(issue(c,DAY))));
        var issues = issueReader.issues(third.checkId(),null,1,20);
        assertThat(issues.total()).isZero(); assertThat(issues.items()).isEmpty();
        assertThat(repository.issues(third.checkId(),null,1,20).total()).isEqualTo(1);
    }
    IntegrityCheckRepository readAfterCount(Runnable mutation) {
        var once = new AtomicBoolean();
        var connection = new JdbcTemplate(source) {
            @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                T result = super.queryForObject(sql,type,args);
                if (sql.startsWith("SELECT COUNT(*)") && once.compareAndSet(false,true)) {
                    try (var pool = Executors.newSingleThreadExecutor()) { pool.submit(mutation).get(15,TimeUnit.SECONDS); }
                    catch (Exception e) { throw new AssertionError(e); }
                }
                return result;
            }
        };
        return new IntegrityCheckRepository(connection,manager,json);
    }
    static void await(CountDownLatch latch) {
        try { if (!latch.await(15,TimeUnit.SECONDS)) throw new AssertionError("Latch timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    @Test void badInputsAndEveryPublicEntryRejectOutsideTransactionsBeforeSql() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        for (int size : List.of(0,101)) {
            assertThatThrownBy(() -> repository.tasks(null,1,size)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repository.results(task.checkId(),null,1,size)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repository.issues(task.checkId(),null,1,size)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> repository.tasks(null,0,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.find(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findBySubmissionId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.result(task.checkId(),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.create(null,List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.issues(task.checkId(),new IssueFilter(null,null,null,null,null,DAY.plusDays(1),DAY),1,20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.create(task("X".repeat(256)),List.of(unit("X".repeat(256)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid integrity check input");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            List<Runnable> calls = List.of(() -> repository.create(task,List.of(unit)), () -> repository.find(task.checkId()),
                    () -> repository.findBySubmissionId(task.submissionId()), () -> repository.result(task.checkId(),unit.resultId()),
                    () -> save(task,unit,List.of()), () -> repository.tasks(null,1,20),
                    () -> repository.results(task.checkId(),null,1,20), () -> repository.issues(task.checkId(),null,1,20));
            calls.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(IllegalStateException.class));
        });
    }

    @Test void databaseConstraintsRejectInvalidDirectWrites() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        for (String update : List.of("status='INVALID'", "planned_units=-1", "original_request=JSON_ARRAY()",
                "normalized_scope=JSON_ARRAY()", "definition_snapshot=JSON_ARRAY()", "error_code='X'"))
            assertThatThrownBy(() -> jdbc.update("UPDATE tensor_integrity_check_task SET "+update)).isInstanceOf(DataAccessException.class);
        for (String update : List.of("unit_status='INVALID'", "coverage_status='INVALID'", "key_status='INVALID'", "field_status='INVALID'",
                "overall_status='INVALID'", "start_date='2027-01-01'", "definition_snapshot=JSON_ARRAY()", "report=JSON_ARRAY()",
                "actual_count=-1", "expected_count=-1", "matched_count=-1", "missing_count=-1", "suspected_missing_count=-1",
                "extra_count=-1", "required_field_issue_count=-1", "incomplete=2", "issues_complete=2", "incomplete=0",
                "expected_count=1", "matched_count=1", "check_id='00000000-0000-0000-0000-000000000000'"))
            assertThatThrownBy(() -> jdbc.update("UPDATE tensor_integrity_check_result SET "+update)).isInstanceOf(DataAccessException.class);
        save(task,unit,List.of(issue(unit,DAY)));
        for (String update : List.of("matched_count=3", "missing_count=2", "extra_count=1", "unit_status='ERROR'"))
            assertThatThrownBy(() -> jdbc.update("UPDATE tensor_integrity_check_result SET "+update)).isInstanceOf(DataAccessException.class);
        for (String update : List.of("type='INVALID'", "status='INVALID'", "date_field=NULL", "business_key=JSON_ARRAY()",
                "related_dates=JSON_ARRAY()", "evidence=JSON_OBJECT()", "incomplete=2", "result_id='00000000-0000-0000-0000-000000000000'"))
            assertThatThrownBy(() -> jdbc.update("UPDATE tensor_integrity_check_issue SET "+update)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tensor_integrity_check_task")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tensor_integrity_check_result")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tensor_integrity_check_task SET check_id=?",UUID.randomUUID().toString())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tensor_integrity_check_result SET result_id=?",UUID.randomUUID().toString())).isInstanceOf(DataAccessException.class);
    }

    @Test void corruptedStoredDocumentIsSafeQueryFailureAndNeverAnEmptyReport() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        jdbc.update("UPDATE tensor_integrity_check_result SET report=JSON_OBJECT('schemaVersion',2,'payload',JSON_OBJECT())");
        assertThatThrownBy(() -> repository.result(task.checkId(),unit.resultId())).isInstanceOf(TensorException.class)
                .hasMessage(ErrorCode.QUERY_FAILED.message()).hasNoCause();
        assertThatThrownBy(() -> repository.results(task.checkId(),null,1,20)).isInstanceOf(TensorException.class);
    }
    @Test void fixedScopeRequiresEverySelectedApiSymbolUnitAndNoForeignSnapshot() {
        var task = task("A","B");
        assertThatThrownBy(() -> repository.create(task,List.of(unit("A")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.create(task,List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.find(task.checkId())).isEmpty();
        var wrongHash = new NewTask(task.checkId(),task.submissionId(),PLUGIN,task.originalRequest(),task.scope(),"f".repeat(64),
                task.capabilityHash(),task.definitionSnapshot(),NOW);
        assertThatThrownBy(() -> repository.create(wrongHash,List.of(unit("A"),unit("B"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid integrity check input");
        var scope = new TaskScope(PLUGIN,List.of("A","B"),DAY,DAY.plusDays(3),List.of(new ApiName("unknown")),NOW);
        var foreign = new NewTask(task.checkId(),task.submissionId(),PLUGIN,task.originalRequest(),scope,task.requestHash(),
                task.capabilityHash(),task.definitionSnapshot(),NOW);
        assertThatThrownBy(() -> repository.create(foreign,List.of())).isInstanceOf(IllegalArgumentException.class);
        repository.create(task,List.of(unit("A"),unit("B")));
        assertThatThrownBy(() -> jdbc.update("UPDATE tensor_integrity_check_result SET unit_key=? WHERE symbol='B'", json.unitKey(API,"A")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test void nonStockNullKeyAndMissingDescriptorLiteralNullSymbolRemainDistinct() throws Exception {
        var definition = snapshot().definition();
        var nonStock = new IntegrityCheckJson.ApiSnapshot(definition,new IntegrityDescriptor(KEY,IntegrityDescriptor.ScopeKind.NON_STOCK,
                null,null,"Not dated",ZoneOffset.UTC,"v1",List.of(),List.of(),List.of()),List.of(),List.of());
        var original = task("null");
        var task = new NewTask(original.checkId(),original.submissionId(),PLUGIN,original.originalRequest(),original.scope(),
                original.requestHash(),original.capabilityHash(),List.of(nonStock),NOW);
        var unit = new NewUnit(UUID.randomUUID(),new IntegrityScope(KEY,null,DAY,DAY.plusDays(3),NOW,null),nonStock);
        repository.create(task,List.of(unit));
        String expected = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("[\"daily\",null]".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var saved = repository.result(task.checkId(),unit.resultId()).orElseThrow();
        assertThat(saved.unitKey()).isEqualTo(expected).isNotEqualTo(json.unitKey(API,"null"));
        assertThat(saved.symbol()).isNull();
        var missing = new IntegrityCheckJson.ApiSnapshot(definition,null,List.of(),List.of());
        var second = task("null");
        var missingTask = new NewTask(second.checkId(),second.submissionId(),PLUGIN,second.originalRequest(),second.scope(),
                second.requestHash(),second.capabilityHash(),List.of(missing),NOW);
        var missingUnit = new NewUnit(UUID.randomUUID(),new IntegrityScope(KEY,"null",DAY,DAY.plusDays(3),NOW,null),missing);
        repository.create(missingTask,List.of(missingUnit));
        assertThat(repository.result(missingTask.checkId(),missingUnit.resultId()).orElseThrow().symbol()).isEqualTo("null");
        assertThat(repository.result(missingTask.checkId(),missingUnit.resultId()).orElseThrow().report().path("descriptor").isNull()).isTrue();
    }

    @Test void readFailureFromMissingTablesIsWrappedWithoutDatabaseDetails() {
        var failing = new JdbcTemplate(source) {
            @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                return super.queryForObject("SELECT COUNT(*) FROM absent_private_table",type,args);
            }
        };
        var reader = new IntegrityCheckRepository(failing,manager,json);
        assertThatThrownBy(() -> reader.tasks(null,1,20)).isInstanceOf(TensorException.class)
                .hasMessage(ErrorCode.QUERY_FAILED.message()).hasNoCause();
    }

    NewIssue incompleteIssue(NewUnit unit) {
        var bound = issue(unit,DAY); var i = bound.issue();
        return new NewIssue(bound.ruleId(),bound.ruleVersion(),new IntegrityIssue(i.type(),i.status(),i.symbol(),i.apiName(),
                i.dateField(),i.date(),i.businessKey(),i.field(),i.relatedDates(),i.reasonCode(),i.message(),i.evidence(),true));
    }
    IntegrityUnitResult error(NewUnit unit, List<IntegrityRuleResult> rules) {
        return new IntegrityUnitResult(unit.scope(),unit.snapshot().descriptor(),json.definitionHash(unit.snapshot().definition()),null,
                IntegrityUnitStatus.ERROR,FAIL,UNKNOWN,UNKNOWN,unknownCounts(),rules,List.of(),NOW,true,false,"ERROR","Stopped");
    }
    @Test void errorRejectsNestedFormalCoverageAndUnbackedPerRuleLowerBounds() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        var descriptor = unit.snapshot().descriptor().rules().getFirst();
        for (var stats : List.of(new IntegrityStatistics(1L,2L,1L,1L,0L,0L,null),
                new IntegrityStatistics(null,null,1L,null,null,null,null),
                new IntegrityStatistics(null,null,null,2L,null,null,null))) {
            var rule = new IntegrityRuleResult(descriptor,FAIL,"X","X",stats,List.of());
            assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),error(unit,List.of(rule)),List.of(incompleteIssue(unit))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertPending(task,unit);
    }
    @Test void knownFailIssuesCannotBeHiddenByUnknownOrPassReportDimensions() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        for (var status : List.of(UNKNOWN,PASS)) {
            assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),completed(unit,status,unknownCounts()),List.of(issue(unit,DAY))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var hidden = new IntegrityUnitResult(unit.scope(),unit.snapshot().descriptor(),json.definitionHash(unit.snapshot().definition()),null,
                IntegrityUnitStatus.ERROR,UNKNOWN,UNKNOWN,UNKNOWN,unknownCounts(),List.of(),List.of(),NOW,true,false,"ERROR","Stopped");
        assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),hidden,List.of(incompleteIssue(unit))))
                .isInstanceOf(IllegalArgumentException.class);
        var unbackedRule = new IntegrityRuleResult(unit.snapshot().descriptor().rules().getFirst(),FAIL,"X","X",unknownCounts(),List.of());
        assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),error(unit,List.of(unbackedRule)),List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertPending(task,unit);
    }

    @Test void storesTwentyThousandIssuesWithLongRuleIdentityAndMessagesWithoutTruncation() {
        var base = snapshot(); var old = base.descriptor();
        String id = "fixture." + "r".repeat(1024), version = "v".repeat(2048), message = "说明".repeat(1500);
        var rule = new IntegrityRuleDescriptor(id,version,"Saved long rule name",IntegrityRuleDescriptor.Dimension.COVERAGE,
                List.of("symbol","day"),List.of(),"Saved description");
        var descriptor = new IntegrityDescriptor(KEY,old.scopeKind(),old.symbolField(),old.dateField(),old.dateLabel(),old.marketZone(),
                old.capabilityVersion(),List.of(),List.of(rule),List.of());
        var snapshot = new IntegrityCheckJson.ApiSnapshot(base.definition(),descriptor,List.of(),base.coreRules());
        var input = task("A");
        var task = new NewTask(input.checkId(),input.submissionId(),PLUGIN,input.originalRequest(),input.scope(),input.requestHash(),
                input.capabilityHash(),List.of(snapshot),NOW);
        var unit = new NewUnit(UUID.randomUUID(),unit("A").scope(),snapshot);
        repository.create(task,List.of(unit));
        var item = issue(unit,DAY).issue();
        var longIssue = new NewIssue(id,version,new IntegrityIssue(item.type(),item.status(),item.symbol(),item.apiName(),item.dateField(),item.date(),
                item.businessKey(),item.field(),item.relatedDates(),"R".repeat(1024),message,item.evidence(),false));
        // Only one issue needs wide text; the other 19,999 use ordinary messages but the same saved rule identity.
        var ordinary = new NewIssue(id,version,item);
        var issues = new ArrayList<NewIssue>(Collections.nCopies(20_000,ordinary)); issues.set(0,longIssue);
        repository.saveResult(task.checkId(),unit.resultId(),completed(unit,FAIL,unknownCounts()),issues);
        var page = repository.issues(task.checkId(),null,1,1);
        assertThat(page.total()).isEqualTo(20_000);
        assertThat(page.items().getFirst().ruleId()).isEqualTo(id);
        assertThat(page.items().getFirst().ruleVersion()).isEqualTo(version);
        assertThat(page.items().getFirst().issue().path("message").asText()).isEqualTo(message);
        assertThat(page.items().getFirst().issue().path("reasonCode").asText()).isEqualTo("R".repeat(1024));
        assertThat(repository.issues(task.checkId(),null,200,100).items()).hasSize(100);
    }

    @Test void everyIncompleteOrNotRunReportRejectsNestedFormalCoverage() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        var rule = new IntegrityRuleResult(unit.snapshot().descriptor().rules().getFirst(),UNKNOWN,"X","X",
                new IntegrityStatistics(1L,2L,1L,1L,0L,0L,null),List.of());
        for (var state : List.of(IntegrityUnitStatus.COMPLETED,IntegrityUnitStatus.NOT_RUN)) {
            var report = new IntegrityUnitResult(unit.scope(),unit.snapshot().descriptor(),json.definitionHash(unit.snapshot().definition()),null,
                    state,UNKNOWN,UNKNOWN,UNKNOWN,unknownCounts(),List.of(rule),List.of(),NOW,
                    state == IntegrityUnitStatus.COMPLETED,true,"X","X");
            assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),report,List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertPending(task,unit);
    }

    @Test void reportDimensionCannotHideWarningOrUnknownIssuesButMayBeStronger() {
        var task = task("A"); var unit = unit("A"); repository.create(task,List.of(unit));
        var base = issue(unit,DAY); var i = base.issue();
        var warnings = new ArrayList<NewIssue>();
        for (var status : List.of(WARN,UNKNOWN)) {
            var type = status == WARN ? IntegrityIssue.Type.SUSPECTED_MISSING : IntegrityIssue.Type.REFERENCE_INCOMPLETE;
            var issue = new NewIssue(base.ruleId(),base.ruleVersion(),new IntegrityIssue(type,status,i.symbol(),i.apiName(),i.dateField(),
                    i.date(),i.businessKey(),i.field(),i.relatedDates(),i.reasonCode(),i.message(),i.evidence(),false));
            warnings.add(issue);
            assertThatThrownBy(() -> repository.saveResult(task.checkId(),unit.resultId(),completed(unit,PASS,unknownCounts()),List.of(issue)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        repository.saveResult(task.checkId(),unit.resultId(),completed(unit,UNKNOWN,unknownCounts()),warnings);
        assertThat(repository.result(task.checkId(),unit.resultId()).orElseThrow().report().path("coverageStatus").asText()).isEqualTo("UNKNOWN");
    }

}
