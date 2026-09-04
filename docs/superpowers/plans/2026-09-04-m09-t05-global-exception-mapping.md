# M09-T05 Global Exception and HTTP Mapping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement one Servlet global exception boundary that maps the frozen 16 Tensor error codes, validation failures, persistence/query failures, and unknown exceptions to safe `ApiErrorResponse` values with correlated request IDs.

**Architecture:** A final `@RestControllerAdvice` uses exhaustive code switches for domain status/message mapping, projects only safe field names/messages, and classifies untyped persistence/query failures from the two fixed API routes left by M09-T03/M09-T04. The existing `RequestIdFilter` remains the sole request-ID producer; client output never uses Throwable messages, while 5xx logs retain only a redacted stack copy. One standalone MockMvc test supplies real filtering/validation and test-only failure endpoints without changing any existing Controller or dependency.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Framework 6.2.19, Jakarta Bean Validation, SLF4J/Logback, JUnit Jupiter 5.12.2, AssertJ, MockMvc, Jackson.

**Spec:** `docs/task-designs/M09-T05-design.md`

## Global Constraints

- The authoritative design is `docs/task-designs/M09-T05-design.md`; do not broaden its exact two-file implementation scope.
- Create only `GlobalExceptionHandler.java` and `GlobalExceptionHandlerTest.java`; do not modify POMs, contracts, TRD, existing Java/tests, resources, migrations, Core, or plugins.
- Preserve the M00 frozen 16-code matrix exactly; emit only 400/409/422/500/502/504 and do not add or repurpose a code for TRD's broad 404/503 categories.
- Use strict TDD: create the complete test first, observe a `testCompile` RED caused only by the missing production handler, then add the handler.
- Reuse `ApiErrorResponse`, `FieldErrorResponse`, `ErrorCode.retryable()`, and `RequestIdFilter.MDC_KEY`; never create a second error DTO or request ID.
- Never return or log raw Throwable messages, causes, SQL, request values, bodies, headers, cookies, Token values, upstream payloads, or absolute internal paths.
- Preserve Core `ParameterValidationException.fieldErrors()` order; normalize Bean Validation and MVC failures only as specified by the design.
- Distinguish untyped persistence/query failures only by the fixed method/path and exception-type rules; never branch on exception messages or private Controller exception types.
- The implementation commit must contain exactly the two Java files and use `feat(api): map domain errors safely`.

---

### Task 1: Implement the M09-T05 global exception boundary

**Files:**
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/GlobalExceptionHandlerTest.java`
- Create: `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `TensorException.code()`, `ErrorCode.retryable()`, `ParameterValidator.ParameterValidationException.fieldErrors()`, `RequestIdFilter.MDC_KEY`, `ApiErrorResponse(String, ErrorCode, String, boolean, List<FieldErrorResponse>)`, and Spring MVC exception types.
- Produces: one Servlet-only `GlobalExceptionHandler`; six `@ExceptionHandler` method groups for domain, Bean Validation, missing parameter, type mismatch, unreadable body, and catch-all failures; safe JSON for every current API error.

- [ ] **Step 1: Re-read authoritative inputs and confirm the clean 295-test baseline**

Read in this exact order:

```text
docs/task-designs/M09-T05-design.md
docs/superpowers/plans/2026-09-04-m09-t05-global-exception-mapping.md
docs/task-handoffs/M09-T05-handoff.md
docs/task-handoffs/tensor-v1-task-board.md (M09-T05 row and detail)
docs/superpowers/plans/tensor-modules/M09-app-api.md (Global Constraints, Task M09-T05, Module Gate)
docs/contracts/error-codes.md
docs/contracts/openapi-v1.yaml (all error responses, ApiError, FieldError)
docs/design/Tensor_多源证券数据平台_TRD_v1.0.md (12.5 and 12.6)
docs/task-designs/M09-T02-design.md
docs/task-designs/M09-T03-design.md
docs/task-designs/M09-T04-design.md
data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java
data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/TensorException.java
data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiErrorResponse.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/FieldErrorResponse.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java
data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java
```

