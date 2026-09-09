package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.retry.RetryTaskQueryService;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.web.dto.DownloadFailureResponse.ScopeResponse;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Map;

public final class RetryTaskResponse {
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private RetryTaskResponse() {}
    public record OriginalRange(String startDate, String endDate) {
        static OriginalRange from(RetryTaskQueryService.OriginalRange range) { return range == null ? null : new OriginalRange(range.startDate(), range.endDate()); }
    }
    public record Blocker(ErrorCode code, String message) {
        static Blocker from(RetryTaskQueryService.ExecutionBlocker blocker) { return blocker == null ? null : new Blocker(blocker.code(), blocker.message()); }
    }
    public record Summary(String taskId, String pluginId, String apiName, String pluginDisplayName, String apiDisplayName,
            OriginalRange originalDateRange, RetryTaskQueryService.OriginalRangeStatus originalDateRangeStatus,
            @JsonSerialize(using = DownloadResponse.LongNumberSerializer.class) long failedItemCount, List<ScopeResponse> failedScopes, String createdAt, String updatedAt) {
        public Summary { failedScopes = List.copyOf(failedScopes); }
        public static Summary from(RetryTaskQueryService.TaskSummary task) {
            return new Summary(task.taskId().toString(), task.pluginId().value(), task.apiName().value(), task.pluginDisplayName(), task.apiDisplayName(),
                    OriginalRange.from(task.originalDateRange()), task.originalDateRangeStatus(), task.failedItemCount(),
                    task.failedScopes().stream().map(ScopeResponse::from).toList(), time(task.createdAt()), time(task.updatedAt()));
        }
    }
    public record Page(String requestId, int page, int pageSize,
            @JsonSerialize(using = DownloadResponse.LongNumberSerializer.class) long totalElements,
            @JsonSerialize(using = DownloadResponse.LongNumberSerializer.class) long totalPages, List<Summary> items) {
        public Page { items = List.copyOf(items); }
        public static Page from(String requestId, RetryTaskQueryService.TaskPage page) {
            return new Page(requestId, page.page(), page.pageSize(), page.totalElements(), page.totalPages(), page.items().stream().map(Summary::from).toList());
        }
    }
    public record Item(RecoverySelector.TargetType targetType, String targetValue, RecoverySelector.TimeType timeType,
            String timeValue, ErrorCode errorCode, String errorMessage, String updatedAt) {
        static Item from(RetryTaskQueryService.TaskItem item) {
            var scope = item.selector();
            return new Item(scope.targetType(), scope.targetValue(), scope.timeType(), scope.timeValue(), item.errorCode(), DownloadFailureResponse.safeReason(item.errorCode()), time(item.updatedAt()));
        }
    }
    public record Detail(String taskId, String pluginId, String apiName, String pluginDisplayName, String apiDisplayName,
            OriginalRange originalDateRange, RetryTaskQueryService.OriginalRangeStatus originalDateRangeStatus,
            @JsonSerialize(using = DownloadResponse.LongNumberSerializer.class) long failedItemCount, List<ScopeResponse> failedScopes, String createdAt, String updatedAt,
            String requestId, Map<String, Object> taskParams, List<Item> items, boolean retrying, boolean canExecute, Blocker executionBlocker) {
        public Detail { failedScopes = List.copyOf(failedScopes); taskParams = Map.copyOf(taskParams); items = List.copyOf(items); }
        public static Detail from(String requestId, RetryTaskQueryService.TaskDetail detail) {
            var task = Summary.from(detail.summary());
            return new Detail(task.taskId(), task.pluginId(), task.apiName(), task.pluginDisplayName(), task.apiDisplayName(), task.originalDateRange(), task.originalDateRangeStatus(),
                    task.failedItemCount(), task.failedScopes(), task.createdAt(), task.updatedAt(), requestId, detail.taskParams(),
                    detail.items().stream().map(Item::from).toList(), detail.retrying(), detail.canExecute(), Blocker.from(detail.executionBlocker()));
        }
    }
    private static String time(Instant instant) { return TIME.format(instant); }
}
