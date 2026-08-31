package com.akkc.tensor.plugin.tushare.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

class DatasetDefinitionLoaderTest {
    @TempDir
    Path tempDir;

    private final DatasetDefinitionLoader loader = new DatasetDefinitionLoader();
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Test
    void loadsCompleteDailyDefinitionAndReturnsAnImmutableList() {
        List<DatasetDefinition> definitions = loader.loadAll(resolver, "classpath*:datasets/valid-daily.yaml");

        DatasetDefinition definition = definitions.getFirst();
        assertThat(definitions).hasSize(1);
        assertThat(definition.datasetKey().pluginId().value()).isEqualTo("tushare_pro");
        assertThat(definition.datasetKey().apiName().value()).isEqualTo("daily");
        assertThat(definition.category()).isEqualTo("market");
        assertThat(definition.displayName()).isEqualTo("日线行情");
        assertThat(definition.tableName().value()).isEqualTo("tushare_pro__daily");
        assertThat(definition.queryMode().name()).isEqualTo("trade_date");
        assertThat(definition.parameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.name()).isEqualTo("trade_date");
            assertThat(parameter.label()).isEqualTo("交易日期");
            assertThat(parameter.type().name()).isEqualTo("DATE");
            assertThat(parameter.required()).isTrue();
            assertThat(parameter.allowedValues()).isEmpty();
            assertThat(parameter.description()).isNull();
            assertThat(parameter.defaultValue()).isNull();
            assertThat(parameter.pattern()).isNull();
            assertThat(parameter.relatedParameter()).isNull();
        });
        assertColumns(definition);
        assertThat(definition.businessKey().mode().name()).isEqualTo("COMPOSITE");
        assertThat(definition.businessKey().fields()).containsExactly("ts_code", "trade_date");
        assertThat(definition.filters()).extracting(filter -> filter.field()).containsExactly("ts_code", "trade_date");
        assertThat(definition.fixedColumn()).isEqualTo("ts_code");
        assertThat(definition.batchSize()).isEqualTo(500);
        assertThat(catchThrowable(() -> definitions.add(definition))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sortsDefinitionsByApiNameRegardlessOfDiscoveredResourceOrder() throws Exception {
        write("zulu.yaml", valid().replace("apiName: daily", "apiName: zulu").replace("tushare_pro__daily", "tushare_pro__zulu"));
        write("alpha.yaml", valid().replace("apiName: daily", "apiName: alpha").replace("tushare_pro__daily", "tushare_pro__alpha"));
        Resource[] forward = resourcesInFilenameOrder();
        Resource[] reverse = reversed(forward);

        List<DatasetDefinition> definitions = loader.loadAll(new OrderedResolver(forward), temporaryPattern());
        List<DatasetDefinition> reversedDefinitions = loader.loadAll(new OrderedResolver(reverse), temporaryPattern());

        assertThat(definitions).extracting(value -> value.datasetKey().apiName().value()).containsExactly("alpha", "zulu");
        assertThat(reversedDefinitions).isEqualTo(definitions);
    }

    @Test
    void aggregatesDeterministicSafeDiagnosticsForInvalidResources() throws Exception {
        write("z-schema.yaml", valid() + "\nunknown: true\n");
        write("a-plugin.yaml", valid().replace("pluginId: tushare_pro", "pluginId: wrong_plugin"));
        Resource[] forward = resourcesInFilenameOrder();
        Resource[] reverse = reversed(forward);

        TensorException exception = misconfigured(() -> loader.loadAll(new OrderedResolver(forward), temporaryPattern()));
        TensorException reversedException = misconfigured(() -> loader.loadAll(new OrderedResolver(reverse), temporaryPattern()));

        assertThat(exception.getMessage()).contains("a-plugin.yaml", "z-schema.yaml").doesNotContain(tempDir.toString());
        assertThat(exception.getMessage().indexOf("a-plugin.yaml")).isLessThan(exception.getMessage().indexOf("z-schema.yaml"));
        assertThat(exception.retryable()).isFalse();
        assertThat(reversedException.code()).isEqualTo(exception.code());
        assertThat(reversedException.retryable()).isEqualTo(exception.retryable());
        assertThat(reversedException.getMessage()).isEqualTo(exception.getMessage());
    }

    @Test
    void rejectsM02AndM03SemanticViolations() throws Exception {
        write("duplicate.yaml", fixture("invalid-duplicate-column.yaml"));
        write("table.yaml", valid().replace("tushare_pro__daily", "tushare_pro__wrong"));
        write("key.yaml", valid().replace("[ts_code, trade_date]", "[missing_column]"));
        write("filter.yaml", valid().replace("filters: [ts_code, trade_date]", "filters: [missing_column]"));
        write("plugin.yaml", valid().replace("pluginId: tushare_pro", "pluginId: other_plugin"));
        write("order.yaml", valid().replace("displayOrder: 1", "displayOrder: 3"));
        write("scale.yaml", valid().replace("precision: 38, scale: 18", "precision: 18, scale: 19"));
        write("related.yaml", valid().replace("required: true", "required: true\n    relatedParameter: missing_parameter"));

        TensorException exception = misconfigured(() -> loader.loadAll(resolver, temporaryPattern()));

        assertThat(exception.getMessage()).contains("duplicate.yaml", "table.yaml", "key.yaml", "filter.yaml", "plugin.yaml", "order.yaml", "scale.yaml", "related.yaml");
    }

    @Test
    void rejectsSchemaAndStrictYamlFailures() throws Exception {
        write("missing.yaml", valid().replace("displayName: 日线行情\n", ""));
        write("unknown.yaml", valid() + "\nunknown: true\n");
        write("duplicate-key.yaml", valid().replace("apiName: daily", "apiName: daily\napiName: duplicate"));
        write("multiple-documents.yaml", valid() + "\n---\n" + valid());

        TensorException exception = misconfigured(() -> loader.loadAll(resolver, temporaryPattern()));

        assertThat(exception.getMessage()).contains("missing.yaml", "unknown.yaml", "duplicate-key.yaml", "multiple-documents.yaml");
    }

    @Test
    void rejectsDuplicateApiNamesAndZeroMatches() throws Exception {
        write("first.yaml", valid());
        write("second.yaml", valid());

        TensorException duplicate = misconfigured(() -> loader.loadAll(resolver, temporaryPattern()));
        TensorException zero = misconfigured(() -> loader.loadAll(resolver, temporaryPattern("none-*.yaml")));

        assertThat(duplicate.getMessage()).contains("first.yaml: duplicate apiName: daily", "second.yaml: duplicate apiName: daily");
        assertThat(zero.getMessage()).contains("<pattern>: no resources matched");
    }

    @Test
    void doesNotExposeSchemaFactoryExceptionMessages() throws Exception {
        Method method = DatasetDefinitionLoader.class.getDeclaredMethod("schemaReason", Exception.class);
        method.setAccessible(true);

        String reason = (String) method.invoke(null, new IOException("file:///private/tmp/schema-secret"));

        assertThat(reason).isEqualTo("resource cannot be read");
    }

    private void assertColumns(DatasetDefinition definition) {
        List<ExpectedColumn> expected = List.of(
                new ExpectedColumn("ts_code", "TS代码", "STRING", false, 0, 16, null, null, List.of(), false),
                new ExpectedColumn("trade_date", "交易日期", "DATE", false, 1, null, null, null, List.of(), false),
                new ExpectedColumn("open", "开盘价", "DECIMAL", true, 2, null, 38, 18, List.of(), false),
                new ExpectedColumn("high", "最高价", "DECIMAL", true, 3, null, 38, 18, List.of(), false),
                new ExpectedColumn("low", "最低价", "DECIMAL", true, 4, null, 38, 18, List.of(), false),
                new ExpectedColumn("close", "收盘价", "DECIMAL", true, 5, null, 38, 18, List.of(), false),
                new ExpectedColumn("pre_close", "昨收价", "DECIMAL", true, 6, null, 38, 18, List.of(), false),
                new ExpectedColumn("change", "涨跌额", "DECIMAL", true, 7, null, 38, 18, List.of(), false),
                new ExpectedColumn("pct_chg", "涨跌幅", "DECIMAL", true, 8, null, 38, 18, List.of(), false),
                new ExpectedColumn("vol", "成交量", "DECIMAL", true, 9, null, 38, 18, List.of(), false),
                new ExpectedColumn("amount", "成交额", "DECIMAL", true, 10, null, 38, 18, List.of(), false));

        assertThat(definition.columns()).hasSize(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            var actual = definition.columns().get(index);
            ExpectedColumn value = expected.get(index);
            assertThat(actual.name()).isEqualTo(value.name());
            assertThat(actual.label()).isEqualTo(value.label());
            assertThat(actual.logicalType().name()).isEqualTo(value.logicalType());
            assertThat(actual.nullable()).isEqualTo(value.nullable());
            assertThat(actual.displayOrder()).isEqualTo(value.displayOrder());
            assertThat(actual.length()).isEqualTo(value.length());
            assertThat(actual.precision()).isEqualTo(value.precision());
            assertThat(actual.scale()).isEqualTo(value.scale());
            assertThat(actual.allowedValues()).isEqualTo(value.allowedValues());
            assertThat(actual.longText()).isEqualTo(value.longText());
        }
    }

    private TensorException misconfigured(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown).isInstanceOf(TensorException.class);
        TensorException exception = (TensorException) thrown;
        assertThat(exception.code()).isEqualTo(ErrorCode.DATASET_MISCONFIGURED);
        return exception;
    }

    private String fixture(String name) throws IOException, URISyntaxException {
        return Files.readString(Path.of(getClass().getClassLoader().getResource("datasets/" + name).toURI()));
    }

    private String valid() throws IOException, URISyntaxException {
        return fixture("valid-daily.yaml");
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }

    private String temporaryPattern() {
        return temporaryPattern("*.yaml");
    }

    private String temporaryPattern(String glob) {
        return tempDir.resolve(glob).toUri().toString();
    }

    private Resource[] resourcesInFilenameOrder() throws IOException {
        Resource[] resources = resolver.getResources(temporaryPattern());
        Arrays.sort(resources, java.util.Comparator.comparing(Resource::getFilename));
        return resources;
    }

    private Resource[] reversed(Resource[] resources) {
        Resource[] reversed = resources.clone();
        for (int left = 0, right = reversed.length - 1; left < right; left++, right--) {
            Resource value = reversed[left];
            reversed[left] = reversed[right];
            reversed[right] = value;
        }
        return reversed;
    }

    private record ExpectedColumn(
            String name,
            String label,
            String logicalType,
            boolean nullable,
            int displayOrder,
            Integer length,
            Integer precision,
            Integer scale,
            List<String> allowedValues,
            boolean longText) {
    }

    private static final class OrderedResolver extends PathMatchingResourcePatternResolver {
        private final Resource[] resources;

        private OrderedResolver(Resource[] resources) {
            this.resources = resources.clone();
        }

        @Override
        public Resource[] getResources(String locationPattern) {
            return resources.clone();
        }
    }
}
