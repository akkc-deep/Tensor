package com.akkc.tensor.plugin.tushare.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
        assertThat(definition.columns()).extracting(column -> column.name())
                .containsExactly("ts_code", "trade_date", "open", "high", "low", "close", "pre_close", "change", "pct_chg", "vol", "amount");
        assertThat(definition.columns()).allSatisfy(column -> assertThat(column.displayOrder())
                .isEqualTo(definition.columns().indexOf(column)));
        assertThat(definition.columns().getFirst().logicalType().name()).isEqualTo("STRING");
        assertThat(definition.columns().getFirst().length()).isEqualTo(16);
        assertThat(definition.columns().subList(2, 11)).allSatisfy(column -> {
            assertThat(column.logicalType().name()).isEqualTo("DECIMAL");
            assertThat(column.precision()).isEqualTo(38);
            assertThat(column.scale()).isEqualTo(18);
            assertThat(column.longText()).isFalse();
            assertThat(column.allowedValues()).isEmpty();
        });
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

        List<DatasetDefinition> definitions = loader.loadAll(resolver, temporaryPattern());

        assertThat(definitions).extracting(value -> value.datasetKey().apiName().value()).containsExactly("alpha", "zulu");
    }

    @Test
    void aggregatesDeterministicSafeDiagnosticsForInvalidResources() throws Exception {
        write("z-schema.yaml", valid() + "\nunknown: true\n");
        write("a-plugin.yaml", valid().replace("pluginId: tushare_pro", "pluginId: wrong_plugin"));

        TensorException exception = misconfigured(() -> loader.loadAll(resolver, temporaryPattern()));

        assertThat(exception.getMessage()).contains("a-plugin.yaml", "z-schema.yaml").doesNotContain(tempDir.toString());
        assertThat(exception.getMessage().indexOf("a-plugin.yaml")).isLessThan(exception.getMessage().indexOf("z-schema.yaml"));
        assertThat(exception.retryable()).isFalse();
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
}
