# M13-T03 Production Web Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit SPA history fallback, fail-closed development CORS, frozen cache behavior, and graceful shutdown to the packaged `tensor-app` without changing its API, security header, frontend, or packaging contracts.

**Architecture:** `SpaWebConfiguration` is a Servlet-only MVC configuration with one verified PathPattern-based forward controller and one optional exact-origin CORS mapping. `application.yml` owns the environment and lifecycle values; one isolated Spring Test/MockMvc contract exercises the real configuration, generated classpath resources, existing security filter, and YAML without starting the full database-backed application.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Framework 6.2.19 MVC, JUnit 5, Spring Test MockMvc, AssertJ, Maven, YAML.

**Design:** `docs/task-designs/M13-T03-design.md`

## Global Constraints

- Modify/create exactly the three files named by M13-T03: one YAML modification and two Java additions.
- Keep `WebSecurityHeadersConfiguration`, all controllers, POMs, frontend files, business modules, migrations, runbooks, and proxy configuration unchanged.
- Production CORS is off when `${TENSOR_DEV_CORS_ALLOWED_ORIGIN:}` resolves to empty or blank; a configured value is one exact non-wildcard origin and applies only to `/api/v1/**`.
- Allow only `GET`, `POST`, and preflight `OPTIONS`; allow request headers `Content-Type` and `X-Request-Id`; expose `X-Request-Id`; set `allowCredentials(false)`.
- Forward only GET/HEAD extensionless UI paths whose first segment is not exactly `api`, `actuator`, or `assets`; keep reserved, file-like, and non-GET/HEAD paths out of SPA fallback.
- Preserve existing cache rules: `/` and `/index.html` are `no-store`, UI fallbacks are `no-cache`, `/api/**` and Actuator are `no-store`, and `/assets/**` are `public, max-age=31536000, immutable`.
- Add `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=70s`; retain the ordering `120s` Tushare read `< 130s` Axios `<=` production proxy response timeout.
- Do not add an application request timeout, retry, redirect, copied index body, wildcard route, origin pattern, credentialed CORS, dependency, profile, or generated artifact.
- Do not commit the RED state. The single implementation commit must be `feat(app): configure production web delivery` and contain exactly the three implementation files.
- Preserve the user's existing `.idea/misc.xml` and all Maven `target/` directories; never stage them.

---

### Task 1: Implement and verify the complete production Web contract

**Files:**

- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java`
- Modify: `data-plane/tensor-app/src/main/resources/application.yml`

**Interfaces:**

- Consumes: classpath `static/index.html` and its referenced `/assets/<hash>.js|css` from M13-T02; `WebSecurityHeadersConfiguration.securityHeadersFilter()`; existing `application.yml` Tushare and Actuator values.
- Produces: property `tensor.web.dev-allowed-origin`; exact CORS mapping `/api/v1/**`; `SpaWebConfiguration.SpaForwardController` GET/HEAD mappings returning `forward:/index.html`; Boot properties `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=70s`.

- [ ] **Step 1: Confirm the protected baseline and target paths**

Run from the repository root:

```bash
git status --short
git diff -- \
  data-plane/tensor-app/src/main/resources/application.yml \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java
test ! -e data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java
test ! -e data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java
```

Expected: only the established `.idea/misc.xml` and Maven `target/` noise is present; the three target paths have no overlapping user edits; both new Java paths are absent. Stop and resolve any overlap rather than overwriting it.

- [ ] **Step 2: Write the complete failing production Web test**

Create `data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java` with this complete structure and behavior. Keep helpers in this test file; do not add production inspection APIs or a test dependency.

```java
package com.akkc.tensor.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
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
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context, "tensor.web.dev-allowed-origin=" + origin);
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

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestMvcConfiguration implements WebMvcConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }
    }

    @RestController
    static class ApiProbeController {
        @RequestMapping(
                path = "/api/v1/probe",
                method = {RequestMethod.GET, RequestMethod.POST})
        ResponseEntity<Void> probe() {
            return ResponseEntity.noContent()
                    .header("X-Request-Id", "cors-test-request")
                    .build();
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
```

- [ ] **Step 3: Run the focused test and verify the strict RED**

Run:

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am \
  -Dtest=ProductionWebConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the pinned frontend pipeline completes 20 files / 120 tests and copies generated resources; upstream Java modules with no matching test continue; `tensor-app:testCompile` then fails only because `SpaWebConfiguration` and its nested controller do not exist. A dependency, frontend, unrelated compile, database, Docker, network, or wrong-test-selection failure is not a valid RED. Do not commit this state.

- [ ] **Step 4: Add the minimal Servlet SPA and CORS configuration**

Create `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java`:

```java
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
        if (devAllowedOrigin.isBlank()) {
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
```

Keep the constructor package-private so the same-package contract can verify wildcard rejection without adding a public configuration API. Do not trim or normalize `devAllowedOrigin`; blank values disable CORS and nonblank values must match the browser `Origin` exactly.

- [ ] **Step 5: Add the exact environment and graceful shutdown values**

Modify `data-plane/tensor-app/src/main/resources/application.yml` with only these insertions:

```yaml
spring:
  datasource:
    url: ${TENSOR_DB_URL}
    username: ${TENSOR_DB_USERNAME}
    password: ${TENSOR_DB_PASSWORD}
  flyway:
    enabled: true
  lifecycle:
    timeout-per-shutdown-phase: 70s

server:
  shutdown: graceful

tensor:
  web:
    dev-allowed-origin: ${TENSOR_DEV_CORS_ALLOWED_ORIGIN:}
```

Leave every existing sibling under `tensor`, `management`, datasource, and Flyway unchanged. Do not quote or supply a nonempty default for the environment placeholder.

- [ ] **Step 6: Run the focused GREEN test and fix only contract failures**

Run the same focused command:

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am \
  -Dtest=ProductionWebConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: frontend 20 files / 120 tests and Vite build pass; generated static resources copy; all `ProductionWebConfigurationTest` invocations pass with no database, Docker, server port, or network access. The route log may report handled 404 exceptions but must contain no stack trace, ERROR, credential, or test failure.

Do not change route, status, cache, CORS, health, lifecycle, or wildcard assertions to obtain GREEN.

- [ ] **Step 7: Prove the route and CORS guards are behaviorally necessary**

Make each temporary mutation independently, run the focused command, observe the named failure, and restore the file before the next mutation:

1. Temporarily remove `api` from `UI_FIRST_SEGMENT`; `keepsReservedAndFileLikeRoutesAsRealNotFound` must fail because `/api` forwards.
2. Temporarily remove the `rest.contains(".")` branch; the same test must fail because `/reports/file.csv` forwards.
3. Temporarily remove the blank-origin return; `disablesCorsWhenTheDevelopmentOriginIsBlank` must fail or context creation must reject the empty mapping.
4. Temporarily replace `allowedOrigins(devAllowedOrigin)` with `allowedOriginPatterns("*")`; default/allowed/rejected CORS tests or the forbidden static gate must fail.
5. Temporarily set `allowCredentials(true)`; `allowsOnlyTheConfiguredApiOriginAndContract` must fail because `Access-Control-Allow-Credentials` appears.

After restoring, rerun the focused command once and expect all scenarios green. Confirm `git diff` contains only the approved final implementation, never a mutation.

- [ ] **Step 8: Run the complete packaged-app regression**

Run:

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am clean verify
```

Expected sequence and result:

```text
frontend:1.15.4 installs Node v24.15.0 / npm 11.12.1
npm ci
20 frontend files / 120 tests pass
Vite 8.2.2 production build succeeds with only the established chunk-size advisory
generated resources copy
all plugin-api, core, Tushare, fixture, and app Surefire tests pass
spring-boot:3.5.16:repackage succeeds
PackagedJarContractTest runs under Failsafe: 4/4 pass
reactor BUILD SUCCESS
```

If sandboxed Mockito/Byte Buddy self-attach is the only failure, rerun this exact command in the already approved normal JVM environment. Do not skip tests, alter Mockito/JVM configuration, or report success from the sandbox failure.

- [ ] **Step 9: Run format, scope, tracking, configuration, and forbidden-capability gates**

Run:

```bash
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/src/main/resources/application.yml \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java
rg -n 'dev-allowed-origin|TENSOR_DEV_CORS_ALLOWED_ORIGIN|shutdown|timeout-per-shutdown-phase' \
  data-plane/tensor-app/src/main/resources/application.yml
rg -n 'allowedOriginPatterns|allowCredentials[(]true[)]|setStatus|sendRedirect|RedirectView|Authorization|Cookie|spring-security' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java
git diff -- data-plane/tensor-app/pom.xml control-plane \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-tushare data-plane/tensor-plugin-fixture \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/WebSecurityHeadersConfiguration.java
git ls-files --error-unmatch \
  data-plane/tensor-app/src/main/resources/application.yml \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java
```

Expected before staging: `git diff --check` exits 0; scoped status shows exactly one modified and two untracked paths; YAML scan shows only the three approved additions plus the existing Tushare timeout; forbidden production scan returns no match; protected-path diff is empty. The two new files will make `git ls-files --error-unmatch` fail before staging; rerun it after the next step and require exit 0.

- [ ] **Step 10: Stage only the implementation, verify the index, and commit once**

Run:

```bash
git add \
  data-plane/tensor-app/src/main/resources/application.yml \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java
git diff --cached --check
git diff --cached --name-status
git ls-files --error-unmatch \
  data-plane/tensor-app/src/main/resources/application.yml \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java
git commit -m "feat(app): configure production web delivery"
```

Expected staged names and statuses, with no other path:

```text
A data-plane/tensor-app/src/main/java/com/akkc/tensor/config/SpaWebConfiguration.java
M data-plane/tensor-app/src/main/resources/application.yml
A data-plane/tensor-app/src/test/java/com/akkc/tensor/config/ProductionWebConfigurationTest.java
```

After commit, run `git status --short`. Expected: the implementation paths are clean; the user's `.idea/misc.xml` and generated Maven `target/` directories remain untouched and uncommitted.
