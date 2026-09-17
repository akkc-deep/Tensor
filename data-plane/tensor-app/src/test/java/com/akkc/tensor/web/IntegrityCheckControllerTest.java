package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.integrity.*;
import com.akkc.tensor.core.integrity.IntegrityCheckRepository.*;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.hamcrest.Matcher;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrityCheckControllerTest {
    private static final UUID REQUEST_ID = UUID.fromString("c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33");
    private static final UUID CHECK_ID = UUID.fromString("2e1a6b66-7e12-4d9d-86f5-3f47a699f8c0");
    private static final UUID RESULT_ID = UUID.fromString("d4182940-ac81-40aa-b0d5-5197b26c927c");
    private static final UUID SUBMISSION_ID = UUID.fromString("47e27b3d-d13c-41d8-8793-c8b2ed1a6732");
    private static final String REQUEST = """
            {"submissionId":"47e27b3d-d13c-41d8-8793-c8b2ed1a6732","pluginId":"fixture_source","capabilityHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","symbols":["000001.SZ"],"startDate":"2026-09-01","endDate":"2026-09-02"}
            """;

    private IntegrityCheckService service;
    private IntegrityCheckRepository repository;
    private IntegrityCheckJson json;
    private DownloadTaskService downloads;
    private ObjectMapper mapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(IntegrityCheckRepository.class);
        json = new IntegrityCheckJson(); downloads = mock(DownloadTaskService.class);
        service = spy(new IntegrityCheckService(mock(PluginRegistry.class), mock(DatasetCatalog.class), repository,
                json, new IntegrityCheckQueue(20), Clock.systemUTC(), IntegrityCheckService.Settings.defaults()));
        mapper = new ObjectMapper().findAndRegisterModules()
                .registerModule(new JacksonPrecisionConfiguration().precisionModule());
        mvc = MockMvcBuilders.standaloneSetup(
                        new IntegrityCheckController(service, json, downloads, mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(mapper))
                .addFilters(new RequestIdFilter())
                .build();
    }

    @AfterEach void clearMdc() { MDC.clear(); }

    @Test
    void acceptsAnUnchangedOriginalRequestAndReturnsItsCreatedReceipt() throws Exception {
        doReturn(new IntegrityCheckService.SubmissionResult(task(IntegrityTaskStatus.QUEUED), true))
                .when(service).submit(any());
        mvc.perform(identified(post("/api/v1/integrity-checks")).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isAccepted()).andExpect(header().string("Location", "/api/v1/integrity-checks/" + CHECK_ID))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.checkId").value(CHECK_ID.toString()))
                .andExpect(jsonPath("$.submissionId").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$.pluginId").value("fixture_source"))
                .andExpect(jsonPath("$.plannedUnits").value(1));
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(service).submit(request.capture());
        assertThat((JsonNode) mapper.valueToTree(request.getValue())).isEqualTo(json.readValue(REQUEST));
    }

    @Test
    void replayUsesTheSavedStatusAndStillReturnsTheSameLocation() throws Exception {
        String oldRequest = REQUEST.replace("a".repeat(64), "b".repeat(64));
        doReturn(new IntegrityCheckService.SubmissionResult(task(IntegrityTaskStatus.COMPLETED), false))
                .when(service).submit(any());
        mvc.perform(identified(post("/api/v1/integrity-checks")).contentType(MediaType.APPLICATION_JSON).content(oldRequest))
                .andExpect(status().isOk()).andExpect(header().string("Location", "/api/v1/integrity-checks/" + CHECK_ID))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(service).submit(request.capture());
        assertThat((JsonNode) mapper.valueToTree(request.getValue())).isEqualTo(json.readValue(oldRequest));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"x\":1,\"x\":1}", "{} trailing", "[]", "null"})
    void rejectsMalformedDuplicateTrailingAndNonObjectBodies(String body) throws Exception {
        mvc.perform(identified(post("/api/v1/integrity-checks")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAM_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void doesNotTurnServiceConflictIntoAParsingError() throws Exception {
        doThrow(new TestException(ErrorCode.SUBMISSION_CONFLICT)).when(service).submit(any());
        mvc.perform(identified(post("/api/v1/integrity-checks")).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_CONFLICT"));
    }

    @Test
    void mapsCapabilitySettingsApiOrderDescriptorAndDownloadAvailability() throws Exception {
        var settings = new IntegrityCheckService.Settings(100, 36600, 4000, 20, 1, 500,
                Long.MAX_VALUE, 20000, 120, 1800);
        doReturn(new IntegrityCheckService.Capability(
                PluginId.of("fixture_source"), true, null, "a".repeat(64), settings,
                List.of(snapshot("daily", descriptor("daily")), snapshot("calendar", null))))
                .when(service).capability(PluginId.of("fixture_source"));
        var availability = downloadCapabilities();
        when(downloads.capabilities(any())).thenReturn(availability);
        mvc.perform(identified(get("/api/v1/data-sources/fixture_source/integrity-capabilities")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pluginId").value("fixture_source"))
                .andExpect(jsonPath("$.limits.maxScannedRowsPerUnit").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.apis[0].apiName").value("daily"))
                .andExpect(jsonPath("$.apis[0].descriptor.capabilityVersion").value("rules@2"))
                .andExpect(jsonPath("$.apis[0].downloadAvailability.single.available").value(false))
                .andExpect(jsonPath("$.apis[1].apiName").value("calendar"))
                .andExpect(jsonPath("$.apis[1].descriptor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unavailableCapabilityDoesNotConsultDownloadCapabilities() throws Exception {
        doReturn(new IntegrityCheckService.Capability(
                PluginId.of("old_source"), false, "Plugin disabled", null,
                IntegrityCheckService.Settings.defaults(), List.of()))
                .when(service).capability(PluginId.of("old_source"));
        mvc.perform(identified(get("/api/v1/data-sources/old_source/integrity-capabilities")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.localCheckAvailable").value(false))
                .andExpect(jsonPath("$.unavailableReason").value("Plugin disabled"))
                .andExpect(jsonPath("$.capabilityHash").value(org.hamcrest.Matchers.nullValue()));
        verifyNoInteractions(downloads);
    }

    @Test
    void passesHistoryFiltersAndArbitraryLegalPageSizeExactly() throws Exception {
        var filter = new TaskFilter(PluginId.of("fixture_source"), IntegrityTaskStatus.FAILED, SUBMISSION_ID);
        when(repository.tasks(filter, 3, 37)).thenReturn(new Page<>(3, 37, Long.MAX_VALUE, List.of(task(IntegrityTaskStatus.FAILED))));
        mvc.perform(identified(get("/api/v1/integrity-checks").param("pluginId", "fixture_source")
                        .param("status", "FAILED").param("submissionId", SUBMISSION_ID.toString())
                        .param("page", "3").param("pageSize", "37")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.total").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.items[0].originalRequest.symbols[0]").value("000001.SZ"))
                .andExpect(jsonPath("$.items[0].requestHash").doesNotExist());
    }

    @Test
    void mapsOneProgressSnapshotWithFixedPreciseStatusCounts() throws Exception {
        var counts = new EnumMap<IntegrityStatus, Long>(IntegrityStatus.class);
        for (var status : IntegrityStatus.values()) counts.put(status, status == IntegrityStatus.FAIL ? Long.MAX_VALUE : 0L);
        when(repository.progress(CHECK_ID)).thenReturn(Optional.of(new Progress(task(IntegrityTaskStatus.COMPLETED),
                Long.MAX_VALUE, 0, 1, counts, IntegrityStatus.FAIL)));
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}", CHECK_ID)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedUnits").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.statusCounts.PASS").value("0"))
                .andExpect(jsonPath("$.statusCounts.FAIL").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.startedAt").value(org.hamcrest.Matchers.nullValue()));
        verify(service, never()).capability(any());
        verifyNoInteractions(downloads);
    }

    @Test
    void passesResultFiltersAndReturnsSavedReportWithoutCurrentCapabilityCalls() throws Exception {
        var filter = new ResultFilter(" legacy symbol ", ApiName.of("daily"), IntegrityStatus.UNKNOWN);
        var report = mapper.readTree("{\"statistics\":{\"actualCount\":\"9223372036854775807\",\"coverageRate\":\"0.950000\"},\"incomplete\":true,\"issuesComplete\":false}");
        when(repository.find(CHECK_ID)).thenReturn(Optional.of(task(IntegrityTaskStatus.INTERRUPTED)));
        when(repository.results(CHECK_ID, filter, 1, 100)).thenReturn(new Page<>(1, 100, 1, List.of(
                new ResultRecord(RESULT_ID, CHECK_ID, "private-unit", PluginId.of("fixture_source"),
                        ApiName.of("daily"), " legacy symbol ", "private-definition", mapper.createObjectNode(), report))));
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/results", CHECK_ID)
                        .param("symbol", " legacy symbol ").param("apiName", "daily")
                        .param("overallStatus", "UNKNOWN").param("pageSize", "100")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].report.statistics.coverageRate").value("0.950000"))
                .andExpect(jsonPath("$.items[0].unitKey").doesNotExist());
        verify(service, never()).capability(any());
        verifyNoInteractions(downloads);
    }

    @Test
    void passesIssueFiltersAndPreservesSavedPayloadAndIssueIdPrecision() throws Exception {
        var filter = new IssueFilter(RESULT_ID, "000001.SZ", ApiName.of("daily"), IntegrityIssue.Type.REFERENCE_INCOMPLETE,
                IntegrityStatus.UNKNOWN, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));
        var issue = mapper.readTree("{\"type\":\"REFERENCE_INCOMPLETE\",\"status\":\"UNKNOWN\",\"symbol\":\"000001.SZ\",\"apiName\":\"daily\",\"dateField\":\"trade_date\",\"date\":null,\"businessKey\":{\"symbol\":\"000001.SZ\",\"trade_date\":\"20260901\"},\"field\":null,\"relatedDates\":{\"previous\":\"2026-08-31\"},\"reasonCode\":\"REFERENCE_INCOMPLETE\",\"message\":\"safe\",\"evidence\":[{\"kind\":\"saved\"}],\"incomplete\":true}");
        when(repository.find(CHECK_ID)).thenReturn(Optional.of(task(IntegrityTaskStatus.FAILED)));
        when(repository.issues(CHECK_ID, filter, 2, 20)).thenReturn(new Page<>(2, 20, 1, List.of(
                new IssueRecord(Long.MAX_VALUE, RESULT_ID, "rule.old", "1", issue))));
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/issues", CHECK_ID)
                        .param("resultId", RESULT_ID.toString()).param("symbol", "000001.SZ")
                        .param("apiName", "daily").param("type", "REFERENCE_INCOMPLETE")
                        .param("status", "UNKNOWN").param("dateFrom", "2026-09-01")
                        .param("dateTo", "2026-09-30").param("page", "2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].issueId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.items[0].ruleVersion").value("1"))
                .andExpect(jsonPath("$.items[0].issue.date").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].issue.businessKey.trade_date").value("20260901"));
        verify(service, never()).capability(any());
        verifyNoInteractions(downloads);
    }

    @Test
    void returnsNotFoundForDetailAndChildrenButEmptyForValidForeignResultFilter() throws Exception {
        when(repository.progress(CHECK_ID)).thenReturn(Optional.empty());
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}", CHECK_ID))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTEGRITY_CHECK_NOT_FOUND"));
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/results", CHECK_ID))).andExpect(status().isNotFound());
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/issues", CHECK_ID))).andExpect(status().isNotFound());
        when(repository.find(CHECK_ID)).thenReturn(Optional.of(task(IntegrityTaskStatus.COMPLETED)));
        var foreign = UUID.randomUUID();
        when(repository.issues(CHECK_ID, new IssueFilter(foreign, null, null, null, null, null, null), 1, 20))
                .thenReturn(new Page<>(1, 20, 0, List.of()));
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/issues", CHECK_ID).param("resultId", foreign.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/integrity-checks?unknown=x", "/api/v1/integrity-checks?page=1&page=2",
            "/api/v1/integrity-checks?page=", "/api/v1/integrity-checks?page=0",
            "/api/v1/integrity-checks?page=-1", "/api/v1/integrity-checks?page=1.5",
            "/api/v1/integrity-checks?page=2147483648",
            "/api/v1/integrity-checks?page=999999999999999999999999999999999999",
            "/api/v1/integrity-checks?pageSize=0",
            "/api/v1/integrity-checks?pageSize=101", "/api/v1/integrity-checks?status=failed",
            "/api/v1/integrity-checks?submissionId=1-1-1-1-1",
            "/api/v1/integrity-checks/1-1-1-1-1",
            "/api/v1/integrity-checks/not-a-uuid/results",
            "/api/v1/integrity-checks/2e1a6b66-7e12-4d9d-86f5-3f47a699f8c0/issues?dateFrom=2026-02-30",
            "/api/v1/integrity-checks/2e1a6b66-7e12-4d9d-86f5-3f47a699f8c0/issues?dateFrom=0999-12-31",
            "/api/v1/integrity-checks/2e1a6b66-7e12-4d9d-86f5-3f47a699f8c0/issues?dateTo=10000-01-01",
            "/api/v1/integrity-checks/2e1a6b66-7e12-4d9d-86f5-3f47a699f8c0/issues?dateFrom=2026-10-01&dateTo=2026-09-01",
            "/api/v1/integrity-checks/2e1a6b66-7e12-4d9d-86f5-3f47a699f8c0/results?overallStatus=NOT-APPLICABLE"})
    void rejectsUnknownDuplicateAndInvalidQueryValues(String path) throws Exception {
        mvc.perform(identified(get(path))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsBlankHistoricalSymbolWithoutNormalizingNonblankValues() throws Exception {
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/results", CHECK_ID).param("symbol", "   ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAM_INVALID"));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsHistoricalSymbolsLongerThan255CodePoints() throws Exception {
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/results", CHECK_ID)
                        .param("symbol", "股".repeat(256))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAM_INVALID"));
        verifyNoInteractions(repository);
    }

    @Test
    void appliesDefaultPagingAcceptsPageSizeOneAndAllowsOneSidedIssueDates() throws Exception {
        var taskFilter = new TaskFilter(null, null, null);
        when(repository.tasks(taskFilter, 1, 20)).thenReturn(new Page<>(1, 20, 0, List.of()));
        mvc.perform(identified(get("/api/v1/integrity-checks"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.pageSize").value(20));

        when(repository.find(CHECK_ID)).thenReturn(Optional.of(task(IntegrityTaskStatus.COMPLETED)));
        var resultFilter = new ResultFilter(null, null, null);
        when(repository.results(CHECK_ID, resultFilter, 1, 1)).thenReturn(new Page<>(1, 1, 0, List.of()));
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/results", CHECK_ID).param("pageSize", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pageSize").value(1));

        var from = LocalDate.parse("2026-09-01");
        var issueFilter = new IssueFilter(null, null, null, null, null, from, null);
        when(repository.issues(CHECK_ID, issueFilter, 1, 20)).thenReturn(new Page<>(1, 20, 0, List.of()));
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/issues", CHECK_ID)
                        .param("dateFrom", from.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

        String maximumSymbol = "股".repeat(255);
        var exactSymbol = new ResultFilter(maximumSymbol, null, null);
        when(repository.results(CHECK_ID, exactSymbol, 1, 20)).thenReturn(new Page<>(1, 20, 0, List.of()));
        mvc.perform(identified(get("/api/v1/integrity-checks/{id}/results", CHECK_ID)
                        .param("symbol", maximumSymbol)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void capabilityDetailAndPostRejectAnyQueryParameter() throws Exception {
        for (var request : List.of(get("/api/v1/data-sources/fixture_source/integrity-capabilities").param("x", "1"),
                get("/api/v1/integrity-checks/{id}", CHECK_ID).param("x", "1"),
                post("/api/v1/integrity-checks").param("x", "1").contentType(MediaType.APPLICATION_JSON).content(REQUEST)))
            mvc.perform(identified(request)).andExpect(status().isBadRequest());
        verifyNoInteractions(service, repository, downloads);
    }

    private MockHttpServletRequestBuilder identified(MockHttpServletRequestBuilder request) {
        return request.header(RequestIdFilter.HEADER_NAME, REQUEST_ID);
    }

    private TreePath jsonPath(String expression) { return new TreePath(expression); }

    private final class TreePath {
        private final String expression;
        private TreePath(String expression) { this.expression = expression; }
        ResultMatcher value(Object expected) {
            return result -> {
                var node = node(result);
                Object actual = node == null || node.isMissingNode() || node.isNull() ? null
                        : node.isTextual() ? node.textValue()
                        : node.isBoolean() ? node.booleanValue()
                        : node.isIntegralNumber() ? node.numberValue() : node;
                if (expected instanceof Matcher<?> matcher) assertThat(matcher.matches(actual)).isTrue();
                else assertThat(actual).isEqualTo(expected);
            };
        }
        ResultMatcher doesNotExist() { return result -> assertThat(node(result).isMissingNode()).isTrue(); }
        ResultMatcher isEmpty() { return result -> assertThat(node(result).isEmpty()).isTrue(); }
        private JsonNode node(MvcResult result) throws Exception {
            JsonNode current = mapper.readTree(result.getResponse().getContentAsByteArray());
            for (var part : expression.substring(2).split("\\.")) {
                int bracket = part.indexOf('[');
                if (bracket < 0) current = current.path(part);
                else {
                    current = current.path(part.substring(0, bracket));
                    current = current.path(Integer.parseInt(part.substring(bracket + 1, part.length() - 1)));
                }
            }
            return current;
        }
    }

    private TaskRecord task(IntegrityTaskStatus status) {
        var now = Instant.parse("2026-09-17T03:04:05Z");
        return new TaskRecord(CHECK_ID, SUBMISSION_ID, PluginId.of("fixture_source"), "private-request-hash",
                "a".repeat(64), json.readValue(REQUEST), mapper.valueToTree(Map.of("symbols", List.of("000001.SZ"))),
                JsonNodeFactory.instance.arrayNode(), status, 1, now, now, null, null,
                status == IntegrityTaskStatus.FAILED ? "QUERY_FAILED" : null,
                status == IntegrityTaskStatus.FAILED ? "Query failed" : null);
    }

    private IntegrityCheckJson.ApiSnapshot snapshot(String api, IntegrityDescriptor descriptor) {
        var key = DatasetKey.of(PluginId.of("fixture_source"), ApiName.of(api));
        var definition = new DatasetDefinition(key, api + " display", "local", QueryMode.trade_date, List.of(),
                TableName.from(key), List.of(new ColumnDefinition("symbol", "Symbol", LogicalType.STRING, false,
                0, 255, null, null, List.of(), false)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("symbol")), List.of(), null);
        return new IntegrityCheckJson.ApiSnapshot(definition, descriptor, List.of(), List.of());
    }

    private IntegrityDescriptor descriptor(String api) {
        var key = DatasetKey.of(PluginId.of("fixture_source"), ApiName.of(api));
        return new IntegrityDescriptor(key, IntegrityDescriptor.ScopeKind.STOCK_DATE, "symbol", "trade_date", "Trade date",
                ZoneOffset.UTC, "rules@2", List.of(), List.of(new IntegrityRuleDescriptor("coverage", "2", "Coverage",
                IntegrityRuleDescriptor.Dimension.COVERAGE, List.of("symbol"), List.of(), "Saved rule")), List.of("Local only"));
    }

    private DownloadTaskService.DownloadCapabilities downloadCapabilities() {
        var single = mock(DownloadTaskService.SingleCapability.class); var api = mock(ApiDescriptor.class);
        when(api.parameters()).thenReturn(List.of()); when(single.available()).thenReturn(false); when(single.api()).thenReturn(api);
        var range = mock(BatchDownloadDescriptor.class); when(range.parameters()).thenReturn(List.of());
        when(range.completenessRule()).thenReturn(new BatchDownloadDescriptor.CompletenessRule(
                BatchDownloadDescriptor.CompletenessRule.Kind.RESPONSE_ONLY, null, "saved"));
        return new DownloadTaskService.DownloadCapabilities(single, range);
    }

    private static final class TestException extends TensorException {
        TestException(ErrorCode code) { super(code, code.message()); }
    }
}
