package com.akkc.tensor.plugin.tushare.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TushareErrorClassifierTest {
    private static final String UNTRUSTED =
            "m07-t03-secret-sentinel credential=value https://secret.invalid raw-body";

    @Test
    void exposesOnlyFourPackagePrivateStaticOperationsOnAStatelessFinalType() {
        assertThat(Modifier.isPublic(TushareErrorClassifier.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(TushareErrorClassifier.class.getModifiers())).isTrue();
        assertThat(TushareErrorClassifier.class.getDeclaredConstructors())
                .singleElement()
                .matches(constructor -> Modifier.isPrivate(constructor.getModifiers()));
        assertThat(TushareErrorClassifier.class.getDeclaredFields())
                .allMatch(field -> Modifier.isStatic(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()));

        Method[] operations = Arrays.stream(TushareErrorClassifier.class.getDeclaredMethods())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .toArray(Method[]::new);
        assertThat(operations).extracting(Method::getName)
                .containsExactlyInAnyOrder(
                        "classifyHttp", "classifyBusiness", "classifyTransport", "invalidPayload");
        assertThat(operations).allMatch(method -> Modifier.isStatic(method.getModifiers())
                && !Modifier.isPublic(method.getModifiers())
                && !Modifier.isProtected(method.getModifiers())
                && method.getReturnType().equals(SourceException.class));
    }

    @Test
    void mapsHttpStatusesToFixedSourceFailures() {
        assertSource(TushareErrorClassifier.classifyHttp(401), ErrorCode.SOURCE_AUTH_FAILED,
                "Tushare credentials were rejected");
        assertSource(TushareErrorClassifier.classifyHttp(403), ErrorCode.SOURCE_PERMISSION_DENIED,
                "Tushare API permission is unavailable");
        assertSource(TushareErrorClassifier.classifyHttp(429), ErrorCode.SOURCE_RATE_LIMITED,
                "Tushare rate limit was reached");
        assertSource(TushareErrorClassifier.classifyHttp(500), ErrorCode.SOURCE_UNAVAILABLE,
                "Tushare service is unavailable");
        assertSource(TushareErrorClassifier.classifyHttp(503), ErrorCode.SOURCE_UNAVAILABLE,
                "Tushare service is unavailable");
        assertSource(TushareErrorClassifier.classifyHttp(418), ErrorCode.SOURCE_UNAVAILABLE,
                "Tushare service is unavailable");
    }

    @Test
    void recognizesAuthenticationMessagesBeforeOtherCategories() {
        for (String message : List.of("ToKeN invalid", "认证失败", "用户不存在")) {
            assertThat(TushareErrorClassifier.classifyBusiness(message).code())
                    .isEqualTo(ErrorCode.SOURCE_AUTH_FAILED);
        }
        assertThat(TushareErrorClassifier.classifyBusiness("TOKEN 每分钟 权限 积分").code())
                .isEqualTo(ErrorCode.SOURCE_AUTH_FAILED);
    }

    @Test
    void recognizesRateMessagesBeforePermissionMessages() {
        for (String message : List.of("每分钟调用过多", "每小时调用过多", "访问频率过高", "已被限流")) {
            assertThat(TushareErrorClassifier.classifyBusiness(message).code())
                    .isEqualTo(ErrorCode.SOURCE_RATE_LIMITED);
        }
        for (String message : List.of("频率过高且权限不足", "限流且积分不足")) {
            assertThat(TushareErrorClassifier.classifyBusiness(message).code())
                    .isEqualTo(ErrorCode.SOURCE_RATE_LIMITED);
        }
    }

    @Test
    void mapsPermissionAndUnknownBusinessMessagesWithoutGuessing() {
        assertThat(TushareErrorClassifier.classifyBusiness("权限不足").code())
                .isEqualTo(ErrorCode.SOURCE_PERMISSION_DENIED);
        assertThat(TushareErrorClassifier.classifyBusiness("积分不足").code())
                .isEqualTo(ErrorCode.SOURCE_PERMISSION_DENIED);
        for (String message : Arrays.asList(null, "", "   ", "unknown upstream failure")) {
            assertThat(TushareErrorClassifier.classifyBusiness(message).code())
                    .isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
        }
    }

    @Test
    void classifiesDnsConnectRouteAndConnectTimeoutFailuresAsNetworkErrors() {
        List<Throwable> failures = List.of(
                new UnknownHostException(UNTRUSTED),
                new RuntimeException(new ConnectException(UNTRUSTED)),
                new RuntimeException(new NoRouteToHostException(UNTRUSTED)),
                new RuntimeException(new HttpConnectTimeoutException(UNTRUSTED)));

        for (Throwable failure : failures) {
            assertThat(TushareErrorClassifier.classifyTransport(failure).code())
                    .isEqualTo(ErrorCode.SOURCE_NETWORK_ERROR);
        }
    }

    @Test
    void prioritizesReadTimeoutsAcrossTheWholeCauseChain() {
        assertThat(TushareErrorClassifier.classifyTransport(new SocketTimeoutException(UNTRUSTED)).code())
                .isEqualTo(ErrorCode.SOURCE_TIMEOUT);
        assertThat(TushareErrorClassifier.classifyTransport(new HttpTimeoutException(UNTRUSTED)).code())
                .isEqualTo(ErrorCode.SOURCE_TIMEOUT);

        ConnectException network = new ConnectException(UNTRUSTED);
        network.initCause(new SocketTimeoutException(UNTRUSTED));
        assertThat(TushareErrorClassifier.classifyTransport(new RuntimeException(network)).code())
                .isEqualTo(ErrorCode.SOURCE_TIMEOUT);
    }

    @Test
    void keepsAllFailureSummariesRetryabilityAndThrowableStateFixedAndSafe() {
        Map<ErrorCode, SourceException> failures = Map.of(
                ErrorCode.SOURCE_AUTH_FAILED,
                TushareErrorClassifier.classifyBusiness("TOKEN " + UNTRUSTED),
                ErrorCode.SOURCE_PERMISSION_DENIED,
                TushareErrorClassifier.classifyBusiness("权限 " + UNTRUSTED),
                ErrorCode.SOURCE_RATE_LIMITED,
                TushareErrorClassifier.classifyBusiness("频率 " + UNTRUSTED),
                ErrorCode.SOURCE_UNAVAILABLE,
                TushareErrorClassifier.classifyHttp(503),
                ErrorCode.SOURCE_NETWORK_ERROR,
                TushareErrorClassifier.classifyTransport(new UnknownHostException(UNTRUSTED)),
                ErrorCode.SOURCE_TIMEOUT,
                TushareErrorClassifier.classifyTransport(new SocketTimeoutException(UNTRUSTED)),
                ErrorCode.SOURCE_PAYLOAD_INVALID,
                TushareErrorClassifier.invalidPayload());
        Map<ErrorCode, String> messages = Map.of(
                ErrorCode.SOURCE_AUTH_FAILED, "Tushare credentials were rejected",
                ErrorCode.SOURCE_PERMISSION_DENIED, "Tushare API permission is unavailable",
                ErrorCode.SOURCE_RATE_LIMITED, "Tushare rate limit was reached",
                ErrorCode.SOURCE_UNAVAILABLE, "Tushare service is unavailable",
                ErrorCode.SOURCE_NETWORK_ERROR, "Tushare could not be reached",
                ErrorCode.SOURCE_TIMEOUT, "Tushare response timed out",
                ErrorCode.SOURCE_PAYLOAD_INVALID, "Tushare returned an invalid payload");

        failures.forEach((code, failure) -> {
            assertSource(failure, code, messages.get(code));
            String rendered = String.valueOf(failure);
            assertThat(!rendered.contains("m07-t03-secret-sentinel")
                    && !rendered.contains("credential=value")
                    && !rendered.contains("https://secret.invalid")
                    && !rendered.contains("raw-body"))
                    .as("source failures omit all untrusted inputs")
                    .isTrue();
        });
        assertThat(TushareErrorClassifier.classifyBusiness(UNTRUSTED).code())
                .isEqualTo(ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    private void assertSource(SourceException failure, ErrorCode code, String message) {
        assertThat(failure.code()).isEqualTo(code);
        assertThat(message.equals(failure.getMessage()))
                .as("source failure uses its fixed safe message")
                .isTrue();
        assertThat(failure.retryable()).isEqualTo(code.retryable());
        assertThat(failure.getCause() == null).as("source failure omits its cause").isTrue();
        assertThat(failure.getSuppressed().length == 0).as("source failure omits suppressed failures").isTrue();
    }
}
