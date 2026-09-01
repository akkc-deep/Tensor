package com.akkc.tensor.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.catalog.SchemaInspector.ColumnMetadata;
import com.akkc.tensor.core.catalog.SchemaInspector.TableSchema;
import com.akkc.tensor.core.catalog.SchemaInspector.UniqueKeyMetadata;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatasetStartupValidatorTest {
    @Test
    void admitsValidDefinitionsAndReturnsImmutableSortedCatalog() throws SQLException {
        // Catches insertion-order catalogs, mutable inputs/outputs, and lookup that exposes unvalidated data.
        DatasetDefinition zulu = definition("alpha", "zulu");
        DatasetDefinition able = definition("alpha", "able");
        DatasetDefinition other = definition("bravo", "other");
        List<DatasetDefinition> input = new ArrayList<>(List.of(zulu, able, other));
        JdbcFixture jdbc = jdbc(Map.of(
                zulu.tableName().value(), table(basicSchema()),
                able.tableName().value(), table(basicSchema()),
                other.tableName().value(), table(basicSchema())));

        DatasetStartupValidator validator = new DatasetStartupValidator(input, jdbc.inspector());
        input.clear();
        DatasetCatalog catalog = validator.validate();

        assertThat(catalog.find(able.datasetKey())).containsSame(able);
        assertThat(catalog.find(DatasetKey.of(PluginId.of("alpha"), ApiName.of("missing")))).isEmpty();
        assertThat(catalog.list(PluginId.of("alpha"))).containsExactly(able, zulu);
        assertThat(catalog.list(PluginId.of("bravo"))).containsExactly(other);
        assertThat(catalog.list(PluginId.of("absent"))).isEmpty();
        assertThatThrownBy(() -> catalog.list(PluginId.of("alpha")).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void inspectsOrderedImmutableJdbcMetadataAndReturnsEmptyWhenNoColumns() throws SQLException {
        // Catches reliance on driver row order, retention of JDBC resources, and mutable schema snapshots.
        JdbcTable shuffled = new JdbcTable(
                List.of(
                        row("COLUMN_NAME", "trade_date", "DATA_TYPE", Types.DATE,
                                "NULLABLE", DatabaseMetaData.columnNoNulls, "ORDINAL_POSITION", 2),
                        row("COLUMN_NAME", "ts_code", "DATA_TYPE", Types.VARCHAR,
                                "NULLABLE", DatabaseMetaData.columnNoNulls, "ORDINAL_POSITION", 1),
                        row("TABLE_NAME", "alpha_xdaily", "COLUMN_NAME", "collision", "DATA_TYPE", Types.VARCHAR,
                                "NULLABLE", DatabaseMetaData.columnNullable, "ORDINAL_POSITION", 1)),
                List.of(
                        row("COLUMN_NAME", "trade_date", "KEY_SEQ", 2),
                        row("COLUMN_NAME", "ts_code", "KEY_SEQ", 1)),
                List.of(
                        row("INDEX_NAME", "u_beta", "COLUMN_NAME", "trade_date", "ORDINAL_POSITION", 2,
                                "TYPE", DatabaseMetaData.tableIndexOther),
                        row("INDEX_NAME", "PRIMARY", "COLUMN_NAME", "ts_code", "ORDINAL_POSITION", 1,
                                "TYPE", DatabaseMetaData.tableIndexOther),
                        row("INDEX_NAME", null, "COLUMN_NAME", null, "ORDINAL_POSITION", 0,
                                "TYPE", DatabaseMetaData.tableIndexStatistic),
                        row("INDEX_NAME", "u_alpha", "COLUMN_NAME", "ts_code", "ORDINAL_POSITION", 1,
                                "TYPE", DatabaseMetaData.tableIndexOther),
                        row("INDEX_NAME", "u_expression", "COLUMN_NAME", null, "ORDINAL_POSITION", 1,
                                "TYPE", DatabaseMetaData.tableIndexOther),
                        row("INDEX_NAME", "u_beta", "COLUMN_NAME", "ts_code", "ORDINAL_POSITION", 1,
                                "TYPE", DatabaseMetaData.tableIndexOther)));
        JdbcFixture jdbc = jdbc(Map.of("alpha__daily", shuffled));

        TableSchema actual = jdbc.inspector().inspect(new TableName("alpha__daily")).orElseThrow();

        assertThat(actual.columns()).containsExactly(
                new ColumnMetadata("ts_code", Types.VARCHAR, false),
                new ColumnMetadata("trade_date", Types.DATE, false));
        assertThat(actual.primaryKey()).containsExactly("ts_code", "trade_date");
        assertThat(actual.uniqueKeys()).containsExactly(
                new UniqueKeyMetadata("u_alpha", List.of("ts_code")),
                new UniqueKeyMetadata("u_beta", List.of("ts_code", "trade_date")),
                new UniqueKeyMetadata("u_expression", List.of()));
        assertThatThrownBy(() -> actual.columns().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> actual.primaryKey().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> actual.uniqueKeys().get(1).columns().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(jdbc.inspector().inspect(new TableName("alpha__missing"))).isEmpty();
        verify(jdbc.connection(), times(2)).close();
        jdbc.resultSets().forEach(result -> {
            try {
                verify(result, times(1)).close();
            } catch (SQLException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void isolatesMissingTableAndInspectsEachUniqueValidDefinitionOnce() throws SQLException {
        // Catches fail-fast validation and repeated metadata reads that can observe inconsistent startup state.
        DatasetDefinition missing = definition("alpha", "missing");
        DatasetDefinition valid = definition("alpha", "valid");
        JdbcFixture jdbc = jdbc(Map.of(valid.tableName().value(), table(basicSchema())));

        DatasetCatalog catalog = new DatasetStartupValidator(List.of(missing, valid), jdbc.inspector()).validate();

        assertThat(catalog.find(missing.datasetKey())).isEmpty();
        assertThat(catalog.find(valid.datasetKey())).containsSame(valid);
        verify(jdbc.metadata(), times(1)).getColumns(eq("tensor"), isNull(), eq(missing.tableName().value()), isNull());
        verify(jdbc.metadata(), times(1)).getColumns(eq("tensor"), isNull(), eq(valid.tableName().value()), isNull());
    }

    @Test
    void isolatesMissingExtraAndReorderedColumns() throws SQLException {
        // Catches prefix-only, set-only, and count-only column comparisons.
        DatasetDefinition missing = definition("alpha", "missing_col");
        DatasetDefinition extra = definition("alpha", "extra_col");
        DatasetDefinition reordered = definition("alpha", "reordered_col");
        DatasetDefinition valid = definition("alpha", "valid_col");
        TableSchema base = basicSchema();
        List<ColumnMetadata> extraColumns = new ArrayList<>(base.columns());
        extraColumns.add(2, new ColumnMetadata("rogue", Types.VARCHAR, true));
        List<ColumnMetadata> reorderedColumns = new ArrayList<>(base.columns());
        java.util.Collections.swap(reorderedColumns, 0, 1);
        JdbcFixture jdbc = jdbc(Map.of(
                missing.tableName().value(), table(schema(base.columns().subList(1, base.columns().size()),
                        base.primaryKey(), List.of())),
                extra.tableName().value(), table(schema(extraColumns, base.primaryKey(), List.of())),
                reordered.tableName().value(), table(schema(reorderedColumns, base.primaryKey(), List.of())),
                valid.tableName().value(), table(base)));

        DatasetCatalog catalog = new DatasetStartupValidator(
                List.of(missing, extra, reordered, valid), jdbc.inspector()).validate();

        assertThat(catalog.list(PluginId.of("alpha"))).containsExactly(valid);
    }

    @Test
    void isolatesBusinessAndTechnicalJdbcTypeDrift() throws SQLException {
        // Catches incomplete logical-type mapping and validators that ignore technical-column type drift.
        DatasetDefinition businessDrift = allTypesDefinition("business_type");
        DatasetDefinition technicalDrift = allTypesDefinition("technical_type");
        DatasetDefinition valid = allTypesDefinition("valid_type");
        TableSchema expected = allTypesSchema();
        List<ColumnMetadata> wrongBusiness = replace(expected.columns(), 2,
                new ColumnMetadata("note", Types.VARCHAR, true));
        List<ColumnMetadata> wrongTechnical = replace(expected.columns(), expected.columns().size() - 1,
                new ColumnMetadata("ingested_at", Types.DATE, false));
        JdbcFixture jdbc = jdbc(Map.of(
                businessDrift.tableName().value(), table(schema(wrongBusiness, expected.primaryKey(), List.of())),
                technicalDrift.tableName().value(), table(schema(wrongTechnical, expected.primaryKey(), List.of())),
                valid.tableName().value(), table(expected)));

        DatasetCatalog catalog = new DatasetStartupValidator(
                List.of(businessDrift, technicalDrift, valid), jdbc.inspector()).validate();

        assertThat(catalog.list(PluginId.of("alpha"))).containsExactly(valid);
    }

    @Test
    void isolatesBusinessAndTechnicalNullabilityDrift() throws SQLException {
        // Catches validators that compare names and types but silently accept nullable-key/source drift.
        DatasetDefinition businessDrift = definition("alpha", "business_null");
        DatasetDefinition technicalDrift = definition("alpha", "technical_null");
        DatasetDefinition valid = definition("alpha", "valid_null");
        TableSchema expected = basicSchema();
        List<ColumnMetadata> wrongBusiness = replace(expected.columns(), 0,
                new ColumnMetadata("ts_code", Types.VARCHAR, true));
        List<ColumnMetadata> wrongTechnical = replace(expected.columns(), 2,
                new ColumnMetadata("source_plugin", Types.VARCHAR, true));
        JdbcFixture jdbc = jdbc(Map.of(
                businessDrift.tableName().value(), table(schema(wrongBusiness, expected.primaryKey(), List.of())),
                technicalDrift.tableName().value(), table(schema(wrongTechnical, expected.primaryKey(), List.of())),
                valid.tableName().value(), table(expected)));

        DatasetCatalog catalog = new DatasetStartupValidator(
                List.of(businessDrift, technicalDrift, valid), jdbc.inspector()).validate();

        assertThat(catalog.list(PluginId.of("alpha"))).containsExactly(valid);
    }

    @Test
    void isolatesMissingReorderedReplacedAndAdditionalUniqueKeys() throws SQLException {
        // Catches unordered primary-key comparison and acceptance of UNIQUE as a primary-key substitute.
        DatasetDefinition missing = definition("alpha", "missing_key");
        DatasetDefinition reordered = definition("alpha", "reordered_key");
        DatasetDefinition replaced = definition("alpha", "unique_key");
        DatasetDefinition extra = definition("alpha", "extra_key");
        DatasetDefinition expression = definition("alpha", "expression_key");
        DatasetDefinition valid = definition("alpha", "valid_key");
        TableSchema base = basicSchema();
        JdbcTable expressionTable = table(base);
        List<Map<String, Object>> expressionIndexes = new ArrayList<>(expressionTable.uniqueKeys());
        expressionIndexes.add(row("INDEX_NAME", "u_expression", "COLUMN_NAME", null, "ORDINAL_POSITION", 1,
                "TYPE", DatabaseMetaData.tableIndexOther));
        JdbcFixture jdbc = jdbc(Map.of(
                missing.tableName().value(), table(schema(base.columns(), List.of(), List.of())),
                reordered.tableName().value(), table(schema(base.columns(),
                        List.of("trade_date", "ts_code"), List.of())),
                replaced.tableName().value(), table(schema(base.columns(), List.of(),
                        List.of(new UniqueKeyMetadata("u_business", List.of("ts_code", "trade_date"))))),
                extra.tableName().value(), table(schema(base.columns(), base.primaryKey(),
                        List.of(new UniqueKeyMetadata("u_extra", List.of("ts_code"))))),
                expression.tableName().value(), new JdbcTable(
                        expressionTable.columns(), expressionTable.primaryKeys(), expressionIndexes),
                valid.tableName().value(), table(base)));

        DatasetCatalog catalog = new DatasetStartupValidator(
                List.of(missing, reordered, replaced, extra, expression, valid), jdbc.inspector()).validate();

        assertThat(catalog.list(PluginId.of("alpha"))).containsExactly(valid);
    }

    @Test
    void isolatesPrimaryAndUniqueKeysThatReferenceUnknownColumns() throws SQLException {
        // Catches schema validation that trusts driver key rows without checking their column references.
        DatasetDefinition badPrimary = definition("alpha", "bad_primary_ref");
        DatasetDefinition badUnique = definition("alpha", "bad_unique_ref");
        DatasetDefinition valid = definition("alpha", "valid_ref");
        TableSchema base = basicSchema();
        JdbcFixture jdbc = jdbc(Map.of(
                badPrimary.tableName().value(), table(schema(base.columns(),
                        List.of("ts_code", "unknown"), List.of())),
                badUnique.tableName().value(), table(schema(base.columns(), base.primaryKey(),
                        List.of(new UniqueKeyMetadata("u_unknown", List.of("unknown"))))),
                valid.tableName().value(), table(base)));

        DatasetCatalog catalog = new DatasetStartupValidator(
                List.of(badPrimary, badUnique, valid), jdbc.inspector()).validate();

        assertThat(catalog.list(PluginId.of("alpha"))).containsExactly(valid);
    }

    @Test
    void isolatesInvalidDefinitionRelationshipsAndEveryDuplicateDatasetKey() throws SQLException {
        // Catches first/last-wins duplicates and definitions inspected before local metadata relations are checked.
        DatasetDefinition displayOrder = definition("alpha", "display_order", List.of(
                stringColumn("ts_code", false, 1), dateColumn("trade_date", false, 0)), List.of());
        ParameterDescriptor dangling = parameter("filter", ParameterType.TEXT, "missing");
        DatasetDefinition parameterReference = definition("alpha", "parameter_ref", basicColumns(), List.of(dangling));
        ParameterDescriptor start = parameter("start_date", ParameterType.DATE_RANGE_MEMBER, "end_date");
        ParameterDescriptor end = parameter("end_date", ParameterType.DATE_RANGE_MEMBER, "third_date");
        DatasetDefinition asymmetricRange = definition(
                "alpha", "asymmetric_range", basicColumns(), List.of(start, end));
        DatasetDefinition duplicateFirst = definition("alpha", "duplicate_key");
        DatasetDefinition duplicateSecond = definition("alpha", "duplicate_key");
        DatasetDefinition valid = definition("alpha", "valid_definition");
        JdbcFixture jdbc = jdbc(Map.of(valid.tableName().value(), table(basicSchema())));

        DatasetCatalog catalog = new DatasetStartupValidator(Arrays.asList(
                displayOrder, parameterReference, asymmetricRange, duplicateFirst, duplicateSecond, valid),
                jdbc.inspector()).validate();

        assertThat(catalog.list(PluginId.of("alpha"))).containsExactly(valid);
        verify(jdbc.metadata(), never()).getColumns(eq("tensor"), isNull(), eq(displayOrder.tableName().value()), isNull());
        verify(jdbc.metadata(), never()).getColumns(eq("tensor"), isNull(), eq(parameterReference.tableName().value()), isNull());
        verify(jdbc.metadata(), never()).getColumns(eq("tensor"), isNull(), eq(asymmetricRange.tableName().value()), isNull());
        verify(jdbc.metadata(), never()).getColumns(eq("tensor"), isNull(), eq(duplicateFirst.tableName().value()), isNull());
    }

    @Test
    void enforcesNullBoundariesAndPropagatesSafeSqlRuntimeAndErrorFailures() throws SQLException {
        // Catches unsafe SQLException leakage, swallowed VM errors, and nulls that abort otherwise valid siblings.
        DatasetDefinition valid = definition("alpha", "valid_boundary");
        JdbcFixture jdbc = jdbc(Map.of(valid.tableName().value(), table(basicSchema())));

        assertThatThrownBy(() -> new DatasetStartupValidator(null, jdbc.inspector()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DatasetStartupValidator(List.of(), null))
                .isInstanceOf(NullPointerException.class);
        DatasetCatalog catalog = new DatasetStartupValidator(Arrays.asList(null, valid), jdbc.inspector()).validate();
        assertThat(catalog.find(valid.datasetKey())).containsSame(valid);
        assertThatThrownBy(() -> catalog.find(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> catalog.list(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SchemaInspector(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> jdbc.inspector().inspect(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ColumnMetadata(" ", Types.VARCHAR, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UniqueKeyMetadata("u_test", null))
                .isInstanceOf(NullPointerException.class);

        SchemaInspector sqlFailure = inspectorThrowing(new SQLException("jdbc:mysql://secret?password=top-secret"));
        assertThatThrownBy(() -> new DatasetStartupValidator(List.of(valid), sqlFailure).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Schema inspection failed")
                .hasNoCause();

        RuntimeException runtime = new IllegalArgumentException("runtime must propagate");
        assertThatThrownBy(() -> new DatasetStartupValidator(
                List.of(valid), inspectorThrowing(runtime)).validate()).isSameAs(runtime);
        Error error = new AssertionError("error must propagate");
        assertThatThrownBy(() -> new DatasetStartupValidator(
                List.of(valid), inspectorThrowing(error)).validate()).isSameAs(error);
    }

    private static DatasetDefinition definition(String pluginId, String apiName) {
        return definition(pluginId, apiName, basicColumns(), List.of());
    }

    private static DatasetDefinition definition(
            String pluginId, String apiName, List<ColumnDefinition> columns, List<ParameterDescriptor> parameters) {
        DatasetKey key = DatasetKey.of(PluginId.of(pluginId), ApiName.of(apiName));
        return new DatasetDefinition(key, apiName, "test", QueryMode.snapshot, parameters, TableName.from(key),
                columns, new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(), null);
    }

    private static DatasetDefinition allTypesDefinition(String apiName) {
        DatasetKey key = DatasetKey.of(PluginId.of("alpha"), ApiName.of(apiName));
        List<ColumnDefinition> columns = List.of(
                stringColumn("ts_code", false, 0),
                dateColumn("trade_date", false, 1),
                column("note", LogicalType.TEXT, true, 2, null, null, null, List.of()),
                column("month", LogicalType.MONTH, true, 3, null, null, null, List.of()),
                column("shares", LogicalType.LONG, true, 4, null, null, null, List.of()),
                column("amount", LogicalType.DECIMAL, true, 5, null, 38, 18, List.of()),
                column("status", LogicalType.ENUM, true, 6, 8, null, null, List.of("OPEN", "CLOSED")));
        return new DatasetDefinition(key, apiName, "test", QueryMode.snapshot, List.of(), TableName.from(key),
                columns, new BusinessKeyDefinition(BusinessKeyMode.FINGERPRINT, List.of("ts_code", "trade_date")),
                List.of(), null);
    }

    private static List<ColumnDefinition> basicColumns() {
        return List.of(stringColumn("ts_code", false, 0), dateColumn("trade_date", false, 1));
    }

    private static ColumnDefinition stringColumn(String name, boolean nullable, int order) {
        return column(name, LogicalType.STRING, nullable, order, 64, null, null, List.of());
    }

    private static ColumnDefinition dateColumn(String name, boolean nullable, int order) {
        return column(name, LogicalType.DATE, nullable, order, null, null, null, List.of());
    }

    private static ColumnDefinition column(
            String name, LogicalType type, boolean nullable, int order,
            Integer length, Integer precision, Integer scale, List<String> allowedValues) {
        return new ColumnDefinition(
                name, name, type, nullable, order, length, precision, scale, allowedValues, type == LogicalType.TEXT);
    }

    private static ParameterDescriptor parameter(String name, ParameterType type, String related) {
        return new ParameterDescriptor(name, name, null, type, true, null, List.of(), null, related);
    }

    private static TableSchema basicSchema() {
        return schema(List.of(
                new ColumnMetadata("ts_code", Types.VARCHAR, false),
                new ColumnMetadata("trade_date", Types.DATE, false),
                new ColumnMetadata("source_plugin", Types.VARCHAR, false),
                new ColumnMetadata("source_api", Types.VARCHAR, false),
                new ColumnMetadata("ingested_at", Types.TIMESTAMP, false)),
                List.of("ts_code", "trade_date"), List.of());
    }

    private static TableSchema allTypesSchema() {
        return schema(List.of(
                new ColumnMetadata("ts_code", Types.VARCHAR, false),
                new ColumnMetadata("trade_date", Types.DATE, false),
                new ColumnMetadata("note", Types.LONGVARCHAR, true),
                new ColumnMetadata("month", Types.CHAR, true),
                new ColumnMetadata("shares", Types.BIGINT, true),
                new ColumnMetadata("amount", Types.DECIMAL, true),
                new ColumnMetadata("status", Types.CHAR, true),
                new ColumnMetadata("business_key", Types.CHAR, false),
                new ColumnMetadata("source_plugin", Types.VARCHAR, false),
                new ColumnMetadata("source_api", Types.VARCHAR, false),
                new ColumnMetadata("ingested_at", Types.TIMESTAMP, false)),
                List.of("business_key"), List.of());
    }

    private static TableSchema schema(
            List<ColumnMetadata> columns, List<String> primaryKey, List<UniqueKeyMetadata> uniqueKeys) {
        return new TableSchema(columns, primaryKey, uniqueKeys);
    }

    private static List<ColumnMetadata> replace(List<ColumnMetadata> source, int index, ColumnMetadata replacement) {
        List<ColumnMetadata> result = new ArrayList<>(source);
        result.set(index, replacement);
        return result;
    }

    private static JdbcTable table(TableSchema schema) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int index = 0; index < schema.columns().size(); index++) {
            ColumnMetadata column = schema.columns().get(index);
            columns.add(row("COLUMN_NAME", column.name(), "DATA_TYPE", column.jdbcType(),
                    "NULLABLE", column.nullable() ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls,
                    "ORDINAL_POSITION", index + 1));
        }
        List<Map<String, Object>> primaryKeys = new ArrayList<>();
        for (int index = 0; index < schema.primaryKey().size(); index++) {
            primaryKeys.add(row("COLUMN_NAME", schema.primaryKey().get(index), "KEY_SEQ", index + 1));
        }
        List<Map<String, Object>> uniqueKeys = new ArrayList<>();
        for (UniqueKeyMetadata key : schema.uniqueKeys()) {
            for (int index = 0; index < key.columns().size(); index++) {
                uniqueKeys.add(row("INDEX_NAME", key.name(), "COLUMN_NAME", key.columns().get(index),
                        "ORDINAL_POSITION", index + 1, "TYPE", DatabaseMetaData.tableIndexOther));
            }
        }
        return new JdbcTable(columns, primaryKeys, uniqueKeys);
    }

    private static JdbcFixture jdbc(Map<String, JdbcTable> tables) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        List<ResultSet> resultSets = new ArrayList<>();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("tensor");
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getColumns(eq("tensor"), isNull(), anyString(), isNull())).thenAnswer(invocation ->
                resultSet(withTableName(
                        tables.getOrDefault(invocation.getArgument(2), JdbcTable.EMPTY).columns(),
                        invocation.getArgument(2)), resultSets));
        when(metadata.getPrimaryKeys(eq("tensor"), isNull(), anyString())).thenAnswer(invocation ->
                resultSet(tables.getOrDefault(invocation.getArgument(2), JdbcTable.EMPTY).primaryKeys(), resultSets));
        when(metadata.getIndexInfo(eq("tensor"), isNull(), anyString(), eq(true), eq(false))).thenAnswer(invocation ->
                resultSet(tables.getOrDefault(invocation.getArgument(2), JdbcTable.EMPTY).uniqueKeys(), resultSets));
        return new JdbcFixture(new SchemaInspector(dataSource), metadata, connection, resultSets);
    }

    private static SchemaInspector inspectorThrowing(Throwable failure) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        if (failure instanceof SQLException exception) {
            when(dataSource.getConnection()).thenThrow(exception);
        } else if (failure instanceof RuntimeException exception) {
            when(dataSource.getConnection()).thenThrow(exception);
        } else {
            when(dataSource.getConnection()).thenThrow((Error) failure);
        }
        return new SchemaInspector(dataSource);
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows, List<ResultSet> resultSets) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        resultSets.add(resultSet);
        AtomicInteger index = new AtomicInteger(-1);
        AtomicInteger lastWasNull = new AtomicInteger();
        when(resultSet.next()).thenAnswer(ignored -> index.incrementAndGet() < rows.size());
        when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(index.get()).get(invocation.getArgument(0));
            lastWasNull.set(value == null ? 1 : 0);
            return (String) value;
        });
        when(resultSet.getInt(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(index.get()).get(invocation.getArgument(0));
            lastWasNull.set(value == null ? 1 : 0);
            return value == null ? 0 : ((Number) value).intValue();
        });
        when(resultSet.getShort(anyString())).thenAnswer(invocation -> {
            Object value = rows.get(index.get()).get(invocation.getArgument(0));
            lastWasNull.set(value == null ? 1 : 0);
            return value == null ? (short) 0 : ((Number) value).shortValue();
        });
        when(resultSet.wasNull()).thenAnswer(ignored -> lastWasNull.get() == 1);
        return resultSet;
    }

    private static List<Map<String, Object>> withTableName(List<Map<String, Object>> rows, String tableName) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : rows) {
            Map<String, Object> row = new LinkedHashMap<>(source);
            row.putIfAbsent("TABLE_NAME", tableName);
            result.add(row);
        }
        return result;
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private record JdbcFixture(
            SchemaInspector inspector,
            DatabaseMetaData metadata,
            Connection connection,
            List<ResultSet> resultSets) {
    }

    private record JdbcTable(
            List<Map<String, Object>> columns,
            List<Map<String, Object>> primaryKeys,
            List<Map<String, Object>> uniqueKeys) {
        private static final JdbcTable EMPTY = new JdbcTable(List.of(), List.of(), List.of());
    }
}
