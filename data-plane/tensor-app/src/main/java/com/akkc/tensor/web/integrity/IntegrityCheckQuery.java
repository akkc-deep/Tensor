package com.akkc.tensor.web.integrity;

import com.akkc.tensor.core.integrity.IntegrityCheckRepository.IssueFilter;
import com.akkc.tensor.core.integrity.IntegrityCheckRepository.ResultFilter;
import com.akkc.tensor.core.integrity.IntegrityCheckRepository.TaskFilter;
import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.integrity.IntegrityIssue;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatus;
import com.akkc.tensor.plugin.api.integrity.IntegrityTaskStatus;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.springframework.util.MultiValueMap;

/** Strict HTTP query binding for integrity endpoints. */
public final class IntegrityCheckQuery {
    private IntegrityCheckQuery() {}

    public static void none(MultiValueMap<String, String> parameters) {
        values(parameters, Set.of());
    }

    public static PluginId pluginId(String value) {
        return parse(value, PluginId::new);
    }

    public static UUID checkId(String value) {
        return uuid(value);
    }

    public static Tasks tasks(MultiValueMap<String, String> parameters) {
        var values = values(parameters, Set.of("pluginId", "status", "submissionId", "page", "pageSize"));
        return new Tasks(new TaskFilter(parse(values.get("pluginId"), PluginId::new),
                enumeration(values.get("status"), IntegrityTaskStatus.class), uuid(values.get("submissionId"))),
                page(values.get("page"), 1, Integer.MAX_VALUE), page(values.get("pageSize"), 20, 100));
    }

    public static Results results(MultiValueMap<String, String> parameters) {
        var values = values(parameters, Set.of("symbol", "apiName", "overallStatus", "page", "pageSize"));
        return new Results(new ResultFilter(symbol(values.get("symbol")), parse(values.get("apiName"), ApiName::new),
                enumeration(values.get("overallStatus"), IntegrityStatus.class)),
                page(values.get("page"), 1, Integer.MAX_VALUE), page(values.get("pageSize"), 20, 100));
    }

    public static Issues issues(MultiValueMap<String, String> parameters) {
        var values = values(parameters, Set.of("resultId", "symbol", "apiName", "type", "status",
                "dateFrom", "dateTo", "page", "pageSize"));
        var from = date(values.get("dateFrom")); var to = date(values.get("dateTo"));
        if (from != null && to != null && from.isAfter(to)) throw invalid();
        return new Issues(new IssueFilter(uuid(values.get("resultId")), symbol(values.get("symbol")),
                parse(values.get("apiName"), ApiName::new), enumeration(values.get("type"), IntegrityIssue.Type.class),
                enumeration(values.get("status"), IntegrityStatus.class), from, to),
                page(values.get("page"), 1, Integer.MAX_VALUE), page(values.get("pageSize"), 20, 100));
    }

    public record Tasks(TaskFilter filter, int page, int pageSize) {}
    public record Results(ResultFilter filter, int page, int pageSize) {}
    public record Issues(IssueFilter filter, int page, int pageSize) {}

    private static Map<String, String> values(MultiValueMap<String, String> parameters, Set<String> accepted) {
        if (parameters == null || parameters.isEmpty()) return Map.of();
        var result = new HashMap<String, String>();
        parameters.forEach((key, entries) -> {
            if (!accepted.contains(key) || entries == null || entries.size() != 1
                    || entries.getFirst() == null || entries.getFirst().isEmpty()) throw invalid();
            result.put(key, entries.getFirst());
        });
        return result;
    }

    private static String symbol(String value) {
        if (value == null) return null;
        if (value.isBlank() || value.codePointCount(0, value.length()) > 255) throw invalid();
        return value;
    }

    private static UUID uuid(String value) {
        if (value == null) return null;
        if (!value.matches(ValidationConstants.UUID_REGEX)) throw invalid();
        return parse(value, UUID::fromString);
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        var date = parse(value, LocalDate::parse);
        if (date.getYear() < 1000) throw invalid();
        return date;
    }

    private static int page(String value, int fallback, int maximum) {
        if (value == null) return fallback;
        if (!value.matches("[1-9][0-9]*")) throw invalid();
        long parsed = parse(value, Long::parseLong);
        if (parsed > maximum) throw invalid();
        return (int) parsed;
    }

    private static <E extends Enum<E>> E enumeration(String value, Class<E> type) {
        return value == null ? null : parse(value, text -> Enum.valueOf(type, text));
    }

    private static <T> T parse(String value, Function<String, T> parser) {
        if (value == null) return null;
        try { return parser.apply(value); }
        catch (RuntimeException exception) { throw invalid(); }
    }

    public static BindingException invalid() {
        return new BindingException();
    }

    public static final class BindingException extends TensorException {
        private BindingException() {
            super(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.message());
        }
    }
}
