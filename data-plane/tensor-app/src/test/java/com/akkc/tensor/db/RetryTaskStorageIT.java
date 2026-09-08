package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.download.DownloadParameterConverter;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.core.retry.RetryTaskRepository;
import com.akkc.tensor.core.retry.RetryTaskRepository.Criteria;
import com.akkc.tensor.core.retry.RetryTaskRepository.ItemKey;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.DownloadParameterProjection;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import com.akkc.tensor.core.retry.RetryTaskRepository.Failure;
import com.akkc.tensor.core.retry.RetryTaskStorageService;
import com.akkc.tensor.core.retry.TaskParametersJson;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.download.RecoverySelector.TargetType;
import com.akkc.tensor.plugin.api.download.RecoverySelector.TimeType;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RetryTaskStorageIT {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs",
                    "--log-bin-trust-function-creators=1");
    private static final DatasetKey DATASET = new DatasetKey(new PluginId("tushare_pro"), new ApiName("daily"));
    private static final Instant NOW = Instant.parse("2026-09-09T01:02:03.123456Z");
    private static final Map<String, Object> PARAMS = Map.of("start_date", "20260901", "end_date", "20260910");
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;
    private RetryTaskRepository repository;
    private RetryTaskStorageService service;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach
    void prepare() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_first_item");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_task_touch");
        jdbc.update("DELETE FROM tensor_download_task_item");
        jdbc.update("DELETE FROM tensor_download_task");
        repository = new RetryTaskRepository(jdbc, new TaskParametersJson());
        service = new RetryTaskStorageService(repository, new DataSourceTransactionManager(dataSource),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void firstItemFailureRollsBackHeaderAndPreservesPreviouslySavedTask() {
        var saved = service.create(DATASET, PARAMS, failure("2026-09-03"));
        var before = service.find(saved.key().taskId()).orElseThrow();
        assertThat(before.header().datasetKey()).isEqualTo(DATASET);
        assertThat(before.header().taskParams()).isEqualTo(PARAMS);
        assertThat(before.header().createdAt()).isEqualTo(Instant.parse("2026-09-09T01:02:03.123Z"));
        assertThat(before.header().updatedAt()).isEqualTo(before.header().createdAt());
        assertThat(before.items()).containsExactly(new RetryTaskRepository.Item(saved.key(), ErrorCode.SOURCE_TIMEOUT,
                "Source request timed out", before.header().createdAt()));
        jdbc.execute("""
                CREATE TRIGGER fail_first_item BEFORE INSERT ON tensor_download_task_item
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected first item failure'
                """);
        assertThatThrownBy(() -> service.create(DATASET, PARAMS, failure("2026-09-07")))
                .isInstanceOfSatisfying(TensorException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED))
                .hasMessage("Failure record save is unconfirmed").hasNoCause();
        // DriverManagerDataSource opens independent connections outside the completed service transaction.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task_item", Integer.class)).isOne();
        assertThat(service.find(saved.key().taskId())).contains(before);
    }

    @Test
    void appendAndDuplicateReasonRollBackWhenHeaderTouchFails() {
        var saved = service.create(DATASET, PARAMS, failure("2026-09-03"));
        var before = service.find(saved.key().taskId()).orElseThrow();
        jdbc.execute("""
                CREATE TRIGGER fail_task_touch BEFORE UPDATE ON tensor_download_task
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected touch failure'
                """);
        assertCode(() -> service.append(saved.key().taskId(), failure("2026-09-07")), ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);
        assertThat(service.find(saved.key().taskId())).contains(before);
        assertCode(() -> service.append(saved.key().taskId(), new Failure(saved.key().selector(), ErrorCode.SOURCE_TRUNCATED)),
                ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);
        assertThat(service.find(saved.key().taskId())).contains(before);
        assertCode(() -> service.updateReason(saved.key(), ErrorCode.DATA_CONFLICT), ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);
        assertThat(service.find(saved.key().taskId())).contains(before);
    }

    @Test
    void completeKeysKeepStocksDatesAndTasksIndependentAndParametersFrozen() {
        var first = service.create(DATASET, PARAMS, failure("2026-09-03"));
        var id = first.key().taskId();
        service.append(id, failure("2026-09-07"));
        var stock1 = service.append(id, stock("000001.SZ", TimeType.DATE, "2026-09-03"));
        var stock2 = service.append(id, stock("600000.SH", TimeType.DATE, "2026-09-03"));
        service.append(id, stock("000001.SZ", TimeType.MONTH, "2026-09"));
        service.append(id, stock("000001.SZ", TimeType.RANGE, "2026-09-03/2026-09-07"));
        service.append(id, new Failure(new RecoverySelector(TargetType.REQUEST, "", TimeType.NONE, ""), ErrorCode.SOURCE_TIMEOUT));
        var second = service.create(DATASET, PARAMS, failure("2026-09-03"));
        var before = service.find(id).orElseThrow();
        service.append(id, new Failure(first.key().selector(), ErrorCode.SOURCE_TRUNCATED));
        service.updateReason(stock1.key(), ErrorCode.DATA_CONFLICT);
        var task = service.find(id).orElseThrow();
        assertThat(task.header().taskParams()).isEqualTo(PARAMS);
        assertThat(task.header().createdAt()).isEqualTo(before.header().createdAt());
        assertThat(task.items()).hasSize(7);
        assertThat(task.items()).extracting(i -> i.key().selector()).containsExactly(
                first.key().selector(), failure("2026-09-07").selector(),
                new RecoverySelector(TargetType.REQUEST, "", TimeType.NONE, ""),
                stock1.key().selector(), stock("000001.SZ", TimeType.MONTH, "2026-09").selector(),
                stock("000001.SZ", TimeType.RANGE, "2026-09-03/2026-09-07").selector(), stock2.key().selector());
        assertThat(task.items().stream().filter(i -> i.key().equals(stock1.key())).findFirst().orElseThrow().errorCode())
                .isEqualTo(ErrorCode.DATA_CONFLICT);
        assertThat(task.items().stream().filter(i -> i.key().equals(stock2.key())).findFirst().orElseThrow().errorCode())
                .isEqualTo(ErrorCode.SOURCE_TIMEOUT);
        assertThat(task.items().stream().filter(i -> !i.key().equals(stock1.key()) && !i.key().equals(first.key())).toList())
                .containsExactlyElementsOf(before.items().stream()
                        .filter(i -> !i.key().equals(stock1.key()) && !i.key().equals(first.key())).toList());
        assertThat(service.find(second.key().taskId()).orElseThrow().items().getFirst().errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
        var missing = new ItemKey(id, failure("2026-09-08").selector());
        assertCode(() -> service.updateReason(missing, ErrorCode.DATA_CONFLICT), ErrorCode.RETRY_TASK_NOT_FOUND);
        assertCode(() -> service.append(UUID.randomUUID(), failure("2026-09-03")), ErrorCode.RETRY_TASK_NOT_FOUND);
        assertThat(service.find(id)).contains(task);
    }

    @Test
    void jsonRoundTripPreservesValuesAndRejectsActualDatabasePrecisionLossAtomically() {
        var nested = new LinkedHashMap<String, Object>();
        nested.put("values", Arrays.asList("中文\"\\", true, null, Map.of("amount", new BigDecimal("1.25"))));
        nested.put("integer", 42);
        nested.put("scaled", new BigDecimal("1.2300"));
        var saved = service.create(DATASET, nested, failure("2026-09-03"));
        var before = service.find(saved.key().taskId()).orElseThrow();
        assertThat(before.header().taskParams().get("integer")).isEqualTo(new BigInteger("42"));
        assertThat((BigDecimal) before.header().taskParams().get("scaled")).isEqualByComparingTo("1.23");
        assertThat(before.header().taskParams().get("values")).isEqualTo(nested.get("values"));
        nested.clear();
        assertThat(service.find(saved.key().taskId())).contains(before);
        assertThat(jdbc.queryForObject("SELECT JSON_TYPE(task_params) FROM tensor_download_task", String.class))
                .isEqualTo("OBJECT");
        for (var bad : List.of(Map.of("number", new BigDecimal("12345678901234567890.123456789")),
                Map.of("number", new BigInteger("18446744073709551617")),
                Map.of("nested", List.of(Map.of("number", new BigDecimal("12345678901234567890.123456789")))))) {
            assertThatThrownBy(() -> service.create(DATASET, new LinkedHashMap<>(bad), failure("2026-09-07")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Task parameters cannot be stored without loss").hasNoCause();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task_item", Integer.class)).isOne();
            assertThat(service.find(saved.key().taskId())).contains(before);
        }
        // This larger-than-uint64 integer is exactly representable; reject only actual value changes.
        var exact = service.create(DATASET, Map.of("number", new BigInteger("100000000000000000000")), failure("2026-09-03"));
        var value = (Number) service.find(exact.key().taskId()).orElseThrow().header().taskParams().get("number");
        assertThat(new BigDecimal(value.toString())).isEqualByComparingTo("100000000000000000000");
    }

    @Test
    void databaseEnforcesObjectJsonPrimaryForeignKeysAndSelectorShape() {
        var saved = service.create(DATASET, Map.of(), failure("2026-09-03"));
        String id = saved.key().taskId().toString();
        for (String bad : Arrays.asList("{", "null", "[]", "42", "true", "\"{}\"", null)) {
            assertThatThrownBy(() -> jdbc.update("UPDATE tensor_download_task SET task_params=? WHERE task_id=?", bad, id))
                    .isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> rawItem(id, "REQUEST", "", "DATE", "2026-09-03")).isInstanceOf(DataAccessException.class);
        rawItem(id, "STOCK", "000001.SZ", "DATE", "2026-09-03");
        rawItem(id, "STOCK", "600000.SH", "DATE", "2026-09-03");
        rawItem(id, "REQUEST", "", "NONE", "");
        assertThatThrownBy(() -> rawItem(UUID.randomUUID().toString(), "REQUEST", "", "NONE", ""))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tensor_download_task WHERE task_id=?", id))
                .isInstanceOf(DataAccessException.class);
        for (var invalid : List.of(List.of("REQUEST", "stock", "NONE", ""), List.of("STOCK", "", "NONE", ""),
                List.of("BAD", "", "NONE", ""), List.of("REQUEST", "", "NONE", "day"),
                List.of("REQUEST", "", "DATE", ""), List.of("REQUEST", "", "BAD", ""))) {
            assertThatThrownBy(() -> rawItem(id, invalid.get(0), invalid.get(1), invalid.get(2), invalid.get(3)))
                    .isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> stock("000001.sz", TimeType.DATE, "2026-09-03")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> failure("2026-02-30")).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.find(saved.key().taskId()).orElseThrow().items()).hasSize(4);
    }

    @Test
    void transactionPrimitivesJoinCallerConnectionRollbackAndDeleteOnlyExactItem() {
        var first = service.create(DATASET, PARAMS, stock("000001.SZ", TimeType.DATE, "2026-09-03"));
        var second = service.append(first.key().taskId(), stock("600000.SH", TimeType.DATE, "2026-09-03"));
        var anotherDate = service.append(first.key().taskId(), stock("000001.SZ", TimeType.DATE, "2026-09-07"));
        var month = service.append(first.key().taskId(), stock("000001.SZ", TimeType.MONTH, "2026-09"));
        var range = service.append(first.key().taskId(), stock("000001.SZ", TimeType.RANGE, "2026-09-03/2026-09-07"));
        var request = service.append(first.key().taskId(), failure("2026-09-03"));
        var otherTask = service.create(DATASET, PARAMS, stock("000001.SZ", TimeType.DATE, "2026-09-03"));
        var before = service.find(first.key().taskId()).orElseThrow();
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() -> tx.execute(status -> {
            assertThat(repository.lockTask(first.key().taskId())).isPresent();
            assertThat(repository.containsItem(first.key())).isTrue();
            assertThat(repository.deleteItem(first.key())).isOne();
            assertThat(repository.hasItems(first.key().taskId())).isTrue();
            assertThat(repository.deleteEmptyTask(first.key().taskId())).isZero();
            repository.touchTask(first.key().taskId(), NOW.plusSeconds(1));
            throw new IllegalStateException("rollback test");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback test");
        assertThat(service.find(first.key().taskId())).contains(before);
        tx.executeWithoutResult(status -> {
            repository.lockTask(first.key().taskId());
            assertThat(repository.deleteItem(first.key())).isOne();
            assertThat(repository.deleteItem(first.key())).isZero();
            assertThat(repository.containsItem(second.key())).isTrue();
            assertThat(repository.deleteEmptyTask(first.key().taskId())).isZero();
        });
        assertThat(service.find(first.key().taskId()).orElseThrow().items()).extracting(i -> i.key())
                .containsExactlyInAnyOrder(second.key(), anotherDate.key(), month.key(), range.key(), request.key());
        assertThat(service.find(otherTask.key().taskId()).orElseThrow().items()).extracting(i -> i.key())
                .containsExactly(otherTask.key());
        tx.executeWithoutResult(status -> {
            repository.lockTask(second.key().taskId());
            for (var key : List.of(second.key(), anotherDate.key(), month.key(), range.key(), request.key())) {
                assertThat(repository.containsItem(key)).isTrue();
                assertThat(repository.deleteItem(key)).isOne();
            }
            assertThat(repository.hasItems(second.key().taskId())).isFalse();
            assertThat(repository.deleteEmptyTask(second.key().taskId())).isOne();
        });
        assertThat(service.find(first.key().taskId())).isEmpty();
    }

    @Test
    void taskRowLockLastsUntilCommitOrRollbackAndDoesNotBlockAnotherTask() throws Exception {
        for (boolean rollback : List.of(false, true)) {
            var first = service.create(DATASET, PARAMS, failure("2026-09-03"));
            var other = service.create(DATASET, PARAMS, failure("2026-09-03"));
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var attempting = new CountDownLatch(1);
            try (var threads = Executors.newFixedThreadPool(3)) {
                var holder = threads.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                        .executeWithoutResult(status -> {
                            repository.lockTask(first.key().taskId());
                            locked.countDown();
                            await(release);
                            if (rollback) status.setRollbackOnly();
                        }));
                try {
                    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                    var writer = threads.submit(() -> {
                        attempting.countDown();
                        return service.append(first.key().taskId(), failure("2026-09-07"));
                    });
                    assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> writer.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    var independent = threads.submit(() -> service.append(other.key().taskId(), failure("2026-09-07")));
                    assertThat(independent.get(5, TimeUnit.SECONDS)).isNotNull();
                    release.countDown();
                    holder.get(5, TimeUnit.SECONDS);
                    assertThat(writer.get(5, TimeUnit.SECONDS)).isNotNull();
                } finally {
                    release.countDown();
                }
            }
        }
    }

    @Test
    void utcMillisecondsIgnoreJvmAndConnectionSessionTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            var zoned = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            var properties = new Properties();
            properties.setProperty("sessionVariables", "time_zone='+08:00'");
            zoned.setConnectionProperties(properties);
            var zonedJdbc = new JdbcTemplate(zoned);
            assertThat(zonedJdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+08:00");
            var zonedService = new RetryTaskStorageService(new RetryTaskRepository(zonedJdbc, new TaskParametersJson()),
                    new DataSourceTransactionManager(zoned), Clock.fixed(NOW, ZoneOffset.UTC));
            var key = zonedService.create(DATASET, PARAMS, failure("2026-09-03")).key();
            var task = zonedService.find(key.taskId()).orElseThrow();
            var expected = Instant.parse("2026-09-09T01:02:03.123Z");
            assertThat(task.header().createdAt()).isEqualTo(expected);
            assertThat(task.header().updatedAt()).isEqualTo(expected);
            assertThat(task.items().getFirst().updatedAt()).isEqualTo(expected);
            assertThat(zonedJdbc.queryForObject("SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f') FROM tensor_download_task",
                    String.class)).isEqualTo("2026-09-09 01:02:03.123000");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void stablePagesAndIndependentFiltersKeepAllChildrenAndUnknownPluginRecords() {
        List<String> expected = new ArrayList<>();
        for (int n = 1; n <= 23; n++) {
            String id = new UUID(0, n).toString();
            expected.add(id);
            rawHeader(id, n <= 20 ? "retired" : "tushare_pro", n <= 21 ? "daily" : "weekly", "{}");
            rawItem(id, "REQUEST", "", "DATE", "2026-09-03");
        }
        String populated = expected.getLast();
        rawItem(populated, "STOCK", "600000.SH", "DATE", "2026-09-03");
        rawItem(populated, "STOCK", "000001.SZ", "DATE", "2026-09-03");
        expected.sort(Comparator.reverseOrder());
        var first = service.list(Criteria.defaults());
        var second = service.list(new Criteria(null, null, 2, 20));
        assertThat(first.items()).hasSize(20);
        assertThat(first.totalElements()).isEqualTo(23);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.items()).hasSize(3);
        List<String> actual = new ArrayList<>();
        first.items().forEach(t -> actual.add(t.header().taskId().toString()));
        second.items().forEach(t -> actual.add(t.header().taskId().toString()));
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(first.items().getFirst().items()).extracting(i -> i.key().selector().targetValue())
                .containsExactly("", "000001.SZ", "600000.SH");
        assertThat(service.list(new Criteria(null, null, Integer.MAX_VALUE, 20))).isEqualTo(second);
        for (int size : List.of(50, 100)) assertThat(service.list(new Criteria(null, null, 1, size)).items()).hasSize(23);
        assertThat(service.list(new Criteria(new PluginId("retired"), null, 1, 20)).totalElements()).isEqualTo(20);
        assertThat(service.list(new Criteria(null, new ApiName("weekly"), 1, 20)).totalElements()).isEqualTo(2);
        assertThat(service.list(new Criteria(new PluginId("tushare_pro"), new ApiName("daily"), 1, 20)).totalElements()).isOne();
        var empty = service.list(new Criteria(new PluginId("unknown"), null, 100, 50));
        assertThat(empty.items()).isEmpty();
        assertThat(empty.page()).isOne();
        assertThat(empty.pageSize()).isEqualTo(50);
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.totalPages()).isZero();
        assertThat(service.find(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> new Criteria(null, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Criteria(null, null, 1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PluginId("x' OR 1=1")).isInstanceOf(IllegalArgumentException.class);
        // A changed timestamp takes precedence over UUID order.
        service.append(UUID.fromString(expected.getLast()), failure("2026-09-07"));
        assertThat(service.list(Criteria.defaults()).items().getFirst().header().taskId().toString()).isEqualTo(expected.getLast());
    }

    @Test
    void listCountHeadersAndChildrenShareOneRepeatableReadSnapshot() {
        var saved = service.create(DATASET, PARAMS, failure("2026-09-03"));
        var before = service.find(saved.key().taskId()).orElseThrow();
        var inserted = new AtomicBoolean();
        var intercepted = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException { return wrap(dataSource.getConnection()); }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                return wrap(dataSource.getConnection(user, password));
            }
            private Connection wrap(Connection connection) {
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            Object result = invoke(connection, method, args);
                            if (method.getName().equals("prepareStatement")
                                    && ((String) args[0]).toLowerCase(Locale.ROOT).contains("count(")) {
                                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                        (statement, operation, values) -> {
                                            Object response = invoke(result, operation, values);
                                            if (operation.getName().equals("executeQuery") && inserted.compareAndSet(false, true)) {
                                                try (var executor = Executors.newSingleThreadExecutor()) {
                                                    executor.submit(() -> {
                                                        service.append(saved.key().taskId(), failure("2026-09-07"));
                                                        service.create(DATASET, PARAMS, failure("2026-09-08"));
                                                    }).get(5, TimeUnit.SECONDS);
                                                }
                                            }
                                            return response;
                                        });
                            }
                            return result;
                        });
            }
        };
        var snapshotService = new RetryTaskStorageService(
                new RetryTaskRepository(new JdbcTemplate(intercepted), new TaskParametersJson()),
                new DataSourceTransactionManager(intercepted), Clock.systemUTC());
        var page = snapshotService.list(Criteria.defaults());
        assertThat(inserted).isTrue();
        assertThat(page.totalElements()).isOne();
        assertThat(page.items()).containsExactly(before);
        assertThat(service.list(Criteria.defaults()).totalElements()).isEqualTo(2);
        assertThat(service.find(saved.key().taskId()).orElseThrow().items()).hasSize(2);
    }

    @Test
    void converterConditionsRemainFrozenAndLegacyDatesAreReadWithoutMetadata() {
        var converter = new DownloadParameterConverter(new ParameterValidator());
        var api = controlledStockApi();
        var raw = new LinkedHashMap<>(PARAMS);
        raw.put("ts_code", " 000001.sz ");
        var validated = converter.bindInitial(api, raw);
        for (var target : List.of(TargetType.REQUEST, TargetType.STOCK)) {
            var params = converter.taskParameters(api, validated, target);
            var selector = target == TargetType.STOCK ? stock("000001.SZ", TimeType.DATE, "2026-09-03").selector()
                    : failure("2026-09-03").selector();
            var saved = service.create(DATASET, params, new Failure(selector, ErrorCode.SOURCE_TIMEOUT));
            service.append(saved.key().taskId(), new Failure(selector, ErrorCode.SOURCE_TRUNCATED));
            service.updateReason(saved.key(), ErrorCode.DATA_CONFLICT);
            var task = service.find(saved.key().taskId()).orElseThrow();
            assertThat(task.header().taskParams()).isEqualTo(params).containsAllEntriesOf(PARAMS);
            assertThat(task.header().taskParams().containsKey("ts_code")).isEqualTo(target == TargetType.REQUEST);
            assertThat(converter.mapRetry(api, task.header().taskParams(), selector).sourceParams().values())
                    .isEqualTo(Map.of("ts_code", "000001.SZ", "trade_date", "20260903"));
        }
        for (var legacy : List.<Map<String, Object>>of(Map.of(), Map.of("start_date", "20260901"),
                Map.of("start_date", "bad", "end_date", "20260910"))) {
            var selected = stock("000001.SZ", TimeType.DATE, "2026-09-03");
            var key = service.create(DATASET, legacy, selected).key();
            var loaded = service.find(key.taskId()).orElseThrow().header().taskParams();
            assertThat(loaded).isEqualTo(legacy);
            if (legacy.isEmpty()) assertThat(converter.mapRetry(api, loaded, selected.selector()).originalDateRange()).isNull();
            else assertCode(() -> converter.mapRetry(api, loaded, selected.selector()), ErrorCode.RETRY_TASK_INVALID);
        }
    }

    @Test
    void recreatingServicesReadsCommittedRecordsButUncommittedHeaderIsAbsent() throws Exception {
        var saved = service.create(DATASET, PARAMS, failure("2026-09-03"));
        var before = service.find(saved.key().taskId()).orElseThrow();
        var fresh = new RetryTaskStorageService(new RetryTaskRepository(new JdbcTemplate(dataSource), new TaskParametersJson()),
                new DataSourceTransactionManager(dataSource), Clock.systemUTC());
        assertThat(fresh.find(saved.key().taskId())).contains(before);
        String uncommitted = UUID.randomUUID().toString();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO tensor_download_task VALUES (?, 'retired', 'daily', '{}', ?, ?)
                    """)) {
                statement.setString(1, uncommitted);
                statement.setString(2, "2026-09-01 00:00:00.000");
                statement.setString(3, "2026-09-01 00:00:00.000");
                statement.executeUpdate();
            }
            assertThat(fresh.find(UUID.fromString(uncommitted))).isEmpty();
            connection.rollback();
        }
        assertThat(fresh.find(UUID.fromString(uncommitted))).isEmpty();
        assertThat(fresh.find(saved.key().taskId())).contains(before);
    }

    @Test
    void corruptSavedIdentifiersSelectorsAndReasonsProduceOnlySafeErrors() {
        var key = service.create(DATASET, PARAMS, failure("2026-09-03")).key();
        String id = key.taskId().toString();
        for (var invalid : List.of(List.of("error_code", "NOT_A_CODE"), List.of("error_message", ""),
                List.of("error_message", "credential\nSENTINEL"), List.of("time_value", "invalid-SENTINEL"))) {
            // Column identifiers below are a closed list of test constants, never application input.
            jdbc.update("UPDATE tensor_download_task_item SET " + invalid.get(0) + "=? WHERE task_id=?", invalid.get(1), id);
            assertCode(() -> service.find(key.taskId()), ErrorCode.RETRY_TASK_INVALID);
            jdbc.update("UPDATE tensor_download_task_item SET error_code='SOURCE_TIMEOUT', error_message='Source request timed out', time_value='2026-09-03' WHERE task_id=?", id);
        }
        jdbc.update("UPDATE tensor_download_task SET plugin_id='INVALID-SENTINEL' WHERE task_id=?", id);
        assertCode(() -> service.find(key.taskId()), ErrorCode.RETRY_TASK_INVALID);
        jdbc.update("UPDATE tensor_download_task SET plugin_id='tushare_pro' WHERE task_id=?", id);
        for (String malformed : List.of("invalid-uuid-SENTINEL", "1-1-1-1-1", "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")) {
            rawHeader(malformed, "retired", "daily", "{}");
            rawItem(malformed, "REQUEST", "", "NONE", "");
            assertCode(() -> service.list(Criteria.defaults()), ErrorCode.RETRY_TASK_INVALID);
            jdbc.update("DELETE FROM tensor_download_task_item WHERE task_id=?", malformed);
            jdbc.update("DELETE FROM tensor_download_task WHERE task_id=?", malformed);
        }
    }

    private static void rawHeader(String id, String plugin, String api, String json) {
        jdbc.update("INSERT INTO tensor_download_task VALUES (?, ?, ?, ?, '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000')",
                id, plugin, api, json);
    }

    private static void rawItem(String id, String target, String targetValue, String time, String timeValue) {
        jdbc.update("INSERT INTO tensor_download_task_item VALUES (?, ?, ?, ?, ?, 'SOURCE_TIMEOUT', 'Source request timed out', '2026-09-01 00:00:00.000')",
                id, target, targetValue, time, timeValue);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(TensorException.class, error -> {
            assertThat(error.code()).isEqualTo(code);
            assertThat(error).hasNoCause();
            assertThat(error.getMessage()).doesNotContain("SENTINEL", "SELECT", "UPDATE", "INSERT");
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static ApiDescriptor controlledStockApi() {
        var p = com.akkc.tensor.test.DownloadPolicies.tradeRange();
        var policy = new DownloadPolicy(p.mode(), p.dateSemantic(), p.description(), p.calendarProfile(), p.limits(),
                p.sourceRequestMode(), p.sourceDateParameter(), p.requestEvidenceStatus(), p.batchPlanning(),
                new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "trade_date", TimeType.DATE, true, List.of("test")),
                p.completenessPolicy(), p.calendarEvidenceStatus(), p.evidenceRefs());
        var source = List.of(new ParameterDescriptor("ts_code", "Stock", "Stock", ParameterType.TS_CODE, true,
                        null, List.of(), null, null),
                new ParameterDescriptor("trade_date", "Date", "Date", ParameterType.DATE, true, null, List.of(), null, null));
        return new ApiDescriptor(new ApiName("daily"), "Controlled", "Controlled", QueryMode.snapshot,
                DownloadParameterProjection.project(source, policy), policy, source);
    }

    private static Failure stock(String code, TimeType time, String value) {
        return new Failure(new RecoverySelector(TargetType.STOCK, code, time, value), ErrorCode.SOURCE_TIMEOUT);
    }

    private static Failure failure(String day) {
        return new Failure(new RecoverySelector(TargetType.REQUEST, "", TimeType.DATE, day), ErrorCode.SOURCE_TIMEOUT);
    }
}
