package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.persistence.ExistingKeyRepository;
import com.akkc.tensor.core.persistence.GenericUpsertRepository;
import com.akkc.tensor.core.persistence.PersistenceService;
import com.akkc.tensor.core.persistence.WriteCounts;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class DividendBusinessKeyMigrationIT {
    private static final String TABLE = "tushare_pro__dividend";
    private static final String V7 = "db/migration/V7__version_dividend_business_key.sql";
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
            .withDatabaseName("tensor")
            .withUsername("tensor")
            .withPassword("tensor")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs");

    private static DataSource dataSource;

    @BeforeAll
    static void startContainer() {
        MYSQL.start();
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @BeforeEach
    void resetSchema() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .cleanDisabled(false).load().clean();
    }

    @AfterAll
    static void stopContainer() {
        MYSQL.stop();
    }

    @Test
    void migratesFreshDatabaseAndValidatesRepeatably() {
        Flyway flyway = flyway(dataSource);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(primaryKey(jdbc)).containsExactly("business_key");
        assertThat(indexes(jdbc)).containsEntry("idx_dividend_ann_date", List.of("ann_date"))
                .containsEntry("idx_dividend_ts_code", List.of("ts_code"));
    }

    @Test
    void upgradesV6RowsWithoutChangingBusinessOrSourceValuesAndMatchesJavaFingerprint() {
        Flyway v6 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("6").load();
        assertThat(v6.migrate().migrationsExecuted).isEqualTo(6);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertLegacyRows(jdbc);
        List<DividendRow> before = dividendRows(jdbc);

        Flyway v7 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("7").load();
        assertThat(v7.migrate().migrationsExecuted).isOne();
        assertThat(v7.validateWithResult().validationSuccessful).isTrue();

        assertThat(dividendRows(jdbc)).containsExactlyElementsOf(before);
        Map<String, String> migratedKeys = jdbc.query(
                "SELECT ts_code, business_key FROM " + TABLE + " ORDER BY ts_code",
                result -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    while (result.next()) {
                        values.put(result.getString(1), result.getString(2));
                    }
                    return values;
                });
        FingerprintKeyCodec codec = new FingerprintKeyCodec();
        for (DividendRow row : before) {
            assertThat(migratedKeys.get(row.tsCode())).isEqualTo(codec.sha256(
                    List.of("ts_code", "end_date", "ann_date", "div_proc"),
                    linkedRow(
                            "ts_code", row.tsCode(), "end_date", row.endDate(),
                            "ann_date", row.annDate(), "div_proc", row.divProc())));
        }
        assertThat(migratedKeys.get("NULL.SZ")).isNotEqualTo(migratedKeys.get("TEXT.SZ"));
        assertThat(primaryKey(jdbc)).containsExactly("business_key");
        assertThat(indexes(jdbc)).containsEntry("idx_dividend_ann_date", List.of("ann_date"))
                .containsEntry("idx_dividend_ts_code", List.of("ts_code"));
    }

    @Test
    void updatesMigratedStageAcrossBatchAndInsertsDifferentStage() {
        Flyway v6 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("6").load();
        assertThat(v6.migrate().migrationsExecuted).isEqualTo(6);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertLegacyRow(jdbc, new DividendRow(
                "000001.SZ", LocalDate.of(2025, 12, 31), LocalDate.of(2026, 3, 1), "实施",
                null, null, null, new BigDecimal("0.110000000000000000"),
                new BigDecimal("0.110000000000000000"), null, null, null, null, null,
                "legacy-plugin", "dividend", Instant.parse("2026-09-05T01:02:03.456Z")));
        assertThat(Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("7").load().migrate().migrationsExecuted).isOne();

        DatasetDefinition definition = dividendDefinition();
        DatasetCatalog catalog = new DatasetStartupValidator(
                List.of(definition), new SchemaInspector(dataSource)).validate();
        assertThat(catalog.find(definition.datasetKey())).isPresent();
        PersistenceService persistence = new PersistenceService(
                catalog,
                new DatasetLockManager(),
                new ExistingKeyRepository(jdbc),
                new GenericUpsertRepository(jdbc),
                new DataSourceTransactionManager(dataSource));

        assertThat(persistence.persist(adapt(definition, Instant.parse("2026-09-06T02:00:00Z"),
                dividendRow("实施", "0.120000000000000000"))))
                .isEqualTo(new WriteCounts(0, 1));
        assertThat(persistence.persist(adapt(definition, Instant.parse("2026-09-06T03:00:00Z"),
                dividendRow("预案", "0.100000000000000000"))))
                .isEqualTo(new WriteCounts(1, 0));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT cash_div FROM " + TABLE + " WHERE div_proc = '实施'", BigDecimal.class))
                .isEqualByComparingTo("0.120000000000000000");
    }

    @Test
    void rejectsSameIdentityConflicts() {
        DatasetDefinition definition = dividendDefinition();

        assertThatThrownBy(() -> adapt(definition, Instant.parse("2026-09-06T04:00:00Z"),
                dividendRow("实施", "0.120000000000000000"),
                dividendRow("实施", "0.130000000000000000")))
                .isInstanceOfSatisfying(AdapterException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
                    assertThat(failure).hasMessage("Conflicting adapter key: api=dividend, row=1").hasNoCause();
                });
    }

    @Test
    void failedFinalPrimaryKeySwitchRetainsLegacyPrimaryKey() throws IOException {
        Flyway v6 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("6").load();
        assertThat(v6.migrate().migrationsExecuted).isEqualTo(6);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertLegacyRows(jdbc);
        List<String> statements = v7Statements();
        assertThat(statements).hasSize(3);
        jdbc.execute(statements.get(0));
        jdbc.execute(statements.get(1));
        String nullKey = jdbc.queryForObject(
                "SELECT business_key FROM " + TABLE + " WHERE ts_code = 'NULL.SZ'", String.class);
        jdbc.update("UPDATE " + TABLE + " SET div_proc = 'null', business_key = NULL WHERE ts_code = 'NULL.SZ'");
        jdbc.execute(statements.get(1));
        String textKey = jdbc.queryForObject(
                "SELECT business_key FROM " + TABLE + " WHERE ts_code = 'NULL.SZ'", String.class);
        FingerprintKeyCodec codec = new FingerprintKeyCodec();
        Map<String, Object> identity = linkedRow(
                "ts_code", "NULL.SZ", "end_date", LocalDate.of(2024, 2, 29),
                "ann_date", LocalDate.of(2024, 3, 1), "div_proc", null);
        assertThat(nullKey).isEqualTo(codec.sha256(
                List.of("ts_code", "end_date", "ann_date", "div_proc"), identity));
        identity.put("div_proc", "null");
        assertThat(textKey).isEqualTo(codec.sha256(
                List.of("ts_code", "end_date", "ann_date", "div_proc"), identity)).isNotEqualTo(nullKey);
        jdbc.update("UPDATE " + TABLE + " SET business_key = ?", "0".repeat(64));

        assertThatThrownBy(() -> jdbc.execute(statements.get(2))).isInstanceOf(DataAccessException.class);

        assertThat(primaryKey(jdbc)).containsExactly("ts_code", "end_date", "ann_date");
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'business_key'
                """, String.class, TABLE)).isEqualTo("YES");
        assertThat(indexes(jdbc)).doesNotContainKey("idx_dividend_ts_code");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class)).isEqualTo(3);
    }

    private static Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
    }

    private static DatasetDefinition dividendDefinition() {
        return new DatasetDefinitionLoader()
                .loadAll(new org.springframework.core.io.support.PathMatchingResourcePatternResolver(),
                        "classpath*:datasets/tushare_pro/*.yaml")
                .stream()
                .filter(definition -> definition.datasetKey().apiName().value().equals("dividend"))
                .findFirst()
                .orElseThrow();
    }

    @SafeVarargs
    private static AdaptedBatch adapt(
            DatasetDefinition definition, Instant ingestedAt, List<Object>... rows) {
        List<String> fields = definition.columns().stream().map(ColumnDefinition::name).toList();
        DownloadEnvelope envelope = new DownloadEnvelope(
                definition.datasetKey().pluginId(), definition.datasetKey().apiName(), Map.of(), fields,
                rows.length, List.of(rows), DownloadStatus.SUCCESS, null);
        return new GenericDatasetAdapter(definition, new ValueConverter(), new FingerprintKeyCodec())
                .adapt(envelope, ingestedAt);
    }

    private static List<Object> dividendRow(String progress, String cashDividend) {
        return Arrays.asList(
                "000001.SZ", "20251231", "20260301", progress,
                null, null, null, new BigDecimal(cashDividend), new BigDecimal(cashDividend),
                null, null, null, null, null);
    }

    private static void insertLegacyRows(JdbcTemplate jdbc) {
        insertLegacyRow(jdbc, new DividendRow(
                "中文.SH", LocalDate.of(2025, 12, 31), LocalDate.of(2026, 3, 1), "实施中",
                new BigDecimal("0.100000000000000000"), null, new BigDecimal("0.020000000000000000"),
                new BigDecimal("0.110000000000000000"), new BigDecimal("0.120000000000000000"),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 12),
                LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 5),
                "legacy-plugin", "dividend", Instant.parse("2026-09-05T01:02:03.456Z")));
        insertLegacyRow(jdbc, new DividendRow(
                "NULL.SZ", LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 1), null,
                null, null, null, new BigDecimal("0.010000000000000000"), null,
                null, null, null, null, null,
                "legacy-plugin", "dividend", Instant.parse("2026-09-05T02:03:04.567Z")));
        insertLegacyRow(jdbc, new DividendRow(
                "TEXT.SZ", LocalDate.of(1000, 1, 1), LocalDate.of(1000, 1, 2), "null",
                null, null, null, null, null, null, null, null, null, null,
                "legacy-plugin", "dividend", Instant.parse("2026-09-05T03:04:05.678Z")));
    }

    private static void insertLegacyRow(JdbcTemplate jdbc, DividendRow row) {
        jdbc.update("""
                INSERT INTO tushare_pro__dividend
                    (ts_code, end_date, ann_date, div_proc, stk_div, stk_bo_rate, stk_co_rate,
                     cash_div, cash_div_tax, record_date, ex_date, pay_date, div_listdate, imp_ann_date,
                     source_plugin, source_api, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.tsCode(), row.endDate(), row.annDate(), row.divProc(), row.stkDiv(), row.stkBoRate(),
                row.stkCoRate(), row.cashDiv(), row.cashDivTax(), row.recordDate(), row.exDate(), row.payDate(),
                row.divListDate(), row.impAnnDate(), row.sourcePlugin(), row.sourceApi(), Timestamp.from(row.ingestedAt()));
    }

    private static List<DividendRow> dividendRows(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT ts_code, end_date, ann_date, div_proc, stk_div, stk_bo_rate, stk_co_rate,
                       cash_div, cash_div_tax, record_date, ex_date, pay_date, div_listdate, imp_ann_date,
                       source_plugin, source_api, ingested_at
                FROM tushare_pro__dividend ORDER BY ts_code
                """, (result, row) -> new DividendRow(
                result.getString("ts_code"), date(result, "end_date"), date(result, "ann_date"),
                result.getString("div_proc"), result.getBigDecimal("stk_div"),
                result.getBigDecimal("stk_bo_rate"), result.getBigDecimal("stk_co_rate"),
                result.getBigDecimal("cash_div"), result.getBigDecimal("cash_div_tax"),
                date(result, "record_date"), date(result, "ex_date"), date(result, "pay_date"),
                date(result, "div_listdate"), date(result, "imp_ann_date"),
                result.getString("source_plugin"), result.getString("source_api"),
                result.getTimestamp("ingested_at").toInstant()));
    }

    private static LocalDate date(ResultSet result, String column) throws SQLException {
        Date value = result.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static List<String> primaryKey(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT column_name FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = 'PRIMARY'
                ORDER BY seq_in_index
                """, (result, row) -> result.getString(1), TABLE);
    }

    private static Map<String, List<String>> indexes(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT index_name, column_name FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name <> 'PRIMARY'
                ORDER BY index_name, seq_in_index
                """, result -> {
                    Map<String, List<String>> values = new LinkedHashMap<>();
                    while (result.next()) {
                        values.computeIfAbsent(result.getString(1), ignored -> new ArrayList<>())
                                .add(result.getString(2));
                    }
                    return values;
                }, TABLE);
    }

    private static List<String> v7Statements() throws IOException {
        try (InputStream input = DividendBusinessKeyMigrationIT.class.getClassLoader().getResourceAsStream(V7)) {
            assertThat(input).as(V7).isNotNull();
            return Arrays.stream(new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";"))
                    .map(String::trim)
                    .filter(statement -> !statement.isEmpty())
                    .toList();
        }
    }

    private static Map<String, Object> linkedRow(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private record DividendRow(
            String tsCode,
            LocalDate endDate,
            LocalDate annDate,
            String divProc,
            BigDecimal stkDiv,
            BigDecimal stkBoRate,
            BigDecimal stkCoRate,
            BigDecimal cashDiv,
            BigDecimal cashDivTax,
            LocalDate recordDate,
            LocalDate exDate,
            LocalDate payDate,
            LocalDate divListDate,
            LocalDate impAnnDate,
            String sourcePlugin,
            String sourceApi,
            Instant ingestedAt) {
    }
}
