package com.akkc.tensor.plugin.fixture;

import static org.assertj.core.api.Assertions.*;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.error.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class FixtureBatchSourceTest {
    static final ObjectMapper JSON = new ObjectMapper();
    HttpServer server;
    FixtureBatchSource client;
    FetchBatch batch;
    final List<String> requests = new ArrayList<>();
    final List<String> pages = new ArrayList<>();
    int status = 200;
    @BeforeEach void setup() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("acceptance");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture", Map.of("tensor.plugins.fixture.enabled", "true")));
            context.register(FixtureConfiguration.class); context.refresh();
            DatasetDefinition definition = context.getBean(DatasetAdapter.class).definition();
            batch = new FetchBatch(Map.of("scenario", "SUCCESS"), context.getBean(FixturePlugin.class).descriptor().apis().getFirst().downloadPolicy().recoveryPolicy());
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = pages.get(Math.min(requests.size() - 1, pages.size() - 1)).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Secret-Canary", "CANARY_NO_LEAK");
                exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
            }); server.start();
            client = new FixtureBatchSource(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), definition);
        }
    }
    @AfterEach void close() { if (server != null) server.stop(0); }
    static String page(String rows, int total, String next, boolean complete) {
        return "{\"fields\":[\"ts_code\",\"trade_date\",\"amount\",\"note\"],\"data\":" + rows + ",\"totalRows\":" + total + ",\"nextPage\":" + next + ",\"complete\":" + complete + "}";
    }
    @Test void collectsAllPagesAndPreservesEveryExactParameter() throws Exception {
        pages.add(page("[[\"000001.SZ\",\"20260901\",\"1\",null]]",2,"2",false));
        pages.add(page("[[\"000002.SZ\",\"20260901\",\"2\",null]]",2,"null",true));
        AtomicInteger checks = new AtomicInteger();
        var result = client.fetch(batch, checks::incrementAndGet);
        assertThat(result.envelope().rowCount()).isEqualTo(2);
        assertThat(result.envelope().pluginId().value()).isEqualTo("fixture");
        assertThat(checks).hasValue(4);
        assertThat(requests).hasSize(2);
        for (int i=0;i<2;i++) {
            var request = JSON.readTree(requests.get(i));
            assertThat(request.path("apiName").asText()).isEqualTo("fixture_daily");
            assertThat(request.path("params")).isEqualTo(JSON.valueToTree(batch.sourceParams()));
            assertThat(request.path("page").asInt()).isEqualTo(i+1);
        }
    }
    @ParameterizedTest @CsvSource({"401,SOURCE_AUTH_FAILED", "403,SOURCE_PERMISSION_DENIED", "429,SOURCE_RATE_LIMITED", "500,SOURCE_UNAVAILABLE", "302,SOURCE_UNAVAILABLE", "400,SOURCE_UNAVAILABLE"})
    void mapsHttpStatusWithoutEchoingPayload(int status, String code) {
        this.status=status; pages.add("CANARY_NO_LEAK http://secret.invalid?token=private");
        assertFailure(code); assertThat(requests).hasSize(1);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"SOURCE_AUTH_FAILED", "SOURCE_PERMISSION_DENIED", "SOURCE_RATE_LIMITED", "SOURCE_UNAVAILABLE", "SOURCE_NETWORK_ERROR", "SOURCE_TIMEOUT", "SOURCE_PAYLOAD_INVALID", "SOURCE_TRUNCATED", "SOURCE_COMPLETENESS_UNCONFIRMED"})
    void acceptsOnlySafeSourceCodes(String code) {
        pages.add("{\"errorCode\":\""+code+"\",\"errorMessage\":\"CANARY_NO_LEAK\"}"); assertFailure(code);
    }
    @Test void rejectsUnknownErrorCode() { pages.add("{\"errorCode\":\"ADAPTER_TYPE_INVALID\"}"); assertFailure("SOURCE_PAYLOAD_INVALID"); }
    @Test void rejectsSecondPageAndNeverPublishesPriorData() {
        pages.add(page("[[\"000001.SZ\",\"20260901\",\"1\",null]]",2,"2",false));
        pages.add("{\"errorCode\":\"SOURCE_RATE_LIMITED\"}"); assertFailure("SOURCE_RATE_LIMITED"); assertThat(requests).hasSize(2);
    }
    @ParameterizedTest @CsvSource({"2,null,true,SOURCE_TRUNCATED", "0,null,false,SOURCE_COMPLETENESS_UNCONFIRMED", "0,3,false,SOURCE_TRUNCATED", "0,2,true,SOURCE_COMPLETENESS_UNCONFIRMED"})
    void rejectsUnprovedPagination(int total,String next,boolean complete,String code) {
        pages.add(page("[]",total,next,complete)); assertFailure(code);
    }
    @Test void rejectsMalformedFieldsAndPartialJson() {
        pages.add("{\"fields\":[\"CANARY_NO_LEAK\"],\"data\":[],\"totalRows\":0,\"nextPage\":null,\"complete\":true}");
        assertFailure("SOURCE_PAYLOAD_INVALID"); pages.set(0,"{\"data\":"); assertFailure("SOURCE_PAYLOAD_INVALID");
    }
    @Test void acceptsExplicitCompleteEmpty() { pages.add(page("[]",0,"null",true)); assertThat(client.fetch(batch,()->{}).envelope().rowCount()).isZero(); }
    @Test void calendarCoversExactlyRequestedDatesWithBooleanValues() {
        pages.add("{\"calendars\":{\"fixture\":{\"2026-09-01\":true,\"2026-09-02\":false}}}");
        var scope = new CalendarScope(Map.of("scenario","SUCCESS"),Set.of(LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-02")));
        assertThat(client.confirmCalendar(scope,()->{}).openDates()).containsExactly(LocalDate.parse("2026-09-01"));
        pages.set(0,"{\"calendars\":{\"fixture\":{\"2026-09-01\":true}}}");
        assertThatThrownBy(()->client.confirmCalendar(scope,()->{})).isInstanceOf(CalendarUnconfirmedException.class);
        pages.set(0,"{\"calendars\":{\"fixture\":{\"2026-09-01\":\"true\",\"2026-09-02\":false}}}");
        assertThatThrownBy(()->client.confirmCalendar(scope,()->{})).isInstanceOf(CalendarUnconfirmedException.class);
    }
    @Test void preservesJsonDecimalPrecisionAcrossPages() {
        pages.add(page("[[\"000001.SZ\",\"20260901\",12345678901234567890.123456789012345678,null]]",1,"null",true));
        assertThat(client.fetch(batch,()->{}).envelope().data().getFirst().get(2))
                .isEqualTo(new java.math.BigDecimal("12345678901234567890.123456789012345678"));
    }
    @Test void permitsEmptyIntermediatePageWhenFollowingTerminalProvesComplete() {
        pages.add(page("[]",1,"2",false));
        pages.add(page("[[\"000001.SZ\",\"20260901\",\"1\",null]]",1,"null",true));
        assertThat(client.fetch(batch,()->{}).envelope().rowCount()).isEqualTo(1);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"http://localhost:4189", "https://127.0.0.1:4189", "http://127.0.0.1", "http://127.0.0.1:4189/", "http://127.0.0.1:4189/path", "http://user@127.0.0.1:4189", "http://127.0.0.1:4189?token=CANARY", "http://127.0.0.1:4189#fragment", "http://127.0.0.1:65536", "http://[::1]:4189"})
    void rejectsNonExactLoopbackOrigins(String url) {
        assertThatThrownBy(()->new FixtureBatchSource(URI.create(url), null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Fixture source must be a loopback HTTP origin");
    }
    @Test void rejectsChangedTotalAndChangedFieldsAcrossPages() {
        pages.add(page("[[\"000001.SZ\",\"20260901\",\"1\",null]]",2,"2",false));
        pages.add(page("[]",1,"null",true)); assertFailure("SOURCE_TRUNCATED");
        requests.clear(); pages.set(1,page("[]",2,"null",true).replace("\"note\"","\"other\"")); assertFailure("SOURCE_PAYLOAD_INVALID");
    }
    @Test void checksStateAfterFailedResponseAndBeforeNetwork() {
        pages.add("{\"errorCode\":\"SOURCE_UNAVAILABLE\"}");
        AtomicInteger checks=new AtomicInteger();
        assertThatThrownBy(()->client.fetch(batch,checks::incrementAndGet)).isInstanceOf(SourceException.class);
        assertThat(checks).hasValue(2);
        var stopped=new IllegalStateException("stopped");
        assertThatThrownBy(()->client.fetch(batch,()->{throw stopped;})).isSameAs(stopped);
        assertThat(requests).hasSize(1);
    }
    @Test void mapsConnectionFailureWithoutLeakingCause() {
        server.stop(0); pages.add(page("[]",0,"null",true)); assertFailure("SOURCE_NETWORK_ERROR");
    }
    @Test void explicitMembersRequireTerminalProofAndExactScope() throws Exception {
        var refs=batch.recoveryPolicy().evidenceRefs();
        var stock=new RecoveryPolicy(RecoveryPolicy.Mode.STOCK_TIME,"ts_code","trade_date",RecoverySelector.TimeType.DATE,true,refs);
        batch=new FetchBatch(Map.of("scenario","SUCCESS","ann_date","20260901","ts_code","000002.SZ"),stock);
        var terminal=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(page("[]",0,"null",true));
        terminal.set("unitFailures",JSON.valueToTree(List.of(Map.of("selector",Map.of("targetType","STOCK","targetValue","000002.SZ","timeType","DATE","timeValue","2026-09-01"),"errorCode","SOURCE_PAYLOAD_INVALID","errorMessage","CANARY_NO_LEAK"))));
        pages.add(JSON.writeValueAsString(terminal));
        var result=client.fetch(batch,()->{}); assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().getFirst().errorMessage()).doesNotContain("CANARY");
        var selector=(com.fasterxml.jackson.databind.node.ObjectNode)terminal.path("unitFailures").get(0).path("selector");
        selector.put("targetValue","000003.SZ"); pages.set(0,JSON.writeValueAsString(terminal)); assertFailure("SOURCE_PAYLOAD_INVALID");
        selector.put("targetValue","000002.SZ"); selector.put("timeValue","2026-09-02"); pages.set(0,JSON.writeValueAsString(terminal)); assertFailure("SOURCE_PAYLOAD_INVALID");
        selector.put("timeValue","2026-09-01"); terminal.put("nextPage",2); terminal.put("complete",false); pages.set(0,JSON.writeValueAsString(terminal)); assertFailure("SOURCE_PAYLOAD_INVALID");
    }
    void assertFailure(String code) {
        assertThatThrownBy(()->client.fetch(batch,()->{})).isInstanceOfSatisfying(SourceException.class, e -> {
            assertThat(e.code().name()).isEqualTo(code); assertThat(e.getMessage()).doesNotContain("CANARY", "http", "private"); assertThat(e.getCause()).isNull();
        });
    }
}
