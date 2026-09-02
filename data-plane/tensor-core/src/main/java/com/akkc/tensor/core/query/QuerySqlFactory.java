package com.akkc.tensor.core.query;

import com.akkc.tensor.core.persistence.SqlIdentifierPolicy;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class QuerySqlFactory {
    private static final Set<String> SUPPORTED_FILTERS = Set.of("ts_code", "trade_date", "ann_date");
    private final SqlIdentifierPolicy identifiers = new SqlIdentifierPolicy();

    public QuerySql create(DatasetDefinition definition, QueryCriteria criteria) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(criteria, "criteria");

        Set<String> filters = definition.filters().stream()
                .map(filter -> filter.field())
                .collect(Collectors.toUnmodifiableSet());
        if (!SUPPORTED_FILTERS.containsAll(filters)) {
            throw new IllegalArgumentException("Unsupported dataset filter metadata");
        }

        List<String> conditions = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        addTsCodeCondition(criteria, filters, conditions, values);
        addDateConditions("trade_date", criteria.tradeDateFrom(), criteria.tradeDateTo(), filters, conditions, values);
        addDateConditions("ann_date", criteria.annDateFrom(), criteria.annDateTo(), filters, conditions, values);

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        String table = identifiers.quote(definition.tableName().value());
        String countSql = "SELECT COUNT(*) FROM " + table + where;
        String pageSql = "SELECT " + selectColumns(definition) + " FROM " + table + where + " ORDER BY "
                + orderColumns(definition) + " LIMIT ? OFFSET ?";
        List<Object> pageValues = new ArrayList<>(values);
        pageValues.add(criteria.pageSize());
        pageValues.add((long) (criteria.page() - 1) * criteria.pageSize());
        return new QuerySql(countSql, values, pageSql, pageValues);
    }

    private void addTsCodeCondition(QueryCriteria criteria, Set<String> filters, List<String> conditions, List<Object> values) {
        if (criteria.tsCode() != null) {
            requireDeclaredFilter("ts_code", filters);
            conditions.add(identifiers.quote("ts_code") + " = ?");
            values.add(criteria.tsCode());
        }
    }

    private void addDateConditions(String field, java.time.LocalDate from, java.time.LocalDate to, Set<String> filters,
            List<String> conditions, List<Object> values) {
        if (from == null && to == null) {
            return;
        }
        requireDeclaredFilter(field, filters);
        String column = identifiers.quote(field);
        if (from != null && to != null) {
            conditions.add(column + " BETWEEN ? AND ?");
            values.add(from);
            values.add(to);
        } else if (from != null) {
            conditions.add(column + " >= ?");
            values.add(from);
        } else {
            conditions.add(column + " <= ?");
            values.add(to);
        }
    }

    private void requireDeclaredFilter(String field, Set<String> filters) {
        if (!filters.contains(field)) {
            throw new IllegalArgumentException("Filter is not supported by dataset");
        }
    }

    private String selectColumns(DatasetDefinition definition) {
        List<String> columns = definition.columns().stream()
                .map(column -> identifiers.quote(column.name()))
                .collect(Collectors.toCollection(ArrayList::new));
        columns.add(identifiers.quote("source_plugin"));
        columns.add(identifiers.quote("source_api"));
        columns.add(identifiers.quote("ingested_at"));
        return String.join(", ", columns);
    }

    private String orderColumns(DatasetDefinition definition) {
        List<String> columns = definition.businessKey().fields().stream()
                .map(field -> identifiers.quote(field) + " ASC")
                .collect(Collectors.toCollection(ArrayList::new));
        if (definition.businessKey().mode() == BusinessKeyMode.FINGERPRINT) {
            columns.add(identifiers.quote("business_key") + " ASC");
        }
        return String.join(", ", columns);
    }
}
