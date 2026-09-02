package com.akkc.tensor.core.query;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

public record QueryCriteria(
        String tsCode,
        LocalDate tradeDateFrom,
        LocalDate tradeDateTo,
        LocalDate annDateFrom,
        LocalDate annDateTo,
        int page,
        int pageSize) {
    private static final Pattern TS_CODE = Pattern.compile("[A-Z0-9]+\\.[A-Z0-9]+");

    public QueryCriteria {
        if (tsCode != null) {
            tsCode = tsCode.strip().toUpperCase(Locale.ROOT);
            if (!TS_CODE.matcher(tsCode).matches()) {
                throw new IllegalArgumentException("tsCode has invalid format");
            }
        }
        if (tradeDateFrom != null && tradeDateTo != null && tradeDateFrom.isAfter(tradeDateTo)) {
            throw new IllegalArgumentException("tradeDateFrom must not be after tradeDateTo");
        }
        if (annDateFrom != null && annDateTo != null && annDateFrom.isAfter(annDateTo)) {
            throw new IllegalArgumentException("annDateFrom must not be after annDateTo");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (pageSize != 20 && pageSize != 50 && pageSize != 100) {
            throw new IllegalArgumentException("pageSize must be one of 20, 50, 100");
        }
    }
}
