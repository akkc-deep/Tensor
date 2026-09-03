package com.akkc.tensor.plugin.tushare.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

class TushareProClientTest {
    private static final String SECRET = "m07-t02-secret-sentinel";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> DAILY_FIELDS = List.of(
            "ts_code", "trade_date", "open", "high", "low", "close",
            "pre_close", "change", "pct_chg", "vol", "amount");
    private static final String SUCCESS_JSON = """
            {"code":0,"msg":null,"request_id":"ignored","data":{"fields":["ts_code","trade_date","open","high","low","close","pre_close","change","pct_chg","vol","amount"],"items":[["000001.SZ","20260902",1,2,3,4,5,6,7,8,9],["000002.SZ","20260902",10,11,12,13,14,15,16,17,18]]}}
            """.strip();
    private static final String EMPTY_JSON = """
            {"code":0,"msg":null,"data":{"fields":["ts_code","trade_date","open","high","low","close","pre_close","change","pct_chg","vol","amount"],"items":[]}}
            """.strip();

    @RegisterExtension
    private final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void exposesOnlyTheSpecifiedSurfaceAndRedactsProtocolDtos() throws Exception {
        assertThat(Modifier.isPublic(TushareProClient.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(TushareProClient.class.getModifiers())).isTrue();
        assertThat(TushareProClient.class.getConstructors()).hasSize(1);
        assertThat(TushareProClient.class.getConstructors()[0].getParameterTypes())
                .containsExactly(RestClient.class, TushareProperties.class);
        assertThat(publicDeclaredMethods(TushareProClient.class)).extracting(Method::getName)
                .containsExactly("execute");
        assertThat(publicDeclaredMethods(TushareProClient.class)[0].getParameterTypes())
                .containsExactly(DatasetDefinition.class, Map.class);
        assertThat(publicDeclaredMethods(TushareProClient.class)[0].getReturnType())
                .isEqualTo(DownloadEnvelope.class);

        Field[] instanceFields = java.util.Arrays.stream(TushareProClient.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toArray(Field[]::new);
        assertThat(instanceFields).extracting(Field::getType)
                .containsExactlyInAnyOrder(RestClient.class, TushareProperties.class);
        assertThat(instanceFields).allMatch(field -> Modifier.isPrivate(field.getModifiers())
                && Modifier.isFinal(field.getModifiers()));
        assertThat(TushareProClient.class.getDeclaredFields())
                .filteredOn(field -> Modifier.isStatic(field.getModifiers()))
                .singleElement()
                .matches(field -> field.getType().equals(ObjectMapper.class)
                        && Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()));

        assertThat(List.of(TushareRequest.class, TushareResponse.class, TushareData.class,
                TushareResponseValidator.class))
                .allMatch(type -> !Modifier.isPublic(type.getModifiers()) && Modifier.isFinal(type.getModifiers()));
        assertThat(TushareRequest.class.isRecord()).isTrue();
        assertThat(TushareResponse.class.isRecord()).isTrue();
        assertThat(TushareData.class.isRecord()).isTrue();
        assertThat(TushareRequest.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("apiName", "token", "params", "fields");
        assertThat(TushareRequest.class.getAnnotation(JsonPropertyOrder.class).value())
                .containsExactly("api_name", "token", "params", "fields");
        assertThat(TushareRequest.class.getDeclaredMethod("apiName").getAnnotation(JsonProperty.class).value())
                .isEqualTo("api_name");
        assertThat(TushareResponse.class.getAnnotation(JsonIgnoreProperties.class).ignoreUnknown()).isTrue();
        assertThat(TushareData.class.getAnnotation(JsonIgnoreProperties.class).ignoreUnknown()).isTrue();
        assertThat(publicDeclaredMethods(TushareRequest.class)).extracting(Method::getName)
                .containsExactlyInAnyOrder("apiName", "token", "params", "fields", "equals", "hashCode", "toString");
        assertThat(publicDeclaredMethods(TushareResponse.class)).extracting(Method::getName)
                .containsExactlyInAnyOrder("code", "msg", "data", "equals", "hashCode", "toString");
        assertThat(publicDeclaredMethods(TushareData.class)).extracting(Method::getName)
                .containsExactlyInAnyOrder("fields", "items", "equals", "hashCode", "toString");
        Method validate = TushareResponseValidator.class.getDeclaredMethod(
                "validate", DatasetDefinition.class, Map.class, TushareResponse.class);
        assertTrue(!Modifier.isPublic(validate.getModifiers()) && Modifier.isStatic(validate.getModifiers()),
                "validator exposes only its package-private static operation");

        Map<String, Object> mutableParams = new LinkedHashMap<>(Map.of("trade_date", "20260902"));
        TushareRequest request = new TushareRequest("daily", SECRET, mutableParams, String.join(",", DAILY_FIELDS));
        mutableParams.put("ts_code", "000001.SZ");
        TushareData data = new TushareData(DAILY_FIELDS, List.of());
        TushareResponse response = new TushareResponse(0, SECRET, data);
        assertTrue(Map.of("trade_date", "20260902").equals(request.params()), "request copies parameters");
        assertTrue("TushareRequest[REDACTED]".equals(request.toString()), "request string is fixed and redacted");
        assertTrue("TushareResponse[REDACTED]".equals(response.toString()), "response string is fixed and redacted");
        assertTrue("TushareData[REDACTED]".equals(data.toString()), "data string is fixed and redacted");
        assertTrue(!request.toString().contains(SECRET) && !response.toString().contains(SECRET),
                "protocol strings omit upstream secrets");
        assertRequestNullRejected(null, SECRET, Map.of(), "x", "apiName");
        assertRequestNullRejected("daily", null, Map.of(), "x", "token");
        assertRequestNullRejected("daily", SECRET, null, "x", "params");
        assertRequestNullRejected("daily", SECRET, Map.of(), null, "fields");
    }

    @Test
    void sendsExactRequestAndReturnsSuccessfulEnvelope() {
        stub(HttpStatus.OK.value(), SUCCESS_JSON);
        DatasetDefinition definition = dailyDefinition();
        Map<String, Object> params = Map.of("trade_date", "20260902");

        DownloadEnvelope envelope = client(1_024 * 1_024).execute(definition, params);

        assertTrue("tushare_pro".equals(envelope.pluginId().value()), "envelope has the metadata plugin ID");
        assertTrue("daily".equals(envelope.apiName().value()), "envelope has the metadata API name");
        assertThat(envelope.params()).isEqualTo(params);
        assertThat(envelope.fields()).containsExactlyElementsOf(DAILY_FIELDS);
        assertThat(envelope.data()).containsExactly(
                List.of("000001.SZ", "20260902", 1, 2, 3, 4, 5, 6, 7, 8, 9),
                List.of("000002.SZ", "20260902", 10, 11, 12, 13, 14, 15, 16, 17, 18));
        assertThat(envelope.rowCount()).isEqualTo(2);
        assertThat(envelope.status()).isEqualTo(DownloadStatus.SUCCESS);
        assertThat(envelope.error()).isNull();

        List<com.github.tomakehurst.wiremock.stubbing.ServeEvent> events = wireMock.getAllServeEvents();
        assertTrue(events.size() == 1, "exactly one request reaches the upstream server");
        if (events.size() == 1) {
            var request = events.getFirst().getRequest();
            boolean requestLineIsSafe = "POST".equals(request.getMethod().getName())
                    && "/".equals(request.getUrl())
                    && !request.getAbsoluteUrl().contains(SECRET);
            assertTrue(requestLineIsSafe, "request uses POST at the configured root without the credential in its URI");
            assertTrue("application/json".equals(request.getHeader("Accept")), "request accepts JSON");
            assertTrue(request.getHeader("Content-Type").startsWith("application/json"), "request sends JSON");
            assertTrue(request.getHeader("Authorization") == null && request.getHeader("Cookie") == null,
                    "request omits credential headers and cookies");
            assertTrue(request.getHeaders().all().stream()
                    .noneMatch(header -> header.values().contains(SECRET)), "all request headers omit the credential");
            assertTrue(hasExactSafeRequestBody(request.getBody()),
                    "request body contains only the ordered protocol fields and the method-local credential");
        }
    }

    @Test
    void returnsSuccessfulEnvelopeForLegalEmptyItems() {
        stub(HttpStatus.OK.value(), EMPTY_JSON);

        DownloadEnvelope envelope = client(1_024 * 1_024)
                .execute(dailyDefinition(), Map.of("trade_date", "20260902"));

        assertThat(envelope.fields()).containsExactlyElementsOf(DAILY_FIELDS);
        assertThat(envelope.data()).isEmpty();
        assertThat(envelope.rowCount()).isZero();
        assertThat(envelope.status()).isEqualTo(DownloadStatus.SUCCESS);
        assertThat(envelope.error()).isNull();
    }

    @Test
    void classifiesHttpAndTransportFailuresWithoutReadingOrLeaking() {
        stub(HttpStatus.SERVICE_UNAVAILABLE.value(), "{" + SECRET);

        Throwable failure = catchThrowable(() -> client(1_024 * 1_024)
                .execute(dailyDefinition(), Map.of("trade_date", "20260902")));

        assertSourceFailure(failure, ErrorCode.SOURCE_UNAVAILABLE, "Tushare service is unavailable");
        assertTrue(!String.valueOf(failure).contains(SECRET), "HTTP failure omits the response body and credential");
        assertTrue(!String.valueOf(failure).contains("503"), "HTTP failure omits the upstream status");
        assertTrue(!String.valueOf(failure).contains(wireMock.baseUrl()), "HTTP failure omits the upstream URI");
        assertTrue(wireMock.getAllServeEvents().size() == 1, "HTTP failure is not retried");

        RestClient unreachable = RestClient.builder()
                .baseUrl("https://m07-t03.invalid")
                .requestFactory((uri, method) -> {
                    throw new UnknownHostException(SECRET);
                })
                .build();
        Throwable networkFailure = catchThrowable(() -> client(unreachable, 1_024 * 1_024)
                .execute(dailyDefinition(), Map.of("trade_date", "20260902")));
        assertSourceFailure(networkFailure, ErrorCode.SOURCE_NETWORK_ERROR,
                "Tushare could not be reached");
        assertTrue(!String.valueOf(networkFailure).contains(SECRET)
                && !String.valueOf(networkFailure).contains("m07-t03.invalid"),
                "network failure omits the transport message and URI");

        InputStream timedOutBody = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new SocketTimeoutException(SECRET);
            }
        };
        Throwable timeoutFailure = catchThrowable(() -> client(restClientReturning(timedOutBody), 1_024 * 1_024)
                .execute(dailyDefinition(), Map.of("trade_date", "20260902")));
        assertSourceFailure(timeoutFailure, ErrorCode.SOURCE_TIMEOUT, "Tushare response timed out");
        assertTrue(!String.valueOf(timeoutFailure).contains(SECRET),
                "response read timeout omits the transport message");
    }

