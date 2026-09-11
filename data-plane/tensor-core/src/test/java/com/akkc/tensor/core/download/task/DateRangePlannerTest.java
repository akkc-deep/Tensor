package com.akkc.tensor.core.download.task;

import com.akkc.tensor.plugin.api.download.batch.DateRange;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DateRangePlannerTest {
    @Test void splitsLeapDaysAndYearBoundariesWithoutGaps() {
        assertThat(DateRangePlanner.split(range("20240228", "20240301")))
                .containsExactly(range("20240228", "20240229"), range("20240301", "20240301"));
        assertThat(DateRangePlanner.split(range("20251231", "20260101")))
                .containsExactly(range("20251231", "20251231"), range("20260101", "20260101"));
        assertThat(DateRangePlanner.split(range("20260227", "20260302")))
                .containsExactly(range("20260227", "20260228"), range("20260301", "20260302"));
    }

    @Test void enumeratesBothEndpointsIncludingLeapDay() {
        List<DateRange> days = DateRangePlanner.calendarDays(range("20240228", "20240301"));
        assertThat(days).containsExactly(range("20240228", "20240228"),
                range("20240229", "20240229"), range("20240301", "20240301"));
        assertThatThrownBy(() -> days.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void handlesExtremeDatesAndRejectsSinglePointSplitting() {
        assertThat(DateRangePlanner.calendarDays(new DateRange(LocalDate.MAX, LocalDate.MAX)))
                .containsExactly(new DateRange(LocalDate.MAX, LocalDate.MAX));
        assertThat(DateRangePlanner.split(new DateRange(LocalDate.MIN, LocalDate.MAX)))
                .containsExactly(new DateRange(LocalDate.MIN, LocalDate.of(0, 7, 1)),
                        new DateRange(LocalDate.of(0, 7, 2), LocalDate.MAX));
        assertThatThrownBy(() -> DateRangePlanner.split(range("20260101", "20260101")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Range cannot be split");
        assertThatNullPointerException().isThrownBy(() -> DateRangePlanner.split(null));
        assertThatNullPointerException().isThrownBy(() -> DateRangePlanner.calendarDays(null));
    }

    private static DateRange range(String start, String end) {
        return new DateRange(LocalDate.parse(start, java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                LocalDate.parse(end, java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
    }
}
