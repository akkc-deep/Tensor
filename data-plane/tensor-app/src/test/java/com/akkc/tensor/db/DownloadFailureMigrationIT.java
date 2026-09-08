package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DownloadFailureMigrationIT {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs");
    private static final String LOCATION = "filesystem:src/main/resources/db/migration";

    @BeforeEach
    void clearIsolatedContainerSchema() {
        configure().cleanDisabled(false).load().clean();
    }

    @Test
    void productionFreshSchemaHasSevenMigrationsAndFiftyOneTablesWithoutFixture() {
        var flyway = configure().load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(7);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        var tables = jdbc().queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'
                """, String.class);
        assertThat(tables).hasSize(51).contains("tensor_download_task", "tensor_download_task_item")
                .doesNotContain("fixture__fixture_daily");
    }

    @Test
    void upgradesOnlyV8AndKeepsCompleteBusinessRowsAndHistoricalKeys() {
        assertThat(configure().target("7").load().migrate().migrationsExecuted).isEqualTo(6);
        var jdbc = jdbc();
        jdbc.update("""
                INSERT INTO tushare_pro__stock_basic
                    (ts_code, name, list_date, source_plugin, source_api, ingested_at)
                VALUES ('000001.SZ', '迁移哨兵', '2020-01-02', 'legacy', 'stock_basic', '2026-09-01 02:03:04.567')
                """);
        jdbc.update("""
                INSERT INTO tushare_pro__dividend
                    (business_key, ts_code, end_date, ann_date, div_proc, cash_div,
                     source_plugin, source_api, ingested_at)
                VALUES (?, '000001.SZ', '2025-12-31', '2026-03-01', '实施', 0.123456789012345678,
                        'legacy', 'dividend', '2026-09-01 03:04:05.678')
                """, "a".repeat(64));
        var stock = jdbc.queryForList("SELECT * FROM tushare_pro__stock_basic");
        var dividend = jdbc.queryForList("SELECT * FROM tushare_pro__dividend");
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        var flyway = configure().load();
        assertThat(flyway.migrate().migrationsExecuted).isOne();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM tushare_pro__stock_basic")).isEqualTo(stock);
        assertThat(jdbc.queryForList("SELECT * FROM tushare_pro__dividend")).isEqualTo(dividend);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version <> '8' ORDER BY installed_rank"))
                .isEqualTo(history);
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration configure() {
        return Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(LOCATION);
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }
}
