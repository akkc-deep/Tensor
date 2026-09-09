package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Acceptance-only collector: nothing leaves the batch buffer before its terminal proof. */
public final class FixtureBatchSource {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final Set<ErrorCode> SOURCE_CODES = Set.of(ErrorCode.SOURCE_AUTH_FAILED, ErrorCode.SOURCE_PERMISSION_DENIED,
            ErrorCode.SOURCE_RATE_LIMITED, ErrorCode.SOURCE_UNAVAILABLE, ErrorCode.SOURCE_NETWORK_ERROR, ErrorCode.SOURCE_TIMEOUT,
            ErrorCode.SOURCE_PAYLOAD_INVALID, ErrorCode.SOURCE_TRUNCATED, ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
    private final URI sourceUrl;
    private final PluginId pluginId;
    private final ApiName apiName;
    private final List<String> fields;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public FixtureBatchSource(URI sourceUrl, DatasetDefinition definition) {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        if (!"http".equals(sourceUrl.getScheme()) || !"127.0.0.1".equals(sourceUrl.getHost())
                || sourceUrl.getPort() < 1 || sourceUrl.getPort() > 65535 || sourceUrl.getRawUserInfo() != null
                || sourceUrl.getRawQuery() != null || sourceUrl.getRawFragment() != null
                || !"".equals(sourceUrl.getRawPath())) throw new IllegalArgumentException("Fixture source must be a loopback HTTP origin");
        this.sourceUrl = sourceUrl;
        Objects.requireNonNull(definition, "definition");
        pluginId = definition.datasetKey().pluginId(); apiName = definition.datasetKey().apiName();
        fields = definition.columns().stream().sorted(java.util.Comparator.comparingInt(c -> c.displayOrder()))
                .map(c -> c.name()).toList();
    }

    public FetchResult fetch(FetchBatch batch, DownloadContext context) {
        Objects.requireNonNull(batch, "batch"); Objects.requireNonNull(context, "context");
        var rows = new ArrayList<List<Object>>();
        Integer total = null;
        for (int page = 1; ; page++) {
            JsonNode body = post("/batch", Map.of("apiName", apiName.value(), "params", batch.sourceParams(), "page", page), context);
            if (body.has("errorCode")) throw failure(code(body.get("errorCode")));
            if (!body.path("fields").isArray() || !body.path("data").isArray()
                    || !body.path("totalRows").isIntegralNumber() || !body.path("totalRows").canConvertToInt()
                    || body.path("totalRows").intValue() < 0 || !body.path("complete").isBoolean()
                    || !body.has("nextPage")) throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
            var pageFields = new ArrayList<String>();
            for (JsonNode field : body.get("fields")) {
                if (!field.isTextual()) throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
                pageFields.add(field.textValue());
            }
            if (!fields.equals(pageFields)) throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
            int declared = body.get("totalRows").intValue();
            if (total != null && total != declared) throw failure(ErrorCode.SOURCE_TRUNCATED);
            total = declared;
            for (JsonNode row : body.get("data")) {
                if (!row.isArray() || row.size() != fields.size()) throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
                var values = new ArrayList<Object>();
                for (JsonNode value : row) {
                    if (value.isContainerNode()) throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
                    values.add(JSON.convertValue(value, Object.class));
                }
                rows.add(values);
                if (rows.size() > total) throw failure(ErrorCode.SOURCE_TRUNCATED);
            }
            JsonNode next = body.get("nextPage");
            if (!next.isNull()) {
                if (!next.isIntegralNumber() || !next.canConvertToInt() || next.intValue() != page + 1)
                    throw failure(ErrorCode.SOURCE_TRUNCATED);
                if (body.get("complete").booleanValue()) throw failure(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
                if (body.has("unitFailures")) throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
                continue;
            }
            if (!body.get("complete").booleanValue()) throw failure(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED);
            if (rows.size() != total) throw failure(ErrorCode.SOURCE_TRUNCATED);
            var failures = unitFailures(body.get("unitFailures"), batch);
            try {
                return new FetchResult(new DownloadEnvelope(pluginId, apiName, batch.sourceParams(), fields,
                        rows.size(), rows, DownloadStatus.SUCCESS, null), failures);
            } catch (IllegalArgumentException e) { throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID); }
        }
    }

    public CalendarDecision confirmCalendar(CalendarScope scope, DownloadContext context) {
        Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(context, "context");
        JsonNode body;
        try {
            body = post("/calendar", Map.of("apiName", apiName.value(), "params", scope.publicParams(),
                    "dates", scope.dates().stream().sorted().map(LocalDate::toString).toList()), context);
        } catch (SourceException e) { throw new CalendarUnconfirmedException(); }
        try {
            JsonNode calendars = body.get("calendars");
            if (body.has("errorCode") || calendars == null || !calendars.isObject()) throw new IllegalArgumentException();
            var values = new HashMap<String, Map<LocalDate, Boolean>>();
            var entries = calendars.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (!entry.getValue().isObject()) throw new IllegalArgumentException();
                var days = new HashMap<LocalDate, Boolean>();
                var dates = entry.getValue().fields();
                while (dates.hasNext()) {
                    var date = dates.next();
                    if (!date.getValue().isBoolean()) throw new IllegalArgumentException();
                    days.put(LocalDate.parse(date.getKey()), date.getValue().booleanValue());
                }
                values.put(entry.getKey(), days);
            }
            return new CalendarDecision(scope, values);
        } catch (IllegalArgumentException | java.time.DateTimeException e) { throw new CalendarUnconfirmedException(); }
    }

