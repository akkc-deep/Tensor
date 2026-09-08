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
    void bindsTheDeclaredParameterTypeBeforeTheControllerRuns() throws Exception {
        try (Flow flow = flow(true, true)) {
            var request = flow.mapper().readValue(request("daily", "{\"trade_date\":\"20260905\"}"),
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

    @org.junit.jupiter.api.Test
    void retainsLastValueForDuplicatesBeforeTheRecordCanBeCreated() throws Exception {
        try (Flow flow = flow(true, true)) {
            var response = flow.mvc().perform(post("/api/v1/downloads")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"apiName\":\"income\",\"apiName\":\"daily\",\"pluginId\":\"tushare_pro\",\"params\":{\"trade_date\":\"20260905\"}}"))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(flow.mapper().readTree(response.getContentAsString()).path("apiName").asText()).isEqualTo("daily");
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
                invalid(request("daily", "{}"), "PARAM_REQUIRED", "trade_date:is required", true, true),
                invalid(request("daily", "{\"ann_date\":123}"), "PARAM_REQUIRED", "trade_date:is required", true, true),
                invalid(request("income", "{\"ts_code\":123}"), "PARAM_REQUIRED", "ann_date:is required", true, true),
                invalid(request("income", "{\"ts_code\":123,\"ann_date\":\"invalid\",\"extra\":1}"), "PARAM_INVALID", "extra:is not declared,ts_code:has invalid value,ann_date:has invalid value", true, true),
                invalid(request("daily", "{\"trade_date\":123}"), "PARAM_INVALID", "trade_date:has invalid value", true, true),
                invalid(request("daily", "{\"trade_date\":true}"), "PARAM_INVALID", "trade_date:has invalid value", true, true),
                invalid(request("daily", "{\"trade_date\":[]}"), "PARAM_INVALID", "trade_date:has invalid value", true, true),
                invalid(request("daily", "{\"trade_date\":{}}"), "PARAM_INVALID", "trade_date:has invalid value", true, true),
                invalid(request("daily", "{\"trade_date\":null,\"unknown\":1}"), "PARAM_REQUIRED", "trade_date:is required", true, true),
                invalid(request("daily", "{\"trade_date\":\"20260230\",\"bad-field\":1}"), "PARAM_INVALID", "params:contains an invalid field name,trade_date:has invalid value", true, true),
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
            assertThat(flow.completed()).singleElement().asString().contains("paramSummary=" + summary);
        }
    }

    static Stream<Arguments> validRequests() {
        return Stream.of(
                Arguments.of("daily", "{\"trade_date\":\"20260905\"}", "[trade_date]"),
                Arguments.of("broker_recommend", "{\"month\":\"202609\"}", "[month]"),
                Arguments.of("trade_cal", "{\"exchange\":\"SSE\",\"start_date\":\"20260101\",\"end_date\":\"20260303\"}", "[exchange, start_date, end_date]"),
                Arguments.of("index_classify", "{}", "[]"));
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
        DownloadService service = new DownloadService(plugins, adapters, validator,
                mock(PersistenceService.class), Clock.systemUTC());
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
        return new Flow(mvc, mapper, beanValidator, registry, logger, appender);
    }

    private record Flow(MockMvc mvc, ObjectMapper mapper, LocalValidatorFactoryBean validator,
            SimpleMeterRegistry registry, Logger logger, ListAppender<ILoggingEvent> appender) implements AutoCloseable {
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
