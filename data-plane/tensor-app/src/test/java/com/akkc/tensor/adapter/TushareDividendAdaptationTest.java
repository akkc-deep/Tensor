package com.akkc.tensor.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.DownloadStatus;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class TushareDividendAdaptationTest {
    private static final Instant INGESTED_AT = Instant.parse("2026-09-06T00:00:00Z");

    @Test
    void keepsDifferentStagesForTheSameLegacyDividendKey() {
        DatasetDefinition definition = definition("dividend");

        AdaptedBatch batch = adapt(definition, List.of(
                dividendRow("预案", "0.100000000000000000", "0.120000000000000000"),
                dividendRow("实施", "0.110000000000000000", "0.130000000000000000"),
                dividendRow(null, "0.090000000000000000", "0.100000000000000000")));

        assertThat(definition.businessKey().fields())
                .containsExactly("ts_code", "end_date", "ann_date", "div_proc");
        assertThat(batch.rows()).hasSize(3);
        assertThat(batch.rows()).extracting(row -> row.get("div_proc")).containsExactly("预案", "实施", null);
        assertThat(batch.rows()).extracting(row -> row.get("business_key")).doesNotHaveDuplicates();
    }

    @Test
    void deduplicatesExactDividendStageButRejectsChangedContentForThatStage() {
        DatasetDefinition definition = definition("dividend");
        List<Object> first = dividendRow("实施", "0.110000000000000000", "0.130000000000000000");

        assertThat(adapt(definition, List.of(first, new ArrayList<>(first))).rows()).hasSize(1);
        assertThatThrownBy(() -> adapt(definition, List.of(
                first, dividendRow("实施", "0.120000000000000000", "0.140000000000000000"))))
                .isInstanceOfSatisfying(AdapterException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
                    assertThat(failure).hasMessage("Conflicting adapter key: api=dividend, row=1").hasNoCause();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"stk_managers", "pledge_detail"})
    void acceptsNullIdentityFieldsForExistingFingerprintDatasets(String apiName) {
        DatasetDefinition definition = definition(apiName);
        List<Object> nullRow = new ArrayList<>();
        definition.columns().forEach(ignored -> nullRow.add(null));

        assertThat(adapt(definition, List.of(nullRow)).rows()).singleElement().satisfies(row ->
                assertThat(row.get("business_key")).isInstanceOf(String.class));
    }

    private static AdaptedBatch adapt(DatasetDefinition definition, List<List<Object>> rows) {
        List<String> fields = definition.columns().stream().map(ColumnDefinition::name).toList();
        DownloadEnvelope envelope = new DownloadEnvelope(
                definition.datasetKey().pluginId(), definition.datasetKey().apiName(), Map.of(), fields,
                rows.size(), rows, DownloadStatus.SUCCESS, null);
        return new GenericDatasetAdapter(definition, new ValueConverter(), new FingerprintKeyCodec())
                .adapt(envelope, INGESTED_AT);
    }

    private static DatasetDefinition definition(String apiName) {
        return new DatasetDefinitionLoader()
                .loadAll(new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml")
                .stream()
                .filter(item -> item.datasetKey().apiName().value().equals(apiName))
                .findFirst()
                .orElseThrow();
    }

    private static List<Object> dividendRow(String progress, String cashDividend, String cashDividendTax) {
        return Arrays.asList(
                "000001.SZ", "20251231", "20260301", progress,
                null, null, null, new BigDecimal(cashDividend), new BigDecimal(cashDividendTax),
                "20260310", "20260311", "20260312", null, "20260305");
    }
}
