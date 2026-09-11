package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.plugin.tushare.TushareProPlugin;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class StockScopedDownloadTest {
    private static final String BASE_URL = "https://stock-scope.invalid";
    private static final String TOKEN = "stock-scope-test-token";
    private static final PluginId PLUGIN_ID = PluginId.of("tushare_pro");
    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<DatasetDefinition> DEFINITIONS = new DatasetDefinitionLoader().loadAll(
            new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml");

    @Test
    void rejectsMixedStockBatchesForAll34DefinitionsBeforePersistence() {
        Flow flow = flow();
        List<DatasetDefinition> stockDefinitions = DEFINITIONS.stream()
                .filter(StockScopedDownloadTest::isStockScoped)
                .toList();

        assertThat(stockDefinitions).hasSize(34);
        for (DatasetDefinition definition : stockDefinitions) {
            Map<String, Object> params = parameters(definition, "000001.SZ");
            expect(flow, definition, params, response(definition,
                    List.of(row(definition, "000001.SZ"), row(definition, "600000.SH"))));
        }

        for (DatasetDefinition definition : stockDefinitions) {
            Map<String, Object> params = parameters(definition, "000001.SZ");
            assertThatThrownBy(() -> flow.service().execute(
                            PLUGIN_ID, definition.datasetKey().apiName(), params, RequestId.newId()))
                    .isInstanceOfSatisfying(SourceException.class, failure -> assertThat(failure.code())
                            .as(definition.datasetKey().apiName().value())
                            .isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID));
        }

        flow.upstream().verify();
        verifyNoInteractions(flow.persistence());
    }

    @Test
    void persistsOnlyTheRequestedStockForTwoIndependentDailyDownloads() {
        Flow flow = flow();
        DatasetDefinition daily = definition("daily");

        for (String code : List.of("000001.SZ", "600000.SH")) {
            Map<String, Object> params = Map.of("ts_code", code, "trade_date", "20260807");
            List<Object> sourceRow = row(daily, code);
            sourceRow.set(fieldIndex(daily, "trade_date"), "20260807");
            expect(flow, daily, params, response(daily, List.of(sourceRow)));
        }

        for (String code : List.of("000001.SZ", "600000.SH")) {
            Map<String, Object> params = Map.of("ts_code", code, "trade_date", "20260807");
            assertThat(flow.service().execute(
                            PLUGIN_ID, ApiName.of("daily"), params, RequestId.newId()).outcome())
                    .isEqualTo(DownloadOutcome.SUCCESS);
        }

        ArgumentCaptor<AdaptedBatch> batches = ArgumentCaptor.forClass(AdaptedBatch.class);
        verify(flow.persistence(), times(2)).persist(batches.capture());
        assertThat(batches.getAllValues()).extracting(batch -> batch.rows().getFirst().get("ts_code"))
                .containsExactly("000001.SZ", "600000.SH");
        assertThat(batches.getAllValues()).allSatisfy(batch -> assertThat(batch.rows()).singleElement());
        flow.upstream().verify();
    }

    @Test
    void rejectsNullOrMissingDailyResponseCodesWithoutPersistence() {
        Flow flow = flow();
        DatasetDefinition daily = definition("daily");
        Map<String, Object> params = Map.of("ts_code", "000001.SZ", "trade_date", "20260807");

        List<Object> nullCode = row(daily, null);
        nullCode.set(fieldIndex(daily, "trade_date"), "20260807");
        expect(flow, daily, params, response(daily, List.of(nullCode)));

        List<String> missingCodeFields = daily.columns().stream()
                .map(column -> column.name())
                .filter(name -> !name.equals("ts_code"))
                .toList();
        expect(flow, daily, params, response(missingCodeFields,
                List.of(new ArrayList<>(Collections.nCopies(missingCodeFields.size(), null)))));

        assertSourcePayloadRejected(flow, daily, params);
        assertSourcePayloadRejected(flow, daily, params);

        flow.upstream().verify();
        verifyNoInteractions(flow.persistence());
    }

    @Test
    void acceptsMultiStockNewShareAndRepurchaseBatches() {
        Flow flow = flow();
        DatasetDefinition newShare = definition("new_share");
        DatasetDefinition repurchase = definition("repurchase");
        Map<String, Object> newShareParams = Map.of("start_date", "20260801", "end_date", "20260807");
        Map<String, Object> repurchaseParams = Map.of("ann_date", "20260807");

        expect(flow, newShare, newShareParams, response(newShare, List.of(
                List.of("000001.SZ", "001001", "Alpha", "20260801", "20260802",
                        1000, 800, 10.5, 20, 500, 10000, 0.01),
                List.of("600000.SH", "730000", "Beta", "20260803", "20260804",
                        2000, 1600, 11.5, 21, 600, 20000, 0.02))));
        expect(flow, repurchase, repurchaseParams, response(repurchase, List.of(
                List.of("000001.SZ", "20260807", "20260901", "实施", "20260930", 100, 1000, 10, 9),
                List.of("600000.SH", "20260807", "20260902", "完成", "20261001", 200, 2200, 12, 10))));

        assertThat(flow.service().execute(
                        PLUGIN_ID, ApiName.of("new_share"), newShareParams, RequestId.newId()).outcome())
                .isEqualTo(DownloadOutcome.SUCCESS);
        assertThat(flow.service().execute(
                        PLUGIN_ID, ApiName.of("repurchase"), repurchaseParams, RequestId.newId()).outcome())
                .isEqualTo(DownloadOutcome.SUCCESS);

        ArgumentCaptor<AdaptedBatch> batches = ArgumentCaptor.forClass(AdaptedBatch.class);
        verify(flow.persistence(), times(2)).persist(batches.capture());
        assertThat(batches.getAllValues()).allSatisfy(batch -> assertThat(batch.rows())
                .extracting(row -> row.get("ts_code"))
                .containsExactly("000001.SZ", "600000.SH"));
        flow.upstream().verify();
    }

    private static void assertSourcePayloadRejected(
            Flow flow,
            DatasetDefinition definition,
            Map<String, Object> params) {
        assertThatThrownBy(() -> flow.service().execute(
                        PLUGIN_ID, definition.datasetKey().apiName(), params, RequestId.newId()))
                .isInstanceOfSatisfying(SourceException.class, failure -> assertThat(failure.code())
                        .isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID));
    }

    private static Flow flow() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(builder).build();
        TushareProperties properties = new TushareProperties(
                true, URI.create(BASE_URL), new TushareProperties.Credential(TOKEN),
                Duration.ofSeconds(5), Duration.ofSeconds(5), 1_048_576, Duration.ZERO);
        TushareProPlugin plugin = new TushareProPlugin(
                properties, new TushareProClient(builder.build(), properties), DEFINITIONS);
        List<DatasetAdapter> adapters = DEFINITIONS.stream()
                .<DatasetAdapter>map(definition -> new GenericDatasetAdapter(
                        definition, new ValueConverter(), new FingerprintKeyCodec()))
                .toList();
        PersistenceService persistence = mock(PersistenceService.class);
        when(persistence.persist(any())).thenAnswer(invocation -> {
            AdaptedBatch batch = invocation.getArgument(0);
            return new WriteCounts(batch.rows().size(), 0);
        });
        DownloadService service = new DownloadService(
                new PluginRegistry(List.of(plugin)), new AdapterRegistry(adapters),
                new ParameterValidator(), persistence, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Flow(service, persistence, upstream);
    }

    private static void expect(
            Flow flow,
            DatasetDefinition definition,
            Map<String, Object> params,
            String response) {
        flow.upstream().expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(outbound -> assertThat(JSON.readTree(
                        ((MockClientHttpRequest) outbound).getBodyAsBytes()))
                        .isEqualTo(JSON.readTree(request(definition, params))))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private static String request(DatasetDefinition definition, Map<String, Object> params) {
        return json(Map.of(
                "api_name", definition.datasetKey().apiName().value(),
                "token", TOKEN,
                "params", params,
                "fields", String.join(",", definition.columns().stream().map(column -> column.name()).toList())));
    }

    private static String response(DatasetDefinition definition, List<List<Object>> rows) {
        return response(definition.columns().stream().map(column -> column.name()).toList(), rows);
    }

    private static String response(List<String> fields, List<List<Object>> rows) {
        return json(Map.of("code", 0, "data", Map.of("fields", fields, "items", rows)));
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError("test JSON cannot be encoded", exception);
        }
    }

    private static Map<String, Object> parameters(DatasetDefinition definition, String code) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (ParameterDescriptor parameter : definition.parameters()) {
            params.put(parameter.name(), parameterValue(parameter, code));
        }
        return params;
    }

    private static String parameterValue(ParameterDescriptor parameter, String code) {
        if (parameter.name().equals("exchange")) {
            return code.endsWith(".SH") ? "SSE" : "SZSE";
        }
        return switch (parameter.type()) {
            case TS_CODE -> code;
            case DATE, DATE_RANGE_MEMBER -> "20260807";
            case MONTH -> "202608";
            case ENUM -> parameter.allowedValues().getFirst();
            case TEXT -> "test";
        };
    }

    private static List<Object> row(DatasetDefinition definition, String code) {
        List<Object> row = new ArrayList<>(Collections.nCopies(definition.columns().size(), null));
        row.set(fieldIndex(definition, "ts_code"), code);
        return row;
    }

    private static int fieldIndex(DatasetDefinition definition, String field) {
        return definition.columns().stream().map(column -> column.name()).toList().indexOf(field);
    }

    private static boolean isStockScoped(DatasetDefinition definition) {
        return definition.parameters().stream().anyMatch(parameter -> parameter.name().equals("ts_code")
                && parameter.type() == ParameterType.TS_CODE
                && parameter.required());
    }

    private static DatasetDefinition definition(String apiName) {
        return DEFINITIONS.stream()
                .filter(definition -> definition.datasetKey().apiName().value().equals(apiName))
                .findFirst()
                .orElseThrow();
    }

    private record Flow(
            DownloadService service,
            PersistenceService persistence,
            MockRestServiceServer upstream) {}
}
