package com.akkc.tensor.plugin.tushare.integrity;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.plugin.tushare.TushareConstants;
import java.time.ZoneId;
import java.util.*;

import static com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor.ScopeKind.*;

/** Fixed local inspection semantics, independent of download parameters and credentials. */
public final class TushareIntegrityPolicies {
    private static final String UNPROVEN = "缺少与所选范围同口径的可靠预期全集";
    private static final String HISTORY = "当前快照不保存所选历史窗口的完整版本";
    private static final Set<String> MARKET_APIS = Set.of("daily", "weekly", "monthly");
    private static final Map<String, Axis> AXES = axes();
    static final List<IntegrityDependency> MARKET_DEPENDENCIES = List.of(
            new IntegrityDependency(key("trade_cal"), List.of("exchange", "cal_date", "is_open"), "行情交易日历与周期边界"),
            new IntegrityDependency(key("stock_basic"), List.of("ts_code", "list_date"), "上市日期线索"),
            new IntegrityDependency(key("suspend_d"), List.of("ts_code", "trade_date", "suspend_timing", "suspend_type"), "停复牌解释线索"));

    private final Map<ApiName, IntegrityDescriptor> descriptors;
    private final Map<ApiName, List<IntegrityRule>> rules;

    public TushareIntegrityPolicies(List<DatasetDefinition> definitions,
            Map<ApiName, BatchDownloadDescriptor> batchDescriptors) {
        definitions = List.copyOf(definitions);
        batchDescriptors = Map.copyOf(batchDescriptors);
        var byKey = new LinkedHashMap<DatasetKey, DatasetDefinition>();
        for (var definition : definitions) {
            var key = definition.datasetKey();
            if (!key.pluginId().value().equals(TushareConstants.PLUGIN_ID)
                    || !AXES.containsKey(key.apiName().value()) || byKey.put(key, definition) != null)
                throw new IllegalArgumentException("Invalid Tushare integrity datasets");
        }
        if (byKey.size() != AXES.size()) throw new IllegalArgumentException("Expected exactly 40 Tushare integrity datasets");

        var descriptors = new LinkedHashMap<ApiName, IntegrityDescriptor>();
        var rules = new LinkedHashMap<ApiName, List<IntegrityRule>>();
        for (var definition : definitions) {
            var api = definition.datasetKey().apiName();
            var name = api.value();
            var axis = AXES.get(name);
            var scope = axis == Axis.NON_STOCK ? NON_STOCK : axis == Axis.SNAPSHOT ? STOCK_SNAPSHOT : STOCK_DATE;
            var dependencies = MARKET_APIS.contains(name) ? MARKET_DEPENDENCIES : List.<IntegrityDependency>of();
            var limitations = new ArrayList<String>();
            limitations.add("只检查本地数据；下载完成不证明覆盖");
            if (scope == STOCK_SNAPSHOT) limitations.add(HISTORY);
            if (scope == NON_STOCK) limitations.add("不按单只股票执行");
            if (name.equals("index_member_all")) limitations.add("入选窗口不等于窗口内全部有效成员");
            if (name.equals("new_share")) limitations.add("日期为空时范围无法确定");
            if (MARKET_APIS.contains(name)) limitations.add("参考日历、生命周期、停牌及发布时间证据不足；BJ 缺适用日历时不能以 SSE 替代");
            var batch = batchDescriptors.get(api);
            if (batch != null && batch.availability() != BatchDownloadDescriptor.Availability.AVAILABLE)
                limitations.add(batch.unavailableReason());

            String version = MARKET_APIS.contains(name) ? "2" : "1";
            List<IntegrityRule> implementations = List.of();
            if (scope != NON_STOCK) {
                var columns = new LinkedHashSet<>(definition.businessKey().fields());
                columns.add("ts_code");
                if (axis.field != null) columns.add(axis.field);
                var rule = new IntegrityRuleDescriptor("tushare.coverage." + name, version, definition.displayName() + "覆盖",
                        IntegrityRuleDescriptor.Dimension.COVERAGE, List.copyOf(columns), dependencies,
                        scope == STOCK_SNAPSHOT ? HISTORY : UNPROVEN);
                implementations = List.of(MARKET_APIS.contains(name)
                        ? new TushareMarketIntegrityRule(definition.datasetKey(), rule)
                        : new TushareIntegrityCoverageRule(definition.datasetKey(), rule,
                                scope == STOCK_SNAPSHOT ? "HISTORY_NOT_STORED" : "EXPECTED_SET_UNPROVEN"));
            }
            var descriptor = new IntegrityDescriptor(definition.datasetKey(), scope, scope == NON_STOCK ? null : "ts_code",
                    axis.field, axis.label, ZoneId.of("Asia/Shanghai"), version, dependencies,
                    implementations.stream().map(IntegrityRule::descriptor).toList(), limitations);
            IntegrityContracts.validate(descriptor, implementations, byKey);
            descriptors.put(api, descriptor);
            rules.put(api, implementations);
        }
        IntegrityContracts.validatePlugin(List.copyOf(descriptors.values()));
        this.descriptors = Collections.unmodifiableMap(descriptors);
        this.rules = Collections.unmodifiableMap(rules);
    }

