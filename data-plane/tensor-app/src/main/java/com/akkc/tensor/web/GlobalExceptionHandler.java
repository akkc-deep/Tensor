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

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidArgument(
            IllegalArgumentException exception) {
        return response(ErrorCode.PARAM_INVALID, List.of(), exception);
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
