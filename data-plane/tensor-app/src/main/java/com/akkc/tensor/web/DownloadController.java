package com.akkc.tensor.web;

import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.observability.OperationLogger;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.web.dto.DownloadResponse;
import jakarta.validation.Valid;
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

    public DownloadController(
            DownloadService downloadService, OperationLogger operationLogger) {
        this.downloadService = Objects.requireNonNull(downloadService, "downloadService");
        this.operationLogger = Objects.requireNonNull(operationLogger, "operationLogger");
    }

    @PostMapping
    public DownloadResponse download(@Valid @RequestBody DownloadRequest request) {
        Objects.requireNonNull(request, "request");
        String value = MDC.get(RequestIdFilter.MDC_KEY);
        if (value == null) {
            throw new IllegalStateException("Request ID is unavailable");
        }
        DatasetKey key = DatasetKey.of(
                PluginId.of(request.pluginId()), ApiName.of(request.apiName()));
        RequestId requestId = new RequestId(UUID.fromString(value));
        return operationLogger.download(key, request.params(), () -> DownloadResponse.from(
                downloadService.execute(
                        key.pluginId(), key.apiName(), request.params(), requestId)));
    }
}
