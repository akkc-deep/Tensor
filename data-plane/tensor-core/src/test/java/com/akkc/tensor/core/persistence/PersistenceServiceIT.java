package com.akkc.tensor.core.persistence;

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
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PersistenceServiceIT {
    private static final String COMPOSITE_TABLE = "m06__composite_write";
    private static final String FINGERPRINT_TABLE = "m06__fingerprint_write";
    private static final String FAILURE_TRIGGER = "m06_composite_write_fail";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
                    .withCommand("--log-bin-trust-function-creators=1");

    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static DataSourceTransactionManager transactionManager;

    @BeforeAll
    static void createTables() {
        dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE m06__composite_write (
                    ts_code VARCHAR(64) NOT NULL,
                    trade_date DATE NOT NULL,
                    amount DECIMAL(38,18) NULL,
                    note VARCHAR(64) NULL,
                    source_plugin VARCHAR(64) NOT NULL,
                    source_api VARCHAR(64) NOT NULL,
                    ingested_at TIMESTAMP(3) NOT NULL,
                    PRIMARY KEY (ts_code, trade_date)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE m06__fingerprint_write (
                    identity_value VARCHAR(64) NOT NULL,
                    amount DECIMAL(38,18) NULL,
                    business_key CHAR(64) NOT NULL,
                    source_plugin VARCHAR(64) NOT NULL,
                    source_api VARCHAR(64) NOT NULL,
                    ingested_at TIMESTAMP(3) NOT NULL,
                    PRIMARY KEY (business_key)
                ) ENGINE=InnoDB
                """);
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FAILURE_TRIGGER);
        jdbcTemplate.update("DELETE FROM " + COMPOSITE_TABLE);
        jdbcTemplate.update("DELETE FROM " + FINGERPRINT_TABLE);
    }

    @Test
    void enforcesPublicSurfaceAndRejectsInvalidBatchesBeforeSideEffects() throws Exception {
        assertThat(Modifier.isFinal(GenericUpsertRepository.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(PersistenceService.class.getModifiers())).isTrue();
        assertThat(GenericUpsertRepository.class.getConstructors()).containsExactly(
                GenericUpsertRepository.class.getConstructor(JdbcTemplate.class));
        assertThat(PersistenceService.class.getConstructors()).containsExactly(
                PersistenceService.class.getConstructor(
                        DatasetCatalog.class,
                        DatasetLockManager.class,
                        ExistingKeyRepository.class,
                        GenericUpsertRepository.class,
                        PlatformTransactionManager.class));
        assertThat(publicDeclaredMethods(GenericUpsertRepository.class)).containsExactly(
                GenericUpsertRepository.class.getDeclaredMethod(
                        "upsert", DatasetDefinition.class, AdaptedBatch.class));
        assertThat(publicDeclaredMethods(PersistenceService.class)).containsExactly(
                PersistenceService.class.getDeclaredMethod("persist", AdaptedBatch.class));

        DatasetCatalog catalog = catalog();
        DatasetLockManager lockManager = new DatasetLockManager();
        RejectingDataSource rejectingDataSource = new RejectingDataSource();
        JdbcTemplate rejectingJdbc = new JdbcTemplate(rejectingDataSource);
        FailOnTransactionManager rejectingTransactions = new FailOnTransactionManager();
        ExistingKeyRepository existingKeys = new ExistingKeyRepository(rejectingJdbc);
        GenericUpsertRepository upserts = new GenericUpsertRepository(rejectingJdbc);

        assertThatNullPointerException().isThrownBy(() -> new GenericUpsertRepository(null))
                .withMessage("jdbcTemplate");
        assertThatNullPointerException().isThrownBy(() -> new PersistenceService(
                        null, lockManager, existingKeys, upserts, rejectingTransactions))
                .withMessage("datasetCatalog");
        assertThatNullPointerException().isThrownBy(() -> new PersistenceService(
                        catalog, null, existingKeys, upserts, rejectingTransactions))
                .withMessage("datasetLockManager");
        assertThatNullPointerException().isThrownBy(() -> new PersistenceService(
                        catalog, lockManager, null, upserts, rejectingTransactions))
                .withMessage("existingKeyRepository");
        assertThatNullPointerException().isThrownBy(() -> new PersistenceService(
                        catalog, lockManager, existingKeys, null, rejectingTransactions))
                .withMessage("genericUpsertRepository");
        assertThatNullPointerException().isThrownBy(() -> new PersistenceService(
                        catalog, lockManager, existingKeys, upserts, null))
                .withMessage("transactionManager");

        PersistenceService service = new PersistenceService(
                catalog, lockManager, existingKeys, upserts, rejectingTransactions);
        assertThatNullPointerException().isThrownBy(() -> service.persist(null)).withMessage("batch");
        assertThatNullPointerException().isThrownBy(() -> upserts.upsert(null, emptyBatch(compositeDefinition())))
                .withMessage("definition");
        assertThatNullPointerException().isThrownBy(() -> upserts.upsert(compositeDefinition(), null))
                .withMessage("batch");

        DatasetDefinition unknown = definition("unknown_write", BusinessKeyMode.COMPOSITE, 2);
        assertThatIllegalArgumentException().isThrownBy(() -> service.persist(batch(
                        unknown,
                        Instant.parse("2026-09-03T00:00:00Z"),
                        compositeRow("UNKNOWN", LocalDate.of(2026, 9, 3), "1.00", "unknown"))))
                .withMessage("Dataset is not available");

        DatasetDefinition composite = compositeDefinition();
        List<String> reorderedColumns = List.of("trade_date", "ts_code", "amount", "note");
        AdaptedBatch reordered = new AdaptedBatch(
                composite.datasetKey(),
                composite.tableName(),
                reorderedColumns,
                List.of(compositeRow("REORDER", LocalDate.of(2026, 9, 3), "2.00", "bad")),
                composite.businessKey(),
                Instant.parse("2026-09-03T00:01:00Z"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.persist(reordered))
                .withMessage("Adapted batch does not match dataset");

        AdaptedBatch wrongBusinessKey = new AdaptedBatch(
                composite.datasetKey(),
                composite.tableName(),
                columnNames(composite),
                List.of(compositeRow("KEY", LocalDate.of(2026, 9, 3), "3.00", "bad")),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code")),
                Instant.parse("2026-09-03T00:02:00Z"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.persist(wrongBusinessKey))
                .withMessage("Adapted batch does not match dataset");

        assertThat(service.persist(emptyBatch(composite))).isEqualTo(new WriteCounts(0, 0));
        assertThat(rejectingTransactions.attempts()).isZero();
        assertThat(rejectingDataSource.attempts()).isZero();
        assertThat(locks(lockManager)).isEmpty();

        AdaptedBatch nonEmpty = batch(
                composite,
                Instant.parse("2026-09-03T00:03:00Z"),
                compositeRow("DIRECT", LocalDate.of(2026, 9, 3), "4.00", "direct"));
        assertThatIllegalStateException().isThrownBy(() -> upserts.upsert(composite, nonEmpty))
                .withMessage("Upsert requires an active transaction");
        assertThat(rejectingDataSource.attempts()).isZero();

        assertThatIllegalStateException().isThrownBy(() -> service.persist(nonEmpty))
                .withMessage("transaction refused");
        assertThat(locks(lockManager)).isEmpty();

        NoSynchronizationTransactionManager noSynchronization = new NoSynchronizationTransactionManager();
        PersistenceService noSynchronizationService = new PersistenceService(
                catalog, lockManager, existingKeys, upserts, noSynchronization);
        assertThatIllegalStateException().isThrownBy(() -> noSynchronizationService.persist(nonEmpty))
                .withMessage("Transaction synchronization is not active");
        assertThat(locks(lockManager)).isEmpty();
        assertThat(rejectingDataSource.attempts()).isZero();
    }

    @Test
    void insertsCompositeRowsInMetadataSizedJdbcBatches() throws Exception {
        RecordingDataSource recording = new RecordingDataSource(dataSource);
        DatasetLockManager lockManager = new DatasetLockManager();
        PersistenceService service = service(
                recording, lockManager, new DataSourceTransactionManager(recording));
        Instant ingestedAt = Instant.parse("2026-09-03T01:02:03.456Z");

        WriteCounts counts = service.persist(batch(
                compositeDefinition(),
                ingestedAt,
                compositeRow("AAA", LocalDate.of(2026, 9, 1), "10.25", "first"),
                compositeRow("BBB", LocalDate.of(2026, 9, 2), "20.50", "second"),
                compositeRow("CCC", LocalDate.of(2026, 9, 3), "30.75", "third")));

        assertThat(counts).isEqualTo(new WriteCounts(3, 0));
        assertThat(recording.batchSizes()).containsExactly(2, 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + COMPOSITE_TABLE, Long.class)).isEqualTo(3);
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT amount, note, source_plugin, source_api FROM "
                        + COMPOSITE_TABLE + " WHERE ts_code = 'AAA'");
        assertThat((BigDecimal) stored.get("amount")).isEqualByComparingTo("10.25");
        assertThat(stored.get("note")).isEqualTo("first");
        assertThat(stored.get("source_plugin")).isEqualTo("m06");
        assertThat(stored.get("source_api")).isEqualTo("composite_write");
        assertThat(readInstants(
                "SELECT ingested_at FROM " + COMPOSITE_TABLE + " WHERE ts_code = 'AAA'"))
                .containsExactly(ingestedAt);
        assertThat(locks(lockManager)).isEmpty();
    }

    @Test
    void updatesExistingCompositeRowsInRequiredSixtySecondTransaction() {
        jdbcTemplate.update("""
                INSERT INTO m06__composite_write
                    (ts_code, trade_date, amount, note, source_plugin, source_api, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?)
                """,
                "AAA", LocalDate.of(2026, 9, 1), new BigDecimal("1.00"), "old-a",
                "old", "old", Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")),
                "BBB", LocalDate.of(2026, 9, 2), new BigDecimal("2.00"), "old-b",
                "old", "old", Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")));
        RecordingTransactionManager recording = new RecordingTransactionManager(transactionManager);
        Instant ingestedAt = Instant.parse("2026-09-03T02:03:04.567Z");
        PersistenceService service = service(dataSource, new DatasetLockManager(), recording);

        WriteCounts counts = service.persist(batch(
                compositeDefinition(),
                ingestedAt,
                compositeRow("AAA", LocalDate.of(2026, 9, 1), "11.00", "new-a"),
                compositeRow("BBB", LocalDate.of(2026, 9, 2), "22.00", "new-b")));

        assertThat(counts).isEqualTo(new WriteCounts(0, 2));
        assertThat(recording.definitions()).containsExactly(
                new TransactionSnapshot(TransactionDefinition.PROPAGATION_REQUIRED, 60));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ts_code, amount, note, source_plugin, source_api FROM "
                        + COMPOSITE_TABLE + " ORDER BY ts_code");
        assertThat(rows).extracting(row -> row.get("ts_code")).containsExactly("AAA", "BBB");
        assertThat((BigDecimal) rows.get(0).get("amount")).isEqualByComparingTo("11.00");
        assertThat((BigDecimal) rows.get(1).get("amount")).isEqualByComparingTo("22.00");
        assertThat(rows).extracting(row -> row.get("note")).containsExactly("new-a", "new-b");
        assertThat(rows).extracting(row -> row.get("source_plugin")).containsOnly("m06");
        assertThat(rows).extracting(row -> row.get("source_api")).containsOnly("composite_write");
        assertThat(readInstants(
                "SELECT ingested_at FROM " + COMPOSITE_TABLE + " ORDER BY ts_code"))
                .containsOnly(ingestedAt);
    }

    @Test
    void countsMixedCompositeRowsFromPreflightMembership() {
        jdbcTemplate.update("""
                INSERT INTO m06__composite_write
                    (ts_code, trade_date, amount, note, source_plugin, source_api, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "AAA", LocalDate.of(2026, 9, 1), new BigDecimal("1.00"), "old",
                "old", "old", Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")));
        PersistenceService service = service(dataSource, new DatasetLockManager(), transactionManager);

        WriteCounts counts = service.persist(batch(
                compositeDefinition(),
                Instant.parse("2026-09-03T03:04:05.678Z"),
                compositeRow("AAA", LocalDate.of(2026, 9, 1), "9.00", "updated"),
                compositeRow("BBB", LocalDate.of(2026, 9, 2), "8.00", "inserted")));

        assertThat(counts).isEqualTo(new WriteCounts(1, 1));
        assertThat(Math.addExact(counts.insertedRows(), counts.updatedRows())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + COMPOSITE_TABLE, Long.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT note FROM " + COMPOSITE_TABLE + " ORDER BY ts_code", String.class))
                .containsExactly("updated", "inserted");
    }

    @Test
    void rollsBackEarlierJdbcBatchAndReleasesLockWhenLaterBatchFails() throws Exception {
        jdbcTemplate.execute("""
                CREATE TRIGGER m06_composite_write_fail
                BEFORE INSERT ON m06__composite_write
                FOR EACH ROW
                BEGIN
                    IF NEW.ts_code = 'FAIL' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced batch failure';
                    END IF;
                END
                """);
        RecordingDataSource recording = new RecordingDataSource(dataSource);
        DatasetLockManager lockManager = new DatasetLockManager();
        PersistenceService service = service(
                recording, lockManager, new DataSourceTransactionManager(recording));
        AdaptedBatch failing = batch(
                compositeDefinition(),
                Instant.parse("2026-09-03T04:05:06.789Z"),
                compositeRow("AAA", LocalDate.of(2026, 9, 1), "1.00", "first"),
                compositeRow("BBB", LocalDate.of(2026, 9, 2), "2.00", "second"),
                compositeRow("FAIL", LocalDate.of(2026, 9, 3), "3.00", "sentinel"));

        try {
            assertThatThrownBy(() -> service.persist(failing)).isInstanceOf(DataAccessException.class);
            assertThat(recording.batchSizes()).containsExactly(2, 1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + COMPOSITE_TABLE, Long.class)).isZero();
            assertThat(locks(lockManager)).isEmpty();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FAILURE_TRIGGER);
        }

        WriteCounts retry = service.persist(batch(
                compositeDefinition(),
                Instant.parse("2026-09-03T04:06:00Z"),
                compositeRow("RETRY", LocalDate.of(2026, 9, 4), "4.00", "retry")));
        assertThat(retry).isEqualTo(new WriteCounts(1, 0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + COMPOSITE_TABLE, Long.class)).isEqualTo(1);
        assertThat(locks(lockManager)).isEmpty();
    }

    @Test
    void retainsDatasetLockUntilJoinedOuterTransactionCompletes() throws Exception {
        DatasetLockManager lockManager = new DatasetLockManager();
        PersistenceService service = service(dataSource, lockManager, transactionManager);
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch secondStarted = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        Future<WriteCounts>[] secondResult = new Future[1];
        WriteCounts[] firstResult = new WriteCounts[1];

        try {
            outer.executeWithoutResult(status -> {
                firstResult[0] = service.persist(batch(
                        compositeDefinition(),
                        Instant.parse("2026-09-03T05:00:00Z"),
                        compositeRow("LOCK", LocalDate.of(2026, 9, 3), "1.00", "first")));
                secondResult[0] = executor.submit(() -> {
                    secondStarted.countDown();
                    return service.persist(batch(
                            compositeDefinition(),
                            Instant.parse("2026-09-03T05:01:00Z"),
                            compositeRow("LOCK", LocalDate.of(2026, 9, 3), "2.00", "second")));
                });
                try {
                    assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> secondResult[0].get(300, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
            });

            assertThat(firstResult[0]).isEqualTo(new WriteCounts(1, 0));
            assertThat(secondResult[0].get(5, TimeUnit.SECONDS)).isEqualTo(new WriteCounts(0, 1));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT note FROM " + COMPOSITE_TABLE + " WHERE ts_code = 'LOCK'", String.class))
                    .isEqualTo("second");
            assertThat(locks(lockManager)).isEmpty();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void keepsFingerprintWritesIdempotentWithoutRehashing() {
        PersistenceService service = service(dataSource, new DatasetLockManager(), transactionManager);
        String fingerprint = "c593b786a7708a9b7a106e244094f1cabd200caa3e95fad3b041225c17ac19ad";
        DatasetDefinition definition = fingerprintDefinition();

        WriteCounts first = service.persist(batch(
                definition,
                Instant.parse("2026-09-03T06:00:00Z"),
                fingerprintRow("identity", "1.00", fingerprint)));
        WriteCounts second = service.persist(batch(
                definition,
                Instant.parse("2026-09-03T06:01:00Z"),
                fingerprintRow("identity", "2.00", fingerprint)));

        assertThat(first).isEqualTo(new WriteCounts(1, 0));
        assertThat(second).isEqualTo(new WriteCounts(0, 1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + FINGERPRINT_TABLE, Long.class)).isEqualTo(1);
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT identity_value, amount, business_key, source_plugin, source_api FROM "
                        + FINGERPRINT_TABLE);
        assertThat(stored.get("identity_value")).isEqualTo("identity");
        assertThat((BigDecimal) stored.get("amount")).isEqualByComparingTo("2.00");
        assertThat(stored.get("business_key")).isEqualTo(fingerprint);
        assertThat(stored.get("source_plugin")).isEqualTo("m06");
        assertThat(stored.get("source_api")).isEqualTo("fingerprint_write");
    }

    @Test
    void bindsOneBatchIngestedAtAcrossAllJdbcBatches() {
        PersistenceService service = service(dataSource, new DatasetLockManager(), transactionManager);
        Instant ingestedAt = Instant.parse("2026-09-03T07:08:09.123Z")
                .truncatedTo(ChronoUnit.MILLIS);

        service.persist(batch(
                compositeDefinition(),
                ingestedAt,
                compositeRow("AAA", LocalDate.of(2026, 9, 1), "1.00", "first"),
                compositeRow("BBB", LocalDate.of(2026, 9, 2), "2.00", "second"),
                compositeRow("CCC", LocalDate.of(2026, 9, 3), "3.00", "third")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT ingested_at) FROM " + COMPOSITE_TABLE, Long.class)).isEqualTo(1);
        assertThat(readInstants(
                "SELECT ingested_at FROM " + COMPOSITE_TABLE + " ORDER BY ts_code"))
                .containsExactly(ingestedAt, ingestedAt, ingestedAt);
    }

    private static List<Instant> readInstants(String sql) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            return resultSet.getTimestamp(1, utc).toInstant();
        });
    }

    private static PersistenceService service(
            DataSource serviceDataSource,
            DatasetLockManager lockManager,
            PlatformTransactionManager serviceTransactionManager) {
        JdbcTemplate serviceJdbc = new JdbcTemplate(serviceDataSource);
        return new PersistenceService(
                catalog(),
                lockManager,
                new ExistingKeyRepository(serviceJdbc),
                new GenericUpsertRepository(serviceJdbc),
                serviceTransactionManager);
    }

    private static DatasetCatalog catalog() {
        return new DatasetStartupValidator(
                List.of(compositeDefinition(), fingerprintDefinition()),
                new SchemaInspector(dataSource))
                .validate();
    }

    private static DatasetDefinition compositeDefinition() {
        return definition("composite_write", BusinessKeyMode.COMPOSITE, 2);
    }

    private static DatasetDefinition fingerprintDefinition() {
        return definition("fingerprint_write", BusinessKeyMode.FINGERPRINT, 2);
    }

    private static DatasetDefinition definition(String apiName, BusinessKeyMode mode, int batchSize) {
        DatasetKey key = new DatasetKey(new PluginId("m06"), new ApiName(apiName));
        List<ColumnDefinition> columns;
        List<String> keyFields;
        if (mode == BusinessKeyMode.FINGERPRINT) {
            columns = List.of(
                    stringColumn("identity_value", false, 0),
                    decimalColumn("amount", true, 1));
            keyFields = List.of("identity_value");
        } else {
            columns = List.of(
                    stringColumn("ts_code", false, 0),
                    dateColumn("trade_date", false, 1),
                    decimalColumn("amount", true, 2),
                    stringColumn("note", true, 3));
            keyFields = List.of("ts_code", "trade_date");
        }
        return new DatasetDefinition(
                key,
                "M06 persistence test",
                "test",
                QueryMode.trade_date,
                List.of(),
                TableName.from(key),
                columns,
                new BusinessKeyDefinition(mode, keyFields),
                List.of(),
                null,
                batchSize);
    }

    private static ColumnDefinition stringColumn(
            String name, boolean nullable, int displayOrder) {
        return new ColumnDefinition(
                name,
                name,
                LogicalType.STRING,
                nullable,
                displayOrder,
                64,
                null,
                null,
                List.of(),
                false);
    }

    private static ColumnDefinition dateColumn(
            String name, boolean nullable, int displayOrder) {
        return new ColumnDefinition(
                name,
                name,
                LogicalType.DATE,
                nullable,
                displayOrder,
                null,
                null,
                null,
                List.of(),
                false);
    }

    private static ColumnDefinition decimalColumn(
            String name, boolean nullable, int displayOrder) {
        return new ColumnDefinition(
                name,
                name,
                LogicalType.DECIMAL,
                nullable,
                displayOrder,
                null,
                38,
                18,
                List.of(),
                false);
    }

    private static AdaptedBatch emptyBatch(DatasetDefinition definition) {
        return batch(definition, Instant.parse("2026-09-03T00:00:00Z"));
    }

    @SafeVarargs
    private static AdaptedBatch batch(
            DatasetDefinition definition,
            Instant ingestedAt,
            Map<String, Object>... rows) {
        List<String> columns = new ArrayList<>(columnNames(definition));
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            columns.add("business_key");
        }
        return new AdaptedBatch(
                definition.datasetKey(),
                definition.tableName(),
                columns,
                List.of(rows),
                definition.businessKey(),
                ingestedAt);
    }

    private static List<String> columnNames(DatasetDefinition definition) {
        return definition.columns().stream().map(ColumnDefinition::name).toList();
    }

    private static Map<String, Object> compositeRow(
            String tsCode, LocalDate tradeDate, String amount, String note) {
        return row(
                "ts_code", tsCode,
                "trade_date", tradeDate,
                "amount", new BigDecimal(amount),
                "note", note);
    }

    private static Map<String, Object> fingerprintRow(
            String identity, String amount, String fingerprint) {
        return row(
                "identity_value", identity,
                "amount", new BigDecimal(amount),
                "business_key", fingerprint);
    }

    private static Map<String, Object> row(Object... entries) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            row.put((String) entries[index], entries[index + 1]);
        }
        return row;
    }

    private static List<Method> publicDeclaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<DatasetKey, ?> locks(DatasetLockManager manager) throws Exception {
        Field field = DatasetLockManager.class.getDeclaredField("locks");
        field.setAccessible(true);
        return (Map<DatasetKey, ?>) field.get(manager);
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
        private final List<Integer> batchSizes = new CopyOnWriteArrayList<>();

        private RecordingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        private List<Integer> batchSizes() {
            return List.copyOf(batchSizes);
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
                    PersistenceServiceIT.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(connection, method, arguments);
                        if (method.getName().equals("prepareStatement")
                                && arguments != null
                                && arguments.length > 0
                                && arguments[0] instanceof String sql
                                && sql.startsWith("INSERT INTO")
                                && result instanceof PreparedStatement statement) {
                            return record(statement);
                        }
                        return result;
                    });
        }

        private PreparedStatement record(PreparedStatement statement) {
            AtomicInteger pending = new AtomicInteger();
            return (PreparedStatement) Proxy.newProxyInstance(
                    PersistenceServiceIT.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("addBatch")) {
                            pending.incrementAndGet();
                        } else if (method.getName().equals("clearBatch")) {
                            pending.set(0);
                        } else if (method.getName().equals("executeBatch")) {
                            batchSizes.add(pending.getAndSet(0));
                        }
                        return invoke(statement, method, arguments);
                    });
        }
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        private final PlatformTransactionManager delegate;
        private final List<TransactionSnapshot> definitions = new CopyOnWriteArrayList<>();

        private RecordingTransactionManager(PlatformTransactionManager delegate) {
            this.delegate = delegate;
        }

        private List<TransactionSnapshot> definitions() {
            return List.copyOf(definitions);
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            definitions.add(new TransactionSnapshot(
                    definition.getPropagationBehavior(), definition.getTimeout()));
            return delegate.getTransaction(definition);
        }

        @Override
        public void commit(TransactionStatus status) {
            delegate.commit(status);
        }

        @Override
        public void rollback(TransactionStatus status) {
            delegate.rollback(status);
        }
    }

    private record TransactionSnapshot(int propagation, int timeout) {
    }

    private static final class RejectingDataSource extends AbstractDataSource {
        private final AtomicInteger attempts = new AtomicInteger();

        private int attempts() {
            return attempts.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            attempts.incrementAndGet();
            throw new SQLException("database access was not expected");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }

    private static final class FailOnTransactionManager implements PlatformTransactionManager {
        private final AtomicInteger attempts = new AtomicInteger();

        private int attempts() {
            return attempts.get();
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            attempts.incrementAndGet();
            throw new IllegalStateException("transaction refused");
        }

        @Override
        public void commit(TransactionStatus status) {
            throw new AssertionError("commit was not expected");
        }

        @Override
        public void rollback(TransactionStatus status) {
            throw new AssertionError("rollback was not expected");
        }
    }

    private static final class NoSynchronizationTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