    @Test
    void rejectsInvalidJsonWithoutLeakingUntrustedContent() {
        List<String> invalidBodies = List.of(
                "",
                "{" + SECRET,
                "{\"code\":0,\"code\":0,\"data\":{\"fields\":[],\"items\":[]}}",
                "{\"code\":\"0\",\"data\":{\"fields\":[],\"items\":[]}}",
                EMPTY_JSON + " {}"
        );

        for (String body : invalidBodies) {
            Throwable failure = executeFailure(HttpStatus.OK.value(), body);
            assertSourceFailure(failure, ErrorCode.SOURCE_PAYLOAD_INVALID,
                    "Tushare returned an invalid payload");
            assertTrue(!String.valueOf(failure).contains(SECRET), "JSON failure omits untrusted response content");
        }
    }

    @Test
    void validatesBusinessCodeBeforeObservingDataOrMessage() {
        Throwable missingCode = executeFailure(HttpStatus.OK.value(), "{\"data\":null}");
        assertSourceFailure(missingCode, ErrorCode.SOURCE_PAYLOAD_INVALID,
                "Tushare returned an invalid payload");

        Throwable businessFailure = executeFailure(HttpStatus.OK.value(),
                "{\"code\":-2001,\"msg\":\"" + SECRET + "\"}");
        assertSourceFailure(businessFailure, ErrorCode.SOURCE_PAYLOAD_INVALID,
                "Tushare returned an invalid payload");
        assertTrue(!String.valueOf(businessFailure).contains("-2001")
                && !String.valueOf(businessFailure).contains(SECRET),
                "business failure omits the upstream code and message");

        Throwable authFailure = executeFailure(HttpStatus.OK.value(),
                "{\"code\":-2002,\"msg\":\"TOKEN " + SECRET + "\"}");
        assertSourceFailure(authFailure, ErrorCode.SOURCE_AUTH_FAILED,
                "Tushare credentials were rejected");
        assertTrue(!String.valueOf(authFailure).contains("-2002")
                && !String.valueOf(authFailure).contains(SECRET),
                "classified business failure omits the upstream code and message");
    }

