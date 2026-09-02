package com.akkc.tensor.core.persistence;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ExistingKeyRepository {
    private static final int MAX_BIND_PARAMETERS = 1000;
    private static final String FINGERPRINT_COLUMN = "business_key";

    private final JdbcTemplate jdbcTemplate;

    public ExistingKeyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public Set<BusinessKey> findExisting(DatasetDefinition definition, List<BusinessKey> keys) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(keys, "keys");
        if (keys.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("business keys must not contain null");
        }
        List<BusinessKey> copiedKeys = List.copyOf(keys);

        List<String> columnNames = physicalColumnNames(definition);
        List<Integer> jdbcTypes = physicalJdbcTypes(definition);
        int keyWidth = columnNames.size();
        for (BusinessKey key : copiedKeys) {
            if (key.values().size() != keyWidth) {
                throw new IllegalArgumentException("Business key width does not match dataset");
            }
        }
        if (copiedKeys.isEmpty()) {
            return Set.of();
        }

        List<BusinessKey> distinctKeys = new ArrayList<>(new LinkedHashSet<>(copiedKeys));
        int chunkSize = MAX_BIND_PARAMETERS / keyWidth;
        LinkedHashSet<BusinessKey> existing = new LinkedHashSet<>();
        for (int start = 0; start < distinctKeys.size(); start += chunkSize) {
            List<BusinessKey> chunk = distinctKeys.subList(start, Math.min(start + chunkSize, distinctKeys.size()));
            existing.addAll(queryChunk(definition, columnNames, jdbcTypes, chunk));
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(existing));
    }

    private List<BusinessKey> queryChunk(
            DatasetDefinition definition,
            List<String> columnNames,
            List<Integer> jdbcTypes,
            List<BusinessKey> keys) {
        String sql = selectSql(definition, columnNames, keys.size());
        return jdbcTemplate.query(
                sql,
                statement -> {
                    JdbcValueBinder binder = new JdbcValueBinder();
                    int parameter = 1;
                    for (BusinessKey key : keys) {
                        for (int column = 0; column < jdbcTypes.size(); column++) {
                            binder.bind(statement, parameter++, key.values().get(column), jdbcTypes.get(column));
                        }
                    }
                },
                (resultSet, rowNumber) -> readKey(resultSet, jdbcTypes));
    }

    private static String selectSql(
            DatasetDefinition definition,
            List<String> columnNames,
            int keyCount) {
        SqlIdentifierPolicy policy = new SqlIdentifierPolicy();
        String table = policy.quote(definition.tableName().value());
        String columns = columnNames.stream().map(policy::quote).collect(Collectors.joining(", "));
        if (columnNames.size() == 1) {
            String placeholders = String.join(", ", Collections.nCopies(keyCount, "?"));
            return "SELECT " + columns + " FROM " + table + " WHERE " + columns + " IN (" + placeholders + ")";
        }
        String tuple = "(" + String.join(", ", Collections.nCopies(columnNames.size(), "?")) + ")";
        String tuples = String.join(", ", Collections.nCopies(keyCount, tuple));
        return "SELECT " + columns + " FROM " + table + " WHERE (" + columns + ") IN (" + tuples + ")";
    }

    private static BusinessKey readKey(ResultSet resultSet, List<Integer> jdbcTypes) throws SQLException {
        List<Object> values = new ArrayList<>(jdbcTypes.size());
        for (int index = 0; index < jdbcTypes.size(); index++) {
            Object value = readValue(resultSet, index + 1, jdbcTypes.get(index));
            if (value == null) {
                throw new IllegalStateException("Existing business key contains null");
            }
            values.add(value);
        }
        return new BusinessKey(values);
    }

    private static Object readValue(ResultSet resultSet, int index, int jdbcType) throws SQLException {
        return switch (jdbcType) {
            case Types.VARCHAR, Types.LONGVARCHAR, Types.CHAR -> resultSet.getString(index);
            case Types.DATE -> {
                Date value = resultSet.getDate(index);
                yield value == null ? null : value.toLocalDate();
            }
            case Types.BIGINT -> {
                long value = resultSet.getLong(index);
                yield resultSet.wasNull() ? null : value;
            }
            case Types.DECIMAL -> resultSet.getBigDecimal(index);
            default -> throw new IllegalStateException("Unsupported business key JDBC type");
        };
    }

    private static List<String> physicalColumnNames(DatasetDefinition definition) {
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            return List.of(FINGERPRINT_COLUMN);
        }
        return definition.businessKey().fields();
    }

    private static List<Integer> physicalJdbcTypes(DatasetDefinition definition) {
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            return List.of(Types.CHAR);
        }
        return definition.businessKey().fields().stream()
                .map(field -> definition.columns().stream()
                        .filter(column -> column.name().equals(field))
                        .findFirst()
                        .orElseThrow())
                .map(ColumnDefinition::logicalType)
                .map(ExistingKeyRepository::jdbcType)
                .toList();
    }

    private static int jdbcType(LogicalType logicalType) {
        return switch (logicalType) {
            case STRING -> Types.VARCHAR;
            case TEXT -> Types.LONGVARCHAR;
            case DATE -> Types.DATE;
            case MONTH, ENUM -> Types.CHAR;
            case LONG -> Types.BIGINT;
            case DECIMAL -> Types.DECIMAL;
        };
    }
}
