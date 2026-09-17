package com.akkc.tensor.core.integrity;

import com.akkc.tensor.core.adapter.ConversionContext;
import com.akkc.tensor.core.adapter.FingerprintKeyCodec;
import com.akkc.tensor.core.adapter.ValueConverter;
import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact logical business-key normalization shared by integrity comparison and core rules. */
public final class IntegrityBusinessKeys {
    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern(ValidationConstants.MONTH_FORMAT);

    private final DatasetDefinition definition;
    private final List<String> fields;
    private final Map<String, ColumnDefinition> columns;
    private final ValueConverter converter = new ValueConverter();
    private final FingerprintKeyCodec fingerprints = new FingerprintKeyCodec();

    public IntegrityBusinessKeys(DatasetDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
        fields = definition.businessKey().fields();
        LinkedHashMap<String, ColumnDefinition> indexed = new LinkedHashMap<>();
        definition.columns().forEach(column -> indexed.put(column.name(), column));
        columns = Collections.unmodifiableMap(indexed);
    }

    public Key normalize(Map<String, Object> row, boolean expected) {
        Key key = normalizeLogical(row, expected);
        if (!expected && definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            Object stored = row.get(DatasetFields.BUSINESS_KEY);
            if (!row.containsKey(DatasetFields.BUSINESS_KEY) || !(stored instanceof String value)
                    || value.isBlank()) {
                throw invalid(DatasetFields.BUSINESS_KEY, "KEY_FIELD_MISSING");
            }
            String computed = fingerprints.sha256(fields, key.fields());
            if (!computed.equals(stored)) {
                throw invalid(DatasetFields.BUSINESS_KEY, "FINGERPRINT_MISMATCH");
            }
        }
        return key;
    }

    /** Logical-only form used when a damaged stored fingerprint must not change set membership. */
    public Key normalizeLogical(Map<String, Object> row) {
        return normalizeLogical(row, false);
    }

    /** Best-effort exact scalars for locating an invalid row; invalid or absent fields stay null. */
    public Map<String, Object> locatableFields(Map<String, Object> row) {
        Objects.requireNonNull(row, "row");
        LinkedHashMap<String, Object> available = new LinkedHashMap<>();
        for (String field : fields) {
            if (!row.containsKey(field)) continue;
            Object source = row.get(field);
            Object value;
            try {
                value = convert(source, columns.get(field));
            } catch (IllegalArgumentException ignored) {
                value = boundaryScalar(source) ? source : null;
            }
            available.put(field, value);
        }
        return Collections.unmodifiableMap(available);
    }

    private static boolean boundaryScalar(Object value) {
        return value == null || value instanceof String || value instanceof Long || value instanceof Integer
                || value instanceof java.math.BigDecimal || value instanceof LocalDate || value instanceof YearMonth
                || value instanceof java.time.Instant || value instanceof Boolean;
    }

    private Key normalizeLogical(Map<String, Object> row, boolean expected) {
        Objects.requireNonNull(row, "row");
        if (expected && (!row.keySet().equals(new java.util.LinkedHashSet<>(fields)))) {
            throw invalid(null, "KEY_VALUE_INVALID");
        }
        ArrayList<Object> values = new ArrayList<>(fields.size());
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (String field : fields) {
            if (!row.containsKey(field)) throw invalid(field, "KEY_FIELD_MISSING");
            ColumnDefinition column = columns.get(field);
            Object value = convert(row.get(field), column);
            boolean nullableFingerprint = definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT
                    && column.nullable();
            if (value == null && !nullableFingerprint) throw invalid(field, "KEY_FIELD_MISSING");
            values.add(value);
            normalized.put(field, value);
        }
        return new Key(values, normalized);
    }

    private Object convert(Object source, ColumnDefinition column) {
        try {
            if (source instanceof LocalDate && column.logicalType() == LogicalType.DATE) return source;
            if (source instanceof YearMonth month && column.logicalType() == LogicalType.MONTH) {
                source = MONTH_FORMATTER.format(month);
            }
            return converter.convert(source, column, new ConversionContext(definition.datasetKey().apiName(), 0));
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidKeyException invalid) throw invalid;
            throw invalid(column.name(), "KEY_VALUE_INVALID");
        }
    }

    private static InvalidKeyException invalid(String field, String reasonCode) {
        return new InvalidKeyException(field, reasonCode);
    }

    public static final class InvalidKeyException extends IllegalArgumentException {
        private final String field;
        private final String reasonCode;

        private InvalidKeyException(String field, String reasonCode) {
            super("Invalid integrity business key");
            this.field = field;
            this.reasonCode = reasonCode;
        }

        public String field() { return field; }
        public String reasonCode() { return reasonCode; }
    }

    public record Key(List<Object> values, Map<String, Object> fields) {
        public Key {
            values = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(values, "values")));
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(fields, "fields")));
        }

        @Override public boolean equals(Object other) {
            return other instanceof Key key && values.equals(key.values);
        }

        @Override public int hashCode() {
            return values.hashCode();
        }
    }
}
