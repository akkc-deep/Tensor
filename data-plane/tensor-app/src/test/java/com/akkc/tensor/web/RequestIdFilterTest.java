package com.akkc.tensor.web;

import com.akkc.tensor.TensorApplication;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.web.dto.ApiErrorResponse;
import com.akkc.tensor.web.dto.FieldErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestIdFilterTest {

    private static final String VALID_REQUEST_ID = "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesAValidLowercaseUuidToTheResponseAndMdc() throws Exception {
        HttpServletRequest request = requestWithHeader(VALID_REQUEST_ID);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(VALID_REQUEST_ID);

        new RequestIdFilter().doFilter(request, response, chain);

        verify(request).getHeader(RequestIdFilter.HEADER_NAME);
        verify(response).setHeader(RequestIdFilter.HEADER_NAME, VALID_REQUEST_ID);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesDistinctVersionFourVariantTwoUuidsWhenTheHeaderIsAbsent() throws Exception {
        String first = filterAndCapture(null);
        String second = filterAndCapture(null);

        assertThat(first).isNotEqualTo(second);
        assertUuid(first);
        assertUuid(second);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            " ",
            "C52BCE3D-5AA5-4C8E-AE64-E73CB76D8F33",
            "client-trace-id",
            "c52bce3d5aa54c8eae64e73cb76d8f33",
            "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33\r\nforged: value",
            "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33x"
    })
    void replacesInvalidClientValuesWithoutPropagatingThem(String invalidValue) throws Exception {
        String generated = filterAndCapture(invalidValue);

        assertThat(generated).isNotEqualTo(invalidValue);
        assertUuid(generated);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsMdcWithoutRestoringAStaleValueWhenTheChainFails() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "stale-request-id");
        HttpServletRequest request = requestWithHeader(VALID_REQUEST_ID);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletException failure = new ServletException("downstream failure");
        FilterChain chain = (ignoredRequest, ignoredResponse) -> {
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(VALID_REQUEST_ID);
            throw failure;
        };

        assertThatThrownBy(() -> new RequestIdFilter().doFilter(request, response, chain))
                .isSameAs(failure);

        verify(response).setHeader(RequestIdFilter.HEADER_NAME, VALID_REQUEST_ID);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void errorDtosEnforceTheOpenApiShapeAndImmutableSafeValues() throws Exception {
        assertThat(Arrays.stream(ApiErrorResponse.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("requestId", "code", "message", "retryable", "fieldErrors");
        assertThat(Arrays.stream(FieldErrorResponse.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("field", "message");

        assertThatThrownBy(() -> new FieldErrorResponse(null, "required"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FieldErrorResponse(" ", "required"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldErrorResponse("field", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FieldErrorResponse("field", " "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ApiErrorResponse(null, ErrorCode.PARAM_INVALID, "invalid", false, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiErrorResponse(" ", ErrorCode.PARAM_INVALID, "invalid", false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiErrorResponse(VALID_REQUEST_ID, null, "invalid", false, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiErrorResponse(VALID_REQUEST_ID, ErrorCode.PARAM_INVALID, null, false, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiErrorResponse(VALID_REQUEST_ID, ErrorCode.PARAM_INVALID, " ", false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiErrorResponse(VALID_REQUEST_ID, ErrorCode.PARAM_INVALID, "invalid", true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiErrorResponse(VALID_REQUEST_ID, ErrorCode.PARAM_INVALID, "invalid", false, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiErrorResponse(
                VALID_REQUEST_ID,
                ErrorCode.PARAM_INVALID,
                "invalid",
                false,
                Arrays.asList(new FieldErrorResponse("field", "required"), null)))
                .isInstanceOf(NullPointerException.class);

        List<FieldErrorResponse> source = new ArrayList<>();
        source.add(new FieldErrorResponse("start_date", "must not be later than end_date"));
        ApiErrorResponse response = new ApiErrorResponse(
                VALID_REQUEST_ID, ErrorCode.PARAM_INVALID, "invalid range", false, source);
        source.add(new FieldErrorResponse("end_date", "must not be earlier than start_date"));

        assertThat(response.fieldErrors()).containsExactly(
                new FieldErrorResponse("start_date", "must not be later than end_date"));
        assertThatThrownBy(() -> response.fieldErrors().add(new FieldErrorResponse("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new ObjectMapper().writeValueAsString(response)).isEqualTo(
                "{\"requestId\":\"c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33\","
                        + "\"code\":\"PARAM_INVALID\",\"message\":\"invalid range\","
                        + "\"retryable\":false,\"fieldErrors\":[{\"field\":\"start_date\","
                        + "\"message\":\"must not be later than end_date\"}]}");
    }

    @Test
    void startsTheBootContextWithTheApplicationAndFilterBeans() {
        try (var context = new SpringApplicationBuilder(TensorApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "spring.autoconfigure.exclude="
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                        "tensor.plugins.tushare-pro.enabled=false")
                .run()) {
            assertThat(context.getBean(TensorApplication.class)).isNotNull();
            assertThat(context.getBean(RequestIdFilter.class)).isNotNull();
            Order order = RequestIdFilter.class.getAnnotation(Order.class);
            assertThat(order).isNotNull();
            assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        }
    }

    private static HttpServletRequest requestWithHeader(String value) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(RequestIdFilter.HEADER_NAME)).thenReturn(value);
        return request;
    }

    private static String filterAndCapture(String clientValue) throws Exception {
        HttpServletRequest request = requestWithHeader(clientValue);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> responseHeader = new AtomicReference<>();
        doAnswer(invocation -> {
            responseHeader.set(invocation.getArgument(1));
            return null;
        }).when(response).setHeader(anyString(), anyString());
        AtomicReference<String> chainMdc = new AtomicReference<>();
        FilterChain chain = (ignoredRequest, ignoredResponse) ->
                chainMdc.set(MDC.get(RequestIdFilter.MDC_KEY));

        new RequestIdFilter().doFilter(request, response, chain);

        verify(request).getHeader(RequestIdFilter.HEADER_NAME);
        verify(response).setHeader(RequestIdFilter.HEADER_NAME, responseHeader.get());
        assertThat(chainMdc.get()).isEqualTo(responseHeader.get());
        return responseHeader.get();
    }

    private static void assertUuid(String value) {
        assertThat(value).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        UUID uuid = UUID.fromString(value);
        assertThat(uuid.version()).isEqualTo(4);
        assertThat(uuid.variant()).isEqualTo(2);
    }
}
