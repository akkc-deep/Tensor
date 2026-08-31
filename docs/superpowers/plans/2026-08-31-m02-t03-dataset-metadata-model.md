# M02-T03 Dataset Metadata Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the immutable Java dataset metadata model defined by M02-T03, including constructor invariants, the approved schema compatibility boundary, and complete contract tests.

**Architecture:** Add two enums and four records under `com.akkc.tensor.plugin.api.dataset`. The records reuse M02-T01 identifiers and M02-T02 descriptors, preserve ordered immutable input, validate local and cross-reference invariants at construction, and keep YAML/REST mapping outside plugin-api.

**Tech Stack:** Java 21 records, JUnit 5.12.2, AssertJ 3.27.7, Maven Surefire 3.5.6, Maven Enforcer 3.6.3.

**Spec:** `docs/task-designs/M02-T03-design.md`

## Global Constraints

- Work directly on the current `main` branch; do not create a worktree.
- Create exactly six production Java files and one test file named by the spec; do not modify POMs or existing Java types.
- Keep `tensor-plugin-api` free of Spring, JDBC, HTTP, concrete plugin, YAML, Jackson, and Vue dependencies.
- Preserve the M00-T02 schema: `FilterDefinition` has only `field`; `batchSize` is Java-only and defaults to 500 through an overloaded constructor.
- Use `^[a-z][a-z0-9_]{1,63}$` without trimming or case normalization for every new identifier string.
- Copy every list with `List.copyOf`, preserve declaration order, and reject null elements and duplicates required by the spec.
- Follow strict TDD: add the complete test first, record a missing-type `testCompile` failure, then add production types.
- The implementation commit must contain exactly the seven Java files and use `feat(plugin-api): define dataset metadata model`.

---

### Task 1: Implement and verify the complete dataset metadata contract

**Files:**
- Create: `data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinitionTest.java`
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/LogicalType.java`
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/ColumnDefinition.java`
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyMode.java`
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyDefinition.java`
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/FilterDefinition.java`
- Create: `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinition.java`

**Interfaces:**
- Consumes: `DatasetKey.of(PluginId, ApiName)`, `TableName.from(DatasetKey)`, `QueryMode`, and `ParameterDescriptor` from completed M02-T01/M02-T02.
- Produces: `LogicalType`, `ColumnDefinition`, `BusinessKeyMode`, `BusinessKeyDefinition`, `FilterDefinition`, and both `DatasetDefinition` constructors frozen in `docs/task-designs/M02-T03-design.md`.

- [ ] **Step 1: Confirm the clean implementation baseline**

Run:

```bash
git status --short
test ! -e data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset
test ! -e data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinitionTest.java
```

Expected: the working tree is clean and both delivery paths are absent.

- [ ] **Step 2: Write the complete failing contract test**

Create `DatasetDefinitionTest.java` with this content:

```java
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
```

- [ ] **Step 3: Run the focused test and record the expected RED**

Run:

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am \
  -Dtest=DatasetDefinitionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-zero exit during `testCompile` with missing symbols for the six dataset production types. Stop if the failure instead comes from dependency resolution, syntax, or environment configuration.

- [ ] **Step 4: Add the minimal production implementation**

Create `LogicalType.java`:

```java
package com.akkc.tensor.plugin.api.dataset;

public enum LogicalType {
    STRING,
    TEXT,
    DATE,
    MONTH,
    LONG,
    DECIMAL,
    ENUM
}
```

Create `BusinessKeyMode.java`:

```java
package com.akkc.tensor.plugin.api.dataset;

public enum BusinessKeyMode {
    COMPOSITE,
    FINGERPRINT
}
```

Create `FilterDefinition.java`:

```java
package com.akkc.tensor.plugin.api.dataset;

import java.util.Objects;
import java.util.regex.Pattern;

public record FilterDefinition(String field) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public FilterDefinition {
        Objects.requireNonNull(field, "field");
        if (!IDENTIFIER_PATTERN.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field: " + field);
        }
    }
}
```

Create `BusinessKeyDefinition.java`:

```java
package com.akkc.tensor.plugin.api.dataset;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record BusinessKeyDefinition(BusinessKeyMode mode, List<String> fields) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public BusinessKeyDefinition {
        Objects.requireNonNull(mode, "mode");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        if (fields.size() != new HashSet<>(fields).size()) {
            throw new IllegalArgumentException("fields must not contain duplicates");
        }
        for (String field : fields) {
            if (!IDENTIFIER_PATTERN.matcher(field).matches()) {
                throw new IllegalArgumentException("Invalid field: " + field);
            }
        }
    }
}
```

Create `ColumnDefinition.java`:

```java
package com.akkc.tensor.plugin.api.dataset;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record ColumnDefinition(
        String name,
        String label,
        LogicalType logicalType,
        boolean nullable,
        int displayOrder,
        Integer length,
        Integer precision,
        Integer scale,
        List<String> allowedValues,
        boolean longText
) {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public ColumnDefinition {
        Objects.requireNonNull(name, "name");
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid name: " + name);
        }
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        Objects.requireNonNull(logicalType, "logicalType");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must be non-negative");
        }
        if (length != null && length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        if (precision != null && (precision < 1 || precision > 65)) {
            throw new IllegalArgumentException("precision must be between 1 and 65");
        }
        if (scale != null && (scale < 0 || scale > 30)) {
            throw new IllegalArgumentException("scale must be between 0 and 30");
        }
        if ((logicalType == LogicalType.STRING || logicalType == LogicalType.ENUM) && length == null) {
            throw new IllegalArgumentException(logicalType + " columns require length");
        }
        if (logicalType == LogicalType.DECIMAL && (precision == null || scale == null)) {
            throw new IllegalArgumentException("DECIMAL columns require precision and scale");
        }
        allowedValues = List.copyOf(Objects.requireNonNull(allowedValues, "allowedValues"));
        if (allowedValues.size() != new HashSet<>(allowedValues).size()) {
            throw new IllegalArgumentException("allowedValues must not contain duplicates");
        }
    }
}
```

Create `DatasetDefinition.java`:

```java
package com.akkc.tensor.plugin.api.dataset;

