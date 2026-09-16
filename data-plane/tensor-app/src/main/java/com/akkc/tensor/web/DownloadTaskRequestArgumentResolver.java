package com.akkc.tensor.web;

import com.akkc.tensor.core.download.task.*;
import com.akkc.tensor.plugin.api.constant.PaginationConstants;
import com.akkc.tensor.plugin.api.constant.RequestFields;
import com.akkc.tensor.plugin.api.constant.ValidationConstants;
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
        Set<String> allowed = type == Tasks.class ? Set.of(RequestFields.PAGE, RequestFields.PAGE_SIZE, RequestFields.PLUGIN_ID, RequestFields.API_NAME, RequestFields.STATUS, RequestFields.SUBMISSION_ID)
                : type == Batches.class ? Set.of(RequestFields.PAGE, RequestFields.PAGE_SIZE, RequestFields.STATUS, RequestFields.INCLUDE_SPLIT) : Set.of();
        for (var entry : request.getParameterMap().entrySet()) {
            if (!allowed.contains(entry.getKey())) throw invalid("query");
            if (entry.getValue().length != 1) throw invalid(entry.getKey());
        }
        if (type == NoQuery.class) return new NoQuery();
        if (type == Dataset.class) return new Dataset(DatasetKey.of(
                PluginId.of(identifier(RequestFields.PLUGIN_ID, path(request, RequestFields.PLUGIN_ID))),
                ApiName.of(identifier(RequestFields.API_NAME, path(request, RequestFields.API_NAME)))));
        if (type == TaskId.class) return new TaskId(uuid(RequestFields.TASK_ID, path(request, RequestFields.TASK_ID)));
        int page = integer(RequestFields.PAGE, value(request, RequestFields.PAGE, PaginationConstants.FIRST_PAGE_TEXT));
        String size = value(request, RequestFields.PAGE_SIZE, PaginationConstants.DEFAULT_TASK_PAGE_SIZE_TEXT);
        if (!PaginationConstants.PAGE_SIZE_VALUES.contains(size)) throw invalid(RequestFields.PAGE_SIZE);
        int pageSize = Integer.parseInt(size);
        if (type == Tasks.class) {
            String plugin = request.getParameter(RequestFields.PLUGIN_ID), api = request.getParameter(RequestFields.API_NAME);
            String submission = request.getParameter(RequestFields.SUBMISSION_ID);
            return new Tasks(page, pageSize, new DownloadTaskRepository.TaskFilter(
                    plugin == null ? null : identifier(RequestFields.PLUGIN_ID, plugin), api == null ? null : identifier(RequestFields.API_NAME, api),
                    status(DownloadTask.Status.class, request.getParameter(RequestFields.STATUS)),
                    submission == null ? null : uuid(RequestFields.SUBMISSION_ID, submission)));
        }
        String split = value(request, RequestFields.INCLUDE_SPLIT, Boolean.FALSE.toString());
        if (!split.equals(Boolean.TRUE.toString()) && !split.equals(Boolean.FALSE.toString())) throw invalid(RequestFields.INCLUDE_SPLIT);
        return new Batches(uuid(RequestFields.TASK_ID, path(request, RequestFields.TASK_ID)), page, pageSize,
                new DownloadTaskRepository.BatchFilter(status(DownloadBatch.Status.class, request.getParameter(RequestFields.STATUS)),
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
            if (number < PaginationConstants.FIRST_PAGE) throw invalid(field);
            return number;
        } catch (NumberFormatException invalid) { throw invalid(field); }
    }
    private static String identifier(String field, String value) {
        if (value == null || !value.matches(ValidationConstants.IDENTIFIER_REGEX)) throw invalid(field);
        return value;
    }
    private static UUID uuid(String field, String value) {
        if (value == null || !value.matches(ValidationConstants.UUID_REGEX))
            throw invalid(field);
        return UUID.fromString(value);
    }
    private static <E extends Enum<E>> E status(Class<E> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException invalid) { throw invalid(RequestFields.STATUS); }
    }
    private static DownloadBindingException invalid(String field) {
        return new DownloadBindingException(ErrorCode.PARAM_INVALID, List.of(new FieldErrorResponse(field, "has invalid value")));
    }
}
