package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.plugin.api.constant.DatasetFields;
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
    private static final DatasetDefinition DEFINITION = definition();

    @Bean
    public FixturePlugin fixturePlugin() {
        return new FixturePlugin(DEFINITION, new FixtureEnvelopeFactory());
    }

    @Bean
    public DatasetAdapter fixtureDatasetAdapter() {
        return new GenericDatasetAdapter(DEFINITION, new ValueConverter(), new FingerprintKeyCodec());
    }

    private static DatasetDefinition definition() {
        DatasetKey key = DatasetKey.of(PluginId.of(FixtureConstants.PLUGIN_ID), ApiName.of(FixtureConstants.API_NAME));
        return new DatasetDefinition(
                key,
                "Fixture 日线",
                "验收",
                QueryMode.trade_date,
                List.of(new ParameterDescriptor(
                        FixtureConstants.SCENARIO,
                        "场景",
                        "确定性验收场景",
                        ParameterType.ENUM,
                        true,
                        FixtureScenario.SUCCESS.name(),
                        java.util.Arrays.stream(FixtureScenario.values()).map(Enum::name).toList(),
                        null,
                        null)),
                TableName.from(key),
                List.of(
                        column(DatasetFields.TS_CODE, LogicalType.STRING, false, 0, 64, null, null),
                        column(DatasetFields.TRADE_DATE, LogicalType.DATE, false, 1, null, null, null),
                        column(FixtureConstants.AMOUNT, LogicalType.DECIMAL, false, 2, null, 38, 18),
                        column(FixtureConstants.NOTE, LogicalType.STRING, true, 3, 255, null, null)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of(DatasetFields.TS_CODE, DatasetFields.TRADE_DATE)),
                List.of(new FilterDefinition(DatasetFields.TS_CODE)),
                DatasetFields.TS_CODE);
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
