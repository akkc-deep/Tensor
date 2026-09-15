package com.akkc.tensor.plugin.tushare.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

class TushareProClientControlTest {
    private static final DatasetDefinition DAILY = new DatasetDefinitionLoader()
            .loadAll(new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml")
            .stream().filter(value -> value.datasetKey().apiName().value().equals("daily")).findFirst().orElseThrow();
    private static final Map<String, Object> PARAMS = Map.of("ts_code", "000001.SZ", "trade_date", "20260902");
    private static final byte[] EMPTY = ("{\"code\":0,\"data\":{\"fields\":["
            + String.join(",", DAILY.columns().stream().map(column -> "\"" + column.name() + "\"").toList())
            + "],\"items\":[]}}").getBytes(StandardCharsets.UTF_8);

    @Test
    void budgetDenialMakesNoRequestAndPreservesClassifiedFailure() {
        var requests = new AtomicInteger();
        var context = new Context();
        var denied = new TensorException(ErrorCode.TASK_LIMIT_EXCEEDED, "Download task limit exceeded") {};
        context.onReserve = () -> { throw denied; };
        var client = client(fake(() -> {
            requests.incrementAndGet();
            return response(200, new ByteArrayInputStream(EMPTY), () -> {});
        }), new Time());
        assertThat(catchThrowable(() -> client.execute(DAILY, PARAMS, context))).isSameAs(denied);
        assertThat(context.reservations.get()).isEqualTo(1);
        assertThat(requests.get()).isZero();
    }

    @Test
    void oldAndContextCallsShareGateAndWaitForResponseClose() {
        var time = new Time();
        var starts = new ArrayList<Long>();
        var closed = new AtomicInteger();
        var rest = fake(() -> {
            starts.add(time.millis());
            return response(200, new ByteArrayInputStream(EMPTY), () -> {
                closed.incrementAndGet();
                time.advance(Duration.ofMillis(20));
            });
        });
        var client = client(rest, time);
        var context = new Context();
        assertThat(client.execute(DAILY, PARAMS).rowCount()).isZero();
        assertThat(client.execute(DAILY, PARAMS, context).params()).isEqualTo(PARAMS);
        assertThat(client.execute(DAILY, PARAMS).rowCount()).isZero();
        assertThat(starts).containsExactly(0L, 1520L, 3040L);
        assertThat(closed.get()).isEqualTo(3);
        assertThat(context.reservations.get()).isEqualTo(1);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsLateSuccessAndLateIoFailureAfterActualExit(boolean ioFailure) {
        var time = new Time();
        var context = new Context();
        context.deadline = Instant.EPOCH.plusSeconds(1);
        var closed = new AtomicBoolean();
        var client = client(fake(() -> {
            time.advance(Duration.ofSeconds(1));
            if (ioFailure) {
                closed.set(true);
                throw new IOException("secret transport detail");
            }
            return response(200, new ByteArrayInputStream(EMPTY), () -> closed.set(true));
        }), time);
        assertCode(catchThrowable(() -> client.execute(DAILY, PARAMS, context)), ErrorCode.TASK_LIMIT_EXCEEDED);
        assertThat(closed.get()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"headers,stop", "headers,deadline", "body,stop", "body,deadline",
            "close,stop", "close,deadline", "headers,read", "body,read", "close,read"})
    void productionTransportStopsHeadersBodyAndClose(String phase, String reason) throws Exception {
        var arrived = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var requests = new AtomicInteger();
        var ioThread = new AtomicReference<Thread>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var serverThreads = Executors.newVirtualThreadPerTaskExecutor();
             var caller = Executors.newSingleThreadExecutor()) {
            server.setExecutor(serverThreads);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                try {
                    exchange.getRequestBody().readAllBytes();
                    if (!phase.equals("headers")) {
                        exchange.sendResponseHeaders(phase.equals("close") ? 503 : 200, 1_000_000);
                        exchange.getResponseBody().write('{');
                        exchange.getResponseBody().flush();
                    }
                    arrived.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("server release timed out");
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            var time = new Time();
            var context = new Context();
            context.deadline = Instant.EPOCH.plusSeconds(5);
            var props = properties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    reason.equals("read") ? Duration.ofMillis(400) : Duration.ofSeconds(5));
            var rest = new TushareRestClientFactory().create(props).mutate()
                    .requestInitializer(request -> ioThread.set(Thread.currentThread())).build();
            var client = new TushareProClient(rest, props,
                    new TushareRequestGate(Duration.ZERO, time, () -> time.nanos, time::advance));
            Future<Throwable> result = caller.submit(() -> catchThrowable(() -> client.execute(DAILY, PARAMS, context)));
            try {
                assertThat(arrived.await(3, TimeUnit.SECONDS)).isTrue();
                if (reason.equals("stop")) context.stop.set(true);
                if (reason.equals("deadline")) time.advance(Duration.ofSeconds(5));
                ErrorCode code = switch (reason) {
                    case "stop" -> ErrorCode.EXECUTION_INTERRUPTED;
                    case "deadline" -> ErrorCode.TASK_LIMIT_EXCEEDED;
                    default -> ErrorCode.SOURCE_TIMEOUT;
                };
                assertCode(result.get(3, TimeUnit.SECONDS), code);
                assertThat(ioThread.get().isAlive()).isFalse();
                assertThat(ioThread.get().isVirtual()).isTrue();
                assertThat(requests.get()).isEqualTo(1);
                assertThat(context.reservations.get()).isEqualTo(1);
                assertThat(caller.submit(() -> Thread.currentThread().isInterrupted()).get(1, TimeUnit.SECONDS)).isFalse();
            } finally {
                release.countDown();
                context.stop.set(true);
                result.cancel(true);
                server.stop(0);
            }
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationKeepsGateUntilInterruptIgnoringIoActuallyExits(boolean transientStop) throws Exception {
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var reserved = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var ioThread = new AtomicReference<Thread>();
        var time = new Time();
        var context = new Context();
        var next = new Context();
        next.onReserve = reserved::countDown;
        var client = client(fake(() -> {
            if (attempts.incrementAndGet() == 1) {
                ioThread.set(Thread.currentThread());
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException expected) {
                    interrupted.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("release timed out");
                    } catch (InterruptedException again) {
                        throw new IOException("repeated interrupt");
                    }
                }
            }
            return response(200, new ByteArrayInputStream(EMPTY), () -> {});
        }), time);
        try (var callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> catchThrowable(() -> client.execute(DAILY, PARAMS, context)));
            Future<?> second = null;
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                context.stop.set(true);
                assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
                second = callers.submit(() -> client.execute(DAILY, PARAMS, next));
                assertThat(reserved.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(first.isDone()).isFalse();
                assertThat(second.isDone()).isFalse();
                assertThat(attempts.get()).isEqualTo(1);
                if (transientStop) {
                    context.stop.set(false);
                    context.deadline = time.instant();
                }
                release.countDown();
                assertCode(first.get(2, TimeUnit.SECONDS), ErrorCode.EXECUTION_INTERRUPTED);
                assertThat(ioThread.get().isAlive()).isFalse();
                second.get(2, TimeUnit.SECONDS);
                assertThat(attempts.get()).isEqualTo(2);
            } finally {
                release.countDown();
                first.cancel(true);
                if (second != null) second.cancel(true);
            }
        }
    }

