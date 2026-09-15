package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
        assertThat(definitions).hasSize(40);
        assertThat(definitions).isSortedAccordingTo(Comparator.comparing(
                value -> value.datasetKey().apiName().value()));
        assertThat(definitions.stream().map(value -> value.datasetKey().apiName().value())).doesNotHaveDuplicates();
        assertThat(definitions.stream().map(value -> value.tableName().value())).doesNotHaveDuplicates();
        assertThat(definitions.stream().mapToInt(value -> value.columns().size()).sum()).isEqualTo(789);
        assertThat(definitions.stream().filter(value -> value.businessKey().mode() == BusinessKeyMode.COMPOSITE)).hasSize(37);
        assertThat(definitions.stream().filter(value -> value.businessKey().mode() == BusinessKeyMode.FINGERPRINT)
                .map(value -> value.datasetKey().apiName().value()))
                .containsExactly("dividend", "pledge_detail", "stk_managers");

        MYSQL.start();
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
        MigrateResult firstMigration = flyway.migrate();
        firstMigrationsExecuted = firstMigration.migrationsExecuted;
        assertThat(firstMigrationsExecuted).as("first Flyway migration count").isEqualTo(8);
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
        assertThat(firstMigrationsExecuted).isEqualTo(8);
        assertThat(validationSuccessful).isTrue();
        assertThat(repeatMigrationsExecuted).isZero();
        assertThat(snapshot.tables()).hasSize(52);
        assertThat(snapshot.columns().values().stream().mapToInt(List::size).sum()).isEqualTo(1051);
        assertThat(snapshot.indexes().values().stream().flatMap(value -> value.values().stream())
                .filter(value -> value.name().equals("PRIMARY"))).hasSize(52);
        assertThat(snapshot.indexes().values().stream().flatMap(value -> value.values().stream())
                .filter(value -> !value.name().equals("PRIMARY"))).hasSize(48);

        Set<String> productionTables = definitions.stream().map(value -> value.tableName().value())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(snapshot.tables().keySet()).containsAll(productionTables).contains(FIXTURE_TABLE);
        assertThat(productionTables).hasSize(40);
        assertThat(productionTables.stream().mapToInt(table -> snapshot.columns().get(table).size()).sum()).isEqualTo(912);
        assertThat(productionTables.stream().map(table -> snapshot.indexes().get(table).get("PRIMARY"))).hasSize(40);
        assertThat(productionTables.stream().flatMap(table -> snapshot.indexes().get(table).values().stream())
                .filter(value -> !value.name().equals("PRIMARY"))).hasSize(34);
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
                "V5__create_corporate_and_governance_tables.sql",
                "V7__version_dividend_business_key.sql",
                "V8__create_download_task_tables.sql");
    }

    @Test
    void taskTablesHaveExactColumnsDefaultsIndexesAndConstraints() {
        String task = "tensor_download_task";
        String batch = "tensor_download_batch";
        assertTable(task);
        assertTable(batch);
        assertColumns(task, List.of(
                textColumn("task_id", 36, false, true), textColumn("submission_id", 36, false, true),
                textColumn("request_hash", 64, false, true), textColumn("plugin_id", 64, false, false),
                textColumn("api_name", 64, false, false), textColumn("mode", 8, false, false),
                jsonColumn("params"), textColumn("definition_hash", 64, false, true), jsonColumn("policy_snapshot"),
                textColumn("status", 24, false, false), numberColumn("plan_ready", "tinyint", Types.TINYINT, 3, false),
                textColumn("active_run_id", 36, false, true), numberColumn("run_generation", "int", Types.INTEGER, 10, false),
                numberColumn("version", "bigint", Types.BIGINT, 19, false),
                numberColumn("request_count", "bigint", Types.BIGINT, 19, false),
                numberColumn("run_request_count", "bigint", Types.BIGINT, 19, false),
                textColumn("last_error_code", 64, true, false), textColumn("last_error_message", 512, true, false),
                timeColumn("created_at", false), timeColumn("updated_at", false), timeColumn("queued_at", false),
                timeColumn("started_at", true), timeColumn("finished_at", true), timeColumn("deadline_at", true)));
        assertColumns(batch, List.of(
                textColumn("batch_id", 36, false, true), textColumn("task_id", 36, false, true),
                textColumn("parent_batch_id", 36, true, true), textColumn("batch_key", 128, false, false),
                column("range_start", "date", Types.DATE, true, null, null, null, null),
                column("range_end", "date", Types.DATE, true, null, null, null, null),
                jsonColumn("source_params"), textColumn("status", 16, false, false),
                numberColumn("attempt_count", "int", Types.INTEGER, 10, false),
                numberColumn("run_generation", "int", Types.INTEGER, 10, true),
                numberColumn("source_rows", "bigint", Types.BIGINT, 19, false),
                numberColumn("inserted_rows", "bigint", Types.BIGINT, 19, false),
                numberColumn("updated_rows", "bigint", Types.BIGINT, 19, false),
                textColumn("error_code", 64, true, false), textColumn("error_message", 512, true, false),
                timeColumn("created_at", false), timeColumn("updated_at", false),
                timeColumn("started_at", true), timeColumn("finished_at", true)));
        assertThat(snapshot.indexes().get(task)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "PRIMARY", new IndexSnapshot("PRIMARY", false, List.of("task_id")),
                "uk_download_task_submission", new IndexSnapshot("uk_download_task_submission", false, List.of("submission_id")),
                "idx_download_task_queue", new IndexSnapshot("idx_download_task_queue", true,
                        List.of("status", "active_run_id", "queued_at", "task_id")),
                "idx_download_task_created", new IndexSnapshot("idx_download_task_created", true, List.of("created_at", "task_id")),
                "idx_download_task_source", new IndexSnapshot("idx_download_task_source", true,
                        List.of("plugin_id", "api_name", "created_at", "task_id"))));
        assertThat(snapshot.indexes().get(batch)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "PRIMARY", new IndexSnapshot("PRIMARY", false, List.of("batch_id")),
                "uk_download_batch_key", new IndexSnapshot("uk_download_batch_key", false, List.of("task_id", "batch_key")),
                "idx_download_batch_status", new IndexSnapshot("idx_download_batch_status", true, List.of("task_id", "status", "batch_key")),
                "idx_download_batch_parent", new IndexSnapshot("idx_download_batch_parent", true, List.of("parent_batch_id"))));
        JdbcTemplate jdbc = jdbc(MYSQL.getJdbcUrl(), MYSQL.getUsername());
        for (String table : List.of(task, batch)) {
            Map<String, String> defaults = new LinkedHashMap<>();
            jdbc.query("SELECT column_name, column_default, extra FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = ?", result -> {
                if (result.getString("column_default") != null) {
                    defaults.put(result.getString("column_name"), result.getString("column_default"));
                }
                assertThat(result.getString("extra")).isEmpty();
            }, table);
            assertThat(defaults).containsExactlyInAnyOrderEntriesOf(table.equals(task)
                    ? Map.of("plan_ready", "0", "run_generation", "0", "version", "1", "request_count", "0", "run_request_count", "0")
                    : Map.of("attempt_count", "0", "source_rows", "0", "inserted_rows", "0", "updated_rows", "0"));
        }
        assertThat(jdbc.queryForList("SELECT constraint_name FROM information_schema.table_constraints "
                + "WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' AND table_name IN (?, ?)",
                String.class, task, batch)).containsExactlyInAnyOrder(
                "ck_download_task_mode", "ck_download_task_status", "ck_download_task_plan_ready", "ck_download_task_counters",
                "ck_download_task_params", "ck_download_task_policy", "ck_download_task_error",
                "ck_download_batch_status", "ck_download_batch_range", "ck_download_batch_counters", "ck_download_batch_params",
                "ck_download_batch_error", "ck_download_batch_parent", "ck_download_batch_success_counts");
        assertThat(jdbc.queryForList("SELECT CONCAT(k.constraint_name, ':', k.column_name, ':', k.referenced_table_name, ':', "
                + "k.referenced_column_name, ':', r.delete_rule, ':', r.update_rule) "
                + "FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r "
                + "ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name "
                + "WHERE k.constraint_schema = DATABASE() AND k.table_name = ?", String.class, batch))
                .containsExactlyInAnyOrder(
                        "fk_download_batch_task:task_id:tensor_download_task:task_id:RESTRICT:RESTRICT",
                        "fk_download_batch_parent:parent_batch_id:tensor_download_batch:batch_id:RESTRICT:RESTRICT");
    }

    @Test
    void upgradesV7WithoutChangingChecksumsOrExistingSecurities() {
        JdbcTemplate admin = jdbc(MYSQL.getJdbcUrl(), "root");
        admin.execute("CREATE DATABASE tensor_upgrade CHARACTER SET utf8mb4 COLLATE " + COLLATION);
        String url = MYSQL.getJdbcUrl().replace("/" + SCHEMA, "/tensor_upgrade");
        Flyway old = Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration").target("7").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(7);
        JdbcTemplate upgraded = jdbc(url, "root");
        List<Map<String, Object>> before = upgraded.queryForList(
                "SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank");
        upgraded.update("INSERT INTO tushare_pro__daily (ts_code, trade_date, source_plugin, source_api, ingested_at) "
                + "VALUES ('000001.SZ', '2024-02-29', 'tushare_pro', 'daily', '2024-02-29 12:00:00.123')");
        Flyway current = Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration").load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(current.validateWithResult().validationSuccessful).isTrue();
        assertThat(upgraded.queryForList("SELECT version, checksum FROM flyway_schema_history "
                + "WHERE version <> '8' ORDER BY installed_rank")).isEqualTo(before);
        assertThat(upgraded.queryForObject("SELECT COUNT(*) FROM tushare_pro__daily "
                + "WHERE ts_code = '000001.SZ' AND trade_date = '2024-02-29' AND source_api = 'daily'", Integer.class)).isEqualTo(1);
        assertThat(upgraded.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isZero();
        assertThat(upgraded.queryForObject("SELECT COUNT(*) FROM tensor_download_batch", Integer.class)).isZero();
        assertThat(current.migrate().migrationsExecuted).isZero();
    }

    @Test
    void productionMigrationInventoryCreates51TablesWithoutFixture() {
        jdbc(MYSQL.getJdbcUrl(), "root").execute("CREATE DATABASE tensor_production CHARACTER SET utf8mb4 COLLATE " + COLLATION);
        String url = MYSQL.getJdbcUrl().replace("/" + SCHEMA, "/tensor_production");
        Flyway production = Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load();
        assertThat(production.migrate().migrationsExecuted).isEqualTo(7);
        JdbcTemplate jdbc = jdbc(url, "root");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(51);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(1044);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT table_name, index_name) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' "
                + "AND index_name = 'PRIMARY'", Integer.class)).isEqualTo(51);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT table_name, index_name) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' "
                + "AND index_name <> 'PRIMARY'", Integer.class)).isEqualTo(48);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5", "7", "8");
        assertThat(jdbc.queryForList("SHOW TABLES", String.class)).contains("tensor_download_task", "tensor_download_batch")
                .doesNotContain(FIXTURE_TABLE);
        assertThat(production.migrate().migrationsExecuted).isZero();
    }

    @Test
    void mysqlRejectsTaskAndBatchConstraintViolations() {
        JdbcTemplate jdbc = jdbc(MYSQL.getJdbcUrl(), MYSQL.getUsername());
        String taskId = "c67429d7-c9c2-4d00-aa00-000000000001";
        String batchId = "c67429d7-c9c2-4d00-aa00-000000000002";
        jdbc.update("INSERT INTO tensor_download_task (task_id, submission_id, request_hash, plugin_id, api_name, mode, "
                + "params, definition_hash, policy_snapshot, status, active_run_id, created_at, updated_at, queued_at) "
                + "VALUES (?, ?, ?, 'fixture', 'daily', 'SINGLE', '{}', ?, '{}', 'QUEUED', ?, NOW(3), NOW(3), NOW(3))",
                taskId, taskId, "a".repeat(64), "b".repeat(64), taskId);
        jdbc.update("INSERT INTO tensor_download_batch (batch_id, task_id, batch_key, source_params, status, created_at, updated_at) "
                + "VALUES (?, ?, '000001', '{}', 'PENDING', NOW(3), NOW(3))", batchId, taskId);
        for (var invalid : List.of(
                Map.entry("mode='OTHER'", "mode"), Map.entry("status='DONE'", "status"),
                Map.entry("plan_ready=2", "plan_ready"), Map.entry("run_generation=-1", "counters"),
                Map.entry("version=0", "counters"), Map.entry("request_count=-1", "counters"),
                Map.entry("run_request_count=-1", "counters"), Map.entry("run_request_count=1", "counters"),
                Map.entry("params='[]'", "params"), Map.entry("policy_snapshot='[]'", "policy"),
                Map.entry("last_error_code='SOURCE_TIMEOUT'", "error"), Map.entry("last_error_message='Failed'", "error"))) {
            assertThatThrownBy(() -> jdbc.update("UPDATE tensor_download_task SET " + invalid.getKey() + " WHERE task_id=?", taskId))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasMessageContaining("ck_download_task_" + invalid.getValue());
        }
        for (var invalid : List.of(
                Map.entry("status='DONE'", "status"), Map.entry("range_start='2024-02-29'", "range"),
                Map.entry("range_end='2024-02-29'", "range"),
                Map.entry("range_start='2024-03-01', range_end='2024-02-29'", "range"),
                Map.entry("attempt_count=-1", "counters"), Map.entry("run_generation=0", "counters"),
                Map.entry("status='SUCCEEDED', source_rows=-1", "counters"),
                Map.entry("status='SUCCEEDED', inserted_rows=-1", "counters"),
                Map.entry("status='SUCCEEDED', updated_rows=-1", "counters"),
                Map.entry("source_params='[]'", "params"), Map.entry("error_code='SOURCE_TIMEOUT'", "error"),
                Map.entry("error_message='Failed'", "error"), Map.entry("parent_batch_id=batch_id", "parent"),
                Map.entry("source_rows=1", "success_counts"), Map.entry("inserted_rows=1", "success_counts"),
                Map.entry("updated_rows=1", "success_counts"))) {
            assertThatThrownBy(() -> jdbc.update("UPDATE tensor_download_batch SET " + invalid.getKey() + " WHERE batch_id=?", batchId))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasMessageContaining("ck_download_batch_" + invalid.getValue());
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE tensor_download_batch SET task_id='missing' WHERE batch_id=?", batchId))
                .hasMessageContaining("fk_download_batch_task");
        assertThatThrownBy(() -> jdbc.update("UPDATE tensor_download_batch SET parent_batch_id='missing' WHERE batch_id=?", batchId))
                .hasMessageContaining("fk_download_batch_parent");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tensor_download_task WHERE task_id=?", taskId))
                .hasMessageContaining("fk_download_batch_task");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tensor_download_batch (batch_id, task_id, batch_key, source_params, status, created_at, updated_at) "
                + "VALUES ('c67429d7-c9c2-4d00-aa00-000000000003', ?, '000001', '{}', 'PENDING', NOW(3), NOW(3))", taskId))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class).hasMessageContaining("uk_download_batch_key");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tensor_download_task (task_id, submission_id, request_hash, plugin_id, api_name, mode, "
                + "params, definition_hash, policy_snapshot, status, active_run_id, created_at, updated_at, queued_at) "
                + "SELECT 'c67429d7-c9c2-4d00-aa00-000000000004', submission_id, request_hash, plugin_id, api_name, mode, "
                + "params, definition_hash, policy_snapshot, status, active_run_id, created_at, updated_at, queued_at "
                + "FROM tensor_download_task WHERE task_id=?", taskId))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class).hasMessageContaining("uk_download_task_submission");
        assertThat(jdbc.queryForObject("SELECT status FROM tensor_download_batch WHERE batch_id=?", String.class, batchId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM tensor_download_task WHERE task_id=?", Long.class, taskId)).isEqualTo(1);
    }

    private static JdbcTemplate jdbc(String url, String username) {
        return new JdbcTemplate(new DriverManagerDataSource(url, username, MYSQL.getPassword()));
    }

    private static ExpectedColumn textColumn(String name, int length, boolean nullable, boolean fixed) {
        return column(name, fixed ? "char" : "varchar", fixed ? Types.CHAR : Types.VARCHAR,
                nullable, length, null, null, null);
    }

    private static ExpectedColumn jsonColumn(String name) {
        return column(name, "json", Types.LONGVARCHAR, false, null, null, null, null);
    }

    private static ExpectedColumn numberColumn(String name, String type, int jdbcType, int precision, boolean nullable) {
        return column(name, type, jdbcType, nullable, null, precision, 0, null);
    }

    private static ExpectedColumn timeColumn(String name, boolean nullable) {
        return column(name, "datetime", Types.TIMESTAMP, nullable, null, null, null, 3);
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
            case "tinyint" -> Types.TINYINT;
            case "int" -> Types.INTEGER;
            case "json" -> Types.LONGVARCHAR;
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
