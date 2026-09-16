package com.akkc.tensor.config;

import com.akkc.tensor.plugin.api.constant.StringConstants;
import com.akkc.tensor.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class SpaWebConfiguration implements WebMvcConfigurer {
    private static final String ANY_ORIGIN = "*";
    private static final String API_PATH = "/api/v1/**";
    private static final String PATH_EXTENSION_SEPARATOR = ".";
    private static final String INDEX_FORWARD = "forward:/index.html";
    private static final String UI_FIRST_SEGMENT =
            "{first:^(?!api$|actuator$|assets$)[^.]+$}";

    private final String devAllowedOrigin;

    SpaWebConfiguration(
            @Value("${tensor.web.dev-allowed-origin:}") String devAllowedOrigin) {
        if (ANY_ORIGIN.equals(devAllowedOrigin)) {
            throw new IllegalArgumentException(
                    "tensor.web.dev-allowed-origin must be one exact origin");
        }
        this.devAllowedOrigin = devAllowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (devAllowedOrigin.isBlank()
                || devAllowedOrigin.contains(StringConstants.COMMA)
                || devAllowedOrigin.endsWith(StringConstants.SLASH)) {
            return;
        }
        registry.addMapping(API_PATH)
                .allowedOrigins(devAllowedOrigin)
                .allowedMethods(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.OPTIONS.name())
                .allowedHeaders(HttpHeaders.CONTENT_TYPE, RequestIdFilter.HEADER_NAME)
                .exposedHeaders(RequestIdFilter.HEADER_NAME, HttpHeaders.LOCATION)
                .allowCredentials(false);
    }

    @Controller
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static final class SpaForwardController {
        @GetMapping({
            "/",
            "/" + UI_FIRST_SEGMENT,
            "/" + UI_FIRST_SEGMENT + "/{*rest}"
        })
        String forward(
                @PathVariable(name = "rest", required = false) String rest,
                HttpServletRequest request) throws NoResourceFoundException {
            if (rest != null && rest.contains(PATH_EXTENSION_SEPARATOR)) {
                throw new NoResourceFoundException(
                        HttpMethod.valueOf(request.getMethod()), request.getRequestURI());
            }
            return INDEX_FORWARD;
        }
    }
}
