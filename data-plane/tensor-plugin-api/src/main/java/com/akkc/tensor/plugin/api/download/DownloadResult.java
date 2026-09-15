package com.akkc.tensor.plugin.api.download;

import com.akkc.tensor.plugin.api.constant.ValidationMessages;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import java.util.Objects;

public record DownloadResult(
        RequestId requestId,
        DownloadOutcome outcome,
        PluginId pluginId,
        ApiName apiName,
        long sourceRowCount,
        long insertedRows,
        long updatedRows) {

    public DownloadResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        if (sourceRowCount < 0 || insertedRows < 0 || updatedRows < 0) {
            throw new IllegalArgumentException(ValidationMessages.NEGATIVE_ROW_COUNTS);
        }
        if (outcome == DownloadOutcome.EMPTY
                && (sourceRowCount != 0 || insertedRows != 0 || updatedRows != 0)) {
            throw new IllegalArgumentException("empty result counts must all be zero");
        }
        if (outcome == DownloadOutcome.SUCCESS && sourceRowCount == 0) {
            throw new IllegalArgumentException("successful result must contain source rows");
        }
    }

    public String message() {
        return outcome.message();
    }
}
