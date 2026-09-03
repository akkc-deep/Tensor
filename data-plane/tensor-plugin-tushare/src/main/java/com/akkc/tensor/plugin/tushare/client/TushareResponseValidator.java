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
            throw TushareErrorClassifier.invalidPayload();
        }
        if (response.code() != 0) {
            throw TushareErrorClassifier.classifyBusiness(response.msg());
        }
        if (response.data() == null) {
            throw TushareErrorClassifier.invalidPayload();
        }

        List<String> fields = response.data().fields();
        if (fields == null) {
            throw TushareErrorClassifier.invalidPayload();
        }
        List<List<Object>> items = response.data().items();
        if (items == null) {
            throw TushareErrorClassifier.invalidPayload();
        }
        if (fields.contains(null) || new HashSet<>(fields).size() != fields.size()) {
            throw TushareErrorClassifier.invalidPayload();
        }

        List<String> expectedFields = definition.columns().stream().map(ColumnDefinition::name).toList();
        if (!expectedFields.equals(fields)) {
            throw TushareErrorClassifier.invalidPayload();
        }
        for (List<Object> row : items) {
            if (row == null) {
                throw TushareErrorClassifier.invalidPayload();
            }
            if (row.size() != fields.size()) {
                throw TushareErrorClassifier.invalidPayload();
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