    @Test
    void validatesDataFieldsAndItemsInOrder() {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("{\"code\":0}", "Tushare returned an invalid payload");
        cases.put("{\"code\":0,\"data\":{}}", "Tushare returned an invalid payload");
        cases.put("{\"code\":0,\"data\":{\"fields\":[]}}", "Tushare returned an invalid payload");

        cases.forEach((body, expectedMessage) ->
                assertSourceFailure(executeFailure(HttpStatus.OK.value(), body),
                        ErrorCode.SOURCE_PAYLOAD_INVALID, expectedMessage));
    }

    @Test
    void rejectsNullDuplicateOrMismatchedFieldsWithoutRepairingThem() {
        List<String> withNull = new ArrayList<>(DAILY_FIELDS);
        withNull.set(2, null);
        List<String> withDuplicate = new ArrayList<>(DAILY_FIELDS);
        withDuplicate.set(2, "ts_code");
        List<String> wrongSet = new ArrayList<>(DAILY_FIELDS);
        wrongSet.set(10, "unexpected_field");
        List<String> wrongOrder = new ArrayList<>(DAILY_FIELDS);
        java.util.Collections.swap(wrongOrder, 0, 1);

        assertPayloadFailure(executeFailure(HttpStatus.OK.value(), successWith(withNull, "[]")));
        assertPayloadFailure(executeFailure(HttpStatus.OK.value(), successWith(withDuplicate, "[]")));
        assertPayloadFailure(executeFailure(HttpStatus.OK.value(), successWith(wrongSet, "[]")));
        assertPayloadFailure(executeFailure(HttpStatus.OK.value(), successWith(wrongOrder, "[]")));
    }

