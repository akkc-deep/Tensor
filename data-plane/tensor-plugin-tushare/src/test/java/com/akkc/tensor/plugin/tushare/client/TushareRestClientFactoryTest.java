package com.akkc.tensor.plugin.tushare.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class TushareRestClientFactoryTest {
    private static final String PREFIX = "tensor.plugins.tushare-pro";
    private static final String SECRET = "m07-t01-secret-sentinel";

    @Test
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
        assertThat(properties.token().value()).isEqualTo(SECRET);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxResponseBytes()).isEqualTo(1_048_576);
    }

    @Test
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
    void projectsEnabledAndCredentialStateIntoReadinessWithoutNetworkAccess() {
        assertThat(properties(false, "").readiness()).isEqualTo(new PluginReadiness(false, false, false, "Disabled"));
        assertThat(properties(false, SECRET).readiness()).isEqualTo(new PluginReadiness(false, true, false, "Disabled"));
        assertThat(properties(true, "").readiness()).isEqualTo(new PluginReadiness(true, false, false, "Credentials missing"));
        assertThat(properties(true, SECRET).readiness()).isEqualTo(new PluginReadiness(true, true, true, null));

        TushareProperties properties = properties(true, "");
        new TushareRestClientFactory().create(properties);
    }

    @Test
    void redactsCredentialsFromStringsAndFailureMessages() {
        TushareProperties properties = properties(true, SECRET);

        assertThat(properties.token().toString()).isEqualTo("[REDACTED]");
        assertThat(properties.toString()).contains("[REDACTED]").doesNotContain(SECRET);
        assertThatThrownBy(() -> new TushareProperties(true, URI.create("https://example.test?token=" + SECRET),
                new TushareProperties.Credential(SECRET), Duration.ofSeconds(5), Duration.ofSeconds(120), 1))
                .hasMessage("baseUrl must be an absolute HTTP(S) URI without credentials, query, or fragment")
                .hasMessageNotContaining(SECRET);
        assertThatThrownBy(() -> new TushareRestClientFactory().create(null))
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void sendsExactlyOneRequestWithOnlyTheFixedUserAgentAndNoCredential() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        server.stubFor(get(urlEqualTo("/status")).willReturn(aResponse().withStatus(200)));

        new TushareRestClientFactory().create(properties(server.baseUrl(), SECRET))
                .get().uri("/status").retrieve().toBodilessEntity();

        server.verify(1, getRequestedFor(urlEqualTo("/status"))
                .withHeader("User-Agent", com.github.tomakehurst.wiremock.client.WireMock.equalTo("Tensor/1.0")));
        assertThat(server.getAllServeEvents()).singleElement().satisfies(event -> {
            assertThat(event.getRequest().getAbsoluteUrl()).doesNotContain(SECRET);
            assertThat(event.getRequest().getHeaders().getHeader("User-Agent").values()).containsExactly("Tensor/1.0");
            assertThat(event.getRequest().getHeaders().all()).noneMatch(header -> header.values().contains(SECRET));
            assertThat(event.getRequest().getBodyAsString()).doesNotContain(SECRET);
        });
    }

    @Test
    void appliesJdkConnectAndRequestReadTimeouts() {
        assertThat(TushareRestClientFactory.createHttpClient(Duration.ofSeconds(2)).connectTimeout())
                .contains(Duration.ofSeconds(2));
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        server.stubFor(get(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        assertThatThrownBy(() -> new TushareRestClientFactory().create(properties(server.baseUrl(), ""))
                .get().uri("/slow").retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
        server.verify(1, getRequestedFor(urlEqualTo("/slow")));
    }

    @Test
    void doesNotRetryAServiceUnavailableResponse() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo("/upstream")).willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())));

        assertThatThrownBy(() -> new TushareRestClientFactory().create(properties(server.baseUrl(), SECRET))
                .post().uri("/upstream").retrieve().toBodilessEntity())
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        server.verify(1, postRequestedFor(urlEqualTo("/upstream")));
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
        assertThatThrownBy(() -> new TushareProperties(true, baseUrl, new TushareProperties.Credential(SECRET),
                connectTimeout, readTimeout, maxResponseBytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage)
                .hasMessageNotContaining(SECRET);
    }

    private void assertInvalidBinding(Map<String, Object> values) {
        assertThatThrownBy(() -> bind(values))
                .isInstanceOf(BindException.class)
                .hasMessageNotContaining(SECRET);
    }

    private TushareProperties properties(boolean enabled, String credential) {
        return properties(URI.create("https://example.test"), enabled, credential);
    }

    private TushareProperties properties(String baseUrl, String credential) {
        return properties(URI.create(baseUrl), true, credential);
    }

    private TushareProperties properties(URI baseUrl, boolean enabled, String credential) {
        return new TushareProperties(enabled, baseUrl, new TushareProperties.Credential(credential),
                Duration.ofMillis(100), Duration.ofMillis(100), 1_024);
    }
}