Run in an environment that permits Mockito/Byte Buddy self-attach:

```bash
git status --short
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
```

Expected: Git status is empty; Maven reports plugin-api 79, core 75, Tushare 93, fixture 12, app 36, total 295/295, with zero failures/errors/skips and all six Enforcer executions, app ArchUnit, and forbidden-Git tests successful.

- [ ] **Step 2: Create the complete 25-invocation test contract before production code**

Create `GlobalExceptionHandlerTest.java` in package `com.akkc.tensor.web`. Use these fixed identities and sensitive sentinel:

```java
private static final String REQUEST_ID =
        "c52bce3d-5aa5-4c8e-ae64-e73cb76d8f33";
private static final String SENSITIVE =
        "SELECT secret_token FROM /private/internal/path stacktrace";

private final ObjectMapper objectMapper = new ObjectMapper();
private MockMvc mockMvc;

@BeforeEach
void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(Validation.buildDefaultValidatorFactory().getValidator())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .addFilters(new RequestIdFilter())
            .build();
}

@AfterEach
void clearMdc() {
    MDC.clear();
}
```

The test file defines only test-local support types. Use one minimal exception to exercise all domain codes without adding a production exception:

```java
private static final class TestTensorException extends TensorException {
    private TestTensorException(ErrorCode code) {
        super(code, SENSITIVE);
    }
}
```

Use this real Core descriptor for the two field-error cases; do not reflectively construct the private `ParameterValidationException` constructor:

```java
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
```

Create one nested `@RestController` with these exact routes and failures:

```java
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
```

Add exactly these seven test methods, whose parameterized sources yield 25 Surefire invocations:

1. `mapsEveryDomainCodeToTheFrozenContract` is `@ParameterizedTest @EnumSource(ErrorCode.class)` and therefore runs 16 times. For each code, perform `GET /test/domain/{code}` with the fixed request header. Assert the exact status/message table from the design, body field order `requestId,code,message,retryable,fieldErrors`, `retryable == code.retryable()`, empty fields, and Header/body ID equality. Lowercase response JSON must not contain `select`, `secret_token`, `/private/`, `stacktrace`, `tensorException`, or `java.`.
2. `projectsCoreParameterErrorsWithoutChangingTheirOrder` is `@ParameterizedTest @ValueSource(strings = {"required", "invalid"})` and runs twice. Call `/test/core/{kind}`; assert `PARAM_REQUIRED` with `trade_date/is required`, or `PARAM_INVALID` with `trade_date/has invalid value`, matching the real Core exception's field order and text exactly.
3. `mapsBeanValidationToUniqueSortedSafeFields` is one `@Test`. POST three JSON bodies to `/test/bean`: all three fields absent, two syntactically invalid IDs with non-null params, and a mixed missing/invalid body. Assert pure missing is `PARAM_REQUIRED`, any non-missing constraint makes the response `PARAM_INVALID`, each field appears once in lexical order, and messages are only `is required|has invalid value`; rejected strings never appear.
4. `mapsMvcInputFailuresWithoutParserDetails` is one `@Test`. Call `/test/input` once without `required`, once with `number=not-an-integer`, once with `date=not-a-date`, then POST malformed JSON to `/test/bean`. Assert the exact `PARAM_REQUIRED|PARAM_INVALID` field/message rules and verify the invalid values, Java target types, Jackson messages, and sensitive sentinel never appear.
5. `classifiesUntypedFailuresByExactOperation` is `@ParameterizedTest @CsvSource` with exactly three rows: `POST,/api/v1/downloads,PERSISTENCE_FAILED`; `GET,/api/v1/data-sources/test/datasets/test/records,QUERY_FAILED`; `GET,/test/unknown,INTERNAL_ERROR`. Build each request with `MockMvcRequestBuilders.request(HttpMethod.valueOf(method), URI.create(path))`; assert 500, the selected code, fixed message, retryable truth, empty fields, and no sensitive response content.
6. `mapsAnUnknownResponseStatusExceptionToInternalError` is one `@Test`. Call `/test/annotated` and assert the catch-all returns fixed `500 + INTERNAL_ERROR`, proving the mapping does not delegate to annotations or messages.
7. `exposesOnlyTheApprovedSurfaceAndWritesSanitizedLogs` is one `@Test`. Assert the advice class is final and has `@RestControllerAdvice` plus `@ConditionalOnWebApplication(SERVLET)`; assert its only field is a private static final SLF4J logger and exactly six methods carry `@ExceptionHandler`. Attach a Logback `ListAppender<ILoggingEvent>` to `LoggerFactory.getLogger(GlobalExceptionHandler.class)`, make one 400 domain request and one 500 unknown request, then assert one WARN has no Throwable, one ERROR has a Throwable proxy whose message is exactly `Request failure details redacted`, cause is null, and stack frames are non-empty. Concatenate formatted messages and Throwable messages and assert none contain `SELECT`, `secret_token`, `/private/internal/path`, or the raw `stacktrace` sentinel. Always detach/stop the appender in `finally`.

