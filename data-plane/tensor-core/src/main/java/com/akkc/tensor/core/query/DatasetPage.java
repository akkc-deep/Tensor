package com.akkc.tensor.core.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DatasetPage(
        List<String> columns,
        List<Map<String, Object>> items,
        int page,
        int pageSize,
        long totalElements,
        long totalPages) {
    public DatasetPage {
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(items, "items");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        if (columns.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("columns must not contain null");
        }
        if (new HashSet<>(columns).size() != columns.size()) {
            throw new IllegalArgumentException("columns must not contain duplicates");
        }
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("items must not contain null");
        }

        columns = List.copyOf(columns);
        List<Map<String, Object>> copiedItems = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            if (!new ArrayList<>(item.keySet()).equals(columns)) {
                throw new IllegalArgumentException("row keys must exactly match columns in order");
            }
            copiedItems.add(Collections.unmodifiableMap(new LinkedHashMap<>(item)));
        }
        items = List.copyOf(copiedItems);

        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (pageSize != 20 && pageSize != 50 && pageSize != 100) {
            throw new IllegalArgumentException("pageSize must be one of 20, 50, 100");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("totals must be non-negative");
        }
        long expectedPages = totalElements == 0 ? 0 : 1 + (totalElements - 1) / pageSize;
        if (totalPages != expectedPages) {
            throw new IllegalArgumentException("totalPages must match totalElements and pageSize");
        }
        if (totalElements == 0 && (page != 1 || !items.isEmpty())) {
            throw new IllegalArgumentException("empty pages must use page 1 and no items");
        }
        if (totalElements > 0 && page > totalPages) {
            throw new IllegalArgumentException("page must not exceed totalPages");
        }
        if (items.size() > pageSize || items.size() > totalElements) {
            throw new IllegalArgumentException("items must not exceed pageSize or totalElements");
        }
    }
}
