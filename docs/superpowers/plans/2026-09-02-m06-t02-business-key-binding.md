# M06-T02 Business Key and JDBC Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement immutable ordered business keys, metadata-directed COMPOSITE/FINGERPRINT extraction, and explicit JDBC value binding for M06-T02.

**Architecture:** `BusinessKeyExtractor` converts one already-adapted row into a structural `BusinessKey`: COMPOSITE values follow metadata order, while FINGERPRINT consumes the single `business_key` produced by M05. `JdbcValueBinder` maps each approved Java runtime type to one explicit `PreparedStatement` setter, uses a caller-supplied JDBC type for null, and binds `Instant` with an explicit UTC calendar.

**Tech Stack:** Java 21, JDBC, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- Implement only board task `M06-T02`; do not start M06-T03 or later work.
- Create exactly three production files and one test file under `tensor-core`; do not modify existing Java, POM, YAML, SQL, or other modules.
- Preserve M05 `FingerprintKeyCodec` as the only fingerprint encoder; production persistence code must not import or call it.
- Do not use `PreparedStatement.setObject`, a system-default timezone, a database, Spring context, network, or current time.
- Keep the production API and fixed error messages exactly as specified in `docs/task-designs/M06-T02-design.md`.
- Follow strict TDD: establish the 138-test baseline, create the complete test first, observe a missing-type RED, then add the minimum implementation.
- The implementation commit must contain exactly the four task Java files and use `feat(core): bind dataset keys and JDBC values`.

---

### Task 1: Implement business keys and explicit JDBC binding

**Files:**

- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java`
- Create: `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java`
- Test: `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/BusinessKeyExtractorTest.java`

**Interfaces:**

- Consumes: `DatasetDefinition.businessKey()`, M05-adapted row maps, and FINGERPRINT rows containing the existing `business_key` column.
- Produces: `BusinessKeyExtractor.extract(DatasetDefinition, Map<String,Object>)` for M06-T03 key preflight and `JdbcValueBinder.bind(PreparedStatement, int, Object, int)` for M06-T03/M06-T04 prepared statements.

- [ ] **Step 1: Read the frozen sources in order**

Read:

```text
docs/task-designs/M06-T02-design.md
docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md (Task M06-T02)
docs/task-designs/M05-T05-design.md
data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java
data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java
data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/UpsertSqlFactoryTest.java
```

Confirm the task is still `READY`, its design and handoff paths match the board, and the worktree has no conflicting changes. Do not change the task contract while implementing it.

- [ ] **Step 2: Establish the current reactor baseline**

Run in an environment that permits Mockito/Byte Buddy JVM attach:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
```

Expected: plugin-api 79/79 and core 59/59, total 138/138; zero failures, errors, or skipped tests; parent/plugin-api/core Enforcer rules pass. Treat the known ten attach errors in a restricted sandbox as environment failures, not as the baseline.

- [ ] **Step 3: Create the complete failing test**

Create `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/BusinessKeyExtractorTest.java` with exactly these eight tests:

