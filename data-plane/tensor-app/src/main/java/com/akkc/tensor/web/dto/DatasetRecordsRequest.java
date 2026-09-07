package com.akkc.tensor.web.dto;

import java.util.List;
import java.util.Set;

public record DatasetRecordsRequest(
        DatasetPath path,
        String tsCode,
        DateRange tradeDate,
        DateRange annDate,
        Pagination pagination,
        Set<String> parameterNames) {

    public DatasetRecordsRequest {
        parameterNames = Set.copyOf(parameterNames);
    }

    public record DateRange(String from, String to) {}

    public record Pagination(List<String> page, List<String> pageSize) {
        public Pagination {
            if (page != null) {
                page = List.copyOf(page);
            }
            if (pageSize != null) {
                pageSize = List.copyOf(pageSize);
            }
        }
    }
}