    @Test
    void externalInterruptIsRestoredAfterIoExit() throws Exception {
        var entered = new CountDownLatch(1);
        var callerThread = new AtomicReference<Thread>();
        var ioThread = new AtomicReference<Thread>();
        var interrupted = new AtomicBoolean();
        var client = client(fake(() -> {
            ioThread.set(Thread.currentThread());
            entered.countDown();
            try {
                new CountDownLatch(1).await(5, TimeUnit.SECONDS);
            } catch (InterruptedException stopped) {
                throw new IOException("secret interrupt detail");
            }
            return response(200, new ByteArrayInputStream(EMPTY), () -> {});
        }), new Time());
        try (var caller = Executors.newSingleThreadExecutor()) {
            var result = caller.submit(() -> {
                callerThread.set(Thread.currentThread());
                var failure = catchThrowable(() -> client.execute(DAILY, PARAMS, new Context()));
                interrupted.set(Thread.currentThread().isInterrupted());
                return failure;
            });
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                callerThread.get().interrupt();
                assertCode(result.get(2, TimeUnit.SECONDS), ErrorCode.EXECUTION_INTERRUPTED);
                assertThat(interrupted.get()).isTrue();
                assertThat(ioThread.get().isAlive()).isFalse();
            } finally {
                result.cancel(true);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"401,SOURCE_AUTH_FAILED", "403,SOURCE_PERMISSION_DENIED", "429,SOURCE_RATE_LIMITED",
            "503,SOURCE_UNAVAILABLE", "200,SOURCE_PAYLOAD_INVALID"})
    void contextPathRetainsSafeHttpAndPayloadClassificationAndReleasesGate(int status, ErrorCode code) {
        var attempts = new AtomicInteger();
        var context = new Context();
        var client = client(fake(() -> {
            boolean first = attempts.incrementAndGet() == 1;
            return response(first ? status : 200,
                    new ByteArrayInputStream(first ? "{secret".getBytes(StandardCharsets.UTF_8) : EMPTY), () -> {});
        }), new Time());
        assertCode(catchThrowable(() -> client.execute(DAILY, PARAMS, context)), code);
        assertThat(context.reservations.get()).isEqualTo(1);
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(client.execute(DAILY, PARAMS, context).rowCount()).isZero();
        assertThat(context.reservations.get()).isEqualTo(2);
    }

