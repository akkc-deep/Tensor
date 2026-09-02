package com.akkc.tensor.core.query;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import org.springframework.jdbc.core.JdbcTemplate;

public final class GenericQueryRepository {
    private final JdbcTemplate jdbcTemplate;

    public GenericQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public long count(QuerySql querySql) {
        Objects.requireNonNull(querySql, "querySql");
        validateValues(querySql.countValues());
        List<Long> counts = jdbcTemplate.query(
                querySql.countSql(),
                statement -> bind(statement, querySql.countValues()),
                (resultSet, rowNumber) -> {
                    long value = resultSet.getLong(1);
                    return resultSet.wasNull() ? null : value;
                });
        if (counts.size() != 1 || counts.getFirst() == null || counts.getFirst() < 0) {
            throw new IllegalStateException("Count query returned an invalid result");
        }
        return counts.getFirst();
    }

    public List<Map<String, Object>> query(DatasetDefinition definition, QuerySql querySql) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(querySql, "querySql");
        validateValues(querySql.pageValues());
        List<Map<String, Object>> rows = jdbcTemplate.query(
                querySql.pageSql(),
                statement -> bind(statement, querySql.pageValues()),
                (resultSet, rowNumber) -> readRow(definition, resultSet));
        return Collections.unmodifiableList(new ArrayList<>(rows));
    }

    static List<String> columns(DatasetDefinition definition) {
        List<String> columns = definition.columns().stream()
                .map(ColumnDefinition::name)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        columns.add("source_plugin");
        columns.add("source_api");
        columns.add("ingested_at");
        return List.copyOf(columns);
    }

    private static void validateValues(List<Object> values) {
        for (Object value : values) {
            if (!(value instanceof String
                    || value instanceof LocalDate
                    || value instanceof Integer
                    || value instanceof Long)) {
                throw new IllegalArgumentException("Unsupported query value type");
            }
        }
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            int parameter = index + 1;
            if (value instanceof String text) {
                statement.setString(parameter, text);
            } else if (value instanceof LocalDate date) {
                statement.setDate(parameter, Date.valueOf(date));
            } else if (value instanceof Integer integer) {
                statement.setInt(parameter, integer);
            } else if (value instanceof Long longValue) {
                statement.setLong(parameter, longValue);
            } else {
                throw new IllegalArgumentException("Unsupported query value type");
            }
        }
    }

    private static Map<String, Object> readRow(DatasetDefinition definition, ResultSet resultSet)
            throws SQLException {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        int index = 1;
        for (ColumnDefinition column : definition.columns()) {
            row.put(column.name(), readValue(resultSet, index++, column));
        }
        row.put("source_plugin", resultSet.getString(index++));
        row.put("source_api", resultSet.getString(index++));
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Timestamp ingestedAt = resultSet.getTimestamp(index, utc);
        row.put("ingested_at", ingestedAt == null ? null : ingestedAt.toInstant());
        return Collections.unmodifiableMap(row);
    }

    private static Object readValue(ResultSet resultSet, int index, ColumnDefinition column)
            throws SQLException {
        return switch (column.logicalType()) {
            case STRING, TEXT, MONTH, ENUM -> resultSet.getString(index);
            case DATE -> {
                Date value = resultSet.getDate(index);
                yield value == null ? null : value.toLocalDate();
            }
            case LONG -> {
                long value = resultSet.getLong(index);
                yield resultSet.wasNull() ? null : value;
            }
            case DECIMAL -> resultSet.getBigDecimal(index);
        };
    }
}
