package com.akkc.tensor.plugin.tushare.batch;

import static com.akkc.tensor.plugin.tushare.batch.TushareBatchPoliciesTest.*;
import static com.akkc.tensor.plugin.tushare.batch.TushareTradeCalendarTest.day;
import static org.assertj.core.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.tushare.TushareProPlugin;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.ResourceAccessException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.net.URI;
import org.springframework.web.client.RestClient;

class TushareBatchDownloadTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @RegisterExtension final WireMockExtension server=WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();
    private TushareProClient client;
    private TushareProPlugin plugin;
    private int expectedRequests;

    @BeforeEach void setup() {
        var properties=new TushareProperties(true,URI.create(server.baseUrl()),PROPERTIES.token(),PROPERTIES.connectTimeout(),PROPERTIES.readTimeout(),PROPERTIES.maxResponseBytes(),PROPERTIES.minRequestInterval());
        client=new TushareProClient(RestClient.builder().baseUrl(server.baseUrl()).build(),properties);
        plugin=new TushareProPlugin(properties,client,DEFINITIONS);
    }

    @ParameterizedTest @ValueSource(strings={"000001.SZ","600000.SH"})
    void tradingPlanAndEachDailyDownloadShareOneContextAndReserveExactlyOnce(String stock) throws Exception {
        String exchange=stock.endsWith("SH")?"SSE":"SZSE";
        var input=params("top_list");input.put("ts_code",stock);
        var days=List.of(Arrays.<Object>asList(exchange,"20240301",0,"20240229"),Arrays.<Object>asList(exchange,"20240228",1,"20240227"),Arrays.<Object>asList(exchange,"20240229",1,"20240228"));
        expect("trade_cal",Map.of("exchange",exchange,"start_date","20240228","end_date","20240301"),response("trade_cal",days));
        for(String date:List.of("20240228","20240229")) expect("top_list",Map.of("ts_code",stock,"trade_date",date),response("top_list",List.of(with("top_list",row("top_list",date),"ts_code",stock))));
        var context=new Context();var policies=verified(client,"top_list","trade_cal");
        var plan=policies.plan(api("top_list"),input,context);
        assertThat(plan).containsExactly(range("2024-02-28","2024-02-28"),range("2024-02-29","2024-02-29"));
        assertThat(context.reservations.get()).isEqualTo(1);
        int securitiesRows=0;
        for(var date:plan) {
            var envelope=plugin.downloadBatch(api("top_list"),policies.sourceParameters(api("top_list"),input,date),context);
            securitiesRows+=envelope.rowCount();
            assertThat(envelope.apiName()).isEqualTo(api("top_list"));
            assertThat(policies.assess(api("top_list"),date,envelope)).isEqualTo(BatchAssessment.COMPLETE);
        }
        assertThat(securitiesRows).isEqualTo(2);assertThat(context.reservations.get()).isEqualTo(3);verifyRequests();
        assertThat(plugin.batchDescriptor(api("top_list")).orElseThrow().availability()).isEqualTo(BatchDownloadDescriptor.Availability.NEEDS_VERIFICATION);
    }

    @Test void nativeAndSixSingleOnlyDownloadsUseTheSameRealClientContext() throws Exception {
        var context=new Context();
        expect("daily",params("daily"),response("daily",List.of(row("daily","20240229"))));
        var policies=verified(client,"daily");
        var plan=policies.plan(api("daily"),params("daily"),context);
        assertThat(plan).containsExactly(RANGE);assertThat(context.reservations.get()).isZero();
        assertThat(policies.assess(api("daily"),RANGE,plugin.downloadBatch(api("daily"),policies.sourceParameters(api("daily"),params("daily"),RANGE),context))).isEqualTo(BatchAssessment.COMPLETE);
        for(String name:SINGLE_ONLY) {
            Map<String,Object> input=switch(name) {
                case "pledge_stat" -> Map.of("ts_code","000001.SZ","end_date","20240229");
                case "stk_rewards" -> Map.of("ts_code","000001.SZ","end_date","20231231");
                case "index_classify" -> Map.of("src","SW2021");
                default -> Map.of("ts_code","000001.SZ");
            };
            expect(name,input,response(name,List.of()));
            assertThat(plugin.downloadBatch(api(name),input,context).params()).isEqualTo(input);
        }
        assertThat(context.reservations.get()).isEqualTo(7);verifyRequests();
    }

    @Test void wholeClosedCalendarMakesEmptyPlanButEmptyResponseFailsAndNoResultsAreCached() throws Exception {
        expect("trade_cal",params("trade_cal"),response("trade_cal",List.of(day("20240228",0),day("20240229",0),day("20240301",0))));
        expect("trade_cal",params("trade_cal"),response("trade_cal",List.of()));
        var context=new Context();var policies=verified(client,"top_list","trade_cal");
        assertThat(policies.plan(api("top_list"),params("top_list"),context)).isEmpty();
        code(() -> policies.plan(api("top_list"),params("top_list"),context),ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
        assertThat(context.reservations.get()).isEqualTo(2);verifyRequests();
    }

    @Test void unavailableCalendarOrUnsupportedExchangeNeverMakesHttpOrReservations() {
        var context=new Context();
        code(() -> verified(client,"top_list").plan(api("top_list"),params("top_list"),context),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        for(String stock:List.of("000001.BJ","000001.XX")) {var input=params("top_list");input.put("ts_code",stock);code(() -> verified(client,"top_list","trade_cal").plan(api("top_list"),input,context),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);}
        assertThat(context.reservations.get()).isZero();verifyRequests();
    }

    @Test void reservationDenialPropagatesIdenticalClassifiedFailureAndMakesNoHttp() {
        var denied=new TensorException(ErrorCode.TASK_LIMIT_EXCEEDED,"Download task limit exceeded") {};
        var context=new Context(){public void beforeRequest(){super.beforeRequest();throw denied;}};
        assertThat(catchThrowable(() -> verified(client,"top_list","trade_cal").plan(api("top_list"),params("top_list"),context))).isSameAs(denied);
        assertThat(context.reservations.get()).isEqualTo(1);verifyRequests();
    }

    @ParameterizedTest @CsvSource({"401,SOURCE_AUTH_FAILED","403,SOURCE_PERMISSION_DENIED","429,SOURCE_RATE_LIMITED","503,SOURCE_UNAVAILABLE"})
    void classifiedHttpFailureStopsPlanningWithoutRetry(int status,ErrorCode expected) {
        expect("trade_cal",params("trade_cal"),aResponse().withStatus(status).withBody("secret response"));
        var context=new Context();
        code(() -> verified(client,"top_list","trade_cal").plan(api("top_list"),params("top_list"),context),expected);
        assertThat(context.reservations.get()).isEqualTo(1);verifyRequests();
    }

    @Test void networkFailureRetainsT05Classification() {
        expect("trade_cal",params("trade_cal"),aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
        var context=new Context();code(() -> verified(client,"top_list","trade_cal").plan(api("top_list"),params("top_list"),context),ErrorCode.SOURCE_NETWORK_ERROR);
        assertThat(context.reservations.get()).isEqualTo(1);verifyRequests();
    }

    @Test void oversizedCalendarResponseCannotBecomeAnEmptyPlan() throws Exception {
        expect("trade_cal",params("trade_cal"),response("trade_cal",List.of(day("20240228",1),day("20240229",1),day("20240301",0))));
        var properties=new TushareProperties(true,URI.create(server.baseUrl()),PROPERTIES.token(),PROPERTIES.connectTimeout(),PROPERTIES.readTimeout(),32,PROPERTIES.minRequestInterval());
        var limited=new TushareProClient(RestClient.builder().baseUrl(server.baseUrl()).build(),properties);
        var context=new Context();
        code(() -> verified(limited,"top_list","trade_cal").plan(api("top_list"),params("top_list"),context),ErrorCode.SOURCE_PAYLOAD_INVALID);
        assertThat(context.reservations.get()).isEqualTo(1);verifyRequests();
    }

    @Test void calendarTransportTimeoutRetainsT05ClassificationWithoutRetry() {
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        var rest=RestClient.builder().baseUrl(server.baseUrl()).requestFactory((uri,method) -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("secret timeout",new SocketTimeoutException("secret transport"));
        }).build();
        var timedOut=new TushareProClient(rest,PROPERTIES);var context=new Context();
        code(() -> verified(timedOut,"top_list","trade_cal").plan(api("top_list"),params("top_list"),context),ErrorCode.SOURCE_TIMEOUT);
        assertThat(attempts.get()).isEqualTo(1);assertThat(context.reservations.get()).isEqualTo(1);verifyRequests();
    }

    @Test void stopAfterCalendarHttpCompletesCannotReturnASuccessfulPlan() throws Exception {
        expect("trade_cal",params("trade_cal"),response("trade_cal",List.of(day("20240228",1),day("20240229",1),day("20240301",0))));
        var context=new Context();
        var rest=RestClient.builder().baseUrl(server.baseUrl()).requestInterceptor((request,body,execution) -> {
            var response=execution.execute(request,body);context.stopped=true;return response;
        }).build();
        var stopped=new TushareProClient(rest,PROPERTIES);
        code(() -> verified(stopped,"top_list","trade_cal").plan(api("top_list"),params("top_list"),context),ErrorCode.EXECUTION_INTERRUPTED);
        assertThat(context.reservations.get()).isEqualTo(1);verifyRequests();
    }

    @Test void stopOrExpiryBeforePlanningAndAfterReservationPreventsFurtherRequests() {
        var context=new Context();var policies=verified(client,"top_list","trade_cal");context.stopped=true;
        code(() -> policies.plan(api("top_list"),params("top_list"),context),ErrorCode.EXECUTION_INTERRUPTED);
        context.stopped=false;context.deadline=CLOCK.instant();code(() -> policies.plan(api("top_list"),params("top_list"),context),ErrorCode.TASK_LIMIT_EXCEEDED);
        assertThat(context.reservations.get()).isZero();
        var stop=new Context(){public void beforeRequest(){super.beforeRequest();stopped=true;}};
        code(() -> policies.plan(api("top_list"),params("top_list"),stop),ErrorCode.EXECUTION_INTERRUPTED);
        var expire=new Context(){public void beforeRequest(){super.beforeRequest();deadline=Instant.EPOCH;}};
        code(() -> policies.plan(api("top_list"),params("top_list"),expire),ErrorCode.TASK_LIMIT_EXCEEDED);
        assertThat(stop.reservations.get()).isEqualTo(1);assertThat(expire.reservations.get()).isEqualTo(1);verifyRequests();
    }

    @Test void realValidatorNormalizesValidHolderTimestampAndRetainsInvalidDateRejection() throws Exception {
        var source=params("stk_holdernumber");
        expect("stk_holdernumber",source,response("stk_holdernumber",List.of(row("stk_holdernumber","2024-02-29 12:30:59"))));
        expect("stk_holdernumber",source,response("stk_holdernumber",List.of(row("stk_holdernumber","2024-02-30 12:30:59"))));
        var context=new Context();
        var valid=plugin.downloadBatch(api("stk_holdernumber"),source,context);
        assertThat(valid.data().getFirst().get(valid.fields().indexOf("ann_date"))).isEqualTo("20240229");
        assertThat(production().assess(api("stk_holdernumber"),RANGE,valid)).isEqualTo(BatchAssessment.UNKNOWN);
        var invalid=plugin.downloadBatch(api("stk_holdernumber"),source,context);
        code(() -> production().assess(api("stk_holdernumber"),RANGE,invalid),ErrorCode.SOURCE_PAYLOAD_INVALID);
        verifyRequests();
    }

    @Test void actualClientRetainsPayloadClassificationForMixedStocks() throws Exception {
        expect("daily",params("daily"),response("daily",List.of(with("daily",row("daily","20240229"),"ts_code","600000.SH"))));
        code(() -> plugin.downloadBatch(api("daily"),params("daily"),new Context()),ErrorCode.SOURCE_PAYLOAD_INVALID);verifyRequests();
    }

    private void expect(String name,Map<String,Object> params,ResponseDefinitionBuilder response) {
        try {
            var body=Map.of("api_name",name,"params",params,"token",PROPERTIES.token().value(),"fields",String.join(",",definition(name).columns().stream().map(c -> c.name()).toList()));
            server.stubFor(post(urlEqualTo("/")).withRequestBody(equalToJson(JSON.writeValueAsString(body)))
                    .inScenario("requests").whenScenarioStateIs(expectedRequests==0?Scenario.STARTED:"step"+expectedRequests)
                    .willSetStateTo("step"+(++expectedRequests)).willReturn(response));
        } catch(Exception failure) { throw new AssertionError(failure); }
    }
    private void verifyRequests() { assertThat(server.getAllServeEvents()).hasSize(expectedRequests).allSatisfy(event -> assertThat(event.getWasMatched()).isTrue()); }
    private ResponseDefinitionBuilder response(String name,List<List<Object>> data) throws Exception {
        return aResponse().withHeader("Content-Type","application/json").withBody(JSON.writeValueAsString(Map.of("code",0,"data",Map.of("fields",definition(name).columns().stream().map(c -> c.name()).toList(),"items",data))));
    }
}
