package com.akkc.tensor.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ExistingKeyRepositoryIT {
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"));

    private static JdbcTemplate jdbcTemplate;
    private ExistingKeyRepository repository;

    @BeforeAll
    static void createTables() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbcTemplate.execute("CREATE TABLE m06__single (code VARCHAR(64) NOT NULL PRIMARY KEY)");
        jdbcTemplate.execute("CREATE TABLE m06__fingerprint (business_key CHAR(64) NOT NULL PRIMARY KEY)");
        jdbcTemplate.execute("CREATE TABLE m06__composite (ts_code VARCHAR(64) NOT NULL, trade_date DATE NOT NULL, PRIMARY KEY (ts_code, trade_date))");
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update("DELETE FROM m06__single");
        jdbcTemplate.update("DELETE FROM m06__fingerprint");
        jdbcTemplate.update("DELETE FROM m06__composite");
        repository = new ExistingKeyRepository(jdbcTemplate);
    }

    @Test
    void enforcesPublicNullValidationLockHandleAndSqlFailureContracts() throws Exception {
        assertThat(Modifier.isFinal(DatasetLockManager.class.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(ExistingKeyRepository.class.getModifiers())).isTrue();
        assertThat(WriteCounts.class.isRecord()).isTrue();
        assertThat(DatasetLockManager.class.getConstructors())
                .containsExactly(DatasetLockManager.class.getConstructor());
        assertThat(ExistingKeyRepository.class.getConstructors())
                .containsExactly(ExistingKeyRepository.class.getConstructor(JdbcTemplate.class));
        Method acquire = DatasetLockManager.class.getDeclaredMethod("acquire", DatasetKey.class);
        Method findExisting = ExistingKeyRepository.class.getDeclaredMethod(
                "findExisting", DatasetDefinition.class, List.class);
        Method from = WriteCounts.class.getDeclaredMethod("from", List.class, Set.class);
        assertThat(publicDeclaredMethods(DatasetLockManager.class)).containsExactly(acquire);
        assertThat(publicDeclaredMethods(ExistingKeyRepository.class)).containsExactly(findExisting);
        assertThat(Modifier.isStatic(from.getModifiers())).isTrue();
        assertThat(from.getReturnType()).isEqualTo(WriteCounts.class);

        DatasetLockManager lockManager = new DatasetLockManager();
        DatasetDefinition single = singleDefinition();
        BusinessKey key = key("one");
        assertThatNullPointerException().isThrownBy(() -> lockManager.acquire(null)).withMessage("datasetKey");
        assertThatNullPointerException().isThrownBy(() -> new ExistingKeyRepository(null)).withMessage("jdbcTemplate");
        assertThatNullPointerException().isThrownBy(() -> repository.findExisting(null, List.of(key)))
                .withMessage("definition");
        assertThatNullPointerException().isThrownBy(() -> repository.findExisting(single, null))
                .withMessage("keys");
        assertThatNullPointerException().isThrownBy(() -> WriteCounts.from(null, Set.of()))
                .withMessage("keys");
        assertThatNullPointerException().isThrownBy(() -> WriteCounts.from(List.of(key), null))
                .withMessage("existingKeys");

        List<BusinessKey> keysWithNull = Arrays.asList(key, null);
        Set<BusinessKey> existingWithNull = new LinkedHashSet<>();
        existingWithNull.add(null);
        assertThatIllegalArgumentException().isThrownBy(() -> repository.findExisting(single, keysWithNull))
                .withMessage("business keys must not contain null");
        assertThatIllegalArgumentException().isThrownBy(() -> WriteCounts.from(keysWithNull, Set.of()))
                .withMessage("business keys must not contain null");
        assertThatIllegalArgumentException().isThrownBy(() -> WriteCounts.from(List.of(key), existingWithNull))
                .withMessage("business keys must not contain null");
        assertThatIllegalArgumentException().isThrownBy(() -> new WriteCounts(-1, 0))
                .withMessage("write counts must be non-negative");
        assertThatIllegalArgumentException().isThrownBy(() -> new WriteCounts(0, -1))
                .withMessage("write counts must be non-negative");
        assertThatIllegalArgumentException().isThrownBy(() -> WriteCounts.from(List.of(key), Set.of(key("other"))))
                .withMessage("existingKeys must be a subset of keys");

        Set<BusinessKey> empty = repository.findExisting(missingDefinition(), List.of());
        assertThat(empty).isEmpty();
        assertThatThrownBy(() -> empty.add(key)).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> repository.findExisting(missingDefinition(), List.of(new BusinessKey(List.of("one", "two")))))
                .withMessage("Business key width does not match dataset");
        assertThatThrownBy(() -> repository.findExisting(missingDefinition(), List.of(key)))
                .isInstanceOf(DataAccessException.class);

        Lock handle = lockManager.acquire(single.datasetKey());
        assertThatThrownBy(handle::lock).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Lock handle is already acquired");
        assertThatThrownBy(handle::lockInterruptibly).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Lock handle is already acquired");
        assertThatThrownBy(handle::tryLock).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Lock handle is already acquired");
        assertThatThrownBy(() -> handle.tryLock(1, TimeUnit.MILLISECONDS))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Lock handle is already acquired");
        assertThatThrownBy(handle::newCondition).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Lock handle is already acquired");
        handle.unlock();
        assertThatIllegalStateException().isThrownBy(handle::unlock).withMessage("Lock handle already released");
    }

    @Test
    void returnsAnUnmodifiableEmptySetWhenNoSingleCompositeKeysExist() {
        Set<BusinessKey> result = repository.findExisting(singleDefinition(), List.of(key("missing")));

        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.add(key("change")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findsAllFingerprintKeysUsingOnlyThePhysicalBusinessKey() {
        String first = "a".repeat(64);
        String second = "b".repeat(64);
        jdbcTemplate.update("INSERT INTO m06__fingerprint (business_key) VALUES (?), (?)", first, second);

        Set<BusinessKey> result = repository.findExisting(
                fingerprintDefinition(), List.of(key(first), key(second)));

        assertThat(result).containsExactlyInAnyOrder(key(first), key(second));
    }

    @Test
    void deduplicatesMixedSingleKeysAndComputesCountsFromMembership() {
        jdbcTemplate.update("INSERT INTO m06__single (code) VALUES (?)", "existing");
        List<BusinessKey> keys = List.of(key("existing"), key("new"), key("existing"));

        Set<BusinessKey> existing = repository.findExisting(singleDefinition(), keys);
        WriteCounts counts = WriteCounts.from(keys, existing);

        assertThat(existing).containsExactly(key("existing"));
        assertThat(counts).isEqualTo(new WriteCounts(1, 1));
        assertThat(Math.addExact(counts.insertedRows(), counts.updatedRows())).isEqualTo(2);
    }

    @Test
    void matchesCompositeTuplesWithoutCrossingColumnsAndPreservesTypes() {
        LocalDate firstDate = LocalDate.of(2026, 9, 1);
        LocalDate secondDate = LocalDate.of(2026, 9, 2);
        jdbcTemplate.update("INSERT INTO m06__composite (ts_code, trade_date) VALUES (?, ?), (?, ?)",
                "AAA", firstDate, "BBB", secondDate);
        BusinessKey first = new BusinessKey(List.of("AAA", firstDate));
        BusinessKey second = new BusinessKey(List.of("BBB", secondDate));

        assertThat(repository.findExisting(compositeDefinition(), List.of(
                new BusinessKey(List.of("AAA", secondDate)),
                new BusinessKey(List.of("BBB", firstDate)))))
                .isEmpty();
        assertThat(repository.findExisting(compositeDefinition(), List.of(first, second)))
                .containsExactlyInAnyOrder(first, second);
        assertThat(repository.findExisting(compositeDefinition(), List.of(
                first, new BusinessKey(List.of("CCC", firstDate)))))
                .containsExactly(first);
    }

    @Test
    void splitsOneThousandOneSingleKeysAtTheBindLimitWithoutInterpolatingValues() {
        List<BusinessKey> keys = new ArrayList<>();
        keys.add(key("edge-'?-value"));
        for (int index = 1; index < 1000; index++) {
            keys.add(key("code%04d".formatted(index)));
        }
        keys.add(key("code1000"));
        jdbcTemplate.update("INSERT INTO m06__single (code) VALUES (?), (?)",
                "edge-'?-value", "code1000");

        Set<BusinessKey> result = repository.findExisting(singleDefinition(), keys);

        assertThat(result).containsExactlyInAnyOrder(key("edge-'?-value"), key("code1000"));
    }

    @Test
    void splitsFiveHundredOneCompositeKeysAtOneThousandBindParameters() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        List<BusinessKey> keys = new ArrayList<>();
        for (int index = 0; index <= 500; index++) {
            keys.add(new BusinessKey(List.of("TS%04d".formatted(index), start.plusDays(index))));
        }
        jdbcTemplate.update("INSERT INTO m06__composite (ts_code, trade_date) VALUES (?, ?), (?, ?)",
                "TS0000", start, "TS0500", start.plusDays(500));

        Set<BusinessKey> result = repository.findExisting(compositeDefinition(), keys);

        assertThat(result).containsExactlyInAnyOrder(
                new BusinessKey(List.of("TS0000", start)),
                new BusinessKey(List.of("TS0500", start.plusDays(500))));
    }

    @Test
    void ordersSameDatasetWaitersWhileAllowingIsolationReentrancyAndSafeCleanup() throws Exception {
        DatasetLockManager manager = new DatasetLockManager();
        DatasetKey datasetKey = singleDefinition().datasetKey();
        DatasetKey otherKey = fingerprintDefinition().datasetKey();
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        Lock outer = manager.acquire(datasetKey);
        Lock nested = manager.acquire(datasetKey);

        Lock independent = manager.acquire(otherKey);
        independent.unlock();
        nested.unlock();

        Thread first = waiter(manager, datasetKey, 1, firstStarted, order);
        first.start();
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        awaitBlocked(first);
        Thread second = waiter(manager, datasetKey, 2, secondStarted, order);
        second.start();
        assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
        awaitBlocked(second);

        AtomicReference<Throwable> wrongThreadFailure = new AtomicReference<>();
        Thread wrongThread = new Thread(() -> {
            try {
                outer.unlock();
            } catch (Throwable failure) {
                wrongThreadFailure.set(failure);
            }
        });
        wrongThread.start();
        wrongThread.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(wrongThread.isAlive()).isFalse();
        assertThat(wrongThreadFailure.get()).isInstanceOf(IllegalMonitorStateException.class);

        outer.unlock();
        first.join(TimeUnit.SECONDS.toMillis(5));
        second.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(first.isAlive()).isFalse();
        assertThat(second.isAlive()).isFalse();
        assertThat(order).containsExactly(1, 2);
        assertThatIllegalStateException().isThrownBy(outer::unlock).withMessage("Lock handle already released");
        assertThat(locks(manager)).isEmpty();
    }

    private static List<Method> publicDeclaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
    }

    private static Thread waiter(
            DatasetLockManager manager,
            DatasetKey datasetKey,
            int value,
            CountDownLatch started,
            List<Integer> order) {
        return new Thread(() -> {
            started.countDown();
            Lock handle = manager.acquire(datasetKey);
            try {
                order.add(value);
            } finally {
                handle.unlock();
            }
        });
    }

    private static void awaitBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(thread.getState()).isEqualTo(Thread.State.WAITING);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<DatasetKey, ?> locks(DatasetLockManager manager) throws Exception {
        var field = DatasetLockManager.class.getDeclaredField("locks");
        field.setAccessible(true);
        return (java.util.Map<DatasetKey, ?>) field.get(manager);
    }

    private static BusinessKey key(String value) {
        return new BusinessKey(List.of(value));
    }

    private static DatasetDefinition singleDefinition() {
        return definition("single", BusinessKeyMode.COMPOSITE,
                List.of(stringColumn("code")), List.of("code"));
    }

    private static DatasetDefinition fingerprintDefinition() {
        return definition("fingerprint", BusinessKeyMode.FINGERPRINT,
                List.of(stringColumn("identity")), List.of("identity"));
    }

    private static DatasetDefinition compositeDefinition() {
        return definition("composite", BusinessKeyMode.COMPOSITE,
                List.of(stringColumn("ts_code"), dateColumn("trade_date")),
                List.of("ts_code", "trade_date"));
    }

    private static DatasetDefinition missingDefinition() {
        return definition("missing", BusinessKeyMode.COMPOSITE,
                List.of(stringColumn("code")), List.of("code"));
    }

    private static DatasetDefinition definition(
            String api,
            BusinessKeyMode mode,
            List<ColumnDefinition> columns,
            List<String> keyFields) {
        DatasetKey datasetKey = new DatasetKey(new PluginId("m06"), new ApiName(api));
        return new DatasetDefinition(
                datasetKey,
                "Dataset",
                "test",
                QueryMode.trade_date,
                List.of(),
                TableName.from(datasetKey),
                columns,
                new BusinessKeyDefinition(mode, keyFields),
                List.of(),
                null,
                500);
    }

    private static ColumnDefinition stringColumn(String name) {
        return new ColumnDefinition(
                name, name, LogicalType.STRING, false, 0, 64, null, null, List.of(), false);
    }

    private static ColumnDefinition dateColumn(String name) {
        return new ColumnDefinition(
                name, name, LogicalType.DATE, false, 0, null, null, null, List.of(), false);
    }
}
