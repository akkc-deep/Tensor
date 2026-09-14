package com.akkc.tensor.plugin.tushare.batch;

import static org.assertj.core.api.Assertions.*;
import static com.akkc.tensor.plugin.tushare.batch.TushareBatchPoliciesTest.*;

import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.download.batch.BatchCallContext;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TushareRangeSourceProbeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    @Test void authorizedConfigurationRequiresOptInTokenAndSafeInterval() {
        var env = new HashMap<>(Map.of("TENSOR_TUSHARE_LIVE_E2E", "1", "TENSOR_TUSHARE_TOKEN", "synthetic",
                "M14_T05_CALL_INTERVAL_MS", "2000"));
        assertThat(TushareRangeSourceProbe.properties(env).minRequestInterval()).isEqualTo(Duration.ofSeconds(2));
        for (String key : List.copyOf(env.keySet())) {
            var missing = new HashMap<>(env); missing.remove(key);
            safeFailure(() -> TushareRangeSourceProbe.properties(missing));
        }
        for (String interval : List.of("0", "1999", "3600001", "bad", " 2000")) {
            env.put("M14_T05_CALL_INTERVAL_MS", interval);
            safeFailure(() -> TushareRangeSourceProbe.properties(env));
        }
    }

    @Test void acceptsPrivatePlanAndCreatesExclusivePrivateOutput() throws Exception {
        Path directory = privateDirectory(); Path input = write(directory.resolve("cases.json"), plan());
        assertThat(TushareRangeSourceProbe.loadPlan(input)).isEqualTo(plan());
        Path output = TushareRangeSourceProbe.prepareOutput(directory);
        assertThat(Files.getPosixFilePermissions(output)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        Files.writeString(output, "preserved");
        safeFailure(() -> TushareRangeSourceProbe.prepareOutput(directory));
        assertThat(Files.readString(output)).isEqualTo("preserved");
    }

    @Test void rejectsPublicPermissionsSymlinksAndRelativePaths() throws Exception {
        Path directory = privateDirectory(); Path input = write(directory.resolve("cases.json"), plan());
        Files.setPosixFilePermissions(input, PosixFilePermissions.fromString("rw-r--r--"));
        safeFailure(() -> TushareRangeSourceProbe.loadPlan(input));
        Files.setPosixFilePermissions(input, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
        safeFailure(() -> TushareRangeSourceProbe.loadPlan(input));
        safeFailure(() -> TushareRangeSourceProbe.prepareOutput(directory));
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        Path link = Files.createSymbolicLink(directory.resolve("link.json"), input);
        safeFailure(() -> TushareRangeSourceProbe.loadPlan(link));
        Path dirLink = Files.createSymbolicLink(temporary.resolve("link-dir"), directory);
        safeFailure(() -> TushareRangeSourceProbe.loadPlan(dirLink.resolve("cases.json")));
        safeFailure(() -> TushareRangeSourceProbe.prepareOutput(dirLink));
        safeFailure(() -> TushareRangeSourceProbe.loadPlan(Path.of("cases.json")));
        safeFailure(() -> TushareRangeSourceProbe.prepareOutput(directory.resolve("missing")));
        safeFailure(() -> TushareRangeSourceProbe.privatePath(input, false, () -> "other-owner"));
    }

    @Test void rejectsDuplicateJsonTrailingInputAndUnsafeStringsWithoutEcho() throws Exception {
        Path input = privateDirectory().resolve("cases.json"); write(input, plan());
        for (String bad : List.of("{\"runId\":\"a\",\"runId\":\"b\",\"cases\":[]}", plan() + " {}",
                plan().toString().replace("official-reference", "token=synthetic-do-not-echo"))) {
            Files.writeString(input, bad);
            safeFailure(() -> TushareRangeSourceProbe.loadPlan(input));
        }
    }

    @Test void validatesAllCasesBeforeAnyRequest() {
        ObjectNode input = plan(); input.withArray("cases").add(rangeCase("bad", "not_registered", "TRADE_DATE", "20260803", "20260804"));
        AtomicInteger calls = new AtomicInteger();
        safeFailure(() -> TushareRangeSourceProbe.run(input, (d, p, c) -> { calls.incrementAndGet(); return null; }, CLOCK));
        assertThat(calls.get()).isZero();
    }

    @Test void rejectsUnknownFieldsMissingRefsInvalidDatesAndMultipleStocks() {
        List<ObjectNode> bad = new ArrayList<>();
        ObjectNode extra = plan(); extra.put("token", "synthetic"); bad.add(extra);
        extra = plan(); first(extra).put("extra", "bad"); bad.add(extra);
        extra = plan(); first(extra).withArray("evidenceRefs").removeAll(); bad.add(extra);
        extra = plan(); extra.withArray("cases").add(first(extra).deepCopy()); bad.add(extra);
        for (String key : List.of("type", "offset", "limit", "trade_date")) {
            extra = plan(); first(extra).withObject("params").put(key, "1"); bad.add(extra);
        }
        for (String stock : List.of("000001.SZ,600000.SH", "000001.sz", "foo.SZ", "000001.XX")) {
            extra = plan(); first(extra).withObject("params").put("ts_code", stock); bad.add(extra);
        }
        for (String date : List.of("20260230", "19991231", "21010101", "2026-08-03", "20260805")) {
            extra = plan(); first(extra).put("start", date); first(extra).withObject("params").put("start_date", date); bad.add(extra);
        }
        extra = plan(); first(extra).put("dateAxis", "REPORT_PERIOD"); bad.add(extra);
        extra = plan(); first(extra).withObject("params").put("start_date", "20260802"); bad.add(extra);
        for (ObjectNode value : bad) safeFailure(() -> TushareRangeSourceProbe.validatePlan(value));
    }

    @Test void singlesFollowYamlAndDoNotInventRangeOrType() {
        ObjectNode input = plan(); input.withArray("cases").removeAll().add(singleCase("s", "fina_mainbz", Map.of("ts_code", "000001.SZ")));
        assertThat(TushareRangeSourceProbe.validatePlan(input)).isEqualTo(input);
        first(input).withObject("params").put("type", "P"); safeFailure(() -> TushareRangeSourceProbe.validatePlan(input));
        input.withArray("cases").removeAll().add(singleCase("s", "stock_company", Map.of("ts_code", "600000.SH", "exchange", "SZSE")));
        safeFailure(() -> TushareRangeSourceProbe.validatePlan(input));
        input.withArray("cases").removeAll().add(rangeCase("s", "pledge_stat", "REPORT_PERIOD", "20260803", "20260804"));
        safeFailure(() -> TushareRangeSourceProbe.validatePlan(input));
        input.withArray("cases").removeAll().add(singleCase("s", "daily", Map.of("ts_code", "000001.SZ")));
        safeFailure(() -> TushareRangeSourceProbe.validatePlan(input));
    }

    @Test void nativeRangeKeepsOriginalParamsAndProjectsNoPayloadOrPersistence() {
        JsonNode output = TushareRangeSourceProbe.run(plan(), (d,p,c) -> {
            c.beforeRequest(); return envelope("daily", p, List.of(row("daily", "20260803")));
        }, CLOCK);
        JsonNode item = output.path("cases").get(0);
        assertThat(item.path("params")).isEqualTo(first(plan()).path("params"));
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("requestCount").asInt()).isEqualTo(1);
        assertThat(item.path("batchNodes").get(0).path("start").asText()).isEqualTo("20260803");
        for (String key : List.of("taskId", "submissionId", "insertedRows", "updatedRows", "sqlBeforeKeyCount", "sqlAfterKeyCount", "ownershipSummary", "businessKeyDigest")) assertThat(item.path(key).isNull()).as(key).isTrue();
        assertThat(output.toString()).doesNotContain("fields", "data", "raw", "controlled-secret");
    }

    @Test void naturalDaysIncludeWeekendAndLeapDayWithOneSharedContext() {
        ObjectNode input = plan(); input.withArray("cases").removeAll().add(rangeCase("days", "dividend", "ANNOUNCEMENT_DATE", "20240228", "20240302"));
        Set<BatchCallContext> contexts = new HashSet<>(); List<String> dates = new ArrayList<>();
        JsonNode output = TushareRangeSourceProbe.run(input, (d,p,c) -> {
            contexts.add(c); c.beforeRequest(); dates.add((String)p.get("ann_date"));
            return envelope("dividend", p, List.of(row("dividend", (String)p.get("ann_date"))));
        }, CLOCK);
        assertThat(dates).containsExactly("20240228", "20240229", "20240301", "20240302");
        assertThat(contexts).hasSize(1);
        assertThat(output.path("cases").get(0).path("leafCount").asInt()).isEqualTo(4);
    }

    @Test void tradingDaysRequireCompleteCalendarAndCountPlanningRequest() {
        ObjectNode input = plan(); first(input).put("apiName", "top_list"); List<String> calls = new ArrayList<>();
        JsonNode output = TushareRangeSourceProbe.run(input, (d,p,c) -> {
            c.beforeRequest(); String api = d.datasetKey().apiName().value(); calls.add(api);
            if (api.equals("trade_cal")) return envelope(api,p,List.of(calendarDay("20260803",1,"SZSE"),calendarDay("20260804",0,"SZSE")));
            assertThat(p).isEqualTo(Map.of("ts_code","000001.SZ","trade_date","20260803"));
            return envelope(api,p,List.of(row(api,"20260803")));
        }, CLOCK);
        assertThat(calls).containsExactly("trade_cal", "top_list");
        JsonNode item = output.path("cases").get(0);
        assertThat(item.path("requestCount").asInt()).isEqualTo(2);
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(1);
        assertThat(item.path("leafCount").asInt()).isEqualTo(1);
    }

    @Test void closedCalendarIsDifferentFromEmptyCalendarAndEmptySecurities() {
        ObjectNode input = plan(); first(input).put("apiName", "top_list");
        JsonNode closed = TushareRangeSourceProbe.run(input, (d,p,c) -> { c.beforeRequest(); return envelope("trade_cal",p,List.of(calendarDay("20260803",0,"SZSE"),calendarDay("20260804",0,"SZSE"))); }, CLOCK).path("cases").get(0);
        assertThat(closed.path("status").asText()).isEqualTo("PASS");
        assertThat(closed.path("expectedCoverage").asText()).isEqualTo("COMPLETE_CLOSED_CALENDAR");
        assertThat(closed.path("leafCount").asInt()).isZero();
        JsonNode empty = TushareRangeSourceProbe.run(input, (d,p,c) -> { c.beforeRequest(); return envelope("trade_cal",p,List.of()); }, CLOCK).path("cases").get(0);
        assertThat(empty.path("status").asText()).isEqualTo("FAILED");
        assertThat(empty.path("errorCode").asText()).isEqualTo("BATCH_COMPLETENESS_UNCONFIRMED");
        empty = TushareRangeSourceProbe.run(plan(), (d,p,c) -> { c.beforeRequest(); return envelope("daily",p,List.of()); }, CLOCK).path("cases").get(0);
        assertThat(empty.path("status").asText()).isEqualTo("EVIDENCE_MISSING");
    }

    @Test void bseDirectCalendarProbeKeepsRequestedExchange() {
        ObjectNode input = plan(); ObjectNode item = rangeCase("bse", "trade_cal", "CALENDAR_DATE", "20260803", "20260804");
        item.withObject("params").remove("ts_code"); item.withObject("params").put("exchange", "BSE"); input.withArray("cases").removeAll().add(item);
        JsonNode output = TushareRangeSourceProbe.run(input, (d,p,c) -> { c.beforeRequest(); assertThat(p.get("exchange")).isEqualTo("BSE"); return envelope("trade_cal",p,List.of(calendarDay("20260803",1,"BSE"),calendarDay("20260804",1,"BSE"))); }, CLOCK);
        assertThat(output.path("cases").get(0).path("status").asText()).isEqualTo("PASS");
    }

    @Test void bjCandidateUsesSseCalendarAndProductionPreservesBjSecurity() {
        ObjectNode input=plan();first(input).put("apiName","top_list").withObject("params").put("ts_code","920008.BJ");
        List<Map<String,Object>> requests=new ArrayList<>();
        JsonNode result=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();requests.add(p);
            if(d.datasetKey().apiName().value().equals("trade_cal"))
                return envelope("trade_cal",p,List.of(calendarDay("20260804",0,"SSE"),calendarDay("20260803",1,"SSE")));
            return envelope("top_list",p,List.of(with("top_list",row("top_list","20260803"),"ts_code","920008.BJ")));
        },CLOCK).path("cases").get(0);
        assertThat(result.path("status").asText()).isEqualTo("PASS");
        assertThat(requests).containsExactly(Map.of("exchange","SSE","start_date","20260803","end_date","20260804"),
                Map.of("ts_code","920008.BJ","trade_date","20260803"));
        assertThat(result.path("requestCount").asInt()).isEqualTo(2);
        assertThat(result.path("reviewMethod").asText()).contains("calendarExchange=SSE", "calendarOpenDates=20260803;");
        var productionParams=params("top_list");productionParams.put("ts_code","920008.BJ");
        assertThat(production().sourceParameters(api("top_list"),productionParams,RANGE))
                .isEqualTo(Map.of("ts_code","920008.BJ","trade_date","20240228"));
        result=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();return envelope("trade_cal",p,List.of(calendarDay("20260803",1,"SSE")));
        },CLOCK).path("cases").get(0);
        assertThat(result.path("errorCode").asText()).isEqualTo("BATCH_COMPLETENESS_UNCONFIRMED");
        assertThat(result.path("requestCount").asInt()).isEqualTo(1);
        assertThat(result.path("leafCount").asInt()).isZero();
    }

    @Test void calendarProjectionRetainsResponseCountWhenCoverageFailsWithoutInventingOpenDays() {
        ObjectNode input=plan();
        ObjectNode item=rangeCase("bse","trade_cal","CALENDAR_DATE","20260803","20260804");
        item.withObject("params").remove("ts_code");item.withObject("params").put("exchange","BSE");
        input.withArray("cases").removeAll().add(item);
        for(var rows:List.<List<List<Object>>>of(List.of(),List.of(calendarDay("20260803",1,"BSE")),
                List.of(calendarDay("20260803",1,"BSE"),calendarDay("20260803",0,"BSE")),
                List.of(calendarDay("20260803",1,"private-exchange")))) {
            JsonNode result=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope("trade_cal",p,rows);},CLOCK).path("cases").get(0);
            assertThat(result.path("status").asText()).isEqualTo("FAILED");
            assertThat(result.path("sourceRowCount").asInt()).isZero();
            assertThat(result.path("reviewMethod").asText()).contains("calendarExchange=BSE",
                    "calendarResponseRowCount="+rows.size()+";", "calendarOpenDates=unconfirmed");
            assertThat(result.toString()).doesNotContain("private-exchange");
        }
    }

    @Test void calendarProjectionPublishesOnlyValidatedOpenDatesAndNoCountsBeforeValidResponse() {
        ObjectNode input=plan();first(input).put("apiName","top_list");
        JsonNode result=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();
            if(d.datasetKey().apiName().value().equals("trade_cal"))
                return envelope("trade_cal",p,List.of(calendarDay("20260804",0,"SZSE"),calendarDay("20260803",1,"SZSE")));
            return envelope("top_list",p,List.of(row("top_list","20260803")));
        },CLOCK).path("cases").get(0);
        assertThat(result.path("reviewMethod").asText()).contains("calendarExchange=SZSE",
                "calendarResponseRowCount=2;", "calendarOpenDates=20260803;");
        result=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();return envelope("trade_cal",p,List.of(calendarDay("20260803",0,"SZSE"),calendarDay("20260804",0,"SZSE")));
        },CLOCK).path("cases").get(0);
        assertThat(result.path("status").asText()).isEqualTo("PASS");
        assertThat(result.path("reviewMethod").asText()).contains("calendarResponseRowCount=2;", "calendarOpenDates=;", "closedCalendar=true");
        for(boolean transportFailure:List.of(false,true)) {
            result=TushareRangeSourceProbe.run(input,(d,p,c)->{
                c.beforeRequest();if(transportFailure)throw new SourceException(ErrorCode.SOURCE_NETWORK_ERROR,"private-response");
                return envelope("daily",p,List.of());
            },CLOCK).path("cases").get(0);
            assertThat(result.path("status").asText()).isEqualTo("FAILED");
            assertThat(result.path("reviewMethod").asText()).doesNotContain("calendarResponseRowCount", "calendarOpenDates", "private-response");
        }
    }

    @Test void failureStopsRunWithoutRetryAndKeepsRemainingFactsNull() {
        ObjectNode input = plan(); input.withArray("cases").add(rangeCase("later","daily","TRADE_DATE","20260803","20260804")); AtomicInteger calls = new AtomicInteger();
        JsonNode output = TushareRangeSourceProbe.run(input,(d,p,c)->{ c.beforeRequest(); calls.incrementAndGet(); throw new SourceException(ErrorCode.SOURCE_PERMISSION_DENIED,"token=synthetic-do-not-echo"); },CLOCK);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(output.path("cases").get(0).path("errorCode").asText()).isEqualTo("SOURCE_PERMISSION_DENIED");
        JsonNode later = output.path("cases").get(1);
        assertThat(later.path("status").asText()).isEqualTo("NOT_RUN");
        assertThat(later.path("requestCount").isNull()).isTrue();
        assertThat(later.path("sourceRowCount").isNull()).isTrue();
        assertThat(output.toString()).doesNotContain("synthetic-do-not-echo", "token=", "SourceException");
    }

    @Test void rangeMismatchStopsWithoutCountingInvalidRowsAsSuccessful() {
        JsonNode output = TushareRangeSourceProbe.run(plan(),(d,p,c)->{c.beforeRequest(); return envelope("daily",p,List.of(row("daily","20260805")));},CLOCK).path("cases").get(0);
        assertThat(output.path("status").asText()).isEqualTo("FAILED");
        assertThat(output.path("errorCode").asText()).isEqualTo("SOURCE_RANGE_MISMATCH");
        assertThat(output.path("sourceRowCount").asInt()).isZero();
    }

    @Test void budgetStopsAt5000AndDeadlineAndStopSignal() {
        var context = new TushareRangeSourceProbe.Context(CLOCK);
        for (int i=0;i<5000;i++) context.beforeRequest();
        assertThatThrownBy(context::beforeRequest).hasMessage("Download task limit exceeded");
        assertThat(context.requestCount()).isEqualTo(5000);
        assertThat(context.deadline()).isEqualTo(CLOCK.instant().plus(Duration.ofMinutes(30)));
        context.stop(); assertThat(context.stopRequested()).isTrue();
        assertThatThrownBy(context::beforeRequest).hasMessage("Download task execution was interrupted");
        Clock advancing = new Clock() { int calls; public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return CLOCK.instant().plusSeconds(calls++ == 0 ? 0 : 1800);} };
        assertThatThrownBy(new TushareRangeSourceProbe.Context(advancing)::beforeRequest).hasMessage("Download task limit exceeded");
    }

    @Test void unknownCompletenessStillRecordsSuccessfulSourceSemantics() {
        ObjectNode input = plan(); first(input).put("apiName", "adj_factor");
        JsonNode item = TushareRangeSourceProbe.run(input, (d,p,c) -> {
            c.beforeRequest(); return envelope("adj_factor",p,List.of(row("adj_factor","20260803")));
        }, CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("reviewMethod").asText()).contains("completeness UNKNOWN", "actualDates=20260803", "min=20260803", "max=20260803");
        assertThat(production().batchDescriptor(api("adj_factor")).orElseThrow().availability()).isEqualTo(com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.Availability.AVAILABLE);
    }

    @Test void candidateFullResponseNeedsEvidenceAndDoesNotSplitOrRetry() {
        ObjectNode input = plan(); first(input).put("apiName","fina_indicator").put("dateAxis","REPORT_PERIOD");
        AtomicInteger calls = new AtomicInteger();
        JsonNode item = TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();calls.incrementAndGet();return envelope("fina_indicator",p,Collections.nCopies(100,row("fina_indicator","20260803")));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("EVIDENCE_MISSING");
        assertThat(item.path("reviewMethod").asText()).contains("candidateRowLimit=100", "candidateLimitReached=true");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void partialDailyFailureKeepsOnlyExecutedNodesAndDoesNotClaimCoveredRange() {
        ObjectNode input=plan();input.withArray("cases").removeAll().add(rangeCase("days","dividend","ANNOUNCEMENT_DATE","20240228","20240302"));
        AtomicInteger calls=new AtomicInteger();
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();if(calls.incrementAndGet()==2) throw new IllegalStateException("password=synthetic-do-not-echo");
            return envelope("dividend",p,List.of(row("dividend","20240228")));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("errorCode").asText()).isEqualTo("SOURCE_UNAVAILABLE");
        assertThat(item.path("leafCount").asInt()).isEqualTo(2);
        assertThat(item.path("succeededLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("failedLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(1);
        assertThat(item.path("batchNodes").get(1).path("sourceRowCount").isNull()).isTrue();
        assertThat(item.toString()).doesNotContain("synthetic-do-not-echo", "password=", "IllegalStateException");
    }

    @Test void validatesAllFortyCurrentSingleShapesAndThirtyFourRangeShapes() {
        ObjectNode input=plan();input.withArray("cases").removeAll();
        for(var definition:DEFINITIONS) {
            Map<String,Object> params=new LinkedHashMap<>();
            for(var p:definition.parameters()) params.put(p.name(),switch(p.name()) {
                case "ts_code" -> "000001.SZ";
                case "exchange","exchange_id" -> "SZSE";
                case "list_status" -> "L";
                default -> "20260807";
            });
            input.withArray("cases").add(singleCase(definition.datasetKey().apiName().value(),definition.datasetKey().apiName().value(),params));
        }
        assertThat(TushareRangeSourceProbe.validatePlan(input).path("cases").size()).isEqualTo(40);
        input.withArray("cases").removeAll();
        rows().forEach(row->{
            String[] fields=row.split(" ");ObjectNode item=rangeCase(fields[0],fields[0],fields[3],"20260803","20260804");
            if(!fields[1].equals("S")) item.withObject("params").remove("ts_code");
            if(fields[1].equals("I")) item.withObject("params").put("exchange_id","SZSE");
            if(fields[1].equals("E")) item.withObject("params").put("exchange","SZSE");
            input.withArray("cases").add(item);
        });
        assertThat(TushareRangeSourceProbe.validatePlan(input).path("cases").size()).isEqualTo(34);
    }

    @Test void standaloneCalendarSummaryRecordsActualClosedDays() {
        ObjectNode input=plan(); ObjectNode item=rangeCase("closed","trade_cal","CALENDAR_DATE","20260803","20260804");
        item.withObject("params").remove("ts_code");item.withObject("params").put("exchange","SZSE");input.withArray("cases").removeAll().add(item);
        JsonNode evidence=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope("trade_cal",p,List.of(calendarDay("20260803",0,"SZSE"),calendarDay("20260804",0,"SZSE")));},CLOCK).path("cases").get(0);
        assertThat(evidence.path("status").asText()).isEqualTo("PASS");
        assertThat(evidence.path("reviewMethod").asText()).contains("calendarOpenDayCount=0", "closedCalendar=true");
    }

    @Test void singleCalendarRejectsMissingEmptyDuplicateDaysAndInvalidOpenFlag() {
        List<List<List<Object>>> calendars=List.of(
                List.of(calendarDay("20260803",0,"SZSE")),
                List.of(),
                List.of(calendarDay("20260803",0,"SZSE"),calendarDay("20260803",0,"SZSE"),calendarDay("20260804",0,"SZSE")),
                List.of(calendarDay("20260803",2,"SZSE"),calendarDay("20260804",0,"SZSE")));
        for(int index=0;index<calendars.size();index++) {
            ObjectNode input=plan();input.withArray("cases").insert(0,singleCase("calendar","trade_cal",Map.of("exchange","SZSE","start_date","20260803","end_date","20260804")));
            List<List<Object>> rows=calendars.get(index);AtomicInteger calls=new AtomicInteger();
            JsonNode output=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();calls.incrementAndGet();return envelope("trade_cal",p,rows);},CLOCK);
            JsonNode item=output.path("cases").get(0);
            assertThat(item.path("status").asText()).isEqualTo("FAILED");
            assertThat(item.path("errorCode").asText()).isEqualTo(index==3?"SOURCE_PAYLOAD_INVALID":"BATCH_COMPLETENESS_UNCONFIRMED");
            assertThat(item.path("sourceRowCount").asInt()).isZero();
            assertThat(output.path("cases").get(1).path("status").asText()).isEqualTo("NOT_RUN");
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test void singleCompleteClosedCalendarRecordsActualCoverage() {
        ObjectNode input=plan();input.withArray("cases").removeAll().add(singleCase("calendar","trade_cal",Map.of("exchange","SZSE","start_date","20260803","end_date","20260804")));
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope("trade_cal",p,List.of(calendarDay("20260803",0,"SZSE"),calendarDay("20260804",0,"SZSE")));},CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(2);
        assertThat(item.path("reviewMethod").asText()).contains("calendarOpenDayCount=0", "closedCalendar=true", "actualDates=20260803,20260804");
    }

    @Test void singleWrongDailyAndAnnouncementDatesFailAndStopTheFixedRun() {
        for(String api:List.of("daily","income","fina_indicator","top10_holders","top10_floatholders","repurchase")) {
            String column=api.equals("daily")?"trade_date":"ann_date";
            Map<String,Object> params=new LinkedHashMap<>(Map.of(column,"20260807"));if(!api.equals("repurchase")) params.put("ts_code","000001.SZ");
            ObjectNode input=plan();input.withArray("cases").insert(0,singleCase("wrong-date",api,params));AtomicInteger calls=new AtomicInteger();
            JsonNode output=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();calls.incrementAndGet();return envelope(api,p,List.of(with(api,row(api,"20260807"),column,"20260806")));},CLOCK);
            JsonNode item=output.path("cases").get(0);
            assertThat(item.path("status").asText()).as(api).isEqualTo("FAILED");
            assertThat(item.path("errorCode").asText()).isEqualTo("SOURCE_RANGE_MISMATCH");
            assertThat(item.path("batchNodes").get(0).path("sourceRowCount").isNull()).isTrue();
            assertThat(item.path("sourceRowCount").asInt()).isZero();
            assertThat(output.path("cases").get(1).path("status").asText()).isEqualTo("NOT_RUN");
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test void singleFinancialAnnouncementIsDistinctFromRangeReportPeriod() {
        for(String api:List.of("fina_indicator","top10_holders","top10_floatholders")) {
            ObjectNode input=plan();input.withArray("cases").removeAll().add(singleCase("announcement",api,Map.of("ts_code","000001.SZ","ann_date","20260807")));
            JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope(api,p,List.of(with(api,row(api,"20251231"),"ann_date","20260807")));},CLOCK).path("cases").get(0);
            assertThat(item.path("status").asText()).as(api).isEqualTo("PASS");
            assertThat(item.path("reviewMethod").asText()).contains("actualDateColumn=ann_date", "actualDates=20260807").doesNotContain("actualDates=20251231");
        }
    }

    @Test void singleNewShareUsesIpoDateWindowAndNotIssueDate() {
        ObjectNode input=plan();input.withArray("cases").removeAll().add(singleCase("ipo","new_share",Map.of("start_date","20260803","end_date","20260804")));
        JsonNode invalid=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope("new_share",p,List.of(with("new_share",row("new_share","20260805"),"issue_date","20260803")));},CLOCK).path("cases").get(0);
        assertThat(invalid.path("status").asText()).isEqualTo("FAILED");
        assertThat(invalid.path("errorCode").asText()).isEqualTo("SOURCE_RANGE_MISMATCH");
        JsonNode valid=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope("new_share",p,List.of(with("new_share",row("new_share","20260803"),"issue_date","20260805"),with("new_share",row("new_share","20260804"),"issue_date","20260806")));},CLOCK).path("cases").get(0);
        assertThat(valid.path("status").asText()).isEqualTo("PASS");
        assertThat(valid.path("reviewMethod").asText()).contains("actualDateColumn=ipo_date", "actualDates=20260803,20260804");
    }

    @Test void singleSnapshotsDoNotAcquireRangeDatePredicates() {
        for(String api:List.of("fina_mainbz","stk_holdernumber","stk_managers","pledge_detail")) {
            ObjectNode input=plan();input.withArray("cases").removeAll().add(singleCase("snapshot",api,Map.of("ts_code","000001.SZ")));
            JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope(api,p,List.of(row(api,"20241231"),row(api,"20250131")));},CLOCK).path("cases").get(0);
            assertThat(item.path("status").asText()).as(api).isEqualTo("PASS");
            assertThat(item.path("reviewMethod").asText()).contains("actualDateColumn=none", "datePredicate=none");
        }
    }

    @Test void incomeRangeProjectsSameRowDateDistinctionAndUnavailableCompanions() {
        ObjectNode input=plan();input.withArray("cases").removeAll().add(rangeCase("income","income","ANNOUNCEMENT_DATE","20260803","20260804"));
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope("income",p,List.of(
                with("income",row("income","20260803"),"end_date","20251231"),
                with("income",row("income","20260803"),"end_date","20260804"),
                with("income",row("income","20260804"),"end_date","20260804"),
                row("income","20260804"),
                with("income",row("income","20260804"),"end_date","20260230")));},CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("reviewMethod").asText()).contains(
                "dateComparisonValidRows=3", "dateComparisonUnavailableRows=2",
                "annDateDifferentFromEndDateRows=2", "annInRangeEndOutsideRows=1");
    }

    @Test void finaIndicatorRangeProjectsSameRowOutsideIntersectionAndUnavailableCompanions() {
        ObjectNode input=plan();first(input).put("apiName","fina_indicator").put("dateAxis","REPORT_PERIOD");
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope("fina_indicator",p,List.of(
                with("fina_indicator",row("fina_indicator","20260803"),"ann_date","20260731"),
                with("fina_indicator",row("fina_indicator","20260803"),"ann_date","20260804"),
                with("fina_indicator",row("fina_indicator","20260804"),"ann_date","20260804"),
                row("fina_indicator","20260804"),
                with("fina_indicator",row("fina_indicator","20260804"),"ann_date","20260230")));},CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("reviewMethod").asText()).contains(
                "dateComparisonValidRows=3", "dateComparisonUnavailableRows=2", "endInRangeAnnOutsideRows=1");
    }

    @Test void holderAndIpoRangesCompareDatesFromTheSameRow() {
        for (String api : List.of("stk_holdernumber", "new_share")) {
            String companion = api.equals("new_share") ? "issue_date" : "end_date";
            ObjectNode input = dateComparisonPlan(api);
            JsonNode item = TushareRangeSourceProbe.run(input, (d,p,c) -> {
                c.beforeRequest();
                return envelope(api,p,List.of(
                        with(api,row(api,"20260803"),companion,"20260731"),
                        with(api,row(api,"20260803"),companion,"20260804"),
                        with(api,row(api,"20260804"),companion,"20260804"),
                        with(api,row(api,"20260804"),companion,null),
                        with(api,row(api,"20260804"),companion,"20260230")));
            },CLOCK).path("cases").get(0);
            assertThat(item.path("status").asText()).isEqualTo("PASS");
            assertThat(item.path("sourceRowCount").asInt()).isEqualTo(5);
            assertThat(item.path("reviewMethod").asText()).contains(
                    "dateComparisonValidRows=3", "dateComparisonUnavailableRows=2",
                    api.equals("new_share") ? "ipoDateDifferentFromIssueDateRows=2" : "annDateDifferentFromEndDateRows=2",
                    api.equals("new_share") ? "ipoInRangeIssueOutsideRows=1" : "annInRangeEndOutsideRows=1");
            assertThat(item.toString()).doesNotContain("20260731", "20260230");
        }
    }

    @Test void holderAndIpoComparisonsDistinguishEmptyFailedUnexecutedAndSingle() {
        for (String api : List.of("stk_holdernumber", "new_share")) {
            ObjectNode input = dateComparisonPlan(api);
            ObjectNode ranged = first(input).deepCopy();
            input.withArray("cases").removeAll().add(ranged.deepCopy().put("caseId","empty"))
                    .add(singleCase("single",api,api.equals("new_share")
                            ? Map.of("start_date","20260803","end_date","20260804") : Map.of("ts_code","000001.SZ")))
                    .add(ranged.deepCopy().put("caseId","failed"))
                    .add(ranged.deepCopy().put("caseId","not-run"));
            AtomicInteger calls = new AtomicInteger();
            JsonNode cases = TushareRangeSourceProbe.run(input,(d,p,c) -> {
                c.beforeRequest();
                if (calls.incrementAndGet()==3) throw new IllegalStateException("private response");
                return envelope(api,p,List.of());
            },CLOCK).path("cases");
            assertThat(cases.get(0).path("reviewMethod").asText()).contains("dateComparisonValidRows=0", "dateComparisonUnavailableRows=0");
            assertThat(cases.get(1).path("reviewMethod").asText()).doesNotContain("dateComparison", "InRange", "DifferentFrom");
            assertThat(cases.get(2).path("status").asText()).isEqualTo("FAILED");
            assertThat(cases.get(2).path("reviewMethod").asText()).doesNotContain("dateComparison", "InRange", "DifferentFrom");
            assertThat(cases.get(3).path("status").asText()).isEqualTo("NOT_RUN");
            assertThat(cases.get(3).path("reviewMethod").isNull()).isTrue();
        }
    }

    @Test void holderAndIpoComparisonsDoNotRescueInvalidPrimaryDates() {
        for (String api : List.of("stk_holdernumber", "new_share")) {
            for (String primary : List.of("20260805", "20260230")) {
                JsonNode item = TushareRangeSourceProbe.run(dateComparisonPlan(api),(d,p,c) -> {
                    c.beforeRequest();
                    return envelope(api,p,List.of(with(api,row(api,primary),
                            api.equals("new_share") ? "issue_date" : "end_date","20260803")));
                },CLOCK).path("cases").get(0);
                assertThat(item.path("status").asText()).isEqualTo("FAILED");
                assertThat(item.path("reviewMethod").asText()).doesNotContain("dateComparison", "InRange", "DifferentFrom");
            }
        }
    }

    private static ObjectNode dateComparisonPlan(String api) {
        ObjectNode input = plan();
        first(input).put("apiName",api).put("dateAxis",api.equals("new_share") ? "ISSUE_DATE"
                : api.startsWith("top10_") ? "REPORT_PERIOD" : api.equals("suspend_d") ? "TRADE_DATE" : "ANNOUNCEMENT_DATE");
        if (api.equals("new_share")) first(input).withObject("params").remove("ts_code");
        return input;
    }

    @Test void unknownFinancialRangesCompareTheDeclaredAxisFromTheSameRow() {
        for(String api:List.of("balancesheet","cashflow","fina_audit","express","top10_holders","top10_floatholders")) {
            boolean report=api.startsWith("top10_");String companion=report ? "ann_date" : "end_date";
            JsonNode item=TushareRangeSourceProbe.run(dateComparisonPlan(api),(d,p,c)->{
                c.beforeRequest();return envelope(api,p,List.of(
                        with(api,row(api,"20260803"),companion,"20260731"),
                        with(api,row(api,"20260803"),companion,"20260804"),
                        with(api,row(api,"20260804"),companion,"20260804"),
                        with(api,row(api,"20260804"),companion,null),
                        with(api,row(api,"20260804"),companion,"20260230")));
            },CLOCK).path("cases").get(0);
            assertThat(item.path("status").asText()).as(api).isEqualTo("PASS");
            assertThat(item.path("sourceRowCount").asInt()).isEqualTo(5);
            assertThat(item.path("reviewMethod").asText()).contains("dateComparisonValidRows=3", "dateComparisonUnavailableRows=2",
                    report ? "endInRangeAnnOutsideRows=1" : "annInRangeEndOutsideRows=1");
            assertThat(item.toString()).doesNotContain("20260731", "20260230");
        }
    }

    @Test void cashflowAndManagersKeepActualAnnouncementAndTenureDatesSeparate() {
        for(String api:List.of("cashflow","stk_managers")) {
            String companion=api.equals("cashflow") ? "f_ann_date" : "begin_date";
            JsonNode item=TushareRangeSourceProbe.run(dateComparisonPlan(api),(d,p,c)->{
                c.beforeRequest();var rows=List.of(
                        with(api,row(api,"20260803"),companion,"20260731"),
                        with(api,row(api,"20260804"),companion,"20260804"),
                        with(api,row(api,"20260804"),companion,"20260230"));
                if(api.equals("stk_managers")) rows=List.of(
                        with(api,with(api,rows.get(0),"end_date","20260803"),"name","private-manager-name"),
                        with(api,rows.get(1),"end_date","20260805"),rows.get(2));
                return envelope(api,p,rows);
            },CLOCK).path("cases").get(0);
            String prefix=api.equals("cashflow") ? "cashflowActualAnnouncement" : "managerBegin";
            assertThat(item.path("status").asText()).isEqualTo("PASS");
            assertThat(item.path("reviewMethod").asText()).contains(prefix+"DateComparisonValidRows=2",
                    prefix+"DateComparisonUnavailableRows=1",
                    api.equals("cashflow") ? prefix+"DifferentRows=1" : "managerAnnDateDifferentFromBeginDateRows=1",
                    api.equals("cashflow") ? prefix+"AnnInRangeCompanionOutsideRows=1" : "managerAnnInRangeBeginOutsideRows=1");
            if(api.equals("stk_managers")) assertThat(item.path("reviewMethod").asText())
                    .contains("managerEndDateComparisonValidRows=2", "managerEndDateComparisonUnavailableRows=1",
                            "managerAnnDateDifferentFromEndDateRows=1", "managerAnnInRangeEndOutsideRows=1");
            assertThat(item.toString()).doesNotContain("20260731", "20260230", "20260805", "private-manager-name");
        }
    }

    @Test void suspensionProjectionCountsTypesWithoutExposingIntradayText() {
        String api="suspend_d";
        JsonNode item=TushareRangeSourceProbe.run(dateComparisonPlan(api),(d,p,c)->{
            c.beforeRequest();return envelope(api,p,List.of(
                    with(api,with(api,row(api,"20260803"),"suspend_type","S"),"suspend_timing","private-intraday"),
                    with(api,row(api,"20260804"),"suspend_type","R"),
                    with(api,row(api,"20260804"),"suspend_type","invalid-private-type"),row(api,"20260804")));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("reviewMethod").asText()).contains("suspensionRows=1", "resumptionRows=1",
                "suspensionTypeUnavailableRows=2", "intradayTimingPresentRows=1");
        assertThat(item.toString()).doesNotContain("private-intraday", "invalid-private-type");
    }

    @Test void unknownProjectionsDistinguishEmptySingleFailedAndUnexecutedCases() {
        for(String api:List.of("balancesheet","cashflow","fina_audit","express","top10_holders","top10_floatholders","stk_managers","suspend_d")) {
            ObjectNode input=dateComparisonPlan(api),ranged=first(input).deepCopy();
            Map<String,Object> params=api.equals("stk_managers") ? Map.of("ts_code","000001.SZ")
                    : Map.of("ts_code","000001.SZ",api.equals("suspend_d") ? "trade_date" : "ann_date","20260803");
            input.withArray("cases").removeAll().add(ranged.deepCopy().put("caseId","empty"))
                    .add(singleCase("single",api,params)).add(ranged.deepCopy().put("caseId","invalid-primary"))
                    .add(ranged.deepCopy().put("caseId","not-run"));
            AtomicInteger calls=new AtomicInteger();
            JsonNode cases=TushareRangeSourceProbe.run(input,(d,p,c)->{
                c.beforeRequest();return envelope(api,p,calls.incrementAndGet()==3 ? List.of(row(api,"20260805")) : List.of());
            },CLOCK).path("cases");
            String metric=api.equals("suspend_d") ? "suspensionRows" : api.equals("stk_managers") ? "managerBeginDateComparisonValidRows" : "dateComparisonValidRows";
            assertThat(cases.get(0).path("reviewMethod").asText()).as(api).contains(metric+"=0");
            for(int i:List.of(1,2)) assertThat(cases.get(i).path("reviewMethod").asText())
                    .doesNotContain(metric,"cashflowActualAnnouncement","managerEnd","resumptionRows");
            assertThat(cases.get(2).path("status").asText()).isEqualTo("FAILED");
            assertThat(cases.get(3).path("status").asText()).isEqualTo("NOT_RUN");
            assertThat(cases.get(3).path("reviewMethod").isNull()).isTrue();
        }
    }

    @Test void repurchaseRangeCountsValidRowsAndDistinctStocksWithoutRetainingCodes() {
        ObjectNode input=plan();first(input).put("apiName","repurchase").put("dateAxis","ANNOUNCEMENT_DATE");first(input).withObject("params").remove("ts_code");
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{c.beforeRequest();return envelope("repurchase",p,List.of(
                row("repurchase","20260803"), row("repurchase","20260804"),
                with("repurchase",row("repurchase","20260804"),"ts_code","600000.SH"),
                with("repurchase",row("repurchase","20260804"),"ts_code","830000.BJ"),
                with("repurchase",row("repurchase","20260804"),"ts_code","000001.sz"),
                with("repurchase",row("repurchase","20260804"),"ts_code","12345.SZ"),
                with("repurchase",row("repurchase","20260804"),"ts_code","123456.XX"),
                with("repurchase",row("repurchase","20260804"),"ts_code",null)));},CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("reviewMethod").asText()).contains(
                "validStockCodeRows=4", "unavailableStockCodeRows=4", "distinctStockCodeCount=3");
        assertThat(item.toString()).doesNotContain("600000.SH", "830000.BJ", "000001.sz");
    }

    @Test void projectionScopeDistinguishesEmptyFailedUnexecutedSingleAndUnrelatedCases() {
        ObjectNode input=plan();input.withArray("cases").removeAll()
                .add(rangeCase("empty","income","ANNOUNCEMENT_DATE","20260803","20260804"))
                .add(singleCase("single","income",Map.of("ts_code","000001.SZ","ann_date","20260803")))
                .add(rangeCase("unrelated","daily","TRADE_DATE","20260803","20260804"))
                .add(rangeCase("failed","fina_indicator","REPORT_PERIOD","20260803","20260804"))
                .add(rangeCase("unexecuted","repurchase","ANNOUNCEMENT_DATE","20260803","20260804"));
        ((ObjectNode)input.withArray("cases").get(4).path("params")).remove("ts_code");
        JsonNode cases=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();String api=d.datasetKey().apiName().value();
            if(api.equals("fina_indicator")) throw new IllegalStateException("raw response unavailable");
            return envelope(api,p,List.of());
        },CLOCK).path("cases");
        assertThat(cases.get(0).path("reviewMethod").asText()).contains(
                "dateComparisonValidRows=0", "dateComparisonUnavailableRows=0",
                "annDateDifferentFromEndDateRows=0", "annInRangeEndOutsideRows=0");
        assertThat(cases.get(1).path("reviewMethod").asText()).doesNotContain("dateComparison", "annDateDifferent", "annInRange");
        assertThat(cases.get(2).path("reviewMethod").asText()).doesNotContain("dateComparison", "StockCode", "distinctStock");
        assertThat(cases.get(3).path("status").asText()).isEqualTo("FAILED");
        assertThat(cases.get(3).path("reviewMethod").asText()).doesNotContain("dateComparison", "endInRangeAnnOutside");
        assertThat(cases.get(4).path("status").asText()).isEqualTo("NOT_RUN");
        assertThat(cases.get(4).path("reviewMethod").isNull()).isTrue();
    }

    @Test void mainbzProjectsCategoriesAndCrossCategoryKeysWithoutChangingRequestsOrRows() {
        for (String mode : List.of("SINGLE", "RANGE")) {
            ObjectNode input=plan();
            input.withArray("cases").removeAll().add(mode.equals("SINGLE")
                    ? singleCase("mainbz","fina_mainbz",Map.of("ts_code","000001.SZ"))
                    : rangeCase("mainbz","fina_mainbz","REPORT_PERIOD","20250630","20251231"));
            JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
                assertThat(p.keySet()).containsExactlyInAnyOrderElementsOf(mode.equals("SINGLE")
                        ? Set.of("ts_code") : Set.of("ts_code","start_date","end_date"));
                c.beforeRequest();
                return envelope("fina_mainbz",p,List.of(
                        mainbz("20250630","private-item","CNY","P"),
                        mainbz("20250630","private-item","CNY","D"),
                        mainbz("20250630","private-item","CNY","P"),
                        mainbz("20250630","private-item","USD","I"),
                        mainbz("20251231","other-private-item","CNY",null),
                        mainbz("20251231",null,"CNY","token=synthetic-do-not-echo")));
            },CLOCK).path("cases").get(0);
            assertThat(item.path("status").asText()).isEqualTo("PASS");
            assertThat(item.path("sourceRowCount").asInt()).isEqualTo(6);
            assertThat(item.path("reviewMethod").asText()).contains(
                    "mainbzTypePRows=2", "mainbzTypeDRows=1", "mainbzTypeIRows=1", "mainbzTypeUnavailableRows=2",
                    "mainbzReportDates=20250630,20251231", "mainbzReportDateUnavailableRows=0",
                    "mainbzBusinessKeyValidRows=5", "mainbzBusinessKeyUnavailableRows=1",
                    "mainbzDistinctBusinessKeyCount=3", "mainbzCrossTypeBusinessKeyCount=1");
            assertThat(item.toString()).doesNotContain("private-item", "CNY", "USD", "synthetic-do-not-echo");
        }
    }

    @Test void mainbzRangeRecursivelySplitsDepthFirstAndProjectsOnlySuccessfulLeaves() {
        ObjectNode input=plan();input.withArray("cases").removeAll()
                .add(rangeCase("mainbz","fina_mainbz","REPORT_PERIOD","20260101","20260108"));
        var requested=new ArrayList<String>();
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();
            String range=p.get("start_date")+"-"+p.get("end_date");requested.add(range);
            if(Set.of("20260101-20260108","20260101-20260104","20260105-20260108").contains(range))
                return envelope("fina_mainbz",p,Collections.nCopies(100,mainbz((String)p.get("start_date"),"parent","CNY","I")));
            List<List<Object>> rows=switch(range) {
                case "20260101-20260102" -> List.of(mainbz("20260101","leaf-a","CNY","P"));
                case "20260103-20260104" -> List.of();
                case "20260105-20260106" -> List.of(mainbz("20260105","leaf-b","CNY","D"),mainbz("20260106","leaf-c","CNY","I"));
                case "20260107-20260108" -> List.of(mainbz("20260107","leaf-d","CNY","P"),mainbz("20260108","leaf-e","CNY","P"),mainbz("20260108","leaf-f","CNY","P"));
                default -> throw new AssertionError(range);
            };
            return envelope("fina_mainbz",p,rows);
        },CLOCK).path("cases").get(0);

        assertThat(requested).containsExactly("20260101-20260108","20260101-20260104",
                "20260101-20260102","20260103-20260104","20260105-20260108",
                "20260105-20260106","20260107-20260108");
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("requestCount").asInt()).isEqualTo(7);
        assertThat(item.path("leafCount").asInt()).isEqualTo(4);
        assertThat(item.path("succeededLeafCount").asInt()).isEqualTo(4);
        assertThat(item.path("emptyLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(6);
        JsonNode nodes=item.path("batchNodes");
        assertThat(nodes).hasSize(7);
        assertThat(nodes.get(0).path("status").asText()).isEqualTo("SPLIT");
        assertThat(nodes.get(1).path("status").asText()).isEqualTo("SPLIT");
        assertThat(nodes.get(2).path("status").asText()).isEqualTo("SPLIT");
        assertThat(nodes.get(1).path("parentBatchId").asText()).isEqualTo("source-0");
        assertThat(nodes.get(2).path("parentBatchId").asText()).isEqualTo("source-0");
        assertThat(nodes.get(3).path("parentBatchId").asText()).isEqualTo("source-1");
        assertThat(nodes.get(4).path("parentBatchId").asText()).isEqualTo("source-1");
        assertThat(nodes.get(5).path("parentBatchId").asText()).isEqualTo("source-2");
        assertThat(nodes.get(6).path("parentBatchId").asText()).isEqualTo("source-2");
        assertThat(nodes.get(0).path("sourceRowCount").isNull()).isTrue();
        assertThat(nodes.get(1).path("sourceRowCount").isNull()).isTrue();
        assertThat(nodes.get(2).path("sourceRowCount").isNull()).isTrue();
        assertThat(item.path("reviewMethod").asText()).contains(
                "candidateRowLimit=100", "candidateLimitReached=true", "splitParentCount=3",
                "unresolvedFullLeafCount=0", "mainbzTypePRows=4", "mainbzTypeDRows=1",
                "mainbzTypeIRows=1", "mainbzReportDates=20260101,20260105,20260106,20260107,20260108")
                .doesNotContain("parent");
    }

    @Test void mainbzSingleDayAtLimitFailsCompletenessWithoutProjectingTheResponse() {
        ObjectNode input=plan();input.withArray("cases").removeAll()
                .add(rangeCase("mainbz","fina_mainbz","REPORT_PERIOD","20260101","20260101"));
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();return envelope("fina_mainbz",p,Collections.nCopies(100,mainbz("20260101","full","CNY","P")));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("errorCode").asText()).isEqualTo("BATCH_COMPLETENESS_UNCONFIRMED");
        assertThat(item.path("requestCount").asInt()).isEqualTo(1);
        assertThat(item.path("leafCount").asInt()).isEqualTo(1);
        assertThat(item.path("failedLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("sourceRowCount").asInt()).isZero();
        assertThat(item.path("reviewMethod").asText()).contains("unresolvedFullLeafCount=1").doesNotContain("mainbzType");
    }

    @Test void mainbzSplitChildRejectsBadEnvelopeAndOutOfRangeRowsWithSiblingNotRun() {
        for(String failure:List.of("bad-envelope","out-of-range")) {
            ObjectNode input=plan();input.withArray("cases").removeAll()
                    .add(rangeCase("mainbz","fina_mainbz","REPORT_PERIOD","20260101","20260104"));
            AtomicInteger calls=new AtomicInteger();
            JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
                c.beforeRequest();
                if(calls.incrementAndGet()==1)
                    return envelope("fina_mainbz",p,Collections.nCopies(100,mainbz("20260101","parent","CNY","P")));
                return failure.equals("bad-envelope") ? null
                        : envelope("fina_mainbz",p,List.of(mainbz("20260103","outside","CNY","D")));
            },CLOCK).path("cases").get(0);
            assertThat(item.path("status").asText()).as(failure).isEqualTo("FAILED");
            assertThat(item.path("errorCode").asText()).isEqualTo(
                    failure.equals("bad-envelope")?"SOURCE_PAYLOAD_INVALID":"SOURCE_RANGE_MISMATCH");
            assertThat(item.path("requestCount").asInt()).isEqualTo(2);
            assertThat(item.path("leafCount").asInt()).isEqualTo(2);
            assertThat(item.path("failedLeafCount").asInt()).isEqualTo(1);
            assertThat(item.path("sourceRowCount").asInt()).isZero();
            assertThat(item.path("batchNodes").get(0).path("status").asText()).isEqualTo("SPLIT");
            assertThat(item.path("batchNodes").get(1).path("status").asText()).isEqualTo("FAILED");
            assertThat(item.path("batchNodes").get(2).path("status").asText()).isEqualTo("NOT_RUN");
            assertThat(item.path("reviewMethod").asText()).contains("splitParentCount=1", "unresolvedFullLeafCount=0")
                    .doesNotContain("mainbzType");
        }
    }

    @Test void mainbzPartialFailureKeepsSuccessfulLeafProjectionAndCompleteChildFacts() {
        ObjectNode input=plan();input.withArray("cases").removeAll()
                .add(rangeCase("mainbz","fina_mainbz","REPORT_PERIOD","20260101","20260104"));
        AtomicInteger calls=new AtomicInteger();
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();int call=calls.incrementAndGet();
            if(call==1) return envelope("fina_mainbz",p,Collections.nCopies(100,mainbz("20260101","parent","CNY","I")));
            if(call==2) return envelope("fina_mainbz",p,List.of(mainbz("20260101","leaf","CNY","D")));
            throw new SourceException(ErrorCode.SOURCE_NETWORK_ERROR,"private-response");
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("errorCode").asText()).isEqualTo("SOURCE_NETWORK_ERROR");
        assertThat(item.path("requestCount").asInt()).isEqualTo(3);
        assertThat(item.path("leafCount").asInt()).isEqualTo(2);
        assertThat(item.path("succeededLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("failedLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(1);
        assertThat(item.path("batchNodes").get(1).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(item.path("batchNodes").get(2).path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("reviewMethod").asText()).contains("mainbzTypeDRows=1", "mainbzTypeIRows=0")
                .doesNotContain("private-response", "parent");
    }

    @Test void mainbzInterruptedSplitKeepsBothChildrenNotRunAndActualParentRequestCount() {
        for(String interruption:List.of("stop","deadline")) {
            ObjectNode input=plan();input.withArray("cases").removeAll()
                    .add(rangeCase("mainbz","fina_mainbz","REPORT_PERIOD","20260101","20260104"));
            Clock clock=interruption.equals("stop") ? CLOCK : new Clock() {
                int calls;
                public ZoneId getZone(){return ZoneOffset.UTC;}
                public Clock withZone(ZoneId zone){return this;}
                public Instant instant(){return CLOCK.instant().plusSeconds(calls++ < 4 ? 0 : 1800);}
            };
            JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
                c.beforeRequest();
                if(interruption.equals("stop")) ((TushareRangeSourceProbe.Context)c).stop();
                return envelope("fina_mainbz",p,Collections.nCopies(100,mainbz("20260101","parent","CNY","P")));
            },clock).path("cases").get(0);
            assertThat(item.path("status").asText()).as(interruption).isEqualTo("FAILED");
            assertThat(item.path("errorCode").asText()).isEqualTo(
                    interruption.equals("stop")?"EXECUTION_INTERRUPTED":"TASK_LIMIT_EXCEEDED");
            assertThat(item.path("requestCount").asInt()).isEqualTo(1);
            assertThat(item.path("leafCount").asInt()).isEqualTo(2);
            assertThat(item.path("batchNodes").get(0).path("status").asText()).isEqualTo("SPLIT");
            assertThat(item.path("batchNodes").get(1).path("status").asText()).isEqualTo("NOT_RUN");
            assertThat(item.path("batchNodes").get(2).path("status").asText()).isEqualTo("NOT_RUN");
        }
    }

    @Test void mainbzRequestBudgetFailureKeepsAttemptedChildAndNotRunSiblingFacts() {
        ObjectNode input=plan();input.withArray("cases").removeAll()
                .add(rangeCase("mainbz","fina_mainbz","REPORT_PERIOD","20260101","20260104"));
        var context=new TushareRangeSourceProbe.Context(CLOCK);
        for(int request=0;request<4999;request++) context.beforeRequest();
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();return envelope("fina_mainbz",p,Collections.nCopies(100,mainbz("20260101","parent","CNY","P")));
        },context).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("errorCode").asText()).isEqualTo("TASK_LIMIT_EXCEEDED");
        assertThat(item.path("requestCount").asInt()).isEqualTo(1);
        assertThat(item.path("leafCount").asInt()).isEqualTo(2);
        assertThat(item.path("failedLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("batchNodes").get(0).path("status").asText()).isEqualTo("SPLIT");
        assertThat(item.path("batchNodes").get(1).path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("batchNodes").get(2).path("status").asText()).isEqualTo("NOT_RUN");
    }

    @Test void rowLimitSplitRemainsIsolatedFromMainbzSingleAndOtherRangeApis() {
        ObjectNode input=plan();input.withArray("cases").removeAll()
                .add(singleCase("single","fina_mainbz",Map.of("ts_code","000001.SZ")))
                .add(rangeCase("other","fina_indicator","REPORT_PERIOD","20260101","20260104"));
        JsonNode cases=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();String api=d.datasetKey().apiName().value();
            return api.equals("fina_mainbz")
                    ? envelope(api,p,Collections.nCopies(100,mainbz("20260101","single","CNY","P")))
                    : envelope(api,p,Collections.nCopies(100,row(api,"20260101")));
        },CLOCK).path("cases");
        assertThat(cases.get(0).path("status").asText()).isEqualTo("PASS");
        assertThat(cases.get(1).path("status").asText()).isEqualTo("EVIDENCE_MISSING");
        for(JsonNode item:cases) {
            assertThat(item.path("requestCount").asInt()).isEqualTo(1);
            assertThat(item.path("batchNodes")).hasSize(1);
            assertThat(item.path("batchNodes").get(0).path("status").asText()).isEqualTo("SUCCEEDED");
        }
    }

    @Test void mainbzInvalidSingleReportDatesAndKeysRemainUnavailable() {
        ObjectNode input=plan();input.withArray("cases").removeAll()
                .add(singleCase("mainbz","fina_mainbz",Map.of("ts_code","000001.SZ")));
        JsonNode item=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();return envelope("fina_mainbz",p,List.of(
                    mainbz("20260230","private-item","CNY","p"),
                    mainbz(null,"private-item","CNY"," P")));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("reviewMethod").asText()).contains("mainbzTypePRows=0", "mainbzTypeUnavailableRows=2",
                "mainbzReportDates=;", "mainbzReportDateUnavailableRows=2",
                "mainbzBusinessKeyValidRows=0", "mainbzBusinessKeyUnavailableRows=2", "mainbzDistinctBusinessKeyCount=0");
    }

    @Test void mainbzProjectionDistinguishesEmptyFailedAndUnexecutedResponses() {
        ObjectNode input=plan();input.withArray("cases").removeAll();
        for(String id:List.of("empty","failed","not-run")) input.withArray("cases")
                .add(singleCase(id,"fina_mainbz",Map.of("ts_code","000001.SZ")));
        AtomicInteger calls=new AtomicInteger();
        JsonNode cases=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();if(calls.incrementAndGet()==2) throw new IllegalStateException("private failure");
            return envelope("fina_mainbz",p,List.of());
        },CLOCK).path("cases");
        assertThat(cases.get(0).path("status").asText()).isEqualTo("EVIDENCE_MISSING");
        assertThat(cases.get(0).path("reviewMethod").asText()).contains("mainbzTypePRows=0", "mainbzDistinctBusinessKeyCount=0");
        assertThat(cases.get(1).path("status").asText()).isEqualTo("FAILED");
        assertThat(cases.get(1).path("reviewMethod").asText()).doesNotContain("mainbzType", "mainbzBusinessKey");
        assertThat(cases.get(2).path("status").asText()).isEqualTo("NOT_RUN");
        assertThat(cases.get(2).path("reviewMethod").isNull()).isTrue();
    }

    private static List<Object> mainbz(String date,String item,String currency,String type) {
        return with("fina_mainbz",with("fina_mainbz",with("fina_mainbz",row("fina_mainbz",date),
                "bz_item",item),"curr_type",currency),"bz_code",type);
    }

    @Test void eventRangeRequestsAuxiliaryColumnsAtTheTransportBoundaryOnly() {
        for (String api : List.of("stk_holdertrade","disclosure_date")) {
            ObjectNode input=eventPlan(api,"20260803","20260803");
            input.withArray("cases").add(singleCase("single",api,Map.of("ts_code","000001.SZ","ann_date","20260803")));
            AtomicInteger calls=new AtomicInteger();
            JsonNode cases=TushareRangeSourceProbe.run(input,(d,p,c)->{
                c.beforeRequest();
                boolean ranged=calls.incrementAndGet()==1;
                var wanted=new ArrayList<>(definition(api).columns().stream().map(ColumnDefinition::name).toList());
                if(ranged) wanted.addAll(api.equals("stk_holdertrade") ? List.of("begin_date","close_date") : List.of("modify_date"));
                assertThat(d.columns().stream().map(ColumnDefinition::name)).containsExactlyElementsOf(wanted);
                assertThat(d.businessKey()).isEqualTo(definition(api).businessKey());
                assertThat(p).isEqualTo(api.equals("disclosure_date") || !ranged
                        ? Map.of("ts_code","000001.SZ","ann_date","20260803")
                        : Map.of("ts_code","000001.SZ","start_date","20260803","end_date","20260803"));
                return ranged ? eventEnvelope(api,p,List.of()) : envelope(api,p,List.of());
            },CLOCK).path("cases");
            assertThat(calls.get()).isEqualTo(2);
            assertThat(cases.get(1).path("reviewMethod").asText()).doesNotContain("holderBegin", "disclosureReport", "disclosureModify");
        }
    }

    @Test void blockTradesKeepDuplicateRowsButNormalizeDecimalKeysAndDistinguishParties() {
        JsonNode item=TushareRangeSourceProbe.run(eventPlan("block_trade","20260803","20260804"),(d,p,c)->{
            c.beforeRequest();return envelope("block_trade",p,List.of(
                    blockTrade("20260803","private-buyer-A","private-seller-A","12.00",100),
                    blockTrade("20260803","private-buyer-A","private-seller-A",12,"100.0"),
                    blockTrade("20260803","private-buyer-B","private-seller-A",12,100),
                    blockTrade("20260803","private-buyer-A","private-seller-B",12,100),
                    blockTrade("20260804","private-buyer-A","private-seller-A",12,100),
                    blockTrade("20260803",null,"private-seller-A",12,100),
                    blockTrade("20260803","private-buyer-A","private-seller-A","NaN",100)));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(7);
        assertThat(item.path("reviewMethod").asText()).contains("blockTradeBusinessKeyValidRows=5",
                "blockTradeBusinessKeyUnavailableRows=2", "blockTradeDistinctBusinessKeyCount=4",
                "blockTradeSameStockDateMultipleKeyGroupCount=1", "blockTradeDistinctBuyerSellerPairCount=3");
        assertThat(item.toString()).doesNotContain("private-buyer", "private-seller", "987654321", "NaN");
    }

    @Test void holderTradeComparesBothBusinessDatesOnTheSameRows() {
        JsonNode item=TushareRangeSourceProbe.run(eventPlan("stk_holdertrade","20260803","20260804"),(d,p,c)->{
            c.beforeRequest();return eventEnvelope("stk_holdertrade",p,List.of(
                    holderTrade("20260803","20260731","20260804"),
                    holderTrade("20260803","20260804","20260803"),
                    holderTrade("20260804","20260804",null),
                    holderTrade("20260804",null,"20260230"),
                    holderTrade("20260804","20260230","20260805")));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("reviewMethod").asText()).contains(
                "holderBeginDateComparisonValidRows=3", "holderBeginDateComparisonUnavailableRows=2",
                "holderAnnDateDifferentFromBeginDateRows=2", "holderAnnInRangeBeginOutsideRows=1",
                "holderCloseDateComparisonValidRows=3", "holderCloseDateComparisonUnavailableRows=2",
                "holderAnnDateDifferentFromCloseDateRows=2", "holderAnnInRangeCloseOutsideRows=1");
        assertThat(item.toString()).doesNotContain("private-holder", "20260731", "20260230", "20260805");
    }

    @Test void pledgeComparesEachBusinessDateAndSingleOnlyExposesValidAnnouncementDates() {
        ObjectNode input=eventPlan("pledge_detail","20260803","20260804");
        input.withArray("cases").add(singleCase("single","pledge_detail",Map.of("ts_code","000001.SZ")));
        AtomicInteger calls=new AtomicInteger();
        JsonNode cases=TushareRangeSourceProbe.run(input,(d,p,c)->{
            c.beforeRequest();
            return envelope("pledge_detail",p,calls.incrementAndGet()==1 ? List.of(
                    pledge("20260803","20260731","20260804","20260803"),
                    pledge("20260804","20260804",null,"20260805"),
                    pledge("20260804","20260230","20260230",null)) : List.of(
                    pledge("20241231",null,null,null), pledge("20241231",null,null,null),
                    pledge("20250131",null,null,null), pledge("20260230",null,null,null), pledge(null,null,null,null)));
        },CLOCK).path("cases");
        assertThat(cases.get(0).path("status").asText()).isEqualTo("PASS");
        assertThat(cases.get(0).path("reviewMethod").asText()).contains(
                "pledgeStartDateComparisonValidRows=2", "pledgeStartDateComparisonUnavailableRows=1",
                "pledgeAnnDateDifferentFromStartDateRows=1", "pledgeAnnInRangeStartOutsideRows=1",
                "pledgeEndDateComparisonValidRows=1", "pledgeEndDateComparisonUnavailableRows=2",
                "pledgeAnnDateDifferentFromEndDateRows=1", "pledgeAnnInRangeEndOutsideRows=0",
                "pledgeReleaseDateComparisonValidRows=2", "pledgeReleaseDateComparisonUnavailableRows=1",
                "pledgeAnnDateDifferentFromReleaseDateRows=1", "pledgeAnnInRangeReleaseOutsideRows=1");
        assertThat(cases.get(1).path("status").asText()).isEqualTo("PASS");
        assertThat(cases.get(1).path("reviewMethod").asText()).contains("pledgeAnnouncementDates=20241231,20250131")
                .doesNotContain("DateComparison", "DifferentFrom", "InRange", "20260230");
        assertThat(cases.toString()).doesNotContain("private-holder", "private-pledgor", "987654321");
    }

    @Test void disclosureKeepsNaturalDayLeavesAndFindsSameKeyDifferentRecordsWithoutClaimingUpdates() {
        JsonNode item=TushareRangeSourceProbe.run(eventPlan("disclosure_date","20260803","20260804"),(d,p,c)->{
            c.beforeRequest();String ann=(String)p.get("ann_date");
            return eventEnvelope("disclosure_date",p,ann.equals("20260803") ? List.of(
                    disclosure(ann,"20251231","20260803","20260804","20260731"),
                    disclosure(ann,"20251231","20260803","20260804","private-modification"),
                    disclosure(ann,"20260803",null,"20260230",null)) : List.of(
                    disclosure(ann,"20251231","20260804","20260804",""),
                    disclosure(ann,"20260230","20260804",null,"20260230")));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(5);
        assertThat(item.path("succeededLeafCount").asInt()).isEqualTo(2);
        assertThat(item.path("batchNodes").get(0).path("start").asText()).isEqualTo("20260803");
        assertThat(item.path("batchNodes").get(1).path("start").asText()).isEqualTo("20260804");
        assertThat(item.path("reviewMethod").asText()).contains(
                "disclosureReportDates=20251231,20260803", "disclosureReportDateUnavailableRows=1",
                "disclosureBusinessKeyValidRows=4", "disclosureBusinessKeyUnavailableRows=1",
                "disclosureDistinctBusinessKeyCount=2", "disclosureSameKeyDifferentRecordCount=1",
                "disclosureAnnEndDateComparisonValidRows=4", "disclosureAnnEndDateComparisonUnavailableRows=1",
                "disclosureAnnDateDifferentFromEndDateRows=3",
                "disclosurePreActualDateComparisonValidRows=3", "disclosurePreActualDateComparisonUnavailableRows=2",
                "disclosurePreDateDifferentFromActualDateRows=2",
                "disclosureModifyDatePresentRows=3", "disclosureModifyDateParseableRows=1");
        assertThat(metric(item,"disclosureBusinessKeyDigestSha256")).matches("[a-f0-9]{64}");
        assertThat(metric(item,"disclosureRecordDigestSha256")).matches("[a-f0-9]{64}");
        assertThat(item.path("updatedRows").isNull()).isTrue();
        assertThat(item.toString()).doesNotContain("private-modification", "20260731", "20260230");
    }

    @Test void disclosureDigestsIgnoreOrderAndAuxiliaryCorrectionButDetectOriginalRecordChange() {
        var records=List.of(disclosure("20260803","20251231","20260803",null,"20260731"),
                disclosure("20260803","20260630","20260804",null,null));
        JsonNode initial=disclosureSample(records);
        JsonNode reordered=disclosureSample(List.of(records.get(1),records.get(1),disclosure("20260803","20251231","20260803",null,"private-correction")));
        JsonNode revised=disclosureSample(List.of(records.get(1),disclosure("20260803","20251231","20260805",null,null)));
        for(String name:List.of("disclosureBusinessKeyDigestSha256","disclosureRecordDigestSha256")) {
            assertThat(metric(initial,name)).matches("[a-f0-9]{64}").isEqualTo(metric(reordered,name));
        }
        assertThat(metric(initial,"disclosureBusinessKeyDigestSha256")).isEqualTo(metric(revised,"disclosureBusinessKeyDigestSha256"));
        assertThat(metric(initial,"disclosureRecordDigestSha256")).isNotEqualTo(metric(revised,"disclosureRecordDigestSha256"));
        assertThat(reordered.path("sourceRowCount").asInt()).isEqualTo(3);
    }

    @Test void disclosureCorrectionDatesRequireEveryDelimitedValueToBeARealDate() {
        var rows=new ArrayList<List<Object>>();
        for(Object modify:Arrays.asList(null,"","20260731","2026-07-31,2026-08-01",
                "20260731,2026-07-31","20260230","private text 20260731"))
            rows.add(disclosure("20260803","20251231",null,null,modify));
        JsonNode item=disclosureSample(rows);
        assertThat(item.path("status").asText()).isEqualTo("PASS");
        assertThat(item.path("reviewMethod").asText()).contains(
                "disclosureModifyDatePresentRows=5", "disclosureModifyDateParseableRows=3",
                "disclosureModifyDateUnavailableRows=4", "disclosureModifyDateMultipleDatesRows=1");
        assertThat(item.toString()).doesNotContain("private text", "20260731", "2026-07-31", "20260230");
    }

    @Test void extendedEventEnvelopeRejectsMissingOrReorderedColumnsAndFailureStatus() {
        for(String api:List.of("stk_holdertrade","disclosure_date")) {
            for(String bad:List.of("missing","reordered","failed")) {
                JsonNode item=TushareRangeSourceProbe.run(eventPlan(api,"20260803","20260803"),(d,p,c)->{
                    c.beforeRequest();
                    if(bad.equals("missing")) return envelope(api,p,List.of());
                    DownloadEnvelope good=eventEnvelope(api,p,List.of());
                    var fields=new ArrayList<>(good.fields());Collections.swap(fields,0,fields.size()-1);
                    return new DownloadEnvelope(good.pluginId(),good.apiName(),p,bad.equals("failed")?List.of():fields,0,List.of(),
                            bad.equals("failed")?DownloadStatus.FAILURE:DownloadStatus.SUCCESS,bad.equals("failed")?"private error":null);
                },CLOCK).path("cases").get(0);
                assertThat(item.path("status").asText()).as(api+bad).isEqualTo("FAILED");
                assertThat(item.path("errorCode").asText()).isEqualTo("SOURCE_PAYLOAD_INVALID");
                assertThat(item.path("reviewMethod").asText()).doesNotContain("holderBegin", "disclosureReport");
            }
        }
    }

    @Test void eventProjectionNeverRescuesInvalidPrimaryDatesOrForeignStocks() {
        for(String api:List.of("block_trade","stk_holdertrade","pledge_detail","disclosure_date")) {
            for(String invalid:List.of("20260805","20260230","foreign-stock")) {
                JsonNode item=TushareRangeSourceProbe.run(eventPlan(api,"20260803","20260803"),(d,p,c)->{
                    c.beforeRequest();String date=invalid.equals("foreign-stock")?"20260803":invalid;
                    List<Object> values=switch(api) {
                        case "block_trade"->blockTrade(date,"private-buyer","private-seller",12,100);
                        case "stk_holdertrade"->holderTrade(date,"20260803","20260803");
                        case "pledge_detail"->pledge(date,"20260803","20260803","20260803");
                        default->disclosure(date,"20251231","20260803","20260803",null);
                    };
                    if(invalid.equals("foreign-stock")) values.set(0,"600000.SH");
                    return eventEnvelope(api,p,List.of(values));
                },CLOCK).path("cases").get(0);
                assertThat(item.path("status").asText()).as(api+invalid).isEqualTo("FAILED");
                assertThat(item.path("reviewMethod").asText()).doesNotContain("BusinessKeyValidRows", "DateComparisonValidRows");
            }
        }
    }

    @Test void eventProjectionDistinguishesSuccessfulEmptyFailedAndUnexecutedResponses() {
        for(String api:List.of("block_trade","stk_holdertrade","pledge_detail","disclosure_date")) {
            ObjectNode input=eventPlan(api,"20260803","20260803");
            input.withArray("cases").add(first(input).deepCopy().put("caseId","failed"))
                    .add(first(input).deepCopy().put("caseId","not-run"));
            AtomicInteger calls=new AtomicInteger();
            JsonNode cases=TushareRangeSourceProbe.run(input,(d,p,c)->{
                c.beforeRequest();if(calls.incrementAndGet()==2) throw new IllegalStateException("private error");
                return eventEnvelope(api,p,List.of());
            },CLOCK).path("cases");
            String metric=switch(api) {
                case "block_trade"->"blockTradeBusinessKeyValidRows";
                case "stk_holdertrade"->"holderBeginDateComparisonValidRows";
                case "pledge_detail"->"pledgeStartDateComparisonValidRows";
                default->"disclosureBusinessKeyValidRows";
            };
            assertThat(cases.get(0).path("status").asText()).isEqualTo("EVIDENCE_MISSING");
            assertThat(cases.get(0).path("reviewMethod").asText()).contains(metric+"=0");
            assertThat(cases.get(1).path("status").asText()).isEqualTo("FAILED");
            assertThat(cases.get(1).path("reviewMethod").asText()).doesNotContain(metric);
            assertThat(cases.get(2).path("status").asText()).isEqualTo("NOT_RUN");
            assertThat(cases.get(2).path("reviewMethod").isNull()).isTrue();
        }
    }

    @Test void disclosurePartialFailureKeepsOnlySuccessfulLeafProjection() {
        JsonNode item=TushareRangeSourceProbe.run(eventPlan("disclosure_date","20260803","20260804"),(d,p,c)->{
            c.beforeRequest();String ann=(String)p.get("ann_date");
            return eventEnvelope("disclosure_date",p,List.of(disclosure(ann.equals("20260803")?ann:"20260805",
                    ann.equals("20260803")?"20251231":"20260630","20260803",null,null)));
        },CLOCK).path("cases").get(0);
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("succeededLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("failedLeafCount").asInt()).isEqualTo(1);
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(1);
        assertThat(item.path("reviewMethod").asText()).contains("disclosureReportDates=20251231;", "disclosureBusinessKeyValidRows=1")
                .doesNotContain("20260630");
    }

    @Test void slbFinancingKeepsOriginalDateAndBalanceKeyWithoutInventingTenor() {
        var first=with("slb_len",row("slb_len","20260803"),"ob","123456.50");
        var second=with("slb_len",first,"ob","987654.25");
        JsonNode item=slbSample("slb_len",List.of(first,second,with("slb_len",first,"ob",123456.5)));
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(3);
        assertThat(item.path("reviewMethod").asText()).contains("slbBusinessKeyValidRows=3",
                "slbBusinessKeyUnavailableRows=0", "slbDistinctBusinessKeyCount=2",
                "slbDuplicateBusinessKeyRows=1", "slbSameDateMultipleKeyGroupCount=1")
                .doesNotContain("Tenor", "FeeRate", "123456", "987654");
        assertThat(metric(item,"slbBusinessKeyDigestSha256")).matches("[a-f0-9]{64}")
                .isEqualTo(metric(slbSample("slb_len",List.of(second,first)),"slbBusinessKeyDigestSha256"));
    }

    @Test void slbSummaryUsesStockDateKeyAndDoesNotInferDetailDimensions() {
        var row=row("slb_sec","20260803");
        JsonNode item=slbSample("slb_sec",List.of(row,with("slb_sec",row,"lent_qnt",987654321)));
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(2);
        assertThat(item.path("reviewMethod").asText()).contains("slbDistinctBusinessKeyCount=1",
                "slbDuplicateBusinessKeyRows=1", "slbSameDateMultipleKeyGroupCount=0").doesNotContain("Tenor", "FeeRate", "987654321");
    }

    @Test void slbDetailPreservesDifferentTenorsAndRatesAndNormalizesEquivalentNumericKeys() {
        var first=slbDetail(14,"2.20");
        JsonNode item=slbSample("slb_sec_detail",List.of(first,slbDetail("14.0",2.2),slbDetail(28,2.2),slbDetail(14,3.3)));
        assertThat(item.path("sourceRowCount").asInt()).isEqualTo(4);
        assertThat(item.path("reviewMethod").asText()).contains("slbBusinessKeyValidRows=4",
                "slbDistinctBusinessKeyCount=3", "slbDuplicateBusinessKeyRows=1",
                "slbSameDateMultipleKeyGroupCount=1", "slbDistinctTenorCount=2", "slbDistinctFeeRateCount=2",
                "slbDistinctTenorFeeRatePairCount=3", "slbSameStockDateMultipleTenorGroupCount=1",
                "slbSameStockDateTenorMultipleFeeRateGroupCount=1");
        assertThat(item.toString()).doesNotContain("private-security", "987654321", "2.20");
        assertThat(metric(item,"slbBusinessKeyDigestSha256")).isEqualTo(metric(
                slbSample("slb_sec_detail",List.of(slbDetail(14,3.3),slbDetail(28,2.2),first)),"slbBusinessKeyDigestSha256"));
    }

    @Test void slbInvalidNumericKeysAreCountedUnavailableWithoutLeakingValues() {
        var rows=new ArrayList<List<Object>>();
        for(Object tenor:Arrays.asList(null,"",0,-1,"14.5","private-tenor")) rows.add(slbDetail(tenor,2.2));
        for(Object rate:Arrays.asList(null,"private-rate",Double.NaN,Double.POSITIVE_INFINITY,"100E+2147483647")) rows.add(slbDetail(14,rate));
        rows.add(slbDetail(14,2.2));
        JsonNode item=slbSample("slb_sec_detail",rows);
        assertThat(item.path("reviewMethod").asText()).contains("slbBusinessKeyValidRows=1",
                "slbBusinessKeyUnavailableRows=11", "slbDistinctBusinessKeyCount=1", "slbDistinctTenorCount=1");
        assertThat(item.toString()).doesNotContain("private-tenor", "private-rate", "NaN", "Infinity");
        item=slbSample("slb_len",List.of(with("slb_len",row("slb_len","20260803"),"ob",null)));
        assertThat(item.path("reviewMethod").asText()).contains("slbBusinessKeyValidRows=0", "slbBusinessKeyUnavailableRows=1");
    }

    @Test void slbProjectionDistinguishesEmptyFailedUnexecutedAndSingle() {
        for(String api:List.of("slb_len","slb_sec","slb_sec_detail")) {
            ObjectNode input=slbPlan(api);
            input.withArray("cases").add(first(input).deepCopy().put("caseId","failed"))
                    .add(first(input).deepCopy().put("caseId","not-run"));
            AtomicInteger calls=new AtomicInteger();
            JsonNode cases=TushareRangeSourceProbe.run(input,(d,p,c)->{
                c.beforeRequest();if(calls.incrementAndGet()==2) throw new IllegalStateException("private error");
                return envelope(api,p,List.of());
            },CLOCK).path("cases");
            assertThat(cases.get(0).path("status").asText()).isEqualTo("EVIDENCE_MISSING");
            assertThat(cases.get(0).path("reviewMethod").asText()).contains("slbBusinessKeyValidRows=0");
            assertThat(cases.get(1).path("status").asText()).isEqualTo("FAILED");
            assertThat(cases.get(1).path("reviewMethod").asText()).doesNotContain("slbBusinessKey");
            assertThat(cases.get(2).path("status").asText()).isEqualTo("NOT_RUN");
            var params=new HashMap<String,Object>(Map.of("trade_date","20260803"));
            if(!api.equals("slb_len")) params.put("ts_code","000001.SZ");
            input.withArray("cases").removeAll().add(singleCase("single",api,params));
            JsonNode single=TushareRangeSourceProbe.run(input,(d,p,c)->{
                c.beforeRequest();return envelope(api,p,List.of());
            },CLOCK).path("cases").get(0);
            assertThat(single.path("reviewMethod").asText()).doesNotContain("slbBusinessKey");
        }
    }

    @Test void slbProjectionDoesNotRescueWrongDateOrForeignStock() {
        for(String api:List.of("slb_len","slb_sec","slb_sec_detail")) {
            JsonNode item=slbSample(api,List.of(row(api,"20260805")));
            assertThat(item.path("status").asText()).isEqualTo("FAILED");
            assertThat(item.path("reviewMethod").asText()).doesNotContain("slbBusinessKey");
            if(api.equals("slb_len")) continue;
            item=slbSample(api,List.of(with(api,row(api,"20260803"),"ts_code","600000.SH")));
            assertThat(item.path("status").asText()).isEqualTo("FAILED");
            assertThat(item.path("reviewMethod").asText()).doesNotContain("slbBusinessKey");
        }
    }

    private static ObjectNode slbPlan(String api) {
        ObjectNode input=plan();input.withArray("cases").removeAll().add(rangeCase("slb",api,"TRADE_DATE","20260803","20260804"));
        if(api.equals("slb_len")) first(input).withObject("params").remove("ts_code");
        return input;
    }

    private static JsonNode slbSample(String api,List<List<Object>> rows) {
        return TushareRangeSourceProbe.run(slbPlan(api),(d,p,c)->{
            c.beforeRequest();assertThat(d.columns().stream().map(ColumnDefinition::name).toList()).isEqualTo(envelope(api,p,List.of()).fields());
            return envelope(api,p,rows);
        },CLOCK).path("cases").get(0);
    }

    private static List<Object> slbDetail(Object tenor,Object rate) {
        return Arrays.asList("20260803","000001.SZ","private-security",tenor,rate,987654321);
    }

    private static ObjectNode eventPlan(String api,String start,String end) {
        ObjectNode input=plan();input.withArray("cases").removeAll().add(rangeCase("event",api,
                api.equals("block_trade")?"TRADE_DATE":"ANNOUNCEMENT_DATE",start,end));return input;
    }

    private static DownloadEnvelope eventEnvelope(String api,Map<String,Object> params,List<List<Object>> rows) {
        DownloadEnvelope original=envelope(api,params,List.of());
        var fields=new ArrayList<>(original.fields());
        if(api.equals("stk_holdertrade")) fields.addAll(List.of("begin_date","close_date"));
        if(api.equals("disclosure_date")) fields.add("modify_date");
        return new DownloadEnvelope(original.pluginId(),original.apiName(),params,fields,rows.size(),rows,DownloadStatus.SUCCESS,null);
    }

    private static List<Object> blockTrade(String ann,Object buyer,Object seller,Object price,Object volume) {
        return new ArrayList<>(Arrays.asList("000001.SZ",ann,price,volume,987654321,buyer,seller));
    }

    private static List<Object> holderTrade(String ann,Object begin,Object close) {
        var values=new ArrayList<>(with("stk_holdertrade",row("stk_holdertrade",ann),"holder_name","private-holder"));
        values.add(begin);values.add(close);return values;
    }

    private static List<Object> pledge(String ann,Object start,Object end,Object release) {
        var values=row("pledge_detail",ann);
        for(var entry:Map.of("holder_name","private-holder","pledgor","private-pledgor","pledge_amount",987654321).entrySet())
            values=with("pledge_detail",values,entry.getKey(),entry.getValue());
        values=with("pledge_detail",values,"start_date",start);
        values=with("pledge_detail",values,"end_date",end);
        return with("pledge_detail",values,"release_date",release);
    }

    private static List<Object> disclosure(String ann,Object end,Object pre,Object actual,Object modify) {
        return new ArrayList<>(Arrays.asList("000001.SZ",ann,end,pre,actual,modify));
    }

    private static JsonNode disclosureSample(List<List<Object>> rows) {
        return TushareRangeSourceProbe.run(eventPlan("disclosure_date","20260803","20260803"),(d,p,c)->{
            c.beforeRequest();return eventEnvelope("disclosure_date",p,rows);
        },CLOCK).path("cases").get(0);
    }

    private static String metric(JsonNode item,String name) {
        return Arrays.stream(item.path("reviewMethod").asText().split("; ")).filter(v->v.startsWith(name+"="))
                .map(v->v.substring(name.length()+1)).findFirst().orElse("");
    }

    static ObjectNode plan() {
        ObjectNode plan=JSON.createObjectNode().put("runId","offline-run");
        plan.putArray("cases").add(rangeCase("daily-range","daily","TRADE_DATE","20260803","20260804")); return plan;
    }
    static ObjectNode first(ObjectNode plan){return (ObjectNode)plan.path("cases").get(0);}
    static ObjectNode rangeCase(String id,String api,String axis,String start,String end) {
        ObjectNode item=JSON.createObjectNode().put("caseId",id).put("apiName",api).put("mode","RANGE").put("dateAxis",axis).put("start",start).put("end",end);
        item.putObject("params").put("ts_code","000001.SZ").put("start_date",start).put("end_date",end); item.putArray("evidenceRefs").add("official-reference");return item;
    }
    static ObjectNode singleCase(String id,String api,Map<String,Object> params) {
        ObjectNode item=rangeCase(id,api,"unused","20260803","20260804"); item.put("mode","SINGLE").putNull("dateAxis").putNull("start").putNull("end"); item.set("params",JSON.valueToTree(params));return item;
    }
    static List<Object> calendarDay(String date,int open,String exchange){return List.of(exchange,date,open,"20260802");}
    Path privateDirectory() throws Exception {return Files.createDirectory(temporary.resolve("private"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();}
    static Path write(Path path,JsonNode value) throws Exception {Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));Files.writeString(path,value.toString());return path;}
    static void safeFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Tushare SOURCE probe configuration").hasNoCause();}
}
