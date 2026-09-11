package com.akkc.tensor.plugin.tushare.batch;

import static org.assertj.core.api.Assertions.*;
import static com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.*;
import static com.akkc.tensor.plugin.tushare.batch.TushareBatchPolicies.*;

import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.descriptor.*;
import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.client.RestClient;

class TushareBatchPoliciesTest {
    // Independent specification: API, shape, mode, axis, output, candidate limit, official document.
    static Stream<String> rows() { return """
        daily S N TRADE_DATE trade_date 6000 27
        weekly S N TRADE_DATE trade_date 6000 144
        monthly S N TRADE_DATE trade_date 4500 145
        adj_factor S N TRADE_DATE trade_date - 28
        daily_basic S N TRADE_DATE trade_date 6000 32
        stk_limit S N TRADE_DATE trade_date 5800 183
        suspend_d S N TRADE_DATE trade_date - 214
        moneyflow S N TRADE_DATE trade_date 6000 170
        margin I N TRADE_DATE trade_date 4000 58
        margin_detail S N TRADE_DATE trade_date 6000 59
        block_trade S N TRADE_DATE trade_date 1000 161
        slb_len D N TRADE_DATE trade_date 5000 331
        slb_sec S N TRADE_DATE trade_date 5000 332
        slb_sec_detail S N TRADE_DATE trade_date 5000 333
        trade_cal E N CALENDAR_DATE cal_date coverage 26
        new_share D N ISSUE_DATE ipo_date 2000 123
        income S N ANNOUNCEMENT_DATE ann_date - 33
        balancesheet S N ANNOUNCEMENT_DATE ann_date - 36
        cashflow S N ANNOUNCEMENT_DATE ann_date - 44
        fina_audit S N ANNOUNCEMENT_DATE ann_date - 80
        forecast S N ANNOUNCEMENT_DATE ann_date 3500 45
        express S N ANNOUNCEMENT_DATE ann_date - 46
        repurchase D N ANNOUNCEMENT_DATE ann_date - 124
        stk_managers S N ANNOUNCEMENT_DATE ann_date - 193
        stk_holdernumber S N ANNOUNCEMENT_DATE ann_date 3000 166
        stk_holdertrade S N ANNOUNCEMENT_DATE ann_date 3000 175
        pledge_detail S N ANNOUNCEMENT_DATE ann_date 1000 111
        fina_indicator S N REPORT_PERIOD end_date 100 79
        fina_mainbz S N REPORT_PERIOD end_date 100 81
        top10_holders S N REPORT_PERIOD end_date - 61
        top10_floatholders S N REPORT_PERIOD end_date - 62
        top_list S T TRADE_DATE trade_date 10000 106
        dividend S C ANNOUNCEMENT_DATE ann_date 2000 103
        disclosure_date S C ANNOUNCEMENT_DATE ann_date 6000 162
        """.strip().lines(); }
    static final List<String> SINGLE_ONLY = List.of("pledge_stat", "stk_rewards", "stock_basic", "stock_company", "index_classify", "index_member_all");
    static final List<DatasetDefinition> DEFINITIONS = new DatasetDefinitionLoader().loadAll(
            new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);
    static final DateRange RANGE = range("2024-02-28", "2024-03-01");
    static final TushareProperties PROPERTIES = new TushareProperties(true, URI.create("https://synthetic.invalid"),
            new TushareProperties.Credential("controlled-secret"), Duration.ofSeconds(1), Duration.ofSeconds(2), 4_000_000, Duration.ZERO);
    static TushareProClient client() { return new TushareProClient(RestClient.builder().baseUrl(PROPERTIES.baseUrl().toString()).build(), PROPERTIES); }
    static TushareBatchPolicies production() { return new TushareBatchPolicies(client(), DEFINITIONS); }
    static ApiName api(String name) { return new ApiName(name); }
    static DateRange range(String start, String end) { return new DateRange(LocalDate.parse(start), LocalDate.parse(end)); }
    static DatasetDefinition definition(String name) { return DEFINITIONS.stream().filter(d -> d.datasetKey().apiName().value().equals(name)).findFirst().orElseThrow(); }

