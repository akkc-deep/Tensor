package com.akkc.tensor.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpsertSqlFactoryTest {
    private final SqlIdentifierPolicy policy = new SqlIdentifierPolicy();
    private final UpsertSqlFactory factory = new UpsertSqlFactory();

    @Test
    void exposesOnlyTheSpecifiedStatelessPublicContractsAndNullBoundaries() throws Exception {
        assertThat(SqlIdentifierPolicy.class.getModifiers()).matches(Modifier::isFinal);
        assertThat(UpsertSqlFactory.class.getModifiers()).matches(Modifier::isFinal);
        assertPublicSurface(SqlIdentifierPolicy.class, "quote", String.class);
        assertPublicSurface(UpsertSqlFactory.class, "create", DatasetDefinition.class);
        assertThat(SqlIdentifierPolicy.class.getDeclaredFields()).isEmpty();
        assertThat(UpsertSqlFactory.class.getDeclaredFields()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> policy.quote(null)).withMessage("identifier");
        assertThatNullPointerException().isThrownBy(() -> factory.create(null)).withMessage("definition");
    }

    @Test
    void quotesOnlyLowercaseSnakeCaseIdentifiersIncludingReservedWords() {
        assertThat(policy.quote("ab")).isEqualTo("`ab`");
        assertThat(policy.quote("a1_b2")).isEqualTo("`a1_b2`");
        assertThat(policy.quote("change")).isEqualTo("`change`");
        assertThat(policy.quote("a".repeat(64))).isEqualTo("`" + "a".repeat(64) + "`");
        for (String identifier : List.of("", "a", "1abc", "Abc", "a.b", "a`b", "a b", "a".repeat(65), "a-")) {
            assertThatIllegalArgumentException().isThrownBy(() -> policy.quote(identifier))
                    .withMessage("Invalid SQL identifier");
        }
    }

    @Test
    void createsTheExactDailyCompositeUpsertWithQuotedReservedWordAndFourteenPlaceholders() {
        String sql = factory.create(daily());

        assertThat(sql).isEqualTo("INSERT INTO `tushare_pro__daily` (`ts_code`, `trade_date`, `open`, `high`, `low`, `close`, `pre_close`, `change`, `pct_chg`, `vol`, `amount`, `source_plugin`, `source_api`, `ingested_at`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `open` = VALUES(`open`), `high` = VALUES(`high`), `low` = VALUES(`low`), `close` = VALUES(`close`), `pre_close` = VALUES(`pre_close`), `change` = VALUES(`change`), `pct_chg` = VALUES(`pct_chg`), `vol` = VALUES(`vol`), `amount` = VALUES(`amount`), `source_plugin` = VALUES(`source_plugin`), `source_api` = VALUES(`source_api`), `ingested_at` = VALUES(`ingested_at`)");
        assertThat(sql.chars().filter(character -> character == '?').count()).isEqualTo(14);
        assertThat(sql).doesNotContain("`ts_code` = VALUES", "`trade_date` = VALUES")
                .contains("`change`", "`change` = VALUES(`change`)", "`source_plugin` = VALUES(`source_plugin`)",
                        "`source_api` = VALUES(`source_api`)", "`ingested_at` = VALUES(`ingested_at`)");
    }

    @Test
    void updatesOnlySourceColumnsWhenEveryCompositeBusinessColumnIsAKey() {
        String sql = factory.create(definition("only_key", List.of("first", "second"),
                BusinessKeyMode.COMPOSITE, List.of("first", "second")));

        assertThat(sql).isEqualTo("INSERT INTO `tushare_pro__only_key` (`first`, `second`, `source_plugin`, `source_api`, `ingested_at`) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `source_plugin` = VALUES(`source_plugin`), `source_api` = VALUES(`source_api`), `ingested_at` = VALUES(`ingested_at`)");
        assertThat(sql).doesNotEndWith(";");
    }

    @Test
    void appendsAndProtectsFingerprintBusinessKeyWhileUpdatingAllBusinessColumns() {
        String sql = factory.create(definition("fingerprint", List.of("identity", "value"),
                BusinessKeyMode.FINGERPRINT, List.of("identity")));

        assertThat(sql).isEqualTo("INSERT INTO `tushare_pro__fingerprint` (`identity`, `value`, `business_key`, `source_plugin`, `source_api`, `ingested_at`) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `identity` = VALUES(`identity`), `value` = VALUES(`value`), `source_plugin` = VALUES(`source_plugin`), `source_api` = VALUES(`source_api`), `ingested_at` = VALUES(`ingested_at`)");
        assertThat(sql).doesNotContain("`business_key` = VALUES");
    }

    @Test
    void isDeterministicAndDoesNotMutateDefinitionOrPlaceRowValuesInSql() {
        DatasetDefinition definition = daily();
        List<String> columnNames = definition.columns().stream().map(ColumnDefinition::name).toList();
        List<String> keyFields = definition.businessKey().fields();

        String first = factory.create(definition);
        String second = factory.create(definition);

        assertThat(second).isEqualTo(first);
        assertThat(definition.columns().stream().map(ColumnDefinition::name).toList()).isEqualTo(columnNames);
        assertThat(definition.businessKey().fields()).isEqualTo(keyFields);
        assertThat(first).matches("INSERT INTO `[a-z][a-z0-9_]{1,63}` \\((?:`[a-z][a-z0-9_]{1,63}`)(?:, `[a-z][a-z0-9_]{1,63}`)*\\) VALUES \\(\\?(?:, \\?)*\\) ON DUPLICATE KEY UPDATE `[a-z][a-z0-9_]{1,63}` = VALUES\\(`[a-z][a-z0-9_]{1,63}`\\)(?:, `[a-z][a-z0-9_]{1,63}` = VALUES\\(`[a-z][a-z0-9_]{1,63}`\\))*")
                .containsOnlyOnce("INSERT INTO")
                .doesNotContain("daily-row-value", "client-value", "'", "\"", ";", "--", "/*", "*/");
        assertThat(first.chars().filter(character -> character == '?').count()).isEqualTo(14);
    }

    private static void assertPublicSurface(Class<?> type, String methodName, Class<?> parameterType) throws Exception {
        Constructor<?> constructor = type.getConstructor();
        Method method = type.getDeclaredMethod(methodName, parameterType);

        assertThat(type.getConstructors()).containsExactly(constructor);
        assertThat(method.getReturnType()).isEqualTo(String.class);
        assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
        assertThat(type.getDeclaredMethods()).filteredOn(candidate -> Modifier.isPublic(candidate.getModifiers()))
                .containsExactly(method);
    }

    private static DatasetDefinition daily() {
        return definition("daily", List.of("ts_code", "trade_date", "open", "high", "low", "close", "pre_close",
                "change", "pct_chg", "vol", "amount"), BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date"));
    }

    private static DatasetDefinition definition(String api, List<String> names, BusinessKeyMode mode, List<String> keyFields) {
        DatasetKey datasetKey = new DatasetKey(new PluginId("tushare_pro"), new ApiName(api));
        return new DatasetDefinition(datasetKey, "Dataset", "market", QueryMode.trade_date, List.of(),
                TableName.from(datasetKey), names.stream().map(UpsertSqlFactoryTest::column).toList(),
                new BusinessKeyDefinition(mode, keyFields), List.of(), null, 500);
    }

    private static ColumnDefinition column(String name) {
        return new ColumnDefinition(name, name, LogicalType.TEXT, true, 0, null, null, null, List.of(), false);
    }
}
