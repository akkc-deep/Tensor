package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.tushare.TusharePluginConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
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
    private static final List<DatasetDefinition> DEFINITIONS =
            new TusharePluginConfiguration().tushareDatasetDefinitions();
    private static final PluginId PLUGIN = PluginId.of("tushare_pro");

    private static final Set<String> NON_STOCK_APIS = Set.of(
            "trade_cal", "new_share", "repurchase", "margin", "slb_len", "index_classify");

    @org.junit.jupiter.api.Test
    void bindsTheDeclaredParameterTypeBeforeTheControllerRuns() throws Exception {
        try (Flow flow = flow(true, true)) {
            var request = flow.mapper().readValue(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":\"20260905\"}"),
                    com.akkc.tensor.web.dto.DownloadRequest.class);
            assertThat(request.params().getClass().getSimpleName()).isEqualTo("TradeDateParameters");
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

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "top_inst", "broker_recommend", "share_float", "hs_const", "moneyflow_hsgt",
        "hk_hold", "index_member", "hsgt_top10", "namechange"
    })
    void rejectsRetiredApisAtTheHttpBoundary(String apiName) throws Exception {
        preservesErrorPriorityAndFieldsWithoutOperationEvents(
                request(apiName, "{}"), "DATASET_MISCONFIGURED", "[]", true, true);
    }

    @org.junit.jupiter.api.Test
    void retainsLastValueForDuplicatesBeforeTheRecordCanBeCreated() throws Exception {
        try (Flow flow = flow(true, true)) {
            var response = flow.mvc().perform(post("/api/v1/downloads")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"apiName\":\"income\",\"apiName\":\"daily\",\"pluginId\":\"tushare_pro\",\"params\":{\"ts_code\":\"000001.SZ\",\"trade_date\":\"20260905\"}}"))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(flow.mapper().readTree(response.getContentAsString()).path("apiName").asText()).isEqualTo("daily");
        }
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                invalid("{\"params\":[],\"pluginId\":\"tushare_pro\",\"apiName\":\"daily\",\"params\":{\"ts_code\":\"000001.SZ\",\"trade_date\":\"20260905\"}}",
                        "PARAM_INVALID", "request:has invalid value", true, true),
                invalid("{}", "PARAM_REQUIRED", "apiName:is required,params:is required,pluginId:is required", true, true),
                invalid("{\"pluginId\":\"\",\"params\":{}}", "PARAM_INVALID", "apiName:is required,pluginId:has invalid value", true, true),
                invalid("{\"pluginId\":\"Invalid\",\"apiName\":\"daily\",\"params\":null}", "PARAM_INVALID", "params:is required,pluginId:has invalid value", true, true),
                invalid("{\"pluginId\":\"Invalid\",\"params\":[]}", "PARAM_INVALID", "request:has invalid value", true, true),
                invalid(request("daily", "{}"), "PARAM_REQUIRED", "ts_code:is required,trade_date:is required", true, true),
                invalid(request("daily", "{\"ann_date\":123}"), "PARAM_REQUIRED", "ts_code:is required,trade_date:is required", true, true),
                invalid(request("income", "{\"ts_code\":123}"), "PARAM_REQUIRED", "ann_date:is required", true, true),
                invalid(request("income", "{\"ts_code\":123,\"ann_date\":\"invalid\",\"extra\":1}"), "PARAM_INVALID", "extra:is not declared,ts_code:has invalid value,ann_date:has invalid value", true, true),
                invalid(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":123}"), "PARAM_INVALID", "trade_date:has invalid value", true, true),
                invalid(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":true}"), "PARAM_INVALID", "trade_date:has invalid value", true, true),
                invalid(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":[]}"), "PARAM_INVALID", "trade_date:has invalid value", true, true),
                invalid(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":{}}"), "PARAM_INVALID", "trade_date:has invalid value", true, true),
                invalid(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":null,\"unknown\":1}"), "PARAM_REQUIRED", "trade_date:is required", true, true),
                invalid(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":\"20260230\",\"bad-field\":1}"), "PARAM_INVALID", "params:contains an invalid field name,trade_date:has invalid value", true, true),
                invalid(request("new_share", "{\"start_date\":\"20260907\",\"end_date\":\"20260906\"}"), "PARAM_INVALID", "start_date:must not be after end_date", true, true),
                invalid(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":123}"), "PLUGIN_DISABLED", "", false, true),
                invalid(request("missing_api", "{\"ts_code\":\"000001.SZ\",\"trade_date\":123}"), "DATASET_MISCONFIGURED", "", true, true),
                invalid(request("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":123}"), "DATASET_MISCONFIGURED", "", true, false));
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
            assertThat(flow.completed()).singleElement().asString().contains("paramSummary=" + summary);
        }
    }

    static Stream<Arguments> validRequests() {
        return Stream.of(
                Arguments.of("daily", "{\"ts_code\":\"000001.SZ\",\"trade_date\":\"20260905\"}", "[ts_code, trade_date]"),
                Arguments.of("index_classify", "{}", "[]"));
    }

    @ParameterizedTest(name = "current request: {0}")
    @MethodSource("currentRequests")
    void forwardsEveryCurrentRequestWithNormalizedParameters(String api, ObjectNode example) throws Exception {
        try (Flow flow = flow(true, true)) {
            ObjectNode request = example.deepCopy();
            if (!NON_STOCK_APIS.contains(api)) {
                ((ObjectNode) request.path("params")).put("ts_code", " 000001.sz ");
            }
            var response = flow.mvc().perform(post("/api/v1/downloads")
                    .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(flow.mapper().readTree(response.getContentAsString()).path("outcome").asText())
                    .isEqualTo("EMPTY");
            assertThat(flow.upstream()).containsExactly(example);
            verifyNoInteractions(flow.persistence());
        }
    }

    @ParameterizedTest(name = "stock required and single-valued: {0}")
    @MethodSource("stockRequests")
    void rejectsMissingBlankAndInvalidStockBeforeUpstreamOrPersistence(String api, ObjectNode example)
            throws Exception {
        try (Flow flow = flow(true, true)) {
            // null Java value means omitted; JSON null is tested independently.
            String[] values = {null, "null", "\"\"", "\"   \"", "123", "true", "[]", "{}",
                    "\"invalid\"", "\"000001.SZ,600000.SH\""};
            for (int index = 0; index < values.length; index++) {
                ObjectNode request = example.deepCopy();
                ObjectNode params = (ObjectNode) request.path("params");
                if (values[index] == null) params.remove("ts_code");
                else params.set("ts_code", flow.mapper().readTree(values[index]));
                var response = flow.mvc().perform(post("/api/v1/downloads")
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                        .andReturn().getResponse();
                JsonNode error = flow.mapper().readTree(response.getContentAsString());
                assertThat(response.getStatus()).as("%s stock %s", api, values[index]).isEqualTo(400);
                assertThat(error.path("code").asText()).isEqualTo(index < 4 ? "PARAM_REQUIRED" : "PARAM_INVALID");
                assertThat(error.path("fieldErrors")).hasSize(1);
                assertThat(error.path("fieldErrors").get(0).path("field").asText()).isEqualTo("ts_code");
            }
            assertThat(flow.upstream()).isEmpty();
            verifyNoInteractions(flow.persistence());
        }
    }

    @ParameterizedTest(name = "undeclared stock: {0}")
    @MethodSource("nonStockRequests")
    void rejectsExtraStockForTheSixOriginalDownloadShapes(String api, ObjectNode example) throws Exception {
        try (Flow flow = flow(true, true)) {
            ObjectNode request = example.deepCopy();
            ((ObjectNode) request.path("params")).put("ts_code", "000001.SZ");
            var response = flow.mvc().perform(post("/api/v1/downloads")
                    .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(400);
            JsonNode error = flow.mapper().readTree(response.getContentAsString());
            assertThat(error.path("code").asText()).isEqualTo("PARAM_INVALID");
            assertThat(error.path("fieldErrors")).isEqualTo(flow.mapper().readTree(
                    "[{\"field\":\"ts_code\",\"message\":\"is not declared\"}]"));
            assertThat(flow.upstream()).isEmpty();
            verifyNoInteractions(flow.persistence());
        }
    }

    static Stream<Arguments> currentRequests() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("docs/data-template/manifest.json"))) {
            root = root.getParent();
        }
        assertThat(root).as("repository root").isNotNull();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode requests = mapper.readTree(root.resolve("docs/contracts/download-request-examples.json")
                .toFile()).path("requests");
        List<String> names = new ArrayList<>();
        List<Arguments> arguments = new ArrayList<>();
        for (JsonNode request : requests) {
            String api = request.path("apiName").asText();
            assertThat(request.path("pluginId").asText()).isEqualTo("tushare_pro");
            assertThat(request.path("params").has("ts_code")).isEqualTo(!NON_STOCK_APIS.contains(api));
            names.add(api);
            arguments.add(Arguments.of(api, (ObjectNode) request));
        }
        assertThat(names).hasSize(40).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(
                DEFINITIONS.stream().map(definition -> definition.datasetKey().apiName().value()).toList());
        List<String> manifestOrder = new ArrayList<>();
        mapper.readTree(root.resolve("docs/data-template/manifest.json").toFile()).path("interfaces")
                .forEach(item -> manifestOrder.add(item.path("api_name").asText()));
        assertThat(names).containsExactlyElementsOf(manifestOrder);
        return arguments.stream();
    }

    static Stream<Arguments> stockRequests() throws Exception {
        return currentRequests().filter(value -> !NON_STOCK_APIS.contains((String) value.get()[0]));
    }

    static Stream<Arguments> nonStockRequests() throws Exception {
        return currentRequests().filter(value -> NON_STOCK_APIS.contains((String) value.get()[0]));
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

    private static Flow flow(boolean available, boolean hasAdapters) {
        List<JsonNode> upstream = new ArrayList<>();
        PersistenceService persistence = mock(PersistenceService.class);
        DataSourcePlugin plugin = new DataSourcePlugin() {
            public PluginDescriptor descriptor() {
                return new PluginDescriptor(PLUGIN, "Test", "Test", true, available, available,
                        available ? null : "Unavailable", DEFINITIONS.stream().map(definition ->
                        new ApiDescriptor(definition.datasetKey().apiName(), definition.displayName(),
                                definition.category(), definition.queryMode(), definition.parameters())).toList(),
                        DEFINITIONS.stream().map(DatasetDefinition::datasetKey).toList());
            }
            public PluginReadiness readiness() {
                return new PluginReadiness(true, available, available, available ? null : "Unavailable");
            }
            public DownloadEnvelope download(ApiName api, Map<String, Object> params) {
                upstream.add(new ObjectMapper().valueToTree(Map.of(
                        "pluginId", PLUGIN.value(), "apiName", api.value(), "params", params)));
                return new DownloadEnvelope(PLUGIN, api, params, List.of("ts_code"), 0, List.of(), DownloadStatus.SUCCESS, null);
            }
        };
        PluginRegistry plugins = new PluginRegistry(List.of(plugin));
        AdapterRegistry adapters = new AdapterRegistry(hasAdapters ? DEFINITIONS.stream()
                .<DatasetAdapter>map(definition -> new GenericDatasetAdapter(
                        definition, new ValueConverter(), new FingerprintKeyCodec())).toList() : List.of());
        ParameterValidator validator = new ParameterValidator();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationLogger operations = new OperationLogger(plugins, new TensorMetrics(registry, plugins));
        DownloadService service = new DownloadService(plugins, adapters, validator,
                persistence, Clock.systemUTC());
        DownloadParameterResolver resolver = new DownloadParameterResolver(
                new DownloadDescriptorResolver(plugins, adapters), validator);
        ObjectMapper mapper = new ObjectMapper().registerModule(new DownloadBindingConfiguration()
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
        return new Flow(mvc, mapper, beanValidator, registry, logger, appender, upstream, persistence);
    }

    private record Flow(MockMvc mvc, ObjectMapper mapper, LocalValidatorFactoryBean validator,
            SimpleMeterRegistry registry, Logger logger, ListAppender<ILoggingEvent> appender,
            List<JsonNode> upstream, PersistenceService persistence) implements AutoCloseable {
        List<String> completed() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.startsWith("tensor.operation.completed")).toList();
        }
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            validator.close();
            registry.close();
        }
    }
}
