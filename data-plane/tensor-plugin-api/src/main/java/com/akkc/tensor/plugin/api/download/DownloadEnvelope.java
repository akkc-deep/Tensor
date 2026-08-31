package com.akkc.tensor.plugin.api.download;

import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public record DownloadEnvelope(
        PluginId pluginId,
        ApiName apiName,
        Map<String, Object> params,
        List<String> fields,
        int rowCount,
        List<List<Object>> data,
        DownloadStatus status,
        String error) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public DownloadEnvelope {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(status, "status");

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "param name");
            Objects.requireNonNull(entry.getValue(), "param value");
            if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid param name: " + name);
            }
        }
        params = Map.copyOf(params);

        fields = List.copyOf(fields);
        if (fields.size() != new HashSet<>(fields).size()) {
            throw new IllegalArgumentException("fields must not contain duplicates");
        }
        for (String field : fields) {
            if (!IDENTIFIER_PATTERN.matcher(field).matches()) {
                throw new IllegalArgumentException("Invalid field: " + field);
            }
        }

        List<List<Object>> copiedData = new ArrayList<>(data.size());
        for (List<Object> row : data) {
            Objects.requireNonNull(row, "data row");
            copiedData.add(java.util.Collections.unmodifiableList(new ArrayList<>(row)));
        }
        data = List.copyOf(copiedData);

        if (rowCount < 0 || rowCount != data.size()) {
            throw new IllegalArgumentException("rowCount must equal data size and be non-negative");
        }
        if (status == DownloadStatus.SUCCESS) {
            if (error != null) {
                throw new IllegalArgumentException("successful envelope must not contain an error");
            }
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("successful envelope must declare fields");
            }
            for (List<Object> row : data) {
                if (row.size() != fields.size()) {
                    throw new IllegalArgumentException("data row width must equal field count");
                }
            }
        } else {
            Objects.requireNonNull(error, "error");
            if (error.isBlank()) {
                throw new IllegalArgumentException("failure error must not be blank");
            }
            if (!fields.isEmpty() || rowCount != 0 || !data.isEmpty()) {
                throw new IllegalArgumentException("failure envelope must not contain partial data");
            }
        }
    }
}