    @ParameterizedTest @MethodSource("rows")
    void everyProductionPolicyMatchesIndependentSpecificationAndStaysClosed(String specification) {
        String[] r = specification.split(" ");
        var p = productionPolicies().get(api(r[0]));
        var d = production().batchDescriptor(api(r[0])).orElseThrow();
        assertThat(p.parameterShape()).isEqualTo(switch(r[1]) { case "S" -> ParameterShape.STOCK; case "D" -> ParameterShape.DATES; case "E" -> ParameterShape.EXCHANGE; default -> ParameterShape.EXCHANGE_ID; });
        assertThat(p.planningMode()).isEqualTo(switch(r[2]) { case "N" -> PlanningMode.NATIVE_RANGE; case "C" -> PlanningMode.CALENDAR_DAYS; default -> PlanningMode.TRADING_DAYS; });
        assertThat(p.dateAxis()).isEqualTo(DateAxis.valueOf(r[3]));
        assertThat(p.outputDateColumn()).isEqualTo(r[4]);
        assertThat(p.documentedRowLimit()).isEqualTo(r[5].matches("[0-9]+") ? Long.valueOf(r[5]) : null);
        assertThat(p.ruleKind()).isEqualTo(r[5].equals("-") ? RuleKind.UNKNOWN : r[5].equals("coverage") ? RuleKind.CALENDAR_COVERAGE : RuleKind.ROW_LIMIT);
        assertThat(p.officialUrl()).isEqualTo("https://tushare.pro/document/2?doc_id=" + r[6]);
        assertThat(p.documentationCheckedOn()).isEqualTo(LocalDate.of(2026,9,11));
        assertThat(p.policyVersion()).isEqualTo("tushare-range-v1");
        assertThat(p.documentationNote()).isNotBlank();
        assertThat(p.sourceVerified()).isFalse(); assertThat(p.verificationEvidence()).isNull();
        assertThat(p.dailyParameter()).isEqualTo(r[2].equals("N") ? null : r[4]);
        assertThat(p.splittable()).isEqualTo(r[2].equals("N") && !r[0].equals("trade_cal"));
        assertThat(d.dateAxis()).isEqualTo(DateAxis.valueOf(r[3]));
        String label=r[0].equals("disclosure_date")?"最新披露公告日":switch(r[3]) {case "TRADE_DATE" -> "交易日期";case "ANNOUNCEMENT_DATE" -> "公告日期";case "REPORT_PERIOD" -> "报告期";case "CALENDAR_DATE" -> "日历日期";default -> "上网发行日期";};
        assertThat(d.dateLabel()).isEqualTo(label);
        String endpointLabel=label.endsWith("日期")?label.substring(0,label.length()-2):label;
        assertThat(d.parameters().get(d.parameters().size()-2).label()).isEqualTo(endpointLabel+"开始日期");
        assertThat(d.parameters().getLast().label()).isEqualTo(endpointLabel+"结束日期");
        assertThat(d.availability()).isEqualTo(Availability.NEEDS_VERIFICATION);
        assertThat(d.completenessRule()).isEqualTo(new CompletenessRule(CompletenessRule.Kind.UNKNOWN,null,null));
        assertThat(d.unavailableReason()).contains("区间参数语义与完整性尚待真实接口验证");
        if (r[5].equals("-")) assertThat(d.unavailableReason()).contains("尚无可确认的完整提取依据");
        assertThat(d.parameters()).allSatisfy(v -> { assertThat(v.required()).isTrue(); assertThat(v.defaultValue()).isNull(); assertThat(v.description()).isNull(); assertThat(v.pattern()).isNull(); });
        assertThat(d.parameters().get(d.parameters().size()-2).relatedParameter()).isEqualTo("end_date");
        assertThat(d.parameters().getLast().relatedParameter()).isEqualTo("start_date");
        d.parameters().stream().filter(v -> v.type()==ParameterType.ENUM).forEach(v -> assertThat(v.allowedValues()).containsExactly("SSE","SZSE","BSE"));
    }

