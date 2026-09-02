package com.akkc.tensor.core.adapter;

import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.error.AdapterException;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ValueConverter {
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern DATE_PATTERN = Pattern.compile("[0-9]{8}");
    private static final Pattern MONTH_PATTERN = Pattern.compile("[0-9]{6}");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("uuuuMM", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    public Object convert(Object source, ColumnDefinition column, ConversionContext context) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(context, "context");
        if (source == null) {
            return null;
        }
        return switch (column.logicalType()) {
            case STRING -> shortString(source, column, context);
            case TEXT -> text(source, column, context);
            case ENUM -> enumeration(source, column, context);
            case DATE -> date(source, column, context);
            case MONTH -> month(source, column, context);
            case LONG -> longValue(source, column, context);
            case DECIMAL -> decimal(source, column, context);
        };
    }

    private String shortString(Object source, ColumnDefinition column, ConversionContext context) {
        String value = trimmedString(source, column, context);
        if (value == null) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > column.length()) {
            throw invalid(column, context);
        }
        return value;
    }

    private String text(Object source, ColumnDefinition column, ConversionContext context) {
        if (source instanceof String value) {
            return value;
        }
        throw invalid(column, context);
    }

    private String enumeration(Object source, ColumnDefinition column, ConversionContext context) {
        String value = shortString(source, column, context);
        if (value != null && !column.allowedValues().isEmpty() && !column.allowedValues().contains(value)) {
            throw invalid(column, context);
        }
        return value;
    }

    private LocalDate date(Object source, ColumnDefinition column, ConversionContext context) {
        String value = trimmedString(source, column, context);
        if (value == null) {
            return null;
        }
        if (!DATE_PATTERN.matcher(value).matches()) {
            throw invalid(column, context);
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeException exception) {
            throw invalid(column, context);
        }
    }

    private String month(Object source, ColumnDefinition column, ConversionContext context) {
        String value = trimmedString(source, column, context);
        if (value == null) {
            return null;
        }
        if (!MONTH_PATTERN.matcher(value).matches()) {
            throw invalid(column, context);
        }
        try {
            YearMonth.parse(value, MONTH_FORMATTER);
            return value;
        } catch (DateTimeException exception) {
            throw invalid(column, context);
        }
    }

    private Long longValue(Object source, ColumnDefinition column, ConversionContext context) {
        try {
            if (source instanceof String value) {
                String normalized = value.trim();
                if (normalized.isEmpty()) {
                    return null;
                }
                if (!INTEGER_PATTERN.matcher(normalized).matches()) {
                    throw invalid(column, context);
                }
                return new BigInteger(normalized).longValueExact();
            }
            if (source instanceof Byte value) {
                return value.longValue();
            }
            if (source instanceof Short value) {
                return value.longValue();
            }
            if (source instanceof Integer value) {
                return value.longValue();
            }
            if (source instanceof Long value) {
                return value;
            }
            if (source instanceof BigInteger value) {
                return value.longValueExact();
            }
            if (source instanceof BigDecimal value && value.scale() == 0) {
                return value.longValueExact();
            }
        } catch (ArithmeticException exception) {
            throw invalid(column, context);
        }
        throw invalid(column, context);
    }

    private BigDecimal decimal(Object source, ColumnDefinition column, ConversionContext context) {
        BigDecimal value;
        try {
            if (source instanceof String text) {
                String normalized = text.trim();
                if (normalized.isEmpty()) {
                    return null;
                }
                value = new BigDecimal(normalized);
            } else if (source instanceof BigDecimal decimal) {
                value = decimal;
            } else if (source instanceof BigInteger integer) {
                value = new BigDecimal(integer);
            } else if (source instanceof Byte integer) {
                value = BigDecimal.valueOf(integer.longValue());
            } else if (source instanceof Short integer) {
                value = BigDecimal.valueOf(integer.longValue());
            } else if (source instanceof Integer integer) {
                value = BigDecimal.valueOf(integer.longValue());
            } else if (source instanceof Long integer) {
                value = BigDecimal.valueOf(integer);
            } else {
                throw invalid(column, context);
            }
            BigDecimal scaled = value.setScale(column.scale(), RoundingMode.UNNECESSARY);
            if (scaled.precision() > column.precision()) {
                throw invalid(column, context);
            }
            return scaled;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw invalid(column, context);
        }
    }

    private String trimmedString(Object source, ColumnDefinition column, ConversionContext context) {
        if (!(source instanceof String value)) {
            throw invalid(column, context);
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private AdapterException invalid(ColumnDefinition column, ConversionContext context) {
        return new AdapterException(
                ErrorCode.ADAPTER_TYPE_INVALID,
                "Invalid adapter value: api=" + context.apiName().value() + ", row=" + context.rowIndex()
                        + ", field=" + column.name());
    }
}
