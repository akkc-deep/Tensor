package com.akkc.tensor.plugin.tushare.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TushareRestClientFactoryTest {
    private static final String PREFIX = "tensor.plugins.tushare-pro";
    private static final String SECRET = "m07-t01-secret-sentinel";

    @RegisterExtension
    private final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    @Order(1)
    void exposesOnlyTheSpecifiedConfigurationAndFactorySurface() {
        assertThat(TushareProperties.class.isRecord()).isTrue();
        assertThat(Modifier.isPublic(TushareProperties.class.getModifiers())).isTrue();
        assertThat(TushareProperties.class.getAnnotation(ConfigurationProperties.class).value()).isEqualTo(PREFIX);
        assertThat(TushareProperties.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("enabled", "baseUrl", "token", "connectTimeout", "readTimeout", "maxResponseBytes");
        assertThat(TushareProperties.class.getRecordComponents()).extracting(component -> (Object) component.getType())
                .containsExactly(boolean.class, URI.class, TushareProperties.Credential.class,
                        Duration.class, Duration.class, int.class);
        assertThat(TushareProperties.Credential.class.isRecord()).isTrue();
        assertThat(Modifier.isPublic(TushareProperties.Credential.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(TushareRestClientFactory.class.getModifiers())).isTrue();
        assertThat(publicConstructors(TushareRestClientFactory.class)).hasSize(1);
        assertThat(publicConstructors(TushareRestClientFactory.class)[0].getParameterTypes()).isEmpty();
        assertThat(publicMethods(TushareRestClientFactory.class)).extracting(Method::getName)
                .containsExactly("create");
        assertThat(publicMethods(TushareRestClientFactory.class)[0].getParameterTypes())
                .containsExactly(TushareProperties.class);
        assertThat(publicMethods(TushareRestClientFactory.class)[0].getReturnType().getName())
                .isEqualTo("org.springframework.web.client.RestClient");
        assertThat(TushareProperties.class.getDeclaredMethods())
                .noneMatch(method -> Modifier.isPublic(method.getModifiers()) && method.getName().startsWith("set"));
    }

    @Test
    @Order(2)
    void bindsAuthoritativeDefaultsWithoutRequiringAToken() {
        TushareProperties properties = bind(Map.of());

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.baseUrl()).isEqualTo(URI.create("https://api.tushare.pro"));
        assertThat(properties.token().configured()).isFalse();
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.maxResponseBytes()).isEqualTo(67_108_864);
    }

    @Test
    @Order(3)
    void bindsKebabCaseOverridesIncludingAScalarCredential() {
        TushareProperties properties = bind(Map.of(
                PREFIX + ".enabled", "false",
                PREFIX + ".base-url", "http://localhost:8089/api",
                PREFIX + ".token", SECRET,
                PREFIX + ".connect-timeout", "2s",
                PREFIX + ".read-timeout", "30s",
                PREFIX + ".max-response-bytes", "1048576"));

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.baseUrl()).isEqualTo(URI.create("http://localhost:8089/api"));
        assertTrue(SECRET.equals(properties.token().value()), "credential binds from the configured scalar");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxResponseBytes()).isEqualTo(1_048_576);
    }

    @Test
    @Order(4)
    void rejectsInvalidConfigurationWithoutLeakingInputValues() {
        assertInvalidDirect(null, Duration.ofSeconds(5), Duration.ofSeconds(120), 1, "baseUrl must be an absolute HTTP(S) URI without credentials, query, or fragment");
        for (URI uri : new URI[] {URI.create("relative"), URI.create("https://user:pass@example.test"),
                URI.create("https://example.test?secret=" + SECRET), URI.create("https://example.test#fragment")}) {
            assertInvalidDirect(uri, Duration.ofSeconds(5), Duration.ofSeconds(120), 1,
                    "baseUrl must be an absolute HTTP(S) URI without credentials, query, or fragment");
            assertInvalidBinding(Map.of(PREFIX + ".base-url", uri.toString()));
        }
        assertInvalidDirect(URI.create("https://example.test"), Duration.ZERO, Duration.ofSeconds(120), 1,
                "connectTimeout must be positive");
        assertInvalidBinding(Map.of(PREFIX + ".connect-timeout", "0s"));
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ZERO, 1,
                "readTimeout must be positive and at most 120 seconds");
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ofSeconds(121), 1,
                "readTimeout must be positive and at most 120 seconds");
        assertInvalidBinding(Map.of(PREFIX + ".read-timeout", "121s"));
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ofSeconds(120), 0,
                "maxResponseBytes must be between 1 and 67108864");
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ofSeconds(120), 67_108_865,
                "maxResponseBytes must be between 1 and 67108864");
        assertInvalidBinding(Map.of(PREFIX + ".max-response-bytes", "67108865"));
    }

    @Test
    @Order(5)
    void projectsEnabledAndCredentialStateIntoReadinessWithoutNetworkAccess() {
        assertThat(properties(false, "").readiness()).isEqualTo(new PluginReadiness(false, false, false, "Disabled"));
        assertThat(properties(false, SECRET).readiness()).isEqualTo(new PluginReadiness(false, true, false, "Disabled"));
        assertThat(properties(true, "").readiness()).isEqualTo(new PluginReadiness(true, false, false, "Credentials missing"));
        assertThat(properties(true, SECRET).readiness()).isEqualTo(new PluginReadiness(true, true, true, null));

        TushareProperties properties = properties(true, "");
        new TushareRestClientFactory().create(properties);
    }

    @Test
    @Order(6)
    void redactsCredentialsFromStringsAndFailureMessages() {
        TushareProperties properties = properties(true, SECRET);
        String credentialText = properties.token().toString();
        String propertiesText = properties.toString();

        assertTrue("[REDACTED]".equals(credentialText), "credential string is the redaction marker");
        assertTrue(!credentialText.contains(SECRET), "credential string omits the credential");
        assertTrue(propertiesText.contains("[REDACTED]"), "properties string contains the redaction marker");
        assertTrue(!propertiesText.contains(SECRET), "properties string omits the credential");
        IllegalArgumentException invalidUrl = expectIllegalArgument(() -> new TushareProperties(true,
                URI.create("https://example.test?token=" + SECRET), new TushareProperties.Credential(SECRET),
                Duration.ofSeconds(5), Duration.ofSeconds(120), 1));
        assertTrue("baseUrl must be an absolute HTTP(S) URI without credentials, query, or fragment"
                .equals(invalidUrl.getMessage()), "invalid URL message is fixed");
        assertTrue(!invalidUrl.getMessage().contains(SECRET), "invalid URL message omits the credential");
        Throwable nullProperties = org.assertj.core.api.Assertions.catchThrowable(() -> new TushareRestClientFactory().create(null));
        assertTrue(nullProperties != null, "null properties are rejected");
        assertTrue(!String.valueOf(nullProperties.getMessage()).contains(SECRET), "null properties message omits the credential");
    }

    @Test
    @Order(7)
    void sendsExactlyOneRequestWithOnlyTheFixedUserAgentAndNoCredential() {
        wireMock.stubFor(get(urlEqualTo("/status")).willReturn(aResponse().withStatus(200)));

        new TushareRestClientFactory().create(properties(wireMock.baseUrl(), SECRET))
                .get().uri("/status").retrieve().toBodilessEntity();

        var events = wireMock.getAllServeEvents();
        boolean oneStatusRequest = events.size() == 1
                && "GET".equals(events.getFirst().getRequest().getMethod().getName())
                && "/status".equals(events.getFirst().getRequest().getUrl());
        assertTrue(oneStatusRequest, "exactly one status request reaches the upstream server");
        if (oneStatusRequest) {
            var event = events.getFirst();
            assertTrue(!event.getRequest().getAbsoluteUrl().contains(SECRET), "request URL omits the credential");
            assertTrue(java.util.List.of("Tensor/1.0").equals(event.getRequest().getHeaders()
                    .getHeader("User-Agent").values()), "request has the fixed user agent");
            assertTrue(event.getRequest().getHeaders().all().stream()
                    .noneMatch(header -> header.values().contains(SECRET)), "request headers omit the credential");
            assertTrue(!event.getRequest().getBodyAsString().contains(SECRET), "request body omits the credential");
        }
    }

    @Test
    @Order(9)
    void appliesJdkConnectAndRequestReadTimeouts() {
        assertThat(TushareRestClientFactory.createHttpClient(Duration.ofSeconds(2)).connectTimeout())
                .contains(Duration.ofSeconds(2));
        wireMock.stubFor(get(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        Throwable timeout = org.assertj.core.api.Assertions.catchThrowable(() -> new TushareRestClientFactory()
                .create(properties(wireMock.baseUrl(), "", Duration.ofMillis(100)))
                .get().uri("/slow").retrieve().toBodilessEntity());
        assertTrue(timeout instanceof ResourceAccessException, "delayed request fails with a resource access exception");
        var events = wireMock.getAllServeEvents();
        assertTrue(events.size() == 1 && "GET".equals(events.getFirst().getRequest().getMethod().getName())
                && "/slow".equals(events.getFirst().getRequest().getUrl()), "exactly one delayed request reaches the upstream server");
    }

    @Test
    @Order(8)
    void doesNotRetryAServiceUnavailableResponse() {
        wireMock.stubFor(post(urlEqualTo("/upstream")).willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())));

        Throwable unavailable = org.assertj.core.api.Assertions.catchThrowable(() -> new TushareRestClientFactory()
                .create(properties(wireMock.baseUrl(), SECRET)).post().uri("/upstream").retrieve().toBodilessEntity());
        assertTrue(unavailable instanceof HttpServerErrorException.ServiceUnavailable,
                "service unavailable response propagates as the standard exception");
        var events = wireMock.getAllServeEvents();
        assertTrue(events.size() == 1 && "POST".equals(events.getFirst().getRequest().getMethod().getName())
                && "/upstream".equals(events.getFirst().getRequest().getUrl()), "exactly one unavailable request reaches the upstream server");
    }

    private Constructor<?>[] publicConstructors(Class<?> type) {
        return type.getConstructors();
    }

    private Method[] publicMethods(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);
    }

    private TushareProperties bind(Map<String, Object> values) {
        return new Binder(java.util.List.of(new MapConfigurationPropertySource(values)))
                .bindOrCreate(PREFIX, Bindable.of(TushareProperties.class));
    }

    private void assertInvalidDirect(URI baseUrl, Duration connectTimeout, Duration readTimeout, int maxResponseBytes,
                                     String expectedMessage) {
        IllegalArgumentException exception = expectIllegalArgument(() -> new TushareProperties(true, baseUrl,
                new TushareProperties.Credential(SECRET), connectTimeout, readTimeout, maxResponseBytes));
        assertTrue(expectedMessage.equals(exception.getMessage()), "invalid configuration reports the fixed message");
        assertTrue(!exception.getMessage().contains(SECRET), "invalid configuration message omits the credential");
    }

    private void assertInvalidBinding(Map<String, Object> values) {
        BindException exception = expectBindException(() -> bind(values));
        assertTrue(!exception.getMessage().contains(SECRET), "binding failure message omits the credential");
    }

    private IllegalArgumentException expectIllegalArgument(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        try {
            action.call();
        } catch (IllegalArgumentException exception) {
            return exception;
        } catch (Throwable ignored) {
            throw new AssertionError("configuration rejects invalid URLs with IllegalArgumentException");
        }
        throw new AssertionError("configuration rejects invalid URLs");
    }

    private BindException expectBindException(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        try {
            action.call();
        } catch (BindException exception) {
            return exception;
        } catch (Throwable ignored) {
            throw new AssertionError("binding rejects invalid configuration");
        }
        throw new AssertionError("binding rejects invalid configuration");
    }

    private TushareProperties properties(boolean enabled, String credential) {
        return properties(URI.create("https://example.test"), enabled, credential);
    }

    private TushareProperties properties(String baseUrl, String credential) {
        return properties(baseUrl, credential, Duration.ofSeconds(5));
    }

    private TushareProperties properties(String baseUrl, String credential, Duration readTimeout) {
        return new TushareProperties(true, URI.create(baseUrl), new TushareProperties.Credential(credential),
                Duration.ofSeconds(5), readTimeout, 1_024);
    }

    private TushareProperties properties(URI baseUrl, boolean enabled, String credential) {
        return new TushareProperties(enabled, baseUrl, new TushareProperties.Credential(credential),
                Duration.ofSeconds(5), Duration.ofSeconds(5), 1_024);
    }
}
