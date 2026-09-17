package com.akkc.tensor.plugin.fixture;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.GenericDatasetAdapter;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.plugin.api.DatasetAdapter;
import com.akkc.tensor.plugin.api.constant.DatasetFields;
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
import org.springframework.beans.factory.annotation.Value;
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
    private static final String DISPLAY_NAME = "Fixture 日线";
    private static final String CATEGORY = "验收";
    private static final String SCENARIO_LABEL = "场景";
    private static final String SCENARIO_DESCRIPTION = "确定性验收场景";
    private static final DatasetDefinition DEFINITION = definition();

    @Bean
    public FixturePlugin fixturePlugin(
            @Value("${tensor.plugins.fixture.integrity-version:2}") int integrityVersion) {
        return new FixturePlugin(DEFINITION, new FixtureEnvelopeFactory(), integrityVersion);
    }

    public FixturePlugin fixturePlugin() {
        return fixturePlugin(2);
    }

    @Bean
    public DatasetAdapter fixtureDatasetAdapter() {
        return new GenericDatasetAdapter(DEFINITION, new ValueConverter(), new FingerprintKeyCodec());
    }

    private static DatasetDefinition definition() {
        DatasetKey key = DatasetKey.of(PluginId.of(FixtureConstants.PLUGIN_ID), ApiName.of(FixtureConstants.API_NAME));
        return new DatasetDefinition(
                key,
                DISPLAY_NAME,
                CATEGORY,
                QueryMode.trade_date,
                List.of(new ParameterDescriptor(
                        FixtureConstants.SCENARIO,
                        SCENARIO_LABEL,
                        SCENARIO_DESCRIPTION,
                        ParameterType.ENUM,
                        true,
                        FixtureScenario.SUCCESS.name(),
                        java.util.Arrays.stream(FixtureScenario.values()).map(Enum::name).toList(),
                        null,
                        null)),
                TableName.from(key),
                List.of(
                        column(DatasetFields.TS_CODE, "证券代码", LogicalType.STRING, false, 0, FixtureConstants.TS_CODE_MAX_LENGTH, null, null),
                        column(DatasetFields.TRADE_DATE, "交易日", LogicalType.DATE, false, 1, null, null, null),
                        column(FixtureConstants.AMOUNT, "金额", LogicalType.DECIMAL, false, FixtureConstants.AMOUNT_DISPLAY_ORDER, null, 38, 18),
                        column(FixtureConstants.NOTE, "备注", LogicalType.STRING, true, FixtureConstants.NOTE_DISPLAY_ORDER, FixtureConstants.NOTE_MAX_LENGTH, null, null)),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of(DatasetFields.TS_CODE, DatasetFields.TRADE_DATE)),
                List.of(new FilterDefinition(DatasetFields.TS_CODE)),
                DatasetFields.TS_CODE);
    }

    private static ColumnDefinition column(
            String name,
            String label,
            LogicalType type,
            boolean nullable,
            int order,
            Integer length,
            Integer precision,
            Integer scale) {
        return new ColumnDefinition(
                name, label, type, nullable, order, length, precision, scale, List.of(), false);
    }
}
