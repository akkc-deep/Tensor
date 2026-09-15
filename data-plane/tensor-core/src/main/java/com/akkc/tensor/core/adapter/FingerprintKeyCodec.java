package com.akkc.tensor.core.adapter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FingerprintKeyCodec {
    public static final String HASH_ALGORITHM = "SHA-256";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd");

    public String sha256(List<String> fields, Map<String, Object> row) {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(row, "row");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        Set<String> names = new HashSet<>();
        for (String field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("fields must not contain null");
            }
            if (!names.add(field)) {
                throw new IllegalArgumentException("fields must not contain duplicates");
            }
            if (!row.containsKey(field)) {
                throw new IllegalArgumentException("row must contain fields");
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (String field : fields) {
            Object value = row.get(field);
            if (value == null) {
                bytes.write(0);
            } else {
                byte[] text = canonicalText(value).getBytes(StandardCharsets.UTF_8);
                bytes.write(1);
                bytes.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(text.length).array());
                bytes.writeBytes(text);
            }
        }
        return HexFormat.of().formatHex(digest(bytes.toByteArray()));
    }

    private String canonicalText(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof LocalDate date) {
            return DATE_FORMATTER.format(date);
        }
        if (value instanceof Long number) {
            return Long.toString(number);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        throw new IllegalArgumentException("Unsupported fingerprint value type");
    }

    private byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM).digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
