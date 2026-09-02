package com.akkc.tensor.core.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class DatasetQueryServiceIT {
    private static final String COMPOSITE_TABLE = "m06__composite_query";
    private static final String FINGERPRINT_TABLE = "m06__fingerprint_query";
    private static final String WIDE_TABLE = "m06__wide_query";
    private static final Instant INGESTED_AT = Instant.parse("2026-09-03T01:02:03.456Z");
    private static final List<String> COMPOSITE_COLUMNS = List.of(
            "ts_code", "trade_date", "ann_date", "amount", "shares", "note", "trade_month", "status",
            "source_plugin", "source_api", "ingested_at");

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
                    .withEnv("TZ", "UTC");

    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void createTables() {
        dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                MYSQL.getUsername(),
                MYSQL.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE m06__composite_query (
                    ts_code VARCHAR(64) NOT NULL,
                    trade_date DATE NOT NULL,
                    ann_date DATE NULL,
                    amount DECIMAL(38,18) NULL,
                    shares BIGINT NULL,
                    note TEXT NULL,
                    trade_month CHAR(6) NULL,
                    status CHAR(1) NULL,
                    source_plugin VARCHAR(64) NOT NULL,
                    source_api VARCHAR(64) NOT NULL,
                    ingested_at DATETIME(3) NOT NULL,
                    PRIMARY KEY (ts_code, trade_date)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE m06__fingerprint_query (
                    identity_value VARCHAR(64) NOT NULL,
                    amount DECIMAL(38,18) NULL,
                    marker VARCHAR(64) NULL,
                    business_key CHAR(64) NOT NULL,
                    source_plugin VARCHAR(64) NOT NULL,
                    source_api VARCHAR(64) NOT NULL,
                    ingested_at DATETIME(3) NOT NULL,
                    PRIMARY KEY (business_key)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute(wideTableSql());
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update("DELETE FROM " + COMPOSITE_TABLE);
        jdbcTemplate.update("DELETE FROM " + FINGERPRINT_TABLE);
        jdbcTemplate.update("DELETE FROM " + WIDE_TABLE);
    }

    @Test
    void enforcesExactPublicContractsPageInvariantsAndUnknownDatasetBoundary() throws Exception {
        assertThat(DatasetPage.class.isRecord()).isTrue();
        assertThat(Arrays.stream(DatasetPage.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("columns", "items", "page", "pageSize", "totalElements", "totalPages");
        assertThat(Arrays.stream(DatasetPage.class.getRecordComponents()).map(component -> component.getType()).toArray())
                .containsExactly(List.class, List.class, int.class, int.class, long.class, long.class);
        assertThat(Modifier.isFinal(GenericQueryRepository.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(DatasetQueryService.class.getModifiers())).isTrue();
        assertThat(GenericQueryRepository.class.getConstructors()).containsExactly(
                GenericQueryRepository.class.getConstructor(JdbcTemplate.class));
        assertThat(DatasetQueryService.class.getConstructors()).containsExactly(
                DatasetQueryService.class.getConstructor(DatasetCatalog.class, GenericQueryRepository.class));
        assertThat(publicDeclaredMethods(GenericQueryRepository.class)).containsExactlyInAnyOrder(
                GenericQueryRepository.class.getDeclaredMethod("count", QuerySql.class),
                GenericQueryRepository.class.getDeclaredMethod("query", DatasetDefinition.class, QuerySql.class));
        assertThat(publicDeclaredMethods(DatasetQueryService.class)).containsExactly(
                DatasetQueryService.class.getDeclaredMethod("query", DatasetKey.class, QueryCriteria.class));

        RejectingDataSource rejecting = new RejectingDataSource();
        JdbcTemplate rejectingJdbc = new JdbcTemplate(rejecting);
        assertThatNullPointerException().isThrownBy(() -> new GenericQueryRepository(null))
                .withMessage("jdbcTemplate");
        assertThatNullPointerException().isThrownBy(() -> new DatasetQueryService(null, new GenericQueryRepository(rejectingJdbc)))
                .withMessage("datasetCatalog");
        assertThatNullPointerException().isThrownBy(() -> new DatasetQueryService(catalog(), null))
                .withMessage("repository");

        DatasetQueryService service = new DatasetQueryService(catalog(), new GenericQueryRepository(rejectingJdbc));
        QueryCriteria criteria = criteria(null, null, null, null, null, 1, 20);
        assertThatNullPointerException().isThrownBy(() -> service.query(null, criteria)).withMessage("key");
        assertThatNullPointerException().isThrownBy(() -> service.query(compositeDefinition().datasetKey(), null))
                .withMessage("criteria");
        DatasetKey unknown = new DatasetKey(new PluginId("m06"), new ApiName("unknown_query"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.query(unknown, criteria))
                .withMessage("Dataset is not available");
        assertThat(rejecting.attempts()).isZero();

        List<String> mutableColumns = new ArrayList<>(List.of("value"));
        LinkedHashMap<String, Object> mutableRow = new LinkedHashMap<>();
        mutableRow.put("value", null);
        List<Map<String, Object>> mutableItems = new ArrayList<>(List.of(mutableRow));
        DatasetPage page = new DatasetPage(mutableColumns, mutableItems, 1, 20, 1, 1);
        mutableColumns.set(0, "changed");
        mutableRow.put("value", "changed");
        mutableItems.clear();
        assertThat(page.columns()).containsExactly("value");
        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(row).containsOnlyKeys("value");
            assertThat(row.get("value")).isNull();
        });
        assertThatThrownBy(() -> page.columns().add("no")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> page.items().add(Map.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> page.items().getFirst().put("value", "no"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatNullPointerException().isThrownBy(() -> new DatasetPage(null, List.of(), 1, 20, 0, 0))
                .withMessage("columns");
        assertThatNullPointerException().isThrownBy(() -> new DatasetPage(List.of("value"), null, 1, 20, 0, 0))
                .withMessage("items");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(List.of(), List.of(), 1, 20, 0, 0))
                .withMessage("columns must not be empty");
        assertThatNullPointerException().isThrownBy(() -> new DatasetPage(
                        Arrays.asList("value", null), List.of(), 1, 20, 0, 0))
                .withMessage("columns must not contain null");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(
                        List.of("value", "value"), List.of(), 1, 20, 0, 0))
                .withMessage("columns must not contain duplicates");
        assertThatNullPointerException().isThrownBy(() -> new DatasetPage(
                        List.of("value"), Arrays.asList((Map<String, Object>) null), 1, 20, 0, 0))
                .withMessage("items must not contain null");
        LinkedHashMap<String, Object> wrongOrder = new LinkedHashMap<>();
        wrongOrder.put("second", 2);
        wrongOrder.put("first", 1);
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(
                        List.of("first", "second"), List.of(wrongOrder), 1, 20, 1, 1))
                .withMessage("row keys must exactly match columns in order");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(List.of("value"), List.of(), 0, 20, 0, 0))
                .withMessage("page must be at least 1");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(List.of("value"), List.of(), 1, 21, 0, 0))
                .withMessage("pageSize must be one of 20, 50, 100");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(List.of("value"), List.of(), 1, 20, -1, 0))
                .withMessage("totals must be non-negative");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(List.of("value"), List.of(), 1, 20, 21, 1))
                .withMessage("totalPages must match totalElements and pageSize");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(List.of("value"), List.of(Map.of("value", 1)), 1, 20, 0, 0))
                .withMessage("empty pages must use page 1 and no items");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(List.of("value"), List.of(), 2, 20, 0, 0))
                .withMessage("empty pages must use page 1 and no items");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(List.of("value"), List.of(), 2, 20, 1, 1))
                .withMessage("page must not exceed totalPages");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetPage(
                        List.of("value"), List.of(Map.of("value", 1), Map.of("value", 2)), 1, 20, 1, 1))
                .withMessage("items must not exceed pageSize or totalElements");
    }

    @Test
    void returnsCanonicalEmptyPageAfterCountWithoutPreparingPageSql() {
        RecordingDataSource recording = new RecordingDataSource(dataSource);
        DatasetPage page = service(recording).query(
                compositeDefinition().datasetKey(), criteria(null, null, null, null, null, 99, 50));

        assertThat(page.columns()).containsExactlyElementsOf(COMPOSITE_COLUMNS);
        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(50);
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(recording.calls()).singleElement().satisfies(call -> {
            assertThat(call.sql()).isEqualTo("SELECT COUNT(*) FROM `m06__composite_query`");
            assertThat(call.bindings()).isEmpty();
        });
    }

    @Test
    void pagesCompositeRowsInStableOrderAndPreservesEveryPreciseJdbcType() {
        for (int index = 1; index <= 25; index++) {
            insertComposite(
                    "000001.SZ",
                    LocalDate.of(2026, 1, index),
                    LocalDate.of(2025, 12, index),
                    index == 21 ? "12345678901234567890.123456789012345678" : Integer.toString(index),
                    index == 21 ? 9_007_199_254_740_993L : index,
                    "note-" + index);
        }

        DatasetPage page = service(dataSource).query(
                compositeDefinition().datasetKey(), criteria(null, null, null, null, null, 2, 20));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).hasSize(5);
        assertThat(page.items()).extracting(row -> row.get("trade_date")).containsExactly(
                LocalDate.of(2026, 1, 21), LocalDate.of(2026, 1, 22), LocalDate.of(2026, 1, 23),
                LocalDate.of(2026, 1, 24), LocalDate.of(2026, 1, 25));
        Map<String, Object> first = page.items().getFirst();
        assertThat(first.keySet()).containsExactlyElementsOf(COMPOSITE_COLUMNS);
        assertThat(first.get("amount")).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) first.get("amount"))
                .isEqualByComparingTo("12345678901234567890.123456789012345678");
        assertThat(first.get("shares")).isEqualTo(9_007_199_254_740_993L).isInstanceOf(Long.class);
        assertThat(first.get("trade_date")).isInstanceOf(LocalDate.class);
        assertThat(first.get("ann_date")).isInstanceOf(LocalDate.class);
        assertThat(first.get("ingested_at")).isEqualTo(INGESTED_AT).isInstanceOf(Instant.class);
        assertThat(first).containsEntry("note", "note-21")
                .containsEntry("trade_month", "202601")
                .containsEntry("status", "A");
    }

    @Test
    void combinesDeclaredFiltersAndBindsCountAndPageValuesWithoutInterpolation() {
        insertComposite("000001.SZ", LocalDate.of(2026, 1, 2), LocalDate.of(2025, 12, 2), "1", 1, "match");
        insertComposite("000001.SZ", LocalDate.of(2026, 1, 5), LocalDate.of(2025, 12, 2), "2", 2, "late");
        insertComposite("000002.SZ", LocalDate.of(2026, 1, 2), LocalDate.of(2025, 12, 2), "3", 3, "other");
        RecordingDataSource recording = new RecordingDataSource(dataSource);

        DatasetPage page = service(recording).query(
                compositeDefinition().datasetKey(),
                criteria(
                        "000001.SZ",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 3),
                        LocalDate.of(2025, 12, 1),
                        LocalDate.of(2025, 12, 3),
                        1,
                        20));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(row -> assertThat(row).containsEntry("note", "match"));
        assertThat(recording.calls()).hasSize(2);
        assertThat(recording.calls().get(0).sql())
                .doesNotContain("000001.SZ", "2026-01-01", "2026-01-03", "2025-12-01", "2025-12-03")
                .contains("WHERE `ts_code` = ? AND `trade_date` BETWEEN ? AND ? AND `ann_date` BETWEEN ? AND ?");
        assertThat(recording.calls().get(0).bindings()).containsExactly(
                "000001.SZ", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3),
                LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 3));
        assertThat(recording.calls().get(1).sql())
                .doesNotContain("000001.SZ", "2026-01-01", "2026-01-03", "2025-12-01", "2025-12-03")
                .endsWith("LIMIT ? OFFSET ?");
        assertThat(recording.calls().get(1).bindings()).containsExactly(
                "000001.SZ", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3),
                LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 3), 20, 0L);
    }

    @Test
    void rebuildsAndExecutesOnlyTheCanonicalLastPageForOutOfRangeRequests() {
        for (int index = 1; index <= 23; index++) {
            insertComposite(
                    "000001.SZ", LocalDate.of(2026, 2, index), LocalDate.of(2026, 1, index),
                    Integer.toString(index), index, "row-" + index);
        }
        RecordingDataSource recording = new RecordingDataSource(dataSource);

        DatasetPage page = service(recording).query(
                compositeDefinition().datasetKey(), criteria(null, null, null, null, null, 99, 20));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(23);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).hasSize(3);
        assertThat(page.items()).extracting(row -> row.get("note"))
                .containsExactly("row-21", "row-22", "row-23");
        assertThat(recording.calls()).hasSize(2);
        assertThat(recording.calls().get(1).bindings()).containsExactly(20, 20L);
        assertThat(recording.calls().get(1).bindings()).doesNotContain(1960L);
    }

    @Test
    void ordersFingerprintRowsByIdentityThenInternalKeyWithoutExposingTheKey() {
        insertFingerprint("same", "2", "second", "b".repeat(64));
        insertFingerprint("same", "1", "first", "a".repeat(64));

        DatasetPage page = service(dataSource).query(
                fingerprintDefinition().datasetKey(), criteria(null, null, null, null, null, 1, 20));

        assertThat(page.columns()).containsExactly(
                "identity_value", "amount", "marker", "source_plugin", "source_api", "ingested_at");
        assertThat(page.items()).extracting(row -> row.get("marker")).containsExactly("first", "second");
        assertThat(page.items()).allSatisfy(row -> {
            assertThat(row.keySet()).containsExactlyElementsOf(page.columns());
            assertThat(row).doesNotContainKey("business_key");
        });
    }

    @Test
    void returnsAllOneHundredFiftyTwoBusinessColumnsInOrderWithoutPrecisionLoss() {
        jdbcTemplate.update("""
                INSERT INTO m06__wide_query
                    (wide_000, wide_001, wide_151, source_plugin, source_api, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "row-key",
                new BigDecimal("12345678901234567890.123456789012345678"),
                new BigDecimal("99999999999999999999.999999999999999999"),
                "m06",
                "wide_query",
                Timestamp.from(INGESTED_AT));

        DatasetPage page = service(dataSource).query(
                wideDefinition().datasetKey(), criteria(null, null, null, null, null, 1, 20));

        assertThat(page.columns()).hasSize(155);
        assertThat(page.columns().getFirst()).isEqualTo("wide_000");
        assertThat(page.columns().get(151)).isEqualTo("wide_151");
        assertThat(page.columns().subList(152, 155))
                .containsExactly("source_plugin", "source_api", "ingested_at");
        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(row).hasSize(155);
            assertThat(row.keySet()).containsExactlyElementsOf(page.columns());
            assertThat((BigDecimal) row.get("wide_001"))
                    .isEqualByComparingTo("12345678901234567890.123456789012345678");
            assertThat((BigDecimal) row.get("wide_151"))
                    .isEqualByComparingTo("99999999999999999999.999999999999999999");
            assertThat(row.get("wide_002")).isNull();
            assertThat(row.get("wide_001")).isInstanceOf(BigDecimal.class);
            assertThat(row.get("ingested_at")).isEqualTo(INGESTED_AT);
        });
    }

    @Test
    void propagatesJdbcFailuresRejectsUnsupportedValuesAndRejectsMalformedCounts() {
        GenericQueryRepository repository = new GenericQueryRepository(jdbcTemplate);
        QuerySql missingCount = new QuerySql(
                "SELECT missing FROM m06__composite_query", List.of(), "SELECT 1", List.of());
        assertThatThrownBy(() -> repository.count(missingCount)).isInstanceOf(DataAccessException.class);

        QuerySql missingPage = new QuerySql(
                "SELECT 1", List.of(), "SELECT missing FROM m06__composite_query", List.of());
        assertThatThrownBy(() -> repository.query(compositeDefinition(), missingPage))
                .isInstanceOf(DataAccessException.class);

        GenericQueryRepository connectionFailure = new GenericQueryRepository(
                new JdbcTemplate(new FailingConnectionDataSource()));
        assertThatThrownBy(() -> connectionFailure.count(new QuerySql("SELECT 1", List.of(), "SELECT 1", List.of())))
                .isInstanceOf(DataAccessException.class);

        GenericQueryRepository bindingFailure = new GenericQueryRepository(
                new JdbcTemplate(new FailingSetterDataSource(dataSource)));
        assertThatThrownBy(() -> bindingFailure.count(new QuerySql(
                        "SELECT COUNT(*) FROM m06__composite_query WHERE ts_code = ?",
                        List.of("000001.SZ"),
                        "SELECT 1",
                        List.of())))
                .isInstanceOf(DataAccessException.class);

        QuerySql invalidRead = new QuerySql(
                "SELECT 1",
                List.of(),
                "SELECT 'not-a-date', 'm06', 'read_failure', CURRENT_TIMESTAMP",
                List.of());
        assertThatThrownBy(() -> repository.query(dateOnlyDefinition(), invalidRead))
                .isInstanceOf(DataAccessException.class);

        RejectingDataSource rejecting = new RejectingDataSource();
        GenericQueryRepository rejectingRepository = new GenericQueryRepository(new JdbcTemplate(rejecting));
        assertThatIllegalArgumentException().isThrownBy(() -> rejectingRepository.count(new QuerySql(
                        "SELECT ?", List.of(BigDecimal.ONE), "SELECT 1", List.of())))
                .withMessage("Unsupported query value type");
        assertThat(rejecting.attempts()).isZero();

        assertThatNullPointerException().isThrownBy(() -> repository.count(null)).withMessage("querySql");
        assertThatNullPointerException().isThrownBy(() -> repository.query(null, missingPage)).withMessage("definition");
        assertThatNullPointerException().isThrownBy(() -> repository.query(compositeDefinition(), null)).withMessage("querySql");
        for (String sql : List.of("SELECT NULL", "SELECT -1", "SELECT 1 UNION ALL SELECT 2")) {
            assertThatIllegalStateException().isThrownBy(() -> repository.count(
                            new QuerySql(sql, List.of(), "SELECT 1", List.of())))
                    .withMessage("Count query returned an invalid result");
        }
    }

    private static DatasetQueryService service(DataSource repositoryDataSource) {
        return new DatasetQueryService(
                catalog(), new GenericQueryRepository(new JdbcTemplate(repositoryDataSource)));
    }

    private static DatasetCatalog catalog() {
        return new DatasetStartupValidator(
                List.of(compositeDefinition(), fingerprintDefinition(), wideDefinition()),
                new SchemaInspector(dataSource))
                .validate();
    }

    private static DatasetDefinition compositeDefinition() {
        return definition(
                "composite_query",
                List.of(
                        column("ts_code", LogicalType.STRING, false, 0),
                        column("trade_date", LogicalType.DATE, false, 1),
                        column("ann_date", LogicalType.DATE, true, 2),
                        column("amount", LogicalType.DECIMAL, true, 3),
                        column("shares", LogicalType.LONG, true, 4),
                        column("note", LogicalType.TEXT, true, 5),
                        column("trade_month", LogicalType.MONTH, true, 6),
                        column("status", LogicalType.ENUM, true, 7)),
                BusinessKeyMode.COMPOSITE,
                List.of("ts_code", "trade_date"),
                List.of("ts_code", "trade_date", "ann_date"));
    }

    private static DatasetDefinition fingerprintDefinition() {
        return definition(
                "fingerprint_query",
                List.of(
                        column("identity_value", LogicalType.STRING, false, 0),
                        column("amount", LogicalType.DECIMAL, true, 1),
                        column("marker", LogicalType.STRING, true, 2)),
                BusinessKeyMode.FINGERPRINT,
                List.of("identity_value"),
                List.of());
    }

    private static DatasetDefinition wideDefinition() {
        List<ColumnDefinition> columns = new ArrayList<>();
        columns.add(column("wide_000", LogicalType.STRING, false, 0));
        for (int index = 1; index < 152; index++) {
            columns.add(column("wide_%03d".formatted(index), LogicalType.DECIMAL, true, index));
        }
        return definition(
                "wide_query", columns, BusinessKeyMode.COMPOSITE, List.of("wide_000"), List.of());
    }

    private static DatasetDefinition dateOnlyDefinition() {
        return definition(
                "composite_query",
                List.of(column("trade_date", LogicalType.DATE, false, 0)),
                BusinessKeyMode.COMPOSITE,
                List.of("trade_date"),
                List.of());
    }

    private static DatasetDefinition definition(
            String api,
            List<ColumnDefinition> columns,
            BusinessKeyMode mode,
            List<String> keyFields,
            List<String> filters) {
        DatasetKey key = new DatasetKey(new PluginId("m06"), new ApiName(api));
        return new DatasetDefinition(
                key,
                "M06 query test",
                "test",
                QueryMode.trade_date,
                List.of(),
                TableName.from(key),
                columns,
                new BusinessKeyDefinition(mode, keyFields),
                filters.stream().map(FilterDefinition::new).toList(),
                null,
                500);
    }

    private static ColumnDefinition column(
            String name, LogicalType type, boolean nullable, int order) {
        Integer length = null;
        Integer precision = null;
        Integer scale = null;
        if (type == LogicalType.STRING) {
            length = 64;
        } else if (type == LogicalType.ENUM) {
            length = 1;
        } else if (type == LogicalType.DECIMAL) {
            precision = 38;
            scale = 18;
        }
        return new ColumnDefinition(
                name,
                name,
                type,
                nullable,
                order,
                length,
                precision,
                scale,
                type == LogicalType.ENUM ? List.of("A", "B") : List.of(),
                type == LogicalType.TEXT);
    }

    private static QueryCriteria criteria(
            String tsCode,
            LocalDate tradeDateFrom,
            LocalDate tradeDateTo,
            LocalDate annDateFrom,
            LocalDate annDateTo,
            int page,
            int pageSize) {
        return new QueryCriteria(
                tsCode, tradeDateFrom, tradeDateTo, annDateFrom, annDateTo, page, pageSize);
    }

    private static void insertComposite(
            String tsCode,
            LocalDate tradeDate,
            LocalDate annDate,
            String amount,
            long shares,
            String note) {
        jdbcTemplate.update("""
                INSERT INTO m06__composite_query
                    (ts_code, trade_date, ann_date, amount, shares, note, trade_month, status,
                     source_plugin, source_api, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tsCode,
                tradeDate,
                annDate,
                new BigDecimal(amount),
                shares,
                note,
                "%04d%02d".formatted(tradeDate.getYear(), tradeDate.getMonthValue()),
                "A",
                "m06",
                "composite_query",
                Timestamp.from(INGESTED_AT));
    }

    private static void insertFingerprint(
            String identity, String amount, String marker, String businessKey) {
        jdbcTemplate.update("""
                INSERT INTO m06__fingerprint_query
                    (identity_value, amount, marker, business_key, source_plugin, source_api, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                identity,
                new BigDecimal(amount),
                marker,
                businessKey,
                "m06",
                "fingerprint_query",
                Timestamp.from(INGESTED_AT));
    }

    private static String wideTableSql() {
        StringBuilder sql = new StringBuilder("CREATE TABLE m06__wide_query (");
        sql.append("wide_000 VARCHAR(64) NOT NULL");
        for (int index = 1; index < 152; index++) {
            sql.append(", wide_%03d DECIMAL(38,18) NULL".formatted(index));
        }
        sql.append(", source_plugin VARCHAR(64) NOT NULL")
                .append(", source_api VARCHAR(64) NOT NULL")
                .append(", ingested_at DATETIME(3) NOT NULL")
                .append(", PRIMARY KEY (wide_000)) ENGINE=InnoDB");
        return sql.toString();
    }

    private static List<Method> publicDeclaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static final class RecordingDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final List<StatementCall> calls = new CopyOnWriteArrayList<>();

        private RecordingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        private List<StatementCall> calls() {
            return List.copyOf(calls);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return record(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return record(delegate.getConnection(username, password));
        }

        private Connection record(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    DatasetQueryServiceIT.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(connection, method, arguments);
                        if (method.getName().equals("prepareStatement")
                                && arguments != null
                                && arguments.length > 0
                                && arguments[0] instanceof String sql
                                && result instanceof PreparedStatement statement) {
                            StatementCall call = new StatementCall(sql);
                            calls.add(call);
                            return call.record(statement);
                        }
                        return result;
                    });
        }
    }

    private static final class StatementCall {
        private final String sql;
        private final Map<Integer, Object> bindings = new LinkedHashMap<>();

        private StatementCall(String sql) {
            this.sql = sql;
        }

        private String sql() {
            return sql;
        }

        private List<Object> bindings() {
            return List.copyOf(bindings.values());
        }

        private PreparedStatement record(PreparedStatement statement) {
            return (PreparedStatement) Proxy.newProxyInstance(
                    DatasetQueryServiceIT.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().startsWith("set")
                                && arguments != null
                                && arguments.length >= 2
                                && arguments[0] instanceof Integer index) {
                            Object value = arguments[1] instanceof Date date ? date.toLocalDate() : arguments[1];
                            bindings.put(index, value);
                        }
                        return invoke(statement, method, arguments);
                    });
        }
    }

    private static final class RejectingDataSource extends AbstractDataSource {
        private final AtomicInteger attempts = new AtomicInteger();

        private int attempts() {
            return attempts.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            attempts.incrementAndGet();
            throw new SQLException("connection must not be accessed");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }

    private static final class FailingConnectionDataSource extends AbstractDataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("connection failed");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }

    private static final class FailingSetterDataSource extends AbstractDataSource {
        private final DataSource delegate;

        private FailingSetterDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return failSetters(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return failSetters(delegate.getConnection(username, password));
        }

        private Connection failSetters(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    DatasetQueryServiceIT.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(connection, method, arguments);
                        if (method.getName().equals("prepareStatement")
                                && result instanceof PreparedStatement statement) {
                            return Proxy.newProxyInstance(
                                    DatasetQueryServiceIT.class.getClassLoader(),
                                    new Class<?>[] {PreparedStatement.class},
                                    (statementProxy, statementMethod, statementArguments) -> {
                                        if (statementMethod.getName().equals("setString")) {
                                            throw new SQLException("binding failed");
                                        }
                                        return invoke(statement, statementMethod, statementArguments);
                                    });
                        }
                        return result;
                    });
        }
    }
}
