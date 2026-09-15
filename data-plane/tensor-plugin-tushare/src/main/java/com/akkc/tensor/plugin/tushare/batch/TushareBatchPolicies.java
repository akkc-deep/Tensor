package com.akkc.tensor.plugin.tushare.batch;

import static com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.*;
import static com.akkc.tensor.plugin.api.error.ErrorCode.*;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.download.batch.*;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Local range semantics. Documented candidates remain closed until source verification. */
public final class TushareBatchPolicies {
    enum ParameterShape { STOCK, DATES, EXCHANGE, EXCHANGE_ID }
    enum RuleKind { ROW_LIMIT, CALENDAR_COVERAGE, RESPONSE_ONLY, UNKNOWN }
    record Policy(ApiName apiName, ParameterShape parameterShape,
                  DateAxis dateAxis, String dateLabel, PlanningMode planningMode,
                  String outputDateColumn, String dailyParameter, boolean splittable,
                  RuleKind ruleKind, Long documentedRowLimit, String documentationNote,
                  String officialUrl, LocalDate documentationCheckedOn,
                  String policyVersion, boolean sourceVerified, String verificationEvidence) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final List<String> EXCHANGES = List.of("SSE", "SZSE", "BSE");
    private static final Set<String> RESPONSE_ONLY = Set.of("adj_factor", "suspend_d", "income", "balancesheet",
            "cashflow", "fina_audit", "express", "repurchase", "stk_managers", "top10_holders", "top10_floatholders");
    private static final Map<String, CandidateEvidence> CANDIDATES = candidateEvidence();
    private static final Map<ApiName, Policy> PRODUCTION = createPolicies();
    private final TushareProClient client;
    private final Map<ApiName, DatasetDefinition> definitions;
    private final Map<ApiName, Policy> policies;
    private final Clock clock;

    public TushareBatchPolicies(TushareProClient client, List<DatasetDefinition> definitions) {
        this(client, definitions, PRODUCTION, Clock.systemUTC());
    }

    TushareBatchPolicies(TushareProClient client, List<DatasetDefinition> definitions,
                        Map<ApiName, Policy> policies, Clock clock) {
        try {
            this.client = Objects.requireNonNull(client);
            this.clock = Objects.requireNonNull(clock);
            var index = new HashMap<ApiName, DatasetDefinition>();
            for (var definition : definitions) {
                if (!definition.datasetKey().pluginId().value().equals("tushare_pro")
                        || index.put(definition.datasetKey().apiName(), definition) != null) throw invalidPolicies();
            }
            if (index.size() != 40 || !policies.keySet().equals(PRODUCTION.keySet())) throw invalidPolicies();
            for (var entry : policies.entrySet()) {
                Policy p = Objects.requireNonNull(entry.getValue());
                var definition = index.get(entry.getKey());
                if (!entry.getKey().equals(p.apiName()) || definition == null
                        || p.parameterShape() == null || p.dateAxis() == null || p.planningMode() == null
                        || p.ruleKind() == null || !text(p.dateLabel()) || !text(p.outputDateColumn())
                        || !text(p.documentationNote()) || !text(p.officialUrl())
                        || p.documentationCheckedOn() == null || !text(p.policyVersion())
                        || definition.columns().stream().noneMatch(c -> c.name().equals(p.outputDateColumn()))) throw invalidPolicies();
                boolean stock = definition.parameters().stream().anyMatch(v -> v.name().equals("ts_code")
                        && v.type() == ParameterType.TS_CODE && v.required());
                if (stock != (p.parameterShape() == ParameterShape.STOCK)
                        || (p.ruleKind() == RuleKind.ROW_LIMIT
                            ? p.documentedRowLimit() == null || p.documentedRowLimit() <= 0
                            : p.documentedRowLimit() != null)
                        || (p.sourceVerified() ? p.ruleKind() == RuleKind.UNKNOWN || !text(p.verificationEvidence())
                                             : p.verificationEvidence() != null)
                        || (p.planningMode() == PlanningMode.NATIVE_RANGE ? p.dailyParameter() != null
                            : !text(p.dailyParameter()) || p.splittable())
                        || p.splittable() != (p.planningMode() == PlanningMode.NATIVE_RANGE
                            && (p.ruleKind() == RuleKind.ROW_LIMIT || p.ruleKind() == RuleKind.UNKNOWN))
                        || (p.ruleKind() == RuleKind.RESPONSE_ONLY && p.planningMode() != PlanningMode.NATIVE_RANGE)
                        || (p.ruleKind() == RuleKind.CALENDAR_COVERAGE && !p.apiName().value().equals("trade_cal"))) throw invalidPolicies();
            }
            this.definitions = Map.copyOf(index);
            this.policies = Map.copyOf(policies);
        } catch (RuntimeException ignored) {
            throw invalidPolicies();
        }
    }

