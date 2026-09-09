package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.*;

import com.akkc.tensor.config.ApplicationConfiguration;
import com.akkc.tensor.core.download.*;
import com.akkc.tensor.core.persistence.*;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.retry.*;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class InitialDownloadServiceIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static final DatasetKey DATASET = new DatasetKey(new PluginId("tushare_pro"), new ApiName("daily"));
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T01:02:03Z"), ZoneOffset.UTC);
    static final Map<String, Object> PARAMS = Map.of("start_date", "20260901", "end_date", "20260903");
    static DataSource dataSource;
    static JdbcTemplate jdbc;
    static DatasetDefinition definition;
    ApiDescriptor api;
    ControlledPlugin plugin;
    Probe probe;
    DownloadService service;
    DownloadExecutionSlot slot;
    DatasetLockManager locks;
    List<String> calls;

    @BeforeAll static void migrate() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var d = new DatasetDefinitionLoader().loadAll(new PathMatchingResourcePatternResolver(),
                "classpath:datasets/tushare_pro/daily.yaml").getFirst();
        // Keep the registered schema and keys; force B to cross a real SQL batch boundary.
        definition = new DatasetDefinition(d.datasetKey(), d.displayName(), d.category(), d.queryMode(),
                d.parameters(), d.tableName(), d.columns(), d.businessKey(), d.filters(), d.fixedColumn(), 2);
    }

    @BeforeEach void prepare() {
        for (String trigger : List.of("t12_business", "t12_insert", "t12_touch", "t12_stock_business")) {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger);
        }
        jdbc.update("DELETE FROM tensor_download_task_item");
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM tushare_pro__daily");
        jdbc.update("DELETE FROM tushare_pro__fina_audit");
        calls = new ArrayList<>();
        var policy = com.akkc.tensor.test.DownloadPolicies.tradeRange();
        api = new ApiDescriptor(DATASET.apiName(), "Controlled", "Controlled", definition.queryMode(),
                DownloadParameterProjection.project(definition.parameters(), policy), policy, definition.parameters());
        plugin = new ControlledPlugin();
        probe = new Probe(dataSource);
        configure(false);
    }

    void configure(boolean frameworkFailureAfterCommit) {
        var config = new ApplicationConfiguration();
        var adapters = config.tensorDatasetAdapters(List.of(definition),
                new DefaultListableBeanFactory().getBeanProvider(DatasetAdapter.class));
        var catalog = config.datasetCatalog(adapters, probe);
        var registry = config.adapterRegistry(adapters, catalog);
        assertThat(registry.find(DATASET)).isPresent();
        var template = new JdbcTemplate(probe);
        var manager = new DataSourceTransactionManager(probe);
        PlatformTransactionManager transactions = frameworkFailureAfterCommit ? new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition d) { return manager.getTransaction(d); }
            public void rollback(TransactionStatus s) { manager.rollback(s); }
            public void commit(TransactionStatus s) {
                manager.commit(s);
                if (s.isNewTransaction()) throw new TransactionSystemException("SQL TOKEN SENTINEL");
            }
        } : manager;
        locks = config.datasetLockManager();
        var persistence = config.persistenceService(catalog, locks, config.existingKeyRepository(template),
                config.genericUpsertRepository(template), transactions);
        var repository = config.retryTaskRepository(template, config.taskParametersJson());
        var storage = config.retryTaskStorageService(repository, transactions, CLOCK);
        var commits = config.batchCommitService(persistence, repository, config.parameterValidator(), transactions, CLOCK);
        slot = new DownloadExecutionSlot();
        service = new DownloadService(new PluginRegistry(List.of(plugin)), registry, config.parameterValidator(),
                persistence, commits, storage, slot, CLOCK);
        probe.completions.clear();
        probe.sql.clear();
        probe.trace.clear();
    }

    // Omitting a rollback, failure-save gate, or later batch makes these independently read facts disagree.
    @Test void laterSqlGroupRollsBackBThenSavesOnlyBAndCommitsC() {
        seedOldB();
        failLastBusinessGroup();
        plugin.rows = day -> day.equals("20260902") ? groupedB() : List.of(row("000001.SZ", day, "1"));
        plugin.beforeFetch = day -> {
            if (day.equals("20260903")) {
                assertThat(items()).singleElement().satisfies(item -> {
                    assertThat(item.get("time_value")).isEqualTo("2026-09-02");
                    assertThat(item.get("error_code")).isEqualTo("PERSISTENCE_FAILED");
                });
            }
        };
        var result = execute(PARAMS);
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.PARTIAL);
        counts(result, 2, 1, 0L, 0, 2, 2, 0);
        assertThat(result.remainingFailedUnits()).isEqualTo(1L);
        assertThat(result.failureRecordStatus()).isEqualTo(DownloadExecutionResult.FailureRecordStatus.CONFIRMED);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(date(2));
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
        });
        assertThat(result.notStartedScopes()).isEmpty();
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(calls).containsExactly("calendar", "plan:20260901", "plan:20260902", "plan:20260903",
                "fetch:20260901", "fetch:20260902", "fetch:20260903");
        assertBusiness("2026-09-01:000001.SZ:1.0000", "2026-09-02:000001.SZ:8.0000", "2026-09-03:000001.SZ:1.0000");
        assertTask(result.taskId(), PARAMS, "2026-09-02");
        assertThat(probe.completions).containsExactly("commit", "rollback", "commit", "commit");
        assertThat(probe.sql.stream().filter(sql -> sql.startsWith("INSERT INTO `tushare_pro__daily`")).count()).isEqualTo(4);
    }

    @Test void independentStocksPreserveAAndCAfterLaterSqlGroupRollsBackOnlyB() {
        var original = new DatasetDefinitionLoader().loadAll(new PathMatchingResourcePatternResolver(),
                "classpath:datasets/tushare_pro/fina_audit.yaml").getFirst();
        // The local complete source accepts all stocks. Its matching registered definition and API
        // make ts_code optional; real columns, composite keys and migrated table remain unchanged.
        var source = original.parameters().stream().map(p -> p.name().equals("ts_code")
                ? new ParameterDescriptor(p.name(), p.label(), p.description(), p.type(), false,
                        p.defaultValue(), p.allowedValues(), p.pattern(), p.relatedParameter()) : p).toList();
        var audit = new DatasetDefinition(original.datasetKey(), original.displayName(), original.category(),
                original.queryMode(), source, original.tableName(), original.columns(), original.businessKey(),
                original.filters(), original.fixedColumn(), 2);
        var base = com.akkc.tensor.test.DownloadPolicies.original();
        var policy = new DownloadPolicy(DownloadPolicy.Mode.ANN_DATE_RANGE, DownloadPolicy.DateSemantic.ANN_DATE,
                "Controlled complete all-stock source", null, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.DATE, "ann_date", DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SINGLE_DATE, new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME,
                        "ts_code", "ann_date", RecoverySelector.TimeType.DATE, true, List.of("controlled local test")),
                base.completenessPolicy(), null, base.evidenceRefs());
        var auditApi = new ApiDescriptor(audit.datasetKey().apiName(), "Controlled audit", "Controlled", audit.queryMode(),
                DownloadParameterProjection.project(source, policy), policy, source);
        var key = audit.datasetKey();
        var requests = new ArrayList<String>();
        DataSourcePlugin complete = new DataSourcePlugin() {
            public PluginDescriptor descriptor() {
                return new PluginDescriptor(key.pluginId(), "Controlled", "Controlled", true, true, true, null,
                        List.of(auditApi), List.of(key));
            }
            public PluginReadiness readiness() { return new PluginReadiness(true, true, true, null); }
            public FetchResult download(ApiName name, Map<String, Object> params, DownloadContext context) {
                throw new AssertionError("No legacy fallback");
            }
            public DownloadPolicy.BatchPlanning planBatch(ApiName name, FetchBatch batch, DownloadContext context) {
                outsideTransaction();
                requests.add("plan:" + batch.sourceParams());
                return DownloadPolicy.BatchPlanning.SINGLE_DATE;
            }
            public FetchResult fetchBatch(ApiName name, FetchBatch batch, DownloadContext context) {
                outsideTransaction();
                requests.add("fetch:" + batch.sourceParams());
                assertThat(batch.sourceParams()).containsExactlyEntriesOf(Map.of("ann_date", "20260902"));
                // Shuffled stocks make iteration order observable; B spans two actual SQL groups.
                var rows = List.of(auditRow("600000.SH", "20260630", "3"),
                        auditRow("000002.SZ", "20260331", "9"), auditRow("000001.SZ", "20260630", "1"),
                        auditRow("000002.SZ", "20260630", "2"), auditRow("000002.SZ", "20260930", "4"));
                return new FetchResult(new DownloadEnvelope(key.pluginId(), name, batch.sourceParams(),
                        audit.columns().stream().map(c -> c.name()).toList(), rows.size(), rows, DownloadStatus.SUCCESS, null), List.of());
            }
        };
        var config = new ApplicationConfiguration();
        var registered = config.tensorDatasetAdapters(List.of(audit),
                new DefaultListableBeanFactory().getBeanProvider(DatasetAdapter.class));
        var catalog = config.datasetCatalog(registered, probe);
        var jdbcProxy = new JdbcTemplate(probe);
        var manager = new DataSourceTransactionManager(probe);
        var persistence = config.persistenceService(catalog, config.datasetLockManager(),
                config.existingKeyRepository(jdbcProxy), config.genericUpsertRepository(jdbcProxy), manager);
        var repository = config.retryTaskRepository(jdbcProxy, config.taskParametersJson());
        var downloads = new DownloadService(new PluginRegistry(List.of(complete)), config.adapterRegistry(registered, catalog),
                config.parameterValidator(), persistence,
                config.batchCommitService(persistence, repository, config.parameterValidator(), manager, CLOCK),
                config.retryTaskStorageService(repository, manager, CLOCK), new DownloadExecutionSlot(), CLOCK);
        jdbc.update("INSERT INTO tushare_pro__fina_audit (ts_code, ann_date, end_date, audit_fees, source_plugin, source_api, ingested_at) "
                + "VALUES ('000002.SZ', '2026-09-02', '2026-03-31', 8, 'tushare_pro', 'fina_audit', '2026-09-01 00:00:00')");
        var before = jdbc.queryForMap("SELECT * FROM tushare_pro__fina_audit WHERE ts_code='000002.SZ'");
        jdbc.execute("CREATE TRIGGER t12_stock_business BEFORE INSERT ON tushare_pro__fina_audit FOR EACH ROW "
                + "BEGIN IF NEW.ts_code='000002.SZ' AND NEW.end_date='2026-09-30' THEN SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT='SQL TOKEN SENTINEL'; END IF; END");
        probe.completions.clear();
        probe.sql.clear();
        probe.trace.clear();
        var params = Map.<String, Object>of("start_date", "20260902", "end_date", "20260902");
        var result = downloads.executeInitial(key.pluginId(), key.apiName(), params, new RequestId(UUID.randomUUID()));
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.PARTIAL);
        counts(result, 2, 1, 0L, 0, 2, 2, 0);
        assertThat(result.remainingFailedUnits()).isEqualTo(1L);
        assertThat(result.failureRecordStatus()).isEqualTo(DownloadExecutionResult.FailureRecordStatus.CONFIRMED);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(new RecoverySelector(RecoverySelector.TargetType.STOCK,
                    "000002.SZ", RecoverySelector.TimeType.DATE, "2026-09-02"));
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
        });
        assertThat(result.notStartedScopes()).isEmpty();
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(requests).containsExactly("plan:{ann_date=20260902}", "fetch:{ann_date=20260902}");
        assertThat(probe.completions).containsExactly("commit", "rollback", "commit", "commit");
        assertThat(probe.sql.stream().filter(sql -> sql.startsWith("INSERT INTO `tushare_pro__fina_audit`")).count()).isEqualTo(4);
        assertThat(probe.trace).containsExactly("business", "commit", "business", "business", "rollback",
                "failure", "commit", "business", "commit");
        assertThat(jdbc.queryForList("SELECT * FROM tushare_pro__fina_audit WHERE ts_code='000002.SZ'"))
                .containsExactly(before);
        assertThat(jdbc.query("SELECT ts_code, audit_fees FROM tushare_pro__fina_audit ORDER BY ts_code",
                (rs, n) -> rs.getString(1) + ":" + rs.getBigDecimal(2).stripTrailingZeros().toPlainString()))
                .containsExactly("000001.SZ:1", "000002.SZ:8", "600000.SH:3");
        assertThat(headers()).singleElement().satisfies(header -> {
            assertThat(header.get("task_id")).isEqualTo(result.taskId().toString());
            assertThat(header.get("api_name")).isEqualTo("fina_audit");
            assertThat(new TaskParametersJson().read((String) header.get("task_params"))).isEqualTo(params);
        });
        assertThat(items()).singleElement().satisfies(item -> {
            assertThat(item.get("task_id")).isEqualTo(result.taskId().toString());
            assertThat(item.get("target_type")).isEqualTo("STOCK");
            assertThat(item.get("target_value")).isEqualTo("000002.SZ");
            assertThat(item.get("time_type")).isEqualTo("DATE");
            assertThat(item.get("time_value")).isEqualTo("2026-09-02");
            assertThat(item.get("error_code")).isEqualTo("PERSISTENCE_FAILED");
        });
    }

    static List<Object> auditRow(String stock, String period, String fees) {
        return Arrays.asList(stock, "20260902", period, "Approved", fees, null, null);
    }

    @Test void firstDetailInsertFailureLeavesNoHalfTaskAndStopsBeforeC() {
        seedOldB();
        failLastBusinessGroup();
        jdbc.execute("CREATE TRIGGER t12_insert BEFORE INSERT ON tensor_download_task_item FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SQL TOKEN SENTINEL'");
        plugin.rows = day -> day.equals("20260902") ? groupedB() : List.of(row("000001.SZ", day, "1"));
        var result = stopped(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);
        counts(result, 1, 1, 1L, 0, 1, 1, 0);
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isNull();
        assertThat(result.notStartedScopes()).containsExactly(date(3));
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(fetches()).containsExactly("fetch:20260901", "fetch:20260902");
        assertThat(headers()).isEmpty();
        assertThat(items()).isEmpty();
        assertBusiness("2026-09-01:000001.SZ:1.0000", "2026-09-02:000001.SZ:8.0000");
        assertThat(probe.completions).containsExactly("commit", "rollback", "rollback");
    }

    @Test void appendTouchFailureKeepsConfirmedTaskAndOriginalReasonThenStops() {
        jdbc.execute("CREATE TRIGGER t12_touch BEFORE UPDATE ON tensor_download_task FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SQL TOKEN SENTINEL'");
        plugin.errors = Map.of("20260901", ErrorCode.SOURCE_RATE_LIMITED, "20260902", ErrorCode.SOURCE_TIMEOUT);
        var firstHeader = new ArrayList<Map<String, Object>>();
        var firstItems = new ArrayList<Map<String, Object>>();
        plugin.beforeFetch = day -> {
            if (day.equals("20260902")) {
                firstHeader.addAll(headers());
                firstItems.addAll(items());
            }
        };
        var result = stopped(ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);
        counts(result, 0, 2, 1L, 0, 0, 0, 0);
        assertThat(result.taskId()).isNotNull();
        assertThat(result.remainingFailedUnits()).isNull();
        assertThat(result.failures()).extracting(RecoveryUnitProcessor.Failure::selector).containsExactly(date(1), date(2));
        assertThat(result.notStartedScopes()).containsExactly(date(3));
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(fetches()).containsExactly("fetch:20260901", "fetch:20260902");
        assertThat(headers()).isEqualTo(firstHeader).hasSize(1);
        assertThat(items()).isEqualTo(firstItems).hasSize(1);
        assertThat(items().getFirst().get("error_code")).isEqualTo("SOURCE_RATE_LIMITED");
        assertTask(result.taskId(), PARAMS, "2026-09-01");
        assertBusiness();
        assertThat(probe.completions).containsExactly("commit", "rollback");
    }

    @Test void physicalCommitThenLostReplyDoesNotCountOrSaveUnknownUnit() {
        probe.throwAfterCommit = true;
        var result = stopped(ErrorCode.COMMIT_UNCONFIRMED);
        counts(result, 0, 0, 2L, 0, 0, 0, 0);
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isZero();
        assertThat(result.failures()).isEmpty();
        assertThat(result.unconfirmedScopes()).containsExactly(date(1));
        assertThat(result.notStartedScopes()).containsExactly(date(2), date(3));
        assertThat(fetches()).containsExactly("fetch:20260901");
        // This independent observation is test evidence, never a production reconciliation path.
        assertBusiness("2026-09-01:000001.SZ:1.0000");
        assertThat(headers()).isEmpty();
        assertThat(items()).isEmpty();
        assertThat(probe.completions).containsExactly("commit");
    }

    @Test void frameworkFailureAfterConfirmedCommitCountsCurrentSuccessAndStops() {
        configure(true);
        var result = stopped(ErrorCode.INTERNAL_ERROR);
        counts(result, 1, 0, 2L, 0, 1, 1, 0);
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isZero();
        assertThat(result.failures()).isEmpty();
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(result.notStartedScopes()).containsExactly(date(2), date(3));
        assertThat(fetches()).containsExactly("fetch:20260901");
        assertBusiness("2026-09-01:000001.SZ:1.0000");
        assertThat(headers()).isEmpty();
        assertThat(items()).isEmpty();
        assertThat(probe.completions).containsExactly("commit");
    }

    @Test void sourceFailuresOnThreeAndSevenStillAttemptAllTenDatesAndShareFrozenTask() {
        var params = Map.<String, Object>of("start_date", "20260901", "end_date", "20260910");
        plugin.errors = Map.of("20260903", ErrorCode.SOURCE_RATE_LIMITED, "20260907", ErrorCode.SOURCE_TIMEOUT);
        plugin.beforeFetch = day -> {
            if (day.equals("20260904")) assertThat(items()).hasSize(1);
            if (day.equals("20260908")) assertThat(items()).hasSize(2);
        };
        var result = execute(params);
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.PARTIAL);
        counts(result, 8, 2, 0L, 0, 8, 8, 0);
        assertThat(result.remainingFailedUnits()).isEqualTo(2L);
        assertThat(result.failureRecordStatus()).isEqualTo(DownloadExecutionResult.FailureRecordStatus.CONFIRMED);
        assertThat(fetches()).containsExactly("fetch:20260901", "fetch:20260902", "fetch:20260903", "fetch:20260904",
                "fetch:20260905", "fetch:20260906", "fetch:20260907", "fetch:20260908", "fetch:20260909", "fetch:20260910");
        assertTask(result.taskId(), params, "2026-09-03", "2026-09-07");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tushare_pro__daily", Integer.class)).isEqualTo(8);
        assertThat(result.toString()).doesNotContain("SENTINEL", "TOKEN", "SELECT");
        assertThat(headers().toString() + items()).doesNotContain("SENTINEL", "TOKEN", "SELECT");
        assertThat(probe.completions).hasSize(10).containsOnly("commit");
    }

    @Test void emptyUnitsCommitWithoutDeletingOldBusinessOrCreatingTask() {
        seedOldB();
        plugin.rows = day -> List.of();
        var result = execute(PARAMS);
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.EMPTY);
        counts(result, 3, 0, 0L, 0, 0, 0, 0);
        assertThat(result.taskId()).isNull();
        assertThat(result.failureRecordStatus()).isEqualTo(DownloadExecutionResult.FailureRecordStatus.NOT_REQUIRED);
        assertBusiness("2026-09-02:000001.SZ:8.0000");
        assertThat(headers()).isEmpty();
        assertThat(items()).isEmpty();
        assertThat(probe.completions).containsExactly("commit", "commit", "commit");
    }

    @Test void allClosedDatesDoNotStartBusinessOrFailureTransactions() {
        seedOldB();
        plugin.closed = true;
        var result = execute(PARAMS);
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.NO_OPEN_DATES);
        counts(result, 0, 0, 0L, 3, 0, 0, 0);
        assertThat(result.taskId()).isNull();
        assertThat(result.failureRecordStatus()).isEqualTo(DownloadExecutionResult.FailureRecordStatus.NOT_REQUIRED);
        assertThat(calls).containsExactly("calendar");
        assertThat(probe.completions).isEmpty();
        assertThat(probe.sql).isEmpty();
        assertBusiness("2026-09-02:000001.SZ:8.0000");
        assertThat(headers()).isEmpty();
        assertThat(items()).isEmpty();
    }

    @Test void calendarPlanningAndFetchRunWhileAnotherThreadOwnsDatasetLock() throws Exception {
        var fetching = new CountDownLatch(1);
        plugin.beforeFetch = day -> fetching.countDown();
        var held = locks.acquire(DATASET);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> execute(PARAMS));
            try {
                assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(slot.busy()).isTrue();
                assertThatThrownBy(() -> future.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                // Release before executor.close even if a regression blocks before fetch.
                held.unlock();
            }
            var result = future.get(5, TimeUnit.SECONDS);
            counts(result, 3, 0, 0L, 0, 3, 3, 0);
            assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.SUCCESS);
        }
    }

    @Test void finalPlanRejectionDoesNotFetchEarlierDatesOrWriteFailureRecords() {
        plugin.rejectPlanDay = "20260903";
        assertThatThrownBy(() -> execute(PARAMS)).isInstanceOfSatisfying(SourceException.class,
                error -> assertThat(error.code()).isEqualTo(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED));
        assertThat(calls).containsExactly("calendar", "plan:20260901", "plan:20260902", "plan:20260903");
        assertThat(probe.sql).isEmpty();
        assertThat(probe.completions).isEmpty();
        assertThat(slot.busy()).isFalse();
        assertThat(headers()).isEmpty();
        assertThat(items()).isEmpty();
        assertBusiness();
    }

    DownloadExecutionResult execute(Map<String, Object> params) {
        var result = service.executeInitial(DATASET.pluginId(), DATASET.apiName(), params, new RequestId(UUID.randomUUID()));
        assertThat(slot.busy()).isFalse();
        return result;
    }

    DownloadExecutionResult stopped(ErrorCode code) {
        var exception = catchThrowableOfType(() -> execute(PARAMS), DownloadExecutionException.class);
        assertThat(exception).isNotNull().hasNoCause();
        assertThat(exception.code()).isEqualTo(code);
        assertThat(exception.getMessage()).doesNotContain("SENTINEL", "TOKEN", "SQL");
        var result = exception.downloadResult();
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.UNCONFIRMED);
        assertThat(result.failureRecordStatus()).isEqualTo(DownloadExecutionResult.FailureRecordStatus.UNCONFIRMED);
        assertThat(slot.busy()).isFalse();
        return result;
    }

    static void counts(DownloadExecutionResult r, long s, long f, Long n, long h, long rows, long inserts, long updates) {
        assertThat(r.completedUnits()).isEqualTo(s);
        assertThat(r.failedUnits()).isEqualTo(f);
        assertThat(r.notStartedUnits()).isEqualTo(n);
        assertThat(r.skippedClosedDates()).isEqualTo(h);
        assertThat(r.sourceRowCount()).isEqualTo(rows);
        assertThat(r.insertedRows()).isEqualTo(inserts);
        assertThat(r.updatedRows()).isEqualTo(updates);
    }

    static void seedOldB() {
        jdbc.update("INSERT INTO tushare_pro__daily (ts_code, trade_date, amount, source_plugin, source_api, ingested_at) "
                + "VALUES ('000001.SZ', '2026-09-02', 8, 'tushare_pro', 'daily', '2026-09-01 00:00:00')");
    }
    static void failLastBusinessGroup() {
        jdbc.execute("CREATE TRIGGER t12_business BEFORE INSERT ON tushare_pro__daily FOR EACH ROW "
                + "BEGIN IF NEW.ts_code='600000.SH' THEN SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT='SQL TOKEN SENTINEL'; END IF; END");
    }
    static List<List<Object>> groupedB() {
        return List.of(row("000001.SZ", "20260902", "9"), row("000002.SZ", "20260902", "2"), row("600000.SH", "20260902", "3"));
    }
    static List<Object> row(String stock, String day, String amount) {
        return Arrays.asList(stock, day, null, null, null, null, null, null, null, null, amount);
    }
    static RecoverySelector date(int day) {
        return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE,
                LocalDate.of(2026, 9, day).toString());
    }
    List<String> fetches() { return calls.stream().filter(call -> call.startsWith("fetch:")).toList(); }
    // These reads use the underlying DataSource, outside the service's proxy and transaction manager.
    static List<Map<String, Object>> headers() { return jdbc.queryForList("SELECT * FROM tensor_download_task ORDER BY task_id"); }
    static List<Map<String, Object>> items() { return jdbc.queryForList("SELECT * FROM tensor_download_task_item ORDER BY time_value"); }
    static void assertBusiness(String... expected) {
        assertThat(jdbc.query("SELECT trade_date, ts_code, amount FROM tushare_pro__daily ORDER BY trade_date, ts_code",
                (rs, n) -> rs.getDate(1) + ":" + rs.getString(2) + ":" + rs.getBigDecimal(3).setScale(4))).containsExactly(expected);
    }
    static void assertTask(UUID id, Map<String, Object> params, String... dates) {
        assertThat(headers()).singleElement().satisfies(header -> {
            assertThat(header.get("task_id")).isEqualTo(id.toString());
            assertThat(header.get("plugin_id")).isEqualTo("tushare_pro");
            assertThat(header.get("api_name")).isEqualTo("daily");
            assertThat(new TaskParametersJson().read((String) header.get("task_params"))).isEqualTo(params);
        });
        assertThat(items()).extracting(item -> item.get("time_value")).containsExactly((Object[]) dates);
        assertThat(items()).allSatisfy(item -> {
            assertThat(item.get("task_id")).isEqualTo(id.toString());
            assertThat(item.get("target_type")).isEqualTo("REQUEST");
            assertThat(item.get("target_value")).isEqualTo("");
            assertThat(item.get("time_type")).isEqualTo("DATE");
        });
    }
    static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    final class ControlledPlugin implements DataSourcePlugin {
        Function<String, List<List<Object>>> rows = day -> List.of(row("000001.SZ", day, "1"));
        java.util.function.Consumer<String> beforeFetch = day -> {};
        Map<String, ErrorCode> errors = Map.of();
        boolean closed;
        String rejectPlanDay;
        public PluginDescriptor descriptor() {
            return new PluginDescriptor(DATASET.pluginId(), "Controlled", "Controlled", true, true, true, null,
                    List.of(api), List.of(DATASET));
        }
        public PluginReadiness readiness() { return new PluginReadiness(true, true, true, null); }
        public FetchResult download(ApiName name, Map<String, Object> params, DownloadContext context) {
            throw new AssertionError("Initial execution must never fall back to legacy download");
        }
        public CalendarDecision confirmCalendar(ApiName name, CalendarScope scope, DownloadContext context) {
            outsideTransaction();
            assertThat(slot.busy()).isTrue();
            calls.add("calendar");
            var days = new HashMap<LocalDate, Boolean>();
            scope.dates().forEach(day -> days.put(day, !closed));
            return new CalendarDecision(scope, Map.of("SSE", days, "SZSE", days));
        }
        public DownloadPolicy.BatchPlanning planBatch(ApiName name, FetchBatch batch, DownloadContext context) {
            outsideTransaction();
            assertThat(slot.busy()).isTrue();
            calls.add("plan:" + batch.sourceParams().get("trade_date"));
            if (Objects.equals(rejectPlanDay, batch.sourceParams().get("trade_date"))) {
                throw new SourceException(ErrorCode.SOURCE_COMPLETENESS_UNCONFIRMED, "SQL TOKEN SENTINEL");
            }
            return DownloadPolicy.BatchPlanning.SINGLE_DATE;
        }
        public FetchResult fetchBatch(ApiName name, FetchBatch batch, DownloadContext context) {
            outsideTransaction();
            assertThat(slot.busy()).isTrue();
            String day = (String) batch.sourceParams().get("trade_date");
            calls.add("fetch:" + day);
            beforeFetch.accept(day);
            if (errors.containsKey(day)) throw new SourceException(errors.get(day), "SQL TOKEN SENTINEL");
            var data = rows.apply(day);
            return new FetchResult(new DownloadEnvelope(DATASET.pluginId(), name, batch.sourceParams(),
                    definition.columns().stream().map(column -> column.name()).toList(), data.size(), data,
                    DownloadStatus.SUCCESS, null), List.of());
        }
    }

    static final class Probe extends AbstractDataSource {
        final DataSource delegate;
        final List<String> completions = new ArrayList<>(), sql = new ArrayList<>(), trace = new ArrayList<>();
        boolean throwAfterCommit;
        Probe(DataSource delegate) { this.delegate = delegate; }
        public Connection getConnection(String username, String password) throws SQLException { return getConnection(); }
        public Connection getConnection() throws SQLException {
            var connection = delegate.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, m, args) -> {
                if (m.getName().equals("commit") || m.getName().equals("rollback")) {
                    completions.add(m.getName());
                    trace.add(m.getName());
                }
                Object value = invoke(connection, m, args);
                if (m.getName().equals("commit") && throwAfterCommit) throw new SQLException("SQL TOKEN SENTINEL", "08006");
                if (m.getName().equals("prepareStatement")) {
                    String query = (String) args[0];
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (s, method, values) -> {
                        if (Set.of("executeQuery", "executeUpdate", "executeBatch").contains(method.getName())) {
                            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                            sql.add(query);
                            if (query.startsWith("INSERT INTO `tushare_pro__")) trace.add("business");
                            if (query.startsWith("INSERT INTO tensor_download_task_item")) trace.add("failure");
                        }
                        return invoke(value, method, values);
                    });
                }
                return value;
            });
        }
    }
    static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
    }
}
