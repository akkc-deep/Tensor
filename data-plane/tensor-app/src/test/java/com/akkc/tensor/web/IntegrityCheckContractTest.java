package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.core.integrity.IntegrityCheckRepository.*;
import com.akkc.tensor.core.integrity.IntegrityCheckJson;
import com.akkc.tensor.core.integrity.IntegrityCheckService;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.web.dto.IntegrityCheckResponses.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class IntegrityCheckContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path CONTRACTS = contractsDirectory();

    @Test
    void receiptUsesTheProductionMapperAndOnlyPublicFields() throws IOException {
        var task = savedTask();
        var receipt = serialized(Receipt.from("44444444-4444-4444-8444-444444444444", task));
        assertValid("Receipt", receipt);
        assertThat(receipt.path("pluginId").asText()).isEqualTo("fixture");
        assertThat(receipt.path("plannedUnits").isInt()).isTrue();
        assertThat(receipt.path("status").asText()).isEqualTo("RUNNING");
        assertThat(receipt.toString()).doesNotContain("private-request-hash", "definitionSnapshot");
    }

    @Test
    void productionMappingsPreserveExactCountsSavedReportsAndExplicitNulls() throws IOException {
        var task = savedTask();
        var counts = new EnumMap<IntegrityStatus, Long>(IntegrityStatus.class);
        for (var status : IntegrityStatus.values()) counts.put(status, status == IntegrityStatus.UNKNOWN ? Long.MAX_VALUE : 0L);
        var detail = serialized(Detail.from(new Progress(task, Long.MAX_VALUE, 0L, 0L, counts, IntegrityStatus.UNKNOWN)));
        assertValid("Detail", detail);
        assertThat(detail.path("completedUnits").isTextual()).isTrue();
        assertThat(detail.path("completedUnits").asText()).isEqualTo("9223372036854775807");
        assertThat(detail.path("statusCounts").path("PASS").asText()).isEqualTo("0");
        assertThat(detail.path("finishedAt").isNull()).isTrue();
        assertThat(detail.path("originalRequest")).isEqualTo(task.originalRequest());
        assertThat(detail.path("scope")).isEqualTo(task.normalizedScope());
        assertValid("TaskSummary", serialized(TaskSummary.from(task)));

        for (var name : List.of("failedDataResult", "unknownResult", "notApplicableResult", "incompleteResult")) {
            var example = example(name);
            var saved = new ResultRecord(UUID.fromString(example.path("resultId").asText()), task.checkId(),
                    "private-unit-key", PluginId.of("fixture"), ApiName.of("fixture_daily"), "000001.SZ",
                    "a".repeat(64), JSON.createObjectNode(), example.path("report"));
            var result = serialized(Result.from(saved));
            assertValid("Result", result);
            assertThat(result.path("report")).isEqualTo(example.path("report"));
            assertThat(result.toString()).doesNotContain("unitKey", "definitionSnapshot");
            var page = serialized(com.akkc.tensor.web.dto.IntegrityCheckResponses.Page.from(
                    new com.akkc.tensor.core.integrity.IntegrityCheckRepository.Page<>(1, 17, Long.MAX_VALUE, List.of(saved)), Result::from));
            assertValid("ResultPage", page);
            assertThat(page.path("total").asText()).isEqualTo("9223372036854775807");
        }
        var issue = example("undatedIssue");
        var savedIssue = new IssueRecord(Long.MAX_VALUE, UUID.fromString(issue.path("resultId").asText()),
                "core.business-key", "1", issue.path("issue"));
        var actual = serialized(Issue.from(savedIssue));
        assertValid("Issue", actual);
        assertThat(actual.path("issueId").isTextual()).isTrue();
        assertThat(actual.path("issue")).isEqualTo(issue.path("issue"));
        assertValid("IssuePage", serialized(com.akkc.tensor.web.dto.IntegrityCheckResponses.Page.from(
                new com.akkc.tensor.core.integrity.IntegrityCheckRepository.Page<>(1, 20, 1L, List.of(savedIssue)), Issue::from)));
        assertValid("TaskPage", serialized(com.akkc.tensor.web.dto.IntegrityCheckResponses.Page.from(
                new com.akkc.tensor.core.integrity.IntegrityCheckRepository.Page<>(1, 20, 1L, List.of(task)), TaskSummary::from)));

        var codec = new IntegrityCheckJson();
        var descriptor = codec.readDescriptor(example("failedDataResult").path("report").path("descriptor"));
        var descriptorJson = serialized(Descriptor.from(descriptor));
        assertValid("Descriptor", descriptorJson);
        assertThat(descriptorJson.path("datasetKey").path("pluginId").asText()).isEqualTo("fixture");
        var capability = Capability.from(new IntegrityCheckService.Capability(PluginId.of("fixture"), false,
                "Plugin disabled", null, IntegrityCheckService.Settings.defaults(), List.of()), null);
        var capabilityJson = serialized(capability);
        assertValid("Capability", capabilityJson);
        assertThat(capabilityJson.path("limits").path("unitTimeoutSeconds").isTextual()).isTrue();
        assertThat(capabilityJson.path("limits").path("unitTimeoutSeconds").asText()).isEqualTo("120");
    }

    private static TaskRecord savedTask() throws IOException {
        var detail = example("runningDetail");
        return new TaskRecord(UUID.fromString(detail.path("checkId").asText()),
                UUID.fromString(detail.path("submissionId").asText()), PluginId.of("fixture"),
                "private-request-hash", detail.path("capabilityHash").asText(), detail.path("originalRequest"),
                detail.path("scope"), JSON.createArrayNode(), IntegrityTaskStatus.RUNNING, 1,
                Instant.parse("2026-09-17T00:00:00Z"), Instant.parse("2026-09-17T00:00:00Z"),
                Instant.parse("2026-09-17T00:00:00Z"), null, null, null);
    }

    private static JsonNode serialized(Object value) throws IOException {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .registerModule(new JacksonPrecisionConfiguration().precisionModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper.readTree(mapper.writeValueAsBytes(value));
    }

    @Test
    void examplesCoverThePublishedResponseShapesAndValidate() throws IOException {
        assertThat(CONTRACTS.resolve("integrity-check.schema.json")).exists();
        var names = new ArrayList<String>();
        for (var example : JSON.readTree(CONTRACTS.resolve("integrity-check-examples.json").toFile()).path("examples")) {
            names.add(example.path("name").asText());
            assertValid(example.path("schema").asText(), example.path("value"));
        }
        assertThat(names).contains("submission", "availableCapability", "unavailableCapability", "createdReceipt",
                "replayedReceipt", "runningDetail", "failedDataResult", "unknownResult", "notApplicableResult",
                "incompleteResult", "undatedIssue", "emptyTaskPage", "emptyResultPage", "emptyIssuePage");
    }

    @Test
    void schemaRejectsLostPrecisionUnknownFieldsAndMissingNulls() throws IOException {
        var detail = example("runningDetail");
        assertInvalid("Detail", detail.deepCopy().put("completedUnits", 1));
        assertInvalid("Detail", detail.deepCopy().put("requestHash", "private"));
        assertInvalid("Detail", detail.deepCopy().put("checkId", "1-1-1-1-1"));
        assertInvalid("Detail", detail.deepCopy().put("status", "PASS"));
        var missing = detail.deepCopy(); missing.remove("finishedAt");
        assertInvalid("Detail", missing);
        var stats = (ObjectNode) example("failedDataResult").path("report").path("statistics");
        for (var rate : List.of("1.000001", "-0.100000", "0.95", "NaN"))
            assertInvalid("Statistics", stats.deepCopy().put("coverageRate", rate));
        assertInvalid("Statistics", stats.deepCopy().put("actualCount", "01"));
        assertInvalid("Statistics", stats.deepCopy().put("actualCount", "-1"));
        assertInvalid("Statistics", stats.deepCopy().put("actualCount", 9007199254740992L));
        assertValid("Statistics", stats.deepCopy().put("actualCount", "9223372036854775807"));
        assertValid("Statistics", stats.deepCopy().putNull("expectedCount").putNull("coverageRate"));
        var request = example("submission");
        assertInvalid("Request", request.deepCopy().put("extra", true));
        assertInvalid("Request", request.deepCopy().put("startDate", "2024-02-30"));
        assertInvalid("Request", request.deepCopy().put("startDate", "0999-01-01"));
        assertInvalid("Request", request.deepCopy().set("apiNames", JSON.createArrayNode()));
        var omitted = request.deepCopy(); omitted.remove("apiNames"); assertValid("Request", omitted);
    }

    @Test
    void openApiOperationsAndEnumsMatchIndependentSchemas() throws IOException {
        var api = new ObjectMapper(new YAMLFactory()).readTree(CONTRACTS.resolve("openapi-v1.yaml").toFile());
        String checks = "/api/v1/integrity-checks";
        assertOperation(api, "/api/v1/data-sources/{pluginId}/integrity-capabilities", "get", "200", "Capability");
        assertOperation(api, checks, "post", "202", "Receipt");
        assertOperation(api, checks, "post", "200", "Receipt");
        assertOperation(api, checks, "get", "200", "TaskPage");
        assertOperation(api, checks + "/{checkId}", "get", "200", "Detail");
        assertOperation(api, checks + "/{checkId}/results", "get", "200", "ResultPage");
        assertOperation(api, checks + "/{checkId}/issues", "get", "200", "IssuePage");
        for (var status : List.of("202", "200")) assertThat(api.path("paths").path(checks).path("post")
                .path("responses").path(status).path("headers").has("Location")).isTrue();
        assertQueryNames(api, checks, "page", "pageSize", "pluginId", "status", "submissionId");
        assertQueryNames(api, checks + "/{checkId}/results", "page", "pageSize", "symbol", "apiName", "overallStatus");
        assertQueryNames(api, checks + "/{checkId}/issues", "page", "pageSize", "resultId", "symbol", "apiName", "type", "status", "dateFrom", "dateTo");
        var defs = JSON.readTree(CONTRACTS.resolve("integrity-check.schema.json").toFile()).path("$defs");
        assertEnum(defs, "Status", IntegrityStatus.values());
        assertEnum(defs, "TaskStatus", IntegrityTaskStatus.values());
        assertEnum(defs, "UnitStatus", IntegrityUnitStatus.values());
        assertEnum(defs, "IssueType", IntegrityIssue.Type.values());
    }

    private static void assertQueryNames(JsonNode api, String path, String... expected) {
        var names = new ArrayList<String>();
        for (var parameter : api.path("paths").path(path).path("get").path("parameters")) {
            if (parameter.has("$ref")) parameter = api.at(parameter.path("$ref").asText().substring(1));
            if (parameter.path("in").asText().equals("query")) names.add(parameter.path("name").asText());
        }
        assertThat(names).containsExactlyInAnyOrder(expected);
    }

    private static void assertEnum(JsonNode defs, String name, Enum<?>[] values) {
        var actual = new ArrayList<String>(); defs.path(name).path("enum").forEach(v -> actual.add(v.asText()));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(Arrays.stream(values).map(Enum::name).toList());
    }

    private static void assertOperation(JsonNode api, String path, String method, String status, String definition) {
        var response = api.path("paths").path(path).path(method).path("responses").path(status);
        String component = response.path("content").path("application/json").path("schema").path("$ref").asText();
        assertThat(component).startsWith("#/components/schemas/");
        assertThat(api.at(component.substring(1)).path("$ref").asText())
                .isEqualTo("./integrity-check.schema.json#/$defs/" + definition);
        assertThat(response.path("headers").has("X-Request-Id")).isTrue();
    }

    static void assertValid(String definition, JsonNode value) throws IOException {
        assertThat(schema(definition).validate(value)).as(definition).isEmpty();
    }

    private static void assertInvalid(String definition, JsonNode value) throws IOException {
        assertThat(schema(definition).validate(value)).as(definition + ": " + value).isNotEmpty();
    }

    private static JsonSchema schema(String definition) throws IOException {
        Path path = CONTRACTS.resolve("integrity-check.schema.json");
        ObjectNode root = (ObjectNode) JSON.readTree(path.toFile());
        assertThat(root.path("$defs").has(definition)).isTrue();
        root.put("$id", path.toUri().toString());
        root.put("$ref", "#/$defs/" + definition);
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
    }

    private static ObjectNode example(String name) throws IOException {
        for (var example : JSON.readTree(CONTRACTS.resolve("integrity-check-examples.json").toFile()).path("examples"))
            if (name.equals(example.path("name").asText())) return example.path("value").deepCopy();
        throw new AssertionError("Missing example " + name);
    }

    private static Path contractsDirectory() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent())
            if (Files.isDirectory(path.resolve("docs/contracts"))) return path.resolve("docs/contracts");
        throw new AssertionError("Missing contracts directory");
    }
}
