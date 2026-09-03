package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TushareResponseValidator {
    private TushareResponseValidator() {}

    static DownloadEnvelope validate(
            DatasetDefinition definition,
            Map<String, Object> params,
            TushareResponse response) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(response, "response");
        if (response.code() == null) {
            throw new IllegalStateException("Tushare response code is missing");
        }
        if (response.code() != 0) {
            throw new IllegalStateException("Tushare business request failed");
        }
        if (response.data() == null) {
            throw new IllegalStateException("Tushare response data is missing");
        }

        List<String> fields = response.data().fields();
        if (fields == null) {
            throw new IllegalStateException("Tushare response fields are missing");
        }
        List<List<Object>> items = response.data().items();
        if (items == null) {
            throw new IllegalStateException("Tushare response items are missing");
        }
        if (fields.contains(null) || new HashSet<>(fields).size() != fields.size()) {
            throw new IllegalStateException("Tushare response fields contain duplicates or null");
        }

        List<String> expectedFields = definition.columns().stream().map(ColumnDefinition::name).toList();
        if (!expectedFields.equals(fields)) {
            throw new IllegalStateException("Tushare response fields do not match dataset definition");
        }
        for (List<Object> row : items) {
            if (row == null) {
                throw new IllegalStateException("Tushare response row is missing");
            }
            if (row.size() != fields.size()) {
                throw new IllegalStateException("Tushare response row width does not match fields");
            }
        }

        return new DownloadEnvelope(
                definition.datasetKey().pluginId(),
                definition.datasetKey().apiName(),
                params,
                fields,
                items.size(),
                items,
                DownloadStatus.SUCCESS,
                null);
    }
}
