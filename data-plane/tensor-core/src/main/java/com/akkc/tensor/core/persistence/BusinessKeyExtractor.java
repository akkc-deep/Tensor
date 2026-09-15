package com.akkc.tensor.core.persistence;

import com.akkc.tensor.plugin.api.constant.ValidationConstants;
import com.akkc.tensor.plugin.api.constant.DatasetFields;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class BusinessKeyExtractor {
    private static final String MISSING_BUSINESS_KEY = "Missing business key";

    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile(ValidationConstants.FINGERPRINT_REGEX);

    public BusinessKey extract(DatasetDefinition definition, Map<String, Object> row) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(row, "row");
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            return fingerprint(row);
        }
        List<Object> values = new ArrayList<>();
        for (String field : definition.businessKey().fields()) {
            if (!row.containsKey(field) || row.get(field) == null) {
                throw new IllegalArgumentException(MISSING_BUSINESS_KEY);
            }
            values.add(row.get(field));
        }
        return new BusinessKey(values);
    }

    private BusinessKey fingerprint(Map<String, Object> row) {
        if (!row.containsKey(DatasetFields.BUSINESS_KEY) || row.get(DatasetFields.BUSINESS_KEY) == null) {
            throw new IllegalArgumentException(MISSING_BUSINESS_KEY);
        }
        Object value = row.get(DatasetFields.BUSINESS_KEY);
        if (!(value instanceof String fingerprint) || !FINGERPRINT_PATTERN.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("Invalid fingerprint business key");
        }
        return new BusinessKey(List.of(fingerprint));
    }
}
