package com.akkc.tensor.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
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
    private static final String UI_FIRST_SEGMENT =
            "{first:^(?!api$|actuator$|assets$)[^.]+$}";

    private final String devAllowedOrigin;

    SpaWebConfiguration(
            @Value("${tensor.web.dev-allowed-origin:}") String devAllowedOrigin) {
        if ("*".equals(devAllowedOrigin)) {
            throw new IllegalArgumentException(
                    "tensor.web.dev-allowed-origin must be one exact origin");
        }
        this.devAllowedOrigin = devAllowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (devAllowedOrigin.isBlank()
                || devAllowedOrigin.contains(",")
                || devAllowedOrigin.endsWith("/")) {
            return;
        }
        registry.addMapping("/api/v1/**")
                .allowedOrigins(devAllowedOrigin)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Request-Id")
                .exposedHeaders("X-Request-Id")
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
            if (rest != null && rest.contains(".")) {
                throw new NoResourceFoundException(
                        HttpMethod.valueOf(request.getMethod()), request.getRequestURI());
            }
            return "forward:/index.html";
        }
    }
}
