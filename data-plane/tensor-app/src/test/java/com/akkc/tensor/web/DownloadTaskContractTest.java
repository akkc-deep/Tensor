package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.core.download.task.DownloadBatch;
import com.akkc.tensor.core.download.task.DownloadTask;
import com.akkc.tensor.core.download.task.DownloadTaskRepository;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.download.batch.DownloadMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.web.dto.DownloadBatchResponse;
import com.akkc.tensor.web.dto.DownloadCapabilitiesResponse;
import com.akkc.tensor.web.dto.DownloadTaskPage;
import com.akkc.tensor.web.dto.DownloadTaskReceipt;
import com.akkc.tensor.web.dto.DownloadTaskResponse;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DownloadTaskContractTest {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Path CONTRACTS = contractsDirectory();

    @Test
    void productionDtosKeepExactInt64NumbersExplicitNullsAndOnlyPublicFields() throws IOException {
        ObjectMapper mapper = productionMapper();
        UUID taskId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        Instant at = Instant.parse("2026-09-12T00:00:00.123Z");
        var task = new DownloadTask(taskId, UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "private-request-hash", new DatasetKey(new PluginId("contract_fixture"), new ApiName("daily")),
                DownloadMode.SINGLE, Map.of("ts_code", "000001.SZ", "trade_date", "20260803"),
                "private-definition-hash", "private-policy", DownloadTask.Status.FAILED, true,
                UUID.randomUUID(), 9, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE - 1,
                new DownloadTaskRepository.StoredError(ErrorCode.SOURCE_TIMEOUT), at, at, at, null, null, null);
        var counts = new DownloadTaskRepository.Counts(Long.MAX_VALUE, Long.MAX_VALUE - 6, 1, 2, 3, 11,
                Long.MAX_VALUE, Long.MAX_VALUE - 1, 1);
        var response = DownloadTaskResponse.from(new DownloadTaskRepository.TaskSnapshot(Optional.of(task), counts),
                new DownloadTaskService.ControlAvailability(true, false));
        JsonNode taskJson = mapper.readTree(mapper.writeValueAsBytes(response));
        assertThat(schema("TaskResponse").validate(taskJson)).isEmpty();
        assertNumber(taskJson, "version", Long.MAX_VALUE);
        assertNumber(taskJson, "requestCount", Long.MAX_VALUE);
        assertNumber(taskJson, "runRequestCount", Long.MAX_VALUE - 1);
        JsonNode countJson = taskJson.path("counts");
        assertNumber(countJson, "totalBatches", Long.MAX_VALUE);
        assertNumber(countJson, "pendingBatches", Long.MAX_VALUE - 6);
        assertNumber(countJson, "runningBatches", 1);
        assertNumber(countJson, "succeededBatches", 2);
        assertNumber(countJson, "failedBatches", 3);
        assertNumber(countJson, "splitBatches", 11);
        assertNumber(countJson, "sourceRows", Long.MAX_VALUE);
        assertNumber(countJson, "insertedRows", Long.MAX_VALUE - 1);
        assertNumber(countJson, "updatedRows", 1);
        assertThat(taskJson.path("createdAt").asText()).isEqualTo("2026-09-12T00:00:00.123Z");
        assertThat(taskJson.path("startedAt").isNull()).isTrue();
        assertThat(taskJson.path("finishedAt").isNull()).isTrue();
        assertThat(taskJson.path("deadlineAt").isNull()).isTrue();
        assertThat(taskJson.path("params").path("trade_date").asText()).isEqualTo("20260803");
        assertThat(taskJson.path("lastError").path("message").asText()).isEqualTo("Source request timed out");
        assertThat(taskJson.path("canRetry").booleanValue()).isTrue();
        assertThat(taskJson.path("canResume").booleanValue()).isFalse();
        assertThat(taskJson.toString()).doesNotContain("private-", "activeRunId", "runGeneration", "retryable");

        JsonNode receipt = mapper.readTree(mapper.writeValueAsBytes(DownloadTaskReceipt.from(
                new RequestId(UUID.fromString("11111111-1111-4111-8111-111111111111")), task)));
        assertThat(schema("Receipt").validate(receipt)).isEmpty();
        assertNumber(receipt, "version", Long.MAX_VALUE);
        JsonNode page = mapper.readTree(mapper.writeValueAsBytes(new DownloadTaskPage<>(1, 20, Long.MAX_VALUE, List.of(response))));
        assertThat(schema("TaskPage").validate(page)).isEmpty();
        assertNumber(page, "page", 1);
        assertNumber(page, "pageSize", 20);
        assertNumber(page, "total", Long.MAX_VALUE);

        var batch = new DownloadBatch(UUID.randomUUID(), taskId, null, "000001", null,
                Map.of("trade_date", "20260803"), DownloadBatch.Status.SUCCEEDED, Integer.MAX_VALUE, 9,
                Long.MAX_VALUE, Long.MAX_VALUE - 1, 1, null, at, at, null, null);
        JsonNode batchJson = mapper.readTree(mapper.writeValueAsBytes(DownloadBatchResponse.from(batch)));
        assertThat(schema("BatchResponse").validate(batchJson)).isEmpty();
        assertNumber(batchJson, "attemptCount", Integer.MAX_VALUE);
        assertNumber(batchJson, "sourceRows", Long.MAX_VALUE);
        assertNumber(batchJson, "insertedRows", Long.MAX_VALUE - 1);
        assertNumber(batchJson, "updatedRows", 1);
        for (String field : List.of("parentBatchId", "rangeStart", "rangeEnd", "error", "startedAt", "finishedAt")) {
            assertThat(batchJson.path(field).isNull()).as(field).isTrue();
        }
        var rangeBatch = new DownloadBatch(batch.batchId(), taskId, UUID.randomUUID(), "000001/0",
                new DateRange(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 5)), batch.sourceParams(),
                batch.status(), 1, 9, 1, 1, 0, null, at, at, at, at);
        JsonNode rangeJson = mapper.readTree(mapper.writeValueAsBytes(DownloadBatchResponse.from(rangeBatch)));
        assertThat(schema("BatchResponse").validate(rangeJson)).isEmpty();
        assertThat(rangeJson.path("rangeStart").asText()).isEqualTo("2026-08-03");
        assertThat(rangeJson.path("rangeEnd").asText()).isEqualTo("2026-08-05");
        JsonNode batchPage = mapper.readTree(mapper.writeValueAsBytes(new DownloadTaskPage<>(1, 20, 1,
                List.of(DownloadBatchResponse.from(rangeBatch)))));
        assertThat(schema("BatchPage").validate(batchPage)).isEmpty();

        JsonNode securities = mapper.readTree(mapper.writeValueAsBytes(Map.of(
                "volume", Long.MAX_VALUE, "price", new BigDecimal("1.230000000000000000"))));
        assertThat(securities.path("volume").isTextual()).isTrue();
        assertThat(securities.path("volume").asText()).isEqualTo("9223372036854775807");
        assertThat(securities.path("price").asText()).isEqualTo("1.230000000000000000");
    }

    @Test
    void capabilitiesSerializeNullableRowLimitAsNumberWithoutChangingParameterOmissionRules() throws IOException {
        var parameters = List.of(
                new ParameterDescriptor("start_date", "Start", null, ParameterType.DATE_RANGE_MEMBER,
                        true, null, List.of(), null, "end_date"),
                new ParameterDescriptor("end_date", "End", null, ParameterType.DATE_RANGE_MEMBER,
                        true, null, List.of(), null, "start_date"));
        var single = new DownloadTaskService.SingleCapability(true,
                new ApiDescriptor(new ApiName("daily"), "Daily", "test", QueryMode.snapshot, List.of()));
        var range = new BatchDownloadDescriptor(parameters, "start_date", "end_date",
                BatchDownloadDescriptor.DateAxis.CALENDAR_DATE, "Calendar date",
                BatchDownloadDescriptor.PlanningMode.NATIVE_RANGE, true,
                BatchDownloadDescriptor.Availability.AVAILABLE, null, "contract-v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.CONFIRMED_ROW_LIMIT, Long.MAX_VALUE,
                        "Controlled fixture boundary"));
        ObjectMapper mapper = productionMapper();
        JsonNode actual = mapper.readTree(mapper.writeValueAsBytes(DownloadCapabilitiesResponse.from(
                new DownloadTaskService.DownloadCapabilities(single, range))));
        assertThat(schema("CapabilitiesResponse").validate(actual)).isEmpty();
        assertNumber(actual.path("range").path("completenessRule"), "rowLimit", Long.MAX_VALUE);
        JsonNode parameter = actual.path("range").path("parameters").get(0);
        for (String omitted : List.of("description", "defaultValue", "allowedValues", "pattern")) {
            assertThat(parameter.has(omitted)).as(omitted).isFalse();
        }
        var unsupported = new BatchDownloadDescriptor(List.of(), null, null, null, null, null, false,
                BatchDownloadDescriptor.Availability.UNSUPPORTED, "Unsupported", "unsupported-v1",
                new BatchDownloadDescriptor.CompletenessRule(
                        BatchDownloadDescriptor.CompletenessRule.Kind.UNKNOWN, null, null));
        JsonNode nulls = mapper.readTree(mapper.writeValueAsBytes(DownloadCapabilitiesResponse.from(
                new DownloadTaskService.DownloadCapabilities(single, unsupported))));
        assertThat(schema("CapabilitiesResponse").validate(nulls)).isEmpty();
        assertThat(nulls.path("range").path("completenessRule").path("rowLimit").isNull()).isTrue();
        assertThat(nulls.path("range").path("completenessRule").path("evidence").isNull()).isTrue();
    }

    private static ObjectMapper productionMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .registerModule(new JacksonPrecisionConfiguration().precisionModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static void assertNumber(JsonNode object, String field, long expected) {
        assertThat(object.path(field).isIntegralNumber()).as(field + " JSON integer token").isTrue();
        assertThat(object.path(field).longValue()).as(field + " exact int64 value").isEqualTo(expected);
    }

    @Test
    void examplesValidateAgainstDraft202012IncludingEveryPublishedResponseShape() throws IOException {
        JsonNode examples = JSON.readTree(CONTRACTS.resolve("download-task-examples.json").toFile());
        List<String> names = new ArrayList<>();
        for (JsonNode example : examples.path("examples")) {
            names.add(example.path("name").asText());
            assertThat(schema(example.path("schema").asText()).validate(example.path("value")))
                    .as(example.path("name").asText()).isEmpty();
        }
        assertThat(names).contains("singleSubmission", "rangeSubmission", "createdReceipt", "replayedReceipt",
                "retryRequest", "retryReceipt", "resumeRequest", "resumeReceipt", "queuedTask", "runningTask",
                "partialFailedTask", "succeededTask", "interruptedTask", "unplannedFailedTask",
                "splitParent", "splitChild", "emptyTaskPage", "emptyBatchPage", "availableCapabilities",
                "needsVerificationCapabilities", "unsupportedCapabilities", "invalidError", "notFoundError",
                "conflictError", "queueFullError", "persistenceError");
    }

    @Test
    void controlSchemaRequiresAClosedPositiveInt64Object() throws IOException {
        var control = schema("ControlRequest");
        assertThat(control.validate(JSON.readTree("{\"expectedVersion\":9223372036854775807}"))).isEmpty();
        for (String value : List.of("{}", "null", "[]", "{\"expectedVersion\":null}",
                "{\"expectedVersion\":0}", "{\"expectedVersion\":-1}", "{\"expectedVersion\":1.5}",
                "{\"expectedVersion\":\"1\"}", "{\"expectedVersion\":true}",
                "{\"expectedVersion\":9223372036854775808}", "{\"expectedVersion\":1,\"extra\":1}")) {
            assertThat(control.validate(JSON.readTree(value))).as(value).isNotEmpty();
        }
    }

    @Test
    void schemaRejectsLeakedInternalsWrongNullabilityFormatsAndNonStringParameters() throws IOException {
        ObjectNode task = (ObjectNode) example("queuedTask");
        assertInvalid("TaskResponse", task.deepCopy().put("activeRunId", "secret"));
        assertInvalid("TaskResponse", task.deepCopy().put("version", "1"));
        assertInvalid("TaskResponse", task.deepCopy().put("version", 0));
        assertInvalid("TaskResponse", task.deepCopy().put("createdAt", "2026-09-12T00:00:00+08:00"));
        assertInvalid("TaskResponse", task.deepCopy().put("taskId", "1-1-1-1-1"));
        assertInvalid("TaskResponse", task.deepCopy().put("status", "queued"));
        ObjectNode missingNull = task.deepCopy();
        missingNull.remove("startedAt");
        assertInvalid("TaskResponse", missingNull);
        ObjectNode badCounts = task.deepCopy();
        ((ObjectNode) badCounts.path("counts")).put("sourceRows", -1);
        assertInvalid("TaskResponse", badCounts);
        ObjectNode submission = (ObjectNode) example("singleSubmission");
        for (String invalid : List.of("null", "1", "true", "[]", "{}")) {
            ObjectNode badParams = submission.deepCopy();
            ((ObjectNode) badParams.path("params")).set("trade_date", JSON.readTree(invalid));
            assertInvalid("SubmissionRequest", badParams);
        }
    }

    @Test
    void storedErrorAndHttpErrorCoverTheEntireErrorCodeEnum() throws IOException {
        JsonNode contract = JSON.readTree(CONTRACTS.resolve("download-task.schema.json").toFile());
        List<String> codes = new ArrayList<>();
        contract.at("/$defs/ErrorCode/enum").forEach(code -> codes.add(code.asText()));
        assertThat(codes).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        for (ErrorCode code : ErrorCode.values()) {
            ObjectNode error = (ObjectNode) example("persistenceError");
            error.put("code", code.name()).put("retryable", code.retryable());
            assertThat(schema("Error").validate(error)).as(code.name()).isEmpty();
            ObjectNode stored = JSON.createObjectNode().put("code", code.name()).put("message", "Fixed message");
            assertThat(schema("StoredErrorResponse").validate(stored)).as(code.name()).isEmpty();
            assertInvalid("StoredErrorResponse", stored.put("retryable", code.retryable()));
        }
    }

    @Test
    void openApiTaskOperationsReferenceTheSameSchemas() throws IOException {
        JsonNode api = new ObjectMapper(new YAMLFactory())
                .readTree(CONTRACTS.resolve("openapi-v1.yaml").toFile());
        assertOperation(api, "/api/v1/download-tasks", "post", "202", "Receipt");
        assertOperation(api, "/api/v1/download-tasks", "post", "200", "Receipt");
        assertOperation(api, "/api/v1/download-tasks", "get", "200", "TaskPage");
        assertOperation(api, "/api/v1/download-tasks/{taskId}", "get", "200", "TaskResponse");
        assertOperation(api, "/api/v1/download-tasks/{taskId}/batches", "get", "200", "BatchPage");
        assertOperation(api, "/api/v1/download-tasks/{taskId}/retry", "post", "202", "Receipt");
        assertOperation(api, "/api/v1/download-tasks/{taskId}/resume", "post", "202", "Receipt");
        assertOperation(api, "/api/v1/data-sources/{pluginId}/apis/{apiName}/download-capabilities",
                "get", "200", "CapabilitiesResponse");
    }

    private static void assertOperation(JsonNode api, String path, String method, String status, String definition) {
        String component = api.path("paths").path(path).path(method).path("responses").path(status)
                .path("content").path("application/json").path("schema").path("$ref").asText();
        assertThat(component).as(method + " " + path + " " + status).startsWith("#/components/schemas/");
        assertThat(api.at(component.substring(1)).path("$ref").asText())
                .isEqualTo("./download-task.schema.json#/$defs/" + definition);
    }

    private static void assertInvalid(String definition, JsonNode value) throws IOException {
        assertThat(schema(definition).validate(value)).as(definition + ": " + value).isNotEmpty();
    }

    static JsonSchema schema(String definition) throws IOException {
        Path path = CONTRACTS.resolve("download-task.schema.json");
        assertThat(path).as("published download task schema").exists();
        ObjectNode root = (ObjectNode) JSON.readTree(path.toFile());
        assertThat(root.path("$defs").has(definition)).as("schema definition " + definition).isTrue();
        root.put("$ref", "#/$defs/" + definition);
        return JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(root,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
    }

    private static JsonNode example(String name) throws IOException {
        for (JsonNode item : JSON.readTree(CONTRACTS.resolve("download-task-examples.json").toFile()).path("examples")) {
            if (name.equals(item.path("name").asText())) return item.path("value").deepCopy();
        }
        throw new AssertionError("Missing contract example " + name);
    }

    private static Path contractsDirectory() {
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent()) {
            Path contracts = current.resolve("docs/contracts");
            if (Files.isDirectory(contracts)) return contracts;
        }
        throw new AssertionError("Cannot locate repository contracts");
    }
}