    static Map<ApiName, Policy> productionPolicies() { return PRODUCTION; }

    public Optional<BatchDownloadDescriptor> batchDescriptor(ApiName apiName) {
        Policy p = policies.get(apiName);
        if (p == null) return Optional.empty();
        var parameters = new ArrayList<ParameterDescriptor>();
        String scope = scopeParameter(p);
        if (scope != null) parameters.add(new ParameterDescriptor(scope,
                p.parameterShape() == ParameterShape.STOCK ? "股票代码" : "交易所", null,
                p.parameterShape() == ParameterShape.STOCK ? ParameterType.TS_CODE : ParameterType.ENUM,
                true, null, p.parameterShape() == ParameterShape.STOCK ? List.of() : EXCHANGES, null, null));
        String label = p.dateLabel().endsWith("日期") ? p.dateLabel().substring(0, p.dateLabel().length() - 2) : p.dateLabel();
        parameters.add(endpoint("start_date", label + "开始日期", "end_date"));
        parameters.add(endpoint("end_date", label + "结束日期", "start_date"));
        String reason = p.sourceVerified() ? null : "区间参数语义与完整性尚待真实接口验证"
                + (p.ruleKind() == RuleKind.UNKNOWN ? "；尚无可确认的完整提取依据" : "");
        var rule = !p.sourceVerified() ? new CompletenessRule(CompletenessRule.Kind.UNKNOWN, null, null)
                : new CompletenessRule(switch (p.ruleKind()) {
                    case ROW_LIMIT -> CompletenessRule.Kind.CONFIRMED_ROW_LIMIT;
                    case CALENDAR_COVERAGE -> CompletenessRule.Kind.VERIFIED_RULE;
                    case RESPONSE_ONLY -> CompletenessRule.Kind.RESPONSE_ONLY;
                    case UNKNOWN -> throw invalidPolicies();
                }, p.documentedRowLimit(), p.verificationEvidence() + "；" + p.officialUrl());
        return Optional.of(new BatchDownloadDescriptor(parameters, "start_date", "end_date", p.dateAxis(),
                p.dateLabel(), p.planningMode(), p.splittable(), p.sourceVerified() ? Availability.AVAILABLE
                : Availability.NEEDS_VERIFICATION, reason, p.policyVersion(), rule));
    }

    public List<DateRange> plan(ApiName apiName, Map<String, Object> params, BatchCallContext context) {
        Policy p = requirePolicy(apiName);
        DateRange range = inputRange(p, params, PARAM_INVALID);
        if (context == null || context.deadline() == null) throw failure(PARAM_INVALID);
        requireLocalConditions(p, params);
        if (!p.sourceVerified()) throw failure(BATCH_DOWNLOAD_UNAVAILABLE);
        check(context);
        if (p.planningMode() == PlanningMode.NATIVE_RANGE) return List.of(range);
        if (p.planningMode() == PlanningMode.TRADING_DAYS) {
            Policy calendar = requirePolicy(new ApiName("trade_cal"));
            if (!calendar.sourceVerified()) throw failure(BATCH_DOWNLOAD_UNAVAILABLE);
            String exchange = exchangeForStock(params.get("ts_code"));
            Map<String, Object> calendarParams = Map.of("exchange", exchange,
                    "start_date", format(range.start()), "end_date", format(range.end()));
            var envelope = client.execute(definitions.get(calendar.apiName()), calendarParams, context);
            var result = new ArrayList<DateRange>();
            for (LocalDate date : TushareTradeCalendar.openDays(range, exchange, envelope)) {
                check(context);
                result.add(new DateRange(date, date));
            }
            check(context);
            return List.copyOf(result);
        }
        var result = new ArrayList<DateRange>();
        for (LocalDate date = range.start();; date = date.plusDays(1)) {
            check(context);
            result.add(new DateRange(date, date));
            if (date.equals(range.end())) break;
        }
        return List.copyOf(result);
    }

