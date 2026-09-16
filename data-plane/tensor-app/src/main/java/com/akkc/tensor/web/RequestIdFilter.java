package com.akkc.tensor.web;

import com.akkc.tensor.plugin.api.constant.RequestFields;
import com.akkc.tensor.plugin.api.model.RequestId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = RequestFields.REQUEST_ID;

    private static final String UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern CLIENT_REQUEST_ID = Pattern.compile(
            UUID_REGEX);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        RequestId requestId = requestId(request.getHeader(HEADER_NAME));
        String value = requestId.value().toString();
        MDC.put(MDC_KEY, value);
        try {
            response.setHeader(HEADER_NAME, value);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static RequestId requestId(String candidate) {
        return candidate != null && CLIENT_REQUEST_ID.matcher(candidate).matches()
                ? new RequestId(UUID.fromString(candidate))
                : RequestId.newId();
    }
}
