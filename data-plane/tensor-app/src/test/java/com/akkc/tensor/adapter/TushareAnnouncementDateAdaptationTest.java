package com.akkc.tensor.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.plugin.api.download.AdaptedBatch;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TushareAnnouncementDateAdaptationTest {
    @Test
    void mapsHolderAnnouncementDateTimesToTheirCalendarDate() {
        var batch = adapt("""
                ["SYNTHETIC.SZ","20260102","20251231",42],
                ["SYNTHETIC.SZ","2026-01-03 15:04:05","20251231",null],
                ["SYNTHETIC.SZ","2024-02-29 23:59:59","20231231",7]
                """);

        assertThat(batch.rows()).hasSize(3);
        assertThat(batch.rows()).extracting(row -> row.get("ann_date")).containsExactly(
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), LocalDate.of(2024, 2, 29));
        assertThat(batch.rows()).extracting(row -> row.get("holder_num")).containsExactly(42L, null, 7L);
        assertThat(batch.rows().get(1).get("end_date")).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-02-29 15:04:05", "2026-01-03 24:00:00", "2026-01-03 15:04:60",
            "2026-01-03T15:04:05", "2026-01-03 15:04:05Z", "2026-01-03 15:04:05+08:00"})
    void stillRejectsInvalidOrUnsupportedAnnouncementDates(String date) {
        assertThatThrownBy(() -> adapt("[\"SYNTHETIC.SZ\",\"" + date + "\",\"20251231\",42]"))
                .isInstanceOfSatisfying(AdapterException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
                    assertThat(failure).hasMessage("Invalid adapter value: api=stk_holdernumber, row=0, field=ann_date")
                            .hasNoCause();
                });
    }

    @Test
    void keepsOtherHolderDateColumnsStrict() {
        assertThatThrownBy(() -> adapt("[\"SYNTHETIC.SZ\",\"20260103\",\"2025-12-31 15:04:05\",42]"))
                .isInstanceOf(AdapterException.class)
                .hasMessage("Invalid adapter value: api=stk_holdernumber, row=0, field=end_date");
    }

    private AdaptedBatch adapt(String rows) {
        var definition = new DatasetDefinitionLoader()
                .loadAll(new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml")
                .stream().filter(item -> item.datasetKey().apiName().value().equals("stk_holdernumber"))
                .findFirst().orElseThrow();
        var builder = RestClient.builder().baseUrl("https://synthetic.invalid");
        var upstream = MockRestServiceServer.bindTo(builder).build();
        upstream.expect(requestTo("https://synthetic.invalid")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":0,\"msg\":null,\"data\":{\"fields\":[\"ts_code\",\"ann_date\",\"end_date\",\"holder_num\"],\"items\":["
                        + rows + "]}}", MediaType.APPLICATION_JSON));
        var properties = new TushareProperties(true, URI.create("https://synthetic.invalid"),
                new TushareProperties.Credential("synthetic-test-token"),
                Duration.ofSeconds(5), Duration.ofSeconds(5), 1_048_576);
        var envelope = new TushareProClient(builder.build(), properties)
                .execute(definition, Map.of("ts_code", "SYNTHETIC.SZ"));
        upstream.verify();
        return new GenericDatasetAdapter(definition, new ValueConverter(), new FingerprintKeyCodec())
                .adapt(envelope, Instant.parse("2026-09-06T00:00:00Z"));
    }
}
