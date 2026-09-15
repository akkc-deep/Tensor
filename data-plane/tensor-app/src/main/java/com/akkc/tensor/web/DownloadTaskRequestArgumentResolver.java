package com.akkc.tensor.web;

import com.akkc.tensor.core.download.task.*;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.web.download.DownloadBindingException;
import com.akkc.tensor.web.dto.DownloadTaskQuery.*;
import com.akkc.tensor.web.dto.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.HandlerMapping;

/** Strict task-only query/path binding; does not change legacy dataset query rules. */
public final class DownloadTaskRequestArgumentResolver implements HandlerMethodArgumentResolver {
    private static final Set<Class<?>> TYPES = Set.of(Tasks.class, Batches.class, TaskId.class, Dataset.class, NoQuery.class);

    @Override
    public boolean supportsParameter(MethodParameter parameter) { return TYPES.contains(parameter.getParameterType()); }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var request = Objects.requireNonNull(webRequest.getNativeRequest(HttpServletRequest.class));
        var type = parameter.getParameterType();
        Set<String> allowed = type == Tasks.class ? Set.of("page", "pageSize", "pluginId", "apiName", "status", "submissionId")
                : type == Batches.class ? Set.of("page", "pageSize", "status", "includeSplit") : Set.of();
        for (var entry : request.getParameterMap().entrySet()) {
            if (!allowed.contains(entry.getKey())) throw invalid("query");
            if (entry.getValue().length != 1) throw invalid(entry.getKey());
        }
        if (type == NoQuery.class) return new NoQuery();
        if (type == Dataset.class) return new Dataset(DatasetKey.of(
                PluginId.of(identifier("pluginId", path(request, "pluginId"))),
                ApiName.of(identifier("apiName", path(request, "apiName")))));
        if (type == TaskId.class) return new TaskId(uuid("taskId", path(request, "taskId")));
        int page = integer("page", value(request, "page", "1"));
        String size = value(request, "pageSize", "20");
        if (!Set.of("20", "50", "100").contains(size)) throw invalid("pageSize");
        int pageSize = Integer.parseInt(size);
        if (type == Tasks.class) {
            String plugin = request.getParameter("pluginId"), api = request.getParameter("apiName");
            String submission = request.getParameter("submissionId");
            return new Tasks(page, pageSize, new DownloadTaskRepository.TaskFilter(
                    plugin == null ? null : identifier("pluginId", plugin), api == null ? null : identifier("apiName", api),
                    status(DownloadTask.Status.class, request.getParameter("status")),
                    submission == null ? null : uuid("submissionId", submission)));
        }
        String split = value(request, "includeSplit", "false");
        if (!split.equals("true") && !split.equals("false")) throw invalid("includeSplit");
        return new Batches(uuid("taskId", path(request, "taskId")), page, pageSize,
                new DownloadTaskRepository.BatchFilter(status(DownloadBatch.Status.class, request.getParameter("status")),
                        Boolean.parseBoolean(split)));
    }

    private static String value(HttpServletRequest request, String field, String fallback) {
        String value = request.getParameter(field); return value == null ? fallback : value;
    }
    @SuppressWarnings("unchecked")
    private static String path(HttpServletRequest request, String name) {
        var variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables == null ? null : variables.get(name);
    }
    private static int integer(String field, String value) {
        if (!value.matches("[0-9]+")) throw invalid(field);
        try {
            int number = Integer.parseInt(value);
            if (number < 1) throw invalid(field);
            return number;
        } catch (NumberFormatException invalid) { throw invalid(field); }
    }
    private static String identifier(String field, String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{1,63}")) throw invalid(field);
        return value;
    }
    private static UUID uuid(String field, String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw invalid(field);
        return UUID.fromString(value);
    }
    private static <E extends Enum<E>> E status(Class<E> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException invalid) { throw invalid("status"); }
    }
    private static DownloadBindingException invalid(String field) {
        return new DownloadBindingException(ErrorCode.PARAM_INVALID, List.of(new FieldErrorResponse(field, "has invalid value")));
    }
}