Use one helper for every JSON request so the correlation contract cannot be skipped:

```java
private JsonNode perform(RequestBuilder request, int status) throws Exception {
    MvcResult result = mockMvc.perform(request)
            .andExpect(status().is(status))
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
```

Do not mock `GlobalExceptionHandler`, `RequestIdFilter`, Validator, ErrorCode, DTOs, or logger. Do not add production test hooks, a second advice, a POM dependency, or a full Boot context.

- [ ] **Step 3: Run the test-only state and verify the strict RED**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=GlobalExceptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-zero at `tensor-app:testCompile`; diagnostics identify only missing `GlobalExceptionHandler`. Fix test imports, signatures, route ambiguity, Validation wiring, or Logback access before continuing if any other failure appears. Save the complete output as `/private/tmp/m09-t05-red.log`; do not add it to Git.

- [ ] **Step 4: Implement the minimal global handler**

Create `GlobalExceptionHandler.java` with this exact structure and behavior:

```java
package com.akkc.tensor.web;

import com.akkc.tensor.core.validation.ParameterValidator.ParameterValidationException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.web.dto.ApiErrorResponse;
import com.akkc.tensor.web.dto.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TensorException.class)
    ResponseEntity<ApiErrorResponse> handleTensorException(TensorException exception) {
        List<FieldErrorResponse> fields = exception instanceof ParameterValidationException validation
                ? validation.fieldErrors().stream()
                        .map(field -> new FieldErrorResponse(field.field(), field.message()))
                        .toList()
                : List.of();
        return response(exception.code(), fields, exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException exception) {
        Map<String, Boolean> invalidByField = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(field ->
                invalidByField.merge(
                        field.getField(),
                        !isRequiredConstraint(field.getCode()),
                        Boolean::logicalOr));
        ErrorCode code = invalidByField.values().stream().anyMatch(Boolean::booleanValue)
                ? ErrorCode.PARAM_INVALID
                : ErrorCode.PARAM_REQUIRED;
        List<FieldErrorResponse> fields = invalidByField.entrySet().stream()
                .map(entry -> new FieldErrorResponse(
                        entry.getKey(),
                        entry.getValue() ? "has invalid value" : "is required"))
                .toList();
        return response(code, fields, exception);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception) {
        return response(
                ErrorCode.PARAM_REQUIRED,
                List.of(new FieldErrorResponse(exception.getParameterName(), "is required")),
                exception);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        return response(
                ErrorCode.PARAM_INVALID,
                List.of(new FieldErrorResponse(exception.getName(), "has invalid value")),
                exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception) {
        return response(
                ErrorCode.PARAM_INVALID,
                List.of(new FieldErrorResponse("request", "has invalid value")),
                exception);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        return response(unexpectedCode(exception, request), List.of(), exception);
    }

    private static boolean isRequiredConstraint(String code) {
        return "NotNull".equals(code) || "NotBlank".equals(code) || "NotEmpty".equals(code);
    }

    private static ErrorCode unexpectedCode(
            Exception exception, HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method)
                && "/api/v1/downloads".equals(path)
                && (exception instanceof DataAccessException
                        || exception instanceof TransactionException)) {
            return ErrorCode.PERSISTENCE_FAILED;
        }
        if ("GET".equals(method)
                && path.startsWith("/api/v1/data-sources/")
                && path.contains("/datasets/")
                && path.endsWith("/records")) {
            return ErrorCode.QUERY_FAILED;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private static ResponseEntity<ApiErrorResponse> response(
            ErrorCode code,
            List<FieldErrorResponse> fields,
            Exception exception) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        HttpStatus status = status(code);
        log(status, requestId, code, exception);
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                requestId, code, message(code), code.retryable(), fields));
    }

    private static void log(
            HttpStatus status,
            String requestId,
            ErrorCode code,
            Exception exception) {
        if (status.is4xxClientError()) {
            LOGGER.warn(
                    "Request rejected requestId={} code={} exceptionType={}",
                    requestId,
                    code,
                    exception.getClass().getName());
            return;
        }
        RuntimeException redacted = new RuntimeException("Request failure details redacted");
        redacted.setStackTrace(exception.getStackTrace());
        LOGGER.error(
                "Request failed requestId={} code={} exceptionType={}",
                requestId,
                code,
                exception.getClass().getName(),
                redacted);
    }

    private static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case PARAM_REQUIRED, PARAM_INVALID -> HttpStatus.BAD_REQUEST;
            case PLUGIN_DISABLED, DATASET_MISCONFIGURED -> HttpStatus.CONFLICT;
            case ADAPTER_FIELD_MISSING, ADAPTER_TYPE_INVALID ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case PERSISTENCE_FAILED, QUERY_FAILED, INTERNAL_ERROR ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
            case SOURCE_AUTH_FAILED,
                    SOURCE_PERMISSION_DENIED,
                    SOURCE_RATE_LIMITED,
                    SOURCE_UNAVAILABLE,
                    SOURCE_NETWORK_ERROR,
                    SOURCE_PAYLOAD_INVALID -> HttpStatus.BAD_GATEWAY;
            case SOURCE_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
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
}
```