```java
package com.akkc.tensor.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BusinessKeyExtractorTest {
    private final BusinessKeyExtractor extractor = new BusinessKeyExtractor();
    private final JdbcValueBinder binder = new JdbcValueBinder();

    @Test
    void exposesOnlyTheSpecifiedPublicContractsAndConstructionBoundaries() throws Exception {
        assertThat(BusinessKey.class.isRecord()).isTrue();
        assertThat(BusinessKey.class.getConstructors()).containsExactly(BusinessKey.class.getConstructor(List.class));
        assertStatelessFinal(BusinessKeyExtractor.class);
        assertStatelessFinal(JdbcValueBinder.class);

        Method extract = BusinessKeyExtractor.class.getDeclaredMethod("extract", DatasetDefinition.class, Map.class);
        assertThat(extract.getReturnType()).isEqualTo(BusinessKey.class);
        assertThat(publicDeclaredMethods(BusinessKeyExtractor.class)).containsExactly(extract);

        Method bind = JdbcValueBinder.class.getDeclaredMethod(
                "bind", PreparedStatement.class, int.class, Object.class, int.class);
        assertThat(bind.getReturnType()).isEqualTo(void.class);
        assertThat(bind.getExceptionTypes()).containsExactly(SQLException.class);
        assertThat(publicDeclaredMethods(JdbcValueBinder.class)).containsExactly(bind);

        assertThatNullPointerException().isThrownBy(() -> new BusinessKey(null)).withMessage("values");
        assertThatIllegalArgumentException().isThrownBy(() -> new BusinessKey(List.of()))
                .withMessage("values must not be empty");
        assertThatIllegalArgumentException().isThrownBy(() -> new BusinessKey(Arrays.asList("key", null)))
                .withMessage("values must not contain null");
        assertThatNullPointerException().isThrownBy(() -> extractor.extract(null, Map.of()))
                .withMessage("definition");
        assertThatNullPointerException().isThrownBy(() -> extractor.extract(compositeDefinition(), null))
                .withMessage("row");
        assertThatNullPointerException().isThrownBy(() -> binder.bind(null, 1, "value", Types.VARCHAR))
                .withMessage("statement");
    }

    @Test
    void extractsOrderedImmutableCompositeKeysWithStructuralEquality() {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("other", "ignored");
        row.put("second", 2L);
        row.put("first", "first-value");
        Map<String, Object> snapshot = new LinkedHashMap<>(row);

        BusinessKey key = extractor.extract(compositeDefinition(), row);

        assertThat(key.values()).containsExactly("first-value", 2L);
        assertThat(key).isEqualTo(new BusinessKey(List.of("first-value", 2L)))
                .hasSameHashCodeAs(new BusinessKey(List.of("first-value", 2L)))
                .isNotEqualTo(new BusinessKey(List.of(2L, "first-value")));
        assertThat(row).isEqualTo(snapshot);
        assertThatThrownBy(() -> key.values().add("change"))
                .isInstanceOf(UnsupportedOperationException.class);

        List<Object> source = new ArrayList<>(List.of("stable", 3L));
        BusinessKey copied = new BusinessKey(source);
        source.set(0, "changed");
        assertThat(copied.values()).containsExactly("stable", 3L);
    }

    @Test
    void extractsTheExactFingerprintProducedByTheM05Codec() {
        List<String> fields = List.of("text", "missing", "count", "amount");
        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("text", "中");
        source.put("missing", null);
        source.put("count", 42L);
        source.put("amount", new BigDecimal("1.20"));
        String fingerprint = new FingerprintKeyCodec().sha256(fields, source);
        assertThat(fingerprint)
                .isEqualTo("c593b786a7708a9b7a106e244094f1cabd200caa3e95fad3b041225c17ac19ad");
        LinkedHashMap<String, Object> adaptedRow = new LinkedHashMap<>(source);
        adaptedRow.put("business_key", fingerprint);

        BusinessKey first = extractor.extract(fingerprintDefinition(fields), adaptedRow);
        BusinessKey second = extractor.extract(fingerprintDefinition(fields), adaptedRow);

        assertThat(first.values()).containsExactly(fingerprint);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void rejectsMissingNullAndInvalidKeysWithFixedSafeErrors() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> extractor.extract(compositeDefinition(), Map.of("first", "value")))
                .withMessage("Missing business key");
        LinkedHashMap<String, Object> nullComposite = new LinkedHashMap<>();
        nullComposite.put("first", "value");
        nullComposite.put("second", null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> extractor.extract(compositeDefinition(), nullComposite))
                .withMessage("Missing business key");

        DatasetDefinition fingerprint = fingerprintDefinition(List.of("text"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> extractor.extract(fingerprint, Map.of("text", "value")))
                .withMessage("Missing business key");
        LinkedHashMap<String, Object> nullFingerprint = new LinkedHashMap<>();
        nullFingerprint.put("business_key", null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> extractor.extract(fingerprint, nullFingerprint))
                .withMessage("Missing business key");
        for (Object invalid : List.of(42L, "a".repeat(63), "A".repeat(64), "g".repeat(64))) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> extractor.extract(fingerprint, Map.of("business_key", invalid)))
                    .withMessage("Invalid fingerprint business key");
        }
    }

    @Test
    void bindsStringDateLongAndBigDecimalWithExplicitSetters() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        LocalDate date = LocalDate.of(2026, 9, 2);
        BigDecimal amount = new BigDecimal("123.4500");

        binder.bind(statement, 1, "text", Types.VARCHAR);
        binder.bind(statement, 2, date, Types.DATE);
        binder.bind(statement, 3, 42L, Types.BIGINT);
        binder.bind(statement, 4, amount, Types.DECIMAL);

        verify(statement).setString(1, "text");
        verify(statement).setDate(2, java.sql.Date.valueOf(date));
        verify(statement).setLong(3, 42L);
        verify(statement).setBigDecimal(4, amount);
        verifyNoMoreInteractions(statement);
    }

    @Test
    void bindsInstantWithAnExplicitUtcCalendarWithoutPrecisionLoss() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        Instant instant = Instant.parse("2026-09-01T23:59:59.123456789Z");
        ArgumentCaptor<Timestamp> timestamp = ArgumentCaptor.forClass(Timestamp.class);
        ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);

        binder.bind(statement, 1, instant, Types.TIMESTAMP);

        verify(statement).setTimestamp(org.mockito.ArgumentMatchers.eq(1), timestamp.capture(), calendar.capture());
        assertThat(timestamp.getValue().toInstant()).isEqualTo(instant);
        assertThat(calendar.getValue().getTimeZone().getID()).isEqualTo("UTC");
        verifyNoMoreInteractions(statement);
    }

    @Test
    void bindsNullWithTheCallerSuppliedJdbcTypeAndNeverUsesSetObject() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        binder.bind(statement, 1, null, Types.VARCHAR);
        binder.bind(statement, 2, null, Types.DECIMAL);
        binder.bind(statement, 3, null, Types.TIMESTAMP);

        verify(statement).setNull(1, Types.VARCHAR);
        verify(statement).setNull(2, Types.DECIMAL);
        verify(statement).setNull(3, Types.TIMESTAMP);
        verify(statement, never()).setObject(anyInt(), any());
        verifyNoMoreInteractions(statement);
    }

    @Test
    void rejectsInvalidIndexesAndUnsupportedTypesAndPropagatesSqlExceptions() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> binder.bind(statement, 0, "value", Types.VARCHAR))
                .withMessage("index must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> binder.bind(statement, 1, new Object(), Types.VARCHAR))
                .withMessage("Unsupported JDBC value type");
        verifyNoInteractions(statement);

        SQLException failure = new SQLException("driver failure");
        doThrow(failure).when(statement).setString(1, "safe");
        assertThatThrownBy(() -> binder.bind(statement, 1, "safe", Types.VARCHAR)).isSameAs(failure);
    }

    private static void assertStatelessFinal(Class<?> type) throws Exception {
        assertThat(Modifier.isFinal(type.getModifiers())).isTrue();
        assertThat(type.getConstructors()).containsExactly(type.getConstructor());
        assertThat(Arrays.stream(type.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .toList())
                .isEmpty();
    }

    private static List<Method> publicDeclaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
    }

    private static DatasetDefinition compositeDefinition() {
        return definition(BusinessKeyMode.COMPOSITE, List.of("first", "second", "other"),
                List.of("first", "second"));
    }

    private static DatasetDefinition fingerprintDefinition(List<String> fields) {
        return definition(BusinessKeyMode.FINGERPRINT, fields, fields);
    }

    private static DatasetDefinition definition(
            BusinessKeyMode mode, List<String> columns, List<String> keyFields) {
        String api = mode == BusinessKeyMode.COMPOSITE ? "composite" : "fingerprint";
        DatasetKey datasetKey = new DatasetKey(new PluginId("tushare_pro"), new ApiName(api));
        return new DatasetDefinition(
                datasetKey,
                "Dataset",
                "market",
                QueryMode.trade_date,
                List.of(),
                TableName.from(datasetKey),
                columns.stream().map(BusinessKeyExtractorTest::column).toList(),
                new BusinessKeyDefinition(mode, keyFields),
                List.of(),
                null,
                500);
    }

    private static ColumnDefinition column(String name) {
        return new ColumnDefinition(name, name, LogicalType.TEXT, true, 0,
                null, null, null, List.of(), false);
    }
}
```

