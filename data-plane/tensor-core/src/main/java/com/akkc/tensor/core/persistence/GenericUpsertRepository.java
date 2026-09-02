package com.akkc.tensor.core.persistence;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class GenericUpsertRepository {
    private static final String FINGERPRINT_COLUMN = "business_key";

    private final JdbcTemplate jdbcTemplate;

    public GenericUpsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public void upsert(DatasetDefinition definition, AdaptedBatch batch) {
        validateBatch(definition, batch);
        if (batch.rows().isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Upsert requires an active transaction");
        }

        String sql = new UpsertSqlFactory().create(definition);
        JdbcValueBinder binder = new JdbcValueBinder();
        jdbcTemplate.batchUpdate(
                sql,
                batch.rows(),
                definition.batchSize(),
                (statement, row) -> bindRow(statement, definition, batch, row, binder));
    }

    static void validateBatch(DatasetDefinition definition, AdaptedBatch batch) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(batch, "batch");
        List<String> expectedColumns = new ArrayList<>(definition.columns().stream()
                .map(ColumnDefinition::name)
                .toList());
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            expectedColumns.add(FINGERPRINT_COLUMN);
        }
        if (!batch.datasetKey().equals(definition.datasetKey())
                || !batch.tableName().equals(definition.tableName())
                || !batch.businessKeyDefinition().equals(definition.businessKey())
                || !batch.columns().equals(expectedColumns)) {
            throw new IllegalArgumentException("Adapted batch does not match dataset");
        }
    }

    private static void bindRow(
            PreparedStatement statement,
            DatasetDefinition definition,
            AdaptedBatch batch,
            Map<String, Object> row,
            JdbcValueBinder binder) throws SQLException {
        int parameter = 1;
        for (ColumnDefinition column : definition.columns()) {
            binder.bind(statement, parameter++, row.get(column.name()), jdbcType(column.logicalType()));
        }
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            binder.bind(statement, parameter++, row.get(FINGERPRINT_COLUMN), Types.CHAR);
        }
        binder.bind(
                statement,
                parameter++,
                definition.datasetKey().pluginId().value(),
                Types.VARCHAR);
        binder.bind(
                statement,
                parameter++,
                definition.datasetKey().apiName().value(),
                Types.VARCHAR);
        binder.bind(statement, parameter, batch.ingestedAt(), Types.TIMESTAMP);
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
