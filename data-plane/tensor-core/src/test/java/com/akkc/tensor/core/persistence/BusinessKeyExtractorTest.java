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
