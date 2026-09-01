package com.akkc.tensor.plugin.tushare.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TushareMetadataContractTest {
    private static final String DATASET_PATTERN = "classpath*:datasets/tushare_pro/*.yaml";
    private static final int EXPECTED_DATASETS = 49;
    private static final int EXPECTED_COLUMNS = 851;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Map<String, List<ExpectedParameter>> EXPECTED_PARAMETERS = expectedParameters();
    private static final Map<String, ExpectedBusinessKey> EXPECTED_BUSINESS_KEYS = expectedBusinessKeys();
    private static final Map<String, List<String>> EXPECTED_FILTERS = expectedFilters();

    private final ContractData contracts = loadContracts();

    @Test
    void hasExactManifestAndExpectationCoverage() {
        List<String> manifestApis = contracts.manifestEntries().stream().map(ManifestEntry::apiName).toList();
        List<String> manifestFilenames = contracts.manifestEntries().stream().map(ManifestEntry::filename).toList();
        List<String> loadedApis = contracts.definitions().stream()
                .map(definition -> definition.datasetKey().apiName().value())
                .toList();

        assertThat(manifestApis).as("manifest API names")
                .hasSize(EXPECTED_DATASETS)
                .doesNotContainNull()
                .allSatisfy(api -> assertThat(api).isNotBlank())
                .doesNotHaveDuplicates();
        assertThat(manifestFilenames).as("manifest filenames")
                .hasSize(EXPECTED_DATASETS)
                .doesNotContainNull()
                .allSatisfy(filename -> assertThat(filename).isNotBlank())
                .doesNotHaveDuplicates();
        assertThat(loadedApis).as("loaded Tushare APIs")
                .hasSize(EXPECTED_DATASETS)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(manifestApis);
        assertThat(EXPECTED_PARAMETERS.keySet()).as("parameter expectation APIs")
                .containsExactlyInAnyOrderElementsOf(manifestApis);
        assertThat(EXPECTED_BUSINESS_KEYS.keySet()).as("business-key expectation APIs")
                .containsExactlyInAnyOrderElementsOf(manifestApis);
        assertThat(EXPECTED_FILTERS.keySet()).as("filter expectation APIs")
                .containsExactlyInAnyOrderElementsOf(manifestApis);
        assertThat(contracts.definitions().stream().mapToInt(definition -> definition.columns().size()).sum())
                .as("total Tushare columns")
                .isEqualTo(EXPECTED_COLUMNS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("datasetDefinitions")
    void matchesIndependentContract(String apiName, DatasetDefinition definition) throws IOException {
        ManifestEntry manifest = manifestEntry(apiName);
        List<String> columnNames = definition.columns().stream().map(ColumnDefinition::name).toList();

        assertThat(definition.datasetKey().pluginId().value()).as("%s pluginId", apiName).isEqualTo("tushare_pro");
        assertThat(definition.datasetKey().apiName().value()).as("%s apiName", apiName).isEqualTo(apiName);
        assertThat(definition.tableName().value()).as("%s tableName", apiName).isEqualTo("tushare_pro__" + apiName);
        assertThat(definition.batchSize()).as("%s batchSize", apiName).isEqualTo(500);
        assertThat(manifest.filename()).as("%s manifest filename", apiName).isEqualTo(apiName + ".json");

        TemplateProjection template = readTemplate(contracts.repositoryRoot(), manifest);
        assertThat(template.apiName()).as("%s template api_name", apiName).isEqualTo(apiName);
        assertThat(columnNames).as("%s template field order", apiName).containsExactlyElementsOf(template.fields());

        definition.parameters().forEach(parameter -> assertParameterDefaults(apiName, parameter));
        List<ExpectedParameter> actualParameters = definition.parameters().stream()
                .map(parameter -> new ExpectedParameter(
                        parameter.name(), parameter.label(), parameter.type(), parameter.required(),
                        parameter.allowedValues(), parameter.relatedParameter()))
                .toList();
        assertThat(actualParameters).as("%s parameters", apiName)
                .containsExactlyElementsOf(EXPECTED_PARAMETERS.get(apiName));

        ExpectedBusinessKey actualBusinessKey = new ExpectedBusinessKey(
                definition.businessKey().mode(), definition.businessKey().fields());
        assertThat(actualBusinessKey).as("%s business key", apiName)
                .isEqualTo(EXPECTED_BUSINESS_KEYS.get(apiName));
        assertThat(columnNames).as("%s business-key references", apiName)
                .containsAll(definition.businessKey().fields());
        if (definition.businessKey().mode() == BusinessKeyMode.COMPOSITE) {
            definition.businessKey().fields().forEach(field -> assertThat(column(definition, field).nullable())
                    .as("%s composite key column %s nullable", apiName, field)
                    .isFalse());
        }

        assertThat(definition.filters()).extracting(FilterDefinition::field).as("%s filters", apiName)
                .containsExactlyElementsOf(EXPECTED_FILTERS.get(apiName));
        assertThat(columnNames).as("%s filter references", apiName)
                .containsAll(definition.filters().stream().map(FilterDefinition::field).toList());
    }

    Stream<Arguments> datasetDefinitions() {
        return contracts.definitions().stream().map(definition -> Arguments.of(
                definition.datasetKey().apiName().value(), definition));
    }

    private ManifestEntry manifestEntry(String apiName) {
        List<ManifestEntry> matches = contracts.manifestEntries().stream()
                .filter(entry -> entry.apiName().equals(apiName))
                .toList();
        assertThat(matches).as("manifest entry for %s", apiName).singleElement();
        return matches.getFirst();
    }

    private static void assertParameterDefaults(String apiName, ParameterDescriptor parameter) {
        assertThat(parameter.required()).as("%s parameter %s required", apiName, parameter.name()).isTrue();
        assertThat(parameter.description()).as("%s parameter %s description", apiName, parameter.name()).isNull();
        assertThat(parameter.defaultValue()).as("%s parameter %s defaultValue", apiName, parameter.name()).isNull();
        assertThat(parameter.pattern()).as("%s parameter %s pattern", apiName, parameter.name()).isNull();
        if (parameter.type() != ParameterType.ENUM) {
            assertThat(parameter.allowedValues()).as("%s parameter %s allowedValues", apiName, parameter.name()).isEmpty();
        }
    }

    private static ColumnDefinition column(DatasetDefinition definition, String field) {
        return definition.columns().stream()
                .filter(column -> column.name().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        definition.datasetKey().apiName().value() + " missing column " + field));
    }

    private static ContractData loadContracts() {
        Path repositoryRoot = findRepositoryRoot();
        try {
            List<ManifestEntry> manifestEntries = readManifest(repositoryRoot.resolve("docs/data-template/manifest.json"));
            List<DatasetDefinition> definitions = new DatasetDefinitionLoader().loadAll(
                    new PathMatchingResourcePatternResolver(), DATASET_PATTERN);
            return new ContractData(repositoryRoot, manifestEntries, definitions);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read Tushare contract inputs from " + repositoryRoot, exception);
        }
    }

    private static Path findRepositoryRoot() {
        Path start = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("docs/data-template/manifest.json"))
                    && Files.isRegularFile(candidate.resolve("data-plane/pom.xml"))) {
                return candidate;
            }
        }
        throw new AssertionError("Repository root with docs/data-template/manifest.json and data-plane/pom.xml"
                + " not found from " + start);
    }

    private static List<ManifestEntry> readManifest(Path manifestPath) throws IOException {
        assertThat(Files.isRegularFile(manifestPath)).as("manifest file %s", manifestPath).isTrue();
        JsonNode root = JSON.readTree(manifestPath.toFile());
        assertThat(root).as("manifest root").isNotNull();
        assertThat(root.isObject()).as("manifest root object").isTrue();
        JsonNode interfaces = root.path("interfaces");
        assertThat(interfaces.isArray()).as("manifest interfaces array").isTrue();

        List<ManifestEntry> entries = new ArrayList<>();
        for (int index = 0; index < interfaces.size(); index++) {
            JsonNode entry = interfaces.get(index);
            assertThat(entry.isObject()).as("manifest interfaces[%s] object", index).isTrue();
            entries.add(new ManifestEntry(
                    requiredText(entry, "api_name", index),
                    requiredText(entry, "filename", index)));
        }
        return List.copyOf(entries);
    }

    private static String requiredText(JsonNode entry, String field, int index) {
        JsonNode value = entry.path(field);
        assertThat(value.isTextual()).as("manifest interfaces[%s].%s string", index, field).isTrue();
        assertThat(value.textValue()).as("manifest interfaces[%s].%s", index, field).isNotBlank();
        return value.textValue();
    }

    private static TemplateProjection readTemplate(Path repositoryRoot, ManifestEntry manifest) throws IOException {
        Path templateDirectory = repositoryRoot.resolve("docs/data-template").normalize();
        Path templatePath = templateDirectory.resolve(manifest.filename()).normalize();
        assertThat(templatePath.startsWith(templateDirectory)).as("%s template path stays in docs/data-template", manifest.apiName())
                .isTrue();
        assertThat(Files.isRegularFile(templatePath)).as("%s template file %s", manifest.apiName(), templatePath).isTrue();

        try (JsonParser parser = JSON.getFactory().createParser(Files.newInputStream(templatePath))) {
            assertThat(parser.nextToken()).as("%s template root", manifest.apiName()).isEqualTo(JsonToken.START_OBJECT);
            String templateApi = null;
            List<String> fields = null;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                assertThat(token).as("%s template root property", manifest.apiName()).isEqualTo(JsonToken.FIELD_NAME);
                String property = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if ("api_name".equals(property)) {
                    assertThat(templateApi).as("%s duplicate template api_name", manifest.apiName()).isNull();
                    assertThat(valueToken).as("%s template api_name type", manifest.apiName()).isEqualTo(JsonToken.VALUE_STRING);
                    templateApi = parser.getText();
                    assertThat(templateApi).as("%s template api_name value", manifest.apiName()).isNotBlank();
                } else if ("fields".equals(property)) {
                    assertThat(fields).as("%s duplicate template fields", manifest.apiName()).isNull();
                    fields = readTemplateFields(parser, valueToken, manifest.apiName());
                } else {
                    parser.skipChildren();
                }
            }
            assertThat(parser.nextToken()).as("%s trailing template document", manifest.apiName()).isNull();
            assertThat(templateApi).as("%s template api_name", manifest.apiName()).isNotNull();
            assertThat(fields).as("%s template fields", manifest.apiName()).isNotNull();
            return new TemplateProjection(templateApi, fields);
        }
    }

    private static List<String> readTemplateFields(JsonParser parser, JsonToken token, String apiName) throws IOException {
        assertThat(token).as("%s template fields type", apiName).isEqualTo(JsonToken.START_ARRAY);
        List<String> fields = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            assertThat(parser.currentToken()).as("%s template field type", apiName).isEqualTo(JsonToken.VALUE_STRING);
            String field = parser.getText();
            assertThat(field).as("%s template field value", apiName).isNotBlank();
            assertThat(unique.add(field)).as("%s duplicate template field %s", apiName, field).isTrue();
            fields.add(field);
        }
        return List.copyOf(fields);
    }

    private static Map<String, List<ExpectedParameter>> expectedParameters() {
        Map<String, List<ExpectedParameter>> expected = new LinkedHashMap<>();
        addParameters(expected, List.of("stock_basic"),
                parameter("list_status", "上市状态", ParameterType.ENUM, List.of("L", "P", "D"), null));
        addParameters(expected, List.of("stock_company"),
                parameter("exchange", "交易所", ParameterType.ENUM, List.of("SSE", "SZSE", "BSE"), null));
        addParameters(expected, List.of("hs_const"),
                parameter("hs_type", "沪深港通类型", ParameterType.ENUM, List.of("SH", "SZ"), null));
        addParameters(expected, List.of("trade_cal"),
                parameter("exchange", "交易所", ParameterType.ENUM, List.of("SSE", "SZSE", "BSE"), null),
                parameter("start_date", "开始日期", ParameterType.DATE_RANGE_MEMBER, List.of(), "end_date"),
                parameter("end_date", "结束日期", ParameterType.DATE_RANGE_MEMBER, List.of(), "start_date"));
        addParameters(expected, List.of("new_share", "namechange"),
                parameter("start_date", "开始日期", ParameterType.DATE_RANGE_MEMBER, List.of(), "end_date"),
                parameter("end_date", "结束日期", ParameterType.DATE_RANGE_MEMBER, List.of(), "start_date"));
        addParameters(expected, List.of("broker_recommend"),
                parameter("month", "月份", ParameterType.MONTH, List.of(), null));
        addParameters(expected, List.of(
                        "daily", "weekly", "monthly", "adj_factor", "suspend_d", "daily_basic", "stk_limit",
                        "moneyflow", "margin_detail", "top_list", "top_inst", "block_trade", "moneyflow_hsgt",
                        "hsgt_top10", "hk_hold", "slb_len", "slb_sec", "slb_sec_detail"),
                parameter("trade_date", "交易日期", ParameterType.DATE, List.of(), null));
        addParameters(expected, List.of("margin"),
                parameter("exchange_id", "交易所", ParameterType.ENUM, List.of("SSE", "SZSE", "BSE"), null),
                parameter("trade_date", "交易日期", ParameterType.DATE, List.of(), null));
        addParameters(expected, List.of(
                        "income", "balancesheet", "cashflow", "fina_indicator", "fina_audit", "fina_mainbz"),
                parameter("ts_code", "股票代码", ParameterType.TS_CODE, List.of(), null),
                parameter("ann_date", "公告日期", ParameterType.DATE, List.of(), null));
        addParameters(expected, List.of(
                        "express", "forecast", "disclosure_date", "dividend", "repurchase", "share_float",
                        "stk_holdertrade", "top10_holders", "top10_floatholders"),
                parameter("ann_date", "公告日期", ParameterType.DATE, List.of(), null));
        addParameters(expected, List.of("stk_rewards", "stk_holdernumber"),
                parameter("ts_code", "股票代码", ParameterType.TS_CODE, List.of(), null));
        addParameters(expected, List.of(
                "stk_managers", "index_classify", "index_member", "index_member_all", "pledge_stat", "pledge_detail"));
        return Map.copyOf(expected);
    }

    private static ExpectedParameter parameter(
            String name, String label, ParameterType type, List<String> allowedValues, String relatedParameter) {
        return new ExpectedParameter(name, label, type, true, allowedValues, relatedParameter);
    }

    private static void addParameters(
            Map<String, List<ExpectedParameter>> target, List<String> apiNames, ExpectedParameter... parameters) {
        List<ExpectedParameter> values = List.of(parameters);
        apiNames.forEach(apiName -> assertThat(target.putIfAbsent(apiName, values))
                .as("duplicate parameter expectation for %s", apiName)
                .isNull());
    }

    private static Map<String, ExpectedBusinessKey> expectedBusinessKeys() {
        Map<String, ExpectedBusinessKey> expected = new LinkedHashMap<>();
        addBusinessKey(expected, "stock_basic", BusinessKeyMode.COMPOSITE, "ts_code");
        addBusinessKey(expected, "stock_company", BusinessKeyMode.COMPOSITE, "ts_code");
        addBusinessKey(expected, "hs_const", BusinessKeyMode.COMPOSITE, "hs_type", "ts_code", "in_date");
        addBusinessKey(expected, "trade_cal", BusinessKeyMode.COMPOSITE, "exchange", "cal_date");
        addBusinessKey(expected, "new_share", BusinessKeyMode.COMPOSITE, "ts_code");
        addBusinessKey(expected, "namechange", BusinessKeyMode.COMPOSITE, "ts_code", "start_date", "name");
        addBusinessKey(expected, "stk_managers", BusinessKeyMode.FINGERPRINT,
                "ts_code", "ann_date", "name", "gender", "lev", "title", "birthday", "begin_date");
        addBusinessKey(expected, "broker_recommend", BusinessKeyMode.COMPOSITE, "month", "broker", "ts_code");
        addBusinessKey(expected, "index_classify", BusinessKeyMode.COMPOSITE, "index_code");
        addBusinessKey(expected, "index_member", BusinessKeyMode.COMPOSITE, "index_code", "con_code", "in_date");
        addBusinessKey(expected, "index_member_all", BusinessKeyMode.COMPOSITE,
                "l1_code", "l2_code", "l3_code", "ts_code", "in_date");
        addBusinessKey(expected, "daily", BusinessKeyMode.COMPOSITE, "ts_code", "trade_date");
        addBusinessKey(expected, "weekly", BusinessKeyMode.COMPOSITE, "ts_code", "trade_date");
        addBusinessKey(expected, "monthly", BusinessKeyMode.COMPOSITE, "ts_code", "trade_date");
        addBusinessKey(expected, "adj_factor", BusinessKeyMode.COMPOSITE, "ts_code", "trade_date");
        addBusinessKey(expected, "suspend_d", BusinessKeyMode.COMPOSITE, "ts_code", "trade_date");
        addBusinessKey(expected, "daily_basic", BusinessKeyMode.COMPOSITE, "ts_code", "trade_date");
        addBusinessKey(expected, "stk_limit", BusinessKeyMode.COMPOSITE, "trade_date", "ts_code");
        addBusinessKey(expected, "moneyflow", BusinessKeyMode.COMPOSITE, "ts_code", "trade_date");
        addBusinessKey(expected, "margin", BusinessKeyMode.COMPOSITE, "trade_date", "exchange_id");
        addBusinessKey(expected, "margin_detail", BusinessKeyMode.COMPOSITE, "trade_date", "ts_code");
        addBusinessKey(expected, "top_list", BusinessKeyMode.COMPOSITE, "trade_date", "ts_code", "reason");
        addBusinessKey(expected, "top_inst", BusinessKeyMode.COMPOSITE,
                "trade_date", "ts_code", "exalter", "side", "reason", "net_buy");
        addBusinessKey(expected, "block_trade", BusinessKeyMode.COMPOSITE,
                "trade_date", "ts_code", "buyer", "seller", "price", "vol");
        addBusinessKey(expected, "moneyflow_hsgt", BusinessKeyMode.COMPOSITE, "trade_date");
        addBusinessKey(expected, "hsgt_top10", BusinessKeyMode.COMPOSITE, "trade_date", "ts_code", "market_type");
        addBusinessKey(expected, "hk_hold", BusinessKeyMode.COMPOSITE, "trade_date", "code", "exchange");
        addBusinessKey(expected, "slb_len", BusinessKeyMode.COMPOSITE, "trade_date", "ob");
        addBusinessKey(expected, "slb_sec", BusinessKeyMode.COMPOSITE, "trade_date", "ts_code");
        addBusinessKey(expected, "slb_sec_detail", BusinessKeyMode.COMPOSITE,
                "trade_date", "ts_code", "tenor", "fee_rate");
        addBusinessKey(expected, "income", BusinessKeyMode.COMPOSITE,
                "ts_code", "end_date", "report_type", "ann_date");
        addBusinessKey(expected, "balancesheet", BusinessKeyMode.COMPOSITE,
                "ts_code", "end_date", "report_type", "ann_date");
        addBusinessKey(expected, "cashflow", BusinessKeyMode.COMPOSITE,
                "ts_code", "end_date", "report_type", "ann_date");
        addBusinessKey(expected, "fina_indicator", BusinessKeyMode.COMPOSITE, "ts_code", "end_date", "ann_date");
        addBusinessKey(expected, "fina_audit", BusinessKeyMode.COMPOSITE, "ts_code", "end_date", "ann_date");
        addBusinessKey(expected, "fina_mainbz", BusinessKeyMode.COMPOSITE,
                "ts_code", "end_date", "bz_item", "curr_type");
        addBusinessKey(expected, "express", BusinessKeyMode.COMPOSITE, "ts_code", "end_date", "ann_date");
        addBusinessKey(expected, "forecast", BusinessKeyMode.COMPOSITE,
                "ts_code", "end_date", "ann_date", "type");
        addBusinessKey(expected, "disclosure_date", BusinessKeyMode.COMPOSITE, "ts_code", "end_date");
        addBusinessKey(expected, "dividend", BusinessKeyMode.COMPOSITE, "ts_code", "end_date", "ann_date");
        addBusinessKey(expected, "repurchase", BusinessKeyMode.COMPOSITE, "ts_code", "ann_date", "proc");
        addBusinessKey(expected, "share_float", BusinessKeyMode.COMPOSITE,
                "ts_code", "float_date", "holder_name", "share_type");
        addBusinessKey(expected, "stk_rewards", BusinessKeyMode.COMPOSITE,
                "ts_code", "ann_date", "end_date", "name");
        addBusinessKey(expected, "stk_holdernumber", BusinessKeyMode.COMPOSITE, "ts_code", "end_date", "ann_date");
        addBusinessKey(expected, "stk_holdertrade", BusinessKeyMode.COMPOSITE,
                "ts_code", "ann_date", "holder_name", "in_de", "change_vol");
        addBusinessKey(expected, "top10_holders", BusinessKeyMode.COMPOSITE,
                "ts_code", "end_date", "holder_name", "ann_date");
        addBusinessKey(expected, "top10_floatholders", BusinessKeyMode.COMPOSITE,
                "ts_code", "end_date", "holder_name", "ann_date");
        addBusinessKey(expected, "pledge_stat", BusinessKeyMode.COMPOSITE, "ts_code", "end_date");
        addBusinessKey(expected, "pledge_detail", BusinessKeyMode.FINGERPRINT,
                "ts_code", "ann_date", "holder_name", "pledge_amount", "start_date", "end_date", "is_release",
                "release_date", "pledgor", "holding_amount", "pledged_amount", "p_total_ratio", "h_total_ratio",
                "is_buyback");
        return Map.copyOf(expected);
    }

    private static void addBusinessKey(
            Map<String, ExpectedBusinessKey> target, String apiName, BusinessKeyMode mode, String... fields) {
        assertThat(target.putIfAbsent(apiName, new ExpectedBusinessKey(mode, List.of(fields))))
                .as("duplicate business-key expectation for %s", apiName)
                .isNull();
    }

    private static Map<String, List<String>> expectedFilters() {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        addFilters(expected, List.of("trade_cal", "index_classify", "index_member"), List.of());
        addFilters(expected, List.of(
                "stock_basic", "stock_company", "hs_const", "new_share", "broker_recommend", "index_member_all",
                "fina_mainbz", "pledge_stat"), List.of("ts_code"));
        addFilters(expected, List.of("margin", "moneyflow_hsgt", "slb_len"), List.of("trade_date"));
        addFilters(expected, List.of(
                "daily", "weekly", "monthly", "adj_factor", "suspend_d", "daily_basic", "stk_limit", "moneyflow",
                "margin_detail", "top_list", "top_inst", "block_trade", "hsgt_top10", "hk_hold", "slb_sec",
                "slb_sec_detail"), List.of("ts_code", "trade_date"));
        addFilters(expected, List.of(
                "namechange", "stk_managers", "income", "balancesheet", "cashflow", "fina_indicator", "fina_audit",
                "express", "forecast", "disclosure_date", "dividend", "repurchase", "share_float", "stk_rewards",
                "stk_holdernumber", "stk_holdertrade", "top10_holders", "top10_floatholders", "pledge_detail"),
                List.of("ts_code", "ann_date"));
        return Map.copyOf(expected);
    }

    private static void addFilters(Map<String, List<String>> target, List<String> apiNames, List<String> filters) {
        apiNames.forEach(apiName -> assertThat(target.putIfAbsent(apiName, filters))
                .as("duplicate filter expectation for %s", apiName)
                .isNull());
    }

    private record ContractData(
            Path repositoryRoot, List<ManifestEntry> manifestEntries, List<DatasetDefinition> definitions) {
        private ContractData {
            manifestEntries = List.copyOf(manifestEntries);
            definitions = List.copyOf(definitions);
        }
    }

    private record ManifestEntry(String apiName, String filename) {
    }

    private record TemplateProjection(String apiName, List<String> fields) {
        private TemplateProjection {
            fields = List.copyOf(fields);
        }
    }

    private record ExpectedParameter(
            String name,
            String label,
            ParameterType type,
            boolean required,
            List<String> allowedValues,
            String relatedParameter) {
        private ExpectedParameter {
            allowedValues = List.copyOf(allowedValues);
        }
    }

    private record ExpectedBusinessKey(BusinessKeyMode mode, List<String> fields) {
        private ExpectedBusinessKey {
            fields = List.copyOf(fields);
        }
    }
}
