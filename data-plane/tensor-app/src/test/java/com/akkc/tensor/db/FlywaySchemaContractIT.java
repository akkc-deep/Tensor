package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class FlywaySchemaContractIT {
    private static final String SCHEMA = "tensor";
    private static final String FIXTURE_TABLE = "fixture__fixture_daily";
    private static final String COLLATION = "utf8mb4_0900_as_cs";
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
            .withDatabaseName(SCHEMA)
            .withUsername("tensor")
            .withPassword("tensor")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=" + COLLATION);

    private static List<DatasetDefinition> definitions;
    private static SchemaSnapshot snapshot;
    private static int firstMigrationsExecuted;
    private static boolean validationSuccessful;
    private static int repeatMigrationsExecuted;
    private static String mysqlVersion;

    @BeforeAll
    static void prepareSchema() throws SQLException {
        definitions = new DatasetDefinitionLoader().loadAll(
                new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml");
        assertThat(definitions).hasSize(49);
        assertThat(definitions).isSortedAccordingTo(Comparator.comparing(
                value -> value.datasetKey().apiName().value()));
        assertThat(definitions.stream().map(value -> value.datasetKey().apiName().value())).doesNotHaveDuplicates();
        assertThat(definitions.stream().map(value -> value.tableName().value())).doesNotHaveDuplicates();
        assertThat(definitions.stream().mapToInt(value -> value.columns().size()).sum()).isEqualTo(851);
        assertThat(definitions.stream().filter(value -> value.businessKey().mode() == BusinessKeyMode.COMPOSITE)).hasSize(47);
        assertThat(definitions.stream().filter(value -> value.businessKey().mode() == BusinessKeyMode.FINGERPRINT)
                .map(value -> value.datasetKey().apiName().value()))
                .containsExactly("pledge_detail", "stk_managers");

        MYSQL.start();
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
        MigrateResult firstMigration = flyway.migrate();
        firstMigrationsExecuted = firstMigration.migrationsExecuted;
        assertThat(firstMigrationsExecuted).as("first Flyway migration count").isEqualTo(6);
        ValidateResult validation = flyway.validateWithResult();
        validationSuccessful = validation.validationSuccessful;
        assertThat(validationSuccessful).as(validation.getAllErrorMessages()).isTrue();
        MigrateResult repeatMigration = flyway.migrate();
        repeatMigrationsExecuted = repeatMigration.migrationsExecuted;
        assertThat(repeatMigrationsExecuted).as("repeat Flyway migration count").isZero();

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            mysqlVersion = singleString(connection, "SELECT VERSION()");
            snapshot = readSnapshot(connection);
        }
    }

    @AfterAll
    static void stopContainer() {
        MYSQL.stop();
    }

    @TestFactory
    Stream<DynamicTest> productionSchemasMatchDatasetDefinitions() {
        return definitions.stream().map(definition -> DynamicTest.dynamicTest(
                "schema contract: " + definition.datasetKey().apiName().value(),
                () -> assertProductionSchema(definition)));
    }

    @Test
    void migratesAndValidatesRepeatablyOnMySql846() {
        assertThat(mysqlVersion).startsWith("8.4.6");
        assertThat(firstMigrationsExecuted).isEqualTo(6);
        assertThat(validationSuccessful).isTrue();
        assertThat(repeatMigrationsExecuted).isZero();
        assertThat(snapshot.tables()).hasSize(50);
        assertThat(snapshot.columns().values().stream().mapToInt(List::size).sum()).isEqualTo(1007);
        assertThat(snapshot.indexes().values().stream().flatMap(value -> value.values().stream())
                .filter(value -> value.name().equals("PRIMARY"))).hasSize(50);
        assertThat(snapshot.indexes().values().stream().flatMap(value -> value.values().stream())
                .filter(value -> !value.name().equals("PRIMARY"))).hasSize(40);

        Set<String> productionTables = definitions.stream().map(value -> value.tableName().value())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(snapshot.tables().keySet()).containsAll(productionTables).contains(FIXTURE_TABLE);
        assertThat(productionTables).hasSize(49);
        assertThat(productionTables.stream().mapToInt(table -> snapshot.columns().get(table).size()).sum()).isEqualTo(1000);
        assertThat(productionTables.stream().map(table -> snapshot.indexes().get(table).get("PRIMARY"))).hasSize(49);
        assertThat(productionTables.stream().flatMap(table -> snapshot.indexes().get(table).values().stream())
                .filter(value -> !value.name().equals("PRIMARY"))).hasSize(40);
    }

    @Test
    void fixtureSchemaMatchesContract() {
        assertColumns(FIXTURE_TABLE, fixtureColumns());
        assertTable(FIXTURE_TABLE);
        assertIndexes(FIXTURE_TABLE, List.of("ts_code", "trade_date"), Map.of());
    }

    @Test
    void keepsV6InTestOutputOnly() throws URISyntaxException, IOException {
        Path testClasses = Paths.get(FlywaySchemaContractIT.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        assertThat(migrationNames(testClasses.resolve("db/migration"))).containsExactly("V6__create_fixture_tables.sql");
        assertThat(migrationNames(testClasses.getParent().resolve("classes/db/migration"))).containsExactly(
                "V1__create_basic_and_organization_tables.sql",
                "V2__create_market_and_trading_tables.sql",
                "V3__create_connect_and_slb_tables.sql",
                "V4__create_financial_tables.sql",
                "V5__create_corporate_and_governance_tables.sql");
    }

    private static void assertProductionSchema(DatasetDefinition definition) {
        String table = definition.tableName().value();
        List<ExpectedColumn> expectedColumns = new ArrayList<>();
        for (int index = 0; index < definition.columns().size(); index++) {
            ColumnDefinition column = definition.columns().get(index);
            assertThat(column.displayOrder()).as("%s display order for %s", table, column.name()).isEqualTo(index);
            expectedColumns.add(expectedBusinessColumn(column));
        }
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            expectedColumns.add(column("business_key", "char", Types.CHAR, false, 64, null, null, null));
        }
        expectedColumns.addAll(sourceColumns());
        assertColumns(table, expectedColumns);
        assertTable(table);

        List<String> primary = definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT
                ? List.of("business_key") : definition.businessKey().fields();
        Map<String, List<String>> secondary = new LinkedHashMap<>();
        for (var filter : definition.filters()) {
            if (!filter.field().equals(primary.getFirst())) {
                secondary.put("idx_" + definition.datasetKey().apiName().value() + "_" + filter.field(), List.of(filter.field()));
            }
        }
        assertIndexes(table, primary, secondary);
    }

    private static ExpectedColumn expectedBusinessColumn(ColumnDefinition column) {
        return switch (column.logicalType()) {
            case STRING -> column(column.name(), "varchar", Types.VARCHAR, column.nullable(), column.length(), null, null, null);
            case TEXT -> column(column.name(), "text", Types.LONGVARCHAR, column.nullable(), 65535, null, null, null);
            case DATE -> column(column.name(), "date", Types.DATE, column.nullable(), null, null, null, null);
            case MONTH -> column(column.name(), "char", Types.CHAR, column.nullable(), 6, null, null, null);
            case LONG -> column(column.name(), "bigint", Types.BIGINT, column.nullable(), null, 19, 0, null);
            case DECIMAL -> column(column.name(), "decimal", Types.DECIMAL, column.nullable(), null,
                    column.precision(), column.scale(), null);
            case ENUM -> throw new AssertionError("Unsupported logical type: " + column.logicalType());
        };
    }

    private static List<ExpectedColumn> fixtureColumns() {
        return List.of(
                column("ts_code", "varchar", Types.VARCHAR, false, 64, null, null, null),
                column("trade_date", "date", Types.DATE, false, null, null, null, null),
                column("amount", "decimal", Types.DECIMAL, false, null, 38, 18, null),
                column("note", "varchar", Types.VARCHAR, true, 255, null, null, null),
                sourceColumns().get(0), sourceColumns().get(1), sourceColumns().get(2));
    }

    private static List<ExpectedColumn> sourceColumns() {
        return List.of(
                column("source_plugin", "varchar", Types.VARCHAR, false, 64, null, null, null),
                column("source_api", "varchar", Types.VARCHAR, false, 64, null, null, null),
                column("ingested_at", "datetime", Types.TIMESTAMP, false, null, null, null, 3));
    }

    private static ExpectedColumn column(String name, String dataType, int jdbcType, boolean nullable,
                                         Integer characterLength, Integer numericPrecision, Integer numericScale,
                                         Integer datetimePrecision) {
        return new ExpectedColumn(name, dataType, jdbcType, nullable, characterLength, numericPrecision,
                numericScale, datetimePrecision);
    }

    private static void assertColumns(String table, List<ExpectedColumn> expected) {
        List<ColumnSnapshot> actual = snapshot.columns().get(table);
        assertThat(actual).as("columns for %s", table).isNotNull().hasSize(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            ExpectedColumn expectedColumn = expected.get(index);
            ColumnSnapshot actualColumn = actual.get(index);
            assertThat(actualColumn.name()).as("column name for %s ordinal %s", table, index + 1)
                    .isEqualTo(expectedColumn.name());
            assertThat(actualColumn.ordinal()).isEqualTo(index + 1);
            assertThat(actualColumn.dataType()).isEqualTo(expectedColumn.dataType());
            assertThat(jdbcType(actualColumn.dataType())).isEqualTo(expectedColumn.jdbcType());
            assertThat(actualColumn.nullable()).isEqualTo(expectedColumn.nullable());
            assertThat(actualColumn.characterLength()).isEqualTo(expectedColumn.characterLength());
            assertThat(actualColumn.numericPrecision()).isEqualTo(expectedColumn.numericPrecision());
            assertThat(actualColumn.numericScale()).isEqualTo(expectedColumn.numericScale());
            assertThat(actualColumn.datetimePrecision()).isEqualTo(expectedColumn.datetimePrecision());
        }
    }

    private static void assertTable(String table) {
        TableSnapshot actual = snapshot.tables().get(table);
        assertThat(actual).as("table %s", table).isNotNull();
        assertThat(actual.engine()).isEqualTo("InnoDB");
        assertThat(actual.collation()).isEqualTo(COLLATION);
    }

    private static void assertIndexes(String table, List<String> primary, Map<String, List<String>> secondary) {
        Map<String, IndexSnapshot> actual = snapshot.indexes().get(table);
        assertThat(actual).as("indexes for %s", table).isNotNull().hasSize(secondary.size() + 1);
        assertThat(actual.get("PRIMARY")).isEqualTo(new IndexSnapshot("PRIMARY", false, primary));
        secondary.forEach((name, columns) -> assertThat(actual.get(name))
                .isEqualTo(new IndexSnapshot(name, true, columns)));
    }

    private static SchemaSnapshot readSnapshot(Connection connection) throws SQLException {
        Map<String, TableSnapshot> tables = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
                SELECT table_name, engine, table_collation
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                tables.put(result.getString("table_name"), new TableSnapshot(result.getString("engine"),
                        result.getString("table_collation")));
            }
        }
        Map<String, List<ColumnSnapshot>> columns = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
                SELECT table_name, column_name, ordinal_position, data_type, is_nullable,
                       character_maximum_length, numeric_precision, numeric_scale, datetime_precision
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                ORDER BY table_name, ordinal_position
                """); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                columns.computeIfAbsent(result.getString("table_name"), ignored -> new ArrayList<>()).add(
                        new ColumnSnapshot(result.getString("column_name"), result.getInt("ordinal_position"),
                                result.getString("data_type"), "YES".equals(result.getString("is_nullable")),
                                nullableInt(result, "character_maximum_length"), nullableInt(result, "numeric_precision"),
                                nullableInt(result, "numeric_scale"), nullableInt(result, "datetime_precision")));
            }
        }
        Map<String, Map<String, IndexSnapshot>> indexes = new LinkedHashMap<>();
        Map<String, Map<String, MutableIndex>> mutableIndexes = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
                SELECT table_name, index_name, non_unique, seq_in_index, column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                ORDER BY table_name, index_name, seq_in_index
                """); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String table = result.getString("table_name");
                String name = result.getString("index_name");
                MutableIndex index = mutableIndexes.computeIfAbsent(table, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(name, ignored -> new MutableIndex(name, resultUnchecked(result, "non_unique") == 1));
                index.columns().add(result.getString("column_name"));
            }
        }
        mutableIndexes.forEach((table, byName) -> {
            Map<String, IndexSnapshot> byNameSnapshot = new LinkedHashMap<>();
            byName.forEach((name, index) -> byNameSnapshot.put(name,
                    new IndexSnapshot(index.name(), index.nonUnique(), List.copyOf(index.columns()))));
            indexes.put(table, byNameSnapshot);
        });
        return new SchemaSnapshot(tables, columns, indexes);
    }

    private static int resultUnchecked(ResultSet result, String column) {
        try {
            return result.getInt(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Integer nullableInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static int jdbcType(String dataType) {
        return switch (dataType) {
            case "varchar" -> Types.VARCHAR;
            case "text" -> Types.LONGVARCHAR;
            case "date" -> Types.DATE;
            case "char" -> Types.CHAR;
            case "bigint" -> Types.BIGINT;
            case "decimal" -> Types.DECIMAL;
            case "datetime" -> Types.TIMESTAMP;
            default -> throw new AssertionError("Unsupported MySQL data type: " + dataType);
        };
    }

    private static String singleString(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static List<String> migrationNames(Path migrationDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(migrationDirectory)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private record ExpectedColumn(String name, String dataType, int jdbcType, boolean nullable,
                                  Integer characterLength, Integer numericPrecision, Integer numericScale,
                                  Integer datetimePrecision) {
    }

    private record ColumnSnapshot(String name, int ordinal, String dataType, boolean nullable,
                                  Integer characterLength, Integer numericPrecision, Integer numericScale,
                                  Integer datetimePrecision) {
    }

    private record TableSnapshot(String engine, String collation) {
    }

    private record IndexSnapshot(String name, boolean nonUnique, List<String> columns) {
    }

    private record MutableIndex(String name, boolean nonUnique, List<String> columns) {
        private MutableIndex(String name, boolean nonUnique) {
            this(name, nonUnique, new ArrayList<>());
        }
    }

    private record SchemaSnapshot(Map<String, TableSnapshot> tables, Map<String, List<ColumnSnapshot>> columns,
                                  Map<String, Map<String, IndexSnapshot>> indexes) {
    }
}
