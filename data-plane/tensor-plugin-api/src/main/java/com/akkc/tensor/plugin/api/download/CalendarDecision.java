package com.akkc.tensor.plugin.api.download;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CalendarDecision(CalendarScope scope, Map<String, Map<LocalDate, Boolean>> calendars) {
    public CalendarDecision {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(calendars, "calendars");
        if (calendars.isEmpty()) throw new IllegalArgumentException("Necessary calendars must not be empty");
        var copied = new HashMap<String, Map<LocalDate, Boolean>>();
        for (var entry : calendars.entrySet()) {
            String identity = Objects.requireNonNull(entry.getKey(), "calendar identity");
            if (identity.isBlank()) throw new IllegalArgumentException("Calendar identity must not be blank");
            var dates = Map.copyOf(Objects.requireNonNull(entry.getValue(), "calendar dates"));
            if (!dates.keySet().equals(scope.dates())) {
                throw new IllegalArgumentException("Calendar must cover exactly the requested dates");
            }
            copied.put(identity, dates);
        }
        calendars = Map.copyOf(copied);
    }

    public Set<LocalDate> openDates() {
        var result = new HashSet<LocalDate>();
        calendars.values().forEach(dates -> dates.forEach((date, open) -> { if (open) result.add(date); }));
        return Set.copyOf(result);
    }
}
