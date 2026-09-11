package com.akkc.tensor.web;

import com.akkc.tensor.core.download.task.*;
import com.akkc.tensor.observability.DownloadTaskOperationLogger;
import com.akkc.tensor.observability.DownloadTaskOperationLogger.AcceptanceKind;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.dto.*;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/download-tasks", produces = "application/json")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@DependsOn("downloadTaskCoordinator")
public final class DownloadTaskController {
    private final DownloadTaskService service;
    private final DownloadTaskQueryService queries;
    private final DownloadParameterResolver parameters;
    private final DownloadTaskOperationLogger logger;

    public DownloadTaskController(DownloadTaskService service, DownloadTaskQueryService queries,
            DownloadParameterResolver parameters, DownloadTaskOperationLogger logger) {
        this.service = Objects.requireNonNull(service); this.queries = Objects.requireNonNull(queries);
        this.parameters = Objects.requireNonNull(parameters); this.logger = Objects.requireNonNull(logger);
    }

    @PostMapping
    public ResponseEntity<DownloadTaskReceipt> submit(DownloadTaskQuery.NoQuery ignored,
            @RequestBody DownloadTaskRequest request) {
        long started = System.nanoTime();
        var submission = switch (request) {
            case DownloadTaskRequest.Bound bound -> new DownloadTaskService.Submission(bound.submissionId(), bound.dataset(),
                    bound.mode(), parameters.toRawValues(bound.params(), bound.suppliedFields()));
            case DownloadTaskRequest.Replay replay -> replay.submission();
        };
        var result = service.submit(submission);
        return receipt(result.task(), result.created() ? AcceptanceKind.CREATED : AcceptanceKind.REPLAYED,
                result.created() ? 202 : 200, started);
    }

    @GetMapping
    public DownloadTaskPage<DownloadTaskResponse> tasks(DownloadTaskQuery.Tasks query) {
        var page = queries.tasks(query.filter(), query.page(), query.pageSize());
        return new DownloadTaskPage<>(query.page(), query.pageSize(), page.total(),
                page.items().stream().map(task -> detail(new DownloadTaskQuery.TaskId(task.taskId()))).toList());
    }

    @GetMapping("/{taskId}")
    public DownloadTaskResponse detail(DownloadTaskQuery.TaskId taskId) {
        var snapshot = queries.detail(taskId.value());
        return DownloadTaskResponse.from(snapshot, service.controls(snapshot.task().orElseThrow()));
    }

    @GetMapping("/{taskId}/batches")
    public DownloadTaskPage<DownloadBatchResponse> batches(DownloadTaskQuery.Batches query) {
        var page = queries.batches(query.taskId(), query.filter(), query.page(), query.pageSize());
        return new DownloadTaskPage<>(query.page(), query.pageSize(), page.total(),
                page.items().stream().map(DownloadBatchResponse::from).toList());
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<DownloadTaskReceipt> retry(DownloadTaskQuery.TaskId taskId,
            @RequestBody DownloadTaskControlRequest request) {
        long started = System.nanoTime();
        return receipt(service.retry(taskId.value(), request.expectedVersion()), AcceptanceKind.RETRY, 202, started);
    }

    @PostMapping("/{taskId}/resume")
    public ResponseEntity<DownloadTaskReceipt> resume(DownloadTaskQuery.TaskId taskId,
            @RequestBody DownloadTaskControlRequest request) {
        long started = System.nanoTime();
        return receipt(service.resume(taskId.value(), request.expectedVersion()), AcceptanceKind.RESUME, 202, started);
    }

    private ResponseEntity<DownloadTaskReceipt> receipt(DownloadTask task, AcceptanceKind kind, int status, long started) {
        var requestId = new RequestId(UUID.fromString(Objects.requireNonNull(MDC.get(RequestIdFilter.MDC_KEY), "Request ID is unavailable")));
        logger.recordAccepted(requestId, task, kind, Duration.ofNanos(System.nanoTime() - started));
        return ResponseEntity.status(status).location(URI.create("/api/v1/download-tasks/" + task.taskId()))
                .body(DownloadTaskReceipt.from(requestId, task));
    }
}
