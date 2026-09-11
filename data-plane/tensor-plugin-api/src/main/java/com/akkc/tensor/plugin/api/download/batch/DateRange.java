package com.akkc.tensor.plugin.api.download.batch;

import java.time.LocalDate;
import java.util.Objects;

/** An inclusive range; the plugin descriptor defines its date meaning. */
public record DateRange(LocalDate start, LocalDate end) {
    public DateRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must not be after end");
        }
    }
}
