package com.akkc.tensor.plugin.api.integrity;

import java.time.LocalDate;
import java.util.Objects;

public record IntegrityDateRange(LocalDate startDate, LocalDate endDate) {
    public IntegrityDateRange {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("Reversed date range");
    }
    public boolean contains(IntegrityDateRange other) {
        return !startDate.isAfter(other.startDate) && !endDate.isBefore(other.endDate);
    }
}
