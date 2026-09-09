package com.akkc.tensor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.config.ApplicationConfiguration;
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
class RetryTaskControllerIT {
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
    org.springframework.test.web.servlet.MockMvc mvc;
    com.fasterxml.jackson.databind.ObjectMapper mapper;

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
        for (String trigger : List.of("t13_item_delete", "t13_header_delete", "t13_header_touch", "t14_item_insert")) {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger);
        }
        jdbc.update("DELETE FROM tensor_download_task_item");
        jdbc.update("DELETE FROM tensor_download_task");
        jdbc.update("DELETE FROM tushare_pro__daily");
        jdbc.update("DELETE FROM tushare_pro__fina_audit");
        configure(dailyDefinition, dailyApi(), Fault.NONE, false);
    }

    @Test
    void firstDownloadThenExactThreeAndSevenHttpLoopPreservesOriginalJson() throws Exception {
        plugin.errors.put(Map.of("trade_date", "20260903"), ErrorCode.SOURCE_RATE_LIMITED);
        plugin.errors.put(Map.of("trade_date", "20260907"), ErrorCode.SOURCE_TIMEOUT);
        var first = initial(200);
        assertThat(first.path("outcome").asText()).isEqualTo("PARTIAL");
        jsonCounts(first, 8, 2, 0, 0, 8, 8, 0);
        UUID id = UUID.fromString(first.path("taskId").asText());
        assertThat(itemSelectors(id)).containsExactly(requestDate(3), requestDate(7));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tushare_pro__daily", Integer.class)).isEqualTo(8);
        String frozen = taskJson(id);
        var detail = read(id, 200);
        assertThat(detail.path("originalDateRange").path("startDate").asText()).isEqualTo("20260901");
        assertThat(detail.path("originalDateRange").path("endDate").asText()).isEqualTo("20260910");
        assertThat(detail.path("failedItemCount").longValue()).isEqualTo(2);
        assertThat(detail.path("items").get(0).path("timeValue").asText()).isEqualTo("2026-09-03");
        UUID other = task(DAILY, ORIGINAL, requestDate(3));
        plugin.fetches.clear(); plugin.errors.remove(Map.of("trade_date", "20260903"));
        var retry = executeHttp(id, 200);
        jsonCounts(retry, 1, 1, 0, 0, 1, 1, 0);
        assertThat(retry.path("remainingFailedUnits").longValue()).isEqualTo(1);
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260903"), Map.of("trade_date", "20260907"));
        assertThat(read(id, 200).path("items")).hasSize(1);
        assertThat(taskJson(id)).isEqualTo(frozen);
        plugin.fetches.clear(); plugin.errors.clear();
        assertThat(executeHttp(id, 200).path("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260907"));
        assertThat(headerCount(id)).isZero();
        read(id, 404); executeHttp(id, 404);
        assertThat(itemSelectors(other)).containsExactly(requestDate(3));
    }

    @Test
    void bothHttpExecutionsExposeAllFiveNormalOutcomes() throws Exception {
        for (String outcome : List.of("SUCCESS", "EMPTY", "NO_OPEN_DATES", "PARTIAL", "FAILED")) {
            prepare();
            configureOutcome(outcome);
            var initial = initial(200);
            assertThat(initial.path("outcome").asText()).isEqualTo(outcome);
            prepare();
            UUID id = task(DAILY, ORIGINAL, requestDate(3), requestDate(7));
            configureOutcome(outcome);
            var retry = executeHttp(id, 200);
            assertThat(retry.path("outcome").asText()).isEqualTo(outcome);
            assertThat(retry.path("notStartedUnits").longValue()).isZero();
            if (outcome.equals("NO_OPEN_DATES")) {
                assertThat(retry.path("completedUnits").longValue()).isZero();
                assertThat(retry.path("skippedClosedDates").longValue()).isEqualTo(2);
            }
        }
    }

    @Test
    void sameDateStocksRemainSeparateAndOnlySuccessfulStockIsDeleted() throws Exception {
        configure(auditDefinition, auditApi(), Fault.NONE, false);
        var a = stockDate("000001.SZ", 2); var b = stockDate("000002.SZ", 2);
        UUID id = task(AUDIT, ORIGINAL, a, b);
        String frozen = taskJson(id);
        plugin.errors.put(Map.of("ann_date", "20260902", "ts_code", "000002.SZ"), ErrorCode.SOURCE_TIMEOUT);
        var result = executeHttp(id, 200);
        jsonCounts(result, 1, 1, 0, 0, 1, 1, 0);
        assertThat(result.path("failures").get(0).path("targetValue").asText()).isEqualTo("000002.SZ");
        assertThat(itemSelectors(id)).containsExactly(b);
        assertThat(read(id, 200).path("taskParams").has("ts_code")).isFalse();
        assertThat(taskJson(id)).isEqualTo(frozen);
        plugin.errors.clear(); executeHttp(id, 200); read(id, 404);
    }

    @Test
    void initialSaveFailureStopsWithKnownFailureAndDoesNotInventSavedTask() throws Exception {
        plugin.errors.put(Map.of("trade_date", "20260903"), ErrorCode.SOURCE_TIMEOUT);
        jdbc.execute("CREATE TRIGGER t14_item_insert BEFORE INSERT ON tensor_download_task_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SQL_SECRET'");
        var error = initial(500);
        assertThat(error.path("code").asText()).isEqualTo("TASK_RECORD_SAVE_UNCONFIRMED");
        var result = error.path("downloadResult");
        jsonCounts(result, 2, 1, 7, 0, 2, 2, 0);
        assertThat(result.path("taskId").isNull()).isTrue();
        assertThat(result.path("remainingFailedUnits").isNull()).isTrue();
        assertThat(result.path("unconfirmedScopes")).isEmpty();
        assertThat(result.path("failures")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tushare_pro__daily", Integer.class)).isEqualTo(2);
    }

    @Test
    void initialPhysicalCommitReplyLossDoesNotClaimCommittedRowsOrSaveFailure() throws Exception {
        configure(dailyDefinition, dailyApi(), Fault.AFTER_BUSINESS_COMMIT, false);
        var error = initial(500);
        assertThat(error.path("code").asText()).isEqualTo("COMMIT_UNCONFIRMED");
        jsonCounts(error.path("downloadResult"), 0, 0, 9, 0, 0, 0, 0);
        assertThat(error.path("downloadResult").path("unconfirmedScopes")).hasSize(1);
        assertThat(error.path("downloadResult").path("failures")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tushare_pro__daily", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isZero();
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260901"));
    }

    @Test
    void unavailableBusinessTransactionStopsWithPersistenceFailedAndNoFailureWrite() throws Exception {
        probe.failBegin = true;
        var error = initial(500);
        assertThat(error.path("code").asText()).isEqualTo("PERSISTENCE_FAILED");
        assertThat(error.path("retryable").booleanValue()).isTrue();
        jsonCounts(error.path("downloadResult"), 0, 1, 9, 0, 0, 0, 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tushare_pro__daily", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isZero();
    }

    @Test
    void lastCommitReplyLossKeepsHttpUnknownEvenThoughSqlDeletedTheTask() throws Exception {
        configure(dailyDefinition, dailyApi(), Fault.AFTER_CLEANUP_COMMIT, false);
        UUID id = task(DAILY, ORIGINAL, requestDate(3)); probe.reset();
        var error = executeHttp(id, 500);
        assertThat(error.path("code").asText()).isEqualTo("COMMIT_UNCONFIRMED");
        var result = error.path("downloadResult");
        jsonCounts(result, 0, 0, 0, 0, 0, 0, 0);
        assertThat(result.path("taskId").isNull()).isTrue();
        assertThat(result.path("remainingFailedUnits").isNull()).isTrue();
        assertThat(result.path("unconfirmedScopes").get(0).path("timeValue").asText()).isEqualTo("2026-09-03");
        assertThat(headerCount(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tushare_pro__daily", Integer.class)).isEqualTo(1);
        assertNoFailureUpdateOrTaskInsert(); assertSingleFindWithoutReadback(id);
        read(id, 404);
    }

    @Test
    void reasonCommitReplyLossReportsKnownFailureAndKeepsOriginalTaskJson() throws Exception {
        configure(dailyDefinition, dailyApi(), Fault.AFTER_REASON_COMMIT, false);
        UUID id = task(DAILY, ORIGINAL, requestDate(3), requestDate(7)); String frozen = taskJson(id);
        plugin.errors.put(Map.of("trade_date", "20260903"), ErrorCode.SOURCE_TIMEOUT);
        var error = executeHttp(id, 500);
        assertThat(error.path("code").asText()).isEqualTo("TASK_RECORD_SAVE_UNCONFIRMED");
        jsonCounts(error.path("downloadResult"), 0, 1, 1, 0, 0, 0, 0);
        assertThat(error.path("downloadResult").path("unconfirmedScopes")).isEmpty();
        assertThat(itemReason(id, requestDate(3))).isEqualTo("SOURCE_TIMEOUT");
        assertThat(taskJson(id)).isEqualTo(frozen);
        assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260903"));
    }

    @Test
    void committedLastDeleteThenFrameworkFailurePreservesConfirmedHttpSubtotals() throws Exception {
        configure(dailyDefinition, dailyApi(), Fault.NONE, true);
        UUID id = task(DAILY, ORIGINAL, requestDate(3));
        var error = executeHttp(id, 500);
        assertThat(error.path("code").asText()).isEqualTo("INTERNAL_ERROR");
        jsonCounts(error.path("downloadResult"), 1, 0, 0, 0, 1, 1, 0);
        assertThat(error.path("downloadResult").path("taskId").isNull()).isTrue();
        assertThat(error.path("downloadResult").path("remainingFailedUnits").longValue()).isZero();
        assertThat(headerCount(id)).isZero();
    }

    @Test
    void committedTaskReadsStayAvailableDuringFetchCommitAndReasonUpdateAndWaiterTimeout() throws Exception {
        for (String stage : List.of("fetch", "commit", "reason")) {
            prepare();
            UUID id = task(DAILY, ORIGINAL, requestDate(3), requestDate(7));
            var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
            Runnable pause = () -> { entered.countDown(); try { assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException failure) { throw new AssertionError(failure); } };
            if (stage.equals("fetch")) plugin.beforeFetch = source -> { if (source.get("trade_date").equals("20260903")) pause.run(); };
            else { probe.pauseStage = stage; probe.pause = pause; }
            if (stage.equals("reason")) plugin.errors.put(Map.of("trade_date", "20260903"), ErrorCode.SOURCE_TIMEOUT);
            try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                var future = worker.submit(() -> executeHttp(id, 200));
                try {
                    assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> future.get(50, java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                    assertThat(slot.busy()).isTrue();
                    var detail = read(id, 200);
                    assertThat(detail.path("retrying").booleanValue()).isTrue();
                    assertThat(detail.path("canExecute").booleanValue()).isFalse();
                    assertThat(detail.path("items")).hasSize(2);
                    executeHttp(UUID.randomUUID(), 409);
                } finally { release.countDown(); }
                var result = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(result.path("outcome").asText()).isEqualTo(stage.equals("reason") ? "PARTIAL" : "SUCCESS");
                assertThat(slot.busy()).isFalse();
                assertThat(plugin.fetches).containsExactly(Map.of("trade_date", "20260903"), Map.of("trade_date", "20260907"));
            }
        }
    }

    @Test
    void listUsesRealStableOrderingPagingIndependentFiltersAndFullSelectors() throws Exception {
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 23; i++) ids.add(task(DAILY, ORIGINAL, requestDate(3), requestDate(7)));
        ids.sort(java.util.Comparator.comparing(UUID::toString).reversed());
        var page = request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/retry-tasks"), 200);
        assertThat(page.path("items")).hasSize(20);
        assertThat(page.path("totalElements").longValue()).isEqualTo(23);
        assertThat(page.path("items").get(0).path("taskId").asText()).isEqualTo(ids.getFirst().toString());
        assertThat(page.path("items").get(0).path("failedScopes")).hasSize(2);
        var last = request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/retry-tasks").param("page", "999"), 200);
        assertThat(last.path("page").intValue()).isEqualTo(2); assertThat(last.path("items")).hasSize(3);
        for (String filter : List.of("pluginId", "apiName")) {
            var empty = request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/retry-tasks").param(filter, "unknown"), 200);
            assertThat(empty.path("totalElements").longValue()).isZero(); assertThat(empty.path("items")).isEmpty();
        }
    }

    @Test
    void malformedOrOrphanRecordsAndSqlReadFailureAreSafeQueryErrors() throws Exception {
        UUID id = task(DAILY, ORIGINAL, requestDate(3));
        jdbc.update("UPDATE tensor_download_task_item SET error_message='HISTORY_SECRET' WHERE task_id=?", id.toString());
        assertThat(read(id, 200).toString()).doesNotContain("HISTORY_SECRET");
        jdbc.update("DELETE FROM tensor_download_task_item WHERE task_id=?", id.toString());
        assertThat(read(id, 500).path("code").asText()).isEqualTo("QUERY_FAILED");
        assertThat(request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/retry-tasks"), 500).path("code").asText()).isEqualTo("QUERY_FAILED");
        assertThat(headerCount(id)).isEqualTo(1);
        storage.append(id, failure(requestDate(3)));
        jdbc.update("UPDATE tensor_download_task_item SET time_value='2026-02-30' WHERE task_id=?", id.toString());
        assertThat(read(id, 500).path("code").asText()).isEqualTo("QUERY_FAILED");
        probe.failReads = true;
        assertThat(read(UUID.randomUUID(), 500).path("code").asText()).isEqualTo("QUERY_FAILED");
    }

    @Test
    void responseWriteFailureAfterExecutionDoesNotReplayOrChangeSavedFailures() throws Exception {
        plugin.errors.put(Map.of("trade_date", "20260903"), ErrorCode.SOURCE_TIMEOUT);
        var context = mvc.getDispatcherServlet().getWebApplicationContext();
        var converter = new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper) {
            @Override protected void writeInternal(Object value, java.lang.reflect.Type type, org.springframework.http.HttpOutputMessage output) throws java.io.IOException {
                assertThat(slot.busy()).isFalse();
                throw new java.io.IOException("RESPONSE_WRITE_SECRET");
            }
        };
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(context.getBean(DownloadController.class), context.getBean(RetryTaskController.class))
                .setMessageConverters(converter).setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        assertThatThrownBy(() -> initial(200)).isInstanceOf(Exception.class);
        assertThat(slot.busy()).isFalse();
        assertThat(plugin.fetches).hasSize(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT time_value FROM tensor_download_task_item", String.class)).isEqualTo("2026-09-03");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tushare_pro__daily", Integer.class)).isEqualTo(9);
    }

    @Test
    void sourceSqlHistoryAndAuthorizationSentinelsNeverEnterHttpOrOperationHandlerLogs() throws Exception {
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); appender.start();
        var operation = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(com.akkc.tensor.observability.OperationLogger.class);
        var handler = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        operation.addAppender(appender); handler.addAppender(appender);
        try {
            UUID id = task(DAILY, ORIGINAL, requestDate(3));
            jdbc.update("UPDATE tensor_download_task_item SET error_message='HISTORY_SECRET' WHERE task_id=?", id.toString());
            read(id, 200);
            assertThat(appender.list).isEmpty();
            plugin.errors.put(Map.of("trade_date", "20260903"), ErrorCode.SOURCE_TIMEOUT);
            executeHttp(id, 200);
            assertThat(appender.list).hasSize(1);
            jdbc.execute("CREATE TRIGGER t13_header_touch BEFORE UPDATE ON tensor_download_task FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SQL_SECRET'");
            executeHttp(id, 500);
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("SECRET", "SENTINEL", "TOKEN", "Authorization");
                if (event.getThrowableProxy() != null) assertThat(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy())).doesNotContain("SECRET", "SENTINEL", "TOKEN");
            });
        } finally { operation.detachAppender(appender); handler.detachAppender(appender); appender.stop(); }
    }

    private void configureOutcome(String outcome) {
        if (outcome.equals("EMPTY")) plugin.rows = source -> List.of();
        if (outcome.equals("NO_OPEN_DATES")) plugin.closedDates = java.time.LocalDate.of(2026, 9, 1).datesUntil(java.time.LocalDate.of(2026, 9, 11)).collect(java.util.stream.Collectors.toSet());
        if (outcome.equals("FAILED")) plugin.beforeFetch = source -> { throw new SourceException(ErrorCode.SOURCE_TIMEOUT, "SOURCE_SECRET"); };
        if (outcome.equals("PARTIAL")) plugin.errors.put(Map.of("trade_date", "20260907"), ErrorCode.SOURCE_TIMEOUT);
    }

    private com.fasterxml.jackson.databind.JsonNode initial(int status) throws Exception {
        return request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/downloads").contentType("application/json")
                .content("{\"pluginId\":\"tushare_pro\",\"apiName\":\"daily\",\"params\":{\"start_date\":\"20260901\",\"end_date\":\"20260910\"}}"), status);
    }
    private com.fasterxml.jackson.databind.JsonNode executeHttp(UUID id, int status) throws Exception {
        return request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/retry-tasks/" + id + "/execute"), status);
    }
    private com.fasterxml.jackson.databind.JsonNode read(UUID id, int status) throws Exception {
        return request(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/retry-tasks/" + id), status);
    }
    private com.fasterxml.jackson.databind.JsonNode request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status) throws Exception {
        var response = mvc.perform(request.header("Authorization", "AUTH_SECRET")).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        var result = mapper.readTree(response.getContentAsString());
        assertThat(result.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        if (result.has("downloadResult")) assertThat(result.path("downloadResult").path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(result.toString()).doesNotContain("SECRET", "SENTINEL", "TOKEN");
        return result;
    }
    private static void jsonCounts(com.fasterxml.jackson.databind.JsonNode result, long completed, long failed, long notStarted, long closed, long rows, long inserted, long updated) {
        var fields = List.of("completedUnits", "failedUnits", "notStartedUnits", "skippedClosedDates", "sourceRowCount", "insertedRows", "updatedRows");
        var expected = List.of(completed, failed, notStarted, closed, rows, inserted, updated);
        for (int i = 0; i < fields.size(); i++) { assertThat(result.path(fields.get(i)).isIntegralNumber()).isTrue(); assertThat(result.path(fields.get(i)).longValue()).isEqualTo(expected.get(i)); }
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
        var plugins = new PluginRegistry(List.of(plugin));
        service = new RetryDownloadService(plugins, adapterRegistry, validator, commits, storage, slot, CLOCK);
        var initial = new com.akkc.tensor.core.download.DownloadService(plugins, adapterRegistry, validator, persistence, commits, storage, slot, CLOCK);
        var queries = new com.akkc.tensor.core.retry.RetryTaskQueryService(storage, plugins, adapterRegistry, validator, slot);
        var resolver = new com.akkc.tensor.web.download.DownloadParameterResolver(new com.akkc.tensor.web.download.DownloadDescriptorResolver(plugins, adapterRegistry), validator);
        mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JacksonPrecisionConfiguration().precisionModule())
                .registerModule(new com.akkc.tensor.config.DownloadBindingConfiguration().downloadRequestJacksonModule(new com.akkc.tensor.web.download.DownloadRequestDeserializer(resolver)));
        var operations = new com.akkc.tensor.observability.OperationLogger(plugins, new com.akkc.tensor.observability.TensorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), plugins));
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new DownloadController(initial, operations, resolver), new RetryTaskController(queries, service, operations))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper)).setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
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

    private static String taskJson(UUID taskId) {
        return jdbc.queryForObject("SELECT CAST(task_params AS CHAR) FROM tensor_download_task WHERE task_id=?",
                String.class, taskId.toString());
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
            assertThat(slot.busy()).isTrue();
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

    }

    private enum Fault {
        NONE,
        AFTER_CLEANUP_COMMIT,
        AFTER_BUSINESS_COMMIT,
        AFTER_REASON_COMMIT
    }

    private static final class Probe extends AbstractDataSource {
        final DataSource delegate;
        final List<String> sql = new ArrayList<>();
        final AtomicBoolean frameworkFailure = new AtomicBoolean();
        volatile Fault fault = Fault.NONE;
        volatile boolean tracking;
        volatile boolean failReads;
        volatile boolean failBegin;
        volatile String pauseStage;
        volatile Runnable pause = () -> {};
        final AtomicBoolean paused = new AtomicBoolean();

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
            AtomicBoolean business = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (failBegin && method.getName().equals("setAutoCommit") && Boolean.FALSE.equals(args[0])) throw new SQLException("SQL_SECRET", "08006");
                        if (method.getName().equals("commit")) {
                            if (cleanup.get() && "commit".equals(pauseStage) && paused.compareAndSet(false, true)) pause.run();
                            Object answer = invoke(connection, method, args);
                            if (fault == Fault.AFTER_BUSINESS_COMMIT && business.get()) throw new SQLException("SQL_SECRET", "08006");
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
                                            if (failReads && statementSql.startsWith("SELECT")) throw new SQLException("SQL_SECRET", "08006");
                                            if (statementSql.startsWith("UPDATE tensor_download_task_item SET error_code=") && "reason".equals(pauseStage) && paused.compareAndSet(false, true)) pause.run();
                                            synchronized (sql) { sql.add(statementSql); }
                                            if (statementSql.startsWith("INSERT INTO `tushare_pro__")) business.set(true);
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
