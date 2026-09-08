package com.akkc.tensor.plugin.api.download;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CalendarScope(Map<String, Object> publicParams, Set<LocalDate> dates) {
    public CalendarScope {
        publicParams = FetchBatch.copyParameters(publicParams);
        dates = Set.copyOf(Objects.requireNonNull(dates, "dates"));
        if (dates.isEmpty()) throw new IllegalArgumentException("Calendar dates must not be empty");
    }
}
