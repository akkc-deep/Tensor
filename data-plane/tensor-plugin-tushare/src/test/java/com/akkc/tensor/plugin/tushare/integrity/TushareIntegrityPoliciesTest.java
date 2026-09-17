package com.akkc.tensor.plugin.tushare.integrity;

import com.akkc.tensor.plugin.api.IntegrityCheckSupport;
import com.akkc.tensor.plugin.api.dataset.*;
import com.akkc.tensor.plugin.api.download.batch.BatchDownloadDescriptor;
import com.akkc.tensor.plugin.api.integrity.*;
import com.akkc.tensor.plugin.api.model.*;
import com.akkc.tensor.core.integrity.IntegrityCheckJson;
import com.akkc.tensor.plugin.tushare.TushareProPlugin;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static com.akkc.tensor.plugin.api.integrity.IntegrityDescriptor.ScopeKind.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TushareIntegrityPoliciesTest {
    private static final List<DatasetDefinition> DEFINITIONS = new DatasetDefinitionLoader().loadAll(
            new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml");
    private static final Map<String, String> AXES = Map.ofEntries(
            Map.entry("adj_factor", "trade_date"), Map.entry("balancesheet", "ann_date"),
            Map.entry("block_trade", "trade_date"), Map.entry("cashflow", "ann_date"),
            Map.entry("daily", "trade_date"), Map.entry("daily_basic", "trade_date"),
            Map.entry("disclosure_date", "ann_date"), Map.entry("dividend", "ann_date"),
            Map.entry("express", "ann_date"), Map.entry("fina_audit", "ann_date"),
            Map.entry("fina_indicator", "end_date"), Map.entry("fina_mainbz", "end_date"),
            Map.entry("forecast", "ann_date"), Map.entry("income", "ann_date"),
            Map.entry("index_classify", "NON_STOCK"), Map.entry("index_member_all", "in_date"),
            Map.entry("margin", "NON_STOCK"), Map.entry("margin_detail", "trade_date"),
            Map.entry("moneyflow", "trade_date"), Map.entry("monthly", "trade_date"),
            Map.entry("new_share", "ipo_date"), Map.entry("pledge_detail", "ann_date"),
            Map.entry("pledge_stat", "end_date"), Map.entry("repurchase", "ann_date"),
            Map.entry("slb_len", "NON_STOCK"), Map.entry("slb_sec", "trade_date"),
            Map.entry("slb_sec_detail", "trade_date"), Map.entry("stk_holdernumber", "ann_date"),
            Map.entry("stk_holdertrade", "ann_date"), Map.entry("stk_limit", "trade_date"),
            Map.entry("stk_managers", "ann_date"), Map.entry("stk_rewards", "ann_date"),
            Map.entry("stock_basic", "STOCK_SNAPSHOT"), Map.entry("stock_company", "STOCK_SNAPSHOT"),
            Map.entry("suspend_d", "trade_date"), Map.entry("top10_floatholders", "end_date"),
            Map.entry("top10_holders", "end_date"), Map.entry("top_list", "trade_date"),
            Map.entry("trade_cal", "NON_STOCK"), Map.entry("weekly", "trade_date"));
    private final TushareProClient client = mock(TushareProClient.class);

    static Stream<Arguments> axes() {
        return AXES.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    static Stream<Arguments> nonMarketAxes() {
        return axes().filter(arguments -> !Set.of("daily", "weekly", "monthly").contains(arguments.get()[0]));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("axes")
    void declaresTheIndependentDateAxisAndCompleteKeyForEveryYaml(String api, String axis) {
        IntegrityCheckSupport plugin = plugin();
        var definition = definition(api);
        var descriptor = plugin.integrityDescriptor(ApiName.of(api)).orElseThrow();
        assertThat(descriptor.datasetKey()).isEqualTo(definition.datasetKey());
        assertThat(descriptor.marketZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        String version = Set.of("daily", "weekly", "monthly").contains(api) ? "2" : "1";
        assertThat(descriptor.capabilityVersion()).isEqualTo(version);
        assertThat(descriptor.limitations()).contains("只检查本地数据；下载完成不证明覆盖");
        if (axis.equals("NON_STOCK")) {
            assertThat(descriptor.scopeKind()).isEqualTo(NON_STOCK);
            assertThat(descriptor.symbolField()).isNull();
            assertThat(descriptor.dateField()).isNull();
            assertThat(descriptor.rules()).isEmpty();
            assertThat(plugin.integrityRules(ApiName.of(api))).isEmpty();
        } else {
            assertThat(descriptor.symbolField()).isEqualTo("ts_code");
            assertThat(descriptor.scopeKind()).isEqualTo(axis.equals("STOCK_SNAPSHOT") ? STOCK_SNAPSHOT : STOCK_DATE);
            assertThat(descriptor.dateField()).isEqualTo(axis.equals("STOCK_SNAPSHOT") ? null : axis);
            assertThat(descriptor.rules()).singleElement().satisfies(rule -> {
                assertThat(rule.ruleId()).isEqualTo("tushare.coverage." + api);
                assertThat(rule.version()).isEqualTo(version);
                assertThat(rule.dimension()).isEqualTo(IntegrityRuleDescriptor.Dimension.COVERAGE);
                assertThat(rule.requiredColumns()).containsAll(definition.businessKey().fields()).contains("ts_code");
                if (!axis.equals("STOCK_SNAPSHOT")) assertThat(rule.requiredColumns()).contains(axis);
            });
        }
        IntegrityContracts.validate(descriptor, plugin.integrityRules(ApiName.of(api)), definitionMap());
        verifyNoInteractions(client);
    }

    @Test
    void matchesManifestAndYamlExactlyAndRetainsInputOrder() throws Exception {
        var plugin = plugin();
        var root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("docs/data-template/manifest.json"))) root = root.getParent();
        assertThat(root).isNotNull();
        var manifest = new ObjectMapper().readTree(root.resolve("docs/data-template/manifest.json").toFile());
        var names = new ArrayList<String>();
        manifest.path("interfaces").forEach(entry -> names.add(entry.path("api_name").asText()));
        assertThat(names).hasSize(40).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(AXES.keySet());
        assertThat(DEFINITIONS).hasSize(40).extracting(d -> d.datasetKey().apiName().value())
                .containsExactlyInAnyOrderElementsOf(names);
        var descriptors = DEFINITIONS.stream().map(d -> plugin.integrityDescriptor(d.datasetKey().apiName()).orElseThrow()).toList();
        IntegrityContracts.validatePlugin(descriptors);
        assertThat(descriptors.stream().flatMap(d -> d.rules().stream())).hasSize(36);
        assertThat(descriptors.stream().filter(d -> d.scopeKind() == STOCK_SNAPSHOT)).hasSize(2);
        assertThat(descriptors.stream().filter(d -> d.scopeKind() == NON_STOCK)).hasSize(4);
        var reversed = new TushareProPlugin(properties(), client, DEFINITIONS.reversed());
        assertThat(reversed.descriptor().datasets()).containsExactlyElementsOf(plugin.descriptor().datasets().reversed());
        verifyNoInteractions(client);
    }

    @Test
    void eachMarketVersionChangeAltersTheFullCapabilityHashAndOldDescriptorStillReads() {
        var plugin = plugin();
        var json = new IntegrityCheckJson();
        var current = snapshots(plugin);
        String currentHash = json.capabilityHash(PluginId.of("tushare_pro"), current);
        for (String api : List.of("daily", "weekly", "monthly")) {
            var changed = new ArrayList<>(current);
            int index = DEFINITIONS.indexOf(definition(api));
            var snapshot = changed.get(index);
            var descriptor = snapshot.descriptor();
            var rule = descriptor.rules().getFirst();
            var oldRule = new IntegrityRuleDescriptor(rule.ruleId(), "1", rule.displayName(), rule.dimension(),
                    rule.requiredColumns(), rule.dependencies(), rule.description());
            var oldDescriptor = new IntegrityDescriptor(descriptor.datasetKey(), descriptor.scopeKind(),
                    descriptor.symbolField(), descriptor.dateField(), descriptor.dateLabel(), descriptor.marketZone(),
                    "1", descriptor.dependencies(), List.of(oldRule), descriptor.limitations());
            changed.set(index, new IntegrityCheckJson.ApiSnapshot(snapshot.definition(), oldDescriptor,
                    snapshot.referenceDefinitions(), snapshot.coreRules()));
            assertThat(json.capabilityHash(PluginId.of("tushare_pro"), changed)).isNotEqualTo(currentHash);
            assertThat(json.readDescriptor(json.readValue(json.write(oldDescriptor)))).isEqualTo(oldDescriptor);
        }
        verifyNoInteractions(client);
    }

    @ParameterizedTest(name = "{0}: unknown local coverage")
    @MethodSource("nonMarketAxes")
    void keepsUnprovenCoverageUnknownWithoutScanningOrInventingCounts(String api, String axis) {
        var plugin = plugin();
        var scope = scope(api);
        for (var rule : plugin.integrityRules(ApiName.of(api))) {
            var result = rule.evaluate(scope, NO_READS, issue -> { throw new AssertionError("No synthetic issues"); });
            assertThat(result.descriptor()).isEqualTo(rule.descriptor());
            assertThat(result.status()).isEqualTo(IntegrityStatus.UNKNOWN);
            assertThat(result.reasonCode()).isEqualTo(axis.equals("STOCK_SNAPSHOT")
                    ? "HISTORY_NOT_STORED" : "EXPECTED_SET_UNPROVEN");
            assertThat(result.statistics()).isEqualTo(new IntegrityStatistics(null, null, null, null, null, null, null));
            assertThat(result.statistics().coverageRate()).isNull();
            assertThat(result.evidence()).singleElement().satisfies(e -> {
                assertThat(e.source()).isEqualTo("tushare-local-policy:" + api);
                assertThat(e.ruleVersion()).isEqualTo("1");
                assertThat(e.range()).isEqualTo(scope.range());
                assertThat(e.readAt()).isEqualTo(scope.snapshotStartedAt());
                assertThat(e.summary()).isNotBlank();
            });
        }
        verifyNoInteractions(client);
    }

    @Test
    void declaresOnlyRealMarketReferenceColumnsAndExplainsDateLimitations() {
        var plugin = plugin();
        var dependencies = List.of(
                new IntegrityDependency(key("trade_cal"), List.of("exchange", "cal_date", "is_open"), "行情交易日历与周期边界"),
                new IntegrityDependency(key("stock_basic"), List.of("ts_code", "list_date"), "上市日期线索"),
                new IntegrityDependency(key("suspend_d"), List.of("ts_code", "trade_date", "suspend_timing", "suspend_type"), "停复牌解释线索"));
        for (String api : AXES.keySet()) {
            var descriptor = plugin.integrityDescriptor(ApiName.of(api)).orElseThrow();
            var expected = Set.of("daily", "weekly", "monthly").contains(api) ? dependencies : List.of();
            assertThat(descriptor.dependencies()).isEqualTo(expected);
            descriptor.rules().forEach(rule -> assertThat(rule.dependencies()).isEqualTo(expected));
            if (!expected.isEmpty()) {
                assertThat(String.join("；", descriptor.limitations())).contains("BJ", "日历", "停牌", "生命周期", "发布时间");
                assertThat(plugin.integrityReferenceReads(scope(api)))
                        .extracting(IntegrityReadRequest::datasetKey, IntegrityReadRequest::columns, IntegrityReadRequest::purpose)
                        .containsExactly(
                                tuple(dependencies.get(0).datasetKey(), dependencies.get(0).columns(), dependencies.get(0).purpose()),
                                tuple(dependencies.get(1).datasetKey(), dependencies.get(1).columns(), dependencies.get(1).purpose()),
                                tuple(dependencies.get(2).datasetKey(), dependencies.get(2).columns(), dependencies.get(2).purpose()));
            }
        }
        assertThat(plugin.integrityDescriptor(ApiName.of("index_member_all")).orElseThrow().limitations())
                .contains("入选窗口不等于窗口内全部有效成员");
        assertThat(plugin.integrityDescriptor(ApiName.of("new_share")).orElseThrow().limitations())
                .contains("日期为空时范围无法确定");
        verifyNoInteractions(client);
    }

    @Test
    void withdrawnRangeApisRemainLocallyDescribedWithTheirDownloadLimitations() {
        var plugin = plugin();
        for (String api : List.of("fina_indicator", "balancesheet", "cashflow", "repurchase")) {
            var batch = plugin.batchDescriptor(ApiName.of(api)).orElseThrow();
            assertThat(batch.availability()).isNotEqualTo(BatchDownloadDescriptor.Availability.AVAILABLE);
            var descriptor = plugin.integrityDescriptor(ApiName.of(api)).orElseThrow();
            assertThat(descriptor.scopeKind()).isEqualTo(STOCK_DATE);
            assertThat(descriptor.limitations()).contains(batch.unavailableReason());
        }
        assertThat(plugin.readiness().downloadAvailable()).isFalse();
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @CsvSource({"' 600000.sh ',600000.SH", "000001.sz,000001.SZ", "920008.bj,920008.BJ", "999999.SH,999999.SH"})
    void normalizesFormatWithoutRequiringLocalRows(String input, String expected) {
        assertThat(plugin().normalizeIntegritySymbol(input)).isEqualTo(expected);
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00001.SZ", "0000001.SZ", "000001", "000001.HK", "000001. SZ", "000001.SZ\nX", "０００００１.SZ"})
    void rejectsInvalidSymbols(String symbol) {
        assertThatThrownBy(() -> plugin().normalizeIntegritySymbol(symbol)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    @Test
    void unknownApisAreAbsentAndNullApisRejected() {
        var plugin = plugin();
        assertThat(plugin.integrityDescriptor(ApiName.of("unknown"))).isEmpty();
        assertThat(plugin.integrityRules(ApiName.of("unknown"))).isEmpty();
        assertThatThrownBy(() -> plugin.integrityDescriptor(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> plugin.integrityRules(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(client);
    }

    @Test
    void rejectsMissingDuplicateForeignAndInvalidDefinitionsAtConstruction() {
        assertThatThrownBy(() -> new TushareIntegrityPolicies(DEFINITIONS.subList(0, 39), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        var duplicate = new ArrayList<>(DEFINITIONS);
        duplicate.set(0, duplicate.get(1));
        assertThatThrownBy(() -> new TushareIntegrityPolicies(duplicate, Map.of())).isInstanceOf(IllegalArgumentException.class);
        var daily = definition("daily");
        var foreign = copy(daily, new DatasetKey(PluginId.of("other"), ApiName.of("daily")), daily.columns());
        assertInvalidReplacement(daily, foreign);
        var unknown = copy(daily, key("unknown"), daily.columns());
        assertInvalidReplacement(daily, unknown);
        var wrongDate = daily.columns().stream().map(c -> c.name().equals("trade_date")
                ? new ColumnDefinition(c.name(), c.label(), LogicalType.STRING, c.nullable(), c.displayOrder(), 8, null, null, List.of(), false) : c).toList();
        assertInvalidReplacement(daily, copy(daily, daily.datasetKey(), wrongDate));
        var stock = definition("stock_basic");
        var missingDependency = stock.columns().stream().filter(c -> !c.name().equals("list_date")).toList();
        assertInvalidReplacement(stock, copy(stock, stock.datasetKey(), missingDependency));
    }

    @Test
    void coverageRejectsMismatchedOrUnstartedScope() {
        var rule = plugin().integrityRules(ApiName.of("daily")).getFirst();
        var s = scope("daily");
        for (var invalid : List.of(scope("income"),
                new IntegrityScope(s.datasetKey(), null, s.startDate(), s.endDate(), s.acceptedAt(), s.snapshotStartedAt()),
                new IntegrityScope(s.datasetKey(), s.symbol(), s.startDate(), s.endDate(), s.acceptedAt(), null))) {
            assertThatThrownBy(() -> rule.evaluate(invalid, NO_READS, issue -> { throw new AssertionError(); }))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void assertInvalidReplacement(DatasetDefinition original, DatasetDefinition replacement) {
        var definitions = new ArrayList<>(DEFINITIONS);
        definitions.set(definitions.indexOf(original), replacement);
        assertThatThrownBy(() -> new TushareIntegrityPolicies(definitions, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private static DatasetDefinition copy(DatasetDefinition d, DatasetKey key, List<ColumnDefinition> columns) {
        return new DatasetDefinition(key, d.displayName(), d.category(), d.queryMode(), d.parameters(), TableName.from(key),
                columns, d.businessKey(), d.filters(), d.fixedColumn(), d.batchSize());
    }

    private TushareProPlugin plugin() { return new TushareProPlugin(properties(), client, DEFINITIONS); }
    private static TushareProperties properties() {
        return new TushareProperties(true, URI.create("https://integrity.invalid"), new TushareProperties.Credential(""),
                Duration.ofSeconds(1), Duration.ofSeconds(2), 1024);
    }
    private static DatasetDefinition definition(String api) {
        return DEFINITIONS.stream().filter(d -> d.datasetKey().equals(key(api))).findFirst().orElseThrow();
    }
    private static Map<DatasetKey, DatasetDefinition> definitionMap() {
        var result = new HashMap<DatasetKey, DatasetDefinition>();
        DEFINITIONS.forEach(d -> result.put(d.datasetKey(), d));
        return result;
    }
    private static List<IntegrityCheckJson.ApiSnapshot> snapshots(IntegrityCheckSupport plugin) {
        return DEFINITIONS.stream().map(definition -> {
            var descriptor = plugin.integrityDescriptor(definition.datasetKey().apiName()).orElseThrow();
            var references = descriptor.dependencies().stream().map(dependency -> definition(dependency.datasetKey().apiName().value())).toList();
            var core = descriptor.scopeKind() == NON_STOCK ? List.<IntegrityRuleDescriptor>of()
                    : IntegrityContracts.coreRules(definition);
            return new IntegrityCheckJson.ApiSnapshot(definition, descriptor, references, core);
        }).toList();
    }
    private static DatasetKey key(String api) { return new DatasetKey(PluginId.of("tushare_pro"), ApiName.of(api)); }
    private static IntegrityScope scope(String api) {
        return new IntegrityScope(key(api), "999999.SH", LocalDate.of(2020, 1, 1), LocalDate.of(2026, 9, 16),
                Instant.parse("2026-09-16T00:00:00Z"), Instant.parse("2026-09-16T00:00:01Z"));
    }
    private static final IntegrityContext NO_READS = new IntegrityContext() {
        public void scan(IntegrityReadRequest request, Consumer<List<Map<String, Object>>> rows) { throw new AssertionError("No scan"); }
        public IntegrityStatistics compare(IntegrityExpectedKeys expected) { throw new AssertionError("No compare"); }
    };
}
