package com.akkc.tensor.web;

import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.web.dto.DownloadResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/downloads")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class DownloadController {
    private final DownloadService downloadService;
    private final OperationLogger operationLogger;
    private final DownloadParameterResolver parameterResolver;

    public DownloadController(
            DownloadService downloadService, OperationLogger operationLogger,
            DownloadParameterResolver parameterResolver) {
        this.downloadService = Objects.requireNonNull(downloadService, "downloadService");
        this.operationLogger = Objects.requireNonNull(operationLogger, "operationLogger");
        this.parameterResolver = Objects.requireNonNull(parameterResolver, "parameterResolver");
    }

    @PostMapping
    public DownloadResponse download(@RequestBody DownloadRequest request) {
        Objects.requireNonNull(request, "request");
        String value = MDC.get(RequestIdFilter.MDC_KEY);
        if (value == null) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        DatasetKey key = request.dataset();
        Map<String, Object> parameters = parameterResolver.toRawValues(request.params(), request.suppliedFields());
        RequestId requestId = new RequestId(UUID.fromString(value));
        long started = System.nanoTime();
        DownloadResult result = downloadService.execute(
                key.pluginId(), key.apiName(), parameters, requestId);
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        DownloadResponse response = DownloadResponse.from(result);
        operationLogger.recordDownloadSuccess(requestId, key, parameters, result, duration);
        return response;
    }
}
