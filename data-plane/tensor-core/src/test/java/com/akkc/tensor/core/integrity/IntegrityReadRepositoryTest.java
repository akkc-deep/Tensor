package com.akkc.tensor.core.integrity;

import static com.akkc.tensor.core.integrity.IntegrityReadException.INVALID_READ_REQUEST;
import static com.akkc.tensor.core.integrity.IntegrityReadException.READ_FAILED;
import static com.akkc.tensor.core.integrity.IntegrityReadException.READ_SESSION_CLOSED;
import static com.akkc.tensor.core.integrity.IntegrityReadException.SCAN_LIMIT_EXCEEDED;
import static com.akkc.tensor.core.integrity.IntegrityReadException.UNIT_TIME_BUDGET_EXHAUSTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.integrity.IntegrityDateRange;
import com.akkc.tensor.plugin.api.integrity.IntegrityDependency;
import com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityReadRequest;
import com.akkc.tensor.plugin.api.integrity.IntegrityRuleDescriptor;
import com.akkc.tensor.plugin.api.integrity.IntegrityScope;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class IntegrityReadRepositoryTest {
    private static final Instant ACCEPTED = Instant.parse("2026-09-16T01:00:00Z");
    private static final Instant SNAPSHOT = Instant.parse("2026-09-16T01:00:01Z");
    private static final LocalDate FIRST = LocalDate.of(2026, 9, 1);
    private static final LocalDate LAST = LocalDate.of(2026, 9, 2);
    private static final DatasetKey TARGET_KEY = key("daily");
    private static final DatasetKey REFERENCE_KEY = key("calendar");

    @Test
    void cancellationAndThreadInterruptLatchWithoutReplacingAnEarlierBudgetFailure() {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var budget = new IntegrityReadBudget(1, SNAPSHOT.plusSeconds(10),
                Clock.fixed(SNAPSHOT, ZoneOffset.UTC), cancelled::get);
        budget.consume(1); cancelled.set(true);
        assertReason(budget::check, "EXECUTION_INTERRUPTED");
        cancelled.set(false);
        assertReason(budget::check, "EXECUTION_INTERRUPTED");
        var limited = new IntegrityReadBudget(1, SNAPSHOT.plusSeconds(10),
                Clock.fixed(SNAPSHOT, ZoneOffset.UTC), cancelled::get);
        assertReason(() -> limited.consume(2), SCAN_LIMIT_EXCEEDED);
        cancelled.set(true);
        assertReason(limited::check, SCAN_LIMIT_EXCEEDED);
        var interrupted = budget();
        try {
            Thread.currentThread().interrupt();
            assertReason(interrupted::check, "EXECUTION_INTERRUPTED");
        } finally { Thread.interrupted(); }
    }

    @Test
    void budgetAllowsTheExactItemLimitAndRejectsTheNextItem() {
        MutableClock clock = new MutableClock(SNAPSHOT);
        IntegrityReadBudget budget = new IntegrityReadBudget(3, SNAPSHOT.plusSeconds(10), clock);

        budget.consume(2);
        budget.consume(1);

        assertThat(budget.remainingItems()).isZero();
        assertThat(budget.remainingTime()).isEqualTo(Duration.ofSeconds(10));
        assertReason(() -> budget.consume(1), SCAN_LIMIT_EXCEEDED);
        assertReason(budget::check, SCAN_LIMIT_EXCEEDED);
        assertThat(budget.remainingItems()).isZero();
    }

    @Test
    void caughtBudgetOverflowStillPreventsTheSnapshotActionFromSucceeding() throws Exception {
        Jdbc jdbc = jdbcWithEmptyPage();
        IntegrityReadRepository repository = repository(jdbc.dataSource, targetDefinition(), referenceDefinition());
        IntegrityReadBudget shared = budget();

        assertReason(() -> repository.withSnapshot(scope(), plan(), shared, 2, session -> {
            try { shared.consume(11); }
            catch (IntegrityReadException ignored) { }
            return "must not complete";
        }), SCAN_LIMIT_EXCEEDED);
    }

    @Test
    void budgetStopsAtTheDeadlineAndRejectsInvalidConstructionOrConsumption() {
        MutableClock clock = new MutableClock(SNAPSHOT);
        IntegrityReadBudget budget = new IntegrityReadBudget(1, SNAPSHOT.plusSeconds(1), clock);
        clock.now = SNAPSHOT.plusSeconds(1);

        assertReason(budget::check, UNIT_TIME_BUDGET_EXHAUSTED);
        assertThat(budget.remainingTime()).isEqualTo(Duration.ZERO);
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityReadBudget(0, SNAPSHOT, clock));
        assertThatIllegalArgumentException().isThrownBy(() -> new IntegrityReadBudget(1, SNAPSHOT.minusNanos(1), clock));
        assertThatIllegalArgumentException().isThrownBy(() -> budget.consume(-1));
    }

    @Test
    void planDefensivelyCopiesPermitsAndFixedEqualities() {
        IntegrityDescriptor target = descriptor(TARGET_KEY, IntegrityDescriptor.ScopeKind.STOCK_DATE,
                List.of(new IntegrityDependency(REFERENCE_KEY, List.of("exchange", "cal_date"), "calendar")));
        IntegrityDescriptor reference = descriptor(REFERENCE_KEY, IntegrityDescriptor.ScopeKind.NON_STOCK, List.of());
        Map<String, Object> equalities = new java.util.LinkedHashMap<>(Map.of("exchange", "SSE"));
        List<IntegrityReadPlan.ReferencePermit> permits = new ArrayList<>();
        permits.add(new IntegrityReadPlan.ReferencePermit(reference, "cal_date",
                new IntegrityDateRange(FIRST, LAST), equalities, "calendar"));

        IntegrityReadPlan plan = new IntegrityReadPlan(target, permits);
        equalities.put("exchange", "SZSE");
        permits.clear();

        assertThat(plan.references()).hasSize(1);
        assertThat(plan.references().getFirst().fixedEqualities()).containsEntry("exchange", "SSE");
        assertThatThrownBy(() -> plan.references().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.references().getFirst().fixedEqualities().put("exchange", "SZSE"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidEntrypointsBeforeObtainingAConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        IntegrityReadRepository repository = repository(dataSource, targetDefinition(), referenceDefinition());
        IntegrityReadPlan plan = plan();
        IntegrityReadBudget budget = budget();

        assertReason(() -> repository.withSnapshot(
                new IntegrityScope(REFERENCE_KEY, "000001.SZ", FIRST, LAST, ACCEPTED, null),
                plan, budget, 2, session -> null), INVALID_READ_REQUEST);
        assertReason(() -> repository.withSnapshot(
                new IntegrityScope(TARGET_KEY, null, FIRST, LAST, ACCEPTED, null),
                plan, budget, 2, session -> null), INVALID_READ_REQUEST);
        assertReason(() -> repository.withSnapshot(scope(), plan, budget, 0, session -> null),
                INVALID_READ_REQUEST);
        verify(dataSource, never()).getConnection();
    }

    @Test
    void rejectsAReferencePermitFromAnotherPluginBeforeObtainingAConnection() throws Exception {
        DatasetKey foreignKey = DatasetKey.of(PluginId.of("foreign"), ApiName.of("calendar"));
        DatasetDefinition foreignDefinition = definition(foreignKey, BusinessKeyMode.COMPOSITE,
                List.of("exchange", "cal_date"), List.of(
                        column("exchange", LogicalType.STRING, false, 0, 8, null, null),
                        column("cal_date", LogicalType.DATE, false, 1, null, null, null)));
        IntegrityDependency dependency = new IntegrityDependency(
                foreignKey, List.of("exchange", "cal_date"), "calendar");
        IntegrityDescriptor target = descriptor(
                TARGET_KEY, IntegrityDescriptor.ScopeKind.STOCK_DATE, List.of(dependency));
        IntegrityReadPlan crossSource = new IntegrityReadPlan(target, List.of(
                new IntegrityReadPlan.ReferencePermit(
                        descriptor(foreignKey, IntegrityDescriptor.ScopeKind.NON_STOCK, List.of()),
                        "cal_date", new IntegrityDateRange(FIRST, LAST),
                        Map.of("exchange", "SSE"), "calendar")));
        DataSource dataSource = mock(DataSource.class);
        IntegrityReadRepository repository = repository(dataSource, targetDefinition(), foreignDefinition);

        assertReason(() -> repository.withSnapshot(scope(), crossSource, budget(), 2, session -> null),
                INVALID_READ_REQUEST);
        verify(dataSource, never()).getConnection();
    }

    @Test
    void startsAndCleansUpOneReadOnlyRepeatableReadSnapshotAndClosesEscapedSession() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.isReadOnly()).thenReturn(false);
        when(connection.createStatement()).thenReturn(statement);
        IntegrityReadRepository repository = repository(dataSource, targetDefinition(), referenceDefinition());
        AtomicReference<IntegrityReadRepository.ReadSession> escaped = new AtomicReference<>();

        IntegrityScope actual = repository.withSnapshot(scope(), plan(), budget(), 2, session -> {
            escaped.set(session);
            return session.scope();
        });

        assertThat(actual.snapshotStartedAt()).isEqualTo(SNAPSHOT);
        assertThat(actual.acceptedAt()).isEqualTo(ACCEPTED);
        assertReason(() -> escaped.get().scan(validTargetRequest(), rows -> {}), READ_SESSION_CLOSED);
        InOrder order = inOrder(connection, statement);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        order.verify(connection).setReadOnly(true);
        order.verify(connection).setAutoCommit(false);
        order.verify(statement).execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
        order.verify(connection).rollback();
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        order.verify(connection).setReadOnly(false);
        order.verify(connection).setAutoCommit(true);
        order.verify(connection).close();
    }

    @Test
    void rejectsUnauthorizedRequestsBeforePreparingSqlAndPoisonsTheSession() throws Exception {
        Jdbc jdbc = jdbcWithEmptyPage();
        IntegrityReadRepository repository = repository(jdbc.dataSource, targetDefinition(), referenceDefinition());

        assertReason(() -> repository.withSnapshot(scope(), plan(), budget(), 2, session -> {
            IntegrityReadRequest wrongSymbol = new IntegrityReadRequest(TARGET_KEY, List.of("close"),
                    Map.of("ts_code", "000002.SZ"), "trade_date", new IntegrityDateRange(FIRST, LAST),
                    false, "target");
            try {
                session.scan(wrongSymbol, rows -> {});
            } catch (IntegrityReadException expected) {
                assertThat(expected.reasonCode()).isEqualTo(INVALID_READ_REQUEST);
            }
            return null;
        }), INVALID_READ_REQUEST);

        verify(jdbc.connection, never()).prepareStatement(anyString());
    }

    @Test
    void scansAnAuthorizedEmptyRequestRecordsItOnceAndReturnsImmutableHistory() throws Exception {
        Jdbc jdbc = jdbcWithEmptyPage();
        IntegrityReadRepository repository = repository(jdbc.dataSource, targetDefinition(), referenceDefinition());

        List<IntegrityReadRequest> requests = repository.withSnapshot(scope(), plan(), budget(), 2, session -> {
            session.scan(validTargetRequest(), rows -> { throw new AssertionError("empty scan must not callback"); });
            return session.readRequests();
        });

        assertThat(requests).containsExactly(new IntegrityReadRequest(TARGET_KEY,
                List.of("close", "trade_date"), Map.of("ts_code", "000001.SZ"), "trade_date",
                new IntegrityDateRange(FIRST, LAST), false, "target"));
        assertThatThrownBy(() -> requests.clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(jdbc.statement).setQueryTimeout(10);
        verify(jdbc.statement).close();
        verify(jdbc.resultSet).close();
    }

    @Test
    void consumerFailureIsSafeAndPreventsTheActionFromReturningSuccess() throws Exception {
        Jdbc jdbc = jdbcWithOneTargetRow();
        IntegrityReadRepository repository = repository(jdbc.dataSource, targetDefinition(), referenceDefinition());

        assertReason(() -> repository.withSnapshot(scope(), plan(), budget(), 2, session -> {
            try {
                session.scan(validTargetRequest(), rows -> { throw new IllegalStateException("secret consumer text"); });
            } catch (IntegrityReadException expected) {
                assertThat(expected.reasonCode()).isEqualTo(READ_FAILED);
                assertThat(expected.getMessage()).doesNotContain("secret consumer text");
            }
            return "must fail";
        }), READ_FAILED);
    }

    @Test
    void preservesNullableDatabaseValuesInImmutableRows() throws Exception {
        Jdbc jdbc = jdbc();
        when(jdbc.resultSet.next()).thenReturn(true, false);
        when(jdbc.resultSet.getBigDecimal(1)).thenReturn(null);
        when(jdbc.resultSet.getDate(2)).thenReturn(java.sql.Date.valueOf(FIRST));
        when(jdbc.resultSet.getString(3)).thenReturn("000001.SZ");
        IntegrityReadRepository repository = repository(jdbc.dataSource, targetDefinition(), referenceDefinition());

        Map<String, Object> row = repository.withSnapshot(scope(), plan(), budget(), 2, session -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            session.scan(validTargetRequest(), rows::addAll);
            return rows.getFirst();
        });

        assertThat(row).containsEntry("close", null).containsEntry("trade_date", FIRST);
        assertThatThrownBy(() -> row.put("close", BigDecimal.ZERO))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDatabaseDecimalsThatCannotFitTheDeclaredScaleBeforeDelivery() throws Exception {
        Jdbc jdbc = jdbc();
        when(jdbc.resultSet.next()).thenReturn(true, false);
        when(jdbc.resultSet.getBigDecimal(1)).thenReturn(new BigDecimal("1.0000000000000000001"));
        when(jdbc.resultSet.getDate(2)).thenReturn(java.sql.Date.valueOf(FIRST));
        when(jdbc.resultSet.getString(3)).thenReturn("000001.SZ");
        IntegrityReadRepository repository = repository(jdbc.dataSource, targetDefinition(), referenceDefinition());
        java.util.concurrent.atomic.AtomicBoolean delivered = new java.util.concurrent.atomic.AtomicBoolean();

        assertReason(() -> repository.withSnapshot(scope(), plan(), budget(), 2, session -> {
            session.scan(validTargetRequest(), rows -> delivered.set(true));
            return null;
        }), READ_FAILED);
        assertThat(delivered).isFalse();
    }

    @Test
    void nullCallbacksPoisonTheSessionEvenWhenTheActionCatchesTheFailure() throws Exception {
        Jdbc jdbc = jdbcWithEmptyPage();
        IntegrityReadRepository repository = repository(jdbc.dataSource, targetDefinition(), referenceDefinition());

        assertReason(() -> repository.withSnapshot(scope(), plan(), budget(), 2, session -> {
            try { session.scan(validTargetRequest(), null); }
            catch (IntegrityReadException ignored) { }
            return null;
        }), INVALID_READ_REQUEST);
    }

    @Test
    void rejectsAnExistingSpringTransactionBeforeUsingTheDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        IntegrityReadRepository repository = repository(dataSource, targetDefinition(), referenceDefinition());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertReason(() -> repository.withSnapshot(scope(), plan(), budget(), 2, session -> null),
                    INVALID_READ_REQUEST);
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verify(dataSource, never()).getConnection();
    }

    @Test
    void rejectsCrossThreadAndRecursiveSessionUseAndPoisonsTheAction() throws Exception {
        Jdbc crossThreadJdbc = jdbcWithEmptyPage();
        IntegrityReadRepository repository = repository(
                crossThreadJdbc.dataSource, targetDefinition(), referenceDefinition());
        assertReason(() -> repository.withSnapshot(scope(), plan(), budget(), 2, session -> {
            AtomicReference<Throwable> problem = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try { session.scan(validTargetRequest(), rows -> {}); }
                catch (Throwable exception) { problem.set(exception); }
            });
            thread.start();
            try { thread.join(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
            assertThat(problem.get()).isInstanceOfSatisfying(IntegrityReadException.class,
                    exception -> assertThat(exception.reasonCode()).isEqualTo(READ_SESSION_CLOSED));
            return null;
        }), READ_SESSION_CLOSED);

        Jdbc recursiveJdbc = jdbcWithOneTargetRow();
        IntegrityReadRepository recursive = repository(
                recursiveJdbc.dataSource, targetDefinition(), referenceDefinition());
        assertReason(() -> recursive.withSnapshot(scope(), plan(), budget(), 2, session -> {
            try {
                session.scan(validTargetRequest(), rows -> session.scan(validTargetRequest(), ignored -> {}));
            } catch (IntegrityReadException expected) {
                assertThat(expected.reasonCode()).isEqualTo(INVALID_READ_REQUEST);
            }
            return null;
        }), INVALID_READ_REQUEST);
    }

    @Test
    void checksTheDeadlineAfterReadingAndRollsBackSqlFailures() throws Exception {
        MutableClock clock = new MutableClock(SNAPSHOT);
        Jdbc expiredJdbc = jdbc();
        when(expiredJdbc.resultSet.next()).thenReturn(true).thenAnswer(invocation -> {
            clock.now = SNAPSHOT.plusSeconds(2);
            return false;
        });
        when(expiredJdbc.resultSet.getBigDecimal(1)).thenReturn(BigDecimal.ONE);
        when(expiredJdbc.resultSet.getDate(2)).thenReturn(java.sql.Date.valueOf(FIRST));
        when(expiredJdbc.resultSet.getString(3)).thenReturn("000001.SZ");
        IntegrityReadRepository expiring = new IntegrityReadRepository(expiredJdbc.dataSource,
                catalog(targetDefinition(), referenceDefinition()), clock);
        IntegrityReadBudget shortBudget = new IntegrityReadBudget(10, SNAPSHOT.plusSeconds(2), clock);
        assertReason(() -> expiring.withSnapshot(scope(), plan(), shortBudget, 2,
                session -> { session.scan(validTargetRequest(), rows -> {}); return null; }),
                UNIT_TIME_BUDGET_EXHAUSTED);
        verify(expiredJdbc.connection).rollback();

        Jdbc failedJdbc = jdbc();
        when(failedJdbc.statement.executeQuery()).thenThrow(new java.sql.SQLException("jdbc-url-secret"));
        IntegrityReadRepository failing = repository(failedJdbc.dataSource, targetDefinition(), referenceDefinition());
        assertThatThrownBy(() -> failing.withSnapshot(scope(), plan(), budget(), 2,
                session -> { session.scan(validTargetRequest(), rows -> {}); return null; }))
                .isInstanceOfSatisfying(IntegrityReadException.class, exception -> {
                    assertThat(exception.reasonCode()).isEqualTo(READ_FAILED);
                    assertThat(exception.getMessage()).doesNotContain("jdbc-url-secret");
                });
        verify(failedJdbc.connection).rollback();
        verify(failedJdbc.connection).setReadOnly(false);
        verify(failedJdbc.connection).setAutoCommit(true);
        verify(failedJdbc.connection).close();
    }

    @Test
    void samplesTheLatestDeadlineAfterPreparingAndBindingImmediatelyBeforeExecution() throws Exception {
        MutableClock prepareClock = new MutableClock(SNAPSHOT);
        Jdbc prepareJdbc = jdbcWithEmptyPage();
        when(prepareJdbc.connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            prepareClock.now = SNAPSHOT.plusSeconds(2);
            return prepareJdbc.statement;
        });
        IntegrityReadRepository prepareRepository = new IntegrityReadRepository(prepareJdbc.dataSource,
                catalog(targetDefinition(), referenceDefinition()), prepareClock);
        IntegrityReadBudget prepareBudget = new IntegrityReadBudget(
                10, SNAPSHOT.plusSeconds(3), prepareClock);

        prepareRepository.withSnapshot(scope(), plan(), prepareBudget, 2, session -> {
            session.scan(validTargetRequest(), rows -> {});
            return null;
        });

        verify(prepareJdbc.statement).setQueryTimeout(1);

        MutableClock bindClock = new MutableClock(SNAPSHOT);
        Jdbc bindJdbc = jdbcWithEmptyPage();
        org.mockito.Mockito.doAnswer(invocation -> {
            bindClock.now = SNAPSHOT.plusSeconds(3);
            return null;
        }).when(bindJdbc.statement).setString(anyInt(), anyString());
        IntegrityReadRepository bindRepository = new IntegrityReadRepository(bindJdbc.dataSource,
                catalog(targetDefinition(), referenceDefinition()), bindClock);
        IntegrityReadBudget bindBudget = new IntegrityReadBudget(10, SNAPSHOT.plusSeconds(3), bindClock);

        assertReason(() -> bindRepository.withSnapshot(scope(), plan(), bindBudget, 2, session -> {
            session.scan(validTargetRequest(), rows -> {});
            return null;
        }), UNIT_TIME_BUDGET_EXHAUSTED);
        verify(bindJdbc.statement, never()).executeQuery();
    }

    @Test
    void acceptsOnlyTheMinimalWholeWeekOrMonthExpansionAcrossMultiplePeriods() throws Exception {
        IntegrityScope multiWeek = new IntegrityScope(TARGET_KEY, "000001.SZ",
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 20), ACCEPTED, null);
        IntegrityDateRange week = new IntegrityDateRange(
                LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 25));
        withValidPlan(multiWeek, week);

        IntegrityScope multiMonth = new IntegrityScope(TARGET_KEY, "000001.SZ",
                LocalDate.of(2026, 1, 20), LocalDate.of(2026, 2, 10), ACCEPTED, null);
        IntegrityDateRange month = new IntegrityDateRange(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));
        withValidPlan(multiMonth, month);

        DataSource dataSource = mock(DataSource.class);
        IntegrityReadRepository repository = repository(dataSource, targetDefinition(), referenceDefinition());
        IntegrityDateRange extraWeek = new IntegrityDateRange(
                LocalDate.of(2025, 12, 22), LocalDate.of(2026, 1, 25));
        assertReason(() -> repository.withSnapshot(multiWeek, plan(extraWeek), budget(), 2, session -> null),
                INVALID_READ_REQUEST);
        verify(dataSource, never()).getConnection();
    }

    private static IntegrityReadRequest validTargetRequest() {
        return new IntegrityReadRequest(TARGET_KEY, List.of("close", "trade_date"), Map.of(),
                "trade_date", new IntegrityDateRange(FIRST, LAST), false, "target");
    }

    private static IntegrityReadPlan plan() {
        return plan(new IntegrityDateRange(FIRST, LAST));
    }

    private static IntegrityReadPlan plan(IntegrityDateRange range) {
        IntegrityDescriptor target = descriptor(TARGET_KEY, IntegrityDescriptor.ScopeKind.STOCK_DATE,
                List.of(new IntegrityDependency(REFERENCE_KEY, List.of("exchange", "cal_date"), "calendar")));
        IntegrityReadPlan.ReferencePermit reference = new IntegrityReadPlan.ReferencePermit(
                descriptor(REFERENCE_KEY, IntegrityDescriptor.ScopeKind.NON_STOCK, List.of()), "cal_date",
                range, Map.of("exchange", "SSE"), "calendar");
        return new IntegrityReadPlan(target, List.of(reference));
    }

    private static void withValidPlan(IntegrityScope scope, IntegrityDateRange range) throws Exception {
        Jdbc jdbc = jdbcWithEmptyPage();
        IntegrityReadRepository repository = repository(jdbc.dataSource, targetDefinition(), referenceDefinition());
        repository.withSnapshot(scope, plan(range), budget(), 2, session -> null);
    }

    private static IntegrityReadRepository repository(DataSource dataSource, DatasetDefinition... definitions) {
        return new IntegrityReadRepository(dataSource, catalog(definitions), Clock.fixed(SNAPSHOT, ZoneOffset.UTC));
    }

    private static DatasetCatalog catalog(DatasetDefinition... definitions) {
        try {
            Constructor<DatasetCatalog> constructor = DatasetCatalog.class.getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            return constructor.newInstance(List.of(definitions));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static IntegrityReadBudget budget() {
        return new IntegrityReadBudget(10, SNAPSHOT.plusSeconds(10), Clock.fixed(SNAPSHOT, ZoneOffset.UTC));
    }

    private static IntegrityScope scope() {
        return new IntegrityScope(TARGET_KEY, "000001.SZ", FIRST, LAST, ACCEPTED, null);
    }

    private static IntegrityDescriptor descriptor(DatasetKey key, IntegrityDescriptor.ScopeKind kind,
            List<IntegrityDependency> dependencies) {
        List<IntegrityRuleDescriptor> rules = kind == IntegrityDescriptor.ScopeKind.NON_STOCK ? List.of() : List.of(
                new IntegrityRuleDescriptor("fixture.coverage", "1", "coverage",
                        IntegrityRuleDescriptor.Dimension.COVERAGE, List.of("ts_code"), List.of(), "coverage"));
        return new IntegrityDescriptor(key, kind, kind == IntegrityDescriptor.ScopeKind.NON_STOCK ? null : "ts_code",
                kind == IntegrityDescriptor.ScopeKind.STOCK_DATE ? "trade_date" : null, "date",
                ZoneId.of("Asia/Shanghai"), "1", dependencies, rules, List.of());
    }

    private static DatasetDefinition targetDefinition() {
        return definition(TARGET_KEY, BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date"), List.of(
                column("ts_code", LogicalType.STRING, false, 0, 32, null, null),
                column("trade_date", LogicalType.DATE, false, 1, null, null, null),
                column("close", LogicalType.DECIMAL, true, 2, null, 30, 18)));
    }

    private static DatasetDefinition referenceDefinition() {
        return definition(REFERENCE_KEY, BusinessKeyMode.COMPOSITE, List.of("exchange", "cal_date"), List.of(
                column("exchange", LogicalType.STRING, false, 0, 8, null, null),
                column("cal_date", LogicalType.DATE, false, 1, null, null, null)));
    }

    private static DatasetDefinition definition(DatasetKey key, BusinessKeyMode mode, List<String> businessKey,
            List<ColumnDefinition> columns) {
        return new DatasetDefinition(key, key.apiName().value(), "test", QueryMode.trade_date, List.of(),
                TableName.from(key), columns, new BusinessKeyDefinition(mode, businessKey), List.of(), null, 500);
    }

    private static ColumnDefinition column(String name, LogicalType type, boolean nullable, int order,
            Integer length, Integer precision, Integer scale) {
        return new ColumnDefinition(name, name, type, nullable, order, length, precision, scale, List.of(), false);
    }

    private static DatasetKey key(String api) {
        return DatasetKey.of(PluginId.of("reader"), ApiName.of(api));
    }

    private static void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(IntegrityReadException.class,
                exception -> assertThat(exception.reasonCode()).isEqualTo(reason));
    }

    private static Jdbc jdbcWithEmptyPage() throws Exception {
        Jdbc jdbc = jdbc();
        when(jdbc.resultSet.next()).thenReturn(false);
        return jdbc;
    }

    private static Jdbc jdbcWithOneTargetRow() throws Exception {
        Jdbc jdbc = jdbc();
        when(jdbc.resultSet.next()).thenReturn(true, false);
        when(jdbc.resultSet.getBigDecimal(1)).thenReturn(new BigDecimal("1.000000000000000001"));
        when(jdbc.resultSet.getDate(2)).thenReturn(java.sql.Date.valueOf(FIRST));
        when(jdbc.resultSet.getString(3)).thenReturn("000001.SZ");
        when(jdbc.resultSet.getDate(4)).thenReturn(java.sql.Date.valueOf(FIRST));
        return jdbc;
    }

    private static Jdbc jdbc() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement snapshot = mock(Statement.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.isReadOnly()).thenReturn(false);
        when(connection.createStatement()).thenReturn(snapshot);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        return new Jdbc(dataSource, connection, statement, resultSet);
    }

    private record Jdbc(DataSource dataSource, Connection connection,
            PreparedStatement statement, ResultSet resultSet) {}

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
