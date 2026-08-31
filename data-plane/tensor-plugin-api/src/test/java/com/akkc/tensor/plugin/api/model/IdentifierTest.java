package com.akkc.tensor.plugin.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IdentifierTest {

    static Stream<String> validValues() {
        return Stream.of("tushare_pro", "daily", "ab", "a" + "a".repeat(63));
    }

    static Stream<String> invalidValues() {
        return Stream.of("ABC", "a-b", "1abc", "a", "a".repeat(65), "", " ");
    }

    @ParameterizedTest
    @MethodSource("validValues")
    void acceptsValidPluginIds(String value) {
        assertThat(PluginId.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("validValues")
    void acceptsValidApiNames(String value) {
        assertThat(ApiName.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void rejectsInvalidPluginIds(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> PluginId.of(value));
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void rejectsInvalidApiNames(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> ApiName.of(value));
    }

    @Test
    void rejectsNullAndDoesNotNormalizeStringIdentifiers() {
        assertThatNullPointerException().isThrownBy(() -> PluginId.of(null));
        assertThatNullPointerException().isThrownBy(() -> ApiName.of(null));
        assertThatIllegalArgumentException().isThrownBy(() -> PluginId.of(" DAILY "));
        assertThatIllegalArgumentException().isThrownBy(() -> ApiName.of("DAILY"));
        assertThatIllegalArgumentException().isThrownBy(() -> PluginId.of(" daily "));
        assertThatIllegalArgumentException().isThrownBy(() -> ApiName.of(" daily "));
    }

    @Test
    void preservesDatasetComponents() {
        PluginId pluginId = PluginId.of("tushare_pro");
        ApiName apiName = ApiName.of("daily");
        assertThat(DatasetKey.of(pluginId, apiName)).isEqualTo(new DatasetKey(pluginId, apiName));
        assertThatNullPointerException().isThrownBy(() -> DatasetKey.of(null, apiName));
        assertThatNullPointerException().isThrownBy(() -> DatasetKey.of(pluginId, null));
    }

    @Test
    void derivesTableName() {
        DatasetKey key = DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of("daily"));
        assertThat(TableName.from(key).value()).isEqualTo("tushare_pro__daily");
        assertThatNullPointerException().isThrownBy(() -> TableName.from(null));
        assertThatNullPointerException().isThrownBy(() -> new TableName(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TableName("tushare_pro_daily"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TableName("Tushare_pro__daily"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TableName("tushare-pro__daily"));
    }

    @Test
    void createsRandomV4RequestIds() {
        RequestId first = RequestId.newId();
        RequestId second = RequestId.newId();
        assertThat(first.value()).isNotNull().extracting(UUID::version).isEqualTo(4);
        assertThat(first.value().variant()).isEqualTo(2);
        assertThat(second.value()).isNotEqualTo(first.value());
        assertThatNullPointerException().isThrownBy(() -> new RequestId(null));
    }
}
