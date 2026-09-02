package com.akkc.tensor.core.query;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import java.util.List;
import java.util.Objects;

public final class DatasetQueryService {
    private final DatasetCatalog datasetCatalog;
    private final GenericQueryRepository repository;
    private final QuerySqlFactory querySqlFactory = new QuerySqlFactory();

    public DatasetQueryService(
            DatasetCatalog datasetCatalog, GenericQueryRepository repository) {
        this.datasetCatalog = Objects.requireNonNull(datasetCatalog, "datasetCatalog");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public DatasetPage query(DatasetKey key, QueryCriteria criteria) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(criteria, "criteria");
        DatasetDefinition definition = datasetCatalog.find(key)
                .orElseThrow(() -> new IllegalArgumentException("Dataset is not available"));
        QuerySql querySql = querySqlFactory.create(definition, criteria);
        long totalElements = repository.count(querySql);
        List<String> columns = GenericQueryRepository.columns(definition);
        if (totalElements == 0) {
            return new DatasetPage(columns, List.of(), 1, criteria.pageSize(), 0, 0);
        }

        long totalPages = 1 + (totalElements - 1) / criteria.pageSize();
        int page = (int) Math.min(criteria.page(), totalPages);
        if (page != criteria.page()) {
            QueryCriteria normalized = new QueryCriteria(
                    criteria.tsCode(),
                    criteria.tradeDateFrom(),
                    criteria.tradeDateTo(),
                    criteria.annDateFrom(),
                    criteria.annDateTo(),
                    page,
                    criteria.pageSize());
            querySql = querySqlFactory.create(definition, normalized);
        }
        return new DatasetPage(
                columns,
                repository.query(definition, querySql),
                page,
                criteria.pageSize(),
                totalElements,
                totalPages);
    }
}
