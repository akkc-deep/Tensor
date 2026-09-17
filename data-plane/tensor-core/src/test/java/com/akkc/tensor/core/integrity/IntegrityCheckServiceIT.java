package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.akkc.tensor.core.integrity.IntegrityCheckServiceTest.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.error.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class IntegrityCheckServiceIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    final IntegrityCheckJson json = new IntegrityCheckJson();
    final LocalPlugin plugin = new LocalPlugin();
    IntegrityCheckRepository repository;
    IntegrityCheckQueue queue;
    IntegrityCheckService service;

    @BeforeAll static void schema() throws Exception {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(source); transactions = new DataSourceTransactionManager(source);
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    "../tensor-app/src/main/resources/db/migration/V9__create_integrity_check_tables.sql"));
        }
        // No securities tables exist: accepting a never-downloaded stock must need none.
    }
    @BeforeEach void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_second_unit");
        jdbc.update("DELETE FROM tensor_integrity_check_issue");
        jdbc.update("DELETE FROM tensor_integrity_check_result");
        jdbc.update("DELETE FROM tensor_integrity_check_task");
        repository = spy(new IntegrityCheckRepository(jdbc, transactions, json));
        queue = new IntegrityCheckQueue(3); service = service(repository);
    }

    @Test void concurrentIdenticalSubmissionsCreateOneCommittedPlanAndOneQueueEntry() throws Exception {
        var request = request();
        var results = concurrent(Collections.nCopies(12, () -> service.submit(request)));
        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(IntegrityCheckService.SubmissionResult.class));
        assertThat(results.stream().map(IntegrityCheckService.SubmissionResult.class::cast)
                .filter(IntegrityCheckService.SubmissionResult::created)).hasSize(1);
        assertThat(results.stream().map(IntegrityCheckService.SubmissionResult.class::cast)
                .map(r -> r.task().checkId()).distinct()).hasSize(1);
        assertCounts(1, 7);
        var checkId = queue.poll(Duration.ZERO).orElseThrow();
        assertThat(repository.results(checkId, null, 1, 100).items()).hasSize(7);
        assertThat(queue.poll(Duration.ZERO)).isEmpty();
    }

    @Test void differentRequestsWithOneSubmissionIdHaveOneWinnerAndOneConflict() throws Exception {
        var request = request(); var changed = new LinkedHashMap<>(request); changed.put("symbols", List.of("C"));
        var results = concurrent(List.of(() -> service.submit(request), () -> service.submit(changed)));
        assertThat(results.stream().filter(IntegrityCheckService.SubmissionResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(TensorException.class::isInstance).map(TensorException.class::cast))
                .singleElement().satisfies(e -> assertThat(e.code()).isEqualTo(ErrorCode.SUBMISSION_CONFLICT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_integrity_check_task", Integer.class)).isOne();
    }

    @Test void crossServiceUniqueConflictReplaysCommittedWinnerAfterReleasingReservation() throws Exception {
        crossServiceRace(false);
    }

    @Test void crossServiceUniqueConflictRejectsDifferentOriginalRequest() throws Exception {
        crossServiceRace(true);
    }

    void crossServiceRace(boolean different) throws Exception {
        var barrier = new CyclicBarrier(2); var reads = new AtomicInteger();
        doAnswer(call -> {
            var result = call.callRealMethod();
            if (reads.incrementAndGet() <= 2) { assertThat(result).isEqualTo(Optional.empty()); barrier.await(10, TimeUnit.SECONDS); }
            return result;
        }).when(repository).findBySubmissionId(any());
        var second = service(repository); var request = request(); var other = new LinkedHashMap<>(request);
        if (different) other.put("symbols", List.of("C"));
        var results = concurrent(List.of(() -> service.submit(request), () -> second.submit(other)));
        assertThat(results.stream().filter(IntegrityCheckService.SubmissionResult.class::isInstance)
                .map(IntegrityCheckService.SubmissionResult.class::cast).filter(IntegrityCheckService.SubmissionResult::created))
                .hasSize(1);
        if (different) assertThat(results.stream().filter(TensorException.class::isInstance).map(TensorException.class::cast))
                .singleElement().satisfies(e -> assertThat(e.code()).isEqualTo(ErrorCode.SUBMISSION_CONFLICT));
        else assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(IntegrityCheckService.SubmissionResult.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_integrity_check_task", Integer.class)).isOne();
        assertThat(queue.poll(Duration.ZERO)).isPresent(); assertThat(queue.poll(Duration.ZERO)).isEmpty();
        try (var a = queue.reserve(); var b = queue.reserve(); var c = queue.reserve()) {
            rejects(queue::reserve, ErrorCode.INTEGRITY_QUEUE_FULL);
        }
    }

    @Test void secondUnitFailureRollsBackWholePlanAndReleasesReservation() throws Exception {
        queue = new IntegrityCheckQueue(1); service = service(repository);
        jdbc.execute("CREATE TRIGGER reject_second_unit BEFORE INSERT ON tensor_integrity_check_result FOR EACH ROW "
                + "BEGIN IF NEW.symbol='B' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private SQL'; END IF; END");
        var request = request();
        rejects(() -> service.submit(request), ErrorCode.PERSISTENCE_FAILED);
        assertCounts(0, 0); assertThat(queue.poll(Duration.ZERO)).isEmpty();
        jdbc.execute("DROP TRIGGER reject_second_unit");
        var result = service.submit(request); assertThat(result.created()).isTrue();
        assertCounts(1, 7); assertThat(queue.poll(Duration.ZERO)).contains(result.task().checkId());
    }

    @Test void concurrentNewRequestsAdmitExactlyRemainingCapacityAndRejectedIdsHaveNoRows() throws Exception {
        var requests = new ArrayList<Map<String, Object>>();
        var actions = new ArrayList<Callable<Object>>();
        for (int i = 0; i < 8; i++) { var request = request(); requests.add(request); actions.add(() -> service.submit(request)); }
        var results = concurrent(actions);
        assertThat(results.stream().filter(IntegrityCheckService.SubmissionResult.class::isInstance)).hasSize(3);
        assertCounts(3, 21);
        for (int i = 0; i < results.size(); i++) if (results.get(i) instanceof TensorException failure) {
            assertThat(failure.code()).isEqualTo(ErrorCode.INTEGRITY_QUEUE_FULL);
            assertThat(repository.findBySubmissionId(UUID.fromString((String) requests.get(i).get("submissionId")))).isEmpty();
        }
        for (int i = 0; i < 3; i++) assertThat(queue.poll(Duration.ZERO)).isPresent();
        assertThat(queue.poll(Duration.ZERO)).isEmpty();
    }

    @Test void queuePublicationFollowsCommittedPlanAndReadFailureStillAllowsReplay() throws Exception {
        doAnswer(call -> {
            call.callRealMethod();
            // DriverManagerDataSource obtains new connections: this observes committed rows.
            assertCounts(1, 7); assertThat(queue.poll(Duration.ZERO)).isEmpty(); return null;
        }).when(repository).create(any(), anyList());
        doThrow(new TestFailure(ErrorCode.QUERY_FAILED)).when(repository).find(any());
        var request = request(); rejects(() -> service.submit(request), ErrorCode.QUERY_FAILED);
        var replay = service.submit(request);
        assertThat(replay.created()).isFalse();
        assertCounts(1, 7);
        assertThat(queue.poll(Duration.ZERO)).contains(replay.task().checkId());
        assertThat(queue.poll(Duration.ZERO)).isEmpty();
    }

    @Test void realRepositoryKeepsRawRequestFullSnapshotAndPendingMixedUnits() {
        var request = request(); var result = service.submit(request).task();
        assertThat(result.originalRequest().get("symbols").toString()).isEqualTo("[\" a \",\"B\",\"A\"]");
        assertThat(result.normalizedScope().get("symbols").toString()).isEqualTo("[\"A\",\"B\"]");
        assertThat(result.definitionSnapshot()).hasSize(4);
        var units = repository.results(result.checkId(), null, 1, 100).items();
        assertThat(units).hasSize(7).allSatisfy(unit -> {
            assertThat(unit.report().get("unitStatus").asText()).isEqualTo("PENDING");
            assertThat(unit.report().get("overallStatus").asText()).isEqualTo("UNKNOWN");
        });
        assertThat(units.stream().filter(u -> u.symbol() == null)).singleElement()
                .satisfies(unit -> assertThat(unit.apiName().value()).isEqualTo("non_stock"));
    }

    IntegrityCheckService service(IntegrityCheckRepository repo) {
        return new IntegrityCheckService(new PluginRegistry(List.of(plugin)), catalog(plugin.definitions()), repo,
                json, queue, Clock.fixed(NOW, ZoneOffset.UTC), IntegrityCheckService.Settings.defaults());
    }
    Map<String, Object> request() {
        return new LinkedHashMap<>(Map.of("submissionId", UUID.randomUUID().toString(), "pluginId", "local",
                "capabilityHash", service.capability(PLUGIN).capabilityHash(), "symbols", List.of(" a ", "B", "A"),
                "startDate", "2026-01-01", "endDate", "2026-01-02"));
    }
    static List<Object> concurrent(List<? extends Callable<Object>> actions) throws Exception {
        try (var executor = Executors.newFixedThreadPool(actions.size())) {
            var start = new CountDownLatch(1); var futures = new ArrayList<Future<Object>>();
            for (var action : actions) futures.add(executor.submit(() -> {
                start.await(); try { return action.call(); } catch (TensorException e) { return e; }
            }));
            start.countDown(); var results = new ArrayList<Object>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        }
    }
    static void assertCounts(int tasks, int units) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_integrity_check_task", Integer.class)).isEqualTo(tasks);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_integrity_check_result", Integer.class)).isEqualTo(units);
    }
}
