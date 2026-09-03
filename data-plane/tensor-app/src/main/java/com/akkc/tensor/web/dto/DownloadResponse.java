package com.akkc.tensor.web.dto;

import com.akkc.tensor.plugin.api.download.DownloadOutcome;
import com.akkc.tensor.plugin.api.download.DownloadResult;
import java.util.Objects;

public record DownloadResponse(
        String requestId,
        DownloadOutcome outcome,
        String pluginId,
        String apiName,
        long sourceRowCount,
        long insertedRows,
        long updatedRows,
        String message) {
    public DownloadResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(message, "message");
        if (requestId.isBlank() || pluginId.isBlank() || apiName.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("download response text must not be blank");
        }
        if (sourceRowCount < 0 || insertedRows < 0 || updatedRows < 0) {
            throw new IllegalArgumentException("row counts must be non-negative");
        }
        if (outcome == DownloadOutcome.EMPTY
                && (sourceRowCount != 0 || insertedRows != 0 || updatedRows != 0)) {
            throw new IllegalArgumentException("empty response counts must all be zero");
        }
        if (outcome == DownloadOutcome.SUCCESS && sourceRowCount == 0) {
            throw new IllegalArgumentException("successful response must contain source rows");
        }
    }

    public static DownloadResponse from(DownloadResult result) {
        Objects.requireNonNull(result, "result");
        return new DownloadResponse(
                result.requestId().value().toString(),
                result.outcome(),
                result.pluginId().value(),
                result.apiName().value(),
                result.sourceRowCount(),
                result.insertedRows(),
                result.updatedRows(),
                result.message());
    }
}