Do not extract status/message maps into new files, inject a logger, add a constructor, catch/rethrow inside Controllers, or use `exception.getMessage()` anywhere.

- [ ] **Step 5: Run the focused GREEN and inspect every invocation**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am -Dtest=GlobalExceptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `GlobalExceptionHandlerTest` 25/25, zero failures/errors/skips. Inspect `data-plane/tensor-app/target/surefire-reports/TEST-com.akkc.tensor.web.GlobalExceptionHandlerTest.xml`; verify tests=25 and no failure/error/skipped elements. Save the complete Maven output as `/private/tmp/m09-t05-green.log`; do not add it to Git.

- [ ] **Step 6: Prove the frozen matrix and retryable assertions detect a status mutation**

Temporarily change `PARAM_INVALID` in `status(ErrorCode)` from `BAD_REQUEST` to `CONFLICT`, then run the focused command from Step 5.

Expected: the `PARAM_INVALID` invocation in `mapsEveryDomainCodeToTheFrozenContract` fails because expected 400 but received 409. Restore the source exactly and rerun; expect 25/25.

- [ ] **Step 7: Prove the client leak scan detects an exception-message mutation**

Temporarily change the `ApiErrorResponse` message argument in `response(...)` from `message(code)` to `exception.getMessage()`, then run the focused command.