    private List<FetchResult.UnitFailure> unitFailures(JsonNode node, FetchBatch batch) {
        if (node == null) return List.of();
        if (!node.isArray()) throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
        var failures = new ArrayList<FetchResult.UnitFailure>();
        try {
            for (JsonNode item : node) {
                JsonNode selector = item.get("selector");
                if (selector == null || !selector.isObject()) throw new IllegalArgumentException();
                for (String key : List.of("targetType", "targetValue", "timeType", "timeValue"))
                    if (!selector.path(key).isTextual()) throw new IllegalArgumentException();
                if (!item.path("errorMessage").isTextual()) throw new IllegalArgumentException();
                var scope = new RecoverySelector(RecoverySelector.TargetType.valueOf(selector.get("targetType").textValue()),
                        selector.get("targetValue").textValue(), RecoverySelector.TimeType.valueOf(selector.get("timeType").textValue()),
                        selector.get("timeValue").textValue());
                if (scope.targetType() != RecoverySelector.TargetType.STOCK
                        || batch.recoveryPolicy().mode() != RecoveryPolicy.Mode.STOCK_TIME
                        || scope.timeType() != batch.recoveryPolicy().unitTimeType()) throw new IllegalArgumentException();
                var params = batch.sourceParams();
                if (params.containsKey("ts_code") && !params.get("ts_code").equals(scope.targetValue())) throw new IllegalArgumentException();
                String compact = scope.timeValue().replace("-", "");
                if (params.containsKey("ann_date") && !params.get("ann_date").equals(compact)
                        || params.containsKey("trade_date") && !params.get("trade_date").equals(compact)
                        || params.containsKey("month") && !params.get("month").equals(compact)) throw new IllegalArgumentException();
                if (params.containsKey("start_date")) {
                    String[] ends = compact.split("/");
                    if (ends[0].compareTo((String) params.get("start_date")) < 0
                            || ends[ends.length-1].compareTo((String) params.get("end_date")) > 0) throw new IllegalArgumentException();
                }
                ErrorCode code = code(item.get("errorCode"));
                failures.add(new FetchResult.UnitFailure(scope, code, safeMessage(code)));
            }
        } catch (IllegalArgumentException e) { throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID); }
        return failures;
    }

    private JsonNode post(String path, Object body, DownloadContext context) {
        context.checkServerState();
        HttpResponse<String> response;
        try {
            var request = HttpRequest.newBuilder(sourceUrl.resolve(path)).timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) { throw failure(ErrorCode.SOURCE_TIMEOUT);
        } catch (IOException e) { throw failure(ErrorCode.SOURCE_NETWORK_ERROR);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw failure(ErrorCode.SOURCE_NETWORK_ERROR);
        } finally { context.checkServerState(); }
        int status = response.statusCode();
        if (status < 200 || status >= 300) throw failure(switch (status) {
            case 401 -> ErrorCode.SOURCE_AUTH_FAILED; case 403 -> ErrorCode.SOURCE_PERMISSION_DENIED;
            case 429 -> ErrorCode.SOURCE_RATE_LIMITED; default -> ErrorCode.SOURCE_UNAVAILABLE;
        });
        try {
            JsonNode json = JSON.readTree(response.body());
            if (json == null || !json.isObject()) throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
            return json;
        } catch (IOException e) { throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID); }
    }
    private static ErrorCode code(JsonNode node) {
        try {
            if (node == null || !node.isTextual()) throw new IllegalArgumentException();
            ErrorCode code = ErrorCode.valueOf(node.textValue());
            if (!SOURCE_CODES.contains(code)) throw new IllegalArgumentException();
            return code;
        } catch (IllegalArgumentException e) { throw failure(ErrorCode.SOURCE_PAYLOAD_INVALID); }
    }
    private static String safeMessage(ErrorCode code) { return "Controlled fixture source failure: " + code.name(); }
    private static SourceException failure(ErrorCode code) { return new SourceException(code, safeMessage(code)); }
}
