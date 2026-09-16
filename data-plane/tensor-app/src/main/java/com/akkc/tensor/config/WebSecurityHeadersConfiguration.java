package com.akkc.tensor.config;

import com.akkc.tensor.plugin.api.constant.StringConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class WebSecurityHeadersConfiguration {
    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String CONTENT_TYPE_OPTIONS_VALUE = "nosniff";
    private static final String FRAME_OPTIONS = "X-Frame-Options";
    private static final String FRAME_OPTIONS_VALUE = "DENY";
    private static final String REFERRER_POLICY = "Referrer-Policy";
    private static final String REFERRER_POLICY_VALUE = "no-referrer";
    private static final String PERMISSIONS_POLICY = "Permissions-Policy";
    private static final String PERMISSIONS_POLICY_VALUE = "camera=(), microphone=(), geolocation=()";
    private static final String OPENER_POLICY = "Cross-Origin-Opener-Policy";
    private static final String OPENER_POLICY_VALUE = "same-origin";
    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String FILTER_NAME = "tensorSecurityHeadersFilter";
    private static final String ASSET_PATH = "/assets/";
    private static final String ASSET_CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final String INDEX_PATH = "/index.html";
    private static final String API_PATH = "/api/";
    private static final String ACTUATOR_PATH = "/actuator";
    private static final String ACTUATOR_PREFIX = "/actuator/";
    private static final String NO_STORE = "no-store";
    private static final String NO_CACHE = "no-cache";
    private static final String CSP =
            "default-src 'self'; base-uri 'none'; object-src 'none'; "
                    + "frame-ancestors 'none'; form-action 'self'; "
                    + "script-src 'self'; style-src 'self'; "
                    + "img-src 'self' data:; font-src 'self'; connect-src 'self'";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> securityHeadersFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                response.setHeader(CONTENT_SECURITY_POLICY, CSP);
                response.setHeader(CONTENT_TYPE_OPTIONS, CONTENT_TYPE_OPTIONS_VALUE);
                response.setHeader(FRAME_OPTIONS, FRAME_OPTIONS_VALUE);
                response.setHeader(REFERRER_POLICY, REFERRER_POLICY_VALUE);
                response.setHeader(
                        PERMISSIONS_POLICY,
                        PERMISSIONS_POLICY_VALUE);
                response.setHeader(OPENER_POLICY, OPENER_POLICY_VALUE);
                response.setHeader(CACHE_CONTROL, cacheControl(path(request)));
                chain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setName(FILTER_NAME);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context.isEmpty() ? uri : uri.substring(context.length());
    }

    private static String cacheControl(String path) {
        if (path.startsWith(ASSET_PATH)) {
            return ASSET_CACHE_CONTROL;
        }
        if (StringConstants.SLASH.equals(path)
                || INDEX_PATH.equals(path)
                || path.startsWith(API_PATH)
                || ACTUATOR_PATH.equals(path)
                || path.startsWith(ACTUATOR_PREFIX)) {
            return NO_STORE;
        }
        return NO_CACHE;
    }

    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    private static final class MissingResourceHandler {
        @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
        ResponseEntity<Void> notFound() {
            return ResponseEntity.notFound().build();
        }
    }
}
