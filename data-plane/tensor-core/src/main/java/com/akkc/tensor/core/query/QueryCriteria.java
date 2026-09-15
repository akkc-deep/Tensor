package com.akkc.tensor.core.query;

import com.akkc.tensor.plugin.api.constant.PaginationConstants;
import com.akkc.tensor.plugin.api.constant.ValidationConstants;
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
    private static final Pattern TS_CODE = Pattern.compile(ValidationConstants.TS_CODE_REGEX);

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
        if (page < PaginationConstants.FIRST_PAGE) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (!PaginationConstants.PAGE_SIZES.contains(pageSize)) {
            throw new IllegalArgumentException("pageSize must be one of 20, 50, 100");
        }
    }
}
