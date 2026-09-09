package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("acceptance")
@ConditionalOnProperty(
        prefix = "tensor.plugins.fixture",
        name = "enabled",
        havingValue = "true")
public final class FixtureConfiguration {
    private final DatasetDefinition definition;
    private final com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode mode;
    private final com.akkc.tensor.plugin.api.download.RecoveryPolicy.Mode recovery;
    private final java.net.URI sourceUrl;

    public FixtureConfiguration() { this("ORIGINAL_PARAMS", "REQUEST", ""); }

    @org.springframework.beans.factory.annotation.Autowired
    public FixtureConfiguration(
            @org.springframework.beans.factory.annotation.Value("${tensor.plugins.fixture.mode:ORIGINAL_PARAMS}") String mode,
            @org.springframework.beans.factory.annotation.Value("${tensor.plugins.fixture.recovery:REQUEST}") String recovery,
            @org.springframework.beans.factory.annotation.Value("${tensor.plugins.fixture.source-url:}") String sourceUrl) {
        this.mode = com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode.valueOf(mode);
        this.recovery = com.akkc.tensor.plugin.api.download.RecoveryPolicy.Mode.valueOf(recovery);
        if (this.recovery == com.akkc.tensor.plugin.api.download.RecoveryPolicy.Mode.STOCK_TIME
                && this.mode != com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode.TRADE_DATE_RANGE
                && this.mode != com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode.ANN_DATE_RANGE)
            throw new IllegalArgumentException("Stock recovery requires fixture DATE mode");
        this.sourceUrl = sourceUrl.isEmpty() ? null : java.net.URI.create(sourceUrl);
        this.definition = definition(this.mode);
    }

    @Bean
    public FixturePlugin fixturePlugin() {
        return new FixturePlugin(definition, new FixtureEnvelopeFactory(), mode, recovery, sourceUrl);
    }

    @Bean
    public DatasetAdapter fixtureDatasetAdapter() {
        return new GenericDatasetAdapter(definition, new ValueConverter(), new FingerprintKeyCodec());
    }

    private static DatasetDefinition definition(com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode mode) {
        DatasetKey key = DatasetKey.of(PluginId.of("fixture"), ApiName.of("fixture_daily"));
        return new DatasetDefinition(
                key,
                "Fixture 日线",
                "验收",
                QueryMode.trade_date,
                parameters(mode, new ParameterDescriptor(
                        "scenario",
                        "场景",
                        "确定性验收场景",
                        ParameterType.ENUM,
                        true,
                        "SUCCESS",
                        List.of("SUCCESS", "EMPTY", "SOURCE_FAILURE", "TYPE_FAILURE", "PERSISTENCE_FAILURE"),
                        null,
                        null)),
                TableName.from(key),
                List.of(
                        column("ts_code", LogicalType.STRING, false, 0, 64, null, null),
                        column("trade_date", LogicalType.DATE, false, 1, null, null, null),
                        column("amount", LogicalType.DECIMAL, false, 2, null, 38, 18),
                        column("note", LogicalType.STRING, true, 3, 255, null, null)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(new FilterDefinition("ts_code")),
                "ts_code");
    }

    private static List<ParameterDescriptor> parameters(com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode mode, ParameterDescriptor scenario) {
        var params = new java.util.ArrayList<ParameterDescriptor>(); params.add(scenario);
        switch (mode) {
            case TRADE_DATE_RANGE, ANN_DATE_RANGE -> {
                String name = mode == com.akkc.tensor.plugin.api.download.DownloadPolicy.Mode.TRADE_DATE_RANGE ? "trade_date" : "ann_date";
                params.add(parameter(name, ParameterType.DATE, true, null));
                params.add(parameter("ts_code", ParameterType.TS_CODE, false, null));
            }
            case MONTH_RANGE -> params.add(parameter("month", ParameterType.MONTH, true, null));
            case NATIVE_RANGE -> {
                params.add(parameter("start_date", ParameterType.DATE_RANGE_MEMBER, true, "end_date"));
                params.add(parameter("end_date", ParameterType.DATE_RANGE_MEMBER, true, "start_date"));
            }
            case ORIGINAL_PARAMS -> { }
        }
        return List.copyOf(params);
    }
    private static ParameterDescriptor parameter(String name, ParameterType type, boolean required, String related) {
        return new ParameterDescriptor(name, name, "受控 fixture 参数", type, required, null, List.of(), null, related);
    }

    private static ColumnDefinition column(
            String name,
            LogicalType type,
            boolean nullable,
            int order,
            Integer length,
            Integer precision,
            Integer scale) {
        return new ColumnDefinition(
                name, name, type, nullable, order, length, precision, scale, List.of(), false);
    }
}
