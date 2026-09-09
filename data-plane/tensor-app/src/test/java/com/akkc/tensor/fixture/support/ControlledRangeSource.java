package com.akkc.tensor.fixture.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Test-only exact-script HTTP source, also usable by the browser acceptance process. */
public final class ControlledRangeSource implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
    private final List<JsonNode> calls = new CopyOnWriteArrayList<>();
    private final Map<JsonNode, Hold> holds = new ConcurrentHashMap<>();
    private final Map<String, HttpHandler> handlers = new ConcurrentHashMap<>();
    private volatile JsonNode script;
    private Path scriptPath;

    public ControlledRangeSource(JsonNode script) throws IOException { this(script, 0); }
    private ControlledRangeSource(JsonNode script, int port) throws IOException {
        replaceScript(script);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(executor);
        server.createContext("/batch", this::respond);
        server.createContext("/calendar", this::respond);
        server.start();
    }
    public URI url() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
    public static JsonNode readScript(Path path) throws IOException { return JSON.readTree(path.toFile()); }
    public void replaceScript(JsonNode next) {
        if (next == null || !next.isObject()) throw new IllegalArgumentException("Script must be an object");
        for (String section : List.of("batchRules", "calendarRules")) {
            var keys = new HashSet<JsonNode>();
            for (JsonNode rule : next.path(section)) {
                if (!rule.isObject() || !rule.path("apiName").isTextual() || !rule.path("params").isObject()
                        || (section.equals("batchRules") ? !rule.path("page").isInt() || rule.path("page").intValue() < 1 : !rule.path("dates").isArray())
                        || (rule.has("response") == rule.has("errorCode")))
                    throw new IllegalArgumentException("Invalid exact source rule");
                if (!keys.add(key(rule, section.equals("batchRules")))) throw new IllegalArgumentException("Duplicate source rule");
            }
        }
        script = next.deepCopy();
    }
    public List<JsonNode> calls() { return calls.stream().map(n -> n.<JsonNode>deepCopy()).toList(); }
    public void clearCalls() { calls.clear(); }
    public void handler(String path, HttpHandler handler) { handlers.put(path, handler); }
    public Hold hold(String apiName, Map<String,Object> params, int page) {
        Hold hold = new Hold();
        if (holds.putIfAbsent(JSON.valueToTree(Map.of("apiName", apiName, "params", params, "page", page)), hold) != null)
            throw new IllegalArgumentException("Already held");
        return hold;
    }
    private void respond(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405, -1); return; }
            boolean batch = exchange.getRequestURI().getPath().equals("/batch");
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            ObjectNode recorded = request.deepCopy();
            recorded.put("path", exchange.getRequestURI().getPath());
            calls.add(recorded);
            Hold hold = holds.get(key(request, batch));
            if (hold != null) hold.enter();
            HttpHandler handler = handlers.get(exchange.getRequestURI().getPath());
            if (handler != null) { handler.handle(exchange); return; }
            if (scriptPath != null) replaceScript(readScript(scriptPath));
            JsonNode response = JSON.valueToTree(Map.of("errorCode", "SOURCE_UNAVAILABLE"));
            for (JsonNode rule : script.path(batch ? "batchRules" : "calendarRules")) {
                if (key(rule, batch).equals(key(request, batch))) {
                    response = rule.has("response") ? rule.get("response") : JSON.valueToTree(Map.of("errorCode", rule.path("errorCode").asText()));
                    break;
                }
            }
            byte[] bytes = JSON.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally { exchange.close(); }
    }
    private static JsonNode key(JsonNode node, boolean batch) {
        ObjectNode result = JSON.createObjectNode();
        for (String field : List.of("apiName", "params", batch ? "page" : "dates")) result.set(field, node.path(field));
        return result;
    }
    @Override public void close() {
        holds.values().forEach(Hold::release);
        server.stop(0);
        executor.shutdownNow();
    }
    public static final class Hold {
        private final CountDownLatch entered = new CountDownLatch(1), released = new CountDownLatch(1);
        public void awaitEntered(Duration timeout) throws InterruptedException {
            if (!entered.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new AssertionError("Source was not entered");
        }
        public void release() { released.countDown(); }
        private void enter() {
            entered.countDown();
            try {
                if (!released.await(120, TimeUnit.SECONDS)) throw new AssertionError("Source hold was not released");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError("Source interrupted", e); }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: scriptPath port");
        Path path = Path.of(args[0]);
        var source = new ControlledRangeSource(readScript(path), Integer.parseInt(args[1]));
        source.scriptPath = path;
        Runtime.getRuntime().addShutdownHook(new Thread(source::close));
        System.out.println("Controlled range source listening at " + source.url());
        new CountDownLatch(1).await();
    }
}
