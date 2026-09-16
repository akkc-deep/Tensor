package com.akkc.tensor.plugin.tushare.batch;

import static com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor.*;
import static com.akkc.tensor.plugin.api.error.ErrorCode.*;

import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.constant.StringConstants;
import com.akkc.tensor.plugin.api.constant.ValidationConstants;
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
import com.akkc.tensor.plugin.tushare.TushareConstants;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Local range semantics. Documented candidates remain closed until source verification. */
public final class TushareBatchPolicies {
    private static final String STOCK_CODE_LABEL = "股票代码";
    private static final String EXCHANGE_LABEL = "交易所";
    private static final String START_DATE_LABEL_SUFFIX = "开始日期";
    private static final String END_DATE_LABEL_SUFFIX = "结束日期";
    private static final String UNVERIFIED_RANGE_REASON = "区间参数语义与完整性尚待真实接口验证";
    private static final String UNKNOWN_COMPLETENESS_REASON_SUFFIX = "；尚无可确认的完整提取依据";
    private static final String SHANGHAI_STOCK_SUFFIX = ".SH";
    private static final String SHENZHEN_STOCK_SUFFIX = ".SZ";
    private static final String BEIJING_STOCK_SUFFIX = ".BJ";
    private static final String WEEKLY_DATE_NOTE = "实际每周最后交易日";
    private static final String MONTHLY_DATE_NOTE = "实际每月最后交易日";
    private static final String CROSS_YEAR_CASE = "cross-year";
    private static final String EXCHANGE_ID_NOTE = "保留 exchange_id";
    private static final String CALENDAR_COVERAGE_NOTE = "完整自然日覆盖；无猜测行数上限";
    private static final String IPO_DATE_NOTE = "不是 issue_date 上市日期";
    private static final String CASHFLOW_DATE_NOTE = "不是 f_ann_date";
    private static final String REPURCHASE_LIMIT_NOTE = "默认 2000 不是区间硬上限";
    private static final String HOLDER_NUMBER_DATE_NOTE = "不是 enddate/end_date";
    private static final String HOLDER_TRADE_DATE_NOTE = "不是持股变动起止日";
    private static final String PLEDGE_DATE_NOTE = "不是质押 start_date/end_date";
    private static final String MAINBZ_PARAMETERS_NOTE = "不传 type/period/ann_date";
    private static final String TOP_LIST_DATE_NOTE = "逐交易日 trade_date";
    private static final String DIVIDEND_DATE_NOTE = "逐自然日 ann_date";
    private static final String DISCLOSURE_DATE_NOTE = "逐自然日 ann_date，最新披露公告日";
    private static final String DISCLOSURE_DATE_LABEL = "最新披露公告日";
    private static final String TRADE_DATE_LABEL = "交易日期";
    private static final String ANNOUNCEMENT_DATE_LABEL = "公告日期";
    private static final String REPORT_PERIOD_LABEL = "报告期";
    private static final String CALENDAR_DATE_LABEL = "日历日期";
    private static final String ISSUE_DATE_LABEL = "上网发行日期";
    private static final String RANGE_ACCEPTANCE_REFERENCE = "docs/verification/ISSUE-018-range-acceptance.md#";
    private static final String SOURCE_EVIDENCE_PREFIX = "；SOURCE ";
    private static final String DOCUMENTED_LIMIT_NOTE_PREFIX = "官网候选行数上限 ";
    private static final String RESPONSE_ONLY_NOTE = "允许不完整的响应采集；上游完整性未确认；";
    private static final String CALENDAR_RULE_NOTE = "完整自然日覆盖；";
    private static final String SECURITIES_LENDING_API_PREFIX = "slb_";
    private static final String HISTORICAL_LIMIT_NOTE_PREFIX = "已采用历史查询及工程阈值 ";
    private static final String ENGINEERING_LIMIT_NOTE_PREFIX = "已采用工程阈值 ";
    private static final String OFFICIAL_DOCUMENTATION_PREFIX = "https://tushare.pro/document/2?doc_id=";
    private static final String WITHDRAWN_POLICY_VERSION = "tushare-range-v3";
    private static final String RANGE_POLICY_VERSION = "tushare-range-v2";
    private static final String PLEDGE_SAMPLE_DECISION = "docs/task-designs/ISSUE-023-design.md#质押非空样本决定2026-09-13";
    private static final String PRIORITY_SOURCE_RUN = "issue018-t14-priority-source-20260913T092938Z";
    private static final String ROW_LIMITS_DECISION = "docs/issues/proposals/ISSUE-019-documented-range-limits.md#决策记录";
    private static final String MAINBZ_DEFAULT_TYPE_DECISION = "docs/issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录";
    private static final String EXCHANGE_SCOPE_DECISION = "docs/task-designs/ISSUE-021-design.md";
    private static final String DATE_AXIS_DECISION = "docs/task-designs/ISSUE-022-design.md";
    private static final String DAILY_PLANNING_DECISION = "docs/task-designs/ISSUE-023-design.md";
    private static final String HISTORICAL_SUPPORT_DECISION = "docs/issues/proposals/ISSUE-024-historical-support.md#决策记录";
    private static final String EXTRACTION_CONTRACTS_DECISION = "docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录";
    private static final String DAILY_VERIFICATION_CASE = "issue019-source-20260913T122126Z-daily-000001-whole";
    private static final String FORECAST_VERIFICATION_CASE = "issue019-source-20260913T122126Z-forecast-000001-whole";
    private static final String DIVIDEND_VERIFICATION_CASE = "issue019-source-20260913T122126Z-dividend-000001-whole";
    private static final String FINA_MAINBZ_VERIFICATION_CASE = "issue026-mainbz-split-source-20260914T013736Z-000001-annual";
    private static final String TRADE_CAL_VERIFICATION_CASE = "issue021-source-20260913T135006Z-trade_cal-sse-whole";
    private static final String MARGIN_VERIFICATION_CASE = "issue021-source-20260913T135006Z-margin-sse-whole";
    private static final String TOP_LIST_VERIFICATION_CASE = "issue021-bj-source-20260913T135704Z-920008-whole";
    private static final String WEEKLY_VERIFICATION_CASE = "issue022-source-20260913T141544Z-weekly-000001-whole";
    private static final String MONTHLY_VERIFICATION_CASE = "issue022-source-20260913T141544Z-monthly-000001-whole";
    private static final String FINA_INDICATOR_VERIFICATION_CASE = "issue022-source-20260913T141544Z-indicator-000001-whole";
    private static final String STK_HOLDERNUMBER_VERIFICATION_CASE = "issue022-source-20260913T141544Z-holder-000001-whole";
    private static final String NEW_SHARE_VERIFICATION_CASE = "issue022-source-20260913T141544Z-ipo-whole";
    private static final String BLOCK_TRADE_VERIFICATION_CASE = "issue023-source-20260913T144308Z-block-official";
    private static final String DISCLOSURE_DATE_VERIFICATION_CASE = "issue023-source-20260913T144308Z-disclosure-official";
    private static final String STK_HOLDERTRADE_VERIFICATION_CASE = "issue023-source-20260913T144308Z-holder-official";
    private static final String PLEDGE_DETAIL_VERIFICATION_CASE = "issue023-source-20260913T144308Z-pledge-official";
    private static final String SLB_LEN_VERIFICATION_CASE = "issue024-source-20260913T153146Z-len-whole";
    private static final String SLB_SEC_VERIFICATION_CASE = "issue024-source-20260913T153146Z-slb_sec-000001-whole";
    private static final String SLB_SEC_DETAIL_VERIFICATION_CASE = "issue024-source-20260913T153146Z-slb_sec_detail-000001-whole";
    private static final String ADJ_FACTOR_VERIFICATION_CASE = "issue025-source-20260913T162759Z-adj_factor-000001-whole";
    private static final String SUSPEND_D_VERIFICATION_CASE = "issue025-source-20260913T162759Z-suspend_d-000001-whole";
    private static final String INCOME_VERIFICATION_CASE = "issue025-source-20260913T162759Z-income-000001-whole";
    private static final String BALANCESHEET_VERIFICATION_CASE = "issue025-source-20260913T162759Z-balancesheet-000001-whole";
    private static final String CASHFLOW_VERIFICATION_CASE = "issue025-source-20260913T162759Z-cashflow-000001-whole";
    private static final String FINA_AUDIT_VERIFICATION_CASE = "issue025-source-20260913T162759Z-fina_audit-000001-whole";
    private static final String EXPRESS_VERIFICATION_CASE = "issue025-source-20260913T162759Z-express-600000-whole";
    private static final String REPURCHASE_VERIFICATION_CASE = "issue025-source-20260913T162759Z-repurchase-all-whole";
    private static final String STK_MANAGERS_VERIFICATION_CASE = "issue025-source-20260913T162759Z-stk_managers-000001-whole";
    private static final String TOP10_HOLDERS_VERIFICATION_CASE = "issue025-source-20260913T162759Z-top10_holders-000001-whole";
    private static final String TOP10_FLOATHOLDERS_VERIFICATION_CASE = "issue025-source-20260913T162759Z-top10_floatholders-000001-whole";
    private static final String RANGE_ACCEPTANCE_JSON_REFERENCE = "docs/verification/ISSUE-018-range-acceptance.json#";