    @Test
    void unexpectedRuntimeFailureIsSafeAndErrorsAreNotConvertedToBusinessFailures() {
        var runtime = client(fake(() -> { throw new IllegalStateException("secret address"); }), new Time());
        assertCode(catchThrowable(() -> runtime.execute(DAILY, PARAMS, new Context())), ErrorCode.SOURCE_UNAVAILABLE);
        var error = new AssertionError("controlled error");
        var fatal = client(fake(() -> { throw error; }), new Time());
        assertThat(catchThrowable(() -> fatal.execute(DAILY, PARAMS, new Context()))).isSameAs(error);
    }

    @Test
    void validatesInputsAndEncodesBeforeReserving() {
        var attempts = new AtomicInteger();
        var client = client(fake(() -> {
            attempts.incrementAndGet();
            return response(200, new ByteArrayInputStream(EMPTY), () -> {});
        }), new Time());
        var context = new Context();
        assertThat(catchThrowable(() -> client.execute(null, PARAMS, context)))
                .isInstanceOf(NullPointerException.class).hasMessage("definition");
        assertThat(catchThrowable(() -> client.execute(DAILY, null, context)))
                .isInstanceOf(NullPointerException.class).hasMessage("params");
        assertThat(catchThrowable(() -> client.execute(DAILY, PARAMS, null)))
                .isInstanceOf(NullPointerException.class).hasMessage("context");
        assertThat(catchThrowable(() -> client.execute(DAILY, Map.of("unsupported", new Object()), context)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Tushare request cannot be encoded").hasNoCause();
        assertThat(context.reservations.get()).isZero();
        assertThat(attempts.get()).isZero();
    }

    private static TushareProClient client(RestClient rest, Time time) {
        var props = properties(URI.create("https://synthetic.invalid"), Duration.ofSeconds(5));
        return new TushareProClient(rest, props,
                new TushareRequestGate(Duration.ofMillis(1500), time, () -> time.nanos, time::advance));
    }

    private static TushareProperties properties(URI uri, Duration timeout) {
        return new TushareProperties(true, uri, new TushareProperties.Credential("synthetic-secret"),
                Duration.ofSeconds(1), timeout, 1_048_576);
    }

    private static void assertCode(Throwable failure, ErrorCode code) {
        assertThat(failure).isInstanceOf(TensorException.class);
        assertThat(((TensorException) failure).code()).isEqualTo(code);
        assertThat(failure.getCause()).isNull();
        assertThat(failure.getSuppressed()).isEmpty();
        assertThat(failure.toString()).doesNotContain("synthetic", "secret", "http:", "IOException");
    }

    @FunctionalInterface
    private interface Exchange { ClientHttpResponse run() throws IOException; }

    private static RestClient fake(Exchange exchange) {
        return RestClient.builder().baseUrl("https://synthetic.invalid")
                .requestFactory((uri, method) -> new AbstractClientHttpRequest() {
                    public HttpMethod getMethod() { return method; }
                    public URI getURI() { return uri; }
                    protected OutputStream getBodyInternal(HttpHeaders headers) { return new ByteArrayOutputStream(); }
                    protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                        return exchange.run();
                    }
                }).build();
    }

    private static ClientHttpResponse response(int status, InputStream body, Runnable close) {
        return new ClientHttpResponse() {
            public HttpStatus getStatusCode() { return HttpStatus.valueOf(status); }
            public String getStatusText() { return ""; }
            public HttpHeaders getHeaders() { return new HttpHeaders(); }
            public InputStream getBody() { return body; }
            public void close() { close.run(); }
        };
    }

    private static final class Context implements BatchCallContext {
        volatile Instant deadline = Instant.MAX;
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicInteger reservations = new AtomicInteger();
        Runnable onReserve = () -> {};
        public Instant deadline() { return deadline; }
        public boolean stopRequested() { return stop.get(); }
        public void beforeRequest() { reservations.incrementAndGet(); onReserve.run(); }
    }

    private static final class Time extends Clock {
        volatile long nanos;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.EPOCH.plusNanos(nanos); }
        void advance(Duration duration) { nanos += duration.toNanos(); }
    }
}
