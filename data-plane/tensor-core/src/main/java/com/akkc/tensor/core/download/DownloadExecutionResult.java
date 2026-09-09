package com.akkc.tensor.core.download;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import java.util.*;
public record DownloadExecutionResult(RequestId requestId, Outcome outcome, PluginId pluginId, ApiName apiName,
        long sourceRowCount, long insertedRows, long updatedRows, String message,
        long completedUnits, long failedUnits, Long notStartedUnits, long skippedClosedDates,
        UUID taskId, Long remainingFailedUnits, FailureRecordStatus failureRecordStatus,
        List<RecoveryUnitProcessor.Failure> failures, List<RecoverySelector> notStartedScopes,
        List<RecoverySelector> unconfirmedScopes) {
    public DownloadExecutionResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(failureRecordStatus, "failureRecordStatus");
        failures = List.copyOf(failures);
        notStartedScopes = List.copyOf(notStartedScopes);
        unconfirmedScopes = List.copyOf(unconfirmedScopes);
        if (message.isBlank() || sourceRowCount < 0 || insertedRows < 0 || updatedRows < 0
                || completedUnits < 0 || failedUnits < 0 || skippedClosedDates < 0
                || notStartedUnits != null && notStartedUnits < 0
                || remainingFailedUnits != null && remainingFailedUnits < 0
                || failedUnits != failures.size()
                || completedUnits == 0 && (sourceRowCount != 0 || insertedRows != 0 || updatedRows != 0)) throw invalid();
        boolean valid = switch (outcome) {
            case SUCCESS -> completedUnits > 0 && failedUnits == 0 && sourceRowCount > 0;
            case EMPTY -> completedUnits > 0 && failedUnits == 0 && sourceRowCount == 0 && insertedRows == 0 && updatedRows == 0;
            case NO_OPEN_DATES -> completedUnits == 0 && failedUnits == 0 && skippedClosedDates > 0;
            case PARTIAL -> completedUnits > 0 && failedUnits > 0;
            case FAILED -> completedUnits == 0 && failedUnits > 0;
            case UNCONFIRMED -> failureRecordStatus == FailureRecordStatus.UNCONFIRMED;
        };
        if (!valid) throw invalid();
        if (outcome != Outcome.UNCONFIRMED) {
            if (!Long.valueOf(0).equals(notStartedUnits) || !notStartedScopes.isEmpty() || !unconfirmedScopes.isEmpty()) throw invalid();
            if (failedUnits == 0) {
                if (taskId != null || !Long.valueOf(0).equals(remainingFailedUnits)
                        || failureRecordStatus != FailureRecordStatus.NOT_REQUIRED) throw invalid();
            } else if (taskId == null || remainingFailedUnits == null || remainingFailedUnits == 0
                    || remainingFailedUnits != failures.stream().map(RecoveryUnitProcessor.Failure::selector).distinct().count()
                    || failureRecordStatus != FailureRecordStatus.CONFIRMED) throw invalid();
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid download execution result"); }
    public enum Outcome { SUCCESS, EMPTY, NO_OPEN_DATES, PARTIAL, FAILED, UNCONFIRMED }
    public enum FailureRecordStatus { NOT_REQUIRED, CONFIRMED, UNCONFIRMED }
}
