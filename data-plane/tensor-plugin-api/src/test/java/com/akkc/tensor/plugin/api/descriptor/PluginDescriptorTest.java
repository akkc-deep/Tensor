package com.akkc.tensor.plugin.api.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {

    @Test
    void exposesFrozenParameterTypesInOrder() {
        assertThat(ParameterType.values()).containsExactly(
                ParameterType.DATE,
                ParameterType.DATE_RANGE_MEMBER,
                ParameterType.MONTH,
                ParameterType.TS_CODE,
                ParameterType.ENUM,
                ParameterType.TEXT);
    }

    @Test
    void exposesFrozenQueryModesInOrder() {
        assertThat(QueryMode.values()).containsExactly(
                QueryMode.trade_date,
                QueryMode.ann_date,
                QueryMode.snapshot,
                QueryMode.date_range);
    }

    @Test
    void constructsOrdinaryParameter() {
        ParameterDescriptor parameter = parameter("trade_date", ParameterType.DATE, false, null, List.of(), null, null);

        assertThat(parameter).isEqualTo(new ParameterDescriptor(
                "trade_date", "Trade date", "Trading day", ParameterType.DATE,
                false, null, List.of(), null, null));
    }

    @Test
    void constructsApiWithoutParameters() {
        ApiDescriptor api = new ApiDescriptor(ApiName.of("daily"), "Daily", "Market", QueryMode.trade_date, List.of());

        assertThat(api.parameters()).isEmpty();
    }

    @Test
    void constructsTushareDailyPluginDescriptor() {
        PluginId pluginId = PluginId.of("tushare_pro");
        ApiName apiName = ApiName.of("daily");
        ApiDescriptor api = new ApiDescriptor(apiName, "Daily", "Market", QueryMode.trade_date,
                List.of(parameter("trade_date", ParameterType.DATE, true, null, List.of(), null, null)));
        PluginDescriptor descriptor = new PluginDescriptor(pluginId, "Tushare Pro", "Market data", true, true, true,
                null, List.of(api), List.of(DatasetKey.of(pluginId, apiName)));

        assertThat(descriptor.pluginId()).isEqualTo(pluginId);
        assertThat(descriptor.apis()).containsExactly(api);
        assertThat(descriptor.datasets()).containsExactly(DatasetKey.of(pluginId, apiName));
    }

    @Test
    void rejectsInvalidParameterNamesAndRelatedParameterNames() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> parameter("TradeDate", ParameterType.DATE, false, null, List.of(), null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> parameter("start_date", ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, "EndDate"));
    }

    @Test
    void rejectsBlankParameterDisplayText() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ParameterDescriptor("trade_date", " ", "Trading day", ParameterType.DATE, false,
                        null, List.of(), null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ParameterDescriptor("trade_date", "Trade date", " ", ParameterType.DATE, false,
                        null, List.of(), null, null));
    }

    @Test
    void rejectsNullParameterListsAndElements() {
        assertThatNullPointerException().isThrownBy(
                () -> parameter("trade_date", ParameterType.DATE, false, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> parameter("trade_date", ParameterType.DATE, false, null, java.util.Arrays.asList("a", null), null, null));
    }

    @Test
    void rejectsDuplicateAllowedValuesAndEmptyEnumValues() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> parameter("market", ParameterType.ENUM, false, null, List.of("SSE", "SSE"), null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> parameter("market", ParameterType.ENUM, false, null, List.of(), null, null));
    }

    @Test
    void rejectsSelfRelatedDateRangeMember() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> parameter("start_date", ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, "start_date"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> parameter("start_date", ParameterType.DATE_RANGE_MEMBER, true, null, List.of(), null, null));
    }

    @Test
    void makesAllowedValuesImmutableCopies() {
        List<String> values = new ArrayList<>(List.of("SSE"));
        ParameterDescriptor parameter = parameter("market", ParameterType.ENUM, false, null, values, null, null);
        values.add("SZSE");

        assertThat(parameter.allowedValues()).containsExactly("SSE");
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> parameter.allowedValues().add("SZSE"));
    }

    @Test
    void rejectsInvalidApiComponentsAndDuplicateParameterNames() {
        ParameterDescriptor first = parameter("trade_date", ParameterType.DATE, false, null, List.of(), null, null);
        ParameterDescriptor second = parameter("trade_date", ParameterType.TEXT, false, null, List.of(), null, null);
        assertThatNullPointerException().isThrownBy(
                () -> new ApiDescriptor(null, "Daily", "Market", QueryMode.trade_date, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ApiDescriptor(ApiName.of("daily"), " ", "Market", QueryMode.trade_date, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ApiDescriptor(ApiName.of("daily"), "Daily", "x".repeat(65), QueryMode.trade_date, List.of()));
        assertThatNullPointerException().isThrownBy(
                () -> new ApiDescriptor(ApiName.of("daily"), "Daily", "Market", QueryMode.trade_date, null));
        assertThatNullPointerException().isThrownBy(
                () -> new ApiDescriptor(ApiName.of("daily"), "Daily", "Market", QueryMode.trade_date,
                        java.util.Arrays.asList(first, null)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ApiDescriptor(ApiName.of("daily"), "Daily", "Market", QueryMode.trade_date, List.of(first, second)));
    }

    @Test
    void makesParameterListsImmutableCopies() {
        List<ParameterDescriptor> parameters = new ArrayList<>(List.of(
                parameter("trade_date", ParameterType.DATE, false, null, List.of(), null, null)));
        ApiDescriptor api = new ApiDescriptor(ApiName.of("daily"), "Daily", "Market", QueryMode.trade_date, parameters);
        parameters.clear();

        assertThat(api.parameters()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> api.parameters().clear());
    }

    @Test
    void enforcesReadinessTruthTable() {
        assertThat(new PluginReadiness(true, true, true, null)).isEqualTo(new PluginReadiness(true, true, true, null));
        assertThat(new PluginReadiness(true, true, false, "Maintenance").unavailableReason()).isEqualTo("Maintenance");
        assertThat(new PluginReadiness(false, false, false, "Disabled").downloadAvailable()).isFalse();
        assertThat(new PluginReadiness(false, true, false, "Disabled").downloadAvailable()).isFalse();
        assertThat(new PluginReadiness(true, false, false, "Credentials missing").downloadAvailable()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginReadiness(false, true, true, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginReadiness(true, false, true, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginReadiness(true, true, true, "Maintenance"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginReadiness(true, true, false, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginReadiness(false, false, false, " "));
    }

    @Test
    void enforcesPluginReadinessTruthTable() {
        PluginId pluginId = PluginId.of("tushare_pro");
        assertThat(new PluginDescriptor(pluginId, "Tushare Pro", "Market data", false, false, false, "Disabled", List.of(), List.of())
                .downloadAvailable()).isFalse();
        assertThat(new PluginDescriptor(pluginId, "Tushare Pro", "Market data", true, false, false,
                "Credentials missing", List.of(), List.of()).downloadAvailable()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PluginDescriptor(pluginId, "Tushare Pro", "Market data", false, true, true, null, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PluginDescriptor(pluginId, "Tushare Pro", "Market data", true, false, true, null, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PluginDescriptor(pluginId, "Tushare Pro", "Market data", true, true, true, "Maintenance", List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new PluginDescriptor(pluginId, "Tushare Pro", "Market data", true, true, false, null, List.of(), List.of()));
    }

    @Test
    void rejectsInvalidPluginReferencesAndDuplicates() {
        PluginId pluginId = PluginId.of("tushare_pro");
        ApiName apiName = ApiName.of("daily");
        ApiDescriptor api = new ApiDescriptor(apiName, "Daily", "Market", QueryMode.trade_date, List.of());
        DatasetKey dataset = DatasetKey.of(pluginId, apiName);
        assertThatIllegalArgumentException().isThrownBy(
                () -> plugin(pluginId, List.of(api, api), List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> plugin(pluginId, List.of(api), List.of(dataset, dataset)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> plugin(pluginId, List.of(api), List.of(DatasetKey.of(PluginId.of("other_plugin"), apiName))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> plugin(pluginId, List.of(api), List.of(DatasetKey.of(pluginId, ApiName.of("weekly")))));
    }

    @Test
    void makesApiAndDatasetListsImmutableCopies() {
        PluginId pluginId = PluginId.of("tushare_pro");
        ApiName apiName = ApiName.of("daily");
        List<ApiDescriptor> apis = new ArrayList<>(List.of(
                new ApiDescriptor(apiName, "Daily", "Market", QueryMode.trade_date, List.of())));
        List<DatasetKey> datasets = new ArrayList<>(List.of(DatasetKey.of(pluginId, apiName)));
        PluginDescriptor descriptor = plugin(pluginId, apis, datasets);
        apis.clear();
        datasets.clear();

        assertThat(descriptor.apis()).hasSize(1);
        assertThat(descriptor.datasets()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> descriptor.apis().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> descriptor.datasets().clear());
    }

    @Test
    void rejectsNullPluginListsAndElements() {
        PluginId pluginId = PluginId.of("tushare_pro");
        assertThatNullPointerException().isThrownBy(() -> plugin(pluginId, null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> plugin(pluginId, List.of(), null));
        assertThatNullPointerException().isThrownBy(() -> plugin(pluginId, java.util.Arrays.asList((ApiDescriptor) null), List.of()));
        assertThatNullPointerException().isThrownBy(() -> plugin(pluginId, List.of(), java.util.Arrays.asList((DatasetKey) null)));
    }

    @Test
    void exposesOnlySpecifiedPublicRecordComponents() {
        assertThat(componentNames(PluginReadiness.class)).containsExactly(
                "enabled", "credentialConfigured", "downloadAvailable", "unavailableReason");
        assertThat(componentNames(PluginDescriptor.class)).containsExactly(
                "pluginId", "displayName", "description", "enabled", "credentialConfigured", "downloadAvailable",
                "unavailableReason", "apis", "datasets");
        RecordComponent datasets = PluginDescriptor.class.getRecordComponents()[8];
        assertThat(datasets.getGenericType().getTypeName()).isEqualTo("java.util.List<com.akkc.tensor.plugin.api.model.DatasetKey>");
        assertThat(PluginDescriptor.class.getDeclaredFields()).extracting(field -> field.getName())
                .containsExactlyInAnyOrder("pluginId", "displayName", "description", "enabled", "credentialConfigured",
                        "downloadAvailable", "unavailableReason", "apis", "datasets");
    }

    private static List<String> componentNames(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static ParameterDescriptor parameter(
            String name, ParameterType type, boolean required, String defaultValue, List<String> allowedValues,
            String pattern, String relatedParameter) {
        return new ParameterDescriptor(name, "Trade date", "Trading day", type, required, defaultValue, allowedValues,
                pattern, relatedParameter);
    }

    private static PluginDescriptor plugin(PluginId pluginId, List<ApiDescriptor> apis, List<DatasetKey> datasets) {
        return new PluginDescriptor(pluginId, "Tushare Pro", "Market data", true, true, true, null, apis, datasets);
    }
}
