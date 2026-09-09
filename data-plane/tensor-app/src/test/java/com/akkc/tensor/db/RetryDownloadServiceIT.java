package com.akkc.tensor.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.akkc.tensor.config.ApplicationConfiguration;
import com.akkc.tensor.core.download.DownloadExecutionException;
import com.akkc.tensor.core.download.DownloadExecutionResult;
import com.akkc.tensor.core.download.DownloadExecutionSlot;
import com.akkc.tensor.core.download.RetryDownloadService;
import com.akkc.tensor.core.persistence.DatasetLockManager;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.retry.RetryTaskRepository.Failure;
import com.akkc.tensor.core.retry.RetryTaskStorageService;
import com.akkc.tensor.plugin.api.DataSourcePlugin;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.download.CalendarDecision;
import com.akkc.tensor.plugin.api.download.CalendarScope;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadParameterProjection;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.FetchBatch;
import com.akkc.tensor.plugin.api.download.FetchResult;
import com.akkc.tensor.plugin.api.download.RecoveryPolicy;
import com.akkc.tensor.plugin.api.download.RecoverySelector;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.RequestId;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.plugin.api.descriptor.PluginReadiness;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RetryDownloadServiceIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withCommand("--log-bin-trust-function-creators=1");
    static final DatasetKey DAILY = DatasetKey.of(new PluginId("tushare_pro"), new ApiName("daily"));
    static final DatasetKey AUDIT = DatasetKey.of(new PluginId("tushare_pro"), new ApiName("fina_audit"));
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T03:04:05.678Z"), ZoneOffset.UTC);
    static final Map<String, Object> ORIGINAL = Map.of(
            "start_date", "20260901", "end_date", "20260910");
    static DataSource dataSource;
    static JdbcTemplate jdbc;
    static DatasetDefinition dailyDefinition;
    static DatasetDefinition auditDefinition;

    RetryTaskStorageService storage;
    RetryDownloadService service;
    DownloadExecutionSlot slot;
    ControlledPlugin plugin;
    Probe probe;
    DatasetDefinition definition;
    ApiDescriptor api;

    @BeforeAll
    static void migrate() {
        dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var loader = new DatasetDefinitionLoader();
        var resources = new PathMatchingResourcePatternResolver();
        dailyDefinition = loader.loadAll(resources, "classpath:datasets/tushare_pro/daily.yaml").getFirst();
        var audit = loader.loadAll(resources, "classpath:datasets/tushare_pro/fina_audit.yaml").getFirst();
        List<ParameterDescriptor> source = audit.parameters().stream()
                .map(parameter -> parameter.name().equals("ts_code")
                        ? new ParameterDescriptor(parameter.name(), parameter.label(), parameter.description(),
                                parameter.type(), false, parameter.defaultValue(), parameter.allowedValues(),
                                parameter.pattern(), parameter.relatedParameter())
                        : parameter)
                .toList();
        auditDefinition = new DatasetDefinition(audit.datasetKey(), audit.displayName(), audit.category(),
                audit.queryMode(), source, audit.tableName(), audit.columns(), audit.businessKey(), audit.filters(),
                audit.fixedColumn(), 2);
    }

    @BeforeEach
    void prepare() {
        for (String trigger : List.of("t13_item_delete", "t13_header_delete", "t13_header_touch")) {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger);
        }
        jdbc.update("DELETE FROM tensor_download_task_item");
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM tushare_pro__daily");
        jdbc.update("DELETE FROM tushare_pro__fina_audit");
        configure(dailyDefinition, dailyApi(), Fault.NONE, false);
    }

    @Test
    void exactThreeAndSevenRetryKeepsOriginalJsonAndRebuiltServiceReadsOnlySeven() {
        var three = requestDate(3);
        var seven = requestDate(7);
        UUID taskId = task(DAILY, ORIGINAL, three, seven);
        UUID otherTask = task(DAILY, ORIGINAL, three);
        String originalJson = taskJson(taskId);
        Timestamp createdAt = createdAt(taskId);
        plugin.errors.put(Map.of("trade_date", "20260907"), ErrorCode.SOURCE_TIMEOUT);
        probe.reset();

        DownloadExecutionResult first = execute(taskId);

        assertThat(first.outcome()).isEqualTo(DownloadExecutionResult.Outcome.PARTIAL);
        counts(first, 1, 1, 0L, 0, 1, 1, 0);
        assertThat(first.taskId()).isEqualTo(taskId);
        assertThat(first.remainingFailedUnits()).isEqualTo(1L);
        assertThat(first.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(seven);
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
        });
        assertThat(plugin.plans).containsExactly(
                Map.of("trade_date", "20260903"),
                Map.of("trade_date", "20260907"));
        assertThat(plugin.fetches).isEqualTo(plugin.plans);
        assertThat(itemSelectors(taskId)).containsExactly(seven);
        assertThat(taskJson(taskId)).isEqualTo(originalJson);
        assertThat(createdAt(taskId)).isEqualTo(createdAt);
        assertThat(itemSelectors(otherTask)).containsExactly(three);
        assertSingleFindWithoutReadback(taskId);

        configure(dailyDefinition, dailyApi(), Fault.NONE, false);
        probe.reset();
        DownloadExecutionResult second = execute(taskId);
        assertThat(second.outcome()).isEqualTo(DownloadExecutionResult.Outcome.SUCCESS);
        counts(second, 1, 0, 0L, 0, 1, 1, 0);
        assertThat(second.taskId()).isNull();
        assertThat(second.remainingFailedUnits()).isZero();
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260907"));
        assertThat(headerCount(taskId)).isZero();
        assertThat(itemSelectors(otherTask)).containsExactly(three);

        int sourceCalls = plugin.plans.size() + plugin.fetches.size() + plugin.calendarScopes.size();
        assertThatThrownBy(() -> execute(taskId)).isInstanceOfSatisfying(TensorException.class, error -> {
            assertThat(error.code()).isEqualTo(ErrorCode.RETRY_TASK_NOT_FOUND);
            assertThat(error).hasNoCause();
        });
        assertThat(plugin.plans.size() + plugin.fetches.size() + plugin.calendarScopes.size()).isEqualTo(sourceCalls);
    }

    @Test
    void sameDateStocksUseRealAuditCompositeKeysAndRollbackAllBGroupsWhenItsExactDeleteFails() {
        configure(auditDefinition, auditApi(), Fault.NONE, false);
        var a = stockDate("000001.SZ", 2);
        var b = stockDate("000002.SZ", 2);
        var c = stockDate("600000.SH", 2);
        Map<String, Object> params = Map.of("start_date", "20260902", "end_date", "20260902");
        UUID taskId = task(AUDIT, params, a, b, c);
        String originalJson = taskJson(taskId);
        jdbc.update("INSERT INTO tushare_pro__fina_audit "
                + "(ts_code, ann_date, end_date, audit_fees, source_plugin, source_api, ingested_at) "
                + "VALUES ('000002.SZ', '2026-09-02', '2026-03-31', 8, 'tushare_pro', 'fina_audit', "
                + "'2026-09-01 00:00:00')");
        Map<String, Object> oldB = jdbc.queryForMap(
                "SELECT * FROM tushare_pro__fina_audit WHERE ts_code='000002.SZ'");
        plugin.rows = source -> switch ((String) source.get("ts_code")) {
            case "000001.SZ" -> List.of(auditRow("000001.SZ", "20260630", "1"));
            case "000002.SZ" -> List.of(
                    auditRow("000002.SZ", "20260331", "9"),
                    auditRow("000002.SZ", "20260630", "2"),
                    auditRow("000002.SZ", "20260930", "4"));
            case "600000.SH" -> List.of(auditRow("600000.SH", "20260630", "3"));
            default -> throw new AssertionError("Unexpected stock request");
        };
        plugin.beforeFetch = source -> {
            if (source.get("ts_code").equals("600000.SH")) {
                assertThat(itemReason(taskId, b)).isEqualTo("PERSISTENCE_FAILED");
            }
        };
        jdbc.execute("CREATE TRIGGER t13_item_delete BEFORE DELETE ON tensor_download_task_item FOR EACH ROW "
                + "BEGIN IF OLD.task_id='" + taskId + "' AND OLD.target_value='000002.SZ' THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SQL TOKEN SENTINEL'; END IF; END");
        probe.reset();

        DownloadExecutionResult result = execute(taskId);

        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.PARTIAL);
        counts(result, 2, 1, 0L, 0, 2, 2, 0);
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.remainingFailedUnits()).isEqualTo(1L);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(b);
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.PERSISTENCE_FAILED);
        });
        assertThat(itemSelectors(taskId)).containsExactly(b);
        assertThat(taskJson(taskId)).isEqualTo(originalJson);
        assertThat(plugin.fetches).containsExactly(
                Map.of("ann_date", "20260902", "ts_code", "000001.SZ"),
                Map.of("ann_date", "20260902", "ts_code", "000002.SZ"),
                Map.of("ann_date", "20260902", "ts_code", "600000.SH"));
        assertThat(probe.sql.stream()
                .filter(sql -> sql.startsWith("INSERT INTO `tushare_pro__fina_audit`")).count()).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT * FROM tushare_pro__fina_audit WHERE ts_code='000002.SZ'"))
                .containsExactly(oldB);
        assertThat(jdbc.query("SELECT ts_code, audit_fees FROM tushare_pro__fina_audit ORDER BY ts_code",
                (rs, row) -> rs.getString(1) + ":" + rs.getBigDecimal(2).stripTrailingZeros().toPlainString()))
                .containsExactly("000001.SZ:1", "000002.SZ:8", "600000.SH:3");
    }

    @Test
    void lastHeaderDeleteFailureRollsBackBusinessThenConfirmsOnlyTheOriginalReasonUpdate() {
        var selected = requestDate(3);
        UUID taskId = task(DAILY, ORIGINAL, selected);
        String originalJson = taskJson(taskId);
        Timestamp createdAt = createdAt(taskId);
        seedDaily("2026-09-03", "8");
        plugin.rows = source -> List.of(dailyRow("000001.SZ", (String) source.get("trade_date"), "9"));
        jdbc.execute("CREATE TRIGGER t13_header_delete BEFORE DELETE ON tensor_download_task FOR EACH ROW "
                + "BEGIN IF OLD.task_id='" + taskId + "' THEN SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT='SQL TOKEN SENTINEL'; END IF; END");

        DownloadExecutionResult result = execute(taskId);

        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.FAILED);
        counts(result, 0, 1, 0L, 0, 0, 0, 0);
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.remainingFailedUnits()).isEqualTo(1L);
        assertThat(itemReason(taskId, selected)).isEqualTo("PERSISTENCE_FAILED");
        assertThat(taskJson(taskId)).isEqualTo(originalJson);
        assertThat(createdAt(taskId)).isEqualTo(createdAt);
        assertDaily("2026-09-03:000001.SZ:8.0000");
    }

    @Test
    void nonLastTouchAndReasonTouchFailuresRollbackBothTransactionsAndStopBeforeSeven() {
        var three = requestDate(3);
        var seven = requestDate(7);
        UUID taskId = task(DAILY, ORIGINAL, three, seven);
        String originalJson = taskJson(taskId);
        Timestamp originalUpdated = updatedAt(taskId);
        seedDaily("2026-09-03", "8");
        jdbc.execute("CREATE TRIGGER t13_header_touch BEFORE UPDATE ON tensor_download_task FOR EACH ROW "
                + "BEGIN IF OLD.task_id='" + taskId + "' THEN SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT='SQL TOKEN SENTINEL'; END IF; END");

        DownloadExecutionResult result = stopped(taskId, ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);

        counts(result, 0, 1, 1L, 0, 0, 0, 0);
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.remainingFailedUnits()).isNull();
        assertThat(result.notStartedScopes()).containsExactly(seven);
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260903"));
        assertThat(itemSelectors(taskId)).containsExactly(three, seven);
        assertThat(itemReason(taskId, three)).isEqualTo("SOURCE_RATE_LIMITED");
        assertThat(itemReason(taskId, seven)).isEqualTo("SOURCE_RATE_LIMITED");
        assertThat(taskJson(taskId)).isEqualTo(originalJson);
        assertThat(updatedAt(taskId)).isEqualTo(originalUpdated);
        assertDaily("2026-09-03:000001.SZ:8.0000");
    }

    @Test
    void validEmptyAndClosedCleanupDeleteOnlyTheFailureAndKeepOldBusinessRows() {
        var three = requestDate(3);
        seedDaily("2026-09-03", "8");
        UUID emptyTask = task(DAILY, ORIGINAL, three);
        plugin.rows = source -> List.of();

        DownloadExecutionResult empty = execute(emptyTask);

        assertThat(empty.outcome()).isEqualTo(DownloadExecutionResult.Outcome.EMPTY);
        counts(empty, 1, 0, 0L, 0, 0, 0, 0);
        assertThat(empty.taskId()).isNull();
        assertThat(headerCount(emptyTask)).isZero();
        assertDaily("2026-09-03:000001.SZ:8.0000");

        configure(dailyDefinition, dailyApi(), Fault.NONE, false);
        UUID closedTask = task(DAILY, ORIGINAL, three);
        plugin.closedDates = Set.of(LocalDate.of(2026, 9, 3));
        DownloadExecutionResult closed = execute(closedTask);
        assertThat(closed.outcome()).isEqualTo(DownloadExecutionResult.Outcome.NO_OPEN_DATES);
        counts(closed, 0, 0, 0L, 1, 0, 0, 0);
        assertThat(closed.taskId()).isNull();
        assertThat(plugin.plans).isEmpty();
        assertThat(plugin.fetches).isEmpty();
        assertThat(itemSelectors(closedTask)).isEmpty();
        assertThat(headerCount(closedTask)).isZero();
        assertDaily("2026-09-03:000001.SZ:8.0000");

        configure(dailyDefinition, dailyApi(), Fault.NONE, false);
        var four = requestDate(4);
        UUID failedCleanup = task(DAILY, ORIGINAL, four);
        plugin.closedDates = Set.of(LocalDate.of(2026, 9, 4));
        jdbc.execute("CREATE TRIGGER t13_item_delete BEFORE DELETE ON tensor_download_task_item FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SQL TOKEN SENTINEL'");
        DownloadExecutionResult failed = execute(failedCleanup);
        assertThat(failed.outcome()).isEqualTo(DownloadExecutionResult.Outcome.FAILED);
        counts(failed, 0, 1, 0L, 1, 0, 0, 0);
        assertThat(itemSelectors(failedCleanup)).containsExactly(four);
        assertThat(itemReason(failedCleanup, four)).isEqualTo("PERSISTENCE_FAILED");
        assertThat(plugin.plans).isEmpty();
        assertThat(plugin.fetches).isEmpty();
        assertDaily("2026-09-03:000001.SZ:8.0000");
    }

    @Test
    void physicalCommitReplyLossIsUnknownWithoutReadbackAndNextServiceReadsOnlyTheSibling() {
        configure(dailyDefinition, dailyApi(), Fault.AFTER_CLEANUP_COMMIT, false);
        var three = requestDate(3);
        var seven = requestDate(7);
        UUID taskId = task(DAILY, ORIGINAL, three, seven);
        probe.reset();

        DownloadExecutionResult result = stopped(taskId, ErrorCode.COMMIT_UNCONFIRMED);

        counts(result, 0, 0, 1L, 0, 0, 0, 0);
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.remainingFailedUnits()).isNull();
        assertThat(result.unconfirmedScopes()).containsExactly(three);
        assertThat(result.notStartedScopes()).containsExactly(seven);
        assertThat(itemSelectors(taskId)).containsExactly(seven);
        assertDaily("2026-09-03:000001.SZ:1.0000");
        assertSingleFindWithoutReadback(taskId);

        configure(dailyDefinition, dailyApi(), Fault.NONE, false);
        DownloadExecutionResult recovered = execute(taskId);
        assertThat(recovered.outcome()).isEqualTo(DownloadExecutionResult.Outcome.SUCCESS);
        counts(recovered, 1, 0, 0L, 0, 1, 1, 0);
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260907"));
        assertThat(headerCount(taskId)).isZero();
        assertDaily("2026-09-03:000001.SZ:1.0000", "2026-09-07:000001.SZ:1.0000");
    }

    @Test
    void finalPhysicalCommitReplyLossDeletesTaskButRemainsUnknownWithoutReadbackOrReplay() {
        configure(dailyDefinition, dailyApi(), Fault.AFTER_CLEANUP_COMMIT, false);
        var three = requestDate(3);
        UUID taskId = task(DAILY, ORIGINAL, three);
        probe.reset();

        DownloadExecutionResult result = stopped(taskId, ErrorCode.COMMIT_UNCONFIRMED);

        counts(result, 0, 0, 0L, 0, 0, 0, 0);
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isNull();
        assertThat(result.unconfirmedScopes()).containsExactly(three);
        assertThat(result.notStartedScopes()).isEmpty();
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260903"));
        assertThat(itemSelectors(taskId)).isEmpty();
        assertThat(headerCount(taskId)).isZero();
        assertDaily("2026-09-03:000001.SZ:1.0000");
        assertSingleFindWithoutReadback(taskId);
        assertNoFailureUpdateOrTaskInsert();

        ControlledPlugin firstPlugin = plugin;
        configure(dailyDefinition, dailyApi(), Fault.NONE, false);
        assertThatThrownBy(() -> execute(taskId)).isInstanceOfSatisfying(TensorException.class, error -> {
            assertThat(error.code()).isEqualTo(ErrorCode.RETRY_TASK_NOT_FOUND);
            assertThat(error).hasNoCause();
        });
        assertThat(slot.busy()).isFalse();
        assertThat(firstPlugin.fetches).containsExactly(Map.of("trade_date", "20260903"));
        assertThat(plugin.calendarScopes).isEmpty();
        assertThat(plugin.plans).isEmpty();
        assertThat(plugin.fetches).isEmpty();
        assertThat(itemSelectors(taskId)).isEmpty();
        assertThat(headerCount(taskId)).isZero();
    }

    @Test
    void frameworkFailureAfterCommittedCleanupKeepsCurrentCountsAndConfirmedRemainingTask() {
        configure(dailyDefinition, dailyApi(), Fault.NONE, true);
        var three = requestDate(3);
        var seven = requestDate(7);
        UUID taskId = task(DAILY, ORIGINAL, three, seven);
        probe.reset();

        DownloadExecutionResult result = stopped(taskId, ErrorCode.INTERNAL_ERROR);

        counts(result, 1, 0, 1L, 0, 1, 1, 0);
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.remainingFailedUnits()).isEqualTo(1L);
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(result.notStartedScopes()).containsExactly(seven);
        assertThat(itemSelectors(taskId)).containsExactly(seven);
        assertDaily("2026-09-03:000001.SZ:1.0000");
        assertSingleFindWithoutReadback(taskId);
    }

    @Test
    void frameworkFailureAfterCommittedFinalBusinessKeepsCountsAndDeletedTaskFacts() {
        configure(dailyDefinition, dailyApi(), Fault.NONE, true);
        var three = requestDate(3);
        UUID taskId = task(DAILY, ORIGINAL, three);
        probe.reset();

        DownloadExecutionResult result = stopped(taskId, ErrorCode.INTERNAL_ERROR);

        counts(result, 1, 0, 0L, 0, 1, 1, 0);
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isZero();
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(result.notStartedScopes()).isEmpty();
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260903"));
        assertThat(itemSelectors(taskId)).isEmpty();
        assertThat(headerCount(taskId)).isZero();
        assertDaily("2026-09-03:000001.SZ:1.0000");
        assertSingleFindWithoutReadback(taskId);
        assertNoFailureUpdateOrTaskInsert();
    }

    @Test
    void frameworkFailureAfterCommittedFinalClosedCleanupKeepsFrozenCountsAndDeletedTaskFacts() {
        configure(dailyDefinition, dailyApi(), Fault.NONE, true);
        var three = requestDate(3);
        UUID taskId = task(DAILY, ORIGINAL, three);
        seedDaily("2026-09-03", "8");
        plugin.closedDates = Set.of(LocalDate.of(2026, 9, 3));
        probe.reset();

        DownloadExecutionResult result = stopped(taskId, ErrorCode.INTERNAL_ERROR);

        counts(result, 0, 0, 0L, 1, 0, 0, 0);
        assertThat(result.taskId()).isNull();
        assertThat(result.remainingFailedUnits()).isZero();
        assertThat(result.unconfirmedScopes()).isEmpty();
        assertThat(result.notStartedScopes()).isEmpty();
        assertThat(plugin.plans).isEmpty();
        assertThat(plugin.fetches).isEmpty();
        assertThat(itemSelectors(taskId)).isEmpty();
        assertThat(headerCount(taskId)).isZero();
        assertDaily("2026-09-03:000001.SZ:8.0000");
        assertSingleFindWithoutReadback(taskId);
        assertNoFailureUpdateOrTaskInsert();
    }

    @Test
    void reasonUpdateCommitReplyLossRetainsKnownFailureButDoesNotReadBackItsActualDatabaseState() {
        configure(dailyDefinition, dailyApi(), Fault.AFTER_REASON_COMMIT, false);
        var three = requestDate(3);
        var seven = requestDate(7);
        UUID taskId = task(DAILY, ORIGINAL, three, seven);
        plugin.errors.put(Map.of("trade_date", "20260903"), ErrorCode.SOURCE_TIMEOUT);
        probe.reset();

        DownloadExecutionResult result = stopped(taskId, ErrorCode.TASK_RECORD_SAVE_UNCONFIRMED);

        counts(result, 0, 1, 1L, 0, 0, 0, 0);
        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.remainingFailedUnits()).isNull();
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.selector()).isEqualTo(three);
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.SOURCE_TIMEOUT);
        });
        assertThat(result.notStartedScopes()).containsExactly(seven);
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260903"));
        // The independent read proves MySQL committed; the service must still report the save as unconfirmed.
        assertThat(itemReason(taskId, three)).isEqualTo("SOURCE_TIMEOUT");
        assertThat(itemReason(taskId, seven)).isEqualTo("SOURCE_RATE_LIMITED");
        assertSingleFindWithoutReadback(taskId);
    }

    private void configure(DatasetDefinition selectedDefinition, ApiDescriptor selectedApi,
            Fault fault, boolean frameworkFailureAfterCleanup) {
        definition = selectedDefinition;
        api = selectedApi;
        plugin = new ControlledPlugin(definition.datasetKey(), api, definition);
        probe = new Probe(dataSource);
        probe.fault = fault;
        var config = new ApplicationConfiguration();
        var adapters = config.tensorDatasetAdapters(List.of(definition),
                new DefaultListableBeanFactory().getBeanProvider(DatasetAdapter.class));
        var catalog = config.datasetCatalog(adapters, probe);
        var adapterRegistry = config.adapterRegistry(adapters, catalog);
        var template = new JdbcTemplate(probe);
        var delegate = new DataSourceTransactionManager(probe);
        PlatformTransactionManager transactions = frameworkFailureAfterCleanup
                ? new PlatformTransactionManager() {
                    @Override
                    public TransactionStatus getTransaction(TransactionDefinition definition) {
                        return delegate.getTransaction(definition);
                    }

                    @Override
                    public void commit(TransactionStatus status) {
                        delegate.commit(status);
                        if (status.isNewTransaction() && probe.frameworkFailure.compareAndSet(true, false)) {
                            throw new TransactionSystemException("SQL TOKEN SENTINEL");
                        }
                    }

                    @Override
                    public void rollback(TransactionStatus status) {
                        delegate.rollback(status);
                    }
                }
                : delegate;
        var validator = config.parameterValidator();
        DatasetLockManager locks = config.datasetLockManager();
        var persistence = config.persistenceService(catalog, locks, config.existingKeyRepository(template),
                config.genericUpsertRepository(template), transactions);
        var repository = config.retryTaskRepository(template, config.taskParametersJson());
        storage = config.retryTaskStorageService(repository, transactions, CLOCK);
        var commits = config.batchCommitService(persistence, repository, validator, transactions, CLOCK);
        slot = new DownloadExecutionSlot();
        service = new RetryDownloadService(new PluginRegistry(List.of(plugin)), adapterRegistry, validator,
                commits, storage, slot, CLOCK);
        probe.tracking = true;
        probe.reset();
    }

    private UUID task(DatasetKey dataset, Map<String, Object> params, RecoverySelector first,
            RecoverySelector... remaining) {
        UUID taskId = storage.create(dataset, params, failure(first)).key().taskId();
        for (RecoverySelector selector : remaining) {
            storage.append(taskId, failure(selector));
        }
        return taskId;
    }

    private DownloadExecutionResult execute(UUID taskId) {
        plugin.expectedTask = taskId;
        DownloadExecutionResult result = service.execute(taskId, new RequestId(UUID.randomUUID()));
        assertThat(slot.busy()).isFalse();
        return result;
    }

    private DownloadExecutionResult stopped(UUID taskId, ErrorCode code) {
        DownloadExecutionException exception = catchThrowableOfType(
                () -> execute(taskId), DownloadExecutionException.class);
        assertThat(exception).isNotNull().hasNoCause();
        assertThat(exception.code()).isEqualTo(code);
        assertThat(exception.getMessage()).doesNotContain("SENTINEL", "TOKEN", "SQL");
        DownloadExecutionResult result = exception.downloadResult();
        assertThat(result.outcome()).isEqualTo(DownloadExecutionResult.Outcome.UNCONFIRMED);
        assertThat(result.failureRecordStatus()).isEqualTo(DownloadExecutionResult.FailureRecordStatus.UNCONFIRMED);
        assertThat(slot.busy()).isFalse();
        return result;
    }

    private void assertSingleFindWithoutReadback(UUID taskId) {
        String id = taskId.toString();
        assertThat(probe.sql.stream().filter(sql -> sql.startsWith(
                        "SELECT task_id, plugin_id, api_name, task_params, created_at, updated_at "
                                + "FROM tensor_download_task WHERE task_id=?")
                        && !sql.endsWith("FOR UPDATE")).count())
                .as("one service-level task read for %s", id)
                .isEqualTo(1);
        assertThat(probe.sql.stream().filter(sql -> sql.startsWith(
                "SELECT task_id, target_type, target_value, time_type, time_value, error_code, error_message, "
                        + "updated_at FROM tensor_download_task_item WHERE task_id=? ORDER BY")).count()).isEqualTo(1);
    }

    private void assertNoFailureUpdateOrTaskInsert() {
        assertThat(probe.sql).noneMatch(sql -> sql.startsWith("UPDATE tensor_download_task_item SET error_code=")
                || sql.startsWith("INSERT INTO tensor_download_task ")
                || sql.startsWith("INSERT INTO tensor_download_task_item "));
    }

    private static void counts(DownloadExecutionResult result, long completed, long failed, Long notStarted,
            long closed, long rows, long inserted, long updated) {
        assertThat(result.completedUnits()).isEqualTo(completed);
        assertThat(result.failedUnits()).isEqualTo(failed);
        assertThat(result.notStartedUnits()).isEqualTo(notStarted);
        assertThat(result.skippedClosedDates()).isEqualTo(closed);
        assertThat(result.sourceRowCount()).isEqualTo(rows);
        assertThat(result.insertedRows()).isEqualTo(inserted);
        assertThat(result.updatedRows()).isEqualTo(updated);
    }

    private static ApiDescriptor dailyApi() {
        var policy = com.akkc.tensor.test.DownloadPolicies.tradeRange();
        return new ApiDescriptor(DAILY.apiName(), "Controlled daily", "Controlled", dailyDefinition.queryMode(),
                DownloadParameterProjection.project(dailyDefinition.parameters(), policy), policy,
                dailyDefinition.parameters());
    }

    private static ApiDescriptor auditApi() {
        var base = com.akkc.tensor.test.DownloadPolicies.original();
        var policy = new DownloadPolicy(DownloadPolicy.Mode.ANN_DATE_RANGE, DownloadPolicy.DateSemantic.ANN_DATE,
                "Controlled complete all-stock audit", null, new DownloadPolicy.Limits(31),
                DownloadPolicy.SourceRequestMode.DATE, "ann_date",
                DownloadPolicy.RequestEvidenceStatus.DOCUMENTED_CANDIDATE,
                DownloadPolicy.BatchPlanning.SINGLE_DATE,
                new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME, "ts_code", "ann_date",
                        RecoverySelector.TimeType.DATE, true, List.of("controlled local test")),
                base.completenessPolicy(), null, base.evidenceRefs());
        return new ApiDescriptor(AUDIT.apiName(), "Controlled audit", "Controlled", auditDefinition.queryMode(),
                DownloadParameterProjection.project(auditDefinition.parameters(), policy), policy,
                auditDefinition.parameters());
    }

    private static Failure failure(RecoverySelector selector) {
        return new Failure(selector, ErrorCode.SOURCE_RATE_LIMITED);
    }

    private static RecoverySelector requestDate(int day) {
        return new RecoverySelector(RecoverySelector.TargetType.REQUEST, "", RecoverySelector.TimeType.DATE,
                LocalDate.of(2026, 9, day).toString());
    }

    private static RecoverySelector stockDate(String stock, int day) {
        return new RecoverySelector(RecoverySelector.TargetType.STOCK, stock, RecoverySelector.TimeType.DATE,
                LocalDate.of(2026, 9, day).toString());
    }

    private static List<Object> dailyRow(String stock, String date, String amount) {
        return Arrays.asList(stock, date, null, null, null, null, null, null, null, null, amount);
    }

    private static List<Object> auditRow(String stock, String period, String fees) {
        return Arrays.asList(stock, "20260902", period, "Approved", fees, null, null);
    }

    private static void seedDaily(String date, String amount) {
        jdbc.update("INSERT INTO tushare_pro__daily "
                + "(ts_code, trade_date, amount, source_plugin, source_api, ingested_at) "
                + "VALUES ('000001.SZ', ?, ?, 'tushare_pro', 'daily', '2026-09-01 00:00:00')", date, amount);
    }

    private static void assertDaily(String... expected) {
        assertThat(jdbc.query("SELECT trade_date, ts_code, amount FROM tushare_pro__daily ORDER BY trade_date, ts_code",
                (rs, row) -> rs.getDate(1) + ":" + rs.getString(2) + ":" + rs.getBigDecimal(3).setScale(4)))
                .containsExactly(expected);
    }

    private static String taskJson(UUID taskId) {
        return jdbc.queryForObject("SELECT CAST(task_params AS CHAR) FROM tensor_download_task WHERE task_id=?",
                String.class, taskId.toString());
    }

    private static Timestamp createdAt(UUID taskId) {
        return jdbc.queryForObject("SELECT created_at FROM tensor_download_task WHERE task_id=?",
                Timestamp.class, taskId.toString());
    }

    private static Timestamp updatedAt(UUID taskId) {
        return jdbc.queryForObject("SELECT updated_at FROM tensor_download_task WHERE task_id=?",
                Timestamp.class, taskId.toString());
    }

    private static int headerCount(UUID taskId) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT count(*) FROM tensor_download_task WHERE task_id=?", Integer.class, taskId.toString()));
    }

    private static List<RecoverySelector> itemSelectors(UUID taskId) {
        return jdbc.query("SELECT target_type, target_value, time_type, time_value "
                        + "FROM tensor_download_task_item WHERE task_id=? "
                        + "ORDER BY time_value, target_type, target_value",
                (rs, row) -> new RecoverySelector(
                        RecoverySelector.TargetType.valueOf(rs.getString(1)), rs.getString(2),
                        RecoverySelector.TimeType.valueOf(rs.getString(3)), rs.getString(4)),
                taskId.toString());
    }

    private static String itemReason(UUID taskId, RecoverySelector selector) {
        return jdbc.queryForObject("SELECT error_code FROM tensor_download_task_item WHERE task_id=? "
                        + "AND target_type=? AND target_value=? AND time_type=? AND time_value=?",
                String.class, taskId.toString(), selector.targetType().name(), selector.targetValue(),
                selector.timeType().name(), selector.timeValue());
    }

    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private final class ControlledPlugin implements DataSourcePlugin {
        final DatasetKey dataset;
        final ApiDescriptor descriptor;
        final DatasetDefinition registered;
        final List<CalendarScope> calendarScopes = new ArrayList<>();
        final List<Map<String, Object>> plans = new ArrayList<>();
        final List<Map<String, Object>> fetches = new ArrayList<>();
        final Map<Map<String, Object>, ErrorCode> errors = new HashMap<>();
        Function<Map<String, Object>, List<List<Object>>> rows;
        java.util.function.Consumer<Map<String, Object>> beforeFetch = source -> {};
        Set<LocalDate> closedDates = Set.of();

        ControlledPlugin(DatasetKey dataset, ApiDescriptor descriptor, DatasetDefinition registered) {
            this.dataset = dataset;
            this.descriptor = descriptor;
            this.registered = registered;
            rows = source -> dataset.equals(DAILY)
                    ? List.of(dailyRow("000001.SZ", (String) source.get("trade_date"), "1"))
                    : List.of(auditRow((String) source.get("ts_code"), "20260630", "1"));
        }

        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor(dataset.pluginId(), "Controlled", "Controlled", true, true, true, null,
                    List.of(descriptor), List.of(dataset));
        }

        @Override
        public PluginReadiness readiness() {
            return new PluginReadiness(true, true, true, null);
        }

        @Override
        public FetchResult download(ApiName name, Map<String, Object> params, DownloadContext context) {
            throw new AssertionError("Retry must not use the legacy download entry");
        }

        @Override
        public CalendarDecision confirmCalendar(ApiName name, CalendarScope scope, DownloadContext context) {
            outsideTransaction();
            assertThat(slot.retrying(expectedTask)).isTrue();
            calendarScopes.add(scope);
            Map<LocalDate, Boolean> days = new LinkedHashMap<>();
            scope.dates().forEach(day -> days.put(day, !closedDates.contains(day)));
            return new CalendarDecision(scope, Map.of("SSE", days, "SZSE", days));
        }

        @Override
        public DownloadPolicy.BatchPlanning planBatch(
                ApiName name, FetchBatch batch, DownloadContext context) {
            outsideTransaction();
            plans.add(Map.copyOf(batch.sourceParams()));
            return descriptor.downloadPolicy().batchPlanning();
        }

        @Override
        public FetchResult fetchBatch(ApiName name, FetchBatch batch, DownloadContext context) {
            outsideTransaction();
            Map<String, Object> source = Map.copyOf(batch.sourceParams());
            fetches.add(source);
            beforeFetch.accept(source);
            ErrorCode error = errors.get(source);
            if (error != null) {
                throw new SourceException(error, "SQL TOKEN SENTINEL");
            }
            List<List<Object>> values = rows.apply(source);
            return new FetchResult(new DownloadEnvelope(dataset.pluginId(), name, source,
                    registered.columns().stream().map(column -> column.name()).toList(),
                    values.size(), values, DownloadStatus.SUCCESS, null), List.of());
        }

        UUID expectedTask;
    }

    private enum Fault {
        NONE,
        AFTER_CLEANUP_COMMIT,
        AFTER_REASON_COMMIT
    }

    private static final class Probe extends AbstractDataSource {
        final DataSource delegate;
        final List<String> sql = new ArrayList<>();
        final AtomicBoolean frameworkFailure = new AtomicBoolean();
        volatile Fault fault = Fault.NONE;
        volatile boolean tracking;

        Probe(DataSource delegate) {
            this.delegate = delegate;
        }

        void reset() {
            sql.clear();
            frameworkFailure.set(false);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = delegate.getConnection();
            AtomicBoolean cleanup = new AtomicBoolean();
            AtomicBoolean reason = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commit")) {
                            Object answer = invoke(connection, method, args);
                            if (fault == Fault.AFTER_CLEANUP_COMMIT && cleanup.get()) {
                                throw new SQLException("SQL TOKEN SENTINEL", "08006");
                            }
                            if (fault == Fault.AFTER_REASON_COMMIT && reason.get()) {
                                throw new SQLException("SQL TOKEN SENTINEL", "08006");
                            }
                            return answer;
                        }
                        Object answer = invoke(connection, method, args);
                        if (method.getName().equals("prepareStatement")) {
                            String statementSql = (String) args[0];
                            return Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[] {PreparedStatement.class}, (statement, operation, values) -> {
                                        if (Set.of("executeQuery", "executeUpdate", "executeBatch")
                                                .contains(operation.getName()) && tracking) {
                                            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                                            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                                            sql.add(statementSql);
                                            if (statementSql.startsWith("DELETE FROM tensor_download_task_item")) {
                                                cleanup.set(true);
                                                frameworkFailure.set(true);
                                            }
                                            if (statementSql.startsWith(
                                                    "UPDATE tensor_download_task_item SET error_code=")) {
                                                reason.set(true);
                                            }
                                        }
                                        return invoke(answer, operation, values);
                                    });
                        }
                        return answer;
                    });
        }
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
