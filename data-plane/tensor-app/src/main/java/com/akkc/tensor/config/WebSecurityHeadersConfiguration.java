package com.akkc.tensor.config;

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
                response.setHeader("Content-Security-Policy", CSP);
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("Referrer-Policy", "no-referrer");
                response.setHeader(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()");
                response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
                response.setHeader("Cache-Control", cacheControl(path(request)));
                chain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setName("tensorSecurityHeadersFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context.isEmpty() ? uri : uri.substring(context.length());
    }

    private static String cacheControl(String path) {
        if (path.startsWith("/assets/")) {
            return "public, max-age=31536000, immutable";
        }
        if ("/".equals(path)
                || "/index.html".equals(path)
                || path.startsWith("/api/")
                || "/actuator".equals(path)
                || path.startsWith("/actuator/")) {
            return "no-store";
        }
        return "no-cache";
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
