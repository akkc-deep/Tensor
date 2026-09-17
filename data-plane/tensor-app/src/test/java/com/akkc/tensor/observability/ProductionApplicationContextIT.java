package com.akkc.tensor.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.akkc.tensor.TensorApplication;
import com.akkc.tensor.config.ApplicationConfiguration;
import com.akkc.tensor.config.DownloadTaskProperties;
import com.akkc.tensor.core.download.task.DownloadTaskCoordinator;
import com.akkc.tensor.core.download.task.DownloadTaskQueryService;
import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.download.task.DownloadTaskRunner;
import com.akkc.tensor.web.download.DownloadDescriptorResolver;
import com.akkc.tensor.web.download.DownloadParameterResolver;
import com.akkc.tensor.web.download.DownloadRequestDeserializer;
import com.akkc.tensor.web.download.DownloadParameters.TradeDateParameters;
import com.akkc.tensor.web.dto.DownloadRequest;
import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.core.download.DownloadService;
import com.akkc.tensor.core.query.DatasetQueryService;
import com.akkc.tensor.core.metadata.MetadataQueryService;
import com.akkc.tensor.core.registry.AdapterRegistry;
import com.akkc.tensor.core.registry.PluginRegistry;
import com.akkc.tensor.core.integrity.IntegrityCheckRepository;
import com.akkc.tensor.core.integrity.IntegrityCheckService;
import com.akkc.tensor.plugin.api.integrity.IntegrityStatus;
import com.akkc.tensor.plugin.api.integrity.IntegrityTaskStatus;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.PluginDescriptor;
import com.akkc.tensor.web.DataSourceController;
import com.akkc.tensor.web.DatasetController;
import com.akkc.tensor.web.DownloadController;
import com.akkc.tensor.web.GlobalExceptionHandler;
import com.akkc.tensor.web.RequestIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class ProductionApplicationContextIT {
    private static final String SECRET = "m09-t06-token-password-secret";
    private static final String INVALID_PAGE = "not-a-number";
    private static final List<String> HIDDEN_ENDPOINTS = List.of(
            "/actuator", "/actuator/env", "/actuator/configprops", "/actuator/metrics",
            "/actuator/beans", "/actuator/heapdump", "/actuator/logfile", "/actuator/mappings",
            "/actuator/health/db", "/actuator/health/liveness/livenessState",
            "/actuator/health/readiness/readinessState", "/actuator/health/unknown");

    @Test
    void startsTheSafeProductionServletGraphAndTracksOnlyDatabaseHealth() throws Exception {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
                .withDatabaseName("tensor")
                .withUsername("tensor")
                .withPassword(SECRET);
        ConfigurableApplicationContext first = null;
        ConfigurableApplicationContext second = null;
        CapturedLog captured = null;
        try {
            mysql.start();
            String jdbcUrl = mysql.getJdbcUrl();
            String username = mysql.getUsername();
            first = start(mysql, "");
            assertProductionGraph(first);
            HttpResponse firstHealth = get(first, "/actuator/health");
            assertHealth(first, firstHealth, 200, "UP");
            assertProbesUp(first);
            HttpResponse metadata = get(first, "/api/v1/data-sources");
            assertMetadata(first, metadata, false, false);
            HttpResponse invalid = get(first,
                    "/api/v1/data-sources/tushare_pro/datasets/daily/records?page="
                            + INVALID_PAGE + "&pageSize=50");
            assertSafeParameterError(first, invalid);
            assertSafeResponse(firstHealth, jdbcUrl, username);
            assertSafeResponse(metadata, jdbcUrl, username);
            assertSafeResponse(invalid, jdbcUrl, username);
            assertHidden(first, jdbcUrl, username);
            assertLocalMarketChecks(first);
            first.close();
            first = null;

            captured = captureRootLog();
            second = start(mysql, SECRET);
            assertProductionGraph(second);
            DownloadRequest bound = second.getBean(ObjectMapper.class).readValue(
                    "{\"pluginId\":\"tushare_pro\",\"apiName\":\"daily\",\"params\":{\"ts_code\":\"000001.SZ\",\"trade_date\":\"20260905\"}}",
                    DownloadRequest.class);
            assertThat(bound.params()).isEqualTo(new TradeDateParameters("000001.SZ", "20260905"));
            HttpResponse secondHealth = get(second, "/actuator/health");
            assertHealth(second, secondHealth, 200, "UP");
            assertProbesUp(second);
            HttpResponse secondMetadata = get(second, "/api/v1/data-sources");
            assertMetadata(second, secondMetadata, true, true);
            assertSafeResponse(secondHealth, jdbcUrl, username);
            assertSafeResponse(secondMetadata, jdbcUrl, username);
            assertHidden(second, jdbcUrl, username);
            assertThat(captured.text()).doesNotContain(SECRET);

            mysql.stop();
            HttpResponse down = get(second, "/actuator/health");
            assertHealth(second, down, 503, "DOWN");
            assertProbesUp(second);
            assertSafeResponse(down, jdbcUrl, username);
        } finally {
            if (captured != null) {
                captured.close();
            }
            if (second != null) {
                second.close();
            }
            if (first != null) {
                first.close();
            }
            if (mysql.isRunning()) {
                mysql.stop();
            }
        }
    }

    private static void assertLocalMarketChecks(ConfigurableApplicationContext context) throws Exception {
        var repository = context.getBean(IntegrityCheckRepository.class);
        var jdbc = context.getBean(JdbcTemplate.class);
        var emptyBefore = marketRows(jdbc);
        var emptyId = submitMarketCheck(context);
        assertMarketReports(repository, emptyId, "0", "0");
        assertMarketHttpReports(context, emptyId, false);
        assertThat(repository.issues(emptyId, null, 1, 100).items().stream()
                .filter(issue -> issue.issue().path("reasonCode").asText().equals("REFERENCE_INCOMPLETE")))
                .extracting(issue -> issue.issue().path("apiName").asText())
                .containsExactlyInAnyOrder("daily", "monthly", "weekly");
        assertThat(marketRows(jdbc)).isEqualTo(emptyBefore);

        // Controlled calendar: Feb 27/28 are the only open days; leap day is closed.
        // The requested partial week/month ends Feb 29, but weekly references extend to Mar 3.
        for (var date = LocalDate.parse("2024-02-01"); !date.isAfter(LocalDate.parse("2024-03-03")); date = date.plusDays(1)) {
            jdbc.update("INSERT INTO tushare_pro__trade_cal(exchange,cal_date,is_open,source_plugin,source_api,ingested_at) "
                            + "VALUES ('SSE',?,?,'tushare_pro','trade_cal','2024-04-01 00:00:00')",
                    date, date.equals(LocalDate.parse("2024-02-27")) || date.equals(LocalDate.parse("2024-02-28")) ? 1L : 0L);
        }
        jdbc.update("INSERT INTO tushare_pro__stock_basic(ts_code,list_date,source_plugin,source_api,ingested_at) "
                + "VALUES ('600000.SH','2000-01-01','tushare_pro','stock_basic','2024-04-01 00:00:00')");
        jdbc.update("INSERT INTO tushare_pro__suspend_d(ts_code,trade_date,suspend_type,suspend_timing,source_plugin,source_api,ingested_at) "
                + "VALUES ('600000.SH','2024-02-28','S','09:30-10:30','tushare_pro','suspend_d','2024-04-01 00:00:00')");
        for (var api : List.of("daily", "weekly", "monthly")) {
            jdbc.update("INSERT INTO tushare_pro__" + api + "(ts_code,trade_date,source_plugin,source_api,ingested_at) "
                    + "VALUES ('600000.SH','2024-02-27','tushare_pro',?,'2024-04-01 00:00:00')", api);
        }
        var before = marketRows(jdbc);
        var checkId = submitMarketCheck(context);
        assertMarketReports(repository, checkId, "1", "1");
        assertMarketHttpReports(context, checkId, true);
        var issues = repository.issues(checkId, null, 1, 100).items();
        var suspected = issues.stream().filter(i -> i.issue().path("type").asText().equals("SUSPECTED_MISSING")).toList();
        assertThat(suspected).hasSize(3).allSatisfy(item -> {
            var issue = item.issue();
            assertThat(item.ruleVersion()).isEqualTo("2");
            assertThat(issue.path("status").asText()).isEqualTo("WARN");
            assertThat(issue.path("date").asText()).isEqualTo("2024-02-28");
            assertThat(issue.path("businessKey").size()).isEqualTo(2);
            assertThat(issue.path("businessKey").path("ts_code").asText()).isEqualTo("600000.SH");
            assertThat(issue.path("businessKey").path("trade_date").asText()).isEqualTo("2024-02-28");
        });
        assertThat(suspected).extracting(i -> i.issue().path("apiName").asText())
                .containsExactlyInAnyOrder("daily", "weekly", "monthly");
        assertThat(issues).noneSatisfy(i -> assertThat(i.issue().path("type").asText()).isIn("MISSING", "EXTRA"));
        assertThat(marketRows(jdbc)).isEqualTo(before);
        // A later reference change must not rewrite the previously persisted UNKNOWN report.
        assertThat(repository.results(emptyId, null, 1, 100).items()).allSatisfy(result ->
                assertThat(result.report().path("statistics").path("actualCount").asText()).isEqualTo("0"));
    }

    private static UUID submitMarketCheck(ConfigurableApplicationContext context) throws Exception {
        var mapper = context.getBean(ObjectMapper.class);
        var capabilities = get(context, "/api/v1/data-sources/tushare_pro/integrity-capabilities");
        assertThat(capabilities.status()).isEqualTo(200);
        assertSecurityHeaders(capabilities);
        var capability = mapper.readTree(capabilities.body());
        assertThat(capability.path("localCheckAvailable").booleanValue()).isTrue();
        assertThat(capability.path("apis")).hasSize(40);
        assertThat(capability.path("limits").path("maxScannedRowsPerUnit").isTextual()).isTrue();
        var expectedOrder = context.getBean(IntegrityCheckService.class).capability(PluginId.of("tushare_pro"))
                .apis().stream().map(api -> api.definition().datasetKey().apiName().value()).toList();
        var actualOrder = new ArrayList<String>();
        var nonStock = new ArrayList<String>();
        for (var api : capability.path("apis")) {
            String apiName = api.path("apiName").asText();
            actualOrder.add(apiName);
            assertThat(api.path("downloadAvailability").path("single").path("available").booleanValue()).isFalse();
            var descriptor = api.path("descriptor");
            assertThat(descriptor.path("capabilityVersion").asText()).isEqualTo(
                    List.of("daily", "weekly", "monthly").contains(apiName) ? "2" : "1");
            if (descriptor.path("scopeKind").asText().equals("NON_STOCK")) {
                nonStock.add(apiName);
                assertThat(descriptor.path("symbolField").isNull()).isTrue();
                assertThat(descriptor.path("dateField").isNull()).isTrue();
                assertThat(descriptor.path("rules")).isEmpty();
            }
        }
        assertThat(actualOrder).containsExactlyElementsOf(expectedOrder);
        assertThat(nonStock).containsExactlyInAnyOrder("margin", "slb_len", "index_classify", "trade_cal");
        var submissionId = UUID.randomUUID().toString();
        var request = Map.of("submissionId", submissionId, "pluginId", "tushare_pro",
                "symbols", List.of("600000.SH"), "startDate", "2024-02-26", "endDate", "2024-02-29",
                "apiNames", List.of("daily", "weekly", "monthly"), "capabilityHash", capability.path("capabilityHash").asText());
        var created = post(context, "/api/v1/integrity-checks", mapper.writeValueAsString(request));
        assertThat(created.status()).isEqualTo(202);
        assertSecurityHeaders(created);
        var receipt = mapper.readTree(created.body());
        assertThat(receipt.path("requestId").asText()).isEqualTo(created.header(RequestIdFilter.HEADER_NAME));
        String location = "/api/v1/integrity-checks/" + receipt.path("checkId").asText();
        assertThat(created.header("Location")).isEqualTo(location);
        assertThat(receipt.path("plannedUnits").intValue()).isEqualTo(3);
        var replay = post(context, "/api/v1/integrity-checks", mapper.writeValueAsString(request));
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.header("Location")).isEqualTo(location);
        assertThat(mapper.readTree(replay.body()).path("checkId")).isEqualTo(receipt.path("checkId"));
        var history = get(context, "/api/v1/integrity-checks?submissionId=" + submissionId + "&pageSize=17");
        assertThat(history.status()).isEqualTo(200);
        var page = mapper.readTree(history.body());
        assertThat(page.path("total").isTextual()).isTrue();
        assertThat(page.path("total").asText()).isEqualTo("1");
        assertThat(page.path("pageSize").intValue()).isEqualTo(17);
        assertThat(page.path("items").get(0).path("originalRequest")).isEqualTo(mapper.valueToTree(request));
        assertThat(page.path("items").get(0).path("checkId")).isEqualTo(receipt.path("checkId"));
        var changed = new LinkedHashMap<String, Object>(request);
        changed.put("symbols", List.of("600001.SH"));
        var conflict = post(context, "/api/v1/integrity-checks", mapper.writeValueAsString(changed));
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(mapper.readTree(conflict.body()).path("code").asText()).isEqualTo("SUBMISSION_CONFLICT");
        for (var field : List.of("extra", "symbols", "apiNames", "startDate", "pluginId", "capabilityHash")) {
            var invalid = new LinkedHashMap<String, Object>(request);
            invalid.put("submissionId", UUID.randomUUID().toString());
            invalid.put(field, switch (field) {
                case "extra" -> true;
                case "symbols" -> List.of(123);
                case "apiNames" -> List.of();
                case "startDate" -> "2024-02-30";
                case "pluginId" -> null;
                default -> 123;
            });
            var error = post(context, "/api/v1/integrity-checks", mapper.writeValueAsString(invalid));
            assertThat(error.status()).as(field).isEqualTo(400);
            var errorBody = mapper.readTree(error.body());
            assertThat(errorBody.path("code").asText()).as(field)
                    .isEqualTo(field.equals("pluginId") ? "PARAM_REQUIRED" : "PARAM_INVALID");
            assertThat(errorBody.path("requestId").asText()).isEqualTo(error.header(RequestIdFilter.HEADER_NAME));
        }
        return UUID.fromString(receipt.path("checkId").asText());
    }

    private static void assertMarketHttpReports(ConfigurableApplicationContext context, UUID id,
            boolean hasCandidates) throws Exception {
        var mapper = context.getBean(ObjectMapper.class);
        String location = "/api/v1/integrity-checks/" + id;
        var detail = get(context, location);
        assertThat(detail.status()).isEqualTo(200);
        assertSecurityHeaders(detail);
        var body = mapper.readTree(detail.body());
        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("overallStatus").asText()).isEqualTo("UNKNOWN");
        assertThat(body.path("completedUnits").asText()).isEqualTo("3");
        assertThat(body.path("completedUnits").isTextual()).isTrue();
        assertThat(body.path("errorUnits").asText()).isEqualTo("0");
        assertThat(body.path("statusCounts").path("UNKNOWN").asText()).isEqualTo("3");
        var results = get(context, location + "/results?symbol=600000.SH&overallStatus=UNKNOWN&pageSize=100");
        assertThat(results.status()).isEqualTo(200);
        var resultPage = mapper.readTree(results.body());
        assertThat(resultPage.path("total").asText()).isEqualTo("3");
        assertThat(resultPage.path("items")).hasSize(3);
        for (var result : resultPage.path("items")) {
            var report = result.path("report");
            assertThat(report.path("scope").path("startDate").asText()).isEqualTo("2024-02-26");
            assertThat(report.path("scope").path("endDate").asText()).isEqualTo("2024-02-29");
            assertThat(report.path("descriptor").path("rules").get(0).path("version").asText()).isEqualTo("2");
            assertThat(report.path("statistics").path("expectedCount").isNull()).isTrue();
            assertThat(report.path("statistics").path("coverageRate").isNull()).isTrue();
        }
        var allIssues = get(context, location + "/issues?pageSize=100");
        assertThat(allIssues.status()).isEqualTo(200);
        assertThat(mapper.readTree(allIssues.body()).path("items")).isNotEmpty();
        if (hasCandidates) {
            var filtered = get(context, location + "/issues?symbol=600000.SH&type=SUSPECTED_MISSING&status=WARN"
                    + "&dateFrom=2024-02-28&dateTo=2024-02-28&pageSize=17");
            assertThat(filtered.status()).isEqualTo(200);
            var issues = mapper.readTree(filtered.body());
            assertThat(issues.path("total").asText()).isEqualTo("3");
            for (var item : issues.path("items")) {
                assertThat(item.path("issueId").isTextual()).isTrue();
                assertThat(item.path("ruleVersion").asText()).isEqualTo("2");
                assertThat(item.path("issue").path("date").asText()).isEqualTo("2024-02-28");
                assertThat(item.path("issue").path("businessKey").path("ts_code").asText()).isEqualTo("600000.SH");
            }
        }
        var foreign = get(context, location + "/issues?resultId=" + UUID.randomUUID());
        assertThat(foreign.status()).isEqualTo(200);
        assertThat(mapper.readTree(foreign.body()).path("items")).isEmpty();
        var beyond = get(context, location + "/results?page=999&pageSize=17");
        assertThat(beyond.status()).isEqualTo(200);
        assertThat(mapper.readTree(beyond.body()).path("items")).isEmpty();
        assertThat(detail.body() + results.body() + allIssues.body()).doesNotContain("requestHash", "unitKey", SECRET);
    }

    private static void assertMarketReports(IntegrityCheckRepository repository, UUID checkId,
            String actualCount, String suspectedCount) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            var status = repository.find(checkId).orElseThrow().status();
            if (status != IntegrityTaskStatus.QUEUED && status != IntegrityTaskStatus.RUNNING) break;
            Thread.sleep(25);
        }
        var progress = repository.progress(checkId).orElseThrow();
        assertThat(progress.task().status()).isEqualTo(IntegrityTaskStatus.COMPLETED);
        assertThat(progress.task().plannedUnits()).isEqualTo(3);
        assertThat(progress.completedUnits()).isEqualTo(3);
        assertThat(progress.errorUnits()).isZero();
        assertThat(progress.overallStatus()).isEqualTo(IntegrityStatus.UNKNOWN);
        assertThat(repository.results(checkId, null, 1, 100).items()).hasSize(3).allSatisfy(result -> {
            var report = result.report();
            assertThat(report.path("scope").path("startDate").asText()).isEqualTo("2024-02-26");
            assertThat(report.path("scope").path("endDate").asText()).isEqualTo("2024-02-29");
            assertThat(report.path("coverageStatus").asText()).isEqualTo("UNKNOWN");
            assertThat(report.path("unitStatus").asText()).isEqualTo("COMPLETED");
            assertThat(report.path("incomplete").asBoolean()).isFalse();
            assertThat(report.path("statistics").path("actualCount").asText()).isEqualTo(actualCount);
            assertThat(report.path("statistics").path("suspectedMissingCount").asText()).isEqualTo(suspectedCount);
            assertThat(report.path("statistics").path("expectedCount").isNull()).isTrue();
            assertThat(report.path("statistics").path("matchedCount").isNull()).isTrue();
            assertThat(report.path("statistics").path("coverageRate").isNull()).isTrue();
        });
    }

    private static Map<String, List<Map<String, Object>>> marketRows(JdbcTemplate jdbc) {
        var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var api : List.of("daily", "weekly", "monthly", "trade_cal", "stock_basic", "suspend_d")) {
            rows.put(api, jdbc.queryForList("SELECT * FROM tushare_pro__" + api + " ORDER BY 1,2"));
        }
        return rows;
    }

    private static ConfigurableApplicationContext start(
            MySQLContainer<?> mysql, String token) {
        // The test classpath also contains acceptance-only V6; inspect the production resource output.
        Path migrations = Path.of(URI.create(ApplicationConfiguration.class.getProtectionDomain()
                .getCodeSource().getLocation().toExternalForm())).resolve("db/migration");
        return new SpringApplicationBuilder(TensorApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(context -> context.getBeanFactory().registerSingleton(
                        "productionTestTypeExcludeFilter", new TestTypeExcludeFilter()))
                .properties(
                        "server.port=0",
                        "spring.profiles.active=production",
                        "spring.flyway.locations=filesystem:" + migrations,
                        "spring.datasource.hikari.connection-timeout=250",
                        "TENSOR_DB_URL=" + mysql.getJdbcUrl(),
                        "TENSOR_DB_USERNAME=" + mysql.getUsername(),
                        "TENSOR_DB_PASSWORD=" + SECRET,
                        "TENSOR_TUSHARE_TOKEN=" + token)
                .run();
    }

    private static final class TestTypeExcludeFilter extends TypeExcludeFilter {
        @Override
        public boolean match(
                MetadataReader reader, MetadataReaderFactory factory) {
            return reader.getClassMetadata().getClassName().equals(
                    "com.akkc.tensor.web.GlobalExceptionHandlerTest$FailureController");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestTypeExcludeFilter;
        }

        @Override
        public int hashCode() {
            return TestTypeExcludeFilter.class.hashCode();
        }
    }

    private static void assertProductionGraph(ConfigurableApplicationContext context) {
        assertUnique(context, ApplicationConfiguration.class);
        assertUnique(context, PluginRegistry.class);
        assertUnique(context, DatasetCatalog.class);
        assertUnique(context, AdapterRegistry.class);
        assertUnique(context, DownloadService.class);
        assertUnique(context, DatasetQueryService.class);
        assertUnique(context, MetadataQueryService.class);
        assertUnique(context, TensorMetrics.class);
        assertUnique(context, OperationLogger.class);
        assertUnique(context, DownloadTaskOperationLogger.class);
        assertUnique(context, DownloadTaskProperties.class);
        assertUnique(context, DownloadTaskService.class);
        assertUnique(context, DownloadTaskQueryService.class);
        assertUnique(context, DownloadTaskRunner.class);
        assertUnique(context, DownloadTaskCoordinator.class);
        assertThat(context.getBean(DownloadTaskCoordinator.class).isRunning()).isTrue();
        assertUnique(context, com.akkc.tensor.core.integrity.IntegrityCheckService.class);
        assertUnique(context, com.akkc.tensor.core.integrity.IntegrityCheckRepository.class);
        assertUnique(context, com.akkc.tensor.core.integrity.IntegrityCheckQueue.class);
        var localCheck = context.getBean(com.akkc.tensor.core.integrity.IntegrityCheckService.class)
                .capability(com.akkc.tensor.plugin.api.model.PluginId.of("tushare_pro"));
        assertThat(localCheck.localCheckAvailable()).isTrue();
        assertThat(localCheck.apis()).hasSize(40);
        assertThat(localCheck.capabilityHash()).matches("[0-9a-f]{64}");
        assertUnique(context, DataSourceController.class);
        assertUnique(context, DownloadController.class);
        assertUnique(context, DownloadDescriptorResolver.class);
        assertUnique(context, DownloadParameterResolver.class);
        assertUnique(context, DownloadRequestDeserializer.class);
        assertUnique(context, DatasetController.class);
        assertUnique(context, GlobalExceptionHandler.class);
        assertUnique(context, RequestIdFilter.class);
        assertThat(context.getBean("securityHeadersFilter"))
                .isInstanceOf(FilterRegistrationBean.class);
        assertThat(context.getBeansOfType(FilterRegistrationBean.class))
                .containsKey("securityHeadersFilter");

        Flyway flyway = context.getBean(Flyway.class);
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2", "3", "4", "5", "7", "8", "9");
        assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'fixture__fixture_daily'",
                Integer.class)).isZero();
        List<?> definitions = context.getBean("tushareDatasetDefinitions", List.class);
        List<?> adapters = context.getBean("tensorDatasetAdapters", List.class);
        assertThat(definitions).hasSize(40).allSatisfy(
                definition -> assertThat(definition).isInstanceOf(DatasetDefinition.class));
        assertThat(adapters).hasSize(40).allSatisfy(
                adapter -> assertThat(adapter).isInstanceOf(DatasetAdapter.class));

        DatasetCatalog catalog = context.getBean(DatasetCatalog.class);
        AdapterRegistry adapterRegistry = context.getBean(AdapterRegistry.class);
        for (Object candidate : definitions) {
            DatasetDefinition definition = (DatasetDefinition) candidate;
            assertThat(catalog.find(definition.datasetKey())).isPresent();
            assertThat(adapterRegistry.find(definition.datasetKey())).isPresent();
        }
    }

    private static void assertUnique(
            ConfigurableApplicationContext context, Class<?> type) {
        assertThat(context.getBeansOfType(type)).hasSize(1);
    }

    private static void assertMetadata(
            ConfigurableApplicationContext context,
            HttpResponse response,
            boolean credentialConfigured,
            boolean downloadAvailable) throws Exception {
        assertThat(response.status()).isEqualTo(200);
        assertSecurityHeaders(response);
        JsonNode body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body.isArray()).isTrue();
        JsonNode tushare = null;
        for (JsonNode candidate : body) {
            if ("tushare_pro".equals(candidate.path("pluginId").textValue())) {
                tushare = candidate;
            }
        }
        assertThat(tushare).isNotNull();
        assertThat(tushare.path("credentialConfigured").booleanValue())
                .isEqualTo(credentialConfigured);
        assertThat(tushare.path("downloadAvailable").booleanValue())
                .isEqualTo(downloadAvailable);

        PluginDescriptor descriptor = context.getBean(PluginRegistry.class).descriptors().stream()
                .filter(value -> value.pluginId().value().equals("tushare_pro"))
                .findFirst()
                .orElseThrow();
        assertThat(descriptor.enabled()).isTrue();
        assertThat(descriptor.credentialConfigured()).isEqualTo(credentialConfigured);
        assertThat(descriptor.downloadAvailable()).isEqualTo(downloadAvailable);
    }

    private static void assertHealth(
            ConfigurableApplicationContext context,
            HttpResponse response,
            int status,
            String health) throws Exception {
        assertThat(response.status()).isEqualTo(status);
        assertSecurityHeaders(response);
        JsonNode body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body).isEqualTo(context.getBean(ObjectMapper.class)
                .valueToTree(Map.of("status", health)));
    }

    private static void assertProbesUp(ConfigurableApplicationContext context) throws Exception {
        for (String probe : List.of("liveness", "readiness")) {
            assertHealth(context, get(context, "/actuator/health/" + probe), 200, "UP");
        }
    }

    private static void assertSafeParameterError(
            ConfigurableApplicationContext context, HttpResponse response) throws Exception {
        assertThat(response.status()).isEqualTo(400);
        assertSecurityHeaders(response);
        JsonNode body = context.getBean(ObjectMapper.class).readTree(response.body());
        assertThat(body.path("code").textValue()).isEqualTo("PARAM_INVALID");
        assertThat(body.path("message").textValue()).isEqualTo("Parameters are invalid");
        assertThat(body.path("fieldErrors").path(0).path("field").textValue())
                .isEqualTo("page");
        assertThat(response.body()).doesNotContain(INVALID_PAGE);
    }

    private static void assertHidden(
            ConfigurableApplicationContext context, String jdbcUrl, String username) {
        for (String path : HIDDEN_ENDPOINTS) {
            HttpResponse response = get(context, path);
            assertThat(response.status()).isEqualTo(404);
            assertSecurityHeaders(response);
            assertSafeResponse(response, jdbcUrl, username);
        }
    }

    private static void assertSafeResponse(
            HttpResponse response, String jdbcUrl, String username) {
        assertThat(response.body()).doesNotContain(SECRET, jdbcUrl, username);
    }

    private static void assertSecurityHeaders(HttpResponse response) {
        assertThat(response.header(RequestIdFilter.HEADER_NAME)).isNotBlank();
        assertThat(response.header("Content-Security-Policy")).isEqualTo(
                "default-src 'self'; base-uri 'none'; object-src 'none'; "
                        + "frame-ancestors 'none'; form-action 'self'; "
                        + "script-src 'self'; style-src 'self'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'");
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.header("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.header("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.header("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
        assertThat(response.header("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
        assertThat(response.header("Cache-Control")).isEqualTo("no-store");
    }

    private static HttpResponse get(ConfigurableApplicationContext context, String path) {
        return http(context).get().uri(path).exchange(ProductionApplicationContextIT::readResponse);
    }

    private static HttpResponse post(ConfigurableApplicationContext context, String path, String body) {
        return http(context).post().uri(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body).exchange(ProductionApplicationContextIT::readResponse);
    }

    private static RestClient http(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return RestClient.create("http://127.0.0.1:" + port);
    }

    private static HttpResponse readResponse(org.springframework.http.HttpRequest request,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        response.getHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) headers.put(name, values.getFirst());
        });
        return new HttpResponse(response.getStatusCode().value(), headers,
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static CapturedLog captureRootLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new CapturedLog(logger, appender);
    }

    private record HttpResponse(int status, Map<String, String> headers, String body) {
        private HttpResponse {
            headers = Map.copyOf(headers);
        }

        private String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }

    private record CapturedLog(
            Logger logger,
            ListAppender<ILoggingEvent> appender) implements AutoCloseable {
        private String text() {
            List<String> values = new ArrayList<>();
            for (ILoggingEvent event : appender.list) {
                values.add(event.getFormattedMessage());
                IThrowableProxy throwable = event.getThrowableProxy();
                while (throwable != null) {
                    values.add(String.valueOf(throwable.getMessage()));
                    throwable = throwable.getCause();
                }
            }
            return String.join("\n", values);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