    public Map<String, Object> sourceParameters(ApiName apiName, Map<String, Object> params, DateRange range) {
        Policy p = requirePolicy(apiName);
        DateRange requested = inputRange(p, params, PARAM_INVALID);
        if (range == null || range.start().isBefore(requested.start()) || range.end().isAfter(requested.end())) throw failure(PARAM_INVALID);
        requireLocalConditions(p, params);
        var result = new HashMap<String, Object>();
        String scope = scopeParameter(p);
        if (scope != null) result.put(scope, params.get(scope));
        if (p.planningMode() == PlanningMode.NATIVE_RANGE) {
            result.put("start_date", format(range.start())); result.put("end_date", format(range.end()));
        } else {
            // T03 probes a whole range locally; execution consumes only plan's single-day ranges.
            result.put(p.dailyParameter(), format(range.start()));
        }
        return Map.copyOf(result);
    }

    public BatchAssessment assess(ApiName apiName, DateRange range, DownloadEnvelope envelope) {
        Policy p = requirePolicy(apiName);
        if (range == null) throw failure(SOURCE_RANGE_MISMATCH);
        validateEnvelope(apiName, definitions.get(apiName).columns().stream().map(ColumnDefinition::name).toList(), envelope);
        Map<String, Object> params = envelope.params();
        if (p.planningMode() == PlanningMode.NATIVE_RANGE) {
            if (!inputRange(p, params, SOURCE_RANGE_MISMATCH).equals(range)) throw failure(SOURCE_RANGE_MISMATCH);
        } else {
            if (!range.start().equals(range.end()) || !params.keySet().equals(Set.of("ts_code", p.dailyParameter()))) throw failure(SOURCE_RANGE_MISMATCH);
            validateScope(p, params, SOURCE_RANGE_MISMATCH);
            if (!date(params.get(p.dailyParameter()), SOURCE_RANGE_MISMATCH).equals(range.start())) throw failure(SOURCE_RANGE_MISMATCH);
        }
        int dateIndex = envelope.fields().indexOf(p.outputDateColumn());
        String scope = scopeParameter(p);
        int scopeIndex = scope == null ? -1 : envelope.fields().indexOf(scope);
        for (List<Object> row : envelope.data()) {
            within(date(row.get(dateIndex), SOURCE_PAYLOAD_INVALID), range);
            if (scope != null && (scopeIndex < 0 || !params.get(scope).equals(row.get(scopeIndex)))) throw failure(SOURCE_RANGE_MISMATCH);
            if (p.ruleKind() == RuleKind.CALENDAR_COVERAGE) TushareTradeCalendar.isOpen(row.get(envelope.fields().indexOf("is_open")));
        }
        if (!p.sourceVerified() || p.ruleKind() == RuleKind.UNKNOWN) return BatchAssessment.UNKNOWN;
        if (p.ruleKind() == RuleKind.RESPONSE_ONLY) return BatchAssessment.RESPONSE_ONLY;
        if (p.ruleKind() == RuleKind.CALENDAR_COVERAGE) {
            TushareTradeCalendar.openDays(range, (String) params.get("exchange"), envelope);
            return BatchAssessment.COMPLETE;
        }
        return envelope.rowCount() < p.documentedRowLimit() ? BatchAssessment.COMPLETE : BatchAssessment.SPLIT_REQUIRED;
    }

    static void validateEnvelope(ApiName apiName, List<String> fields, DownloadEnvelope envelope) {
        if (envelope == null || envelope.status() != DownloadStatus.SUCCESS) throw failure(SOURCE_PAYLOAD_INVALID);
        if (!envelope.pluginId().value().equals("tushare_pro") || !envelope.apiName().equals(apiName)) throw failure(SOURCE_RANGE_MISMATCH);
        if (!envelope.fields().equals(fields) || envelope.rowCount() != envelope.data().size()
                || envelope.data().stream().anyMatch(row -> row.size() != fields.size())) throw failure(SOURCE_PAYLOAD_INVALID);
    }

