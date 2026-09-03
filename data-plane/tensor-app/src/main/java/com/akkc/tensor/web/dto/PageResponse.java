package com.akkc.tensor.web.dto;

import com.akkc.tensor.core.query.DatasetPage;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PageResponse(
        String requestId,
        String pluginId,
        String apiName,
        int page,
        int pageSize,
        long totalElements,
        long totalPages,
        List<String> columns,
        List<Map<String, Object>> items) {
    public PageResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(apiName, "apiName");
        if (requestId.isBlank() || pluginId.isBlank() || apiName.isBlank()) {
            throw new IllegalArgumentException("page response identity must not be blank");
        }
        DatasetPage validated = new DatasetPage(
                columns, items, page, pageSize, totalElements, totalPages);
        columns = validated.columns();
        items = validated.items();
    }

    public static PageResponse from(
            String requestId, DatasetKey key, DatasetPage page) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(page, "page");
        return new PageResponse(
                requestId,
                key.pluginId().value(),
                key.apiName().value(),
                page.page(),
                page.pageSize(),
                page.totalElements(),
                page.totalPages(),
                page.columns(),
                page.items());
    }
}
