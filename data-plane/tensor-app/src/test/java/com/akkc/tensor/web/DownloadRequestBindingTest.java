package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.akkc.tensor.config.DownloadBindingConfiguration;
import com.akkc.tensor.web.download.DownloadDescriptorResolver;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.download.DownloadRequestDeserializer;
import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.download.BatchCommitService;
import com.akkc.tensor.core.download.DownloadExecutionSlot;
import com.akkc.tensor.core.retry.RetryTaskStorageService;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.observability.TensorMetrics;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.tushare.TusharePluginConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DownloadRequestBindingTest {
    private static final Map<ApiName, com.akkc.tensor.plugin.api.download.DownloadPolicy> POLICIES = new TusharePluginConfiguration().tushareDownloadPolicies();
    private static final List<DatasetDefinition> DEFINITIONS =
            new TusharePluginConfiguration().tushareDatasetDefinitions();
    private static final PluginId PLUGIN = PluginId.of("tushare_pro");

    @org.junit.jupiter.api.Test
    void rejectsLegacyDateAndRoutesRangesToInitialExecution() throws Exception {
        try (Flow flow = flow(true, true, true)) {
            var legacy = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                    .content(request("daily", "{\"trade_date\":\"20260905\"}"))).andReturn().getResponse();
            assertThat(legacy.getStatus()).isEqualTo(400);
            assertThat(flow.mapper().readTree(legacy.getContentAsString()).path("code").asText()).isEqualTo("PARAM_INVALID");
            var range = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                    .content(request("daily", "{\"start_date\":\"20260905\",\"end_date\":\"20260905\"}"))).andReturn().getResponse();
            // Real initial execution reaches the unconfirmed calendar; the legacy entry rejects these dates.
            assertThat(range.getStatus()).isEqualTo(502);
            assertThat(flow.mapper().readTree(range.getContentAsString()).path("code").asText()).isEqualTo("CALENDAR_UNCONFIRMED");
        }
    }

    @org.junit.jupiter.api.Test
    void bindsTheDeclaredParameterTypeBeforeTheControllerRuns() throws Exception {
        try (Flow flow = flow(true, true)) {
            var request = flow.mapper().readValue(request("daily", "{\"start_date\":\"20260905\",\"end_date\":\"20260905\"}"),
                    com.akkc.tensor.web.dto.DownloadRequest.class);
            assertThat(request.params().getClass().getSimpleName()).isEqualTo("DateRangeParameters");
        }
    }

    // Changing binding order must not replace an earlier required/access error with a type error.
    @ParameterizedTest
    @MethodSource("invalidRequests")
    void preservesErrorPriorityAndFieldsWithoutOperationEvents(
            String body, String code, String fields, boolean available, boolean adapters)
            throws Exception {
        try (Flow flow = flow(available, adapters)) {
            var response = flow.mvc().perform(post("/api/v1/downloads")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            JsonNode error = flow.mapper().readTree(response.getContentAsString());
            assertThat(response.getStatus()).isEqualTo(code.startsWith("PARAM_") ? 400 : 409);
            assertThat(error.path("code").asText()).isEqualTo(code);
            assertThat(error.path("fieldErrors")).isEqualTo(flow.mapper().readTree(fields));
            assertThat(flow.completed()).isEmpty();
            assertThat(flow.registry().getMeters()).isEmpty();
        }
    }

    @org.junit.jupiter.api.Test
    void rejectsDuplicateOuterAndParameterFieldsBeforeExecution() throws Exception {
        try (Flow flow = flow(true, true)) {
            var response = flow.mvc().perform(post("/api/v1/downloads")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"apiName\":\"income\",\"apiName\":\"daily\",\"pluginId\":\"tushare_pro\",\"params\":{\"start_date\":\"20260905\",\"end_date\":\"20260905\"}}"))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(flow.mapper().readTree(response.getContentAsString()).path("code").asText()).isEqualTo("PARAM_INVALID");
            var duplicateParam = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                    .content(request("daily", "{\"start_date\":\"20260901\",\"start_date\":\"20260902\",\"end_date\":\"20260903\"}"))).andReturn().getResponse();
            assertThat(duplicateParam.getStatus()).isEqualTo(400);
            org.mockito.Mockito.verifyNoInteractions(flow.service());

        }
    }

    @org.junit.jupiter.api.Test
    void rejectsUnknownOuterFieldsWithProductionSpringMapperDefaults() throws Exception {
        try (Flow flow = flow(true, true)) {
            assertThat(flow.mapper().isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
            for (String unknown : List.of("\"unknown\":\"OUTER_BODY_SECRET\"", "\"unknown\":{\"nested\":true}", "\"unknown\":null")) {
                for (boolean first : List.of(false, true)) {
                    String fields = "\"pluginId\":\"tushare_pro\",\"apiName\":\"daily\",\"params\":{\"start_date\":\"20260901\",\"end_date\":\"20260903\"}";
                    String body = "{" + (first ? unknown + "," + fields : fields + "," + unknown) + "}";
                    var response = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                            .content(body)).andReturn().getResponse();
                    assertThat(response.getStatus()).isEqualTo(400);
                    var error = flow.mapper().readTree(response.getContentAsString());
                    assertThat(error.path("code").asText()).isEqualTo("PARAM_INVALID");
                    assertThat(error.path("fieldErrors")).isEqualTo(flow.mapper().readTree("[{\"field\":\"request\",\"message\":\"has invalid value\"}]"));
                    assertThat(error.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
                    assertThat(response.getContentAsString()).doesNotContain("OUTER_BODY_SECRET", "unknown", "nested");
                }
            }
            org.mockito.Mockito.verifyNoInteractions(flow.service());
            assertThat(flow.mapper().isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        }
    }

    @org.junit.jupiter.api.Test
    void rejectsRawIdentifierTypesBeforeRegistryChecksWithSpringMapper() throws Exception {
        try (Flow flow = flow(true, true)) {
            // Keep unrelated Jackson scalar coercion enabled; only DownloadRequest rejects it.
            assertThat(flow.mapper().readValue("true", String.class)).isEqualTo("true");
            assertThat(flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                    .content(request("daily", "{\"start_date\":\"20260901\",\"end_date\":\"20260903\"}")))
                    .andReturn().getResponse().getStatus()).isEqualTo(200);
            org.mockito.Mockito.clearInvocations(flow.service());
            for (String field : List.of("pluginId", "apiName")) {
                for (Object value : List.of(true, false, 123, 1.5, Map.of("secret", "IDENTIFIER_BODY_SECRET"), List.of("IDENTIFIER_BODY_SECRET"))) {
                    for (boolean missing : List.of(false, true)) {
                        var input = new java.util.LinkedHashMap<String, Object>();
                        input.put("pluginId", "tushare_pro"); input.put("apiName", "daily");
                        input.put("params", Map.of("start_date", "20260901", "end_date", "20260903"));
                        input.put(field, value);
                        var expected = new java.util.TreeMap<String, String>(); expected.put(field, "has invalid value");
                        if (missing) {
                            String other = field.equals("pluginId") ? "apiName" : "pluginId";
                            input.remove(other); input.remove("params");
                            expected.put(other, "is required"); expected.put("params", "is required");
                        }
                        var response = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                                .content(flow.mapper().writeValueAsString(input))).andReturn().getResponse();
                        assertThat(response.getStatus()).as(field + ":" + value.getClass().getSimpleName()).isEqualTo(400);
                        var error = flow.mapper().readTree(response.getContentAsString());
                        assertThat(error.path("code").asText()).isEqualTo("PARAM_INVALID");
                        assertThat(error.path("fieldErrors")).isEqualTo(flow.mapper().valueToTree(expected.entrySet().stream()
                                .map(entry -> Map.of("field", entry.getKey(), "message", entry.getValue())).toList()));
                        assertThat(error.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
                        assertThat(error.has("downloadResult")).isFalse();
                        assertThat(response.getContentAsString()).doesNotContain("IDENTIFIER_BODY_SECRET");
                    }
                }
            }
            org.mockito.Mockito.verifyNoInteractions(flow.service());
            assertThat(flow.mapper().readValue("false", String.class)).isEqualTo("false");
        }
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                invalid("{\"params\":[],\"pluginId\":\"tushare_pro\",\"apiName\":\"daily\",\"params\":{\"trade_date\":\"20260905\"}}",
                        "PARAM_INVALID", "request:has invalid value", true, true),
                invalid("{}", "PARAM_REQUIRED", "apiName:is required,params:is required,pluginId:is required", true, true),
                invalid("{\"pluginId\":\"\",\"params\":{}}", "PARAM_INVALID", "apiName:is required,pluginId:has invalid value", true, true),
                invalid("{\"pluginId\":\"Invalid\",\"apiName\":\"daily\",\"params\":null}", "PARAM_INVALID", "params:is required,pluginId:has invalid value", true, true),
                invalid("{\"pluginId\":\"Invalid\",\"params\":[]}", "PARAM_INVALID", "request:has invalid value", true, true),
                invalid(request("daily", "{}"), "PARAM_REQUIRED", "start_date:is required,end_date:is required", true, true),
                invalid(request("daily", "{\"ann_date\":123}"), "PARAM_INVALID", "ann_date:is no longer accepted", true, true),
                invalid(request("income", "{\"ts_code\":123}"), "PARAM_REQUIRED", "start_date:is required,end_date:is required", true, true),
                invalid(request("income", "{\"ts_code\":123,\"start_date\":\"invalid\",\"end_date\":\"20260905\",\"extra\":1}"), "PARAM_INVALID", "extra:is not declared,ts_code:has invalid value,start_date:has invalid value", true, true),
                invalid(request("daily", "{\"trade_date\":123}"), "PARAM_INVALID", "trade_date:is no longer accepted", true, true),
                invalid(request("daily", "{\"trade_date\":true}"), "PARAM_INVALID", "trade_date:is no longer accepted", true, true),
                invalid(request("daily", "{\"trade_date\":[]}"), "PARAM_INVALID", "trade_date:is no longer accepted", true, true),
                invalid(request("daily", "{\"trade_date\":{}}"), "PARAM_INVALID", "trade_date:is no longer accepted", true, true),
                invalid(request("daily", "{\"trade_date\":null,\"unknown\":1}"), "PARAM_INVALID", "trade_date:is no longer accepted", true, true),
                invalid(request("daily", "{\"start_date\":\"20260230\",\"end_date\":\"20260301\",\"bad-field\":1}"), "PARAM_INVALID", "params:contains an invalid field name,start_date:has invalid value", true, true),
                invalid(request("namechange", "{\"start_date\":\"20260907\",\"end_date\":\"20260906\"}"), "PARAM_INVALID", "start_date:must not be after end_date", true, true),
                invalid(request("daily", "{\"trade_date\":123}"), "PLUGIN_DISABLED", "", false, true),
                invalid(request("missing_api", "{\"trade_date\":123}"), "DATASET_MISCONFIGURED", "", true, true),
                invalid(request("daily", "{\"trade_date\":123}"), "DATASET_MISCONFIGURED", "", true, false));
    }

    @ParameterizedTest
    @MethodSource("validRequests")
    void retainsSuccessAndParameterSummary(String api, String params, String summary) throws Exception {
        try (Flow flow = flow(true, true)) {
            var response = flow.mvc().perform(post("/api/v1/downloads")
                    .contentType(MediaType.APPLICATION_JSON).content(request(api, params)))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(flow.mapper().readTree(response.getContentAsString()).path("outcome").asText())
                    .isEqualTo("EMPTY");
            assertThat(flow.completed()).singleElement().asString().contains("outcome=EMPTY");
        }
    }

    static Stream<Arguments> validRequests() {
        return Stream.of(
                Arguments.of("daily", "{\"start_date\":\"20260905\",\"end_date\":\"20260905\"}", "[start_date, end_date]"),
                Arguments.of("broker_recommend", "{\"start_date\":\"20260905\",\"end_date\":\"20260905\"}", "[start_date, end_date]"),
                Arguments.of("trade_cal", "{\"exchange\":\"SSE\",\"start_date\":\"20260101\",\"end_date\":\"20260131\"}", "[exchange, start_date, end_date]"),
                Arguments.of("index_classify", "{}", "[]"));
    }

    @org.junit.jupiter.api.Test
    void realFortyNineApisMatchIndependentOpenApiTargetsAndBindThroughHttp() throws Exception {
        var contract = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
                .readTree(java.nio.file.Path.of("../../docs/contracts/openapi-v1.yaml").toFile());
        var targets = contract.path("x-tensor-range-targets");
        assertThat(targets).hasSize(49);
        var values = Map.of("start_date", "20260901", "end_date", "20260910", "ts_code", " 000001.sz ",
                "exchange", "SSE", "exchange_id", "SSE", "list_status", "L", "hs_type", "SH");
        var modes = new java.util.HashMap<String, Integer>();
        var shapes = new java.util.HashSet<String>();
        try (Flow flow = flow(true, true)) {
            for (var target : targets) {
                String name = target.path("apiName").asText(), shape = target.path("paramsSchema").asText();
                if (shape.startsWith("#/")) shape = shape.substring(shape.lastIndexOf('/') + 1);
                shapes.add(shape);
                var schema = contract.path("components").path("schemas").path(shape);
                var raw = new java.util.LinkedHashMap<String, Object>();
                schema.path("properties").fieldNames().forEachRemaining(field -> raw.put(field, values.get(field)));
                var response = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                        .content(request(name, flow.mapper().writeValueAsString(raw)))).andReturn().getResponse();
                assertThat(response.getStatus()).as(name + ": " + response.getContentAsString()).isEqualTo(200);
                var normalized = new java.util.LinkedHashMap<>(raw);
                if (normalized.containsKey("ts_code")) normalized.put("ts_code", "000001.SZ");
                org.mockito.Mockito.verify(flow.service()).executeInitial(org.mockito.ArgumentMatchers.eq(PLUGIN), org.mockito.ArgumentMatchers.eq(ApiName.of(name)),
                        org.mockito.ArgumentMatchers.eq(normalized), org.mockito.ArgumentMatchers.any());
                var definition = DEFINITIONS.stream().filter(d -> d.datasetKey().apiName().value().equals(name)).findFirst().orElseThrow();
                var policy = POLICIES.get(ApiName.of(name));
                var api = new ApiDescriptor(ApiName.of(name), definition.displayName(), definition.category(), definition.queryMode(),
                        com.akkc.tensor.plugin.api.download.DownloadParameterProjection.project(definition.parameters(), policy), policy, definition.parameters());
                var metadata = flow.mapper().valueToTree(com.akkc.tensor.web.dto.ApiDescriptorResponse.from(api));
                var publicPolicy = metadata.path("downloadPolicy");
                assertThat(RangeResponseContractTest.names(publicPolicy)).containsExactlyInAnyOrder("mode", "dateSemantic", "description", "calendarProfile", "limits");
                assertThat(publicPolicy.path("mode").asText()).isEqualTo(target.path("mode").asText());
                assertThat(publicPolicy.path("calendarProfile")).isEqualTo(target.path("calendarProfile"));
                modes.merge(publicPolicy.path("mode").asText(), 1, Integer::sum);
                assertThat(api.parameters().stream().map(p -> p.name())).containsExactlyElementsOf(raw.keySet());
                boolean range = !target.path("mode").asText().equals("ORIGINAL_PARAMS");
                if (range) {
                    assertThat(publicPolicy.path("limits").path("maxRangeDays").intValue()).isEqualTo(31);
                    var dates = api.parameters().stream().filter(p -> p.name().endsWith("_date")).toList();
                    assertThat(dates).hasSize(2).allSatisfy(d -> assertThat(d.type().name()).isEqualTo("DATE_RANGE_MEMBER"));
                    assertThat(dates.get(0).relatedParameter()).isEqualTo("end_date");
                    assertThat(dates.get(1).relatedParameter()).isEqualTo("start_date");
                    for (String old : List.of("trade_date", "ann_date", "month")) {
                        for (boolean mixed : List.of(false, true)) {
                            var rejected = new java.util.LinkedHashMap<String, Object>(mixed ? raw : Map.of()); rejected.put(old, "20260901");
                            var error = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                                    .content(request(name, flow.mapper().writeValueAsString(rejected)))).andReturn().getResponse();
                            assertThat(error.getStatus()).as(name).isEqualTo(400);
                            assertThat(flow.mapper().readTree(error.getContentAsString()).path("code").asText()).isEqualTo("PARAM_INVALID");
                        }
                    }
                } else {
                    assertThat(publicPolicy.path("limits").isNull()).isTrue();
                    assertThat(api.parameters()).isEqualTo(definition.parameters());
                    var dated = new java.util.LinkedHashMap<>(raw); dated.put("start_date", "20260901"); dated.put("end_date", "20260910");
                    assertThat(flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                            .content(request(name, flow.mapper().writeValueAsString(dated)))).andReturn().getResponse().getStatus()).isEqualTo(400);
                }
                assertThat(metadata.toString()).doesNotContain("sourceRequestMode", "sourceParameters", "evidenceRefs", "recoveryPolicy", "completenessPolicy");
            }
        }
        assertThat(modes).containsExactlyInAnyOrderEntriesOf(Map.of("TRADE_DATE_RANGE", 19, "ANN_DATE_RANGE", 15, "MONTH_RANGE", 1, "NATIVE_RANGE", 3, "ORIGINAL_PARAMS", 11));
        assertThat(shapes).hasSize(9);
    }

    @org.junit.jupiter.api.Test
    void httpDateAndStockBoundariesRejectBeforeTheUseCase() throws Exception {
        try (Flow flow = flow(true, true)) {
            for (String[] dates : List.of(new String[] {"20260131", "20260302"}, new String[] {"20240229", "20240229"}, new String[] {"00010101", "00010101"})) {
                var response = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                        .content(request("daily", flow.mapper().writeValueAsString(Map.of("start_date", dates[0], "end_date", dates[1]))))).andReturn().getResponse();
                assertThat(response.getStatus()).isEqualTo(200);
            }
            org.mockito.Mockito.clearInvocations(flow.service());
            var invalid = List.<Map<String, Object>>of(Map.of("start_date", "20260131", "end_date", "20260303"),
                    Map.of("start_date", "00000101", "end_date", "00000101"), Map.of("start_date", "20260229", "end_date", "20260301"),
                    Map.of("start_date", "20260910", "end_date", "20260901"), Map.of("start_date", 20260901, "end_date", "20260910"),
                    Map.of("start_date", List.of("20260901"), "end_date", "20260910"), Map.of("start_date", "20260901", "end_date", "20260910", "unknown", "BODY_SECRET"));
            for (var values : invalid) {
                var response = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON).content(request("daily", flow.mapper().writeValueAsString(values)))).andReturn().getResponse();
                assertThat(response.getStatus()).isEqualTo(400);
                assertThat(flow.mapper().readTree(response.getContentAsString()).path("code").asText()).isEqualTo("PARAM_INVALID");
                assertThat(response.getContentAsString()).doesNotContain("BODY_SECRET");
            }
            for (String endpoint : List.of("start_date", "end_date")) {
                var response = flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON).content(request("daily", flow.mapper().writeValueAsString(Map.of(endpoint, "20260901"))))).andReturn().getResponse();
                assertThat(response.getStatus()).isEqualTo(400);
                assertThat(flow.mapper().readTree(response.getContentAsString()).path("code").asText()).isEqualTo("PARAM_REQUIRED");
            }
            for (String stock : List.of("000001.SZ,600000.SH", "BAD", "TOKEN_SECRET"))
                assertThat(flow.mvc().perform(post("/api/v1/downloads").contentType(MediaType.APPLICATION_JSON)
                        .content(request("income", flow.mapper().writeValueAsString(Map.of("ts_code", stock, "start_date", "20260901", "end_date", "20260910"))))).andReturn().getResponse().getStatus()).isEqualTo(400);
            org.mockito.Mockito.verifyNoInteractions(flow.service());
        }
    }

    private static Arguments invalid(String body, String code, String errors,
            boolean available, boolean adapters) {
        String fields = errors.isEmpty() ? "[]" : Stream.of(errors.split(","))
                .map(value -> value.split(":", 2))
                .map(pair -> "{\"field\":\"" + pair[0] + "\",\"message\":\"" + pair[1] + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return Arguments.of(body, code, fields, available, adapters);
    }

    private static String request(String api, String params) {
        return "{\"pluginId\":\"tushare_pro\",\"apiName\":\"" + api + "\",\"params\":" + params + "}";
    }

    private static Flow flow(boolean available, boolean hasAdapters) { return flow(available, hasAdapters, false); }
    private static Flow flow(boolean available, boolean hasAdapters, boolean realExecution) {
        DataSourcePlugin plugin = new DataSourcePlugin() {
            public PluginDescriptor descriptor() {
                return new PluginDescriptor(PLUGIN, "Test", "Test", true, available, available,
                        available ? null : "Unavailable", DEFINITIONS.stream().map(definition ->
                        new ApiDescriptor(definition.datasetKey().apiName(), definition.displayName(),
                                definition.category(), definition.queryMode(), com.akkc.tensor.plugin.api.download.DownloadParameterProjection.project(definition.parameters(), POLICIES.get(definition.datasetKey().apiName())), POLICIES.get(definition.datasetKey().apiName()), definition.parameters())).toList(),
                        DEFINITIONS.stream().map(DatasetDefinition::datasetKey).toList());
            }
            public PluginReadiness readiness() {
                return new PluginReadiness(true, available, available, available ? null : "Unavailable");
            }
            public FetchResult download(ApiName api, Map<String, Object> params, DownloadContext context) {
                return new FetchResult(new DownloadEnvelope(PLUGIN, api, params, List.of("ts_code"), 0, List.of(), DownloadStatus.SUCCESS, null), List.of());
            }
        };
        PluginRegistry plugins = new PluginRegistry(List.of(plugin));
        AdapterRegistry adapters = new AdapterRegistry(hasAdapters ? DEFINITIONS.stream()
                .<DatasetAdapter>map(definition -> new GenericDatasetAdapter(
                        definition, new ValueConverter(), new FingerprintKeyCodec())).toList() : List.of());
        ParameterValidator validator = new ParameterValidator();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationLogger operations = new OperationLogger(plugins, new TensorMetrics(registry, plugins));
        BatchCommitService commits = mock(BatchCommitService.class);
        RetryTaskStorageService failures = mock(RetryTaskStorageService.class);
        DownloadService service = new DownloadService(plugins, adapters, validator,
                mock(PersistenceService.class), commits, failures, new DownloadExecutionSlot(), Clock.systemUTC());
        if (!realExecution) {
            service = mock(DownloadService.class);
            org.mockito.Mockito.when(service.executeInitial(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(invocation -> new com.akkc.tensor.core.download.DownloadExecutionResult(invocation.getArgument(3),
                            com.akkc.tensor.core.download.DownloadExecutionResult.Outcome.EMPTY, invocation.getArgument(0), invocation.getArgument(1),
                            0, 0, 0, "下载成功，0 条数据", 1, 0, 0L, 0, null, 0L,
                            com.akkc.tensor.core.download.DownloadExecutionResult.FailureRecordStatus.NOT_REQUIRED, List.of(), List.of(), List.of()));
        }
        DownloadParameterResolver resolver = new DownloadParameterResolver(
                new DownloadDescriptorResolver(plugins, adapters), validator);
        ObjectMapper mapper = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        mapper.registerModule(new DownloadBindingConfiguration()
                .downloadRequestJacksonModule(new DownloadRequestDeserializer(resolver)));
        LocalValidatorFactoryBean beanValidator = new LocalValidatorFactoryBean();
        beanValidator.afterPropertiesSet();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DownloadController(service, operations, resolver))
                .setValidator(beanValidator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        Logger logger = (Logger) LoggerFactory.getLogger(OperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new Flow(mvc, mapper, beanValidator, registry, logger, appender, commits, failures, service);
    }

    private record Flow(MockMvc mvc, ObjectMapper mapper, LocalValidatorFactoryBean validator,
            SimpleMeterRegistry registry, Logger logger, ListAppender<ILoggingEvent> appender,
            BatchCommitService commits, RetryTaskStorageService failures, DownloadService service) implements AutoCloseable {
        List<String> completed() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.startsWith("tensor.operation.completed")).toList();
        }
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            validator.close();
            registry.close();
            org.mockito.Mockito.verifyNoInteractions(commits, failures);
        }
    }
}