    @Test
    void rejectsMissingOrWrongWidthRowsAndPreservesLegalNullCells() {
        assertPayloadFailure(executeFailure(HttpStatus.OK.value(), successWith(DAILY_FIELDS, "[null]")));
        assertPayloadFailure(executeFailure(HttpStatus.OK.value(), successWith(DAILY_FIELDS,
                "[[\"000001.SZ\",\"20260902\",1,2,3,4,5,6,7,8]]")));
        assertPayloadFailure(executeFailure(HttpStatus.OK.value(), successWith(DAILY_FIELDS,
                "[[\"000001.SZ\",\"20260902\",1,2,3,4,5,6,7,8,9,10]]")));

        stub(HttpStatus.OK.value(), successWith(DAILY_FIELDS,
                "[[\"000001.SZ\",\"20260902\",null,2,3,4,5,6,7,8,9]]"));
        DownloadEnvelope envelope = client(1_024 * 1_024)
                .execute(dailyDefinition(), Map.of("trade_date", "20260902"));
        assertTrue(envelope.rowCount() == 1 && envelope.data().getFirst().get(2) == null,
                "legal null cells are preserved in the successful envelope");
    }

    @Test
    void enforcesTheActualResponseSizeBeforeParsing() {
        int exactBytes = EMPTY_JSON.getBytes(StandardCharsets.UTF_8).length;
        stub(HttpStatus.OK.value(), EMPTY_JSON);
        DownloadEnvelope exact = client(exactBytes)
                .execute(dailyDefinition(), Map.of("trade_date", "20260902"));
        assertThat(exact.rowCount()).isZero();

        stub(HttpStatus.OK.value(), EMPTY_JSON + " ");
        Throwable oversized = catchThrowable(() -> client(exactBytes)
                .execute(dailyDefinition(), Map.of("trade_date", "20260902")));
        assertPayloadFailure(oversized);
    }

