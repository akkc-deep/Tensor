package com.akkc.tensor.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.config.WebSecurityHeadersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ObservabilityTest {
    private static final String SECRET = "m09-t06-token-password-secret";

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/downloads", "/assets/app-deadbeef.js"})
    void writesAllSecurityHeaders(String path) throws Exception {
        MockHttpServletResponse response = filtered(path);

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(
                "default-src 'self'; base-uri 'none'; object-src 'none'; "
                        + "frame-ancestors 'none'; form-action 'self'; "
                        + "script-src 'self'; style-src 'self'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(response.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
    }

    @ParameterizedTest
    @CsvSource({
        "/index.html, no-store",
        "/assets/app-deadbeef.js, 'public, max-age=31536000, immutable'"
    })
    void appliesStaticCachePolicy(String path, String expected) throws Exception {
        assertThat(filtered(path).getHeader("Cache-Control")).isEqualTo(expected);
    }

    @Test
    void loadsOnlyTheApprovedEnvironmentAndActuatorDefaults() throws Exception {
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(source.getProperty("spring.datasource.url")).isEqualTo("${TENSOR_DB_URL}");
        assertThat(source.getProperty("spring.datasource.username"))
                .isEqualTo("${TENSOR_DB_USERNAME}");
        assertThat(source.getProperty("spring.datasource.password"))
                .isEqualTo("${TENSOR_DB_PASSWORD}");
        assertThat(source.getProperty("tensor.display-zone"))
                .isEqualTo("${TENSOR_DISPLAY_ZONE:Asia/Shanghai}");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.enabled"))
                .isEqualTo("${TENSOR_TUSHARE_ENABLED:true}");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.base-url"))
                .isEqualTo("${TENSOR_TUSHARE_BASE_URL:https://api.tushare.pro}");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.token"))
                .isEqualTo("${TENSOR_TUSHARE_TOKEN:}");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.connect-timeout"))
                .isEqualTo("5s");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.read-timeout"))
                .isEqualTo("120s");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.max-response-bytes"))
                .isEqualTo(67108864);
        assertThat(source.getProperty("tensor.persistence.batch-size")).isEqualTo(500);
        assertThat(source.getProperty("tensor.query.default-page-size")).isEqualTo(50);
        assertThat(List.of(
                source.getProperty("tensor.query.allowed-page-sizes[0]"),
                source.getProperty("tensor.query.allowed-page-sizes[1]"),
                source.getProperty("tensor.query.allowed-page-sizes[2]")))
                .containsExactly(20, 50, 100);
        assertThat(source.getProperty("management.endpoints.web.base-path"))
                .isEqualTo("/actuator");
        assertThat(source.getProperty("management.endpoints.web.discovery.enabled"))
                .isEqualTo(false);
        assertThat(source.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
        assertThat(source.getProperty("management.endpoint.health.probes.enabled")).isEqualTo(true);
        assertThat(source.getProperty("management.endpoint.health.show-components"))
                .isEqualTo("never");
        assertThat(source.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(source.getProperty("management.endpoint.env.show-values")).isEqualTo("never");
        assertThat(source.getProperty("management.endpoint.configprops.show-values"))
                .isEqualTo("never");
        assertThat(source.getSource().toString()).doesNotContain(SECRET);
    }

    private static MockHttpServletResponse filtered(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new WebSecurityHeadersConfiguration().securityHeadersFilter().getFilter()
                .doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });
        return response;
    }
}
