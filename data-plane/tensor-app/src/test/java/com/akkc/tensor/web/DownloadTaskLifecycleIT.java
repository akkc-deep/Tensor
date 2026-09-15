package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.TensorApplication;
import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.download.task.BatchCommitService;
import com.akkc.tensor.core.download.task.DownloadBatch;
import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskCoordinator;
import com.akkc.tensor.core.download.task.DownloadTaskJson;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.plugin.api.BatchDownloadSupport;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.batch.BatchAssessment;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DownloadTaskLifecycleIT {
    private static final DatasetKey KEY = DatasetKey.of(PluginId.of("http_test"), ApiName.of("prices"));
    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern SCRIPT = Pattern.compile("<script[^>]+src=\"([^\"]+)\"");
    private static final String DROP_HEADER = "X-Tensor-Test-Drop-Receipt";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6");

    private final ObjectMapper mapper = new ObjectMapper();
    private Path root;
    private String migrations;
    private DriverManagerDataSource adminDataSource;
    private JdbcTemplate jdbc;
    private ControlledSource source;

    @BeforeEach
    void productionSchemaAndControlledSource() {
        root = repositoryRoot();
        Path location = root.resolve("data-plane/tensor-app/target/classes/db/migration").toAbsolutePath();
        assertThat(Files.isDirectory(location)).isTrue();
        migrations = "filesystem:" + location;
        adminDataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway flyway = Flyway.configure().dataSource(adminDataSource).locations(migrations).cleanDisabled(false).load();
        flyway.clean();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(7);
        jdbc = new JdbcTemplate(adminDataSource);
        createLifecycleTable(jdbc);
        source = new ControlledSource();
    }

    @Test
    void browserDisconnectAndRetryUseTheRealApplication() throws Exception {
        try (ConfigurableApplicationContext application = start(adminDataSource, source)) {
            assertApplicationAssets(application);
            try {
                runBrowser(application, "flow", null,
                        List.of("liveDisconnectReloadAndReopen", "livePartialFailureRetriesOnlyTheMiddleBatch"));
            } finally {
                source.gates.values().forEach(CountDownLatch::countDown);
            }
            assertThat(jdbc.queryForList("SELECT value FROM http_test__prices ORDER BY value", String.class))
                    .containsExactly("20260901", "20260902", "20260903", "20260910", "20260911", "20260912");
        }
    }

    @Test
    void recreatedApplicationRecoversWithoutAutomaticSourceCalls() throws Exception {
        UUID oldRun;
        UUID queuedId;
        UUID unplannedId;
        UUID mixedId;
        UUID allSucceededId;
        UUID pendingId;
        try (ConfigurableApplicationContext before = start(adminDataSource, source)) {
            oldRun = before.getBean("downloadTaskRunId", UUID.class);
            DownloadTaskRepository repository = before.getBean(DownloadTaskRepository.class);
            BatchCommitService commits = before.getBean(BatchCommitService.class);
            DownloadTask template = acceptedTemplate(before);
            before.getBean(DownloadTaskCoordinator.class).close();
            queuedId = inserted(repository, template, oldRun, "2026-09-26", "2026-09-28").taskId();
            DownloadTask unplanned = inserted(repository, template, oldRun, "2026-09-29", "2026-10-01");
            repository.claimTask(unplanned.taskId(), oldRun, Instant.now(), Instant.now().plusSeconds(120)).orElseThrow();
            unplannedId = unplanned.taskId();
            mixedId = preparePlan(repository, commits, template, oldRun,
                    List.of("2026-09-20", "2026-09-21", "2026-09-22"), Snapshot.MIXED).taskId();
            allSucceededId = preparePlan(repository, commits, template, oldRun,
                    List.of("2026-09-23", "2026-09-24", "2026-09-25"), Snapshot.ALL_SUCCEEDED).taskId();
            pendingId = preparePlan(repository, commits, template, oldRun,
                    List.of("2026-10-02", "2026-10-03", "2026-10-04"), Snapshot.MIXED_PENDING).taskId();
            source.configureResume();
        }

        try (ConfigurableApplicationContext after = start(adminDataSource, source)) {
            UUID newRun = after.getBean("downloadTaskRunId", UUID.class);
            assertThat(newRun).isNotEqualTo(oldRun);
            DownloadTaskRepository repository = after.getBean(DownloadTaskRepository.class);
            assertThat(repository.findTask(queuedId).orElseThrow().status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
            assertThat(repository.findTask(unplannedId).orElseThrow()).satisfies(task -> {
                assertThat(task.status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
                assertThat(task.planReady()).isFalse();
            });
            assertThat(repository.findTask(mixedId).orElseThrow().status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
            assertThat(repository.findTask(allSucceededId).orElseThrow().status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
            assertThat(repository.counts(allSucceededId))
                    .isEqualTo(new DownloadTaskRepository.Counts(3, 0, 0, 3, 0, 0, 3, 3, 0));
            assertThat(repository.findTask(pendingId).orElseThrow().status()).isEqualTo(DownloadTask.Status.INTERRUPTED);
            assertThat(repository.batches(pendingId, new DownloadTaskRepository.BatchFilter(null, false), 1, 100).items())
                    .extracting(DownloadBatch::status).containsExactly(DownloadBatch.Status.SUCCEEDED,
                            DownloadBatch.Status.FAILED, DownloadBatch.Status.PENDING);
            assertThat(source.totalCalls()).isZero();
            int port = port(after);
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThat(getJson(port, "/api/v1/download-tasks/" + mixedId).path("status").asText())
                        .isEqualTo("INTERRUPTED");
            }
            assertThat(source.totalCalls()).isZero();

            try {
                runBrowser(after, "resume", mixedId, List.of("liveResumeAfterRestart"));
            } finally {
                source.gates.values().forEach(CountDownLatch::countDown);
            }
            DownloadTask mixed = repository.findTask(mixedId).orElseThrow();
            assertThat(mixed.status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
            assertThat(mixed.requestCount()).isEqualTo(4);
            assertThat(repository.counts(mixedId))
                    .isEqualTo(new DownloadTaskRepository.Counts(3, 0, 0, 2, 1, 0, 2, 2, 0));
            assertThat(repository.batches(mixedId, new DownloadTaskRepository.BatchFilter(null, false), 1, 100).items())
                    .extracting(DownloadBatch::attemptCount).containsExactly(1, 1, 2);
            assertThat(jdbc.queryForList("SELECT value FROM http_test__prices WHERE value BETWEEN '20260920' AND '20260922' ORDER BY value", String.class))
                    .containsExactly("20260920", "20260922");

            for (UUID taskId : List.of(queuedId, unplannedId, pendingId)) {
                DownloadTask interrupted = repository.findTask(taskId).orElseThrow();
                assertThat(postJson(port, "/api/v1/download-tasks/" + taskId + "/resume",
                        Map.of("expectedVersion", interrupted.version()), Map.of()).statusCode()).isEqualTo(202);
            }
            assertThat(await(() -> terminal(repository, queuedId) && terminal(repository, unplannedId)
                    && terminal(repository, pendingId), 15)).isTrue();
            assertThat(repository.findTask(queuedId).orElseThrow().status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
            assertThat(repository.findTask(unplannedId).orElseThrow().status()).isEqualTo(DownloadTask.Status.SUCCEEDED);
            assertThat(repository.findTask(pendingId).orElseThrow().status()).isEqualTo(DownloadTask.Status.PARTIAL_FAILED);
            assertThat(repository.counts(pendingId))
                    .isEqualTo(new DownloadTaskRepository.Counts(3, 0, 0, 2, 1, 0, 2, 2, 0));
            assertThat(repository.batches(pendingId, new DownloadTaskRepository.BatchFilter(null, false), 1, 100).items())
                    .extracting(DownloadBatch::attemptCount).containsExactly(1, 1, 1);
            assertThat(source.calls.get("2026-10-04").get()).isEqualTo(1);
            assertThat(source.calls).doesNotContainKeys("2026-10-02", "2026-10-03");
            assertThat(jdbc.queryForList("SELECT value FROM http_test__prices WHERE value BETWEEN '20261002' AND '20261004' ORDER BY value", String.class))
                    .containsExactly("20261002", "20261004");
        }
    }

    @Test
    void lostSubmissionResponseFindsOneCommittedTask() throws Exception {
        try (ConfigurableApplicationContext application = start(adminDataSource, source)) {
            int port = port(application);
            UUID submission = UUID.randomUUID();
            String body = mapper.writeValueAsString(Map.of("submissionId", submission, "pluginId", "http_test",
                    "apiName", "prices", "mode", "SINGLE", "params", Map.of()));
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/download-tasks"))
                    .header("Content-Type", "application/json").header(DROP_HEADER, "1")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            try {
                HttpResponse<String> lost = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(lost.statusCode()).isEqualTo(500);
                assertThat(lost.headers().firstValue("Location")).isEmpty();
                assertThat(lost.body()).doesNotContain("taskId", "submissionId", "createdAt", "QUEUED", "controlled-lifecycle-canary");
                System.out.println("Lifecycle receipt injection: committed 202 discarded; client received HTTP 500 without receipt");
            } catch (IOException expectedDisconnect) {
                assertThat(expectedDisconnect).hasMessageNotContaining("controlled-lifecycle-canary");
                System.out.println("Lifecycle receipt injection: client received IOException without receipt");
            }
            assertThat(await(() -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tensor_download_task WHERE submission_id=?", Integer.class, submission.toString()) == 1, 8)).isTrue();
            JsonNode found = getJson(port, "/api/v1/download-tasks?submissionId=" + submission);
            assertThat(found.path("total").asLong()).isEqualTo(1);
            String taskId = found.path("items").get(0).path("taskId").asText();
            HttpResponse<String> replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(request.uri())
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(replay.body()).path("taskId").asText()).isEqualTo(taskId);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task WHERE submission_id=?",
                    Integer.class, submission.toString())).isEqualTo(1);
            assertThat(await(() -> source.totalCalls() == 1, 8)).isTrue();
        }
    }

    @Test
    void documentedSchemaPrivilegesSupportV8AndTaskWrites() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String schema = "tensor_lifecycle_priv_" + suffix;
        String user = "tensor_lifecycle_" + suffix;
        String password = "Lifecycle-" + suffix + "-9x";
        JdbcTemplate rootJdbc = rootJdbc();
        try {
            rootJdbc.execute("CREATE DATABASE `" + schema + "`");
            rootJdbc.execute("CREATE USER '" + user + "'@'%' IDENTIFIED BY '" + password + "'");
            rootJdbc.execute("GRANT CREATE,SELECT,INSERT,UPDATE,ALTER,INDEX,REFERENCES ON `" + schema + "`.* TO '" + user + "'@'%'");
            DriverManagerDataSource restricted = new DriverManagerDataSource(jdbcUrl(schema), user, password);
            Flyway flyway = Flyway.configure().dataSource(restricted).locations(migrations).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(7);
            JdbcTemplate restrictedJdbc = new JdbcTemplate(restricted);
            createLifecycleTable(restrictedJdbc);
            ControlledSource restrictedSource = new ControlledSource();
            try (ConfigurableApplicationContext application = start(restricted, restrictedSource)) {
                int port = port(application);
                UUID submission = UUID.randomUUID();
                HttpResponse<String> receipt = postJson(port, "/api/v1/download-tasks", Map.of(
                        "submissionId", submission, "pluginId", "http_test", "apiName", "prices",
                        "mode", "SINGLE", "params", Map.of()), Map.of());
                assertThat(receipt.statusCode()).isEqualTo(202);
                UUID taskId = UUID.fromString(mapper.readTree(receipt.body()).path("taskId").asText());
                assertThat(await(() -> application.getBean(DownloadTaskRepository.class).findTask(taskId)
                        .orElseThrow().status() == DownloadTask.Status.SUCCEEDED, 10)).isTrue();
                assertThat(restrictedJdbc.queryForObject("SELECT COUNT(*) FROM http_test__prices", Integer.class)).isEqualTo(1);
                assertThat(restrictedJdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS"
                        + " WHERE CONSTRAINT_SCHEMA=? AND TABLE_NAME='tensor_download_batch'", Integer.class, schema)).isEqualTo(2);
            }
            String grants = String.join("\n", rootJdbc.queryForList("SHOW GRANTS FOR '" + user + "'@'%'", String.class));
            assertThat(grants).contains("CREATE", "SELECT", "INSERT", "UPDATE", "ALTER", "INDEX", "REFERENCES")
                    .contains("GRANT USAGE ON *.*")
                    .doesNotContain("DROP", "DELETE", "ALL PRIVILEGES");
        } finally {
            rootJdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            rootJdbc.execute("DROP USER IF EXISTS '" + user + "'@'%'");
        }
    }

    private ConfigurableApplicationContext start(DataSource dataSource, ControlledSource controlled) {
        String url = ((DriverManagerDataSource) dataSource).getUrl();
        String username = ((DriverManagerDataSource) dataSource).getUsername();
        String password = ((DriverManagerDataSource) dataSource).getPassword();
        GenericDatasetAdapter adapter = new GenericDatasetAdapter(controlled.definition(), new ValueConverter(), new FingerprintKeyCodec());
        return new SpringApplicationBuilder(TensorApplication.class).web(WebApplicationType.SERVLET)
                .initializers(raw -> {
                    GenericApplicationContext context = (GenericApplicationContext) raw;
                    context.getBeanFactory().registerSingleton("lifecycleTypeExcludeFilter", new LifecycleTypeExcludeFilter());
                    context.registerBean("lifecycleSource", ControlledSource.class, () -> controlled);
                    context.registerBean("lifecycleDatasetAdapter", DatasetAdapter.class, () -> adapter);
                    context.registerBean("lifecycleController", LifecycleController.class,
                            () -> new LifecycleController(controlled, context.getBean(JdbcTemplate.class)));
                    context.registerBean("lostReceiptFilter", FilterRegistrationBean.class,
                            () -> lostReceiptFilter(new LostReceiptFilter()));
                })
                .properties("server.address=127.0.0.1", "server.port=0", "spring.profiles.active=production",
                        "spring.flyway.locations=" + migrations, "spring.flyway.fail-on-missing-locations=true",
                        "spring.datasource.hikari.connection-timeout=1000", "TENSOR_DB_URL=" + url,
                        "TENSOR_DB_USERNAME=" + username, "TENSOR_DB_PASSWORD=" + password,
                        "tensor.plugins.tushare-pro.enabled=false", "TENSOR_TUSHARE_TOKEN=")
                .run();
    }

    private DownloadTask acceptedTemplate(ConfigurableApplicationContext application) {
        DownloadTaskService service = application.getBean(DownloadTaskService.class);
        DownloadTaskRepository repository = application.getBean(DownloadTaskRepository.class);
        DownloadTask accepted = service.submit(new DownloadTaskService.Submission(UUID.randomUUID(), KEY,
                DownloadMode.RANGE, Map.of("start_date", "20260920", "end_date", "20260922"))).task();
        assertThat(await(() -> terminal(repository, accepted.taskId()), 10)).isTrue();
        jdbc.update("DELETE FROM tensor_download_batch WHERE task_id=?", accepted.taskId().toString());
        jdbc.update("DELETE FROM tensor_download_task WHERE task_id=?", accepted.taskId().toString());
        jdbc.update("DELETE FROM http_test__prices WHERE value BETWEEN '20260920' AND '20260922'");
        return accepted;
    }

    private DownloadTask inserted(DownloadTaskRepository repository, DownloadTask template, UUID run,
            String start, String end) {
        return repository.insert(new DownloadTaskRepository.NewTask(UUID.randomUUID(), UUID.randomUUID(), KEY,
                DownloadMode.RANGE, Map.of("start_date", LocalDate.parse(start).format(BASIC),
                        "end_date", LocalDate.parse(end).format(BASIC)), template.definitionHash(),
                template.policySnapshot(), run, Instant.now()));
    }

    private DownloadTask preparePlan(DownloadTaskRepository repository, BatchCommitService commits,
            DownloadTask template, UUID run, List<String> dates, Snapshot snapshot) {
        DownloadTask inserted = inserted(repository, template, run, dates.getFirst(), dates.getLast());
        DownloadTask claimed = repository.claimTask(inserted.taskId(), run, Instant.now(), Instant.now().plusSeconds(120)).orElseThrow();
        var permit = new DownloadTaskRepository.ExecutionPermit(claimed.taskId(), run, claimed.runGeneration());
        List<DownloadTaskRepository.NewBatch> roots = new ArrayList<>();
        for (int index = 0; index < dates.size(); index++) {
            LocalDate day = LocalDate.parse(dates.get(index));
            roots.add(new DownloadTaskRepository.NewBatch(UUID.randomUUID(), "%06d".formatted(index + 1),
                    new DateRange(day, day), Map.of("start_date", day.format(BASIC), "end_date", day.format(BASIC))));
        }
        repository.savePlan(permit, roots, 10, Instant.now());
        for (int index = 0; index < roots.size(); index++) {
            DownloadTaskRepository.NewBatch batch = roots.get(index);
            if (snapshot == Snapshot.MIXED_PENDING && index == 2) break;
            repository.claimBatch(permit, batch.batchId(), Instant.now()).orElseThrow();
            repository.reserveRequest(permit, 20, Instant.now());
            if (snapshot != Snapshot.ALL_SUCCEEDED && index == 1) {
                repository.failBatch(permit, batch.batchId(), ErrorCode.SOURCE_TIMEOUT, Instant.now());
            } else if (snapshot == Snapshot.MIXED && index == 2) {
                break;
            } else {
                String value = batch.sourceParams().get("start_date").toString();
                DownloadEnvelope envelope = new DownloadEnvelope(KEY.pluginId(), KEY.apiName(), batch.sourceParams(),
                        List.of("value"), 1, List.of(List.of(value)), DownloadStatus.SUCCESS, null);
                DatasetAdapter adapter = new GenericDatasetAdapter(source.definition(), new ValueConverter(), new FingerprintKeyCodec());
                commits.commit(permit, batch.batchId(), adapter.adapt(envelope, Instant.now()), 1);
            }
        }
        return inserted;
    }

    private void runBrowser(ConfigurableApplicationContext application, String scenario, UUID taskId,
            List<String> expectedNames) throws Exception {
        Path target = root.resolve("data-plane/tensor-app/target");
        Files.createDirectories(target);
        String unique = scenario + "-" + UUID.randomUUID();
        Path report = target.resolve("download-task-lifecycle-" + unique + ".json").toAbsolutePath();
        Path log = target.resolve("download-task-lifecycle-" + unique + ".log").toAbsolutePath();
        ProcessBuilder builder = browserProcess("e2e/download-task-lifecycle.spec.js", scenario, taskId,
                "http://127.0.0.1:" + port(application), report, log);
        assertThat(runBrowserProcess(builder, log)).as("Lifecycle Playwright log: %s", log).isZero();
        assertPlaywrightReport(report, expectedNames);
    }

    private ProcessBuilder browserProcess(String spec, String scenario, UUID taskId, String baseUrl,
            Path report, Path log) {
        ProcessBuilder builder = new ProcessBuilder("npm", "--prefix", "control-plane", "run", "test:e2e", "--", spec);
        builder.directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(name -> name.matches(
                "(?i).*(DB_|PASSWORD|TUSHARE|TOKEN|JAVA_TOOL_OPTIONS|JAVA_OPTIONS|NODE_OPTIONS).*"));
        environment.keySet().removeIf(name -> name.startsWith("TENSOR_") || name.startsWith("SPRING_")
                || name.startsWith("M14_") || name.startsWith("MYSQL_")
                || (name.startsWith("PLAYWRIGHT_") && !name.equals("PLAYWRIGHT_BROWSERS_PATH")));
        Path node = root.resolve("data-plane/tensor-app/target/frontend/node");
        environment.put("PATH", node + java.io.File.pathSeparator + environment.getOrDefault("PATH", ""));
        environment.put("PLAYWRIGHT_BASE_URL", baseUrl);
        environment.put("PLAYWRIGHT_JSON_OUTPUT_FILE", report.toString());
        environment.put("TENSOR_TASK_LIVE_E2E", "1");
        environment.put("TENSOR_TASK_LIFECYCLE_SCENARIO", scenario);
        if (taskId != null) environment.put("TENSOR_TASK_LIFECYCLE_TASK_ID", taskId.toString());
        environment.remove("CI");
        return builder;
    }

    private int runBrowserProcess(ProcessBuilder builder, Path log) throws Exception {
        Process process = builder.start();
        try {
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                throw new AssertionError("Lifecycle Playwright timed out; log=" + log);
            }
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                List<ProcessHandle> children = process.descendants().toList();
                children.forEach(ProcessHandle::destroy);
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
                children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            }
        }
    }

    @Test
    void rejectsMissingSpecAndInvalidBrowserReports() throws Exception {
        Path directory = Files.createTempDirectory(root.resolve("data-plane/tensor-app/target"), "lifecycle-report-probe-");
        Path report = directory.resolve("report.json");
        Path log = directory.resolve("missing-spec.log");
        assertThat(runBrowserProcess(browserProcess("e2e/download-task-lifecycle-missing.spec.js", "flow", null,
                "http://127.0.0.1:4173", report, log), log)).isNotZero();
        assertThat(Files.readString(log)).contains("No tests found");
        List<String> expected = List.of("liveResumeAfterRestart");
        Files.deleteIfExists(report);
        assertThatThrownBy(() -> assertPlaywrightReport(report, expected)).isInstanceOf(AssertionError.class);
        for (String invalid : List.of("{", "{}", "{\"errors\":[],\"stats\":{\"expected\":0,\"unexpected\":0,\"skipped\":0,\"flaky\":0},\"suites\":[]}")) {
            Files.writeString(report, invalid);
            assertThatThrownBy(() -> assertPlaywrightReport(report, expected))
                    .isInstanceOfAny(IOException.class, AssertionError.class);
        }
        String valid = """
                {"errors":[],"stats":{"expected":1,"unexpected":0,"skipped":0,"flaky":0},"suites":[{"specs":[
                  {"title":"liveResumeAfterRestart","file":"download-task-lifecycle.spec.js","ok":true,"tests":[
                    {"expectedStatus":"passed","status":"expected","results":[{"status":"passed","retry":0}]}
                  ]}
                ]}]}
                """;
        Files.writeString(report, valid);
        assertPlaywrightReport(report, expected);
        for (String invalid : List.of(valid.replace("\"expected\":1", "\"expected\":0"),
                valid.replace("\"flaky\":0", "\"flaky\":1"), valid.replace("\"retry\":0", "\"retry\":1"),
                valid.replace("\"retry\":0", "\"other\":0"), valid.replace("download-task-lifecycle.spec.js", "wrong.spec.js"))) {
            Files.writeString(report, invalid);
            assertThatThrownBy(() -> assertPlaywrightReport(report, expected)).isInstanceOf(AssertionError.class);
        }
    }

    private void assertPlaywrightReport(Path report, List<String> expectedNames) throws IOException {
        assertThat(report).isRegularFile();
        JsonNode rootNode = mapper.readTree(report.toFile());
        assertThat(rootNode.isObject()).isTrue();
        assertThat(rootNode.path("errors").isArray()).isTrue();
        assertThat(rootNode.path("errors")).isEmpty();
        assertThat(rootNode.path("stats").path("expected")).isEqualTo(mapper.valueToTree(expectedNames.size()));
        for (String field : List.of("unexpected", "skipped", "flaky"))
            assertThat(rootNode.path("stats").path(field)).isEqualTo(mapper.valueToTree(0));
        assertThat(rootNode.path("suites").isArray()).isTrue();
        List<JsonNode> specs = new ArrayList<>();
        collectSpecs(rootNode.path("suites"), specs);
        assertThat(specs).hasSize(expectedNames.size());
        assertThat(specs).extracting(spec -> spec.path("title").asText()).containsExactlyElementsOf(expectedNames);
        for (JsonNode spec : specs) {
            assertThat(Path.of(spec.path("file").asText()).getFileName().toString())
                    .isEqualTo("download-task-lifecycle.spec.js");
            assertThat(spec.path("ok").asBoolean()).isTrue();
            assertThat(spec.path("tests")).hasSize(1);
            JsonNode test = spec.path("tests").get(0);
            assertThat(test.path("expectedStatus").asText()).isEqualTo("passed");
            assertThat(test.path("status").asText()).isEqualTo("expected");
            assertThat(test.path("results")).hasSize(1);
            assertThat(test.path("results").get(0).path("status").asText()).isEqualTo("passed");
            assertThat(test.path("results").get(0).path("retry")).isEqualTo(mapper.valueToTree(0));
        }
    }

    private static void collectSpecs(JsonNode suites, List<JsonNode> output) {
        for (JsonNode suite : suites) {
            suite.path("specs").forEach(output::add);
            collectSpecs(suite.path("suites"), output);
        }
    }

    private void assertApplicationAssets(ConfigurableApplicationContext application) {
        int port = port(application);
        assertThat(getText(port, "/actuator/health")).contains("\"status\":\"UP\"");
        String html = getText(port, "/downloads");
        assertThat(html).contains("<div id=\"app\"></div>");
        Matcher script = SCRIPT.matcher(html);
        assertThat(script.find()).isTrue();
        assertThat(getText(port, script.group(1))).hasSizeGreaterThan(10_000).doesNotContain("<div id=\"app\">");
    }

    private JsonNode getJson(int port, String path) {
        try {
            return mapper.readTree(getText(port, path));
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static String getText(int port, String path) {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        } catch (IOException | InterruptedException failure) {
            throw new AssertionError(failure);
        }
    }

    private HttpResponse<String> postJson(int port, String path, Object body, Map<String, String> headers) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            headers.forEach(request::header);
            return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException failure) {
            throw new AssertionError(failure);
        }
    }

    private static int port(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static boolean terminal(DownloadTaskRepository repository, UUID taskId) {
        DownloadTask.Status status = repository.findTask(taskId).orElseThrow().status();
        return status != DownloadTask.Status.QUEUED && status != DownloadTask.Status.RUNNING;
    }

    private static boolean await(BooleanSupplier condition, int seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private static void createLifecycleTable(JdbcTemplate target) {
        target.execute("CREATE TABLE http_test__prices (value VARCHAR(64) NOT NULL PRIMARY KEY,"
                + " source_plugin VARCHAR(64) NOT NULL, source_api VARCHAR(64) NOT NULL,"
                + " ingested_at TIMESTAMP(3) NOT NULL) ENGINE=InnoDB");
    }

    private JdbcTemplate rootJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(jdbcUrl("mysql"), "root", MYSQL.getPassword()));
    }

    private String jdbcUrl(String schema) {
        String base = MYSQL.getJdbcUrl();
        int query = base.indexOf('?');
        int slash = base.lastIndexOf('/', query < 0 ? base.length() : query);
        return base.substring(0, slash + 1) + schema + (query < 0 ? "" : base.substring(query));
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("data-plane/pom.xml"))
                    && Files.isRegularFile(candidate.resolve("control-plane/package.json"))) return candidate;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }

    private static FilterRegistrationBean<LostReceiptFilter> lostReceiptFilter(LostReceiptFilter filter) {
        FilterRegistrationBean<LostReceiptFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.addUrlPatterns("/api/v1/download-tasks");
        return registration;
    }

    private enum Snapshot { MIXED, MIXED_PENDING, ALL_SUCCEEDED }

    private static final class LifecycleTypeExcludeFilter extends TypeExcludeFilter {
        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
            String name = reader.getClassMetadata().getClassName();
            return name.startsWith(DownloadTaskLifecycleIT.class.getName())
                    || name.equals("com.akkc.tensor.web.GlobalExceptionHandlerTest$FailureController");
        }
        @Override public boolean equals(Object other) { return other instanceof LifecycleTypeExcludeFilter; }
        @Override public int hashCode() { return LifecycleTypeExcludeFilter.class.hashCode(); }
    }

    private static final class LostReceiptFilter extends OncePerRequestFilter {
        private final AtomicBoolean first = new AtomicBoolean(true);

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (!"1".equals(request.getHeader(DROP_HEADER)) || !first.compareAndSet(true, false)) {
                chain.doFilter(request, response);
                return;
            }
            ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
            chain.doFilter(request, captured);
            if (captured.getStatus() != HttpStatus.ACCEPTED.value()) {
                captured.copyBodyToResponse();
                return;
            }
            response.reset();
            throw new IOException("Controlled lifecycle receipt disconnect");
        }
    }

    @RestController
    @RequestMapping("/__test/download-task-lifecycle")
    final class LifecycleController {
        private final ControlledSource source;
        private final JdbcTemplate jdbc;

        LifecycleController(ControlledSource source, JdbcTemplate jdbc) {
            this.source = source;
            this.jdbc = jdbc;
        }

        @PostMapping("/scenario")
        ResponseEntity<?> scenario(@RequestBody ScenarioRequest request) {
            if (request == null || !List.of("disconnect", "partial").contains(request.scenario()))
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid scenario"));
            if (jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task WHERE plugin_id='http_test'"
                    + " AND api_name='prices' AND status IN ('QUEUED','RUNNING')", Integer.class) != 0
                    || !source.configure(request.scenario()))
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Scenario is still active"));
            return ResponseEntity.ok(Map.of("scenario", request.scenario()));
        }

        @PostMapping("/release")
        ResponseEntity<?> release(@RequestBody ReleaseRequest request) {
            if (request == null || !source.release(request.date()))
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown lifecycle date"));
            return ResponseEntity.ok(Map.of("date", request.date()));
        }

        @GetMapping("/snapshot")
        SnapshotResponse snapshot() {
            List<String> range = source.range();
            List<String> keys = range.isEmpty() ? List.of() : jdbc.queryForList(
                    "SELECT value FROM http_test__prices WHERE value BETWEEN ? AND ? ORDER BY value", String.class,
                    range.getFirst().replace("-", ""), range.getLast().replace("-", ""));
            return new SnapshotResponse(source.scenario(), source.callsInRange(), keys, keys.size(), source.inFlight());
        }
    }

    record ScenarioRequest(String scenario) {}
    record ReleaseRequest(String date) {}
    record SnapshotResponse(String scenario, Map<String, Integer> calls, List<String> committedKeys,
            int rowCount, boolean inFlight) {}

    static final class ControlledSource implements BatchDownloadSupport {
        private static final Map<String, List<String>> RANGES = Map.of(
                "disconnect", List.of("2026-09-01", "2026-09-02", "2026-09-03"),
                "partial", List.of("2026-09-10", "2026-09-11", "2026-09-12"),
                "resume", List.of("2026-09-20", "2026-09-21", "2026-09-22"));
        private final ConcurrentHashMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CountDownLatch> gates = new ConcurrentHashMap<>();
        private final AtomicInteger inFlight = new AtomicInteger();
        private volatile String scenario;

        synchronized boolean configure(String next) {
            if (inFlight.get() != 0) return false;
            scenario = next;
            calls.clear();
            gates.clear();
            if ("disconnect".equals(next)) range().forEach(date -> gates.put(date, new CountDownLatch(1)));
            return true;
        }

        synchronized void configureResume() {
            if (!configure("resume")) throw new IllegalStateException("Source still active");
            gates.put("2026-09-22", new CountDownLatch(1));
        }

        boolean release(String date) {
            if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
            CountDownLatch gate = gates.get(date);
            if (gate == null) return false;
            gate.countDown();
            return true;
        }

        String scenario() { return scenario; }
        List<String> range() { return RANGES.getOrDefault(scenario, List.of()); }
        boolean inFlight() { return inFlight.get() > 0; }
        int totalCalls() { return calls.values().stream().mapToInt(AtomicInteger::get).sum(); }
        Map<String, Integer> callsInRange() {
            Map<String, Integer> result = new LinkedHashMap<>();
            range().forEach(date -> Optional.ofNullable(calls.get(date)).ifPresent(count -> result.put(date, count.get())));
            return result;
        }
        Map<String, Integer> callsForResume() {
            Map<String, Integer> result = new LinkedHashMap<>();
            RANGES.get("resume").forEach(date -> Optional.ofNullable(calls.get(date)).ifPresent(count -> result.put(date, count.get())));
            return result;
        }

        @Override
        public PluginDescriptor descriptor() {
            ApiDescriptor api = new ApiDescriptor(KEY.apiName(), "Prices", "test", QueryMode.snapshot, List.of());
            return new PluginDescriptor(KEY.pluginId(), "HTTP lifecycle test", "Controlled lifecycle source",
                    true, true, true, null, List.of(api), List.of(KEY));
        }

        @Override public PluginReadiness readiness() { return new PluginReadiness(true, true, true, null); }

        @Override
        public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName api) {
            return Optional.of(new BatchDownloadDescriptor(List.of(endpoint("start_date", "end_date"),
                    endpoint("end_date", "start_date")), "start_date", "end_date",
                    BatchDownloadDescriptor.DateAxis.CALENDAR_DATE, "Calendar date",
                    BatchDownloadDescriptor.PlanningMode.CALENDAR_DAYS, false,
                    BatchDownloadDescriptor.Availability.AVAILABLE, null, "http-test-v1",
                    new BatchDownloadDescriptor.CompletenessRule(
                            BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE, null,
                            "Controlled lifecycle complete source")));
        }

        @Override
        public List<DateRange> plan(ApiName api, Map<String, Object> params, BatchCallContext context) {
            LocalDate start = LocalDate.parse((String) params.get("start_date"), BASIC);
            LocalDate end = LocalDate.parse((String) params.get("end_date"), BASIC);
            return start.datesUntil(end.plusDays(1)).map(day -> new DateRange(day, day)).toList();
        }

        @Override
        public Map<String, Object> sourceParameters(ApiName api, Map<String, Object> params, DateRange range) {
            return Map.of("start_date", range.start().format(BASIC), "end_date", range.end().format(BASIC));
        }

        @Override
        public DownloadEnvelope downloadBatch(ApiName api, Map<String, Object> params, BatchCallContext context) {
            context.beforeRequest();
            String value = (String) params.getOrDefault("start_date", "single");
            String date = value.equals("single") ? value : LocalDate.parse(value, BASIC).toString();
            calls.computeIfAbsent(date, ignored -> new AtomicInteger()).incrementAndGet();
            inFlight.incrementAndGet();
            try {
                CountDownLatch gate = gates.get(date);
                if (gate != null && !gate.await(20, TimeUnit.SECONDS))
                    throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "controlled-lifecycle-canary-timeout");
                if ("partial".equals(scenario) && "2026-09-11".equals(date) && calls.get(date).get() == 1)
                    throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "controlled-lifecycle-canary-response");
                return new DownloadEnvelope(KEY.pluginId(), api, params, List.of("value"), 1,
                        List.of(List.of(value)), DownloadStatus.SUCCESS, null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "controlled-lifecycle-canary-interrupted");
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override public BatchAssessment assess(ApiName api, DateRange range, DownloadEnvelope envelope) {
            return BatchAssessment.COMPLETE;
        }
        @Override public DownloadEnvelope download(ApiName api, Map<String, Object> params) {
            String value = (String) params.getOrDefault("start_date", "single");
            calls.computeIfAbsent(value.equals("single") ? value : LocalDate.parse(value, BASIC).toString(),
                    ignored -> new AtomicInteger()).incrementAndGet();
            return new DownloadEnvelope(KEY.pluginId(), api, params, List.of("value"), 1,
                    List.of(List.of(value)), DownloadStatus.SUCCESS, null);
        }

        DatasetDefinition definition() {
            return new DatasetDefinition(KEY, "Prices", "test", QueryMode.snapshot, List.of(), TableName.from(KEY),
                    List.of(new ColumnDefinition("value", "Value", LogicalType.STRING, false, 0, 64,
                            null, null, List.of(), false)),
                    new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("value")), List.of(), null, 100);
        }

        private static ParameterDescriptor endpoint(String name, String related) {
            return new ParameterDescriptor(name, name, null, ParameterType.DATE_RANGE_MEMBER, true,
                    null, List.of(), null, related);
        }
    }
}