    private Method[] publicDeclaredMethods(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);
    }

    private void assertRequestNullRejected(String apiName, String token, Map<String, Object> params,
                                           String fields, String component) {
        Throwable failure = catchThrowable(() -> new TushareRequest(apiName, token, params, fields));
        assertTrue(failure instanceof NullPointerException && component.equals(failure.getMessage()),
                "request rejects a null " + component + " with its fixed component name");
    }

    private boolean hasExactSafeRequestBody(byte[] body) {
        try {
            JsonNode root = JSON.readTree(body);
            List<String> names = new ArrayList<>();
            root.fieldNames().forEachRemaining(names::add);
            return List.of("api_name", "token", "params", "fields").equals(names)
                    && root.size() == 4
                    && "daily".equals(root.path("api_name").textValue())
                    && SECRET.equals(root.path("token").textValue())
                    && root.path("params").size() == 1
                    && "20260902".equals(root.path("params").path("trade_date").textValue())
                    && String.join(",", DAILY_FIELDS).equals(root.path("fields").textValue());
        } catch (Exception ignored) {
            return false;
        }
    }

    private Throwable executeFailure(int status, String body) {
        stub(status, body);
        return catchThrowable(() -> client(1_024 * 1_024)
                .execute(dailyDefinition(), Map.of("trade_date", "20260902")));
    }

    private void assertPayloadFailure(Throwable failure) {
        assertSourceFailure(failure, ErrorCode.SOURCE_PAYLOAD_INVALID,
                "Tushare returned an invalid payload");
    }

    private void assertSourceFailure(Throwable failure, ErrorCode code, String expectedMessage) {
        assertTrue(failure instanceof SourceException, "upstream failure uses the source exception type");
        SourceException sourceFailure = (SourceException) failure;
        assertThat(sourceFailure.code()).isEqualTo(code);
        assertTrue(expectedMessage.equals(sourceFailure.getMessage()),
                "upstream failure uses its fixed safe message");
        assertThat(sourceFailure.retryable()).isEqualTo(code.retryable());
        assertTrue(sourceFailure.getCause() == null, "upstream failure omits its cause");
        assertTrue(sourceFailure.getSuppressed().length == 0, "upstream failure omits suppressed failures");
    }

    private String successWith(List<String> fields, String itemsJson) {
        try {
            return "{\"code\":0,\"msg\":null,\"data\":{\"fields\":"
                    + JSON.writeValueAsString(fields) + ",\"items\":" + itemsJson + "}}";
        } catch (Exception ignored) {
            throw new AssertionError("test response cannot be encoded");
        }
    }

    private void stub(int status, String body) {
        wireMock.resetAll();
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private TushareProClient client(int maxResponseBytes) {
        TushareProperties properties = new TushareProperties(
                true,
                URI.create(wireMock.baseUrl()),
                new TushareProperties.Credential(SECRET),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                maxResponseBytes);
        RestClient restClient = new TushareRestClientFactory().create(properties);
        return new TushareProClient(restClient, properties);
    }

    private TushareProClient client(RestClient restClient, int maxResponseBytes) {
        TushareProperties properties = new TushareProperties(
                true,
                URI.create("https://m07-t03.invalid"),
                new TushareProperties.Credential(SECRET),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                maxResponseBytes);
        return new TushareProClient(restClient, properties);
    }

    private RestClient restClientReturning(InputStream body) {
        return RestClient.builder()
                .baseUrl("https://m07-t03.invalid")
                .requestFactory((uri, method) -> new AbstractClientHttpRequest() {
                    @Override
                    public HttpMethod getMethod() {
                        return method;
                    }

                    @Override
                    public URI getURI() {
                        return uri;
                    }

                    @Override
                    protected OutputStream getBodyInternal(HttpHeaders headers) {
                        return new ByteArrayOutputStream();
                    }

                    @Override
                    protected ClientHttpResponse executeInternal(HttpHeaders headers) {
                        return new ClientHttpResponse() {
                            @Override
                            public HttpStatus getStatusCode() {
                                return HttpStatus.OK;
                            }

                            @Override
                            public String getStatusText() {
                                return "OK";
                            }

                            @Override
                            public HttpHeaders getHeaders() {
                                return new HttpHeaders();
                            }

                            @Override
                            public InputStream getBody() {
                                return body;
                            }

                            @Override
                            public void close() {}
                        };
                    }
                })
                .build();
    }

    private DatasetDefinition dailyDefinition() {
        return new DatasetDefinitionLoader()
                .loadAll(new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml")
                .stream()
                .filter(definition -> "daily".equals(definition.datasetKey().apiName().value()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("daily dataset definition is available"));
    }
}