- [ ] **Step 4: Run the focused test and verify the RED**

Run:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am -Dtest=BusinessKeyExtractorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: non-zero in `tensor-core:testCompile` only because `BusinessKey`, `BusinessKeyExtractor`, and `JdbcValueBinder` do not exist. Fix test syntax before proceeding if any other cause appears.

- [ ] **Step 5: Implement the immutable structural key**

Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java`:

```java
package com.akkc.tensor.core.persistence;

import java.util.List;
import java.util.Objects;

public record BusinessKey(List<Object> values) {
    public BusinessKey {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("values must not contain null");
        }
        values = List.copyOf(values);
    }
}
```

- [ ] **Step 6: Implement metadata-ordered key extraction**

Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java`:

```java
package com.akkc.tensor.core.persistence;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class BusinessKeyExtractor {
    private static final String FINGERPRINT_COLUMN = "business_key";
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public BusinessKey extract(DatasetDefinition definition, Map<String, Object> row) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(row, "row");
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            return fingerprint(row);
        }
        List<Object> values = new ArrayList<>();
        for (String field : definition.businessKey().fields()) {
            if (!row.containsKey(field) || row.get(field) == null) {
                throw new IllegalArgumentException("Missing business key");
            }
            values.add(row.get(field));
        }
        return new BusinessKey(values);
    }

    private BusinessKey fingerprint(Map<String, Object> row) {
        if (!row.containsKey(FINGERPRINT_COLUMN) || row.get(FINGERPRINT_COLUMN) == null) {
            throw new IllegalArgumentException("Missing business key");
        }
        Object value = row.get(FINGERPRINT_COLUMN);
        if (!(value instanceof String fingerprint) || !FINGERPRINT_PATTERN.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("Invalid fingerprint business key");
        }
        return new BusinessKey(List.of(fingerprint));
    }
}
```

