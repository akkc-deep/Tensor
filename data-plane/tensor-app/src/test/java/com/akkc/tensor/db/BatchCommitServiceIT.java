package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.*;

import com.akkc.tensor.config.ApplicationConfiguration;
import com.akkc.tensor.core.adapter.*;
import com.akkc.tensor.core.catalog.*;
import com.akkc.tensor.core.download.*;
import com.akkc.tensor.core.download.RecoveryUnitProcessor.ReadyUnit;
import com.akkc.tensor.core.persistence.*;
import com.akkc.tensor.core.retry.*;
import com.akkc.tensor.core.retry.RetryTaskRepository.*;
import com.akkc.tensor.core.validation.ParameterValidator;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class BatchCommitServiceIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static final DatasetKey DATASET = new DatasetKey(new PluginId("tushare_pro"), new ApiName("daily"));
    static final Instant NOW = Instant.parse("2026-09-09T01:02:03.123456Z");
    static final Map<String, Object> PARAMS = Map.of("start_date", "20260901", "end_date", "20260910");
    static DataSource dataSource;
    static JdbcTemplate jdbc;
    static DatasetDefinition definition;
    GenericDatasetAdapter adapter;
    DownloadParameterConverter converter = new DownloadParameterConverter(new ParameterValidator());
    RecoveryUnitProcessor processor;
    DatasetLockManager locks;
    RetryTaskStorageService storage;
    BatchCommitService service;
    DataSourceTransactionManager manager;
    ApiDescriptor api;

    @BeforeAll static void migrate() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var d = new DatasetDefinitionLoader().loadAll(new PathMatchingResourcePatternResolver(),
                "classpath:datasets/tushare_pro/daily.yaml").getFirst();
        definition = new DatasetDefinition(d.datasetKey(), d.displayName(), d.category(), d.queryMode(),
                d.parameters(), d.tableName(), d.columns(), d.businessKey(), d.filters(), d.fixedColumn(), 2);
    }

    @BeforeEach void prepare() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_cleanup");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_business");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_finish");
        jdbc.update("DELETE FROM tensor_download_task_item");
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM tushare_pro__daily");
        var policy = com.akkc.tensor.test.DownloadPolicies.tradeRange();
        api = new ApiDescriptor(DATASET.apiName(), "Controlled", "Controlled", QueryMode.trade_date,
                DownloadParameterProjection.project(definition.parameters(), policy), policy, definition.parameters());
        configure(dataSource);
    }

    void configure(DataSource source) {
        var config = new ApplicationConfiguration();
        var adapters = config.tensorDatasetAdapters(List.of(definition),
                new DefaultListableBeanFactory().getBeanProvider(com.akkc.tensor.plugin.api.DatasetAdapter.class));
        var catalog = config.datasetCatalog(adapters, source);
        adapter = (GenericDatasetAdapter) config.adapterRegistry(adapters, catalog).find(DATASET).orElseThrow();
        assertThat(catalog.find(DATASET)).contains(definition);
        processor = new RecoveryUnitProcessor(adapter, converter, new BusinessKeyExtractor(), new BusinessContentCodec());
        locks = config.datasetLockManager();
        var template = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        var repository = config.retryTaskRepository(template, config.taskParametersJson());
        var persistence = config.persistenceService(catalog, locks, config.existingKeyRepository(template),
                config.genericUpsertRepository(template), manager);
        storage = config.retryTaskStorageService(repository, manager, Clock.fixed(NOW.minusSeconds(60), ZoneOffset.UTC));
        service = config.batchCommitService(persistence, repository, config.parameterValidator(), manager, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(service, "persistence")).isSameAs(persistence);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(service, "tasks")).isSameAs(repository);
        assertThat(((TransactionTemplate) org.springframework.test.util.ReflectionTestUtils.getField(service, "transactions")).getTransactionManager()).isSameAs(manager);
        assertThat(((TransactionTemplate) org.springframework.test.util.ReflectionTestUtils.getField(persistence, "transactionTemplate")).getTransactionManager()).isSameAs(manager);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(persistence, "datasetLockManager")).isSameAs(locks);
        assertThat(manager.getDataSource()).isSameAs(source);
    }

    @Test void deleteFailureRollsBackEveryBusinessGroupAndPreservesPriorCommitAndOldRow() {
        var index = new CommittedKeyIndex(DATASET);
        var a = ready(index, date("2026-09-07"), List.of(row("000001.SZ", "20260907", "1")));
        assertThat(service.commitInitial(a)).isInstanceOf(BatchCommitService.Committed.class);
        index.confirmCommitted(a);
        var old = ready(new CommittedKeyIndex(DATASET), date("2026-09-03"), List.of(row("000001.SZ", "20260903", "8")));
        assertThat(service.commitInitial(old)).isInstanceOf(BatchCommitService.Committed.class);
        var previous = business();
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        var task = storage.find(item.taskId()).orElseThrow();
        var b = ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "9"),
                row("000002.SZ", "20260903", "2"), row("600000.SH", "20260903", "3")));
        jdbc.execute("CREATE TRIGGER fail_cleanup BEFORE DELETE ON tensor_download_task_item "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled delete failure'");

        assertThat(service.commitRetry(item, b)).isEqualTo(new BatchCommitService.RolledBack(item.selector(), false));

        assertThat(business()).isEqualTo(previous);
        assertThat(storage.find(item.taskId())).contains(task);
        assertThat(ready(index, item.selector(), List.of(row("000002.SZ", "20260903", "2"))).batch().rows()).hasSize(1);
    }


    @Test void finalBusinessGroupFailureNeedsConfirmedIndependentReasonSaveBeforeNextUnit() {
        var index = new CommittedKeyIndex(DATASET);
        var a = ready(index, date("2026-09-07"), List.of(row("000001.SZ", "20260907", "1")));
        index.confirmCommitted(committed(service.commitInitial(a), 1, 1, 0, false, a));
        var old = ready(new CommittedKeyIndex(DATASET), date("2026-09-03"), List.of(row("000001.SZ", "20260903", "8")));
        committed(service.commitInitial(old), 1, 1, 0, false, old);
        var previous = business();
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        var b = ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "9"),
                row("000002.SZ", "20260903", "2"), row("600000.SH", "20260903", "3")));
        jdbc.execute("CREATE TRIGGER fail_business BEFORE INSERT ON tushare_pro__daily FOR EACH ROW "
                + "BEGIN IF NEW.ts_code='600000.SH' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='last group'; END IF; END");
        assertThat(service.commitRetry(item, b)).isEqualTo(new BatchCommitService.RolledBack(item.selector(), false));
        assertThat(business()).isEqualTo(previous);
        assertThat(storage.find(item.taskId()).orElseThrow().items().getFirst().errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
        jdbc.execute("DROP TRIGGER fail_business");
        assertThat(storage.updateReason(item, ErrorCode.PERSISTENCE_FAILED).key()).isEqualTo(item);
        var c = ready(index, date("2026-09-08"), List.of(row("000003.SZ", "20260908", "4")));
        index.confirmCommitted(committed(service.commitInitial(c), 1, 1, 0, false, c));
        assertThat(business()).hasSize(3);
        assertThat(storage.find(item.taskId()).orElseThrow().items().getFirst().errorCode()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
    }

    @Test void reasonSaveFailureStopsDriverBeforeC() {
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        jdbc.execute("CREATE TRIGGER fail_business BEFORE INSERT ON tushare_pro__daily FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='business'");
        var b = ready(new CommittedKeyIndex(DATASET), item.selector(), List.of(row("000001.SZ", "20260903", "1")));
        assertThat(service.commitRetry(item, b)).isEqualTo(new BatchCommitService.RolledBack(item.selector(), false));
        jdbc.execute("CREATE TRIGGER fail_finish BEFORE UPDATE ON tensor_download_task FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='reason save'");
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> {
            storage.updateReason(item, ErrorCode.PERSISTENCE_FAILED);
            calls.incrementAndGet();
            service.commitInitial(ready(new CommittedKeyIndex(DATASET), date("2026-09-07"), List.of()));
        }).isInstanceOfSatisfying(TensorException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED));
        assertThat(calls).hasValue(0);
        assertThat(storage.find(item.taskId()).orElseThrow().items().getFirst().errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
    }

    @Test void touchAndLastHeaderDeleteFailuresRestoreBusinessDeletedItemAndHeader() {
        for (boolean last : List.of(false, true)) {
            var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
            if (!last) storage.append(item.taskId(), failure(date("2026-09-07")));
            var previous = storage.find(item.taskId()).orElseThrow();
            jdbc.execute("CREATE TRIGGER fail_finish BEFORE " + (last ? "DELETE" : "UPDATE")
                    + " ON tensor_download_task FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='finish'");
            var ready = ready(new CommittedKeyIndex(DATASET), item.selector(), List.of(row("000001.SZ", "20260903", "1")));
            assertThat(service.commitRetry(item, ready)).isEqualTo(new BatchCommitService.RolledBack(item.selector(), false));
            assertThat(business()).isEmpty();
            assertThat(storage.find(item.taskId())).contains(previous);
            jdbc.execute("DROP TRIGGER fail_finish");
        }
    }

    @Test void sameClockTouchWithChangedRowsDriverSettingCanReturnZeroAndStillCommit() {
        var changedRows = new DriverManagerDataSource(MYSQL.getJdbcUrl() + (MYSQL.getJdbcUrl().contains("?") ? "&" : "?") + "useAffectedRows=true", MYSQL.getUsername(), MYSQL.getPassword());
        configure(changedRows);
        var repository = new RetryTaskRepository(new JdbcTemplate(changedRows), new TaskParametersJson());
        storage = new RetryTaskStorageService(repository, manager, Clock.fixed(NOW, ZoneOffset.UTC));
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        storage.append(item.taskId(), failure(date("2026-09-07")));
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            repository.lockTask(item.taskId());
            assertThat(repository.touchTask(item.taskId(), NOW.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))).isZero();
        });
        var ready = ready(new CommittedKeyIndex(DATASET), item.selector(), List.of());
        committed(service.commitRetry(item, ready), 0, 0, 0, false, ready);
        assertThat(storage.find(item.taskId()).orElseThrow().items()).hasSize(1);
    }

    @Test void retryUpdatesOnlyTimestampUntilFinalItemAndReturnsDistinctInsertUpdateCounts() {
        var old = ready(new CommittedKeyIndex(DATASET), date("2026-09-03"), List.of(row("000001.SZ", "20260903", "8")));
        committed(service.commitInitial(old), 1, 1, 0, false, old);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tensor_download_task", Integer.class)).isZero();
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        var other = storage.append(item.taskId(), failure(date("2026-09-07"))).key();
        var otherTask = storage.create(DATASET, PARAMS, failure(item.selector())).key();
        var before = storage.find(item.taskId()).orElseThrow().header();
        var index = new CommittedKeyIndex(DATASET);
        var ready = ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "9"),
                row("000002.SZ", "20260903", "2"), row("000002.SZ", "20260903", "2.0")));
        index.confirmCommitted(committed(service.commitRetry(item, ready), 3, 1, 1, false, ready));
        var after = storage.find(item.taskId()).orElseThrow();
        assertThat(after.items()).extracting(i -> i.key()).containsExactly(other);
        assertThat(after.header().taskParams()).isEqualTo(before.taskParams());
        assertThat(after.header().createdAt()).isEqualTo(before.createdAt());
        assertThat(after.header().updatedAt()).isEqualTo(Instant.parse("2026-09-09T01:02:03.123Z"));
        assertThat(storage.find(otherTask.taskId())).isPresent();
        var empty = ready(index, other.selector(), List.of());
        index.confirmCommitted(committed(service.commitRetry(other, empty), 0, 0, 0, false, empty));
        assertThat(storage.find(item.taskId())).isEmpty();
        assertThatThrownBy(() -> index.confirmCommitted(ready)).isInstanceOf(IllegalStateException.class);
    }

    @Test void emptyAndSameRoundDuplicateTicketsKeepDifferentSourceCountsAndOldBusinessColumns() {
        var index = new CommittedKeyIndex(DATASET);
        var rows = List.of(row("000001.SZ", "20260903", "1"));
        var first = ready(index, date("2026-09-03"), rows);
        index.confirmCommitted(committed(service.commitInitial(first), 1, 1, 0, false, first));
        var previous = business();
        for (boolean duplicate : List.of(false, true)) {
            var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
            var ticket = ready(index, item.selector(), duplicate ? rows : List.of());
            assertThat(ticket.batch().rows()).isEmpty();
            index.confirmCommitted(committed(service.commitRetry(item, ticket), duplicate ? 1 : 0, 0, 0, false, ticket));
            assertThat(storage.find(item.taskId())).isEmpty();
            var initial = ready(index, item.selector(), duplicate ? rows : List.of());
            index.confirmCommitted(committed(service.commitInitial(initial), duplicate ? 1 : 0, 0, 0, false, initial));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM tensor_download_task_item", Integer.class)).isZero();
            assertThat(business()).isEqualTo(previous);
        }
    }

    @Test void missingAndMismatchedBindingsDoNoBusinessSqlAndNeverRecreateItems() {
        var probe = new Probe(dataSource);
        configure(probe);
        var ticket = ready(new CommittedKeyIndex(DATASET), date("2026-09-03"), List.of(row("000001.SZ", "20260903", "1")));
        var missing = new ItemKey(UUID.randomUUID(), ticket.selector());
        probe.reset();
        assertCode(() -> service.commitRetry(missing, ticket), ErrorCode.RETRY_TASK_NOT_FOUND);
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-07"))).key();
        probe.reset();
        assertCode(() -> service.commitRetry(item, ticket), ErrorCode.RETRY_TASK_INVALID);
        assertThat(probe.events).isEmpty();
        assertCode(() -> service.commitRetry(new ItemKey(item.taskId(), ticket.selector()), ticket), ErrorCode.RETRY_TASK_NOT_FOUND);
        var wrong = storage.create(new DatasetKey(DATASET.pluginId(), new ApiName("weekly")), PARAMS, failure(ticket.selector())).key();
        assertCode(() -> service.commitRetry(wrong, ticket), ErrorCode.RETRY_TASK_INVALID);
        assertThat(probe.events).noneMatch(e -> e.sql.contains("tushare_pro__daily"));
        assertThat(business()).isEmpty();
    }

    @Test void wholeCalendarDecisionProjectsDatesWithoutDroppingMarketsAndPreservesBusiness() {
        var seed = ready(new CommittedKeyIndex(DATASET), date("2026-09-03"), List.of(row("000001.SZ", "20260903", "8")));
        committed(service.commitInitial(seed), 1, 1, 0, false, seed);
        var previous = business();
        var first = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        var later = storage.append(first.taskId(), failure(date("2026-09-07"))).key();
        var decision = decision(PARAMS, Set.of(day(3), day(7)), Set.of(day(7)));
        assertThat(service.commitClosedRetry(DATASET, api, first, decision))
                .isEqualTo(new BatchCommitService.Committed(first.selector(), 0, new WriteCounts(0, 0), false));
        assertThat(storage.find(first.taskId()).orElseThrow().items()).extracting(i -> i.key()).containsExactly(later);
        assertCode(() -> service.commitClosedRetry(DATASET, api, later, decision), ErrorCode.CALENDAR_UNCONFIRMED);
        assertThat(business()).isEqualTo(previous);
    }

    @Test void closedCalendarRejectsMissingDatesWrongConditionsAndOpenNecessaryMarketBeforeDeleting() {
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        var previous = storage.find(item.taskId());
        for (var decision : List.of(decision(PARAMS, Set.of(day(7)), Set.of()),
                decision(PARAMS, Set.of(day(3)), Set.of(day(3))),
                decision(Map.of("exchange", "OTHER"), Set.of(day(3)), Set.of()),
                decision(Map.of("start_date", "20260902", "end_date", "20260910"), Set.of(day(3)), Set.of()))) {
            assertCode(() -> service.commitClosedRetry(DATASET, api, item, decision), ErrorCode.CALENDAR_UNCONFIRMED);
            assertThat(storage.find(item.taskId())).isEqualTo(previous);
        }
        assertCode(() -> service.commitClosedRetry(DATASET, api, item, null), ErrorCode.CALENDAR_UNCONFIRMED);
        var scope = new CalendarScope(PARAMS, Set.of(day(3), day(7)));
        assertThatThrownBy(() -> new CalendarDecision(scope, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarDecision(scope, Map.of("SSE", Map.of(day(3), false))))
                .isInstanceOf(IllegalArgumentException.class);
        var later = storage.append(item.taskId(), failure(date("2026-09-07"))).key();
        previous = storage.find(item.taskId());
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
            var incomplete = new CalendarDecision(scope, Map.of("SSE", Map.of(day(3), false), "SZSE", Map.of(day(3), false)));
            calls.incrementAndGet();
            service.commitClosedRetry(DATASET, api, item, incomplete);
        }).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
        assertThat(storage.find(item.taskId())).isEqualTo(previous);
    }

    @Test void closedRangeRequiresEntireSavedRangeAndSupportedSelectorMode() {
        var rangeApi = rangeApi(false);
        var selector = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.RANGE, "2026-09-03/2026-09-07");
        var item = storage.create(DATASET, PARAMS, failure(selector)).key();
        assertCode(() -> service.commitClosedRetry(DATASET, rangeApi, item,
                decision(PARAMS, Set.of(day(3), day(7)), Set.of())), ErrorCode.CALENDAR_UNCONFIRMED);
        assertThat(service.commitClosedRetry(DATASET, rangeApi, item,
                decision(PARAMS, Set.of(day(3), day(4), day(5), day(6), day(7)), Set.of())))
                .isEqualTo(new BatchCommitService.Committed(selector, 0, new WriteCounts(0, 0), false));
        assertThat(storage.find(item.taskId())).isEmpty();
        for (var invalid : List.of(new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.NONE, ""),
                new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.MONTH, "2026-09"))) {
            var key = storage.create(DATASET, PARAMS, failure(invalid)).key();
            assertCode(() -> service.commitClosedRetry(DATASET, api, key, decision(PARAMS, Set.of(day(3)), Set.of())), ErrorCode.RETRY_TASK_INVALID);
            assertThat(storage.find(key.taskId())).isPresent();
        }
        var original = new ApiDescriptor(DATASET.apiName(), "Original", "Original", QueryMode.snapshot,
                definition.parameters(), com.akkc.tensor.test.DownloadPolicies.original(), definition.parameters());
        var key = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        assertCode(() -> service.commitClosedRetry(DATASET, original, key, decision(PARAMS, Set.of(day(3)), Set.of())), ErrorCode.RETRY_TASK_INVALID);
    }

    @Test void physicalConnectionAndSqlOrderHaveOneCommitAcrossAllGroupsAndCleanup() {
        var probe = new Probe(dataSource);
        configure(probe);
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        storage.append(item.taskId(), failure(date("2026-09-07")));
        var ticket = ready(new CommittedKeyIndex(DATASET), item.selector(), List.of(row("000001.SZ", "20260903", "1"),
                row("000002.SZ", "20260903", "2"), row("600000.SH", "20260903", "3")));
        probe.reset();
        committed(service.commitRetry(item, ticket), 3, 3, 0, false, ticket);
        assertThat(probe.commits).hasValue(1);
        assertThat(probe.rollbacks).hasValue(0);
        assertThat(probe.events.stream().map(e -> e.connection).distinct()).hasSize(1);
        assertThat(probe.events).extracting(e -> e.phase).containsExactly("query", "query", "query", "batch", "batch", "update", "query", "update");
        assertThat(probe.events.getFirst().sql).contains("FOR UPDATE");
        assertThat(probe.events.get(2).sql).contains("tushare_pro__daily");
        assertThat(probe.events.get(5).sql).contains("target_type=? AND target_value=? AND time_type=? AND time_value=?");
        assertThat(probe.events.getLast().sql).startsWith("UPDATE tensor_download_task SET updated_at");
        probe.reset();
        var initial = ready(new CommittedKeyIndex(DATASET), date("2026-09-08"), List.of(row("000001.SZ", "20260908", "1")));
        committed(service.commitInitial(initial), 1, 1, 0, false, initial);
        assertThat(probe.commits).hasValue(1);
        assertThat(probe.events).noneMatch(e -> e.sql.contains("tensor_download_task"));
    }

    @Test void commitResponseLossBeforeOrAfterDelegateIsAlwaysUnknownEvenIfRollbackThenSucceeds() {
        for (String mode : List.of("beforeCommit", "afterCommit", "afterCommitRollback")) {
            var probe = new Probe(dataSource);
            configure(probe);
            var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
            var index = new CommittedKeyIndex(DATASET);
            var ticket = ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "1")));
            probe.mode = mode;
            manager.setRollbackOnCommitFailure(mode.equals("afterCommitRollback"));
            assertThat(service.commitRetry(item, ticket)).isEqualTo(new BatchCommitService.Unconfirmed(item.selector()));
            probe.mode = "normal";
            if (mode.equals("beforeCommit")) {
                // Spring restores auto-commit during cleanup: MySQL commits even though delegate.commit was never called.
                assertThat(probe.physicalCalls).containsSubsequence("commit:beforeCommit", "setAutoCommit:true");
            }
            assertThat(business()).hasSize(1);
            assertThat(storage.find(item.taskId())).isEmpty();
            if (mode.equals("afterCommitRollback")) assertThat(probe.rollbacks).hasValue(1);
            assertThat(ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "1"))).batch().rows()).hasSize(1);
            jdbc.update("DELETE FROM tushare_pro__daily");
        }
    }

    @Test void rollbackFailureIsUnknownAndDoesNotUpdateOriginalReasonOrContinue() {
        var probe = new Probe(dataSource);
        configure(probe);
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        var before = storage.find(item.taskId());
        jdbc.execute("CREATE TRIGGER fail_cleanup BEFORE DELETE ON tensor_download_task_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SENTINEL'");
        var index = new CommittedKeyIndex(DATASET);
        var ticket = ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "1")));
        probe.mode = "rollbackFailure";
        assertThat(service.commitRetry(item, ticket)).isEqualTo(new BatchCommitService.Unconfirmed(item.selector()));
        probe.mode = "normal";
        assertThat(storage.find(item.taskId())).isEqualTo(before);
        assertThat(ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "1"))).batch().rows()).hasSize(1);
    }

    @Test void managerFailureAfterKnownCommitKeepsCountsAndStopsDriver() {
        var config = new ApplicationConfiguration();
        var repository = new RetryTaskRepository(jdbc, new TaskParametersJson());
        var catalog = new DatasetStartupValidator(List.of(definition), new SchemaInspector(dataSource)).validate();
        PlatformTransactionManager wrapper = new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition d) { return manager.getTransaction(d); }
            public void rollback(TransactionStatus s) { manager.rollback(s); }
            public void commit(TransactionStatus s) { manager.commit(s); if (s.isNewTransaction()) throw new TransactionSystemException("SENTINEL"); }
        };
        var persistence = new PersistenceService(catalog, locks, new ExistingKeyRepository(jdbc), new GenericUpsertRepository(jdbc), wrapper);
        var wrapped = new BatchCommitService(persistence, repository, converter, wrapper, Clock.fixed(NOW, ZoneOffset.UTC));
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        var index = new CommittedKeyIndex(DATASET);
        var ticket = ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "1")));
        var outcome = (BatchCommitService.Committed) wrapped.commitRetry(item, ticket);
        assertThat(outcome).isEqualTo(new BatchCommitService.Committed(item.selector(), 1, new WriteCounts(1, 0), true));
        index.confirmCommitted(ticket);
        int nextCalls = 0;
        if (!outcome.stopExecution()) nextCalls++;
        assertThat(nextCalls).isZero();
        assertThat(business()).hasSize(1);
        assertThat(storage.find(item.taskId())).isEmpty();
    }

    @Test void startConnectionFailureHasNoUnitSqlAndSupportsOrRealOuterTransactionsAreRejected() {
        var probe = new Probe(dataSource);
        configure(probe);
        var ticket = ready(new CommittedKeyIndex(DATASET), date("2026-09-03"), List.of(row("000001.SZ", "20260903", "1")));
        probe.reset();
        probe.mode = "startFailure";
        assertThat(service.commitInitial(ticket)).isEqualTo(new BatchCommitService.Unavailable(ticket.selector()));
        assertThat(probe.events).isEmpty();
        probe.mode = "normal";
        for (int propagation : List.of(TransactionDefinition.PROPAGATION_SUPPORTS, TransactionDefinition.PROPAGATION_REQUIRED)) {
            var tx = new TransactionTemplate(manager);
            tx.setPropagationBehavior(propagation);
            tx.executeWithoutResult(s -> assertThatThrownBy(() -> service.commitInitial(ticket))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Recovery unit commit must not join an existing transaction"));
        }
        assertThat(business()).isEmpty();
    }




    @Test void sqlConnectionFailureWithConfirmedRollbackStopsWithoutSavingReasonOrCounting() {
        var probe = new Probe(dataSource);
        configure(probe);
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        var before = storage.find(item.taskId());
        var index = new CommittedKeyIndex(DATASET);
        var ticket = ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "1")));
        probe.reset(); probe.mode = "sqlConnectionFailure";
        var result = service.commitRetry(item, ticket);
        assertThat(result).isEqualTo(new BatchCommitService.RolledBack(item.selector(), true));
        assertThat(result.toString()).doesNotContain("SENTINEL", "INSERT", "08006");
        assertThat(probe.rollbacks).hasValue(1);
        assertThat(probe.commits).hasValue(0);
        probe.mode = "normal";
        assertThat(storage.find(item.taskId())).isEqualTo(before);
        assertThat(business()).isEmpty();
        assertThat(ready(index, item.selector(), List.of(row("000001.SZ", "20260903", "1"))).batch().rows()).hasSize(1);
    }

    @Test void calendarPreflightFailuresExecuteZeroSqlAndEmptyRetryDoesNotAcquireDatasetLock() throws Exception {
        var probe = new Probe(dataSource);
        configure(probe);
        var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
        probe.reset();
        for (var decision : List.of(decision(PARAMS, Set.of(day(7)), Set.of()),
                decision(PARAMS, Set.of(day(3)), Set.of(day(3))))) {
            assertCode(() -> service.commitClosedRetry(DATASET, api, item, decision), ErrorCode.CALENDAR_UNCONFIRMED);
        }
        var wrongApi = new ApiDescriptor(new ApiName("weekly"), api.displayName(), api.category(), api.queryMode(),
                api.parameters(), api.downloadPolicy(), api.sourceParameters());
        assertCode(() -> service.commitClosedRetry(DATASET, wrongApi, item, decision(PARAMS, Set.of(day(3)), Set.of())), ErrorCode.RETRY_TASK_INVALID);
        assertThat(probe.events).isEmpty();
        assertThat(probe.commits).hasValue(0);
        assertThat(probe.rollbacks).hasValue(0);
        try (var threads = Executors.newSingleThreadExecutor()) {
            var held = locks.acquire(DATASET);
            try {
                assertThat(threads.submit(() -> {
                    var empty = ready(new CommittedKeyIndex(DATASET), item.selector(), List.of());
                    return service.commitRetry(item, empty);
                }).get(5, TimeUnit.SECONDS)).isEqualTo(new BatchCommitService.Committed(item.selector(), 0, new WriteCounts(0, 0), false));
            } finally { held.unlock(); }
        }
        assertThat(probe.events).noneMatch(e -> e.sql.contains("tushare_pro__daily"));
    }

    @Test void wholeRequestRangeDeletesOnlyItsCompleteKey() {
        api = rangeApi(false);
        var selected = new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.RANGE, "2026-09-03/2026-09-07");
        var item = storage.create(DATASET, PARAMS, failure(selected)).key();
        var otherDate = storage.append(item.taskId(), failure(date("2026-09-07"))).key();
        var otherRange = storage.append(item.taskId(), failure(new RecoverySelector(RecoverySelector.TargetType.REQUEST,
                "", RecoverySelector.TimeType.RANGE, "2026-09-01/2026-09-10"))).key();
        var otherTask = storage.create(DATASET, PARAMS, failure(selected)).key();
        var ticket = ready(new CommittedKeyIndex(DATASET), selected,
                List.of(row("000001.SZ", "20260903", "1"), row("000001.SZ", "20260907", "2")));
        committed(service.commitRetry(item, ticket), 2, 2, 0, false, ticket);
        assertThat(storage.find(item.taskId()).orElseThrow().items()).extracting(i -> i.key()).containsExactlyInAnyOrder(otherDate, otherRange);
        assertThat(storage.find(otherTask.taskId()).orElseThrow().items()).extracting(i -> i.key()).containsExactly(otherTask);
    }

    @Test void registeredAuditAdapterCommitsExactStockDateAndFullRangeWithoutChangingNeighbors() {
        var config = new ApplicationConfiguration();
        var audit = new DatasetDefinitionLoader().loadAll(new PathMatchingResourcePatternResolver(),
                "classpath:datasets/tushare_pro/fina_audit.yaml").getFirst();
        jdbc.update("DELETE FROM tushare_pro__fina_audit");
        var adapters = config.tensorDatasetAdapters(List.of(audit),
                new DefaultListableBeanFactory().getBeanProvider(com.akkc.tensor.plugin.api.DatasetAdapter.class));
        var catalog = config.datasetCatalog(adapters, dataSource);
        var auditAdapter = (GenericDatasetAdapter) config.adapterRegistry(adapters, catalog).find(audit.datasetKey()).orElseThrow();
        var auditProcessor = new RecoveryUnitProcessor(auditAdapter, converter, new BusinessKeyExtractor(), new BusinessContentCodec());
        var repository = config.retryTaskRepository(jdbc, config.taskParametersJson());
        var persistence = config.persistenceService(catalog, locks, config.existingKeyRepository(jdbc), config.genericUpsertRepository(jdbc), manager);
        var auditService = config.batchCommitService(persistence, repository, config.parameterValidator(), manager, Clock.fixed(NOW, ZoneOffset.UTC));
        for (var time : List.of(RecoverySelector.TimeType.DATE, RecoverySelector.TimeType.RANGE)) {
            var base = com.akkc.tensor.test.DownloadPolicies.original();
            boolean range = time == RecoverySelector.TimeType.RANGE;
            var policy = new DownloadPolicy(DownloadPolicy.Mode.ANN_DATE_RANGE, DownloadPolicy.DateSemantic.ANN_DATE,
                    "Controlled audit", null, new DownloadPolicy.Limits(31),
                    range ? DownloadPolicy.SourceRequestMode.RANGE : DownloadPolicy.SourceRequestMode.DATE,
                    range ? null : "ann_date", DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                    range ? DownloadPolicy.BatchPlanning.SOURCE_RANGE : DownloadPolicy.BatchPlanning.SINGLE_DATE,
                    new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date", time, true, List.of("controlled")),
                    base.completenessPolicy(), null, base.evidenceRefs());
            var auditApi = new ApiDescriptor(audit.datasetKey().apiName(), "Audit", "Audit", audit.queryMode(),
                    DownloadParameterProjection.project(audit.parameters(), policy), policy, audit.parameters());
            String value = range ? "2026-09-03/2026-09-07" : "2026-09-03";
            var selected = new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ", time, value);
            var item = storage.create(audit.datasetKey(), PARAMS, failure(selected)).key();
            var otherStock = storage.append(item.taskId(), failure(new RecoverySelector(RecoverySelector.TargetType.STOCK,
                    "600000.SH", time, value))).key();
            var otherDate = storage.append(item.taskId(), failure(new RecoverySelector(RecoverySelector.TargetType.STOCK,
                    "000001.SZ", RecoverySelector.TimeType.DATE, "2026-09-07"))).key();
            var otherTask = storage.create(audit.datasetKey(), PARAMS, failure(selected)).key();
            List<List<Object>> rows = new ArrayList<>();
            rows.add(Arrays.asList("000001.SZ", "20260903", "20260630", "Approved", "1", null, null));
            if (range) rows.add(Arrays.asList("000001.SZ", "20260907", "20260630", "Approved", "2", null, null));
            var batch = new FetchBatch(converter.mapRetry(auditApi, PARAMS, selected).sourceParams().values(), policy.recoveryPolicy());
            var envelope = new DownloadEnvelope(audit.datasetKey().pluginId(), audit.datasetKey().apiName(), batch.sourceParams(),
                    audit.columns().stream().map(c -> c.name()).toList(), rows.size(), rows, DownloadStatus.SUCCESS, null);
            var prepared = auditProcessor.openRetry(auditApi, PARAMS, selected, batch, () -> {}).accept(new FetchResult(envelope, List.of()));
            assertThat(prepared.failures()).isEmpty();
            var index = new CommittedKeyIndex(audit.datasetKey());
            var ticket = (ReadyUnit) auditProcessor.validate(prepared.units().getFirst(), index, NOW);
            index.confirmCommitted(committed(auditService.commitRetry(item, ticket), range ? 2 : 1, 1, range ? 1 : 0, false, ticket));
            assertThat(storage.find(item.taskId()).orElseThrow().items()).extracting(i -> i.key()).containsExactlyInAnyOrder(otherStock, otherDate);
            assertThat(storage.find(otherTask.taskId()).orElseThrow().items()).extracting(i -> i.key()).containsExactly(otherTask);
            assertCode(() -> auditService.commitClosedRetry(audit.datasetKey(), auditApi, otherTask,
                    decision(PARAMS, Set.of(day(3), day(4), day(5), day(6), day(7)), Set.of())), ErrorCode.RETRY_TASK_INVALID);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tushare_pro__fina_audit", Integer.class)).isEqualTo(2);
    }

    @Test void closedStockRangeNeedsFrozenTaskBindingAndDoesNotAcquireBusinessLock() throws Exception {
        var controlledApi = rangeApi(true);
        var selector = new RecoverySelector(RecoverySelector.TargetType.STOCK, "000001.SZ", RecoverySelector.TimeType.RANGE, "2026-09-03/2026-09-07");
        var item = storage.create(DATASET, PARAMS, failure(selector)).key();
        var days = Set.of(day(3), day(4), day(5), day(6), day(7));
        try (var threads = Executors.newSingleThreadExecutor()) {
            var held = locks.acquire(DATASET);
            try {
                assertThat(threads.submit(() -> service.commitClosedRetry(DATASET, controlledApi, item,
                        decision(PARAMS, days, Set.of()))).get(5, TimeUnit.SECONDS))
                        .isEqualTo(new BatchCommitService.Committed(selector, 0, new WriteCounts(0, 0), false));
            } finally { held.unlock(); }
        }
        var invalid = storage.create(DATASET, Map.of("start_date", "bad", "end_date", "20260910"), failure(selector)).key();
        assertCode(() -> service.commitClosedRetry(DATASET, controlledApi, invalid,
                decision(Map.of("start_date", "bad", "end_date", "20260910"), days, Set.of())), ErrorCode.RETRY_TASK_INVALID);
        assertThat(storage.find(invalid.taskId())).isPresent();
    }

    @Test void taskLockPrecedesDatasetLockAndBothLastThroughCleanupAndPhysicalCompletion() throws Exception {
        for (boolean rollback : List.of(false, true)) {
            var probe = new Probe(dataSource);
            configure(probe);
            var item = storage.create(DATASET, PARAMS, failure(date("2026-09-03"))).key();
            var another = storage.create(DATASET, PARAMS, failure(date("2026-09-07"))).key();
            if (rollback) jdbc.execute("CREATE TRIGGER fail_cleanup BEFORE DELETE ON tensor_download_task_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='rollback lock test'");
            var taskLocked = new CountDownLatch(1);
            var deleting = new CountDownLatch(1);
            var releaseDelete = new CountDownLatch(1);
            var completing = new CountDownLatch(1);
            var releaseCompletion = new CountDownLatch(1);
            probe.afterTaskLock = taskLocked::countDown;
            probe.beforeDelete = () -> { deleting.countDown(); await(releaseDelete); };
            probe.beforeCompletion = () -> { completing.countDown(); await(releaseCompletion); };
            var heldDataset = locks.acquire(DATASET);
            boolean releasedDataset = false;
            try (var threads = Executors.newFixedThreadPool(4)) {
                var unit = threads.submit(() -> {
                    var ticket = ready(new CommittedKeyIndex(DATASET), item.selector(), List.of(row("000001.SZ", "20260903", "1")));
                    return service.commitRetry(item, ticket);
                });
                try {
                    assertThat(taskLocked.await(5, TimeUnit.SECONDS)).isTrue();
                    var attemptTask = new CountDownLatch(1);
                    var competingTask = threads.submit(() -> {
                        attemptTask.countDown();
                        return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status ->
                                new RetryTaskRepository(jdbc, new TaskParametersJson()).lockTask(item.taskId()));
                    });
                    assertThat(attemptTask.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> competingTask.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    var independent = threads.submit(() -> {
                        var anotherDataset = new DatasetKey(DATASET.pluginId(), new ApiName("weekly"));
                        var lock = locks.acquire(anotherDataset);
                        try {
                            return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status ->
                                    new RetryTaskRepository(jdbc, new TaskParametersJson()).lockTask(another.taskId()));
                        } finally { lock.unlock(); }
                    });
                    assertThat(independent.get(5, TimeUnit.SECONDS)).isPresent();
                    heldDataset.unlock(); releasedDataset = true;
                    assertThat(deleting.await(5, TimeUnit.SECONDS)).isTrue();
                    var attemptDataset = new CountDownLatch(1);
                    var competingDataset = threads.submit(() -> {
                        attemptDataset.countDown();
                        var next = ready(new CommittedKeyIndex(DATASET), date("2026-09-08"), List.of(row("000002.SZ", "20260908", "2")));
                        return service.commitInitial(next);
                    });
                    assertThat(attemptDataset.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> competingDataset.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    releaseDelete.countDown();
                    assertThat(completing.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> competingDataset.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    assertThatThrownBy(() -> competingTask.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    releaseCompletion.countDown();
                    var result = unit.get(5, TimeUnit.SECONDS);
                    assertThat(result).isInstanceOf(rollback ? BatchCommitService.RolledBack.class : BatchCommitService.Committed.class);
                    assertThat(competingDataset.get(5, TimeUnit.SECONDS)).isInstanceOf(BatchCommitService.Committed.class);
                    assertThat(competingTask.get(5, TimeUnit.SECONDS).isPresent()).isEqualTo(rollback);
                } finally {
                    if (!releasedDataset) heldDataset.unlock();
                    releaseDelete.countDown(); releaseCompletion.countDown();
                }
            }
            if (rollback) jdbc.execute("DROP TRIGGER fail_cleanup");
        }
    }

    static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Bounded latch timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    static ReadyUnit committed(BatchCommitService.CommitResult actual, long r, long i, long u, boolean stop, ReadyUnit ticket) {
        assertThat(actual).isEqualTo(new BatchCommitService.Committed(ticket.selector(), r, new WriteCounts(i, u), stop));
        return ticket;
    }
    static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(TensorException.class, e -> {
            assertThat(e.code()).isEqualTo(code); assertThat(e).hasNoCause();
            assertThat(e.getMessage()).doesNotContain("SENTINEL", "SELECT", "INSERT");
        });
    }
    static LocalDate day(int day) { return LocalDate.of(2026, 9, day); }
    static CalendarDecision decision(Map<String, Object> params, Set<LocalDate> days, Set<LocalDate> open) {
        Map<LocalDate, Boolean> first = new HashMap<>(), second = new HashMap<>();
        days.forEach(d -> { first.put(d, false); second.put(d, open.contains(d)); });
        return new CalendarDecision(new CalendarScope(params, days), Map.of("SSE", first, "SZSE", second));
    }
    ApiDescriptor rangeApi(boolean stock) {
        var p = com.akkc.tensor.test.DownloadPolicies.tradeRange();
        var policy = new DownloadPolicy(p.mode(), p.dateSemantic(), p.description(), p.calendarProfile(), p.limits(),
                DownloadPolicy.SourceRequestMode.RANGE, null, p.requestEvidenceStatus(), DownloadPolicy.BatchPlanning.SOURCE_RANGE,
                stock ? new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "trade_date", RecoverySelector.TimeType.RANGE, true, List.of("controlled")) : p.recoveryPolicy(),
                p.completenessPolicy(), p.calendarEvidenceStatus(), p.evidenceRefs());
        var source = new ArrayList<>(definition.parameters());
        if (stock) source.add(new ParameterDescriptor("ts_code", "Stock", "Stock", ParameterType.TS_CODE, true, null, List.of(), null, null));
        return new ApiDescriptor(DATASET.apiName(), "Range", "Range", definition.queryMode(),
                DownloadParameterProjection.project(source, policy), policy, source);
    }
    record SqlEvent(Connection connection, String sql, String phase) {}
    static class Probe extends AbstractDataSource {
        final DataSource delegate;
        final List<SqlEvent> events = new CopyOnWriteArrayList<>();
        final List<String> physicalCalls = new CopyOnWriteArrayList<>();
        final AtomicInteger commits = new AtomicInteger(), rollbacks = new AtomicInteger();
        volatile String mode = "normal";
        volatile Runnable beforeDelete = () -> {}, beforeCompletion = () -> {}, afterTaskLock = () -> {};
        Probe(DataSource delegate) { this.delegate = delegate; }
        void reset() { events.clear(); commits.set(0); rollbacks.set(0); }
        public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
        public Connection getConnection() throws SQLException {
            if (mode.equals("startFailure")) throw new SQLException("SENTINEL", "08006");
            var connection = delegate.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                String operation = method.getName();
                if (operation.equals("commit")) physicalCalls.add("commit:" + mode);
                if (operation.equals("setAutoCommit")) physicalCalls.add("setAutoCommit:" + args[0]);
                if (operation.equals("commit")) {
                    beforeCompletion.run(); commits.incrementAndGet();
                    if (mode.equals("beforeCommit")) throw new SQLException("SENTINEL", "08006");
                    Object result = invoke(connection, method, args);
                    if (mode.equals("afterCommit") || mode.equals("afterCommitRollback")) throw new SQLException("SENTINEL", "08006");
                    return result;
                }
                if (operation.equals("rollback")) {
                    beforeCompletion.run(); rollbacks.incrementAndGet();
                    if (mode.equals("rollbackFailure")) {
                        // Leave an unknown completion for Spring, but independently clean the isolated connection.
                        connection.rollback();
                        throw new SQLException("SENTINEL", "08006");
                    }
                }
                Object result = invoke(connection, method, args);
                if (operation.equals("prepareStatement")) {
                    String sql = (String) args[0];
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (statement, m, values) -> {
                        String phase = switch (m.getName()) { case "executeQuery" -> "query"; case "executeBatch" -> "batch"; case "executeUpdate" -> "update"; default -> null; };
                        if (phase != null) {
                            events.add(new SqlEvent(connection, sql, phase));
                            if (sql.startsWith("DELETE FROM tensor_download_task_item")) beforeDelete.run();
                            if (mode.equals("sqlConnectionFailure") && phase.equals("batch")) throw new SQLException("SENTINEL", "08006");
                        }
                        Object answer = invoke(result, m, values);
                        if ("query".equals(phase) && sql.endsWith("FOR UPDATE")) afterTaskLock.run();
                        return answer;
                    });
                }
                return result;
            });
        }
    }
    static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); } catch (InvocationTargetException e) { throw e.getCause(); }
    }

    ReadyUnit ready(CommittedKeyIndex index, RecoverySelector selector, List<List<Object>> rows) {
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        var batch = new FetchBatch(converter.mapRetry(api, PARAMS, selector).sourceParams().values(), api.downloadPolicy().recoveryPolicy());
        var envelope = new DownloadEnvelope(DATASET.pluginId(), DATASET.apiName(), batch.sourceParams(),
                definition.columns().stream().map(c -> c.name()).toList(), rows.size(), rows, DownloadStatus.SUCCESS, null);
        var prepared = processor.openRetry(api, PARAMS, selector, batch, () -> {}).accept(new FetchResult(envelope, List.of()));
        assertThat(prepared.failures()).isEmpty();
        return (ReadyUnit) processor.validate(prepared.units().getFirst(), index, NOW);
    }

    static RecoverySelector date(String date) {
        return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE, date);
    }
    static Failure failure(RecoverySelector selector) { return new Failure(selector, ErrorCode.SOURCE_TIMEOUT); }
    static List<Object> row(String stock, String date, String amount) {
        return Arrays.asList(stock, date, null, null, null, null, null, null, null, null, amount);
    }
    static List<Map<String, Object>> business() {
        // This template obtains a fresh, independent physical connection after the unit completes.
        return jdbc.queryForList("SELECT * FROM tushare_pro__daily ORDER BY ts_code, trade_date");
    }
}
