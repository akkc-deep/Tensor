package com.akkc.tensor.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.plugin.tushare.client.TushareProClient;
import com.akkc.tensor.plugin.tushare.config.TushareProperties;
import com.akkc.tensor.plugin.tushare.metadata.DatasetDefinitionLoader;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TushareDecimalAdaptationTest {
    @Test
    void adaptsStockCompanyJsonDecimalsWithoutLosingPrecision() {
        var definition = new DatasetDefinitionLoader()
                .loadAll(new PathMatchingResourcePatternResolver(), "classpath*:datasets/tushare_pro/*.yaml")
                .stream().filter(item -> item.datasetKey().apiName().value().equals("stock_company"))
                .findFirst().orElseThrow();
        var builder = RestClient.builder().baseUrl("https://synthetic.invalid");
        var upstream = MockRestServiceServer.bindTo(builder).build();
        upstream.expect(requestTo("https://synthetic.invalid"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":0,"msg":null,"data":{
                          "fields":["ts_code","com_name","com_id","chairman","manager","secretary","reg_capital","setup_date","province","city","introduction","website","email","office","business_scope","employees","main_business","exchange"],
                          "items":[["SYNTHETIC.SH","Synthetic company",null,null,null,null,12345.123456789012345678,"20200101",null,null,null,null,null,null,null,42,null,"SSE"]]
                        }}
                        """, MediaType.APPLICATION_JSON));
        var properties = new TushareProperties(true, URI.create("https://synthetic.invalid"),
                new TushareProperties.Credential("synthetic-test-token"),
                Duration.ofSeconds(5), Duration.ofSeconds(5), 1_048_576);
        var envelope = new TushareProClient(builder.build(), properties)
                .execute(definition, Map.of("ts_code", "SYNTHETIC.SH", "exchange", "SSE"));

        var batch = new GenericDatasetAdapter(definition, new ValueConverter(), new FingerprintKeyCodec())
                .adapt(envelope, Instant.parse("2026-09-06T00:00:00Z"));

        assertThat(batch.rows()).singleElement().satisfies(row -> {
            assertThat(row.get("reg_capital")).isEqualTo(new BigDecimal("12345.123456789012345678"));
            assertThat(row.get("employees")).isEqualTo(42L);
            assertThat(row.get("setup_date")).isEqualTo(LocalDate.of(2020, 1, 1));
            assertThat(row.get("com_id")).isNull();
        });
        upstream.verify();
    }
}
