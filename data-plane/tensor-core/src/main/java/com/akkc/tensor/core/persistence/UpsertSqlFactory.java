package com.akkc.tensor.core.persistence;

import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UpsertSqlFactory {
    public String create(DatasetDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        SqlIdentifierPolicy policy = new SqlIdentifierPolicy();
        List<String> insertColumns = new ArrayList<>(definition.columns().stream()
                .map(ColumnDefinition::name)
                .toList());
        List<String> keyColumns = definition.businessKey().fields();

        policy.quote(definition.tableName().value());
        keyColumns.forEach(policy::quote);
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            insertColumns.add(DatasetFields.BUSINESS_KEY);
        }
        insertColumns.addAll(List.of(DatasetFields.SOURCE_PLUGIN, DatasetFields.SOURCE_API, DatasetFields.INGESTED_AT));

        List<String> updateColumns = insertColumns.stream()
                .filter(column -> definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT
                        ? !column.equals(DatasetFields.BUSINESS_KEY)
                        : !keyColumns.contains(column))
                .toList();
        String quotedColumns = insertColumns.stream().map(policy::quote).collect(Collectors.joining(SqlConstants.COLUMN_SEPARATOR));
        String placeholders = insertColumns.stream().map(column -> SqlConstants.PARAMETER).collect(Collectors.joining(SqlConstants.COLUMN_SEPARATOR));
        String updates = updateColumns.stream()
                .map(policy::quote)
                .map(column -> column + " = VALUES(" + column + SqlConstants.CLOSE_PARENTHESIS)
                .collect(Collectors.joining(SqlConstants.COLUMN_SEPARATOR));

        return "INSERT INTO " + policy.quote(definition.tableName().value()) + " (" + quotedColumns + ") VALUES ("
                + placeholders + ") ON DUPLICATE KEY UPDATE " + updates;
    }
}
