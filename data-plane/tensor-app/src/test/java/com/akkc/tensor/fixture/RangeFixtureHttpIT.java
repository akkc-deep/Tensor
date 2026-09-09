package com.akkc.tensor.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.TensorApplication;
import com.akkc.tensor.fixture.support.ControlledRangeSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

class RangeFixtureHttpIT {
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.context.annotation.ComponentScan(basePackages = "com.akkc.tensor", excludeFilters = {
        @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.REGEX, pattern = ".*(Test|IT)(\\$.*)?"),
        @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = TensorApplication.class)
    })
    static class HttpTestApplication {}
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName("tensor_range_http").withUsername("tensor").withPassword("tensor")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs");
    static JdbcTemplate sql;
    ControlledRangeSource source;
    ServletWebServerApplicationContext app;
    final HttpClient http = HttpClient.newHttpClient();
    final List<JsonNode> responses = new ArrayList<>();
    Map<String,Object> schemaBefore;
    static final Path EVIDENCE = Path.of("../../.superpowers/sdd/RANGE-T18-design/http");
    @BeforeAll static void database() throws Exception {
        MYSQL.start();
        sql = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        Files.createDirectories(EVIDENCE);
        Path cp = Path.of("target/range-acceptance/test-classpath.txt");
        Files.createDirectories(cp.getParent());
        Files.writeString(cp, System.getProperty("java.class.path"));
    }
    @BeforeEach void source() throws Exception { source = new ControlledRangeSource(script("success")); }
    @AfterEach void close() {
        try { if (schemaBefore != null) assertThat(schema()).isEqualTo(schemaBefore); }
        finally { try { if (app != null) app.close(); } finally { if (source != null) source.close(); } }
    }
    @AfterAll static void stopDatabase() { MYSQL.stop(); }
    void start(String mode, String recovery, boolean remote) {
        app = (ServletWebServerApplicationContext) new SpringApplicationBuilder(HttpTestApplication.class)
                .profiles("acceptance").run("--server.port=0", "--server.address=127.0.0.1",
                        "--spring.datasource.url=" + MYSQL.getJdbcUrl(), "--spring.datasource.username=" + MYSQL.getUsername(),
                        "--spring.datasource.password=" + MYSQL.getPassword(), "--tensor.plugins.tushare-pro.enabled=false",
                        "--tensor.plugins.fixture.enabled=true", "--tensor.plugins.fixture.mode=" + mode,
                        "--tensor.plugins.fixture.recovery=" + recovery,
                        "--tensor.plugins.fixture.source-url=" + (remote ? source.url() : ""));
        sql.update("DELETE FROM tensor_download_task_item");
        sql.update("DELETE FROM tensor_download_task");
        sql.update("DELETE FROM fixture__fixture_daily");
        schemaBefore = schema();
    }
    @Test void bareFixtureFirstHttpExecutesWithoutWrapper() throws Exception {
        start("ORIGINAL_PARAMS", "REQUEST", false);
        JsonNode result = download(Map.of("scenario", "SUCCESS"), 200);
        assertThat(result.path("insertedRows").asLong()).isEqualTo(1);
        assertThat(count("fixture__fixture_daily")).isEqualTo(1);
        assertNoTasks();
        evidence("bare-fixture");
    }
    @Test void pageTwoFailurePublishesNoPriorRowsAndSavesExactRequest() throws Exception {
        JsonNode rules = JSON.readTree("""
                {"batchRules":[
                {"apiName":"fixture_daily","params":{"scenario":"SUCCESS","ann_date":"20260901"},"page":1,"response":{"fields":["ts_code","trade_date","amount","note"],"data":[["000001.SZ","20260901","11.23",null]],"totalRows":2,"nextPage":2,"complete":false}},
                {"apiName":"fixture_daily","params":{"scenario":"SUCCESS","ann_date":"20260901"},"page":2,"errorCode":"SOURCE_RATE_LIMITED"}]}
                """);
        source.replaceScript(rules);
        start("ANN_DATE_RANGE", "STOCK_TIME", true);
        JsonNode result = download(range("2026-09-01", "2026-09-01"), 200);
        assertThat(result.path("failedUnits").asLong()).isEqualTo(1);
        assertThat(count("fixture__fixture_daily")).isZero();
        assertThat(sql.queryForMap("SELECT target_type,target_value,time_type,time_value,error_code FROM tensor_download_task_item"))
                .containsEntry("target_type", "REQUEST").containsEntry("target_value", "").containsEntry("time_type", "DATE")
                .containsEntry("time_value", "2026-09-01").containsEntry("error_code", "SOURCE_RATE_LIMITED");
        assertThat(source.calls().stream().map(c -> c.path("page").asInt())).containsExactly(1, 2);
        evidence("page-two-failure");
    }
    @Test void browserScriptsRetryOnlyThreeAndSevenThenSevenAndPreserveOriginalTask() throws Exception {
        source.replaceScript(script("initial"));
        start("ANN_DATE_RANGE", "REQUEST", true);
        JsonNode first = download(range("2026-09-01", "2026-09-10"), 200);
        assertThat(first.path("completedUnits").asLong()).isEqualTo(8);
        assertThat(first.path("failedUnits").asLong()).isEqualTo(2);
        String id = first.path("taskId").asText();
        Map<String,Object> original = sql.queryForMap("SELECT task_params,created_at FROM tensor_download_task");
        assertThat(source.calls().stream().map(c -> c.path("params").path("ann_date").asText())).containsExactly(
                "20260901", "20260902", "20260903", "20260904", "20260905", "20260906", "20260907", "20260908", "20260909", "20260910");
        assertThat(count("fixture__fixture_daily")).isEqualTo(8);
        assertThat(count("tensor_download_task_item")).isEqualTo(2);
        assertThat(get("/api/v1/retry-tasks/" + id).path("originalDateRange").path("endDate").asText()).isEqualTo("20260910");
        assertThat(get("/api/v1/retry-tasks").path("totalElements").asInt()).isEqualTo(1);
        evidence("sparse-initial");
        source.clearCalls(); source.replaceScript(script("partial"));
        JsonNode partial = request("POST", "/api/v1/retry-tasks/" + id + "/execute", "", 200);
        assertThat(partial.path("taskId").asText()).isEqualTo(id);
        assertThat(partial.path("remainingFailedUnits").asLong()).isEqualTo(1);
        assertThat(source.calls().stream().map(c -> c.path("params").path("ann_date").asText())).containsExactly("20260903", "20260907");
        assertThat(sql.queryForMap("SELECT task_params,created_at FROM tensor_download_task")).isEqualTo(original);
        assertThat(count("fixture__fixture_daily")).isEqualTo(9);
        evidence("sparse-partial");
        source.clearCalls(); source.replaceScript(script("success"));
        JsonNode last = request("POST", "/api/v1/retry-tasks/" + id + "/execute", "", 200);
        assertThat(last.path("remainingFailedUnits").asLong()).isZero();
        assertThat(source.calls().stream().map(c -> c.path("params").path("ann_date").asText())).containsExactly("20260907");
        assertThat(count("fixture__fixture_daily")).isEqualTo(10);
        assertNoTasks();
        evidence("sparse-success");
    }
    @Test void twoStockScriptsKeepSeparateKeysUpdateReasonAndDeleteIndividually() throws Exception {
        source.replaceScript(script("two-stocks")); start("ANN_DATE_RANGE", "STOCK_TIME", true);
        JsonNode first = download(range("2026-09-01", "2026-09-01"), 200);
        assertThat(first.path("failedUnits").asLong()).isEqualTo(2);
        assertThat(count("fixture__fixture_daily")).isZero();
        String id = first.path("taskId").asText();
        var original = sql.queryForMap("SELECT task_params,created_at FROM tensor_download_task");
        assertThat(sql.queryForList("SELECT target_value FROM tensor_download_task_item ORDER BY target_value", String.class)).containsExactly("000001.SZ", "000002.SZ");
        evidence("two-stocks-initial");
        source.clearCalls(); source.replaceScript(script("partial"));
        var partial = request("POST", "/api/v1/retry-tasks/"+id+"/execute", "", 200);
        assertThat(partial.path("completedUnits").asLong()).isEqualTo(1);
        assertThat(partial.path("failedUnits").asLong()).isEqualTo(1);
        assertThat(source.calls().stream().map(c -> c.path("params").path("ts_code").asText())).containsExactly("000001.SZ", "000002.SZ");
        assertThat(sql.queryForList("SELECT target_value FROM tensor_download_task_item", String.class)).containsExactly("000002.SZ");
        assertThat(sql.queryForMap("SELECT task_params,created_at FROM tensor_download_task")).isEqualTo(original);
        assertThat(count("fixture__fixture_daily")).isEqualTo(1);
        evidence("two-stocks-partial");
        source.clearCalls();
        request("POST", "/api/v1/retry-tasks/"+id+"/execute", "", 200);
        assertThat(count("tensor_download_task_item")).isEqualTo(1);
        assertThat(source.calls()).hasSize(1);
        evidence("two-stocks-repeat");
        source.clearCalls(); source.replaceScript(script("success"));
        request("POST", "/api/v1/retry-tasks/"+id+"/execute", "", 200);
        assertThat(source.calls().stream().map(c -> c.path("params").path("ts_code").asText())).containsExactly("000002.SZ");
        assertThat(count("fixture__fixture_daily")).isEqualTo(2); assertNoTasks(); evidence("two-stocks-success");
    }

    @Test void completePagesIsolateBadStockWithoutInventingMissingMembersAndEmptyRetryDeletesOnlyItem() throws Exception {
        source.replaceScript(rules("20260901", List.of(row("000001.SZ", "20260901", "1"), row("000002.SZ", "20260901", "bad"), row("000003.SZ", "20260901", "3"))));
        start("ANN_DATE_RANGE", "STOCK_TIME", true);
        var first = download(range("2026-09-01", "2026-09-01"), 200);
        assertThat(first.path("completedUnits").asLong()).isEqualTo(2);
        assertThat(first.path("failedUnits").asLong()).isEqualTo(1);
        assertThat(first.path("insertedRows").asLong()).isEqualTo(2);
        assertThat(sql.queryForList("SELECT ts_code FROM fixture__fixture_daily ORDER BY ts_code", String.class)).containsExactly("000001.SZ", "000003.SZ");
        evidence("complete-stock-isolation");
        String id=first.path("taskId").asText(); source.clearCalls();
        source.replaceScript(JSON.valueToTree(Map.of("batchRules", List.of(Map.of("apiName","fixture_daily","params",Map.of("scenario","SUCCESS","ann_date","20260901","ts_code","000002.SZ"),"page",1,"response",page(List.of()))))));
        var empty=request("POST","/api/v1/retry-tasks/"+id+"/execute","",200);
        assertThat(empty.path("completedUnits").asLong()).isEqualTo(1);
        assertThat(empty.path("sourceRowCount").asLong()).isZero(); assertNoTasks();
        assertThat(count("fixture__fixture_daily")).isEqualTo(2); evidence("empty-stock-retry");
        source.replaceScript(rules("20260902",List.of(row("000001.SZ","20260902","4"))));
        var absent=download(range("2026-09-02","2026-09-02"),200);
        assertThat(absent.path("completedUnits").asLong()).isEqualTo(1);
        assertThat(absent.path("failedUnits").asLong()).isZero(); assertNoTasks(); evidence("missing-stock-is-not-failure");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"SOURCE_AUTH_FAILED","SOURCE_PERMISSION_DENIED","SOURCE_RATE_LIMITED","SOURCE_UNAVAILABLE","SOURCE_NETWORK_ERROR","SOURCE_TIMEOUT","SOURCE_PAYLOAD_INVALID","SOURCE_TRUNCATED","SOURCE_COMPLETENESS_UNCONFIRMED"})
    void everySourceFailureContinuesLaterDatesWithoutAutomaticRetry(String code) throws Exception {
        var rules=(com.fasterxml.jackson.databind.node.ObjectNode)script("success");
        for (int day=0; day<2; day++)
            ((com.fasterxml.jackson.databind.node.ObjectNode)rules.path("batchRules").get(day)).set("response",JSON.valueToTree(Map.of("errorCode",code,"errorMessage","CANARY_NO_LEAK http://private.invalid?token=private")));
        source.replaceScript(rules); start("ANN_DATE_RANGE","REQUEST",true);
        var result=download(range("2026-09-01","2026-09-03"),200);
        assertThat(result.path("completedUnits").asLong()).isEqualTo(1); assertThat(result.path("failedUnits").asLong()).isEqualTo(2);
        assertThat(count("fixture__fixture_daily")).isEqualTo(1); assertThat(count("tensor_download_task_item")).isEqualTo(2);
        assertThat(sql.queryForList("SELECT error_code FROM tensor_download_task_item ORDER BY time_value",String.class)).containsExactly(code,code);
        assertThat(JSON.writeValueAsString(sql.queryForList("SELECT * FROM tensor_download_task_item"))).doesNotContain("CANARY", "private.invalid");
        assertThat(source.calls().stream().map(c->c.path("params").path("ann_date").asText())).containsExactly("20260901","20260902","20260903");
        var original=sql.queryForMap("SELECT task_id,task_params,created_at FROM tensor_download_task");
        String id=result.path("taskId").asText();
        evidence("source-code-"+code);
        source.clearCalls();
        ((com.fasterxml.jackson.databind.node.ArrayNode)rules.path("batchRules")).set(1,script("success").path("batchRules").get(1));
        source.replaceScript(rules);
        var retry=request("POST","/api/v1/retry-tasks/"+id+"/execute","",200);
        assertThat(retry.path("completedUnits").asLong()).isEqualTo(1);
        assertThat(retry.path("failedUnits").asLong()).isEqualTo(1);
        assertThat(retry.path("remainingFailedUnits").asLong()).isEqualTo(1);
        assertThat(retry.path("taskId").asText()).isEqualTo(id);
        assertThat(source.calls().stream().map(c->c.path("params").path("ann_date").asText())).containsExactly("20260901","20260902");
        assertThat(sql.queryForMap("SELECT task_id,task_params,created_at FROM tensor_download_task")).isEqualTo(original);
        assertThat(sql.queryForMap("SELECT time_value,error_code FROM tensor_download_task_item")).containsEntry("time_value","2026-09-01").containsEntry("error_code",code);
        assertThat(count("fixture__fixture_daily")).isEqualTo(2);
        evidence("source-code-retry-"+code);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"TRADE_DATE_RANGE,2026-09-01,2026-10-01,31", "ANN_DATE_RANGE,2028-02-28,2028-03-01,3", "ANN_DATE_RANGE,2026-12-31,2027-01-01,2", "MONTH_RANGE,2026-08-15,2026-09-03,2", "NATIVE_RANGE,2026-09-01,2026-09-03,3"})
    void localModesHonorRangeBoundariesAndIndependentInsertUpdateCounts(String mode,String from,String to,long rows) throws Exception {
        start(mode,"REQUEST",false);
        var first=download(range(from,to),200);
        assertThat(first.path("insertedRows").asLong()).isEqualTo(rows); assertThat(first.path("updatedRows").asLong()).isZero();
        assertThat(count("fixture__fixture_daily")).isEqualTo(rows); assertNoTasks(); evidence("boundary-"+mode+"-"+from);
        var second=download(range(from,to),200);
        assertThat(second.path("insertedRows").asLong()).isZero(); assertThat(second.path("updatedRows").asLong()).isEqualTo(rows);
        assertThat(count("fixture__fixture_daily")).isEqualTo(rows); assertNoTasks(); evidence("updates-"+mode+"-"+from);
    }

    @Test void preflightRejectsThirtyTwoDaysAndBadParamsWithoutSourceOrTasks() throws Exception {
        start("ANN_DATE_RANGE","REQUEST",true); assertNoTasks(); evidence("startup");
        download(range("2026-09-01","2026-10-02"),400);
        download(range("2026-09-02","2026-09-01"),400);
        download(Map.of("scenario","SUCCESS","start_date","2026-09-01"),400);
        assertThat(source.calls()).isEmpty(); assertThat(count("fixture__fixture_daily")).isZero(); assertNoTasks(); evidence("preflight-rejections");
    }

    @Test void calendarMissingDayFailsBeforeBusinessAndAllClosedPreservesOldRows() throws Exception {
        start("TRADE_DATE_RANGE","REQUEST",true);
        var publicParams=range("2026-09-01","2026-09-02");
        var rule=new java.util.LinkedHashMap<String,Object>(); rule.put("apiName","fixture_daily"); rule.put("params",publicParams); rule.put("dates",List.of("2026-09-01","2026-09-02"));
        rule.put("response",Map.of("calendars",Map.of("fixture",Map.of("2026-09-01",true))));
        source.replaceScript(JSON.valueToTree(Map.of("calendarRules",List.of(rule))));
        var missing=download(publicParams,502); assertThat(missing.path("code").asText()).isEqualTo("CALENDAR_UNCONFIRMED");
        assertThat(source.calls()).hasSize(1); assertNoTasks(); evidence("calendar-missing");
        sql.update("INSERT INTO fixture__fixture_daily(ts_code,trade_date,amount,source_plugin,source_api,ingested_at) VALUES ('000001.SZ','2026-09-01',9,'fixture','fixture_daily',CURRENT_TIMESTAMP(3))");
        source.clearCalls(); rule.put("response",Map.of("calendars",Map.of("fixture",Map.of("2026-09-01",false,"2026-09-02",false))));
        source.replaceScript(JSON.valueToTree(Map.of("calendarRules",List.of(rule))));
        var closed=download(publicParams,200); assertThat(closed.path("skippedClosedDates").asLong()).isEqualTo(2);
        assertThat(closed.path("completedUnits").asLong()).isZero(); assertThat(count("fixture__fixture_daily")).isEqualTo(1); assertNoTasks();
        assertThat(source.calls()).hasSize(1); evidence("calendar-all-closed");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"truncated", "wrong-total", "missing-complete", "unknown-stock", "wrong-date", "explicit-member"})
    void incompleteOrUnattributedPayloadStaysRequestAndCompleteMemberFailureIsStock(String variant) throws Exception {
        var response=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.valueToTree(page(List.of(row("000001.SZ","20260901","1"))));
        if (variant.equals("truncated")) { response.put("nextPage",2); response.put("complete",false); response.put("totalRows",2); }
        if (variant.equals("wrong-total")) response.put("totalRows",2);
        if (variant.equals("missing-complete")) response.put("complete",false);
        if (variant.equals("unknown-stock")) ((com.fasterxml.jackson.databind.node.ArrayNode)response.path("data").get(0)).set(0,JSON.nullNode());
        if (variant.equals("wrong-date")) ((com.fasterxml.jackson.databind.node.ArrayNode)response.path("data").get(0)).set(1,JSON.valueToTree("20260902"));
        if (variant.equals("explicit-member")) {
            response.set("data",JSON.createArrayNode()); response.put("totalRows",0);
            response.set("unitFailures",JSON.valueToTree(List.of(Map.of("selector",Map.of("targetType","STOCK","targetValue","000002.SZ","timeType","DATE","timeValue","2026-09-01"),"errorCode","SOURCE_PAYLOAD_INVALID","errorMessage","CANARY_NO_LEAK"))));
        }
        source.replaceScript(JSON.valueToTree(Map.of("batchRules",List.of(Map.of("apiName","fixture_daily","params",Map.of("scenario","SUCCESS","ann_date","20260901"),"page",1,"response",response)))));
        start("ANN_DATE_RANGE","STOCK_TIME",true); var result=download(range("2026-09-01","2026-09-01"),200);
        assertThat(result.path("failedUnits").asLong()).isEqualTo(1); assertThat(count("fixture__fixture_daily")).isZero();
        assertThat(sql.queryForObject("SELECT target_type FROM tensor_download_task_item",String.class)).isEqualTo(variant.equals("explicit-member")?"STOCK":"REQUEST");
        evidence("payload-"+variant);
    }

    static List<Object> row(String stock,String date,String amount) { return java.util.Arrays.asList(stock,date,amount,null); }
    static Map<String,Object> page(List<List<Object>> rows) {
        var page=new java.util.LinkedHashMap<String,Object>(); page.put("fields",List.of("ts_code","trade_date","amount","note")); page.put("data",rows); page.put("totalRows",rows.size()); page.put("nextPage",null); page.put("complete",true); return page;
    }
    static JsonNode rules(String date,List<List<Object>> rows) {
        return JSON.valueToTree(Map.of("batchRules",List.of(Map.of("apiName","fixture_daily","params",Map.of("scenario","SUCCESS","ann_date",date),"page",1,"response",page(rows)))));
    }

    static JsonNode script(String stage) throws Exception {
        return JSON.readTree(RangeFixtureHttpIT.class.getResourceAsStream("/fixture/range-browser-" + stage + ".json"));
    }
    static Map<String,Object> range(String from, String to) { return Map.of("scenario", "SUCCESS", "start_date", from.replace("-", ""), "end_date", to.replace("-", "")); }
    JsonNode download(Map<String,Object> params, int status) throws Exception {
        return request("POST", "/api/v1/downloads", JSON.writeValueAsString(Map.of("pluginId", "fixture", "apiName", "fixture_daily", "params", params)), status);
    }
    JsonNode get(String path) throws Exception { return request("GET", path, "", 200); }
    JsonNode request(String method, String path, String body, int status) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getWebServer().getPort() + path))
                .timeout(Duration.ofSeconds(60)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = JSON.readTree(response.body()); responses.add(json);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json;
    }
    static long count(String table) { return sql.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    static void assertNoTasks() { assertThat(count("tensor_download_task")).isZero(); assertThat(count("tensor_download_task_item")).isZero(); }
    static Map<String,Object> schema() {
        var snapshot = new java.util.TreeMap<String,Object>();
        for (String table : List.of("fixture__fixture_daily", "tensor_download_task", "tensor_download_task_item")) {
            var indexes = sql.queryForList("SHOW INDEX FROM " + table);
            indexes.forEach(row -> row.remove("Cardinality"));
            snapshot.put(table, Map.of("create", sql.queryForList("SHOW CREATE TABLE " + table), "indexes", indexes));
        }
        return snapshot;
    }
    void evidence(String name) throws Exception {
        var schemaAfter = schema();
        var snapshot = Map.of("sourceCalls", source.calls(), "http", responses,
                "business", sql.queryForList("SELECT * FROM fixture__fixture_daily ORDER BY ts_code,trade_date"),
                "tasks", sql.queryForList("SELECT * FROM tensor_download_task ORDER BY task_id"),
                "items", sql.queryForList("SELECT * FROM tensor_download_task_item ORDER BY task_id,target_type,target_value,time_type,time_value"),
                "schemaBefore", schemaBefore, "schemaAfter", schemaAfter);
        JSON.writerWithDefaultPrettyPrinter().writeValue(EVIDENCE.resolve(name + ".json").toFile(), snapshot);
        assertThat(schemaAfter).isEqualTo(schemaBefore);
    }
}
