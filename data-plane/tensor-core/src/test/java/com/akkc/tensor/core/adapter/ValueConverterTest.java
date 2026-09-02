package com.akkc.tensor.core.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValueConverterTest {
    private static final ApiName API_NAME = new ApiName("daily");
    private static final ConversionContext CONTEXT = new ConversionContext(API_NAME, 0);
    private final ValueConverter converter = new ValueConverter();

    @Test
    void exposesOnlyTheSpecifiedConverterAndContextContracts() throws Exception {
        assertThat(Modifier.isFinal(ValueConverter.class.getModifiers())).isTrue();
        assertThat(ValueConverter.class.getConstructors()).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).isEmpty());
        Method convert = ValueConverter.class.getDeclaredMethod(
                "convert", Object.class, ColumnDefinition.class, ConversionContext.class);
        assertThat(convert.getReturnType()).isEqualTo(Object.class);
        assertThat(Modifier.isPublic(convert.getModifiers())).isTrue();
        assertThat(ValueConverter.class.getDeclaredMethods())
                .filteredOn(method -> Modifier.isPublic(method.getModifiers()))
                .containsExactly(convert);

        assertThat(ConversionContext.class.isRecord()).isTrue();
        assertThat(ConversionContext.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("apiName", "rowIndex");
        assertThatNullPointerException().isThrownBy(() -> new ConversionContext(null, 0));
        assertThatThrownBy(() -> new ConversionContext(API_NAME, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsNullForNullSourceWithoutReadingNullabilityAndRejectsNullMetadata() {
        assertThat(converter.convert(null, column("nullable", LogicalType.STRING, true, 5, null, null, List.of()), CONTEXT))
                .isNull();
        assertThat(converter.convert(null, column("required", LogicalType.STRING, false, 5, null, null, List.of()), CONTEXT))
                .isNull();
        assertThatNullPointerException().isThrownBy(() -> converter.convert("value", null, CONTEXT));
        assertThatNullPointerException().isThrownBy(() -> converter.convert(
                "value", column("field", LogicalType.STRING, true, 5, null, null, List.of()), null));
    }

    @Test
    void convertsShortStringsByTrimAndUnicodeCodePointLengthWithoutTruncation() {
        ColumnDefinition column = column("code", LogicalType.STRING, true, 2, null, null, List.of());

        assertThat(converter.convert("  ab  ", column, CONTEXT)).isEqualTo("ab");
        assertThat(converter.convert(" \t ", column, CONTEXT)).isNull();
        assertThat(converter.convert("😀a", column, CONTEXT)).isEqualTo("😀a");
        assertInvalid(() -> converter.convert("😀ab", column, CONTEXT), "code", "😀ab");
    }

    @Test
    void preservesTextExactlyIncludingWhitespaceAndEmptyValues() {
        ColumnDefinition column = column("memo", LogicalType.TEXT, true, null, null, null, List.of());

        assertThat(converter.convert("ordinary", column, CONTEXT)).isEqualTo("ordinary");
        assertThat(converter.convert("  kept  ", column, CONTEXT)).isEqualTo("  kept  ");
        assertThat(converter.convert("", column, CONTEXT)).isEqualTo("");
        assertThat(converter.convert(" \t ", column, CONTEXT)).isEqualTo(" \t ");
    }

    @Test
    void convertsOpenAndClosedEnumsWithExactCaseSensitiveMembers() {
        ColumnDefinition open = column("state", LogicalType.ENUM, true, 2, null, null, List.of());
        ColumnDefinition closed = column("state", LogicalType.ENUM, true, 2, null, null, List.of("UP", "DN"));

        assertThat(converter.convert(" up ", open, CONTEXT)).isEqualTo("up");
        assertThat(converter.convert("  ", closed, CONTEXT)).isNull();
        assertThat(converter.convert(" UP ", closed, CONTEXT)).isEqualTo("UP");
        assertInvalid(() -> converter.convert("up", closed, CONTEXT), "state", "up");
        assertInvalid(() -> converter.convert("LONG", closed, CONTEXT), "state", "LONG");
    }

    @Test
    void convertsOnlyAsciiEightDigitStrictCalendarDates() {
        ColumnDefinition column = column("trade_date", LogicalType.DATE, true, null, null, null, List.of());

        assertThat(converter.convert(" 20240229 ", column, CONTEXT)).isEqualTo(LocalDate.of(2024, 2, 29));
        for (String invalid : List.of(
                "2024-02-29", "2024022", "020240229", "+123450228", "-00010101", "２０２４０２２９", "20240230")) {
            assertInvalid(() -> converter.convert(invalid, column, CONTEXT), "trade_date", invalid);
        }
    }

    @Test
    void convertsOnlyAsciiSixDigitStrictCalendarMonthsToStrings() {
        ColumnDefinition column = column("period", LogicalType.MONTH, true, null, null, null, List.of());

        assertThat(converter.convert(" 202402 ", column, CONTEXT)).isEqualTo("202402").isInstanceOf(String.class);
        for (String invalid : List.of(
                "2024-02", "20242", "0202402", "+1234502", "-000101", "２０２４０２", "202400", "202413")) {
            assertInvalid(() -> converter.convert(invalid, column, CONTEXT), "period", invalid);
        }
    }

    @Test
    void convertsAllApprovedExactLongSources() {
        ColumnDefinition column = column("volume", LogicalType.LONG, true, null, null, null, List.of());

        assertThat(converter.convert(" +42 ", column, CONTEXT)).isEqualTo(42L);
        assertThat(converter.convert((byte) 1, column, CONTEXT)).isEqualTo(1L);
        assertThat(converter.convert((short) 2, column, CONTEXT)).isEqualTo(2L);
        assertThat(converter.convert(3, column, CONTEXT)).isEqualTo(3L);
        assertThat(converter.convert(4L, column, CONTEXT)).isEqualTo(4L);
        assertThat(converter.convert(new BigInteger("5"), column, CONTEXT)).isEqualTo(5L);
        assertThat(converter.convert(new BigDecimal("6"), column, CONTEXT)).isEqualTo(6L);
    }

    @Test
    void rejectsInexactOrUnsupportedLongSources() {
        ColumnDefinition column = column("volume", LogicalType.LONG, true, null, null, null, List.of());

        for (Object invalid : List.of(
                "1.0", "1e2", new BigDecimal("1.0"), new BigInteger("9223372036854775808"),
                1.0F, 1.0D, true, new Object())) {
            assertInvalid(() -> converter.convert(invalid, column, CONTEXT), "volume", "secret-long");
        }
    }

    @Test
    void convertsApprovedDecimalSourcesWithoutBinaryFloatingPoint() {
        ColumnDefinition column = column("amount", LogicalType.DECIMAL, true, null, 8, 2, List.of());

        assertThat(converter.convert(" 12.30 ", column, CONTEXT)).isEqualTo(new BigDecimal("12.30"));
        assertThat(converter.convert(new BigDecimal("12.3"), column, CONTEXT)).isEqualTo(new BigDecimal("12.30"));
        assertThat(converter.convert(new BigInteger("12"), column, CONTEXT)).isEqualTo(new BigDecimal("12.00"));
        assertThat(converter.convert((byte) 1, column, CONTEXT)).isEqualTo(new BigDecimal("1.00"));
        assertThat(converter.convert((short) 2, column, CONTEXT)).isEqualTo(new BigDecimal("2.00"));
        assertThat(converter.convert(3, column, CONTEXT)).isEqualTo(new BigDecimal("3.00"));
        assertThat(converter.convert(4L, column, CONTEXT)).isEqualTo(new BigDecimal("4.00"));
        assertInvalid(() -> converter.convert(0.1F, column, CONTEXT), "amount", "secret-decimal");
        assertInvalid(() -> converter.convert(0.1D, column, CONTEXT), "amount", "secret-decimal");
        assertInvalid(() -> converter.convert(true, column, CONTEXT), "amount", "secret-decimal");
        assertInvalid(() -> converter.convert(new Object(), column, CONTEXT), "amount", "secret-decimal");
    }

    @Test
    void fixesDecimalScaleOnlyWhenExactAndThenChecksPrecision() {
        ColumnDefinition column = column("amount", LogicalType.DECIMAL, true, null, 4, 2, List.of());

        assertThat(converter.convert(new BigDecimal("12.3"), column, CONTEXT)).isEqualTo(new BigDecimal("12.30"));
        assertInvalid(() -> converter.convert(new BigDecimal("12.345"), column, CONTEXT), "amount", "12.345");
        assertInvalid(() -> converter.convert(new BigDecimal("123.45"), column, CONTEXT), "amount", "123.45");
    }

    @Test
    void reportsEveryConversionFailureWithOneSafeNonRetryableSummary() {
        List<FailureCase> cases = List.of(
                new FailureCase(column("string", LogicalType.STRING, true, 2, null, null, List.of()), 1),
                new FailureCase(column("text", LogicalType.TEXT, true, null, null, null, List.of()), 1),
                new FailureCase(column("enum", LogicalType.ENUM, true, 2, null, null, List.of("OK")), "NO"),
                new FailureCase(column("date", LogicalType.DATE, true, null, null, null, List.of()), "20240230"),
                new FailureCase(column("month", LogicalType.MONTH, true, null, null, null, List.of()), "202413"),
                new FailureCase(column("long", LogicalType.LONG, true, null, null, null, List.of()), "1.0"),
                new FailureCase(column("decimal", LogicalType.DECIMAL, true, null, 3, 1, List.of()), "123.4"));

        for (FailureCase failure : cases) {
            assertInvalid(
                    () -> converter.convert(failure.source(), failure.column(), CONTEXT),
                    failure.column().name(),
                    "secret-source");
        }
    }

    private static void assertInvalid(ThrowingCall call, String field, String secret) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AdapterException.class, exception -> {
            assertThat(exception.code()).isEqualTo(ErrorCode.ADAPTER_TYPE_INVALID);
            assertThat(exception.retryable()).isFalse();
            assertThat(exception).hasMessage("Invalid adapter value: api=daily, row=0, field=" + field).hasNoCause();
            assertThat(exception.getMessage()).doesNotContain(secret, "String", "DECIMAL", "NumberFormatException");
        });
    }

    private static ColumnDefinition column(
            String name,
            LogicalType type,
            boolean nullable,
            Integer length,
            Integer precision,
            Integer scale,
            List<String> allowedValues) {
        return new ColumnDefinition(name, name, type, nullable, 0, length, precision, scale, allowedValues, false);
    }

    private record FailureCase(ColumnDefinition column, Object source) {
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
