package com.akkc.tensor.core.catalog;

import com.akkc.tensor.plugin.api.model.TableName;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import javax.sql.DataSource;

public final class SchemaInspector {
    private final DataSource dataSource;

    public SchemaInspector(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public Optional<TableSchema> inspect(TableName tableName) {
        Objects.requireNonNull(tableName, "tableName");
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            List<ColumnRow> columnRows = columns(metadata, catalog, tableName.value());
            if (columnRows.isEmpty()) {
                return Optional.empty();
            }
            List<ColumnMetadata> columns = columnRows.stream()
                    .sorted(Comparator.comparingInt(ColumnRow::ordinal))
                    .map(ColumnRow::column)
                    .toList();
            List<String> primaryKey = primaryKey(metadata, catalog, tableName.value());
            List<UniqueKeyMetadata> uniqueKeys = uniqueKeys(metadata, catalog, tableName.value());
            return Optional.of(new TableSchema(columns, primaryKey, uniqueKeys));
        } catch (SQLException exception) {
            throw failure();
        }
    }

    private static List<ColumnRow> columns(DatabaseMetaData metadata, String catalog, String table)
            throws SQLException {
        List<ColumnRow> columns = new ArrayList<>();
        try (ResultSet result = metadata.getColumns(catalog, null, table, null)) {
            while (result.next()) {
                if (!table.equals(result.getString("TABLE_NAME"))) {
                    continue;
                }
                int nullableValue = result.getInt("NULLABLE");
                boolean nullable;
                if (nullableValue == DatabaseMetaData.columnNullable) {
                    nullable = true;
                } else if (nullableValue == DatabaseMetaData.columnNoNulls) {
                    nullable = false;
                } else {
                    throw failure();
                }
                columns.add(new ColumnRow(
                        result.getInt("ORDINAL_POSITION"),
                        new ColumnMetadata(result.getString("COLUMN_NAME"), result.getInt("DATA_TYPE"), nullable)));
            }
        }
        return columns;
    }

    private static List<String> primaryKey(DatabaseMetaData metadata, String catalog, String table)
            throws SQLException {
        List<KeyRow> keys = new ArrayList<>();
        try (ResultSet result = metadata.getPrimaryKeys(catalog, null, table)) {
            while (result.next()) {
                keys.add(new KeyRow(result.getInt("KEY_SEQ"), result.getString("COLUMN_NAME")));
            }
        }
        return keys.stream().sorted(Comparator.comparingInt(KeyRow::sequence)).map(KeyRow::column).toList();
    }

    private static List<UniqueKeyMetadata> uniqueKeys(DatabaseMetaData metadata, String catalog, String table)
            throws SQLException {
        Map<String, List<IndexRow>> indexes = new TreeMap<>();
        try (ResultSet result = metadata.getIndexInfo(catalog, null, table, true, false)) {
            while (result.next()) {
                String indexName = result.getString("INDEX_NAME");
                String columnName = result.getString("COLUMN_NAME");
                int type = result.getInt("TYPE");
                if (type == DatabaseMetaData.tableIndexStatistic || indexName == null || "PRIMARY".equals(indexName)) {
                    continue;
                }
                List<IndexRow> rows = indexes.computeIfAbsent(indexName, ignored -> new ArrayList<>());
                if (columnName != null) {
                    rows.add(new IndexRow(result.getInt("ORDINAL_POSITION"), columnName));
                }
            }
        }
        Map<String, UniqueKeyMetadata> snapshots = new LinkedHashMap<>();
        indexes.forEach((name, rows) -> snapshots.put(name, new UniqueKeyMetadata(name, rows.stream()
                .sorted(Comparator.comparingInt(IndexRow::ordinal))
                .map(IndexRow::column)
                .toList())));
        return List.copyOf(snapshots.values());
    }

    private static IllegalStateException failure() {
        return new IllegalStateException("Schema inspection failed");
    }

    private static String requireName(String value, String component) {
        Objects.requireNonNull(value, component);
        if (value.isBlank()) {
            throw new IllegalArgumentException(component + " must not be blank");
        }
        return value;
    }

    private static <T> List<T> copy(List<T> values, String component) {
        Objects.requireNonNull(values, component);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(component + " must not contain null");
        }
        return List.copyOf(values);
    }

    public record ColumnMetadata(String name, int jdbcType, boolean nullable) {
        public ColumnMetadata {
            name = requireName(name, "name");
        }
    }

    public record UniqueKeyMetadata(String name, List<String> columns) {
        public UniqueKeyMetadata {
            name = requireName(name, "name");
            columns = copy(columns, "columns");
            for (String column : columns) {
                requireName(column, "column");
            }
        }
    }

    public record TableSchema(
            List<ColumnMetadata> columns,
            List<String> primaryKey,
            List<UniqueKeyMetadata> uniqueKeys) {
        public TableSchema {
            columns = copy(columns, "columns");
            primaryKey = copy(primaryKey, "primaryKey");
            for (String column : primaryKey) {
                requireName(column, "primaryKey column");
            }
            uniqueKeys = copy(uniqueKeys, "uniqueKeys");
        }
    }

    private record ColumnRow(int ordinal, ColumnMetadata column) {
    }

    private record KeyRow(int sequence, String column) {
    }

    private record IndexRow(int ordinal, String column) {
    }
}
