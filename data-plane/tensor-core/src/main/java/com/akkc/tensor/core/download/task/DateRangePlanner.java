package com.akkc.tensor.core.download.task;

import com.akkc.tensor.plugin.api.download.batch.DateRange;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DateRangePlanner {
    private DateRangePlanner() {}

    public static List<DateRange> split(DateRange range) {
        Objects.requireNonNull(range, "range");
        if (range.start().equals(range.end())) throw new IllegalArgumentException("Range cannot be split");
        LocalDate middle = range.start().plusDays(ChronoUnit.DAYS.between(range.start(), range.end()) / 2);
        return List.of(new DateRange(range.start(), middle), new DateRange(middle.plusDays(1), range.end()));
    }

    public static List<DateRange> calendarDays(DateRange range) {
        Objects.requireNonNull(range, "range");
        List<DateRange> days = new ArrayList<>();
        for (LocalDate day = range.start(); ; day = day.plusDays(1)) {
            days.add(new DateRange(day, day));
            if (day.equals(range.end())) return List.copyOf(days);
        }
    }
}
