package com.akkc.tensor.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class ProductionWebConfigurationTest {
    private static final String DEV_ORIGIN = "http://127.0.0.1:5173";
    private static final String OTHER_ORIGIN = "https://other.example";
    private static final String INDEX_MARKER = "<div id=\"app\"></div>";
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";

    @Test
    void loadsTheApprovedDeliveryAndLifecycleSettings() throws Exception {
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(source.getProperty("tensor.web.dev-allowed-origin"))
                .isEqualTo("${TENSOR_DEV_CORS_ALLOWED_ORIGIN:}");
        assertThat(source.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(source.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
                .isEqualTo("70s");
        assertThat(source.getProperty("tensor.plugins.tushare-pro.read-timeout"))
                .isEqualTo("120s");
        assertThat(source.getProperty("management.endpoints.web.discovery.enabled"))
                .isEqualTo(false);
        assertThat(source.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
        assertThat(source.getProperty("management.endpoint.health.probes.enabled"))
                .isEqualTo(true);
        assertThat(source.getProperty("management.endpoint.health.show-details"))
                .isEqualTo("never");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/downloads", "/datasets", "/unknown", "/reports/daily"})
    void forwardsEligibleUiRoutesWithoutRedirecting(String path) throws Exception {
        try (WebFixture web = web("")) {
            MvcResult result = web.mockMvc().perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"))
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(header().string(
                            "Cache-Control", "/".equals(path) ? "no-store" : "no-cache"))
                    .andReturn();
            assertSecurityHeaders(result);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/downloads", "/datasets", "/reports/daily"})
    void forwardsEligibleUiHeadRoutesWithoutRedirecting(String path) throws Exception {
        try (WebFixture web = web("")) {
            web.mockMvc().perform(head(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"))
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(header().string(
                            "Cache-Control", "/".equals(path) ? "no-store" : "no-cache"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api", "/api/v1/missing", "/actuator/missing",
        "/assets/missing.js", "/missing.json", "/reports/file.csv"
    })
    void keepsReservedAndFileLikeRoutesAsRealNotFound(String path) throws Exception {
        try (WebFixture web = web("")) {
            MvcResult result = web.mockMvc().perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(""))
                    .andExpect(header().string("Cache-Control", cacheFor(path)))
                    .andReturn();
            assertThat(result.getResponse().getForwardedUrl()).isNull();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(INDEX_MARKER);
            assertSecurityHeaders(result);
        }
    }

    @Test
    void servesTheGeneratedEntryAndReferencedAssetWithFrozenCaches() throws Exception {
        try (WebFixture web = web("")) {
            MvcResult index = web.mockMvc().perform(get("/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn();
            assertThat(index.getResponse().getForwardedUrl()).isNull();
            assertThat(index.getResponse().getContentAsString()).contains(INDEX_MARKER);
            assertSecurityHeaders(index);

            MvcResult asset = web.mockMvc().perform(get(referencedAsset()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", IMMUTABLE))
                    .andReturn();
            assertThat(asset.getResponse().getForwardedUrl()).isNull();
            assertSecurityHeaders(asset);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void disablesCorsWhenTheDevelopmentOriginIsBlank(String origin) throws Exception {
        try (WebFixture web = web(origin)) {
            MvcResult actual = web.mockMvc().perform(get("/api/v1/probe")
                            .header("Origin", DEV_ORIGIN))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertNoCorsPermission(actual);

            MvcResult preflight = web.mockMvc().perform(preflight("GET", "Content-Type"))
                    .andExpect(status().isForbidden())
                    .andReturn();
            assertNoCorsPermission(preflight);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1:5173,https://other.example",
        "http://127.0.0.1:5173/",
        "*,https://other.example",
        "*/"
    })
    void disablesCorsForMalformedDevelopmentOrigin(String origin) throws Exception {
        try (WebFixture web = web(origin)) {
            MvcResult actual = web.mockMvc().perform(get("/api/v1/probe")
                            .header("Origin", DEV_ORIGIN))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertNoCorsPermission(actual);

            MvcResult preflight = web.mockMvc().perform(preflight("GET", "Content-Type"))
                    .andExpect(status().isForbidden())
                    .andReturn();
            assertNoCorsPermission(preflight);
        }
    }

    @Test
    void doesNotForwardUiRoutesForPost() throws Exception {
        try (WebFixture web = web("")) {
            MvcResult result = web.mockMvc().perform(post("/downloads"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Cache-Control", "no-cache"))
                    .andReturn();
            assertThat(result.getResponse().getForwardedUrl()).isNull();
            assertSecurityHeaders(result);
        }
    }

    @Test
    void allowsOnlyTheConfiguredApiOriginAndContract() throws Exception {
        try (WebFixture web = web(DEV_ORIGIN)) {
            for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                    get("/api/v1/probe"), post("/api/v1/probe")}) {
                MvcResult actual = web.mockMvc().perform(request.header("Origin", DEV_ORIGIN))
                        .andExpect(status().isNoContent())
                        .andExpect(header().string("Access-Control-Allow-Origin", DEV_ORIGIN))
                        .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id"))
                        .andExpect(header().string("X-Request-Id", "cors-test-request"))
                        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                        .andReturn();
                assertSecurityHeaders(actual);
            }

            for (String method : new String[] {"GET", "POST"}) {
                MvcResult preflight = web.mockMvc()
                        .perform(preflight(method, "Content-Type, X-Request-Id"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Access-Control-Allow-Origin", DEV_ORIGIN))
                        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                        .andReturn();
                String methods = preflight.getResponse()
                        .getHeader("Access-Control-Allow-Methods");
                String headers = preflight.getResponse()
                        .getHeader("Access-Control-Allow-Headers");
                assertThat(methods).contains("GET", "POST", "OPTIONS");
                assertThat(headers.toLowerCase(Locale.ROOT))
                        .contains("content-type", "x-request-id");
            }
        }
    }

    @Test
    void doesNotExtendCorsBeyondApi() throws Exception {
        try (WebFixture web = web(DEV_ORIGIN)) {
            MvcResult ui = web.mockMvc().perform(get("/downloads")
                            .header("Origin", DEV_ORIGIN))
                    .andExpect(status().isOk())
                    .andReturn();
            MvcResult asset = web.mockMvc().perform(get(referencedAsset())
                            .header("Origin", DEV_ORIGIN))
                    .andExpect(status().isOk())
                    .andReturn();
            MvcResult actuator = web.mockMvc().perform(get("/actuator/missing")
                            .header("Origin", DEV_ORIGIN))
                    .andExpect(status().isNotFound())
                    .andReturn();
            assertNoCorsPermission(ui);
            assertNoCorsPermission(asset);
            assertNoCorsPermission(actuator);
        }
    }

    @Test
    void rejectsUnapprovedCorsOriginMethodAndHeader() throws Exception {
        try (WebFixture web = web(DEV_ORIGIN)) {
            MvcResult origin = web.mockMvc().perform(get("/api/v1/probe")
                            .header("Origin", OTHER_ORIGIN))
                    .andExpect(status().isForbidden())
                    .andReturn();
            MvcResult method = web.mockMvc().perform(preflight("DELETE", "Content-Type"))
                    .andExpect(status().isForbidden())
                    .andReturn();
            MvcResult header = web.mockMvc().perform(preflight("GET", "Authorization"))
                    .andExpect(status().isForbidden())
                    .andReturn();
            assertNoCorsPermission(origin);
            assertNoCorsPermission(method);
            assertNoCorsPermission(header);
        }
    }

    @Test
    void rejectsWildcardDevelopmentOrigin() {
        assertThatThrownBy(() -> new SpaWebConfiguration("*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tensor.web.dev-allowed-origin must be one exact origin");
    }

    private static MockHttpServletRequestBuilder preflight(String method, String headers) {
        return options("/api/v1/probe")
                .header("Origin", DEV_ORIGIN)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", headers);
    }

    private static WebFixture web(String origin) {
        AnnotationConfigWebApplicationContext context =
                new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test-origin", Map.of("tensor.web.dev-allowed-origin", origin)));
        @RestController
        class ApiProbeController {
            @RequestMapping(
                    path = "/api/v1/probe",
                    method = {RequestMethod.GET, RequestMethod.POST})
            ResponseEntity<Void> probe() {
                return ResponseEntity.noContent()
                        .header("X-Request-Id", "cors-test-request")
                        .build();
            }
        }
        context.register(
                TestMvcConfiguration.class,
                SpaWebConfiguration.class,
                SpaWebConfiguration.SpaForwardController.class,
                WebSecurityHeadersConfiguration.class,
                ApiProbeController.class);
        try {
            context.refresh();
            FilterRegistrationBean<?> registration =
                    context.getBean("securityHeadersFilter", FilterRegistrationBean.class);
            Filter filter = registration.getFilter();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(filter)
                    .build();
            return new WebFixture(context, mockMvc);
        } catch (RuntimeException failure) {
            context.close();
            throw failure;
        }
    }

    private static String referencedAsset() throws Exception {
        String index = new ClassPathResource("static/index.html").getContentAsString(UTF_8);
        Matcher matcher = Pattern.compile("[\"'](/assets/[^\"']+[.](?:js|css))[\"']")
                .matcher(index);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String cacheFor(String path) {
        if (path.startsWith("/assets/")) {
            return IMMUTABLE;
        }
        if (path.startsWith("/api/")
                || "/actuator".equals(path)
                || path.startsWith("/actuator/")) {
            return "no-store";
        }
        return "no-cache";
    }

    private static void assertNoCorsPermission(MvcResult result) {
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(result.getResponse().getHeader("Access-Control-Expose-Headers")).isNull();
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isNull();
    }

    private static void assertSecurityHeaders(MvcResult result) {
        assertThat(result.getResponse().getHeader("Content-Security-Policy")).isNotBlank();
        assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(result.getResponse().getHeader("Referrer-Policy"))
                .isEqualTo("no-referrer");
        assertThat(result.getResponse().getHeader("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(result.getResponse().getHeader("Cross-Origin-Opener-Policy"))
                .isEqualTo("same-origin");
    }

    @EnableWebMvc
    static class TestMvcConfiguration implements WebMvcConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }
    }

    private record WebFixture(
            AnnotationConfigWebApplicationContext context,
            MockMvc mockMvc) implements AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
    }
}