Do not import `FingerprintKeyCodec`; the adapted row already contains its result.

- [ ] **Step 7: Implement explicit JDBC setter dispatch**

Create `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java`:

```java
package com.akkc.tensor.core.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;

public final class JdbcValueBinder {
    public void bind(PreparedStatement statement, int index, Object value, int jdbcType) throws SQLException {
        Objects.requireNonNull(statement, "statement");
        if (index < 1) {
            throw new IllegalArgumentException("index must be positive");
        }
        if (value == null) {
            statement.setNull(index, jdbcType);
        } else if (value instanceof String text) {
            statement.setString(index, text);
        } else if (value instanceof LocalDate date) {
            statement.setDate(index, Date.valueOf(date));
        } else if (value instanceof Long number) {
            statement.setLong(index, number);
        } else if (value instanceof BigDecimal decimal) {
            statement.setBigDecimal(index, decimal);
        } else if (value instanceof Instant instant) {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            statement.setTimestamp(index, Timestamp.from(instant), utc);
        } else {
            throw new IllegalArgumentException("Unsupported JDBC value type");
        }
    }
}
```

- [ ] **Step 8: Run the focused test and verify GREEN**

Run the same focused command from Step 4.

Expected: `BusinessKeyExtractorTest` 8/8, zero failures, errors, or skipped tests. Verify the test contains exactly eight `@Test` annotations and no parameterized/dynamic tests.

- [ ] **Step 9: Run module regression and verification**

Run in the JVM-attach-capable environment:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am test
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am verify
```

Expected for each command: plugin-api 79/79, core 67/67, total 146/146; zero failures, errors, or skipped tests; all three Enforcer levels pass.

- [ ] **Step 10: Run static, scope, formatting, and cleanup gates**

Run the two scans; each must produce no output and exit 1:

```bash
rg -n 'org\.springframework|javax\.sql|DataSource|JdbcTemplate|tushare|RestClient|ServiceLoader|(?i:token|credential)|setObject|Instant\.now|ZoneId\.systemDefault|TimeZone\.getDefault' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java
rg -n 'MessageDigest|SHA-256|FingerprintKeyCodec|ByteBuffer|StandardCharsets' \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java
```

Run the remaining gates:

```bash
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml \
  -pl tensor-core -am clean
git diff --quiet -- data-plane/pom.xml data-plane/tensor-app \
  data-plane/tensor-plugin-api data-plane/tensor-plugin-tushare \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java
git status --short --untracked-files=all -- \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence
git diff --check
```

Expected: clean exits 0; no protected path diff; scoped status lists exactly the four new task files and no `target`; diff check exits 0.

- [ ] **Step 11: Commit the exact implementation scope**

Run:

```bash
git add \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKey.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java \
  data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/JdbcValueBinder.java \
  data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/BusinessKeyExtractorTest.java
git diff --cached --check
git diff --cached --name-only
git commit -m "feat(core): bind dataset keys and JDBC values"
git show --stat --oneline HEAD
```

Expected: the staged-name check and commit contain exactly the four listed Java files; the commit subject is exact. Leave task-state completion and successor preparation to the authoritative task-board workflow after fresh verification and review.
