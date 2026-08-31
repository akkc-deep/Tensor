package com.akkc.tensor.plugin.api.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.ParameterType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetDefinitionTest {

    @Test
    void exposesFrozenEnumsAndRecordComponents() {
        assertThat(LogicalType.values()).containsExactly(
                LogicalType.STRING, LogicalType.TEXT, LogicalType.DATE, LogicalType.MONTH,
                LogicalType.LONG, LogicalType.DECIMAL, LogicalType.ENUM);
        assertThat(BusinessKeyMode.values()).containsExactly(
                BusinessKeyMode.COMPOSITE, BusinessKeyMode.FINGERPRINT);
        assertThat(componentNames(ColumnDefinition.class)).containsExactly(
                "name", "label", "logicalType", "nullable", "displayOrder", "length",
                "precision", "scale", "allowedValues", "longText");
        assertThat(componentNames(BusinessKeyDefinition.class)).containsExactly("mode", "fields");
        assertThat(componentNames(FilterDefinition.class)).containsExactly("field");
        assertThat(componentNames(DatasetDefinition.class)).containsExactly(
                "datasetKey", "displayName", "category", "queryMode", "parameters", "tableName",
                "columns", "businessKey", "filters", "fixedColumn", "batchSize");
        assertThat(componentTypes(ColumnDefinition.class)).containsExactly(
                String.class, String.class, LogicalType.class, boolean.class, int.class,
                Integer.class, Integer.class, Integer.class, List.class, boolean.class);
        assertThat(componentTypes(BusinessKeyDefinition.class)).containsExactly(
                BusinessKeyMode.class, List.class);
        assertThat(componentTypes(FilterDefinition.class)).containsExactly(String.class);
        assertThat(componentTypes(DatasetDefinition.class)).containsExactly(
                DatasetKey.class, String.class, String.class, QueryMode.class, List.class, TableName.class,
                List.class, BusinessKeyDefinition.class, List.class, String.class, int.class);
        assertThat(Arrays.stream(DatasetDefinition.class.getConstructors())
                .mapToInt(constructor -> constructor.getParameterCount()))
                .containsExactlyInAnyOrder(10, 11);
    }

    @Test
    void constructsCompleteDailyDefinitionWithDefaultBatchSize() {
        DatasetDefinition definition = dailyDefinition(dailyColumns(), null, null, null, null);

        assertThat(definition.datasetKey()).isEqualTo(datasetKey("daily"));
        assertThat(definition.tableName()).isEqualTo(TableName.from(datasetKey("daily")));
        assertThat(definition.columns()).extracting(ColumnDefinition::name).containsExactly(
                "ts_code", "trade_date", "open", "high", "low", "close", "pre_close",
                "change", "pct_chg", "vol", "amount");
        assertThat(definition.businessKey().fields()).containsExactly("ts_code", "trade_date");
        assertThat(definition.filters()).extracting(FilterDefinition::field)
                .containsExactly("ts_code", "trade_date");
        assertThat(definition.fixedColumn()).isEqualTo("ts_code");
        assertThat(definition.batchSize()).isEqualTo(500);
    }

    @Test
    void validatesColumnLocalShape() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("Bad-Name", "Bad", LogicalType.DATE, false, 0, null, null, null, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("trade_date", " ", LogicalType.DATE, false, 0, null, null, null, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("trade_date", "Date", LogicalType.DATE, false, -1, null, null, null, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("ts_code", "Code", LogicalType.STRING, false, 0, 0, null, null, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("value", "Value", LogicalType.DECIMAL, true, 0, null, 66, 18, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("value", "Value", LogicalType.DECIMAL, true, 0, null, 38, 31, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("ts_code", "Code", LogicalType.STRING, false, 0, null, null, null, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("status", "Status", LogicalType.ENUM, false, 0, null, null, null, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("value", "Value", LogicalType.DECIMAL, true, 0, null, null, 18, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("value", "Value", LogicalType.DECIMAL, true, 0, null, 38, null, List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> column("status", "Status", LogicalType.ENUM, false, 0, 1, null, null,
                        List.of("Y", "Y"), false));
        assertThatNullPointerException().isThrownBy(
                () -> column("status", "Status", LogicalType.ENUM, false, 0, 1, null, null,
                        Arrays.asList("Y", null), false));
        assertThatNullPointerException().isThrownBy(
                () -> column("trade_date", "Date", null, false, 0, null, null, null, List.of(), false));
        assertThatNullPointerException().isThrownBy(
                () -> column("trade_date", "Date", LogicalType.DATE, false, 0, null, null, null, null, false));
    }

    @Test
    void validatesBusinessKeysAndFilters() {
        assertThat(new BusinessKeyDefinition(BusinessKeyMode.FINGERPRINT, List.of("ts_code")).fields())
                .containsExactly("ts_code");
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BusinessKeyDefinition(BusinessKeyMode.FINGERPRINT, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "ts_code")));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("Bad-Name")));
        assertThatNullPointerException().isThrownBy(
                () -> new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, Arrays.asList("ts_code", null)));
        assertThatNullPointerException().isThrownBy(
                () -> new BusinessKeyDefinition(null, List.of("ts_code")));
        assertThatIllegalArgumentException().isThrownBy(() -> new FilterDefinition("Trade-Date"));
        assertThatNullPointerException().isThrownBy(() -> new FilterDefinition(null));
    }

    @Test
    void validatesDatasetTopLevelShape() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> definition(" ", "market", List.of(parameter("trade_date")), dailyColumns(),
                        List.of(new FilterDefinition("ts_code"))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> definition("x".repeat(129), "market", List.of(parameter("trade_date")), dailyColumns(),
                        List.of(new FilterDefinition("ts_code"))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> definition("Daily", " ", List.of(parameter("trade_date")), dailyColumns(),
                        List.of(new FilterDefinition("ts_code"))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> definition("Daily", "x".repeat(65), List.of(parameter("trade_date")), dailyColumns(),
                        List.of(new FilterDefinition("ts_code"))));
        assertThatIllegalArgumentException().isThrownBy(
                () -> definition("Daily", "market", List.of(parameter("trade_date")), List.of(),
                        List.of(new FilterDefinition("ts_code"))));
        assertThatNullPointerException().isThrownBy(
                () -> definition("Daily", "market", null, dailyColumns(),
                        List.of(new FilterDefinition("ts_code"))));
        assertThatNullPointerException().isThrownBy(
                () -> definition("Daily", "market", List.of(parameter("trade_date")), null,
                        List.of(new FilterDefinition("ts_code"))));
        assertThatNullPointerException().isThrownBy(
                () -> definition("Daily", "market", List.of(parameter("trade_date")), dailyColumns(), null));
        assertThatNullPointerException().isThrownBy(
                () -> definition("Daily", "market", Arrays.asList(parameter("trade_date"), null), dailyColumns(),
                        List.of(new FilterDefinition("ts_code"))));
        assertThatNullPointerException().isThrownBy(
                () -> definition("Daily", "market", List.of(parameter("trade_date")),
                        Arrays.asList(stringColumn("ts_code", 0), null),
                        List.of(new FilterDefinition("ts_code"))));
        assertThatNullPointerException().isThrownBy(
                () -> definition("Daily", "market", List.of(parameter("trade_date")), dailyColumns(),
                        Arrays.asList(new FilterDefinition("ts_code"), null)));
    }

    @Test
    void rejectsDuplicateNamesAndMissingReferences() {
        List<ColumnDefinition> duplicateColumns = new ArrayList<>(dailyColumns());
        duplicateColumns.add(stringColumn("ts_code", 11));
        assertThatIllegalArgumentException().isThrownBy(
                () -> dailyDefinition(duplicateColumns, null, null, null, null));

        ParameterDescriptor parameter = parameter("trade_date");
        assertThatIllegalArgumentException().isThrownBy(
                () -> dailyDefinition(dailyColumns(), List.of(parameter, parameter), null, null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> dailyDefinition(dailyColumns(), null, null,
                        List.of(new FilterDefinition("ts_code"), new FilterDefinition("ts_code")), null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> dailyDefinition(dailyColumns(), null,
                        new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("missing_column")), null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> dailyDefinition(dailyColumns(), null, null,
                        List.of(new FilterDefinition("missing_column")), null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> dailyDefinition(dailyColumns(), null, null, null, "missing_column"));
    }

    @Test
    void rejectsTableNameMismatchAndInvalidBatchSizes() {
        DatasetKey key = datasetKey("daily");
        assertThatIllegalArgumentException().isThrownBy(() -> new DatasetDefinition(
                key, "Daily", "market", QueryMode.trade_date, List.of(parameter("trade_date")),
                new TableName("tushare_pro__weekly"), dailyColumns(),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(new FilterDefinition("ts_code")), "ts_code", 500));
        for (int invalid : List.of(-1, 0, 501)) {
            assertThatIllegalArgumentException().isThrownBy(() -> dailyDefinitionWithBatchSize(invalid));
        }
        assertThat(dailyDefinitionWithBatchSize(1).batchSize()).isEqualTo(1);
        assertThat(dailyDefinitionWithBatchSize(500).batchSize()).isEqualTo(500);
    }

    @Test
    void permitsAbsentFixedColumnWithoutDerivingRestDefault() {
        DatasetDefinition definition = new DatasetDefinition(
                datasetKey("daily"), "Daily", "market", QueryMode.trade_date,
                List.of(parameter("trade_date")), TableName.from(datasetKey("daily")), dailyColumns(),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(new FilterDefinition("ts_code")), null);

        assertThat(definition.fixedColumn()).isNull();
        assertThat(definition.batchSize()).isEqualTo(500);
    }

    @Test
    void makesAllListsImmutableOrderedCopies() {
        List<String> values = new ArrayList<>(List.of("Y", "N"));
        ColumnDefinition status = column(
                "status", "Status", LogicalType.ENUM, true, 0, 1, null, null, values, false);
        values.clear();
        assertThat(status.allowedValues()).containsExactly("Y", "N");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> status.allowedValues().add("U"));

        List<String> keyFields = new ArrayList<>(List.of("ts_code", "trade_date"));
        BusinessKeyDefinition key = new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, keyFields);
        keyFields.clear();
        assertThat(key.fields()).containsExactly("ts_code", "trade_date");
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> key.fields().clear());

        List<ParameterDescriptor> parameters = new ArrayList<>(List.of(parameter("trade_date")));
        List<ColumnDefinition> columns = new ArrayList<>(dailyColumns());
        List<FilterDefinition> filters = new ArrayList<>(List.of(
                new FilterDefinition("ts_code"), new FilterDefinition("trade_date")));
        DatasetDefinition definition = dailyDefinition(columns, parameters, key, filters, "ts_code");
        parameters.clear();
        columns.clear();
        filters.clear();
        assertThat(definition.parameters()).hasSize(1);
        assertThat(definition.columns()).hasSize(11);
        assertThat(definition.filters()).extracting(FilterDefinition::field)
                .containsExactly("ts_code", "trade_date");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> definition.columns().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> definition.parameters().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> definition.filters().clear());
    }

    private static DatasetDefinition definition(
            String displayName,
            String category,
            List<ParameterDescriptor> parameters,
            List<ColumnDefinition> columns,
            List<FilterDefinition> filters) {
        DatasetKey key = datasetKey("daily");
        return new DatasetDefinition(
                key, displayName, category, QueryMode.trade_date, parameters, TableName.from(key), columns,
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                filters, "ts_code");
    }

    private static DatasetDefinition dailyDefinition(
            List<ColumnDefinition> columns,
            List<ParameterDescriptor> parameters,
            BusinessKeyDefinition businessKey,
            List<FilterDefinition> filters,
            String fixedColumn) {
        DatasetKey key = datasetKey("daily");
        return new DatasetDefinition(
                key,
                "Daily",
                "market",
                QueryMode.trade_date,
                parameters == null ? List.of(parameter("trade_date")) : parameters,
                TableName.from(key),
                columns,
                businessKey == null
                        ? new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date"))
                        : businessKey,
                filters == null
                        ? List.of(new FilterDefinition("ts_code"), new FilterDefinition("trade_date"))
                        : filters,
                fixedColumn == null ? "ts_code" : fixedColumn);
    }

    private static DatasetDefinition dailyDefinitionWithBatchSize(int batchSize) {
        DatasetKey key = datasetKey("daily");
        return new DatasetDefinition(
                key, "Daily", "market", QueryMode.trade_date, List.of(parameter("trade_date")),
                TableName.from(key), dailyColumns(),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code", "trade_date")),
                List.of(new FilterDefinition("ts_code"), new FilterDefinition("trade_date")), "ts_code", batchSize);
    }

    private static List<ColumnDefinition> dailyColumns() {
        return List.of(
                stringColumn("ts_code", 0),
                column("trade_date", "Trade date", LogicalType.DATE, false, 1, null, null, null, List.of(), false),
                decimalColumn("open", 2),
                decimalColumn("high", 3),
                decimalColumn("low", 4),
                decimalColumn("close", 5),
                decimalColumn("pre_close", 6),
                decimalColumn("change", 7),
                decimalColumn("pct_chg", 8),
                decimalColumn("vol", 9),
                decimalColumn("amount", 10));
    }

    private static ColumnDefinition stringColumn(String name, int displayOrder) {
        return column(name, name, LogicalType.STRING, false, displayOrder, 16, null, null, List.of(), false);
    }

    private static ColumnDefinition decimalColumn(String name, int displayOrder) {
        return column(name, name, LogicalType.DECIMAL, true, displayOrder, null, 38, 18, List.of(), false);
    }

    private static ColumnDefinition column(
            String name,
            String label,
            LogicalType logicalType,
            boolean nullable,
            int displayOrder,
            Integer length,
            Integer precision,
            Integer scale,
            List<String> allowedValues,
            boolean longText) {
        return new ColumnDefinition(
                name, label, logicalType, nullable, displayOrder, length, precision, scale, allowedValues, longText);
    }

    private static ParameterDescriptor parameter(String name) {
        return new ParameterDescriptor(
                name, "Trade date", "Trading day", ParameterType.DATE, true,
                null, List.of(), null, null);
    }

    private static DatasetKey datasetKey(String apiName) {
        return DatasetKey.of(PluginId.of("tushare_pro"), ApiName.of(apiName));
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<Class<?>> componentTypes(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toList();
    }
}