    private static final String DATE_LABEL_SUFFIX = "日期";

    private static final String EVIDENCE_SEPARATOR = "；";
    private static final String SOURCE_ID_SEPARATOR = "-";
    private static final String SUSPENDED_SUPPORT_NOTE = "标停；保留未知起止及持续可访问承诺";
    private static final String SINGLE_000001_CASE = "single-000001";
    private static final String SINGLE_600000_CASE = "single-600000";
    private static final String RANGE_CASE = "range";
    private static final String LOWER_BOUND_CASE = "lower-bound";
    private static final String UPPER_BOUND_CASE = "upper-bound";
    private static final String RANGE_600000_CASE = "range-600000";
    private static final String LOWER_BOUND_600000_CASE = "lower-bound-600000";
    private static final String UPPER_BOUND_600000_CASE = "upper-bound-600000";

    enum ParameterShape { STOCK, DATES, EXCHANGE, EXCHANGE_ID }
    enum RuleKind { ROW_LIMIT, CALENDAR_COVERAGE, RESPONSE_ONLY, UNKNOWN }
    record Policy(ApiName apiName, ParameterShape parameterShape,
                  DateAxis dateAxis, String dateLabel, PlanningMode planningMode,
                  String outputDateColumn, String dailyParameter, boolean splittable,
                  RuleKind ruleKind, Long documentedRowLimit, String documentationNote,
                  String officialUrl, LocalDate documentationCheckedOn,
                  String policyVersion, boolean sourceVerified, String verificationEvidence) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(ValidationConstants.DATE_FORMAT, Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final List<String> EXCHANGES = List.of(TushareConstants.SSE, TushareConstants.SZSE, TushareConstants.BSE);
    private static final Set<String> RESPONSE_ONLY = Set.of(TushareConstants.ADJ_FACTOR, TushareConstants.SUSPEND_D, TushareConstants.INCOME, TushareConstants.BALANCESHEET,
            TushareConstants.CASHFLOW, TushareConstants.FINA_AUDIT, TushareConstants.EXPRESS, TushareConstants.REPURCHASE, TushareConstants.STK_MANAGERS, TushareConstants.TOP10_HOLDERS, TushareConstants.TOP10_FLOATHOLDERS);
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
                if (!definition.datasetKey().pluginId().value().equals(TushareConstants.PLUGIN_ID)
                        || index.put(definition.datasetKey().apiName(), definition) != null) throw invalidPolicies();
            }
            if (index.size() != TushareConstants.API_COUNT || !policies.keySet().equals(PRODUCTION.keySet())) throw invalidPolicies();
            for (var entry : policies.entrySet()) {
                Policy p = Objects.requireNonNull(entry.getValue());
                var definition = index.get(entry.getKey());
                if (!entry.getKey().equals(p.apiName()) || definition == null
                        || p.parameterShape() == null || p.dateAxis() == null || p.planningMode() == null
                        || p.ruleKind() == null || !text(p.dateLabel()) || !text(p.outputDateColumn())
                        || !text(p.documentationNote()) || !text(p.officialUrl())
                        || p.documentationCheckedOn() == null || !text(p.policyVersion())
                        || definition.columns().stream().noneMatch(c -> c.name().equals(p.outputDateColumn()))) throw invalidPolicies();
                boolean stock = definition.parameters().stream().anyMatch(v -> v.name().equals(DatasetFields.TS_CODE)
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
                        || (p.ruleKind() == RuleKind.CALENDAR_COVERAGE && !p.apiName().value().equals(TushareConstants.TRADE_CAL))) throw invalidPolicies();
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
                p.parameterShape() == ParameterShape.STOCK ? STOCK_CODE_LABEL : EXCHANGE_LABEL, null,
                p.parameterShape() == ParameterShape.STOCK ? ParameterType.TS_CODE : ParameterType.ENUM,
                true, null, p.parameterShape() == ParameterShape.STOCK ? List.of() : EXCHANGES, null, null));
        String label = p.dateLabel().endsWith(DATE_LABEL_SUFFIX) ? p.dateLabel().substring(0, p.dateLabel().length() - DATE_LABEL_SUFFIX.length()) : p.dateLabel();
        parameters.add(endpoint(DatasetFields.START_DATE, label + START_DATE_LABEL_SUFFIX, DatasetFields.END_DATE));
        parameters.add(endpoint(DatasetFields.END_DATE, label + END_DATE_LABEL_SUFFIX, DatasetFields.START_DATE));
        String reason = p.sourceVerified() ? null : UNVERIFIED_RANGE_REASON
                + (p.ruleKind() == RuleKind.UNKNOWN ? UNKNOWN_COMPLETENESS_REASON_SUFFIX : StringConstants.EMPTY);
        var rule = !p.sourceVerified() ? new CompletenessRule(CompletenessRule.Kind.UNKNOWN, null, null)
                : new CompletenessRule(switch (p.ruleKind()) {
                    case ROW_LIMIT -> CompletenessRule.Kind.CONFIRMED_ROW_LIMIT;
                    case CALENDAR_COVERAGE -> CompletenessRule.Kind.VERIFIED_RULE;
                    case RESPONSE_ONLY -> CompletenessRule.Kind.RESPONSE_ONLY;
                    case UNKNOWN -> throw invalidPolicies();
                }, p.documentedRowLimit(), p.verificationEvidence() + EVIDENCE_SEPARATOR + p.officialUrl());
        return Optional.of(new BatchDownloadDescriptor(parameters, DatasetFields.START_DATE, DatasetFields.END_DATE, p.dateAxis(),
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
            Policy calendar = requirePolicy(new ApiName(TushareConstants.TRADE_CAL));
            if (!calendar.sourceVerified()) throw failure(BATCH_DOWNLOAD_UNAVAILABLE);
            String exchange = exchangeForStock(params.get(DatasetFields.TS_CODE));
            Map<String, Object> calendarParams = Map.of(DatasetFields.EXCHANGE, exchange,
                    DatasetFields.START_DATE, format(range.start()), DatasetFields.END_DATE, format(range.end()));
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
            result.put(DatasetFields.START_DATE, format(range.start())); result.put(DatasetFields.END_DATE, format(range.end()));
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
            if (!range.start().equals(range.end()) || !params.keySet().equals(Set.of(DatasetFields.TS_CODE, p.dailyParameter()))) throw failure(SOURCE_RANGE_MISMATCH);
            validateScope(p, params, SOURCE_RANGE_MISMATCH);
            if (!date(params.get(p.dailyParameter()), SOURCE_RANGE_MISMATCH).equals(range.start())) throw failure(SOURCE_RANGE_MISMATCH);
        }
        int dateIndex = envelope.fields().indexOf(p.outputDateColumn());
        String scope = scopeParameter(p);
        int scopeIndex = scope == null ? -1 : envelope.fields().indexOf(scope);
        for (List<Object> row : envelope.data()) {
            within(date(row.get(dateIndex), SOURCE_PAYLOAD_INVALID), range);
            if (scope != null && (scopeIndex < 0 || !params.get(scope).equals(row.get(scopeIndex)))) throw failure(SOURCE_RANGE_MISMATCH);
            if (p.ruleKind() == RuleKind.CALENDAR_COVERAGE) TushareTradeCalendar.isOpen(row.get(envelope.fields().indexOf(DatasetFields.IS_OPEN)));
        }
        if (!p.sourceVerified() || p.ruleKind() == RuleKind.UNKNOWN) return BatchAssessment.UNKNOWN;
        if (p.ruleKind() == RuleKind.RESPONSE_ONLY) return BatchAssessment.RESPONSE_ONLY;
        if (p.ruleKind() == RuleKind.CALENDAR_COVERAGE) {
            TushareTradeCalendar.openDays(range, (String) params.get(DatasetFields.EXCHANGE), envelope);
            return BatchAssessment.COMPLETE;
        }
        return envelope.rowCount() < p.documentedRowLimit() ? BatchAssessment.COMPLETE : BatchAssessment.SPLIT_REQUIRED;
    }

    static void validateEnvelope(ApiName apiName, List<String> fields, DownloadEnvelope envelope) {
        if (envelope == null || envelope.status() != DownloadStatus.SUCCESS) throw failure(SOURCE_PAYLOAD_INVALID);
        if (!envelope.pluginId().value().equals(TushareConstants.PLUGIN_ID) || !envelope.apiName().equals(apiName)) throw failure(SOURCE_RANGE_MISMATCH);
        if (!envelope.fields().equals(fields) || envelope.rowCount() != envelope.data().size()
                || envelope.data().stream().anyMatch(row -> row.size() != fields.size())) throw failure(SOURCE_PAYLOAD_INVALID);
    }

    static LocalDate date(Object value, ErrorCode code) {
        if (!(value instanceof String text) || !text.matches(ValidationConstants.DATE_REGEX)) throw failure(code);
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
        Set<String> keys = scope == null ? Set.of(DatasetFields.START_DATE, DatasetFields.END_DATE) : Set.of(scope, DatasetFields.START_DATE, DatasetFields.END_DATE);
        if (params == null || !params.keySet().equals(keys)) throw failure(code);
        validateScope(p, params, code);
        LocalDate start = date(params.get(DatasetFields.START_DATE), code), end = date(params.get(DatasetFields.END_DATE), code);
        if (start.isAfter(end)) throw failure(code);
        return new DateRange(start, end);
    }
    private static void validateScope(Policy p, Map<String, Object> params, ErrorCode code) {
        String scope = scopeParameter(p);
        if (scope == null) return;
        if (!(params.get(scope) instanceof String value)
                || (p.parameterShape() == ParameterShape.STOCK ? !value.matches(ValidationConstants.TS_CODE_REGEX) : !EXCHANGES.contains(value))) throw failure(code);
    }
    private static String scopeParameter(Policy p) {
        return switch (p.parameterShape()) { case STOCK -> DatasetFields.TS_CODE; case DATES -> null; case EXCHANGE -> DatasetFields.EXCHANGE; case EXCHANGE_ID -> DatasetFields.EXCHANGE_ID; };
    }
    private static void requireLocalConditions(Policy p, Map<String, Object> params) {
        if (p.planningMode() == PlanningMode.TRADING_DAYS) exchangeForStock(params.get(DatasetFields.TS_CODE));
        if (p.apiName().value().equals(TushareConstants.TRADE_CAL) && params.get(DatasetFields.EXCHANGE).equals(TushareConstants.BSE)) throw failure(BATCH_DOWNLOAD_UNAVAILABLE);
    }
    private static String exchangeForStock(Object stock) {
        String text = (String) stock;
        if (text.endsWith(SHANGHAI_STOCK_SUFFIX)) return TushareConstants.SSE;
        if (text.endsWith(SHENZHEN_STOCK_SUFFIX)) return TushareConstants.SZSE;
        if (text.endsWith(BEIJING_STOCK_SUFFIX)) return TushareConstants.SSE;
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
        add(result,TushareConstants.DAILY,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.DAILY_ROW_LIMIT,27,StringConstants.EMPTY);
        add(result,TushareConstants.WEEKLY,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.WEEKLY_ROW_LIMIT,144,WEEKLY_DATE_NOTE);
        add(result,TushareConstants.MONTHLY,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.MONTHLY_ROW_LIMIT,145,MONTHLY_DATE_NOTE);
        add(result,TushareConstants.ADJ_FACTOR,ParameterShape.STOCK,DateAxis.TRADE_DATE,null,28,StringConstants.EMPTY);
        add(result,TushareConstants.DAILY_BASIC,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.DAILY_BASIC_ROW_LIMIT,32,StringConstants.EMPTY,
                SINGLE_000001_CASE, SINGLE_600000_CASE, RANGE_CASE, LOWER_BOUND_CASE, UPPER_BOUND_CASE, RANGE_600000_CASE, LOWER_BOUND_600000_CASE, UPPER_BOUND_600000_CASE, CROSS_YEAR_CASE);
        add(result,TushareConstants.STK_LIMIT,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.STK_LIMIT_ROW_LIMIT,183,StringConstants.EMPTY,
                SINGLE_000001_CASE, SINGLE_600000_CASE, RANGE_CASE, LOWER_BOUND_CASE, UPPER_BOUND_CASE, RANGE_600000_CASE, LOWER_BOUND_600000_CASE, UPPER_BOUND_600000_CASE);
        add(result,TushareConstants.SUSPEND_D,ParameterShape.STOCK,DateAxis.TRADE_DATE,null,214,StringConstants.EMPTY);
        add(result,TushareConstants.MONEYFLOW,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.MONEYFLOW_ROW_LIMIT,170,StringConstants.EMPTY,
                SINGLE_000001_CASE, SINGLE_600000_CASE, RANGE_CASE, LOWER_BOUND_CASE, UPPER_BOUND_CASE, RANGE_600000_CASE, LOWER_BOUND_600000_CASE, UPPER_BOUND_600000_CASE);
        add(result,TushareConstants.MARGIN,ParameterShape.EXCHANGE_ID,DateAxis.TRADE_DATE,TushareConstants.MARGIN_ROW_LIMIT,58,EXCHANGE_ID_NOTE);
        add(result,TushareConstants.MARGIN_DETAIL,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.MARGIN_DETAIL_ROW_LIMIT,59,StringConstants.EMPTY,
                SINGLE_000001_CASE, SINGLE_600000_CASE, RANGE_CASE, LOWER_BOUND_CASE, UPPER_BOUND_CASE, RANGE_600000_CASE, LOWER_BOUND_600000_CASE, UPPER_BOUND_600000_CASE);
        add(result,TushareConstants.BLOCK_TRADE,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.BLOCK_TRADE_ROW_LIMIT,161,StringConstants.EMPTY);
        add(result,TushareConstants.SLB_LEN,ParameterShape.DATES,DateAxis.TRADE_DATE,TushareConstants.SLB_LEN_ROW_LIMIT,331,StringConstants.EMPTY);
        add(result,TushareConstants.SLB_SEC,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.SLB_SEC_ROW_LIMIT,332,SUSPENDED_SUPPORT_NOTE);
        add(result,TushareConstants.SLB_SEC_DETAIL,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.SLB_SEC_DETAIL_ROW_LIMIT,333,SUSPENDED_SUPPORT_NOTE);
        add(result,TushareConstants.TRADE_CAL,ParameterShape.EXCHANGE,DateAxis.CALENDAR_DATE,null,26,CALENDAR_COVERAGE_NOTE);
        add(result,TushareConstants.NEW_SHARE,ParameterShape.DATES,DateAxis.ISSUE_DATE,TushareConstants.NEW_SHARE_ROW_LIMIT,123,IPO_DATE_NOTE);
        add(result,TushareConstants.INCOME,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,33,StringConstants.EMPTY);
        add(result,TushareConstants.BALANCESHEET,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,36,StringConstants.EMPTY);
        add(result,TushareConstants.CASHFLOW,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,44,CASHFLOW_DATE_NOTE);
        add(result,TushareConstants.FINA_AUDIT,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,80,StringConstants.EMPTY);
        add(result,TushareConstants.FORECAST,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,TushareConstants.FORECAST_ROW_LIMIT,45,StringConstants.EMPTY);
        add(result,TushareConstants.EXPRESS,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,46,StringConstants.EMPTY);
        add(result,TushareConstants.REPURCHASE,ParameterShape.DATES,DateAxis.ANNOUNCEMENT_DATE,null,124,REPURCHASE_LIMIT_NOTE);
        add(result,TushareConstants.STK_MANAGERS,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,null,193,StringConstants.EMPTY);
        add(result,TushareConstants.STK_HOLDERNUMBER,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,TushareConstants.STK_HOLDERNUMBER_ROW_LIMIT,166,HOLDER_NUMBER_DATE_NOTE);
        add(result,TushareConstants.STK_HOLDERTRADE,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,TushareConstants.STK_HOLDERTRADE_ROW_LIMIT,175,HOLDER_TRADE_DATE_NOTE);
        add(result,TushareConstants.PLEDGE_DETAIL,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,TushareConstants.PLEDGE_DETAIL_ROW_LIMIT,111,PLEDGE_DATE_NOTE);
        add(result,TushareConstants.FINA_INDICATOR,ParameterShape.STOCK,DateAxis.REPORT_PERIOD,TushareConstants.FINA_INDICATOR_ROW_LIMIT,79,StringConstants.EMPTY);
        add(result,TushareConstants.FINA_MAINBZ,ParameterShape.STOCK,DateAxis.REPORT_PERIOD,TushareConstants.FINA_MAINBZ_ROW_LIMIT,81,MAINBZ_PARAMETERS_NOTE);
        add(result,TushareConstants.TOP10_HOLDERS,ParameterShape.STOCK,DateAxis.REPORT_PERIOD,null,61,StringConstants.EMPTY);
        add(result,TushareConstants.TOP10_FLOATHOLDERS,ParameterShape.STOCK,DateAxis.REPORT_PERIOD,null,62,StringConstants.EMPTY);
        add(result,TushareConstants.TOP_LIST,ParameterShape.STOCK,DateAxis.TRADE_DATE,TushareConstants.TOP_LIST_ROW_LIMIT,106,TOP_LIST_DATE_NOTE);
        add(result,TushareConstants.DIVIDEND,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,TushareConstants.DIVIDEND_ROW_LIMIT,103,DIVIDEND_DATE_NOTE);
        add(result,TushareConstants.DISCLOSURE_DATE,ParameterShape.STOCK,DateAxis.ANNOUNCEMENT_DATE,TushareConstants.DISCLOSURE_DATE_ROW_LIMIT,162,DISCLOSURE_DATE_NOTE);
        return Map.copyOf(result);
    }
    private static void add(Map<ApiName,Policy> result, String name, ParameterShape shape, DateAxis axis,
                            Long limit, int document, String note, String... sourceCases) {
        PlanningMode mode = switch (name) { case TushareConstants.TOP_LIST -> PlanningMode.TRADING_DAYS; case TushareConstants.DIVIDEND, TushareConstants.DISCLOSURE_DATE -> PlanningMode.CALENDAR_DAYS; default -> PlanningMode.NATIVE_RANGE; };
        String column = switch (axis) { case TRADE_DATE -> DatasetFields.TRADE_DATE; case ANNOUNCEMENT_DATE -> DatasetFields.ANN_DATE; case REPORT_PERIOD -> DatasetFields.END_DATE; case CALENDAR_DATE -> DatasetFields.CAL_DATE; case ISSUE_DATE -> DatasetFields.IPO_DATE; };
        String label = name.equals(TushareConstants.DISCLOSURE_DATE) ? DISCLOSURE_DATE_LABEL : switch (axis) { case TRADE_DATE -> TRADE_DATE_LABEL; case ANNOUNCEMENT_DATE -> ANNOUNCEMENT_DATE_LABEL; case REPORT_PERIOD -> REPORT_PERIOD_LABEL; case CALENDAR_DATE -> CALENDAR_DATE_LABEL; case ISSUE_DATE -> ISSUE_DATE_LABEL; };
        RuleKind rule = name.equals(TushareConstants.TRADE_CAL) ? RuleKind.CALENDAR_COVERAGE
                : RESPONSE_ONLY.contains(name) ? RuleKind.RESPONSE_ONLY : limit == null ? RuleKind.UNKNOWN : RuleKind.ROW_LIMIT;
        CandidateEvidence candidate = CANDIDATES.get(name);
        boolean withdrawn = Set.of(TushareConstants.FINA_INDICATOR, TushareConstants.BALANCESHEET, TushareConstants.CASHFLOW, TushareConstants.REPURCHASE).contains(name); // ISSUE-031: actual RANGE TASKs failed adaptation.
        boolean verified = !withdrawn && (sourceCases.length > 0 || candidate != null);
        String evidence = sourceCases.length > 0 ? RANGE_ACCEPTANCE_REFERENCE + name + SOURCE_EVIDENCE_PREFIX + PRIORITY_SOURCE_RUN + EVIDENCE_SEPARATOR
                + String.join(StringConstants.COMMA, Arrays.stream(sourceCases).map(suffix -> PRIORITY_SOURCE_RUN + SOURCE_ID_SEPARATOR + name + SOURCE_ID_SEPARATOR + suffix).toList())
                : candidate == null ? null : candidate.reference(name);
        ApiName api = new ApiName(name);
        var p = new Policy(api,shape,axis,label,mode,column,mode == PlanningMode.NATIVE_RANGE ? null : column,
                mode == PlanningMode.NATIVE_RANGE && rule == RuleKind.ROW_LIMIT,rule,limit,
                (sourceCases.length > 0 ? DOCUMENTED_LIMIT_NOTE_PREFIX + limit + EVIDENCE_SEPARATOR
                        : rule == RuleKind.RESPONSE_ONLY ? RESPONSE_ONLY_NOTE
                        : rule == RuleKind.CALENDAR_COVERAGE ? CALENDAR_RULE_NOTE
                        : name.startsWith(SECURITIES_LENDING_API_PREFIX) ? HISTORICAL_LIMIT_NOTE_PREFIX + limit + EVIDENCE_SEPARATOR
                        : ENGINEERING_LIMIT_NOTE_PREFIX + limit + EVIDENCE_SEPARATOR) + note,
                OFFICIAL_DOCUMENTATION_PREFIX + document,
                LocalDate.of(2026,java.time.Month.SEPTEMBER.getValue(),sourceCases.length > 0 ? 13 : 14),
                withdrawn ? WITHDRAWN_POLICY_VERSION : RANGE_POLICY_VERSION,verified,verified ? evidence : null);
        if (result.put(api,p) != null) throw invalidPolicies();
    }

    private record CandidateEvidence(String decision, String source) {
        String reference(String apiName) {
            return decision + EVIDENCE_SEPARATOR + RANGE_ACCEPTANCE_REFERENCE + apiName
                    + EVIDENCE_SEPARATOR + RANGE_ACCEPTANCE_JSON_REFERENCE + source;
        }
    }

    private static Map<String, CandidateEvidence> candidateEvidence() {
        return Map.ofEntries(
                evidence(TushareConstants.DAILY, ROW_LIMITS_DECISION, DAILY_VERIFICATION_CASE),
                evidence(TushareConstants.FORECAST, ROW_LIMITS_DECISION, FORECAST_VERIFICATION_CASE),
                evidence(TushareConstants.DIVIDEND, ROW_LIMITS_DECISION, DIVIDEND_VERIFICATION_CASE),
                evidence(TushareConstants.FINA_MAINBZ, MAINBZ_DEFAULT_TYPE_DECISION, FINA_MAINBZ_VERIFICATION_CASE),
                evidence(TushareConstants.TRADE_CAL, EXCHANGE_SCOPE_DECISION, TRADE_CAL_VERIFICATION_CASE),
                evidence(TushareConstants.MARGIN, EXCHANGE_SCOPE_DECISION, MARGIN_VERIFICATION_CASE),
                evidence(TushareConstants.TOP_LIST, EXCHANGE_SCOPE_DECISION, TOP_LIST_VERIFICATION_CASE),
                evidence(TushareConstants.WEEKLY, DATE_AXIS_DECISION, WEEKLY_VERIFICATION_CASE),
                evidence(TushareConstants.MONTHLY, DATE_AXIS_DECISION, MONTHLY_VERIFICATION_CASE),
                evidence(TushareConstants.FINA_INDICATOR, DATE_AXIS_DECISION, FINA_INDICATOR_VERIFICATION_CASE),
                evidence(TushareConstants.STK_HOLDERNUMBER, DATE_AXIS_DECISION, STK_HOLDERNUMBER_VERIFICATION_CASE),
                evidence(TushareConstants.NEW_SHARE, DATE_AXIS_DECISION, NEW_SHARE_VERIFICATION_CASE),
                evidence(TushareConstants.BLOCK_TRADE, DAILY_PLANNING_DECISION, BLOCK_TRADE_VERIFICATION_CASE),
                evidence(TushareConstants.DISCLOSURE_DATE, DAILY_PLANNING_DECISION, DISCLOSURE_DATE_VERIFICATION_CASE),
                evidence(TushareConstants.STK_HOLDERTRADE, DAILY_PLANNING_DECISION, STK_HOLDERTRADE_VERIFICATION_CASE),
                evidence(TushareConstants.PLEDGE_DETAIL, PLEDGE_SAMPLE_DECISION, PLEDGE_DETAIL_VERIFICATION_CASE),
                evidence(TushareConstants.SLB_LEN, HISTORICAL_SUPPORT_DECISION, SLB_LEN_VERIFICATION_CASE),
                evidence(TushareConstants.SLB_SEC, HISTORICAL_SUPPORT_DECISION, SLB_SEC_VERIFICATION_CASE),
                evidence(TushareConstants.SLB_SEC_DETAIL, HISTORICAL_SUPPORT_DECISION, SLB_SEC_DETAIL_VERIFICATION_CASE),
                evidence(TushareConstants.ADJ_FACTOR, EXTRACTION_CONTRACTS_DECISION, ADJ_FACTOR_VERIFICATION_CASE),
                evidence(TushareConstants.SUSPEND_D, EXTRACTION_CONTRACTS_DECISION, SUSPEND_D_VERIFICATION_CASE),
                evidence(TushareConstants.INCOME, EXTRACTION_CONTRACTS_DECISION, INCOME_VERIFICATION_CASE),
                evidence(TushareConstants.BALANCESHEET, EXTRACTION_CONTRACTS_DECISION, BALANCESHEET_VERIFICATION_CASE),
                evidence(TushareConstants.CASHFLOW, EXTRACTION_CONTRACTS_DECISION, CASHFLOW_VERIFICATION_CASE),
                evidence(TushareConstants.FINA_AUDIT, EXTRACTION_CONTRACTS_DECISION, FINA_AUDIT_VERIFICATION_CASE),
                evidence(TushareConstants.EXPRESS, EXTRACTION_CONTRACTS_DECISION, EXPRESS_VERIFICATION_CASE),
                evidence(TushareConstants.REPURCHASE, EXTRACTION_CONTRACTS_DECISION, REPURCHASE_VERIFICATION_CASE),
                evidence(TushareConstants.STK_MANAGERS, EXTRACTION_CONTRACTS_DECISION, STK_MANAGERS_VERIFICATION_CASE),
                evidence(TushareConstants.TOP10_HOLDERS, EXTRACTION_CONTRACTS_DECISION, TOP10_HOLDERS_VERIFICATION_CASE),
                evidence(TushareConstants.TOP10_FLOATHOLDERS, EXTRACTION_CONTRACTS_DECISION, TOP10_FLOATHOLDERS_VERIFICATION_CASE));
    }

    private static Map.Entry<String, CandidateEvidence> evidence(String apiName, String decision, String caseId) {
        String runId = caseId.substring(0, caseId.indexOf('Z') + 1);
        return Map.entry(apiName, new CandidateEvidence(decision, runId + StringConstants.SLASH + caseId));
    }
}
