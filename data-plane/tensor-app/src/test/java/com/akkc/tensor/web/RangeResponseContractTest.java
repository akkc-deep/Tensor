package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.akkc.tensor.core.download.*;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RangeResponseContractTest {
    static final String ID = "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";
    static ObjectMapper mapper() { return new ObjectMapper().registerModule(new JacksonPrecisionConfiguration().precisionModule()); }
    @Test void commitUnknownPreservesConfirmedNumbersAndRequiredNulls() throws Exception {
        var mapper = mapper();
        var mvc = MockMvcBuilders.standaloneSetup(new Stopped()).setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).addFilters(new RequestIdFilter()).build();
        var response = mvc.perform(get("/stopped").header("X-Request-Id", ID)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(500);
        var json = mapper.readTree(response.getContentAsString());
        assertThat(json.path("code").asText()).isEqualTo("COMMIT_UNCONFIRMED");
        var result = json.path("downloadResult");
        assertThat(result.path("sourceRowCount").isIntegralNumber()).isTrue();
        assertThat(result.path("sourceRowCount").longValue()).isEqualTo(3000000001L);
        assertThat(result.path("insertedRows").longValue()).isEqualTo(3000000000L);
        assertThat(result.path("updatedRows").longValue()).isEqualTo(1);
        for (String field : List.of("taskId", "notStartedUnits", "remainingFailedUnits")) assertThat(result.path(field).isNull()).as(field).isTrue();
        assertThat(result.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(result.size()).isEqualTo(18);
    }
    @Test void allResultNumbersOverridePrecisionWhileRecordValuesKeepPrecision() throws Exception {
        var mapper = mapper();
        var scope = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE, "2026-09-03");
        for (var outcome : DownloadExecutionResult.Outcome.values()) {
            boolean failed = outcome == DownloadExecutionResult.Outcome.FAILED || outcome == DownloadExecutionResult.Outcome.PARTIAL;
            long completed = outcome == DownloadExecutionResult.Outcome.FAILED || outcome == DownloadExecutionResult.Outcome.NO_OPEN_DATES ? 0 : 2;
            long rows = outcome == DownloadExecutionResult.Outcome.EMPTY || completed == 0 ? 0 : 3000000001L;
            var result = new DownloadExecutionResult(new RequestId(UUID.fromString(ID)), outcome, PluginId.of("fixture"), ApiName.of("fixture_daily"),
                    rows, rows == 0 ? 0 : 3000000000L, rows == 0 ? 0 : 1, "Safe", completed, failed ? 1 : 0, 0L,
                    outcome == DownloadExecutionResult.Outcome.NO_OPEN_DATES ? 3000000000L : 0,
                    failed ? UUID.fromString(ID) : null, failed ? 1L : 0L,
                    outcome == DownloadExecutionResult.Outcome.UNCONFIRMED ? DownloadExecutionResult.FailureRecordStatus.UNCONFIRMED
                            : failed ? DownloadExecutionResult.FailureRecordStatus.CONFIRMED : DownloadExecutionResult.FailureRecordStatus.NOT_REQUIRED,
                    failed ? List.of(new RecoveryUnitProcessor.Failure(scope, ErrorCode.SOURCE_TIMEOUT, "SOURCE_SECRET")) : List.of(), List.of(), List.of());
            var json = mapper.valueToTree(com.akkc.tensor.web.dto.DownloadResponse.from(result));
            assertThat(names(json)).containsExactlyInAnyOrder("requestId", "outcome", "pluginId", "apiName", "sourceRowCount", "insertedRows", "updatedRows", "message",
                    "completedUnits", "failedUnits", "notStartedUnits", "skippedClosedDates", "taskId", "remainingFailedUnits", "failureRecordStatus", "failures", "notStartedScopes", "unconfirmedScopes");
            for (String field : List.of("sourceRowCount", "insertedRows", "updatedRows", "completedUnits", "failedUnits", "notStartedUnits", "skippedClosedDates", "remainingFailedUnits"))
                assertThat(json.path(field).isIntegralNumber()).as(outcome + ":" + field).isTrue();
            if (failed) {
                var failure = json.path("failures").get(0);
                assertThat(names(failure)).containsExactlyInAnyOrder("targetType", "targetValue", "timeType", "timeValue", "errorCode", "errorMessage");
                assertThat(failure.path("errorMessage").asText()).isEqualTo("Source request timed out");
            }
            assertThat(json.toString()).doesNotContain("SOURCE_SECRET");
        }
        var records = mapper.valueToTree(Map.of("bigint", 9007199254740993L, "decimal", new java.math.BigDecimal("12345678901234567890.123456789")));
        assertThat(records.path("bigint").asText()).isEqualTo("9007199254740993");
        assertThat(records.path("bigint").isTextual()).isTrue();
        assertThat(records.path("decimal").isTextual()).isTrue();
        assertThat(records.path("decimal").asText()).isEqualTo("12345678901234567890.123456789");
    }

    @Test void taskDtosFlattenAllFieldsPreserveNullsAndFormatUtcMilliseconds() {
        var scope = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE, "2026-09-03");
        var summary = new com.akkc.tensor.core.retry.RetryTaskQueryService.TaskSummary(UUID.fromString(ID), PluginId.of("removed"), ApiName.of("daily"), null, null, null,
                com.akkc.tensor.core.retry.RetryTaskQueryService.OriginalRangeStatus.UNCONFIRMED, 1, List.of(scope), java.time.Instant.EPOCH, java.time.Instant.ofEpochMilli(123));
        var detail = new com.akkc.tensor.core.retry.RetryTaskQueryService.TaskDetail(summary, Map.of("start_date", "20260901"),
                List.of(new com.akkc.tensor.core.retry.RetryTaskQueryService.TaskItem(scope, ErrorCode.SOURCE_TIMEOUT, java.time.Instant.EPOCH)), false, false,
                new com.akkc.tensor.core.retry.RetryTaskQueryService.ExecutionBlocker(ErrorCode.PLUGIN_DISABLED, "Plugin is unavailable"));
        var mapper = mapper();
        var json = mapper.valueToTree(com.akkc.tensor.web.dto.RetryTaskResponse.Detail.from(ID, detail));
        assertThat(names(json)).containsExactlyInAnyOrder("taskId", "pluginId", "apiName", "pluginDisplayName", "apiDisplayName", "originalDateRange", "originalDateRangeStatus",
                "failedItemCount", "failedScopes", "createdAt", "updatedAt", "requestId", "taskParams", "items", "retrying", "canExecute", "executionBlocker");
        for (String field : List.of("pluginDisplayName", "apiDisplayName", "originalDateRange")) assertThat(json.path(field).isNull()).isTrue();
        assertThat(json.path("createdAt").asText()).isEqualTo("1970-01-01T00:00:00.000Z");
        assertThat(json.path("updatedAt").asText()).isEqualTo("1970-01-01T00:00:00.123Z");
        assertThat(json.path("taskParams").isObject()).isTrue();
        assertThat(json.path("failedItemCount").isIntegralNumber()).isTrue();
        assertThat(names(json.path("items").get(0))).containsExactlyInAnyOrder("targetType", "targetValue", "timeType", "timeValue", "errorCode", "errorMessage", "updatedAt");
        var page = mapper.valueToTree(com.akkc.tensor.web.dto.RetryTaskResponse.Page.from(ID,
                new com.akkc.tensor.core.retry.RetryTaskQueryService.TaskPage(List.of(summary), 1, 20, 3000000000L, 150000000L)));
        assertThat(page.path("totalElements").isIntegralNumber()).isTrue();
        assertThat(page.path("totalElements").longValue()).isEqualTo(3000000000L);
        assertThat(page.path("totalPages").isIntegralNumber()).isTrue();
        assertThat(names(page.path("items").get(0))).hasSize(11);
    }

    static Set<String> names(com.fasterxml.jackson.databind.JsonNode node) {
        var names = new HashSet<String>(); node.fieldNames().forEachRemaining(names::add); return names;
    }
    @Test void allStoppedErrorCodesKeepSnapshotRetryabilityAndOriginalFacts() throws Exception {
        for (ErrorCode code : List.of(ErrorCode.PERSISTENCE_FAILED, ErrorCode.INTERNAL_ERROR, ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED, ErrorCode.COMMIT_UNCONFIRMED)) {
            var mapper = mapper();
            var mvc = MockMvcBuilders.standaloneSetup(new Stopped(code)).setControllerAdvice(new GlobalExceptionHandler())
                    .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).addFilters(new RequestIdFilter()).build();
            var response = mvc.perform(get("/stopped").header("X-Request-Id", ID)).andReturn().getResponse();
            var json = mapper.readTree(response.getContentAsString());
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(json.path("code").asText()).isEqualTo(code.name());
            assertThat(json.path("retryable").booleanValue()).isEqualTo(code == ErrorCode.PERSISTENCE_FAILED);
            var result = json.path("downloadResult");
            assertThat(result.path("outcome").asText()).isEqualTo("UNCONFIRMED");
            assertThat(result.path("insertedRows").longValue()).isEqualTo(3000000000L);
            assertThat(result.path("updatedRows").longValue()).isEqualTo(1);
            assertThat(result.path("notStartedUnits").isNull()).isTrue();
            assertThat(result.path("remainingFailedUnits").isNull()).isTrue();
            assertThat(result.path("requestId").asText()).isEqualTo(ID);
            assertThat(json.path("fieldErrors")).isEmpty();
        }
    }

    @Test void everyPersistableReasonUsesAFixedPublicMessage() {
        var expected = Map.ofEntries(
                Map.entry(ErrorCode.SOURCE_AUTH_FAILED, "Source authentication failed"), Map.entry(ErrorCode.SOURCE_PERMISSION_DENIED, "Source permission denied"),
                Map.entry(ErrorCode.SOURCE_RATE_LIMITED, "Source rate limit exceeded"), Map.entry(ErrorCode.SOURCE_UNAVAILABLE, "Source is unavailable"),
                Map.entry(ErrorCode.SOURCE_NETWORK_ERROR, "Source network request failed"), Map.entry(ErrorCode.SOURCE_TIMEOUT, "Source request timed out"),
                Map.entry(ErrorCode.SOURCE_PAYLOAD_INVALID, "Source returned an invalid payload"), Map.entry(ErrorCode.SOURCE_TRUNCATED, "Source response is truncated"),
                Map.entry(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, "Source response completeness is unconfirmed"),
                Map.entry(ErrorCode.ADAPTER_FIELD_MISSING, "Source data is missing a required field"), Map.entry(ErrorCode.ADAPTER_TYPE_INVALID, "Source data contains an invalid value"),
                Map.entry(ErrorCode.DATA_CONFLICT, "Source data contains conflicting values"), Map.entry(ErrorCode.PERSISTENCE_FAILED, "Persistence failed"));
        var scope = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE, "2026-09-03");
        expected.forEach((code, message) -> {
            var json = mapper().valueToTree(com.akkc.tensor.web.dto.DownloadFailureResponse.from(new RecoveryUnitProcessor.Failure(scope, code, "HISTORY_SECRET")));
            assertThat(json.path("errorMessage").asText()).isEqualTo(message);
            assertThat(json.toString()).doesNotContain("HISTORY_SECRET");
        });
    }

    @RestController static class Stopped {
        private final ErrorCode code;
        Stopped() { this(ErrorCode.COMMIT_UNCONFIRMED); }
        Stopped(ErrorCode code) { this.code = code; }
        @GetMapping("/stopped") Object stopped() {
            throw new DownloadExecutionException(code,
                    new DownloadExecutionResult(new RequestId(UUID.fromString(ID)), DownloadExecutionResult.Outcome.UNCONFIRMED,
                            PluginId.of("fixture"), ApiName.of("fixture_daily"), 3000000001L, 3000000000L, 1,
                            "结果未确认", 2, 0, null, 0, null, null, DownloadExecutionResult.FailureRecordStatus.UNCONFIRMED,
                            List.of(), List.of(), List.of(new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE, "2026-09-07"))));
        }
    }
}