    static LocalDate date(Object value, ErrorCode code) {
        if (!(value instanceof String text) || !text.matches("[0-9]{8}")) throw failure(code);
        try { return LocalDate.parse(text, DATE); }
        catch (DateTimeException ignored) { throw failure(code); }
    }
    static String format(LocalDate date) { return date.format(DATE); }
    static void within(LocalDate date, DateRange range) {
        if (date.isBefore(range.start()) || date.isAfter(range.end())) throw failure(SOURCE_RANGE_MISMATCH);
    }
    static TensorException failure(ErrorCode code) {
        String message = switch (code) {
            case PARAM_INVALID -> "Invalid Tushare range parameters";
            case BATCH_DOWNLOAD_UNAVAILABLE -> "Tushare range download is unavailable";
            case BATCH_COMPLETENESS_UNCONFIRMED -> "Tushare calendar coverage is unconfirmed";
            case SOURCE_PAYLOAD_INVALID -> "Invalid Tushare range response";
            case SOURCE_RANGE_MISMATCH -> "Tushare response does not match requested range";
            case EXECUTION_INTERRUPTED -> "Download task execution was interrupted";
            case TASK_LIMIT_EXCEEDED -> "Download task limit exceeded";
            default -> throw new IllegalArgumentException("Invalid Tushare batch error");
        };
        return code == SOURCE_PAYLOAD_INVALID || code == SOURCE_RANGE_MISMATCH
                ? new SourceException(code, message) : new BatchException(code, message);
    }

    private static final class BatchException extends TensorException {
        private BatchException(ErrorCode code, String message) { super(code, message); }
    }
    private Policy requirePolicy(ApiName apiName) {
        Policy p = policies.get(apiName);
        if (p == null) throw failure(BATCH_DOWNLOAD_UNAVAILABLE);
        return p;
    }
    private static DateRange inputRange(Policy p, Map<String, Object> params, ErrorCode code) {
        String scope = scopeParameter(p);
        Set<String> keys = scope == null ? Set.of("start_date", "end_date") : Set.of(scope, "start_date", "end_date");
        if (params == null || !params.keySet().equals(keys)) throw failure(code);
        validateScope(p, params, code);
        LocalDate start = date(params.get("start_date"), code), end = date(params.get("end_date"), code);
        if (start.isAfter(end)) throw failure(code);
        return new DateRange(start, end);
    }
    private static void validateScope(Policy p, Map<String, Object> params, ErrorCode code) {
        String scope = scopeParameter(p);
        if (scope == null) return;
        if (!(params.get(scope) instanceof String value)
                || (p.parameterShape() == ParameterShape.STOCK ? !value.matches("[A-Z0-9]+\\.[A-Z0-9]+") : !EXCHANGES.contains(value))) throw failure(code);
    }
    private static String scopeParameter(Policy p) {
        return switch (p.parameterShape()) { case STOCK -> "ts_code"; case DATES -> null; case EXCHANGE -> "exchange"; case EXCHANGE_ID -> "exchange_id"; };
    }
    private static void requireLocalConditions(Policy p, Map<String, Object> params) {
        if (p.planningMode() == PlanningMode.TRADING_DAYS) exchangeForStock(params.get("ts_code"));
        if (p.apiName().value().equals("trade_cal") && params.get("exchange").equals("BSE")) throw failure(BATCH_DOWNLOAD_UNAVAILABLE);
    }
    private static String exchangeForStock(Object stock) {
        String text = (String) stock;
        if (text.endsWith(".SH")) return "SSE";
        if (text.endsWith(".SZ")) return "SZSE";
        if (text.endsWith(".BJ")) return "SSE";
        throw failure(BATCH_DOWNLOAD_UNAVAILABLE);
    }
    private void check(BatchCallContext context) {
        if (Thread.currentThread().isInterrupted() || context.stopRequested()) throw failure(EXECUTION_INTERRUPTED);
        if (!clock.instant().isBefore(context.deadline())) throw failure(TASK_LIMIT_EXCEEDED);
    }
    private static ParameterDescriptor endpoint(String name, String label, String related) {
        return new ParameterDescriptor(name, label, null, ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, related);
    }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static IllegalArgumentException invalidPolicies() { return new IllegalArgumentException("Invalid Tushare batch policies"); }

