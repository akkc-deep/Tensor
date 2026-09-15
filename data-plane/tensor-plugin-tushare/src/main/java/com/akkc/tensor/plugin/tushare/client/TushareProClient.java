package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

public final class TushareProClient {
    private static final ObjectMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private final RestClient restClient;
    private final TushareProperties properties;
    private final TushareRequestGate gate;

    public TushareProClient(RestClient restClient, TushareProperties properties) {
        this(restClient, properties, new TushareRequestGate(
                Objects.requireNonNull(properties, "properties").minRequestInterval(),
                Clock.systemUTC(), System::nanoTime, Thread::sleep));
    }

    TushareProClient(RestClient restClient, TushareProperties properties, TushareRequestGate gate) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    public DownloadEnvelope execute(DatasetDefinition definition, Map<String, Object> params) {
        return execute(definition, params, new BatchCallContext() {
            public Instant deadline() { return Instant.MAX; }
            public boolean stopRequested() { return false; }
            public void beforeRequest() {}
        });
    }

    public DownloadEnvelope execute(DatasetDefinition definition, Map<String, Object> params,
                                    BatchCallContext context) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(context, "context");

        String fields = definition.columns().stream()
                .map(ColumnDefinition::name)
                .collect(Collectors.joining(","));
        TushareRequest request = new TushareRequest(
                definition.datasetKey().apiName().value(),
                properties.token().value(),
                params,
                fields);
        byte[] requestBody = encode(request);
        return gate.execute(context, () -> controlledExchange(definition, params, requestBody, context));
    }

    private DownloadEnvelope controlledExchange(DatasetDefinition definition, Map<String, Object> params,
                                                byte[] requestBody, BatchCallContext context) {
        var future = new FutureTask<>(() -> {
            TushareRequestGate.check(context, gate.clock());
            return exchange(definition, params, requestBody, context);
        });
        Thread io = Thread.ofVirtual().name("tushare-request").start(future);
        DownloadEnvelope result = null;
        Throwable failure = null;
        TensorException controlFailure = null;
        boolean interrupted = false;
        try {
            while (true) {
                TushareRequestGate.check(context, gate.clock());
                Duration remaining = Duration.between(gate.clock().instant(), context.deadline());
                long waitNanos = remaining.compareTo(Duration.ofMillis(100)) < 0
                        ? Math.max(1, remaining.toNanos()) : TimeUnit.MILLISECONDS.toNanos(100);
                try {
                    result = future.get(waitNanos, TimeUnit.NANOSECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    // A completed Future still requires joining the actual I/O thread below.
                }
            }
        } catch (InterruptedException stopped) {
            interrupted = true;
            future.cancel(true);
        } catch (TensorException stopped) {
            controlFailure = stopped;
            future.cancel(true);
        } catch (ExecutionException failed) {
            failure = failed.getCause();
        } finally {
            while (io.isAlive()) {
                try {
                    io.join();
                } catch (InterruptedException stopped) {
                    interrupted = true;
                    future.cancel(true);
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (interrupted) throw TushareRequestGate.failure(ErrorCode.EXECUTION_INTERRUPTED);
        if (controlFailure != null) throw controlFailure;
        TushareRequestGate.check(context, gate.clock());
        if (failure instanceof Error error) throw error;
        if (failure instanceof TensorException classified) throw classified;
        if (failure instanceof ResourceAccessException || failure instanceof IOException) {
            throw TushareErrorClassifier.classifyTransport(failure);
        }
        if (failure != null) throw TushareErrorClassifier.failure(ErrorCode.SOURCE_UNAVAILABLE);
        return result;
    }

    private DownloadEnvelope exchange(DatasetDefinition definition, Map<String, Object> params,
                                      byte[] requestBody, BatchCallContext context) {
        try {
            return restClient.post()
                    .uri("")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .attribute(TushareRestClientFactory.CONTROL_ATTRIBUTE,
                            new TushareRestClientFactory.RequestControl(context, gate.clock()))
                    .body(requestBody)
                    .exchange((outboundRequest, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw TushareErrorClassifier.classifyHttp(response.getStatusCode().value());
                        }
                        byte[] responseBody = read(response.getBody(), properties.maxResponseBytes());
                        return TushareResponseValidator.validate(definition, params, decode(responseBody));
                    });
        } catch (ResourceAccessException failure) {
            throw TushareErrorClassifier.classifyTransport(failure);
        }
    }

    private static byte[] encode(TushareRequest request) {
        try {
            return JSON.writeValueAsBytes(request);
        } catch (Exception ignored) {
            throw new IllegalStateException("Tushare request cannot be encoded");
        }
    }

    private static byte[] read(InputStream input, int maxResponseBytes) {
        if (input == null) {
            return new byte[0];
        }
        try {
            byte[] body = input.readNBytes(maxResponseBytes + 1);
            if (body.length > maxResponseBytes) {
                throw TushareErrorClassifier.invalidPayload();
            }
            return body;
        } catch (IOException failure) {
            throw TushareErrorClassifier.classifyTransport(failure);
        }
    }

    private static TushareResponse decode(byte[] body) {
        try {
            return JSON.readValue(body, TushareResponse.class);
        } catch (Exception ignored) {
            throw TushareErrorClassifier.invalidPayload();
        }
    }
}
