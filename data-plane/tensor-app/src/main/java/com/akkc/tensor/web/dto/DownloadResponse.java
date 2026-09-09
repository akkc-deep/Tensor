package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.download.DownloadExecutionResult;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NumberSerializer;
import java.util.List;
import java.util.Objects;
import com.akkc.tensor.web.dto.DownloadFailureResponse.ScopeResponse;

public record DownloadResponse(String requestId, DownloadExecutionResult.Outcome outcome, String pluginId, String apiName,
        @JsonSerialize(using = LongNumberSerializer.class) long sourceRowCount,
        @JsonSerialize(using = LongNumberSerializer.class) long insertedRows,
        @JsonSerialize(using = LongNumberSerializer.class) long updatedRows, String message,
        @JsonSerialize(using = LongNumberSerializer.class) long completedUnits,
        @JsonSerialize(using = LongNumberSerializer.class) long failedUnits,
        @JsonSerialize(using = LongNumberSerializer.class) Long notStartedUnits,
        @JsonSerialize(using = LongNumberSerializer.class) long skippedClosedDates, String taskId,
        @JsonSerialize(using = LongNumberSerializer.class) Long remainingFailedUnits,
        DownloadExecutionResult.FailureRecordStatus failureRecordStatus, List<DownloadFailureResponse> failures,
        List<ScopeResponse> notStartedScopes, List<ScopeResponse> unconfirmedScopes) {
    public static final class LongNumberSerializer extends NumberSerializer {
        public LongNumberSerializer() { super(Long.class); }
    }
    public DownloadResponse {
        failures = List.copyOf(failures);
        notStartedScopes = List.copyOf(notStartedScopes);
        unconfirmedScopes = List.copyOf(unconfirmedScopes);
    }
    public static DownloadResponse from(DownloadExecutionResult result) {
        Objects.requireNonNull(result, "result");
        return new DownloadResponse(result.requestId().value().toString(), result.outcome(), result.pluginId().value(), result.apiName().value(),
                result.sourceRowCount(), result.insertedRows(), result.updatedRows(), result.message(), result.completedUnits(), result.failedUnits(),
                result.notStartedUnits(), result.skippedClosedDates(), result.taskId() == null ? null : result.taskId().toString(),
                result.remainingFailedUnits(), result.failureRecordStatus(), result.failures().stream().map(DownloadFailureResponse::from).toList(),
                result.notStartedScopes().stream().map(ScopeResponse::from).toList(), result.unconfirmedScopes().stream().map(ScopeResponse::from).toList());
    }
}
