package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public final class TushareProClient {
    private static final ObjectMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private final RestClient restClient;
    private final TushareProperties properties;

    public TushareProClient(RestClient restClient, TushareProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public DownloadEnvelope execute(DatasetDefinition definition, Map<String, Object> params) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(params, "params");

        String fields = definition.columns().stream()
                .map(ColumnDefinition::name)
                .collect(Collectors.joining(","));
        TushareRequest request = new TushareRequest(
                definition.datasetKey().apiName().value(),
                properties.token().value(),
                params,
                fields);
        byte[] requestBody = encode(request);

        return restClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange((outboundRequest, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Tushare HTTP request failed");
                    }
                    byte[] responseBody = read(response.getBody(), properties.maxResponseBytes());
                    return TushareResponseValidator.validate(definition, params, decode(responseBody));
                });
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
                throw new IllegalStateException("Tushare response exceeds maxResponseBytes");
            }
            return body;
        } catch (IOException ignored) {
            throw new IllegalStateException("Tushare response cannot be read");
        }
    }

    private static TushareResponse decode(byte[] body) {
        try {
            return JSON.readValue(body, TushareResponse.class);
        } catch (Exception ignored) {
            throw new IllegalStateException("Tushare response is invalid JSON");
        }
    }
}
