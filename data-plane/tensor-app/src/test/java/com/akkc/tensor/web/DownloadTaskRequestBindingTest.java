package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.akkc.tensor.config.DownloadBindingConfiguration;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.task.*;
import com.akkc.tensor.core.registry.*;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.tushare.TusharePluginConfiguration;
import com.akkc.tensor.web.download.*;
import com.akkc.tensor.web.dto.DownloadTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.web.dto.DownloadTaskControlRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import org.junit.jupiter.api.Test;

class DownloadTaskRequestBindingTest {
    private static final String BODY = """
            {"submissionId":"10000000-0000-0000-0000-000000000001","pluginId":"tushare_pro",
             "apiName":"daily","mode":"SINGLE","params":{"ts_code":"000001.SZ","trade_date":"20260905"}}
            """;

    @Test
    void bindsSingleToTheDeclaredParameterTypeWithoutCreatingATask() throws Exception {
        var flow = flow();
        var request = flow.mapper.readValue(BODY, DownloadTaskRequest.class);
        assertThat(request).isInstanceOfSatisfying(DownloadTaskRequest.Bound.class,
                bound -> assertThat(bound.params()).isInstanceOf(DownloadParameters.TradeDateParameters.class));
        org.mockito.Mockito.verify(flow.repository, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findsEquivalentHistoricalSubmissionAfterPluginIsDisabledWithoutRebinding() throws Exception {
        var flow = flow();
        var key = DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily"));
        var params = Map.<String,Object>of("ts_code", "000001.SZ", "trade_date", "20260905");
        var json = new DownloadTaskJson();
        var time = Instant.parse("2026-09-12T00:00:00Z");
        var id = UUID.fromString("10000000-0000-0000-0000-000000000001");
        var task = new DownloadTask(UUID.randomUUID(), id, json.requestHash(key, DownloadMode.SINGLE, params),
                key, DownloadMode.SINGLE, params, "stored-definition", json.policySnapshot(DownloadMode.SINGLE, null),
                DownloadTask.Status.SUCCEEDED, true, UUID.randomUUID(), 1, 5, 1, 1, null, time, time, time, time, time, time);
        when(flow.repository.findSubmission(id)).thenReturn(Optional.of(task));
        flow.available.set(false);
        var disabledMapper = flow.mapperFor.apply(new PluginRegistry(List.of(flow.plugin)));
        var replay = disabledMapper.readValue(BODY.replace("000001.SZ", " 000001.sz "), DownloadTaskRequest.class);
        assertThat(replay).isInstanceOfSatisfying(DownloadTaskRequest.Replay.class,
                value -> assertThat(value.submission().params()).isEqualTo(params));
        assertCode(disabledMapper, BODY.replace("000001.SZ", "000002.SZ"), ErrorCode.SUBMISSION_CONFLICT);
        assertCode(disabledMapper, BODY.replace(id.toString(), UUID.randomUUID().toString()), ErrorCode.PLUGIN_DISABLED);
        org.mockito.Mockito.verify(flow.repository, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bindsAllFourRangeShapesUsingTheirIndependentDescriptions() throws Exception {
        Map<String, Class<? extends DownloadParameters>> expected = Map.of(
                "dates", DownloadParameters.DateRangeParameters.class,
                "exchange", DownloadParameters.ExchangeDateRangeParameters.class,
                "exchange_id", DownloadParameters.ExchangeIdDateRangeParameters.class,
                "ts_code", DownloadParameters.TsCodeDateRangeParameters.class);
        for (var shape : expected.entrySet()) {
            var source = new RangeSource(shape.getKey());
            var flow = rangeFlow(source);
            var request = (DownloadTaskRequest.Bound) flow.mapper.readValue(rangeBody(shape.getKey()), DownloadTaskRequest.class);
            assertThat(request.mode()).isEqualTo(DownloadMode.RANGE);
            assertThat(request.params()).isInstanceOf(shape.getValue());
            Map<String, Object> values = flow.resolver.toRawValues(request.params(), request.suppliedFields());
            assertThat(values).containsEntry("start_date", "20260803").containsEntry("end_date", "20260805")
                    .doesNotContainKeys("trade_date", "ann_date");
            if (!shape.getKey().equals("dates")) {
                assertThat(values).containsEntry(shape.getKey(), shape.getKey().equals("ts_code") ? "000001.SZ" : "SSE");
            }
            org.mockito.Mockito.verify(flow.repository, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.verify(flow.repository, org.mockito.Mockito.never()).queuedCount();
        }
    }

    @Test
    void refusesUnknownRangeShapeAndOversizedParametersWithoutCreatingTaskOrCallingSource() {
        var flow = rangeFlow(new RangeSource("market"));
        assertCode(flow.mapper, rangeBody("market"), ErrorCode.DATASET_MISCONFIGURED);
        assertCode(flow.mapper, rangeBody("market").replace("basic", "x".repeat(9000)), ErrorCode.PARAM_INVALID);
        org.mockito.Mockito.verify(flow.repository, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rangeReplayUsesStoredPolicyAfterAllCurrentDescriptionsAreRemoved() throws Exception {
        var source = new RangeSource("ts_code");
        var flow = rangeFlow(source);
        Map<String, Object> params = Map.of("ts_code", "000001.SZ", "start_date", "20260803", "end_date", "20260805");
        DownloadTask task = storedTask(DownloadMode.RANGE, params, source.range);
        when(flow.repository.findSubmission(task.submissionId())).thenReturn(Optional.of(task));
        var historicalMapper = flow.mapperFor.apply(new PluginRegistry(List.of()));
        var request = historicalMapper.readValue(rangeBody("ts_code"), DownloadTaskRequest.class);
        assertThat(request).isInstanceOfSatisfying(DownloadTaskRequest.Replay.class,
                replay -> assertThat(replay.submission().params()).isEqualTo(params));
        assertCode(historicalMapper, rangeBody("ts_code").replace("000001.sz", "000002.sz"), ErrorCode.SUBMISSION_CONFLICT);
        assertCode(historicalMapper, rangeBody("ts_code").replace("\"params\":{", "\"params\":{\"unexpected\":\"value\","),
                ErrorCode.SUBMISSION_CONFLICT);
        org.mockito.Mockito.verify(flow.repository, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void singleReplayWithoutCurrentMetadataRequiresExactNormalizedStoredValues() throws Exception {
        var flow = flow();
        Map<String, Object> params = Map.of("ts_code", "000001.SZ", "trade_date", "20260905");
        var task = storedTask(DownloadMode.SINGLE, params, null);
        when(flow.repository.findSubmission(task.submissionId())).thenReturn(Optional.of(task));
        var historicalMapper = flow.mapperFor.apply(new PluginRegistry(List.of()));
        assertThat(historicalMapper.readValue(BODY, DownloadTaskRequest.class))
                .isInstanceOfSatisfying(DownloadTaskRequest.Replay.class,
                        replay -> assertThat(replay.submission().params()).isEqualTo(params));
        assertCode(historicalMapper, BODY.replace("000001.SZ", " 000001.sz "), ErrorCode.SUBMISSION_CONFLICT);
        org.mockito.Mockito.verify(flow.repository, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preservesClassifiedQueryFailureThroughTheJacksonBoundary() {
        var flow = flow();
        when(flow.repository.findSubmission(UUID.fromString("10000000-0000-0000-0000-000000000001")))
                .thenThrow(new TensorException(ErrorCode.QUERY_FAILED, "Query failed") {});
        assertCode(flow.mapper, BODY, ErrorCode.QUERY_FAILED);
        org.mockito.Mockito.verify(flow.repository, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidPresentTopLevelValuesWinOverMissingFieldsWithSortedSafeErrors() {
        var flow = flow();
        var failure = bindingFailure(flow.mapper, """
                {"submissionId":null,"pluginId":true,"apiName":"daily","mode":"SINGLE","params":{}}
                """);
        assertThat(failure.code()).isEqualTo(ErrorCode.PARAM_INVALID);
        assertThat(failure.fieldErrors()).extracting(com.akkc.tensor.web.dto.FieldErrorResponse::field)
                .containsExactly("pluginId", "submissionId");
        assertThat(failure.fieldErrors()).extracting(com.akkc.tensor.web.dto.FieldErrorResponse::message)
                .containsExactly("has invalid value", "is required");
        var required = bindingFailure(flow.mapper, "{}");
        assertThat(required.code()).isEqualTo(ErrorCode.PARAM_REQUIRED);
        assertThat(required.fieldErrors()).extracting(com.akkc.tensor.web.dto.FieldErrorResponse::field)
                .containsExactly("apiName", "mode", "params", "pluginId", "submissionId");
        org.mockito.Mockito.verifyNoInteractions(flow.repository);
    }

    @Test
    void bindsEveryCurrentSingleShapeAndKeepsNormalizedValues() throws Exception {
        var flow = flow();
        for (var args : DownloadRequestBindingTest.currentRequests().toList()) {
            var node = ((com.fasterxml.jackson.databind.node.ObjectNode) args.get()[1]).deepCopy();
            node.put("submissionId", UUID.randomUUID().toString()); node.put("mode", "SINGLE");
            var bound = (DownloadTaskRequest.Bound) flow.mapper.readValue(node.toString(), DownloadTaskRequest.class);
            assertThat(flow.resolver.toRawValues(bound.params(), bound.suppliedFields()))
                    .isEqualTo(flow.mapper.convertValue(node.get("params"), Map.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "true", "null", "[]", "{}"})
    void rejectsNonStringBusinessValuesBeforeAdmission(String value) {
        var flow = flow();
        assertCode(flow.mapper, BODY.replace("\"000001.SZ\"", value), ErrorCode.PARAM_INVALID);
        org.mockito.Mockito.verifyNoInteractions(flow.repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{\"expectedVersion\":null}", "{\"expectedVersion\":0}",
            "{\"expectedVersion\":-1}", "{\"expectedVersion\":\"1\"}", "{\"expectedVersion\":1.0}",
            "{\"expectedVersion\":1e0}", "{\"expectedVersion\":true}", "{\"expectedVersion\":9223372036854775808}",
            "{\"expectedVersion\":1,\"extra\":1}", "{\"expectedVersion\":1,\"expectedVersion\":1}",
            "{\"expectedVersion\":1} {}"})
    void refusesInvalidControlTokens(String body) {
        assertThatThrownBy(() -> flow().mapper.readValue(body, DownloadTaskControlRequest.class))
                .isInstanceOfAny(DownloadBindingException.class, com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test
    void preservesLongControlVersionAsAnIntegerWithoutTruncation() throws Exception {
        var request = flow().mapper.readValue("{\"expectedVersion\":9223372036854775807}", DownloadTaskControlRequest.class);
        assertThat(request.expectedVersion()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsDuplicateUnknownTrailingAndMalformedSubmissionStructure() {
        var flow = flow();
        for (String body : List.of(BODY + "{}", BODY.replace("\"mode\":", "\"extra\":1,\"mode\":"),
                BODY.replace("\"mode\":", "\"mode\":\"RANGE\",\"mode\":"),
                BODY.replace("\"ts_code\":", "\"ts_code\":\"secret\",\"ts_code\":"),
                BODY.replace("10000000-0000-0000-0000-000000000001", "1-1-1-1-1"),
                BODY.replace("\"SINGLE\"", "\"single\""), BODY.replace("\"SINGLE\"", "true"),
                BODY.replace("\"mode\":\"SINGLE\",", ""), "null", "[]")) {
            assertThatThrownBy(() -> flow.mapper.readValue(body, DownloadTaskRequest.class))
                    .isInstanceOfAny(DownloadBindingException.class, com.fasterxml.jackson.core.JsonProcessingException.class);
        }
        org.mockito.Mockito.verifyNoInteractions(flow.repository);
    }

    private static Flow flow() {
        var available = new AtomicBoolean(true);
        var definitions = new TusharePluginConfiguration().tushareDatasetDefinitions();
        PluginId id = PluginId.of("tushare_pro");
        DataSourcePlugin plugin = new DataSourcePlugin() {
            public PluginReadiness readiness() {
                return new PluginReadiness(true, available.get(), available.get(), available.get() ? null : "Unavailable");
            }
            public PluginDescriptor descriptor() {
                return new PluginDescriptor(id, "Test", "Test", true, available.get(), available.get(), available.get() ? null : "Unavailable",
                        definitions.stream().map(d -> new ApiDescriptor(d.datasetKey().apiName(),
                                d.displayName(), d.category(), d.queryMode(), d.parameters())).toList(),
                        definitions.stream().map(d -> d.datasetKey()).toList());
            }
            public DownloadEnvelope download(ApiName api, Map<String, Object> params) {
                throw new AssertionError("Binding must not call the source");
            }
        };
        var plugins = new PluginRegistry(List.of(plugin));
        var catalog = mock(DatasetCatalog.class);
        List<DatasetAdapter> adapterList = new ArrayList<>();
        for (var definition : definitions) {
            when(catalog.find(definition.datasetKey())).thenReturn(Optional.of(definition));
            var adapter = mock(DatasetAdapter.class);
            when(adapter.datasetKey()).thenReturn(definition.datasetKey());
            adapterList.add(adapter);
        }
        var adapters = new AdapterRegistry(adapterList);
        var validator = new ParameterValidator();
        var repository = mock(DownloadTaskRepository.class);
        var resolver = new DownloadParameterResolver(new DownloadDescriptorResolver(plugins, adapters), validator);
        java.util.function.Function<PluginRegistry, ObjectMapper> mapperFor = registry -> mapper(
                registry, catalog, adapters, validator, repository);
        return new Flow(mapperFor.apply(plugins), repository, resolver, available, mapperFor, plugin);
    }

    private record Flow(ObjectMapper mapper, DownloadTaskRepository repository,
            DownloadParameterResolver resolver, AtomicBoolean available,
            java.util.function.Function<PluginRegistry, ObjectMapper> mapperFor, DataSourcePlugin plugin) {}

    private static Flow rangeFlow(RangeSource source) {
        var catalog = mock(DatasetCatalog.class);
        var definition = new TusharePluginConfiguration().tushareDatasetDefinitions().stream()
                .filter(value -> value.datasetKey().apiName().value().equals("daily")).findFirst().orElseThrow();
        when(catalog.find(RangeSource.KEY)).thenReturn(Optional.of(definition));
        var adapter = mock(DatasetAdapter.class);
        when(adapter.datasetKey()).thenReturn(RangeSource.KEY);
        var adapters = new AdapterRegistry(List.of(adapter));
        var validator = new ParameterValidator();
        var repository = mock(DownloadTaskRepository.class);
        var registry = new PluginRegistry(List.of(source));
        var resolver = new DownloadParameterResolver(new DownloadDescriptorResolver(registry, adapters), validator);
        java.util.function.Function<PluginRegistry, ObjectMapper> mapperFor = plugins -> mapper(
                plugins, catalog, adapters, validator, repository);
        return new Flow(mapperFor.apply(registry), repository, resolver, new AtomicBoolean(true), mapperFor, source);
    }

    private static ObjectMapper mapper(PluginRegistry plugins, DatasetCatalog catalog, AdapterRegistry adapters,
            ParameterValidator validator, DownloadTaskRepository repository) {
        var service = new DownloadTaskService(plugins, catalog, adapters, validator, repository,
                new DownloadTaskJson(), Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC),
                UUID.randomUUID(), DownloadTaskService.Settings.defaults());
        var resolver = new DownloadParameterResolver(new DownloadDescriptorResolver(plugins, adapters), validator);
        return new ObjectMapper().registerModule(new JacksonPrecisionConfiguration().precisionModule())
                .registerModule(new DownloadBindingConfiguration().downloadTaskRequestJacksonModule(
                        new DownloadTaskRequestDeserializer(service, resolver), new DownloadTaskControlRequestDeserializer()));
    }

    private static DownloadTask storedTask(DownloadMode mode, Map<String, Object> params, BatchDownloadDescriptor range) {
        var json = new DownloadTaskJson();
        var at = Instant.parse("2026-09-12T00:00:00Z");
        return new DownloadTask(UUID.randomUUID(), UUID.fromString("10000000-0000-0000-0000-000000000001"),
                json.requestHash(RangeSource.KEY, mode, params), RangeSource.KEY, mode, params, "stored-definition",
                json.policySnapshot(mode, range), DownloadTask.Status.SUCCEEDED, true, UUID.randomUUID(),
                1, 5, 1, 1, null, at, at, at, at, at, at);
    }

    private static String rangeBody(String shape) {
        String scope = switch (shape) {
            case "dates" -> "";
            case "ts_code" -> "\"ts_code\":\" 000001.sz \",";
            case "market" -> "\"market\":\"basic\",";
            default -> "\"" + shape + "\":\"SSE\",";
        };
        return "{\"submissionId\":\"10000000-0000-0000-0000-000000000001\",\"pluginId\":\"tushare_pro\","
                + "\"apiName\":\"daily\",\"mode\":\"RANGE\",\"params\":{" + scope
                + "\"start_date\":\"20260803\",\"end_date\":\"20260805\"}}";
    }

    private static void assertCode(ObjectMapper mapper, String body, ErrorCode expected) {
        assertThat(bindingFailure(mapper, body).code()).isEqualTo(expected);
    }

    private static DownloadBindingException bindingFailure(ObjectMapper mapper, String body) {
        Throwable failure = catchThrowable(() -> mapper.readValue(body, DownloadTaskRequest.class));
        assertThat(failure).isNotNull();
        while (failure != null) {
            if (failure instanceof DownloadBindingException binding) return binding;
            failure = failure.getCause();
        }
        throw new AssertionError("Expected a classified download binding failure");
    }

    private static final class RangeSource implements BatchDownloadSupport {
        static final DatasetKey KEY = DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily"));
        final BatchDownloadDescriptor range;
        RangeSource(String shape) {
            var parameters = new ArrayList<ParameterDescriptor>();
            if (!shape.equals("dates")) {
                ParameterType type = shape.equals("ts_code") ? ParameterType.TS_CODE
                        : shape.equals("market") ? ParameterType.TEXT : ParameterType.ENUM;
                parameters.add(new ParameterDescriptor(shape, shape, null, type, true, null,
                        type == ParameterType.ENUM ? List.of("SSE", "SZSE", "BSE") : List.of(), null, null));
            }
            parameters.add(new ParameterDescriptor("start_date", "Start", null, ParameterType.DATE_RANGE_MEMBER,
                    true, null, List.of(), null, "end_date"));
            parameters.add(new ParameterDescriptor("end_date", "End", null, ParameterType.DATE_RANGE_MEMBER,
                    true, null, List.of(), null, "start_date"));
            range = new BatchDownloadDescriptor(parameters, "start_date", "end_date",
                    BatchDownloadDescriptor.DateAxis.CALENDAR_DATE, "Calendar date",
                    BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true,
                    BatchDownloadDescriptor.Availability.AVAILABLE, null, "binding-test-v1",
                    new BatchDownloadDescriptor.CompletenessRule(
                            BatchDownloadDescriptor.CompletenessRule.Kind.VERIFIED_RULE, null, "Controlled source"));
        }
        public PluginDescriptor descriptor() {
            return new PluginDescriptor(KEY.pluginId(), "Controlled", "Binding test", true, true, true, null,
                    List.of(new ApiDescriptor(KEY.apiName(), "Daily", "test", QueryMode.trade_date,
                            List.of(new ParameterDescriptor("trade_date", "Date", null, ParameterType.DATE,
                                    true, null, List.of(), null, null)))), List.of(KEY));
        }
        public PluginReadiness readiness() { return new PluginReadiness(true, true, true, null); }
        public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName api) { return Optional.of(range); }
        public Map<String, Object> sourceParameters(ApiName api, Map<String, Object> params, DateRange dates) { return params; }
        public DownloadEnvelope download(ApiName api, Map<String, Object> params) { throw new AssertionError("No source during binding"); }
        public DownloadEnvelope downloadBatch(ApiName api, Map<String, Object> params, BatchCallContext context) { throw new AssertionError("No source during binding"); }
        public List<DateRange> plan(ApiName api, Map<String, Object> params, BatchCallContext context) { throw new AssertionError("No planning during binding"); }
        public BatchAssessment assess(ApiName api, DateRange range, DownloadEnvelope envelope) { throw new AssertionError("No assessment during binding"); }
    }
}