    private static Map<ApiName, Policy> createPolicies() {
        var result = new LinkedHashMap<ApiName, Policy>();
        add(result,"daily",ParameterShape.STOCK,DateAxis.TRADE_DATE,6000L,27,"");
        add(result,"weekly",ParameterShape.STOCK,DateAxis.TRADE_DATE,6000L,144,"实际每周最后交易日");
        add(result,"monthly",ParameterShape.STOCK,DateAxis.TRADE_DATE,4500L,145,"实际每月最后交易日");
        add(result,"adj_factor",ParameterShape.STOCK,DateAxis.TRADE_DATE,null,28,"");
        add(result,"daily_basic",ParameterShape.STOCK,DateAxis.TRADE_DATE,6000L,32,"",
                "single-000001", "single-600000", "range", "lower-bound", "upper-bound", "range-600000", "lower-bound-600000", "upper-bound-600000", "cross-year");
        add(result,"stk_limit",ParameterShape.STOCK,DateAxis.TRADE_DATE,5800L,183,"",
                "single-000001", "single-600000", "range", "lower-bound", "upper-bound", "range-600000", "lower-bound-600000", "upper-bound-600000");
        add(result,"suspend_d",ParameterShape.STOCK,DateAxis.TRADE_DATE,null,214,"");
        add(result,"moneyflow",ParameterShape.STOCK,DateAxis.TRADE_DATE,6000L,170,"",
                "single-000001", "single-600000", "range", "lower-bound", "upper-bound", "range-600000", "lower-bound-600000", "upper-bound-600000");
        add(result,"margin",ParameterShape.EXCHANGE_ID,DateAxis.TRADE_DATE,4000L,58,"保留 exchange_id");
        add(result,"margin_detail",ParameterShape.STOCK,DateAxis.TRADE_DATE,6000L,59,"",
                "single-000001", "single-600000", "range", "lower-bound", "upper-bound", "range-600000", "lower-bound-600000", "upper-bound-600000");
        add(result,"block_trade",ParameterShape.STOCK,DateAxis.TRADE_DATE,1000L,161,"");
        add(result,"slb_len",ParameterShape.DATES,DateAxis.TRADE_DATE,5000L,331,"");
        add(result,"slb_sec",ParameterShape.STOCK,DateAxis.TRADE_DATE,5000L,332,"标停；保留未知起止及持续可访问承诺");
        add(result,"slb_sec_detail",ParameterShape.STOCK,DateAxis.TRADE_DATE,5000L,333,"标停；保留未知起止及持续可访问承诺");
        add(result,"trade_cal",ParameterShape.EXCHANGE,DateAxis.CALENDAR_DATE,null,26,"完整自然日覆盖；无猜测行数上限");
        add(result,"new_share",ParameterShape.DATES,DateAxis.ISSUE_DATE,2000L,123,"不是 issue_date 上市日期");
        add(result,"income",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,33,"");
        add(result,"balancesheet",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,36,"");
        add(result,"cashflow",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,44,"不是 f_ann_date");
        add(result,"fina_audit",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,80,"");
        add(result,"forecast",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,3500L,45,"");
        add(result,"express",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,46,"");
        add(result,"repurchase",ParameterShape.DATES,DateAxis.ANNOUNCEMENT_DATE,null,124,"默认 2000 不是区间硬上限");
        add(result,"stk_managers",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,193,"");
        add(result,"stk_holdernumber",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,3000L,166,"不是 enddate/end_date");
        add(result,"stk_holdertrade",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,3000L,175,"不是持股变动起止日");
        add(result,"pledge_detail",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,1000L,111,"不是质押 start_date/end_date");
        add(result,"fina_indicator",ParameterShape.STOCK,DateAxis.REPORT_PERIOD,100L,79,"");
        add(result,"fina_mainbz",ParameterShape.STOCK,DateAxis.REPORT_PERIOD,100L,81,"不传 type/period/ann_date");
        add(result,"top10_holders",ParameterShape.STOCK,DateAxis.REPORT_PERIOD,null,61,"");
        add(result,"top10_floatholders",ParameterShape.STOCK,DateAxis.REPORT_PERIOD,null,62,"");
        add(result,"top_list",ParameterShape.STOCK,DateAxis.TRADE_DATE,10000L,106,"逐交易日 trade_date");
        add(result,"dividend",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,2000L,103,"逐自然日 ann_date");
        add(result,"disclosure_date",ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,6000L,162,"逐自然日 ann_date，最新披露公告日");
        return Map.copyOf(result);
    }
    private static void add(Map<ApiName,Policy> result, String name, ParameterShape shape, DateAxis axis,
                            Long limit, int document, String note, String... sourceCases) {
        PlanningMode mode = switch (name) { case "top_list" -> PlanningMode.TRADING_DAYS; case "dividend", "disclosure_date" -> PlanningMode.CALENDAR_DAYS; default -> PlanningMode.NATIVE_RANGE; };
        String column = switch (axis) { case TRADE_DATE -> "trade_date"; case ANNOUNCEMENT_DATE -> "ann_date"; case REPORT_PERIOD -> "end_date"; case CALENDAR_DATE -> "cal_date"; case ISSUE_DATE -> "ipo_date"; };
        String label = name.equals("disclosure_date") ? "最新披露公告日" : switch (axis) { case TRADE_DATE -> "交易日期"; case ANNOUNCEMENT_DATE -> "公告日期"; case REPORT_PERIOD -> "报告期"; case CALENDAR_DATE -> "日历日期"; case ISSUE_DATE -> "上网发行日期"; };
        RuleKind rule = name.equals("trade_cal") ? RuleKind.CALENDAR_COVERAGE
                : RESPONSE_ONLY.contains(name) ? RuleKind.RESPONSE_ONLY : limit == null ? RuleKind.UNKNOWN : RuleKind.ROW_LIMIT;
        CandidateEvidence candidate = CANDIDATES.get(name);
        boolean withdrawn = Set.of("fina_indicator", "balancesheet", "cashflow", "repurchase").contains(name); // ISSUE-031: actual RANGE TASKs failed adaptation.
        boolean verified = !withdrawn && (sourceCases.length > 0 || candidate != null);
        String sourceRun = "issue018-t14-priority-source-20260913T092938Z";
        String evidence = sourceCases.length > 0 ? "docs/verification/ISSUE-018-range-acceptance.md#" + name + "；SOURCE " + sourceRun + "；"
                + String.join(",", Arrays.stream(sourceCases).map(suffix -> sourceRun + "-" + name + "-" + suffix).toList())
                : candidate == null ? null : candidate.reference(name);
        ApiName api = new ApiName(name);
        var p = new Policy(api,shape,axis,label,mode,column,mode == PlanningMode.NATIVE_RANGE ? null : column,
                mode == PlanningMode.NATIVE_RANGE && rule == RuleKind.ROW_LIMIT,rule,limit,
                (sourceCases.length > 0 ? "官网候选行数上限 " + limit + "；"
                        : rule == RuleKind.RESPONSE_ONLY ? "允许不完整的响应采集；上游完整性未确认；"
                        : rule == RuleKind.CALENDAR_COVERAGE ? "完整自然日覆盖；"
                        : name.startsWith("slb_") ? "已采用历史查询及工程阈值 " + limit + "；"
                        : "已采用工程阈值 " + limit + "；") + note,
                "https://tushare.pro/document/2?doc_id=" + document,
                LocalDate.of(2026,9,sourceCases.length > 0 ? 13 : 14),
                withdrawn ? "tushare-range-v3" : "tushare-range-v2",verified,verified ? evidence : null);
        if (result.put(api,p) != null) throw invalidPolicies();
    }