import com.akkc.tensor.plugin.api.descriptor.ParameterDescriptor;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.TableName;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public record DatasetDefinition(
        DatasetKey datasetKey,
        String displayName,
        String category,
        QueryMode queryMode,
        List<ParameterDescriptor> parameters,
        TableName tableName,
        List<ColumnDefinition> columns,
        BusinessKeyDefinition businessKey,
        List<FilterDefinition> filters,
        String fixedColumn,
        int batchSize
) {
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public DatasetDefinition {
        Objects.requireNonNull(datasetKey, "datasetKey");
        requireNonBlank(displayName, "displayName");
        if (displayName.length() > 128) {
            throw new IllegalArgumentException("displayName must be at most 128 characters");
        }
        requireNonBlank(category, "category");
        if (category.length() > 64) {
            throw new IllegalArgumentException("category must be at most 64 characters");
        }
        Objects.requireNonNull(queryMode, "queryMode");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(tableName, "tableName");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        Objects.requireNonNull(businessKey, "businessKey");
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        rejectDuplicates(parameters, ParameterDescriptor::name, "parameters");
        rejectDuplicates(columns, ColumnDefinition::name, "columns");
        rejectDuplicates(filters, FilterDefinition::field, "filters");
        if (!tableName.equals(TableName.from(datasetKey))) {
            throw new IllegalArgumentException("tableName must match datasetKey");
        }

        Set<String> columnNames = columns.stream().map(ColumnDefinition::name).collect(java.util.stream.Collectors.toSet());
        requireReferences(columnNames, businessKey.fields(), "businessKey");
        requireReferences(columnNames, filters.stream().map(FilterDefinition::field).toList(), "filters");
        if (fixedColumn != null) {
            if (!IDENTIFIER_PATTERN.matcher(fixedColumn).matches()) {
                throw new IllegalArgumentException("Invalid fixedColumn: " + fixedColumn);
            }
            if (!columnNames.contains(fixedColumn)) {
                throw new IllegalArgumentException("fixedColumn must reference a column");
            }
        }
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("batchSize must be between 1 and 500");
        }
    }

    public DatasetDefinition(
            DatasetKey datasetKey,
            String displayName,
            String category,
            QueryMode queryMode,
            List<ParameterDescriptor> parameters,
            TableName tableName,
            List<ColumnDefinition> columns,
            BusinessKeyDefinition businessKey,
            List<FilterDefinition> filters,
            String fixedColumn) {
        this(datasetKey, displayName, category, queryMode, parameters, tableName, columns,
                businessKey, filters, fixedColumn, DEFAULT_BATCH_SIZE);
    }

    private static <T> void rejectDuplicates(List<T> values, Function<T, String> name, String component) {
        if (values.stream().map(name).collect(java.util.stream.Collectors.toSet()).size() != values.size()) {
            throw new IllegalArgumentException(component + " must not contain duplicate names");
        }
    }

    private static void requireReferences(Set<String> columns, List<String> references, String component) {
        if (!columns.containsAll(references)) {
            throw new IllegalArgumentException(component + " must reference declared columns");
        }
    }

    private static void requireNonBlank(String value, String component) {
        Objects.requireNonNull(value, component);
        if (value.isBlank()) {
            throw new IllegalArgumentException(component + " must not be blank");
        }
    }
}
```

- [ ] **Step 5: Run the focused GREEN test**

Run:

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am \
  -Dtest=DatasetDefinitionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `DatasetDefinitionTest` executes with 0 failures, 0 errors, 0 skipped; reactor 2/2 succeeds.

- [ ] **Step 6: Run module regression and Enforcer verification**

Run:

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am test
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am verify
```

Expected: both commands exit 0; all M02-T01, M02-T02, and M02-T03 tests pass; `ban-git-capabilities` passes for `data-plane` and `tensor-plugin-api`; no new warning category appears.

- [ ] **Step 7: Verify exact scope and remove generated output**

Run:

```bash
mvn -f data-plane/pom.xml -pl tensor-plugin-api -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app
git status --short --untracked-files=all -- data-plane/tensor-plugin-api
git diff --check
```

Expected: `clean` succeeds; POM/app diff check exits 0; status lists exactly six production Java files and `DatasetDefinitionTest.java`, with no `target`; format check exits 0.

- [ ] **Step 8: Commit the exact implementation**

Run:

```bash
git add \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/LogicalType.java \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/ColumnDefinition.java \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyMode.java \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/BusinessKeyDefinition.java \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/FilterDefinition.java \
  data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinition.java \
  data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/dataset/DatasetDefinitionTest.java
git commit -m "feat(plugin-api): define dataset metadata model"
git show --stat --oneline HEAD
```

Expected: the commit succeeds with the exact message and exactly seven Java files; preparation documentation is not part of the implementation commit.
