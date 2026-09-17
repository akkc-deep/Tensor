package com.akkc.tensor.core.integrity;

import static org.assertj.core.api.Assertions.*;
import static com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor.ScopeKind.*;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.catalog.DatasetStartupValidator;
import com.akkc.tensor.core.catalog.SchemaInspector;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.lang.reflect.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IntegrityReadRepositoryIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6");
    static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final LocalDate DAY = LocalDate.of(2026, 1, 7);
    static final IntegrityDateRange RANGE = new IntegrityDateRange(DAY, DAY.plusDays(1));
    static final DatasetKey TRADES = key("trades"), CALENDAR = key("calendar");
    static final List<ColumnDefinition> TRADE_COLUMNS = List.of(
            column("symbol", LogicalType.STRING, false, 0),
            column("day", LogicalType.DATE, false, 1),
            column("event", LogicalType.LONG, false, 2),
            column("amount", LogicalType.DECIMAL, true, 3));
    static final DatasetDefinition TRADE_DEF = definition(TRADES, TRADE_COLUMNS,
            BusinessKeyMode.COMPOSITE, List.of("symbol", "day", "event"));
    static final DatasetDefinition CAL_DEF = definition(CALENDAR, List.of(
            column("exchange", LogicalType.STRING, false, 0),
            column("day", LogicalType.DATE, false, 1),
            column("open", LogicalType.LONG, false, 2)),
            BusinessKeyMode.COMPOSITE, List.of("exchange", "day"));
    static final IntegrityDependency CAL_DEP = new IntegrityDependency(CALENDAR,
            List.of("exchange", "day", "open"), "calendar");
    static final IntegrityDescriptor TARGET = descriptor(TRADES, STOCK_DATE, List.of(CAL_DEP));
    static final IntegrityDescriptor CAL_DESCRIPTOR = descriptor(CALENDAR, NON_STOCK, List.of());
    static final DatasetKey EVENTS = key("events"), STOCKS = key("stocks");
    static final DatasetDefinition EVENT_DEF = definition(EVENTS, List.of(
            column("symbol", LogicalType.STRING, false, 0), column("day", LogicalType.DATE, true, 1),
            column("event", LogicalType.LONG, false, 2)), BusinessKeyMode.FINGERPRINT,
            List.of("symbol", "day", "event"));
    static final DatasetDefinition STOCK_DEF = definition(STOCKS, List.of(
            column("symbol", LogicalType.STRING, false, 0), column("day", LogicalType.DATE, true, 1)),
            BusinessKeyMode.COMPOSITE, List.of("symbol"));
    static final IntegrityDescriptor EVENT_DESCRIPTOR = descriptor(EVENTS, STOCK_DATE, List.of());
    static final IntegrityDescriptor STOCK_DESCRIPTOR = descriptor(STOCKS, STOCK_SNAPSHOT, List.of());
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    DatasetCatalog catalog;
    IntegrityReadRepository repository;

    @BeforeAll static void schema() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE reader__trades (symbol VARCHAR(32) NOT NULL, day DATE NOT NULL, "
                + "event BIGINT NOT NULL, amount DECIMAL(38,18), " + metadata("trades")
                + ", PRIMARY KEY(symbol,day,event)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE reader__calendar (exchange VARCHAR(32) NOT NULL, day DATE NOT NULL, "
                + "`open` BIGINT NOT NULL, " + metadata("calendar")
                + ", PRIMARY KEY(exchange,day)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE reader__events (symbol VARCHAR(32) NOT NULL, day DATE, event BIGINT NOT NULL, "
                + "business_key CHAR(64) NOT NULL, " + metadata("events") + ", PRIMARY KEY(business_key)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE reader__stocks (symbol VARCHAR(32) NOT NULL, day DATE, "
                + metadata("stocks") + ", PRIMARY KEY(symbol)) ENGINE=InnoDB");
        jdbc.execute("CREATE PROCEDURE reader_attempt_write() DELETE FROM reader__trades");
    }

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM reader__trades");
        jdbc.update("DELETE FROM reader__calendar");
        jdbc.update("DELETE FROM reader__events");
        jdbc.update("DELETE FROM reader__stocks");
        catalog = new DatasetStartupValidator(List.of(TRADE_DEF, CAL_DEF, EVENT_DEF, STOCK_DEF), new SchemaInspector(dataSource)).validate();
        assertThat(catalog.list(TRADES.pluginId())).hasSize(4);
        repository = new IntegrityReadRepository(dataSource, catalog, CLOCK);
    }

    @Test void compositePagesAndFirstReferenceReadShareSnapshotDespiteConcurrentCommits() {
        for (long i = 1; i <= 5; i++) trade("AA", DAY, i);
        jdbc.update("INSERT INTO reader__calendar(exchange,day,`open`) VALUES ('X',?,1)", DAY);
        List<Long> events = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        var writer = Executors.newSingleThreadExecutor();
        CountDownLatch firstPage = new CountDownLatch(1);
        Future<?> committed = writer.submit(() -> {
            await(firstPage);
            jdbc.update("DELETE FROM reader__trades WHERE event=3");
            jdbc.update("UPDATE reader__trades SET amount=99 WHERE event=4");
            trade("AA", DAY, 6);
            jdbc.update("UPDATE reader__calendar SET `open`=0");
        });
        try {
            repository.withSnapshot(scope(TRADES), plan(RANGE), budget(100), 2, session -> {
                assertThat(session.scope().snapshotStartedAt()).isEqualTo(NOW);
                assertThat(session.scope().range()).isEqualTo(RANGE);
                session.scan(targetRequest(List.of("event", "amount")), rows -> {
                    sizes.add(rows.size());
                    rows.forEach(row -> {
                        events.add((Long) row.get("event"));
                        assertThat(row.get("amount")).isEqualTo(new BigDecimal("1.000000000000000001"));
                        assertThat(row.keySet()).containsExactlyInAnyOrder("event", "amount");
                    });
                    if (sizes.size() == 1) {
                        firstPage.countDown();
                        get(committed);
                    }
                });
                assertThat(events).containsExactly(1L, 2L, 3L, 4L, 5L);
                assertThat(sizes).containsExactly(2, 2, 1);
                assertThat(read(session, targetRequest(List.of("event"))))
                        .extracting(row -> row.get("event")).containsExactly(1L, 2L, 3L, 4L, 5L);
                assertThat(read(session, calendarRequest(RANGE)))
                        .extracting(row -> row.get("open")).containsExactly(1L);
                assertThat(session.readRequests()).hasSize(3);
                assertThat(session.readRequests().getLast().equalities()).containsEntry("exchange", "X");
                return null;
            });
        } finally {
            firstPage.countDown();
            writer.shutdownNow();
        }
        repository.withSnapshot(scope(TRADES), plan(RANGE), budget(100), 2, session -> {
            assertThat(read(session, targetRequest(List.of("event"))))
                    .extracting(row -> row.get("event")).containsExactly(1L, 2L, 4L, 5L, 6L);
            assertThat(read(session, calendarRequest(RANGE))).extracting(row -> row.get("open")).containsExactly(0L);
            return null;
        });
    }

    @Test void snapshotIsEstablishedBeforeTheFirstConsumerRead() {
        trade("AA", DAY, 1);
        repository.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2, session -> {
            trade("AA", DAY, 2); // Separate connection commits before any session SELECT.
            assertThat(read(session, targetRequest(List.of("event"))))
                    .extracting(row -> row.get("event")).containsExactly(1L);
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reader__trades", Long.class)).isEqualTo(2L);
    }

    @Test void fingerprintCursorUsesPhysicalKeysAndKeepsExactTypesAndNullableValues() {
        String[] hashes = {"c", "a", "e", "b", "d"};
        for (int i = 0; i < hashes.length; i++) event("AA", DAY, i + 1, hashes[i]);
        repository.withSnapshot(scope(EVENTS), new IntegrityReadPlan(EVENT_DESCRIPTOR, List.of()), budget(20), 2,
                session -> {
                    var rows = read(session, request(EVENTS, List.of("event", "day"), Map.of(), RANGE, false, "target"));
                    assertThat(rows).extracting(r -> r.get("event")).containsExactly(2L, 4L, 1L, 5L, 3L);
                    assertThat(rows).allSatisfy(row -> {
                        assertThat(row.get("day")).isEqualTo(DAY);
                        assertThat(row).doesNotContainKey("business_key");
                        assertThatThrownBy(() -> row.put("event", 0L)).isInstanceOf(UnsupportedOperationException.class);
                    });
                    return null;
                });
        trade("AA", DAY, 9007199254740993L);
        trade("AA", DAY, 2);
        jdbc.update("UPDATE reader__trades SET amount=NULL WHERE event=2");
        var before = jdbc.queryForList("SELECT * FROM reader__trades ORDER BY event");
        repository.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2, session -> {
            var rows = read(session, targetRequest(List.of("event", "amount", "day", "ingested_at")));
            assertThat(rows.getFirst().get("amount")).isNull();
            assertThat(rows.getLast()).containsEntry("event", 9007199254740993L)
                    .containsEntry("amount", new BigDecimal("1.000000000000000001"))
                    .containsEntry("day", DAY).containsEntry("ingested_at", Instant.parse("2026-01-01T00:00:00Z"));
            return null;
        });
        assertThat(jdbc.queryForList("SELECT * FROM reader__trades ORDER BY event")).isEqualTo(before);
    }

    @Test void rejectsCatalogCompatibleDecimalRowsThatRequireRoundingBeforeDeliveringThem() {
        jdbc.execute("ALTER TABLE reader__trades MODIFY amount DECIMAL(38,19)");
        try {
            var checked = new DatasetStartupValidator(List.of(TRADE_DEF, CAL_DEF), new SchemaInspector(dataSource)).validate();
            assertThat(checked.find(TRADES)).contains(TRADE_DEF);
            var reader = new IntegrityReadRepository(dataSource, checked, CLOCK);
            jdbc.update("INSERT INTO reader__trades(symbol,day,event,amount) VALUES ('AA',?,1,?)",
                    DAY, new BigDecimal("1.0000000000000000001"));
            List<Map<String, Object>> delivered = new ArrayList<>();
            assertReason(() -> reader.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2, session -> {
                try { session.scan(targetRequest(List.of("amount")), delivered::addAll); }
                catch (IntegrityReadException ignored) { }
                return "must not succeed";
            }), "READ_FAILED");
            assertThat(delivered).isEmpty();
            jdbc.update("UPDATE reader__trades SET amount=1.0000000000000000000");
            reader.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2, session -> {
                assertThat(read(session, targetRequest(List.of("amount")))).singleElement().satisfies(row ->
                        assertThat(row.get("amount")).isEqualTo(new BigDecimal("1.000000000000000000")));
                return null;
            });
        } finally {
            jdbc.update("DELETE FROM reader__trades");
            jdbc.execute("ALTER TABLE reader__trades MODIFY amount DECIMAL(38,18)");
        }
    }

    @Test void nullDatesAreSeparateAndSnapshotReadsNeverApplyHistoricalDateFilters() {
        event("AA", DAY, 1, "a"); event("AA", DAY.plusDays(1), 2, "b");
        event("AA", null, 3, "c"); event("BB", null, 4, "d"); event("AA", DAY.minusDays(1), 5, "e");
        repository.withSnapshot(scope(EVENTS), new IntegrityReadPlan(EVENT_DESCRIPTOR, List.of()), budget(3), 2,
                session -> {
                    List<IntegrityReadRepository.TargetBatch> batches = new ArrayList<>();
                    session.scanTarget(batches::add);
                    assertThat(batches).hasSize(2);
                    assertThat(batches.getFirst().dateScopeUnresolved()).isFalse();
                    assertThat(batches.getFirst().rows()).extracting(row -> row.get("event")).containsExactly(1L, 2L);
                    assertThat(batches.getLast().dateScopeUnresolved()).isTrue();
                    assertThat(batches.getLast().rows()).singleElement().satisfies(row ->
                            assertThat(row).containsEntry("symbol", "AA").containsEntry("day", null).containsEntry("event", 3L));
                    return null;
                });
        jdbc.update("INSERT INTO reader__stocks(symbol,day) VALUES ('AA','2000-01-01'),('BB',NULL)");
        repository.withSnapshot(scope(STOCKS), new IntegrityReadPlan(STOCK_DESCRIPTOR, List.of()), budget(1), 2,
                session -> {
                    List<IntegrityReadRepository.TargetBatch> batches = new ArrayList<>();
                    session.scanTarget(batches::add);
                    assertThat(batches).singleElement().satisfies(batch -> {
                        assertThat(batch.dateScopeUnresolved()).isFalse();
                        assertThat(batch.rows()).singleElement().satisfies(row ->
                                assertThat(row).containsEntry("symbol", "AA").containsEntry("day", LocalDate.of(2000, 1, 1)));
                    });
                    return null;
                });
    }

    @Test void exactlyFullAndEmptyScansSucceedButExtraRowsAndCombinedScansExhaustOneBudget() {
        for (int count : List.of(0, 2, 3)) {
            jdbc.update("DELETE FROM reader__trades");
            for (long i = 1; i <= count; i++) trade("AA", DAY, i);
            repository.withSnapshot(scope(TRADES), plan(RANGE), budget(Math.max(1, count)), 2, session -> {
                assertThat(read(session, targetRequest(List.of("event")))).hasSize(count);
                return null;
            });
        }
        trade("AA", DAY, 4);
        assertReason(() -> repository.withSnapshot(scope(TRADES), plan(RANGE), budget(3), 2,
                session -> read(session, targetRequest(List.of("event")))), "SCAN_LIMIT_EXCEEDED");
        jdbc.update("DELETE FROM reader__trades WHERE event>2");
        jdbc.update("INSERT INTO reader__calendar(exchange,day,`open`) VALUES ('X',?,1),('X',?,1)", DAY, DAY.plusDays(1));
        for (boolean repeatTarget : List.of(false, true)) {
            assertReason(() -> repository.withSnapshot(scope(TRADES), plan(RANGE), budget(3), 2, session -> {
                read(session, targetRequest(List.of("event")));
                return read(session, repeatTarget ? targetRequest(List.of("event")) : calendarRequest(RANGE));
            }), "SCAN_LIMIT_EXCEEDED");
        }
        var shared = budget(3);
        assertReason(() -> repository.withSnapshot(scope(TRADES), plan(RANGE), shared, 2, session -> {
            shared.consume(2); // Expected key generation by the later comparison layer.
            return read(session, targetRequest(List.of("event")));
        }), "SCAN_LIMIT_EXCEEDED");
        event("AA", DAY, 1, "a"); event("AA", DAY, 2, "b");
        event("AA", null, 3, "c"); event("AA", null, 4, "d");
        assertReason(() -> repository.withSnapshot(scope(EVENTS), new IntegrityReadPlan(EVENT_DESCRIPTOR, List.of()),
                budget(3), 2, session -> { session.scanTarget(batch -> {}); return null; }), "SCAN_LIMIT_EXCEEDED");
    }

    @Test void unauthorizedRequestsAndLossyParametersNeverExecuteASecurityTableSelect() {
        var tracked = new TrackingDataSource(dataSource);
        var reader = new IntegrityReadRepository(tracked, catalog, CLOCK);
        var requests = List.of(
                request(TRADES, List.of("event"), Map.of("symbol", "BB"), RANGE, false, "target"),
                request(TRADES, List.of("event"), Map.of(), new IntegrityDateRange(DAY.minusDays(1), DAY.plusDays(1)), false, "target"),
                new IntegrityReadRequest(TRADES, List.of("event"), Map.of(), "event", RANGE, false, "target"),
                request(key("unknown"), List.of("event"), Map.of(), RANGE, false, "target"),
                request(TRADES, List.of("secret"), Map.of(), RANGE, false, "target"),
                request(CALENDAR, List.of("source_api"), Map.of(), RANGE, false, "calendar"),
                request(CALENDAR, List.of("day"), Map.of("exchange", "Y"), RANGE, false, "calendar"),
                request(CALENDAR, List.of("day"), Map.of(), RANGE, false, "wrong-purpose"),
                request(TRADES, List.of("event"), Map.of("event", 1), RANGE, false, "target"),
                request(TRADES, List.of("amount"), Map.of("amount", new BigDecimal("1.0000000000000000001")), RANGE, false, "target"),
                request(TRADES, List.of("amount"), Map.of("amount", new BigDecimal("1e38")), RANGE, false, "target"),
                request(TRADES, List.of("day"), Map.of("day", "2026-01-07"), RANGE, false, "target"),
                request(TRADES, List.of("symbol"), Map.of("symbol", "x".repeat(33)), RANGE, false, "target"));
        for (var request : requests) {
            assertReason(() -> reader.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2,
                    session -> read(session, request)), "INVALID_READ_REQUEST");
        }
        assertThat(tracked.selects).isZero();
        assertThatThrownBy(() -> targetRequest(List.of("event;DROP_TABLE"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TableName("reader__trades;DROP_TABLE")).isInstanceOf(IllegalArgumentException.class);
        trade("AA", DAY.minusDays(1), 1); trade("AA", DAY, 2); trade("BB", DAY, 3);
        reader.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2, session -> {
            assertThat(read(session, targetRequest(List.of("event")))).extracting(row -> row.get("event")).containsExactly(2L);
            return null;
        });
    }

    @Test void referenceWindowsAllowOnlyDeclaredWeekMonthOrOriginalRange() {
        var week = new IntegrityDateRange(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11));
        var month = new IntegrityDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        jdbc.update("INSERT INTO reader__calendar(exchange,day,`open`) VALUES ('X','2026-01-01',1),"
                + "('X','2026-01-05',1),('X','2026-01-07',1),('X','2026-01-11',1),"
                + "('X','2026-01-31',1),('X','2026-02-01',1),('Y','2026-01-07',1)");
        for (var range : List.of(RANGE, week, month)) {
            repository.withSnapshot(scope(TRADES), plan(range), budget(20), 2, session -> {
                assertThat(read(session, calendarRequest(range))).hasSize(range.equals(RANGE) ? 1 : range.equals(week) ? 3 : 5);
                assertThat(session.readRequests().getFirst().dateRange()).isEqualTo(range);
                assertThat(session.scope().range()).isEqualTo(RANGE);
                return null;
            });
        }
        for (var range : List.of(new IntegrityDateRange(DAY.minusDays(1), DAY.plusDays(1)),
                new IntegrityDateRange(LocalDate.of(2025, 1, 1), DAY.plusDays(1)))) {
            assertReason(() -> repository.withSnapshot(scope(TRADES), plan(range), budget(10), 2, session -> null),
                    "INVALID_READ_REQUEST");
        }
        var unfixed = new IntegrityReadPlan(TARGET, List.of(new IntegrityReadPlan.ReferencePermit(
                CAL_DESCRIPTOR, "day", RANGE, Map.of(), "calendar")));
        assertReason(() -> repository.withSnapshot(scope(TRADES), unfixed, budget(10), 2, session -> null), "INVALID_READ_REQUEST");
        assertReason(() -> repository.withSnapshot(scope(TRADES), new IntegrityReadPlan(TARGET, List.of()), budget(10), 2,
                session -> read(session, calendarRequest(RANGE))), "INVALID_READ_REQUEST");
    }

    @Test void stockReferencesEnforceTheSameSymbolAndWindow() {
        var dep = new IntegrityDependency(EVENTS, List.of("symbol", "day", "event"), "events");
        var target = descriptor(TRADES, STOCK_DATE, List.of(dep));
        var permit = new IntegrityReadPlan.ReferencePermit(EVENT_DESCRIPTOR, "day", RANGE, Map.of(), "events");
        var plan = new IntegrityReadPlan(target, List.of(permit));
        event("AA", DAY, 1, "a"); event("BB", DAY, 2, "b"); event("AA", DAY.minusDays(1), 3, "c");
        repository.withSnapshot(scope(TRADES), plan, budget(10), 2, session -> {
            assertThat(read(session, request(EVENTS, List.of("event"), Map.of(), RANGE, false, "events")))
                    .extracting(row -> row.get("event")).containsExactly(1L);
            return null;
        });
        assertReason(() -> repository.withSnapshot(scope(TRADES), plan, budget(10), 2, session ->
                read(session, request(EVENTS, List.of("event"), Map.of("symbol", "BB"), RANGE, false, "events"))),
                "INVALID_READ_REQUEST");
        var expanded = new IntegrityReadPlan(target, List.of(new IntegrityReadPlan.ReferencePermit(
                EVENT_DESCRIPTOR, "day", new IntegrityDateRange(DAY.minusDays(1), DAY.plusDays(1)), Map.of(), "events")));
        assertReason(() -> repository.withSnapshot(scope(TRADES), expanded, budget(10), 2, session -> null), "INVALID_READ_REQUEST");
    }

    @Test void actualDatabaseReadOnlyTransactionAndCleanupProtectSecurities() {
        trade("AA", DAY, 1);
        var before = jdbc.queryForList("SELECT * FROM reader__trades");
        var tracked = new TrackingDataSource(dataSource);
        var reader = new IntegrityReadRepository(tracked, catalog, CLOCK);
        var escaped = reader.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2, session -> {
            try {
                assertThat(tracked.connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
                assertThat(tracked.connection.isReadOnly()).isTrue();
                assertThat(tracked.connection.getAutoCommit()).isFalse();
                // Exercise the database's READ ONLY guarantee even if JDBC's client-side check is disabled.
                try (var statement = tracked.connection.createStatement()) {
                    assertThatThrownBy(() -> statement.execute("CALL reader_attempt_write()"))
                            .isInstanceOfSatisfying(SQLException.class, error ->
                                    assertThat(error.getErrorCode()).isEqualTo(1792));
                }
            } catch (SQLException e) { throw new AssertionError(e); }
            read(session, targetRequest(List.of("event")));
            return session;
        });
        assertReason(() -> read(escaped, targetRequest(List.of("event"))), "READ_SESSION_CLOSED");
        assertThat(tracked.closed).isEqualTo(1);
        assertThat(tracked.rolledBack).isEqualTo(1);
        assertThat(tracked.restored).isTrue();
        assertThat(tracked.timeouts).isNotEmpty().allSatisfy(timeout -> assertThat(timeout).isBetween(1, 120));
        assertThat(jdbc.queryForList("SELECT * FROM reader__trades")).isEqualTo(before);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertReason(() -> transaction.execute(status -> reader.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2,
                session -> null)), "INVALID_READ_REQUEST");
    }

    @Test void swallowedCallbackOrSqlFailureNeverReturnsSuccessfulPartialScan() {
        trade("AA", DAY, 1); trade("AA", DAY, 2); trade("AA", DAY, 3);
        for (boolean sqlFailure : List.of(false, true)) {
            var tracked = new TrackingDataSource(dataSource);
            tracked.failSelect = sqlFailure;
            var reader = new IntegrityReadRepository(tracked, catalog, CLOCK);
            assertReason(() -> reader.withSnapshot(scope(TRADES), plan(RANGE), budget(10), 2, session -> {
                try {
                    session.scan(targetRequest(List.of("event")), rows -> { throw new IllegalStateException("sensitive callback"); });
                } catch (RuntimeException ignored) { }
                return "partial";
            }), "READ_FAILED");
            assertThat(tracked.closed).isEqualTo(1);
            assertThat(tracked.rolledBack).isEqualTo(1);
            assertThat(tracked.restored).isTrue();
        }
    }

    @Test void realStatementsUseRemainingDeadlineAndDoNotStartBelowOneSecond() {
        trade("AA", DAY, 1); trade("AA", DAY, 2); trade("AA", DAY, 3);
        var clock = new MutableClock();
        var tracked = new TrackingDataSource(dataSource);
        var reader = new IntegrityReadRepository(tracked, catalog, clock);
        var budget = new IntegrityReadBudget(10, NOW.plusMillis(2500), clock);
        assertReason(() -> reader.withSnapshot(scope(TRADES), plan(RANGE), budget, 2, session -> {
            session.scan(targetRequest(List.of("event")), rows -> clock.now = NOW.plusSeconds(2));
            return null;
        }), "UNIT_TIME_BUDGET_EXHAUSTED");
        assertThat(tracked.selects).isEqualTo(1);
        assertThat(tracked.timeouts).allSatisfy(timeout -> assertThat(timeout).isBetween(1, 2));
        clock.now = NOW;
        assertReason(() -> reader.withSnapshot(scope(TRADES), plan(RANGE),
                new IntegrityReadBudget(10, NOW.plusSeconds(1), clock), 2, session -> {
                    clock.now = NOW.plusSeconds(1);
                    return "not complete";
                }), "UNIT_TIME_BUDGET_EXHAUSTED");
    }

    static void event(String symbol, LocalDate day, long event, String hash) {
        jdbc.update("INSERT INTO reader__events(symbol,day,event,business_key) VALUES (?,?,?,?)", symbol, day, event, hash);
    }
    static IntegrityReadRequest request(DatasetKey key, List<String> columns, Map<String, Object> equalities,
            IntegrityDateRange range, boolean nullDates, String purpose) {
        return new IntegrityReadRequest(key, columns, equalities, "day", range, nullDates, purpose);
    }
    static void assertReason(Runnable operation, String reason) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(IntegrityReadException.class,
                error -> assertThat(error.reasonCode()).isEqualTo(reason));
    }
    static final class MutableClock extends Clock {
        Instant now = NOW;
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }
    static final class TrackingDataSource extends AbstractDataSource {
        final DataSource delegate;
        Connection connection;
        int selects, closed, rolledBack;
        boolean restored, failSelect;
        final List<Integer> timeouts = new ArrayList<>();
        TrackingDataSource(DataSource delegate) { this.delegate = delegate; }
        public Connection getConnection() throws SQLException {
            connection = delegate.getConnection();
            var actual = connection;
            int isolation = actual.getTransactionIsolation();
            boolean readOnly = actual.isReadOnly(), auto = actual.getAutoCommit();
            return proxy(Connection.class, actual, (method, args) -> {
                if (method.getName().equals("rollback")) rolledBack++;
                if (method.getName().equals("close")) {
                    restored = actual.getTransactionIsolation() == isolation && actual.isReadOnly() == readOnly
                            && actual.getAutoCommit() == auto;
                    closed++;
                }
                Object value = invoke(actual, method, args);
                if (value instanceof PreparedStatement statement) {
                    return proxy(PreparedStatement.class, statement, (call, parameters) -> {
                        if (call.getName().equals("setQueryTimeout")) timeouts.add((Integer) parameters[0]);
                        if (call.getName().equals("executeQuery")) {
                            selects++;
                            if (failSelect) throw new SQLException("secret driver URL and values");
                        }
                        return invoke(statement, call, parameters);
                    });
                }
                return value;
            });
        }
        public Connection getConnection(String username, String password) throws SQLException { return getConnection(); }
    }
    interface Call { Object call(Method method, Object[] args) throws Throwable; }
    static <T> T proxy(Class<T> type, T original, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> call.call(method, args)));
    }
    static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }

    static DatasetKey key(String api) { return DatasetKey.of(PluginId.of("reader"), ApiName.of(api)); }
    static ColumnDefinition column(String name, LogicalType type, boolean nullable, int order) {
        return new ColumnDefinition(name, name, type, nullable, order,
                type == LogicalType.STRING ? 32 : null,
                type == LogicalType.DECIMAL ? 38 : null, type == LogicalType.DECIMAL ? 18 : null, List.of(), false);
    }
    static DatasetDefinition definition(DatasetKey key, List<ColumnDefinition> columns,
            BusinessKeyMode mode, List<String> keys) {
        return new DatasetDefinition(key, key.apiName().value(), "test", QueryMode.snapshot, List.of(),
                TableName.from(key), columns, new BusinessKeyDefinition(mode, keys), List.of(), null);
    }
    static IntegrityDescriptor descriptor(DatasetKey key, IntegrityDescriptor.ScopeKind kind,
            List<IntegrityDependency> dependencies) {
        return new IntegrityDescriptor(key, kind, kind == NON_STOCK ? null : "symbol",
                kind == STOCK_DATE ? "day" : null, "test date", ZoneOffset.UTC, "1", dependencies,
                kind == NON_STOCK ? List.of() : List.of(new IntegrityRuleDescriptor(
                        "reader.coverage." + key.apiName().value(), "1", "coverage",
                        IntegrityRuleDescriptor.Dimension.COVERAGE, List.of(), List.of(), "test")), List.of());
    }
    static String metadata(String api) {
        return "source_plugin VARCHAR(64) NOT NULL DEFAULT 'reader', source_api VARCHAR(64) NOT NULL DEFAULT '"
                + api + "', ingested_at TIMESTAMP(3) NOT NULL DEFAULT '2026-01-01 00:00:00'";
    }
    static IntegrityScope scope(DatasetKey key) {
        return new IntegrityScope(key, "AA", RANGE.startDate(), RANGE.endDate(), NOW.minusSeconds(60), null);
    }
    static IntegrityReadBudget budget(long items) { return new IntegrityReadBudget(items, NOW.plusSeconds(120), CLOCK); }
    static IntegrityReadPlan plan(IntegrityDateRange range) {
        return new IntegrityReadPlan(TARGET, List.of(new IntegrityReadPlan.ReferencePermit(
                CAL_DESCRIPTOR, "day", range, Map.of("exchange", "X"), "calendar")));
    }
    static IntegrityReadRequest targetRequest(List<String> columns) {
        return new IntegrityReadRequest(TRADES, columns, Map.of(), "day", RANGE, false, "target");
    }
    static IntegrityReadRequest calendarRequest(IntegrityDateRange range) {
        return new IntegrityReadRequest(CALENDAR, List.of("day", "open"), Map.of(), "day", range, false, "calendar");
    }
    static void trade(String symbol, LocalDate day, long event) {
        jdbc.update("INSERT INTO reader__trades(symbol,day,event,amount) VALUES (?,?,?,?)",
                symbol, day, event, new BigDecimal("1.000000000000000001"));
    }
    static List<Map<String, Object>> read(IntegrityReadRepository.ReadSession session, IntegrityReadRequest request) {
        List<Map<String, Object>> rows = new ArrayList<>();
        session.scan(request, rows::addAll);
        return rows;
    }
    static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    static void get(Future<?> future) {
        try { future.get(10, TimeUnit.SECONDS); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
