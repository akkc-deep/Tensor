package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.akkc.tensor.config.DownloadBindingConfiguration;
import com.akkc.tensor.core.adapter.*;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.*;
import com.akkc.tensor.core.metadata.MetadataQueryService;
import com.akkc.tensor.core.persistence.*;
import com.akkc.tensor.core.registry.*;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.observability.DownloadTaskOperationLogger;
import com.akkc.tensor.plugin.api.BatchDownloadSupport;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.web.download.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class DownloadTaskControllerIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6");
    static final DatasetKey KEY = DatasetKey.of(PluginId.of("http_test"), ApiName.of("prices"));
    static final String REQUEST_ID = "12345678-1234-1234-1234-123456789abc";
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;

    @BeforeAll static void schema() throws Exception {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource); transactions = new DataSourceTransactionManager(dataSource);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V8__create_download_task_tables.sql"));
        }
        jdbc.execute("CREATE TABLE http_test__prices (value VARCHAR(64) NOT NULL PRIMARY KEY, source_plugin VARCHAR(64) NOT NULL, source_api VARCHAR(64) NOT NULL, ingested_at TIMESTAMP(3) NOT NULL) ENGINE=InnoDB");
    }
    @BeforeEach void reset() {
        for (String id : jdbc.queryForList("SELECT batch_id FROM tensor_download_batch ORDER BY LENGTH(batch_key) DESC", String.class))
            jdbc.update("DELETE FROM tensor_download_batch WHERE batch_id=?", id);
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM http_test__prices");
    }

    @Test void returnsCommittedReceiptBeforeBlockedSourceFinishesAndReplaysWithoutAnotherTask() throws Exception {
        try (var flow = new Flow(100)) {
            flow.source.entered = new CountDownLatch(1); flow.source.release = new CountDownLatch(1);
            UUID submission = UUID.randomUUID();
            var response = flow.submit(submission, "SINGLE", "{}");
            assertThat(response.getStatus()).isEqualTo(202);
            JsonNode receipt = flow.json(response);
            String id = receipt.path("taskId").asText();
            assertThat(receipt.path("requestId").asText()).isEqualTo(REQUEST_ID);
            assertThat(receipt.path("status").asText()).isEqualTo("QUEUED");
            assertThat(receipt.path("version").isIntegralNumber()).isTrue();
            assertThat(response.getHeader("Location")).isEqualTo("/api/v1/download-tasks/" + id);
            try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT submission_id FROM tensor_download_task WHERE task_id=?")) {
                statement.setString(1, id);
                try (var row = statement.executeQuery()) { assertThat(row.next()).isTrue(); assertThat(row.getString(1)).isEqualTo(submission.toString()); }
            }
            assertThat(flow.source.entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(flow.source.release.getCount()).isEqualTo(1);
            var replay = flow.submit(submission, "SINGLE", "{}");
            assertThat(replay.getStatus()).isEqualTo(200);
            assertThat(flow.json(replay).path("taskId").asText()).isEqualTo(id);
            assertThat(flow.json(flow.get("/api/v1/download-tasks/" + id)).path("status").asText()).isEqualTo("RUNNING");
            flow.source.release.countDown();
            flow.terminal(id, "SUCCEEDED");
            assertThat(flow.source.calls.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM http_test__prices", Integer.class)).isEqualTo(1);
        }
    }

    @Test void retriesOnlyTheFailedMiddleBatchAndReturnsStoredTimeoutAsQueryData() throws Exception {
        try (var flow = new Flow(100)) {
            flow.source.failDate = "20260911";
            var created = flow.submit(UUID.randomUUID(), "RANGE", "{\"start_date\":\"20260910\",\"end_date\":\"20260912\"}");
            assertThat(created.getStatus()).isEqualTo(202);
            String id = flow.json(created).path("taskId").asText();
            JsonNode failed = flow.terminal(id, "PARTIAL_FAILED");
            assertThat(failed.path("counts").path("succeededBatches").asLong()).isEqualTo(2);
            assertThat(failed.path("lastError").path("code").asText()).isEqualTo("SOURCE_TIMEOUT");
            var batchResponse = flow.get("/api/v1/download-tasks/" + id + "/batches");
            assertThat(batchResponse.getStatus()).isEqualTo(200);
            assertThat(flow.json(batchResponse).path("items").get(1).path("error").path("code").asText()).isEqualTo("SOURCE_TIMEOUT");
            flow.source.failDate = null;
            try (var requests = Executors.newFixedThreadPool(2)) {
                var start = new CountDownLatch(1);
                var first = requests.submit(() -> { start.await(); return flow.control(id, "retry", failed.path("version").asLong()); });
                var second = requests.submit(() -> { start.await(); return flow.control(id, "retry", failed.path("version").asLong()); });
                start.countDown();
                var results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
                assertThat(results).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(202, 409);
                assertThat(results.stream().filter(r -> r.getStatus() == 202).findFirst().orElseThrow().getHeader("Location")).endsWith(id);
            }
            JsonNode done = flow.terminal(id, "SUCCEEDED");
            assertThat(done.path("counts").path("sourceRows").asLong()).isEqualTo(3);
            assertThat(done.path("counts").path("insertedRows").asLong()).isEqualTo(3);
            assertThat(flow.source.calls.get()).isEqualTo(4);
            JsonNode batches = flow.json(flow.get("/api/v1/download-tasks/" + id + "/batches")).path("items");
            assertThat(batches.findValuesAsText("attemptCount")).containsExactly("1", "2", "1");
            assertThat(flow.control(id, "retry", failed.path("version").asLong()).getStatus()).isEqualTo(409);
        }
    }

    @Test void rejectsBadInputsBeforeCreatingTasksAndMapsMissingAndConflictingRequests() throws Exception {
        try (var flow = new Flow(100)) {
            for (String body : List.of("{}", "null", "[]", "{\"expectedVersion\":1}",
                    body(UUID.randomUUID(), "RANGE", "{\"start_date\":\"20260912\",\"end_date\":\"20260910\"}"),
                    body(UUID.randomUUID(), "SINGLE", "{\"start_date\":\"20260910\",\"end_date\":\"20260912\"}"),
                    body(UUID.randomUUID(), "RANGE", "{\"start_date\":123,\"end_date\":\"20260912\"}"))) {
                assertThat(flow.post("/api/v1/download-tasks", body).getStatus()).as(body).isEqualTo(400);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isZero();
            assertThat(flow.source.calls.get()).isZero();
            assertThat(flow.get("/api/v1/download-tasks/" + UUID.randomUUID()).getStatus()).isEqualTo(404);
            assertThat(flow.get("/api/v1/download-tasks/1-1-1-1-1").getStatus()).isEqualTo(400);
            UUID submission = UUID.randomUUID();
            assertThat(flow.submit(submission, "SINGLE", "{}").getStatus()).isEqualTo(202);
            var conflict = flow.submit(submission, "RANGE", "{\"start_date\":\"20260910\",\"end_date\":\"20260912\"}");
            assertThat(conflict.getStatus()).isEqualTo(409);
            assertThat(flow.json(conflict).path("code").asText()).isEqualTo("SUBMISSION_CONFLICT");
        }
    }

    @Test void preservesPaginationFiltersHistoricalQueriesAndReportsDatabaseFailure() throws Exception {
        try (var flow = new Flow(100)) {
            UUID submission = UUID.randomUUID();
            String id = flow.json(flow.submit(submission, "SINGLE", "{}")).path("taskId").asText();
            flow.terminal(id, "SUCCEEDED"); flow.coordinator.close();
            var page = flow.json(flow.get("/api/v1/download-tasks?submissionId=" + submission + "&pluginId=http_test&apiName=prices&status=SUCCEEDED"));
            assertThat(page.path("page").asInt()).isEqualTo(1);
            assertThat(page.path("pageSize").asInt()).isEqualTo(20);
            assertThat(page.path("total").isIntegralNumber()).isTrue();
            assertThat(page.path("total").asLong()).isEqualTo(1);
            assertThat(page.path("items").get(0).path("taskId").asText()).isEqualTo(id);
            assertThat(page.toString()).doesNotContain("activeRunId", "policySnapshot", "requestHash", "runGeneration");
            var beyond = flow.json(flow.get("/api/v1/download-tasks?page=9&pageSize=50"));
            assertThat(beyond.path("page").asInt()).isEqualTo(9); assertThat(beyond.path("total").asLong()).isEqualTo(1);
            assertThat(beyond.path("items")).isEmpty();
            assertThat(flow.json(flow.get("/api/v1/download-tasks?submissionId=" + UUID.randomUUID())).path("total").asLong()).isZero();
            assertThat(flow.submit(submission, "SINGLE", "{}").getStatus()).isEqualTo(200);
            jdbc.execute("RENAME TABLE tensor_download_task TO unavailable_tasks");
            try {
                var failed = flow.get("/api/v1/download-tasks/" + id);
                assertThat(failed.getStatus()).isEqualTo(500);
                assertThat(flow.json(failed).path("code").asText()).isEqualTo("QUERY_FAILED");
                assertThat(flow.json(failed).toString()).doesNotContain("jdbc:", "unavailable_tasks");
            } finally { jdbc.execute("RENAME TABLE unavailable_tasks TO tensor_download_task"); }
        }
    }

    @Test void keepsQueuedCapacityAndAllRouteQueryWhitelists() throws Exception {
        try (var flow = new Flow(1)) {
            flow.source.entered = new CountDownLatch(1); flow.source.release = new CountDownLatch(1);
            String id = flow.json(flow.submit(UUID.randomUUID(), "SINGLE", "{}")).path("taskId").asText();
            assertThat(flow.source.entered.await(5, TimeUnit.SECONDS)).isTrue();
            UUID queuedSubmission = UUID.randomUUID();
            var queued = flow.json(flow.submit(queuedSubmission, "SINGLE", "{}"));
            var full = flow.submit(UUID.randomUUID(), "SINGLE", "{}");
            assertThat(full.getStatus()).isEqualTo(429);
            assertThat(flow.json(full).path("code").asText()).isEqualTo("TASK_QUEUE_FULL");
            var replay = flow.submit(queuedSubmission, "SINGLE", "{}");
            assertThat(replay.getStatus()).isEqualTo(200);
            assertThat(flow.json(replay)).isEqualTo(queued);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isEqualTo(2);
            assertThat(flow.source.calls.get()).isEqualTo(1);
            for (String suffix : List.of("?page=1&page=1", "?status=failed", "?pageSize=10", "?bad=value"))
                assertThat(flow.get("/api/v1/download-tasks" + suffix).getStatus()).isEqualTo(400);
            assertThat(flow.get("/api/v1/download-tasks/" + id + "?page=1").getStatus()).isEqualTo(400);
            assertThat(flow.get("/api/v1/download-tasks/" + id + "/batches?includeSplit=TRUE").getStatus()).isEqualTo(400);
            assertThat(flow.post("/api/v1/download-tasks?extra=1", body(UUID.randomUUID(), "SINGLE", "{}")).getStatus()).isEqualTo(400);
            assertThat(flow.post("/api/v1/download-tasks/" + id + "/retry?extra=1", "{\"expectedVersion\":1}").getStatus()).isEqualTo(400);
            assertThat(flow.get("/api/v1/data-sources/http_test/apis/prices/download-capabilities?extra=1").getStatus()).isEqualTo(400);
        }
    }

    @Test void resumesInterruptedWorkAfterRestartWithoutReplayingSuccessfulOrOrdinaryFailedBatches() throws Exception {
        String id;
        try (var before = new Flow(100)) {
            before.source.failDate = "20260911"; before.source.blockDate = "20260912";
            before.source.entered = new CountDownLatch(1); before.source.release = new CountDownLatch(1);
            id = before.json(before.submit(UUID.randomUUID(), "RANGE", "{\"start_date\":\"20260910\",\"end_date\":\"20260912\"}")).path("taskId").asText();
            assertThat(before.source.entered.await(5, TimeUnit.SECONDS)).isTrue();
            try (var shutdown = Executors.newSingleThreadExecutor()) {
                var stopped = shutdown.submit(before.coordinator::close);
                await(() -> !before.coordinator.isRunning());
                assertThat(stopped.isDone()).isFalse();
                before.source.release.countDown();
                stopped.get(5, TimeUnit.SECONDS);
            }
            assertThat(before.source.calls.get()).isEqualTo(3);
            assertThat(before.json(before.get("/api/v1/download-tasks/" + id)).path("status").asText()).isEqualTo("INTERRUPTED");
        }
        try (var after = new Flow(100)) {
            var interrupted = after.terminal(id, "INTERRUPTED");
            assertThat(after.source.calls.get()).isZero();
            var response = after.control(id, "resume", interrupted.path("version").asLong());
            assertThat(response.getStatus()).isEqualTo(202);
            var done = after.terminal(id, "PARTIAL_FAILED");
            assertThat(done.path("counts").path("succeededBatches").asLong()).isEqualTo(2);
            assertThat(done.path("counts").path("failedBatches").asLong()).isEqualTo(1);
            assertThat(after.source.calls.get()).isEqualTo(1);
            var batches = after.json(after.get("/api/v1/download-tasks/" + id + "/batches")).path("items");
            assertThat(batches.findValuesAsText("attemptCount")).containsExactly("1", "1", "2");
            assertThat(batches.get(1).path("error").path("code").asText()).isEqualTo("SOURCE_TIMEOUT");
        }
    }

    @Test void listsOnlyLeavesUnlessSplitParentsAreExplicitlyIncluded() throws Exception {
        try (var flow = new Flow(100)) {
            flow.source.mode = BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE;
            String id = flow.json(flow.submit(UUID.randomUUID(), "RANGE", "{\"start_date\":\"20260910\",\"end_date\":\"20260912\"}")).path("taskId").asText();
            var done = flow.terminal(id, "SUCCEEDED");
            assertThat(done.path("counts").path("totalBatches").asLong()).isEqualTo(3);
            assertThat(done.path("counts").path("splitBatches").asLong()).isEqualTo(2);
            assertThat(flow.json(flow.get("/api/v1/download-tasks/" + id + "/batches")).path("total").asLong()).isEqualTo(3);
            assertThat(flow.json(flow.get("/api/v1/download-tasks/" + id + "/batches?includeSplit=true")).path("total").asLong()).isEqualTo(5);
            assertThat(flow.json(flow.get("/api/v1/download-tasks/" + id + "/batches?status=SPLIT")).path("items")).isEmpty();
            var parents = flow.json(flow.get("/api/v1/download-tasks/" + id + "/batches?status=SPLIT&includeSplit=true"));
            assertThat(parents.path("total").asLong()).isEqualTo(2);
            assertThat(parents.path("items").findValuesAsText("status")).containsOnly("SPLIT");
        }
    }

    @Test void findsCommittedRequeueAfterReceiptFailureAndExecutesOnlyFailedWorkOnce() throws Exception {
        var receiptFailure = new ReceiptFailureTransactions();
        try (var flow = new Flow(100, receiptFailure, true)) {
            flow.source.failDate = "20260911";
            UUID submission = UUID.randomUUID();
            String params = "{\"start_date\":\"20260910\",\"end_date\":\"20260912\"}";
            String id = flow.json(flow.submit(submission, "RANGE", params)).path("taskId").asText();
            flow.dispatch();
            var failed = flow.terminal(id, "PARTIAL_FAILED");
            flow.source.failDate = null;
            receiptFailure.failNext.set(true);
            var lost = flow.control(id, "retry", failed.path("version").asLong());
            assertThat(lost.getStatus()).isEqualTo(500);
            assertThat(flow.json(lost).path("code").asText()).isEqualTo("PERSISTENCE_FAILED");
            assertThat(receiptFailure.failNext.get()).isFalse();
            var response = flow.get("/api/v1/download-tasks/" + id);
            assertThat(response.getStatus()).isEqualTo(200);
            var queued = flow.json(response);
            assertThat(queued.path("taskId")).isEqualTo(failed.path("taskId"));
            assertThat(queued.path("status").asText()).isEqualTo("QUEUED");
            assertThat(queued.path("version").asLong()).isEqualTo(failed.path("version").asLong() + 1);
            assertThat(queued.path("createdAt")).isEqualTo(failed.path("createdAt"));
            assertThat(queued.path("counts").path("sourceRows").asLong()).isEqualTo(2);
            var facts = flow.facts(id);
            var stale = flow.control(id, "retry", failed.path("version").asLong());
            assertThat(stale.getStatus()).isEqualTo(409);
            assertThat(flow.json(stale).path("code").asText()).isEqualTo("TASK_STATE_CONFLICT");
            var replay = flow.submit(submission, "RANGE", params);
            assertThat(replay.getStatus()).isEqualTo(200);
            assertThat(flow.json(replay).path("version")).isEqualTo(queued.path("version"));
            assertThat(flow.facts(id)).isEqualTo(facts);
            assertThat(flow.source.calls.get()).isEqualTo(3);
            flow.dispatch();
            var done = flow.terminal(id, "SUCCEEDED");
            assertThat(done.path("counts").path("sourceRows").asLong()).isEqualTo(3);
            assertThat(done.path("counts").path("insertedRows").asLong()).isEqualTo(3);
            assertThat(done.path("requestCount").asLong()).isEqualTo(4);
            assertThat(done.path("runRequestCount").asLong()).isEqualTo(1);
            var batches = flow.json(flow.get("/api/v1/download-tasks/" + id + "/batches")).path("items");
            assertThat(batches.findValuesAsText("attemptCount")).containsExactly("1", "2", "1");
            flow.dispatch();
            assertThat(flow.source.calls.get()).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM http_test__prices", Integer.class)).isEqualTo(3);
        }
    }

    @Test void replaysDuringRecoveryAndFaultedAdmissionWithoutChangingStoredFacts() throws Exception {
        var receiptFailure = new ReceiptFailureTransactions();
        try (var flow = new Flow(100, receiptFailure, true)) {
            UUID submission = UUID.randomUUID();
            String id = flow.json(flow.submit(submission, "SINGLE", "{}")).path("taskId").asText();
            // The claim really commits, then the lost receipt puts the real coordinator in RECOVERING.
            receiptFailure.failNext.set(true);
            flow.dispatch();
            assertThat(receiptFailure.failNext.get()).isFalse();
            assertThat(flow.json(flow.get("/api/v1/download-tasks/" + id)).path("status").asText()).isEqualTo("RUNNING");
            flow.assertReplayWhileAdmissionClosed(submission, id);
            flow.dispatch(); // Recover the committed claim, without calling the source.
            flow.terminal(id, "INTERRUPTED");
            flow.worker.shutdown();
            assertThatThrownBy(flow.poller.tick::run).isInstanceOf(TensorException.class);
            flow.assertReplayWhileAdmissionClosed(submission, id);
            assertThat(flow.source.calls.get()).isZero();
        }
    }

    @Test void rejectsControlsForDefinitionReadinessCapacityAndActiveLeaseWithoutChangingStoredFacts() throws Exception {
        try (var flow = new Flow(1, transactions, true)) {
            flow.source.failDate = "20260911";
            String id = flow.json(flow.submit(UUID.randomUUID(), "RANGE", "{\"start_date\":\"20260910\",\"end_date\":\"20260912\"}")).path("taskId").asText();
            flow.dispatch();
            var failed = flow.terminal(id, "PARTIAL_FAILED");
            long version = failed.path("version").asLong();
            flow.source.policyVersion = "http-test-v2";
            flow.assertRejectedControlUnchanged(id, version, 409, "TASK_DEFINITION_CHANGED");
            flow.source.policyVersion = "http-test-v1";
            flow.source.ready = false;
            flow.assertRejectedControlUnchanged(id, version, 409, "PLUGIN_DISABLED");
            flow.source.ready = true;
            assertThat(flow.submit(UUID.randomUUID(), "SINGLE", "{}").getStatus()).isEqualTo(202);
            flow.assertRejectedControlUnchanged(id, version, 429, "TASK_QUEUE_FULL");
            flow.source.entered = new CountDownLatch(1); flow.source.release = new CountDownLatch(1);
            flow.poller.tick.run();
            assertThat(flow.source.entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(flow.json(flow.get("/api/v1/download-tasks/" + id)).path("canRetry").asBoolean()).isFalse();
            flow.assertRejectedControlUnchanged(id, version, 409, "TASK_STATE_CONFLICT");
            assertThat(flow.source.calls.get()).isEqualTo(4);
            flow.source.release.countDown();
            flow.idle();
            assertThat(flow.json(flow.get("/api/v1/download-tasks/" + id)).path("canRetry").asBoolean()).isTrue();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test void exposesCapabilitiesAndRejectsUnknownIdentity() throws Exception {
        try (var flow = new Flow(100)) {
            var response = flow.get("/api/v1/data-sources/http_test/apis/prices/download-capabilities");
            assertThat(response.getStatus()).isEqualTo(200);
            var capability = flow.json(response);
            assertThat(capability.path("single").path("available").asBoolean()).isTrue();
            assertThat(capability.path("range").path("availability").asText()).isEqualTo("AVAILABLE");
            assertThat(capability.path("range").path("dateAxis").asText()).isEqualTo("CALENDAR_DATE");
            assertThat(flow.get("/api/v1/data-sources/http_test/apis/missing/download-capabilities").getStatus()).isEqualTo(409);
            assertThat(flow.source.calls.get()).isZero();
        }
    }

    static String body(UUID id, String mode, String params) {
        return "{\"submissionId\":\"" + id + "\",\"pluginId\":\"http_test\",\"apiName\":\"prices\",\"mode\":\"" + mode + "\",\"params\":" + params + "}";
    }
    static final class Flow implements AutoCloseable {
        final Source source = new Source();
        final DownloadTaskRepository repository;
        final DownloadTaskCoordinator coordinator;
        final ObjectMapper mapper;
        final MockMvc mvc;
        final ManualPoller poller;
        final ExecutorService worker;
        Flow(int capacity) throws Exception { this(capacity, transactions, false); }
        Flow(int capacity, DataSourceTransactionManager transactionManager, boolean manual) throws Exception {
            var constructor = DatasetCatalog.class.getDeclaredConstructor(List.class); constructor.setAccessible(true);
            var catalog = constructor.newInstance(List.of(source.definition()));
            var plugins = new PluginRegistry(List.of(source));
            var adapters = new AdapterRegistry(List.of(new GenericDatasetAdapter(source.definition(), new ValueConverter(), new FingerprintKeyCodec())));
            var validator = new ParameterValidator(); var json = new DownloadTaskJson(); var run = UUID.randomUUID(); var clock = Clock.systemUTC();
            var logger = new DownloadTaskOperationLogger();
            repository = new DownloadTaskRepository(jdbc, transactionManager, json);
            var tasks = new DownloadTaskService(plugins, catalog, adapters, validator, repository, json, clock, run,
                    new DownloadTaskService.Settings(true, capacity, 36600));
            var persistence = new PersistenceService(catalog, new DatasetLockManager(), new ExistingKeyRepository(jdbc), new GenericUpsertRepository(jdbc), transactionManager);
            var runner = new DownloadTaskRunner(tasks, repository, new BatchCommitService(persistence, repository, clock), json, clock, run, DownloadTaskRunner.Settings.defaults());
            poller = manual ? new ManualPoller() : null;
            worker = manual ? Executors.newSingleThreadExecutor() : null;
            if (manual) {
                var coordinatorConstructor = DownloadTaskCoordinator.class.getDeclaredConstructor(DownloadTaskService.class,
                        DownloadTaskRepository.class, DownloadTaskRunner.class, Clock.class, UUID.class,
                        ScheduledExecutorService.class, ExecutorService.class);
                coordinatorConstructor.setAccessible(true);
                coordinator = coordinatorConstructor.newInstance(tasks, repository, runner, clock, run, poller, worker);
            } else coordinator = new DownloadTaskCoordinator(tasks, repository, runner, clock, run);
            coordinator.start();
            var resolver = new DownloadParameterResolver(new DownloadDescriptorResolver(plugins, adapters), validator);
            mapper = new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new JacksonPrecisionConfiguration().precisionModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .registerModule(new DownloadBindingConfiguration().downloadTaskRequestJacksonModule(new DownloadTaskRequestDeserializer(tasks, resolver), new DownloadTaskControlRequestDeserializer()));
            mvc = MockMvcBuilders.standaloneSetup(new DownloadTaskController(tasks, new DownloadTaskQueryService(repository), resolver, logger),
                    new DataSourceController(new MetadataQueryService(plugins, catalog), tasks))
                    .setCustomArgumentResolvers(new DownloadTaskRequestArgumentResolver())
                    .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter())
                    .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        }
        MockHttpServletResponse submit(UUID id, String mode, String params) throws Exception { return post("/api/v1/download-tasks", body(id, mode, params)); }
        MockHttpServletResponse post(String path, String body) throws Exception { return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body).header(RequestIdFilter.HEADER_NAME, REQUEST_ID)).andReturn().getResponse(); }
        MockHttpServletResponse get(String path) throws Exception { return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andReturn().getResponse(); }
        MockHttpServletResponse control(String id, String action, long version) throws Exception { return post("/api/v1/download-tasks/" + id + "/" + action, "{\"expectedVersion\":" + version + "}"); }
        JsonNode json(MockHttpServletResponse response) throws Exception {
            JsonNode value = mapper.readTree(response.getContentAsString());
            String shape = value.has("code") ? "Error" : value.has("requestId") ? "Receipt" : value.has("counts") ? "TaskResponse"
                    : value.has("single") ? "CapabilitiesResponse" : value.path("items").path(0).has("batchId") ? "BatchPage" : "TaskPage";
            assertThat(DownloadTaskContractTest.schema(shape).validate(value)).as(shape).isEmpty();
            return value;
        }
        JsonNode terminal(String id, String expected) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            JsonNode value;
            do {
                var response = get("/api/v1/download-tasks/" + id); assertThat(response.getStatus()).isEqualTo(200); value = json(response);
                if (value.path("status").asText().equals(expected) && (expected.equals("SUCCEEDED") || value.path("canRetry").asBoolean() || value.path("canResume").asBoolean())) return value;
                Thread.sleep(10);
            } while (System.nanoTime() < end);
            assertThat(value.path("status").asText()).isEqualTo(expected); return value;
        }
        void dispatch() throws Exception { poller.tick.run(); idle(); }
        void idle() throws Exception { worker.submit(() -> {}).get(5, TimeUnit.SECONDS); }
        List<List<Map<String, Object>>> facts(String id) {
            return List.of(jdbc.queryForList("SELECT * FROM tensor_download_task WHERE task_id=?", id),
                    jdbc.queryForList("SELECT * FROM tensor_download_batch WHERE task_id=? ORDER BY batch_key", id));
        }
        void assertRejectedControlUnchanged(String id, long version, int status, String code) throws Exception {
            var before = facts(id);
            int calls = source.calls.get();
            var response = control(id, "retry", version);
            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(json(response).path("code").asText()).isEqualTo(code);
            assertThat(facts(id)).isEqualTo(before);
            assertThat(source.calls.get()).isEqualTo(calls);
        }
        void assertReplayWhileAdmissionClosed(UUID submission, String id) throws Exception {
            var before = facts(id);
            var detail = json(get("/api/v1/download-tasks/" + id));
            var rejected = submit(UUID.randomUUID(), "SINGLE", "{}");
            assertThat(rejected.getStatus()).isEqualTo(409);
            assertThat(json(rejected).path("code").asText()).isEqualTo("TASK_STATE_CONFLICT");
            var replay = submit(submission, "SINGLE", "{}");
            assertThat(replay.getStatus()).isEqualTo(200);
            assertThat(replay.getHeader("Location")).isEqualTo("/api/v1/download-tasks/" + id);
            for (String field : List.of("taskId", "status", "version", "createdAt"))
                assertThat(json(replay).path(field)).as(field).isEqualTo(detail.path(field));
            assertThat(facts(id)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isEqualTo(1);
        }
        public void close() { if (source.release != null) source.release.countDown(); coordinator.close(); }
    }
    static final class ManualPoller extends ScheduledThreadPoolExecutor {
        Runnable tick;
        ManualPoller() { super(1); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            tick = command;
            return super.scheduleWithFixedDelay(() -> {}, 1, 1, TimeUnit.DAYS);
        }
    }
    static final class ReceiptFailureTransactions extends DataSourceTransactionManager {
        final AtomicBoolean failNext = new AtomicBoolean();
        ReceiptFailureTransactions() { super(dataSource); }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            super.doCommit(status);
            if (!status.isReadOnly() && failNext.compareAndSet(true, false))
                throw new TransactionSystemException("Controlled lost commit receipt");
        }
    }
    static final class Source implements BatchDownloadSupport {
        final AtomicInteger calls = new AtomicInteger();
        volatile String failDate, blockDate;
        volatile String policyVersion = "http-test-v1";
        volatile boolean ready = true;
        volatile BatchDownloadDescriptor.PlanningMode mode = BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS;
        volatile CountDownLatch entered, release;
        final ApiDescriptor api = new ApiDescriptor(KEY.apiName(), "Prices", "test", QueryMode.snapshot, List.of());
        public PluginDescriptor descriptor() { return new PluginDescriptor(KEY.pluginId(), "HTTP test", "Test", true, true, true, null, List.of(api), List.of(KEY)); }
        public PluginReadiness readiness() { return new PluginReadiness(true, true, ready, ready ? null : "Controlled unavailable source"); }
        public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName name) {
            return Optional.of(new BatchDownloadDescriptor(List.of(endpoint("start_date", "end_date"), endpoint("end_date", "start_date")),
                    "start_date", "end_date", BatchDownloadDescriptor.DateAxis.CALENDAR_DATE, "Calendar date", mode,
                    mode == BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, BatchDownloadDescriptor.Availability.AVAILABLE, null, policyVersion, new BatchDownloadDescriptor.CompletenessRule(BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE, null, "Controlled complete source")));
        }
        public List<DateRange> plan(ApiName api, Map<String,Object> params, BatchCallContext context) {
            var start = LocalDate.parse((String) params.get("start_date"), DateTimeFormatter.BASIC_ISO_DATE);
            var end = LocalDate.parse((String) params.get("end_date"), DateTimeFormatter.BASIC_ISO_DATE);
            if (mode == BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE) return List.of(new DateRange(start, end));
            return start.datesUntil(end.plusDays(1)).map(day -> new DateRange(day, day)).toList();
        }
        public Map<String,Object> sourceParameters(ApiName api, Map<String,Object> params, DateRange range) {
            return Map.of("start_date", range.start().format(DateTimeFormatter.BASIC_ISO_DATE), "end_date", range.end().format(DateTimeFormatter.BASIC_ISO_DATE));
        }
        public DownloadEnvelope downloadBatch(ApiName api, Map<String,Object> params, BatchCallContext context) {
            context.beforeRequest(); calls.incrementAndGet();
            if (entered != null && (blockDate == null || blockDate.equals(params.get("start_date")))) { entered.countDown(); try { if (!release.await(8, TimeUnit.SECONDS)) throw new AssertionError("Source was not released"); } catch (InterruptedException e) { throw new AssertionError(e); } }
            String value = (String) params.getOrDefault("start_date", "single");
            if (value.equals(failDate)) throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "private-token-response");
            return new DownloadEnvelope(KEY.pluginId(), api, params, List.of("value"), 1, List.of(List.of(value)), DownloadStatus.SUCCESS, null);
        }
        public BatchAssessment assess(ApiName api, DateRange range, DownloadEnvelope envelope) {
            return mode == BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE && range.start().isBefore(range.end())
                    ? BatchAssessment.SPLIT_REQUIRED : BatchAssessment.COMPLETE;
        }
        public DownloadEnvelope download(ApiName api, Map<String,Object> params) { throw new AssertionError("Use batch context"); }
        DatasetDefinition definition() {
            return new DatasetDefinition(KEY, "Prices", "test", QueryMode.snapshot, List.of(), TableName.from(KEY),
                    List.of(new ColumnDefinition("value", "Value", LogicalType.STRING, false, 0, 64, null, null, List.of(), false)),
                    new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("value")), List.of(), null, 100);
        }
        static ParameterDescriptor endpoint(String name, String related) { return new ParameterDescriptor(name, name, null, ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, related); }
    }
}
