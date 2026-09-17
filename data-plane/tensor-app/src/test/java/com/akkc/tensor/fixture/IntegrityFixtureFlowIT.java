package com.akkc.tensor.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.TensorApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class IntegrityFixtureFlowIT {
    private static final String SECRET = "integrity-fixture-secret";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void fixtureCompletesRealHttpSqlFlowAndPreservesVersionedHistory() throws Exception {
        var upstreamCalls = new AtomicInteger();
        var upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            upstreamCalls.incrementAndGet();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        });
        upstream.start();
        try (var mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
                .withDatabaseName("tensor").withUsername("tensor").withPassword(SECRET)) {
            mysql.start();
            String checkA;
            Map<String, Object> requestA;
            String detailA;
            String resultsA;
            String issuesA;
            String databaseReportA;
            FixtureSnapshot seeded;

            try (var application = start(mysql, "2", upstream.getAddress().getPort(), "")) {
                var jdbc = application.getBean(JdbcTemplate.class);
                seedProvenExtra(jdbc);
                seeded = fixtureSnapshot(jdbc);
                assertThat(seeded.rows()).hasSize(20);
                assertNoDownloadRows(jdbc);

                JsonNode capabilityA = capability(application, "2", List.of(
                        Map.entry("fixture.coverage.fixture_daily", "2")));
                requestA = request(UUID.randomUUID(), capabilityA.path("capabilityHash").asText(),
                        "PROVEN_EXTRA", "2026-01-01", "2026-01-21");
                var created = post(application, "/api/v1/integrity-checks", requestA);
                assertThat(created.statusCode()).isEqualTo(202);
                JsonNode receipt = JSON.readTree(created.body());
                checkA = receipt.path("checkId").asText();
                String location = "/api/v1/integrity-checks/" + checkA;
                assertThat(created.headers().firstValue("Location")).contains(location);
                assertThat(receipt.path("plannedUnits").intValue()).isEqualTo(1);

                var replay = post(application, "/api/v1/integrity-checks", requestA);
                assertThat(replay.statusCode()).isEqualTo(200);
                assertThat(replay.headers().firstValue("Location")).contains(location);
                assertThat(JSON.readTree(replay.body()).path("checkId").asText()).isEqualTo(checkA);
                var conflicting = new LinkedHashMap<>(requestA);
                conflicting.put("symbols", List.of("PROVEN"));
                assertError(post(application, "/api/v1/integrity-checks", conflicting), 409, "SUBMISSION_CONFLICT");

                JsonNode detail = awaitCompleted(application, checkA);
                assertThat(detail.path("status").asText()).isEqualTo("COMPLETED");
                assertThat(detail.path("overallStatus").asText()).isEqualTo("FAIL");
                assertText(detail.path("completedUnits"), "1");
                assertThat(detail.path("scope").path("symbols")).hasSize(1);
                assertThat(detail.path("scope").path("symbols").get(0).asText()).isEqualTo("PROVEN_EXTRA");

                var history = get(application, "/api/v1/integrity-checks?submissionId="
                        + requestA.get("submissionId") + "&pluginId=fixture&status=COMPLETED&pageSize=17");
                assertThat(history.statusCode()).isEqualTo(200);
                JsonNode historyJson = JSON.readTree(history.body());
                assertText(historyJson.path("total"), "1");
                assertThat(historyJson.path("items").get(0).path("checkId").asText()).isEqualTo(checkA);
                assertThat(historyJson.path("items").get(0).path("originalRequest"))
                        .isEqualTo(JSON.valueToTree(requestA));

                JsonNode resultPage = json(get(application,
                        location + "/results?symbol=PROVEN_EXTRA&apiName=fixture_daily&overallStatus=FAIL&pageSize=17"), 200);
                assertText(resultPage.path("total"), "1");
                JsonNode report = resultPage.path("items").get(0).path("report");
                assertProvenExtraReport(report, "2");
                assertThat(report.path("descriptor").path("rules")).singleElement().satisfies(rule -> {
                    assertThat(rule.path("ruleId").asText()).isEqualTo("fixture.coverage.fixture_daily");
                    assertThat(rule.path("version").asText()).isEqualTo("2");
                });
                assertThat(report.path("evidence")).anySatisfy(evidence ->
                        assertThat(evidence.path("summary").asText()).contains("fixture", "非 Tushare"));

                JsonNode issuePage = json(get(application, location + "/issues?pageSize=17"), 200);
                assertText(issuePage.path("total"), "2");
                assertIssue(application, location, "MISSING", "FAIL", "2026-01-20");
                assertIssue(application, location, "EXTRA", "WARN", "2026-01-21");

                assertRecursiveSql(jdbc);
                String provenCheckId = assertScenario(application, capabilityA, "PROVEN", "2026-01-01", "2026-01-20",
                        "FAIL", "20", false, "20");
                assertProvenSqlAndIssues(jdbc, application, provenCheckId);
                assertScenario(application, capabilityA, "PROVEN_EMPTY", "2026-01-01", "2026-01-21",
                        "PASS", "0", true, "0");
                assertScenario(application, capabilityA, "UNCONFIRMED", "2026-01-01", "2026-01-20",
                        "UNKNOWN", null, true, "20");

                assertThat(fixtureSnapshot(jdbc)).isEqualTo(seeded);
                assertNoDownloadRows(jdbc);
                assertThat(upstreamCalls).hasValue(0);
                detailA = get(application, location).body();
                resultsA = get(application, location + "/results?pageSize=100").body();
                issuesA = get(application, location + "/issues?pageSize=100").body();
                databaseReportA = jdbc.queryForObject(
                        "SELECT CAST(report AS CHAR) FROM tensor_integrity_check_result WHERE check_id=?",
                        String.class, checkA);
            }

            try (var application = start(
                    mysql, "3", upstream.getAddress().getPort(), "integrity-fixture-token")) {
                var jdbc = application.getBean(JdbcTemplate.class);
                JsonNode capabilityB = capability(application, "3", List.of(
                        Map.entry("fixture.coverage.fixture_daily", "3"),
                        Map.entry("fixture.acceptance.extension", "1")));
                assertThat(capabilityB.path("capabilityHash").asText()).isNotEqualTo(requestA.get("capabilityHash"));

                String locationA = "/api/v1/integrity-checks/" + checkA;
                assertThat(get(application, locationA).body()).isEqualTo(detailA);
                assertThat(get(application, locationA + "/results?pageSize=100").body()).isEqualTo(resultsA);
                assertThat(get(application, locationA + "/issues?pageSize=100").body()).isEqualTo(issuesA);
                assertThat(jdbc.queryForObject(
                        "SELECT CAST(report AS CHAR) FROM tensor_integrity_check_result WHERE check_id=?",
                        String.class, checkA)).isEqualTo(databaseReportA);

                var oldReplay = post(application, "/api/v1/integrity-checks", requestA);
                assertThat(oldReplay.statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(oldReplay.body()).path("checkId").asText()).isEqualTo(checkA);
                var stale = new LinkedHashMap<>(requestA);
                stale.put("submissionId", UUID.randomUUID().toString());
                assertError(post(application, "/api/v1/integrity-checks", stale),
                        409, "INTEGRITY_DEFINITION_CHANGED");

                Map<String, Object> requestB = request(UUID.randomUUID(), capabilityB.path("capabilityHash").asText(),
                        "PROVEN_EXTRA", "2026-01-01", "2026-01-21");
                JsonNode receiptB = json(post(application, "/api/v1/integrity-checks", requestB), 202);
                String checkB = receiptB.path("checkId").asText();
                assertThat(awaitCompleted(application, checkB).path("overallStatus").asText()).isEqualTo("FAIL");
                JsonNode reportB = json(get(application,
                        "/api/v1/integrity-checks/" + checkB + "/results?pageSize=20"), 200)
                        .path("items").get(0).path("report");
                assertProvenExtraReport(reportB, "3");
                JsonNode savedCoverage = rule(reportB.path("ruleResults"), "fixture.coverage.fixture_daily");
                assertThat(savedCoverage.path("descriptor").path("version").asText()).isEqualTo("3");
                assertThat(savedCoverage.path("evidence")).isNotEmpty().allSatisfy(evidence ->
                        assertThat(evidence.path("ruleVersion").asText()).isEqualTo("3"));
                assertThat(rule(reportB.path("descriptor").path("rules"), "fixture.coverage.fixture_daily")
                        .path("version").asText()).isEqualTo("3");
                assertThat(reportB.path("ruleResults")).anySatisfy(rule -> {
                    assertThat(rule.path("descriptor").path("ruleId").asText())
                            .isEqualTo("fixture.acceptance.extension");
                    assertThat(rule.path("descriptor").path("version").asText()).isEqualTo("1");
                    assertThat(rule.path("status").asText()).isEqualTo("PASS");
                    assertThat(rule.path("reasonCode").asText()).isEqualTo("FIXTURE_EXTENSION_VERIFIED");
                    assertThat(rule.path("evidence").get(0).path("summary").asText())
                            .contains("验收扩展规则已执行", "acceptance");
                });
                JsonNode oldReport = json(get(application, locationA + "/results?pageSize=20"), 200)
                        .path("items").get(0).path("report");
                assertThat(oldReport.path("descriptor").path("capabilityVersion").asText()).isEqualTo("2");
                assertThat(oldReport.path("ruleResults").toString()).doesNotContain("fixture.acceptance.extension");
                JsonNode issuesB = json(get(application,
                        "/api/v1/integrity-checks/" + checkB + "/issues?pageSize=100"), 200);
                assertText(issuesB.path("total"), "2");
                assertThat(issuesB.path("items")).allSatisfy(issue ->
                        assertThat(issue.path("ruleVersion").asText()).isEqualTo("3"));
                assertThat(fixtureSnapshot(jdbc)).isEqualTo(seeded);
                assertNoDownloadRows(jdbc);
                assertThat(upstreamCalls).hasValue(0);
            }
        } finally {
            upstream.stop(0);
        }
    }

    private static JsonNode capability(ConfigurableApplicationContext application, String version,
            List<Map.Entry<String, String>> rules) throws Exception {
        JsonNode capability = json(get(application, "/api/v1/data-sources/fixture/integrity-capabilities"), 200);
        assertThat(capability.path("pluginId").asText()).isEqualTo("fixture");
        assertThat(capability.path("localCheckAvailable").asBoolean()).isTrue();
        assertThat(capability.path("capabilityHash").asText()).matches("[a-f0-9]{64}");
        JsonNode api = capability.path("apis").get(0);
        assertThat(api.path("apiName").asText()).isEqualTo("fixture_daily");
        assertThat(api.path("descriptor").path("capabilityVersion").asText()).isEqualTo(version);
        assertThat(api.path("descriptor").path("rules")).hasSize(rules.size());
        for (int index = 0; index < rules.size(); index++) {
            assertThat(api.path("descriptor").path("rules").get(index).path("ruleId").asText())
                    .isEqualTo(rules.get(index).getKey());
            assertThat(api.path("descriptor").path("rules").get(index).path("version").asText())
                    .isEqualTo(rules.get(index).getValue());
        }
        return capability;
    }

    private static void assertProvenExtraReport(JsonNode report, String version) {
        assertThat(report.path("unitStatus").asText()).isEqualTo("COMPLETED");
        assertThat(report.path("overallStatus").asText()).isEqualTo("FAIL");
        assertThat(report.path("scope").path("symbol").asText()).isEqualTo("PROVEN_EXTRA");
        assertThat(report.path("scope").path("startDate").asText()).isEqualTo("2026-01-01");
        assertThat(report.path("scope").path("endDate").asText()).isEqualTo("2026-01-21");
        assertThat(report.path("descriptor").path("capabilityVersion").asText()).isEqualTo(version);
        assertText(report.at("/statistics/actualCount"), "20");
        assertText(report.at("/statistics/expectedCount"), "20");
        assertText(report.at("/statistics/matchedCount"), "19");
        assertText(report.at("/statistics/missingCount"), "1");
        assertText(report.at("/statistics/extraCount"), "1");
        assertText(report.at("/statistics/coverageRate"), "0.950000");
        assertThat(report.path("incomplete").asBoolean()).isFalse();
        assertThat(report.path("issuesComplete").asBoolean()).isTrue();
    }

    private static void assertIssue(ConfigurableApplicationContext application, String location,
            String type, String status, String date) throws Exception {
        JsonNode page = json(get(application, location + "/issues?symbol=PROVEN_EXTRA&apiName=fixture_daily&type="
                + type + "&status=" + status + "&dateFrom=" + date + "&dateTo=" + date + "&pageSize=17"), 200);
        assertText(page.path("total"), "1");
        JsonNode item = page.path("items").get(0);
        assertThat(item.path("issueId").isTextual()).isTrue();
        assertThat(item.path("ruleId").asText()).isEqualTo("fixture.coverage.fixture_daily");
        assertThat(item.path("ruleVersion").asText()).isEqualTo("2");
        JsonNode issue = item.path("issue");
        assertThat(issue.path("type").asText()).isEqualTo(type);
        assertThat(issue.path("status").asText()).isEqualTo(status);
        assertThat(issue.path("date").asText()).isEqualTo(date);
        assertThat(issue.path("businessKey")).hasSize(2);
        assertThat(issue.path("businessKey").path("ts_code").asText()).isEqualTo("PROVEN_EXTRA");
        assertThat(issue.path("businessKey").path("trade_date").asText()).isEqualTo(date);
    }

    private static String assertScenario(ConfigurableApplicationContext application, JsonNode capability,
            String symbol, String start, String end, String status, String expected, boolean nullRate,
            String issueCount) throws Exception {
        Map<String, Object> request = request(UUID.randomUUID(), capability.path("capabilityHash").asText(),
                symbol, start, end);
        JsonNode receipt = json(post(application, "/api/v1/integrity-checks", request), 202);
        String checkId = receipt.path("checkId").asText();
        JsonNode detail = awaitCompleted(application, checkId);
        assertThat(detail.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.path("overallStatus").asText()).isEqualTo(status);
        JsonNode results = json(get(application,
                "/api/v1/integrity-checks/" + checkId + "/results?symbol=" + symbol + "&pageSize=20"), 200);
        assertText(results.path("total"), "1");
        JsonNode report = results.path("items").get(0).path("report");
        if (expected == null) assertThat(report.at("/statistics/expectedCount").isNull()).isTrue();
        else assertText(report.at("/statistics/expectedCount"), expected);
        if (nullRate) assertThat(report.at("/statistics/coverageRate").isNull()).isTrue();
        else assertText(report.at("/statistics/coverageRate"), "0.000000");
        assertText(json(get(application,
                "/api/v1/integrity-checks/" + checkId + "/issues?pageSize=100"), 200)
                .path("total"), issueCount);
        if (symbol.equals("PROVEN")) {
            assertText(report.at("/statistics/missingCount"), "20");
        } else if (symbol.equals("PROVEN_EMPTY")) {
            assertThat(report.path("reasonCode").asText()).isEqualTo("VERIFIED_EMPTY");
            assertText(report.at("/statistics/actualCount"), "0");
            assertText(report.at("/statistics/matchedCount"), "0");
        } else {
            assertThat(report.at("/statistics/matchedCount").isNull()).isTrue();
            assertThat(report.at("/statistics/missingCount").isNull()).isTrue();
            assertText(report.at("/statistics/suspectedMissingCount"), "20");
        }
        return checkId;
    }

    private static void assertRecursiveSql(JdbcTemplate jdbc) {
        String expected = """
                WITH RECURSIVE expected(ts_code,trade_date) AS (
                  SELECT CAST('PROVEN_EXTRA' AS CHAR(64)) COLLATE utf8mb4_0900_as_cs, DATE('2026-01-01')
                  UNION ALL SELECT ts_code, DATE_ADD(trade_date, INTERVAL 1 DAY)
                  FROM expected WHERE trade_date < DATE('2026-01-20'))
                SELECT COUNT(*) AS expected_count, COUNT(a.trade_date) AS matched_count,
                       COUNT(*) - COUNT(a.trade_date) AS missing_count,
                       ROUND(COUNT(a.trade_date) / COUNT(*), 6) AS coverage_rate
                FROM expected e LEFT JOIN fixture__fixture_daily a
                  ON a.ts_code=e.ts_code AND a.trade_date=e.trade_date
                """;
        Map<String, Object> counts = jdbc.queryForMap(expected);
        assertThat(((Number) counts.get("expected_count")).longValue()).isEqualTo(20);
        assertThat(((Number) counts.get("matched_count")).longValue()).isEqualTo(19);
        assertThat(((Number) counts.get("missing_count")).longValue()).isEqualTo(1);
        assertThat((BigDecimal) counts.get("coverage_rate")).isEqualByComparingTo("0.950000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixture__fixture_daily"
                + " WHERE ts_code='PROVEN_EXTRA' AND trade_date BETWEEN '2026-01-01' AND '2026-01-21'", Long.class))
                .isEqualTo(20);
        String missing = """
                WITH RECURSIVE expected(ts_code,trade_date) AS (
                  SELECT CAST('PROVEN_EXTRA' AS CHAR(64)) COLLATE utf8mb4_0900_as_cs, DATE('2026-01-01')
                  UNION ALL SELECT ts_code, DATE_ADD(trade_date, INTERVAL 1 DAY)
                  FROM expected WHERE trade_date < DATE('2026-01-20'))
                SELECT e.ts_code,e.trade_date FROM expected e LEFT JOIN fixture__fixture_daily a
                  ON a.ts_code=e.ts_code AND a.trade_date=e.trade_date
                WHERE a.trade_date IS NULL ORDER BY e.trade_date
                """;
        assertThat(jdbc.query(missing, (row, index) ->
                new ExpectedKey(row.getString("ts_code"), row.getDate("trade_date").toLocalDate())))
                .containsExactly(new ExpectedKey("PROVEN_EXTRA", LocalDate.parse("2026-01-20")));
        String extra = """
                WITH RECURSIVE expected(ts_code,trade_date) AS (
                  SELECT CAST('PROVEN_EXTRA' AS CHAR(64)) COLLATE utf8mb4_0900_as_cs, DATE('2026-01-01')
                  UNION ALL SELECT ts_code, DATE_ADD(trade_date, INTERVAL 1 DAY)
                  FROM expected WHERE trade_date < DATE('2026-01-20'))
                SELECT a.ts_code,a.trade_date FROM fixture__fixture_daily a LEFT JOIN expected e
                  ON a.ts_code=e.ts_code AND a.trade_date=e.trade_date WHERE a.ts_code='PROVEN_EXTRA'
                  AND a.trade_date BETWEEN '2026-01-01' AND '2026-01-21'
                  AND e.trade_date IS NULL ORDER BY a.trade_date
                """;
        assertThat(jdbc.query(extra, (row, index) ->
                new ExpectedKey(row.getString("ts_code"), row.getDate("trade_date").toLocalDate())))
                .containsExactly(new ExpectedKey("PROVEN_EXTRA", LocalDate.parse("2026-01-21")));
    }

    private static void assertProvenSqlAndIssues(
            JdbcTemplate jdbc, ConfigurableApplicationContext application, String checkId) throws Exception {
        String countsSql = """
                WITH RECURSIVE expected(ts_code,trade_date) AS (
                  SELECT CAST('PROVEN' AS CHAR(64)) COLLATE utf8mb4_0900_as_cs, DATE('2026-01-01')
                  UNION ALL SELECT ts_code, DATE_ADD(trade_date, INTERVAL 1 DAY)
                  FROM expected WHERE trade_date < DATE('2026-01-20'))
                SELECT COUNT(*) AS expected_count,COUNT(a.trade_date) AS matched_count,
                       COUNT(*)-COUNT(a.trade_date) AS missing_count
                FROM expected e LEFT JOIN fixture__fixture_daily a
                  ON a.ts_code=e.ts_code AND a.trade_date=e.trade_date
                """;
        Map<String, Object> counts = jdbc.queryForMap(countsSql);
        assertThat(((Number) counts.get("expected_count")).longValue()).isEqualTo(20);
        assertThat(((Number) counts.get("matched_count")).longValue()).isZero();
        assertThat(((Number) counts.get("missing_count")).longValue()).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixture__fixture_daily"
                + " WHERE ts_code='PROVEN' AND trade_date BETWEEN '2026-01-01' AND '2026-01-20'", Long.class)).isZero();

        String keysSql = """
                WITH RECURSIVE expected(ts_code,trade_date) AS (
                  SELECT CAST('PROVEN' AS CHAR(64)) COLLATE utf8mb4_0900_as_cs, DATE('2026-01-01')
                  UNION ALL SELECT ts_code, DATE_ADD(trade_date, INTERVAL 1 DAY)
                  FROM expected WHERE trade_date < DATE('2026-01-20'))
                SELECT e.ts_code,e.trade_date FROM expected e LEFT JOIN fixture__fixture_daily a
                  ON a.ts_code=e.ts_code AND a.trade_date=e.trade_date
                WHERE a.trade_date IS NULL ORDER BY e.trade_date
                """;
        List<ExpectedKey> sqlKeys = jdbc.query(keysSql, (row, index) ->
                new ExpectedKey(row.getString("ts_code"), row.getDate("trade_date").toLocalDate()));
        assertThat(sqlKeys).containsExactlyElementsOf(provenKeys());

        JsonNode page = json(get(application, "/api/v1/integrity-checks/" + checkId
                + "/issues?symbol=PROVEN&apiName=fixture_daily&type=MISSING&status=FAIL"
                + "&dateFrom=2026-01-01&dateTo=2026-01-20&pageSize=100"), 200);
        assertText(page.path("total"), "20");
        var apiKeys = new java.util.ArrayList<ExpectedKey>();
        for (JsonNode item : page.path("items")) {
            assertThat(item.path("ruleId").asText()).isEqualTo("fixture.coverage.fixture_daily");
            assertThat(item.path("ruleVersion").asText()).isEqualTo("2");
            JsonNode issue = item.path("issue");
            assertThat(issue.path("type").asText()).isEqualTo("MISSING");
            assertThat(issue.path("status").asText()).isEqualTo("FAIL");
            assertThat(issue.path("businessKey")).hasSize(2);
            assertThat(issue.path("businessKey").path("ts_code").asText()).isEqualTo("PROVEN");
            assertThat(issue.path("businessKey").path("trade_date").asText())
                    .isEqualTo(issue.path("date").asText());
            apiKeys.add(new ExpectedKey("PROVEN", LocalDate.parse(issue.path("date").asText())));
        }
        assertThat(apiKeys).containsExactlyElementsOf(sqlKeys);
    }

    private static List<ExpectedKey> provenKeys() {
        var keys = new java.util.ArrayList<ExpectedKey>();
        for (var date = LocalDate.parse("2026-01-01"); !date.isAfter(LocalDate.parse("2026-01-20"));
                date = date.plusDays(1)) keys.add(new ExpectedKey("PROVEN", date));
        return List.copyOf(keys);
    }

    private static ConfigurableApplicationContext start(
            MySQLContainer<?> mysql, String integrityVersion, int upstreamPort, String tushareToken) {
        return new SpringApplicationBuilder(TensorApplication.class).web(WebApplicationType.SERVLET)
                .initializers(context -> context.getBeanFactory().registerSingleton(
                        "integrityFixtureTypeExcludeFilter", new TestTypeExcludeFilter()))
                .properties("server.address=127.0.0.1", "server.port=0", "spring.profiles.active=acceptance",
                        "TENSOR_DB_URL=" + mysql.getJdbcUrl(), "TENSOR_DB_USERNAME=" + mysql.getUsername(),
                        "TENSOR_DB_PASSWORD=" + SECRET, "tensor.plugins.tushare-pro.enabled=true",
                        "TENSOR_TUSHARE_TOKEN=" + tushareToken,
                        "tensor.plugins.tushare-pro.base-url=http://127.0.0.1:" + upstreamPort,
                        "tensor.plugins.fixture.enabled=true", "tensor.plugins.fixture.integrity-version=" + integrityVersion)
                .run();
    }

    private static void seedProvenExtra(JdbcTemplate jdbc) {
        for (var date = LocalDate.parse("2026-01-01"); date.isBefore(LocalDate.parse("2026-01-20"));
                date = date.plusDays(1)) insert(jdbc, "PROVEN_EXTRA", date);
        insert(jdbc, "PROVEN_EXTRA", LocalDate.parse("2026-01-21"));
    }

    private static void insert(JdbcTemplate jdbc, String symbol, LocalDate date) {
        jdbc.update("INSERT INTO fixture__fixture_daily"
                        + "(ts_code,trade_date,amount,note,source_plugin,source_api,ingested_at)"
                        + " VALUES (?,?,1.230000000000000000,NULL,'fixture','fixture_daily','2026-01-22 00:00:00')",
                symbol, date);
    }

    private static FixtureSnapshot fixtureSnapshot(JdbcTemplate jdbc) throws Exception {
        List<String> rows = jdbc.query("SELECT ts_code,trade_date,amount,note,source_plugin,source_api,ingested_at"
                        + " FROM fixture__fixture_daily ORDER BY ts_code,trade_date",
                (result, row) -> String.join("|", result.getString("ts_code"), result.getDate("trade_date").toString(),
                        result.getBigDecimal("amount").toPlainString(),
                        result.getString("note") == null ? "<NULL>" : result.getString("note"),
                        result.getString("source_plugin"), result.getString("source_api"),
                        result.getTimestamp("ingested_at").toLocalDateTime().toString()));
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", rows).getBytes(StandardCharsets.UTF_8)));
        return new FixtureSnapshot(List.copyOf(rows), sha);
    }

    private static void assertNoDownloadRows(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_task", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tensor_download_batch", Long.class)).isZero();
    }

    private static JsonNode rule(JsonNode rules, String id) {
        for (JsonNode rule : rules) {
            JsonNode descriptor = rule.has("descriptor") ? rule.path("descriptor") : rule;
            if (descriptor.path("ruleId").asText().equals(id)) return rule;
        }
        throw new AssertionError("Missing rule " + id);
    }

    private static void assertText(JsonNode value, String expected) {
        assertThat(value.isTextual()).isTrue();
        assertThat(value.asText()).isEqualTo(expected);
    }

    private static Map<String, Object> request(UUID submissionId, String capabilityHash,
            String symbol, String start, String end) {
        var request = new LinkedHashMap<String, Object>();
        request.put("submissionId", submissionId.toString());
        request.put("pluginId", "fixture");
        request.put("capabilityHash", capabilityHash);
        request.put("symbols", List.of(symbol));
        request.put("startDate", start);
        request.put("endDate", end);
        request.put("apiNames", List.of("fixture_daily"));
        return request;
    }

    private static JsonNode awaitCompleted(ConfigurableApplicationContext application, String checkId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        JsonNode detail = null;
        while (System.nanoTime() < deadline) {
            detail = json(get(application, "/api/v1/integrity-checks/" + checkId), 200);
            if (!List.of("QUEUED", "RUNNING").contains(detail.path("status").asText())) return detail;
            Thread.sleep(20);
        }
        return detail;
    }

    private static void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(JSON.readTree(response.body()).path("code").asText()).isEqualTo(code);
    }

    private static JsonNode json(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        return JSON.readTree(response.body());
    }

    private static HttpResponse<String> get(ConfigurableApplicationContext application, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(application, path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(
            ConfigurableApplicationContext application, String path, Map<String, Object> body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(application, path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(ConfigurableApplicationContext application, String path) {
        int port = ((WebServerApplicationContext) application).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private record ExpectedKey(String symbol, LocalDate date) {}
    private record FixtureSnapshot(List<String> rows, String sha256) {}

    private static final class TestTypeExcludeFilter extends TypeExcludeFilter {
        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
            return reader.getClassMetadata().getClassName().equals(
                    "com.akkc.tensor.web.GlobalExceptionHandlerTest$FailureController");
        }

        @Override public boolean equals(Object other) { return other instanceof TestTypeExcludeFilter; }
        @Override public int hashCode() { return TestTypeExcludeFilter.class.hashCode(); }
    }
}
