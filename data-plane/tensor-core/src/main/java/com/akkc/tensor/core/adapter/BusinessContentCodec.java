package com.akkc.tensor.core.adapter;

import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BusinessContentCodec {
    public static final int VERSION = 1;
    private static final Set<String> EXCLUDED =
            Set.of("ingested_at", "source_plugin", "source_api", "business_key");

    public byte[] encode(DatasetDefinition definition, Map<String, Object> row) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(row, "row");
        List<ColumnDefinition> columns = definition.columns().stream()
                .filter(column -> !EXCLUDED.contains(column.name())).toList();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            output.writeInt(columns.size());
            for (ColumnDefinition column : columns) {
                if (!row.containsKey(column.name())) {
                    throw new IllegalArgumentException("Missing adapted business column");
                }
                writeUtf8(output, column.name());
                output.writeByte(typeCode(column));
                if (column.logicalType() == com.akkc.tensor.plugin.api.dataset.LogicalType.DECIMAL) {
                    output.writeInt(column.precision());
                    output.writeInt(column.scale());
                }
                Object value = row.get(column.name());
                if (value == null) {
                    if (!column.nullable()) throw invalid();
                    output.writeByte(0);
                } else {
                    output.writeByte(1);
                    writeUtf8(output, canonical(column, value));
                }
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to encode business content", impossible);
        }
    }

    public String sha256(byte[] canonical) {
        Objects.requireNonNull(canonical, "canonical");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static int typeCode(ColumnDefinition column) {
        return switch (column.logicalType()) {
            case STRING -> 1;
            case TEXT -> 2;
            case ENUM -> 3;
            case DATE -> 4;
            case MONTH -> 5;
            case LONG -> 6;
            case DECIMAL -> 7;
        };
    }

    private static String canonical(ColumnDefinition column, Object value) {
        try {
            return switch (column.logicalType()) {
                case STRING, ENUM -> {
                    String text = require(value, String.class);
                    if (text.codePointCount(0, text.length()) > column.length()
                            || column.logicalType() == com.akkc.tensor.plugin.api.dataset.LogicalType.ENUM
                            && !column.allowedValues().isEmpty() && !column.allowedValues().contains(text)) throw invalid();
                    yield text;
                }
                case TEXT -> require(value, String.class);
                case DATE -> require(value, LocalDate.class).toString();
                case MONTH -> {
                    String month = require(value, String.class);
                    if (!month.matches("[0-9]{6}")) throw invalid();
                    yield YearMonth.of(Integer.parseInt(month.substring(0, 4)),
                            Integer.parseInt(month.substring(4, 6))).toString();
                }
                case LONG -> require(value, Long.class).toString();
                case DECIMAL -> {
                    BigDecimal decimal = require(value, BigDecimal.class)
                            .setScale(column.scale(), RoundingMode.UNNECESSARY);
                    if (decimal.precision() > column.precision()) throw invalid();
                    yield decimal.toPlainString();
                }
            };
        } catch (ArithmeticException | java.time.DateTimeException | NumberFormatException failure) {
            throw invalid();
        }
    }

    private static <T> T require(Object value, Class<T> type) {
        if (!type.isInstance(value)) throw invalid();
        return type.cast(value);
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid adapted business value");
    }
}
