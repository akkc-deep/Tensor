package com.akkc.tensor.plugin.tushare.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@FunctionalInterface
public interface CalendarSource {
    CalendarData fetch(Set<String> identities, Set<LocalDate> dates);

    record CalendarData(String sourceId, LocalDate effectiveFrom, LocalDate effectiveThrough,
            boolean revisionsConfirmed, List<CalendarRow> rows) {
        public CalendarData {
            if (rows != null) rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }
    }

    record CalendarRow(String identity, LocalDate date, String isOpen) {}
}
