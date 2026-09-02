package com.akkc.tensor.core.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuerySqlFactoryTest {
    private final QuerySqlFactory factory = new QuerySqlFactory();

    @Test
    void exposesTheExactImmutableContractsAndValidatesQueryValues() throws Exception {
        assertRecordComponents(QueryCriteria.class, String.class, LocalDate.class, LocalDate.class,
                LocalDate.class, LocalDate.class, int.class, int.class);
        assertRecordComponents(QuerySql.class, String.class, List.class, String.class, List.class);
        assertThat(QuerySqlFactory.class.getModifiers()).matches(Modifier::isFinal);
        Constructor<QuerySqlFactory> constructor = QuerySqlFactory.class.getConstructor();
        Method create = QuerySqlFactory.class.getDeclaredMethod("create", DatasetDefinition.class, QueryCriteria.class);
        assertThat(QuerySqlFactory.class.getConstructors()).containsExactly(constructor);
        assertThat(QuerySqlFactory.class.getDeclaredMethods()).filteredOn(method -> Modifier.isPublic(method.getModifiers()))
                .containsExactly(create);

        QueryCriteria normalized = criteria(" \u2003000001.sz\u2003", null, null, null, null, 1, 50);
        assertThat(normalized.tsCode()).isEqualTo("000001.SZ");
        for (String invalid : List.of("", "000001", "000001.SZ.X", "000001.", ".SZ", "000 001.SZ", "000001-SZ", "x' OR 1=1 --")) {
            assertThatIllegalArgumentException().isThrownBy(() -> criteria(invalid, null, null, null, null, 1, 50))
                    .withMessage("tsCode has invalid format");
        }
        assertThatIllegalArgumentException().isThrownBy(() -> criteria(null, LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 1), null, null, 1, 50))
                .withMessage("tradeDateFrom must not be after tradeDateTo");
        assertThatIllegalArgumentException().isThrownBy(() -> criteria(null, null, null, LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 1), 1, 50))
                .withMessage("annDateFrom must not be after annDateTo");
        assertThatIllegalArgumentException().isThrownBy(() -> criteria(null, null, null, null, null, 0, 50))
                .withMessage("page must be at least 1");
        assertThatIllegalArgumentException().isThrownBy(() -> criteria(null, null, null, null, null, 1, 21))
                .withMessage("pageSize must be one of 20, 50, 100");

        List<Object> countValues = new ArrayList<>(List.of("count"));
        List<Object> pageValues = new ArrayList<>(List.of("page"));
        QuerySql sql = new QuerySql("count", countValues, "page", pageValues);
        countValues.add("changed");
        pageValues.add("changed");
        assertThat(sql.countValues()).containsExactly("count");
        assertThat(sql.pageValues()).containsExactly("page");
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> sql.countValues().add("no"));
        assertThatNullPointerException().isThrownBy(() -> new QuerySql(null, List.of(), "page", List.of())).withMessage("countSql");
        assertThatNullPointerException().isThrownBy(() -> new QuerySql("count", null, "page", List.of())).withMessage("countValues");
        assertThatNullPointerException().isThrownBy(() -> new QuerySql("count", List.of(), null, List.of())).withMessage("pageSql");
        assertThatNullPointerException().isThrownBy(() -> new QuerySql("count", List.of(), "page", null)).withMessage("pageValues");
        assertThatNullPointerException().isThrownBy(() -> factory.create(null, normalized)).withMessage("definition");
        assertThatNullPointerException().isThrownBy(() -> factory.create(composite(List.of()), null)).withMessage("criteria");
    }

    @Test
    void createsExactUnfilteredCompositeCountAndPageSql() {
        QuerySql sql = factory.create(composite(List.of()), criteria(null, null, null, null, null, 1, 50));

        assertThat(sql.countSql()).isEqualTo("SELECT COUNT(*) FROM `tushare_pro__daily`");
        assertThat(sql.pageSql()).isEqualTo("SELECT `ts_code`, `trade_date`, `close`, `source_plugin`, `source_api`, `ingested_at` FROM `tushare_pro__daily` ORDER BY `ts_code` ASC, `trade_date` ASC LIMIT ? OFFSET ?");
        assertThat(sql.countValues()).isEmpty();
        assertThat(sql.pageValues()).containsExactly(50, 0L);
    }

    @Test
    void createsEachSupportedSingleAndDateRangePredicateInFixedForm() {
        DatasetDefinition definition = composite(List.of("ts_code", "trade_date", "ann_date"));
        assertSql(definition, criteria("000001.SZ", null, null, null, null, 1, 20), "`ts_code` = ?", List.of("000001.SZ"));
        assertSql(definition, criteria(null, LocalDate.of(2026, 1, 2), null, null, null, 1, 20), "`trade_date` >= ?", List.of(LocalDate.of(2026, 1, 2)));
        assertSql(definition, criteria(null, null, LocalDate.of(2026, 1, 3), null, null, 1, 20), "`trade_date` <= ?", List.of(LocalDate.of(2026, 1, 3)));
        assertSql(definition, criteria(null, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), null, null, 1, 20), "`trade_date` BETWEEN ? AND ?", List.of(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3)));
        assertSql(definition, criteria(null, null, null, LocalDate.of(2026, 2, 2), null, 1, 20), "`ann_date` >= ?", List.of(LocalDate.of(2026, 2, 2)));
        assertSql(definition, criteria(null, null, null, null, LocalDate.of(2026, 2, 3), 1, 20), "`ann_date` <= ?", List.of(LocalDate.of(2026, 2, 3)));
        assertSql(definition, criteria(null, null, null, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 3), 1, 20), "`ann_date` BETWEEN ? AND ?", List.of(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 3)));
    }

    @Test
    void joinsAllProvidedFiltersAndBindsPageValuesAfterConditions() {
        QuerySql sql = factory.create(composite(List.of("ann_date", "trade_date", "ts_code")), criteria("000001.SZ",
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 3), 3, 20));

        assertThat(sql.countSql()).isEqualTo("SELECT COUNT(*) FROM `tushare_pro__daily` WHERE `ts_code` = ? AND `trade_date` BETWEEN ? AND ? AND `ann_date` BETWEEN ? AND ?");
        assertThat(sql.pageSql()).startsWith("SELECT `ts_code`, `trade_date`, `close`, `ann_date`, `source_plugin`, `source_api`, `ingested_at` FROM `tushare_pro__daily` WHERE `ts_code` = ? AND `trade_date` BETWEEN ? AND ? AND `ann_date` BETWEEN ? AND ? ORDER BY ");
        assertThat(sql.countValues()).containsExactly("000001.SZ", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 3));
        assertThat(sql.pageValues()).containsExactly("000001.SZ", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 3), 20, 40L);
    }

    @Test
    void rejectsProvidedFiltersThatTheDatasetDoesNotDeclare() {
        DatasetDefinition noFilters = composite(List.of());
        assertThat(factory.create(noFilters, criteria(null, null, null, null, null, 1, 50)).countSql())
                .isEqualTo("SELECT COUNT(*) FROM `tushare_pro__daily`");
        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(noFilters,
                criteria("000001.SZ", null, null, null, null, 1, 50)))
                .withMessage("Filter is not supported by dataset");
        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(composite(List.of("ts_code")),
                criteria(null, LocalDate.of(2026, 1, 1), null, null, null, 1, 50)))
                .withMessage("Filter is not supported by dataset");
        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(composite(List.of("trade_date")),
                criteria(null, null, null, null, LocalDate.of(2026, 1, 1), 1, 50)))
                .withMessage("Filter is not supported by dataset");
    }

    @Test
    void rejectsUnsupportedDatasetFilterMetadataBeforeGeneratingSql() {
        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(composite(List.of("close")),
                criteria(null, null, null, null, null, 1, 50)))
                .withMessage("Unsupported dataset filter metadata");
    }

    @Test
    void usesFingerprintIdentityFieldsThenInternalBusinessKeyOnlyForOrdering() {
        QuerySql sql = factory.create(definition("stk_managers", List.of("ts_code", "ann_date", "name"),
                BusinessKeyMode.FINGERPRINT, List.of("ts_code", "ann_date"), List.of("ts_code", "ann_date")),
                criteria(null, null, null, null, null, 1, 100));

        assertThat(sql.pageSql()).isEqualTo("SELECT `ts_code`, `ann_date`, `name`, `source_plugin`, `source_api`, `ingested_at` FROM `tushare_pro__stk_managers` ORDER BY `ts_code` ASC, `ann_date` ASC, `business_key` ASC LIMIT ? OFFSET ?");
        assertThat(sql.pageSql()).doesNotContain("SELECT `ts_code`, `ann_date`, `name`, `business_key`");
    }

    @Test
    void neverPlacesCriteriaOrPagingValuesIntoSqlAndQuotesOnlyMetadataIdentifiers() {
        QueryCriteria criteria = criteria("X12125.SZ", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), null, null, 2, 100);
        QuerySql sql = factory.create(composite(List.of("ts_code", "trade_date")), criteria);

        assertThat(sql.countSql()).doesNotContain("X12125.SZ", "2026-01-02", "2026-01-03", "100", "? ?")
                .containsOnlyOnce("`tushare_pro__daily`");
        assertThat(sql.pageSql()).doesNotContain("X12125.SZ", "2026-01-02", "2026-01-03", "100", "50")
                .contains("LIMIT ? OFFSET ?", "`ts_code`", "`trade_date`");
        assertThat(sql.countSql().chars().filter(value -> value == '?').count()).isEqualTo(3);
        assertThat(sql.pageSql().chars().filter(value -> value == '?').count()).isEqualTo(5);
        assertThatIllegalArgumentException().isThrownBy(() -> criteria("000001.SZ' OR 1=1 --", null, null, null, null, 1, 50))
                .withMessage("tsCode has invalid format");
    }

    private void assertSql(DatasetDefinition definition, QueryCriteria criteria, String condition, List<Object> values) {
        QuerySql sql = factory.create(definition, criteria);
        assertThat(sql.countSql()).isEqualTo("SELECT COUNT(*) FROM `tushare_pro__daily` WHERE " + condition);
        assertThat(sql.countValues()).containsExactlyElementsOf(values);
        List<Object> expectedPageValues = new ArrayList<>(values);
        expectedPageValues.add(20);
        expectedPageValues.add(0L);
        assertThat(sql.pageValues()).containsExactlyElementsOf(expectedPageValues);
    }

    private static void assertRecordComponents(Class<?> type, Class<?>... componentTypes) {
        assertThat(type.isRecord()).isTrue();
        assertThat(type.getRecordComponents()).extracting(component -> component.getName()).containsExactly(
                type == QueryCriteria.class ? new String[] {"tsCode", "tradeDateFrom", "tradeDateTo", "annDateFrom", "annDateTo", "page", "pageSize"}
                        : new String[] {"countSql", "countValues", "pageSql", "pageValues"});
        assertThat(Arrays.stream(type.getRecordComponents()).map(component -> component.getType()).toArray())
                .containsExactly((Object[]) componentTypes);
    }

    private static QueryCriteria criteria(String tsCode, LocalDate tradeDateFrom, LocalDate tradeDateTo,
            LocalDate annDateFrom, LocalDate annDateTo, int page, int pageSize) {
        return new QueryCriteria(tsCode, tradeDateFrom, tradeDateTo, annDateFrom, annDateTo, page, pageSize);
    }

    private static DatasetDefinition composite(List<String> filters) {
        List<String> names = new ArrayList<>(List.of("ts_code", "trade_date", "close"));
        if (filters.contains("ann_date")) {
            names.add("ann_date");
        }
        return definition("daily", names, BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date"), filters);
    }

    private static DatasetDefinition definition(String api, List<String> names, BusinessKeyMode mode, List<String> keyFields,
            List<String> filters) {
        DatasetKey datasetKey = new DatasetKey(new PluginId("tushare_pro"), new ApiName(api));
        return new DatasetDefinition(datasetKey, "Dataset", "market", QueryMode.trade_date, List.of(), TableName.from(datasetKey),
                names.stream().map(QuerySqlFactoryTest::column).toList(), new BusinessKeyDefinition(mode, keyFields),
                filters.stream().map(FilterDefinition::new).toList(), null, 500);
    }

    private static ColumnDefinition column(String name) {
        return new ColumnDefinition(name, name, LogicalType.TEXT, true, 0, null, null, null, List.of(), false);
    }
}
