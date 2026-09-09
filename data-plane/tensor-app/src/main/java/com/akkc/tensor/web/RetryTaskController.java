package com.akkc.tensor.web;

import com.akkc.tensor.core.download.DownloadExecutionException;
import com.akkc.tensor.core.download.RetryDownloadService;
import com.akkc.tensor.core.retry.RetryTaskQueryService;
import com.akkc.tensor.core.retry.RetryTaskRepository;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.web.download.DownloadBindingException;
import com.akkc.tensor.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retry-tasks")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class RetryTaskController {
    private final RetryTaskQueryService queries;
    private final RetryDownloadService retries;
    private final OperationLogger operations;
    public RetryTaskController(RetryTaskQueryService queries, RetryDownloadService retries, OperationLogger operations) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.retries = Objects.requireNonNull(retries, "retries");
        this.operations = Objects.requireNonNull(operations, "operations");
    }
    @GetMapping
    public RetryTaskResponse.Page list(HttpServletRequest request) {
        int page = number(request, "page", 1), size = number(request, "pageSize", 20);
        if (page < 1) throw invalid("page");
        if (size != 20 && size != 50 && size != 100) throw invalid("pageSize");
        String plugin = identifier(request, "pluginId"), api = identifier(request, "apiName");
        return RetryTaskResponse.Page.from(requestId().value().toString(), queries.list(new RetryTaskRepository.Criteria(
                plugin == null ? null : PluginId.of(plugin), api == null ? null : ApiName.of(api), page, size)));
    }
    @GetMapping("/{taskId}")
    public RetryTaskResponse.Detail get(@PathVariable("taskId") String taskId) {
        UUID id = taskId(taskId);
        return RetryTaskResponse.Detail.from(requestId().value().toString(), queries.get(id));
    }
    @PostMapping("/{taskId}/execute")
    public DownloadResponse execute(@PathVariable("taskId") String taskId, HttpServletRequest request) throws IOException {
        UUID id = taskId(taskId);
        if (request.getInputStream().read() != -1) throw invalid("request");
        var requestId = requestId();
        long start = System.nanoTime();
        try {
            var result = retries.execute(id, requestId);
            var response = DownloadResponse.from(result);
            operations.recordDownloadExecution(result, Duration.ofNanos(System.nanoTime() - start));
            return response;
        } catch (DownloadExecutionException failure) {
            operations.recordDownloadExecution(failure.downloadResult(), Duration.ofNanos(System.nanoTime() - start));
            throw failure;
        }
    }
    private static UUID taskId(String value) {
        if (!value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid("taskId");
        return UUID.fromString(value);
    }
    private static String single(HttpServletRequest request, String name) {
        var values = request.getParameterValues(name);
        if (values == null) return null;
        if (values.length != 1 || values[0].isEmpty()) throw invalid(name);
        return values[0];
    }
    private static int number(HttpServletRequest request, String name, int fallback) {
        String value = single(request, name);
        if (value == null) return fallback;
        if (!value.matches("[0-9]+")) throw invalid(name);
        try { return Integer.parseInt(value); }
        catch (NumberFormatException failure) { throw invalid(name); }
    }
    private static String identifier(HttpServletRequest request, String name) {
        String value = single(request, name);
        if (value != null && !value.matches("[a-z][a-z0-9_]{1,63}")) throw invalid(name);
        return value;
    }
    private static RequestId requestId() {
        String value = MDC.get(RequestIdFilter.MDC_KEY);
        if (value == null) throw new IllegalStateException("Request ID is unavailable");
        return new RequestId(UUID.fromString(value));
    }
    private static DownloadBindingException invalid(String field) {
        return new DownloadBindingException(ErrorCode.PARAM_INVALID, List.of(new FieldErrorResponse(field, "has invalid value")));
    }
}
