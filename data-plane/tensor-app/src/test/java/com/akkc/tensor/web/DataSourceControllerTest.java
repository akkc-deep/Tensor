package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import com.akkc.tensor.web.dto.ApiDescriptorResponse;
import com.akkc.tensor.web.dto.DataSourceResponse;
import com.akkc.tensor.web.dto.DatasetDefinitionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DataSourceControllerTest {
    private static final String REQUEST_ID = "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";

    @Mock
    private PluginRegistry pluginRegistry;

    @Mock
    private DatasetCatalog datasetCatalog;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DataSourceController controller = new DataSourceController(pluginRegistry, datasetCatalog);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void listsReadyAndUnavailableDataSourcesWithoutCredentialsOrInternalMetadata() throws Exception {
        PluginDescriptor ready = descriptor("fixture", true, true, true, null, List.of(), List.of());
        PluginDescriptor unavailable = descriptor(
                "tushare_pro", true, false, false, "credential is not configured", List.of(), List.of());
        PluginDescriptor duplicateA = descriptor(
                "zz_duplicate", "Duplicate A", false, false, false, "duplicate plugin id", List.of(), List.of());
        PluginDescriptor duplicateB = descriptor(
                "zz_duplicate", "Duplicate B", false, false, false, "duplicate plugin id", List.of(), List.of());
        org.mockito.Mockito.when(pluginRegistry.descriptors())
                .thenReturn(List.of(ready, unavailable, duplicateA, duplicateB));

        MvcResult result = perform("/api/v1/data-sources", 200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body).hasSize(4);
        assertThat(fieldNames(body.get(0))).containsExactly(
                "pluginId", "displayName", "description", "enabled", "credentialConfigured",
                "downloadAvailable", "unavailableReason");
        assertThat(body.get(0).get("pluginId").asText()).isEqualTo("fixture");
        assertThat(body.get(0).get("unavailableReason").isNull()).isTrue();
        assertThat(body.get(1).get("pluginId").asText()).isEqualTo("tushare_pro");
        assertThat(body.get(1).get("credentialConfigured").asBoolean()).isFalse();
        assertThat(body.get(1).get("unavailableReason").asText()).isEqualTo("credential is not configured");
        assertThat(body.get(2).get("pluginId").asText()).isEqualTo("zz_duplicate");
        assertThat(body.get(2).get("displayName").asText()).isEqualTo("Duplicate A");
        assertThat(body.get(3).get("pluginId").asText()).isEqualTo("zz_duplicate");
        assertThat(body.get(3).get("displayName").asText()).isEqualTo("Duplicate B");
        assertThat(DataSourceResponse.from(ready).pluginId()).isEqualTo("fixture");

        String json = result.getResponse().getContentAsString().toLowerCase();
        assertThat(json).doesNotContain("secret-token", "authorization", "\"apis\"", "\"datasets\"",
                "tablename", "businesskey", "batchsize");
        assertConflict("/api/v1/data-sources/zz_duplicate/apis", ErrorCode.PLUGIN_DISABLED);
    }

    @Test
    void listsFortyNineApisInDescriptorOrderWithExactOptionalParameterFields() throws Exception {
        List<ApiDescriptor> apis = apiDescriptors();
        org.mockito.Mockito.when(pluginRegistry.descriptors()).thenReturn(List.of(
                descriptor("tushare_pro", true, true, true, null, apis, List.of())));

        JsonNode body = body("/api/v1/data-sources/tushare_pro/apis");

        assertThat(body).hasSize(49);
        assertThat(body.get(0).get("apiName").asText()).isEqualTo("daily");
        assertThat(body.get(48).get("apiName").asText()).isEqualTo("api_48");
        assertThat(body.get(0).get("queryMode").asText()).isEqualTo("trade_date");
        assertThat(fieldNames(body.get(0))).containsExactly(
                "apiName", "displayName", "category", "queryMode", "parameters");
        JsonNode enumParameter = body.get(0).get("parameters").get(0);
        assertThat(fieldNames(enumParameter)).containsExactly(
                "name", "label", "type", "required", "description", "defaultValue", "allowedValues", "pattern");
        assertThat(enumParameter.get("type").asText()).isEqualTo("ENUM");
        assertThat(enumParameter.get("allowedValues").toString()).isEqualTo("[\"SSE\",\"SZSE\"]");
        assertThat(fieldNames(body.get(0).get("parameters").get(1)))
                .containsExactly("name", "label", "type", "required");
        assertThat(body.get(1).get("queryMode").asText()).isEqualTo("date_range");
        assertThat(fieldNames(body.get(1).get("parameters").get(0)))
                .containsExactly("name", "label", "type", "required", "relatedParameter");
        assertThat(body.get(1).get("parameters").get(0).get("relatedParameter").asText())
                .isEqualTo("end_date");

        ApiDescriptorResponse response = ApiDescriptorResponse.from(apis.get(0));
        assertThatThrownBy(() -> response.parameters().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        List<ApiDescriptorResponse.ParameterResponse> source = new ArrayList<>(response.parameters());
        ApiDescriptorResponse copy = new ApiDescriptorResponse(
                "daily", "日线行情", "market", QueryMode.trade_date, source);
        source.clear();
        assertThat(copy.parameters()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "disabled", "missing_token"})
    void rejectsUnknownDisabledAndCredentialMissingApiSources(String pluginId) throws Exception {
        PluginDescriptor listed = switch (pluginId) {
            case "disabled" -> descriptor(pluginId, false, false, false, "plugin disabled", List.of(), List.of());
            case "missing_token" -> descriptor(
                    pluginId, true, false, false, "credential is not configured", List.of(), List.of());
            default -> descriptor("other_plugin", true, true, true, null, List.of(), List.of());
        };
        org.mockito.Mockito.when(pluginRegistry.descriptors()).thenReturn(List.of(listed));

        assertConflict("/api/v1/data-sources/" + pluginId + "/apis", ErrorCode.PLUGIN_DISABLED);
    }

    @Test
    void listsSortedDatasetSummariesForAPluginWithoutCredentials() throws Exception {
        org.mockito.Mockito.when(pluginRegistry.descriptors()).thenReturn(List.of(
                descriptor("tushare_pro", true, false, false, "credential is not configured", List.of(), List.of())));
        DatasetDefinition daily = dataset("daily", List.of("trade_date", "ts_code"), null);
        DatasetDefinition weekly = dataset("weekly", List.of("ts_code"), "trade_date");
        org.mockito.Mockito.when(datasetCatalog.list(PluginId.of("tushare_pro")))
                .thenReturn(List.of(daily, weekly));

        JsonNode body = body("/api/v1/data-sources/tushare_pro/datasets");

        assertThat(body).hasSize(2);
        assertThat(body.get(0).get("apiName").asText()).isEqualTo("daily");
        assertThat(body.get(1).get("apiName").asText()).isEqualTo("weekly");
        assertThat(fieldNames(body.get(0))).containsExactly(
                "pluginId", "apiName", "displayName", "category", "queryMode", "filters", "fixedColumn");
        assertThat(body.get(0).get("fixedColumn").asText()).isEqualTo("ts_code");
        assertThat(body.get(0).get("filters").toString()).isEqualTo(
                "[{\"field\":\"trade_date\",\"operator\":\"BETWEEN\",\"controlType\":\"DATE_RANGE\"},"
                        + "{\"field\":\"ts_code\",\"operator\":\"EQ\",\"controlType\":\"TEXT\"}]");
    }

    @Test
    void returnsACompleteDefinitionWithOrderedBusinessColumnsAndNoInternalFields() throws Exception {
        org.mockito.Mockito.when(pluginRegistry.descriptors()).thenReturn(List.of(
                descriptor("tushare_pro", true, false, false, "credential is not configured", List.of(), List.of())));
        DatasetDefinition definition = dataset("daily", List.of("trade_date", "ts_code"), null);
        org.mockito.Mockito.when(datasetCatalog.find(
                DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily"))))
                .thenReturn(Optional.of(definition));

        MvcResult result = perform("/api/v1/data-sources/tushare_pro/datasets/daily", 200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(fieldNames(body)).containsExactly(
                "pluginId", "apiName", "displayName", "category", "queryMode", "filters", "fixedColumn", "columns");
        assertThat(body.get("fixedColumn").asText()).isEqualTo("ts_code");
        assertThat(body.get("filters").get(0).get("field").asText()).isEqualTo("trade_date");
        assertThat(body.get("columns")).hasSize(5);
        assertThat(body.get("columns").get(0).get("name").asText()).isEqualTo("ts_code");
        assertThat(body.get("columns").get(4).get("name").asText()).isEqualTo("name");
        assertThat(fieldNames(body.get("columns").get(1))).containsExactly(
                "name", "label", "logicalType", "nullable", "displayOrder", "longText");
        assertThat(body.get("columns").get(2).get("precision").asInt()).isEqualTo(20);
        assertThat(body.get("columns").get(2).get("scale").asInt()).isEqualTo(4);
        assertThat(body.get("columns").get(3).get("longText").asBoolean()).isTrue();
        assertThat(result.getResponse().getContentAsString().toLowerCase())
                .doesNotContain("source_plugin", "source_api", "ingested_at", "tablename", "businesskey", "batchsize");

        DatasetDefinitionResponse response = DatasetDefinitionResponse.from(definition);
        assertThatThrownBy(() -> response.columns().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        List<DatasetDefinitionResponse.ColumnResponse> source = new ArrayList<>(response.columns());
        DatasetDefinitionResponse copy = new DatasetDefinitionResponse(
                response.pluginId(), response.apiName(), response.displayName(), response.category(),
                response.queryMode(), response.filters(), response.fixedColumn(), source);
        source.clear();
        assertThat(copy.columns()).hasSize(5);

        List<ColumnDefinition> shuffledColumns = List.of(
                definition.columns().get(3),
                definition.columns().get(1),
                definition.columns().get(4),
                definition.columns().get(0),
                definition.columns().get(2));
        DatasetDefinition shuffled = dataset(
                "daily", List.of("trade_date", "ts_code"), null, shuffledColumns);
        DatasetDefinitionResponse shuffledResponse = DatasetDefinitionResponse.from(shuffled);
        assertThat(shuffledResponse.columns().stream()
                .map(DatasetDefinitionResponse.ColumnResponse::name).toList())
                .containsExactly("ts_code", "trade_date", "amount", "note", "name");
        assertThat(shuffledResponse.fixedColumn()).isEqualTo("ts_code");
    }

    @Test
    void rejectsDatasetListForAnUnregisteredPlugin() throws Exception {
        org.mockito.Mockito.when(pluginRegistry.descriptors()).thenReturn(List.of(
                descriptor("other_plugin", true, true, true, null, List.of(), List.of())));

        assertConflict(
                "/api/v1/data-sources/unknown/datasets", ErrorCode.DATASET_MISCONFIGURED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown_plugin", "unknown_dataset", "invalid_filter"})
    void rejectsUnregisteredMissingAndUnsafeDatasetDefinitions(String scenario) throws Exception {
        if (scenario.equals("unknown_plugin")) {
            org.mockito.Mockito.when(pluginRegistry.descriptors()).thenReturn(List.of(
                    descriptor("other_plugin", true, true, true, null, List.of(), List.of())));
        } else {
            org.mockito.Mockito.when(pluginRegistry.descriptors()).thenReturn(List.of(
                    descriptor("tushare_pro", true, false, false,
                            "credential is not configured", List.of(), List.of())));
            Optional<DatasetDefinition> definition = scenario.equals("invalid_filter")
                    ? Optional.of(dataset("daily", List.of("name"), null))
                    : Optional.empty();
            org.mockito.Mockito.when(datasetCatalog.find(
                    DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily"))))
                    .thenReturn(definition);
        }

        String pluginId = scenario.equals("unknown_plugin") ? "unknown" : "tushare_pro";
        assertConflict("/api/v1/data-sources/" + pluginId + "/datasets/daily",
                ErrorCode.DATASET_MISCONFIGURED);
    }

    @Test
    void returnsAnEmptyCatalogForARegisteredDisabledPluginAndHasServletRegistrationGuard() throws Exception {
        org.mockito.Mockito.when(pluginRegistry.descriptors()).thenReturn(List.of(
                descriptor("tushare_pro", false, false, false, "plugin disabled", List.of(), List.of())));
        org.mockito.Mockito.when(datasetCatalog.list(PluginId.of("tushare_pro"))).thenReturn(List.of());

        JsonNode body = body("/api/v1/data-sources/tushare_pro/datasets");

        assertThat(body.isArray()).isTrue();
        assertThat(body).isEmpty();
        ConditionalOnWebApplication conditional =
                DataSourceController.class.getAnnotation(ConditionalOnWebApplication.class);
        assertThat(conditional).isNotNull();
        assertThat(conditional.type()).isEqualTo(ConditionalOnWebApplication.Type.SERVLET);
    }

    private JsonNode body(String path) throws Exception {
        return objectMapper.readTree(perform(path, 200).getResponse().getContentAsString());
    }

    private MvcResult perform(String path, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header(RequestIdFilter.HEADER_NAME, REQUEST_ID)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(REQUEST_ID);
        return result;
    }

    private void assertConflict(String path, ErrorCode code) throws Exception {
        MvcResult result = perform(path, 409);
        assertThat(result.getResolvedException()).isInstanceOf(TensorException.class);
        TensorException exception = (TensorException) result.getResolvedException();
        assertThat(exception.code()).isEqualTo(code);
        assertThat(exception.getMessage()).isEqualTo(
                code == ErrorCode.PLUGIN_DISABLED
                        ? "Plugin metadata is unavailable"
                        : "Dataset metadata is unavailable");
    }

    private static List<String> fieldNames(JsonNode node) {
        return StreamSupport.stream(
                        java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false)
                .toList();
    }

    private static List<ApiDescriptor> apiDescriptors() {
        List<ParameterDescriptor> parameters = List.of(
                new ParameterDescriptor("exchange", "交易所", "交易所代码", ParameterType.ENUM,
                        false, "SSE", List.of("SSE", "SZSE"), "^[A-Z]+$", null),
                new ParameterDescriptor("ts_code", "股票代码", null, ParameterType.TS_CODE,
                        false, null, List.of(), null, null));
        List<ParameterDescriptor> rangeParameters = List.of(
                new ParameterDescriptor("start_date", "开始日期", null, ParameterType.DATE_RANGE_MEMBER,
                        false, null, List.of(), null, "end_date"),
                new ParameterDescriptor("end_date", "结束日期", null, ParameterType.DATE_RANGE_MEMBER,
                        false, null, List.of(), null, "start_date"));
        List<ApiDescriptor> apis = new ArrayList<>();
        apis.add(new ApiDescriptor(ApiName.of("daily"), "日线行情", "market", QueryMode.trade_date, parameters));
        apis.add(new ApiDescriptor(
                ApiName.of("api_01"), "接口 1", "market", QueryMode.date_range, rangeParameters));
        IntStream.rangeClosed(2, 48).forEach(index -> apis.add(new ApiDescriptor(
                ApiName.of("api_%02d".formatted(index)), "接口 " + index, "market", QueryMode.snapshot, List.of())));
        return apis;
    }

    private static DatasetDefinition dataset(String apiName, List<String> filterFields, String fixedColumn) {
        List<ColumnDefinition> columns = List.of(
                new ColumnDefinition("ts_code", "股票代码", LogicalType.STRING,
                        false, 0, 16, null, null, List.of(), false),
                new ColumnDefinition("trade_date", "交易日期", LogicalType.DATE,
                        false, 1, null, null, null, List.of(), false),
                new ColumnDefinition("amount", "成交额", LogicalType.DECIMAL,
                        true, 2, null, 20, 4, List.of(), false),
                new ColumnDefinition("note", "备注", LogicalType.TEXT,
                        true, 3, null, null, null, List.of(), true),
                new ColumnDefinition("name", "名称", LogicalType.STRING,
                        true, 4, 80, null, null, List.of(), false));
        return dataset(apiName, filterFields, fixedColumn, columns);
    }

    private static DatasetDefinition dataset(
            String apiName,
            List<String> filterFields,
            String fixedColumn,
            List<ColumnDefinition> columns) {
        DatasetKey key = DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of(apiName));
        return new DatasetDefinition(
                key,
                apiName + " dataset",
                "market",
                QueryMode.trade_date,
                List.of(),
                TableName.from(key),
                columns,
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                filterFields.stream().map(FilterDefinition::new).toList(),
                fixedColumn);
    }

    private static PluginDescriptor descriptor(
            String pluginId,
            boolean enabled,
            boolean credentialConfigured,
            boolean downloadAvailable,
            String unavailableReason,
            List<ApiDescriptor> apis,
            List<DatasetKey> datasets) {
        return descriptor(
                pluginId,
                pluginId + " display",
                enabled,
                credentialConfigured,
                downloadAvailable,
                unavailableReason,
                apis,
                datasets);
    }

    private static PluginDescriptor descriptor(
            String pluginId,
            String displayName,
            boolean enabled,
            boolean credentialConfigured,
            boolean downloadAvailable,
            String unavailableReason,
            List<ApiDescriptor> apis,
            List<DatasetKey> datasets) {
        return new PluginDescriptor(
                PluginId.of(pluginId),
                displayName,
                pluginId + " description",
                enabled,
                credentialConfigured,
                downloadAvailable,
                unavailableReason,
                apis,
                datasets);
    }
}
