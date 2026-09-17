package com.akkc.tensor.plugin.api.integrity;

import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.*;

/** Authorization, type binding and cumulative budgets are enforced by the core reader. */
public record IntegrityReadRequest(DatasetKey datasetKey, List<String> columns,
        Map<String, Object> equalities, String dateField, IntegrityDateRange dateRange,
        boolean nullDates, String purpose) {
    public IntegrityReadRequest {
        Objects.requireNonNull(datasetKey, "datasetKey");
        columns = IntegrityValues.columns(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("Projection is required");
        equalities = IntegrityValues.values(equalities);
        IntegrityValues.text(purpose);
        if (dateField != null) IntegrityValues.column(dateField);
        if ((dateRange != null || nullDates) && dateField == null || nullDates && dateRange != null)
            throw new IllegalArgumentException("Invalid date predicate");
    }
}