    private record CandidateEvidence(String decision, String source) {
        String reference(String apiName) {
            return decision + "；docs/verification/ISSUE-018-range-acceptance.md#" + apiName
                    + "；docs/verification/ISSUE-018-range-acceptance.json#" + source;
        }
    }

    private static Map<String, CandidateEvidence> candidateEvidence() {
        String issue019 = "docs/issues/proposals/ISSUE-019-documented-range-limits.md#决策记录";
        String issue020 = "docs/issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录";
        String issue021 = "docs/task-designs/ISSUE-021-design.md";
        String issue022 = "docs/task-designs/ISSUE-022-design.md";
        String issue023 = "docs/task-designs/ISSUE-023-design.md";
        String issue024 = "docs/issues/proposals/ISSUE-024-historical-support.md#决策记录";
        String issue025 = "docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录";
        return Map.ofEntries(
                evidence("daily", issue019, "issue019-source-20260913T122126Z-daily-000001-whole"),
                evidence("forecast", issue019, "issue019-source-20260913T122126Z-forecast-000001-whole"),
                evidence("dividend", issue019, "issue019-source-20260913T122126Z-dividend-000001-whole"),
                evidence("fina_mainbz", issue020, "issue026-mainbz-split-source-20260914T013736Z-000001-annual"),
                evidence("trade_cal", issue021, "issue021-source-20260913T135006Z-trade_cal-sse-whole"),
                evidence("margin", issue021, "issue021-source-20260913T135006Z-margin-sse-whole"),
                evidence("top_list", issue021, "issue021-bj-source-20260913T135704Z-920008-whole"),
                evidence("weekly", issue022, "issue022-source-20260913T141544Z-weekly-000001-whole"),
                evidence("monthly", issue022, "issue022-source-20260913T141544Z-monthly-000001-whole"),
                evidence("fina_indicator", issue022, "issue022-source-20260913T141544Z-indicator-000001-whole"),
                evidence("stk_holdernumber", issue022, "issue022-source-20260913T141544Z-holder-000001-whole"),
                evidence("new_share", issue022, "issue022-source-20260913T141544Z-ipo-whole"),
                evidence("block_trade", issue023, "issue023-source-20260913T144308Z-block-official"),
                evidence("disclosure_date", issue023, "issue023-source-20260913T144308Z-disclosure-official"),
                evidence("stk_holdertrade", issue023, "issue023-source-20260913T144308Z-holder-official"),
                evidence("pledge_detail", "docs/task-designs/ISSUE-023-design.md#质押非空样本决定2026-09-13", "issue023-source-20260913T144308Z-pledge-official"),
                evidence("slb_len", issue024, "issue024-source-20260913T153146Z-len-whole"),
                evidence("slb_sec", issue024, "issue024-source-20260913T153146Z-slb_sec-000001-whole"),
                evidence("slb_sec_detail", issue024, "issue024-source-20260913T153146Z-slb_sec_detail-000001-whole"),
                evidence("adj_factor", issue025, "issue025-source-20260913T162759Z-adj_factor-000001-whole"),
                evidence("suspend_d", issue025, "issue025-source-20260913T162759Z-suspend_d-000001-whole"),
                evidence("income", issue025, "issue025-source-20260913T162759Z-income-000001-whole"),
                evidence("balancesheet", issue025, "issue025-source-20260913T162759Z-balancesheet-000001-whole"),
                evidence("cashflow", issue025, "issue025-source-20260913T162759Z-cashflow-000001-whole"),
                evidence("fina_audit", issue025, "issue025-source-20260913T162759Z-fina_audit-000001-whole"),
                evidence("express", issue025, "issue025-source-20260913T162759Z-express-600000-whole"),
                evidence("repurchase", issue025, "issue025-source-20260913T162759Z-repurchase-all-whole"),
                evidence("stk_managers", issue025, "issue025-source-20260913T162759Z-stk_managers-000001-whole"),
                evidence("top10_holders", issue025, "issue025-source-20260913T162759Z-top10_holders-000001-whole"),
                evidence("top10_floatholders", issue025, "issue025-source-20260913T162759Z-top10_floatholders-000001-whole"));
    }

    private static Map.Entry<String, CandidateEvidence> evidence(String apiName, String decision, String caseId) {
        String runId = caseId.substring(0, caseId.indexOf('Z') + 1);
        return Map.entry(apiName, new CandidateEvidence(decision, runId + "/" + caseId));
    }
}
