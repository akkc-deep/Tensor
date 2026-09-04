package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class GlobalExceptionHandlerTest {
    private static final String REQUEST_ID =
            "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";
    private static final String SENSITIVE =
            "SELECT secret_token FROM /private/internal/path stacktrace";
    private static final ApiDescriptor VALIDATION_API = new ApiDescriptor(
            ApiName.of("validation"),
            "Validation",
            "test",
            QueryMode.trade_date,
            List.of(new ParameterDescriptor(
                    "trade_date",
                    "Trade Date",
                    null,
                    ParameterType.DATE,
                    true,
                    null,
                    List.of(),
                    null,
                    null)));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new RequestIdFilter())
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void mapsEveryDomainCodeToTheFrozenContract(ErrorCode code) throws Exception {
        JsonNode body = perform(identified(get("/test/domain/{code}", code)), expectedStatus(code));

        assertThat(fieldNames(body)).containsExactly(
                "requestId", "code", "message", "retryable", "fieldErrors");
        assertThat(body.get("code").asText()).isEqualTo(code.name());
        assertThat(body.get("message").asText()).isEqualTo(message(code));
        assertThat(body.get("retryable").asBoolean()).isEqualTo(code.retryable());
        assertThat(body.get("fieldErrors")).isEmpty();
        assertNoSensitive(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"required", "invalid"})
    void projectsCoreParameterErrorsWithoutChangingTheirOrder(String kind) throws Exception {
        ErrorCode code = "required".equals(kind)
                ? ErrorCode.PARAM_REQUIRED
                : ErrorCode.PARAM_INVALID;
        JsonNode body = perform(identified(get("/test/core/{kind}", kind)), 400);

        assertError(body, code, message(code));
        assertThat(body.get("fieldErrors")).hasSize(1);
        assertThat(body.get("fieldErrors").get(0).get("field").asText())
                .isEqualTo("trade_date");
        assertThat(body.get("fieldErrors").get(0).get("message").asText())
                .isEqualTo("required".equals(kind) ? "is required" : "has invalid value");
    }

    @Test
    void mapsBeanValidationToUniqueSortedSafeFields() throws Exception {
        JsonNode missing = perform(identified(post("/test/bean")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")), 400);
        assertError(missing, ErrorCode.PARAM_REQUIRED, "Required parameters are missing");
        assertFields(missing,
                "apiName", "is required",
                "params", "is required",
                "pluginId", "is required");

        JsonNode invalid = perform(identified(post("/test/bean")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pluginId\":\"BAD-PLUGIN\",\"apiName\":\"BAD-API\",\"params\":{}}")), 400);
        assertError(invalid, ErrorCode.PARAM_INVALID, "Parameters are invalid");
        assertFields(invalid,
                "apiName", "has invalid value",
                "pluginId", "has invalid value");

        JsonNode mixed = perform(identified(post("/test/bean")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pluginId\":\"BAD-PLUGIN\",\"params\":{}}")), 400);
        assertError(mixed, ErrorCode.PARAM_INVALID, "Parameters are invalid");
        assertFields(mixed,
                "apiName", "is required",
                "pluginId", "has invalid value");

        assertThat(Stream.of(missing, invalid, mixed).map(JsonNode::toString)
                .reduce("", String::concat))
                .doesNotContain("BAD-PLUGIN", "BAD-API");
    }

    @Test
    void mapsMvcAndValueObjectInputFailuresWithoutParserDetails() throws Exception {
        JsonNode missing = perform(identified(get("/test/input")
                .param("number", "1")
                .param("date", "2026-09-04")), 400);
        assertError(missing, ErrorCode.PARAM_REQUIRED, "Required parameters are missing");
        assertFields(missing, "required", "is required");

        JsonNode number = perform(identified(get("/test/input")
                .param("required", "present")
                .param("number", "not-an-integer")
                .param("date", "2026-09-04")), 400);
        assertError(number, ErrorCode.PARAM_INVALID, "Parameters are invalid");
        assertFields(number, "number", "has invalid value");

        JsonNode date = perform(identified(get("/test/input")
                .param("required", "present")
                .param("number", "1")
                .param("date", "not-a-date")), 400);
        assertError(date, ErrorCode.PARAM_INVALID, "Parameters are invalid");
        assertFields(date, "date", "has invalid value");

        JsonNode malformed = perform(identified(post("/test/bean")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{")), 400);
        assertError(malformed, ErrorCode.PARAM_INVALID, "Parameters are invalid");
        assertFields(malformed, "request", "has invalid value");

        JsonNode identifier = perform(identified(get("/test/identifier/BAD-ID")), 400);
        assertError(identifier, ErrorCode.PARAM_INVALID, "Parameters are invalid");
        assertThat(identifier.get("fieldErrors")).isEmpty();

        String json = Stream.of(number, date, malformed, identifier)
                .map(JsonNode::toString)
                .reduce("", String::concat);
        assertThat(json).doesNotContain(
                "not-an-integer", "not-a-date", "BAD-ID", "java.lang", "Jackson", SENSITIVE);
    }

    @ParameterizedTest
    @CsvSource({
        "POST,/api/v1/downloads,PERSISTENCE_FAILED",
        "GET,/api/v1/data-sources/test/datasets/test/records,QUERY_FAILED",
        "GET,/test/unknown,INTERNAL_ERROR"
    })
    void classifiesUntypedFailuresByExactOperation(
            String method, String path, ErrorCode code) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(
                HttpMethod.valueOf(method), URI.create(path));
        JsonNode body = perform(identified(request), 500);

        assertError(body, code, message(code));
        assertThat(body.get("retryable").asBoolean()).isEqualTo(code.retryable());
        assertThat(body.get("fieldErrors")).isEmpty();
        assertNoSensitive(body);
    }

    @Test
    void mapsAnUnknownResponseStatusExceptionToInternalError() throws Exception {
        JsonNode body = perform(identified(get("/test/annotated")), 500);

        assertError(body, ErrorCode.INTERNAL_ERROR, "Internal server error");
        assertThat(body.get("fieldErrors")).isEmpty();
        assertNoSensitive(body);
    }

    @Test
    void exposesOnlyTheApprovedSurfaceAndWritesSanitizedLogs() throws Exception {
        assertThat(Modifier.isFinal(GlobalExceptionHandler.class.getModifiers())).isTrue();
        assertThat(GlobalExceptionHandler.class.getAnnotation(RestControllerAdvice.class)).isNotNull();
        ConditionalOnWebApplication conditional =
                GlobalExceptionHandler.class.getAnnotation(ConditionalOnWebApplication.class);
        assertThat(conditional).isNotNull();
        assertThat(conditional.type()).isEqualTo(ConditionalOnWebApplication.Type.SERVLET);
        assertThat(GlobalExceptionHandler.class.getDeclaredFields()).singleElement().satisfies(field -> {
            assertThat(field.getType()).isEqualTo(org.slf4j.Logger.class);
            assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
            assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        });
        assertThat(Stream.of(GlobalExceptionHandler.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(
                        org.springframework.web.bind.annotation.ExceptionHandler.class)))
                .hasSize(7);

        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            perform(identified(get("/test/domain/PARAM_INVALID")), 400);
            perform(identified(get("/test/unknown")), 500);

            List<ILoggingEvent> warnings = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .toList();
            List<ILoggingEvent> errors = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .toList();
            assertThat(warnings).singleElement().satisfies(event -> {
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).contains(
                        "Request rejected", REQUEST_ID, "PARAM_INVALID", TestTensorException.class.getName());
            });
            assertThat(errors).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains(
                        "Request failed", REQUEST_ID, "INTERNAL_ERROR", IllegalStateException.class.getName());
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getMessage())
                        .isEqualTo("Request failure details redacted");
                assertThat(event.getThrowableProxy().getCause()).isNull();
                assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
            });
            String logged = appender.list.stream()
                    .map(event -> event.getFormattedMessage()
                            + (event.getThrowableProxy() == null
                                    ? ""
                                    : event.getThrowableProxy().getMessage()))
                    .reduce("", String::concat);
            assertThat(logged).doesNotContain(
                    "SELECT", "secret_token", "/private/internal/path", "stacktrace");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private JsonNode perform(RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER_NAME))
                .isEqualTo(REQUEST_ID);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("requestId").asText()).isEqualTo(REQUEST_ID);
        return body;
    }

    private static MockHttpServletRequestBuilder identified(
            MockHttpServletRequestBuilder request) {
        return request.header(RequestIdFilter.HEADER_NAME, REQUEST_ID);
    }

    private static void assertError(JsonNode body, ErrorCode code, String expectedMessage) {
        assertThat(body.get("code").asText()).isEqualTo(code.name());
        assertThat(body.get("message").asText()).isEqualTo(expectedMessage);
        assertThat(body.get("retryable").asBoolean()).isEqualTo(code.retryable());
    }

    private static void assertFields(JsonNode body, String... fieldAndMessage) {
        assertThat(fieldAndMessage.length % 2).isZero();
        JsonNode fields = body.get("fieldErrors");
        assertThat(fields).hasSize(fieldAndMessage.length / 2);
        for (int index = 0; index < fieldAndMessage.length; index += 2) {
            assertThat(fields.get(index / 2).get("field").asText())
                    .isEqualTo(fieldAndMessage[index]);
            assertThat(fields.get(index / 2).get("message").asText())
                    .isEqualTo(fieldAndMessage[index + 1]);
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void assertNoSensitive(JsonNode body) {
        assertThat(body.toString().toLowerCase()).doesNotContain(
                "select", "secret_token", "/private/", "stacktrace", "tensorexception", "java.");
    }

    private static int expectedStatus(ErrorCode code) {
        return switch (code) {
            case PARAM_REQUIRED, PARAM_INVALID -> 400;
            case PLUGIN_DISABLED, DATASET_MISCONFIGURED -> 409;
            case ADAPTER_FIELD_MISSING, ADAPTER_TYPE_INVALID -> 422;
            case PERSISTENCE_FAILED, QUERY_FAILED, INTERNAL_ERROR -> 500;
            case SOURCE_AUTH_FAILED,
                    SOURCE_PERMISSION_DENIED,
                    SOURCE_RATE_LIMITED,
                    SOURCE_UNAVAILABLE,
                    SOURCE_NETWORK_ERROR,
                    SOURCE_PAYLOAD_INVALID -> 502;
            case SOURCE_TIMEOUT -> 504;
        };
    }

    private static String message(ErrorCode code) {
        return switch (code) {
            case PARAM_REQUIRED -> "Required parameters are missing";
            case PARAM_INVALID -> "Parameters are invalid";
            case PLUGIN_DISABLED -> "Plugin is unavailable";
            case DATASET_MISCONFIGURED -> "Dataset metadata is unavailable";
            case SOURCE_AUTH_FAILED -> "Source authentication failed";
            case SOURCE_PERMISSION_DENIED -> "Source permission denied";
            case SOURCE_RATE_LIMITED -> "Source rate limit exceeded";
            case SOURCE_UNAVAILABLE -> "Source is unavailable";
            case SOURCE_NETWORK_ERROR -> "Source network request failed";
            case SOURCE_TIMEOUT -> "Source request timed out";
            case SOURCE_PAYLOAD_INVALID -> "Source returned an invalid payload";
            case ADAPTER_FIELD_MISSING -> "Source data is missing a required field";
            case ADAPTER_TYPE_INVALID -> "Source data contains an invalid value";
            case PERSISTENCE_FAILED -> "Persistence failed";
            case QUERY_FAILED -> "Query failed";
            case INTERNAL_ERROR -> "Internal server error";
        };
    }

    @RestController
    private static final class FailureController {
        private final ParameterValidator validator = new ParameterValidator();

        @GetMapping("/test/domain/{code}")
        void domain(@PathVariable("code") String code) {
            throw new TestTensorException(ErrorCode.valueOf(code));
        }

        @GetMapping("/test/core/{kind}")
        void core(@PathVariable("kind") String kind) {
            Map<String, Object> values = "required".equals(kind)
                    ? Map.of()
                    : Map.of("trade_date", "not-a-date");
            validator.validate(VALIDATION_API, values);
        }

        @PostMapping("/test/bean")
        void bean(@Valid @RequestBody DownloadRequest request) {
        }

        @GetMapping("/test/input")
        void input(
                @RequestParam("required") String required,
                @RequestParam("number") int number,
                @RequestParam("date")
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                        LocalDate date) {
        }

        @PostMapping("/api/v1/downloads")
        void persistence() {
            throw new DataIntegrityViolationException(SENSITIVE);
        }

        @GetMapping("/api/v1/data-sources/test/datasets/test/records")
        void query() {
            throw new IllegalStateException(SENSITIVE);
        }

        @GetMapping("/test/unknown")
        void unknown() {
            throw new IllegalStateException(SENSITIVE);
        }

        @GetMapping("/test/identifier/{value}")
        void identifier(@PathVariable("value") String value) {
            PluginId.of(value);
        }

        @GetMapping("/test/annotated")
        void annotated() {
            throw new TeapotException();
        }
    }

    @ResponseStatus(HttpStatus.I_AM_A_TEAPOT)
    private static final class TeapotException extends RuntimeException {
        private TeapotException() {
            super(SENSITIVE);
        }
    }

    private static final class TestTensorException extends TensorException {
        private TestTensorException(ErrorCode code) {
            super(code, SENSITIVE);
        }
    }
}
