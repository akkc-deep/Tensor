package com.akkc.tensor.plugin.api.integrity;

import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.time.*;
import java.util.Objects;

public record IntegrityScope(DatasetKey datasetKey, String symbol, LocalDate startDate,
        LocalDate endDate, Instant acceptedAt, Instant snapshotStartedAt) {
    public IntegrityScope {
        Objects.requireNonNull(datasetKey, "datasetKey");
        if (symbol != null) IntegrityValues.text(symbol);
        new IntegrityDateRange(startDate, endDate);
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        if (snapshotStartedAt != null && snapshotStartedAt.isBefore(acceptedAt))
            throw new IllegalArgumentException("Snapshot precedes acceptance");
    }
    public IntegrityDateRange range() { return new IntegrityDateRange(startDate, endDate); }
}
