package com.akkc.tensor.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.fixture.support.ControlledRangeSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real packaged JVM and real lost HTTP connections; SQL is read on independent connections. */
class RangeProcessIT {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Path JAR = Path.of("target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar").toAbsolutePath();
    private static final Path EVIDENCE = Path.of("../../.superpowers/sdd/RANGE-T18-design/process").toAbsolutePath();
    private static final String TASK = "tensor_download_task", ITEM = "tensor_download_task_item";
    private static final String BUSINESS = "fixture__fixture_daily";
    private static final Duration WAIT = Duration.ofSeconds(45);
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
            .withDatabaseName("tensor_range_process").withUsername("tensor").withPassword("tensor")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs");
    private static JdbcTemplate sql;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final List<Map<String, Object>> events = new ArrayList<>();
    private ControlledRangeSource source;
    private Process child;
    private int port, generation, httpNumber;
    private Path directory;
    private List<Map<String, Object>> schema;

    @BeforeAll static void database() throws Exception {
        assertThat(JAR).as("Build the current acceptance JAR before explicit ProcessIT").isRegularFile();
        Path classpath = Path.of("target/range-acceptance/test-classpath.txt");
        Files.createDirectories(classpath.getParent());
        Files.writeString(classpath, System.getProperty("java.class.path"));
        MYSQL.start();
        sql = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    @AfterAll static void stopDatabase() { MYSQL.stop(); }

    @BeforeEach void start(TestInfo info) throws Exception {
        directory = EVIDENCE.resolve(info.getTestMethod().orElseThrow().getName() + "-" + UUID.randomUUID());
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("jar.sha256"), HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))) + "\n");
        source = new ControlledRangeSource(script("initial"));
        startChild();
        sql.update("DELETE FROM " + ITEM);
        sql.update("DELETE FROM " + TASK);
        sql.update("DELETE FROM " + BUSINESS);
        schema = schema();
        snapshot("start-empty");
        assertThat(count(TASK)).isZero();
        assertThat(count(ITEM)).isZero();
    }

    @AfterEach void cleanup() throws Exception {
        try {
            if (child != null && child.isAlive()) killChild();
            if (source != null) {
                snapshot("final");
                assertThat(schema()).isEqualTo(schema);
                source.close();
                event("source-closed");
            }
        } finally {
            if (source != null) source.close();
            if (child != null && child.isAlive()) { child.destroyForcibly(); child.waitFor(20, TimeUnit.SECONDS); }
            write("events.json", events);
            for (int i = 1; i <= generation; i++) {
                String log = Files.readString(directory.resolve("child-" + i + ".log"));
                assertThat(log).doesNotContain("RANGE_T18_SECRET_CANARY", "Authorization: Bearer");
            }
        }
    }

    @Test void suddenExitPreservesOnlySavedFailureAndRestartDoesNotResume() throws Exception {
        var hold = source.hold("fixture_daily", params("20260905"), 1);
        CompletableFuture<HttpResponse<String>> request = sendAsync("/api/v1/downloads", body(1, 10));
        hold.awaitEntered(WAIT);
        assertThat(businessDates()).containsExactly("2026-09-01", "2026-09-02", "2026-09-04");
        assertThat(failedDates()).containsExactly("2026-09-03");
        String id = taskId();
        JsonNode original = taskHeader();
        snapshot("before-kill-at-five");
        killChild();
        hold.release();
        await("client observes killed server", request::isDone);
        assertThat(sourceDates()).containsExactly("20260901", "20260902", "20260903", "20260904", "20260905");
        source.replaceScript(script("success"));
        source.clearCalls();
        startChild();
        JsonNode detail = get("/api/v1/retry-tasks/" + id, 200);
        assertThat(detail.path("failedItemCount").asLong()).isEqualTo(1);
        assertOriginalRange(detail, 1, 10);
        assertThat(source.calls()).isEmpty();
        assertThat(taskHeader()).isEqualTo(original);
        snapshot("restarted-read-only");
        JsonNode result = post("/api/v1/retry-tasks/" + id + "/execute", "", 200);
        assertThat(result.path("completedUnits").asLong()).isEqualTo(1);
        assertThat(sourceDates()).containsExactly("20260903");
        assertThat(businessDates()).containsExactly("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04");
        assertThat(count(TASK)).isZero();
        assertThat(count(ITEM)).isZero();
        post("/api/v1/retry-tasks/" + id + "/execute", "", 404);
        snapshot("explicit-retry-only-three");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void initialDisconnectKeepsSlotUntilActualExecutionEnds(boolean readTimeout) throws Exception {
        var hold = source.hold("fixture_daily", params("20260905"), 1);
        try (Socket socket = openPost("/api/v1/downloads", body(1, 10))) {
            hold.awaitEntered(WAIT);
            String id = taskId();
            disconnect(socket, readTimeout);
            assertBusy(id, false);
            snapshot("disconnected-initial-held");
            hold.release();
            await("initial independent SQL completion", () -> count(BUSINESS) == 8 && count(ITEM) == 2);
            awaitIdle(id);
            assertThat(sourceDates()).containsExactly("20260901", "20260902", "20260903", "20260904", "20260905",
                    "20260906", "20260907", "20260908", "20260909", "20260910");
            assertThat(failedDates()).containsExactly("2026-09-03", "2026-09-07");
            assertOriginalRange(get("/api/v1/retry-tasks/" + id, 200), 1, 10);
            snapshot("disconnected-initial-completed");
            post("/api/v1/downloads", body(1, 1), 200);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lastRetryDisconnectDeletesAtomicallyAndNeverReinserts(boolean readTimeout) throws Exception {
        JsonNode first = post("/api/v1/downloads", body(3, 3), 200);
        String id = first.path("taskId").asText();
        assertThat(count(ITEM)).isEqualTo(1);
        source.replaceScript(script("success"));
        source.clearCalls();
        var hold = source.hold("fixture_daily", params("20260903"), 1);
        try (Socket socket = openPost("/api/v1/retry-tasks/" + id + "/execute", "")) {
            hold.awaitEntered(WAIT);
            disconnect(socket, readTimeout);
            assertBusy(id, true);
            assertThat(count(BUSINESS)).isZero();
            assertThat(count(ITEM)).isEqualTo(1);
            snapshot("disconnected-retry-held");
            hold.release();
            await("last item atomically committed", () -> count(BUSINESS) == 1 && count(TASK) == 0 && count(ITEM) == 0);
            await("last retry slot released", () -> status("/api/v1/retry-tasks/" + id + "/execute", "") == 404);
            assertThat(sourceDates()).containsExactly("20260903");
            post("/api/v1/retry-tasks/" + id + "/execute", "", 404);
            get("/api/v1/retry-tasks/" + id, 404);
            assertThat(count(TASK)).isZero();
            assertThat(count(ITEM)).isZero();
            snapshot("last-retry-response-lost");
            post("/api/v1/downloads", body(1, 1), 200);
        }
    }

    @Test void suddenExitDuringRetryDoesNotRestoreAlreadyDeletedItem() throws Exception {
        String id = post("/api/v1/downloads", body(1, 10), 200).path("taskId").asText();
        assertThat(failedDates()).containsExactly("2026-09-03", "2026-09-07");
        JsonNode original = taskHeader();
        source.replaceScript(script("success"));
        source.clearCalls();
        var hold = source.hold("fixture_daily", params("20260907"), 1);
        var response = sendAsync("/api/v1/retry-tasks/" + id + "/execute", "");
        hold.awaitEntered(WAIT);
        assertThat(failedDates()).containsExactly("2026-09-07");
        assertThat(count(BUSINESS)).isEqualTo(9);
        snapshot("retry-three-committed-seven-held");
        killChild();
        hold.release();
        await("retry client observes killed process", response::isDone);
        assertThat(sourceDates()).containsExactly("20260903", "20260907");
        source.clearCalls();
        startChild();
        JsonNode detail = get("/api/v1/retry-tasks/" + id, 200);
        assertOriginalRange(detail, 1, 10);
        assertThat(detail.path("items").get(0).path("timeValue").asText()).isEqualTo("2026-09-07");
        assertThat(taskHeader()).isEqualTo(original);
        assertThat(source.calls()).isEmpty();
        snapshot("retry-restart-seven-only");
        JsonNode result = post("/api/v1/retry-tasks/" + id + "/execute", "", 200);
        assertThat(result.path("completedUnits").asLong()).isEqualTo(1);
        assertThat(result.path("insertedRows").asLong()).isEqualTo(1);
        assertThat(sourceDates()).containsExactly("20260907");
        assertThat(count(BUSINESS)).isEqualTo(10);
        assertThat(count(TASK)).isZero();
        assertThat(count(ITEM)).isZero();
        snapshot("seven-explicitly-completed");
    }

    @Test void sourceSecretsNeverEnterPackagedHttpTaskStorageOrChildLog() throws Exception {
        source.handler("/batch", exchange -> {
            byte[] payload = "{\"errorCode\":\"SOURCE_RATE_LIMITED\",\"message\":\"RANGE_T18_SECRET_CANARY\",\"token\":\"RANGE_T18_SECRET_CANARY\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Authorization", "Bearer RANGE_T18_SECRET_CANARY");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
        });
        JsonNode result = post("/api/v1/downloads", body(1, 2), 200);
        assertThat(result.path("failedUnits").asLong()).isEqualTo(2);
        assertThat(result.path("outcome").asText()).isEqualTo("FAILED");
        assertThat(sourceDates()).containsExactly("20260901", "20260902");
        assertThat(count(BUSINESS)).isZero();
        assertThat(count(TASK)).isEqualTo(1);
        assertThat(count(ITEM)).isEqualTo(2);
        assertThat(sql.queryForList("SELECT task_params FROM " + TASK).toString()).doesNotContain("RANGE_T18_SECRET_CANARY");
        assertThat(sql.queryForList("SELECT error_message FROM " + ITEM).toString()).doesNotContain("RANGE_T18_SECRET_CANARY");
        get("/api/v1/retry-tasks/" + result.path("taskId").asText(), 200);
        snapshot("source-secret-sanitized");
    }

    private void assertBusy(String id, boolean retrying) throws Exception {
        JsonNode detail = get("/api/v1/retry-tasks/" + id, 200);
        assertThat(detail.path("retrying").asBoolean()).isEqualTo(retrying);
        assertThat(detail.path("canExecute").asBoolean()).isFalse();
        assertThat(detail.path("executionBlocker").path("code").asText()).isEqualTo("DOWNLOAD_BUSY");
        int calls = source.calls().size();
        post("/api/v1/downloads", body(1, 1), 409);
        post("/api/v1/retry-tasks/" + id + "/execute", "", 409);
        get("/api/v1/retry-tasks", 200);
        assertThat(source.calls()).hasSize(calls);
    }

    private void awaitIdle(String id) throws Exception {
        await("slot released after execution", () -> {
            try { return get("/api/v1/retry-tasks/" + id, 200).path("canExecute").asBoolean(); }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }

    private Socket openPost(String path, String body) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "POST " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port
                + "\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
        event("socket-request-written:" + path + ":body-bytes=" + bytes.length);
        return socket;
    }

    private void disconnect(Socket socket, boolean readTimeout) throws Exception {
        if (readTimeout) {
            socket.setSoTimeout(100);
            assertThatThrownBy(() -> socket.getInputStream().read()).isInstanceOf(SocketTimeoutException.class);
            event("client-read-timeout");
        } else {
            socket.setSoLinger(true, 0);
        }
        socket.close();
        event(readTimeout ? "client-close-after-timeout" : "client-RST");
    }

    private void startChild() throws Exception {
        try (ServerSocket reserve = new ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            port = reserve.getLocalPort();
        }
        Path log = directory.resolve("child-" + (++generation) + ".log");
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Xmx512m", "-jar", JAR.toString(), "--spring.profiles.active=acceptance", "--server.address=127.0.0.1",
                "--server.port=" + port, "--tensor.plugins.fixture.enabled=true", "--tensor.plugins.fixture.mode=ANN_DATE_RANGE",
                "--tensor.plugins.fixture.recovery=REQUEST", "--tensor.plugins.fixture.source-url=" + source.url(),
                "--tensor.plugins.tushare-pro.enabled=false");
        builder.environment().remove("TENSOR_TUSHARE_TOKEN");
        builder.environment().put("TENSOR_TUSHARE_ENABLED", "false");
        builder.environment().put("TENSOR_DB_URL", MYSQL.getJdbcUrl());
        builder.environment().put("TENSOR_DB_USERNAME", MYSQL.getUsername());
        builder.environment().put("TENSOR_DB_PASSWORD", MYSQL.getPassword());
        child = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        event("child-started");
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (true) {
            assertThat(child.isAlive()).as("child startup; see %s", log).isTrue();
            try {
                if (http.send(HttpRequest.newBuilder(uri("/api/v1/data-sources/fixture/apis"))
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 200) break;
            } catch (IOException ignored) { /* Startup readiness is actual HTTP, not a fixed sleep. */ }
            if (System.nanoTime() > deadline) throw new AssertionError("Child did not become ready; see " + log);
            Thread.sleep(25);
        }
        event("child-metadata-ready");
    }

    private void killChild() throws Exception {
        event("child-destroyForcibly");
        child.destroyForcibly();
        assertThat(child.waitFor(20, TimeUnit.SECONDS)).isTrue();
        assertThat(child.isAlive()).isFalse();
        event("child-exited:" + child.exitValue());
    }

    private JsonNode get(String path, int expected) throws Exception { return exchange("GET", path, null, expected); }
    private JsonNode post(String path, String body, int expected) throws Exception { return exchange("POST", path, body, expected); }
    private JsonNode exchange(String method, String path, String body, int expected) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).timeout(WAIT);
        if (method.equals("GET")) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        write("http-" + (++httpNumber) + ".json", Map.of("method", method, "path", path,
                "status", response.statusCode(), "body", response.body()));
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(expected);
        assertThat(response.body()).doesNotContain("RANGE_T18_SECRET_CANARY");
        return JSON.readTree(response.body());
    }

    private CompletableFuture<HttpResponse<String>> sendAsync(String path, String body) {
        return http.sendAsync(HttpRequest.newBuilder(uri(path)).timeout(WAIT).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private int status(String path, String body) {
        try { return http.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(3))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).statusCode(); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private static String body(int start, int end) throws Exception {
        return JSON.writeValueAsString(Map.of("pluginId", "fixture", "apiName", "fixture_daily", "params",
                Map.of("scenario", "SUCCESS", "start_date", "202609%02d".formatted(start), "end_date", "202609%02d".formatted(end))));
    }
    private static Map<String, Object> params(String date) { return Map.of("scenario", "SUCCESS", "ann_date", date); }
    private static JsonNode script(String name) throws Exception {
        return ControlledRangeSource.readScript(Path.of("src/test/resources/fixture/range-browser-" + name + ".json"));
    }
    private static void assertOriginalRange(JsonNode detail, int start, int end) {
        assertThat(detail.path("originalDateRange").path("startDate").asText()).isEqualTo("202609%02d".formatted(start));
        assertThat(detail.path("originalDateRange").path("endDate").asText()).isEqualTo("202609%02d".formatted(end));
    }
    private static long count(String table) { return sql.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private static String taskId() { return sql.queryForObject("SELECT task_id FROM " + TASK, String.class); }
    private static List<String> failedDates() { return sql.queryForList("SELECT time_value FROM " + ITEM + " ORDER BY time_value", String.class); }
    private static List<String> businessDates() { return sql.queryForList("SELECT CAST(trade_date AS CHAR) FROM " + BUSINESS + " ORDER BY trade_date", String.class); }
    private List<String> sourceDates() { return source.calls().stream().filter(c -> c.path("path").asText().equals("/batch"))
            .map(c -> c.path("params").path("ann_date").asText()).toList(); }
    private static JsonNode taskHeader() throws Exception {
        var row = sql.queryForMap("SELECT task_id, plugin_id, api_name, task_params, created_at FROM " + TASK);
        row.put("task_params", JSON.readTree(row.get("task_params").toString()));
        row.put("created_at", row.get("created_at").toString());
        return JSON.valueToTree(row);
    }
    private static List<Map<String, Object>> schema() {
        var result = new ArrayList<Map<String, Object>>();
        for (String table : List.of(BUSINESS, TASK, ITEM)) {
            result.addAll(sql.queryForList("SHOW CREATE TABLE " + table));
            result.addAll(sql.queryForList("SHOW INDEX FROM " + table).stream().map(row -> {
                row.remove("Cardinality"); // Optimizer estimates change with row counts; index definitions must not.
                return row;
            }).toList());
        }
        return result;
    }
    private void snapshot(String name) throws Exception {
        write(name + ".json", Map.of("sourceCalls", source.calls(), "business", sql.queryForList("SELECT * FROM " + BUSINESS + " ORDER BY ts_code, trade_date"),
                "tasks", sql.queryForList("SELECT * FROM " + TASK + " ORDER BY task_id"),
                "items", sql.queryForList("SELECT * FROM " + ITEM + " ORDER BY task_id,target_type,target_value,time_type,time_value"), "schema", schema()));
        event("sql-snapshot:" + name);
    }
    private void write(String name, Object value) throws Exception { JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(name).toFile(), value); }
    private void event(String name) { events.add(Map.of("event", name, "time", Instant.now().toString(), "pid", child == null ? -1 : child.pid(), "port", port)); }
    private void await(String label, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError(label + "; evidence: " + directory);
            Thread.sleep(25);
        }
        event(label);
    }
}