    public String normalizeSymbol(String symbol) {
        if (symbol == null) throw new IllegalArgumentException("Invalid Tushare stock code");
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9]{6}[.](SH|SZ|BJ)")) throw new IllegalArgumentException("Invalid Tushare stock code");
        return normalized;
    }

    public Optional<IntegrityDescriptor> descriptor(ApiName api) {
        return Optional.ofNullable(descriptors.get(Objects.requireNonNull(api, "apiName")));
    }

    public List<IntegrityRule> rules(ApiName api) {
        return rules.getOrDefault(Objects.requireNonNull(api, "apiName"), List.of());
    }

    public List<IntegrityReadRequest> referenceReads(IntegrityScope scope) {
        return TushareMarketIntegrityRule.referenceReads(scope);
    }

    private static DatasetKey key(String api) {
        return new DatasetKey(PluginId.of(TushareConstants.PLUGIN_ID), ApiName.of(api));
    }

    private enum Axis {
        TRADE("trade_date", "交易日期"), ANNOUNCEMENT("ann_date", "公告日期"), END("end_date", "报告期或统计截止日"),
        IPO("ipo_date", "上网发行日期"), IN("in_date", "入选日期"), SNAPSHOT(null, "当前快照"), NON_STOCK(null, "非股票级范围");
        private final String field;
        private final String label;
        Axis(String field, String label) { this.field = field; this.label = label; }
    }

    private static Map<String, Axis> axes() {
        var axes = new LinkedHashMap<String, Axis>();
        for (String api : List.of("daily", "weekly", "monthly", "daily_basic", "stk_limit", "moneyflow", "margin_detail",
                "block_trade", "slb_sec", "slb_sec_detail", "top_list", "adj_factor", "suspend_d")) axes.put(api, Axis.TRADE);
        for (String api : List.of("forecast", "stk_holdernumber", "stk_holdertrade", "pledge_detail", "dividend", "disclosure_date",
                "income", "fina_audit", "express", "stk_managers", "balancesheet", "cashflow", "repurchase", "stk_rewards")) axes.put(api, Axis.ANNOUNCEMENT);
        for (String api : List.of("fina_mainbz", "top10_holders", "top10_floatholders", "fina_indicator", "pledge_stat")) axes.put(api, Axis.END);
        axes.put("new_share", Axis.IPO);
        axes.put("index_member_all", Axis.IN);
        for (String api : List.of("stock_basic", "stock_company")) axes.put(api, Axis.SNAPSHOT);
        for (String api : List.of("margin", "slb_len", "index_classify", "trade_cal")) axes.put(api, Axis.NON_STOCK);
        return Collections.unmodifiableMap(axes);
    }
}
