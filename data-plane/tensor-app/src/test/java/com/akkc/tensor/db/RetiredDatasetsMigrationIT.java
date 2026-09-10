package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class RetiredDatasetsMigrationIT {
    private static final List<String> RETIRED_TABLES = List.of(
            "tushare_pro__top_inst", "tushare_pro__broker_recommend", "tushare_pro__share_float",
            "tushare_pro__hs_const", "tushare_pro__moneyflow_hsgt", "tushare_pro__hk_hold",
            "tushare_pro__index_member", "tushare_pro__hsgt_top10", "tushare_pro__namechange");

    @Test
    void upgradesV7ByRemovingOnlyRetiredTablesAndPreservesRemainingRowsRepeatably() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
                .withDatabaseName("tensor").withUsername("tensor").withPassword("tensor")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs")) {
            mysql.start();
            var dataSource = new DriverManagerDataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway v7 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .target("7").load();
            assertThat(v7.migrate().migrationsExecuted).isEqualTo(7);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<String> oldTables = productionTables(jdbc);
            assertThat(oldTables).hasSize(49).containsAll(RETIRED_TABLES);
            jdbc.update("""
                    INSERT INTO tushare_pro__hs_const
                        (ts_code, hs_type, in_date, source_plugin, source_api, ingested_at)
                    VALUES ('000001.SZ', 'SH', '2026-09-01', 'tushare_pro', 'hs_const', '2026-09-01 01:02:03.456')
                    """);
            jdbc.update("""
                    INSERT INTO tushare_pro__daily
                        (ts_code, trade_date, close, source_plugin, source_api, ingested_at)
                    VALUES ('000001.SZ', '2026-09-01', 12.345, 'tushare_pro', 'daily', '2026-09-01 01:02:03.456')
                    """);
            jdbc.update("""
                    INSERT INTO tushare_pro__dividend
                        (ts_code, end_date, ann_date, div_proc, cash_div, business_key,
                         source_plugin, source_api, ingested_at)
                    VALUES ('000001.SZ', '2025-12-31', '2026-09-01', '实施', 0.125, ?,
                            'tushare_pro', 'dividend', '2026-09-01 01:02:03.456')
                    """, "a".repeat(64));
            jdbc.update("""
                    INSERT INTO tushare_pro__index_member_all
                        (l1_code, l2_code, l3_code, ts_code, in_date, source_plugin, source_api, ingested_at)
                    VALUES ('801010.SI', '801011.SI', '850111.SI', '000001.SZ', '2026-09-01',
                            'tushare_pro', 'index_member_all', '2026-09-01 01:02:03.456')
                    """);
            var dailyRows = jdbc.queryForList("SELECT * FROM tushare_pro__daily");
            var dividendRows = jdbc.queryForList("SELECT * FROM tushare_pro__dividend");
            var memberRows = jdbc.queryForList("SELECT * FROM tushare_pro__index_member_all");

            Flyway v8 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
            assertThat(v8.migrate().migrationsExecuted).isOne();
            assertThat(v8.info().current().getVersion().getVersion()).isEqualTo("8");
            assertThat(v8.validateWithResult().validationSuccessful).isTrue();
            assertThat(v8.migrate().migrationsExecuted).isZero();

            List<String> retainedTables = new ArrayList<>(oldTables);
            retainedTables.removeAll(RETIRED_TABLES);
            assertThat(productionTables(jdbc)).hasSize(40).containsExactlyElementsOf(retainedTables);
            assertThat(jdbc.queryForList("SELECT * FROM tushare_pro__daily")).isEqualTo(dailyRows);
            assertThat(jdbc.queryForList("SELECT * FROM tushare_pro__dividend")).isEqualTo(dividendRows);
            assertThat(jdbc.queryForList("SELECT * FROM tushare_pro__index_member_all")).isEqualTo(memberRows);
        }
    }

    private static List<String> productionTables(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND LEFT(table_name, 13) = 'tushare_pro__'
                ORDER BY table_name
                """, String.class);
    }
}