Expected: the domain/unknown security assertions fail because the response contains the sensitive SQL/Token/path/stacktrace sentinel. Restore the fixed switch call and rerun; expect 25/25.

- [ ] **Step 8: Prove route classification and log sanitization are observable**

Run two independent temporary mutations, restoring after each:

1. Return `INTERNAL_ERROR` before both route branches in `unexpectedCode`; `classifiesUntypedFailuresByExactOperation` must fail for downloads and records.
2. Pass the original `exception` instead of `redacted` as the final SLF4J argument; `exposesOnlyTheApprovedSurfaceAndWritesSanitizedLogs` must fail because the Throwable proxy contains the sensitive message.

After both observations, restore the approved source and rerun the focused command; expect 25/25. No mutation may remain in `git diff` or the implementation commit.

- [ ] **Step 9: Run the full reactor regression**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am verify
```

Expected for each command: plugin-api 79, core 75, Tushare 93, fixture 12, app 61, total 320/320, zero failures/errors/skips. All six Enforcer executions, app ArchUnit, and forbidden-Git tests pass. Existing `*IT` classes remain outside default Surefire and do not change these counts.

- [ ] **Step 10: Run authorization, security, JAR, and scope gates**

Run:

```bash
rg -n '@RestControllerAdvice|@ExceptionHandler|TensorException|MethodArgumentNotValidException|MethodArgumentTypeMismatchException|HttpMessageNotReadableException|DataAccessException|TransactionException|PERSISTENCE_FAILED|QUERY_FAILED|INTERNAL_ERROR|RequestIdFilter\.MDC_KEY' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java
rg -n 'getMessage\(|getQueryString|getParameter\(|getHeader\(|getCookies\(|request\.getReader|request\.getInputStream|(?i:authorization|credential|password|token)|SELECT |INSERT |UPDATE |DELETE ' \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java
jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  | rg 'com/akkc/tensor/web/GlobalExceptionHandler.*\.class'
git diff --quiet -- \
  docs data-plane/pom.xml data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/main/resources \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/TensorApplication.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/RequestIdFilter.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DatasetController.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/JacksonPrecisionConfiguration.java \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/RequestIdFilterTest.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DataSourceControllerTest.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DatasetControllerIT.java \
  data-plane/tensor-core data-plane/tensor-plugin-api \
  data-plane/tensor-plugin-fixture data-plane/tensor-plugin-tushare
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/GlobalExceptionHandlerTest.java
```

Expected: authorization scan finds all approved handler/classification symbols; the forbidden scan has no output and exits 1; JAR contains `GlobalExceptionHandler.class`; protected paths and format checks exit 0; scoped status shows exactly the two untracked Java files and no `target/` after the later clean step.

- [ ] **Step 11: Review and commit the exact two-file implementation**

Review both files against every design Acceptance bullet. Confirm the six handler groups, 16-code switches, 25 invocation count, fixed messages, route predicates, MDC-only ID, field ordering, response/log leak scans, and all mutation restorations.

Run:

```bash
git diff --check
git status --short --untracked-files=all
git add \
  data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/web/GlobalExceptionHandlerTest.java
git diff --cached --check
git diff --cached --stat
git commit -m "feat(api): map domain errors safely"
git show --format= --name-status HEAD
```

Expected: the staged stat and committed name-status contain exactly the two added Java files; commit message is exact. Do not stage the design, plan, handoff, board, target output, `/private/tmp` logs, or any unrelated user change.

- [ ] **Step 12: Clean and record final evidence**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-app -am clean
git status --short
git show --stat --oneline --summary HEAD
```

Expected: clean succeeds, no `target/` remains, Git status is empty, and HEAD is `feat(api): map domain errors safely` with exactly the two implementation files. Record strict RED, focused 25/25, both reactor 320/320 runs, three mutation outcomes, static/JAR/security/scope results, and the commit ID for task review and the authoritative board completion evidence.
