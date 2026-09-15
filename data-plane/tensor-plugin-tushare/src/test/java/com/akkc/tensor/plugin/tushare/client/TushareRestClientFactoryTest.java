package com.akkc.tensor.plugin.tushare.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TushareRestClientFactoryTest {
    private static final String PREFIX = "tensor.plugins.tushare-pro";
    private static final String SECRET = "m07-t01-secret-sentinel";
    private static final DatasetDefinition DAILY = new DatasetDefinitionLoader()
            .loadAll(new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml")
            .stream().filter(value -> value.datasetKey().apiName().value().equals("daily")).findFirst().orElseThrow();
    private static final Map<String, Object> DAILY_PARAMS =
            Map.of("ts_code", "000001.SZ", "trade_date", "20260902");

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
                .containsExactly("enabled", "baseUrl", "token", "connectTimeout", "readTimeout", "maxResponseBytes",
                        "minRequestInterval");
        assertThat(TushareProperties.class.getRecordComponents()).extracting(component -> (Object) component.getType())
                .containsExactly(boolean.class, URI.class, TushareProperties.Credential.class,
                        Duration.class, Duration.class, int.class, Duration.class);
        assertThat(publicConstructors(TushareProperties.class)).extracting(Constructor::getParameterCount)
                .containsExactlyInAnyOrder(6, 7);
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
        assertThat(properties.minRequestInterval()).isEqualTo(Duration.ofMillis(1_500));
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
                PREFIX + ".max-response-bytes", "1048576",
                PREFIX + ".min-request-interval", "3s"));

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.baseUrl()).isEqualTo(URI.create("http://localhost:8089/api"));
        assertTrue(SECRET.equals(properties.token().value()), "credential binds from the configured scalar");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxResponseBytes()).isEqualTo(1_048_576);
        assertThat(properties.minRequestInterval()).isEqualTo(Duration.ofSeconds(3));

        assertThat(bind(Map.of(PREFIX + ".min-request-interval", "0ms")).minRequestInterval()).isZero();
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
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(-1), Duration.ofSeconds(120), 1,
                "connectTimeout must be positive");
        assertInvalidBinding(Map.of(PREFIX + ".connect-timeout", "-1s"));
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ZERO, 1,
                "readTimeout must be positive and at most 120 seconds");
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ofSeconds(-1), 1,
                "readTimeout must be positive and at most 120 seconds");
        assertInvalidBinding(Map.of(PREFIX + ".read-timeout", "-1s"));
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ofSeconds(121), 1,
                "readTimeout must be positive and at most 120 seconds");
        assertInvalidBinding(Map.of(PREFIX + ".read-timeout", "121s"));
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ofSeconds(120), 0,
                "maxResponseBytes must be between 1 and 67108864");
        assertInvalidDirect(URI.create("https://example.test"), Duration.ofSeconds(5), Duration.ofSeconds(120), 67_108_865,
                "maxResponseBytes must be between 1 and 67108864");
        assertInvalidBinding(Map.of(PREFIX + ".max-response-bytes", "67108865"));
        assertInvalidInterval(Duration.ofNanos(-1));
        assertInvalidInterval(null);
        assertInvalidInterval(Duration.ofSeconds(Long.MAX_VALUE));
        assertInvalidBinding(Map.of(PREFIX + ".min-request-interval", "-1ns"));

        assertThat(new TushareProperties(true, URI.create("https://example.test"),
                new TushareProperties.Credential(SECRET), Duration.ofSeconds(5), Duration.ofSeconds(120), 1)
                .minRequestInterval()).isEqualTo(Duration.ofMillis(1_500));
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
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));

        Throwable requestFailure = org.assertj.core.api.Assertions.catchThrowable(() -> new TushareRestClientFactory()
                .create(properties(wireMock.baseUrl(), SECRET)).get().uri("/status").retrieve().toBodilessEntity());
        assertTrue(requestFailure == null, () -> "credential safety request completes: " + requestFailure);

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
    @Order(10)
    void appliesTheSmallerConfiguredOrRemainingTimeoutToTheActualJdkRequest() {
        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        RecordingHttpClient transport = new RecordingHttpClient();
        var client = new TushareRestClientFactory(transport)
                .create(properties("https://example.test", "", Duration.ofMillis(800)));

        client.get().uri("/config-limited")
                .attribute(TushareRestClientFactory.CONTROL_ATTRIBUTE,
                        new TushareRestClientFactory.RequestControl(context(now.plusSeconds(2)), clock))
                .retrieve().toBodilessEntity();

        assertThat(transport.timeout("/config-limited")).contains(Duration.ofMillis(800));

        transport = new RecordingHttpClient();
        client = new TushareRestClientFactory(transport)
                .create(properties("https://example.test", "", Duration.ofSeconds(5)));
        client.get().uri("/deadline-limited")
                .attribute(TushareRestClientFactory.CONTROL_ATTRIBUTE,
                        new TushareRestClientFactory.RequestControl(
                                context(now.plusNanos(2_345_999_999L)), clock))
                .retrieve().toBodilessEntity();

        assertThat(transport.timeout("/deadline-limited")).contains(Duration.ofMillis(2_345));
    }

    @Test
    @Order(11)
    void keepsConcurrentRequestDeadlinesIndependentAndAttributesOffTheWire() {
        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        RecordingHttpClient transport = new RecordingHttpClient();
        var client = new TushareRestClientFactory(transport)
                .create(properties("https://example.test", "", Duration.ofSeconds(10)));

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> client.get().uri("/first")
                        .attribute(TushareRestClientFactory.CONTROL_ATTRIBUTE,
                                new TushareRestClientFactory.RequestControl(context(now.plusMillis(3_200)), clock))
                        .retrieve().toBodilessEntity()),
                CompletableFuture.runAsync(() -> client.get().uri("/second")
                        .attribute(TushareRestClientFactory.CONTROL_ATTRIBUTE,
                                new TushareRestClientFactory.RequestControl(context(now.plusMillis(7_400)), clock))
                        .retrieve().toBodilessEntity()))
                .join();

        assertThat(transport.timeout("/first")).contains(Duration.ofMillis(3_200));
        assertThat(transport.timeout("/second")).contains(Duration.ofMillis(7_400));
        assertThat(transport.requests()).allSatisfy(request -> {
            assertThat(request.uri().toString()).doesNotContain(TushareRestClientFactory.CONTROL_ATTRIBUTE);
            assertThat(request.headers().map()).allSatisfy((name, values) -> {
                assertThat(name).doesNotContain(TushareRestClientFactory.CONTROL_ATTRIBUTE);
                assertThat(values).noneMatch(value -> value.contains(TushareRestClientFactory.CONTROL_ATTRIBUTE));
            });
        });
    }

    @Test
    @Order(12)
    void preservesConfiguredTimeoutWithoutControlAndRejectsSubMillisecondLimitsBeforeSending() {
        RecordingHttpClient transport = new RecordingHttpClient();
        var client = new TushareRestClientFactory(transport)
                .create(properties("https://example.test", "", Duration.ofMillis(725)));
        client.get().uri("/uncontrolled").retrieve().toBodilessEntity();
        assertThat(transport.timeout("/uncontrolled")).contains(Duration.ofMillis(725));

        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        Throwable expired = org.assertj.core.api.Assertions.catchThrowable(() -> client.get().uri("/too-short")
                .attribute(TushareRestClientFactory.CONTROL_ATTRIBUTE,
                        new TushareRestClientFactory.RequestControl(context(now.plusNanos(999_999)), clock))
                .retrieve().toBodilessEntity());
        assertThat(expired).isInstanceOfSatisfying(TensorException.class,
                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));

        var shortConfigClient = new TushareRestClientFactory(transport)
                .create(properties("https://example.test", "", Duration.ofNanos(999_999)));
        Throwable configured = org.assertj.core.api.Assertions.catchThrowable(() -> shortConfigClient.get()
                .uri("/short-config").retrieve().toBodilessEntity());
        assertThat(configured).isInstanceOfSatisfying(TensorException.class,
                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.SOURCE_TIMEOUT));
        assertThat(transport.requests()).hasSize(1);
    }

    @Test
    @Order(13)
    void rechecksStopImmediatelyBeforeSending() {
        RecordingHttpClient transport = new RecordingHttpClient();
        AtomicInteger checks = new AtomicInteger();
        BatchCallContext context = new BatchCallContext() {
            @Override
            public Instant deadline() {
                return Instant.MAX;
            }

            @Override
            public boolean stopRequested() {
                return checks.incrementAndGet() >= 2;
            }

            @Override
            public void beforeRequest() {}
        };

        Throwable stopped = org.assertj.core.api.Assertions.catchThrowable(() -> new TushareRestClientFactory(transport)
                .create(properties("https://example.test", "", Duration.ofSeconds(5)))
                .get().uri("/stopped")
                .attribute(TushareRestClientFactory.CONTROL_ATTRIBUTE,
                        new TushareRestClientFactory.RequestControl(context, Clock.systemUTC()))
                .retrieve().toBodilessEntity());

        assertThat(stopped).isInstanceOfSatisfying(TensorException.class,
                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.EXECUTION_INTERRUPTED));
        assertThat(checks).hasValue(2);
        assertThat(transport.requests()).isEmpty();
    }

    @Test
    @Order(14)
    void preservesTaskTimeoutOriginWhenTheMillisecondFloorExpiresBeforeTheDeadline() {
        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        CloseAwareInputStream body = new CloseAwareInputStream();
        RecordingHttpClient transport = new RecordingHttpClient(body);
        var client = new TushareRestClientFactory(transport)
                .create(properties("https://example.test", "", Duration.ofSeconds(5)));

        Throwable timeout = org.assertj.core.api.Assertions.catchThrowable(() -> client.get().uri("/fractional")
                .attribute(TushareRestClientFactory.CONTROL_ATTRIBUTE,
                        new TushareRestClientFactory.RequestControl(
                                context(now.plusNanos(2_999_999)), Clock.fixed(now, ZoneOffset.UTC)))
                .retrieve().body(String.class));

        assertThat(timeout).isInstanceOfSatisfying(TensorException.class,
                failure -> assertThat(failure.code()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));
        assertThat(body.closed.getCount()).isZero();
        assertThat(transport.timeout("/fractional")).contains(Duration.ofMillis(2));
    }

    @Test
    @Order(15)
    void computesNativeTimeoutAfterThrottleAndIoSchedulingConsumeTheBudget() throws Exception {
        RecordingHttpClient transport = new RecordingHttpClient();
        TestTime time = new TestTime();
        TushareRequestGate gate = completedGate(time);
        CountingContext context = new CountingContext(Instant.EPOCH.plusSeconds(10));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TushareProClient client = controlledClient(transport, time, gate, entered, release);

        try (var caller = Executors.newSingleThreadExecutor()) {
            Future<Throwable> result = caller.submit(() ->
                    org.assertj.core.api.Assertions.catchThrowable(() -> client.execute(DAILY, DAILY_PARAMS, context)));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(time.nanos.get()).isEqualTo(Duration.ofMillis(1_500).toNanos());
                assertThat(context.reservations).hasValue(1);
                time.advance(Duration.ofMillis(2_250));
                release.countDown();

                assertCode(result.get(2, TimeUnit.SECONDS), ErrorCode.SOURCE_PAYLOAD_INVALID);
                assertThat(transport.requests()).hasSize(1);
                assertThat(transport.requests().getFirst().timeout()).contains(Duration.ofMillis(6_250));
            } finally {
                release.countDown();
                result.cancel(true);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"stop", "deadline"})
    @Order(16)
    void rechecksControlAfterThrottleAndIoSchedulingBeforeNativeSend(String control) throws Exception {
        RecordingHttpClient transport = new RecordingHttpClient();
        TestTime time = new TestTime();
        TushareRequestGate gate = completedGate(time);
        CountingContext context = new CountingContext(Instant.EPOCH.plusSeconds(10));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TushareProClient client = controlledClient(transport, time, gate, entered, release);

        try (var caller = Executors.newSingleThreadExecutor()) {
            Future<Throwable> result = caller.submit(() ->
                    org.assertj.core.api.Assertions.catchThrowable(() -> client.execute(DAILY, DAILY_PARAMS, context)));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(context.reservations).hasValue(1);
                if (control.equals("stop")) {
                    context.stop.set(true);
                } else {
                    time.advance(Duration.ofMillis(8_500));
                }
                release.countDown();

                assertCode(result.get(2, TimeUnit.SECONDS), control.equals("stop")
                        ? ErrorCode.EXECUTION_INTERRUPTED : ErrorCode.TASK_LIMIT_EXCEEDED);
                assertThat(transport.requests()).isEmpty();
            } finally {
                release.countDown();
                result.cancel(true);
            }
        }
    }

    @Test
    @Order(8)
    void doesNotRetryAServiceUnavailableResponse() {
        wireMock.stubFor(post(urlEqualTo("/upstream")).willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())));

        Throwable unavailable = org.assertj.core.api.Assertions.catchThrowable(() -> new TushareRestClientFactory()
                .create(properties(wireMock.baseUrl(), "")).post().uri("/upstream").retrieve().toBodilessEntity());
        assertTrue(unavailable instanceof HttpServerErrorException.ServiceUnavailable,
                () -> "service unavailable response propagates as the standard exception: " + unavailable);
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

    private void assertInvalidInterval(Duration interval) {
        IllegalArgumentException exception = expectIllegalArgument(() -> new TushareProperties(true,
                URI.create("https://example.test"), new TushareProperties.Credential(SECRET),
                Duration.ofSeconds(5), Duration.ofSeconds(120), 1, interval));
        assertThat(exception).hasMessage("minRequestInterval must be non-negative and fit in nanoseconds");
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

    private BatchCallContext context(Instant deadline) {
        return new BatchCallContext() {
            @Override
            public Instant deadline() {
                return deadline;
            }

            @Override
            public boolean stopRequested() {
                return false;
            }

            @Override
            public void beforeRequest() {}
        };
    }

    private TushareRequestGate completedGate(TestTime time) {
        TushareRequestGate gate = new TushareRequestGate(
                Duration.ofMillis(1_500), time, time.nanos::get, time::advance);
        gate.execute(context(Instant.MAX), () -> null);
        return gate;
    }

    private TushareProClient controlledClient(RecordingHttpClient transport, TestTime time,
                                               TushareRequestGate gate, CountDownLatch entered,
                                               CountDownLatch release) {
        TushareProperties properties = properties("https://example.test", "", Duration.ofSeconds(8));
        var restClient = new TushareRestClientFactory(transport).create(properties).mutate()
                .requestInitializer(request -> {
                    entered.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test release timed out");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("test request interrupted");
                    }
                }).build();
        return new TushareProClient(restClient, properties, gate);
    }

    private void assertCode(Throwable failure, ErrorCode code) {
        assertThat(failure).isInstanceOf(TensorException.class);
        assertThat(((TensorException) failure).code()).isEqualTo(code);
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final Map<String, HttpRequest> requests = new ConcurrentHashMap<>();
        private final InputStream body;

        private RecordingHttpClient() {
            this(InputStream.nullInputStream());
        }

        private RecordingHttpClient(InputStream body) {
            this.body = body;
        }

        List<HttpRequest> requests() {
            return List.copyOf(requests.values());
        }

        Optional<Duration> timeout(String path) {
            HttpRequest request = requests.get(path);
            return request == null ? Optional.empty() : request.timeout();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(5)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            return response(request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.put(request.uri().getPath(), request);
            return CompletableFuture.completedFuture(response(request));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @SuppressWarnings("unchecked")
        private <T> HttpResponse<T> response(HttpRequest request) {
            return new HttpResponse<>() {
                @Override public int statusCode() { return 200; }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (name, value) -> true);
                }
                @Override public T body() { return (T) body; }
                @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public Version version() { return Version.HTTP_1_1; }
            };
        }
    }

    private static final class CloseAwareInputStream extends InputStream {
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public int read() throws IOException {
            try {
                if (!closed.await(1, TimeUnit.SECONDS)) {
                    throw new IOException("timeout did not close response body");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("response body interrupted");
            }
            throw new IOException("response body closed");
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }

    private static final class CountingContext implements BatchCallContext {
        private final Instant deadline;
        private final AtomicBoolean stop = new AtomicBoolean();
        private final AtomicInteger reservations = new AtomicInteger();

        private CountingContext(Instant deadline) {
            this.deadline = deadline;
        }

        @Override public Instant deadline() { return deadline; }
        @Override public boolean stopRequested() { return stop.get(); }
        @Override public void beforeRequest() { reservations.incrementAndGet(); }
    }

    private static final class TestTime extends Clock {
        private final AtomicLong nanos = new AtomicLong();

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.EPOCH.plusNanos(nanos.get()); }
    }
}
