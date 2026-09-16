package com.akkc.tensor.web;

import com.akkc.tensor.plugin.api.constant.RequestFields;
import com.akkc.tensor.plugin.api.constant.StringConstants;
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
    private static final String ARRAY_TS_CODE = "tsCode[]";

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
                        first(request, RequestFields.TRADE_DATE_FROM), first(request, RequestFields.TRADE_DATE_TO)),
                new DatasetRecordsRequest.DateRange(
                        first(request, RequestFields.ANN_DATE_FROM), first(request, RequestFields.ANN_DATE_TO)),
                new DatasetRecordsRequest.Pagination(
                        values(request, RequestFields.PAGE), values(request, RequestFields.PAGE_SIZE)),
                request.getParameterMap().keySet());
    }

    @SuppressWarnings("unchecked")
    private static DatasetPath path(HttpServletRequest request) {
        Map<String, String> variables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return new DatasetPath(variables.get(RequestFields.PLUGIN_ID), variables.get(RequestFields.API_NAME));
    }

    private static String joined(String[] values) {
        return values == null ? null : String.join(StringConstants.COMMA, values);
    }

    private static String tsCode(HttpServletRequest request) {
        String[] values = request.getParameterValues(RequestFields.TS_CODE);
        return joined(values == null ? request.getParameterValues(ARRAY_TS_CODE) : values);
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