    @Test void exactMembershipAndCountsAreImmutable() {
        assertThat(productionPolicies().keySet()).containsExactlyInAnyOrderElementsOf(rows().map(r -> api(r.split(" ")[0])).toList());
        assertThat(productionPolicies().values().stream().filter(p -> p.planningMode()==PlanningMode.NATIVE_RANGE)).hasSize(31);
        assertThat(productionPolicies().values().stream().filter(p -> p.parameterShape()==ParameterShape.STOCK)).hasSize(29);
        assertThat(productionPolicies().values().stream().filter(p -> p.ruleKind()==RuleKind.UNKNOWN)).hasSize(11);
        for (String n : SINGLE_ONLY) assertThat(production().batchDescriptor(api(n))).isEmpty();
        for (String n : List.of("unknown_api","top_inst","broker_recommend","share_float","hs_const","moneyflow_hsgt","hk_hold","index_member","hsgt_top10","namechange")) assertThat(production().batchDescriptor(api(n))).isEmpty();
        assertThatThrownBy(() -> productionPolicies().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    static Map<String,Object> params(String name) {
        String shape = rows().filter(r -> r.startsWith(name + " ")).findFirst().orElseThrow().split(" ")[1];
        var result = new HashMap<String,Object>();
        result.put("start_date", "20240228"); result.put("end_date", "20240301");
        switch (shape) { case "S" -> result.put("ts_code", "000001.SZ"); case "E" -> result.put("exchange", "SZSE"); case "I" -> result.put("exchange_id", "SZSE"); }
        return result;
    }
    static Policy verified(Policy p) { return new Policy(p.apiName(),p.parameterShape(),p.dateAxis(),p.dateLabel(),p.planningMode(),p.outputDateColumn(),p.dailyParameter(),p.splittable(),p.ruleKind(),p.documentedRowLimit(),p.documentationNote(),p.officialUrl(),p.documentationCheckedOn(),p.policyVersion(),true,"controlled-test-only"); }
    static TushareBatchPolicies verified(String... names) { return verified(client(), names); }
    static TushareBatchPolicies verified(TushareProClient client, String... names) {
        var copy = new HashMap<>(productionPolicies());
        for (String name : names) copy.put(api(name), verified(copy.get(api(name))));
        return new TushareBatchPolicies(client, DEFINITIONS, copy, CLOCK);
    }
    static DownloadEnvelope envelope(String name, Map<String,Object> params, List<List<Object>> data) {
        return new DownloadEnvelope(new PluginId("tushare_pro"),api(name),params,definition(name).columns().stream().map(ColumnDefinition::name).toList(),data.size(),data,DownloadStatus.SUCCESS,null);
    }
    static List<Object> row(String name, String date) {
        return definition(name).columns().stream().map(c -> (Object) switch(c.name()) {
            case "ts_code" -> "000001.SZ"; case "exchange", "exchange_id" -> "SZSE"; case "is_open" -> 1;
            default -> c.name().equals(productionPolicies().get(api(name)).outputDateColumn()) ? date : null;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
    static List<Object> with(String name,List<Object> row,String column,Object value) {
        var copy = new ArrayList<>(row); copy.set(definition(name).columns().stream().map(ColumnDefinition::name).toList().indexOf(column),value); return copy;
    }
    static void code(Runnable action, ErrorCode expected) {
        Throwable thrown = catchThrowable(action::run);
        assertThat(thrown).isInstanceOf(TensorException.class).hasNoCause();
        assertThat(((TensorException)thrown).code()).isEqualTo(expected);
        assertThat(thrown.getSuppressed()).isEmpty();
        assertThat(thrown.getMessage()).doesNotContain("secret", "000001", "2024");
    }
    static class Context implements BatchCallContext {
        volatile Instant deadline = Instant.MAX; volatile boolean stopped; AtomicInteger reservations = new AtomicInteger();
        public Instant deadline() { return deadline; } public boolean stopRequested() { return stopped; }
        public void beforeRequest() { reservations.incrementAndGet(); }
    }

    @ParameterizedTest @MethodSource("rows")
    void pureMappingAndProductionGateCoverEveryPolicy(String specification) {
        String[] r=specification.split(" "); String name=r[0]; var input=params(name); var before=Map.copyOf(input);
        var p=production(); var slice=range("2024-02-29","2024-03-01");
        Map<String,Object> expected=new HashMap<>(input);
        if(r[2].equals("N")) expected.put("start_date","20240229");
        else { expected.remove("start_date"); expected.remove("end_date"); expected.put(r[4],"20240229"); }
        if(r[2].equals("N")) assertThat(p.sourceParameters(api(name),input,RANGE)).isEqualTo(input);
        else assertThat(p.sourceParameters(api(name),input,RANGE)).isEqualTo(Map.of("ts_code","000001.SZ",r[4],"20240228"));
        var last=range("2024-03-01","2024-03-01");
        var lastExpected=new HashMap<>(expected);
        if(r[2].equals("N")){lastExpected.put("start_date","20240301");lastExpected.put("end_date","20240301");}
        else lastExpected.put(r[4],"20240301");
        assertThat(p.sourceParameters(api(name),input,last)).isEqualTo(lastExpected);
        assertThat(p.sourceParameters(api(name),input,slice)).isEqualTo(expected);
        assertThat(p.sourceParameters(api(name),input,slice)).isEqualTo(expected);
        assertThat(input).isEqualTo(before);
        assertThatThrownBy(() -> p.sourceParameters(api(name),input,slice).clear()).isInstanceOf(UnsupportedOperationException.class);
        var context=new Context(); code(() -> p.plan(api(name),input,context),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE); assertThat(context.reservations.get()).isZero();
        DateRange actual=r[2].equals("N")? RANGE : new DateRange(RANGE.start(),RANGE.start());
        var source=p.sourceParameters(api(name),input,actual);
        assertThat(p.assess(api(name),actual,envelope(name,source,List.of()))).isEqualTo(BatchAssessment.UNKNOWN);
        assertThat(p.assess(api(name),actual,envelope(name,source,List.of(row(name,"20240228"))))).isEqualTo(BatchAssessment.UNKNOWN);
        code(() -> p.assess(api(name),actual,envelope(name,source,List.of(row(name,"20240302")))),ErrorCode.SOURCE_RANGE_MISMATCH);
        if(r[1].equals("S")) code(() -> p.assess(api(name),actual,envelope(name,source,List.of(with(name,row(name,"20240228"),"ts_code","600000.SH")))),ErrorCode.SOURCE_RANGE_MISMATCH);
    }

    @Test void rejectsMalformedParametersAndUnsupportedConditionsLocally() {
        var p=production();
        for (String key:List.of("start_date","end_date","ts_code")) {
            var missing=params("daily"); missing.remove(key); code(() -> p.sourceParameters(api("daily"),missing,RANGE),ErrorCode.PARAM_INVALID);
            for (Object value:Arrays.asList(null,"",1," secret ")) { var bad=params("daily"); bad.put(key,value); code(() -> p.sourceParameters(api("daily"),bad,RANGE),ErrorCode.PARAM_INVALID); }
        }
        for(String date:List.of("20230229","20240230","20240228suffix","2024-02-28","20241301","20240302")) {
            var bad=params("daily"); bad.put("start_date",date); code(() -> p.sourceParameters(api("daily"),bad,RANGE),ErrorCode.PARAM_INVALID);
        }
        for(String stock:List.of("000001.sz","000001.SZ,600000.SH"," 000001.SZ")) { var bad=params("daily");bad.put("ts_code",stock);code(() -> p.sourceParameters(api("daily"),bad,RANGE),ErrorCode.PARAM_INVALID); }
        var extra=params("daily");extra.put("trade_date","20240228");code(() -> p.sourceParameters(api("daily"),extra,RANGE),ErrorCode.PARAM_INVALID);
        code(() -> p.sourceParameters(api("daily"),params("daily"),range("2024-02-27","2024-03-01")),ErrorCode.PARAM_INVALID);
        for(String stock:List.of("000001.BJ","000001.XX")) { var bad=params("top_list");bad.put("ts_code",stock);code(() -> p.sourceParameters(api("top_list"),bad,RANGE),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE); }
        var bse=params("trade_cal");bse.put("exchange","BSE");code(() -> p.sourceParameters(api("trade_cal"),bse,RANGE),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        var margin=params("margin");margin.put("exchange_id","BSE");assertThat(p.sourceParameters(api("margin"),margin,RANGE)).containsEntry("exchange_id","BSE");
        for(String n:SINGLE_ONLY) {code(() -> p.plan(api(n),Map.of(),new Context()),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);code(() -> p.sourceParameters(api(n),Map.of(),RANGE),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);code(() -> p.assess(api(n),RANGE,null),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);}
    }

    @ParameterizedTest @MethodSource("rows")
    void confirmedThresholdUsesRawRowsIncludingEqualityAndSingleDay(String specification) {
        String[] r=specification.split(" "); if(!r[5].matches("[0-9]+"))return;
        String name=r[0]; int limit=Integer.parseInt(r[5]); var p=verified(name); DateRange day=new DateRange(RANGE.start(),RANGE.start());
        var source=p.sourceParameters(api(name),params(name),day);
        for(int size:new int[]{0,limit-1,limit,limit+1}) assertThat(p.assess(api(name),day,envelope(name,source,Collections.nCopies(size,row(name,"20240228")))))
                .as(name+" rows="+size).isEqualTo(size<limit?BatchAssessment.COMPLETE:BatchAssessment.SPLIT_REQUIRED);
        assertThat(p.batchDescriptor(api(name)).orElseThrow().completenessRule().evidence()).contains("controlled-test-only", "https://tushare.pro/");
        assertThat(production().batchDescriptor(api(name)).orElseThrow().availability()).isEqualTo(Availability.NEEDS_VERIFICATION);
    }

    @Test void nativePlanningDoesNotInventPeriodBoundariesAndCalendarDaysIncludeWeekends() {
        for(String name:List.of("daily","weekly","monthly","fina_indicator")) assertThat(verified(name).plan(api(name),params(name),new Context())).containsExactly(RANGE);
        for(String name:List.of("dividend","disclosure_date")) {
            assertThat(verified(name).plan(api(name),params(name),new Context())).containsExactly(range("2024-02-28","2024-02-28"),range("2024-02-29","2024-02-29"),range("2024-03-01","2024-03-01"));
            var input=params(name);input.put("start_date","20231230");input.put("end_date","20240101");
            assertThat(verified(name).plan(api(name),input,new Context())).containsExactly(range("2023-12-30","2023-12-30"),range("2023-12-31","2023-12-31"),range("2024-01-01","2024-01-01"));
            code(() -> verified(name).assess(api(name),RANGE,envelope(name,production().sourceParameters(api(name),params(name),RANGE),List.of())),ErrorCode.SOURCE_RANGE_MISMATCH);
        }
    }

    @Test void planningChecksCpuControlWithoutReserving() {
        var p=verified("dividend"); var context=new Context(); context.deadline=CLOCK.instant();
        code(() -> p.plan(api("dividend"),params("dividend"),context),ErrorCode.TASK_LIMIT_EXCEEDED);
        context.deadline=Instant.MAX;context.stopped=true;code(() -> p.plan(api("dividend"),params("dividend"),context),ErrorCode.EXECUTION_INTERRUPTED);
        context.stopped=false;Thread.currentThread().interrupt();try {code(() -> p.plan(api("dividend"),params("dividend"),context),ErrorCode.EXECUTION_INTERRUPTED);assertThat(Thread.currentThread().isInterrupted()).isTrue();} finally {Thread.interrupted();}
        var checks=new AtomicInteger();var midway=new Context(){public boolean stopRequested(){return checks.incrementAndGet()>2;}};
        code(() -> p.plan(api("dividend"),params("dividend"),midway),ErrorCode.EXECUTION_INTERRUPTED);
        assertThat(context.reservations.get()).isZero();assertThat(midway.reservations.get()).isZero();
        code(() -> p.plan(api("dividend"),params("dividend"),null),ErrorCode.PARAM_INVALID);
        context.deadline=null;code(() -> p.plan(api("dividend"),params("dividend"),context),ErrorCode.PARAM_INVALID);
    }

    @Test void onlySelectedDateAxisControlsRangeAndNonStockRowsAreNotFiltered() {
        for(String name:List.of("fina_indicator","fina_mainbz","top10_holders","top10_floatholders")) {
            var source=production().sourceParameters(api(name),params(name),RANGE);
            var inside=row(name,"20240229");
            if (!name.equals("fina_mainbz")) inside=with(name,inside,"ann_date","20250101");
            assertThat(production().assess(api(name),RANGE,envelope(name,source,List.of(inside)))).isEqualTo(BatchAssessment.UNKNOWN);
            var outside=row(name,"20250101");
            if (!name.equals("fina_mainbz")) outside=with(name,outside,"ann_date","20240229");
            var outsideRow=outside;
            code(() -> production().assess(api(name),RANGE,envelope(name,source,List.of(outsideRow))),ErrorCode.SOURCE_RANGE_MISMATCH);
        }
        for(String[] pair:new String[][]{{"new_share","issue_date"},{"cashflow","f_ann_date"},{"pledge_detail","start_date"},{"stk_holdernumber","end_date"}}) {
            String name=pair[0];var source=production().sourceParameters(api(name),params(name),RANGE);
            assertThat(production().assess(api(name),RANGE,envelope(name,source,List.of(with(name,row(name,"20240229"),pair[1],"20250101"))))).isEqualTo(BatchAssessment.UNKNOWN);
        }
        for(String name:List.of("new_share","repurchase")) {
            var source=production().sourceParameters(api(name),params(name),RANGE);
            assertThat(production().assess(api(name),RANGE,envelope(name,source,List.of(row(name,"20240229"),with(name,row(name,"20240229"),"ts_code","600000.SH"))))).isEqualTo(BatchAssessment.UNKNOWN);
        }
    }

    @Test void responseStructureSnapshotAndValuesAreCheckedBeforeUnknown() {
        var p=production();var source=p.sourceParameters(api("daily"),params("daily"),RANGE);
        for(Object bad:Arrays.asList(null,20240229,"20240230","20240229suffix")) code(() -> p.assess(api("daily"),RANGE,envelope("daily",source,List.of(with("daily",row("daily","20240229"),"trade_date",bad)))),ErrorCode.SOURCE_PAYLOAD_INVALID);
        var fields=new ArrayList<>(definition("daily").columns().stream().map(ColumnDefinition::name).toList());Collections.reverse(fields);
        code(() -> p.assess(api("daily"),RANGE,new DownloadEnvelope(new PluginId("tushare_pro"),api("daily"),source,fields,0,List.of(),DownloadStatus.SUCCESS,null)),ErrorCode.SOURCE_PAYLOAD_INVALID);
        code(() -> p.assess(api("daily"),RANGE,new DownloadEnvelope(new PluginId("tushare_pro"),api("daily"),source,List.of(),0,List.of(),DownloadStatus.FAILURE,"secret")),ErrorCode.SOURCE_PAYLOAD_INVALID);
        code(() -> p.assess(api("daily"),RANGE,new DownloadEnvelope(new PluginId("other_plugin"),api("daily"),source,fields,0,List.of(),DownloadStatus.SUCCESS,null)),ErrorCode.SOURCE_RANGE_MISMATCH);
        var wrong=new HashMap<>(source);wrong.put("end_date","20240229");code(() -> p.assess(api("daily"),RANGE,envelope("daily",wrong,List.of())),ErrorCode.SOURCE_RANGE_MISMATCH);
        var margin=p.sourceParameters(api("margin"),params("margin"),RANGE);code(() -> p.assess(api("margin"),RANGE,envelope("margin",margin,List.of(with("margin",row("margin","20240229"),"exchange_id","SSE")))),ErrorCode.SOURCE_RANGE_MISMATCH);
    }

    @Test void invalidRegistryCannotClaimVerificationOrChangeMembership() {
        for(String name:rows().filter(r -> r.split(" ")[5].equals("-")).map(r -> r.split(" ")[0]).toList()) assertThatThrownBy(() -> verified(name)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Tushare batch policies").hasNoCause();
        var missing=new HashMap<>(productionPolicies());missing.remove(api("daily"));invalidRegistry(DEFINITIONS,missing);
        var definitions=new ArrayList<>(DEFINITIONS);definitions.add(DEFINITIONS.getFirst());invalidRegistry(definitions,productionPolicies());
        invalidRegistry(DEFINITIONS.stream().filter(d -> !d.datasetKey().apiName().equals(api("daily"))).toList(),productionPolicies());
        var mismatch=new HashMap<>(productionPolicies());mismatch.put(api("daily"),mismatch.get(api("weekly")));invalidRegistry(DEFINITIONS,mismatch);
        var p=productionPolicies().get(api("daily"));
        for(Policy bad:List.of(new Policy(p.apiName(),p.parameterShape(),p.dateAxis(),p.dateLabel(),p.planningMode(),"missing_column",null,true,p.ruleKind(),6000L,p.documentationNote(),p.officialUrl(),p.documentationCheckedOn(),p.policyVersion(),false,null),new Policy(p.apiName(),ParameterShape.DATES,p.dateAxis(),p.dateLabel(),p.planningMode(),p.outputDateColumn(),null,true,p.ruleKind(),6000L,p.documentationNote(),p.officialUrl(),p.documentationCheckedOn(),p.policyVersion(),false,null),new Policy(p.apiName(),p.parameterShape(),p.dateAxis(),p.dateLabel(),p.planningMode(),p.outputDateColumn(),null,true,p.ruleKind(),0L,p.documentationNote(),p.officialUrl(),p.documentationCheckedOn(),p.policyVersion(),false,null),new Policy(p.apiName(),p.parameterShape(),p.dateAxis(),p.dateLabel(),p.planningMode(),p.outputDateColumn(),null,true,p.ruleKind(),6000L,p.documentationNote(),p.officialUrl(),p.documentationCheckedOn(),p.policyVersion(),false,"secret"))) {
            var copy=new HashMap<>(productionPolicies());copy.put(p.apiName(),bad);invalidRegistry(DEFINITIONS,copy);
        }
    }
    private void invalidRegistry(List<DatasetDefinition> definitions,Map<ApiName,Policy> policies) { assertThatThrownBy(() -> new TushareBatchPolicies(client(),definitions,policies,CLOCK)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Tushare batch policies").hasNoCause(); }
}
