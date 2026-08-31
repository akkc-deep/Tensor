package com.akkc.tensor.plugin.api.download;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.TableName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record AdaptedBatch(
        DatasetKey datasetKey,
        TableName tableName,
        List<String> columns,
        List<Map<String, Object>> rows,
        BusinessKeyDefinition businessKeyDefinition,
        Instant ingestedAt) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public AdaptedBatch {
        Objects.requireNonNull(datasetKey, "datasetKey");
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(businessKeyDefinition, "businessKeyDefinition");
        Objects.requireNonNull(ingestedAt, "ingestedAt");

        if (!tableName.equals(TableName.from(datasetKey))) {
            throw new IllegalArgumentException("tableName must match datasetKey");
        }

        columns = List.copyOf(columns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        if (columns.size() != new HashSet<>(columns).size()) {
            throw new IllegalArgumentException("columns must not contain duplicates");
        }
        for (String column : columns) {
            if (!IDENTIFIER_PATTERN.matcher(column).matches()) {
                throw new IllegalArgumentException("Invalid column: " + column);
            }
        }

        Set<String> columnSet = Set.copyOf(columns);
        List<Map<String, Object>> copiedRows = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Objects.requireNonNull(row, "row");
            for (String key : row.keySet()) {
                Objects.requireNonNull(key, "row key");
            }
            if (!row.keySet().equals(columnSet)) {
                throw new IllegalArgumentException("row keys must exactly match columns");
            }
            copiedRows.add(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row)));
        }
        rows = List.copyOf(copiedRows);

        if (!columnSet.containsAll(businessKeyDefinition.fields())) {
            throw new IllegalArgumentException("business key fields must reference columns");
        }
    }
}
