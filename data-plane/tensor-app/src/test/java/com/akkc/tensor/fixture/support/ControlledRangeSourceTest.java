package com.akkc.tensor.fixture.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ControlledRangeSourceTest {
    @TempDir Path directory;

    @Test void numericDecimalsSurviveScriptFileAndRealHttpResponse() throws Exception {
        Path script = directory.resolve("scenario.json");
        Files.writeString(script, """
                {"batchRules":[{"apiName":"fixture_daily","params":{"scenario":"SUCCESS"},"page":1,
                  "response":{"fields":["amount"],"data":[[12345678901234567890.123456789012345678]],
                    "totalRows":1,"nextPage":null,"complete":true}}]}
                """);
        try (var source = new ControlledRangeSource(ControlledRangeSource.readScript(script));
                var http = HttpClient.newHttpClient()) {
            var response = http.send(HttpRequest.newBuilder(source.url().resolve("/batch"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"apiName":"fixture_daily","params":{"scenario":"SUCCESS"},"page":1}
                            """)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var json = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(response.body());
            assertThat(json.path("data").get(0).get(0).decimalValue().toPlainString())
                    .isEqualTo("12345678901234567890.123456789012345678");
            assertThat(source.calls()).hasSize(1);
        }
    }
}
