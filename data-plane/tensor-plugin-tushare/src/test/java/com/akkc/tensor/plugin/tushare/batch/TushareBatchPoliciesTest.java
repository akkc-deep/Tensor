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
    // Independent specification: API, shape, mode, axis, output, rule, candidate limit, official document.
    static Stream<String> rows() { return """
        daily S N TRADE_DATE trade_date ROW 6000 27
        weekly S N TRADE_DATE trade_date ROW 6000 144
        monthly S N TRADE_DATE trade_date ROW 4500 145
        adj_factor S N TRADE_DATE trade_date RESPONSE - 28
        daily_basic S N TRADE_DATE trade_date ROW 6000 32
        stk_limit S N TRADE_DATE trade_date ROW 5800 183
        suspend_d S N TRADE_DATE trade_date RESPONSE - 214
        moneyflow S N TRADE_DATE trade_date ROW 6000 170
        margin I N TRADE_DATE trade_date ROW 4000 58
        margin_detail S N TRADE_DATE trade_date ROW 6000 59
        block_trade S N TRADE_DATE trade_date ROW 1000 161
        slb_len D N TRADE_DATE trade_date ROW 5000 331
        slb_sec S N TRADE_DATE trade_date ROW 5000 332
        slb_sec_detail S N TRADE_DATE trade_date ROW 5000 333
        trade_cal E N CALENDAR_DATE cal_date CALENDAR - 26
        new_share D N ISSUE_DATE ipo_date ROW 2000 123
        income S N ANNOUNCEMENT_DATE ann_date RESPONSE - 33
        balancesheet S N ANNOUNCEMENT_DATE ann_date RESPONSE - 36
        cashflow S N ANNOUNCEMENT_DATE ann_date RESPONSE - 44
        fina_audit S N ANNOUNCEMENT_DATE ann_date RESPONSE - 80
        forecast S N ANNOUNCEMENT_DATE ann_date ROW 3500 45
        express S N ANNOUNCEMENT_DATE ann_date RESPONSE - 46
        repurchase D N ANNOUNCEMENT_DATE ann_date RESPONSE - 124
        stk_managers S N ANNOUNCEMENT_DATE ann_date RESPONSE - 193
        stk_holdernumber S N ANNOUNCEMENT_DATE ann_date ROW 3000 166
        stk_holdertrade S N ANNOUNCEMENT_DATE ann_date ROW 3000 175
        pledge_detail S N ANNOUNCEMENT_DATE ann_date ROW 1000 111
        fina_indicator S N REPORT_PERIOD end_date ROW 100 79
        fina_mainbz S N REPORT_PERIOD end_date ROW 100 81
        top10_holders S N REPORT_PERIOD end_date RESPONSE - 61
        top10_floatholders S N REPORT_PERIOD end_date RESPONSE - 62
        top_list S T TRADE_DATE trade_date ROW 10000 106
        dividend S C ANNOUNCEMENT_DATE ann_date ROW 2000 103
        disclosure_date S C ANNOUNCEMENT_DATE ann_date ROW 6000 162
        """.strip().lines(); }
    static final List<String> SINGLE_ONLY = List.of("pledge_stat", "stk_rewards", "stock_basic", "stock_company", "index_classify", "index_member_all");
    static final Set<String> LEGACY_SOURCE_VERIFIED = Set.of("daily_basic", "stk_limit", "moneyflow", "margin_detail");
    static final Set<String> SOURCE_VERIFIED = rows().map(r -> r.split(" ")[0]).filter(n -> !n.equals("fina_indicator")).collect(java.util.stream.Collectors.toUnmodifiableSet());
    static final Map<String, String> CANDIDATE_SOURCE = Map.ofEntries(
            Map.entry("daily", "issue019-source-20260913T122126Z/issue019-source-20260913T122126Z-daily-000001-whole"),
            Map.entry("forecast", "issue019-source-20260913T122126Z/issue019-source-20260913T122126Z-forecast-000001-whole"),
            Map.entry("dividend", "issue019-source-20260913T122126Z/issue019-source-20260913T122126Z-dividend-000001-whole"),
            Map.entry("fina_mainbz", "issue026-mainbz-split-source-20260914T013736Z/issue026-mainbz-split-source-20260914T013736Z-000001-annual"),
            Map.entry("trade_cal", "issue021-source-20260913T135006Z/issue021-source-20260913T135006Z-trade_cal-sse-whole"),
            Map.entry("margin", "issue021-source-20260913T135006Z/issue021-source-20260913T135006Z-margin-sse-whole"),
            Map.entry("top_list", "issue021-bj-source-20260913T135704Z/issue021-bj-source-20260913T135704Z-920008-whole"),
            Map.entry("weekly", "issue022-source-20260913T141544Z/issue022-source-20260913T141544Z-weekly-000001-whole"),
            Map.entry("monthly", "issue022-source-20260913T141544Z/issue022-source-20260913T141544Z-monthly-000001-whole"),
            Map.entry("fina_indicator", "issue022-source-20260913T141544Z/issue022-source-20260913T141544Z-indicator-000001-whole"),
            Map.entry("stk_holdernumber", "issue022-source-20260913T141544Z/issue022-source-20260913T141544Z-holder-000001-whole"),
            Map.entry("new_share", "issue022-source-20260913T141544Z/issue022-source-20260913T141544Z-ipo-whole"),
            Map.entry("block_trade", "issue023-source-20260913T144308Z/issue023-source-20260913T144308Z-block-official"),
            Map.entry("disclosure_date", "issue023-source-20260913T144308Z/issue023-source-20260913T144308Z-disclosure-official"),
            Map.entry("stk_holdertrade", "issue023-source-20260913T144308Z/issue023-source-20260913T144308Z-holder-official"),
            Map.entry("pledge_detail", "issue023-source-20260913T144308Z/issue023-source-20260913T144308Z-pledge-official"),
            Map.entry("slb_len", "issue024-source-20260913T153146Z/issue024-source-20260913T153146Z-len-whole"),
            Map.entry("slb_sec", "issue024-source-20260913T153146Z/issue024-source-20260913T153146Z-slb_sec-000001-whole"),
            Map.entry("slb_sec_detail", "issue024-source-20260913T153146Z/issue024-source-20260913T153146Z-slb_sec_detail-000001-whole"),
            Map.entry("adj_factor", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-adj_factor-000001-whole"),
            Map.entry("suspend_d", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-suspend_d-000001-whole"),
            Map.entry("income", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-income-000001-whole"),
            Map.entry("balancesheet", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-balancesheet-000001-whole"),
            Map.entry("cashflow", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-cashflow-000001-whole"),
            Map.entry("fina_audit", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-fina_audit-000001-whole"),
            Map.entry("express", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-express-600000-whole"),
            Map.entry("repurchase", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-repurchase-all-whole"),
            Map.entry("stk_managers", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-stk_managers-000001-whole"),
            Map.entry("top10_holders", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-top10_holders-000001-whole"),
            Map.entry("top10_floatholders", "issue025-source-20260913T162759Z/issue025-source-20260913T162759Z-top10_floatholders-000001-whole"));
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
    void everyProductionPolicyMatchesIndependentSpecificationAndVerificationGate(String specification) {
        String[] r = specification.split(" ");
        String name = r[0];
        var p = productionPolicies().get(api(r[0]));
        var d = production().batchDescriptor(api(r[0])).orElseThrow();
        assertThat(p.parameterShape()).isEqualTo(switch(r[1]) { case "S" -> ParameterShape.STOCK; case "D" -> ParameterShape.DATES; case "E" -> ParameterShape.EXCHANGE; default -> ParameterShape.EXCHANGE_ID; });
        assertThat(p.planningMode()).isEqualTo(switch(r[2]) { case "N" -> PlanningMode.NATIVE_RANGE; case "C" -> PlanningMode.CALENDAR_DAYS; default -> PlanningMode.TRADING_DAYS; });
        assertThat(p.dateAxis()).isEqualTo(DateAxis.valueOf(r[3]));
        assertThat(p.outputDateColumn()).isEqualTo(r[4]);
        assertThat(p.ruleKind()).isEqualTo(switch (r[5]) { case "ROW" -> RuleKind.ROW_LIMIT; case "CALENDAR" -> RuleKind.CALENDAR_COVERAGE; default -> RuleKind.RESPONSE_ONLY; });
        assertThat(p.documentedRowLimit()).isEqualTo(r[6].matches("[0-9]+") ? Long.valueOf(r[6]) : null);
        assertThat(p.officialUrl()).isEqualTo("https://tushare.pro/document/2?doc_id=" + r[7]);
        assertThat(p.documentationCheckedOn()).isEqualTo(LocalDate.of(2026,9,LEGACY_SOURCE_VERIFIED.contains(name) ? 13 : 14));
        boolean withdrawn = name.equals("fina_indicator");
        assertThat(p.policyVersion()).isEqualTo(withdrawn ? "tushare-range-v3" : "tushare-range-v2");
        assertThat(p.documentationNote()).isNotBlank();
        assertThat(p.sourceVerified()).isEqualTo(!withdrawn);
        if (withdrawn) assertThat(p.verificationEvidence()).isNull();
        else assertThat(p.verificationEvidence()).contains("docs/verification/ISSUE-018-range-acceptance.md#" + name);
        if (LEGACY_SOURCE_VERIFIED.contains(name)) {
            String sourceRun = "issue018-t14-priority-source-20260913T092938Z";
            var cases = new ArrayList<>(List.of("single-000001", "single-600000", "range", "lower-bound",
                    "upper-bound", "range-600000", "lower-bound-600000", "upper-bound-600000"));
            if (name.equals("daily_basic")) cases.add("cross-year");
            assertThat(p.documentationNote()).isEqualTo("官网候选行数上限 " + r[6] + "；");
            assertThat(p.verificationEvidence()).isEqualTo("docs/verification/ISSUE-018-range-acceptance.md#" + name
                    + "；SOURCE " + sourceRun + "；" + String.join(",", cases.stream()
                            .map(suffix -> sourceRun + "-" + name + "-" + suffix).toList()));
            for (String suffix : cases) assertThat(p.verificationEvidence())
                    .contains("issue018-t14-priority-source-20260913T092938Z-" + name + "-" + suffix);
        } else if (!withdrawn) {
            assertThat(p.verificationEvidence()).contains("docs/verification/ISSUE-018-range-acceptance.json#" + CANDIDATE_SOURCE.get(name));
        }
        if (r[5].equals("RESPONSE")) {
            assertThat(p.verificationEvidence()).contains("docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录");
            assertThat(p.documentationNote()).contains("允许不完整", "响应采集");
        } else if (name.startsWith("slb_")) {
            assertThat(p.verificationEvidence()).contains("docs/issues/proposals/ISSUE-024-historical-support.md#决策记录");
            assertThat(p.documentationNote()).contains("历史查询");
        } else if (LEGACY_SOURCE_VERIFIED.contains(name)) {
            assertThat(p.documentationNote()).contains("官网候选行数上限");
        } else if (r[5].equals("ROW")) {
            assertThat(p.documentationNote()).contains("工程阈值");
        } else {
            assertThat(p.documentationNote()).contains("完整自然日覆盖");
        }
        assertThat(p.dailyParameter()).isEqualTo(r[2].equals("N") ? null : r[4]);
        assertThat(p.splittable()).isEqualTo(r[2].equals("N") && r[5].equals("ROW"));
        assertThat(d.dateAxis()).isEqualTo(DateAxis.valueOf(r[3]));
        String label=r[0].equals("disclosure_date")?"最新披露公告日":switch(r[3]) {case "TRADE_DATE" -> "交易日期";case "ANNOUNCEMENT_DATE" -> "公告日期";case "REPORT_PERIOD" -> "报告期";case "CALENDAR_DATE" -> "日历日期";default -> "上网发行日期";};
        assertThat(d.dateLabel()).isEqualTo(label);
        String endpointLabel=label.endsWith("日期")?label.substring(0,label.length()-2):label;
        assertThat(d.parameters().get(d.parameters().size()-2).label()).isEqualTo(endpointLabel+"开始日期");
        assertThat(d.parameters().getLast().label()).isEqualTo(endpointLabel+"结束日期");
        assertThat(d.availability()).isEqualTo(withdrawn ? Availability.NEEDS_VERIFICATION : Availability.AVAILABLE);
        assertThat(d.completenessRule().kind()).isEqualTo(withdrawn ? CompletenessRule.Kind.UNKNOWN : switch (r[5]) {
            case "ROW" -> CompletenessRule.Kind.CONFIRMED_ROW_LIMIT;
            case "CALENDAR" -> CompletenessRule.Kind.VERIFIED_RULE;
            default -> CompletenessRule.Kind.RESPONSE_ONLY;
        });
        assertThat(d.completenessRule().rowLimit()).isEqualTo(!withdrawn && r[6].matches("[0-9]+") ? Long.valueOf(r[6]) : null);
        if (withdrawn) {
            assertThat(d.completenessRule().evidence()).isNull();
            assertThat(d.unavailableReason()).isNotBlank();
            code(() -> production().plan(api(name), params(name), new Context()), ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        } else {
            assertThat(d.completenessRule().evidence()).contains(p.verificationEvidence(), p.officialUrl());
            assertThat(d.unavailableReason()).isNull();
        }
        assertThat(d.parameters()).allSatisfy(v -> { assertThat(v.required()).isTrue(); assertThat(v.defaultValue()).isNull(); assertThat(v.description()).isNull(); assertThat(v.pattern()).isNull(); });
        assertThat(d.parameters().get(d.parameters().size()-2).relatedParameter()).isEqualTo("end_date");
        assertThat(d.parameters().getLast().relatedParameter()).isEqualTo("start_date");
        d.parameters().stream().filter(v -> v.type()==ParameterType.ENUM).forEach(v -> assertThat(v.allowedValues()).containsExactly("SSE","SZSE","BSE"));
    }

    @Test void exactMembershipAndCountsAreImmutable() {
        assertThat(productionPolicies().keySet()).containsExactlyInAnyOrderElementsOf(rows().map(r -> api(r.split(" ")[0])).toList());
        assertThat(productionPolicies().values().stream().filter(p -> p.planningMode()==PlanningMode.NATIVE_RANGE)).hasSize(31);
        assertThat(productionPolicies().values().stream().filter(p -> p.parameterShape()==ParameterShape.STOCK)).hasSize(29);
        assertThat(productionPolicies().values().stream().filter(p -> p.ruleKind()==RuleKind.ROW_LIMIT)).hasSize(22);
        assertThat(productionPolicies().values().stream().filter(p -> p.ruleKind()==RuleKind.RESPONSE_ONLY)).hasSize(11);
        assertThat(productionPolicies().values().stream().filter(p -> p.ruleKind()==RuleKind.CALENDAR_COVERAGE)).hasSize(1);
        assertThat(productionPolicies().values().stream().filter(p -> p.ruleKind()==RuleKind.UNKNOWN)).isEmpty();
        assertThat(productionPolicies().values().stream().filter(Policy::sourceVerified).map(p -> p.apiName().value()))
                .containsExactlyInAnyOrderElementsOf(SOURCE_VERIFIED);
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
    static Policy responseOnlyPolicy(String name, PlanningMode mode, boolean split, Long limit, boolean verified) {
        var p = productionPolicies().get(api(name));
        return new Policy(p.apiName(), p.parameterShape(), p.dateAxis(), p.dateLabel(), mode,
                p.outputDateColumn(), mode == PlanningMode.NATIVE_RANGE ? null : p.outputDateColumn(), split,
                RuleKind.valueOf("RESPONSE_ONLY"), limit, p.documentationNote(), p.officialUrl(),
                p.documentationCheckedOn(), "controlled-response-v2", verified, verified ? "controlled-test-only" : null);
    }
    static TushareBatchPolicies responseOnly(TushareProClient client, String name, boolean verified) {
        var copy = new HashMap<>(productionPolicies());
        copy.put(api(name), responseOnlyPolicy(name, PlanningMode.NATIVE_RANGE, false, null, verified));
        return new TushareBatchPolicies(client, DEFINITIONS, copy, CLOCK);
    }
    static Policy verified(Policy p) { return new Policy(p.apiName(),p.parameterShape(),p.dateAxis(),p.dateLabel(),p.planningMode(),p.outputDateColumn(),p.dailyParameter(),p.splittable(),p.ruleKind(),p.documentedRowLimit(),p.documentationNote(),p.officialUrl(),p.documentationCheckedOn(),p.policyVersion(),true,"controlled-test-only"); }
    static TushareBatchPolicies verified(String... names) { return verified(client(), names); }
    static TushareBatchPolicies verified(TushareProClient client, String... names) {
        var copy = new HashMap<>(productionPolicies());
        for (String name : names) copy.put(api(name), verified(copy.get(api(name))));
        return new TushareBatchPolicies(client, DEFINITIONS, copy, CLOCK);
    }
    static BatchAssessment expectedAssessment(String name) {
        String rule = rows().filter(r -> r.startsWith(name + " ")).findFirst().orElseThrow().split(" ")[5];
        return rule.equals("RESPONSE") ? BatchAssessment.RESPONSE_ONLY : BatchAssessment.COMPLETE;
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
        var p=name.equals("fina_indicator") ? verified(name) : production(); var slice=range("2024-02-29","2024-03-01");
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
        var context=new Context();
        if (r[2].equals("N")) assertThat(p.plan(api(name),input,context)).containsExactly(RANGE);
        if (r[2].equals("C")) assertThat(p.plan(api(name),input,context)).containsExactly(
                range("2024-02-28","2024-02-28"), range("2024-02-29","2024-02-29"), range("2024-03-01","2024-03-01"));
        assertThat(context.reservations.get()).isZero();
        DateRange actual=r[2].equals("N")? RANGE : new DateRange(RANGE.start(),RANGE.start());
        var source=p.sourceParameters(api(name),input,actual);
        var assessment=expectedAssessment(name);
        if (name.equals("trade_cal")) {
            var calendarRows = List.of(row(name,"20240228"), row(name,"20240229"), row(name,"20240301"));
            assertThat(p.assess(api(name),actual,envelope(name,source,calendarRows))).isEqualTo(BatchAssessment.COMPLETE);
        } else {
        assertThat(p.assess(api(name),actual,envelope(name,source,List.of()))).isEqualTo(assessment);
        assertThat(p.assess(api(name),actual,envelope(name,source,List.of(row(name,"20240228"))))).isEqualTo(assessment);
        }
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
        var bj=params("top_list");bj.put("ts_code","920008.BJ");
        assertThat(p.sourceParameters(api("top_list"),bj,new DateRange(RANGE.start(),RANGE.start())))
                .isEqualTo(Map.of("ts_code","920008.BJ","trade_date","20240228"));
        var bad=params("top_list");bad.put("ts_code","000001.XX");code(() -> p.sourceParameters(api("top_list"),bad,RANGE),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        var bse=params("trade_cal");bse.put("exchange","BSE");code(() -> p.sourceParameters(api("trade_cal"),bse,RANGE),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        for (String exchange : List.of("SSE", "SZSE", "BSE")) {
            var margin=params("margin");margin.put("exchange_id",exchange);
            assertThat(p.sourceParameters(api("margin"),margin,RANGE)).containsEntry("exchange_id",exchange);
        }
        for(String n:SINGLE_ONLY) {code(() -> p.plan(api(n),Map.of(),new Context()),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);code(() -> p.sourceParameters(api(n),Map.of(),RANGE),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);code(() -> p.assess(api(n),RANGE,null),ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);}
    }

    @ParameterizedTest @MethodSource("rows")
    void confirmedThresholdUsesRawRowsIncludingEqualityAndSingleDay(String specification) {
        String[] r=specification.split(" "); if(!r[5].equals("ROW"))return;
        String name=r[0]; int limit=Integer.parseInt(r[6]);
        var p=name.equals("fina_indicator") ? verified(name) : production(); DateRange day=new DateRange(RANGE.start(),RANGE.start());
        var source=p.sourceParameters(api(name),params(name),day);
        for(int size:new int[]{0,limit-1,limit,limit+1}) assertThat(p.assess(api(name),day,envelope(name,source,Collections.nCopies(size,row(name,"20240228")))))
                .as(name+" rows="+size).isEqualTo(size<limit?BatchAssessment.COMPLETE:BatchAssessment.SPLIT_REQUIRED);
        if (name.equals("fina_indicator")) return; // Admission is covered separately; threshold above stays controlled.
        assertThat(p.batchDescriptor(api(name)).orElseThrow().completenessRule().evidence())
                .contains(LEGACY_SOURCE_VERIFIED.contains(name)?"issue018-t14-priority-source-20260913T092938Z":CANDIDATE_SOURCE.get(name), "https://tushare.pro/");
        assertThat(production().batchDescriptor(api(name)).orElseThrow().availability())
                .isEqualTo(Availability.AVAILABLE);
    }

    @Test void nativePlanningDoesNotInventPeriodBoundariesAndCalendarDaysIncludeWeekends() {
        for(String name:List.of("daily","weekly","monthly","fina_indicator")) assertThat((name.equals("fina_indicator") ? verified(name) : production()).plan(api(name),params(name),new Context())).containsExactly(RANGE);
        for(String name:List.of("dividend","disclosure_date")) {
            assertThat(production().plan(api(name),params(name),new Context())).containsExactly(range("2024-02-28","2024-02-28"),range("2024-02-29","2024-02-29"),range("2024-03-01","2024-03-01"));
            var input=params(name);input.put("start_date","20231230");input.put("end_date","20240101");
            assertThat(production().plan(api(name),input,new Context())).containsExactly(range("2023-12-30","2023-12-30"),range("2023-12-31","2023-12-31"),range("2024-01-01","2024-01-01"));
            code(() -> production().assess(api(name),RANGE,envelope(name,production().sourceParameters(api(name),params(name),RANGE),List.of())),ErrorCode.SOURCE_RANGE_MISMATCH);
        }
    }

    @Test void planningChecksCpuControlWithoutReserving() {
        var p=production(); var context=new Context(); context.deadline=CLOCK.instant();
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
            var p=name.equals("fina_indicator") ? verified(name) : production();
            var source=p.sourceParameters(api(name),params(name),RANGE);
            var inside=row(name,"20240229");
            if (!name.equals("fina_mainbz")) inside=with(name,inside,"ann_date","20250101");
            assertThat(p.assess(api(name),RANGE,envelope(name,source,List.of(inside)))).isEqualTo(expectedAssessment(name));
            var outside=row(name,"20250101");
            if (!name.equals("fina_mainbz")) outside=with(name,outside,"ann_date","20240229");
            var outsideRow=outside;
            code(() -> p.assess(api(name),RANGE,envelope(name,source,List.of(outsideRow))),ErrorCode.SOURCE_RANGE_MISMATCH);
        }
        for(String[] pair:new String[][]{{"new_share","issue_date"},{"cashflow","f_ann_date"},{"pledge_detail","start_date"},{"stk_holdernumber","end_date"}}) {
            String name=pair[0];var source=production().sourceParameters(api(name),params(name),RANGE);
            assertThat(production().assess(api(name),RANGE,envelope(name,source,List.of(with(name,row(name,"20240229"),pair[1],"20250101"))))).isEqualTo(expectedAssessment(name));
        }
        for(String name:List.of("new_share","repurchase")) {
            var source=production().sourceParameters(api(name),params(name),RANGE);
            assertThat(production().assess(api(name),RANGE,envelope(name,source,List.of(row(name,"20240229"),with(name,row(name,"20240229"),"ts_code","600000.SH"))))).isEqualTo(expectedAssessment(name));
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

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"adj_factor", "suspend_d", "income", "balancesheet",
            "cashflow", "fina_audit", "express", "repurchase", "stk_managers", "top10_holders", "top10_floatholders"})
    void responseOnlyCollectsValidatedRowsAndEmptyResponsesWithoutClaimingCompleteness(String name) {
        assertThat(RuleKind.values()).extracting(Enum::name).contains("RESPONSE_ONLY");
        var p = production(); var input = params(name); var context = new Context();
        var descriptor = p.batchDescriptor(api(name)).orElseThrow();
        assertThat(descriptor.completenessRule().kind()).isEqualTo(CompletenessRule.Kind.RESPONSE_ONLY);
        assertThat(descriptor.completenessRule().rowLimit()).isNull(); assertThat(descriptor.splittable()).isFalse();
        assertThat(p.plan(api(name), input, context)).containsExactly(RANGE);
        assertThat(p.sourceParameters(api(name), input, RANGE)).isEqualTo(input);
        assertThat(context.reservations.get()).isZero();
        for (var rows : List.of(List.<List<Object>>of(), List.of(row(name, "20240228"), row(name, "20240301")),
                Collections.nCopies(7000, row(name, "20240229")))) {
            assertThat(p.assess(api(name), RANGE, envelope(name, input, rows))).isEqualTo(BatchAssessment.RESPONSE_ONLY);
        }
        for (String date : List.of("20240227", "20240302")) {
            code(() -> p.assess(api(name), RANGE, envelope(name, input, List.of(row(name, date)))), ErrorCode.SOURCE_RANGE_MISMATCH);
        }
        code(() -> p.assess(api(name), RANGE, envelope(name, input, List.of(row(name, "20240230")))), ErrorCode.SOURCE_PAYLOAD_INVALID);
        var otherStock = envelope(name, input, List.of(with(name, row(name, "20240229"), "ts_code", "600000.SH")));
        // repurchase retains its original date-only, market-wide request shape.
        if (name.equals("repurchase")) assertThat(p.assess(api(name), RANGE, otherStock)).isEqualTo(BatchAssessment.RESPONSE_ONLY);
        else code(() -> p.assess(api(name), RANGE, otherStock), ErrorCode.SOURCE_RANGE_MISMATCH);
        var wrong = new HashMap<>(input); wrong.put("end_date", "20240229");
        code(() -> p.assess(api(name), RANGE, envelope(name, wrong, List.of())), ErrorCode.SOURCE_RANGE_MISMATCH);
        var badFields = new DownloadEnvelope(new PluginId("tushare_pro"), api(name), input,
                List.of("ts_code"), 0, List.of(), DownloadStatus.SUCCESS, null);
        code(() -> p.assess(api(name), RANGE, badFields), ErrorCode.SOURCE_PAYLOAD_INVALID);
        code(() -> p.assess(api(name), RANGE, null), ErrorCode.SOURCE_PAYLOAD_INVALID);
        var unavailable = responseOnly(client(), name, false);
        assertThat(unavailable.batchDescriptor(api(name)).orElseThrow().completenessRule().kind()).isEqualTo(CompletenessRule.Kind.UNKNOWN);
        assertThat(unavailable.assess(api(name), RANGE, envelope(name, input, List.of()))).isEqualTo(BatchAssessment.UNKNOWN);
        code(() -> unavailable.plan(api(name), input, context), ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
    }

    @Test void responseOnlyRegistryRejectsSplitDailyAndThresholdCombinations() {
        assertThat(RuleKind.values()).extracting(Enum::name).contains("RESPONSE_ONLY");
        for (var mode : PlanningMode.values()) {
            for (boolean split : List.of(false, true)) {
                if (mode == PlanningMode.NATIVE_RANGE && !split) continue;
                var copy = new HashMap<>(productionPolicies());
                copy.put(api("adj_factor"), responseOnlyPolicy("adj_factor", mode, split, null, true));
                invalidRegistry(DEFINITIONS, copy);
            }
        }
        var copy = new HashMap<>(productionPolicies());
        copy.put(api("adj_factor"), responseOnlyPolicy("adj_factor", PlanningMode.NATIVE_RANGE, false, 100L, true));
        invalidRegistry(DEFINITIONS, copy);
    }

    @Test void invalidRegistryCannotClaimVerificationOrChangeMembership() {
        var original = productionPolicies().get(api("adj_factor"));
        var historical = new Policy(original.apiName(), original.parameterShape(), original.dateAxis(), original.dateLabel(),
                original.planningMode(), original.outputDateColumn(), original.dailyParameter(), true, RuleKind.UNKNOWN,
                null, "历史 UNKNOWN 候选", original.officialUrl(), original.documentationCheckedOn(),
                "tushare-range-v1", false, null);
        var historicalPolicies = new HashMap<>(productionPolicies()); historicalPolicies.put(original.apiName(), historical);
        var historicalRegistry = new TushareBatchPolicies(client(), DEFINITIONS, historicalPolicies, CLOCK);
        assertThat(historicalRegistry.batchDescriptor(original.apiName()).orElseThrow().completenessRule().kind())
                .isEqualTo(CompletenessRule.Kind.UNKNOWN);
        code(() -> historicalRegistry.plan(original.apiName(), params("adj_factor"), new Context()), ErrorCode.BATCH_DOWNLOAD_UNAVAILABLE);
        historicalPolicies.put(original.apiName(), verified(historical));
        invalidRegistry(DEFINITIONS, historicalPolicies);
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
