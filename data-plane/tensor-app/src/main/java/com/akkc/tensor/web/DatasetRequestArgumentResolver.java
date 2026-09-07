package com.akkc.tensor.web;

import com.akkc.tensor.web.dto.DatasetPath;
import com.akkc.tensor.web.dto.DatasetRecordsRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

public final class DatasetRequestArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return type == DatasetPath.class || type == DatasetRecordsRequest.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        DatasetPath path = path(request);
        if (parameter.getParameterType() == DatasetPath.class) {
            return path;
        }
        return new DatasetRecordsRequest(
                path,
                tsCode(request),
                new DatasetRecordsRequest.DateRange(
                        first(request, "tradeDateFrom"), first(request, "tradeDateTo")),
                new DatasetRecordsRequest.DateRange(
                        first(request, "annDateFrom"), first(request, "annDateTo")),
                new DatasetRecordsRequest.Pagination(
                        values(request, "page"), values(request, "pageSize")),
                request.getParameterMap().keySet());
    }

    @SuppressWarnings("unchecked")
    private static DatasetPath path(HttpServletRequest request) {
        Map<String, String> variables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return new DatasetPath(variables.get("pluginId"), variables.get("apiName"));
    }

    private static String joined(String[] values) {
        return values == null ? null : String.join(",", values);
    }

    private static String tsCode(HttpServletRequest request) {
        String[] values = request.getParameterValues("tsCode");
        return joined(values == null ? request.getParameterValues("tsCode[]") : values);
    }

    private static String first(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null ? null : values[0];
    }

    private static List<String> values(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null ? null : List.of(values);
    }
}
