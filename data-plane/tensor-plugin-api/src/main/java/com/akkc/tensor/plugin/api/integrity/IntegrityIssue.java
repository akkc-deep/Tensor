package com.akkc.tensor.plugin.api.integrity;

import com.akkc.tensor.plugin.api.model.ApiName;
import java.time.LocalDate;
import java.util.*;

/** Rule identity is bound by the executor, never selected by the source rule. */
public record IntegrityIssue(Type type, IntegrityStatus status, String symbol, ApiName apiName,
        String dateField, LocalDate date, Map<String, Object> businessKey, String field,
        Map<String, LocalDate> relatedDates, String reasonCode, String message,
        List<IntegrityEvidence> evidence, boolean incomplete) {
    public enum Type { MISSING, SUSPECTED_MISSING, EXTRA, REQUIRED_FIELD_MISSING,
        BUSINESS_KEY_INVALID, SOURCE_IDENTITY_MISMATCH, REFERENCE_INCOMPLETE,
        DATE_SCOPE_UNRESOLVED, RULE_EXECUTION_FAILED }
    public IntegrityIssue {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(apiName, "apiName");
        if (symbol != null) IntegrityValues.text(symbol);
        if (dateField != null) IntegrityValues.column(dateField);
        if (date != null && dateField == null) throw new IllegalArgumentException("Date requires an axis");
        if (field != null) IntegrityValues.column(field);
        businessKey = IntegrityValues.values(businessKey);
        relatedDates = Map.copyOf(relatedDates);
        relatedDates.keySet().forEach(IntegrityValues::column);
        IntegrityValues.text(reasonCode);
        IntegrityValues.text(message);
        evidence = List.copyOf(evidence);
        if (type == Type.MISSING && status != IntegrityStatus.FAIL
                || type == Type.SUSPECTED_MISSING && status != IntegrityStatus.WARN)
            throw new IllegalArgumentException("Invalid missing issue severity");
    }
}
