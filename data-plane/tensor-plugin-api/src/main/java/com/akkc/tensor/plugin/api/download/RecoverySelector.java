package com.akkc.tensor.plugin.api.download;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.DateTimeException;
import java.util.Objects;

public record RecoverySelector(TargetType targetType, String targetValue, TimeType timeType, String timeValue) {
    public enum TargetType { STOCK, REQUEST }
    public enum TimeType { DATE, MONTH, RANGE, NONE }

    public RecoverySelector {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetValue, "targetValue");
        Objects.requireNonNull(timeType, "timeType");
        Objects.requireNonNull(timeValue, "timeValue");
        if (targetType == TargetType.REQUEST ? !targetValue.isEmpty()
                : targetValue.length() > 64 || !targetValue.matches("[A-Z0-9]+\\.[A-Z0-9]+")) {
            throw new IllegalArgumentException("Invalid recovery target");
        }
        try {
            switch (timeType) {
                case DATE -> date(timeValue);
                case MONTH -> {
                    if (!timeValue.matches("[0-9]{4}-[0-9]{2}") || timeValue.startsWith("0000")) {
                        throw new IllegalArgumentException("Invalid recovery month");
                    }
                    YearMonth.parse(timeValue);
                }
                case RANGE -> {
                    String[] dates = timeValue.split("/", -1);
                    if (dates.length != 2 || !date(dates[0]).isBefore(date(dates[1]))) {
                        throw new IllegalArgumentException("Recovery range must have distinct ordered endpoints");
                    }
                }
                case NONE -> {
                    if (!timeValue.isEmpty()) throw new IllegalArgumentException("NONE time must be empty");
                }
            }
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid recovery time");
        }
    }

    private static LocalDate date(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
            throw new IllegalArgumentException("Invalid recovery date");
        }
        return LocalDate.parse(value);
    }
}
